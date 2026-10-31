# core 프리셋: 인증과 기반 플랫폼만

core는 "먼저 로그인, 테넌트, 권한을 관리할 수 있고 업무 프로세스는 나중에"라는 상황에 맞습니다. 가치는 기능의 많음이 아니라 폐쇄가 작고 경계가 명확하다는 데 있습니다. 이 페이지의 구성, 포트, 자원은 현재 프리셋 파일과 catalog에서 가져오며, 실행 결과는 "검증됨/미검증"으로 나눠 표시합니다. 구조 해석을 기동 성공으로 쓰지 않습니다.

이 페이지의 실행 결과는 커버리지 축소 결함을 수정하기 전 기록이며 메뉴 수와 심사 대기 상태도 당시 값입니다. 이전 G7과 납품 검사 통과만으로 현재 버전의 전체 요구사항을 증명할 수 없습니다. 현재 버전은 모든 적용 요구사항을 보존한 G6/G7 및 필요한 G11로 재검증합니다. 빌드·기동·역할 분리 증거는 입력 해시가 여전히 유효한 범위에서 재사용할 수 있습니다.

## 누구에게 맞는가

- 로그인, 테넌트, RBAC, 조직, 사전, 로그를 먼저 검증하려는 팀.
- 최소 백오피스 골격을 두고 업무 도메인을 하나씩 추가하려는 개발자.
- CRM, SRM, 구매, 자산, 워크플로가 없는 독립 프로젝트를 생성하려는 사람.

승인 흐름, 영업 파이프라인, 공급업체/구매/자산 협업, 스케줄러가 필요한 정기 작업에는 맞지 않습니다. core 폐쇄에 이런 업무 서비스가 없고 `xxl-job` 스케줄러도 없습니다.

## 명시 5개에서 폐쇄 7개로

`scaffold/presets/core.yaml`의 명시 모듈:

```text
base, gateway, mysql, redis, nacos
```

`preset explain core`가 해석한 폐쇄:

```text
platform, auth, base, nacos, gateway, mysql, redis
```

추가되는 `platform`과 `auth`는 의존성에서 옵니다. `base`는 `auth`에, `auth`와 `nacos`는 `platform`에 의존합니다. `auth`를 직접 쓰지 않았는데 로그인과 권한 서비스가 있는 이유입니다.

## 실제 서비스와 포트

7개 모듈은 다음 8개 Compose 서비스에 대응합니다.

| 서비스 | 원천 모듈 | 역할 |
|---|---|---|
| `mysql` | mysql | 업무·기반 데이터 |
| `redis` | redis | 캐시, 세션/토큰 관련 데이터 |
| `nacos` | nacos | 서비스 발견과 설정 센터 |
| `omni-db-migrator` | platform | 일회성 스키마 마이그레이션과 시드 초기화 |
| `omni-auth` | auth | 로그인, 테넌트, RBAC, 조직, 사전, 감사 등 |
| `omni-base` | base | 기반 관리, 모니터링, 로그, 워크스페이스, 작업 API |
| `omni-gateway` | gateway | 통합 진입점과 라우팅 |
| `omni-frontend` | platform | Vue 프런트엔드 |

catalog가 선언한 포트(모듈의 여러 포트는 그대로 나열하며 선언되지 않은 용도는 추론하지 않음):

| 모듈 | 선언 포트 |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

프런트엔드 진입점은 platform 선언 포트 3000입니다. 포트가 사용 중이면 프리셋 폐쇄를 바꿔 "피하지" 말고 포트를 확보하거나 배포 문서에 따라 호스트 매핑을 조정하세요. Nacos 3.x의 9848은 gRPC 포트이며 표준 포트이고 무작위 오프셋이 아닙니다.

## 전제와 설정

core는 "제로 설정"이 아닙니다. 기동 전에 최소한 다음을 준비합니다.

