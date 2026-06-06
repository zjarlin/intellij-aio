package site.addzero.composebuddy.previewsandbox

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.security.MessageDigest

object PreviewReachableAstCollector {
    private const val MAX_DECLARATIONS = 256

    fun collect(previewFunction: KtNamedFunction): PreviewSandboxSnapshot? {
        val previewFile = previewFunction.containingKtFile
        val previewVirtualFile = previewFile.virtualFile ?: return null
        val previewName = previewFunction.name ?: return null

        val declarations = linkedMapOf<DeclarationKey, KtNamedDeclaration>()
        val queue = ArrayDeque<KtNamedDeclaration>()

        fun enqueue(declaration: KtNamedDeclaration) {
            val actualDeclarations = declaration.matchingJvmActualDeclarations()
            if (actualDeclarations.isNotEmpty()) {
                actualDeclarations.forEach(::enqueue)
                return
            }
            if (!declaration.isSandboxEligible()) {
                return
            }
            val key = declaration.declarationKey() ?: return
            if (declarations.putIfAbsent(key, declaration) == null) {
                queue.add(declaration)
                declaration.matchingKoinImplementationDeclarations().forEach(::enqueue)
                declaration.matchingThemeSupportDeclarations().forEach(::enqueue)
            }
        }

        enqueue(previewFunction)

        while (queue.isNotEmpty() && declarations.size <= MAX_DECLARATIONS) {
            val declaration = queue.removeFirst()
            declaration.collectReachableDeclarations().forEach(::enqueue)
        }

        if (declarations.isEmpty()) {
            return null
        }

        val sourceFiles = declarations.values
            .groupBy { declaration -> declaration.containingKtFile.fileKey() }
            .mapNotNull { (fileKey, reachableDeclarations) ->
                val file = reachableDeclarations.firstOrNull()?.containingKtFile ?: return@mapNotNull null
                val reachableSet = reachableDeclarations.toSet()
                val orderedDeclarations = file.declarations
                    .filterIsInstance<KtNamedDeclaration>()
                    .filter { declaration -> declaration in reachableSet }
                if (orderedDeclarations.isEmpty()) {
                    return@mapNotNull null
                }

                val usedNames = orderedDeclarations
                    .flatMap { declaration -> declaration.collectReferencedNames() }
                    .toSet() + orderedDeclarations.mapNotNull { declaration -> declaration.name }
                val declarationTexts = orderedDeclarations.map { declaration -> declaration.text.trimEnd() }
                val usesPropertyDelegation = declarationTexts.any { declarationText ->
                    Regex("""\bby\s+""").containsMatchIn(declarationText)
                }

                PreviewSandboxSourceFile(
                    key = fileKey,
                    packageName = file.packageFqName.asString(),
                    originalPath = file.virtualFile?.path ?: file.name,
                    outputFileName = file.sandboxOutputFileName(),
                    imports = file.importDirectives
                        .mapNotNull { importDirective -> importDirective.text.trim().takeIf(String::isNotBlank) }
                        .filter { importText -> importText.isRelevantImport(usedNames, usesPropertyDelegation) }
                        .distinct(),
                    declarations = declarationTexts,
                )
            }
            .sortedWith(
                compareByDescending<PreviewSandboxSourceFile> { sourceFile -> sourceFile.key == previewFile.fileKey() }
                    .thenBy { sourceFile -> sourceFile.packageName }
                    .thenBy { sourceFile -> sourceFile.outputFileName },
            )

        val sandboxId = buildSandboxId(previewVirtualFile, previewName, previewFunction.textRange.startOffset)
        val dependencyClassPath = PreviewSandboxDependencyClasspath.collect(previewFile)
        return PreviewSandboxSnapshot(
            sandboxId = sandboxId,
            previewName = previewName,
            previewPackage = previewFile.packageFqName.asString(),
            originalPreviewPath = previewVirtualFile.path,
            entryFileKey = previewFile.fileKey(),
            files = sourceFiles,
            dependencyClassPath = dependencyClassPath,
            externalMavenDependencies = PreviewSandboxExternalDependencies.infer(sourceFiles, dependencyClassPath),
        )
    }

    private fun KtNamedDeclaration.collectReachableDeclarations(): List<KtNamedDeclaration> {
        val reachable = linkedMapOf<DeclarationKey, KtNamedDeclaration>()
        collectDescendantsOfType<KtNameReferenceExpression>().forEach { reference ->
            val resolved = resolveReference(reference) ?: return@forEach
            val declaration = resolved.sourceTopLevelDeclaration() ?: return@forEach
            if (!declaration.isSandboxEligible()) {
                return@forEach
            }
            val key = declaration.declarationKey() ?: return@forEach
            reachable.putIfAbsent(key, declaration)
        }
        return reachable.values.toList()
    }

