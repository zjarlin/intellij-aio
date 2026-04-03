/**
 * Events 对象提取能力。
 *
 * <p>适用于多个 onXxx 回调参数膨胀的 composable。
 * 当前将回调参数收拢成 data class 形式的 Events 对象，并更新调用点。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，不生成 interface。
 */
package site.addzero.composebuddy.features.events;
