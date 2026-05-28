# Core Business Flows

This document traces the essential user-facing flows end-to-end, from browser interaction to backend processing and back. Use this as a reference when implementing or modifying features.

## Flow 1: User Login

### Sequence

```
Browser                    Frontend                     Gateway :8090            Business :8081
  |                           |                              |                        |
  |  1. Enter credentials     |                              |                        |
  |  2. Click "Login" ------->|                              |                        |
  |                           |  3. Validate form            |                        |
  |                           |     (Element Plus rules)     |                        |
  |                           |                              |                        |
  |                           |  4. Mock login (TODO:        |                        |
  |                           |     replace with API call)   |                        |
  |                           |     token = 'mock-token-xxx' |                        |
  |                           |                              |                        |
  |                           |  5. userStore.setToken()     |                        |
  |                           |     userStore.setUsername()  |                        |
  |                           |                              |                        |
  |  6. Redirect to dashboard |                              |                        |
  |<--------------------------|                              |                        |
  |                           |                              |                        |
  |  7. Navigate to /dashboard                               |                        |
  |                           |  8. Router guard checks      |                        |
  |                           |     requiresAuth + token OK   |                        |
  |                           |                              |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| Form UI | `src/views/login/index.vue` | Element Plus form with `reactive()` model and `FormRules` validation |
| Form validation | `src/views/login/index.vue` | Required fields: `username`, `password`; triggered on blur |
| Login handler | `src/views/login/index.vue` | `handleLogin()`: validates, sets loading, calls mock login |
| Token storage | `src/stores/user.ts` | `setToken()` persists to `localStorage` |
| Route redirect | `src/router/index.ts` | Redirects to `route.query.redirect` or `/` |
| Auth guard | `src/router/index.ts` | `beforeEach`: checks `requiresAuth !== false` and `userStore.token` |
| Request auth header | `src/api/request.ts` | Axios request interceptor attaches `Authorization: Bearer <token>` |
| Gateway filter | `AuthFilter.java` | Checks token on non-public paths (currently stub: pass-through) |

### Current Status

- **Login is mocked**: No actual backend API call. Token is generated client-side as `'mock-token-' + Date.now()`
- **TODO**: `[frontend] Replace with actual login API call` in `login/index.vue`
- **TODO**: `[gateway] Return 401 response or implement token validation` in `AuthFilter.java`

---

## Flow 2: List Query (Pagination)

### Sequence

```
Browser                    Frontend                     Gateway :8090            Business :8081
  |                           |                              |                        |
  |  1. View list page        |                              |                        |
  |                           |  2. Call listUsers(1, 10)    |                        |
  |                           |     from api/user.ts         |                        |
  |                           |                              |                        |
  |                           |  3. Axios GET                |                        |
  |                           |  /api/business/user/list     |                        |
  |                           |  ?page=1&size=10 ----------->|                        |
  |                           |                              |                        |
  |                           |                              |  4. Route match:       |
  |                           |                              |     Path=/api/business/**
  |                           |                              |     StripPrefix=2      |
  |                           |                              |                        |
  |                           |                              |  5. lb://omni-business |
  |                           |                              |  GET /user/list ------->|
  |                           |                              |                        |
  |                           |                              |                        |  6. UserController
  |                           |                              |                        |     .listUsers()
  |                           |                              |                        |        |
  |                           |                              |                        |  7. UserService
  |                           |                              |                        |     .listUsers()
  |                           |                              |                        |     returns PageResult
  |                           |                              |                        |        |
  |                           |                              |  8. R.ok(pageResult) <-|
  |                           |                              |<-----------------------|
  |                           |                              |                        |
  |                           |  9. Response interceptor     |                        |
  |                           |     checks code === 200      |                        |
  |                           |                              |                        |
  |  10. Render table <-------|                              |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| API function | `src/api/user.ts` | `listUsers(page, size)` -> `GET /business/user/list` |
| Axios instance | `src/api/request.ts` | Base URL `/api`, timeout 15s, auth header |
| Vite proxy | `vite.config.ts` | `/api` -> `http://localhost:8090` |
| Gateway route | `application.yml` (gateway) | `Path=/api/business/**` -> `lb://omni-business`, `StripPrefix=2` |
| Load balancer | Spring Cloud LoadBalancer | Resolves `omni-business` via Nacos service registry |
| Controller | `UserController.java` | `@GetMapping("/list")` -> calls `UserService.listUsers()` |
| Service | `UserService.java` / `UserServiceImpl.java` | Returns `PageResult<Map<String, Object>>` (stub data) |
| Response wrapper | `R.java` | `R.ok(pageResult)` -> `{ code: 200, message: "success", data: {...} }` |
| Response interceptor | `src/api/request.ts` | Checks `code === 200`, rejects on error |

### Response Shape

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      { "id": 1, "username": "demo", "email": "demo@example.com" }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### Current Status

- **Stub data**: `UserService` returns hardcoded `HashMap` data, no database integration
- **TODO**: `[business] Replace with actual database query` in `UserServiceImpl.java`

---

## Flow 3: Form Submission (Create)

### Sequence

```
Browser                    Frontend                     Gateway :8090            Business :8081
  |                           |                              |                        |
  |  1. Fill form             |                              |                        |
  |  2. Click "Submit" ------>|                              |                        |
  |                           |  3. Client-side validation   |                        |
  |                           |     (Element Plus rules)     |                        |
  |                           |                              |                        |
  |                           |  4. Axios POST               |                        |
  |                           |  /api/business/user          |                        |
  |                           |  { username, email } ------->|                        |
  |                           |                              |                        |
  |                           |                              |  5. StripPrefix=2      |
  |                           |                              |  POST /user ----------->|
  |                           |                              |                        |
  |                           |                              |                        |  6. UserController
  |                           |                              |                        |     @Valid CreateUserRequest
  |                           |                              |                        |        |
  |                           |                              |                        |  [A] Validation OK:
  |                           |                              |                        |     UserService.createUser()
  |                           |                              |                        |     R.ok()
  |                           |                              |                        |        |
  |                           |                              |                        |  [B] Validation FAIL:
  |                           |                              |                        |     MethodArgumentNotValidException
  |                           |                              |                        |     -> GlobalExceptionHandler
  |                           |                              |                        |     -> R.fail(400, fieldErrors)
  |                           |                              |                        |        |
  |                           |                              |  7. R<Void> <----------|
  |                           |                              |<-----------------------|
  |                           |                              |                        |
  |                           |  8. Response interceptor     |                        |
  |                           |     [A] code=200: success    |                        |
  |                           |     [B] code=400: show error |                        |
  |                           |                              |                        |
  |  9. Show result <---------|                              |                        |
```

### Key Components

| Step | File | Logic |
|------|------|-------|
| Form validation | Frontend view | Element Plus `FormRules`: required fields, format checks |
| API call | `src/api/user.ts` | `createUser(data)` -> `POST /business/user` |
| Gateway routing | `application.yml` (gateway) | Same as Flow 2 |
| Controller validation | `UserController.java` | `@Valid @RequestBody CreateUserRequest` triggers Jakarta Validation |
| Validation failure | `GlobalExceptionHandler.java` | Catches `MethodArgumentNotValidException`, aggregates field errors into message |
| Service call | `UserServiceImpl.java` | `createUser(username, email)` (stub: no-op) |
| Success response | `R.java` | `R.ok()` -> `{ code: 200, message: "success", data: null }` |
| Error display | `src/api/request.ts` | Interceptor shows `ElMessage.error(res.message)` |

### Validation Error Response

```json
{
  "code": 400,
  "message": "username: Username is required; email: Email is required"
}
```

### Current Status

- **Create is a no-op**: `UserServiceImpl.createUser()` does nothing (no database)
- **TODO**: `[business] Replace with actual database insert` in `UserServiceImpl.java`
