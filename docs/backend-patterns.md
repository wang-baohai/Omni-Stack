# 后端模式与规范

> 本文档定义了 Omni-Stack 后端的内部组织方式。所有后端代码必须遵循这些模式。  
> 架构概览详见 [architecture.md](architecture.md)。Docker 部署配置详见 [docker-deployment.md](docker-deployment.md)。

## 分层架构

```
Controller --> Service --> Repository (DAO)
     |            |            |
  参数校验      业务逻辑      数据访问
  结果包装      逻辑处理      SQL / ORM
                事务管理
```

### Controller 层

- **职责**：接收 HTTP 请求，校验参数，调用 Service，封装响应
- Controller 中**禁止编写业务逻辑**
- 所有方法返回 `R<T>`（成功）或 `R<PageResult<T>>`（分页）
- 请求 DTO 使用 `@Valid`（Jakarta Bean Validation）校验
- DTO 可以是 Controller 的内部静态类或独立文件
- RESTful 风格：`GET /user/{id}`、`POST /user`、`GET /user/list`

### Service 层

- **接口 + 实现**：`XxxService`（接口）+ `XxxServiceImpl`（实现类）
- 实现类上添加 `@Service` 注解
- 实现方法上添加 `@Transactional`：
  - 读操作：`@Transactional(readOnly = true)`
  - 写操作：`@Transactional`
- Service 层禁止使用 `HttpServletRequest` / `HttpServletResponse`
- 通过 `@RequiredArgsConstructor` + `final` 字段进行构造器注入

### Repository / DAO 层

- 使用 MyBatis-Plus 或 JPA；Mapper 接口命名：`XxxMapper`
- Mapper 中禁止编写业务逻辑
- SQL 参数：始终使用 `#{}`，禁止使用 `${}`（防止 SQL 注入）

## 依赖注入（DI）

```java
// 正确：通过 Lombok 构造器注入
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}

// 禁止：字段注入
@Autowired
private UserService userService;
```

**规则**：所有依赖注入必须使用 `@RequiredArgsConstructor` + `final` 字段。禁止 `@Autowired` 字段注入。

### 技术选型思考：为什么用 @RequiredArgsConstructor 而非 @Autowired

| 考量 | 理由 |
|------|------|
| **不可变性** | `final` 字段保证依赖一旦构造就不可更改，避免运行时被意外替换 |
| **编译时安全** | 遗漏依赖导致编译错误，而非运行时 `NullPointerException` |
| **测试友好** | 构造器注入可直接在测试中传入 Mock 对象，无需 Spring 容器或反射工具 |
| **明确性** | 所有依赖在类的构造器中一目了然，不需要扫描全部字段寻找 `@Autowired` |
| **Spring 官方推荐** | Spring 团队从 4.x 开始推荐构造器注入，字段注入在 5.x 后被标记为不推荐 |

## 参数校验

- 在请求 DTO 上使用 Jakarta Bean Validation 注解
- 在 Controller 方法参数上使用 `@Valid` 触发校验
- `MethodArgumentNotValidException` 和 `BindException` 由 `GlobalExceptionHandler` 统一捕获

```java
@Data
public static class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;
    @NotBlank(message = "邮箱不能为空")
    private String email;
}
```

## 异常处理

### BusinessException

```java
// 业务规则违反
throw new BusinessException("用户不存在");           // code: 500
throw new BusinessException(404, "用户不存在");      // code: 404
```

### GlobalExceptionHandler

位于 `omni-common`，捕获所有异常并转换为 `R<Void>`：

| 异常 | 处理方式 |
|------|----------|
| `BusinessException` | `log.warn` + `R.fail(code, message)` |
| `MethodArgumentNotValidException` | HTTP 400 + 聚合字段错误 + `R.fail(400, ...)` |
| `BindException` | HTTP 400 + 聚合字段错误 + `R.fail(400, ...)` |
| `Exception`（兜底捕获） | `log.error`（完整堆栈）+ `R.fail("服务器内部错误")` |

### 规则

- 禁止使用空的 `catch` 块
- 禁止使用异常进行流程控制
- NPE 防御：使用 `Optional`、`Objects.requireNonNull()`、提前进行空值检查
- 使用 `log.error("msg", e)` 记录异常并保留完整堆栈；禁止使用 `e.printStackTrace()`

