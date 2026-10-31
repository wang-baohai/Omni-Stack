# 3. Framework and source: turning a protocol into checks

The previous page explained authorization codes and PKCE. Now open the framework: who receives a request, how the server compares exchange material, and why a code cannot be used repeatedly.

Spring Boot 4.0.6 in this project manages Spring Security 7.0.5, including the authorization-server artifact. The excerpts below are continuous original text from that version. Read the surrounding explanation first; there is no need to memorize class names.

## Why use a framework rather than write the protocol in controllers?

A login form and a maintained authorization protocol are different amounts of work. The latter also needs client registration, callback restrictions, codes, exchange parameters, token formats, and error handling. Each condition affects how applications integrate.

The current source supports these trade-offs:

| Approach | What must be maintained | Relationship to this project |
|---|---|---|
| Write protocol endpoints directly | Protocol rules, security checks, compatibility, and tests | The local authorization-code protocol is not rewritten this way |
| Use security and authorization-server frameworks inside Spring | Reuse protocol handlers; maintain clients, login integration, keys, and business permissions | The project uses Spring Security and its authorization-server module |
| Centralize identity in a separate platform | Operate that platform and map it to business accounts, tenants, and roles | The standard local environment does not replace the built-in Auth service this way |

This explains choices supported by the source; it does not invent a historical review meeting. The project already uses Spring Boot and needs its own user, tenant, and permission model. Integrating protocol endpoints with Spring security avoids duplicate implementation, while business identity and permissions remain the project's responsibility.

Spring Security supplies filter chains and authentication mechanisms. Its authorization-server module uses them for OAuth2/OIDC. It is not the role table and does not decide whether Li may approve a particular requisition.

## Excerpt one: parse the request, then delegate processing

The browser sends parameters. The framework turns them into an internal object and delegates to the authentication manager. This continuous excerpt is from the authorization endpoint filter.

Original: OAuth2AuthorizationEndpointFilter.java, lines 193–201, Spring Security 7.0.5.

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-request-dispatch.java.txt" -->
```java
			Authentication authentication = (Authentication) request
				.getAttribute(OAuth2AuthorizationCodeRequestAuthenticationToken.class.getName());
			if (authentication == null) {
				authentication = this.authenticationConverter.convert(request);
				if (authentication instanceof AbstractAuthenticationToken authenticationToken) {
					authenticationToken.setDetails(this.authenticationDetailsSource.buildDetails(request));
				}
			}
			Authentication authenticationResult = this.authenticationManager.authenticate(authentication);
```

The first lines look for a request object prepared by an earlier check. Otherwise, converter parses the current request. Request details are then attached before authenticationManager continues processing.

Authentication is a common framework object type. Its variable name alone does not prove that the user's password has already been accepted. Parsing and authentication are separate phases; the appropriate handler must still check the client, parameters, current user, and other conditions.

In the project, a dedicated security chain first matches authorization-server endpoints, then these handlers run. Ordinary password login uses a project controller, explained separately.

## Excerpt two: match the PKCE original to its digest

A public client cannot safely hide a long-lived password in browser code. During authorization-code exchange, the framework's public-client provider calls codeVerifierAuthenticator.authenticateRequired. The following method compares PKCE material.

Original: CodeVerifierAuthenticator.java, lines 151–170, Spring Security 7.0.5.

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/pkce-s256.java.txt" -->
```java
	private boolean codeVerifierValid(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
		if (!StringUtils.hasText(codeVerifier)) {
			return false;
		}
		else if ("S256".equals(codeChallengeMethod)) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
				String encodedVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
				return encodedVerifier.equals(codeChallenge);
			}
			catch (NoSuchAlgorithmException ex) {
				// It is unlikely that SHA-256 is not available on the server. If it is
				// not available,
				// there will likely be bigger issues as well. We default to SERVER_ERROR.
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
			}
		}
		return false;
	}
```

