# S2-01 公共业务 Starter 边界与自动配置矩阵

日期：2026-08-21  
分支：`codex/scaffold-upgrade`  
目标模块：`omni-common-service`

## 1. 结论

`omni-common-service` 只面向 Servlet 业务服务，抽取请求级基础设施和固定安全顺序，不抽取领域
权限映射、状态机或工作流协调。CRM 作为首个迁移样板；SRM、Procurement、Asset 只有在 CRM 的
401/403、租户、DataScope、XSS 和内部调用等价性通过后才依次迁移。

Gateway、Auth 和 Workflow 的特殊安全语义不能被四个业务服务的共同实现反向定义：

- Gateway 保持 WebFlux 与 reactive Redis，不依赖本 Starter。
- Auth 保留 Authorization Server、安全链、登录日志与权威数据范围解析。
- Workflow 保留 `TenantInfoHolder` 与 Flowable tenant 适配，只允许后续复用外围能力。

## 2. 重复实现清单

| 能力 | 当前重复位置 | 共同语义 | 差异/风险 | 处理 |
|---|---|---|---|---|
| Gateway 预认证 | CRM/SRM/Procurement/Asset，另有 Base/Auth/Workflow 变体 | 校验 `X-Gateway-Forwarded`，从用户/角色/权限头建立 Authentication，finally 清理 | 服务名错误文案、公开路径和内部路径不同 | Starter 提供可配置 Filter；业务服务迁移，其他服务暂不迁移 |
| 请求租户上下文 | 四个业务服务各一套 Filter + ThreadLocal record | userId/tenantId 必须为正数；业务路径缺失时 401；finally 清理 | 当前类型名带领域前缀 | 抽为不可变 `ServiceRequestIdentity` 与 `ServiceIdentityContext` |
| DataScope 注解/上下文/切面 | 四个业务服务各一套 | 按完整 permissionCode 向 Auth 解析；返回身份必须一致；403/503 失败关闭；finally 清理 | Auth Feign 类型、错误前缀不同 | Starter 提供 `@ServiceDataScope`、共享上下文和切面；Auth 调用走 SPI |
| Tenant/DataPermission 拦截器顺序 | 四个业务服务各一套 MybatisPlusConfig | TenantLine → DataPermission → OptimisticLock → Pagination | 表前缀、子表继承、owner/current-user 列均为领域语义 | Starter 固定装配顺序；服务实现 Tenant/DataScope policy |
| XSS 配置缓存/回源/基线 | CRM/SRM/Procurement/Asset，另有 Base/Auth/Workflow 变体 | Redis 30 分钟缓存；未命中回源 Auth；失败启用安全基线 | Auth 自己是权威源，不能调用自身；日志名称不同 | 业务服务使用共享 Provider；Auth 保留权威实现 |
| 内部 API Token Filter | 当前位于 `omni-common-mqlog` | `/api/internal/**` 校验共享 Token | 安全能力错误绑定到 MQ；重复注册会改变顺序 | 移入 Starter，mqlog 保留条件化兼容桥一个发布周期 |
| Feign 内部头传播 | 每个 Feign client 内嵌 RequestInterceptor | 内部 Token、显式 tenant、Trace/traceparent 传播 | 各目标 Token 不同；全局 interceptor 会向错误目标泄漏 Token | Starter 提供按 client configuration 使用的 factory，不注册全局 Token interceptor |

## 3. 公共 SPI 边界

| SPI/类型 | 所属 | 输入/输出 | 无实现或失败时行为 |
|---|---|---|---|
| `ServiceIdentityProperties` | Starter 配置 | 服务名、显示名、公开/管理/内部路径、功能开关 | 必需值缺失时启动失败 |
| `ServiceRequestIdentity` | Starter 值对象 | userId、tenantId、username | 不允许可变字段 |
| `ServiceIdentityContext` | Starter 上下文 | 当前请求身份 | `require` 失败关闭；Filter finally 清理 |
| `TenantTablePolicy` | 业务 SPI | `tableName -> 是否租户表` | 开启租户拦截但无实现时启动失败 |
| `DataScopeTablePolicy` | 业务 SPI | 领域表到 SQL 权限表达式 | 开启 DataScope 但无实现时启动失败 |
| `DataScopeResolver` | Starter SPI | user/tenant/permission -> 权威范围 | 403/503，不回退为全量 |
| `XssSettingsResolver` | Starter SPI | tenant -> Auth 权威 XSS 设置 | 调用失败后使用 enabled=true 本地基线 |
| `XssSettingsFallback` | 可覆盖 SPI | 本地安全基线 | 默认开启三类危险规则 |
| `InternalTenantResolver` | Starter SPI | 为内部消息/任务显式建立租户上下文 | 默认不读取或猜测请求 ThreadLocal |
| `InternalFeignHeadersFactory` | Starter 工具 | 为指定 client 创建 Token/tenant/trace interceptor | Token 为空时拒绝创建，不注册全局 Bean |

