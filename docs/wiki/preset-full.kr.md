# full 프리셋: 현재 저장소의 모든 코어와 업무 기능

full 프리셋은 Omni-Stack 현재 기능의 전체 집합입니다. 인증과 기반 플랫폼, 워크플로, CRM, SRM, 구매, 자산, 메시징과 스케줄링을 동시에 활성화합니다. 엔드투엔드 인수, 모듈 횡단 데모, 모든 진입점을 한 번에 봐야 하는 팀에 맞습니다. 자원 제약 환경, 단일 업무 도메인만 검증하는 경우, "컨테이너 전부 기동"을 "업무 전부 폐루프"로 오해하는 상황에는 맞지 않습니다. 이 페이지의 구성은 현재 `scaffold/presets/full.yaml`, `scaffold/catalog/modules.yaml`, `preset explain full`의 실제 해석에서 가져오며, 실행 결과는 검증됨/미검증으로 나눠 표시합니다.

이 페이지의 실행 결과는 커버리지 축소 결함 수정 전 단계별 기록이며 현재 버전의 결과가 아닙니다. 초기 실패와 미완료는 보존하고 이후 성공으로 이전 단계를 소급 승인하지 않습니다. 이전 G7 통과만으로 현재 전체 요구사항을 증명할 수 없으므로 릴리스에는 모든 적용 요구사항을 보존한 G6/G7과 필요한 G11 재검증이 필요합니다.

## 누구에게 맞는가

- 인증, 테넌트, RBAC, 워크플로, CRM, 공급망, 자산 진입점을 한 번에 검증하려는 팀.
- full로 공유 데이터베이스를 초기화한 뒤 다른 프리셋을 원위치 하위 집합으로 검증하려는 유지보수 담당자.
- 모듈 횡단 메뉴, 권한, 데이터 범위, 메시징/스케줄링 협업을 점검하려는 인수 담당자.

맞지 않는 경우: 로그인과 기반 관리만 먼저 통과시키려는 경우(core), 영업만(CRM), 공급업체/구매/자산만(supply-chain), 승인만(workflow). full은 자원 점유, 기동 시간, 장애 표면이 모두 최대입니다.

## 명시 8개 모듈에서 폐쇄 14개 모듈로

`scaffold/presets/full.yaml`의 명시 모듈:

```text
crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain full`가 현재 해석하는 폐쇄:

