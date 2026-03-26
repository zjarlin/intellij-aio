package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.intentions.catalog.VersionCatalogDependencyHelper

/**
 * 将 Gradle Kotlin DSL 中基于 `libs.xxx` / `libs.plugins.xxx` 的引用，
 * 批量替换为 `findLibrary("alias").get()` / `findPlugin("alias").get()`。
 */
class GradleKtsCatalogAccessorToFindLibraryIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.catalog.accessor.dynamic")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.preview"))
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        if (file.name == "settings.gradle.kts") {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        return detectTargetCatalogAccessor(project, element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val plan = collectRewritePlan(project)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("intention.catalog.accessor.dynamic.none"),
                GradleBuddyBundle.message("intention.catalog.accessor.dynamic.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(plan),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.title"),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.ok"),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(plan),
            GradleBuddyBundle.message("intention.catalog.accessor.dynamic.title")
        )
    }

    private fun detectTargetCatalogAccessor(project: Project, element: PsiElement): ResolvedAccessor? {
        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        return resolveCatalogAccessor(project, topExpression)
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val basePath = project.basePath ?: return RewritePlan(emptyList(), 0, emptyList())
        val psiManager = PsiManager.getInstance(project)
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in collectTargetGradleKtsFiles(basePath)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val fileCandidates = collectFileCandidates(project, psiFile)
            unresolved += fileCandidates.unresolved

            if (fileCandidates.replacements.isEmpty()) {
                continue
            }

            val originalText = psiFile.text
            val existingVarName = findExistingCatalogVariableName(originalText)
            val variableName = existingVarName ?: chooseCatalogVariableName(psiFile, fileCandidates.replacements)
            val insertOffset = if (existingVarName == null) {
                findCatalogVariableInsertOffset(originalText)
            } else {
                null
            }

            val replacements = fileCandidates.replacements.map { replacement ->
                replacement.copy(newText = buildReplacementText(variableName, replacement.kind, replacement.catalogKey))
            }

            filePlans += FilePlan(
                file = virtualFile,
                variableName = variableName,
                insertOffset = insertOffset,
                replacements = replacements
            )
        }

        return RewritePlan(
            filePlans = filePlans,
            unresolvedCount = unresolved.size,
            unresolvedSamples = unresolved.take(MAX_UNRESOLVED_SAMPLES)
        )
    }

    private fun collectFileCandidates(project: Project, file: PsiFile): FileCandidates {
        val topExpressions = PsiTreeUtil.collectElementsOfType(file, KtDotQualifiedExpression::class.java)
            .map { findTopDotExpression(it) }
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        val replacements = mutableListOf<Replacement>()
        val unresolved = mutableListOf<String>()

        for (expression in topExpressions) {
            val candidate = classifyCatalogAccessor(expression) ?: continue
            val resolved = resolveCatalogAccessor(project, expression)
            if (resolved == null) {
                unresolved += "${file.name}: ${candidate.displayText}"
                continue
            }

            replacements += Replacement(
                range = expression.textRange,
                catalogKey = resolved.catalogKey,
                kind = resolved.kind,
                newText = ""
            )
        }

        return FileCandidates(replacements, unresolved)
    }

    private fun resolveCatalogAccessor(project: Project, expression: KtDotQualifiedExpression): ResolvedAccessor? {
        val candidate = classifyCatalogAccessor(expression) ?: return null

        return when (candidate.kind) {
            CatalogEntryKind.LIBRARY -> {
                val resolved = VersionCatalogDependencyHelper.findCatalogDependencyByAccessor(project, candidate.accessor)
                    ?: return null
                ResolvedAccessor(candidate.kind, resolved.second.key, candidate.displayText)
            }

            CatalogEntryKind.PLUGIN -> {
                val pluginAccessor = candidate.accessor.removePrefix("plugins.")
                val resolved = VersionCatalogDependencyHelper.findCatalogPluginByAccessor(project, pluginAccessor)
                    ?: return null
                ResolvedAccessor(candidate.kind, resolved.second.key, candidate.displayText)
            }
        }
    }

    private fun classifyCatalogAccessor(expression: KtDotQualifiedExpression): CatalogAccessorCandidate? {
        val accessor = extractPlainCatalogAccessor(expression) ?: return null

        if (isSupportedLibraryAccessor(accessor) && isInsideDependencyConfiguration(expression)) {
            return CatalogAccessorCandidate(
                kind = CatalogEntryKind.LIBRARY,
                accessor = accessor,
                displayText = "libs.$accessor"
            )
        }

        if (isSupportedPluginAccessor(accessor) && isInsidePluginAlias(expression)) {
            return CatalogAccessorCandidate(
                kind = CatalogEntryKind.PLUGIN,
                accessor = accessor,
                displayText = "libs.$accessor"
            )
        }

        return null
    }

    private fun buildReplacementText(variableName: String, kind: CatalogEntryKind, catalogKey: String): String {
        return when (kind) {
            CatalogEntryKind.LIBRARY -> "$variableName.findLibrary(\"$catalogKey\").get()"
            CatalogEntryKind.PLUGIN -> "$variableName.findPlugin(\"$catalogKey\").get()"
        }
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(project, GradleBuddyBundle.message("intention.catalog.accessor.dynamic.command"), null, {
            val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)

            for (filePlan in plan.filePlans) {
                val document = documentManager.getDocument(filePlan.file) ?: continue
                var text = document.text
                var replacements = filePlan.replacements

                if (filePlan.insertOffset != null) {
                    val declaration = buildCatalogVariableDeclaration(text, filePlan.insertOffset, filePlan.variableName)
                    text = text.substring(0, filePlan.insertOffset) + declaration + text.substring(filePlan.insertOffset)

                    val shift = declaration.length
                    replacements = replacements.map { replacement ->
                        if (replacement.range.startOffset >= filePlan.insertOffset) {
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

                document.replaceString(0, document.textLength, text)
                psiDocumentManager.commitDocument(document)
                documentManager.saveDocument(document)
            }
        })
    }

    private fun buildConfirmMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val libraryCount = plan.filePlans.sumOf { it.replacements.count { replacement -> replacement.kind == CatalogEntryKind.LIBRARY } }
        val pluginCount = plan.filePlans.sumOf { it.replacements.count { replacement -> replacement.kind == CatalogEntryKind.PLUGIN } }
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }
        val unresolvedLine = if (plan.unresolvedCount > 0) {
            GradleBuddyBundle.message(
                "intention.catalog.accessor.dynamic.confirm.unresolved.line",
                plan.unresolvedCount,
                plan.unresolvedSamples.joinToString(separator = "\n") { "  $it" }
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.catalog.accessor.dynamic.confirm.body",
            fileCount,
            replacementCount,
            if (libraryCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.library.line", libraryCount) else "",
            if (pluginCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.plugin.line", pluginCount) else "",
            if (insertCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.insert.line", insertCount) else "",
            if (fallbackVarCount > 0) GradleBuddyBundle.message("intention.catalog.accessor.dynamic.confirm.fallback.line", fallbackVarCount) else "",
            unresolvedLine
        )
    }

    private fun buildResultMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val libraryCount = plan.filePlans.sumOf { it.replacements.count { replacement -> replacement.kind == CatalogEntryKind.LIBRARY } }
        val pluginCount = plan.filePlans.sumOf { it.replacements.count { replacement -> replacement.kind == CatalogEntryKind.PLUGIN } }
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }
        val extraLines = buildString {
            if (libraryCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.library.line", libraryCount))
            }
            if (pluginCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.plugin.line", pluginCount))
            }
            if (insertCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.insert.line", insertCount))
            }
            if (fallbackVarCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.fallback.line", fallbackVarCount))
            }
            if (plan.unresolvedCount > 0) {
                append(GradleBuddyBundle.message("intention.catalog.accessor.dynamic.result.unresolved.line", plan.unresolvedCount))
            }
        }

        return GradleBuddyBundle.message(
            "intention.catalog.accessor.dynamic.result.body",
            fileCount,
            replacementCount,
            extraLines
        )
    }

    private fun collectTargetGradleKtsFiles(basePath: String): List<VirtualFile> {
        val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return emptyList()
        val files = mutableListOf<VirtualFile>()

        VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    if (file.name in SKIP_DIR_NAMES || file.name.startsWith(".")) {
                        return false
                    }
                    return true
                }

                if (file.name.endsWith(".gradle.kts") && file.name != "settings.gradle.kts") {
                    files += file
                }
                return true
            }
        })

        return files
    }
    private fun findTopDotExpression(expression: KtDotQualifiedExpression): KtDotQualifiedExpression {
        var current = expression
        while (current.parent is KtDotQualifiedExpression) {
            current = current.parent as KtDotQualifiedExpression
        }
        return current
    }

    private fun extractPlainCatalogAccessor(expression: KtDotQualifiedExpression): String? {
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

    private fun isSupportedPluginAccessor(accessor: String): Boolean {
        if (!accessor.startsWith("plugins.")) {
            return false
        }
        return !accessor.contains(".javaClass")
    }

    private fun isInsideDependencyConfiguration(expression: KtDotQualifiedExpression): Boolean {
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

    private fun isInsidePluginAlias(expression: KtDotQualifiedExpression): Boolean {
        var current: PsiElement? = expression.parent
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "alias") {
                return isInsidePluginsBlock(current)
            }
            current = current.parent
        }
        return false
    }

    private fun isInsidePluginsBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "plugins") {
                return true
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
            if (!isSupportedLibraryAccessor(accessor) && !isSupportedPluginAccessor(accessor) && !accessor.startsWith("versions.") && !accessor.startsWith("bundles.")) {
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

    private enum class CatalogEntryKind {
        LIBRARY,
        PLUGIN
    }

    private data class CatalogAccessorCandidate(
        val kind: CatalogEntryKind,
        val accessor: String,
        val displayText: String
    )

    private data class ResolvedAccessor(
        val kind: CatalogEntryKind,
        val catalogKey: String,
        val displayText: String
    )

    private data class FileCandidates(
        val replacements: List<Replacement>,
        val unresolved: List<String>
    )

    private data class FilePlan(
        val file: VirtualFile,
        val variableName: String,
        val insertOffset: Int?,
        val replacements: List<Replacement>
    )

    private data class RewritePlan(
        val filePlans: List<FilePlan>,
        val unresolvedCount: Int,
        val unresolvedSamples: List<String>
    )

    private data class Replacement(
        val range: TextRange,
        val catalogKey: String,
        val kind: CatalogEntryKind,
        val newText: String
    )

    companion object {
        private val DEPENDENCY_CONFIGURATIONS = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
            "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
            "debugImplementation", "releaseImplementation",
            "kapt", "ksp", "annotationProcessor", "lintChecks"
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
        private const val MAX_UNRESOLVED_SAMPLES = 5

        private val FALLBACK_CATALOG_VAR_NAMES = listOf(
            "catalogLibs",
            "versionCatalogLibs",
            "resolvedLibs"
        )
    }
}
