# Backend Patterns & Conventions

This document defines how the Omni-Stack backend is organized internally. All backend code must follow these patterns.

## Layering

```
Controller --> Service --> Repository (DAO)
     |            |            |
  Param check   Business     Data access
  Result wrap   logic        SQL / ORM
                Transaction
```

### Controller Layer

- **Responsibility**: Receive HTTP requests, validate parameters, call Service, wrap response
- **No business logic** in Controller
- All methods return `R<T>` (success) or `R<PageResult<T>>` (paginated)
- Request DTOs validated with `@Valid` (Jakarta Bean Validation)
- DTOs can be inner static classes of the Controller or standalone files
- RESTful style: `GET /user/{id}`, `POST /user`, `GET /user/list`

### Service Layer

- **Interface + Implementation**: `XxxService` (interface) + `XxxServiceImpl` (class)
- `@Service` annotation on the implementation class
- `@Transactional` on implementation methods:
  - Read operations: `@Transactional(readOnly = true)`
  - Write operations: `@Transactional`
- No `HttpServletRequest` / `HttpServletResponse` in Service layer
- Constructor injection via `@RequiredArgsConstructor` + `final` fields

### Repository / DAO Layer

- Use MyBatis-Plus or JPA; Mapper interface naming: `XxxMapper`
- No business logic in Mapper
- SQL parameters: always `#{}`, never `${}` (prevent SQL injection)

## Dependency Injection

```java
// CORRECT: constructor injection via Lombok
@RequiredArgsConstructor
@RestController
public class UserController {
    private final UserService userService;
}

// FORBIDDEN: field injection
@Autowired
private UserService userService;
```

**Rule**: All dependency injection must use `@RequiredArgsConstructor` + `final` fields. `@Autowired` field injection is prohibited.

## Validation

- Use Jakarta Bean Validation annotations on request DTOs
- Trigger validation with `@Valid` on Controller method parameters
- `MethodArgumentNotValidException` and `BindException` are caught globally by `GlobalExceptionHandler`

```java
@Data
public static class CreateUserRequest {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Email is required")
    private String email;
}
```

## Exception Handling

### BusinessException

```java
// Business rule violation
throw new BusinessException("User not found");           // code: 500
throw new BusinessException(404, "User not found");      // code: 404
```

### GlobalExceptionHandler

Located in `omni-common`, catches all exceptions and converts them to `R<Void>`:

| Exception | Handling |
|-----------|----------|
| `BusinessException` | `log.warn` + `R.fail(code, message)` |
| `MethodArgumentNotValidException` | HTTP 400 + aggregate field errors + `R.fail(400, ...)` |
| `BindException` | HTTP 400 + aggregate field errors + `R.fail(400, ...)` |
| `Exception` (catch-all) | `log.error` with full stack + `R.fail("Internal server error")` |

### Rules

- Never use empty `catch` blocks
- Never use exceptions for flow control
- NPE defense: use `Optional`, `Objects.requireNonNull()`, early null checks
- Log exceptions with `log.error("msg", e)` preserving the full stack trace; never `e.printStackTrace()`

## Logging

Use Lombok `@Slf4j` and the `log` object:

| Level | Usage |
|-------|-------|
| `ERROR` | System-level errors requiring immediate attention |
| `WARN` | Business exceptions, recoverable issues |
| `INFO` | Key business flow checkpoints |
| `DEBUG` | Development and debugging |

```java
// CORRECT: parameterized placeholder
log.info("User {} logged in from {}", userId, ip);

// FORBIDDEN: string concatenation
log.info("User " + userId + " logged in");

// FORBIDDEN: console output
System.out.println("debug info");
```

- Never log sensitive information (passwords, tokens, ID numbers)

## OOP Conventions

- All POJO classes implement `Serializable` and declare `serialVersionUID`
- Use Lombok: `@Data`, `@Getter`, `@Slf4j`, `@RequiredArgsConstructor`
- Class member order: static constants -> static variables -> instance variables -> constructors -> public methods -> private methods
- `equals`: put constants/deterministic values on the left: `"success".equals(status)`
- Wrapper types: use `valueOf()`, never `new Integer()`
- Float comparison: use `BigDecimal` or specify an epsilon

## Collection & Concurrency

- Initialize collections with capacity: `new ArrayList<>(16)`, `new HashMap<>(16)`
- Empty check: use `CollectionUtils.isEmpty()`, not `== null` or `size() == 0`
- No `remove` during iteration; use `Iterator` or `removeIf()`
- Map traversal: use `entrySet()`, not `keySet()` then `get()`
- Thread pools: use `ThreadPoolExecutor`, never `Executors.newXxx()`
- Concurrent modifications: `ConcurrentHashMap`, `AtomicXxx`; avoid manual locking unless necessary

## Naming Conventions (Java)

