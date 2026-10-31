/**
 * @module navigation/workspace-stats.test
 * @description WP-05.S02 概览统计卡的定向测试（node:test）。
 *
 * 行为层在 vm 模块图里执行**真实**的 `src/stores/workspace.ts`（连同真实 `utils/errors.ts` 与 `utils/error-display.ts`、
 * 真实 vue 响应式与真实 pinia），只把接口、身份 store、权限 store 与 i18n/axios 边界换成受控替身，
 * 因此以下判据是跑出来的而不是读字符串读出来的：
 * - 未登录不请求任务统计；权限清单里没有 workflow 码不请求流程统计，`workflow:` 子码同样放行；
 * - 判定改为可用之前重试也不会发请求，改为可用之后才真正请求一次；
 * - 同一张卡并发载入只发一次请求；
 * - 真实全零判 empty，非零判 loaded，字段缺失或类型异常保留 null，响应形态不可用判 failed，任何分支都不补 0；
 * - 403、断网、超时、500 分别落到不同语义分类，展示文案来自本地化 key，绝不出现后端 message 或 Axios 技术串；
 * - 一张卡失败或重试都不改写另一张卡的状态，store 也不引用菜单接口，统计切片不触碰本地存储。
 *
 * 结构层再核对：只调用两个既有统计接口、卡片组件自持四种状态、概览页只负责挂载、
 * 统计词条四语齐全且 ja/ko 给出本地译文、store 引用的每个指标 key 在四语里都真实存在。
 *
 * S03 修订：同一 store 开始承载按身份隔离的收藏与最近访问，因此原「store 不写本地存储」
 * 与「概览页不引入 store」两条否定断言改为正向约束（落盘只有偏好读写两处、统计切片与页面取数路径不变），
 * 判据强度不降，断言一条未删。
 *
 * 已知范围缺口（见 unfinished-tasks.md 的 WP-05.S02-M1）：本页仍未被路由记录挂载，
 * 且本轮无应用栈可跑真实后端，故这里只证明前端取数与状态语义，不声称运行时或截图证据。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/workspace-stats.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import type { WorkspaceStatsCardId } from '../../src/stores/workspace.ts'
import type { ErrorDisplay } from '../../src/utils/error-display.ts'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const vue = require('vue') as typeof import('vue')
const pinia = require('pinia') as typeof import('pinia')

/** 受控响应信封，形状与 `src/types/api.ts` 的 `ApiResponse` 一致。 */
interface Envelope {
  code: number
  message: string
  data: unknown
}

/** 一次被记录的接口调用及其脚本化结局。 */
interface PendingCall {
  cardId: WorkspaceStatsCardId
  succeed(data: unknown): void
  fail(error: unknown): void
}

/** 本步断言所需的 store 可见形状。 */
interface CardView {
  status: string
  metrics: { id: string; labelKey: string; value: number | null }[]
  display: ErrorDisplay | null
}

interface WorkspaceStoreView {
  cards: Record<WorkspaceStatsCardId, CardView>
  loadCard(cardId: WorkspaceStatsCardId): Promise<void>
}

/** 四语断言用到的 `workspace.stats` 键集合。 */
const STATS_KEYS = [
  'jobsTitle',
  'workflowTitle',
  'totalJobs',
  'todayExecuted',
  'todayFailed',
  'myInitiatedRunning',
  'myInitiatedTotal',
  'empty',
  'unavailable',
]

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

/**
 * 把 vm 领域产出的数组规范化为宿主值。
 *
 * vm 上下文里的 `[]`/`{}` 携带该领域自身的原型，`assert.deepEqual` 的严格原型比较会把结构相同的两侧判为不等，
 * 因此只携带 id/labelKey/value 标量信息的指标数组先做无类型损失的规范化再比较；数值与文案仍来自真实执行结果。
 */
function plainValues<T>(value: readonly T[]): T[] {
  return JSON.parse(JSON.stringify(value)) as T[]
}

const storeSource = read('../../src/stores/workspace.ts')
const cardSource = read('../../src/components/workspace/WorkspaceStatsCard.vue')
const overview = read('../../src/views/workspace/index.vue')
const zhCN = read('../../src/locales/zh-CN.ts')
const enUS = read('../../src/locales/en-US.ts')
const jaJP = read('../../src/locales/ja-JP.ts')
const koKR = read('../../src/locales/ko-KR.ts')
const LOCALES: readonly [string, string][] = [['zh-CN', zhCN], ['en-US', enUS], ['ja-JP', jaJP], ['ko-KR', koKR]]

