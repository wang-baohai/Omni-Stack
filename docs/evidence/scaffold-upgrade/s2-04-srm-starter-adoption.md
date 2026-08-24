# S2-04 SRM 采用公共业务 Starter 证据

日期：2026-08-24

分支：`codex/scaffold-upgrade`

代码提交：`376a251 feat(srm): migrate service infrastructure to common starter`

隔离环境：Compose project `omni-g1`，SRM 宿主机端口 `28105`

## 1. 当前结论

SRM 已在源码和自动化测试层迁移到 `omni-common-service`。Gateway 预认证、请求身份、DataScope
注解/上下文、内部 API Token Filter、内部 Feign 认证头、XSS 缓存/Auth 回源/安全基线，以及
MyBatis-Plus 拦截器装配均使用公共 Starter。

SRM 继续保留供应商 Portal 关联校验、Portal DataScope 切换、子资源权限继承 SQL、AccessGuard、
准入工作流协调、供应商生命周期、评估/风险策略和 Portal 角色 Saga/Outbox 等领域逻辑。

源码镜像已经在 `omni-g1` 完成运行态复验。直访、缺租户、内部 API Token、正常业务读取、
Auth 故障时 DataScope 失败关闭、Portal 角色/绑定边界，以及 XSS 基线降级和权威配置恢复均符合
预期。S2-04 的代码、验收证据和独立 Git 提交均已完成。

## 2. 迁移映射

| 迁移前 | 迁移后 | SRM 保留内容 |
|---|---|---|
| `GatewayPreAuthFilter` | `GatewayPreAuthenticationFilter` | `SecurityConfig` 的过滤器顺序 |
| `SrmTenantContextFilter` / `SrmTenantContext` | `ServiceIdentityFilter` / `ServiceIdentityContext` | 后台任务显式建立不可变身份 |
| `@SrmDataScope` / Aspect / Context | `@ServiceDataScope` / 公共 Aspect / `ServiceDataScopeContext` | `SrmPermissionScopeExecutor` 的分块范围保存与恢复 |
| Portal ThreadLocal 特例 | `SrmPortalScope` + 公共 DataScope Context | PortalUser 关联与 SUPPLIER 角色前置校验 |
| `MybatisPlusConfig` | `ServicePersistenceAutoConfiguration` | `SrmTenantTablePolicy`、`SrmDataPermissionHandler` |
| `XssConfigProviderImpl` | `CachedServiceXssConfigProvider` | SRM 只声明服务显示名与功能开关 |
| Feign 内嵌 Token lambda | `InternalFeignHeadersFactory` | Auth、Procurement、Workflow 的 SRM 专用 API 契约 |

旧 SRM 重复 Filter、ThreadLocal、DataScope 切面、XSS Provider 和 MyBatis 配置已删除，没有保留
新旧 Bean 双注册。`SrmDataPermissionHandler` 的供应商、评估、报价及其子资源继承规则未抽入 Starter。

## 3. 自动化验证

```text
命令：omni-backend/mvnw.cmd -pl omni-srm test
SRM：Tests run: 118, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：10.068 s
```

```text
命令：omni-backend/mvnw.cmd clean install
Reactor：20/20 modules SUCCESS
Starter：Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
SRM：Tests run: 118, Failures: 0, Errors: 0, Skipped: 0
结果：BUILD SUCCESS
耗时：02:07 min
```

专项覆盖：

- 直访拒绝、Gateway 角色/权限绑定以及 `SecurityContext` finally 清理。
- 缺租户失败关闭以及 `ServiceIdentityContext` finally 清理。
- 内部 API 缺 Token 拒绝、合法 Token 建立 `ROLE_INTERNAL_SERVICE` 并清理。
- Auth、Procurement、Workflow 三个 Feign 客户端均由 Starter 工厂注入内部 Token。
- DataScope 缺失生成 `id = -1`；SELF、DEPT、DEPT_AND_BELOW、CUSTOM、TENANT、ALL 语义不变。
- 联系人、资质、银行账户、风险、评估明细、报价头和报价行继续从领域聚合根继承权限。
- TenantLine → DataPermission → OptimisticLocker → Pagination 顺序不变。
- Portal 范围只在调用期间可见；消费线程、内部服务和租户初始化线程结束后清理上下文。
- XSS 显式关闭缓存值不被误判；Redis/Auth 双失败时启用公共安全基线。
- 供应商生命周期、邀请、准入、评估、风险、报价、Saga 和租户初始化测试继续通过。

