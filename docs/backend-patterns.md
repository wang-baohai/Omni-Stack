# 后端模式与规范

> 本文维护后端分层、编码约定和公共能力接入边界。下列“应当”约束新增或修改代码，不代表历史代码已全部符合或已通过业务验收。
> 模块与信任边界见[架构](architecture.md)，线协议见[API 契约](api-contract.md)，部署变量见[Docker 部署](docker-deployment.md)。完整实操由 Wiki 和开发指南维护。

## 分层架构

```text
Controller --> Service --> Mapper (DAO)
HTTP/validation   Domain rules   SQL/data access
                  Local transaction boundary
```

### Controller 层

- 接收 HTTP 请求、触发已声明的参数约束、执行入口权限和身份检查，再调用 Service。领域状态机与持久化规则由 Service 承担。
- 普通业务 API 使用 `R<T>`，分页使用 `R<PageResult<T>>`。OAuth/OIDC、重定向、Actuator 等协议端点按各自契约返回，不强行包装。
- `@Valid` 只触发 DTO 已声明的约束，不能代替租户归属、资源可见性、唯一性或业务状态校验。DTO 可独立定义或使用内部静态类。
- 路径、HTTP 方法和字段以实际 Controller 为准；示意类和代码节选不是可以直接调用的完整接口。新增写入口需同步权限种子、迁移校验与前端按钮权限。

### Service 层

- 使用 `XxxService` 接口与 `XxxServiceImpl` 实现，业务实现标注 `@Service`，以构造器传入依赖。
- 在实际需要原子性的本地业务边界声明事务；读取是否需要只读事务由一致性需求决定。不能从分层约定推断每个现有方法已有事务。
- 新领域 Service 尽量接收明确的身份、租户和业务参数；HTTP 对象留在适配层。历史实现有特例，变更时按模块契约处理。

### Repository / DAO 层

- 当前业务持久化以 MyBatis-Plus Mapper 为主，接口使用 `XxxMapper`。Flowable 的内部持久化由引擎管理，不据此宣称项目另有通用 JPA 接入方案。
- Mapper 负责数据访问，领域决策由 Service 承担。`#{}` 绑定参数值；表名、列名、排序等动态 SQL 标识符必须来自受控映射，不能拼接用户输入。
- 自定义 SQL、关联子表与后台跨租户任务需要显式核对租户和数据范围；仅引入 Starter 不能证明所有 SQL 已受保护。

## 依赖注入（DI）

下面是构造器注入示意，省略 imports；不是新增的生产 Controller。

```java
// 构造器注入示意，省略 imports
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}
```

禁止 `@Autowired` 字段注入。通常用 `@RequiredArgsConstructor` 与 `final` 字段；需要 `@Lazy`、可选依赖或初始化分支时可写显式构造器、`@Bean` 方法或使用 `ObjectProvider`。`OperLogProducer` 和 `OperLogAutoConfiguration` 提供了现有例子。

### 技术选型思考：为什么用 @RequiredArgsConstructor 而非 @Autowired

| 考虑 | 实际收益与边界 |
|---|---|
| 依赖可见 | 构造参数表明创建对象需要什么，测试可以直接传入 Mock |
| 字段不变 | `final` 阻止字段被重新赋值，不保证所引用对象不可变或线程安全 |
| 失败定位 | 手工构造缺少参数可被编译器发现；Spring Bean 缺失、歧义或配置错误仍可能在启动时失败 |
| 可选能力 | 可选依赖需明确声明和处理，不能用 Lombok 消除运行期条件 |

## 参数校验

`@Valid @RequestBody` 配合 Jakarta Bean Validation。以下摘自 `CreateDictTypeRequest`，其余字段与 imports 省略；源码中文诊断文案在四语版本中保持原样。

```java
/** 字典类型编码 */
@NotBlank(message = "字典类型编码不能为空")
private String typeCode;

/** 字典类型名称 */
@NotBlank(message = "字典类型名称不能为空")
private String typeName;
```

