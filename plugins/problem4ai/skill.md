# Problem4AI MCP 服务

## 概述

Problem4AI 实现了 **Model Context Protocol (MCP)** 服务器，通过 HTTP 协议暴露诊断功能给 AI。

支持 MCP 协议的 AI 工具（如 Claude Desktop、Claude Code）可以直接调用工具、访问资源和获取提示模板。

## 服务地址

插件启动后自动在本地启动 HTTP 服务：
```
http://localhost:8964/mcp/v1/
```

如果端口被占用，会自动尝试 8965、8966 等。

## MCP 协议端点

### 1. 初始化
```
POST /mcp/v1/initialize
```

### 2. 工具 (Tools)
```
GET  /mcp/v1/tools/list          # 列出所有可用工具
POST /mcp/v1/tools/call          # 调用工具
```

### 3. 资源 (Resources)
```
GET /mcp/v1/resources/list       # 列出所有资源
GET /mcp/v1/resources/read?uri=  # 读取资源
```

### 4. 提示 (Prompts)
```
GET /mcp/v1/prompts/list         # 列出所有提示模板
GET /mcp/v1/prompts/get?name=    # 获取提示模板
```

## 可用工具

### get_project_stats
**描述**: 获取项目诊断统计信息

**输入**: 无

**输出示例**:
```json
{
  "project": "my-project",
  "fileCount": 5,
  "errorCount": 3,
  "warningCount": 2
}
```

### get_all_errors
**描述**: 获取所有错误文件列表

**输入**: 无

**输出示例**:
```json
{
  "count": 5,
  "files": [
    {
      "file": "/path/to/File.java",
      "errors": [
        {"line": 42, "message": "Cannot resolve symbol"}
      ]
    }
  ]
}
```

### get_all_warnings
**描述**: 获取所有警告信息

### get_file_diagnostics
**描述**: 获取指定文件的诊断信息

**输入**:
```json
{
  "filePath": "UserService.java"
}
```

### refresh_diagnostics
**描述**: 触发重新扫描

### generate_fix_prompt
**描述**: 生成 AI 修复提示词

**输入**:
```json
{
  "filePath": "UserService.java"
}
```

## 可用资源

### diagnostics://project/overview
**描述**: 项目诊断总览 JSON

### diagnostics://errors
**描述**: 仅错误列表 JSON

## 可用提示模板

### fix_all_errors
**描述**: 生成修复所有错误的提示词

### code_review
**描述**: 生成代码审查提示词

## 简单 HTTP API（兼容旧版）

### 健康检查
```bash
curl http://localhost:8964/health
```

### 获取统计
```bash
curl http://localhost:8964/stats
```

### 获取诊断
```bash
curl http://localhost:8964/diagnostics
```

## AI 使用示例

### 场景 1: 检查项目错误
```
用户: "我的项目有什么错误？"

AI 应该:
1. 调用 GET /mcp/v1/tools/list 查看可用工具
2. 找到 get_all_errors 工具
3. 调用 POST /mcp/v1/tools/call {"name": "get_all_errors", "arguments": {}}
4. 向用户展示结果
```

### 场景 2: 修复特定文件
```
用户: "帮我修复 UserService.java"

AI 应该:
1. 调用 POST /mcp/v1/tools/call {"name": "generate_fix_prompt", "arguments": {"filePath": "UserService.java"}}
2. 获取提示词后处理修复
```

### 场景 3: 代码审查
```
用户: "审查我的代码"

AI 应该:
1. 调用 GET /mcp/v1/prompts/get?name=code_review
2. 获取提示模板后进行分析
```

## Claude Desktop 配置

编辑 `~/Library/Application Support/Claude/claude_desktop_config.json`：

```json
{
  "mcpServers": {
    "problem4ai": {
      "command": "curl",
      "args": [
        "-s",
        "http://localhost:8964/mcp/v1/initialize"
      ]
    }
  }
}
```

或者使用 HTTP MCP 配置：

```json
{
  "mcpServers": {
    "problem4ai": {
      "url": "http://localhost:8964/mcp/v1"
    }
  }
}
```

## 故障排查

**服务未响应**
- 确认 IDEA 已启动且插件已加载
- 检查端口：`lsof -i :8964`
- 查看 IDEA 日志

**返回空数据**
- 等待扫描完成（看 Problem4AI 面板）
- 调用 refresh_diagnostics 触发扫描
- 检查排除规则

**MCP 协议错误**
- 确认使用正确的协议版本（2024-11-05）
- 检查 Content-Type: application/json
