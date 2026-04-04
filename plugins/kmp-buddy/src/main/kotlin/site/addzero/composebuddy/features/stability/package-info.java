/**
 * 参数稳定性检查能力。
 *
 * <p>适用于 composable 直接暴露明显不稳定集合类型的场景。
 * 当前只对 MutableList、ArrayList、MutableSet、HashMap、MutableMap 提供检查与 QuickFix。
 * 触发入口：Inspection 与 Alt+Enter QuickFix。
 * 当前实现状态：第一阶段可用，不做激进自动语义迁移。
 */
package site.addzero.composebuddy.features.stability;
