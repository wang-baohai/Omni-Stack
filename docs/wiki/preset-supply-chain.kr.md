# supply-chain 프리셋: 공급업체, 구매, 자산, 승인

supply-chain 프리셋은 인증과 기반 플랫폼 위에 SRM, 구매, 자산, 워크플로, 메시지, 스케줄링을 한 번에 추가합니다. "공급업체 진입 → 구매 요청과 승인 → 견적/주문/입고 → 자산 카드 생성 → 이동/폐기" 협업 체인을 대상으로 하며, 여러 역할의 업무 흐름을 검증하려는 팀에 맞습니다. 이 저장소에서도 구성이 많고 업무 전제가 복잡한 프리셋입니다. 이 페이지의 모듈, 서비스, 포트, 리소스는 현재 `scaffold/presets/supply-chain.yaml`, `scaffold/catalog/modules.yaml`, `preset explain supply-chain`의 실제 해석 결과입니다. 실행 결과는 검증됨/미검증으로 나눕니다.

이 페이지의 실행 결과는 커버리지 축소 결함 수정 전 단계별 기록이며 현재 버전의 결과가 아닙니다. 초기 실패와 미완료는 보존하고 이후 성공으로 이전 단계를 소급 승인하지 않습니다. 이전 G7 통과만으로 현재 전체 요구사항을 증명할 수 없으므로 릴리스에는 모든 적용 요구사항을 보존한 G6/G7과 필요한 G11 재검증이 필요합니다.

## 누구에게 맞는가

- 공급업체, 구매, 자산, 승인을 하나의 실제 체인으로 연결하려는 팀.
- 내부 직원, 구매/승인자, 공급업체 포털의 세 신원과 데이터 범위를 검증해야 하는 플랫폼.
- MQ와 스케줄링을 업무(Outbox 중계, 테넌트 구성, 사용자 작업)에 참여시키려는 팀.

맞지 않는 경우: CRM만 필요(CRM 프리셋), BPMN 승인만 필요(workflow 프리셋), 리소스가 제한되어 로그인과 RBAC만 먼저 검증하려는 환경. supply-chain은 "페이지 몇 장이 더 있는" 것이 아니라 여러 서비스, 여러 신원 영역, 여러 단계의 전제 데이터를 동반합니다.

## 명시 7개에서 폐쇄 13개로

`scaffold/presets/supply-chain.yaml`의 명시 모듈:

```text
asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

`preset explain supply-chain`이 현재 해석하는 폐쇄:

```text
platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

의존 체인이 `asset` 하나로 공급망 전체가 들어오는 이유를 설명합니다.

```text
asset → procurement, srm
procurement → srm
srm → workflow
workflow → base
gateway → base
base → auth → platform
```

`platform`은 마이그레이션, 프론트엔드, 공통 기능을 제공하고, `auth`는 로그인/테넌트/RBAC, `base`는 기반 관리와 작업 API, `workflow`는 승인 런타임, `srm → procurement → asset`은 업무 주축입니다. `rocketmq`와 `xxl-job`은 명시적으로 선택된 것이며 무시할 수 있는 옵션이 아닙니다. Outbox 중계, 테넌트 구성, 사용자 작업은 스케줄링에 의존하고 업무 이벤트는 메시지에 의존합니다.

## 실제 서비스와 포트

13개 모듈은 15개 Compose 서비스에 대응합니다.

| 서비스 | 원천 모듈 | 역할 |
|---|---|---|
| `mysql` | mysql | 기반과 업무 데이터 |
| `redis` | redis | 캐시와 토큰 관련 |
| `nacos` | nacos | 서비스 발견과 설정 |
| `rocketmq-namesrv` | rocketmq | 메시지 라우팅 |
| `rocketmq-broker` | rocketmq | 메시지 저장과 전달 |
| `xxl-job-admin` | xxl-job | 스케줄링 센터 |
| `omni-db-migrator` | platform | 일회성 스키마 마이그레이션과 시드 |
| `omni-auth` | auth | 로그인, 테넌트, RBAC, 조직, 사전, 감사 |
| `omni-base` | base | 기반 관리, 로그, 워크벤치, 작업 API |
| `omni-workflow` | workflow | BPMN 모델, 작업, 승인, 프로세스 인스턴스 |
| `omni-srm` | srm | 공급업체, 진입, 평가, 리스크, 포털 |
| `omni-procurement` | procurement | 자재, 요청, 승인 라우트, 견적, 주문, 입고 |
| `omni-asset` | asset | 자산 대장, 카드 생성, 수령/반납, 이동, 폐기 |
| `omni-gateway` | gateway | 통합 진입점과 라우팅 |
| `omni-frontend` | platform | Vue 프론트엔드 |