领域服务继续拥有：DataPermission 表/列映射、子资源继承 SQL、AccessGuard、owner/current-user 规则、
Portal 关联校验、状态机、Workflow coordinator、Inbox/Outbox 幂等以及 MQ 消息的显式 tenantId。

## 4. 自动配置条件矩阵

| 自动配置 | Class 条件 | Property 条件 | Bean 条件 | 产物 | 失败关闭条件 |
|---|---|---|---|---|---|
| Identity | Servlet + Spring Security | `gateway-preauth.enabled=true` | `@ConditionalOnMissingBean` | Gateway Filter、身份上下文过滤器 | name/display-name 空；公开/内部路径非法重叠 |
| Internal API | Servlet + Spring Security | `internal-api.enabled=true` | Filter 缺失 | 内部 Token Filter | Token 空、过短或默认占位值 |
| DataScope | AspectJ + Feign/API adapter | `data-scope.enabled=true` | Resolver 与 policy 唯一 | 注解切面、不可变 ScopeContext | Resolver/policy 缺失；身份不一致；Auth 不可用 |
| Persistence | MyBatis-Plus/JSqlParser | `tenant.enabled=true` | TenantTablePolicy + DataScopeTablePolicy | 固定顺序 MybatisPlusInterceptor | policy 缺失；拦截器顺序不满足契约 |
| XSS settings | Redis + Jackson + common XSS SPI | `xss.enabled=true` | resolver/fallback 可覆盖 | 共享 XssConfigProvider | Redis/Auth 失败时不是放行，而是安全基线 |
| Feign propagation | Feign | client 显式引用 configuration | factory 工具 Bean | Token/tenant/Trace 头传播 | 禁止全局 Token interceptor；Token 空时报错 |
| Health/validation | Actuator（health 可选） | Starter 启用 | validator 缺失 | 配置校验、Starter health details | 任一已启用安全能力配置不完整 |

所有自动配置写入 `AutoConfiguration.imports`；默认 Bean 使用 `@ConditionalOnMissingBean`，但安全校验
Bean 不允许被“缺配置即不创建”的条件静默跳过。

## 5. 迁移与兼容顺序

1. 创建 Starter v0 和自动配置 slice tests，不修改现有服务 Bean。
2. CRM 增加 Starter，保留旧类，用等价测试覆盖身份头、直接访问拒绝、缺租户、上下文清理、
   DataScope 403/503、TenantLine 顺序与 XSS 回退。
3. CRM 切换 Bean 后删除 CRM 重复类；运行模块测试和隔离全栈复验。
4. 依次重复 SRM、Procurement、Asset；每次迁移一个服务并保留可回退提交。
5. Base 只评估预认证和内部 API；Workflow 只评估外围 Filter；Auth/Gateway 不套用业务 Starter。
6. Internal API Filter 迁出 mqlog 时，以 class/property/bean 条件保证新旧实现最多存在一个。

## 6. 不变量与验收测试

- 外部业务路径无 `X-Gateway-Forwarded=true` 返回 403。
- 业务路径缺少合法 userId 或 tenantId 返回 401，不能得到匿名全量查询。
- `/api/internal/**` 不建立 Gateway 用户身份，只接受内部 Token 与显式 tenant 参数。
- Authentication、IdentityContext、DataScopeContext 均在 finally 清理。
- TenantLine、DataPermission、OptimisticLock、Pagination 顺序固定。
- `sys_mq_message` 不进入 Procurement/Asset TenantLine；Relay 继续跨租户扫描。
- XSS Redis/Auth 双失败时仍启用本地安全基线。
- Feign Token 只发送给显式绑定该 configuration 的 client。
- Auth 不引入 OperLog；Gateway 不引入阻塞 Redis；Workflow/Asset/Procurement 不引入错误的流程运行时依赖。

S2-02 只有在上述边界有自动化测试表达后才开始迁移 CRM。