## 日志规范

使用 Lombok `@Slf4j` 和 `log` 对象：

| 级别 | 用途 |
|------|------|
| `ERROR` | 需要立即关注的系统级错误 |
| `WARN` | 业务异常，可恢复的问题 |
| `INFO` | 关键业务流程检查点 |
| `DEBUG` | 开发和调试 |

```java
// 正确：参数化占位符
log.info("用户 {} 从 {} 登录", userId, ip);

// 禁止：字符串拼接
log.info("User " + userId + " logged in");

// 禁止：控制台输出
System.out.println("debug info");
```

- 禁止记录敏感信息（密码、令牌、身份证号）

## 面向对象规范

- 所有 POJO 类实现 `Serializable` 并声明 `serialVersionUID`
- 使用 Lombok：`@Data`、`@Getter`、`@Slf4j`、`@RequiredArgsConstructor`
- 类成员顺序：静态常量 -> 静态变量 -> 实例变量 -> 构造方法 -> 公共方法 -> 私有方法
- `equals`：将常量/确定值放在左侧：`"success".equals(status)`
- 包装类型：使用 `valueOf()`，禁止使用 `new Integer()`
- 浮点数比较：使用 `BigDecimal` 或指定精度（epsilon）

## 集合与并发

- 初始化集合时指定容量：`new ArrayList<>(16)`、`new HashMap<>(16)`
- 空判断：使用 `CollectionUtils.isEmpty()`，而非 `== null` 或 `size() == 0`
- 迭代过程中禁止 `remove`；使用 `Iterator` 或 `removeIf()`
- Map 遍历：使用 `entrySet()`，而非先 `keySet()` 再 `get()`
- 线程池：使用 `ThreadPoolExecutor`，禁止使用 `Executors.newXxx()`
- 并发修改：使用 `ConcurrentHashMap`、`AtomicXxx`；除非必要，避免手动加锁

## 命名规范（Java）

| 类型 | 风格 | 示例 |
|------|------|------|
| 包名 | 小写，点分隔 | `com.omni.business.controller` |
| 类名 | UpperCamelCase | `UserController`、`BusinessException` |
| 方法名 / 变量名 | lowerCamelCase | `getUserById`、`createTime` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`、`DEFAULT_PAGE_SIZE` |
| Boolean 变量 | 不加 `is` 前缀 | `deleted`、`enabled`（而非 `isDeleted`） |
| 抽象类 | `Abstract` 前缀 | `AbstractEntity` |
| 异常类 | `Exception` 后缀 | `BusinessException` |
| 枚举类 | `Enum` 后缀 | `OrderStatusEnum` |
| DTO 类 | `Request` / `Response` / `VO` 后缀 | `CreateUserRequest`、`UserVO` |
| Feign 接口 | `FeignClient` 后缀 | `RemoteServiceFeignClient` |
| Service 接口 | `XxxService` | `UserService` |
| Service 实现 | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper 接口 | `XxxMapper` | `UserMapper` |

## 代码格式（Java）

- 缩进：4 个空格，禁止使用 Tab
- 最大行宽：120 字符
- 花括号：K&R 风格（左花括号不换行）
- 方法之间保留一个空行
- 运算符两侧加空格：`a + b`、`if (x == y)`
- 逗号后加空格：`method(a, b, c)`
- 方法参数最多 5 个；超出时封装为 Request 对象
- import 顺序：`java.*` -> `jakarta.*` -> 第三方 -> `com.omni.*`，各组之间空行分隔
- 禁止通配符导入（`import xxx.*`），Controller 注解包除外

## 注释规范

- 类、类属性和类方法必须添加 Javadoc（`/** ... */`）
- 使用 `//` 作为行内注释，解释关键逻辑
- 禁止无意义的注释（例如 `// get name` 对应 `getName()`）
- TODO 格式：`// TODO: [模块] 描述`，定期清理
- FIXME 格式：`// FIXME: 描述`，用于已知问题

## 安全与权限

### 功能权限（API 鉴权）

Controller 方法使用 `@PreAuthorize` 声明所需权限编码：

```java
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<Void> create(@Valid @RequestBody CreateUserRequest request) {
    userService.createUser(request);
    return R.ok();
}
```

