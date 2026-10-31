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
ARG VITE_OMNI_MODULES=""
RUN VITE_OMNI_MODULES="$VITE_OMNI_MODULES" npm run build

# ---------- 公开 Wiki：顺序复用构建阶段，不增加运行服务 ----------
FROM build AS wiki-build
WORKDIR /workspace/docs
COPY docs/package*.json ./
RUN --mount=type=cache,id=omni-npm-cache,target=/root/.npm,sharing=locked \
    npm ci --prefer-offline --no-audit
WORKDIR /workspace
COPY docs ./docs
COPY ai ./ai
COPY tools/omni-cli/scripts/translation-review.mjs ./tools/omni-cli/scripts/translation-review.mjs
COPY README*.md AGENTS.md CHANGELOG.md LICENSE ./
ENV DOCS_BASE=/wiki/
RUN npm --prefix docs run build

# ---------- Stage 2: 运行 ----------
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=wiki-build /workspace/docs/.vitepress/dist /usr/share/nginx/html/wiki
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
RUN nginx -t
EXPOSE 3000
