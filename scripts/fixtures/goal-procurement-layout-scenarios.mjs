import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原27S02：旧教程链已经收齐，只建立一张独立订单分阶段重拍修复后的未提交表单，再正式取消。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock')); assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2), phases = ['draft', 'confirmed', 'cancelled']
assert.ok(phases.includes(mode)); assert.match(attempt ?? '', new RegExp(`^layout-business-${mode}-r[1-9][0-9]*$`))
const expectedImages = { PROCUREMENT: 'e043e8bebc7df4cb5c8b55bb88e9624c9e204f7c2897dda30c942e6921d2d340', SRM: '169eb5f48c0ebf44367a09e8771f7c8b45cb8b1ff5eb1d16185a0795a6640143', WORKFLOW: '2d0479552f79fd62fe589e263dd6ffa87c74916f1f7c75b31c5f7d6a130094da', ASSET: '57314b7774192e23630e6fde829a2ac0830304f16f9759e0d3699382190c9176' }
for (const [name, image] of Object.entries(expectedImages)) assert.equal(process.env['E2E_' + name + '_IMAGE_ID'], 'sha256:' + image)
const scope = JSON.parse(readFileSync('/source/wiki-workspace-ux.records.local/codex-procurement-layout-fixture-scope-20261027.json', 'utf8'))
assert.equal(scope.namespace, 'GOAL20261027PROCLAYOUT'); assert.equal(scope.new_objects_maximum.goods_receipt, 0)
const local = createLocalContext(), path = privateRoot + '/procurement-layout-scenarios.json'
assert.equal(local.state.accounts.procStaff.id, 215); assert.equal(local.state.accounts.supplier.id, 224)
const state = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : { namespace: scope.namespace, commands: {}, completed: {}, objects: {} }
assert.equal(state.namespace, scope.namespace); assert.ok(!state.completed[mode], '阶段已完成，不重放成功业务')
if (mode !== 'draft') assert.ok(state.completed[phases[phases.indexOf(mode) - 1]])
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const raw = privateRoot + '/' + attempt + '-wire.ndjson'; writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const sha = bytes => createHash('sha256').update(bytes).digest('hex'), clone = value => JSON.parse(JSON.stringify(value))
const report = { original_step: 'WP-27.S02', mode, attempt, namespace: state.namespace, started_at: new Date().toISOString(), workers: 1,
  identity_source: 'test-fixture', human_captcha_login_claimed: false, script_sha256: sha(readFileSync(new URL(import.meta.url))), runtime_images: expectedImages,
  scope_sha256: sha(readFileSync('/source/wiki-workspace-ux.records.local/codex-procurement-layout-fixture-scope-20261027.json')), requests: [], original_raw_log: raw,
  limits: ['只新增1询价/1报价/1订单，不新增账号/角色/配置/收货/资产', '旧成功阶段与PO2/PO5不重放，不直接改库状态', '相应UI表单只预览不提交，最终通过正式取消关闭新增订单，保留可追踪记录'] }
