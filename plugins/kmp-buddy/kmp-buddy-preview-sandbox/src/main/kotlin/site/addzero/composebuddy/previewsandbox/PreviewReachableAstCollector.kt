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
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
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
        val referencedNames = collectReferencedNames().toSet()
        collectDescendantsOfType<KtNameReferenceExpression>().forEach { reference ->
            val resolved = resolveReference(reference) ?: return@forEach
            val declaration = resolved.sourceTopLevelDeclaration() ?: return@forEach
            if (!declaration.isSandboxEligible()) {
                return@forEach
            }
            val key = declaration.declarationKey() ?: return@forEach
            reachable.putIfAbsent(key, declaration)
        }

        containingKtFile.declarations
            .filterIsInstance<KtNamedDeclaration>()
            .filter { declaration ->
                declaration.hasModifier(KtTokens.EXPECT_KEYWORD) &&
                    declaration.name in referencedNames &&
                    declaration.isSandboxEligible()
            }
            .forEach { declaration ->
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
        val packagePath = expectedFile.packageFqName
            .asString()
            .replace('.', '/')
            .takeIf(String::isNotBlank)

        return expectedFile.jvmActualPackageDirectories(packagePath)
            .flatMap { packageDirectory ->
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
            }
            .distinctBy { actualDeclaration -> actualDeclaration.declarationKey() }
    }

    private fun KtFile.jvmActualPackageDirectories(packagePath: String?): List<VirtualFile> {
        return jvmActualSourceRoots()
            .mapNotNull { sourceRoot ->
                packagePath
                    ?.let(sourceRoot::findFileByRelativePath)
                    ?: sourceRoot
            }
            .distinctBy(VirtualFile::getUrl)
    }

    private fun KtFile.jvmActualSourceRoots(): List<VirtualFile> {
        val sourceRootCandidates = buildList {
            virtualFile?.commonMainModuleRoots()?.forEach { moduleRoot ->
                jvmActualSourceSets.forEach { sourceSetName ->
                    moduleRoot.findFileByRelativePath("src/$sourceSetName/kotlin")?.let(::add)
                }
            }

            val expectedUrl = virtualFile?.url
                ?: return@buildList
            val moduleRootUrl = expectedUrl.substringBefore(COMMON_MAIN_SOURCE_ROOT_MARKER, missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
                ?: return@buildList
            jvmActualSourceSets.forEach { sourceSetName ->
                val sourceRootUrl = "$moduleRootUrl/src/$sourceSetName/kotlin"
                VirtualFileManager.getInstance().findFileByUrl(sourceRootUrl)?.let(::add)
            }
        }

        return sourceRootCandidates.distinctBy(VirtualFile::getUrl)
    }

    private fun VirtualFile.commonMainModuleRoots(): List<VirtualFile> {
        return generateSequence(this) { file -> file.parent }
            .filter { file ->
                file.name == "kotlin" &&
                    file.parent?.name == "commonMain" &&
                    file.parent?.parent?.name == "src"
            }
            .mapNotNull { commonKotlinRoot -> commonKotlinRoot.parent?.parent?.parent }
            .distinctBy(VirtualFile::getUrl)
            .toList()
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
        val expressionNames = collectDescendantsOfType<KtSimpleNameExpression>()
            .map { reference -> reference.getReferencedName() }
        val textNames = kotlinIdentifierRegex.findAll(text.withoutKotlinComments())
            .map { match -> match.value }
            .filterNot(kotlinKeywords::contains)
        return (expressionNames + textNames)
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun String.withoutKotlinComments(): String {
        val result = StringBuilder(length)
        var index = 0
        var blockDepth = 0
        var inLineComment = false
        var inString = false
        var inTripleString = false
        var escaped = false

        while (index < length) {
            val char = this[index]
            val next = getOrNull(index + 1)
            val nextTwo = getOrNull(index + 2)

            when {
                inLineComment -> {
                    if (char == '\n' || char == '\r') {
                        inLineComment = false
                        result.append(char)
                    } else {
                        result.append(' ')
                    }
                }

                blockDepth > 0 -> {
                    when {
                        char == '/' && next == '*' -> {
                            blockDepth++
                            result.append("  ")
                            index++
                        }

                        char == '*' && next == '/' -> {
                            blockDepth--
                            result.append("  ")
                            index++
                        }

                        char == '\n' || char == '\r' -> result.append(char)
                        else -> result.append(' ')
                    }
                }

                inTripleString -> {
                    if (char == '"' && next == '"' && nextTwo == '"') {
                        inTripleString = false
                        result.append("\"\"\"")
                        index += 2
                    } else {
                        result.append(char)
                    }
                }

                inString -> {
                    result.append(char)
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                }

                char == '"' && next == '"' && nextTwo == '"' -> {
                    inTripleString = true
                    result.append("\"\"\"")
                    index += 2
                }

                char == '"' -> {
                    inString = true
                    result.append(char)
                }

                char == '/' && next == '/' -> {
                    inLineComment = true
                    result.append("  ")
                    index++
                }

                char == '/' && next == '*' -> {
                    blockDepth = 1
                    result.append("  ")
                    index++
                }

                else -> result.append(char)
            }

            index++
        }

        return result.toString()
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

    private val kotlinIdentifierRegex = Regex("""[A-Za-z_\p{L}][A-Za-z0-9_\p{L}\p{N}]*""")

    private val kotlinKeywords = setOf(
        "abstract",
        "actual",
        "annotation",
        "as",
        "break",
        "by",
        "catch",
        "class",
        "companion",
        "const",
        "constructor",
        "continue",
        "crossinline",
        "data",
        "do",
        "dynamic",
        "else",
        "enum",
        "expect",
        "external",
        "false",
        "field",
        "file",
        "final",
        "finally",
        "for",
        "fun",
        "get",
        "if",
        "import",
        "in",
        "infix",
        "init",
        "inline",
        "inner",
        "interface",
        "internal",
        "is",
        "it",
        "lateinit",
        "noinline",
        "null",
        "object",
        "open",
        "operator",
        "out",
        "override",
        "package",
        "param",
        "private",
        "property",
        "protected",
        "public",
        "receiver",
        "reified",
        "return",
        "sealed",
        "set",
        "setparam",
        "super",
        "suspend",
        "tailrec",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "val",
        "var",
        "vararg",
        "when",
        "where",
        "while",
    )

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
