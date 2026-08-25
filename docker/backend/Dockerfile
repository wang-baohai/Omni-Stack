# syntax=docker/dockerfile:1.7
# ============================================================
# Omni-Stack 后端微服务 — 共享多阶段 Dockerfile
# ============================================================
# 通过 docker-compose build arg SERVICE_NAME 指定构建哪个微服务：
#   omni-auth / omni-base / omni-gateway / omni-workflow / omni-crm / omni-srm / omni-procurement / omni-asset
#
# 用法（由 docker-compose 自动调用，无需手动执行）：
#   docker build --build-arg SERVICE_NAME=omni-auth \
#     -f docker/backend/Dockerfile -t omni-auth .
# ============================================================

# ---------- Stage 1: 构建 ----------
FROM maven:3.9-eclipse-temurin-25-alpine AS build

# 配置阿里云 Maven 镜像（加速依赖下载）
COPY omni-backend/docker-settings.xml /root/.m2/settings.xml

WORKDIR /build

# 先拷入 Maven Wrapper + 所有 POM（利用 Docker 层缓存，POM 不变时跳过依赖下载）
COPY omni-backend/mvnw omni-backend/pom.xml ./
COPY omni-backend/omni-common-core/pom.xml omni-common-core/
COPY omni-backend/omni-common/pom.xml omni-common/
COPY omni-backend/omni-common-mybatis/pom.xml omni-common-mybatis/
COPY omni-backend/omni-common-redis/pom.xml omni-common-redis/
COPY omni-backend/omni-common-redis-reactive/pom.xml omni-common-redis-reactive/
COPY omni-backend/omni-common-operlog/pom.xml omni-common-operlog/
COPY omni-backend/omni-common-job/pom.xml omni-common-job/
COPY omni-backend/omni-common-mqlog/pom.xml omni-common-mqlog/
COPY omni-backend/omni-common-workflow/pom.xml omni-common-workflow/
COPY omni-backend/omni-common-service/pom.xml omni-common-service/
COPY omni-backend/omni-db-migrator/pom.xml omni-db-migrator/
COPY omni-backend/omni-auth/pom.xml omni-auth/
COPY omni-backend/omni-base/pom.xml omni-base/
COPY omni-backend/omni-gateway/pom.xml omni-gateway/
COPY omni-backend/omni-workflow/pom.xml omni-workflow/
COPY omni-backend/omni-crm/pom.xml omni-crm/
COPY omni-backend/omni-srm/pom.xml omni-srm/
COPY omni-backend/omni-procurement/pom.xml omni-procurement/
COPY omni-backend/omni-asset/pom.xml omni-asset/

RUN --mount=type=cache,id=omni-maven-repository,target=/root/.m2,sharing=locked \
    mvn dependency:go-offline -B -q -Dmaven.wagon.http.retryHandler.count=5 || true

# 拷入全部后端源码和运行时目录事实源。Auth 的 Maven resources 会将后两者打入应用包。
COPY omni-backend/ .
COPY scaffold/ /scaffold/
COPY database/seed/ /database/seed/

# 镜像构建也是发布门禁：当前预设服务 reactor 任一模块测试失败时不得产出镜像。
# 数据库迁移器由独立镜像携带完整 database/scripts 上下文构建和测试，此处排除它。
# 本步骤不依赖 SERVICE_NAME，因此多个服务镜像共享同一个完整构建层，避免重复编译。
RUN --mount=type=cache,id=omni-maven-repository,target=/root/.m2,sharing=locked \
    mvn package -pl '!omni-db-migrator' -B -q -Dmaven.wagon.http.retryHandler.count=5

# ---------- Stage 2: 运行 ----------
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl bash

ARG SERVICE_NAME
COPY --from=build /build/${SERVICE_NAME}/target/*.jar /app/app.jar

# 统一容器内部端口为 8080（覆盖各服务 application.yml 中的 server.port）
ENV SERVER_PORT=8080
# Actuator 管理端口（各服务通过 docker-compose 环境变量覆盖）
ENV MANAGEMENT_PORT=8080
# 增大 HTTP 请求头限制，JWT + X-User-Scopes 等头部可能超过 Tomcat 默认 8KB
ENV SERVER_MAX_HTTP_HEADER_SIZE=32768
# 额外的 JVM 参数（可通过 docker-compose environment 覆盖）
ENV JAVA_OPTS=""
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --retries=5 --start-period=90s \
  CMD curl -sf http://localhost:${MANAGEMENT_PORT}/actuator/health || exit 1

# 使用 JSON 形式固定进程入口；JDK 25 下显式开放 Netty 等依赖所需的受限原生访问。
ENTRYPOINT ["/bin/bash", "-c", "exec java --enable-native-access=ALL-UNNAMED $JAVA_OPTS -jar /app/app.jar"]
