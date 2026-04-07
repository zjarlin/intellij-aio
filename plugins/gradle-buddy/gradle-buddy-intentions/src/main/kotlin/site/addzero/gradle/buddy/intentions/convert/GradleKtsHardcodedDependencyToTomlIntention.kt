package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将硬编码依赖转换为版本目录格式的意图操作
 *
 * 此意图操作允许将 build.gradle.kts 文件中的硬编码依赖字符串
 * 替换为 TOML 版本目录格式，并自动合并到 libs.versions.toml 文件中。
 *
 * 优先级：高 - 在硬编码依赖声明时优先显示此意图操作
 *
 * @description 将硬编码依赖字符串转换为 TOML 格式并合并到版本目录文件
 */
class GradleKtsHardcodedDependencyToTomlIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.hardcoded.dependency.to.toml")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(GradleBuddyBundle.message("intention.hardcoded.dependency.to.toml.preview"))
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!HardcodedDependencyCatalogSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return false
        return HardcodedDependencyCatalogSupport.findHardcodedDependency(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        if (!HardcodedDependencyCatalogSupport.isTargetGradleKtsFile(file)) {
            return
        }

        val offset = editor?.caretModel?.offset ?: 0
        val element = file.findElementAt(offset) ?: return

        val dependencyInfo = HardcodedDependencyCatalogSupport.findHardcodedDependency(element) ?: return
        HardcodedDependencyCatalogSupport.chooseVersionReference(project, editor, dependencyInfo) {
                catalogFile,
                existingContent,
                versionRefFromVar,
                selectedVersionKey ->
            WriteCommandAction.runWriteCommandAction(project) {
                convertToVersionCatalog(
                    file = file,
                    dependencyInfo = dependencyInfo,
                    catalogFile = catalogFile,
                    existingContent = existingContent,
                    versionRefFromVar = versionRefFromVar,
                    selectedVersionKey = selectedVersionKey
                )
            }
        }
    }

    /**
     * 转换为版本目录格式并合并到 libs.versions.toml
     *
     * @param versionRefFromVar 如果版本字符串本身就是 libs.versions.xxx 变量引用，则为提取出的 ref 名称
     * @param selectedVersionKey 用户从弹窗中选择的已有版本 key；为 null 表示需要创建新的版本条目
     */
    private fun convertToVersionCatalog(
        file: PsiFile,
        dependencyInfo: HardcodedDependencyCatalogSupport.DependencyInfo,
        catalogFile: java.io.File,
        existingContent: HardcodedDependencyCatalogSupport.VersionCatalogContent,
        versionRefFromVar: String?,
        selectedVersionKey: String?
    ) {
        val project = file.project
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return

        val prepared = HardcodedDependencyCatalogSupport.prepareCatalogEntry(
            existingContent = existingContent,
            info = dependencyInfo,
            versionRefFromVar = versionRefFromVar,
            selectedVersionKey = selectedVersionKey
        )

        // 生成新的依赖声明
        val dependencyRef = when {
            dependencyInfo.classifier != null && dependencyInfo.extension != null -> {
                "libs.${prepared.accessorKey}(\"${dependencyInfo.extension}\", \"${dependencyInfo.classifier}\")"
            }
            dependencyInfo.classifier != null -> {
                "libs.${prepared.accessorKey}(classifier = \"${dependencyInfo.classifier}\")"
            }
            dependencyInfo.extension != null -> {
                "libs.${prepared.accessorKey}(\"${dependencyInfo.extension}\")"
            }
            else -> {
                "libs.${prepared.accessorKey}"
            }
        }

        // 生成完整的依赖声明
        val newDeclaration = "${dependencyInfo.configuration}($dependencyRef)"

        // 替换原始声明
        val startOffset = dependencyInfo.callExpression.textOffset
        val endOffset = startOffset + dependencyInfo.callExpression.text.length

        document.replaceString(startOffset, endOffset, newDeclaration)

        // 合并到 libs.versions.toml
        HardcodedDependencyCatalogSupport.mergeToVersionCatalog(
            catalogFile = catalogFile,
            existingContent = existingContent,
            info = dependencyInfo,
            prepared = prepared
        )
    }
}
