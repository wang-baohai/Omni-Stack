# The workflow preset: core platform plus BPMN and approvals

workflow adds a standalone workflow service on top of core's authentication and base platform. It suits BPMN modeling, process publishing, tasks, approvals and process instances without CRM, SRM, procurement or asset business. Components come from the current preset and catalog; runtime results are split into verified/unverified.

Runtime results here are staged historical records from before the coverage-trimming fix, not current-version results. Early failures remain recorded; later success cannot retroactively sign off an earlier stage. An old G7 pass cannot prove current complete coverage. Current release requires G6/G7 with every applicable requirement preserved, plus necessary G11 checks.

## From 5 explicit modules to 8 closure modules

Explicit modules in `scaffold/presets/workflow.yaml`:

```text
workflow, gateway, mysql, redis, nacos
```

Closure resolved by `preset explain workflow`:

```text
platform, auth, base, workflow, nacos, gateway, mysql, redis
```

`workflow` depends on `base`, `gateway` on `base`, and `base` on `auth`. Login, permissions, organization, dictionary and base management remain in the closure even though `auth`/`base` are not written explicitly.

## Actual services and ports

The 8 modules map to 9 Compose services:

| Service | Source module | Purpose |
|---|---|---|
| `mysql` | mysql | Business, platform and Flowable data |
| `redis` | redis | Cache and token-related data |
| `nacos` | nacos | Service discovery and configuration center |
| `omni-db-migrator` | platform | One-shot schema and seed initialization |
| `omni-auth` | auth | Login, tenants, RBAC, organization, dictionary, audit |
| `omni-base` | base | Base management, logs, workspace and job APIs |
| `omni-workflow` | workflow | BPMN models, deployment, tasks, approvals, process instances |
| `omni-gateway` | gateway | Unified entry and routing |
| `omni-frontend` | platform | Vue frontend |

Catalog-declared ports:

| Module | Declared ports |
|---|---|
| platform | 80, 3000 |
| auth | 8100, 19900, 9900 |
| base | 8101, 19901, 9901 |
| workflow | 8103, 19903, 9903 |
| gateway | 8102, 19902 |
| nacos | 8080, 8848, 9848 |
| mysql | 3306 |
| redis | 6379 |

`omni-workflow` is a standalone service (8103), never merged into `omni-base` or `omni-auth`; Flowable exists only in `omni-workflow`, and business services must not add Flowable dependencies.

## Prerequisites and configuration

- **Docker and ports**: 3000, 8100, 8101, 8102, 8103, 3306, 6379, 8080, 8848 and 9848 must be free.
- **Private `.env`**: database, Redis, Nacos authentication and gateway assertion secrets; never commit real values.
- **Nacos configuration**: `omni-common.yml`, `omni-auth.yml`, `omni-base.yml`, `omni-workflow.yml`, `omni-gateway.yml`; Nacos credentials must match everywhere.
- **Migration and seeds**: `omni-db-migrator` runs once; do not re-migrate or clear an initialized shared database.
- **BPMN models**: `wf_process_model.model_key` is unique per tenant and must match the BPMN `<process id>`; publishing must pass `BpmnXmlValidator`. Demo models may come from seeds; use IDs from your own run.
- **Candidates and roles**: the `omni:assignment` JSON extension is the only candidate-resolution entry, parsed on task start. A login account is not automatically a candidate; it needs the role and data scope.

## Path 1: start workflow from source

### Clean-database first install

```bash
docker compose --parallel 1 --profile workflow up -d --build
docker compose ps
```

Expected: the migrator finishes and exits; mysql/redis/nacos/auth/base/workflow/gateway/frontend start; apps are healthy and frontend port 3000 is reachable. This runs migrations and seeds and is only for an empty database or an environment explicitly allowed to initialize.

### In-place subset on an existing shared environment

If the database was initialized by full, do not run the migrator. Build this round's images, stop business apps outside the workflow closure, then replace the workflow apps:

```bash
docker compose --parallel 1 -p omni-stack build omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack stop omni-crm omni-srm omni-procurement omni-asset xxl-job-admin rocketmq-broker rocketmq-namesrv
docker compose -p omni-stack up -d --no-build --no-deps --force-recreate omni-auth omni-base omni-workflow omni-gateway omni-frontend
docker compose -p omni-stack ps
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3000/
```

Fill the stop list from the actual environment; keep mysql/redis/nacos and data; never `down -v` or clear volumes.

## Path 2: generate an independent workflow project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create workflow --output generated/learning-workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate workflow --output generated/learning-workflow
```

The generated project has its own `scaffold.lock`, Compose, docs and AI entries. Build it inside itself; do not start its Compose project from the original repository, and do not run its migrator against the shared database.

Early historical stage: G6 structure (8 modules/9 services) and G7 generated backend/frontend build and lint passed; delivery-role isolation and generated-image in-place replacement are incomplete.

## First use

1. Open `http://localhost:3000`, choose the development tenant, and enter the seed account and the **CAPTCHA shown for this run**.
2. The home page displays the Wiki. Open My Workspace in a separate tab; approvals and personal tasks share one screen. From the application directory, open the workflow module in a new tab and check model and instance entries according to role.
3. Suggested order: model design/validation → publish (unique model_key) → start a process → candidates handle tasks → inspect process instance and history.
4. If tasks are empty, confirm the current identity is in the candidate scope, the process is published and tasks are created; do not edit task tables directly.

## Success criteria

- Workflow closure apps are healthy and the migrator exited as expected.
- Frontend port 3000 returns 200.
- Real CAPTCHA login succeeds and workflow menu/task entries appear per permission.
- A published model can start a process and a candidate can see and handle the task.
- No new project, network, volume or port offset; shared data was not cleared or re-migrated.

**Menu boundary**: a shared full database may keep CRM/SRM/procurement/asset permission codes; that is shared-data state, not evidence that the workflow closure contains those businesses. To verify "workflow has no other business menus," use an independent empty database/tenant initialized with workflow seeds.

## Stop and restore

- Stop only applications started this round (by container name or explicit services); keep mysql/redis/nacos and data volumes.
- Restore: `docker start` the registered original service set, or run `up -d --no-build --no-deps` for explicit services; never `down -v`, clear volumes or run a global prune.
- If replacement fails, restore the old image at the same service position using the ID recorded before replacement; do not start a side service.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Port conflict | 8103/3000/8100/8101/8102/3306/6379/8080/8848/9848 | Change the closure or randomly offset ports |
| Nacos authentication fails | Whether `.env` and Nacos credentials match | Disable authentication or write sample secrets |
| Model publish fails | Whether model_key is unique per tenant, matches BPMN process id, and XML passes validation | Bypass validation by writing the database |
| Tasks are empty | Candidate extension, roles, whether tasks exist, whether the process is published | Edit task/candidate tables directly |
| Gateway 503 | Assertion keys, downstream registration, routes | Only change frontend URLs |
| Menu load fails | auth/base/workflow/gateway health, Nacos, `/api/auth/menus` | Edit menu tables directly |

## Historical stage records and verification boundaries

- **Verified**: workflow closure 8 modules/9 services/18 ports; five application images built and replaced in place; auth/base/workflow/gateway healthy, frontend 200; generated G6/G7 build passed.
- **Unverified**: real CAPTCHA login for workflow (two visual attempts failed this round); real workflow menu/task/approval operations; clean-database first install; no business menus; generated-image replacement and delivery-role isolation; scheduled jobs (workflow has no xxl-job).

Continue with [the preset overview](presets-overview.en.md) or the [workflow fact document](../workflow.en.md).
