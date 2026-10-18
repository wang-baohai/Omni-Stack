# 前端模式与规范

> 本文维护前端的当前组织、共享客户端状态、路由、权限显示、语言及生命周期约束。源码和配置定义实际行为；新增代码约定不表示所有历史代码已被逐行认证。
> API 线格式与错误分类由 [API 契约](api-contract.md)维护；业务不变量见各领域事实/设计。安装、模块接入、操作与恢复统一见 [Wiki](wiki/index.md)、[脚手架开发](guides/scaffold-development.md)和 [Docker 部署](docker-deployment.md)。

---

## 目录

- [1. 技术基线](#1-技术基线)
- [2. 目录结构](#2-目录结构)
- [3. API 层 (src/api/)](#3-api-层-srcapi)
- [4. 状态管理 (src/stores/)](#4-状态管理-srcstores)
- [5. 路由 (src/router/)](#5-路由-srcrouter)
- [6. 组件设计模式](#6-组件设计模式)
- [7. 组件约定](#7-组件约定)
- [8. 命名约定 (TypeScript)](#8-命名约定-typescript)
- [9. 代码格式 (TypeScript / Vue)](#9-代码格式-typescript--vue)
- [10. UI 框架](#10-ui-框架)
- [11. 国际化（i18n）](#11-国际化i18n)
- [12. 权限与访问控制](#12-权限与访问控制)
- [13. 业务模块结构范式](#13-业务模块结构范式)
- [14. Composables 模式](#14-composables-模式)
- [15. 构建与静态服务边界](#15-构建与静态服务边界)
- [16. 构建与工具](#16-构建与工具)
- [17. 故障排查指南](#17-故障排查指南)

---

## 1. 技术基线

前端采用 Vue 3 Composition API、TypeScript、Pinia、Vue Router、Element Plus、vue-i18n 与 Vite。声明范围见 [package.json](../omni-frontend/package.json)，实际解析版本见 [package-lock.json](../omni-frontend/package-lock.json)，不把范围下限当作当前安装版本。

应用使用 `<script setup lang="ts">` 和 Pinia setup store。共享组件由 [plugins/element-plus.ts](../omni-frontend/src/plugins/element-plus.ts)显式注册，应用语言与 Element Plus locale 关联。冷启动、HMR、包体与生产性能须注明测量环境；依赖版本不证明固定速度、体积或全局 UI 验收。

---

## 2. 目录结构

下表是职责与代表入口，不是完整文件树；所有业务模块仍按当前源码和模块目录组织。

| 目录/入口 | 当前职责 |
|---|---|
| [main.ts](../omni-frontend/src/main.ts)、[App.vue](../omni-frontend/src/App.vue) | Pinia、Router、UI、i18n、指令、主题启动；根路由出口与组件语言 |
| `src/api/` | 共享 request.ts；按子域拆分的 user.ts、dict.ts、`crm-*`、`srm-*`、`procurement-*`、`asset-*` 等 |
| `src/stores/` | user.ts、app.ts、permission.ts |
| [router/index.ts](../omni-frontend/src/router/index.ts) | 静态/动态路由、视图/图标映射、会话清理和导航守卫 |
| `src/views/`、`src/components/`、`src/layout/` | 页面、复用组件、管理布局；OAuth 客户端实际在 views/oauth2-client，不在 system/oauth2 |
| `src/composables/` | 字典状态与 BPMN 生命周期；扩展读写也可导出普通函数 |
| `src/utils/`、`src/constants/` | JWT 解码、错误/回跳/访问策略、PKCE 与菜单文案映射；PKCE 用于授权码流程 |
| `src/i18n/`、`src/locales/` | 语言实例和 zh-CN.ts、en-US.ts、ja-JP.ts、ko-KR.ts |
| [types/api.ts](../omni-frontend/src/types/api.ts)、`src/styles/` | 唯一共享响应类型与全局样式 |

---

## 3. API 层 (`src/api/`)

[request.ts](../omni-frontend/src/api/request.ts) 共享 Axios 实例的 baseURL 为 VITE_API_BASE_URL（缺省 /api）、timeout 为15000ms、withCredentials 为true。请求从 userStore.token 设置 Bearer，并由 token 解析 X-Tenant-Id；客户端解析不验证 JWT 签名，可信身份由 Gateway 决定。

成功拦截器返回完整 AxiosResponse，不自动解包业务 data：

| 层次 | 当前示例 |
|---|---|
| HTTP 响应 | `AxiosResponse<ApiResponse<PageResult<SysUser>>>` |
| `response.data` | `R<T>` 对应的 ApiResponse |
| `response.data.data` | 业务 PageResult |

当前 [user.ts](../omni-frontend/src/api/user.ts) 使用 SysUser、listUsers、getUser；领域函数的返回类型由 Axios 泛型推导。共享 ApiResponse/PageResult 仅从 `@/types/api` 导入，不复制第二份；DTO 字段与 null 行为见 API 契约。

错误处理使用 [error-display.ts](../omni-frontend/src/utils/error-display.ts) 与 [errors.ts](../omni-frontend/src/utils/errors.ts) 的分类和本地化描述符。401 不是一律过期：凭据端点保留会话/输入，其他未登录或过期分支才执行重新认证；403 不清会话。重新认证先统一清理状态，再在对话框结束时导航；并发对话框和显式退出有抑制标志。

远端/Axios 技术 message 只供诊断，调用方使用 displayMessage/getErrorMessage 等安全入口。getErrorMessage 对非 Axios 的本地 Error 仍保留原 message，不能据此声称所有本地异常都已翻译。OAuth2 标准 token 请求等使用独立 fetch/协议响应，不套用这份 R 客户端规则。

---

## 4. 状态管理 (`src/stores/`)

三个 setup store 的实际职责为：

| Store | 当前状态与持久化 |
|---|---|
| [user](../omni-frontend/src/stores/user.ts) | token、username 均持久化；isLoggedIn 只判断 token 存在；isLoggingOut 标记显式退出 |
| [app](../omni-frontend/src/stores/app.ts) | sidebarCollapsed、theme；主题键 omni-theme，默认 dark；初次侧栏按768px媒体条件收起，不持久化侧栏 |
| [permission](../omni-frontend/src/stores/permission.ts) | permissions、menuTree、menusLoaded、menuLoadState、menuLoadError；内存状态与并发菜单 Promise |

nickname/avatar 不是 userStore 当前字段，语言由 i18n 的 omni-lang 管理，不属于 appStore。不要把精简 token-only logout 示例当作完整会话回收。

路由层 `clearAuthenticatedSession(explicit)` 依次调用 user.logout、permission.reset 和 clearDynamicRoutes。user.logout 自身只清 token/username；字典缓存另外观察身份变化。appStore 通过专属主题 helper 更新 HTML dark 类，此职责是明确的 DOM 副作用；普通业务 store 不应随意操作页面 DOM。

当前菜单异步请求没有字典缓存的 Token/代次回填守卫；reset 清状态/Promise 引用，不等于已取消发出的请求。字典回归不能证明所有共享状态均有相同隔离能力。

---

## 5. 路由 (`src/router/`)

Router 使用 `createWebHistory(import.meta.env.BASE_URL)`。静态只表示预先声明，并不等于公开；只有 `requiresAuth === false` 明确走公开分支，未设置也按保护分支处理。

| 静态路径 | 当前访问处理 |
|---|---|
| `/`、`/login`、`/register` | 公开；有 token 访问 Login 回 Home |
| `/portal-login`、`/portal-register` | 公开；有 token 访问 PortalLogin 回 supplier-portal |
| `/callback`、`/device`、`/device/verify`、`/consent` | 公开路由；页面/协议仍有各自验证 |
| `/supplier-portal` | 需要 token 与 portalAccess 策略 |
| `/menu-load-error` | 需要 token；提供菜单重试 |
| `/admin`、`/admin/dashboard` | 管理布局及默认子页；受管理身份策略约束 |
| `/admin/:pathMatch(.*)*`、`/:pathMatch(.*)*` | 分别渲染403/404页；前者受保护，后者公开 |

保护路由无 token 时通过 safeAppRedirect 保留站内回跳，并选择 Login/PortalLogin。有 token 时每次 initFromToken；菜单未完成则等待 loadMenus、注册并 replace 重导航，失败进入 MenuLoadError；其余分支补注册，再执行管理/供应商门户/资产本人访问策略。Token 存在与客户端解码不证明签名有效或未过期，服务端请求仍须认证。

动态注册只处理有匹配组件的 MENU，排除 srm:portal: 子项，并递归 children。permissionCode 中冒号换斜线形成 admin 子路径、换连字符形成路由名；`system:user → /admin/system/user`，`base:dict → /admin/base/dict`，不是 /admin/user 或 /admin/dict。MenuNode.path 是权限树物化路径，不是前端 URL。

视图由 `import.meta.glob('../views/**/index.vue')` 延迟加载。当前 viewOverrides 恰有两项：system:oauth2→views/oauth2-client/index.vue、base:operlog→views/monitor/oper-log/index.vue。动态 meta 包含 title/icon/requiresAuth/permissionCode；类型中的元字段可选，不能说所有静态记录都显式填写全部字段。动态名称集合用于去重，退出时按注册名移除。

菜单失败也会设置 menusLoaded=true，必须同时查看 idle/loading/loaded/failed 状态；retryLoadMenus 先重置后重载。具体恢复操作归 Wiki。

---

## 6. 组件设计模式

页面把响应式数据、加载/表单状态、操作函数与模板分开；API/类型由领域模块导入，复用逻辑放 Composables，业务专用组件可与页面共同组织。实际用户页见 [views/system/user](../omni-frontend/src/views/system/user/index.vue)，不维护含缺失导入或省略号的第二份“可运行”页面。

加载状态在 finally 回收；提交先等待表单校验，写操作同时遵守按钮权限与服务端校验。新增交互应区分用户取消与真实失败，避免用宽泛 catch 把失败当作取消；已有处理范围仍以源码为准。

父子组件用 defineProps/defineEmits，跨组件客户端状态用 Pinia，扩展 UI 用 slots。类型推导不替代运行时载荷检查。

---

## 7. 组件约定

新增 Vue SFC 按 `<script setup lang="ts">` → `<template>` → `<style scoped>` 排列。页面通常为 kebab-case 目录的 index.vue；共享组件可为 LoginForm.vue、LanguageSelector.vue 等 UpperCamelCase 文件，不能把页面目录规则强加到所有组件文件。

Props/事件用类型化 defineProps/defineEmits。组件样式按范围组织，全局样式归 src/styles；覆盖第三方样式需有明确作用域。日期时间、金额和共享响应类型遵循 [API 契约](api-contract.md) 与 [AGENTS](../AGENTS.md)，不另建冲突格式。

---

## 8. 命名约定 (TypeScript)

| 对象 | 新增代码约定 |
|---|---|
| 变量/函数 | lowerCamelCase |
| 接口/类型/组件 | UpperCamelCase |
| 页面目录 | kebab-case + index.vue |
| Store/领域模块文件 | 按现有领域入口命名；新文件优先 kebab-case |
| Store/状态 Composable | use 前缀 |
| 常量/CSS 类 | UPPER_SNAKE_CASE / kebab-case |

myJob.ts、systemJob.ts 等历史入口仍存在，命名约定不是已完成批量重命名的声明。纯辅助函数不因位于 composables 目录就必须返回 Ref 或命名为 Hook。新增代码注释按 AGENTS 使用中文。

---

## 9. 代码格式 (TypeScript / Vue)

[eslint.config.mjs](../omni-frontend/eslint.config.mjs) 实际检查两空格、无分号、单引号、多行尾逗号和 SFC block-order；lint 使用 `--max-warnings 0`，配置为 warn 的问题仍会导致该命令失败。Node 脚本的局部 globals/no-console 例外不改变 src 规则。

[tsconfig.json](../omni-frontend/tsconfig.json) 启用 strict、noUnusedLocals/noUnusedParameters、noFallthroughCasesInSwitch、bundler resolution、noEmit；@/* 映射 src/*。这些检查不证明后端权限或所有历史格式与命名约定已完成审计。

---

## 10. UI 框架

[installElementPlus](../omni-frontend/src/plugins/element-plus.ts) 显式注册列出的组件、图标和 loading 指令，由 main 调用；不是把完整图标库全部注册。Globe 是 Connection 的别名。页面的 iconMap 与真实注册集合要配套维护。

main 导入 Element Plus 基础/暗色变量 CSS；initTheme/setTheme 才应用 document.documentElement 的 dark 类。主题默认值与持久化归 appStore，CSS 导入本身不会自动切换主题。表单规则需要随语言更新时采用响应式规则和 t()；具体组件实现以对应页面为准。

---

## 11. 国际化（i18n）

[i18n/index.ts](../omni-frontend/src/i18n/index.ts) 使用 legacy=false，支持 `zh-CN/en-US/ja-JP/ko-KR`，存储键 omni-lang；持久化值无效或缺失时默认 zh-CN，缺词 fallback 为 en-US。非组件上下文通过 translate() 使用同一实例。

[LanguageSelector](../omni-frontend/src/components/LanguageSelector.vue) 同时修改 locale 与 storeLang；后者持久化并设置 HTML lang。[App.vue](../omni-frontend/src/App.vue) 将该 locale 映射到 Element Plus ElConfigProvider，异常映射回 enUS。只写 localStorage 不等于当前响应式语言已切换。

新增文案在四份 locale 中保持相同键结构；菜单键同步 menuI18nMap。API/协议技术标识不翻译，例示某语言源文件时不得把内容误译成另一语言。文件齐全和 fallback 不代替四语语义/界面验证。

动态页面标题由 router 在导航时按菜单映射生成，公开路由使用 docTitle；不保证同页切语言立即重算 document.title。语言 key 一致性、硬编码审计、文档 AI_REVIEW 与真实浏览器覆盖是不同检查。

---

## 12. 权限与访问控制

permissions 按约定来自 JWT scope 数组，roles 单独来自 roles；本地解码不校验签名或 claims 结构。在该数组约定下，hasPermission 做权限字符串精确成员判断，不会自动给 SUPER_ADMIN 或角色名赋予通配权限。

菜单树来自 `GET /api/auth/menus`，MenuNode 的 DIRECTORY/MENU 用于分组/路由；path 为物化路径。客户端可见性不是后端授权证明。v-permission 在 mounted/updated 时设置 display:none 或恢复空 display，不移除 DOM；空绑定也不是拒绝访问的安全措施。

路由准入还使用 [access.ts](../omni-frontend/src/utils/access.ts) 的管理、供应商与资产本人策略。后端认证、功能权限/逐行归属、租户与数据范围承担实际安全边界，前端按钮/路径不能代替它们；具体服务现有限制见领域事实文档。

XSS 配置页、API 与错误/租户约束见 [API 契约](api-contract.md)。前端传 X-Tenant-Id 不等于服务端信任任意输入；Gateway 会从已验证身份覆盖。退出必须走统一会话清理，不能只隐藏菜单。

---

## 13. 业务模块结构范式

页面、API、可选 Store 与四语 locale 按领域组织，覆盖 CRM、SRM、Procurement、Asset 及平台功能。响应类型唯一归 src/types/api.ts，模块不得复制。

router 的 iconMap、constants/menu.ts 的 menuI18nMap、后端 sys_permission/sys_role_permission、按钮指令与端点权限保持一致；种子修改同步 manifest 摘要与断言。接入步骤统一维护在[脚手架开发](guides/scaffold-development.md)，实例参考[字典页面](../omni-frontend/src/views/base/dict/index.vue)与[字典 API](../omni-frontend/src/api/dict.ts)。

---

## 14. Composables 模式

有状态 Composable 按功能域返回有名称的 Ref、ComputedRef 与操作函数；纯辅助函数按其接口返回值。副作用必须有明确归属：组件资源随组件作用域回收，跨组件共享缓存由自己的上下文观察管理。

### 设计原则

| 原则 | 约束 |
| --- | --- |
| 单一职责 | 按功能域组织，不复制共享 ApiResponse/PageResult |
| 响应式返回 | 暴露响应式状态时返回 refs/computed，解构后保持更新；模板按 Vue 规则自动解包 |
| 上下文归属 | 缓存与在途请求必须区分租户和身份上下文 |
| 生命周期 | 组件观察随作用域停止；模块共享观察独立持有，并在上下文替换或 HMR 时停止 |

### 典型 Composable：useDictOptions

```typescript
import { useDictOptions } from '@/composables/useDictOptions'

const { options, loading } = useDictOptions('workflow_category')
```

当前 `useDictOptions(typeCode)` 返回 `{ options, loading }`：options 为 `ComputedRef<DictOption[]>`，loading 为 `Ref<boolean>`。它调用已认证的 `getDictOptions()`，没有公开 `reload` 方法，也不存在示例中的 `listDictDataByType()` 调用。

缓存键为租户 ID 与 typeCode；同租户同 Token 的进行中请求合并。租户变化切换选项视图，Token 变化使旧请求失效；已完成的同租户缓存仍可复用。旧响应仅在请求仍有效且 Token 未变时回填，结束处理也不会删除新的在途记录。

模块拥有独立于消费者的身份观察作用域；最后一个消费者销毁后，退出仍清空缓存并禁止旧响应回填。解码租户值为假值时不发选项请求；该客户端判断不验证 JWT 签名或 claims 结构。共享缓存不因组件卸载自行失效，后台刷新 Redis 也不等于已刷新客户端内存。

模板消费 loading 时直接绑定该 Ref 的自动解包值；脚本中读写使用 `.value`。相关契约与 8 项实际源码/Vue 回归均有源码入口，具体操作步骤归 Wiki。

[dict.ts](../omni-frontend/src/api/dict.ts) / [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) / [dict-options.test.ts](../omni-frontend/tests/base/dict-options.test.ts)

### 现有 Composables

| 源码 | 职责 |
| --- | --- |
| [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) | 租户选项缓存、上下文观察与加载状态 |
| [useBpmnModeler.ts](../omni-frontend/src/composables/useBpmnModeler.ts) | bpmn-js 建模器初始化、导入导出和 onBeforeUnmount 销毁 |
| [useBpmnExtension.ts](../omni-frontend/src/composables/useBpmnExtension.ts) | readAssignment/writeAssignment 等 BPMN 扩展读写函数 |

[Flow 9](core-flows.md)

该缓存隔离是 useDictOptions 的具体实现，不能推广到 permissionStore 的菜单请求。useBpmnModeler 监听容器后初始化、处理导入导出，卸载时移除 keydown 并 destroy；useBpmnExtension 导出普通扩展函数和 moddle 描述符。它们不共享字典请求状态，也不因这8项回归就获得全部异步取消、只读或浏览器生命周期认证。

---

## 15. 构建与静态服务边界

[前端 Dockerfile](../docker/frontend/Dockerfile) 以 Node 22 构建、Nginx 1.28 交付 dist，监听3000。当前 [Nginx](../docker/frontend/nginx.conf) 把 /api/、/oauth2/、/.well-known/ 代理到 omni-gateway:8080，以 Docker DNS 解析，并提供 history 的 index.html 回退。

HTML 配置 no-cache/no-store，assets 为一年 immutable 缓存；声明 add_header 的 location 显式重列安全头。以上是当前配置，不是本轮已启动服务、已验证代理或缓存行为的宣称。部署和持久化操作归 [Docker 部署](docker-deployment.md)与[快速开始](guides/quick-start.md)。

---

## 16. 构建与工具

package.json 定义 build=`vue-tsc -b && vite build`、lint=`eslint . --max-warnings 0`、单元/E2E/截图/语言审计命令。Vite 采用 es2020、dist、750KB警告阈值及 bpmn-vendor/element-plus/vue-vendor 分块；开发代理 `http://localhost:8102` 指 Vite 所在网络，不是容器间地址。

应用、构建、测试和浏览器验证在本机 Docker；精确命令见 [AGENTS](../AGENTS.md)。使用锁文件和 npm ci；源码变化按影响运行 build/lint/相关测试，E2E 另做其 TypeScript 检查。文档修改不把无变化的昂贵验证反复重开，复用须有源摘要/提交等价证据；预设生成物仍需自己的检查。

---

## 17. 故障排查指南

[排错指南](guides/troubleshooting.md)维护启动、网络、构建、登录、菜单与语言故障的操作和恢复；[源码调试](wiki/source-debugging.md)维护调用链和断点。

诊断保留真实 HTTP/R.code、trace 与错误来源，分别核对身份、菜单状态/视图映射、权限和翻译；不能用改默认端口、清共享缓存或关质量门掩盖失败。源码阅读、测试、静态配置、浏览器与真实业务证据须分开记录。
