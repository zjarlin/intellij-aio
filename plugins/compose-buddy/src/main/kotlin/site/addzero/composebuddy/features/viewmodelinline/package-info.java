/**
 * ViewModel 显式形参内联能力。
 *
 * <p>适用于 composable 直接吃一个大而全 viewModel 参数，但函数体只使用其中部分状态与事件的场景。
 * 当前只处理 {@code viewModel.xxx} 属性访问与 {@code viewModel.foo(...)} 方法调用，并把实际用到的成员提为最小形参集合。
 * 触发入口：Alt+Enter Intention。
 * 当前实现状态：第一阶段可用，调用点仅更新当前项目内可解析调用。
 */
package site.addzero.composebuddy.features.viewmodelinline;
