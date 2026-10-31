/**
 * @module navigation/session-sync.test
 * @description WP-06.S03 跨标签会话同步的定向测试（node:test，不依赖浏览器、Vue 运行时组件或 Pinia 插件）。
 *
 * 行为层在 vm 模块图里执行**真实**的 `src/utils/session-sync.ts`，连带真实 `src/utils/jwt.ts`，
 * 换号用例还挂上**真实** `src/stores/workspace.ts`，只把共享存储、事件目标与身份/路由端口换成受控替身，
 * 因此本步判据是跑出来的而不是读字符串读出来的：
 * - 一个标签退出后，另一个标签只走统一清理（先回收、再恢复），一次 `signOut` 加一次重新进入路由；
 * - A→B 换号严格按「清理旧会话 → 写入新身份 → 重新导航」顺序，绝不把 A 的动态路由或偏好留给 B；
 * - 同账号刷新令牌只更新令牌，不清权限也不换页面；本标签原为未登录时采纳新身份不做多余清理；
 * - 重复事件、回声事件与「本标签自己写回共享存储」触发的同批事件都不产生第二次处理，因此不形成回写循环；
 * - 非会话键、来源不是共享存储（含 `sessionStorage`）与存储值已等于内存值的事件一律忽略且不触碰任何端口；
 * - 身份判定用 JWT 的 `tenant_id` 与 `sub`，戳内不含令牌，同账号不同令牌串判为同一身份；
 * - 监听只安装一次，卸载后可再安装；非浏览器环境不安装；
 * - 换号后真实工作台 store 的作用域键即变，收藏与最近访问内存记录同时清空，且不会把 B 的写入落到 A 的键。
 *
 * 结构层只核对无法在 Node 里跑出来的接线：清理必须复用路由层唯一统一入口、监听只在应用入口安装一次、
 * 403 等非登出语义不得走到全局登出、令牌不另存第三份也不写进消息或 URL。
 *
 * 已知范围缺口（见 unfinished-tasks.md 的 WP-06.S03-P1）：真实双标签与新标签首屏的运行态取证需要 `omni-stack`
 * 运行栈，本轮环境缺失，因此这里只证明同步语义与接线，不声称浏览器运行证据或截图。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/session-sync.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import type {
  SessionSnapshot,
  SessionStorageRead,
  SessionSyncAction,
  SessionSyncEnv,
  SessionSyncEventLike,
  SessionSyncPorts,
} from '../../src/utils/session-sync.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const vue = require('vue') as typeof import('vue')
const pinia = require('pinia') as typeof import('pinia')

/** 同步模块导出的可调用面（vm 命名空间解包后的形状）。 */
interface SessionSyncApi {
  SESSION_TOKEN_KEY: string
  SESSION_USERNAME_KEY: string
  SESSION_SYNC_KEYS: readonly string[]
  readStoredSession(storage: SessionStorageRead): SessionSnapshot
  identityStamp(snapshot: SessionSnapshot): string
  decideSessionSync(
    event: SessionSyncEventLike,
    current: SessionSnapshot,
    stored: SessionSnapshot,
    bus: unknown,
  ): SessionSyncAction
  applySessionSync(action: SessionSyncAction, ports: SessionSyncPorts, stored: SessionSnapshot): SessionSyncAction
  browserSessionEnv(): SessionSyncEnv | null
  initSessionSync(ports: SessionSyncPorts, env?: SessionSyncEnv | null): (() => void) | null
  isSessionSyncInstalled(): boolean
}

/** 真实工作台 store 在本步用到的形状。 */
interface WorkspaceStoreView {
  preferenceKey: string | null
  favoriteIds: string[]
  recentIds: string[]
  loadPreferences(): void
  toggleFavorite(id: string): boolean
  recordRecent(id: string): void
}