catalog는 포트 선언 32건, 고유 포트 31개를 가집니다(`8080`은 nacos와 xxl-job이 모두 선언).

| 모듈 | 선언 포트 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

프론트엔드 진입점은 3000이고 업무 서비스는 workflow 8103, SRM 8105, 구매 8106, 자산 8107입니다. `xxl-job`과 nacos는 모두 8080을 선언합니다. 이 저장소의 예시 환경은 `OMNI_XXL_JOB_HOST_PORT=18080`으로 스케줄링 센터를, `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876`으로 NameServer를 매핑합니다. `.env.example`에 등록된 명시적 매핑이며 임의 오프셋이 아닙니다. 포트 충돌 시 점유를 해제하거나 명시 변수를 조정하고 프리셋 폐쇄를 바꾸지 마십시오.

## 이점과 비용

이점은 공급업체 마스터와 진입, 구매 요청과 승인, 견적/주문/입고, 자산 카드 생성과 이후 이동/폐기가 같은 테넌트, 권한, 데이터 범위, 감사를 공유한다는 것입니다. MQ와 스케줄링도 있어 Outbox 중계와 사용자 작업을 직접 검증할 수 있습니다.

비용은 많은 리소스와 복잡한 업무 전제가 필요하다는 점입니다.

- catalog `resourceHints` 합계는 최소 메모리 6784MB, 권장 11008MB입니다. 계획용 안내이며 실측 상한이 아닙니다. 빌드 피크, MySQL/RocketMQ 캐시, JVM 힙이 추가됩니다. 실제 사용량은 `docker stats`와 빌드 로그로 확인하십시오.
- 워크플로 런타임, 메시지 미들웨어(NameServer + Broker), 스케줄링 센터가 늘어 포트, 자격 증명, 헬스 체크, 로그 면이 크게 증가합니다.
- 업무는 "로그인하면 실행"되는 것이 아닙니다. 공급업체는 진입/초대와 포털 연결, 구매 요청은 승인 라우트와 워크플로 모델, 입고는 권한 있는 주문과 품질 결과, 자산 카드 생성은 수량과 자산 관리 플래그 조건을 충족해야 합니다.
- 신원은 내부 사용자와 공급업체 포털 사용자로 나뉩니다. 로그인할 수 있어도 `SUPPLIER` 역할과 유효한 포털 연결이 없으면 포털 프로필과 평가(`srm:portal:profile` / `srm:portal:evaluation`)에 접근할 수 없습니다. 일반 `USER`는 가입 신청만 할 수 있습니다.

## 전제와 설정

