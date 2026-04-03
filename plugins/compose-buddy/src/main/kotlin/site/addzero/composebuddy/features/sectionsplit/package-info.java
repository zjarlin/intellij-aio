/**
 * 大型 composable 分段拆分能力。
 *
 * <p>适用于顶层布局直接承载多个大块子布局的场景。
 * 当前第一阶段基于顶层布局的直接子调用做提取，默认要求用户选区命中其中一个子块。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.sectionsplit;
