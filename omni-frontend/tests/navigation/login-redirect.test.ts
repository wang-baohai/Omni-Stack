/**
 * @module navigation/login-redirect.test
 * @description `utils/navigation.ts` 登录回跳校验与落点解析的行为定向测试（node:test，不依赖浏览器、路由单例、Pinia 或 i18n 实例）。
 *
 * 特意置于 omni-frontend/tests/navigation/ 下（不在 src/），与既有 tests/wp012、tests/workflow、tests/base 一同被
 * `test:unit` 发现，并用 node --experimental-strip-types 直接加载真实模块：navigation.ts 是纯函数，
 * 不 import router/index.ts（其 createWebHistory 在模块加载即依赖浏览器环境）。
 *
 * 断言针对「输出取值」而非「实现函数被调用」：站内合法目标原样保留（含 path/query/hash 与编码字符）、
 * 非字符串与空值一律拒绝、外站/协议相对/反斜线/控制字符/非法转义拒绝、编码后才成为协议相对的二次校验、
 * 认证入口页构成登录循环而 OAuth2 与设备码协议页必须原样可回、三类入口各自默认落点符合冻结行为表、
 * 恶意目标不被统一丢到工作台、纯函数确定且不改写输入；末尾四条按源码接线取证登录页、门户登录页与
 * router 守卫确实只消费这一处校验器（协议相对判断在输入面内只出现一次）。
 *
 * 运行方式：`node --experimental-strip-types --test tests/navigation/login-redirect.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

import { APP_LANDINGS, resolveAppLanding, safeAppRedirect } from '../../src/utils/navigation.ts'

test('站内合法深链接原样返回，path、query 与 hash 全部保留', () => {
  assert.equal(safeAppRedirect('/admin/base/user?id=1&page=2#section'), '/admin/base/user?id=1&page=2#section')
  assert.equal(safeAppRedirect('/workspace/jobs'), '/workspace/jobs')
  assert.equal(safeAppRedirect('/'), '/')
})

test('已编码的合法中文查询参数保留，解码后仍是站内路径', () => {
  const value = '/admin/base/user?keyword=%E4%B8%AD%E6%96%87'
  assert.equal(safeAppRedirect(value), value)
})

test('非字符串与空值一律拒绝：数组、对象、null、undefined、数字、空串与纯空白', () => {
  assert.equal(safeAppRedirect(['/admin/a', '/admin/b']), undefined)
  assert.equal(safeAppRedirect({ path: '/admin/a' }), undefined)
  assert.equal(safeAppRedirect(null), undefined)
  assert.equal(safeAppRedirect(undefined), undefined)
  assert.equal(safeAppRedirect(404), undefined)
  assert.equal(safeAppRedirect(''), undefined)
  assert.equal(safeAppRedirect('   '), undefined)
})

test('数组输入不被就地修改', () => {
  const value = ['/admin/a', '//evil.com']
  safeAppRedirect(value)
  assert.deepEqual(value, ['/admin/a', '//evil.com'])
})

test('外站、协议相对与相对路径全部拒绝', () => {
  assert.equal(safeAppRedirect('https://evil.example.com/x'), undefined)
  assert.equal(safeAppRedirect('//evil.example.com'), undefined)
  assert.equal(safeAppRedirect('/\\evil.example.com'), undefined)
  assert.equal(safeAppRedirect('admin/base/user'), undefined)
  assert.equal(safeAppRedirect('javascript:alert(1)'), undefined)
})

test('含反斜线与控制字符的目标拒绝，制表符与换行同样拒绝', () => {
  assert.equal(safeAppRedirect('/admin\\base\\user'), undefined)
  assert.equal(safeAppRedirect('/admin/base\u0000user'), undefined)
  assert.equal(safeAppRedirect('/admin/base\nuser'), undefined)
  assert.equal(safeAppRedirect('/admin/base\tuser'), undefined)
  assert.equal(safeAppRedirect('/admin/base\u007fuser'), undefined)
})

test('首尾空白的目标拒绝，避免拼出带空格的跳转地址', () => {
  assert.equal(safeAppRedirect(' /admin/base/user'), undefined)
  assert.equal(safeAppRedirect('/admin/base/user '), undefined)
})

test('编码后才成为协议相对或跳出站外的目标被二次校验拒绝', () => {
  assert.equal(safeAppRedirect('%2F%2Fevil.example.com'), undefined)
  assert.equal(safeAppRedirect('/%2F%2Fevil.example.com'), undefined)
  assert.equal(safeAppRedirect('%2f%2fevil.example.com'), undefined)
  assert.equal(safeAppRedirect('/%00admin'), undefined)
})

test('非法百分号转义序列拒绝，不把无法解码的目标交给路由', () => {
  assert.equal(safeAppRedirect('/admin/%zz'), undefined)
  assert.equal(safeAppRedirect('/admin/%'), undefined)
  assert.equal(safeAppRedirect('/admin/%E4%B8'), undefined)
})

test('认证入口页构成登录循环，前缀后必须紧跟路径边界', () => {
  assert.equal(safeAppRedirect('/login'), undefined)
  assert.equal(safeAppRedirect('/login?redirect=/admin/base/user'), undefined)
  assert.equal(safeAppRedirect('/login#frag'), undefined)
  assert.equal(safeAppRedirect('/portal-login'), undefined)
  assert.equal(safeAppRedirect('/register'), undefined)
  assert.equal(safeAppRedirect('/portal-register'), undefined)
  assert.equal(safeAppRedirect('/menu-load-error'), undefined)
  assert.equal(safeAppRedirect('/menu-load-error?redirect=%2F%2Fevil.example.com'), undefined)
  assert.equal(safeAppRedirect('/login-history'), '/login-history')
  assert.equal(safeAppRedirect('/registered-orders'), '/registered-orders')
})

test('OAuth2 与设备码协议页目标原样可回，不被改写成普通工作台或首页跳转', () => {
  assert.equal(safeAppRedirect('/callback?code=abc&state=xyz'), '/callback?code=abc&state=xyz')
  assert.equal(safeAppRedirect('/consent'), '/consent')
  assert.equal(safeAppRedirect('/device'), '/device')
  assert.equal(safeAppRedirect('/device/verify'), '/device/verify')
})

test('官网入口：合法原目标优先，无目标或恶意目标都回官网首页而不是工作台', () => {
  assert.equal(resolveAppLanding('/admin/base/user?id=1', { entry: 'site' }), '/admin/base/user?id=1')
  assert.equal(resolveAppLanding(undefined, { entry: 'site' }), APP_LANDINGS.site)
  assert.equal(resolveAppLanding('//evil.example.com', { entry: 'site' }), '/')
})

test('门户入口：合法原目标优先，否则落供应商门户，官网规则不受影响', () => {
  assert.equal(resolveAppLanding('/supplier-portal/evaluation', { entry: 'portal' }), '/supplier-portal/evaluation')
  assert.equal(resolveAppLanding(null, { entry: 'portal' }), APP_LANDINGS.portal)
  assert.equal(resolveAppLanding('/login', { entry: 'portal' }), '/supplier-portal')
})

test('控制台入口：合法原目标优先，否则落个人工作台，资产本人落自己的资产页', () => {
  assert.equal(resolveAppLanding('/admin/crm/opportunity', { entry: 'console' }), '/admin/crm/opportunity')
  assert.equal(resolveAppLanding('/menu-load-error?redirect=/admin', { entry: 'console' }), '/workspace')
  assert.equal(
    resolveAppLanding(undefined, { entry: 'console', assetSelfServiceOnly: true }),
    APP_LANDINGS.assetSelf,
  )
  assert.equal(resolveAppLanding(undefined, { entry: 'console', assetSelfServiceOnly: false }), '/workspace')
})

test('落点常量与冻结行为表一致', () => {
  assert.deepEqual(APP_LANDINGS, {
    site: '/',
    console: '/workspace',
    portal: '/supplier-portal',
    assetSelf: '/admin/asset/asset',
  })
})

test('同一输入重复解析结果确定且无内部缓存，不缓存上一次的落点', () => {
  assert.equal(safeAppRedirect('/admin/base/user'), '/admin/base/user')
  assert.equal(safeAppRedirect('//evil.example.com'), undefined)
  assert.equal(safeAppRedirect('/admin/base/user'), '/admin/base/user')
  assert.equal(resolveAppLanding('/admin/a', { entry: 'site' }), resolveAppLanding('/admin/a', { entry: 'console' }))
  assert.equal(resolveAppLanding('//evil.example.com', { entry: 'site' }), '/')
  assert.equal(resolveAppLanding('//evil.example.com', { entry: 'portal' }), '/supplier-portal')
})

test('resolveAppLanding 绝不返回外站目标，返回值总是站内绝对路径', () => {
  const hostile = [
    '//evil.example.com',
    'https://evil.example.com',
    '/\\evil.example.com',
    '%2F%2Fevil.example.com',
    '/%2F%2Fevil.example.com',
    '/login',
    null,
    undefined,
    ['/admin/a'],
    '',
  ]
  for (const entry of ['site', 'portal', 'console'] as const) {
    for (const value of hostile) {
      const landed = resolveAppLanding(value, { entry })
      assert.ok(landed.startsWith('/') && !landed.startsWith('//'), `${entry} <- ${String(value)} => ${landed}`)
      assert.ok(!landed.includes('\\'), `${entry} <- ${String(value)} => ${landed}`)
    }
  }
})

/** SFC 与 router 入口在 Node 里无法实例化（createWebHistory 于模块顶层求值），这里按源码接线取证。 */
function read(relative: string): string {
  return readFileSync(new URL(relative, import.meta.url), 'utf8')
}

