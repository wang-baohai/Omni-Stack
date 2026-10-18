# Custom Preset Tutorial

A custom preset is suitable for combining catalog-known modules beyond the five official presets. It uses the same Schema, dependency closure, conflict check, atomic generation and residual check as the official presets.

## 1. Write the YAML

For example, if you only need supplier and workflow capabilities:

~~~yaml
id: supplier-workspace
version: "1.0.0"
displayName: Supplier Workspace
description: Core platform, workflow, and supplier management.
modules: [srm, gateway, mysql, redis, nacos]
~~~

`modules` lists only entry modules. `srm` automatically pulls in `workflow → base → auth → platform`; the generator does not automatically add Procurement or Asset, which are unrelated to the dependencies.

## 2. Validate and explain

Save the YAML above as scaffold/presets/supplier-workspace.yaml in the source repository. Run the following commands at its root; CLI builds and execution use local Docker tooling.

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset explain scaffold/presets/supplier-workspace.yaml
~~~

Unknown modules, Schema errors or conflicts fail before any file is written.

## 3. Preview and generate

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app --dry-run
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset create scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node dist/src/cli.js preset validate scaffold/presets/supplier-workspace.yaml --output generated/supplier-app
~~~

The generated project saves the normalized `scaffold/presets/supplier-workspace.yaml` and `scaffold.lock`. In the shared Workflow service, the procurement and asset default processes are pruned by the combination, keeping only the supplier admission process.

## 4. Verify the generated project

`docker compose config`/up reads the project's `.env`; without it they fail with "required variable is missing" (exit 1). First copy the generated project's bundled `.env.example` and fill in **self-generated random secrets** (≥ 32 bytes; `JWK_ENCRYPT_KEY` must be 32 chars; never reuse repo placeholder defaults or real shared secrets). For parallel isolation set a distinct project/ports/volume/image namespace:

NACOS_AUTH_TOKEN must be the Base64 encoding of at least 32 random bytes. Use nacos as NACOS_USERNAME and keep NACOS_PASSWORD consistent across the server and all clients.

~~~powershell
Set-Location generated/supplier-app
Copy-Item .env.example .env   # required: OMNI_INTERNAL_API_TOKEN / GATEWAY_ASSERTION_SECRET / MYSQL_ROOT_PASSWORD / OMNI_DB_PASSWORD / REDIS_PASSWORD / XXL_JOB_ACCESS_TOKEN / XXL_JOB_ADMIN_USERNAME / XXL_JOB_ADMIN_PASSWORD / NACOS_PASSWORD / NACOS_AUTH_TOKEN / NACOS_AUTH_IDENTITY_KEY / NACOS_AUTH_IDENTITY_VALUE / OAUTH2_STATE_SECRET / JWK_ENCRYPT_KEY; isolation: COMPOSE_PROJECT_NAME(or -p), OMNI_MYSQL_VOLUME_NAME, OMNI_*_HOST_PORT, OMNI_IMAGE_NAMESPACE
~~~

Then build the backend/frontend and validate the Compose config:

~~~bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose config --quiet
~~~

Then use an isolated Compose project/volume to run a fresh database, startup and browser smoke tests. Do not share the database volume with the existing development stack.

## Common mistakes

- Writing `omni-*` artifact names into `modules`: you must use the catalog module IDs here.
- Explicitly copying all transitive dependencies: unnecessary, and it adds maintenance noise.
- Overwriting the source repository root itself or a non-empty directory: the generator rejects it.
- Manually deleting modules after generation: this distorts `scaffold.lock`, seed digests and routes; modify the YAML and regenerate instead.
- Assuming optional infrastructure is automatically disabled: you still need to check the corresponding runtime switches after generation.
- Including RocketMQ but omitting `srm`: `auth`'s portal-role consumer subscribes to `srm-domain-event`, so the generator blocks writing on a dangling consumer; any MQ-enabled combination must include `srm` as well. Without RocketMQ this constraint does not apply (e.g. a pruned combination of just `auth`, `workflow`, `crm` generates fine).
