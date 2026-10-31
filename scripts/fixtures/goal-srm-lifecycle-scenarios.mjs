import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP26S02：内部准入及撤回/取消的真实流程；仅使用固定本Goal对象和正式接口。
const local = createLocalContext()
assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2)
assert.ok(['admission', 'withdrawal'].includes(mode))
assert.match(attempt ?? '', new RegExp(`^lifecycle-${mode}-r[1-9][0-9]*$`))
assert.match(process.env.E2E_SRM_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const file = `${privateRoot}/srm-lifecycle-scenarios.json`
const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8'))
  : { namespace: 'GOAL20261022SRM', suppliers: {}, commands: {}, completed: {}, runs: [], approvals: [] }
assert.equal(state.namespace, 'GOAL20261022SRM')
const output = `/validation/evidence/codex-2026-10-22/wp26-s02/${attempt}`
assert.ok(!existsSync(output)); mkdirSync(output, { recursive: true })
const raw = `${privateRoot}/${attempt}-wire.ndjson`
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const run = { original_step: 'WP-26.S02', mode, attempt, started_at: new Date().toISOString(), identity_source: 'test-fixture',
  srm_image_id: process.env.E2E_SRM_IMAGE_ID, source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'),
  cases: [], requests: [] }
state.runs.push(run)
const clone = (value) => JSON.parse(JSON.stringify(value))
let currentCase
const persist = () => {
  writeFileSync(file, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(`${output}/result.json`, JSON.stringify({ ...run, suppliers: state.suppliers, approvals: state.approvals, original_raw_log: raw }, null, 2) + '\n')
}
const tokens = {}
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(path.startsWith('/api/srm/') || path.startsWith('/api/workflow/'))
  tokens[actor] ??= await local.login(actor)
  const entry = { case_id: currentCase, actor, user_id: local.state.accounts[actor].id, method, path, body, at: new Date().toISOString() }
  run.requests.push(entry); persist()
  let response
  try {
    response = await fetch(`http://omni-gateway:8080${path}`, { method, headers: { Authorization: `Bearer ${tokens[actor]}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
  } catch {
    entry.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist()
    throw new Error(`${method} ${path}结果未知，先核对实际对象/流程，不重发写命令`)
  }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ ...entry, http_status: response.status, response: json }) + '\n')
  entry.http_status = response.status; entry.response = json; persist()
  assert.equal(json.code, expected, `${method} ${path}实际业务码${json.code}不符`)
  return json.data
}
async function command(key, actor, path, method, body) {
  const before = state.commands[key]
  if (before?.complete) return clone(before.result)
  assert.ok(!before, `${key}结果未确定，禁止自动重复写入`)
  state.commands[key] = { actor, method, path, body: clone(body ?? null), attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist()
  return result
}
async function check(id, action) {
  currentCase = id
  if (state.completed[id]) {
    assert.equal(state.completed[id].srm_image_id, run.srm_image_id, '镜像变化后须重新审定证据适用范围，不自动继承')
    run.cases.push({ id, result: 'REUSE_PREVIOUS_ACTUAL_PASS', evidence: clone(state.completed[id]) }); persist(); return
  }
  const start = run.requests.length
  try {
    const evidence = await action()
    const result = { id, result: 'PASS', attempt, source_sha256: run.source_sha256, srm_image_id: run.srm_image_id,
      request_indexes: [start, run.requests.length - 1], at: new Date().toISOString(), evidence }
    state.completed[id] = clone(result); run.cases.push(result); persist()
    console.log(JSON.stringify({ case: id, result: 'PASS', requests: run.requests.length - start }))
  } catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message }); persist(); throw error }
}
const path = (id) => `/api/srm/supplier/${id}`
const detail = (id, actor = 'srmAdmin') => api(actor, path(id))
async function supplier(key, actor, creditCode, name, ownerUserId) {
  const row = await command(`create-${key}`, actor, '/api/srm/supplier', 'POST', {
    name, supplierType: 'OTHER', creditCode, phone: '13900000135', email: `${key}@example.invalid`, ...(ownerUserId ? { ownerUserId } : {}) })
  assert.ok(row.id && row.processInstanceId); assert.equal(row.status, 'APPROVING')
  state.suppliers[key] = { id: row.id, name, creditCode, actor, initial_process_id: row.processInstanceId,
    created_at: new Date().toISOString(), initial_status: row.status }; persist()
  return row
}
async function waitStatus(id, status) {
  for (let count = 0; count < 120; count++) {
    const row = await detail(id)
    if (row.status === status) return row
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error(`供应商${id}实际状态未达到${status}`)
}
async function decide(alias, processId, approved) {
  const key = `decision:${processId}:${alias}:${approved}`
  if (state.commands[key]?.complete) return
  const tasks = await api(alias, '/api/workflow/task/todo?page=1&size=100')
  const task = tasks.records.find((row) => row.processInstanceId === processId)
  assert.ok(task?.taskId, '实际目标流程没有该账号待办，不能替换成第一条任务')
  assert.equal(String(task.assigneeId), String(local.state.accounts[alias].id))
  await command(key, alias, `/api/workflow/approval/${encodeURIComponent(task.taskId)}/complete`, 'POST', {
    approved, comment: `Goal SRM internal ${approved ? 'approved' : 'rejected'}`, variables: {} })
  state.approvals.push({ process_instance_id: processId, task_id: task.taskId, user_id: local.state.accounts[alias].id,
    actor: alias, approved, at: new Date().toISOString() }); persist()
}
try {
  if (mode === 'admission') {
    await check('SUP-06-internal-create', async () => {
      await api('procStaff', '/api/srm/supplier', 'POST', { name: '', supplierType: 'OTHER' }, 400)
      await api('srmPortal', '/api/srm/supplier', 'POST', { name: 'forbidden supplier', supplierType: 'OTHER' }, 403)
      await api('procStaff', '/api/srm/supplier', 'POST', { name: 'forbidden owner', supplierType: 'OTHER', ownerUserId: 216 }, 403)
      const row = await supplier('main', 'procStaff', '913100002026102231', 'Goal 20261022 internal supplier')
      assert.equal(row.ownerUserId, 215); assert.equal(row.ownerUnitId, 606)
      await api('srmAdmin', '/api/srm/supplier', 'POST', { name: 'duplicate credit', supplierType: 'OTHER', creditCode: '913100002026102231' }, 409)
      const progress = await api('procStaff', `/api/workflow/process-instance/${row.processInstanceId}/progress`)
      assert.ok(progress.activeActivityIds.length > 0)
      return { supplier_id: row.id, process_instance_id: row.processInstanceId, status: row.status, owner_user_id: 215, owner_unit_id: 606 }
    })
    await check('SUP-03-internal-reject-resubmit-approve', async () => {
      const own = state.suppliers.main; assert.ok(own?.id)
      await decide('srmManager', own.initial_process_id, false)
      const rejected = await waitStatus(own.id, 'REJECTED')
      const old = await detail(own.id, 'procStaff')
      await command('edit-rejected-main', 'procStaff', path(own.id), 'PUT', { version: old.version, address: '本Goal虚构内部供应商地址' })
      await api('procStaff', path(own.id), 'PUT', { version: old.version, address: 'stale-write' }, 409)
      const ready = await detail(own.id, 'procStaff')
      const submitted = await command('submit-rejected-main', 'procStaff', `${path(own.id)}/submit`, 'POST', { version: ready.version, reason: '本Goal真实修订后重新准入' })
      assert.equal(submitted.status, 'APPROVING'); assert.notEqual(submitted.processInstanceId, own.initial_process_id)
      own.second_process_id = submitted.processInstanceId; persist()
      for (const alias of ['srmManager', 'srmCompliance', 'srmDirector']) await decide(alias, submitted.processInstanceId, true)
      const approved = await waitStatus(own.id, 'APPROVED')
      assert.equal(approved.ownerUserId, 215); assert.equal(approved.ownerUnitId, 606)
      const progress = await api('procStaff', `/api/workflow/process-instance/${submitted.processInstanceId}/progress`)
      assert.equal(progress.activeActivityIds.length, 0)
      const records = await api('procStaff', `/api/workflow/process-instance/${submitted.processInstanceId}/approval-records`)
      assert.ok(records.length >= 3)
      await api('procStaff', `${path(own.id)}/submit`, 'POST', { version: approved.version }, 409)
      await api('srmAdmin', `${path(own.id)}?version=${approved.version}`, 'DELETE', undefined, 409)
      await detail(own.id, 'procScopeManager'); await api('procPeer', path(own.id), 'GET', undefined, 404)
      return { supplier_id: own.id, rejected_status: rejected.status, old_process_id: own.initial_process_id,
        new_process_id: own.second_process_id, actual_final_status: approved.status, approval_records: records.length }
    })
  } else if (mode === 'withdrawal') {
    for (const [key, action, code, name] of [
      ['withdraw', 'withdraw', '913100002026102232', 'Goal 20261022 withdrawal supplier'],
      ['cancel', 'cancel', '913100002026102233', 'Goal 20261022 cancellation supplier'],
    ]) {
      await check(`SUP-03-${action}-actual-process`, async () => {
        const row = await supplier(key, 'srmAdmin', code, name, 215)
        const before = await detail(row.id)
        await api('procScopeManager', `${path(row.id)}/${action}`, 'POST', { version: before.version, reason: 'non-initiator negative' }, 403)
        const unchanged = await detail(row.id); assert.equal(unchanged.status, 'APPROVING'); assert.equal(unchanged.version, before.version)
        const active = await api('srmAdmin', `/api/workflow/process-instance/${row.processInstanceId}/progress`)
        assert.ok(active.activeActivityIds.length > 0)
        const pending = await command(`terminate-own-${key}`, 'srmAdmin', `${path(row.id)}/${action}`, 'POST', { version: unchanged.version, reason: '本Goal真实终止专用流程' })
        assert.equal(pending.status, 'PENDING_REVIEW'); assert.equal(pending.workflowStartStatus, 'NOT_STARTED'); assert.equal(pending.processInstanceId, null)
        const ended = await api('srmAdmin', `/api/workflow/process-instance/${row.processInstanceId}/progress`)
        assert.equal(ended.activeActivityIds.length, 0)
        const tasks = await api('srmManager', '/api/workflow/task/todo?page=1&size=100')
        assert.ok(!tasks.records.some((task) => task.processInstanceId === row.processInstanceId))
        await api('srmAdmin', `${path(row.id)}/submit`, 'POST', { version: pending.version }, 409)
        await api('srmAdmin', `${path(row.id)}/${action}`, 'POST', { version: pending.version }, 409)
        return { supplier_id: row.id, action, actual_initiator_user_id: 218, other_permitted_manager_user_id: 228,
          non_initiator_refused_without_local_change: true, old_process_id: row.processInstanceId, active_nodes_after: 0,
          final_status: pending.status, no_generic_pending_resubmit: true }
      })
    }
    await check('SUP-05-own-withdrawn-supplier-delete', async () => {
      const own = state.suppliers.withdraw; assert.ok(own?.id)
      const contact = await command('withdrawn-child-contact', 'procStaff', `${path(own.id)}/contact`, 'POST', { name: 'Goal删除范围联系人' })
      const bank = await command('withdrawn-child-bank', 'procStaff', `${path(own.id)}/bank-account`, 'POST', {
        accountName: 'Goal虚构删除账户', accountNo: '6222000000000000232', bankName: '本Goal虚构银行' })
      const before = await detail(own.id)
      await api('srmAdmin', `${path(own.id)}?version=-1`, 'DELETE', undefined, 400)
      await api('srmAdmin', `${path(own.id)}?version=${before.version - 1}`, 'DELETE', undefined, 409)
      assert.equal((await detail(own.id)).version, before.version)
      await command('delete-own-withdrawn-supplier', 'srmAdmin', `${path(own.id)}?version=${before.version}`, 'DELETE')
      await api('srmAdmin', path(own.id), 'GET', undefined, 404)
      await api('srmAdmin', `${path(own.id)}/contact/${contact.id}`, 'GET', undefined, 404)
      await api('srmAdmin', `${path(own.id)}/bank-account/${bank.id}`, 'GET', undefined, 404)
      own.deleted_by_formal_api = true; own.child_contact_id = contact.id; own.child_bank_id = bank.id; persist()
      return { supplier_id: own.id, invalid_and_stale_deletes_refused: true, formal_delete: true,
        parent_and_children_unreachable_by_api: true, direct_database_tombstone_inspection: false, retained_cancel_supplier_id: state.suppliers.cancel.id }
    })
  }
  run.result = 'PASS_ACTUAL_LIFECYCLE_PHASE_NOT_FULL_SRM'; run.finished_at = new Date().toISOString(); persist(); local.publish()
  console.log(JSON.stringify({ result: run.result, mode, cases: run.cases.length, requests: run.requests.length }))
} catch (error) { run.result = 'FAIL'; run.error = error.message; run.finished_at = new Date().toISOString(); persist(); throw error }
