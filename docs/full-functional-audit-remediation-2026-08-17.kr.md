# Omni-Stack 전체 기능 감사 수정 보고서

> 기준: [2026-08-14 감사](full-functional-audit-2026-08-14.kr.md). 수정·재검증일: 2026-08-17. 사람의 검토가 필요한 기술 번역 초안입니다.

## 1. 종합 결과

확정 문제 32개를 모두 수정·재검증했습니다. blocker 5/5, severe 12/12, medium 15/15. Maven reactor 18/18, 498 test에서 failure/error/skip 없음, frontend build 성공, lint 0 error(당시 197 warning), Chromium E2E 18/18, `npm audit` 0 vulnerability, 컨테이너 15개와 선언된 health check가 정상이었습니다.

당시 평가는 90/100 pre-production candidate입니다. 실제 OAuth, capacity/chaos, backup restore, 운영 telemetry와 독립 security test는 별도 production gate입니다. 이후 WP-08이 197 warning을 제거했고 Compose/module baseline도 변경되어 최신 evidence가 과거 count를 대체합니다.

## 2. Blocker 종료

- B-01: Jackson 2/3와 공통 날짜 형식을 지원하는 Feign decoder로 quotation, MQ와 Asset backfill 복구.
- B-02: Auth User DTO/VO allowlist와 password JSON ignore로 hash 출력 방지.
- B-03: Jackson 3 request string sanitizer와 Jackson 2 호환으로 JSON XSS 복구.
- B-04: SRM이 `SRM_SUPPLIER_ONBOARDING` published model을 멱등 해석·초기화.
- B-05: Asset seed/initializer/guard/doc을 `ASSET_TRANSFER` / `ASSET_DISPOSAL`로 통일하고 숫자 model ID UI 제거.

## 3. Severe 종료

User write를 allowlist DTO와 tenant validation으로 바꾸고 Docker/CI에서 test를 실행했습니다. Secret은 `.env` 필수, 내부 port는 loopback/private, 공개 진입점은 Frontend/Gateway뿐입니다. Nginx HTML/static security header와 CSP, assertion E2E, Base/Gateway test, real dashboard, named MySQL volume, XSS fail-safe, menu state/retry, empty permission fail-closed와 403/404 deep link를 구현했습니다.

## 4. Medium 종료

32자리 Trace ID, Asset searchable selector, approval processing/retry, Gateway/Nginx header 분담, script/port/README 4개 언어 동기화, lazy chunk와 750KB budget, CRM MySQL CI, public form 빈 값, tenant password 필수, warning 정리, Workflow 409, logout/expiry redirect, error/loading/empty 분리, Asset validation, 올바른 empty text와 390×844 Portal을 확인했습니다.

## 5. 재검증 중 추가 수정

primary unit 없는 social/portal user는 Asset candidate에서 제외하되 tenant mismatch는 fail closed를 유지했습니다. Procurement backfill/consumer는 tenant + TENANT scope를 설정하고 `finally`에서 제거했으며 replay는 duplicate card를 만들지 않았습니다. Nginx header/cache, frontend dependency, tenant admin password와 JDK 25 container warning도 수정했습니다.

## 6. 증거와 남은 production gate

종료 증거: Maven 18/18·498 test, real MySQL test 4개, frontend build/lint, Chromium 18/18, dependency 0, HTTP security, Asset backfill created=8/replay duplicate=8, named volume, 컨테이너 15개. 종합 90/100.

남은 항목은 실제 OAuth, capacity/soak/fault, backup/cross-host restore/DB rollback, production metrics/log/alert/SLO, 독립 SAST/DAST/penetration과 demo identity/secret 교체입니다. lint warning은 후속 WP-08에서 종료되었습니다.
