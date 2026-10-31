/**
 * @module navigation/workspace-preferences.test
 * @description WP-05.S03 收藏与最近访问的定向测试（node:test，不依赖浏览器、Vue 运行时组件或 Pinia 插件）。
 *
 * 行为层在 vm 模块图里执行**真实**的 `src/stores/workspace.ts` 与真实 `src/utils/jwt.ts`
 * （连同真实 vue 响应式与真实 pinia），只把接口、身份 store、权限 store、i18n/axios 与 `localStorage`
 * 换成受控替身，因此本步判据是跑出来的而不是读字符串读出来的：
 * - 作用域键只由 JWT 的 `tenant_id` 与 `sub` 派生，改用户名不动键，同名跨租户落在两个互不命中的键；
 * - A/B 用户互相看不到对方记录，切走再切回仍从自己的键读回自己的收藏；
 * - 未登录、令牌不可解、缺 claim、claim 非有限数一律判为无作用域，收藏返回 `false`、访问不入记录，
 *   且一次 `setItem` 都不发生，任何公共键也不出现；
 * - 损坏 JSON、非对象形态、非字符串与重复 ID 在读取阶段清洗，绝不回落到别的键；
 * - 收藏按加入先后稳定排列，取消后其余相对顺序不变；最近访问去重、倒序并裁到十二项；
 * - 落盘内容只有版本号与两个稳定 ID 数组，不含令牌、用户名、标题或路径。
 *
 * 结构层再核对「两处读同一份记录并按当前可见集合求交」这一判据只能落在源码事实上的部分：
 * 概览页与抽屉各自用统一导航集合过滤 ID、收藏写入前校验可见性（收藏不授予权限）、
 * 两处共用同一个 store 引用，条目组件只在收到 `favoriteIds` 时渲染星标；
 * 另加 `common.favorite`/`common.unfavorite` 四语齐备、星标图标已在既有插件里全局注册。
 *
 * 已知范围缺口（见 unfinished-tasks.md 的 WP-05.S01-M1）：概览页仍未被路由记录挂载，
 * 最近访问的写入方（路由访问成功钩子）属 WP-05.S04，因此这里只证明存储与读取语义，
 * 不声称运行时或截图证据，也不声称真实浏览器里两处渲染已比对。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/workspace-preferences.test.ts`（cwd=omni-frontend）。
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

/** 本步断言所需的 store 偏好可见形状（pinia setup store 已自动解包 ref/computed）。 */
interface PreferenceStoreView {
  preferences: WorkspacePreferences
  preferenceKey: string | null
  favoriteIds: string[]
  recentIds: string[]
  loadPreferences(): void
  isFavorite(id: string): boolean
  toggleFavorite(id: string): boolean
  recordRecent(id: string): void
}

/** 受控 `localStorage` 替身：只记录被访问到的键，供「不写公共键」判据取证。 */
interface StorageView {
  values: Map<string, string>
  writes: string[]
  getItem(key: string): string | null
  setItem(key: string, value: string): void
}

/** 作用域键的固定前缀：判据要求键本身带完整作用域，故测试里直接按字面量核对。 */
const PREFIX = 'omni-workspace-preferences'

/** 身份有效时的规范键形状。 */
const SCOPED_KEY = new RegExp(`^${PREFIX}:\\d+:\\d+$`)

function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

/**
 * 构造只含 ASCII claim 的假 JWT。
 *
 * 签名段固定为无意义占位串，因为 `decodeJwt` 只解码不验签；这里要的是真实解析路径而不是替身解析。
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
 * 只替换接口、身份与权限 store、i18n/axios 与 `localStorage`；store 函数体与 jwt 解析代码不复制、不改写。
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
  const namespace = workspaceModule.namespace as unknown as { useWorkspaceStore: () => PreferenceStoreView }
  const store = namespace.useWorkspaceStore()

  /** 切换身份并等 store 自身的 watch 把内存记录换成新作用域。 */
  async function switchIdentity(claims: Record<string, unknown>): Promise<void> {
    user.token = makeToken(claims)
    user.username = String(claims.username ?? 'controlled-user')
    await vue.nextTick()
  }

  return {
    store,
    user,
    storage,
    switchIdentity,
    /** 写入过的作用域键（去重），用于核对身份缺失时零写入。 */
    writtenKeys: (): string[] => [...new Set(storage.writes)],
  }
}

