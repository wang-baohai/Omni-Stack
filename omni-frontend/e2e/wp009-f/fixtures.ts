import { test as base, expect, type APIRequestContext, type Browser, type BrowserContext, type Page } from '@playwright/test'
import { randomBytes } from 'node:crypto'
import { resolve } from 'node:path'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import type { ApiResponse } from '../../src/types/api'
import type { AuthLoginPolicy } from '../../src/api/system-config'
import zh from '../../src/locales/zh-CN'
import en from '../../src/locales/en-US'
import ja from '../../src/locales/ja-JP'
import ko from '../../src/locales/ko-KR'
import { ARTIFACTS, EXECUTION, F_UUID, ORIGIN, ROOT, RUN, checkDatabase, controlledCaptcha, ensure, evidence, inspect, preflight, redis, sha, sql } from './target'

export const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'] as const
export type Locale = typeof locales[number]
export const messages = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
export interface Actor { id: string; username: string; password: string; token: string }
export const POLICY = '/api/auth/system-config/login-policy'
const evidenceRows: object[] = []
const issuedTokens = new Set<string>()
export function rememberToken(token: string) { issuedTokens.add(token) }

/** 只把 HTTP/业务状态写入证据，凭据、原始认证报文始终只在进程内。 */
export async function api<T>(request: APIRequestContext, path: string, actor?: Actor, method = 'GET', data?: unknown) {
  ensure(/^\/(api|oauth2)\//.test(path) && !path.includes('://'), '拒绝非 F 路径')
  checkDatabase()
  const response = await request.fetch(`${ORIGIN}${path}`, {
    method, data, maxRedirects: 0, timeout: 20_000,
    headers: { 'X-Tenant-Id': '1', ...(actor?.token ? { Authorization: `Bearer ${actor.token}` } : {}) },
  })
  const body = await response.json().catch(() => ({ code: -1, data: null })) as ApiResponse<T>
  if (path === '/api/auth/login' && body.code === 200) rememberToken((body.data as { accessToken: string }).accessToken)
  evidenceRows.push({ at: new Date().toISOString(), method, path: path.split('?')[0], actor: actor?.id || 'public', http: response.status(), code: body.code })
  evidence(`http-statuses-${process.pid}.json`, evidenceRows)
  return { http: response.status(), body }
}

export async function ok<T>(request: APIRequestContext, path: string, actor?: Actor, method = 'GET', data?: unknown): Promise<T> {
  const result = await api<T>(request, path, actor, method, data)
  ensure(result.http === 200 && result.body.code === 200, `${method} ${path.split('?')[0]} 未成功：HTTP=${result.http}, code=${result.body.code}`)
  return result.body.data
}

export class World {
  actors: Actor[] = []
  contexts: BrowserContext[] = []
  admin!: Actor
  employee!: Actor
  initialPolicy!: AuthLoginPolicy
  private sequence = 0
  private prefix = `WP009-${randomBytes(5).toString('hex')}`
  private images: ReturnType<typeof preflight> | undefined

  constructor(readonly request: APIRequestContext, readonly browser: Browser) {}

  async init() {
    this.images = preflight()
    const frontend = await this.request.get(ORIGIN, { maxRedirects: 0 })
    ensure(frontend.headers()['x-omni-test-environment'] === 'wp009f', '前端缺少 F 标识')
    this.admin = await this.register('admin', true)
    this.employee = await this.register('employee')
    this.initialPolicy = await ok<AuthLoginPolicy>(this.request, POLICY, this.admin)
    evidence('runtime.json', { run: RUN, execution: EXECUTION, uuid: F_UUID, origin: ORIGIN, runtime: this.images, capturedAt: new Date().toISOString(), source: { config: sha(resolve(ROOT, 'omni-frontend/src/views/system/config/index.vue')), loginRecord: sha(resolve(ROOT, 'omni-frontend/src/views/system/loginrecord/index.vue')) }, binding: '容器 image ID 与本轮前端构建日志绑定；不代表 M 已部署' })
  }

  name(suffix: string) { return `${this.prefix}-${++this.sequence}-${suffix}` }

  async register(suffix: string, admin = false): Promise<Actor> {
    const username = this.name(suffix)
    const password = `Pw!${randomBytes(16).toString('hex')}`
    const captcha = controlledCaptcha()
    ensure(sql(`SELECT COUNT(*) FROM sys_user WHERE tenant_id=1 AND username='${username}'`) === '0', '新自然键已存在，禁止重造')
    const actor: Actor = { id: '', username, password, token: '' }
    this.actors.push(actor)
    await ok(this.request, '/api/auth/register', undefined, 'POST', { username, password, tenantId: 1, captchaKey: captcha.captchaKey, captchaCode: captcha.captchaCode })
    actor.id = sql(`SELECT id FROM sys_user WHERE tenant_id=1 AND username='${username}'`)
    ensure(/^\d+$/.test(actor.id), '未能对账实际注册用户 ID')
    ensure(sql(`SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.id WHERE ur.user_id=${actor.id} AND r.role_code='USER' AND r.tenant_id=1`) === '1', '注册未分配默认 USER')
    if (admin) {
      // F 受控身份只关联真实模板角色；不是拼 claims，也不操作旧账户。
      sql(`INSERT IGNORE INTO sys_user_role(user_id,role_id) SELECT ${actor.id},id FROM sys_role WHERE tenant_id=1 AND role_code='SUPER_ADMIN' AND status=1`)
    }
    actor.token = await this.login(actor)
    this.recordActors()
    return actor
  }

  recordActors() {
    evidence(`actors-${this.prefix.toLowerCase()}.json`, { run: RUN, actors: this.actors.map(a => ({ id: a.id, username: a.username, tenant: 1 })), identity: '真实注册/DB角色关联，真实 Auth 签发；秘密不持久化' })
  }

  async login(actor: Actor, password = actor.password) {
    const c = controlledCaptcha()
    const result = await ok<{ accessToken: string }>(this.request, '/api/auth/login', undefined, 'POST', { username: actor.username, password, tenantId: 1, captchaKey: c.captchaKey, captchaCode: c.captchaCode })
    ensure(Boolean(result.accessToken), '真实登录未返回 Token')
    return result.accessToken
  }

  async page(actor?: Actor) {
    const context = await this.browser.newContext({ baseURL: ORIGIN, viewport: { width: 1440, height: 900 }, locale: 'zh-CN', timezoneId: 'Asia/Shanghai', colorScheme: 'light', serviceWorkers: 'block' })
    this.contexts.push(context)
    context.setDefaultTimeout(15_000)
    context.setDefaultNavigationTimeout(30_000)
    // 跨源请求直接拒绝，页面或协议重定向不能误访 M/旧 F/第三方。
    await context.route('**/*', route => {
      const url = new URL(route.request().url())
      return url.origin === ORIGIN || ['data:', 'blob:'].includes(url.protocol) ? route.continue() : route.abort('blockedbyclient')
    })
    await context.addInitScript(({ token, username }) => {
      localStorage.setItem('last_tenant_id', '1')
      if (token) { localStorage.setItem('token', token); localStorage.setItem('username', username) }
    }, { token: actor?.token || '', username: actor?.username || '' })
    const page = await context.newPage()
    return page
  }

  async browserCaptcha(page: Page) {
    let current: ReturnType<typeof controlledCaptcha> | undefined
    await page.route('**/api/auth/captcha', async route => {
      current = controlledCaptcha()
      await route.fulfill({ contentType: 'application/json', json: { code: 200, message: 'success', data: { captchaKey: current.captchaKey, captchaImage: current.captchaImage } } })
    })
    return () => { ensure(current, '浏览器未请求受控 CAPTCHA'); return current }
  }

  async browserLogin(page: Page, actor: Actor) {
    const captcha = await this.browserCaptcha(page)
    await page.goto('/login')
    await expect(page.locator('.captcha-image img')).toBeVisible()
    await page.getByPlaceholder(zh.login.username, { exact: true }).fill(actor.username)
    await page.getByPlaceholder(zh.login.password, { exact: true }).fill(actor.password)
    await page.locator('.captcha-input input').fill(captcha().captchaCode)
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST')
    await page.locator('.login-btn').click()
    const r = await response
    const body = await r.json() as ApiResponse<{ accessToken: string }>
    ensure(r.status() === 200 && body.code === 200 && body.data.accessToken, '浏览器密码登录失败')
    actor.token = body.data.accessToken
    rememberToken(actor.token)
    await expect.poll(() => page.evaluate(() => Boolean(localStorage.getItem('token')))).toBe(true)
  }

  async restorePolicy() {
    if (!this.initialPolicy || !this.admin?.token) return
    const current = await ok<AuthLoginPolicy>(this.request, POLICY, this.admin)
    if (current.failureThreshold !== this.initialPolicy.failureThreshold || current.failureWindowSeconds !== this.initialPolicy.failureWindowSeconds) {
      await ok(this.request, POLICY, this.admin, 'PUT', { failureThreshold: this.initialPolicy.failureThreshold, failureWindowSeconds: this.initialPolicy.failureWindowSeconds, expectedVersion: current.version })
    }
  }

  async closePages() {
    for (const context of this.contexts.splice(0)) await context.close()
  }

  async close() {
    const actions: object[] = []
    const errors: string[] = []
    try { await this.restorePolicy(); actions.push({ policy: '通过版本化 PUT 恢复测试前数值，保留审计' }) } catch { errors.push('policy-restore') }
    for (const actor of [...this.actors].reverse()) {
      if (!actor.id) {
        // 超时先查询同一自然键，不再次注册。
        actor.id = sql(`SELECT id FROM sys_user WHERE tenant_id=1 AND username='${actor.username}'`)
      }
      if (!/^\d+$/.test(actor.id)) continue
      try {
        const before = await api(this.request, '/api/auth/menus', actor)
        if (before.http !== 401) await ok(this.request, `/api/auth/online/${actor.id}`, this.admin, 'DELETE')
        if (actor !== this.admin) await ok(this.request, `/api/auth/user/${actor.id}/status?status=0`, this.admin, 'PUT')
        const probe = await api(this.request, '/api/auth/menus', actor)
        actions.push({ userId: actor.id, suppliedTokenRejectedByKick: probe.http === 401 })
      } catch { errors.push(`user-${actor.id}`) }
    }
    // 管理员已自踢，只对本次实际 ID 做 F 夹具停用与角色解绑；不删安全历史。
    const ids = this.actors.map(a => a.id).filter(id => /^\d+$/.test(id))
    if (ids.length) {
      sql(`UPDATE sys_user SET status=0 WHERE tenant_id=1 AND id IN (${ids.join(',')}) AND username LIKE '${this.prefix}-%'`)
      sql(`DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.tenant_id=1 AND u.id IN (${ids.join(',')}) AND u.username LIKE '${this.prefix}-%'`)
    }
    let revoked = 0
    for (const token of issuedTokens) {
      try {
        const parts = token.split('.')
        ensure(parts.length === 3, '非三段式 JWT，拒绝清理')
        const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')) as { jti?: string; exp: number }
        // 密码登录 JWT 无 iss，仅 SAS/OAuth token 有；黑名单只按 jti 生效，故不强制 iss。
        if (!claims.jti || !/^[a-zA-Z0-9-]+$/.test(claims.jti)) { errors.push('token-no-jti'); continue }
        const ttl = Math.ceil(claims.exp - Date.now() / 1000)
        if (ttl > 0) redis('SET', `token:blacklist:${claims.jti}`, '1', 'EX', String(ttl + 10))
        const probe = await this.request.get(`${ORIGIN}/api/auth/menus`, { headers: { Authorization: `Bearer ${token}` }, maxRedirects: 0 })
        ensure(probe.status() === 401, '测试 JWT 清理后仍可访问')
        revoked++
      } catch { errors.push('issued-token-revoke') }
    }
    issuedTokens.clear()
    actions.push({ explicitFixtureTokenCleanup: revoked, mechanism: '仅本进程实际签发 jti 逐个黑名单并实测401；不作为产品 logout 证据' })
    await this.closePages()
    evidence(`cleanup-${this.prefix.toLowerCase()}.json`, { at: new Date().toISOString(), actions, errors, fixtureUsers: '保留停用用户与真实审计，解绑测试角色', olderTokens: '本进程实际签发 JWT 已逐个处理；此前 execution 历史 JWT 不纳入此声明', sessions: '关闭浏览器不等于服务端 session 撤销，协议测试会话需随 F 停止处置' })
    ensure(errors.length === 0, `F 清理存在未闭项目：${errors.join(',')}`)
  }
}

export const test = base.extend<object, { world: World }>({
  world: [async ({ playwright, browser }, use) => {
    const request = await playwright.request.newContext({ baseURL: ORIGIN })
    const world = new World(request, browser)
    try { await world.init(); await use(world) } finally { try { await world.close() } finally { await request.dispose() } }
  }, { scope: 'worker' }],
})
export { expect }
test.afterEach(async ({ world }) => { await world.closePages() })

const shots: object[] = existsSync(resolve(ARTIFACTS, 'screenshots.json')) ? JSON.parse(readFileSync(resolve(ARTIFACTS, 'screenshots.json'), 'utf8')) : []
export async function fourShots(page: Page, flow: string, state: string, object: string, assertion: (locale: Locale) => Promise<void>, tablet = false) {
  const viewports = [{ name: 'desktop', width: 1440, height: 900 }, { name: 'mobile', width: 390, height: 844 }, ...(tablet ? [{ name: 'tablet', width: 768, height: 1024 }] : [])]
  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    const sidebar = page.locator('.sidebar')
    if (await sidebar.count()) {
      const collapsed = await sidebar.evaluate(element => element.getBoundingClientRect().width < 100)
      if ((viewport.name === 'mobile') !== collapsed) await page.locator('.collapse-btn').click()
    }
    for (const locale of locales) {
      const current = await page.evaluate(() => localStorage.getItem('omni-lang') || 'zh-CN') as Locale
      if (current !== locale) {
        await page.getByRole('button', { name: messages[current].lang.switch, exact: true }).first().click()
        const key = { 'zh-CN': 'zhCN', 'en-US': 'enUS', 'ja-JP': 'jaJP', 'ko-KR': 'koKR' }[locale] as 'zhCN' | 'enUS' | 'jaJP' | 'koKR'
        await page.getByRole('menuitem', { name: messages[current].lang[key], exact: true }).click()
      }
      await expect(page.locator('html')).toHaveAttribute('lang', locale)
      await assertion(locale)
      await page.evaluate(() => document.fonts.ready)
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      const directory = resolve(ARTIFACTS, 'screenshots', locale)
      mkdirSync(directory, { recursive: true })
      const file = resolve(directory, `${flow}-${state}-${viewport.name}.png`)
      await page.screenshot({ path: file, fullPage: true, animations: 'disabled', mask: [page.locator('input[type=password]'), page.locator('.captcha-input'), page.locator('.captcha-image'), page.locator('.user-code-input'), page.locator('.device-qr-code')] })
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)
      shots.push({ file, sha256: sha(file), locale, viewport, flow, state, object, tenant: 1, route: new URL(page.url()).pathname, source: 'F_REAL_UI', captcha: 'CONTROLLED_CAPTCHA_FIXTURE', asserted: true, horizontalOverflow: overflow, at: new Date().toISOString(), executorPixelReview: 'PENDING', codexReview: 'NOT_DECLARED', frontendImage: inspect('frontend').image })
      evidence('screenshots.json', shots)
    }
  }
}
