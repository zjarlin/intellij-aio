package com.addzero.addl.util.kt_util

// 为了保持向后兼容性，提供对新模块中工具类的引用
import com.addzero.util.kt_util.isCollectionType as newIsCollectionType
import com.addzero.util.kt_util.isNullableCollectionType
import com.addzero.util.kt_util.getCollectionElementType
import com.addzero.util.kt_util.getMapKeyValueTypes
import com.addzero.util.kt_util.isMapType
import com.addzero.util.kt_util.isStatic
import com.addzero.util.kt_util.isInCompanionObject
import com.addzero.util.kt_util.isInObjectDeclaration
import com.addzero.util.kt_util.hasJvmStaticAnnotation
import com.addzero.util.kt_util.getStaticFieldType
import com.addzero.util.kt_util.hasAnnotation
import com.addzero.util.kt_util.getArgumentValue
import com.addzero.util.kt_util.StaticFieldType
import com.addzero.util.kt_util.CollectionKind

// 为保持兼容性，创建类型别名和扩展函数
typealias StaticFieldType = com.addzero.util.kt_util.StaticFieldType
typealias CollectionKind = com.addzero.util.kt_util.CollectionKind

fun org.jetbrains.kotlin.psi.KtProperty.isCollectionType(): Boolean = this.newIsCollectionType()
fun com.intellij.psi.PsiType.isCollectionType(): Boolean = this.newIsCollectionType()
fun com.intellij.psi.PsiType.isNullableCollectionType(): Boolean = this.isNullableCollectionType()
fun com.intellij.psi.PsiType.getCollectionElementType(): com.intellij.psi.PsiType? = this.getCollectionElementType()
fun com.intellij.psi.PsiType.getMapKeyValueTypes(): Pair<com.intellij.psi.PsiType?, com.intellij.psi.PsiType?>? = this.getMapKeyValueTypes()
fun com.intellij.psi.PsiType.isMapType(): Boolean = this.isMapType()
fun org.jetbrains.kotlin.psi.KtProperty.isStatic(): Boolean = this.isStatic()
fun org.jetbrains.kotlin.psi.KtProperty.isInCompanionObject(): Boolean = this.isInCompanionObject()
fun org.jetbrains.kotlin.psi.KtProperty.isInObjectDeclaration(): Boolean = this.isInObjectDeclaration()
fun org.jetbrains.kotlin.psi.KtProperty.hasJvmStaticAnnotation(): Boolean = this.hasJvmStaticAnnotation()
fun org.jetbrains.kotlin.psi.KtProperty.getStaticFieldType(): StaticFieldType = this.getStaticFieldType()
fun org.jetbrains.kotlin.psi.KtAnnotated.hasAnnotation(fqName: String): Boolean = this.hasAnnotation(fqName)
fun org.jetbrains.kotlin.psi.KtAnnotationEntry.getArgumentValue(name: String): String? = this.getArgumentValue(name)