type Harness = Awaited<typeof createHarness>

/** 有效身份的最小 claim 集：租户 1、用户 101、用户名与用户 id 无关。 */
const TOKEN_A = { sub: '101', tenant_id: 1, username: 'alice' }

/** 同租户另一用户。 */
const TOKEN_B = { sub: '202', tenant_id: 1, username: 'bob' }

/** 与 A 同名同用户 id、但租户不同，用来证明租户确实参与键。 */
const TOKEN_CROSS_TENANT = { sub: '101', tenant_id: 2, username: 'alice' }

/** 取带身份令牌的作用域键字符串。 */
function scopedKey(claims: { sub?: string; tenant_id?: number }): string {
  return `${PREFIX}:${claims.tenant_id}:${claims.sub}`
}

/** 等待一次身份切换后的重载入。 */
async function withIdentity(h: Harness, claims: Record<string, unknown>): Promise<void> {
  await h.switchIdentity(claims)
}

test('作用域键只由 tenant_id 与 sub 派生，改用户名不动键', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  assert.equal(h.store.preferenceKey, scopedKey(TOKEN_A))
  h.user.username = 'renamed-admin'
  await withIdentity(h, TOKEN_A)
  assert.equal(h.store.preferenceKey, scopedKey(TOKEN_A), '用户名变化不得改变作用域键')
  assert.doesNotMatch(String(h.store.preferenceKey), /renamed-admin|alice/, '键里不得出现用户名')
})

test('A/B 用户互不泄漏：切走清空、切回按自己的键读回', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  assert.equal(h.store.toggleFavorite('10'), true)
  h.store.recordRecent('100')
  assert.deepEqual([...h.store.favoriteIds], ['10'])

  await withIdentity(h, TOKEN_B)
  assert.deepEqual([...h.store.favoriteIds], [], '切换身份后不得保留上一账号的收藏')
  assert.deepEqual([...h.store.recentIds], [], '切换身份后不得保留上一账号的最近访问')
  assert.deepEqual(h.writtenKeys(), [scopedKey(TOKEN_A)], '新身份没有写入，不应产生任何键')

  assert.equal(h.store.toggleFavorite('20'), true)
  assert.deepEqual(JSON.parse(h.storage.raw(scopedKey(TOKEN_A)) as string).favorites, ['10'], 'B 的写入不得覆盖 A 的记录')
  assert.deepEqual([...h.store.favoriteIds], ['20'])

  await withIdentity(h, TOKEN_A)
  assert.deepEqual([...h.store.favoriteIds], ['10'], '切回 A 应从 A 自己的键读回')
  assert.deepEqual([...h.store.recentIds], ['100'])
})

test('同名同用户 ID 跨租户仍隔离，租户确实参与作用域键', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  h.store.toggleFavorite('10')
  assert.equal(h.store.preferenceKey, scopedKey(TOKEN_A))

  await withIdentity(h, TOKEN_CROSS_TENANT)
  assert.equal(h.store.preferenceKey, scopedKey(TOKEN_CROSS_TENANT))
  assert.deepEqual([...h.store.favoriteIds], [], '跨租户不得命中同名同 ID 的记录')
  assert.deepEqual(h.writtenKeys(), [scopedKey(TOKEN_A)], '另一租户身份未写入，不应出现第二个键')
  assert.deepEqual(h.storage.keys(), [scopedKey(TOKEN_A)])
})

test('身份缺失时既不读也不写：未登录、不可解令牌、缺 claim、claim 非有限数', async () => {
  const invalid: [string, string][] = [
    ['未登录空令牌', ''],
    ['非 JWT 字符串', 'opaque-token'],
    ['缺 sub', makeToken({ tenant_id: 1, username: 'alice' })],
    ['缺 tenant_id', makeToken({ sub: '101', username: 'alice' })],
    ['sub 非数字', makeToken({ sub: 'not-a-number', tenant_id: 1, username: 'alice' })],
    ['tenant_id 非数字', makeToken({ sub: '101', tenant_id: Number.POSITIVE_INFINITY, username: 'alice' })],
  ]
  for (const [name, token] of invalid) {
    const h = await createHarness({
      token,
      seed: { [`${PREFIX}:1:101`]: '{"version":1,"favorites":["10"],"recents":["100"]}' },
    })
    assert.equal(h.store.preferenceKey, null, `${name} 应判为无作用域`)
    h.store.loadPreferences()
    assert.deepEqual([...h.store.favoriteIds], [], `${name} 不得读取任何记录`)
    assert.equal(h.store.toggleFavorite('11'), false, `${name} 收藏应被拒绝`)
    h.store.recordRecent('11')
    assert.deepEqual([...h.store.recentIds], [], `${name} 不得写入最近访问`)
    assert.deepEqual(h.writtenKeys(), [], `${name} 不得发生任何落盘写入`)
    assert.deepEqual(
      h.storage.keys().filter((key) => !SCOPED_KEY.test(key)),
      [],
      `${name} 不得落入公共命名空间`,
    )
    assert.equal(h.storage.raw(`${PREFIX}:1:101`), '{"version":1,"favorites":["10"],"recents":["100"]}', `${name} 不得改动他人记录`)
  }
})

