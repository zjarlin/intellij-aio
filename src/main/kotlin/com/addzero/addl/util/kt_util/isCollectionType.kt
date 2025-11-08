package com.addzero.addl.util.kt_util


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
