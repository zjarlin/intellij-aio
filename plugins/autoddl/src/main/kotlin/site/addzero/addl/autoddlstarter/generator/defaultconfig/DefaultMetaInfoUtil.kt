package site.addzero.addl.autoddlstarter.generator.defaultconfig

//import io.swagger.v3.oas.annotations.media.Schema
//import org.babyfish.jimmer.sql.Table
import site.addzero.util.lsi.anno.Comment
import site.addzero.addl.autoddlstarter.generator.consts.MYSQL
import site.addzero.addl.autoddlstarter.generator.entity.JavaFieldMetaInfo
import site.addzero.addl.ktututil.toUnderlineCase
import site.addzero.util.str.removeNotChinese
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field


interface IMetaInfoUtil {

    /**
     * 数据库类型
     * @return [String]
     */
    fun mydbType(): String

    /**
     * 表名如何获取
     * @param [clazz]
     * @return [String]
     */
    fun getTableEnglishNameFun(clazz: Class<*>): String

    /**
     * 表注释如何获取
     * @param [clazz]
     * @return [String]
     */
    fun getTableChineseNameFun(clazz: Class<*>): String

    fun getColumnNameFun(element: JavaFieldMetaInfo): String

    /**
     * 字段的注释如何获取
     * @param [method]
     * @return [String]
     * @param [element]
     * @return [String]
     */
    fun getCommentFun(element: AnnotatedElement): String
}

/**
 * 元数据信息默认实现，用于获取表名、表注释等元数据信息
 * @author zjarlin
 * @date 2024/09/25
 */
object DefaultMetaInfoUtil : IMetaInfoUtil {

    override fun mydbType(): String {
        return MYSQL
    }

    override fun getTableEnglishNameFun(clazz: Class<*>): String {
        val annotation = clazz.getAnnotation<Comment>()
        return annotation?.value ?: ""
    }

    override fun getTableChineseNameFun(clazz: Class<*>): String {
        val annotation = clazz.getAnnotation(Comment::class.java) ?: return clazz.simpleName
        val value = annotation.value
        val removeNotChinese = value.removeNotChinese()
        return removeNotChinese
    }

    override fun getColumnNameFun(element: JavaFieldMetaInfo): String {
        return element.name.toUnderlineCase()
    }


    override fun getCommentFun(element: AnnotatedElement): String {
        val annotation = element.getAnnotation(Comment::class.java)
        annotation ?: return ""

        if (element is Field) {
            return if (BaseMetaInfoUtil.isPrimaryKeyBoolean(element.name)) "主键" else return annotation.value

        }
        return ""
    }
}
