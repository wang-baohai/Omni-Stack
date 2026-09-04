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
- [15. SRM MVP 계약](#15-srm-mvp-계약)
- [16. Workflow 크로스 서비스 계약](#16-workflow-크로스-서비스-계약)
- [17. Procurement MVP 계약](#17-procurement-mvp-계약)
- [18. Asset MVP 계약](#18-asset-mvp-계약)

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
| 200 | 409 | 상태/동시성 충돌 | 낙관적 잠금 버전 불일치 또는 상태 머신이 전환을 거부 | 프론트엔드가 데이터를 새로 고친 후 재시도 안내 |
| 200 | 503 | 하위 의존 서비스 사용 불가 | CRM이 Auth 데이터 범위 API를 호출하여 fail-close | 프론트엔드가 서비스 일시 중단 안내. 권한 초과 데이터로 격하하지 않음 |
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
| 409 | 낙관적 잠금 또는 상태 충돌 | "데이터가 다른 사용자에 의해 수정되었습니다. 새로 고친 후 재시도하십시오" |
| 503 | 필수 의존 서비스 사용 불가 | "인증/권한 서비스를 일시적으로 사용할 수 없습니다" |

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

**Gateway 경로 접두사**: 모든 프론트엔드 요청은 `/api/<service>/<resource>`를 사용합니다(예: `/api/auth/user/list`). 현재 Gateway는 Auth·Base·Workflow·CRM·SRM·Procurement·Asset 등 비즈니스 라우팅에 `StripPrefix`를 사용하지 않으며, 다운스트림 Controller가 전체 `/api/**` 경로를 선언하고 수신합니다.

---

## 5. Gateway 라우팅 설정

### 5.1 로컬 개발 환경 라우팅

Gateway `application.yml`의 라우팅 설정(`spring.cloud.gateway.server.webflux.routes`):

| 라우트 ID | 경로 매칭 | 대상 서비스 | StripPrefix | 설명 |
|---------|---------|---------|-------------|------|
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` | 없음 | OAuth2 인증 서버 엔드포인트 |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` | 없음 | OpenID Connect Discovery 엔드포인트 |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` | **없음** | Auth 서비스 REST API(전체 경로 사용) |
| `omni-base` | `/api/base/**` | `lb://omni-base` | **없음** | Base 서비스(전체 경로 사용) |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` | **없음** | 스케줄링 작업 관리 |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` | **없음** | 워크플로우 엔진 |

### 5.2 Docker 배포 라우팅

Docker 배포 시 라우팅 설정은 동일하며, 대상 서비스의 URI는 Nacos 서비스 검색을 통해 자동으로 해석됩니다:

| 프론트엔드 요청 | Gateway 라우트 | 다운스트림 수신 경로 | 설명 |
|---------|-------------|-------------|------|
| `GET /api/auth/user/list` | `lb://omni-auth` StripPrefix 없음 | `GET /api/auth/user/list` | Auth 서비스는 전체 경로 유지 |
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

### 8.3 내부 서비스 요청 헤더

모든 서비스 간 인터페이스는 `/api/internal/**` 아래에 두며, 공유 토큰 인증을 사용하고 엔드유저 JWT를 사용하지 않습니다:

| 헤더 | 필수 | 설명 |
|--------|------|------|
| `X-Internal-Token` | 예 | 서비스 간 공유 토큰. `InternalApiAuthFilter`가 검증 |
| `X-Tenant-Id` | 예 | 현재 비즈니스 테넌트. body/query의 `tenantId`와 일치해야 함 |
| `Content-Type: application/json` | JSON 요청 시 필수 | JSON 요청 본문 |

`InternalApiAuthFilter`는 컨테이너 수준의 전면 필터로서 `/api/internal/**`을 일괄 보호합니다. 서비스 보안 체인이 Gateway 사용자 신원을 다시 요구해서는 안 됩니다. 토큰이 누락되거나 불일치하면 HTTP 401을 반환하고, 서버 측에 토큰이 설정되지 않았으면 fail-closed로 HTTP 503을 반환하며, 헤더와 body/query의 테넌트가 불일치하면 비즈니스 코드 403을 반환합니다. 내부 경로는 `X-Gateway-Forwarded`나 사용자 권한 헤더에 의존해서는 안 됩니다.

### 8.4 보안 응답 헤더(Gateway 주입)

`SecurityHeadersFilter`(WebFlux WebFilter)는 게이트웨이를 거치는 모든 응답에 다음을 추가합니다:

| 응답 헤더 | 값 | 용도 |
|--------|-----|------|
| `X-Content-Type-Options` | `nosniff` | 브라우저 MIME 타입 스니핑 방지 |
| `X-Frame-Options` | `DENY` | 페이지가 iframe에 중첩되는 것을 금지하여 클릭재킹 방지 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Referer 헤더 유출 제어 |
| `X-Trace-Id` | 32자 소문자 16진수 문자열 | Gateway·Servlet·Feign과 오류 피드백을 연관 |

Gateway는 항상 새로운 `X-Trace-Id`를 생성하며 공용망 클라이언트가 제공한 같은 이름의 헤더를 신뢰하지 않습니다. 다운스트림 Servlet 서비스는 유효한 값을 MDC와 응답 헤더에 기록하고, 공통 Feign 인터셉터는 내부 호출로 계속 전파합니다. 프론트엔드 오류 패널은 응답의 traceId를 표시할 수 있으며, 로그 조사 시에는 같은 값으로 전체 호출 체인을 검색하는 것이 우선입니다.

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

Base path: `/api/auth/xss-config`(Gateway는 접두사를 제거하지 않으며 다운스트림이 전체 경로를 유지)

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

### MQ 전송 런타임 상태

`GET /api/base/mq-message/runtime`은 `base:mqmessage:list` 권한을 요구하며, 현재 Outbox와 백그라운드 전송 능력을 반환합니다:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "outboxWriteEnabled": true,
    "deliveryEnabled": false,
    "mode": "OUTBOX_ONLY"
  }
}
```

`OUTBOX_ONLY`는 비즈니스 트랜잭션은 여전히 로컬 Outbox에 기록하지만 MQ relay/XXL-JOB이 실행되지 않음을 의미합니다. 프론트엔드는 성능 저하 안내를 표시하고 재전송 작업을 비활성화해야 합니다. `FULL`은 쓰기와 비동기 전송이 모두 활성화되었음을 의미합니다.

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

---

## 15. SRM MVP 계약

### 15.1 공급업체 및 하위 리소스

- 관리측 공급업체 수명주기 명령은 모두 `version` 을 휴대합니다; 블랙리스트 복구는
  `POST /api/srm/supplier/{id}/restore-from-blacklist` 를 사용합니다.
- 연락처, 자격, 은행 계좌 경로의 `supplierId` 는 하위 리소스의 실제 귀속과 일치해야 합니다; 불일치는 일괄 404 를 반환합니다.
- `creditCode` 는 테넌트 내 유일; 페이지네이션 `size` 는 최대 100.
- 공급업체 360 은 `GET /api/srm/supplier/{id}/overview` 를 사용하며, 반환 내용은 여전히 호출자의 하위 리소스 권한과 PII 권한으로 트리밍됩니다.

### 15.2 포털 등록

`POST /api/srm/portal/enroll` 은 Gateway 가 주입한 tenant/user 신원만 허용하며, 요청은 tenantId 나 userId 를 휴대해서는 안 됩니다:

```json
{
  "requestId": "client-generated-uuid",
  "inviteToken": "raw-token-returned-once",
  "name": "예시 공급업체 주식회사",
  "creditCode": "91320000EXAMPLE"
}
```

응답의 상태는 `PENDING_ROLE_ASSIGN`, `ROLE_ASSIGN_FAILED`, `COMPLETED`, `CANCELLED` 만 사용합니다.
현재 사용자는 `GET /api/srm/portal/enrollment` 로 상태를 조회할 수 있으며, 실패 후
`POST /api/srm/portal/enrollment/retry` 로 멱등 재시도를 호출할 수 있습니다. 역할 할당이 완료되기 전에는 PortalUser 를 생성하지 않고 기업 자료 인터페이스도 개방하지 않습니다.

### 15.3 평가 및 리스크

- `GET /api/srm/evaluation/template/default/dimensions` 는 현재 테넌트의 기본 템플릿과 유효 차원을 반환합니다; 프론트엔드는 데이터베이스 ID 를 하드코딩해서는 안 됩니다.
- 점수 범위는 1–5 이며, 기본 템플릿의 모든 차원을 커버하고 중복되어서는 안 됩니다.
- `GET /api/srm/risk/list` 는 공급업체별 최신 평가로 집계된 리스크 요약 페이지네이션을 반환하며, `riskLevel` 로 필터링할 수 있습니다.
- `GET /api/srm/supplier/{id}/risk` 는 `indicators/latestAssessment/history` 집계 뷰를 반환합니다.
- 리스크 지표 업데이트는 `version` 을 휴대; 종합 등급이 비 RED 에서 RED 로 바뀔 때만 `srm.risk.level-changed.v1` 을 생성합니다.

### 15.4 내부 공급업체 요약

후속 Procurement/Asset 은 `X-Internal-Token` 과 `X-Tenant-Id` 를 동시에 휴대할 때만 호출할 수 있습니다:

- `GET /api/internal/supplier/{id}?tenantId={tenantId}`
- `GET /api/internal/supplier/search?tenantId={tenantId}&status=APPROVED&categoryCode={code}&limit=50`
- `POST /api/internal/supplier/batch`

GET 의 query tenantId, batch 의 body tenantId 는 `X-Tenant-Id` 와 완전히 일치해야 하며, 그렇지 않으면 403 을 반환합니다. batch 요청 본문은:

```json
{
  "tenantId": 1,
  "supplierIds": [101, 102, 101]
}
```

`supplierIds` 는 1–100 개의 양의 정수를 포함해야 합니다; 서버측은 첫 출현 순서로 중복 제거하고 반환 순서를 유지하며, 존재하지 않거나 삭제된 ID 는 결과에서 생략하고 단일 항목 누락으로 전체 요청을 404 로 만들지 않습니다. 응답은 공급업체 `id/supplierNo/name/status/levelCode/categoryCode` 만 포함하고 연락처, 은행 계좌 또는 기타 PII 는 반환하지 않습니다.

### 15.5 공급업체 포털 견적

포털 엔드포인트는 `srm:portal:quotation`, `SUPPLIER` 역할을 필요로 하며, 현재 사용자는 유효한
`srm_supplier_portal_user` 연관이 존재해야 합니다. 이 권한 노드는 `SUPPLIER` 와 플랫폼 규칙으로 전체 권한 트리를 가진 `SUPER_ADMIN` 에게만 부여됩니다; SUPER_ADMIN 역할만으로는 포털 신원 조건을 충족하지 못해 공급업체를 대신해 견적할 수 없습니다:

- `GET /api/srm/portal/quotation/invitations`
- `GET /api/srm/portal/quotation/invitations/{rfqId}`
- `POST /api/srm/portal/quotation`

초대 목록은 `R<List<RfqInvitationVO>>` 를 사용하며, 단일 항목은 최소한 다음을 포함합니다:

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "사무용 컴퓨터 조달 견적 요청",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "invitedTime": "2026-07-21 10:00:00",
  "quotationId": 501,
  "quotationVersion": 2,
  "quotationStatus": "SUBMITTED",
  "totalAmount": "128000.0000",
  "validUntil": "2026-08-31 18:00:00"
}
```

초대 상세는 위 필드를 기반으로 RFQ 행 스냅샷을 반환하며, 기존 견적이 있을 때 `currentQuotation` 을 반환합니다:

```json
{
  "rfqId": 1001,
  "rfqNo": "RFQ-202607-0001",
  "title": "사무용 컴퓨터 조달 견적 요청",
  "status": "SENT",
  "invitationStatus": "INVITED",
  "quotationDeadline": "2026-07-31 18:00:00",
  "currencyCode": "CNY",
  "lines": [
    {
      "rfqLineId": 10011,
      "materialCode": "IT-LAPTOP-001",
      "materialName": "비즈니스 노트북",
      "unit": "대",
      "quantity": "20.000000",
      "remark": "3년 보증 포함"
    }
  ],
  "currentQuotation": null
}
```

제출 요청:

```json
{
  "requestId": "f93b7342-9416-45bd-95f2-1e7e6045686d",
  "rfqId": 1001,
  "version": 0,
  "validUntil": "2026-08-31 18:00:00",
  "lines": [
    {
      "rfqLineId": 10011,
      "unitPrice": "6400.000000",
      "deliveryDays": 7,
      "remark": "도착 후 검수"
    }
  ]
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| `requestId` | String | 예 | 최대 64; 테넌트 내 유일 멱등 키 |
| `rfqId` | Long | 예 | 양의 정수; 현재 공급업체의 유효한 초대가 존재해야 함 |
| `version` | Integer | 예 | 첫 제출은 0; 수정 시 현재 견적 버전과 같아야 함 |
| `validUntil` | LocalDateTime | 예 | `yyyy-MM-dd HH:mm:ss`; 현재 시간보다 늦고 quotationDeadline 보다 이르면 안 됨 |
| `lines` | Array | 예 | 비어 있지 않음; rfqLineId 집합은 초대 상세의 RFQ 행 집합과 완전히 일치해야 함 |
| `lines[].rfqLineId` | Long | 예 | 양의 정수이며 중복 불가 |
| `lines[].unitPrice` | Decimal String | 예 | 십진 문자열; `DECIMAL(19,6)`, 0 보다 크고 정수부 최대 13 자리 |
| `lines[].deliveryDays` | Integer | 예 | 0–3650 |
| `lines[].remark` | String | 아니오 | 최대 500, 일반 텍스트 |

요청은 `tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount` 을 허용하지 않습니다. 이 필드들은 각각 신뢰할 수 있는 신원 헤더, PortalUser, Procurement 초대 상세에서 읽거나 서버측에서
`unitPrice × quantity` 로 계산합니다; 행 금액과 총액은 `DECIMAL(19,4)` 로 저장합니다. 응답은 `R<QuotationVO>` 로, 견적 헤더, `version` 과 모든 행 스냅샷을 포함합니다.

멱등 및 동시성 규칙:

- `srm_quotation_request` 는 `(tenantId, requestId)`, 정규화된 요청 본문 SHA-256, quotationId, targetVersion 을 영구 저장합니다; 동일 requestId·동일 requestHash 재시도는 현재 견적 스냅샷을 반환하고 견적이나 Outbox 를 다시 쓰지 않습니다.
- 동일 requestId 가 다른 rfqId 나 요청 내용에 바인딩되면 비즈니스 코드 409 를 반환합니다.
- `(tenantId, rfqId, supplierId)` 는 미삭제 견적을 최대 1 건; 첫 요청은 생성 센티널 `version=0` 을 사용하고, 첫 버전이 영속화되고 `version=1` 로 응답되며, 이후 업데이트는 현재 version 을 휴대해야 하고 만료 버전이나 0 재사용은 모두 409 를 반환합니다.
- 제출 전 RFQ `status=SENT`, 초대 `status IN (INVITED, QUOTED)`, 마감 시간과 행 집합을 재검증해야 합니다; 기타 RFQ 상태(`DRAFT/CLOSED/AWARDED/CANCELLED`)는 거부합니다. Procurement 이 사용 불가일 때 503 을 반환하며 오프라인 쓰기를 허용하지 않습니다.

requestHash 는 requestId 를 포함하지 않으며, 본 단계의 정규화 입력은
`rfqId/version/validUntil/lines` 입니다; lines 는 `rfqLineId` 오름차순, 단가는 6 자리 소수로 정규화하고 비과학적 표기 문자열을 사용하며, 비고는 trim 후 null/공백을 null 로 통일합니다. 서버측은 필드 순서로 인한 동일 의도 오판을 피하기 위해 원시 JSON 바이트를 직접 해시해서는 안 됩니다.

### 15.6 SRM 및 Procurement 견적 내부 계약

SRM 은 초대 조회 시 Procurement 을 호출합니다:

- `GET /api/internal/procurement/rfq/invitations?supplierId={supplierId}`
- `GET /api/internal/procurement/rfq/{rfqId}/invitation?supplierId={supplierId}`

목록 항목은 최소한 `tenantId/rfqId/rfqNo/title/status/invitationStatus/supplierId/quotationDeadline/currencyCode/invitedTime` 을 포함하며; 상세는
`lines[{rfqLineId,materialCode,materialName,unit,quantity,remark}]` 을 추가합니다. SRM 은 PortalUser 연관으로 얻은 supplierId 를 사용해야 합니다.

Procurement 은 비교/선정 시 SRM 을 호출합니다:

```http
GET /api/internal/quotation/batch?tenantId=1&rfqId=1001
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

응답은 `R<List<QuotationVO>>`. `QuotationVO` 는
`id/rfqId/rfqNo/supplierId/supplierNameSnapshot/quotationTime/validUntil/totalAmount/currencyCode/status/version/lines` 를 포함하며; 행은
`id/rfqLineId/materialCode/materialName/unit/unitPrice/quantity/lineAmount/deliveryDays/remark` 를 포함합니다. 지정된 tenant·RFQ 이고 공급업체가 현재도 APPROVED 인 미삭제 유효 견적만 반환합니다. 포털 초대, 견적 응답 및 내부 batch 의 `totalAmount/unitPrice/quantity/lineAmount` 는 일괄 JSON 십진 문자열을 사용하며, JavaScript 고정밀 금액이나 수량 손실을 피하기 위해 JSON number 출력을 금지합니다.

견적 헤더, 명세, `srm_quotation_request` 과 `srm.quotation.submitted.v1` Outbox 는 동일 트랜잭션에서 커밋됩니다. 이벤트 봉투는
`eventId/eventType/occurredAt/tenantId/payload` 를 따르며, payload 는 최소한
`requestId/quotationId/quotationVersion/rfqId/rfqNo/supplierId/status/totalAmount/currencyCode/validUntil` 을 포함합니다. Procurement 은 eventId Inbox 로 멱등 소비하고 오래된 quotationVersion 으로 새 버전을 덮어쓰는 것을 거부합니다.

---

## 16. Workflow 크로스 서비스 계약

Workflow 내부 엔드포인트는 §8.3 의 `X-Internal-Token` 과 `X-Tenant-Id` 를 통일적으로 사용하고, 응답은 표준
`R<T>` 를 계속 사용합니다. 상세한 실행 메커니즘은 [workflow.kr.md](workflow.kr.md#28-크로스-서비스-내부-계약) 참조.

### 16.1 멱등 프로세스 시작

```http
POST /api/internal/workflow/process-instance/start
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

요청:

```json
{
  "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
  "tenantId": 1,
  "modelVersionId": 42,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "startUserId": 7,
  "startUserName": "buyer",
  "title": "구매 신청 PR-202607-0001",
  "variables": {
    "requisitionId": 10001,
    "approvalAttempt": 1,
    "materialCategory": "IT_EQUIPMENT",
    "totalAmount": "120000.0000",
    "requesterUnitId": 12
  }
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| `requestId` | String | 예 | 비어 있지 않음, 최대 64; 호출자 생성 멱등 키 |
| `tenantId` | Long | 예 | 양의 정수, `X-Tenant-Id` 와 같아야 함 |
| `modelVersionId` | Long | 예 | 양의 정수, 모델 버전은 현재 테넌트에 속하고 `processDefinitionId` 가 존재해야 함 |
| `businessType` | String | 예 | 비어 있지 않음, 최대 100 |
| `businessKey` | String | 예 | 비어 있지 않음, 최대 255 |
| `startUserId` | Long | 예 | 양의 정수 |
| `startUserName` | String | 아니오 | 최대 100 |
| `title` | String | 아니오 | 최대 500; 빈 값은 `{businessType}:{businessKey}` 로 자동 생성 |
| `variables` | Object | 아니오 | 프로세스 변수; 예약 필드 `requestId/businessType/businessKey` 는 서비스가 덮어씀 |

응답:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "6d2f4d1a-41d7-4f68-a60a-8a2e9425a703",
    "businessType": "PROCUREMENT_REQUISITION",
    "businessKey": "10001:1",
    "processInstanceId": "22501",
    "replayed": false
  }
}
```

멱등성은 두 개의 테넌트 내 유일 키로 공동 보장됩니다:

- `(tenantId, requestId)`: 요청 수준 멱등; 동일 요청 ID 를 다른 비즈니스에 바인딩해서는 안 됩니다.
- `(tenantId, businessType, businessKey)`: 비즈니스 수준 멱등; 동일 비즈니스는 여러 프로세스를 시작해서는 안 됩니다.
- 이미 성공한 동일 의도 재시도는 원래 `processInstanceId` 를 반환하고 `replayed = true`.
- 처리 중, 요청 ID 충돌, 또는 비즈니스 키에 대응하는 `modelVersionId/startUserId` 변경은 모두 비즈니스 코드 409 를 반환합니다.

### 16.2 작업 처리 자격 검증

```http
POST /api/internal/workflow/task/assignment/validate
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
Content-Type: application/json
```

요청:

```json
{
  "tenantId": 1,
  "taskId": "25017",
  "userId": 7,
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1"
}
```

| 필드 | 타입 | 필수 | 제약 |
|------|------|------|------|
| `tenantId` | Long | 예 | 양의 정수, `X-Tenant-Id` 와 같아야 함 |
| `taskId` | String | 예 | 비어 있지 않음, 최대 64 |
| `userId` | Long | 예 | 양의 정수 |
| `businessType` | String | 예 | 비어 있지 않음, 최대 100 |
| `businessKey` | String | 예 | 비어 있지 않음, 최대 255 |

응답:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "processInstanceId": "22501",
    "assignmentType": "CANDIDATE",
    "message": "검증 통과"
  }
}
```

서비스는 Flowable 작업 테넌트, 인스턴스 확장 기록 테넌트, `businessType + businessKey` 비즈니스 귀속을 동시에 일치시키고,
`userId` 가 현재 `ASSIGNEE` 또는 미수령 작업의 `CANDIDATE` 임을 확인해야 합니다. `assignmentType` 은
`ASSIGNEE`, `CANDIDATE`, `NONE` 만 사용; 작업이 존재하지 않거나 어느 경계가 불일치할 때 `valid = false` 를 반환합니다.

### 16.3 프로세스 완료 이벤트

| 속성 | 값 |
|------|----|
| 이벤트 타입 | `workflow.process.completed.v1` |
| 생산자 | `omni-workflow` |
| Stream binding | `workflow-domain-out-0` |
| Destination | `workflow-domain-event` |

```json
{
  "eventId": "3f206832-9dc1-4422-870a-a286a979404d",
  "eventType": "workflow.process.completed.v1",
  "occurredAt": "2026-07-21 10:30:00",
  "tenantId": 1,
  "producer": "omni-workflow",
  "businessType": "PROCUREMENT_REQUISITION",
  "businessKey": "10001:1",
  "processInstanceId": "22501",
  "result": "APPROVED",
  "completedTime": "2026-07-21 10:30:00"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `eventId` | String(UUID) | 이벤트 ID, Outbox `msgKey`, 소비 멱등 키 |
| `eventType` | String | `workflow.process.completed.v1` 로 고정 |
| `occurredAt` | LocalDateTime | 이벤트 기록 생성 시간, 형식 `yyyy-MM-dd HH:mm:ss` |
| `tenantId` | Long | 테넌트 ID |
| `producer` | String | `omni-workflow` 로 고정 |
| `businessType` | String | 호출자 비즈니스 타입 |
| `businessKey` | String | 호출자 비즈니스 기본 키 |
| `processInstanceId` | String | Flowable 프로세스 인스턴스 ID |
| `result` | Enum | `APPROVED`, `REJECTED`, `CANCELLED` |
| `completedTime` | LocalDateTime | 프로세스 실제 완료 또는 종료 시간 |

완료 상태와 `completionEventId` 의 조건부 업데이트 및 PENDING Outbox 기록은 동일 로컬 트랜잭션 내에서 커밋됩니다;
`completion_event_id IS NULL` 은 동일 프로세스 인스턴스가 논리 완료 이벤트를 한 번만 생성함을 보장하는 데이터베이스 래치입니다.
커밋 후 신뢰성 메시지 릴레이가 비동기로 전달·재시도하며, 전송 의미는 최소 한 번이므로 컨슈머는 `eventId` 로 멱등 처리해야 합니다.

### 16.4 게시된 모델 버전 조회

```http
GET /api/internal/workflow/model-version/{modelVersionId}
X-Internal-Token: <shared-token>
X-Tenant-Id: 1
```

성공 응답:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 301,
    "modelId": 30,
    "modelKey": "asset-transfer-approval",
    "category": "ASSET_TRANSFER",
    "version": 2,
    "processDefinitionId": "asset-transfer-approval:2:8801",
    "status": "PUBLISHED"
  }
}
```

엔드포인트는 요청된 테넌트 내에서 여전히 사용 가능한 모델과 버전만 반환합니다. `modelKey` 는 테넌트 내 유일하고 BPMN process id 와
일치하는 모델 식별자; `category` 는 크로스 서비스 비즈니스 분류입니다. 호출자는 `PUBLISHED` 와 비어 있지 않은
`processDefinitionId` 검증 외에도 안정된 비즈니스 타입을 `category` 에 바인딩할 수 있습니다. Asset 은 이동 모델 분류를
`ASSET_TRANSFER`, 처분 모델 분류를 `ASSET_DISPOSAL` 로 강제하며, 교차 재사용이나 다른 비즈니스 모델 사용을 금지합니다;
Workflow 는 실제 자산 승인 인스턴스 생성 전에 동일한 검증을 다시 수행하여 사전 검증 후의 모델 변경 창을 닫습니다.

### 16.5 승인 규칙 읽기 전용 모델 집계

| 메서드 | 경로 | 제약 |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | 현재 테넌트, 분류 완전 일치, 메인 모델 유효 및 currentPublishedVersionId 가 배포 가능한 게시 버전을 가리키는 기록만 반환 |
| POST | `/api/internal/workflow/model-versions/resolve` | body 는 `{ "modelVersionIds": [1, 2] }`, 1회 1–200 개의 양의 정수, 요청 순서로 반환 |
| GET | `/api/internal/workflow/model-version/{id}/preview` | 안전 승인 다이어그램을 반환하고 BPMN XML 이나 designerJson 은 반환하지 않음 |

일괄 해석의 `availability` 는 `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND` 만 사용합니다.
안전 미리보기는 노드, 비식별화 간선, 모델 메타데이터만 포함; UserTask 는 `roleCode/approvalMode` 를 포함할 수 있고, 조건 표현식은
"조건 설정됨(내용 숨김)" 만 반환합니다. 분기나 루프가 있을 때 `linearSummary=null`, 프론트엔드는 실제 경로가 비즈니스 데이터로 결정됨을 안내해야 하며,
현재 조직을 미래의 실제 승인자로 해석해서는 안 됩니다.

---

## 17. Procurement MVP 계약

### 17.1 공통 경계

- 외부 Base path 는 `/api/procurement`, Gateway 는 전체 경로를 보존하고 `StripPrefix` 를 사용하지 않습니다.
- 모든 요청은 Gateway 가 주입한 `X-User-Id`, `X-Tenant-Id` 와 권한 헤더를 사용; 비즈니스 테이블은 TenantLine 과 permission-aware DataScope 양쪽으로 제약됩니다.
- 수량, 단가는 `DECIMAL(19,6)`, 금액은 `DECIMAL(19,4)` 를 사용; 모든 응답의 수량, 단가, 금액은 JSON string 이며, 프론트엔드는 JavaScript `number` 로 비즈니스 금액을 계산해서는 안 됩니다.
- 업데이트 body 와 삭제 query 는 `version` 을 휴대해야 합니다; 버전 충돌은 비즈니스 코드 409 를 반환합니다.
- 내부 엔드포인트는 통일적으로 `/api/internal/procurement/**` 를 사용하고, `X-Internal-Token` 과 `X-Tenant-Id` 를 요구하며, Gateway 를 통해 노출해서는 안 됩니다.

### 17.2 자재 및 품목

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | 최대 2 단계 품목 트리 반환 |
| POST | `/api/procurement/material/category` | `procurement:material:create` | 품목 생성; `categoryCode` 는 생성 후 수정 불가 |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body 는 `version` 휴대 |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | 하위 품목이나 자재가 존재하면 409 반환 |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | 자재 상세 조회 |
| POST | `/api/procurement/material` | `procurement:material:create` | 자재 생성; `materialCode` 는 생성 후 수정 불가 |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body 는 `version` 휴대 |
| DELETE | `/api/procurement/material/{id}?version={version}` | `procurement:material:delete` | 논리 삭제 |

`assetManaged=true` 일 때 `unit` 은 `EA/PCS/UNIT/SET` 만 허용; 구매 요청은 상태가 `ACTIVE` 이고 품목이 활성화된 자재만 참조할 수 있습니다.

### 17.3 승인 라우트

| 메서드 | 경로 | 권한 |
|---|---|---|
| GET | `/api/procurement/approval-route/list` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/workflow-options` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route/match-preview` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/coverage` | `procurement:approval-route:list` |
| GET | `/api/procurement/approval-route/impact?routeId={id}` | `procurement:approval-route:list` |
| POST | `/api/procurement/approval-route` | `procurement:approval-route:create` |
| PUT | `/api/procurement/approval-route/{id}` | `procurement:approval-route:update` |
| DELETE | `/api/procurement/approval-route/{id}?version={version}` | `procurement:approval-route:delete` |

새 UI 생성 요청은 `routeName/categoryCode/minAmount/maxAmount/modelVersionId/status` 를 포함합니다. `routeCode` 는 서버측에서
`APR-{ULID}` 를 생성하고 생성 후 수정 불가; 하나의 호환 게시 주기 내에서는 오래된 생성 요청이 누락된
`routeName` 의 폴백으로 `routeCode` 를 전달할 수 있으며, 서버측은 폐기 예정 로그를 기록합니다. `priority` 는 호환 고급 호출자를 위해서만 보존; 신규 생성 시 미전달 시에는 테넌트 설정 잠금 내에서
동일 품목의 최대값에 10 을 더하며, 프론트엔드는 이 필드를 표시하지 않습니다.

`minAmount/maxAmount` 는 JSON 십진 문자열을 사용해야 하며(`maxAmount=null` 제외), JSON number 는 400 을 반환합니다.
활성 구간은 `minAmount <= amount < maxAmount` 를 사용하고, `maxAmount=null` 은 상한 없음을 의미합니다. 동일 품목의 활성 구간은 중복되어서는 안 되며,
쓰기 트랜잭션은 테넌트 설정 행 잠금으로 직렬화 검증합니다. 신규 생성이나 `modelVersionId` 교체 시 현재 테넌트, `category=purchase`,
`availability=AVAILABLE` 의 현재 게시 버전만 허용; 레거시 비 purchase 참조는 목록에서 `LEGACY_CATEGORY` 로 표시하며 조용히 마이그레이션할 수 없습니다.

`match-preview` 요청은 `{ "categoryCode": "IT_DEVICE", "totalAmount": "10000.0000" }`, 응답
`outcome` 은 `MATCHED/NO_MATCH/AMBIGUOUS/WORKFLOW_UNAVAILABLE` 만 사용합니다. 구매 요청 제출과 공동으로
`ApprovalRouteResolver.evaluate` 를 호출; 제출 경로는 비 MATCHED 결과를 기존 409 로 변환하므로 브라우저는 적중을 계산하지 않습니다.
`coverage` 는 모든 활성화 품목에 대해 0 부터 무한까지의 `COVERED/GAP/AMBIGUOUS` 반개방 구간을 출력하고, 기본 폴백,
실효 모델, Workflow 사용 불가를 표시합니다. `impact` 는 메모리에서 지정 규칙을 제외한 후 동일 알고리즘을 재사용하며, 데이터베이스는 수정하지 않습니다.

### 17.4 구매 요청

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/requisition/list` | `procurement:requisition:list` | `keyword/status/categoryCode/page/size` |
| GET | `/api/procurement/requisition/{id}` | `procurement:requisition:list` | 일반 상세는 여전히 requester DataScope 로 제약 |
| GET | `/api/procurement/requisition/{id}/approval-view?taskId={taskId}` | `procurement:requisition:approve` | 먼저 Workflow 가 작업이 현재 사용자와 본 구매 요청에 속함을 검증 |
| POST | `/api/procurement/requisition` | `procurement:requisition:create` | DRAFT 생성 |
| PUT | `/api/procurement/requisition/{id}` | `procurement:requisition:update` | DRAFT/REJECTED 만; REJECTED 는 업데이트 후 DRAFT 로 복귀 |
| DELETE | `/api/procurement/requisition/{id}?version={version}` | `procurement:requisition:delete` | DRAFT 만 |
| POST | `/api/procurement/requisition/{id}/submit` | `procurement:requisition:submit` | body `{ "version": 0 }` |
| POST | `/api/procurement/requisition/{id}/retry-start` | `procurement:requisition:submit` | `SUBMITTED + FAILED` 만, 원래 Workflow 멱등 스냅샷 재사용 |
| POST | `/api/procurement/requisition/{id}/cancel` | `procurement:requisition:cancel` | DRAFT 또는 `SUBMITTED + FAILED` 만 |

생성/업데이트 요청 예시:

```json
{
  "title": "R&D 노트북 조달",
  "reason": "신규 직원 입사",
  "lines": [
    {
      "materialId": 101,
      "quantity": "2.000000",
      "estimatedUnitPrice": "8500.000000",
      "remark": "16GB 메모리 이상"
    }
  ]
}
```

`lines[].quantity` 와 `lines[].estimatedUnitPrice` 는 JSON 십진 문자열만 허용; 수치가 JavaScript 안전 범위 내여도 JSON number 는 400 을 반환합니다.

MVP 는 모든 행이 동일 품목에 속할 것을 요구합니다. 서비스는 제출 트랜잭션에서 활성 자재와 품목을 일괄 재조회하고, 자재 코드, 이름, 품목, 단위 스냅샷을 갱신하며 행 금액과 총액을 재계산합니다; 클라이언트는 총액이나 `modelVersionId` 를 전달할 수 없습니다. 매 신규 제출마다 `approvalAttempt + 1`, Workflow `businessKey={requisitionId}:{approvalAttempt}`; 시작의 불확실한 실패 후 retry 는 영속화된 `requestId/businessKey/modelVersionId` 를 재사용해야 합니다.

Workflow 완료 이벤트는 `eventId` 로 `proc_event_inbox` 에 들어갑니다. 현재 라운드 이벤트는 tenant, businessKey, processInstanceId 와 `APPROVING` 상태가 모두 일치할 때만 구매 요청을 업데이트; 이전 라운드 이벤트는 멱등으로 무시하고, 로컬 시작 확인보다 이른 이벤트는 Inbox 를 롤백하고 메시지 재시도를 트리거하며, 동일 eventId 가 다른 전체 payload 에 바인딩되면 409 를 반환합니다.

### 17.5 견적 요청, 비교 및 선정

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/rfq/supplier-options` | `procurement:rfq:create` 또는 `procurement:rfq:list` | 현재 테넌트의 SRM 합격 공급업체 옵션 조회 |
| GET | `/api/procurement/rfq/list` | `procurement:rfq:list` | `keyword/status/deadlineFrom/deadlineTo/page/size` |
| GET | `/api/procurement/rfq/{id}` | `procurement:rfq:list` | RFQ, 행 및 초대 스냅샷 조회 |
| GET | `/api/procurement/rfq/{id}/comparison` | `procurement:rfq:list` | SRM 에서 현재 유효 견적과 전체 행 스냅샷을 재읽기 |
| POST | `/api/procurement/rfq` | `procurement:rfq:create` | 승인된 구매 요청에서 DRAFT 생성 |
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | DRAFT 만; body 는 `version` 휴대 |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | DRAFT 만 |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`, 초대된 공급업체에 게시 |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | 견적 버전을 잠그고 구매 주문을 원자적으로 생성 |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`; DRAFT/SENT 만 |

생성 및 업데이트 요청은 `requisitionId/title/quotationDeadline/supplierIds` 를 포함; 업데이트 시 시간 형식은
`yyyy-MM-dd HH:mm:ss` 로 통일. `SENT` RFQ 만 비교와 선정이 가능합니다. 초대 상태는
`INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`; 선정 후 낙찰 초대는 `AWARDED`, 나머지는
`REJECTED` 가 되며, 이 종태는 공급업체 포털 이력 조회 전용이고 견적을 계속할 수 없습니다.

선정 요청 예시:

```json
{
  "rfqVersion": 2,
  "quotationId": 501,
  "quotationVersion": 3,
  "title": "R&D 노트북 구매 주문",
  "expectedDeliveryDate": "2026-08-15",
  "deliveryAddress": "상하이시 푸둥 신구 예시로 1호",
  "contactName": "홍길동",
  "contactPhone": "13800000000"
}
```

서버측은 동일 트랜잭션에서 RFQ 와 초대를 잠그고, SRM 에서 `quotationId` 의 현재 버전, tenant, 공급업체,
통화, 유효기간 및 전체 행 집합을 재조회; `rfqVersion` 이나 `quotationVersion` 중 하나라도 불일치하면 409 를 반환합니다. 성공 응답은
`{ "rfq": ..., "purchaseOrder": ... }` 이며, 불변 견적 금액/납기 스냅샷을 저장; SRM 의 후속 견적 변화는 기존 선정이나 구매 주문을 변경해서는 안 됩니다. 견적 비교 응답의 수량, 단가, 금액은 모두 JSON 십진 문자열입니다.

### 17.6 구매 주문

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/purchase-order/list` | `procurement:purchase-order:list` | `keyword/status/expectedDeliveryFrom/expectedDeliveryTo/page/size` |
| GET | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:list` | 주문 및 불변 견적 행 스냅샷 조회 |
| PUT | `/api/procurement/purchase-order/{id}` | `procurement:purchase-order:update` | DRAFT 만, 제목과 납품 정보 수정 가능 |
| DELETE | `/api/procurement/purchase-order/{id}?version={version}` | `procurement:purchase-order:delete` | DRAFT 만 |
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT, body 는 `version` 휴대 |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED, body 는 `version` 휴대 |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | 입고 발생 전 취소, body 는 `version` 휴대 |

외부 API 는 구매 주문 생성 엔드포인트를 제공하지 않습니다; MVP 주문은 RFQ 선정 트랜잭션으로만 생성할 수 있으며, 클라이언트는 공급업체, 견적이나
주문 행을 위조할 수 없습니다. 상태는 `DRAFT/SENT/CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED/CANCELLED`.
목록의 주소, 연락처, 전화는 기본적으로 비식별화되고, 상세는 여전히 owner DataScope 로 제약; 수량, 단가, 행 금액, 총액은
항상 JSON 십진 문자열로 반환합니다.

### 17.7 입고 및 품질 검사

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/goods-receipt/list` | `procurement:goods-receipt:list` | `keyword/status/receiveTimeFrom/receiveTimeTo/page/size` |
| GET | `/api/procurement/goods-receipt/{id}` | `procurement:goods-receipt:list` | 입고 상세 조회 |
| POST | `/api/procurement/goods-receipt` | `procurement:goods-receipt:create` | CONFIRMED/PARTIAL_RECEIVED 주문에 DRAFT 생성 |
| POST | `/api/procurement/goods-receipt/{id}/confirm` | `procurement:goods-receipt:confirm` | body `{ "version": 0 }`, 입고 확인 및 주문 누적 상태 업데이트 |
| POST | `/api/procurement/goods-receipt/{id}/quality-result` | `procurement:goods-receipt:confirm` | 확인된 입고의 PENDING 행만 PASS/FAIL 로 변경 |

생성 요청의 `receiveTime` 은 `yyyy-MM-dd HH:mm:ss` 를 사용하고, 각 행은
`poLineId/receivedQuantity/qualityStatus/remark` 를 포함합니다. `receivedQuantity` 는 JSON 십진 문자열만 허용하며, JSON
number 는 400 을 반환합니다. DRAFT 생성은 입고된 수량을 점유하지 않습니다; 확인 트랜잭션은 구매 주문을 잠그고 모든 CONFIRMED 입고 행으로 재누적 검증하며, 동시 초과 입고를 금지합니다. 부분 및 전체 입고는 각각 주문을 `PARTIAL_RECEIVED` 와 `RECEIVED` 로 진행합니다.

`qualityStatus=PASS`, 자재 `assetManaged=true` 이고 수량이 양의 정수인 행만 자산 후보에 들어갑니다. 확인 시
`procurement.goods-receipt.confirmed.v1` 을 발행; PENDING 이 이후 처음 PASS 로 바뀔 때
`procurement.goods-receipt.quality-passed.v1` 을 발행하고, 동일 배치의 신규 통과 행은 하나의 이벤트 ID 를 공유합니다. 이력 보상 읽기는
`X-Internal-Token` 으로 보호되는
`GET /api/internal/procurement/goods-receipt/asset-candidates?tenantId={tenantId}&afterId={id}&size={size}` 를 사용;
실시간 소비와 백스캔은 모두 `tenantId + goodsReceiptLineId + unitSequence` 로 멱등입니다.

두 이벤트와 이력 후보는 입고 관리 귀속 `ownerUserId/ownerUnitId` 를 휴대해야 하며, Asset 은 이를 새 자산의
관리 귀속으로 상속; 필드가 누락되거나 양의 정수가 아니면 실패 차단합니다. 이벤트 행의 `receivedQuantity/unitPrice/totalPrice`
는 계속 JSON 십진 문자열을 사용하고, 단위 수준 카운트 `assetQuantity` 만 양의 정수를 사용합니다. Asset 실시간 컨슈머는
`consumerName + eventId` 로 Inbox 멱등 래치도 확립; 동일 이벤트 ID 나 소스 단위가 다른 전체 비즈니스 의도에 바인딩되면
충돌을 반환하고 생성된 자산을 덮어써서는 안 됩니다.

### 17.8 조달 개요

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/overview/summary` | `procurement:overview:list` | 조달 흐름 미결, 주문 상태 및 통화별 약정 금액 |
| GET | `/api/procurement/overview/spend-analysis?dimension={dimension}&limit={limit}` | `procurement:overview:list` | 차원 및 통화별로 확인된 조달 지출 집계 |

`dimension` 은 필수이며 `CATEGORY`, `SUPPLIER`, `DEPARTMENT` 만 허용; `limit` 은 기본 20, 범위 1–100.
DEPARTMENT 는 구매 주문 담당 부서 `ownerUnitId` 를 의미합니다. 지출은
`CONFIRMED/PARTIAL_RECEIVED/RECEIVED/CLOSED` 구매 주문만 집계하고, 초안, 발송만 또는 취소된 주문은 포함하지 않습니다.

요약 응답 예시:

```json
{
  "pendingApprovalRequisitionCount": 3,
  "waitingQuotationRfqCount": 2,
  "purchaseOrderStatusCounts": [
    { "status": "DRAFT", "count": 1 },
    { "status": "SENT", "count": 2 },
    { "status": "CONFIRMED", "count": 4 },
    { "status": "PARTIAL_RECEIVED", "count": 1 },
    { "status": "RECEIVED", "count": 5 },
    { "status": "CLOSED", "count": 8 },
    { "status": "CANCELLED", "count": 1 }
  ],
  "draftGoodsReceiptCount": 2,
  "committedAmountsByCurrency": [
    { "currencyCode": "CNY", "amount": "120000.0000" },
    { "currencyCode": "USD", "amount": "8500.0000" }
  ]
}
```

지출 분석 항목은 `dimension/dimensionKey/dimensionName/currencyCode/amount` 를 포함하며, 먼저
`currencyCode` 오름차순, 다음으로 동일 통화의 `amount` 내림차순으로 정렬합니다. `amount` 는 항상 JSON 십진 문자열;
서로 다른 통화는 독립 기록을 유지해야 하며, 서버측과 프론트엔드 모두 직접 더해서는 안 됩니다. 요약의 각 집계 SQL 은 대응하는 구매 요청, RFQ, 구매 주문 또는 입고 집계 루트를 직접 히트하고,
일반 목록과 동일한 requester/owner DataScope 와 TenantLine 을 적용; 지출 분석은 구매 주문 owner 범위를 사용하며, 집계 쿼리로 데이터 권한을 우회해서는 안 됩니다.

---

## 18. Asset MVP 계약

### 18.1 공통 경계

- 외부 Base path 는 `/api/asset`; Gateway 는 전체 경로를 보존하고 `StripPrefix` 를 사용하지 않습니다.
- 외부 요청은 Gateway 가 주입한 `X-User-Id`, `X-Tenant-Id`, `X-Username`, 역할 및 권한 헤더를 사용합니다. 비즈니스 테이블은 항상 TenantLine 으로 제약되고, 관리 목록, 하위 리소스 및 개요는 추가로 permission-aware DataScope 로 제약됩니다.
- 관리 목록은 `owner_user_id/owner_unit_id` 로 필터; `GET /api/asset/asset/my` 는 항상 `current_user_id` 로 조회하며, 동일 사용자가 관리 역할을 가졌다고 확대할 수 없습니다.
- 쓰기 명령은 `version` 을 휴대하고 낙관적 잠금 검증을 수행; 버전이나 활성 작업 점유 불일치는 비즈니스 충돌을 반환합니다.
- 자산 원가, 잔존가치 및 집계 금액은 `DECIMAL(18,2)` 를 사용하고, 요청과 응답 모두 JSON 십진 문자열을 사용; JSON number 는 400 을 반환합니다. 통화는 3 자리 ISO 4217 코드를 사용합니다.
- 자산 상태는 `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`.
- 내부 엔드포인트는 `/api/internal/asset/**` 를 사용하고, `X-Internal-Token` 과 `X-Tenant-Id` 를 휴대해야 하며, Gateway 에 의해 명시적으로 차단됩니다.

### 18.2 자산 대장 및 명령

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/asset/asset/list` | `asset:asset:list` | `keyword/status/categoryCode/ownerUnitId/locationCode/page/size`, 관리 귀속으로 조회 |
| GET | `/api/asset/asset/my` | `asset:asset:self` | `keyword/status/categoryCode/page/size`, 항상 현재 사용자 조회 |
| GET | `/api/asset/asset/{id}` | `asset:asset:list` | 관리 범위 내 자산 상세 조회 |
| GET | `/api/asset/asset/{id}/history` | `asset:asset:list` | `page/size`, 불변 상태 이력 조회 |
| POST | `/api/asset/asset` | `asset:asset:create` | `IN_STOCK` 자산 수동 생성 |
| PUT | `/api/asset/asset/{id}` | `asset:asset:update` | 기본 자료 업데이트; 상태, 사용자, 위치를 직접 업데이트 불가 |
| DELETE | `/api/asset/asset/{id}?version={version}` | `asset:asset:delete` | 비즈니스 동작이 발생하지 않은 수동 재고 자산만 삭제 |
| POST | `/api/asset/asset/{id}/allocate` | `asset:asset:allocate` | `IN_STOCK → ALLOCATED` |
| POST | `/api/asset/asset/{id}/accept` | `asset:asset:accept` | 현재 사용자가 `ALLOCATED → IN_USE` 실행 |
| POST | `/api/asset/asset/{id}/return` | `asset:asset:return` | 현재 사용자가 반환, `IN_STOCK` 복원 및 사용 귀속 정리 |
| POST | `/api/asset/asset/{id}/maintenance/start` | `asset:asset:maintenance` | `IN_USE → MAINTENANCE` |
| POST | `/api/asset/asset/{id}/maintenance/complete` | `asset:asset:maintenance` | `MAINTENANCE → IN_USE` |
| GET | `/api/asset/options/users` | 자산 대장/할당/이동/처분 관련 권한 중 하나 | 현재 테넌트 활성 사용자 후보, 주 조직 반환, 전화/이메일 미포함 |
| GET | `/api/asset/options/suppliers` | `asset:asset:create/update` | 현재 테넌트 승인된 공급업체 키워드 후보 |
| GET | `/api/asset/options/transfer-assets` | `asset:transfer:create` | 현재 DataScope 내 활성 점유가 없고 이동 가능한 상태의 자산 |
| GET | `/api/asset/options/disposal-assets` | `asset:disposal:create` | 현재 DataScope 내 활성 점유가 없고 처분 가능한 상태의 자산 |

수동 생성 요청은
`name/categoryCode/specification/brand/model/supplierId/supplierNameSnapshot/purchaseDate/purchaseAmount/currencyCode/locationCode/warrantyExpiryDate/expectedLifeYears/remark/ownerUserId/ownerUnitId` 를 포함합니다.
`purchaseAmount` 는 기본으로 `null` 가능, 비어 있지 않을 때는 JSON 십진 문자열이어야 하며; `currencyCode`, `ownerUserId` 와 `ownerUnitId` 는 필수. 업데이트 요청은 필수 `version` 을 추가하지만 `locationCode` 는 허용하지 않습니다.

할당 요청은:

```json
{
  "version": 0,
  "targetUserId": 101,
  "targetUnitId": 12,
  "remark": "R&D 장비 수령"
}
```

수령, 반환 및 유지보수 명령은 `{ "version": 0, "remark": "..." }` 를 사용합니다. `accept/return` 은 권한 검증 외에도 자산의
`current_user_id` 가 현재 사용자와 같은지 검증해야 하며; 관리 범위는 이 행별 귀속 검증을 대체할 수 없습니다.

### 18.3 Procurement 입고 연동

Asset 은 `procurement.goods-receipt.confirmed.v1` 과
`procurement.goods-receipt.quality-passed.v1` 을 소비합니다. 이벤트 봉투와 입고 행 필드는 17.7 을 권위로 하며; Asset 은
`qualityStatus=PASS && assetManaged=true && assetQuantity>0` 의 단위 수준 자산만 처리합니다.

- 실시간 소비는 `consumerName + eventId` 로 `ast_inbox_event` 에 기록하고, 동일 이벤트 ID 가 다른 전체 비즈니스 의도에 바인딩되지 않음을 검증합니다.
- 실시간 소비와 이력 백스캔은 공동으로
  `tenantId + goodsReceiptLineId + unitSequence` 로 소스 유일 키를 확립하여, 어떤 입구도 자산을 중복 생성할 수 없습니다.
- 새 자산은 입고 관리 귀속 `ownerUserId/ownerUnitId` 를 상속하고, PO, GR, 공급업체, 자재, 품목, 통화 및 금액 스냅샷을 저장합니다.
- 내부 제어 보상 엔드포인트는
  `POST /api/internal/asset/procurement/backfill?tenantId={tenantId}&afterId={id}&size={size}`; 요청 헤더
  `X-Tenant-Id` 는 query `tenantId` 와 완전히 일치해야 합니다. `size` 는 1–100, 응답은 본 페이지 처리 결과와 다음 커서를 반환합니다.

### 18.4 이동

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/asset/transfer/list` | `asset:transfer:list` | `keyword/status/page/size`, 연관 자산을 통해 관리 DataScope 상속 |
| GET | `/api/asset/transfer/{id}` | `asset:transfer:list` | 이동 상세 조회 |
| GET | `/api/asset/transfer/{id}/approval-view?taskId={taskId}` | `asset:transfer:approve` | Workflow 가 현재 작업 할당을 검증한 후 tenant 로 읽기 전용 승인 뷰 읽음 |
| POST | `/api/asset/transfer` | `asset:transfer:create` | 신청 생성, 자산을 원자적으로 점유하고 Workflow 시작 |
| POST | `/api/asset/transfer/{id}/retry-start` | `asset:transfer:retry` | `PENDING_APPROVAL + PENDING` 또는 `START_FAILED + FAILED` 신청에 원래 멱등 스냅샷을 재사용해 시작 |
| POST | `/api/asset/transfer/{id}/cancel` | `asset:transfer:cancel` | `START_FAILED + FAILED` 의 명시적 실패 신청만 취소하고 자산 복원 |
| POST | `/api/asset/transfer/{id}/complete` | `asset:transfer:complete` | 승인 통과 후 인계 완료, 자산은 `IN_USE` 진입 |

생성 요청은:

```json
{
  "assetId": 10001,
  "toUserId": 102,
  "toUnitId": 12,
  "toLocation": "SH-A-03-021",
  "reason": "직위 조정"
}
```

생성은 자산이 `IN_STOCK/ALLOCATED/IN_USE` 이고 활성 작업이 없을 때만 허용. 신청은 원래 사용 귀속, 위치와
`previousAssetStatus` 를 저장. 서버측은 현재 테넌트와 `category=ASSET_TRANSFER` 로 게시되고 시작 가능한 Workflow 모델 버전을 자동 해석하고 멱등 스냅샷을 영속화; 클라이언트는 `modelVersionId` 를 제공하거나 선택해서는 안 됩니다.
`retry-start/cancel/complete` body 는 모두 `{ "version": 0 }`.

### 18.5 폐기 및 스크랩 처분

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/asset/disposal/list` | `asset:disposal:list` | `keyword/disposalType/status/page/size`, 연관 자산을 통해 관리 DataScope 상속 |
| GET | `/api/asset/disposal/{id}` | `asset:disposal:list` | 처분 상세 조회 |
| GET | `/api/asset/disposal/{id}/approval-view?taskId={taskId}` | `asset:disposal:approve` | Workflow 가 현재 작업 할당을 검증한 후 tenant 로 읽기 전용 승인 뷰 읽음 |
| POST | `/api/asset/disposal` | `asset:disposal:create` | 신청 생성, 자산을 원자적으로 점유하고 Workflow 시작 |
| POST | `/api/asset/disposal/{id}/retry-start` | `asset:disposal:retry` | `PENDING_APPROVAL + PENDING` 또는 `START_FAILED + FAILED` 신청에 원래 멱등 스냅샷을 재사용해 시작 |
| POST | `/api/asset/disposal/{id}/cancel` | `asset:disposal:cancel` | `START_FAILED + FAILED` 의 명시적 실패 신청만 취소하고 자산 복원 |
| POST | `/api/asset/disposal/{id}/complete` | `asset:disposal:complete` | 승인 통과 후 실물 처분 완료 |

생성 요청은
`assetId/disposalType/reason/residualValue/disposalMethod` 를 포함; `disposalType` 은
`DISCARD/SCRAP` 만 허용, `residualValue` 는 비어 있지 않을 때 JSON 십진 문자열이어야 합니다. 신청은
`ASSET_DISPOSAL + businessKey` 로 서버측이 자동 해석한 `category=ASSET_DISPOSAL` Workflow 모델을 시작하며,
클라이언트는 `modelVersionId` 를 제공해서는 안 됩니다.
`DISCARD` 완료 후 자산은 `DISPOSED` 에 진입하고, `SCRAP` 완료
후 `SCRAPPED` 에 진입하며, 둘 다 복구 불가한 종태입니다.

### 18.6 Workflow 완료 이벤트 및 작업 상태

이동과 처분 상태는
`PENDING_APPROVAL/START_FAILED/APPROVED/REJECTED/COMPLETED/CANCELLED` 로 통일되고, Workflow 시작 상태는
`PENDING/STARTED/FAILED`.

Workflow 는 16.3 의 `workflow.process.completed.v1` 로 승인 결과를 게시합니다. Asset 컨슈머는:

1. `eventId/eventType/producer/tenantId/businessType/businessKey/processInstanceId/result` 를 검증;
2. `consumerName + eventId` 로 Inbox 멱등 래치를 확립하고 동일 이벤트 ID 가 다른 전체 payload 에 바인딩되는 것을 거부;
3. 현재 신청, 확인된 프로세스 인스턴스 및 활성 상태와 완전히 일치하는 이벤트만 수락;
4. 로컬 시작 확인보다 일찍 도착한 이벤트는 Inbox 를 롤백하고 메시지 재시도를 트리거;
5. `APPROVED` 는 신청을 비즈니스 완료 대기로만 진행; `REJECTED/CANCELLED` 는 동일 트랜잭션에서
   `previousAssetStatus` 를 복원하고, 신청을 닫고 자산 `active_operation_*` 를 정리.

Workflow 시작 호출은 로컬 생성 트랜잭션 커밋 후에 발생합니다. 네트워크 예외, 409/기타 결과를 확정할 수 없는 비 200 응답 또는 로컬 확인 실패는
모두 원격에서 수락되었을 수 있으므로, `PENDING_APPROVAL + PENDING` 을 유지하고 로컬 취소를 금지하며 동일 멱등 스냅샷으로 재시도를 허용합니다.
Workflow 비즈니스 응답 404 는 모델 버전이 시작 불가하고 원격 트랜잭션이 인스턴스를 생성하지 않았음을 의미하며, 신청은
`START_FAILED + FAILED` 에 진입; 이 명시적 실패 상태는 재시도나 로컬 취소를 허용합니다.
두 종류의 재시도는 모두 멱등 스냅샷을 재사용해야 하며: `businessType` 은 이동/처분 집계 타입에서 고정 도출되고, 영속화된
`requestId/businessKey/modelVersionId/workflowStartUserId/workflowStartUserName` 를 재사용하며,
다른 사용자가 재시도를 실행할 때도 원래 발기인 신원을 계속 사용하는 것을 포함하고, 두 번째 프로세스 인스턴스 생성을 금지합니다.

### 18.7 자산 개요

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | 관리 범위 내 각 상태 수량 및 통화별 원가 |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | 상태, 품목, 관리 부서 또는 위치로 집계 |

`dimension` 은 필수이며 `STATUS/CATEGORY/DEPARTMENT/LOCATION` 만 허용; `limit` 은 기본 20, 범위 1–100.
모든 집계 SQL 은 관리 대장과 동일한 owner DataScope 와 TenantLine 을 적용해야 합니다. 금액은 통화별로 독립 기록을 유지하고 십진 문자열을 출력하며, 서로 다른 통화를 직접 더해서는 안 됩니다.
