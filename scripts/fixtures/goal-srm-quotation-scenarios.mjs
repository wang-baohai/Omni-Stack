import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP26S02：固定专用种子，真实采购前置和门户报价；不读取源仓秘密，不重放未知写入。
const local = createLocalContext()
assert.equal(process.cwd(), '/validation/source')
assert.ok(!existsSync('/var/run/docker.sock'))
const [mode, attempt] = process.argv.slice(2)
assert.ok(['invites', 'prepare', 'quote', 'closed'].includes(mode))
assert.match(attempt ?? '', new RegExp(`^quotation-${mode}-r[1-9][0-9]*$`))
for (const name of ['SRM', 'PROCUREMENT', 'WORKFLOW']) assert.match(process.env[`E2E_${name}_IMAGE_ID`] ?? '', /^sha256:[a-f0-9]{64}$/)
const file = `${privateRoot}/srm-quotation-scenarios.json`
const clone = value => JSON.parse(JSON.stringify(value))
const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : {
  namespace: 'GOAL20261022SRMQUOTE', supplier_id: 10, materials: [], commands: {}, completed: {}, runs: [],
}
assert.equal(state.namespace, 'GOAL20261022SRMQUOTE'); assert.equal(state.supplier_id, 10)
const dir = `/validation/evidence/codex-2026-10-22/wp26-s02/${attempt}`
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const raw = `${privateRoot}/${attempt}-wire.ndjson`
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
function redact(value) {
  if (Array.isArray(value)) return value.map(redact)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
    [key, /token|password|secret|authorization/i.test(key) && item != null ? '[PRIVATE]' : redact(item)]))
  return value
}
const run = { original_step: 'WP-26.S02', mode, attempt, started_at: new Date().toISOString(), identity_source: 'test-fixture', workers: 1,
  runtime_images: Object.fromEntries(['SRM', 'PROCUREMENT', 'WORKFLOW'].map(name => [name, process.env[`E2E_${name}_IMAGE_ID`]])),
  source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'), cases: [], requests: [] }
state.runs.push(run)
let currentCase
const persist = () => {
  writeFileSync(file, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(`${dir}/result.json`, JSON.stringify(redact({ ...run, objects: { supplier_id: state.supplier_id, materials: state.materials,
    requisition: state.requisition, rfq: state.rfq, quotation: state.quotation, invite: state.invite }, original_raw_log: raw }), null, 2) + '\n')
}
const tokens = {}
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(/^\/api\/(srm|procurement|workflow)\//.test(path), '只允许正式网关业务接口')
  tokens[actor] ??= await local.login(actor)
  const entry = { case_id: currentCase, actor, user_id: local.state.accounts[actor].id, method, path, body: redact(body), at: new Date().toISOString() }
  run.requests.push(entry); persist()
  let response
  try {
    response = await fetch(`http://omni-gateway:8080${path}`, { method, headers: { Authorization: `Bearer ${tokens[actor]}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
  } catch {
    entry.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist()
    throw new Error(`${method} ${path}结果未知，必须按固定对象及真实历史核对，不能自动重发`)
  }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ ...entry, http_status: response.status, response: json }) + '\n')
  entry.http_status = response.status; entry.response = redact(json); persist()
  assert.equal(json.code ?? response.status, expected, `${method} ${path}实际业务码${json.code ?? response.status}不符`)
  return json.data
}
async function command(key, actor, path, method, body, expected = 200) {
  if (state.commands[key]?.complete) return clone(state.commands[key].result)
  assert.ok(!state.commands[key], `命令${key}结果未知，不能重放`)
  state.commands[key] = { actor, path, method, body: clone(body ?? null), attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist()
  return result
}
async function check(id, action) {
  currentCase = id
  if (state.completed[id]) {
    assert.deepEqual(state.completed[id].runtime_images, run.runtime_images, '新镜像不能自动继承旧证据')
    run.cases.push({ id, result: 'REUSE_PREVIOUS_ACTUAL_PASS', evidence: state.completed[id] }); persist(); return
  }
  const start = run.requests.length
  try {
    const evidence = await action()
    const item = { id, result: 'PASS', attempt, at: new Date().toISOString(), runtime_images: run.runtime_images,
      request_indexes: [start, run.requests.length - 1], evidence }
    state.completed[id] = clone(item); run.cases.push(item); persist()
    console.log(JSON.stringify({ case: id, result: 'PASS', requests: run.requests.length - start }))
  } catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message }); persist(); throw error }
}
const reqPath = () => `/api/procurement/requisition/${state.requisition.id}`
const rfqPath = () => `/api/procurement/rfq/${state.rfq.id}`
const invitationPath = () => `/api/srm/portal/quotation/invitations/${state.rfq.id}`
const dateAfterDays = days => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(Date.now() + days * 86400000))
try {
  if (mode === 'invites') {
    await check('INV-01-INV-02-aliases-permissions-and-revocation', async () => {
      const original = JSON.parse(readFileSync(`${privateRoot}/srm-portal-scenarios.json`, 'utf8'))
      const lists = []
      for (const path of ['/api/srm/portal/invite', '/api/srm/portal/invite/list', '/api/srm/invite', '/api/srm/invite/list']) {
        const rows = await api('srmAdmin', path); lists.push(rows)
        assert.ok(rows.every(row => row.inviteToken == null), '列表不能再次披露原始邀请令牌')
      }
      assert.ok(lists.every(rows => JSON.stringify(rows.map(row => row.id)) === JSON.stringify(lists[0].map(row => row.id))))
      const ownUsed = lists[0].find(row => Number(row.id) === Number(original.invite.id))
      assert.ok(ownUsed); assert.equal(ownUsed.usedCount, 1); assert.equal(ownUsed.status, 'USED')
      await api('srmPortal', '/api/srm/invite/list', 'GET', undefined, 403)
      await api('procStaff', '/api/srm/portal/invite', 'POST', { maxUses: 1, expiresHours: 1 }, 403)
      await api('srmAdmin', '/api/srm/invite', 'POST', { maxUses: 0, expiresHours: 1 }, 400)
      await api('srmAdmin', '/api/srm/invite', 'POST', { maxUses: 1, expiresHours: 0 }, 400)
      const invite = await command('create-invite', 'srmAdmin', '/api/srm/invite', 'POST', { maxUses: 1, expiresHours: 1 })
      assert.ok(invite.id && invite.inviteToken); assert.equal(invite.status, 'ACTIVE')
      state.invite = invite; persist()
      await api('procStaff', `/api/srm/portal/invite/${invite.id}/revoke`, 'POST', undefined, 403)
      await command('revoke-invite', 'srmAdmin', `/api/srm/portal/invite/${invite.id}/revoke`, 'POST')
      await api('srmAdmin', `/api/srm/invite/${invite.id}/revoke`, 'POST', undefined, 409)
      const after = (await api('srmAdmin', '/api/srm/invite/list')).find(row => row.id === invite.id)
      assert.equal(after.status, 'REVOKED'); assert.equal(after.usedCount, 0); assert.ok(after.inviteToken == null)
      return { used_goal_invite_id: ownUsed.id, revoked_goal_invite_id: invite.id, original_invite_kept: true }
    })
  }
  if (mode === 'prepare') {
    await check('POR-05-real-requisition-and-approval', async () => {
      const supplier = await api('procStaff', '/api/srm/supplier/10'); assert.equal(supplier.status, 'APPROVED')
      const categories = await api('procScopeManager', '/api/procurement/material/category/list')
      const flatten = rows => rows.flatMap(row => [row, ...flatten(row.children ?? [])])
      assert.ok(flatten(categories).some(row => row.id === 5 && row.categoryCode === 'LAPTOP' && row.status === 1))
      const routes = await api('procScopeManager', '/api/procurement/approval-route/list?page=1&size=100')
      assert.ok(routes.records.some(row => row.id === 1 && row.status === 'ACTIVE' && row.categoryCode === '*' && row.modelVersionId === 2 && row.workflowAvailability === 'AVAILABLE'))
      for (let index = 1; index <= 2; index++) {
        const materialCode = `GOAL20261022_SRM_Q${index}`
        if (!state.commands[`material-${index}`]) {
          const matches = await api('procScopeManager', `/api/procurement/material/list?keyword=${materialCode}`)
          assert.equal(matches.total, 0, '同名未登记对象不得冒认为本次种子')
        }
        const material = await command(`material-${index}`, 'procScopeManager', '/api/procurement/material', 'POST', {
          categoryId: 5, materialCode, materialName: `Goal SRM quotation sample ${index}`, unit: 'PCS', assetManaged: false,
          status: 'ACTIVE', specification: '本Goal虚构报价前置，不产生真实采购交付' })
        if (!state.materials.some(row => row.id === material.id)) state.materials.push(material)
        persist()
      }
      const created = await command('create-requisition', 'procStaff', '/api/procurement/requisition', 'POST', {
        title: 'Goal 20261022 SRM quotation requisition', reason: '专用门户报价验收，真实流程审批',
        lines: state.materials.map((row, index) => ({ materialId: row.id, quantity: String(index + 2), estimatedUnitPrice: '20.000000', remark: '虚构样本' })) })
      state.requisition ??= created; persist()
      await command('submit-requisition', 'procStaff', `${reqPath()}/submit`, 'POST', { version: created.version })
      const submitted = await api('procStaff', reqPath()); assert.equal(submitted.status, 'APPROVING'); assert.ok(submitted.processInstanceId)
      if (!state.commands['approve-requisition']?.complete) {
        const tasks = await api('procManager', '/api/workflow/task/todo?page=1&size=100')
        const task = tasks.records.find(row => row.processInstanceId === submitted.processInstanceId)
        assert.ok(task?.taskId); assert.equal(Number(task.assigneeId), 217)
        await command('approve-requisition', 'procManager', `/api/workflow/approval/${encodeURIComponent(task.taskId)}/complete`, 'POST', {
          approved: true, comment: '本Goal门户报价真实采购前置审批', variables: {} })
        state.approval_task_id = task.taskId; persist()
      }
      let approved
      for (let count = 0; count < 90; count++) {
        approved = await api('procStaff', reqPath())
        if (approved.status === 'APPROVED') break
        await new Promise(resolve => setTimeout(resolve, 1000))
      }
      assert.equal(approved.status, 'APPROVED'); state.requisition = approved; persist()
      await api('procPeer', reqPath(), 'GET', undefined, 404); await api('procScopeManager', reqPath())
      return { requisition_id: approved.id, process_instance_id: approved.processInstanceId, approval_task_id: state.approval_task_id,
        actual_approver_id: 217, source_route_unchanged: 1, model_version_id: 2, dedicated_material_ids: state.materials.map(row => row.id) }
    })
    await check('POR-05-real-rfq-sent-and-portal-isolation', async () => {
      state.quotation_deadline ??= dateAfterDays(3); state.valid_until ??= dateAfterDays(7); persist()
      const created = await command('create-rfq', 'procStaff', '/api/procurement/rfq', 'POST', {
        requisitionId: state.requisition.id, title: 'Goal 20261022 SRM portal quotation', quotationDeadline: state.quotation_deadline, supplierIds: [10] })
      state.rfq ??= created; persist(); assert.equal(created.status, 'DRAFT')
      await command('send-rfq', 'procStaff', `${rfqPath()}/send`, 'POST', { version: created.version })
      state.rfq = await api('procStaff', rfqPath()); assert.equal(state.rfq.status, 'SENT'); assert.equal(state.rfq.lines.length, 2); persist()
      const list = await api('srmPortal', '/api/srm/portal/quotation/invitations'); assert.ok(list.some(row => row.rfqId === state.rfq.id))
      const invitation = await api('srmPortal', invitationPath()); assert.equal(invitation.status, 'SENT'); assert.equal(invitation.lines.length, 2)
      await api('supplier', invitationPath(), 'GET', undefined, 404); await api('srmAdmin', invitationPath(), 'GET', undefined, 403)
      return { rfq_id: state.rfq.id, rfq_no: state.rfq.rfqNo, real_status: state.rfq.status, supplier_id: 10, line_count: 2, uninvited_supplier_id: 9 }
    })
  }
  if (mode === 'quote') {
    assert.ok(state.completed['POR-05-real-rfq-sent-and-portal-isolation'])
    await check('POR-05-quote-validation-and-first-submit', async () => {
      const invitation = await api('srmPortal', invitationPath()); assert.equal(invitation.currentQuotation, null)
      const body = { requestId: 'goal_20261022_srm_quote_v1', rfqId: state.rfq.id, version: 0, validUntil: state.valid_until,
        lines: invitation.lines.map((row, index) => ({ rfqLineId: row.rfqLineId, unitPrice: index === 0 ? '10.125000' : '20.500000', deliveryDays: 7, remark: '虚构报价样本' })) }
      state.first_quote_body ??= body; persist()
      const invalid = (suffix, fields) => ({ ...body, requestId: `goal_20261022_srm_quote_bad_${suffix}`, ...fields })
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', invalid('missing', { lines: body.lines.slice(0, 1) }), 400)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', invalid('duplicate', { lines: [body.lines[0], body.lines[0]] }), 400)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', invalid('number', { lines: [{ ...body.lines[0], unitPrice: 10.125 }, body.lines[1]] }), 400)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', invalid('date', { validUntil: dateAfterDays(1) }), 400)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', invalid('version', { version: 7 }), 409)
      await api('supplier', '/api/srm/portal/quotation', 'POST', invalid('outsider', {}), 404)
      await api('srmAdmin', '/api/srm/portal/quotation', 'POST', body, 403)
      const quotation = await command('first-quote', 'srmPortal', '/api/srm/portal/quotation', 'POST', body)
      assert.equal(quotation.supplierId, 10); assert.equal(quotation.version, 1); assert.equal(quotation.status, 'SUBMITTED')
      assert.equal(quotation.totalAmount, '81.7500'); assert.ok(quotation.lines.every(row => typeof row.unitPrice === 'string'))
      state.quotation = quotation; persist()
      return { quotation_id: quotation.id, version: quotation.version, total_amount: quotation.totalAmount }
    })
    await check('POR-05-real-idempotency-and-versioned-update', async () => {
      const first = state.first_quote_body
      const replay = await api('srmPortal', '/api/srm/portal/quotation', 'POST', first)
      assert.equal(replay.id, state.quotation.id); assert.equal(replay.version, 1)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', { ...first, lines: first.lines.map(row => ({ ...row, deliveryDays: 8 })) }, 409)
      const second = { ...first, requestId: 'goal_20261022_srm_quote_v2', version: 1,
        lines: first.lines.map((row, index) => index === 0 ? { ...row, unitPrice: '11.125000' } : row) }
      const updated = await command('second-quote', 'srmPortal', '/api/srm/portal/quotation', 'POST', second)
      assert.equal(updated.id, replay.id); assert.equal(updated.version, 2); assert.equal(updated.totalAmount, '83.7500')
      state.quotation = updated; state.second_quote_body = second; persist()
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', { ...second, requestId: 'goal_20261022_srm_quote_stale', version: 1 }, 409)
      const historical = await api('srmPortal', '/api/srm/portal/quotation', 'POST', first)
      assert.equal(historical.version, 2); assert.equal(historical.totalAmount, updated.totalAmount)
      const current = (await api('srmPortal', invitationPath())).currentQuotation
      assert.equal(current.version, 2); assert.equal(current.id, updated.id)
      return { quotation_id: current.id, current_version: 2, total_amount: current.totalAmount, historical_replay_returns_current_snapshot: true }
    })
  }
  if (mode === 'closed') {
    assert.ok(state.completed['POR-05-real-idempotency-and-versioned-update'])
    await check('POR-05-closed-rfq-new-intent-denied-history-readable', async () => {
      const rfq = await api('procStaff', rfqPath()); assert.equal(rfq.status, 'SENT')
      await command('cancel-rfq', 'procStaff', `${rfqPath()}/cancel`, 'POST', { version: rfq.version })
      state.rfq = await api('procStaff', rfqPath()); assert.equal(state.rfq.status, 'CANCELLED'); persist()
      const after = await api('srmPortal', invitationPath()); assert.equal(after.status, 'CANCELLED'); assert.equal(after.currentQuotation.version, 2)
      await api('srmPortal', '/api/srm/portal/quotation', 'POST', { ...state.second_quote_body, requestId: 'goal_20261022_srm_quote_closed', version: 2 }, 409)
      const replay = await api('srmPortal', '/api/srm/portal/quotation', 'POST', state.first_quote_body)
      assert.equal(replay.version, 2); assert.equal(replay.id, state.quotation.id)
      return { rfq_id: state.rfq.id, real_rfq_status: 'CANCELLED', quotation_version_unchanged: 2, historical_replay_not_new_submission: true }
    })
  }
  run.result = 'PASS'; run.finished_at = new Date().toISOString(); persist()
  console.log(JSON.stringify({ result: run.result, cases: run.cases.length, requests: run.requests.length, output: dir }))
} catch (error) {
  run.result = 'FAIL'; run.error = error.message; run.finished_at = new Date().toISOString(); persist()
  console.error(JSON.stringify({ result: 'FAIL', error: error.message, output: dir })); process.exitCode = 1
}