/** 取 `<script setup>` 区块，用于断言只在逻辑层成立的约束。 */
function scriptBlock(source: string): string {
  const start = source.indexOf('<script setup')
  const end = source.indexOf('</script>')
  assert.notEqual(start, -1, '组件应包含 script setup 区块')
  return source.slice(start, end)
}

/** 取 workspace.stats 区块文本，用于逐语言核对键集合。 */
function statsBlock(localeSource: string): string {
  const start = localeSource.indexOf('    stats: {', localeSource.indexOf('  workspace: {'))
  assert.notEqual(start, -1, 'workspace 下应存在 stats 区块')
  const end = localeSource.indexOf('\n    },', start)
  assert.notEqual(end, -1, 'stats 区块应正确闭合')
  return localeSource.slice(start, end)
}

/**
 * 判断词条叶子键是否在该语言文件里真实出现。
 *
 * ko 采用「一行多键」的紧凑写法，行锚定会漏判真实存在的键，故只要求键名前是块起始或分隔符。
 */
function hasLocaleKey(localeSource: string, leaf: string): boolean {
  return new RegExp(`(^|[{,\\s])${leaf}:\\s*['"\`]`).test(localeSource)
}

/** 展示层用到的既有词条；其余 key 原样返回，用于触发 resolveDisplayText 的降级分支。 */
const DISPLAY_TEXTS: Record<string, string> = {
  'request.forbiddenMessage': 'controlled-forbidden-text',
  'request.networkError': 'controlled-network-text',
  'request.timeout': 'controlled-timeout-text',
  'request.serverError': 'controlled-server-text',
  'request.requestFailed': 'controlled-generic-failed-text',
  'request.unknownError': 'controlled-last-resort-text',
  'request.traceHint': 'trace:{traceId}',
}

/** 替身 `translate()`：只查表，缺失时原样返回 key（与真实实现的缺键行为一致）。 */
function controlledTranslate(key: string, params: Record<string, string | number> = {}): string {
  const text = DISPLAY_TEXTS[key]
  if (text === undefined) return key
  return text.replace(/\{(\w+)\}/g, (_match, name: string) => String(params[name] ?? ''))
}

/**
 * 构造仅带码值与响应头的假 Axios 错误。
 *
 * `message` 故意写成技术串，用于证明判定与展示都不读取任何自然语言文案。
 */
function axiosError(status: number | null, options: { code?: string; data?: unknown; headers?: Record<string, string> } = {}): unknown {
  return {
    isAxiosError: true,
    name: 'AxiosError',
    message: 'technical axios message must never reach the ui',
    code: options.code,
    config: { url: '/base/my-job/stats', headers: { Authorization: 'Bearer controlled-session' } },
    response: status === null ? undefined : { status, data: options.data, headers: options.headers ?? {} },
  }
}

/**
 * 在 vm 模块图中执行真实 store。
 *
 * 只替换接口、身份与权限 store、i18n 与 axios 判定谓词；store 函数体与错误分类代码不复制、不改写。
 */
