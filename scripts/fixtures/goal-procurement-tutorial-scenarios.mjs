import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { appendFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02：只为缺失的原生操作图建立一条专用单据链，逐阶段停留，不重放已有成功业务。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2), phases = ['open', 'quote', 'award', 'confirm', 'receive', 'quality']
assert.ok(phases.includes(mode)); assert.match(attempt ?? '', new RegExp(`^tutorial-business-${mode}-r[1-9][0-9]*$`))
const expectedImages = { PROCUREMENT: 'e043e8bebc7df4cb5c8b55bb88e9624c9e204f7c2897dda30c942e6921d2d340', SRM: '169eb5f48c0ebf44367a09e8771f7c8b45cb8b1ff5eb1d16185a0795a6640143', WORKFLOW: '2d0479552f79fd62fe589e263dd6ffa87c74916f1f7c75b31c5f7d6a130094da', ASSET: '57314b7774192e23630e6fde829a2ac0830304f16f9759e0d3699382190c9176' }
for (const [name, image] of Object.entries(expectedImages)) assert.equal(process.env['E2E_' + name + '_IMAGE_ID'], 'sha256:' + image)
const local = createLocalContext(), statePath = privateRoot + '/procurement-tutorial-scenarios.json'
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : { namespace: 'GOAL20261027PROCTUTORIAL', commands: {}, objects: {}, completed: {} }
assert.equal(state.namespace, 'GOAL20261027PROCTUTORIAL'); assert.ok(!state.completed[mode], '本阶段已完成，禁止再次执行；需要图片时只执行只读取证脚本')
if (mode !== 'open') assert.ok(state.completed[phases[phases.indexOf(mode) - 1]], '必须先完成上一实际业务阶段')
const main = JSON.parse(readFileSync(privateRoot + '/procurement-business-scenarios.json', 'utf8'))
assert.ok(main.completed['OV-01-real-summary-spend-currency-and-three-data-scopes'])
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const raw = privateRoot + '/' + attempt + '-wire.ndjson'; writeFileSync(raw, '', { flag: 'wx', mode: 0o600 })
const sha = value => createHash('sha256').update(value).digest('hex'), clone = value => JSON.parse(JSON.stringify(value))
const run = { original_step: 'WP-27.S02', mode, attempt, started_at: new Date().toISOString(), workers: 1, identity_source: 'test-fixture', human_captcha_login_claimed: false,
  script_sha256: sha(readFileSync(new URL(import.meta.url))), runtime_images: expectedImages, requests: [], original_raw_log: raw,
  limits: ['单条新增教程链，正式业务请求，不是新增WP/S', '旧PO2/GR2/3/5和失败恢复链不重放、不改库状态', '已确认收货和资产保留可追踪记录，不用数据库删除伪造回退', '仅本阶段，不冒称全部教程/图片审校/内部G11完成'] }