- **Docker와 리소스**: 이 페이지의 메모리/디스크 안내 이상을 확보하고 3000, 8100, 8101, 8102, 8103, 8105, 8106, 8107, 3306, 6379, 8080, 8848, 9848, 9876 등의 가용성을 확인합니다. 스케줄링과 NameServer 호스트 매핑은 `.env` 명시 변수로 확인합니다.
- **비공개 `.env`**: 데이터베이스, Redis, Nacos 인증, 게이트웨이 검증, `XXL_JOB_ACCESS_TOKEN`, `XXL_JOB_ADMIN_PASSWORD` 등. 실제 값을 커밋하지 말고 예시 값을 비밀로 취급하지 마십시오.
- **Nacos 설정**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-srm.yml`, `omni-procurement.yml`, `omni-asset.yml`, `omni-gateway.yml`. Nacos 인증을 켜면 모든 클라이언트가 같은 자격 증명을 사용합니다.
- **마이그레이션과 시드**: `omni-db-migrator`가 한 번 실행합니다. 공유 데이터베이스가 이미 초기화되었다면 다시 가져오거나 재실행하거나 초기화하지 마십시오.
- **내부 신원**: 관리자와 `SRM_ADMIN`, `PROCUREMENT_MANAGER`, `PROCUREMENT_STAFF` 등 시드 역할. 승인은 워크플로 후보자와 데이터 범위에도 의존합니다.
- **공급업체 포털 신원**: `auth.sql`/`srm.sql`이 테넌트 1에 `supplier1`, `supplier2`와 공급업체 1/2 포털 연결을 제공하며 역할은 `SUPPLIER`입니다. 포털 진입에는 `inviteToken`과 클라이언트 `requestId`가 필요합니다. 유효한 초대나 연결이 없으면 데이터베이스를 직접 편집해 포털 사용자를 "만들지" 마십시오.
- **승인 모델**: 승인 런타임은 `omni-workflow`에 있고 구매/자산 서비스는 Flowable을 내장하지 않습니다. 승인 시작에는 게시된 모델과 해석 가능한 후보자/역할이 필요합니다.
- **메시지와 스케줄링**: RocketMQ와 `xxl-job`은 폐쇄 안에 있습니다. Outbox 메시지에는 중계 작업이 필요하며, 스케줄러가 준비되지 않았을 때 "기록됨"을 "전달됨"으로 취급하지 마십시오.
- **업무 데이터**: 공급업체 진입/평가, 자재 카탈로그, 구매 요청과 승인 라우트, 주문, 입고, 자산 수량과 품질 결과. 컨테이너 기동은 이것들이 준비되었다는 뜻이 아닙니다.

### 기동과 업무 준비는 다릅니다

헬스 체크 통과는 서비스 응답만 의미합니다. 다음을 증명하지 않습니다.

- 공급업체가 진입을 통과하고 포털 사용자가 연결되었다.
- 워크플로 모델이 게시되어 승인자에게 작업이 도달한다.
- 구매 요청이 승인되어 주문을 입고할 수 있다.
- 입고 행이 "품질 합격 + 자산 관리 + 양의 정수 수량"을 충족해 자산 카드가 생성된다.
- Outbox 메시지가 중계로 전달되었다.

이것들은 업무 순서로 준비·검증하며 데이터베이스 상태 직접 변경으로 우회하지 마십시오.

## 경로 1: 소스에서 supply-chain 기동

### 깨끗한 데이터베이스에 첫 설치

공유 데이터가 초기화되지 않은 독립 환경에서:

```bash
docker compose --parallel 1 --profile supply-chain up -d --build
docker compose ps
```

예상: migrator가 실행 후 종료되고 mysql/redis/nacos/rocketmq/xxl-job과 auth/base/workflow/srm/procurement/asset/gateway/frontend가 기동, 애플리케이션 서비스가 healthy, 프론트엔드 3000 접속 가능. 이 경로는 마이그레이션과 시드를 실행하므로 빈 데이터베이스 또는 초기화가 명시적으로 허용된 환경에만 사용합니다.

### 기존 공유 환경의 원위치 부분집합

데이터베이스가 full로 초기화되었다면 migrator를 다시 실행하지 않습니다. 8개 애플리케이션 이미지를 빌드하고 폐쇄 밖의 CRM을 중지한 뒤 원위치 교체합니다.

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: 폐쇄 안 8개 애플리케이션 이미지만 빌드하고 migrator를 시작하지 않으며 mysql/redis/nacos/rocketmq/xxl-job도 다시 빌드하지 않습니다.
- `docker stop`: 폐쇄 밖 CRM을 중지해 같은 환경에 여러 업무 실행면이 남지 않게 합니다. 기반과 데이터 볼륨은 유지합니다.
- `up -d --no-build --no-deps --force-recreate`: 새 이미지로 8개 애플리케이션을 원위치 교체하고 의존 서비스를 시작하지 않으며 마이그레이션도 실행하지 않습니다.
- `ps`: 실제 이미지와 건강을 확인합니다. healthy는 프로세스와 헬스 체크 통과만 의미하며 업무 체인 준비 완료가 아닙니다.
- `curl`: 프런트엔드의 200을 확인합니다. 토큰 없는 업무 요청의 401은 인증 거절만 증명하며 서비스와 라우트의 가용성은 인가된 요청 성공 및 등록·상태 증거도 필요합니다.

중지 목록은 실제 등록된 서비스로 채우십시오. mysql/redis/nacos/rocketmq/xxl-job과 데이터를 유지하고 `down -v`나 볼륨 초기화를 실행하지 마십시오.

## 경로 2: 독립 supply-chain 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

- `preset create`: supply-chain을 독립 프로젝트로 줄이고 자체 `scaffold.lock`, Compose, 문서, Maven, 프론트엔드 소스를 포함합니다.
- `preset validate`: 생성물의 모듈 폐쇄, Compose, 문서, AI 진입점, 절단 경계를 검증하며 애플리케이션을 시작하지 않습니다.
- 생성물에는 자체 빌드 명령이 있습니다. 생성물 안에서 사용하고 원래 저장소에서 생성물 Compose를 시작하거나 생성물 migrator로 공유 데이터베이스를 덮어쓰지 마십시오.

과거 초기 단계: G6 구조 PASS(13개 모듈/15개 서비스). G7은 생성물 백엔드 BUILD SUCCESS, 프론트엔드 build/lint PASS(`VERIFY_PASS modules=13 services=15`). 전달 역할 격리(소스/socket 미마운트)와 생성물 이미지의 G11 원위치 교체는 실행하지 않았습니다. 따라서 "생성물 이미지가 같은 환경에서 실행되었다"고 쓰지 않습니다.

## 첫 사용(업무 순서)

1. `http://localhost:3000`을 열고 개발 테넌트를 선택한 뒤 `admin`과 **이번에 표시된 CAPTCHA**로 내부에 로그인합니다. 이전 CAPTCHA를 재사용하거나 인증 우회를 쓰지 마십시오.
2. Wiki 홈은 원래 탭에 남기고 내 워크스페이스를 별도 탭으로 엽니다. 앱 디렉터리에서 SRM·구매·자산·워크플로를 각각 새 탭으로 엽니다. 각 업무 화면에는 현재 모듈의 접근 가능한 메뉴만 표시하고 승인 작업은 워크스페이스에 모읍니다.
3. 업무를 순서대로 준비합니다: 공급업체 진입/평가 → 자재와 승인 라우트 → 요청 제출과 승인 → 견적/주문 → 입고 확인 → 자산 카드 생성 → (선택) 이동/폐기 승인. 각 단계는 실제 API가 반환한 ID와 상태를 기준으로 합니다.
4. 공급업체 포털: `supplier1` 또는 `supplier2`(테넌트 1, `SUPPLIER` 역할, 공급업체 1/2 연결)로 포털에 로그인합니다. 포털 진입에는 유효한 `inviteToken`과 클라이언트 `requestId`가 필요합니다. `srm_supplier_portal_user` 직접 기록이나 소유 변경으로 우회하지 마십시오.
5. 승인: 작업 목록이 비어 있으면 모델 게시 여부, 현재 사용자가 후보자 범위인지, 작업이 생성되었는지 확인합니다. 작업/후보자 테이블을 직접 수정하지 마십시오.
6. 입고와 카드 생성: `qualityStatus=PASS`, `assetManaged=true`, 양의 정수 `assetQuantity`를 충족하는 입고 행만 자산 카드를 만듭니다. "카드 없음"은 업무 규칙이며 서비스 장애가 아닙니다.
7. 메뉴가 없으면 역할 권한과 `/api/auth/menus`를, 페이지는 열리지만 목록이 비면 테넌트/데이터 범위와 전제 데이터를 확인합니다. 먼저 데이터베이스를 변경하지 마십시오.

