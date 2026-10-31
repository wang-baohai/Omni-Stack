# CRM 프리셋: 코어 플랫폼과 영업 파이프라인

CRM 프리셋은 core의 인증·기반 플랫폼 위에 고객 관계 관리 서비스를 추가합니다. 리드, 고객, 연락처, 영업기회, 팔로업 기록이 필요하지만 SRM, 구매, 자산, 승인 흐름은 아직 필요 없는 팀에 맞습니다. 이 페이지의 모듈, 서비스, 포트는 현재 `scaffold/presets/crm.yaml`, `scaffold/catalog/modules.yaml`, `preset explain crm`의 실제 해석 결과입니다. 실행 결과는 검증됨/미검증으로 나누어 표시하며, 구조 해석을 기동 성공으로 쓰지 않습니다.

이 페이지의 실행 결과는 커버리지 축소 결함 수정 전 단계별 기록이며 현재 버전의 결과가 아닙니다. 초기 실패와 미완료는 보존하고 이후 성공으로 이전 단계를 소급 승인하지 않습니다. 이전 G7 통과만으로 현재 전체 요구사항을 증명할 수 없으므로 릴리스에는 모든 적용 요구사항을 보존한 G6/G7과 필요한 G11 재검증이 필요합니다.

## 누구에게 맞는가

- "리드 → 팔로업 → 고객/연락처 → 영업기회 → 수주/실주" 영업 체인을 먼저 실행하려는 팀.
- 테넌트 격리, 기능 권한, 데이터 범위, PII 마스킹이 필요하지만 공급망이나 워크플로를 함께 도입하고 싶지 않은 프로젝트.
- CRM을 업무 예시로 사용한 뒤 리드, 고객, 영업기회 구현을 자체 도메인 모델로 교체하려는 개발자.

맞지 않는 경우: 공급업체, 구매, 자산 협업, BPMN 승인, 스케줄링 센터의 정기 실행이 필요한 상황. CRM 프리셋에는 SRM, 구매, 자산, workflow, RocketMQ, `xxl-job`이 포함되지 않습니다. CRM 모듈은 catalog에서 RocketMQ/xxl-job을 선택적 의존으로 나열하지만 이 프리셋은 선택하지 않았습니다. 따라서 "비동기 메시지 중계나 스케줄링이 사용 가능하다"는 전제로 인수 조건을 설계하지 마십시오.

## 명시 5개에서 폐쇄 8개로

`scaffold/presets/crm.yaml`의 명시 모듈:

```text
crm, gateway, mysql, redis, nacos
```

`preset explain crm`이 현재 해석하는 폐쇄:

```text
platform, auth, base, crm, nacos, gateway, mysql, redis
```

`crm`은 `base`, `base`는 `auth`, `auth`와 `nacos`는 `platform`, `gateway`는 `base`에 의존합니다. `auth`/`base`를 직접 쓰지 않아도 로그인, 권한, 조직, 사전, 로그, 기반 관리가 폐쇄에 남는 이유입니다. CRM은 그 위에 더해질 뿐 대체하지 않습니다.

## 실제 서비스와 포트

8개 모듈은 9개 Compose 서비스에 대응합니다.

| 서비스 | 원천 모듈 | 역할 |
|---|---|---|
| `mysql` | mysql | 기반과 CRM 업무 데이터 |
| `redis` | redis | 캐시와 토큰 관련 데이터 |
| `nacos` | nacos | 서비스 발견과 설정 센터 |
| `omni-db-migrator` | platform | 일회성 스키마 마이그레이션과 시드 초기화 |
| `omni-auth` | auth | 로그인, 테넌트, RBAC, 조직, 사전, 감사 |
| `omni-base` | base | 기반 관리, 로그, 워크벤치, 작업 API |
| `omni-crm` | crm | 리드, 고객, 연락처, 영업기회, 팔로업, 영업 파이프라인 |
| `omni-gateway` | gateway | 통합 진입점과 라우팅 |
| `omni-frontend` | platform | Vue 프론트엔드 |

