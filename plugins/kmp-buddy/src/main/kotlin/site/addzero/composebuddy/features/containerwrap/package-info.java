/**
 * 布局容器高阶封装能力。
 *
 * <p>适用于在 composable 内部选中布局容器后，将其提取成只暴露 content slot 的高阶容器。
 * 当前只处理常见布局容器 Box/Column/Row/LazyColumn/LazyRow。
 * 触发入口：Alt+Enter，且必须存在用户选区。
 * 当前实现状态：第一阶段可用，优先保证嵌套容器下按选区与光标选中目标容器。
 */
package site.addzero.composebuddy.features.containerwrap;
