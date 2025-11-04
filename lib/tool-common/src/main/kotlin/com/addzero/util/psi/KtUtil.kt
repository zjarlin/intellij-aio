package com.addzero.util.psi

import com.addzero.util.psi.ktclass.KtClassUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Kotlin 专用工具类
 */
object KtUtil {
    fun guessTableName(ktClass: org.jetbrains.kotlin.psi.KtClass): String? {
        return KtClassUtil.guessTableName(ktClass)
    }

    fun guessTableNameByAnno(ktClass: org.jetbrains.kotlin.psi.KtClass): String? {
        return KtClassUtil.guessTableNameByAnno(ktClass)
    }

    fun extractInterfaceMetaInfo(ktClass: org.jetbrains.kotlin.psi.KtClass): MutableList<JavaFieldMetaInfo> {
        return KtClassUtil.extractInterfaceMetaInfo(ktClass)
    }

    fun KtProperty.guessFieldComment(idName: String): String {
        return KtClassUtil.guessFieldComment(this, idName)
    }

    fun getClassMetaInfo4KtClass(ktClass: org.jetbrains.kotlin.psi.KtClass): Pair<String, String?> {
        return KtClassUtil.getClassMetaInfo4KtClass(ktClass)
    }

    fun isKotlinPojo(
        element: PsiElement?
    ): Boolean {
        return KtClassUtil.isKotlinPojo(element)
    }

    fun isKotlinPojo(
        editor: Editor?, file: PsiFile?
    ): Boolean {
        return KtClassUtil.isKotlinPojo(editor, file)
    }
}

fun KtProperty.isDbField(): Boolean {
    return KtClassUtil.isDbField(this)
}