test('损坏 JSON、非对象形态与非法 ID 只降级为空或清洗，绝不回落到别的键', async () => {
  const cases: [string, string, string[]][] = [
    ['截断的 JSON', '{', []],
    ['null 形态', 'null', []],
    ['字符串形态', '"favorites:10"', []],
    ['数组形态', '["10"]', []],
    ['favorites 非数组', '{"favorites":"10","recents":null}', []],
    ['含非法项', '{"favorites":[7,"","x","x"],"recents":["a",null,"a"]}', ['x']],
  ]
  for (const [name, raw, expected] of cases) {
    const h = await createHarness({ token: makeToken(TOKEN_A) })
    const otherKey = `${PREFIX}:9:999`
    h.storage.setItem(otherKey, '{"version":1,"favorites":["SECRET"],"recents":["SECRET"]}')
    h.storage.setItem(scopedKey(TOKEN_A), raw)
    h.store.loadPreferences()
    assert.deepEqual([...h.store.favoriteIds], expected, `${name} 的清洗结果不符`)
    assert.deepEqual([...h.store.recentIds], name === '含非法项' ? ['a'] : [], `${name} 的最近访问清洗不符`)
    assert.ok(!h.store.favoriteIds.includes('SECRET'), `${name} 不得回落到别的键`)
    assert.equal(h.writtenKeys().filter((key) => key === scopedKey(TOKEN_A)).length, 1, '损坏记录只应被本次读走')
  }
})

test('收藏按加入先后稳定排列，取消后其余相对顺序不变', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  for (const id of ['a', 'b', 'c']) h.store.toggleFavorite(id)
  assert.deepEqual([...h.store.favoriteIds], ['a', 'b', 'c'])
  assert.equal(h.store.isFavorite('b'), true)

  assert.equal(h.store.toggleFavorite('b'), false, '重复调用应取消收藏并返回取消后的状态')
  assert.deepEqual([...h.store.favoriteIds], ['a', 'c'], '取消不得改变其余项的相对顺序')

  h.store.toggleFavorite('b')
  assert.deepEqual([...h.store.favoriteIds], ['a', 'c', 'b'], '重新收藏追加到末尾，不恢复旧位置')
  assert.deepEqual(JSON.parse(h.storage.raw(scopedKey(TOKEN_A)) as string).favorites, ['a', 'c', 'b'])
})

test('最近访问去重、倒序并裁到十二项', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  for (let index = 1; index <= 14; index += 1) h.store.recordRecent(String(index))
  assert.equal(h.store.recentIds.length, 12, '只应保留最近十二项')
  assert.deepEqual([...h.store.recentIds.slice(0, 3)], ['14', '13', '12'], '最近访问按访问先后倒序')
  assert.ok(!h.store.recentIds.includes('1') && !h.store.recentIds.includes('2'), '被裁掉的旧项不得留下')

  h.store.recordRecent('14')
  assert.equal(h.store.recentIds.length, 12)
  assert.equal(h.store.recentIds[0], '14')
  assert.equal(h.store.recentIds.filter((id) => id === '14').length, 1, '重复访问只留最新位置')
})

