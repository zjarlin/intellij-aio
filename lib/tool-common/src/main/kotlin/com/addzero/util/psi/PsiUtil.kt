package com.addzero.util.psi

import com.addzero.util.lsi.*
import com.addzero.util.lsi.field.LsiField
import com.addzero.util.lsi.impl.psi.PsiLsiType
import com.addzero.util.lsi.method.LsiMethod
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import site.addzero.util.str.cleanDocComment

/**
 * Java PsiClass 专用工具类
 *
 * @deprecated 大部分方法已迁移到 LSI 层，建议使用 LSI 相关方法
 */
@Deprecated("Most methods migrated to LSI layer")
object PsiUtil {

    fun addComment(project: Project, field: PsiField) {
        // 创建新的文档注释
        val factory = PsiElementFactory.getInstance(project)
        val newDocComment = factory.createDocCommentFromText("/** */")
        field.addBefore(newDocComment, field.firstChild)
    }

    /**
     * 从 Jimmer 接口实体中提取字段信息
     * @deprecated 使用 PsiClass.extractInterfaceFields() 从 LsiExtensions
     */
    @Deprecated(
        "Use PsiClass.extractInterfaceFields() from LsiExtensions instead",
        ReplaceWith("psiClass.extractInterfaceFields()", "com.addzero.util.lsi.extractInterfaceFields")
    )
    fun extractInterfaceMetaInfo(psiClass: PsiClass): List<LsiMethod> {
        return psiClass.extractInterfaceFields()
    }

    /**
     * 获取 PsiType 对应的 Java Class
     * @deprecated 使用 LsiType.javaClass
     */
    @Deprecated(
        "Use LsiType.javaClass instead",
        ReplaceWith("PsiLsiType(psiType).javaClass", "com.addzero.util.lsi.impl.psi.PsiLsiType")
    )
    fun getJavaClassFromPsiType(psiType: PsiType): Class<*> {
        return PsiLsiType(psiType).javaClass
    }

    /**
     * 获取方法注释
     * @deprecated 使用 LsiMethod.comment
     */
    @Deprecated(
        "Use LsiMethod.comment or method.toLsiMethod().comment instead",
        ReplaceWith("method.toLsiMethod().comment ?: \"\"", "com.addzero.util.lsi.toLsiMethod")
    )
    fun getCommentFunByMethod(method: PsiMethod): String {
        return method.toLsiMethod().comment ?: ""
    }

    /**
     * 获取类元信息（类注释和表名）
     * @deprecated 使用 LsiClass.comment 和 LsiClass.guessTableName
     */
    @Deprecated(
        "Use LsiClass.comment and LsiClass.guessTableName instead",
        ReplaceWith("psiClass.toLsiClass().let { it.comment to it.guessTableName }", "com.addzero.util.lsi.toLsiClass")
    )
    fun getClassMetaInfo(psiClass: PsiClass): Pair<String, String?> {
        val classComment = cleanDocComment(psiClass.docComment?.text)
        return Pair(classComment, psiClass.toLsiClass().guessTableName)
    }

    /**
     * 获取 Java 类的字段元数据
     * @deprecated 使用 PsiClass.getJavaFields() 或 PsiClass.toLsiClass().dbFields
     */
    @Deprecated(
        "Use PsiClass.getJavaFields() or PsiClass.toLsiClass().dbFields instead",
        ReplaceWith("psiClass.getJavaFields()", "com.addzero.util.lsi.getJavaFields")
    )
    fun getJavaFieldMetaInfo(psiClass: PsiClass): List<LsiField> {
        return psiClass.getJavaFields()
    }
}
