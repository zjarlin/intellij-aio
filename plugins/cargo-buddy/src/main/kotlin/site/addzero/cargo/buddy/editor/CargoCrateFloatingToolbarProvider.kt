package site.addzero.cargo.buddy.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import site.addzero.cargo.buddy.model.CargoCrateResolver

class CargoCrateFloatingToolbarProvider :
    AbstractFloatingToolbarProvider("CargoBuddy.CrateToolbarGroup") {

    override fun isApplicable(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        if (!CargoCrateResolver.isCargoFile(project, file)) return false
        return super.isApplicable(dataContext) && editor.project == project
    }
}

