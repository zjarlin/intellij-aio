@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Description(
    val description: String,
    val defaultValue: String = "",
    val type: FieldType = FieldType.TEXT, // 新增字段用于指明组件类型
    val options: Array<String> = [] // 用于下拉框选项
)
enum class FieldType {
    TEXT,  // 文本框
    DROPDOWN  // 下拉框
}