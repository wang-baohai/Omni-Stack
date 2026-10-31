import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP25S02的34项业务任务：只用专用身份与正式接口，不写业务数据库。
const local = createLocalContext()
const attempt = process.argv[2]
assert.match(attempt ?? '', /^crm-full-r[1-9][0-9]*$/)
assert.match(process.env.E2E_CRM_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const output = `/validation/evidence/codex-2026-10-22/wp25-s02/${attempt}`
assert.ok(!existsSync(output), '旧尝试和原始日志必须保留')
mkdirSync(output, { recursive: true })
const stateFile = `${privateRoot}/crm-full-scenarios.json`
const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8'))
  : { namespace: 'GOAL20261023CRM', objects: {}, commands: {}, completed: {}, runs: [] }
assert.equal(state.namespace, 'GOAL20261023CRM')
const sourceSha = createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex')
const run = { attempt, original_step: 'WP-25.S02', started_at: new Date().toISOString(), identity_source: 'test-fixture',
  source_sha256: sourceSha, crm_image_id: process.env.E2E_CRM_IMAGE_ID, namespace: state.namespace, cases: [], requests: [] }
state.runs.push(run)
const clone = (value) => JSON.parse(JSON.stringify(value))
const persist = () => {
  writeFileSync(stateFile, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(`${output}/result.json`, JSON.stringify({ ...run, objects: state.objects }, null, 2) + '\n')
}
const tokens = {}
for (const alias of ['salesRep', 'salesManager', 'crmViewer', 'employee']) tokens[alias] = await local.login(alias)
assert.deepEqual(local.state.accounts.salesManager.actual_roles, ['SALES_MANAGER'])
assert.equal(Number(local.state.accounts.salesManager.organization_id), 604)
assert.equal(Number(local.state.accounts.salesRep.organization_id), 606)
const repId = Number(local.state.accounts.salesRep.id)
const managerId = Number(local.state.accounts.salesManager.id)
const peerId = Number(local.state.accounts.salesPeer.id)
const uri = (type, id) => `/api/crm/${type}/${id}`
const query = (values) => new URLSearchParams(Object.entries(values).filter(([, value]) => value != null)).toString()
let currentCase
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(tokens[actor] && path.startsWith('/api/crm/'))
  const start = local.state.operations.length
  const request = { case_id: currentCase, actor, user_id: local.state.accounts[actor].id, method, path, body,
    started_at: new Date().toISOString() }
  run.requests.push(request); persist()
  try {
    const response = await local.api(path, { token: tokens[actor], method, body, expected, details: true })
    request.response = response; return response.data
  } finally { request.results = local.state.operations.slice(start); request.finished_at = new Date().toISOString(); persist() }
}
async function command(key, actor, path, method, body, expected = 200) {
  const saved = state.commands[key]
  if (saved?.complete) return clone(saved.result)
  assert.ok(!saved, `命令${key}结果未确定：先核对真实状态，禁止自动重放`)
  state.commands[key] = { attempted_at: new Date().toISOString(), attempt, actor, path, method, body: clone(body ?? null), expected }
  persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, completed_at: new Date().toISOString(), result: clone(result) }); persist()
  return result
}
async function check(id, action) {
  currentCase = id
  const previous = state.completed[id]
  if (previous) {
    assert.equal(previous.crm_image_id, run.crm_image_id, '镜像变化后不能继承旧业务执行结果')
    run.cases.push({ id, result: 'REUSE_PREVIOUS_ACTUAL_PASS', evidence: clone(previous) }); persist(); return
  }
  const start = run.requests.length
  try {
    const evidence = await action()
    const result = { id, result: 'PASS', attempt, source_sha256: sourceSha, crm_image_id: run.crm_image_id,
      at: new Date().toISOString(), request_indexes: [start, run.requests.length - 1], evidence }
    state.completed[id] = clone(result); run.cases.push(result); persist()
    console.log(JSON.stringify({ case: id, result: 'PASS', requests: run.requests.length - start }))
  } catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message }); persist(); throw error }
}
const get = (type, id, actor = 'salesRep') => api(actor, uri(type, id))
const object = (key) => { assert.ok(state.objects[key]?.id, `缺少已登记对象${key}`); return state.objects[key] }
async function create(key, type, body, actor = 'salesRep', customPath) {
  if (state.objects[key]?.id) return clone(state.objects[key])
  const name = `${state.namespace}-${key}`
  const payload = { ...body, [type === 'lead' ? 'fullName' : type === 'activity' ? 'subject' : 'name']: name }
  const row = await command(`create-${key}`, actor, customPath ?? `/api/crm/${type}`, 'POST', payload)
  assert.ok(Number(row?.id) > 0)
  state.objects[key] = { id: row.id, type, name, actor, created_attempt: attempt, created_at: new Date().toISOString() }
  persist(); return clone(state.objects[key])
}
const newLead = (key) => create(key, 'lead', { companyName: `${state.namespace}-${key} Company`, sourceCode: 'WEB',
  mobile: '13900000131', email: `${key.toLowerCase()}@example.invalid` })
