//package site.addzero.util.old
//
//import cn.hutool.core.util.StrUtil
//import com.intellij.util.ReflectionUtil
//import org.apache.commons.lang3.AnnotationUtils
//import org.jetbrains.kotlin.psi.KtClass
//
///**
// * Kotlin KtClass 相关工具类
// */
//object KtClassUtil {
//
//
//    fun KtClass.extractInterfaceMetaInfo(): MutableList<JavaFieldMetaInfo> {
//        val fieldsMetaInfo = mutableListOf<JavaFieldMetaInfo>()
//        // 获取所有字段
//        val fields = this?.properties()?.filter { it.isDbField() }
//
//        for (field in fields!!) {
//            if (field.isCollectionType()) {
//                continue
//            }
//            val toUnderlineCase = field.name?.toUnderlineCase()
//
//            val fieldType = field.typeReference?.text
//
//            val typeReference = field.typeReference
//            val ktFieldTypeName = typeReference?.text
//            // 返回 Java 类型的字符串表示形式
//            // 获取字段注释
//            val fieldComment = field.guessFieldComment()
//
//            // 获取字段的类对象
//            val typeClass = try {
//                ReflectionUtil.getClassOrNull(
//                    if (StrUtil.isBlank(ktFieldTypeName)) {
//                        String::class.java.name
//                    } else {
//                        ktType2RefType(ktFieldTypeName!!)
//                    }
//                )
//
//            } catch (e: ClassNotFoundException) {
//                Any::class.java // 如果类未找到，使用 Any
//            }
//
//
//            if (field.toLsiField().hasAnnotation("org.babyfish.jimmer.sql.ManyToOne")) {
//                val joingColumn = if (field.toLsiField().hasAnnotation("org.babyfish.jimmer.sql.JoinColumn")) {
//                    val map = field.annotationEntries.filter { it.shortName?.asString() == "JoinColumn" }.map {
//                        val annotationValue1 = AnnotationUtils.getAnnotationValue(it, "name")
//                        annotationValue1
//                    }.firstOrNull()
//                    map
//                } else {
//                    toUnderlineCase + "Id"
//                }
//
//
//                // 创建 JavaFieldMetaInfo 对象并添加到列表
//                val element = JavaFieldMetaInfo(joingColumn!!, typeClass, typeClass, fieldComment)
//                fieldsMetaInfo.add(element)
//                return fieldsMetaInfo
//            }
//            val fieldName = field.name // 字段名称
//            //这里优先使用数据库列名，如果没有，则使用字段名
//            val columnName = field.toLsiField().columnName
//            val finalColumn = StrUtil.firstNonBlank(columnName, fieldName)
//
//
//            // 创建 JavaFieldMetaInfo 对象并添加到列表
//            val element = JavaFieldMetaInfo(finalColumn!!, typeClass, typeClass, fieldComment)
//            fieldsMetaInfo.add(element)
//        }
//
//        return fieldsMetaInfo
//
//    }
//
//
//}
