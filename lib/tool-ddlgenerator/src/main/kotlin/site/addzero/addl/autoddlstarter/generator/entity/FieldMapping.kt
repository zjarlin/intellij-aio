package site.addzero.addl.autoddlstarter.generator.entity

import java.util.function.Predicate
import kotlin.reflect.KClass

/**
 * @author zjarlin
 * @since 2023/11/4 09:24
 */
data class FieldMapping(
    val predi: Predicate<JavaFieldMetaInfo>,
    val mysqlType: String,
    val pgType: String,
    val oracleType: String,
    val dmType: String,
    val h2Type: String,
    val length: String,
    val classRef: KClass<*>,
){

    var javaClassRef: String=classRef.java.name
    var javaClassSimple: String=classRef.java.simpleName

    var ktClassRef: String=classRef.qualifiedName!!
    var ktClassSimple: String=classRef.simpleName!!
}