Bean Validation 失败、参数缺失和类型不匹配由匹配的 MVC advice 处理。业务层仍需校验租户、权限、资源和状态；嵌套对象、集合元素及方法参数是否验证取决于实际声明和调用路径。

## 异常处理

### BusinessException

以下是两个独立的抛出示例：单参数构造器的业务码为 500，双参数构造器使用指定码。业务码与 HTTP 状态分开记录。

```java
// 两个独立示例：默认业务码与显式业务码
throw new BusinessException("资源不存在");
throw new BusinessException(404, "资源不存在");
```

### GlobalExceptionHandler

`omni-common` 的自动配置导入 `GlobalExceptionHandler`。下表针对异常确实到达该 MVC advice 的路径；不覆盖 Gateway、Security Filter、OAuth 协议或响应提交后的异常。

| 异常 | HTTP 状态 | `R.code` 与处理 |
|---|---|---|
| `BusinessException` | 方法不设置状态；普通未改状态的响应通常为 200 | 异常携带的业务码；WARN |
| `MethodArgumentNotValidException` / `BindException` | 400 | 400；聚合字段错误 |
| `MissingServletRequestParameterException` / `MethodArgumentTypeMismatchException` | 400 | 400；参数错误 |
| `AccessDeniedException` / `AuthorizationDeniedException` | 403 | 403；权限不足 |
| `NoResourceFoundException` | 404 | 404；服务内无匹配资源 |
| `HttpRequestMethodNotSupportedException` | 405 | 405 |
| `HttpMediaTypeNotSupportedException` | 415 | 415 |
| 兜底 `Exception` | 500 | 500；ERROR 保留堆栈，响应使用通用文案 |

完整错误、空值、日期及金额序列化语义由 [API 契约](api-contract.md)维护；不能把所有 JSON 解析问题自动视为 400。

### 规则

- 不吞掉需要处理的异常；预期拒绝与非预期故障分别记录。明确降级策略的 `catch` 需要说明日志、返回值和恢复行为。
- 在边界检查空值；`Optional`、`Objects.requireNonNull` 不替代业务异常契约。
- 使用带异常参数的日志保存所需堆栈，避免 `e.printStackTrace()`；日志和返回信息均需避免泄露凭据与个人数据。

## 日志规范

使用 `@Slf4j` 与参数化占位符。级别按实际影响选择：ERROR 表示失败，WARN 表示预期拒绝或降级，INFO 用于关键业务节点，DEBUG 用于诊断。

```java
// 使用占位符，示意日志不包含凭据
log.info("用户 {} 从 {} 登录", userId, ip);
```

不使用 `System.out.println`，不拼接日志字符串。密码、Bearer/Refresh Token、客户端秘密和完整个人信息不得进入普通日志。操作日志的规则脱敏不能保证任意自定义字段已脱敏；选择字段和访问权限仍由业务负责。追踪、指标和日志关联见[可观测性](observability.md)。

## 面向对象规范

- 根据模型用途选用 Lombok；实现 `Serializable` 的类必须声明 `serialVersionUID`，不强制每个 DTO、record 或领域类都实现序列化接口。
- 成员通常按常量、静态变量、字段、构造器、public 方法、private 方法排列。使用明确的封装和职责，避免依赖顺序隐藏不变量。
- 常量放在 `equals` 左侧可避免该接收者为空；装箱使用 `valueOf`。金额使用 `BigDecimal` 并明确精度、舍入与比较规则；浮点近似比较需有业务容差。

## 集合与并发

- 集合容量按可预期规模选择，避免机械地为所有集合分配固定容量。`CollectionUtils.isEmpty` 可处理空引用；对于非空集合，`isEmpty()` 也是有效表达。
- 迭代修改使用适用的 Iterator、`removeIf` 或分阶段操作。需要键和值时优先遍历 `entrySet()`。
- 新线程池应显式设置队列、线程上限、拒绝策略和关闭生命周期，避免不受控的 `Executors.newXxx()` 默认配置。
- `ConcurrentHashMap`、原子变量不自动保护跨对象不变量。复合更新、数据库并发和审批占用按模块锁、版本或事务协议处理，不以“用了并发集合”代替并发验证。