```text
platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

추가되는 `srm`, `procurement`, `workflow`는 `asset`의 의존성 체인(`asset → procurement, srm → workflow → base`)에서 오고, `auth`와 `platform`은 `base`의 의존성에서 옵니다. 즉 `asset`을 쓰면 공급망 전체와 승인 런타임이 full에 들어옵니다. `crm`은 명시적으로 추가한 독립 업무 도메인입니다.

## 실제 서비스와 포트

14개 모듈은 다음 16개 Compose 서비스로 대응됩니다.

| 서비스 | 출처 모듈 | 역할 |
|---|---|---|
| `mysql` | mysql | 플랫폼과 각 업무 데이터 |
| `redis` | redis | 캐시와 토큰 관련 데이터 |
| `nacos` | nacos | 서비스 디스커버리와 설정 센터 |
| `rocketmq-namesrv` | rocketmq | 메시지 라우팅 |
| `rocketmq-broker` | rocketmq | 메시지 저장과 전달 |
| `xxl-job-admin` | xxl-job | 스케줄링 센터 |
| `omni-db-migrator` | platform | 1회성 스키마 마이그레이션과 시드 초기화 |
| `omni-auth` | auth | 로그인, 테넌트, RBAC, 조직, 사전, 감사 |
| `omni-base` | base | 기반 관리, 로그, 워크스페이스와 작업 API |
| `omni-workflow` | workflow | BPMN 모델, 할 일, 승인과 프로세스 인스턴스 |
| `omni-crm` | crm | 리드, 고객, 연락처, 기회, 후속 조치와 영업 파이프라인 |
| `omni-srm` | srm | 공급업체, 등록 심사, 평가, 리스크와 포털 |
| `omni-procurement` | procurement | 자재, 신청, 승인 경로, RFQ, 주문과 입고 |
| `omni-asset` | asset | 자산 대장, 카드 생성, 지급/반납, 이전과 처분 |
| `omni-gateway` | gateway | 통합 진입점과 라우팅 |
| `omni-frontend` | platform | Vue 프런트엔드 |

catalog는 35건의 포트 기록, 34개의 고유 포트를 선언합니다(`8080`은 nacos와 xxl-job이 동시 선언).

| 모듈 | 선언 포트 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| crm | 8104, 19904, 9904 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

프런트엔드 진입점은 3000이고 업무 서비스는 CRM 8104, SRM 8105, 구매 8106, 자산 8107, 워크플로 8103입니다. `xxl-job`과 nacos는 둘 다 8080을 선언하며, 이 저장소 샘플 환경은 `OMNI_XXL_JOB_HOST_PORT=18080`으로 스케줄링 센터를, `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876`으로 NameServer를 매핑합니다(`.env.example`의 명시 변수 참조). 무작위 오프셋이 아닙니다.

## 이득과 대가

이득은 모든 업무 도메인을 한 번에 얻는 것입니다. 동일한 테넌트, RBAC, 데이터 범위, 감사, 워크플로, 메시징, 스케줄링이 CRM 영업 체인과 공급망·자산 체인을 모두 커버합니다. 모듈 횡단 메뉴, 권한, 데이터 격리 인수에 맞고 다른 프리셋의 공유 데이터베이스 초기화 출처로도 쓰입니다.

대가는 최대입니다.

- catalog `resourceHints`를 합산하면 최소 메모리 힌트 7296 MB, 권장 11776 MB입니다. Quick Start가 full에 제안하는 초기 값은 12–16 GiB이며, 이는 준비 예산이지 실측 상한이 아닙니다. 빌드 피크, MySQL/RocketMQ 캐시, JVM 힙이 겹칩니다.
- 16개 서비스, 34개 고유 포트, 5개 업무 앱, 1개 메시지 브로커(NameServer + Broker), 1개 스케줄링 센터. 기동, 헬스체크, 로그, 장애 격리 표면이 최대입니다.
- 업무 선행 조건이 가장 많습니다. CRM 영업 데이터, SRM 공급업체 등록 심사와 포털, 구매 승인 경로/주문/입고, 자산 카드 생성과 이전/처분 규칙, 워크플로 모델 게시와 후보자, 메시지 릴레이와 스케줄 실행.
- "모든 컨테이너 healthy"는 서비스가 응답한다는 뜻일 뿐, 모듈 횡단 업무 체인이 폐루프되었음을 증명하지 않습니다.

## 전제와 설정

- **Docker와 자원**: 메모리를 12–16 GiB로 준비하고 디스크를 확보하세요. 3000, 8100–8107, 3306, 6379, 8080, 8848, 9848, 9876 포트가 비어 있는지 확인합니다(스케줄러와 NameServer의 호스트 매핑은 `.env` 명시 변수로 대조).
- **사유 `.env`**: 데이터베이스, Redis, Nacos 인증, 게이트웨이 단언, `XXL_JOB_ACCESS_TOKEN`, `XXL_JOB_ADMIN_PASSWORD` 등. 실제 값을 커밋하지 마세요.
- **Nacos 설정**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-crm.yml`, `omni-srm.yml`, `omni-procurement.yml`, `omni-asset.yml`, `omni-gateway.yml`. 인증이 켜져 있으면 모든 클라이언트가 동일한 계정/비밀번호를 사용합니다.
- **마이그레이션과 시드**: `omni-db-migrator`는 1회 실행입니다. 공유 DB가 이미 초기화되었다면 재가져오기, 마이그레이션 재생, DB 삭제를 하지 마세요.
- **신원**: 관리자, `CRM_ADMIN`/`SALES_MANAGER`/`SALES_REP`/`CRM_VIEWER`, `SRM_ADMIN`/`PROCUREMENT_MANAGER`/`PROCUREMENT_STAFF`, `SUPPLIER`, 그리고 자산과 워크플로 관련 역할. 공급업체 포털은 포털 연결과 초대도 필요합니다.
- **승인과 메시지**: 승인 런타임은 `omni-workflow`에 있습니다. Outbox 릴레이는 `xxl-job`에, 업무 이벤트는 RocketMQ에 의존합니다. 스케줄러가 준비되지 않았으면 메시지 작성은 전달 완료가 아닙니다.
- **업무 데이터**: CRM 영업 체인, 공급업체 등록 심사, 구매 신청/승인/주문/입고, 자산 카드 생성과 이전/처분. 컨테이너 기동은 이 선행 조건이 끝났다는 뜻이 아닙니다.

