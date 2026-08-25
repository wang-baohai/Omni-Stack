#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "[ERROR] 未找到 .env，请先复制 .env.example 并填写本地 Secret。"
  exit 1
fi
command -v docker >/dev/null || { echo "[ERROR] 未检测到 Docker。"; exit 1; }
docker info >/dev/null || { echo "[ERROR] Docker 引擎未运行。"; exit 1; }
command -v npm >/dev/null || { echo "[ERROR] 未检测到 Node.js/npm，要求 Node.js 22.12.0 或更高版本。"; exit 1; }

if [[ ! -d tools/omni-cli/node_modules ]]; then
  echo "[INFO] 首次运行，正在安装 Omni CLI 依赖..."
  npm --prefix tools/omni-cli ci
fi

preset="${1:-full}"
echo "[INFO] 启动 ${preset} 预设。"
npm --prefix tools/omni-cli run dev -- dev up --preset "$preset" --build
