# Preset Project Upgrade Guide

`scaffold.lock` records the source, catalog, preset, module, and generator-template versions. The default upgrade strategy is to generate a new directory and migrate handwritten work; destructive in-place pruning is unsupported.

## Before upgrading

1. Commit or back up the project, database, and environment configuration.
2. Preserve the current lock and detect manual drift in managed files.
3. Review release notes plus catalog `compatibility` and `deprecation` metadata.
4. Compare official presets with `omni preset diff`; compare resolved closures for custom YAML.
5. Identify database expand/migrate/contract windows and cross-service contract changes.

## Procedure

1. Run `validate`, `explain`, and `create --dry-run` with the new CLI.
2. Generate into a new empty directory.
3. Compare lock files and generated output. Do not copy old Maven, Compose, Gateway, seed, or catalog registrations over the new output.
4. Migrate handwritten domain code, tests, and non-secret environment configuration. Re-express managed changes in declarations or templates.
5. Exercise database backup, fresh migration, and clone-upgrade from the production-shaped snapshot.
6. Pass backend, frontend, Compose, residual, login/menu/health, core-flow, and E2E gates.
7. Deploy with one release cycle of compatibility rollback capacity.

`preset.version` tracks the composition boundary; catalog `version` tracks its fact structure; module versions track implementations; `templates.*` track generated shape; `source.version` identifies Omni-Stack. A major preset or template change requires manual migration review.

## Rollback and completion

Roll back applications using the previous image/project against a compatible expanded schema. Do not run an untested down SQL; add a forward Liquibase repair instead. If the new project has not been deployed, discard its directory—the original was not modified.

The upgrade is complete when the lock matches modules/routes/permissions/database/Compose, fresh and clone-upgrade are idempotent, removed modules leave no residuals, tenant and role flows pass, and README/matrix/maintenance docs match the new catalog.
