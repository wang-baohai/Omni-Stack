import type { ApiResponse, PageResult } from '../../src/types/api'
import type { AuthLoginPolicy } from '../../src/api/system-config'
import type { LoginRecord } from '../../src/api/login-record'
import { test, expect, api, ok, POLICY, fourShots, messages } from './fixtures'
import { checkDatabase, command, container, controlledCaptcha, ensure, evidence, redis, sql } from './target'

// 每个用例只代表标题中的子合同，不能把局部通过提升为 FA/FB/FC 整门通过。
test('GUARD 错 UUID 实际拒绝，正确 UUID 随后仍可核验', async () => {
  expect(() => checkDatabase('00000000-0000-0000-0000-000000000000')).toThrow(/UUID/)
  checkDatabase()
})

test('FA01 实际验证码图片、TTL、错误输入和重复消费', async ({ world }) => {
  const generated = await ok<{ captchaKey: string; captchaImage: string }>(world.request, '/api/auth/captcha')
  ensure(generated.captchaImage.startsWith('data:image/png;base64,'), '正式图片链不是 PNG')
  const ttl = Number(redis('TTL', `captcha:${generated.captchaKey}`))
  expect(ttl).toBeGreaterThan(0)
  expect(ttl).toBeLessThanOrEqual(300)
  const c = controlledCaptcha()
  const body = { username: world.employee.username, password: world.employee.password, tenantId: 1, captchaKey: c.captchaKey, captchaCode: 'invalid-input' }
  expect((await api(world.request, '/api/auth/login', undefined, 'POST', body)).body.code).toBe(400)
  expect(redis('EXISTS', `captcha:${c.captchaKey}`)).toBe('0')
  body.captchaCode = c.captchaCode
  expect((await api(world.request, '/api/auth/login', undefined, 'POST', body)).body.code).toBe(400)
})

test('FA01 两个并发 HTTP 请求不能消费同一验证码两次', async ({ world }) => {
  const c = controlledCaptcha()
  const body = { username: world.employee.username, password: world.employee.password, tenantId: 1, captchaKey: c.captchaKey, captchaCode: c.captchaCode }
  // 不把已知 GET/DELETE 源码竞态隐藏为 PASS：真实并发结果必须满足最多一次成功。
  const results = await Promise.all([api(world.request, '/api/auth/login', undefined, 'POST', body), api(world.request, '/api/auth/login', undefined, 'POST', body)])
  expect(results.filter(r => r.body.code === 200)).toHaveLength(1)
  expect(results.filter(r => r.body.code === 400)).toHaveLength(1)
  const success = results.find(r => r.body.code === 200)?.body.data as { accessToken: string }
  world.employee.token = success.accessToken
})

test('FA02 浏览器真实密码登录与四语已认证结果', async ({ world }) => {
  const page = await world.page()
  await world.browserLogin(page, world.admin)
  await page.goto('/admin/system/config')
  await expect(page.locator('.policy-form')).toBeVisible()
  await fourShots(page, 'password-captcha', 'authenticated-config', world.admin.id, async locale => {
    await expect(page.locator('.card-header')).toContainText(messages[locale].systemConfig.title)
    await expect(page.locator('.policy-form').getByRole('spinbutton').first()).toHaveValue(String(world.initialPolicy.failureThreshold))
  }, true)
})

