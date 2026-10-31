import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import { dirname, resolve } from 'node:path'
import { createContext, SourceTextModule } from 'node:vm'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext, privateRoot } from './goal-local-identities.mjs'

// 原WP27S02的真实失败/恢复页面；只读UI，不改DOM，不用响应模拟，不点击业务写按钮。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
const [mode, attempt] = process.argv.slice(2)
assert.ok(['failed','recovered'].includes(mode)); assert.match(attempt ?? '', new RegExp(`^recovery-${mode}-images-r[1-9][0-9]*$`))
const cancelDetailOnly = process.argv.includes('--cancel-detail-only')
assert.ok(!cancelDetailOnly || mode === 'recovered')
const state = JSON.parse(readFileSync(privateRoot + '/procurement-recovery-scenarios.json', 'utf8'))
assert.equal(state.namespace, 'GOAL20261022PROCRECOVERY'); assert.ok(state.candidate.restored)
assert.ok(mode === 'failed' ? !state.completed.closed : state.completed.closed)
assert.equal(process.env.E2E_RUNTIME_IMAGE_ID, 'sha256:d2cf6cb712e38a037855a41f18febec74a6095b83247731520c35181376ecb5a')
const dir = '/validation/evidence/codex-2026-10-22/wp27-s02/' + attempt
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const report = { original_step: 'WP-27.S02', mode, attempt, started_at: new Date().toISOString(), workers: 1,
  script: 'scripts/fixtures/capture-procurement-recovery.mjs', script_sha256: sha(readFileSync(new URL(import.meta.url))),
  frontend_image: process.env.E2E_RUNTIME_IMAGE_ID, identity_source: 'test-fixture', actor: 'procStaff', user_id: 215,
  response_mocked: false, display_dom_modified: false, business_write_performed: false, human_captcha_login_claimed: false,
  viewport: { width: 1440, height: 900 }, browser_timezone: 'Asia/Shanghai', images: [], checks: [], page_errors: [], blocked_mutations: [], api_errors: [] }
