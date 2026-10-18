# API 계약

> 이 문서는 응답, 필드, 경로, 식별 헤더와 서비스 간 메시지의 현재 전송 형식을 정의합니다. 실제 동작은 소스, 설정, Controller/DTO가 결정합니다. 예제 ID, 날짜, 주소, 상태는 구조 설명용이며 현재 환경의 데이터나 검증 결과가 아닙니다.
> 상태 전이와 구현 범위는 각 도메인의 사실·설계 문서에서 관리합니다. 설정, 로그인, 브라우저 작업과 복구는 [Wiki](wiki/index.kr.md) 및 [인증 가이드](guides/authentication.kr.md)를 참조하세요. 로컬 프로토콜 인가와 외부 소셜 로그인은 별개의 흐름입니다.

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

일반 업무 Controller는 `R<T>`, 페이지 조회는 `R<PageResult<T>>`를 반환합니다. OAuth2/OIDC 프로토콜, 소셜 로그인 리디렉션, Actuator, 필터나 프레임워크가 생성하는 응답에 이 래퍼를 일괄 적용할 수는 없습니다.

```json
{"code":200,"message":"success","data":{"id":1}}
```

```json
{"code":500,"message":"操作失败","data":null}
```

```json
{"code":400,"message":"username: 用户名不能为空","data":null}
```

`R.ok(data)`의 업무 코드는 200, message는 `success`입니다. `R.ok()`와 실패 응답의 data는 null입니다. `R.fail(message)`의 기본 코드는 500이며 코드를 명시하는 오버로드는 지정값을 사용합니다. JSON 속성 순서는 계약에 포함되지 않습니다.

백엔드의 유일한 타입은 [R.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/R.java), 프런트엔드의 유일한 선언은 [src/types/api.ts](../omni-frontend/src/types/api.ts)의 `ApiResponse<T>`입니다. `data: T` 선언은 런타임 null을 제거하지 않습니다. 이 문서에서는 컴파일 가능한 타입을 복제하지 않으며 호출자는 엔드포인트의 실제 빈 값 동작을 처리해야 합니다.

---

## 2. 오류 코드 참조표

### 2.1 시스템 오류 코드

HTTP 상태와 `R.code`를 따로 확인합니다.

| HTTP 상태 | 업무 코드 | 현재 매핑 |
|---|---|---|
| 200 | 200 | 일반 업무 성공 |
| 200 | 예외 발생 지점에서 지정한 코드, 기본 500 | `BusinessException`; 업무 400/403/404/409/503 포함 |
| 400 | 400 | 해당 advice가 처리하는 Bean 검증, 바인딩, query 누락, 타입 불일치 |
| 403 | 403 | `AccessDeniedException/AuthorizationDeniedException` |
| 404 / 405 / 415 | HTTP 상태와 동일 | advice가 처리하는 리소스·메서드·미디어 타입 오류 |
| 500 | 500 | 전용 처리기가 없는 예외 |

JSON 읽기 오류 매핑은 서비스마다 다릅니다. SRM/Procurement advice는 `HttpMessageNotReadableException`을 400으로 변환하지만 현재 Asset의 공통 처리기는 500을 반환합니다. 통제된 MockMvc에서 Asset 금액에 JSON number 또는 잘못된 십진수 문자열을 보내면 500이 관찰되었습니다. 유효한 요청이라는 뜻이 아니며 모든 서비스의 상태가 같다는 보장도 아닙니다.

### 2.2 업무 오류 코드

업무 코드는 현재 예외 발생 지점이 결정하며 message로 추측하지 않습니다. CAPTCHA·계정·테넌트 실패에는 기본 500을 사용하는 경로가 있습니다. 업무 400, 403, 404, 409, 503도 HTTP 200 본문에 포함될 수 있습니다. 프런트엔드에 410, 423, 429, 502 표시 분류가 있어도 모든 엔드포인트가 그 분기를 구현한다는 의미는 아닙니다.

### 2.3 Gateway 오류 코드

| HTTP 상태 / code | 조건 | 현재 message |
|---|---|---|
| 401 / 401 | Bearer 인증 없음 | 请先登录 |
| 401 / 401 | JWT 만료 | 登录已过期，请重新登录 |
| 401 / 401 | 폐기 기록 일치를 포함한 기타 JWT/보안 검증 실패 | 认证失败，请重新登录 |
| 503 / 503 | test 이외 설정에서 서명 키 누락·취약 또는 서명 생성 실패 | 网关身份签名未就绪，请联系管理员 |

이는 `AuthFilter`의 분기입니다. 다른 필터, 요청 제한, 하위 서비스, 프레임워크는 별도의 오류 매핑을 사용합니다. 표의 중국어는 진단용 실제 message 값이며 프런트엔드 인증 상태 판단에 사용하지 않습니다.

### 2.4 소셜 로그인 오류 코드

`user_denied/invalid_callback/social_login_failed`는 302 Location의 query 값이며 `R.code`가 아닙니다. 조건과 이동 주소는 10절에서 정의합니다.

### 2.5 프런트엔드 오류 처리

[request.ts](../omni-frontend/src/api/request.ts)와 [error-display.ts](../omni-frontend/src/utils/error-display.ts)는 HTTP 상태, 업무 코드, 응답이 없을 때의 Axios 전송 오류 코드를 분류합니다. HTTP 401/403이 본문의 업무 코드보다 우선하며 나머지는 업무 코드, HTTP 상태 순입니다.

