/**
 * remember/effect key 归一化能力。
 *
 * <p>适用于 remember、LaunchedEffect、DisposableEffect 等副作用未声明 key 或 key 冗余的场景。
 * 当前默认基于捕获到的函数形参做静态推断。
 * 触发入口：Inspection 与 Alt+Enter Intention。
 * 当前实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.effectkeys;
