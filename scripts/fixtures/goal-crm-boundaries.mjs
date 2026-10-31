import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP25S02：专用角色、真实业务命令与范围拒绝；只使用已登记的本次测试数据。
const local = createLocalContext()
const file = `${privateRoot}/crm-boundaries.json`
const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : { namespace: 'GOAL20261022CRM', objects: {}, completed: {}, runs: [] }
const out = '/validation/evidence/codex-2026-10-22/wp25-s02'
mkdirSync(out, { recursive: true })
const run = { at: new Date().toISOString(), original_step: 'WP-25.S02', identity_source: 'test-fixture', cases: [], requests: [] }
state.runs.push(run)
const save = () => writeFileSync(file, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
const tokens = {}
for (const alias of ['salesRep', 'salesPeer', 'salesManager', 'crmViewer', 'employee']) tokens[alias] = await local.login(alias)
const api = async (alias, path, method = 'GET', body, expected = 200) => {
  const start = local.state.operations.length
  try { return await local.api(path, { token: tokens[alias], method, body, expected }) }
  finally {
    run.requests.push({ actor: alias, method, path, body_sha256: body ? createHash('sha256').update(JSON.stringify(body)).digest('hex') : undefined,
      results: local.state.operations.slice(start) })
    save()
  }
}
const check = async (id, action) => {
  try { await action(); run.cases.push({ id, result: 'PASS', at: new Date().toISOString() }); save() }
  catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message, at: new Date().toISOString() }); save(); throw error }
}
const once = async (id, action) => {
  if (state.completed[id]) return state.completed[id].value
  const value = await action()
  state.completed[id] = { at: new Date().toISOString(), value }
  save()
  return value
}
const path = (type, id) => `/api/crm/${type}/${id}`
async function lead(alias, label) {
  const name = `${state.namespace}-${label}`
  const record = state.objects[label] ??= { name, alias, create_attempted: false }
  if (record.id) return api(alias, path('lead', record.id))
  const list = await api(alias, `/api/crm/lead/list?page=1&size=100&keyword=${encodeURIComponent(name)}`)
  const found = list.records.find((item) => item.fullName === name)
  if (found) {
    assert.ok(record.create_attempted, '同名对象没有本次创建归属，不接管')
    record.id = found.id; save(); return api(alias, path('lead', found.id))
  }
  record.create_attempted = true; save()
  const made = await api(alias, '/api/crm/lead', 'POST', { fullName: name, companyName: `${name} Company`, mobile: '13900000111',
    email: `${label.toLowerCase()}@example.invalid`, sourceCode: 'WEB' })
  record.id = made.id; save()
  assert.equal(Number(made.ownerUserId), Number(local.state.accounts[alias].id))
  return made
}
const version = async (alias, type, id) => (await api(alias, path(type, id))).version
try {
  const own = await lead('salesRep', 'MAIN')
  const peer = await lead('salesPeer', 'PEER')
  const managed = await lead('salesRep', 'ASSIGN')
  await check('self-list-isolation', async () => {
    for (const [actor, ownId, forbiddenId] of [['salesRep', own.id, peer.id], ['salesPeer', peer.id, own.id]]) {
      const rows = (await api(actor, `/api/crm/lead/list?page=1&size=100&keyword=${state.namespace}`)).records
      assert.ok(rows.some((r) => r.id === ownId)); assert.ok(rows.every((r) => r.id !== forbiddenId))
      await api(actor, path('lead', forbiddenId), 'GET', undefined, 404)
      await api(actor, path('lead', forbiddenId), 'PUT', { version: 0, fullName: `${state.namespace}-forbidden` }, 404)
    }
  })
  await check('manager-department-list-and-detail', async () => {
    const rows = (await api('salesManager', `/api/crm/lead/list?page=1&size=100&keyword=${state.namespace}`)).records
    assert.ok(rows.some((r) => r.id === own.id)); assert.ok(rows.every((r) => r.id !== peer.id))
    await api('salesManager', path('lead', own.id))
    await api('salesManager', path('lead', peer.id), 'GET', undefined, 404)
  })
  await check('viewer-tenant-read-pii-masked-write-denied', async () => {
    const rows = (await api('crmViewer', `/api/crm/lead/list?page=1&size=100&keyword=${state.namespace}`)).records
    assert.ok(rows.some((r) => r.id === own.id) && rows.some((r) => r.id === peer.id))
    const detail = await api('crmViewer', path('lead', own.id))
    assert.notEqual(detail.mobile, '13900000111'); assert.ok(detail.mobile?.includes('*'), '只读无PII权限详情必须脱敏')
    await api('crmViewer', path('lead', own.id), 'PUT', { version: own.version, fullName: 'forbidden' }, 403)
    await api('crmViewer', '/api/crm/lead', 'POST', { fullName: `${state.namespace}-viewer-forbidden` }, 403)
    await api('employee', '/api/crm/lead/list?page=1&size=10', 'GET', undefined, 403)
  })
  await check('manager-cross-department-assignment-rejected', async () => {
    const before = await api('salesRep', path('lead', managed.id))
    await api('salesManager', `${path('lead', managed.id)}/assign`, 'POST', { version: before.version, ownerUserId: local.state.accounts.salesPeer.id, reason: 'boundary probe' }, 403)
    const after = await api('salesRep', path('lead', managed.id))
    assert.equal(after.ownerUserId, before.ownerUserId); assert.equal(after.version, before.version)
    await api('salesRep', `${path('lead', managed.id)}/assign`, 'POST', { version: before.version, ownerUserId: local.state.accounts.salesManager.id, reason: 'no assign permission' }, 403)
  })
  await check('manager-in-scope-assign-and-restore-through-api', async () => once('assign-roundtrip', async () => {
    let row = await api('salesManager', path('lead', managed.id))
    row = await api('salesManager', `${path('lead', managed.id)}/assign`, 'POST', { version: row.version, ownerUserId: local.state.accounts.salesManager.id, reason: state.namespace })
    assert.equal(Number(row.ownerUserId), Number(local.state.accounts.salesManager.id))
    await api('salesRep', path('lead', managed.id), 'GET', undefined, 404)
    row = await api('salesManager', `${path('lead', managed.id)}/assign`, 'POST', { version: row.version, ownerUserId: local.state.accounts.salesRep.id, reason: `${state.namespace} restore` })
    assert.equal(Number(row.ownerUserId), Number(local.state.accounts.salesRep.id)); return { leadId: row.id }
  }))
  await check('representative-edit-and-concurrency-reject', async () => once('edit-concurrency', async () => {
    const row = await api('salesRep', path('lead', own.id))
    const updated = await api('salesRep', path('lead', own.id), 'PUT', { version: row.version, jobTitle: 'Goal test contact' })
    await api('salesRep', path('lead', own.id), 'PUT', { version: row.version, jobTitle: 'stale write' }, 409)
    const actual = await api('salesRep', path('lead', own.id)); assert.equal(actual.jobTitle, 'Goal test contact')
    return { id: updated.id, version: actual.version }
  }))
  const follow = await once('lead-follow-create', () => api('salesRep', '/api/crm/activity', 'POST', { rootType: 'LEAD', rootId: own.id,
    activityType: 'CALL', subject: `${state.namespace} first contact`, content: 'Fictional test discussion', status: 'PLANNED' }))
  await check('representative-follow-qualify-convert-and-idempotent-replay', async () => once('convert', async () => {
    const activity = await api('salesRep', path('activity', follow.id))
    if (activity.status !== 'COMPLETED') await api('salesRep', `${path('activity', follow.id)}/complete`, 'POST', { version: activity.version })
    let row = await api('salesRep', path('lead', own.id))
    if (row.status === 'FOLLOWING') row = await api('salesRep', `${path('lead', own.id)}/qualify`, 'POST', { version: row.version })
    assert.ok(['QUALIFIED', 'CONVERTED'].includes(row.status))
    const command = { version: row.version, customerMode: 'CREATE', customerName: `${state.namespace} Customer`, contactMode: 'CREATE',
      contactName: `${state.namespace} Contact`, contactMobile: '13900000112', createOpportunity: true,
      opportunityName: `${state.namespace} Opportunity`, amount: 1000, expectedCloseDate: '2026-12-31' }
    const conversion = await api('salesRep', `${path('lead', own.id)}/convert`, 'POST', command)
    state.conversion = conversion; save()
    const replay = await api('salesRep', `${path('lead', own.id)}/convert`, 'POST', command)
    for (const key of ['customerId', 'contactId', 'opportunityId']) assert.equal(replay[key], conversion[key])
    assert.equal(replay.idempotentReplay, true)
    assert.equal((await api('salesRep', path('lead', own.id))).status, 'CONVERTED')
    return conversion
  }))
  const ids = state.conversion
  assert.ok(ids?.customerId && ids.contactId && ids.opportunityId)
  await check('nested-aggregate-ownership-and-viewer-360', async () => {
    for (const [type, id] of [['customer', ids.customerId], ['contact', ids.contactId], ['opportunity', ids.opportunityId], ['activity', follow.id]]) {
      await api('salesRep', path(type, id)); await api('salesPeer', path(type, id), 'GET', undefined, 404)
      await api('crmViewer', path(type, id)); await api('crmViewer', path(type, id), 'PUT', { version: 0 }, 403)
    }
    await api('salesRep', `${path('customer', ids.customerId)}/overview`)
    await api('crmViewer', `${path('customer', ids.customerId)}/overview`)
    await api('salesPeer', `${path('customer', ids.customerId)}/overview`, 'GET', undefined, 404)
    const migrated = await api('salesRep', path('activity', follow.id))
    assert.equal(migrated.rootType, 'CUSTOMER'); assert.equal(migrated.rootId, ids.customerId)
    const viewer = await api('crmViewer', path('activity', follow.id)); assert.equal(viewer.content, '[REDACTED]')
  })
  await check('manager-cross-scope-transfer-no-side-effects', async () => {
    const before = await api('salesRep', path('customer', ids.customerId))
    await api('salesManager', `${path('customer', ids.customerId)}/transfer`, 'POST', { version: before.version, ownerUserId: local.state.accounts.salesPeer.id, reason: state.namespace, cascadeOpenOpportunities: true }, 403)
    const after = await api('salesRep', path('customer', ids.customerId)); assert.equal(after.version, before.version); assert.equal(after.ownerUserId, before.ownerUserId)
  })
  await check('representative-stage-win-and-customer-result', async () => once('win', async () => {
    const row = await api('salesRep', path('opportunity', ids.opportunityId))
    const stages = await api('salesRep', `/api/crm/pipeline/${row.pipelineId}/stages`)
    const won = stages.find((s) => s.stageCode === 'WON'); assert.ok(won, '从真实管道获取赢单阶段，不硬编码阶段ID')
    if (row.status !== 'WON') await api('salesRep', `${path('opportunity', row.id)}/stage`, 'POST', { version: row.version, stageId: won.id, reason: state.namespace })
    assert.equal((await api('salesRep', path('opportunity', row.id))).status, 'WON')
    assert.equal((await api('salesRep', path('customer', ids.customerId))).status, 'ACTIVE')
    const history = await api('salesRep', `${path('opportunity', row.id)}/stage-history`); assert.ok(history.length >= 2)
    return { opportunityId: row.id, customerId: ids.customerId }
  }))
  await check('representative-cancel-reschedule-complete', async () => once('activity-cycle', async () => {
    const made = await once('cycle-create', () => api('salesRep', '/api/crm/activity', 'POST', { rootType: 'CUSTOMER', rootId: ids.customerId,
      activityType: 'MEETING', subject: `${state.namespace} reschedule`, status: 'PLANNED', plannedStartTime: '2026-12-01 10:00:00' }))
    let row = await api('salesRep', path('activity', made.id))
    // 中断后先读取真实状态；已完成对象只复验拒绝，不重复制造取消/完成写入。
    if (row.status !== 'COMPLETED') {
      if (row.status === 'PLANNED') row = await api('salesRep', `${path('activity', made.id)}/cancel`, 'POST', { version: row.version, reason: 'Goal schedule adjustment' })
      assert.equal(row.status, 'CANCELLED')
      row = await api('salesRep', `${path('activity', made.id)}/reschedule`, 'POST', { version: row.version, plannedStartTime: '2026-12-02 10:00:00' })
      assert.equal(row.status, 'PLANNED')
      row = await api('salesRep', `${path('activity', made.id)}/complete`, 'POST', { version: row.version })
    }
    assert.equal(row.status, 'COMPLETED')
    await api('salesRep', `${path('activity', made.id)}/cancel`, 'POST', { version: row.version, reason: 'invalid transition' }, 409)
    return { activityId: made.id, status: row.status }
  }))
  for (const endpoint of ['/overview/summary', '/overview/funnel', '/overview/follow-ups', '/opportunity/board', `/activity/timeline?rootType=CUSTOMER&rootId=${ids.customerId}`]) {
    await check(`ordinary-role-read:${endpoint}`, () => api('salesRep', `/api/crm${endpoint}`))
  }
  run.finished_at = new Date().toISOString(); run.result = 'PASS'; save()
  const report = { ...run, source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'),
    objects: state.objects, conversion: state.conversion, completed: state.completed,
    rollback: '仅本Goal专用对象；保留已转换/赢单/审批历史，按正式状态命令停用或归档；禁止直接改表状态。' }
  writeFileSync(`${out}/boundaries.json`, JSON.stringify(report, null, 2) + '\n')
  local.publish()
  console.log(JSON.stringify({ result: run.result, original_step: run.original_step, cases: run.cases.length, requests: run.requests.length }))
} catch (error) {
  run.result = 'FAIL'; run.error = error.message; save()
  writeFileSync(`${out}/boundaries-failed-${state.runs.length}.json`, JSON.stringify(run, null, 2) + '\n')
  throw error
}
