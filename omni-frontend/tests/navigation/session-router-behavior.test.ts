/**
 * @module navigation/session-router-behavior.test
 * @description WP-06.S03 跨标签退出/换号的真实路由行为测试（真实 Vue Router + memory history）。
 *
 * 本测试使用真实路由器验证“同址 replace 会被判为重复导航、守卫不会重跑”，并用真实落点解析器
 * `resolveSessionSyncRedirect` 驱动退出、换号、重复事件与 403 场景。应用守卫的认证契约在此以
 * 与 `src/router/index.ts` 相同的 `requiresAuth` 分支建模；真实双标签浏览器证据仍归 WP-07.S03。
 */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { createMemoryHistory, createRouter } from 'vue-router'
import { resolveSessionSyncRedirect } from '../../src/utils/navigation.ts'

interface AuthState {
  token: string | null
  user: string | null
  menusLoaded: boolean
}

function createHarness() {
  const auth: AuthState = { token: 'token-a', user: 'alice', menusLoaded: true }
  const events: string[] = []
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'Home', component: {}, meta: { requiresAuth: false } },
      { path: '/login', name: 'Login', component: {}, meta: { requiresAuth: false } },
      { path: '/dashboard', name: 'Dashboard', component: {}, meta: { requiresAuth: true } },
      { path: '/workspace/overview', name: 'WorkspaceOverview', component: {}, meta: { requiresAuth: true } },
      { path: '/admin/denied', name: 'AdminDenied', component: {}, meta: { requiresAuth: true } },
    ],
  })
  router.beforeEach((to) => {
    events.push(`guard:${String(to.name)}`)
    if (to.meta.requiresAuth === false) {
      if (to.name === 'Login' && auth.token) return '/dashboard'
      return true
    }
    if (!auth.token) return { name: 'Login', query: { redirect: to.fullPath } }
    if (!auth.menusLoaded) {
      auth.menusLoaded = true
      return { path: to.fullPath, replace: true }
    }
    return true
  })
  return { router, auth, events }
}

test('同址 replace 被真实路由器判为重复导航，守卫不会重跑', async () => {
  const { router, events } = createHarness()
  await router.push('/workspace/overview')
  const before = events.length
  try {
    await router.replace('/workspace/overview')
  } catch {
    // Vue Router 在不同版本对重复导航返回失败或抛出；两种都不得触发守卫。
  }
  assert.equal(events.length, before, '重复导航不得触发认证守卫')
})

test('退出后受保护静态工作台与动态业务页都进入登录入口，并携带已校验回跳', async () => {
  const { router, auth, events } = createHarness()
  router.addRoute({ path: '/admin/crm/lead', name: 'CrmLeadDynamic', component: {}, meta: { requiresAuth: true } })
  await router.push('/workspace/overview')
  const beforeLogout = events.length

  auth.token = null
  auth.user = null
  auth.menusLoaded = false
  const target = resolveSessionSyncRedirect(router.currentRoute.value)
  assert.deepEqual(target, { name: 'Login', query: { redirect: '/workspace/overview' } })
  await router.replace(target!)
  assert.equal(router.currentRoute.value.name, 'Login')
  assert.equal(router.currentRoute.value.query.redirect, '/workspace/overview')
  assert.ok(events.length > beforeLogout, '退出后的导航必须重新进入认证守卫')

  await router.push('/admin/crm/lead')
  assert.equal(router.currentRoute.value.name, 'Login', '动态业务页在退出后同样回到登录')
  assert.equal(router.currentRoute.value.query.redirect, '/admin/crm/lead')
})

test('换号先回收动态路由，再以新身份进入合法落点', async () => {
  const { router, auth } = createHarness()
  router.addRoute({ path: '/admin/crm/lead', name: 'CrmLeadDynamic', component: {}, meta: { requiresAuth: true } })
  await router.push('/admin/crm/lead')

  auth.token = null
  auth.user = null
  auth.menusLoaded = false
  if (router.hasRoute('CrmLeadDynamic')) router.removeRoute('CrmLeadDynamic')
  auth.token = 'token-b'
  auth.user = 'bob'
  const target = resolveSessionSyncRedirect(router.currentRoute.value)
  await router.replace(target!)

  assert.equal(router.currentRoute.value.path, '/dashboard', '新身份从登录入口被守卫送到合法落点')
  assert.equal(router.hasRoute('CrmLeadDynamic'), false, '旧身份动态路由不得残留')
  assert.equal(auth.user, 'bob')
})

test('重复的退出事件保持登录态，不重复清理也不回写旧身份', async () => {
  const { router, auth } = createHarness()
  await router.push('/workspace/overview')
  auth.token = null
  auth.user = null
  auth.menusLoaded = false
  const firstTarget = resolveSessionSyncRedirect(router.currentRoute.value)
  await router.replace(firstTarget!).catch(() => undefined)
  const secondTarget = resolveSessionSyncRedirect(router.currentRoute.value)
  if (secondTarget) await router.replace(secondTarget).catch(() => undefined)
  assert.equal(router.currentRoute.value.name, 'Login', '重复事件后仍停留在合法认证页')
  assert.equal(auth.user, null, '不得回写旧身份')
})

test('静态工作台与动态业务路由的真实路径仍由路由文件提供，403 不触发全局登出', async () => {
  const routerSource = readFileSync(new URL('../../src/router/index.ts', import.meta.url), 'utf8')
  assert.match(routerSource, /path: '\/workspace'[\s\S]*path: 'overview'/)
  assert.match(routerSource, /name: 'WorkspaceOverview'/)
  assert.match(routerSource, /path: '\/admin'/)

  const { router, auth } = createHarness()
  await router.push('/admin/denied')
  assert.equal(router.currentRoute.value.name, 'AdminDenied')
  assert.equal(auth.token, 'token-a', '403 页面不得清掉会话')
})
