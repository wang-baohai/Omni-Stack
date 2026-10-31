# The core preset: authentication and the base platform only

core is for "I need something that can log in, manage tenants and permissions, and do no business process for now." Its value is not many features but a small closure and clear boundaries. The components, ports and resources on this page come from the current preset file and catalog; runtime results are split into verified/unverified and structural resolution is never presented as a successful start.

Runtime results on this page are a historical snapshot from before the coverage-trimming fix. Menu and pending-review counts describe that snapshot. Old G7 and delivery-gate passes cannot prove current complete coverage. The current version needs G6/G7 with every applicable requirement retained, plus necessary G11 checks. Build, startup and role-isolation evidence may be reused only against still-valid input digests.

## Who it is for

- Teams that want to verify login, tenants, RBAC, organization, dictionary and logs first.
- Developers who need a minimal back-office skeleton and then add business domains one by one.
- Anyone who needs a generated project without CRM, SRM, procurement, asset or workflow.

It is not for approval flows, sales pipelines, supplier/procurement/asset collaboration, or scheduled jobs that need a scheduler. core's closure has none of those business services and no `xxl-job` scheduler.

## From 5 explicit modules to 7 closure modules

Explicit modules in `scaffold/presets/core.yaml`:

```text
base, gateway, mysql, redis, nacos
```

The closure resolved by `preset explain core`:

```text
platform, auth, base, nacos, gateway, mysql, redis
```

The extra `platform` and `auth` come from dependencies: `base` depends on `auth`, and `auth` and `nacos` depend on `platform`. This explains a common question: why login and permission services exist even though `auth` is not written explicitly.

## Actual services and ports

The seven modules map to these 8 Compose services:

| Service | Source module | Purpose |
|---|---|---|
| `mysql` | mysql | Business and platform data |
| `redis` | redis | Cache and session/token-related data |
| `nacos` | nacos | Service discovery and configuration center |
| `omni-db-migrator` | platform | One-shot schema migration and seed initialization |
| `omni-auth` | auth | Login, tenants, RBAC, organization, dictionary, audit, etc. |
| `omni-base` | base | Base management, monitoring, logs, workspace and job APIs |
| `omni-gateway` | gateway | Unified entry and routing |
| `omni-frontend` | platform | Vue frontend |

Ports declared by the catalog (multiple ports of a module are listed as-is; undeclared purposes are not inferred):

| Module | Declared ports |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

The frontend entry uses the platform-declared port 3000. If a port is occupied, do not change the preset closure to "avoid" it; free the port or adjust the host mapping per deployment documentation. Nacos 3.x port 9848 is the gRPC port and is a standard port, not a random offset.

## Prerequisites and configuration

core is not "zero configuration." Before starting, prepare at least:

1. **Local Docker**, and confirm ports 3000, 8100, 8101, 8102, 3306, 6379, 8080, 8848 and 9848 are free.
2. **Private `.env` configuration**: database, Redis, Nacos authentication, gateway assertions and other secrets. Never commit real values or treat sample values as usable secrets.
3. **Nacos configuration**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-gateway.yml`. When Nacos authentication is enabled, all clients use the same `NACOS_USERNAME` / `NACOS_PASSWORD`; a mismatch shows up as restart loops or authentication failures.
4. **Schema and seeds**: executed by `omni-db-migrator`, a one-shot process, not a long-running service. Do not re-import or clear an already-initialized shared database.
5. **Tenant and administrator identity**: from the authentication seeds. On first login, choose the tenant and seed account described in the guide, and enter the CAPTCHA shown for this run.

Gateway assertion keys are injected only from controlled configuration. When missing or inconsistent with downstream services, APIs return authentication/gateway errors; check the configuration source before changing business code.

## Path 1: start core from source

### First install (clean database)

In an independent environment whose shared data has not been initialized:

```bash
docker compose --parallel 1 --profile core up -d --build
docker compose ps
```

Expected: `omni-db-migrator` finishes and exits, mysql/redis/nacos/auth/base/gateway/frontend start, application services are healthy in `docker compose ps`, and frontend port 3000 is reachable. This path runs migrations and seeds and is only for an empty database or an environment explicitly allowed to initialize. **This page did not run this path**: the verification environment uses a shared database already initialized by full, so first install on an empty database stays marked "unverified" and shared-database results are never presented as it.

### In-place subset on an existing shared environment (verified on this page)

If the database was already initialized by full, do not run the migrator again. Build this round's source images, stop business applications outside the core closure, then replace only the core applications:

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack stop omni-workflow omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

Fill the stop list from the services actually registered in the current environment; do not copy another preset. core contains no workflow, CRM, SRM, procurement, asset, xxl-job or RocketMQ. Keep mysql/redis/nacos and their data; do not run `down -v` or clear volumes. After verification, restore all services with `up -d` for the registered set.

## Path 2: generate an independent core project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create core --output generated/learning-core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate core --output generated/learning-core
```

The generated project has its own `scaffold.lock`, Compose, docs and AI entry points. Use its own build commands after entering it; do not start the generated Compose project from the original repository, and do not run the generated migrator against the shared database.