    private fun KtNamedDeclaration.matchingJvmActualDeclarations(): List<KtNamedDeclaration> {
        if (!hasModifier(KtTokens.EXPECT_KEYWORD)) {
            return emptyList()
        }
        val expectedName = name ?: return emptyList()
        val expectedFile = containingKtFile
        val expectedUrl = expectedFile.virtualFile?.url ?: return emptyList()
        val moduleRootUrl = expectedUrl.substringBefore(COMMON_MAIN_SOURCE_ROOT_MARKER, missingDelimiterValue = "")
            .takeIf(String::isNotBlank)
            ?: return emptyList()
        val packagePath = expectedFile.packageFqName
            .asString()
            .replace('.', '/')
            .takeIf(String::isNotBlank)

        return buildList {
            jvmActualSourceSets.forEach { sourceSetName ->
                val sourceRootUrl = "$moduleRootUrl/src/$sourceSetName/kotlin"
                val packageDirectoryUrl = packagePath
                    ?.let { "$sourceRootUrl/$it" }
                    ?: sourceRootUrl
                val packageDirectory = VirtualFileManager.getInstance().findFileByUrl(packageDirectoryUrl)
                    ?: return@forEach
                packageDirectory.children
                    .asSequence()
                    .filter { child -> !child.isDirectory && child.extension == "kt" }
                    .mapNotNull { child -> PsiManager.getInstance(project).findFile(child) as? KtFile }
                    .flatMap { file -> file.declarations.filterIsInstance<KtNamedDeclaration>() }
                    .filter { candidate ->
                        candidate.name == expectedName &&
                            candidate.hasModifier(KtTokens.ACTUAL_KEYWORD) &&
                            candidate.isSandboxEligible()
                    }
                    .forEach(::add)
            }
        }.distinctBy { actualDeclaration -> actualDeclaration.declarationKey() }
    }

    private fun KtNamedDeclaration.matchingKoinImplementationDeclarations(): List<KtNamedDeclaration> {
        val interfaceDeclaration = this as? KtClass ?: return emptyList()
        if (!interfaceDeclaration.isInterface()) {
            return emptyList()
        }
        val interfaceName = interfaceDeclaration.name ?: return emptyList()
        val packageDirectory = containingKtFile.virtualFile?.parent ?: return emptyList()
        return packageDirectory.children
            .asSequence()
            .filter { child -> !child.isDirectory && child.extension == "kt" }
            .mapNotNull { child -> PsiManager.getInstance(project).findFile(child) as? KtFile }
            .flatMap { file -> file.declarations.filterIsInstance<KtClass>() }
            .filter { candidate ->
                !candidate.isInterface() &&
                    candidate.hasKoinProviderAnnotation() &&
                    candidate.superTypeNames().contains(interfaceName) &&
                    candidate.isSandboxEligible()
            }
            .distinctBy { candidate -> candidate.declarationKey() }
            .toList()
    }

    private fun KtClass.hasKoinProviderAnnotation(): Boolean {
        return annotationEntries
            .mapNotNull { annotationEntry -> annotationEntry.shortName?.asString() }
            .any(providerAnnotationNames::contains)
    }

    private fun KtClass.superTypeNames(): Set<String> {
        return superTypeListEntries
            .mapNotNull { superTypeEntry ->
                (superTypeEntry.typeReference?.typeElement as? KtUserType)
                    ?.referencedName
                    ?: superTypeEntry.text.substringBefore('<').substringBefore('(').substringAfterLast('.').trim()
            }
            .filter(String::isNotBlank)
            .toSet()
    }

    private fun KtNamedDeclaration.matchingThemeSupportDeclarations(): List<KtNamedDeclaration> {
        val declarationName = name ?: return emptyList()
        if (declarationName !in themeSupportTriggerNames) {
            return emptyList()
        }
        val packageDirectory = containingKtFile.virtualFile?.parent ?: return emptyList()
        return packageDirectory.children
            .asSequence()
            .filter { child -> !child.isDirectory && child.extension == "kt" }
            .mapNotNull { child -> PsiManager.getInstance(project).findFile(child) as? KtFile }
            .flatMap { file -> file.declarations.filterIsInstance<KtNamedDeclaration>() }
            .filter { candidate ->
                candidate.name in themeSupportDeclarationNames &&
                    candidate.isSandboxEligible()
            }
            .distinctBy { candidate -> candidate.declarationKey() }
            .toList()
    }