test('工作台与抽屉读同一份响应式记录：一次写入三处可见', async () => {
  const h = await createHarness({ token: makeToken(TOKEN_A) })
  h.store.toggleFavorite('10')
  assert.deepEqual([...h.store.favoriteIds], ['10'])
  assert.deepEqual([...h.store.preferences.favorites], ['10'], 'preferences 与 favoriteIds 必须是同一份数据')
  assert.equal(h.store.isFavorite('10'), true)
  h.store.recordRecent('20')
  assert.deepEqual([...h.store.recentIds], ['20'])
  assert.deepEqual([...h.store.preferences.recents], ['20'])
})

test('落盘内容只有版本与稳定 ID，不含令牌、用户名、标题或路径', async () => {
  const token = makeToken(TOKEN_A)
  const h = await createHarness({ token })
  h.store.toggleFavorite('10')
  h.store.recordRecent('20')
  const raw = h.storage.raw(scopedKey(TOKEN_A)) as string
  assert.deepEqual(Object.keys(JSON.parse(raw)).sort(), ['favorites', 'recents', 'version'])
  assert.doesNotMatch(raw, /token|jwt|authorization|secret/i, '持久化内容不得含凭据')
  assert.ok(!raw.includes('alice'), '持久化内容不得含用户名')
  assert.ok(!raw.includes('controlled-signature') && !raw.includes(token.slice(0, 12)), '持久化内容不得含令牌片段')
  assert.doesNotMatch(raw, /\/workspace|title|label|href|path/i, '持久化内容不得含标题或路径')
})

/** 取 `<script setup>` 区块，用于断言只在逻辑层成立的约束。 */
function scriptBlock(source: string): string {
  const start = source.indexOf('<script setup')
  const end = source.indexOf('</script>')
  assert.notEqual(start, -1, '组件应包含 script setup 区块')
  return source.slice(start, end)
}

const storeSource = read('../../src/stores/workspace.ts')
const overview = read('../../src/views/workspace/index.vue')
const drawer = read('../../src/components/ApplicationDrawer.vue')
const entryList = read('../../src/components/workspace/WorkspaceEntryList.vue')
const elementPlugins = read('../../src/plugins/element-plus.ts')