- 업무 실패는 `businessCode/httpStatus/traceId/i18nKey/displayMessage`가 있는 `ApiError`를 던집니다. HTTP 실패는 AxiosError를 유지하고 정보를 추가합니다. 두 경로가 같은 생성자를 사용하는 것은 아닙니다.
- 서버·Axios의 원본 message를 직접 표시하지 않습니다. 코드 기반의 4개 언어 안전 문구를 사용하며 400 필드 힌트에는 검증된 ASCII 필드명만 허용합니다.
- 401이어도 자격 증명 엔드포인트는 세션과 입력을 보존합니다. 다른 요청은 세션 유무로 미로그인과 만료를 구분하며 둘 다 `shouldLogout=true`로 안전하게 세션을 정리하고 로그인으로 이동합니다. 자격 증명 분류에는 `/auth/login`, `/auth/session-login`, `/auth/register`, `/auth/social-login`, `/srm/portal/auth/login`이 포함됩니다. 이 프런트엔드 분류는 Gateway 허용 목록과 다릅니다.
- 403은 세션을 보존합니다. 400/409/5xx, 네트워크 실패와 시간 초과는 각 분류의 번역 문구를 사용하며 기술 메시지로 의미를 추측하지 않습니다. 충돌 데이터 새로고침과 재시도 여부는 호출자가 결정합니다.

---

## 3. 페이지네이션 계약

백엔드 타입은 [PageResult.java](../omni-backend/omni-common-core/src/main/java/com/omni/common/core/result/PageResult.java), 프런트엔드 타입은 [src/types/api.ts](../omni-frontend/src/types/api.ts)에서만 정의합니다. 응답 data에는 다음 필드가 있습니다.

| 필드 | 의미 |
|---|---|
| `records` | 현재 페이지 레코드 |
| `total` | 전체 레코드 수 |
| `size` | 페이지 크기 |
| `current` | 현재 페이지 번호 |
| `pages` | 생성자가 `(total + size - 1) / size`로 계산 |

일반적인 query 예는 `page=1&size=10`입니다. 범위, 기본값, 필터와 상한은 각 Controller/조회 DTO가 정의하며 예제 기본값이 모든 엔드포인트의 제한은 아닙니다. 생성자는 전달받은 records를 유지하고 전역 null→[] 변환이나 페이지 인수 검증을 제공하지 않습니다. 유효한 size를 전달하고 빈 레코드는 해당 엔드포인트 규약에 따라 처리합니다.

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

**Gateway 경로 접두사**: 일반 프런트엔드 업무 리소스는 `/api/<service>/<resource>`를 사용합니다. 예는 /api/auth/user/list입니다. Auth, Base, Workflow, CRM, SRM, Procurement, Asset 업무 경로는 StripPrefix 없이 전체 경로를 유지합니다. 프로토콜·내부 경로는 각 엔드포인트 정의에 따릅니다.

표는 업무 리소스 명명 규칙입니다. 작업 명령, OAuth2/OIDC, 내부 API는 실제 Controller 경로를 사용하며 예제로 미구현 엔드포인트를 추론하면 안 됩니다.

---

## 5. Gateway 라우팅 설정

### 5.1 로컬 개발 환경 경로

현재 [Gateway application.yml](../omni-backend/omni-gateway/src/main/resources/application.yml)의 `spring.cloud.gateway.server.webflux.routes`에는 명시적 경로 12개가 있으며 서비스 검색 자동 라우팅은 비활성화되어 있습니다.

| 경로 ID | Path 일치 | 대상 |
|---|---|---|
| `block-internal-api` | `/internal/**,/api/internal/**` | `no://op` |
| `block-internal-api-services` | `/omni-auth/internal/**,/omni-auth/api/internal/**,/omni-base/internal/**,/omni-base/api/internal/**,/omni-workflow/internal/**,/omni-workflow/api/internal/**,/omni-crm/internal/**,/omni-crm/api/internal/**,/omni-srm/internal/**,/omni-srm/api/internal/**,/omni-procurement/internal/**,/omni-procurement/api/internal/**,/omni-asset/internal/**,/omni-asset/api/internal/**,/omni-gateway/internal/**` | `no://op` |
| `omni-auth-oauth2` | `/oauth2/**` | `lb://omni-auth` |
| `omni-auth-wellknown` | `/.well-known/**` | `lb://omni-auth` |
| `omni-auth` | `/api/auth/**` | `lb://omni-auth` |
| `omni-base` | `/api/base/**` | `lb://omni-base` |
| `omni-base-job` | `/api/job/**` | `lb://omni-base` |
| `omni-workflow` | `/api/workflow/**` | `lb://omni-workflow` |
| `omni-crm` | `/api/crm/**` | `lb://omni-crm` |
| `omni-srm` | `/api/srm/**` | `lb://omni-srm` |
| `omni-procurement` | `/api/procurement/**` | `lb://omni-procurement` |
| `omni-asset` | `/api/asset/**` | `lb://omni-asset` |

일반 업무 경로는 `StripPrefix` 없이 전체 경로를 유지합니다. 두 `no://op` 경로는 `SetStatus=404`로 내부 경로를 차단하지만 인증 등 선행 필터가 자체 오류를 먼저 반환할 수 있습니다.

### 5.2 Docker 배포 경로

공식 Compose는 Nacos로 같은 `lb://` 서비스 이름을 해석합니다. 접근 가능 여부는 환경, 설정 재정의, 시작된 모듈에 달려 있으며 경로 설정만으로 하위 서비스의 정상 상태를 증명하지 못합니다. 예를 들어 `/api/base/dict/type/list`는 그대로 Base에 전달되고 `/api/job/**`도 Base로 연결됩니다.

### 5.3 AuthFilter 허용 목록

정확한 일치: `/api/auth/login`, `/api/auth/session-login`, `/api/auth/register`, `/api/auth/captcha`, `/api/auth/tenants`, `/favicon.ico`.

접두사 일치: `/api/auth/oauth2/`, `/actuator/`, `/oauth2/`, `/.well-known/`, `/login`, `/error`. startsWith 판정이며 정확한 일치로 바꾸면 안 됩니다. `/api/auth/**` 전체를 공개한다는 의미도 아닙니다.

허용 목록은 이 필터의 JWT 검증만 생략하며 경로, Controller, 하위 권한을 생성하지 않습니다. 공개 요청에서도 위조된 신뢰 헤더와 Bearer Authorization을 제거하고 OAuth2에 필요한 Basic Authorization은 유지하며 `X-Gateway-Forwarded=true`를 설정합니다.

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

