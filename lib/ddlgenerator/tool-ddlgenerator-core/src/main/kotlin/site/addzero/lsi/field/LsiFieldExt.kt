package site.addzero.lsi.field

import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.field.getArg
import site.addzero.util.lsi.field.hasAnnotation
import site.addzero.util.lsi.field.hasAnnotationIgnoreCase
import site.addzero.util.str.addSuffixIfNot
import site.addzero.util.str.containsAnyIgnoreCase

/*
 * 获取所有非主键列
 */
val LsiClass.nonPrimaryColumns: List<LsiField>
    get() = fields.filter { !it.isPrimaryKey }

/**数据库无关列  */
val LsiField.isTransient: Boolean
    get() {
        this.hasAnnotation("Transient")
        return this.annotations.any {
            it.qualifiedName?.endsWith(".Transient") == true
        }
    }

/** 主键列 */
val LsiField.isPrimaryKey: Boolean
    get() {
        val hasAnnotation = this.hasAnnotationIgnoreCase("Id")
        val equals = this.name.equals("id", ignoreCase = true)
        return hasAnnotation || equals
    }

/**
 * 判断是否为数据库字段
 * 数据库字段需要满足：非静态 && 非集合类型
 */
val LsiField.isDbField: Boolean
    get() = !isStatic && !isCollectionType

/**
 * 长度
 */
val LsiField.length: Int
    get() {
        val arg = this.getArg("Length")
        val toInt = arg?.toInt()
        return toInt ?: -1
    }

/** 整数位数 */
val LsiField.precision: Int
    get() {
        val arg = this.getArg("Precision")
        return arg?.toInt() ?: -1
    }

/** 小数位数 */
val LsiField.scale: Int
    get() {
        val arg = this.getArg("Scale")
        return arg?.toInt() ?: -1
    }

/**  是否自增*/
val LsiField.isAutoIncrement: Boolean
    get() {
        val arg = this.getArg("GeneratedValue", "strategy")
        val containsAnyIgnoreCase = arg?.containsAnyIgnoreCase("IDENTITY", "Auto")
        if (containsAnyIgnoreCase != null) {
            return containsAnyIgnoreCase
        }
        return false
    }

/**  是否序列*/
val LsiField.iSsequence: Boolean
    get() {
        return isIdTypeByAnno("SEQUENCE")
    }


/**  是否序列*/
val LsiField.isUUID: Boolean
    get() {
        return isIdTypeByAnno("UUIDIdGenerator")
    }

private fun LsiField.isIdTypeByAnno(strategy: String): Boolean {
    val arg = this.getArg("GeneratedValue", "strategy")
//        "GenerationType.IDENTITY"
    val contains = arg?.contains(strategy, ignoreCase = true)
    if (contains != null) {
        return contains
    }
    return false
}

/** 字段长度字符串 例如  (255) */
val LsiField.lengthStr: String
    get() {
        val length = this.length
        val toString = length.toString()
        return toString.addSuffixIfNot("(").addSuffixIfNot(")")
    }

    /**
     * 判断是否为长文本类型
     */
    fun LsiField.isTextType(): Boolean {
        val textKeywords = listOf("url", "base64", "text", "path", "introduction", "content", "description")
        return textKeywords.any { name?.contains(it, ignoreCase = true) ?: false }
    }