- 权限编码格式：`resource:action`（如 `system:user:update`、`system:role:delete`）
- 权限集合在 JWT Claims 中携带，Spring Security 在方法调用前自动校验
- 无权限时返回 403 Forbidden

### 数据权限（DataPermission）

基于 MyBatis-Plus `DataPermissionInterceptor` 实现行级数据过滤，业务代码零侵入。

**核心组件协作**：

```
Gateway → X-User-Id/X-Tenant-Id Header
    ↓
DataScopeResolveFilter（@Order(0), OncePerRequestFilter）
    ↓ 查询角色 → 合并 dataScope（最宽松优先）→ 解析可访问组织单元 ID
    ↓
DataScopeContext（ThreadLocal 存储 userId, tenantId, primaryUnitId, effectiveScope, accessibleUnitIds）
    ↓
DataPermissionInterceptor（MyBatis-Plus InnerInterceptor）
    ↓ 调用 DataPermissionHandlerImpl.getSqlSegment(Table, Expression, String)
    ↓ 仅对 sys_user 表追加 WHERE 条件
    ↓
业务 SQL 执行（自动过滤后的结果集）
    ↓
DataScopeContext.clear()（finally 块，防 ThreadLocal 泄漏）
```

**六级数据范围**：

| dataScope | SQL 行为 |
|-----------|---------|
| `ALL` | 不追加条件（跨租户可见） |
| `TENANT` | 不追加条件（现有 tenant_id 过滤已满足） |
| `DEPT_AND_BELOW` | `WHERE sys_user.primary_unit_id IN (本部门及后代 ID)` |
| `DEPT` | `WHERE sys_user.primary_unit_id IN (本部门 ID)` |
| `CUSTOM` | `WHERE sys_user.primary_unit_id IN (自定义部门+后代 ID)` |
| `SELF` | `WHERE sys_user.id = {当前用户ID}` |

**多角色合并**：用户拥有多个角色时，取优先级最高的 dataScope（ALL > TENANT > DEPT_AND_BELOW > DEPT > CUSTOM > SELF）。

**新增表的数据权限扩展**：

1. 在 `DataPermissionHandlerImpl` 中添加目标表名和对应的列映射
2. 目标表必须包含关联到 `sys_user` 的外键列（如 `primary_unit_id`、`create_by`）
3. `DataPermissionInterceptor` 注册顺序必须在 `PaginationInnerInterceptor` 之前

**内存过滤模式**：对于非数据库查询的数据（如 Redis 中的在线用户列表），Controller 从 `DataScopeContext.get()` 读取数据范围，手动按 `primaryUnitId` 过滤。

### ThreadLocal 使用规范

- `DataScopeContext` 使用 `ThreadLocal<DataScopeInfo>` 存储请求级上下文
- **必须**在 `try/finally` 块中清除，避免线程池场景下的泄漏
- 写入时机：`DataScopeResolveFilter.doFilterInternal()` 中 `try` 块之前
- 清除时机：`DataScopeResolveFilter.doFilterInternal()` 中 `finally` 块

### XSS 防护（三层防御架构）

XSS 防护采用三层纵深防御，配置通过数据库 + Redis 缓存管理，支持按租户隔离。

```
Layer 1: Jackson StringDeserializer — 自动清洗 @RequestBody JSON 中的 String 字段
Layer 2: Servlet Filter + HttpServletRequestWrapper — 清洗查询参数
Layer 3: Gateway WebFilter — 添加安全响应头
```

**核心组件**：

| 组件 | 模块 | 职责 |
|------|------|------|
| `XssConfigProvider` | omni-common-core | SPI 接口，由具体服务模块实现 |
| `XssSettings` / `XssRule` | omni-common-core | 配置值对象（enabled + 规则列表） |
| `XssSanitizer` | omni-common | 核心净化逻辑（HTML_TAG / EVENT_HANDLER / DANGEROUS_PROTOCOL / CUSTOM_PATTERN） |
| `XssStringDeserializer` | omni-common | Jackson String 反序列化器包装，自动清洗 |
| `XssFilter` | omni-common | OncePerRequestFilter，加载配置 + 设置 ThreadLocal |
| `XssHttpServletRequestWrapper` | omni-common | 重写 getParameter/getParameterValues |
| `XssAutoConfiguration` | omni-common | 自动注册 Filter + Jackson SimpleModule |
| `XssConfigProviderImpl` | omni-auth | 实现配置加载（Redis 缓存 + 数据库回源） |
| `SecurityHeadersFilter` | omni-gateway | 添加 X-Content-Type-Options / X-Frame-Options / Referrer-Policy |

