package site.addzero.ide.config.model

/**
 * 配置项数据类
 * 代表一个具体的配置项
 */
data class ConfigItem(
    val key: String,
    val label: String,
    val description: String,
    val required: Boolean,
    val inputType: InputType,
    val options: List<SelectOption> = emptyList(),
    val tableColumns: List<TableColumn> = emptyList(), // 表格列配置
    val minRows: Int = 0, // 表格最小行数
    val maxRows: Int = -1, // 表格最大行数（-1表示无限制）
    val defaultValue: Any? = null // 默认值
)

/**
 * 下拉选项数据类
 */
data class SelectOption(
    val value: String,
    val label: String
)

/**
 * 配置字段的输入类型枚举
 */
enum class InputType {
    TEXT,       // 文本输入
    NUMBER,     // 数字输入
    PASSWORD,   // 密码输入
    TEXTAREA,   // 多行文本输入
    CHECKBOX,   // 复选框
    SELECT,     // 下拉选择
    TABLE       // 表格
}

/**
 * 表格列配置
 */
data class TableColumn(
    val key: String,
    val label: String,
    val inputType: InputType = InputType.TEXT,
    val width: Int = 150
)