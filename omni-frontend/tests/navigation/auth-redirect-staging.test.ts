/**
 * @module navigation/auth-redirect-staging.test
 * @description WP-06.S02「接入密码、社交回调、设备登录的应用回跳」定向测试（node:test，不依赖浏览器、路由单例与真实 sessionStorage）。
 *
 * 覆盖对象为五条登录链：密码成功（既有集中校验落点，本步不改其语义）、社交 callback 成功、
 * 社交 callback 拒绝、设备码成功、设备码失效，以及「既有会话登录→授权端点→回调」这条 OAuth 协议链。
 * 前 10 条按真实 `utils/navigation.ts` 的暂存/一次消费/清理函数在注入的会话存储替身上取证：
 * 只落已校验站内目标、非法目标反而清理陈旧值、取出即删除、被篡改的存储值二次校验后拒绝、
 * 只操作自己的键不碰 PKCE/state 协议参数、宿主无 sessionStorage 时安全退化为不落盘。
 * 后 5 条按源码接线取证：SFC 与 router 入口在 Node 无法实例化（createWebHistory 于模块顶层求值），
 * 因此逐个断言写入方、消费方、清理点与协议分支，并逐字核对 state/PKCE 键值和设备验证页未被改写。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/auth-redirect-staging.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

import {
  APP_REDIRECT_STORAGE_KEY,
  clearAppRedirect,
  consumeAppRedirect,
  safeAppRedirect,
  stageAppRedirect,
} from '../../src/utils/navigation.ts'

/** PKCE 与 state 协议键：本步必须与 utils/pkce 暂存实现完全无关。 */
const PROTOCOL_KEYS = {
  codeVerifier: 'oauth2_code_verifier',
  state: 'oauth2_state',
  clientId: 'oauth2_client_id',
  redirectUri: 'oauth2_redirect_uri',
  authorizeUrl: 'oauth2_authorize_url',
}

/**
 * 构造会话存储替身，只实现 navigation.ts 需要的最小能力。
 *
 * @param seed 预置键值
 * @return 可读取快照与删除次数的存储对象
 */
function makeStorage(seed: Record<string, string> = {}) {
  const map = new Map(Object.entries(seed))
  let removeCalls = 0
  const read = (key: string): string | null => {
    const value = map.get(key)
    return value === undefined ? null : value
  }
  return {
    getItem: (key: string) => read(key),
    setItem: (key: string, value: string) => {
      map.set(key, value)
    },
    removeItem: (key: string) => {
      removeCalls += 1
      map.delete(key)
    },
    peek: (key: string) => read(key),
    keys: () => [...map.keys()].sort(),
    removeCount: () => removeCalls,
  }
}

test('暂存的站内深链接原样落盘，取出即删除且只此一次', () => {
  const store = makeStorage()
  const staged = stageAppRedirect('/admin/base/user?id=1&page=2#section', store)
  assert.equal(staged, '/admin/base/user?id=1&page=2#section')
  assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), '/admin/base/user?id=1&page=2#section')
  assert.equal(consumeAppRedirect(store), '/admin/base/user?id=1&page=2#section')
  assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null, '消费后必须删除，避免刷新页面再跳一次')
  assert.equal(consumeAppRedirect(store), undefined, '同一目标不得二次消费')
})

test('暂存保留 query 与 hash，工作台深链接同样原样回跳', () => {
  const store = makeStorage()
  assert.equal(stageAppRedirect('/workspace/jobs?status=RUNNING', store), '/workspace/jobs?status=RUNNING')
  assert.equal(consumeAppRedirect(store), '/workspace/jobs?status=RUNNING')
})