### 8.1 Gateway가 설정하는 요청 헤더

보호 경로의 JWT 검증 후 Gateway는 클라이언트 Authorization과 같은 이름의 신뢰 헤더를 제거하고 다음을 설정합니다.

| 헤더 | 출처/의미 |
|---|---|
| `X-User-Id` | JWT 사용자 ID, 양의 정수 |
| `X-Tenant-Id` | JWT 테넌트 ID, 양의 정수 |
| `X-User-Name` | JWT 사용자 이름, `X-Username`이 아님 |
| `X-User-Roles` | 역할 claim |
| `X-User-Scopes` | 권한 claim |
| `X-Gateway-Forwarded` | `true` |
| `X-Gateway-Assertion` | Gateway가 생성한 식별 assertion |

역할·권한 claim이 List이면 쉼표로 연결하고 다른 non-null 값은 문자열로, 누락은 빈 문자열로 처리합니다. assertion은 `userId|tenantId|username|issuedAt`에 HMAC-SHA256을 적용하며 padding 없는 `base64url(payload):base64url(signature)` 형식입니다. test 이외 설정은 강한 비밀 키가 필요합니다. 각 서비스는 사전 인증 체인으로 검증해야 하며 같은 이름의 헤더가 있다는 이유만으로 신원을 신뢰하면 안 됩니다.

### 8.2 프런트엔드 요청 헤더

Axios는 `useUserStore().token`에서 `Authorization: Bearer <JWT>`를 설정하고 `getTenantIdFromToken(token)`으로 테넌트 헤더를 얻습니다. 독립적으로 수정 가능한 테넌트 필드를 신뢰하는 방식이 아닙니다. Gateway는 보호 요청의 신뢰 헤더를 덮어씁니다. JSON Content-Type은 실제 본문에 따르며 공유 Axios 인스턴스에 명시적인 전역 Content-Type 기본값은 없습니다.

### 8.3 내부 서비스 요청 헤더

이 문서의 업무 내부 API는 `X-Internal-Token`과 명시적 `X-Tenant-Id`를 사용하며 최종 사용자 JWT로 내부 인증을 대체하지 않습니다. JSON 본문은 `Content-Type: application/json`을 사용합니다. body/query tenantId가 필요한 엔드포인트는 헤더와도 비교하며 불일치 오류는 각 절에서 정의합니다.

검증기는 서비스 설정에 따라 적용됩니다. `omni-common-service` 내부 필터는 잘못된 요청 토큰에 HTTP 401을 반환하고, 관련 기능에 필요한 비밀 토큰이 없거나 취약하면 설정 검증기가 시작을 막습니다. 호환 MQ `InternalApiAuthFilter`는 서버 토큰 미설정 시 HTTP 503, 요청 토큰 누락·불일치 시 401입니다. 이를 모든 서비스의 공통 런타임 503 분기로 설명하면 안 됩니다.

내부 경로는 Gateway가 명시적으로 차단하며 클라이언트 `X-Gateway-Forwarded`나 역할·권한 헤더를 신뢰하지 않습니다. 신뢰된 내부 호출자도 요청마다 올바른 업무 테넌트를 전달해야 하며 토큰 자체가 테넌트를 결정하지 않습니다.

### 8.4 보안 응답 헤더(Gateway 설정)

| 응답 헤더 | 현재 값 |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `X-Trace-Id` | 실제 tracer의 trace ID, span이 없으면 하이픈을 제거한 32자 UUID |

[SecurityHeadersFilter](../omni-backend/omni-gateway/src/main/java/com/omni/gateway/config/SecurityHeadersFilter.java)는 공개 요청의 같은 이름 trace 헤더를 제거합니다. 실제 tracer span이 있으면 해당 ID를 하위 요청과 응답에 넣습니다. span이 없으면 응답 전용 fallback을 만들고 하위 요청에는 넣지 않습니다. 응답 헤더만으로 Gateway, Servlet, Feign 전체의 연계를 증명할 수 없습니다. 프런트엔드는 1–128자의 안전한 ASCII traceId만 허용하며 비정상 값을 거부합니다.

---

## 9. 인증 헤더

프런트엔드 인터셉터는 `Authorization: Bearer <token>`을 설정합니다. Gateway는 JWT 서명, 만료, 양의 사용자·테넌트 ID와 해당 폐기 기록을 검증한 뒤 식별 정보를 넣습니다. 현재 폐기 검사는 JWT jti에 의존하므로 jti 없는 토큰까지 블랙리스트를 조회했다고 설명하면 안 됩니다. 정확한 일치·접두사 공개 범위는 5.3절에서만 관리하며 사용자·역할 관리에는 유효한 인증과 엔드포인트 권한이 필요합니다.

---

## 10. 소셜 로그인 엔드포인트

다음 브라우저 탐색 엔드포인트는 정상 인가 흐름에서 HTTP 302를 반환하며 `R<T>`로 감싸지 않습니다. 제공자 식별자는 `github`, `google`, `gitee`입니다.

| 메서드 | URL / 매개변수 | 계약 |
|---|---|---|
| GET | `/api/auth/oauth2/{provider}?tenant_id=1` | `tenant_id` 필수. 제공자·테넌트를 검사한 뒤 제공자 인가 페이지로302 |
| GET | `/api/auth/oauth2/{provider}/callback`. `code`, `state`, `error`는 선택 바인딩 인수 | 아래 거부, 누락, 업무 실패, 성공을 처리. 선택 바인딩이 성공 시 code/state를 생략해도 된다는 의미는 아님 |

| 콜백 조건 | HTTP 302 Location |
|---|---|
| 제공자 `error`가 공백이 아님 | 프론트엔드 `/login?error=user_denied` |
| `code` 또는 `state` 누락/공백 | 프론트엔드 `/login?error=invalid_callback` |
| state 검증·제공자 코드 교환 실패 등 `BusinessException` | 프론트엔드 `/login?error=social_login_failed&message=<URL인코딩 메시지>` |
| 성공 | 설정된 프론트엔드 `/callback#token=<URL인코딩JWT>&username=<URL인코딩사용자이름>` |