catalog가 선언한 포트는 총 18개입니다.

| 모듈 | 선언 포트 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| crm | 8104, 19904, 9904 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

프론트엔드는 platform이 선언한 3000번으로 접속하고, CRM 백엔드는 8104번입니다. 포트가 사용 중이면 점유를 해제하거나 배포 문서에 따라 로컬 매핑을 조정하십시오. 충돌을 피하려고 프리셋 폐쇄를 바꾸거나 임의의 포트 오프셋을 쓰지 마십시오.

## 이점과 비용

이점은 경계가 명확하다는 것입니다. 인증, 테넌트, RBAC, 기반 관리는 플랫폼에서 오고, CRM은 자체 서비스, 권한 트리, 역할, 테이블만 추가합니다. 영업 파이프라인, 리드 전환, 고객 소유 규칙은 `omni-crm`에 모여 있어 개별적으로 교체하거나 줄일 수 있습니다.

비용은 core보다 구성과 운영 면이 분명히 크다는 점입니다. JVM 서비스가 하나 늘고, CRM 마이그레이션과 시드가 한 세트 늘며, 네 개의 CRM 역할과 `crm:*` 권한 코드 그룹이 추가됩니다. 데이터 범위는 본인, 부서, 테넌트 세 가지를 아우르고 PII 필드는 마스킹 규칙의 적용을 받습니다. catalog의 각 모듈 `resourceHints`를 합산하면 폐쇄의 최소 메모리 안내는 3840MB, 권장은 6400MB입니다. 이는 계획용 안내이며 실측 상한이 아닙니다. 실제 사용량은 `docker stats`로 확인하십시오.

## 전제와 설정