## 命名规范（Java）

以下是新增代码的约定，现有类名以源码为准；例如 `OperType` 未使用 `Enum` 后缀，不应为追求格式重命名公开类型。

| 类型 | 约定或实例 |
|---|---|
| 包 / 类 / 方法和变量 | 小写点分包；UpperCamelCase；lowerCamelCase |
| 常量 / 布尔变量 | UPPER_SNAKE_CASE；通常避免字段 `is` 前缀 |
| 抽象类 / 异常 | `AbstractXxx` / `XxxException` |
| 枚举 / DTO | 含义清晰的类型名；Request、Response、VO 按职责选用 |
| Feign / Service / 实现 / Mapper | 按所在模块保持命名一致；`XxxService` / `XxxServiceImpl` / `XxxMapper` |

## 代码格式（Java）

新增或修改代码使用 4 空格缩进、K&R 花括号、运算符及逗号间距，方法之间留空行。以 120 字符和较短参数列表为可读性目标，参数过多时评估参数对象；现有 Controller 可能超过 5 个参数。

imports 按 `java`、`jakarta`、第三方和 `com.omni` 分组。不用通配符 imports，Controller 注解包例外。当前 Maven POM 扫描未发现 Checkstyle、Spotless 或 PMD 声明；编译通过不代表这些格式目标已由检查器证明。

## 注释规范

新增 public 类与方法提供 Javadoc，关键字段和不直观逻辑按需解释。代码注释使用中文，不重复方法名。TODO 使用 `// TODO: [module] description`，FIXME 明确问题和边界；不把文档约定表述为历史代码全量符合的结论。

## 安全与权限

### 功能权限（API 鉴权）

普通受保护业务入口依赖 Gateway 校验 JWT，再由服务验证可信身份断言、建立 Authentication 并执行方法权限。不能直接相信客户端提交的身份/权限 Header。身份签名的覆盖范围与网络信任限制见[架构](architecture.md)。

以下两个注解摘自 Base 字典类型更新入口；它们不代替资源归属检查：

```java
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
```

权限码按 `resource:action` 分层命名，例如 `dict:type:update`。新增写入口需同步 `@PreAuthorize`、`scripts/sql/seed/auth.sql`、`database/seed/manifest.yaml` 和前端 `v-permission`。公开注册、OAuth/OIDC、内部 Token 协议和按行 ownership 的 `MyJobController` 分别遵循各自契约，不机械追加同一注解。普通方法鉴权拒绝为 403；实际过滤器错误需按对应路径判断。

### 数据权限（DataPermission）

```text
Trusted identity --> Request context --> Permission-aware scope
    --> Domain table/aggregate policy --> SQL + explicit tenant predicates
    --> finally: restore/clear context
```

Auth 的 `MybatisPlusConfig` 顺序为 DataPermission → OptimisticLock → Pagination，没有通用 TenantLine。`DataPermissionHandlerImpl` 映射 `sys_user`、`sys_org_unit`、`sys_role`，租户条件仍需相应 Mapper/Service 显式承担。

| dataScope | Auth `sys_user` 条件示例 |
|---|---|
| `ALL` / `TENANT` | 此 handler 不附加条件；不等于所有调用都允许跨租户 |
| `DEPT_AND_BELOW` / `DEPT` / `CUSTOM` | 按解析后的组织 ID 集合过滤 `primary_unit_id` |
| `SELF` | 按当前 `id` 过滤 |

无权限上下文时该 Auth handler 也可能不附加条件，不能以拦截器存在代替入口授权。普通两参数角色解析与指定 permission 的三参数解析不同：后者仅合并授予目标权限的角色，没有匹配授权时拒绝。完整六种范围与优先级见[核心流程](core-flows.md)。