    private fun resolveReference(reference: KtNameReferenceExpression): PsiElement? {
        return try {
            reference.mainReference.resolve()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (_: Throwable) {
            null
        }
    }

    private fun KtElement.collectReferencedNames(): List<String> {
        return collectDescendantsOfType<KtNameReferenceExpression>()
            .map { reference -> reference.getReferencedName() }
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun PsiElement.sourceTopLevelDeclaration(): KtNamedDeclaration? {
        val declaration = generateSequence(this) { element -> element.parent }
            .takeWhile { element -> element !is KtFile }
            .filterIsInstance<KtNamedDeclaration>()
            .lastOrNull() ?: return null

        return declaration.takeIf { topLevelDeclaration -> topLevelDeclaration.parent is KtFile }
    }

    private fun KtNamedDeclaration.isSandboxEligible(): Boolean {
        val virtualFile = containingKtFile.virtualFile ?: return false
        if (!virtualFile.isInLocalFileSystem || !virtualFile.isValid) {
            return false
        }
        if (virtualFile.path.contains("/.kmp-buddy/preview-sandbox/")) {
            return false
        }
        return ProjectFileIndex.getInstance(project).isInContent(virtualFile)
    }

    private fun KtNamedDeclaration.declarationKey(): DeclarationKey? {
        val name = name ?: return null
        return DeclarationKey(
            fileKey = containingKtFile.fileKey(),
            name = name,
            offset = textRange.startOffset,
        )
    }

    private fun KtFile.fileKey(): String {
        return virtualFile?.path ?: "${packageFqName.asString()}/$name"
    }

    private fun KtFile.sandboxOutputFileName(): String {
        val virtualPath = virtualFile?.path ?: name
        val baseName = name.removeSuffix(".kt").ifBlank { "PreviewSource" }
        return "${baseName.toKotlinIdentifier()}__${stableHash(virtualPath).take(8)}.kt"
    }

    private fun String.isRelevantImport(
        usedNames: Set<String>,
        usesPropertyDelegation: Boolean,
    ): Boolean {
        val importedName = importedShortName() ?: return false
        return importedName == "*" ||
            importedName in usedNames ||
            (usesPropertyDelegation && importedName in propertyDelegateImportNames)
    }

    private fun String.importedShortName(): String? {
        if (!startsWith("import ")) {
            return null
        }
        val importBody = removePrefix("import").trim()
        val aliasMatch = Regex("""\bas\s+([A-Za-z_][A-Za-z0-9_]*)$""").find(importBody)
        if (aliasMatch != null) {
            return aliasMatch.groupValues[1]
        }
        if (importBody.endsWith(".*")) {
            return "*"
        }
        return importBody.substringAfterLast('.').trim().takeIf(String::isNotBlank)
    }

    private fun buildSandboxId(
        previewFile: VirtualFile,
        previewName: String,
        previewOffset: Int,
    ): String {
        val slug = previewName.toKotlinIdentifier().ifBlank { "Preview" }
        return "$slug-${stableHash("${previewFile.path}:$previewOffset").take(12)}"
    }

    private fun String.toKotlinIdentifier(): String {
        val cleaned = mapIndexed { index, char ->
            val valid = if (index == 0) {
                char == '_' || char.isLetter()
            } else {
                char == '_' || char.isLetterOrDigit()
            }
            if (valid) char else '_'
        }.joinToString("")
        return cleaned.trim('_').ifBlank { "PreviewSource" }
    }

    private fun stableHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private val propertyDelegateImportNames = setOf("getValue", "setValue")

    private val providerAnnotationNames = setOf("Single", "Factory")

    private val themeSupportTriggerNames = setOf(
        "LocalAppColorScheme",
        "LocalAppRadius",
        "LocalAppShadows",
        "appColors",
        "radius",
        "shadow",
    )

    private val themeSupportDeclarationNames = setOf(
        "AppColorScheme",
        "AppRadius",
        "AppShadows",
        "LocalAppColorScheme",
        "LocalAppRadius",
        "LocalAppShadows",
        "DefaultLightAppColorScheme",
        "DefaultDarkAppColorScheme",
        "DefaultMaterialLightColorScheme",
        "DefaultMaterialDarkColorScheme",
        "DefaultTypography",
        "Radius",
        "Shadows",
    )

    private const val COMMON_MAIN_SOURCE_ROOT_MARKER = "/src/commonMain/kotlin/"

    private val jvmActualSourceSets = listOf("jvmMain", "desktopMain")
}

private data class DeclarationKey(
    val fileKey: String,
    val name: String,
    val offset: Int,
)
