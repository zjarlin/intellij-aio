/**
 * 状态映射提取能力。
 *
 * <p>适用于在 UI 中直接书写 when/if 状态映射逻辑的场景。
 * 当前第一阶段要求用户选中 if/when 表达式，并提取为文件内 private mapper 函数。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，仅处理纯表达式块。
 */
package site.addzero.composebuddy.features.statemapper;
