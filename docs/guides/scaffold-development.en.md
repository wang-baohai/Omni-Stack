# Presets, Container Development, and Service Extension

Scope: CLI 0.6.1, service template 1.5.1, CRUD template 1.1.1 and preset delivery template 1.1.0, checked on 2026-10-17. The operator is a local developer; business login is not required. generated is separate learning output, not production seeds. Prepare the Docker tool image, dependencies and working-directory mappings using [Quick Start](quick-start.en.md). Run services, builds, tests and browsers in local Docker. Use a separate learning project without personal uncommitted changes for integration experiments.

## 1. Choose and generate a preset

A preset selects modules; the catalog computes their dependency closure. preset list should show core, workflow, crm, supply-chain, full. explain crm prints explicit and resolved modules. Read the [Dependency Matrix](../preset-dependency-matrix.en.md) for module and resource budgets.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain crm
```

Running a service subset uses the original repository. Generating a trimmed project copies selected code, migrations, seeds, four-language Wiki and applicable AI assets into a new directory. Preview with --dry-run, then remove it to create; this command has no --apply. The output must be absent or empty.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/wiki-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/wiki-crm
```

Expect `preset created: crm@...` and `generated preset valid`. The sourceDigest and scaffoldDigest in scaffold.lock bind the source and generated projects’ scaffold/ directories respectively; catalog, preset, documentation and AI inputs have their own digest/resource checks. The lock does not sign all application source or prove compilation or business acceptance. In the new directory, use its own compose.tools.yaml, compose.docs.yaml and ai/adapters/bootstrap.mjs, configure its repository URL, and create a private .env through quick-start. Do not depend on the original repository’s absolute path.

## 2. Run a subset and understand lite mode

dev provides up, down, status, doctor and logs; dev plan does not exist. Before invoking Docker, dev up prints modules, services, mode and degradation details. core/workflow/crm default to lite, disabling Nacos clients, XXL-JOB, MQ relay and autodetect while configuration still permits transactional Outbox writes. That is not evidence of an actual insertion or delivery. An unsafe-consumer closure containing SRM/Procurement/Asset selects full when mode is omitted and rejects explicit lite; full keeps .env authoritative.

These commands use the standard omni-stack project and fixed ports, including MySQL 3306. Inspect ports, project, volumes and ownership before using an existing stack. Isolated verification uses owned volumes and internal networking without moving business ports; down retains volumes. The CLI tool image needs Node.js, Docker CLI and JDK 25, with Compose mapping the Docker socket and project directory. doctor may exit 0 with SKIP items. Linux tool containers can skip host-port detection, so inspect Docker published ports separately. Its Nacos probe checks server APIs/server lists, not the registration of every application.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset crm --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev status --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev logs --module crm --tail 200 --project-name omni-stack
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev down --project-name omni-stack
```

## 3. Generate and integrate a service bundle

create-service previews by default. Its --apply creates a bundle with omni-service.lock.json; it does not integrate the monorepo. Run service validate and service integrate next. integrate is also read-only by default; its separate --apply writes Maven, catalog, Gateway, Compose, permissions, menus and migration entry points. First generate a separate core learning project:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
cd generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- create-service inventory --output generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service validate inventory --output generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- service integrate inventory --source generated/inventory-bundle --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-inventory,omni-db-migrator -am
```

Expect no CONFLICT in the plan and integration applied after applying it. Resolve port conflicts, bundle digest changes or uncommitted target files in a Git project without disabling guards or forcing overwrite. omni-inventory exists as a Maven module only after integration. The service uses Servlet common starters. Check that the common configuration’s XssConfigProvider SPI is active under the actual classpath/settings and extend it as needed; do not copy the reactive Gateway chain. The skeleton still needs domain validation, DataScope table mappings and denial tests.

Both planner and renderer now cover zh-CN, en-US, ja-JP and ko-KR frontend locales. When the target has .git, all four locale files participate in the pre-write dirty-file check. A learning project without .git has no such Git guard; establish and commit a clean baseline when that protection is needed. Four registered locale entries do not prove translated domain terminology. New documents/translations remain present-unverified until actual semantic review; owner-authorized AI_REVIEW suffices without mandatory human review, and generation cannot approve itself.

<!-- omni:modules:procurement -->
## 4. Generate CRUD

Current CRUD specifications support procurement only. Use scaffold/specs/material-brand.yaml in a separate learning project containing that module. The command is generate crud --spec: --check validates the specification, --dry-run renders a plan, and --apply writes atomically across files; dry-run and apply are mutually exclusive. Review module, permission, DataScope and fields to avoid existing-table conflicts.

The cd ../.. below continues section 3 and returns from generated/learning-core to the original repository root. If reading this section independently, start at that root and omit this cd command.

