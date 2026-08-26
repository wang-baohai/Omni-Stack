# Omni-Stack Scaffold Upgrade Implementation Plan

> Translation status: engineering draft translated from the Chinese source; human review is pending.
> The Chinese file `docs/scaffold-upgrade-implementation-plan.md` remains the execution source of truth.

## 0. Credibility boundary

This plan is executable, evidence-driven, and reversible, but no future implementation can be declared “100% correct” in advance. Facts are tied to a repository baseline; choices are explicit decisions; every work package has acceptance, compatibility, evidence, and rollback requirements.

## 1. Objective, scope, and non-goals

Implement R-01 through R-10 plus the prerequisite database baseline. Scope includes approval-rule UX, common starter, service/CRUD generators, presets, lightweight mode, observability, frontend quality, four-language delivery, screenshots, and final cleanup. It does not add unrelated business domains, replace the technology stack, create production secrets, or bypass CAPTCHA/authentication.

## 2. Baseline and principles

Before each phase, record branch, commit, dirty files, tool versions, module/file counts, Compose services, database state, builds, lint, and E2E. Preserve user changes. Chinese architecture and domain documents are system truth. Use forward-only database changes, fail-closed tenant/security behavior, a single metadata catalog, deterministic generation, isolated fixtures, and independent evidence.

## 3. Frozen decisions D-01–D-09

| Decision | Default |
|---|---|
| D-01 | Liquibase and `omni-db-migrator` own schema versioning; adopt existing databases only after fingerprint validation and backup. |
| D-02 | Approval preview and submission call the same server-side resolver and result model. |
| D-03 | Shared service concerns live in `omni-common-service`; business semantics remain in domain services. |
| D-04 | A versioned `modules.yaml`-style catalog is the single source for generators and presets. |
| D-05 | Generators plan and dry-run first, refuse non-empty/conflicting targets by default, and write atomically. |
| D-06 | Presets are dependency-closed outputs, not runtime feature flags. |
| D-07 | Lite and full modes use the same application code and differ only in declarative infrastructure/configuration. |
| D-08 | OpenTelemetry-compatible trace context, Prometheus metrics, structured logs, dashboards, alerts, and SLO templates form the observability baseline. |
| D-09 | Chinese docs are source; translations require manifest tracking and human review before synchronized status. |

Any replacement decision requires an ADR before dependent work starts.

## 4. Target structure and dependency order

Add a database migrator, shared service starter, scaffold CLI/catalog/templates, preset definitions and maintenance guides, observability profile, documentation manifest, screenshot suite, evidence directory, and cleanup report. Database versioning precedes generators and cleanup; common starter extraction precedes generated templates; stable UI and data fixtures precede final screenshots.

## 5. WP-00: database version management

Inventory every SQL file, table, seed, vendor schema, procedure, and tenant-provisioning dependency. Create ordered Liquibase changelogs and an `omni-db-migrator`. Implement read-only preflight, existing-database fingerprint/adopt, backup requirement, checksums, lock handling, and failure recovery. Replace one-off tenant procedures with tested Java orchestration where required.

Acceptance: empty-database fresh install, sanitized existing-database upgrade, repeated execution, interrupted migration recovery, and new-tenant provisioning all pass. Rollback uses database backup/restore and application compatibility, never destructive reverse SQL guessed at release time.

## 6. WP-01: business approval-rule UI

Add a business rule name and compatible migration; expose published workflow options safely; implement match, coverage, and impact previews; split the page into understandable components; and preserve existing permission codes. Preview and real submission share the resolver. Verify interval boundaries, fallback rules, overlap/gap/unpublished-flow errors, audit records, Trace ID errors, and three viewports.

## 7. WP-08: frontend lint and type governance

Resolve unsafe `any`, console usage, reactivity defects, and formatting in risk order. Add narrow adapters and type guards around BPMN and complex APIs. Gate CI at zero errors and zero warnings, with production build and critical browser E2E passing. Do not hide warnings by weakening rules.

## 8. WP-02: shared service starter

Build a duplication and responsibility matrix first. Extract only infrastructure-level tenant, gateway pre-authentication, DataScope wiring, internal API authentication, XSS, audit, and common configuration. Preserve interceptor order and fail-closed defaults. Pilot CRM, then migrate SRM, Procurement, Asset, applicable Base concerns, while Auth/Gateway exceptions remain explicit.

Acceptance includes auto-configuration condition tests, opt-out tests, tenant isolation, DataScope mapping, XSS, internal-call authentication, permission checks, and four-service regression. Each service can revert to its previous implementation until adoption is verified.

## 9. WP-03: `create-service` CLI

Validate the catalog, create a deterministic change plan, support dry-run, report conflicts, refuse accidental overwrite, and write atomically. Generate Maven module/layers, configuration, tests, Docker/Compose, Gateway/Nacos wiring, frontend/API/i18n/menu/permission skeletons, XSS SPI, documentation, and lock metadata.

A golden generated service must build, start, pass health/security smoke tests, regenerate without drift, and be removable using the recorded plan. Generated code and template changes are reviewed in separate commits.

## 10. WP-04: full-stack CRUD generator

Define a validated entity descriptor with safe type mappings, constraints, ownership/DataScope choices, permissions, and UI fields. Generate forward-only Liquibase changes, entity/DTO/mapper/service/controller/tests, frontend API/route/page/i18n, permissions/menu/seed assertions, and E2E skeletons. Golden CRUD acceptance covers create/read/update/delete, validation, pagination, authorization, tenant isolation, ownership, XSS, regeneration, and clean build.

