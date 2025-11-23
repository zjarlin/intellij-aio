package site.addzero.addl.action.anycodegen.entity

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField

/**
 * LSI 类元信息
 * 用于代码生成的轻量级数据传输对象
 */
data class LsiClassMetaInfo(
    /**
     * 包名
     */
    val packageName: String?,
    
    /**
     * 类名
     */
    val className: String?,
    
    /**
     * 类注释
     */
    val classComment: String?,
    
    /**
     * 全限定名
     */
    val qualifiedName: String?,
    
    /**
     * 所有字段
     */
    val fields: List<LsiField>,
    
    /**
     * 原始 LsiClass（如果需要更多信息可以使用）
     */
    val lsiClass: LsiClass
) {
    companion object {
        /**
         * 从 LsiClass 创建 LsiClassMetaInfo
         */
        fun from(lsiClass: LsiClass): LsiClassMetaInfo {
            val qualifiedName = lsiClass.qualifiedName
            val packageName = qualifiedName?.substringBeforeLast('.', "")
            
            return LsiClassMetaInfo(
                packageName = packageName.takeIf { it.isNotEmpty() },
                className = lsiClass.name,
                classComment = lsiClass.comment,
                qualifiedName = qualifiedName,
                fields = lsiClass.fields,
                lsiClass = lsiClass
            )
        }
    }
}
