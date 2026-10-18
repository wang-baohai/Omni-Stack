# 프리셋, 컨테이너 개발과 서비스 확장

범위: 2026-10-17에 확인한 CLI 0.6.1, 서비스 템플릿 1.5.1, CRUD 템플릿 1.1.1, 프리셋 배포 템플릿 1.1.0. 작업자는 로컬 개발자이며 업무 로그인이 필요하지 않습니다. generated는 독립 학습 출력이며 정식 seed가 아닙니다. [첫 시작](quick-start.kr.md)에서 Docker 도구 이미지, 의존성과 작업 디렉터리 매핑을 준비합니다. 서비스, 빌드, 테스트와 브라우저는 로컬 Docker에서 실행하며 개인 미커밋 변경이 있는 정식 작업 공간에서 통합을 실험하지 않습니다.

## 1. 프리셋 선택과 생성

프리셋은 모듈을 선택하고 catalog가 의존 폐포를 계산합니다. preset list에는 core, workflow, crm, supply-chain, full이 나옵니다. explain crm의 explicit와 resolved를 확인하세요. 모듈 구성과 자원 예산은 [의존성 매트릭스](../preset-dependency-matrix.kr.md)를 참조합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain crm
```

일부 서비스 시작은 원래 저장소를 사용합니다. 잘라낸 새 프로젝트 생성은 선택한 코드, migration, seed, 4개 언어 Wiki와 적용 AI 자산을 새 디렉터리로 복사합니다. --dry-run으로 확인한 후 이 옵션을 제거하여 생성합니다. 이 명령에는 --apply가 없습니다. 출력은 없거나 비어 있어야 합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/wiki-crm
```

`preset created: crm@...`와 `generated preset valid`를 확인합니다. scaffold.lock의 sourceDigest와 scaffoldDigest는 각각 원본 및 생성 프로젝트의 scaffold/ 디렉터리를 고정합니다. catalog, 프리셋, 문서와 AI 입력은 별도 해시/리소스 검사 대상입니다. 잠금 파일은 전체 애플리케이션 소스의 서명이 아니며 컴파일이나 업무 검증을 증명하지 않습니다. 새 디렉터리의 compose.tools.yaml, compose.docs.yaml, ai/adapters/bootstrap.mjs를 사용하고 자신의 저장소 URL과 비공개 .env를 준비합니다. 원본 저장소 절대 경로에 의존하지 않습니다.

## 2. 일부 서비스 실행과 lite 모드

dev는 up, down, status, doctor, logs를 제공하며 dev plan은 없습니다. up은 Docker 호출 전에 modules, services, mode와 기능 제한을 출력합니다. core/workflow/crm 기본 lite는 Nacos 클라이언트, XXL-JOB, MQ relay와 자동 탐지를 끕니다. 설정상 업무 트랜잭션의 Outbox 쓰기는 허용되지만 실제 삽입이나 전달의 증거는 아닙니다. SRM/Procurement/Asset의 안전하게 끌 수 없는 소비자를 포함한 폐포는 모드 생략 시 full을 선택하고 명시적 lite를 거부합니다. full에서는 .env가 기준입니다.

아래 명령은 표준 omni-stack과 고정 포트(MySQL 3306)를 사용합니다. 기존 환경의 포트, project, 볼륨과 소유권을 먼저 확인합니다. 격리 검증은 소유한 전용 볼륨과 내부 네트워크를 쓰며 업무 포트를 이동하지 않습니다. down은 볼륨을 보존합니다. CLI 도구 이미지에는 Node.js, Docker CLI와 JDK 25가 필요하며 Compose가 Docker socket과 프로젝트 디렉터리를 매핑합니다. doctor는 종료 코드 0에서도 SKIP을 포함할 수 있습니다. Linux 도구 컨테이너에서 호스트 포트 검사를 생략하면 Docker 공개 포트를 별도로 확인합니다. Nacos 프로브는 서버 API/서버 목록을 검사하며 모든 업무 서비스의 등록을 증명하지 않습니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset crm --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev status --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev logs --module crm --tail 200 --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev down --project-name omni-stack
```

## 3. 서비스 패키지 생성과 통합

create-service 기본 동작은 미리 보기입니다. --apply도 omni-service.lock.json이 있는 패키지만 생성하고 monorepo에 통합하지 않습니다. 이어서 service validate와 service integrate를 실행합니다. integrate 역시 기본 읽기 전용이고 별도 --apply가 Maven, catalog, Gateway, Compose, 권한, 메뉴 및 migration 진입점을 씁니다. 먼저 독립 core 학습 프로젝트를 생성합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
cd generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service validate inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-inventory,omni-db-migrator -am
```

