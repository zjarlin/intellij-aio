package site.addzero.addl.action.base

import site.addzero.addl.util.DialogUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

abstract class BaseAction : AnAction() {
    protected val logger = Logger.getInstance(javaClass)

    final override fun actionPerformed(e: AnActionEvent) {
        try {
            doActionPerformed(e)
        } catch (ex: Exception) {
            logger.error(ex)
            DialogUtil.showErrorMsg("操作失败: ${ex.message}")
        }
    }

    /**
     * 实际的操作逻辑
     */
    protected abstract fun doActionPerformed(e: AnActionEvent)
}
