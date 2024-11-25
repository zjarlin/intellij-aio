package com.addzero.addl.util

import java.awt.KeyboardFocusManager
import javax.swing.JFrame
import javax.swing.JOptionPane
import java.awt.Component
object DialogUtil {
    /**
     * 显示错误消息对话框
     */
    fun showErrorMsg(message: String) {
        val component = findOrCreateWindow()
        JOptionPane.showMessageDialog(
            component, "出现错误: $message", "错误", JOptionPane.ERROR_MESSAGE
        )
    }

    /**
     * 显示信息提示对话框
     */
    fun showInfoMsg(message: String?, title: String = "提示") {
        val component = findOrCreateWindow()
        JOptionPane.showMessageDialog(
            component, message, title, JOptionPane.INFORMATION_MESSAGE
        )
    }

    /**
     * 显示警告消息对话框
     */
    fun showWarningMsg(message: String) {
        val component = findOrCreateWindow()
        JOptionPane.showMessageDialog(
            component, "警告: $message", "警告", JOptionPane.WARNING_MESSAGE
        )
    }

    /**
     * 显示确认对话框
     * @return true 如果用户点击了"是"，false 如果用户点击了"否"或关闭对话框
     */
    fun showConfirmDialog(message: String, title: String = "确认"): Boolean {
        val component = findOrCreateWindow()
        val result = JOptionPane.showConfirmDialog(
            component, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE
        )
        return result == JOptionPane.YES_OPTION
    }

    /**
     * 查找或创建窗口组件
     */
    private fun findOrCreateWindow(): Component {
        var component: Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (component == null) {
            component = JFrame().apply {
                setSize(300, 200)
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
        return component
    }
}