No original means failure. In the S256 branch, the server hashes it with SHA-256, applies unpadded base64url encoding, and compares it with the previously supplied digest. Matching content returns true.

Unrecognized methods return false here. If the required digest algorithm is unavailable, the exception branch reports a server error rather than accepting the request.

The default project browser client requires PKCE. The frontend creates random material and uses Web Crypto to compute the same digest, keeping the original for the callback exchange. Both sides must agree on that calculation; renaming a parameter cannot replace the comparison.

PKCE does not turn a browser into a server capable of protecting a permanent secret. Compromised page scripts still require separate defenses, alongside callback restrictions and other checks.

## Excerpt three: invalidate the code after a normal exchange

After generating tokens, the framework records that the code has been used. This is the normal success path, not a rejection branch for an incorrect client.

Original: OAuth2AuthorizationCodeAuthenticationProvider.java, lines 294–299, Spring Security 7.0.5.

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-code-consumption.java.txt" -->
```java
		// Invalidate the authorization code as it can only be used once
		authorizationBuilder.invalidate(authorizationCode.getToken());

		authorization = authorizationBuilder.build();

		this.authorizationService.save(authorization);
```

The first statement invalidates the code; the following statements build and save the updated authorization record. The code is not a permanent password for repeated exchanges. Other framework checks reject invalid or reused codes. Restart from the application entry instead of repeatedly submitting an old exchange.

Access-token lifetime and authorization-code consumption are separate questions. This excerpt does not revoke every JWT in the project simultaneously. The final page explains tokens, logout, and business permissions.

## Divide framework and project responsibilities

| Responsibility | Owner here | Continue with |
|---|---|---|
| Parse OAuth2 requests, validate exchange material, handle codes | Spring authorization-server handlers | The three excerpts above |
| Validate local accounts and CAPTCHA | Project Auth controller and services | [Ordinary password login](authentication-password.en.md) |
| Establish browser sessions, save PKCE material, handle callbacks | Project security configuration and frontend | [Enterprise SSO](authentication-oauth2.en.md) |
| Connect devices, external accounts, registration, and portal entry | Project entries and adapters | [Device and social login](authentication-device-social.en.md) |
| Menus, API permissions, data scope, and logout state | Gateway, business services, and frontend rules | [Permissions and troubleshooting](authentication-security.en.md) |

A framework reduces protocol maintenance, but clients, sessions, and key storage still need correct project configuration. Declaring an endpoint in source does not prove real third-party account authorization.

## Find request addresses by responsibility

Understand the responsibilities first, then use this index. It reflects current framework defaults and project entries, not an endpoint-by-endpoint runtime acceptance record.

| Address | Main responsibility |
|---|---|
| /api/auth/login | Ordinary project password login; returns a local login result |
| /api/auth/session-login | Establishes current user state for the authorization server |
| /oauth2/authorize | Handles authorization requests and code issuance |
| /oauth2/token | Exchanges tokens according to the applicable grant |
| /oauth2/jwks | Publishes verification keys, never private keys |
| /oauth2/device_authorization | Starts device authorization and obtains device and user codes |
| /oauth2/device_verification | Handles the user's confirmation of a device user code |

Ordinary login and protocol responses have different structures. The former uses the project's result wrapper; the latter uses fields such as access_token and error. Parse each according to its contract.

## Original material and license

Upstream excerpts come from the [7.0.5 source archive](https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/7.0.5/spring-security-oauth2-authorization-server-7.0.5-sources.jar), retain the original text and Copyright 2004-present the original author or authors notice, and use the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Provenance records bind the archive, original files, line ranges, and excerpt digests. These are teaching excerpts, not additional runtime dependencies.

See the [official Spring migration announcement](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/) for the authorization server's move into Spring Security 7. This page uses the project's 7.0.5 source throughout, without replacing it with APIs from newer web examples.

The complete [license](../reference/upstream/spring-security-7.0.5/LICENSE.txt) and [provenance record](../reference/upstream/spring-security-7.0.5/provenance.json) are included with this site.
