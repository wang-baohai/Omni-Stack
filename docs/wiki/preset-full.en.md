# The full preset: every core and business capability in this repository

The full preset is the complete set of Omni-Stack's current capabilities: authentication and the base platform, workflow, CRM, SRM, procurement, asset, messaging and scheduling all enabled at once. It suits end-to-end acceptance, cross-module demos and teams that need to see every entry at once; it does not suit resource-constrained environments, single-domain verification, or treating "all containers started" as "all business loops closed." The components on this page come from the actual resolution of the current `scaffold/presets/full.yaml`, `scaffold/catalog/modules.yaml` and `preset explain full`; runtime results are labelled verified/unverified separately.

Runtime results here are staged historical records from before the coverage-trimming fix, not current-version results. Early failures remain recorded; later success cannot retroactively sign off an earlier stage. An old G7 pass cannot prove current complete coverage. Current release requires G6/G7 with every applicable requirement preserved, plus necessary G11 checks.

## Who it is for

- Teams that need to verify login, tenants, RBAC, workflow, CRM, supply chain and asset entries in one run.
- Maintainers who initialize the shared database with full and then verify other presets as in-place subsets.
- Acceptance staff who check cross-module menus, permissions, data scopes and messaging/scheduling cooperation.

It is not for: only getting login and base management working first (use core), sales only (CRM), supplier/procurement/asset only (supply-chain), or approvals only (workflow). full has the largest resource footprint, start-up time and failure surface.

## From 8 explicit modules to 14 closure modules

Explicit modules in `scaffold/presets/full.yaml`:

```text
crm, asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

The closure resolved by `preset explain full`:

```text
platform, auth, base, workflow, crm, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

The extra `srm`, `procurement` and `workflow` come from `asset`'s dependency chain (`asset → procurement, srm → workflow → base`); `auth` and `platform` come from `base`'s dependencies. In other words, writing `asset` pulls the whole supply chain and the approval runtime into full. `crm` is an explicitly added independent business domain.

## Actual services and ports

The 14 modules map to these 16 Compose services:

| Service | Source module | Purpose |
|---|---|---|
| `mysql` | mysql | Platform and business data |
| `redis` | redis | Cache and token-related data |
| `nacos` | nacos | Service discovery and configuration center |
| `rocketmq-namesrv` | rocketmq | Message routing |
| `rocketmq-broker` | rocketmq | Message storage and delivery |
| `xxl-job-admin` | xxl-job | Scheduling center |
| `omni-db-migrator` | platform | One-shot schema migration and seed initialization |
| `omni-auth` | auth | Login, tenants, RBAC, organization, dictionary, audit |
| `omni-base` | base | Base management, logs, workspace and job APIs |
| `omni-workflow` | workflow | BPMN models, todos, approvals and process instances |
| `omni-crm` | crm | Leads, customers, contacts, opportunities, follow-ups and sales pipeline |
| `omni-srm` | srm | Suppliers, admission, evaluation, risk and portal |
| `omni-procurement` | procurement | Materials, requisitions, approval routes, RFQ, orders and receipts |
| `omni-asset` | asset | Asset ledger, card creation, allocation/return, transfer and disposal |
| `omni-gateway` | gateway | Unified entry and routing |
| `omni-frontend` | platform | Vue frontend |

The catalog declares 35 port records and 34 unique ports (`8080` is declared by both nacos and xxl-job):

| Module | Declared ports |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| crm | 8104, 19904, 9904 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

The frontend entry is 3000; business services are CRM 8104, SRM 8105, procurement 8106, asset 8107 and workflow 8103. Both `xxl-job` and nacos declare 8080; this repository's sample environment maps the scheduling center through `OMNI_XXL_JOB_HOST_PORT=18080` and the NameServer through `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876` (see the explicit variables in `.env.example`). These are not random offsets.

## Benefits and costs

The benefit is getting every business domain at once: one set of tenants, RBAC, data scopes, audit, workflow, messaging and scheduling covering both the CRM sales chain and the supply-chain/asset chain; it suits acceptance of cross-module menus, permissions and data isolation, and serves as the shared-database initialization source for other presets.

The cost is the largest:

- Summing catalog `resourceHints`, the minimum memory hint is 7296 MB and the recommended 11776 MB. Quick Start's initial suggestion for full is 12–16 GiB; that is a preparation budget, not a measured ceiling — build peaks, MySQL/RocketMQ caches and JVM heaps stack on top.
- 16 services, 34 unique ports, 5 business applications, 1 message broker (NameServer + Broker) and 1 scheduling center; the largest surface for start-up, health checks, logs and fault isolation.
- The most business prerequisites: CRM sales data, SRM supplier admission and portal, procurement approval routes/orders/receipts, asset card creation and transfer/disposal rules, workflow model publishing and candidates, plus message relay and schedule execution.
- "All containers healthy" only proves services respond; it does not prove cross-module business loops are closed.

