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
import org.jetbrains.kotlin.psi.KtExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle
import site.addzero.gradle.buddy.intentions.catalog.VersionCatalogDependencyHelper
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots

/**
 * 将 Gradle Kotlin DSL 中依赖里的 `libs.xxx` 强类型访问器，
 * 批量替换为 `findLibrary("alias").get()`。
 *
 * plugins {} 块中的 `alias(libs.plugins.xxx)` 暂不转换。
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

        val element = file.findElementAt(editor.caretModel.offset)
        if (element != null && detectCatalogAccessorCandidate(element) != null) {
            return true
        }

        val fileCandidates = collectFileCandidates(project, file)
        if (fileCandidates.replacements.isNotEmpty()) {
            return true
        }

        // 兜底：即使当前还没成功解析到 catalog key，也先把意图展示出来，
        // 让用户能触发批量扫描，并在后续弹窗里看到更明确的结果或错误提示。
        return containsCatalogAccessorText(file.text)
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
        findEnclosingCatalogCall(project, element)?.let { return it }

        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        return resolveCatalogAccessor(project, topExpression.text, topExpression)
    }

    private fun detectCatalogAccessorCandidate(element: PsiElement): CatalogAccessorCandidate? {
        findEnclosingCatalogCallCandidate(element)?.let { return it }

        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        val accessor = extractPlainCatalogAccessor(topExpression) ?: return null
        return classifyCatalogAccessor(accessor, topExpression)
    }

    private fun findEnclosingCatalogCall(project: Project, element: PsiElement): ResolvedAccessor? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression) {
                resolveCatalogAccessorFromCall(project, current)?.let { return it }
            }
            current = current.parent
        }
        return null
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

    private fun resolveCatalogAccessorFromCall(project: Project, callExpression: KtCallExpression): ResolvedAccessor? {
        val callee = callExpression.calleeExpression?.text ?: return null
        val argumentExpression = callExpression.valueArguments.singleOrNull()?.getArgumentExpression() ?: return null
        val accessorText = extractPlainCatalogAccessor(argumentExpression) ?: return null

        return when {
            callee in DEPENDENCY_CONFIGURATIONS -> resolveCatalogAccessor(project, accessorText, argumentExpression)
            else -> null
        }
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in collectTargetGradleKtsFiles(project)) {
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
                    kind = resolved.kind,
                    newText = ""
                )
            }
        }

        // 兜底：部分 Gradle Kotlin DSL 场景下 PSI 结构不稳定，但源码文本仍然是确定的。
        // 这里补一轮基于文本的扫描，确保强类型 libs.xxx 不会漏掉。
        val fileText = file.text
        for (match in DEPENDENCY_LIBS_CALL_REGEX.findAll(fileText)) {
            val accessorText = match.groupValues[2]
            val accessor = accessorText.removePrefix("libs.")
            if (!isSupportedLibraryAccessor(accessor)) {
                continue
            }

            val resolved = resolveCatalogAccessor(project, accessor, CatalogEntryKind.LIBRARY, accessorText)
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
                    kind = resolved.kind,
                    newText = ""
                )
            }
        }

        return FileCandidates(replacements.sortedBy { it.range.startOffset }, unresolved.distinct())
    }

    private fun resolveCatalogAccessor(
        project: Project,
        accessor: String,
        expression: PsiElement
    ): ResolvedAccessor? {
        val candidate = classifyCatalogAccessor(accessor, expression) ?: return null
        return resolveCatalogAccessor(project, candidate.accessor, candidate.kind, candidate.displayText)
    }

    private fun resolveCatalogAccessor(
        project: Project,
        accessor: String,
        kind: CatalogEntryKind,
        displayText: String
    ): ResolvedAccessor? {
        return when (kind) {
            CatalogEntryKind.LIBRARY -> {
                val resolved = VersionCatalogDependencyHelper.findCatalogDependencyByAccessor(project, accessor)
                    ?: return null
                ResolvedAccessor(kind, resolved.second.key, displayText)
            }

            CatalogEntryKind.PLUGIN -> {
                val pluginAccessor = accessor.removePrefix("plugins.")
                val resolved = VersionCatalogDependencyHelper.findCatalogPluginByAccessor(project, pluginAccessor)
                    ?: return null
                ResolvedAccessor(kind, resolved.second.key, displayText)
            }
        }
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
                kind = CatalogEntryKind.LIBRARY,
                accessor = accessor,
                displayText = displayText,
                expression = expression
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

    private fun collectTargetGradleKtsFiles(project: Project): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        val seen = linkedSetOf<String>()

        for (baseDir in GradleProjectRoots.collectSearchRoots(project)) {
            VfsUtilCore.visitChildrenRecursively(baseDir, object : VirtualFileVisitor<Void>() {
                override fun visitFile(file: VirtualFile): Boolean {
                    if (file.isDirectory) {
                        if (file.name in SKIP_DIR_NAMES || file.name.startsWith(".")) {
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

    private fun isSupportedPluginAccessor(accessor: String): Boolean {
        if (!accessor.startsWith("plugins.")) {
            return false
        }
        return !accessor.contains(".javaClass")
    }

    private fun containsCatalogAccessorText(text: String): Boolean {
        return DEPENDENCY_LIBS_CALL_REGEX.findAll(text).any { match ->
            val accessor = match.groupValues[2].removePrefix("libs.")
            isSupportedLibraryAccessor(accessor)
        }
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
        val displayText: String,
        val expression: PsiElement
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
        private const val MAX_UNRESOLVED_SAMPLES = 5

        private val FALLBACK_CATALOG_VAR_NAMES = listOf(
            "catalogLibs",
            "versionCatalogLibs",
            "resolvedLibs"
        )
    }
}
