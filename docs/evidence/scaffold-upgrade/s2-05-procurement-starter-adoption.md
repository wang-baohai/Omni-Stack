# S2-05 Procurement 采用公共业务 Starter 证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：`320ef56 feat(procurement): migrate service infrastructure to common starter`

隔离环境：Compose project `omni-g1`，Procurement 宿主机端口 `28106`

## 1. 当前结论

Procurement 已迁移到 `omni-common-service`。Gateway 预认证、不可变请求身份、DataScope 注解与
上下文、内部 API Token Filter、内部 Feign 认证头、XSS 缓存/Auth 回源/安全基线，以及
MyBatis-Plus 拦截器装配均改用公共 Starter。

采购模块继续保留请购、审批路由、RFQ、报价回执、采购订单、收货、Outbox、租户初始化和领域
AccessGuard。`ProcTenantTablePolicy` 只对 `proc_*` 表启用 TenantLine，`sys_mq_message` 明确排除；
`ProcDataPermissionHandler` 继续按请购 requester、RFQ/PO/GR owner 及子资源聚合根继承规则生成 SQL。

旧的 Procurement Filter、ThreadLocal、DataScope 切面、XSS Provider 与 MyBatis 配置已删除，未保留
新旧 Bean 双注册。

## 2. 迁移映射

| 迁移前 | 迁移后 | Procurement 保留内容 |
|---|---|---|
| `GatewayPreAuthFilter` | `GatewayPreAuthenticationFilter` | `SecurityConfig` 的固定过滤器顺序 |
| `ProcTenantContextFilter` / `ProcTenantContext` | `ServiceIdentityFilter` / `ServiceIdentityContext` | 后台消费与初始化显式建立身份 |
| `@ProcDataScope` / Aspect / Context | `@ServiceDataScope` / 公共 Aspect / `ServiceDataScopeContext` | 主数据、概览与聚合权限映射 |
| `MybatisPlusConfig` | `ServicePersistenceAutoConfiguration` | `ProcTenantTablePolicy`、`ProcDataPermissionHandler` |
| `XssConfigProviderImpl` | `CachedServiceXssConfigProvider` | 服务名称和能力开关配置 |
| 三个 Feign 客户端的 Token lambda | `InternalFeignHeadersFactory` | Auth、SRM、Workflow 采购专用契约 |

## 3. 自动化验证

```text
命令：omni-backend/mvnw.cmd -pl omni-procurement -am test
Reactor：10/10 modules SUCCESS
Procurement：Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：21.799 s
```

```text
命令：omni-backend/mvnw.cmd clean install
Reactor：20/20 modules SUCCESS
Starter：Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
Procurement：Tests run: 173, Failures: 0, Errors: 0, Skipped: 0
Asset：Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：02:28 min
```

专项覆盖：

- 直访拒绝、Gateway 权限头绑定、缺租户失败关闭以及上下文 finally 清理。
- 内部 API 缺 Token 拒绝；Auth、SRM、Workflow Feign 客户端统一注入内部 Token。
- SELF、DEPT、DEPT_AND_BELOW、CUSTOM、TENANT、ALL 语义和聚合根继承映射保持不变。
- `proc_*` 租户隔离与 `sys_mq_message` 排除策略。
- TenantLine → DataPermission → OptimisticLocker → Pagination 固定顺序。
- XSS Redis/Auth 双失败时启用安全基线，不降级为关闭防护。
- 审批路由解析、请购工作流、RFQ/报价、PO、收货并发与租户初始化测试继续通过。

Dockerfile 发布门禁使用当前源码构建 `omni-procurement:latest` 成功，容器内同样执行了目标服务及
依赖测试；只替换 `omni-g1-omni-procurement-1`，原开发栈和数据卷未改动。

## 4. 隔离运行态验证

| 探针 | 结果 | 判定 |
|---|---|---|
| 直访 `GET /api/procurement/overview/summary` | HTTP 403 / 业务码 403 | 通过 |
| Gateway 标记和用户存在、缺 `X-Tenant-Id` | HTTP 401 / 业务码 401 | 通过 |
| 合法 tenant/user/role/scope 查询采购概览 | HTTP 200 / 业务码 200 | 通过 |
| Asset 历史候选内部接口缺 Token | HTTP 401 / 业务码 401 | 通过 |
| 正确内部 Token + 显式 tenant 查询候选 | HTTP 200 / 业务码 200 | 通过 |
| 暂停 Auth 后执行合法 DataScope 查询 | HTTP 200 / 业务码 503 | 通过，未失效开放 |
| Procurement → Workflow 查询可绑定流程 | HTTP 200 / 业务码 200，1 个选项 | 通过 |
| Procurement → SRM 查询合格供应商 | HTTP 200 / 业务码 200，1 个选项 | 通过 |

Workflow 第一次只读探针返回业务码 503。定位结果为隔离 Workflow 容器在此前 Nacos 重启期间出现
DNS 解析失败后保持 unhealthy，HTTP 连接被提前关闭；Procurement 与 Workflow 的内部 Token 值和
长度一致。仅重启隔离 Workflow 后容器恢复 healthy，同一探针立即返回 200，因此不是本次 Feign
迁移回归。

## 5. XSS 双故障与清理证明

清除隔离租户 1 的 `xss:enabled:1`、`xss:rules:1` 后暂停 Auth，向创建品类接口发送名称为
`<script>alert(1)</script>` 的请求：

- 公共安全基线把名称清洗为空，响应为 HTTP 400 / 业务码 400，校验信息为
  `categoryName: must not be blank`；
- Procurement 日志记录“XSS 配置回源失败，启用基线防护”，证明 Redis/Auth 双失败时未放行；
- `finally` 恢复 Auth 后容器为 healthy，两个权威缓存键均重建，`EXISTS` 为 `2`。

租户当前权威 XSS 配置允许该输入，Auth 恢复后的同一请求因此返回 200 并创建了唯一测试品类
`XSS_RUNTIME_PROBE`。验收结束后以 tenantId 和唯一编码精确物理清理，数据库计数由 `1` 变为 `0`，
未留下临时业务数据。

## 6. 回滚与继续条件

- Procurement 迁移的独立回滚单位为提交 `320ef56`。
- 运行态、自动化测试、文档哈希和临时数据清理均已通过。
- 下一步按实施计划迁移 Asset；不得把 Procurement 的 requester/owner 映射机械复制到资产域。