CRM/SRM/Procurement/Asset 启用 common-service 持久化能力时采用 TenantLine → DataPermission → OptimisticLock → Pagination。表策略、owner 映射、子表经根聚合继承及 `AccessGuard` 由各领域拥有；TenantLine 只作用于对应业务前缀，`sys_mq_message` 保留跨租户中继用途。

扩展新表时更新所属服务的 `DataScopeTablePolicy` / handler，区分 owner、当前使用人、租户共享表和子聚合关联。不要给无 owner 列的子表凭空追加条件，也不要把组织 ID 当成用户外键。DataPermission 必须在分页之前。

Redis 在线用户由 `OnlineUserServiceImpl` 显式检查：`ALL` 保留跨租户语义，其余先验证租户所属再应用 SELF 或组织范围；强制下线在 Redis 操作和成功审计之前检查目标。缺少身份/租户/有效范围时为业务码 403；这条特定逻辑不应泛化到全部 SQL。

### ThreadLocal 使用规范

Auth `DataScopeResolveFilter` 在 `try` 内解析并设置上下文，在 `finally` 清理；内部路径有独立分支。common-service 的身份 Filter 与方法 DataScope Aspect 分别管理其生命周期，也需恢复或清理上下文。

`DataScopeContext`、身份上下文与 `XssRuleHolder` 不会自动随异步任务传播。线程池任务应显式传入所需身份/tenantId 并单独管理生命周期；可靠消息发送不得从 ThreadLocal 猜测租户。

### XSS 防护（三层防御架构）

```text
JSON String conversion --> Jackson 2/3 XSS modules + request rules
Query/form access --> XssHttpServletRequestWrapper
Gateway response --> SecurityHeadersFilter (no input sanitization)
```

`omni-common` 的 `XssAutoConfiguration` 在 Servlet 环境和 `XssConfigProvider` Bean 条件满足时注册 `/*` Filter，以及按 classpath 条件注册 Jackson 2/3 String 模块。JSON 字符串净化需要实际转换器使用对应模块和请求线程规则；仅实现接口但未注册 Bean 不能证明生效。

`XssFilter` 从租户 Header 取得配置，缺失/不可解析租户、关闭开关或空规则会直接放行。启用时包装 `getParameter` / `getParameterValues` 并通过 `finally` 清理规则；它未覆盖 `getParameterMap`，不能宣称任意输入访问方式都已净化。

| ruleType | 当前处理 |
|---|---|
| `HTML_TAG` | 按指定标签去除标签/配对内容，竖线分隔的标签按字面量转义 |
| `EVENT_HANDLER` | 按配置模式移除事件属性 |
| `DANGEROUS_PROTOCOL` | 不区分大小写去除配置协议，竖线分隔值按字面量处理 |
| `CUSTOM_PATTERN` | 正则替换；非法正则记录 WARN 并跳过该规则 |

