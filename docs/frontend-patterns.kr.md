# 프론트엔드 패턴 및 규칙

> 이 문서는 Omni-Stack 프론트엔드의 내부 조직 방식을 정의합니다. 모든 프론트엔드 코드는 반드시 이러한 패턴을 따라야 합니다.  
> 아키텍처 개요는 [architecture.kr.md](architecture.kr.md)를 참조하십시오. Docker 배포 구성은 [docker-deployment.kr.md](docker-deployment.kr.md)를 참조하십시오.

---

## 목차

- [1. 기술 선정 배경](#1-기술-선정-배경)
- [2. 디렉터리 구조](#2-디렉터리-구조)
- [3. API 계층](#3-api-계층-srcapi)
- [4. 상태 관리](#4-상태-관리-srcstores)
- [5. 라우팅](#5-라우팅-srcrouter)
- [6. 컴포넌트 설계 패턴](#6-컴포넌트-설계-패턴)
- [7. 컴포넌트 규칙](#7-컴포넌트-규칙)
- [8. 명명 규칙](#8-명명-규칙-typescript)
- [9. 코드 포맷](#9-코드-포맷-typescript--vue)
- [10. UI 프레임워크](#10-ui-프레임워크)
- [11. 국제화(i18n)](#11-국제화i18n)
- [12. 권한 및 접근 제어](#12-권한-및-접근-제어)
- [13. 비즈니스 모듈 구조 패턴](#13-비즈니스-모듈-구조-패턴-business-module-structure-pattern)
- [14. Composables 패턴](#14-composables-패턴)
- [15. Docker 배포 환경에서의 Vite 빌드 최적화](#15-docker-배포-환경에서의-vite-빌드-최적화)
- [16. 빌드 및 도구](#16-빌드-및-도구)
- [17. 문제 해결 가이드](#17-문제-해결-가이드)

---

## 1. 기술 선정 배경

### Vue 3.5 + Composition API를 선택한 이유

| 고려 사항 | 이유 |
|-----------|------|
| **TypeScript 네이티브 지원** | Vue 3.5는 `<script setup lang="ts">`에 대한 완전한 타입 추론을 제공하며, `defineProps<T>()`에 런타임 선언이 불필요합니다 |
| **Composition API** | 로직 재사용이 Composables를 통해 구현되어, Mixin의 이름 충돌 및 출처 불투명 문제를 해결합니다 |
| **Tree-shaking 친화적** | API를 필요 시 임포트하며(`ref`, `computed`, `watch`), 사용되지 않는 기능은 번들에 포함되지 않습니다 |
| **성능** | Proxy 기반 반응형 시스템이 Vue 2의 `Object.defineProperty`보다 빠르며, `Map`/`Set` 등 컬렉션 타입을 지원합니다 |
| **Teleport / Suspense** | 내장 `<Teleport>` 및 `<Suspense>` 컴포넌트를 통해 모달 및 비동기 컴포넌트 처리를 간소화합니다 |

### Vite 8을 선택한 이유

| 고려 사항 | 이유 |
|-----------|------|
| **네이티브 ESM** | 개발 서버가 브라우저 네이티브 ES Module 기반이며, 번들링 없이 기동되어 콜드 스타트 속도가 Webpack의 30초 이상에서 2초 미만으로 단축됩니다 |
| **HMR 속도** | 모듈 단위 HMR로, 단일 파일 수정 시 해당 모듈만 업데이트되며 전체 chunk가 아닌, 핫 업데이트 지연 시간이 50ms 미만입니다 |
| **Rollup 호환** | 프로덕션 빌드가 Rollup 기반으로, 성숙한 tree-shaking 및 chunk splitting을 지원합니다 |
| **플러그인 생태계** | `@vitejs/plugin-vue` 6.x가 SFC 컴파일 및 HMR 통합을 제공합니다 |

### Vuex 대신 Pinia 3를 선택한 이유

| 고려 사항 | 이유 |
|-----------|------|
| **공식 권장** | Vue 3부터 Vue 공식에서는 Pinia를 Vuex 대신 권장하며, Vuex는 유지보수 모드로 전환되었습니다 |
| **Composition API 스타일** | `defineStore('id', () => { ... })`가 Vue 3의 `<script setup>` 스타일과 일관됩니다 |
| **TypeScript 지원** | 완전한 타입 추론을 제공하며, `@Mutation` / `@Action` 데코레이터가 불필요합니다 |
| **Mutation 없음** | State + Getters + Actions만으로 데이터 흐름이 간소화됩니다(state 직접 수정 또는 action을 통한 수정) |
| **경량** | 핵심 패키지가 gzip 기준 1KB 미만이며, 보일러플레이트 코드가 없습니다 |
| **DevTools** | Vue DevTools와 통합되어 시간 여행 디버깅 및 상태 스냅샷을 지원합니다 |

### Element Plus 2.x를 선택한 이유

| 고려 사항 | 이유 |
|-----------|------|
| **Vue 3 네이티브** | Element UI의 Vue 3 버전으로, 완전한 Composition API를 지원합니다 |
| **엔터프라이즈급 컴포넌트** | 테이블(`el-table`), 폼(`el-form`), 대화상자(`el-dialog`) 등 엔터프라이즈 시나리오 컴포넌트가 성숙합니다 |
| **내장 국제화** | `vue-i18n` 통합을 지원하며, 컴포넌트에 중국어/영문 텍스트가 내장되어 있습니다 |
| **다크 모드** | `theme-chalk/dark/css-vars.css`를 통해 한 번에 다크 테마로 전환할 수 있습니다 |
| **아이콘 라이브러리** | `@element-plus/icons-vue`가 280개 이상의 벡터 아이콘을 제공합니다 |

---

## 2. 디렉터리 구조

```
omni-frontend/
├── index.html              # Vite 진입 HTML
├── package.json
├── vite.config.ts          # Vite 설정(프록시, 별칭, 빌드)
├── tsconfig.json
├── eslint.config.mjs       # ESLint flat config
└── src/
    ├── main.ts             # 애플리케이션 부트스트랩(Pinia, Router, Element Plus, I18n)
    ├── App.vue             # 루트 컴포넌트(<router-view />)
    ├── api/                # API 계층(비즈니스 도메인당 하나의 파일)
    │   ├── request.ts      # 공유 Axios 인스턴스 + 인터셉터
    │   ├── user.ts         # 사용자 API 함수
    │   ├── dict.ts         # 사전 API(유형 + 데이터 CRUD)
    │   ├── auth.ts         # 인증 API(로그인, 회원가입, 인증코드, 소셜 로그인, OAuth2)
    │   ├── menu.ts         # 메뉴 API(동적 메뉴 트리)
    │   ├── workflow.ts     # 워크플로 API(모델, 정의, 인스턴스, 승인)
    │   └── myJob.ts        # 사용자 예약 작업 API
    ├── stores/             # Pinia 상태 관리(비즈니스 도메인당 하나의 파일)
    │   ├── user.ts         # 사용자 인증 Store(Token, 사용자 정보)
    │   ├── app.ts          # 애플리케이션 설정 Store(테마, 사이드바)
    │   └── permission.ts   # 권한 Store(권한 코드 목록, 동적 메뉴 트리)
    ├── router/
    │   └── index.ts        # 라우트 정의 + 내비게이션 가드 + 동적 라우트 등록
    ├── views/              # 페이지 컴포넌트(kebab-case 디렉터리)
    │   ├── home/index.vue          # 홈페이지(미로그인 랜딩 페이지 / 로그인 후 워크벤치)
    │   ├── login/index.vue         # 로그인 페이지
    │   ├── register/index.vue      # 회원가입 페이지
    │   ├── callback/index.vue      # OAuth2 콜백 페이지
    │   ├── dashboard/index.vue     # 대시보드
    │   ├── consent/index.vue       # OAuth2 권한 동의 페이지
    │   ├── device/                 # 디바이스 인증(Device Code Flow)
    │   │   ├── index.vue           # 디바이스 시뮬레이터: QR코드 표시 + Token 폴링
    │   │   └── verify.vue          # 사용자 승인 확인 페이지
    │   ├── base/                   # 기본 데이터 모듈
    │   │   └── dict/index.vue      # 사전 관리(master-detail 레이아웃)
    │   ├── system/                 # 시스템 관리 모듈
    │   │   ├── user/index.vue      # 사용자 관리
    │   │   ├── role/index.vue      # 역할 관리
    │   │   ├── permission/index.vue # 권한 관리
    │   │   ├── org/index.vue       # 조직 관리
    │   │   ├── tenant/index.vue    # 테넌트 관리
    │   │   ├── oauth2/index.vue    # OAuth2 클라이언트 관리
    │   │   ├── online/index.vue    # 온라인 사용자 모니터링
    │   │   └── xssconfig/index.vue # XSS 방어 설정
    │   ├── workflow/               # 워크플로 모듈
    │   │   ├── model/index.vue     # 프로세스 모델 관리 + BPMN 디자이너
    │   │   ├── definition/index.vue # 프로세스 정의 관리
    │   │   ├── instance/index.vue  # 프로세스 인스턴스 관리
    │   │   └── stats/index.vue     # 프로세스 통계
    │   └── monitor/                # 모니터링 모듈
    │       └── oper-log/index.vue  # 운영 로그
    ├── layout/
    │   └── index.vue       # 애플리케이션 셸 레이아웃(사이드바, 상단 바, 콘텐츠 영역)
    ├── components/         # 공유/재사용 UI 컴포넌트
    │   ├── LoginForm.vue           # 로그인 폼(비밀번호 + SSO + 서드파티 OAuth2)
    │   ├── CronGenerator.vue       # Cron 표현식 생성기
    │   ├── DynamicFormRenderer.vue # 동적 폼 렌더러
    │   └── workflow/               # 워크플로 전용 컴포넌트
    │       ├── ModelDesigner.vue   # BPMN 모델링 디자이너
    │       └── panels/             # BPMN 속성 패널
    ├── composables/        # Vue Composables(로직 재사용)
    │   ├── useDictOptions.ts       # 사전 옵션 로드
    │   ├── useBpmnModeler.ts       # BPMN Modeler 수명 주기 관리
    │   └── useBpmnExtension.ts     # BPMN 확장 요소 읽기/쓰기
    ├── constants/          # 상수 정의
    │   └── menu.ts                 # 메뉴 i18n 매핑 테이블
    ├── directives/         # Vue 커스텀 디렉티브
    │   └── permission.ts           # v-permission 권한 디렉티브
    ├── utils/              # 유틸리티 함수
    │   ├── jwt.ts                  # JWT 파싱(권한, 테넌트 ID 추출)
    │   └── pkce.ts                 # PKCE 유틸리티(OAuth2 Device Code Flow)
    ├── i18n/
    │   └── index.ts        # vue-i18n 인스턴스 설정
    ├── locales/            # 번역 파일
    │   ├── zh-CN.ts        # 중국어 번역
    │   └── en-US.ts        # 영어 번역
    ├── types/              # 공유 TypeScript 타입
    │   └── api.ts          # ApiResponse<T>, PageResult<T>
    └── styles/
        └── index.scss      # 전역 스타일(reset, 레이아웃, 테마)
```

---

## 3. API 계층 (`src/api/`)

### 공유 Axios 인스턴스

`request.ts`는 단일 설정된 Axios 인스턴스를 내보냅니다:

- **baseURL**: `VITE_API_BASE_URL` 환경 변수에서 읽으며, 기본값은 `/api`입니다
- **timeout**: 15000ms
- **withCredentials**: `true`(쿠키를 포함하여 OAuth2 Session 관리를 지원합니다)
- **요청 인터셉터**: `useUserStore()`에서 Token을 읽어 `Authorization: Bearer <token>`을 주입합니다
- **응답 인터셉터**: `code === 200`을 검사하고, 비즈니스 오류는 `ElMessage.error`로 표시하며, 401 오류 시 만료 대화상자를 표시하고 로그인 페이지로 리다이렉트합니다(redirect 파라미터 포함)

### API 함수 패턴

비즈니스 도메인당 하나의 파일. 함수 명명 규칙:

| 조작 | 접두사 | 예시 |
|------|--------|------|
| ID로 조회 | `getXxx` | `getUserById(id)` |
| 페이지 목록 | `listXxx` | `listUsers(page, size)` |
| 생성 | `createXxx` | `createUser(data)` |
| 수정 | `updateXxx` | `updateUser(id, data)` |
| 삭제 | `deleteXxx` | `deleteUser(id)` |

모든 함수는 타입화된 파라미터와 반환 타입을 가져야 하며, `ApiResponse<T>`를 사용합니다:

```typescript
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### 타입 시스템

- **공유 타입**(`ApiResponse<T>`, `PageResult<T>`): `src/types/api.ts`에 정의되어 유일한 출처(single source of truth)로 사용됩니다
- **도메인 타입**(`UserInfo`, `SysUser` 등): 이를 사용하는 API 파일에 함께 배치됩니다(`src/api/user.ts`)
- 공유 타입의 **중복 정의 금지**; 항상 `@/types/api`에서 임포트합니다
- 타입 전용 임포트에는 `import type { ... }`를 사용합니다

---

## 4. 상태 관리 (`src/stores/`)

### Pinia Composition API 스타일

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const username = ref<string>('')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
  }

  return { token, username, setToken, logout }
})
```

### 규칙

- 비즈니스 도메인당 하나의 Store: `user.ts`, `app.ts`, `permission.ts`
- Store 파일 명명: kebab-case
- Store Hook 명명: `use` 접두사(`useUserStore`, `useAppStore`, `usePermissionStore`)
- Composition API 스타일 사용(`defineStore('id', () => { ... })`)
- 영속화는 `localStorage`를 사용; 비영속 상태는 `ref()`를 사용
- Store 내에서 DOM 직접 조작 금지

### 세 가지 핵심 Store 역할

| Store | 역할 | 영속화 |
|-------|------|--------|
| `useUserStore` | Token 관리, 사용자 기본 정보(username, nickname, avatar) | `localStorage`(token) |
| `useAppStore` | 테마 전환(light/dark), 사이드바 접기 상태, 언어 기본 설정 | `localStorage`(theme, lang) |
| `usePermissionStore` | 권한 코드 목록(JWT에서 파싱), 동적 메뉴 트리(백엔드에서 로드), `menusLoaded` 플래그 | 없음(매 로그인 시 재획득) |

---

## 5. 라우팅 (`src/router/`)

### 라우팅 아키텍처

Omni-Stack은 **정적 라우트 + 동적 라우트** 혼합 아키텍처를 사용합니다:

```
정적 라우트(공개 페이지)
├── /            → Home(홈페이지 랜딩 / 워크벤치)
├── /login       → Login
├── /register    → Register
├── /callback    → OAuth2Callback
├── /device      → DeviceAuth
├── /device/verify → DeviceVerify
├── /consent     → OAuth2 권한 동의
└── /admin       → Layout(관리 백엔드 셸)
    └── /admin/dashboard → Dashboard(정적 자식 라우트)

동적 라우트(권한 기반, 런타임 등록)
└── /admin/<feature> → 해당 views/<module>/<feature>/index.vue
    예시: /admin/user → views/system/user/index.vue
         /admin/dict → views/base/dict/index.vue
```

### 내비게이션 가드 흐름

```
router.beforeEach()
    ↓
to.meta.requiresAuth 판단
    ├── false → 로그인 사용자가 /login 접근 → /home으로 리다이렉트
    │           기타 공개 페이지 → 허용
    └── true  → userStore.token 확인
                ├── Token 없음 → /login?redirect=<path>로 리다이렉트
                └── Token 있음 → permissionStore.menusLoaded 확인
                    ├── false → initFromToken() → loadMenus() → registerDynamicRoutes() → 재내비게이션
                    └── true  → registerDynamicRoutes()(멱등) → 허용
```

### 관례적 뷰 컴포넌트 매핑

동적 라우트는 `import.meta.glob`을 통해 `views/` 디렉터리 아래 모든 `index.vue`를 스캔하여 permissionCode → 뷰 컴포넌트의 자동 매핑을 구현합니다:

```typescript
// router/index.ts
const viewModules = import.meta.glob('../views/**/index.vue')

// 매핑 규칙: permissionCode "system:user" → "views/system/user/index.vue"
function resolveViewComponent(permissionCode: string) {
  if (viewOverrides[permissionCode]) return viewModules[viewOverrides[permissionCode]]
  const modulePath = permissionCode.replace(/:/g, '/')
  return viewModules[`../views/${modulePath}/index.vue`]
}
```

**특수 매핑 재정의**(`viewOverrides`):

| permissionCode | 특수 매핑 경로 | 이유 |
|----------------|----------------|------|
| `system:oauth2` | `views/oauth2-client/index.vue` | OAuth2 콜백 페이지와의 충돌 방지 |
| `base:operlog` | `views/monitor/oper-log/index.vue` | monitor 디렉터리로 분류 |

### 규칙

- 모든 라우트 컴포넌트는 지연 로딩을 사용합니다: `() => import('@/views/xxx/index.vue')`
- `meta` 필드: `title`(필수), `icon`(선택), `requiresAuth`(필수), `permissionCode`(동적 라우트 자동 설정)
- 인증 필요 라우트: 명시적으로 `requiresAuth: true` 설정
- 공개 라우트: 명시적으로 `requiresAuth: false` 설정
- 로그아웃 시 `clearDynamicRoutes()`를 호출하여 모든 동적 라우트를 제거합니다

---

## 6. 컴포넌트 설계 패턴

### 페이지 컴포넌트(Views)

페이지 컴포넌트는 표준 **데이터 + 조작 + UI** 3계층 구조를 따릅니다:

```vue
<script setup lang="ts">
// 1. 임포트(API, 타입, 컴포넌트, i18n)
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, type SysUser } from '@/api/user'

const { t } = useI18n()

// 2. 반응형 상태
const tableData = ref<SysUser[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 3. 폼 상태
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = { username: [{ required: true, ... }] }

// 4. 비즈니스 함수
async function loadData() { loading.value = true; try { ... } finally { loading.value = false } }
function handleCreate() { dialogVisible.value = true }
async function handleSubmit() { await formRef.value?.validate(); ... }
async function handleDelete(id: number) { await ElMessageBox.confirm(...); ... }
</script>
```

### 공유 컴포넌트(Components)

- 범용 UI 컴포넌트는 `src/components/`에 배치합니다
- 비즈니스 특정 컴포넌트는 해당 views와 함께 배치합니다
- 컴포넌트 사용 시 UpperCamelCase(`<UserCard />`), 파일은 kebab-case 디렉터리를 사용합니다

### 컴포넌트 통신 패턴

| 시나리오 | 패턴 | 예시 |
|----------|------|------|
| 부모-자식 통신 | `defineProps<T>()` + `defineEmits<T>()` | `<LoginForm @login-success="onLogin" />` |
| 컴포넌트 간 상태 | Pinia Store | `useUserStore().token` |
| 로직 재사용 | Composable | `useDictOptions('dict_type_code')` |
| 슬롯 확장 | `<slot>` + named slots | `<template #header>...</template>` |

---

## 7. 컴포넌트 규칙

### SFC 요소 순서

```vue
<script setup lang="ts">
// Script 먼저
</script>

<template>
  <!-- Template 중간 -->
</template>

<style scoped lang="scss">
/* Styles 마지막 */
</style>
```

### 규칙

- 모든 컴포넌트는 `<script setup lang="ts">`를 사용합니다
- Props: `defineProps<T>()` TypeScript 제네릭을 사용합니다
- 디렉터리: kebab-case 디렉터리 + `index.vue`(예: `views/login/index.vue`)
- 스타일: `<style scoped lang="scss">`로 컴포넌트별 격리
- 전역 스타일은 `src/styles/`에 배치
- 서드파티 컴포넌트 스타일을 재정의하는 경우를 제외하고 `!important` 사용 금지

---

## 8. 명명 규칙 (TypeScript)

| 유형 | 스타일 | 예시 |
|------|--------|------|
| 변수 / 함수 | lowerCamelCase | `getUserById`, `userList` |
| 인터페이스 / 타입 | UpperCamelCase | `ApiResponse`, `UserInfo` |
| 컴포넌트 파일 | kebab-case 디렉터리 + `index.vue` | `views/login/index.vue` |
| Store 파일 | kebab-case | `stores/user.ts` |
| Store Hook | `use` 접두사 | `useUserStore` |
| Composable | `use` 접두사 | `useDictOptions` |
| 상수 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| CSS 클래스명 | kebab-case | `sidebar-menu`, `login-container` |

---

## 9. 코드 포맷 (TypeScript / Vue)

- 들여쓰기: 2칸 공백
- 세미콜론 없음(ASI 스타일)
- 작은따옴표; JSX에서는 큰따옴표
- 여러 줄 구조에서는 후행 쉼표 유지
- 타입 전용 임포트에는 `import type { ... }` 사용
- `src/` 아래 모듈 임포트 시 `@` 경로 별칭 사용
- Vue SFC 순서: `<script setup>` → `<template>` → `<style scoped>`

---

## 10. UI 프레임워크

- **Element Plus**를 UI 컴포넌트 라이브러리로 사용합니다(폼, 테이블, 카드, 메뉴 등)
- **@element-plus/icons-vue**로 아이콘을 제공합니다(`main.ts`에서 전역 등록)
- Element Plus CSS 변수를 사용하여 테마를 커스터마이징합니다(`--el-color-primary`, `--el-bg-color-page` 등)
- 폼 유효성 검사: Element Plus `FormRules` + `ref<FormInstance>()` 패턴
- 다크 모드: `theme-chalk/dark/css-vars.css`를 통해 자동 전환

```typescript
// 폼 유효성 검사 표준 패턴
const formRef = ref<FormInstance>()
const rules: FormRules = {
  username: [{ required: true, message: '사용자 이름을 입력하십시오', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value?.validate()  // 유효성 검사를 통과해야 계속 진행
  // ... 제출 로직
}
```

---

## 11. 국제화(i18n)

### 설정

`vue-i18n` 11.x를 기반으로 하며, Composition API 모드(`legacy: false`)를 사용합니다:

```typescript
// i18n/index.ts
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),        // localStorage에서 읽기, 기본값 'zh-CN'
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

### 사용 방식

```typescript
// 컴포넌트 내부
const { t, locale } = useI18n()
const label = t('common.dashboard')

// 언어 전환
locale.value = 'en-US'
storeLang('en-US')  // localStorage에 영속화
```

### 번역 파일 구조

번역 파일은 `src/locales/`에 위치하며, 모듈별로 key를 조직합니다:

```typescript
// zh-CN.ts
export default {
  common: {
    dashboard: '대시보드',
    systemManagement: '시스템 관리',
    userManagement: '사용자 관리',
    // ...
  },
  login: {
    title: '로그인',
    username: '사용자 이름',
    // ...
  },
  // ...
}
```

### 신규 모듈 i18n 체크리스트

| 단계 | 파일 | 작업 |
|------|------|------|
| 1 | `src/locales/zh-CN.ts` | `common.<feature>Management` 및 기능별 번역 key 추가 |
| 2 | `src/locales/en-US.ts` | 해당 영어 번역 추가 |
| 3 | `src/constants/menu.ts` → `menuI18nMap` | `permissionCode → i18n key` 매핑 추가 |

---

## 12. 권한 및 접근 제어

### Permission Store (`src/stores/permission.ts`)

`usePermissionStore`는 권한 코드 목록과 동적 메뉴 트리를 관리합니다:

```typescript
// JWT Token에서 권한 코드 초기화
permissionStore.initFromToken()

// 백엔드에서 동적 메뉴 로드
await permissionStore.loadMenus()

// 사용자가 특정 권한을 보유하고 있는지 조회
permissionStore.hasPermission('system:user:create')
```

**데이터 출처**:
- 권한 코드 목록: JWT Token의 `authorities` 필드(로그인 시 기록되며, `getPermissionsFromToken()`을 통해 파싱)
- 동적 메뉴 트리: `GET /api/auth/menus` 호출(백엔드에서 사용자 권한에 따라 필터링됨)

### 동적 메뉴 라우팅

로그인 성공 후, 라우트 가드는 다음 흐름을 트리거합니다:

```
JWT 디코딩 → permissionStore.initFromToken() 권한 코드 추출
    → permissionStore.loadMenus() 백엔드 필터링된 메뉴 트리 획득
    → registerDynamicRoutes() 메뉴 트리를 순회하며 Vue Router 라우트 추가
    → 사이드바에서 permissionStore.menuTree 렌더링
```

메뉴 데이터 구조(`MenuNode`)에는 `path`(프론트엔드 라우트 경로), `permissionCode`(권한 코드), `type`(DIRECTORY/MENU) 등의 필드가 포함됩니다.

### 버튼 수준 권한 (`v-permission`)

Vue 커스텀 디렉티브 `v-permission`을 통해 버튼의 표시/숨김을 제어합니다:

```vue
<!-- system:user:create 권한을 가진 사용자만 볼 수 있음 -->
<el-button v-permission="'system:user:create'" type="primary">
  신규 추가
</el-button>
```

**구현 원리**:
- 디렉티브 마운트 시 `usePermissionStore`에서 권한 코드를 조회합니다
- 권한이 없을 때 `el.style.display = 'none'`을 설정합니다(`removeChild`가 아니며, Vue 반응형 업데이트와 호환됩니다)
- `mounted` 및 `updated` 두 훅에서 검사를 실행합니다

**등록 방식**(`src/directives/permission.ts`):

```typescript
// main.ts에서 등록
import { setupPermissionDirective } from '@/directives/permission'
setupPermissionDirective(app)
```

### 규칙

- 권한 코드는 반드시 백엔드 `sys_permission` 테이블에 정의된 것과 일치해야 하며, 형식은 `resource:action`입니다
- `v-permission` 디렉티브는 UI 계층의 표시/숨김 제어에만 사용하며, 백엔드 `@PreAuthorize`가 보안 경계입니다
- 동적 라우트는 반드시 내비게이션 가드에서 `permissionStore.loadMenus()` 완료 후 등록해야 합니다
- 로그아웃 시 반드시 `permissionStore.reset()`을 호출하여 권한 상태를 초기화해야 합니다

### XSS 방어 설정 관리

XSS 방어 관리 페이지(`views/system/xssconfig/index.vue`)는 전역 스위치 및 블랙리스트 규칙 CRUD를 제공합니다:

- **전역 스위치**: `el-switch` 컴포넌트, `PUT /api/auth/xss-config/toggle` 호출
- **규칙 목록**: `el-table` 페이지네이션 테이블, 신규 생성, 수정, 삭제, 개별 활성화/비활성화 지원
- **테넌트 격리**: API 계층에서 JWT Token으로부터 `tenant_id`를 추출하여 `X-Tenant-Id` 요청 헤더로 전달
- **규칙 유형**: HTML 태그 / 이벤트 핸들러 / 위험 프로토콜 / 커스텀 정규식

---

## 13. 비즈니스 모듈 구조 패턴 (Business Module Structure Pattern)

신규 비즈니스 모듈 추가 시, 다음 규칙에 따라 디렉터리를 조직하고 라우트를 등록하며 국제화를 설정합니다.

### 표준 디렉터리 레이아웃

「기본 데이터 모듈 → 사전 관리 기능」을 예로 듭니다:

```
src/
├── views/
│   └── base/                  # 모듈 그룹 디렉터리
│       └── dict/
│           └── index.vue      # 기능 페이지(SFC 순서: script → template → style)
├── api/
│   └── dict.ts               # API 함수 + TypeScript 인터페이스(파일당 하나의 기능 도메인)
├── stores/
│   └── dict.ts               # (선택) 복잡한 클라이언트 상태가 필요한 경우에만 생성
└── locales/
    ├── zh-CN.ts              # 중국어 번역
    └── en-US.ts              # 영어 번역
```

### 등록 체크리스트

각 신규 비즈니스 모듈은 다음 등록을 완료해야 합니다:

| 단계 | 파일 | 작업 |
|------|------|------|
| 1 | `src/router/index.ts` → `iconMap` | `'<module>:<feature>': '<IconName>'` 추가(예: `'base:dict': 'Collection'`) |
| 2 | `src/constants/menu.ts` → `menuI18nMap` | 모듈 디렉터리 및 메뉴 항목의 i18n key 매핑 추가 |
| 3 | `src/locales/zh-CN.ts` + `en-US.ts` | `common.<module>Management`(디렉터리명), `common.<feature>Management`(메뉴명) 및 기능별 번역 key 추가 |
| 4 | `scripts/sql/seed/auth.sql` → `sys_permission` | 멱등 DIRECTORY(모듈 그룹) + MENU(기능 페이지) + BUTTON/API(조작 권한) 시드를 추가하고 seed manifest 갱신 |
| 5 | `scripts/sql/seed/auth.sql` → `sys_role_permission` | SUPER_ADMIN 역할에 신규 권한 노드를 멱등 할당하고 자연 키 검증 추가 |

### 참고 구현: 사전 관리 모듈

`base:dict`(사전 관리)를 신규 모듈의 참고 템플릿으로 사용합니다:

- **View**: `views/base/dict/index.vue` — master-detail 레이아웃(왼쪽 유형 목록 10/24 cols, 오른쪽 데이터 목록 14/24 cols)
- **API**: `api/dict.ts` — 11개의 typed 함수(`listDictTypes`, `getDictType`, `createDictType`, `updateDictType`, `deleteDictType`, `toggleDictTypeStatus`, `listDictData`, `createDictData`, `updateDictData`, `deleteDictData`, `refreshDictCache`)
- **Permission codes**: `dict:type:*`(4개) + `dict:data:*`(5개)
- **UI patterns**: `v-permission` 버튼 권한 제어, `el-switch` 상태 전환, `el-pagination` 페이지네이션, `ElMessageBox.confirm` 삭제 확인, `X-Tenant-Id` 멀티 테넌트 격리

---

## 14. Composables 패턴

Composables는 Omni-Stack 프론트엔드의 로직 재사용 핵심 메커니즘으로, 기존의 Mixin 모드를 대체합니다.

### 설계 원칙

| 원칙 | 설명 |
|------|------|
| **단일 책임** | 하나의 Composable은 하나의 기능 도메인만 담당합니다 |
| **반환값 규칙** | `{ state, computed, actions }` 객체를 반환하며, 구조 분해 할당으로 소비합니다 |
| **반응형** | 내부에서 `ref()` / `reactive()`를 사용하며, 반환값은 자동으로 반응형이 됩니다 |
| **수명 주기 안전** | `onMounted` / `onUnmounted`에서 부작용을 관리합니다 |

### 대표적인 Composable: useDictOptions

```typescript
// composables/useDictOptions.ts
export function useDictOptions(dictTypeCode: string) {
  const options = ref<DictOption[]>([])
  const loading = ref(false)

  async function loadOptions() {
    loading.value = true
    try {
      const { data: res } = await listDictDataByType(dictTypeCode)
      options.value = res.data.map(item => ({
        label: item.dictLabel,
        value: item.dictValue,
      }))
    } finally {
      loading.value = false
    }
  }

  onMounted(loadOptions)

  return { options, loading, reload: loadOptions }
}
```

**사용 방식**:

```vue
<script setup lang="ts">
const { options: categoryOptions } = useDictOptions('workflow_category')
</script>

<template>
  <el-select v-model="form.category">
    <el-option v-for="opt in categoryOptions" :key="opt.value"
               :label="opt.label" :value="opt.value" />
  </el-select>
</template>
```

### 기존 Composables 목록

| Composable | 파일 | 역할 |
|------------|------|------|
| `useDictOptions` | `composables/useDictOptions.ts` | 사전 옵션을 로드하고 반응형 `options` 배열을 반환 |
| `useBpmnModeler` | `composables/useBpmnModeler.ts` | bpmn-js Modeler의 생성, 소멸, 가져오기/내보내기 관리 |
| `useBpmnExtension` | `composables/useBpmnExtension.ts` | BPMN extensionElements 읽기/쓰기 조작 |

---

## 15. Docker 배포 환경에서의 Vite 빌드 최적화

### 다단계 빌드

프론트엔드 Docker 이미지는 2단계 빌드를 사용합니다(상세: `docker/frontend/Dockerfile`):

```dockerfile
# 단계 1: 빌드
FROM node:22-alpine AS builder
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                      # 결정적 설치(버전 고정)
COPY omni-frontend/ .
RUN npm run build               # vue-tsc 타입 검사 + Vite 프로덕션 빌드

# 단계 2: 실행(Nginx 정적 서버)
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
```

### Vite 빌드 설정

```typescript
// vite.config.ts
build: {
  target: 'es2020',            // 빌드 대상: 옵셔널 체이닝, 널 병합 등 최신 문법 지원
  outDir: 'dist',
  chunkSizeWarningLimit: 2000, // Element Plus의 용량이 크므로 임계값을 적절히 상향 조정
}
```

### 프로덕션 빌드 최적화 항목

| 최적화 항목 | 설명 |
|-------------|------|
| **라우트 지연 로딩** | 모든 views 컴포넌트는 `() => import()` 동적 임포트를 사용하여 지연 로딩을 구현합니다 |
| **Element Plus按需** | `@vitejs/plugin-vue`를 통해 사용되지 않는 컴포넌트를 자동으로 tree-shake합니다 |
| **`import.meta.glob`** | 뷰 모듈 스캔은 컴파일 시 정적 분석을 수행하여 무관한 파일을 번들에 포함하지 않습니다 |
| **chunk splitting** | Vite/Rollup이 자동으로 vendor(vue, element-plus, axios)를 독립 chunk로 분리합니다 |
| **CSS 추출** | 프로덕션 빌드 시 CSS를 독립 파일로 자동 추출하여 브라우저 캐싱을 지원합니다 |

### Nginx 설정 핵심

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA History 모드: 모든 비파일 요청을 index.html로 폴백
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 리버스 프록시 → Gateway(컨테이너 내부 포트)
    location /api/ {
        proxy_pass http://omni-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OAuth2 엔드포인트 프록시
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
    }
}
```

---

## 16. 빌드 및 도구

| 도구 | 용도 | 설정 |
|------|------|------|
| Vite 8 | 개발 서버 + 번들러 | `vite.config.ts` |
| TypeScript 5.9 | 타입 검사 | `tsconfig.json`(strict 모드) |
| ESLint 9 | 코드 린팅 | `eslint.config.mjs`(flat config) |
| Sass | CSS 전처리 | `<style lang="scss">`를 통해 사용 |
| vue-i18n 11 | 국제화 | `src/i18n/index.ts` |
| bpmn-js 18 | BPMN 프로세스 모델링 | `composables/useBpmnModeler.ts` |

### Vite 프록시

개발 서버는 `/api`, `/oauth2`, `/.well-known`을 `http://localhost:8102`(Gateway)로 프록시합니다:

```typescript
server: {
  port: 3000,
  host: true,
  proxy: {
    '/api': { target: 'http://localhost:8102', changeOrigin: true },
    '/oauth2': { target: 'http://localhost:8102', changeOrigin: true },
    '/.well-known': { target: 'http://localhost:8102', changeOrigin: true },
  },
},
```

### 명령어

```bash
npm install        # 의존성 설치
npm run dev        # 개발 서버 :3000
npm run build      # 타입 검사 + 프로덕션 빌드
npm run lint       # ESLint 검사
npm run preview    # 프로덕션 빌드 미리보기
```

---

## 17. 문제 해결 가이드

### 개발 환경 문제

| 문제 | 가능한 원인 | 해결 방법 |
|------|-------------|-----------|
| **Vite 기동 오류 `EADDRINUSE`** | 포트 3000이 점유됨 | `lsof -i :3000` 또는 `netstat -ano \| findstr :3000`으로 점유 프로세스를 종료하거나 `vite.config.ts`의 `server.port`를 수정 |
| **API 요청 404** | Gateway가 기동되지 않았거나 프록시 설정 오류 | Gateway가 8102 포트에서 실행 중인지 확인; `vite.config.ts`의 proxy target 확인 |
| **CORS 오류** | 프록시를 통하지 않고 백엔드에 직접 요청 | 요청이 `/api` 접두사를 통해 Vite 프록시를 거치도록 확인 |
| **Element Plus 스타일 누락** | CSS를 임포트하지 않음 | `main.ts`에 `import 'element-plus/dist/index.css'`가 포함되어 있는지 확인 |
| **TypeScript 타입 오류** | `node_modules` 캐시 만료 | `rm -rf node_modules && npm install` 실행 |
| **HMR이 작동하지 않음** | 파일 시스템 감시자 제한 | Linux: `echo fs.inotify.max_user_watches=524288 \| sudo tee -a /etc/sysctl.conf` |

### 빌드 문제

| 문제 | 가능한 원인 | 해결 방법 |
|------|-------------|-----------|
| **`vue-tsc` 타입 검사 실패** | 타입 불일치 | `npx vue-tsc --noEmit`을 실행하여 구체적인 파일과 행 번호를 확인 |
| **chunk 크기가 너무 큼** | 의존성이 tree-shake되지 않음 | `npx vite build --mode analyze`를 실행하여 의존성 용량을 분석 |
| **Docker 빌드 `npm ci` 실패** | 네트워크 문제 또는 `package-lock.json` 비동기화 | 로컬에서 `npm install`을 다시 실행하여 새 lock 파일을 생성하거나 npm 미러 소스를 설정 |

### 런타임 문제

| 문제 | 가능한 원인 | 해결 방법 |
|------|-------------|-----------|
| **로그인 후 흰 화면** | Token이 유효하지 않거나 메뉴 로드 실패 | DevTools → Application → Local Storage에서 token 확인; Network 탭에서 `/api/auth/menus` 응답 확인 |
| **동적 메뉴가 표시되지 않음** | `permissionStore.loadMenus()` 실패 | 백엔드 `/api/auth/menus`가 정상적으로 반환되는지 확인; `menusLoaded` 상태 확인 |
| **v-permission 버튼이 계속 숨겨짐** | 권한 코드 불일치 | `sys_permission` 테이블의 `permission_code`와 프론트엔드 `v-permission` 값을 비교 |
| **OAuth2 콜백 404** | redirect_uri 설정 오류 | Auth 서비스의 OAuth2 클라이언트 설정에서 redirect_uri가 실제 콜백 URL과 일치하는지 확인 |
| **i18n에 key가 번역 대신 표시됨** | 번역 key 누락 | `zh-CN.ts` / `en-US.ts`에 해당 key가 포함되어 있는지 확인; `menuI18nMap` 매핑 확인 |
| **SPA 라우트 새로고침 시 404** | Nginx 폴백 설정 누락 | Nginx 설정에 `try_files $uri $uri/ /index.html;`이 포함되어 있는지 확인 |
