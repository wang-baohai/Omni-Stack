import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP26S02：专用账号真实角色失败/退避重试和门户准入；业务状态完全由正式链路推进。
const local = createLocalContext()
assert.equal(process.cwd(), '/validation/source')
const mode = process.argv[2]
const attempt = process.argv[3]
const modes = { 'fail-role': 'fail', 'recover-role': 'recover', 'reject-admission': 'reject', 'approve-admission': 'approve' }
assert.ok(Object.hasOwn(modes, mode))
assert.match(attempt ?? '', new RegExp(`^portal-${modes[mode]}-r[1-9][0-9]*$`))
assert.match(process.env.E2E_SRM_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const output = `/validation/evidence/codex-2026-10-22/wp26-s02/${attempt}`
assert.ok(!existsSync(output)); mkdirSync(output, { recursive: true })
const file = `${privateRoot}/srm-portal-scenarios.json`
const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : { namespace: 'GOAL20261022SRM',
  request_id: 'goal_20261022_srmportal_enrollment', credit_code: '913100002026102229', name: 'Goal 20261022 SRM portal supplier',
  commands: {}, completed: {}, runs: [], approvals: [] }
assert.equal(state.namespace, 'GOAL20261022SRM')
assert.ok(!state.completed[mode], '本业务阶段已有真实完成断点，须复验当前状态，不重复操作历史')
const run = { original_step: 'WP-26.S02', mode, attempt, namespace: state.namespace, started_at: new Date().toISOString(),
  srm_image_id: process.env.E2E_SRM_IMAGE_ID, identity_source: 'test-fixture',
  source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'), cases: [], requests: [] }
state.runs.push(run)
const raw = `${privateRoot}/srm-portal-${attempt}.ndjson`
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const clone = (value) => JSON.parse(JSON.stringify(value))
const redact = (value) => {
  if (Array.isArray(value)) return value.map(redact)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
    [key, /password|captcha|token|secret/i.test(key) ? '<redacted>' : redact(item)]))
  return value
}
const persist = () => {
  writeFileSync(file, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(`${output}/result.json`, JSON.stringify({ ...run, supplier_id: state.supplier_id, request_id: state.request_id,
    invite_id: state.invite?.id, original_raw_log: raw, approvals: state.approvals }, null, 2) + '\n')
}
const tokens = { admin: local.bootstrap() }
async function token(actor) { return tokens[actor] ??= await local.login(actor) }
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(path.startsWith('/api/srm/') || path.startsWith('/api/workflow/') || path.startsWith('/api/auth/user/'))
  const entry = { actor, method, path, at: new Date().toISOString(), body: redact(body),
    body_sha256: body ? createHash('sha256').update(JSON.stringify(body)).digest('hex') : undefined }
  run.requests.push(entry); persist()
  let response
  try {
    response = await fetch(`http://omni-gateway:8080${path}`, { method, headers: { Authorization: `Bearer ${await token(actor)}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
  } catch {
    entry.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist()
    throw new Error(`${method} ${path}网络结果未确定，先核对正式对象，禁止自动重发`)
  }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ actor, method, path, body, http_status: response.status, response: json }) + '\n')
  entry.http_status = response.status; entry.response = redact(json); persist()
  const actual = json.code ?? response.status
  assert.ok((Array.isArray(expected) ? expected : [expected]).includes(actual), `${method} ${path}实际业务码${actual}不符`)
  return json.data
}
async function command(key, actor, path, method, body, expected = 200) {
  const old = state.commands[key]
  if (old?.complete) return clone(old.result)
  assert.ok(!old, `${key}写入结果未确定，先读回核对，不能重复发送`)
  state.commands[key] = { actor, path, method, at: new Date().toISOString(), body: clone(body ?? null), attempt }; persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, completed_at: new Date().toISOString(), result: clone(result) }); persist()
  return result
}
function checkpoint(id, evidence) { run.cases.push({ id, result: 'PASS', at: new Date().toISOString(), evidence }); persist(); console.log(JSON.stringify({ case: id, result: 'PASS' })) }
async function pollEnrollment(status) {
  for (let count = 0; count < 120; count++) {
    const result = await api('srmPortal', '/api/srm/portal/enrollment')
    if (result?.status === status) return result
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error(`真实入驻状态未达到${status}`)
}
const detail = () => api('srmAdmin', `/api/srm/supplier/${state.supplier_id}`)
async function pollSupplier(status) {
  for (let count = 0; count < 120; count++) {
    const result = await detail()
    if (result.status === status) return result
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  throw new Error(`真实供应商状态未达到${status}`)
}
const payload = () => ({ requestId: state.request_id, inviteToken: state.invite.inviteToken, creditCode: state.credit_code,
  name: state.name, supplierType: 'OTHER', phone: '13900000133', email: 'srm-portal@example.invalid' })
async function restoreOwnedAccount() {
  const account = local.state.accounts.srmPortal
  const user = await api('admin', `/api/auth/user/${account.id}`)
  assert.equal(user.username, 'goal_20261022_srmportal'); assert.equal(Number(user.tenantId), 1)
  if (user.status === 0) {
    assert.equal(state.disabled_user_id, account.id, '不接管外部停用的账号')
    await api('admin', `/api/auth/user/${account.id}/status?status=1`, 'PUT')
    const enabled = await api('admin', `/api/auth/user/${account.id}`); assert.equal(enabled.status, 1)
    checkpoint('restore-only-owned-test-account', { id: account.id, status: enabled.status, method: 'formal-api' })
  }
  state.disabled_user_id = null; persist()
}
async function decide(alias, processId, approved) {
  const key = `${processId}:${alias}:${approved}`
  if (state.commands[key]?.complete) return
  const tasks = await api(alias, '/api/workflow/task/todo?page=1&size=100')
  const task = tasks.records.find((row) => row.processInstanceId === processId)
  assert.ok(task?.taskId, `${alias}没有本次流程的实际待办`)
  assert.equal(String(task.assigneeId), String(local.state.accounts[alias].id))
  await command(key, alias, `/api/workflow/approval/${encodeURIComponent(task.taskId)}/complete`, 'POST', {
    approved, comment: `Goal 20261022 SRM ${approved ? 'approval' : 'rejection'} test`, variables: {} })
  state.approvals.push({ alias, user_id: local.state.accounts[alias].id, process_instance_id: processId, task_id: task.taskId,
    approved, at: new Date().toISOString() }); persist()
  checkpoint('actual-assigned-candidate-decision', { alias, task_id: task.taskId, process_instance_id: processId, approved })
}
async function workflowRound(processId) {
  const page = await api('srmPortal', '/api/workflow/process-instance/my-initiated?page=1&size=100')
  const row = page.records.find((item) => item.processInstanceId === processId)
  assert.ok(row, '必须找到当前门户账号实际发起的目标流程，不能从未公开字段推断轮次')
  assert.equal(Number(row.startUserId), Number(local.state.accounts.srmPortal.id))
  assert.equal(row.businessType, 'SRM_SUPPLIER_ONBOARDING')
  const match = String(row.businessKey).match(new RegExp(`^${state.supplier_id}:([1-9][0-9]*)$`))
  assert.ok(match, '业务键必须给出本供应商的真实准入轮次')
  const round = Number(match[1]); assert.ok(Number.isSafeInteger(round) && round > 0)
  return { process_instance_id: processId, business_key: row.businessKey, round }
}
try {
  const account = local.state.accounts.srmPortal
  assert.equal(account.username, 'goal_20261022_srmportal'); assert.equal(Number(account.id), 229)
  if (state.disabled_user_id) await restoreOwnedAccount()
  await token('srmPortal'); await token('srmAdmin')
  if (mode === 'fail-role') {
    assert.deepEqual(account.actual_roles, ['USER'])
    await api('srmPortal', '/api/srm/portal/profile', 'GET', undefined, 403)
    await api('srmAdmin', '/api/srm/portal/profile', 'GET', undefined, 403)
    let enrollment = await api('srmPortal', '/api/srm/portal/enrollment')
    if (enrollment) assert.equal(enrollment.requestId, state.request_id)
    if (!state.invite) {
      state.invite = await command('create-invite', 'srmAdmin', '/api/srm/portal/invite', 'POST', { maxUses: 1, expiresHours: 24 })
      assert.ok(state.invite.id && state.invite.inviteToken); persist()
    }
    if (!enrollment) {
      const user = await api('admin', `/api/auth/user/${account.id}`)
      assert.equal(user.status, 1)
      state.disabled_user_id = account.id; persist()
      try {
        await command('disable-own-user-for-real-role-failure', 'admin', `/api/auth/user/${account.id}/status?status=0`, 'PUT')
        enrollment = await command('enroll-owned-user', 'srmPortal', '/api/srm/portal/enroll', 'POST', payload())
        state.supplier_id = enrollment.supplierId; persist()
        assert.equal(enrollment.status, 'PENDING_ROLE_ASSIGN')
        checkpoint('formal-enrollment-accepted-not-completed', { supplier_id: state.supplier_id, status: enrollment.status })
        enrollment = await pollEnrollment('ROLE_ASSIGN_FAILED')
        assert.equal(enrollment.lastErrorCode, 'USER_NOT_FOUND_OR_DISABLED')
        const supplier = await detail(); assert.equal(supplier.status, 'REGISTERING_FAILED')
        assert.equal(supplier.ownerUserId, null); assert.equal(supplier.ownerUnitId, null)
        state.role_failure = clone(enrollment); persist()
        checkpoint('actual-auth-refusal-reaches-role-failure', { error: enrollment.lastErrorCode, status: enrollment.status,
          supplier_status: supplier.status, next_retry_time: enrollment.nextRetryTime, supplier_id: state.supplier_id })
      } finally { await restoreOwnedAccount() }
    } else {
      state.supplier_id = enrollment.supplierId; persist()
      assert.equal(enrollment.status, 'ROLE_ASSIGN_FAILED'); assert.ok(state.role_failure)
    }
  } else if (mode === 'recover-role') {
    assert.ok(state.completed['fail-role'] && state.role_failure)
    let enrollment = await api('srmPortal', '/api/srm/portal/enrollment')
    assert.equal(enrollment.requestId, state.request_id)
    assert.equal(enrollment.status, 'ROLE_ASSIGN_FAILED')
    const retryAfter = Date.parse(`${enrollment.nextRetryTime.replace(' ', 'T')}+08:00`)
    assert.ok(Number.isFinite(retryAfter))
    if (Date.now() < retryAfter) {
      await api('srmPortal', '/api/srm/portal/enrollment/retry', 'POST', undefined, 429)
      checkpoint('real-backoff-rejects-early-retry', { next_retry_time: enrollment.nextRetryTime })
      while (Date.now() <= retryAfter + 100) await new Promise((resolve) => setTimeout(resolve, 500))
    } else run.observations = ['开始本轮恢复时服务端退避时间已过去，不虚构已实测429；失败阶段的真实nextRetryTime保留。']
    const beforeToken = tokens.srmPortal
    const pending = await command('real-role-retry', 'srmPortal', '/api/srm/portal/enrollment/retry', 'POST')
    assert.equal(pending.supplierId, state.supplier_id); assert.equal(pending.requestId, state.request_id)
    enrollment = await pollEnrollment('COMPLETED'); assert.equal(Number(enrollment.retryCount), 1)
    checkpoint('same-request-retry-completes-real-saga', { supplier_id: state.supplier_id, retry_count: enrollment.retryCount, status: enrollment.status })
    await api('srmPortal', '/api/srm/portal/profile', 'GET', undefined, 403)
    assert.equal(tokens.srmPortal, beforeToken)
    tokens.srmPortal = await local.login('srmPortal')
    assert.deepEqual([...account.actual_roles].sort(), ['SUPPLIER', 'USER'])
    account.role_ids = await api('admin', `/api/auth/user/${account.id}/roles`)
    account.portal_enrolled = true; local.save()
    const profile = await api('srmPortal', '/api/srm/portal/profile'); assert.equal(Number(profile.supplierId), Number(state.supplier_id))
    await api('srmPortal', '/api/srm/supplier/list?page=1&size=10', 'GET', undefined, 403)
    const repeated = await api('srmPortal', '/api/srm/portal/enroll', 'POST', payload())
    assert.equal(repeated.supplierId, state.supplier_id); assert.equal(repeated.status, 'COMPLETED')
    await api('srmPortal', '/api/srm/portal/enroll', 'POST', { ...payload(), creditCode: '913100002026102230' }, 409)
    const replay = await api('srmPortal', '/api/srm/portal/enrollment/retry', 'POST')
    assert.equal(replay.status, 'COMPLETED'); assert.equal(Number(replay.retryCount), 1)
    const invites = await api('srmAdmin', '/api/srm/portal/invite')
    assert.equal(Number(invites.find((row) => row.id === state.invite.id).usedCount), 1)
    const supplier = await pollSupplier('APPROVING')
    assert.ok(supplier.processInstanceId); assert.equal(supplier.ownerUserId, null); assert.equal(supplier.ownerUnitId, null)
    state.first_process_id = supplier.processInstanceId; persist()
    checkpoint('role-refresh-profile-and-internal-boundary', { user_id: account.id, supplier_id: state.supplier_id, roles: account.actual_roles,
      invite_uses: 1, process_instance_id: supplier.processInstanceId, final_supplier_status: supplier.status })
  } else if (mode === 'reject-admission') {
    assert.ok(state.completed['recover-role'])
    const supplier = await detail(); assert.equal(supplier.status, 'APPROVING'); assert.equal(supplier.processInstanceId, state.first_process_id)
    await decide('srmManager', supplier.processInstanceId, false)
    const rejected = await pollSupplier('REJECTED')
    checkpoint('real-workflow-rejection-to-supplier', { supplier_id: state.supplier_id, status: rejected.status, process_instance_id: state.first_process_id })
    const before = await api('srmPortal', '/api/srm/portal/profile')
    const edited = await command('portal-profile-edit', 'srmPortal', '/api/srm/portal/profile', 'PUT', {
      version: before.version, address: '本Goal专用虚构供应商地址', website: 'https://example.invalid/goal-srm' })
    assert.equal(edited.address, '本Goal专用虚构供应商地址')
    await api('srmPortal', '/api/srm/portal/profile', 'PUT', { version: before.version, address: 'stale-write' }, 409)
    assert.equal((await api('srmPortal', '/api/srm/portal/profile')).address, edited.address)
    state.first_round = await workflowRound(state.first_process_id); persist()
    checkpoint('portal-profile-edit-and-stale-refusal', { supplier_id: state.supplier_id, status: edited.status, version: edited.version })
  } else if (mode === 'approve-admission') {
    assert.ok(state.completed['reject-admission'])
    const profile = await api('srmPortal', '/api/srm/portal/profile')
    const submitted = await command('portal-profile-resubmit', 'srmPortal', '/api/srm/portal/profile/submit', 'POST', { version: profile.version, reason: 'Goal corrected application' })
    assert.equal(submitted.status, 'APPROVING')
    const supplier = await detail(); assert.notEqual(supplier.processInstanceId, state.first_process_id)
    const firstRound = await workflowRound(state.first_process_id)
    const secondRound = await workflowRound(supplier.processInstanceId)
    assert.equal(secondRound.round, firstRound.round + 1)
    state.first_round = firstRound; state.second_round = secondRound; persist()
    state.second_process_id = supplier.processInstanceId; persist()
    for (const alias of ['srmManager', 'srmCompliance', 'srmDirector']) await decide(alias, supplier.processInstanceId, true)
    const approved = await pollSupplier('APPROVED')
    assert.equal(approved.ownerUserId, null); assert.equal(approved.ownerUnitId, null)
    const finalProfile = await api('srmPortal', '/api/srm/portal/profile'); assert.equal(finalProfile.status, 'APPROVED')
    await api('srmPortal', '/api/srm/portal/profile/submit', 'POST', { version: finalProfile.version }, 409)
    checkpoint('portal-resubmission-and-three-real-approvals', { supplier_id: state.supplier_id, old_process_id: state.first_process_id,
      new_process_id: state.second_process_id, first_round: firstRound, second_round: secondRound, status: approved.status, owner_user_id: approved.ownerUserId })
  }
  run.result = 'PASS_ACTUAL_PORTAL_PHASE_NOT_FULL_SRM'; run.finished_at = new Date().toISOString()
  state.completed[mode] = { attempt, srm_image_id: run.srm_image_id, source_sha256: run.source_sha256, at: run.finished_at,
    supplier_id: state.supplier_id, cases: clone(run.cases) }; persist(); local.publish()
  console.log(JSON.stringify({ result: run.result, mode, supplier_id: state.supplier_id, cases: run.cases.length, requests: run.requests.length }))
} catch (error) {
  run.result = 'FAIL'; run.error = error.message; run.finished_at = new Date().toISOString(); persist(); throw error
}