- **Docker와 포트**: 3000, 8100, 8101, 8102, 8104, 3306, 6379, 8080, 8848, 9848이 비어 있어야 합니다.
- **비공개 `.env`**: 데이터베이스, Redis, Nacos 인증, 게이트웨이 검증 등의 비밀 값. 실제 값을 커밋하지 말고 예시 값을 사용 가능한 비밀로 취급하지 마십시오.
- **Nacos 설정**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-crm.yml`, `omni-gateway.yml`. Nacos 인증을 켜면 모든 클라이언트가 같은 `NACOS_USERNAME` / `NACOS_PASSWORD`를 사용합니다.
- **마이그레이션과 시드**: `omni-db-migrator`가 실행합니다. 공유 데이터베이스가 이미 초기화되었다면 다시 가져오거나 마이그레이션을 재실행하거나 초기화하지 마십시오.
- **신원과 역할**: `auth.sql`이 테넌트, 관리자, 역할/권한 시드를 제공합니다. CRM 역할은 `CRM_ADMIN`, `SALES_MANAGER`, `SALES_REP`, `CRM_VIEWER`입니다. 시드 계정이 CRM 메뉴를 볼 수 있는지는 해당 역할에 `crm`과 `crm:*` 권한이 부여되었는지에 달려 있습니다.

### 인증 시드와 업무 예시 데이터는 다릅니다

- **인증/기반 시드**(`scripts/sql/seed/auth.sql`, `base.sql`, `nacos.sql`)는 테넌트, 사용자, 역할, 권한, 조직, 사전 등 "로그인하고 권한을 부여할 수 있는" 기반 데이터를 담당합니다.
- **CRM 업무 시드**(`scripts/sql/seed/crm.sql`)는 기본 영업 파이프라인과 단계, CRM 테넌트 설정, 테넌트 1의 데모 리드, 고객, 연락처, 영업기회, 팔로업 기록, 전환 기록을 담당합니다.
- 업무 예시 데이터는 첫 방문이 비어 있지 않게 하는 샘플이며 CRM 기동의 필수 조건이 아닙니다. 리드나 고객이 없을 때는 먼저 "업무 데이터가 비었다"와 "서비스/권한 장애"를 구분하십시오. 데모를 위해 데이터베이스 상태를 직접 바꾸지 마십시오.
- 다른 테넌트는 테넌트 생성 이벤트로 CRM 역할, 권한, 기본 설정을 받습니다. 데모 업무 기록은 정식 시드가 대상으로 하는 테넌트 1에만 존재합니다. 테넌트를 넘어 보이지 않는 것은 격리가 작동한 것이지 메뉴 누락이 아닙니다.

## 경로 1: 소스에서 CRM 기동

### 깨끗한 데이터베이스에 첫 설치

공유 데이터가 초기화되지 않은 독립 환경에서:

```bash
docker compose --parallel 1 --profile crm up -d --build
docker compose ps
```

예상: `omni-db-migrator`가 실행 후 종료되고, mysql/redis/nacos/auth/base/crm/gateway/frontend가 기동하며, `docker compose ps`의 애플리케이션 서비스가 healthy이고 프론트엔드 3000에 접속할 수 있습니다. 이 경로는 마이그레이션과 시드를 실행하므로 빈 데이터베이스 또는 초기화가 명시적으로 허용된 환경에만 사용합니다.

### 기존 공유 환경의 원위치 부분집합

데이터베이스가 full로 초기화되었다면 migrator를 다시 실행하지 않습니다. 이번 소스 이미지를 빌드하고 CRM 폐쇄 밖의 업무 애플리케이션을 중지한 뒤 CRM 애플리케이션만 교체합니다.

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: CRM 폐쇄 안의 5개 애플리케이션 이미지만 빌드합니다. migrator를 시작하지 않고 mysql/redis/nacos도 다시 빌드하지 않습니다.
- `docker stop`: CRM 폐쇄 밖의 업무 애플리케이션을 중지해 같은 환경에 여러 업무 실행면이 공존하지 않게 합니다. 기반과 데이터 볼륨은 유지합니다.
- `up -d --no-build --no-deps --force-recreate`: 방금 빌드한 이미지로 5개 애플리케이션을 원위치 교체합니다. 의존 서비스를 시작하지 않고 마이그레이션도 실행하지 않습니다.
- `ps`: 컨테이너의 실제 상태와 이미지를 확인합니다. healthy는 프로세스와 헬스 체크 통과만 의미하며 업무 흐름 완료를 의미하지 않습니다.
- `curl`: 프런트엔드의 200을 확인합니다. 토큰 없는 `/api/crm/lead/list`의 401은 인증 거절만 증명합니다. 라우트와 하위 서비스의 가용성은 인가된 요청의 성공 및 등록·상태 증거도 필요하며 401만으로 판단할 수 없습니다.

중지 목록은 현재 환경에 실제로 등록된 서비스로 채우십시오. 다른 프리셋을 그대로 복사하지 마십시오. mysql/redis/nacos와 데이터는 유지하고 `down -v`나 볼륨 초기화를 실행하지 마십시오.

## 경로 2: 독립 CRM 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/learning-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/learning-crm
```

- `preset create`: CRM 프리셋을 독립 프로젝트로 줄입니다. 산출물에는 자체 `scaffold.lock`, Compose, 문서, Maven, 프론트엔드 소스가 포함됩니다.
- `preset validate`: 생성물의 모듈 폐쇄, Compose, 문서, AI 진입점, 절단 경계를 검증합니다. 애플리케이션을 시작하지 않습니다.
- 생성물에는 자체 빌드 명령이 있습니다. 생성물 안에서 그 명령을 사용하십시오. 원래 저장소 디렉터리에서 생성물의 Compose 프로젝트를 시작하거나 생성물 migrator로 공유 데이터베이스를 덮어쓰지 마십시오.

