# Troubleshooting Handbook

Principle: identify the failing layer first, then locate the root cause with Trace ID, business ID and log evidence; do not mask root causes by repeated restarts or direct database edits. Run builds, checks and diagnostic programs in local Docker; use the host only for editing, read-only audit and Docker orchestration. Record HTTP status and response-body R.code separately; a business code is not automatically an HTTP status.

The four-language documents follow the [authorized AI review policy](../adr/0003-authorized-ai-translation-review.md): actual AI semantic approval is sufficient; human translation review is not required. The [current review scope](../reference/evidence/troubleshooting-guide-review-2026-10-17.json) covers documentation and current source without retriggering login, jobs, messages, cache refresh or log archival. Usable steps do not prove that a present failure was retested and resolved.

## 1. Startup Failure

| Symptom | Inspect | Action |
|---|---|---|
| Maven plugin incompatibility | `java -version`, `JAVA_HOME` | Switch to JDK 25 and use `./mvnw` |
| Application waits for Nacos | Nacos health, 8848/9848, identity config | Restore Nacos first, then restart the target service |
| Migrator failure | Migrator logs, DATABASECHANGELOG | Fix the new changeSet or environment; never edit executed changeSets |
| MySQL connection refused | Container health, port, account, database | Confirm the target Compose project and connection string |
| Unselected dependency retries endlessly | `dev status` / `dev doctor` (there is no `dev plan` subcommand; the plan is printed by `dev up` before Docker), profile | Use the correct preset or explicitly disable the optional integration |

## 2. Login Failure

- CAPTCHA empty or wrong: refresh the image and use the new `captchaKey`.
- User does not exist: confirm the tenant and the tenant-local username.
- Redirected back to login after login: check token lifetime, local clock, and Gateway identity headers.

- Social callback failure: check the callback URI, the backend HMAC-signed `state` and provider client configuration (external social login does not involve frontend PKCE); frontend PKCE (`code_verifier`/`code_challenge`) belongs to the local `/oauth2/authorize` SPA flow and is diagnosed separately.
- Portal 403: check the `SUPPLIER` role, the Portal association and the supplier status.

Use the login log for login events: it is a read-only projection of security-audit login events. Consult security-audit records for other authentication/security events. Auth does not record authentication activity in business operation logs.

## 3. Menu or Permission Errors

1. Privately inspect the JWT `scope` permission collection and the `/api/auth/menus` response; never paste the full JWT into logs or a support request.
2. Check permission seeds, role relations and organization scope.
3. Re-login after permission changes.
4. Call a write API directly with an account without permission and confirm the backend returns 403.
5. If only a frontend button misbehaves, verify `v-permission` matches the backend permission code exactly.

## 4. Data Invisible or Unauthorized

- Confirm the request goes through the Gateway, not a forged identity header directly to the business service.
- Check tenant ID, current user, organization and role scope.
- Check the domain DataPermission table and column mapping.
- Child tables must inherit through the aggregate root; never append predicates to nonexistent owner columns.
- Check that the DataPermission interceptor sits before Pagination.

## 5. Workflow

- Model cannot be saved: check the BPMN XML and designer state.
- Validation failure: fix process id, connections, candidate configuration and expressions per the validation list.
- Publish conflict: check concurrent operations on the same model and the current draft.
- Start failure: query the start-request reservation record and retry status by business key.
- Empty approvers: use candidate preview to check roles, anchors, organization scope and fallback strategy.
- Abnormal countersign result: check the `MI_END` historic task delete reason and the counter variable.

## 6. XXL-JOB

- System job invisible: check the `@XxlJob` and `@SystemJobMeta` dual annotations.
- Personal task without handler: the Bean name must equal `typeCode`.
- Creation leaves an orphan record: check the database delete compensation after registration failure.
- Immediate run without logs: check Admin, executor registration, handler exceptions and log persistence.

## 7. MQ and Cross-Service