test('官网登录页只把已校验目标交给路由，无目标时回官网而不是工作台', () => {
  const source = read('../../src/views/login/index.vue')
  assert.match(source, /safeAppRedirect\(route\.query\.redirect\)/, '深链接目标必须先经集中校验')
  assert.match(source, /router\.push\(redirect\)/, '合法深链接原样回跳')
  assert.match(source, /resolveAppLanding\(undefined, \{ entry:/, '无回跳目标时按入口身份解析默认落点')
  assert.doesNotMatch(source, /['"]\/workspace['"]/, '官网登录不得统一丢到个人工作台')
  // 已有供应商规则仍有效：角色与门户入口判定保持原语义。
  assert.match(source, /roles\.includes\('SUPPLIER'\)/)
  assert.match(source, /hasManagementAccess\(/)
  assert.match(source, /'srm:portal:enroll'/)
  assert.doesNotMatch(source, /['"]\/supplier-portal['"]/, '落点字面量只由 navigation 提供')
})

test('门户登录页按门户入口解析回跳，且不出现其它入口的落点字面量', () => {
  const source = read('../../src/views/portal-login/index.vue')
  assert.match(source, /resolveAppLanding\(route\.query\.redirect, \{ entry: 'portal' \}\)/)
  assert.doesNotMatch(source, /['"]\/workspace['"]|['"]\/supplier-portal['"]|['"]\/admin\/asset\/asset['"]/)
})

test('守卫的降级页目标只来自集中解析，裸 fullPath 不再写进 redirect', () => {
  const source = read('../../src/router/index.ts')
  assert.doesNotMatch(source, /redirect: to\.fullPath/, '旧的未校验写入方必须消失')
  assert.match(source, /resolveAppLanding\(to\.fullPath,/, '降级目标经集中解析')
  assert.match(source, /entry: 'console', assetSelfServiceOnly/, '控制台上下文按资产本人身份选择落点')
  assert.match(source, /if \(to\.name === 'MenuLoadError'\)/, '伪造的降级页目标在守卫内再校验')
  assert.match(source, /return APP_LANDINGS\.site/, '官网发起登录回官网')
  assert.match(source, /return APP_LANDINGS\.assetSelf/, '资产本人规则仍有效')
  assert.match(source, /safeAppRedirect\(to\.fullPath\)/, '未登录时的 redirect 参数仍由同一校验器产出')
})

test('回跳校验只有一处实现，本步输入面全部委托给集中解析器', () => {
  const consumers = [
    '../../src/views/login/index.vue',
    '../../src/views/portal-login/index.vue',
    '../../src/router/index.ts',
    '../../src/api/request.ts',
  ]
  for (const relative of consumers) {
    const source = read(relative)
    assert.match(source, /from '@\/utils\/navigation'/, `${relative} 应导入集中解析器`)
    assert.doesNotMatch(source, /startsWith\('\/\/'\)/, `${relative} 不得自行分叉实现协议相对判断`)
  }
  assert.match(read('../../src/utils/navigation.ts'), /startsWith\('\/\/'\)/, '协议相对判断的唯一来源')
})