async function createHarness(options: { token?: string; permissions?: string[] } = {}) {
  const context = vm.createContext({ Promise, setTimeout, clearTimeout })
  const user = vue.reactive({ token: options.token ?? 'tenant-8-admin' })
  const permission = vue.reactive({ permissions: options.permissions ?? ['workflow'] })
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
    '@/i18n': synthetic({ translate: controlledTranslate }),
    '@/stores/user': synthetic({ useUserStore: () => user }),
    '@/stores/permission': synthetic({ usePermissionStore: () => permission }),
    '@/api/myJob': synthetic({ getMyJobStats: () => enqueue('jobs') }),
    '@/api/workflow': synthetic({ getWorkspaceStats: () => enqueue('workflow') }),
    '@/utils/error-display': real('../../src/utils/error-display.ts'),
    '@/utils/errors': real('../../src/utils/errors.ts'),
    // S03 起 store 用真实 jwt 解析稳定身份，因此这里同样载入真实 jwt 而不是替身。
    '@/utils/jwt': real('../../src/utils/jwt.ts'),
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

  const instance = pinia.createPinia()
  pinia.setActivePinia(instance)
  const namespace = workspaceModule.namespace as unknown as { useWorkspaceStore: () => WorkspaceStoreView }
  const store = namespace.useWorkspaceStore()

  /** 取该卡最近一次调用，断言前自动校验调用次数。 */
  function lastCall(cardId: WorkspaceStatsCardId): PendingCall {
    const call = [...calls].reverse().find((entry) => entry.cardId === cardId)
    assert.ok(call, `缺少 ${cardId} 卡请求`)
    return call
  }

  /** 结算该卡最近一次调用为成功响应。 */
  function succeed(cardId: WorkspaceStatsCardId, data: unknown): void {
    lastCall(cardId).succeed(data)
  }

  /** 结算该卡最近一次调用为失败。 */
  function fail(cardId: WorkspaceStatsCardId, error: unknown): void {
    lastCall(cardId).fail(error)
  }

  /** 该卡已发出的请求次数。 */
  function countOf(cardId: WorkspaceStatsCardId): number {
    return calls.filter((entry) => entry.cardId === cardId).length
  }

  return {
    store,
    user,
    permission,
    calls,
    succeed,
    fail,
    countOf,
    card: (cardId: WorkspaceStatsCardId) => store.cards[cardId],
    /** 用真实展示层把描述符解析为当前语言文案。 */
    text: (display: ErrorDisplay) => errorsNamespace.resolveDisplayText(display),
  }
}

type Harness = Awaited<ReturnType<typeof createHarness>>

/** 等待 store 侧 Promise 链走完。 */
async function settle(cardId: WorkspaceStatsCardId, harness: Harness, data: unknown): Promise<void> {
  const task = harness.store.loadCard(cardId)
  harness.succeed(cardId, data)
  await task
}

/** 未登录时不请求任务统计，但有 workflow 权限仍请求流程统计。 */
test('未登录不发任务统计请求，流程统计仍按权限请求', async () => {
  const h = await createHarness({ token: '' })
  const jobsTask = h.store.loadCard('jobs')
  await jobsTask
  assert.equal(h.card('jobs').status, 'unavailable')
  assert.equal(h.countOf('jobs'), 0, '未登录不得发送该业务请求')
  assert.deepEqual(plainValues(h.card('jobs').metrics), [])
  assert.equal(h.card('jobs').display, null)

  const workflowTask = h.store.loadCard('workflow')
  assert.equal(h.countOf('workflow'), 1)
  h.succeed('workflow', { todoCount: 2, myInitiatedRunning: 1, myInitiatedTotal: 4 })
  await workflowTask
  assert.equal(h.card('workflow').status, 'loaded')
})

/** 权限清单里没有 workflow 码时判为不可用，并且一次请求都不发。 */
test('权限里没有 workflow 码时不请求流程统计', async () => {
  const h = await createHarness({ permissions: ['base:user:list', 'srm:portal:profile'] })
  const task = h.store.loadCard('workflow')
  await task
  assert.equal(h.card('workflow').status, 'unavailable')
  assert.equal(h.countOf('workflow'), 0)
  assert.deepEqual(plainValues(h.card('workflow').metrics), [])
  assert.equal(h.card('workflow').display, null)
})

/** `workflow:` 子码同样放行：判定按权限码前缀而非文案。 */
test('workflow 子码放行流程统计请求', async () => {
  const h = await createHarness({ permissions: ['workflow:todo:query'] })
  const task = h.store.loadCard('workflow')
  assert.equal(h.countOf('workflow'), 1)
  h.succeed('workflow', { todoCount: 1, myInitiatedRunning: 0, myInitiatedTotal: 0 })
  await task
  assert.equal(h.card('workflow').status, 'loaded')
  assert.deepEqual(plainValues(h.card('workflow').metrics.map((metric) => metric.value)), [1, 0, 0])
})

/** 判定改为可用之前重试也不发请求，改为可用之后并发的两次调用只共用一个请求。 */
test('unavailable 卡重试不发请求直到判定通过', async () => {
  const h = await createHarness({ permissions: [] })
  await h.store.loadCard('workflow')
  await h.store.loadCard('workflow')
  assert.equal(h.countOf('workflow'), 0)
  h.permission.permissions = ['workflow']
  const first = h.store.loadCard('workflow')
  const second = h.store.loadCard('workflow')
  assert.equal(second, first, '同一张卡的并发载入应复用进行中的请求')
  assert.equal(h.countOf('workflow'), 1, '并发重试只应发一次请求')
  h.succeed('workflow', { todoCount: 0, myInitiatedRunning: 0, myInitiatedTotal: 0 })
  await Promise.all([first, second])
  assert.equal(h.card('workflow').status, 'empty')
})

