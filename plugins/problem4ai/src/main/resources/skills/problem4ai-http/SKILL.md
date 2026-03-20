---
name: problem4ai-http
description: Use when the user wants current IntelliJ IDEA diagnostics, compile errors, warnings, or file-level problems from the local Problem4AI plugin. This skill discovers the matching local Problem4AI HTTP server for the current workspace and fetches diagnostics through its HTTP API.
---

# Problem4AI HTTP

Use this skill when the user asks what errors or warnings currently exist in the IDE, asks to inspect one file's diagnostics, or wants to build a fix prompt from Problem4AI instead of rerunning the whole compiler manually.

## Quick Start

Prefer the bundled script over handwritten curl. The plugin port can move if multiple projects are open, and the script matches the server against the current workspace.

Common commands:

```bash
python3 scripts/problem4ai_http.py info --cwd "$PWD"
python3 scripts/problem4ai_http.py stats --cwd "$PWD"
python3 scripts/problem4ai_http.py diagnostics --cwd "$PWD"
python3 scripts/problem4ai_http.py errors --cwd "$PWD"
python3 scripts/problem4ai_http.py file-diagnostics --cwd "$PWD" --file path/to/File.kt
python3 scripts/problem4ai_http.py refresh --cwd "$PWD"
python3 scripts/problem4ai_http.py fix-prompt --cwd "$PWD" --file path/to/File.kt
```

If the user gives an absolute file path, pass it as-is. If they give a relative path, resolve it against `--cwd`.

## Workflow

1. Start with `diagnostics` or `errors` for the current workspace.
2. If the user points at one file, use `file-diagnostics`.
3. If results look stale or empty, call `refresh`, wait briefly, then read diagnostics again.
4. If no server matches, tell the user to open the project in IntelliJ IDEA with the Problem4AI plugin enabled and wait for the scan to complete.

## Direct HTTP Fallback

If you cannot use the script, probe `http://127.0.0.1:8964-8999/api/v1/info` and choose the server whose `projectBasePath` best matches the current workspace.

Available HTTP endpoints:

- `GET /api/v1/info`
- `GET /api/v1/stats`
- `GET /api/v1/diagnostics`
- `GET /api/v1/errors`
- `GET /api/v1/file-diagnostics?filePath=...`
- `GET /api/v1/refresh`
- `GET /api/v1/fix-prompt?filePath=...`

The API returns JSON. Use that JSON directly instead of reformatting it through other local scanners first.
