# Five-Minute Quick Start

Applies to: Omni-Stack 0.6.x

Use this guide to start a local development environment and sign in for the first time. For production, read the [Docker Deployment Guide](../docker-deployment.en.md) and [Operations, Backup, Recovery, and Upgrade Guide](operations-upgrade.en.md).

## 1. Prerequisites

- JDK 25.
- Node.js LTS 22.12.0 or later.
- Docker Desktop with Docker Compose.
- At least 12 GB of available memory is recommended. Prefer a project preset when evaluating one domain.

## 2. Choose a Startup Mode

Full system:

```bash
docker compose --profile full up -d
docker compose ps
```

CRM only:

```bash
npm --prefix tools/omni-cli run dev -- dev up --preset crm
```

Available presets include `core`, `crm`, `srm`, `procurement`, `asset`, and `supply-chain`. See [Preset Quick Selection](../preset-quick-selection.en.md). On first startup, Liquibase migrates the database before services start. Do not run legacy `init-all.sql` or `migrate-*.sql` files.

## 3. Verify Service Health

Wait until every selected application is `healthy` in `docker compose ps`.

| Entry | Address | Purpose |
|---|---|---|
| Frontend | `http://localhost:3000` | Login, workspace, and administration |
| Nacos | `http://localhost:8080` | Local discovery and configuration |
| Gateway | `http://localhost:8102` | Unified frontend API entry |

If a port is busy, use `docker compose ps` and `docker compose logs <service>` to identify the failed service. Do not repeatedly rebuild the database volume.

## 4. First Login

1. Open the frontend login page.
2. Select the development tenant.
3. Use the development administrator explicitly marked in `scripts/sql/seed/auth.sql`.
4. Enter the one-time CAPTCHA shown on the page.
5. Replace the development credential immediately after login. Never use seed credentials in shared, non-development, or production environments.

A CAPTCHA belongs to one request and can be used once. After refreshing it or after a failed submission, use the new image and `captchaKey`.

## 5. First Functional Check

An administrator should see System, Base Data, Scheduling, Monitoring, Workflow, and the business modules included by the selected preset. Check that:

1. System Management → Users loads a list.
2. Workflow → Process Models shows the seeded models.
3. The target domain overview loads real data or an explicit empty state.
4. My Workspace can open the Create Task dialog.

A missing menu usually means the role lacks permission or the preset omits the module. Do not fix it by hard-coding frontend routes.

## 6. Source Development

Backend:

```bash
cd omni-backend
./mvnw clean install
```

On Windows, set `JAVA_HOME` to JDK 25 first. Frontend:

```bash
cd omni-frontend
npm install
npm run lint
npm run build
npm run dev
```

## 7. Stop the Environment

```bash
docker compose --profile full down
```

Do not add `--volumes` by default. Delete a dedicated volume only when you intentionally need a fresh database and have verified the exact Compose project and volume name.

## 8. Next Steps

- [Authentication and Tenant Selection](authentication.en.md)
- [Menus, Roles, and Data Permissions](permissions.en.md)
- [Scaffold Development Tutorial](scaffold-development.en.md)
- [Troubleshooting](troubleshooting.en.md)

