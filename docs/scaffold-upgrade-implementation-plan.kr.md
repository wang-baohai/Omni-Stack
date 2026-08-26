# Omni-Stack 스캐폴드 업그레이드 구현 계획

> 번역 상태: 중국어 원문을 기반으로 한 기술 번역 초안이며 사람의 검토가 남아 있습니다.
> 실행 사실의 원천은 `docs/scaffold-upgrade-implementation-plan.md`입니다.

## 0. 정확성의 경계

이 계획은 실행 가능하고 증거 기반이며 rollback 가능하지만 미래 구현을 사전에 “100% 정확”하다고 보장할 수는 없습니다. 사실은 repository baseline에 연결하고 선택은 명시적 decision으로 관리하며 모든 work package에 검수, 호환, 증거, 복구 조건을 둡니다.

## 1. 목표, 범위, 비목표

필수 DB 기반과 R-01~R-10을 구현합니다. 승인 규칙 UX, 공통 Starter, service/CRUD generator, preset, lightweight mode, observability, frontend quality, 4개 언어 delivery, screenshots, 최종 cleanup이 범위입니다. 관련 없는 업무 domain 추가, technology stack 교체, 운영 Secret 생성, CAPTCHA/인증 bypass는 범위가 아닙니다.

## 2. 기준선과 원칙

각 phase 전 branch, commit, dirty files, tool version, module/file count, Compose services, DB state, build, lint, E2E를 기록합니다. 사용자 변경을 보호하고 중국어 architecture/domain docs를 사실 원천으로 사용합니다. forward-only DB, fail-closed tenant/security, 단일 metadata catalog, 결정적 생성, 격리 fixture, 독립 증거를 원칙으로 합니다.

## 3. 결정 D-01~D-09

| Decision | 기본 방침 |
|---|---|
| D-01 | Liquibase와 `omni-db-migrator`가 schema version을 관리하며 기존 DB는 fingerprint와 backup 후에만 adopt한다. |
| D-02 | 승인 preview와 실제 제출은 같은 server resolver/result model을 사용한다. |
| D-03 | 공통 infrastructure는 `omni-common-service`, 업무 semantics는 각 domain service에 둔다. |
| D-04 | version 관리된 module catalog를 generator/preset의 유일한 사실 원천으로 사용한다. |
| D-05 | generator는 plan/dry-run을 먼저 수행하고 충돌을 기본 거부하며 atomic write한다. |
| D-06 | preset은 dependency-closed 결과물이며 runtime feature flag가 아니다. |
| D-07 | lite/full은 같은 application code를 사용하고 선언적 infrastructure/config만 다르다. |
| D-08 | OTel 호환 trace, Prometheus metrics, structured logs, dashboard, alert, SLO를 관측 기준으로 한다. |
| D-09 | 중국어를 원문으로 하며 manifest와 사람 검토 전에는 synchronized로 표시하지 않는다. |

대체 결정은 의존 작업 전에 ADR로 기록합니다.

## 4. 목표 구조와 순서

DB migrator, common service starter, scaffold CLI/catalog/templates, preset 정의/유지보수 문서, observability profile, docs manifest, screenshot suite, evidence, cleanup report를 구축합니다. DB versioning은 generator/cleanup보다 먼저, Starter 추출은 template보다 먼저, 안정된 UI/fixture는 최종 이미지보다 먼저입니다.

## 5. WP-00: DB 버전 관리

모든 SQL, table, seed, vendor schema, procedure, tenant provisioning을 조사하고 순서화된 Liquibase changelog와 `omni-db-migrator`를 만듭니다. read-only preflight, 기존 DB fingerprint/adopt, backup 필수, checksum, lock, failure recovery를 구현하고 필요한 일회성 procedure는 테스트된 Java orchestration으로 대체합니다.

빈 DB fresh, 비식별 기존 DB upgrade, 반복 실행, 중단 복구, 신규 tenant provisioning을 검증합니다. rollback은 backup/restore와 application compatibility를 사용하며 현장에서 추측한 파괴적 reverse SQL을 사용하지 않습니다.

## 6. WP-01: 업무 친화적 승인 규칙 UI

