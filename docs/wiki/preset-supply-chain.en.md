# The supply-chain preset: suppliers, procurement, assets and approvals

The supply-chain preset adds SRM, procurement, asset management, workflow, messaging and scheduling on top of authentication and the foundation platform. It covers the collaboration chain "supplier onboarding → purchase requisition and approval → RFQ/order/goods receipt → asset card creation → transfer/disposal". It suits teams that need to validate a cross-role business flow and is one of the presets with many components and substantial business prerequisites in this repository. Modules, services, ports and resources on this page come from the current `scaffold/presets/supply-chain.yaml`, `scaffold/catalog/modules.yaml` and `preset explain supply-chain`. Results are marked verified or unverified.

Runtime results here are staged historical records from before the coverage-trimming fix, not current-version results. Early failures remain recorded; later success cannot retroactively sign off an earlier stage. An old G7 pass cannot prove current complete coverage. Current release requires G6/G7 with every applicable requirement preserved, plus necessary G11 checks.

## Who it is for

- Teams that need to connect suppliers, procurement, assets and approvals into one real chain rather than inspecting a single module's list page.
- Platforms that must validate three identity domains: internal employees, procurement/approvers, and the supplier portal, with their data scopes.
- Teams that need MQ and scheduling to participate in the business (Outbox relay, tenant provisioning, user jobs).

It is not for: teams that only need CRM (use the CRM preset), only need BPMN approvals (workflow preset), or have limited resources and only want to validate login and RBAC. supply-chain is not "a few more pages"; it adds several services, several identity domains and multi-stage prerequisite data.

## From 7 explicit modules to 13 resolved modules

Explicit modules in `scaffold/presets/supply-chain.yaml`:

```text
asset, gateway, mysql, redis, nacos, rocketmq, xxl-job
```

The resolved modules reported by `preset explain supply-chain`:

```text
platform, auth, base, workflow, srm, procurement, asset, nacos, xxl-job, gateway, mysql, redis, rocketmq
```

The dependency chain explains why `asset` alone brings in the whole supply chain:

```text
asset → procurement, srm
procurement → srm
srm → workflow
workflow → base
gateway → base
base → auth → platform
```

`platform` provides migration, the frontend and shared capabilities; `auth` provides login/tenants/RBAC; `base` provides foundation management and job APIs; `workflow` is the approval runtime; `srm → procurement → asset` is the business mainline. `rocketmq` and `xxl-job` are explicit choices, not ignorable options: the Outbox relay, tenant provisioning and user jobs depend on scheduling, and business events depend on messaging.

## Actual services and ports

The 13 modules map to 15 Compose services:

| Service | Source module | Purpose |
|---|---|---|
| `mysql` | mysql | Platform and business data |
| `redis` | redis | Cache and token-related data |
| `nacos` | nacos | Service discovery and configuration center |
| `rocketmq-namesrv` | rocketmq | Message routing |
| `rocketmq-broker` | rocketmq | Message storage and delivery |
| `xxl-job-admin` | xxl-job | Scheduling center |
| `omni-db-migrator` | platform | One-shot schema migration and seed initialization |
| `omni-auth` | auth | Login, tenants, RBAC, organization, dictionaries, audit |
| `omni-base` | base | Foundation management, logs, workbench and job APIs |
| `omni-workflow` | workflow | BPMN models, tasks, approvals and process instances |
| `omni-srm` | srm | Suppliers, onboarding, evaluation, risk and portal |
| `omni-procurement` | procurement | Materials, requisitions, approval routes, RFQ, orders and receipts |
| `omni-asset` | asset | Asset ledger, card creation, allocation/return, transfer and disposal |
| `omni-gateway` | gateway | Unified entry point and routing |
| `omni-frontend` | platform | Vue frontend |

The catalog declares 32 port records and 31 unique ports (`8080` is declared by both nacos and xxl-job):

