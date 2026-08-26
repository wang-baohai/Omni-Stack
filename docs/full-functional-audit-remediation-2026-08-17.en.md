# Omni-Stack Full Functional Audit Remediation Report

> Baseline: [2026-08-14 audit](full-functional-audit-2026-08-14.en.md). Remediation and re-verification date: 2026-08-17. Engineering translation draft pending human review.

## 1. Overall result

All 32 deterministic audit findings were fixed and reverified: blockers 5/5, severe 12/12, medium 15/15. At that date, Maven reactor 18/18 succeeded with 498 tests and no failure/error/skip; frontend production build passed and lint had zero errors but retained 197 warnings; Chromium E2E passed 18/18; `npm audit` reported zero vulnerability; the 15-container stack was running and declared health checks were healthy.

The result was rated 90/100, a pre-production candidate suitable for internal trial, acceptance, and controlled staging. It did not waive real OAuth callbacks, capacity/chaos tests, backup restore, production telemetry, or independent security testing. Later scaffold-upgrade work removed the 197 lint warnings and changed the Compose/module baseline; current evidence supersedes old counts where explicitly recorded.

## 2. Closed blockers

- B-01: a shared Feign decoder supports Jackson 2/3 and `yyyy-MM-dd HH:mm:ss`; quotation, MQ aggregation, and Asset backfill were rerun.
- B-02: Auth user APIs use DTO/VO allowlists and entity password JSON ignore; list/detail no longer expose hashes.
- B-03: Jackson 3 request-string sanitization plus Jackson 2 compatibility restored JSON-body XSS defense with integration tests.
- B-04: SRM resolves and initializes the required `SRM_SUPPLIER_ONBOARDING` published model idempotently.
- B-05: Asset seeds, initializer, guards, and docs use `ASSET_TRANSFER` / `ASSET_DISPOSAL`; numeric model IDs were removed from the UI.

## 3. Closed severe findings

User writes use allowlist DTOs and tenant-bound association validation. Docker/CI execute tests instead of skipping them. Secrets are required through `.env`; internal ports are loopback/private and only Frontend/Gateway are public. Nginx static and HTML responses carry CSP and security headers. Assertion-based E2E covers public pages, seven admin modules, writable user tasks, employee 403, and supplier mobile isolation.

Base/Gateway tests were added; fake dashboard values were removed; MySQL uses a named persistent volume; XSS fallback fails safe; menu loading has an explicit state machine and retry page; empty permission sets fail closed; unauthorized deep links render stable 403/404 pages.

## 4. Closed medium findings

Cross-service errors preserve a 32-character Trace ID. Asset numeric user/unit/supplier fields became tenant-restricted searchable selectors. Approval starts expose processing and retry states. Gateway/Nginx header responsibility was separated. Startup scripts, ports, README, and four deployment languages were aligned. Lazy loading and chunk boundaries introduced a 750 KB budget. CRM MySQL tests run in CI.

Public forms no longer prefill credentials; tenant creation requires an explicit password. Controlled warnings were removed or documented. Completed Workflow tasks return 409 instead of 500. Logout and token expiry are distinct and preserve a safe redirect. Error, loading, and empty states are separate. Asset forms show validation, workflow empty states use correct business text, and the supplier portal supports 390×844.

## 5. Extra defects fixed during re-verification

Asset candidates skip social/portal users without a primary unit while tenant mismatch still fails closed. Procurement backfill and message consumption establish tenant and TENANT scope then clear them in `finally`; replay created zero duplicate cards. Nginx child locations explicitly preserve security headers and emit one cache policy. Vulnerable frontend transitive packages were upgraded. Tenant administrator passwords became explicit. JDK 25 native-access and JSON entry warnings were handled in container startup.

## 6. Evidence and maturity

Evidence at closure: Maven 18/18 and 498 tests; four real MySQL interceptor tests; frontend type/build; lint zero errors; Chromium 18/18; dependency audit zero; unique Gateway trace/security headers; Asset backfill first run created eight cards and replay reported eight duplicates; named-volume persistence; 15 running containers.

Scores were functional 93, cross-module 93, security 91, usability 89, engineering 90, documentation 92, operations 82; overall 90/100.

## 7. Remaining production gates

Use real GitHub/Gitee/Google applications; run capacity, soak, fault injection, and datastore recovery; rehearse backup/cross-host restore/database rollback strategy; connect production metrics/logs/alerts and SLO ownership; complete independent dependency/SAST/DAST/penetration tests; replace all demo identities and secrets. The historical lint warning item has since been closed by WP-08 evidence.