과거 초기 단계: G6 구조는 통과했습니다(고정 산출물 위치에서 8개 모듈/9개 서비스). G7에서 생성물 백엔드 `./mvnw clean install`이 BUILD SUCCESS에 도달했습니다(CRM과 게이트웨이 테스트 포함). 그러나 프론트엔드 `npm run build`는 아직 통과하지 못했습니다. 첫 실행에서는 `@rolldown/binding-linux-x64-gnu`가 없었고, 검증 볼륨의 npm 캐시를 지운 재실행에서는 Maven 다운로드 중 TLS 핸드셰이크 종료가 발생했습니다. 전달 역할 격리와 생성물 이미지 원위치 교체는 아직 실행하지 않았습니다. 따라서 "생성물 백엔드가 빌드된다"는 "생성물이 같은 환경에서 기동되었다"와 다릅니다. 이 페이지는 둘을 섞어 쓰지 않습니다.

## 첫 사용

1. `http://localhost:3000`을 열고 개발 테넌트를 선택한 뒤 시드 계정과 **이번에 표시된 CAPTCHA**를 입력합니다. 로컬 데모 관리자는 `admin`입니다(비밀번호는 [첫 기동 튜토리얼](../guides/quick-start.kr.md) 참조). 이전 CAPTCHA를 재사용하거나 인증 우회를 쓰지 마십시오.
2. Wiki 홈은 기존 탭에 남기고 내 워크스페이스를 별도 탭으로 엽니다. 앱 디렉터리에서 CRM을 새 탭으로 열면 개요·리드·고객·연락처·영업기회·팔로업 등 CRM 메뉴만 표시됩니다. 권한은 계속 `/api/auth/menus`를 따릅니다.
3. 권장 순서: 리드 생성 → 팔로업 기록 → 리드 전환(고객/연락처/영업기회 생성) → 영업기회 단계 진행 → 수주 또는 실주 → 고객과 연락처 유지. 데모 데이터에는 일부 전환 결과가 이미 있어 바로 볼 수 있습니다. 신규 기록은 이번 작업이 반환한 ID를 기준으로 하십시오.
4. 메뉴는 보이는데 목록이 비어 있으면 현재 테넌트와 데이터 범위를 확인합니다. `SALES_REP`는 본인 데이터, `SALES_MANAGER`는 부서 및 하위, `CRM_ADMIN`/`CRM_VIEWER`는 테넌트 단위입니다. 다른 사람의 데이터가 보이지 않는 것은 데이터 권한이 작동한 것일 수 있으며 API 장애와 다릅니다.
5. 메뉴가 없으면 현재 역할에 CRM 권한이 부여되었는지 확인합니다. `SUPER_ADMIN`, `CRM_ADMIN`, `SALES_MANAGER`는 전체 CRM 권한, `SALES_REP`는 일상 입력과 전환 권한, `CRM_VIEWER`는 읽기 전용 목록 권한만 가집니다. 메뉴를 "고치려고" 데이터베이스 역할 테이블을 직접 수정하지 마십시오.

## 성공 기준

- CRM 폐쇄 애플리케이션 서비스가 healthy이고 migrator가 예상대로 종료됩니다.
- 프론트엔드 3000이 200을 반환하고 정적 리소스가 로드됩니다.
- 실제 CAPTCHA 로그인이 성공하고 `/api/auth/menus`가 현재 신원에 보이는 CRM 메뉴를 반환합니다.
- CRM 개요, 리드, 고객, 연락처, 영업기회, 팔로업 페이지가 열립니다. 권한이 있는 신원은 시드 데모 데이터를 읽을 수 있습니다.
- 생성과 전환 같은 쓰기 작업은 실제 API 결과로 판단합니다. 헬스 체크 통과는 업무 흐름 완료가 아닙니다.
- 새 프로젝트, 네트워크, 볼륨, 포트 오프셋이 없고 공유 데이터가 초기화되거나 다시 마이그레이션되지 않았습니다.