`OAuth2StateUtils` 형식은 `tenantId|timestamp|hex(HMAC-SHA256(tenantId|timestamp, secret))`입니다. 무결성을 검사하고 10분보다 오래된 시각을 거부한 뒤 테넌트를 복원합니다. 현재 코드는 소비 완료 표시, 임의 nonce와 브라우저 세션 바인딩이 없어 완전한 일회성 CSRF·재전송 방어를 증명하지 않습니다. 앱 자격 증명은 외부 사용자의 로그인·동의를 대신하지 않습니다.

정식 Docker Compose 콜백은 Gateway를 통해 `http://localhost:8102/api/auth/oauth2/{provider}/callback`으로 진입합니다. 운영은 설정된 Frontend/Gateway HTTPS 도메인을 사용합니다. 제공자 등록은 실제 redirect_uri와 일치해야 하며 비공개 컨테이너 주소를 공개 진입점으로 등록하지 않습니다. 시작 단계 인수·제공자·테넌트 검증 실패는 Auth 오류 매핑을 따르므로302를 보장하지 않습니다.

소스: [SocialLoginController](../omni-backend/omni-auth/src/main/java/com/omni/auth/controller/SocialLoginController.java), [OAuth2StateUtils](../omni-backend/omni-auth/src/main/java/com/omni/auth/util/OAuth2StateUtils.java), [Compose 설정](../compose.apps.yaml). 설정·브라우저 절차·실패 재현은 [인증 가이드](guides/authentication.kr.md)가 관리합니다. 로컬 인가 코드·PKCE와 장치 코드는 별도 프로토콜입니다.

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
PUT /api/auth/xss-config/toggle?enabled=true
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
| PUT | `/api/auth/xss-config/rules/{id}/toggle?enabled=true` | `system:xssconfig:update` | 규칙 활성화 상태 전환 |

**ruleType 열거값**: `HTML_TAG` | `EVENT_HANDLER` | `DANGEROUS_PROTOCOL` | `CUSTOM_PATTERN`

### 권한 코드

| 권한 코드 | 설명 |
|--------|------|
| `system:xssconfig:list` | XSS 설정 및 규칙 조회 |
| `system:xssconfig:update` | 전역 스위치 전환, 규칙 수정, 규칙 상태 전환 |
| `system:xssconfig:create` | 규칙 생성 |
| `system:xssconfig:delete` | 규칙 삭제 |

규칙 갱신·삭제·상태 전환은 Gateway가 주입한 `X-Tenant-Id`로 소속을 검사합니다. 없거나 다른 테넌트의 규칙이면 쓰기와 캐시 무효화 전에 HTTP 200 + `R.code=404`를 반환합니다. 양의 정수 테넌트 검증 실패는 업무 코드 400입니다. 기존 권한 코드는 유지합니다.

스위치 API의 `enabled`는 필수 쿼리 파라미터입니다. 생성 시 `ruleName`, `ruleType`, `pattern`은 비어 있을 수 없고, 갱신은 null 아닌 필드를 처리하며 제출된 pattern의 정규식 구문을 검사합니다. 지원 형식은 위와 같지만 현재 DTO는 열거형을 검증하지 않으며 알 수 없는 실행 형식은 정화하지 않습니다. 적용 조건, Jackson 2/3, 권위 조회와 캐시 경계는 [핵심 흐름](core-flows.kr.md)을 참고합니다.

---

## 12. Base 서비스 사전 관리 엔드포인트

Base는 Gateway의 `/api/base/**` 경로로 전체 경로 API를 제공한다. 신원 체인과 기본 포트는 핵심 플로우를 참조한다. 유형과 데이터는 각 6개 엔드포인트이며 검증된 현재 테넌트 문맥을 사용한다.

### 사전 유형 엔드포인트

기본 경로: `/api/base/dict/type`

