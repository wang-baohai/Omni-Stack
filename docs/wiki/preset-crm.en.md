# The CRM preset: core platform plus a sales pipeline

The CRM preset adds customer relationship management on top of the core authentication and foundation platform. It suits teams that need leads, customers, contacts, opportunities and follow-up records but do not yet need SRM, procurement, assets or approval flows. The modules, services and ports on this page come from the current `scaffold/presets/crm.yaml`, `scaffold/catalog/modules.yaml` and the actual `preset explain crm` output. Results are marked as verified or unverified; structural resolution does not mean the stack started successfully.

Runtime results here are staged historical records from before the coverage-trimming fix, not current-version results. Early failures remain recorded; later success cannot retroactively sign off an earlier stage. An old G7 pass cannot prove current complete coverage. Current release requires G6/G7 with every applicable requirement preserved, plus necessary G11 checks.

## Who it is for

- Teams that want to exercise the sales chain "lead → follow-up → customer/contact → opportunity → won/lost" first.
- Projects that need tenant isolation, functional permissions, data scopes and PII masking without also bringing in supply chain or workflow.
- Developers who plan to use CRM as a business example and then replace the lead, customer or opportunity implementation with their own domain model.

It is not for scenarios that need suppliers, procurement, asset collaboration, BPMN approvals or a scheduling center for timed jobs. The CRM preset does not include SRM, procurement, assets, workflow, RocketMQ or `xxl-job`. The CRM module lists RocketMQ and xxl-job as optional dependencies in the catalog, but this preset does not select them. Do not design acceptance criteria around "asynchronous message relay or scheduling is available".

## From 5 explicit modules to 8 resolved modules

Explicit modules in `scaffold/presets/crm.yaml`:

```text
crm, gateway, mysql, redis, nacos
```

The resolved modules reported by `preset explain crm`:

```text
platform, auth, base, crm, nacos, gateway, mysql, redis
```

`crm` depends on `base`, `base` depends on `auth`, `auth` and `nacos` depend on `platform`, and `gateway` depends on `base`. That is why `auth` and `base` appear even though they were not listed: login, permissions, organization, dictionaries, logs and foundation management remain in the resolved set. CRM is added on top of them and does not replace them.

## Actual services and ports

The 8 modules map to 9 Compose services:

| Service | Source module | Purpose |
|---|---|---|
| `mysql` | mysql | Platform and CRM business data |
| `redis` | redis | Cache and token-related data |
| `nacos` | nacos | Service discovery and configuration center |
| `omni-db-migrator` | platform | One-shot schema migration and seed initialization |
| `omni-auth` | auth | Login, tenants, RBAC, organization, dictionaries, audit |
| `omni-base` | base | Foundation management, logs, workbench and job APIs |
| `omni-crm` | crm | Leads, customers, contacts, opportunities, follow-ups and sales pipeline |
| `omni-gateway` | gateway | Unified entry point and routing |
| `omni-frontend` | platform | Vue frontend |

The catalog declares 18 ports:

| Module | Declared ports |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| crm | 8104, 19904, 9904 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

The frontend entry point uses port 3000 from the platform declaration, and the CRM backend is exposed on 8104. If a port is occupied, free it or adjust the local mapping according to the deployment documentation. Do not change the preset closure to "avoid" the conflict, and do not use random port offsets.

## Benefits and costs

The benefit is a clear boundary: authentication, tenants, RBAC and foundation management come from the platform, while CRM only adds its own service, permission tree, roles and tables. The sales pipeline, lead conversion and customer ownership rules stay inside `omni-crm`, so they can be replaced or trimmed independently.

The cost is a visibly larger component and operations surface than core: one more JVM service, a CRM migration and seed set, four CRM roles and a group of `crm:*` permission codes. Data scopes cover self, department and tenant, and PII fields are subject to masking rules. Summing the catalog `resourceHints` over the resolved modules gives a minimum memory hint of 3840 MB and a recommended hint of 6400 MB. These are planning hints, not measured limits; use `docker stats` on your machine for actual usage.

## Prerequisites and configuration

