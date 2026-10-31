/**
 * 站内落点字面量的唯一来源，取值与 route-contract.json 冻结行为表一一对应。
 * 登录成功、守卫降级与门户跳转都从这里取目标，避免同一条落点规则在多处各写一遍字符串。
 */
import type { LocationQuery, RouteLocationRaw } from 'vue-router'

export const APP_LANDINGS = {
  /** 官网首页：官网发起登录且无可用回跳目标时的落点。 */
  site: '/',
  /** 个人工作台：控制台上下文里原目标不可用时的高级落点。 */
  console: '/workspace',
  /** 供应商门户：门户入口与仅有门户身份的账号落点。 */
  portal: '/supplier-portal',
  /** 资产本人自服务页：资产使用人不得进入通用管理首页。 */
  assetSelf: '/admin/asset/asset',
} as const

/** 回跳解析的入口身份，决定原目标不可用时的默认落点。 */
export type RedirectEntry = 'site' | 'console' | 'portal'

/** 菜单加载后按独立字段重新匹配；Router 的对象 path 不解析其内嵌的 query/hash。 */
export function resolveMenuReloadTarget(target: { path: string; query: LocationQuery; hash: string }): RouteLocationRaw {
  return { path: target.path, query: target.query, hash: target.hash, replace: true }
}

/**
 * 认证入口页前缀：登录成功后回跳到这里会立刻被守卫再次送回登录页，构成循环。
 * `/callback`、`/consent`、`/device` 不在此列——OAuth2 与设备码授权请求必须原样回到
 * 自己的协议页完成流程，不能被改写成普通工作台或首页跳转。
 */
const AUTH_ENTRY_PREFIXES = ['/login', '/portal-login', '/register', '/portal-register', '/menu-load-error']

/**
 * 判断取值是否为可安全使用的站内绝对路径：必须以单斜杠开头，拒绝协议相对、反斜线与控制字符。
 *
 * @param value 待判定的路径
 * @return 是否站内可用
 */
function isInSitePath(value: string): boolean {
  if (!value.startsWith('/') || value.startsWith('//')) return false
  if (value.includes('\\')) return false
  return ![...value].some((char) => {
    const code = char.charCodeAt(0)
    return code < 32 || code === 127
  })
}

/**
 * 解码后再判一次，阻止 `%2F%2Fevil.com` 这类编码后才成为协议相对的目标绕过首层检查。
 * 非法百分号转义序列无法解码，一律视为不可用。
 *
 * @param value 待判定的路径
 * @return 解码结果是否仍为站内路径
 */
function isDecodedInSitePath(value: string): boolean {
  if (!value.includes('%')) return true
  try {
    return isInSitePath(decodeURIComponent(value))
  } catch {
    return false
  }
}

/**
 * 判断目标是否落在认证入口页上。要求前缀后紧跟路径边界，`/login-history` 这类真实业务路径不受影响。
 *
 * @param value 待判定的路径
 * @return 是否构成登录循环
 */
function isAuthEntryLoop(value: string): boolean {
  return AUTH_ENTRY_PREFIXES.some((prefix) => {
    if (!value.startsWith(prefix)) return false
    const rest = value.slice(prefix.length)
    return rest === '' || rest.startsWith('/') || rest.startsWith('?') || rest.startsWith('#')
  })
}

/**
 * 校验登录回跳地址，只允许当前应用内的绝对路径，并保留合法 path、query 与 hash。
 *
 * 非字符串（数组、对象、null）、空白、外站、协议相对（含编码后）、反斜线、控制字符、
 * 非法转义序列以及认证入口页一律返回 `undefined`，由调用方改用入口默认落点。
 *
 * @param value 路由 query 中的 redirect 值或守卫手上的原始 fullPath
 * @return 可用的站内路径；无效时返回 undefined
 */
export function safeAppRedirect(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  if (value.trim() === '' || value !== value.trim()) return undefined
  if (!isInSitePath(value) || !isDecodedInSitePath(value)) return undefined
  if (isAuthEntryLoop(value)) return undefined
  return value
}

/** resolveAppLanding 的入口参数。 */
/** 会话同步时可用的最小当前路由形状。 */
export interface SessionSyncRoute {
  fullPath: string
  meta: { requiresAuth?: boolean }
}

/**
 * 跨标签退出/换号后的导航目标。
 *
 * 受保护路由统一回到登录入口并携带已校验回跳：路由地址必然变化，认证守卫会重新执行；
 * 新会话下由守卫决定最终落点。公开路由保持原位，避免把公开页面上的读者强行带走。
 *
 * @param current 当前路由
 * @return 登录目标；公开路由返回 undefined
 */
