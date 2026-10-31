/**
 * @module utils/session-sync
 * 跨标签会话同步：监听浏览器在同源标签之间广播的 `storage` 事件，把另一标签的退出、换号或令牌刷新
 * 反映到本标签的内存会话，并复用路由层已有的统一清理入口。
 *
 * 会话的唯一事实源仍是 `stores/user.ts` 写入的 `token` / `username` 两个 localStorage 键：
 * 本模块只读取事件涉及的两个键，不另存第三份令牌，也不把令牌写进消息、URL 或日志。
 * 只有事件来源确实就是这块共享存储（`storageArea` 同一对象）时才处理，
 * 因此 `sessionStorage` 不会被当作跨标签总线——它的值本来就出不了单个标签。
 *
 * 处理顺序固定为「先回收旧的、再按新身份恢复」：本标签原有会话时先走统一清理
 * （登出 + 权限快照 + 动态路由；工作台收藏与最近访问的作用域键随身份变化自动作废），
 * 然后写入新身份并重新进入当前路由，让既有路由守卫按新身份重建菜单并给出合法落点——
 * 本模块不自行判定权限，也不复制第二套落点逻辑。
 * 判定为「无需处理」的事件不产生任何写入与导航，因此回声事件与重复事件不会形成回写循环。
 */
import { getTenantIdFromToken, getUserIdFromToken } from '@/utils/jwt'

/** 会话令牌键：与 `stores/user.ts` 使用的键名一致，此处只作为事件过滤与读取依据。 */
export const SESSION_TOKEN_KEY = 'token'

/** 用户名键：与 `stores/user.ts` 使用的键名一致。 */
export const SESSION_USERNAME_KEY = 'username'

/** 参与跨标签同步的键集合：其余键（语言、主题、菜单缓存等）的变更一律忽略。 */
export const SESSION_SYNC_KEYS: readonly string[] = [SESSION_TOKEN_KEY, SESSION_USERNAME_KEY]

/** 一份会话快照：只包含 user store 已有的两个字段，不包含权限、菜单或偏好内容。 */
export interface SessionSnapshot {
  readonly token: string
  readonly username: string
}

/** 同步动作：忽略、本标签跟随退出、身份更换、本标签采纳新身份、同身份令牌刷新。 */
export type SessionSyncAction = 'ignored' | 'signed_out' | 'identity_switched' | 'identity_adopted' | 'token_refreshed'

/** 本模块需要的最小事件形态：与浏览器 `StorageEvent` 的这三个字段同名同义，便于在 Node 内直接构造。 */
export interface SessionSyncEventLike {
  readonly key: string | null
  readonly newValue: string | null
  readonly storageArea?: unknown
}

/** 共享存储的只读视图：同步过程只读取，不写入，因此不会触发新的跨标签事件。 */
export interface SessionStorageRead {
  getItem(key: string): string | null
}

/** 事件目标：浏览器里是 `window`（`storage` 事件派发在 Window 上，而非 Storage 对象）。 */
export interface SessionEventTarget {
  addEventListener(type: 'storage', listener: (event: SessionSyncEventLike) => void): void
  removeEventListener(type: 'storage', listener: (event: SessionSyncEventLike) => void): void
}

/** 运行环境：共享存储与事件目标的组合，测试可整体替换。 */
export interface SessionSyncEnv {
  readonly storage: SessionStorageRead
  readonly events: SessionEventTarget
}

/** 同步执行端口：由调用方提供会话写入、统一清理与重新导航的具体接线。 */
export interface SessionSyncPorts {
  /** 本标签内存里的当前会话快照。 */
  local(): SessionSnapshot
  /** 把存储中的会话写入本标签内存（只走 store 既有 setter）。 */
  apply(next: SessionSnapshot): void
  /** 复用路由层的统一清理入口，不在本模块另建一份登出实现。 */
  signOut(): void
  /** 重新进入当前路由，由既有守卫按新身份重建菜单与合法落点。 */
  rerun(): void | Promise<unknown>
}

