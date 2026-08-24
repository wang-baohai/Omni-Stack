# Custom Preset Tutorial

A custom preset combines catalog-known modules outside the five official choices. It uses the same Schema, dependency closure, conflict validation, atomic generation, and residual checks.

## Define the YAML

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: Supplier Workspace
description: Core platform, workflow, and supplier management.
modules: [srm, gateway, mysql, redis, nacos]
~~~

List entry modules only. `srm` resolves `workflow → base → auth → platform`; unrelated Procurement and Asset modules are not added.

## Validate, preview, and create

~~~powershell
Set-Location tools/omni-cli
npm run build
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset explain C:\WorkSpace\supplier-workspace.yaml
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app --dry-run
node dist/src/cli.js preset create C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
node dist/src/cli.js preset validate C:\WorkSpace\supplier-workspace.yaml --output C:\WorkSpace\supplier-app
~~~

Schema errors, unknown modules, and conflicts fail before any write. The output contains normalized `scaffold/presets/supplier-workspace.yaml` and `scaffold.lock`. Shared Workflow seeds are filtered so this example retains supplier onboarding but not procurement or asset approval models.

## Verify

Run backend `mvnw.cmd clean install`, frontend `npm ci`, lint/build, and `docker compose config --quiet`. Then use an isolated Compose project and volume for fresh migration, startup, and browser smoke tests.

Common mistakes include using Maven artifact names instead of catalog IDs, copying every transitive dependency, targeting a non-empty directory, manually deleting output modules, and assuming optional infrastructure is automatically disabled. Change the YAML or catalog and regenerate instead.