## 성공 기준

- supply-chain 폐쇄 애플리케이션 서비스가 healthy, migrator가 예상대로 종료, RocketMQ와 xxl-job이 healthy.
- 프론트엔드 3000이 200, 내부 로그인 성공, 권한에 따라 SRM/구매/자산/워크플로 메뉴 표시.
- 공급업체 포털 신원이 로그인해 자기 포털 데이터만 볼 수 있음. 내부와 포털을 별도로 검증.
- 실제 체인을 순서대로 완료: 공급업체/자재 준비 → 요청 승인 → 주문 → 입고 → 자산 카드. 각 단계에 실제 API 결과.
- 새 프로젝트, 네트워크, 볼륨, 포트 오프셋이 없고 공유 데이터가 초기화/재마이그레이션되지 않음.

**메뉴 경계**: 공유 full 데이터베이스에서는 CRM 서비스 중지 후에도 CRM 권한 코드가 남을 수 있습니다. 이는 supply-chain 폐쇄에 CRM이 포함된다는 증거가 아닙니다. supply-chain만의 메뉴를 검증하려면 해당 시드로 초기화한 독립 빈 데이터베이스/테넌트를 사용하십시오.

## 중지와 복구

- 이번에 시작한 애플리케이션만 중지하고 mysql/redis/nacos/rocketmq/xxl-job과 데이터 볼륨을 유지합니다.
- 복구: 등록한 원래 서비스 집합을 `docker start`하거나 명시 서비스에 `up -d --no-build --no-deps`를 실행합니다. `down -v`, 볼륨 초기화, 전역 prune은 하지 마십시오.
- 교체 실패 시 교체 전에 기록한 이전 이미지를 같은 서비스 위치에서 복원하고 사이드 서비스를 시작하지 마십시오.

