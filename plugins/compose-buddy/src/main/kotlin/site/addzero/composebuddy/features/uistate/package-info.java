/**
 * 最小 UiState 提取能力。
 *
 * <p>适用于 composable 直接吃大对象参数，但函数体只读取部分字段的场景。
 * 当前只处理可静态解析字段类型、且函数体中以 {@code state.xxx} 形式读取的对象参数。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，生成最小 UiState 数据类并更新调用点。
 */
package site.addzero.composebuddy.features.uistate;
