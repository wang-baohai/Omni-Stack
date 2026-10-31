# What a preset is: source subset vs generated project

A "preset" is not a second codebase; it is a named module selection. `scaffold/presets/*.yaml` lists only explicit modules, while the real component set is computed from dependencies in `scaffold/catalog/modules.yaml`. Understanding this is the basis for judging the benefit and cost of trimming.

## Explicit modules and the dependency closure

Take core:

```yaml
id: core
displayName: Core platform
description: Authentication, RBAC, organization, dictionary, logs and base jobs.
modules: [base, gateway, mysql, redis, nacos]
```

The file has 5 explicit modules, but `base` depends on `auth`, `auth` and `nacos` depend on `platform`, and `gateway` depends on `base`. The current CLI resolution yields 7 modules:

```text
platform, auth, base, nacos, gateway, mysql, redis
```

So always separate two things:

- **Explicit modules**: the selection written by a person.
- **Dependency closure**: the actual module set computed by the catalog.

Changing only the explicit list without checking the closure easily leads to "not selected means absent." For example, `auth` is not written directly, yet core's closure contains it because login and permissions are prerequisites of the base platform.

## Two ways to use a preset

| Way | Command shape | Output | Use case |
|---|---|---|---|
| Source subset | `dev up --preset core` | Starts the services for the closure inside the current source tree | Reading source, debugging, developing in the same repository |
| Generated project | `preset create core --output ...` | A new trimmed project with its own lock, docs and AI entry points | Delivering an independent project, starting from a smaller codebase |

They are not synonyms:

- A source subset keeps the full source tree and only starts fewer services; unselected modules may still be on disk.
- A generated project trims modules, frontend assets, docs, seeds and config references; the output must be self-contained and must not depend on the original repository path.
- Both still need database migration, seeds, private configuration and Nacos authentication; trimming reduces components, not these prerequisites.

## Benefits and costs

**Benefits**

- Fewer services, ports and processes; a smaller startup and troubleshooting surface.
- Permissions, menus, gateway routes and migrations revolve around the selected closure, with less noise when reading.
- A generated project can be delivered independently without carrying unneeded business.

**Costs**

- A preset describes module selection only; it does not fill secrets, tenants, port-conflict handling or external dependencies.
- A trimmed project still runs migrations and seeds; without a database, authentication and permissions cannot work.
- Resources are not zero. The catalog gives minimum/recommended memory hints per module; core's seven modules sum to 3328 MB minimum and 5632 MB recommended. This is **the arithmetic sum of configuration hints, not measured usage**; actual values depend on JVM, database cache, build and host load.
- Fewer components does not mean zero complexity: Nacos configuration, gateway assertions, database connections and logs still need to be understood.

## Positioning of the five presets

| Preset | One line | Detail page |
|---|---|---|
| `core` | Authentication, RBAC, organization, dictionary, logs and base platform | [core preset](preset-core.en.md) |
| `workflow` | Adds a standalone approval service to the base | [workflow preset](preset-workflow.en.md) |
| `crm` | Adds the CRM sales pipeline | [crm preset](preset-crm.en.md) |
| `supply-chain` | Adds SRM, procurement and asset | [supply-chain preset](preset-supply-chain.en.md) |
| `full` | Union of the five business directions | [full preset](preset-full.en.md) |

This table gives positioning only and does not copy the dependency matrix; for side-by-side comparison see the [preset selection guide](../preset-quick-selection.en.md) and the [dependency matrix](../preset-dependency-matrix.en.md). Before choosing, look at explicit modules, closure, services, ports and resource hints, not just the preset name.

## Five presets × two paths: historical measurement matrix

This table preserves a snapshot taken before the coverage-trimming defect was fixed. Container, menu and pending-review counts describe that snapshot, not the current version. The old generator removed coverage requirements when evidence was missing, so the old G7 and `automated-delivery-pass` **cannot establish current complete acceptance**. Historical build, startup and delivery-role isolation evidence can be reused only while its inputs remain valid. Current release requires G6/G7 with every applicable requirement preserved, and the necessary G11 checks.

