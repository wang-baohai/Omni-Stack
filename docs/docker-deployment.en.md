# Docker Full-Stack Deployment Deep Guide

> This document is the complete technical reference for Omni-Stack Docker deployment. It covers architecture design, build principles, configuration details, operations, and troubleshooting.  
> For quick start, refer to the Docker One-Click Deployment section in [README.en.md](../README.en.md).

---

## Table of Contents

- [1. Architecture Overview](#1-architecture-overview)
- [2. Container Network Topology](#2-container-network-topology)
- [3. Startup Chain & Health Checks](#3-startup-chain--health-checks)
- [4. Multi-Stage Build Principles](#4-multi-stage-build-principles)
- [5. docker-compose.yml Service-by-Service Breakdown](#5-docker-composeyml-service-by-service-breakdown)
- [6. Environment Variable Override Mechanism](#6-environment-variable-override-mechanism)
- [7. Nginx Reverse Proxy Configuration](#7-nginx-reverse-proxy-configuration)
- [8. Data Initialization & Persistence](#8-data-initialization--persistence)
- [9. Docker Registry Mirror Configuration](#9-docker-registry-mirror-configuration)
- [10. Windows Hyper-V/WSL2 Port Reservation Issues](#10-windows-hyper-vws12-port-reservation-issues)
- [11. Nacos v3.1.1 Health Check Endpoint Changes](#11-nacos-v311-health-check-endpoint-changes)
- [12. RocketMQ Broker Docker Network Configuration](#12-rocketmq-broker-docker-network-configuration)
- [13. Scaling Guide](#13-scaling-guide)
- [14. Operations Manual](#14-operations-manual)
- [15. Troubleshooting Guide](#15-troubleshooting-guide)
- [16. Configuration Reference](#16-configuration-reference)

---

## 1. Architecture Overview

The Omni-Stack Docker full-stack contains **15 containers** organized in three layers:

```
┌─────────────────────────────────────────────────────────────┐
│                   Frontend Layer (1 container)               │
│  omni-frontend (Vue 3 + Nginx:3000)                        │
└──────────────────────────┬──────────────────────────────────┘
                           │ proxy_pass
┌──────────────────────────┴──────────────────────────────────┐
│               Backend Microservices Layer (8 containers)     │
│  Gateway · Auth · Base · Workflow · CRM · SRM              │
│  Procurement · Asset (only Gateway is publicly exposed)     │
└────────┼────────────────────┬───────────────────────────────┘
         │                    │
┌────────┴────────────────────┴───────────────────────────────┐
│                  Middleware Layer (6 containers)             │
│  MySQL (:3306) · Redis (:6379) · Nacos (:8080/:8848/:9848) │
│  RocketMQ NameServer (:19876) · Broker (:10909-10912)      │
│  XXL-JOB Admin (:18080)                                    │
└─────────────────────────────────────────────────────────────┘
```

**Design Principles**:
- All backend microservice containers use internal port **8080**, differentiated via host port mapping
- Inter-container communication uses **Docker internal network** (`omni-network`) with container name resolution
- Frontend Nginx serves both static files and API reverse proxy — users only access one port (3000)

---

## 2. Container Network Topology

All containers share a Docker Bridge network `omni-network`:

```yaml
networks:
  omni-network:
    driver: bridge
```

**Network Communication Rules**:

| Source Container | Target Container | Address Used | Description |
|-----------------|------------------|--------------|-------------|
| omni-frontend | omni-gateway | `omni-gateway:8080` | Nginx proxy_pass (internal port) |
| omni-gateway | omni-auth | `omni-auth:8080` | Spring Cloud Gateway routing |
| omni-auth | mysql | `mysql:3306` | JDBC connection |
| omni-auth | redis | `redis:6379` | Cache/Session |
| omni-auth | nacos | `nacos:8848` | Service registry/config |
| omni-base | rocketmq-namesrv | `rocketmq-namesrv:9876` | MQ message sending |
| omni-base | xxl-job-admin | `xxl-job-admin:8080` | Job executor registration |
| Host Browser | omni-frontend | `localhost:3000` | User access entry point |
| Host Browser | Nacos Console | `localhost:8080` | Operations management |

> **Key Distinction**: Inter-container communication uses internal ports (e.g., 8080), while host access uses mapped ports (e.g., 8100/8101/8102/8103).

---

## 3. Startup Chain & Health Checks

### 3.1 Layered Startup Order

Containers are organized into 5 layers using `depends_on` + `condition: service_healthy` for ordered startup:

```
Layer 0:  mysql · redis · rocketmq-namesrv          (no dependencies, start first)
            │         │          │
Layer 1:  nacos     xxl-job   rocketmq-broker        (depend on Layer 0)
            │         │          │
Layer 2:  omni-auth                                  (depends on nacos + redis + mysql)
            │
Layer 3:  omni-base · omni-workflow · omni-gateway   (depend on Layer 1 + Layer 2)
                                                │
Layer 4:  omni-frontend                           (depends on omni-gateway)
```

### 3.2 Health Check Configuration Overview

| Service | Check Method | interval | timeout | retries | start_period |
|---------|-------------|----------|---------|---------|--------------|
| MySQL | `mysqladmin ping` | 10s | 5s | 5 | 30s |
| Redis | `redis-cli ping` | 10s | 5s | 5 | 10s |
| Nacos | `curl http://localhost:8848/nacos/` | 15s | 5s | 5 | 60s |
| RocketMQ NameServer | TCP probe `/dev/tcp/127.0.0.1/9876` | 10s | 5s | 5 | 30s |
| RocketMQ Broker | TCP probe `/dev/tcp/127.0.0.1/10911` | 15s | 5s | 5 | 60s |
| XXL-JOB Admin | TCP probe `/dev/tcp/localhost/8080` | 15s | 5s | 5 | 60s |
| omni-auth | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-base | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-gateway | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-workflow | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |

> **Why start_period = 90s?**  
> Spring Boot microservices need to load many beans + initialize databases + register with Nacos on cold start, which may take 60-80 seconds. Setting 90s prevents false unhealthy status.

---

## 4. Multi-Stage Build Principles

### 4.1 Backend Microservice Build

All backend microservices share a single Dockerfile (`docker/backend/Dockerfile`), differentiated by the `SERVICE_NAME` build argument:

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-25-alpine AS build
COPY docker-settings.xml /root/.m2/settings.xml   # Aliyun Maven mirror
WORKDIR /build

# Copy POM files first (leverage Docker layer caching)
COPY mvnw pom.xml ./
COPY omni-common-core/pom.xml omni-common-core/
COPY omni-common/pom.xml omni-common/
# ... other module POMs ...
RUN mvn dependency:go-offline -B -q || true

# Copy source and build the specified service
COPY . .
ARG SERVICE_NAME
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl bash
ARG SERVICE_NAME
COPY --from=build /build/${SERVICE_NAME}/target/*.jar /app/app.jar
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK ...
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Layer Caching Optimization Strategy**:

1. **POM layer caching**: Copy all `pom.xml` files and run `dependency:go-offline` first — as long as POMs don't change, subsequent builds reuse the cached layer and skip dependency downloads
2. **Source isolation**: `COPY . .` only triggers a rebuild when source code changes (POM cache still valid)
3. **Single-service build**: `mvn package -pl ${SERVICE_NAME} -am` only builds the target service and its dependencies, avoiding full compilation

**Image Selection Considerations**:

| Choice | Rationale |
|--------|-----------|
| `maven:3.9-eclipse-temurin-25-alpine` | Alpine base is small (~200MB vs ~800MB for Debian), includes Maven 3.9 + JDK 25 |
| `eclipse-temurin:25-jre-alpine` | Runtime only needs JRE (~80MB), 300MB+ smaller than full JDK image |

### 4.2 Frontend Build

```dockerfile
# Stage 1: Node Build
FROM node:22-alpine AS build
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                                  # Exact install (using lock file)
COPY omni-frontend/ .
RUN npm run build                           # Vite production build

# Stage 2: Nginx Runtime
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
```

**Layer caching**: Copy `package*.json` and run `npm ci` first — source code changes won't trigger dependency reinstallation.

---

## 5. docker-compose.yml Service-by-Service Breakdown

### 5.1 MySQL 8.4

```yaml
mysql:
  image: mysql:8.4
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?configure MYSQL_ROOT_PASSWORD in .env}
    MYSQL_DATABASE: omni_auth          # First auto-created database
    MYSQL_CHARACTER_SET_SERVER: utf8mb4
    MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    TZ: Asia/Shanghai                  # Timezone
  volumes:
    - omni-mysql-data:/var/lib/mysql
  volumes:
    - ./scripts/sql/init-all.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    - ./scripts/sql/init-nacos.sql:/docker-entrypoint-initdb.d/02-init-nacos.sql:ro
    - ./scripts/sql/init-xxl-job.sql:/docker-entrypoint-initdb.d/03-init-xxl-job.sql:ro
```

**Key Points**:
- Three SQL scripts auto-execute in order: `01-init.sql` (business DBs + seed data) → `02-init-nacos.sql` (Nacos config DB) → `03-init-xxl-job.sql` (XXL-JOB DB)
- Mounted as `:ro` (read-only) to prevent accidental modification
- MySQL uses the named volume `omni-mysql-data`; normal container recreation preserves data

### 5.2 Redis 7.4

```yaml
redis:
  image: redis:7.4
  command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:?configure REDIS_PASSWORD in .env}"]
```

### 5.3 Nacos v3.1.1

```yaml
nacos:
  image: nacos/nacos-server:v3.1.1
  environment:
    MODE: standalone                      # Single-node mode
    SPRING_DATASOURCE_PLATFORM: mysql     # External MySQL storage (not embedded Derby)
    MYSQL_SERVICE_HOST: mysql             # Points to MySQL container name
    NACOS_AUTH_TOKEN: ...                 # JWT signing secret (Base64 encoded)
  ports:
    - "8080:8080"                         # Console
    - "8848:8848"                         # API port (service registry/config)
    - "9848:9848"                         # gRPC port (long connections)
```

**Three-Port Explanation**:

| Port | Purpose | Consumer |
|------|---------|----------|
| 8080 | Web Console | Operations via browser |
| 8848 | HTTP API | Backend microservice registry/config |
| 9848 | gRPC | Nacos 2.x+ client long connections |

### 5.4 RocketMQ 5.3.2 (NameServer + Broker)

```yaml
rocketmq-namesrv:
  ports:
    - "19876:9876"     # Host 19876 → Container 9876 (avoids Windows Hyper-V port conflicts)

rocketmq-broker:
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876          # Inter-container communication
    JAVA_OPT_EXT: "-Drocketmq.broker.diskSpaceWarningLevelRatio=0.98"
  volumes:
    - ./docker/rocketmq/broker-docker.conf:...   # Docker-specific Broker config
  command: sh mqbroker -n rocketmq-namesrv:9876 -c ... --enable-proxy
```

> **Why port mapping 19876:9876**: Windows Hyper-V/WSL2 reserves port ranges including 9859-9958. Mapping 9876 directly causes conflicts. See [Section 10](#10-windows-hyper-vws12-port-reservation-issues).

### 5.5 XXL-JOB Admin v3.3.1

```yaml
xxl-job-admin:
  environment:
    PARAMS: >
      --spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?...
      --spring.datasource.username=root
      --spring.datasource.password=${MYSQL_ROOT_PASSWORD}
      --xxl.job.login.username=${XXL_JOB_ADMIN_USERNAME}
      --xxl.job.login.password=${XXL_JOB_ADMIN_PASSWORD}
      --xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN}
      --xxl.job.accessToken=           # Empty token (no auth in dev)
```

### 5.6 Backend Microservices (4 instances)

All four backend microservices use the same Dockerfile, differentiated by `build.args.SERVICE_NAME`. Each service's `environment` overrides key configurations:

| Environment Variable | Description | Example Value |
|---------------------|-------------|---------------|
| `SERVER_PORT` | Container internal port (unified 8080) | `8080` |
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Nacos address | `nacos:8848` |
| `SPRING_DATASOURCE_URL` | Database connection | `jdbc:mysql://mysql:3306/omni_auth?...` |
| `SPRING_DATA_REDIS_HOST` | Redis address | `redis` |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | Registration IP (empty = auto-detect container IP) | `""` |
| `AUTH_ISSUER` | JWT Issuer (auth only) | `http://omni-auth:8080` |
| `AUTH_JWKS_URI` | JWKS endpoint (gateway only) | `http://omni-auth:8080/oauth2/jwks` |

### 5.7 Frontend

```yaml
omni-frontend:
  build:
    context: .                          # Project root (needs access to docker/ configs)
    dockerfile: docker/frontend/Dockerfile
  ports:
    - "3000:3000"                       # Single user access entry point
  depends_on:
    omni-gateway:
      condition: service_healthy        # Start only after gateway is ready
```

---

## 6. Environment Variable Override Mechanism

Spring Boot supports overriding `application.yml` configurations via environment variables, heavily used in Docker deployments.

### 6.1 Conversion Rules

| application.yml Config | Environment Variable | Conversion Rule |
|----------------------|---------------------|-----------------|
| `server.port` | `SERVER_PORT` | Uppercase + dots→underscores |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | Uppercase + dots→underscores |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | Uppercase + dots→underscores |
| `spring.cloud.nacos.discovery.server-addr` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Uppercase + hyphens→underscores |

### 6.2 Priority (High to Low)

```
1. Command-line arguments (--server.port=9090)
2. Environment variables (SERVER_PORT=9090)
3. application-{profile}.yml
4. application.yml
5. Nacos Config Center (if enabled)
```

### 6.3 Typical Overrides in Docker Deployment

| Config | application.yml Value (local dev) | Docker Environment Value |
|--------|----------------------------------|------------------------|
| Database URL | `localhost:3306` | `mysql:3306` |
| Redis host | `localhost` | `redis` |
| Nacos address | `localhost:8848` | `nacos:8848` |
| RocketMQ | `localhost:9876` | `rocketmq-namesrv:9876` |
| JWT Issuer | `http://localhost:8100` | `http://omni-auth:8080` |
| OAuth2 Callback | `http://localhost:8102/api/auth/...` | `http://localhost:8102/api/auth/...` |

> **Note**: OAuth2 callback URIs use the Gateway address `localhost:8102`. Auth and other private service ports are bound to loopback for diagnostics only and must not be exposed publicly.

---

## 7. Nginx Reverse Proxy Configuration

### 7.1 Configuration Breakdown

```nginx
server {
    listen 3000;
    root /usr/share/nginx/html;

    # API request reverse proxy to Gateway
    location /api/ {
        proxy_pass http://omni-gateway:8080;    # ← Container internal port!
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 endpoint reverse proxy
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
        # ... same as above
    }

    # OIDC Discovery endpoint reverse proxy
    location /.well-known/ {
        proxy_pass http://omni-gateway:8080;
        # ... same as above
    }

    # Vue Router History Mode
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### 7.2 Common Mistake: proxy_pass Port

| Configuration | Correct? | Explanation |
|---------------|----------|-------------|
| `proxy_pass http://omni-gateway:8080` | ✅ | Container internal port, reachable within Docker network |
| `proxy_pass http://omni-gateway:8102` | ❌ | 8102 is host mapped port, not accessible between containers |
| `proxy_pass http://localhost:8102` | ❌ | localhost inside container refers to itself, not gateway |
| `proxy_pass http://host.docker.internal:8102` | ⚠️ | Works but bypasses Docker network, poor performance |

---

## 8. Data Initialization & Persistence

### 8.1 Database Initialization Chain

MySQL container auto-executes SQL scripts in `/docker-entrypoint-initdb.d/` on first startup:

```
01-init.sql       → Create omni_auth / omni_base / omni_workflow databases + seed data
02-init-nacos.sql → Create nacos_config database + Nacos config data
03-init-xxl-job.sql → Create xxl_job database + XXL-JOB scheduling data
```

### 8.2 Data Persistence Strategy

The current configuration persists MySQL in the named volume `omni-mysql-data`. `docker compose down` retains it; `docker compose down -v` deletes it irreversibly.

```yaml
mysql:
  volumes:
    - omni-mysql-data:/var/lib/mysql

volumes:
  omni-mysql-data:
```

---

## 9. Docker Registry Mirror Configuration

### 9.1 Docker Registry Mirror

Users in regions with slow Docker Hub access should configure mirrors:

**Linux** (`/etc/docker/daemon.json`):
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```
```bash
sudo systemctl restart docker
```

**Windows / Mac** (Docker Desktop → Settings → Docker Engine):
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```

### 9.2 Maven Dependency Acceleration

The backend Dockerfile includes Aliyun Maven mirror (`docker-settings.xml`):

```xml
<mirror>
  <id>aliyun</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

### 9.3 npm Dependency Acceleration

To speed up frontend npm installs, add a mirror registry in the Dockerfile:

```dockerfile
RUN npm config set registry https://registry.npmmirror.com
RUN npm ci
```

---

## 10. Windows Hyper-V/WSL2 Port Reservation Issues

### 10.1 Problem Description

Windows 10/11 Hyper-V or WSL2 dynamically reserves TCP port ranges (e.g., 9859-9958). If a reserved range includes a port Docker needs to map (e.g., 9876), container startup fails:

```
Error starting userland proxy: listen tcp4 0.0.0.0:9876: bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

### 10.2 Solutions

**Solution A: Port Offset (Adopted)**

Change RocketMQ NameServer host port from 9876 to 19876:

```yaml
ports:
  - "19876:9876"    # Host 19876 → Container 9876
```

Inter-container communication is unaffected (still uses `rocketmq-namesrv:9876`); only host access uses 19876.

**Solution B: Port Reservation Protection (Implemented in start.bat)**

Reserve required ports with admin privileges before starting Docker:

```batch
:: Stop winnat → Reserve ports → Restart winnat
net stop winnat
netsh int ipv4 add excludedportrange protocol=tcp startport=9876 numberofports=1 persistent=yes
net start winnat
```

**Solution C: Restart WSL (Temporary)**

```powershell
wsl --shutdown
# Then restart Docker Desktop
```

### 10.3 Verify Reserved Ports

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

---

## 11. Nacos v3.1.1 Health Check Endpoint Changes

### 11.1 Change Description

Starting from Nacos v3.x, the `/nacos/actuator/health` endpoint was removed. Health checks now use:

| Nacos Version | Health Check Endpoint | Method |
|---------------|----------------------|--------|
| v2.x | `/nacos/actuator/health` | GET |
| v3.0+ | `/nacos/` | GET |

### 11.2 docker-compose.yml Configuration

```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/"]
  start_period: 60s     # Nacos starts slowly, needs 40-60 seconds
```

> **Note**: `curl -sf` — `-s` for silent mode, `-f` for non-zero exit on HTTP errors. Together they prevent extraneous output in healthcheck.

---

## 12. RocketMQ Broker Docker Network Configuration

### 12.1 Background

RocketMQ Broker registers its IP with the NameServer by default. In Docker, the Broker may get `127.0.0.1` or a Docker bridge IP, making it unreachable from other containers.

### 12.2 Solution

Custom config file `docker/rocketmq/broker-docker.conf` explicitly sets `brokerIP1`:

```properties
brokerIP1 = rocketmq-broker    # Set to container name, resolvable via Docker DNS
```

### 12.3 Backend Connection Configuration

Backend microservices connect to RocketMQ via environment variables:

```yaml
SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER: rocketmq-namesrv:9876
```

Note this uses the NameServer's container name and internal port, not the host-mapped port 19876.

---

## 13. Scaling Guide

### 13.1 Horizontal Scaling of Backend Services

```bash
# Start 3 omni-base instances
docker compose up -d --scale omni-base=3

# Check instance status
docker compose ps
```

**Important Notes**:
- Docker automatically assigns different host ports for multiple instances
- Nacos service discovery auto-registers all instances
- Spring Cloud Gateway load-balances across instances via Nacos
- For fixed ports, manual specification is needed

### 13.2 Database Connection Pool Considerations

Each instance maintains an independent connection pool during horizontal scaling. With HikariCP `maximumPoolSize=20` and 3 instances = 60 database connections. Ensure MySQL `max_connections` is sufficient:

```sql
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 200;
```

### 13.3 Stateful Service Considerations

- **XXL-JOB**: No horizontal scaling (single Admin mode), scheduling data stored in MySQL
- **RocketMQ Broker**: Production should use multi-Broker cluster; current is single-Broker dev mode
- **Nacos**: Production should use 3-node cluster; current is standalone single-node mode

---

## 14. Operations Manual

### 14.1 Common Commands

```bash
# View all container status
docker compose ps

# View service logs (real-time follow)
docker compose logs -f omni-auth

# Restart a single service
docker compose restart omni-gateway

# Rebuild and restart a service
docker compose up -d --build omni-base

# Stop all containers (keep images)
docker compose down

# Stop and remove all data (full reset)
docker compose down -v

# View container resource usage
docker stats --no-stream
```

### 14.2 Log Investigation

```bash
# View last 100 log lines
docker compose logs --tail=100 omni-gateway

# View logs from a specific time
docker compose logs --since="2025-01-01T10:00:00" omni-auth

# Export logs to file
docker compose logs omni-base > base-logs.txt
```

### 14.3 Enter Container for Debugging

```bash
# Enter backend service container
docker exec -it omni-auth sh

# View processes inside container
docker exec -it omni-auth ps aux

# Test inter-container network connectivity
docker exec -it omni-auth curl -s http://nacos:8848/nacos/
```

---

## 15. Troubleshooting Guide

### 15.1 502 Bad Gateway

**Symptom**: Browser accessing `http://localhost:3000` returns 502.

**Diagnostic Steps**:
```bash
# 1. Check if Nginx container is running
docker compose ps omni-frontend

# 2. Check if Gateway container is running
docker compose ps omni-gateway

# 3. View Nginx error logs
docker compose logs omni-frontend

# 4. Test inter-container connectivity
docker exec -it omni-frontend curl -s http://omni-gateway:8080/actuator/health
```

**Common Causes**:
- Nginx `proxy_pass` uses host port (8102) instead of container internal port (8080)
- Gateway container hasn't passed health check yet
- Gateway started before Nacos was ready

### 15.2 Image Pull Failure

**Symptom**: `docker compose pull` times out or reports `pull access denied`.

**Solutions**:
1. Configure Docker registry mirror (see [Section 9](#9-docker-registry-mirror-configuration))
2. Manual pull verification: `docker pull xuxueli/xxl-job-admin:3.3.1`
3. Check disk space: `docker system df`

### 15.3 Build Failure

**Symptom**: `docker compose build` fails.

**Maven dependency download timeout**:
- Aliyun mirror is built-in (`docker-settings.xml`); if still failing, check network
- Try clearing Docker build cache: `docker builder prune -a`

**npm install timeout**:
- Add mirror registry in Dockerfile (see [Section 9.3](#93-npm-dependency-acceleration))

**Insufficient disk space**:
```bash
docker system prune -a    # Clean all unused images/containers/networks
```

### 15.4 Port Conflicts

**Symptom**: `bind: address already in use`.

**Diagnosis**:
```bash
# Windows
netstat -ano | findstr :8080
# Linux/Mac
lsof -i :8080
```

**Solutions**:
- Stop the process occupying the port
- Modify host port mapping in docker-compose.yml
- Windows users: check Hyper-V port reservation (see [Section 10](#10-windows-hyper-vws12-port-reservation-issues))

### 15.5 Nacos Registration Failure

**Symptom**: Backend logs report `NacosException: failed to req API:/nacos/v1/ns/instance`.

**Diagnosis**:
```bash
# Check if Nacos is healthy
docker compose ps nacos
docker exec -it omni-nacos curl -s http://localhost:8848/nacos/

# Check backend service Nacos config
docker compose exec omni-auth env | grep NACOS
```

**Common Causes**:
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` not set to `nacos:8848`
- `SPRING_CLOUD_NACOS_DISCOVERY_IP` is not empty (should be `""` for auto-detection)
- Nacos hadn't passed health check when backend service started (depends_on config issue)

### 15.6 RocketMQ Connection Failure

**Symptom**: Backend logs report `org.apache.rocketmq.remoting.exception.RemotingConnectException`.

**Diagnosis**:
```bash
# Check if Broker is registered to NameServer
docker exec -it omni-rocketmq-namesrv sh mqadmin clusterList -n localhost:9876

# Check brokerIP1 configuration
docker exec -it omni-rocketmq-broker cat /home/rocketmq/rocketmq-5.3.2/conf/broker.conf
```

**Common Causes**:
- `brokerIP1` not set to container name `rocketmq-broker`, preventing other containers from connecting
- Backend service uses host port 19876 instead of container port 9876

### 15.7 Container Startup Order Anomalies

**Symptom**: Backend service fails to start but `docker compose ps` shows container running.

**Diagnosis**:
```bash
# View service startup order
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

# View service startup timestamps
docker compose logs --timestamps omni-auth | head -5
```

---

## 16. Configuration Reference

### 16.1 Service Port Mapping Table

| Service | Internal Port | Host Mapped Port | Protocol | Description |
|---------|--------------|-----------------|----------|-------------|
| omni-frontend | 3000 | 3000 | HTTP | User access entry |
| omni-auth | 8080 | 8100 | HTTP | Auth service |
| omni-base | 8080 | 8101 | HTTP | Base data service |
| omni-gateway | 8080 | 8102 | HTTP | API Gateway |
| omni-workflow | 8080 | 8103 | HTTP | Workflow service |
| omni-crm | 8080 | 8104 | HTTP | CRM service |
| omni-srm | 8080 | 8105 | HTTP | SRM service |
| omni-procurement | 8080 | 8106 | HTTP | Procurement service |
| omni-asset | 8080 | 8107 | HTTP | Asset service |
| MySQL | 3306 | 13306 | TCP | Database |
| Redis | 6379 | 6379 | TCP | Cache |
| Nacos Console | 8080 | 8080 | HTTP | Console |
| Nacos API | 8848 | 8848 | HTTP | Service registry/config |
| Nacos gRPC | 9848 | 9848 | gRPC | Long connections |
| RocketMQ NameServer | 9876 | **19876** | TCP | Name service |
| RocketMQ Broker | 10909-10912 | 10909-10912 | TCP | Message broker |
| XXL-JOB Admin | 8080 | 18080 | HTTP | Job scheduler |

### 16.2 Credentials and Exposure

No production credential is hard-coded. Configure MySQL, Redis, Nacos, XXL-JOB, OAuth state, JWK encryption, internal API, and application database secrets in `.env` before startup. Seed application accounts are local-demo data only and must be changed or removed before any shared deployment. Only the frontend (`3000`) and Gateway (`8102`) are intended as public entry points; all other published ports bind to `127.0.0.1`.

### 16.3 Key File Paths

| File | Path | Description |
|------|------|-------------|
| docker-compose.yml | `docker-compose.yml` | Container orchestration config |
| Backend Dockerfile | `docker/backend/Dockerfile` | Microservice multi-stage build |
| Frontend Dockerfile | `docker/frontend/Dockerfile` | Vue frontend multi-stage build |
| Nginx Config | `docker/frontend/nginx.conf` | Frontend reverse proxy rules |
| Broker Config | `docker/rocketmq/broker-docker.conf` | RocketMQ Docker network config |
| Maven Mirror | `omni-backend/docker-settings.xml` | Aliyun Maven acceleration |
| DB Init | `scripts/sql/init-all.sql` | Business schema & seed data |
| Nacos Init | `scripts/sql/init-nacos.sql` | Nacos config data |
| XXL-JOB Init | `scripts/sql/init-xxl-job.sql` | Scheduling task data |
| Start Script (Linux) | `start.sh` | One-click start |
| Start Script (Windows) | `start.bat` | One-click start (with port protection) |
| Stop Script (Linux) | `stop.sh` | One-click stop |
| Stop Script (Windows) | `stop.bat` | One-click stop |

---

## Appendix: Build Artifact Size Reference

| Image | Size (approx.) | Description |
|-------|----------------|-------------|
| omni-auth:latest | ~200MB | JRE + Fat JAR |
| omni-base:latest | ~200MB | JRE + Fat JAR |
| omni-gateway:latest | ~200MB | JRE + Fat JAR |
| omni-workflow:latest | ~250MB | JRE + Fat JAR + Flowable engine |
| omni-frontend:latest | ~50MB | Nginx + Vue static files |
| mysql:8.4 | ~600MB | Official image |
| nacos/nacos-server:v3.1.1 | ~800MB | Official image |
| apache/rocketmq:5.3.2 | ~700MB | Official image |
