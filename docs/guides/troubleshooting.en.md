# Troubleshooting Handbook

Identify the failing layer first, then correlate Trace ID, business ID, and logs. Repeated restarts and direct database edits hide rather than solve root causes.

## 1. Startup

| Symptom | Inspect | Action |
|---|---|---|
| Maven plugin incompatibility | `java -version`, `JAVA_HOME` | Select JDK 25 and use `./mvnw` |
| Waiting for Nacos | Health, 8848/9848, identity | Restore Nacos, then restart the target |
| Migrator failure | Logs and DATABASECHANGELOG | Fix the new changeSet/environment; never edit executed history |
| MySQL refusal | Container health, port, user, database | Confirm the target Compose project and URL |
| Unselected dependency retries | CLI plan and profile | Select the right preset or disable the optional integration |

## 2. Authentication

Refresh CAPTCHA and use the new key; verify tenant and tenant-local username. For redirect loops, inspect token lifetime, clock, and Gateway identity. For social login, inspect callback URI, PKCE, state, and client. For Portal 403, inspect supplier role, association, and state.

## 3. Menus and Permissions

Inspect JWT authorities and `/api/auth/menus`, seed and role relations, re-login after changes, and test a forbidden write directly. Compare `v-permission` with backend authority exactly.

## 4. Data Scope

Confirm requests pass Gateway, then inspect tenant, user, unit, role, domain table/column mapping, child inheritance, and interceptor order. Never append owner predicates to a child table without those columns.

## 5. Workflow

Validate BPMN and assignments before publishing. Correlate a failed start by business ID and reservation record. Use candidate preview for role/anchor/scope issues. For countersign, inspect `MI_END` and approval counters.

## 6. XXL-JOB

System jobs need both annotations. Personal handler bean name equals `typeCode`. Registration failure must remove the database row. For a trigger without logs, inspect Admin, executor, handler, and log persistence.

## 7. Messaging

Follow Outbox → Broker → Inbox by message ID, topic/key, producer trace, and consumer trace. Inspect retry state and resend a dead letter only after confirming downstream idempotency. Cross-tenant relay scanning is intentional; external queries remain tenant-filtered.

## 8. Frontend

For blank pages, inspect menu loading, route chunks, and 401/403/404. For dynamic forms, inspect Schema type/enum/options/required. `omni-lang` must be one of four supported locales. Formatting uses the active locale without changing API values. Fix lint findings without weakening `--max-warnings 0`.

## 9. Support Evidence

Provide version/commit, preset, Compose project, time range, Trace/business IDs, masked response/logs, reproduction, expected result, and actual result. Never provide passwords, CAPTCHA answers, JWTs, internal tokens, private keys, or unmasked personal data.