全量构建同时证明 CRM 45 项（其中 4 项为既有条件化 MySQL 跳过）、Procurement 168 项、Asset
102 项及其余反应堆模块未因 SRM 迁移产生回归。

## 4. 运行态门禁

当前源码构建镜像后，只操作隔离 Compose project `omni-g1`。Windows 动态保留端口与原映射冲突，
因此仅把隔离 Nacos gRPC 宿主机端口改为 `30848`、RocketMQ NameServer 宿主机端口改为
`30876`；容器网络内部仍分别使用 `9848`、`9876`，业务端口和原开发栈均未改变。

| 探针 | 结果 | 判定 |
|---|---|---|
| 直访 `GET /api/srm/supplier/list`，无 Gateway 标记 | HTTP 403 / 业务码 403 | 通过 |
| 有 Gateway 标记和用户、缺 `X-Tenant-Id` | HTTP 401 / 业务码 401 | 通过 |
| `/api/internal/supplier/batch` 缺内部 Token | HTTP 401 / 业务码 401 | 通过 |
| 正确内部 Token + 显式 tenant 查询供应商摘要 | HTTP 200 / 业务码 200 | 通过 |
| 合法 tenant/user/role/scope 查询供应商列表 | HTTP 200 / 业务码 200，只返回租户 1 记录 | 通过 |
| 暂停 Auth 后执行合法 DataScope 查询 | HTTP 200 / 业务码 503，未退化为无范围查询 | 通过（按既有业务异常响应约定） |
| `SUPER_ADMIN` 读取 Portal profile | HTTP 403 / 业务码 403 | 通过 |
| 仅伪造 `SUPPLIER` 角色、无 PortalUser 绑定读取 profile | HTTP 200 / 业务码 403 | 通过（绑定校验生效；传输层沿用既有异常映射） |

### 4.1 XSS 自动配置缺陷与修复

第一次源码镜像复验发现：`ServiceXssAutoConfiguration` 能创建 `XssConfigProvider`，但
`XssAutoConfiguration` 的 `@ConditionalOnBean` 可能先被求值，导致 XSS Filter 和 Jackson 模块
不注册。此问题在单模块测试中未暴露，但在真实 Spring Boot 启动顺序中可复现。

修复方式：

- `ServiceXssAutoConfiguration` 使用 `@AutoConfigureBefore(XssAutoConfiguration.class)` 固定条件求值顺序；
- 新增 `ServiceXssAutoConfigurationTest`，断言 Provider、FilterRegistration、Jackson 2 模块和
  Jackson 3 模块全部存在且 Filter 已启用。

修复后清空 `xss:enabled:1`、`xss:rules:1`，暂停隔离 Auth，并提交包含
`<script>alert(1)</script>` 的无副作用无效 Portal 入驻请求：

- `name` 被清洗为空，校验响应为 HTTP 400，证明 Filter/Jackson 清洗链已实际执行；
- SRM 日志记录 `XSS 配置回源失败，启用基线防护`，证明 Auth 不可用时没有失效开放；
- 恢复 Auth 后只读业务请求返回 200；
- 隔离 Redis 中两个租户 XSS 缓存键均恢复，`EXISTS` 结果为 `2`。

构建镜像时曾有一次阿里云 Maven 镜像提前截断 JUnit 依赖下载；原命令重试成功，属于外部网络
瞬时故障，不是源码或测试失败。

## 5. 回滚与继续条件

- 回滚单位为 SRM 采用 Starter 的独立提交 `376a251`；该提交同时包含 XSS 自动配置顺序修复，
  如仅回退 SRM 迁移，必须保留该公共安全修复及其回归测试。
- 运行态探针现已全部通过；若后续回归，只修复或回退 SRM 当前迁移，不带入 Procurement 改动。
- 下一步按既定顺序迁移 Procurement，再迁移 Asset。
