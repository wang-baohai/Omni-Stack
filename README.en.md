# Omni-Stack

A Spring Boot 4 + Vue 3 microservices scaffold for enterprise applications with tenant isolation, access control, and room for business extensions. Source code, engineering facts, illustrated tutorials, and AI collaboration rules live in one repository.

[简体中文](README.md) · [日本語](README.jp.md) · [한국어](README.kr.md)

## Highlights

- Read the Wiki from the public home page; find authorized applications in your workspace and open business modules in new tabs.
- Multiple tenants, functional permissions, and data scopes; CAPTCHA login, social login, and OAuth2 authorization.
<!-- omni:modules:workflow -->
- A standalone Workflow service for BPMN modeling, publishing, approvals, and countersigning.
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB manages system and personal tasks.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM covers leads, customers, opportunities, and follow-up.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset connects supplier, purchasing, and asset processes.
<!-- omni:modules:end -->
- Transactional Outbox, idempotent consumption, and dead-letter management; optional metrics, tracing, and centralized log collection.
- `tools/omni-cli` generates independent preset projects, services, and CRUD.
- Harness practices support ongoing development with concise engineering facts, shared AI workflows, and verification evidence.

Technology baseline: Spring Boot 4.0.6, JDK 25, Spring Cloud, Vue 3, TypeScript, Vite, and Element Plus.

## Project Structure

| Path | Purpose and entry points |
|---|---|
| `omni-backend/` | Multi-module Maven backend. `omni-backend/pom.xml` aggregates the services and shared modules. |
| `omni-frontend/` | Vue 3 application with the public home page, personal workspace, and business screens. |
| `database/` · `scripts/sql/seed/` | Liquibase migrations live in `database/changelog/`. `database/seed/manifest.yaml` registers seed data and validation rules. |
| `scripts/fixtures/` | Controlled test fixtures and screenshot capture tools. |
| `scaffold/` · `tools/omni-cli/` | `scaffold/catalog/modules.yaml` defines modules, `scaffold/presets/` defines presets, and the CLI generates projects, services, and CRUD. |
| `docker/` · `compose.yaml` | Dockerfiles live in `docker/`. `compose.yaml` orchestrates local services, and `compose.tools.yaml` provides build and verification tools. |
| `observability/` | Optional configuration for metrics, tracing, logs, dashboards, and alerts. |
| `docs/` | Engineering reference, the four-language Wiki, and the [source code index](docs/reference/source-map.en.md). |
| `AGENTS.md` · `ai/` | Project operating rules and shared AI resources. |
| `.agents/skills/` | Codex project skill entry points, generated from `ai/skills/`. Includes `omni-change` for project changes and reviews and `grill-me` for refining plans through questions. |
| `.qoder/skills/` | Qoder project skill entry points for the same `omni-change` and `grill-me` skills, synchronized by the adapter. |
| `.github/workflows/` | GitHub Actions configuration for the source repository. `quality.yml` can run on pushes, pull requests, a schedule, or manual dispatch. It checks the backend, frontend, docs, AI skill entry points, database migrations, scaffolding, and security. |

### Backend modules

- `omni-gateway` handles incoming requests and routing, `omni-auth` handles authentication and authorization, and `omni-base` provides reference data and platform administration.
<!-- omni:modules:workflow -->
- `omni-workflow` handles BPMN modeling, approvals, and countersigning.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- `omni-crm` manages leads, customers, and opportunities.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- `omni-srm`, `omni-procurement`, and `omni-asset` handle suppliers, purchasing, and assets, respectively.
<!-- omni:modules:end -->
- `omni-common` and `omni-common-*` provide shared infrastructure, and `omni-db-migrator` runs database migrations.

### Frontend source entry points

- `omni-frontend/src/views/` contains pages, `omni-frontend/src/components/` contains reusable components, and `omni-frontend/src/api/` contains backend API clients.
- `omni-frontend/src/router/` handles routing, `omni-frontend/src/stores/` holds Pinia state, and `omni-frontend/src/locales/` holds UI translations.
- `omni-frontend/tests/` contains unit tests. `omni-frontend/e2e/` and `omni-frontend/e2e-docs/` cover end-to-end tests and documentation screenshots.

### AI tool directories

`ai/` is the source for project-level AI assets. `ai/manifest.yaml` records their origins, versions, licenses, and supported tools.

- `ai/skills/` holds skill sources, including the `omni-change` workflow for project changes and `grill-me` for questioning a plan.
- `ai/prompts/` holds templates for change review and documentation updates.
- `ai/knowledge/` records project practices such as Docker verification.
- `ai/adapters/bootstrap.mjs` generates and synchronizes the Codex and Qoder project skill entry points from the shared sources.

To update a skill, edit `ai/skills/` first, then sync the tool entry points with the adapter. See the [AI tools guide](ai/README.en.md) for installation and verification.

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[Full Wiki: introduction, illustrated usage, startup, and development](docs/wiki/index.en.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