1. **로컬 Docker**. 3000, 8100, 8101, 8102, 3306, 6379, 8080, 8848, 9848이 비어 있는지 확인합니다.
2. **`.env` 사설 설정**: DB, Redis, Nacos 인증, 게이트웨이 단언 등 비밀 값. 실제 값을 커밋하지 말고 샘플 값을 사용 가능한 비밀로 취급하지 마세요.
3. **Nacos 설정**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-gateway.yml`. Nacos 인증이 켜져 있으면 모든 클라이언트가 같은 `NACOS_USERNAME` / `NACOS_PASSWORD`를 씁니다. 불일치는 재시작 루프나 인증 실패로 나타납니다.
4. **스키마와 시드**: `omni-db-migrator`가 실행하는 일회성 프로세스이며 상주 서비스가 아닙니다. 초기화된 공유 DB를 다시 가져오거나 지우지 마세요.
5. **테넌트와 관리자 신원**: 인증 시드에서 옵니다. 최초 로그인에서는 가이드의 테넌트와 시드 계정을 선택하고 이번에 표시된 CAPTCHA를 입력합니다.

게이트웨이 단언 키는 통제된 설정에서만 주입합니다. 누락되거나 하위 서비스와 불일치하면 API가 인증/게이트웨이 오류를 반환합니다. 업무 코드를 바꾸기 전에 설정 출처를 확인하세요.

## 경로 1: 소스에서 core 기동

### 최초 설치(깨끗한 DB)

공유 데이터가 초기화되지 않은 독립 환경:

```bash
docker compose --parallel 1 --profile core up -d --build
docker compose ps
```

기대: `omni-db-migrator`가 끝나고 종료되며 mysql/redis/nacos/auth/base/gateway/frontend가 기동하고, `docker compose ps`에서 앱이 healthy이며 프런트엔드 3000에 접근할 수 있습니다. 이 경로는 마이그레이션과 시드를 실행하므로 빈 DB 또는 초기화가 명시적으로 허용된 환경에만 사용합니다. **본 페이지에서는 이 경로를 실측하지 않았습니다**: 현재 검증 환경은 full로 초기화된 공유 DB를 사용하므로, 빈 DB 최초 설치는 "미검증" 표기를 유지하고 공유 DB 결과를 그 증거로 제시하지 않습니다.

### 기존 공유 환경의 원위치 하위 집합(이 페이지에서 검증한 경로)

DB가 full로 초기화되어 있으면 migrator를 다시 실행하지 않습니다. 이번 소스 이미지를 빌드하고 core 폐쇄 밖 업무 앱을 중지한 뒤 core 앱만 교체합니다.

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

중지 목록은 현재 환경에 실제 등록된 서비스로 채우고 다른 프리셋을 복사하지 마세요. core에는 workflow, CRM, SRM, 구매, 자산, xxl-job, RocketMQ가 없습니다. mysql/redis/nacos와 데이터는 유지하고 `down -v`나 볼륨 삭제를 하지 않습니다. 검증 후에는 등록된 집합을 `up -d`로 전체 서비스 복원합니다.

## 경로 2: 독립 core 프로젝트 생성

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate core --output generated/learning-core
```

생성물은 자체 `scaffold.lock`, Compose, 문서, AI 진입점을 가집니다. 생성물에 들어간 뒤 자체 빌드 명령을 사용하고 원 저장소에서 생성물의 Compose 프로젝트를 기동하지 마세요. 생성물 migrator를 공유 DB에 실행하지 마세요.

과거 기록: G6 구조(7개 모듈/8개 서비스), 생성물 백엔드 빌드와 프런트엔드 build/lint가 통과했습니다. 생성물과 캐시만 참조하고 소스 및 Docker socket이 없는 납품 실행 환경에서도 자체 빌드가 통과했습니다. 생성 이미지 4개를 같은 환경에서 교체하고 로그인·메뉴·워크스페이스를 확인한 뒤 원래 이미지로 복원했습니다. 당시 `verify-preset-delivery core`는 `automated-delivery-pass`, `release_ready=false`였지만 이전 처리는 검증 요구사항을 누락할 수 있었습니다. 이 결과로 현재 G7이나 릴리스 인수 완료를 선언하거나 남은 작업을 번역 심사뿐이라고 설명하면 안 됩니다.

## 최초 접근과 로그인

1. `http://localhost:3000`을 열고 Wiki 홈과 로그인 진입점이 로드되는지 확인합니다.
2. 가이드의 개발 테넌트를 선택하고 시드 계정과 **이번에 표시된 CAPTCHA**를 입력합니다. 오래된 CAPTCHA 재사용이나 인증 백도어는 쓰지 않습니다.
3. 로그인 후 확인:
   - 홈에서 내 워크스페이스를 별도 브라우저 탭으로 열고 기존 Wiki 탭은 유지한다.
   - 시스템 관리, 사용자/역할/권한, 조직, 테넌트, 로그 진입점이 역할에 따라 나온다.
   - 기반 관리, 사전, 작업 API가 `base` 권한에 따라 나온다.
4. 메뉴가 계속 로드되지 않으면 프런트엔드보다 먼저 auth/base/gateway 상태, Nacos 설정, `/api/auth/menus`를 확인합니다. DB 메뉴 테이블을 직접 편집하지 마세요.

## 성공 기준

- `docker compose ps`에서 core 폐쇄 앱이 healthy이고 migrator가 예상대로 종료됨.
- 프런트엔드 3000이 200을 반환하고 정적 자산이 로드됨.
- 실제 CAPTCHA 로그인이 성공하고 메뉴 API가 현재 신원에 보이는 노드를 반환함.
- 인증, RBAC, 조직, 사전, 로그 진입점이 동작함.
- 새 프로젝트, 네트워크, 볼륨, 포트 오프셋이 없음. 공유 데이터를 지우거나 마이그레이션을 재생하지 않음.

