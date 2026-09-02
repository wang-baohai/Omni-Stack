# Omni-Stack

> Spring Boot 4 + Vue 3 기반의 마이크로서비스 스캐폴딩 플랫폼으로, Harness 산업 설계 패턴을 채택하여 AI 보조 개발을 위한 업계 모범 사례 기반을 제공합니다.
>
> **한 번의 명령으로 미들웨어, 데이터베이스 마이그레이터, 8개 마이크로서비스와 프론트엔드를 시작합니다. full 모드는 Docker 컨테이너 16개입니다.**

**[中文](README.md)** | **[English](README.en.md)** | **[日本語](README.jp.md)**

**GitHub**: https://github.com/wang-baohai/Omni-Stack | **Gitee**: https://gitee.com/wang-baohai/Omni-Stack

**연락처 이메일**: wangbaohai1993@gmail.com

## 처음 시작하기

- [5분 빠른 시작](docs/guides/quick-start.kr.md)에서 시작, 로그인, 검증까지 가장 짧은 경로를 제공합니다.
- [인증 가이드](docs/guides/authentication.kr.md)에서 권한, Workflow, Scheduling, CRM, SRM, `omni-procurement`, `omni-asset` 사용 가이드로 이어집니다.
- 프리셋, `create-service`, CRUD 생성과 경량 모드는 [스캐폴드 개발 가이드](docs/guides/scaffold-development.kr.md)를 참조하세요. 유지 관리 CLI는 `tools/omni-cli`에 있습니다.
- 스키마는 `omni-db-migrator`와 `database/changelog/`에서 관리하며 `scripts/sql/seed`에는 멱등 시드 데이터만 둡니다.
- 로컬 관측성과 운영 보안 경계는 [docs/observability.kr.md](docs/observability.kr.md)와 [운영·업그레이드 가이드](docs/guides/operations-upgrade.kr.md)를 참조하세요.
- [한국어 화면 캡처 색인](docs/images/ko-KR/auth-login.png)은 문서 전용 Playwright에서 생성하며 각 가이드에 역할, 전제 조건, 단계와 기대 결과를 기록합니다.

기업 관리 화면, 테넌트 인식 데이터 애플리케이션, Workflow·CRM·공급망 기능이 필요한 마이크로서비스에 적합합니다. 노코드 플랫폼이 아니며 도메인 모델링, 운영 Secret 관리, 용량 계획과 사람의 배포 승인을 대체하지 않습니다.

---

## 주요 특징

- **JDK 25** + Spring Boot 4.0.6 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 최신 기술 스택
- **온디맨드 개발과 원클릭 배포**: Omni CLI는 core/workflow/crm/supply-chain/full의 최소 의존 구성을 시작하고 `start.bat` / `./start.sh`는 기본으로 16개 컨테이너 full 모드를 시작합니다.
- **CRM 프리세일즈 폐쇄 루프**: 독립 `omni-crm` 서비스가 리드, 고객, 연락처, 기회, 팔로업, 전환, 대시보드를 커버 — 테넌트, RBAC, 데이터 범위, XSS, 감사, Outbox 기능 재사용
- **SRM 공급업체 수명주기**: 독립 `omni-srm` 서비스가 공급업체 입점, 심사, 등급 분류, 성과 평가, 리스크 관리, 포털 셀프서비스, 공급업체 360을 커버 — 자세한 내용은 [docs/srm.kr.md](docs/srm.kr.md) 참조
- **멀티 제공자 소셜 로그인**: GitHub + Google + Gitee OAuth2 원클릭 로그인 (전략 패턴으로 확장 가능), 최초 로그인 시 자동 회원가입
- **3계층 XSS 종심 방어**: Jackson 역직렬화기 + Servlet Filter + Gateway 보안 응답 헤더, 테넌트별 설정 가능, 프론트엔드 관리 UI 완전 지원
- **Common Starter 생태계**: Servlet 조합 Starter `omni-common-service`를 포함한 10개 모듈이며 필수 보안 컨텍스트가 없으면 안전하게 거부합니다.
- **선택형 전체 관측성**: OpenTelemetry, Prometheus, Pushgateway, Grafana, Tempo, Loki, Alloy는 기본 비활성화이며 명시적으로 켭니다. [docs/observability.kr.md](docs/observability.kr.md)를 참조하세요.
- **이중 트랙 스케줄링**: XXL-JOB 3.3.1 시스템 작업 + 사용자 작업 듀얼 모드, 프론트엔드 Cron 에디터 + 실행 로그 실시간 푸시, 자세한 내용은 [docs/scheduling.kr.md](docs/scheduling.kr.md) 참조
- **Transactional Outbox 신뢰성 메시지**: 로컬 아웃박스 + XXL-JOB 릴레이 + 지수 백오프 재시도 + 데드레터 관리, 자세한 내용은 [docs/mq-reliability.kr.md](docs/mq-reliability.kr.md) 참조
- **시각적 BPMN 워크플로우**: Flowable 7.x 엔진, 프론트엔드 드래그 앤 드롭 모델링 + 이중 버전 관리 + 다중 인스턴스 countersign + 동적 후보자 해석, 자세한 내용은 [docs/workflow.kr.md](docs/workflow.kr.md) 참조
- **완전한 RBAC 권한 체계**: 기능 권한 (동적 메뉴 + v-permission + @PreAuthorize) + 데이터 권한 (DataPermissionInterceptor 6단계 필터링), 자세한 내용은 [docs/architecture.kr.md](docs/architecture.kr.md) 참조
- **AI 네이티브 프로젝트**: AGENTS.md 실행 매뉴얼 + docs/ 시스템 진실 + Skills 행동 확장, 첫 두 레이어를 고정하고 세 번째 레이어는 AI가 고속 생산

