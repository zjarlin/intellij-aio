package site.addzero.addl.autoddlstarter.generator.entity

import site.addzero.addl.autoddlstarter.generator.filterBaseEneity

fun DDLContext.toDDLContext(): List<DDLFLatContext> {


    val map = this.dto
        .distinctBy { it.colName }
        .filter { filterBaseEneity(it) }

        .map {
        DDLFLatContext(this.tableChineseName, this.tableEnglishName, this.databaseType, this.databaseName, it.colName, it.colType, it.colComment, it.colLength, it.isPrimaryKey, it.isSelfIncreasing)
    }
    return map
}


fun List<DDLFLatContext>.toDDLContext(): List<DDLContext> {
    // 按 tableChineseName 和 tableEnglishName 分组
    return this.groupBy { it.databaseType to it.tableEnglishName }.map { (tableKey, flatContexts) ->
        // 提取合并后的 DDLContext
        val first = flatContexts.first()
        val tableChineseName = tableKey.first
        DDLContext(tableChineseName = first.tableChineseName, tableEnglishName = tableKey.second, databaseType = first.databaseType, // 假设所有记录 databaseType 一致
            databaseName = first.databaseName, // 假设所有记录 databaseName 一致
            dto = flatContexts.map { flatContext ->
                // 将每个 FlatContext 转换为 RangeContext
                DDlRangeContext(
                    colName = flatContext.colName, colType = flatContext.colType, colComment = flatContext.colComment, colLength = flatContext.colLength, isPrimaryKey = flatContext.isPrimaryKey, isSelfIncreasing = flatContext.isSelfIncreasing
                )
            }
                .filter { filterBaseEneity(it) }
            .distinctBy { it.colName }
            )
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
