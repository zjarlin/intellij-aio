#  Batch fix Kotlin null-safety errors in one click. Automatically replaces unsafe dot calls (.)
        with safe calls (?.) on nullable receivers, and fixes return type mismatches with .orEmpty()
        or elvis operator. Shows an editor banner when fixable null-safety errors are detected.

一键批量修复 Kotlin 文件中的空安全错误。

---

## 功能

- **UNSAFE_CALL 修复**：自动将 nullable receiver 上的 `.` 替换为 `?.`
- **RETURN_TYPE_MISMATCH 修复**：返回类型不匹配时，集合类型自动加 `.orEmpty()`，其他类型加 `?: error("unexpected null")`
- **编辑器横幅**：文件存在空安全错误时，编辑器顶部自动出现修复横幅，点击即可批量修复；修复后横幅自动消失
- **动态刷新**：编辑代码产生新错误时横幅自动出现，基于 `DaemonCodeAnalyzer` 监听高亮完成事件
- **右键菜单 / Tools 菜单**：不依赖横幅也能触发修复

## 支持的错误类型

| 错误 | 修复方式 |
|------|---------|
| `Only safe (?.) or non-null asserted (!!.) calls are allowed` | `.` → `?.` |
| `Return type mismatch: expected 'List<T>', actual 'List<T>?'` | 加 `.orEmpty()` |
| `Return type mismatch` (非集合) | 加 `?: error("unexpected null")` |

## 使用方式

1. 打开含空安全错误的 `.kt` / `.kts` 文件
2. 编辑器顶部出现黄色横幅，显示可修复的错误数量
3. 点击「一键修复 → ?.」按钮
4. 修复完成后横幅自动消失，右下角弹出通知

也可以通过右键菜单或 Tools → Fix Null Safety → ?. 触发。

## 要求

- IntelliJ IDEA 2024.2+ (build 242+)
- Kotlin 插件已安装

## 作者

zjarlin — [gitee.com/zjarlin](https://gitee.com/zjarlin)
