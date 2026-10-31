/**
 * @module navigation/workspace-regression.test
 * @description WP-05.S05「四语与交互回归」的定向回归测试（node:test，不依赖浏览器）。
 *
 * 本文件把 WP-05 前三步各自证过的片段串成一条端到端的使用故事，在 vm 模块图里执行**真实**的
 * `src/stores/workspace.ts`（连同真实 `utils/jwt.ts`、`utils/errors.ts`、`utils/error-display.ts`、真实 vue 响应式与真实 pinia），
 * 只把两个统计接口、身份 store、权限 store、i18n/axios 与 `localStorage` 换成受控替身，
 * 且统计接口用可脚本化的替身，因此「真实统计状态断言」跑的是 store 自身的六态机，不是模拟结论：
 * - 偏好恢复：同一份磁盘内容另起一个 store 后，收藏与最近访问按存盘顺序原样读回，不重排也不丢项；
 * - 取消与重新收藏：其余项相对顺序不变、重新收藏落到末尾、重复取消幂等，且这些动作一律不触碰最近访问；
 * - 十二项边界与重复项：长度恒为 store 自己导出的 `RECENT_LIMIT`，重复访问只提到最前而不增长；
 * - 身份切换：B 只写自己的键、切回 A 读回 A 的记录，同时流程卡在权限撤销后转 `unavailable` 且不再发请求；
 * - 卡片独立失败：流程卡 403 失败不清空任务卡已加载数据，重试只重发本卡；403、断网、超时、500 的语义与文案互不复用；
 * - 两张通道互不触发：偏好写入一次统计请求都不发，统计失败一字不改偏好记录；
 * - 异常存储与隔离：损坏值只把本身份键降级为空，其它身份键原样保留；全流程只触碰带身份的作用域键；
 * - 落盘内容只有版本号与两个稳定 ID 数组，令牌、用户名、权限码、路径、query、hash 都不入库。
 *
 * 结构层补上「搜索命中」与「四语」两条只能在源码与词条文件上取证的判据：
 * 抽屉的 `searchHits`、`favoriteTargets`、`targetsById` 与概览页的 `visibleEntries` 全部只遍历同一份
 * `useApplicationNavigation()` 派生集合，搜索词只做本地 `trim().toLowerCase()` 比较，命中字段限定在标题、分组与权限编码，
 * 不发起额外请求、不放大可见集合；最近访问的唯一写入方仍是路由末尾的访问成功钩子。
 * 四语面直接从真实组件源码里提取所有字面词条 key（含 store 的 `labelKey` 与卡片的 `title-key`），
 * 再载入真实 locale 模块图（ja/ko 通过 `...enUS` 继承 en-US）逐 key 求值：四语都必须解析出非空字符串，
 * 且 ja/ko 的取值不得等于 en-US —— 这条比「文件里出现过该键名」更强，能直接证明这两门语言不是靠继承掩盖未翻译。
 *
 * 每条场景同时向标准输出打印一行脱敏取证记录（`event=`、`key=`、`filter=`、`writes=`、`result=`），
 * 其中作用域键只出现 SHA-256 前 12 位，令牌、claim 值与用户名一律不打印，满足「记录读写键、过滤依据及实际检查结果，
 * 不在日志输出身份凭据」。最后一个用例复核这些行本身不含凭据。
 *
 * 已知范围缺口（见 unfinished-tasks.md 的 WP-05.S05-M1）：概览页仍未被路由记录挂载，环境内也没有 omni-stack 应用栈，
 * 因此本步不声称真实浏览器里的点击、搜索输入、失权重过滤与四语截图已取得运行态证据；
 * 组件的 `<script setup>` 无法在 Node 里实例化，搜索与求交判据只能落在真实源码事实 + 共享记录源的行为语义两侧。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/workspace-regression.test.ts`（cwd=omni-frontend）。
 */
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import vm from 'node:vm'
import type { WorkspacePreferences, WorkspaceStatsCardId } from '../../src/stores/workspace.ts'
import type { ErrorDisplay } from '../../src/utils/error-display.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const vue = require('vue') as typeof import('vue')
const pinia = require('pinia') as typeof import('pinia')

/** 登记只需要菜单节点的这三个字段与子节点，与 store 内部的结构化局部类型一致。 */
interface MenuLike {
  id: number
  type: string
  permissionCode: string
  children?: MenuLike[]
}

/** 受控响应信封，形状与 `src/types/api.ts` 的 `ApiResponse` 一致。 */
interface Envelope {
  code: number
  message: string
  data: unknown
}

/** 一次被记录的统计接口调用及其可脚本化的结局。 */
interface PendingCall {
  cardId: WorkspaceStatsCardId
  succeed(data: unknown): void
  fail(error: unknown): void
}

/** 本步断言所需的 store 可见形状（pinia setup store 已自动解包 ref/computed）。 */
interface RegressionStoreView {
  cards: Record<WorkspaceStatsCardId, { status: string; metrics: { id: string; labelKey: string; value: number | null }[]; display: ErrorDisplay | null }>
  preferences: WorkspacePreferences
  preferenceKey: string | null
  favoriteIds: string[]
  recentIds: string[]
  jobsAllowed: boolean
  workflowAllowed: boolean
  loadCard(cardId: WorkspaceStatsCardId): Promise<void>
  loadPreferences(): void
  isFavorite(id: string): boolean
  toggleFavorite(id: string): boolean
  recordRecent(id: string): void
  recordVisit(menus: readonly MenuLike[], permissionCode: string | null | undefined): boolean
}

