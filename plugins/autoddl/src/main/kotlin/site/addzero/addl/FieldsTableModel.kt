
import site.addzero.util.ai.agent.dbdesign.FieldDTO
import javax.swing.table.DefaultTableModel

class FieldsTableModel : DefaultTableModel() {
    private val columnNames = arrayOf("Java Type", "Column Name", "Column Comment")

    var fields = emptyList<FieldDTO>().toMutableList()

    init {
        setColumnIdentifiers(columnNames)  // 使用列名初始化
    }
    override fun getRowCount(): Int {
       return if (fields == null) {
            0
        } else {
            fields!!.size
        }
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val field = fields[rowIndex]
        return when (columnIndex) {
            0 -> field.javaType
            1 -> field.fieldName
            2 -> field.fieldChineseName
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val field = fields[rowIndex]
        when (columnIndex) {
            0 -> field.javaType = aValue as String
            1 -> field.fieldName = aValue as String
            2 -> field.fieldChineseName = aValue as String
        }
        fireTableCellUpdated(rowIndex, columnIndex)
    }

    // 添加字段的方法
    fun addField(field: FieldDTO) {
        fields.add(field)
        fireTableRowsInserted(fields.size - 1, fields.size - 1)
    }
}