/** 受控共享存储替身：读、写、删各自留痕，供「同步过程零写入」判据取证。 */
function createBus(seed: Record<string, string> = {}) {
  const values = new Map<string, string>(Object.entries(seed))
  return {
    values,
    reads: [] as string[],
    setItemCalls: [] as string[],
    removeItemCalls: [] as string[],
    getItem(key: string): string | null {
      this.reads.push(key)
      return values.has(key) ? (values.get(key) as string) : null
    },
    setItem(key: string, value: string): void {
      this.setItemCalls.push(key)
      values.set(key, value)
    },
    removeItem(key: string): void {
      this.removeItemCalls.push(key)
      values.delete(key)
    },
    raw: (key: string): string | undefined => values.get(key),
  }
}

/** 事件目标替身：记录安装/卸载，并按浏览器语义同步派发。 */
function createEventTarget() {
  const listeners: ((event: SessionSyncEventLike) => void)[] = []
  return {
    addCalls: [] as string[],
    removeCalls: [] as string[]
    ,
    addEventListener(type: string, listener: (event: SessionSyncEventLike) => void): void {
      this.addCalls.push(type)
      listeners.push(listener)
    },
    removeEventListener(type: string, listener: (event: SessionSyncEventLike) => void): void {
      this.removeCalls.push(type)
      const index = listeners.indexOf(listener)
      if (index >= 0) listeners.splice(index, 1)
    },
    dispatch(event: SessionSyncEventLike): void {
      for (const listener of [...listeners]) listener(event)
    },
    listenerCount: (): number => listeners.length,
  }
}

/** 端口替身与其留痕：`calls` 记录动作顺序，`applied` 记录被采纳的会话。 */
interface PortsSpy {
  ports: SessionSyncPorts
  calls: string[]
  applied: SessionSnapshot[]
  /** 本标签当前会话的只读视图。 */
  memory: SessionSnapshot
}

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

/**
 * 构造只含 ASCII claim 的假 JWT。
 *
 * 签名段是无意义占位串：`decodeJwt` 只解码不验签，这里要的是真实解析路径而不是替身解析。
 */
function makeToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.controlled-signature`
}

/** 租户 1 的用户 A。 */
const TOKEN_A = makeToken({ sub: '101', tenant_id: 1, username: 'alice' })

/** A 的同账号新令牌：claim 相同、签名与 jti 不同，专治「用字符串比较身份」。 */
const TOKEN_A_REFRESHED = makeToken({ sub: '101', tenant_id: 1, username: 'alice', jti: 'second' })

/** 同租户另一用户 B。 */
const TOKEN_B = makeToken({ sub: '202', tenant_id: 1, username: 'bob' })

/** 与 A 同名同用户 ID、但租户不同。 */
const TOKEN_CROSS_TENANT = makeToken({ sub: '101', tenant_id: 2, username: 'alice' })

/** 工作台偏好键前缀：与 `stores/workspace.ts` 的字面量一致。 */
const PREFERENCE_PREFIX = 'omni-workspace-preferences'

/**
 * 在 vm 模块图里执行真实同步模块；`withWorkspace` 时同时执行真实工作台 store。
 *
 * 只替换共享存储、事件目标与身份/权限/接口依赖；同步逻辑与 jwt 解析代码不复制、不改写。
 *
 * @param options seed 为共享存储初始值；env 决定浏览器全局是否可用
 */
async function createHarness(
  options: { seed?: Record<string, string>; env?: 'full' | 'bare'; withWorkspace?: boolean } = {},
) {
  const bus = createBus(options.seed)
  const target = createEventTarget()
  const globals: Record<string, unknown> = {
    Promise,
    setTimeout,
    clearTimeout,
    // 真实 jwt.ts 依赖浏览器全局 atob，这里只提供等价解码，不改写其判定逻辑。
    atob: (value: string) => Buffer.from(value, 'base64').toString('binary'),
  }
  if (options.env !== 'bare') {
    globals.localStorage = bus
    globals.window = target
  }
  const context = vm.createContext(globals)

  /** 向 VM 提供显式依赖替身。 */
  function synthetic(exports: Record<string, unknown>): vm.SyntheticModule {
    return new vm.SyntheticModule(Object.keys(exports), function () {
      for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
    }, { context })
  }

  /** 转译并载入真实源码模块。 */
  function real(relative: string): vm.SourceTextModule {
    const sourceUrl = new URL(relative, import.meta.url)
    const code = ts.transpileModule(readFileSync(sourceUrl, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    }).outputText
    return new vm.SourceTextModule(code, { context, identifier: sourceUrl.pathname })
  }

  /** 统计接口与工单接口在本步不参与判定，只提供可 awaiting 的空响应。 */
  function emptyStats(): Promise<{ data: { code: number; message: string; data: unknown } }> {
    return Promise.resolve({ data: { code: 200, message: 'success', data: null } })
  }

  /** 身份 store 替身：字段与持久化动作都对齐真实 `stores/user.ts`。 */
  const user = vue.reactive({
    token: bus.raw('token') ?? '',
    username: bus.raw('username') ?? '',
    isLoggingOut: false,
    logoutCalls: 0,
    /** 打开后每次落盘都同步派发同名事件，模拟「本标签写回后立刻收到同源事件」的环境。 */
    echoWrites: false,
    write(key: string, value: string | null): void {
      if (value === null) bus.removeItem(key)
      else bus.setItem(key, value)
      if (user.echoWrites) target.dispatch({ key, newValue: value, storageArea: bus })
    },
    setToken(value: string): void {
      user.isLoggingOut = false
      user.token = value
      user.write('token', value)
    },
    setUsername(value: string): void {
      user.username = value
      user.write('username', value)
    },
    logout(explicit = true): void {
      user.logoutCalls += 1
      user.isLoggingOut = explicit
      user.token = ''
      user.username = ''
      user.write('token', null)
      user.write('username', null)
    },
  })
  const permission = vue.reactive({ permissions: ['workflow'], resetCalls: 0 })

  const modules: Record<string, vm.Module> = {
    vue: synthetic({ ...vue }),
    pinia: synthetic({ ...pinia }),
    axios: synthetic({ isAxiosError: (value: unknown): boolean => value instanceof Error && value.name === 'AxiosError' }),
    '@/i18n': synthetic({ translate: (key: string) => key }),
    '@/stores/user': synthetic({ useUserStore: () => user }),
    '@/stores/permission': synthetic({
      usePermissionStore: () => ({
        permissions: permission.permissions,
        reset(): void {
          permission.resetCalls += 1
          permission.permissions = []
        },
      }),
    }),
    '@/api/myJob': synthetic({ getMyJobStats: () => emptyStats() }),
    '@/api/workflow': synthetic({ getWorkspaceStats: () => emptyStats() }),
    '@/utils/jwt': real('../../src/utils/jwt.ts'),
    '@/utils/error-display': real('../../src/utils/error-display.ts'),
    '@/utils/errors': real('../../src/utils/errors.ts'),
    '@/utils/session-sync': real('../../src/utils/session-sync.ts'),
  }
  if (options.withWorkspace) modules['@/stores/workspace'] = real('../../src/stores/workspace.ts')

  /** 链入并求值一个真实模块，返回其命名空间。 */
  async function evaluate(specifier: string): Promise<Record<string, unknown>> {
    const module = modules[specifier] as vm.SourceTextModule
    await module.link((dependency) => {
      const resolved = modules[dependency]
      assert.ok(resolved, `未配置依赖替身: ${dependency}`)
      return resolved
    })
    await module.evaluate()
    return module.namespace as unknown as Record<string, unknown>
  }

  const syncNamespace = (await evaluate('@/utils/session-sync')) as unknown as SessionSyncApi
  let workspace: WorkspaceStoreView | null = null
  if (options.withWorkspace) {
    await evaluate('@/stores/workspace')
    pinia.setActivePinia(pinia.createPinia())
    const namespace = modules['@/stores/workspace'].namespace as unknown as {
      useWorkspaceStore: () => WorkspaceStoreView
    }
    workspace = namespace.useWorkspaceStore()
  }

  /** 按 `main.ts` 的接线造端口：读写都走替身 store 的既有 setter / 统一清理，`memory` 只是只读视图。 */
  function spy(echoWrites = false): PortsSpy {
    user.echoWrites = echoWrites
    const calls: string[] = []
    const applied: SessionSnapshot[] = []
    const memory: SessionSnapshot = {
      get token() {
        return String(user.token)
      },
      get username() {
        return String(user.username)
      },
    }
    const ports: SessionSyncPorts = {
      local: () => ({ token: memory.token, username: memory.username }),
      apply: (next) => {
        calls.push('apply')
        applied.push(next)
        user.setToken(next.token)
        if (next.username !== '') user.setUsername(next.username)
      },
      signOut: () => {
        calls.push('signOut')
        // main.ts 复用的是路由层统一入口：登出 + 权限快照失效 + 动态路由回收。
        user.logout(false)
        permission.resetCalls += 1
        permission.permissions = []
      },
      rerun: () => {
        calls.push('rerun')
        return Promise.resolve()
      },
    }
    return { ports, calls, applied, memory }
  }

  /** 写入共享存储并派发一次跨标签事件，模拟另一个标签的动作。 */
  function otherTabWrites(next: Record<string, string>): void {
    for (const [key, value] of Object.entries(next)) {
      if (value === '') bus.values.delete(key)
      else bus.values.set(key, value)
    }
    target.dispatch({ key: 'token', newValue: next.token ?? null, storageArea: bus })
  }

  /** 把本标签内存换成某身份（等价于该标签自己登录完成）。 */
  function become(snapshot: SessionSnapshot): void {
    user.token = snapshot.token
    user.username = snapshot.username
  }

  return { bus, target, user, permission, sync: syncNamespace, workspace, spy, otherTabWrites, become }
}

type Harness = Awaited<typeof createHarness>

/** 身份 A 的会话快照。 */
const SESSION_A: SessionSnapshot = { token: TOKEN_A, username: 'alice' }

/** 身份 B 的会话快照。 */
const SESSION_B: SessionSnapshot = { token: TOKEN_B, username: 'bob' }

/** 已登出的会话快照。 */
const SESSION_NONE: SessionSnapshot = { token: '', username: '' }

/** 取指向共享存储的事件。 */
function busEvent(h: Harness, key = 'token'): SessionSyncEventLike {
  return { key, newValue: h.bus.raw(key) ?? null, storageArea: h.bus }
}

/** 把端口动作序列压成字符串，便于断言顺序。 */
function sequence(spy: PortsSpy): string {
  return spy.calls.join('>')
}

/** 断言 vm 里构造的快照等于给定身份（跨 realm 对象不能整体 deepEqual，只能逐字段比）。 */
function isSession(snapshot: SessionSnapshot | undefined, expected: SessionSnapshot): boolean {
  return snapshot !== undefined && snapshot.token === expected.token && snapshot.username === expected.username
}

test('一个标签退出：另一标签只走一次统一清理并重新进入路由', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.otherTabWrites({ token: '', username: '' })

  const stored = h.sync.readStoredSession(h.bus)
  assert.ok(isSession(stored, SESSION_NONE))
  const action = h.sync.decideSessionSync(busEvent(h), spy.memory, stored, h.bus)
  assert.equal(action, 'signed_out')
  assert.equal(h.sync.applySessionSync(action, spy.ports, stored), 'signed_out')
  assert.equal(sequence(spy), 'signOut>rerun', '退出只应清理后重新导航，不得再写入任何身份')
  assert.deepEqual(spy.applied, [], '退出不得写入新身份')
  assert.deepEqual(h.bus.setItemCalls, [], '退出不得由本模块写入任何身份键')
  assert.deepEqual(h.bus.removeItemCalls, ['token', 'username'], '删键只由统一清理入口的 store 登出完成')
  assert.equal(h.user.logoutCalls, 1, '清理只走一次统一登出')
  assert.equal(h.permission.resetCalls, 1, '权限快照随统一清理同时失效')
})

test('`storage.clear()` 这类 key 为 null 的事件仍按退出处理', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.bus.values.clear()

  const action = h.sync.decideSessionSync(
    { key: null, newValue: null, storageArea: h.bus },
    spy.memory,
    h.sync.readStoredSession(h.bus),
    h.bus,
  )
  assert.equal(action, 'signed_out', '键缺失只表示整块存储被清，不得当作可忽略的无关变更')
})

test('A→B 换号先回收旧会话再写入新身份，最后重新导航', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.otherTabWrites({ token: TOKEN_B, username: 'bob' })

  const stored = h.sync.readStoredSession(h.bus)
  assert.ok(isSession(stored, SESSION_B))
  const action = h.sync.decideSessionSync(busEvent(h), spy.memory, stored, h.bus)
  assert.equal(action, 'identity_switched')
  h.sync.applySessionSync(action, spy.ports, stored)
  assert.equal(sequence(spy), 'signOut>apply>rerun', '顺序必须是清理→写入→重新导航')
  assert.equal(spy.applied.length, 1)
  assert.ok(isSession(spy.applied[0], SESSION_B), '写入的只能是共享存储里的新身份')
  assert.equal(spy.memory.token, TOKEN_B)
})

test('换号时先清理再采纳，共享存储最终仍是新标签的会话', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.otherTabWrites({ token: TOKEN_B, username: 'bob' })

  h.sync.applySessionSync('identity_switched', spy.ports, h.sync.readStoredSession(h.bus))
  assert.equal(h.bus.raw('token'), TOKEN_B, '清理不得把另一个标签刚写入的会话抹掉')
  assert.equal(h.bus.raw('username'), 'bob')
})

test('同账号刷新令牌只换令牌，不清权限也不换页面', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.otherTabWrites({ token: TOKEN_A_REFRESHED, username: 'alice' })

  const stored = h.sync.readStoredSession(h.bus)
  const action = h.sync.decideSessionSync(busEvent(h), spy.memory, stored, h.bus)
  assert.equal(action, 'token_refreshed', '同一 tenant_id 与 sub 的刷新不得误判成换号')
  h.sync.applySessionSync(action, spy.ports, stored)
  assert.equal(sequence(spy), 'apply', '刷新不得触发清理或重新导航')
})

test('本标签原本未登录：采纳新身份不做多余清理', async () => {
  const h = await createHarness()
  const spy = h.spy()
  h.become(SESSION_NONE)
  h.otherTabWrites({ token: TOKEN_B, username: 'bob' })

  const stored = h.sync.readStoredSession(h.bus)
  const action = h.sync.decideSessionSync(busEvent(h), spy.memory, stored, h.bus)
  assert.equal(action, 'identity_adopted')
  h.sync.applySessionSync(action, spy.ports, stored)
  assert.equal(sequence(spy), 'apply>rerun', '没有旧会话时不得凭空调用清理')
})

test('无关键、非共享存储来源与值未变的事件全部忽略且不触碰端口', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  const sessionStorageLike = { getItem: () => TOKEN_B }
  const cases: [string, SessionSyncEventLike, SessionSnapshot][] = [
    ['主题键', { key: 'theme', newValue: 'dark', storageArea: h.bus }, SESSION_B],
    ['语言键', { key: 'language', newValue: 'ja-JP', storageArea: h.bus }, SESSION_B],
    ['会话存储事件', { key: 'token', newValue: TOKEN_B, storageArea: sessionStorageLike }, SESSION_B],
    ['来源缺失', { key: 'token', newValue: TOKEN_B }, SESSION_B],
    ['回声事件', { key: 'token', newValue: TOKEN_A, storageArea: h.bus }, SESSION_A],
    ['重复投递', { key: 'username', newValue: 'alice', storageArea: h.bus }, SESSION_A],
  ]
  for (const [name, event, stored] of cases) {
    const action = h.sync.decideSessionSync(event, spy.memory, stored, h.bus)
    assert.equal(action, 'ignored', `${name} 应当忽略`)
    assert.equal(h.sync.applySessionSync(action, spy.ports, stored), 'ignored')
  }
  assert.deepEqual(spy.calls, [], '忽略的事件不得触碰任何端口')
  assert.deepEqual(h.bus.setItemCalls, [], '忽略的事件不得写盘')
  assert.deepEqual(h.bus.removeItemCalls, [], '忽略的事件不得删盘')
})

test('身份戳只由 tenant_id 与 sub 决定，且不含令牌', async () => {
  const h = await createHarness()
  assert.equal(
    h.sync.identityStamp({ token: TOKEN_A_REFRESHED, username: 'alice' }),
    h.sync.identityStamp(SESSION_A),
    '同账号刷新后身份戳不变',
  )
  assert.notEqual(h.sync.identityStamp(SESSION_B), h.sync.identityStamp(SESSION_A))
  assert.notEqual(
    h.sync.identityStamp({ token: TOKEN_CROSS_TENANT, username: 'alice' }),
    h.sync.identityStamp(SESSION_A),
    '同名同用户 ID 跨租户不得判为同一身份',
  )
  assert.equal(h.sync.identityStamp(SESSION_NONE), 'anonymous')
  assert.equal(h.sync.identityStamp({ token: 'opaque-token', username: 'carol' }), 'name:carol')
  assert.equal(h.sync.identityStamp({ token: 'opaque-token', username: '' }), 'unknown')
  const stamp = h.sync.identityStamp(SESSION_A)
  assert.ok(!stamp.includes('controlled-signature') && !stamp.includes(TOKEN_A.slice(0, 12)), '戳内不得含令牌')
})

test('监听只安装一次：重复初始化被拒绝，卸载后可再装', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  const dispose = h.sync.initSessionSync(spy.ports)
  assert.equal(typeof dispose, 'function')
  assert.deepEqual(h.target.addCalls, ['storage'], '浏览器全局可用时默认装在 window 的 storage 事件上')
  assert.equal(h.sync.isSessionSyncInstalled(), true)
  assert.equal(h.sync.initSessionSync(spy.ports), null, '第二次初始化不得再装一份监听')
  assert.equal(h.target.addCalls.length, 1)

  dispose?.()
  assert.equal(h.sync.isSessionSyncInstalled(), false)
  assert.deepEqual(h.target.removeCalls, ['storage'])
  assert.equal(h.target.listenerCount(), 0)
  assert.equal(typeof h.sync.initSessionSync(spy.ports), 'function', '卸载后应允许重新安装')
})

test('非浏览器环境不安装监听也不报错', async () => {
  const h = await createHarness({ env: 'bare' })
  const spy = h.spy()
  assert.equal(h.sync.browserSessionEnv(), null)
  assert.equal(h.sync.initSessionSync(spy.ports), null)
  assert.equal(h.sync.isSessionSyncInstalled(), false)
  assert.deepEqual(spy.calls, [])
})

test('端到端：一次换号事件只处理一次，本标签写回引发的同批事件不再进循环', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy(true)
  h.become(SESSION_A)
  h.sync.initSessionSync(spy.ports)

  h.otherTabWrites({ token: TOKEN_B, username: 'bob' })
  assert.equal(sequence(spy), 'signOut>apply>rerun', '自我回写造成的同批事件必须被吞掉，不得递归处理')
  assert.equal(spy.memory.token, TOKEN_B)

  h.target.dispatch({ key: 'token', newValue: TOKEN_B, storageArea: h.bus })
  h.target.dispatch({ key: null, newValue: null, storageArea: h.bus })
  assert.equal(sequence(spy), 'signOut>apply>rerun', '重复或回声事件不得产生第二次清理')
})

test('端到端：另一标签退出后本标签回到未登录并只导航一次', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.sync.initSessionSync(spy.ports)

  h.bus.values.clear()
  h.target.dispatch({ key: null, newValue: null, storageArea: h.bus })
  assert.equal(sequence(spy), 'signOut>rerun')
  assert.equal(spy.memory.token, '')

  h.target.dispatch({ key: null, newValue: null, storageArea: h.bus })
  assert.equal(sequence(spy), 'signOut>rerun', '已登出后的重复退出事件不再产生导航')
})

test('端到端：无关键的变更什么都不做', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' } })
  const spy = h.spy()
  h.become(SESSION_A)
  h.sync.initSessionSync(spy.ports)

  h.bus.values.set('theme', 'dark')
  h.target.dispatch({ key: 'theme', newValue: 'dark', storageArea: h.bus })
  assert.deepEqual(spy.calls, [])
})

test('换号后真实工作台 store 的作用域键即变，收藏与最近访问同时失效', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' }, withWorkspace: true })
  const store = h.workspace as WorkspaceStoreView
  assert.equal(store.toggleFavorite('10'), true)
  store.recordRecent('100')
  const keyA = `${PREFERENCE_PREFIX}:1:101`
  assert.equal(store.preferenceKey, keyA)
  assert.equal(h.bus.raw(keyA), '{"version":1,"favorites":["10"],"recents":["100"]}')

  const spy = h.spy()
  h.sync.initSessionSync(spy.ports)
  h.otherTabWrites({ token: TOKEN_B, username: 'bob' })
  await vue.nextTick()

  assert.equal(sequence(spy), 'signOut>apply>rerun')
  assert.equal(store.preferenceKey, `${PREFERENCE_PREFIX}:1:202`, '偏好作用域键必须随新身份改变')
  assert.deepEqual([...store.favoriteIds], [], '上一账号的收藏不得留在内存里')
  assert.deepEqual([...store.recentIds], [], '上一账号的最近访问不得留在内存里')
  assert.equal(h.bus.raw(keyA), '{"version":1,"favorites":["10"],"recents":["100"]}', '新身份不得覆盖旧身份的落盘记录')
  assert.ok(!h.bus.values.has(`${keyA}:token`), '不得出现第三种偏好键')
})

test('另一标签退出后工作台不再保留任何身份记录', async () => {
  const h = await createHarness({ seed: { token: TOKEN_A, username: 'alice' }, withWorkspace: true })
  const store = h.workspace as WorkspaceStoreView
  store.toggleFavorite('10')

  const spy = h.spy()
  h.sync.initSessionSync(spy.ports)
  h.bus.values.clear()
  h.target.dispatch({ key: null, newValue: null, storageArea: h.bus })
  await vue.nextTick()

  assert.equal(store.preferenceKey, null, '身份消失后不得有作用域键')
  assert.deepEqual([...store.favoriteIds], [])
  assert.deepEqual([...store.recentIds], [])
})

/** 递归列出 `src` 下的源码文件绝对路径，用于「安装点只有一处」的全局核对。 */
function sourceFiles(dir: URL, out: string[] = []): string[] {
  const base = dir.href.endsWith('/') ? dir.href : `${dir.href}/`
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const child = new URL(entry.name, base)
    if (entry.isDirectory()) sourceFiles(child, out)
    else if (/\.(ts|vue)$/.test(entry.name)) out.push(fileURLToPath(child))
  }
  return out
}

const mainSource = read('../../src/main.ts')
const syncSource = read('../../src/utils/session-sync.ts')
const routerSource = read('../../src/router/index.ts')
const requestSource = read('../../src/api/request.ts')
const errorDisplaySource = read('../../src/utils/error-display.ts')

test('跨标签会话监听只在应用入口安装一次', () => {
  const installed = [
    ...new Set(
      sourceFiles(new URL('../../src/', import.meta.url))
        .filter((file) => readFileSync(file, 'utf8').includes('initSessionSync'))
        .map((file) => file.replace(/\\/g, '/').replace(/^.*\/src\//, 'src/')),
    ),
  ].sort()
  assert.deepEqual(installed, ['src/main.ts', 'src/utils/session-sync.ts'], '除入口与模块自身外不得再有安装点')
  assert.equal(mainSource.match(/initSessionSync\(/g)?.length, 1, '入口只调用一次')
  assert.ok(
    mainSource.indexOf('app.mount(\'#app\')') < mainSource.indexOf('initSessionSync('),
    '监听在应用初始化之后统一安装',
  )
  assert.equal(mainSource.match(/useUserStore\(\)/g)?.length, 1)
})

test('清理复用路由层唯一统一入口，本模块不自建登出与落点', () => {
  assert.match(mainSource, /signOut: \(\) => clearSessionInMemory\(\)/)
  assert.match(mainSource, /import router, \{ clearSessionInMemory \} from '.\/router'/)
  assert.match(
    routerSource,
    /export function clearAuthenticatedSession\(explicit = true\) \{\s*\n\s*useUserStore\(\)\.logout\(explicit\)\s*\n\s*usePermissionStore\(\)\.reset\(\)\s*\n\s*clearDynamicRoutes\(\)/,
    '主动退出的统一入口仍是身份、权限与动态路由的唯一清理点',
  )
  assert.match(
    routerSource,
    /export function clearSessionInMemory\(\) \{\s*\n\s*useUserStore\(\)\.clearLocalSession\(\)\s*\n\s*usePermissionStore\(\)\.reset\(\)\s*\n\s*clearDynamicRoutes\(\)/,
    '跨标签同步复用同一组清理动作，但不写共享存储',
  )
  assert.doesNotMatch(syncSource, /defineStore|useUserStore|usePermissionStore|clearDynamicRoutes|from '@\/router'/)
  assert.doesNotMatch(syncSource, /localStorage\.setItem|localStorage\.removeItem|sessionStorage\.|indexedDB|document\.cookie/)
  assert.doesNotMatch(syncSource, /JSON\.stringify|fetch\(|axios/)
})

test('跨标签同步只更新本标签内存，不写共享存储形成回声', () => {
  assert.match(mainSource, /apply: \(next\) => userStore\.applyLocalSession\(next\.token, next\.username\)/)
  assert.doesNotMatch(mainSource, /apply:[\s\S]{0,160}userStore\.setToken/)
  assert.doesNotMatch(mainSource, /apply:[\s\S]{0,160}userStore\.setUsername/)
})

test('重新进入使用会真正改变地址的登录落点，复用既有守卫而非同址 replace', () => {
  assert.match(mainSource, /import \{ resolveSessionSyncRedirect \} from '\.\/utils\/navigation'/)
  assert.match(
    mainSource,
    /rerun: \(\) => \{[\s\S]*const target = resolveSessionSyncRedirect\(router\.currentRoute\.value\)[\s\S]*router\.replace\(target\)/,
  )
  assert.doesNotMatch(mainSource, /router\.replace\(current\.fullPath\)/, '同址 replace 会被路由器判为重复导航，守卫不会重跑')
  assert.doesNotMatch(mainSource, /resolveAppLanding|APP_LANDINGS|loadMenus|registerDynamicRoutes/)
  assert.match(routerSource, /if \(!userStore\.token\) \{/)
})

test('令牌不另存第三份也不写进消息或导航参数', () => {
  assert.doesNotMatch(syncSource, /setItem|postMessage|location\.|href|redirect/)
  assert.doesNotMatch(mainSource, /localStorage\.setItem|sessionStorage|query: \{[^}]*token/i)
  assert.match(syncSource, /getTenantIdFromToken\(token\)/)
  assert.match(syncSource, /getUserIdFromToken\(token\)/)
  assert.doesNotMatch(syncSource, /getPermissionsFromToken|getRolesFromToken/, '同步不解析权限与角色')
  assert.doesNotMatch(syncSource, /console\.|JSON\.stringify/, '不把会话内容写进日志')
})

test('403 等非登出语义走不到全局登出，同步也不插手请求层', () => {
  assert.match(requestSource, /function presentError\(display: ErrorDisplay\) \{\s*\n\s*if \(display\.shouldLogout\) \{/)
  assert.match(requestSource, /clearAuthenticatedSession\(false\)/)
  assert.equal(requestSource.match(/clearAuthenticatedSession\(/g)?.length, 1)
  assert.equal(errorDisplaySource.match(/shouldLogout: true/g)?.length, 2, '只有会话过期与未登录允许登出')
  assert.match(
    errorDisplaySource,
    /permissionDenied: \{[\s\S]*?shouldLogout: false,[\s\S]*?preserveInput: true,/,
    '403 不得允许登出',
  )
  assert.doesNotMatch(requestSource, /initSessionSync|addEventListener\('storage'/, '请求层不装第二份监听')
})
