import assert from 'node:assert/strict'
import { writeFileSync } from 'node:fs'
import { createLocalContext } from './goal-local-identities.mjs'

// 本 Goal 专用供应商前置：正式邀请、入驻 Saga、单角色与真实待办；不直接写关联或业务状态。
const context = createLocalContext()
const { state, save, api, login, bootstrap, record } = context
const evidence = '/validation/evidence/codex-2026-10-22/wp07-s03'
state.portal_prerequisite ??= { request_id: 'goal_20261022_supplier_enrollment',
  credit_code: '913100002026102224', name: 'Goal 20261022 supplier', cases: [] }
const portal = state.portal_prerequisite
const checkpoint = (item) => {
  portal.cases.push({ at: new Date().toISOString(), ...item })
  save()
  writeFileSync(`${evidence}/portal-prerequisite.json`, JSON.stringify({ at: new Date().toISOString(),
    namespace: state.namespace, identity_source: 'test-fixture', request_id: portal.request_id,
    supplier_id: portal.supplier_id, invite_id: portal.invite?.id, process_instance_id: portal.process_instance_id,
    cases: portal.cases, approvals: portal.approvals || [],
  }, null, 2) + '\n')
}
save()

async function prepare() {
  const manager = await login('srmAdmin')
  const supplier = await login('supplier')
  await api('/api/srm/portal/profile', { token: manager, expected: 403 })
  checkpoint({ case: 'internal-manager-cannot-masquerade-as-supplier', result: 'PASS' })
  let enrollment = await api('/api/srm/portal/enrollment', { token: supplier })
  if (enrollment) assert.equal(enrollment.requestId, portal.request_id, '不接管其他入驻请求')
  if (!enrollment) {
    if (!portal.invite) {
      assert.ok(!portal.invite_creation_uncertain, '邀请原令牌可能在超时中丢失，须先核对及撤销原邀请，禁止盲建')
      portal.invite_creation_uncertain = true
      save()
      portal.invite = await api('/api/srm/portal/invite', { token: manager, method: 'POST', body: { maxUses: 1, expiresHours: 24 } })
      assert.ok(portal.invite.id && portal.invite.inviteToken)
      portal.invite_creation_uncertain = false
      save()
      checkpoint({ case: 'formal-single-use-invitation-created', invite_id: portal.invite.id, result: 'PASS' })
    }
    portal.enrollment_requested = true
    save()
    enrollment = await api('/api/srm/portal/enroll', { token: supplier, method: 'POST', body: {
      requestId: portal.request_id, inviteToken: portal.invite.inviteToken, name: portal.name,
      creditCode: portal.credit_code, supplierType: 'OTHER',
    } })
    portal.supplier_id = enrollment.supplierId
    save()
    checkpoint({ case: 'formal-enrollment-accepted', enrollment_status: enrollment.status, result: 'PASS' })
  }
  portal.supplier_id = enrollment.supplierId
  for (let attempt = 0; enrollment.status !== 'COMPLETED' && attempt < 120; attempt++) {
    assert.notEqual(enrollment.status, 'ROLE_ASSIGN_FAILED', '角色 Saga 失败，须读真实错误和退避恢复入口，不改库状态')
    await new Promise((resolve) => setTimeout(resolve, 1000))
    enrollment = await api('/api/srm/portal/enrollment', { token: supplier })
    portal.enrollment_status = enrollment.status
    save()
  }
  assert.equal(enrollment.status, 'COMPLETED', 'Saga 未完成，不能把请求受理当成 PortalUser ACTIVE')
  checkpoint({ case: 'real-role-saga-completed', enrollment_status: enrollment.status, result: 'PASS' })

  if (!portal.idempotency_verified) {
    assert.ok(state.accounts.supplier.actual_roles.includes('USER'), '缺原 enroll 权限，不能伪造请求幂等证据')
    const replay = await api('/api/srm/portal/enroll', { token: supplier, method: 'POST', body: {
      requestId: portal.request_id, inviteToken: portal.invite.inviteToken, name: portal.name, creditCode: portal.credit_code,
    } })
    assert.equal(replay.supplierId, portal.supplier_id)
    assert.equal(replay.requestId, portal.request_id)
    const invites = await api('/api/srm/portal/invite/list', { token: manager })
    const invite = invites.find((item) => Number(item.id) === Number(portal.invite.id))
    assert.equal(invite.usedCount, 1)
    portal.idempotency_verified = true
    checkpoint({ case: 'same-request-reuses-supplier-and-single-invite-consumption', supplier_id: replay.supplierId, used_count: invite.usedCount, result: 'PASS' })
  }
  const admin = bootstrap()
  const roles = await api('/api/auth/role/all', { token: admin })
  const role = roles.find((item) => item.roleCode === 'SUPPLIER')
  assert.ok(role?.id)
  const user = state.accounts.supplier
  const before = await api(`/api/auth/user/${user.id}/roles`, { token: admin })
  assert.ok(before.includes(role.id), '必须由正式 Saga 真实授予 SUPPLIER，不能由夹具替代关联/角色结果')
  if (before.length !== 1) await api(`/api/auth/user/${user.id}/roles`, { token: admin, method: 'POST', body: [role.id] })
  const after = await api(`/api/auth/user/${user.id}/roles`, { token: admin })
  assert.deepEqual(after, [role.id])
  user.role_ids = after
  user.portal_enrolled = true
  save()
  const pureSupplier = await login('supplier')
  assert.deepEqual(user.actual_roles, ['SUPPLIER'])
  const profile = await api('/api/srm/portal/profile', { token: pureSupplier })
  assert.equal(Number(profile.supplierId), Number(portal.supplier_id))
  const evaluations = await api('/api/srm/portal/evaluations?page=1&size=10', { token: pureSupplier })
  await api('/api/auth/user/list?page=1&size=10', { token: pureSupplier, expected: 403 })
  await api('/api/srm/supplier/list?page=1&size=10', { token: pureSupplier, expected: 403 })
  checkpoint({ case: 'pure-supplier-active-association-and-internal-management-denial', supplier_id: profile.supplierId,
    supplier_status: profile.status, evaluation_count: evaluations.total, role_ids: after, result: 'PASS' })
  const detail = await api(`/api/srm/supplier/${portal.supplier_id}`, { token: manager })
  assert.ok(Object.hasOwn(detail, 'ownerUserId') && Object.hasOwn(detail, 'ownerUnitId'), 'API 未暴露 owner 快照时必须另取只读行证据，不能用 undefined 冒充空值')
  assert.ok(detail.ownerUserId == null && detail.ownerUnitId == null, '门户用户 ID 不得写入内部 owner 字段')
  portal.process_instance_id = detail.processInstanceId
  portal.supplier_status = detail.status
  save()
  checkpoint({ case: 'portal-does-not-forge-internal-owner', workflow_start_status: detail.workflowStartStatus,
    supplier_status: detail.status, result: 'PASS' })
  context.publish()
  console.log(JSON.stringify({ result: 'PORTAL_IDENTITY_PREPARED', supplier_id: portal.supplier_id,
    supplier_status: detail.status, process_instance_id: detail.processInstanceId, identity_source: 'test-fixture' }))
}

