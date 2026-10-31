import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02：复用既有审批定义和专用候选人，真实触发启动失败；不改候选规则或业务状态。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
const local = createLocalContext()
const [mode, attempt] = process.argv.slice(2)
assert.ok(['prepare-failed', 'complete'].includes(mode))
assert.match(attempt ?? '', new RegExp(`^recovery-${mode}-r[1-9][0-9]*$`))
assert.equal(process.env.E2E_PROCUREMENT_IMAGE_ID, 'sha256:e043e8bebc7df4cb5c8b55bb88e9624c9e204f7c2897dda30c942e6921d2d340')
assert.equal(process.env.E2E_WORKFLOW_IMAGE_ID, 'sha256:2d0479552f79fd62fe589e263dd6ffa87c74916f1f7c75b31c5f7d6a130094da')
const statePath = privateRoot + '/procurement-recovery-scenarios.json'
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : { namespace: 'GOAL20261022PROCRECOVERY', commands: {}, objects: {}, completed: {}, runs: [] }
assert.equal(state.namespace, 'GOAL20261022PROCRECOVERY')
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const raw = privateRoot + '/' + attempt + '-wire.ndjson'
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const clone = value => JSON.parse(JSON.stringify(value))
const run = { original_step: 'WP-27.S02', mode, attempt, started_at: new Date().toISOString(), identity_source: 'test-fixture', workers: 1,
  procurement_image: process.env.E2E_PROCUREMENT_IMAGE_ID, workflow_image: process.env.E2E_WORKFLOW_IMAGE_ID,
  script_sha256: sha(readFileSync(new URL(import.meta.url))), requests: [], checks: [], original_raw_log: raw,
  candidate_rule_changed: false, shared_role_changed: false, business_database_directly_updated: false,
  limits: ['本fixture仅REQ04，不作为全部17任务/53接口/文图验收', '复用原定义、物料、规则；只短暂禁用本Goal专用217，不能对共享基础设施故障注入', 'DTO不公开原requestId；直接核验businessKey/attempt/modelVersion/实际实例，requestId复用由现行源码及后续原G11补充，不冒称已读取'] }
