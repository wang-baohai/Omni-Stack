# Omni-Stack 전체 기능 감사 보고서

> 감사일: 2026-08-14. 이 문서는 수정 전 역사 기준입니다. 기록한 32개 항목은 이후 수정·재검증되었습니다. [2026-08-17 수정 보고서](full-functional-audit-remediation-2026-08-17.kr.md)를 참조하세요.
>
> 기술 번역 초안으로 사람의 검토가 필요합니다. ID와 증거 값은 중국어 원본을 유지합니다.

## 1. 당시 종합 결론

기능 형태는 넓게 구현되어 있었지만 2026-08-14 기준 배포 가능한 상태는 아니었습니다. blocker 5개, severe 12개, medium 15개를 기록했습니다. test skip package는 가능했지만 backend, lint, cross-service, security와 browser의 실제 gate가 모두 green은 아니었습니다.

주요 blocker는 공통 Feign/Jackson 날짜 decode, Auth password hash 노출, Spring Boot 4/Jackson 3 JSON XSS 미동작, SRM onboarding model 미배포, Asset Workflow category seed 불일치였습니다.

## 2. 범위와 합격 기준

code, API, DB/seed, RBAC/DataScope, frontend route/action, Compose runtime/log, 문서와 실제 browser flow를 감사했습니다. 반복 가능한 core flow, 올바른 tenant/permission, 설명 가능한 실패, 사용 가능한 default, build/test/lint green, 실행 가능한 regression과 문서-code 일치를 합격 기준으로 삼았습니다.

대상은 Auth, Base, Gateway, Workflow, CRM, SRM, Procurement, Asset, common starter, frontend, database/Compose와 documentation입니다.

## 3. Build와 runtime 증거

Frontend production build는 성공했지만 lint에 2 error와 약 200 warning이 있었습니다. test skip Maven package는 성공했지만 full backend gate는 SRM에서 실패했고 Procurement contract test도 실패했습니다. Asset test는 성공했습니다. 즉 image build는 release gate 증거가 아니었습니다.

실행 확인은 password/CAPTCHA, register, device code, menu/RBAC, user task ownership, Workflow approval, CRM, SRM Portal, Procurement approval/retry, RFQ, Goods Receipt→Asset, MQ, XSS와 관리자 route 42개를 포함했습니다.

## 4. Cross-module 결과

- Authentication, menu isolation, task ownership, Workflow assignee, Procurement requisition approve/reject/cancel/retry는 반복 가능했습니다.
- SELF, DEPT_AND_BELOW와 tenant mismatch rejection을 확인했습니다.
- Supplier Portal은 `SUPPLIER` role과 active association이 모두 필요했습니다.
- SRM 생성은 onboarding model 미배포로 실패했습니다.
- 날짜를 포함한 SRM→Procurement quotation, Procurement→Asset backfill, Base MQ aggregation이 공통 decoder로 실패했습니다.
- Redis XSS 설정이 있어도 위험 문자열이 저장되었습니다.
- 관리자 화면은 렌더링되었지만 무권한 deep link는 blank이고 network failure가 empty data로 보이는 경우가 있었습니다.

## 5. 문제 목록

Blocker B-01–B-05: 공통 날짜 decoder, password hash, JSON XSS, SRM default Workflow, Asset category.

Severe S-01–S-12: user mass assignment, 붉은 quality gate와 skip test, 안전하지 않은 secret/port, static security header, 약한 E2E, Base/Gateway test 부족, fake dashboard, DB persistence 문서 불일치, XSS fail-open, menu loop, empty permission fail-open, 무권한 blank page.

Medium M-01–M-15: Trace 부족, 숫자 ID UI, approval feedback, header 중복, port drift, bundle, CRM DB test skip, default credential, runtime warning, Workflow conflict code, logout/expiry, error/empty 혼동, Asset validation, 잘못된 empty 문구, mobile Portal.

## 6. 문서와 수정 방향

문서는 설계 이해에 유용했지만 목표 설계를 완료된 동작으로 표현한 부분이 있었습니다. target, current implementation과 verified evidence를 분리하고 SRM Workflow, Asset category, README closed-loop, port와 task behavior를 code와 동기화해야 했습니다.

P0는 security/core flow, P1은 backend/MySQL/lint/assertion E2E, P2는 selector/feedback/error/responsive/bundle, P3는 production operations/backup/observability/security/documentation을 담당했습니다. 현재 상태는 이 문서가 아니라 수정 보고서와 `docs/evidence/scaffold-upgrade/`를 참조하세요.