**메뉴 경계**: 메뉴 API는 "테넌트 권한 트리 ∩ 현재 신원 권한 코드"만 반환합니다(`MenuController`가 JWT 권한으로 DIRECTORY/MENU 노드를 필터링). 어떤 서비스가 실행 중인지와 무관합니다. full로 초기화한 공유 DB에서는 업무 서비스를 중지해도 업무 권한 코드가 관리자 등 역할에 부여된 채 남기 때문에 메뉴에 업무 진입점이 계속 표시됩니다. 실측에서 core 창 관리자 메뉴 57개 노드 중 36개가 업무 DIRECTORY/MENU 코드였고 공유 DB 부여 조회와 일치했습니다. 이는 공유 데이터 상태이며 core 폐쇄에 업무 모듈이 포함된다는 증거가 아닙니다. core의 재단 시드는 업무 권한 행을 전혀 포함하지 않으므로(소스 시드는 업무 권한 141행, 생성 core는 0), **core 시드로 초기화한 빈 DB 최초 설치에는 업무 진입점이 표시되지 않습니다**. 이 결론은 메뉴 파생 메커니즘과 시드 재단으로 증명되며, 실행 시 실측은 독립 빈 DB/독립 테넌트 환경에서 수행합니다. 기존 공유 DB에서 권한을 지우거나 역할을 바꿔 모의하지 마세요.

## 중지와 복원

- 이번에 기동한 앱만 중지: 컨테이너 이름으로 `docker stop`, 또는 명시한 Compose 서비스 중지. mysql/redis/nacos와 데이터 볼륨은 유지.
- 복원: 등록된 원 서비스 집합을 `docker start`, 또는 명시 서비스에 `up -d --no-build --no-deps`. `down -v`, 볼륨 삭제, 전역 prune은 금지.
- 교체 실패 시 교체 전에 기록한 이전 이미지를 같은 서비스 위치에서 복원하고 우회 서비스를 기동하지 않습니다.

## 흔한 실패

| 현상 | 먼저 확인 | 하지 말 것 |
|---|---|---|
| 포트 사용 중 | 3000/8100/8101/8102/3306/6379/8080/8848/9848 | 프리셋 폐쇄 변경이나 무작위 포트 오프셋 |
| Nacos 인증 실패 | `.env`와 Nacos 자격 증명 일치 여부 | 인증 비활성화, 샘플 비밀 입력 |
| 게이트웨이 503/인증 실패 | 단언 키, 하위 등록, 라우트 설정 | 프런트엔드 URL만 변경 |
| 메뉴 로드 실패 | auth/base/gateway 상태, `/api/auth/menus`, Nacos 설정 | DB 메뉴 직접 편집 |
| 작업 진입점은 있으나 실행 안 됨 | core에 `xxl-job` 포함 여부(미포함) | 작업 코드 존재를 스케줄러 동작으로 표기 |
| 마이그레이션 이중 실행 | 공유 DB가 초기화되었는지 | DB 삭제나 시드 재가져오기 |
| Maven 다운로드 `Remote host terminated the handshake` | 기업 네트워크 TLS 변동. 같은 명령을 나중에 재시도 | 의존 버전이나 저장소 좌표 변경 |
| `npm ci` ECONNRESET | 네트워크 변동. 재시도하고 오프라인 캐시 유지 | lockfile 삭제나 lint 건너뛰기 |
| 컨테이너 내 다운로드 전부 실패(DNS/핸드셰이크) | 호스트 프록시로 `HTTP_PROXY/HTTPS_PROXY` 설정(컨테이너 내 `host.docker.internal:<혼합 포트>`), 이미지 빌드에는 동일 이름 `--build-arg` | 제품이나 프리셋 탓으로 돌리기 |
| 검증 컨테이너에 `docker`/BuildKit 없음 | docker CLI와 buildx 플러그인 설치 후 `DOCKER_BUILDKIT=1` | 생성물을 호스트 임시 디렉터리로 복사해 빌드 |
| 컨테이너 재빌드마다 Maven 로컬 저장소 재다운로드 | `-Dmaven.repo.local`를 고정 검증 볼륨 캐시로 지정 | 이미지에 사유 저장소 설정을 굽기 |

## 검증됨과 미검증

- **검증됨**: core 폐쇄 해석, 8개 서비스 목록, 포트와 자원 힌트, 소스 앱 이미지 빌드, 같은 `omni-stack` 프로젝트에서 auth/base/gateway/frontend 원위치 교체, 서비스 상태, 프런트엔드 200, 실제 CAPTCHA 로그인 200, core 창 메뉴와 워크스페이스 화면(업무 잔존은 공유 DB 부여 기반이라는 결론 포함), full 서비스 집합 복원 후 상태, 생성물 G6/G7, 배달 역할 자체 충분성(소스 없음·socket 없음), 생성물 4개 이미지 원위치 교체와 실제 진입점 검증 및 이후 저장소 이미지 복원, 배달 게이트 `automated-delivery-pass`.
- **미검증**: 깨끗한 빈 DB 최초 설치, core 시드의 "업무 메뉴 없음" 실행 시 실측(메커니즘과 시드 재단은 증명됨, 실행 시는 독립 빈 DB 환경 대기), 공개 준비 완료(`release_ready=false`, 번역문 지정 검토 대기), 정기 작업 실행(core에 스케줄러 없음).

[프리셋 총람](presets-overview.kr.md) 또는 [최초 기동 가이드](../guides/quick-start.kr.md)로 이어집니다.
