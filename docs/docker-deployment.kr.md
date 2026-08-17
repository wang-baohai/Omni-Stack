# Docker 풀스택 배포 심화 가이드

> 이 문서는 Omni-Stack Docker 배포의 완전한 기술 레퍼런스입니다. 아키텍처 설계, 빌드 원리, 설정 상세, 운영 조작, 트러블슈팅을 다룹니다.  
> 빠른 시작은 [README.kr.md](../README.kr.md)의 Docker 원클릭 배포 섹션을 참조하세요.

---

## 목차

- [1. 아키텍처 개요](#1-아키텍처-개요)
- [2. 컨테이너 네트워크 토폴로지](#2-컨테이너-네트워크-토폴로지)
- [3. 기동 체인과 헬스체크](#3-기동-체인과-헬스체크)
- [4. 멀티스테이지 빌드 원리](#4-멀티스테이지-빌드-원리)
- [5. docker-compose.yml 서비스별 설정 해설](#5-docker-composeyml-서비스별-설정-해설)
- [6. 환경 변수 오버라이드 메커니즘](#6-환경-변수-오버라이드-메커니즘)
- [7. Nginx 리버스 프록시 설정](#7-nginx-리버스-프록시-설정)
- [8. 데이터 초기화와 영속화](#8-데이터-초기화와-영속화)
- [9. Docker 레지스트리 미러 설정](#9-docker-레지스트리-미러-설정)
- [10. Windows Hyper-V/WSL2 포트 예약 문제](#10-windows-hyper-vws12-포트-예약-문제)
- [11. Nacos v3.1.1 헬스체크 엔드포인트 변경](#11-nacos-v311-헬스체크-엔드포인트-변경)
- [12. RocketMQ Broker Docker 네트워크 설정](#12-rocketmq-broker-docker-네트워크-설정)
- [13. 스케일링 가이드](#13-스케일링-가이드)
- [14. 운영 매뉴얼](#14-운영-매뉴얼)
- [15. 트러블슈팅 가이드](#15-트러블슈팅-가이드)
- [16. 설정 레퍼런스](#16-설정-레퍼런스)

---

## 1. 아키텍처 개요

Omni-Stack Docker 풀스택은 **15개 컨테이너**로 구성되며, 3개의 계층으로 나뉩니다:

```
┌─────────────────────────────────────────────────────────────┐
│                   프론트엔드 계층 (1개 컨테이너)              │
│  omni-frontend (Vue 3 + Nginx:3000)                        │
└──────────────────────────┬──────────────────────────────────┘
                           │ proxy_pass
┌──────────────────────────┴──────────────────────────────────┐
│              백엔드 마이크로서비스 계층 (8개 컨테이너)         │
│  Gateway · Auth · Base · Workflow · CRM · SRM              │
│  Procurement · Asset (공개 진입점은 Gateway만)              │
└────────┼────────────────────┬───────────────────────────────┘
         │                    │
┌────────┴────────────────────┴───────────────────────────────┐
│                   미들웨어 계층 (6개 컨테이너)                │
│  MySQL (:3306) · Redis (:6379) · Nacos (:8080/:8848/:9848) │
│  RocketMQ NameServer (:19876) · Broker (:10909-10912)      │
│  XXL-JOB Admin (:18080)                                    │
└─────────────────────────────────────────────────────────────┘
```

**설계 원칙**:
- 모든 백엔드 마이크로서비스 컨테이너 내부 포트는 **8080**으로 통일, 호스트 포트 매핑으로 구분
- 컨테이너 간 통신은 **Docker 내부 네트워크**(`omni-network`)를 통해 컨테이너 이름으로 해결
- 프론트엔드 Nginx가 정적 파일과 API 리버스 프록시를 모두 제공 — 사용자는 하나의 포트(3000)만 접근

---

## 2. 컨테이너 네트워크 토폴로지

모든 컨테이너는 Docker 브리지 네트워크 `omni-network`를 공유합니다:

```yaml
networks:
  omni-network:
    driver: bridge
```

**네트워크 통신 규칙**:

| 송신 컨테이너 | 수신 컨테이너 | 사용 주소 | 설명 |
|--------------|--------------|----------|------|
| omni-frontend | omni-gateway | `omni-gateway:8080` | Nginx proxy_pass (내부 포트) |
| omni-gateway | omni-auth | `omni-auth:8080` | Spring Cloud Gateway 라우팅 |
| omni-auth | mysql | `mysql:3306` | JDBC 연결 |
| omni-auth | redis | `redis:6379` | 캐시/세션 |
| omni-auth | nacos | `nacos:8848` | 서비스 등록/설정 |
| omni-base | rocketmq-namesrv | `rocketmq-namesrv:9876` | MQ 메시지 전송 |
| omni-base | xxl-job-admin | `xxl-job-admin:8080` | 잡 실행자 등록 |
| 호스트 브라우저 | omni-frontend | `localhost:3000` | 사용자 접근 입구 |
| 호스트 브라우저 | Nacos Console | `localhost:8080` | 운영 관리 |

> **핵심 구분**: 컨테이너 간 통신은 내부 포트(예: 8080)를 사용하고, 호스트 접근은 매핑 포트(예: 8100/8101/8102/8103)를 사용합니다.

---

## 3. 기동 체인과 헬스체크

### 3.1 계층별 기동 순서

컨테이너는 `depends_on` + `condition: service_healthy`를 사용하여 5개 계층으로 순차 기동됩니다:

```
Layer 0:  mysql · redis · rocketmq-namesrv          (의존성 없음, 가장 먼저 기동)
            │         │          │
Layer 1:  nacos     xxl-job   rocketmq-broker        (Layer 0에 의존)
            │         │          │
Layer 2:  omni-auth                                  (nacos + redis + mysql에 의존)
            │
Layer 3:  omni-base · omni-workflow · omni-gateway   (Layer 1 + Layer 2에 의존)
                                                │
Layer 4:  omni-frontend                           (omni-gateway에 의존)
```

### 3.2 헬스체크 설정 일람

| 서비스 | 확인 방식 | interval | timeout | retries | start_period |
|--------|----------|----------|---------|---------|--------------|
| MySQL | `mysqladmin ping` | 10s | 5s | 5 | 30s |
| Redis | `redis-cli ping` | 10s | 5s | 5 | 10s |
| Nacos | `curl http://localhost:8848/nacos/` | 15s | 5s | 5 | 60s |
| RocketMQ NameServer | TCP 포트 탐색 `/dev/tcp/127.0.0.1/9876` | 10s | 5s | 5 | 30s |
| RocketMQ Broker | TCP 포트 탐색 `/dev/tcp/127.0.0.1/10911` | 15s | 5s | 5 | 60s |
| XXL-JOB Admin | TCP 포트 탐색 `/dev/tcp/localhost/8080` | 15s | 5s | 5 | 60s |
| omni-auth | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-base | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-gateway | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-workflow | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |

> **왜 start_period = 90s?**  
> Spring Boot 마이크로서비스는 콜드 스타트 시 많은 Bean 로딩 + DB 초기화 + Nacos 등록이 필요하여 60-80초가 소요될 수 있습니다. 90초로 설정하여 오판을 방지합니다.

---

## 4. 멀티스테이지 빌드 원리

### 4.1 백엔드 마이크로서비스 빌드

모든 백엔드 마이크로서비스는 공통 Dockerfile(`docker/backend/Dockerfile`)을 공유하며, `SERVICE_NAME` 빌드 인수로 구분합니다:

```dockerfile
# Stage 1: 빌드
FROM maven:3.9-eclipse-temurin-25-alpine AS build
COPY docker-settings.xml /root/.m2/settings.xml   # Aliyun Maven 미러
WORKDIR /build

# POM 파일을 먼저 복사 (Docker 레이어 캐싱 활용)
COPY mvnw pom.xml ./
COPY omni-common-core/pom.xml omni-common-core/
COPY omni-common/pom.xml omni-common/
# ... 기타 모듈 POM ...
RUN mvn dependency:go-offline -B -q || true

# 소스 코드를 복사하고 지정 서비스 빌드
COPY . .
ARG SERVICE_NAME
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B -q

# Stage 2: 런타임
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl bash
ARG SERVICE_NAME
COPY --from=build /build/${SERVICE_NAME}/target/*.jar /app/app.jar
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK ...
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**레이어 캐싱 최적화 전략**:

1. **POM 레이어 캐싱**: 모든 `pom.xml`을 복사하고 `dependency:go-offline` 실행 — POM이 변경되지 않으면 후속 빌드에서 캐시 레이어를 재사용하여 의존성 다운로드를 건너뜀
2. **소스 분리**: `COPY . .`는 소스 코드 변경 시에만 리빌드 트리거
3. **단일 서비스 빌드**: `mvn package -pl ${SERVICE_NAME} -am`은 대상 서비스와 그 의존성만 빌드하여 전체 컴파일을 회피

**이미지 선정 고려사항**:

| 선택 | 이유 |
|------|------|
| `maven:3.9-eclipse-temurin-25-alpine` | Alpine 기반은 작음 (~200MB vs ~800MB for Debian), Maven 3.9 + JDK 25 내장 |
| `eclipse-temurin:25-jre-alpine` | 런타임에는 JRE만 필요 (~80MB), 풀 JDK 이미지보다 300MB+ 작음 |

### 4.2 프론트엔드 빌드

```dockerfile
# Stage 1: Node 빌드
FROM node:22-alpine AS build
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                                  # 정확한 설치 (lock 파일 사용)
COPY omni-frontend/ .
RUN npm run build                           # Vite 프로덕션 빌드

# Stage 2: Nginx 런타임
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
```

**레이어 캐싱**: `package*.json`을 복사하고 `npm ci`를 먼저 실행 — 소스 코드 변경 시 의존성 재설치를 방지합니다.

---

## 5. docker-compose.yml 서비스별 설정 해설

### 5.1 MySQL 8.4

```yaml
mysql:
  image: mysql:8.4
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:? .env에 설정하세요}
    MYSQL_DATABASE: omni_auth          # 자동 생성되는 첫 번째 DB
    MYSQL_CHARACTER_SET_SERVER: utf8mb4
    MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    TZ: Asia/Shanghai                  # 타임존
  volumes:
    - omni-mysql-data:/var/lib/mysql
  volumes:
    - ./scripts/sql/init-all.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    - ./scripts/sql/init-nacos.sql:/docker-entrypoint-initdb.d/02-init-nacos.sql:ro
    - ./scripts/sql/init-xxl-job.sql:/docker-entrypoint-initdb.d/03-init-xxl-job.sql:ro
```

**핵심 포인트**:
- 3개의 SQL 스크립트가 순서대로 자동 실행: `01-init.sql`(비즈니스 DB + 초기 데이터) → `02-init-nacos.sql`(Nacos 설정 DB) → `03-init-xxl-job.sql`(XXL-JOB DB)
- `:ro`(읽기 전용)로 마운트하여 우발적 변경 방지
- MySQL은 이름 있는 볼륨 `omni-mysql-data`를 사용하며 일반적인 컨테이너 재생성 시 데이터를 유지

### 5.2 Redis 7.4

```yaml
redis:
  image: redis:7.4
  command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:? .env에 설정하세요}"]
```

### 5.3 Nacos v3.1.1

```yaml
nacos:
  image: nacos/nacos-server:v3.1.1
  environment:
    MODE: standalone                      # 싱글 노드 모드
    SPRING_DATASOURCE_PLATFORM: mysql     # 외부 MySQL 저장소 (내장 Derby 아님)
    MYSQL_SERVICE_HOST: mysql             # MySQL 컨테이너 이름 지정
    NACOS_AUTH_TOKEN: ...                 # JWT 서명 시크릿 (Base64 인코딩)
  ports:
    - "8080:8080"                         # 콘솔
    - "8848:8848"                         # API 포트 (서비스 등록/설정)
    - "9848:9848"                         # gRPC 포트 (롱 커넥션)
```

**3포트 설명**:

| 포트 | 용도 | 사용자 |
|------|------|--------|
| 8080 | Web 콘솔 | 운영자 브라우저 접근 |
| 8848 | HTTP API | 백엔드 마이크로서비스 등록/설정 |
| 9848 | gRPC | Nacos 2.x+ 클라이언트 롱 커넥션 |

### 5.4 RocketMQ 5.3.2 (NameServer + Broker)

```yaml
rocketmq-namesrv:
  ports:
    - "19876:9876"     # 호스트 19876 → 컨테이너 9876 (Windows Hyper-V 포트 충돌 회피)

rocketmq-broker:
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876          # 컨테이너 간 통신
    JAVA_OPT_EXT: "-Drocketmq.broker.diskSpaceWarningLevelRatio=0.98"
  volumes:
    - ./docker/rocketmq/broker-docker.conf:...   # Docker 전용 Broker 설정
  command: sh mqbroker -n rocketmq-namesrv:9876 -c ... --enable-proxy
```

> **포트 매핑 19876:9876 이유**: Windows Hyper-V/WSL2는 9859-9958 포트 범위를 예약합니다. 9876을 직접 매핑하면 충돌이 발생합니다. [섹션 10](#10-windows-hyper-vws12-포트-예약-문제) 참조.

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
      --xxl.job.accessToken=           # 빈 토큰 (개발 환경에서 인증 없음)
```

### 5.6 백엔드 마이크로서비스 (4개 인스턴스)

4개 백엔드 마이크로서비스는 동일한 Dockerfile을 사용하며, `build.args.SERVICE_NAME`으로 구분합니다. 각 서비스의 `environment`에서 핵심 설정을 오버라이드합니다:

| 환경 변수 | 설명 | 예시 값 |
|----------|------|---------|
| `SERVER_PORT` | 컨테이너 내부 포트 (통일 8080) | `8080` |
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Nacos 주소 | `nacos:8848` |
| `SPRING_DATASOURCE_URL` | DB 연결 | `jdbc:mysql://mysql:3306/omni_auth?...` |
| `SPRING_DATA_REDIS_HOST` | Redis 주소 | `redis` |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | 등록 IP (빈 값=자동 감지 컨테이너 IP) | `""` |
| `AUTH_ISSUER` | JWT Issuer (auth만) | `http://omni-auth:8080` |
| `AUTH_JWKS_URI` | JWKS 엔드포인트 (gateway만) | `http://omni-auth:8080/oauth2/jwks` |

### 5.7 프론트엔드

```yaml
omni-frontend:
  build:
    context: .                          # 프로젝트 루트 (docker/ 설정 접근 필요)
    dockerfile: docker/frontend/Dockerfile
  ports:
    - "3000:3000"                       # 유일한 사용자 접근 입구
  depends_on:
    omni-gateway:
      condition: service_healthy        # 게이트웨이 준비 후 기동
```

---

## 6. 환경 변수 오버라이드 메커니즘

Spring Boot는 환경 변수를 통한 `application.yml` 설정 오버라이드를 지원하며, Docker 배포에서 대량으로 사용됩니다.

### 6.1 변환 규칙

| application.yml 설정 | 환경 변수명 | 변환 규칙 |
|---------------------|-----------|-----------|
| `server.port` | `SERVER_PORT` | 대문자 + 점→언더스코어 |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | 대문자 + 점→언더스코어 |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | 대문자 + 점→언더스코어 |
| `spring.cloud.nacos.discovery.server-addr` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | 대문자 + 하이픈→언더스코어 |

### 6.2 우선순위 (높은 → 낮은)

```
1. 명령줄 인수 (--server.port=9090)
2. 환경 변수 (SERVER_PORT=9090)
3. application-{profile}.yml
4. application.yml
5. Nacos 설정 센터 (활성화된 경우)
```

### 6.3 Docker 배포에서의 전형적인 오버라이드

| 설정 | application.yml 값 (로컬 개발) | Docker 환경 변수 값 |
|------|-------------------------------|-------------------|
| DB URL | `localhost:3306` | `mysql:3306` |
| Redis 호스트 | `localhost` | `redis` |
| Nacos 주소 | `localhost:8848` | `nacos:8848` |
| RocketMQ | `localhost:9876` | `rocketmq-namesrv:9876` |
| JWT Issuer | `http://localhost:8100` | `http://omni-auth:8080` |
| OAuth2 콜백 | `http://localhost:8102/api/auth/...` | `http://localhost:8102/api/auth/...` |

> **주의**: OAuth2 콜백 URI는 Gateway 주소 `localhost:8102`를 사용합니다. Auth 등 내부 서비스 포트는 진단용으로 루프백에 바인딩하며 외부에 공개하지 않습니다.

---

## 7. Nginx 리버스 프록시 설정

### 7.1 설정 해설

```nginx
server {
    listen 3000;
    root /usr/share/nginx/html;

    # API 요청을 Gateway로 리버스 프록시
    location /api/ {
        proxy_pass http://omni-gateway:8080;    # ← 컨테이너 내부 포트!
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 엔드포인트 리버스 프록시
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
        # ... 위와 동일
    }

    # OIDC Discovery 엔드포인트 리버스 프록시
    location /.well-known/ {
        proxy_pass http://omni-gateway:8080;
        # ... 위와 동일
    }

    # Vue Router History 모드
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### 7.2 흔한 실수: proxy_pass 포트

| 설정 | 올바름? | 설명 |
|------|---------|------|
| `proxy_pass http://omni-gateway:8080` | ✅ | 컨테이너 내부 포트, Docker 네트워크 내에서 도달 가능 |
| `proxy_pass http://omni-gateway:8102` | ❌ | 8102는 호스트 매핑 포트, 컨테이너 간 접근 불가 |
| `proxy_pass http://localhost:8102` | ❌ | 컨테이너 내 localhost는 자기 자신, Gateway에 도달 불가 |
| `proxy_pass http://host.docker.internal:8102` | ⚠️ | 동작하지만 Docker 네트워크를 우회, 성능 저하 |

---

## 8. 데이터 초기화와 영속화

### 8.1 DB 초기화 체인

MySQL 컨테이너 최초 기동 시 `/docker-entrypoint-initdb.d/`의 SQL 스크립트가 자동 실행됩니다:

```
01-init.sql       → omni_auth / omni_base / omni_workflow DB 생성 + 초기 데이터
02-init-nacos.sql → nacos_config DB 생성 + Nacos 설정 데이터
03-init-xxl-job.sql → xxl_job DB 생성 + XXL-JOB 스케줄링 데이터
```

### 8.2 데이터 영속화 전략

현재 설정은 MySQL을 이름 있는 볼륨 `omni-mysql-data`에 영속화합니다. `docker compose down`은 데이터를 유지하고, `docker compose down -v`는 복구할 수 없게 삭제합니다.

```yaml
mysql:
  volumes:
    - omni-mysql-data:/var/lib/mysql

volumes:
  omni-mysql-data:
```

---

## 9. Docker 레지스트리 미러 설정

### 9.1 Docker 레지스트리 미러

Docker Hub 접근이 느린 지역의 사용자는 미러를 설정하세요:

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

### 9.2 Maven 의존성 가속

백엔드 Dockerfile에 Aliyun Maven 미러가 내장되어 있습니다 (`docker-settings.xml`):

```xml
<mirror>
  <id>aliyun</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

### 9.3 npm 의존성 가속

프론트엔드 npm 설치를 가속하려면 Dockerfile에 미러 레지스트리를 추가하세요:

```dockerfile
RUN npm config set registry https://registry.npmmirror.com
RUN npm ci
```

---

## 10. Windows Hyper-V/WSL2 포트 예약 문제

### 10.1 문제 설명

Windows 10/11의 Hyper-V 또는 WSL2는 TCP 포트 범위를 동적으로 예약합니다 (예: 9859-9958). 예약된 범위에 Docker가 매핑해야 하는 포트(예: 9876)가 포함되면 컨테이너 기동이 실패합니다:

```
Error starting userland proxy: listen tcp4 0.0.0.0:9876: bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

### 10.2 해결책

**해결책 A: 포트 오프셋 (채택됨)**

RocketMQ NameServer 호스트 포트를 9876에서 19876으로 변경:

```yaml
ports:
  - "19876:9876"    # 호스트 19876 → 컨테이너 9876
```

컨테이너 간 통신에는 영향 없음 (여전히 `rocketmq-namesrv:9876` 사용). 호스트 접근만 19876 사용.

**해결책 B: 포트 예약 보호 (start.bat에서 구현됨)**

Docker 기동 전 관리자 권한으로 필요한 포트를 예약:

```batch
:: winnat 중지 → 포트 예약 → winnat 재시작
net stop winnat
netsh int ipv4 add excludedportrange protocol=tcp startport=9876 numberofports=1 persistent=yes
net start winnat
```

**해결책 C: WSL 재시작 (임시)**

```powershell
wsl --shutdown
# 그 후 Docker Desktop 재시작
```

### 10.3 예약 포트 확인

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

---

## 11. Nacos v3.1.1 헬스체크 엔드포인트 변경

### 11.1 변경 내용

Nacos v3.x부터 `/nacos/actuator/health` 엔드포인트가 제거되었습니다. 헬스체크는 다음을 사용합니다:

| Nacos 버전 | 헬스체크 엔드포인트 | 메서드 |
|-----------|-------------------|--------|
| v2.x | `/nacos/actuator/health` | GET |
| v3.0+ | `/nacos/` | GET |

### 11.2 docker-compose.yml 설정

```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/"]
  start_period: 60s     # Nacos는 기동이 느려 40-60초 필요
```

> **주의**: `curl -sf` — `-s`는 사일런트 모드, `-f`는 HTTP 오류 시 비정상 종료 코드를 반환합니다. 헬스체크의 불필요한 출력을 방지합니다.

---

## 12. RocketMQ Broker Docker 네트워크 설정

### 12.1 배경

RocketMQ Broker는 기본적으로 자체 IP를 NameServer에 등록합니다. Docker 환경에서 Broker가 `127.0.0.1`이나 Docker 브리지 IP를 획득하면 다른 컨테이너에서 연결할 수 없습니다.

### 12.2 해결책

사용자 정의 설정 파일 `docker/rocketmq/broker-docker.conf`에서 `brokerIP1`을 명시적으로 설정:

```properties
brokerIP1 = rocketmq-broker    # 컨테이너 이름으로 설정, Docker DNS로 해결 가능
```

### 12.3 백엔드 연결 설정

백엔드 마이크로서비스는 환경 변수로 RocketMQ에 연결:

```yaml
SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER: rocketmq-namesrv:9876
```

NameServer의 컨테이너 이름과 내부 포트를 사용하며, 호스트 매핑 포트 19876이 아님에 주의하세요.

---

## 13. 스케일링 가이드

### 13.1 백엔드 서비스 수평 스케일링

```bash
# omni-base 3개 인스턴스 기동
docker compose up -d --scale omni-base=3

# 인스턴스 상태 확인
docker compose ps
```

**주의사항**:
- 다중 인스턴스 시 Docker가 자동으로 다른 호스트 포트를 할당
- Nacos 서비스 디스커버리가 모든 인스턴스를 자동 등록
- Spring Cloud Gateway가 Nacos를 통해 각 인스턴스로 로드밸런싱
- 고정 포트가 필요한 경우 수동 지정 필요

### 13.2 DB 연결 풀 고려사항

수평 스케일링 시 각 인스턴스는 독립적인 연결 풀을 유지합니다. HikariCP `maximumPoolSize=20`, 3개 인스턴스 = 60개 DB 연결. MySQL의 `max_connections`가 충분한지 확인:

```sql
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 200;
```

### 13.3 유상태 서비스 주의사항

- **XXL-JOB**: 수평 스케일링 미지원 (싱글 Admin 모드), 스케줄링 데이터는 MySQL에 저장
- **RocketMQ Broker**: 프로덕션에서는 멀티 Broker 클러스터 권장, 현재는 싱글 Broker 개발 모드
- **Nacos**: 프로덕션에서는 3노드 클러스터 권장, 현재는 스탠드얼론 싱글 노드 모드

---

## 14. 운영 매뉴얼

### 14.1 자주 사용하는 명령어

```bash
# 모든 컨테이너 상태 확인
docker compose ps

# 서비스 로그 실시간 추적
docker compose logs -f omni-auth

# 단일 서비스 재시작
docker compose restart omni-gateway

# 서비스 재빌드 후 재시작
docker compose up -d --build omni-base

# 모든 컨테이너 중지 (이미지 유지)
docker compose down

# 중지 후 모든 데이터 삭제 (완전 리셋)
docker compose down -v

# 컨테이너 리소스 사용량 확인
docker stats --no-stream
```

### 14.2 로그 조사

```bash
# 최신 100줄 로그 확인
docker compose logs --tail=100 omni-gateway

# 특정 시간대 로그 확인
docker compose logs --since="2025-01-01T10:00:00" omni-auth

# 로그를 파일로 내보내기
docker compose logs omni-base > base-logs.txt
```

### 14.3 컨테이너 내부 디버깅

```bash
# 백엔드 서비스 컨테이너 진입
docker exec -it omni-auth sh

# 컨테이너 내부 프로세스 확인
docker exec -it omni-auth ps aux

# 컨테이너 간 네트워크 연결성 테스트
docker exec -it omni-auth curl -s http://nacos:8848/nacos/
```

---

## 15. 트러블슈팅 가이드

### 15.1 502 Bad Gateway

**증상**: 브라우저에서 `http://localhost:3000` 접근 시 502 반환.

**진단 단계**:
```bash
# 1. Nginx 컨테이너 실행 확인
docker compose ps omni-frontend

# 2. Gateway 컨테이너 실행 확인
docker compose ps omni-gateway

# 3. Nginx 에러 로그 확인
docker compose logs omni-frontend

# 4. 컨테이너 간 연결성 테스트
docker exec -it omni-frontend curl -s http://omni-gateway:8080/actuator/health
```

**흔한 원인**:
- Nginx `proxy_pass`가 호스트 포트(8102)를 사용 (컨테이너 내부 포트 8080이 맞음)
- Gateway 컨테이너가 아직 헬스체크를 통과하지 못함
- Nacos가 준비되기 전에 Gateway가 기동됨

### 15.2 이미지 풀 실패

**증상**: `docker compose pull` 타임아웃 또는 `pull access denied` 오류.

**해결책**:
1. Docker 레지스트리 미러 설정 ([섹션 9](#9-docker-레지스트리-미러-설정) 참조)
2. 수동 풀 확인: `docker pull xuxueli/xxl-job-admin:3.3.1`
3. 디스크 공간 확인: `docker system df`

### 15.3 빌드 실패

**증상**: `docker compose build` 실패.

**Maven 의존성 다운로드 타임아웃**:
- Aliyun 미러가 내장되어 있음 (`docker-settings.xml`). 여전히 실패하면 네트워크 확인
- Docker 빌드 캐시 정리: `docker builder prune -a`

**npm install 타임아웃**:
- Dockerfile에 미러 레지스트리 추가 ([섹션 9.3](#93-npm-의존성-가속) 참조)

**디스크 공간 부족**:
```bash
docker system prune -a    # 미사용 이미지/컨테이너/네트워크 모두 정리
```

### 15.4 포트 충돌

**증상**: `bind: address already in use`.

**진단**:
```bash
# Windows
netstat -ano | findstr :8080
# Linux/Mac
lsof -i :8080
```

**해결책**:
- 포트를 점유 중인 프로세스 중지
- docker-compose.yml의 호스트 포트 매핑 변경
- Windows 사용자: Hyper-V 포트 예약 확인 ([섹션 10](#10-windows-hyper-vws12-포트-예약-문제) 참조)

### 15.5 Nacos 등록 실패

**증상**: 백엔드 로그에 `NacosException: failed to req API:/nacos/v1/ns/instance` 보고됨.

**진단**:
```bash
# Nacos 정상 여부 확인
docker compose ps nacos
docker exec -it omni-nacos curl -s http://localhost:8848/nacos/

# 백엔드 서비스 Nacos 설정 확인
docker compose exec omni-auth env | grep NACOS
```

**흔한 원인**:
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`이 `nacos:8848`로 설정되지 않음
- `SPRING_CLOUD_NACOS_DISCOVERY_IP`이 비어있지 않음 (자동 감지를 위해 `""`로 설정해야 함)
- 백엔드 서비스 기동 시 Nacos가 아직 헬스체크를 통과하지 못함 (depends_on 설정 문제)

### 15.6 RocketMQ 연결 실패

**증상**: 백엔드 로그에 `org.apache.rocketmq.remoting.exception.RemotingConnectException` 보고됨.

**진단**:
```bash
# Broker가 NameServer에 등록되어 있는지 확인
docker exec -it omni-rocketmq-namesrv sh mqadmin clusterList -n localhost:9876

# brokerIP1 설정 확인
docker exec -it omni-rocketmq-broker cat /home/rocketmq/rocketmq-5.3.2/conf/broker.conf
```

**흔한 원인**:
- `brokerIP1`이 컨테이너 이름 `rocketmq-broker`로 설정되지 않아 다른 컨테이너가 연결 불가
- 백엔드 서비스가 컨테이너 포트 9876 대신 호스트 포트 19876을 사용

### 15.7 컨테이너 기동 순서 이상

**증상**: 백엔드 서비스 기동이 실패했지만 `docker compose ps`에서는 컨테이너가 실행 중으로 표시됨.

**진단**:
```bash
# 서비스 기동 순서 확인
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

# 서비스 기동 타임스탬프 확인
docker compose logs --timestamps omni-auth | head -5
```

---

## 16. 설정 레퍼런스

### 16.1 서비스 포트 매핑 총람

| 서비스 | 내부 포트 | 호스트 매핑 포트 | 프로토콜 | 설명 |
|--------|----------|-----------------|---------|------|
| omni-frontend | 3000 | 3000 | HTTP | 사용자 접근 입구 |
| omni-auth | 8080 | 8100 | HTTP | 인증 서비스 |
| omni-base | 8080 | 8101 | HTTP | 기초 데이터 서비스 |
| omni-gateway | 8080 | 8102 | HTTP | API 게이트웨이 |
| omni-workflow | 8080 | 8103 | HTTP | 워크플로우 서비스 |
| omni-crm | 8080 | 8104 | HTTP | CRM 서비스 |
| omni-srm | 8080 | 8105 | HTTP | SRM 서비스 |
| omni-procurement | 8080 | 8106 | HTTP | 조달 서비스 |
| omni-asset | 8080 | 8107 | HTTP | 자산 서비스 |
| MySQL | 3306 | 13306 | TCP | 데이터베이스 |
| Redis | 6379 | 6379 | TCP | 캐시 |
| Nacos Console | 8080 | 8080 | HTTP | 콘솔 |
| Nacos API | 8848 | 8848 | HTTP | 서비스 등록/설정 |
| Nacos gRPC | 9848 | 9848 | gRPC | 롱 커넥션 |
| RocketMQ NameServer | 9876 | **19876** | TCP | 네임 서비스 |
| RocketMQ Broker | 10909-10912 | 10909-10912 | TCP | 메시지 브로커 |
| XXL-JOB Admin | 8080 | 18080 | HTTP | 잡 스케줄러 |

### 16.2 인증 정보와 공개 범위

운영 자격 증명은 하드코딩하지 않습니다. 시작 전에 `.env`에서 MySQL, Redis, Nacos, XXL-JOB, OAuth state, JWK 암호화, 내부 API 및 애플리케이션 DB 비밀값을 설정해야 합니다. 초기 애플리케이션 계정은 로컬 데모 전용이며 공유 배포 전에 변경하거나 제거해야 합니다. 공개 진입점은 프런트엔드(`3000`)와 Gateway(`8102`)뿐이며 다른 포트는 `127.0.0.1`에 바인딩됩니다.

### 16.3 주요 파일 경로

| 파일 | 경로 | 설명 |
|------|------|------|
| docker-compose.yml | `docker-compose.yml` | 컨테이너 오케스트레이션 설정 |
| 백엔드 Dockerfile | `docker/backend/Dockerfile` | 마이크로서비스 멀티스테이지 빌드 |
| 프론트엔드 Dockerfile | `docker/frontend/Dockerfile` | Vue 프론트엔드 멀티스테이지 빌드 |
| Nginx 설정 | `docker/frontend/nginx.conf` | 프론트엔드 리버스 프록시 규칙 |
| Broker 설정 | `docker/rocketmq/broker-docker.conf` | RocketMQ Docker 네트워크 설정 |
| Maven 미러 | `omni-backend/docker-settings.xml` | Aliyun Maven 가속 |
| DB 초기화 | `scripts/sql/init-all.sql` | 비즈니스 스키마 및 초기 데이터 |
| Nacos 초기화 | `scripts/sql/init-nacos.sql` | Nacos 설정 데이터 |
| XXL-JOB 초기화 | `scripts/sql/init-xxl-job.sql` | 스케줄링 태스크 데이터 |
| 기동 스크립트 (Linux) | `start.sh` | 원클릭 기동 |
| 기동 스크립트 (Windows) | `start.bat` | 원클릭 기동 (포트 보호 포함) |
| 정지 스크립트 (Linux) | `stop.sh` | 원클릭 정지 |
| 정지 스크립트 (Windows) | `stop.bat` | 원클릭 정지 |

---

## 부록: 빌드 산출물 크기 참고

| 이미지 | 크기 (약) | 설명 |
|--------|----------|------|
| omni-auth:latest | ~200MB | JRE + Fat JAR |
| omni-base:latest | ~200MB | JRE + Fat JAR |
| omni-gateway:latest | ~200MB | JRE + Fat JAR |
| omni-workflow:latest | ~250MB | JRE + Fat JAR + Flowable 엔진 |
| omni-frontend:latest | ~50MB | Nginx + Vue 정적 파일 |
| mysql:8.4 | ~600MB | 공식 이미지 |
| nacos/nacos-server:v3.1.1 | ~800MB | 공식 이미지 |
| apache/rocketmq:5.3.2 | ~700MB | 공식 이미지 |
