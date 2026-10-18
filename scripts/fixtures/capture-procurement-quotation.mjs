/** 采购报价只读取证：保留定标终态，复查两份报价版本和供应商各自的报价记录。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import zh from '../../omni-frontend/src/locales/zh-CN.ts'
import en from '../../omni-frontend/src/locales/en-US.ts'
import ja from '../../omni-frontend/src/locales/ja-JP.ts'
import ko from '../../omni-frontend/src/locales/ko-KR.ts'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../../omni-frontend/e2e-docs/fixtures/docs-page.ts'

const project = 'omni-wiki-acceptance-20261009', base = 'http://omni-frontend:3000'
const sha = value => createHash('sha256').update(value).digest('hex')
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const { chromium, expect } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
/** 私有身份与输出禁止放进源码仓库。 */
function outside(name) {
  const path = process.env[name]
  assert.ok(path && isAbsolute(path) && relative(process.cwd(), resolve(path)).startsWith('..' + sep), name)
  return resolve(path)
}
assert.ok(existsSync('/.dockerenv'))
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_PROCUREMENT_PROJECT, project)
const captureFilter = process.env.E2E_PROCUREMENT_CAPTURE_FILTER?.split(',')
if (captureFilter) for (const item of captureFilter) assert.ok(docsLocales.some(locale => ['manager', 'supplierC', 'supplierD'].some(actor => item === `${locale}:${actor}`)), 'Invalid capture filter')
const teaching = JSON.parse(readFileSync(outside('E2E_TEACHING_STATE_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const runtime = JSON.parse(readFileSync(outside('E2E_PROCUREMENT_RUNTIME_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const receiptPath = outside('E2E_PROCUREMENT_RECEIPT_FILE')
outside('E2E_DOCS_IMAGE_DIR')
assert.ok(!existsSync(receiptPath)); assert.equal(teaching.project, project); assert.equal(runtime.project, project)
assert.equal(runtime.containers.length, 24); assert.ok(runtime.containers.every(c => c.ports === 0))
for (const actor of ['manager', 'supplierC', 'supplierD']) {
  const claims = JSON.parse(Buffer.from(tokens[actor + 'Token'].split('.')[1], 'base64url').toString())
  assert.ok(claims.exp * 1000 > Date.now() + 120_000); assert.ok(!claims.roles.includes('SUPER_ADMIN'))
}
const sources = ['scripts/fixtures/capture-procurement-quotation.mjs', 'omni-frontend/e2e-docs/fixtures/docs-page.ts',
  'omni-frontend/src/views/procurement/rfq/index.vue', 'omni-frontend/src/components/procurement/RfqCompareView.vue',
  'omni-frontend/src/views/supplier-portal/index.vue', 'omni-frontend/src/api/procurement-rfq.ts',
  'omni-frontend/src/api/srm-portal.ts'].map(path => ({ path, sha256: sha(readFileSync(path)) }))
const receipt = { schema: 1, project, baseline: teaching.baseline, started_at: new Date().toISOString(),
  state: 'RUNNING_READ_ONLY', runtime, sources, source_sha256: sha(JSON.stringify(sources)),
  observations: [], captures: [], mutations: [], mocks: false, cleanup: 'NO_BUSINESS_WRITES' }
/** 观察结果原子写入，失败也保留完整尝试。 */
function persist() {
  mkdirSync(dirname(receiptPath), { recursive: true })
  writeFileSync(receiptPath + '.next', JSON.stringify(receipt, null, 2) + '\n'); renameSync(receiptPath + '.next', receiptPath)
}
function label(locale, path) {
  let value = dictionaries[locale]
  for (const key of path.split('.')) value = value?.[key]
  assert.equal(typeof value, 'string', locale + ':' + path); return value
}
async function get(actor, path, expectedCode = 200) {
  const response = await fetch(base + path, { headers: { Authorization: 'Bearer ' + tokens[actor + 'Token'], 'X-Tenant-Id': '1' }, signal: AbortSignal.timeout(30_000) })
  const body = await response.json()
  receipt.observations.push({ at: new Date().toISOString(), actor, method: 'GET', path, http: response.status, code: body.code })
  persist(); assert.equal(response.status, 200); assert.equal(body.code, expectedCode, path)
  return expectedCode === 200 ? body.data : { business_code: body.code, http: response.status }
}
async function snapshot() {
  const id = teaching.objects.rfq.id
  return { rfq: await get('manager', `/api/procurement/rfq/${id}`), closed_comparison: await get('manager', `/api/procurement/rfq/${id}/comparison`, 409),
    supplierC: await get('supplierC', `/api/srm/portal/quotation/invitations/${id}`), supplierD: await get('supplierD', `/api/srm/portal/quotation/invitations/${id}`) }
}
const browser = await chromium.launch()
receipt.tools = { node: process.version, playwright: createRequire(resolve('omni-frontend/package.json'))('@playwright/test/package.json').version, chromium: browser.version() }
try {
  const before = await snapshot()
  assert.equal(before.rfq.status, 'AWARDED'); assert.equal(before.rfq.awardedQuotationId, 2)
  assert.equal(before.rfq.suppliers.length, 2); assert.equal(before.closed_comparison.business_code, 409)
  assert.equal(before.supplierC.currentQuotation.totalAmount, '3850.0000')
  assert.equal(before.supplierD.currentQuotation.totalAmount, '3500.0000')
  assert.equal(before.supplierC.currentQuotation.id, 1); assert.equal(before.supplierD.currentQuotation.id, 2)
  receipt.snapshot = before; receipt.before_sha256 = sha(JSON.stringify(before))
  for (const locale of docsLocales) for (const actor of ['manager', 'supplierC', 'supplierD']) {
    if (captureFilter && !captureFilter.includes(`${locale}:${actor}`)) continue
    const route = actor === 'manager' ? '/admin/procurement/rfq' : '/supplier-portal'
    const context = await browser.newContext({ baseURL: base, viewport: { width: 1920, height: 1080 }, timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' })
    try {
      const page = await context.newPage()
      await page.route('**/api/**', async r => {
        const request = r.request()
        if (['GET', 'HEAD', 'OPTIONS'].includes(request.method())) await r.continue()
        else { receipt.mutations.push({ method: request.method(), path: new URL(request.url()).pathname, blocked: true }); persist(); await r.abort('blockedbyclient') }
      })
      await prepareDocsPage(page, { locale, token: tokens[actor + 'Token'], username: teaching.users[actor].username })
      await page.goto(route); await waitForDocsPage(page, actor === 'manager' ? 'main' : '.portal-page')
      await expect(page).not.toHaveURL(/login|menu-load-error/)
      if (actor !== 'manager') {
        // 使用产品自身主题按钮切到浅色，便于阅读已禁用的报价输入框；不注入展示样式。
        if (await page.locator('html').evaluate(element => element.classList.contains('dark'))) await page.locator('.portal-header').getByTitle(label(locale, 'theme.toggle'), { exact: true }).click()
        await expect(page.locator('html')).not.toHaveClass(/dark/)
        await page.getByRole('tab', { name: label(locale, 'common.srmPortalQuotation'), exact: true }).click()
      }
      const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: teaching.objects.rfq.rfqNo }).first()
      await expect(row).toBeVisible()
      await row.getByRole('button', { name: label(locale, actor === 'manager' ? 'procurementRequisitionPage.detail' : 'common.view'), exact: true }).click()
      const panel = page.locator(actor === 'manager' ? '.el-drawer:visible' : '.el-dialog:visible')
      await expect(panel).toBeVisible(); await expect(panel).toContainText('AWARDED')
      if (actor === 'manager') {
        for (const supplier of before.rfq.suppliers) await expect(panel).toContainText(supplier.supplierName)
        await panel.locator('.el-table').last().scrollIntoViewIfNeeded()
        await expect(panel.locator('.el-table').last()).toContainText('REJECTED')
        await expect(panel.locator('.el-table').last()).toContainText('AWARDED')
      } else {
        await expect(panel).toContainText(label(locale, 'portal.quotation.readOnly'))
        const expected = before[actor].currentQuotation.lines
        const lines = panel.locator('.el-table__body-wrapper tr')
        for (let i = 0; i < expected.length; i++) await expect(lines.nth(i).locator('input.el-input__inner').first()).toHaveValue(expected[i].unitPrice)
        // 正式配图使用报价列表的金额与邀请终态；只读详情的字段值另由响应和控件断言核对。
        await panel.getByRole('button', { name: label(locale, 'common.cancel'), exact: true }).click()
        await expect(panel).not.toBeVisible()
        await expect(row).toContainText(actor === 'supplierC' ? '3850' : '3500')
        await expect(row).toContainText(actor === 'supplierC' ? 'REJECTED' : 'AWARDED')
      }
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await waitForDocsPage(page, actor === 'manager' ? '.el-drawer' : '.portal-page')
      // 等待样式稳定后的绘制，再以一次不保存的原生截图刷新浏览器绘制；不修改页面内容。
      await page.evaluate(() => new Promise(resolveFrame => requestAnimationFrame(() => requestAnimationFrame(resolveFrame))))
      await page.screenshot({ animations: 'disabled' })
      const name = actor === 'manager' ? 'procurement-current-quote-versions' : `procurement-current-${actor}-quote`
      await captureDocsImage(page, locale, name)
      const image = resolve(process.env.E2E_DOCS_IMAGE_DIR, locale, name + '.png')
      receipt.captures.push({ locale, name, actor, roles: teaching.users[actor].roleCodes, flow: actor === 'manager' ? 'comparison' : 'quotation-receipt', route,
        viewport: { width: 1920, height: 1080 }, expected: actor === 'manager' ? { rfq: before.rfq.rfqNo, state: 'AWARDED', quotes: [1, 2], versions: [1, 1], winning_quote: 2, scope: 'Retained result; no live award click' } : { rfq: before.rfq.rfqNo, own_quote: before[actor].currentQuotation.id, total: before[actor].currentQuotation.totalAmount, read_only: true, scope: 'Own quotation list amount and invitation state; detail opened and closed by native clicks; API and input assertions verify line prices' },
        image, sha256: sha(readFileSync(image)), captured_at: new Date().toISOString(), test_sha256: sources[0].sha256, source_sha256: receipt.source_sha256, visual_review: 'pending' })
      persist()
    } finally { await context.close() }
  }
  const after = await snapshot(); assert.deepEqual(after, before); receipt.after_sha256 = sha(JSON.stringify(after))
  assert.equal(receipt.mutations.length, 0); assert.equal(receipt.captures.length, captureFilter?.length || 12)
  receipt.state = 'FOUR_LANGUAGE_QUOTATION_READONLY_PASS'; receipt.completed_at = new Date().toISOString(); persist()
  process.stdout.write(`PROCUREMENT_QUOTATION_PASS captures=${receipt.captures.length} persisted_writes=0\n`)
} catch (error) { receipt.state = 'FAILED_READONLY_ATTEMPT'; receipt.failure = { at: new Date().toISOString(), message: error.message }; persist(); throw error }
finally { await browser.close() }
