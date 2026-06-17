# Frontend Patterns & Conventions

This document defines how the Omni-Stack frontend is organized internally. All frontend code must follow these patterns.

## Directory Structure

```
omni-frontend/
├── index.html              # Vite entry HTML
├── package.json
├── vite.config.ts          # Vite config (proxy, alias, build)
├── tsconfig.json
├── eslint.config.mjs       # ESLint flat config
└── src/
    ├── main.ts             # App bootstrap (Pinia, Router, Element Plus)
    ├── App.vue             # Root component (<router-view />)
    ├── api/                # API layer (one file per domain)
    │   ├── request.ts      # Shared Axios instance + interceptors
    │   ├── user.ts         # User API functions
    │   └── dict.ts         # Dictionary API (type + data CRUD)
    ├── stores/             # Pinia stores (one per domain)
    │   ├── user.ts         # User auth store
    │   └── app.ts          # App settings store
    ├── router/
    │   └── index.ts        # Route definitions + navigation guard
    ├── views/              # Page components (kebab-case dirs)
    │   ├── login/index.vue
    │   ├── dashboard/index.vue
    │   ├── base/           # Base data module
    │   │   └── dict/index.vue  # Dictionary management (master-detail layout)
    │   ├── device/         # Device authorization pages (OAuth2 Device Code Flow)
    │   │   ├── index.vue   # Device simulator: shows QR code and polls for token
    │   │   └── verify.vue  # Verification page: user approves device authorization
    ├── layout/
    │   └── index.vue       # App shell layout (sidebar, header, content)
    ├── components/         # Shared/reusable UI components
    ├── types/              # Shared TypeScript type definitions
    │   └── api.ts          # ApiResponse<T>, PageResult<T>
    └── styles/
        └── index.scss      # Global styles (reset, layout, theme)
```

## API Layer (`src/api/`)

### Shared Axios Instance

`request.ts` exports a single configured Axios instance:

- `baseURL`: from `VITE_API_BASE_URL` env variable, defaults to `/api`
- `timeout`: 15000ms
- **Request interceptor**: Attaches `Authorization: Bearer <token>` from `useUserStore()`
- **Response interceptor**: Checks `code === 200`, handles errors, redirects on 401

### API Function Pattern

One file per business domain. Function naming conventions:

| Operation | Prefix | Example |
|-----------|--------|---------|
| Get by ID | `getXxx` | `getUserById(id)` |
| List (paginated) | `listXxx` | `listUsers(page, size)` |
| Create | `createXxx` | `createUser(data)` |
| Update | `updateXxx` | `updateUser(id, data)` |
| Delete | `deleteXxx` | `deleteUser(id)` |

All functions must have typed parameters and return types using `ApiResponse<T>`.

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
- **Domain types** (e.g., `UserInfo`): Co-located in the API file where they're used (`src/api/user.ts`)
- **Never duplicate** shared types in other files; always import from `@/types/api`
- Use `import type { ... }` for type-only imports

## State Management (`src/stores/`)

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

- One store per domain: `user.ts`, `app.ts`
- Store file naming: kebab-case
- Store hook naming: `use` prefix (`useUserStore`, `useAppStore`)
- Use Composition API style (`defineStore('id', () => { ... })`)
- Persist with `localStorage`; non-persistent state uses `ref()`
- No direct DOM manipulation in stores

## Router (`src/router/`)

### Route Definitions

```typescript
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),  // lazy load
    meta: { title: 'Login', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: 'Dashboard', icon: 'Odometer', requiresAuth: true },
      },
    ],
  },
]
```

### Rules

- All route components use lazy loading: `() => import('@/views/xxx/index.vue')`
- `meta` fields: `title` (required), `icon` (optional), `requiresAuth` (required)
- Auth-required routes: explicitly set `requiresAuth: true`
- Public routes: explicitly set `requiresAuth: false`
- Navigation guard checks `to.meta.requiresAuth !== false` (defaults to requiring auth)

## Component Conventions

### SFC Element Order

```vue
<script setup lang="ts">
// Script FIRST
</script>

<template>
  <!-- Template SECOND -->
</template>

<style scoped lang="scss">
/* Styles LAST */
</style>
```

### Rules

- Use `<script setup lang="ts">` for all components
- Props: use `defineProps<T>()` with TypeScript generics
- Directory: kebab-case directory + `index.vue` (e.g., `views/login/index.vue`)
- Styles: `<style scoped lang="scss">` per component
- Global styles in `src/styles/`
- No `!important` except when overriding third-party component styles