**规则类型**：

| ruleType | 匹配与清洗方式 |
|----------|---------------|
| `HTML_TAG` | 剥离成对标签和自闭合标签 |
| `EVENT_HANDLER` | 剥离 `on*` 属性 |
| `DANGEROUS_PROTOCOL` | 替换 `javascript:` / `vbscript:` / `data:` 等协议字符串 |
| `CUSTOM_PATTERN` | 自定义正则替换 |

**扩展新服务**：实现 `XssConfigProvider` 接口即可自动获得 XSS 防护能力。`omni-common` 依赖引入后通过 `AutoConfiguration.imports` 自动装配。

**缓存策略**：Redis 键 `xss:enabled:{tenantId}` + `xss:rules:{tenantId}`，TTL 30 分钟。所有写操作（开关切换、规则 CRUD）后主动失效缓存。

## MQ 消息发送记录与补偿管理（omni-common-mqlog）

MQ 消息发送记录与补偿管理系统基于 Transactional Outbox + XXL-JOB 异步投递架构，为各微服务提供可靠消息发送能力，引入即用、零业务代码。

### 核心架构

```
业务事务（@Transactional）
    ↓
ReliableMessageTemplate.send(bindingName, payload)
    ↓ INSERT sys_mq_message (status=PENDING) -- 同一本地事务
    ↓
XXL-JOB mqRelayHandler (10s 轮询)
    ↓
MqMessageRelayService.relayAll()
    ↓ 批量查询 PENDING/FAILED 且 next_retry_time <= NOW()
    ↓
MessageSender.send(message) -- 策略模式，按 broker_type 路由
    ↓
成功 → status=SENT | 失败 → retry_count++, 指数退避 | 超限 → DEAD_LETTER
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `ReliableMessageTemplate` | 提供 `send(bindingName, payload)` / `send(bindingName, payload, msgKey)` 两个重载，在调用方事务中 INSERT 消息记录 |
| `MqMessageRelayService` | 轮询待投递消息，调用 `MessageSender` 策略实现发送，处理重试退避和死信标记 |
| `MqMessageRelayJob` | XXL-JOB handler（`@XxlJob("mqRelayHandler")` + `@SystemJobMeta`），触发 relay 逻辑 |
| `MessageSender` | 策略接口，按 `broker_type` 路由。当前实现 `RocketMqMessageSender`（基于 StreamBridge），后续可扩展 `KafkaMessageSender` |
| `MqMessageInternalController` | Feign 内部查询 API（`/api/internal/mq-message`），供聚合查询服务调用 |

### 新服务接入步骤

1. POM 中依赖 `omni-common-mqlog`
2. 确保已依赖 `omni-common-mybatis`（数据库）和 `omni-common-job`（XXL-JOB）
3. 如需 RocketMQ 发送能力，依赖 `spring-cloud-starter-stream-rocketmq`
4. `sys_mq_message` 表自动创建（`schema.sql` + `CREATE TABLE IF NOT EXISTS`）
5. `mqRelayHandler` 自动注册到 XXL-JOB（各服务执行器 AppName 不同，handler name 天然隔离）
6. 业务代码注入 `ReliableMessageTemplate`，调用 `send()` 方法即可

### 指数退避策略

重试间隔：`2^retryCount × 10s`。第1次 20s，第2次 40s，第3次 80s。超过 `max_retry`（默认 3）进入死信状态（DEAD_LETTER）。

### 死信处理

- **重发**：将 PENDING/FAILED/DEAD_LETTER 状态重置为 PENDING，`retry_count` 清零，relay 任务下次轮询重新投递
- **忽略**：DEAD_LETTER → SKIPPED，确认无需再投递的终态

## 操作日志（OperLog）

操作日志系统基于 AOP + RocketMQ 异步架构，自动采集 Controller 方法的请求上下文和实体变更快照，实现对业务操作的完整审计追踪。

### 核心记录流程

```
Controller 方法（@OperLog 注解）
    ↓