test('FA03 浏览器注册、默认 USER、BCrypt 与四语成功后登录入口', async ({ world }) => {
  const page = await world.page()
  const c = await world.browserCaptcha(page)
  await page.goto('/register')
  await expect(page.locator('.captcha-image img')).toBeVisible()
  const username = world.name('registered')
  const password = world.employee.password
  const actor = { id: '', username, password, token: '' }
  world.actors.push(actor)
  await page.getByPlaceholder(messages['zh-CN'].register.username, { exact: true }).fill(username)
  await page.getByPlaceholder(messages['zh-CN'].register.password, { exact: true }).fill(password)
  await page.getByPlaceholder(messages['zh-CN'].register.confirmPassword, { exact: true }).fill(password)
  evidence('register-selector.json', await page.getByRole('combobox').evaluate(element => ({ tag: element.tagName, width: element.getBoundingClientRect().width, height: element.getBoundingClientRect().height, parentClass: element.parentElement?.className })))
  await test.step('打开注册租户选择器', () => page.locator('.register-card .el-select__wrapper').click())
  // 以真实公开租户结果定位，不依赖租户排序。
  const tenants = await ok<{ id: number; name: string }[]>(world.request, '/api/auth/tenants')
  const tenant = tenants.find(t => Number(t.id) === 1)
  ensure(tenant, 'F 租户 1 不可注册')
  await test.step('选中注册目标租户', () => page.getByRole('option', { name: tenant.name, exact: true }).click())
  await page.locator('.captcha-input input').fill(c().captchaCode)
  const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/register' && r.request().method() === 'POST')
  await test.step('提交真实注册表单', () => page.locator('.register-btn').click())
  const result = await response
  expect((await result.json() as ApiResponse<unknown>).code).toBe(200)
  await expect(page).toHaveURL(/\/login$/)
  actor.id = sql(`SELECT id FROM sys_user WHERE tenant_id=1 AND username='${username}'`)
  ensure(/^\d+$/.test(actor.id), '浏览器注册没有实际用户')
  expect(sql(`SELECT password LIKE '$2%' FROM sys_user WHERE id=${actor.id} AND tenant_id=1`)).toBe('1')
  expect(sql(`SELECT r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=${actor.id}`)).toBe('USER')
  actor.token = await world.login(actor)
  world.recordActors()
  await page.getByPlaceholder(messages['zh-CN'].login.username, { exact: true }).fill(username)
  await fourShots(page, 'register', 'created-login-entry', actor.id, async locale => {
    await expect(page.locator('.login-form-subtitle')).toHaveText(messages[locale].login.title)
    await expect(page.getByPlaceholder(messages[locale].login.username, { exact: true })).toHaveValue(username)
  }, true)
})

test('FA03 同租户重复注册不得多建用户', async ({ world }) => {
  const c = controlledCaptcha()
  const result = await api(world.request, '/api/auth/register', undefined, 'POST', { username: world.employee.username, password: world.employee.password, tenantId: 1, captchaKey: c.captchaKey, captchaCode: c.captchaCode })
  expect(result.body.code).not.toBe(200)
  expect(sql(`SELECT COUNT(*) FROM sys_user WHERE tenant_id=1 AND username='${world.employee.username}'`)).toBe('1')
})

test('FC01 浏览器保存策略、真实 DB/审计对账、四语同版本截图', async ({ world }) => {
  const page = await world.page(world.admin)
  try {
    await page.goto('/admin/system/config')
    await expect(page.locator('.policy-form')).toBeVisible()
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    await page.locator('.policy-form').getByRole('spinbutton').first().fill('4')
    await page.locator('.policy-form').getByRole('spinbutton').nth(1).fill('7200')
    const response = page.waitForResponse(r => new URL(r.url()).pathname === POLICY && r.request().method() === 'PUT')
    await page.getByRole('button', { name: messages['zh-CN'].systemConfig.save, exact: true }).click()
    const result = await response
    const saved = await result.json() as ApiResponse<AuthLoginPolicy>
    expect(saved.code).toBe(200)
    expect(sql('SELECT CONCAT(failure_threshold,\'|\',failure_window_seconds) FROM sys_auth_login_policy WHERE tenant_id=1')).toBe('4|7200')
    expect(Number(sql(`SELECT COUNT(*) FROM sys_audit_log WHERE tenant_id=1 AND event_type='AUTH_CONFIG_UPDATED' AND create_by='${world.admin.username}'`))).toBeGreaterThan(0)
    await fourShots(page, 'config', 'saved', `policy:1:v${saved.data.version}`, async locale => {
      await expect(page.locator('.card-header')).toContainText(messages[locale].systemConfig.title)
      await expect(page.locator('.policy-form').getByRole('spinbutton').first()).toHaveValue('4')
      await expect(page.locator('.policy-form').getByRole('spinbutton').nth(1)).toHaveValue('7200')
      await expect(page.locator('.effective-note')).toContainText(messages[locale].systemConfig.effectiveNote)
    }, true)
  } finally { await world.restorePolicy() }
})

