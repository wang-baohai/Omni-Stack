# API 계약

> 본 문서는 프론트엔드와 백엔드 간의 권위 있는 API 계약을 정의합니다. 양측은 반드시 이 구조를 준수해야 합니다. 이탈 시 팀의 명시적 승인이 필요합니다.
> 소셜 로그인 전체 플로우 상세는 [core-flows.kr.md](core-flows.kr.md)를 참조하십시오. 데이터 사전 및 워크플로우 엔드포인트는 각 문서에 상세히 기술되어 있습니다.

---

## 목차

- [1. 응답 래퍼](#1-응답-래퍼)
- [2. 오류 코드 참조표](#2-오류-코드-참조표)
- [3. 페이지네이션 계약](#3-페이지네이션-계약)
- [4. RESTful URL 규격](#4-restful-url-규격)
- [5. Gateway 라우팅 설정](#5-gateway-라우팅-설정)
- [6. 명명 규칙](#6-명명-규칙)
- [7. 시간 형식](#7-시간-형식)
- [8. 요청 헤더 규칙](#8-요청-헤더-규칙)
- [9. 인증 헤더](#9-인증-헤더)
- [10. 소셜 로그인 엔드포인트](#10-소셜-로그인-엔드포인트)
- [11. XSS 설정 관리 엔드포인트](#11-xss-설정-관리-엔드포인트)
- [12. Base 서비스 사전 관리 엔드포인트](#12-base-서비스-사전-관리-엔드포인트)
- [13. API 버전 관리 전략](#13-api-버전-관리-전략)
- [14. Null 의미론](#14-null-의미론)

---

## 1. 응답 래퍼

모든 API 응답은 통일된 `R<T>` 래퍼를 사용합니다.

```json
// 성공
{
  "code": 200,
  "message": "success",
  "data": { ... }
}

// 실패 (비즈니스 오류)
{
  "code": 500,
  "message": "작업 실패"
}

// 실패 (유효성 검사 오류)
{
  "code": 400,
  "message": "username: 사용자 이름은 필수입니다; email: 이메일은 필수입니다"
}
```

### 백엔드 타입: `R<T>`

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

**위치**: `omni-common-core` 모듈, `com.omni.common.core.result.R`.

### 프론트엔드 타입: `ApiResponse<T>`

```typescript
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}
```

**권위 위치**: `src/types/api.ts`(유일한 소스; 다른 파일에서 중복 정의하지 마십시오).

---

## 2. 오류 코드 참조표

### 2.1 시스템 수준 오류 코드

| HTTP 상태 코드 | 비즈니스 코드 | 시나리오 | 발생 조건 | 처리 주체 |
|------------|--------|------|---------|--------|
| 200 | 200 | 성공 | `R.ok(data)` | — |
| 400 | 400 | 파라미터 유효성 검사 실패 | `MethodArgumentNotValidException` / `BindException`이 `GlobalExceptionHandler`에 의해 처리됨 | 프론트엔드에서 `message` 내 필드 오류 표시 |
| 401 | 401 | 미인증 | Gateway `AuthFilter`가 401 JSON 응답 | 프론트엔드 자동으로 로그인 페이지 이동 |
| 403 | 403 | 권한 부족 | `AccessDeniedException` / `AuthorizationDeniedException`이 `GlobalExceptionHandler`에 의해 처리됨 | 프론트엔드에서 "권한 부족" 안내 표시 |
| 200 | 404 | 리소스 없음 | `throw new BusinessException(404, "xxx가 존재하지 않습니다")` | 프론트엔드에서 오류 메시지 표시 |
| 200 | 500 | 비즈니스 예외 | `BusinessException`이 `GlobalExceptionHandler`에 의해 처리됨 | 프론트엔드에서 오류 메시지 표시 |
| 500 | 500 | 알 수 없는 시스템 오류 | 최종 `Exception` 핸들러 | 프론트엔드에서 "서버 내부 오류" 표시 |

### 2.2 비즈니스 수준 오류 코드

| 비즈니스 코드 | 시나리오 | 메시지 예시 |
|--------|------|---------|
| 500 | 유효하지 않은/만료된 인증코드 | "인증코드가 만료되었습니다" |
| 500 | 인증 실패 | "사용자 이름 또는 비밀번호가 올바르지 않습니다" |
| 500 | 계정 비활성화 | "계정이 비활성화되었습니다" |
| 500 | 계정 잠김 | "계정이 잠겼습니다. N분 후 다시 시도해 주십시오" |
| 500 | 테넌트 없음/비활성화 | "테넌트가 존재하지 않거나 비활성화되었습니다" |
| 400 | 고유성 충돌 | "사용자 이름이 이미 존재합니다" / "작업 유형 코드가 이미 존재합니다" |
| 404 | 리소스 없음 | "조직 단위가 존재하지 않습니다" / "사전 데이터가 존재하지 않습니다" |
| 403 | 권한 부족 | "권한이 부족하여 접근이 거부되었습니다" |

### 2.3 Gateway 수준 오류 코드

| HTTP 상태 코드 | 시나리오 | 응답 형식 |
|------------|------|---------|
| 401 | JWT 서명 무효 | `{"code":401,"message":"Invalid JWT signature","data":null}` |
| 401 | JWT 만료 | `{"code":401,"message":"JWT token expired","data":null}` |
| 401 | Token 취소됨 | `{"code":401,"message":"Token has been revoked","data":null}` |
| 401 | Authorization 헤더 누락 | `{"code":401,"message":"Missing Authorization header","data":null}` |

### 2.4 소셜 로그인 오류 코드

| error 파라미터 | 의미 | 발생 조건 |
|------------|------|---------|
| `user_denied` | 사용자가 인증을 거부함 | 서드파티 플랫폼 콜백에서 `error=access_denied`를 전달받음 |
| `invalid_callback` | 콜백 파라미터 누락 | code 또는 state가 비어 있음 |
| `social_login_failed` | 로그인 프로세스 이상 | state 검증 실패, 서드파티 API 오류, 사용자 정보 획득 실패, 사용자 비활성화 |

### 2.5 프론트엔드 오류 처리 흐름

Axios 응답 인터셉터(`src/api/request.ts`)에서 `res.code !== 200`을 확인합니다:
1. `ElMessage.error(res.message)` 오류 알림 표시
2. 코드가 `401`인 경우: `userStore.logout()` 호출 후 `/login`으로 리디렉션
3. `Promise.reject(new Error(res.message))` 반환

**HTTP 상태 코드 처리**:
- HTTP 401(Gateway JWT 검증 실패): Axios `onError` 인터셉터에서 캐치하여 Token을 제거하고 로그인 페이지로 이동
- HTTP 403(권한 부족): `ElMessage.error("권한 부족")` 표시 후 이전 페이지로 이동
- HTTP 400(파라미터 유효성 검사 실패): `GlobalExceptionHandler`가 반환한 필드 수준 오류 메시지 표시

---

## 3. 페이지네이션 계약

### 백엔드 타입: `PageResult<T>`

```java
@Data
public class PageResult<T> implements Serializable {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;   // 자동 계산: (total + size - 1) / size

    public PageResult(List<T> records, long total, long size, long current) { ... }
}
```

### 프론트엔드 타입: `PageResult<T>`

```typescript
interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}
```

**권위 위치**: `src/types/api.ts`.

### 사용 패턴

```java
// 백엔드 Controller
@GetMapping("/list")
public R<PageResult<UserVO>> listUsers(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "10") int size) {
    return R.ok(userService.listUsers(page, size));
}
```

```typescript
// 프론트엔드 API 호출
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### 페이지네이션 파라미터 규칙

| 파라미터 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `page` | int | 1 | 현재 페이지 번호(1부터 시작) |
| `size` | int | 10 | 페이지당 항목 수 |
| `records` | List | — | 현재 페이지 데이터 목록 |
| `total` | long | — | 전체 레코드 수 |
| `pages` | long | — | 전체 페이지 수(자동 계산) |

---

## 4. RESTful URL 규격

| 작업 | HTTP 메서드 | URL 패턴 | 예시 |
|------|-----------|---------|------|
| ID로 조회 | GET | `/{resource}/{id}` | `GET /user/1` |
| 페이지네이션 목록 | GET | `/{resource}/list` | `GET /user/list?page=1&size=10` |
| 생성 | POST | `/{resource}` | `POST /user` |
| 수정 | PUT | `/{resource}/{id}` | `PUT /user/1` |
| 삭제 | DELETE | `/{resource}/{id}` | `DELETE /user/1` |
| 일괄 작업 | POST | `/{resource}/batch` | `POST /user/batch` |

**Gateway 경로 접두사**: 모든 프론트엔드 요청은 `/api/<service>/<resource>`를 사용합니다(예: `/api/auth/user/list`). Gateway가 `/api/<service>`를 제거하고(StripPrefix=2), 다운스트림 서비스는 `/<resource>`를 수신합니다.

**예외**: Base 서비스의 `/api/base/**` 라우팅에는 StripPrefix 필터가 **없으며**, Base 서비스 컨트롤러는 전체 경로를 사용합니다(예: `@RequestMapping("/api/base/dict/type")`).

---

## 5. Gateway 라우팅 설정

### 5.1 로컬 개발 환경 라우팅

Gateway `application.yml`의 라우팅 설정(`spring.cloud.gateway.server.webflux.routes`):

| 라우트 ID | 경로 매칭 | 대상 서비스 | StripPrefix | 설명 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | 없음 | OAuth2 인증 서버 엔드포인트 |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | 없음 | OpenID Connect Discovery 엔드포인트 |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | 2 | Auth 서비스 REST API |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **없음** | Base 서비스(전체 경로 사용) |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **없음** | 스케줄링 작업 관리 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **없음** | 워크플로우 엔진 |

### 5.2 Docker 배포 라우팅

Docker 배포 시 라우팅 설정은 동일하며, 대상 서비스의 URI는 Nacos 서비스 검색을 통해 자동으로 해석됩니다:

| 프론트엔드 요청 | Gateway 라우트 | 다운스트림 수신 경로 | 설명 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` + StripPrefix=2 | `GET /user/list` | Auth 서비스는 접두사 제거 |
| `GET /api/base/dict/type/list` | `lb://omni-base` StripPrefix 없음 | `GET /api/base/dict/type/list` | Base 서비스는 전체 경로 유지 |
| `POST /api/workflow/model` | `lb://omni-workflow` StripPrefix 없음 | `POST /api/workflow/model` | Workflow 서비스는 전체 경로 유지 |
| `GET /api/job/type/list` | `lb://omni-base` StripPrefix 없음 | `GET /api/job/type/list` | Job 라우트는 Base 서비스로 |

### 5.3 AuthFilter 화이트리스트 경로

다음 경로는 JWT 검증을 건너뜁니다(`AuthFilter`가 차단하지 않음):

```
/api/auth/login          — 로그인
/api/auth/register       — 회원가입
/api/auth/captcha        — 인증코드
/api/auth/tenants        — 테넌트 목록
/api/auth/oauth2/        — 소셜 로그인
/actuator/               — 헬스 체크
/oauth2/                 — OAuth2 엔드포인트
/.well-known/            — OIDC Discovery
/login                   — Spring Security 로그인
/error                   — 오류 페이지
```

---

## 6. 명명 규칙

### 요청/응답 DTO

| 타입 | 접미사 | 예시 |
|------|------|------|
| 생성 요청 | `CreateXxxRequest` | `CreateUserRequest` |
| 수정 요청 | `UpdateXxxRequest` | `UpdateUserRequest` |
| 뷰 객체 | `XxxVO` | `UserVO` |
| 조회 파라미터 | `XxxQuery` | `UserQuery` |

DTO는 Controller의 정적 내부 클래스(단순한 경우) 또는 독립 파일(복잡한 경우)로 정의할 수 있습니다.

### 필드 명명

- Java 필드: `lowerCamelCase`(예: `createTime`, `userName`)
- JSON 직렬화: `lowerCamelCase`(Java 필드 이름을 그대로 매칭)
- URL 경로 세그먼트: `kebab-case` 또는 단일 단어(예: `/user/list`, `/user/getAllUsers`가 아님)

---

## 7. 시간 형식

`JacksonConfig.java`에서 설정:

| Java 타입 | JSON 형식 | 예시 |
|-----------|----------|------|
| `LocalDateTime` | `yyyy-MM-dd HH:mm:ss` | `2026-05-28 14:30:00` |
| `LocalDate` | `yyyy-MM-dd` | `2026-05-28` |

타임스탬프는 숫자가 아닌 문자열로 직렬화됩니다(`WRITE_DATES_AS_TIMESTAMPS` 비활성화됨).

**설정 위치**: `omni-common` 모듈의 `JacksonConfig`에서 `AutoConfiguration.imports`를 통해 자동으로 적용됩니다. `omni-common`에 의존하는 모든 서비스는 일관된 시간 형식을 자동으로 획득합니다.

---

## 8. 요청 헤더 규칙

### 8.1 Gateway가 주입하는 요청 헤더

Gateway의 `AuthFilter`는 JWT 검증 성공 후 다운스트림 요청에 다음 헤더를 주입합니다:

| 요청 헤더 | 타입 | 설명 | 예시 |
|--------|------|------|------|
| `X-User-Id` | String | 사용자 ID | `"1"` |
| `X-User-Name` | String | 사용자 이름 | `"admin"` |
| `X-Tenant-Id` | String | 테넌트 ID | `"1"` |
| `X-User-Roles` | String | 쉼표로 구분된 역할 코드 | `"SUPER_ADMIN,DEPT_LEADER"` |
| `X-User-Scopes` | String | 공백/쉼표로 구분된 권한 코드 | `"dict:type:list dict:data:create"` |

### 8.2 프론트엔드가 전송하는 요청 헤더

| 요청 헤더 | 출처 | 설명 |
|--------|------|------|
| `Authorization: Bearer <JWT>` | Axios 인터셉터 자동 주입 | `useUserStore()`에서 Token 획득 |
| `X-Tenant-Id` | Axios 인터셉터 자동 주입 | `useUserStore()`에서 테넌트 ID 획득 |
| `Content-Type: application/json` | Axios 기본값 | JSON 요청 본문 |

### 8.3 보안 응답 헤더(Gateway 주입)

`SecurityHeadersFilter`(WebFlux WebFilter)는 게이트웨이를 거치는 모든 응답에 다음을 추가합니다:

| 응답 헤더 | 값 | 용도 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | 브라우저 MIME 타입 스니핑 방지 |
| `X-Frame-Options` | `SAMEORIGIN` | 클릭재킹 방지 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referer 헤더 유출 제어 |

---

## 9. 인증 헤더

```
Authorization: Bearer <token>
```

- Axios 요청 인터셉터(`src/api/request.ts`)에서 `useUserStore()`의 Token을 사용하여 설정
- `omni-gateway`의 `AuthFilter`에서 검증(JWT RS256 서명 검증 + claims 추출 + identity 헤더 주입)
- 공개 경로는 인증 면제: `/api/auth/**`, `/actuator/**`, `/favicon.ico`

---

## 10. 소셜 로그인 엔드포인트

소셜 로그인 엔드포인트는 HTTP 302 리디렉션을 반환합니다(표준 `R<T>` 응답이 아님). 프론트엔드가 `window.location.href`를 통해 브라우저 내비게이션을 트리거하기 때문입니다.

| HTTP 메서드 | URL | 설명 |
|-----------|-----|------|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | 서드파티 로그인 시작, 302 리디렉션으로 서드파티 인증 페이지로 이동 |
| GET | `/api/auth/oauth2/{provider}/callback?code=XXX&state=YYY` | 서드파티 콜백 처리, 성공 시 302 리디렉션으로 프론트엔드 콜백 페이지로 이동 |

### 로그인 시작

```
# GitHub
GET /api/auth/oauth2/github?tenant_id=1
→ 302 Location: https://github.com/login/oauth/authorize?client_id=...&redirect_uri=...&scope=...&state=...

# Google
GET /api/auth/oauth2/google?tenant_id=1
→ 302 Location: https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&response_type=code&scope=openid+profile+email&state=...

# Gitee
GET /api/auth/oauth2/gitee?tenant_id=1
→ 302 Location: https://gitee.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&scope=user_info&state=...
```

- `{provider}`는 `github`, `google`, `gitee`를 지원합니다
- `tenant_id`는 필수이며, 로그인 대상 테넌트를 지정합니다
- State 파라미터에는 HMAC-SHA256 서명(`tenantId|timestamp|hmac`)이 포함되어 CSRF 공격을 방지합니다

### 콜백 처리

```
# GitHub/Google/Gitee 콜백
GET /api/auth/oauth2/{provider}/callback?code=XXX&state=YYY

→ 성공: 302 Location: /callback#token=<JWT>&username=<username>
→ 실패: 302 Location: /login?error=<error_code>&message=<message>
```

### Docker 배포 시 OAuth2 콜백 URL 설정

Docker 배포 시 소셜 로그인의 `redirect_uri`는 **호스트 머신에서 접근 가능한 URL**을 사용해야 합니다:

| 배포 환경 | redirect_uri 예시 |
|---------|------------------|
| 로컬 개발 | `http://localhost:8100/api/auth/oauth2/github/callback` |
| Docker 배포 | `http://<호스트IP>:8100/api/auth/oauth2/github/callback` |
| 프로덕션 환경 | `https://your-domain.com/api/auth/oauth2/github/callback` |

> **참고**: Docker 배포에서 Auth 서비스 컨테이너 내부 포트는 8080이지만, OAuth2 콜백 URL은 반드시 호스트 머신 매핑 포트 8100을 사용해야 합니다(서드파티 플랫폼이 호스트 머신의 공인/사설 네트워크 도달 가능 주소로 콜백해야 하기 때문입니다).

### 프론트엔드 콜백 페이지

`/callback` 페이지(`src/views/callback/index.vue`)의 역할:
1. URL fragment에서 `token`과 `username` 파싱
2. `localStorage`에 저장(`useUserStore`를 통해)
3. 대시보드로 리디렉션

> 전체 플로우 시퀀스 다이어그램은 [core-flows.kr.md](core-flows.kr.md) Flow 4를 참조하십시오.

---

## 11. XSS 설정 관리 엔드포인트

Base path: `/api/auth/xss-config`(Gateway StripPrefix=2 → 다운스트림 `/xss-config/...`)

### 현재 XSS 설정 조회

```
GET /api/auth/xss-config/settings
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "enabled": false,
    "rules": [
      { "id": 1, "ruleType": "HTML_TAG", "pattern": "script" }
    ]
  }
}
```

### 전역 스위치 전환

```
PUT /api/auth/xss-config/toggle
Authorization: Bearer <token>
X-Tenant-Id: 1

@PreAuthorize("hasAuthority('system:xssconfig:update')")
Response 200: { "code": 200, "message": "success" }
```

### 규칙 CRUD

| HTTP 메서드 | URL | 권한 코드 | 설명 |
|-----------|-----|--------|------|
| GET | `/api/auth/xss-config/rules/list?page=1&size=10` | `system:xssconfig:list` | 페이지네이션 목록 |
| POST | `/api/auth/xss-config/rules` | `system:xssconfig:create` | 규칙 생성 |
| PUT | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:update` | 규칙 수정 |
| DELETE | `/api/auth/xss-config/rules/{id}` | `system:xssconfig:delete` | 규칙 삭제 |
| PUT | `/api/auth/xss-config/rules/{id}/toggle` | `system:xssconfig:update` | 규칙 활성화 상태 전환 |

**ruleType 열거값**: `HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 권한 코드

| 권한 코드 | 설명 |
|--------|------|
| `system:xssconfig:list` | XSS 설정 및 규칙 조회 |
| `system:xssconfig:update` | 전역 스위치 전환, 규칙 수정, 규칙 상태 전환 |
| `system:xssconfig:create` | 규칙 생성 |
| `system:xssconfig:delete` | 규칙 삭제 |

---

## 12. Base 서비스 사전 관리 엔드포인트

Base 서비스(`omni-base :8101`)는 데이터 사전 관리를 제공하며, 「타입 + 데이터」 2단계 구조를 사용합니다.

**라우팅 설명**: Gateway 라우트 `Path=/api/base/**`에는 StripPrefix 필터가 **없으며**, Base 서비스 컨트롤러는 전체 경로를 사용합니다(예: `@RequestMapping("/api/base/dict/type")`).

### Dictionary Type 엔드포인트

Base path: `/api/base/dict/type`

| HTTP 메서드 | URL | 권한 코드 | 설명 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/type/list?page=1&size=10&typeCode=&typeName=&status=` | `dict:type:list` | 페이지네이션 목록, 필터링 지원 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | ID로 조회 |
| POST | `/api/base/dict/type` | `dict:type:create` | 생성(테넌트 내 typeCode 고유성 검증) |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | 수정(부분 수정) |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 삭제(연관 데이터 연쇄 삭제) |
| PUT | `/api/base/dict/type/{id}/status` | `dict:type:update` | 활성화/비활성화 전환 |

**요청 예시**:

```
GET /api/base/dict/type/list?page=1&size=10
Authorization: Bearer <token>
X-Tenant-Id: 1

Response 200:
{
  "code": 200,
  "data": {
    "records": [
      { "id": 1, "typeCode": "sys_user_gender", "typeName": "사용자 성별", "status": 1, "sort": 0 }
    ],
    "total": 3,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### Dictionary Data 엔드포인트

Base path: `/api/base/dict/data`

| HTTP 메서드 | URL | 권한 코드 | 설명 |
|-----------|-----|--------|------|
| GET | `/api/base/dict/data/list?typeCode=sys_user_gender&page=1&size=10` | `dict:data:list` | typeCode로 페이지네이션 조회 |
| POST | `/api/base/dict/data` | `dict:data:create` | 생성(부모 타입 존재 검증) |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | 수정(부분 수정) |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 단일 항목 삭제 |
| POST | `/api/base/dict/data/refresh-cache` | `dict:data:refresh` | Redis 캐시 수동 새로고침 |

**요청 예시**:

```
POST /api/base/dict/data
Authorization: Bearer <token>
X-Tenant-Id: 1
Content-Type: application/json

{
  "typeCode": "sys_user_gender",
  "dictValue": "3",
  "dictLabel": "비공개",
  "tagType": "warning",
  "sort": 3
}

@PreAuthorize("hasAuthority('dict:data:create')")
Response 200: { "code": 200, "data": { "id": 8, ... } }
```

### 사전 권한 코드

| 권한 코드 | 설명 |
|--------|------|
| `dict:type:list` | 사전 타입 목록 조회 |
| `dict:type:create` | 사전 타입 생성 |
| `dict:type:update` | 사전 타입 수정/상태 전환 |
| `dict:type:delete` | 사전 타입 삭제(연쇄) |
| `dict:data:list` | 사전 데이터 목록 조회 |
| `dict:data:create` | 사전 데이터 생성 |
| `dict:data:update` | 사전 데이터 수정 |
| `dict:data:delete` | 사전 데이터 삭제 |
| `dict:data:refresh` | 사전 캐시 수동 새로고침 |

### 테넌트 격리

모든 목록 조회 및 생성 작업은 `X-Tenant-Id` 요청 헤더를 요구합니다(프론트엔드에서 JWT Token에서 추출, Gateway가 주입). 데이터는 SQL 쿼리 수준에서 `tenant_id` 기준으로 격리됩니다. 사전 타입 고유성 제약 조건 범위는 `(tenant_id, type_code)`입니다.

---

## 13. API 버전 관리 전략

### 현재 결정

스캐폴딩 단계에서는 URL 버전 번호를 사용하지 않습니다. API가 안정화되고 여러 소비자가 존재할 때 접두사 버전 관리를 도입합니다.

### 향후 발전 경로

| 단계 | 버전 전략 | URL 예시 |
|------|---------|---------|
| **현재(스캐폴딩)** | 버전 번호 없음 | `/api/auth/user/list` |
| **V1(API 안정화 후)** | URL 접두사 버전 | `/api/v1/auth/user/list` |
| **V2(Breaking Change)** | URL 접두사 버전 | `/api/v2/auth/user/list` |

**버전 규칙**:
- 신규 필드 추가(하위 호환): 버전 번호 변경 불필요
- 필드 삭제/이름 변경: 새 버전 필요
- 요청/응답 구조 변경: 새 버전 필요
- 이전 버전은 최소 6개월간 유지보수

---

## 14. Null 의미론

- `null` 필드는 JSON 출력에 포함됩니다(생략하지 않음)
- 빈 컬렉션은 `null`이 아닌 `[]`로 반환됩니다
- 선택적 단일 값은 빈 문자열이 아닌 `null`로 부재를 표시합니다