### Shared Components (`src/components/`)

- General UI components (buttons, modals, tables) go here
- Business-specific components stay co-located with their views
- Component naming: UpperCamelCase for usage (`<UserCard />`), kebab-case directory for files

## Naming Conventions (TypeScript)

| Type | Style | Example |
|------|-------|---------|
| Variable / Function | lowerCamelCase | `getUserById`, `userList` |
| Interface / Type | UpperCamelCase | `ApiResponse`, `UserInfo` |
| Component file | kebab-case dir + `index.vue` | `views/login/index.vue` |
| Store file | kebab-case | `stores/user.ts` |
| Store hook | `use` prefix | `useUserStore` |
| Constant | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| CSS class | kebab-case | `sidebar-menu`, `login-container` |

## Code Format (TypeScript / Vue)

- Indent: 2 spaces
- No semicolons (ASI style)
- Single quotes; double quotes in JSX
- Trailing commas in multi-line structures
- `import type { ... }` for type-only imports
- Use `@` path alias for `src/` imports
- Vue SFC order: `<script setup>` -> `<template>` -> `<style scoped>`

## UI Framework

- **Element Plus** for UI components (forms, tables, cards, menus, etc.)
- **@element-plus/icons-vue** for icons
- Use Element Plus CSS variables for theming (`--el-color-primary`, `--el-bg-color-page`, etc.)
- Form validation: Element Plus `FormRules` with `ref<FormInstance>()` pattern

## Permission & Access Control

### Permission Store (`src/stores/permission.ts`)

`usePermissionStore` 管理权限编码列表和动态菜单树：

```typescript
// 从 JWT Token 初始化权限编码
permissionStore.initFromToken()

// 从后端加载动态菜单
await permissionStore.loadMenus()

// 查询用户是否拥有指定权限
permissionStore.hasPermission('system:user:create')
```

**数据来源**：
- 权限编码列表：JWT Token 的 `authorities` 字段（登录时写入）
- 动态菜单树：调用 `GET /api/auth/menus`（后端已按用户权限过滤）

### Dynamic Menu Routing

登录成功后，路由守卫触发以下流程：

```
JWT 解码 → permissionStore.initFromToken() 提取权限编码
    → permissionStore.loadMenus() 获取后端过滤后的菜单树
    → 遍历菜单树动态添加 Vue Router 路由
    → 侧边栏渲染 permissionStore.menuTree
```

菜单数据结构（`MenuNode`）包含 `path`（前端路由路径）、`permissionCode`（权限编码）、`type`（DIRECTORY/MENU）等字段。

### Button-Level Permission (`v-permission`)

通过 Vue 自定义指令 `v-permission` 控制按钮的显隐：

```vue
<!-- 仅拥有 system:user:create 权限的用户可见 -->
<el-button v-permission="'system:user:create'" type="primary">
  新增
</el-button>

<!-- 编辑和删除按钮 -->
<el-button v-permission="'system:user:update'" size="small">编辑</el-button>
<el-button v-permission="'system:user:delete'" size="small" type="danger">删除</el-button>
```

**实现原理**：
- 指令挂载时从 `usePermissionStore` 查询权限编码
- 无权限时设置 `el.style.display = 'none'`（非 `removeChild`，兼容 Vue 响应式更新）
- 在 `mounted` 和 `updated` 两个钩子中执行检查

**注册方式**（`src/directives/permission.ts`）：

```typescript
// 在 main.ts 中注册
import { setupPermissionDirective } from '@/directives/permission'
setupPermissionDirective(app)
```

### Rules

- 权限编码必须与后端 `sys_permission` 表中定义的一致，格式为 `resource:action`
- `v-permission` 指令仅用于 UI 层显隐控制，后端 `@PreAuthorize` 才是安全边界
- 动态路由必须在导航守卫中等待 `permissionStore.loadMenus()` 完成后注册
- 登出时必须调用 `permissionStore.reset()` 清除权限状态

### XSS 防护配置管理

XSS 防护管理页面（`views/system/xssconfig/index.vue`）提供全局开关和黑名单规则 CRUD：

