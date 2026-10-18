# From cloning to first login

Baseline: 0.6.x from main fc165e3; this remediation branch adds documentation/tooling. The checked-out Compose, source and seeds define behavior. First-build time depends on network, CPU and caches; no five-minute promise.

## 1. Clone and understand the layout

Choose one hosting platform and clone into an empty location. Do not run both commands into the same directory. Run all following commands from the repository root.

GitHub:

```bash
git clone --branch main https://github.com/wang-baohai/Omni-Stack.git
cd Omni-Stack
```

Gitee:

```bash
git clone --branch main https://gitee.com/wang-baohai/Omni-Stack.git
cd Omni-Stack
```

`omni-backend/` contains Maven modules; `omni-frontend/` contains Vue source. `docker/` and Compose build and run containers. `database/` holds schema migrations, `scripts/sql/seed/` holds official bootstrap data, and `tools/omni-cli/` plus `scaffold/` generate projects. `docs/` holds engineering references and Wiki content; `ai/` holds shared AI assets.

## 2. Prepare Docker and the tool container

The host needs Git, Docker Desktop/Engine with Compose, and an editor. Builds and verification do not require host JDK/Node: tool containers provide JDK 25, Node 22 and Maven Wrapper. Use the local Linux engine. Allocate Docker resources from the [preset matrix](../preset-dependency-matrix.en.md). Start planning full-stack memory around 12–16 GiB and spare disk around 20 GiB, then adjust for actual images and build peaks. These are preparation budgets, not measured limits.

```bash
docker version
docker compose version
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
```

Expected: both Docker client and server respond, Compose reports a version, and the tool image builds. Registry/Maven/npm timeouts require proxy, DNS and Docker-network checks. Handle local permission failures through platform authorization; they do not establish that the daemon is stopped.

## 3. Create private configuration for a fresh environment

The container script securely derives all required secrets from `.env.example`. It creates a new `.env`, never overwrites an existing file and does not print values. Existing data volumes require their original credentials and the [upgrade/recovery path](operations-upgrade.en.md); do not regenerate passwords to adopt an old database.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node scripts/init-env.mjs
```

Expected output only confirms creation. Git ignores the real `.env`. All four languages use the same variable names.

| Variables | Purpose and constraints |
|---|---|
| `MYSQL_ROOT_PASSWORD` / `OMNI_DB_PASSWORD` | Migration administrator / application user for seven databases; migrator creates and grants on fresh startup |
| `REDIS_PASSWORD` | Must match Redis and every client |
| `NACOS_AUTH_TOKEN` | Base64 of at least 32 random bytes for server signing |
| `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE` | Nacos server identity authentication |
| `NACOS_PASSWORD` | Same console/client password; default username nacos |
| `XXL_JOB_ACCESS_TOKEN` / `XXL_JOB_ADMIN_PASSWORD` | Executor token / random console password; default admin username, 14–20 password characters |
| `GATEWAY_ASSERTION_SECRET` / `OMNI_INTERNAL_API_TOKEN` | Shared gateway HMAC secret (at least 32 bytes) / internal API authentication |
| `OAUTH2_STATE_SECRET` / `JWK_ENCRYPT_KEY` | Social state HMAC (at least 32 bytes) / Redis JWK encryption key (exactly 32 characters) |

GitHub, Google and Gitee OAuth client IDs/secrets are optional external app credentials. Password login does not need them. Empty credentials do not supply working external social login.

## 4. Select and start

Use full for the complete supply chain or all capabilities. First `--build` downloads dependencies and builds inside images. The migrator must exit successfully before health-based service startup.

```bash
docker compose --profile full up -d --build
docker compose ps -a
docker compose logs omni-db-migrator
```

Use core to learn login, permissions and the base platform. The CLI prints the module closure, services and degraded capabilities. Core lite disables Nacos clients, XXL-JOB and MQ relay; it cannot validate the supply chain.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset list
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- preset explain core
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm run dev -- dev up --preset core --project-name omni-stack --build
```

This starts selected services. To generate a pruned project, read [scaffold development](scaffold-development.en.md): `preset create` and `dev up` produce different results.

## 5. Confirm migration and health

`omni-db-migrator` must show Exited (0). Selected containers with a declared healthcheck must become healthy, and the frontend must be reachable; omitted services need not appear. Investigate the migrator or failed service, required variables and ports before retrying. Do not delete data volumes to mask a failure. The standard project is omni-stack with fixed defaults: frontend 3000, Gateway 8102 and MySQL 3306.

```bash
docker compose ps -a
docker compose logs --tail 100 omni-auth omni-gateway omni-frontend
```

## 6. First login and one-time CAPTCHA

1. Open `http://localhost:3000` in the browser.
2. Select the development tenant and use the local demonstration identity `admin` / `admin123` from official `auth.sql`.
3. A person enters the current image CAPTCHA; refreshing creates a new captchaKey.
4. Successful login opens the workspace. Open the console and check System Management → Users.
5. Use demonstration credentials only in an isolated development environment; replace them before shared or production use.

After a wrong CAPTCHA or failed submission, request a new image: Redis GETDEL has consumed the old value. Diagnose wrong tenants, locked accounts and 503 through the [authentication guide](authentication.en.md). Never replay the old captchaKey. A short-lived fixture token only tests authenticated functionality and cannot replace this step.

This image illustrates the localized form layout with CAPTCHA masked. A human-entered one-time CAPTCHA completed the real first login on 2026-10-09 with HTTP/business 200; see the [sanitized login receipt](../reference/evidence/human-login-2026-10-09.json). Other localized page tests use short-lived test identities and do not prove four password logins.

![Login entry: tenant, account, password and CAPTCHA](../images/en-US/auth-login.png)

## 7. Check the preset, stop and develop

Admins see selected modules. Core omits workflow and supply-chain menus; ordinary users are further limited by roles. Empty lists may be expected: bootstrap seeds do not contain historical orders, approvals or assets. Create your own teaching records using [data and fixtures](../wiki/data-and-fixtures.en.md).

Default down retains named volumes and a new up reuses data. Omit `--volumes`. After source changes, test in containers and rebuild the relevant images:

```bash
docker compose --profile full down
docker compose --profile full up -d
```

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
docker compose --profile full up -d --build
```

Next: [Authentication and tenants](authentication.en.md) · [Permissions and scope](permissions.en.md) · [Services and pruning](scaffold-development.en.md) · [Troubleshooting](troubleshooting.en.md)