/** 受控 `localStorage` 替身：记录每一次读与写的键，供「读写键」取证与「不写公共键」判据。 */
interface StorageView {
  values: Map<string, string>
  writes: string[]
  reads: string[]
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

/** 展示层用到的既有词条；缺失时原样返回 key，与真实 `translate()` 的缺键行为一致。 */
const DISPLAY_TEXTS: Record<string, string> = {
  'request.forbiddenMessage': 'controlled-forbidden-text',
  'request.networkError': 'controlled-network-text',
  'request.timeout': 'controlled-timeout-text',
  'request.serverError': 'controlled-server-text',
  'request.requestFailed': 'controlled-generic-failed-text',
  'request.unknownError': 'controlled-last-resort-text',
  'request.traceHint': 'trace:{traceId}',
}

/** 作用域键前缀只有一个来源，判据要求按字面量核对键形状。 */
const PREFIX = 'omni-workspace-preferences'

/** 有效身份的最小 claim 集；同租户另一用户用于证明两把键互不覆盖。 */
const TOKEN_A = { sub: '101', tenant_id: 1, username: 'alice' }
const TOKEN_B = { sub: '202', tenant_id: 2, username: 'bob' }

const SCOPED_KEY = new RegExp(`^${PREFIX}:\\d+:\\d+$`)

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

const storeSource = read('../../src/stores/workspace.ts')
const routerSource = read('../../src/router/index.ts')
const drawerSource = read('../../src/components/ApplicationDrawer.vue')
const overviewSource = read('../../src/views/workspace/index.vue')
const entryListSource = read('../../src/components/workspace/WorkspaceEntryList.vue')
const statsCardSource = read('../../src/components/workspace/WorkspaceStatsCard.vue')

/** 最近访问上限取自 store 自己的导出常量文本，避免测试与实现各写一个数。 */
const RECENT_LIMIT = Number(/export const RECENT_LIMIT = (\d+)/.exec(storeSource)?.[1] ?? '0')
assert.ok(
  Number.isInteger(RECENT_LIMIT) && RECENT_LIMIT > 0,
  'store 应导出正的最近访问上限常量',
)

/** 把 vm 领域产出的值规范化为宿主值：结构不变，只消除跨 realm 原型差异导致的 deepEqual 误判。 */
function plain<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

/** 造一个只含 ASCII claim 的假 JWT：`decodeJwt` 只解码不验签，这里要的是真实解析路径。 */
function makeToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.controlled-signature`
}

/** 取带身份令牌的作用域键字符串。 */
function scopedKey(claims: { sub?: string; tenant_id?: number }): string {
  return `${PREFIX}:${claims.tenant_id}:${claims.sub}`
}

/** 作用域键只以摘要形式进入取证日志，满足「不在日志输出身份凭据」。 */
function redactKey(key: string | null): string {
  return key === null ? 'none' : createHash('sha256').update(key).digest('hex').slice(0, 12)
}

/** 本文件的脱敏取证记录：每场景一行，最后由专门用例复核其自身不含凭据。 */
const AUDIT: string[] = []

/**
 * 打印一行脱敏取证记录。
 *
 * @param event 场景标识（kebab-case ASCII）
 * @param fields 只允许 ASCII 短值的字段，如作用域键摘要、读写次数、过滤依据与结论
 */
function audit(event: string, fields: Record<string, string | number>): void {
  const detail = Object.entries(fields)
    .map(([name, value]) => `${name}=${value}`)
    .join(' ')
  const line = `WP05S05-AUDIT event=${event} ${detail}`
  AUDIT.push(line)
  // eslint-disable-next-line no-console -- Node 取证脚本需把脱敏检查行写入标准输出，成为 G2 门的可追溯证据
  console.log(line)
}

/** 造一个记录读写的 `localStorage` 替身。 */
function createStorage(seed: Record<string, string> = {}): StorageView & { raw(key: string): string | undefined } {
  const values = new Map<string, string>(Object.entries(seed))
  const writes: string[] = []
  const reads: string[] = []
  return {
    values,
    writes,
    reads,
    getItem: (key: string) => {
      reads.push(key)
      return values.has(key) ? (values.get(key) as string) : null
    },
    setItem: (key: string, value: string) => {
      writes.push(key)
      values.set(key, value)
    },
    raw: (key: string) => values.get(key),
  }
}

/**
 * 构造仅带码值与响应头的假 Axios 错误。
 *
 * `message` 故意写成技术串，用于证明判定与展示都不读取任何自然语言文案。
 */
function axiosError(status: number | null, options: { code?: string; data?: unknown } = {}): unknown {
  return {
    isAxiosError: true,
    name: 'AxiosError',
    message: 'technical axios message must never reach the ui',
    code: options.code,
    config: { url: '/workflow/stats/workspace', headers: { Authorization: 'Bearer controlled-session' } },
    response: status === null ? undefined : { status, data: options.data, headers: {} },
  }
}

/**
 * 在 vm 模块图里执行真实 store，统计接口用可脚本化替身，偏好读写用受控存储。
 *
 * 只替换接口、身份与权限 store、i18n/axios 与 `localStorage`；store 函数体、错误分类与 jwt 解析都不复制、不改写。
 */
async function createHarness(
  options: { token?: string; permissions?: string[]; seed?: Record<string, string> } = {},
) {
  const storage = createStorage(options.seed)
  const context = vm.createContext({
    Promise,
    setTimeout,
    clearTimeout,
    // 真实 jwt.ts 依赖浏览器全局 atob，这里只提供等价解码，不改写其判定逻辑。
    atob: (value: string) => Buffer.from(value, 'base64').toString('binary'),
    localStorage: storage,
  })
  const user = vue.reactive({ token: options.token ?? '', username: 'controlled-user' })
  const permission = vue.reactive({ permissions: options.permissions ?? ['workflow'], menuTree: [] as MenuLike[] })
  const calls: PendingCall[] = []

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

  /** 记录一次调用并返回可脚本化结束的 Promise。 */
  function enqueue(cardId: WorkspaceStatsCardId): Promise<{ data: Envelope }> {
    let succeed!: (data: unknown) => void
    let fail!: (error: unknown) => void
    const promise = new Promise<{ data: Envelope }>((resolve, reject) => {
      succeed = (data: unknown) => resolve({ data: { code: 200, message: 'success', data } })
      fail = (error: unknown) => reject(error)
    })
    calls.push({ cardId, succeed, fail })
    return promise
  }

  const modules: Record<string, vm.Module> = {
    vue: synthetic({ ...vue }),
    pinia: synthetic({ ...pinia }),
    axios: synthetic({
      isAxiosError: (value: unknown): boolean =>
        typeof value === 'object' && value !== null && (value as { isAxiosError?: unknown }).isAxiosError === true,
    }),
    '@/i18n': synthetic({
      translate: (key: string, params: Record<string, string | number> = {}) => {
        const text = DISPLAY_TEXTS[key]
        if (text === undefined) return key
        return text.replace(/\{(\w+)\}/g, (_match: string, name: string) => String(params[name] ?? ''))
      },
    }),
    '@/stores/user': synthetic({ useUserStore: () => user }),
    '@/stores/permission': synthetic({ usePermissionStore: () => permission }),
    '@/api/myJob': synthetic({ getMyJobStats: () => enqueue('jobs') }),
    '@/api/workflow': synthetic({ getWorkspaceStats: () => enqueue('workflow') }),
    '@/utils/jwt': real('../../src/utils/jwt.ts'),
    '@/utils/error-display': real('../../src/utils/error-display.ts'),
    '@/utils/errors': real('../../src/utils/errors.ts'),
    '@/stores/workspace': real('../../src/stores/workspace.ts'),
  }

  const workspaceModule = modules['@/stores/workspace'] as vm.SourceTextModule
  await workspaceModule.link((specifier) => {
    const dependency = modules[specifier]
    assert.ok(dependency, `未配置依赖替身: ${specifier}`)
    return dependency
  })
  await workspaceModule.evaluate()

  const errorsNamespace = (modules['@/utils/errors'] as vm.SourceTextModule).namespace as unknown as {
    resolveDisplayText(display: ErrorDisplay): string
  }

  pinia.setActivePinia(pinia.createPinia())
  const namespace = workspaceModule.namespace as unknown as { useWorkspaceStore: () => RegressionStoreView }
  const store = namespace.useWorkspaceStore()

  /** 取该卡最近一次调用，断言前自动校验调用次数。 */
  function lastCall(cardId: WorkspaceStatsCardId): PendingCall {
    const call = [...calls].reverse().find((entry) => entry.cardId === cardId)
    assert.ok(call, `缺少 ${cardId} 卡请求`)
    return call
  }

  return {
    store,
    user,
    permission,
    storage,
    calls,
    /** 该卡已发出的请求次数。 */
    countOf: (cardId: WorkspaceStatsCardId): number => calls.filter((entry) => entry.cardId === cardId).length,
    /** 发请求并结算为成功响应。 */
    async settle(cardId: WorkspaceStatsCardId, data: unknown): Promise<void> {
      const task = store.loadCard(cardId)
      lastCall(cardId).succeed(data)
      await task
    },
    /** 发请求并结算为失败。 */
    async reject(cardId: WorkspaceStatsCardId, error: unknown): Promise<void> {
      const task = store.loadCard(cardId)
      lastCall(cardId).fail(error)
      await task
    },
    /** 只发起请求不结算，用于断言并发复用与 loading 态。 */
    start(cardId: WorkspaceStatsCardId): Promise<void> {
      return store.loadCard(cardId)
    },
    /** 结算最近一次该卡调用为失败（配合 start 使用）。 */
    settleFailure(cardId: WorkspaceStatsCardId, error: unknown): void {
      lastCall(cardId).fail(error)
    },
    /** 切换身份并等 store 自身的 watch 把内存记录换成新作用域。 */
    switchIdentity: async (claims: Record<string, unknown>): Promise<void> => {
      user.token = makeToken(claims)
      user.username = String(claims.username ?? 'controlled-user')
      await vue.nextTick()
    },
    /** 写入过的作用域键（去重）。 */
    writtenKeys: (): string[] => [...new Set(storage.writes)],
    /** 用真实展示层把描述符解析为当前语言文案。 */
    text: (display: ErrorDisplay): string => errorsNamespace.resolveDisplayText(display),
  }
}

type Harness = Awaited<typeof createHarness>

/**
 * 受控已授权菜单树：目录、普通叶子与二级目录下的叶子各一，形状与真实 `MenuNode` 一致。
 * 稳定 ID 全部 ≥ 1001，便于与「不在树里的 ID」区分。
 */
function menuTree(): MenuLike[] {
  return [
    {
      id: 1000,
      type: 'DIRECTORY',
      permissionCode: 'system',
      children: [
        { id: 1001, type: 'MENU', permissionCode: 'system:user' },
        { id: 1002, type: 'MENU', permissionCode: 'system:role' },
        {
          id: 1003,
          type: 'DIRECTORY',
          permissionCode: 'system:monitor',
          children: [{ id: 1004, type: 'MENU', permissionCode: 'system:online' }],
        },
      ],
    },
    { id: 2000, type: 'MENU', permissionCode: 'crm' },
  ]
}

/** 失权后的菜单树：`system:online` 被降级为 1003 内的目录，`system:role` 整体移除。 */
function revokedTree(): MenuLike[] {
  return [
    {
      id: 1000,
      type: 'DIRECTORY',
      permissionCode: 'system',
      children: [
        { id: 1001, type: 'MENU', permissionCode: 'system:user' },
        {
          id: 1003,
          type: 'DIRECTORY',
          permissionCode: 'system:monitor',
          children: [{ id: 1004, type: 'DIRECTORY', permissionCode: 'system:online' }],
        },
      ],
    },
    { id: 2000, type: 'MENU', permissionCode: 'crm' },
  ]
}

/** 取 `<script setup>` 区块。 */
function scriptBlock(source: string): string {
  const start = source.indexOf('<script setup')
  const end = source.indexOf('</script>')
  assert.notEqual(start, -1, '组件应包含 script setup 区块')
  assert.notEqual(end, -1, '组件应闭合 script 区块')
  return source.slice(start, end)
}

/** 取 store 里某个函数的函数体文本（按大括号配对截取）。 */
function storeFnBody(name: string): string {
  const start = storeSource.indexOf(`function ${name}(`)
  assert.notEqual(start, -1, `store 缺少 ${name}`)
  let depth = 0
  let index = start
  let open = -1
  for (; index < storeSource.length; index += 1) {
    const char = storeSource[index]
    if (char === '{') {
      if (open === -1) open = index
      depth += 1
    } else if (char === '}') {
      depth -= 1
      if (depth === 0) break
    }
  }
  assert.notEqual(open, -1, `store 的 ${name} 应有函数体`)
  return storeSource.slice(open, index + 1)
}

/** 取抽屉 script 里从 `from` 到 `to` 之间的片段。 */
function drawerSection(from: string, to: string): string {
  const script = scriptBlock(drawerSource)
  const start = script.indexOf(from)
  assert.notEqual(start, -1, `抽屉源码缺少片段起点：${from}`)
  const end = script.indexOf(to, start + from.length)
  assert.notEqual(end, -1, `抽屉源码缺少片段终点：${to}`)
  return script.slice(start, end)
}

/** 取当前落盘的信封对象。 */
function envelope(h: Harness, claims: { sub?: string; tenant_id?: number }): Record<string, unknown> {
  const raw = h.storage.raw(scopedKey(claims))
  assert.ok(raw !== undefined, '应已写入该身份的作用域键')
  return JSON.parse(raw) as Record<string, unknown>
}

test('偏好恢复：同一份磁盘内容另起 store 后收藏与最近访问按存盘顺序原样读回', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  assert.equal(h.store.toggleFavorite('1001'), true)
  assert.equal(h.store.toggleFavorite('1002'), true)
  assert.equal(h.store.recordVisit(menuTree(), 'system:online'), true)
  const stored = h.storage.raw(scopedKey(TOKEN_A))
  assert.ok(stored !== undefined)

  const reopened = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'], seed: { [scopedKey(TOKEN_A)]: stored } })
  reopened.store.loadPreferences()
  assert.deepEqual(plain(reopened.store.favoriteIds), ['1001', '1002'])
  assert.deepEqual(plain(reopened.store.recentIds), ['1004'])
  assert.equal(reopened.store.isFavorite('1001'), true)
  audit('preference-restore', {
    key: redactKey(reopened.store.preferenceKey),
    reads: reopened.storage.reads.length,
    filter: 'stored-envelope',
    favorites: 2,
    recents: 1,
    result: 'pass',
  })
})

test('收藏取消与重新收藏：其余项相对顺序不变、重新收藏落到末尾、重复取消幂等', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  h.store.toggleFavorite('1001')
  h.store.toggleFavorite('1002')
  h.store.toggleFavorite('1004')
  assert.deepEqual(plain(h.store.favoriteIds), ['1001', '1002', '1004'])

  assert.equal(h.store.toggleFavorite('1002'), false)
  assert.deepEqual(plain(h.store.favoriteIds), ['1001', '1004'])
  assert.equal(h.store.toggleFavorite('1002'), true)
  assert.deepEqual(plain(h.store.favoriteIds), ['1001', '1004', '1002'])
  assert.equal(h.store.toggleFavorite('1002'), false)
  assert.deepEqual(plain(h.store.favoriteIds), ['1001', '1004'])
  assert.deepEqual(plain(h.store.recentIds), [], '收藏动作不应触碰最近访问')
  audit('favorite-toggle', {
    key: redactKey(h.store.preferenceKey),
    writes: h.storage.writes.length,
    filter: 'stable-insert-order',
    result: 'pass',
  })
})

test('最近访问十二项边界与重复项：长度恒为 store 导出的上限且重复访问只提到最前', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  for (let index = 1; index <= RECENT_LIMIT; index += 1) h.store.recordRecent(String(1000 + index))
  assert.equal(h.store.recentIds.length, RECENT_LIMIT)
  assert.equal(h.store.recentIds[0], String(1000 + RECENT_LIMIT))

  h.store.recordRecent('1013')
  assert.equal(h.store.recentIds.length, RECENT_LIMIT, '第十三项应挤掉最旧项而不是增长')
  assert.equal(h.store.recentIds[0], '1013')
  assert.equal(h.store.recentIds.includes('1001'), false)

  h.store.recordRecent('1005')
  assert.equal(h.store.recentIds.length, RECENT_LIMIT, '重复访问不增长')
  assert.equal(h.store.recentIds[0], '1005')
  assert.equal(new Set(h.store.recentIds).size, RECENT_LIMIT, '去重后每项唯一')
  audit('recents-limit', {
    key: redactKey(h.store.preferenceKey),
    limit: RECENT_LIMIT,
    writes: h.storage.writes.length,
    filter: 'dedupe-prepend-truncate',
    result: 'pass',
  })
})

test('身份切换：另一身份只写自己的键且流程权限撤销后转 unavailable 不再发请求', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  await h.settle('jobs', { totalJobs: 3, todayExecuted: 2, todayFailed: 1 })
  await h.settle('workflow', { todoCount: 4, myInitiatedRunning: 1, myInitiatedTotal: 2 })
  assert.equal(h.store.cards.jobs.status, 'loaded')
  assert.equal(h.store.cards.workflow.status, 'loaded')
  h.store.toggleFavorite('1001')
  const storedA = h.storage.raw(scopedKey(TOKEN_A))
  assert.ok(storedA !== undefined)

  h.permission.permissions = []
  await h.switchIdentity(TOKEN_B)
  assert.equal(h.store.workflowAllowed, false)
  assert.equal(h.store.jobsAllowed, true)
  assert.deepEqual(plain(h.store.favoriteIds), [], '新身份起手不得带上一身份的内存记录')
  await h.store.loadCard('workflow')
  assert.equal(h.store.cards.workflow.status, 'unavailable')
  assert.equal(h.countOf('workflow'), 1, '判定不通过不得再发该业务请求')

  assert.equal(h.store.toggleFavorite('2000'), true)
  assert.notEqual(h.storage.raw(scopedKey(TOKEN_B)), undefined)
  assert.equal(h.storage.raw(scopedKey(TOKEN_A)), storedA, 'B 身份的写入不得改动 A 的落盘内容')

  h.permission.permissions = ['workflow']
  await h.switchIdentity(TOKEN_A)
  assert.deepEqual(plain(h.store.favoriteIds), ['1001'], '切回 A 读回 A 自己的记录')
  assert.equal(h.store.workflowAllowed, true)
  audit('identity-switch', {
    key: redactKey(h.store.preferenceKey),
    writes: h.storage.writes.length,
    filter: 'tenant-and-user-scope',
    stats_requests: h.calls.length,
    result: 'pass',
  })
})

test('身份缺失时统计与偏好同向降级：清令牌后任务卡 unavailable 且偏好零写入', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  const baselineWrites = h.storage.writes.length
  h.store.toggleFavorite('1001')
  await h.switchIdentity({ sub: '101', tenant_id: 1, username: 'alice' })

  h.user.token = ''
  await vue.nextTick()
  assert.equal(h.store.preferenceKey, null)
  assert.equal(h.store.jobsAllowed, false)
  await h.store.loadCard('jobs')
  assert.equal(h.store.cards.jobs.status, 'unavailable')
  assert.equal(h.countOf('jobs'), 0, '未登录不得发任务统计请求')
  assert.equal(h.store.toggleFavorite('1002'), false)
  assert.equal(h.store.recordVisit(menuTree(), 'crm'), false)
  assert.equal(h.storage.writes.length, baselineWrites + 1, '只允许有身份时的那一次写入')
  audit('identity-missing', {
    key: 'none',
    writes: h.storage.writes.length,
    filter: 'null-scope-rejects-all',
    result: 'pass',
  })
})

test('卡片独立失败：流程卡 403 不清空任务卡已加载数据，重试只重发本卡', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  await h.settle('jobs', { totalJobs: 7, todayExecuted: 5, todayFailed: 2 })
  const jobsSnapshot = plain(h.store.cards.jobs)
  await h.reject('workflow', axiosError(403, { data: { code: 403, message: 'server text must not reach the ui' } }))

  assert.equal(h.store.cards.workflow.status, 'failed')
  assert.equal(h.store.cards.workflow.display?.kind, 'permissionDenied')
  assert.deepEqual(plain(h.store.cards.jobs), jobsSnapshot, '一张卡失败不得清空另一张卡的成功数据')

  const task = h.start('workflow')
  assert.equal(h.store.cards.workflow.status, 'loading')
  h.settleFailure('workflow', axiosError(403, { data: { code: 403 } }))
  await task
  assert.equal(h.countOf('workflow'), 2)
  assert.equal(h.countOf('jobs'), 1, '重试只重发本卡')
  assert.deepEqual(plain(h.store.cards.jobs), jobsSnapshot)
  audit('card-independent-failure', {
    key: redactKey(h.store.preferenceKey),
    jobs_requests: h.countOf('jobs'),
    workflow_requests: h.countOf('workflow'),
    filter: 'per-card-state',
    result: 'pass',
  })
})

test('失败分类互不混同：403、断网、超时、500 的语义与文案各不相同且不含后端原文', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  const cases: { error: unknown; kind: string; text: string }[] = [
    { error: axiosError(403, { data: { code: 403 } }), kind: 'permissionDenied', text: 'controlled-forbidden-text' },
    { error: axiosError(null, { code: 'ERR_NETWORK' }), kind: 'network', text: 'controlled-network-text' },
    { error: axiosError(null, { code: 'ECONNABORTED' }), kind: 'timeout', text: 'controlled-timeout-text' },
    { error: axiosError(500, { data: { code: 500 } }), kind: 'serverError', text: 'controlled-server-text' },
  ]
  const kinds: string[] = []
  const keys: string[] = []
  for (const item of cases) {
    await h.reject('workflow', item.error)
    const display = h.store.cards.workflow.display
    assert.ok(display !== null, '失败态应保留归一化描述符')
    assert.equal(display.kind, item.kind)
    assert.equal(h.text(display), item.text)
    kinds.push(display.kind)
    keys.push(display.i18nKey)
  }
  assert.equal(new Set(kinds).size, 4, '四种失败各自归类')
  assert.equal(new Set(keys).size, 4, '四种失败各自取不同词条')
  assert.equal(h.countOf('workflow'), 4)
  audit('failure-classification', {
    key: redactKey(h.store.preferenceKey),
    kinds: kinds.join('|'),
    filter: 'status-code-plus-business-code',
    result: 'pass',
  })
})

test('真实统计状态：全零判 empty、缺字段留 null 不补 0、响应形态不可用判 failed', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  await h.settle('jobs', { totalJobs: 0, todayExecuted: 0, todayFailed: 0 })
  assert.equal(h.store.cards.jobs.status, 'empty')
  assert.deepEqual(plain(h.store.cards.jobs.metrics.map((metric) => metric.value)), [0, 0, 0])

  await h.settle('workflow', { todoCount: 6 })
  assert.equal(h.store.cards.workflow.status, 'loaded')
  assert.deepEqual(plain(h.store.cards.workflow.metrics.map((metric) => metric.value)), [6, null, null])

  await h.settle('jobs', null)
  assert.equal(h.store.cards.jobs.status, 'failed')
  assert.equal(h.store.cards.jobs.display, null, '响应形态不可用不是请求异常，不带错误描述符')
  assert.equal(h.store.cards.jobs.metrics.length, 0, '任何分支都不把异常写成 0')
  audit('stats-status', {
    key: redactKey(h.store.preferenceKey),
    filter: 'zero-vs-missing-vs-malformed',
    result: 'pass',
  })
})

test('两张通道互不触发：偏好写入一次统计请求都不发，统计失败一字不改偏好记录', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  await h.settle('jobs', { totalJobs: 1, todayExecuted: 1, todayFailed: 0 })
  await h.settle('workflow', { todoCount: 1, myInitiatedRunning: 0, myInitiatedTotal: 1 })
  const requestsBefore = h.calls.length

  h.store.toggleFavorite('1001')
  h.store.recordVisit(menuTree(), 'system:user')
  h.store.recordVisit(menuTree(), 'crm')
  h.store.loadPreferences()
  assert.equal(h.calls.length, requestsBefore, '偏好读写不得触发任何统计请求')

  const favorites = plain(h.store.favoriteIds)
  const recents = plain(h.store.recentIds)
  await h.reject('workflow', axiosError(403, { data: { code: 403 } }))
  await h.reject('jobs', axiosError(null, { code: 'ERR_NETWORK' }))
  assert.deepEqual(plain(h.store.favoriteIds), favorites)
  assert.deepEqual(plain(h.store.recentIds), recents)
  assert.equal(h.calls.length, requestsBefore + 2, '只有两次显式取数各发一次请求')
  audit('channel-isolation', {
    key: redactKey(h.store.preferenceKey),
    requests: h.calls.length,
    writes: h.storage.writes.length,
    filter: 'preferences-never-fetch-stats',
    result: 'pass',
  })
})

test('异常存储：损坏值只把本身份键降级为空，其它身份键原样保留且随后仍可正常读写', async () => {
  const keyA = scopedKey(TOKEN_A)
  const keyB = scopedKey(TOKEN_B)
  const goodB = JSON.stringify({ version: 1, favorites: ['2001'], recents: ['2002'] })
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'], seed: { [keyA]: '{', [keyB]: goodB } })
  h.store.loadPreferences()
  assert.deepEqual(plain(h.store.preferences), { favorites: [], recents: [] }, '损坏 JSON 降级为空记录')
  assert.equal(h.storage.raw(keyB), goodB, '读操作不得改写别的键')

  assert.equal(h.store.toggleFavorite('1001'), true)
  const restored = envelope(h, TOKEN_A)
  assert.deepEqual(restored.favorites, ['1001'])
  assert.deepEqual(restored.recents, [])
  assert.equal(h.storage.raw(keyB), goodB, '本身份的修复不得波及另一身份')
  audit('corrupt-storage', {
    key: redactKey(h.store.preferenceKey),
    reads: h.storage.reads.length,
    writes: h.storage.writes.length,
    filter: 'per-key-degrade',
    result: 'pass',
  })
})

test('失权后行为：流程权限撤销使卡片转 unavailable 且不再发请求，菜单降级使登记被拒且记录不变', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  await h.settle('workflow', { todoCount: 2, myInitiatedRunning: 0, myInitiatedTotal: 1 })
  h.store.recordVisit(menuTree(), 'system:online')
  h.store.toggleFavorite('1004')
  const favorites = plain(h.store.favoriteIds)
  const recents = plain(h.store.recentIds)
  const requests = h.calls.length

  h.permission.permissions = []
  assert.equal(h.store.workflowAllowed, false)
  await h.store.loadCard('workflow')
  assert.equal(h.store.cards.workflow.status, 'unavailable')
  assert.equal(h.countOf('workflow'), 1, '失权后不得再发该业务请求')

  assert.equal(h.store.recordVisit(revokedTree(), 'system:online'), false, '降级为目录的权限码不可登记')
  assert.equal(h.store.recordVisit(revokedTree(), 'system:role'), false, '已从菜单树移除的权限码不可登记')
  h.permission.permissions = ['workflow']
  assert.deepEqual(plain(h.store.favoriteIds), favorites, '失权只拒新登记，不擅自改写既有记录')
  assert.deepEqual(plain(h.store.recentIds), recents)
  assert.equal(h.calls.length, requests, '拒绝登记也不得触发取数')
  audit('revoked-permission', {
    key: redactKey(h.store.preferenceKey),
    filter: 'menu-leaf-and-permission-root',
    writes: h.storage.writes.length,
    result: 'pass',
  })
})

test('隔离落盘：全流程只触碰带身份的作用域键，公共键零写入零读取', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  h.store.toggleFavorite('1001')
  h.store.recordVisit(menuTree(), 'system:role')
  await h.switchIdentity(TOKEN_B)
  h.store.toggleFavorite('2000')
  await h.switchIdentity(TOKEN_A)
  h.store.recordVisit(menuTree(), 'crm')

  const written = h.writtenKeys()
  const read = [...new Set(h.storage.reads)]
  assert.ok(written.length > 0)
  for (const key of [...written, ...read]) assert.match(key, SCOPED_KEY, '读写键必须是带 tenant 与用户的作用域键')
  assert.equal(new Set(written).size, 2, '两个身份各一把键')
  assert.equal(written.includes(PREFIX), false, '不落公共命名空间')
  assert.equal(h.storage.values.size, 2, '存储里只存在两把身份键')
  audit('storage-isolation', {
    keys: written.map((key) => redactKey(key)).join('|'),
    writes: h.storage.writes.length,
    reads: h.storage.reads.length,
    filter: 'scoped-key-only',
    result: 'pass',
  })
})

test('落盘内容只有版本与稳定 ID：不含令牌、用户名、权限码、路径、query 或 hash', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A), permissions: ['workflow'] })
  const token = makeToken(TOKEN_A)
  h.store.recordVisit(menuTree(), 'system:user')
  h.store.toggleFavorite('2000')
  const raw = h.storage.raw(scopedKey(TOKEN_A))
  assert.ok(raw !== undefined)

  const parsed = JSON.parse(raw) as Record<string, unknown>
  assert.deepEqual(Object.keys(parsed).sort(), ['favorites', 'recents', 'version'])
  assert.equal(parsed.version, 1)
  assert.deepEqual(parsed.favorites, ['2000'])
  assert.deepEqual(parsed.recents, ['1001'])
  for (const forbidden of [token, 'controlled-signature', 'alice', 'system:user', 'crm', '/workspace', '?', '#', 'Bearer']) {
    assert.equal(raw.includes(forbidden), false, `落盘不应包含 ${forbidden}`)
  }
  audit('envelope-contents', {
    key: redactKey(h.store.preferenceKey),
    fields: Object.keys(parsed).sort().join(','),
    filter: 'stable-ids-only',
    result: 'pass',
  })
})

test('搜索命中与收藏共用同一份派生集合：抽屉只做本地过滤且不发起任何请求', () => {
  const search = drawerSection('const searchHits = computed<SearchHit[]>', '/** 加载与失败态沿用同一份菜单加载状态')
  assert.match(search, /if \(normalizedKeyword\.value === ''\) return \[\]/, '空关键字直接返回空集合')
  assert.match(search, /for \(const category of categories\.value\)/, '命中范围限已授权派生集合')
  const fields = [...search.matchAll(/matches\(([^)]*)\)/g)].map((match) => match[1].trim()).sort()
  assert.deepEqual(fields, [
    'category.app.permissionCode',
    'category.title',
    'leaf.group',
    'leaf.page.permissionCode',
    'leaf.title',
  ], '命中字段限定在标题、分组与权限编码')
  assert.doesNotMatch(search, /await|axios|fetch\(|loadMenus|retryLoadMenus|getMyJobStats|getWorkspaceStats/, '搜索不发请求')

  const script = scriptBlock(drawerSource)
  assert.match(script, /const normalizedKeyword = computed\(\(\) => keyword\.value\.trim\(\)\.toLowerCase\(\)\)/)
  const favorites = drawerSection('const favoriteTargets = computed', '/** 收藏写入只接受当前可见目标')
  assert.match(favorites, /for \(const id of workspaceStore\.favoriteIds\)/)
  assert.match(favorites, /const target = targetsById\.value\.get\(id\)/)
  assert.match(favorites, /if \(target !== undefined\) out\.push\(target\)/)
  const targets = drawerSection('const targetsById = computed', '/** 抽屉收藏集合')
  assert.match(targets, /for \(const category of categories\.value\)/, '收藏索引与搜索遍历同一份派生集合')
  assert.match(script, /useApplicationNavigation\(\)/)
  const apiImports = script.match(/^import .*from '@\/api\/.*$/gm) ?? []
  const allowedApiImport = 'import type { MenuNode } from \'@/api/menu\''
  assert.deepEqual(apiImports, [allowedApiImport], '抽屉只允许类型导入，不直连接口')
  audit('search-filter-basis', {
    filter: 'categories-only',
    matched_fields: fields.length,
    result: 'pass',
  })
})

test('概览求交依据与抽屉一致：记录 ID 只经可见集合解析，写入前先校验可见性', () => {
  const script = scriptBlock(overviewSource)
  assert.match(script, /const visibleEntries = computed\(\(\) => \{/)
  assert.match(script, /\[\.\.\.modules\.value, \.\.\.pages\.value\]/, '求交集合就是统一导航派生的模块与叶子')
  assert.match(script, /map\.set\(String\(entry\.id\), entry\)/)
  assert.match(script, /\.map\(\(id\) => visibleEntries\.value\.get\(id\)\)/)
  assert.match(script, /entry !== undefined/, '查不到的 ID 直接丢弃而不是渲染成死项')
  assert.match(script, /if \(!visibleEntries\.value\.has\(id\)\) return/, '收藏写入前校验可见性')
  assert.match(script, /onMounted\(\(\) => workspaceStore\.loadPreferences\(\)\)/)
  assert.match(script, /const favoriteEntries = computed\(\(\) => entriesOf\(workspaceStore\.favoriteIds\)\)/)
  assert.match(script, /const recentEntries = computed\(\(\) => entriesOf\(workspaceStore\.recentIds\)\)/)
  assert.doesNotMatch(script, /localStorage|defineStore|recordVisit|recordRecent/, '页面既不落存储也不是访问记录写入方')
  assert.doesNotMatch(script, /getMyJobStats|getWorkspaceStats|api\/myJob|api\/workflow/, '页面不另建统计 API 来源')
  assert.match(script, /workspaceStore\.loadCard\(cardId\)/, '嵌入操作完成后通过同一 Store 刷新统计')
  for (const source of [overviewSource, drawerSource]) {
    assert.match(source, /from '@\/stores\/workspace'/, '两处消费方引用同一个 store')
    assert.match(source, /from '@\/composables\/useApplicationNavigation'/, '两处消费方引用同一份派生导航')
  }
  assert.doesNotMatch(scriptBlock(entryListSource), /localStorage|defineStore|useWorkspaceStore/, '条目组件不读写存储也不自取记录')
  const statsScript = scriptBlock(statsCardSource)
  assert.match(statsScript, /workspaceStore\.loadCard\(props\.cardId\)/, '统计卡只按本卡 id 取数')
  assert.doesNotMatch(
    statsScript,
    /localStorage|defineStore|loadPreferences|persistPreferences|favoriteIds|recentIds|recordVisit|recordRecent/,
    '统计卡不碰偏好记录',
  )
})

test('写入方唯一：最近访问只由路由访问成功钩子触发且该钩子不改写导航', () => {
  const hook = routerSource.indexOf('router.afterEach((to, _from, failure) => {')
  assert.notEqual(hook, -1, '访问成功钩子应在文件末尾以固定签名存在')
  assert.match(routerSource.slice(hook, hook + 200), /if \(failure\) return/)
  const beforeHook = routerSource.slice(0, routerSource.lastIndexOf('/**', hook))
  assert.equal(beforeHook.includes('recordVisit'), false, 'beforeEach 与守卫链不得抢先登记')
  assert.equal((routerSource.match(/recordVisit\(/g) ?? []).length, 1, '登记调用点只有一个')
  const hookBody = routerSource.slice(hook, routerSource.indexOf('export default router', hook))
  assert.match(hookBody, /usePermissionStore\(\)\.menuTree/, '菜单来源固定为已授权菜单树')
  assert.doesNotMatch(hookBody, /next\(|router\.push|\.replace\(|redirect|window\.|location|to\.query|to\.hash|fullPath|localStorage/)
  assert.equal((routerSource.match(/\brecordRecent\b/g) ?? []).length, 0, '路由不直接调 recordRecent')
  assert.doesNotMatch(routerSource.slice(0, hook), /localStorage/, '守卫与路由表不读写存储')
})

test('取数切片与偏好切片互不引用：状态机不落存储，偏好读写不发请求', () => {
  const statsSlice = `${storeFnBody('loadCard')}${storeFnBody('performLoad')}${storeFnBody('metricsOf')}`
  assert.doesNotMatch(statsSlice, /localStorage|preferences\.value|persistPreferences|loadPreferences|recordRecent|recordVisit|preferenceKey/)
  const preferenceSlice = `${storeFnBody('persistPreferences')}${storeFnBody('loadPreferences')}${storeFnBody('toggleFavorite')}${storeFnBody('recordRecent')}${storeFnBody('recordVisit')}`
  assert.doesNotMatch(preferenceSlice, /getMyJobStats|getWorkspaceStats|loadCard|performLoad|cards\.value/)
  assert.match(storeFnBody('persistPreferences'), /localStorage\.setItem/)
  assert.match(storeFnBody('loadPreferences'), /localStorage\.getItem/)
  assert.equal((storeSource.match(/localStorage\.setItem/g) ?? []).length, 1, '落盘点只有一处')
  assert.equal((storeSource.match(/localStorage\.getItem/g) ?? []).length, 1, '读盘点只有一处')
  assert.doesNotMatch(storeSource, /@\/api\/menu|loadMenus|hasPermission|checkPermi/, '偏好与统计都给不出访问权限')
})

test('WP-05 用到的词条在四语里全部解析出非空译文，ja/ko 不靠继承掩盖未翻译', async () => {
  const sources = [overviewSource, drawerSource, statsCardSource, entryListSource, storeSource]
  const keys = new Set<string>()
  for (const source of sources) {
    for (const match of source.matchAll(/(?:\bt|\bte)\(\s*'([A-Za-z][\w]*\.[\w.]+)'/g)) keys.add(match[1])
    for (const match of source.matchAll(/labelKey:\s*'([\w.]+)'/g)) keys.add(match[1])
    for (const match of source.matchAll(/title-key="([\w.]+)"/g)) keys.add(match[1])
  }
  const list = [...keys].sort()
  assert.ok(list.length >= 30, `提取到的词条数量异常：${list.length}`)

  const context = vm.createContext({ Object, Array, String, Number, Boolean, Symbol, JSON, Reflect, Promise, TypeError })
  const cache = new Map<string, vm.SourceTextModule>()
  /** 载入真实 locale 模块；ja/ko 通过 `...enUS` 依赖 en-US，这里按真实继承图解析。 */
  function localeModule(relative: string): vm.SourceTextModule {
    const url = new URL(relative, import.meta.url)
    const cached = cache.get(url.pathname)
    if (cached !== undefined) return cached
    const code = ts.transpileModule(readFileSync(url, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    }).outputText
    const created = new vm.SourceTextModule(code, { context, identifier: url.pathname })
    cache.set(url.pathname, created)
    return created
  }

  const names = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'] as const
  const messages: Record<string, Record<string, unknown>> = {}
  for (const name of names) {
    const mod = localeModule(`../../src/locales/${name}.ts`)
    await mod.link((specifier) => {
      assert.ok(specifier.startsWith('./'), `locale ${name} 出现未预期依赖：${specifier}`)
      return localeModule(`../../src/locales/${specifier.slice(2)}.ts`)
    })
    await mod.evaluate()
    messages[name] = (mod.namespace as unknown as { default: Record<string, unknown> }).default
  }

  /** 按点分路径求值；任一段缺失即返回 undefined。 */
  function resolve(root: Record<string, unknown>, key: string): unknown {
    let cursor: unknown = root
    for (const segment of key.split('.')) {
      if (cursor === null || typeof cursor !== 'object') return undefined
      cursor = (cursor as Record<string, unknown>)[segment]
    }
    return cursor
  }

  const missing: string[] = []
  const inherited: string[] = []
  for (const key of list) {
    const english = resolve(messages['en-US'], key)
    for (const name of names) {
      const value = resolve(messages[name], key)
      if (typeof value !== 'string' || value.trim() === '') missing.push(`${name} ${key}`)
      if ((name === 'ja-JP' || name === 'ko-KR') && value === english) inherited.push(`${name} ${key}`)
    }
  }
  assert.deepEqual(missing, [], `存在缺译或空译文词条：${missing.join(', ')}`)
  assert.deepEqual(inherited, [], `ja/ko 直接沿用 en-US 文本，未经本地化：${inherited.join(', ')}`)

  // 四语言新增键不引入顶层键：scaffold/catalog/modules.yaml 因此无需变更。
  for (const name of names) {
    assert.equal(
      Object.prototype.hasOwnProperty.call(messages[name], 'workspaceOverview'),
      false,
      `${name} 不应新增 workspaceOverview 顶层键`,
    )
    assert.equal(
      Object.prototype.hasOwnProperty.call(messages[name], 'workspaceStats'),
      false,
      `${name} 不应新增 workspaceStats 顶层键`,
    )
  }
  assert.equal(resolve(messages['zh-CN'], 'common.favorite'), '收藏')
  assert.ok(typeof resolve(messages['ja-JP'], 'common.favorite') === 'string')
  assert.ok(typeof resolve(messages['ko-KR'], 'common.unfavorite') === 'string')
  audit('locale-coverage', {
    keys: list.length,
    missing: missing.length,
    inherited: inherited.length,
    filter: 'four-locales-resolved',
    result: 'pass',
  })
})

test('取证记录本身不含身份凭据：每行只出现键摘要、次数、过滤依据与结论', () => {
  assert.ok(AUDIT.length >= 12, `取证记录行数异常：${AUDIT.length}`)
  const events = AUDIT.map((line) => /event=([a-z-]+)/.exec(line)?.[1] ?? '')
  for (const expected of [
    'preference-restore',
    'favorite-toggle',
    'recents-limit',
    'identity-switch',
    'identity-missing',
    'card-independent-failure',
    'failure-classification',
    'stats-status',
    'channel-isolation',
    'corrupt-storage',
    'revoked-permission',
    'storage-isolation',
    'envelope-contents',
    'search-filter-basis',
    'locale-coverage',
  ]) {
    assert.ok(events.includes(expected), `缺少取证场景：${expected}`)
  }
  for (const line of AUDIT) {
    assert.match(line, /^WP05S05-AUDIT event=[a-z-]+( [a-z_-]+=[^=\s]+)+$/)
    for (const forbidden of ['controlled-signature', 'Bearer', 'alice', 'bob', 'sub', 'tenant_id', `${PREFIX}:`]) {
      assert.equal(line.includes(forbidden), false, `取证行泄漏身份凭据：${line}`)
    }
    assert.equal(/event=[a-z-]+.*key=(none|[0-9a-f]{12})/.test(line) || !line.includes('key='), true)
  }
  // eslint-disable-next-line no-console -- 汇总行同样是 G2 门的取证输出，不属于浏览器代码
  console.log(`WP05S05-AUDIT-SUMMARY lines=${AUDIT.length} gate=G2 fields=key,reads,writes,filter,result`)
})