**메뉴 경계**: full로 초기화한 공유 데이터베이스에서는 해당 서비스를 중지해도 다른 업무 권한 코드가 남을 수 있습니다. 이는 공유 데이터의 상태이며 CRM 폐쇄에 SRM/구매/자산/워크플로가 포함된다는 증거가 아닙니다. "CRM 프리셋에 다른 업무 메뉴가 없다"를 검증하려면 CRM 시드로 초기화한 독립 빈 데이터베이스나 테넌트를 사용하십시오. 기존 공유 데이터베이스에서 권한을 지우거나 역할을 바꿔 흉내 내지 마십시오.

## 중지와 복구

- 이번에 시작한 애플리케이션만 중지합니다. 컨테이너 이름으로 `docker stop`하거나 Compose로 명시 서비스를 중지합니다. mysql/redis/nacos와 데이터 볼륨은 유지합니다.
- 복구: 등록한 원래 서비스 집합을 `docker start`하거나 명시 서비스에 `up -d --no-build --no-deps`를 실행합니다. 전체 스택 `down -v`, 볼륨 초기화, 전역 prune은 하지 마십시오.
- 교체가 실패하면 교체 전에 기록한 이전 이미지를 같은 서비스 위치에서 복원합니다. 사이드 서비스를 시작하지 마십시오.

## 흔한 실패

| 증상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 포트 충돌 | 3000/8100/8101/8102/8104/3306/6379/8080/8848/9848 | 프리셋 폐쇄 변경 또는 임의 포트 오프셋 |
| Nacos 인증 실패 | `.env`와 Nacos 자격 증명이 일치하는지 | 인증 비활성화 또는 예시 비밀 기록 |
| 게이트웨이 503/인가 실패 | 검증 비밀, `omni-crm` 등록 여부, 라우트 설정 | 프론트엔드 요청 주소만 변경 |
| CRM 메뉴 누락 | 현재 역할에 `crm:*` 부여 여부, `/api/auth/menus`, auth/base 건강 | 데이터베이스 메뉴나 역할 직접 수정 |
| 페이지는 열리지만 목록이 빔 | 현재 테넌트, 데이터 범위, 업무 데이터 유무 | 빈 데이터를 기동 실패로 보거나 수동으로 데이터 생성 |
| 리드 전환 실패 | 리드 상태, 필수 필드, `crm:lead:convert` 권한 | API를 우회해 고객/영업기회 직접 삽입 |
| 마이그레이션 중복 실행 | 공유 데이터베이스가 초기화되었는지 | 데이터베이스 초기화 후 재실행 또는 시드 재가져오기 |

## 과거 단계별 기록과 검증 범위

- **검증됨(소스 경로)**: 5개 애플리케이션 이미지를 빌드하고 같은 `omni-stack` 프로젝트에서 원위치 교체했습니다. auth/base/crm/gateway가 healthy이고 프론트엔드가 200입니다. 실제 CAPTCHA 로그인이 code 200을 반환했습니다. `/api/auth/menus`는 57개 노드를 반환했고 CRM 디렉터리와 6개 영업 메뉴가 모두 있습니다. `omni_crm` 읽기 전용 조회 결과 lead=8, customer=5, opportunity=6, activity=13, pipeline_stage=6이었습니다. 종료 후 원래 등록된 15개 컨테이너가 다시 실행되었습니다.
- **검증됨(생성 경로, 일부)**: G6 구조가 8개 모듈/9개 서비스로 통과했습니다. 생성물 백엔드 `./mvnw clean install`이 BUILD SUCCESS에 도달했고 CRM 45개 테스트와 게이트웨이 테스트가 통과했습니다.
- **미검증**: 생성물 프론트엔드 `npm run build`(네이티브 바인딩 누락과 이후 Maven TLS 핸드셰이크 중단), 전달 역할 격리, 생성물 이미지 원위치 교체, 깨끗한 빈 데이터베이스 첫 설치, CRM 페이지 브라우저 시각 렌더링과 업무 쓰기 흐름, 스케줄링과 비동기 중계(이 프리셋에 `xxl-job`/RocketMQ 없음).

[프리셋 개요](presets-overview.kr.md) 또는 [CRM 사실 문서](../crm.kr.md)로 이어서 읽으십시오.
