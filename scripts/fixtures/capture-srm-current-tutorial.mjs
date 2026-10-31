/** 当前 SRM 教程原生只读取证：复用本次真实样本，不重放业务写入；需 JDK25 PATH 和 node --experimental-vm-modules。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { stripTypeScriptTypes } from 'node:module'
import { createContext, SourceTextModule } from 'node:vm'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.equal(process.env.E2E_SRM_PROJECT, 'omni-stack')
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
assert.match(process.env.E2E_SRM_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^srm-current-r[1-9][0-9]*$/)
const reframeOnly = process.argv.includes('--native-reframes-only')
const dir = '/validation/evidence/codex-2026-10-22/wp26-s04/' + run
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = value => createHash('sha256').update(value).digest('hex')
const source = readFileSync(new URL(import.meta.url)); writeFileSync(dir + '/source-used.mjs', source)
const report = { original_step: 'WP-26.S04', started_at: new Date().toISOString(), workers: 1,
  script: 'scripts/fixtures/capture-srm-current-tutorial.mjs', script_sha256: sha(source),
  frontend_image: process.env.E2E_RUNTIME_IMAGE_ID, srm_image: process.env.E2E_SRM_IMAGE_ID,
  project: 'omni-stack', identity_source: 'test-fixture', human_captcha_login_claimed: false,
  authentication_audit_may_be_written: true, business_write_performed: false,
  response_mocked: false, display_dom_modified: false,
  capture_scope: reframeOnly ? 'Only qualification status, selected risk criteria and threshold viewport; full coverage retains its other real receipts' : 'All 24 scenes in four languages',
  images: [], checks: [], page_errors: [], blocked_mutations: [], api_failures: [] }
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']
const localeRoot = '/validation/source/omni-frontend/src/locales', vmContext = createContext({}), modules = new Map()
/** 只解析四份实际源码字典，不改写产品导入或页面文字。 */
async function dictionary(locale) {
  const compile = path => {
    assert.ok(locales.some(value => path === localeRoot + '/' + value + '.ts'))
    if (!modules.has(path)) modules.set(path, new SourceTextModule(stripTypeScriptTypes(readFileSync(path, 'utf8')), { context: vmContext, identifier: path }))
    return modules.get(path)
  }
  const module = compile(localeRoot + '/' + locale + '.ts')
  if (module.status === 'unlinked') await module.link((specifier, parent) => {
    assert.match(specifier, /^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/)
    return compile(resolve(dirname(parent.identifier), specifier.endsWith('.ts') ? specifier : specifier + '.ts'))
  })
  if (module.status === 'linked') await module.evaluate()
  assert.equal(module.status, 'evaluated'); return module.namespace.default
}
let local, quote, browser
const tokens = {}
const supplierName = 'Goal 20261022 SRM portal supplier', base = 'http://omni-frontend:3000'
let currentPage, currentLocale, currentActor
const responseAt = (page, path, predicate = () => true) => page.waitForResponse(response => {
  const url = new URL(response.url()); return url.pathname === path && predicate(url)
})
async function responseData(promise) {
  const response = await promise, json = await response.json()
  assert.equal(response.status(), 200); assert.equal(json.code, 200); return json.data
}
async function settled(panel) {
  await panel.waitFor({ state: 'visible' })
  await panel.page().waitForFunction(element => {
    const rect = element.getBoundingClientRect(); return rect.left >= -1 && rect.right <= innerWidth + 1
  }, await panel.elementHandle())
  for (const mask of await panel.locator('.el-loading-mask').all()) await mask.waitFor({ state: 'hidden' })
}
/** 只使用截图原生遮罩；字段值、接口响应和 DOM 不变。 */
async function privacyMasks(page, scope, dict) {
  const labels = new Set([dict.srmSupplierOverview.phone, dict.srmSupplierOverview.mobile, dict.srmSupplierOverview.email,
    dict.srmSupplierOverview.accountNo, dict.srmSupplierPage.creditCode, dict.portal.creditCode, dict.portalRegister.phone, dict.portalRegister.email].filter(Boolean))
  const masks = [], fields = []
  for (const label of await scope.locator('.el-descriptions__label').all()) {
    if (!labels.has((await label.innerText()).trim())) continue
    const value = label.locator('xpath=following-sibling::td[1]')
    if (await value.count()) { masks.push(value); fields.push((await label.innerText()).trim()) }
  }
  for (const item of await scope.locator('.el-form-item').all()) {
    const label = item.locator('.el-form-item__label'); if (await label.count() !== 1 || !labels.has((await label.innerText()).replace(/\s*\*\s*/g, '').trim())) continue
    for (const input of await item.locator('input').all()) if ((await input.inputValue()).length) { masks.push(input); fields.push((await label.innerText()).trim()) }
  }
  for (const table of await scope.locator('.el-table:visible').all()) {
    for (const header of await table.locator('.el-table__header-wrapper th').all()) {
      if (!labels.has((await header.innerText()).trim())) continue
      const column = (await header.getAttribute('class')).split(/\s+/).find(value => /^el-table_\d+_column_\d+$/.test(value))
      assert.ok(column); masks.push(table.locator('.el-table__body-wrapper td.' + column + ' .cell')); fields.push((await header.innerText()).trim())
    }
  }
  return { masks, fields }
}
async function capture(page, scene, dict, observations = {}, scope = page.locator('body')) {
  await page.evaluate(() => document.fonts.ready)
  if (await page.locator('.el-notification:visible').count()) await page.locator('.el-notification:visible').last().waitFor({ state: 'hidden' })
  const { masks, fields } = await privacyMasks(page, scope, dict), path = dir + '/' + scene + '-' + currentLocale + '.png'
  await page.screenshot({ path, animations: 'disabled', mask: masks, maskColor: '#303849' })
  const row = { scene, locale: currentLocale, actor: currentActor, user_id: local.state.accounts[currentActor].id,
    path, public_path: 'docs/images/' + currentLocale + '/' + scene + '.png', route: new URL(page.url()).pathname,
    viewport: page.viewportSize(), captured_at: new Date().toISOString(), sha256: sha(readFileSync(path)),
    native_mask_fields: fields, observations, actual_visual_review: 'PENDING' }
  report.images.push(row); report.checks.push({ locale: currentLocale, scene, result: 'PASS_REAL_READONLY_CAPTURE', observations }); save()
}
async function navigate(page, route) {
  await page.goto(base + route, { waitUntil: 'networkidle' })
  assert.equal(new URL(page.url()).pathname, route)
  if (route.startsWith('/admin/')) {
    assert.equal(await page.locator('.console-header__apps').count(), 0)
    assert.equal(await page.locator('header a[href="/"],header a[href^="/workspace/"]').count(), 0)
    assert.equal(await page.locator('.console-header__username').innerText(), local.state.accounts[currentActor].username)
  }
}
async function exactSupplier(page, dict) {
  const input = page.locator('.search-form .el-input input').first(); await input.fill(supplierName)
  const pending = responseAt(page, '/api/srm/supplier/list', url => url.searchParams.get('name') === supplierName)
  await page.getByRole('button', { name: dict.common.search, exact: true }).click()
  const data = await responseData(pending); assert.equal(data.records.length, 1); assert.equal(data.records[0].id, 10)
  assert.equal(data.records[0].status, 'APPROVED'); assert.equal(data.records[0].ownerUserId, 215)
  const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: supplierName })
  await row.waitFor(); assert.equal(await row.count(), 1); return row
}
/** 原生横滚露出完整状态列；不能改列宽、文本或覆盖前景。 */
async function qualificationCapture(page, dict, drawer) {
  await drawer.locator('#tab-qualification').click(); const panel = drawer.locator('#pane-qualification')
  const row = panel.locator('.el-table__body-wrapper tbody tr'); await row.waitFor(); assert.equal(await row.count(), 1)
  await panel.locator('.el-table__body-wrapper .el-scrollbar__wrap').evaluate(element => { element.scrollLeft = element.scrollWidth })
  const status = row.locator('td').nth(5).locator('.cell'), actions = row.locator('td').last()
  await page.waitForFunction(({ state, action }) => state.getBoundingClientRect().right <= action.getBoundingClientRect().left + 1,
    { state: await status.elementHandle(), action: await actions.elementHandle() })
  assert.equal((await status.innerText()).trim(), dict.srmResources.statusActive)
  await capture(page, 'srm-qualifications', dict, { supplier_id: 10, records: 1, native_horizontal_scroll: true, fully_visible_status: dict.srmResources.statusActive }, drawer)
}
/** 选中真实财务类型，展示其标准；较矮的原生视口聚焦阈值，不保存配置。 */
async function riskConfigScenes(page, dict) {
  await navigate(page, '/admin/srm/risk/config')
  const tables = page.locator('.risk-config-page .el-row .el-table'), financial = tables.first().locator('tbody tr').filter({ hasText: 'FINANCIAL' })
  await financial.waitFor(); assert.equal(await financial.count(), 1); await financial.locator('td').first().click()
  const criteria = tables.nth(1).locator('.el-table__body-wrapper tbody tr'); await criteria.first().waitFor()
  assert.ok(await criteria.count() > 0)
  await capture(page, 'srm-risk-config', dict, { selected_type: 'FINANCIAL', actual_criteria: await criteria.count(), config_not_saved: true })
  await page.setViewportSize({ width: 1440, height: 700 }); const thresholds = page.locator('.el-card').last()
  await thresholds.scrollIntoViewIfNeeded()
  assert.equal(await thresholds.locator('.el-table__body-wrapper tbody tr').count(), 3)
  await capture(page, 'srm-risk-thresholds', dict, { actual_threshold_rows: 3, native_viewport_height: 700, config_not_saved: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
}
async function nativeReframeScenes(page, dict) {
  await navigate(page, '/admin/srm/supplier'); const row = await exactSupplier(page, dict)
  await row.getByRole('button', { name: dict.srmSupplierPage.more, exact: true }).click()
  await page.getByRole('menuitem', { name: dict.srmSupplierPage.maintainResources, exact: true }).click()
  const drawer = page.locator('.el-drawer:visible'); await settled(drawer)
  await qualificationCapture(page, dict, drawer); await riskConfigScenes(page, dict)
}
async function internalScenes(page, dict) {
  await navigate(page, '/admin/srm/overview'); await page.locator('.overview-total').waitFor()
  await capture(page, 'srm-overview', dict)
  await navigate(page, '/admin/srm/supplier'); const row = await exactSupplier(page, dict)
  await capture(page, 'srm-suppliers', dict, { supplier_id: 10, status: 'APPROVED', owner_user_id: 215 })
  await page.getByRole('button', { name: dict.srmSupplierPage.create, exact: true }).click()
  let dialog = page.locator('.el-dialog:visible'); await settled(dialog)
  await capture(page, 'srm-supplier-create', dict, { form_submitted: false }, dialog)
  await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click(); await dialog.waitFor({ state: 'hidden' })
  const overviewLoaded = responseAt(page, '/api/srm/supplier/10/overview')
  await row.getByRole('button', { name: dict.srmSupplierPage.overview, exact: true }).click()
  const overview = await responseData(overviewLoaded)
  assert.deepEqual([overview.contacts.length, overview.qualifications.length, overview.bankAccounts.length, overview.recentEvaluations.length, overview.riskIndicators.length], [1, 1, 1, 5, 6])
  let drawer = page.locator('.el-drawer:visible'); await settled(drawer)
  await capture(page, 'srm-supplier-detail', dict, { supplier_id: 10, actual_resource_counts: [1, 1, 1, 5, 6] }, drawer)
  await drawer.locator('.el-descriptions').last().scrollIntoViewIfNeeded()
  await capture(page, 'srm-supplier-detail-bottom', dict, { supplier_id: 10, recent_evaluations: 5, risk_indicators: 6 }, drawer)
  await drawer.locator('.el-drawer__close-btn').click(); await drawer.waitFor({ state: 'hidden' })
  await row.getByRole('button', { name: dict.srmSupplierPage.more, exact: true }).click()
  await page.getByRole('menuitem', { name: dict.srmSupplierPage.maintainResources, exact: true }).click()
  drawer = page.locator('.el-drawer:visible'); await settled(drawer)
  for (const [tab, scene] of [['contact', 'srm-contacts'], ['qualification', 'srm-qualifications'], ['bank', 'srm-bank-accounts']]) {
    if (tab === 'qualification') { await qualificationCapture(page, dict, drawer); continue }
    await drawer.locator('#tab-' + tab).click(); const panel = drawer.locator('#pane-' + tab)
    await panel.locator('.el-table__body-wrapper tbody tr').waitFor(); assert.equal(await panel.locator('.el-table__body-wrapper tbody tr').count(), 1)
    await capture(page, scene, dict, { supplier_id: 10, records: 1 }, drawer)
  }
  await drawer.locator('.el-drawer__close-btn').click(); await drawer.waitFor({ state: 'hidden' })
  await row.getByRole('button', { name: dict.common.edit, exact: true }).click()
  dialog = page.locator('.el-dialog:visible'); await settled(dialog)
  const ownerField = dialog.locator('.el-form-item').filter({ hasText: dict.srmSupplierOverview.owner }).locator('.el-select')
  assert.equal(await ownerField.count(), 1); await ownerField.click(); const ownerInput = ownerField.locator('input').first()
  for (const [account, scene, ids] of [['procStaff', 'srm-owner-internal', [215]], ['srmPortal', 'srm-owner-portal-empty', []]]) {
    const name = local.state.accounts[account].username, loaded = responseAt(page, '/api/srm/options/owners', url => url.searchParams.get('keyword') === name)
    await ownerInput.fill(name); const values = await responseData(loaded); assert.deepEqual(values.map(value => value.userId), ids)
    await page.locator(ids.length ? '.el-select-dropdown:visible .el-select-dropdown__item' : '.el-select-dropdown:visible .el-select-dropdown__empty').waitFor()
    await capture(page, scene, dict, { expected_candidate_ids: ids, owner_not_saved: true }, dialog)
  }
  await page.keyboard.press('Escape'); await dialog.getByRole('button', { name: dict.common.cancel, exact: true }).click(); await dialog.waitFor({ state: 'hidden' })
  const empty = '__OMNI_GOAL_NO_SUPPLIER_MATCH_20261026__'
  await page.locator('.search-form .el-input input').first().fill(empty)
  const emptyLoaded = responseAt(page, '/api/srm/supplier/list', url => url.searchParams.get('name') === empty)
  await page.getByRole('button', { name: dict.common.search, exact: true }).click()
  assert.equal((await responseData(emptyLoaded)).total, 0); await page.locator('.el-table__empty-block').waitFor()
  await capture(page, 'srm-supplier-empty', dict, { matched_count: 0 })
  await navigate(page, '/admin/srm/evaluation'); await page.locator('.el-table__body-wrapper tbody tr').first().waitFor()
  const evaluationRows = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: supplierName }); assert.equal(await evaluationRows.count(), 5)
  await capture(page, 'srm-evaluations', dict, { supplier_id: 10, known_period_count: 5 })
  await evaluationRows.filter({ hasText: '100' }).first().getByRole('button', { name: dict.srmRiskPage.detail, exact: true }).click()
  drawer = page.locator('.el-drawer:visible'); await settled(drawer); assert.equal(await drawer.locator('.el-table__body-wrapper tbody tr').count(), 4)
  await capture(page, 'srm-evaluation-detail', dict, { dimension_count: 4, total_score: 100 }, drawer)
  await navigate(page, '/admin/srm/risk'); const riskRow = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: supplierName })
  await riskRow.waitFor(); assert.equal(await riskRow.count(), 1); await capture(page, 'srm-risks', dict, { supplier_id: 10, current_overall_level: 'GREEN' })
  await riskRow.getByRole('button', { name: dict.srmRiskPage.detail, exact: true }).click()
  drawer = page.locator('.el-drawer:visible'); await settled(drawer); assert.equal(await drawer.locator('.el-table__body-wrapper tbody tr').count(), 6)
  await capture(page, 'srm-risk-detail', dict, { indicator_count: 6 }, drawer)
  await drawer.getByRole('button', { name: dict.srmRiskPage.loadHistory, exact: true }).click()
  await drawer.locator('.el-table').last().locator('tbody tr').first().waitFor()
  await drawer.locator('.el-table').last().scrollIntoViewIfNeeded()
  await capture(page, 'srm-risk-history', dict, { history_rows: await drawer.locator('.el-table').last().locator('tbody tr').count(), history_not_rewritten: true }, drawer)
  await riskConfigScenes(page, dict)
  await navigate(page, '/admin/srm/invite'); await page.locator('.el-table').waitFor()
  await capture(page, 'srm-invites', dict, { original_invite_secret_not_displayed: true })
}
async function portalScenes(page, dict) {
  await navigate(page, '/supplier-portal'); await page.locator('#pane-profile .el-form').waitFor()
  await capture(page, 'srm-portal-profile', dict, { supplier_id: 10, admission_status: 'APPROVED', profile_not_saved: true }, page.locator('#pane-profile'))
  const invitations = responseAt(page, '/api/srm/portal/quotation/invitations'); await page.locator('#tab-quotation').click()
  const values = await responseData(invitations), current = values.find(value => value.rfqId === 3)
  assert.ok(current); assert.equal(current.totalAmount, '83.7500'); assert.equal(current.status, 'CANCELLED')
  const row = page.locator('#pane-quotation .el-table__body-wrapper tbody tr').filter({ hasText: quote.rfq.rfqNo }); await row.waitFor()
  await capture(page, 'srm-portal-quotation', dict, { rfq_id: 3, status: current.status, amount: current.totalAmount })
  const detailLoaded = responseAt(page, '/api/srm/portal/quotation/invitations/3'); await row.getByRole('button').click()
  const detail = await responseData(detailLoaded); assert.equal(detail.currentQuotation.version, 2); assert.equal(detail.status, 'CANCELLED')
  const dialog = page.locator('.el-dialog:visible'); await settled(dialog)
  for (const input of await dialog.locator('input[inputmode="decimal"]').all()) assert.equal(await input.isEnabled(), false)
  await capture(page, 'srm-portal-quotation-detail', dict, { rfq_id: 3, real_version: 2, amount: '83.7500', read_only: true, new_submission_claimed: false }, dialog)
  await dialog.locator('.el-dialog__headerbtn').click(); await dialog.waitFor({ state: 'hidden' })
  const evaluationLoaded = responseAt(page, '/api/srm/portal/evaluations'); await page.locator('#tab-evaluation').click()
  const evaluations = await responseData(evaluationLoaded); assert.equal(evaluations.total, 5); assert.equal(evaluations.records.length, 5)
  await page.locator('#pane-evaluation tbody tr').first().waitFor()
  assert.equal(await page.locator('#pane-evaluation tbody tr').count(), 5)
  await capture(page, 'srm-portal-evaluation', dict, { supplier_id: 10, visible_periods: 5 })
}
try {
  save(); assert.equal(process.env.JAVA_HOME, '/validation/cache/jdk25', '测试身份需要固定卷中原有的 JDK25，不安装旁路运行时')
  local = createLocalContext()
  for (const actor of reframeOnly ? ['srmAdmin'] : ['srmAdmin', 'srmPortal', 'employee']) tokens[actor] = await local.login(actor)
  const core = JSON.parse(readFileSync(privateRoot + '/srm-core-scenarios.json', 'utf8'))
  quote = JSON.parse(readFileSync(privateRoot + '/srm-quotation-scenarios.json', 'utf8'))
  assert.equal(core.supplier_id, 10); assert.equal(quote.rfq.id, 3); assert.equal(quote.quotation.version, 2)
  browser = await chromium.launch({ args: ['--no-sandbox'] })
  for (const locale of locales) {
    currentLocale = locale; const dict = await dictionary(locale)
    for (const actor of reframeOnly ? ['srmAdmin'] : ['srmAdmin', 'srmPortal', 'employee']) {
      currentActor = actor
      const context = await browser.newContext({ locale, viewport: { width: 1440, height: 1000 } }); context.setDefaultTimeout(20000)
      context.on('page', page => {
        page.on('pageerror', error => { report.page_errors.push({ locale, actor, error: error.message }); save() })
        page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) {
          report.api_failures.push({ locale, actor, path: new URL(response.url()).pathname, status: response.status() }); save()
        } })
      })
      await context.route('**/api/**', async route => {
        if (['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) return route.continue()
        report.blocked_mutations.push({ locale, actor, path: new URL(route.request().url()).pathname, method: route.request().method() }); save(); return route.abort()
      })
      await context.addInitScript(({ token, username, locale }) => {
        if (location.origin !== 'http://omni-frontend:3000') return
        localStorage.setItem('token', token); localStorage.setItem('username', username)
        localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
      }, { token: tokens[actor], username: local.state.accounts[actor].username, locale })
      const page = await context.newPage(); currentPage = page
      if (reframeOnly) await nativeReframeScenes(page, dict)
      else if (actor === 'srmAdmin') await internalScenes(page, dict)
      else if (actor === 'srmPortal') await portalScenes(page, dict)
      else {
        await page.goto(base + '/admin/srm/supplier', { waitUntil: 'networkidle' })
        await page.locator('.status-page .el-result__title').filter({ hasText: /^403\s/ }).waitFor()
        assert.equal(await page.locator('aside .el-menu-item,.status-page .el-result__extra').count(), 0)
        await capture(page, 'srm-employee-403', dict, { denied_identity: 'EMPLOYEE', no_module_fallback: true })
      }
      await context.close()
    }
    console.log(JSON.stringify({ locale, captured: report.images.filter(value => value.locale === locale).length }))
  }
  assert.equal(report.images.length, reframeOnly ? 12 : 96); assert.equal(new Set(report.images.map(value => value.scene)).size, reframeOnly ? 3 : 24)
  assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, []); assert.deepEqual(report.api_failures, [])
  report.result = reframeOnly ? 'CAPTURED_12_REAL_REFRAMES_PENDING_ACTUAL_AI' : 'CAPTURED_96_REAL_IMAGES_PENDING_ACTUAL_AI'; report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, images: report.images.length, checks: report.checks.length }))
} catch (error) {
  report.result = 'FAIL'; report.error = error.message
  if (currentPage && !currentPage.isClosed()) { const path = dir + '/actual-failure.png'; await currentPage.screenshot({ path }); report.failure_image = { path, sha256: sha(readFileSync(path)) } }
  save(); throw error
} finally { await browser?.close() }
