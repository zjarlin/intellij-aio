/**
 * Slot 参数提取能力。
 *
 * <p>适用于直接把大块 lambda 内联到 Compose 调用中的场景。
 * 当前第一阶段要求用户有选区，并且选中的正好是一个带 lambda 的调用表达式。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，默认提取 content slot。
 */
package site.addzero.composebuddy.features.slotextract;