const newCustomer = (key) => create(key, 'customer', { phone: '01000000031', email: `${key.toLowerCase()}@example.invalid` })
const newContact = (key, customerId, primary = false) => create(key, 'contact', { primary, mobile: '13900000132' },
  'salesRep', `/api/crm/customer/${customerId}/contact`)
const newActivity = (key, rootType, rootId) => create(key, 'activity', { rootType, rootId, activityType: 'CALL', status: 'PLANNED',
  content: '本Goal专用虚构跟进记录', plannedStartTime: '2026-12-02 10:00:00' })
async function mutate(key, actor, type, id, suffix, fields = {}, expected = 200) {
  const before = await get(type, id, actor)
  return command(key, actor, `${uri(type, id)}${suffix}`, suffix ? 'POST' : 'PUT', { version: before.version, ...fields }, expected)
}
async function remove(key, actor, type, id, expected = 200) {
  const before = await get(type, id, actor)
  return command(key, actor, `${uri(type, id)}?version=${before.version}`, 'DELETE', undefined, expected)
}
const conversionCommand = (lead, extra = {}) => ({ version: lead.version, customerMode: 'CREATE', customerName: `${state.namespace}-CONVERTED-CUSTOMER`,
  contactMode: 'CREATE', contactName: `${state.namespace}-CONVERTED-CONTACT`, createOpportunity: true,
  opportunityName: `${state.namespace}-CONVERTED-OPPORTUNITY`, amount: 1234.56, ...extra })
