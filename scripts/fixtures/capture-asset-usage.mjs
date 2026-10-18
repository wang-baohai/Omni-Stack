/** 资产领用真实教学：限定隔离项目，逐阶段登记命令，禁止断流后盲目重放。 */
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

const project = 'omni-wiki-acceptance-20261009'
const runId = 'WIKI-20261012-ASSET-USAGE'
const base = 'http://omni-frontend:3000'
const route = '/admin/asset/asset'
const viewport = { width: 1920, height: 1080 }
const sha = value => createHash('sha256').update(value).digest('hex')
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const { chromium, expect } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
/** 短期身份、原图和操作登记只允许写入仓库外的显式目录。 */
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), name + ' requires an absolute private path')
  const child = relative(process.cwd(), resolve(value))
  assert.ok(child.startsWith('..' + sep), name + ' must be outside the repository')
  return resolve(value)
}
assert.ok(existsSync('/.dockerenv'), 'Local Docker is required')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_ASSET_PROJECT, project)
assert.equal(process.env.E2E_ASSET_WRITES, 'allow-owned-project')
const phase = process.argv[2]
assert.ok(['prepare', 'source-visual', 'allocate', 'accept', 'return', 'close', 'final-visual'].includes(phase), 'Explicit asset phase required')
const teaching = JSON.parse(readFileSync(outside('E2E_TEACHING_STATE_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const runtime = JSON.parse(readFileSync(outside('E2E_ASSET_RUNTIME_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const statePath = outside('E2E_ASSET_STATE_FILE')
outside('E2E_DOCS_IMAGE_DIR')
assert.equal(teaching.project, project)
assert.equal(runtime.project, project)
assert.equal(runtime.containers.length, 24)
assert.ok(runtime.containers.every(item => item.ports === 0), 'Published ports are forbidden')
assert.ok(teaching.users.assetstaff.roleCodes.includes('ASSET_ADMIN'))
assert.ok(teaching.users.requester.roleCodes.includes('ASSET_USER'))
assert.notEqual(teaching.users.outsider.primaryUnitId, teaching.users.requester.primaryUnitId)
const sourceFiles = ['scripts/fixtures/capture-asset-usage.mjs', 'omni-frontend/e2e-docs/fixtures/docs-page.ts',
  'omni-frontend/src/views/asset/asset/index.vue', 'omni-frontend/src/api/asset-asset.ts',
  'omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/AssetServiceImpl.java',
  'omni-backend/omni-asset/src/main/java/com/omni/asset/dto/AssetRequests.java']
const sources = sourceFiles.map(path => ({ path, sha256: sha(readFileSync(path)) }))
const sourceSha = sha(JSON.stringify(sources))
const state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8'))
  : { schema: 1, project, runId, baseline: teaching.baseline, started_at: new Date().toISOString(),
    operations: {}, observations: [], captures: [], attempts: [], phase_complete: [], cleanup: 'PENDING' }
assert.equal(state.project, project)
assert.equal(state.runId, runId)
assert.ok(!state.phase_complete.includes(phase), 'Completed phases must not be replayed')
assert.ok(!Object.values(state.operations).some(item => item.status === 'SUBMITTING'), 'Reconcile unknown writes first')
state.attempts.push({ phase, at: new Date().toISOString(), sources, source_sha256: sourceSha, runtime })
/** 原子保存回执，保留每个已提交命令与原始图片摘要。 */
function persist() {
  mkdirSync(dirname(statePath), { recursive: true })
  state.updated_at = new Date().toISOString()
  writeFileSync(statePath + '.next', JSON.stringify(state, null, 2) + '\n')
  renameSync(statePath + '.next', statePath)
}
function token(actor) {
  const value = tokens[actor + 'Token']
  assert.equal(typeof value, 'string', 'Missing identity ' + actor)
  const claims = JSON.parse(Buffer.from(value.split('.')[1], 'base64url').toString())
  assert.ok(claims.exp * 1000 > Date.now() + 60_000, 'Refresh expired identity')
  assert.ok(!claims.roles.includes('SUPER_ADMIN'), 'Ordinary role evidence required')
  return value
}
async function api(actor, path, method = 'GET', body, expected = 200) {
  const response = await fetch(base + path, { method,
    headers: { Authorization: 'Bearer ' + token(actor), 'X-Tenant-Id': '1',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) })
  const result = await response.json()
  state.observations.push({ at: new Date().toISOString(), actor, method, path,
    http: response.status, code: result.code, expected })
  persist()
  assert.equal(result.code, expected, actor + ' ' + method + ' ' + path + ' HTTP ' + response.status + ' business ' + result.code)
  if (expected === 200) assert.equal(response.status, 200)
  return result.data
}
/** 只有未提交过的唯一命令才能发出业务写入。 */
async function command(key, actor, path, body, submit) {
  const digest = sha(JSON.stringify({ actor, path, body }))
  const previous = state.operations[key]
  if (previous) {
    assert.equal(previous.digest, digest)
    assert.equal(previous.status, 'COMMITTED')
    return previous.result
  }
  state.operations[key] = { digest, actor, path, status: 'SUBMITTING', at: new Date().toISOString() }
  persist()
  const result = await submit()
  state.operations[key] = { ...state.operations[key], status: 'COMMITTED', finished_at: new Date().toISOString(), result }
  persist()
  return result
}
function label(locale, path) {
  let value = dictionaries[locale]
  for (const key of path.split('.')) value = value?.[key]
  assert.equal(typeof value, 'string', locale + ' missing label ' + path)
  return value
}
const browser = await chromium.launch()
state.tools = { node: process.version, playwright: createRequire(resolve('omni-frontend/package.json'))('@playwright/test/package.json').version,
  chromium: browser.version(), loader: 'existing CLI tsx dependency volume' }
async function pageFor(actor, locale, item) {
  const context = await browser.newContext({ baseURL: base, viewport, timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' })
  const page = await context.newPage()
  await prepareDocsPage(page, { locale, token: token(actor), username: teaching.users[actor].username })
  await page.goto(route)
  await waitForDocsPage(page, 'main')
  await expect(page).not.toHaveURL(/login|menu-load-error/)
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  const keyword = page.locator('main .el-form input').first()
  await keyword.fill(item.assetNo)
  await Promise.all([
    page.waitForResponse(response => response.url().includes('/api/asset/asset/') && response.request().method() === 'GET'),
    keyword.press('Enter'),
  ])
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  return { page, context, row: page.locator('.el-table__body-wrapper tr').filter({ hasText: item.assetNo }) }
}
async function captureAll(name, flow, actor, item, prepare, assertions) {
  for (const locale of docsLocales) {
    const old = state.captures.find(capture => capture.name === name && capture.locale === locale)
    if (old) {
      assert.equal(sha(readFileSync(old.image)), old.sha256)
      continue
    }
    const { page, context, row } = await pageFor(actor, locale, item)
    try {
      await prepare(page, row, locale)
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
      await waitForDocsPage(page, 'main')
      await captureDocsImage(page, locale, name)
      const image = resolve(process.env.E2E_DOCS_IMAGE_DIR, locale, name + '.png')
      state.captures.push({ name, flow, actor, roles: teaching.users[actor].roleCodes, route, viewport, locale,
        assertions, image, sha256: sha(readFileSync(image)), captured_at: new Date().toISOString(),
        test_sha256: sources[0].sha256, source_sha256: sourceSha, mocks: false, visual_review: 'pending' })
      persist()
    } finally { await context.close() }
  }
  console.log('ASSET_CAPTURE_PASS ' + name + ' locales=4')
}
async function detail(page, row, locale, item) {
  await expect(row).toHaveCount(1)
  await row.getByRole('button', { name: label(locale, 'assetDisposal.details'), exact: true }).click()
  const drawer = page.locator('.el-drawer:visible')
  await expect(drawer).toContainText(item.assetNo)
  const statusKey = { IN_STOCK: 'statusInStock', ALLOCATED: 'statusAllocated', IN_USE: 'statusInUse', SCRAPPED: 'statusScrapped' }[item.status]
  await expect(drawer.locator('.el-descriptions')).toContainText(label(locale, 'assetLedger.' + statusKey))
  await expect(drawer.locator('.el-descriptions .el-tag')).toHaveText(label(locale, 'assetLedger.' + statusKey))
  await expect(drawer.locator('.el-descriptions .el-tag')).toBeVisible()
  await expect(drawer).toContainText(item.purchaseAmount + ' ' + item.currencyCode)
  await expect(drawer.locator('.el-table__body-wrapper tr').first()).toContainText(item.status)
  await waitForDocsPage(page, '.el-drawer')
}
async function allocationForm(page, row, locale) {
  await expect(row).toHaveCount(1)
  await row.getByRole('button', { name: label(locale, 'assetLedger.allocate'), exact: true }).click()
  const dialog = page.locator('.el-dialog:visible')
  await expect(dialog).toBeVisible()
  await dialog.locator('.el-select input').fill(teaching.users.requester.username)
  const option = page.locator('.el-select-dropdown__item:visible').filter({ hasText: teaching.users.requester.username })
  await expect(option).toHaveCount(1)
  await option.click()
  await expect(dialog.locator('input:disabled')).toHaveValue(String(teaching.users.requester.primaryUnitId))
  await dialog.locator('textarea').fill(runId + ' allocate')
}
async function confirmation(page, row, locale, action) {
  await expect(row).toHaveCount(1)
  await row.getByRole('button', { name: label(locale, action === 'accept' ? 'assetLedger.accept' : 'assetLedger.return'), exact: true }).click()
  await expect(page.locator('.el-message-box')).toBeVisible()
  await expect(page.locator('.el-message-box')).toContainText(state.asset.assetNo)
}
/** 实际点击仅发生一次；预览四语确认框不会提交。 */
async function uiCommand(key, actor, item, path, body, prepare, confirmSelector) {
  return command(key, actor, path, body, async () => {
    const { page, context, row } = await pageFor(actor, 'zh-CN', item)
    try {
      await prepare(page, row, 'zh-CN')
      const [response] = await Promise.all([
        page.waitForResponse(reply => new URL(reply.url()).pathname === path && reply.request().method() === 'POST'),
        page.locator(confirmSelector).click(),
      ])
      assert.equal(response.status(), 200)
      const result = await response.json()
      assert.equal(result.code, 200)
      state.observations.push({ at: new Date().toISOString(), actor, method: 'POST', path,
        http: response.status(), code: result.code, mechanism: 'real-UI-click' })
      return result.data
    } finally { await context.close() }
  })
}
function requirePhase(name) { assert.ok(state.phase_complete.includes(name), 'Required phase not complete: ' + name) }
async function current(status) {
  const asset = await api('assetstaff', '/api/asset/asset/' + state.asset.id)
  assert.equal(asset.status, status)
  assert.equal(asset.name, runId)
  assert.equal(asset.ownerUserId, teaching.users.assetstaff.id)
  assert.equal(asset.ownerUnitId, teaching.users.assetstaff.primaryUnitId)
  assert.equal(asset.purchaseAmount, '1000.00')
  state.asset = asset
  persist()
  return asset
}
try {
  if (phase === 'prepare') {
    const receipt = await api('buyer', '/api/procurement/goods-receipt/' + teaching.objects.receipt2.id)
    assert.equal(receipt.status, 'CONFIRMED')
    const original = await api('assetstaff', '/api/asset/asset/' + teaching.objects.assetCards[1].id)
    assert.equal(original.status, 'SCRAPPED')
    assert.equal(original.sourceGrId, receipt.id)
    assert.equal(original.sourcePoId, teaching.objects.purchaseOrder.id)
    assert.ok(receipt.lines.some(line => line.id === original.sourceGrLineId && line.qualityStatus === 'PASS' && line.assetManaged))
    assert.equal(original.purchaseAmount, '900.00')
    state.receipt_source = original
    await captureAll('asset-receipt-source-card', 'receipt-card', 'assetstaff', original,
      (page, row, locale) => detail(page, row, locale, original),
      { status: original.status, purchaseAmount: original.purchaseAmount, sourcePoNo: original.sourcePoNo, sourceGrNo: original.sourceGrNo,
        scope: 'Existing confirmed receipt linkage; this image is the current scrapped card, not the arrival-time state.' })
    const body = { name: runId, categoryCode: 'WIKI20261009A', purchaseAmount: '1000.00', currencyCode: 'CNY',
      ownerUserId: teaching.users.assetstaff.id, ownerUnitId: teaching.users.assetstaff.primaryUnitId,
      remark: 'Isolated documentation fixture; history retained until owned project cleanup.' }
    if (!state.operations.create) {
      const matches = await api('assetstaff', '/api/asset/asset/list?page=1&size=100&keyword=' + encodeURIComponent(runId))
      assert.equal(matches.records.length, 0, 'Reconcile existing matching fixture before creating')
    }
    state.asset = await command('create', 'assetstaff', '/api/asset/asset', body,
      () => api('assetstaff', '/api/asset/asset', 'POST', body))
    persist()
    const asset = await current('IN_STOCK')
    await captureAll('asset-manual-in-stock', 'ledger', 'assetstaff', asset,
      (page, row, locale) => detail(page, row, locale, asset), { status: asset.status, ownerUserId: asset.ownerUserId, ownerUnitId: asset.ownerUnitId })
  } else if (phase === 'source-visual') {
    requirePhase('prepare')
    const original = await api('assetstaff', '/api/asset/asset/' + state.receipt_source.id)
    assert.equal(original.status, 'SCRAPPED')
    assert.equal(original.sourceGrId, state.receipt_source.sourceGrId)
    await captureAll('asset-receipt-source-card-stable', 'receipt-card', 'assetstaff', original,
      (page, row, locale) => detail(page, row, locale, original),
      { status: original.status, sourcePoNo: original.sourcePoNo, sourceGrNo: original.sourceGrNo, scope: 'Current scrapped card with receipt linkage; no receipt replay.' })
  } else if (phase === 'allocate') {
    requirePhase('prepare')
    let asset = await current(state.operations.allocate ? 'ALLOCATED' : 'IN_STOCK')
    if (!state.operations.allocate) await captureAll('asset-allocation-form', 'allocation', 'assetstaff', asset,
      allocationForm, { targetUserId: teaching.users.requester.id, targetUnitId: teaching.users.requester.primaryUnitId, preview_only: true })
    const body = { version: state.operations.allocate?.input_version ?? asset.version,
      targetUserId: teaching.users.requester.id, targetUnitId: teaching.users.requester.primaryUnitId, remark: runId + ' allocate' }
    if (!state.operations.allocate) { state.allocation_version = asset.version; persist() }
    body.version = state.allocation_version
    await uiCommand('allocate', 'assetstaff', asset, '/api/asset/asset/' + asset.id + '/allocate', body,
      allocationForm, '.el-dialog:visible .el-dialog__footer .el-button--primary')
    asset = await current('ALLOCATED')
    assert.equal(asset.currentUserId, teaching.users.requester.id)
    assert.equal(asset.currentUnitId, teaching.users.requester.primaryUnitId)
    await captureAll('asset-allocation-pending', 'allocation', 'requester', asset, async (page, row, locale) => {
      await expect(row).toHaveCount(1)
      await expect(row).toContainText(label(locale, 'assetLedger.statusAllocated'))
      await expect(row.getByRole('button', { name: label(locale, 'assetLedger.accept'), exact: true })).toBeVisible()
    }, { status: asset.status, currentUserId: asset.currentUserId, scope: 'fixed current_user self-service' })
  } else if (phase === 'accept') {
    requirePhase('allocate')
    let asset = await current(state.operations.accept ? 'IN_USE' : 'ALLOCATED')
    if (!state.operations.accept) {
      await api('outsider', '/api/asset/asset/' + asset.id + '/accept', 'POST', { version: asset.version }, 404)
      const outside = await api('outsider', '/api/asset/asset/my?page=1&size=100&keyword=' + encodeURIComponent(asset.assetNo))
      assert.equal(outside.records.length, 0)
      state.accept_version = asset.version; persist()
      await captureAll('asset-accept-confirm', 'acceptance', 'requester', asset,
        (page, row, locale) => confirmation(page, row, locale, 'accept'), { status: asset.status, preview_only: true, actor: 'assigned user' })
    }
    await uiCommand('accept', 'requester', asset, '/api/asset/asset/' + asset.id + '/accept',
      { version: state.accept_version }, (page, row, locale) => confirmation(page, row, locale, 'accept'), '.el-message-box__btns .el-button--primary')
    asset = await current('IN_USE')
    await captureAll('asset-accepted-detail', 'acceptance', 'assetstaff', asset,
      (page, row, locale) => detail(page, row, locale, asset), { status: asset.status, currentUserId: asset.currentUserId })
  } else if (phase === 'return') {
    requirePhase('accept')
    let asset = await current(state.operations.return ? 'IN_STOCK' : 'IN_USE')
    if (!state.operations.return) {
      await api('requester', '/api/asset/asset/' + asset.id + '/return', 'POST', { version: state.accept_version }, 409)
      state.return_version = asset.version; persist()
      await captureAll('asset-return-confirm', 'return', 'requester', asset,
        (page, row, locale) => confirmation(page, row, locale, 'return'), { status: asset.status, preview_only: true })
    }
    await uiCommand('return', 'requester', asset, '/api/asset/asset/' + asset.id + '/return',
      { version: state.return_version }, (page, row, locale) => confirmation(page, row, locale, 'return'), '.el-message-box__btns .el-button--primary')
    asset = await current('IN_STOCK')
    assert.equal(asset.currentUserId, null)
    assert.equal(asset.currentUnitId, null)
    assert.equal(asset.allocatedTime, null)
    await captureAll('asset-returned-detail', 'return', 'assetstaff', asset,
      (page, row, locale) => detail(page, row, locale, asset),
      { status: asset.status, currentUserId: null, currentUnitId: null, allocatedTime: null, ownerUserId: asset.ownerUserId })
    await captureAll('asset-self-empty-after-return', 'return', 'requester', asset, async (page, row) => {
      await expect(row).toHaveCount(0)
      await expect(page.locator('.el-table__empty-block')).toBeVisible()
    }, { my_asset_count: 0, scope: 'same assigned user after return' })
  } else if (phase === 'final-visual') {
    requirePhase('close')
    const asset = await current('IN_STOCK')
    const original = await api('assetstaff', '/api/asset/asset/' + state.receipt_source.id)
    assert.equal(original.status, 'SCRAPPED')
    await captureAll('asset-final-receipt-source-card', 'receipt-card', 'assetstaff', original,
      (page, row, locale) => detail(page, row, locale, original), { status: original.status, sourcePoNo: original.sourcePoNo,
        sourceGrNo: original.sourceGrNo, scope: 'Current card and original immutable receipt linkage; no business replay.' })
    await captureAll('asset-final-returned-detail', 'ledger', 'assetstaff', asset,
      (page, row, locale) => detail(page, row, locale, asset), { status: asset.status, currentUserId: null,
        currentUnitId: null, history: ['IN_STOCK', 'ALLOCATED', 'IN_USE', 'IN_STOCK'], scope: 'Returned state and complete four-entry history.' })
  } else if (phase === 'close') {
    requirePhase('return')
    const asset = await current('IN_STOCK')
    const history = await api('assetstaff', '/api/asset/asset/' + asset.id + '/history?page=1&size=100')
    assert.deepEqual(history.records.map(item => item.toStatus).reverse(), ['IN_STOCK', 'ALLOCATED', 'IN_USE', 'IN_STOCK'])
    await api('assetstaff', '/api/asset/asset/' + asset.id + '?version=' + asset.version, 'DELETE', undefined, 409)
    await current('IN_STOCK')
    state.history = history
    state.cleanup = 'OWNED_MANUAL_ASSET_IN_STOCK_HISTORY_RETAINED'
    state.status = 'ASSET_USAGE_REAL_UI_PASS_HISTORY_RETAINED'
  }
  state.phase_complete.push(phase)
  persist()
  console.log('ASSET_USAGE_PHASE_PASS ' + phase + ' captures=' + state.captures.length)
} catch (error) {
  state.last_error = { at: new Date().toISOString(), phase, message: String(error.message).replace(/Bearer\s+\S+/g, 'Bearer [redacted]') }
  persist()
  console.error('ASSET_USAGE_PHASE_FAILED ' + phase + ': ' + state.last_error.message)
  process.exitCode = 1
} finally { await browser.close() }
