/**
 * @module navigation/recent-visit-hook.test
 * @description WP-05.S04「只在成功访问可用业务路由后登记最近访问」的定向测试（node:test，不依赖浏览器）。
 *
 * 行为层在 vm 模块图里执行**真实**的 `src/stores/workspace.ts` 与真实 `src/utils/jwt.ts`，
 * 只把接口、身份 store、权限 store、i18n/axios 与 `localStorage` 换成受控替身，并把受控菜单树交给 `recordVisit`：
 * - 只有已授权菜单树里真实存在、且类型为 `MENU`、带正整数稳定 ID 的叶子会被登记，目录、异常 ID 与树外权限码一律拒绝；
 * - 权限码缺失（静态路由、认证回调、403/404 兜底）与非字符串值都不写入；
 * - 新标签首访先按当前身份读回历史再前置追加，不拿空列表覆盖已存记录；刷新后从同一个键读回同一份顺序；
 * - 稳定 ID 去重、按访问先后倒序并裁到十二项，且不改动收藏数组；
 * - 身份缺失时零写入、不落公共命名空间；存储损坏时降级为空后只留本次一项；
 * - 持久化内容只有版本号与两个稳定 ID 数组，权限码、路径、query、hash、令牌都不入库。
 *
 * 结构层核对「写入方只有一个且只能在导航确认后触发」这一只能落在源码事实上的判据：
 * 登记只发生在 `router.afterEach` 且先按 failure 短路，`beforeEach` 内不含任何登记调用，
 * 钩子只做裸 `return` 因而无法取消或改写导航（原生 `<a>`、前进后退与既有守卫规则不变），
 * 静态路由表不携带 `permissionCode`，消费方只读记录不写记录。
 *
 * 已知范围缺口（见 unfinished-tasks.md 的 WP-05.S01-M1）：概览页仍未被路由记录挂载，也没有运行中的 omni-stack 应用栈，
 * 因此本步只证明登记语义与钩子位置，不声称真实浏览器里的访问登记、刷新与新标签首访已取得运行时或截图证据。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/recent-visit-hook.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'
import type { WorkspacePreferences } from '../../src/stores/workspace.ts'

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

/** 本步断言所需的 store 可见形状（pinia setup store 已自动解包 ref/computed）。 */
interface VisitStoreView {
  preferences: WorkspacePreferences
  preferenceKey: string | null
  favoriteIds: string[]
  recentIds: string[]
  loadPreferences(): void
  toggleFavorite(id: string): boolean
  recordVisit(menus: readonly MenuLike[], permissionCode: string | null | undefined): boolean
}

