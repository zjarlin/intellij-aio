package site.addzero.smart.intentions.hiddenfiles.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

internal object HiddenFilesActionSupport {
    fun getSelectedFiles(event: AnActionEvent): List<VirtualFile> {
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
            ?: event.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf)
            ?: emptyList()

        return files
            .asSequence()
            .filter { it.isInLocalFileSystem }
            .distinctBy { it.path }
            .toList()
    }
}