## 기술 스택

| 계층 | 기술 | 버전 |
|------|------|------|
| JDK | OpenJDK | 25 |
| 백엔드 프레임워크 | Spring Boot | 4.0.6 |
| 마이크로서비스 프레임워크 | Spring Cloud + Spring Cloud Alibaba | 2025.1.1 / 2025.1.0.0 |
| API 게이트웨이 | Spring Cloud Gateway Server (WebFlux) | 5.0.1 |
| 등록/설정 | Nacos Server | v3.1.1 |
| 흐름 제어/서킷 브레이커 | Sentinel Dashboard | 1.8.8 |
| 메시지 큐 | Apache RocketMQ | 5.3.2 |
| 작업 스케줄링 | XXL-JOB Admin | 3.3.1 |
| 워크플로우 엔진 | Flowable BPMN | 7.x |
| 프론트엔드 프레임워크 | Vue 3 + TypeScript | 3.5.35 / 5.9.3 |
| 빌드 도구 | Vite 8 (Rolldown) | 8.2.1 |
| UI 프레임워크 | Element Plus | 2.14.0 |
| 상태 관리 | Pinia | 3.0.4 |
| Node.js | Node.js LTS | >= 22.12.0 |

## 아키텍처 개요

```
                                 ┌─────────────────┐
                                 │    omni-auth     │
                                 │   Spring :8100   │
                                 │  Security+OAuth2 │
                                 └─────────────────┘
                                        ▲
┌─────────────────┐     ┌──────────────────┐
│   omni-frontend  │────>│   omni-gateway    │lb://
│   Vue 3 SPA     │/api │  WebFlux :8102    │────>┌─────────────────┐
│   Nginx :3000   │────>│  StripPrefix=2    │     │    omni-base     │
└─────────────────┘     └──────────────────┘     │   Spring :8101   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │  omni-workflow   │
                            │                    │  Flowable :8103  │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-crm      │
                            │                    │   Sales :8104   │
                            │                    └─────────────────┘
                            │                    ┌─────────────────┐
                            │                    │    omni-srm      │
                            │                    │   SRM :8105     │
                            │                    └─────────────────┘
                    ┌───────┴────────┐
                    │  MySQL :3306   │  영구 저장소
                    │  Redis :6379   │  캐시 + 인증코드
                    │  Nacos :8848   │  서비스 검색 + 설정 센터
                    │  RocketMQ      │  메시지 큐 (비동기 전송)
                    │  XXL-JOB       │  분산 작업 스케줄링
                    └────────────────┘
```

**요청 흐름**: 브라우저 `:3000` → Nginx 리버스 프록시 → Gateway `:8102` → `lb://` → 백엔드 서비스

## 프로젝트 구조

