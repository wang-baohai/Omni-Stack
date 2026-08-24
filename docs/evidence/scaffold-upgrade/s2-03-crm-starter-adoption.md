# S2-03 CRM 采用公共业务 Starter 证据

日期：2026-08-21

分支：`codex/scaffold-upgrade`

代码提交：`f198792 feat(crm): migrate service infrastructure to common starter`

隔离环境：Compose project `omni-g1`，CRM 宿主机端口 `28104`

## 1. 结论

CRM 已作为首个业务服务迁移到 `omni-common-service`。预认证、请求身份、DataScope 切面与上下文、
XSS 缓存/回源模板、内部 API Token Filter 和 MyBatis-Plus 拦截器装配均改用公共 Starter；CRM
继续保留表范围、owner 列映射、AccessGuard、状态机、Customer 360 分块授权和租户默认数据等领域逻辑。

旧 CRM 重复 Filter、ThreadLocal、切面、XSS Provider 和 MyBatis 配置已删除，没有新旧 Bean 双注册。

## 2. 迁移映射

| 迁移前 | 迁移后 | CRM 保留内容 |
|---|---|---|
| `GatewayPreAuthFilter` | `GatewayPreAuthenticationFilter` | `SecurityConfig` 中固定过滤器顺序 |
| `CrmTenantContextFilter` / `CrmTenantContext` | `ServiceIdentityFilter` / `ServiceIdentityContext` | 无领域副本 |
| `@CrmDataScope` / Aspect / Context | `@ServiceDataScope` / 公共 Aspect / `ServiceDataScopeContext` | `CrmPermissionScopeExecutor` 的分块 scope 保存与恢复 |
| `MybatisPlusConfig` | `ServicePersistenceAutoConfiguration` | `CrmTenantTablePolicy`、`CrmDataPermissionHandler` |
| `XssConfigProviderImpl` | `CachedServiceXssConfigProvider` | CRM 只配置服务显示名和功能开关 |
| Feign 内嵌 Token lambda | `InternalFeignHeadersFactory` | Auth 用户/组织等 CRM 专用 API 定义和 decoder 预热 |

CRM 的 Starter 配置启用 Gateway 预认证、内部 API、tenant、DataScope 和 XSS；内部 Token 继续使用
所有 Servlet 服务共享的 `OMNI_INTERNAL_API_TOKEN`。

## 3. 自动化验证

```text
命令：omni-backend/mvnw.cmd -pl omni-crm -am test
Reactor：10/10 modules SUCCESS
CRM：Tests run: 45, Failures: 0, Errors: 0, Skipped: 4
结果：BUILD SUCCESS
耗时：26.618 s
```

```text
命令：omni-backend/mvnw.cmd clean install
Reactor：20/20 modules SUCCESS
CRM：Tests run: 45, Failures: 0, Errors: 0, Skipped: 4
结果：BUILD SUCCESS
耗时：03:39 min
```

4 个跳过用例属于既有 `CRM_TEST_MYSQL_URL` 条件化真实 MySQL 测试；其拦截器构造已同步切换为
公共 Starter。CRM 的普通单元/组件测试全部执行成功。

专项覆盖：

- 直访拒绝与 Gateway 权限头绑定、SecurityContext 清理。
- 缺 user/tenant 返回 401，`ServiceIdentityContext` finally 清理。
- DataScope 缺失生成 `id = -1`，SELF/DEPT/DEPT_AND_BELOW/CUSTOM/TENANT/ALL 映射不变。
- TenantLine → DataPermission → OptimisticLock → Pagination 顺序不变。
- XSS 显式关闭缓存值不被误判；Redis/Auth 双失败启用公共安全基线。
- 租户开通、Outbox tenantId、owner 校验和 Customer 360 分块 scope 测试继续通过。

## 4. 隔离运行态验证

镜像 `omni-crm:latest` 使用当前源码构建成功。只重建 `omni-g1` 的应用服务后，7 个后端服务曾同时
达到 healthy，CRM 使用新镜像健康启动。

首次启动门禁发现 G1 历史内部 Token 不足 32 位，Starter 按设计拒绝启动。没有降低校验，而是在
隔离项目进程内生成 48 字节随机值，并用同一值重建 Auth、Base、Workflow、CRM、SRM、Procurement、
Asset。密钥未写入仓库、`.env`、证据或命令输出，原开发栈未修改。

| 探针 | HTTP | 业务码 | 结果 |
|---|---:|---:|---|
| 直接访问 `/api/crm/pipeline/list` | 403 | 403 | `禁止直接访问 CRM 服务` |
| 有 Gateway 标记和 user、缺 tenant | 401 | 401 | `缺少合法的用户或租户身份` |
| 合法 user/tenant/权限头，DataScope 回源 Auth | 200 | 200 | 返回默认销售管道 |
| 内部 MQ 查询缺 Token | 401 | 401 | `内部 API 认证失败` |
| 内部 MQ 查询使用正确 Token 和显式 tenant | 200 | 200 | 返回租户内分页结果 |
| 临时停止 Auth 后请求 CRM | 200 | 503 | `权限服务暂时不可用`，未降级为无范围查询 |

HTTP 200 + 业务码 503 符合 `docs/api-contract.md` 的下游依赖不可用契约。503 演练命令在 `finally`
中成功执行 Auth 启动；后续 SRM 运行态复验再次确认同一隔离 Auth 容器恢复为 healthy。

## 5. 停止条件与后续

- S2-03 代码和运行态语义通过，已形成独立可回退提交 `f198792`。
- SRM 已在独立提交和证据中专项验证 Portal、内部 API 与 XSS 边界，没有复制 CRM 表策略。
