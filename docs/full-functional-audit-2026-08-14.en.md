# Omni-Stack Full Functional Audit

> Audit date: 2026-08-14. This is the pre-remediation historical baseline. All 32 recorded findings were later remediated and reverified; see the [2026-08-17 remediation report](full-functional-audit-remediation-2026-08-17.en.md).
>
> Translation status: engineering draft pending human review. IDs and evidence values follow the Chinese source.

## 1. Executive conclusion at the audit date

The system already had broad functional shape, but it was not ready for delivery on 2026-08-14. The audit recorded 5 blockers, 12 severe findings, and 15 medium findings. Compilation with skipped tests was possible, but the true backend, frontend lint, cross-service, security, and browser gates were not all green.

Primary blockers were: shared Feign/Jackson date decoding broke SRM quotation, Asset receipt backfill, and Base MQ aggregation; Auth exposed password hashes; JSON-body XSS protection did not execute correctly on the Spring Boot 4/Jackson 3 path; SRM lacked a usable default onboarding model; Asset workflow category seeds did not match server guards.

## 2. Audit scope and standard

The review inspected code, API contracts, database and seeds, RBAC/DataScope, frontend routes and actions, Compose/runtime health, logs, documentation, and real browser flows. Passing meant repeatable core paths, correct tenant/permission boundaries, actionable failures, usable defaults, green build/test/lint, executable regression, and documentation matching code.

Modules reviewed: Auth, Base, Gateway, Workflow, CRM, SRM, Procurement, Asset, common starters, frontend, database/Compose, and documentation.

## 3. Build and runtime evidence

At the audit baseline, frontend production build passed but lint had two errors and roughly 200 warnings. Maven packaging with tests skipped passed, while the full backend gate failed in SRM and a Procurement contract test failed. Asset tests passed. This proved that image packaging was not equivalent to a release-quality gate.

Real runtime checks covered password/CAPTCHA login, registration, device code, menu/RBAC, user-task ownership, Workflow approval, CRM screens, SRM portal isolation, Procurement requisition approval/retry, RFQ integration, goods receipt/Asset backfill, MQ aggregation, XSS, and 42 administrator routes.

## 4. Cross-module flow results

- Authentication, basic menu isolation, user-task ownership, Workflow assignee enforcement, and Procurement requisition approve/reject/cancel/retry were repeatable.
- SELF and DEPT_AND_BELOW scopes and tenant mismatch rejection were verified.
- Supplier Portal required both the `SUPPLIER` role and an active association.
- SRM creation was blocked by the missing published onboarding model.
- SRM → Procurement quotation, Procurement → Asset backfill, and Base MQ aggregation failed when responses contained `LocalDateTime`; empty responses hid the common decoder defect.
- XSS tenant configuration existed in Redis, but dangerous strings were still persisted.
- Administrator pages rendered, but unauthorized deep links could produce a blank page and several network failures were shown as empty data.

## 5. Finding register

### Blockers B-01–B-05

1. Shared date decoder incompatibility across Jackson 2/3.
2. Password hash exposure from Auth entities.
3. JSON-body XSS defense not active.
4. Missing default SRM onboarding workflow.
5. Asset transfer/disposal workflow category mismatch.

### Severe S-01–S-12

The severe set covered user entity mass assignment, red quality gates and skipped Docker tests, unsafe default deployment/secrets/ports, incomplete static security headers, screenshot-only E2E, missing Base/Gateway tests, fake dashboard data, database persistence/documentation conflict, XSS fail-open fallback, dynamic-menu request loops, empty-permission fail-open, and blank unauthorized deep links.

### Medium M-01–M-15

The medium set covered trace-poor remote errors, numeric-ID forms, missing asynchronous approval feedback, duplicated security headers, script/port drift, bundle size, skipped CRM MySQL tests, prefilled public credentials, actionable runtime warnings, wrong Workflow conflict semantics, logout/expiry recovery, failures presented as empty states, invisible Asset validation, unrelated approval empty text, and poor mobile portal layout.

## 6. Documentation findings

Architecture and implementation documents were useful for intent, but several statements described desired design as delivered behavior. The audit required every document to separate target design, current implementation, and verified evidence. Notable mismatches involved SRM Workflow behavior, Asset workflow category values, README claims about the SRM→Procurement→Asset closed loop, deployment ports, and task behavior.

## 7. Required remediation and re-verification

P0 restored security and core cross-service flows. P1 made backend, MySQL integration, lint, and assertion-based browser suites mandatory. P2 improved selectors, asynchronous feedback, access/error states, responsive layout, and bundle boundaries. P3 covered production operations, backup/recovery, observability, security testing, and documentation synchronization.

The historical “not deliverable” module judgments in this report must not be read as current status. Current status is governed by the remediation report and later scaffold-upgrade evidence under `docs/evidence/scaffold-upgrade/`.
