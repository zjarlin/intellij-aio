package site.addzero.gradle.buddy.intentions.convert

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots
import site.addzero.gradle.catalog.CatalogReferenceScanner

internal object FindLibraryToCatalogAccessorSupport {

    fun isTargetGradleKtsFile(file: PsiFile): Boolean {
        return file.name.endsWith(".gradle.kts") && file.name != "settings.gradle.kts"
    }

    fun containsDynamicFindLibraryText(text: String): Boolean {
        return DYNAMIC_FIND_LIBRARY_REGEX.containsMatchIn(text)
    }

    fun loadAvailableLibraryAliases(project: Project): Set<String> {
        return CatalogReferenceScanner(project)
            .scanAllCatalogs()[LIBS_CATALOG_NAME]
            .orEmpty()
            .map(::canonicalizeCatalogKey)
            .toSet()
    }

    fun detectTargetReplacement(
        element: PsiElement,
        availableLibraryAliases: Set<String>
    ): Replacement? {
        val stringExpression = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression::class.java, false)
            ?: return null
        val callInfo = resolveDynamicFindLibraryCall(stringExpression) ?: return null
        if (callInfo.catalogName != LIBS_CATALOG_NAME) {
            return null
        }
        if (!availableLibraryAliases.contains(canonicalizeCatalogKey(callInfo.alias))) {
            return null
        }
        val replaceTarget = findReplaceTargetExpression(stringExpression) ?: return null
        return Replacement(
            range = replaceTarget.textRange,
            alias = callInfo.alias,
            newText = buildStrongAccessor(callInfo.alias)
        )
    }

    fun collectFileCandidates(file: PsiFile, availableLibraryAliases: Set<String>): FileCandidates {
        val replacements = mutableListOf<Replacement>()
        val unresolved = mutableListOf<String>()
        val seenRanges = linkedSetOf<Pair<Int, Int>>()

        val stringExpressions = PsiTreeUtil.collectElementsOfType(file, KtStringTemplateExpression::class.java)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        for (stringExpression in stringExpressions) {
            val callInfo = resolveDynamicFindLibraryCall(stringExpression) ?: continue
            if (callInfo.catalogName != LIBS_CATALOG_NAME) {
                continue
            }

            if (!availableLibraryAliases.contains(canonicalizeCatalogKey(callInfo.alias))) {
                unresolved += "${file.name}: ${callInfo.alias}"
                continue
            }

            val replaceTarget = findReplaceTargetExpression(stringExpression) ?: continue
            val range = replaceTarget.textRange
            if (seenRanges.add(range.startOffset to range.endOffset)) {
                replacements += Replacement(
                    range = range,
                    alias = callInfo.alias,
                    newText = buildStrongAccessor(callInfo.alias)
                )
            }
        }

        return FileCandidates(
            replacements = replacements.sortedBy { it.range.startOffset },
            unresolved = unresolved.distinct(),
            hasShadowingLibsDeclaration = hasShadowingLibsDeclaration(file.text)
        )
    }

    fun rewriteText(
        originalText: String,
        replacements: List<Replacement>,
        cleanupShadowingDeclaration: Boolean
    ): String {
        var text = originalText

        for (replacement in replacements.sortedByDescending { it.range.startOffset }) {
            text = text.substring(0, replacement.range.startOffset) +
                replacement.newText +
                text.substring(replacement.range.endOffset)
        }

        return if (cleanupShadowingDeclaration) {
            cleanupShadowingLibsDeclaration(text)
        } else {
            text
        }
    }

    fun hasShadowingLibsDeclaration(text: String): Boolean {
        return SHADOWING_LIBS_DECLARATION_REGEX.containsMatchIn(text)
    }

    fun collectTargetGradleKtsFiles(project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val seen = linkedSetOf<String>()

        for (baseDir in GradleProjectRoots.collectSearchRoots(project)) {
            VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        if (file.name in SKIP_DIR_NAMES || file.name.startsWith('.')) {
                            return false
                        }
                        return true
                    }

                    if (file.name.endsWith(".gradle.kts") && file.name != "settings.gradle.kts" && seen.add(file.path)) {
                        files += file
                    }
                    return true
                }
            })
        }

        return files
    }

    private fun resolveDynamicFindLibraryCall(stringExpression: KtStringTemplateExpression): DynamicLibraryCallInfo? {
        val alias = extractLiteralString(stringExpression) ?: return null
        val findLibraryCall = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java, false)
            ?.takeIf { it.calleeExpression?.text == "findLibrary" }
            ?: return null
        if (findLibraryCall.valueArguments.singleOrNull()?.getArgumentExpression() != stringExpression) {
            return null
        }

        val receiverExpression = (findLibraryCall.parent as? KtDotQualifiedExpression)?.receiverExpression ?: return null
        val catalogName = resolveCatalogName(
            receiverExpression = receiverExpression,
            file = stringExpression.containingFile as? KtFile,
            contextOffset = stringExpression.textRange.startOffset
        ) ?: return null

        return DynamicLibraryCallInfo(
            catalogName = catalogName,
            alias = alias
        )
    }

    private fun findReplaceTargetExpression(stringExpression: KtStringTemplateExpression): KtDotQualifiedExpression? {
        val findLibraryCall = PsiTreeUtil.getParentOfType(stringExpression, KtCallExpression::class.java, false)
            ?.takeIf { it.calleeExpression?.text == "findLibrary" }
            ?: return null
        var current: PsiElement = findLibraryCall

        while (current.parent is KtDotQualifiedExpression) {
            val parent = current.parent as KtDotQualifiedExpression
            val selectorCall = parent.selectorExpression as? KtCallExpression
            if (selectorCall?.calleeExpression?.text == "get") {
                return parent
            }
            current = parent
        }

        return null
    }

    private fun extractLiteralString(expression: KtStringTemplateExpression): String? {
        if (expression.entries.isEmpty()) {
            return null
        }
        if (expression.entries.any { it !is KtLiteralStringTemplateEntry }) {
            return null
        }
        return expression.entries
            .filterIsInstance<KtLiteralStringTemplateEntry>()
            .joinToString(separator = "") { it.text }
            .takeIf { it.isNotBlank() }
    }

    private fun resolveCatalogName(
        receiverExpression: KtExpression,
        file: KtFile?,
        contextOffset: Int
    ): String? {
        return when (receiverExpression) {
            is KtNameReferenceExpression -> resolveCatalogNameFromVariable(
                variableName = receiverExpression.getReferencedName(),
                file = file,
                contextOffset = contextOffset
            )
            else -> resolveCatalogNameFromExpression(receiverExpression)
        }
    }

    private fun resolveCatalogNameFromVariable(
        variableName: String,
        file: KtFile?,
        contextOffset: Int
    ): String? {
        if (file == null) {
            return variableName.takeIf { it == LIBS_CATALOG_NAME }
        }

        val matchingProperty = PsiTreeUtil.collectElementsOfType(file, KtProperty::class.java)
            .asSequence()
            .filter { it.name == variableName && it.textRange.startOffset <= contextOffset }
            .maxByOrNull { it.textRange.startOffset }

        if (matchingProperty != null) {
            return resolveCatalogNameFromExpression(matchingProperty.initializer)
                ?: variableName.takeIf { it == LIBS_CATALOG_NAME }
        }

        return variableName.takeIf { it == LIBS_CATALOG_NAME }
    }

    private fun resolveCatalogNameFromExpression(expression: KtExpression?): String? {
        val text = expression?.text ?: return null
        return VERSION_CATALOG_NAMED_REGEX.find(text)?.groupValues?.getOrNull(1)
    }

    private fun buildStrongAccessor(alias: String): String {
        val accessor = alias.replace('-', '.').replace('_', '.')
        return "$LIBS_CATALOG_NAME.$accessor"
    }

    private fun canonicalizeCatalogKey(key: String): String {
        return key
            .trim('"', '\'')
            .replace('-', '.')
            .replace('_', '.')
            .split('.')
            .flatMap { segment ->
                segment
                    .replace(Regex("([a-z0-9])([A-Z])"), "$1.$2")
                    .split('.')
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = ".") { it.lowercase() }
    }

    private fun cleanupShadowingLibsDeclaration(text: String): String {
        if (!SHADOWING_LIBS_DECLARATION_REGEX.containsMatchIn(text)) {
            return text
        }

        return if (REMAINING_DYNAMIC_LIBS_USAGE_REGEX.containsMatchIn(text)) {
            val fallbackName = chooseFallbackVarName(text)
            val declarationMatch = SHADOWING_LIBS_DECLARATION_REGEX.find(text) ?: return text
            val indent = declarationMatch.groupValues[1]
            val newline = declarationMatch.groupValues[3]
            val replacement = "${indent}val $fallbackName = versionCatalogs.named(\"libs\")$newline"
            text
                .replaceRange(declarationMatch.range, replacement)
                .replace(REMAINING_DYNAMIC_LIBS_USAGE_REGEX, "$fallbackName.find$1(")
        } else {
            text.replaceFirst(SHADOWING_LIBS_DECLARATION_REGEX, "")
        }
    }

    private fun chooseFallbackVarName(text: String): String {
        for (candidate in FALLBACK_DYNAMIC_VAR_NAMES) {
            val regex = Regex("""\b${Regex.escape(candidate)}\b""")
            if (!regex.containsMatchIn(text)) {
                return candidate
            }
        }
        return "catalogLibsDyn"
    }

    data class Replacement(
        val range: TextRange,
        val alias: String,
        val newText: String
    )

    data class FileCandidates(
        val replacements: List<Replacement>,
        val unresolved: List<String>,
        val hasShadowingLibsDeclaration: Boolean
    )

    private data class DynamicLibraryCallInfo(
        val catalogName: String,
        val alias: String
    )

    private const val LIBS_CATALOG_NAME = "libs"

    private val SKIP_DIR_NAMES = setOf(
        "build", "out", ".gradle", ".idea", "node_modules", "target", ".git"
    )

    private val DYNAMIC_FIND_LIBRARY_REGEX = Regex(
        """\b[A-Za-z_][A-Za-z0-9_]*\s*\.\s*findLibrary\s*\(\s*"[^"]+"\s*\)\s*\.\s*get\s*\(\s*\)"""
    )

    private val SHADOWING_LIBS_DECLARATION_REGEX = Regex(
        """(?m)^([ \t]*)(val|var)\s+libs\s*=\s*versionCatalogs\.named\("libs"\)\s*(\R?)"""
    )

    private val REMAINING_DYNAMIC_LIBS_USAGE_REGEX = Regex(
        """\blibs\s*\.\s*find(Library|Plugin|Bundle|Version)\s*\("""
    )

    private val FALLBACK_DYNAMIC_VAR_NAMES = listOf(
        "catalogLibs",
        "versionCatalogLibs",
        "resolvedLibs"
    )

    private val VERSION_CATALOG_NAMED_REGEX =
        Regex("""versionCatalogs\s*\.\s*named\s*\(\s*"([^"]+)"\s*\)""")
}
