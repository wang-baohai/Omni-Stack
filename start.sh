#!/bin/bash
# ============================================================
# Omni-Stack 中间件 — 一键启动 (Linux/Mac)
# ============================================================
# 用法：./start.sh              启动所有服务
#       ./start.sh mysql redis  启动指定服务
# ============================================================

set -e

cd "$(dirname "$0")"

if ! command -v docker &> /dev/null; then
    echo "[ERROR] 未检测到 Docker，请先安装 Docker"
    exit 1
fi

echo "========== 启动中间件 =========="

if [ $# -eq 0 ]; then
    docker compose up -d
else
    docker compose up -d "$@"
fi

echo ""
echo "========== 启动完成 =========="