try {
  await check('OV-01', async () => {
    const summary = await api('salesRep', '/api/crm/overview/summary')
    for (const key of ['newLeadCount', 'qualifiedLeadCount', 'openOpportunityCount']) assert.ok(Number(summary[key]) >= 0)
    assert.ok(Array.isArray(await api('salesRep', '/api/crm/overview/funnel')))
    await api('employee', '/api/crm/overview/summary', 'GET', undefined, 403)
    return { metrics_nonnegative: true, employee_denied: true }
  })
  await check('OV-02', async () => { assert.ok(Array.isArray(await api('salesRep', '/api/crm/overview/follow-ups'))); return { actual_followups_read: true } })
  await check('LEAD-02', async () => {
    await command('lead-required-negative', 'salesRep', '/api/crm/lead', 'POST', { fullName: '' }, 400)
    const made = await newLead('MAIN'); const row = await get('lead', made.id)
    assert.equal(row.status, 'NEW'); assert.equal(Number(row.ownerUserId), repId); assert.equal(Number(row.ownerUnitId), 606)
    const duplicates = await api('salesRep', '/api/crm/lead/duplicate-check', 'POST', { companyName: `${made.name} Company` })
    assert.ok(duplicates.some((item) => Number(item.id) === Number(made.id)))
    await api('crmViewer', '/api/crm/lead', 'POST', { fullName: `${state.namespace}-FORBIDDEN` }, 403)
    return { lead_id: made.id, initial_status: row.status, duplicate_candidate: made.id }
  })
  await check('LEAD-01', async () => {
    const own = object('MAIN')
    const list = await api('salesRep', `/api/crm/lead/list?${query({ page: 1, size: 100, keyword: state.namespace, status: 'NEW', sourceCode: 'WEB' })}`)
    assert.ok(list.records.some((row) => row.id === own.id)); await get('lead', own.id)
    assert.equal(Number((await api('salesRep', `/api/crm/lead/list?keyword=${state.namespace}-ABSENT-EXACT`)).total), 0)
    const foreign = JSON.parse(readFileSync(`${privateRoot}/crm-boundaries.json`, 'utf8')).objects.PEER.id
    await api('salesRep', uri('lead', foreign), 'GET', undefined, 404)
    return { filtered_id: own.id, empty_total: 0, excluded_foreign_id: foreign }
  })
  await check('LEAD-03', async () => {
    const id = object('MAIN').id; const before = await get('lead', id)
    await command('lead-edit', 'salesRep', uri('lead', id), 'PUT', { version: before.version, jobTitle: 'Goal fixture lead' })
    await api('salesRep', uri('lead', id), 'PUT', { version: before.version, jobTitle: 'STALE' }, 409)
    assert.equal((await get('lead', id)).jobTitle, 'Goal fixture lead'); return { id, stale_rejected: true }
  })
  await check('LEAD-04', async () => {
    const a = await newLead('ASSIGN-A'); const b = await newLead('ASSIGN-B')
    let row = await get('lead', a.id)
    await command('lead-assign-foreign', 'salesManager', `${uri('lead', a.id)}/assign`, 'POST', { version: row.version, ownerUserId: peerId }, 403)
    assert.equal((await get('lead', a.id)).version, row.version)
    await command('lead-assign-parent', 'salesManager', `${uri('lead', a.id)}/assign`, 'POST', { version: row.version, ownerUserId: managerId })
    await api('salesRep', uri('lead', a.id), 'GET', undefined, 404)
    await mutate('lead-assign-child', 'salesManager', 'lead', a.id, '/assign', { ownerUserId: repId })
    const items = await Promise.all([a, b].map(async (item) => ({ id: item.id, version: (await get('lead', item.id)).version })))
    const assigned = await command('lead-batch-parent', 'salesManager', '/api/crm/lead/batch-assign', 'POST', { items, ownerUserId: managerId, reason: state.namespace })
    assert.equal(assigned.length, 2)
    assert.ok(assigned.every((item) => Number(item.ownerUserId) === managerId && Number(item.ownerUnitId) === 604))
    const restored = await command('lead-batch-child', 'salesManager', '/api/crm/lead/batch-assign', 'POST', {
      items: assigned.map(({ id, version }) => ({ id, version })), ownerUserId: repId, reason: `${state.namespace} restore` })
    assert.ok(restored.every((item) => Number(item.ownerUserId) === repId && Number(item.ownerUnitId) === 606))
    await api('salesRep', `${uri('lead', a.id)}/assign`, 'POST', { version: restored[0].version, ownerUserId: managerId }, 403)
    return { ids: [a.id, b.id], single_and_batch_roundtrips: true, parent_unit: 604, child_unit: 606 }
  })
  await check('LEAD-05', async () => {
    const id = object('MAIN').id
    await mutate('lead-new-qualify-negative', 'salesRep', 'lead', id, '/qualify', {}, 409)
    const follow = await newActivity('LEAD-FOLLOW', 'LEAD', id)
    await mutate('lead-follow-complete', 'salesRep', 'activity', follow.id, '/complete')
    assert.equal((await get('lead', id)).status, 'FOLLOWING')
    const qualified = await mutate('lead-qualify', 'salesRep', 'lead', id, '/qualify')
    assert.equal(qualified.status, 'QUALIFIED'); return { id, activity_id: follow.id, transitions: ['NEW', 'FOLLOWING', 'QUALIFIED'] }
  })
  await check('LEAD-06', async () => {
    const lead = await newLead('DISQUALIFIED')
    await mutate('lead-disqualify-reason-negative', 'salesRep', 'lead', lead.id, '/disqualify', { reason: '' }, 400)
    const row = await mutate('lead-disqualify', 'salesRep', 'lead', lead.id, '/disqualify', { reason: '本Goal虚构无效线索' })
    assert.equal(row.status, 'DISQUALIFIED'); return { id: lead.id, status: row.status }
  })
  await check('LEAD-07', async () => {
    const lead = object('DISQUALIFIED'); const row = await mutate('lead-reopen', 'salesRep', 'lead', lead.id, '/reopen')
    assert.equal(row.status, 'FOLLOWING'); return { id: lead.id, restored_status: row.status }
  })
  await check('LEAD-08', async () => {
    const lead = await get('lead', object('MAIN').id)
    const conversion = await command('lead-convert-create', 'salesRep', `${uri('lead', lead.id)}/convert`, 'POST', conversionCommand(lead))
    const replay = await api('salesRep', `${uri('lead', lead.id)}/convert`, 'POST', conversionCommand(lead))
    assert.equal(replay.idempotentReplay, true)
    for (const key of ['customerId', 'contactId', 'opportunityId']) assert.equal(replay[key], conversion[key])
    state.conversion = clone(conversion); persist()
    const migrated = await get('activity', object('LEAD-FOLLOW').id)
    assert.equal(migrated.rootType, 'CUSTOMER'); assert.equal(migrated.rootId, conversion.customerId)
    await mutate('converted-reopen-negative', 'salesRep', 'lead', lead.id, '/reopen', {}, 409)
    const linked = await mutate('link-qualify', 'salesRep', 'lead', object('DISQUALIFIED').id, '/qualify')
    const tutorial = JSON.parse(readFileSync(`${privateRoot}/crm-boundaries.json`, 'utf8'))
    const linkPayload = { version: linked.version, customerMode: 'LINK', customerId: conversion.customerId, contactMode: 'LINK',
      contactId: tutorial.conversion.contactId, createOpportunity: false }
    await command('lead-link-contact-mismatch', 'salesRep', `${uri('lead', linked.id)}/convert`, 'POST', linkPayload, 400)
    const linkResult = await command('lead-convert-link', 'salesRep', `${uri('lead', linked.id)}/convert`, 'POST', { ...linkPayload, contactId: conversion.contactId })
    assert.equal(linkResult.customerId, conversion.customerId); assert.equal(linkResult.contactId, conversion.contactId)
    return { lead_id: lead.id, conversion, replay_same_ids: true, migrated_activity_id: migrated.id, linked_lead_id: linked.id }
  })
  await check('LEAD-09', async () => {
    await remove('converted-delete-negative', 'salesManager', 'lead', object('MAIN').id, 409)
    const leaf = await newLead('DELETE'); await remove('lead-delete', 'salesManager', 'lead', leaf.id)
    await api('salesManager', uri('lead', leaf.id), 'GET', undefined, 404); return { deleted_id: leaf.id, converted_preserved: object('MAIN').id }
  })
  await check('CUS-02', async () => {
    await command('customer-required-negative', 'salesRep', '/api/crm/customer', 'POST', { name: '' }, 400)
    const customer = await newCustomer('CUSTOMER'); const row = await get('customer', customer.id)
    assert.equal(row.status, 'POTENTIAL'); return { id: row.id, status: row.status }
  })
  await check('CUS-01', async () => {
    const customer = object('CUSTOMER')
    const list = await api('salesRep', `/api/crm/customer/list?${query({ keyword: customer.name, status: 'POTENTIAL' })}`)
    assert.ok(list.records.some((row) => row.id === customer.id))
    const duplicate = await api('crmViewer', '/api/crm/customer/duplicate-check', 'POST', { name: customer.name })
    assert.ok(duplicate.some((row) => row.id === customer.id)); return { filtered_and_duplicate_id: customer.id }
  })
  await check('CUS-03', async () => {
    const id = object('CUSTOMER').id
    const edited = await mutate('customer-edit', 'salesRep', 'customer', id, '', { region: 'Goal fixture region' })
    assert.equal(edited.region, 'Goal fixture region')
    await mutate('customer-invalid-transition', 'salesManager', 'customer', id, '/status', { status: 'LOST' }, 409)
    const active = await mutate('customer-active', 'salesManager', 'customer', id, '/status', { status: 'ACTIVE' }); assert.equal(active.status, 'ACTIVE')
    await api('salesManager', `${uri('customer', id)}/status`, 'POST', { version: edited.version, status: 'DORMANT' }, 409)
    assert.equal((await get('customer', id)).status, 'ACTIVE'); return { id, status: 'ACTIVE', stale_rejected: true }
  })
  await check('CUS-05', async () => {
    const id = object('CUSTOMER').id
    const contact = await newContact('CONTACT-FIRST', id, true)
    const activity = await newActivity('CUSTOMER-FOLLOW', 'CUSTOMER', id)
    const opportunity = await create('OPPORTUNITY', 'opportunity', { customerId: id, primaryContactId: contact.id, amount: 5678.9 })
    const opportunityActivity = await newActivity('OPPORTUNITY-FOLLOW', 'OPPORTUNITY', opportunity.id)
    let before = await get('customer', id)
    await command('customer-transfer-foreign', 'salesManager', `${uri('customer', id)}/transfer`, 'POST', {
      version: before.version, ownerUserId: peerId, cascadeOpenOpportunities: true }, 403)
    assert.equal((await get('customer', id)).version, before.version)
    await mutate('customer-transfer-parent', 'salesManager', 'customer', id, '/transfer', { ownerUserId: managerId, cascadeOpenOpportunities: true })
    for (const [type, objectId] of [['customer', id], ['contact', contact.id], ['activity', activity.id], ['opportunity', opportunity.id], ['activity', opportunityActivity.id]]) {
      const row = await get(type, objectId, 'salesManager'); assert.equal(Number(row.ownerUserId), managerId); assert.equal(Number(row.ownerUnitId), 604)
      await api('salesRep', uri(type, objectId), 'GET', undefined, 404)
    }
    await mutate('customer-transfer-child', 'salesManager', 'customer', id, '/transfer', { ownerUserId: repId, cascadeOpenOpportunities: true })
    for (const [type, objectId] of [['customer', id], ['contact', contact.id], ['activity', activity.id], ['opportunity', opportunity.id], ['activity', opportunityActivity.id]]) {
      const row = await get(type, objectId); assert.equal(Number(row.ownerUserId), repId); assert.equal(Number(row.ownerUnitId), 606)
    }
    return { id, contact_id: contact.id, activity_ids: [activity.id, opportunityActivity.id], open_opportunity_id: opportunity.id, cascade_roundtrip: true }
  })
  await check('CUS-04', async () => {
    const id = object('CUSTOMER').id; const overview = await api('crmViewer', `${uri('customer', id)}/overview`)
    assert.equal(Number(overview.customer.id), id)
    const contacts = await api('salesRep', `/api/crm/customer/${id}/contact/list`); assert.ok(contacts.records.length > 0)
    assert.ok((await api('salesRep', `/api/crm/opportunity/list?customerId=${id}`)).records.some((row) => row.id === object('OPPORTUNITY').id))
    assert.ok(Array.isArray(await api('salesRep', `/api/crm/activity/timeline?rootType=CUSTOMER&rootId=${id}`)))
    return { customer_id: id, actual_blocks_read: true }
  })
  await check('CUS-06', async () => {
    const id = object('CUSTOMER').id
    await mutate('customer-blacklist-ordinary-denied', 'salesManager', 'customer', id, '/status', { status: 'BLACKLISTED' }, 403)
    await mutate('customer-blacklist-rep-denied', 'salesRep', 'customer', id, '/blacklist', {}, 403)
    const blocked = await mutate('customer-blacklist', 'salesManager', 'customer', id, '/blacklist'); assert.equal(blocked.status, 'BLACKLISTED')
    await mutate('customer-blacklist-ordinary-restore-denied', 'salesManager', 'customer', id, '/status', { status: 'ACTIVE' }, 403)
    const restored = await mutate('customer-restore', 'salesManager', 'customer', id, '/restore-from-blacklist'); assert.equal(restored.status, 'ACTIVE')
    return { id, transitions: ['ACTIVE', 'BLACKLISTED', 'ACTIVE'], ordinary_and_rep_denied: true }
  })
  await check('CON-01', async () => {
    const id = object('CONTACT-FIRST').id
    assert.equal((await get('contact', id)).customerId, object('CUSTOMER').id)
    assert.ok((await api('salesRep', `/api/crm/contact/list?customerId=${object('CUSTOMER').id}`)).records.some((row) => row.id === id))
    return { contact_id: id }
  })
  await check('CON-02', async () => {
    const id = object('CUSTOMER').id; const contact = await newContact('CONTACT-SECOND', id)
    await api('salesRep', `/api/crm/customer/${id}/contact`, 'POST', { name: '' }, 400)
    assert.equal((await get('contact', contact.id)).customerId, id); return { id: contact.id, customer_id: id }
  })
  await check('CON-03', async () => {
    const id = object('CONTACT-SECOND').id
    const edited = await mutate('contact-edit', 'salesRep', 'contact', id, '', { jobTitle: 'Goal fixture decision maker' })
    assert.equal(edited.jobTitle, 'Goal fixture decision maker')
    await mutate('contact-primary', 'salesRep', 'contact', id, '/primary')
    const rows = (await api('salesRep', `/api/crm/customer/${object('CUSTOMER').id}/contact/list`)).records
    const primary = rows.filter((row) => Number(row.primaryFlag) === 1)
    assert.deepEqual(primary.map((row) => row.id), [id]); return { primary_id: id, at_most_one: true }
  })
  await check('CON-04', async () => {
    const id = object('CONTACT-SECOND').id
    await remove('contact-viewer-delete-denied', 'crmViewer', 'contact', id, 403)
    await remove('contact-delete-primary', 'salesManager', 'contact', id)
    await api('salesManager', uri('contact', id), 'GET', undefined, 404)
    const rows = (await api('salesRep', `/api/crm/customer/${object('CUSTOMER').id}/contact/list`)).records
    assert.equal(rows.filter((row) => Number(row.primaryFlag) === 1).length, 0)
    return { deleted_primary_id: id, replacement_not_required: true }
  })
  await check('OPP-01', async () => {
    const id = object('OPPORTUNITY').id
    const list = await api('salesRep', `/api/crm/opportunity/list?customerId=${object('CUSTOMER').id}&status=OPEN`)
    assert.ok(list.records.some((row) => row.id === id))
    const board = await api('salesRep', '/api/crm/opportunity/board'); assert.ok(Array.isArray(board.stages))
    const pipelines = await api('salesRep', '/api/crm/pipeline/list'); assert.ok(pipelines.length > 0)
    const row = await get('opportunity', id); const stages = await api('salesRep', `/api/crm/pipeline/${row.pipelineId}/stages`)
    assert.ok(stages.some((stage) => stage.id === row.stageId)); return { id, pipeline_id: row.pipelineId, stage_count: stages.length }
  })
  await check('OPP-02', async () => {
    await command('opportunity-negative-amount', 'salesRep', '/api/crm/opportunity', 'POST', { name: `${state.namespace}-INVALID`, customerId: object('CUSTOMER').id, amount: -1 }, 400)
    const row = await get('opportunity', object('OPPORTUNITY').id); assert.equal(row.status, 'OPEN'); assert.equal(Number(row.amount), 5678.9)
    return { created_id: row.id, creation_evidence: 'create-OPPORTUNITY', invalid_amount_rejected: true }
  })
  await check('OPP-03', async () => {
    const id = object('OPPORTUNITY').id; const before = await get('opportunity', id)
    await command('opportunity-edit', 'salesRep', uri('opportunity', id), 'PUT', { version: before.version, amount: 6789.01, expectedCloseDate: '2026-12-31' })
    await api('salesRep', uri('opportunity', id), 'PUT', { version: before.version, amount: 1 }, 409)
    assert.equal(Number((await get('opportunity', id)).amount), 6789.01); return { id, stale_rejected: true }
  })
  await check('OPP-04', async () => {
    const id = object('OPPORTUNITY').id; const original = await get('opportunity', id)
    const stages = await api('salesRep', `/api/crm/pipeline/${original.pipelineId}/stages`)
    const opens = stages.filter((row) => row.stageType === 'OPEN').sort((a, b) => a.sort - b.sort)
    const won = stages.find((row) => row.stageType === 'WON'); const lost = stages.find((row) => row.stageType === 'LOST')
    assert.ok(opens.length >= 2 && won && lost)
    await mutate('opportunity-same-stage-negative', 'salesRep', 'opportunity', id, '/stage', { stageId: original.stageId }, 409)
    await mutate('opportunity-forward', 'salesRep', 'opportunity', id, '/stage', { stageId: opens.at(-1).id, reason: state.namespace })
    await mutate('opportunity-backward-reason-negative', 'salesRep', 'opportunity', id, '/stage', { stageId: opens[0].id }, 400)
    await mutate('opportunity-backward', 'salesRep', 'opportunity', id, '/stage', { stageId: opens[0].id, reason: '本Goal真实回退验证' })
    await mutate('opportunity-lost-reason-negative', 'salesRep', 'opportunity', id, '/stage', { stageId: lost.id }, 400)
    const closed = await mutate('opportunity-won', 'salesRep', 'opportunity', id, '/stage', { stageId: won.id, reason: state.namespace })
    assert.equal(closed.status, 'WON')
    await mutate('opportunity-closed-edit-negative', 'salesRep', 'opportunity', id, '', { amount: 1 }, 409)
    await mutate('opportunity-closed-stage-negative', 'salesRep', 'opportunity', id, '/stage', { stageId: lost.id, lossReason: 'closed negative' }, 409)
    return { id, actual_transitions: ['OPEN', 'OPEN_FORWARD', 'OPEN_BACKWARD', 'WON'], invalid_commands_rejected: true }
  })
  await check('OPP-05', async () => {
    const id = object('OPPORTUNITY').id
    await mutate('opportunity-viewer-reopen-negative', 'crmViewer', 'opportunity', id, '/reopen', {}, 403)
    const opened = await mutate('opportunity-reopen-won', 'salesRep', 'opportunity', id, '/reopen'); assert.equal(opened.status, 'OPEN')
    await mutate('opportunity-open-reopen-negative', 'salesRep', 'opportunity', id, '/reopen', {}, 409)
    const stages = await api('salesRep', `/api/crm/pipeline/${opened.pipelineId}/stages`); const lost = stages.find((row) => row.stageType === 'LOST')
    const closed = await mutate('opportunity-lost', 'salesRep', 'opportunity', id, '/stage', { stageId: lost.id, lossReason: '本Goal虚构输单原因' }); assert.equal(closed.status, 'LOST')
    await mutate('opportunity-reopen-lost', 'salesRep', 'opportunity', id, '/reopen')
    const history = await api('salesRep', `${uri('opportunity', id)}/stage-history`)
    assert.ok(history.filter((row) => row.changeReason === 'REOPEN').length >= 2)
    return { id, history_count: history.length, won_and_lost_reopen: true }
  })
  await check('OPP-06', async () => {
    const id = object('OPPORTUNITY').id
    await mutate('opportunity-assign-foreign', 'salesManager', 'opportunity', id, '/assign', { ownerUserId: peerId }, 403)
    await mutate('opportunity-assign-parent', 'salesManager', 'opportunity', id, '/assign', { ownerUserId: managerId })
    const activity = await get('activity', object('OPPORTUNITY-FOLLOW').id, 'salesManager'); assert.equal(Number(activity.ownerUserId), managerId)
    await api('salesRep', uri('opportunity', id), 'GET', undefined, 404)
    const restored = await mutate('opportunity-assign-child', 'salesManager', 'opportunity', id, '/assign', { ownerUserId: repId })
    assert.equal(Number(restored.ownerUnitId), 606); return { id, activity_id: activity.id, assignment_roundtrip: true }
  })
  await check('ACT-02', async () => {
    const id = object('CUSTOMER').id; const activity = await newActivity('ACTIVITY', 'CUSTOMER', id)
    const base = { rootType: 'CUSTOMER', rootId: id, activityType: 'CALL', subject: `${state.namespace}-INVALID-ACTIVITY` }
    await command('activity-cancelled-create-negative', 'salesRep', '/api/crm/activity', 'POST', { ...base, status: 'CANCELLED' }, 400)
    await command('activity-completed-time-negative', 'salesRep', '/api/crm/activity', 'POST', { ...base, status: 'COMPLETED' }, 400)
    assert.equal((await get('activity', activity.id)).status, 'PLANNED'); return { activity_id: activity.id, root_id: id }
  })
  await check('ACT-01', async () => {
    const id = object('ACTIVITY').id; const rootId = object('CUSTOMER').id
    const list = await api('salesRep', `/api/crm/activity/list?rootType=CUSTOMER&rootId=${rootId}&status=PLANNED`)
    assert.ok(list.records.some((row) => row.id === id))
    const timeline = await api('crmViewer', `/api/crm/activity/timeline?rootType=CUSTOMER&rootId=${rootId}`)
    assert.ok(timeline.some((row) => row.id === id && row.content === '[REDACTED]'))
    await get('activity', id); return { id, real_root_filter: rootId, viewer_content_redacted: true }
  })
  await check('ACT-03', async () => {
    const id = object('ACTIVITY').id
    const cancelled = await mutate('activity-cancel', 'salesRep', 'activity', id, '/cancel', { reason: '本Goal计划调整' }); assert.equal(cancelled.status, 'CANCELLED')
    await mutate('activity-cancelled-complete-negative', 'salesRep', 'activity', id, '/complete', {}, 409)
    const rescheduled = await mutate('activity-reschedule', 'salesRep', 'activity', id, '/reschedule', { plannedStartTime: '2026-12-03 10:00:00' }); assert.equal(rescheduled.status, 'PLANNED')
    const completed = await mutate('activity-complete', 'salesRep', 'activity', id, '/complete'); assert.equal(completed.status, 'COMPLETED'); assert.ok(completed.completedTime)
    await mutate('activity-completed-cancel-negative', 'salesRep', 'activity', id, '/cancel', { reason: 'invalid' }, 409)
    return { id, transitions: ['PLANNED', 'CANCELLED', 'PLANNED', 'COMPLETED'], actual_completed_time: completed.completedTime }
  })
  await check('ACT-04', async () => {
    const completedId = object('ACTIVITY').id
    await mutate('activity-completed-edit-negative', 'salesRep', 'activity', completedId, '', { content: 'invalid' }, 409)
    await remove('activity-completed-delete-negative', 'salesManager', 'activity', completedId, 409)
    const editable = await newActivity('ACTIVITY-EDIT', 'CUSTOMER', object('CUSTOMER').id)
    const edited = await mutate('activity-edit', 'salesRep', 'activity', editable.id, '', { content: '本Goal修改后的虚构记录' }); assert.equal(edited.content, '本Goal修改后的虚构记录')
    await remove('activity-rep-delete-negative', 'salesRep', 'activity', editable.id, 403)
    await remove('activity-delete', 'salesManager', 'activity', editable.id)
    await api('salesManager', uri('activity', editable.id), 'GET', undefined, 404); return { edited_and_deleted_id: editable.id, completed_preserved_id: completedId }
  })
  await check('OPP-07', async () => {
    const closedId = state.conversion.opportunityId
    let closed = await get('opportunity', closedId)
    const stages = await api('salesRep', `/api/crm/pipeline/${closed.pipelineId}/stages`)
    const won = stages.find((row) => row.stageType === 'WON')
    await mutate('converted-opportunity-won', 'salesRep', 'opportunity', closedId, '/stage', { stageId: won.id, reason: state.namespace })
    await remove('opportunity-closed-delete-negative', 'salesManager', 'opportunity', closedId, 409)
    const id = object('OPPORTUNITY').id; const activityId = object('OPPORTUNITY-FOLLOW').id
    await remove('opportunity-delete', 'salesManager', 'opportunity', id)
    await api('salesManager', uri('opportunity', id), 'GET', undefined, 404)
    await api('salesManager', uri('activity', activityId), 'GET', undefined, 404)
    return { deleted_open_id: id, cascaded_activity_id: activityId, closed_preserved_id: closedId }
  })
  await check('CUS-07', async () => {
    const protectedId = object('CUSTOMER').id
    const open = await create('DELETE-GUARD-OPPORTUNITY', 'opportunity', { customerId: protectedId, amount: 1 })
    await remove('customer-open-delete-negative', 'salesManager', 'customer', protectedId, 409)
    assert.equal((await get('customer', protectedId)).status, 'ACTIVE')
    await remove('delete-guard-opportunity', 'salesManager', 'opportunity', open.id)
    const temporary = await newCustomer('CUSTOMER-DELETE')
    const contact = await newContact('DELETE-CUSTOMER-CONTACT', temporary.id)
    const activity = await newActivity('DELETE-CUSTOMER-ACTIVITY', 'CUSTOMER', temporary.id)
    await remove('customer-delete', 'salesManager', 'customer', temporary.id)
    for (const [type, id] of [['customer', temporary.id], ['contact', contact.id], ['activity', activity.id]]) await api('salesManager', uri(type, id), 'GET', undefined, 404)
    return { preserved_customer_id: protectedId, deleted_customer_id: temporary.id, cascaded_contact_id: contact.id, cascaded_activity_id: activity.id }
  })
  await check('OPT-01', async () => {
    const manager = await api('salesManager', '/api/crm/options/owners')
    const ids = manager.map((row) => Number(row.id)); assert.ok(ids.includes(repId) && ids.includes(managerId)); assert.ok(!ids.includes(peerId))
    const representative = await api('salesRep', '/api/crm/options/owners'); assert.deepEqual(representative.map((row) => Number(row.id)), [repId])
    await api('employee', '/api/crm/options/owners', 'GET', undefined, 403)
    return { parent_and_child_included: [managerId, repId], foreign_excluded: peerId, rep_self_only: repId }
  })
  assert.equal(run.cases.length, 34)
  assert.equal(new Set(run.cases.map((row) => row.id)).size, 34)
  run.result = 'PASS_34_ACTUAL_CRM_TASKS'; run.finished_at = new Date().toISOString()
  run.rollback = '仅本Goal登记对象经正式状态/删除命令处置；已转换、赢单及完成历史保留，不直接修改业务状态或清库。'
  persist(); local.publish()
  console.log(JSON.stringify({ result: run.result, cases: run.cases.length, requests: run.requests.length,
    objects: Object.keys(state.objects).length, reused_cases: run.cases.filter((row) => row.result.startsWith('REUSE')).length }))
} catch (error) {
  run.result = 'FAIL'; run.error = error.message; run.finished_at = new Date().toISOString(); persist(); throw error
}
