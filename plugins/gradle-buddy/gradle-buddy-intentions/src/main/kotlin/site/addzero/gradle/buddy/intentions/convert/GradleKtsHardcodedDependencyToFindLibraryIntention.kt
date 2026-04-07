package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 将硬编码 Maven 依赖直接改写为 `findLibrary("alias").get()`。
 *
 * 如果版本目录中不存在对应条目，则自动 upsert 到配置的 `libs.versions.toml`。
 */
class GradleKtsHardcodedDependencyToFindLibraryIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.HIGH

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (!HardcodedDependencyCatalogSupport.isTargetGradleKtsFile(file)) {
            return false
        }
        val element = file.findElementAt(editor?.caretModel?.offset ?: 0) ?: return false
        val dependencyInfo = HardcodedDependencyCatalogSupport.findHardcodedDependency(element) ?: return false
        return HardcodedDependencyCatalogSupport.isSupportedForFindLibrary(dependencyInfo)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val currentEditor = editor ?: return
        if (!HardcodedDependencyCatalogSupport.isTargetGradleKtsFile(file)) {
            return
        }

        val element = file.findElementAt(currentEditor.caretModel.offset) ?: return
        val dependencyInfo = HardcodedDependencyCatalogSupport.findHardcodedDependency(element) ?: return
        if (!HardcodedDependencyCatalogSupport.isSupportedForFindLibrary(dependencyInfo)) {
            return
        }

        HardcodedDependencyCatalogSupport.chooseVersionReference(
            project = project,
            editor = currentEditor,
            info = dependencyInfo,
            versionPolicy = HardcodedDependencyCatalogSupport.VersionReferencePolicy.DEDICATED
        ) {
                catalogFile,
                existingContent,
                versionRefFromVar,
                selectedVersionKey ->
            WriteCommandAction.writeCommandAction(project)
                .withName(GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.command"))
                .run<Throwable> {
                    val prepared = HardcodedDependencyCatalogSupport.prepareCatalogEntry(
                        existingContent = existingContent,
                        info = dependencyInfo,
                        versionRefFromVar = versionRefFromVar,
                        selectedVersionKey = selectedVersionKey,
                        versionPolicy = HardcodedDependencyCatalogSupport.VersionReferencePolicy.DEDICATED
                    )

                    HardcodedDependencyCatalogSupport.mergeToVersionCatalog(
                        catalogFile = catalogFile,
                        existingContent = existingContent,
                        info = dependencyInfo,
                        prepared = prepared
                    )

                    val rewritePlan = CatalogAccessorToFindLibrarySupport.buildRewritePlan(
                        file = file,
                        replacements = listOf(
                            CatalogAccessorToFindLibrarySupport.Replacement(
                                range = dependencyInfo.argumentExpression.textRange,
                                catalogKey = prepared.libraryKey,
                                newText = ""
                            )
                        )
                    ) ?: return@run

                    val document = FileDocumentManager.getInstance().getDocument(file.virtualFile) ?: return@run
                    val rewrittenText = CatalogAccessorToFindLibrarySupport.rewriteText(document.text, rewritePlan)
                    if (rewrittenText == document.text) {
                        return@run
                    }

                    document.replaceString(0, document.textLength, rewrittenText)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
        }
    }
}
