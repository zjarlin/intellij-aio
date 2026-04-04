/**
 * Modifier 链提取能力。
 *
 * <p>适用于同文件重复出现的长 Modifier 链。
 * 当前识别完全相同的链文本，并提取成文件内顶层 private Modifier 扩展。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，仅处理重复 2 次及以上的完全相同链。
 */
package site.addzero.composebuddy.features.modifierchain;