| Module | Declared ports |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| srm | 8105, 19905, 9905 |
| procurement | 8106, 19906, 9906 |
| asset | 8107, 19907, 9907 |
| nacos | 8080, 8848, 9848 |
| xxl-job | 8080 |
| gateway | 8102, 19902 |
| mysql | 3306 |
| redis | 6379 |
| rocketmq | 9876, 10909, 10911, 10912 |

The frontend entry is 3000; the business services are workflow 8103, SRM 8105, procurement 8106 and assets 8107. `xxl-job` and nacos both declare 8080. This repository's example environment maps the scheduling center via `OMNI_XXL_JOB_HOST_PORT=18080` and the NameServer via `OMNI_ROCKETMQ_NAMESRV_HOST_PORT=19876`; these are explicit mappings registered in `.env.example`, not random offsets. On a port conflict, free the port or adjust the explicit variables; do not change the preset closure.

## Benefits and costs

The benefit is a complete collaboration chain in one environment: supplier master data and onboarding, requisitions and approvals, RFQ/orders/receipts, and asset card creation plus later transfer/disposal all share tenants, permissions, data scopes and audit. MQ and scheduling are also present, so Outbox relay and user jobs can be validated directly.

The cost is substantial resources and prerequisite complexity:

- Summing the catalog `resourceHints` gives a minimum memory hint of 6784 MB and a recommended hint of 11008 MB. These are planning hints, not measured limits; build peaks, MySQL/RocketMQ caches and JVM heaps add more. Use `docker stats` and build logs for actuals.
- One more workflow runtime, a message middleware (NameServer + Broker) and a scheduling center significantly increase ports, credentials, health checks and log surfaces.
- The business does not "run just because you logged in": suppliers need onboarding/invitation and a portal association, requisitions need approval routes and workflow models, receipts need a permitted order and quality result, and asset card creation requires quantity and asset-management flags to satisfy the rules.
- Identities split into internal users and supplier portal users. A supplier account that can log in still needs the `SUPPLIER` role and a valid supplier portal association to access portal profiles and evaluations (`srm:portal:profile` / `srm:portal:evaluation`); an ordinary `USER` may only enroll.

## Prerequisites and configuration

