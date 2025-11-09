package com.addzero.util.lsi_impl.impl.psi.clazz

import com.addzero.util.lsi.constant.*
import com.addzero.util.lsi_impl.impl.kt.anno.guessTableName
import com.addzero.util.lsi_impl.impl.psi.field.getDefaultValue
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import site.addzero.util.str.cleanDocComment
import site.addzero.util.str.toUnderLineCase


fun PsiClass.toDefaultValueMap(): java.util.LinkedHashMap<Any?, Any?> {
    val outputMap: java.util.LinkedHashMap<Any?, Any?> = LinkedHashMap()
    val psiFields = this.fields
    for (field in psiFields) {
        outputMap[field.name] = field.getDefaultValue()
    }
    return outputMap
}

/**
 * 判断 PsiClass 是否为 POJO/实体类
 */
fun PsiClass.isPojo(): Boolean {
    // 排除接口和枚举
    if (isInterface || isEnum) {
        return false
    }
    // 排除抽象类（除非有实体注解）
    val isAbstract = hasModifierProperty(PsiModifier.ABSTRACT)

    val annotations = annotations.mapNotNull { it.qualifiedName }

    // 检查是否有实体注解
    val hasEntityAnnotation = annotations.any { it in ENTITY_ANNOTATIONS }
    val hasTableAnnotation = annotations.any { it in TABLE_ANNOTATIONS }
    val hasLombokAnnotation = annotations.any { it in LOMBOK_ANNOTATIONS }

    // 如果是抽象类，只有带实体注解才认为是 POJO
    if (isAbstract) {
        return hasEntityAnnotation || hasTableAnnotation
    }

    // 非抽象类：有任何相关注解即可
    return hasEntityAnnotation || hasTableAnnotation || hasLombokAnnotation
}

// ============ 集合类型判断相关 ============


/**
 * 判断 PsiClass 是否为集合类型
 */
fun PsiClass.isCollectionType(): Boolean {
    val qualifiedName = qualifiedName ?: return false

    // 检查是否为集合类型
    return COLLECTION_TYPE_FQ_NAMES.any { qualifiedName.startsWith(it) } ||
            supers.any { superClass ->
                val superQualifiedName = superClass.qualifiedName ?: return@any false
                COLLECTION_TYPE_FQ_NAMES.any { superQualifiedName.startsWith(it) }
            }
}


fun PsiClass.comment(): String? {
    val string = this.docComment?.text ?: ""
    return string
}

fun PsiClass.resolveClassByName(className: String, project: Project): PsiClass? {
    val classes = PsiShortNamesCache.getInstance(project)
        .getClassesByName(className, GlobalSearchScope.projectScope(project))

    return when {
        classes.isEmpty() -> null
        classes.size == 1 -> classes[0]
        else -> findClassFromImports(classes)
    }
}

fun PsiClass.findClassFromImports(classes: Array<PsiClass>): PsiClass? {
    val containingFile = this.containingFile as? PsiJavaFile ?: return null
    val importList = containingFile.importList ?: return null
    val importedQualifiedNames = importList.importStatements.mapNotNull { it.qualifiedName }.toSet()

    return classes.firstOrNull { psiClass ->
        val qualifiedName = psiClass.qualifiedName
        qualifiedName != null && importedQualifiedNames.contains(qualifiedName)
    }
}

fun PsiClass.toMap(): Map<String, Any?> {
    val associate = fields.associate {
        val name1 = it.name
        name1 to it.getDefaultValue()
    }
    return associate
}

fun PsiClass.docComment(): String {
    return cleanDocComment(this.docComment?.text)
}

fun PsiClass.importList(): List<String?>? {
    val importList = toPsiJavaFile().importList
    val importStatements = importList?.importStatements
    val map = importStatements?.map { it.qualifiedName }
    return map
}

fun PsiClass.toPsiJavaFile(): PsiJavaFile {
    val targetClassContainingFile = this.containingFile as PsiJavaFile
    return targetClassContainingFile
}

fun PsiClass.packageName(): String {
    val packageName = toPsiJavaFile().packageName
    return packageName
}

/**
 * 推断数据库表名
 * 优先从注解获取，否则使用类名转下划线命名
 */
fun PsiClass.guessTableName(): String? {
    val text = name?.toUnderLineCase()
    // 获取所有注解
    val guessTableNameByAnno = guessTableNameByAnno()

    val firstNonBlank = cn.hutool.core.util.StrUtil.firstNonBlank(guessTableNameByAnno, text)
    return firstNonBlank
}

/**
 * 从注解中推断表名
 * 支持 MyBatis Plus、Jimmer、JPA 的表名注解
 */
fun PsiClass.guessTableNameByAnno(): String? {
    val annotations = annotations
    val guessTableName = annotations.guessTableName()
    val any = guessTableName ?: (comment())
    return any
}

/**
 * 如果类实现了带 @Entity 或 @MappedSuperclass 注解的接口，
 * 则也会包含这些接口中的方法
 */
fun PsiClass.jimmerProperty(): List<PsiMethod> {
    val supers = interfaces.filter { interfaceClass ->
        interfaceClass.annotations.any {
            it.qualifiedName in listOf(JIMMER_ENTITY, MAPPED_SUPERCLASS)
        }
    }
    return methods.toList() + supers.map { it.jimmerProperty() }.flatten()
}