test('身份来源只用 jwt 的两个 claim 读取函数，不改认证存储方案', () => {
  assert.match(storeSource, /import \{ getTenantIdFromToken, getUserIdFromToken \} from '@\/utils\/jwt'/)
  assert.match(storeSource, /getUserIdFromToken\(token\)/)
  assert.match(storeSource, /getTenantIdFromToken\(token\)/)
  assert.doesNotMatch(storeSource, /\.username\b/, '不得用用户名作键')
  // 只读偏好，不改登录态也不新增认证通道。
  assert.doesNotMatch(storeSource, /setToken|logout|removeItem|clear\(\)|sessionStorage|indexedDB|document\.cookie/)
  assert.doesNotMatch(storeSource, /localStorage\.setItem\([^)]*token/i, '不得把令牌写进本地记录')
  // 单条持久化路径：写入只经由 persistPreferences，避免别处再拼一个作用域键。
  assert.equal(storeSource.match(/localStorage\.setItem\(/g)?.length, 1)
  assert.equal(storeSource.match(/localStorage\.getItem\(/g)?.length, 1)
})

test('store 不触碰菜单与授权，收藏给不出任何访问权限', () => {
  assert.doesNotMatch(storeSource, /api\/menu|loadMenus|retryLoadMenus|menuLoadState/)
  assert.doesNotMatch(storeSource, /router\.push|router-link|v-permission|hasPermission|checkPermi/)
  // 持久化偏好集合与权限判定之间不存在写入通道：store 只返回记录与两个动作。
  assert.match(storeSource, /return \{[\s\S]*preferences,[\s\S]*loadPreferences,[\s\S]*toggleFavorite,[\s\S]*recordRecent,/)
})

test('概览页把记录 ID 与当前可见集合求交，写入前校验可见性', () => {
  const body = scriptBlock(overview)
  assert.match(body, /for \(const entry of \[\.\.\.modules\.value, \.\.\.pages\.value\]\) map\.set\(String\(entry\.id\), entry\)/)
  assert.match(body, /\.filter\(\(entry\): entry is NavigationEntry => entry !== undefined\)/)
  assert.match(body, /const favoriteEntries = computed\(\(\) => entriesOf\(workspaceStore\.favoriteIds\)\)/)
  assert.match(body, /const recentEntries = computed\(\(\) => entriesOf\(workspaceStore\.recentIds\)\)/)
  assert.match(body, /if \(!visibleEntries\.value\.has\(id\)\) return/)
  assert.match(body, /onMounted\(\(\) => workspaceStore\.loadPreferences\(\)\)/)
  assert.match(overview, /:items="favoriteEntries"/)
  assert.match(overview, /:items="recentEntries"/)
  // 概览页不自行落盘也不另建记录源。
  assert.doesNotMatch(body, /localStorage|sessionStorage/)
  assert.doesNotMatch(body, /defineStore/)
})

test('抽屉与概览共用同一 store 与同一份派生导航，收藏只渲染可见目标', () => {
  const body = scriptBlock(drawer)
  assert.match(body, /const workspaceStore = useWorkspaceStore\(\)/)
  assert.match(body, /const \{ modules, pages, menuLoadState \} = useApplicationNavigation\(\)/)
  assert.match(body, /for \(const id of workspaceStore\.favoriteIds\)/)
  assert.match(body, /if \(target !== undefined\) out\.push\(target\)/)
  assert.match(body, /if \(!targetsById\.value\.has\(id\)\) return/)
  assert.match(body, /if \(open\) workspaceStore\.loadPreferences\(\)/)
  assert.match(drawer, /:aria-pressed="workspaceStore\.isFavorite\(category\.favoriteId\)"/)
  assert.doesNotMatch(body, /localStorage|sessionStorage|defineStore/)
  // 两处共用同一个组合式来源，因此不会出现两套可见集合口径。
  assert.match(scriptBlock(overview), /useApplicationNavigation\(\)/)
})

test('条目组件只在收到收藏集合时渲染星标，状态与文案都由页面给定', () => {
  const body = scriptBlock(entryList)
  assert.match(body, /favoriteIds\?: string\[\] \| null/)
  assert.match(body, /function isFavorited\(item: WorkspaceEntry\): boolean \{[\s\S]*?props\.favoriteIds !== null && props\.favoriteIds\.includes\(item\.id\)/)
  assert.match(body, /defineEmits<\{ \(event: 'toggle-favorite', id: string\): void \}>\(\)/)
  assert.match(entryList, /v-if="props\.favoriteIds !== null"/)
  assert.match(entryList, /:aria-pressed="isFavorited\(item\)"/)
  assert.match(entryList, /@click="emit\('toggle-favorite', item\.id\)"/)
  // 星标状态只由传入集合决定，组件自身不猜语义也不落存储。
  assert.doesNotMatch(body, /localStorage|sessionStorage|defineStore|watch\(/)
  assert.doesNotMatch(entryList, /from 'element-plus'/)
})

test('星标图标沿用既有全局注册，四语收藏词条齐备且 ja/ko 为本地译文', () => {
  assert.match(elementPlugins, /\bStar\b/)
  assert.doesNotMatch(drawer, /@element-plus\/icons/)
  assert.doesNotMatch(entryList, /@element-plus\/icons/)
  const locales = [
    { file: '../../src/locales/zh-CN.ts', cjk: true },
    { file: '../../src/locales/en-US.ts', cjk: false },
    { file: '../../src/locales/ja-JP.ts', cjk: true },
    { file: '../../src/locales/ko-KR.ts', cjk: true },
  ]
  for (const { file, cjk } of locales) {
    const source = read(file)
    const commonStart = source.indexOf('\n  common: {')
    const commonEnd = source.indexOf('\n  }', commonStart)
    assert.ok(commonStart > -1 && commonEnd > commonStart, `${file} 缺少 common 区块`)
    const commonBlock = source.slice(commonStart, commonEnd)
    for (const key of ['favorite', 'unfavorite']) {
      const value = new RegExp(`\\b${key}: '([^']+)'`).exec(commonBlock)?.[1]
      assert.ok(value, `${file} 缺少 common.${key}`)
      assert.notEqual(value.trim(), '', `${file} 的 common.${key} 为空`)
      const isCjk = /[぀-ヿ가-힯一-鿿]/.test(value)
      assert.equal(isCjk, cjk, `${file} 的 common.${key} 不是本地语言文案：${value}`)
    }
  }
  // 两个词条都嵌在已登记的 common 前缀下，因此不新增顶层键，scaffold/catalog/modules.yaml 无需变更。
  for (const source of locales.map(({ file }) => read(file))) {
    assert.doesNotMatch(source, /^ {2}workspacePreferences:/m)
  }
})
