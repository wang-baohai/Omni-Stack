/**
 * @module navigation/menu-generation.test
 * @description WP-06.S04 菜单加载会话代次保护的定向测试（node:test，不依赖浏览器）。
 *
 * 在 vm 模块图里执行**真实**的 `src/stores/permission.ts`，连带真实 `jwt.ts`、
 * 真实 `errors.ts` / `error-display.ts` 与真实 vue/pinia，只把菜单接口和 user store 换成受控替身：
 * - 同一代次并发加载只发一次请求并共享同一 Promise；
 * - A 身份请求未完成 → reset → B 登录并加载 → A 成功/失败晚到，B 的菜单、状态、错误与
 *   加载引用都不被旧结果覆盖；
 * - 旧 Promise 的 finally 只清理自己拥有的引用，不会把新会话的 menuLoadPromise 置空；
 * - 当前代次的失败仍写失败态并抛出，不被吞成空菜单；
 * - reset 递增代次并清空权限/菜单，统一清理入口的登出→重置→动态路由回收顺序不变。
 *
 * 运行方式：
 * `node --experimental-vm-modules --experimental-strip-types --test tests/navigation/menu-generation.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { test } from 'node:test'
import vm from 'node:vm'

const require = createRequire(import.meta.url)
const ts = require('typescript') as typeof import('typescript')
const vue = require('vue') as typeof import('vue')
const pinia = require('pinia') as typeof import('pinia')

/** 菜单节点在本步用到的字段。 */
interface MenuNodeView {
  id: number
  permissionCode: string
  path: string
  children: MenuNodeView[]
}

/** 权限 store 在本步用到的形状。 */
interface PermissionStoreView {
  permissions: string[]
  menuTree: MenuNodeView[]
  menusLoaded: boolean
  menuLoadState: string
  menuLoadError: string | null
  initFromToken(): void
  loadMenus(): Promise<void>
  retryLoadMenus(): Promise<void>
  reset(): void
}

/** 菜单接口响应体：与 `request.get<ApiResponse<MenuNode[]>>` 的 axios 包装一致。 */
type MenuResponse = { data: { data: MenuNodeView[] } }

interface Deferred<T> {
  promise: Promise<T>
  resolve(value: T): void
  reject(reason?: unknown): void
}

