# System Configuration, Security, and Audit Logs

This guide covers tenant configuration, XSS protection, sessions, login records, operation logs, and reliable-message records.

## 1. System Data

System Management includes tenants, organizations, users, roles, permissions, OAuth2 clients, online users, authorization records, audits, and XSS configuration. Base Data includes dictionaries. Confirm the current tenant and permission scope before making changes.

Dictionaries are suitable for stable display enums. APIs continue to carry stable values; translation must never change status codes, permission codes, or request parameters.

## 2. OAuth2 Clients

A client defines its ID, grants, redirect URIs, scopes, and consent behavior. Production clients must use HTTPS, exact redirect addresses, managed secrets, minimum grants/scopes, credential rotation, and periodic grant review.

## 3. XSS Protection

Every new service with `@RequestBody` implements the `XssConfigProvider` SPI. Shared components provide request filtering, Jackson string sanitization, and Gateway security headers.

Every XSS rule or toggle write immediately invalidates:

- `xss:enabled:{tenantId}`
- `xss:rules:{tenantId}`

Do not rely on TTL. After a rule change, test event attributes, script protocols, and intended rich-text boundaries.

## 4. Security Headers

Gateway responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Referrer-Policy`. These headers complement, rather than replace, sanitization, output encoding, CSP, and authorization.

## 5. Audit Sources

| Source | Location | Purpose |
|---|---|---|
| Login log | `sys_login_log` | Login result, source, and authentication method |
| Operation log | Administration → Operation Logs | Business writes, actor, result, and Trace ID |
| MQ records | Administration → MQ Messages | Outbox state, retries, dead letters, and correlation |

`omni-auth` does not use `@OperLog`; authentication is fully captured in login records. Business operation logs use reliable messaging. External queries are tenant-filtered, while the background relay intentionally scans all tenants.

## 6. Diagnostic Workflow

1. Obtain the Trace ID from the page or response header.
2. Confirm request, actor, and result in operation logs.
3. For asynchronous work, locate the message by key, topic, or Trace ID.
4. Inspect the synchronous trace in Tempo and producer/consumer logs in Loki.
5. Resend or skip a dead letter only after checking idempotency and downstream state.

## 7. Production Checklist

- Replace all development users, database passwords, Nacos identities, and internal shared tokens.
- Do not expose MySQL, Redis, Nacos, XXL-JOB, Prometheus, Grafana, Tempo, Loki, or management ports.
- Use least-privilege accounts and TLS.
- Define retention, masking, alert recipients, and audit export.
- Prove that backups restore successfully.
- Keep passwords, CAPTCHA answers, JWTs, internal tokens, and personal data out of screenshots and reports.

See [Reliable Messaging](../mq-reliability.en.md), [Observability](../observability.md), and [Docker Deployment](../docker-deployment.en.md).

