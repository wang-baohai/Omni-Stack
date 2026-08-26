# Presets, Lightweight Mode, Service Creation, and CRUD Generation

Use the CLI and module catalog to build a new project. Do not copy an existing service and perform global string replacement.

## 1. Choose a Preset

```bash
npm --prefix tools/omni-cli run dev -- preset list
npm --prefix tools/omni-cli run dev -- preset explain crm
```

A preset selects backend modules, frontend pages, seeds, migrations, Compose profiles, and external dependencies. `supply-chain` includes SRM, Procurement, and Asset; a smaller preset reduces startup and learning cost. See [Preset Quick Selection](../preset-quick-selection.en.md) and the [Dependency Matrix](../preset-dependency-matrix.en.md).

## 2. Lightweight Development

```bash
npm --prefix tools/omni-cli run dev -- doctor
npm --prefix tools/omni-cli run dev -- dev plan --preset crm
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

The plan is the run's source of truth. Optional MQ, Workflow, or scheduler integration omitted by a preset is explicitly disabled rather than left retrying.

## 3. Create a Service

```bash
npm --prefix tools/omni-cli run dev -- create-service inventory
```

The generator creates the Maven module, application, configuration, health check, Docker integration, integration plan, and lock information. Review the plan, then verify with JDK 25:

```bash
cd omni-backend
./mvnw clean install -pl omni-inventory -am
```

A new service with `@RequestBody` implements `XssConfigProvider`. Servlet services use `omni-common-service`; never copy the reactive Gateway security chain.

## 4. Generate CRUD

```bash
npm --prefix tools/omni-cli run dev -- crud plan path/to/spec.yaml
npm --prefix tools/omni-cli run dev -- crud generate path/to/spec.yaml
```

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
- Frontend lint has zero warnings and production build passes.
- Permissions and data scope have negative tests.
- Fresh, upgrade, and target preset pass.
- Documentation, four-language keys, and screenshots are updated.
- No temporary scripts, generated output, or test credentials remain.

