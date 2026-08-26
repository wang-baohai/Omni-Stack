# Omni-Stack High-Efficiency Scaffold Upgrade Roadmap

> Translation status: engineering draft translated from the Chinese source. Human review is still required.
> Source baseline: `docs/scaffold-upgrade-plan.md` (2026-08-17 proposal).

## 1. Upgrade objective

The next phase turns the existing enterprise application foundation into a reusable delivery scaffold. It must generate services and CRUD modules, support project presets, centralize security and tenant concerns, provide business-friendly administration, ship complete multilingual guidance, and include production-grade quality and observability templates. The target reuse maturity is 92–95/100.

## 2. Roadmap

| ID | Priority | Item | Completion outcome |
|---|---|---|---|
| R-01 | P0 | Business-friendly requisition approval rules | Procurement users can create, preview, test, and validate rules without model-version IDs or interval notation. |
| R-02 | P0 | `create-service` CLI | One command produces a buildable service, configuration, tests, Docker/Gateway wiring, and documentation skeleton. |
| R-03 | P0 | Shared business starter | Tenant, pre-authentication, DataScope, internal API authentication, XSS, and audit capabilities are declarative dependencies. |
| R-04 | P1 | Full-stack CRUD generator | A standard master-data module can be generated, adjusted, and accepted within half a day. |
| R-05 | P1 | Project presets | `core`, `workflow`, `crm`, `supply-chain`, and `full`, with a dependency matrix and maintenance guide. |
| R-06 | P1 | Lightweight development mode | A developer can work on one module without starting the full container stack. |
| R-07 | P1 | Observability template | Metrics, structured logs, tracing, dashboards, alerts, and SLO examples are ready by default. |
| R-08 | P1 | Frontend type/lint cleanup | `npm run lint` reaches zero errors and zero warnings and becomes a CI gate. |
| R-09 | P0 | Four-language documentation and screenshots | Chinese, English, Japanese, and Korean guides share the same scope and reproducible workflow screenshots. |
| R-10 | P0 | Delivery cleanup | Only final delivery files remain; manually maintained SQL is limited to final idempotent seed data. |

## 3. R-01: requisition approval rules

Rename “Approval Routing” to “Requisition Approval Rules”. Organize the screen around the business sequence: explanation, match tester, rule list, three-step wizard, and coverage warnings. Show category, inclusive lower amount, exclusive upper amount, understandable approval steps, published workflow name/version, and status. Keep route code, numeric model-version ID, and priority under advanced information.

The server must expose one shared evaluation result used by both the read-only tester and real requisition submission. Validate invalid categories, overlapping or missing ranges, unpublished workflows, default-rule coverage, and permissions. Existing instances remain bound to the workflow version with which they started. Audit every create, update, enable/disable, and delete operation.

Acceptance includes boundary values, specific-category precedence over default rules, failure states, preview/submission consistency, and desktop, tablet, and 390×844 mobile layouts.

## 4. R-05: presets and maintenance documentation

Presets are backed by one machine-readable catalog. The user guide explains selection, generated contents, first startup, expansion/removal, resource needs, and troubleshooting. The maintainer guide explains schema, dependency closure, module changes, compatibility, deprecation, verification, rollback, and custom-preset creation. A searchable matrix must cover backend modules, frontend routes and menus, permissions, configuration, Compose services, ports, messaging, and database dependencies.

CI validates catalog names, generator options, README navigation, all four languages, and golden outputs. A maintainer must be able to add a custom preset without reading generator source code.

## 5. R-09: documentation, README, and screenshots

Chinese is the source language; `*.en.md`, `*.jp.md`, and `*.kr.md` are synchronized translations. Required guide groups cover quick start, authentication/RBAC, system/security, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/operations, and extension development.

All four READMEs must state current versions, modules, architecture, startup modes, health checks, documentation navigation, verified quality results, production boundaries, generators, and presets. They must not expose default plaintext passwords.

The dedicated Playwright documentation suite records complete workflows for public/authentication, system management, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/monitoring, permissions, and error states. Each manifest entry records role, route, language, viewport, fixture, action, expected result, and masking. Desktop uses 1440×900; important forms and the supplier portal also use 390×844. Secrets, CAPTCHA answers, JWTs, personal data, and production data are never captured.

Every beginner guide follows the same pattern: purpose, roles, prerequisites, numbered screenshots, expected results, errors/remedies, upstream/downstream relationships, and API/permission/design references.

## 6. R-10: mandatory final cleanup

Cleanup happens only after implementation, tests, documentation, and screenshots are complete. First produce a keep-list and deletion inventory and scan source, Compose, CI, tests, and docs references. Promote reusable logic into formal CLI, test, screenshot, or module code before removing temporary implementations.

Remove obsolete one-off SQL, Python, JavaScript/TypeScript, shell, PowerShell, batch, debug, backup, export, cache, and intermediate files. Database structure and upgrades must already be managed by the formal migration mechanism. The only manually maintained SQL left under the repository is final idempotent seed data. Git history remains the recovery source.

Acceptance requires dangling-reference scans, fresh and upgrade database tests, preset generation, full builds/E2E, a cleanup report, and an independently reviewable cleanup commit.

## 7. Phases and dependencies

1. Freeze the baseline and establish database version management.
2. Deliver the visible approval-rule experience and clear frontend lint debt.
3. Extract the common starter.
4. Build metadata-driven service and CRUD generators.
5. Deliver presets and lightweight mode.
6. Deliver observability.
7. Complete four-language UI/docs/screenshots and beginner walkthroughs.
8. Perform final cleanup and release acceptance.

Database versioning precedes generators, presets, and destructive cleanup. Shared starter extraction precedes generated templates. Screenshot finalization follows stable UI and fixtures.

## 8. Quality gates

Each phase must preserve JDK 25 Maven reactor success, frontend lint/build success, tenant and DataScope isolation, permission and internal-call security, XSS behavior, database fresh/upgrade/idempotency/recovery, browser E2E, and documented rollback. CI must execute tests rather than merely list scenarios.

## 9. Effort and budget

The roadmap estimate is 131–199 person-days and 7.05M–11.65M tokens, with an operating target near 9.5M and a hard planning ceiling near 12M. Calendar estimates are 26–40 weeks for one person, 16–24 weeks for two full-stack engineers, or 10–16 weeks with backend, frontend/UX, platform/test, and documentation/localization responsibilities running in parallel. These token numbers are planning estimates, not pricing guarantees or fixed ChatGPT credits.

## 10. Definition of Done

R-01 through R-10 are complete only when all gates have auditable evidence; five presets work from clean directories; fresh and upgrade databases pass; generators follow current architecture/security conventions; lite and full use one business code path; the four UI languages, READMEs, guides, and screenshots have human review; a first-time user can start, explore, and extend the system; temporary artifacts are removed; and no known P0 risk, high-severity dependency vulnerability, secret leak, cross-tenant access, or authorization defect remains.
