# ============================================================
# Omni-Stack 前端 — 多阶段 Dockerfile
# ============================================================
# 构建上下文为项目根目录（docker-compose 中配置）。
# Stage 1: Node 22 构建 Vue 3 生产包
# Stage 2: Nginx 提供静态文件 + 反代 Gateway
# ============================================================

# ---------- Stage 1: 构建 ----------
FROM node:22-alpine AS build
WORKDIR /app

COPY omni-frontend/package*.json ./
RUN npm ci

COPY omni-frontend/ .
RUN npm run build

# ---------- Stage 2: 运行 ----------
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
