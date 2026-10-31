import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext } from './goal-local-identities.mjs'

// 统一日志/MQ教程的只读实拍入口：唯一Docker环境、固定验证卷、显式测试身份。
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^unified-support-images-r[1-9][0-9]*$/)
const output = `/validation/evidence/codex-2026-10-22/wp22-s03/${run}`
assert.ok(!existsSync(output)); mkdirSync(output, { recursive: true })
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
const report = { original_step: 'WP-22.S03', workers: 1, source_baseline: 'dbb3e2156a2afe38829cdb2a779ab0914f6f8d2b',
  runtime_image: process.env.E2E_RUNTIME_IMAGE_ID, identity_source: 'test-fixture', actor: 'existing local admin:1',
  test_sha256: hash(readFileSync(new URL(import.meta.url))), images: [], checks: [], blocked_mutations: [], page_errors: [],
  observability_stack_started: false, note: '实拍应用操作日志与MQ管理，不冒称Grafana/Loki已运行，也不证明新投递或重发。' }
const save = () => writeFileSync(output + '/result.json', JSON.stringify(report, null, 2) + '\n')
const local = createLocalContext(); const token = local.bootstrap()
const browser = await chromium.launch({ args: ['--no-sandbox'] }); let currentPage
try {
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const context = await browser.newContext({ locale, viewport: { width: 1440, height: 900 } })
    context.setDefaultTimeout(15000)
    context.on('page', page => page.on('pageerror', error => { report.page_errors.push(error.message); save() }))
    await context.route('**/api/**', async route => {
      if (['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) await route.continue()
      else { report.blocked_mutations.push({ method: route.request().method(), path: new URL(route.request().url()).pathname }); save(); await route.abort() }
    })
    const page = await context.newPage(); currentPage = page
    await page.goto('http://omni-frontend:3000/', { waitUntil: 'networkidle' })
    await page.evaluate(({ token, locale }) => {
      localStorage.setItem('token', token); localStorage.setItem('username', 'admin'); localStorage.setItem('omni-lang', locale)
    }, { token, locale })
    for (const [kind, route, endpoint] of [
      ['unified-logging-operlog', '/admin/base/operlog', '/api/base/oper-log/list'],
      ['unified-mq-records', '/admin/base/mqmessage', '/api/base/mq-message/list'],
    ]) {
      const response = page.waitForResponse(res => new URL(res.url()).pathname === endpoint)
      await page.goto('http://omni-frontend:3000' + route, { waitUntil: 'networkidle' })
      const loaded = await response; const json = await loaded.json()
      assert.equal(loaded.status(), 200); assert.equal(json.code, 200)
      await page.locator('.el-table').waitFor()
      await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(300)
      const path = `${output}/${kind}-${locale}.png`; await page.screenshot({ path })
      report.images.push({ path, name: kind, locale, route, module: kind.startsWith('unified-logging') ? 'logging' : 'mq',
        width: 1440, height: 900, sha256: hash(readFileSync(path)), captured_at: new Date().toISOString(),
        record_count_in_response: json.data?.total ?? null, fixture: 'omni-stack/goal_20261022/explicit-test-session' })
      report.checks.push({ locale, route, http_status: 200, code: 200, result: 'PASS_READONLY_EXISTING_STATE' }); save()
    }
    await context.close()
  }
  assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, [])
  report.result = 'CAPTURED_8_REAL_IMAGES_AI_REVIEW_PENDING'; save()
  console.log(JSON.stringify({ result: report.result, images: report.images.length }))
} catch (error) {
  report.result = 'FAIL'; report.error = error.message
  if (currentPage && !currentPage.isClosed()) { const path = output + '/actual-failure.png'; await currentPage.screenshot({ path }); report.failure_image = { path, sha256: hash(readFileSync(path)) } }
  save(); throw error
} finally { await browser.close() }