/** 可手动结算的 Promise，用来精确控制请求返回时机。 */
function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 构造只含 ASCII claim 的假 JWT；签名段是占位串，decodeJwt 只解码不验签。 */
function makeToken(claims: Record<string, unknown>): string {
  const encode = (value: unknown): string => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(claims)}.controlled-signature`
}

/** 租户 1 的 A 身份：scope 含 crm。 */
const TOKEN_A = makeToken({ sub: '101', tenant_id: 1, username: 'alice', scope: ['crm'] })

/** 租户 1 的 B 身份：scope 含 srm。 */
const TOKEN_B = makeToken({ sub: '202', tenant_id: 1, username: 'bob', scope: ['srm'] })

/** 租户 2 的 A 同名身份：用于证明跨租户同样按代次隔离。 */
const TOKEN_OTHER_TENANT = makeToken({ sub: '101', tenant_id: 2, username: 'alice', scope: ['workflow'] })

/** 造一个菜单节点。 */
function menu(id: number, permissionCode: string): MenuNodeView {
  return { id, permissionCode, path: `/admin/${permissionCode}`, children: [] }
}

/** 包装成 axios 响应形状。 */
function response(nodes: MenuNodeView[]): MenuResponse {
  return { data: { data: nodes } }
}

/**
 * 在 vm 模块图里执行真实权限 store。
 *
 * @param token 初始身份令牌
 */
async function createHarness(token: string, deploymentModules?: string, physicalModules?: string[]) {
  const user = vue.reactive({ token })
  const requests: Deferred<MenuResponse>[] = []
  let fetchCalls = 0
  const context = vm.createContext({
    ...(physicalModules === undefined ? {} : { __OMNI_PHYSICAL_MODULES__: physicalModules }),
    Promise,
    setTimeout,
    clearTimeout,
    // 真实 jwt.ts 依赖浏览器全局 atob，这里只提供等价解码。
    atob: (value: string) => Buffer.from(value, 'base64').toString('binary'),
  })
  // 错误必须由 vm realm 构造：真实 errors.ts 的 `instanceof Error` 不认宿主 realm 的 Error。
  const VmError = vm.runInContext('Error', context) as ErrorConstructor

  function synthetic(exports: Record<string, unknown>): vm.SyntheticModule {
    return new vm.SyntheticModule(
      Object.keys(exports),
      function () {
        for (const [name, value] of Object.entries(exports)) this.setExport(name, value)
      },
      { context },
    )
  }

  function real(relative: string): vm.SourceTextModule {
    const sourceUrl = new URL(relative, import.meta.url)
    const code = ts.transpileModule(readFileSync(sourceUrl, 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    }).outputText
    return new vm.SourceTextModule(code, {
      context,
      identifier: sourceUrl.pathname,
      initializeImportMeta(meta) { meta.env = { VITE_OMNI_MODULES: deploymentModules } },
    })
  }

  const userModule = synthetic({ useUserStore: () => user })
  const modules: Record<string, vm.Module> = {
    vue: synthetic({ ...vue }),
    pinia: synthetic({ ...pinia }),
    axios: synthetic({ isAxiosError: (value: unknown): boolean => value instanceof Error && value.name === 'AxiosError' }),
    '@/i18n': synthetic({ translate: (key: string) => key }),
    '@/stores/user': userModule,
    './user': userModule,
    '@/api/menu': synthetic({
      fetchMenuTree: () => {
        fetchCalls += 1
        const request = deferred<MenuResponse>()
        requests.push(request)
        return request.promise
      },
    }),
    '@/utils/jwt': real('../../src/utils/jwt.ts'),
    '@/utils/deployment-modules': real('../../src/utils/deployment-modules.ts'),
    '@/utils/error-display': real('../../src/utils/error-display.ts'),
    '@/utils/errors': real('../../src/utils/errors.ts'),
    '@/stores/permission': real('../../src/stores/permission.ts'),
  }

  const permissionModule = modules['@/stores/permission'] as vm.SourceTextModule
  await permissionModule.link((dependency) => {
    const resolved = modules[dependency]
    assert.ok(resolved, `未配置依赖替身: ${dependency}`)
    return resolved
  })
  await permissionModule.evaluate()
  pinia.setActivePinia(pinia.createPinia())
  const namespace = permissionModule.namespace as unknown as { usePermissionStore: () => PermissionStoreView }
  const store = namespace.usePermissionStore()

  return { user, store, requests, fetchCalls: (): number => fetchCalls, VmError }
}

/** 取 store 里的菜单 ID 序列（Array.from 生成宿主 realm 数组，避免跨 realm 原型比较）。 */
function ids(store: PermissionStoreView): number[] {
  return Array.from(store.menuTree, (node) => node.id)
}

test('源码精简部署对全量令牌只缩小客户端权限，不改令牌或增加授权', async () => {
  const token = makeToken({ sub: '101', tenant_id: 1, scope: ['system:user', 'workflow', 'workflow:task', 'crm:lead'] })
  const core = await createHarness(token, 'platform,auth,base,gateway')
  core.store.initFromToken()
  assert.deepEqual(Array.from(core.store.permissions), ['system:user'])
  assert.equal(core.user.token, token)
  const workflow = await createHarness(token, 'platform,auth,base,gateway,workflow')
  workflow.store.initFromToken()
  assert.deepEqual(Array.from(workflow.store.permissions), ['system:user', 'workflow', 'workflow:task'])
  assert.equal(workflow.store.permissions.includes('crm:lead'), false)
  assert.equal(workflow.user.token, token)
})

test('生成工程未传构建选择时，真实权限Store也不能请求已物理裁掉的工作流', async () => {
  const token = makeToken({ sub: '101', tenant_id: 1, scope: ['system:user', 'workflow', 'crm:lead'] })
  const core = await createHarness(token, undefined, [])
  core.store.initFromToken()
  assert.deepEqual(Array.from(core.store.permissions), ['system:user'])
  const crm = await createHarness(token, undefined, ['crm'])
  crm.store.initFromToken()
  assert.deepEqual(Array.from(crm.store.permissions), ['system:user', 'crm:lead'])
  const cannotRevive = await createHarness(token, 'platform,auth,workflow', [])
  cannotRevive.store.initFromToken()
  assert.deepEqual(Array.from(cannotRevive.store.permissions), ['system:user'])
})

test('同一代次的并发加载只发一次请求并共享同一 Promise', async () => {
  const h = await createHarness(TOKEN_A)
  const first = h.store.loadMenus()
  const second = h.store.loadMenus()
  assert.equal(h.fetchCalls(), 1, '并发只应发出一个菜单请求')
  assert.equal(h.store.menuLoadState, 'loading')

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await Promise.all([first, second])
  assert.equal(h.store.menuLoadState, 'loaded')
  assert.deepEqual(ids(h.store), [1])
  assert.equal(h.store.menusLoaded, true)
})

test('A 身份的成功结果晚到不得回填 B 的会话', async () => {
  const h = await createHarness(TOKEN_A)
  const pA = h.store.loadMenus()
  h.store.reset()
  h.user.token = TOKEN_B
  const pB = h.store.loadMenus()
  assert.equal(h.fetchCalls(), 2, 'B 登录后必须能发起自己的加载')

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await pA
  assert.equal(h.store.menuLoadState, 'loading', 'B 仍在加载，旧结果不得把状态改成 loaded')
  assert.deepEqual(ids(h.store), [], 'A 的菜单不得回填')
  assert.equal(h.store.menusLoaded, false)

  h.requests[1].resolve(response([menu(2, 'srm')]))
  await pB
  assert.equal(h.store.menuLoadState, 'loaded')
  assert.deepEqual(ids(h.store), [2], '最终菜单只能属于 B')
})

test('旧请求的 finally 只清理自己拥有的加载引用', async () => {
  const h = await createHarness(TOKEN_A)
  const pA = h.store.loadMenus()
  h.store.reset()
  h.user.token = TOKEN_B
  const pB = h.store.loadMenus()

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await pA
  const again = h.store.loadMenus()
  assert.equal(h.fetchCalls(), 2, '旧 finally 不得把 B 的 menuLoadPromise 置空后触发第三次请求')

  h.requests[1].resolve(response([menu(2, 'srm')]))
  await Promise.all([pB, again])
  assert.equal(h.store.menuLoadState, 'loaded')
  assert.deepEqual(ids(h.store), [2])
})

test('A 的失败晚于 B 成功：不覆盖新菜单也不把旧失败抛给旧守卫', async () => {
  const h = await createHarness(TOKEN_A)
  const pA = h.store.loadMenus()
  h.store.reset()
  h.user.token = TOKEN_B
  const pB = h.store.loadMenus()
  h.requests[1].resolve(response([menu(2, 'srm')]))
  await pB
  assert.equal(h.store.menuLoadState, 'loaded')

  h.requests[0].reject(new h.VmError('A 旧身份失败'))
  await pA
  assert.equal(h.store.menuLoadState, 'loaded', '陈旧失败不得把 B 的 loaded 改成 failed')
  assert.deepEqual(ids(h.store), [2], '陈旧失败不得清空 B 的菜单')
  assert.equal(h.store.menuLoadError, null)
})

test('B 的失败态不被 A 的迟到成功覆盖', async () => {
  const h = await createHarness(TOKEN_A)
  const pA = h.store.loadMenus()
  h.store.reset()
  h.user.token = TOKEN_B
  const pB = h.store.loadMenus()
  h.requests[1].reject(new h.VmError('B 当前身份失败'))
  await assert.rejects(pB, /B 当前身份失败/)
  assert.equal(h.store.menuLoadState, 'failed')
  assert.equal(h.store.menuLoadError, 'B 当前身份失败')

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await pA
  assert.equal(h.store.menuLoadState, 'failed', 'A 的迟到成功不得覆盖 B 的失败态')
  assert.equal(h.store.menuLoadError, 'B 当前身份失败')
  assert.deepEqual(ids(h.store), [])
})

test('跨租户换号同样按代次隔离', async () => {
  const h = await createHarness(TOKEN_A)
  const pA = h.store.loadMenus()
  h.store.reset()
  h.user.token = TOKEN_OTHER_TENANT
  const pOther = h.store.loadMenus()

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await pA
  h.requests[1].resolve(response([menu(3, 'workflow')]))
  await pOther
  assert.deepEqual(ids(h.store), [3], '旧租户菜单不得回填到新租户会话')
})

test('reset 立即清空权限与菜单，并使在途请求失效', async () => {
  const h = await createHarness(TOKEN_A)
  h.store.initFromToken()
  assert.deepEqual([...h.store.permissions], ['crm'])

  const pending = h.store.loadMenus()
  h.store.reset()
  assert.deepEqual([...h.store.permissions], [])
  assert.deepEqual(ids(h.store), [])
  assert.equal(h.store.menusLoaded, false)
  assert.equal(h.store.menuLoadState, 'idle')
  assert.equal(h.store.menuLoadError, null)

  h.requests[0].resolve(response([menu(1, 'crm')]))
  await pending
  assert.equal(h.store.menuLoadState, 'idle', '旧请求不得在 reset 后写回任何状态')
  assert.deepEqual(ids(h.store), [])
})

test('当前代次的失败仍写失败态并抛出，不吞成空菜单', async () => {
  const h = await createHarness(TOKEN_A)
  const pending = h.store.loadMenus()
  h.requests[0].reject(new h.VmError('菜单接口爆炸'))
  await assert.rejects(pending, /菜单接口爆炸/)
  assert.equal(h.store.menuLoadState, 'failed')
  assert.equal(h.store.menusLoaded, true)
  assert.equal(h.store.menuLoadError, '菜单接口爆炸')
  assert.deepEqual(ids(h.store), [])
})

test('加载成功后再调用不再发请求', async () => {
  const h = await createHarness(TOKEN_A)
  const first = h.store.loadMenus()
  h.requests[0].resolve(response([menu(1, 'crm')]))
  await first
  await h.store.loadMenus()
  assert.equal(h.fetchCalls(), 1)
})

test('失败后重试会按当前代次重新请求', async () => {
  const h = await createHarness(TOKEN_A)
  const first = h.store.loadMenus()
  h.requests[0].reject(new h.VmError('首次失败'))
  await assert.rejects(first)
  const retry = h.store.retryLoadMenus()
  assert.equal(h.store.menuLoadState, 'loading')
  assert.equal(h.fetchCalls(), 2)
  h.requests[1].resolve(response([menu(1, 'crm')]))
  await retry
  assert.equal(h.store.menuLoadState, 'loaded')
  assert.deepEqual(ids(h.store), [1])
})

test('无租户令牌时维持既有空菜单 loaded 且不发请求', async () => {
  const h = await createHarness('')
  await h.store.loadMenus()
  assert.equal(h.fetchCalls(), 0)
  assert.equal(h.store.menuLoadState, 'loaded')
  assert.equal(h.store.menusLoaded, true)
  assert.deepEqual(ids(h.store), [])
})

test('结构红线：写回前核对代次、finally 有归属、reset 递增代次且统一清理顺序不变', () => {
  const source = readFileSync(new URL('../../src/stores/permission.ts', import.meta.url), 'utf8')
  assert.ok(
    (source.match(/epoch (?:!==|===) sessionEpoch/g) ?? []).length >= 3,
    '成功、失败与无租户分支写回前都必须核对代次',
  )
  assert.match(source, /if \(menuLoadPromise === request\) menuLoadPromise = null/, 'finally 只清理自己的引用')
  assert.match(source, /function reset\(\) \{\s*\n\s*sessionEpoch \+= 1/, 'reset 必须使旧代次失效')
  assert.doesNotMatch(source, /AbortController/, '不得用仅 abort 代替代次保护')
  assert.doesNotMatch(source, /registerDynamicRoutes\(|from '@\/router'/, 'store 不自行注册或回收路由')

  const routerSource = readFileSync(new URL('../../src/router/index.ts', import.meta.url), 'utf8')
  assert.match(
    routerSource,
    /export function clearAuthenticatedSession\(explicit = true\) \{\s*\n\s*useUserStore\(\)\.logout\(explicit\)\s*\n\s*usePermissionStore\(\)\.reset\(\)\s*\n\s*clearDynamicRoutes\(\)/,
    '统一清理仍按登出→重置→动态路由回收执行，reset 不遗留动态路由',
  )
  assert.match(
    routerSource,
    /await permissionStore\.loadMenus\(\)\s*\n\s*registerDynamicRoutes\(permissionStore\.menuTree\)/,
    '守卫仍以加载结果注册动态路由',
  )
  assert.match(routerSource, /catch \{\s*\n(?:[^\n]*\n)*?[^\n]*name: 'MenuLoadError'/, '加载失败仍走降级页')
})
