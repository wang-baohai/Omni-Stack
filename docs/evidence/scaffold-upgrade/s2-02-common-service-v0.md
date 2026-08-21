# S2-02 公共业务 Starter v0 实施证据

日期：2026-08-21

分支：`codex/scaffold-upgrade`

目标模块：`omni-common-service`

## 1. 结论

已新增仅面向 Servlet 业务微服务的 `omni-common-service` v0，并纳入 20 模块 Maven reactor、
父 POM 依赖管理和后端 Docker 分层构建缓存。该版本只提供公共请求级基础设施与扩展 SPI，尚未让
CRM、SRM、Procurement 或 Asset 切换实现，因此当前运行服务的行为未改变。

全量 `clean install` 与 Starter 针对性测试均通过，可以进入 S2-03 的 CRM 单服务迁移。

## 2. 已实现能力

| 能力 | v0 实现 | 失败关闭行为 |
|---|---|---|
| 服务身份 | `ServiceRequestIdentity`、`ServiceIdentityContext`、Gateway 预认证和身份 Filter | 缺少合法用户/租户身份时拒绝；请求结束始终清理上下文 |
| DataScope | `@ServiceDataScope`、共享上下文、切面和 `DataScopeResolver` SPI | Resolver 缺失、Auth 不可用或身份不一致时不放行 |
| MyBatis-Plus | `TenantTablePolicy`、`DataScopeTablePolicy` 与公共自动配置 | 固定 TenantLine → DataPermission → OptimisticLock → Pagination 顺序；策略缺失时启动失败 |
| 内部 API | Token Filter、显式 `InternalTenantResolver` 和 Feign headers factory | Token 缺失、默认值或长度不足时拒绝；不从 ThreadLocal 猜测租户 |
| XSS | Auth 回源 resolver、Redis 缓存 Provider 与可覆盖 fallback | Redis/Auth 双失败时启用本地安全基线，不降级为关闭防护 |
| 自动配置 | Core、Auth client、Persistence、XSS 四组配置写入 `AutoConfiguration.imports` | 任一已启用安全能力配置不完整时由 validator 阻止启动 |

## 3. 兼容措施

- `omni-common-mqlog` 中旧 `InternalApiAuthFilter` 增加
  `omni.service.internal-api.enabled=false` 或缺省才生效的兼容条件。
- 新 Starter 开启内部 API 后旧 Filter 自动退出，确保最多注册一个内部认证 Filter。
- Feign 内部 Token 通过按 client 显式引用的 factory 创建，不注册全局 interceptor，避免向错误目标泄露。
- Gateway、Auth、Workflow 不依赖该 Starter；WebFlux、认证服务器和 Flowable 特殊语义保持原边界。

## 4. 自动化验证

针对性验证：

```text
omni-common-service: Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
omni-common-mqlog:   Tests run: 4,  Failures: 0, Errors: 0, Skipped: 0
```

全量后端验证：

```text
命令：omni-backend/mvnw clean install
Reactor：20/20 modules SUCCESS
结果：BUILD SUCCESS
耗时：02:23
完成时间：2026-08-21 13:09:45 +08:00
```

其中新增模块在全量 reactor 中报告：

```text
Omni Common Servlet Service Starter ........ SUCCESS
```

## 5. 测试覆盖点

- 必填服务标识与内部 Token 配置校验。
- Gateway 转发标记、公开/内部路径策略、请求身份建立与 finally 清理。
- DataScope 切面建立、解析结果传播和 finally 清理。
- MyBatis-Plus 四类 interceptor 的严格顺序及租户策略。
- 内部 API 缺 Token、错 Token和正确 Token路径。
- Feign Token、显式租户、Trace/traceparent 头传播及空 Token 拒绝。
- Auth DataScope/XSS resolver 的响应契约。
- XSS 安全基线默认开启。

## 6. 后续迁移门槛

S2-03 只迁移 CRM，并保留独立回退提交。迁移必须证明 401/403、租户隔离、DataScope、XSS、
内部调用和 MyBatis interceptor 顺序与迁移前等价；通过后才能继续 SRM、Procurement 和 Asset。
