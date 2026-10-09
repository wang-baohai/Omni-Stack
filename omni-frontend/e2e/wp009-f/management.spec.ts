import type { PageResult } from '../../src/types/api'
import type { SysRole } from '../../src/api/role'
import type { SysOrgUnit } from '../../src/api/org'
import type { SysTenant } from '../../src/api/tenant'
import type { OAuth2ClientVO } from '../../src/api/oauth2-client'
import type { DictType, DictData } from '../../src/api/dict'
import type { XssSettings, BlacklistRule } from '../../src/api/xss-config'
import { test, expect, api, ok, fourShots } from './fixtures'
import { command, container, ensure, evidence, redis, sql } from './target'

test('FB01 正式租户创建复制五权限与 SUPER_ADMIN，保留初始化真实状态', async ({ world }) => {
  const name = world.name('tenant')
  const tenant = await ok<SysTenant>(world.request, '/api/auth/tenant', world.admin, 'POST', { tenantCode: name, tenantName: name, adminPassword: world.employee.password })
  try {
    expect(sql(`SELECT COUNT(*) FROM sys_permission WHERE tenant_id=${tenant.id} AND permission_code IN ('system:config','system:config:list','system:config:update','system:loginrecord','system:loginrecord:list')`)).toBe('5')
    expect(sql(`SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.tenant_id=${tenant.id} AND r.role_code='SUPER_ADMIN' AND p.permission_code IN ('system:config','system:config:list','system:config:update','system:loginrecord','system:loginrecord:list')`)).toBe('5')
    expect(sql(`SELECT COUNT(*) FROM sys_auth_login_policy WHERE tenant_id=${tenant.id}`)).toBe('0')
    evidence('tenant-provision.json', { id: tenant.id, naturalKey: name, provisioningStatus: tenant.provisioningStatus, permissionsCopied: 5, crossModuleActivation: '不以 Auth 同步目录复制冒充跨模块开通完成' })
  } finally { await ok(world.request, `/api/auth/tenant/${tenant.id}`, world.admin, 'PUT', { status: 0 }) }
})

test('FB02 组织创建/调整/删除与真实树、四语相同组织对象', async ({ world }) => {
  const name = world.name('org')
  const root = sql('SELECT id FROM sys_org_unit WHERE tenant_id=1 AND parent_id=0 ORDER BY id LIMIT 1')
  ensure(/^\d+$/.test(root), 'F 缺少组织根节点')
  const org = await ok<SysOrgUnit>(world.request, '/api/auth/org', world.admin, 'POST', { parentId: Number(root), name, type: 'DEPT', sort: 1, status: 1 })
  try {
    expect(org.path).toContain(`/${root}/`)
    await ok(world.request, `/api/auth/org/${org.id}`, world.admin, 'PUT', { name: `${name}-edited` })
    const page = await world.page(world.admin)
    await page.goto('/admin/system/org')
    await fourShots(page, 'organization', 'edited', String(org.id), async () => {
      await expect(page.locator('.node-label').filter({ hasText: `${name}-edited` }).first()).toBeVisible()
    })
  } finally { await ok(world.request, `/api/auth/org/${org.id}`, world.admin, 'DELETE') }
})

