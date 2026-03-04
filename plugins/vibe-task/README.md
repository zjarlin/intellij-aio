# A lightweight task/memo plugin for "vibe coding": Quickly record inspirations within the IDE, categorize and view them by project/module, and support JSON/Markdown import/export as well as adding completed items to the project.

一个面向「vibe coding」的轻量任务/备忘插件：在 IDE 内快速记录灵感、按项目/模块归类查看，并支持 JSON/Markdown 导入导出与把已完成事项追加到项目 `README.md`。

## 功能一览

- 任务面板（Tool Window）：左侧视图树 + 右侧任务列表
- 作用域：全局 / 当前项目 / 项目级（非模块）/ 模块级
- 任务状态：`TODO` / `IN_PROGRESS` / `DONE` / `CANCELLED`
- 导入/导出：JSON / Markdown（可复制到剪贴板或保存为文件）
- 一键追加到 `README.md`：把已完成（`DONE`）任务汇总成表格写入 `## Vibe Coding Tasks` 段落

## 入口与快捷键

- 打开面板：`View → Vibe Task`（或 `Find Action` 搜索 `Open Vibe Task Panel`）
  - Windows/Linux：`Ctrl+Shift+V` 然后 `T`
  - macOS：`⌘+Shift+V` 然后 `T`
- 快速添加任务：右键菜单
  - 编辑器右键：`Add Vibe Task`
  - Project 视图右键：`Add Vibe Task`

## 数据存储与备份

- 默认存储文件：`~/.config/JetBrains/vibe-task/vibe-tasks.txt`
- 建议定期备份该文件，或使用面板里的导出功能生成 JSON。

## “追加到 README” 的规则

- 仅处理 `DONE` 状态的任务
- 在项目根目录查找 `README.md` / `readme.md` / `Readme.md`
- 写入/更新 `## Vibe Coding Tasks` 段落，并按「项目级」与「模块名」分组生成表格

## 开发（本仓库）

- 插件模块：`plugins/vibe-task`
- 入口：`plugins/vibe-task/src/main/resources/META-INF/plugin.xml`
- 主要代码：`plugins/vibe-task/src/main/kotlin/site/addzero/vibetask`

## 注意事项

- 导入 JSON 支持「合并」或「替换」现有任务；建议先导出备份再导入。
- 该插件会在本机用户目录写入存储文件；如在公司/共享机器使用，请注意隐私与合规要求。
