package site.addzero.addl.autoddlstarter.generator.entity

import site.addzero.addl.autoddlstarter.generator.factory.DDLContextFactory4UserInputMetaInfo

data class DDLContext(
    val tableChineseName: String,
    var tableEnglishName: String,
    val databaseType: String,
    val databaseName: String = "",
    val dto: List<DDlRangeContext>,
) {
}


data class DDlRangeContext(
    var colName: String,
    val colType: String,
    val colComment: String,
    val colLength: String,
    val isPrimaryKey: String,
    val isSelfIncreasing: String,
//    val autoIncrement: String,
)

data class DDLRangeContextUserInput(
    val javaType: String,
    val colName: String,
    val colComment: String,
)

fun mockkDDLContext(): DDLContext {
    val ddlRangeContexts = listOf(
        DDLRangeContextUserInput(
            colName = "id",
            colComment = "主键",
            javaType = "String",
        ),
        DDLRangeContextUserInput(
            colName = "fileUrl",
            colComment = "附件",
            javaType = "String",
        ),
        DDLRangeContextUserInput(
            colName = "Foo_bar",
            colComment = "附件",
            javaType = "double",
        ),
        DDLRangeContextUserInput(
            colName = "nameFoo",
            colComment = "名字",
            javaType = "string",
        ),
        DDLRangeContextUserInput(
            colName = "fooBar",
            colComment = "字段1",
            javaType = "String",
        ),
        DDLRangeContextUserInput(
            colName = "FooBar",
            colComment = "字段2",
            javaType = "String",
        ),
        DDLRangeContextUserInput(
            colName = "",
            colComment = "字段3",
            javaType = "localdatetime",
        ),
        DDLRangeContextUserInput(
            colName = "",
            colComment = "字段4",
            javaType = "integer",
        ),
        DDLRangeContextUserInput(
            colName = "",
            colComment = "字段5",
            javaType = "double",
        ),
        DDLRangeContextUserInput(
            colName = "",
            colComment = "字段6",
            javaType = "bigdecimal",
        ),
        DDLRangeContextUserInput(
            colName = "",
            colComment = "字段7",
            javaType = "long",
        ),
    )
    val createDDLContext = DDLContextFactory4UserInputMetaInfo.createDDLContext("用户表", "sys_user", "mysql", ddlRangeContexts)
    println(createDDLContext)
    return createDDLContext
}

fun main() {
    val mockkDDLContext = mockkDDLContext()
    println(mockkDDLContext)
}