test('FB03 角色 CRUD 与普通用户自扩权拒绝', async ({ world }) => {
  const name = world.name('role')
  const input = { roleCode: name, roleName: name, dataScope: 'SELF', sort: 1, status: 1, permissionIds: [] }
  const role = await ok<SysRole>(world.request, '/api/auth/role', world.admin, 'POST', input)
  try {
    expect((await ok<SysRole>(world.request, `/api/auth/role/${role.id}`, world.admin)).dataScope).toBe('SELF')
    expect((await api(world.request, '/api/auth/role', world.employee, 'POST', { ...input, roleCode: `${name}-deny` })).http).toBe(403)
    await ok(world.request, `/api/auth/role/${role.id}`, world.admin, 'PUT', { roleName: `${name}-edited` })
    const page = await world.page(world.admin)
    await page.goto('/admin/system/role')
    // 表格默认分页可能不含新行；用页面搜索框过滤同一个真实对象。
    const search = page.locator('.el-form input').first()
    if (await search.count()) await search.fill(`${name}-edited`)
    const zh = await import('../../src/locales/zh-CN')
    const button = page.getByRole('button', { name: zh.default.common.search, exact: true })
    if (await button.count()) await button.click()
    await fourShots(page, 'role', 'edited-self-scope', String(role.id), async () => {
      await expect(page.getByText(`${name}-edited`, { exact: true }).first()).toBeVisible()
    })
  } finally { await ok(world.request, `/api/auth/role/${role.id}`, world.admin, 'DELETE') }
})

test('FB04 真实内部数据范围解析 ALL/SELF 与无内部凭据401', async ({ world }) => {
  const url = `http://localhost:48100/internal/data-scopes/${world.employee.id}?tenantId=1&permissionCode=system:user:list`
  const noAuth = await world.request.get(url)
  expect(noAuth.status()).toBe(401)
  const internal = command('docker', ['exec', container('auth'), 'sh', '-c', 'printf "%s" "$OMNI_INTERNAL_API_TOKEN"'])
  ensure(Boolean(internal), 'F 内部凭据缺失')
  for (const [actor, scope] of [[world.admin, 'ALL'], [world.employee, 'SELF']] as const) {
    const response = await world.request.get(`http://localhost:48100/internal/data-scopes/${actor.id}?tenantId=1&permissionCode=system:user:list`, { headers: { 'X-Internal-Token': internal, 'X-Tenant-Id': '1' } })
    const body = await response.json()
    expect(response.status()).toBe(200)
    ensure(body.code === 200 && body.data, '内部范围请求没有成功返回数据')
    expect(body.data.effectiveScope).toBe(scope)
  }
})

test('FB04b 真实内部数据范围解析 DEPT/CUSTOM/TENANT 各模式', async ({ world }) => {
  const internal = command('docker', ['exec', container('auth'), 'sh', '-c', 'printf "%s" "$OMNI_INTERNAL_API_TOKEN"'])
  ensure(Boolean(internal), 'F 内部凭据缺失')
  // 目标权限码的 permission id；角色必须持有该权限，dataScope 才对该 permissionCode 生效。
  const permId = sql('SELECT id FROM sys_permission WHERE tenant_id=1 AND permission_code=\'system:user:list\' LIMIT 1')
  ensure(/^\d+$/.test(permId), 'F 缺少 system:user:list 权限码')
  for (const scope of ['DEPT', 'CUSTOM', 'TENANT'] as const) {
    const role = await ok<SysRole>(world.request, '/api/auth/role', world.admin, 'POST', { roleCode: world.name(`role-${scope.toLowerCase()}`), roleName: world.name(`role-${scope.toLowerCase()}`), dataScope: scope, sort: 1, status: 1, permissionIds: [Number(permId)] })
    const actor = await world.register(`scope-${scope.toLowerCase()}`)
    // 该夹具用户默认 USER(SELF)；叠加目标 scope 角色后，effectiveScope 应为更高优先级的目标 scope。
    sql(`INSERT IGNORE INTO sys_user_role(user_id,role_id) VALUES (${actor.id},${role.id})`)
    try {
      const response = await world.request.get(`http://localhost:48100/internal/data-scopes/${actor.id}?tenantId=1&permissionCode=system:user:list`, { headers: { 'X-Internal-Token': internal, 'X-Tenant-Id': '1' } })
      const body = await response.json()
      expect(response.status()).toBe(200)
      ensure(body.code === 200 && body.data, `${scope} 内部范围请求未成功`)
      expect(body.data.effectiveScope).toBe(scope)
    } finally {
      sql(`DELETE FROM sys_user_role WHERE user_id=${actor.id} AND role_id=${role.id}`)
      await ok(world.request, `/api/auth/role/${role.id}`, world.admin, 'DELETE')
    }
  }
})

