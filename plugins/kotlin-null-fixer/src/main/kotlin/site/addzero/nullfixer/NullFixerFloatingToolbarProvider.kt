package site.addzero.nullfixer

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider

/**
 * 悬浮工具条：在 .kt 文件的编辑器右上角显示 "Fix Null Safety" 按钮。
 * 只在 Kotlin 文件上显示。
 */
class NullFixerFloatingToolbarProvider :
    AbstractFloatingToolbarProvider("KotlinNullFixer.FloatingToolbarGroup") {

    override val autoHideable: Boolean get() = true

    override fun isApplicable(dataContext: DataContext): Boolean {
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return false
        if (!file.isValid || file.isDirectory) return false
        if (!file.name.endsWith(".kt") && !file.name.endsWith(".kts")) return false
        return super.isApplicable(dataContext) && editor.project == project
    }
}
