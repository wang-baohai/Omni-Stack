import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { dirname, resolve } from 'node:path'
import { createContext, SourceTextModule } from 'node:vm'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02原生界面取证；仅查询、正式只读试算及未提交表单，不改DOM、响应或业务记录。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2)
assert.ok(['catalog', 'rules', 'basics'].includes(mode))
const affectedOnly = process.argv.includes('--affected-only')
const baselineOnly = process.argv.includes('--baseline-only')
const formsOnly = process.argv.includes('--forms-only')
const overviewOnly = process.argv.includes('--overview-only')
assert.ok(!affectedOnly || ['basics', 'catalog'].includes(mode)); assert.ok(!baselineOnly || mode === 'basics'); assert.ok(!(affectedOnly && baselineOnly))
assert.ok(!formsOnly || (mode === 'basics' && !affectedOnly && !baselineOnly))
assert.ok(!overviewOnly || (mode === 'catalog' && !affectedOnly && !baselineOnly && !formsOnly))
const matchViewport = process.argv.find(value => value.startsWith('--match-viewport='))?.split('=')[1]
assert.ok(!matchViewport || (mode === 'rules' && ['desktop', 'mobile', 'tablet'].includes(matchViewport)))
assert.match(attempt ?? '', new RegExp(`^tutorial-${mode}-images-r[1-9][0-9]*$`))
assert.equal(process.env.E2E_RUNTIME_IMAGE_ID, 'sha256:d2cf6cb712e38a037855a41f18febec74a6095b83247731520c35181376ecb5a')
const state = JSON.parse(readFileSync(privateRoot + '/procurement-business-scenarios.json', 'utf8'))
const config = JSON.parse(readFileSync(privateRoot + '/procurement-config-scenarios.json', 'utf8'))
assert.equal(state.namespace, 'GOAL20261022PROCBUSINESS')
assert.ok(state.completed['OV-01-real-summary-spend-currency-and-three-data-scopes'])
const tutorialPath = privateRoot + '/procurement-tutorial-scenarios.json'
const tutorial = existsSync(tutorialPath) ? JSON.parse(readFileSync(tutorialPath, 'utf8')) : null
if (tutorial) assert.ok(tutorial.completed.quality, '当前新增教程链尚未收口，先保留其真实阶段')
const amountUnits = value => { assert.match(value, /^\d+\.\d{4}$/); return BigInt(value.replace('.', '')) }
const combinedUnits = amountUnits(state.objects.order.totalAmount) + (tutorial ? amountUnits(tutorial.objects.order.totalAmount) : 0n)
const combinedText = combinedUnits.toString().padStart(5, '0'), currentCommitted = combinedText.slice(0, -4) + '.' + combinedText.slice(-4)
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = value => createHash('sha256').update(value).digest('hex')
const actor = mode === 'rules' ? 'procScopeManager' : 'procStaff'
const local = createLocalContext(), token = await local.login(actor)
const report = { original_step: 'WP-27.S02', mode, attempt, started_at: new Date().toISOString(), workers: 1,
  script_sha256: sha(readFileSync(new URL(import.meta.url))), frontend_image: process.env.E2E_RUNTIME_IMAGE_ID,
  actor, user_id: local.state.accounts[actor].id, identity_source: 'test-fixture', human_captcha_login_claimed: false,
  response_mocked: false, display_dom_modified: false, business_write_performed: false,
  viewport: matchViewport === 'mobile' ? { width: 390, height: 844 } : matchViewport === 'tablet' ? { width: 1024, height: 768 } : { width: 1440, height: 900 }, browser_timezone: 'Asia/Shanghai', images: [], checks: [], api_observations: [], read_only_post_calls: [], page_errors: [], blocked_mutations: [] }