업무 이름과 호환 migration, 게시된 workflow options, 안전한 match/coverage/impact preview, 이해하기 쉬운 component를 구현하고 기존 permission code를 유지합니다. preview와 실제 제출은 같은 resolver를 사용하며 경계, fallback, overlap/gap, 미게시 flow, audit, Trace ID, 3개 viewport를 검증합니다.

## 7. WP-08: frontend lint/타입 관리

unsafe `any`, console, reactivity, formatting 순으로 정리하고 BPMN과 복잡한 API에 narrow adapter/type guard를 둡니다. lint error 0/warning 0, production build, critical browser E2E를 CI gate로 적용하며 규칙을 약화해 warning을 숨기지 않습니다.

## 8. WP-02: 공통 Service Starter

중복/책임 matrix를 먼저 만들고 tenant, gateway pre-auth, DataScope wiring, internal API auth, XSS, audit 등 infrastructure만 추출합니다. interceptor order와 fail-closed를 유지하고 CRM pilot 뒤 SRM, Procurement, Asset, Base 적용 영역으로 전개합니다. Auth/Gateway 예외는 명시합니다.

auto-config condition/opt-out, tenant isolation, DataScope, XSS, internal auth, permission, 4개 service regression을 검증하며 채택이 확인될 때까지 이전 구현으로 되돌릴 수 있어야 합니다.

## 9. WP-03: `create-service` CLI

catalog validation, deterministic plan, dry-run, conflict report, 덮어쓰기 거부, atomic write를 구현합니다. Maven module/layer, config, test, Docker/Compose, Gateway/Nacos, frontend/API/i18n/menu/permission, XSS SPI, docs, lock metadata를 생성합니다.

golden service가 build, start, health/security smoke, drift 없는 재생성, 기록된 plan을 이용한 removal을 통과해야 합니다. 생성 결과와 template 수정은 별도 commit으로 검토합니다.

## 10. WP-04: 풀스택 CRUD generator

안전한 type mapping, constraint, ownership/DataScope, permission, UI field를 포함한 descriptor를 정의합니다. forward-only Liquibase, backend layers/tests, frontend API/route/page/i18n, permission/menu/seed assertions, E2E skeleton을 생성합니다. CRUD, validation, pagination, authorization, tenant, ownership, XSS, regeneration, clean build를 golden test로 확인합니다.

## 11. WP-05: 프로젝트 preset

`core`, `workflow`, `crm`, `supply-chain`, `full`의 의존성 폐쇄와 잘못된 조합 검증을 정의하고 report와 함께 target directory에 생성합니다. source monorepo를 암묵적으로 수정하지 않습니다. 생성 dependency matrix와 4개 언어 user/maintainer guide를 유지하며 각 preset의 fresh generate, build, DB init, start, login, core E2E를 검증합니다.

## 12. WP-06: Lightweight mode

Compose profile/local config와 module-focused commands를 추가합니다. optional infrastructure가 없을 때는 명확한 degradation message를 제공합니다. 시작 시간/resource와 full behavior를 비교하고 business code, security, schema, contract가 동일함을 검증합니다.

## 13. WP-07: Observability

gateway, HTTP/Feign, workflow, job, MQ의 Trace ID/MDC를 통일하고 OTel export, Prometheus, cardinality가 제한된 metric, structured log, local Grafana/Tempo/Loki/Alloy, dashboard, alert, SLO를 제공합니다. 동기/비동기 trace, log correlation, metrics, alert, profile off, sensitive filtering, overhead를 검증합니다.

## 14. WP-09: 4개 언어 문서와 이미지

`docs-manifest`에 source/translation hash와 review state를 기록합니다. ja-JP/ko-KR UI와 다중 language selector를 완성하고 중국어 fact docs를 갱신한 뒤 번역과 사람 검토를 수행합니다. 4개 README의 의미를 맞춥니다.

