package com.addzero.addl.action.enumgen

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.kotlin.psi.KtProperty
import com.addzero.addl.util.JlStrUtil.toPascalCase

class GenerateEnumFromCommentIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText() = "Generate Enum from Comment"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return when (element.parent) {
            is PsiField, is KtProperty -> hasValidComment(element.parent)
            else -> false
        }
    }

    private fun hasValidComment(element: PsiElement): Boolean {
        val comment = when (element) {
            is PsiField -> element.docComment?.text
            is KtProperty -> element.docComment?.text
            else -> null
        } ?: return false

        return comment.contains("=") || comment.contains(":")
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val field = element.parent
        val fieldName = when (field) {
            is PsiField -> field.name
            is KtProperty -> field.name
            else -> return
        }

        val comment = when (field) {
            is PsiField -> field.docComment?.text
            is KtProperty -> field.docComment?.text
            else -> return
        } ?: return

        val dictItems = parseDictItems(comment, fieldName!!)
        if (dictItems.isEmpty()) return

        generateEnum(project, fieldName, dictItems)
    }
}