/** 真实零值判为 empty，非零判为 loaded，两者都不是失败。 */
test('全零判 empty 且非零判 loaded', async () => {
  const h = await createHarness()
  await settle('jobs', h, { totalJobs: 0, todayExecuted: 0, todayFailed: 0 })
  assert.equal(h.card('jobs').status, 'empty')
  assert.equal(h.card('jobs').metrics.length, 3)
  assert.equal(h.card('jobs').display, null)

  await settle('jobs', h, { totalJobs: 3, todayExecuted: 0, todayFailed: 0 })
  assert.equal(h.card('jobs').status, 'loaded')
  assert.deepEqual(plainValues(h.card('jobs').metrics.map((metric) => metric.value)), [3, 0, 0])
})

/** 字段缺失或类型异常保留 null，绝不写成 0。 */
test('缺失字段保留 null 而不是补 0', async () => {
  const h = await createHarness()
  await settle('jobs', h, { totalJobs: 5, todayExecuted: 'NaN-ish', todayFailed: null })
  const metrics = h.card('jobs').metrics
  assert.equal(h.card('jobs').status, 'loaded')
  assert.deepEqual(plainValues(metrics.map((metric) => metric.value)), [5, null, null])
  assert.equal(metrics[1]?.labelKey, 'workspace.stats.todayExecuted')
})

/** 响应形态不可用判为失败：既不给数字也不给 0。 */
test('响应形态不可用判 failed 不产出数值', async () => {
  const h = await createHarness()
  const malformed: unknown[] = [null, {}, { totalJobs: 'x' }]
  for (const payload of malformed) {
    await settle('jobs', h, payload)
    assert.equal(h.card('jobs').status, 'failed', `响应形态不可用应判失败：${JSON.stringify(payload)}`)
    assert.deepEqual(plainValues(h.card('jobs').metrics), [])
  }
})

/** 403 归一化为权限语义，带 trace，文案不含后端原文与 Axios 技术串。 */
test('403 落到权限语义且文案不含后端原文', async () => {
  const h = await createHarness()
  const task = h.store.loadCard('workflow')
  h.fail('workflow', axiosError(403, {
    data: { code: 403, message: '没有权限的中文原文', data: null },
    headers: { 'X-Trace-Id': 'trace-403-abc' },
  }))
  await task
  const card = h.card('workflow')
  assert.equal(card.status, 'failed')
  assert.equal(card.display?.kind, 'permissionDenied')
  assert.equal(card.display?.i18nKey, 'request.forbiddenMessage')
  assert.equal(card.display?.traceId, 'trace-403-abc')
  assert.equal(card.display?.shouldLogout, false, '403 不得触发登出')
  const text = h.text(card.display as ErrorDisplay)
  assert.match(text, /controlled-forbidden-text/)
  assert.match(text, /trace-403-abc/)
  assert.doesNotMatch(text, /没有权限|technical axios message/)
})

/** 断网、超时、500 三种失败各自分类，互不混同。 */
test('网络失败超时与服务端错误分类互不混同', async () => {
  const cases: [unknown, string][] = [
    [axiosError(null, { code: 'ERR_NETWORK' }), 'network'],
    [axiosError(null, { code: 'ECONNABORTED' }), 'timeout'],
    [axiosError(500), 'serverError'],
  ]
  const h = await createHarness()
  for (const [error, kind] of cases) {
    const task = h.store.loadCard('jobs')
    h.fail('jobs', error)
    await task
    const display = h.card('jobs').display as ErrorDisplay
    assert.equal(h.card('jobs').status, 'failed')
    assert.equal(display.kind, kind)
    assert.doesNotMatch(h.text(display), /technical axios message/)
  }
})

