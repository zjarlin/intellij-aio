package site.addzero.gradle.buddy.intentions.projectdep

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
import site.addzero.gradle.buddy.intentions.util.GradleProjectRoots

class GradleKtsProjectAccessorToProjectIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.project.accessor.to.project")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.project.accessor.to.project.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        if (file.name == "settings.gradle.kts") {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset) ?: return false
        if (detectTargetAccessor(project, element) != null) {
            return true
        }

        // 这是项目级批量替换意图，不要求光标必须精确落在 projects.xxx 上。
        if (collectFileCandidates(
            project = project,
            file = file,
            modulesByAccessor = ProjectModuleResolver.scanModules(project).associateBy { it.typeSafeAccessor }
        ).replacements.isNotEmpty()
        ) {
            return true
        }

        // 兜底：部分 Kotlin DSL 场景下 PSI 不稳定，但源码里已经明确出现了 projects.xxx。
        // 先把意图展示出来，后续再做整项目扫描与解析。
        return containsProjectAccessorText(file.text)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val plan = collectRewritePlan(project)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("intention.project.accessor.to.project.none"),
                GradleBuddyBundle.message("intention.project.accessor.to.project.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(plan),
            GradleBuddyBundle.message("intention.project.accessor.to.project.confirm.title"),
            GradleBuddyBundle.message("intention.project.accessor.to.project.confirm.ok"),
            GradleBuddyBundle.message("intention.project.accessor.to.project.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(plan),
            GradleBuddyBundle.message("intention.project.accessor.to.project.title")
        )
    }

    private fun detectTargetAccessor(project: Project, element: PsiElement): ResolvedAccessor? {
        findEnclosingProjectAccessorCall(project, element)?.let { return it }

        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        return resolveProjectAccessor(project, topExpression)
    }

    private fun findEnclosingProjectAccessorCall(project: Project, element: PsiElement): ResolvedAccessor? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression) {
                resolveProjectAccessorFromCall(project, current)?.let { return it }
            }
            current = current.parent
        }
        return null
    }

    private fun resolveProjectAccessorFromCall(project: Project, callExpression: KtCallExpression): ResolvedAccessor? {
        if (!isDependencyLikeCall(callExpression)) {
            return null
        }

        val argumentExpression = callExpression.valueArguments.singleOrNull()?.getArgumentExpression()
            as? KtDotQualifiedExpression ?: return null
        val topExpression = findTopDotExpression(argumentExpression)
        return resolveProjectAccessor(project, topExpression)
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val modulesByAccessor = ProjectModuleResolver.scanModules(project).associateBy { it.typeSafeAccessor }
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val fileCandidates = collectFileCandidates(project, psiFile, modulesByAccessor)
            unresolved += fileCandidates.unresolved

            if (fileCandidates.replacements.isEmpty()) {
                continue
            }

            filePlans += FilePlan(
                file = virtualFile,
                replacements = fileCandidates.replacements
            )
        }

        return RewritePlan(
            filePlans = filePlans,
            unresolvedCount = unresolved.size,
            unresolvedSamples = unresolved.take(MAX_UNRESOLVED_SAMPLES)
        )
    }

    private fun collectFileCandidates(
        project: Project,
        file: PsiFile,
        modulesByAccessor: Map<String, ProjectModuleResolver.ModuleInfo>
    ): FileCandidates {
        val seenRanges = linkedSetOf<Pair<Int, Int>>()
        val topExpressions = PsiTreeUtil.collectElementsOfType(file, KtDotQualifiedExpression::class.java)
            .map { findTopDotExpression(it) }
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }

        val replacements = mutableListOf<Replacement>()
        val unresolved = mutableListOf<String>()

        for (expression in topExpressions) {
            val accessor = classifyProjectAccessor(expression) ?: continue
            val modulePath = modulesByAccessor[accessor]?.path ?: resolveProjectAccessor(project, expression)?.modulePath
            if (modulePath == null) {
                unresolved += "${file.name}: $accessor"
                continue
            }

            val range = expression.textRange
            if (seenRanges.add(range.startOffset to range.endOffset)) {
                replacements += Replacement(
                    range = range,
                    modulePath = modulePath,
                    newText = """project("$modulePath")"""
                )
            }
        }

        // 兜底：某些 Gradle Kotlin DSL 结构下，PSI 不会稳定地把 projects.xxx 暴露为可用的 dot expression。
        // 这里直接补一轮文本扫描，确保像 ksp(projects.xxx)、testAnnotationProcessor(projects.xxx) 这类写法不会漏。
        for (match in PROJECT_ACCESSOR_CALL_REGEX.findAll(file.text)) {
            val callName = match.groupValues[1]
            if (!looksLikeDependencyCallName(callName)) {
                continue
            }

            val accessor = match.groupValues[2]
            val modulePath = modulesByAccessor[accessor]?.path ?: ProjectModuleResolver.findByTypeSafeAccessor(project, accessor)?.path
            if (modulePath == null) {
                unresolved += "${file.name}: $accessor"
                continue
            }

            val accessorGroup = match.groups[2] ?: continue
            val range = TextRange(accessorGroup.range.first, accessorGroup.range.last + 1)
            if (seenRanges.add(range.startOffset to range.endOffset)) {
                replacements += Replacement(
                    range = range,
                    modulePath = modulePath,
                    newText = """project("$modulePath")"""
                )
            }
        }

        return FileCandidates(replacements, unresolved)
    }

    private fun resolveProjectAccessor(project: Project, expression: KtDotQualifiedExpression): ResolvedAccessor? {
        val accessor = classifyProjectAccessor(expression) ?: return null
        val module = ProjectModuleResolver.findByTypeSafeAccessor(project, accessor) ?: return null
        return ResolvedAccessor(
            accessor = accessor,
            modulePath = module.path
        )
    }

    private fun classifyProjectAccessor(expression: KtDotQualifiedExpression): String? {
        val text = expression.text.trim()
        if (!PROJECTS_ACCESSOR_REGEX.matches(text)) {
            return null
        }
        if (!isInsideDependencyLikeCall(expression)) {
            return null
        }
        return text
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(
            project,
            GradleBuddyBundle.message("intention.project.accessor.to.project.command"),
            null,
            {
                val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)

                for (filePlan in plan.filePlans) {
                    val document = documentManager.getDocument(filePlan.file) ?: continue
                    var text = document.text

                    for (replacement in filePlan.replacements.sortedByDescending { it.range.startOffset }) {
                        text = text.substring(0, replacement.range.startOffset) +
                            replacement.newText +
                            text.substring(replacement.range.endOffset)
                    }

                    document.replaceString(0, document.textLength, text)
                    psiDocumentManager.commitDocument(document)
                    documentManager.saveDocument(document)
                }
            }
        )
    }

    private fun buildConfirmMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val unresolvedLine = if (plan.unresolvedCount > 0) {
            GradleBuddyBundle.message(
                "intention.project.accessor.to.project.confirm.unresolved",
                plan.unresolvedCount,
                plan.unresolvedSamples.joinToString(separator = "\n") { "  $it" }
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.project.accessor.to.project.confirm.body",
            fileCount,
            replacementCount,
            unresolvedLine
        )
    }

    private fun buildResultMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val extra = if (plan.unresolvedCount > 0) {
            GradleBuddyBundle.message(
                "intention.project.accessor.to.project.result.unresolved",
                plan.unresolvedCount
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.project.accessor.to.project.result.body",
            fileCount,
            replacementCount,
            extra
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

    private fun isInsideDependencyLikeCall(expression: KtDotQualifiedExpression): Boolean {
        var current: PsiElement? = expression.parent
        while (current != null) {
            if (current is KtCallExpression) {
                val argumentExpression = current.valueArguments.singleOrNull()?.getArgumentExpression()
                if (argumentExpression == expression && isDependencyLikeCall(current)) {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    /**
     * 兼容所有写在 dependencies {} 里的依赖配置调用，而不只是一小撮固定名称。
     *
     * 例如:
     * - implementation(...)
     * - testAnnotationProcessor(...)
     * - customSourceSetImplementation(...)
     *
     * 只要它位于 dependencies {} 块中，且有单个参数，就视为依赖声明。
     */
    private fun isDependencyLikeCall(callExpression: KtCallExpression): Boolean {
        if (callExpression.valueArguments.size != 1) {
            return false
        }

        val callee = callExpression.calleeExpression?.text
        if (callee in DEPENDENCY_CONFIGURATIONS) {
            return true
        }

        return isInsideDependenciesBlock(callExpression)
    }

    private fun isInsideDependenciesBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "dependencies") {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun looksLikeDependencyCallName(callName: String): Boolean {
        if (callName in DEPENDENCY_CONFIGURATIONS) {
            return true
        }
        return callName.startsWith("ksp") ||
            callName.startsWith("kapt") ||
            callName.endsWith("Implementation") ||
            callName.endsWith("Api") ||
            callName.endsWith("CompileOnly") ||
            callName.endsWith("RuntimeOnly") ||
            callName.endsWith("Processor") ||
            callName.endsWith("Ksp") ||
            callName.endsWith("Kapt")
    }

    private fun containsProjectAccessorText(text: String): Boolean {
        return PROJECT_ACCESSOR_CALL_REGEX.containsMatchIn(text)
    }

    private data class ResolvedAccessor(
        val accessor: String,
        val modulePath: String
    )

    private data class FileCandidates(
        val replacements: List<Replacement>,
        val unresolved: List<String>
    )

    private data class FilePlan(
        val file: VirtualFile,
        val replacements: List<Replacement>
    )

    private data class RewritePlan(
        val filePlans: List<FilePlan>,
        val unresolvedCount: Int,
        val unresolvedSamples: List<String>
    )

    private data class Replacement(
        val range: TextRange,
        val modulePath: String,
        val newText: String
    )

    companion object {
        private val DEPENDENCY_CONFIGURATIONS = setOf(
            "implementation", "api", "compileOnly", "runtimeOnly",
            "testImplementation", "testApi", "testCompileOnly", "testRuntimeOnly",
            "androidTestImplementation", "androidTestApi", "androidTestCompileOnly", "androidTestRuntimeOnly",
            "debugImplementation", "releaseImplementation",
            "kapt", "ksp", "annotationProcessor", "lintChecks",
            "testFixturesImplementation", "testFixturesApi", "testFixturesCompileOnly", "testFixturesRuntimeOnly"
        )

        private val SKIP_DIR_NAMES = setOf(
            "build", "out", ".gradle", ".idea", "node_modules", "target", ".git"
        )

        private val PROJECTS_ACCESSOR_REGEX = Regex("""^projects\.[A-Za-z0-9_.]+$""")
        private val PROJECT_ACCESSOR_CALL_REGEX = Regex(
            """([A-Za-z_][A-Za-z0-9_]*)\s*\(\s*(projects\.[A-Za-z0-9_.]+)\s*\)"""
        )

        private const val MAX_UNRESOLVED_SAMPLES = 5
    }
}