## 경로 1: 소스에서 full 기동

### 깨끗한 데이터베이스 최초 설치

```bash
docker compose --parallel 1 --profile full up -d --build
docker compose ps
```

예상: migrator가 실행 후 종료되고, mysql/redis/nacos/rocketmq/xxl-job과 9개 앱 서비스가 모두 기동되며, 앱은 healthy이고 프런트엔드 3000에 접근할 수 있습니다. 이 경로는 마이그레이션과 시드를 실행하므로 빈 DB나 초기화가 명시적으로 허용된 환경에만 씁니다. **이 페이지에서는 이 경로를 실측하지 않았습니다**: 검증 환경은 full로 이미 초기화된 공유 DB를 사용하므로 빈 DB 최초 설치는 미검증 표기를 유지합니다.

### 기존 공유 환경에서의 원위치 전체 교체

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: 9개 앱 이미지를 빌드합니다. migrator를 기동하지 않고 인프라를 재빌드하지 않습니다.
- `up -d --no-build --no-deps --force-recreate`: 9개 앱을 원위치 교체합니다. 의존 서비스를 기동하지 않고 마이그레이션도 실행하지 않습니다.
- `ps`: 실제 이미지와 상태를 대조합니다. healthy는 응답만 의미합니다.
- `curl`: 프런트엔드 200을 확인합니다. 토큰 없는 게이트웨이 업무 라우트는 503이 아니라 401이어야 합니다.

full에는 "폐쇄 밖 앱"이 없지만, 실제로 등록된 서비스 집합으로 대조하세요. "깨끗함"을 이유로 인프라를 중지하거나 볼륨을 지우지 마세요.

## 경로 2: 독립 full 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create full --output generated/learning-full
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate full --output generated/learning-full
```

생성물은 자체 `scaffold.lock`, Compose, 문서, AI 진입점을 가집니다. 생성물에 들어간 뒤 자체 빌드 명령을 사용하고 원 저장소에서 생성물의 Compose 프로젝트를 기동하지 마세요. 생성물 migrator를 공유 DB에 실행하지 마세요.

과거 초기 단계: 소스 경로는 9개 앱 이미지 빌드와 원위치 교체, 상태/프런트엔드 200/실제 CAPTCHA 로그인/전 도메인 메뉴 대조/중지 복원까지 완료했습니다. 생성 경로는 고정 위치 생성(G6 14개 모듈/16개 서비스), G7 생성물 자체 빌드, 배달 역할(생성물과 캐시 하위 경로만 마운트, 소스 없음, docker socket 없음) 자체 충분성, 생성 이미지 9개의 원위치 교체와 실제 로그인/메뉴/워크스페이스 검증, 이후 저장소 이미지 복원까지 완료했습니다. 배달 게이트 `verify-preset-delivery full`는 `automated-delivery-pass`(modules=14)이지만 `release_ready=false`: 생성물 문서의 번역문은 지정 검토 대기입니다. 깨끗한 빈 DB 최초 설치는 여전히 미실측입니다.

## 최초 사용

1. `http://localhost:3000`을 열고 개발 테넌트를 선택한 뒤 `admin`과 **이번에 표시된 CAPTCHA**로 로그인합니다. 오래된 CAPTCHA를 재사용하거나 인증 백도어를 쓰지 마세요.
2. Wiki 홈은 기존 탭에 남기고 내 워크스페이스를 별도 탭으로 엽니다. 앱 디렉터리와 검색은 접근 가능한 전체 메뉴를 대상으로 합니다. 디렉터리에서 CRM·SRM·구매·자산·워크플로·시스템/기반 모듈을 새 탭으로 엽니다. 업무 화면의 메뉴와 검색은 현재 모듈로 한정하며 전체 앱 버튼은 없습니다.
3. 모든 페이지를 한 번에 펼치지 말고 도메인별로 검증합니다.
   - CRM: 리드 → 후속 조치 → 전환 → 기회 단계 → 수주/실패.
   - 공급망: 공급업체 등록 심사/자재 → 신청 승인 → 주문 → 입고 → 자산 카드 생성.
   - 승인: 모델 게시 → 시작 → 할 일 처리 → 인스턴스 이력.
   - 포털: 공급업체 포털 신원은 **정식 초대 → 셀프 등록 → 등록 Saga**로 확보합니다(이번 실측: 등록 25초 내 COMPLETED, 포털 연결 ACTIVE, 재로그인, `/supplier-portal` 3개 탭 사용 가능, 내부 관리측 403). `supplier1`/`supplier2`의 비밀번호는 시드에 해시로만 존재해 저장소에서 복원할 수 없으므로 추측하거나 우회하지 않습니다. 두 번째 포털 신원이 필요하면 초대/등록/등록 체인을 다시 수행하세요.
