# Project Preset Maintenance

This guide is for scaffold maintainers. `scaffold/catalog/modules.yaml` is the sole source of truth; `scaffold/presets/*.yaml` only declares entry modules. `npm run docs:preset` generates the dependency matrices and README tables.

## Managed artifacts

- Catalog and Schemas: `scaffold/catalog/modules.yaml`, `module.schema.json`, and `preset.schema.json`.
- Official declarations: `scaffold/presets/*.yaml`.
- Generator: `tools/omni-cli/src/preset-generator.ts`.
- Golden matrix: `tools/omni-cli/scripts/preset-golden.mjs`.
- Generated snapshot: `scaffold.lock`.

## Add a module

1. Complete its code, migration, idempotent seed, permissions, Compose, Gateway, and documentation.
2. Append it to the catalog in valid dependency order. `dependencies` may only reference earlier catalog modules.
3. Declare backend modules; frontend views/components/APIs/i18n; routes; Compose services; changelogs and seeds; provisioning; Nacos; ports; MQ; XXL-JOB; docs; resources; compatibility; and deprecation.
4. Use `optionalModules` only for optional integration. It does not replace a runtime feature switch. Declare conflicts explicitly.
5. Run catalog validation and fix missing paths, dangling Compose dependencies, port collisions, and unmanaged formal modules.
6. Add the entry module to the appropriate preset without copying transitive dependencies.
7. Add pruning tests, generated-build evidence, and runtime smoke coverage.

Domain state machines, DataScope mappings, inherited child scope, Sagas, and idempotency remain in business modules and must not move into the generic generator.

## Change a preset

Edit only the entry modules and metadata in `scaffold/presets/<id>.yaml`, then run:

~~~powershell
Set-Location tools/omni-cli
npm test
npm run test:preset-structure
npm run docs:preset
npm run docs:preset:check
~~~

Bump patch for fixes, minor for backward-compatible capability additions, and major for breaking boundary changes. Module and preset versions are independent.

## Gates

- PR: `npm run test:preset-smoke` validates `core` and one business preset with Maven `clean verify`.
- Nightly/release: `npm run test:preset-golden` runs five-preset `clean install`, frontend ci/lint/build, Compose validation, and residual scans.
- Runtime: db-migrator fresh, real startup, login, menu, health, and a core flow for every preset; full E2E for `full`.

Residual checks cover Maven, Dockerfile, Compose, Gateway, views, components, APIs, i18n, permissions, database, MQ, and module-exclusive docs.

## Failure and rollback

Generation uses a sibling staging directory and removes it on failure; a non-empty destination is rejected before copying. Diagnose in this order: Schema → closure/conflict → catalog resources → pruning → Maven/npm → Compose → fresh database → runtime/E2E.

Do not patch managed output. Fix the source catalog, template, or generator and regenerate to a new directory. Never rewrite an executed Liquibase changeSet; use a forward fix and retain a database compatibility window for application rollback.
