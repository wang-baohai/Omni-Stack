# 프로젝트 프리셋 유지관리 가이드

본 문서는 스캐폴드 유지관리자를 위한 것입니다. `scaffold/catalog/modules.yaml` 이 모듈 구성의 단일 사실 원본이고, `scaffold/presets/*.yaml` 은 진입 모듈만 선언합니다. 의존성 매트릭스와 README 프리셋 표는 `npm run docs:preset` 으로 생성됩니다.

## 관리 대상 파일

- `scaffold/catalog/modules.yaml`: 모듈, 의존성, 리소스, 권한, 데이터베이스, MQ, XXL-JOB, 문서, 호환성.
- `scaffold/schemas/module.schema.json`: 모듈 매니페스트 Schema.
- `scaffold/schemas/preset.schema.json`: 프리셋 Schema.
- `scaffold/presets/*.yaml`: 다섯 종 공식 프리셋.
- `tools/omni-cli/src/preset-generator.ts`: 원자적 복사와 구조화 가지치기.
- `tools/omni-cli/scripts/preset-golden.mjs`: 생성물 매트릭스와 잔여 게이트.
- `scaffold.lock`: 생성된 프로젝트의 버전과 모듈 스냅샷.

## 모듈 추가

1. 먼저 모듈 코드, 마이그레이션, 멱등 시드, 권한, Compose, Gateway, 문서를 완성합니다.
2. catalog 끝에 실제 의존 순서로 모듈을 등록; `dependencies` 는 이미 선언된 모듈만 가리킬 수 있습니다.
3. 백엔드 모듈, 프론트엔드 view/component/API/i18n, Gateway route, Compose service, changelog, seed, provisioning, Nacos, 포트, MQ, XXL-JOB, 문서, 리소스 추정을 빠짐없이 기록합니다.
4. `optionalModules` 는 선택적 통합만 나타내며 런타임 비활성화 스위치를 대체할 수 없습니다; `conflicts` 는 양방향이거나 검증 규칙으로 명시적으로 처리해야 합니다.
5. `omni catalog validate` 를 실행해 없는 경로, 고아 Compose 의존성, 중복 포트, 미관리 공식 모듈을 수정합니다.
6. 모듈을 적절한 프리셋 진입점에 추가; 전이 의존성을 복사하지 않습니다.
7. 가지치기 단위 테스트, 생성 프로젝트 빌드, 런타임 스모크 증거를 추가합니다.

도메인 상태 머신, DataScope 테이블 매핑, 자식 리소스 상속, Saga, 멱등 규칙은 업무 모듈에 속하며 범용 프리셋 생성기로 내릴 수 없습니다.

## 공식 프리셋 변경

`scaffold/presets/<id>.yaml` 의 진입 모듈과 설명만 편집합니다. 그런 다음 실행:

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

모듈 집합, 기본 구성, 생성 결과에 호환성 변화가 생기면 `version` 을 증가: 수정은 patch, 하위 호환 기능 추가는 minor, 파괴적 경계 변화는 major. catalog 모듈 버전과 프리셋 버전은 각각 유지합니다.

## 골든 샘플과 릴리스 게이트

- PR: `npm run test:preset-smoke`, `core` 와 하나의 비즈니스 프리셋을 검증, 백엔드는 `clean verify` 를 사용해 공유 `.m2` 쓰기 충돌을 피합니다.
- 야간/릴리스: `npm run test:preset-golden`, 다섯 프리셋에서 `clean install`, 프론트엔드 ci/lint/build, Compose 구성, 잔여 스캔을 실행.
- 런타임: 각 프리셋에서 db-migrator fresh, 실제 기동, 로그인, 메뉴, health, 핵심 흐름을 실행; `full` 은 전체 E2E 를 실행.

잔여 스캔은 최소한 Maven, Dockerfile, Compose, Gateway, 페이지, 컴포넌트, API, i18n, 권한, 데이터베이스, MQ, 모듈 전용 문서를 커버합니다.

## 실패, 롤백 및 진단

생성은 같은 수준의 staging 디렉터리를 사용하고, 실패 시 staging 을 삭제하므로 대상 디렉터리에 미완성 결과물이 나타나지 않습니다. 기존 비어 있지 않은 대상은 복사 전에 거부됩니다.

진단 순서: Schema 오류 → 의존성 클로저/충돌 → catalog 경로 → 구조화 가지치기 → Maven/npm → Compose → fresh 데이터베이스 → 런타임/E2E. 생성 후 실패 시 로그와 `scaffold.lock` 을 보존하고 관리 대상 파일을 직접 패치하지 않으며; 먼저 원본 catalog, 템플릿, 가지치기 도구를 수정한 뒤 새 디렉터리로 재생성합니다.

실행된 Liquibase changeSet 은 절대 고쳐 쓰지 않습니다. 앱 버전을 롤백할 때 호환 데이터베이스 구조를 보존하고, 필요 시 전방 수정 changeSet 을 추가합니다; 임의의 down SQL 을 쓰지 마세요.
