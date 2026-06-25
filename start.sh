#!/bin/bash
# ============================================================
# Omni-Stack 中间件 — 一键启动 (Linux/Mac)
# ============================================================
# 用法：./start.sh              启动所有服务
#       ./start.sh mysql redis  启动指定服务
# ============================================================

cd "$(dirname "$0")"

# --- 检查 Docker ---
echo "[1/3] 检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "[ERROR] 未检测到 Docker，请先安装 Docker"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo "[ERROR] Docker 未运行，请先启动 Docker 服务"
    echo "  Linux: sudo systemctl start docker"
    echo "  Mac:   打开 Docker Desktop 应用"
    exit 1
fi
echo "      Docker 已就绪"
echo ""

# --- 拉取镜像 ---
echo "[2/3] 拉取镜像（首次使用需下载，请耐心等待）..."
if ! docker compose pull; then
    echo ""
    echo "[ERROR] 镜像拉取失败！常见原因："
    echo ""
    echo "  1. 网络连接问题（国内用户建议配置 Docker 镜像加速）"
    echo "     编辑 /etc/docker/daemon.json，添加："
    echo '     { "registry-mirrors": ["https://docker.1ms.run"] }'
    echo "     然后重启 Docker: sudo systemctl restart docker"
    echo ""
    echo "  2. Docker Hub 无法访问"
    echo "     尝试手动拉取：docker pull xuxueli/xxl-job-admin:3.3.1"
    echo ""
    exit 1
fi
echo "      镜像就绪"
echo ""

# --- 启动服务 ---
echo "[3/3] 启动中间件..."
if [ $# -eq 0 ]; then
    docker compose up -d
else
    docker compose up -d "$@"
fi

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] 启动失败！请检查以上错误信息。"
    echo "        运行 docker compose logs 查看详细日志。"
    exit 1
fi

echo ""
echo "=========================================="
echo "  中间件启动完成！"
echo ""
echo "  MySQL:        localhost:3306  root/root"
echo "  Redis:        localhost:6379"
echo "  Nacos:        http://localhost:8080"
echo "  RocketMQ:     localhost:9876"
echo "  XXL-JOB:      http://localhost:18080  admin/123456"
echo "=========================================="