## 흔한 실패

| 증상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 포트 충돌 | 3000/8100/8101/8102/8103/8105/8106/8107/3306/6379/8080/8848/9848/9876 | 폐쇄 변경 또는 임의 포트 오프셋 |
| Nacos 인증 실패 | `.env`와 Nacos 자격 증명 일치 | 인증 비활성화 또는 예시 비밀 기록 |
| 스케줄러 사용 불가 | `XXL_JOB_*`, 18080 매핑, admin 건강 | Outbox 기록을 전달됨으로 취급 |
| 포털 로그인 후 권한 없음 | `SUPPLIER` 역할, 포털 연결, 초대 상태 | 연결 테이블 직접 기록이나 소유 변경 |
| 승인 작업이 비어 있음 | 모델 게시, 후보자, 역할, 작업 생성 | 작업/후보자 테이블 직접 수정 |
| 입고 후 자산 카드 없음 | 품질 합격, 자산 관리 플래그, 양의 정수 수량, 권한 | 자산 카드 직접 삽입 |
| 메뉴 없음 | 역할 권한, `/api/auth/menus`, auth/base 건강 | 데이터베이스 메뉴 직접 수정 |
| 마이그레이션 중복 실행 | 공유 데이터베이스 초기화 여부 | 초기화 후 재실행, 시드 재가져오기 |

## 과거 단계별 기록과 검증 범위

- **검증됨(소스 경로)**: 8개 애플리케이션 이미지를 빌드해 같은 `omni-stack`에서 원위치 교체. auth/base/workflow/srm/procurement/asset/gateway healthy, 프론트엔드 200. 4개 업무 라우트가 토큰 없이 401. `admin` 실제 CAPTCHA 로그인 200, 메뉴 57개 노드(SRM 11, 구매 8, 자산 5, 워크플로 5개 권한 코드). 읽기 전용 조회에서 공급업체 2, 포털 사용자 2, 자재/요청/주문/자산 0(빈 업무 데이터이며 실패 아님). 종료 후 15개 컨테이너 복원.
- **검증됨(생성 경로)**: G6 구조 13개 모듈/15개 서비스 PASS. G7 생성물 백엔드 BUILD SUCCESS, 프론트엔드 build/lint PASS.
- **미검증**: 공급업체 포털 실제 로그인(`supplier1`에 알려진 관례 비밀번호를 쓰면 "비밀번호 오류", 추가 추측이나 우회는 하지 않음), 공급업체 진입 → 승인 → 주문 → 입고 → 카드 생성 전체 체인, 전달 역할 격리와 생성물 이미지 교체, 깨끗한 빈 데이터베이스 첫 설치, 승인/메시지 전달/스케줄링 실제 결과.

> 공급업체 포털 시드 연결(`SUPPLIER` 역할과 공급업체 1/2)은 존재하지만 "연결이 존재한다"는 "실제 포털 로그인을 완료했다"와 다릅니다. 이 페이지는 통제된 테스트 신원을 실제 로그인으로 쓰지 않습니다.

[프리셋 개요](presets-overview.kr.md), [SRM 사실 문서](../srm.kr.md), [구매 설계](../design/procurement-design.md)로 이어서 읽으십시오.
