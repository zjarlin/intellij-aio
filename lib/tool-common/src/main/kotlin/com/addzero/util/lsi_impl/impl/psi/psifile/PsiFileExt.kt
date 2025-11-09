package com.addzero.util.lsi_impl.impl.psi.psifile

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil

fun PsiFile?.getCurrentPsiElement(editor: Editor?): PsiElement? {
    val offset = editor?.caretModel.offset
    val element = this?.findElementAt(offset)
    return element
}

inline fun <reified T : PsiNameIdentifierOwner> PsiFile.convertTo(): T? {
    return PsiTreeUtil.findChildOfType(originalElement, T::class.java)
}


fun PsiFile.getQualifiedClassName(): String? {
    val fileNameWithoutExtension = this.virtualFile.nameWithoutExtension
    val packageName = when (this) {
        is PsiJavaFile -> this.packageName
        else -> null
    }
    return if (packageName != null) {
        "$packageName.$fileNameWithoutExtension"
    } else {
        fileNameWithoutExtension
    }
}

fun PsiFile?.getPackagePath(): String? {
    val qualifiedClassName = this!!.getQualifiedClassName()
    return qualifiedClassName
}