```
Omni-Stack/
├── AGENTS.md                           # AI 실행 매뉴얼 (하드 제약 + 빌드 명령 + 체크리스트)
├── start.bat / start.sh                # Docker 원클릭 시작 스크립트
├── stop.bat / stop.sh                  # 원클릭 정지 스크립트
├── compose.yaml                        # Compose 통합 진입점
├── compose.infra.yaml / compose.apps.yaml
├── compose.observability.yaml          # 선택형 로컬 관측성 스택
├── docker/
│   ├── backend/Dockerfile              # 백엔드 멀티스테이지 빌드 (Maven 컴파일 + JRE 실행)
│   ├── frontend/Dockerfile             # 프론트엔드 멀티스테이지 빌드 (npm 컴파일 + Nginx)
│   ├── frontend/nginx.conf             # Nginx 리버스 프록시 설정
│   └── rocketmq/broker-docker.conf     # RocketMQ Broker 설정
├── docs/                               # 시스템 진실 문서 (심층 기술 문서, 다국어 지원)
│   ├── architecture.md                 #   시스템 경계, 모듈 맵, 데이터 흐름, RBAC 권한 체계
│   ├── api-contract.md                 #   응답 형식, 에러 코드, 페이징, 네이밍 규칙
│   ├── backend-patterns.md             #   백엔드 계층화, 유효성 검사, 예외, 로그, 보안 권한
│   ├── frontend-patterns.md            #   프론트엔드 디렉토리, API 계층, 상태 관리, 권한 제어
│   ├── core-flows.md                   #   로그인/OAuth2/RBAC 권한 흐름 엔드투엔드 추적
│   ├── scheduling.md                   #   스케줄링 시스템 심층 기술 문서
│   ├── workflow.md                     #   워크플로우 엔진 심층 기술 문서
│   ├── mq-reliability.md              #   신뢰성 메시지 전송 심층 기술 문서
│   ├── crm.md                          #   CRM 프리세일즈 시스템 진실 (Harness 문서)
│   ├── srm.md                          #   SRM 공급업체 관계 관리 시스템 진실 (Harness 문서)
│   ├── design/srm-design.md            #   SRM MVP 설계 및 구현 베이스라인
│   └── docker-deployment.md            #   Docker 전체 스택 배포 심층 가이드
├── database/changelog/                 # Liquibase 구조 이력과 벤더 스키마(공식 DDL)
├── database/seed/                      # 시드 매니페스트(manifest.yaml, SHA-256 검증)
├── scripts/sql/seed/                   # 공식 멱등 시드만 보관
│   ├── init-nacos.sql                  #   호환 기간 레거시: Nacos MySQL 영구 저장
│   └── init-xxl-job.sql               #   호환 기간 레거시: XXL-JOB 데이터베이스
├── omni-backend/                       # Maven 멀티 모듈 백엔드
│   ├── omni-common-core/               #   순수 POJO: R<T>, PageResult, XSS SPI
│   ├── omni-common/                    #   Web 자동 설정: Jackson, CORS, XSS Filter
│   ├── omni-common-mybatis/            #   MyBatis-Plus Starter
│   ├── omni-common-redis/              #   블로킹 Redis Starter
│   ├── omni-common-redis-reactive/     #   리액티브 Redis Starter (Gateway 전용)
│   ├── omni-common-operlog/            #   운영 로그 Starter
│   ├── omni-common-job/                #   스케줄링 작업 Starter
│   ├── omni-common-mqlog/              #   MQ 메시지 신뢰성 Starter
│   ├── omni-common-workflow/           #   워크플로우 Starter
│   ├── omni-common-service/            #   Servlet 비즈니스 서비스 조합 Starter
│   ├── omni-auth/                      #   인증 서비스 (8100)
│   ├── omni-base/                      #   기초 데이터 서비스 (8101)
│   ├── omni-workflow/                  #   워크플로우 엔진 서비스 (8103)
│   ├── omni-crm/                       #   CRM 프리세일즈 폐쇄 루프 서비스 (8104)
│   ├── omni-srm/                       #   SRM 공급업체 관계 관리 서비스 (8105)
│   ├── omni-procurement/               #   Procurement 실행 서비스 (8106)
│   ├── omni-asset/                     #   Asset 수명주기 서비스 (8107)
│   └── omni-gateway/                   #   API 게이트웨이 (8102)
└── omni-frontend/                      # Vue 3 SPA (3000)
```

## Docker 원클릭 배포 (권장)

full 모드는 미들웨어, `omni-db-migrator`, 8개 백엔드 마이크로서비스와 프론트엔드를 시작합니다. 프리셋은 검증된 더 작은 의존 구성만 시작할 수 있습니다.

### 사전 요구사항

| 소프트웨어 | 버전 요구사항 | 설명 |
|------|---------|------|
| Docker Desktop | 최신 안정 버전 | Windows는 WSL2 백엔드 필요 |
| Node.js | >= 22.12.0 | Omni CLI와 시작 스크립트 실행 |
| Git | 최신 버전 | 프로젝트 클론 |

> 컨테이너 시작에는 로컬 JDK/Maven이 필요 없지만 Omni CLI에는 Node.js가 필요합니다.

### 시작

