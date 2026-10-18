# Frontend Patterns & Conventions

> This document maintains current frontend organization, shared client state, routing, permission visibility, language and lifecycle constraints. Source and configuration determine behavior. Conventions for new code do not certify every historical line.
> The [API contract](api-contract.en.md) owns wire formats and error classification; domain facts/designs own business invariants. The [Wiki](wiki/index.en.md), [scaffold guide](guides/scaffold-development.en.md) and [Docker deployment](docker-deployment.en.md) own installation, onboarding, operations and recovery.

---

## Table of Contents

- [1. Technology Baseline](#1-technology-baseline)
- [2. Directory Structure](#2-directory-structure)
- [3. API Layer (src/api/)](#3-api-layer-srcapi)
- [4. State Management (src/stores/)](#4-state-management-srcstores)
- [5. Router (src/router/)](#5-router-srcrouter)
- [6. Component Design Patterns](#6-component-design-patterns)
- [7. Component Conventions](#7-component-conventions)
- [8. Naming Conventions (TypeScript)](#8-naming-conventions-typescript)
- [9. Code Format (TypeScript / Vue)](#9-code-format-typescript--vue)
- [10. UI Framework](#10-ui-framework)
- [11. Internationalization (i18n)](#11-internationalization-i18n)
- [12. Permission & Access Control](#12-permission--access-control)
- [13. Business Module Structure Pattern](#13-business-module-structure-pattern)
- [14. Composables Pattern](#14-composables-pattern)
- [15. Build and Static Serving Boundaries](#15-build-and-static-serving-boundaries)
- [16. Build & Tooling](#16-build--tooling)
- [17. Troubleshooting Guide](#17-troubleshooting-guide)

---

## 1. Technology Baseline

The frontend uses Vue 3 Composition API, TypeScript, Pinia, Vue Router, Element Plus, vue-i18n and Vite. [package.json](../omni-frontend/package.json) owns declared ranges; [package-lock.json](../omni-frontend/package-lock.json) owns resolved versions. A range's lower bound is not the installed version.

The app uses `<script setup lang="ts">` and Pinia setup stores. [plugins/element-plus.ts](../omni-frontend/src/plugins/element-plus.ts) explicitly registers shared components, with Element Plus locale linked to application language. Cold-start/HMR/bundle/production measurements must identify the environment; versions do not establish fixed performance or global UI acceptance.

---

## 2. Directory Structure

These are responsibilities and representative entry points, not a complete file tree. All business modules remain organized by current source and module definitions.

| Directory/entry | Current responsibility |
|---|---|
| [main.ts](../omni-frontend/src/main.ts), [App.vue](../omni-frontend/src/App.vue) | Pinia/Router/UI/i18n/directive/theme bootstrap; root outlet and component locale |
| `src/api/` | Shared request.ts and subdomain files such as user.ts, dict.ts, `crm-*`, `srm-*`, `procurement-*`, `asset-*` |
| `src/stores/` | user.ts, app.ts, permission.ts |
| [router/index.ts](../omni-frontend/src/router/index.ts) | Static/dynamic routes, view/icon mapping, session cleanup and guards |
| `src/views/`, `src/components/`, `src/layout/` | Pages, shared components and admin layout; OAuth clients live in views/oauth2-client, not system/oauth2 |
| `src/composables/` | Dictionary state and BPMN lifecycle; extension modules may also export plain functions |
| `src/utils/`, `src/constants/` | JWT decoding, errors/redirect/access policy, PKCE and menu labels; PKCE belongs to authorization-code flow |
| `src/i18n/`, `src/locales/` | Language instance and zh-CN.ts, en-US.ts, ja-JP.ts, ko-KR.ts |
| [types/api.ts](../omni-frontend/src/types/api.ts), `src/styles/` | Sole shared response types and global styles |

---

## 3. API Layer (`src/api/`)

The shared Axios client in [request.ts](../omni-frontend/src/api/request.ts) uses VITE_API_BASE_URL (default /api), timeout 15000 ms and withCredentials=true. It obtains Bearer and X-Tenant-Id from userStore.token. Client decoding does not verify JWT signatures; Gateway establishes trusted identity.

Success returns the whole AxiosResponse without unwrapping business data:

| Layer | Current example |
|---|---|
| HTTP response | `AxiosResponse<ApiResponse<PageResult<SysUser>>>` |
| `response.data` | ApiResponse corresponding to `R<T>` |
| `response.data.data` | Business PageResult |

Current [user.ts](../omni-frontend/src/api/user.ts) uses SysUser/listUsers/getUser; Axios generics infer domain function return types. Import shared ApiResponse/PageResult only from `@/types/api`; DTO/null semantics remain in the API contract.

[error-display.ts](../omni-frontend/src/utils/error-display.ts) and [errors.ts](../omni-frontend/src/utils/errors.ts) classify and localize errors. A 401 is not always expiry: credential endpoints preserve session/input, while applicable unauthenticated/expired branches reauthenticate. 403 preserves the session. Reauthentication first clears shared state, then navigates when the dialog ends; flags suppress concurrent dialogs and explicit-logout responses.

Remote/Axios technical messages remain diagnostic; callers use displayMessage/getErrorMessage. For a local non-Axios Error, getErrorMessage retains its message, so not all local exceptions are guaranteed translated. Standard OAuth2 token requests use separate fetch/protocol responses rather than this R-client contract.

---

## 4. State Management (`src/stores/`)

Current setup-store responsibilities:

| Store | State/persistence |
|---|---|
| [user](../omni-frontend/src/stores/user.ts) | Persists token and username; isLoggedIn tests token presence only; isLoggingOut marks explicit logout |
| [app](../omni-frontend/src/stores/app.ts) | sidebarCollapsed/theme; omni-theme defaults dark; initial sidebar uses the 768px media condition and is not persisted |
| [permission](../omni-frontend/src/stores/permission.ts) | permissions/menuTree/menusLoaded/menuLoadState/menuLoadError; in-memory state and shared pending menu Promise |

nickname/avatar are not current userStore fields. i18n owns omni-lang, outside appStore. A token-only logout example is not complete session cleanup.

Router `clearAuthenticatedSession(explicit)` calls user.logout, permission.reset and clearDynamicRoutes in order. user.logout itself clears only token/username; dictionary cache separately observes identity. appStore's theme helper changes the HTML dark class as an explicitly owned DOM side effect; ordinary business stores should not manipulate page DOM freely.

Current menu requests lack the dictionary cache's Token/generation result guard. reset clears state/Promise references, not already-sent requests. Dictionary regressions do not certify equivalent isolation for every shared state module.

---

## 5. Router (`src/router/`)

Router uses `createWebHistory(import.meta.env.BASE_URL)`. Static means predeclared, not public. Only `requiresAuth === false` takes the public branch; an unset value follows the protected branch.

| Static path | Current access behavior |
|---|---|
| `/`, `/login`, `/register` | Public; Login with a token returns Home |
| `/portal-login`, `/portal-register` | Public; PortalLogin with a token returns supplier-portal |
| `/callback`, `/device`, `/device/verify`, `/consent` | Public routes; pages/protocols retain their own checks |
| `/supplier-portal` | Token and portalAccess policy |
| `/menu-load-error` | Token required; menu retry |
| `/admin`, `/admin/dashboard` | Admin layout/default child, subject to management policy |
| `/admin/:pathMatch(.*)*`, `/:pathMatch(.*)*` | 403/404 views respectively; the former protected, the latter public |

Without a token, protected navigation uses safeAppRedirect and selects Login/PortalLogin. With a token, initFromToken runs each time. Unfinished menus are awaited, registered and replace-navigated; failures go to MenuLoadError. Remaining branches ensure registration and apply management/supplier-portal/asset-self-service policy. Token presence and local decoding do not prove validity or expiry; server requests still authenticate.

Dynamic registration handles matching MENU components, excludes srm:portal: children and recurses through children. Colons become slashes in admin child paths and hyphens in route names: `system:user → /admin/system/user`, `base:dict → /admin/base/dict`, not /admin/user or /admin/dict. MenuNode.path is a materialized permission-tree path, not a frontend URL.

`import.meta.glob('../views/**/index.vue')` supplies lazy views. Exactly two viewOverrides exist: system:oauth2→views/oauth2-client/index.vue and base:operlog→views/monitor/oper-log/index.vue. Dynamic meta has title/icon/requiresAuth/permissionCode. Meta types are optional; not every static record explicitly supplies all fields. Registered names prevent duplicates and are removed on logout.

Menu failure also sets menusLoaded=true. Inspect idle/loading/loaded/failed state as well; retryLoadMenus resets then reloads. Operational recovery belongs in the Wiki.

---

## 6. Component Design Patterns

Separate reactive data, loading/form state, actions and templates. Domain modules own API/types, Composables own reusable logic, and business components may be colocated with views. See the actual [user view](../omni-frontend/src/views/system/user/index.vue), rather than maintaining a second “runnable” page with missing imports or ellipses.

Release loading in finally, await validation before submitting, and apply button permissions plus server checks. New actions should distinguish user cancellation from real failure instead of treating every catch as cancellation. Existing handling remains source-defined.

Use defineProps/defineEmits for parent-child communication, Pinia for shared client state and slots for UI extension. Inferred types do not validate runtime payloads.

---

## 7. Component Conventions

New Vue SFCs follow `<script setup lang="ts">` → `<template>` → `<style scoped>`. Pages commonly use kebab-case directories with index.vue. Shared components may use UpperCamelCase filenames such as LoginForm.vue/LanguageSelector.vue; page directory conventions do not dictate every component filename.

Type props/events with defineProps/defineEmits. Scope component styles and keep global styles in src/styles; third-party overrides need a clear scope. Dates, money and shared response types follow the [API contract](api-contract.en.md) and [AGENTS](../AGENTS.md), without a competing format.

---

## 8. Naming Conventions (TypeScript)

| Object | New-code convention |
|---|---|
| Variables/functions | lowerCamelCase |
| Interfaces/types/components | UpperCamelCase |
| Page directories | kebab-case + index.vue |
| Store/domain files | Follow current domain entry points; prefer kebab-case for new files |
| Store/state Composables | use prefix |
| Constants/CSS classes | UPPER_SNAKE_CASE / kebab-case |

Historical myJob.ts/systemJob.ts still exist; conventions do not claim a completed bulk rename. Plain helpers do not require Ref returns or Hook names merely because they live in composables. New code comments use Chinese as required by AGENTS.

---

## 9. Code Format (TypeScript / Vue)

[eslint.config.mjs](../omni-frontend/eslint.config.mjs) checks two-space indentation, no semicolons, single quotes, multiline trailing commas and SFC block-order. lint uses `--max-warnings 0`, so warn-level findings still fail that command. Node-script globals/no-console exceptions do not change src rules.

[tsconfig.json](../omni-frontend/tsconfig.json) enables strict, noUnusedLocals/noUnusedParameters, noFallthroughCasesInSwitch, bundler resolution and noEmit; @/* maps src/*. These checks do not certify backend permissions or every historical naming/style rule.

---

## 10. UI Framework

[installElementPlus](../omni-frontend/src/plugins/element-plus.ts), called by main, registers its explicit component/icon/loading-directive lists, not the entire icon library. Globe aliases Connection. Keep iconMap references consistent with the actual registry.

main imports base/dark-variable CSS; initTheme/setTheme applies the document.documentElement dark class. appStore owns theme defaults/persistence; CSS import alone does not switch themes. Use reactive rules and t() when form validation copy must follow locale changes; each view owns its actual implementation.

---

## 11. Internationalization (i18n)

[i18n/index.ts](../omni-frontend/src/i18n/index.ts) uses legacy=false and `zh-CN/en-US/ja-JP/ko-KR`, stored under omni-lang. Invalid/missing preferences default to zh-CN; missing keys fall back to en-US. Noncomponent contexts use translate() on the same instance.

[LanguageSelector](../omni-frontend/src/components/LanguageSelector.vue) updates locale and calls storeLang, which persists and sets HTML lang. [App.vue](../omni-frontend/src/App.vue) maps locale to Element Plus ElConfigProvider, falling back to enUS for an unknown mapping. Writing localStorage alone does not change reactive language.

New messages keep matching key structure in all four locale files; menu keys also update menuI18nMap. Do not translate API/protocol identifiers or put another language's text in an example labeled as a specific source locale. File presence/fallback does not replace semantic or UI verification.

Router recomputes dynamic titles from menu mappings and public titles from docTitle during navigation. Same-page language switching does not guarantee an immediate document.title update. Key parity, hardcoded-text audit, document AI_REVIEW and actual browser coverage are distinct checks.

---

## 12. Permission & Access Control

The permissions array is expected from JWT scope; roles comes separately from roles. Local decoding validates neither signatures nor the claims schema. With the expected array, hasPermission checks exact string membership and does not automatically grant wildcard access to SUPER_ADMIN or a role name.

`GET /api/auth/menus` supplies the menu tree; DIRECTORY/MENU groups/routes nodes, while path is materialized. Client visibility does not prove backend authorization. v-permission sets display:none or restores empty display in mounted/updated without removing DOM. An empty binding is not a security denial mechanism.

Route access also uses management, supplier and asset-self-service policies in [access.ts](../omni-frontend/src/utils/access.ts). Backend authentication, endpoint permissions/row ownership, tenant and data scope are the security boundary; client paths/buttons do not replace them. Domain facts document existing service limits.

The [API contract](api-contract.en.md) owns XSS configuration endpoints, errors and tenant rules. Sending X-Tenant-Id does not make arbitrary client identity trusted; Gateway overwrites it from validated identity. Logout must clear the whole session, not merely hide menus.

---

## 13. Business Module Structure Pattern

Organize views, APIs, optional stores and four-language locales by domain, covering CRM/SRM/Procurement/Asset and platform features. src/types/api.ts alone owns shared response types.

Keep router iconMap, constants/menu.ts menuI18nMap, backend sys_permission/sys_role_permission, directives and endpoint permissions aligned; seed changes refresh manifest digests/assertions. The [scaffold guide](guides/scaffold-development.en.md) owns onboarding procedures. Current examples are the [dictionary view](../omni-frontend/src/views/base/dict/index.vue) and [API](../omni-frontend/src/api/dict.ts).

---

## 14. Composables Pattern

State-bearing composables return named Ref, ComputedRef and operation functions by functional domain; plain helpers return their declared values. Side effects need an owner: component resources end with their scope, while a shared module cache owns its context observer.

### Design Principles

| Principle | Constraint |
| --- | --- |
| Single responsibility | Organize by functional domain; reuse shared ApiResponse/PageResult |
| Reactive returns | When exposing reactive state, Return refs/computed to preserve updates after destructuring; Vue templates unwrap them |
| Context ownership | Caches and pending requests distinguish tenant and identity context |
| Lifecycle | Component observers stop with their scope; a module observer is owned separately and stops on context replacement or HMR |

### Example Composable: useDictOptions

```typescript
import { useDictOptions } from '@/composables/useDictOptions'

const { options, loading } = useDictOptions('workflow_category')
```

`useDictOptions(typeCode)` returns `{ options, loading }`: options is `ComputedRef<DictOption[]>`, loading is `Ref<boolean>`. It calls authenticated `getDictOptions()`. There is no public `reload` method or `listDictDataByType()` call.

The cache key combines tenant ID and typeCode; requests in progress are shared within the same tenant and Token. A tenant change switches the options view, and a Token change invalidates old requests. Completed cache entries for the same tenant can still be reused. An old response populates the cache only while valid and its Token unchanged; its completion cannot remove a newer pending entry.

The module owns an identity-observer scope independent of consumers. Logout still clears the cache and blocks old responses after the last consumer is destroyed. No options request is sent when the decoded tenant value is falsy; this client check validates neither JWT signatures nor the claims schema. Component unmount does not itself expire shared cache; refreshing backend Redis does not refresh client memory.

Templates bind loading through normal Ref unwrapping; scripts use `.value`. The source links cover this contract and 8 regressions against the actual composable and Vue. Step-by-step operations belong in the Wiki.

[dict.ts](../omni-frontend/src/api/dict.ts) / [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) / [dict-options.test.ts](../omni-frontend/tests/base/dict-options.test.ts)

### Existing Composables

| Source | Responsibility |
| --- | --- |
| [useDictOptions.ts](../omni-frontend/src/composables/useDictOptions.ts) | Tenant option cache, context observer and loading state |
| [useBpmnModeler.ts](../omni-frontend/src/composables/useBpmnModeler.ts) | bpmn-js modeler initialization, import/export and onBeforeUnmount destruction |
| [useBpmnExtension.ts](../omni-frontend/src/composables/useBpmnExtension.ts) | BPMN extension read/write functions such as readAssignment/writeAssignment |

[Flow 9](core-flows.en.md)

That isolation belongs to useDictOptions, not permissionStore menu requests. useBpmnModeler initializes after its container is ready, imports/exports and removes keydown/destroys on unmount. useBpmnExtension exports plain extension functions and moddle descriptors. They do not share dictionary request state, and those 8 regressions do not certify universal async cancellation, readonly behavior or browser lifecycle.

---

## 15. Build and Static Serving Boundaries

The [frontend Dockerfile](../docker/frontend/Dockerfile) builds with Node 22 and serves dist with Nginx 1.28 on 3000. Current [Nginx](../docker/frontend/nginx.conf) proxies /api/, /oauth2/ and /.well-known/ to omni-gateway:8080 using Docker DNS and provides index.html history fallback.

HTML uses no-cache/no-store, assets one-year immutable caching. Locations declaring add_header explicitly repeat security headers. These are configuration facts, not claims that this stage started services or verified proxy/cache behavior. [Docker deployment](docker-deployment.en.md) and [quick start](guides/quick-start.en.md) own operations and persistence.

---

## 16. Build & Tooling

package.json defines build=`vue-tsc -b && vite build`, lint=`eslint . --max-warnings 0`, and unit/E2E/screenshot/language-audit commands. Vite uses es2020, dist, a 750 KB warning threshold and bpmn-vendor/element-plus/vue-vendor chunks. Its development target `http://localhost:8102` means the Vite process network, not container-to-container routing.

Applications, builds, tests and browser validation run in local Docker. [AGENTS](../AGENTS.md) owns exact commands. Use the lockfile/npm ci; source changes require affected build/lint/tests, and E2E changes also require their TypeScript check. Documentation edits do not repeatedly reopen unchanged expensive validation; reuse requires source-digest/commit equivalence. Generated presets still require their own checks.

---

## 17. Troubleshooting Guide

The [troubleshooting guide](guides/troubleshooting.en.md) owns startup/network/build/login/menu/language operations and recovery; [source debugging](wiki/source-debugging.en.md) owns call chains and breakpoints.

Preserve actual HTTP/R.code, trace and error origin; inspect identity, menu state/view mapping, permissions and translation separately. Do not conceal failure by changing default ports, clearing shared caches or disabling gates. Record source inspection, tests, static configuration, browser and real-business evidence distinctly.
