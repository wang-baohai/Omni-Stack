# workflow 프리셋: 코어 플랫폼과 BPMN·승인

workflow는 core의 인증·기반 위에 독립 워크플로 서비스를 추가합니다. BPMN 모델링, 프로세스 게시, 작업, 승인, 프로세스 인스턴스가 필요하지만 CRM/SRM/구매/자산 업무는 아직 필요 없는 상황에 맞습니다. 구성은 현재 프리셋과 catalog 해석에서 오며, 실행 결과는 검증됨/미검증으로 나눕니다.

이 페이지의 실행 결과는 커버리지 축소 결함 수정 전 단계별 기록이며 현재 버전의 결과가 아닙니다. 초기 실패와 미완료는 보존하고 이후 성공으로 이전 단계를 소급 승인하지 않습니다. 이전 G7 통과만으로 현재 전체 요구사항을 증명할 수 없으므로 릴리스에는 모든 적용 요구사항을 보존한 G6/G7과 필요한 G11 재검증이 필요합니다.

## 명시 5개에서 폐쇄 8개로

`scaffold/presets/workflow.yaml` 명시 모듈:

```text
workflow, gateway, mysql, redis, nacos
```

`preset explain workflow` 폐쇄:

```text
platform, auth, base, workflow, nacos, gateway, mysql, redis
```

`workflow`는 `base`, `gateway`는 `base`, `base`는 `auth`에 의존합니다. `auth`/`base`를 직접 쓰지 않아도 로그인, 권한, 조직, 사전, 기반 관리가 폐쇄에 남습니다.

## 실제 서비스와 포트

8개 모듈은 9개 Compose 서비스에 대응합니다.

| 서비스 | 원천 | 역할 |
|---|---|---|
| `mysql` | mysql | 업무, 기반, Flowable 데이터 |
| `redis` | redis | 캐시와 토큰 관련 |
| `nacos` | nacos | 서비스 발견과 설정 |
| `omni-db-migrator` | platform | 일회성 스키마/시드 |
| `omni-auth` | auth | 로그인, 테넌트, RBAC, 조직, 사전, 감사 |
| `omni-base` | base | 기반 관리, 로그, 워크스페이스, 작업 API |
| `omni-workflow` | workflow | BPMN, 배포, 작업, 승인, 프로세스 |
| `omni-gateway` | gateway | 통합 진입점과 라우트 |
| `omni-frontend` | platform | Vue 프런트엔드 |

catalog 선언 포트:

| 모듈 | 포트 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

`omni-workflow`는 독립 서비스(8103)이며 `omni-base`/`omni-auth`에 합치지 않습니다. Flowable은 `omni-workflow`에만 있고, 업무 서비스는 Flowable 의존성을 추가하지 않습니다.

## 전제와 설정

- **Docker/포트**: 3000, 8100, 8101, 8102, 8103, 3306, 6379, 8080, 8848, 9848이 비어 있어야 합니다.
- **비공개 `.env`**: DB, Redis, Nacos 인증, 게이트웨이 단언 비밀. 실제 값을 커밋하지 않습니다.
- **Nacos 설정**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-gateway.yml`. 자격 증명이 일치해야 합니다.
- **마이그레이션과 시드**: `omni-db-migrator`는 일회성. 초기화된 공유 DB를 다시 마이그레이션하거나 지우지 않습니다.
- **BPMN 모델**: `wf_process_model.model_key`는 테넌트 내 유일하며 BPMN `<process id>`와 일치해야 합니다. 게시 전 `BpmnXmlValidator`를 통과해야 합니다. 데모 모델은 시드에서 올 수 있지만 실제 ID는 이번 실행에서 확인합니다.
- **후보자와 역할**: `omni:assignment` JSON 확장이 유일한 후보자 해석 진입점이며 작업 시작 시 파싱됩니다. 로그인 계정이 자동으로 후보자가 되지는 않으며 역할과 데이터 범위가 필요합니다.

## 경로 1: 소스에서 workflow 기동

### 깨끗한 DB 최초 설치

```bash
docker compose --parallel 1 --profile workflow up -d --build
docker compose ps
```

기대: migrator가 끝나고 종료되며 mysql/redis/nacos/auth/base/workflow/gateway/frontend가 기동, 앱이 healthy, 프런트엔드 3000 접근 가능. 마이그레이션과 시드를 실행하므로 빈 DB 또는 초기화가 허용된 환경에만 사용합니다.

### 기존 공유 환경의 원위치 하위 집합

full로 초기화되어 있으면 migrator를 다시 실행하지 않습니다. 이번 이미지를 빌드하고 workflow 폐쇄 밖 업무 앱을 중지한 뒤 교체합니다.

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

중지 목록은 실제 환경 등록에서 채우고 mysql/redis/nacos와 데이터를 유지합니다. `down -v`나 볼륨 삭제는 하지 않습니다.

## 경로 2: 독립 workflow 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create workflow --output generated/learning-workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate workflow --output generated/learning-workflow
```