test('非法目标不落盘，并且清掉上一次留下的陈旧目标', () => {
  const store = makeStorage()
  stageAppRedirect('/admin/crm/opportunity', store)
  assert.equal(stageAppRedirect('//evil.example.com', store), undefined)
  assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null, '拒绝外部目标时同时丢弃旧值')
  assert.equal(consumeAppRedirect(store), undefined)

  for (const hostile of ['/\\evil.example.com', '%2F%2Fevil.example.com', '/login', '   ', null, undefined]) {
    stageAppRedirect('/admin/base/user', store)
    assert.equal(stageAppRedirect(hostile, store), undefined, `${String(hostile)} 不应落盘`)
    assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null, `${String(hostile)} 应触发陈旧值清理`)
  }
})

test('认证入口页与非字符串输入一律拒绝暂存，数组目标也不落盘', () => {
  const store = makeStorage()
  assert.equal(stageAppRedirect('/menu-load-error?redirect=/admin', store), undefined)
  assert.equal(stageAppRedirect(['/admin/a', '/admin/b'], store), undefined)
  assert.equal(stageAppRedirect({ path: '/admin/a' }, store), undefined)
  assert.deepEqual(store.keys(), [])
})

test('存储值被篡改为外站时二次校验拒绝，且仍按一次性语义删除', () => {
  const store = makeStorage({ [APP_REDIRECT_STORAGE_KEY]: '//evil.example.com' })
  assert.equal(consumeAppRedirect(store), undefined)
  assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null, '无论取值是否可用都要删除')
  assert.ok(store.removeCount() >= 1)
})

test('存储值被篡改为登录循环或非法转义时同样拒绝', () => {
  const tampered = ['/login?redirect=/admin/base/user', '/%2F%2Fevil.example.com', '/admin/%zz', 'https://a.example.com']
  for (const value of tampered) {
    const store = makeStorage({ [APP_REDIRECT_STORAGE_KEY]: value })
    assert.equal(consumeAppRedirect(store), undefined, `${value} 不得交给路由`)
    assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null)
  }
})

test('清理函数丢弃暂存值，重复清理不抛错', () => {
  const store = makeStorage()
  stageAppRedirect('/admin/base/user', store)
  clearAppRedirect(store)
  assert.equal(store.peek(APP_REDIRECT_STORAGE_KEY), null)
  assert.equal(consumeAppRedirect(store), undefined)
  clearAppRedirect(store)
  clearAppRedirect()
})

test('暂存与清理只操作自己的键，不触碰 state 与 PKCE 协议参数', () => {
  const store = makeStorage({
    [PROTOCOL_KEYS.codeVerifier]: 'verifier-value',
    [PROTOCOL_KEYS.state]: 'state-value',
    [PROTOCOL_KEYS.clientId]: 'omni-frontend',
    [PROTOCOL_KEYS.redirectUri]: 'http://localhost:3000/callback',
    [PROTOCOL_KEYS.authorizeUrl]: 'http://localhost:8102/oauth2/authorize',
  })
  stageAppRedirect('/admin/base/user', store)
  assert.equal(consumeAppRedirect(store), '/admin/base/user')
  stageAppRedirect('//evil.example.com', store)
  clearAppRedirect(store)
  assert.equal(store.peek(PROTOCOL_KEYS.codeVerifier), 'verifier-value')
  assert.equal(store.peek(PROTOCOL_KEYS.state), 'state-value')
  assert.equal(store.peek(PROTOCOL_KEYS.clientId), 'omni-frontend')
  assert.equal(store.peek(PROTOCOL_KEYS.redirectUri), 'http://localhost:3000/callback')
  assert.equal(store.peek(PROTOCOL_KEYS.authorizeUrl), 'http://localhost:8102/oauth2/authorize')
  assert.deepEqual(store.keys(), Object.values(PROTOCOL_KEYS).sort(), '暂存不得新增第二份协议存储')
})

test('宿主无 sessionStorage 时安全退化：不落盘、取不到、清理不抛错', () => {
  assert.equal(typeof globalThis.sessionStorage, 'undefined', 'Node 环境本就不该有会话存储')
  assert.equal(stageAppRedirect('/admin/base/user'), undefined)
  assert.equal(consumeAppRedirect(), undefined)
  clearAppRedirect()
})

