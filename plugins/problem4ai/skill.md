# Problem4AI HTTP Skill

## 概述

Problem4AI 现在只暴露本地 HTTP JSON 接口，并在插件启动时自动安装内置 skill 到：

- `~/.codex/skills/problem4ai-http`
- `~/.claude/skills/problem4ai-http`

AI 侧通过 skill 自带脚本发现当前工作区对应的 IDEA 项目，再调用 Problem4AI 的本地 HTTP 接口获取诊断信息。

## 服务地址

插件启动后会在本地启动 HTTP 服务：

```text
http://127.0.0.1:8964
```

如果端口被占用，会自动尝试 `8965`、`8966` 等。

## HTTP 接口

### 健康检查

```bash
curl http://127.0.0.1:8964/health
```

### 服务信息

```bash
curl http://127.0.0.1:8964/api/v1/info
```

返回值包含：

- `project`
- `projectBasePath`
- `port`
- `httpBaseUrl`

### 项目统计

```bash
curl http://127.0.0.1:8964/api/v1/stats
```

### 全部诊断

```bash
curl http://127.0.0.1:8964/api/v1/diagnostics
```

### 仅错误

```bash
curl http://127.0.0.1:8964/api/v1/errors
```

### 单文件诊断

```bash
curl "http://127.0.0.1:8964/api/v1/file-diagnostics?filePath=src/main/kotlin/demo/Main.kt"
```

### 重新扫描

```bash
curl http://127.0.0.1:8964/api/v1/refresh
```

### 生成修复提示词

```bash
curl "http://127.0.0.1:8964/api/v1/fix-prompt?filePath=src/main/kotlin/demo/Main.kt"
```

## Skill 方式

优先使用自动安装的 skill，而不是手写 curl。

skill 自带脚本：

```bash
python3 scripts/problem4ai_http.py diagnostics --cwd "$PWD"
python3 scripts/problem4ai_http.py file-diagnostics --cwd "$PWD" --file path/to/File.kt
python3 scripts/problem4ai_http.py fix-prompt --cwd "$PWD" --file path/to/File.kt
```

脚本会：

1. 扫描 `127.0.0.1:8964-8999`
2. 读取每个服务的 `/api/v1/info`
3. 根据 `projectBasePath` 匹配当前工作区
4. 再调用对应 HTTP 接口

## 故障排查

### 没有发现服务

- 确认 IntelliJ IDEA 已打开项目
- 确认 Problem4AI 插件已启用
- 等待项目首次扫描完成

### 匹配到错误项目

- 传入正确的 `--cwd`
- 如果同时打开多个 IDEA 项目，优先让 AI 在目标仓库目录下执行脚本