| 플랫폼 | 명령 |
|------|------|
| Windows | `start.bat` (관리자 권한 불필요) |
| Linux / macOS | `./start.sh` |

스크립트 자동 수행: Docker 감지 → Docker 엔진 시작 (미실행 시) → 포트 보호 (Windows Hyper-V/WSL2) → 미들웨어 이미지 풀 → 애플리케이션 이미지 빌드 → 전체 컨테이너 시작.

```bash
# 전체 서비스 시작
./start.sh

# 검증된 CRM 최소 구성 시작
./start.sh crm

# 동등한 CLI와 선택형 관측성
npm --prefix tools/omni-cli run dev -- dev up --preset crm --build
npm --prefix tools/omni-cli run dev -- dev up --preset full --observability

# 서비스 상태 확인
docker compose ps

# 전체 서비스 정지
./stop.sh
```

### 서비스 포트

| 서비스 | 포트 | 설명 |
|------|------|------|
| **프론트엔드** | **http://localhost:3000** | **접속 진입점, Nginx 리버스 프록시로 Gateway 연결** |
| 인증 서비스 | http://127.0.0.1:8100 | Spring Security + OAuth2 (루프백 진단 전용) |
| 기초 데이터 서비스 | http://127.0.0.1:8101 | 사전/조직/사용자/로그/작업 (루프백 진단 전용) |
| API 게이트웨이 | http://localhost:8102 | Spring Cloud Gateway (WebFlux) |
| 워크플로우 엔진 | http://127.0.0.1:8103 | Flowable BPMN (루프백 진단 전용) |
| CRM 서비스 | http://127.0.0.1:8104 | 리드, 고객, 기회, 팔로업 |
| SRM 서비스 | http://127.0.0.1:8105 | 공급업체, 포털, 평가, 리스크 |
| Procurement 서비스 | http://127.0.0.1:8106 | 구매 요청, 견적, 발주, 입고 |
| Asset 서비스 | http://127.0.0.1:8107 | 자산 원장, 이관, 폐기 |
| MySQL | 127.0.0.1:13306 | `root` + `.env`의 `MYSQL_ROOT_PASSWORD` |
| Redis | 127.0.0.1:6379 | `.env`의 `REDIS_PASSWORD` |
| Nacos 콘솔 | http://127.0.0.1:8080 | 자격 증명은 `.env`에서 주입 |
| XXL-JOB 스케줄링 센터 | http://127.0.0.1:18080 | 로컬 초기 계정, 실행 토큰은 `.env`에서 주입 |
| RocketMQ NameServer | localhost:19876 | 호스트 매핑 포트 (컨테이너 내부 9876) |

백엔드 직접 주소는 로컬 개발 및 진단 전용입니다. 운영 환경에서는 Frontend와 Gateway만 공개하고 하위 서비스는 사설 네트워크에 유지해야 합니다.

### 검증

```bash
# 1. 프론트엔드 접속
open http://localhost:3000

# 2. 인증코드 API 검증
curl http://localhost:3000/api/auth/captcha

# 3. 전체 컨테이너 상태 확인
docker compose ps
```

로컬 데모 시드에는 최초 연동 전용 `admin` / `admin123`이 포함됩니다. 첫 로그인 직후 변경하고 운영 환경에서는 저장소 시드 자격 증명을 사용하지 마십시오. 테넌트 생성 시 초기 관리자 비밀번호를 명시해야 하며, 백엔드는 공용 기본 비밀번호를 생성하지 않습니다.

### 자주 발생하는 문제

| 문제 | 원인 | 해결 방법 |
|------|------|---------|
| 이미지 풀 실패 | 네트워크 문제 | Docker 미러 가속 설정: `"registry-mirrors": ["https://docker.1ms.run"]` |
| 포트 바인딩 실패 | 다른 프로세스나 Compose 프로젝트가 포트를 사용 중 | `dev status`로 충돌 프로젝트를 찾아 중지하며 관리자 권한은 불필요 |
| RocketMQ 포트 9876 충돌 | Windows Hyper-V 예약 포트 범위 | 호스트 매핑을 19876으로 변경, 컨테이너 내부는 여전히 9876 |
| 502 Bad Gateway | Nginx 리버스 프록시 포트 설정 오류 | nginx.conf에서 proxy_pass가 컨테이너 내부 포트 `8080`을 사용하는지 확인 (호스트 포트 `8102` 아님) |
| Nacos 시작 실패 | 헬스체크 엔드포인트 변경 | Nacos v3.1.1은 `GET /nacos/` 사용 (`/nacos/actuator/health` 아님) |
| 빌드 타임아웃 | Maven 의존성 다운로드 느림 | 백엔드 Dockerfile에 알리바바 클라우드 Maven 미러 가속 내장 |

