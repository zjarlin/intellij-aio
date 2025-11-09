package site.addzero.addl

import com.fasterxml.jackson.annotation.JsonPropertyDescription

// FormDTO类，用于在数据传输过程中承载表单相关的信息
data class FormDTO(
    // 使用JsonPropertyDescription注解描述tableName属性在JSON序列化和反序列化中的含义
    // 这里表示这个属性对应着表的中文名
    @field:JsonPropertyDescription("表中文名,只能是汉字中文") val tableName: String = "",
    // tableEnglishName属性，用于表示表的英文名称
    @field:JsonPropertyDescription("表英文名称,下划线格式") val tableEnglishName:
    String = "",
    // dbType属性，用于表示数据库的类型
    @field:JsonPropertyDescription(" dbType的候选项为(全部小写字母) mysql oracle pg dm ")
    var dbType: String = "",
    // dbName属性，用于表示数据库的名称
    @field:JsonPropertyDescription("databasename,达梦dm可以给出,其余数据库类型都留空,json不要出现//注释") val dbName: String = "",
    // fields属性，用于表示表中的字段列表
    @field:JsonPropertyDescription("字段列表") var fields: List<FieldDTO> = emptyList(),
)

// FieldDTO类，用于在数据传输过程中承载表单字段相关的信息
data class FieldDTO(
    // javaType属性，用于表示字段对应的Java类型
    @field:JsonPropertyDescription("javaType候选项为(区分大小写字母) Integer long String boolean Date LocalTime LocalDateTime BigDecimal double ")
    var javaType: String = "",
    // fieldName属性，用于表示字段的名称
    @field:JsonPropertyDescription("字段名称") var fieldName: String = "",
    // fieldChineseName属性，用于表示字段的中文名
    @field:JsonPropertyDescription("字段中文名") var fieldChineseName: String = "",
)