OperLogAspect（AOP @Around 切面）
    ↓ 采集请求上下文：username、tenantId、IP、URL、请求参数
    ↓ 实体变更快照：UPDATE/DELETE 操作前查询 oldValue，操作后查询 newValue
    ↓ EntityDiffer.diff()：字段级差异比对（仅 UPDATE）
    ↓
OperLogProducer.send(OperLogMessage)
    ↓ RocketMQ 异步发送
    ↓
omni-base 消费者
    ↓ INSERT INTO sys_oper_log（热表）
    ↓
OperLogArchiver（@Scheduled 每日 02:00）
    ↓ 将超过 180 天的热表记录迁移到 sys_oper_log_archive（冷表）
    ↓ 批次处理（每批 1000 条），迁移后从热表删除
```

### @OperLog 注解使用

```java
@OperLog(module = "用户管理", operType = OperType.CREATE, entityClass = SysUser.class, idExpr = "#result.data.id")
@PreAuthorize("hasAuthority('system:user:create')")
@PostMapping
public R<UserVO> create(@Valid @RequestBody CreateUserRequest request) {
    return R.ok(userService.createUser(request));
}
```

**注解参数说明**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `module` | String | 是 | 业务模块名称，如"用户管理"、"字典类型管理" |
| `operType` | OperType | 是 | 操作类型枚举（见下表） |
| `entityClass` | Class<?> | 条件 | 目标实体类，用于 AOP 自动 diff 变更快照。QUERY/EXPORT/IMPORT 类型无需指定 |
| `idExpr` | String | 条件 | SpEL 表达式，从方法参数或返回值中提取实体 ID。如 `"#id"`、`"#result.data.id"` |

### OperType 枚举

| 枚举值 | 含义 | 是否需要 entityClass | 说明 |
|--------|------|---------------------|------|
| `CREATE` | 新增 | 建议指定 | AOP 从返回值中提取新实体 ID 并查询 newValue |
| `UPDATE` | 修改 | 是 | AOP 在操作前查询 oldValue，操作后查询 newValue，执行字段级 diff |
| `DELETE` | 删除 | 是 | AOP 在操作前查询 oldValue，newValue 为 null |
| `QUERY` | 查询 | 否 | 仅记录查询行为，无实体快照 |
| `EXPORT` | 导出 | 否 | 仅记录导出行为，无实体快照 |
| `IMPORT` | 导入 | 否 | 仅记录导入行为，无实体快照 |

### 开发约束

1. **新增写操作 Controller 方法必须标注 `@OperLog`**，指定 `module`、`operType`，涉及实体变更时还需指定 `entityClass` 和 `idExpr`
2. **omni-auth 模块禁用 `@OperLog`**：认证行为由登录日志（`sys_login_log`）和审计日志（`sys_audit_log`）完整留存，omni-auth 不引入 `omni-common-operlog` 依赖，不在控制器方法上使用 `@OperLog` 注解
3. **新微服务接入**：需在 `pom.xml` 中依赖 `omni-common-operlog` 并配置 RocketMQ，该模块通过 `AutoConfiguration.imports` 自动注册 AOP 切面和 MQ 生产者
4. **`entityClass` 用途**：AOP 通过 `ApplicationContext` 查找对应实体类型的 `BaseMapper`，自动执行 `selectById` 获取变更前后快照
5. **`idExpr` SpEL 语法**：支持引用方法参数（`#id`、`#request.id`）和返回值（`#result.data.id`），解析失败时记录警告日志但不影响业务
6. **JSON 快照限制**：单条 oldValue/newValue 最大 4000 字符，超出自动截断
7. **热冷表分离**：热表 `sys_oper_log` 保留近 180 天数据供快速查询，冷表 `sys_oper_log_archive` 长期留存满足合规要求。归档任务每日 02:00 执行，单批次 1000 条，失败时停止当次归档

### 模块职责