> 심층 장애 조사 가이드는 [docs/docker-deployment.kr.md](docs/docker-deployment.kr.md) 참조

## 로컬 개발

디버깅과 코드 수정이 필요한 경우에 적합합니다. 미들웨어는 Docker를 사용하고, 백엔드와 프론트엔드는 로컬에서 실행합니다.

### 사전 요구사항

| 소프트웨어 | 버전 | 설명 |
|------|------|------|
| JDK | 25 | `JAVA_HOME` 필수 설정 |
| Node.js | >= 22.12.0 | npm 포함 |
| Docker Desktop | 최신 버전 | 미들웨어만 실행 |

### 단계

```bash
# 1. 대상 모듈과 최소 의존 구성 시작 (CRM 예시)
npm --prefix tools/omni-cli run dev -- dev up --module crm

# Nacos 준비 대기 (약 30초), http://localhost:8080 접속하여 확인

# 2. 백엔드 빌드 및 시작
export JAVA_HOME="/path/to/jdk-25"
cd omni-backend && ./mvnw clean install
cd omni-auth && ./mvnw spring-boot:run       # 포트 8100 (새 터미널에서 계속)
cd omni-base && ./mvnw spring-boot:run        # 포트 8101
cd omni-gateway && ./mvnw spring-boot:run     # 포트 8102
cd omni-workflow && ./mvnw spring-boot:run    # 포트 8103

# 3. 프론트엔드 시작
cd omni-frontend && npm install && npm run dev  # 포트 3000
```

> Maven Wrapper가 내장되어 있어 (3.9.16) Maven 전역 설치가 불필요합니다. 빌드 순서는 Maven reactor가 자동으로 해결합니다.

### 소셜 로그인 설정

GitHub, Google, Gitee 세 가지 OAuth2 제공자를 지원합니다. 자격 증명 설정은 `application-local.yml`에 구성하며 (`.gitignore`로 제외됨), 자세한 내용은 [docs/core-flows.kr.md](docs/core-flows.kr.md)를 참조하세요.

## 기능 개요

### 인증 및 로그인

| 로그인 페이지 | 회원가입 |
|--------|--------|
| ![로그인 페이지](docs/images/login.png) | ![회원가입](docs/images/register.png) |

| 데이터 대시보드 | 소셜 로그인 |
|----------|----------|
| ![데이터 대시보드](docs/images/dashboard.png) | ![소셜 로그인](docs/images/social-login-buttons.png) |

| 인증 동의 | 디바이스 코드 로그인 |
|----------|------------|
| ![인증 동의](docs/images/social-consent.png) | ![디바이스 코드 로그인](docs/images/social-device-init.png) |

| 디바이스 코드 검증 | |
|------------|--|
| ![디바이스 코드 검증](docs/images/social-device-verify.png) | |

### 시스템 관리

| 사용자 관리 | 사전 관리 |
|----------|----------|
| ![사용자 관리](docs/images/system-user.png) | ![사전 관리](docs/images/system-dict.png) |

| XSS 방어 설정 | |
|--------------|--|
| ![XSS 방어 설정](docs/images/system-xss.png) | |

### 정시 작업

| 시스템 작업 | 내 작업 |
|----------|----------|
| ![시스템 작업](docs/images/job-system.png) | ![내 작업](docs/images/job-workspace.png) |

### 운영 모니터링

| 운영 로그 | MQ 메시지 로그 |
|----------|-------------|
| ![운영 로그](docs/images/monitor-operlog.png) | ![MQ 메시지 로그](docs/images/monitor-mqmessage.png) |

### 워크플로우

| BPMN 디자이너 | 승인 흐름 |
|-------------|----------|
| ![BPMN 디자이너](docs/images/workflow-designer.png) | ![승인 흐름](docs/images/workflow-approval.png) |

### CRM 영업 관리

CRM 모듈은 프리세일즈 전 과정을 커버합니다: 리드 획득 → 팔로업 → 고객 생성 → 기회 추진 → 수주/실주. 6계층 보안 방어(Gateway JWT → 테넌트 검증 → 기능 권한 → 데이터 범위 → SQL 가로채기 → 행 수준 인가)로 멀티테넌트 데이터 격리를 보장합니다. 자세한 내용은 [CRM 시스템 트루스](docs/crm.kr.md)를 참조하세요.

