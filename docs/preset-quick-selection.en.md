# Project Preset Quick Selection

Presets generate a new, bounded Omni-Stack project without deleting content from the source repository. `scaffold/catalog/modules.yaml` is the source of truth for modules, dependencies, ports, and resources. See the [dependency matrix](preset-dependency-matrix.en.md) for the resolved facts.

## Which preset to choose

| Need | Preset | Business boundary |
|---|---|---|
| Login, RBAC, organization, dictionaries, logs, and basic jobs | `core` | No workflow or business domain |
| BPMN, approvals, work queue, and process instances | `workflow` | `core` + Workflow |
| Sales, customers, and opportunities | `crm` | `core` + CRM; no supply chain |
| Supplier, procurement, and asset lifecycle | `supply-chain` | Workflow + SRM + Procurement + Asset |
| Every capability in this repository | `full` | CRM, supply chain, assets, and full infrastructure |

Start with the smallest preset that covers near-term requirements. Adding a catalog module later is safer than manually deleting modules from `full`.

## Commands

Run these commands at the source repository root. Dependency installation, CLI build and execution use the local Docker tooling container; learning output goes into the separate generated/my-workflow directory.

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset explain workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create workflow --output generated/my-workflow --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create workflow --output generated/my-workflow
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate workflow --output generated/my-workflow
~~~

The preview reports the dependency closure, backend modules, Compose services, ports, databases, permission roots, and memory estimate. The output must be missing or empty. `scaffold.lock` records source, preset, module, and template versions.

## Required verification

First create `.env` in the generated project from its bundled `.env.example` and fill in self-generated random secrets (required items and the isolation project/ports/volume/image namespace are covered in the Custom Preset Tutorial §4; without `.env`, `docker compose config` fails with required variable missing). Then build the generated backend, and run frontend `npm ci`, lint/build, and `docker compose config --quiet`. Repository maintainers also run:

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run test:preset-structure
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run test:preset-smoke
~~~

`test:preset-golden` runs the full build matrix for all five presets, suitable for nightly or pre-release. Database fresh migration, real startup, and browser smoke tests are runtime acceptance and cannot be replaced by structure checks.

## Boundaries

- `core`, `workflow`, and `crm` do not start RocketMQ or XXL-JOB Admin; disable optional integrations or include the infrastructure in a custom preset.
- `supply-chain` resolves Workflow, SRM, and Procurement transitively.
- Custom YAML may only reference catalog IDs; see the [custom preset tutorial](custom-preset-tutorial.en.md).
- In-place pruning and manual deletion of managed generated files are unsupported.
