# 커스텀 프리셋 튜토리얼

커스텀 프리셋은 다섯 종 공식 프리셋 외에도 catalog 에 알려진 모듈을 조합하는 데 적합합니다. 공식 프리셋과 동일한 Schema, 의존성 클로저, 충돌 검사, 원자 생성, 잔여 검사를 사용합니다.

## 1. YAML 작성

예를 들어 공급업체와 워크플로우 기능만 필요하다면:

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: 공급업체 워크스페이스
description: 코어 플랫폼, 워크플로우, 공급업체 관리.
modules: [srm, gateway, mysql, redis, nacos]
~~~

`modules` 에는 진입 모듈만 적습니다. `srm` 은 `workflow → base → auth → platform` 을 자동으로 끌어옵니다; 생성기는 의존성과 무관한 Procurement 이나 Asset 을 자동 추가하지 않습니다.

## 2. 검증과 설명

위 YAML을 원본 저장소의 scaffold/presets/supplier-workspace.yaml로 저장합니다. 다음 명령은 저장소 루트에서 실행하며 CLI 빌드와 실행은 로컬 Docker 도구를 사용합니다.

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset explain scaffold/presets/supplier-workspace.yaml
~~~

알 수 없는 모듈, Schema 오류, 충돌은 어떤 파일 쓰기 전에 실패합니다.

## 3. 미리보기와 생성

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
~~~

생성된 프로젝트는 정규화된 `scaffold/presets/supplier-workspace.yaml` 과 `scaffold.lock` 을 저장합니다. 공유 Workflow 서비스의 조달, 자산 기본 프로세스는 조합에 따라 잘려 나가고, 공급업체 진입 프로세스만 남습니다.

## 4. 생성 프로젝트 검증

`docker compose config`/기동은 프로젝트 최상위의 `.env` 를 읽는다; 없으면 "required variable is missing" 로 실패한다 (exit 1). 먼저 생성 프로젝트에 포함된 `.env.example` 를 복사하고 **자체 생성한 랜덤 비밀값**을 채운다 (≥ 32 바이트, `JWK_ENCRYPT_KEY` 는 반드시 32 자; 저장소 자리표시자 기본값이나 실제 공유 비밀 재사용 금지). 병렬 격리 시 독립적인 project/포트/볼륨/이미지 네임스페이스를 설정한다:

NACOS_AUTH_TOKEN은 32바이트 이상의 난수를 Base64로 인코딩한 값이어야 합니다. NACOS_USERNAME은 nacos를 사용하고 서버와 모든 클라이언트의 NACOS_PASSWORD를 일치시킵니다.

~~~powershell
Set-Location generated/supplier-app
Copy-Item .env.example .env   # 필수: OMNI_INTERNAL_API_TOKEN / GATEWAY_ASSERTION_SECRET / MYSQL_ROOT_PASSWORD / OMNI_DB_PASSWORD / REDIS_PASSWORD / XXL_JOB_ACCESS_TOKEN / XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD / NACOS_PASSWORD / NACOS_AUTH_TOKEN / NACOS_AUTH_IDENTITY_KEY / NACOS_AUTH_IDENTITY_VALUE / OAUTH2_STATE_SECRET / JWK_ENCRYPT_KEY; 격리: COMPOSE_PROJECT_NAME(또는 -p), OMNI_MYSQL_VOLUME_NAME, OMNI_*_HOST_PORT, OMNI_IMAGE_NAMESPACE
~~~

이어 백엔드/프론트를 빌드하고 Compose 구성을 검증한다:

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose config --quiet
~~~

그런 다음 격리된 Compose project/volume 로 fresh 데이터베이스, 기동, 브라우저 스모크를 실행합니다. 기존 개발 스택과 데이터베이스 볼륨을 공유하지 마세요.

## 흔한 실수

- `omni-*` artifact 이름을 `modules` 에 쓰기: 여기서는 catalog 의 모듈 ID 를 사용해야 합니다.
- 모든 전이 의존성을 명시적으로 복사하기: 불필요하며 유지관리 노이즈를 늘립니다.
- 원본 저장소 루트 자체 또는 비어 있지 않은 디렉터리 덮어쓰기: 생성기가 거부합니다.
- 생성 후 모듈을 수동 삭제하기: `scaffold.lock`, 시드 다이제스트, 라우트를 왜곡하므로, YAML 을 수정해 재생성해야 합니다.
- 선택적 인프라를 자동 비활성화로 이해하기: 생성 후에도 해당 런타임 스위치를 확인해야 합니다.
- RocketMQ 를 포함하되 `srm` 을 누락: `auth` 의 포털 롤 컨슈머가 `srm-domain-event` 를 구독하므로, 생성자는 dangling 컨슈머로 기록을 차단한다. MQ 를 활성화하는 조합은 반드시 `srm` 도 함께 포함해야 한다. RocketMQ 가 없으면 이 제약은 적용되지 않는다 (예: `auth`, `workflow`, `crm` 만 포함한 축소 조합은 정상 생성된다).