| 영업 대시보드 | 리드 관리 |
|--------------|----------|
| ![영업 대시보드](docs/images/crm-overview.png) | ![리드 관리](docs/images/crm-lead-list.png) |
| 통계 카드 + 영업 퍼널 + 팔로업 목록으로 전체 영업 현황을 한눈에 | 리드 목록은 검색, 필터, 배정, 일괄 작업 지원 |

| 리드 전환 | 고객 관리 |
|----------|----------|
| ![리드 전환](docs/images/crm-lead-convert.png) | ![고객 관리](docs/images/crm-customer-list.png) |
| 적격 리드를 원클릭으로 고객 + 연락처 + 기회로 전환, 행 잠금으로 멱등성 보장 | 고객 목록은 이관, 상태 변경, 블랙리스트 관리 지원 |

| 고객 360 뷰 | 연락처 관리 |
|-------------|------------|
| ![고객 360](docs/images/crm-customer-360.png) | ![연락처 관리](docs/images/crm-contact-list.png) |
| 고객의 모든 차원을 한 드로어에: 연락처, 기회, 팔로업 활동 | 연락처는 고객에 연결되며 주요 연락처 표시 지원 |

| 기회 관리 | 기회 칸반 |
|----------|----------|
| ![기회 관리](docs/images/crm-opportunity-list.png) | ![기회 칸반](docs/images/crm-opportunity-board.png) |
| 기회 테이블에서 단계, 금액, 확률, 예상 마감일 표시 | 칸반 보드로 기회를 단계별 열로 시각적으로 관리 |

| 팔로업 활동 | |
|------------|--|
| ![팔로업 활동](docs/images/crm-activity-timeline.png) | |
| 활동 목록으로 모든 팔로업을 기록, 계획/완료/취소 상태 흐름 지원 | |

### SRM 공급업체 관리

SRM 모듈은 공급업체의 전체 수명주기를 커버합니다: 등록/입점 → 심사 → 등급 분류 → 성과 평가 → 리스크 관리 → 도태/퇴출. 5계층 신뢰 체인(Gateway JWT → 테넌트 검증 → 기능 권한 → 데이터 범위 → SQL 가로채기 → 행 수준 인가)으로 멀티테넌트 데이터 격리를 보장합니다. 자세한 내용은 [SRM 시스템 트루스](docs/srm.kr.md)를 참조하세요.

- **공급업체 마스터 데이터**: 공급업체 번호 자동 생성, 연락처, 자격, 은행 계좌 (PII 마스킹), 카테고리/등급 자동 매핑
- **입점 & 포털**: 초대 토큰 (SHA-256 해시), Outbox/Saga 크로스 서비스 역할 할당을 통한 셀프서비스 입점
- **성과 평가**: 가중 점수 (1-5 → 백분위 20-100), 자동 등급 매핑 (전략/우선/합격/도태)
- **리스크 관리**: 6차원 지표 (재무/컴플라이언스/공급/협력/품질/자격), 종합 리스크 레벨 (GREEN/YELLOW/RED)
- **공급업체 360**: 블록 단위 권한 제어 — 권한에 따라 다른 사용자가 다른 공급업체 360 섹션을 열람

| 공급업체 개요 | 공급업체 목록 |
|-------------|-------------|
| ![공급업체 개요](docs/images/srm-overview.png) | ![공급업체 목록](docs/images/srm-supplier-list.png) |
| 통계 카드 + 공급업체 분포 + 등급 현황, 핵심 지표를 한눈에 | 공급업체 목록은 검색, 필터, 배정, 일괄 작업 지원, 입점 심사의 시작점 |

| 성과 평가 | 리스크 대시보드 |
|----------|----------------|
| ![성과 평가](docs/images/srm-evaluation.png) | ![리스크 대시보드](docs/images/srm-risk.png) |
| 가중 스코어카드 (품질/납기/가격/서비스), 백분율 점수와 등급 자동 매핑 | 6차원 리스크 지표 신호등 시각화, 자격 만료 알림, 종합 리스크 레벨 |

| 초대 관리 | 공급업체 포털 |
|----------|--------------|
| ![초대 관리](docs/images/srm-invite.png) | ![공급업체 포털](docs/images/srm-portal.png) |
| 초대 토큰 발급 및 취소, 공급업체 입점 입구 제어 | 공급업체 셀프서비스 입점, 기업 정보 유지보수, 성과 조회 |

## 모듈 개요

### 백엔드 마이크로서비스