这些是规则过滤，不能替代前端输出编码和富文本信任策略。Gateway 的 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy` 是响应头防御，不执行输入净化。

Auth `XssConfigProviderImpl` 使用 `xss:enabled:{tenantId}` 与 `xss:rules:{tenantId}`，TTL 30 分钟；写入开关或规则后由 `XssConfigServiceImpl` 主动删两键，不能仅等 TTL。删除位于本地事务内，不据此证明提交与并发缓存回填完全无竞态。Auth Provider 也不是任意 Redis 故障均自动回源。

common-service 的 `CachedServiceXssConfigProvider` 在缓存失败后尝试 Auth，失败或无结果后使用 `DefaultXssSettingsFallback` 的启用基线；权威关闭配置仍应保留。Provider 自动配置顺序和实际 Jackson/Filter Bean 需同时验证，接入步骤见[开发指南](guides/scaffold-development.md)。

## MQ 消息发送记录与补偿管理（omni-common-mqlog）

业务可靠发送使用 `omni-common-mqlog` 及 DB、Job 能力。`ReliableMessageRelay` / `ReliableMessageTemplate` 的 `send(bindingName, payload, tenantId[, msgKey])` 显式要求正 tenantId。Outbox 写入只有在调用处存在实际本地事务时才能与业务更新共同提交，不能覆盖远程服务或 Broker 事务。

`sys_mq_message` DDL 唯一所有者是 `database/changelog/common/0001-mq-schema.yaml`，历史 `schema.sql` 不是主建表路径。`mqRelayHandler` 同时声明 `@XxlJob` 与 `@SystemJobMeta`；中继跨租户扫描 PENDING/FAILED，外部及内部管理查询按 tenantId 过滤。`MessageSender` 按 broker_type 路由，现有实现为 `RocketMqMessageSender`。

状态机、指数退避、死信重发/跳过与扩展由 [MQ 可靠性](mq-reliability.md)维护，实际断点和失败恢复见 [Outbox 开发](wiki/outbox-development.md)。依赖或注解存在不证明执行器、Broker、消费与补偿当前都在运行。

## 操作日志（OperLog）

`@OperLog` AOP 为已标注入口记录操作元数据和可选实体快照，经 MQ 由 Base 存储。它不覆盖全部业务事件，也不提供完整审计、零丢失或合规保证。

### 核心记录流程

```text
@OperLog --> OperLogAspect --> OperLogProducer
    --> ReliableMessageRelay / Outbox (if available)
    --> StreamBridge direct send (fallback)
    --> Base OperLogConsumer --> sys_oper_log
