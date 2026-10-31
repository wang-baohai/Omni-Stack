# Secondary development with a harness: one small task end to end

This page uses one task to connect the five parts of a harness: **in a supply-chain learning project, use the CRUD generator to create a runnable "material brand" feature, then verify permissions, menus and builds.** The task is small, but the pattern applies to other business objects.

The commands below come from the [scaffold development guide](../guides/scaffold-development.en.md). This page does not claim the steps were executed in your environment; each step states the expected result and what to record on failure.

## Step 0: set the boundaries

Before touching anything, answer:

- Target preset: `supply-chain` (SRM, procurement and asset).
- Output location: `generated/learning-supply-chain` inside the repository, leaving the main project untouched.
- Do not modify the shared database; do not run the generated project's migrator or seeds.
- Completion: backend build, frontend lint/build and generated-project validation pass; permission and menu entries have real evidence.

That is context and constraints. Without them, the generator may write to the wrong place or start a second environment on this machine.

## Step 1: read the current facts

Read three places before asking a model to change code:

1. hard constraints and the completion checklist in root [AGENTS.md](../../AGENTS.md);
2. the supply-chain module boundaries in [architecture and engineering facts](../architecture.en.md);
3. the material catalog tables, permissions and data-scope rules in the [procurement design](../design/procurement-design.en.md).

After reading, you should be able to state which service owns the material table, the tenant-interception range, the permission code for writes and the failure response. If not, keep reading; do not start generating.

## Step 2: generate the learning project

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset create supply-chain --output generated/learning-supply-chain
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset validate supply-chain --output generated/learning-supply-chain
```

Expected: the first prints `preset created: supply-chain@...`, the second prints `generated preset valid`. On failure, record the full output and exit code; do not delete the directory and retry repeatedly before determining whether the cause is a template, network or disk issue.

The generated project has its own `scaffold.lock`, Compose and AI entry points. Run later commands inside it and do not depend on absolute paths in the original repository.

## Step 3: preview what the generator will change

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --dry-run
```

`--check` validates input; `--dry-run` lists planned changes. Check three things:

- only the expected module changes, with no other business touched;
- permission codes, menus, migrations and frontend entries are included;
- warnings about overwriting existing files do not appear.

Preview is not a formality. If the plan rewrites shared files, stop and inspect the template instead of forcing `--apply`.

