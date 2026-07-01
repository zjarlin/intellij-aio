package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object EntityFieldMergeActionSupport {
    fun selectedKotlinClasses(
        project: Project,
        event: AnActionEvent,
    ): List<KtClass> {
        val selectedFiles = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.toList()
            .orEmpty()
        val sourceFiles = selectedFiles.ifEmpty {
            EntityFieldMergeSelectionMemory.snapshot().mapNotNull(::findVirtualFile)
        }

        val classesFromFiles = sourceFiles
            .flatMap { file -> file.collectKotlinClasses(project) }
        val currentClass = event.getData(CommonDataKeys.PSI_ELEMENT)
            ?.getNonStrictParentOfType<KtClass>()
        val classes = buildList {
            addAll(classesFromFiles)
            currentClass?.let(::add)
        }

        return classes.distinctBy { klass ->
            klass.containingKtFile.virtualFile?.path.orEmpty() + "#" + klass.name.orEmpty()
        }
    }

    fun selectedKotlinClassFiles(event: AnActionEvent): List<VirtualFile> {
        return event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            ?.toList()
            .orEmpty()
            .filter { file -> !file.isDirectory && file.extension == "kt" }
            .distinctBy { file -> file.path }
    }

    fun rememberSelectedSourceFiles(event: AnActionEvent) {
        val selectedFiles = selectedKotlinClassFiles(event).map { file -> file.path }
        EntityFieldMergeSelectionMemory.remember(selectedFiles)
    }

    private fun VirtualFile.collectKotlinClasses(project: Project): List<KtClass> {
        if (isDirectory || extension != "kt") {
            return emptyList()
        }
        val psiFile = PsiManager.getInstance(project).findFile(this) as? KtFile ?: return emptyList()
        return psiFile.declarations.filterIsInstance<KtClass>()
    }

    private fun findVirtualFile(path: String): VirtualFile? {
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
    }
}