4. 메뉴는 보이는데 목록이 비어 있으면 테넌트/데이터 범위와 업무 선행 조건을 먼저 확인합니다. 메뉴가 없으면 역할 권한과 `/api/auth/menus`를 확인합니다. DB 메뉴나 역할을 직접 편집하지 마세요.
5. 메시징과 스케줄링: `xxl-job`이 준비된 뒤 Outbox 릴레이를 검증합니다. "메시지 테이블에 행이 있음"을 "전달 완료" 대신 쓰지 마세요.

## 성공 기준

- full 폐쇄의 9개 앱과 인프라가 healthy이고 migrator가 예상대로 종료됨.
- 프런트엔드 3000이 200을 반환하고 실제 로그인이 성공하며 각 업무 메뉴가 권한에 따라 나옴.
- CRM, SRM, 구매, 자산, 워크플로 각각에 열 수 있는 실제 진입점이 최소 1개 있고 권한이 있는 신원이 시드 데이터를 읽을 수 있음.
- 모듈 횡단 체인은 실제 API 결과로 판단하며 헬스체크는 업무 폐루프가 아님.
- 새 프로젝트, 네트워크, 볼륨, 포트 오프셋이 없고 공유 데이터를 지우거나 마이그레이션을 재생하지 않음.

**메뉴 경계**: 메뉴 API는 "테넌트 권한 트리 ∩ 현재 신원 권한 코드"만 반환하며 어떤 서비스가 실행 중인지와 무관합니다. full로 초기화한 공유 DB에서 모든 업무 메뉴가 보이는 것은 예상대로입니다. 다른 프리셋으로 교체해도 업무 권한 코드는 관리자 등 역할에 부여된 채 남으므로 메뉴에 업무 진입점이 잔존합니다(실측: core 창 57개 노드 중 36개가 업무 코드이며 공유 DB 부여 조회와 일치). 이는 공유 데이터 상태이지 해당 프리셋 폐쇄가 모든 업무를 포함한다는 증거가 아닙니다. core 시드에는 업무 권한 행이 없습니다. workflow·CRM·supply-chain은 선택하지 않은 업무 모듈의 권한을 제거하고 선택한 업무의 권한은 유지합니다. 빈 DB 최초 설치에는 선택하지 않은 모듈의 메뉴가 남지 않아야 합니다. 하위 집합의 "다른 메뉴 없음" 실행 시 검증은 독립 빈 DB/테넌트가 필요합니다. 공유 DB에서 권한을 지우거나 역할을 바꿔 모의하지 마세요.

## 중지와 복원

- 이번에 기동한 앱만 중지합니다. mysql/redis/nacos/rocketmq/xxl-job과 데이터 볼륨은 유지합니다.
- 복원: 등록된 원 서비스 집합을 `docker start`, 또는 명시 서비스에 `up -d --no-build --no-deps`. `down -v`, 볼륨 삭제, 전역 prune은 금지.
- 교체 실패 시 교체 전에 기록한 이전 이미지를 같은 서비스 위치에서 복원하고 우회 서비스를 기동하지 않습니다.

