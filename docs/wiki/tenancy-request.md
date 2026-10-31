# 4. 工程实践：一次请求怎样带上可信的租户

现在沿着小李的一次查询走到数据库。先只看主线：选企业并登录，网关核对凭据，服务建立这次请求的身份，再按对应规则取数据。

## 第一步：先在正确的企业中找账号

登录时选择企业，不是仅给画面换一个名称。Auth 会在对应租户内寻找启用的用户。SysUserMapper 中的连续原文如下：

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/mapper/SysUserMapper.java" -->
```java
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND status = 1")
    SysUser selectByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);
```

这条查询同时使用 tenant_id、username 和 status=1。同名账号在不同企业里不会因此被当成同一个用户。通过后，签发的 JWT 包含 tenant_id，作为这份身份声明的一部分。

用户能在浏览器里填写租户头，不代表可以靠这个头切换企业。它来自客户端时仍是不可信输入。

## 第二步：网关按已经验证的凭据重写身份

Gateway 对受保护请求核验 JWT 签名、期限、用户/租户声明和令牌黑名单。下面是成功后改写请求头的连续片段：

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

先删除传入的 Bearer 和可信身份头，再从已经核验的 claims 填写用户、租户、角色和权限，并按当前链路传递网关断言。客户端自己填写的 X-Tenant-Id 不会因此覆盖凭据里的企业。

网关断言是下游检查身份来源的一份签名材料。当前签名覆盖 userId、tenantId、username 和签发时间；它不覆盖角色/权限头、请求方法、地址或正文，也没有用于防重复的随机编号 nonce。角色和权限头仍依赖网关清理及可信服务网络，不能把断言说成覆盖整个请求。

## 第三步：先验证来源，再建立请求身份

在采用 common-service 的普通业务链中，GatewayPreAuthenticationFilter 先验证断言、时效和明文身份一致性，建立认证对象。随后 ServiceIdentityFilter 核对用户与租户是正整数，再绑定 ServiceIdentityContext。

两者责任不同：前者验证来源，后者把本次身份放到服务代码方便读取的位置。不能只调用后者，就说自己已经完成签名验证。

请求结束时还要清理。下面是 ServiceIdentityFilter 的处理尾段：

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/identity/ServiceIdentityFilter.java" -->
```java
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    userId, tenantId, request.getHeader("X-User-Name")));
            filterChain.doFilter(request, response);
        } finally {
            ServiceIdentityContext.clear();
        }
```

即使下游抛错，finally 也会清除这次记录。因为服务器会反复使用工作线程，不能让上一个请求的身份留给下一个请求。

公开页面、管理端点、内部服务调用有各自路径策略，不能把这条普通业务链当成所有 HTTP 的唯一处理方式。

## 第四步：符合条件的表再加入租户限制

common-service 在开启租户能力时注册下面的处理器。企业来自可信服务身份；表是否适用，由领域策略决定。

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

requireTenantId 在缺少合法服务身份时失败，不是随便找一个默认企业继续执行。appliesTo 则回答“这是不是本服务负责的租户业务表”。

完整仓库里，CRM、SRM、采购、资产分别针对 crm_、srm_、proc_、ast_。这些策略不拦 sys_mq_message，否则跨租户后台中继可能看不到其他企业的待发消息。不要给所有表统一套一个有 tenant_id 的假设。

## 同一工程里，还有哪些不同路径

Flowable 是项目推进审批流程所用的引擎。流程服务的业务扩展表保存项目补充资料，引擎表保存框架运行资料；两者需要分别确定归属。JDBC 则是直接使用数据库连接的一种访问方式，不会自动经过这里的 MyBatis 插件。

| 路径 | 当前怎样处理 | 不能直接推断什么 |
|---|---|---|
| Auth 的账号等查询 | 对应 Mapper/Service 显式提供租户条件，配合自己的数据范围处理 | 没有通用 TenantLine，不能把领域插件当作它的证明 |
| 使用 common-service 的领域服务 | 按开关、身份上下文、表策略及数据范围处理器生效 | 不代表原始 JDBC 或任何未映射表都受同样保护 |
| Workflow 的业务扩展与引擎 | wf_ 与 ACT_* 分别处理，使用自己的租户上下文和引擎查询条件 | 不能把引擎表交给领域插件后就省掉校验 |
| 内部服务与后台任务 | 通过相应认证与显式租户参数处理 | 不继承浏览器线程的身份，也不都走公开网关 |

<!-- omni:modules:workflow -->
### 流程服务的一处现有限制

Workflow 的 MybatisPlusConfig 读取 TenantInfoHolder，当前有下面的默认分支：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/MybatisPlusConfig.java" -->
```java
            @Override
            public Expression getTenantId() {
                // 从 SecurityContext 或请求头中获取租户 ID，默认返回 1
                String tenantId = com.omni.common.workflow.tenant.TenantInfoHolder.getTenantId();
                return new LongValue(tenantId != null ? Long.parseLong(tenantId) : 1L);
            }
```

缺少上下文时回退到 1，是这里实际存在的行为，不是多租户实现的通用最佳做法。不能把它照抄成“缺企业时随便用第一家”，也不能把 common-service 的缺身份拒绝描述成已经覆盖这一分支。

工程后续如要收紧这个边界，需要单独验证初始化、内部调用、后台任务和引擎部署，不能为了让教程统一就改动底层行为。
<!-- omni:modules:end -->

## 查询之后，写操作还有自己的条件

即使已经限定企业，修改申请还需要操作权限、行级归属、当前状态、版本和影响行数等检查。读列表时“看不到”与修改时“不能改”是两层责任，不能互相替代。

下一篇继续解释[角色、组织与数据范围](tenancy-permissions.md)，看看企业内部怎样进一步分工。
