#!/bin/bash
# ============================================================
# Omni-Stack 全家桶 — 一键启动 (Linux/Mac)
# ============================================================
# 包含：中间件 + 后端微服务 + 前端
# 用法：./start.sh              启动所有服务
#       ./start.sh mysql redis  启动指定服务
# ============================================================

cd "$(dirname "$0")"

if [ ! -f .env ]; then
    echo "[ERROR] 未找到 .env，请复制 .env.example 为 .env，"
    echo "        并将 OMNI_INTERNAL_API_TOKEN 替换为随机密钥。"
    exit 1
fi

# --- 检查 Docker ---
echo "[1/4] 检查 Docker..."
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

# --- 拉取中间件镜像 ---
echo "[2/4] 拉取中间件镜像（首次使用需下载，请耐心等待）..."
if ! docker compose pull mysql redis nacos rocketmq-namesrv rocketmq-broker xxl-job-admin; then
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

# --- 构建应用镜像 ---
echo "[3/4] 构建应用镜像（首次构建约 5-10 分钟）..."
if ! docker compose build omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend; then
    echo ""
    echo "[ERROR] 构建失败！请检查以上错误信息。"
    echo "        常见原因：JDK/Node 镜像下载失败、Maven 依赖下载超时。"
    echo ""
    exit 1
fi
echo "      构建完成"
echo ""

# --- 启动全部容器 ---
echo "[4/4] 启动全家桶..."
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
echo "=================================================="
echo "  Omni-Stack 全家桶启动完成！"
echo ""
echo "  -- 中间件 --"
echo "  MySQL:        localhost:13306      凭据见 .env"
echo "  Redis:        localhost:6379       凭据见 .env"
echo "  Nacos:        http://localhost:8080"
echo "  RocketMQ:     localhost:19876"
echo "  XXL-JOB:      http://localhost:18080  凭据见 .env"
echo ""
echo "  -- 应用 --"
echo "  前端:         http://localhost:3000"
echo "  Auth:         http://localhost:8100"
echo "  Base:         http://localhost:8101"
echo "  Gateway:      http://localhost:8102"
echo "  Workflow:     http://localhost:8103"
echo "  CRM:          http://localhost:8104"
echo "  SRM:          http://localhost:8105"
echo "  Procurement:  http://localhost:8106"
echo "  Asset:        http://localhost:8107"
echo ""
echo "  业务演示账号仅用于本地开发，详见 README。"
echo "=================================================="