## 11. WP-05: project presets

Define `core`, `workflow`, `crm`, `supply-chain`, and `full` with dependency closure and invalid-combination validation. Generate into a target directory with a report; never mutate the source monorepo silently. Maintain a generated dependency matrix and four-language user/maintainer guides. Each preset must fresh-generate, build, initialize its database, start, authenticate, and pass its core E2E. Include one complete custom-preset maintenance example.

## 12. WP-06: lightweight mode

Add Compose profiles/local configuration and module-focused commands. Missing optional infrastructure must produce explicit degradation messages. Measure startup time and resources and compare behavior with full mode. The same business code, security, schema, and contracts must pass in both modes.

## 13. WP-07: observability

Unify Trace ID/MDC propagation across gateway, HTTP/Feign, workflow, jobs, and MQ. Add OpenTelemetry-compatible export, Prometheus registry, bounded-cardinality business metrics, structured logs, local Grafana/Tempo/Loki/Alloy (or equivalent) profile, dashboards, alerts, and SLO examples. Verify synchronous and asynchronous traces, log correlation, metrics, alert rules, disabled-profile behavior, sensitive-field filtering, and performance overhead.

## 14. WP-09: four-language docs and screenshots

Track each source/translation hash and review state in `docs-manifest`. Complete ja-JP and ko-KR UI without binary language assumptions. Update factual Chinese guides first, then translate and obtain human review. Keep README content semantically aligned.

Use an isolated Playwright documentation suite and deterministic non-production fixtures. Its manifest records stable ID, language, role, route, viewport, prerequisites, masks, and expected result. Cover public/auth, system, Workflow, Scheduling, CRM, SRM, Procurement, Asset, MQ/monitoring, permissions, failures, desktop, and important mobile flows. Never store passwords, CAPTCHA answers, tokens, or personal/production data. CI checks links, translation drift, screenshot references/orphans, sensitive content, and critical browser execution. A first-time user walkthrough is mandatory evidence.

Machine translations remain `present-unverified`; only a named human review with date may mark them synchronized.

## 15. WP-10: final cleanup

After all earlier gates pass, inventory temporary names, extensions, untracked files, SQL, BPMN, screenshots, and references. Classify each item as keep, promote, replace, or delete. Promote reusable logic first. Delete in category-specific commits, scan dangling references, and keep Git history/tag as recovery.

The final repository retains production source, formal tests, deployment/build configuration, final docs/screenshots, templates, and stable automation with a documented entry point and owner. Manually maintained SQL exists only under `scripts/sql/seed` and is final idempotent seed data. Re-run fresh/upgrade, every preset, complete builds/E2E, security regression, and produce `cleanup-report` before release.

## 16. Global gates and release strategy

G0 baseline; G1 database; G2 approval UX/frontend; G3 starter; G4 generators; G5 presets/lite; G6 observability; G7 docs/screenshots; G8 cleanup/release. A failed gate blocks dependent work. Releases use small compatibility steps: add schema/API, deploy compatible producers/consumers, switch usage, observe, then remove obsolete code. Document backup, restore, application rollback, and data compatibility for each change.

## 17. Workload and token planning

Estimated total: 131–199 person-days and 7.05M–11.65M tokens, with about 9.5M as an operating target and 12M as a reapproval threshold. WP-09 is the largest documentation/localization item (25–40 days, 1.50M–2.60M tokens). Estimates include reading, generation, tool output, retries, tests, analysis, and documentation; they are not supplier pricing commitments.

## 18. Risk controls

P0 risks are existing-database mis-adoption, lost tenant initialization, security/interceptor changes, preview/submission divergence, generator overwrite, incomplete preset graphs, and destructive cleanup. Controls are fingerprinting/backups, contract tests, fail-closed regression, shared evaluation, dry-run/atomic writes, dependency closure, inventories, independent review, and full recovery exercises. Translation drift, screenshot instability/leaks, telemetry overhead, lite/full divergence, external version drift, and budget overrun are tracked P1 risks with owners and evidence.

## 19. Executable sequence

S0 records the baseline and completes WP-00. S1 delivers WP-01 and WP-08. S2 extracts WP-02. S3 creates the catalog, WP-03, and WP-04. S4 implements WP-05 and WP-06. S5 delivers WP-07. S6 completes WP-09, including human review and new-user walkthrough. S7 inventories, cleans, runs final acceptance, and publishes the release evidence. Each ticket states dependency, scope, tests, rollback, and evidence; commits use Conventional Commits and separate logic, generated output, formatting, and deletion.

## 20. Evidence and plan maintenance

Store concise phase evidence under `docs/evidence/scaffold-upgrade`: commit, environment, commands, exit codes, pass/fail/skip counts, sanitized database provenance, Compose health, E2E index, performance comparison, limitations, owners, and follow-up tickets. Large logs and traces stay in CI artifacts. Update factual docs and this plan when code facts change; record estimate changes above 20% per package or 10% overall.

## 21. Ready and Done

Implementation is ready when D-01–D-09 are accepted or replaced by ADRs, reviewers and responsibility owners are assigned, isolated infrastructure/browser environments and a sanitized upgrade database exist, short-lived test authentication is controlled without CAPTCHA bypass, and user changes are protected on an implementation branch.

The upgrade is done only when WP-00 and R-01–R-10 pass all gates; backend, frontend, CI browser tests, five presets, fresh/upgrade/recovery, generators, lite/full, observability, four-language UI/docs/screenshots with human review, beginner walkthrough, cleanup, security, backup/rollback drills, and final actual effort/token/risk records are complete.
