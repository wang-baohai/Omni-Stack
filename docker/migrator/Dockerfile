# syntax=docker/dockerfile:1.7
# ============================================================
# Omni-Stack 一次性数据库迁移器
# ============================================================

FROM maven:3.9-eclipse-temurin-25-alpine AS build

COPY omni-backend/docker-settings.xml /root/.m2/settings.xml
WORKDIR /workspace
COPY . .
WORKDIR /workspace/omni-backend

# 迁移镜像同样执行模块测试；迁移器测试失败时不得产出镜像。
RUN --mount=type=cache,id=omni-maven-repository,target=/root/.m2,sharing=locked \
    ./mvnw -pl omni-db-migrator -am package -B -q -Dmaven.wagon.http.retryHandler.count=5

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/omni-backend/omni-db-migrator/target/*.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
