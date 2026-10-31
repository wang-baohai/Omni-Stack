import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { dirname, resolve } from 'node:path'
import { createContext, SourceTextModule } from 'node:vm'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02：原生界面和未提交表单分阶段取证；所有业务写入一律拦截，不伪造页面和回调。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock')); assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2)
const assetDetailOnly = process.argv.includes('--asset-detail-only')
assert.ok(!assetDetailOnly || mode === 'final')
const layoutFixture = process.argv.includes('--layout-fixture')
assert.ok(!layoutFixture || ['order', 'receipt'].includes(mode))
const phases = { quote: 'open', compare: 'quote', order: 'award', 'order-result': 'quality', receipt: 'confirm', quality: 'receive', final: 'quality', scope: 'quality', 'supplier-approved': 'quality' }
assert.ok(Object.hasOwn(phases, mode)); assert.match(attempt ?? '', new RegExp(`^execution-${mode}-images-r[1-9][0-9]*$`))
assert.equal(process.env.E2E_RUNTIME_IMAGE_ID, 'sha256:d2cf6cb712e38a037855a41f18febec74a6095b83247731520c35181376ecb5a')
const state = JSON.parse(readFileSync(privateRoot + (layoutFixture ? '/procurement-layout-scenarios.json' : '/procurement-tutorial-scenarios.json'), 'utf8'))
if (layoutFixture) {
  assert.equal(state.namespace, 'GOAL20261027PROCLAYOUT'); assert.ok(state.completed[mode === 'order' ? 'draft' : 'confirmed'])
  assert.ok(!state.completed[mode === 'order' ? 'confirmed' : 'cancelled'], '补充订单已推进，不能重造旧表单状态')
} else {
  assert.equal(state.namespace, 'GOAL20261027PROCTUTORIAL'); assert.ok(state.completed[phases[mode]])
  if (!['final', 'scope', 'order-result', 'supplier-approved'].includes(mode)) assert.ok(!state.completed[{ quote: 'quote', compare: 'award', order: 'confirm', receipt: 'receive', quality: 'quality' }[mode]], '实际单据已推进，不能重造旧状态截图')
}
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = value => createHash('sha256').update(value).digest('hex'), local = createLocalContext()
const report = { original_step: 'WP-27.S02', mode, attempt, started_at: new Date().toISOString(), workers: 1, script_sha256: sha(readFileSync(new URL(import.meta.url))), frontend_image: process.env.E2E_RUNTIME_IMAGE_ID,
  namespace: state.namespace, viewport: { width: 1440, height: 900 }, browser_timezone: 'Asia/Shanghai', identity_source: 'test-fixture', human_captcha_login_claimed: false, business_write_performed: false, display_dom_modified: false, response_mocked: false,
  images: [], api_observations: [], page_errors: [], blocked_mutations: [], limits: ['未保存表单不是该按钮提交成功证据，真实业务阶段另有正式请求日志', '业务名称保留原输入；只验本次实际页面，不冒称全文/所有门禁通过'] }
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'], modules = new Map(), vmContext = createContext({})
async function dictionary(locale) {
  const root = '/validation/source/omni-frontend/src/locales'
  const compile = path => { assert.ok(locales.some(value => path === root + '/' + value + '.ts')); if (!modules.has(path)) modules.set(path, new SourceTextModule(stripTypeScriptTypes(readFileSync(path, 'utf8')), { context: vmContext, identifier: path })); return modules.get(path) }
  const module = compile(root + '/' + locale + '.ts')
  if (module.status === 'unlinked') await module.link((specifier, parent) => { assert.match(specifier, /^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/); return compile(resolve(dirname(parent.identifier), specifier.endsWith('.ts') ? specifier : specifier + '.ts')) })
  if (module.status === 'linked') await module.evaluate()
  return module.namespace.default
}
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] })
try {
  for (const locale of locales) {
    const dict = await dictionary(locale)
    const actors = mode === 'supplier-approved' ? ['srmAdmin'] : mode === 'quote' ? ['supplier'] : mode === 'scope' ? ['procStaff', 'procScopeManager', 'procPeer', 'employee'] : mode === 'final' ? (assetDetailOnly ? ['assetManager'] : ['procStaff', 'assetManager']) : ['procStaff']
    for (const actor of actors) {
      const token = await local.login(actor), context = await browser.newContext({ locale, viewport: report.viewport, timezoneId: report.browser_timezone }); context.setDefaultTimeout(25000)
      await context.route('**/api/**', async route => { if (['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) return route.continue(); report.blocked_mutations.push({ actor, locale, method: route.request().method(), path: new URL(route.request().url()).pathname }); save(); return route.abort() })
      await context.addInitScript(({ token, username, locale }) => { if (location.origin !== 'http://omni-frontend:3000') return; localStorage.setItem('token', token); localStorage.setItem('username', username); localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light') }, { token, username: local.state.accounts[actor].username, locale })
      const page = await context.newPage(), tasks = []
      page.on('pageerror', error => { report.page_errors.push({ actor, locale, message: error.message }); save() })
      page.on('response', response => {
        const url = new URL(response.url()); if (!/^\/api\/(procurement|srm|asset)\//.test(url.pathname)) return
        tasks.push((async () => { const data = await response.json(); report.api_observations.push({ actor, locale, path: url.pathname + url.search, http_status: response.status(), business_code: data.code, payload_sha256: sha(JSON.stringify(data)) }); save(); assert.ok(data.code === 200 || (mode === 'scope' && actor === 'employee' && data.code === 403), actor + ':' + url.pathname + ':' + data.code) })())
      })
      async function settled() { await page.waitForLoadState('networkidle'); for (const loader of await page.locator('.el-loading-mask').all()) await loader.waitFor({ state: 'hidden' }); await Promise.all(tasks); await page.evaluate(() => document.fonts.ready) }
      async function navigate(route, selector) { await page.goto('http://omni-frontend:3000' + route, { waitUntil: 'networkidle' }); assert.ok(!/login|menu-load-error/.test(page.url())); const panel = page.locator(selector); await panel.waitFor(); await settled(); return panel }
      async function search(panel, keyword) { await panel.locator('.el-form').first().locator('input').first().fill(keyword); await panel.getByRole('button', { name: dict.common.search, exact: true }).click(); await settled() }
      const rowBy = (panel, text) => panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: text })
      async function capture(scene, purpose, objects = {}) {
        await settled(); const drawer = page.locator('.el-drawer:visible')
        if (await drawer.count()) await page.waitForFunction(element => { const r = element.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth + 1 }, await drawer.elementHandle())
        assert.ok(!/\b(?:procurement[A-Za-z]+|common|assetLedger|portal)\.[A-Za-z][A-Za-z0-9_.]+/.test(await page.locator('body').innerText()), '可见未翻译技术键')
        if (assetDetailOnly && scene !== 'linked-asset-detail') return
        const filename = 'proc-' + scene + '-' + locale + '.png', bytes = await page.screenshot({ path: dir + '/' + filename, fullPage: false, animations: 'disabled' })
        assert.equal(bytes.readUInt32BE(16), 1440); assert.equal(bytes.readUInt32BE(20), 900)
        report.images.push({ scene, locale, filename, actor, user_id: local.state.accounts[actor].id, sha256: sha(bytes), image_dimensions: report.viewport, captured_at: new Date().toISOString(), route: new URL(page.url()).pathname, purpose, actual_objects: objects, visual_review: 'pending' }); save()
        if (['purchase-order-edit', 'goods-receipt-form', 'goods-receipt-form-pending'].includes(scene)) {
          const labels = await page.locator('.el-dialog:visible .el-form-item__label').evaluateAll(items => items.map(item => ({ text: item.textContent, client_height: item.clientHeight, scroll_height: item.scrollHeight })))
          report.form_label_observations ??= []; report.form_label_observations.push({ scene, locale, labels }); save()
          assert.ok(labels.every(item => item.scroll_height <= item.client_height + 1), '表单标签跨行遮挡下一字段')
        }
      }
      if (mode === 'supplier-approved') {
        assert.equal(local.state.accounts[actor].id, 218)
        const panel = await navigate('/admin/srm/supplier', '.page-container')
        await panel.locator('.search-form input').first().fill('Goal 20261022 supplier')
        const responsePromise = page.waitForResponse(response => {
          const url = new URL(response.url()); return url.pathname === '/api/srm/supplier/list' && url.searchParams.get('name') === 'Goal 20261022 supplier'
        })
        await panel.getByRole('button', { name: dict.common.search, exact: true }).click()
        const response = await responsePromise, data = await response.json(); assert.equal(response.status(), 200); assert.equal(data.code, 200)
        assert.equal(data.data.records.length, 1); const supplier = data.data.records[0]
        assert.equal(supplier.id, 9); assert.equal(supplier.status, 'APPROVED'); assert.equal(supplier.categoryCode, 'GOAL20261022_PROC_LEAF')
        const row = rowBy(panel, supplier.supplierNo); assert.equal(await row.count(), 1)
        await row.getByText(dict.srmSupplierPage.statusApproved, { exact: true }).waitFor()
        await capture('supplier-approved', 'SRM管理员实际读取供应商9已批准与匹配品类；采购RFQ可选邀请的前置，不冒称是采购模块自有审批按钮', { supplier: 9, status: 'APPROVED', category: supplier.categoryCode, owner_unchanged: true })
      } else if (mode === 'quote') {
        await navigate('/supplier-portal', '.portal-page')
        await page.getByRole('tab', { name: dict.common.srmPortalQuotation, exact: true }).click(); await settled()
        const row = rowBy(page, state.objects.rfq.rfqNo); await row.waitFor(); assert.equal(await row.count(), 1)
        await row.getByRole('button', { name: dict.portal.quotation.submit, exact: true }).click()
        const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await dialog.getByText(state.objects.rfq.title, { exact: true }).waitFor()
        const rows = dialog.locator('.el-table__body-wrapper tbody tr'); assert.equal(await rows.count(), 2)
        for (let i = 0; i < 2; i++) { const line = rows.nth(i), text = await line.innerText(); await line.locator('.el-input input').first().fill(text.includes('PROC_ASSET') ? '25.000000' : '12.500000'); await line.locator('.el-input-number input').fill('7') }
        await dialog.locator('.quotation-form .el-date-editor input').fill(state.validUntil); await dialog.locator('.el-dialog__header').click()
        await capture('supplier-quotation-form', '受邀供应商224实际可编辑报价表单，尚未提交；随后正式API报价另记录', { rfq: state.objects.rfq.id, supplier: 9, version: 0, not_saved: true })
        await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      } else if (mode === 'compare') {
        const panel = await navigate('/admin/procurement/rfq', '.rfq-page'); await search(panel, state.objects.rfq.title)
        const row = rowBy(panel, state.objects.rfq.rfqNo); assert.equal(await row.count(), 1); await row.getByRole('button', { name: dict.procurementRfqPage.compareAward, exact: true }).click()
        const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await dialog.locator('.comparison-table').getByText('100.0000 CNY', { exact: true }).waitFor()
        await dialog.locator('.comparison-table .el-radio').click(); await dialog.locator('.el-table__expand-icon').click(); await settled()
        await capture('quotation-comparison', '真实当前报价1及两行展开明细；单个受邀供应商示例，不冒称多供应商竞价', { rfq: state.objects.rfq.id, quotation: state.objects.quotation.id, version: 1, total: '100.0000', not_awarded: true })
        const form = dialog.locator('.award-form')
        for (const [prop, text] of [['title', 'Goal 20261027 illustrated tutorial order'], ['deliveryAddress', 'Goal local test warehouse'], ['contactName', 'Goal fixture receiver'], ['contactPhone', '00000000000']]) {
          const labels = { title: dict.procurementPurchaseOrderTracker.orderTitle, deliveryAddress: dict.procurementPurchaseOrderTracker.deliveryAddress, contactName: dict.procurementPurchaseOrderTracker.contact, contactPhone: dict.procurementPurchaseOrderPage.contactPhone }
          await form.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: new RegExp('^' + labels[prop] + '$') }) }).locator('input,textarea').fill(text)
        }
        await dialog.getByRole('button', { name: dict.procurementRfqCompare.awardAndCreate, exact: true }).scrollIntoViewIfNeeded()
        await capture('award-form', '原生定标表单已选真实报价和交付信息，滚至底部展示确认按钮；未点击最终确认', { rfq: state.objects.rfq.id, quotation: state.objects.quotation.id, not_saved: true })
        await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      } else if (mode === 'order') {
        const panel = await navigate('/admin/procurement/purchase-order', '.purchase-order-page'); await search(panel, state.objects.order.poNo)
        const row = rowBy(panel, state.objects.order.poNo); assert.equal(await row.count(), 1); await row.getByRole('button', { name: dict.procurementPurchaseOrderPage.editDelivery, exact: true }).click()
        const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await capture('purchase-order-edit', '实际DRAFT订单交付信息编辑表单，金额来自中选报价；未保存', { order: state.objects.order.id, status: 'DRAFT', total: '100.0000', not_saved: true }); await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      } else if (mode === 'order-result') {
        const panel = await navigate('/admin/procurement/purchase-order', '.purchase-order-page'); await search(panel, state.objects.order.poNo)
        const row = rowBy(panel, state.objects.order.poNo); assert.equal(await row.count(), 1); await row.getByRole('button', { name: dict.procurementRequisitionPage.detail, exact: true }).click()
        const drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await drawer.locator('.order-tracker').waitFor(); await drawer.getByText(state.objects.order.poNo, { exact: true }).waitFor(); await drawer.getByText('100.0000 CNY', { exact: true }).waitFor()
        await capture('tutorial-order-received', '教程专用PO5的真实已收货、100.0000金额及3/2累计数量', { order: state.objects.order.id, status: 'RECEIVED', total: '100.0000', receipt: state.objects.receipt.id })
      } else if (mode === 'receipt') {
        const panel = await navigate('/admin/procurement/goods-receipt', '.goods-receipt-page'); await panel.getByRole('button', { name: dict.procurementGoodsReceiptPage.create, exact: true }).click()
        const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await dialog.locator('.el-select').first().click()
        const option = page.getByRole('option').filter({ hasText: state.objects.order.poNo + ' ·' }); await option.waitFor(); assert.equal(await option.count(), 1); await option.click(); await settled()
        const rows = dialog.locator('.el-table__body-wrapper tbody tr'); await rows.nth(1).waitFor(); assert.equal(await rows.count(), 2)
        await capture('goods-receipt-form', '真实CONFIRMED订单的可收数量3/2及默认质量选项；未建草稿', { order: state.objects.order.id, not_saved: true })
        for (let i = 0; i < 2; i++) {
          const select = rows.nth(i).locator('.el-select'); await select.click()
          const listId = await select.getByRole('combobox').getAttribute('aria-controls'); assert.ok(listId)
          const list = page.locator('[id="' + listId + '"]'), pending = list.getByRole('option', { name: dict.procurementGoodsReceiptForm.qualityPending, exact: true })
          await pending.waitFor(); await pending.click(); await list.waitFor({ state: 'hidden' })
        }
        await capture('goods-receipt-form-pending', '实际将两行改为待质检，通过原生控件显示质量选项；未保存', { order: state.objects.order.id, not_saved: true, quality: 'PENDING' }); await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      } else if (mode === 'quality' || (mode === 'final' && actor === 'procStaff')) {
        const panel = await navigate('/admin/procurement/goods-receipt', '.goods-receipt-page'); await search(panel, state.objects.receipt.grNo)
        const row = rowBy(panel, state.objects.receipt.grNo); assert.equal(await row.count(), 1)
        await row.getByRole('button', { name: dict.srmRiskPage.detail, exact: true }).click(); const drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await drawer.getByText(state.objects.receipt.grNo, { exact: true }).waitFor()
        await capture(mode === 'quality' ? 'receipt-before-quality' : 'receipt-after-quality', '实际已确认收货详情，确认数量与质检结果分别记录', { receipt: state.objects.receipt.id, order: state.objects.order.id, quality: mode === 'quality' ? 'PENDING' : 'PASS' })
        await drawer.locator('.el-drawer__close-btn').click(); await drawer.waitFor({ state: 'hidden' })
        if (mode === 'quality') { await row.getByRole('button', { name: dict.procurementGoodsReceiptPage.qualityResult, exact: true }).click(); const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await capture('quality-result-form', '实际待检行的后补质检表单，可选通过/不通过；未提交', { receipt: state.objects.receipt.id, not_saved: true }); await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click() }
      } else if (mode === 'final') {
        const card = state.objects.asset_cards[0], panel = await navigate('/admin/asset/asset', '.asset-ledger-page'); await search(panel, card.assetNo)
        const row = rowBy(panel, card.assetNo); assert.equal(await row.count(), 1)
        await capture('linked-asset-list', '按资产编号定位真实三张衍生卡中的一张，不冒称本图展示全部三张', { asset: card.id, actual_total_for_order: 3, order: state.objects.order.id, receipt: state.objects.receipt.id })
        await row.getByRole('button', { name: dict.assetDisposal.details, exact: true }).click(); const drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await drawer.getByText(card.assetNo, { exact: true }).waitFor(); await drawer.getByText(state.objects.order.poNo + ' / ' + state.objects.receipt.grNo, { exact: true }).waitFor()
        await capture('linked-asset-detail', '真实资产详情的25.00金额、在库和采购订单/收货来源', { asset: card.id, purchase_amount: '25.00', order: state.objects.order.id, receipt: state.objects.receipt.id })
      } else {
        if (actor === 'employee') {
          await page.goto('http://omni-frontend:3000/admin/procurement/purchase-order', { waitUntil: 'networkidle' })
          await page.locator('.status-page').getByText('403 ' + dict.errorPage.forbiddenTitle, { exact: true }).waitFor()
          assert.equal(new URL(page.url()).pathname, '/admin/procurement/purchase-order')
          await capture('scope-no-permission', '210当前无采购菜单，保留原深链的原生403兜底页；不是导航到/403，更不是HTTP状态码断言', { user: 210, denied: true })
        }
        else { const panel = await navigate('/admin/procurement/purchase-order', '.purchase-order-page'); await search(panel, state.objects.order.poNo); const count = await rowBy(panel, state.objects.order.poNo).count(); assert.equal(count, actor === 'procPeer' ? 0 : 1); await capture('scope-' + actor, '同一订单按正式SELF/父部门/同级范围展示；只读无权限放宽', { user: local.state.accounts[actor].id, order: state.objects.order.id, matched_rows: count }) }
      }
      await settled(); await context.close()
    }
  }
  assert.equal(report.page_errors.length, 0); assert.equal(report.blocked_mutations.length, 0)
  const expected = { quote: 4, compare: 8, order: 4, 'order-result': 4, receipt: 8, quality: 8, final: 12, scope: 16, 'supplier-approved': 4 }
  assert.equal(report.images.length, assetDetailOnly ? 4 : expected[mode]); report.capture_scope = assetDetailOnly ? 'Only four asset detail images rejected for raw history status; other actually reviewed views retained at their recorded version' : 'All stated native views in this mode'; report.result = 'PASS_NATIVE_EXECUTION_CAPTURE_PENDING_EACH_ACTUAL_IMAGE_REVIEW'; report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, mode, images: report.images.length }))
} catch (error) { report.result = 'FAIL_NATIVE_EXECUTION_CAPTURE'; report.error = error.message; save(); throw error }
finally { await browser.close(); writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) }
