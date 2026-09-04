# Preset Project Upgrade Guide

A preset project records, via `scaffold.lock`, the source version, catalog version, preset version, module versions and the service/CRUD/preset template versions at generation time. Upgrades default to the strategy of "generate a new directory and migrate handwritten changes"; destructive in-place pruning is unsupported.

## Before upgrading

1. Commit or back up the existing project, database and environment configuration.
2. Preserve the current `scaffold.lock` and confirm whether managed files have manual drift.
3. Read the source repository release notes, the catalog's `compatibility`/`deprecation` and preset version changes.
4. Use `omni preset diff <old> <new>` to compare official presets; for custom presets compare the resolved dependency closure.
5. Identify database expand/migrate/contract windows and cross-service contract changes.

## Recommended upgrade procedure

1. Use the new-version CLI to run `validate`, `explain` and `--dry-run` against the original preset or custom YAML.
2. Generate into a brand-new empty directory.
3. Compare the old and new `scaffold.lock` and generated files; do not directly copy old Maven, Compose, Gateway, seed or catalog registrations.
4. Migrate handwritten domain code, tests and non-secret environment configuration; changes in the generated area should be redone in declarations or templates.
5. Exercise database backup, fresh, and a formal snapshot clone-upgrade rehearsal.
6. Pass, in order, the backend, frontend, Compose, residual, login/menu/health, core-flow and E2E gates.
7. Switch the deployment and keep a compatibility rollback window of one release cycle.

## Version judgment

- `preset.version`: the module-composition and default-boundary version.
- catalog `version`: the module fact-structure version.
- `modules[].version`: the specific module implementation version.
- `templates.*`: the generated-file shape version.
- `source.version`: the Omni-Stack source version used for generation.

Any major template change or major preset change must be manually reviewed per the migration guide; handwritten files must not be overwritten automatically.

## Rollback

Application rollback uses the previous image/project and a compatible database structure. The database does not run unrehearsed down SQL; when a structure problem occurs, prefer appending a forward-fix changeSet. Before switching back to the old application you must confirm it can read the expanded structure.

If the newly generated project has not been deployed, simply discard the new directory — the original project is not modified by the generate command. If it has been deployed, keep the logs, migration report, `scaffold.lock` and failure evidence, complete root-cause analysis, then regenerate.

## Completion criteria

- The new lock file matches the actual modules, routes, permissions, database and Compose.
- Both fresh and clone-upgrade pass, and repeated migration and seed execution are idempotent.
- Removed modules leave no Maven, frontend, permission, DB, MQ or documentation residual.
- User core flows and role/tenant isolation have no regressions.
- README, dependency matrix and maintenance docs have been regenerated or synchronized from the new catalog.
