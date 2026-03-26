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
        return detectTargetAccessor(project, element) != null
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
        val expression = element.parentOfType<KtDotQualifiedExpression>(true) ?: return null
        val topExpression = findTopDotExpression(expression)
        return resolveProjectAccessor(project, topExpression)
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val basePath = project.basePath ?: return RewritePlan(emptyList(), 0, emptyList())
        val psiManager = PsiManager.getInstance(project)
        val modulesByAccessor = ProjectModuleResolver.scanModules(project).associateBy { it.typeSafeAccessor }
        val filePlans = mutableListOf<FilePlan>()
        val unresolved = mutableListOf<String>()

        for (virtualFile in collectTargetGradleKtsFiles(basePath)) {
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

            replacements += Replacement(
                range = expression.textRange,
                modulePath = modulePath,
                newText = """project("$modulePath")"""
            )
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
        if (!isInsideDependencyConfiguration(expression)) {
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

        private const val MAX_UNRESOLVED_SAMPLES = 5
    }
}
