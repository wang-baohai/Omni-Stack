# Troubleshooting Handbook

Principle: identify the failing layer first, then locate the root cause with Trace ID, business ID and log evidence; do not mask root causes by repeated restarts or direct database edits.

## 1. Startup Failure

| Symptom | Inspect | Action |
|---|---|---|
| Maven plugin incompatibility | `java -version`, `JAVA_HOME` | Switch to JDK 25 and use `./mvnw` |
| Application waits for Nacos | Nacos health, 8848/9848, identity config | Restore Nacos first, then restart the target service |
| Migrator failure | Migrator logs, DATABASECHANGELOG | Fix the new changeSet or environment; never edit executed changeSets |
| MySQL connection refused | Container health, port, account, database | Confirm the target Compose project and connection string |
| Unselected dependency retries endlessly | CLI dev plan, profile | Use the correct preset or explicitly disable the optional integration |

## 2. Login Failure

- CAPTCHA empty or wrong: refresh the image and use the new `captchaKey`.
- User does not exist: confirm the tenant and the tenant-local username.
- Redirected back to login after login: check token lifetime, local clock, and Gateway identity headers.

Social callback failure: check the callback URI, PKCE, `state` and client configuration.
Portal 403: check the `SUPPLIER` role, the Portal association and the supplier status.

Authentication logs live in the login log; do not look for them in Auth operation logs.

## 3. Menu or Permission Errors

1. Use the API to inspect JWT authorities and the `/api/auth/menus` response.
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