| 메서드 | 경로 | 권한 | 동작 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/type/list` | `dict:type:list` | 테넌트 페이지 조회; typeCode/typeName 부분 일치, 선택 status, sort/id 오름차순 |
| GET | `/api/base/dict/type/{id}` | `dict:type:list` | 현재 테넌트 유형 조회; 누락·다른 테넌트 ID는 비즈니스 코드 404 |
| POST | `/api/base/dict/type` | `dict:type:create` | typeCode/typeName 공백 불가; 유형 코드는 테넌트 내 고유 |
| PUT | `/api/base/dict/type/{id}` | `dict:type:update` | null 아닌 필드만 수정; typeCode 변경 불가 |
| DELETE | `/api/base/dict/type/{id}` | `dict:type:delete` | 소유 검증 후 같은 테넌트·유형 데이터 연쇄 삭제 |
| PUT | `/api/base/dict/type/{id}/status?status=...` | `dict:type:update` | status 쿼리 필수; 수정 후 캐시 무효화 |

페이지 기본값은 `page=1,size=10`; 유형은 `typeCode/typeName/status`를 선택 필터로 사용한다. 유형 생성의 `remark/sort`, 데이터 생성의 `tagType/remark/sort`는 선택값이다. 서비스가 `status=1`, sort 누락 시 0을 설정한다.

### 사전 데이터 엔드포인트

기본 경로: `/api/base/dict/data`

| 메서드 | 경로 | 권한 | 동작 |
| --- | --- | --- | --- |
| GET | `/api/base/dict/data/options?typeCode=...` | 인증됨; 별도 동작 권한 없음 | 인증만 필요; value/label의 활성 데이터 옵션을 캐시해 반환 |
| GET | `/api/base/dict/data/list?typeCode=...` | `dict:data:list` | 관리 페이지는 DB 직접 조회; typeCode 필수, sort/id 오름차순 |
| POST | `/api/base/dict/data` | `dict:data:create` | typeCode/dictValue/dictLabel 공백 불가; 현재 테넌트 부모 존재 검증 |
| PUT | `/api/base/dict/data/{id}` | `dict:data:update` | null 아닌 필드만 수정; DTO에 typeCode 없음 |
| DELETE | `/api/base/dict/data/{id}` | `dict:data:delete` | 소유 검증 후 삭제 및 캐시 무효화 |
| POST | `/api/base/dict/data/refresh-cache?typeCode=...` | `dict:data:refresh` | typeCode 쿼리 필수; 키 삭제 후 활성 데이터 조회·재적재 |

수정 DTO는 null 아닌 필드만 처리하므로 null 제출로 필드를 비우는 동작은 없다. `/options`는 `value`와 `label`만 가진 `R<List<DictOptionVO>>`를 반환한다. 관리 페이지는 `R<PageResult<SysDictType>>` 또는 `R<PageResult<SysDictData>>`를 반환한다.

### 사전 권한 코드

| 권한 코드 | 용도 |
| --- | --- |
| `dict:type:list` | 유형 목록·상세 |
| `dict:type:create` | 유형 생성 |
| `dict:type:update` | 유형 수정·상태 변경 |
| `dict:type:delete` | 유형 연쇄 삭제 |
| `dict:data:list` | 관리 데이터 목록 |
| `dict:data:create` | 데이터 생성 |
| `dict:data:update` | 데이터 수정 |
| `dict:data:delete` | 데이터 삭제 |
| `dict:data:refresh` | 캐시 갱신 |

### 테넌트 격리

목록·생성·옵션·갱신은 테넌트를 명시적으로 전달한다. ID 기반 읽기·쓰기 진입점 6개도 Gateway의 `X-Tenant-Id`를 서비스에 전달하고 양수 ID와 소유를 검증한 후 반환·쓰기·캐시 작업을 수행한다. 유형 고유 제약은 `(tenant_id,type_code)`이다.

누락·다른 테넌트 ID 리소스는 모두 `BusinessException(404)`를 발생시키며 공통 핸들러가 HTTP 200 + `R.code=404`로 매핑한다. 테넌트 ID 누락 또는 0 이하는 비즈니스 코드 400이다. Gateway와 메서드 권한 검증은 별도로 수행한다. 캐시·클라이언트 수명 주기·소스 진입점은 핵심 플로우와 프런트엔드 규칙에 모은다.

[Flow 9](core-flows.kr.md) / [Composable](frontend-patterns.kr.md)

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

설정으로 계산한 결과입니다. `outboxWriteEnabled=true`, `deliveryEnabled = omni.mqlog.relay.enabled && xxl.job.executor.enabled`(두 조회 모두 기본 true)에 따라 mode는 `FULL/OUTBOX_ONLY`입니다. relay 프로세스, XXL-JOB 스케줄, Broker, DB, 메시지 전달 여부를 탐지하지 않습니다. FULL은 관련 스위치 활성화, OUTBOX_ONLY는 적어도 하나의 전달 스위치 비활성화를 뜻합니다. 실제 신뢰성과 복구는 [MQ 신뢰성](mq-reliability.kr.md)을 참조하세요.

---

## 13. API 버전 관리 전략

현재 Gateway와 Controller는 `/api/auth/user/list`처럼 버전 접두사가 없는 `/api/...` 경로를 사용합니다. `/api/v1/...` 또는 `/api/v2/...` 병렬 경로는 구현되어 있지 않습니다.

향후 URL 버전, 호환성을 깨는 변경의 마이그레이션과 구버전 유지 기간은 구현된 계약이 아닙니다. 필요하면 설계·ADR에서 소비자, 호환 범위, 마이그레이션과 지원 기간을 먼저 정하고 구현·문서를 동기화합니다. 미구현 접두사나6개월 유지 보수를 약속하지 않습니다. 현재 필드·오류·상태는 이 계약과 소스를 따릅니다.

---

## 14. Null 의미론

`R.ok()`와 실패 응답의 data는 null이며 현재 일반 mapper는 그 null 필드를 출력합니다. 생산자가 실제로 빈 List를 반환할 때만 []가 됩니다. `PageResult`는 null records를 유지하며 전역 변환 보장은 없습니다. 선택 필드의 null, 빈 문자열, 생략 동작은 DTO, serializer와 엔드포인트에 따릅니다.

JSON 바인딩, 업무 입력 필드, 신원 출처는 별개 제약입니다. 알 수 없는 속성이 일괄적으로 400을 반환하지는 않으며 통제된 현재 Asset DTO 바인딩은 추가 속성을 무시했습니다. DTO에 없는 tenant, user, 금액 스냅샷이 제어 가능한 입력이 된다는 뜻은 아니지만 파서가 모든 위조 속성을 명시적으로 거부한다는 보장도 없습니다. 문서에 정의된 필드만 보내세요.

---

## 15. SRM MVP 계약

### 15.1 공급업체 및 하위 리소스

- 관리측 공급업체 수명주기 명령은 모두 `version` 을 포함합니다; 블랙리스트 복구는
  `POST /api/srm/supplier/{id}/restore-from-blacklist` 를 사용합니다.
- 연락처, 자격, 은행 계좌 경로의 `supplierId` 는 하위 리소스의 실제 귀속과 일치해야 합니다; 불일치는 일괄 404 를 반환합니다.
- `creditCode` 는 테넌트 내 유일; 페이지네이션 `size` 는 최대 100.
- 공급업체 360 은 `GET /api/srm/supplier/{id}/overview` 를 사용하며, 반환 내용은 여전히 호출자의 하위 리소스 권한과 PII 권한으로 트리밍됩니다.

### 15.2 포털 등록

`POST /api/srm/portal/enroll` 은 Gateway 가 주입한 tenant/user 신원만 허용하며, 요청은 tenantId 나 userId 를 포함해서는 안 됩니다:

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

입점 DTO 필수 필드는 requestId(공백 불가,최대64), inviteToken(공백 불가,최대256), name(공백 불가,최대200), creditCode(공백 불가,최대50)입니다. creditCode는 trim 후 대문자로 변환하며 예제는 실제 등록 기업 코드가 아닙니다. 일반적인 알 수 없는 속성 동작과 달리 이 DTO는 JsonAnySetter로 tenantId/userId를 포함한 모든 미정의 속성을 명시적으로 거부합니다. SRM advice는 JSON 읽기 예외를400으로 변환합니다.

### 15.3 평가 및 리스크

- `GET /api/srm/evaluation/template/default/dimensions` 는 현재 테넌트의 기본 템플릿과 유효 차원을 반환합니다; 프론트엔드는 데이터베이스 ID 를 하드코딩해서는 안 됩니다.
- 점수 범위는 1–5 이며, 기본 템플릿의 모든 차원을 커버하고 중복되어서는 안 됩니다.
- `GET /api/srm/risk/list` 는 공급업체별 최신 평가로 집계된 리스크 요약 페이지네이션을 반환하며, `riskLevel` 로 필터링할 수 있습니다.
- `GET /api/srm/supplier/{id}/risk` 는 `indicators/latestAssessment/history` 집계 뷰를 반환합니다.
- 리스크 지표 갱신에는 `version`이 필요합니다. 종합 등급이 RED가 아닌 상태에서 RED로 전환될 때만 `srm.risk.level-changed.v1`을 생성합니다.

평가 생성은 서비스가 테넌트의 활성 기본 템플릿을 선택하며 DTO에 templateId는 없습니다. items는 활성 차원 전체를 중복 없이 포함하는 1–100개 항목입니다. score는 1–5이고 소수는 최대 한 자리입니다. dimensionId는 기본 템플릿 API에서 얻으며 템플릿, 차원 이름, 가중치는 서버가 결정합니다.

### 15.4 내부 공급업체 요약

후속 Procurement/Asset 은 `X-Internal-Token` 과 `X-Tenant-Id` 를 동시에 포함할 때만 호출할 수 있습니다:

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
      "unit": "EA",
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
| `lines[].remark` | String | 아니요 | 최대500, 서비스가 trim 후 공백 정규화 |

`tenantId/supplierId/rfqNo/material/quantity/currencyCode/lineAmount/totalAmount`는 이 DTO의 제어 가능한 입력이 아닙니다. 서비스가 신뢰된 신원, PortalUser, Procurement 초대 스냅샷에서 읽거나 계산합니다. 추가 JSON 속성은 무시될 수 있으며 명시적 오류를 보장하지 않습니다. `R<QuotationVO>` 응답은 헤더, version, 행 스냅샷을 포함하며 금액·수량 전송 형식은 다음 절에서 정의합니다.

멱등 및 동시성 규칙:

- `srm_quotation_request`는 테넌트 내 requestId, 정규화 SHA-256, quotationId, targetVersion을 저장합니다. 과거 COMPLETED 요청은 신규 요청의 공급업체 상태, RFQ, 마감, 초대 행 검증보다 먼저 재생됩니다. 현재 견적 스냅샷을 반환하고 견적·Outbox를 다시 쓰지 않으며 신규 제출 검증 전체를 반복하지 않습니다.
- 같은 requestId의 hash, supplierId, rfqId가 다르거나 기록이 RESERVED·잘못된 상태이거나 현재 견적이 targetVersion보다 오래되면 업무 409입니다.
- 신규 요청은 현재 승인된 공급업체가 제출하며 SENT RFQ, INVITED/QUOTED 초대, 미만료 마감을 다시 읽습니다. validUntil은 현재보다 이후이며 RFQ 마감 이상이어야 합니다. 1–200개 행으로 전체 RFQ 행을 누락·추가·중복 없이 포함합니다. 의존성 사용 불가 시 503으로 차단합니다.
- `(tenantId, rfqId, supplierId)`당 미삭제 견적은 최대 하나입니다. 최초 version=0은 version 1로 저장하며 수정은 현재 version을 사용하고 충돌은 409입니다.
- requestHash는 requestId를 제외한 `rfqId/version/validUntil/lines`를 대상으로 합니다. rfqLineId 순으로 정렬하고 단가는 소수 6자리 비지수 문자열로 정규화하며 비고는 trim하고 null/공백을 통일합니다. 원본 JSON 바이트를 직접 해시하지 않습니다. `unitPrice × quantity`를 소수 4자리 HALF_UP으로 반올림한 뒤 합산합니다.

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
`R<T>` 를 계속 사용합니다. 상세한 실행 메커니즘은 [workflow.kr.md](workflow.kr.md#6-내부-시작과-완료-이벤트) 참조.

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

멱등 기록은 테넌트 내 고유 키 `(tenantId, requestId)`와 `(tenantId, businessType, businessKey)`를 사용합니다. 충돌 시 업무 유형·키, modelVersionId, startUserId를 비교합니다. title, variables, startUserName은 비교하지 않으며 전체 payload hash도 없습니다.

성공 기록은 원래 processInstanceId와 replayed=true를 반환하지만 requestId는 이번 요청값을 사용합니다. 처리 중이거나 업무·모델·시작 사용자 의도가 다르면 업무 409입니다. 같은 requestId의 FAILED 분기는 원자적으로 재시도를 선점할 수 있지만 다른 requestId로 같은 FAILED 업무 키에 일치하면 그 재시도 분기로 자동 진입하지 않습니다.

실제 신규 시작은 PUBLISHED와 비어 있지 않은 processDefinitionId를 재검증하고 `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` 분류를 정확히 일치시킵니다. title이 공백이면 `{businessType}:{businessKey}`입니다. 이 검사로 임의 variables가 신뢰된 신원이 되지는 않습니다. 시작 사용자, 테넌트, 보호 변수와 라우팅 제약은 [Workflow](workflow.kr.md)를 참조하세요.

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

헤더와 본문의 tenantId가 다르면 먼저 업무 403입니다. 나머지 검사는 Flowable 작업 테넌트, 확장 기록 테넌트, businessType/businessKey를 맞추고 현재 ASSIGNEE 또는 미수령 작업의 CANDIDATE를 확인합니다. 작업 없음, 업무 불일치, 자격 없음은 `R.code=200,data.valid=false`이며 assignmentType은 `ASSIGNEE/CANDIDATE/NONE`입니다. false는 승인 명령 실행 시 권한·동시성 검사를 대체하지 않습니다.

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

연결된 승인 완료와 명시적 종료 경로는 완료 상태·completionEventId 갱신과 PENDING Outbox 삽입을 같은 로컬 트랜잭션에서 처리합니다. `completion_event_id IS NULL`이 논리 완료 이벤트 중복을 막습니다. 모든 Flowable engine-end나 즉시 종료를 다루는 공통 리스너는 없습니다. 성공·Outbox 커밋은 Broker 전달·소비 완료를 뜻하지 않습니다. 전달 기능이 활성화되고 의존성이 사용 가능할 때 relay가 비동기로 전달·재시도하며 소비자는 eventId로 멱등 처리합니다.

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

조회는 테넌트를 제한하며 PUBLISHED와 비어 있지 않은 processDefinitionId를 요구합니다. 없거나 사용 불가한 버전은 업무 404입니다. modelKey와 BPMN process id의 일치는 게시 검증이 유지하며 반환 문자열만으로 현재 engine 실행 가능성을 증명할 수 없습니다.

분류별 현재 버전은 `GET /api/internal/workflow/model-version/current?category={category}`로도 조회합니다. 신규 시작은 `ASSET_TRANSFER/ASSET_DISPOSAL/SRM_SUPPLIER_ONBOARDING` 분류를 연결하고 같은 의도의 성공 재생은 기존 인스턴스를 사용합니다. 내부 종료는 `POST /api/internal/workflow/process-instance/{processInstanceId}/terminate?reason={reason}`이며 내부 토큰 외에 X-Tenant-Id, X-User-Id를 명시합니다. reason은 선택입니다.

### 16.5 승인 규칙 읽기 전용 모델 집계

| 메서드 | 경로 | 현재 제약 |
|---|---|---|
| GET | `/api/internal/workflow/model-versions/published?category=purchase` | 현재 테넌트, 정확한 분류, 활성 모델, 현재 게시 버전, 비어 있지 않은 processDefinitionId |
| POST | `/api/internal/workflow/model-versions/resolve` | body `{ "modelVersionIds": [1, 2] }`; 양의 정수 1–200개를 최초 등장 순으로 중복 제거 |
| GET | `/api/internal/workflow/model-version/{id}/preview` | 안전한 승인 그래프·메타데이터, 원본 BPMN XML/designerJson 제외 |

availability는 `AVAILABLE/NOT_CURRENT/UNAVAILABLE/MODEL_ARCHIVED/NOT_FOUND`이며 모델·버전 메타데이터로 계산합니다. Flowable 배포, 일시 중지, 실행 가능성을 실시간으로 탐지하는 값이 아닙니다.

루트 필드는 `approvalPreviewVersion/modelVersion/nodes/edges/hasBranches/linearSummary`입니다. nodes는 `id/name/type/roleCode/approvalMode/description`, type은 `START/END/APPROVAL/GATEWAY/SERVICE`입니다. edges는 `id/name/source/target/defaultBranch/conditionSummary`를 포함합니다. 조건은 실제 고정 문구 “已配置条件（内容已隐藏）”만 반환합니다. 분기 또는 단일 경로 요약을 만들 수 없는 경우 linearSummary는 null입니다. 현재 조직 미리보기를 미래의 실제 승인자로 간주하면 안 됩니다.

### 16.6 디자이너 후보자 미리보기

`POST /api/workflow/identity/resolve-preview`는 `workflow:identity:list`와 신뢰된 테넌트 헤더가 필요합니다. 요청은 `assignmentType/roleCode/anchorType/anchorParams/scopeMode/simulateUserId`이며 assignmentType은 필수입니다. 응답 data는 `candidateCount/candidates`, 후보자는 `userId/username/nickname/unitName`을 포함합니다. 해석 실패·호환 모드·기준점 의미는 [Workflow 3절](workflow.kr.md#3-후보자-리스너와-다중-승인)을 참조하고 내부 안전 그래프 DTO와 혼용하지 마세요.

### 16.7 인스턴스 조회 테넌트 경계

외부 `GET /api/workflow/process-instance/{id}/progress`와 `GET /api/workflow/process-instance/{id}/approval-records`는 Gateway X-Tenant-Id를 명시적으로 사용합니다. 테넌트 누락·잘못된 값은 실패하며 양수가 아니면 업무 400입니다. 동일 테넌트의 이력 인스턴스가 없거나 다른 테넌트 소유이면 활동·댓글·후보자 상세 조회 전에 모두 업무 404입니다. 참여자 ACL을 추가한 것은 아니며 모든 이력 정의 API에 같은 보호가 있음을 뜻하지도 않습니다.

## 17. Procurement MVP 계약

### 17.1 공통 경계

- 외부 기본 경로는 `/api/procurement`이며 전체 경로와 Gateway 신원·권한을 사용합니다. TenantLine은 proc_* 업무 테이블만 대상으로 하며 Outbox 스캔을 테넌트 필터링하지 않습니다.
- DataScope는 구매 요청에 requester_user_id/requester_unit_id, RFQ/PO/GR에 owner_user_id/owner_unit_id를 적용하고 하위 행은 집계 루트를 상속합니다. 자재, 분류, 승인 경로와 설정은 테넌트 내 공유이며 없는 owner 열을 추가하지 않습니다.
- 수량·단가는 DECIMAL(19,6), 금액은 DECIMAL(19,4)이며 응답은 십진수 문자열입니다. 요청도 DTO별 십진수 문자열 제약을 따르며 업무 금액을 JavaScript number로 계산하면 안 됩니다.
- 수정·삭제·버전 상태 명령은 DTO가 정의한 버전을 전달합니다. RFQ 선정은 rfqVersion/quotationVersion을 사용하므로 모든 쓰기에 version 필드가 있지는 않습니다. 생성에는 기존 행 버전이 없습니다.
- 내부 `/api/internal/procurement/**`는 X-Internal-Token/X-Tenant-Id가 필요하고 Gateway가 차단합니다. 상세 상태·트랜잭션은 [Procurement 설계](design/procurement-design.kr.md)를 참조하세요.

### 17.2 자재 및 품목

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/procurement/material/category/list` | `procurement:material:list` | 최대 2 단계 품목 트리 반환 |
| POST | `/api/procurement/material/category` | `procurement:material:create` | 품목 생성; `categoryCode` 는 생성 후 수정 불가 |
| PUT | `/api/procurement/material/category/{id}` | `procurement:material:update` | body 는 `version` 포함 |
| DELETE | `/api/procurement/material/category/{id}?version={version}` | `procurement:material:delete` | 하위 품목이나 자재가 존재하면 409 반환 |
| GET | `/api/procurement/material/list` | `procurement:material:list` | `keyword/categoryId/status/assetManaged/page/size` |
| GET | `/api/procurement/material/{id}` | `procurement:material:list` | 자재 상세 조회 |
| POST | `/api/procurement/material` | `procurement:material:create` | 자재 생성; `materialCode` 는 생성 후 수정 불가 |
| PUT | `/api/procurement/material/{id}` | `procurement:material:update` | body 는 `version` 포함 |
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
| PUT | `/api/procurement/rfq/{id}` | `procurement:rfq:update` | DRAFT 만; body 는 `version` 포함 |
| DELETE | `/api/procurement/rfq/{id}?version={version}` | `procurement:rfq:delete` | DRAFT 만 |
| POST | `/api/procurement/rfq/{id}/send` | `procurement:rfq:send` | body `{ "version": 0 }`, 초대된 공급업체에 게시 |
| POST | `/api/procurement/rfq/{id}/award` | `procurement:rfq:award` | 견적 버전을 잠그고 구매 주문을 원자적으로 생성 |
| POST | `/api/procurement/rfq/{id}/cancel` | `procurement:rfq:cancel` | body `{ "version": 0 }`; DRAFT/SENT 만 |

생성은 `requisitionId/title/quotationDeadline/supplierIds`, 초안 수정은 `version/title/quotationDeadline/supplierIds`를 받으며 requisitionId를 변경하지 않습니다. quotationDeadline은 `yyyy-MM-dd HH:mm:ss` 형식의 미래 시각이며 supplierIds는 양의 정수 1–100개입니다. SENT RFQ만 비교·선정할 수 있습니다. 초대 상태는 `INVITED/QUOTED/EXPIRED/AWARDED/REJECTED`이며 마지막 두 상태는 이력 조회만 허용하고 추가 견적은 금지합니다.

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
| POST | `/api/procurement/purchase-order/{id}/send` | `procurement:purchase-order:send` | DRAFT → SENT, body 는 `version` 포함 |
| POST | `/api/procurement/purchase-order/{id}/confirm` | `procurement:purchase-order:confirm` | SENT → CONFIRMED, body 는 `version` 포함 |
| POST | `/api/procurement/purchase-order/{id}/cancel` | `procurement:purchase-order:cancel` | 입고 발생 전 취소, body 는 `version` 포함 |

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

두 이벤트와 이력 후보는 입고 관리 귀속 `ownerUserId/ownerUnitId` 를 포함해야 하며, Asset 은 이를 새 자산의
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

- 외부 기본 경로는 `/api/asset`이며 전체 경로를 유지합니다. 식별 헤더는 `X-User-Id/X-Tenant-Id/X-User-Name` 및 역할·권한이며 X-Username이 아닙니다. TenantLine은 ast_*만 대상으로 하고 Outbox relay는 전체 테넌트를 스캔합니다.
- 관리 조회는 owner_user_id/owner_unit_id, 하위 리소스는 자산 범위를 상속합니다. 내 자산·수령·반환은 current_user_id로 고정하며 관리 권한으로 개인 자산 조회를 넓히지 않습니다.
- 수정·삭제·기존 자산 명령은 version을 전달하며 생성에는 기존 버전이 없습니다. 활성 작업 점유·버전 충돌은 쓰기를 거부합니다.
- 취득가·잔존가·집계 금액은 DECIMAL(18,2)이며 non-null 입출력은 JSON 십진수 문자열입니다. 숫자·잘못된 문자열은 거부하지만 현재 공통 JSON 읽기 예외는 500으로 매핑되므로 400을 보장하지 않습니다. currencyCode는 영문 3자만 검사하며 ISO 4217 등록 목록 검증은 아닙니다.
- 상태는 `IN_STOCK/ALLOCATED/IN_USE/MAINTENANCE/TRANSFER/DISPOSAL_PENDING/DISPOSED/SCRAPPED`입니다.
- 내부 `/api/internal/asset/**`는 X-Internal-Token/X-Tenant-Id가 필요하고 Gateway가 차단합니다. 생명주기·수령 입고·승인 트랜잭션은 [Asset 설계](design/asset-design.kr.md)를 참조하세요.

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
후 `SCRAPPED` 에 진입하며, 둘 다 복구 불가한 최종 상태입니다.

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
다른 사용자가 재시도를 실행할 때도 원래 시작 사용자 신원을 계속 사용하는 것을 포함하고, 두 번째 프로세스 인스턴스 생성을 금지합니다.

### 18.7 자산 개요

| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/api/asset/overview/summary` | `asset:overview:list` | 관리 범위 내 각 상태 수량 및 통화별 원가 |
| GET | `/api/asset/overview/distribution?dimension={dimension}&limit={limit}` | `asset:overview:list` | 상태, 품목, 관리 부서 또는 위치로 집계 |

`dimension` 은 필수이며 `STATUS/CATEGORY/DEPARTMENT/LOCATION` 만 허용; `limit` 은 기본 20, 범위 1–100.
모든 집계 SQL 은 관리 대장과 동일한 owner DataScope 와 TenantLine 을 적용해야 합니다. 금액은 통화별로 독립 기록을 유지하고 십진 문자열을 출력하며, 서로 다른 통화를 직접 더해서는 안 됩니다.