XXL-JOB operLogArchiveHandler --> sys_oper_log_archive
```

`OperLogProducer` 有 ReliableMessageRelay 时走 Outbox，否则用 StreamBridge 直发；捕获发送异常并 WARN。直发代码没有检查返回布尔值，不能当作可靠投递回执。Outbox 与业务是否同事务还取决于调用边界。

Base 消费者按 `event_id` 唯一约束忽略 DuplicateKeyException，其他异常向消息中间件抛出；旧消息缺少 eventId 时由服务构造确定性 legacy ID。这不等于全部业务消费已证明 exactly-once。

`OperLogArchiver` 是 `operLogArchiveHandler`，同时具有 `@XxlJob` / `@SystemJobMeta`；元数据默认 cron 为 `0 0 2 * * ?`，默认 retentionDays 为 180，批大小为 1000。实际调度需 XXL-JOB 配置与运行。它先插冷表再删热表，失败批次停止；同 Bean 内部调用 `archiveBatch`，不能仅凭该方法的 `@Transactional` 注解宣称当前路径已受代理事务保护。冷表存在不证明长期保留策略或合规验收。

### @OperLog 注解使用

以下是现有 `DictTypeController.update` 的节选，省略 imports/Javadoc；中文 module 与方法参数保持源码原样。Auth 用户管理不能用作 OperLog 接入示例。

```java
@OperLog(module = "字典类型管理", operType = OperType.UPDATE, entityClass = SysDictType.class, idExpr = "#id")
@PutMapping("/{id}")
@PreAuthorize("hasAuthority('dict:type:update')")
public R<SysDictType> update(@PathVariable Long id,
                              @RequestHeader("X-Tenant-Id") Long tenantId,
                              @Valid @RequestBody UpdateDictTypeRequest request) {
    String operator = SecurityContextHolder.getContext().getAuthentication().getName();
    return R.ok(dictTypeService.updateType(id, tenantId, request, operator));
}
```

| 参数 | 当前含义 |
|---|---|
| `module` / `operType` | 模块名默认空串，项目约定明确填写；operType 无默认值 |
| `entityClass` / `idExpr` | 默认 Object.class / 空串；启用快照时按 Mapper 与参数/返回值 SpEL 提取 ID |
| `recordParams` / `recordSnapshot` | 默认 true，可为敏感或大文本业务关闭 |
| `excludeFields` | 额外排除字段，默认空数组，脱敏时规范化字段名 |

### OperType 枚举

| 类型 | 开启快照且配置满足时的行为 |
|---|---|
| `CREATE` | 从返回值提取 ID，查询 newValue |
| `UPDATE` | 前后查询，按顶层字段值比较输出差异 |
| `DELETE` | 查询 oldValue，newValue 保持 null |
| `QUERY` / `EXPORT` / `IMPORT` | 此快照分支不读取实体 |

### 开发约束

- 需要操作日志的新增写入口按业务敏感性配置注解。**omni-auth 禁止引入 `omni-common-operlog` 或使用 `@OperLog`**；认证事件由 `sys_audit_log` 保存，登录记录是其只读投影，不另建 `sys_login_log`。
- Mapper 查询与 SpEL 需要实际可解析。具有 tenantId 属性的快照仅保留匹配当前正 tenantId 的实体；无该属性的全局实体保留既有行为。不要把此检查当成业务写权限校验。
- `OperLogSanitizer` 对命中字段、电话、邮箱与凭据片段作规则脱敏，不是任意文本的完整脱敏。快照/参数等字符串超长时取前 4000 字符并追加 `...[TRUNCATED]`，并非总长度最多 4000；截断可能使 JSON 无效，而数据库快照列为 JSON，不能宣称超长快照一定能持久化。
- 目标方法异常继续向上抛；后处理捕获 Exception，SpEL/Mapper 查找有局部降级。执行目标前的快照查询及 ID 类型转换不全在保护块中，不能承诺所有采集错误均不影响业务。
- 日志 `responseStatus` 正常返回时记 200、目标抛错时记 500，不是最终 MVC HTTP 状态或 `R.code`。

### 模块职责

| 模块 | 组件 |
|---|---|
| `omni-common-core` | OperLog、OperType、OperLogMessage；生产代码无 Spring 依赖 |
| `omni-common-operlog` | Aspect、Producer、Sanitizer、EntityDiffer 与有条件自动配置 |
| `omni-base` | Consumer、OperLogService、Archiver 与热/冷表 |

## Common Starter 接入规范

### Starter 模块概览

当前有 10 个 common 模块，其中 core 为基础类型，其余 9 个按能力组合。完整依赖与模块表以[架构](architecture.md)为唯一入口。

| 模块 | 接入边界 |
|---|---|
| `omni-common-core` | 基础类型；生产代码无 Spring 自动配置 |
| `omni-common` | Servlet Web/MVC、Jackson、异常 advice 与有条件 XSS |
| `omni-common-mybatis` | 默认 OptimisticLock → MySQL Pagination；YAML 映射/逻辑删除/ID 约定 |
| `omni-common-redis` | 阻塞 RedisTemplate、RedisUtils 与默认连接配置 |
| `omni-common-redis-reactive` | reactive 依赖与超时 YAML，无本模块自定义 AutoConfiguration.imports |
| `omni-common-job` | XXL-JOB 执行器、系统任务登记等，按实际条件激活 |
| `omni-common-mqlog` | Outbox、中继、发送策略与管理查询，另需 DB/Job/Broker 配置 |
| `omni-common-operlog` | 注解 AOP 与发送，需匹配 Servlet/AspectJ/Mapper/StreamBridge 等依赖 |
| `omni-common-workflow` | Workflow 公共能力，不能向业务域服务引入引擎运行时 |
| `omni-common-service` | Servlet 身份、内部 API、DataScope、持久化与 XSS 组合 |

8 个能力模块具有自定义 `AutoConfiguration.imports`。导入条目仅证明注册入口，不能证明条件已满足或 Bean 已激活；不要写成所有模块都有同一自动配置机制。

### 新服务接入步骤

Servlet 领域服务优先 common-service，按需选 OperLog/Job/MQ；Gateway、Auth、Base、Workflow 保持各自边界。身份配置、`GATEWAY_ASSERTION_SECRET`、`OMNI_INTERNAL_API_TOKEN` 从私有环境取得，启用能力需满足启动校验。

租户和数据范围能力启用时提供唯一、与领域表匹配的 TenantTablePolicy/DataScopeTablePolicy；身份断言 Filter 在 AuthorizationFilter 前，身份上下文在预认证后，避免再次注册到 Servlet 容器。XSS Provider 配置应先于条件消费，并同时验证 FilterRegistration 与 Jackson 2/3 Bean。完整接入、生成器和 Docker 步骤见[开发指南](guides/scaffold-development.md)。

### 覆盖机制

- MyBatis 默认拦截器按 `MybatisPlusInterceptor` 类型缺失条件注册；按项目惯例定义同名 Bean，并保留正确顺序。
- RedisTemplate 按 Bean 名 `redisTemplate` 缺失条件注册，RedisUtils 按其返回类型条件注册。二者的替换条件不能混为一谈。
- XSS 以 Provider Bean 和 Servlet/classpath 为条件；OperLog 的若干 Bean 并未声明 ConditionalOnMissingBean。不可宣称全部 Starter Bean 都可以同样覆盖；先读对应配置并用容器上下文验证。

### Redis Starter 互斥约束

WebFlux Gateway 只用 `omni-common-redis-reactive`，Servlet 服务使用阻塞模块；同一服务不混用。不要在 Netty 事件循环执行阻塞调用；反应式依赖也不自动把业务中的阻塞操作变成非阻塞。

## 配置参考表

### application.yml 关键配置项

以下区分仓库 YAML 与正式 Compose；完整设置仍以[部署文档](docker-deployment.md)和当前配置为准。

| 项 | 当前约定或正式 Docker 值 |
|---|---|
| `server.port` / `SERVER_PORT` | 宿主入口 8100–8107；应用容器 HTTP 8080；Frontend 3000 |
| `spring.application.name` | omni-auth/base/gateway/workflow/crm/srm/procurement/asset，按服务配置 |
| `spring.datasource.*` | 正式容器 mysql:3306，用户 omni_app，密码来自 OMNI_DB_PASSWORD；Gateway 无业务数据源 |
| `spring.data.redis.*` | 正式 redis:6379；DB 与序列化按服务设置 |
| `spring.cloud.nacos.discovery.server-addr` | 正式 nacos:8848；Auth/Base/Workflow 与领域服务的变量接入方式按各自配置 |
| `spring.cloud.nacos.discovery.ip` | Compose 留空用于容器发现，不固定为宿主回环地址 |
| `auth.jwks.uri` / `AUTH_JWKS_URI` | Gateway YAML 为 `http://localhost:8100/oauth2/jwks`；Compose 为 `http://omni-auth:8080/oauth2/jwks` |
| `auth.jwks.cache-ttl` | Gateway 默认 5m |
| `spring.profiles.active` | 不混淆 Spring Profile、Compose profile 与 CLI preset |