| Type | Style | Example |
|------|-------|---------|
| Package | lowercase, dot-separated | `com.omni.business.controller` |
| Class | UpperCamelCase | `UserController`, `BusinessException` |
| Method / Variable | lowerCamelCase | `getUserById`, `createTime` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE` |
| Boolean variable | No `is` prefix | `deleted`, `enabled` (not `isDeleted`) |
| Abstract class | `Abstract` prefix | `AbstractEntity` |
| Exception class | `Exception` suffix | `BusinessException` |
| Enum class | `Enum` suffix | `OrderStatusEnum` |
| DTO class | `Request` / `Response` / `VO` suffix | `CreateUserRequest`, `UserVO` |
| Feign interface | `FeignClient` suffix | `RemoteServiceFeignClient` |
| Service interface | `XxxService` | `UserService` |
| Service implementation | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper interface | `XxxMapper` | `UserMapper` |

## Code Format (Java)

- Indent: 4 spaces, no tabs
- Max line length: 120 characters
- Braces: K&R style (opening brace on same line)
- One blank line between methods
- Spaces around operators: `a + b`, `if (x == y)`
- Space after commas: `method(a, b, c)`
- Max 5 method parameters; encapsulate as Request object if exceeded
- Import order: `java.*` -> `jakarta.*` -> third-party -> `com.omni.*`, blank line between groups
- No wildcard imports (`import xxx.*`), except for Controller annotation packages

## Comments

- Classes, class attributes, and class methods must have Javadoc (`/** ... */`)
- Use `//` for inline comments explaining key logic
- No meaningless comments (e.g., `// get name` for `getName()`)
- TODO format: `// TODO: [module] description`, clean up regularly
- FIXME format: `// FIXME: description` for known issues

## Security & Permission

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

## Common Starter 接入规范

项目将公共能力拆分为 5 个模块，新微服务通过 Maven 依赖引入即用，无需手动配置。

### Starter 模块概览

| 模块 | 职责 | 自动配置内容 | 适用服务类型 |
|------|------|-------------|-------------|
| `omni-common-core` | 纯 POJO 层 | 无（无 Spring 依赖） | 所有模块 |
| `omni-common` | Web 自动配置 | `JacksonConfig`（时间序列化）、`WebMvcConfig`（CORS）、`GlobalExceptionHandler`、`XssAutoConfiguration`（Filter + Jackson Module） | Servlet 服务 |
| `omni-common-mybatis` | 数据库能力 | `MybatisPlusAutoConfiguration`：`MybatisPlusInterceptor`（MySQL `PaginationInnerInterceptor`）+ YAML 默认配置（驼峰映射、逻辑删除、自增 ID） | Servlet 服务 |
| `omni-common-redis` | 阻塞式 Redis | `RedisAutoConfiguration`：`RedisTemplate<String, Object>`（Jackson 序列化）+ `RedisUtils` 工具类 + Lettuce 连接池配置 | Servlet 服务 |
| `omni-common-redis-reactive` | 响应式 Redis | `spring-boot-starter-data-redis-reactive` + YAML 默认超时配置 | WebFlux 服务（如 Gateway） |

**自动配置注册机制**：所有 starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件注册，这是 Spring Boot 3+/4+ 的标准机制（替代了旧版 `spring.factories`）。

### 新服务接入步骤

1. **POM 依赖**：在 `pom.xml` 中声明所需 starter：
   ```xml
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-core</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-mybatis</artifactId></dependency>
   <dependency><groupId>com.omni</groupId><artifactId>omni-common-redis</artifactId></dependency>
   ```
2. **application.yml**：仅需配置数据源和 Redis 连接信息：
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
   ```
3. **启动类**：添加 `@MapperScan("com.omni.xxx.mapper")`
4. **XSS 防护**：实现 `XssConfigProvider` SPI 接口（`omni-common` 自动注册 Filter + Jackson Module）
5. **自动生效**：分页插件、Jackson 时间序列化、CORS 配置、`GlobalExceptionHandler`、`RedisUtils` 工具类均自动装配，无需额外配置

### 覆盖机制

所有 starter 的自动配置 Bean 均使用 `@ConditionalOnMissingBean` 注解，服务可按需覆盖：

- **MybatisPlusInterceptor**：服务定义同名 `mybatisPlusInterceptor` Bean 即可覆盖默认分页配置。典型场景：添加 `DataPermissionInterceptor`（**必须在 `PaginationInnerInterceptor` 之前注册**）
- **RedisTemplate**：服务定义 `@Bean(name = "redisTemplate")` 即可替换默认序列化策略
- **XSS 配置**：`XssAutoConfiguration` 条件依赖 `XssConfigProvider` Bean，不实现 SPI 则 XSS 过滤链不激活

### Redis Starter 互斥约束

`omni-common-redis`（阻塞式）和 `omni-common-redis-reactive`（响应式）**不可在同一服务中混用**：

- **WebFlux 服务**（如 Gateway）：只能依赖 `omni-common-redis-reactive`，阻塞式 Redis 调用会导致 Netty 事件循环线程饥饿
- **Servlet 服务**：使用 `omni-common-redis`（阻塞式），`RedisUtils` 提供同步 API

## Testing (Future Scaffold)

No tests exist yet. When added:

- **Unit tests**: JUnit 5 + Mockito, placed in `src/test/java/`
- **Integration tests**: `@SpringBootTest` with embedded or test containers
- Test class naming: `XxxTest` (unit) or `XxxIntegrationTest` (integration)
- Test method naming: `should_<expected>_when_<condition>`