test('FB05 OAuth 客户端 CRUD 与 secret 不回显', async ({ world }) => {
  const name = world.name('client')
  const input = { clientName: name, clientId: name, clientSecret: world.employee.password, authenticationMethods: ['client_secret_basic'], grantTypes: ['client_credentials'], scopes: ['read'], requireConsent: false, requireProofKey: false, redirectUris: [] }
  const client = await ok<OAuth2ClientVO>(world.request, '/api/auth/oauth2-client', world.admin, 'POST', input)
  try {
    expect(client.clientSecret).toBe('******')
    const detail = await ok<OAuth2ClientVO>(world.request, `/api/auth/oauth2-client/${client.id}`, world.admin)
    expect(detail.clientSecret).toBe('******')
    const list = await ok<PageResult<OAuth2ClientVO>>(world.request, '/api/auth/oauth2-client/list?size=100', world.admin)
    ensure(!JSON.stringify(list).includes(world.employee.password), '客户端接口泄露秘密')
    await ok(world.request, `/api/auth/oauth2-client/${client.id}`, world.admin, 'PUT', { ...input, clientName: `${name}-edited` })
  } finally { await ok(world.request, `/api/auth/oauth2-client/${client.id}`, world.admin, 'DELETE') }
})

test('FB05 字典类型/项真实增改启停删除', async ({ world }) => {
  const name = world.name('dict')
  const type = await ok<DictType>(world.request, '/api/base/dict/type', world.admin, 'POST', { typeCode: name, typeName: name })
  try {
    const item = await ok<DictData>(world.request, '/api/base/dict/data', world.admin, 'POST', { typeCode: name, dictValue: 'one', dictLabel: name })
    await ok(world.request, `/api/base/dict/data/${item.id}`, world.admin, 'PUT', { dictLabel: `${name}-edited` })
    const data = await ok<PageResult<DictData>>(world.request, `/api/base/dict/data/list?typeCode=${name}&page=1&size=20`, world.admin)
    expect(data.records[0].dictLabel).toBe(`${name}-edited`)
    await ok(world.request, `/api/base/dict/type/${type.id}/status?status=0`, world.admin, 'PUT')
    await ok(world.request, `/api/base/dict/type/${type.id}/status?status=1`, world.admin, 'PUT')
    await ok(world.request, `/api/base/dict/data/${item.id}`, world.admin, 'DELETE')
  } finally { await ok(world.request, `/api/base/dict/type/${type.id}`, world.admin, 'DELETE') }
})

test('FB06 XSS 真实缓存预热、更新失效、规则 CRUD 与恢复', async ({ world }) => {
  const settings = await ok<XssSettings>(world.request, '/api/auth/xss-config/settings', world.admin)
  expect(redis('EXISTS', 'xss:enabled:1')).toBe('1')
  expect(redis('EXISTS', 'xss:rules:1')).toBe('1')
  await ok(world.request, `/api/auth/xss-config/toggle?enabled=${settings.enabled}`, world.admin, 'PUT')
  expect(redis('EXISTS', 'xss:enabled:1')).toBe('0')
  expect(redis('EXISTS', 'xss:rules:1')).toBe('0')
  const name = world.name('xss')
  const rule = await ok<BlacklistRule>(world.request, '/api/auth/xss-config/rules', world.admin, 'POST', { ruleName: name, ruleType: 'TAG', pattern: 'wp009-synthetic-tag' })
  try {
    await ok(world.request, `/api/auth/xss-config/rules/${rule.id}`, world.admin, 'PUT', { ruleName: `${name}-edited` })
    await ok(world.request, `/api/auth/xss-config/rules/${rule.id}/toggle?enabled=false`, world.admin, 'PUT')
  } finally { await ok(world.request, `/api/auth/xss-config/rules/${rule.id}`, world.admin, 'DELETE') }
})