密码、Nacos 身份/签名秘密、Gateway 断言密钥和内部 Token 使用私有配置；源码中的历史回退值不是推荐部署凭据。

### MyBatis-Plus 配置

common-mybatis YAML 声明 `map-underscore-to-camel-case=true`、逻辑删除字段 `deleted`（删除 1、未删 0）与 `id-type=auto`。实际实体和服务可有独立映射/策略，不能据默认值推断每张表都有这些字段。自定义拦截器必须保留 DataPermission 在 Pagination 之前。

### XXL-JOB 配置（omni-base）

| 项 | Base 当前值或正式 Docker 值 |
|---|---|
| `xxl.job.admin.addresses` | YAML http://127.0.0.1:18080/xxl-job-admin；Compose http://xxl-job-admin:8080/xxl-job-admin |
| `xxl.job.executor.appname` | omni-base |
| `xxl.job.executor.port` | Base YAML 9901；属性类通用回退 9999，其他服务独立配置 |
| `xxl.job.executor.address` / `ip` | Compose http://omni-base:9901/ / omni-base |
| `xxl.job.access-token` | 通用属性默认空；Compose 要求私有 XXL_JOB_ACCESS_TOKEN，两端一致 |
| `xxl.job.executor.enabled` | 自动配置默认开启；Compose 受 XXL_JOB_ENABLED 控制 |

