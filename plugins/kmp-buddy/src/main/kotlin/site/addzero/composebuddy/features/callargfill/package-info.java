/**
 * Compose 调用参数自动回填能力。
 *
 * <p>用于在当前 composable 函数内，对某个调用表达式的具名参数执行“同名形参自动回填”。
 *
 * <p>适用场景：
 * <ul>
 *   <li>调用已经写出参数名，但参数值还是 TODO()、null、空字符串或缺失表达式。</li>
 *   <li>外层 composable 刚好存在同名形参，希望快速补全到当前调用。</li>
 * </ul>
 *
 * <p>不处理的边界：
 * <ul>
 *   <li>不会覆盖已有真实表达式。</li>
 *   <li>不会从局部变量、属性或其他作用域推断，只使用外层 composable 形参。</li>
 *   <li>只处理已写出参数名的具名参数，不改位置参数。</li>
 * </ul>
 *
 * <p>触发入口：Alt+Enter。
 *
 * <p>实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.callargfill;
