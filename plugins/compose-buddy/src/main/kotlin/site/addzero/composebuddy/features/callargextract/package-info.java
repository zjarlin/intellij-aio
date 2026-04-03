/**
 * Compose 调用参数外提能力。
 *
 * <p>用于把当前 Compose 调用中的占位参数提取为外层 composable 的显式形参，并将调用点改为转发该形参。
 *
 * <p>适用场景：
 * <ul>
 *   <li>光标位于单个参数上，只提取当前参数。</li>
 *   <li>光标位于调用表达式上，批量提取当前调用中的占位参数。</li>
 * </ul>
 *
 * <p>占位参数包括 TODO()、null、空字符串以及缺失表达式的具名参数。
 *
 * <p>不处理的边界：
 * <ul>
 *   <li>不会覆盖外层 composable 中已存在的同名形参。</li>
 *   <li>当前阶段只处理具名参数，不改位置参数。</li>
 *   <li>若无法解析真实类型，则回退为 Any。</li>
 * </ul>
 *
 * <p>触发入口：Alt+Enter。
 *
 * <p>实现状态：第一阶段可用。
 */
package site.addzero.composebuddy.features.callargextract;
