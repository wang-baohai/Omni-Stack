# Login, CAPTCHA, Social Login, and Tenant Selection

See the [2026-10-12 basic receipt](../reference/evidence/basic-2026-10-12.json): 44 unique checks and 44 individually reviewed originals, with exact source and image identities for each capture epoch. Readable pages do not prove every business operation in this chapter. Some Japanese/Korean Status, Phone, Email and Retry labels still fall back to English; data names retain the teaching dataset.

Audience: end users, tenant administrators, and identity maintainers.

## 1. Entry Points and Tenant Selection

Administrators and regular users sign in at `/login`; suppliers may use `/portal-login`. A username is unique only within a tenant, so password login begins with tenant selection. The backend builds the authentication context from tenant ID, username, and login method.

The public tenant endpoint returns only the minimum login information. After login, tenant identity comes from the issued token and trusted Gateway headers; clients cannot expand scope by constructing their own tenant header.

## 2. Password and CAPTCHA

1. Request `GET /api/auth/captcha`.
2. Display `captchaImage` and keep the matching `captchaKey` in memory.
3. Collect tenant, username, password, and `captchaCode`.
4. Call `POST /api/auth/login`.
5. Discard the password, CAPTCHA answer, and key immediately after success.

Rules:

- CAPTCHA values are single-use. Never cache answers or reuse keys.
- Frontend logs, screenshots, and reports must not contain passwords, CAPTCHA answers, or access tokens.
- Automation uses a short-lived token injected by a trusted environment or a human-handled real CAPTCHA. It must not crack, disable, or bypass production CAPTCHA.
- Failure messages must remain actionable without enabling account enumeration.

## 3. Self-Registration

`POST /api/auth/register` is public but validates tenant, CAPTCHA, tenant-local username uniqueness, and password rules. Every creation path attempts to assign the default `USER` role. A role-assignment failure is logged without leaving an unusable partial user transaction.

Supplier registration creates only an authentication account. The user must then enroll with a supplier invitation, receive the `SUPPLIER` role, and obtain an active Portal association before accessing company profile, evaluation, and quotation features.

## 4. Social Login

GitHub, Google, and Gitee social login is a **different path** from the local OAuth2 authorization-code + PKCE flow where Omni-Stack itself is the authorization server and the SPA is a public client — do not conflate them:

**External social login (backend-led):**

1. The backend `SocialLoginController` generates a `state` carrying tenant info (server-side HMAC-signed) and 302-redirects to the external provider's authorization page.
2. The provider calls back to `/api/auth/oauth2/{provider}/callback`; after verifying `state`, `SocialLoginServiceImpl` exchanges the code for an access token **server-side** and fetches the profile (the frontend never touches the provider `client_secret` and does not generate PKCE).
3. First login creates a provider-prefixed local name such as `gh_`, `go_`, or `ge_`, with a fallback on collision.
4. The user returns to the protected page or role-appropriate workspace.

**Local OAuth2 PKCE (a separate flow):** the frontend `buildAuthorizeUrl` generates `code_verifier`/`code_challenge` and redirects to `/oauth2/authorize`, then exchanges the code at `/oauth2/token` using `code_verifier`. This PKCE/`state` belongs to the local authorization server and SPA client, distinct from the external social `state`/callback handling above.

Callback addresses, client secrets, and allowed origins come from environment configuration. Production must not reuse repository development clients.

### Local app configuration and tool verification

Fill the following variables in Git-ignored `.env` or a private environment file outside the repository. Use your own application credentials; never write real values into the Wiki.

| Provider | Client ID | Client Secret |
|---|---|---|
| GitHub | `GITHUB_CLIENT_ID` | `GITHUB_CLIENT_SECRET` |
| Google | `GOOGLE_CLIENT_ID` | `GOOGLE_CLIENT_SECRET` |
| Gitee | `GITEE_CLIENT_ID` | `GITEE_CLIENT_SECRET` |

Compose currently sets the three `AUTH_OAUTH2_*_REDIRECT_URI` values directly to Gateway callbacks on localhost:8102. Adding identically named keys to `.env` alone does not override these fixed environment values. To change the domain or use 127.0.0.1, override `omni-auth.environment` in a private Compose file and register exactly matching provider URIs. `AUTH_OAUTH2_FRONTEND_CALLBACK_URL` must match the browser's frontend address, for example `http://localhost:3000/callback`. An application's Client Secret is not the platform account's login password.

The current `OAuth2StateUtils` validates HMAC and a 10-minute time window. It does not store or atomically consume a one-time nonce. Repeating a valid state in this run still reached provider code exchange, so these negative tests do not establish single-use state consumption.

The [2026-10-13 local tool receipt](../reference/evidence/oauth-local-2026-10-13.json) records real provider pages, configuration binding and rejected callback branches. HTTP 200 on a login page and rejection of invalid codes do not prove user authorization, successful token exchange or completed local social login.

### External OAuth screenshots pending

The account holder must complete secondary sign-in verification for GitHub, Google and Gitee. For this delivery, the project owner has deferred capture and reserved the three regions below. Successful callbacks, code exchange and local login after real user authorization remain unverified for all three providers. These placeholders do not indicate a passed verification.

#### GitHub

> **Screenshot pending**: GitHub secondary sign-in verification, authorization confirmation, and the login result after returning to this system.

