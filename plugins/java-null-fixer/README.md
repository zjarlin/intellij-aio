#  Batch fix Java null-pointer warnings in one click. Automatically applies IntelliJ built-in
        Quick Fixes (Surround with null check, etc.) to all "may produce NullPointerException" warnings.
        Shows an editor banner when fixable null-pointer issues are detected.
一键批量修复 Java 文件中的空指针警告，自动调用 IntelliJ 内置 Quick Fix。

---

## 功能

- **批量 Quick Fix**：扫描文件中所有空指针相关警告/错误，批量执行 IntelliJ 内置的 "Surround with null check" 等修复
- **编辑器横幅**：文件存在可修复的空指针问题时，编辑器顶部自动出现修复横幅
- **动态刷新**：编辑代码后横幅自动更新，基于 `DaemonCodeAnalyzer` 监听高亮完成事件
- **智能优先级**：优先选择 "Surround with null check"，其次 "Add null check"、"Replace with" 等
- **右键菜单 / Tools 菜单**：不依赖横幅也能触发修复

## 支持的警告模式

- `may produce 'NullPointerException'`
- `might be null` / `could be null`
- `Dereference of` nullable
- `passing 'null'` argument
- `@Nullable` 相关警告
- 以及更多 IntelliJ Java 空指针检查

## 使用方式

1. 打开含空指针警告的 `.java` 文件
2. 编辑器顶部出现黄色横幅，显示可修复的问题数量
3. 点击「一键修复 (Null Check)」按钮
4. 修复完成后横幅自动消失，右下角弹出通知

也可以通过右键菜单或 Tools → Fix Java Null Safety (Batch) 触发。

## 原理

插件不自己实现修复逻辑，而是复用 IntelliJ 内置的 Quick Fix 基础设施：
1. 从 `DaemonCodeAnalyzer` 获取文件的所有高亮信息
2. 筛选出空指针相关的警告
3. 收集每个高亮上附带的 `IntentionAction`
4. 按优先级排序，从后往前批量执行（避免偏移量变化）

## 要求

- IntelliJ IDEA 2024.2+ (build 242+)
- Java 插件已安装

## 作者

zjarlin — [gitee.com/zjarlin](https://gitee.com/zjarlin)
