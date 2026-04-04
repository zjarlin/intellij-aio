/**
 * Compose Blocks 文件编辑器。
 *
 * <p>这里承载 Inspect / Builder 两种自定义编辑体验，并额外处理返回导航后的焦点恢复。
 * 当用户在右侧源码编辑区通过 Cmd+Click 进入其他文件，再执行 Back 返回时，
 * 当前实现会把焦点重新交还给该模式的首选编辑组件，避免键盘方向键落到左侧结构面板。
 */
package site.addzero.composeblocks.editor;