async function approve() {
  assert.ok(portal.process_instance_id, '先完成真实入驻与工作流启动')
  portal.approvals ??= []
  for (const alias of ['srmManager', 'srmCompliance', 'srmDirector']) {
    const token = await login(alias)
    const tasks = await api('/api/workflow/task/todo?page=1&size=100', { token })
    const task = tasks.records.find((item) => item.processInstanceId === portal.process_instance_id)
    if (!task) {
      assert.ok(portal.approvals.some((item) => item.alias === alias && item.result === 'PASS'), `${alias} 没有该流程的真实待办或已完成证据`)
      continue
    }
    const taskId = task.taskId
    assert.ok(taskId)
    const account = state.accounts[alias]
    assert.equal(String(task.assigneeId), String(account.id), '不得模拟其他候选人')
    portal.current_approval = { alias, user_id: account.id, task_id: taskId,
      actual_roles: account.actual_roles, actual_permissions: account.actual_permissions }
    save()
    checkpoint({ case: 'actual-scoped-candidate-has-assigned-task', alias, user_id: account.id, task_id: taskId, result: 'PASS' })
    if (alias === 'srmManager' && !portal.wrong_assignee_rejected) {
      const outsider = await login('srmDirector')
      assert.ok(state.accounts.srmDirector.actual_permissions.includes('workflow:approval:complete'),
        '负例必须具有端点权限，才能验证实际任务归属检查')
      await api(`/api/workflow/approval/${encodeURIComponent(taskId)}/complete`, { token: outsider,
        method: 'POST', expected: 403, body: { approved: true, comment: 'Goal wrong-assignee negative case', variables: {} } })
      const unchanged = await api('/api/workflow/task/todo?page=1&size=100', { token })
      assert.ok(unchanged.records.some((item) => item.taskId === taskId && String(item.assigneeId) === String(account.id)),
        '越权失败后任务必须仍属于原处理人')
      portal.wrong_assignee_rejected = true
      checkpoint({ case: 'permission-does-not-override-task-assignee',
        attempted_user_id: state.accounts.srmDirector.id, assigned_user_id: account.id, task_id: taskId, result: 'PASS' })
    }
    await api(`/api/workflow/approval/${encodeURIComponent(taskId)}/complete`, { token, method: 'POST',
      body: { approved: true, comment: 'Goal 20261022 automated fixture approval', variables: {} } })
    portal.approvals.push({ alias, user_id: account.id, task_id: taskId, result: 'PASS', at: new Date().toISOString() })
    record({ object: 'workflow-approval', alias, task_id: taskId, result: 'PASS' })
    checkpoint({ case: 'formal-approval-completed-by-scoped-candidate', alias, task_id: taskId, result: 'PASS' })
  }
  const manager = await login('srmAdmin')
  let detail
  for (let attempt = 0; attempt < 120; attempt++) {
    detail = await api(`/api/srm/supplier/${portal.supplier_id}`, { token: manager })
    if (detail.status === 'APPROVED') break
    await new Promise((resolve) => setTimeout(resolve, 1000))
  }
  assert.equal(detail.status, 'APPROVED', '审批完成通知与供应商最终状态必须分别取证')
  const supplier = await login('supplier')
  const invitations = await api('/api/srm/portal/quotation/invitations', { token: supplier })
  assert.ok(Array.isArray(invitations))
  portal.supplier_status = detail.status
  checkpoint({ case: 'real-approval-to-supplier-state-and-quotation-readiness', supplier_status: detail.status, invitation_count: invitations.length, result: 'PASS' })
  context.publish()
  console.log(JSON.stringify({ result: 'PORTAL_APPROVAL_CHAIN_PASS', supplier_id: portal.supplier_id, approvals: portal.approvals.length }))
}

if (process.argv[2] === 'prepare') await prepare()
else if (process.argv[2] === 'approve') await approve()
else throw new Error('只接受 prepare 或 approve；不执行任意业务命令')
