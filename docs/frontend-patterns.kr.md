# 프론트엔드 패턴 및 규칙

> 이 문서는 현재 프런트엔드 구조, 공유 클라이언트 상태, 경로, 권한 표시, 언어와 생명 주기 제약을 관리합니다. 실제 동작은 소스와 설정이 결정합니다. 신규 코드 규칙이 기존 모든 행의 검증 완료를 뜻하지는 않습니다.
> 전송 형식과 오류 분류는 [API 계약](api-contract.kr.md), 업무 불변 조건은 도메인 사실·설계 문서가 관리합니다. 설치·기능 통합·운영·복구는 [Wiki](wiki/index.kr.md), [개발 가이드](guides/scaffold-development.kr.md), [Docker 배포](docker-deployment.kr.md)를 참조하세요.

---

## 목차

- [1. 기술 기준](#1-기술-기준)
- [2. 디렉터리 구조](#2-디렉터리-구조)
- [3. API 계층 (src/api/)](#3-api-계층-srcapi)
- [4. 상태 관리 (src/stores/)](#4-상태-관리-srcstores)
- [5. 라우팅 (src/router/)](#5-라우팅-srcrouter)
- [6. 컴포넌트 설계 패턴](#6-컴포넌트-설계-패턴)
- [7. 컴포넌트 규칙](#7-컴포넌트-규칙)
- [8. 명명 규칙 (TypeScript)](#8-명명-규칙-typescript)
- [9. 코드 포맷 (TypeScript / Vue)](#9-코드-포맷-typescript--vue)
- [10. UI 프레임워크](#10-ui-프레임워크)
- [11. 국제화(i18n)](#11-국제화i18n)
- [12. 권한 및 접근 제어](#12-권한-및-접근-제어)
- [13. 비즈니스 모듈 구조 패턴](#13-비즈니스-모듈-구조-패턴)
- [14. Composables 패턴](#14-composables-패턴)
- [15. 빌드와 정적 서비스 경계](#15-빌드와-정적-서비스-경계)
- [16. 빌드 및 도구](#16-빌드-및-도구)
- [17. 문제 해결 가이드](#17-문제-해결-가이드)

---

## 1. 기술 기준

Vue 3 Composition API, TypeScript, Pinia, Vue Router, Element Plus, vue-i18n과 Vite를 사용합니다. 선언 범위는 [package.json](../omni-frontend/package.json), 실제 해석 버전은 [package-lock.json](../omni-frontend/package-lock.json)이 관리합니다. 범위 하한을 현재 설치 버전으로 간주하지 않습니다.

앱은 `<script setup lang="ts">`와 Pinia setup store를 사용합니다. [plugins/element-plus.ts](../omni-frontend/src/plugins/element-plus.ts)가 공유 컴포넌트를 명시적으로 등록하고 Element Plus locale은 앱 언어와 연동됩니다. 시작·HMR·번들·운영 성능은 측정 환경을 명시해야 하며 의존성 버전만으로 고정 성능이나 전체 UI 검증을 주장하지 않습니다.

---

## 2. 디렉터리 구조

다음은 책임과 대표 진입점이며 완전한 파일 트리가 아닙니다. 모든 업무 모듈은 현재 소스와 모듈 정의에 따라 구성합니다.

| 디렉터리/진입점 | 현재 책임 |
|---|---|
| [main.ts](../omni-frontend/src/main.ts), [App.vue](../omni-frontend/src/App.vue) | Pinia/Router/UI/i18n/지시자/테마 시작, 루트 출력과 컴포넌트 언어 |
| `src/api/` | 공유 request.ts와 user.ts, dict.ts, `crm-*`, `srm-*`, `procurement-*`, `asset-*` 하위 도메인 |
| `src/stores/` | user.ts, app.ts, permission.ts |
| [router/index.ts](../omni-frontend/src/router/index.ts) | 정적·동적 경로, 뷰·아이콘 매핑, 세션 정리와 가드 |
| `src/views/`, `src/components/`, `src/layout/` | 페이지, 공유 컴포넌트, 관리 레이아웃. OAuth 클라이언트는 system/oauth2가 아닌 views/oauth2-client |
| `src/composables/` | 사전 상태와 BPMN 생명 주기. 확장 모듈은 일반 함수도 내보낼 수 있음 |
| `src/utils/`, `src/constants/` | JWT 디코딩, 오류·복귀·접근 정책, PKCE와 메뉴 문구. PKCE는 인가 코드 흐름용 |
| `src/i18n/`, `src/locales/` | 언어 인스턴스와 zh-CN.ts, en-US.ts, ja-JP.ts, ko-KR.ts |
| [types/api.ts](../omni-frontend/src/types/api.ts), `src/styles/` | 유일한 공유 응답 타입과 전역 스타일 |

---

## 3. API 계층 (`src/api/`)

[request.ts](../omni-frontend/src/api/request.ts)의 공유 Axios는 VITE_API_BASE_URL(기본 /api), timeout 15000 ms, withCredentials=true입니다. userStore.token에서 Bearer와 X-Tenant-Id를 얻습니다. 클라이언트 디코딩은 JWT 서명을 검증하지 않으며 신뢰된 신원은 Gateway가 결정합니다.

성공 시 AxiosResponse 전체를 반환하며 업무 data를 자동으로 풀지 않습니다.

| 계층 | 현재 예 |
|---|---|
| HTTP 응답 | `AxiosResponse<ApiResponse<PageResult<SysUser>>>` |
| `response.data` | `R<T>`에 대응하는 ApiResponse |
| `response.data.data` | 업무 PageResult |

현재 [user.ts](../omni-frontend/src/api/user.ts)는 SysUser/listUsers/getUser를 사용하며 Axios 제네릭으로 반환 타입을 추론합니다. 공유 ApiResponse/PageResult는 `@/types/api`에서만 가져오며 DTO·null 의미는 API 계약을 따릅니다.

[error-display.ts](../omni-frontend/src/utils/error-display.ts)와 [errors.ts](../omni-frontend/src/utils/errors.ts)가 오류를 분류·번역합니다. 401이 항상 만료는 아닙니다. 자격 증명 엔드포인트는 세션·입력을 보존하고 해당 미로그인·만료 분기만 재인증하며 403은 세션을 지우지 않습니다. 재인증은 공유 상태를 먼저 정리한 뒤 대화상자 종료 시 이동합니다. 동시 대화상자와 명시적 로그아웃 응답은 플래그로 억제합니다.

원격·Axios 기술 message는 진단용이며 표시에는 displayMessage/getErrorMessage를 사용합니다. 다만 로컬 non-Axios Error의 message는 getErrorMessage가 유지하므로 모든 로컬 예외가 번역됐다는 보장은 없습니다. OAuth2 표준 token 요청 등은 별도 fetch·프로토콜 응답을 사용하며 이 R 클라이언트 규칙을 적용하지 않습니다.

---

## 4. 상태 관리 (`src/stores/`)

현재 세 setup store의 책임입니다.

| Store | 상태/영속화 |
|---|---|
| [user](../omni-frontend/src/stores/user.ts) | token·username 영속화, isLoggedIn은 token 존재만 검사, isLoggingOut은 명시적 종료 |
| [app](../omni-frontend/src/stores/app.ts) | sidebarCollapsed/theme, omni-theme 기본 dark, 초기 사이드바는768px 조건으로 접고 영속화하지 않음 |
| [permission](../omni-frontend/src/stores/permission.ts) | permissions/menuTree/menusLoaded/menuLoadState/menuLoadError, 메모리 상태와 공유 메뉴 Promise |

nickname/avatar는 현재 userStore 필드가 아닙니다. omni-lang은 i18n이 관리하며 appStore 소속이 아닙니다. token만 지우는 예제는 전체 세션 정리가 아닙니다.

Router의 `clearAuthenticatedSession(explicit)`는 user.logout, permission.reset, clearDynamicRoutes를 순서대로 호출합니다. user.logout 자체는 token/username만 지우며 사전 캐시는 별도로 신원 변경을 관찰합니다. appStore의 테마 helper가 HTML dark 클래스를 바꾸는 것은 명확하게 소유한 DOM 부수 효과입니다. 일반 업무 store가 페이지 DOM을 임의로 조작해서는 안 됩니다.

현재 메뉴 요청에는 사전 캐시의 Token·세대 응답 가드가 없습니다. reset은 상태·Promise 참조를 지울 뿐 이미 보낸 요청을 취소하지 않습니다. 사전 회귀 테스트로 모든 공유 상태가 같은 격리 능력을 가진다고 증명할 수 없습니다.

---

## 5. 라우팅 (`src/router/`)

Router는 `createWebHistory(import.meta.env.BASE_URL)`를 사용합니다. 정적은 미리 선언됐다는 뜻이며 공개라는 뜻은 아닙니다. `requiresAuth === false`만 공개 분기로 가며 미설정도 보호 분기로 처리합니다.

| 정적 경로 | 현재 접근 처리 |
|---|---|
| `/`, `/login`, `/register` | 공개, token이 있는 Login은 Home으로 |
| `/portal-login`, `/portal-register` | 공개, token이 있는 PortalLogin은 supplier-portal로 |
| `/callback`, `/device`, `/device/verify`, `/consent` | 공개 경로, 페이지·프로토콜 자체 검증 유지 |
| `/supplier-portal` | token과 portalAccess 정책 필요 |
| `/menu-load-error` | token 필요, 메뉴 재시도 |
| `/admin`, `/admin/dashboard` | 관리 레이아웃·기본 자식 페이지, 관리 신원 정책 적용 |
| `/workspace`, `/workspace/overview` | 보호 경로. 루트는 통합 조종석으로 이동하며 이전 승인·작업 경로는 5.1의 같은 화면 내 패널로 이동 |
| `/admin/:pathMatch(.*)*`, `/:pathMatch(.*)*` | 각각403/404페이지, 전자는 보호·후자는 공개 |

보호 경로에 token이 없으면 safeAppRedirect로 앱 내 복귀를 유지하고 Login/PortalLogin을 선택합니다. token이 있으면 매번 initFromToken을 실행합니다. 메뉴 미완료 시 loadMenus를 기다려 등록·replace 재이동하고 실패하면 MenuLoadError로 갑니다. 나머지 분기는 등록을 보완하고 관리·공급업체 포털·자산 본인 정책을 적용합니다. token 존재·디코딩으로 서명 유효성이나 만료를 증명할 수 없으며 서버 요청은 계속 인증해야 합니다.

동적 등록은 뷰가 있는 MENU만 처리하고 srm:portal: 하위 항목을 제외하며 children을 재귀 처리합니다. 권한 코드 콜론은 admin 자식 경로에서 슬래시, 경로명에서 하이픈이 됩니다. `system:user → /admin/system/user`, `base:dict → /admin/base/dict`이며 /admin/user·/admin/dict가 아닙니다. MenuNode.path는 권한 트리 계층을 저장한 경로이며 프런트엔드 URL이 아닙니다.

`import.meta.glob('../views/**/index.vue')`가 지연 뷰를 제공합니다. viewOverrides는 system:oauth2→views/oauth2-client/index.vue, base:operlog→views/monitor/oper-log/index.vue 두 개입니다. 동적 meta에는 title/icon/requiresAuth/permissionCode가 있지만 타입은 선택 필드이며 모든 정적 레코드가 전부 명시하지는 않습니다. 등록명으로 중복을 막고 종료 시 제거합니다.

메뉴 실패에도 menusLoaded=true이므로 idle/loading/loaded/failed를 함께 봐야 합니다. retryLoadMenus는 초기화 후 다시 읽습니다. 복구 작업은 Wiki에서 관리합니다.

---

### 5.1 홈·워크스페이스·앱 탐색 경계

공식 CLI는 catalog에서 해석한 모듈 의존성 집합을 `OMNI_FRONTEND_MODULES`로 프런트엔드 빌드 인수 `VITE_OMNI_MODULES`에 전달합니다. 소스를 작은 프리셋으로 빌드하면 뷰 해석과 클라이언트 표시 권한을 이 선택 범위와 교집합으로 제한합니다. workflow가 없으면 프로세스 통계를 요청하거나 승인 패널을 표시하지 않으며, SRM이 없으면 공급업체 진입점도 제공하지 않습니다. 이 인수가 없는 직접 빌드는 기존 전체 동작을 유지하고, 생성 프로젝트는 실제로 남은 파일의 제약도 받습니다. 이 필터는 JWT, DB 역할 또는 서버 인가를 바꾸지 않습니다. 구현은 [deployment-modules.ts](../omni-frontend/src/utils/deployment-modules.ts)에 있습니다.

홈의 워크스페이스는 동일 출처의 기본 링크로 `noopener noreferrer`를 사용해 새 탭을 엽니다. 기존 세션을 공유하고 URL에 token을 넣지 않습니다. 워크스페이스에는 홈/Wiki 복귀 진입점이 없습니다.

개요, 승인, 내 작업은 `/workspace/overview`에서 기존 목록·양식·API를 재사용합니다. 이전 `/workspace/approvals`, `/workspace/jobs`는 query를 보존해 `#approvals`, `#my-tasks`로 이동하며 세 화면을 따로 유지하지 않습니다.

워크스페이스 앱과 검색은 권한이 있고 실제 뷰가 있는 전체 메뉴가 대상이며 업무 진입점은 새 탭을 엽니다. 업무 레이아웃에는 전체 앱 버튼이나 패널이 없고 사이드바는 현재 모듈에 집중합니다. 검색은 안정적인 루트 ID `route.meta.appId`로 진입 가능한 페이지를 필터링하고 선택 시 대상 appId를 다시 확인해 현재 탭에서 이동합니다. 잘못된 ID는 빈 후보이며 URL 접두사로 추측하지 않습니다.

비공개 오류 화면도 홈·뒤로 복구 버튼이 없습니다. 알 수 없는 모듈이나 권한 거부 화면에서 대시보드를 대체 메뉴로 쓰지 않습니다. 브라우저 뒤로, 탭 전환, 주소 입력은 막지 않으며 UI 경계가 서버 인가를 대신하지 않습니다. 최초 메뉴 로딩 후 `resolveMenuReloadTarget()`에 path, query, hash를 따로 전달하고 객체 path에 fullPath를 넣지 않아 복귀 인수와 앵커를 보존합니다.

구현은 [ConsoleHeader](../omni-frontend/src/components/ConsoleHeader.vue), [워크스페이스](../omni-frontend/src/views/workspace/index.vue), [탐색 도구](../omni-frontend/src/utils/navigation.ts)에 있습니다.

---

## 6. 컴포넌트 설계 패턴

페이지는 반응형 데이터, 로딩·폼 상태, 작업, 템플릿을 분리합니다. API·타입은 도메인, 재사용 로직은 Composables, 업무 컴포넌트는 페이지와 함께 구성할 수 있습니다. 실제 [사용자 페이지](../omni-frontend/src/views/system/user/index.vue)를 참조하며 누락된 import·생략 부호가 있는 두 번째 “실행 가능” 페이지를 유지하지 않습니다.

loading은 finally에서 회수하고 제출 전에 폼 검증을 기다리며 버튼 권한과 서버 검증을 적용합니다. 신규 작업은 사용자 취소와 실제 실패를 구분하고 넓은 catch로 모든 실패를 취소 취급하지 않습니다. 기존 처리 범위는 소스에 따릅니다.

부모·자식은 defineProps/defineEmits, 공유 클라이언트 상태는 Pinia, UI 확장은 slots를 사용합니다. 타입 추론이 런타임 본문 검증을 대체하지는 않습니다.

---

## 7. 컴포넌트 규칙

신규 SFC는 `<script setup lang="ts">` → `<template>` → `<style scoped>` 순서입니다. 페이지는 보통 kebab-case 디렉터리의 index.vue, 공유 컴포넌트는 LoginForm.vue/LanguageSelector.vue 같은 UpperCamelCase 파일도 사용합니다. 페이지 규칙을 모든 컴포넌트명에 강제하지 않습니다.

Props·이벤트는 타입을 지정한 defineProps/defineEmits를 사용하고 스타일 범위를 명확히 하며 전역 스타일은 src/styles에 둡니다. 외부 라이브러리 스타일 재정의도 범위를 제한합니다. 날짜·금액·공유 응답 타입은 [API 계약](api-contract.kr.md)과 [AGENTS](../AGENTS.md)를 따르며 상충하는 형식을 만들지 않습니다.

---

## 8. 명명 규칙 (TypeScript)

| 대상 | 신규 코드 규칙 |
|---|---|
| 변수·함수 | lowerCamelCase |
| 인터페이스·타입·컴포넌트 | UpperCamelCase |
| 페이지 디렉터리 | kebab-case + index.vue |
| Store·도메인 파일 | 현재 도메인 진입점을 따르며 신규는 kebab-case 우선 |
| Store·상태 Composable | use 접두사 |
| 상수·CSS 클래스 | UPPER_SNAKE_CASE / kebab-case |

myJob.ts/systemJob.ts 같은 기존 진입점은 그대로 있으며 일괄 이름 변경 완료를 뜻하지 않습니다. composables의 일반 helper에 Ref 반환이나 Hook 이름을 강제하지 않습니다. 신규 코드 주석은 AGENTS에 따라 중국어로 작성합니다.

---

## 9. 코드 포맷 (TypeScript / Vue)

[eslint.config.mjs](../omni-frontend/eslint.config.mjs)는 공백2칸, 세미콜론 없음, 작은따옴표, 여러 줄 후행 쉼표, SFC block-order를 검사합니다. lint는 `--max-warnings 0`이므로 warn도 명령 실패가 됩니다. Node 스크립트의 globals/no-console 예외는 src 규칙을 바꾸지 않습니다.

[tsconfig.json](../omni-frontend/tsconfig.json)은 strict, noUnusedLocals/noUnusedParameters, noFallthroughCasesInSwitch, bundler resolution, noEmit, @/*→src/*를 설정합니다. 이것으로 백엔드 권한이나 모든 기존 이름·서식을 검증했다고 할 수는 없습니다.

---

## 10. UI 프레임워크

main이 호출하는 [installElementPlus](../omni-frontend/src/plugins/element-plus.ts)는 나열된 컴포넌트·아이콘·loading 지시자만 등록하며 전체 아이콘 라이브러리를 등록하지 않습니다. Globe는 Connection 별칭입니다. iconMap과 실제 등록 집합을 함께 관리합니다.

main은 기본·다크 변수 CSS를 읽고 initTheme/setTheme가 document.documentElement의 dark 클래스를 적용합니다. 기본값·영속화는 appStore 책임이며 CSS import만으로 테마가 바뀌지는 않습니다. 검증 문구가 언어 변경을 따라야 하면 반응형 규칙과 t()를 사용하며 각 페이지 구현을 확인합니다.

---

## 11. 국제화(i18n)

[i18n/index.ts](../omni-frontend/src/i18n/index.ts)는 legacy=false와 `zh-CN/en-US/ja-JP/ko-KR`, 저장 키 omni-lang을 사용합니다. 잘못되거나 없는 설정은 zh-CN, 누락된 키는 en-US로 폴백합니다. 컴포넌트 외부는 같은 인스턴스의 translate()를 사용합니다.

[LanguageSelector](../omni-frontend/src/components/LanguageSelector.vue)는 locale과 storeLang을 함께 갱신하며 후자는 저장하고 HTML lang을 설정합니다. [App.vue](../omni-frontend/src/App.vue)는 ElConfigProvider 언어를 매핑하고 알 수 없는 매핑은 enUS로 되돌립니다. localStorage만 쓰면 반응형 언어는 바뀌지 않습니다.

신규 문구는 네 locale 파일의 키 구조를 맞추고 메뉴는 menuI18nMap도 갱신합니다. API·프로토콜 식별자를 번역하거나 특정 언어 소스 예제에 다른 언어 내용을 넣으면 안 됩니다. 파일 존재·폴백은 의미나 UI 검증을 대체하지 않습니다.

Router는 이동 시 메뉴 매핑 또는 docTitle로 document.title을 갱신합니다. 같은 페이지의 언어 변경 직후 재계산은 보장하지 않습니다. 키 일치, 하드코딩 감사, 문서 AI_REVIEW, 실제 브라우저 확인은 별개 검사입니다.

---

## 12. 권한 및 접근 제어

permissions는 JWT scope 배열을 전제로 하며 roles는 roles에서 따로 얻습니다. 로컬 디코딩은 서명이나 claims 구조를 검증하지 않습니다. 해당 배열 전제에서 hasPermission은 정확한 문자열 원소 포함 여부를 검사하며 SUPER_ADMIN이나 역할명에 자동으로 와일드카드 권한을 주지 않습니다.

메뉴 트리는 `GET /api/auth/menus`에서 얻으며 DIRECTORY/MENU는 그룹·경로, path는 권한 트리 계층을 저장한 경로입니다. 클라이언트 표시로 백엔드 인가를 증명할 수 없습니다. v-permission은 mounted/updated에서 display:none 또는 빈 display를 설정하며 DOM을 제거하지 않습니다. 빈 바인딩은 안전한 접근 거부 수단이 아닙니다.

경로는 [access.ts](../omni-frontend/src/utils/access.ts)의 관리·공급업체·자산 본인 정책도 사용합니다. 실제 보안 경계는 백엔드 인증, 기능 권한·행 소유, 테넌트·데이터 범위이며 화면 경로·버튼으로 대신할 수 없습니다. 현재 구현 제한은 도메인 문서에서 확인합니다.

XSS 설정 API·오류·테넌트 규칙은 [API 계약](api-contract.kr.md)이 관리합니다. X-Tenant-Id를 보내도 임의 신원이 신뢰되는 것은 아니며 Gateway가 검증된 신원으로 덮어씁니다. 종료는 메뉴 숨김만이 아니라 전체 세션을 정리해야 합니다.

---

## 13. 비즈니스 모듈 구조 패턴

페이지, API, 선택 Store, 네 언어 locale을 CRM/SRM/Procurement/Asset과 플랫폼 도메인별로 구성합니다. 공유 응답 타입은 src/types/api.ts에서만 정의합니다.

router iconMap, constants/menu.ts menuI18nMap, sys_permission/sys_role_permission, 지시자와 엔드포인트 권한을 일치시키고 시드 변경 시 manifest 해시·검증을 갱신합니다. 통합 절차는 [개발 가이드](guides/scaffold-development.kr.md), 현재 예제는 [사전 페이지](../omni-frontend/src/views/base/dict/index.vue)와 [API](../omni-frontend/src/api/dict.ts)에서 관리합니다.

---

## 14. Composables 패턴

상태를 가진 Composable은 기능별 이름이 있는 Ref·ComputedRef·동작 함수를 반환한다. 일반 보조 함수는 선언된 인터페이스의 값을 반환한다. 부수 효과의 소유자를 명확히 한다. 컴포넌트 자원은 해당 scope와 함께 정리하고 공유 모듈 캐시는 자체 문맥 observer를 소유한다.

### 설계 원칙

| 원칙 | 제약 |
| --- | --- |
| 단일 책임 | 기능별 구성, 공유 ApiResponse/PageResult 중복 정의 금지 |
| 반응형 반환 | 반응형 상태를 노출할 때 refs/computed 반환으로 구조 분해 후 갱신 유지; 템플릿은 Vue 규칙에 따라 해제 |
| 문맥 소유 | 캐시와 진행 요청은 테넌트 및 신원 문맥을 구분 |
| 수명 주기 | 컴포넌트 observer는 scope와 함께 종료; 모듈 observer는 독립 소유하며 문맥 교체·HMR 때 종료 |

### 대표적인 Composable: useDictOptions

```typescript
import { useDictOptions } from '@/composables/useDictOptions'

const { options, loading } = useDictOptions('workflow_category')
```

`useDictOptions(typeCode)`는 `{ options, loading }`을 반환한다. options는 `ComputedRef<DictOption[]>`, loading은 `Ref<boolean>`이다. 인증된 `getDictOptions()`를 호출하며 공개 `reload`나 `listDictDataByType()` 호출은 없다.

캐시 키는 테넌트 ID와 typeCode이다. 같은 테넌트·Token의 진행 요청을 공유한다. 테넌트 변경은 옵션 뷰를 전환하고 Token 변경은 이전 요청을 무효화한다. 같은 테넌트의 완료 캐시는 재사용할 수 있다. 이전 응답은 여전히 유효하고 Token이 같을 때만 반영하며, 종료 처리로 새 진행 요청을 삭제하지 않는다.

모듈이 소비자와 독립적인 신원 observer scope를 소유한다. 마지막 소비자가 파괴되어도 로그아웃 시 캐시를 비우고 이전 응답을 차단한다. 디코딩된 테넌트 값이 falsy이면 옵션 요청을 보내지 않는다. 이 클라이언트 판단은 JWT 서명이나 claims 구조를 검증하지 않는다. 컴포넌트 unmount만으로 공유 캐시가 만료되지는 않으며 백엔드 Redis 갱신과 클라이언트 메모리 갱신은 별개다.

템플릿은 loading Ref의 자동 해제 값을 직접 바인딩하고 스크립트는 `.value`를 사용한다. 소스 링크는 계약과 실제 Composable/Vue 회귀 8개를 포함한다. 단계별 사용 절차는 Wiki에 둔다.

[dict.ts](../omni-frontend/src/api/dict.ts) / [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) / [dict-options.test.ts](../omni-frontend/tests/base/dict-options.test.ts)

### 기존 Composables 목록

| 소스 | 책임 |
| --- | --- |
| [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) | 테넌트 옵션 캐시·문맥 observer·로딩 상태 |
| [useBpmnModeler.ts](../omni-frontend/src/composables/useBpmnModeler.ts) | bpmn-js 초기화·가져오기/내보내기·onBeforeUnmount 파괴 |
| [useBpmnExtension.ts](../omni-frontend/src/composables/useBpmnExtension.ts) | readAssignment/writeAssignment 등의 BPMN 확장 읽기·쓰기 함수 |

[Flow 9](core-flows.kr.md)

이 격리는 useDictOptions의 구현이며 permissionStore 메뉴 요청에 일반화하지 않습니다. useBpmnModeler는 컨테이너 준비 후 초기화하고 import/export, unmount 시 keydown 제거·destroy를 처리합니다. useBpmnExtension은 일반 확장 함수와 moddle 정의를 내보냅니다. 사전 요청 상태를 공유하지 않으며 8개 회귀 테스트로 전체 비동기 취소·readonly 동작·브라우저 생명 주기를 검증했다고 할 수 없습니다.

---

## 15. 빌드와 정적 서비스 경계

프런트엔드 이미지는 앱 빌드 후 같은 저장소의 Wiki를 순서대로 빌드하고, 동일한 3000 포트의 `/wiki/`에서 공개 정적 문서를 제공합니다. 공개 `/` 페이지는 로그인 없이 문서를 표시하며, 인증 상태는 계정 영역만 바꿉니다. 작업 공간과 업무 경로의 인증 가드는 유지합니다. Wiki 파일이 없으면 앱 홈으로 대체하지 않고 404를 반환합니다. 같은 출처의 삽입은 Wiki 문서에만 허용하고 시작 스크립트에는 응답 nonce를 사용합니다. 로그인 및 업무 경로의 기존 보안 정책은 유지합니다.

[프런트엔드 Dockerfile](../docker/frontend/Dockerfile)은 Node 22로 빌드하고 Nginx 1.28로 dist를 3000에서 제공합니다. 현재 [Nginx](../docker/frontend/nginx.conf)는 /api/, /oauth2/, /.well-known/을 Docker DNS로 omni-gateway:8080에 전달하고 index.html history 폴백을 제공합니다.

HTML은 no-cache/no-store, assets는 1년 immutable 캐시입니다. add_header가 있는 location은 보안 헤더를 명시적으로 다시 정의합니다. 이는 설정 사실이며 이번 단계에서 서비스를 시작하거나 프록시·캐시 동작을 검증했다는 주장이 아닙니다. 운영·영속화는 [Docker 배포](docker-deployment.kr.md)와 [빠른 시작](guides/quick-start.kr.md)에 둡니다.

---

## 16. 빌드 및 도구

package.json은 build=`vue-tsc -b && vite build`, lint=`eslint . --max-warnings 0`, 단위/E2E/스크린샷/언어 감사 명령을 정의합니다. Vite는 es2020, dist, 750 KB 경고, bpmn-vendor/element-plus/vue-vendor 분할을 사용하며 개발 대상 `http://localhost:8102`는 Vite 프로세스 네트워크입니다.

앱, 빌드, 테스트, 브라우저 검증은 로컬 Docker에서 실행하고 정확한 명령은 [AGENTS](../AGENTS.md)를 따릅니다. 잠금 파일과 npm ci를 사용하며 소스 변경은 영향받는 build/lint/tests, E2E는 TypeScript도 검사합니다. 문서만 수정한 경우 불변의 비용 큰 검증을 반복하지 않으며 재사용에는 해시·커밋 동등성 증거가 필요합니다. 생성 프리셋은 자체 검사를 실행합니다.

---

## 17. 문제 해결 가이드

[문제 해결](guides/troubleshooting.kr.md)은 시작·네트워크·빌드·로그인·메뉴·언어 운영과 복구를, [소스 디버깅](wiki/source-debugging.kr.md)은 호출 관계와 중단점을 관리합니다.

실제 HTTP/R.code, trace, 오류 출처를 유지하고 신원, 메뉴 상태·뷰 매핑, 권한, 번역을 구분합니다. 기본 포트 변경, 공유 캐시 삭제, 품질 검사 비활성화로 실패를 숨기지 않습니다. 소스 읽기, 테스트, 정적 설정, 브라우저, 실제 업무 증거는 구별해 기록합니다.
