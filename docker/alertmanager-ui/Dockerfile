# syntax=docker/dockerfile:1.7
# 官方 Elm 界面按固定源码摘要构建四语包；后端继续使用官方镜像。
FROM node:22-bookworm-slim AS build
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN --mount=type=cache,id=omni-alertmanager-elm-npm,target=/root/.npm,sharing=locked \
    npm install --global --no-audit --no-fund elm@0.19.1-6
WORKDIR /build
ADD --checksum=sha256:b3577c531d4c05e0f45f90b02224c81684be9f4083720a01cc05bfa07107fc93 \
    https://codeload.github.com/prometheus/alertmanager/tar.gz/refs/tags/v0.30.1 /upstream.tar.gz
COPY observability/alertmanager-ui/build.mjs observability/alertmanager-ui/messages.tsv ./
RUN --mount=type=cache,id=omni-alertmanager-elm-packages,target=/root/.elm,sharing=locked \
    node build.mjs --archive /upstream.tar.gz --output /dist

FROM nginx:1.28-alpine
COPY --from=build /dist /usr/share/nginx/html
COPY docker/alertmanager-ui/nginx.conf /etc/nginx/conf.d/default.conf
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/zh-CN/index.html || exit 1
EXPOSE 8080
