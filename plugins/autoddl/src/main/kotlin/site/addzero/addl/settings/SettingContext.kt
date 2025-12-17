package site.addzero.addl.settings

/**
 * AutoDDL 设置上下文
 */
object SettingContext {

    /**
     * 当前设置
     */
    object settings {
        /**
         * Controller 生成风格
         * INHERITANCE - 继承式（简洁版）
         * STANDALONE - 独立式（完整版）
         */
        var controllerStyle: String = "INHERITANCE"

        /**
         * 其他设置项可以在这里添加
         */
        var enableAutoSave: Boolean = true
        var targetPackage: String? = null
    }
}