- **Docker and ports**: 3000, 8100, 8101, 8102, 8104, 3306, 6379, 8080, 8848 and 9848 must be free.
- **Private `.env`**: secrets for the database, Redis, Nacos authentication and the gateway assertion. Do not commit real values, and do not treat example values as usable secrets.
- **Nacos configuration**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-crm.yml` and `omni-gateway.yml`. When Nacos authentication is enabled, all clients must use the same `NACOS_USERNAME` and `NACOS_PASSWORD`.
- **Migration and seeds**: executed by `omni-db-migrator`. If the shared database is already initialized, do not re-import, replay migrations or clear it.
- **Identity and roles**: `auth.sql` provides tenants, the administrator and role/permission seeds. CRM roles are `CRM_ADMIN`, `SALES_MANAGER`, `SALES_REP` and `CRM_VIEWER`. Whether a seed account can see CRM menus depends on whether its role is granted `crm` and `crm:*` permissions.

### Authentication seeds are not business sample data

- **Authentication/platform seeds** (`scripts/sql/seed/auth.sql`, `base.sql`, `nacos.sql`) provide the foundation data for logging in and authorizing: tenants, users, roles, permissions, organization and dictionaries.
- **CRM business seeds** (`scripts/sql/seed/crm.sql`) provide the default sales pipeline and stages, CRM tenant configuration, and the demo leads, customers, contacts, opportunities, follow-up records and conversion records for tenant 1.
- Business sample data keeps the first visit from being empty; it is not a prerequisite for CRM to start. When there are no leads or customers, first decide whether "business data is empty" or "services/permissions are broken". Do not change database state directly just for a demo.
- Other tenants receive CRM roles, permissions and default configuration through the tenant-creation event; demo business records exist only in tenant 1 covered by the formal seed. Not seeing that data across tenants is isolation working, not a missing menu.

## Path 1: start CRM from source

### First installation with a clean database

In a separate environment where shared data has not been initialized:

```bash
docker compose --parallel 1 --profile crm up -d --build
docker compose ps
```

Expected: `omni-db-migrator` runs and exits, mysql/redis/nacos/auth/base/crm/gateway/frontend start, the application services are healthy in `docker compose ps`, and frontend port 3000 is reachable. This path runs migrations and seeds, so it is only for an empty database or an environment explicitly allowed to initialize.

### In-place subset in an existing shared environment

If the database was already initialized by full, do not run the migrator again. Build this round's source images, stop the business applications outside the CRM closure, and replace only the CRM applications:

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-crm omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

- `docker compose --parallel 1 ... build`: builds images only for the 5 applications in the CRM closure; it does not start the migrator or rebuild mysql/redis/nacos.
- `docker stop`: stops business applications outside the CRM closure so multiple business runtime sets do not coexist in the same environment; infrastructure and data volumes are kept.
- `up -d --no-build --no-deps --force-recreate`: replaces the 5 applications in place with the newly built images, without starting dependencies or running migrations.
- `ps`: checks the actual container state and images. Healthy only means the process and health check passed, not that the business flow is complete.
- `curl`: confirms that the frontend returns 200. A 401 from `/api/crm/lead/list` without a token proves authentication rejection only. Route and downstream availability also need successful authorized requests and registration/health evidence; a 401 alone cannot prove them.

Fill in the stop list from the services actually registered in your environment; do not copy another preset. Keep mysql/redis/nacos and the data. Do not run `down -v` or clear volumes.

## Path 2: generate a standalone CRM project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create crm --output generated/learning-crm
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate crm --output generated/learning-crm
```

- `preset create`: trims the CRM preset into a standalone project containing its own `scaffold.lock`, Compose file, documentation, Maven build and frontend source.
- `preset validate`: validates the generated module closure, Compose model, documentation, AI entry points and trimming boundaries; it does not start applications.
- The generated project has its own build commands. Use them inside the generated project. Do not start its Compose project from the original repository directory, and do not run its migrator against the shared database.

Early historical stage: G6 structure passed (8 modules/9 services at the fixed artifact location). In G7, the generated backend `./mvnw clean install` reached BUILD SUCCESS (including CRM and gateway tests), but the frontend `npm run build` has not passed: the first run lacked `@rolldown/binding-linux-x64-gnu`, and after clearing the verifier's npm cache the rerun stopped at a TLS handshake termination during Maven downloads. Delivery-role isolation and in-place replacement with the generated image have not been executed. "The generated backend builds" does not mean "the generated project has started in the same environment"; this page keeps the two separate.

## First use