/** 只接受字符串值：非字符串与 `null` 一律归一为空串，等价于「该身份不存在」。 */
function text(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

/**
 * 从共享存储读取当前会话快照。
 *
 * @param storage 共享存储的只读视图
 * @returns 只含 `token` 与 `username` 的快照
 */
export function readStoredSession(storage: SessionStorageRead): SessionSnapshot {
  return { token: text(storage.getItem(SESSION_TOKEN_KEY)), username: text(storage.getItem(SESSION_USERNAME_KEY)) }
}

/**
 * 身份戳：用 JWT 的 `tenant_id` 与 `sub` 判定「是不是同一个账号」。
 *
 * 令牌字符串相同或不同都不足以说明身份相同——同账号刷新令牌会变串，
 * 只用字符串比较会把刷新误判成换号并把会话整体清掉。
 * 令牌解不出这两个 claim（例如另一标签刚写入的半截值）时退化为用户名维度，
 * 两者都缺则归为匿名；戳里不含令牌本身，因此不会被写进任何消息或日志。
 *
 * @param snapshot 会话快照
 * @returns 可比较的身份戳
 */
export function identityStamp(snapshot: SessionSnapshot): string {
  const token = text(snapshot.token)
  if (token === '') return 'anonymous'
  const tenantId = getTenantIdFromToken(token)
  const userId = getUserIdFromToken(token)
  if (tenantId !== null && userId !== null) return `t${tenantId}:u${userId}`
  const username = text(snapshot.username)
  return username === '' ? 'unknown' : `name:${username}`
}

/**
 * 判定一个 `storage` 事件应当触发哪种同步动作（纯函数，不发请求也不写存储）。
 *
 * 忽略条件依次为：事件来源不是本应用的共享存储（含 `sessionStorage` 事件）、
 * 变更的键不在会话键集合内、存储值与本标签内存值已经完全一致（回声与重复事件）。
 *
 * @param event 跨标签存储事件
 * @param current 本标签内存中的会话快照
 * @param stored 共享存储里的会话快照
 * @param bus 本应用共享存储对象，用于校验事件来源
 * @returns 应当执行的动作
 */
export function decideSessionSync(
  event: SessionSyncEventLike,
  current: SessionSnapshot,
  stored: SessionSnapshot,
  bus: unknown,
): SessionSyncAction {
  if (event.storageArea !== bus) return 'ignored'
  if (event.key !== null && !SESSION_SYNC_KEYS.includes(event.key)) return 'ignored'
  if (text(stored.token) === text(current.token) && text(stored.username) === text(current.username)) {
    return 'ignored'
  }
  if (text(stored.token) === '') return 'signed_out'
  if (identityStamp(stored) === identityStamp(current)) return 'token_refreshed'
  return text(current.token) === '' ? 'identity_adopted' : 'identity_switched'
}

/**
 * 执行一个同步动作：顺序固定为先回收旧的、再采纳新的、最后重新进入路由。
 *
 * `ignored` 不触碰任何端口；`token_refreshed` 身份未变，因此只更新令牌、不清权限也不换页面；
 * `signed_out` 只清理；换号与采纳新身份都会先清理再写入，避免把上一个账号的动态路由或偏好带进新会话。
 *
 * @param action 判定结果
 * @param ports 会话写入与清理端口
 * @param stored 共享存储里的目标会话快照
 * @returns 实际执行的动作，便于调用方记录
 */
export function applySessionSync(
  action: SessionSyncAction,
  ports: SessionSyncPorts,
  stored: SessionSnapshot,
): SessionSyncAction {
  if (action === 'ignored') return action
  if (action === 'signed_out' || action === 'identity_switched') ports.signOut()
  if (action === 'identity_switched' || action === 'identity_adopted' || action === 'token_refreshed') {
    ports.apply(stored)
  }
  if (action === 'signed_out' || action === 'identity_switched' || action === 'identity_adopted') ports.rerun()
  return action
}

/** 已登记的监听器与其卸载句柄：全局只保留一份，重复初始化直接返回 `null`。 */
let activeListener: { target: SessionEventTarget; listener: (event: SessionSyncEventLike) => void } | null = null

/** 本标签正在应用同步结果：期间到达的事件一律不再处理，避免自我回写。 */
let isApplyingSync = false

/**
 * 取浏览器运行环境；缺少共享存储或事件目标（例如非浏览器环境）时返回 `null`，调用方据此不安装监听。
 *
 * @returns 共享存储与事件目标的组合，或 `null`
 */
export function browserSessionEnv(): SessionSyncEnv | null {
  if (typeof localStorage === 'undefined' || typeof window === 'undefined') return null
  return { storage: localStorage, events: window }
}

/**
 * 在统一初始化处安装跨标签会话监听：只安装一次，返回卸载句柄；已安装或环境缺失时返回 `null`。
 *
 * @param ports 会话写入与清理端口
 * @param env 运行环境，默认取浏览器的 `localStorage` 与 `window`
 * @returns 卸载函数，或 `null` 表示本次未安装
 */
export function initSessionSync(ports: SessionSyncPorts, env: SessionSyncEnv | null = browserSessionEnv()): (() => void) | null {
  if (env === null || activeListener !== null) return null
  const listener = (event: SessionSyncEventLike) => {
    if (isApplyingSync) return
    isApplyingSync = true
    try {
      const stored = readStoredSession(env.storage)
      const action = decideSessionSync(event, ports.local(), stored, env.storage)
      applySessionSync(action, ports, stored)
    } finally {
      isApplyingSync = false
    }
  }
  env.events.addEventListener('storage', listener)
  activeListener = { target: env.events, listener }
  return () => {
    if (activeListener === null) return
    activeListener.target.removeEventListener('storage', activeListener.listener)
    activeListener = null
  }
}

/** 当前是否已安装监听：供初始化接线处与测试确认「仅一次」。 */
export function isSessionSyncInstalled(): boolean {
  return activeListener !== null
}
