# Docker 全家桶部署深度指南

> 本文档是 Omni-Stack Docker 部署的完整技术参考。涵盖架构设计、构建原理、配置详解、运维操作与故障排查。  
> 快速上手请参阅 [README.md](../README.md) 中的 Docker 一键部署章节。

---

## 目录

- [1. 架构概览](#1-架构概览)
- [2. 容器网络拓扑](#2-容器网络拓扑)
- [3. 启动链与健康检查](#3-启动链与健康检查)
- [4. 多阶段构建原理](#4-多阶段构建原理)
- [5. docker-compose.yml 逐服务配置解读](#5-docker-composeyml-逐服务配置解读)
- [6. 环境变量覆盖机制](#6-环境变量覆盖机制)
- [7. Nginx 反代配置要点](#7-nginx-反代配置要点)
- [8. 数据初始化与持久化](#8-数据初始化与持久化)
- [9. Docker 镜像加速配置](#9-docker-镜像加速配置)
- [10. Windows Hyper-V/WSL2 端口保留问题](#10-windows-hyper-vws12-端口保留问题)
- [11. Nacos v3.1.1 健康检查端点变更](#11-nacos-v311-健康检查端点变更)
- [12. RocketMQ Broker Docker 网络配置](#12-rocketmq-broker-docker-网络配置)
- [13. 扩缩容指南](#13-扩缩容指南)
- [14. 运维操作手册](#14-运维操作手册)
- [15. 故障排查指南](#15-故障排查指南)
- [16. 配置参考表](#16-配置参考表)

---

## 1. 架构概览

Omni-Stack Docker 全家桶包含 **15 个容器**，分为三层：

```
┌─────────────────────────────────────────────────────────────┐
│                      前端层 (1 容器)                         │
│  omni-frontend (Vue 3 + Nginx:3000)                        │
└──────────────────────────┬──────────────────────────────────┘
                           │ proxy_pass
┌──────────────────────────┴──────────────────────────────────┐
│                    后端微服务层 (8 容器)                      │
│  omni-gateway (:8102) ──→ omni-auth (:8100)                │
│        │                 omni-base (:8101)                  │
│        │                 omni-workflow (:8103)              │
│        │                 omni-crm (:8104)                   │
│        │                 omni-srm (:8105，仅回环调试)        │
│        │                 omni-procurement (:8106，仅回环)    │
│        │                 omni-asset (:8107，仅回环调试)      │
└────────┼────────────────────┬───────────────────────────────┘
         │                    │
┌────────┴────────────────────┴───────────────────────────────┐
│                    中间件层 (6 容器)                          │
│  MySQL (宿主机:13306/容器:3306) · Redis (:6379)             │
│  Nacos (:8080/:8848/:9848)                                 │
│  RocketMQ NameServer (:19876) · Broker (:10909-10912)      │
│  XXL-JOB Admin (:18080)                                    │
└─────────────────────────────────────────────────────────────┘
```

**设计原则**：
- 所有后端微服务容器内部端口统一为 **8080**，通过宿主机端口映射区分
- 容器间通信使用 **Docker 内部网络**（`omni-network`），通过容器名解析
- 前端 Nginx 同时提供静态文件和 API 反代，用户只需访问一个端口（3000）

---

## 2. 容器网络拓扑

所有容器共享一个 Docker Bridge 网络 `omni-network`：

```yaml
networks:
  omni-network:
    driver: bridge
```

**网络通信规则**：

| 源容器 | 目标容器 | 使用地址 | 说明 |
|--------|----------|----------|------|
| omni-frontend | omni-gateway | `omni-gateway:8080` | Nginx proxy_pass（容器内部端口） |
| omni-gateway | omni-auth | `omni-auth:8080` | Spring Cloud Gateway 路由 |
| omni-auth | mysql | `mysql:3306` | JDBC 连接 |
| omni-auth | redis | `redis:6379` | 缓存/会话 |
| omni-auth | nacos | `nacos:8848` | 服务注册/配置 |
| omni-base | rocketmq-namesrv | `rocketmq-namesrv:9876` | MQ 消息发送 |
| omni-base | xxl-job-admin | `xxl-job-admin:8080` | 任务执行器注册 |
| omni-crm | omni-auth | `omni-auth:8080` | 用户、组织、数据范围与 XSS 内部查询 |
| omni-crm | rocketmq-namesrv | `rocketmq-namesrv:9876` | CRM Outbox 消息投递 |
| omni-crm | xxl-job-admin | `xxl-job-admin:8080` | CRM 中继执行器注册 |
| omni-srm | mysql | `mysql:3306` | SRM 业务库与本地 Outbox |
| omni-srm | omni-auth | `omni-auth:8080` | 用户/组织内部查询与 Portal Saga |
| omni-procurement | omni-srm | `omni-srm:8080` | 供应商邀请、报价和定点校验 |
| omni-procurement | omni-workflow | `omni-workflow:8080` | 请购审批内部契约 |
| omni-asset | omni-procurement | `omni-procurement:8080` | 收货资产候选历史补偿 |
| omni-asset | omni-workflow | `omni-workflow:8080` | 调拨和处置审批内部契约 |
| 宿主机浏览器 | omni-frontend | `localhost:3000` | 用户访问入口 |
| 宿主机浏览器 | Nacos Console | `localhost:8080` | 运维管理 |

> **关键区分**：容器间通信使用容器内部端口（如 8080），宿主机访问使用映射端口（如 8100-8107）；MySQL 容器内始终使用 3306，宿主机映射为 13306。

> **生产网络边界**：仓库根目录的 `docker-compose.yml` 是本地开发/联调编排。Frontend 3000 与 Gateway 8102
> 可由宿主机访问，其余服务、中间件和管理控制台都只绑定 `127.0.0.1` 作为诊断入口。生产部署必须只发布
> Frontend/Gateway 的 HTTPS 入口，并移除全部诊断端口映射；下游 `X-Gateway-Forwarded` 只是转发标记，
> 不能替代私有网络与服务间令牌。

---

## 3. 启动链与健康检查

### 3.1 分层启动顺序

容器按依赖关系分为 8 层，通过 `depends_on` + `condition: service_healthy` 确保有序启动：

```
Layer 0:  mysql · redis · rocketmq-namesrv          （无依赖，首先启动）
            │         │          │
Layer 1:  nacos     xxl-job   rocketmq-broker        （依赖 Layer 0）
            │         │          │
Layer 2:  omni-auth                                  （依赖 nacos + redis + mysql）
            │
Layer 3:  omni-base · omni-workflow · omni-crm · omni-srm
                                                │
Layer 4:  omni-procurement                           （依赖 workflow + srm）
                                                │
Layer 5:  omni-asset                                 （依赖 procurement + workflow）
                                                │
Layer 6:  omni-gateway                               （依赖全部下游服务）
                                                │
Layer 7:  omni-frontend                              （依赖 omni-gateway）
```

### 3.2 健康检查配置一览

| 服务 | 检查方式 | interval | timeout | retries | start_period |
|------|----------|----------|---------|---------|--------------|
| MySQL | `mysqladmin ping` | 10s | 5s | 5 | 30s |
| Redis | `redis-cli ping` | 10s | 5s | 5 | 10s |
| Nacos | `curl http://localhost:8848/nacos/` | 15s | 5s | 5 | 60s |
| RocketMQ NameServer | TCP 端口探测 `/dev/tcp/127.0.0.1/9876` | 10s | 5s | 5 | 30s |
| RocketMQ Broker | TCP 端口探测 `/dev/tcp/127.0.0.1/10911` | 15s | 5s | 5 | 60s |
| XXL-JOB Admin | TCP 端口探测 `/dev/tcp/localhost/8080` | 15s | 5s | 5 | 60s |
| omni-auth | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-base | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-gateway | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-workflow | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-crm | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-srm | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-procurement | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-asset | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |

> **为什么 start_period 设为 90s？**  
> Spring Boot 微服务首次启动需加载大量 Bean + 数据库初始化 + Nacos 注册，冷启动可能需要 60-80 秒。设置 90s 避免误判为不健康。

---

## 4. 多阶段构建原理

### 4.1 后端微服务构建

所有后端微服务共享同一个 Dockerfile（`docker/backend/Dockerfile`），通过 `SERVICE_NAME` 构建参数区分：

```dockerfile
# Stage 1: 构建阶段
FROM maven:3.9-eclipse-temurin-25-alpine AS build
COPY docker-settings.xml /root/.m2/settings.xml   # 阿里云 Maven 镜像
WORKDIR /build

# 先拷入 POM 文件（利用 Docker 层缓存，依赖不变时跳过下载）
COPY mvnw pom.xml ./
COPY omni-common-core/pom.xml omni-common-core/
COPY omni-common/pom.xml omni-common/
# ... 其他模块 POM ...
RUN mvn dependency:go-offline -B -q || true

# 拷入源码并构建指定服务
COPY . .
ARG SERVICE_NAME
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B -q

# Stage 2: 运行阶段
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl bash
ARG SERVICE_NAME
COPY --from=build /build/${SERVICE_NAME}/target/*.jar /app/app.jar
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK ...
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**层缓存优化策略**：

1. **POM 层缓存**：先拷入所有 `pom.xml` 并执行 `dependency:go-offline`，只要 POM 不变，后续构建直接复用缓存层，跳过依赖下载
2. **源码层隔离**：POM 缓存命中后，`COPY . .` 只在源码变更时触发重建
3. **单服务构建**：`mvn package -pl ${SERVICE_NAME} -am` 仅构建目标服务及其依赖模块，避免全量编译

**镜像选择考量**：

| 选择 | 理由 |
|------|------|
| `maven:3.9-eclipse-temurin-25-alpine` | Alpine 基础镜像体积小（~200MB vs ~800MB for Debian），内置 Maven 3.9 + JDK 25 |
| `eclipse-temurin:25-jre-alpine` | 运行阶段仅需 JRE（~80MB），比完整 JDK 镜像小 300MB+ |

### 4.2 前端构建

```dockerfile
# Stage 1: Node 构建
FROM node:22-alpine AS build
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                                  # 精确安装（使用 lock 文件）
COPY omni-frontend/ .
RUN npm run build                           # Vite 生产构建

# Stage 2: Nginx 运行
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
```

**层缓存优化**：先拷入 `package*.json` 执行 `npm ci`，源码变更时不会重新安装依赖。

---

## 5. docker-compose.yml 逐服务配置解读

### 5.1 MySQL 8.4

```yaml
mysql:
  image: mysql:8.4
  ports:
    - "127.0.0.1:13306:3306"
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?必须在 .env 中配置}
    MYSQL_DATABASE: omni_auth          # 自动创建的首个数据库
    MYSQL_CHARACTER_SET_SERVER: utf8mb4
    MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    TZ: Asia/Shanghai                  # 时区设置
  volumes:
    - omni-mysql-data:/var/lib/mysql
    - ./scripts/sql/init-all.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    - ./scripts/sql/init-nacos.sql:/docker-entrypoint-initdb.d/02-init-nacos.sql:ro
    - ./scripts/sql/init-xxl-job.sql:/docker-entrypoint-initdb.d/03-init-xxl-job.sql:ro
```

**要点**：
- 三个 SQL 脚本按编号顺序自动执行：`01-init.sql`（业务库 + 初始数据）→ `02-init-nacos.sql`（Nacos 配置库）→ `03-init-xxl-job.sql`（XXL-JOB 库）
- 挂载为 `:ro`（只读），防止容器意外修改源文件
- MySQL 使用显式命名卷 `omni-stack-mysql-data`；普通 `docker compose down` 和重建容器不会删除数据，
  `docker compose down -v` 会删除卷且不可恢复。
- Docker entrypoint 初始化脚本只在空数据卷执行。已有环境升级前必须备份，再按 README 所列顺序执行
  CRM、Workflow 幂等、SRM、Procurement、Asset 迁移和 `sp_init_tenant.sql`。

### 5.2 Redis 7.4

```yaml
redis:
  image: redis:7.4
  command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?必须在 .env 中配置}"]
  ports:
    - "127.0.0.1:6379:6379"
```

### 5.3 Nacos v3.1.1

```yaml
nacos:
  image: nacos/nacos-server:v3.1.1
  environment:
    MODE: standalone                      # 单机模式
    SPRING_DATASOURCE_PLATFORM: mysql     # 外部 MySQL 存储（非嵌入式 Derby）
    MYSQL_SERVICE_HOST: mysql             # 指向 MySQL 容器名
    NACOS_AUTH_TOKEN: ...                 # JWT 签名密钥（Base64 编码）
  ports:
    - "8080:8080"                         # Console 控制台
    - "8848:8848"                         # API 端口（服务注册/配置拉取）
    - "9848:9848"                         # gRPC 端口（长连接通信）
```

**三端口说明**：

| 端口 | 用途 | 使用者 |
|------|------|--------|
| 8080 | Web 控制台 | 运维人员浏览器访问 |
| 8848 | HTTP API | 后端微服务注册/配置 |
| 9848 | gRPC | Nacos 2.x+ 客户端长连接 |

### 5.4 RocketMQ 5.3.2（NameServer + Broker）

```yaml
rocketmq-namesrv:
  ports:
    - "19876:9876"     # 宿主机 19876 → 容器 9876（避开 Windows Hyper-V 端口冲突）

rocketmq-broker:
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876          # 容器间通信
    JAVA_OPT_EXT: "-Drocketmq.broker.diskSpaceWarningLevelRatio=0.98"
  volumes:
    - ./docker/rocketmq/broker-docker.conf:...   # Docker 专用 Broker 配置
  command: sh mqbroker -n rocketmq-namesrv:9876 -c ... --enable-proxy
```

> **端口映射 19876:9876 的原因**：Windows Hyper-V/WSL2 会保留端口范围 9859-9958，直接映射 9876 会导致冲突。详见[第 10 节](#10-windows-hyper-vws12-端口保留问题)。

### 5.5 XXL-JOB Admin v3.3.1

```yaml
xxl-job-admin:
  environment:
    PARAMS: >
      --spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?...
      --spring.datasource.username=root
      --spring.datasource.password=root
      --xxl.job.login.username=admin
      --xxl.job.login.password=123456
      --xxl.job.accessToken=           # 空 Token（开发环境不鉴权）
```

### 5.6 后端微服务（8 个）

八个后端微服务使用相同的 Dockerfile，通过 `build.args.SERVICE_NAME` 区分。每个服务的 `environment` 覆盖关键配置：

| 环境变量 | 说明 | 示例值 |
|----------|------|--------|
| `SERVER_PORT` | 容器内端口（统一 8080） | `8080` |
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Nacos 地址 | `nacos:8848` |
| `SPRING_DATASOURCE_URL` | 数据库连接 | `jdbc:mysql://mysql:3306/omni_auth?...` |
| `SPRING_DATA_REDIS_HOST` | Redis 地址 | `redis` |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | 注册 IP（空=自动检测容器 IP） | `""` |
| `AUTH_ISSUER` | JWT Issuer（仅 auth） | `http://omni-auth:8080` |
| `AUTH_JWKS_URI` | JWKS 端点（仅 gateway） | `http://omni-auth:8080/oauth2/jwks` |
| `OMNI_INTERNAL_API_TOKEN` | 服务间共享密钥（所有 Servlet 服务一致） | 从 `.env` 注入 |
| `MYSQL_URL` | 当前业务服务数据库连接 | `jdbc:mysql://mysql:3306/omni_asset?...` |

首次启动必须执行 `cp .env.example .env`，并把 `OMNI_INTERNAL_API_TOKEN` 换成至少 32 字节的随机值。Compose 使用必填变量语法，缺少密钥时直接拒绝启动，不提供仓库内默认值。

### 5.7 前端

```yaml
omni-frontend:
  build:
    context: .                          # 项目根目录（需要访问 docker/ 下的配置）
    dockerfile: docker/frontend/Dockerfile
  ports:
    - "3000:3000"                       # 用户唯一访问入口
  depends_on:
    omni-gateway:
      condition: service_healthy        # 等网关就绪后才启动
```

---

## 6. 环境变量覆盖机制

Spring Boot 支持通过环境变量覆盖 `application.yml` 中的配置，Docker 部署中大量使用此机制。

### 6.1 转换规则

| application.yml 配置 | 环境变量名 | 转换规则 |
|----------------------|-----------|----------|
| `server.port` | `SERVER_PORT` | 全大写 + 点→下划线 |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | 全大写 + 点→下划线 |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | 全大写 + 点→下划线 |
| `spring.cloud.nacos.discovery.server-addr` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | 全大写 + 连字符→下划线 |

### 6.2 优先级（从高到低）

```
1. 命令行参数（--server.port=9090）
2. 环境变量（SERVER_PORT=9090）
3. application-{profile}.yml
4. application.yml
5. Nacos 配置中心（如启用）
```

### 6.3 Docker 部署中的典型覆盖

| 配置项 | application.yml 值（本地开发） | Docker 环境变量值 |
|--------|-------------------------------|------------------|
| 数据库地址 | `localhost:13306`（连接本 Compose MySQL） | `mysql:3306` |
| Redis 地址 | `localhost` | `redis` |
| Nacos 地址 | `localhost:8848` | `nacos:8848` |
| RocketMQ | `localhost:9876` | `rocketmq-namesrv:9876` |
| JWT Issuer | `http://localhost:8100` | `http://omni-auth:8080` |
| OAuth2 回调 | `http://localhost:8100/api/auth/...` | `http://localhost:8100/api/auth/...` |

> **注意**：OAuth2 回调 URI 使用 `localhost:8100`（宿主机端口），因为这是用户浏览器实际访问的地址。

---

## 7. Nginx 反代配置要点

### 7.1 配置文件解读

```nginx
server {
    listen 3000;
    root /usr/share/nginx/html;

    # API 请求反代至 Gateway
    location /api/ {
        proxy_pass http://omni-gateway:8080;    # ← 容器内部端口！
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 端点反代
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
        # ... 同上
    }

    # OIDC Discovery 端点反代
    location /.well-known/ {
        proxy_pass http://omni-gateway:8080;
        # ... 同上
    }

    # Vue Router History 模式
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### 7.2 常见错误：proxy_pass 端口

| 配置 | 正确性 | 说明 |
|------|--------|------|
| `proxy_pass http://omni-gateway:8080` | ✅ | 容器内部端口，Docker 网络内可达 |
| `proxy_pass http://omni-gateway:8102` | ❌ | 8102 是宿主机映射端口，容器间不通 |
| `proxy_pass http://localhost:8102` | ❌ | 容器内 localhost 是自身，无法到达网关 |
| `proxy_pass http://host.docker.internal:8102` | ⚠️ | 可行但绕过 Docker 网络，性能差 |

---

## 8. 数据初始化与持久化

### 8.1 数据库初始化链

MySQL 容器首次启动时自动执行 `/docker-entrypoint-initdb.d/` 下的 SQL 脚本：

```
01-init.sql       → 创建 omni_auth / omni_base / omni_workflow 数据库 + 初始数据
02-init-nacos.sql → 创建 nacos_config 数据库 + Nacos 配置数据
03-init-xxl-job.sql → 创建 xxl_job 数据库 + XXL-JOB 调度数据
```

### 8.2 数据持久化策略

当前 Compose 使用显式 MySQL 命名卷：

```yaml
# 在 docker-compose.yml 中添加 Volume
mysql:
  volumes:
    - omni-mysql-data:/var/lib/mysql

volumes:
  omni-mysql-data:
    name: omni-stack-mysql-data
```

`docker compose down` 保留该卷；`docker compose down -v` 删除卷和全部业务数据。Redis 当前只保存可重建的
验证码、会话/缓存等运行态数据，没有配置持久化卷。生产环境应使用外部数据库或受备份管理的持久卷，并对
恢复流程做定期演练；仓库 Compose 的单机 MySQL、Nacos、RocketMQ 和 XXL-JOB 不能直接等同于高可用生产拓扑。

---

## 9. Docker 镜像加速配置

### 9.1 Docker Registry Mirror

国内用户拉取 Docker Hub 镜像可能超时，建议配置镜像加速：

**Linux**（`/etc/docker/daemon.json`）：
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```
```bash
sudo systemctl restart docker
```

**Windows / Mac**（Docker Desktop → Settings → Docker Engine）：
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```

### 9.2 Maven 依赖加速

后端 Dockerfile 中已内置阿里云 Maven 镜像（`docker-settings.xml`）：

```xml
<mirror>
  <id>aliyun</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

### 9.3 npm 依赖加速

如需加速前端 npm 安装，可在 Dockerfile 中添加淘宝镜像：

```dockerfile
RUN npm config set registry https://registry.npmmirror.com
RUN npm ci
```

---

## 10. Windows Hyper-V/WSL2 端口保留问题

### 10.1 问题描述

Windows 10/11 的 Hyper-V 或 WSL2 会动态保留 TCP 端口范围（如 9859-9958），如果被保留的端口包含 Docker 需要映射的端口（如 9876），容器启动会报错：

```
Error starting userland proxy: listen tcp4 0.0.0.0:9876: bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

### 10.2 解决方案

**方案 A：端口映射偏移（已采用）**

将 RocketMQ NameServer 的宿主机端口从 9876 改为 19876：

```yaml
ports:
  - "19876:9876"    # 宿主机 19876 → 容器 9876
```

容器间通信不受影响（仍然使用 `rocketmq-namesrv:9876`），仅宿主机访问时使用 19876。

**方案 B：端口保留保护（start.bat 已实现）**

在启动 Docker 前，通过管理员权限预留所需端口：

```batch
:: 停止 winnat → 保留端口 → 重启 winnat
net stop winnat
netsh int ipv4 add excludedportrange protocol=tcp startport=9876 numberofports=1 persistent=yes
net start winnat
```

**方案 C：重启 WSL（临时方案）**

```powershell
wsl --shutdown
# 然后重新启动 Docker Desktop
```

### 10.3 验证端口是否被保留

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

---

## 11. Nacos v3.1.1 健康检查端点变更

### 11.1 变更说明

Nacos 从 v3.x 开始，移除了 `/nacos/actuator/health` 端点。健康检查需改用：

| Nacos 版本 | 健康检查端点 | 方法 |
|-----------|-------------|------|
| v2.x | `/nacos/actuator/health` | GET |
| v3.0+ | `/nacos/` | GET |

### 11.2 docker-compose.yml 中的配置

```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/"]
  start_period: 60s     # Nacos 启动较慢，需 40-60 秒
```

> **注意**：`curl -sf` 中的 `-s` 是静默模式，`-f` 是 HTTP 错误时返回非零退出码。两者配合使 healthcheck 不会产生多余输出。

---

## 12. RocketMQ Broker Docker 网络配置

### 12.1 问题背景

RocketMQ Broker 默认使用本机 IP 注册到 NameServer。在 Docker 环境中，Broker 获取的 IP 可能是 `127.0.0.1` 或 Docker 网桥 IP，导致其他容器无法连接 Broker。

### 12.2 解决方案

通过自定义配置文件 `docker/rocketmq/broker-docker.conf` 显式指定 `brokerIP1`：

```properties
brokerIP1 = rocketmq-broker    # 设为容器名，Docker DNS 可解析
```

### 12.3 后端连接配置

后端微服务通过环境变量连接 RocketMQ：

```yaml
SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER: rocketmq-namesrv:9876
```

注意这里使用的是 NameServer 的容器名和内部端口，而非宿主机映射端口 19876。

---

## 13. 扩缩容指南

### 13.1 水平扩展后端服务

```bash
# 启动 3 个 omni-base 实例
docker compose up -d --scale omni-base=3

# 查看实例状态
docker compose ps
```

**注意事项**：
- 多实例时，Docker 自动分配不同的宿主机端口
- Nacos 服务发现会自动注册所有实例
- Spring Cloud Gateway 通过 Nacos 负载均衡路由到各实例
- 如需固定端口，需手动指定：`docker compose up -d --scale omni-base=3`（端口自动递增分配）

### 13.2 数据库连接池考量

水平扩展时，每个实例维护独立的连接池。假设 HikariCP `maximumPoolSize=20`，3 个实例 = 60 个数据库连接。需确保 MySQL `max_connections` 足够：

```sql
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 200;
```

### 13.3 有状态服务注意事项

- **XXL-JOB**：不支持水平扩展（单 Admin 模式），任务调度数据存于 MySQL
- **RocketMQ Broker**：生产环境建议多 Broker 集群，当前为单 Broker 开发模式
- **Nacos**：生产环境建议 3 节点集群，当前为 standalone 单机模式

---

## 14. 运维操作手册

### 14.1 常用命令

```bash
# 查看所有容器状态
docker compose ps

# 查看某个服务日志（实时跟踪）
docker compose logs -f omni-auth

# 重启单个服务
docker compose restart omni-gateway

# 重新构建并重启某个服务
docker compose up -d --build omni-base

# 停止所有容器（保留镜像）
docker compose down

# 停止并删除所有数据（完全重置）
docker compose down -v

# 查看容器资源占用
docker stats --no-stream
```

### 14.2 日志排查

```bash
# 查看最近 100 行日志
docker compose logs --tail=100 omni-gateway

# 查看指定时间段的日志
docker compose logs --since="2025-01-01T10:00:00" omni-auth

# 将日志输出到文件
docker compose logs omni-base > base-logs.txt
```

### 14.3 进入容器调试

```bash
# 进入后端服务容器
docker exec -it omni-auth sh

# 查看容器内进程
docker exec -it omni-auth ps aux

# 测试容器间网络连通性
docker exec -it omni-auth curl -s http://nacos:8848/nacos/
```

---

## 15. 故障排查指南

### 15.1 502 Bad Gateway

**现象**：浏览器访问 `http://localhost:3000` 返回 502。

**排查步骤**：
```bash
# 1. 检查 Nginx 容器是否运行
docker compose ps omni-frontend

# 2. 检查 Gateway 容器是否运行
docker compose ps omni-gateway

# 3. 查看 Nginx 错误日志
docker compose logs omni-frontend

# 4. 测试容器间连通性
docker exec -it omni-frontend curl -s http://omni-gateway:8080/actuator/health
```

**常见原因**：
- Nginx `proxy_pass` 使用了宿主机端口（8102）而非容器内部端口（8080）
- Gateway 容器尚未通过健康检查
- Gateway 启动时 Nacos 未就绪

### 15.2 镜像拉取失败

**现象**：`docker compose pull` 超时或报错 `pull access denied`。

**解决方案**：
1. 配置 Docker 镜像加速（见[第 9 节](#9-docker-镜像加速配置)）
2. 手动拉取验证：`docker pull xuxueli/xxl-job-admin:3.3.1`
3. 检查磁盘空间：`docker system df`

### 15.3 构建失败

**现象**：`docker compose build` 报错。

**Maven 依赖下载超时**：
- 已内置阿里云镜像（`docker-settings.xml`），如仍失败检查网络
- 尝试清理 Docker 构建缓存：`docker builder prune -a`

**npm install 超时**：
- 在 Dockerfile 中添加淘宝镜像源（见[第 9.3 节](#93-npm-依赖加速)）

**磁盘空间不足**：
```bash
docker system prune -a    # 清理所有未使用的镜像/容器/网络
```

### 15.4 端口冲突

**现象**：`bind: address already in use`。

**排查**：
```bash
# Windows
netstat -ano | findstr :8080
# Linux/Mac
lsof -i :8080
```

**解决**：
- 停止占用端口的进程
- 修改 docker-compose.yml 中的宿主机端口映射
- Windows 用户检查 Hyper-V 端口保留（见[第 10 节](#10-windows-hyper-vws12-端口保留问题)）

### 15.5 Nacos 注册失败

**现象**：后端服务日志报 `NacosException: failed to req API:/nacos/v1/ns/instance`。

**排查**：
```bash
# 检查 Nacos 是否健康
docker compose ps nacos
docker exec -it omni-nacos curl -s http://localhost:8848/nacos/

# 检查后端服务的 Nacos 配置
docker compose exec omni-auth env | grep NACOS
```

**常见原因**：
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` 未设为 `nacos:8848`
- `SPRING_CLOUD_NACOS_DISCOVERY_IP` 不为空（应设为 `""` 让 Nacos 自动检测容器 IP）
- Nacos 尚未通过健康检查时后端服务已启动（depends_on 配置问题）

### 15.6 RocketMQ 连接失败

**现象**：后端日志报 `org.apache.rocketmq.remoting.exception.RemotingConnectException`。

**排查**：
```bash
# 检查 Broker 是否注册到 NameServer
docker exec -it omni-rocketmq-namesrv sh mqadmin clusterList -n localhost:9876

# 检查 brokerIP1 配置
docker exec -it omni-rocketmq-broker cat /home/rocketmq/rocketmq-5.3.2/conf/broker.conf
```

**常见原因**：
- `brokerIP1` 未设为容器名 `rocketmq-broker`，导致其他容器无法连接
- 后端服务使用了宿主机端口 19876 而非容器端口 9876

### 15.7 容器启动顺序异常

**现象**：后端服务启动失败但 `docker compose ps` 显示容器在运行。

**排查**：
```bash
# 查看服务启动顺序
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

# 查看服务日志中的启动时间
docker compose logs --timestamps omni-auth | head -5
```

---

## 16. 配置参考表

### 16.1 服务端口映射总表

| 服务 | 容器内部端口 | 宿主机映射端口 | 协议 | 说明 |
|------|-------------|---------------|------|------|
| omni-frontend | 3000 | 3000 | HTTP | 用户访问入口 |
| omni-auth | 8080 | 127.0.0.1:8100 | HTTP | 认证授权服务；仅回环调试 |
| omni-base | 8080 | 127.0.0.1:8101 | HTTP | 基础数据服务；仅回环调试 |
| omni-gateway | 8080 | 8102 | HTTP | API 网关 |
| omni-workflow | 8080 | 127.0.0.1:8103 | HTTP | 工作流服务；仅回环调试 |
| omni-crm | 8080 | 127.0.0.1:8104 | HTTP | CRM 销售前闭环服务；仅回环调试 |
| omni-srm | 8080 | 127.0.0.1:8105 | HTTP | SRM 服务；生产流量只经 Gateway，宿主机仅回环调试 |
| omni-procurement | 8080 | 127.0.0.1:8106 | HTTP | Procurement 服务；生产流量只经 Gateway |
| omni-asset | 8080 | 127.0.0.1:8107 | HTTP | Asset 服务；生产流量只经 Gateway |
| MySQL | 3306 | 127.0.0.1:13306 | TCP | 数据库；显式命名卷，仅回环调试 |
| Redis | 6379 | 127.0.0.1:6379 | TCP | 有密码缓存，仅回环调试 |
| Nacos Console/API/gRPC | 8080/8848/9848 | 仅 127.0.0.1 同端口 | HTTP/gRPC | 注册配置与控制台 |
| RocketMQ NameServer | 9876 | **127.0.0.1:19876** | TCP | 名称服务 |
| RocketMQ Broker | 10909-10912 | 仅 127.0.0.1 同端口 | TCP | 消息代理 |
| XXL-JOB Admin | 8080 | 127.0.0.1:18080 | HTTP | 任务调度；登录与执行器令牌由 `.env` 注入 |

### 16.2 本地初始化账号与密钥来源

| 服务 | 账号 | 密码 | 访问地址 |
|------|------|------|----------|
| 前端应用 | 本地种子 `admin` | 本地种子 `admin123`；首次登录立即修改，生产不得使用 | http://localhost:3000 |
| MySQL | root | `.env: MYSQL_ROOT_PASSWORD` | 127.0.0.1:13306 |
| Redis | 无用户名 | `.env: REDIS_PASSWORD` | 127.0.0.1:6379 |
| Nacos 认证身份 | `.env: NACOS_AUTH_IDENTITY_KEY` | `.env: NACOS_AUTH_IDENTITY_VALUE/TOKEN` | http://127.0.0.1:8080 |
| XXL-JOB Admin | `.env: XXL_JOB_ADMIN_USERNAME` | `.env: XXL_JOB_ADMIN_PASSWORD` | http://127.0.0.1:18080 |

所有 `replace-with-*` 值都必须在启动前替换；Compose 使用必填变量语法失败关闭。新租户管理员密码由创建
请求显式提供，后端只保存 BCrypt 哈希，不再生成 `admin123`。

### 16.3 关键文件路径

| 文件 | 路径 | 说明 |
|------|------|------|
| docker-compose.yml | `docker-compose.yml` | 容器编排配置 |
| 后端 Dockerfile | `docker/backend/Dockerfile` | 微服务多阶段构建 |
| 前端 Dockerfile | `docker/frontend/Dockerfile` | Vue 前端多阶段构建 |
| Nginx 配置 | `docker/frontend/nginx.conf` | 前端反代规则 |
| Broker 配置 | `docker/rocketmq/broker-docker.conf` | RocketMQ Docker 网络配置 |
| Maven 镜像 | `omni-backend/docker-settings.xml` | 阿里云 Maven 加速 |
| 数据库初始化 | `scripts/sql/init-all.sql` | 业务库表结构与数据 |
| CRM 既有环境迁移 | `scripts/sql/migrate-crm-mvp.sql` | 创建 CRM 库、补权限角色与日志幂等列 |
| SRM 既有环境迁移 | `scripts/sql/migrate-srm-mvp.sql` | 幂等创建 SRM/Auth Outbox、权限角色、SRM 表与升级约束 |
| Workflow 启动幂等迁移 | `scripts/sql/migrate-workflow-process-start-idempotency.sql` | 补齐跨服务流程启动幂等表与约束 |
| Procurement 既有环境迁移 | `scripts/sql/migrate-procurement-mvp.sql` | 创建采购库、权限角色、业务表与默认配置 |
| Asset 既有环境迁移 | `scripts/sql/migrate-asset-mvp.sql` | 创建资产库、权限角色、业务表与默认字典 |
| 新租户初始化 | `scripts/sql/sp_init_tenant.sql` | 重建可重入存储过程，映射权限路径并归一 SRM 模板/字典 |
| Nacos 初始化 | `scripts/sql/init-nacos.sql` | Nacos 配置数据 |
| XXL-JOB 初始化 | `scripts/sql/init-xxl-job.sql` | 调度任务数据 |
| 启动脚本 (Linux) | `start.sh` | 一键启动 |
| 启动脚本 (Windows) | `start.bat` | 一键启动（含端口保护） |
| 停止脚本 (Linux) | `stop.sh` | 一键停止 |
| 停止脚本 (Windows) | `stop.bat` | 一键停止 |

---

## 附录：构建产物体积参考

| 镜像 | 大小（约） | 说明 |
|------|-----------|------|
| omni-auth:latest | ~200MB | JRE + Fat JAR |
| omni-base:latest | ~200MB | JRE + Fat JAR |
| omni-gateway:latest | ~200MB | JRE + Fat JAR |
| omni-workflow:latest | ~250MB | JRE + Fat JAR + Flowable 引擎 |
| omni-crm:latest | ~210MB | JRE + Fat JAR + CRM/Outbox |
| omni-frontend:latest | ~50MB | Nginx + Vue 静态文件 |
| mysql:8.4 | ~600MB | 官方镜像 |
| nacos/nacos-server:v3.1.1 | ~800MB | 官方镜像 |
| apache/rocketmq:5.3.2 | ~700MB | 官方镜像 |
