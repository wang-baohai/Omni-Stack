# 프로젝트 프리셋 유지관리 가이드

`scaffold/catalog/modules.yaml`이 단일 사실 원본이고 `scaffold/presets/*.yaml`은 진입 모듈만 선언합니다. 의존성 표와 README 표는 `npm run docs:preset`으로 생성합니다.

## 관리 대상

- catalog와 `module.schema.json` / `preset.schema.json`
- 공식 `scaffold/presets/*.yaml`
- `tools/omni-cli/src/preset-generator.ts`
- `tools/omni-cli/scripts/preset-golden.mjs`
- 생성 스냅샷 `scaffold.lock`

## 모듈 추가

1. 코드, migration, 멱등 seed, 권한, Compose, Gateway, 문서를 완성합니다.
2. 의존 순서로 catalog에 추가하고 `dependencies`는 앞서 선언된 ID만 참조합니다.
3. backend, frontend view/component/API/i18n, route, Compose, changelog/seed, provisioning, Nacos, port, MQ, XXL-JOB, docs, resource, compatibility/deprecation을 실제 상태대로 기록합니다.
4. `optionalModules`를 런타임 스위치 대신 사용하지 말고 충돌을 명시합니다.
5. catalog validation으로 없는 경로, 끊긴 Compose 의존성, 포트 충돌, 미관리 모듈을 수정합니다.
6. 적절한 프리셋에 진입 모듈만 추가하고 전이 의존성을 복사하지 않습니다.
7. 가지치기 테스트, 생성물 build, runtime smoke를 추가합니다.

상태 머신, DataScope, 자식 범위 상속, Saga, 멱등성은 업무 모듈에 남겨야 합니다.

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

수정은 patch, 호환 기능 추가는 minor, 파괴적 경계 변경은 major를 올립니다. PR은 `test:preset-smoke`, 야간/릴리스는 5종 `test:preset-golden`, runtime은 각 프리셋의 fresh·실제 기동·로그인·메뉴·health·핵심 흐름을 검증합니다.

잔여 검사는 Maven, Dockerfile, Compose, Gateway, view/component/API/i18n, 권한, DB, MQ, 전용 문서를 포함합니다. 생성은 sibling staging에서 원자적으로 수행되고 실패 시 제거됩니다. Schema → 의존/충돌 → catalog resource → 가지치기 → Maven/npm → Compose → fresh DB → runtime/E2E 순으로 진단하세요. 관리 결과를 직접 수정하지 말고 catalog/template/generator를 수정해 새 디렉터리에 재생성하며 실행된 Liquibase changeSet은 바꾸지 않습니다.