## Prerequisites and configuration

- **Docker and resources**: budget 12–16 GiB memory and enough disk; confirm ports 3000, 8100–8107, 3306, 6379, 8080, 8848, 9848 and 9876 are free (host mappings for scheduling and NameServer follow the explicit `.env` variables).
- **Private `.env`**: database, Redis, Nacos authentication, gateway assertion, `XXL_JOB_ACCESS_TOKEN`, `XXL_JOB_ADMIN_PASSWORD`, etc. Never commit real values.
- **Nacos configuration**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-crm.yml`, `omni-srm.yml`, `omni-procurement.yml`, `omni-asset.yml`, `omni-gateway.yml`; when authentication is enabled all clients use the same credentials.
- **Migration and seeds**: `omni-db-migrator` runs once. When the shared database is already initialized, do not re-import, replay migrations or clear it.
- **Identities**: administrator, `CRM_ADMIN`/`SALES_MANAGER`/`SALES_REP`/`CRM_VIEWER`, `SRM_ADMIN`/`PROCUREMENT_MANAGER`/`PROCUREMENT_STAFF`, `SUPPLIER`, plus asset and workflow roles. The supplier portal additionally requires a portal association and an invitation.
- **Approvals and messaging**: the approval runtime lives in `omni-workflow`; the Outbox relay depends on `xxl-job`, and business events depend on RocketMQ. While scheduling is not ready, a written message does not mean delivered.
- **Business data**: CRM sales chain, supplier admission, procurement requisition/approval/order/receipt, asset card creation and transfer/disposal. Containers starting does not mean these prerequisites are done.

## Path 1: start full from source

### First install on a clean database

```bash
docker compose --parallel 1 --profile full up -d --build
docker compose ps
```

Expected: the migrator exits after running; mysql/redis/nacos/rocketmq/xxl-job and all 9 application services start; applications are healthy and frontend 3000 is reachable. This path runs migrations and seeds and is only for an empty database or an environment explicitly allowed to initialize. **This page did not run this path**: the verification environment uses a shared database already initialized by full, so first install on an empty database stays marked unverified.

### In-place full replacement on an existing shared environment

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-crm omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: builds images for the 9 applications; does not start the migrator or rebuild infrastructure.
- `up -d --no-build --no-deps --force-recreate`: replaces the 9 applications in place; no dependency start-up, no migration.
- `ps`: checks actual images and health; healthy only means the service responds.
- `curl`: confirms frontend 200; gateway business routes without a token should be 401, not 503.

full has no "out-of-closure applications," but still check against the actually registered service set; do not stop infrastructure or clear volumes for the sake of "cleanliness."

## Path 2: generate an independent full project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create full --output generated/learning-full
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate full --output generated/learning-full
```

The generated project has its own `scaffold.lock`, Compose, docs and AI entry points. Use its own build commands after entering it; do not start the generated Compose project from the original repository directory, and do not run the generated migrator against the shared database.

Early historical stage: the source path completed 9 application image builds and in-place replacement, health/frontend 200/real CAPTCHA login/full-domain menu checks and stop/restore; the generated path completed generation at the fixed position (G6 14 modules/16 services), G7 self-build of the generated project, delivery-role self-sufficiency (mounting only generated-artifact and cache subpaths, no source checkout, no docker socket), in-place replacement of the 9 generated images with real login/menu/workspace verification, and restoration of the repository images afterwards; the delivery gate `verify-preset-delivery full` reports `automated-delivery-pass` (modules=14) but `release_ready=false`: translations of the generated docs await designated review. First install on a clean empty database remains unmeasured.

## First use

1. Open `http://localhost:3000`, choose the development tenant, and log in with `admin` and the **CAPTCHA shown for this run**. Do not reuse an old CAPTCHA or use an authentication backdoor.
2. Keep the Wiki home in its original tab and open My Workspace separately. Its application directory and search cover all accessible menus. Open CRM, SRM, procurement, assets, workflow and system/base modules from that directory in new tabs. Business pages show and search only their own module menus and have no global Applications button.
3. Verify domain by domain instead of flattening all pages at once:
   - CRM: lead → follow-up → conversion → opportunity stage → won/lost.
   - Supply chain: supplier admission/materials → requisition approval → order → receipt → asset card creation.
   - Approval: model publish → start → todo handling → instance history.
   - Portal: obtain a supplier portal identity through the **formal invitation → self-registration → enrollment Saga** (measured this round: enrollment COMPLETED within 25 seconds, portal association ACTIVE, re-login, `/supplier-portal` with three tabs usable, internal admin endpoints 403). The passwords of `supplier1`/`supplier2` exist only as hashes in the seeds and cannot be recovered from the repository; do not guess or bypass them — for a second portal identity, repeat the invitation/registration/enrollment chain.
