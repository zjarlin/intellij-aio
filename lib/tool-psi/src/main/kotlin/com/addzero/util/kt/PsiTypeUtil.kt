package com.addzero.util.kt

import com.addzero.util.kt.KtTypeUtil.isCollectionType
import com.addzero.util.psi.PsiTypeUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import org.jetbrains.kotlin.psi.KtProperty

/**
 * 兼容性包装，提供统一的类型检查接口
 */
fun PsiClass.isCollectionType(): Boolean = PsiTypeUtil.isCollectionType(this)

fun PsiField.isCollectionType(): Boolean = PsiTypeUtil.isCollectionType(this)

fun KtProperty.isCollectionType(): Boolean = KtTypeUtil.isCollectionType(this)

fun isGenericCollectionType(psiClass: PsiClass): Boolean = PsiTypeUtil.isGenericCollectionType(psiClass)