test('FC01 两个真实 CAS 请求恰一成功', async ({ world }) => {
  try {
    const p = await ok<AuthLoginPolicy>(world.request, POLICY, world.admin)
    const data = { failureThreshold: 5, failureWindowSeconds: 3600, expectedVersion: p.version }
    const results = await Promise.all([api(world.request, POLICY, world.admin, 'PUT', data), api(world.request, POLICY, world.admin, 'PUT', data)])
    expect(results.map(r => r.body.code).sort()).toEqual([200, 409])
  } finally { await world.restorePolicy() }
})

test('FC01 非法策略范围必须 HTTP400，不能用业务400替代', async ({ world }) => {
  const p = await ok<AuthLoginPolicy>(world.request, POLICY, world.admin)
  const result = await api(world.request, POLICY, world.admin, 'PUT', { failureThreshold: 2, failureWindowSeconds: 3600, expectedVersion: p.version })
  expect(result.body.code).toBe(400)
  expect(result.http).toBe(400)
})

test('FA04 策略变更不重设在途 HASH 窗口、达到阈值后拒绝', async ({ world }) => {
  const actor = await world.register('lock')
  const key = `login:fail:1:${actor.username}`
  try {
    let p = await ok<AuthLoginPolicy>(world.request, POLICY, world.admin)
    p = await ok<AuthLoginPolicy>(world.request, POLICY, world.admin, 'PUT', { failureThreshold: 3, failureWindowSeconds: 3600, expectedVersion: p.version })
    const fail = async () => {
      const c = controlledCaptcha()
      return api(world.request, '/api/auth/login', undefined, 'POST', { username: actor.username, password: 'wrong-password', tenantId: 1, captchaKey: c.captchaKey, captchaCode: c.captchaCode })
    }
    expect((await fail()).body.code).toBe(401)
    expect(redis('TYPE', key)).toBe('hash')
    expect(redis('HGET', key, 'threshold')).toBe('3')
    const ttl = Number(redis('TTL', key))
    await ok(world.request, POLICY, world.admin, 'PUT', { failureThreshold: 8, failureWindowSeconds: 7200, expectedVersion: p.version })
    await fail(); await fail()
    expect((await fail()).body.code).toBe(423)
    expect(redis('HGET', key, 'threshold')).toBe('3')
    expect(Number(redis('TTL', key))).toBeLessThanOrEqual(ttl)
    evidence('lock-window.json', { username: actor.username, threshold: 3, ttlAtFirstFailure: ttl, ttlAfter: Number(redis('TTL', key)), auditIds: sql(`SELECT id FROM sys_audit_log WHERE tenant_id=1 AND username='${actor.username}' AND event_type='ACCOUNT_LOCKED'`) })
  } finally { await world.restorePolicy() }
})