1. Open `http://localhost:3000`, select the development tenant, and enter a seed account with **the captcha shown this time**. The local demo administrator is `admin` (see the [first-start tutorial](../guides/quick-start.en.md) for the password). Do not reuse an old captcha or use an authentication bypass.
2. Keep the Wiki home page in its original tab. Open My Workspace separately, then open CRM from the application directory in a new tab. The CRM page shows only its own overview, leads, customers, contacts, opportunities and follow-ups; permissions still come from `/api/auth/menus`.
3. Suggested order: create a lead → record a follow-up → convert the lead (creating a customer/contact/opportunity) → advance the opportunity stage → mark won or lost → maintain customers and contacts. The demo data already contains some conversion results and can be viewed directly; for new records, use the IDs returned by this operation.
4. If the menu is visible but a list is empty, confirm the current tenant and data scope: `SALES_REP` sees its own data, `SALES_MANAGER` sees the department and below, and `CRM_ADMIN`/`CRM_VIEWER` see tenant-level data. Not seeing other people's data may be the data scope working, not an API failure.
5. If the menu is missing, check whether the current role has been granted CRM permissions. `SUPER_ADMIN`, `CRM_ADMIN` and `SALES_MANAGER` have full CRM permissions, `SALES_REP` has daily entry and conversion permissions, and `CRM_VIEWER` only has read-only list permissions. Do not edit the database role table to "fix the menu".

## Success criteria

- The CRM closure application services are healthy and the migrator exits as expected.
- Frontend port 3000 returns 200 and static assets load.
- Real captcha login succeeds and `/api/auth/menus` returns the CRM menus visible to the current identity.
- The CRM overview, lead, customer, contact, opportunity and follow-up pages open; an identity with permission can read the seed demo data.
- Write operations such as create and convert are judged by real API results; passing health checks is not the same as a complete business flow.
- No new project, network, volume or port offset is introduced, and shared data is neither cleared nor migrated again.

**Menu boundary**: on a shared database initialized by full, other business permission codes may still exist even when the corresponding services are stopped. That is the state of the shared data, not evidence that the CRM closure includes SRM/procurement/assets/workflow. To verify "the CRM preset has no other business menus", use an independent empty database or tenant initialized with CRM seeds. Do not delete permissions or change roles in the existing shared database to simulate it.

## Stop and recovery

- Stop only the applications started this round: use `docker stop` by container name, or stop explicit services with Compose. Keep mysql/redis/nacos and the data volumes.
- Recovery: `docker start` the registered original service set, or run `up -d --no-build --no-deps` for explicit services. Do not run a full-stack `down -v`, clear volumes or run a global prune.
- If a replacement fails, restore the old image recorded before the replacement in the same service position; do not start a side service.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Port occupied | 3000/8100/8101/8102/8104/3306/6379/8080/8848/9848 | Change the preset closure or use a random port offset |
| Nacos authentication failure | Whether `.env` and Nacos credentials match | Disable authentication or write example secrets |
| Gateway 503/authorization failure | Assertion secret, whether `omni-crm` is registered, route configuration | Only change the frontend request URL |
| CRM menu missing | Whether the current role is granted `crm:*`, `/api/auth/menus`, auth/base health | Edit database menus or roles directly |
| Page opens but list is empty | Current tenant, data scope, whether business data exists | Treat empty data as a startup failure or insert data manually |
| Lead conversion fails | Lead status, required fields, `crm:lead:convert` permission | Bypass the API and insert customers/opportunities directly |
| Migration runs twice | Whether the shared database is already initialized | Clear the database and replay, or re-import seeds |

## Historical stage records and verification boundaries

- **Verified (source path)**: 5 application images built successfully and replaced in place in the same `omni-stack` project; auth/base/crm/gateway healthy and frontend 200; real captcha login returned code 200; `/api/auth/menus` returned 57 nodes including the CRM directory and all 6 sales menus; a read-only query of `omni_crm` returned lead=8, customer=5, opportunity=6, activity=13 and pipeline_stage=6; the 15 originally registered containers were running again afterwards.
- **Verified (generated path, partial)**: G6 structure passed with 8 modules/9 services; the generated backend `./mvnw clean install` reached BUILD SUCCESS, with 45 CRM tests and the gateway tests passing.
- **Unverified**: generated frontend `npm run build` (native binding missing, then a Maven TLS handshake interruption), delivery-role isolation, in-place replacement with the generated image, first installation on a clean database, browser visual rendering and a complete CRM business write flow, and scheduling/asynchronous relay (this preset does not include `xxl-job` or RocketMQ).

Continue with the [preset overview](presets-overview.en.md) or the [CRM fact document](../crm.en.md).