const persist = () => { writeFileSync(statePath, JSON.stringify(state, null, 2) + '\n', { mode: 0o600 }); writeFileSync(dir + '/result.json', JSON.stringify({ ...run, objects: state.objects }, null, 2) + '\n') }
const tokens = {}
async function api(actor, path, method = 'GET', body) {
  assert.ok(['procStaff', 'supplier', 'assetManager'].includes(actor)); assert.match(path, /^\/api\/(procurement|srm\/portal|asset\/asset)\//)
  tokens[actor] ??= await local.login(actor)
  const row = { actor, user_id: local.state.accounts[actor].id, method, path, body: body ?? null, at: new Date().toISOString() }; run.requests.push(row); persist()
  let response
  try { response = await fetch('http://omni-gateway:8080' + path, { method, headers: { Authorization: `Bearer ${tokens[actor]}`, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) }) }
  catch { row.outcome = method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN'; persist(); throw new Error(method + ' ' + path + '结果未知，必须只读核对，不盲重发') }
  const data = await response.json(); appendFileSync(raw, JSON.stringify({ ...row, http_status: response.status, response: data }) + '\n')
  row.http_status = response.status; row.business_code = data.code; row.payload_sha256 = sha(JSON.stringify(data)); persist()
  assert.equal(response.status, 200); assert.equal(data.code, 200, method + ' ' + path); return data.data
}
async function command(key, actor, path, method, body) {
  if (state.commands[key]?.complete) return clone(state.commands[key].result)
  assert.ok(!state.commands[key], '命令结果未知，禁止重放：' + key)
  state.commands[key] = { actor, path, method, body, attempt, at: new Date().toISOString() }; persist()
  const result = (await api(actor, path, method, body)) ?? null
  Object.assign(state.commands[key], { complete: true, result: clone(result), completed_at: new Date().toISOString() }); persist(); return result
}
const rfq = id => '/api/procurement/rfq/' + id, po = id => '/api/procurement/purchase-order/' + id, gr = id => '/api/procurement/goods-receipt/' + id
const dateAfterDays = days => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(new Date(Date.now() + days * 86400000))
async function cards() {
  const rows = await api('assetManager', '/api/asset/asset/list?page=1&size=100&keyword=' + encodeURIComponent('Goal 资产化物料样本'))
  return rows.records.filter(row => row.sourcePoId === state.objects.order.id)
}
try {
  const preservedOrder = await api('procStaff', po(2)); assert.equal(preservedOrder.status, 'RECEIVED'); assert.equal(preservedOrder.version, 6); assert.equal(preservedOrder.totalAmount, '112.5300')
  const preservedRfq = await api('procStaff', rfq(3)); assert.equal(preservedRfq.status, 'CANCELLED')
  if (mode === 'open') {
    const title = 'Goal 20261027 procurement illustrated tutorial'
    if (!state.commands['create-rfq']) {
      const existing = await api('procStaff', '/api/procurement/rfq/list?page=1&size=100&keyword=' + encodeURIComponent(title))
      assert.equal(existing.records.length, 0, '固定自然键已有对象，先核对而不是再创建')
    }
    const source = await api('procStaff', '/api/procurement/requisition/8'); assert.equal(source.status, 'APPROVED'); assert.equal(source.version, 7)
    state.deadline ??= dateAfterDays(7); state.validUntil ??= dateAfterDays(10); persist()
    let current = await command('create-rfq', 'procStaff', '/api/procurement/rfq', 'POST', { requisitionId: source.id, title, quotationDeadline: state.deadline, supplierIds: [9] })
    current = await command('send-rfq', 'procStaff', rfq(current.id) + '/send', 'POST', { version: current.version })
    assert.equal(current.status, 'SENT'); state.objects.rfq = current; persist()
    const invitation = await api('supplier', '/api/srm/portal/quotation/invitations/' + current.id)
    assert.equal(invitation.currentQuotation, null); assert.equal(invitation.lines.length, 2); state.objects.invitation = invitation
  } else if (mode === 'quote') {
    const current = await api('procStaff', rfq(state.objects.rfq.id)); assert.equal(current.status, 'SENT')
    const invitation = await api('supplier', '/api/srm/portal/quotation/invitations/' + current.id)
    const quotation = await command('submit-quotation', 'supplier', '/api/srm/portal/quotation', 'POST', { requestId: 'goal_20261027_proc_tutorial_quote_v1', rfqId: current.id, version: 0, validUntil: state.validUntil,
      lines: invitation.lines.map(row => ({ rfqLineId: row.rfqLineId, unitPrice: row.materialCode === 'GOAL20261022_PROC_ASSET' ? '25.000000' : '12.500000', deliveryDays: 7, remark: 'Goal tutorial real quotation' })) })
    assert.equal(quotation.totalAmount, '100.0000'); assert.equal(quotation.version, 1); assert.equal(quotation.supplierId, 9); state.objects.quotation = quotation
    const comparison = await api('procStaff', rfq(current.id) + '/comparison'); assert.equal(comparison.length, 1); assert.equal(comparison[0].id, quotation.id); assert.equal(comparison[0].version, 1)
  } else if (mode === 'award') {
    const current = await api('procStaff', rfq(state.objects.rfq.id)); assert.equal(current.status, 'SENT')
    const quotation = state.objects.quotation
    const result = await command('award-rfq', 'procStaff', rfq(current.id) + '/award', 'POST', { rfqVersion: current.version, quotationId: quotation.id, quotationVersion: quotation.version, title: 'Goal 20261027 illustrated tutorial order', deliveryAddress: 'Goal local test warehouse', contactName: 'Goal fixture receiver', contactPhone: '00000000000' })
    assert.equal(result.rfq.status, 'AWARDED'); assert.equal(result.purchaseOrder.status, 'DRAFT'); assert.equal(result.purchaseOrder.totalAmount, '100.0000'); assert.equal(result.purchaseOrder.ownerUserId, 215)
    state.objects.rfq = result.rfq; state.objects.order = result.purchaseOrder
  } else if (mode === 'confirm') {
    let order = await command('send-order', 'procStaff', po(state.objects.order.id) + '/send', 'POST', { version: state.objects.order.version })
    order = await command('confirm-order', 'procStaff', po(order.id) + '/confirm', 'POST', { version: order.version }); assert.equal(order.status, 'CONFIRMED'); state.objects.order = order
  } else if (mode === 'receive') {
    const order = await api('procStaff', po(state.objects.order.id)); assert.equal(order.status, 'CONFIRMED'); assert.equal(order.lines.length, 2)
    state.receivedAt ??= dateAfterDays(0); persist()
    let receipt = await command('create-receipt', 'procStaff', '/api/procurement/goods-receipt', 'POST', { poId: order.id, receiveTime: state.receivedAt, remark: 'Goal 20261027 illustrated tutorial pending inspection',
      lines: order.lines.map(row => ({ poLineId: row.id, receivedQuantity: row.quantity, qualityStatus: 'PENDING', remark: 'Goal tutorial awaiting actual inspection' })) })
    receipt = await command('confirm-receipt', 'procStaff', gr(receipt.id) + '/confirm', 'POST', { version: receipt.version })
    assert.equal(receipt.status, 'CONFIRMED'); assert.ok(receipt.lines.every(row => row.qualityStatus === 'PENDING')); state.objects.receipt = receipt; persist()
    const received = await api('procStaff', po(order.id)); assert.equal(received.status, 'RECEIVED'); state.objects.order = received; assert.equal((await cards()).length, 0)
  } else {
    const before = await api('procStaff', gr(state.objects.receipt.id)); assert.equal(before.status, 'CONFIRMED'); assert.ok(before.lines.every(row => row.qualityStatus === 'PENDING'))
    const receipt = await command('complete-quality', 'procStaff', gr(before.id) + '/quality-result', 'POST', { version: before.version, lines: before.lines.map(row => ({ goodsReceiptLineId: row.id, qualityStatus: 'PASS' })) })
    assert.ok(receipt.lines.every(row => row.qualityStatus === 'PASS')); state.objects.receipt = receipt; persist()
    let rows = []
    for (let count = 0; count < 100; count++) { rows = await cards(); if (rows.length === 3) break; assert.ok(rows.length < 3, '不能用等待掩盖重复卡片'); await new Promise(resolve => setTimeout(resolve, 400)) }
    assert.equal(rows.length, 3)
    for (const card of rows) { assert.equal(typeof card.id, 'string'); assert.equal(card.purchaseAmount, '25.00'); assert.equal(card.sourceGrId, receipt.id); assert.equal(card.status, 'IN_STOCK') }
    state.objects.asset_cards = rows.map(row => ({ id: row.id, assetNo: row.assetNo, sourcePoId: row.sourcePoId, sourceGrId: row.sourceGrId, sourceGrLineId: row.sourceGrLineId, sourceUnitSequence: row.sourceUnitSequence, purchaseAmount: row.purchaseAmount, status: row.status }))
  }
  state.completed[mode] = { attempt, at: new Date().toISOString(), actual_requests: run.requests.length }
  run.result = 'PASS_REAL_TUTORIAL_BUSINESS_PHASE_ONLY'; run.finished_at = new Date().toISOString(); persist()
  console.log(JSON.stringify({ result: run.result, phase: mode, requests: run.requests.length, rfq: state.objects.rfq?.id, order: state.objects.order?.id, receipt: state.objects.receipt?.id, asset_cards: state.objects.asset_cards?.length ?? 0 }))
} catch (error) { run.result = 'FAIL_REAL_TUTORIAL_PHASE_PRESERVE_JOURNAL'; run.error = error.message; persist(); throw error }
finally { writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) }