- **全局开关**：`el-switch` 组件，调用 `PUT /api/auth/xss-config/toggle`
- **规则列表**：`el-table` 分页表格，支持新建、编辑、删除、单条启用/禁用
- **租户隔离**：API 层从 JWT Token 提取 `tenant_id`，通过 `X-Tenant-Id` 请求头传递
- **规则类型**：HTML 标签 / 事件处理器 / 危险协议 / 自定义正则
- **路由注册**：`iconMap` 和 `menuI18nMap` 中添加 `system:xssconfig` 映射
- **i18n**：翻译文件添加 `common.xssConfig`（模块名）和 `xssConfig.*`（字段文本）

## 业务模块结构范式 (Business Module Structure Pattern)

新增业务模块时，按以下约定组织目录、注册路由和配置国际化。

### 标准目录布局

以「基础数据模块 → 字典管理功能」为例：

```
src/
├── views/
│   └── base/                  # 模块分组目录
│       └── dict/
│           └── index.vue      # 功能页面（SFC 顺序: script → template → style）
├── api/
│   └── dict.ts               # API 函数 + TypeScript 接口（一文件一功能域）
├── stores/
│   └── dict.ts               # （可选）仅当需要复杂客户端状态时创建
└── locales/
    ├── zh-CN.ts              # 中文翻译
    └── en-US.ts              # 英文翻译
```

### 约定式路由映射

动态路由系统通过 `permissionCode` 自动映射到视图组件：

```
permissionCode → resolveViewComponent() → 视图路径

base:dict       → replace ":" with "/"  → views/base/dict/index.vue
system:user     → replace ":" with "/"  → views/system/user/index.vue
```

**规则**：
- `resolveViewComponent()` 在 `src/router/index.ts` 中将 `:` 替换为 `/`，拼接为 `../views/<path>/index.vue`
- 路由 `path` 取 `permissionCode` 最后一段（如 `base:dict` → path `dict`，注册为 `/admin/dict`）
- 特殊路径可通过 `viewOverrides` Map 覆盖默认映射

### 注册清单

每个新业务模块需要完成以下注册：

| 步骤 | 文件 | 操作 |
|------|------|------|
| 1 | `src/router/index.ts` → `iconMap` | 添加 `'<module>:<feature>': '<IconName>'`（如 `'base:dict': 'Collection'`） |
| 2 | `src/layout/index.vue` → `menuI18nMap` | 添加模块目录和菜单项的 i18n key 映射 |
| 3 | `src/locales/zh-CN.ts` + `en-US.ts` | 添加 `common.<module>Management`（目录名）、`common.<feature>Management`（菜单名）和功能级翻译 key |
| 4 | `scripts/sql/init-all.sql` → `sys_permission` | 添加 DIRECTORY（模块分组）+ MENU（功能页面）+ BUTTON/API（操作权限）种子数据 |
| 5 | `scripts/sql/init-all.sql` → `sys_role_permission` | 为 SUPER_ADMIN 角色分配新权限节点 |

### 参考实现：字典管理模块

以 `base:dict`（字典管理）作为新模块的参考范本：

- **View**: `views/base/dict/index.vue` — master-detail 布局（左侧类型列表 10/24 cols，右侧数据列表 14/24 cols）
- **API**: `api/dict.ts` — 11 个 typed 函数（`listDictTypes`, `getDictType`, `createDictType`, `updateDictType`, `deleteDictType`, `toggleDictTypeStatus`, `listDictData`, `createDictData`, `updateDictData`, `deleteDictData`, `refreshDictCache`）
- **Permission codes**: `dict:type:*`（4 个）+ `dict:data:*`（5 个）
- **UI patterns**: `v-permission` 按钮权限控制、`el-switch` 状态切换、`el-pagination` 分页、`ElMessageBox.confirm` 删除确认、`X-Tenant-Id` 多租户隔离

## Build & Tooling

| Tool | Purpose | Config |
|------|---------|--------|
| Vite 8 | Dev server + bundler | `vite.config.ts` |
| TypeScript | Type checking | `tsconfig.json` (strict mode) |
| ESLint | Linting | `eslint.config.mjs` (flat config) |
| Sass | CSS preprocessing | via `<style lang="scss">` |

### Vite Proxy

Dev server proxies `/api` to `http://localhost:8102` (Gateway):

```typescript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8102',
      changeOrigin: true,
    },
  },
}
```

### Commands

```bash
npm install        # Install dependencies
npm run dev        # Dev server on :3000
npm run build      # Type-check + production build
npm run lint       # ESLint check
```