- **Docker and resources**: reserve at least the memory/disk hints on this page; confirm availability of 3000, 8100, 8101, 8102, 8103, 8105, 8106, 8107, 3306, 6379, 8080, 8848, 9848 and 9876. Check the scheduling and NameServer host mappings against the explicit `.env` variables.
- **Private `.env`**: database, Redis, Nacos authentication, gateway assertion, `XXL_JOB_ACCESS_TOKEN`, `XXL_JOB_ADMIN_PASSWORD`, etc. Do not commit real values or treat examples as secrets.
- **Nacos configuration**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-srm.yml`, `omni-procurement.yml`, `omni-asset.yml`, `omni-gateway.yml`. With Nacos authentication enabled, all clients use the same credentials.
- **Migration and seeds**: executed once by `omni-db-migrator`. If the shared database is already initialized, do not re-import, replay migrations or clear it.
- **Internal identities**: the administrator and seed roles such as `SRM_ADMIN`, `PROCUREMENT_MANAGER` and `PROCUREMENT_STAFF`; approvals also depend on workflow candidates and data scopes.
- **Supplier portal identities**: `auth.sql`/`srm.sql` provide `supplier1`, `supplier2` and portal associations to suppliers 1/2 for tenant 1, with the `SUPPLIER` role. Portal enrollment requires an `inviteToken` and client `requestId`; without a valid invitation or association, do not "create" a portal user by editing the database.
- **Approval models**: the approval runtime is in `omni-workflow`; procurement/asset services do not embed Flowable. Before starting an approval, a published model and resolvable candidates/roles are required.
- **Messaging and scheduling**: RocketMQ and `xxl-job` are in the closure. Outbox messages need the relay job; when the scheduler is not ready, do not treat "message written" as "message delivered".
- **Business data**: supplier onboarding/evaluation, material catalog, requisitions and approval routes, orders, receipts, asset quantity and quality results. Container startup does not mean these are ready.

### Startup and business preparation are different

Passing health checks only means the services respond. It does not prove that:

- the supplier passed onboarding and the portal user is bound;
- the workflow model is published and approvers receive tasks;
- a requisition is approved and its order can be received;
- the receipt line satisfies "quality passed + asset-managed + positive integer quantity", and therefore creates an asset card;
- Outbox messages have been delivered by the relay.

Prepare and verify these in business order; do not bypass them by editing database state.

## Path 1: start supply-chain from source

### First installation with a clean database

In a separate environment where shared data is not initialized:

```bash
docker compose --parallel 1 --profile supply-chain up -d --build
docker compose ps
```

Expected: the migrator runs and exits; mysql/redis/nacos/rocketmq/xxl-job plus auth/base/workflow/srm/procurement/asset/gateway/frontend start; application services are healthy and frontend 3000 is reachable. This path runs migrations and seeds, so it is only for an empty database or an environment explicitly allowed to initialize.

### In-place subset in an existing shared environment

If the database was initialized by full, do not run the migrator again. Build the 8 application images, stop CRM (outside the closure), and replace in place:

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-srm omni-procurement omni-asset omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: builds only the 8 applications in the closure; it does not start the migrator or rebuild mysql/redis/nacos/rocketmq/xxl-job.
- `docker stop`: stops CRM outside the closure so two business runtime sets do not coexist; infrastructure and data volumes remain.
- `up -d --no-build --no-deps --force-recreate`: replaces the 8 applications in place with the new images, without starting dependencies or running migrations.
- `ps`: checks the actual images and health. Healthy only means the process and health check passed, not that the business chain is ready.
- `curl`: confirms frontend 200. A tokenless business request returning 401 proves authentication rejection only; service and route availability also need successful authorized requests and registration/health evidence.

Fill in the stop list from the environment actually registered; keep mysql/redis/nacos/rocketmq/xxl-job and data; do not run `down -v` or clear volumes.

## Path 2: generate a standalone supply-chain project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

- `preset create`: trims supply-chain into a standalone project with its own `scaffold.lock`, Compose file, documentation, Maven and frontend source.
- `preset validate`: validates the generated module closure, Compose model, documentation, AI entry points and trimming boundaries; it does not start applications.
- The generated project has its own build commands. Use them inside it; do not start its Compose project from the original repository directory, and do not run its migrator against the shared database.

Early historical stage: G6 structure PASS (13 modules/15 services); G7 generated backend BUILD SUCCESS and frontend build/lint PASS (`VERIFY_PASS modules=13 services=15`). Delivery-role isolation (no source/socket mount) and G11 in-place replacement with the generated images have not been executed, so do not write "the generated image runs in the same environment".

## First use (in business order)

1. Open `http://localhost:3000`, select the development tenant, and log in to the internal side as `admin` with **the captcha shown this time**. Do not reuse an old captcha or use an authentication bypass.
2. Keep the Wiki home in its original tab. Open My Workspace separately, then open SRM, procurement, assets and workflow from its application directory in separate tabs. Each business page shows only accessible menus for its own module; approval tasks are gathered in the workspace.
3. Prepare the business in order: supplier onboarding/evaluation → materials and approval routes → submit and approve a requisition → RFQ/order → confirm receipt → asset card creation → (optional) transfer/disposal approval. Use the IDs and states returned by the real APIs for each step.
4. Supplier portal: log in as `supplier1` or `supplier2` (tenant 1, `SUPPLIER` role, associated with suppliers 1/2). Portal enrollment still requires a valid `inviteToken` and client `requestId`; do not skip it by writing `srm_supplier_portal_user` or changing supplier ownership.
5. Approvals: if the task list is empty, check whether the model is published, whether the current user is in the candidate scope, and whether the task exists; do not edit task or candidate tables directly.
6. Receipt and card creation: only receipt lines with `qualityStatus=PASS`, `assetManaged=true` and a positive integer `assetQuantity` create asset cards; "no card" is a business rule, not a service failure.
7. If menus are missing, check role permissions and `/api/auth/menus`; if pages open but lists are empty, check tenant/data scope and prerequisite data before changing the database.