| Preset | Source path: in-place replacement in the same `omni-stack` environment | Generated path: fixed artifact position + delivery role |
|---|---|---|
| [core](preset-core.en.md) | 4 images built and replaced in place (auth/base/gateway/frontend); healthy, frontend 200; real CAPTCHA login 200; menus 57 nodes of which 36 business codes come from pre-existing grants in the shared database (menu⇔grant mechanism and seed tailoring 141→0 proven); 15 containers restored afterwards. **Not measured**: the runtime "no business menus" of a first install on an empty database with core seeds. | G6 7 modules/8 services; G7 backend and frontend build/lint of the generated project passed; delivery role self-sufficient with NO_SOURCE/NO_SOCKET; 4 generated images replaced in place and verified with real login/menus/workspace; repository images restored afterwards; delivery gate `automated-delivery-pass` (18 checks, 0 failures, 162 translations pending review, `release_ready=false`). |
| [workflow](preset-workflow.en.md) | 5 images replaced; healthy, frontend 200; real CAPTCHA login returned code 200 on the second attempt (attempts 1/3 were visual misreads, recorded as they happened); menus 57 including the five workflow codes; 15 containers restored. | G6 8 modules/9 services; G7 passed on the first run (in-volume Maven hot cache + proxy); delivery role self-sufficient; 5 generated images replaced, login 200, menus with the five workflow codes, workspace screenshot visually confirmed; repository images rebuilt and restored; delivery gate `automated-delivery-pass` (modules=8, 177 pending review, `release_ready=false`). |
| [crm](preset-crm.en.md) | 5 images replaced; healthy, frontend 200; `/api/crm/leads` returned 401 without a token; login 200, menus 57 including 6 CRM menus; read-only data lead 8/customer 5/opportunity 6/activity 13/stage 6; 15 containers restored. **Unverified**: browser visual rendering of CRM pages (belongs to the visual gates). | G6 8 modules/9 services; the first frontend run failed on a missing rolldown native binding and a Maven TLS interruption, then G7 passed with proxy and hot cache; delivery role self-sufficient (the first run detected a socket left in a committed image and was repeated from the base image); 5 generated images replaced, login 200, menus with the six crm codes; restored from the `pre-wp14` backup tags; delivery gate `automated-delivery-pass` (modules=8, 171 pending review, `release_ready=false`). |
| [supply-chain](preset-supply-chain.en.md) | 8 images replaced (CRM stopped); healthy, frontend 200; 4 business routes returned 401 without a token; administrator login 200, menus 57 (srm 11/procurement 8/asset 5/workflow 5); read-only data suppliers 2, portal users 2, materials/requisitions/orders/assets 0; the portal identity was obtained through the formal invitation → registration → enrollment Saga (COMPLETED within 25 seconds, portal association ACTIVE, re-login, three tabs usable, internal admin endpoints 403); 15 containers restored. **Not completed**: login with the `supplier1` seed password (it exists only as a hash; not guessed, not bypassed). | G6 13 modules/15 services; G7 passed on the first run; delivery role self-sufficient; 8 generated images replaced, login 200, menus 57, screenshots visually confirmed; restored from the `pre-wp15` backups; the first two delivery-gate runs were red because of the stale srm screenshot-coverage marker `partial`, and after confirming that all 8 required_flows have gate-validated real screenshots the marker was corrected to `covered`; run 4 `automated-delivery-pass` (modules=13, 201 pending review, `release_ready=false`). |
| [full](preset-full.en.md) | 9 images replaced; healthy, frontend 200; 5 business routes returned 401 without a token; administrator real CAPTCHA login 200, menus 57 (CRM 7/SRM 11/procurement 8/asset 5/workflow 5/system 13/base 4); the 15-container full baseline stayed up with no extra project, network, volume or port. | G6 14 modules/16 services; G7 passed on the first run; delivery role self-sufficient; 9 generated images replaced and verified via login/menus/workspace; the nine services restored from the `pre-wp16` backups; the first delivery-gate run reported `automated-delivery-fail` because of the srm screenshot marker, 171 pending translations and a post-build `npm ci` ECONNRESET; run 5 `automated-delivery-pass` (modules=14, 210 pending review, `release_ready=false`). |

Three boundaries shared by all ten cells:

- **First install on an empty database was not measured for any of the five presets**: the verification environment is a shared database already initialized by full, and a second environment, a new database or clearing data is not allowed; shared-database results must not be presented as empty-database install results.
- **Release readiness is more than a pending-review count**: the table's `release_ready=false` and 18 green checks are historical. Current translation review, full coverage and actual gate results must also be checked; an old pass cannot be inherited by a new generated artifact.
- **Healthy and a successful login do not establish complete business flows**: the historical windows in this table did not test deep cross-module chains. Any later completed chain needs real business evidence for its own version; this table cannot prove it.

## Four questions before choosing

1. **Which identities and permissions are needed?** Does core already cover login, tenants, roles, organization and dictionary when there is no business module?
2. **Which runtime dependencies are needed?** Are database, Redis and Nacos in the closure; are the scheduler and message queue selected?
3. **Where does configuration come from?** Who provides `.env`, Nacos configuration and database initialization?
4. **Source subset or generated project?** The former suits development, the latter delivery; their verification criteria differ.

## An easy-to-miss boundary

core's description includes "base jobs," but the current core closure **does not include `xxl-job`**. `platform` carries common job modules and `base` contains job-related code and APIs, but the scheduler service is not in core's Compose service set. To actually run system/personal scheduled jobs, choose a preset that includes `xxl-job`, or explicitly accept "job code exists, scheduler absent." Do not present the presence of module code as a working scheduler.

Next, read [the actual components of the core preset](preset-core.en.md) or [every capability of the full preset](preset-full.en.md), or return to the [preset selection guide](../preset-quick-selection.en.md).