test('暂存值不参与权限判断，仍由集中校验器给出同一结论', () => {
  const store = makeStorage()
  const value = '/admin/asset/asset?tab=mine'
  assert.equal(stageAppRedirect(value, store), safeAppRedirect(value))
  assert.equal(consumeAppRedirect(store), safeAppRedirect(value))
})

/** SFC 与 router 入口在 Node 里无法实例化，这里按源码接线取证。 */
function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8').replace(/\r\n/g, '\n')
}

test('社交登录写入方只暂存已校验目标，且协议参数暂存面未被替换', () => {
  const source = read('../../src/components/LoginForm.vue')
  assert.match(
    source,
    /function handleThirdPartyLogin\(provider: string\) \{[\s\S]{0,300}?stageAppRedirect\(route\.query\.redirect\)/,
    '离开本站前暂存已校验目标',
  )
  assert.match(source, /stageAppRedirect\(route\.query\.redirect\)\s*\n\s*window\.location\.href = getThirdPartyLoginUrl\(/)
  assert.doesNotMatch(source, /consumeAppRedirect/, '登录表单不是消费方，避免提前吃掉目标')
  assert.doesNotMatch(source, /['"]omni-app-redirect['"]/, '键名唯一来源在 navigation.ts')
  // 既有会话登录→授权端点链保持原样：PKCE/state 五个协议键与跳转方式逐字不变
  for (const key of Object.values(PROTOCOL_KEYS)) {
    assert.ok(source.includes(`'${key}'`), `协议键 ${key} 必须原样存在`)
  }
  assert.match(source, /sessionStorage\.setItem\(PKCE_STORAGE_KEYS\.codeVerifier, codeVerifier\)/)
  assert.match(source, /sessionStorage\.setItem\(PKCE_STORAGE_KEYS\.state, state\)/)
  assert.match(source, /sessionStorage\.removeItem\(PKCE_STORAGE_KEYS\.authorizeUrl\)\s*\n\s*window\.location\.href = authorizeUrl/)
})

test('改走 OAuth 协议或重进凭证页时清理陈旧目标，协议分支不消费回跳', () => {
  const source = read('../../src/components/LoginForm.vue')
  assert.match(
    source,
    /async function initiateOAuth2Flow\(\) \{[\s\S]{0,300}?clearAppRedirect\(\)/,
    '企业 SSO 进入协议分支前丢弃普通应用目标',
  )
  assert.match(source, /onMounted\(\(\) => \{[\s\S]{0,300}?clearAppRedirect\(\)/, '凭证页挂载即丢弃上一趟未完成往返的旧目标')
  assert.match(source, /const error = route\.query\.error as string/, '第三方失败回跳仍带回登录页提示')
  assert.doesNotMatch(source, /router\.replace\((?:target|redirect)/, '登录页不直接消费暂存目标')
})

test('设备入口只携带已校验目标，回调成功一次性消费、拒绝与协议分支清理', () => {
  const form = read('../../src/components/LoginForm.vue')
  assert.match(
    form,
    /function goDeviceAuth\(\) \{[\s\S]{0,300}?safeAppRedirect\(route\.query\.redirect\)[\s\S]{0,300}name: 'DeviceAuth'/,
    '设备入口按集中校验决定是否携带 redirect',
  )
  assert.match(form, /@click="goDeviceAuth"/, '模板不再裸 push 设备路由')
  assert.doesNotMatch(form, /@click="router\.push\(\{ name: 'DeviceAuth' \}\)"/)

  const callback = read('../../src/views/callback/index.vue')
  assert.match(
    callback,
    /const target = consumeAppRedirect\(\)\s*\n\s*router\.replace\(target \?\? \{ name: 'Dashboard' \}\)/,
    '社交成功优先回跳原目标，无暂存时仍回首页',
  )
  assert.match(
    callback,
    /if \(params\.error\) \{[\s\S]{0,300}?clearAppRedirect\(\)\s*\n\s*router\.replace\(\{ name: 'Login' \}\)/,
    '拒绝或失败后不留下错误的旧目标',
  )
  assert.match(
    callback,
    /async function handleOAuth2CodeCallback\(\) \{[\s\S]{0,300}?clearAppRedirect\(\)/,
    '授权码分支只清理，不消费',
  )
  assert.match(callback, /exchangeCodeForToken\(\{[\s\S]{0,600}?router\.replace\(\{ name: 'Dashboard' \}\)/)
  assert.doesNotMatch(callback, /workspace|supplier-portal/, '协议回调不得被改写成普通工作台或门户跳转')
})

test('设备码成功回跳用已校验目标，失效与错误分支不跳转且验证页保持原协议', () => {
  const device = read('../../src/views/device/index.vue')
  assert.match(device, /const route = useRoute\(\)/)
  assert.match(
    device,
    /const redirect = safeAppRedirect\(route\.query\.redirect\)\s*\n\s*setTimeout\(\(\) => router\.replace\(redirect \?\? \{ name: 'Dashboard' \}\), 1000\)/,
    '成功分支优先原目标，无目标仍回首页',
  )
  assert.match(device, /clearTimers\(\)\s*\n\s*status\.value = 'expired'/, '失效分支只换状态不跳转')
  assert.doesNotMatch(device, /stageAppRedirect|consumeAppRedirect/, '设备链不使用暂存，避免与社交链互相污染')

  const verify = read('../../src/views/device/verify.vue')
  assert.match(verify, /const queryCode = route\.query\.user_code as string/, 'user_code 预填保持')
  assert.match(verify, /new URLSearchParams\(\{ user_code: userCode\.value \}\)/, '原授权提交流程保持')
})

test('密码登录与非社交页仍只经集中校验，暂存键名全站唯一来源', () => {
  const login = read('../../src/views/login/index.vue')
  assert.match(login, /safeAppRedirect\(route\.query\.redirect\)/, '密码成功后深链接先校验')
  assert.match(login, /router\.push\(redirect\)/)
  assert.match(login, /resolveAppLanding\(undefined, \{ entry:/)

  const portal = read('../../src/views/portal-login/index.vue')
  assert.match(portal, /resolveAppLanding\(route\.query\.redirect, \{ entry: 'portal' \}\)/)

  const navigation = read('../../src/utils/navigation.ts')
  assert.match(navigation, /export const APP_REDIRECT_STORAGE_KEY = 'omni-app-redirect'/)
  assert.match(navigation, /store\.setItem\(APP_REDIRECT_STORAGE_KEY, redirect\)/)
  assert.match(navigation, /store\.removeItem\(APP_REDIRECT_STORAGE_KEY\)/)
  // 暂存面（键常量之后的实现）只写已校验站内路径：不写令牌、不写 Cookie、不跨标签持久化
  const stagingSurface = navigation.slice(navigation.indexOf('export const APP_REDIRECT_STORAGE_KEY'))
  assert.ok(stagingSurface.length > 200, '暂存实现应集中在同一段')
  assert.doesNotMatch(stagingSurface, /localStorage|document\.cookie|Bearer|access_token/i, '暂存面不得写入令牌或跨标签持久数据')
  assert.doesNotMatch(stagingSurface, /window\.location\.href|https?:\/\//, '暂存面不得拼出外部跳转地址')
  // 非社交链不共用暂存通道：登录页与门户登录页只消费路由 query
  for (const [name, source] of [['login', login], ['portal-login', portal]] as const) {
    assert.doesNotMatch(source, /omni-app-redirect|stageAppRedirect|consumeAppRedirect/, `${name} 不使用暂存通道`)
    assert.match(source, /from '@\/utils\/navigation'/, `${name} 应导入集中解析器`)
  }
})