const save = () => { writeFileSync(path, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 }); writeFileSync(dir + '/result.json', JSON.stringify({ ...report, objects: state.objects }, null, 2) + '\n') }
const tokens = {}
async function api(actor, endpoint, method = 'GET', body) {
  assert.ok(['procStaff', 'supplier'].includes(actor)); assert.match(endpoint, /^\/api\/(procurement|srm\/portal)\//)
  tokens[actor] ??= await local.login(actor)
  const item = { actor, user_id: local.state.accounts[actor].id, endpoint, method, body: body ?? null, at: new Date().toISOString() }; report.requests.push(item); save()
  let response
  try { response = await fetch('http://omni-gateway:8080' + endpoint, { method, headers: { Authorization: `Bearer ${tokens[actor]}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) }) }
  catch { item.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; save(); throw new Error('结果未知，先只读核对，禁止重放：' + method + ' ' + endpoint) }
  const result = await response.json(); appendFileSync(raw, JSON.stringify({ ...item, http_status: response.status, response: result }) + '\n')
  item.http_status = response.status; item.business_code = result.code; item.payload_sha256 = sha(JSON.stringify(result)); save()
  assert.equal(response.status, 200); assert.equal(result.code, 200, endpoint); return result.data
}
async function command(key, actor, endpoint, body) {
  if (state.commands[key]?.complete) return clone(state.commands[key].result)
  assert.ok(!state.commands[key], '已有结果未知命令，不重发：' + key)
  state.commands[key] = { actor, endpoint, method: 'POST', body, attempt, at: new Date().toISOString() }; save()
  const result = (await api(actor, endpoint, 'POST', body)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); save(); return result
}
const rfq = id => '/api/procurement/rfq/' + id, po = id => '/api/procurement/purchase-order/' + id
const afterDays = days => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date(Date.now() + days * 86400000))
try {
  for (const [id, version, total] of [[2, 6, '112.5300'], [5, 3, '100.0000']]) { const original = await api('procStaff', po(id)); assert.equal(original.status, 'RECEIVED'); assert.equal(original.version, version); assert.equal(original.totalAmount, total) }
  if (mode === 'draft') {
    if (!state.commands['create-rfq']) assert.equal((await api('procStaff', '/api/procurement/rfq/list?page=1&size=100&keyword=' + encodeURIComponent(scope.natural_key))).records.length, 0)
    const requisition = await api('procStaff', '/api/procurement/requisition/8'); assert.equal(requisition.status, 'APPROVED'); assert.equal(requisition.version, 7)
    state.deadline ??= afterDays(7); state.validUntil ??= afterDays(10); save()
    let current = await command('create-rfq', 'procStaff', '/api/procurement/rfq', { requisitionId: 8, title: scope.natural_key, quotationDeadline: state.deadline, supplierIds: [9] })
    current = await command('send-rfq', 'procStaff', rfq(current.id) + '/send', { version: current.version }); assert.equal(current.status, 'SENT'); state.objects.rfq = current; save()
    const invitation = await api('supplier', '/api/srm/portal/quotation/invitations/' + current.id); assert.equal(invitation.currentQuotation, null); assert.equal(invitation.lines.length, 2)
    const quotation = await command('quote', 'supplier', '/api/srm/portal/quotation', { requestId: 'goal_20261027_proc_layout_quote_v1', rfqId: current.id, version: 0, validUntil: state.validUntil,
      lines: invitation.lines.map(row => ({ rfqLineId: row.rfqLineId, unitPrice: row.materialCode === 'GOAL20261022_PROC_ASSET' ? '25.000000' : '12.500000', deliveryDays: 7, remark: 'Goal layout validation quotation' })) })
    assert.equal(quotation.totalAmount, '100.0000'); assert.equal(quotation.version, 1); state.objects.quotation = quotation
    const comparison = await api('procStaff', rfq(current.id) + '/comparison'); assert.equal(comparison.length, 1); assert.equal(comparison[0].id, quotation.id)
    const award = await command('award', 'procStaff', rfq(current.id) + '/award', { rfqVersion: current.version, quotationId: quotation.id, quotationVersion: 1,
      title: 'Goal 20261027 procurement form layout order', deliveryAddress: 'Goal local test warehouse', contactName: 'Goal fixture receiver', contactPhone: '00000000000' })
    assert.equal(award.rfq.status, 'AWARDED'); assert.equal(award.purchaseOrder.status, 'DRAFT'); assert.equal(award.purchaseOrder.ownerUserId, 215); assert.equal(award.purchaseOrder.totalAmount, '100.0000')
    state.objects.rfq = award.rfq; state.objects.order = award.purchaseOrder
  } else if (mode === 'confirmed') {
    let order = await api('procStaff', po(state.objects.order.id)); assert.equal(order.status, 'DRAFT'); assert.equal(order.ownerUserId, 215)
    order = await command('send-order', 'procStaff', po(order.id) + '/send', { version: order.version })
    order = await command('confirm-order', 'procStaff', po(order.id) + '/confirm', { version: order.version }); assert.equal(order.status, 'CONFIRMED'); state.objects.order = order
  } else {
    let order = await api('procStaff', po(state.objects.order.id)); assert.equal(order.status, 'CONFIRMED'); assert.equal(order.ownerUserId, 215)
    assert.ok(order.lines.every(row => row.receivedQuantity === '0.000000'))
    order = await command('cancel-order', 'procStaff', po(order.id) + '/cancel', { version: order.version }); assert.equal(order.status, 'CANCELLED'); state.objects.order = order
    assert.equal((await api('procStaff', '/api/procurement/goods-receipt/list?page=1&size=100&poId=' + order.id)).records.length, 0)
    const summary = await api('procStaff', '/api/procurement/overview/summary'); assert.deepEqual(summary.committedAmountsByCurrency, [{ currencyCode: 'CNY', amount: '212.5300' }])
  }
  state.completed[mode] = { attempt, at: new Date().toISOString() }; report.result = 'PASS_REAL_LAYOUT_FIXTURE_PHASE_NOT_UI_OR_RELEASE'; report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, mode, requests: report.requests.length, rfq: state.objects.rfq.id, quotation: state.objects.quotation.id, order: state.objects.order.id, order_status: state.objects.order.status }))
} catch (error) { report.result = 'FAIL_PRESERVE_AND_RECONCILE'; report.error = error.message; save(); throw error }
finally { writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) }
