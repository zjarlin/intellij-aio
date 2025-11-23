package site.addzero.addl.autoddlstarter.generator.consts

/**
 * 已废弃的数据库类型常量
 * 
 * 请直接使用字符串字面量：
 * - "mysql" 代替 MYSQL
 * - "oracle" 代替 ORACLE  
 * - "pg" 代替 POSTGRESQL
 * - "dm" 代替 DM
 * - "h2" 代替 H2
 * - "tdengine" 代替 TDENGINE
 */

@Deprecated(
    message = "Use string literal \"mysql\" instead",
    replaceWith = ReplaceWith("\"mysql\""),
    level = DeprecationLevel.WARNING
)
const val MYSQL = "mysql"

@Deprecated(
    message = "Use string literal \"oracle\" instead",
    replaceWith = ReplaceWith("\"oracle\""),
    level = DeprecationLevel.WARNING
)
const val ORACLE = "oracle"

@Deprecated(
    message = "Use string literal \"pg\" instead",
    replaceWith = ReplaceWith("\"pg\""),
    level = DeprecationLevel.WARNING
)
const val POSTGRESQL = "pg"

@Deprecated(
    message = "Use string literal \"dm\" instead",
    replaceWith = ReplaceWith("\"dm\""),
    level = DeprecationLevel.WARNING
)
const val DM = "dm"

@Deprecated(
    message = "Use string literal \"h2\" instead",
    replaceWith = ReplaceWith("\"h2\""),
    level = DeprecationLevel.WARNING
)
const val H2 = "h2"

@Deprecated(
    message = "Use string literal \"tdengine\" instead",
    replaceWith = ReplaceWith("\"tdengine\""),
    level = DeprecationLevel.WARNING
)
const val TDENGINE = "tdengine"