/** 一张卡失败不清空另一张卡的成功结果，也不影响其状态。 */
test('一卡失败不清空另一卡的成功数据', async () => {
  const h = await createHarness()
  const jobsTask = h.store.loadCard('jobs')
  const workflowTask = h.store.loadCard('workflow')
  assert.deepEqual([h.countOf('jobs'), h.countOf('workflow')], [1, 1])
  h.succeed('jobs', { totalJobs: 7, todayExecuted: 2, todayFailed: 1 })
  await jobsTask
  h.fail('workflow', axiosError(403))
  await workflowTask

  assert.equal(h.card('jobs').status, 'loaded')
  assert.deepEqual(plainValues(h.card('jobs').metrics.map((metric) => metric.value)), [7, 2, 1])
  assert.equal(h.card('workflow').status, 'failed')
})

/** 重试只重发本卡：另一卡请求次数与状态都不动。 */
test('重试只重发本卡', async () => {
  const h = await createHarness()
  await settle('jobs', h, { totalJobs: 1, todayExecuted: 0, todayFailed: 0 })
  const workflowTask = h.store.loadCard('workflow')
  h.fail('workflow', axiosError(null, { code: 'ERR_NETWORK' }))
  await workflowTask
  const before = h.countOf('workflow')

  const retryTask = h.store.loadCard('jobs')
  assert.equal(h.countOf('jobs'), 2)
  assert.equal(h.countOf('workflow'), before, '重试本卡不得连带请求另一卡')
  h.succeed('jobs', { totalJobs: 2, todayExecuted: 1, todayFailed: 0 })
  await retryTask

  assert.equal(h.card('jobs').status, 'loaded')
  assert.equal(h.card('workflow').status, 'failed')
  assert.equal(h.card('workflow').display?.kind, 'network')
})

