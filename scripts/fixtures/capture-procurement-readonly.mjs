/** 采购只读取证：复用隔离教学数据，规则试算不提交请购或变更配置。 */
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
const base = 'http://omni-frontend:3000'
const sha = value => createHash('sha256').update(value).digest('hex')
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const { chromium, expect } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
/** 令牌、运行快照和原图只使用仓库外显式路径。 */
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), name + ' requires an absolute private path')
  assert.ok(relative(process.cwd(), resolve(value)).startsWith('..' + sep), name + ' must be outside repository')
  return resolve(value)
}
assert.ok(existsSync('/.dockerenv'), 'Local Docker is required')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_PROCUREMENT_PROJECT, project)
const teaching = JSON.parse(readFileSync(outside('E2E_TEACHING_STATE_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const runtime = JSON.parse(readFileSync(outside('E2E_PROCUREMENT_RUNTIME_FILE'), 'utf8').replace(/^\uFEFF/, ''))
const receiptPath = outside('E2E_PROCUREMENT_RECEIPT_FILE')
outside('E2E_DOCS_IMAGE_DIR')
assert.ok(!existsSync(receiptPath), 'Keep previous attempt receipts; choose a new explicit path')
assert.equal(teaching.project, project)
assert.equal(runtime.project, project)
assert.equal(runtime.containers.length, 24)
assert.ok(runtime.containers.every(item => item.ports === 0))
assert.ok(teaching.users.manager.roleCodes.includes('PROCUREMENT_MANAGER'))
const token = tokens.managerToken
assert.equal(typeof token, 'string')
const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
assert.ok(claims.exp * 1000 > Date.now() + 120_000, 'Refresh expired fixture identity')
assert.ok(!claims.roles.includes('SUPER_ADMIN'), 'Use an ordinary manager')
const sourceFiles = ['scripts/fixtures/capture-procurement-readonly.mjs', 'omni-frontend/e2e-docs/fixtures/docs-page.ts',
  'omni-frontend/src/views/procurement/material/index.vue', 'omni-frontend/src/views/procurement/overview/index.vue',
  'omni-frontend/src/views/procurement/approval-route/index.vue',
  'omni-frontend/src/views/procurement/approval-route/components/RuleMatchTester.vue',
  'omni-frontend/src/layout/index.vue', 'omni-frontend/src/stores/app.ts',
  'omni-frontend/src/api/procurement-material.ts', 'omni-frontend/src/api/procurement-overview.ts',
  'omni-frontend/src/api/procurement-approval-route.ts']
const sources = sourceFiles.map(path => ({ path, sha256: sha(readFileSync(path)) }))
const receipt = { schema: 1, project, baseline: teaching.baseline, started_at: new Date().toISOString(),
  state: 'RUNNING_READ_ONLY', runtime, sources, source_sha256: sha(JSON.stringify(sources)),
  actor: 'manager', roles: teaching.users.manager.roleCodes, observations: [], captures: [],
  mutations: [], rule_previews: [], mocks: false, cleanup: 'NO_BUSINESS_WRITES' }
/** 原子保存观察结果，断流后不把已完成的截图或请求当作未知写入。 */
function persist() {
  mkdirSync(dirname(receiptPath), { recursive: true })
  writeFileSync(receiptPath + '.next', JSON.stringify(receipt, null, 2) + '\n')
  renameSync(receiptPath + '.next', receiptPath)
}
function label(locale, path) {
  let value = dictionaries[locale]
  for (const key of path.split('.')) value = value?.[key]
  assert.equal(typeof value, 'string', locale + ' missing ' + path)
  return value
}
async function get(path) {
  const response = await fetch(base + path, { headers: { Authorization: 'Bearer ' + token, 'X-Tenant-Id': '1' },
    signal: AbortSignal.timeout(30_000) })
  const body = await response.json()
  receipt.observations.push({ at: new Date().toISOString(), path, method: 'GET', http: response.status, code: body.code })
  persist()
  assert.equal(response.status, 200)
  assert.equal(body.code, 200, path)
  return body.data
}
async function snapshot() {
  const materials = await get('/api/procurement/material/list?page=1&size=100&keyword=WIKI-20261009A&categoryId=' + teaching.objects.category.id)
  const rules = await get('/api/procurement/approval-route/list?page=1&size=100&keyword=WIKI-20261009A')
  const summary = await get('/api/procurement/overview/summary')
  return { materials, rules, summary }
}
const before = await snapshot()
assert.ok(before.materials.records.some(item => item.id === teaching.objects.materialA.id && item.assetManaged === true))
assert.ok(before.materials.records.some(item => item.id === teaching.objects.materialB.id && item.assetManaged === false))
assert.ok(before.rules.records.some(item => item.id === teaching.objects.route.id && item.status === 'ACTIVE'))
receipt.before_sha256 = sha(JSON.stringify(before))
const browser = await chromium.launch()
receipt.tools = { node: process.version, playwright: createRequire(resolve('omni-frontend/package.json'))('@playwright/test/package.json').version,
  chromium: browser.version() }
/** 请求守卫只放行读取与服务端无持久化的规则试算；不替换任何响应。 */
async function guard(page) {
  await page.route('**/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (['GET', 'HEAD', 'OPTIONS'].includes(request.method()) ||
      (request.method() === 'POST' && path === '/api/procurement/approval-route/match-preview')) {
      await route.continue()
    } else {
      receipt.mutations.push({ method: request.method(), path, blocked: true })
      persist()
      await route.abort('blockedbyclient')
    }
  })
}
async function capture(locale, name, route, viewport, prepare, expected) {
  const context = await browser.newContext({ baseURL: base, viewport, timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce' })
  try {
    const page = await context.newPage()
    await guard(page)
    await prepareDocsPage(page, { locale, token, username: teaching.users.manager.username })
    await page.goto(route)
    await waitForDocsPage(page, 'main')
    await expect(page).not.toHaveURL(/login|menu-load-error/)
    if (viewport.width < 768) {
      await expect(page.locator('.sidebar')).toHaveClass(/sidebar--collapsed/)
      await page.locator('.header .collapse-btn').click()
      await expect(page.locator('.sidebar')).not.toHaveClass(/sidebar--collapsed/)
      await page.locator('.header .collapse-btn').click()
      await expect(page.locator('.sidebar')).toHaveClass(/sidebar--collapsed/)
    }
    await prepare(page)
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
    await waitForDocsPage(page, 'main')
    await captureDocsImage(page, locale, name)
    const image = resolve(process.env.E2E_DOCS_IMAGE_DIR, locale, name + '.png')
    receipt.captures.push({ locale, name, route, viewport, expected, image, sha256: sha(readFileSync(image)),
      captured_at: new Date().toISOString(), test_sha256: sources[0].sha256,
      source_sha256: receipt.source_sha256, visual_review: 'pending' })
    persist()
  } finally { await context.close() }
}
try {
  for (const locale of docsLocales) {
    const desktop = { width: 1920, height: 1080 }
    await capture(locale, 'procurement-current-material', '/admin/procurement/material', desktop, async page => {
      await page.locator('.category-panel .el-tree').getByText(teaching.objects.category.categoryName, { exact: true }).click()
      const keyword = page.locator('.query-form input').first()
      await keyword.fill('WIKI-20261009A')
      await keyword.press('Enter')
      const table = page.locator('.material-panel .el-table')
      await expect(table).toContainText(teaching.objects.materialA.materialCode)
      await expect(table).toContainText(teaching.objects.materialB.materialCode)
      await expect(table.locator('.el-table__body-wrapper tr')).toHaveCount(2)
    }, ['Two ACTIVE materials in same teaching category; asset-managed true and false'])
    for (const [suffix, viewport] of [['', desktop], ['-mobile', { width: 390, height: 844 }], ['-tablet', { width: 1024, height: 768 }]]) {
      await capture(locale, 'procurement-current-rule-match' + suffix, '/admin/procurement/approval-route', viewport, async page => {
        const card = page.locator('.match-tester')
        await card.locator('.el-select').click()
        await page.locator('.el-select-dropdown__item:visible').filter({ hasText: teaching.objects.category.categoryName }).click()
        await card.locator('.el-input input').fill('4000')
        const button = card.getByRole('button', { name: label(locale, 'procurementApprovalRules.test'), exact: true })
        const [response] = await Promise.all([
          page.waitForResponse(item => item.url().includes('/api/procurement/approval-route/match-preview') && item.request().method() === 'POST'),
          button.click(),
        ])
        const body = await response.json()
        assert.equal(body.code, 200)
        assert.equal(body.data.outcome, 'MATCHED')
        assert.equal(body.data.routeId, teaching.objects.route.id)
        assert.equal(body.data.approvalGraph.modelVersion.availability, 'AVAILABLE')
        receipt.rule_previews.push({ locale, viewport, http: response.status, code: body.code,
          outcome: body.data.outcome, route_id: body.data.routeId, model_version: body.data.modelVersion,
          availability: body.data.approvalGraph.modelVersion.availability, amount: '4000' })
        await expect(card.locator('.match-result')).toContainText(label(locale, 'procurementApprovalRules.matched'))
        await expect(card).toContainText(teaching.objects.route.routeName)
        await expect(card.locator('.el-input input')).toHaveValue('4000')
        await card.scrollIntoViewIfNeeded()
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 2), 'Page must not overflow horizontally')
        await expect(button).toBeVisible()
      }, ['Real UI rule preview MATCHED; selected category/4000; correct active route and AVAILABLE model; no persisted write'])
    }
    await capture(locale, 'procurement-current-overview', '/admin/procurement/overview', desktop, async page => {
      const overview = page.locator('.procurement-overview-page')
      await expect(overview.locator('.metric-value')).toHaveCount(4)
      await expect(overview).toContainText(label(locale, 'procurementOverviewPage.title'))
      await expect(overview).toContainText('CNY')
      await expect(overview).toContainText('3500')
    }, ['Current manager data scope; order status and CNY committed amount; no cross-currency sum'])
  }
  const after = await snapshot()
  receipt.after_sha256 = sha(JSON.stringify(after))
  assert.deepEqual(after, before, 'Material/rule/summary changed during read-only capture')
  assert.equal(receipt.mutations.length, 0, 'No attempted persisted writes allowed')
  assert.equal(receipt.captures.length, 20)
  assert.equal(receipt.rule_previews.length, 12)
  receipt.state = 'FOUR_LANGUAGE_READONLY_CAPTURE_PASS'
  receipt.completed_at = new Date().toISOString()
  persist()
  process.stdout.write('PROCUREMENT_READONLY_PASS captures=20 previews=12 persisted_writes=0\n')
} catch (error) {
  receipt.state = 'FAILED_READONLY_ATTEMPT'
  receipt.failure = { at: new Date().toISOString(), message: error.message }
  persist()
  throw error
} finally { await browser.close() }