4. When a menu is visible but its list is empty, check tenant/data scope and business prerequisites first; when a menu is missing, check role permissions and `/api/auth/menus`. Do not edit database menus or roles directly.
5. Messaging and scheduling: verify the Outbox relay after `xxl-job` is ready; do not substitute "a row exists in the message table" for "delivered."

## Success criteria

- The 9 applications and infrastructure in the full closure are healthy, and the migrator exited as expected.
- Frontend 3000 returns 200; real login succeeds and each business menu appears per permission.
- CRM, SRM, procurement, asset and workflow each have at least one real entry that opens; authorized identities can read seed data.
- Cross-module chains are judged by real API results; health checks do not equal business closure.
- No new project, network, volume or port offset; shared data was neither cleared nor re-migrated.

**Menu boundary**: the menu API returns only "tenant permission tree ∩ current identity permission codes," independent of which services run. Seeing all business menus on a shared database initialized by full is expected; when switching to another preset, business permission codes remain granted to administrator and other roles, so menus keep business entries (measured in the core window: 36 business codes among 57 nodes, matching the shared-database grant query) — this is the shared-data state, not proof that the subset's closure contains all business; core's tailored seeds contain no business permission rows. workflow, CRM and supply-chain remove permissions for unselected business modules and retain selected business permissions. A first install on an empty database should not retain menus for unselected modules. Runtime verification of "no other menus" for a subset requires an independent empty database/tenant; do not delete permissions or change roles on the shared database to simulate it.

## Stop and restore

- Stop only applications started this round; keep mysql/redis/nacos/rocketmq/xxl-job and data volumes.
- Restore: `docker start` the registered original service set, or run `up -d --no-build --no-deps` for explicit services; never `down -v`, clear volumes or run a global prune.
- If replacement fails, restore the old image recorded before replacement at the same service position; do not start side services.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Port conflict | 3000/8100–8107/3306/6379/8080/8848/9848/9876 | Change the closure or randomly offset ports |
| Nacos authentication fails | Whether `.env` and Nacos credentials match | Disable authentication or write sample secrets |
| Gateway 503 | Assertion keys, downstream registration, route configuration | Only change the frontend address |
| A business menu is missing | Role permissions, `/api/auth/menus`, the service's health | Edit database menus/roles directly |
| Page opens but list is empty | Tenant, data scope, business prerequisites | Treat empty data as start-up failure or fabricate rows |
| Approval todos empty | Model published, candidates, roles, task creation | Edit task/candidate tables directly |
| No asset card after receipt | Quality pass, asset-managed flag, positive integer quantity | Insert asset cards directly |
| Messages stay PENDING | `xxl-job` relay, RocketMQ, max_retry | Present PENDING as delivered |
| Migration runs twice | Whether the shared database is initialized | Clear the database or re-import seeds |
| Maven download `Remote host terminated the handshake` | Corporate-network TLS flakiness; retry the same command later | Change dependency versions or repository coordinates |
| `npm ci` ECONNRESET | Network flakiness; retry and keep the offline cache | Delete lockfiles or skip lint |
| All container downloads fail (DNS/handshake) | Set `HTTP_PROXY/HTTPS_PROXY` to the host proxy (inside containers use `host.docker.internal:<mixed-port>`), and pass same-named `--build-arg` for image builds | Blame the product or the preset |
| Verifier container lacks `docker`/BuildKit | Install the docker CLI and buildx plugin and set `DOCKER_BUILDKIT=1` | Copy the generated project to a host temp directory to build |
| Maven local repository re-downloads after every container rebuild | Point `-Dmaven.repo.local` at the fixed validation-volume cache | Bake private repository configuration into images |

## Historical stage records and verification boundaries

- **Verified**: `preset explain full` resolves 8 explicit and 14 closure modules; 16 services and 35 port declarations/34 unique ports from the catalog; resource hints summed per module at 7296/11776 MB; source path 9-application image build/in-place replacement/health/frontend 200/real login/full-domain menus/stop-restore; generated path G6/G7, delivery-role self-sufficiency, in-place replacement of the 9 generated images with real entry verification, and restoration of repository images; delivery gate `automated-delivery-pass`; Outbox relay and schedule execution evidenced for real by the portal enrollment Saga (role assignment COMPLETED within 25 seconds).
- **Unverified**: first install on a clean empty database; deep cross-module business flows (CRM conversion/won, supply-chain order-receipt-card, multi-level countersign approval end-to-end); release readiness (`release_ready=false`, translations pending designated review).

Continue with [the preset overview](presets-overview.en.md), [the feature tour](feature-tour.en.md) or [architecture facts](../architecture.en.md).
