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
COPY docker-settings.xml /root/.m2/settings.xml

WORKDIR /build

# 先拷入 Maven Wrapper + 所有 POM（利用 Docker 层缓存，POM 不变时跳过依赖下载）
COPY mvnw pom.xml ./
COPY omni-common-core/pom.xml omni-common-core/
COPY omni-common/pom.xml omni-common/
COPY omni-common-mybatis/pom.xml omni-common-mybatis/
COPY omni-common-redis/pom.xml omni-common-redis/
COPY omni-common-redis-reactive/pom.xml omni-common-redis-reactive/
COPY omni-common-operlog/pom.xml omni-common-operlog/
COPY omni-common-job/pom.xml omni-common-job/
COPY omni-common-mqlog/pom.xml omni-common-mqlog/
COPY omni-common-workflow/pom.xml omni-common-workflow/
COPY omni-db-migrator/pom.xml omni-db-migrator/
COPY omni-auth/pom.xml omni-auth/
COPY omni-base/pom.xml omni-base/
COPY omni-gateway/pom.xml omni-gateway/
COPY omni-workflow/pom.xml omni-workflow/
COPY omni-crm/pom.xml omni-crm/
COPY omni-srm/pom.xml omni-srm/
COPY omni-procurement/pom.xml omni-procurement/
COPY omni-asset/pom.xml omni-asset/

RUN mvn dependency:go-offline -B -q || true

# 拷入全部源码并构建指定服务
COPY . .

ARG SERVICE_NAME
# 镜像构建也是发布门禁：目标服务及其依赖测试失败时不得产出镜像。
RUN mvn package -pl ${SERVICE_NAME} -am -B -q

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
