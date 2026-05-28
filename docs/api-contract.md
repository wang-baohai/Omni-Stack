# API Contract

This document defines the definitive API contract between frontend and backend. Both sides must conform to these structures. Any deviation requires explicit team approval.

## Response Envelope

All API responses use the unified `R<T>` wrapper.

```json
// Success
{
  "code": 200,
  "message": "success",
  "data": { ... }
}

// Failure (business error)
{
  "code": 500,
  "message": "Operation failed"
}

// Failure (validation error)
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

### Backend Type: `R<T>`

```java
@Data
public class R<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok(T data) { ... }
    public static <T> R<T> fail(String message) { ... }
    public static <T> R<T> fail(int code, String message) { ... }
}
```

### Frontend Type: `ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**Canonical location**: `src/types/api.ts` (single source of truth; do not duplicate in other files).

## Error Code Mapping

| HTTP Status | Business Code | Scenario | Trigger |
|-------------|---------------|----------|---------|
| 200 | 200 | Success | `R.ok(data)` |
| 200 | 400 | Validation failure | `MethodArgumentNotValidException` / `BindException` caught by `GlobalExceptionHandler` |
| 200 | 401 | Authentication required | Future: `AuthFilter` returns 401 |
| 200 | 500 | Business exception | `BusinessException` caught by `GlobalExceptionHandler` |
| 200 | 500 | Unexpected error | Catch-all `Exception` handler |

**Note**: The HTTP status is always 200 for business responses (the error code is in the `code` field). The exceptions are validation errors which return HTTP 400 via `@ResponseStatus`.

### Frontend Error Handling

The Axios response interceptor in `src/api/request.ts` checks `res.code !== 200`:
- Shows `ElMessage.error(res.message)`
- On code `401`: calls `userStore.logout()` and redirects to `/login`
- Returns `Promise.reject(new Error(res.message))`

## Pagination Contract

### Backend Type: `PageResult<T>`

```java
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;   // auto-calculated: (total + size - 1) / size

    public PageResult(List<T> records, long total, long size, long current) { ... }
}
```

### Frontend Type: `PageResult<T>`

```typescript
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

**Canonical location**: `src/types/api.ts`.

### Usage Pattern

```java
// Backend controller
@GetMapping("/list")
public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
    return R.ok(userService.listUsers(page, size));
}
```

```typescript
// Frontend API call
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/business/user/list?page=${page}&size=${size}`,
  )
}
```

## RESTful URL Conventions

| Operation | HTTP Method | URL Pattern | Example |
|-----------|-------------|-------------|---------|
| Get by ID | GET | `/{resource}/{id}` | `GET /user/1` |
| List (paginated) | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| Create | POST | `/{resource}` | `POST /user` |
| Update | PUT | `/{resource}/{id}` | `PUT /user/1` |
| Delete | DELETE | `/{resource}/{id}` | `DELETE /user/1` |

**Gateway path prefix**: All frontend requests use `/api/business/<resource>`. The Gateway strips `/api/business` (StripPrefix=2), so the Business service receives `/<resource>`.

## Naming Conventions

### Request/Response DTOs

| Type | Suffix | Example |
|------|--------|---------|
| Create request | `CreateXxxRequest` | `CreateUserRequest` |
| Update request | `UpdateXxxRequest` | `UpdateUserRequest` |
| View object | `XxxVO` | `UserVO` |
| Query parameters | `XxxQuery` | `UserQuery` |

DTOs can be defined as static inner classes of the Controller (for simple cases) or as standalone files (for complex cases).

### Field Naming

- Java fields: `lowerCamelCase` (e.g., `createTime`, `userName`)
- JSON serialization: `lowerCamelCase` (matches Java field names directly)
- URL path segments: `kebab-case` or single words (e.g., `/user/list`, not `/user/getAllUsers`)

## Time Format

Configured in `JacksonConfig.java`:

| Java Type | JSON Format | Example |
|-----------|-------------|---------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

Timestamps are serialized as strings, not numeric timestamps (`WRITE_DATES_AS_TIMESTAMPS` is disabled).

## Authentication Header

```
Authorization: Bearer <token>
```

- Set by the Axios request interceptor in `src/api/request.ts` using the token from `useUserStore()`
- Checked by `AuthFilter` in `omni-gateway` (currently a stub — passes all requests through)
- Public paths exempt from auth: `/api/auth/**`, `/actuator/**`, `/favicon.ico`

## Versioning Strategy

**Current decision**: No URL-based versioning during the scaffolding phase. When the API stabilizes and multiple consumers exist, introduce prefix versioning: `/api/v1/business/user/list`.

## Null Semantics

- `null` fields are included in JSON output (not suppressed)
- Empty collections should be returned as `[]`, not `null`
- Optional single values should use `null` to indicate absence, not empty strings