| 模块 | 组件 | 职责 |
|------|------|------|
| `omni-common-core` | `OperLog` 注解、`OperType` 枚举、`OperLogMessage` POJO | 纯 POJO 层，无 Spring 依赖 |
| `omni-common-operlog` | `OperLogAspect`、`OperLogProducer`、`EntityDiffer`、`OperLogAutoConfiguration` | AOP 切面 + MQ 生产者 + 实体 diff + 自动装配 |
| `omni-base` | `OperLogConsumer`、`OperLogArchiver` | MQ 消费写入热表 + 定时归档到冷表 |

## Common Starter 接入规范

项目将公共能力拆分为组合 Starter 与可选能力模块。Servlet 业务服务优先使用
`omni-common-service`；Gateway、Auth 和 Workflow 按各自特殊边界选择底层模块。

### Starter 模块概览

| 模块 | 职责 | 自动配置内容 | 适用服务类型 |
|------|------|-------------|-------------|
| `omni-common-core` | 纯 POJO 层 | 无（无 Spring 依赖） | 所有模块 |
| `omni-common` | Web 自动配置 | `JacksonConfig`（时间序列化）、`WebMvcConfig`（CORS）、`GlobalExceptionHandler`、`XssAutoConfiguration`（Filter + Jackson Module） | Servlet 服务 |
| `omni-common-mybatis` | 数据库能力 | `MybatisPlusAutoConfiguration`：`MybatisPlusInterceptor`（MySQL `PaginationInnerInterceptor`）+ YAML 默认配置（驼峰映射、逻辑删除、自增 ID） | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis | `RedisAutoConfiguration`：`RedisTemplate<String, Object>`（Jackson 序列化）+ `RedisUtils` 工具类 + Lettuce 连接池配置 | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis | `spring-boot-starter-data-redis-reactive` + YAML 默认超时配置 | WebFlux 服务（如 Gateway） |
| `omni-common-mqlog` | 可靠 MQ 消息发送 | `ReliableMessageTemplate`（Transactional Outbox）、`MqMessageRelayService`（XXL-JOB 异步投递）、`MessageSender` 策略接口、`MqMessageInternalController`（Feign 内部查询）、`schema.sql`（自动建表） | Servlet 服务（需 MQ 能力） |
| `omni-common-service` | Servlet 业务服务安全与上下文组合 | Gateway 预认证 Filter、不可变请求身份、内部 API Token、DataScope SPI/切面、Tenant/DataPermission 固定顺序、Auth XSS 回源与安全基线 | CRM/SRM/Procurement/Asset 等 Servlet 业务服务 |

**自动配置注册机制**：所有 starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册，这是 Spring Boot 3+/4+ 的标准机制（替代了旧版 `spring.factories`）。

### 新服务接入步骤

1. **POM 依赖**：Servlet 业务服务声明组合 Starter，再按需增加 OperLog/Job/MQ：
   ```xml
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-service</artifactId></dependency>
   ```
