package com.addzero.addl.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.JFrame
import javax.swing.JOptionPane


object ShowSqlUtil {

    fun showErrorMsg(message: String) {
        // 获取当前活跃的 Window，如果没有活跃的窗口，将创建一个新的顶层窗口
        var component: Component? = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        if (component == null) {
            // 如果没有找到活跃的窗口，就使用一个临时的 JFrame 作为父窗口
            component = JFrame()
            (component as JFrame).setSize(300, 200)
            (component as JFrame).setLocationRelativeTo(null)
            (component as JFrame).isVisible = true
        }

        // 显示错误消息对话框
        JOptionPane.showMessageDialog(
            component, "出现错误: $message",
            "错误", JOptionPane.ERROR_MESSAGE
        )
    }

    fun openSqlInEditor(project: Project?, sql: String, sqlPrefix: String = "", fileTypeSuffix: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            // 定义 .autoddl 目录
            val autoddlDirectory = File(project!!.basePath, ".autoddl")
            // 确保 .autoddl 目录存在
            if (!autoddlDirectory.exists()) {
                autoddlDirectory.mkdir()
            }
            // 创建 SQL 文件的名称
            val fileName = "$sqlPrefix$fileTypeSuffix"
            val sqlFile = File(autoddlDirectory, fileName)
            // 写入 SQL 到文件
            sqlFile.writeText(sql)

            // 打开 SQL 文件
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sqlFile)
            if (virtualFile != null) {
                FileEditorManager.getInstance(project!!).openFile(virtualFile, true)
            }
        }
    }

    fun showDDLInTextField(project: Project?, ddlResult: String) {
        // 创建一个文本域
        val textField = JBTextField(ddlResult)

        // 调用showTextAreaDialog
        val delimiters = System.lineSeparator()
        Messages.showTextAreaDialog(textField,                        // 第一个参数为JTextField
            "Generated DDL",                  // 窗口标题
            "SQL Output",                     // DimensionServiceKey
            { input -> input.split(delimiters) },   // parser: 将输入按行解析成List
            { lines -> lines.joinToString(delimiters) }
//               lineJoiner: 将List连接成字符串
        )
    }

}