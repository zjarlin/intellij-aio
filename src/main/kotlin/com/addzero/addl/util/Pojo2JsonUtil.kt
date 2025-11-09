package com.addzero.addl.util

import com.addzero.util.lsi.impl.psi.clazz.resolveClassByName
import com.addzero.util.lsi.impl.psi.field.getDefaultValue
import com.addzero.util.lsi.impl.psi.json.toJsonMap
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.util.PsiTypesUtil
import java.util.*

/**
 * POJO 到 JSON 转换工具类
 *
 * @deprecated 已迁移到 LSI 层的分类扩展包中，请使用新的扩展函数：
 * - JSON 生成：com.addzero.util.lsi.impl.psi.json.toJsonMap
 * - 默认值获取：com.addzero.util.lsi.impl.psi.field.getDefaultValue
 * - 类解析：com.addzero.util.lsi.impl.psi.clazz.resolveClassByName
 */
@Deprecated(
    message = "Migrated to LSI layer with categorized extensions",
    replaceWith = ReplaceWith(
        "psiClass.toJsonMap(project)",
        "com.addzero.util.lsi.impl.psi.json.toJsonMap"
    )
)
object Pojo2JsonUtil {
    /**
     * 生成 POJO 的 Map 表示
     * @deprecated 使用 PsiClass.toJsonMap(project) 替代
     */
    @Deprecated(
        "Use PsiClass.toJsonMap(project) instead",
        ReplaceWith("this.toJsonMap(project)", "com.addzero.util.lsi.impl.psi.json.toJsonMap")
    )
    fun PsiClass.generateMap(project: Project): LinkedHashMap<Any?, Any?> {
        return this.toJsonMap(project)
    }

    /**
     * 获取字段的默认值
     * @deprecated 使用 PsiField.getDefaultValue(project) 替代
     */
    @Deprecated(
        "Use PsiField.getDefaultValue(project) instead",
        ReplaceWith("this.getDefaultValue(project)", "com.addzero.util.lsi.impl.psi.field.getDefaultValue")
    )
     fun PsiField.defaultValue(project: Project): Any {
        return this.getDefaultValue(project)
    }

    /**
     * 判断是否为 List 类型
     * @deprecated 使用 LSI 的 isCollectionType 替代
     */
    @Deprecated("Use LSI's isCollectionType instead")
    private fun PsiType.isListType(): Boolean {
        val canonicalText = this.canonicalText
        return canonicalText.startsWith("java.util.List") ||
               canonicalText.startsWith("kotlin.collections.List")
    }

    /**
     * 处理 List 类型
     * @deprecated 内部方法，已整合到 getDefaultValue 中
     */
    @Deprecated("Internal method, integrated into getDefaultValue")
    private fun PsiType.handleList(project: Project, containingClass: PsiClass): Any {
        val list: MutableList<Any?> = ArrayList()
        if (this !is PsiClassType) return list

        val parameters = this.parameters
        if (parameters.isEmpty()) return list

        val subType = parameters[0]
        val subTypeCanonicalText = subType.canonicalText

        val value = when {
            subType.isListType() -> subType.handleList(project, containingClass)
            subTypeCanonicalText == "java.lang.String" -> "str"
            subTypeCanonicalText == "java.util.Date" -> Date().time
            else -> {
                val resolvedClass = PsiTypesUtil.getPsiClass(subType)
                resolvedClass?.let { it.generateMap(project) } ?: subTypeCanonicalText
            }
        }
        list.add(value)
        return list
    }

    /**
     * 根据类名检测正确的类
     * @deprecated 使用 PsiClass.resolveClassByName(className, project) 替代
     */
    @Deprecated(
        "Use PsiClass.resolveClassByName(className, project) instead",
        ReplaceWith("this.resolveClassByName(className, project)", "com.addzero.util.lsi.impl.psi.clazz.resolveClassByName")
    )
    fun PsiClass.detectCorrectClassByName(className: String, project: Project): PsiClass? {
        return this.resolveClassByName(className, project)
    }

    /**
     * 从导入语句中查找类
     * @deprecated 使用 PsiClass.findClassFromImports(classes) 替代
     */
    @Deprecated(
        "Use PsiClass.findClassFromImports(classes) instead",
        ReplaceWith("this.findClassFromImports(classes)", "com.addzero.util.lsi.impl.psi.clazz.findClassFromImports")
    )
    fun PsiClass.findClassFromImports(classes: Array<PsiClass>): PsiClass? {
        val containingFile = this.containingFile as? PsiJavaFile ?: return null
        val importList = containingFile.importList ?: return null
        val importedQualifiedNames = importList.importStatements.mapNotNull { it.qualifiedName }.toSet()

        return classes.firstOrNull { psiClass ->
            val qualifiedName = psiClass.qualifiedName
            qualifiedName != null && importedQualifiedNames.contains(qualifiedName)
        }
    }
}