#### Google

> **Screenshot pending**: Google secondary sign-in verification, authorization confirmation, and the login result after returning to this system.

#### Gitee

> **Screenshot pending**: Gitee secondary sign-in verification, authorization confirmation, and the login result after returning to this system.

After completing actual verification and authorization in a local Docker browser, replace each region with a redacted original and register its language, role, tenant, source version and actual verification scope in the screenshot manifest. Exclude passwords, verification codes, tokens, Cookies and personal account information. This deferral covers only screenshots of these three external flows. Password/CAPTCHA, local PKCE, device authorization and SRM registration retain their respective acceptance scopes.

## 5. Device Authorization

`/device` obtains a device code and presents a user code and QR code. The user signs in and confirms at `/device/verify`; the device only polls the token endpoint and never handles the password. An expired, rejected, or consumed code requires a new request.

Device authorization is for input-constrained devices, not a general CAPTCHA bypass.

### Screenshot

#### Figure 1 `auth-login-en-US`: Login entry

- Prerequisites: public, no login required
- Actor: any user
- Action: open `/login`
- Expected result: tenant, username, password, CAPTCHA (masked), and sign-in methods are visible

![Login entry](../images/en-US/auth-login.png)

#### Figure 2 `auth-register-en-US`: Self-registration

- Prerequisites: public
- Actor: new user
- Action: open the register page from login
- Expected result: username, password, confirm password, and CAPTCHA inputs are visible

![Self-registration](../images/en-US/auth-register.png)

#### Figure 3 `auth-device-code-en-US`: Device authorization

- Prerequisites: a headless device starts the OAuth2 device flow
- Actor: device user
- Action: open `/device` for the user code, then enter it at `/device/verify`
- Expected result: after the real device authorization request succeeds, the user code, QR code, live countdown based on the server expiry, and confirmation entry appear; public images mask active codes and links

![Device authorization](../images/en-US/auth-device-code.png)

#### Figure 4 `supplier-portal-login-en-US`: Supplier portal login

- Prerequisites: public
- Actor: supplier user
- Action: open the supplier portal login page
- Expected result: the portal login form and registration entry are visible

![Supplier portal login](../images/en-US/supplier-portal-login.png)

#### Figure 5 `session-expired-dialog-en-US`: Session expired dialog

- Precondition: Logged in; a deterministic 401 fault is injected into a business API within the test process (in real usage this is Token expiry)
- Operator: Logged-in user
- Action: When a business API returns 401, the app pops up the expiry dialog
- Expected result: A "Session expired" dialog appears (localized title + re-login button); after confirmation it redirects to the login page with a validated `redirect` parameter; no internal exception is leaked

![Session expired dialog](../images/en-US/session-expired-dialog.png)

## 6. Expired Sessions

When a token expires or permissions change, the client clears local authentication, returns to login, and preserves only a validated in-app redirect. `javascript:`, cross-origin, and protocol-relative redirect targets are rejected. A failed menu request displays a retryable error page instead of an infinite request loop.

## 7. Troubleshooting Order

1. Verify the tenant.
2. Refresh CAPTCHA and enter the new value.
3. Inspect Auth login records; `omni-auth` does not replace them with operation logs.
4. For social login, inspect callback URI, the backend HMAC-signed `state`, and the provider client configuration (external social login does not involve frontend PKCE); the frontend PKCE (`code_verifier`/`code_challenge`) belongs to the local `/oauth2/authorize` SPA flow and is diagnosed separately.
5. For Portal access, inspect the `SUPPLIER` role and `srm_supplier_portal_user` association.

See the [API Contract](../api-contract.en.md) and [Core Flows](../core-flows.en.md).

## 8. Docker Callbacks and Actual Acceptance Scope

1. The provider's allowed callback must exactly match `AUTH_OAUTH2_GITHUB_REDIRECT_URI`, `AUTH_OAUTH2_GOOGLE_REDIRECT_URI` or `AUTH_OAUTH2_GITEE_REDIRECT_URI` in `compose.apps.yaml`. Local defaults use Gateway `http://localhost:8102/api/auth/oauth2/{provider}/callback`; production uses a browser-reachable HTTPS domain. Internal `omni-auth:8080` is not an external callback.
2. Vue Router consumes the result at `/callback`; failure returns to login. Nginx falls back to `index.html` for SPA routes and forwards `/api/*` and `/oauth2/*` to Gateway, which discovers Auth. Keep token fragments, Cookies, client secrets and active CAPTCHA values out of public logs and screenshots.
3. For missing CAPTCHA, check Auth-to-Redis connectivity. Refresh a failed or expired challenge and ask a person to enter the new code; never replay it. Check the tenant for password failures. For actual 401, inspect Gateway access to `/oauth2/jwks`, signature, expiry and session. Account throttling and session expiry are separate cases.
4. For `state` failure, check server signing configuration and callback parameters. Provider timeouts require container outbound-network and proxy checks. Username collisions use the `gh_`/`go_`/`ge_` fallback. External provider and local PKCE code exchange use different configuration.
5. The [real login receipt](../reference/evidence/human-login-2026-10-09.json) proves one human CAPTCHA/password login. Registration, device initialization, Portal entry and session-error images have separate scopes. Test identities, client-injected 401, unconfigured providers and page visits do not prove successful registration, completed device authorization or a complete external social login.
