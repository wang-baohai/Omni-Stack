#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "[ERROR] 未找到 .env，无法解析当前 Compose 项目。"
  exit 1
fi

echo "[INFO] 停止开发栈并保留数据库等命名卷。"
docker compose --profile '*' down
