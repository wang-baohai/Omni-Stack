# S2-06 Asset 采用公共业务 Starter 证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：`26f65e9 feat(asset): migrate service infrastructure to common starter`

隔离环境：Compose project `omni-g1`，Asset 宿主机端口 `28107`

## 1. 当前结论

Asset 已迁移到 `omni-common-service`。Gateway 预认证、不可变请求身份、DataScope 注解与
上下文、内部 API Token Filter、内部 Feign 认证头、XSS 缓存/Auth 回源/安全基线，以及
MyBatis-Plus 拦截器装配均改用公共 Starter。

资产模块继续保留台账、分配/归还、调拨/处置、采购收货导入与回扫、Workflow 审批协调、
Outbox、租户初始化与领域 AccessGuard。`AssetTenantTablePolicy` 只对 `ast_*` 表启用
TenantLine，`sys_mq_message` 明确排除；`AssetDataPermissionHandler` 继续按管理归属、当前使用人与
调拨/处置子资源继承规则生成 SQL。

旧的 Asset Filter、ThreadLocal、DataScope 切面、XSS Provider 与 MyBatis 配置已删除，未保留
新旧 Bean 双注册。

## 2. 迁移映射

| 迁移前 | 迁移后 | Asset 保留内容 |
|---|---|---|
| `GatewayPreAuthFilter` | `GatewayPreAuthenticationFilter` | `SecurityConfig` 的固定过滤器顺序 |
| `AssetTenantContextFilter` / `AssetTenantContext` | `ServiceIdentityFilter` / `ServiceIdentityContext` | 事件消费和回扫显式建立身份 |
| `@AssetDataScope` / Aspect / Context | `@ServiceDataScope` / 公共 Aspect / `ServiceDataScopeContext` | 管理维度、使用维度和子表继承映射 |
| `MybatisPlusConfig` | `ServicePersistenceAutoConfiguration` | `AssetTenantTablePolicy`、`AssetDataPermissionHandler` |
| `XssConfigProviderImpl` | `CachedServiceXssConfigProvider` | 服务名称和能力开关配置 |
| 四个 Feign 客户端的 Token lambda | `InternalFeignHeadersFactory` | Auth、SRM、Procurement、Workflow 资产专用契约 |

## 3. 自动化验证

```text
命令：omni-backend/mvnw.cmd -pl omni-asset -am test
Reactor：10/10 modules SUCCESS
Asset：Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：24.792 s
```

```text
命令：omni-backend/mvnw.cmd clean install
Reactor：20/20 modules SUCCESS
Starter：Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
Asset：Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：02:05 min
```

专项覆盖：

- 直访拒绝、Gateway 权限头绑定、缺租户失败关闭以及上下文 `finally` 清理。
- 内部 API 缺 Token 拒绝；Auth、SRM、Procurement、Workflow Feign 客户端统一注入内部 Token。
- SELF、DEPT、DEPT_AND_BELOW、CUSTOM、TENANT、ALL 语义，管理/使用维度和子表继承映射保持不变。
- `ast_*` 租户隔离与 `sys_mq_message` 排除策略。
- TenantLine → DataPermission → OptimisticLocker → Pagination 固定顺序。
- XSS Redis/Auth 双失败时启用安全基线，不降级为关闭防护。
- 台账、使用人操作、调拨/处置占位、采购收货幂等、审批完成事件和租户初始化测试继续通过。

Dockerfile 发布门禁使用当前源码构建 `omni-asset:latest` 成功，容器内同样执行了目标服务及
依赖测试；只替换 `omni-g1-omni-asset-1`，原开发栈、其他隔离服务和数据卷未改动。

## 4. 隔离运行态验证

| 探针 | 结果 | 判定 |
|---|---|---|
| 直访 `GET /api/asset/overview/summary` | HTTP 403 / 业务码 403 | 通过 |
| Gateway 标记和用户存在、缺 `X-Tenant-Id` | HTTP 401 / 业务码 401 | 通过 |
| 合法 tenant/user/role/scope 查询资产概览 | HTTP 200 / 业务码 200 | 通过 |
| 采购历史回扫内部接口缺 Token | HTTP 401 / 业务码 401 | 通过 |
| 正确内部 Token + 故意不一致的 tenant | HTTP 200 / 业务码 403 | 通过，Token 已接受且未执行回扫 |
| 暂停 Auth 后执行合法 DataScope 查询 | HTTP 200 / 业务码 503 | 通过，未失效开放 |
| Asset → SRM 查询合格供应商 | HTTP 200 / 业务码 200，1 个选项 | 通过 |
| Asset → Auth 查询用户候选 | HTTP 200 / 业务码 200，返回用户列表 | 通过 |

Workflow 和 Procurement 的 Feign 认证头由 `AssetInternalFeignConfigTest` 自动化覆盖。运行态未为追求
形式上的 200 而创建调拨/处置审批或执行历史采购回扫，避免产生不必要的业务数据。

## 5. XSS 双故障与清理证明

精确删除隔离租户 1 的 `xss:enabled:1`、`xss:rules:1` 后暂停 Auth，向手工创建资产接口发送
名称为 `<script>alert(1)</script>` 且其他必填字段合法的请求：

- 公共安全基线把名称清洗为空，响应为 HTTP 400 / 业务码 400，校验信息为 `name: must not be blank`；
- Asset 日志记录“XSS 配置回源失败，启用基线防护”，证明 Redis/Auth 双失败时未放行；
- `finally` 恢复 Auth 后容器为 healthy，两个权威缓存键均重建，`EXISTS` 为 `2`；
- 数据库对该精确测试名称的记录计数为 `0`，未留下临时资产或其他业务数据。

## 6. 回滚与继续条件

- Asset 迁移的独立回滚单位为提交 `26f65e9`。
- 运行态、自动化测试、文档哈希和临时数据清理均已通过。
- CRM、SRM、Procurement、Asset 四个业务服务已全部完成公共 Starter 迁移；下一阶段进入 CLI、生成器与裁剪预设实施。