2. **application.yml**：除数据源和 Redis 外，显式配置服务身份和启用的安全能力；内部 Token
   必须来自环境变量，不能使用弱默认值：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://127.0.0.1:3306/omni_xxx?useSSL=false&serverTimezone=Asia/Shanghai
       username: root
       password: root
     data:
       redis:
         host: 127.0.0.1
         port: 6379
         database: 0
   omni:
     service:
       name: omni-xxx
       display-name: Xxx
       gateway-preauth:
         enabled: true
       internal-api:
         enabled: true
         token: ${OMNI_INTERNAL_API_TOKEN}
       tenant:
         enabled: true
       data-scope:
         enabled: true
       xss:
         enabled: true
   ```
3. **领域 SPI**：启用 tenant/data-scope 时分别提供唯一的 `TenantTablePolicy` 和
   `DataScopeTablePolicy`；不得把领域表名或 owner 列放入 Starter。
4. **安全链**：将 Starter 提供的 Gateway Filter 放在 `AuthorizationFilter` 前，将身份上下文 Filter
   放在 Gateway Filter 后；二者已禁止 Servlet 容器重复注册。
5. **可选能力**：OperLog、Job、MQ 与 Workflow 继续作为独立依赖，按业务需要启用。

当前 `omni-common-service` 已提供 v0 自动配置和测试，CRM、SRM、Procurement、Asset 均已完成迁移、模块测试与隔离运行态复验。
Starter 的 XSS Provider 自动配置必须先于 `XssAutoConfiguration` 生效，并由上下文测试同时断言
Provider、Servlet FilterRegistration 以及 Jackson 2/3 清洗模块，避免条件求值顺序导致静默失效。

### 覆盖机制

所有 starter 的自动配置 Bean 均使用 `@ConditionalOnMissingBean` 注解，服务可按需覆盖：

- **MybatisPlusInterceptor**：服务定义同名 `mybatisPlusInterceptor` Bean 即可覆盖默认分页配置。典型场景：添加 `DataPermissionInterceptor`（**必须在 `PaginationInnerInterceptor` 之前注册**）
- **RedisTemplate**：服务定义 `@Bean(name = "redisTemplate")` 即可替换默认序列化策略
- **XSS 配置**：`XssAutoConfiguration` 条件依赖 `XssConfigProvider` Bean，不实现 SPI 则 XSS 过滤链不激活

### Redis Starter 互斥约束

`omni-common-redis`（阻塞式）和 `omni-common-redis-reactive`（响应式）**不可在同一服务中混用**：

- **WebFlux 服务**（如 Gateway）：只能依赖 `omni-common-redis-reactive`，阻塞式 Redis 调用会导致 Netty 事件循环线程饥饿
- **Servlet 服务**：使用 `omni-common-redis`（阻塞式），`RedisUtils` 提供同步 API

## 配置参考表

### application.yml 关键配置项

以下为后端微服务 `application.yml` 中的核心配置项：

| 配置键 | 说明 | 默认值 | Docker 环境变量覆盖 |
|--------|------|--------|-------------------|
| `server.port` | 服务端口 | 8100/8101/8102/8103 | `SERVER_PORT=8080` |
| `spring.application.name` | Nacos 服务名 | omni-auth/base/gateway/workflow | — |
| `spring.datasource.url` | 数据库连接 | `jdbc:mysql://127.0.0.1:3306/omni_xxx` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.username` | 数据库用户 | `root` | `SPRING_DATASOURCE_USERNAME` |
| `spring.datasource.password` | 数据库密码 | `root` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.data.redis.host` | Redis 地址 | `127.0.0.1` | `SPRING_DATA_REDIS_HOST` |
| `spring.data.redis.port` | Redis 端口 | `6379` | `SPRING_DATA_REDIS_PORT` |
| `spring.data.redis.database` | Redis DB 索引 | 0 | `SPRING_DATA_REDIS_DATABASE` |
| `spring.cloud.nacos.discovery.server-addr` | Nacos 地址 | `127.0.0.1:8848` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` |
| `spring.cloud.nacos.discovery.ip` | 注册 IP | `127.0.0.1` | `SPRING_CLOUD_NACOS_DISCOVERY_IP` |
| `auth.jwks.uri` | JWKS 端点 | `http://localhost:8100/oauth2/jwks` | `AUTH_JWKS_URI` |
| `auth.jwks.cache-ttl` | 公钥缓存时间 | `5m` | — |
| `spring.profiles.active` | 激活 Profile | `default` | `SPRING_PROFILES_ACTIVE` |

### MyBatis-Plus 配置

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `mybatis-plus.configuration.map-underscore-to-camel-case` | 下划线转驼峰 | `true` |
| `mybatis-plus.global-config.db-config.logic-delete-field` | 逻辑删除字段 | `deleted` |
| `mybatis-plus.global-config.db-config.logic-delete-value` | 逻辑删除值 | `1` |
| `mybatis-plus.global-config.db-config.logic-not-delete-value` | 逻辑未删除值 | `0` |
| `mybatis-plus.global-config.db-config.id-type` | ID 策略 | `AUTO`（数据库自增） |

### XXL-JOB 配置（omni-base）

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `xxl.job.admin.addresses` | Admin 地址 | `http://127.0.0.1:18080/xxl-job-admin` |
| `xxl.job.executor.appname` | 执行器名称 | `omni-base` |
| `xxl.job.executor.port` | 执行器端口 | `9999` |
| `xxl.job.accessToken` | 通信令牌 | `default_token` |

## Service 层设计模式

### 接口 + 实现分离

```
UserService (interface)     — 定义业务方法签名
    ↑ implements
UserServiceImpl (@Service)  — 实现业务逻辑 + @Transactional
```