report.capture_scope = cancelDetailOnly ? 'Only four native cancellation detail views; retain actual earlier eight viewport receipts with their original script digest' : 'Four-language real list and retry-object detail views'
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const locales = ['zh-CN','en-US','ja-JP','ko-KR'], modules = new Map(), vmContext = createContext({})
async function dictionary(locale) {
  const root = '/validation/source/omni-frontend/src/locales'
  const compile = path => {
    assert.ok(locales.some(value => path === root + '/' + value + '.ts'))
    if (!modules.has(path)) modules.set(path, new SourceTextModule(stripTypeScriptTypes(readFileSync(path, 'utf8')), { context: vmContext, identifier: path }))
    return modules.get(path)
  }
  const module = compile(root + '/' + locale + '.ts')
  if (module.status === 'unlinked') await module.link((specifier,parent) => { assert.match(specifier, /^\.\/(zh-CN|en-US|ja-JP|ko-KR)(\.ts)?$/); return compile(resolve(dirname(parent.identifier), specifier.endsWith('.ts') ? specifier : specifier + '.ts')) })
  if (module.status === 'linked') await module.evaluate()
  assert.equal(module.status, 'evaluated'); return module.namespace.default
}
const local = createLocalContext(), token = await local.login('procStaff')
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] })
try {
  for (const locale of locales) {
    const dict = await dictionary(locale), context = await browser.newContext({ locale, viewport: report.viewport, timezoneId: report.browser_timezone })
    context.setDefaultTimeout(20000)
    await context.route('**/api/**', async route => {
      if (['GET','HEAD','OPTIONS'].includes(route.request().method())) return route.continue()
      report.blocked_mutations.push({ locale, method: route.request().method(), path: new URL(route.request().url()).pathname }); save(); return route.abort()
    })
    await context.addInitScript(({ token,username,locale }) => {
      if (location.origin !== 'http://omni-frontend:3000') return
      localStorage.setItem('token', token); localStorage.setItem('username', username)
      localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
    }, { token, username: local.state.accounts.procStaff.username, locale })
    const page = await context.newPage()
    page.on('pageerror', error => { report.page_errors.push({ locale, message: error.message }); save() })
    page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) { report.api_errors.push({ locale, path: new URL(response.url()).pathname, status: response.status() }); save() } })
    await page.goto('http://omni-frontend:3000/admin/procurement/requisition', { waitUntil: 'networkidle' })
    assert.ok(!/login|403|menu-load-error/.test(page.url()))
    const panel = page.locator('.requisition-page'), form = panel.locator('.el-form').first()
    await form.locator('input').first().fill('Goal 20261022 procurement recovery')
    const responsePromise = page.waitForResponse(response => {
      const url = new URL(response.url()); return url.pathname === '/api/procurement/requisition/list' && url.searchParams.get('keyword') === 'Goal 20261022 procurement recovery'
    })
    await form.getByRole('button', { name: dict.common.search, exact: true }).click()
    const response = await responsePromise, data = await response.json()
    assert.equal(response.status(), 200); assert.equal(data.code, 200); assert.equal(data.data.records.length, 2)
    for (const label of ['retry','cancel']) {
      const expected = state.objects[label], actual = data.data.records.find(row => row.id === expected.id)
      assert.ok(actual); assert.equal(actual.status, mode === 'failed' ? 'SUBMITTED' : label === 'retry' ? 'APPROVED' : 'CANCELLED')
      assert.equal(actual.workflowStartStatus, mode === 'failed' ? 'FAILED' : label === 'retry' ? 'STARTED' : 'FAILED')
      const row = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: expected.requisitionNo })
      await row.waitFor(); assert.equal(await row.count(), 1)
      if (mode === 'failed') {
        assert.ok(await row.getByText(dict.procurementRequisitionPage.startFailed, { exact: true }).isVisible())
        assert.ok(await row.getByRole('button', { name: dict.procurementRequisitionPage.retryStart, exact: true }).isEnabled())
        assert.ok(await row.getByRole('button', { name: dict.common.cancel, exact: true }).isEnabled())
      } else assert.equal(await row.getByRole('button', { name: dict.procurementRequisitionPage.retryStart, exact: true }).count(), 0)
    }
    for (const loader of await panel.locator('.el-loading-mask').all()) await loader.waitFor({ state: 'hidden' })
    async function capture(scene) {
      await page.evaluate(() => document.fonts.ready)
      const name = 'proc-recovery-' + mode + '-' + scene + '-' + locale + '.png'
      const bytes = await page.screenshot({ path: dir + '/' + name, fullPage: false, animations: 'disabled' })
      report.images.push({ locale, scene, filename: name, sha256: sha(bytes), captured_at: new Date().toISOString(), viewport: report.viewport,
        image_dimensions: { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20) }, route: '/admin/procurement/requisition', role: 'PROCUREMENT_STAFF', user_id: 215,
        actual_objects: data.data.records.map(row => ({ id: row.id, requisitionNo: row.requisitionNo, status: row.status, workflowStartStatus: row.workflowStartStatus, approvalAttempt: row.approvalAttempt, version: row.version })), visual_review: 'pending' }); save()
    }
    if (!cancelDetailOnly) await capture('list')
    const detailObject = state.objects[cancelDetailOnly ? 'cancel' : 'retry']
    const selected = panel.locator('.el-table__body-wrapper tbody tr').filter({ hasText: detailObject.requisitionNo })
    await selected.getByRole('button', { name: dict.procurementRequisitionPage.detail, exact: true }).click()
    const drawer = page.locator('.el-drawer:visible'); await drawer.waitFor()
    await drawer.getByText(detailObject.requisitionNo, { exact: true }).waitFor()
    if (cancelDetailOnly) await drawer.getByText(dict.procurementRequisitionPage.statusCancelled, { exact: true }).waitFor()
    for (const mask of await drawer.locator('.el-loading-mask').all()) await mask.waitFor({ state: 'hidden' })
    await page.waitForFunction(element => element.getBoundingClientRect().right <= innerWidth + 1 && element.getBoundingClientRect().left >= 0, await drawer.elementHandle())
    await capture(cancelDetailOnly ? 'cancel-detail' : 'detail')
    report.checks.push({ locale, result: 'PASS_REAL_TWO_OBJECT_STATES_AND_NATIVE_UI', list_payload_sha256: sha(JSON.stringify(data)), images: cancelDetailOnly ? 1 : 2 }); save()
    await context.close()
  }
  assert.equal(report.images.length, cancelDetailOnly ? 4 : 8); assert.equal(report.page_errors.length, 0); assert.equal(report.api_errors.length, 0); assert.equal(report.blocked_mutations.length, 0)
  report.result = 'PASS_' + (cancelDetailOnly ? 'FOUR_NATIVE_CANCEL_DETAILS' : 'EIGHT_REAL_' + mode.toUpperCase() + '_VIEWPORTS') + '_PENDING_ACTUAL_VISUAL_REVIEW'
  report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, images: report.images.length, locales: report.checks.length }))
} catch (error) { report.result = 'FAIL_RECOVERY_CAPTURE'; report.error = error.message; save(); throw error }
finally { await browser.close(); writeFileSync(dir + '/source-used.mjs', readFileSync(new URL(import.meta.url))) }