/** 受控 `localStorage` 替身：只记录被访问到的键，供「不写公共键」判据取证。 */
interface StorageView {
  values: Map<string, string>
  writes: string[]
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

/** 作用域键前缀与最近访问上限各自只有一个来源，判据要求按字面量核对。 */
const PREFIX = 'omni-workspace-preferences'
const RECENT_LIMIT = 12

/** 身份有效时的规范键形状。 */
const SCOPED_KEY = new RegExp(`^${PREFIX}:\\d+:\\d+$`)

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

/**
 * 构造只含 ASCII claim 的假 JWT：`decodeJwt` 只解码不验签，这里要的是真实解析路径而不是替身解析。
 */
function makeToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.controlled-signature`
}

/** 造一个只实现本步用到的两个方法的 `localStorage` 替身。 */
function createStorage(seed: Record<string, string> = {}): StorageView & { keys(): string[]; raw(key: string): string | undefined } {
  const values = new Map<string, string>(Object.entries(seed))
  const writes: string[] = []
  return {
    values,
    writes,
    getItem: (key: string) => (values.has(key) ? values.get(key) as string : null),
    setItem: (key: string, value: string) => {
      writes.push(key)
      values.set(key, value)
    },
    keys: () => [...values.keys()],
    raw: (key: string) => values.get(key),
  }
}

/**
 * 在 vm 模块图里执行真实 store。
 *
 * 只替换接口、身份与权限 store、i18n/axios 与 `localStorage`；菜单树由调用方逐次传入，store 自己不取菜单。
 */
async function createHarness(options: { token?: string; seed?: Record<string, string> } = {}) {
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
  const permission = vue.reactive({ permissions: ['workflow'] })

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

  /** 统计接口在本步不参与判定，只提供可 awaiting 的空响应。 */
  function emptyStats(): Promise<{ data: { code: number; message: string; data: unknown } }> {
    return Promise.resolve({ data: { code: 200, message: 'success', data: null } })
  }

  const modules: Record<string, vm.Module> = {
    vue: synthetic({ ...vue }),
    pinia: synthetic({ ...pinia }),
    axios: synthetic({ isAxiosError: (value: unknown): boolean => value instanceof Error && value.name === 'AxiosError' }),
    '@/i18n': synthetic({ translate: (key: string) => key }),
    '@/stores/user': synthetic({ useUserStore: () => user }),
    '@/stores/permission': synthetic({ usePermissionStore: () => permission }),
    '@/api/myJob': synthetic({ getMyJobStats: () => emptyStats() }),
    '@/api/workflow': synthetic({ getWorkspaceStats: () => emptyStats() }),
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

  pinia.setActivePinia(pinia.createPinia())
  const namespace = workspaceModule.namespace as unknown as { useWorkspaceStore: () => VisitStoreView }
  const store = namespace.useWorkspaceStore()

  return {
    store,
    user,
    storage,
    /** 切换身份并等 store 自身的 watch 把内存记录换成新作用域。 */
    switchIdentity: async (claims: Record<string, unknown>): Promise<void> => {
      user.token = makeToken(claims)
      user.username = String(claims.username ?? 'controlled-user')
      await vue.nextTick()
    },
    /** 写入过的作用域键（去重），用于核对身份缺失时零写入。 */
    writtenKeys: (): string[] => [...new Set(storage.writes)],
  }
}

type Harness = Awaited<typeof createHarness>

/** 有效身份的最小 claim 集。 */
const TOKEN_A = { sub: '101', tenant_id: 1, username: 'alice' }

/** 同租户另一用户，用于证明登记只写当前身份的键。 */
const TOKEN_B = { sub: '202', tenant_id: 1, username: 'bob' }

/** 取带身份令牌的作用域键字符串。 */
function scopedKey(claims: { sub?: string; tenant_id?: number }): string {
  return `${PREFIX}:${claims.tenant_id}:${claims.sub}`
}

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

/** 取当前落盘的最近访问数组。 */
function storedRecents(h: Harness): string[] {
  const raw = h.storage.raw(scopedKey(TOKEN_A))
  return raw === undefined ? [] : (JSON.parse(raw).recents as string[])
}

/** 用同一份磁盘内容另起一个 harness，等价于换标签或刷新后组件重新挂载。 */
async function reopen(h: Harness): Promise<Harness> {
  const key = scopedKey(TOKEN_A)
  const raw = h.storage.raw(key)
  return await createHarness({ token: makeToken(TOKEN_A), seed: raw === undefined ? {} : { [key]: raw } })
}

test('只登记已授权菜单树里真实存在的 MENU 叶子，目录、异常 ID 与树外权限码一律拒绝', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  const menus = menuTree()

  assert.equal(h.store.recordVisit(menus, 'system:user'), true, '已授权叶子应登记')
  assert.equal(h.store.recordVisit(menus, 'system:online'), true, '二级目录下的叶子也要能找到')
  assert.equal(h.store.recordVisit(menus, 'crm'), true, '顶层 MENU 叶子同样可登记')
  assert.deepEqual([...h.store.recentIds], ['2000', '1004', '1001'], '按访问先后倒序')

  for (const directory of ['system', 'system:monitor']) {
    assert.equal(h.store.recordVisit(menus, directory), false, `目录 ${directory} 不是可进入页面，不得登记`)
  }
  for (const absent of ['asset:asset', 'srm:portal:profile', 'workflow', 'system:nope']) {
    assert.equal(h.store.recordVisit(menus, absent), false, `树外权限码 ${absent} 不得登记`)
  }
  // 菜单数据异常时也不登记，最近访问里永远不会出现假页面。
  const broken: MenuLike[] = [
    { id: 0, type: 'MENU', permissionCode: 'zero:id' },
    { id: -3, type: 'MENU', permissionCode: 'negative:id' },
    { id: 1.5, type: 'MENU', permissionCode: 'fraction:id' },
  ]
  for (const code of ['zero:id', 'negative:id', 'fraction:id']) {
    assert.equal(h.store.recordVisit(broken, code), false, `非正整数稳定 ID ${code} 不得登记`)
  }
  assert.deepEqual([...h.store.recentIds], ['2000', '1004', '1001'], '被拒绝的登记不得改动记录')
})

test('权限码缺失或非字符串（静态路由、认证回调、403/404 兜底）不写入任何记录', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  const menus = menuTree()
  const missing: unknown[] = [undefined, null, '', '   ', 1001, {}, []]
  for (const code of missing) {
    assert.equal(
      h.store.recordVisit(menus, code as string | null | undefined),
      false,
      `${JSON.stringify(code)} 不是有效权限码，不得登记`,
    )
  }
  assert.deepEqual([...h.store.recentIds], [])
  assert.deepEqual(h.writtenKeys(), [], '无有效权限码时不得发生任何落盘写入')
})

test('新标签首访先读回历史再前置追加，不拿空列表覆盖已存记录', async () => {
  const key = scopedKey(TOKEN_A)
  const h = await createHarness({
    token: makeToken(TOKEN_A),
    seed: { [key]: '{"version":1,"favorites":["2000"],"recents":["1002","1001"]}' },
  })
  // 全程不调用 loadPreferences，模拟深链接直接抵达业务路由。
  assert.equal(h.store.recordVisit(menuTree(), 'system:online'), true)
  assert.deepEqual([...h.store.recentIds], ['1004', '1002', '1001'], '已存记录必须保留在追加结果之后')
  assert.deepEqual(storedRecents(h), ['1004', '1002', '1001'])
  assert.deepEqual(JSON.parse(h.storage.raw(key) as string).favorites, ['2000'], '登记不得改动收藏数组')
})

test('刷新后从同一个键读回同一份顺序，重复访问只留最新位置', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  const menus = menuTree()
  h.store.recordVisit(menus, 'system:user')
  h.store.recordVisit(menus, 'system:role')
  h.store.recordVisit(menus, 'system:online')
  assert.deepEqual([...h.store.recentIds], ['1004', '1002', '1001'])

  h.store.recordVisit(menus, 'system:user')
  assert.deepEqual([...h.store.recentIds], ['1001', '1004', '1002'], '重复访问去重并移到最前')
  assert.deepEqual([...new Set(h.storage.writes)], [scopedKey(TOKEN_A)], '成功登记只写当前身份的一个键')
  assert.equal(h.storage.writes.length, 4, '四次成功登记各落盘一次')

  const reloaded = await reopen(h)
  assert.deepEqual([...reloaded.store.recentIds], [], '未读取前内存记录为空')
  reloaded.store.loadPreferences()
  assert.deepEqual([...reloaded.store.recentIds], ['1001', '1004', '1002'], '刷新后读回同一份顺序')
  assert.equal(reloaded.store.recordVisit(menuTree(), 'system:role'), true, '刷新后仍可登记')
  assert.deepEqual([...reloaded.store.recentIds], ['1002', '1001', '1004'], '刷新后的登记继续前置')
})

test('连续访问十五个页面只保留最近十二项，被裁掉的旧项不留下', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  const menus: MenuLike[] = Array.from({ length: 15 }, (_unused, index) => ({
    id: 3000 + index,
    type: 'MENU',
    permissionCode: `page:${index + 1}`,
  }))
  for (let index = 0; index < 15; index += 1) {
    assert.equal(h.store.recordVisit(menus, `page:${index + 1}`), true)
  }
  assert.equal(h.store.recentIds.length, RECENT_LIMIT, `只应保留最近 ${RECENT_LIMIT} 项`)
  assert.deepEqual([...h.store.recentIds.slice(0, 3)], ['3014', '3013', '3012'])
  assert.ok(!h.store.recentIds.includes('3000') && !h.store.recentIds.includes('3001'), '最旧两项应被裁掉')
  assert.deepEqual(storedRecents(h).length, RECENT_LIMIT, '落盘内容同样只保留十二项')
})

test('权限变化后失权的页面不再登记，陈旧 ID 只由消费方过滤而不被反复写入', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  const menus = menuTree()
  assert.equal(h.store.recordVisit(menus, 'system:online'), true)

  // 等价于预设裁剪或授权收回后的菜单树：一个页面降级为目录，另一个页面整体从授权树消失。
  const revoked: MenuLike[] = [
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
  ]
  assert.equal(revoked[0].children?.some((child) => child.permissionCode === 'system:role'), false)
  assert.equal(h.store.recordVisit(revoked, 'system:online'), false, '叶子降级为目录后不得再登记')
  assert.equal(h.store.recordVisit(revoked, 'system:role'), false, '已从授权树移除的页面不得再登记')
  assert.deepEqual([...h.store.recentIds], ['1004'], '失权页面不会新增记录')
  assert.ok(!h.store.recentIds.includes('1002'), '失权 ID 不会被反复写入，残留项由可见集合求交过滤')
})

test('存储损坏时恢复为空状态后只留本次一项，且绝不回落到别的键', async () => {
  const key = scopedKey(TOKEN_A)
  const otherKey = `${PREFIX}:9:999`
  const h = await createHarness({
    token: makeToken(TOKEN_A),
    seed: { [key]: '{', [otherKey]: '{"version":1,"favorites":["SECRET"],"recents":["SECRET"]}' },
  })
  assert.equal(h.store.recordVisit(menuTree(), 'system:user'), true)
  assert.deepEqual([...h.store.recentIds], ['1001'], '损坏记录降级为空后只追加本次')
  assert.ok(!h.store.recentIds.includes('SECRET') && !h.store.favoriteIds.includes('SECRET'), '不得读到别的键')
  assert.equal(h.storage.raw(otherKey), '{"version":1,"favorites":["SECRET"],"recents":["SECRET"]}', '别的键不得被改动')
  assert.deepEqual(storedRecents(h), ['1001'])
})

test('身份缺失或换账号时登记只写当前身份的键，不落公共命名空间', async () => {
  const invalid: [string, string][] = [
    ['未登录空令牌', ''],
    ['非 JWT 字符串', 'opaque-token'],
    ['缺 sub', makeToken({ tenant_id: 1, username: 'alice' })],
    ['缺 tenant_id', makeToken({ sub: '101', username: 'alice' })],
    ['sub 非数字', makeToken({ sub: 'not-a-number', tenant_id: 1, username: 'alice' })],
  ]
  for (const [name, token] of invalid) {
    const h = await createHarness({ token })
    assert.equal(h.store.recordVisit(menuTree(), 'system:user'), false, `${name} 应拒绝登记`)
    assert.deepEqual([...h.store.recentIds], [], `${name} 不得有内存记录`)
    assert.deepEqual(h.writtenKeys(), [], `${name} 不得发生任何落盘写入`)
    assert.deepEqual(h.storage.keys().filter((key) => !SCOPED_KEY.test(key)), [], `${name} 不得落入公共命名空间`)
  }

  const h = await createHarness({ token: makeToken(TOKEN_A) })
  h.store.recordVisit(menuTree(), 'system:user')
  await h.switchIdentity(TOKEN_B)
  assert.equal(h.store.recordVisit(menuTree(), 'system:role'), true)
  assert.deepEqual([...h.store.recentIds], ['1002'], 'B 读到的是自己的记录，不是 A 的')
  assert.deepEqual(storedRecents(h), ['1001'], 'B 的登记不得覆盖 A 的记录')
  assert.deepEqual(h.writtenKeys().sort(), [scopedKey(TOKEN_A), scopedKey(TOKEN_B)].sort())
})

test('入库内容只有版本与稳定 ID，权限码、路径、query、hash、令牌都不落盘', async () => {
  const token = makeToken(TOKEN_A)
  const h = await createHarness({ token })
  h.store.recordVisit(menuTree(), 'system:user')
  h.store.toggleFavorite('2000')
  const raw = h.storage.raw(scopedKey(TOKEN_A)) as string
  assert.deepEqual(JSON.parse(raw), { version: 1, favorites: ['2000'], recents: ['1001'] })
  assert.ok(!raw.includes('system:user'), '权限码本身不入库，只存稳定 ID')
  assert.doesNotMatch(raw, /admin\/system|href|path|title|label|query|hash|redirect/i, '不得存路径或带授权参数的 URL')
  assert.doesNotMatch(raw, /token|jwt|authorization|secret|controlled-signature/i, '不得含凭据或令牌片段')
  assert.ok(!raw.includes(token.slice(0, 12)) && !raw.includes('alice'))
})

const storeSource = read('../../src/stores/workspace.ts')
const routerSource = read('../../src/router/index.ts')
const overview = read('../../src/views/workspace/index.vue')
const drawer = read('../../src/components/ApplicationDrawer.vue')

/** 取访问成功钩子整段（它是路由文件里最后一个钩子，到 `export default` 为止）。 */
function afterHookBlock(): string {
  const start = routerSource.indexOf('router.afterEach')
  const end = routerSource.indexOf('export default router', start)
  assert.ok(start > -1 && end > start, '路由里应存在访问成功钩子')
  return routerSource.slice(start, end)
}

/** 取前置守卫整段：终点取访问成功钩子的注释起始，避免把钩子自己的说明文字算进守卫。 */
function beforeHookBlock(): string {
  const start = routerSource.indexOf('router.beforeEach')
  const hook = routerSource.indexOf('router.afterEach')
  const end = routerSource.lastIndexOf('/**', hook)
  assert.ok(start > -1 && hook > -1 && end > start, '路由里应存在前置守卫')
  return routerSource.slice(start, end)
}

test('登记只发生在 afterEach，且先按失败标记短路，因此失败导航不新增', () => {
  const block = afterHookBlock()
  assert.match(block, /^router\.afterEach\(\(to, _from, failure\) => \{/)
  const guard = block.split('\n').find((line) => line.trim().startsWith('if (failure)'))
  assert.ok(guard !== undefined, '钩子首行应按 failure 短路')
  assert.equal(guard.trim(), 'if (failure) return', '失败时直接返回，不做任何登记')
  assert.match(block, /useWorkspaceStore\(\)\.recordVisit\(/, '登记只经由 store 的登记入口')
  assert.equal(block.match(/recordVisit\(/g)?.length, 1, '钩子里只有一个登记调用')
  assert.match(block, /usePermissionStore\(\)\.menuTree/, '菜单来源是当前账号的授权树，不在别处另取')
  // 路由文件里唯一的登记调用就在钩子里，前置守卫与动态注册都不抢先记录。
  assert.equal(routerSource.match(/recordVisit\(/g)?.length, 1)
  assert.doesNotMatch(beforeHookBlock(), /recordVisit|recordRecent|localStorage/, '不在 beforeEach 发起时抢先记访问')
})

test('访问成功钩子不取消也不改写导航，因此原生链接、前进后退与既有守卫规则不变', () => {
  const block = afterHookBlock()
  const returns = [...block.matchAll(/\breturn\b[^\n]*/g)].map((match) => match[0].trim())
  assert.ok(returns.length > 0, '钩子里应有失败短路语句')
  for (const statement of returns) {
    assert.equal(statement, 'return', '钩子只能提前退出，不能返回重定向目标或 false')
  }
  assert.doesNotMatch(block, /next\(|router\.push|replace\(|redirect|beforeResolve/)
  assert.doesNotMatch(block, /fullPath|to\.query|to\.hash|location\.href|window\./, '不读取也不记录完整 URL 或敏感参数')
  assert.doesNotMatch(routerSource, /localStorage/, '路由层不直接落盘，写入通道只有 store')
  // 既有守卫分支保持原样：本步没有替换或删减任何导航判定。
  assert.match(beforeHookBlock(), /if \(to\.meta\.requiresAuth === false\)/)
  assert.match(beforeHookBlock(), /return \{ name: 'MenuLoadError'/)
  assert.match(beforeHookBlock(), /to\.path\.startsWith\('\/admin'\) && !hasManagementPermission/)
})

test('静态路由与兜底页不携带权限码，因此不存在被登记的假页面', () => {
  const start = routerSource.indexOf('const staticRoutes')
  const end = routerSource.indexOf('// 创建路由实例')
  assert.ok(start > -1 && end > start, '应能定位静态路由表区间')
  const staticBlock = routerSource.slice(start, end)
  assert.doesNotMatch(staticBlock, /permissionCode/, '静态路由（含 /callback 与 403/404 兜底）不携带权限码')
  for (const name of ['OAuth2Callback', 'AdminAccessDenied', 'WorkspaceAccessDenied', 'NotFound']) {
    assert.ok(staticBlock.includes(`name: '${name}'`), `静态路由应包含 ${name}`)
  }
  // 动态注册只在 MENU 且能解析到视图时写入 permissionCode，登记与路由共用同一份可进入性判据。
  const registerBlock = routerSource.slice(
    routerSource.indexOf('function registerMenuRoute'),
    routerSource.indexOf('export function clearDynamicRoutes'),
  )
  assert.ok(registerBlock.length > 0, '应能定位动态注册函数区间')
  assert.match(registerBlock, /menu\.type === 'MENU'/)
  assert.match(registerBlock, /if \(component && !dynamicRouteNames\.has/)
  assert.match(registerBlock, /permissionCode: menu\.permissionCode/)
  assert.doesNotMatch(routerSource, /RECENT_LIMIT|slice\(0, 1[0-9]\)/, '十二项上限只有一个来源，在 store 里')
})

test('写入方只有一个：概览页与抽屉只读记录，不自行登记也不自行落盘', () => {
  const overviewBody = overview.slice(overview.indexOf('<script setup'), overview.indexOf('</script>'))
  const drawerBody = drawer.slice(drawer.indexOf('<script setup'), drawer.indexOf('</script>'))
  assert.ok(overviewBody.length > 0 && drawerBody.length > 0, '两个消费方都应有 script setup 区块')
  assert.match(overviewBody, /const recentEntries = computed\(\(\) => entriesOf\(workspaceStore\.recentIds\)\)/)
  assert.match(overviewBody, /\.filter\(\(entry\): entry is NavigationEntry => entry !== undefined\)/)
  for (const body of [overviewBody, drawerBody]) {
    assert.doesNotMatch(body, /recordVisit|recordRecent|localStorage|sessionStorage|defineStore/)
  }
  // store 的登记入口只做登记：不越界取菜单、不判权限，也不新增存储通道。
  assert.match(storeSource, /function recordVisit\([\s\S]*?const leaf = findVisitMenuLeaf\(menus, permissionCode\)/)
  assert.match(storeSource, /recordRecent\(String\(leaf\.id\)\)/)
  assert.doesNotMatch(storeSource, /api\/menu|loadMenus|retryLoadMenus|menuLoadState|hasPermission|checkPermi/)
  assert.equal(storeSource.match(/localStorage\.setItem\(/g)?.length, 1, '写入通道仍只有一个')
  assert.equal(storeSource.match(/localStorage\.getItem\(/g)?.length, 1, '读取通道仍只有一个')
})