계획에 CONFLICT가 없고 적용 후 integration applied가 나오는지 확인합니다. 포트 충돌, 패키지 해시 변경이나 Git 프로젝트의 대상 미커밋 변경은 보호를 끄거나 강제 덮어쓰지 말고 해결합니다. omni-inventory Maven 모듈은 통합 후에 존재합니다. 새 서비스는 Servlet 공통 starter를 사용합니다. 공통 설정의 XssConfigProvider SPI가 실제 classpath/설정에서 활성화되는지 확인하고 필요에 따라 확장하며 reactive Gateway 체인은 복제하지 않습니다. 도메인 검증, DataScope 테이블 매핑과 거부 테스트가 여전히 필요합니다.

현재 계획과 렌더러는 zh-CN, en-US, ja-JP, ko-KR 프런트엔드 locale을 모두 다룹니다. 대상에 .git이 있으면 네 locale 파일이 모두 쓰기 전 미커밋 변경 검사에 포함됩니다. .git이 없는 학습 프로젝트에는 해당 Git 보호가 없으므로 필요하면 깨끗한 기준을 만들고 커밋합니다. 네 언어 등록이 업무 용어 번역 완료를 뜻하지는 않습니다. 새 문서/번역은 present-unverified를 유지하며 실제 의미 검토 후 승인된 AI_REVIEW를 기록합니다. 사람 검토는 필수가 아니며 생성기가 스스로 승인할 수도 없습니다.

<!-- omni:modules:procurement -->
## 4. CRUD 생성

현재 CRUD 사양은 procurement만 지원합니다. 해당 모듈을 포함한 독립 학습 프로젝트에서 scaffold/specs/material-brand.yaml을 사용합니다. generate crud --spec의 --check는 사양 검증, --dry-run은 계획 렌더링, --apply는 여러 파일의 원자적 쓰기이며 dry-run과 apply는 함께 쓸 수 없습니다. 기존 테이블과 충돌하지 않도록 module, permission, DataScope와 필드를 먼저 확인합니다.

아래 cd ../..는 3절에 이어 generated/learning-core에서 원본 저장소 루트로 돌아가는 명령입니다. 이 절부터 시작한다면 먼저 원본 루트로 이동하고 이 cd 명령만 생략하세요.

```bash
cd ../..
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
cd generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
```

출력은 사양 검증 또는 생성 트랜잭션만 증명합니다. 백엔드/프런트엔드, 권한 거부, DB fresh/upgrade를 검증하고 네 언어 설명을 갱신합니다. forward-only Liquibase changeSet을 추가하고 seed 해시/assertion을 갱신하며 임시 migration SQL은 추가하지 않습니다.

CRUD는 현재 중국어 설명과 ja-JP/ko-KR 용어 YAML을 생성하며 완전한 네 언어 문서를 자동 생성하지 않습니다. 런타임 locale 등록은 네 언어를 다루지만 엔터티/필드명에는 원문 언어의 임시 값이 남을 수 있습니다. 실제 번역, 화면 확인과 승인된 의미 검토가 필요합니다. AI_REVIEW로 현재 문서 정책을 충족할 수 있지만 등록이나 잠금 검증만으로 검토 통과 상태를 부여할 수 없습니다.

<!-- omni:modules:end -->

## 5. 모듈 추가·제거와 업그레이드

비활성화: 작은 dev up 폐포를 선택하고 코드와 업그레이드 경로를 유지합니다. 잘라내기: preset create로 잠금, 탐색, 이미지와 AI 규칙을 포함한 새 프로젝트를 전달합니다. 영구 제거: 역의존, Gateway/Nacos/Compose, Maven, 메뉴/라우트, 권한/seed, 업그레이드, consumer와 job을 조사하고 고유 기능을 먼저 옮깁니다. 실행한 migration은 수정하지 않습니다. 기존 데이터 업그레이드는 별도 백업 및 복구 계획이 필요합니다.

