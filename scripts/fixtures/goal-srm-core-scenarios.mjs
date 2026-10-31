import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP26S02派生业务矩阵：固定专用对象、正式接口和持久断点；不新增计划步骤或改库状态。
const local = createLocalContext()
assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2)
assert.ok(['resources', 'evaluations', 'risk-config', 'risk', 'risk-preview', 'risk-recovery'].includes(mode))
assert.match(attempt ?? '', new RegExp(`^srm-${mode}-r[1-9][0-9]*$`))
assert.match(process.env.E2E_SRM_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const portal = JSON.parse(readFileSync(`${privateRoot}/srm-portal-scenarios.json`, 'utf8'))
assert.equal(portal.namespace, 'GOAL20261022SRM')
assert.ok(portal.completed['approve-admission'])
assert.equal(Number(portal.supplier_id), 10)
const file = `${privateRoot}/srm-core-scenarios.json`
const state = existsSync(file) ? JSON.parse(readFileSync(file, 'utf8'))
  : { namespace: 'GOAL20261022SRM', supplier_id: portal.supplier_id, objects: {}, commands: {}, completed: {}, runs: [] }
assert.equal(state.namespace, portal.namespace)
assert.equal(state.supplier_id, portal.supplier_id)
const output = `/validation/evidence/codex-2026-10-22/wp26-s02/${attempt}`
assert.ok(!existsSync(output), '不覆盖任何旧尝试或原始证据')
mkdirSync(output, { recursive: true })
const raw = `${privateRoot}/${attempt}-wire.ndjson`
writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const run = { original_step: 'WP-26.S02', mode, attempt, namespace: state.namespace, supplier_id: state.supplier_id,
  started_at: new Date().toISOString(), identity_source: 'test-fixture', srm_image_id: process.env.E2E_SRM_IMAGE_ID,
  source_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'), cases: [], requests: [] }
state.runs.push(run)
const clone = (value) => JSON.parse(JSON.stringify(value))
let currentCase
const persist = () => {
  writeFileSync(file, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 })
  writeFileSync(`${output}/result.json`, JSON.stringify({ ...run, objects: state.objects, original_raw_log: raw }, null, 2) + '\n')
}
const tokens = {}
async function token(actor) { return tokens[actor] ??= await local.login(actor) }
async function api(actor, path, method = 'GET', body, expected = 200) {
  assert.ok(path.startsWith('/api/srm/'), '本矩阵只访问正式SRM网关接口')
  const entry = { case_id: currentCase, actor, user_id: local.state.accounts[actor].id, method, path, body,
    at: new Date().toISOString() }
  run.requests.push(entry); persist()
  let response
  try {
    response = await fetch(`http://omni-gateway:8080${path}`, { method, headers: { Authorization: `Bearer ${await token(actor)}`,
      ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
  } catch {
    entry.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist()
    throw new Error(`${method} ${path}结果未知，必须读回对象/历史，不自动重发`)
  }
  const json = await response.json()
  appendFileSync(raw, JSON.stringify({ ...entry, http_status: response.status, response: json }) + '\n')
  entry.http_status = response.status; entry.response = json; persist()
  assert.ok((Array.isArray(expected) ? expected : [expected]).includes(json.code ?? response.status),
    `${method} ${path}实际业务码${json.code ?? response.status}与预期${expected}不符`)
  return json.data
}
async function command(key, actor, path, method, body, expected = 200) {
  // 新镜像必须重新实测风险写入；同一镜像重试仍使用固定键，禁止重复发送已确认的命令。
  if (mode === 'risk') key = `${run.srm_image_id}:${key}`
  const previous = state.commands[key]
  if (previous?.complete) return clone(previous.result)
  assert.ok(!previous, `命令${key}结果未确定，禁止重复写入`)
  state.commands[key] = { actor, method, path, body: clone(body ?? null), expected, attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body, expected)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist()
  return result
}
async function check(id, action) {
  currentCase = id
  const previous = state.completed[id]
  if (previous) {
    if (mode === 'risk' && id.startsWith('RISK-') && previous.srm_image_id !== run.srm_image_id) {
      state.revalidation_history ??= {}
      state.revalidation_history[id] ??= []
      if (!state.revalidation_history[id].some((row) => row.at === previous.at)) state.revalidation_history[id].push(clone(previous))
      run.revalidation_decisions ??= []
      run.revalidation_decisions.push({ id, result: 'RERUN_REQUIRED_NEW_RUNTIME_IMAGE', previous_evidence: clone(previous) }); persist()
    } else {
      assert.equal(previous.srm_image_id, run.srm_image_id, '镜像改变后不能自动继承旧业务结论')
      run.cases.push({ id, result: 'REUSE_PREVIOUS_ACTUAL_PASS', evidence: clone(previous) }); persist(); return
    }
  }
  const start = run.requests.length
  try {
    const evidence = await action()
    const result = { id, result: 'PASS', attempt, srm_image_id: run.srm_image_id, source_sha256: run.source_sha256,
      at: new Date().toISOString(), request_indexes: [start, run.requests.length - 1], evidence }
    state.completed[id] = clone(result); run.cases.push(result); persist()
    console.log(JSON.stringify({ case: id, result: 'PASS', requests: run.requests.length - start }))
  } catch (error) { run.cases.push({ id, result: 'FAIL', error: error.message }); persist(); throw error }
}
const root = `/api/srm/supplier/${state.supplier_id}`
const detail = (actor = 'srmAdmin') => api(actor, root)
const rows = (type, actor = 'procStaff') => api(actor, `${root}/${type}`)
const child = (type, id, actor = 'procStaff') => api(actor, `${root}/${type}/${id}`)
async function make(key, type, body) {
  const row = await command(`create-${key}`, 'procStaff', `${root}/${type}`, 'POST', body)
  assert.ok(Number(row.id) > 0)
  state.objects[key] = { id: row.id, type, supplier_id: state.supplier_id, created_attempt: attempt }; persist()
  return row
}
async function edit(key, type, id, fields, actor = 'procStaff') {
  const before = await child(type, id, actor)
  return command(key, actor, `${root}/${type}/${id}`, 'PUT', { version: before.version, ...fields })
}
async function remove(key, type, id) {
  const before = await child(type, id)
  await command(key, 'procStaff', `${root}/${type}/${id}?version=${before.version}`, 'DELETE')
  await api('procStaff', `${root}/${type}/${id}`, 'GET', undefined, 404)
}
const evaluationBody = (template, score, period) => ({ supplierId: state.supplier_id, evaluationPeriod: period,
  items: template.dimensions.map((dimension) => ({ dimensionId: dimension.id, score, remark: '本Goal实际虚构评分样本' })) })
const indicators = () => api('procStaff', `/api/srm/risk/indicators/${state.supplier_id}`)
async function indicatorCriterion(key, type, level) {
  const row = (await indicators()).find((item) => item.indicatorType === type)
  assert.ok(row && row.autoCalc === 0)
  const criterion = row.criteria.find((item) => item.riskLevel === level)
  assert.ok(criterion, `${type}缺少真实${level}评分标准，不创建假标准取得结果`)
  return command(key, 'procStaff', `/api/srm/risk/indicator/${row.id}`, 'PUT', { version: row.version, criterionId: criterion.id })
}
async function riskSnapshot(expectedLevel) {
  const profile = await api('procStaff', `${root}/risk`)
  assert.ok(profile.latestAssessment?.id)
  const sum = profile.indicators.filter((row) => row.score != null).reduce((total, row) => total + Number(row.score), 0)
  const thresholds = await api('procStaff', '/api/srm/risk/config/thresholds')
  let derived = thresholds.find((row) => sum >= row.minScore && sum <= row.maxScore)?.riskLevel
  if (!derived) {
    const highest = [...thresholds].sort((a, b) => b.maxScore - a.maxScore)[0]
    derived = highest && sum > highest.maxScore ? highest.riskLevel : 'GREEN'
  }
  assert.equal(profile.latestAssessment.overallLevel, derived)
  if (expectedLevel) assert.equal(profile.latestAssessment.overallLevel, expectedLevel)
  return { assessment_id: profile.latestAssessment.id, sum, overall_level: derived,
    indicators: profile.indicators.map((row) => ({ id: row.id, type: row.indicatorType, level: row.riskLevel, score: row.score, value: row.indicatorValue })), thresholds }
}
try {
  await token('srmAdmin'); await token('procStaff'); await token('procScopeManager'); await token('procPeer')
  assert.equal(Number(local.state.accounts.procStaff.id), 215)
  assert.deepEqual(local.state.accounts.procScopeManager.actual_roles, ['PROCUREMENT_MANAGER'])
  assert.equal(Number(local.state.accounts.procScopeManager.organization_id), 604)
  assert.equal(Number(local.state.accounts.procStaff.organization_id), 606)
  assert.equal(Number(local.state.accounts.procPeer.organization_id), 605)
  if (mode === 'resources') {
    await check('SUP-04-owner-and-scope', async () => {
      let supplier = await detail(); assert.equal(supplier.name, portal.name); assert.equal(supplier.status, 'APPROVED')
      const template = await api('srmAdmin', '/api/srm/evaluation/template/default/dimensions')
      if (!state.commands['assign-internal-owner']?.complete) {
        assert.equal(supplier.ownerUserId, null); assert.equal(supplier.ownerUnitId, null)
        await api('srmAdmin', '/api/srm/evaluation', 'POST', evaluationBody(template, 5, `${state.namespace}-UNOWNED`), 409)
      }
      supplier = await command('assign-internal-owner', 'srmAdmin', `${root}/transfer`, 'POST', { version: supplier.version, ownerUserId: 215 })
      assert.equal(Number(supplier.ownerUserId), 215); assert.equal(Number(supplier.ownerUnitId), 606)
      await detail('procStaff'); await detail('procScopeManager'); await api('procPeer', root, 'GET', undefined, 404)
      const before = await detail()
      await api('procStaff', `${root}/transfer`, 'POST', { version: before.version, ownerUserId: 216 }, 403)
      await api('procScopeManager', `${root}/transfer`, 'POST', { version: before.version, ownerUserId: 216 }, 403)
      await api('procStaff', root, 'PUT', { version: before.version, ownerUserId: 229 }, 400)
      assert.equal((await detail()).version, before.version)
      const updated = await command('update-owned-supplier', 'procStaff', root, 'PUT', { version: before.version, region: '本Goal虚构区域' })
      await api('procStaff', root, 'PUT', { version: before.version, region: 'stale-write' }, 409)
      assert.equal((await detail()).region, updated.region)
      return { supplier_id: state.supplier_id, owner_user_id: 215, owner_unit_id: 606, parent_manager: 228,
        parent_unit_id: 604, peer_user_id: 216, peer_unit_id: 605, forbidden_writes_unchanged: true }
    })
    await check('OPT-01', async () => {
      const query = '?keyword=goal_20261022&limit=100'
      const manager = await api('procScopeManager', `/api/srm/options/owners${query}`)
      assert.ok(manager.some((row) => Number(row.userId) === 215)); assert.ok(manager.some((row) => Number(row.userId) === 228))
      assert.ok(!manager.some((row) => Number(row.userId) === 216))
      const staff = await api('procStaff', `/api/srm/options/owners${query}`)
      assert.ok(staff.some((row) => Number(row.userId) === 215)); assert.ok(!staff.some((row) => Number(row.userId) === 216))
      await api('srmPortal', '/api/srm/options/owners', 'GET', undefined, 403)
      return { manager_ids: manager.map((row) => row.userId), staff_ids: staff.map((row) => row.userId) }
    })
    await check('SUP-01-SUP-02-OV-01', async () => {
      const list = await api('procStaff', `/api/srm/supplier/list?page=1&size=100&name=${encodeURIComponent(portal.name)}&status=APPROVED`)
      assert.ok(list.records.some((row) => row.id === state.supplier_id))
      const empty = await api('procStaff', '/api/srm/supplier/list?name=GOAL-NO-SUCH-SUPPLIER-20261022')
      assert.equal(empty.total, 0)
      await api('procStaff', '/api/srm/supplier/list?page=0', 'GET', undefined, 400)
      const overview = await api('procStaff', `${root}/overview`)
      for (const key of ['contacts', 'qualifications', 'bankAccounts', 'recentEvaluations', 'riskIndicators']) assert.ok(Array.isArray(overview[key]))
      const summary = await api('procScopeManager', '/api/srm/overview/summary')
      const dashboard = await api('procScopeManager', '/api/srm/overview/risk-dashboard')
      assert.ok(summary && dashboard)
      await api('employee', '/api/srm/overview/summary', 'GET', undefined, 403)
      await api('srmPortal', root, 'GET', undefined, 403)
      return { supplier_id: state.supplier_id, overview_sections: 5, empty_result_verified: true, summary, dashboard }
    })
    await check('CON-01', async () => {
      await api('procStaff', `${root}/contact`, 'POST', { name: '' }, 400)
      const a = await make('contact-main', 'contact', { name: 'Goal虚构采购联系人', mobile: '13900000134', email: 'contact@example.invalid', primaryFlag: true })
      const b = await make('contact-delete', 'contact', { name: 'Goal待删除联系人', primaryFlag: true })
      assert.equal((await rows('contact')).filter((row) => row.primaryFlag).length, 1)
      assert.equal((await child('contact', a.id)).primaryFlag, false)
      const before = await child('contact', a.id)
      const updated = await edit('contact-main-update', 'contact', a.id, { primaryFlag: true, department: '本Goal虚构采购部' })
      await api('procStaff', `${root}/contact/${a.id}`, 'PUT', { version: before.version, name: 'stale-write' }, 409)
      assert.equal(updated.primaryFlag, true); assert.equal((await child('contact', b.id)).primaryFlag, false)
      const full = await child('contact', a.id, 'srmAdmin'); const masked = await child('contact', a.id)
      assert.equal(full.mobile, '13900000134'); assert.notEqual(masked.mobile, full.mobile); assert.ok(masked.mobile.includes('*'))
      await child('contact', a.id, 'procScopeManager')
      await api('procPeer', `${root}/contact/${a.id}`, 'GET', undefined, 404)
      await api('srmAdmin', `/api/srm/supplier/9/contact/${a.id}`, 'GET', undefined, 404)
      await remove('contact-delete', 'contact', b.id)
      assert.equal((await api('procStaff', `${root}/contact/list`)).filter((row) => row.primaryFlag).length, 1)
      return { contact_id: a.id, deleted_contact_id: b.id, primary_count: 1, pii_masked_for_staff: true, parent_scope_and_wrong_parent_guard: true }
    })
    await check('BNK-01', async () => {
      await api('procStaff', `${root}/bank-account`, 'POST', { accountName: 'Goal', accountNo: '', bankName: '虚构银行' }, 400)
      const a = await make('bank-main', 'bank-account', { accountName: 'Goal虚构公司', accountNo: '6222000000000000101', bankName: '本Goal虚构银行', primaryFlag: true })
      const b = await make('bank-delete', 'bank-account', { accountName: 'Goal待删除账户', accountNo: '6222000000000000102', bankName: '本Goal虚构银行', primaryFlag: true })
      assert.equal((await rows('bank-account')).filter((row) => row.primaryFlag).length, 1)
      assert.equal((await child('bank-account', a.id)).primaryFlag, false)
      const before = await child('bank-account', a.id)
      await edit('bank-main-update', 'bank-account', a.id, { primaryFlag: true, bankBranch: '本Goal虚构支行' })
      await api('procStaff', `${root}/bank-account/${a.id}`, 'PUT', { version: before.version, bankName: 'stale-write' }, 409)
      const full = await child('bank-account', a.id, 'srmAdmin'); const masked = await child('bank-account', a.id)
      assert.equal(full.accountNo, '6222000000000000101'); assert.notEqual(masked.accountNo, full.accountNo); assert.ok(masked.accountNo.includes('*'))
      await child('bank-account', a.id, 'procScopeManager')
      await api('procPeer', `${root}/bank-account/${a.id}`, 'GET', undefined, 404)
      await remove('bank-delete', 'bank-account', b.id)
      assert.equal((await api('procStaff', `${root}/bank-account/list`)).filter((row) => row.primaryFlag).length, 1)
      return { bank_id: a.id, deleted_bank_id: b.id, primary_count: 1, actual_required_fields: true, no_claim_of_bank_number_algorithm: true }
    })
    await check('QUA-01', async () => {
      await api('procStaff', `${root}/qualification`, 'POST', { qualificationName: '非法日期', issueDate: '2028-01-01', expiryDate: '2027-01-01' }, 400)
      const a = await make('qualification-main', 'qualification', { qualificationName: 'Goal虚构经营资质', certificateNo: 'GOAL-20261022-CERT', issueDate: '2020-01-01', expiryDate: '2028-12-31' })
      const b = await make('qualification-delete', 'qualification', { qualificationName: 'Goal待删除资质', issueDate: '2020-01-01', expiryDate: '2028-12-31' })
      const before = await child('qualification', a.id)
      const updated = await edit('qualification-main-update', 'qualification', a.id, { expiryDate: '2029-12-31', issuingAuthority: '本Goal虚构发证机构' })
      await api('procStaff', `${root}/qualification/${a.id}`, 'PUT', { version: before.version, expiryDate: '2030-12-31' }, 409)
      assert.equal(updated.expiryDate, '2029-12-31')
      await child('qualification', a.id, 'procScopeManager'); await api('procPeer', `${root}/qualification/${a.id}`, 'GET', undefined, 404)
      await remove('qualification-delete', 'qualification', b.id)
      assert.ok((await api('procStaff', `${root}/qualification/list`)).some((row) => row.id === a.id))
      return { qualification_id: a.id, deleted_qualification_id: b.id, date_guard_and_stale_conflict: true }
    })
  } else if (mode === 'evaluations') {
    assert.ok(state.completed['QUA-01'] && state.completed['SUP-04-owner-and-scope'])
    await check('EVA-02', async () => {
      const template = await api('procStaff', '/api/srm/evaluation/template/default/dimensions')
      assert.equal(template.dimensions.length, 4)
      assert.deepEqual(template.dimensions.map((row) => Number(row.weight)).sort((a, b) => a - b), [20, 20, 30, 30])
      state.template = clone(template); persist()
      return { template_id: template.id, dimension_ids: template.dimensions.map((row) => row.id), total_weight: 100 }
    })
    await check('EVA-03-input-boundaries', async () => {
      const valid = evaluationBody(state.template, 5, `${state.namespace}-INVALID`)
      await api('procStaff', '/api/srm/evaluation', 'POST', { ...valid, items: valid.items.slice(1) }, 400)
      await api('procStaff', '/api/srm/evaluation', 'POST', { ...valid, items: [...valid.items, valid.items[0]] }, 400)
      await api('procStaff', '/api/srm/evaluation', 'POST', { ...valid, items: valid.items.map((row, index) => index ? row : { ...row, score: 5.1 }) }, 400)
      await api('procStaff', '/api/srm/evaluation', 'POST', { ...valid, items: valid.items.map((row, index) => index ? row : { ...row, score: 4.55 }) }, 400)
      await api('procPeer', '/api/srm/evaluation', 'POST', valid, 404)
      await api('srmPortal', '/api/srm/evaluation', 'POST', valid, 403)
      return { missing_dimension: 400, duplicate_dimension: 400, invalid_score: 400, invalid_precision: 400, out_of_scope: 404, portal_role_denied: 403 }
    })
    for (const [index, score, total, level] of [[1, 5, 100, 'STRATEGIC'], [2, 4, 80, 'PREFERRED'], [3, 3, 60, 'QUALIFIED'], [4, 1, 20, 'ELIMINATED'], [5, 5, 100, 'STRATEGIC']]) {
      await check(`EVA-03-cycle-${index}`, async () => {
        const period = `${state.namespace}-CYCLE-${index}`
        const row = await command(`evaluation-cycle-${index}`, 'procStaff', '/api/srm/evaluation', 'POST', evaluationBody(state.template, score, period))
        assert.ok(row.id); assert.equal(Number(row.totalScore), total); assert.equal(row.status, 'COMPLETED'); assert.equal(row.items.length, 4)
        assert.equal(Number(row.ownerUserId), 215); assert.equal(Number(row.ownerUnitId), 606)
        const supplier = await detail(); assert.equal(supplier.levelCode, level); assert.equal(supplier.status, 'APPROVED')
        state.objects[`evaluation-${index}`] = { id: row.id, supplier_id: state.supplier_id, evaluationPeriod: period, totalScore: total, levelCode: level }; persist()
        return { evaluation_id: row.id, score, total, grade: level, lifecycle_still: supplier.status }
      })
    }
    await check('EVA-01-POR-04-history-and-owner-sync', async () => {
      const ids = [1, 2, 3, 4, 5].map((index) => state.objects[`evaluation-${index}`].id)
      const history = await api('procStaff', `/api/srm/evaluation/supplier/${state.supplier_id}`)
      assert.ok(ids.every((id) => history.some((row) => row.id === id))); assert.equal(new Set(ids).size, 5)
      for (const id of ids) { const row = await api('procScopeManager', `/api/srm/evaluation/${id}`); assert.equal(row.items.length, 4) }
      assert.ok((await api('procStaff', `/api/srm/evaluation/list?supplierId=${state.supplier_id}&page=1&size=100`)).records.every((row) => row.supplierId === state.supplier_id))
      assert.ok(ids.every((id) => history.some((row) => row.id === id)))
      const aliasHistory = await api('procScopeManager', `${root}/evaluation/history`); assert.equal(aliasHistory.length, history.length)
      const portalHistory = await api('srmPortal', '/api/srm/portal/evaluations/history')
      assert.ok(ids.every((id) => portalHistory.some((row) => row.id === id)))
      const portalList = await api('srmPortal', '/api/srm/portal/evaluations?page=1&size=100')
      assert.ok(ids.every((id) => portalList.records.some((row) => row.id === id)))
      const other = await api('supplier', '/api/srm/portal/evaluations/history')
      assert.ok(!other.some((row) => ids.includes(row.id)))
      await api('srmAdmin', '/api/srm/portal/evaluations', 'GET', undefined, 403)
      await api('procPeer', `/api/srm/evaluation/${ids[0]}`, 'GET', undefined, 404)
      const before = await detail()
      await command('transfer-with-existing-evaluations-to-parent', 'procScopeManager', `${root}/transfer`, 'POST', { version: before.version, ownerUserId: 228 })
      await api('procStaff', root, 'GET', undefined, 404)
      await api('procStaff', `/api/srm/evaluation/${ids[0]}`, 'GET', undefined, 404)
      for (const id of ids) { const row = await api('procScopeManager', `/api/srm/evaluation/${id}`); assert.equal(Number(row.ownerUserId), 228); assert.equal(Number(row.ownerUnitId), 604) }
      const parentOwned = await detail()
      await command('restore-owner-after-evaluation-scope-check', 'procScopeManager', `${root}/transfer`, 'POST', { version: parentOwned.version, ownerUserId: 215 })
      for (const id of ids) { const row = await api('procStaff', `/api/srm/evaluation/${id}`); assert.equal(Number(row.ownerUserId), 215); assert.equal(Number(row.ownerUnitId), 606) }
      return { evaluation_ids: ids, periods_append_not_overwrite: true, portal_supplier_id: state.supplier_id, other_portal_supplier_id: 9,
        owner_transfer_updates_all_history: true, restored_owner: 215, grade_not_lifecycle: true }
    })
  } else if (mode === 'risk-config') {
    await check('RCFG-01-dedicated-config-crud', async () => {
      await api('srmAdmin', '/api/srm/risk/config/types', 'POST', { typeCode: '', typeName: '无效类型' }, 400)
      const type = await command('risk-config-create-disabled-type', 'srmAdmin', '/api/srm/risk/config/types', 'POST', {
        typeCode: 'GOAL20261022_QA', typeName: '本Goal专用关闭的风险配置', autoCalc: 0, status: 0, sort: 999 })
      state.objects['risk-config-type'] = { id: type.id, typeCode: type.typeCode, status: type.status }; persist()
      assert.equal(type.status, 0)
      await api('srmAdmin', '/api/srm/risk/config/types', 'POST', { typeCode: type.typeCode, typeName: '重复编码', status: 0 }, 400)
      const edited = await command('risk-config-update-disabled-type', 'srmAdmin', `/api/srm/risk/config/types/${type.id}`, 'PUT', {
        typeName: '本Goal专用配置已更新', description: '保持禁用，不影响其他供应商的指标初始化', sort: 998, status: 0 })
      assert.equal(edited.status, 0); assert.equal(edited.typeName, '本Goal专用配置已更新')
      const criterion = await command('risk-config-create-disabled-criterion', 'srmAdmin', '/api/srm/risk/config/criteria', 'POST', {
        indicatorTypeId: type.id, criterionLabel: '本Goal专用评分标准', score: 1, riskLevel: 'GREEN', status: 0, sort: 1 })
      state.objects['risk-config-criterion'] = { id: criterion.id, indicatorTypeId: type.id, status: 0 }; persist()
      const updated = await command('risk-config-update-disabled-criterion', 'srmAdmin', `/api/srm/risk/config/criteria/${criterion.id}`, 'PUT', {
        criterionLabel: '本Goal专用评分标准已更新', score: 2, riskLevel: 'YELLOW', status: 0, sort: 2 })
      assert.equal(updated.score, 2); assert.equal(updated.riskLevel, 'YELLOW'); assert.equal(updated.status, 0)
      await api('srmAdmin', `/api/srm/risk/config/types/${type.id}`, 'DELETE', undefined, 400)
      await command('risk-config-delete-own-criterion', 'srmAdmin', `/api/srm/risk/config/criteria/${criterion.id}`, 'DELETE')
      await api('srmAdmin', `/api/srm/risk/config/criteria/${criterion.id}`, 'PUT', { criterionLabel: 'deleted' }, 404)
      await command('risk-config-delete-own-type', 'srmAdmin', `/api/srm/risk/config/types/${type.id}`, 'DELETE')
      await api('srmAdmin', `/api/srm/risk/config/types/${type.id}`, 'PUT', { typeName: 'deleted' }, 404)
      const types = await api('procStaff', '/api/srm/risk/config/types')
      assert.ok(!types.some((row) => row.typeCode === type.typeCode))
      await api('srmPortal', '/api/srm/risk/config/types', 'GET', undefined, 403)
      return { dedicated_type_id: type.id, dedicated_criterion_id: criterion.id, never_enabled: true, both_formally_deleted: true }
    })
    await check('RCFG-01-threshold-semantics-preserved', async () => {
      const thresholds = await api('srmAdmin', '/api/srm/risk/config/thresholds')
      const values = thresholds.map(({ riskLevel, minScore, maxScore }) => ({ riskLevel, minScore, maxScore }))
      state.thresholds_before = clone(thresholds); state.threshold_values = clone(values); persist()
      await api('srmAdmin', '/api/srm/risk/config/thresholds', 'PUT', [], 400)
      assert.deepEqual(await api('srmAdmin', '/api/srm/risk/config/thresholds'), thresholds)
      const saved = await command('risk-threshold-save-same-values', 'srmAdmin', '/api/srm/risk/config/thresholds', 'PUT', values)
      assert.deepEqual(saved.map(({ riskLevel, minScore, maxScore }) => ({ riskLevel, minScore, maxScore })), values)
      state.thresholds_after = clone(saved); persist()
      return { before: thresholds, after: saved, semantics_unchanged: true,
        implementation: '正式接口删除再插入，ID变化如实保留；没有修改评分区间或声称恢复旧物理ID。' }
    })
  } else if (mode === 'risk') {
    assert.ok(state.completed['QUA-01'])
    await check('RISK-01-indicator-and-scope', async () => {
      const all = await indicators(); assert.equal(all.length, 6)
      assert.equal(all.filter((row) => row.autoCalc === 0).length, 5)
      const certificate = all.find((row) => row.indicatorType === 'CERTIFICATE')
      assert.equal(certificate.autoCalc, 1)
      await api('procStaff', `/api/srm/risk/indicator/${certificate.id}`, 'PUT', { version: certificate.version, riskLevel: 'GREEN' }, 400)
      const manual = all.find((row) => row.autoCalc === 0)
      await api('procStaff', `/api/srm/risk/indicator/${manual.id}`, 'PUT', { version: manual.version, criterionId: 9000000020261022 }, 400)
      await api('procPeer', `/api/srm/risk/indicators/${state.supplier_id}`, 'GET', undefined, 404)
      await api('procPeer', `/api/srm/risk/indicator/${manual.id}`, 'PUT', { version: manual.version, criterionId: manual.criteria[0].id }, 404)
      await api('srmPortal', `/api/srm/risk/indicators/${state.supplier_id}`, 'GET', undefined, 403)
      state.risk_manual_types = all.filter((row) => row.autoCalc === 0).map((row) => row.indicatorType); persist()
      return { indicator_ids: all.map((row) => row.id), manual_types: state.risk_manual_types, automatic_manual_edit_rejected: true }
    })
    await check('RISK-02-green-baseline', async () => {
      for (const type of state.risk_manual_types) await indicatorCriterion(`risk-baseline-${type}`, type, 'GREEN')
      const result = await riskSnapshot('GREEN'); assert.equal(result.sum, 6)
      return result
    })
    await check('RISK-02-single-red-not-maximum-color', async () => {
      const type = state.risk_manual_types[0]
      const before = (await indicators()).find((row) => row.indicatorType === type)
      await indicatorCriterion('risk-one-red', type, 'RED')
      await api('procStaff', `/api/srm/risk/indicator/${before.id}`, 'PUT', { version: before.version, criterionId: before.criteria[0].id }, 409)
      const result = await riskSnapshot('GREEN'); assert.equal(result.sum, 8)
      assert.ok(result.indicators.some((row) => row.level === 'RED'))
      return result
    })
    await check('RISK-02-yellow-total', async () => {
      await indicatorCriterion('risk-second-red', state.risk_manual_types[1], 'RED')
      const result = await riskSnapshot('YELLOW'); assert.equal(result.sum, 10)
      return result
    })
    await check('RISK-02-red-total-and-overflow', async () => {
      for (let index = 2; index < state.risk_manual_types.length; index++) await indicatorCriterion(`risk-red-${index}`, state.risk_manual_types[index], 'RED')
      const result = await riskSnapshot('RED'); assert.equal(result.sum, 16)
      const lastMax = Math.max(...result.thresholds.map((row) => row.maxScore)); assert.ok(result.sum > lastMax)
      const manualAssessment = await command('risk-explicit-assessment-red', 'procStaff', `/api/srm/risk/assessment/${state.supplier_id}`, 'POST', { remark: '本Goal真实总分转红验证' })
      assert.equal(manualAssessment.overallLevel, 'RED')
      const history = await api('procScopeManager', `/api/srm/risk/assessment/history/${state.supplier_id}`)
      assert.ok(history.some((row) => row.id === manualAssessment.id))
      const list = await api('procScopeManager', '/api/srm/risk/list?riskLevel=RED&page=1&size=100')
      assert.ok(list.records.some((row) => row.supplierId === state.supplier_id))
      await api('procStaff', '/api/srm/risk/list?page=0', 'GET', undefined, 400)
      return { ...result, explicit_assessment_id: manualAssessment.id, capture_checkpoint: '当前保留真实RED，取图后再执行risk-recovery。' }
    })
  } else if (mode === 'risk-preview') {
    assert.ok(state.commands['risk-explicit-assessment-red']?.complete, '红色综合评估须已真实完成，不能伪造前置')
    await check('RISK-02-preview-real-input', async () => {
      for (const type of state.risk_manual_types) await indicatorCriterion(`risk-preview-green-${type}`, type, 'GREEN')
      await indicatorCriterion('risk-preview-first-red', state.risk_manual_types[0], 'RED')
      const snapshot = await riskSnapshot('GREEN'); assert.equal(snapshot.sum, 8)
      assert.equal(snapshot.indicators.filter((item) => item.level === 'RED').length, 1)
      return { ...snapshot, next: '保留真实8分/GREEN和一项RED，用实际页面复现最高颜色预览缺陷；不提交弹窗。' }
    })
  } else if (mode === 'risk-recovery') {
    assert.ok(state.completed['RISK-02-red-total-and-overflow'])
    await check('RISK-02-restore-green-before-certificate', async () => {
      for (const type of state.risk_manual_types) await indicatorCriterion(`risk-recovery-${type}`, type, 'GREEN')
      return riskSnapshot('GREEN')
    })
    await check('RISK-02-certificate-expiry-and-renewal', async () => {
      const id = state.objects['qualification-main'].id
      const date = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
      const day = new Date(`${date}T12:00:00Z`)
      const dateOffset = (offset) => new Date(day.getTime() + offset * 86400000).toISOString().slice(0, 10)
      const observations = []
      for (const [key, expiryDate, expected, score, sum] of [
        ['expired', dateOffset(-1), 'RED', 3, 8], ['soon', dateOffset(15), 'YELLOW', 2, 7], ['renewed', '2029-12-31', 'GREEN', 1, 6],
      ]) {
        await edit(`qualification-risk-${key}`, 'qualification', id, { expiryDate })
        const snapshot = await riskSnapshot('GREEN')
        const certificate = snapshot.indicators.find((row) => row.type === 'CERTIFICATE')
        assert.equal(certificate.level, expected); assert.equal(Number(certificate.score), score); assert.equal(certificate.value, expiryDate)
        assert.equal(snapshot.sum, sum); observations.push({ ...certificate, overall_level: snapshot.overall_level, sum: snapshot.sum, assessment_id: snapshot.assessment_id })
      }
      const row = await make('certificate-delete-risk', 'qualification', { qualificationName: 'Goal过期删除恢复资质', issueDate: '2020-01-01', expiryDate: dateOffset(-1) })
      assert.equal((await riskSnapshot()).indicators.find((item) => item.type === 'CERTIFICATE').level, 'RED')
      await remove('certificate-delete-restores-risk', 'qualification', row.id)
      const final = await riskSnapshot('GREEN'); assert.equal(final.indicators.find((item) => item.type === 'CERTIFICATE').level, 'GREEN')
      const history = await api('procStaff', `/api/srm/risk/assessment/history/${state.supplier_id}`)
      assert.ok(observations.every((item) => history.some((entry) => entry.id === item.assessment_id)))
      return { source_date_shanghai: date, observations, final, deleted_qualification_id: row.id, automatic_recalculation_on_all_three_writes: true }
    })
  }
  run.result = 'PASS_ACTUAL_SRM_PHASE_NOT_FULL_SRM'; run.finished_at = new Date().toISOString(); persist(); local.publish()
  console.log(JSON.stringify({ result: run.result, mode, cases: run.cases.length, requests: run.requests.length }))
} catch (error) { run.result = 'FAIL'; run.error = error.message; run.finished_at = new Date().toISOString(); persist(); throw error }
