package site.addzero.gradle.buddy.intentions.convert

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import site.addzero.gradle.buddy.i18n.GradleBuddyBundle

/**
 * 全项目扫描硬编码 Maven 依赖，并尽可能批量转为 `findLibrary("alias").get()`。
 */
class GradleKtsHardcodedDependencyToFindLibraryBatchIntention : IntentionAction, PriorityAction {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.NORMAL

    override fun getFamilyName(): String = GradleBuddyBundle.message("common.family.gradle.buddy")

    override fun getText(): String = GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch")

    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.Html(
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.preview")
        )
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile): Boolean {
        if (editor == null || !HardcodedDependencyCatalogSupport.isTargetGradleKtsFile(file)) {
            return false
        }

        val element = file.findElementAt(editor.caretModel.offset)
        if (element != null) {
            val dependencyInfo = HardcodedDependencyCatalogSupport.findHardcodedDependency(element)
            if (dependencyInfo != null && HardcodedDependencyCatalogSupport.isSupportedForFindLibrary(dependencyInfo)) {
                return true
            }
        }

        val fileCandidates = collectFileCandidates(file)
        if (fileCandidates.dependencies.isNotEmpty()) {
            return true
        }

        return HardcodedDependencyCatalogSupport.containsHardcodedDependencyText(file.text)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        val plan = collectRewritePlan(project)
        if (plan.filePlans.isEmpty()) {
            Messages.showInfoMessage(
                project,
                GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.none"),
                GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.title")
            )
            return
        }

        val confirm = Messages.showYesNoDialog(
            project,
            buildConfirmMessage(plan),
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.confirm.title"),
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.confirm.ok"),
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.confirm.cancel"),
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        applyRewritePlan(project, plan)

        Messages.showInfoMessage(
            project,
            buildResultMessage(plan),
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.title")
        )
    }

    private fun collectRewritePlan(project: Project): RewritePlan {
        val psiManager = PsiManager.getInstance(project)
        val (catalogFile, originalContent) = HardcodedDependencyCatalogSupport.loadVersionCatalog(project)
        val workingContent = HardcodedDependencyCatalogSupport.VersionCatalogContent(
            versions = originalContent.versions.toMutableMap(),
            libraries = originalContent.libraries.toMutableMap()
        )

        val filePlans = mutableListOf<FilePlan>()
        val skipped = mutableListOf<String>()
        var createdLibraryCount = 0
        var createdVersionCount = 0

        for (virtualFile in CatalogAccessorToFindLibrarySupport.collectTargetGradleKtsFiles(project)) {
            val psiFile = psiManager.findFile(virtualFile) ?: continue
            val fileCandidates = collectFileCandidates(psiFile)
            skipped += fileCandidates.skipped

            if (fileCandidates.dependencies.isEmpty()) {
                continue
            }

            val replacements = mutableListOf<CatalogAccessorToFindLibrarySupport.Replacement>()
            val catalogEntries = mutableListOf<CatalogEntryPlan>()

            for (dependencyInfo in fileCandidates.dependencies) {
                val versionSelection = HardcodedDependencyCatalogSupport.selectVersionReferenceForBatch(
                    existingContent = workingContent,
                    info = dependencyInfo
                )
                val prepared = HardcodedDependencyCatalogSupport.prepareCatalogEntry(
                    existingContent = workingContent,
                    info = dependencyInfo,
                    versionRefFromVar = versionSelection.versionRefFromVar,
                    selectedVersionKey = versionSelection.selectedVersionKey
                )
                val mutation = HardcodedDependencyCatalogSupport.registerCatalogEntry(
                    existingContent = workingContent,
                    info = dependencyInfo,
                    prepared = prepared
                )
                if (mutation.createdLibrary) {
                    createdLibraryCount++
                }
                if (mutation.createdVersion) {
                    createdVersionCount++
                }

                replacements += CatalogAccessorToFindLibrarySupport.Replacement(
                    range = dependencyInfo.argumentExpression.textRange,
                    catalogKey = prepared.libraryKey,
                    newText = ""
                )
                catalogEntries += CatalogEntryPlan(
                    info = dependencyInfo,
                    prepared = prepared
                )
            }

            val rewritePlan = CatalogAccessorToFindLibrarySupport.buildRewritePlan(psiFile, replacements) ?: continue
            filePlans += FilePlan(
                file = virtualFile,
                variableName = rewritePlan.variableName,
                insertOffset = rewritePlan.insertOffset,
                replacements = rewritePlan.replacements,
                catalogEntries = catalogEntries
            )
        }

        return RewritePlan(
            catalogFile = catalogFile,
            filePlans = filePlans,
            createdLibraryCount = createdLibraryCount,
            createdVersionCount = createdVersionCount,
            skippedCount = skipped.size,
            skippedSamples = skipped.take(MAX_SKIPPED_SAMPLES)
        )
    }

    private fun collectFileCandidates(file: PsiFile): FileCandidates {
        val dependencies = mutableListOf<HardcodedDependencyCatalogSupport.DependencyInfo>()
        val skipped = mutableListOf<String>()

        val callExpressions = PsiTreeUtil.collectElementsOfType(file, KtCallExpression::class.java)
            .distinctBy { it.textRange.startOffset to it.textRange.endOffset }
            .sortedBy { it.textRange.startOffset }

        for (callExpression in callExpressions) {
            val dependencyInfo = HardcodedDependencyCatalogSupport.findHardcodedDependency(callExpression) ?: continue
            if (HardcodedDependencyCatalogSupport.isSupportedForFindLibrary(dependencyInfo)) {
                dependencies += dependencyInfo
            } else {
                skipped += "${file.name}: ${callExpression.text}"
            }
        }

        return FileCandidates(
            dependencies = dependencies,
            skipped = skipped
        )
    }

    private fun applyRewritePlan(project: Project, plan: RewritePlan) {
        val documentManager = FileDocumentManager.getInstance()

        WriteCommandAction.runWriteCommandAction(
            project,
            GradleBuddyBundle.message("intention.hardcoded.dependency.to.find.library.batch.command"),
            null,
            {
                val psiDocumentManager = com.intellij.psi.PsiDocumentManager.getInstance(project)
                val (_, existingContent) = HardcodedDependencyCatalogSupport.loadVersionCatalog(project)

                for (filePlan in plan.filePlans) {
                    for (catalogEntry in filePlan.catalogEntries) {
                        HardcodedDependencyCatalogSupport.mergeToVersionCatalog(
                            catalogFile = plan.catalogFile,
                            existingContent = existingContent,
                            info = catalogEntry.info,
                            prepared = catalogEntry.prepared
                        )
                    }
                }

                for (filePlan in plan.filePlans) {
                    val document = documentManager.getDocument(filePlan.file) ?: continue
                    val rewrittenText = CatalogAccessorToFindLibrarySupport.rewriteText(
                        originalText = document.text,
                        plan = CatalogAccessorToFindLibrarySupport.FileRewritePlan(
                            variableName = filePlan.variableName,
                            insertOffset = filePlan.insertOffset,
                            replacements = filePlan.replacements
                        )
                    )
                    if (rewrittenText == document.text) {
                        continue
                    }

                    document.replaceString(0, document.textLength, rewrittenText)
                    psiDocumentManager.commitDocument(document)
                    documentManager.saveDocument(document)
                }
            }
        )
    }

    private fun buildConfirmMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }
        val skippedLine = if (plan.skippedCount > 0) {
            GradleBuddyBundle.message(
                "intention.hardcoded.dependency.to.find.library.batch.confirm.skipped.line",
                plan.skippedCount,
                plan.skippedSamples.joinToString(separator = "\n") { "  $it" }
            )
        } else {
            ""
        }

        return GradleBuddyBundle.message(
            "intention.hardcoded.dependency.to.find.library.batch.confirm.body",
            fileCount,
            replacementCount,
            if (plan.createdLibraryCount > 0) {
                GradleBuddyBundle.message(
                    "intention.hardcoded.dependency.to.find.library.batch.confirm.library.line",
                    plan.createdLibraryCount
                )
            } else {
                ""
            },
            if (plan.createdVersionCount > 0) {
                GradleBuddyBundle.message(
                    "intention.hardcoded.dependency.to.find.library.batch.confirm.version.line",
                    plan.createdVersionCount
                )
            } else {
                ""
            },
            if (insertCount > 0) {
                GradleBuddyBundle.message(
                    "intention.hardcoded.dependency.to.find.library.batch.confirm.insert.line",
                    insertCount
                )
            } else {
                ""
            },
            if (fallbackVarCount > 0) {
                GradleBuddyBundle.message(
                    "intention.hardcoded.dependency.to.find.library.batch.confirm.fallback.line",
                    fallbackVarCount
                )
            } else {
                ""
            },
            skippedLine
        )
    }

    private fun buildResultMessage(plan: RewritePlan): String {
        val fileCount = plan.filePlans.size
        val replacementCount = plan.filePlans.sumOf { it.replacements.size }
        val insertCount = plan.filePlans.count { it.insertOffset != null }
        val fallbackVarCount = plan.filePlans.count { it.insertOffset != null && it.variableName != DEFAULT_CATALOG_VAR_NAME }

        val extraLines = buildString {
            if (plan.createdLibraryCount > 0) {
                append(
                    GradleBuddyBundle.message(
                        "intention.hardcoded.dependency.to.find.library.batch.result.library.line",
                        plan.createdLibraryCount
                    )
                )
            }
            if (plan.createdVersionCount > 0) {
                append(
                    GradleBuddyBundle.message(
                        "intention.hardcoded.dependency.to.find.library.batch.result.version.line",
                        plan.createdVersionCount
                    )
                )
            }
            if (insertCount > 0) {
                append(
                    GradleBuddyBundle.message(
                        "intention.hardcoded.dependency.to.find.library.batch.result.insert.line",
                        insertCount
                    )
                )
            }
            if (fallbackVarCount > 0) {
                append(
                    GradleBuddyBundle.message(
                        "intention.hardcoded.dependency.to.find.library.batch.result.fallback.line",
                        fallbackVarCount
                    )
                )
            }
            if (plan.skippedCount > 0) {
                append(
                    GradleBuddyBundle.message(
                        "intention.hardcoded.dependency.to.find.library.batch.result.skipped.line",
                        plan.skippedCount
                    )
                )
            }
        }

        return GradleBuddyBundle.message(
            "intention.hardcoded.dependency.to.find.library.batch.result.body",
            fileCount,
            replacementCount,
            extraLines
        )
    }

    private data class FileCandidates(
        val dependencies: List<HardcodedDependencyCatalogSupport.DependencyInfo>,
        val skipped: List<String>
    )

    private data class CatalogEntryPlan(
        val info: HardcodedDependencyCatalogSupport.DependencyInfo,
        val prepared: HardcodedDependencyCatalogSupport.PreparedCatalogEntry
    )

    private data class FilePlan(
        val file: VirtualFile,
        val variableName: String,
        val insertOffset: Int?,
        val replacements: List<CatalogAccessorToFindLibrarySupport.Replacement>,
        val catalogEntries: List<CatalogEntryPlan>
    )

    private data class RewritePlan(
        val catalogFile: java.io.File,
        val filePlans: List<FilePlan>,
        val createdLibraryCount: Int,
        val createdVersionCount: Int,
        val skippedCount: Int,
        val skippedSamples: List<String>
    )

    companion object {
        private const val DEFAULT_CATALOG_VAR_NAME = "libs"
        private const val MAX_SKIPPED_SAMPLES = 5
    }
}