## Step 4: apply and verify

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- generate crud --spec scaffold/specs/material-brand.yaml --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install -pl omni-procurement -am
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
```

Expected: the generator finishes and lists changes; the Maven reactor succeeds; frontend lint and build pass. On failure record:

- command, working directory and exit code;
- the first real error, not the final summary line;
- whether the cause is the template, dependency download or local environment;
- which files were produced and whether they must be reverted.

Do not substitute "run it again" for root-cause analysis. When a write result is uncertain, check whether files were produced before deciding to retry.

## Step 5: verify permissions and entries

A successful build only proves the code compiles. Also verify:

1. backend write operations declare `resource:action` permission codes;
2. permission seeds and frontend `v-permission` agree;
3. menus/routes appear only with permission;
4. tenant and data-scope rules still apply;
5. after a real login, the intended tenant sees the right entry and unauthorized requests are denied.

This needs a real runtime and identity, not source reading alone. Without the conditions, record "unverified"; do not turn a build into a functional pass.

## Step 6: leave feedback and a checkpoint

Record at least:

- which objects changed and which module owns each;
- which checks passed and where the evidence is;
- what was not verified and why;
- who does what next.

For a long task, also keep the current step, failure-log location and recovery entry. A checkpoint is not a note for "later"; it is the minimum information that lets the next session avoid starting from zero.

## Using AI for a real change: requirements, facts, boundaries and feedback

The six steps above are "following the generator". Real secondary development is more often **letting AI make a controlled change inside an existing project**. This section reuses the same main example — adding module entries to the workspace — to show how to give AI the requirement, facts, boundaries and verification instead of just "build this for me".

### Requirement: state who sees what under which conditions

A workable requirement has at least four parts:

```text
Role: an authenticated administrator or employee
Entry: workspace app catalog, global app drawer, header menu search
Behavior: clicking a module opens a new tab; navigation inside a module stays in the current tab
Boundary: entries come only from pages the current permissions allow; links carry no token; four languages and narrow screens work
Acceptance: card/drawer/search point to the same target; an unauthorized deep link lands on 403; build, unit tests and structure gates pass
```

Avoid sentences like "improve the workspace" that cannot be verified. **Acceptance comes before implementation**; otherwise AI can only guess.

### Impact analysis: check permissions, tenancy and data first

Before writing code, ask AI four questions:

| Question | Answer for the main example | If the answer is "yes" |
| --- | --- | --- |
| New permission code? | No; entries reuse existing menu permissions | Update `sys_permission` seeds and `v-permission` |
| New tenant table or column? | No | Add a forward-only Liquibase changeset and seed assertions |
| Backend API change? | No; only existing menu and statistics APIs are consumed | Update the API contract and tests |
| Generator closure change? | No; only register page ownership | Update the catalog and trimming logic |

The main example has **no** permission, tenancy or migration change. If a copy of the template does need them, do those as separate tasks first rather than mixing them into a UI change.

### Fact selection: give AI files it can verify, not impressions

Provide facts in order of proximity to the change:

1. Target pages and components (`ConsoleHeader.vue`, `ApplicationDrawer.vue`, `WorkspaceEntryList.vue`, `layout/index.vue`);
2. Derivation logic (`useApplicationNavigation`, route meta);
3. Constraint documents (the permission chapters of `docs/frontend-patterns.md` and `docs/architecture.md`);
4. Existing tests and check commands (`tests/navigation/*`, `package.json` scripts).

Require AI to cite `file:line` and to separate "verified fact" from "guess". Guesses must be labelled and must not enter the implementation.

### File boundaries: say what must not change

Boundaries for the main example:

- **Allowed**: workspace, header, drawer, entry list, module layout, matching locale keys and tests, page ownership in `scaffold/catalog/modules.yaml`;
- **Forbidden**: backend business code, database migrations, formal seeds, permission model, generator closure;
- **Requires authorization**: quality scripts, scanners, README and other cross-WP files.

When AI proposes an out-of-scope change, stop and confirm instead of "fixing it along the way".

### Implementation feedback: every step produces a checkable result

The real process was a closed loop:

1. Build and capture first; the CRM main area was pushed to x=1038;
2. Use DOM measurements to find `ConsoleHeader` and `el-main` in the same row, then add `direction="vertical"`;
3. Retest five widths from 1280 to 1920; the main area starts at x=220 in all of them;
4. Four-language screenshots then revealed the English label and mid-word wrapping, fixed with two minimal changes;
5. Run G1/G2/G6 and register the pre-existing G3 failure separately.

Every step keeps "input → command → output → judgment". **A step without output is not done.**

### Verification agreement: put gates in the task, not after it

| Type | Used by the main example | Note |
| --- | --- | --- |
| Build/static | `npm run build`, `npm run lint` | stop on failure, never skip |
| Unit/types | `npm run test:unit`, `npm run typecheck:e2e` | cover navigation derivation and page behavior |
| Structure | five-preset structure gate | proves no dead links after trimming |
| Runtime | real login + container screenshots | proves what users actually see |
| Existing defect | G3 README negative case | registered separately, not folded into this step's PASS |

Also write the next step after a failure: build failures → first error and recent diff; runtime failures → image/port/login first; structure failures → unregistered pages. **Do not rerun and hope.**

### When permissions or migrations are required

- A new accessible page or button: add the permission code and seed, then `v-permission`;
- A new tenant table/column: write the forward-only changeset, then entities and queries, then refresh the seed manifest;
- Display and navigation only: **no** permission or migration change; the main example belongs here.

Stating "this change did not happen" is as important as stating what was done.

## Common failures

| Symptom | Check first | Do not |
|---|---|---|
| Generator reports a missing path | Current directory, preset output, template completeness | Regenerate in a temporary directory outside the repo to bypass it |
| Maven cannot find a module | Whether the generated project contains it, whether the parent POM was updated | Hand-edit the parent POM to hide a template problem |
| Frontend type errors | Whether the template matches the current component API | Disable type checking |
| Permission entry missing | Permission code, role, menu seed, tenant | Add the menu by editing the database |
| Real login fails | CAPTCHA, tenant, account, gateway and service health | Bypass login with a test backdoor |

## After the walkthrough

You should be able to answer three questions: which facts, tools and checks the task needs; where the evidence is on failure; how the next run continues. Only then have you used a harness, rather than merely run a generator.

Return to [what a harness is](harness-what-and-why.en.md) or [fact maintenance](harness-fact-maintenance.en.md) to fill in concepts.
