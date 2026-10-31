# 7. Permissions, logout, and troubleshooting: checks after token issuance

Li being able to sign in does not open every approval, cross-department record, or supplier portal function. This page puts credentials, business permissions, data scope, and logout state together so you can investigate the right layer.

## What still happens to a token-bearing request?

| Stage | Question | Project behavior |
|---|---|---|
| Gateway credential validation | Is this credential trusted and still valid? | Checks signature, expiry, and token-ID blacklist |
| Internal identity propagation | Who confirmed the downstream identity? | Removes client identity headers and supplies verified identity plus a signed gateway assertion |
| Server operation authorization | May this person perform this action? | Controller permissions or dedicated ownership checks |
| Data scope | Which records may this person access? | Uses role, organization, self scope, and domain aggregate relationships |
| Business rules | Is this operation valid for this record now? | Requires valid state, version, and other business conditions |

Even after the gateway identifies Li, an approval API may deny access to another person's record. A manager's action permission still needs record-scope restrictions. Menus and buttons belong to the UI; they do not replace these checks.

A client-written identity header cannot choose the client's tenant or administrator identity. The gateway handles incoming identity headers, and downstream services validate its assertion rather than trusting arbitrary external text.

## Token fields useful for investigation

Ordinary password JWTs use RS256 signatures. Common fields have separate purposes:

| Field | Meaning |
|---|---|
| sub | Current subject identifier |
| tenant_id | Tenant belonging to this identity claim |
| username | Username |
| roles | Role information |
| scope | Permissions written at issuance |
| jti | This token's identifier, used for distinction and revocation checks |
| iat, exp | Issuance and expiry times |

Permissions are an issuance-time snapshot. Changing roles does not automatically replace the token already held by the browser. Check both the new authorization and the current credential.

## Why signature verification needs no private key

The issuer signs with a private key; verifiers check with a public key. Publishing the public key does not enable issuing credentials. Reading a token's fields does not mean an edited token still passes verification. Do not put passwords or other secrets inside tokens.

The gateway's JwkKeyProvider obtains and caches an RSA public key from Auth's JWKS endpoint. This continuous source chooses between cache and retrieval:

<!-- omni:source path="omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/JwkKeyProvider.java" -->
```java
    public Mono<RSAPublicKey> getPublicKey() {
        // 缓存命中且未过期，直接返回（快速路径）
        if (cachedPublicKey != null && !isExpired()) {
            return Mono.just(cachedPublicKey);
        }
        // 缓存未命中或已过期，发起远程请求
        return fetchJwkSet();
    }
```

A valid cache is returned immediately; otherwise it fetches the key set. Configuration controls the cache duration. Signature validation on each request does not imply a new network download every time.

This excerpt does not prove seamless handling of every key-rotation scenario. Check addresses, caching, signing keys, and old-token compatibility against configuration and tests.

After validation, the gateway forwards X-User-Id, X-Tenant-Id, X-User-Name, roles/permissions, and X-Gateway-Assertion, and removes the incoming Bearer. Business services validate the assertion through their pre-authentication rules before continuing.

Do not paste full tokens, passwords, or Cookies into issue reports, tutorial images, or public decoder sites. Start with time, entry, error text, and redacted request correlation data.

## Local logout, administrative kick, and protocol revocation differ

Project-menu logout clears frontend token/permission state and dynamic routes. It does not call a project /api/auth/logout to immediately invalidate all ordinary JWTs already issued. “My browser shows login again” and “the server revoked that old credential” are separate facts.

After authorization checks, an administrator kick removes the corresponding online record and blacklists that session's jti for 24 hours. Protected requests using that credential encounter the blacklist check. This does not revoke every credential for that user across all devices and systems.

Spring's authorization server also supports OAuth2/OIDC protocol revocation and logout with its own authorization-record and session boundaries. Those endpoints do not constitute global logout for ordinary password JWTs.

## Why the supplier portal has another check

The portal reuses local password authentication. Passing it proves only that the account's credentials can sign in.

Profile, evaluation, and quotation access additionally need SUPPLIER and an active supplier-portal association. An administrator does not automatically become a supplier. Check roles, associations, and business state separately.

Self-registration is separate too: success does not sign in or grant management access. Creation paths attempt the default USER role, but failures only log warnings under the current design. “User created” does not prove all authorization is ready.

## Current limits

- The first-party account/password API is not OAuth2 password grant; that grant is not registered as a supported project login method.
- The frontend type has an optional refresh_token field but stores only the access token, without automatic renewal. Registered grants and implemented renewal are different.
- Portal login reuses the existing password entry, without a separate portal credential protocol.
- Actual social providers are GitHub, Google, and Gitee. There is no WeChat frontend entry or backend adapter.
- Source excerpts, runtime tests, controlled protocol tests, and real external-account authorization are recorded separately. None may impersonate another. Real third-party account integration remains separately unverified.

The device page lists polling and rejection-button limits. Understand them before integrating a real device; a “Success” label is not evidence of business access.

## Investigate where the flow stopped

| Symptom | Check first | Continue |
|---|---|---|
| Ordinary login fails | Company availability, consumed CAPTCHA, account lock, password | Obtain fresh CAPTCHA or handle account state; do not replay old material |
| Enterprise SSO returns to login | Established session, current state/original, callback and client match | Restart from the entry; do not reuse cleared exchange material |
| Device waits indefinitely | User confirmation, expiry, pending versus permanent error | Follow the page and protocol error; waiting is not success |
| Social login fails | Provider denial, complete callback, valid state, binding collision | Restart from that provider; keep redacted errors |
| No business entry after login | Role, permissions, updated token, applicable target | Check authorization and credentials; do not share an administrator account |
| Permission exists but a record is absent | Tenant, organization, self scope, ownership, business state | Check domain scope rules; do not forge identity headers |
| Portal still denies after login | SUPPLIER, active portal association, supplier state | Check enrollment and association; do not substitute an administrator |

Ordinary login's default site entry, the authorization-code callback's console, and the portal use different frontend branches. Identify the path and the target it saves/consumes before troubleshooting redirects.

For authentication changes, return to the relevant practice page. For why an identity sees only its own company or department, continue to the multi-tenancy chapter.