scaffold/catalog/modules.yaml과 scaffold/presets/*.yaml의 의존 폐포와 자원 소유권을 관리하고 파생 표 및 다섯 프리셋 검증을 갱신합니다. [사용자 프리셋](../custom-preset-tutorial.kr.md), [유지 관리](../preset-maintenance.kr.md), [업그레이드](../preset-upgrade-guide.kr.md)를 참고하세요. Maven module만 지우면 완료되지 않습니다.

## 6. 검증과 정리

완료 조건은 컨테이너 JDK 25 Reactor, frontend lint/build, 해당 E2E 타입 검사, 권한/데이터 격리 거부, fresh/upgrade, 프리셋/잠금, 4개 언어 본문과 [현행 정책](../adr/0003-authorized-ai-translation-review.md)에 따른 실제 의미 검토입니다. 학습 프로젝트 루트에서 실행합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
```

볼륨을 보존합니다. 이번 실행의 고유 업무 키로 학습 데이터를 정리합니다. generated의 정확한 대상과 추가 개인 파일을 확인한 후 소유한 출력만 삭제합니다. 알 수 없는 디렉터리 덮어쓰기, git clean/reset --hard, Docker 전역 prune을 하지 않습니다. 원시 로그와 token은 저장소 밖에 둡니다. [소스 디버깅](../wiki/source-debugging.kr.md)을 참고하세요.

## 7. 실제 터미널 결과와 비교

[2026-10-12 검증 기록](../reference/evidence/scaffold-2026-10-12.json)은 입력 소스 해시, 도구와 이미지 식별자, 시간, 검증 범위를 보관합니다. 아래 링크는 로컬 Docker의 실제 터미널 텍스트이며 네 언어가 공유합니다. 비공개 출력 경로의 자리표시자를 shell에 그대로 붙여 넣지 마세요.

[프리셋 선택](../reference/evidence/scaffold-preset-select-2026-10-12.txt)은 list, explain crm, core/supply-chain 생성과 검증, core 디렉터리 이동 후 재검증을 실행했습니다. 이 결과만으로 다섯 프리셋의 모든 독립 배포 게이트가 통과한 것은 아닙니다.

[서비스 통합](../reference/evidence/scaffold-create-service-2026-10-12.txt)의 기본 inventory-basic은 잠금 대상 파일 32개를 생성하고 통합 파일 38개를 변경했습니다. MQ/Job/DataScope를 포함한 inventory-sample 골든 빌드는 별도 프로젝트로, 생성 33개와 통합 39개입니다. core에는 rocketmq와 xxl-job이 없으므로 이를 요구하는 패키지의 통합을 쓰기 전에 거부합니다. 의존성이 포함된 프리셋을 선택하고 이 보호를 유지하세요.

<!-- omni:modules:procurement -->
[CRUD 생성](../reference/evidence/scaffold-crud-generate-2026-10-12.txt)은 실제 --check, --dry-run, --apply 및 반복 생성을 기록합니다. 반복 결과는 unchanged이며 잠금 파일 18개, 최초 트랜잭션 변경 파일 29개입니다. 별도 골든 fixture의 backend clean verify와 frontend build/lint가 통과했고 새 lint 오류는 0개였습니다. 생성된 supply-chain 프로젝트의 전체 업무 검증을 대신하지는 않습니다.
<!-- omni:modules:end -->

[경량 실행](../reference/evidence/scaffold-lite-mode-2026-10-12.txt)은 이동한 core 자체의 CLI, 14개 backend 모듈과 frontend 빌드 산출물로 호스트 포트를 공개하지 않는 독립 환경을 시작했습니다. 실행 컨테이너 6개가 준비되었고 일회성 migrator의 종료 코드는 0이었습니다. Gateway → Auth 테넌트 조회가 성공했고 익명 사용자의 보호된 Base API 접근은 401을 반환했습니다. Nacos, XXL-JOB, MQ relay와 consumer는 꺼져 있습니다. 이 읽기 전용 probe는 Outbox 쓰기나 메시지 전달을 검증하지 않았습니다. doctor의 호스트 포트 검사와 Nacos 등록 검사는 SKIP이며 PASS가 아닙니다.

이 과거 환경은 확인된 산출물의 비공개 사전 빌드 이미지를 사용했습니다. 정식 다단계 Docker 빌드의 기존 네트워크 제한은 별도 미통과 게이트이며 --no-build를 일반 시작 지름길로 쓰지 않습니다. 당시 down은 이름 있는 볼륨을 보존했고 그 실행 소유의 볼륨만 이후 삭제했습니다. 최종 컨테이너, 업무 볼륨과 네트워크는 0이었으며 기존 학습 환경의 컨테이너 24개 식별자는 유지되었습니다. 날짜가 있는 이 기록은 현재 실행 중인 컨테이너를 설명하지 않습니다.

2026-10-17의 [현재 CLI 기록](../reference/evidence/scaffold-cli-2026-10-17.json)과 [실제 터미널 텍스트](../reference/evidence/scaffold-cli-2026-10-17.txt)는 성공한 명령 10개를 보충합니다. 프리셋 list/explain/미리 보기, 유효한 서비스 패키지 미리 보기/생성/검증, CRUD --check, dev/dev up/service integrate 도움말입니다. 기본 패키지는 템플릿 1.5.1의 잠금 파일 32개이며 한 번만 생성하여 비공개 증거 디렉터리에 보존했습니다. 처음에는 숫자 하이픈 구간에서 잘못된 기본 Java 패키지명이 파생되어 쓰기 전에 거부되었습니다. 유효한 서비스 ID로 바꾼 뒤 남은 단계만 실행했고 앞서 성공한 명령 세 개는 반복하지 않았습니다.

기존의 확인된 도구 이미지와 의존성 캐시를 사용했습니다. 전체 프리셋 생성, 서비스 통합, 업무 시작이나 Maven 재실행은 하지 않았습니다. 서비스 관련 테스트 11개, CRUD 테스트 13개와 생성 결과 비교는 별도 범위 증거가 있으며 현재 전체 golden과 다섯 프리셋 구조 매트릭스는 계속 검증해야 합니다. 과거 사람 서명 조건은 이력으로 보존합니다. 현재 [승인 정책](../adr/0003-authorized-ai-translation-review.md)은 실제 AI_REVIEW로 충족되며 사람 검토는 필수가 아닙니다. 과거 로그나 자동 생성 상태를 새 문서 승인으로 간주하지 않습니다.

<!-- omni:modules:crm -->
## 8. CRM 확장 및 컨테이너 테스트

개발자 대상이며 업무 로그인이 필요하지 않습니다. 개인 변경이 없는 독립 학습 프로젝트에서 crm/full을 사용하고 [현재 CRM 사실](../crm.kr.md)과 3절을 먼저 읽습니다. 다음은 절차이며 이번 문서 정비에서는 이 업무/DB 작업을 다시 실행하지 않았습니다.

### 8.1 애그리거트, 권한, 이벤트 추가

1. `database/changelog/crm/`에 forward-only changeSet을 추가하고 기존 master에 연결하며 실행된 마이그레이션은 바꾸지 않습니다. `CrmTenantEntity`는 BaseEntity에 tenantId를 추가합니다. owner, version, deleted의 존재는 구체적인 집계 테이블/엔터티에 따라 다르며 모든 새 테이블의 필수 열은 아닙니다. 실제 owner 매핑, 낙관적 잠금과 삭제 전략에 맞춰 schema/엔터티를 설계하고 기본 감사 규약을 유지합니다.
2. 별도 요청/응답 DTO, Mapper, Service 인터페이스/구현, Controller를 추가합니다. 실제 `CrmDataPermissionHandler`에 owner 매핑을 등록하며 없는 owner 열을 필터링하지 않습니다.
3. `scripts/sql/seed/auth.sql`에 API 권한과 역할 할당을 추가합니다. 필요하면 `crm.sql`과 `database/seed/manifest.yaml`의 SHA-256/검증 조건도 갱신합니다. `@PreAuthorize`와 `@ServiceDataScope`는 동일한 전체 코드를 사용하고 쓰기에 `@OperLog`, 버튼에 `v-permission`을 추가합니다.
4. 트랜잭션에서 tenantId를 명시하여 `ReliableMessageRelay.send("crm-domain-out-0", envelope, tenantId, eventId)`를 호출하며 payload에 전체 PII를 넣지 않습니다. 소비자는 봉투 최상위 `eventId`를 읽고 소비자 식별자, 테넌트, 업무 의도와 함께 원자적으로 중복/충돌을 검사합니다. Broker msgKey만으로 업무 한 번 실행이 보장되지 않습니다.
5. 다른 테넌트/owner, identity/scope 누락, 오래된 버전, 잘못된 상태, 롤백, 중복 메시지, PII 분기를 검증합니다. 보이지 않는 레코드 404, 버전/상태 충돌 409를 기대하고 실제 API의 HTTP와 R.code를 기록합니다.

현재 Opportunity 파이프라인 관리 UI는 없습니다. 단계 관리 API/UI와 기존 참조 마이그레이션은 별도 설계가 필요하며 테이블 직접 수정만으로 확장이 완료되지 않습니다.

### 8.2 일반 빌드와 테스트부터 실행

학습 프로젝트 루트에서 실행합니다. backend 도구 컨테이너가 JDK 25/JAVA_HOME을 제공합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-crm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

먼저 6절에 따라 의존성을 설치합니다. 모듈별 BUILD SUCCESS, failures/errors/skipped 및 프론트엔드 종료 코드를 확인합니다. CRM_TEST_MYSQL_URL이 없으면 MySQL 4개가 건너뛰어지므로 빌드 성공이 DB 통합 검수는 아닙니다. 소스/보고 해시가 같으면 재사용하여 변경 없는 재빌드를 피합니다.

### 8.3 MySQL 인터셉터 검증이 필요한 경우

`CrmMysqlInterceptorIntegrationTest`는 `crm_lead`를 DROP/CREATE합니다. 이번에 새로 만든 폐기 가능한 빈 DB만 대상으로 하며 기존 업무 DB를 사용하지 않습니다. 저장소 밖에 비공개 mysql-it.env(MYSQL_DATABASE=crm_it 및 강한 MYSQL_ROOT_PASSWORD), crm-it.env(CRM_TEST_MYSQL_URL은 `jdbc:mysql://crm-it-mysql:3306/crm_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`, CRM_TEST_MYSQL_USERNAME=root, 같은 CRM_TEST_MYSQL_PASSWORD)를 준비합니다. 자격 증명을 Git, 이미지, 공유 터미널 기록에 넣지 않습니다.

PowerShell 예제는 학습 프로젝트 루트에서 이번 실행 소유 이름을 사용합니다. 비공개 경로/볼륨 이름은 작업자가 설정합니다. 이름 충돌 시 소유 관계를 확인하고 기존 리소스를 덮어쓰지 않습니다.

```powershell
$private = 'C:\Private\crm-it-yourrun'
$network = 'crm-it-yourrun'
$cache = 'crm-it-maven-yourrun'
$mysql = 'crm-it-mysql-yourrun'
docker network create $network
docker run -d --name $mysql --network $network --network-alias crm-it-mysql --env-file "$private\mysql-it.env" mysql:8.4
docker logs $mysql
```

로그로 MySQL ready를 확인한 뒤 실행합니다. 실패 시 비공개 파일, 네트워크, DB 이름을 확인하며 전용 테스트 DB 제약을 해제하지 않습니다.

```powershell
docker run --rm --network $network --env-file "$private\crm-it.env" --mount "type=bind,source=$PWD,target=/workspace" --mount "type=volume,source=$cache,target=/root/.m2" -w /workspace/omni-backend eclipse-temurin:25-jdk ./mvnw -pl omni-crm -am -Dtest=CrmMysqlInterceptorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

성공 조건은 대상 보고 4개, 실패/오류/건너뜀 각각 0입니다. 다른 모듈에 일치 테스트가 없다는 사실이 이 4개 통과를 증명하지 않습니다. MyBatis tenant/owner/페이지/scope 누락 경로만 검증하며 로그인, 실제 Feign, 업무 전환 동시성, Broker 전달은 포함하지 않습니다. 이미지 식별자, 소스 해시, 시각, 전체 보고를 저장하고 자격 증명을 가립니다. 현재 정비 보고의 4개는 여전히 건너뜀이며 이 절차는 새 실행 결과가 아닙니다.

이번에 만든 컨테이너, 네트워크와 캐시에 개인 데이터가 없음을 확인한 뒤 정확한 이름으로 정리합니다. MySQL 이미지는 /var/lib/mysql에 익명 볼륨을 사용합니다. 아래 -v는 이 폐기 가능한 테스트 컨테이너와 함께 익명 볼륨만 삭제하며 이름 있는 볼륨은 삭제하지 않습니다. Maven 캐시는 기본 보존하고 전역 prune은 사용하지 않습니다.

```powershell
docker rm -f -v $mysql
docker network rm $network
```

실패 시 민감 값을 가린 로그/보고를 보존하고 원인을 고친 뒤 재시험합니다. 성공한 업무 생성을 반복하지 않습니다. 소스: [테스트](../../omni-backend/omni-crm/src/test/java/com/omni/crm/integration/CrmMysqlInterceptorIntegrationTest.java), [엔티티](../../omni-backend/omni-crm/src/main/java/com/omni/crm/entity/CrmTenantEntity.java), [데이터 권한](../../omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java), [이벤트 봉투](../../omni-backend/omni-crm/src/main/java/com/omni/crm/dto/DomainEventEnvelope.java).

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## 9. SRM 확장 및 컨테이너 테스트

독립 supply-chain/full 학습 프로젝트 개발자 대상이며 업무 로그인은 필요하지 않습니다. [현재 SRM 사실](../srm.kr.md)과 이 안내서의 컨테이너/서비스 통합 절차를 먼저 읽습니다.

1. database/changelog/srm/에 forward-only changeSet을 추가하고 master에 연결합니다. 실행된 마이그레이션이나 init-all.sql은 바꾸지 않습니다. 직접 owner 애그리거트는 SrmOwnedEntity/OWNED_TABLES, 자식은 SrmTenantEntity/부모·중첩 관계로 모델링하며 없는 owner 열 조건을 추가하지 않습니다.
2. Entity, Mapper, 별도 요청/응답 DTO, Service 인터페이스/구현, Controller를 추가합니다. 현재 Supplier/Evaluation/Risk/Quotation을 참고하고 테넌트, 행 권한, 상태, version 검사를 우회하지 않습니다.
3. 모듈 seed는 scripts/sql/seed/srm.sql, API 권한/역할은 scripts/sql/seed/auth.sql, SHA-256/assertion은 database/seed/manifest.yaml을 갱신합니다. 여기의 owner 엔드포인트는 내부 관리 사용자용 업무 API이며 Internal-Token 서비스 간 프로토콜이 아닙니다. 동일 전체 @PreAuthorize/@ServiceDataScope, 쓰기의 @OperLog와 버튼의 v-permission을 사용합니다. 포털 profile/evaluation에는 SUPPLIER, 해당 권한과 ACTIVE PortalUser 연결이 필요합니다. USER의 inviteToken/requestId 입점 흐름은 별도이며 관리 owner를 포털 계정으로 쓰지 않습니다.
4. 업무 트랜잭션에서 tenantId를 명시해 ReliableMessageRelay.send를 호출합니다. 봉투 최상위 eventId와 테넌트/업무 의도를 원자 확인하며 Broker msgKey가 자동 한 번 실행을 보장하지 않습니다. payload에서 계좌/연락 정보/초대 토큰을 제외하고 견적 금액은 십진 문자열로 씁니다.
5. 권한/테넌트 위반, 식별/범위 누락, 오래된 버전, 잘못된 상태, 중복/충돌 이벤트, 롤백, PII를 확인합니다. 버튼 숨김, HTTP 200, Outbox 삽입만 보지 않고 실제 HTTP/R.code로 판단합니다.

6절에 따라 의존성을 설치하고 학습 프로젝트 루트에서 실행합니다. backend 도구 컨테이너가 JDK 25/JAVA_HOME을 제공합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-srm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

해당 소스가 바뀔 때만 빌드를 반복하고 해시 일치 유효 보고는 재사용합니다. Reactor, failures/errors/skipped, 프론트 종료 코드를 확인하며 이미지 식별자, 소스 해시, 시각, 민감값을 가린 보고를 보존합니다.

단위/설정/Controller 보고는 각 단언 범위만 증명하며 실제 CAPTCHA, Workflow/MQ, DB 동시성이나 안내 절차 실행을 증명하지 않습니다. 실제 작업은 [SRM Wiki](srm-flow.kr.md)의 계정, 초대, 고유 접두사, 증거 경계를 따릅니다. 성공한 업무 키는 먼저 조회하고 다시 생성하지 않습니다. DB 통합은 폐기 가능한 환경만 사용하고 공유 업무 DB를 대상으로 하지 않습니다.

실패하면 상세/version, 역할, tenant/owner 또는 ACTIVE PortalUser를 확인하고 trace/오류 코드를 보존합니다. 역할 Saga 재시도와 승인 시작 재시도는 다른 기능이므로 COMPLETED나 오류 문자열만으로 재시작하지 않습니다.

이번 소유 출력에 개인 내용이 없음을 확인하고 정리하며 명명 볼륨/필요 캐시는 유지합니다. 전역 prune이나 git clean/reset --hard를 쓰지 않습니다. 기존 데이터 업그레이드/롤백은 별도 검증합니다. 소스: [범위](../../omni-backend/omni-srm/src/main/java/com/omni/srm/security/SrmDataPermissionHandler.java), [상태 머신](../../omni-backend/omni-srm/src/main/java/com/omni/srm/domain/SrmStateMachine.java), [견적](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/QuotationServiceImpl.java), [리스크](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/RiskServiceImpl.java).
<!-- omni:modules:end -->
