#!/usr/bin/env python3

import argparse
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Dict, List, Optional

PORT_START = 8964
PORT_END = 8999
TIMEOUT_SECONDS = 0.35


def normalize_path(value: Optional[str]) -> str:
    if not value:
        return ""
    try:
        return str(Path(value).expanduser().resolve())
    except OSError:
        return str(Path(value).expanduser())


def fetch_json(url: str) -> Dict:
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


def discover_servers() -> List[Dict]:
    servers = []
    for port in range(PORT_START, PORT_END + 1):
        url = f"http://127.0.0.1:{port}/api/v1/info"
        try:
            info = fetch_json(url)
        except Exception:
            continue
        if info.get("status") != "ok":
            continue
        info["port"] = port
        info["projectBasePath"] = normalize_path(info.get("projectBasePath"))
        info["httpBaseUrl"] = info.get("httpBaseUrl") or f"http://127.0.0.1:{port}"
        servers.append(info)
    return servers


def score_server(server: Dict, cwd: str) -> int:
    base_path = normalize_path(server.get("projectBasePath"))
    if not cwd or not base_path:
        return 0
    if cwd == base_path:
        return 10_000
    if cwd.startswith(base_path.rstrip(os.sep) + os.sep):
        return 8_000 + len(base_path)
    if base_path.startswith(cwd.rstrip(os.sep) + os.sep):
        return 6_000 - len(base_path)
    return 0


def choose_server(servers: List[Dict], cwd: str) -> Dict:
    if not servers:
        raise RuntimeError("No Problem4AI server found on localhost:8964-8999")

    if cwd:
        ranked = sorted(
            servers,
            key=lambda server: (score_server(server, cwd), len(server.get("projectBasePath", ""))),
            reverse=True,
        )
        if score_server(ranked[0], cwd) > 0:
            return ranked[0]

    if len(servers) == 1:
        return servers[0]

    candidates = ", ".join(
        f"{server.get('project') or '<unknown>'}@{server.get('projectBasePath') or '<no-path>'}"
        for server in servers
    )
    raise RuntimeError(
        "Multiple Problem4AI servers found but none matched the current workspace. "
        f"Candidates: {candidates}"
    )


def call_endpoint(server: Dict, command: str, file_path: Optional[str]) -> Dict:
    base_url = server["httpBaseUrl"].rstrip("/")
    routes = {
        "info": "/api/v1/info",
        "stats": "/api/v1/stats",
        "diagnostics": "/api/v1/diagnostics",
        "errors": "/api/v1/errors",
        "refresh": "/api/v1/refresh",
    }

    if command in routes:
        return fetch_json(base_url + routes[command])

    if not file_path:
        raise RuntimeError(f"{command} requires --file")

    encoded_file_path = urllib.parse.quote(file_path)
    if command == "file-diagnostics":
        return fetch_json(f"{base_url}/api/v1/file-diagnostics?filePath={encoded_file_path}")
    if command == "fix-prompt":
        return fetch_json(f"{base_url}/api/v1/fix-prompt?filePath={encoded_file_path}")
    raise RuntimeError(f"Unknown command: {command}")


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Fetch diagnostics from the local Problem4AI HTTP API")
    parser.add_argument(
        "command",
        choices=["info", "stats", "diagnostics", "errors", "file-diagnostics", "refresh", "fix-prompt"],
        help="HTTP endpoint to call",
    )
    parser.add_argument("--cwd", default=os.getcwd(), help="Workspace path used to match the right Problem4AI server")
    parser.add_argument("--file", help="Absolute or relative file path for file-level commands")
    return parser


def main() -> int:
    parser = build_argument_parser()
    args = parser.parse_args()

    cwd = normalize_path(args.cwd)
    file_path = args.file
    if file_path and not os.path.isabs(file_path):
        file_path = str(Path(cwd, file_path).resolve())

    try:
        server = choose_server(discover_servers(), cwd)
        result = call_endpoint(server, args.command, file_path)
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