생성물은 자체 `scaffold.lock`, Compose, 문서, AI 진입점을 가집니다. 생성물 안에서 빌드하고 원 저장소에서 그 Compose 프로젝트를 기동하지 않으며, 생성물 migrator를 공유 DB에 실행하지 않습니다.

과거 초기 단계: G6 구조(8개 모듈/9개 서비스)와 G7 생성물 백엔드/프런트엔드 build·lint 통과. 배달 역할 격리와 생성물 이미지 원위치 교체는 미완료입니다.

## 최초 사용

1. `http://localhost:3000`을 열고 개발 테넌트, 시드 계정, **이번에 표시된 CAPTCHA**를 입력합니다.
2. 홈은 Wiki입니다. 내 워크스페이스를 별도 탭으로 열고 승인과 개인 작업을 한 화면에서 처리합니다. 앱 디렉터리에서 워크플로 모듈을 새 탭으로 열고 역할에 따라 모델과 인스턴스 진입점을 확인합니다.
3. 권장 순서: 모델 설계/검증 → 게시(고유 model_key) → 프로세스 시작 → 후보자가 작업 처리 → 프로세스와 이력 확인.
4. 작업이 비어 있으면 현재 신원이 후보자 범위인지, 프로세스가 게시되었는지, 작업이 생성되었는지 확인합니다. 작업 테이블을 직접 편집하지 않습니다.

## 성공 기준

- workflow 폐쇄 앱이 healthy이고 migrator가 예상대로 종료됨.
- 프런트엔드 3000이 200을 반환.
- 실제 CAPTCHA 로그인이 성공하고 workflow 메뉴/작업 진입점이 권한에 따라 나타남.
- 게시된 모델로 프로세스를 시작하고 후보자가 작업을 처리할 수 있음.
- 새 프로젝트/네트워크/볼륨/포트 오프셋이 없음. 공유 데이터를 지우거나 재마이그레이션하지 않음.

**메뉴 경계**: full 공유 DB에는 CRM/SRM/구매/자산 권한 코드가 남을 수 있습니다. 이는 공유 데이터 상태이며 workflow 폐쇄가 그 업무를 포함한다는 증거가 아닙니다. "workflow에 다른 업무 메뉴가 없다"를 검증하려면 workflow 시드로 초기화한 독립 빈 DB/테넌트를 사용합니다.

## 중지와 복원

- 이번에 기동한 앱만 중지(컨테이너 이름 또는 명시 서비스). mysql/redis/nacos와 데이터 유지.
- 복원: 등록된 원 서비스 집합을 `docker start`, 또는 명시 서비스에 `up -d --no-build --no-deps`. `down -v`, 볼륨 삭제, 전역 prune 금지.
- 교체 실패 시 교체 전에 기록한 이전 이미지를 같은 위치에서 복원하고 우회 서비스를 기동하지 않습니다.

## 흔한 실패

| 현상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 포트 충돌 | 8103/3000/8100/8101/8102/3306/6379/8080/8848/9848 | 폐쇄 변경이나 무작위 오프셋 |
| Nacos 인증 실패 | `.env`와 Nacos 자격 증명 일치 | 인증 비활성화, 샘플 비밀 |
| 모델 게시 실패 | model_key 유일성, BPMN process id 일치, XML 검증 | DB 직접 쓰기로 우회 |
| 작업이 비어 있음 | 후보자 확장, 역할, 작업 생성, 프로세스 게시 | 작업/후보자 테이블 직접 편집 |
| 게이트웨이 503 | 단언 키, 하위 등록, 라우트 | 프런트엔드 URL만 변경 |
| 메뉴 로드 실패 | auth/base/workflow/gateway 상태, Nacos, `/api/auth/menus` | 메뉴 테이블 직접 편집 |

## 과거 단계별 기록과 검증 범위

- **검증됨**: workflow 폐쇄 8개 모듈/9개 서비스/18개 포트, 5개 앱 이미지 빌드와 원위치 교체, auth/base/workflow/gateway healthy, 프런트엔드 200, 생성물 G6/G7 빌드.
- **미검증**: workflow 실제 CAPTCHA 로그인(이번 2회 시각 입력 실패), workflow 메뉴/작업/승인 실제 조작, 깨끗한 DB 최초 설치, 업무 메뉴 없음, 생성물 이미지 교체와 배달 역할 격리, 정기 작업(workflow에 xxl-job 없음).

[프리셋 총람](presets-overview.kr.md) 또는 [워크플로 사실 문서](../workflow.kr.md)로 이어집니다.
