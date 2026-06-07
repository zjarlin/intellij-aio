/**
 * Compose 形参排序能力。
 *
 * <p>适用于把 composable 签名整理为 props、hoisted state、events、lambda slot 的固定分组。
 * 当前只改函数声明形参顺序；如果项目内调用点存在位置实参或尾随 lambda，则不提供 intention，避免改变调用语义。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.parametersort;
