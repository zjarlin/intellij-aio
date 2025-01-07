package com.addzero.addl.util

import com.addzero.common.kt_util.isBlank
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.io.File
import javax.swing.JFrame
import javax.swing.JOptionPane


object ShowContentUtil {

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
            component, "出现错误: $message", "错误", JOptionPane.ERROR_MESSAGE
        )
    }

//    fun openTextInEditor(
//        project: Project?,
//        sql: String,
//        sqlPrefix: String = "",
//        fileTypeSuffix: String,
//        filePath: String? = "${project!!.basePath}/.autoddl",
//    ) {
//        if (sql.isBlank()) {
//            showErrorMsg("生成出错啦")
//            return
//        }
//        WriteCommandAction.runWriteCommandAction(project) {
//            // 定义 .autoddl 目录
//            val autoddlDirectory = File(filePath)
//            // 确保 .autoddl 目录存在
//            if (!autoddlDirectory.exists()) {
//                autoddlDirectory.mkdir()
//            }
//            // 创建 SQL 文件的名称
//            val fileName = "$sqlPrefix$fileTypeSuffix"
//            val sqlFile = File(autoddlDirectory, fileName)
//            // 写入 SQL 到文件
//            sqlFile.writeText(sql)
//
//            // 打开 SQL 文件
//            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sqlFile)
//            if (virtualFile != null) {
//                FileEditorManager.getInstance(project!!).openFile(virtualFile, true)
//            }
//        }
//    }


    fun openTextInEditor(
        project: Project?,
        sql: String,
        sqlPrefix: String = "",
        fileTypeSuffix: String,
        filePath: String? = "${project!!.basePath}/.autoddl",
        focus: Boolean = true,
    ) {
        if (project == null) return
        if (sql.isBlank()) {
            showErrorMsg("生成出错啦")
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            try {
                // 定义 .autoddl 目录
                val autoddlDirectory = File(filePath)
                // 确保 .autoddl 目录存在
                if (!autoddlDirectory.exists()) {
                    autoddlDirectory.mkdir()
                }

                // 创建 SQL 文件的名称
                val fileName = "$sqlPrefix$fileTypeSuffix"
                val sqlFile = File(autoddlDirectory, fileName)
                // 写入 SQL 到文件
                sqlFile.writeText(sql)

                // 刷新并获取虚拟文件
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sqlFile) ?: return@runWriteCommandAction

                // 获取 PsiFile
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@runWriteCommandAction

                // 获取文档管理器
                val documentManager = PsiDocumentManager.getInstance(project)
                val document = documentManager.getDocument(psiFile) ?: return@runWriteCommandAction
                // 提交文档更改
                documentManager.commitDocument(document)

                // 格式化代码
                CodeStyleManager.getInstance(project).reformat(psiFile)

                // 打开文件
                FileEditorManager.getInstance(project).openFile(virtualFile, focus)
            } catch (e: Exception) {
                showErrorMsg("文件处理出错: ${e.message}")
            }
        }
    }


}