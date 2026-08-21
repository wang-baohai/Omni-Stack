# 前端模式与规范

> 本文档定义了 Omni-Stack 前端的内部组织方式。所有前端代码必须遵循这些模式。  
> 架构概览详见 [architecture.md](architecture.md)。Docker 部署配置详见 [docker-deployment.md](docker-deployment.md)。

---

## 目录

- [1. 技术选型思考](#1-技术选型思考)
- [2. 目录结构](#2-目录结构)
- [3. API 层](#3-api-层-srcapi)
- [4. 状态管理](#4-状态管理-srcstores)
- [5. 路由](#5-路由-srcrouter)
- [6. 组件设计模式](#6-组件设计模式)
- [7. 组件约定](#7-组件约定)
- [8. 命名约定](#8-命名约定-typescript)
- [9. 代码格式](#9-代码格式-typescript--vue)
- [10. UI 框架](#10-ui-框架)
- [11. 国际化（i18n）](#11-国际化i18n)
- [12. 权限与访问控制](#12-权限与访问控制)
- [13. 业务模块结构范式](#13-业务模块结构范式)
- [14. Composables 模式](#14-composables-模式)
- [15. Docker 部署下的 Vite 构建优化](#15-docker-部署下的-vite-构建优化)
- [16. 构建与工具](#16-构建与工具)
- [17. 故障排查指南](#17-故障排查指南)

---

## 1. 技术选型思考

### 为什么选择 Vue 3.5 + Composition API

| 考量 | 理由 |
|------|------|
| **TypeScript 原生支持** | Vue 3.5 对 `<script setup lang="ts">` 提供一流的类型推导，`defineProps<T>()` 无需运行时声明 |
| **Composition API** | 逻辑复用通过 Composables 实现，替代 Mixin 的命名冲突和来源不透明问题 |
| **Tree-shaking 友好** | 按需导入 API（`ref`、`computed`、`watch`），未使用的功能不会打包 |
| **性能** | Proxy-based 响应式系统比 Vue 2 的 `Object.defineProperty` 更快，且支持 `Map`/`Set` 等集合类型 |
| **Teleport / Suspense** | 内置 `<Teleport>` 和 `<Suspense>` 组件，简化模态框和异步组件的处理 |

### 为什么选择 Vite 8

| 考量 | 理由 |
|------|------|
| **原生 ESM** | 开发服务器基于浏览器原生 ES Module，无需打包即可启动，冷启动速度从 Webpack 的 30s+ 降至 < 2s |
| **HMR 速度** | 模块级 HMR，修改单文件仅更新该模块而非整个 chunk，热更新延迟 < 50ms |
| **Rollup 兼容** | 生产构建基于 Rollup，成熟的 tree-shaking 和 chunk splitting |
| **插件生态** | `@vitejs/plugin-vue` 6.x 提供 SFC 编译、HMR 集成 |

### 为什么选择 Pinia 3 而非 Vuex

| 考量 | 理由 |
|------|------|
| **官方推荐** | Vue 官方从 Vue 3 起推荐 Pinia 替代 Vuex，Vuex 已进入维护模式 |
| **Composition API 风格** | `defineStore('id', () => { ... })` 与 Vue 3 的 `<script setup>` 风格一致 |
| **TypeScript 支持** | 完整的类型推导，无需 `@Mutation` / `@Action` 装饰器 |
| **无 Mutation** | 只有 State + Getters + Actions，简化数据流（直接修改 state 或通过 action） |
| **轻量** | 核心包 < 1KB gzipped，无 boilerplate 代码 |
| **DevTools** | 集成 Vue DevTools，支持时间旅行调试和状态快照 |

### 为什么选择 Element Plus 2.x

| 考量 | 理由 |
|------|------|
| **Vue 3 原生** | Element UI 的 Vue 3 版本，完整的 Composition API 支持 |
| **企业级组件** | 表格（`el-table`）、表单（`el-form`）、对话框（`el-dialog`）等企业场景组件成熟 |
| **国际化内置** | 支持 `vue-i18n` 集成，组件内置中英文文案 |
| **暗色模式** | 通过 `theme-chalk/dark/css-vars.css` 一键切换暗色主题 |
| **图标库** | `@element-plus/icons-vue` 提供 280+ 矢量图标 |

---

## 2. 目录结构

```
omni-frontend/
├── index.html              # Vite 入口 HTML
├── package.json
├── vite.config.ts          # Vite 配置（代理、别名、构建）
├── tsconfig.json
├── eslint.config.mjs       # ESLint flat config
└── src/
    ├── main.ts             # 应用启动（Pinia、Router、Element Plus、I18n）
    ├── App.vue             # 根组件（<router-view />）
    ├── api/                # API 层（一个业务域一个文件）
    │   ├── request.ts      # 共享 Axios 实例 + 拦截器
    │   ├── user.ts         # 用户 API 函数
    │   ├── dict.ts         # 字典 API（类型 + 数据 CRUD）
    │   ├── auth.ts         # 认证 API（登录、注册、验证码、社交登录、OAuth2）
    │   ├── menu.ts         # 菜单 API（动态菜单树）
    │   ├── workflow.ts     # 工作流 API（模型、定义、实例、审批）
    │   └── myJob.ts        # 用户定时任务 API
    ├── stores/             # Pinia 状态管理（一个业务域一个文件）
    │   ├── user.ts         # 用户认证 Store（Token、用户信息）
    │   ├── app.ts          # 应用设置 Store（主题、侧边栏）
    │   └── permission.ts   # 权限 Store（权限编码列表、动态菜单树）
    ├── router/
    │   └── index.ts        # 路由定义 + 导航守卫 + 动态路由注册
    ├── views/              # 页面组件（kebab-case 目录）
    │   ├── home/index.vue          # 首页（未登录落地页 / 已登录工作台）
    │   ├── login/index.vue         # 登录页
    │   ├── register/index.vue      # 注册页
    │   ├── callback/index.vue      # OAuth2 回调页
    │   ├── dashboard/index.vue     # 仪表盘
    │   ├── consent/index.vue       # OAuth2 授权确认页
    │   ├── device/                 # 设备授权（Device Code Flow）
    │   │   ├── index.vue           # 设备模拟器：显示二维码 + 轮询 Token
    │   │   └── verify.vue          # 用户确认授权页
    │   ├── base/                   # 基础数据模块
    │   │   └── dict/index.vue      # 字典管理（master-detail 布局）
    │   ├── system/                 # 系统管理模块
    │   │   ├── user/index.vue      # 用户管理
    │   │   ├── role/index.vue      # 角色管理
    │   │   ├── permission/index.vue # 权限管理
    │   │   ├── org/index.vue       # 组织管理
    │   │   ├── tenant/index.vue    # 租户管理
    │   │   ├── oauth2/index.vue    # OAuth2 客户端管理
    │   │   ├── online/index.vue    # 在线用户监控
    │   │   └── xssconfig/index.vue # XSS 防护配置
    │   ├── workflow/               # 工作流模块
    │   │   ├── model/index.vue     # 流程模型管理 + BPMN 设计器
    │   │   ├── definition/index.vue # 流程定义管理
    │   │   ├── instance/index.vue  # 流程实例管理
    │   │   └── stats/index.vue     # 流程统计
    │   └── monitor/                # 监控模块
    │       └── oper-log/index.vue  # 操作日志
    ├── layout/
    │   └── index.vue       # 应用壳布局（侧边栏、顶栏、内容区）
    ├── components/         # 共享/复用 UI 组件
    │   ├── LoginForm.vue           # 登录表单（密码 + SSO + 第三方 OAuth2）
    │   ├── CronGenerator.vue       # Cron 表达式生成器
    │   ├── DynamicFormRenderer.vue # 动态表单渲染器
    │   └── workflow/               # 工作流专用组件
    │       ├── ModelDesigner.vue   # BPMN 建模设计器
    │       └── panels/             # BPMN 属性面板
    ├── composables/        # Vue Composables（逻辑复用）
    │   ├── useDictOptions.ts       # 字典选项加载
    │   ├── useBpmnModeler.ts       # BPMN Modeler 生命周期管理
    │   └── useBpmnExtension.ts     # BPMN 扩展元素读写
    ├── constants/          # 常量定义
    │   └── menu.ts                 # 菜单 i18n 映射表
    ├── directives/         # Vue 自定义指令
    │   └── permission.ts           # v-permission 权限指令
    ├── utils/              # 工具函数
    │   ├── jwt.ts                  # JWT 解析（提取权限、租户 ID）
    │   └── pkce.ts                 # PKCE 工具（OAuth2 Device Code Flow）
    ├── i18n/
    │   └── index.ts        # vue-i18n 实例配置
    ├── locales/            # 翻译文件
    │   ├── zh-CN.ts        # 中文翻译
    │   └── en-US.ts        # 英文翻译
    ├── types/              # 共享 TypeScript 类型
    │   └── api.ts          # ApiResponse<T>, PageResult<T>
    └── styles/
        └── index.scss      # 全局样式（reset、布局、主题）
```

---

## 3. API 层 (`src/api/`)

### 共享 Axios 实例

`request.ts` 导出单个配置的 Axios 实例：

- **baseURL**：从 `VITE_API_BASE_URL` 环境变量读取，默认 `/api`
- **timeout**：15000ms
- **withCredentials**：`true`（携带 Cookie，支持 OAuth2 Session 管理）
- **请求拦截器**：从 `useUserStore()` 读取 Token，注入 `Authorization: Bearer <token>`
- **响应拦截器**：检查 `code === 200`，业务错误弹出 `ElMessage.error`，401 弹出过期对话框并跳转登录页（携带 redirect 参数）

### API 函数模式

一个业务域一个文件。函数命名约定：

| 操作 | 前缀 | 示例 |
|------|------|------|
| 按 ID 查询 | `getXxx` | `getUserById(id)` |
| 分页列表 | `listXxx` | `listUsers(page, size)` |
| 创建 | `createXxx` | `createUser(data)` |
| 更新 | `updateXxx` | `updateUser(id, data)` |
| 删除 | `deleteXxx` | `deleteUser(id)` |

所有函数必须有类型化参数和返回类型，使用 `ApiResponse<T>`：

```typescript
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/auth/user/list?page=${page}&size=${size}`,
  )
}
```

### 类型系统

- **共享类型**（`ApiResponse<T>`、`PageResult<T>`）：定义在 `src/types/api.ts`，作为唯一真实来源
- **领域类型**（如 `UserInfo`、`SysUser`）：共同定位在使用它们的 API 文件中（`src/api/user.ts`）
- **禁止重复**定义共享类型；始终从 `@/types/api` 导入
- 使用 `import type { ... }` 仅导入类型

---

## 4. 状态管理 (`src/stores/`)

### Pinia Composition API 风格

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

### 规则

- 一个业务域一个 Store：`user.ts`、`app.ts`、`permission.ts`
- Store 文件命名：kebab-case
- Store Hook 命名：`use` 前缀（`useUserStore`、`useAppStore`、`usePermissionStore`）
- 使用 Composition API 风格（`defineStore('id', () => { ... })`）
- 持久化使用 `localStorage`；非持久化状态使用 `ref()`
- Store 中禁止直接操作 DOM

### 三个核心 Store 职责

| Store | 职责 | 持久化 |
|-------|------|--------|
| `useUserStore` | Token 管理、用户基本信息（username、nickname、avatar） | `localStorage`（token） |
| `useAppStore` | 主题切换（light/dark）、侧边栏折叠状态、语言偏好 | `localStorage`（theme、lang） |
| `usePermissionStore` | 权限编码列表（从 JWT 解析）、动态菜单树（从后端加载）、`menusLoaded` 标志 | 无（每次登录重新获取） |

---

## 5. 路由 (`src/router/`)

### 路由架构

Omni-Stack 采用**静态路由 + 动态路由**混合架构：

```
静态路由（公开页面）
├── /            → Home（首页落地页 / 工作台）
├── /login       → Login
├── /register    → Register
├── /callback    → OAuth2Callback
├── /device      → DeviceAuth
├── /device/verify → DeviceVerify
├── /consent     → OAuth2 授权确认
└── /admin       → Layout（管理后台壳）
    └── /admin/dashboard → Dashboard（静态子路由）

动态路由（权限驱动，运行时注册）
└── /admin/<feature> → 对应 views/<module>/<feature>/index.vue
    例如：/admin/user → views/system/user/index.vue
         /admin/dict → views/base/dict/index.vue
```

### 导航守卫流程

```
router.beforeEach()
    ↓
判断 to.meta.requiresAuth
    ├── false → 已登录用户访问 /login → 重定向到 /home
    │           其他公开页面 → 放行
    └── true  → 检查 userStore.token
                ├── 无 Token → 重定向到 /login?redirect=<path>
                └── 有 Token → 检查 permissionStore.menusLoaded
                    ├── false → initFromToken() → loadMenus() → registerDynamicRoutes() → 重新导航
                    └── true  → registerDynamicRoutes()（幂等）→ 放行
```

### 约定式视图组件映射

动态路由通过 `import.meta.glob` 扫描 `views/` 目录下所有 `index.vue`，实现 permissionCode → 视图组件的自动映射：

```typescript
// router/index.ts
const viewModules = import.meta.glob('../views/**/index.vue')

// 映射规则：permissionCode "system:user" → "views/system/user/index.vue"
function resolveViewComponent(permissionCode: string) {
  if (viewOverrides[permissionCode]) return viewModules[viewOverrides[permissionCode]]
  const modulePath = permissionCode.replace(/:/g, '/')
  return viewModules[`../views/${modulePath}/index.vue`]
}
```

**特殊映射覆盖**（`viewOverrides`）：

| permissionCode | 特殊映射路径 | 原因 |
|----------------|------------|------|
| `system:oauth2` | `views/oauth2-client/index.vue` | 避免与 OAuth2 回调页冲突 |
| `base:operlog` | `views/monitor/oper-log/index.vue` | 归类到 monitor 目录 |

### 规则

- 所有路由组件使用懒加载：`() => import('@/views/xxx/index.vue')`
- `meta` 字段：`title`（必需）、`icon`（可选）、`requiresAuth`（必需）、`permissionCode`（动态路由自动设置）
- 需要认证的路由：明确设置 `requiresAuth: true`
- 公开路由：明确设置 `requiresAuth: false`
- 登出时调用 `clearDynamicRoutes()` 清除所有动态路由

---

## 6. 组件设计模式

### 页面组件（Views）

页面组件遵循标准的**数据 + 操作 + UI**三层结构：

```vue
<script setup lang="ts">
// 1. 导入（API、类型、组件、i18n）
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUsers, createUser, type SysUser } from '@/api/user'

const { t } = useI18n()

// 2. 响应式状态
const tableData = ref<SysUser[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

// 3. 表单状态
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const rules: FormRules = { username: [{ required: true, ... }] }

// 4. 业务函数
async function loadData() { loading.value = true; try { ... } finally { loading.value = false } }
function handleCreate() { dialogVisible.value = true }
async function handleSubmit() { await formRef.value?.validate(); ... }
async function handleDelete(id: number) { await ElMessageBox.confirm(...); ... }
</script>
```

### 共享组件（Components）

- 通用 UI 组件放在 `src/components/`
- 业务特定组件与对应 views 共同定位
- 组件使用 UpperCamelCase（`<UserCard />`），文件使用 kebab-case 目录

### 组件通信模式

| 场景 | 模式 | 示例 |
|------|------|------|
| 父子通信 | `defineProps<T>()` + `defineEmits<T>()` | `<LoginForm @login-success="onLogin" />` |
| 跨组件状态 | Pinia Store | `useUserStore().token` |
| 逻辑复用 | Composable | `useDictOptions('dict_type_code')` |
| 插槽扩展 | `<slot>` + named slots | `<template #header>...</template>` |

---

## 7. 组件约定

### SFC 元素顺序

```vue
<script setup lang="ts">
// Script 在前
</script>

<template>
  <!-- Template 在中 -->
</template>

<style scoped lang="scss">
/* Styles 在后 */
</style>
```

### 规则

- 所有组件使用 `<script setup lang="ts">`
- Props：使用 `defineProps<T>()` TypeScript 泛型
- 目录：kebab-case 目录 + `index.vue`（如 `views/login/index.vue`）
- 样式：`<style scoped lang="scss">` 按组件隔离
- 全局样式在 `src/styles/`
- 禁止使用 `!important`，除非覆盖第三方组件样式

---

## 8. 命名约定 (TypeScript)

| 类型 | 风格 | 示例 |
|------|------|------|
| 变量 / 函数 | lowerCamelCase | `getUserById`、`userList` |
| 接口 / 类型 | UpperCamelCase | `ApiResponse`、`UserInfo` |
| 组件文件 | kebab-case 目录 + `index.vue` | `views/login/index.vue` |
| Store 文件 | kebab-case | `stores/user.ts` |
| Store Hook | `use` 前缀 | `useUserStore` |
| Composable | `use` 前缀 | `useDictOptions` |
| 常量 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| CSS 类名 | kebab-case | `sidebar-menu`、`login-container` |

---

## 9. 代码格式 (TypeScript / Vue)

- 缩进：2 空格
- 无分号（ASI 风格）
- 单引号；JSX 中使用双引号
- 多行结构保留尾逗号
- 使用 `import type { ... }` 仅导入类型
- 使用 `@` 路径别名导入 `src/` 下的模块
- Vue SFC 顺序：`<script setup>` → `<template>` → `<style scoped>`

---

## 10. UI 框架

- **Element Plus** 作为 UI 组件库（表单、表格、卡片、菜单等）
- **@element-plus/icons-vue** 提供图标（全局注册在 `main.ts`）
- 使用 Element Plus CSS 变量进行主题定制（`--el-color-primary`、`--el-bg-color-page` 等）
- 表单校验：Element Plus `FormRules` + `ref<FormInstance>()` 模式
- 暗色模式：通过 `theme-chalk/dark/css-vars.css` 自动切换

```typescript
// 表单校验标准模式
const formRef = ref<FormInstance>()
const rules: FormRules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
}

async function handleSubmit() {
  await formRef.value?.validate()  // 校验通过才继续
  // ... 提交逻辑
}
```

---

## 11. 国际化（i18n）

### 配置

基于 `vue-i18n` 11.x，使用 Composition API 模式（`legacy: false`）：

```typescript
// i18n/index.ts
const i18n = createI18n({
  legacy: false,
  locale: getStoredLang(),        // 从 localStorage 读取，默认 'zh-CN'
  fallbackLocale: 'en-US',
  messages: { 'zh-CN': zhCN, 'en-US': enUS },
})
```

### 使用方式

```typescript
// 组件内
const { t, locale } = useI18n()
const label = t('common.dashboard')

// 切换语言
locale.value = 'en-US'
storeLang('en-US')  // 持久化到 localStorage
```

### 翻译文件结构

翻译文件位于 `src/locales/`，按模块组织 key：

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

### 新增模块的 i18n 清单

| 步骤 | 文件 | 操作 |
|------|------|------|
| 1 | `src/locales/zh-CN.ts` | 添加 `common.<feature>Management` 和功能级翻译 key |
| 2 | `src/locales/en-US.ts` | 添加对应英文翻译 |
| 3 | `src/constants/menu.ts` → `menuI18nMap` | 添加 `permissionCode → i18n key` 映射 |

---

## 12. 权限与访问控制

### 权限 Store (`src/stores/permission.ts`)

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
- 权限编码列表：JWT Token 的 `authorities` 字段（登录时写入，通过 `getPermissionsFromToken()` 解析）
- 动态菜单树：调用 `GET /api/auth/menus`（后端已按用户权限过滤）

### 动态菜单路由

登录成功后，路由守卫触发以下流程：

```
JWT 解码 → permissionStore.initFromToken() 提取权限编码
    → permissionStore.loadMenus() 获取后端过滤后的菜单树
    → registerDynamicRoutes() 遍历菜单树添加 Vue Router 路由
    → 侧边栏渲染 permissionStore.menuTree
```

菜单数据结构（`MenuNode`）包含 `path`（前端路由路径）、`permissionCode`（权限编码）、`type`（DIRECTORY/MENU）等字段。

### 按钮级权限控制 (`v-permission`)

通过 Vue 自定义指令 `v-permission` 控制按钮的显隐：

```vue
<!-- 仅拥有 system:user:create 权限的用户可见 -->
<el-button v-permission="'system:user:create'" type="primary">
  新增
</el-button>
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

### 规则

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

---

## 13. 业务模块结构范式

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

### 注册清单

每个新业务模块需要完成以下注册：

| 步骤 | 文件 | 操作 |
|------|------|------|
| 1 | `src/router/index.ts` → `iconMap` | 添加 `'<module>:<feature>': '<IconName>'`（如 `'base:dict': 'Collection'`） |
| 2 | `src/constants/menu.ts` → `menuI18nMap` | 添加模块目录和菜单项的 i18n key 映射 |
| 3 | `src/locales/zh-CN.ts` + `en-US.ts` | 添加 `common.<module>Management`（目录名）、`common.<feature>Management`（菜单名）和功能级翻译 key |
| 4 | `scripts/sql/seed/auth.sql` → `sys_permission` | 添加幂等 DIRECTORY（模块分组）+ MENU（功能页面）+ BUTTON/API（操作权限）种子数据，并刷新 seed manifest |
| 5 | `scripts/sql/seed/auth.sql` → `sys_role_permission` | 幂等地为 SUPER_ADMIN 角色分配新权限节点，并增加自然键断言 |

### 参考实现：字典管理模块

以 `base:dict`（字典管理）作为新模块的参考范本：

- **View**: `views/base/dict/index.vue` — master-detail 布局（左侧类型列表 10/24 cols，右侧数据列表 14/24 cols）
- **API**: `api/dict.ts` — 11 个 typed 函数（`listDictTypes`、`getDictType`、`createDictType`、`updateDictType`、`deleteDictType`、`toggleDictTypeStatus`、`listDictData`、`createDictData`、`updateDictData`、`deleteDictData`、`refreshDictCache`）
- **Permission codes**: `dict:type:*`（4 个）+ `dict:data:*`（5 个）
- **UI patterns**: `v-permission` 按钮权限控制、`el-switch` 状态切换、`el-pagination` 分页、`ElMessageBox.confirm` 删除确认、`X-Tenant-Id` 多租户隔离

---

## 14. Composables 模式

Composables 是 Omni-Stack 前端的逻辑复用核心机制，替代传统的 Mixin 模式。

### 设计原则

| 原则 | 说明 |
|------|------|
| **单一职责** | 一个 Composable 只关注一个功能域 |
| **返回值约定** | 返回 `{ state, computed, actions }` 对象，使用解构消费 |
| **响应式** | 内部使用 `ref()` / `reactive()`，返回值自动响应式 |
| **生命周期安全** | 在 `onMounted` / `onUnmounted` 中管理副作用 |

### 典型 Composable：useDictOptions

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

**使用方式**：

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

### 现有 Composables

| Composable | 文件 | 职责 |
|------------|------|------|
| `useDictOptions` | `composables/useDictOptions.ts` | 加载字典选项，返回响应式 `options` 数组 |
| `useBpmnModeler` | `composables/useBpmnModeler.ts` | 管理 bpmn-js Modeler 的创建、销毁和导入/导出 |
| `useBpmnExtension` | `composables/useBpmnExtension.ts` | BPMN extensionElements 的读写操作 |

---

## 15. Docker 部署下的 Vite 构建优化

### 多阶段构建

前端 Docker 镜像采用两阶段构建（详见 `docker/frontend/Dockerfile`）：

```dockerfile
# 阶段 1：构建
FROM node:22-alpine AS builder
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                      # 确定性安装（锁定版本）
COPY omni-frontend/ .
RUN npm run build               # vue-tsc 类型检查 + Vite 生产构建

# 阶段 2：运行（Nginx 静态服务）
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
```

### Vite 构建配置

```typescript
// vite.config.ts
build: {
  target: 'es2020',            // 构建目标：支持可选链、空值合并等现代语法
  outDir: 'dist',
  chunkSizeWarningLimit: 2000, // Element Plus 体积较大，适当调高阈值
}
```

### 生产构建优化要点

| 优化项 | 说明 |
|--------|------|
| **路由懒加载** | 所有 views 组件使用 `() => import()` 动态导入，实现按需加载 |
| **Element Plus 按需** | 通过 `@vitejs/plugin-vue` 自动 tree-shake 未使用的组件 |
| **`import.meta.glob`** | 视图模块扫描编译时静态分析，不会打包无关文件 |
| **chunk splitting** | Vite/Rollup 自动将 vendor（vue、element-plus、axios）拆分为独立 chunk |
| **CSS 提取** | 生产构建自动提取 CSS 为独立文件，支持浏览器缓存 |

### Nginx 配置要点

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA History 模式：所有非文件请求回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到 Gateway（容器内部端口）
    location /api/ {
        proxy_pass http://omni-gateway:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # OAuth2 端点代理
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
    }
}
```

---

## 16. 构建与工具

| 工具 | 用途 | 配置 |
|------|------|------|
| Vite 8 | 开发服务器 + 打包器 | `vite.config.ts` |
| TypeScript 5.9 | 类型检查 | `tsconfig.json`（strict 模式） |
| ESLint 9 | 代码检查 | `eslint.config.mjs`（flat config） |
| Sass | CSS 预处理 | 通过 `<style lang="scss">` |
| vue-i18n 11 | 国际化 | `src/i18n/index.ts` |
| bpmn-js 18 | BPMN 流程建模 | `composables/useBpmnModeler.ts` |

### Vite 代理

开发服务器代理 `/api`、`/oauth2`、`/.well-known` 到 `http://localhost:8102`（Gateway）：

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

### 命令

```bash
npm install        # 安装依赖
npm run dev        # 开发服务器 :3000
npm run build      # 类型检查 + 生产构建
npm run lint       # ESLint 检查
npm run preview    # 预览生产构建
```

---

## 17. 故障排查指南

### 开发环境问题

| 问题 | 可能原因 | 排查方法 |
|------|---------|---------|
| **Vite 启动报错 `EADDRINUSE`** | 端口 3000 被占用 | `lsof -i :3000` 或 `netstat -ano \| findstr :3000`，终止占用进程或修改 `vite.config.ts` 中的 `server.port` |
| **API 请求 404** | Gateway 未启动或代理配置错误 | 检查 Gateway 是否在 8102 端口运行；检查 `vite.config.ts` 中 proxy target |
| **CORS 错误** | 未通过代理直接请求后端 | 确保请求通过 `/api` 前缀走 Vite 代理 |
| **Element Plus 样式缺失** | 未导入 CSS | 检查 `main.ts` 中是否包含 `import 'element-plus/dist/index.css'` |
| **TypeScript 类型错误** | `node_modules` 缓存过期 | `rm -rf node_modules && npm install` |
| **HMR 不生效** | 文件系统监听器限制 | Linux: `echo fs.inotify.max_user_watches=524288 \| sudo tee -a /etc/sysctl.conf` |

### 构建问题

| 问题 | 可能原因 | 排查方法 |
|------|---------|---------|
| **`vue-tsc` 类型检查失败** | 类型不匹配 | 运行 `npx vue-tsc --noEmit` 定位具体文件和行号 |
| **chunk 体积过大** | 依赖未 tree-shake | 运行 `npx vite build --mode analyze` 分析依赖体积 |
| **Docker 构建 `npm ci` 失败** | 网络问题或 `package-lock.json` 不同步 | 本地重新 `npm install` 生成新 lock 文件，或配置 npm 镜像源 |

### 运行时问题

| 问题 | 可能原因 | 排查方法 |
|------|---------|---------|
| **登录后白屏** | Token 无效或菜单加载失败 | 打开 DevTools → Application → Local Storage 检查 token；Network 标签检查 `/api/auth/menus` 响应 |
| **动态菜单不显示** | `permissionStore.loadMenus()` 失败 | 检查后端 `/api/auth/menus` 是否正常返回；检查 `menusLoaded` 状态 |
| **v-permission 按钮始终隐藏** | 权限编码不匹配 | 比较 `sys_permission` 表中的 `permission_code` 与前端 `v-permission` 值 |
| **OAuth2 回调 404** | redirect_uri 配置错误 | 检查 Auth 服务的 OAuth2 客户端配置中 redirect_uri 是否与实际回调 URL 一致 |
| **i18n 显示 key 而非翻译** | 翻译 key 缺失 | 检查 `zh-CN.ts` / `en-US.ts` 中是否包含对应 key；检查 `menuI18nMap` 映射 |
| **SPA 路由刷新 404** | Nginx 未配置回退 | 确认 Nginx 配置 `try_files $uri $uri/ /index.html;` |
