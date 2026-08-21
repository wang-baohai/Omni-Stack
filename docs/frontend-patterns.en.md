# Frontend Patterns & Conventions

> This document defines the internal organization of the Omni-Stack frontend. All frontend code must follow these patterns.  
> For an architecture overview, see [architecture.en.md](architecture.en.md). For Docker deployment configuration, see [docker-deployment.en.md](docker-deployment.en.md).

---

## Table of Contents

- [1. Technology Choices](#1-technology-choices)
- [2. Directory Structure](#2-directory-structure)
- [3. API Layer](#3-api-layer-srcapi)
- [4. State Management](#4-state-management-srcstores)
- [5. Router](#5-router-srcrouter)
- [6. Component Design Patterns](#6-component-design-patterns)
- [7. Component Conventions](#7-component-conventions)
- [8. Naming Conventions](#8-naming-conventions-typescript)
- [9. Code Format](#9-code-format-typescript--vue)
- [10. UI Framework](#10-ui-framework)
- [11. Internationalization (i18n)](#11-internationalization-i18n)
- [12. Permission & Access Control](#12-permission--access-control)
- [13. Business Module Structure Pattern](#13-business-module-structure-pattern)
- [14. Composables Pattern](#14-composables-pattern)
- [15. Vite Build Optimization for Docker Deployment](#15-vite-build-optimization-for-docker-deployment)
- [16. Build & Tooling](#16-build--tooling)
- [17. Troubleshooting Guide](#17-troubleshooting-guide)

---

## 1. Technology Choices

### Why Vue 3.5 + Composition API

| Consideration | Rationale |
|---------------|-----------|
| **First-class TypeScript support** | Vue 3.5 provides top-tier type inference for `<script setup lang="ts">`; `defineProps<T>()` requires no runtime declaration |
| **Composition API** | Logic reuse via Composables, eliminating the naming conflicts and opaque origins of Mixins |
| **Tree-shaking friendly** | Import APIs on demand (`ref`, `computed`, `watch`); unused features are excluded from the bundle |
| **Performance** | Proxy-based reactivity is faster than Vue 2's `Object.defineProperty` and supports `Map`/`Set` collection types |
| **Teleport / Suspense** | Built-in `<Teleport>` and `<Suspense>` components simplify modal and async component handling |

### Why Vite 8

| Consideration | Rationale |
|---------------|-----------|
| **Native ESM** | Dev server runs on native browser ES Modules — no bundling needed at startup; cold start drops from Webpack's 30s+ to < 2s |
| **HMR speed** | Module-level HMR updates only the changed module, not the entire chunk; hot update latency < 50ms |
| **Rollup compatibility** | Production builds are powered by Rollup with mature tree-shaking and chunk splitting |
| **Plugin ecosystem** | `@vitejs/plugin-vue` 6.x provides SFC compilation and HMR integration |

### Why Pinia 3 over Vuex

| Consideration | Rationale |
|---------------|-----------|
| **Officially recommended** | Vue has recommended Pinia over Vuex since Vue 3; Vuex is in maintenance mode |
| **Composition API style** | `defineStore('id', () => { ... })` aligns with Vue 3's `<script setup>` style |
| **TypeScript support** | Full type inference without `@Mutation` / `@Action` decorators |
| **No Mutations** | Only State + Getters + Actions, simplifying the data flow (mutate state directly or via actions) |
| **Lightweight** | Core package < 1KB gzipped, zero boilerplate |
| **DevTools** | Vue DevTools integration with time-travel debugging and state snapshots |

### Why Element Plus 2.x

| Consideration | Rationale |
|---------------|-----------|
| **Vue 3 native** | The Vue 3 version of Element UI with full Composition API support |
| **Enterprise-grade components** | Mature components for tables (`el-table`), forms (`el-form`), dialogs (`el-dialog`), etc. |
| **Built-in i18n** | `vue-i18n` integration with built-in Chinese and English messages |
| **Dark mode** | One-line dark theme toggle via `theme-chalk/dark/css-vars.css` |
| **Icon library** | `@element-plus/icons-vue` provides 280+ vector icons |

---

## 2. Directory Structure

```
omni-frontend/
├── index.html              # Vite entry HTML
├── package.json
├── vite.config.ts          # Vite config (proxy, alias, build)
├── tsconfig.json
├── eslint.config.mjs       # ESLint flat config
└── src/
    ├── main.ts             # App bootstrap (Pinia, Router, Element Plus, I18n)
    ├── App.vue             # Root component (<router-view />)
    ├── api/                # API layer (one file per business domain)
    │   ├── request.ts      # Shared Axios instance + interceptors
    │   ├── user.ts         # User API functions
    │   ├── dict.ts         # Dictionary API (type + data CRUD)
    │   ├── auth.ts         # Auth API (login, register, captcha, social login, OAuth2)
    │   ├── menu.ts         # Menu API (dynamic menu tree)
    │   ├── workflow.ts     # Workflow API (models, definitions, instances, approvals)
    │   └── myJob.ts        # User scheduled job API
    ├── stores/             # Pinia stores (one per business domain)
    │   ├── user.ts         # User auth store (token, user info)
    │   ├── app.ts          # App settings store (theme, sidebar)
    │   └── permission.ts   # Permission store (permission codes, dynamic menu tree)
    ├── router/
    │   └── index.ts        # Route definitions + navigation guard + dynamic route registration
    ├── views/              # Page components (kebab-case directories)
    │   ├── home/index.vue          # Home (landing page for guests / workspace for authenticated users)
    │   ├── login/index.vue         # Login page
    │   ├── register/index.vue      # Registration page
    │   ├── callback/index.vue      # OAuth2 callback page
    │   ├── dashboard/index.vue     # Dashboard
    │   ├── consent/index.vue       # OAuth2 authorization consent page
    │   ├── device/                 # Device authorization (Device Code Flow)
    │   │   ├── index.vue           # Device simulator: displays QR code + polls for token
    │   │   └── verify.vue          # User consent/verification page
    │   ├── base/                   # Base data module
    │   │   └── dict/index.vue      # Dictionary management (master-detail layout)
    │   ├── system/                 # System administration module
    │   │   ├── user/index.vue      # User management
    │   │   ├── role/index.vue      # Role management
    │   │   ├── permission/index.vue # Permission management
    │   │   ├── org/index.vue       # Organization management
    │   │   ├── tenant/index.vue    # Tenant management
    │   │   ├── oauth2/index.vue    # OAuth2 client management
    │   │   ├── online/index.vue    # Online user monitoring
    │   │   └── xssconfig/index.vue # XSS protection configuration
    │   ├── workflow/               # Workflow module
    │   │   ├── model/index.vue     # Process model management + BPMN designer
    │   │   ├── definition/index.vue # Process definition management
    │   │   ├── instance/index.vue  # Process instance management
    │   │   └── stats/index.vue     # Process statistics
    │   └── monitor/                # Monitoring module
    │       └── oper-log/index.vue  # Operation logs
    ├── layout/
    │   └── index.vue       # App shell layout (sidebar, header, content area)
    ├── components/         # Shared/reusable UI components
    │   ├── LoginForm.vue           # Login form (password + SSO + third-party OAuth2)
    │   ├── CronGenerator.vue       # Cron expression generator
    │   ├── DynamicFormRenderer.vue # Dynamic form renderer
    │   └── workflow/               # Workflow-specific components
    │       ├── ModelDesigner.vue   # BPMN modeling designer
    │       └── panels/             # BPMN property panels
    ├── composables/        # Vue Composables (logic reuse)
    │   ├── useDictOptions.ts       # Dictionary option loading
    │   ├── useBpmnModeler.ts       # BPMN Modeler lifecycle management
    │   └── useBpmnExtension.ts     # BPMN extension element read/write
    ├── constants/          # Constant definitions
    │   └── menu.ts                 # Menu i18n mapping table
    ├── directives/         # Vue custom directives
    │   └── permission.ts           # v-permission directive
    ├── utils/              # Utility functions
    │   ├── jwt.ts                  # JWT parsing (extract permissions, tenant ID)
    │   └── pkce.ts                 # PKCE utilities (OAuth2 Device Code Flow)
    ├── i18n/
    │   └── index.ts        # vue-i18n instance configuration
    ├── locales/            # Translation files
    │   ├── zh-CN.ts        # Chinese translations
    │   └── en-US.ts        # English translations
    ├── types/              # Shared TypeScript types
    │   └── api.ts          # ApiResponse<T>, PageResult<T>
    └── styles/
        └── index.scss      # Global styles (reset, layout, theme)
```

---

## 3. API Layer (`src/api/`)

### Shared Axios Instance

`request.ts` exports a single configured Axios instance:

- **baseURL**: Read from `VITE_API_BASE_URL` env variable, defaults to `/api`
- **timeout**: 15000ms
- **withCredentials**: `true` (carries cookies for OAuth2 session management)
- **Request interceptor**: Reads token from `useUserStore()` and injects `Authorization: Bearer <token>`
- **Response interceptor**: Checks `code === 200`; business errors trigger `ElMessage.error`; 401 triggers an expiration dialog and redirects to the login page (with a `redirect` parameter)

### API Function Pattern

One file per business domain. Function naming conventions:

| Operation | Prefix | Example |
|-----------|--------|---------|
| Get by ID | `getXxx` | `getUserById(id)` |
| Paginated list | `listXxx` | `listUsers(page, size)` |
| Create | `createXxx` | `createUser(data)` |
| Update | `updateXxx` | `updateUser(id, data)` |
| Delete | `deleteXxx` | `deleteUser(id)` |

All functions must have typed parameters and return types using `ApiResponse<T>`:

```typescript
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### Type System

- **Shared types** (`ApiResponse<T>`, `PageResult<T>`): Defined in `src/types/api.ts` as the single source of truth
- **Domain types** (e.g., `UserInfo`, `SysUser`): Co-located in the API files where they are used (`src/api/user.ts`)
- **Never duplicate** shared type definitions; always import from `@/types/api`
- Use `import type { ... }` for type-only imports

---

## 4. State Management (`src/stores/`)

### Pinia Composition API Style

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

### Rules

- One store per business domain: `user.ts`, `app.ts`, `permission.ts`
- Store file naming: kebab-case
- Store hook naming: `use` prefix (`useUserStore`, `useAppStore`, `usePermissionStore`)
- Use Composition API style (`defineStore('id', () => { ... })`)
- Persist with `localStorage`; non-persistent state uses `ref()`
- No direct DOM manipulation in stores

### Core Store Responsibilities

| Store | Responsibility | Persistence |
|-------|---------------|-------------|
| `useUserStore` | Token management, basic user info (username, nickname, avatar) | `localStorage` (token) |
| `useAppStore` | Theme switching (light/dark), sidebar collapse state, language preference | `localStorage` (theme, lang) |
| `usePermissionStore` | Permission code list (parsed from JWT), dynamic menu tree (loaded from backend), `menusLoaded` flag | None (re-fetched on every login) |

---

## 5. Router (`src/router/`)

### Route Architecture

Omni-Stack uses a **static routes + dynamic routes** hybrid architecture:

```
Static Routes (public pages)
├── /            → Home (landing page / workspace)
├── /login       → Login
├── /register    → Register
├── /callback    → OAuth2Callback
├── /device      → DeviceAuth
├── /device/verify → DeviceVerify
├── /consent     → OAuth2 authorization consent
└── /admin       → Layout (admin shell)
    └── /admin/dashboard → Dashboard (static child route)

Dynamic Routes (permission-driven, registered at runtime)
└── /admin/<feature> → maps to views/<module>/<feature>/index.vue
    e.g.: /admin/user → views/system/user/index.vue
          /admin/dict → views/base/dict/index.vue
```

### Navigation Guard Flow

```
router.beforeEach()
    ↓
Check to.meta.requiresAuth
    ├── false → Authenticated user visiting /login → redirect to /home
    │           Other public pages → allow
    └── true  → Check userStore.token
                ├── No token → redirect to /login?redirect=<path>
                └── Has token → Check permissionStore.menusLoaded
                    ├── false → initFromToken() → loadMenus() → registerDynamicRoutes() → re-navigate
                    └── true  → registerDynamicRoutes() (idempotent) → allow
```

### Convention-Based View Component Mapping

Dynamic routes use `import.meta.glob` to scan all `index.vue` files under `views/`, achieving automatic permissionCode → view component mapping:

```typescript
// router/index.ts
const viewModules = import.meta.glob('../views/**/index.vue')

// Mapping rule: permissionCode "system:user" → "views/system/user/index.vue"
function resolveViewComponent(permissionCode: string) {
  if (viewOverrides[permissionCode]) return viewModules[viewOverrides[permissionCode]]
  const modulePath = permissionCode.replace(/:/g, '/')
  return viewModules[`../views/${modulePath}/index.vue`]
}
```

**Override mappings** (`viewOverrides`):

| permissionCode | Override Path | Reason |
|----------------|---------------|--------|
| `system:oauth2` | `views/oauth2-client/index.vue` | Avoid conflict with OAuth2 callback page |
| `base:operlog` | `views/monitor/oper-log/index.vue` | Categorized under monitor directory |

### Rules

- All route components use lazy loading: `() => import('@/views/xxx/index.vue')`
- `meta` fields: `title` (required), `icon` (optional), `requiresAuth` (required), `permissionCode` (set automatically for dynamic routes)
- Auth-required routes: explicitly set `requiresAuth: true`
- Public routes: explicitly set `requiresAuth: false`
- On logout, call `clearDynamicRoutes()` to remove all dynamic routes

---

## 6. Component Design Patterns

### Page Components (Views)

Page components follow a standard **data + operations + UI** three-layer structure:

```vue
<script setup lang="ts">
// 1. Imports (API, types, components, i18n)
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, type SysUser } from '@/api/user'

const { t } = useI18n()

// 2. Reactive state
const tableData = ref<SysUser[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 3. Form state
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = { username: [{ required: true, ... }] }

// 4. Business functions
async function loadData() { loading.value = true; try { ... } finally { loading.value = false } }
function handleCreate() { dialogVisible.value = true }
async function handleSubmit() { await formRef.value?.validate(); ... }
async function handleDelete(id: number) { await ElMessageBox.confirm(...); ... }
</script>
```

### Shared Components (Components)

- General-purpose UI components go in `src/components/`
- Business-specific components are co-located with their corresponding views
- Components use UpperCamelCase (`<UserCard />`); files use kebab-case directories

### Component Communication Patterns

| Scenario | Pattern | Example |
|----------|---------|---------|
| Parent-child | `defineProps<T>()` + `defineEmits<T>()` | `<LoginForm @login-success="onLogin" />` |
| Cross-component state | Pinia Store | `useUserStore().token` |
| Logic reuse | Composable | `useDictOptions('dict_type_code')` |
| Slot extension | `<slot>` + named slots | `<template #header>...</template>` |

---

## 7. Component Conventions

### SFC Element Order

```vue
<script setup lang="ts">
// Script first
</script>

<template>
  <!-- Template middle -->
</template>

<style scoped lang="scss">
/* Styles last */
</style>
```

### Rules

- All components use `<script setup lang="ts">`
- Props: use `defineProps<T>()` with TypeScript generics
- Directory: kebab-case directory + `index.vue` (e.g., `views/login/index.vue`)
- Styles: `<style scoped lang="scss">` for per-component isolation
- Global styles go in `src/styles/`
- No `!important` except when overriding third-party component styles

---

## 8. Naming Conventions (TypeScript)

| Type | Style | Example |
|------|-------|---------|
| Variable / Function | lowerCamelCase | `getUserById`, `userList` |
| Interface / Type | UpperCamelCase | `ApiResponse`, `UserInfo` |
| Component file | kebab-case dir + `index.vue` | `views/login/index.vue` |
| Store file | kebab-case | `stores/user.ts` |
| Store hook | `use` prefix | `useUserStore` |
| Composable | `use` prefix | `useDictOptions` |
| Constant | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| CSS class | kebab-case | `sidebar-menu`, `login-container` |

---

## 9. Code Format (TypeScript / Vue)

- Indentation: 2 spaces
- No semicolons (ASI style)
- Single quotes; double quotes in JSX
- Trailing commas in multi-line structures
- Use `import type { ... }` for type-only imports
- Use `@` path alias for `src/` imports
- Vue SFC order: `<script setup>` → `<template>` → `<style scoped>`

---

## 10. UI Framework

- **Element Plus** as the UI component library (forms, tables, cards, menus, etc.)
- **@element-plus/icons-vue** for icons (globally registered in `main.ts`)
- Use Element Plus CSS variables for theme customization (`--el-color-primary`, `--el-bg-color-page`, etc.)
- Form validation: Element Plus `FormRules` + `ref<FormInstance>()` pattern
- Dark mode: automatic toggle via `theme-chalk/dark/css-vars.css`

```typescript
// Standard form validation pattern
const formRef = ref<FormInstance>()
const rules: FormRules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value?.validate()  // Proceed only after validation passes
  // ... submit logic
}
```

---

## 11. Internationalization (i18n)

### Configuration

Based on `vue-i18n` 11.x, using Composition API mode (`legacy: false`):

```typescript
// i18n/index.ts
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),        // Read from localStorage, defaults to 'zh-CN'
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

### Usage

```typescript
// Inside a component
const { t, locale } = useI18n()
const label = t('common.dashboard')

// Switch language
locale.value = 'en-US'
storeLang('en-US')  // Persist to localStorage
```

### Translation File Structure

Translation files are located in `src/locales/`, organized by module keys:

```typescript
// zh-CN.ts
export default {
  common: {
    dashboard: '仪表盘',
    systemManagement: '系统管理',
    userManagement: '用户管理',
    // ...
  },
  login: {
    title: '登录',
    username: '用户名',
    // ...
  },
  // ...
}
```

### i18n Checklist for New Modules

| Step | File | Action |
|------|------|--------|
| 1 | `src/locales/zh-CN.ts` | Add `common.<feature>Management` and feature-level translation keys |
| 2 | `src/locales/en-US.ts` | Add corresponding English translations |
| 3 | `src/constants/menu.ts` → `menuI18nMap` | Add `permissionCode → i18n key` mapping |

---

## 12. Permission & Access Control

### Permission Store (`src/stores/permission.ts`)

`usePermissionStore` manages the permission code list and dynamic menu tree:

```typescript
// Initialize permission codes from JWT token
permissionStore.initFromToken()

// Load dynamic menus from backend
await permissionStore.loadMenus()

// Check if user has a specific permission
permissionStore.hasPermission('system:user:create')
```

**Data sources**:
- Permission code list: `authorities` field in the JWT Token (written at login, parsed via `getPermissionsFromToken()`)
- Dynamic menu tree: `GET /api/auth/menus` call (backend filters by user permissions)

### Dynamic Menu Routing

After a successful login, the navigation guard triggers the following flow:

```
JWT decode → permissionStore.initFromToken() extracts permission codes
    → permissionStore.loadMenus() fetches backend-filtered menu tree
    → registerDynamicRoutes() iterates the menu tree to add Vue Router routes
    → Sidebar renders permissionStore.menuTree
```

The menu data structure (`MenuNode`) includes fields such as `path` (frontend route path), `permissionCode` (permission code), and `type` (DIRECTORY/MENU).

### Button-Level Permission (`v-permission`)

The Vue custom directive `v-permission` controls button visibility:

```vue
<!-- Only visible to users with system:user:create permission -->
<el-button v-permission="'system:user:create'" type="primary">
  Create
</el-button>
```

**Implementation details**:
- On directive mount, queries permission codes from `usePermissionStore`
- When permission is absent, sets `el.style.display = 'none'` (not `removeChild`, to stay compatible with Vue reactivity)
- Checks are performed in both `mounted` and `updated` hooks

**Registration** (`src/directives/permission.ts`):

```typescript
// Register in main.ts
import { setupPermissionDirective } from '@/directives/permission'
setupPermissionDirective(app)
```

### Rules

- Permission codes must match those defined in the backend `sys_permission` table, in the format `resource:action`
- The `v-permission` directive is for UI-layer visibility control only; the backend `@PreAuthorize` is the actual security boundary
- Dynamic routes must wait for `permissionStore.loadMenus()` to complete before registration in the navigation guard
- On logout, `permissionStore.reset()` must be called to clear permission state

### XSS Protection Configuration Management

The XSS protection management page (`views/system/xssconfig/index.vue`) provides a global toggle and blacklist rule CRUD:

- **Global toggle**: `el-switch` component, calls `PUT /api/auth/xss-config/toggle`
- **Rule list**: `el-table` paginated table, supporting create, edit, delete, and per-rule enable/disable
- **Tenant isolation**: API layer extracts `tenant_id` from JWT Token and passes it via the `X-Tenant-Id` request header
- **Rule types**: HTML tags / event handlers / dangerous protocols / custom regex

---

## 13. Business Module Structure Pattern

When adding a new business module, follow these conventions for directory organization, route registration, and i18n configuration.

### Standard Directory Layout

Using "Base Data module → Dictionary management" as an example:

```
src/
├── views/
│   └── base/                  # Module group directory
│       └── dict/
│           └── index.vue      # Feature page (SFC order: script → template → style)
├── api/
│   └── dict.ts               # API functions + TypeScript interfaces (one file per feature domain)
├── stores/
│   └── dict.ts               # (Optional) Only create when complex client-side state is needed
└── locales/
    ├── zh-CN.ts              # Chinese translations
    └── en-US.ts              # English translations
```

### Registration Checklist

Each new business module requires the following registrations:

| Step | File | Action |
|------|------|--------|
| 1 | `src/router/index.ts` → `iconMap` | Add `'<module>:<feature>': '<IconName>'` (e.g., `'base:dict': 'Collection'`) |
| 2 | `src/constants/menu.ts` → `menuI18nMap` | Add i18n key mappings for module directory and menu items |
| 3 | `src/locales/zh-CN.ts` + `en-US.ts` | Add `common.<module>Management` (directory name), `common.<feature>Management` (menu name), and feature-level translation keys |
| 4 | `scripts/sql/seed/auth.sql` → `sys_permission` | Add idempotent DIRECTORY (module group) + MENU (feature page) + BUTTON/API (operation permission) seeds and refresh the seed manifest |
| 5 | `scripts/sql/seed/auth.sql` → `sys_role_permission` | Idempotently assign new permission nodes to SUPER_ADMIN and add natural-key assertions |

### Reference Implementation: Dictionary Management Module

Use `base:dict` (Dictionary Management) as a reference template for new modules:

- **View**: `views/base/dict/index.vue` — master-detail layout (left: type list 10/24 cols, right: data list 14/24 cols)
- **API**: `api/dict.ts` — 11 typed functions (`listDictTypes`, `getDictType`, `createDictType`, `updateDictType`, `deleteDictType`, `toggleDictTypeStatus`, `listDictData`, `createDictData`, `updateDictData`, `deleteDictData`, `refreshDictCache`)
- **Permission codes**: `dict:type:*` (4) + `dict:data:*` (5)
- **UI patterns**: `v-permission` button permission control, `el-switch` status toggle, `el-pagination` pagination, `ElMessageBox.confirm` delete confirmation, `X-Tenant-Id` multi-tenant isolation

---

## 14. Composables Pattern

Composables are the core logic reuse mechanism in the Omni-Stack frontend, replacing the traditional Mixin pattern.

### Design Principles

| Principle | Description |
|-----------|-------------|
| **Single responsibility** | A Composable focuses on one functional domain only |
| **Return value convention** | Returns a `{ state, computed, actions }` object, consumed via destructuring |
| **Reactive** | Uses `ref()` / `reactive()` internally; return values are automatically reactive |
| **Lifecycle-safe** | Manages side effects within `onMounted` / `onUnmounted` |

### Example Composable: useDictOptions

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

**Usage**:

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

### Existing Composables

| Composable | File | Responsibility |
|------------|------|----------------|
| `useDictOptions` | `composables/useDictOptions.ts` | Loads dictionary options, returns a reactive `options` array |
| `useBpmnModeler` | `composables/useBpmnModeler.ts` | Manages bpmn-js Modeler creation, destruction, and import/export |
| `useBpmnExtension` | `composables/useBpmnExtension.ts` | Read/write operations on BPMN extensionElements |

---

## 15. Vite Build Optimization for Docker Deployment

### Multi-Stage Build

The frontend Docker image uses a two-stage build (see `docker/frontend/Dockerfile`):

```dockerfile
# Stage 1: Build
FROM node:22-alpine AS builder
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                      # Deterministic install (locked versions)
COPY omni-frontend/ .
RUN npm run build               # vue-tsc type check + Vite production build

# Stage 2: Serve (Nginx static server)
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
```

### Vite Build Configuration

```typescript
// vite.config.ts
build: {
  target: 'es2020',            // Build target: supports optional chaining, nullish coalescing, etc.
  outDir: 'dist',
  chunkSizeWarningLimit: 2000, // Element Plus is relatively large; raise the threshold accordingly
}
```

### Production Build Optimization Highlights

| Optimization | Description |
|--------------|-------------|
| **Route lazy loading** | All view components use `() => import()` dynamic imports for on-demand loading |
| **Element Plus on-demand** | `@vitejs/plugin-vue` automatically tree-shakes unused components |
| **`import.meta.glob`** | View module scanning is statically analyzed at compile time; unrelated files are not bundled |
| **Chunk splitting** | Vite/Rollup automatically splits vendors (vue, element-plus, axios) into separate chunks |
| **CSS extraction** | Production builds automatically extract CSS into separate files for browser caching |

### Nginx Configuration Highlights

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA History mode: fallback all non-file requests to index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API reverse proxy to Gateway (internal container port)
    location /api/ {
        proxy_pass http://omni-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OAuth2 endpoint proxy
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
    }
}
```

---

## 16. Build & Tooling

| Tool | Purpose | Config |
|------|---------|--------|
| Vite 8 | Dev server + bundler | `vite.config.ts` |
| TypeScript 5.9 | Type checking | `tsconfig.json` (strict mode) |
| ESLint 9 | Linting | `eslint.config.mjs` (flat config) |
| Sass | CSS preprocessing | via `<style lang="scss">` |
| vue-i18n 11 | Internationalization | `src/i18n/index.ts` |
| bpmn-js 18 | BPMN process modeling | `composables/useBpmnModeler.ts` |

### Vite Proxy

The dev server proxies `/api`, `/oauth2`, and `/.well-known` to `http://localhost:8102` (Gateway):

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

### Commands

```bash
npm install        # Install dependencies
npm run dev        # Dev server on :3000
npm run build      # Type check + production build
npm run lint       # ESLint check
npm run preview    # Preview production build
```

---

## 17. Troubleshooting Guide

### Development Environment Issues

| Problem | Possible Cause | How to Investigate |
|---------|---------------|-------------------|
| **Vite startup error `EADDRINUSE`** | Port 3000 is occupied | `lsof -i :3000` or `netstat -ano \| findstr :3000`; kill the process or change `server.port` in `vite.config.ts` |
| **API requests return 404** | Gateway not running or proxy misconfigured | Verify Gateway is running on port 8102; check proxy target in `vite.config.ts` |
| **CORS errors** | Requests bypass the proxy and hit the backend directly | Ensure requests go through the `/api` prefix via the Vite proxy |
| **Missing Element Plus styles** | CSS not imported | Verify `main.ts` includes `import 'element-plus/dist/index.css'` |
| **TypeScript type errors** | Stale `node_modules` cache | `rm -rf node_modules && npm install` |
| **HMR not working** | File system watcher limit | Linux: `echo fs.inotify.max_user_watches=524288 \| sudo tee -a /etc/sysctl.conf` |

### Build Issues

| Problem | Possible Cause | How to Investigate |
|---------|---------------|-------------------|
| **`vue-tsc` type check fails** | Type mismatch | Run `npx vue-tsc --noEmit` to locate the specific file and line number |
| **Chunk size too large** | Dependencies not tree-shaken | Run `npx vite build --mode analyze` to inspect dependency sizes |
| **Docker build `npm ci` fails** | Network issues or `package-lock.json` out of sync | Re-run `npm install` locally to regenerate the lock file, or configure an npm mirror |

### Runtime Issues

| Problem | Possible Cause | How to Investigate |
|---------|---------------|-------------------|
| **Blank screen after login** | Invalid token or menu load failure | Open DevTools → Application → Local Storage to check token; check `/api/auth/menus` response in Network tab |
| **Dynamic menus not displayed** | `permissionStore.loadMenus()` failed | Verify backend `/api/auth/menus` returns correctly; check `menusLoaded` state |
| **v-permission button always hidden** | Permission code mismatch | Compare `permission_code` in `sys_permission` table with the frontend `v-permission` value |
| **OAuth2 callback 404** | Incorrect redirect_uri configuration | Verify that the OAuth2 client's redirect_uri in the Auth service matches the actual callback URL |
| **i18n shows keys instead of translations** | Missing translation keys | Check if `zh-CN.ts` / `en-US.ts` contains the corresponding keys; verify `menuI18nMap` mappings |
| **SPA route refresh returns 404** | Nginx fallback not configured | Ensure Nginx config includes `try_files $uri $uri/ /index.html;` |
