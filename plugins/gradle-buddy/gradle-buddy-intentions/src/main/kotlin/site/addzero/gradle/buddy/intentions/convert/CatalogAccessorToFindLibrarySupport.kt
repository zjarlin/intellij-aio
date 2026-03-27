package site.addzero.gradle.buddy.intentions.convert

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import site.addzero.gradle.buddy.intentions.catalog.VersionCatalogDependencyHelper
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots

internal object CatalogAccessorToFindLibrarySupport {

    fun isTargetGradleKtsFile(file: PsiFile): Boolean {
        return file.name.endsWith(".gradle.kts") && file.name != "settings.gradle.kts"
    }

    fun containsCatalogAccessorText(text: String): Boolean {
        return DEPENDENCY_LIBS_CALL_REGEX.findAll(text).any { match ->
            val accessor = match.groupValues[2].removePrefix("libs.")
            isSupportedLibraryAccessor(accessor)
        }
    }

    fun detectTargetReplacement(project: Project, element: PsiElement): Replacement? {
        val candidate = detectCatalogAccessorCandidate(element) ?: return null
        val resolved = resolveCatalogAccessor(project, candidate.accessor, candidate.expression) ?: return null
        return Replacement(
            range = candidate.expression.textRange,
            catalogKey = resolved.catalogKey,
            newText = ""
        )
    }

    fun collectFileCandidates(project: Project, file: PsiFile): FileCandidates {
        val replacements = mutableListOf<Replacement>()
        val unresolved = mutableListOf<String>()
        val seenRanges = linkedSetOf<Pair<Int, Int>>()

        val callExpressions = PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        for (callExpression in callExpressions) {
            val candidate = classifyCatalogAccessor(callExpression) ?: continue
            val resolved = resolveCatalogAccessor(project, candidate.accessor, candidate.expression)
            if (resolved == null) {
                unresolved += "${file.name}: ${candidate.displayText}"
                continue
            }

            val range = candidate.expression.textRange
            if (seenRanges.add(range.startOffset to range.endOffset)) {
                replacements += Replacement(
                    range = range,
                    catalogKey = resolved.catalogKey,
                    newText = ""
                )
            }
        }

        val fileText = file.text
        for (match in DEPENDENCY_LIBS_CALL_REGEX.findAll(fileText)) {
            val accessorText = match.groupValues[2]
            val accessor = accessorText.removePrefix("libs.")
            if (!isSupportedLibraryAccessor(accessor)) {
                continue
            }

            val resolved = resolveCatalogAccessor(project, accessor, accessorText)
            if (resolved == null) {
                unresolved += "${file.name}: $accessorText"
                continue
            }

            val group = match.groups[2] ?: continue
            val range = TextRange(group.range.first, group.range.last + 1)
            if (seenRanges.add(range.startOffset to range.endOffset)) {
                replacements += Replacement(
                    range = range,
                    catalogKey = resolved.catalogKey,
                    newText = ""
                )
            }
        }

        return FileCandidates(replacements.sortedBy { it.range.startOffset }, unresolved.distinct())
    }

    fun buildRewritePlan(file: PsiFile, replacements: List<Replacement>): FileRewritePlan? {
        if (replacements.isEmpty()) {
            return null
        }

        val originalText = file.text
        val existingVarName = findExistingCatalogVariableName(originalText)
        val variableName = existingVarName ?: chooseCatalogVariableName(file, replacements)
        val insertOffset = if (existingVarName == null) {
            findCatalogVariableInsertOffset(originalText)
        } else {
            null
        }

        return FileRewritePlan(
            variableName = variableName,
            insertOffset = insertOffset,
            replacements = replacements.map { replacement ->
                replacement.copy(newText = buildReplacementText(variableName, replacement.catalogKey))
            }
        )
    }

