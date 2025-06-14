package com.addzero.addl.util

import cn.hutool.core.io.FileUtil
import com.addzero.addl.util.fieldinfo.PsiUtil
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
import java.awt.Window
import java.io.File
import javax.swing.JFrame
import javax.swing.JOptionPane

object ShowContentUtil {
    fun showErrorMsg(message: String) {
        val component = findOrCreateWindow()
        JOptionPane.showMessageDialog(
            component, "出现错误: $message", "错误", JOptionPane.ERROR_MESSAGE
        )
    }

    fun openTextInEditor(
        project: Project?,
        sql: String,
        sqlPrefix: String = "",
        fileTypeSuffix: String = if (PsiUtil.isKotlinProject(project!!)) {
            ".kt"
        } else {
            ".java"
        },
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
                val sqlFile = genCode(project, sql, sqlPrefix, fileTypeSuffix, filePath)
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sqlFile) ?: return@runWriteCommandAction
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@runWriteCommandAction
                val documentManager = PsiDocumentManager.getInstance(project)
                val document = documentManager.getDocument(psiFile) ?: return@runWriteCommandAction
                documentManager.commitDocument(document)
                CodeStyleManager.getInstance(project).reformat(psiFile)
                FileEditorManager.getInstance(project).openFile(virtualFile, focus)
            } catch (e: Exception) {
                showErrorMsg("文件处理出错: ${e.message}")
            }
        }
    }

    fun genCode(
        project: Project,
        content: String,
        fileNamePre: String = "",
        fileTypeSuffix: String = if (PsiUtil.isKotlinProject(project)) {
            ".kt"
        } else {
            ".java"
        },
        filePath: String? = "${project.basePath}/.autoddl",
    ): File {
        val autoddlDirectory = File(filePath)
        if (!autoddlDirectory.exists()) {
            autoddlDirectory.mkdir()
        }
        val removeAnyQuote = fileNamePre.removeAnyQuote()
        val removeAny = removeAnyQuote.removeAny("\\")
        val fileName = "$removeAny$fileTypeSuffix"
        val file = FileUtil.file(autoddlDirectory, fileName)
        return FileUtil.writeUtf8String(content, file)
    }

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