```bash
cd ../..
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
cd generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
```

Output proves specification validation or the generation transaction, not business behavior. Run backend and frontend checks, permission denial tests and database fresh/upgrade verification; synchronize four-language explanations. Append forward-only Liquibase changeSets and refresh seed hashes/assertions rather than adding temporary migration SQL.

CRUD currently generates Chinese documentation and ja-JP/ko-KR terminology YAML, not complete four-language documents. Runtime locale registration covers four languages, but entity/field labels can still be source-language placeholders. Translate them, inspect the pages and conduct authorized semantic review. AI_REVIEW satisfies the current document policy; registration or lock validation alone cannot grant approval.

<!-- omni:modules:end -->

## 5. Add or remove modules and upgrade

Disable: choose a smaller dev up closure while retaining code and upgrade paths. Trim: preset create delivers a new project with checked locks, navigation, images and AI rules. Permanently remove: audit reverse dependencies, Gateway/Nacos/Compose, Maven, menu/routes, permissions/seeds, upgrades, consumers and jobs; migrate unique capabilities first. Never edit executed migrations. Existing-data upgrades require their own backup and rollback plan.

For new presets, maintain dependency closure and resource ownership in scaffold/catalog/modules.yaml and scaffold/presets/*.yaml, regenerate matrices, and verify all five presets. See [Custom Presets](../custom-preset-tutorial.en.md), [Maintenance](../preset-maintenance.en.md), and [Upgrade](../preset-upgrade-guide.en.md). Deleting a Maven module alone is incomplete.

## 6. Validate and clean up

Completion requires container JDK 25 reactor checks, frontend lint/build, applicable E2E type checks, permission/data-isolation negatives, fresh/upgrade, selected preset and lock verification, four-language content and actual semantic review under the [current policy](../adr/0003-authorized-ai-translation-review.md). Run from the learning project root:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev doctor
```

Keep volumes. Clean teaching records by this run’s unique business keys. List and verify the exact generated directories and any new personal content before removing owned output. Never overwrite unknown directories or use git clean/reset --hard or global Docker prune. Keep raw logs and tokens outside the repository. See [Source Debugging](../wiki/source-debugging.en.md).

## 7. Compare the actual terminal results

The [2026-10-12 acceptance record](../reference/evidence/scaffold-2026-10-12.json) records source digests, tool and image identities, times, and verification boundaries. These are real local Docker terminal transcripts shared by all four languages. Private output paths use placeholders; do not paste those placeholders into a shell.

[Preset selection](../reference/evidence/scaffold-preset-select-2026-10-12.txt) ran list, explain crm, core/supply-chain creation and validation, then validated core after moving it. These steps do not establish that all independent delivery gates for five presets passed.

[Service integration](../reference/evidence/scaffold-create-service-2026-10-12.txt) generated 32 locked files and changed 38 integration files for basic inventory-basic. The extended inventory-sample golden build is a different project: MQ/Job/DataScope, 33 generated files and 39 integrated files. core omits rocketmq and xxl-job and correctly rejects packages requiring them before integration writes. Choose a preset with those dependencies and preserve the guard.

<!-- omni:modules:procurement -->
[CRUD generation](../reference/evidence/scaffold-crud-generate-2026-10-12.txt) records actual --check, --dry-run, --apply, and repeat generation. The repeat reports unchanged: 18 locked files and 29 changes in the first transaction. A separate golden fixture passed backend clean verify and frontend build/lint with zero new lint errors. This does not replace complete business acceptance of the generated supply-chain project.
<!-- omni:modules:end -->

[Lite runtime](../reference/evidence/scaffold-lite-mode-2026-10-12.txt) used the relocated core project's own CLI, 14-module backend, and frontend build artifacts in a separate environment with no published host ports. Six runtime containers were ready and the one-shot migrator exited 0. Gateway → Auth tenant reading succeeded; anonymous access to a protected Base endpoint returned 401. Nacos, XXL-JOB, MQ relay, and consumers were disabled. This read-only probe did not verify Outbox writes or message delivery. doctor marked host-port scanning and Nacos registration SKIP, not PASS.

That historical runtime used private prebuilt images of verified artifacts. The earlier network limitation on formal multistage Docker builds remains a separate unpassed gate; --no-build is not a general startup shortcut. At the time, down retained named volumes, followed by deletion of only that run’s owned volumes. Final containers, business volumes and networks were zero, and the original teaching environment’s 24 container identities were unchanged. The dated record does not describe containers still running today.

The [current CLI record](../reference/evidence/scaffold-cli-2026-10-17.json) and [actual terminal text](../reference/evidence/scaffold-cli-2026-10-17.txt) add 10 successful commands from 2026-10-17: preset list/explain/preview, legal service-bundle preview/create/validate, CRUD --check and dev/dev up/service integrate help. The basic bundle has 32 locked files at template 1.5.1, was created once and remains in private evidence storage. An earlier numeric hyphen segment produced an invalid default Java package; the CLI rejected it before writing. Only the remaining steps ran after choosing a legal service ID; the first three successful commands were not repeated.

This run used an existing verified tool image and dependency cache. It did not regenerate a full preset, integrate a service, start applications or rerun Maven. The 11 service-related tests, 13 CRUD tests and generated-output comparisons have separate scoped evidence; full current goldens and the five-preset structural matrix still require acceptance. Historical human-signature requirements remain historical. The current [authorization policy](../adr/0003-authorized-ai-translation-review.md) accepts actual AI_REVIEW without mandatory human review. It does not turn old logs or generated status into new document approval.

<!-- omni:modules:crm -->
## 8. CRM Extension and Container Tests

This section is for developers and requires no business login. Use crm/full in an independent learning project with no personal changes. Read [current CRM facts](../crm.en.md) and section 3 before editing code. The following are instructions; this documentation remediation did not rerun these business/database steps.

### 8.1 Add Aggregates, Permissions and Events

1. Append a forward-only changeSet under `database/changelog/crm/` and include it in the existing master; never rewrite executed migrations. `CrmTenantEntity` adds tenantId to BaseEntity. owner, version and deleted depend on the concrete aggregate table/entity and are not required on every new table. Design schema/entities using the actual owner mapping, optimistic-lock and deletion strategy while retaining the base audit conventions.
2. Add separate request/response DTOs, Mapper, Service interface/implementation and Controller. Register owner mappings in the actual `CrmDataPermissionHandler`; do not filter nonexistent owner columns.
3. Add API permissions to `scripts/sql/seed/auth.sql` and assign them to roles. Update `crm.sql` if module seeds are needed, plus SHA-256 values and assertions in `database/seed/manifest.yaml`. Use the same full permission in `@PreAuthorize` and `@ServiceDataScope`, add `@OperLog` to mutations and `v-permission` to UI buttons.
4. In the transaction, pass tenantId explicitly to `ReliableMessageRelay.send("crm-domain-out-0", envelope, tenantId, eventId)`. Keep full PII out of payloads. Consumers read top-level envelope `eventId` and atomically deduplicate/check conflicting intent with consumer identity, tenant and business intent; Broker msgKey does not guarantee business execution only once.
5. Check tenant/owner violations, missing identity/scope, stale versions, invalid states, rollback, duplicate messages and PII branches. Invisible records return 404, version/state conflicts 409; record both HTTP and R.code from the actual endpoint.

Current Opportunity pipelines have no management UI. Stage-management APIs/UI and migration of existing references require later design; direct table edits do not complete an extension.

### 8.2 Run the Ordinary Build and Tests First

Run from the learning project root. The backend tool container supplies JDK 25/JAVA_HOME:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-crm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

Install dependencies as in section 6 first. Check BUILD SUCCESS, failures/errors/skipped for each module and frontend exit codes. Without CRM_TEST_MYSQL_URL, four MySQL cases skip; a successful build is not database integration acceptance. Reuse matching source/report digests instead of rebuilding unchanged code.

### 8.3 When MySQL Interceptor Verification Is Needed

`CrmMysqlInterceptorIntegrationTest` DROP/CREATEs `crm_lead`. Target only a newly created disposable empty database, never an existing business database. Outside the repository, prepare private mysql-it.env (MYSQL_DATABASE=crm_it and a strong MYSQL_ROOT_PASSWORD) and crm-it.env (CRM_TEST_MYSQL_URL set to `jdbc:mysql://crm-it-mysql:3306/crm_it?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai`, CRM_TEST_MYSQL_USERNAME=root and the same CRM_TEST_MYSQL_PASSWORD). Keep credentials out of Git, screenshots and shared terminal records.

The PowerShell example uses names owned by this run, from the learning project root. Set the private directory and volume name yourself. On a name conflict, inspect ownership instead of overwriting resources.

```powershell
$private = 'C:\Private\crm-it-yourrun'
$network = 'crm-it-yourrun'
$cache = 'crm-it-maven-yourrun'
$mysql = 'crm-it-mysql-yourrun'
docker network create $network
docker run -d --name $mysql --network $network --network-alias crm-it-mysql --env-file "$private\mysql-it.env" mysql:8.4
docker logs $mysql
```

After logs confirm MySQL is ready, run the test. On failure, inspect private files, networking and the database name; retain the test-database restriction:

```powershell
docker run --rm --network $network --env-file "$private\crm-it.env" --mount "type=bind,source=$PWD,target=/workspace" --mount "type=volume,source=$cache,target=/root/.m2" -w /workspace/omni-backend eclipse-temurin:25-jdk ./mvnw -pl omni-crm -am -Dtest=CrmMysqlInterceptorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Success means this report has 4 cases, 0 failures, 0 errors and 0 skips. The absence of matching tests in other modules does not prove these four passed. This checks MyBatis tenant/owner/pagination/missing-scope paths, not login, real Feign, business conversion concurrency or Broker delivery. Record image identity, source digests, time and the complete report with credentials redacted. These four cases remain skipped in the current remediation report; this tutorial is not a new execution result.

Verify this run created the container, network and cache and that they contain no personal data before cleanup by exact name. The MySQL image uses an anonymous volume at /var/lib/mysql. Here -v removes anonymous volumes only with this disposable test container; it does not remove named volumes. Retain Maven cache by default and do not use global prune:

```powershell
docker rm -f -v $mysql
docker network rm $network
```

On failure, preserve redacted logs/reports before fixing and retrying; do not replay successful business creation. Source: [test](../../omni-backend/omni-crm/src/test/java/com/omni/crm/integration/CrmMysqlInterceptorIntegrationTest.java), [entity](../../omni-backend/omni-crm/src/main/java/com/omni/crm/entity/CrmTenantEntity.java), [data permission](../../omni-backend/omni-crm/src/main/java/com/omni/crm/security/CrmDataPermissionHandler.java), [event envelope](../../omni-backend/omni-crm/src/main/java/com/omni/crm/dto/DomainEventEnvelope.java).

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## 9. SRM Extension and Container Tests

For developers in an independent supply-chain/full learning project; no business login is needed. Read [current SRM facts](../srm.en.md) and this guide's container/service-integration steps first.

1. Append forward-only changeSets under database/changelog/srm/ and connect them to master; never rewrite executed migrations or legacy init-all.sql. A new directly owned aggregate follows SrmOwnedEntity and OWNED_TABLES. A child follows SrmTenantEntity and parent/nested mappings, never owner predicates for nonexistent columns.
2. Add Entity, Mapper, separate request/response DTOs, Service interface/implementation and Controller. Consult current Supplier/Evaluation/Risk/Quotation services; new resources must retain tenant, row authorization, state and version checks.
3. Put module seeds in scripts/sql/seed/srm.sql, API permissions/role grants in scripts/sql/seed/auth.sql, and refresh SHA-256/assertions in database/seed/manifest.yaml. Here owner endpoints are business APIs for internal management users, not the Internal-Token interservice protocol. Use matching full @PreAuthorize/@ServiceDataScope, @OperLog for mutations and v-permission for buttons. Portal profile/evaluation require SUPPLIER, the relevant permissions and an ACTIVE PortalUser association. The USER inviteToken/requestId enrollment flow is separate; a management owner is not a portal account.
4. Pass tenantId explicitly to ReliableMessageRelay.send in the business transaction. Deduplicate consumers using top-level envelope eventId and validate tenant/intent; Broker msgKey does not automatically execute business once. Payloads exclude account/contact information and invitation tokens; quotation money uses decimal strings.
5. Check authorization/tenant violations, missing identity/scope, stale versions, invalid states, duplicate/conflicting events, rollback and PII branches. Judge actual HTTP and R.code, not hidden buttons, HTTP 200 alone or Outbox insertion.

Install dependencies as in section 6. Run from the learning project root; the backend tool container supplies JDK 25/JAVA_HOME:

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-srm -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

Repeat backend/frontend builds only after relevant source changes; matching valid reports can be reused. Check reactor modules, failures/errors/skipped and frontend exit codes; retain image identity, source digests, time and redacted reports.

Unit/configuration/controller reports prove only their own assertions, not real CAPTCHA, Workflow/MQ, database concurrency or tutorial execution. Real operations follow the [SRM Wiki](srm-flow.en.md) accounts, invitations, unique prefixes and evidence limits. Inspect existing successful business keys before creating again. Database integration requires a disposable environment, never the shared business database.

On failure, reload details/version, inspect role, tenant/owner or ACTIVE PortalUser, and retain trace/error codes. Role-Saga retry and approval-start retry are different capabilities; COMPLETED or an error string does not justify blindly starting business again.

Clean up only this run's owned learning outputs after checking for personal content; preserve named volumes/needed caches. Do not use global prune or git clean/reset --hard. Existing-data upgrade/rollback needs separate verification. Source: [scope policy](../../omni-backend/omni-srm/src/main/java/com/omni/srm/security/SrmDataPermissionHandler.java), [state machine](../../omni-backend/omni-srm/src/main/java/com/omni/srm/domain/SrmStateMachine.java), [quotation](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/QuotationServiceImpl.java), [risk](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/RiskServiceImpl.java).
<!-- omni:modules:end -->
