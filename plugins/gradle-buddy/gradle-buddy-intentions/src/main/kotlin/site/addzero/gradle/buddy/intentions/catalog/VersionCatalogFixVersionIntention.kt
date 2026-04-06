package site.addzero.gradle.buddy.intentions.catalog

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * Fix Version Intention - 修复版本引用
 *
 * 当依赖引用了一个不属于它的版本变量时（比如共享的 LTS 版本），
 * 此 intention 会为该工件创建一个专属的版本变量，并更新引用。
 *
 * 例如:
 * 原始: addzero-jimmer-ext-lowquery = { module = "site.addzero:jimmer-ext-lowquery", version.ref = "addzeroLTSVersion" }
 * 修复后:
 *   [versions] 中新增: jimmer-ext-lowquery = "最新版本"
 *   [libraries] 中更新: version.ref = "jimmer-ext-lowquery"
 *
 * 幂等: 如果 artifactId 对应的版本变量已存在，直接引用，不重复创建。
 */
class VersionCatalogFixVersionIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.version.catalog.fix.version")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.version.catalog.fix.version.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!file.name.endsWith(".toml")) return false

        val virtualFile = file.virtualFile
        if (virtualFile != null && !virtualFile.path.contains("/gradle/")) return false

        val offset = editor?.caretModel?.offset ?: return false
        val element = file.findElementAt(offset) ?: return false

        val dep = VersionCatalogDependencyHelper.detectCatalogDependencyLenientAt(element) ?: return false
        return VersionCatalogFixVersionSupport.isAvailable(file, dep)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val offset = editor?.caretModel?.offset ?: return
        val element = file.findElementAt(offset) ?: return
        val dep = VersionCatalogDependencyHelper.detectCatalogDependencyLenientAt(element) ?: return
        if (!VersionCatalogFixVersionSupport.isAvailable(file, dep)) return
        VersionCatalogFixVersionSupport.apply(project, file, dep)
    }
}
