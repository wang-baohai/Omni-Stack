# Omni-Stack 고효율 스캐폴드 다음 단계 업그레이드 계획

> 번역 상태: 중국어 원문을 바탕으로 작성한 기술 번역 초안이며 사람의 검토가 필요합니다.
> 원문 기준: `docs/scaffold-upgrade-plan.md`(2026-08-17 제안본).

## 1. 업그레이드 목표

다음 단계의 핵심은 업무 화면을 더 추가하는 것이 아니라 현재 기업용 기반을 재사용 가능한 개발 스캐폴드로 발전시키는 것입니다. 서비스/CRUD 생성, 프로젝트 프리셋, 공통 보안, 업무 친화적 관리 UI, 4개 언어 문서, 운영 품질과 관측성을 기본 제공하며 재사용 성숙도 92~95/100을 목표로 합니다.

## 2. 로드맵

| ID | 우선순위 | 항목 | 완료 결과 |
|---|---|---|---|
| R-01 | P0 | 구매요청 승인 규칙 UI | 담당자가 모델 버전 ID나 구간 표기 없이 규칙을 생성·미리보기·검증할 수 있다. |
| R-02 | P0 | `create-service` CLI | 한 명령으로 빌드 가능한 서비스, 설정, 테스트, Docker/Gateway, 문서 골격을 생성한다. |
| R-03 | P0 | 공통 업무 Starter | 테넌트, 사전 인증, DataScope, 내부 API 인증, XSS, 감사를 선언적으로 적용한다. |
| R-04 | P1 | 풀스택 CRUD 생성기 | 표준 기준정보 모듈을 반나절 안에 생성·조정·검수한다. |
| R-05 | P1 | 프로젝트 프리셋 | `core`, `workflow`, `crm`, `supply-chain`, `full`과 의존성 표 및 유지보수 안내를 제공한다. |
| R-06 | P1 | 경량 개발 모드 | 단일 모듈 개발 시 전체 컨테이너 스택을 시작하지 않는다. |
| R-07 | P1 | 관측성 템플릿 | 메트릭, 구조화 로그, 추적, Dashboard, 알림, SLO 예제를 기본 제공한다. |
| R-08 | P1 | 프런트엔드 타입/lint 정리 | `npm run lint`를 error 0, warning 0으로 만들고 CI 필수 게이트로 적용한다. |
| R-09 | P0 | 4개 언어 문서와 전체 흐름 스크린샷 | 중·영·일·한 문서가 같은 범위와 재현 가능한 흐름 이미지를 제공한다. |
| R-10 | P0 | 납품 정리 | 최종 산출물만 남기고 수동 SQL은 최종 멱등 seed 데이터로 제한한다. |

## 3. R-01: 구매요청 승인 규칙

“승인 라우팅”을 “구매요청 승인 규칙”으로 바꾸고 설명, 매칭 테스터, 규칙 목록, 3단계 마법사, 커버리지 경고 순으로 구성합니다. 품목 분류, 포함 하한 금액, 미포함 상한 금액, 이해 가능한 승인 단계, 게시된 흐름 이름/버전, 상태를 표시하고 코드, 숫자 모델 버전 ID, 우선순위는 고급 정보로 이동합니다.

읽기 전용 테스터와 실제 요청 제출은 서버의 동일한 평가 로직과 결과 모델을 사용합니다. 잘못된 분류, 구간 중복/누락, 미게시 흐름, 기본 규칙 부족, 권한 부족을 검증하며 기존 인스턴스는 시작한 버전을 유지합니다. 생성, 수정, 활성화/비활성화, 삭제는 모두 감사 기록에 남깁니다.

경계값, 구체 분류 우선, 오류 상태, 미리보기와 실제 제출의 일치, desktop/tablet/390×844 mobile 화면을 인수 시나리오에 포함합니다.

## 4. R-05: 프리셋과 유지보수 문서

기계 판독 가능한 단일 카탈로그를 사실의 원천으로 사용합니다. 사용자 안내서는 선택, 생성 내용, 최초 시작, 확장/제거, 자원 요구, 문제 해결을 설명합니다. 유지보수 안내서는 schema, 의존성 폐쇄, 모듈 변경, 호환성, 폐기, 검증, rollback, 사용자 프리셋 작성을 설명합니다. 의존성 표에는 backend, frontend route/menu, 권한, 설정, Compose, port, message, database가 포함됩니다.

