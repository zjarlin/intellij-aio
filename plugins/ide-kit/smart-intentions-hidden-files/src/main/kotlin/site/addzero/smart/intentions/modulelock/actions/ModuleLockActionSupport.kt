package site.addzero.smart.intentions.modulelock.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectFileIndex

internal object ModuleLockActionSupport {
    fun getSelectedModules(event: AnActionEvent): List<Module> {
        val project = event.project ?: return emptyList()
        val contextModules = buildList {
            event.getData(LangDataKeys.MODULE_CONTEXT_ARRAY)?.let(::addAll)
            event.getData(LangDataKeys.MODULE)?.let(::add)
        }
        if (contextModules.isNotEmpty()) {
            return contextModules.distinctBy { module -> module.name }
        }

        val fileIndex = ProjectFileIndex.getInstance(project)
        val files = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.toList()
            ?: event.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf)
            ?: emptyList()
        return files.asSequence()
            .filter { file -> file.isInLocalFileSystem }
            .mapNotNull { file -> fileIndex.getModuleForFile(file) }
            .filter { module -> !module.isDisposed }
            .distinctBy { module -> module.name }
            .toList()
    }
}