管理员账号密码由私有环境提供；默认 cron、自动登记成功和任务实际执行是不同状态。系统 handler 必须同时声明 `@XxlJob` 与 `@SystemJobMeta`，用户任务路由和补偿规则见[调度文档](scheduling.md)。

## Service 层设计模式

### 接口 + 实现分离

```text
XxxService (contract)
    ^ implements
XxxServiceImpl (business behavior; local transactions where configured)
```

接口方便隔离调用与 Mock，但替换实现仍需保持语义和事务契约。Feign 是独立的远程协议适配，不能直接把任意 Service 接口当成已可用 Feign 客户端。

### 事务管理策略

| 场景 | 约定与限制 |
|---|---|
| 只读 | `@Transactional(readOnly = true)` 是事务提示/配置，不是授权或普遍禁止写入的证明 |
| 本地写 | `@Transactional` 默认 REQUIRED；需明确实际代理调用、异常回滚规则和数据库边界 |
| 远程操作 | 本地事务与 Outbox 可记录待发消息，不能回滚已发生的 HTTP/Broker/外部账号副作用 |
| 独立事务 | `REQUIRES_NEW` 需经过适用代理并成功提交；它本身不保证日志一定保留 |

不要从注解单独推断同类内部调用会经过代理，也不要把某个日志发送路径统一描述成独立事务。关键状态变更、占用与幂等以对应领域事实文档和回归为准。

### 异常处理链路

```text
Controller --> Service --> matching MVC advice --> R
Gateway / Security Filter / protocol endpoint --> own response boundary
```

Auth 实际依赖 common-core 与 common；common 的 imports 注册全局 advice，AuthExceptionHandler 限定 `com.omni.auth.controller` 并共存。源码旧 Javadoc 关于“不依赖 common”的说明不能覆盖当前 POM/imports。实际异常由匹配的 handler 或 Filter/协议层处理；不同 advice 的通用中文错误文案也不应翻译成后端实际返回值。

## 故障排查指南

[故障排查](guides/troubleshooting.md)维护复现步骤，[源码调试](wiki/source-debugging.md)维护调用关系、变量和断点。分别检查身份断言、方法权限、租户、资源可见性和事务状态，空列表、403、404与连接失败不是同一结论。

按 [AGENTS.md](../AGENTS.md) 在本机 Docker 构建、测试与运行，记录具体容器、会话、退出码和已发生副作用；保护日志和私有配置。修复前核对当前执行句柄，不通过重置数据、关闭授权或删除共享资源制造成功。

## 测试

JUnit 5 / Mockito 用于隔离逻辑；MockMvc 可验证特定 MVC wire 行为，但 Mock 身份、Service 或引擎的测试不证明真实 Gateway、数据库、Flowable、Broker 或浏览器流程。`@SpringBootTest` 的证明范围也取决于实际启用的依赖、环境和断言，不能从类名推断集成已验证。

测试放在对应模块 `src/test/java`，名称表达行为和前提，新增变更遵循模块约定。已有测试覆盖多个业务与公共模块，不能仅列 Auth/CRM 或把条件跳过算作成功。

后端实现变化按仓库要求在 Docker 执行 Maven Wrapper reactor：

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
```

JDK 25、JAVA_HOME 与模块顺序由工具容器/reactor 提供。文档单独变化可在精确核对实现与构建输入未变后复用已有记录，明确来源、通过、跳过与限制；不要把 Markdown 渲染、单元测试或构建成功当成全部业务验收。