report.capture_scope = matchViewport ? 'Only actual approval-rule match in the special viewport required by unchanged screenshot coverage: ' + matchViewport : affectedOnly ? 'Only the twelve images rejected for missing labels, raw invitation status or unfinished order detail; other actual images retained at their recorded baseline' : baselineOnly ? 'Only five basic views missing default-size delivery evidence; twelve already reviewed corrected views retain their actual version' : 'All stated native views in this mode'
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'], modules = new Map(), vmContext = createContext({})
async function dictionary(locale) {
  const root = '/validation/source/omni-frontend/src/locales'
  const compile = path => {
    assert.ok(locales.some(value => path === root + '/' + value + '.ts'))
    if (!modules.has(path)) modules.set(path, new SourceTextModule(stripTypeScriptTypes(readFileSync(path, 'utf8')), { context: vmContext, identifier: path }))
    return modules.get(path)
  }
  const module = compile(root + '/' + locale + '.ts')
  if (module.status === 'unlinked') await module.link((specifier, parent) => { assert.match(specifier, /^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/); return compile(resolve(dirname(parent.identifier), specifier.endsWith('.ts') ? specifier : specifier + '.ts')) })
  if (module.status === 'linked') await module.evaluate()
  assert.equal(module.status, 'evaluated'); return module.namespace.default
}
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] })
try {
  for (const locale of locales) {
    const dict = await dictionary(locale), context = await browser.newContext({ locale, viewport: report.viewport, timezoneId: report.browser_timezone })
    context.setDefaultTimeout(25000)
    await context.route('**/api/**', async route => {
      if (['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) return route.continue()
      if (mode === 'rules' && route.request().method() === 'POST' && new URL(route.request().url()).pathname === '/api/procurement/approval-route/match-preview') {
        const body = route.request().postDataJSON(); assert.equal(body.categoryCode, config.objects.leaf.categoryCode); assert.equal(body.totalAmount, '100.0000')
        report.read_only_post_calls.push({ locale, path: '/api/procurement/approval-route/match-preview', body, purpose: '源码已确认正式只读试算，不保存审批规则或业务单据' }); save(); return route.continue()
      }
      report.blocked_mutations.push({ locale, method: route.request().method(), path: new URL(route.request().url()).pathname }); save(); return route.abort()
    })
    await context.addInitScript(({ token, username, locale }) => {
      if (location.origin !== 'http://omni-frontend:3000') return
      localStorage.setItem('token', token); localStorage.setItem('username', username)
      localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
    }, { token, username: local.state.accounts[actor].username, locale })
    const page = await context.newPage(), responseTasks = []
    page.on('pageerror', error => { report.page_errors.push({ locale, message: error.message }); save() })
    page.on('response', response => {
      const url = new URL(response.url())
      if (!url.pathname.startsWith('/api/procurement/')) return
      responseTasks.push((async () => { const data = await response.json(); report.api_observations.push({ locale, path: url.pathname + url.search, http_status: response.status(), business_code: data.code, payload_sha256: sha(JSON.stringify(data)) }); if (mode === 'catalog' && url.pathname === '/api/procurement/overview/summary') { report.current_overview_by_locale ??= {}; report.current_overview_by_locale[locale] = data.data; assert.deepEqual(data.data.committedAmountsByCurrency, [{ currencyCode: 'CNY', amount: currentCommitted }]) }; save(); assert.equal(response.status(), 200); assert.equal(data.code, 200) })())
    })
    async function settled() {
      await page.waitForLoadState('networkidle')
      for (const loader of await page.locator('.el-loading-mask').all()) await loader.waitFor({ state: 'hidden' })
      await Promise.all(responseTasks); await page.evaluate(() => document.fonts.ready)
    }
    async function navigate(route, selector) {
      await page.goto('http://omni-frontend:3000' + route, { waitUntil: 'networkidle' })
      assert.ok(!/login|403|menu-load-error/.test(page.url())); const panel = page.locator(selector); await panel.waitFor(); await settled(); return panel
    }
    async function capture(scene, purpose, objects = {}) {
      await settled()
      const drawer = page.locator('.el-drawer:visible')
      if (await drawer.count()) await page.waitForFunction(element => {
        const rect = element.getBoundingClientRect(); return rect.left >= 0 && rect.right <= innerWidth + 1
      }, await drawer.elementHandle())
      assert.ok(!/\b(?:procurement[A-Za-z]+|common)\.[A-Za-z][A-Za-z0-9_.]+/.test(await page.locator('body').innerText()), '实际可见界面仍有未翻译的技术键')
      if (affectedOnly && !(mode === 'catalog' ? ['category-form', 'material-form'] : ['requisition-form', 'rfq-awarded-detail', 'purchase-order-received']).includes(scene)) return
      if (baselineOnly && ['requisition-form', 'rfq-awarded-detail', 'purchase-order-received'].includes(scene)) return
      if (formsOnly && !['requisition-form', 'rfq-form'].includes(scene)) return
      if (overviewOnly && !['overview-category', 'overview-supplier', 'overview-department'].includes(scene)) return
      if (matchViewport && scene !== 'approval-match') return
      const filename = 'proc-' + scene + '-' + locale + '.png', bytes = await page.screenshot({ path: dir + '/' + filename, fullPage: false, animations: 'disabled' })
      assert.equal(bytes.readUInt32BE(16), report.viewport.width); assert.equal(bytes.readUInt32BE(20), report.viewport.height)
      report.images.push({ scene, locale, filename, sha256: sha(bytes), captured_at: new Date().toISOString(), viewport: report.viewport, image_dimensions: { ...report.viewport },
        route: new URL(page.url()).pathname, actor, user_id: report.user_id, purpose, actual_objects: objects, visual_review: 'pending' }); save()
      if (['category-form', 'material-form', 'requisition-form', 'rfq-form'].includes(scene)) {
        const labels = await page.locator('.el-dialog:visible .el-form-item__label').evaluateAll(items => items.map(item => ({ text: item.textContent, client_height: item.clientHeight, scroll_height: item.scrollHeight })))
        report.form_label_observations ??= []; report.form_label_observations.push({ scene, locale, labels }); save()
        assert.ok(labels.every(item => item.scroll_height <= item.client_height + 1), '表单标签换行越过本行，可能重叠下一字段')
      }
    }
    async function selectOption(select, text) {
      await select.click(); const option = page.getByRole('option').filter({ hasText: text }); await option.waitFor({ state: 'visible' }); assert.equal(await option.count(), 1); await option.click(); await settled()
    }
    if (mode === 'catalog') {
      const overview = await navigate('/admin/procurement/overview', '.procurement-overview-page')
      await overview.getByText(currentCommitted, { exact: true }).first().waitFor()
      await capture('overview-category', '当前本人概览和品类支出；含已完成教程链，采购承诺不是实际付款', { orders: tutorial ? [2, tutorial.objects.order.id] : [2], cancelled_order: 3, committed: currentCommitted })
      for (const dimension of ['supplier', 'department']) {
        await overview.locator('.el-segmented').getByText(dict.procurementOverviewPage[dimension], { exact: true }).click()
        await capture('overview-' + dimension, '真实切换支出维度；部门当前显示ID文本', { committed: currentCommitted, dimension })
      }
      const panel = await navigate('/admin/procurement/material', '.material-page')
      await panel.locator('.el-form').first().locator('input').first().fill('Goal ')
      await panel.getByRole('button', { name: dict.procurementApprovalRules.search, exact: true }).click(); await settled()
      await panel.getByText(config.objects.asset.materialCode, { exact: true }).waitFor()
      await capture('material-catalog', '分类树及两种真实物料，资产化与非资产化', { category: config.objects.leaf.id, materials: [config.objects.asset.id, config.objects.supply.id] })
      const category = panel.locator('.category-node').filter({ hasText: config.objects.leaf.categoryName })
      assert.equal(await category.count(), 1)
      await category.hover()
      await category.getByRole('button', { name: dict.common.edit, exact: true }).click()
      let dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await capture('category-form', '实际分类编辑表单，未提交；已有编码与父级只读', { category: config.objects.leaf.id, not_saved: true })
      await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      const material = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: config.objects.asset.materialCode }); assert.equal(await material.count(), 1)
      await material.getByRole('button', { name: dict.common.edit, exact: true }).click()
      dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await capture('material-form', '真实物料编辑表单与资产计量说明，未保存', { material: config.objects.asset.id, assetManaged: true, unit: 'PCS', not_saved: true })
      await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
    } else if (mode === 'rules') {
      const panel = await navigate('/admin/procurement/approval-route', '.approval-rules-page')
      await capture('approval-rules', '经理当前规则、覆盖分析及试算入口', { original_rule: 1, owned_rule: 2, deleted_rule: 3 })
      const tester = panel.locator('.match-tester')
      await selectOption(tester.locator('.el-select'), config.objects.leaf.categoryCode)
      await tester.locator('.el-input input').last().fill('100.0000')
      await tester.getByRole('button', { name: dict.procurementApprovalRules.test, exact: true }).click(); await settled()
      await tester.locator('.match-result').waitFor()
      assert.equal(await tester.locator('.el-alert__description').textContent(), dict.procurementApprovalRules.matchExactHint)
      if (matchViewport) await tester.locator('.match-result').scrollIntoViewIfNeeded()
      await capture('approval-match', '真实服务端试算及本语言候选流程说明，不修改规则', { category: config.objects.leaf.categoryCode, amount: '100.0000', matched_rule: 2, model_version: 2 })
      if (matchViewport) {
        const layout = await tester.locator('.match-result').evaluate(element => {
          const targets = [element, ...element.querySelectorAll('.match-summary, .match-summary dd, .flow-preview, .flow-node, .node-description, .el-alert')]
          return targets.map(item => ({ selector: item.className, client_width: item.clientWidth, scroll_width: item.scrollWidth,
            left: item.getBoundingClientRect().left, right: item.getBoundingClientRect().right, viewport_width: innerWidth }))
        })
        report.match_layout_observations ??= []; report.match_layout_observations.push({ locale, viewport: report.viewport, elements: layout }); save()
        assert.ok(layout.every(item => item.scroll_width <= item.client_width + 1 && item.left >= -1 && item.right <= item.viewport_width + 1), '匹配摘要或流程节点仍横向溢出原生视口')
      }
      if (!matchViewport) {
      const row = panel.locator('.desktop-table .el-table__body-wrapper tbody tr').filter({ hasText: config.objects.ruleA.routeName }); assert.equal(await row.count(), 1)
      await row.getByRole('button', { name: dict.procurementApprovalRules.edit, exact: true }).click()
      const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor()
      await capture('approval-rule-scope', '已有专用规则的范围与半开金额区间，未保存', { rule: 2, range: '[0,+infinity)', not_saved: true })
      await dialog.getByRole('button', { name: dict.procurementApprovalRules.next, exact: true }).click()
      await capture('approval-rule-flow', '选择实际已发布的采购审批流程版本，未保存', { rule: 2, model_version: 2, not_saved: true })
      await dialog.getByRole('button', { name: dict.procurementApprovalRules.next, exact: true }).click()
      await capture('approval-rule-confirm', '规则摘要、启用状态和可选技术信息，未保存', { rule: 2, not_saved: true })
      await dialog.getByRole('button', { name: dict.procurementApprovalRules.cancel, exact: true }).click()
      await row.getByRole('button', { name: dict.procurementApprovalRules.deactivate, exact: true }).click()
      const confirmation = page.locator('.el-message-box:visible'); await confirmation.waitFor()
      await confirmation.getByText(dict.procurementApprovalRules.impactSafeHint, { exact: false }).waitFor()
      await capture('approval-rule-impact', '正式只读影响试算后的本语言确认框；最终取消，没有停用规则', { rule: 2, impact: 'no-new-gap-or-conflict', not_confirmed: true })
      await confirmation.getByRole('button', { name: dict.common.cancel, exact: true }).click(); await confirmation.waitFor({ state: 'hidden' })
      }
    } else {
      async function search(panel, keyword) {
        await panel.locator('.el-form').first().locator('input').first().fill(keyword)
        await panel.getByRole('button', { name: dict.common.search, exact: true }).click(); await settled()
      }
      let panel = await navigate('/admin/procurement/requisition', '.requisition-page')
      await search(panel, 'Goal 20261022 procurement')
      await capture('requisition-list', '本人当前请购与真实审批轮次，含失败恢复后的批准和取消结果', { main: 8, retry_approved: 5, start_failed_then_cancelled: 6 })
      await panel.getByRole('button', { name: dict.procurementRequisitionPage.create, exact: true }).click()
      let dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await settled()
      await dialog.locator('.el-form-item').first().locator('input').fill('Goal 教程填写示例（未保存）')
      await dialog.locator('textarea').first().fill('说明实际用途、数量和预计费用，提交前核对品类。')
      await selectOption(dialog.locator('.el-select').first(), config.objects.asset.materialCode)
      let line = dialog.locator('.el-table__body-wrapper tbody tr').first()
      await line.locator('input').nth(1).fill('3.000000'); await line.locator('input').nth(2).fill('25.000000')
      await capture('requisition-form', '实际新建表单，选择真实物料和小数文本输入；没有保存或提交', { material: 6, quantity: '3.000000', price: '25.000000', not_saved: true })
      await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      let row = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: state.objects.requisition.requisitionNo }); assert.equal(await row.count(), 1)
      await row.getByRole('button', { name: dict.procurementRequisitionPage.detail, exact: true }).click()
      let drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await drawer.getByText(state.objects.requisition.requisitionNo, { exact: true }).waitFor()
      await capture('requisition-approved-detail', '真实第2轮批准结果；第一轮驳回和重提另有业务原日志', { requisition: 8, status: 'APPROVED', attempt: 2, total: '100.0000' })
      panel = await navigate('/admin/procurement/rfq', '.rfq-page'); await search(panel, 'Goal 20261022 procurement')
      await capture('rfq-list', '本人当前询价列表及授标/取消终态；非草稿不提供编辑', { main_rfq: 5, awarded_branches: [6, 7], old_cancelled: 3 })
      await panel.getByRole('button', { name: dict.procurementRfqPage.create, exact: true }).click()
      dialog = page.locator('.el-dialog:visible'); await dialog.waitFor(); await settled()
      await selectOption(dialog.locator('.el-select').first(), state.objects.requisition.requisitionNo)
      await selectOption(dialog.locator('.el-select').last(), 'Goal 20261022 supplier')
      await dialog.locator('.el-date-editor input').fill(state.deadline); await dialog.locator('.el-form-item').nth(1).locator('input').click()
      await capture('rfq-form', '真实新建询价表单和按请购品类筛选的合法供应商，未保存', { source_request: 8, supplier: 9, quotation_deadline: state.deadline, not_saved: true })
      await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click()
      row = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: state.objects.rfq.rfqNo }); assert.equal(await row.count(), 1)
      await row.getByRole('button', { name: dict.procurementRfqPage.detail, exact: true }).click()
      drawer = page.locator('.el-drawer:visible'); await drawer.waitFor(); await drawer.getByText(state.objects.rfq.rfqNo, { exact: true }).waitFor()
      await capture('rfq-awarded-detail', '当前已授标询价的物料与供应商报价快照，不伪造可继续比价按钮', { rfq: 5, status: 'AWARDED', quotation: 3, quotation_version: 2 })
      panel = await navigate('/admin/procurement/purchase-order', '.purchase-order-page'); await search(panel, 'Goal 20261022 procurement')
      await capture('purchase-orders', '真实全部收货和取消订单；已删除草稿不会出现在列表', { received: 2, cancelled: 3, deleted: 4 })
      row = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: state.objects.order.poNo }); assert.equal(await row.count(), 1)
      await row.getByRole('button', { name: dict.procurementRequisitionPage.detail, exact: true }).click()
      drawer = page.locator('.el-drawer:visible'); await drawer.waitFor()
      await drawer.locator('.order-tracker').waitFor(); await drawer.getByText(state.objects.order.poNo, { exact: true }).waitFor()
      await capture('purchase-order-received', '当前订单全部已收货及订单行累计；收货可见范围与累计范围分别解释', { order: 2, status: 'RECEIVED', total: '112.5300', confirmed_quantities: ['3.000000', '2.000000'] })
    }
    await settled(); report.checks.push({ locale, result: 'PASS_NATIVE_UI_PENDING_ACTUAL_VISUAL_REVIEW', image_count: report.images.filter(row => row.locale === locale).length }); save(); await context.close()
  }
  assert.equal(report.page_errors.length, 0); assert.equal(report.blocked_mutations.length, 0)
  assert.equal(report.images.length, (matchViewport ? 1 : overviewOnly ? 3 : formsOnly ? 2 : affectedOnly ? (mode === 'catalog' ? 2 : 3) : baselineOnly ? 5 : mode === 'basics' ? 8 : 6) * 4)
  report.result = 'PASS_NATIVE_FOUR_LANGUAGE_CAPTURE_PENDING_ACTUAL_FULL_IMAGE_REVIEW'; report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, mode, images: report.images.length, locales: report.checks.length }))
} catch (error) { report.result = 'FAIL_NATIVE_CAPTURE'; report.error = error.message; save(); throw error }
finally { await browser.close(); writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) }
