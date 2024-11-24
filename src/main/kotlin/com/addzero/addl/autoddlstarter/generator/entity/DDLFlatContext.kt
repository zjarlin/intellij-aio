package com.addzero.addl.autoddlstarter.generator.entity

fun DDLContext.toDDLContext(): List<DDLFLatContext> {
    val map = this.dto.map {
        DDLFLatContext(this.tableChineseName, this.tableEnglishName, this.databaseType, this.databaseName, it.colName, it.colType, it.colComment, it.colLength, it.isPrimaryKey, it.isSelfIncreasing)
    }
    return map
}


fun List<DDLFLatContext>.toDDLContext(): List<DDLContext> {
    // 按 tableChineseName 和 tableEnglishName 分组
    return this.groupBy { it.databaseType to it.tableEnglishName }.map { (tableKey, flatContexts) ->
        // 提取合并后的 DDLContext
        DDLContext(tableChineseName = tableKey.first, tableEnglishName = tableKey.second, databaseType = flatContexts.first().databaseType, // 假设所有记录 databaseType 一致
            databaseName = flatContexts.first().databaseName, // 假设所有记录 databaseName 一致
            dto = flatContexts.map { flatContext ->
                // 将每个 FlatContext 转换为 RangeContext
                DDlRangeContext(
                    colName = flatContext.colName, colType = flatContext.colType, colComment = flatContext.colComment, colLength = flatContext.colLength, isPrimaryKey = flatContext.isPrimaryKey, isSelfIncreasing = flatContext.isSelfIncreasing
                )
            })
    }
}

data class DDLFLatContext(
    val tableChineseName: String,
    var tableEnglishName: String,
    val databaseType: String,
    val databaseName: String = "",

    var colName: String,
    val colType: String,
    val colComment: String,
    val colLength: String,
    val isPrimaryKey: String,
    val isSelfIncreasing: String,
)