**设计理由**：
- 接口层可被 OpenFeign 客户端复用
- 单元测试可直接 Mock 接口，不依赖实现类
- 实现类可替换而不影响调用方

### 事务管理策略

| 场景 | 注解 | 说明 |
|------|------|------|
| 只读查询 | `@Transactional(readOnly = true)` | 优化数据库查询性能，禁止写操作 |
| 写操作 | `@Transactional` | 默认 REQUIRED 传播级别 |
| 跨服务调用 | `@Transactional` + Outbox 模式 | 本地事务写 Outbox 表，不直接参与远程事务 |
| 独立事务 | `@Transactional(propagation = REQUIRES_NEW)` | 如日志记录，确保主事务回滚时日志仍被记录 |

### 异常处理链路

```
Controller 方法
    │ 调用 Service 方法
    ▼
Service 层
    │ 业务校验失败 → throw new BusinessException(400, "用户名已存在")
    │ 资源不存在   → throw new BusinessException(404, "用户不存在")
    ▼
GlobalExceptionHandler (@RestControllerAdvice)
    │ @ExceptionHandler(BusinessException.class)
    │   → log.warn + R.fail(code, message)
    │ @ExceptionHandler(MethodArgumentNotValidException.class)
    │   → HTTP 400 + 聚合字段错误 + R.fail(400, "field: message")
    │ @ExceptionHandler(AccessDeniedException.class)
    │   → HTTP 403 + R.fail(403, "权限不足")
    │ @ExceptionHandler(Exception.class)
    │   → log.error（完整堆栈）+ R.fail("服务器内部错误")
    ▼
统一 R<Void> 响应
```

**omni-auth 的特殊处理**：Auth 模块依赖 `omni-common-core`（非 `omni-common`），因此 `GlobalExceptionHandler` 不在组件扫描范围。Auth 模块通过 `AuthExceptionHandler`（局部 `@RestControllerAdvice`）提供等价的异常处理。

## 故障排查指南

### 常见问题

| 问题 | 可能原因 | 解决方案 |
|------|---------|----------|
| `@PreAuthorize` 不生效 | `GatewayPreAuthFilter` 未注册 | 检查 SecurityConfig 中是否 `addFilterBefore(new GatewayPreAuthFilter(), AuthorizationFilter.class)` |
| 分页查询返回空 | `PaginationInnerInterceptor` 未注册 | 检查 `MybatisPlusConfig` 中的拦截器注册顺序 |
| `@Transactional` 无效 | 方法不是 public / 自调用 | `@Transactional` 仅对 public 方法生效；同类内部方法调用不触发代理 |
| Redis 连接超时 | 容器网络不通 | `docker compose exec omni-auth ping redis` 检查网络连通性 |
| Nacos 注册失败 | 地址配置错误 | 确认 `server-addr` 使用容器名 `nacos:8848` 而非 `localhost` |
| JWT 验证失败 | 公钥缓存过期 | 检查 `auth.jwks.cache-ttl` 配置，或重启 Gateway 清除缓存 |
| XXL-JOB 注册失败 | Admin 未启动 | 确认 `xxl-job-admin` 容器健康运行 |
| MQ 消息不投递 | Relay Job 未触发 | 在 XXL-JOB Admin 控制台检查 `mqRelayHandler` 是否正常调度 |

### 调试技巧

```bash
# 查看后端服务日志
docker compose logs -f omni-auth

# 检查环境变量
docker compose exec omni-auth env | grep SPRING

# 测试数据库连接
docker compose exec omni-auth sh -c 'nc -zv mysql 3306'

# 检查 Nacos 注册的实例
curl -s http://localhost:8848/nacos/v1/ns/instance/list?serviceName=omni-auth
```

## 测试

Auth、CRM 和部分 Common 模块已经建立测试目录。新增或修改后端能力时：

- **单元测试**：JUnit 5 + Mockito，放在 `src/test/java/`
- **集成测试**：`@SpringBootTest` + 嵌入式数据库或 Test Containers
- 测试类命名：`XxxTest`（单元）或 `XxxIntegrationTest`（集成）
- 测试方法命名：`should_<expected>_when_<condition>`
