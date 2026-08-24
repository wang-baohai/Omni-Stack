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

~~~powershell
Set-Location tools/omni-cli
npm ci
npm run build
node dist/src/cli.js preset list
node dist/src/cli.js preset explain workflow
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow --dry-run
node dist/src/cli.js preset create workflow --output C:\WorkSpace\my-workflow
node dist/src/cli.js preset validate workflow --output C:\WorkSpace\my-workflow
~~~

The preview reports the dependency closure, backend modules, Compose services, ports, databases, permission roots, and memory estimate. The output must be missing or empty. `scaffold.lock` records source, preset, module, and template versions.

## Required verification

Build the generated backend, then run frontend `npm ci`, lint/build, and `docker compose config --quiet`. Maintainers also run `npm run test:preset-structure` and `npm run test:preset-smoke`. `test:preset-golden` covers all five presets before a release. Database fresh migration, real startup, login/menu/health, and browser smoke tests remain mandatory runtime gates.

## Boundaries

- `core`, `workflow`, and `crm` do not start RocketMQ or XXL-JOB Admin; disable optional integrations or include the infrastructure in a custom preset.
- `supply-chain` resolves Workflow, SRM, and Procurement transitively.
- Custom YAML may only reference catalog IDs; see the [custom preset tutorial](custom-preset-tutorial.en.md).
- In-place pruning and manual deletion of managed generated files are unsupported.