    fun rewriteText(originalText: String, plan: FileRewritePlan): String {
        var text = originalText
        var replacements = plan.replacements

        if (plan.insertOffset != null) {
            val declaration = buildCatalogVariableDeclaration(text, plan.insertOffset, plan.variableName)
            text = text.substring(0, plan.insertOffset) + declaration + text.substring(plan.insertOffset)

            val shift = declaration.length
            replacements = replacements.map { replacement ->
                if (replacement.range.startOffset >= plan.insertOffset) {
                    replacement.copy(
                        range = TextRange(
                            replacement.range.startOffset + shift,
                            replacement.range.endOffset + shift
                        )
                    )
                } else {
                    replacement
                }
            }
        }

        for (replacement in replacements.sortedByDescending { it.range.startOffset }) {
            text = text.substring(0, replacement.range.startOffset) +
                replacement.newText +
                text.substring(replacement.range.endOffset)
        }

        return text
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

    private fun detectCatalogAccessorCandidate(element: PsiElement): CatalogAccessorCandidate? {
        findEnclosingCatalogCallCandidate(element)?.let { return it }

        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        val accessor = extractPlainCatalogAccessor(topExpression) ?: return null
        return classifyCatalogAccessor(accessor, topExpression)
    }

    private fun findEnclosingCatalogCallCandidate(element: PsiElement): CatalogAccessorCandidate? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression) {
                classifyCatalogAccessor(current)?.let { return it }
            }
            current = current.parent
        }
        return null
    }

    private fun classifyCatalogAccessor(callExpression: KtCallExpression): CatalogAccessorCandidate? {
        val callee = callExpression.calleeExpression?.text ?: return null
        val argumentExpression = callExpression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        val accessor = extractPlainCatalogAccessor(argumentExpression) ?: return null

        return when {
            callee in DEPENDENCY_CONFIGURATIONS -> classifyCatalogAccessor(accessor, argumentExpression)
            else -> null
        }
    }

    private fun classifyCatalogAccessor(accessor: String, expression: PsiElement): CatalogAccessorCandidate? {
        val displayText = "libs.$accessor"

        if (isSupportedLibraryAccessor(accessor) && isInsideDependencyConfiguration(expression)) {
            return CatalogAccessorCandidate(
                accessor = accessor,
                displayText = displayText,
                expression = expression
            )
        }

        return null
    }

    private fun resolveCatalogAccessor(
        project: Project,
        accessor: String,
        expression: PsiElement
    ): ResolvedAccessor? {
        val candidate = classifyCatalogAccessor(accessor, expression) ?: return null
        return resolveCatalogAccessor(project, candidate.accessor, candidate.displayText)
    }

    private fun resolveCatalogAccessor(
        project: Project,
        accessor: String,
        displayText: String
    ): ResolvedAccessor? {
        val resolved = VersionCatalogDependencyHelper.findCatalogDependencyByAccessor(project, accessor)
            ?: return null
        return ResolvedAccessor(resolved.second.key, displayText)
    }

    private fun buildReplacementText(variableName: String, catalogKey: String): String {
        return "$variableName.findLibrary(\"$catalogKey\").get()"
    }

    private fun findTopDotExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var current = expression
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent as KtDotQualifiedExpression
        }
        return current
    }

    private fun extractPlainCatalogAccessor(expression: KtExpression): String? {
        val text = expression.text.trim()
        val match = PLAIN_LIBS_ACCESSOR_REGEX.matchEntire(text) ?: return null
        return match.groupValues[1]
    }

    private fun isSupportedLibraryAccessor(accessor: String): Boolean {
        if (accessor.startsWith("plugins.") || accessor.startsWith("versions.") || accessor.startsWith("bundles.")) {
            return false
        }
        if (accessor.startsWith("javaClass") || accessor.contains(".javaClass")) {
            return false
        }
        return true
    }

    private fun isInsideDependencyConfiguration(expression: PsiElement): Boolean {
        var current: PsiElement? = expression.parent
        while (current != null) {
            if (current is KtCallExpression) {
                val callee = current.calleeExpression?.text
                if (callee in DEPENDENCY_CONFIGURATIONS) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun findExistingCatalogVariableName(text: String): String? {
        return EXISTING_CATALOG_VAR_REGEX.find(text)?.groupValues?.get(1)
    }

    private fun chooseCatalogVariableName(file: PsiFile, replacements: List<Replacement>): String {
        if (!hasRemainingGeneratedLibsUsages(file, replacements)) {
            return DEFAULT_CATALOG_VAR_NAME
        }

        val text = file.text
        for (candidate in FALLBACK_CATALOG_VAR_NAMES) {
            val regex = Regex("""\b${Regex.escape(candidate)}\b""")
            if (!regex.containsMatchIn(text)) {
                return candidate
            }
        }
        return "catalogLibsAuto"
    }

    private fun hasRemainingGeneratedLibsUsages(file: PsiFile, replacements: List<Replacement>): Boolean {
        val replacementRanges = replacements.map { it.range }
        val topExpressions = PsiTreeUtil.collectElementsOfType(file, KtDotQualifiedExpression::class.java)
            .map { findTopDotExpression(it) }
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        for (expression in topExpressions) {
            val accessor = extractPlainCatalogAccessor(expression) ?: continue
            if (!expression.text.startsWith("libs.")) {
                continue
            }
            if (!isSupportedLibraryAccessor(accessor) && !accessor.startsWith("versions.") && !accessor.startsWith("bundles.")) {
                continue
            }

            val range = expression.textRange
            if (replacementRanges.any { it.startOffset == range.startOffset && it.endOffset == range.endOffset }) {
                continue
            }
            return true
        }
        return false
    }

    private fun findCatalogVariableInsertOffset(text: String): Int {
        val pluginsMatch = PLUGINS_BLOCK_REGEX.find(text)
        if (pluginsMatch != null) {
            val openBraceIndex = text.indexOf('{', pluginsMatch.range.first)
            val closeBraceIndex = findMatchingBrace(text, openBraceIndex)
            if (closeBraceIndex >= 0) {
                return advancePastLineBreak(text, closeBraceIndex + 1)
            }
        }

        val packageOrImport = PACKAGE_OR_IMPORT_REGEX.findAll(text).lastOrNull()
        if (packageOrImport != null) {
            return advancePastLineBreak(text, packageOrImport.range.last + 1)
        }

        return 0
    }

    private fun buildCatalogVariableDeclaration(text: String, insertOffset: Int, variableName: String): String {
        val declaration = "val $variableName = versionCatalogs.named(\"libs\")"
        return buildString {
            if (insertOffset > 0 && text[insertOffset - 1] != '\n') {
                append('\n')
            }
            append(declaration)
            append('\n')
            if (insertOffset >= text.length || text[insertOffset] != '\n') {
                append('\n')
            }
        }
    }

    private fun advancePastLineBreak(text: String, offset: Int): Int {
        var current = offset
        if (current < text.length && text[current] == '\r') {
            current++
        }
        if (current < text.length && text[current] == '\n') {
            current++
        }
        return current
    }

    private fun findMatchingBrace(text: String, openBraceIndex: Int): Int {
        if (openBraceIndex !in text.indices || text[openBraceIndex] != '{') {
            return -1
        }

        var depth = 0
        for (index in openBraceIndex until text.length) {
            when (text[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }

    data class FileCandidates(
        val replacements: List<Replacement>,
        val unresolved: List<String>
    )

    data class FileRewritePlan(
        val variableName: String,
        val insertOffset: Int?,
        val replacements: List<Replacement>
    )

    data class Replacement(
        val range: TextRange,
        val catalogKey: String,
        val newText: String
    )

    private data class CatalogAccessorCandidate(
        val accessor: String,
        val displayText: String,
        val expression: PsiElement
    )

    private data class ResolvedAccessor(
        val catalogKey: String,
        val displayText: String
    )

    private val DEPENDENCY_CONFIGURATIONS = setOf(
        "implementation", "api", "compileOnly", "runtimeOnly",
        "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
        "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
        "debugImplementation", "releaseImplementation",
        "kapt", "ksp", "annotationProcessor", "lintChecks"
    )

    private val DEPENDENCY_LIBS_CALL_REGEX = Regex(
        """\b(${DEPENDENCY_CONFIGURATIONS.joinToString("|")})\s*\(\s*(libs\.[A-Za-z0-9_.]+)\s*\)"""
    )

    private val SKIP_DIR_NAMES = setOf(
        "build", "out", ".gradle", ".idea", "node_modules", "target", ".git"
    )

    private val PLAIN_LIBS_ACCESSOR_REGEX = Regex("""^libs\.([A-Za-z0-9_.]+)$""")
    private val EXISTING_CATALOG_VAR_REGEX = Regex(
        """(?m)^\s*val\s+([A-Za-z_][A-Za-z0-9_]*)\s*=\s*versionCatalogs\.named\("libs"\)\s*$"""
    )
    private val PLUGINS_BLOCK_REGEX = Regex("""(?m)^\s*plugins\s*\{""")
    private val PACKAGE_OR_IMPORT_REGEX = Regex("""(?m)^(package\s+.+|import\s+.+)\s*$""")

    private const val DEFAULT_CATALOG_VAR_NAME = "libs"

    private val FALLBACK_CATALOG_VAR_NAMES = listOf(
        "catalogLibs",
        "versionCatalogLibs",
        "resolvedLibs"
    )
}