| 모듈 | 포트 | 역할 | 심층 문서 |
|------|------|------|---------|
| omni-auth | 8100 | 인증/인가: 로그인, JWT, OAuth2, RBAC, XSS 설정 관리 | [core-flows.kr.md](docs/core-flows.kr.md) |
| omni-base | 8101 | 기초 데이터: 사전, 조직, 사용자, 로그, 스케줄링 작업, MQ 메시지 관리 | [scheduling.kr.md](docs/scheduling.kr.md) |
| omni-workflow | 8103 | 워크플로우 엔진: BPMN 모델 관리, 승인, 프로세스 인스턴스 | [workflow.kr.md](docs/workflow.kr.md) |
| omni-crm | 8104 | CRM: 리드, 고객, 연락처, 기회, 팔로업, 영업 대시보드 | [crm.kr.md](docs/crm.kr.md) |
| omni-srm | 8105 | SRM: 공급업체 마스터 데이터, 입점, 평가, 리스크, 포털, 공급업체 360 | [srm.kr.md](docs/srm.kr.md) |
| omni-gateway | 8102 | API 게이트웨이: 라우팅 전달, JWT 검증, CORS, 보안 응답 헤더 | [architecture.kr.md](docs/architecture.kr.md) |

### Common Starter 생태계 (10개 모듈)

새 마이크로서비스에서 의존성 추가만으로 기능을 획득하며, `AutoConfiguration.imports` 제로 설정 자동 어셈블리:

| 모듈 | 기능 | 대상 서비스 |
|------|------|---------|
| `omni-common-core` | 순수 POJO: `R<T>`, `PageResult`, `BaseEntity`, XSS SPI, UserJobHandler SPI | 모든 서비스 |
| `omni-common` | Web 자동 설정: Jackson, CORS, 전역 예외 처리, XSS Filter | Servlet 서비스 |
| `omni-common-mybatis` | MyBatis-Plus + MySQL 드라이버 + 페이징 플러그인 | Servlet 서비스 |
| `omni-common-redis` | 블로킹 Redis + RedisTemplate 직렬화 + RedisUtils | Servlet 서비스 |
| `omni-common-redis-reactive` | 리액티브 Redis (WebFlux 서비스 전용, **블로킹 방식과 혼용 불가**) | Gateway |
| `omni-common-operlog` | 운영 로그: @OperLog AOP + RocketMQ 비동기 + 엔티티 diff + 핫/콜드 테이블 아카이빙 | 비즈니스 서비스 |
| `omni-common-job` | 스케줄링 작업: XXL-JOB 자동 설정 + @SystemJobMeta 이중 어노테이션 기반 | 비즈니스 서비스 |
| `omni-common-mqlog` | 신뢰성 메시지: Transactional Outbox + 릴레이 전송 + 데드레터 관리 | Servlet 서비스 |
| `omni-common-workflow` | 워크플로우: Flowable 자동 설정 + ApprovalService SPI | 워크플로우 서비스 |
| `omni-common-service` | Servlet 공통 Web·DB·Redis·보안·관측성 조합 | 비즈니스 서비스 |

> 자세한 설계는 [docs/backend-patterns.kr.md](docs/backend-patterns.kr.md) 및 [docs/architecture.kr.md](docs/architecture.kr.md) 참조

### 프론트엔드

Vue 3 + TypeScript + Vite 8 + Element Plus + Pinia 3, 자세한 개발 규범은 [docs/frontend-patterns.kr.md](docs/frontend-patterns.kr.md) 참조.

| 계층 | 디렉토리 | 역할 |
|------|------|------|
| API 계층 | `src/api/` | 도메인별 분리, 통합 Axios 인스턴스, 타입 안전 |
| Store 계층 | `src/stores/` | Pinia Composition API, Store당 하나의 도메인 |
| 라우터 계층 | `src/router/` | 지연 로딩 + 내비게이션 가드 |
| 뷰 계층 | `src/views/` | SFC 순서: script → template → style |
| 타입 계층 | `src/types/` | 공유 타입 단일 소스 (중복 정의 금지) |

## 개발 가이드 (신규 멤버 필독)

프로젝트는 **Harness 산업 설계 패턴**을 채택하며, 시스템 지식은 세 계층으로 나뉩니다: **Architecture → Patterns → Code**. 코드 수정 전, 해당 `docs/` 문서를 먼저 읽으세요.

