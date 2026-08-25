# syntax=docker/dockerfile:1.7
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
RUN --mount=type=cache,id=omni-npm-cache,target=/root/.npm,sharing=locked \
    npm ci --prefer-offline --no-audit --fetch-retries=5 \
      --fetch-retry-mintimeout=10000 --fetch-retry-maxtimeout=60000

COPY omni-frontend/ .
RUN npm run build

# ---------- Stage 2: 运行 ----------
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
