# The five parts of a harness

A harness is not a single component. Take one task — add a "budget account" to a purchase requisition and verify permissions — and at least five parts must work together. If any is missing, the task loses verifiability at some point.

## 1. Context: let the model know what is true now

Context is not the whole repository; it is the current facts relevant to the task:

- Schema: purchase requisitions live in `proc_*` tables, and the tenant field is handled by an interceptor.
- Module boundaries: approval runs in `omni-workflow`; the procurement service does not add Flowable.
- Existing APIs: requests and responses follow `R<T>`, and pagination uses `R<PageResult<T>>`.
- Error semantics: which status corresponds to missing permission, invalid input and data-scope denial.

In Omni-Stack these facts live in `AGENTS.md`, `docs/architecture.md`, `docs/api-contract.md` and the module documents. Context quality depends on being current, not on being large. Stale facts are more dangerous than missing ones.

## 2. Tools: make actions actually happen

The model needs to perform engineering actions, not just emit text:

- Build and tests: Maven Wrapper, frontend `npm run build` / `npm run lint`.
- Generators: `tools/omni-cli` creates services, CRUD and trimmed preset projects.
- Database: Liquibase migrations and controlled seeds.
- Runtime: services, browser and logs in local Docker.

Tools turn "I think it runs" into "it ran." Without them, verification is reading, and reading cannot find port conflicts, migration order or a failed real login.

## 3. Constraints: expose mistakes before commit

Constraints are rules written in the repository that both people and models can read:

- Write operations declare `resource:action` permission codes, synchronized with seeds and frontend `v-permission`.
- Tenant interception applies only to the module's tables; `sys_mq_message` remains globally scannable.
- Monetary fields use decimal strings, never JSON numbers.
- The data-permission interceptor is registered before the pagination interceptor.

The value of a constraint is not restriction; it turns tacit experience into a checkable condition. A rule that exists only in verbal review cannot constrain a model or automation.

## 4. Verification: completion must have evidence

Verification covers different layers:

| Layer | Example | What it finds |
|---|---|---|
| Unit/structure | Service tests, preset structure assertions | Logic errors and module residue |
| Build | Maven reactor, frontend build/lint | Compilation, types, style rules |
| Documentation | Links, four-language consistency, source excerpts | Dead links, translation drift, stale examples |
| Runtime | Container health, real login, page actions | Configuration, permissions, data scope |
| Delivery | Generated project build and trimming | Hidden dependencies on this repository |

Verification is not "run the most convenient command." A change should be checked at the highest affected layer; passing only a lower layer does not prove the higher one.

## 5. Feedback and checkpoints: make the next run possible

Long tasks always interrupt: a person leaves, the model changes turns, the environment blocks. A harness leaves four kinds of traces:

- Current state: which step and which objects are done.
- Failure evidence: command, exit code, log location, observed behavior.
- Unfinished list: untried, blocked, or awaiting review.
- Next entry point: what to verify first and which action to resume.

These traces must be verifiable, not "I remember changing it." In Omni-Stack, manifest review status, source-digest invalidation, evidence files and unfinished lists carry this responsibility.

## How the parts relate

The five parts are not a pipeline but a loop:

1. Context determines which options the model can propose.
2. Tools let the option land.
3. Constraints stop it from crossing boundaries.
4. Verification proves the result or exposes failure.
5. Feedback writes failure back into context for the next run.

If any part is missing, the loop breaks: no context repeats work, no verification mistakes errors for completion, and no feedback starts the next run from zero.

## Boundaries

More components do not make a better harness. Every tool or check costs maintenance, runtime and false-positive handling. Judge it only by whether it exposes some real failure earlier and more reliably. A component that cannot answer that should be removed rather than stacked.

Next, read [the industry case](harness-industry-practice.en.md), or compare [this project's actual composition](harness-omni-stack-practice.en.md).