| 규칙 | 설명 |
|------|------|
| 의존성 주입 | `@RequiredArgsConstructor` + `final` 필드, `@Autowired` 사용 금지 |
| 반환 값 | 모든 Controller는 `R<T>` 반환, 페이징은 `R<PageResult<T>>` 사용 |
| 예외 처리 | 비즈니스 예외는 `BusinessException` 발생, `GlobalExceptionHandler`에서 통합 처리 |
| 로깅 | `@Slf4j` + 파라미터화 플레이스홀더, `System.out.println` 사용 금지 |
| 권한 | 쓰기 작업은 반드시 `@PreAuthorize` 선언, 형식 `resource:action` |
| 프론트엔드 타입 | `ApiResponse`/`PageResult`는 `src/types/api.ts`에서만 가져오기 |
| 프론트엔드 컴포넌트 | SFC 순서: `<script setup>` → `<template>` → `<style scoped>` |

```bash
# 커밋 전 검증
cd omni-backend && ./mvnw clean install        # 백엔드 컴파일
cd omni-frontend && npm run build && npm run lint  # 프론트엔드 빌드 + Lint
```

> 전체 규범은 [docs/backend-patterns.kr.md](docs/backend-patterns.kr.md) 및 [docs/frontend-patterns.kr.md](docs/frontend-patterns.kr.md) 참조, API 계약은 [docs/api-contract.kr.md](docs/api-contract.kr.md) 참조

## 주의할 만한 함정

| 함정 | 설명 | 해결 방법 |
|------|------|---------|
| Gateway 라우팅 미작동 | 5.x 설정 접두사 변경 | `spring.cloud.gateway.server.webflux` 사용 |
| Maven class version 오류 | JAVA_HOME이 JDK 25를 가리키지 않음 | `JAVA_HOME`을 JDK 25 디렉토리로 설정 |
| Redis Starter 혼용 | 블로킹 방식을 WebFlux 서비스에 도입 | Gateway는 `omni-common-redis-reactive`만 사용 가능 |
| Docker 502 오류 | Nginx proxy_pass 포트 오류 | 컨테이너 간 통신은 내부 포트 `8080` 사용, 호스트 매핑 포트 아님 |
| Docker 포트 충돌 | 다른 프로세스나 Compose 프로젝트가 포트를 사용 중 | `dev status`로 충돌 프로젝트를 찾아 중지하며 관리자 권한은 불필요 |
| Nacos 헬스체크 실패 | v3.1.1 엔드포인트 변경 | `GET /nacos/` 사용, `/nacos/actuator/health` 아님 |
| 프론트엔드 타입 불일치 | `ApiResponse` 다중 정의 | `@/types/api`에서만 가져오기 |
| Stream 컨슈머 OFFLINE | function.definition 네임스페이스 오류 | `spring.cloud.stream.function`이 아닌 `spring.cloud.function` 아래 배치 |

## AI 네이티브 프로젝트

- **`AGENTS.md`**: AI 실행 매뉴얼, 하드 제약 + 실행 규칙 + 완료 체크리스트
- **`docs/`**: 시스템 진실 문서, AI가 코드 수정 전 시스템 맥락을 이해하기 위해 먼저 읽어야 할 문서
- **`.qoder/skills/`**: AI 행동 확장 유닛 (예: `/grill-me` 솔루션 스트레스 테스트)

> **첫 두 계층 (Architecture + Patterns)을 고정해야, 세 번째 계층 (Code)을 안심하고 AI에게 고속 생산을 맡길 수 있습니다.**

## 라이선스

[Apache License 2.0](LICENSE)

---

## 프로젝트 지원

이 프로젝트가 도움이 되었다면, Star를 눌러 지원해 주세요!

**GitHub**: [https://github.com/wang-baohai/Omni-Stack](https://github.com/wang-baohai/Omni-Stack)
**Gitee**: [https://gitee.com/wang-baohai/Omni-Stack](https://gitee.com/wang-baohai/Omni-Stack)

여러분의 [PR](https://github.com/wang-baohai/Omni-Stack/pulls)을 기대합니다!

---

**© Wang Baohai**

<!-- omni:preset-table:start -->
## 프로젝트 프리셋

| 프리셋 | 명시 모듈 | 의존성 클로저 |
|---|---|---|
| core | base, gateway, mysql, redis, nacos | platform, auth, base, nacos, gateway, mysql, redis |
| crm | crm, gateway, mysql, redis, nacos | platform, auth, base, crm, nacos, gateway, mysql, redis |
| full | crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| supply-chain | asset, gateway, mysql, redis, nacos, rocketmq, xxl-job | platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq |
| workflow | workflow, gateway, mysql, redis, nacos | platform, auth, base, workflow, nacos, gateway, mysql, redis |

[선택 가이드](docs/preset-quick-selection.kr.md) · [프리셋 의존성 매트릭스](docs/preset-dependency-matrix.kr.md)
<!-- omni:preset-table:end -->
