# Presets, Lightweight Mode, Service Creation, and CRUD Generation

Use the CLI and module catalog to build a new project. Do not copy an existing service and perform global string replacement.

## 1. Choose a Preset

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

A preset selects backend modules, frontend pages, seeds, migrations, Compose profiles, and external dependencies. `supply-chain` includes SRM, Procurement, and Asset; a smaller preset reduces startup and learning cost. See [Preset Quick Selection](../preset-quick-selection.en.md) and the [Dependency Matrix](../preset-dependency-matrix.en.md).

## 2. Lightweight Development

`dev` has exactly five subcommands: `up`, `down`, `status`, `doctor`, `logs`. There is no `dev plan`; `dev up` prints the plan before Docker runs.

```bash
npm --prefix tools/omni-cli run dev -- dev doctor
npm --prefix tools/omni-cli run dev -- dev up --preset crm
npm --prefix tools/omni-cli run dev -- dev status
npm --prefix tools/omni-cli run dev -- dev logs --module crm --tail 200
npm --prefix tools/omni-cli run dev -- dev down
```

`dev up` prints `development preset|module: <id>`, `mode: lite|full`, `modules:`, `services:`, `observability:` and the degradation line; that block is the run's source of truth. Mode is inferred from the preset or module (`core`, `workflow`, `crm` are lite) and can be forced with `--mode lite|full` so both modes exercise one contract. Closures containing SRM, Procurement, or Asset are rejected in lite instead of silently dropping realtime consumers. Lite explicitly disables the Nacos client, XXL-JOB, the MQ relay and autodetect (Outbox still writes); full no longer synthesizes those switches, so `.env` stays authoritative.

Run isolated rows with `--project-name` (the Compose project that names containers, networks, and volumes) and `--set NAME=VALUE`, which accepts only host ports and volume names declared in Compose (for example `OMNI_MYSQL_HOST_PORT=23306`, `OMNI_MYSQL_VOLUME_NAME=wp011-row1-mysql`). Secrets are refused and values never reach the logs.

Named volumes are kept by default. `dev down` removes volumes only with `--volumes` plus `--confirm-delete-volumes` plus `--project-name`, and it first verifies each volume's `com.docker.compose.project` label, because fixed names such as `omni-stack-mysql-data` are shared across projects. Without an explicit project, Compose derives the name from the directory and the deletion path stays unavailable.

## 3. Create a Service

`create-service` is **preview-only by default** and writes nothing. Review the plan first:

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

After confirming the previewed resource/integration list, you **must explicitly add `--apply`** to perform the atomic write and create the module:

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory --apply
```

Only `--apply` generates the Maven module, application, configuration, health check, Docker integration, frontend/permission/docs integration and lock files (written to the current workspace by default; if you pass `--output`, adjust the cwd of later build/integration commands). The module does not exist before `--apply`, so compiling first would fail. After generation, verify with JDK 25:

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

A new service with `@RequestBody` implements `XssConfigProvider`. Servlet services use `omni-common-service`; never copy the reactive Gateway security chain.

## 4. Generate CRUD

```bash
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --check
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --dry-run
npm --prefix tools/omni-cli run dev -- generate crud --spec path/to/spec.yaml --apply
```

The command is `generate crud --spec <file>`; `crud plan` and `crud generate` do not exist. `--check` validates the declaration only, `--dry-run` renders the change plan, and `--apply` performs the atomic cross-file write; `--apply` and `--dry-run` are mutually exclusive.

Generation covers frontend/backend types, APIs, services, persistence, permissions, migration, tests, and i18n tasks. Developers still add domain state, data-scope mapping, idempotency, and business validation.

## 5. Database and Permissions

- Add only forward Liquibase changeSets.
- Keep stable seed data only.
- Update `scripts/sql/seed/auth.sql` for write permissions.
- Refresh `database/seed/manifest.yaml` digest and assertions.
- Verify both fresh and upgrade paths.

Do not add permanent `migrate-*.sql` or temporary repair scripts.

## 6. Custom Presets

A custom preset declares dependency closure, conflicts, frontend/backend selection, migrations, and seeds. Run the five-preset golden matrix and update the lock. See [Custom Presets](../custom-preset-tutorial.en.md) and [Preset Maintenance](../preset-maintenance.en.md).

## 7. Definition of Done

- JDK 25 Reactor passes.
- `omni dev doctor` reports no FAIL (SKIP while the stack is down is not a failure, but is not delivery evidence either).
- Frontend lint has zero warnings and production build passes.
- Permissions and data scope have negative tests.
- Fresh, upgrade, and target preset pass.
- Documentation, four-language keys, and screenshots are updated.
- No temporary scripts, generated output, or test credentials remain.