CI는 카탈로그, CLI 선택지, README, 4개 언어, golden output의 일관성을 검증합니다. 유지보수자는 생성기 소스를 읽지 않고도 사용자 프리셋을 추가할 수 있어야 합니다.

## 5. R-09: 문서, README, 스크린샷

중국어를 사실 원본으로 하고 `*.en.md`, `*.jp.md`, `*.kr.md`를 동기화합니다. Quick Start, 인증/RBAC, System/Security, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/운영, 확장 개발을 필수 문서 그룹으로 관리합니다.

4개의 README는 현재 버전, 모듈, 구조, 시작 모드, health check, 문서 탐색, 검증된 품질, production 경계, generator, preset을 같은 의미로 설명해야 하며 평문 초기 password를 포함하지 않습니다.

전용 Playwright 문서 스위트는 공개/인증, 시스템 관리, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/monitoring, 권한, 오류 상태의 전체 흐름을 기록합니다. manifest에는 role, route, language, viewport, fixture, 동작, 예상 결과, mask를 기록합니다. 기본 화면은 1440×900이며 중요 폼과 Supplier Portal은 390×844도 제공합니다. Secret, CAPTCHA 답, JWT, 개인정보, 운영 데이터는 촬영하지 않습니다.

초보자 가이드는 목적, 역할, 사전 조건, 번호가 있는 이미지, 예상 결과, 오류 해결, 상하위 모듈 관계, API/permission/설계 문서 참조의 공통 구조를 사용합니다.

## 6. R-10: 필수 최종 정리

구현, 테스트, 문서, 이미지가 완료된 후에만 수행합니다. 먼저 보존 목록과 삭제 후보를 만들고 source, Compose, CI, test, docs 참조를 검사합니다. 재사용할 가치는 정식 CLI/test/screenshot tool/module code로 옮긴 뒤 임시 구현을 삭제합니다.

불필요한 일회성 SQL, Python, JavaScript/TypeScript, Shell, PowerShell, Batch, debug, backup, export, cache, 중간 산출물을 제거합니다. DB 구조와 업그레이드는 정식 migration 관리로 전환되어 있어야 합니다. 수동 관리 SQL은 최종 멱등 seed만 남기고 Git 이력을 복구 근거로 사용합니다.

끊어진 참조 검사, fresh/upgrade DB, preset 생성, 전체 build/E2E, cleanup report, 독립적으로 검토 가능한 정리 commit이 인수 조건입니다.

## 7. 단계와 의존성

1. 기준선을 동결하고 DB 버전 관리를 수립한다.
2. 승인 규칙 UI를 제공하고 lint 부채를 제거한다.
3. 공통 Starter를 추출한다.
4. metadata 기반 service/CRUD generator를 만든다.
5. preset과 lightweight mode를 제공한다.
6. observability를 제공한다.
7. 4개 언어 UI/docs/screenshots와 초보자 검증을 완료한다.
8. 최종 정리와 release 인수를 수행한다.

DB 버전 관리는 generator, preset, 파괴적 cleanup보다 먼저이며 Starter는 생성 template보다 먼저입니다. 최종 screenshot은 UI와 fixture가 안정된 뒤 생성합니다.

## 8. 품질 게이트

각 단계에서 JDK 25 Maven reactor, frontend lint/build, tenant/DataScope 격리, permission/internal call security, XSS, DB fresh/upgrade/멱등/복구, browser E2E, rollback 증거를 유지합니다. CI는 시나리오를 나열하는 데 그치지 않고 실제 실행해야 합니다.

## 9. 작업량과 예산

추정치는 131~199인일, 7.05M~11.65M token이며 운영 목표 약 9.5M, 계획 상한 약 12M입니다. 1인 직렬 26~40주, 풀스택 2인 16~24주, backend·frontend/UX·platform/test·docs/localization 병렬 구성은 10~16주를 예상합니다. token은 계획 수치이며 가격 보장이나 고정 ChatGPT credits가 아닙니다.

## 10. Definition of Done

R-01~R-10의 감사 가능한 증거, 5개 preset의 clean generation, DB fresh/upgrade, 현행 규약을 따르는 generator, 동일 business code를 쓰는 lite/full, 4개 언어 UI/README/문서/이미지의 사람 검토, 초보자의 독립 사용, temporary artifact 제거, 알려진 P0·중대 dependency vulnerability·secret leak·cross-tenant/authorization 결함 없음이 모두 충족되어야 완료입니다.
