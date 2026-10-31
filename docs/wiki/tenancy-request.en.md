# 4. Project practice: carry a trusted tenant through a request

Follow Li's query to the database. The main path is: choose a company and sign in, let the gateway validate the credential, establish this request's identity in the service, then retrieve data under the relevant rules.

## Step one: find the account in the right company

Selecting a company at login does more than change a heading. Auth searches for an enabled user inside that tenant. Continuous SysUserMapper source:

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/mapper/SysUserMapper.java" -->
```java
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND status = 1")
    SysUser selectByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);
```

The query uses tenant_id, username, and status=1 together. Equal usernames in different companies remain separate accounts. On success, the JWT includes tenant_id as an identity claim.

A browser can submit a tenant header, but that does not authorize switching company. Client-supplied values remain untrusted input.

## Step two: rewrite identity from validated credentials

For protected requests, Gateway checks JWT signature, lifetime, user/tenant claims, and the token blacklist. This continuous excerpt rewrites headers after success:

<!-- omni:source path="omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java" -->
```java
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.remove(AUTH_HEADER);
                                TRUSTED_IDENTITY_HEADERS.forEach(h::remove);
                                h.set(GATEWAY_FORWARDED_HEADER, GATEWAY_FORWARDED_VALUE);
                                // 向后兼容：旧服务仍可读明文身份头；新服务必须验证 X-Gateway-Assertion
                                h.set("X-User-Id", claims.getSubject());
                                h.set("X-Tenant-Id", getClaimAsString(claims, "tenant_id"));
                                h.set("X-User-Name", getClaimAsString(claims, "username"));
                                h.set("X-User-Roles", getClaimAsString(claims, "roles"));
                                h.set("X-User-Scopes", getClaimAsString(claims, "scope"));
                                if (assertionHeader != null) {
                                    h.set(GATEWAY_ASSERTION_HEADER, assertionHeader);
                                }
                            })
                            .build();
```

It removes the incoming Bearer and trusted identity headers, then fills user, tenant, roles, and permissions from validated claims and forwards the gateway assertion for this path. A client-written X-Tenant-Id does not replace the company in the credential.

The assertion lets downstream services check identity origin. It currently signs userId, tenantId, username, and issuance time, not role/permission headers, method, address, or body. It has no random replay-prevention identifier, or nonce. Roles and permissions still rely on gateway sanitization and the trusted service network; the assertion does not cover the whole request.

## Step three: validate origin before binding request identity

In ordinary common-service business chains, GatewayPreAuthenticationFilter validates the assertion, freshness, and plain identity-header consistency, then establishes authentication. ServiceIdentityFilter subsequently requires positive user/tenant IDs and binds ServiceIdentityContext.

The former validates origin; the latter makes the identity available to service code. Calling only the second filter does not validate a signature.

The identity must also be cleared. This is the end of ServiceIdentityFilter processing:

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/identity/ServiceIdentityFilter.java" -->
```java
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    userId, tenantId, request.getHeader("X-User-Name")));
            filterChain.doFilter(request, response);
        } finally {
            ServiceIdentityContext.clear();
        }
```

finally clears the record even when downstream code throws. Servers reuse worker threads; the previous request's identity must not be left for the next one.

Public pages, management endpoints, and internal calls have their own path policies. This is not the only chain for every HTTP request.

## Step four: apply tenant conditions to covered tables

When tenant support is enabled, common-service registers this handler. The trusted service identity supplies the company; domain policy supplies applicable tables.

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/config/ServicePersistenceAutoConfiguration.java" -->
```java
        if (properties.getTenant().isEnabled()) {
            TenantTablePolicy tenantPolicy = requireUnique(tenantPolicyProvider, "TenantTablePolicy");
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new LongValue(ServiceIdentityContext.requireTenantId());
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return !tenantPolicy.appliesTo(tableName);
                }
            }));
        }
```

requireTenantId fails without a valid identity instead of inventing a default company. appliesTo answers whether this table belongs to the service's tenant-managed domain.

In the full repository, CRM, SRM, purchasing, and assets cover crm_, srm_, proc_, and ast_ respectively. These policies exclude sys_mq_message so a background relay can scan other tenants' pending messages. Do not assume every table contains tenant_id.

## Different paths inside the same project

Flowable advances approval processes. Workflow business extension tables hold project additions; engine tables hold framework runtime information. Their ownership is handled separately. JDBC accesses database connections directly and does not automatically pass through these MyBatis plugins.

| Path | Current behavior | What cannot be inferred |
|---|---|---|
| Auth account and related queries | Mapper/Service explicitly supplies tenant conditions alongside its scope handler | There is no general TenantLine; domain configuration proves nothing about these queries |
| Domains using common-service | Capability switches, identity context, table policy, and scope handlers cooperate | Raw JDBC and unmapped tables do not automatically share the protection |
| Workflow extensions and engine | wf_ and ACT_* use separate handling, context, and engine query conditions | An engine table cannot simply rely on the domain plugin |
| Internal services and background tasks | Dedicated authentication and explicit tenant parameters | They neither inherit browser-thread identity nor all pass through the public gateway |

<!-- omni:modules:workflow -->
### A current limitation in Workflow

Workflow MybatisPlusConfig reads TenantInfoHolder with this default branch:

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/MybatisPlusConfig.java" -->
```java
            @Override
            public Expression getTenantId() {
                // 从 SecurityContext 或请求头中获取租户 ID，默认返回 1
                String tenantId = com.omni.common.workflow.tenant.TenantInfoHolder.getTenantId();
                return new LongValue(tenantId != null ? Long.parseLong(tenantId) : 1L);
            }
```

Falling back to 1 when context is absent is current behavior here, not a general recommendation. Do not copy it as “use the first company when missing”, or claim common-service's rejection covers this branch.

Tightening it later requires testing initialization, internal calls, background tasks, and engine deployment. The tutorial does not change underlying behavior just to make configurations uniform.
<!-- omni:modules:end -->

## Writes have further conditions

Restricting the company still leaves operation permission, ownership, state, version, and affected-row checks for writes. Not seeing a list record and being unable to modify it are separate responsibilities.

Continue with [roles, organizations, and data scope](tenancy-permissions.en.md) to see how work is divided inside a company.