Historical record: G6 structure (7 modules/8 services), generated backend build and frontend build/lint passed. Self-builds passed in a delivery role with only generated-artifact and cache subpaths, no source checkout and no Docker socket. Four generated images were replaced in the same environment, checked through login, menus and the workspace, then repository images were restored. At that time `verify-preset-delivery core` reported `automated-delivery-pass` and `release_ready=false`. Because the old coverage trimming could omit requirements, this does not prove current G7 or release acceptance, and the remaining work cannot be described as translation review alone.

## First access and login

1. Open `http://localhost:3000` and confirm the Wiki home page and login entry load.
2. Choose the development tenant described in the guide, enter the seed account and the **CAPTCHA shown for this run**; do not reuse an old CAPTCHA or use an authentication backdoor.
3. After login, check:
   - open My Workspace from the home page in its own browser tab, retaining the original Wiki tab;
   - system management, user/role/permission, organization, tenant and log entries appear according to role;
   - base management, dictionary and job APIs appear according to `base` permissions.
4. If menus keep failing to load, check auth/base/gateway health, Nacos configuration and `/api/auth/menus` before the frontend; do not edit menu tables directly.

## Success criteria

- Application services in the core closure are healthy in `docker compose ps`, and the migrator exited as expected.
- Frontend port 3000 returns 200 and static assets load.
- Real CAPTCHA login succeeds, and the menu API returns nodes visible to the current identity.
- Authentication, RBAC, organization, dictionary and log entries work.
- No new project, network, volume or port offset; shared data was neither cleared nor re-migrated.

**Menu boundary**: the menu API returns only "tenant permission tree ∩ current identity permission codes" (`MenuController` filters DIRECTORY/MENU nodes by the JWT authorities), independent of which services run. On a shared database initialized by full, business permission codes remain granted to administrator and other roles, so business entries still appear in menus after business services stop — measured in the core window: 36 of the administrator's 57 menu nodes are business DIRECTORY/MENU codes, matching the shared-database grant query; this is the shared-data state, not evidence that the core closure contains business modules. core's tailored seeds contain no business permission rows at all (141 business permission rows in the source seeds, 0 in the generated core), so **a first install on an empty database initialized with core seeds shows no business entries**; this conclusion is proven by the menu derivation mechanism plus seed tailoring, while the runtime measurement still requires an independent empty-database/tenant environment. Do not delete permissions or change roles on the existing shared database to simulate it.

## Stop and restore

- Stop only applications started this round: `docker stop` by container name, or stop explicit Compose services; keep mysql/redis/nacos and data volumes.
- Restore: `docker start` the registered original service set, or run `up -d --no-build --no-deps` for explicit services; never `down -v`, clear volumes or run a global prune.
- If replacement fails, restore the old image at the same service position using the ID recorded before replacement; do not start a side service.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Port occupied | 3000/8100/8101/8102/3306/6379/8080/8848/9848 | Change the preset closure or randomly offset ports |
| Nacos authentication fails | Whether `.env` and Nacos credentials match | Disable authentication or write sample secrets |
| Gateway 503/authentication failure | Assertion keys, downstream registration, route configuration | Only change the frontend request URL |
| Menu load failure | auth/base/gateway health, `/api/auth/menus`, Nacos config | Edit database menus directly |
| Job entries exist but jobs do not run | Whether core includes `xxl-job` (it does not) | Present job code as a working scheduler |
| Migration runs twice | Whether the shared database is already initialized | Clear the database or re-import seeds |
| Maven download `Remote host terminated the handshake` | Corporate-network TLS flakiness; retry the same command later | Change dependency versions or repository coordinates |
| `npm ci` ECONNRESET | Network flakiness; retry and keep the offline cache | Delete lockfiles or skip lint |
| All container downloads fail (DNS/handshake) | Set `HTTP_PROXY/HTTPS_PROXY` to the host proxy (inside containers use `host.docker.internal:<mixed-port>`), and pass same-named `--build-arg` for image builds | Blame the product or the preset |
| Verifier container lacks `docker`/BuildKit | Install the docker CLI and buildx plugin and set `DOCKER_BUILDKIT=1` | Copy the generated project to a host temp directory to build |
| Maven local repository re-downloads after every container rebuild | Point `-Dmaven.repo.local` at the fixed validation-volume cache | Bake private repository configuration into images |

## Verified and unverified

- **Verified**: core closure resolution, 8-service list, ports and resource hints; source application image build; in-place replacement of auth/base/gateway/frontend in the same `omni-stack` project; healthy services, frontend 200, real CAPTCHA login 200; core-window menus and workspace UI (including the conclusion that business residue is driven by shared-database grants); healthy after restoring the full service set; generated G6/G7, delivery-role self-sufficiency (no source, no socket), in-place replacement of the four generated images with real entry verification, and restoration of repository images afterwards; delivery gate `automated-delivery-pass`.
- **Unverified**: first install on a clean empty database; runtime measurement of "no business menus" under core seeds (mechanism and seed tailoring proven, runtime pending an independent empty-database environment); release readiness (`release_ready=false`, translations pending designated review); scheduled job execution (core has no scheduler).

Continue with [the preset overview](presets-overview.en.md) or the [first-start guide](../guides/quick-start.en.md).