격리된 Playwright docs suite와 비운영 deterministic fixture를 사용합니다. manifest에 stable ID, language, role, route, viewport, 전제, mask, 예상 결과를 기록하고 public/auth, system, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/monitoring, permission, failure, desktop, 중요 mobile flows를 포함합니다. password, CAPTCHA 답, token, 개인정보/운영 데이터를 저장하지 않습니다. CI는 link, translation drift, image reference/orphan, sensitive content, critical browser execution을 검사하며 초보자 walkthrough를 증거화합니다.

machine translation은 `present-unverified`로 유지하고 이름/날짜가 있는 사람 review 후에만 synchronized로 표시합니다.

## 15. WP-10: 최종 Cleanup

앞선 gate 완료 뒤 temporary name/extension, untracked, SQL, BPMN, images, reference를 조사하고 keep/promote/replace/delete로 분류합니다. 재사용 로직을 먼저 정식화하고 category별 commit으로 삭제한 뒤 dangling reference scan을 수행하며 Git tag/history를 복구 수단으로 둡니다.

최종 repository에는 production source, formal tests, build/deploy config, final docs/images, templates, 명확한 진입점과 owner가 있는 stable automation만 둡니다. 수동 SQL은 `scripts/sql/seed`의 최종 멱등 seed만 허용합니다. release 전에 fresh/upgrade, 모든 preset, full build/E2E, security regression, `cleanup-report`를 완료합니다.

## 16. Global gate와 release

G0 baseline, G1 DB, G2 approval/frontend, G3 starter, G4 generators, G5 presets/lite, G6 observability, G7 docs/screenshots, G8 cleanup/release입니다. 실패한 gate는 의존 작업을 중단합니다. schema/API 추가, 호환 producer/consumer 배포, 전환, 관측, 구 코드 제거의 작은 단계로 release하며 backup, restore, application rollback, data compatibility를 기록합니다.

## 17. 작업량/Token

총 131~199인일, 7.05M~11.65M token, 운영 목표 약 9.5M이며 12M 전에 재승인합니다. WP-09는 25~40일, 1.50M~2.60M token으로 가장 큰 문서 항목입니다. 읽기, 생성, tool output, retry, test, 분석, 문서를 포함한 계획치이며 가격 보장이 아닙니다.

## 18. Risk control

P0는 기존 DB 오인 adopt, tenant init 누락, security/interceptor 변화, preview/submission 불일치, generator overwrite, 불완전 preset graph, cleanup 오삭제입니다. fingerprint/backup, contract test, fail-closed regression, shared evaluation, dry-run/atomic write, dependency closure, inventory, 독립 review, recovery exercise로 통제합니다. translation drift, screenshot leak/flaky, telemetry overhead, lite/full divergence, external version drift, budget overrun은 owner와 증거가 있는 P1으로 관리합니다.

## 19. 실행 순서

S0는 baseline/WP-00, S1은 WP-01/WP-08, S2는 WP-02, S3은 catalog/WP-03/WP-04, S4는 WP-05/WP-06, S5는 WP-07, S6은 사람 검토와 초보자 walkthrough를 포함한 WP-09, S7은 inventory, cleanup, 최종 acceptance, release evidence입니다. ticket에는 dependency, scope, test, rollback, evidence를 기록합니다.

## 20. 증거와 계획 유지보수

`docs/evidence/scaffold-upgrade`에 commit, environment, commands, exit code, pass/fail/skip, 비식별 DB provenance, Compose health, E2E index, performance, limitations, owner, follow-up을 저장합니다. 대용량 log/trace는 CI artifact에 둡니다. code fact가 바뀌면 fact docs와 이 계획을 갱신하고 package 20%/전체 10%를 넘는 추정 변화는 이유를 기록합니다.

## 21. Ready/Done

D-01~D-09 승인 또는 ADR, reviewer/owner, 격리 infrastructure/browser, 비식별 upgrade DB, CAPTCHA bypass 없는 단기 test authentication, 사용자 변경 보호가 Ready 조건입니다.

WP-00과 R-01~R-10, backend/frontend, CI browser, 5개 presets, fresh/upgrade/recovery, generators, lite/full, observability, 4개 언어 UI/docs/images 사람 검토, beginner walkthrough, cleanup, security, backup/rollback drill, 실제 공수/token/risk 기록이 모두 완료되어야 Done입니다.