## Success criteria

- The supply-chain closure application services are healthy and the migrator exits as expected; RocketMQ and xxl-job are healthy.
- Frontend 3000 returns 200; internal login succeeds and SRM/procurement/asset/workflow menus appear according to permissions.
- The supplier portal identity can log in and sees only its own portal data; internal and portal identities are verified separately.
- Complete one real chain in order: supplier/material preparation → requisition approval → order → receipt → asset card; each step has a real API result.
- No new project, network, volume or port offset; shared data is neither cleared nor migrated again.

**Menu boundary**: a shared full database may retain CRM permission codes even when CRM is stopped; this does not mean the supply-chain closure includes CRM. To verify a supply-chain-only menu, use an independent empty database/tenant initialized with the corresponding seeds.

## Stop and recovery

- Stop only the applications started this round; keep mysql/redis/nacos/rocketmq/xxl-job and data volumes.
- Recovery: `docker start` the registered original service set, or run `up -d --no-build --no-deps` for explicit services. Do not run `down -v`, clear volumes or a global prune.
- If a replacement fails, restore the old image recorded before the replacement in the same service position; do not start a side service.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Port conflict | 3000/8100/8101/8102/8103/8105/8106/8107/3306/6379/8080/8848/9848/9876 | Change the closure or use random offsets |
| Nacos authentication failure | Whether `.env` and Nacos credentials match | Disable authentication or write example secrets |
| Scheduler unavailable | `XXL_JOB_*` credentials, 18080 mapping, admin health | Treat an Outbox write as delivered |
| Supplier portal has no permission after login | `SUPPLIER` role, portal association, invitation state | Write the portal association table or change ownership |
| Approval task list is empty | Model published, candidates, roles, task creation | Edit task/candidate tables |
| No asset card after receipt | Quality passed, asset-managed flag, positive integer quantity, permission | Insert asset cards directly |
| Menu missing | Role permissions, `/api/auth/menus`, auth/base health | Edit database menus |
| Migration runs twice | Whether the shared database is initialized | Clear and replay, or re-import seeds |

## Historical stage records and verification boundaries

- **Verified (source path)**: 8 application images built and replaced in place in the same `omni-stack` project; auth/base/workflow/srm/procurement/asset/gateway healthy and frontend 200; 4 business routes returned 401 without a token; `admin` real captcha login returned 200, with 57 menu nodes including SRM 11, procurement 8, asset 5 and workflow 5 permission codes; read-only query found 2 suppliers and 2 portal users, with materials/requisitions/orders/assets at 0 (empty business data, not a failure); 15 containers were restored afterwards.
- **Verified (generated path)**: G6 structure PASS with 13 modules/15 services; G7 generated backend BUILD SUCCESS and frontend build/lint PASS.
- **Unverified**: real supplier portal login (`supplier1` with the known convention password returned "password error"; no further guessing or bypass), the full supplier onboarding → approval → order → receipt → card chain, delivery-role isolation and generated image replacement, clean-database first installation, and real approval/message-delivery/scheduling results.

> The supplier portal seed association (`SUPPLIER` role and suppliers 1/2) is confirmed to exist, but "the association exists" does not mean "a real portal login has been completed". This page does not treat a controlled test identity as a real login.

Continue with the [preset overview](presets-overview.en.md), the [SRM fact document](../srm.en.md) or the [procurement design](../design/procurement-design.md).
