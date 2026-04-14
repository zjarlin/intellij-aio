package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 在 `libs.findLibrary("alias").get()` 上提供一个意图：
 * 如果 [libraries] 中缺少该 alias，则补一条使用专属 version.ref 的 skeleton；
 * 如果已存在，则补齐专属 [versions] 条目并跳转过去。
 */
class GradleKtsFindLibraryCreateCatalogEntryIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.find.library.create.catalog.entry")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.find.library.create.catalog.entry.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !file.name.endsWith(".gradle.kts")) {
            return false
        }
        return FindLibraryCatalogEntrySupport.findTarget(file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (editor == null) {
            return
        }

        val target = FindLibraryCatalogEntrySupport.findTarget(file, editor.caretModel.offset) ?: return

        var navigation: FindLibraryCatalogEntrySupport.NavigationTarget? = null
        WriteCommandAction.writeCommandAction(project)
            .withName(GradleBuddyBundle.message("intention.find.library.create.catalog.entry.command"))
            .run<Throwable> {
                navigation = FindLibraryCatalogEntrySupport.ensureCatalogEntry(
                    project = project,
                    alias = target.alias,
                    preferredBuildFile = file.virtualFile
                )
            }

        val resolvedNavigation = navigation ?: return
        OpenFileDescriptor(project, resolvedNavigation.virtualFile, resolvedNavigation.offset).navigate(true)
    }
}
