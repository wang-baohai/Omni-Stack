/** SRM 原生操作补证：独立供应商的创建、冻结、解冻、黑名单与淘汰。 */
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

const require = createRequire(resolve('omni-frontend/package.json'))
const { chromium, expect } = require('@playwright/test')
const project = 'omni-wiki-acceptance-20261009', runId = 'WIKI-20261013-SRM-F'
const supplierName = runId + ' Native Vendor F', base = 'http://omni-frontend:3000'
const phases = { suspend: ['APPROVED', 'SUSPENDED', 'suspend'], resume: ['SUSPENDED', 'APPROVED', 'resume'],
  blacklist: ['APPROVED', 'BLACKLISTED', 'addToBlacklist'], restore: ['BLACKLISTED', 'APPROVED', 'removeFromBlacklist'],
  eliminate: ['APPROVED', 'ELIMINATED', 'eliminate'] }
const endpoint = phase => phase === 'restore' ? 'restore-from-blacklist' : phase
const phase = process.argv[2]
assert.ok(phase === 'prepare' || phases[phase], 'Explicit native operation phase required')
assert.ok(existsSync('/.dockerenv'))
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_SRM_PROJECT, project)
assert.equal(process.env.E2E_SRM_WRITES, 'allow-owned-project')
const hash = value => createHash('sha256').update(value).digest('hex')
/** 私有状态和身份必须在仓库外；不能把认证材料放进规范源。 */
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), name + ' requires a private absolute path')
  const child = relative(process.cwd(), resolve(value))
  assert.ok(child === '..' || child.startsWith('..' + sep), name + ' must be outside repository')
  return resolve(value)
}
const statePath = outside('E2E_SRM_NATIVE_STATE_FILE')
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const teaching = JSON.parse(readFileSync(outside('E2E_TEACHING_STATE_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const runtime = JSON.parse(readFileSync(outside('E2E_SRM_RUNTIME_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const images = outside('E2E_DOCS_IMAGE_DIR')
assert.equal(teaching.project, project)
assert.equal(runtime.project, project)
assert.equal(runtime.containers.length, 24)
assert.ok(runtime.containers.every(x => x.ports === 0))
const sourceFiles = ['scripts/fixtures/capture-srm-native-actions.mjs', 'omni-frontend/e2e-docs/fixtures/docs-page.ts',
  'omni-frontend/src/views/srm/supplier/index.vue', 'omni-frontend/src/api/srm-supplier.ts',
  'omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierServiceImpl.java']
const sources = sourceFiles.map(path => ({ path, sha256: hash(readFileSync(path)) }))
const journal = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) : {
  schema: 1, project, runId, baseline: teaching.baseline, started_at: new Date().toISOString(), runtime,
  operations: {}, captures: [], observations: [], phase_complete: [], attempts: [],
  limits: ['Only supplier creation and five lifecycle commands use real native UI clicks.',
    'Three admission decisions use ordinary candidate API identities, not native approval clicks.',
    'No portal account, CAPTCHA self-registration or social login is claimed.'],
}
assert.equal(journal.project, project)
assert.equal(journal.runId, runId)
assert.ok(!journal.phase_complete.includes(phase), 'Observe a completed phase rather than replaying it')
assert.ok(!Object.values(journal.operations).some(x => x.status === 'SUBMITTING'), 'Reconcile unknown submissions before retrying')
journal.attempts.push({ phase, started_at: new Date().toISOString(), sources, runtime_snapshot: runtime })
/** 已提交操作和原图逐项持久化，断流后不得盲目重放。 */
function persist() {
  mkdirSync(dirname(statePath), { recursive: true })
  journal.updated_at = new Date().toISOString()
  writeFileSync(statePath + '.next', JSON.stringify(journal, null, 2) + '\n')
  renameSync(statePath + '.next', statePath)
}
function token(actor) {
  const value = tokens[actor + 'Token']
  assert.equal(typeof value, 'string')
  const claims = JSON.parse(Buffer.from(value.split('.')[1], 'base64url').toString())
  assert.ok(claims.exp * 1000 > Date.now() + 60000, 'Refresh expired controlled identities')
  assert.ok(!(claims.roles || []).some(x => String(x).includes('SUPER_ADMIN')))
  return value
}
async function api(actor, path, method = 'GET', body) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(30000),
    headers: { Authorization: 'Bearer ' + token(actor), 'X-Tenant-Id': '1', ...(body ? { 'Content-Type': 'application/json' } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) })
  const result = await response.json()
  journal.observations.push({ at: new Date().toISOString(), actor, path, method, http: response.status, code: result.code })
  persist()
  assert.equal(response.status, 200)
  assert.equal(result.code, 200, actor + ' ' + method + ' ' + path)
  return result.data
}
async function poll(fn, description) {
  const end = Date.now() + 90000
  while (Date.now() < end) {
    const result = await fn()
    if (result) return result
    await new Promise(done => setTimeout(done, 1500))
  }
  throw new Error('Observation timed out: ' + description + '; reconcile existing state before restarting')
}
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
function label(locale, key) {
  let value = dictionaries[locale]
  for (const part of key.split('.')) value = value?.[part]
  assert.equal(typeof value, 'string', locale + '/' + key)
  return value
}
const browser = await chromium.launch()
journal.tools = { node: process.version, playwright: require('@playwright/test/package.json').version, chromium: browser.version() }
async function pageFor(locale) {
  const context = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 },
    timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce', colorScheme: 'light' })
  const page = await context.newPage()
  await prepareDocsPage(page, { locale, token: token('srmstaff'), username: teaching.users.srmstaff.username })
  await page.goto('/admin/srm/supplier')
  await waitForDocsPage(page, 'main')
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  return { page, context }
}
async function rowFor(page, locale) {
  await page.locator('.search-form input').first().fill(supplierName)
  await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click()
  const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: supplierName })
  await expect(row).toHaveCount(1)
  return row
}
async function capture(page, locale, name, flow, assertions) {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
  await page.evaluate(async () => { await document.fonts.ready; await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done))) })
  await page.screenshot({ animations: 'disabled' })
  await captureDocsImage(page, locale, name)
  const image = resolve(images, locale, name + '.png')
  journal.captures.push({ locale, name, flow, actor: 'srmstaff', roles: teaching.users.srmstaff.roleCodes,
    viewport: page.viewportSize(), route: new URL(page.url()).pathname, image, sha256: hash(readFileSync(image)),
    captured_at: new Date().toISOString(), assertions, sources, test_sha256: sources[0].sha256,
    runtime_image: runtime.containers.find(x => x.service === 'omni-frontend').image, visual_review: 'pending', mocks: false })
  persist()
}
async function captureAll(name, flow, prepare) {
  for (const locale of docsLocales) {
    const prior = journal.captures.find(x => x.name === name && x.locale === locale)
    if (prior) { assert.equal(hash(readFileSync(prior.image)), prior.sha256); continue }
    const { page, context } = await pageFor(locale)
    try { await capture(page, locale, name, flow, await prepare(page, locale)) }
    finally { await context.close() }
  }
  console.log('SRM_NATIVE_CAPTURE_PASS ' + name + ' locales=4')
}
async function createForm(page, locale) {
  await page.getByRole('button', { name: label(locale, 'srmSupplierPage.create'), exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  const item = key => dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: label(locale, key) }) })
  await item('srmSupplierPage.supplierName').locator('input').fill(supplierName)
  await item('srmSupplierOverview.type').locator('.el-select').click()
  await page.getByRole('option', { name: label(locale, 'srmSupplierPage.typeManufacturer'), exact: true }).click()
  await item('srmSupplierPage.industry').locator('input').fill('ELECTRONICS')
  await item('srmSupplierPage.creditCode').locator('input').fill('WIKI20261013SRMF')
  return dialog
}
async function detailAll(name, flow, supplier) {
  await captureAll(name, flow, async (page, locale) => {
    const row = await rowFor(page, locale)
    await row.getByRole('button', { name: label(locale, 'srmSupplierPage.overview'), exact: true }).click()
    const drawer = page.locator('.el-drawer:visible')
    await expect(drawer).toContainText(supplierName)
    await expect(drawer).toContainText(supplier.status)
    return { supplier_id: supplier.id, status: supplier.status, version: supplier.version, detail_opened_natively: true }
  })
}
async function confirmation(page, locale, phaseName) {
  const row = await rowFor(page, locale)
  await row.getByRole('button', { name: label(locale, 'srmSupplierPage.more'), exact: true }).click()
  await page.getByRole('menuitem', { name: label(locale, 'srmSupplierPage.' + phases[phaseName][2]), exact: true }).click()
  const box = page.locator('.el-message-box:visible')
  await expect(box).toContainText(supplierName)
  return box
}
try {
  if (phase === 'prepare') {
    await captureAll('srm-native-create-form', 'admission', async (page, locale) => {
      await createForm(page, locale)
      return { name: supplierName, native_form: true, submitted: false }
    })
    if (!journal.operations.create) {
      const found = await api('srmstaff', '/api/srm/supplier/list?name=' + encodeURIComponent(supplierName) + '&page=1&size=100')
      assert.equal(found.total, 0, 'Reconcile any pre-existing uniquely named supplier before creating')
      const { page, context } = await pageFor('zh-CN')
      try {
        const dialog = await createForm(page, 'zh-CN')
        journal.operations.create = { status: 'SUBMITTING', actor: 'srmstaff', native_ui: true, at: new Date().toISOString() }; persist()
        const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === '/api/srm/supplier' && r.request().method() === 'POST')
        await dialog.getByRole('button', { name: label('zh-CN', 'common.save'), exact: true }).click()
        const response = await responsePromise, result = await response.json()
        assert.equal(response.status(), 200); assert.equal(result.code, 200); assert.ok(result.data.id)
        journal.operations.create = { ...journal.operations.create, status: 'COMMITTED', result: result.data, finished_at: new Date().toISOString() }
        journal.supplier = result.data; persist()
      } finally { await context.close() }
    }
    journal.supplier = await poll(async () => { const s = await api('srmstaff', '/api/srm/supplier/' + journal.operations.create.result.id); return s.processInstanceId ? s : null }, 'admission process')
    assert.equal(journal.supplier.name, supplierName)
    assert.equal(journal.supplier.ownerUserId, teaching.users.srmstaff.id)
    for (const actor of ['srmmanager', 'compliance', 'director']) {
      const key = 'approve:' + actor
      if (journal.operations[key]?.status === 'COMMITTED') continue
      const task = await poll(async () => { const data = await api(actor, '/api/workflow/task/todo?page=1&size=100'); return data.records.find(x => x.processInstanceId === journal.supplier.processInstanceId) }, actor + ' task')
      journal.operations[key] = { status: 'SUBMITTING', actor, native_ui: false, task_id: task.taskId, at: new Date().toISOString() }; persist()
      await api(actor, '/api/workflow/approval/' + task.taskId + '/complete', 'POST', { approved: true, comment: runId + ' ' + actor })
      journal.operations[key] = { ...journal.operations[key], status: 'COMMITTED', finished_at: new Date().toISOString() }; persist()
    }
    journal.supplier = await poll(async () => { const s = await api('srmstaff', '/api/srm/supplier/' + journal.supplier.id); return s.status === 'APPROVED' ? s : null }, 'approval result')
    await detailAll('srm-native-approved-detail', 'activation', journal.supplier)
  } else {
    assert.ok(journal.phase_complete.includes('prepare'))
    const [before, after] = phases[phase]
    const key = 'native:' + phase
    const current = await api('srmstaff', '/api/srm/supplier/' + journal.supplier.id)
    assert.equal(current.name, supplierName)
    assert.equal(current.ownerUserId, teaching.users.srmstaff.id)
    if (!journal.operations[key]) {
      assert.equal(current.status, before)
      assert.equal(current.version, journal.supplier.version)
      if (phase === 'suspend') await captureAll('srm-native-suspend-confirmation', 'lifecycle', async (page, locale) => {
        await confirmation(page, locale, phase)
        return { supplier_id: current.id, before: current.status, confirmation_only: true, submitted: false }
      })
      const { page, context } = await pageFor('zh-CN')
      try {
        const box = await confirmation(page, 'zh-CN', phase)
        const path = '/api/srm/supplier/' + current.id + '/' + endpoint(phase)
        journal.operations[key] = { status: 'SUBMITTING', actor: 'srmstaff', native_ui: true, before: current.status, version: current.version, path, at: new Date().toISOString() }; persist()
        const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === 'POST')
        await box.locator('.el-message-box__btns .el-button--primary').click()
        const response = await responsePromise, result = await response.json()
        assert.equal(response.status(), 200); assert.equal(result.code, 200)
        assert.equal(response.request().postDataJSON().version, current.version)
        assert.equal(result.data.status, after)
        journal.operations[key] = { ...journal.operations[key], status: 'COMMITTED', result: result.data, finished_at: new Date().toISOString() }
        journal.supplier = result.data; persist()
      } finally { await context.close() }
    }
    const actual = await api('srmstaff', '/api/srm/supplier/' + journal.supplier.id)
    assert.equal(actual.status, after)
    assert.equal(actual.version, journal.operations[key].result.version)
    journal.supplier = actual
    await detailAll('srm-native-' + phase + '-detail', 'lifecycle', actual)
    if (phase === 'eliminate') journal.cleanup = { status: 'TERMINAL_HISTORY_RETAINED_IN_OWNED_PROJECT', supplier_id: actual.id,
      reason: 'ELIMINATED supplier has formal admission and lifecycle history; project cleanup remains an explicit final step.' }
  }
  journal.phase_complete.push(phase)
  journal.status = phase === 'eliminate' ? 'NATIVE_LIFECYCLE_PASS_VISUAL_REVIEW_PENDING' : 'PHASE_PASS_VISUAL_REVIEW_PENDING'
  persist()
  console.log('SRM_NATIVE_PHASE_PASS ' + phase + ' captures=' + journal.captures.length)
} catch (error) {
  journal.status = 'FAILED_RECONCILIATION_REQUIRED'; journal.error = { phase, at: new Date().toISOString(), message: error.message }; persist()
  throw error
} finally { await browser.close() }