/** store 只用两个既有统计接口，不新增聚合接口也不直连传输层。 */
test('store 只调用既有统计接口', () => {
  assert.match(storeSource, /import \{ getMyJobStats \} from '@\/api\/myJob'/)
  assert.match(storeSource, /import \{ getWorkspaceStats \} from '@\/api\/workflow'/)
  assert.equal(storeSource.match(/^import \{ get.*\} from '@\/api\/.*$/gm)?.length, 2)
  assert.doesNotMatch(storeSource, /request\.|axios|XMLHttpRequest|fetch\(/)
  assert.doesNotMatch(storeSource, /\/stats\/[A-Za-z-]*aggregate|aggregate[A-Z]/)
  // 只读取统计，不写任何业务状态；动词判定要求带 URL 字面量，避免把 Map.delete 误判成 HTTP DELETE。
  assert.doesNotMatch(storeSource, /\.(post|put|patch|delete|get)\(\s*['"`]/)
  // S03 让同一 store 承载按身份隔离的偏好记录，「store 完全不写本地存储」已不成立；
  // 判据按新范围收紧为正向约束：落盘点只有偏好的读与写两处，统计切片仍不碰任何存储。
  const statsSlice = storeSource.slice(0, storeSource.indexOf('/** 当前身份的偏好作用域键'))
  assert.doesNotMatch(statsSlice, /localStorage|sessionStorage/)
  assert.doesNotMatch(storeSource, /sessionStorage/)
  assert.equal(storeSource.match(/localStorage\.(setItem|getItem)\(/g)?.length, 2, '本地存储只允许偏好读写各一次')
})

/** store 不引用菜单接口，因此单卡重试与失败都不会重载菜单。 */
test('store 不触碰菜单加载', () => {
  assert.doesNotMatch(storeSource, /api\/menu|loadMenus|retryLoadMenus|menuLoadState/)
  assert.doesNotMatch(cardSource, /api\/menu|loadMenus|retryLoadMenus/)
})

/** 卡片组件自持四种非成功状态，并把 null 值渲染为占位符而不是 0。 */
test('卡片组件独立渲染四种状态且 null 显示占位符', () => {
  const body = scriptBlock(cardSource)
  assert.match(body, /onMounted\(load\)/)
  assert.match(body, /void workspaceStore\.loadCard\(props\.cardId\)/)
  assert.match(body, /const isBusy = computed\(\(\) => card\.value\.status === 'idle' \|\| card\.value\.status === 'loading'\)/)
  assert.match(cardSource, /v-if="isBusy" class="ws-card__status" role="status">\{\{ t\('common\.loading'\) \}\}/)
  assert.match(cardSource, /card\.status === 'unavailable'"\s*class="ws-card__status">\{\{ t\('workspace\.stats\.unavailable'\) \}\}/)
  assert.match(cardSource, /card\.status === 'empty'"\s*class="ws-card__status">\{\{ t\('workspace\.stats\.empty'\) \}\}/)
  assert.match(cardSource, /card\.status === 'failed'[\s\S]{0,240}role="alert"/)
  assert.match(cardSource, /\{\{ metric\.value === null \? '—' : metric\.value \}\}/)
})

/** 失败文案只由归一化描述符解析，本地异常退回通用词条，绝不读取后端或 Axios 文本。 */
test('失败文案只取归一化描述符', () => {
  const body = scriptBlock(cardSource)
  assert.match(body, /card\.value\.display === null \? t\('request\.requestFailed'\) : resolveDisplayText\(card\.value\.display\)/)
  assert.doesNotMatch(body, /technicalDetail|\.message|error\b.*\.data/)
  assert.doesNotMatch(cardSource, /<el-statistic/)
})

/** 驾驶舱复用两张统计卡；操作后刷新仍由同一 Store 管理失败与并发。 */
test('驾驶舱统计来自原 Store，操作刷新不引入第二份接口或状态', () => {
  const body = scriptBlock(overview)
  assert.match(overview, /<WorkspaceStatsCard card-id="jobs" title-key="workspace\.stats\.jobsTitle" \/>/)
  assert.match(overview, /<WorkspaceStatsCard card-id="workflow" title-key="workspace\.stats\.workflowTitle" \/>/)
  assert.equal(overview.match(/<WorkspaceStatsCard/g)?.length, 2)
  assert.doesNotMatch(body, /getMyJobStats|getWorkspaceStats|api\/myJob|api\/workflow|cards\.value/)
  assert.match(body, /workspaceStore\.loadCard\(cardId\)/)
  assert.match(overview, /@changed="refreshStats\('jobs'\)"/)
  assert.match(overview, /@changed="refreshStats\('workflow'\)"/)
  assert.equal(overview.match(/aria-labelledby="ws-overview-/g)?.length, 5, 'S01 的五张卡标题不受影响')
})

/** 统计词条四语齐全，ja/ko 逐键给出本地译文而不是整块沿用 enUS。 */
test('统计词条四语齐全', () => {
  for (const [name, source] of LOCALES) {
    const block = statsBlock(source)
    for (const key of STATS_KEYS) {
      assert.match(block, new RegExp(`^\\s+${key}: `, 'm'), `${name} 缺少 workspace.stats.${key}`)
    }
  }
  for (const source of [jaJP, koKR]) {
    const block = statsBlock(source)
    for (const key of STATS_KEYS) {
      const line = block.match(new RegExp(`^\\s+${key}: '([^']*)'`, 'm'))?.[1] ?? ''
      assert.ok(line.length > 0, `缺少 ${key} 的本地译文`)
      assert.doesNotMatch(line, /[a-z] [a-z]/, `${key} 译文不应是英文句子`)
    }
  }
  // 不新增顶层键：因此 scaffold/catalog/modules.yaml 无需随之变更。
  for (const source of [zhCN, enUS, jaJP, koKR]) {
    assert.doesNotMatch(source, /^ {2}workspaceStats:/m)
  }
})

/** store 引用的每个指标 key 在四语里都真实存在，缺键即失败而不是回退中文原文。 */
test('指标词条 key 在四语里都存在', () => {
  const keys = [...storeSource.matchAll(/labelKey: '([^']+)'/g)].map((match) => match[1])
  assert.deepEqual(keys, [
    'workspace.stats.totalJobs',
    'workspace.stats.todayExecuted',
    'workspace.stats.todayFailed',
    'workflow.todoCount',
    'workspace.stats.myInitiatedRunning',
    'workspace.stats.myInitiatedTotal',
  ])
  const titleKeys = [...overview.matchAll(/title-key="([^"]+)"/g)].map((match) => match[1])
  assert.deepEqual(titleKeys, ['workspace.stats.jobsTitle', 'workspace.stats.workflowTitle'])
  for (const key of [...keys, ...titleKeys]) {
    const leaf = key.split('.').pop() as string
    for (const [name, source] of LOCALES) {
      assert.ok(hasLocaleKey(source, leaf), `${name} 缺少词条 ${key}`)
    }
  }
})