test('FC03/FC04 真实登录记录筛选、COUNT、白名单脱敏与四语列表/空态', async ({ world }) => {
  const username = world.admin.username
  await expect.poll(async () => (await ok<PageResult<LoginRecord>>(world.request, `/api/auth/login-record/list?username=${username}&size=100`, world.admin)).total).toBeGreaterThan(0)
  const list = await ok<PageResult<LoginRecord>>(world.request, `/api/auth/login-record/list?username=${username}&size=100`, world.admin)
  const count = Number(sql(`SELECT COUNT(*) FROM sys_audit_log WHERE tenant_id=1 AND username='${username}' AND event_type IN ('LOGIN_SUCCESS','LOGIN_FAILED') AND create_time>=DATE_SUB(NOW(),INTERVAL 7 DAY)`))
  expect(Number(list.total)).toBe(count)
  for (const row of list.records) {
    expect(['SUCCESS', 'FAILURE']).toContain(row.result)
    expect(Object.keys(row).sort()).toEqual(['id', 'createTime', 'username', 'result', 'method', 'provider', 'reasonCode', 'ipMasked', 'clientSummary'].sort())
    expect(row.ipMasked === 'unavailable' || row.ipMasked.includes('*')).toBe(true)
  }
  const page = await world.page(world.admin)
  await page.goto('/admin/system/loginrecord')
  await page.getByPlaceholder(messages['zh-CN'].loginRecord.usernamePlaceholder).fill(username)
  await page.getByRole('button', { name: messages['zh-CN'].common.search, exact: true }).click()
  await fourShots(page, 'login-record', 'filtered', `audit:${list.records.map(r => r.id).join(',')}`, async locale => {
    await expect(page.locator('.card-header')).toContainText(messages[locale].loginRecord.title)
    await expect(page.locator('.el-table__body tbody tr').first()).toContainText(username)
    await page.locator('.el-table__body tbody tr').first().getByText(username, { exact: true }).scrollIntoViewIfNeeded()
  })
  await page.getByPlaceholder(messages['ko-KR'].loginRecord.usernamePlaceholder).fill(`${username}-no-match`)
  await page.getByRole('button', { name: messages['ko-KR'].common.search, exact: true }).click()
  await fourShots(page, 'login-record', 'empty', `${username}-no-match`, async locale => {
    await expect(page.locator('.card-header')).toContainText(messages[locale].loginRecord.title)
    await expect(page.locator('.el-table__empty-text')).toHaveText(messages[locale].loginRecord.empty)
  })
})

test('FC03 非法分页必须 HTTP400，不能用业务400替代', async ({ world }) => {
  const result = await api(world.request, '/api/auth/login-record/list?size=999', world.admin)
  expect(result.body.code).toBe(400)
  expect(result.http).toBe(400)
})

test('FA09/FB03 已认证 USER 为403，缺 Token 为401，直连 Auth 拒绝', async ({ world }) => {
  for (const path of [POLICY, '/api/auth/login-record/list']) {
    expect((await api(world.request, path)).http).toBe(401)
    expect((await api(world.request, path, world.employee)).http).toBe(403)
    expect((await api(world.request, path, world.admin)).http).toBe(200)
  }
  const direct = await world.request.get('http://localhost:48100/api/auth/system-config/login-policy', { maxRedirects: 0 })
  expect(direct.status()).toBe(403)
})

test('FA10 定点踢出与真实网关401', async ({ world }) => {
  const actor = await world.register('kick')
  expect((await api(world.request, '/api/auth/menus', actor)).http).toBe(200)
  await ok(world.request, `/api/auth/online/${actor.id}`, world.admin, 'DELETE')
  expect((await api(world.request, '/api/auth/menus', actor)).http).toBe(401)
})

test('FC02 真实 MySQL 暂停时策略读取失败关闭，恢复后成功', async ({ world }) => {
  // 使用 pause 保留连接与数据；finally 必须恢复，本门不宣称细粒度审计回滚已验证。
  checkDatabase()
  command('docker', ['pause', container('mysql')])
  try {
    const response = await world.request.get('/api/auth/system-config/login-policy', { headers: { Authorization: `Bearer ${world.admin.token}`, 'X-Tenant-Id': '1' }, timeout: 40_000 })
    expect(response.status()).toBeGreaterThanOrEqual(500)
  } finally {
    command('docker', ['unpause', container('mysql')])
  }
  expect((await api(world.request, POLICY, world.admin)).http).toBe(200)
})