## 흔한 실패

| 현상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 포트 사용 중 | 3000/8100–8107/3306/6379/8080/8848/9848/9876 | 폐쇄 변경이나 무작위 포트 오프셋 |
| Nacos 인증 실패 | `.env`와 Nacos 자격 증명 일치 여부 | 인증 비활성화, 샘플 비밀 입력 |
| 게이트웨이 503 | 단언 키, 하위 등록, 라우트 설정 | 프런트엔드 URL만 변경 |
| 특정 업무 메뉴 누락 | 역할 권한, `/api/auth/menus`, 해당 서비스 상태 | DB 메뉴/역할 직접 편집 |
| 페이지는 열리나 목록이 비어 있음 | 테넌트, 데이터 범위, 업무 선행 조건 | 빈 데이터를 기동 실패로 보거나 수작업으로 행 생성 |
| 승인 할 일이 비어 있음 | 모델 게시, 후보자, 역할, 작업 생성 | 작업/후보자 테이블 직접 편집 |
| 입고 후 자산 카드 없음 | 검사 합격, 자산 관리 플래그, 양의 정수 수량 | 자산 카드 직접 삽입 |
| 메시지가 오래 PENDING | `xxl-job` 릴레이, RocketMQ, max_retry | PENDING을 전달 완료로 표기 |
| 마이그레이션 이중 실행 | 공유 DB가 초기화되었는지 | DB 삭제나 시드 재가져오기 |
| Maven 다운로드 `Remote host terminated the handshake` | 기업 네트워크 TLS 변동. 같은 명령을 나중에 재시도 | 의존 버전이나 저장소 좌표 변경 |
| `npm ci` ECONNRESET | 네트워크 변동. 재시도하고 오프라인 캐시 유지 | lockfile 삭제나 lint 건너뛰기 |
| 컨테이너 내 다운로드 전부 실패(DNS/핸드셰이크) | 호스트 프록시로 `HTTP_PROXY/HTTPS_PROXY` 설정(컨테이너 내 `host.docker.internal:<혼합 포트>`), 이미지 빌드에는 동일 이름 `--build-arg` | 제품이나 프리셋 탓으로 돌리기 |
| 검증 컨테이너에 `docker`/BuildKit 없음 | docker CLI와 buildx 플러그인 설치 후 `DOCKER_BUILDKIT=1` | 생성물을 호스트 임시 디렉터리로 복사해 빌드 |
| 컨테이너 재빌드마다 Maven 로컬 저장소 재다운로드 | `-Dmaven.repo.local`를 고정 검증 볼륨 캐시로 지정 | 이미지에 사유 저장소 설정을 굽기 |

## 과거 단계별 기록과 검증 범위

- **검증됨**: `preset explain full`가 명시 8개/폐쇄 14개 모듈을 해석. 16개 서비스, 35건 포트 선언/34개 고유 포트는 catalog 출처. 자원 힌트는 모듈 합산 7296/11776 MB. 소스 경로 9개 앱 이미지 빌드/원위치 교체/상태/프런트엔드 200/실제 로그인/전 도메인 메뉴/중지 복원. 생성 경로 G6/G7, 배달 역할 자체 충분성, 생성 이미지 9개 원위치 교체와 실제 진입점 검증, 저장소 이미지 복원. 배달 게이트 `automated-delivery-pass`. Outbox 릴레이와 스케줄 실행은 포털 등록 Saga(25초 내 역할 부여 COMPLETED)로 실증.
- **미검증**: 깨끗한 빈 DB 최초 설치. 모듈 횡단 업무 체인의 심층 흐름(CRM 전환/수주, 공급망 주문 입고 카드 생성, 다단계 합의를 포함한 승인 엔드투엔드). 공개 준비 완료(`release_ready=false`, 번역문 지정 검토 대기).

이어서 [프리셋 개요](presets-overview.kr.md), [기능 투어](feature-tour.kr.md), [아키텍처 사실](../architecture.kr.md)을 읽으세요.
