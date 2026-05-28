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

## Testing (Future Scaffold)

No tests exist yet. When added:

- **Unit tests**: JUnit 5 + Mockito, placed in `src/test/java/`
- **Integration tests**: `@SpringBootTest` with embedded or test containers
- Test class naming: `XxxTest` (unit) or `XxxIntegrationTest` (integration)
- Test method naming: `should_<expected>_when_<condition>`
