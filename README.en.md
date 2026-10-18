# Omni-Stack

A Spring Boot 4 + Vue 3 microservices scaffold for enterprise applications with tenants, permissions, approvals, and business domains. Source code, engineering constraints, tutorials, and reusable AI workflows ship together.

[简体中文](README.md) · [日本語](README.jp.md) · [한국어](README.kr.md)

## Key capabilities

- Tenant isolation, functional permissions, data scope, CAPTCHA, social login, and OAuth2 authorization flows.
<!-- omni:modules:workflow -->
- A standalone Workflow service for BPMN design, publishing, approvals, and countersign.
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB for system and personal jobs.
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM sales flows.
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset collaboration across suppliers, purchasing, and assets.
<!-- omni:modules:end -->
- Transactional Outbox, idempotent consumers, dead letters, and optional metrics, tracing, and logs.
- `tools/omni-cli` generates services, CRUD, and independently pruned projects from presets.
- [AGENTS.md](AGENTS.md) and engineering facts constrain execution; [AI assets](ai/README.en.md) provide reusable tooling entry points.

Main technologies: Spring Boot 4.0.6, JDK 25, Spring Cloud, Vue 3, TypeScript, Vite, and Element Plus. POMs and npm lockfiles define exact dependency versions.

## Quick experience

Follow the [first-start tutorial](docs/guides/quick-start.en.md) to get the code, prepare local Docker, and fill every required secret in `.env`. Build containers supply JDK and Node.

```bash
docker compose --profile full up -d --build
docker compose ps
```

Frontend: `http://localhost:3000`. Select the development tenant and seeded account, then manually enter the current CAPTCHA. Select a preset before running only part of the platform. Migrator owns schema migration and base seeds; see [data sources](docs/wiki/data-and-fixtures.en.md).

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[Selection guide](docs/preset-quick-selection.en.md) · [Preset Dependency Matrix](docs/preset-dependency-matrix.en.md)
<!-- omni:preset-table:end -->

## Documentation and contributions

- [Project Wiki](docs/wiki/index.en.md): code acquisition, business operations, debugging, extension, and operations.
- [Architecture and engineering facts](docs/architecture.en.md), [API contract](docs/api-contract.en.md): current behavior for maintainers.
- [Independent documentation site](docs/wiki/site-maintenance.en.md): Docker, local search, and static publishing.
- [Repository governance](docs/repository-governance.md), [changelog](CHANGELOG.md), [architecture decisions](docs/adr/0001-scaffold-upgrade-foundations.md).

Read the relevant conventions and run affected checks in Docker before contributing. Issues and PRs are welcome on [GitHub](https://github.com/wang-baohai/Omni-Stack) or [Gitee](https://gitee.com/wang-baohai/Omni-Stack).

[Apache License 2.0](LICENSE) · © Wang Baohai
