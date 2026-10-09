#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "[ERROR] 未找到 .env，请先复制 .env.example 并填写本地 Secret。"
  exit 1
fi
command -v docker >/dev/null || { echo "[ERROR] 未检测到 Docker。"; exit 1; }
docker info >/dev/null || { echo "[ERROR] Docker 引擎未运行。"; exit 1; }
preset="${1:-full}"
case "$preset" in
  core|workflow|crm|supply-chain|full) ;;
  *) echo "[ERROR] 不支持的预设：$preset"; exit 1 ;;
esac
echo "[INFO] 启动 ${preset} 预设。"
docker compose -p omni-stack --profile "$preset" up -d --build
