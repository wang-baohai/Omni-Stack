# Omni-Stack

> Spring Boot 4 + Vue 3 기반의 마이크로서비스 스캐폴딩 플랫폼. Harness 산업 디자인 패턴을 채택하여 AI 지원 개발을 위한 업계 모범 사례 기반을 제공합니다.

**[中文](README.md)** | **[English](README.en.md)** | **[日本語](README.jp.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**연락처**: wangbaohai1993@gmail.com

---

## 주요 기능

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 풀 최신 기술 스택
- **Spring Cloud Gateway 5.x** (WebFlux) 리액티브 게이트웨이, Nacos 서비스 디스커버리 및 구성 센터
- **Sentinel** 트래픽 제어 및 서킷 브레이커, **OpenFeign** 선언적 서비스 호출
- **멀티 프로바이더 소셜 로그인**: GitHub + Google + Gitee OAuth2 원클릭 로그인 (전략 패턴 `OAuth2ProviderHandler` 확장 가능), 프론트엔드에 WeChat 로그인入口 예약, HMAC-SHA256 state 서명으로 위변조 방지, 첫 로그인 시 자동 회원가입
- **Vue 3.5** + TypeScript 5.9 + Vite 8 + Element Plus 2.14 모던 프론트엔드
- **Pinia 3** 상태 관리 + **Vue Router 5** 내비게이션 가드
- **Harness 산업 디자인 패턴**: 3계층 높이 모델 (Architecture → Patterns → Code), `docs/` 디렉터리에 시스템 진실 보관
- **AI 네이티브 엔지니어링**: AGENTS.md 실행 매뉴얼 + Skills 행동 확장, AI 지원 개발 워크플로 지원
- **3가지 사용자 생성 경로**: 셀프 등록 (CAPTCHA + 기본 역할), 관리자 백엔드 생성, 소셜 로그인 첫 자동 등록
- **3계층 XSS 종심 방어**: Jackson 역직렬화기가 `@RequestBody` 자동 세척 + Servlet Filter가 쿼리 파라미터 세척 + Gateway 보안 응답 헤더, 테넌트별 글로벌 토글 및 커스텀 블랙리스트 규칙 (HTML 태그, 이벤트 핸들러, 위험 프로토콜, 정규식 패턴) 지원, Redis 캐시 + DB 구성, 완전한 프론트엔드 관리 UI
- **Common Starter 생태계**: `omni-common`을 7개 모듈 (core / common / mybatis / redis / redis-reactive / operlog / job)로 분할, 신규 서비스는 Maven 의존성 추가로 MyBatis-Plus 페이지네이션, Redis 캐시, XSS 방어, 작업 로그 수집, 스케줄링 작업 관리 등의 기능을 획득, `AutoConfiguration.imports` 제로 구성 자동 어셈블리
- **기초 데이터 및 작업 관리**: `omni-base` 서비스 (포트 8101) 데이터 사전 관리, 시스템 작업 관리, 사용자 작업 관리, 작업 로그 열람 제공, Redis cache-aside 캐시, 완전한 프론트엔드 관리 페이지
- **작업 로그 감사 추적**: `@OperLog` 어노테이션 + AOP 애스펙트 비침습적 수집, who/when/what/changed 완전한 감사 정보 자동 기록, 엔티티 변경 스냅샷 자동 diff (oldValue vs newValue) 데이터 추적 지원, RocketMQ 비동기 전송으로 비즈니스 요청 차단 없음, 핫/콜드 테이블 분리 아카이브 전략 (180일 보존 + 콜드 테이블 장기 보관)으로 쿼리 성능과 컴플라이언스 요구사항 동시 충족, 감사 로그 (`sys_audit_log`) 및 로그인 로그 (`sys_login_log`)와 보완하여 완전한 감사 추적 체계 구성
- **이중 트랙 스케줄링 작업**: XXL-JOB 3.3.1 기반 시스템 작업 (`@XxlJob` + `@SystemJobMeta` 이중 어노테이션, 스케줄링 센터에 자동 등록)과 사용자 작업 (SPI 모드, `UserJobHandler` 인터페이스 + JSON 파라미터 라우팅) 두 가지 모드 구현, 프론트엔드 Cron 편집기, 동적 파라미터 폼, 실행 로그 실시간 푸시 지원
- **시각적 BPMN 워크플로 엔진**: Flowable 7.x 기반, `omni-workflow` 독립 마이크로서비스 (포트 8103), 프론트엔드 BPMN 시각적 디자이너로 드래그 앤 드롭 모델링, 이중 버전 관리 (비즈니스 버전 DRAFT → PUBLISHED → ARCHIVED + Flowable 엔진 버전), 멀티 인스턴스 회서는 ALL/ANY 승인 모드 지원, 동적 후보자 해결 (`omni:assignment` JSON 확장 + `ScopedRoleAssignmentListener` 런타임 해결), 승인 기록 + 프로세스 진척도 + CC 알림 완전 사용 가능
- **Maven Wrapper** 내장 — 클론 후 바로 빌드 가능, 시스템 Maven 설치 불필요

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| JDK | OpenJDK | 25 |
| 백엔드 프레임워크 | Spring Boot | 4.0.6 |
| 마이크로서비스 프레임워크 | Spring Cloud | 2025.1.1 |
| 마이크로서비스 프레임워크 | Spring Cloud Alibaba | 2025.1.0.0 |
| API 게이트웨이 | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| 등록/구성 | Nacos Server | v3.1.1 |
| 트래픽 제어/서킷 브레이커 | Sentinel Dashboard | 1.8.8 |
| 메시지 큐 | Apache RocketMQ | 5.3.2 |
| 작업 스케줄링 | XXL-JOB Admin | 3.3.1 |
| 워크플로 엔진 | Flowable BPMN | 7.x |
| 프론트엔드 프레임워크 | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| 빌드 도구 | Vite 8 (Rolldown) | 8.0.14 |
| UI 프레임워크 | Element Plus | 2.14.0 |
| 상태 관리 | Pinia | 3.0.4 |
| 라우터 | Vue Router | 5.0.7 |
| Node.js | Node.js LTS | >= 22.12.0 |

## 프로젝트 구조

```
Omni-Stack/
├── AGENTS.md                        # AI 실행 매뉴얼 (하드 제약 + 빌드 명령 + 체크리스트)
├── start.bat / start.sh              # 원클릭 시작 스크립트 (Docker 자동 시작 + 포트 보호 + 컨테이너)
├── stop.bat / stop.sh                # 원클릭 중지 스크립트
├── docker-compose.yml               # 미들웨어 오케스트레이션 (MySQL, Redis, Nacos, RocketMQ, XXL-JOB)
├── docker/
│   └── rocketmq/broker.conf          # RocketMQ Broker 구성 파일
├── docs/                            # 시스템 진실 문서 (Architecture + Patterns + Contract)
│   ├── architecture.md                # 시스템 경계, 모듈 맵, 데이터 흐름, RBAC 권한 체계
│   ├── api-contract.md                # 응답 형식, 오류 코드, 페이지네이션, 네이밍 규칙
│   ├── backend-patterns.md            # 백엔드 계층화, 유효성 검사, 예외, 로깅, 보안 권한, OOP 규칙
│   ├── frontend-patterns.md           # 프론트엔드 디렉토리, API 계층, 상태 관리, 권한 제어, 컴포넌트 규칙
│   └── core-flows.md                  # 로그인/OAuth2/RBAC 권한 흐름 엔드투엔드 추적
├── scripts/
│   └── sql/
│       ├── init-all.sql               # 공식 DB 초기화 스크립트 (DDL + 시드 데이터)
│       ├── init-nacos.sql           # Nacos v3.1.1 MySQL 영속화 초기화 스크립트
│       └── init-xxl-job.sql          # XXL-JOB v3.3.1 데이터베이스 초기화 스크립트
├── omni-backend/                    # Maven 멀티 모듈 백엔드
│   ├── mvnw / mvnw.cmd                # Maven Wrapper (3.9.16)
│   ├── pom.xml                        # 부모 POM (의존성 관리)
│   ├── omni-common-core/              # 순수 POJO: R<T>, PageResult, BaseEntity, XSS SPI
│   ├── omni-common/                   # Web 자동 구성: Jackson, CORS, 전역 예외, XSS Filter
│   ├── omni-common-mybatis/           # MyBatis-Plus Starter: 페이지네이션 플러그인, MySQL 드라이버
│   ├── omni-common-redis/             # 블로킹 Redis Starter: RedisTemplate, RedisUtils
│   ├── omni-common-redis-reactive/    # 리액티브 Redis Starter: WebFlux 서비스 전용
│   ├── omni-common-operlog/             # 작업 로그 Starter: AOP 애스펙트 + MQ 프로듀서 + 엔티티 diff
│   ├── omni-common-job/                 # 스케줄링 작업 Starter: XXL-JOB 자동 구성 + Admin Client + 시스템 작업 등록
│   ├── omni-common-workflow/            # 워크플로 Starter: Flowable 자동 구성 + 승인 SPI + 테넌트 필터
│   ├── omni-auth/                     # 인증 서비스: 로그인, CAPTCHA, JWT, OAuth2 (포트 8100)
│   ├── omni-base/                     # 기초 데이터 서비스: 데이터 사전 관리 (포트 8101)
│   ├── omni-workflow/                   # 워크플로 엔진 서비스: Flowable BPMN (포트 8103)
│   └── omni-gateway/                  # API 게이트웨이 (WebFlux, 포트 8102)
├── omni-frontend/                   # Vue 3 SPA (개발 서버 포트 3000)
│   ├── package.json
│   ├── vite.config.ts
│   ├── eslint.config.mjs
│   └── src/
│       ├── api/                       # API 계층 (도메인별 파일 분리)
│       ├── stores/                    # Pinia Store (Composition API 스타일)
│       ├── router/                    # 라우트 정의 + 내비게이션 가드
│       ├── views/                     # 페이지 컴포넌트
│       ├── layout/                    # 앱 레이아웃 (사이드바 + 상단바 + 콘텐츠 영역)
│       ├── types/                     # 공유 타입 정의 (ApiResponse, PageResult)
│       └── styles/                    # 전역 스타일
└── .qoder/
    └── skills/
        └── grill-me/SKILL.md          # AI Skill: 설계 스트레스 테스트
```

## 아키텍처 개요

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100  │
                                 │  Security+OAuth2│
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   :3000         │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101  │
                            │                    │  데이터 사전 관리 │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  영속화 스토리지
                    │  Redis :6379   │  캐시 + CAPTCHA + 사전 캐시
                    │  Nacos :8848   │  서비스 디스커버리 + 구성 센터
                    │  Sentinel :8858│  트래픽 제어 + 서킷 브레이커
                    │  RocketMQ :9876│  메시지 큐 (작업 로그 비동기 전송)
                    │  XXL-JOB :18080│  분산 작업 스케줄링 센터
                    └────────────────┘
```

**요청 흐름**:

```
브라우저 :3000  --/api/**-->  Vite 프록시  -->  Gateway :8102  --lb://-->  백엔드 서비스
```

- 프론트엔드는 Vite 개발 서버를 통해 `/api/**` 요청을 Gateway로 프록시
- Gateway는 Nacos 서비스 디스커버리를 통해 등록된 서비스의 라우트를 자동 생성

## 환경 준비

### 필수 소프트웨어

| 소프트웨어 | 버전 요구사항 | 설명 |
|-----------|-------------|------|
| JDK | 25 | `JAVA_HOME` 환경 변수 설정 필수 |
| Node.js | >= 22.12.0 | npm 포함 |
| Docker Desktop | 안정 버전 | 미들웨어 (MySQL, Redis, Nacos, Sentinel, RocketMQ, XXL-JOB) 실행용 |

> **참고**: Maven Wrapper (3.9.16)가 내장되어 있습니다. 모든 Maven 명령은 `./mvnw`로 실행하세요.

### 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `JAVA_HOME` | - | **필수** — JDK 25 설치 경로 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 서버 주소 |
| `NACOS_NAMESPACE` | (비어 있음) | Nacos 네임스페이스 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel Dashboard 주소 |
| `ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` | RocketMQ NameServer 주소 |
| `XXL_JOB_ADMIN_ADDRESSES` | `http://127.0.0.1:18080/xxl-job-admin` | XXL-JOB Admin 주소 |
| `VITE_API_BASE_URL` | `/api` | 프론트엔드 API 기본 경로 |
| `GITHUB_CLIENT_ID` | (내장) | GitHub OAuth App의 Client ID |
| `GITHUB_CLIENT_SECRET` | (내장) | GitHub OAuth App의 Client Secret |
| `GITHUB_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/github/callback` | GitHub 인증 콜백 URL |
| `GITEE_CLIENT_ID` | (내장) | Gitee OAuth App의 Client ID |
| `GITEE_CLIENT_SECRET` | (내장) | Gitee OAuth App의 Client Secret |
| `GITEE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/gitee/callback` | Gitee 인증 콜백 URL |
| `GOOGLE_CLIENT_ID` | (내장) | Google Cloud Console OAuth 2.0 클라이언트의 Client ID |
| `GOOGLE_CLIENT_SECRET` | (내장) | Google Cloud Console OAuth 2.0 클라이언트의 Client Secret |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8100/api/auth/oauth2/google/callback` | Google 인증 콜백 URL |
| `OAUTH2_STATE_SECRET` | (내장) | OAuth2 state 파라미터의 HMAC-SHA256 서명 키, 모든 소셜 로그인 제공자 공유 |

### 소셜 로그인 구성 (GitHub / Google / Gitee)

시스템은 `OAuth2ProviderHandler` 전략 패턴을 채택하여, 각 제공자가 인터페이스를 구현하기만 하면 연동됩니다. 새로운 제공자 추가에 핵심 로직 변경이 필요 없습니다.

#### 1. OAuth App 생성

**GitHub**:

1. GitHub 로그인 → Settings → Developer settings → [OAuth Apps](https://github.com/settings/developers) → New OAuth App
2. 다음 정보 입력:
   - **Application name**: Omni-Stack (임의의 이름)
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:8100/api/auth/oauth2/github/callback`
3. 생성 후 **Client ID**와 **Client Secret** 복사

**Google**:

1. [Google Cloud Console](https://console.cloud.google.com/) 로그인 → APIs & Services → Credentials
2. OAuth 2.0 Client ID 생성 (애플리케이션 유형: Web application 선택)
3. Authorized redirect URIs에 다음 추가: `http://localhost:8100/api/auth/oauth2/google/callback`
4. 생성 후 **Client ID**와 **Client Secret** 복사

**Gitee**:

1. Gitee 로그인 → 설정 → [서드파티 애플리케이션](https://gitee.com/oauth/applications) → 애플리케이션 생성
2. 다음 정보 입력:
   - **애플리케이션 이름**: Omni-Stack (임의의 이름)
   - **애플리케이션 홈페이지**: `http://localhost:3000`
   - **애플리케이션 콜백 URL**: `http://localhost:8100/api/auth/oauth2/gitee/callback`
3. 생성 후 **Client ID**와 **Client Secret** 복사

#### 2. 인증 정보 구성

환경 변수를 설정하거나 `omni-auth/src/main/resources/application.yml`을 수정:

```yaml
auth:
  oauth2:
    github:
      client-id: ${GITHUB_CLIENT_ID:your-client-id}
      client-secret: ${GITHUB_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GITHUB_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/github/callback}
    google:
      client-id: ${GOOGLE_CLIENT_ID:your-client-id}
      client-secret: ${GOOGLE_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GOOGLE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/google/callback}
    gitee:
      client-id: ${GITEE_CLIENT_ID:your-client-id}
      client-secret: ${GITEE_CLIENT_SECRET:your-client-secret}
      redirect-uri: ${GITEE_REDIRECT_URI:http://localhost:8100/api/auth/oauth2/gitee/callback}
    state-secret: ${OAUTH2_STATE_SECRET:your-state-secret}
```

> **참고**: `redirect_uri`는 해당 OAuth App에 설정한 콜백 URL과 정확히 일치해야 합니다. `state-secret`은 state 파라미터의 HMAC-SHA256 서명에 사용되므로 임의의 문자열을 설정하세요.

#### 3. 사용 방법

프론트엔드 로그인 페이지에서 "GitHub", "Google" 또는 "Gitee" 버튼을 클릭하면 소셜 로그인이 시작됩니다. 첫 로그인 시 로컬 사용자가 자동 생성됩니다 (사용자명 형식: GitHub는 `gh_{login}`, Google은 `go_{email_prefix}`, Gitee는 `ge_{login}`).

## 빠른 시작

### 1단계: 미들웨어 시작

프로젝트는 원클릭 시작 스크립트를 제공하여 Docker Desktop 시작, 포트 보호, 컨테이너 배포를 자동으로 완료합니다:

| 플랫폼 | 시작 | 중지 |
|--------|------|------|
| Windows | `start.bat` 우클릭 → 관리자 권한으로 실행 | `stop.bat` 우클릭 → 관리자 권한으로 실행 |
| Linux / macOS | `./start.sh` | `./stop.sh` |

**시작 스크립트 자동 처리**:

1. **Docker Desktop 감지** — 미설치 시 다운로드 안내 및 다운로드 페이지 자동 오픈
2. **Docker 엔진 시작** — 미실행 시 자동 시작, 준비 완료까지 대기
3. **포트 보호** (Windows) — Hyper-V/WSL2가 프로젝트 포트를 동적으로 점유하는 것을 방지 (3306, 6379, 8080, 8848, 9848, 9876, 10909, 10911, 10912, 18080)
4. **컨테이너 시작** — `docker compose up -d` 실행

```bash
# 모든 미들웨어 시작
./start.sh                          # Linux / macOS
# 또는 Windows: start.bat 우클릭 → 관리자 권한으로 실행

# 지정 서비스만 시작
./start.sh mysql redis

# 서비스 상태 확인
docker compose ps
```

> 백엔드 서비스를 시작하기 전에 Nacos가 완전히 시작될 때까지 약 30초 대기하세요. `http://127.0.0.1:8080/`에 접속하여 Nacos 준비 상태를 확인하세요 (기본 인증 정보: nacos/nacos).
> MySQL 컨테이너는 첫 시작 시 `scripts/sql/init-all.sql`을 자동 실행하여 데이터베이스를 초기화합니다.

### 2단계: 백엔드 빌드 및 시작

```bash
# JAVA_HOME 설정 (Spring Boot 4 빌드 플러그인은 JDK 17+ 필요)
export JAVA_HOME="C:/APP/JDK25/jdk-25.0.2"   # Windows
# export JAVA_HOME="/path/to/jdk-25"           # macOS / Linux
export PATH="$JAVA_HOME/bin:$PATH"

# 백엔드 디렉토리로 이동, 모든 모듈 빌드
cd omni-backend
./mvnw clean install

# Auth 서비스 시작 (포트 8100)
cd omni-auth
./mvnw spring-boot:run

# Base 서비스 시작 (포트 8101, 새 터미널 창에서)
cd omni-base
./mvnw spring-boot:run

# Gateway 시작 (포트 8102, 새 터미널 창에서)
cd omni-gateway
./mvnw spring-boot:run
```

**빌드 순서 설명**: `omni-common-core`를 먼저 설치하고, 그 다음 `omni-common`, `omni-common-mybatis`, `omni-common-redis`, `omni-common-redis-reactive`, 마지막으로 `omni-auth`, `omni-base`, `omni-gateway`를 컴파일합니다. Maven reactor가 `<modules>` 선언 순서에 따라 자동으로 해결합니다.

### 3단계: 프론트엔드 시작

```bash
cd omni-frontend

# 의존성 설치
npm install

# 개발 서버 시작 (포트 3000, /api를 Gateway :8102로 자동 프록시)
npm run dev
```

### 4단계: 서비스 확인

| 확인 항목 | 명령 / URL | 예상 결과 |
|----------|-----------|---------|
| 프론트엔드 페이지 | `http://localhost:3000` | 로그인 페이지 |
| Gateway 라우트 | `curl http://localhost:8102/actuator/gateway/routes` | 라우트 목록 JSON 반환 |
| Nacos 콘솔 | `http://127.0.0.1:8080/` | Nacos 관리 화면 |
| Sentinel 콘솔 | `http://localhost:8858` | Sentinel Dashboard |
| XXL-JOB Admin | `http://localhost:18080/xxl-job-admin` | XXL-JOB Admin Web UI (admin/123456) |
| RocketMQ | `telnet localhost 9876` | NameServer 연결 확인 |

**시작 순서**: MySQL → Redis → Nacos → Sentinel → RocketMQ → XXL-JOB → 백엔드 서비스 (Auth, Base, Gateway) → 프론트엔드

## 서비스 포트

| 서비스 | 포트 | 설명 |
|--------|------|------|
| 프론트엔드 개발 서버 | 3000 | Vite dev server, /api 요청 프록시 |
| 인증 서비스 | 8100 | Spring Security + OAuth2 Authorization Server |
| 기초 데이터 서비스 | 8101 | 데이터 사전 관리, Redis cache-aside 캐시 |
| API 게이트웨이 | 8102 | Spring Cloud Gateway (WebFlux) |
| 워크플로 엔진 서비스 | 8103 | Flowable BPMN 프로세스 엔진 |
| MySQL | 3306 | 메인 DB (omni_auth + omni_base + xxl_job) |
| Redis | 6379 | CAPTCHA 캐시 + 사전 캐시 + XSS 구성 캐시 |
| Nacos | 8080, 8848 | 관리 화면 (8080) + 서비스 디스커버리 및 구성 센터 (8848) |
| Sentinel | 8858 | 트래픽 제어 대시보드 |
| XXL-JOB Admin | 18080 | 분산 작업 스케줄링 센터 (Web UI), 기본 계정 admin/123456 |
| RocketMQ NameServer | 9876 | 메시지 큐 네이밍 서버 |
| RocketMQ Broker | 10909, 10911, 10912 | 메시지 큐 브로커 노드 |

## 모듈 설명

### Common Starter 생태계 (7개 모듈)

`omni-common`은 7개의 단일 책임 모듈로 분할되어 Common Starter 생태계를 형성합니다. 신규 마이크로서비스는 의존성 추가만으로 사용 가능하며, **모두 독립 실행 불가**합니다:

| 모듈 | 책임 | 대상 서비스 유형 |
|------|------|----------------|
| `omni-common-core` | 순수 POJO: `R<T>`, `PageResult<T>`, `BaseEntity`, `BusinessException`, `XssConfigProvider` SPI, `UserJobHandler` SPI | 모든 서비스 |
| `omni-common` | Web 자동 구성: Jackson 시간 직렬화, CORS, 전역 예외 처리, XSS Filter + Jackson Module 자동 등록 | Servlet 서비스 |
| `omni-common-mybatis` | MyBatis-Plus + MySQL 드라이버 + 페이지네이션 플러그인 + YAML 기본 구성, `@ConditionalOnMissingBean` 재정의 지원 | Servlet 서비스 |
| `omni-common-redis` | 블로킹 Redis + RedisTemplate 직렬화 + RedisUtils | Servlet 서비스 |
| `omni-common-redis-reactive` | 리액티브 Redis + ReactiveRedisTemplate + ReactiveRedisUtils | WebFlux 서비스 (Gateway) |
| `omni-common-operlog` | 작업 로그 Starter: `@OperLog` AOP 애스펙트 + RocketMQ 프로듀서 + 엔티티 변경 diff | 비즈니스 서비스 |
| `omni-common-job` | 스케줄링 작업 Starter: XXL-JOB 자동 구성 + Admin Client + 시스템 작업 등록 + `@SystemJobMeta` 이중 어노테이션 | 비즈니스 서비스 |
| `omni-common-workflow` | 워크플로 Starter: Flowable 자동 구성, `ApprovalService` SPI, `UserGroupLookup`, `TenantInfoFilter` | 워크플로 서비스 |

> 모든 Starter는 Spring Boot 자동 구성 (`AutoConfiguration.imports`)을 통해 Bean을 등록합니다. 하위 모듈은 수동 `@ComponentScan`이 필요 없습니다.
> `omni-common-redis`와 `omni-common-redis-reactive`는 혼용 불가합니다. WebFlux 서비스는 리액티브 버전만 의존할 수 있습니다.

### omni-auth (인증 서비스)

Spring Security 7 + OAuth2 Authorization Server 기반 인증 마이크로서비스:

- **사용자 로그인**: 사용자명 + 비밀번호 + 그래픽 CAPTCHA + 멀티 테넌트, RS256 JWT 발급
- **멀티 프로바이더 소셜 로그인**: `OAuth2ProviderHandler` 전략 패턴 기반 확장 가능한 소셜 로그인 아키텍처, GitHub·Google·Gitee 3개 프로바이더 연동 완료, 프론트엔드에 WeChat 로그인入口 예약. HMAC-SHA256 state 서명으로 위변조 방지, 첫 로그인 시 로컬 사용자 자동 생성 및 서드파티 ID 연동 (`sys_user_oauth_provider` 테이블)
- **OAuth2 인증**: Authorization Code + PKCE 플로우, 서드파티 연동 지원
- **디바이스 인증 코드 모드** (RFC 8628): IoT 디바이스, CLI 도구 등 브라우저 없는 환경을 위해 `omni-device` 클라이언트를 통해 인증 기능 제공, 프론트엔드 `/device` 페이지에서 디바이스 측 인증 요청 및 토큰 폴링 시뮬레이션, `/device/verify` 페이지에서 사용자가 다른 디바이스로 스캔 또는 코드 입력하여 인증 완료
- **클라이언트 관리**: `oauth2_registered_client` CRUD, 동적 등록 지원
- **멀티 테넌트 RBAC**: `tenantId:username` 형식의 사용자 해석 + 역할 권한 트리
- **RBAC 권한 체계**: 기능 권한 (동적 메뉴 필터링 + `v-permission` 버튼 수준 제어 + `@PreAuthorize` API 인증) + 데이터 권한 (MyBatis-Plus `DataPermissionInterceptor` SQL 자동 가로채기, 6단계 dataScope 제로 침투 필터링)
- **JWT 서명**: RSA 키 쌍, JWK 엔드포인트로 Gateway에 공개키 제공
- **XSS 방어 구성 관리**: 프론트엔드 `시스템 관리 → XSS 방어 구성` 페이지에서 글로벌 토글 및 블랙리스트 규칙 CRUD 지원 (HTML 태그, 이벤트 핸들러, 위험 프로토콜, 커스텀 정규식 4가지 규칙 유형), 테넌트별 분리 구성, Redis 캐시 30분 TTL + 쓰기 시 능동적 무효화

### omni-common-operlog (작업 로그 Starter)

AOP + RocketMQ 기반 작업 로그 수집 프레임워크, 비즈니스 서비스에 비침습적 감사 추적 제공:

- **비침습적 수집**: `@OperLog` 어노테이션 + `OperLogAspect` AOP 애스펙트가 요청 컨텍스트 (사용자명, 테넌트 ID, IP, 요청 파라미터)와 엔티티 변경 스냅샷을 자동 수집
- **엔티티 변경 diff**: `EntityDiffer` 필드 수준 차이 비교 — UPDATE 작업은 변경된 필드만 기록하여 데이터 추적 가능
- **RocketMQ 비동기**: `OperLogProducer`가 로그 메시지를 비동기 전송하여 비즈니스 요청 응답을 차단하지 않음
- **핫/콜드 테이블 분리**: 핫 테이블 `sys_oper_log`은 최근 180일 데이터를 고속 쿼리용으로 보존, 콜드 테이블 `sys_oper_log_archive`는 컴플라이언스용으로 장기 보관. `OperLogArchiver`가 매일 02:00에 자동 아카이브 실행
- **감사 로그와 보완 관계**: 작업 로그는 비즈니스 데이터 변경 (who/when/what/changed)을 기록, 감사 로그 (`sys_audit_log`)는 보안 이벤트를 기록, 로그인 로그 (`sys_login_log`)는 로그인 행동을 기록 — 세 가지가 완전한 감사 추적 체계 구축
- **omni-auth에서 비활성화**: 인증 모듈은 이 모듈에 의존하지 않으며, 인증 행동은 `sys_login_log` + `sys_audit_log`으로 커버

### omni-base (기초 데이터 및 작업 서비스)

데이터 사전, 스케줄링 작업, 작업 로그를 포함하는 기초 데이터 및 작업 관리 마이크로서비스:

- **사전 타입 관리**: `sys_dict_type` 테이블 — 목록 조회, 상세 조회, 생성, 수정, 삭제, 상태 전환, 11개 API 엔드포인트 완전 구현
- **사전 데이터 관리**: `sys_dict_data` 테이블 — 타입 코드로 연관, 목록 조회, 생성, 수정, 삭제, 캐시 새로고침 지원
- **Redis cache-aside 캐시**: TTL 30분, write-through 무효화, `dict:{typeCode}` 키 형식
- **시스템 작업 관리**: `SystemJobRegistry` 메타데이터와 XXL-JOB 런타임 상태를 통합, 등록/시작/중지/트리거/해제 수명 주기 작업 제공, `job:system-job:*` 권한 코드
- **사용자 작업 관리**: SPI 기반 작업 타입 + 작업 인스턴스 + 실행 로그, 사용자 셀프 서비스 생성, Cron 스케줄링, 소유권 검증 지원
- **작업 로그 열람**: 핫 테이블 쿼리 + 모듈, 작업 유형, 운영자, 시간 범위 기반 페이지네이션 필터링
- **프론트엔드 관리 페이지**: 사전 관리 (master-detail 레이아웃), 시스템 작업, 작업 타입, 워크스페이스 내 작업, `base:dict` / `job:*` 권한 코드
- **XSS 방어 상속**: `XssConfigProvider` SPI를 구현하여 3계층 XSS 방어를 자동 획득

### omni-gateway (API 게이트웨이)

Spring Cloud Gateway Server (WebFlux) 기반 리액티브 게이트웨이:

- 라우트 전달: Nacos 등록 서비스의 백엔드로 자동 라우팅 (StripPrefix=2)
- 서비스 디스커버리: Nacos 등록 서비스 자동 라우팅
- 인증 필터: `AuthFilter` (JWT RS256 서명 검증 + claims 추출 + ID 헤더 주입)
- CORS 구성: `CorsConfig`로 크로스 오리진 요청 처리

### omni-frontend (Vue 3 SPA)

| 계층 | 디렉토리 | 책임 |
|------|---------|------|
| API | `src/api/` | 도메인별 파일, 공유 Axios 인스턴스, 타입 안전 |
| Store | `src/stores/` | Pinia Composition API 스타일, 도메인별 Store |
| 라우터 | `src/router/` | 지연 로드 라우트 + 내비게이션 가드 (기본 인증 필요) |
| 뷰 | `src/views/` | 페이지 컴포넌트, SFC 순서: script → template → style. `device/` (디바이스 인증), `job/` (작업 관리), `system/` (시스템 관리) 하위 디렉토리 포함 |
| 레이아웃 | `src/layout/` | 앱 셸 (사이드바 + 상단바 + 콘텐츠 영역) |
| 타입 | `src/types/` | 공유 타입 정의 (ApiResponse, PageResult의 단일 소스) |
| 스타일 | `src/styles/` | 전역 리셋 + 레이아웃 스타일 |

## 스케줄링 작업 시스템

프로젝트는 **XXL-JOB 3.3.1** 기반의 이중 트랙 스케줄링 작업 아키텍처를 구현하며, 시스템 작업과 사용자 작업 두 가지 모드를 지원합니다. 심층 기술 세부사항은 [`docs/scheduling.md`](docs/scheduling.md)를 참조하세요.

### 아키텍처 개요

- **omni-common-job**: `XxlJobAutoConfiguration`, `XxlJobAdminClient`, `SystemJobRegistry`를 캡슐화하여 통합 작업 등록 및 관리 기능 제공
- **omni-common-core**: `UserJobHandler` SPI 인터페이스와 `UserJobMessage` POJO 정의
- **omni-base**: 비즈니스 레이어에서 구체적인 시스템 작업 및 사용자 작업 Handler 구현

### 시스템 작업

`@XxlJob` + `@SystemJobMeta` 이중 어노테이션으로 구동됩니다. `SystemJobRegistry`가 시작 시 자동으로 스캔하여 XXL-JOB Admin에 등록합니다. 예시: `OperLogArchiver`(운영 로그 아카이브) — Bean 등록 → 자동 발견 → REST API 관리 → XXL-JOB 스케줄링 실행. 관리 인터페이스에는 `job:system-job:*` 권한이 필요합니다.

### 사용자 작업

SPI 모드 채택: `UserJobHandler` 인터페이스를 구현하고 Spring Bean으로 등록하면 `UserJobHandlerRegistry`가 자동 발견합니다. 모든 사용자 작업은 단일 `@XxlJob("userJobExecuteHandler")` 진입점을 공유하며, JSON `executorParam`을 통해 구체적인 Handler로 라우팅합니다. `MyJobController`는 소유권 검증(`@PreAuthorize` 아님)을 사용하여 사용자가 자신이 만든 작업만 관리할 수 있도록 합니다.

### 의존 컴포넌트

| 컴포넌트 | 설명 |
|---------|------|
| XXL-JOB Admin (`:18080`) | 분산 스케줄링 센터, Docker 컨테이너 배포 |
| `omni-common-job` 모듈 | 자동 설정, Admin Client, 시스템 작업 등록 |
| `sys_user_job_type` / `sys_user_job` / `sys_user_job_log` | 사용자 작업 유형, 작업 인스턴스, 실행 로그 |

### 신규 작업 유형 추가 가이드

`DrinkWaterRemindHandler`(물 마시기 알림)를 예로: ① `sys_user_job_type` 테이블에 유형 등록 → ② `UserJobHandler` 인터페이스 구현 및 `@Component` 추가 → ③ 사용자가 워크스페이스에서 작업 생성 → ④ XXL-JOB 스케줄링 실행 검증. 상세 단계는 [`docs/scheduling.md` 4장](docs/scheduling.md)을 참조하세요.

### 프론트엔드 통합

세 가지 진입점: 시스템 작업 관리(`SystemJob`), 작업 유형 관리(`UserJobType`), 워크스페이스 내 작업(`MyJob`). Cron 표현식 편집기, `DynamicFormRenderer` 동적 매개변수 폼, 10초 간격 활성 작업 로그 폴링 및 `ElNotification`을 통한 실행 결과 푸시 알림을 지원합니다.

## 워크플로 엔진

프로젝트는 **Flowable 7.x** 기반의 시각적 BPMN 워크플로 엔진을 구축하여, 모델 설계, 버전 관리, 멀티 인스턴스 회서 승인 등의 기능을 지원합니다. 심층 기술 세부사항은 [`docs/workflow.md`](docs/workflow.md)를 참조하세요.

### 아키텍처 개요

- **omni-workflow**: 독립 마이크로서비스 (포트 8103), Flowable BPMN 엔진을 통합하여 모델 관리, 프로세스 정의, 인스턴스 모니터링, 승인 처리, 통계 대시보드 등 7개 컨트롤러 제공
- **omni-common-workflow**: 공유 Starter, `FlowableAutoConfiguration`, `ApprovalService` SPI, `UserGroupLookup`, `TenantInfoFilter` 등 기반 기능 제공

### 핵심 기능

- **시각적 모델 설계**: 프론트엔드 BPMN 디자이너로 드래그 앤 드롭 모델링, XML 편집, 검증 미리보기, `BpmnXmlBuilder`가 디자이너 JSON을 BPMN 2.0 XML로 변환
- **이중 버전 관리**: 비즈니스 버전 (DRAFT → PUBLISHED → ARCHIVED)은 `wf_process_model_version` 테이블에서 관리, 엔진 버전은 Flowable 배포 메커니즘으로 관리
- **멀티 인스턴스 회서**: ALL (전원 승인)과 ANY (한 명 이상 승인) 두 가지 승인 모드 지원, MI `completionCondition`으로 제어, 거부 시 즉시 종료
- **동적 후보자 해결**: `omni:assignment` JSON 확장 요소 + `ScopedRoleAssignmentListener` 런타임 해결, 다양한 앵커 유형 (기안자 주 조직 / 상위 조직 / 절대 조직 등) 지원
- **승인 기록 + 프로세스 진척도 + CC 알림**: 완전한 프로세스 추적 기능, `HistoricTaskInstance` 수준 정밀도로 승인 결과 판정

### 데이터베이스 테이블 (omni_workflow 데이터베이스)

| 테이블 | 설명 |
|--------|------|
| `wf_process_model` | 프로세스 모델 메인 테이블, `model_key`는 테넌트 내 고유 |
| `wf_process_model_version` | 모델 버전 테이블, BPMN XML + 배포 정보 저장 |
| `wf_process_instance_ext` | 프로세스 인스턴스 확장 테이블, 모델 버전과 Flowable 인스턴스 연결 |
| `wf_todo_task` | 미처리 작업 캐시 테이블 |
| `wf_cc_record` | CC 기록 테이블 |

### 프론트엔드 통합

7개 페이지/컴포넌트로 완전한 워크플로 시나리오 커버: 모델 관리 (`ModelDesigner`), 버전 이력 (`VersionHistoryDialog`), 검증 결과 (`ValidateResultDialog`), 프로세스 정의, 프로세스 인스턴스, 승인 기록 (`ApprovalRecordsDialog`), 프로세스 진척도 (`ProcessProgressDialog`), 통계 대시보드.

## RBAC 권한 체계

프로젝트는 완전한 RBAC 권한 모델을 구현하며, 기능 권한과 데이터 권한 두 개의 독립 서브시스템으로 나뉩니다. 상세 설계는 [`docs/architecture.md`](docs/architecture.md)의 RBAC Permission System 섹션을, 엔드투엔드 플로우는 [`docs/core-flows.md`](docs/core-flows.md)의 Flow 5와 Flow 6을 참조하세요.

### 기능 권한

사용자가 "무엇을 할 수 있는지"를 제어하는 3계층 방어:

| 계층 | 메커니즘 | 구현 |
|------|---------|------|
| 동적 메뉴 | 백엔드가 사용자 권한에 따라 메뉴 트리를 재귀 필터링 | `MenuController` → `usePermissionStore` → 동적 라우트 등록 |
| 버튼 제어 | Vue 커스텀 디렉티브로 DOM 표시/비표시 제어 | `v-permission="'system:user:create'"` → `display:none` |
| API 인증 | Spring Security 메서드 수준 권한 검증 | `@PreAuthorize("hasAuthority('system:user:create')")` |

### 데이터 권한

MyBatis-Plus `DataPermissionInterceptor`에 의한 SQL 자동 가로채기 — 비즈니스 코드에 제로 침투, 사용자가 "어떤 데이터를 볼 수 있는지"를 제어:

| dataScope | 의미 |
|-----------|------|
| `ALL` | 모든 데이터 (테넌트 간) |
| `TENANT` | 소속 테넌트의 모든 데이터 |
| `DEPT_AND_BELOW` | 소속 부서 및 하위 부서 |
| `DEPT` | 소속 부서만 |
| `CUSTOM` | 커스텀 부서 집합 |
| `SELF` | 자신의 데이터만 |

**핵심 흐름**: 요청 도착 → `DataScopeResolveFilter`가 역할의 dataScope 해석 (가장 관대한 것 우선) → `DataScopeContext` (ThreadLocal)에 기록 → `DataPermissionInterceptor`가 WHERE 조건 자동 추가 → 요청 완료 시 컨텍스트 정리.

## 사용자 생성

3가지 사용자 생성 경로를 지원하며, 모든 경로에서 `USER` 기본 역할 (`data_scope=SELF`, 자신의 데이터만 조회 가능)이 자동 할당됩니다:

| 경로 | 진입점 | 인증 요구사항 | 테넌트 | 비밀번호 |
|------|--------|-------------|--------|---------|
| 셀프 등록 | 등록 페이지 `/register` | 없음 (공개 엔드포인트) | 사용자가 드롭다운에서 선택 | 사용자 설정 (BCrypt) |
| 관리자 생성 | 사용자 관리 페이지 | `system:user:create` | 관리자 지정 | 관리자 설정 (BCrypt) |
| 소셜 로그인 | OAuth2 콜백 | 없음 (서드파티 인증) | HMAC state 파라미터 | 없음 (소셜 전용) |

상세 플로우는 [`docs/core-flows.md`](docs/core-flows.md) Flow 7을 참조하세요.

## 권한 협업 모델

테넌트, 조직, 역할, 기능 권한, 데이터 권한 5가지 요소가 협업하여 완전한 접근 제어를 수행합니다:

```
테넌트(Tenant) ─── 분리 경계: 사용자명은 테넌트 내에서 고유, 데이터는 기본적으로 테넌트별 분리
  │
  ├── 사용자(User) ─── 하나의 테넌트에 소속, 여러 역할 보유 가능
  │     │
  │     ├── 역할(Role) ─── 사용자와 권한을 연결하는 다리
  │     │     ├── 기능 권한(Permission) ─── "무엇을 할 수 있는지" 제어 (메뉴/버튼/API)
  │     │     └── 데이터 범위(DataScope) ─── "어떤 데이터를 볼 수 있는지" 제어
  │     │
  │     └── 조직 단위(OrgUnit) ─── 사용자의 소속 부서, 데이터 권한의 앵커
  │
  └── 권한 트리(Permission Tree) ─── DIRECTORY → MENU → BUTTON → API 4계층 구조
```

**협업 흐름**:

1. **로그인 시**: `(tenantId, username)`으로 사용자 검색 → 역할 로드 → 권한 로드 → JWT 발급
2. **기능 제어**: JWT `scope` 클레임에 권한 코드 포함 → 프론트엔드 동적 메뉴 + `v-permission` 버튼 숨김 → 백엔드 `@PreAuthorize` API 인증
3. **데이터 제어**: 역할의 `data_scope`로 가시 범위 결정 → `DataScopeResolveFilter`가 가장 넓은 범위 해석 → MyBatis-Plus가 WHERE 조건 자동 추가
4. **조직 연동**: 사용자의 `primaryUnitId`를 데이터 권한 앵커로 사용 → `DEPT`/`DEPT_AND_BELOW` 범위는 구체화 경로로 계층 조회

## 통일 응답 형식

모든 API는 `R<T>` 래퍼를 사용합니다. 프론트엔드와 백엔드는 엄격한 계약 일관성을 유지합니다. 상세 내용은 [`docs/api-contract.md`](docs/api-contract.md)를 참조하세요.

**성공 응답**:
```json
{
  "code": 200,
  "message": "success",
  "data": { "id": 1, "username": "demo", "email": "demo@example.com" }
}
```

**오류 응답**:
```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

**페이지네이션 응답**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [{ "id": 1, "username": "demo" }],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

## 개발자 가이드 (신입 필독)

### 1. 코드 작성 전에 문서 읽기

프로젝트는 **Harness 산업 디자인 패턴**을 따르며, 시스템 지식을 3계층으로 정리합니다:

| 계층 | 내용 | 위치 |
|------|------|------|
| Layer 1: Architecture | 시스템 경계, 모듈 책임, 데이터 흐름, RBAC 권한 체계, 제약사항 | `docs/architecture.md` |
| Layer 2: Patterns | 백엔드/프론트엔드 코딩 패턴, API 계약, 보안, 핵심 흐름 | `docs/backend-patterns.md`, `docs/frontend-patterns.md`, `docs/api-contract.md`, `docs/core-flows.md` |
| Layer 3: Code | 구체적인 함수, 클래스, 컴포넌트 구현 | 소스 파일 |

**규칙**: 코드를 수정하기 전에 해당 `docs/` 문서를 확인하세요. 아키텍처나 계약이 변경되는 경우, 먼저 `docs/`를 업데이트한 후 코드를 수정합니다.

### 2. 백엔드 개발 규칙

- **계층화**: Controller → Service (인터페이스) → ServiceImpl → Repository
- **DI**: `@RequiredArgsConstructor` + `final` 필드. `@Autowired` 필드 주입 금지
- **반환값**: 모든 Controller 메서드는 `R<T>` 반환
- **예외**: 비즈니스 오류는 `BusinessException`을 throw. `GlobalExceptionHandler`가 통일 처리
- **로깅**: `@Slf4j` + 파라미터화된 플레이스홀더. `System.out.println` 금지
- **상세 규칙**: `docs/backend-patterns.md` 참조

### 3. 프론트엔드 개발 규칙

- **API 계층**: 도메인별 파일 (`src/api/user.ts`), `request.ts`의 공유 Axios 인스턴스 사용
- **타입**: 공유 타입은 `src/types/api.ts`에만 — 중복 정의 금지
- **Store**: Pinia Composition API 스타일, `use` 접두사 네이밍
- **컴포넌트**: SFC 순서 `<script setup>` → `<template>` → `<style scoped>`
- **라우터**: 지연 로드 + `meta` 선언 (title, icon, requiresAuth)
- **상세 규칙**: `docs/frontend-patterns.md` 참조

### 4. 커밋 전 체크리스트

```bash
# 백엔드 컴파일 확인
cd omni-backend && ./mvnw clean install

# 프론트엔드 빌드 + Lint 확인
cd omni-frontend && npm run build && npm run lint
```

전체 체크리스트는 `AGENTS.md`의 Completion Checklist 섹션을 참조하세요.

### 5. 흔한 함정

| 함정 | 원인 | 해결책 |
|------|------|--------|
| Gateway 라우트가 로드되지 않음 | 5.x에서 구성 접두사 변경 | `spring.cloud.gateway.server.webflux` 사용 — AGENTS.md Important Notes 참조 |
| Maven 클래스 버전 오류 | JAVA_HOME이 JDK 25를 가리키지 않음 | `JAVA_HOME`을 JDK 25 디렉토리로 설정 |
| 프론트엔드 타입 불일치 | `ApiResponse`가 여러 곳에서 정의됨 | `@/types/api`에서만 가져오기 — 중복 정의 금지 |
| Actuator gateway 엔드포인트 404 | 명시적 활성화 필요 | `management.endpoint.gateway.enabled: true` 구성 |
| GitHub 소셜 로그인 콜백 404 | OAuth App 미생성 또는 Client ID가 플레이스홀더 | 위 "소셜 로그인 구성"에 따라 GitHub OAuth App을 생성하고 실제 인증 정보 입력 |
| Google 소셜 로그인 콜백 404 | Google Cloud Console OAuth 클라이언트 미생성 또는 Client ID가 플레이스홀더 | 위 "소셜 로그인 구성"에 따라 Google Cloud Console에서 OAuth 2.0 클라이언트 생성 후 실제 인증 정보 입력 |
| Gitee 소셜 로그인 콜백 404 | Gitee 서드파티 애플리케이션 미생성 또는 Client ID가 플레이스홀더 | 위 "소셜 로그인 구성"에 따라 Gitee에서 서드파티 애플리케이션 생성 후 실제 인증 정보 입력 |
| Google 로그인 후 콜백 페이지에서 멈춤 | DB에 `sys_user_oauth_provider` 테이블 없음 | `init-all.sql`이 실행되었는지 확인. 이 테이블은 모든 프로바이더의 바인딩을 저장 |
| GitHub 로그인 후 콜백 페이지에서 멈춤 | DB에 `sys_user_oauth_provider` 테이블 없음 | `init-all.sql`이 실행되었는지 확인 (해당 테이블 포함), 또는 수동 생성 |
| Gitee 로그인 후 콜백 페이지에서 멈춤 | GitHub와 동일 — `sys_user_oauth_provider` 테이블 없음 | `init-all.sql`이 실행되었는지 확인. 이 테이블은 모든 프로바이더의 바인딩을 저장 |
| 소셜 로그인 state 서명 검증 실패 | `OAUTH2_STATE_SECRET` 미구성 또는 재시작 후 변경 | 고정 `OAUTH2_STATE_SECRET` 환경 변수를 설정하여 서명 키 일관성 확보 |
| Nacos 재시작 후 구성 사라짐 | 내장 Derby DB 사용, 영속화 없음 | 프로젝트의 `init-nacos.sql`을 사용하여 MySQL 외부 스토리지로 전환 |
| Maven 빌드 순서 오류 | `omni-common-core`가 먼저 설치되지 않아 하위 모듈 컴파일 실패 | 부모 POM에서 `./mvnw clean install` 실행 — Maven reactor가 `<modules>` 선언 순서에 따라 자동 해결 |
| Redis Starter 혼용으로 인한 스레드 기아 | 블로킹 `omni-common-redis`를 WebFlux 서비스에 도입 | WebFlux 서비스 (Gateway 등)는 `omni-common-redis-reactive`만 의존 가능, 혼용 불가 |
| Spring Cloud Stream 컨슈머가 메시지를 수신하지 않음 (RocketMQ 컨슈머 그룹 OFFLINE) | 여러 `Consumer<T>` Bean이 존재할 때 `spring.cloud.function.definition`이 누락되었거나 잘못된 네임스페이스(`spring.cloud.stream.function.definition`)에 배치됨 | `spring.cloud.function.definition: beanName1;beanName2`를 `spring.cloud.function` 아래에 추가 — **`spring.cloud.stream.function` 아래가 아님**. 예시: `spring.cloud.function.definition: operlogConsumer;userJobConsumer` |

## AI 네이티브 엔지니어링 실천

이 프로젝트는 AI 지원 개발 워크플로를 지원합니다:

- **`AGENTS.md`**: AI 실행 매뉴얼 — 하드 제약, 실행 규칙, 완료 체크리스트 정의
- **`docs/` 디렉토리**: 시스템 진실 문서 — AI가 코드를 변경하기 전에 이 문서를 읽고 시스템 맥락을 이해
- **`.qoder/skills/`**: AI 행동 확장 단위 (예: `/grill-me` 설계 스트레스 테스트 Skill)

핵심 원칙: **Layer 1과 Layer 2 (Architecture + Patterns)를 먼저 정의함으로써, Layer 3 (Code)를 AI에게 전력으로 고속 생산시킬 수 있습니다.**

## 라이선스

[Apache License 2.0](LICENSE)

---

## 프로젝트 지원

이 프로젝트가 도움이 되었다면 Star로 응원해 주세요!

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

[PR](https://github.com/wang-baohai/Omni-Stack/pulls)을 환영합니다!

---

**© Wang Baohai**
