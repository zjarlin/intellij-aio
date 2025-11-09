package com.addzero.util

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
import java.nio.charset.StandardCharsets
import javax.swing.JFrame
import javax.swing.JOptionPane

object ShowContentUtil {
    fun showErrorMsg(message: String) {
        val component = findOrCreateWindow()
        JOptionPane.showMessageDialog(
            component, "出现错误: $message", "错误", JOptionPane.ERROR_MESSAGE
        )
    }

    fun Project?.openTextInEditor(
        sql: String,
        sqlPrefix: String = "",
        fileTypeSuffix: String = ".kt",
        filePath: String? = "${this!!.basePath}/.autoddl",
        focus: Boolean = true,
    ) {
        if (this == null) return
        if (sql.isBlank()) {
            showErrorMsg("生成出错啦")
            return
        }
        WriteCommandAction.runWriteCommandAction(this) {
            try {
                val sqlFile = genCode(this, sql, sqlPrefix, fileTypeSuffix, filePath)
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(sqlFile) ?: return@runWriteCommandAction
                val psiFile = PsiManager.getInstance(this).findFile(virtualFile) ?: return@runWriteCommandAction
                val documentManager = PsiDocumentManager.getInstance(this)
                val document = documentManager.getDocument(psiFile) ?: return@runWriteCommandAction
                documentManager.commitDocument(document)
                CodeStyleManager.getInstance(this).reformat(psiFile)
                FileEditorManager.getInstance(this).openFile(virtualFile, focus)
            } catch (e: Exception) {
                showErrorMsg("文件处理出错: ${e.message}")
            }
        }
    }

    fun genCode(
        project: Project,
        content: String,
        fileNamePre: String = "",
        fileTypeSuffix: String = ".kt",
        filePath: String? = "${project.basePath}/.autoddl",
    ): File {
        val autoddlDirectory = File(filePath)
        if (!autoddlDirectory.exists()) {
            autoddlDirectory.mkdir()
        }
        val removeAnyQuote = fileNamePre.removeAnyQuote()
        val removeAny = removeAnyQuote.removeAny( "\\")
        val fileName = "$removeAny$fileTypeSuffix"
        val file = File(autoddlDirectory, fileName)
        file.writeText(content, StandardCharsets.UTF_8)
        return file
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
