import assert from 'node:assert/strict'
import { createHash, randomBytes } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext } from './goal-local-identities.mjs'

// 原WP26S02：保留227的默认角色注册回归，另用固定门户账号走连贯Saga；不改共享角色。
const local = createLocalContext()
assert.equal(process.cwd(), '/validation/source')
const attempt = process.argv[2]
assert.match(attempt ?? '', /^identities-r[1-9][0-9]*$/)
const output = `/validation/evidence/codex-2026-10-22/wp26-s02/${attempt}`
assert.ok(!existsSync(output)); mkdirSync(output, { recursive: true })
const report = { original_step: 'WP-26.S02', at: new Date().toISOString(), goal_namespace: local.state.namespace,
  identity_source: 'test-fixture', source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'),
  requests: [], accounts: {}, configuration_writes: [] }
const persist = () => writeFileSync(`${output}/result.json`, JSON.stringify(report, null, 2) + '\n')
const admin = local.bootstrap()
async function api(path, options = {}, context = {}) {
  const start = local.state.operations.length
  try { return await local.api(path, options) }
  finally { report.requests.push({ ...context, method: options.method ?? 'GET', path,
    // 注册口令与challenge原文不进入普通证据；只记录本次实际请求摘要。
    body_sha256: options.body ? createHash('sha256').update(JSON.stringify(options.body)).digest('hex') : undefined,
    results: local.state.operations.slice(start) }); persist() }
}
const flatten = (rows) => rows.flatMap((row) => [row, ...flatten(row.children ?? [])])
const organizations = flatten(await api('/api/auth/org/tree', { token: admin }))
for (const [id, name, parent] of [[604, 'goal_20261022_a', 1], [606, 'goal_20261022_work', 604]]) {
  const unit = organizations.find((row) => Number(row.id) === id)
  assert.equal(unit?.name, name); assert.equal(Number(unit.parentId), parent); assert.equal(unit.status, 1)
}
const roles = await api('/api/auth/role/all', { token: admin })
const role = (code) => { const result = roles.find((row) => row.roleCode === code && Number(row.tenantId) === 1 && row.status === 1); assert.ok(result); return Number(result.id) }
async function find(username) {
  for (let page = 1; page < 100; page++) {
    const result = await api(`/api/auth/user/list?page=${page}&size=100`, { token: admin })
    const user = result.records.find((row) => row.username === username)
    if (user) return user
    if (page >= Number(result.pages)) return undefined
  }
  throw new Error('用户分页异常')
}
async function prepare(alias, username, primaryUnitId, roleCode, publicRegistration) {
  let account = local.state.accounts[alias]
  let user = await find(username)
  if (user) assert.ok(account?.create_attempted, '不接管无本Goal创建记录的同名账号')
  if (!account) {
    account = local.state.accounts[alias] = { username, password: `G!${randomBytes(24).toString('base64url')}`,
      create_attempted: false, creation_path: publicRegistration ? '/api/auth/register' : '/api/auth/user' }
    local.save()
  }
  assert.equal(account.username, username)
  if (!user) {
    assert.equal(account.create_attempted, false, '此前创建结果待核对，不重复发送未知写入')
    account.create_attempted = true; local.save()
    if (publicRegistration) {
      const challenge = local.challenge('srmportal-register')
      await api('/api/auth/register', { method: 'POST', body: { tenantId: 1, username, password: account.password,
        nickname: username, captchaKey: challenge.captchaKey, captchaCode: challenge.captchaCode } }, { actor: alias, purpose: '真实公开注册及原验证码校验器' })
    } else await api('/api/auth/user', { token: admin, method: 'POST', body: { tenantId: 1, username, password: account.password, nickname: username } }, { actor: alias })
    report.configuration_writes.push({ alias, operation: 'create-owned-account', path: account.creation_path })
    user = await find(username); assert.ok(user?.id)
    account.id = user.id; local.save()
    const initialRoles = await api(`/api/auth/user/${user.id}/roles`, { token: admin })
    assert.deepEqual(initialRoles.map(Number), [role('USER')], '创建路径必须真实分配默认USER角色')
    account.initial_default_role_ids = initialRoles; local.save()
  }
  assert.equal(Number(user.tenantId), 1); assert.equal(user.username, username)
  if (account.id) assert.equal(Number(account.id), Number(user.id))
  account.id = user.id
  assert.ok(user.primaryUnitId == null || Number(user.primaryUnitId) === primaryUnitId, '拒绝接管未知主组织变化')
  const beforeRoles = await api(`/api/auth/user/${user.id}/roles`, { token: admin })
  const before = { primaryUnitId: user.primaryUnitId, role_ids: beforeRoles }
  if (Number(user.primaryUnitId) !== primaryUnitId) {
    await api(`/api/auth/user/${user.id}`, { token: admin, method: 'PUT', body: { primaryUnitId } }, { actor: alias, purpose: '本Goal专用组织前置' })
    report.configuration_writes.push({ alias, operation: 'set-owned-primary-unit', before: user.primaryUnitId, after: primaryUnitId })
  }
  if (!publicRegistration) {
    if (JSON.stringify(beforeRoles.map(Number)) !== JSON.stringify([role(roleCode)])) {
      await api(`/api/auth/user/${user.id}/roles`, { token: admin, method: 'POST', body: [role(roleCode)] }, { actor: alias })
      report.configuration_writes.push({ alias, operation: 'assign-existing-single-role', role_code: roleCode, role_id: role(roleCode) })
    }
  } else {
    const allowed = account.portal_enrolled ? [role('USER'), role('SUPPLIER')].sort() : [role('USER')]
    assert.deepEqual(beforeRoles.map(Number).sort(), allowed, 'SUPPLIER只能由正式Saga授予，不由身份准备脚本补写')
  }
  const afterUser = await api(`/api/auth/user/${user.id}`, { token: admin })
  assert.equal(Number(afterUser.primaryUnitId), primaryUnitId)
  const afterRoles = await api(`/api/auth/user/${user.id}/roles`, { token: admin })
  account.organization_id = primaryUnitId; account.role_ids = afterRoles
  if (Number(before.primaryUnitId) !== primaryUnitId || JSON.stringify(beforeRoles) !== JSON.stringify(afterRoles)) {
    account.configuration_changes ??= []
    account.configuration_changes.push({ at: new Date().toISOString(), method: 'formal-api', before,
      after: { primaryUnitId, role_ids: afterRoles }, reason: 'WP-26.S02 dedicated scenario identity' })
  }
  local.save(); await local.login(alias)
  report.accounts[alias] = { id: user.id, username, primary_unit_id: primaryUnitId, roles: account.actual_roles, role_ids: afterRoles,
    initial_default_role_ids: account.initial_default_role_ids, creation_path: account.creation_path,
    rollback: { method: 'PUT', path: `/api/auth/user/${user.id}/status?status=0`,
      note: '仅禁用本Goal专用账号，保留门户、审批和业务历史；不声称删除历史或把null组织值伪装为已恢复。' } }
  persist()
}
try {
  await prepare('procScopeManager', 'goal_20261022_procscopemanager', 604, 'PROCUREMENT_MANAGER', false)
  await prepare('srmPortal', 'goal_20261022_srmportal', 606, 'USER', true)
  report.preserved_registration_fixture = { id: 227, username: 'goal_20261022_selfreg', role_ids_unchanged: [role('USER')],
    reason: '该专用身份承担现有默认角色回归，不挪作Saga账号以免继承失效断言。' }
  report.result = 'SCENARIO_IDENTITIES_PREPARED_NOT_BUSINESS_ACCEPTANCE'; report.finished_at = new Date().toISOString(); persist(); local.publish()
  console.log(JSON.stringify({ result: report.result, accounts: report.accounts, configuration_writes: report.configuration_writes.length }))
} catch (error) { report.result = 'FAIL'; report.error = error.message; persist(); throw error }
