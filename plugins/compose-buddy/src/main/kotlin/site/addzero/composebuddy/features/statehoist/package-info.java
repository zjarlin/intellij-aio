/**
 * 本地 remember state 提升能力。
 *
 * <p>适用于 composable 内部简单 remember mutableStateOf 状态。
 * 当前只处理 {@code val xxxState = remember { mutableStateOf(...) }} 这一保守模式。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.statehoist;