export function resolveSessionSyncRedirect(current: SessionSyncRoute): { name: 'Login'; query?: { redirect: string } } | undefined {
  if (current.meta.requiresAuth === false) return undefined
  const redirect = safeAppRedirect(current.fullPath)
  return redirect ? { name: 'Login', query: { redirect } } : { name: 'Login' }
}

export interface AppLandingOptions {
  /** 发起登录或降级的入口身份。 */
  entry: RedirectEntry
  /** 控制台入口下是否为仅资产自服务账号，决定资产本人落点。 */
  assetSelfServiceOnly?: boolean
}

/**
 * 按冻结行为表解析应用内落点：已校验的原目标始终优先，只有原目标不可用才回落到入口默认落点。
 * 官网入口回首页、门户入口回供应商门户、控制台入口回个人工作台（资产本人回自己的资产页），
 * 因此不会把所有目标统一丢到工作台。
 *
 * @param value 路由 query 中的 redirect 值或守卫手上的原始 fullPath
 * @param options 入口身份与资产自服务标记
 * @return 可直接交给路由的站内绝对路径
 */
export function resolveAppLanding(value: unknown, options: AppLandingOptions): string {
  const redirect = safeAppRedirect(value)
  if (redirect) return redirect
  if (options.entry === 'portal') return APP_LANDINGS.portal
  if (options.entry === 'console') {
    return options.assetSelfServiceOnly ? APP_LANDINGS.assetSelf : APP_LANDINGS.console
  }
  return APP_LANDINGS.site
}

/** 外部授权往返期间暂存回跳目标的会话存储键；只存已校验站内路径，绝不存令牌或协议参数。 */
export const APP_REDIRECT_STORAGE_KEY = 'omni-app-redirect'

/** 暂存目标所需的最小会话存储能力，浏览器 sessionStorage 即满足，定向测试可注入替身。 */
export interface AppRedirectStorage {
  getItem(key: string): string | null
  setItem(key: string, value: string): void
  removeItem(key: string): void
}

/**
 * 取本次调用使用的会话存储。
 *
 * @param storage 显式注入的存储
 * @return 可用存储；无注入且宿主无 sessionStorage（如 Node 环境）时返回 undefined
 */
function pickStorage(storage?: AppRedirectStorage): AppRedirectStorage | undefined {
  if (storage) return storage
  return typeof sessionStorage === 'undefined' ? undefined : sessionStorage
}

/**
 * 暂存即将离开本站（第三方授权跳转）时的回跳目标。
 *
 * 只有已校验的站内路径才会落盘；目标不可用时清掉上一次留下的值，避免旧目标被后续回调误用。
 *
 * @param value 当前登录页 query 中的 redirect 值
 * @param storage 注入的会话存储，默认使用当前标签页的 sessionStorage
 * @return 实际落盘的站内路径；无可用目标或存储不可用时返回 undefined
 */
export function stageAppRedirect(value: unknown, storage?: AppRedirectStorage): string | undefined {
  const store = pickStorage(storage)
  const redirect = safeAppRedirect(value)
  if (!store || !redirect) {
    store?.removeItem(APP_REDIRECT_STORAGE_KEY)
    return undefined
  }
  store.setItem(APP_REDIRECT_STORAGE_KEY, redirect)
  return redirect
}

/**
 * 一次性取出暂存的回跳目标：读取后立即删除，并对存储内容重新校验。
 *
 * 删除保证同一目标不被二次消费（例如登录成功回跳后刷新页面又跳一次）；再校验保证被篡改的存储值
 * （手工写入外站或认证入口页）不会被当作可信目标交给路由。
 *
 * @param storage 注入的会话存储，默认使用当前标签页的 sessionStorage
 * @return 仍可用的站内路径；无暂存值或值已不可用时返回 undefined
 */
export function consumeAppRedirect(storage?: AppRedirectStorage): string | undefined {
  const store = pickStorage(storage)
  if (!store) return undefined
  const staged = store.getItem(APP_REDIRECT_STORAGE_KEY)
  store.removeItem(APP_REDIRECT_STORAGE_KEY)
  return safeAppRedirect(staged)
}

/**
 * 丢弃暂存的回跳目标：登录失败、取消授权或改走 OAuth 协议流程时调用，不留下错误的旧目标。
 *
 * @param storage 注入的会话存储，默认使用当前标签页的 sessionStorage
 */
export function clearAppRedirect(storage?: AppRedirectStorage): void {
  pickStorage(storage)?.removeItem(APP_REDIRECT_STORAGE_KEY)
}