1. Query the Outbox by message ID, Topic or business Key.
2. Check PENDING, FAILED, DEAD_LETTER status and the next retry time.
3. Check Broker delivery and the consumer Inbox.
4. Correlate producer traceId and consumer traceId in Loki/Tempo.
5. Resend manually only after confirming downstream idempotency.

Cross-tenant relay scanning is by design; external query APIs must still filter by tenant.

## 8. Frontend

- Blank page: check dynamic menu loading, route chunks, browser console and API 401/403/404.
- Dynamic form field type errors: check the JSON Schema `type`, `enum`, `options` and required.
- Language not persisted: check that `omni-lang` is `zh-CN`, `en-US`, `ja-JP` or `ko-KR`.
- Date/amount format errors: check that the current locale is used; API values must never change because of translation.
- Lint failure: never weaken rules; fix the source and keep `--max-warnings 0`.

## 9. What to Provide When Requesting Support

- Applicable version and commit ID.
- Target preset and Compose project name.
- Time range, Trace ID, business ID.
- Sanitized error responses and related service logs.
- Reproducible steps, the expected result, and the actual result.

Do not provide passwords, CAPTCHA answers, JWTs, internal tokens, private keys, or personal data that has not been sanitized.

## 10. Troubleshooting Moved from the Old Runtime Notes

These steps match current source. Old screenshots, deployment ports and historical acceptance do not prove the present failure is resolved. Run builds and diagnostic programs in local Docker; use the host for editing and Docker orchestration.

<!-- omni:modules:auth -->
| Symptom | Inspect first | Source and recovery boundary |
|---|---|---|
| CAPTCHA image missing | Auth health, its Redis connection and private password configuration | `CaptchaServiceImpl`; validation consumes the record once. Refresh after failure or expiry and ask a person to enter it; never replay an old code |
| Immediate 401 after login | Gateway access to Auth `/oauth2/jwks`, `kid`, signature, `exp` and clocks | `JwkKeyProvider` and Gateway verification. The password-login code fallback is 900 seconds, while repository `application.yml` configures 7200 seconds. Inspect deployment-overridden `auth.token.access-token-ttl` and actual `exp`; check other OAuth grants separately |
| Empty menu or hidden button | `scope`, `sys_role_permission`, `permission_code` and frontend `v-permission` | Re-login after permission changes. Verify hidden menus and API 403 separately; hardcoded routes cannot grant permission |
| Social callback failure | Exact provider URI match with `AUTH_OAUTH2_*_REDIRECT_URI`, server `state` signing configuration and container egress | Local callback enters Gateway 8102; internal Auth 8080 is not an external callback. See [Authentication](authentication.en.md) |
<!-- omni:modules:end -->

<!-- omni:modules:platform -->
| Symptom | Inspect first | Source and recovery boundary |
|---|---|---|
| Stale dictionary values after update | Tenant, `typeCode` and the tenant cache key in `DictDataServiceImpl` | Writes must invalidate immediately. With `dict:data:refresh`, call `POST /api/base/dict/data/refresh-cache?typeCode={code}` and re-read, not the obsolete PUT `/refresh`. The 30-minute TTL is not a correctness guarantee |
| Dictionary data remains after type deletion | `DictTypeServiceImpl.deleteType()` deletes the tenant's matching data, type and cache in one transaction | Reconcile only the returned ID and unique code. Inspect transaction and tenant on failure; do not manually delete shared data |
| Missing or delayed operation logs | Source Outbox, formal relay, Broker, `OperLogConsumer.operlogConsumer()` and Inbox deduplication | Confirm request commit first, then correlate producer/consumer traces. Async delay does not prove rollback; Auth login events remain a security-audit projection |
| Log archival does not run | Base executor registration, XXL execution record and the dual annotations on `OperLogArchiver` for handler `operLogArchiveHandler` | Reconcile one execution and its archive records. Use single-instance routing for multiple replicas; do not retrigger before determining the current result or archive shared logs for evidence |
<!-- omni:modules:end -->