state.runs.push({ attempt, mode, started_at: run.started_at })
const persist = () => {
  writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(dir + '/result.json', JSON.stringify({ ...run, objects: state.objects, candidate: state.candidate }, null, 2) + '\n')
}
const tokens = {}
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(/^\/api\/(procurement|workflow|auth)\//.test(path))
  if (actor === 'seedAdmin') assert.ok(/^\/api\/auth\/user\/217(?:\/roles|\/status\?status=[01])?$/.test(path), '管理员只可处理已登记专用217的配置')
  tokens[actor] ??= actor === 'seedAdmin' ? local.bootstrap() : await local.login(actor)
  const row = { actor, method, path, body: body ?? null, expected_business_code: expected, at: new Date().toISOString() }
  run.requests.push(row); persist()
  let response
  try {
    response = await fetch('http://omni-gateway:8080' + path, { method, headers: { Authorization: `Bearer ${tokens[actor]}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
  } catch {
    row.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist()
    throw new Error(method + ' ' + path + '结果未知，先读回，不能盲重发')
  }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ ...row, http_status: response.status, response: json }) + '\n')
  row.http_status = response.status; row.business_code = json.code ?? response.status; row.payload_sha256 = sha(JSON.stringify(json)); persist()
  assert.equal(row.business_code, expected, method + ' ' + path + '返回非预期业务码')
  if (expected === 200) assert.equal(response.status, 200)
  return json.data
}
async function command(key, actor, path, method, body, expected = 200) {
  if (state.commands[key]?.complete) return clone(state.commands[key].result)
  assert.ok(!state.commands[key], '命令' + key + '先前结果未知，必须先核对业务对象，禁止重放')
  state.commands[key] = { actor, path, method, body: body ?? null, expected, attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist()
  return result
}
function pass(id, evidence) { run.checks.push({ id, result: 'PASS', evidence }); persist(); console.log(JSON.stringify({ case: id, result: 'PASS' })) }
const req = id => '/api/procurement/requisition/' + id
function sameSnapshot(before, after) {
  for (const key of ['workflowBusinessKey','workflowModelVersionId','approvalAttempt','requesterUserId','requesterUnitId','primaryCategoryCode','totalAmount']) assert.equal(after[key], before[key], '原启动快照不能变化：' + key)
}
async function candidateUser() {
  const row = await api('seedAdmin', '/api/auth/user/217')
  assert.equal(row.id, 217); assert.equal(row.tenantId, 1); assert.equal(row.username, 'goal_20261022_procmanager'); assert.equal(row.primaryUnitId, 606)
  assert.deepEqual((await api('seedAdmin', '/api/auth/user/217/roles')).map(Number), [31])
  assert.equal(local.state.accounts.procManager.id, 217); assert.ok(local.state.accounts.procManager.create_attempted)
  return row
}
async function restoreCandidate() {
  if (!state.candidate?.disable_intent || state.candidate.restored) return
  const current = await candidateUser()
  assert.ok([0,1].includes(current.status))
  if (current.status === 0) await api('seedAdmin', '/api/auth/user/217/status?status=1', 'PUT')
  const restored = await candidateUser(); assert.equal(restored.status, 1)
  state.candidate.restored = true; state.candidate.restored_at = new Date().toISOString(); delete tokens.procManager; persist()
}
try {
  if (mode === 'prepare-failed') {
    assert.ok(!state.completed['failed-staged'], '已完成失败阶段，不能重复禁用候选人或重交两张单')
    const original = await api('procStaff', req(4)); assert.equal(original.status, 'APPROVED'); assert.equal(original.requesterUserId, 215)
    const materialId = original.lines[0].materialId
    const preview = await api('procScopeManager', '/api/procurement/approval-route/match-preview', 'POST', { categoryCode: original.primaryCategoryCode, totalAmount: '10.0000' })
    assert.equal(preview.outcome, 'MATCHED')
    const version = await api('procManager', '/api/workflow/model/version/' + preview.modelVersionId)
    assert.equal(version.status, 'PUBLISHED')
    assert.equal((version.bpmnXml.match(/<(?:\w+:)?userTask\b/g) ?? []).length, 1)
    for (const required of ['PROCUREMENT_MANAGER','START_USER_PRIMARY_UNIT','scopedRoleAssignmentListener','candidateUserIds','"fallbackStrategy":"ERROR"']) assert.ok(version.bpmnXml.includes(required), '既有定义不满足受控场景前置：' + required)
    state.prerequisites = { materialId, categoryCode: original.primaryCategoryCode, routeId: preview.routeId, modelVersionId: preview.modelVersionId, bpmn_sha256: sha(version.bpmnXml), model_reused_without_change: true }; persist()
    const todo = await api('procManager', '/api/workflow/task/todo?page=1&size=100')
    assert.equal(Number(todo.total), 0, '专用候选人存在待办时不进行状态切换')
    for (const label of ['retry','cancel']) {
      if (state.objects[label]) continue
      const title = 'Goal 20261022 procurement recovery ' + label
      const existing = await api('procStaff', '/api/procurement/requisition/list?page=1&size=100&keyword=' + encodeURIComponent(title))
      assert.equal(existing.records.length, 0, '同自然键已有对象，必须先核对归属，不能自动接管或重建')
      state.objects[label] = await command('create-' + label, 'procStaff', '/api/procurement/requisition', 'POST', { title, reason: '本Goal专用启动失败与恢复验证，保留真实流程和审计', lines: [{ materialId, quantity: '1.000000', estimatedUnitPrice: '10.000000', remark: '专用虚构业务样本' }] }); persist()
    }
    const candidate = await candidateUser(); assert.equal(candidate.status, 1)
    state.candidate = { user_id: 217, username: candidate.username, role_ids: [31], unit_id: 606, original_status: 1, disable_intent: true, restored: false, at: new Date().toISOString() }; persist()
    try {
      await api('seedAdmin', '/api/auth/user/217/status?status=0', 'PUT')
      assert.equal((await candidateUser()).status, 0)
      for (const label of ['retry','cancel']) {
        const current = await api('procStaff', req(state.objects[label].id)); assert.equal(current.status, 'DRAFT')
        await command('submit-failed-' + label, 'procStaff', req(current.id) + '/submit', 'POST', { version: current.version }, 503)
        const failed = await api('procStaff', req(current.id))
        assert.equal(failed.status, 'SUBMITTED'); assert.equal(failed.workflowStartStatus, 'FAILED'); assert.equal(failed.approvalAttempt, 1)
        assert.equal(failed.workflowBusinessKey, failed.id + ':1'); assert.equal(failed.workflowModelVersionId, preview.modelVersionId)
        state.objects[label] = failed; persist()
        pass('real-candidate-unavailable-start-failed-' + label, { requisition_id: failed.id, business_key: failed.workflowBusinessKey, model_version_id: failed.workflowModelVersionId, approval_attempt: failed.approvalAttempt, status: failed.status, start_status: failed.workflowStartStatus, version: failed.version })
      }
    } finally { await restoreCandidate() }
    state.completed['failed-staged'] = { at: new Date().toISOString(), attempt }; persist()
    run.result = 'PASS_TWO_REAL_FAILED_STARTS_CANDIDATE_RESTORED_PENDING_ACTUAL_CAPTURE_AND_RECOVERY'
  } else {
    assert.ok(state.completed['failed-staged']); assert.ok(state.candidate.restored)
    assert.equal((await candidateUser()).status, 1)
    const before = await api('procStaff', req(state.objects.retry.id)); assert.equal(before.status, 'SUBMITTED'); assert.equal(before.workflowStartStatus, 'FAILED')
    const cancelledBefore = await api('procStaff', req(state.objects.cancel.id)); assert.equal(cancelledBefore.workflowStartStatus, 'FAILED')
    await api('procPeer', req(before.id) + '/retry-start', 'POST', { version: before.version }, 404)
    await api('employee', req(before.id) + '/retry-start', 'POST', { version: before.version }, 403)
    await api('procStaff', req(before.id) + '/retry-start', 'POST', { version: before.version + 1 }, 409)
    sameSnapshot(before, await api('procStaff', req(before.id)))
    const started = await command('retry-real-start', 'procStaff', req(before.id) + '/retry-start', 'POST', { version: before.version })
    sameSnapshot(before, started); assert.equal(started.status, 'APPROVING'); assert.equal(started.workflowStartStatus, 'STARTED'); assert.ok(started.processInstanceId)
    state.objects.retry = started; persist()
    await api('procStaff', req(started.id) + '/retry-start', 'POST', { version: started.version }, 409)
    await api('procStaff', req(started.id) + '/cancel', 'POST', { version: started.version }, 409)
    const tasks = await api('procManager', '/api/workflow/task/todo?page=1&size=100')
    const matching = tasks.records.filter(row => row.processInstanceId === started.processInstanceId)
    assert.equal(matching.length, 1); assert.equal(Number(matching[0].assigneeId), 217)
    const taskId = matching[0].taskId
    await api('procScopeManager', req(started.id) + '/approval-view?taskId=' + encodeURIComponent(taskId), 'GET', undefined, 403)
    const view = await api('procManager', req(started.id) + '/approval-view?taskId=' + encodeURIComponent(taskId))
    assert.equal(view.requisition.id, started.id)
    await command('approve-retried-request', 'procManager', '/api/workflow/approval/' + encodeURIComponent(taskId) + '/complete', 'POST', { approved: true, comment: '本Goal真实启动失败后同轮次恢复审批', variables: {} })
    let final
    for (let count=0; count<60; count++) { final = await api('procStaff', req(started.id)); if (final.status === 'APPROVED') break; await new Promise(resolve => setTimeout(resolve, 250)) }
    assert.equal(final.status, 'APPROVED'); sameSnapshot(before, final); assert.equal(final.processInstanceId, started.processInstanceId)
    state.objects.retry = final; persist()
    pass('same-snapshot-retry-real-assignee-approval-and-outbox-final-approved', { requisition_id: final.id, approval_attempt: final.approvalAttempt, business_key: final.workflowBusinessKey, process_instance_id: final.processInstanceId, task_id: taskId, approver: 217 })
    await api('procPeer', req(cancelledBefore.id) + '/cancel', 'POST', { version: cancelledBefore.version }, 404)
    await api('procStaff', req(cancelledBefore.id) + '/cancel', 'POST', { version: cancelledBefore.version + 1 }, 409)
    const cancelled = await command('cancel-real-failed-request', 'procStaff', req(cancelledBefore.id) + '/cancel', 'POST', { version: cancelledBefore.version })
    assert.equal(cancelled.status, 'CANCELLED'); sameSnapshot(cancelledBefore, cancelled)
    await api('procStaff', req(cancelled.id) + '/retry-start', 'POST', { version: cancelled.version }, 409)
    state.objects.cancel = await api('procStaff', req(cancelled.id)); assert.equal(state.objects.cancel.status, 'CANCELLED'); persist()
    pass('real-failed-submission-cancel-and-terminal-retry-rejection', { requisition_id: cancelled.id, business_key: cancelled.workflowBusinessKey, approval_attempt: cancelled.approvalAttempt, status: cancelled.status })
    state.completed.closed = { at: new Date().toISOString(), attempt }; persist()
    run.result = 'PASS_REAL_START_FAILURE_SAME_ROUND_RETRY_APPROVAL_CANCEL_AND_BOUNDARIES_NOT_FULL_PROCUREMENT'
  }
  run.finished_at = new Date().toISOString(); run.raw_log_sha256 = sha(readFileSync(raw)); persist()
  console.log(JSON.stringify({ result: run.result, checks: run.checks.length, requests: run.requests.length, candidate_restored: state.candidate.restored }))
} catch (error) { run.result = 'FAIL_REAL_PROCUREMENT_RECOVERY_PRESERVE_OBJECTS'; run.error = error.message; persist(); throw error }
finally { try { await restoreCandidate() } finally { writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) } }
