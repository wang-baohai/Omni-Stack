/** 在线用户四语实拍：只读，截图遮盖实际会话标识，不改变页面或会话状态。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext } from './goal-local-identities.mjs'
assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^platform-online-r[1-9][0-9]*$/)
const dir = '/validation/evidence/codex-2026-10-22/wp22-s03/' + run
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = raw => createHash('sha256').update(raw).digest('hex'), source = readFileSync(new URL(import.meta.url))
writeFileSync(dir + '/source-used.mjs', source)
const report = { original_step: 'WP-22.S03', started_at: new Date().toISOString(), workers: 1,
  script: 'scripts/fixtures/capture-platform-online-tutorial.mjs', script_sha256: sha(source),
  runtime_image: process.env.E2E_RUNTIME_IMAGE_ID, identity_source: 'test-fixture',
  human_captcha_login_claimed: false, response_mocked: false, display_dom_modified: false,
  business_write_performed: false, images: [], page_errors: [], blocked_mutations: [] }
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const token = createLocalContext().bootstrap(), browser = await chromium.launch({ args: ['--no-sandbox'] })
try {
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const context = await browser.newContext({ locale, viewport: { width: 1920, height: 1080 } })
    context.on('page', page => page.on('pageerror', error => { report.page_errors.push(error.message); save() }))
    await context.addInitScript(({ token, locale }) => {
      if (location.origin !== 'http://omni-frontend:3000') return
      localStorage.setItem('token', token); localStorage.setItem('username', 'admin')
      localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
    }, { token, locale })
    await context.route('**/api/**', async route => {
      if (['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) return route.continue()
      report.blocked_mutations.push({ method: route.request().method(), path: new URL(route.request().url()).pathname }); save(); return route.abort()
    })
    const page = await context.newPage()
    await page.goto('http://omni-frontend:3000/admin/system/online', { waitUntil: 'networkidle' })
    await page.locator('.el-table').waitFor(); assert.equal(await page.locator('.console-header__apps').count(), 0)
    const headers = page.locator('.el-table__header-wrapper th.el-table__cell').filter({ hasText: /Token|トークン|토큰/i })
    assert.equal(await headers.count(), 1)
    const column = (await headers.getAttribute('class')).split(/\s+/).find(value => /^el-table_\d+_column_\d+$/.test(value))
    assert.ok(column)
    const selector = '.el-table__body-wrapper td.' + column + ' .cell'
    await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(350)
    const path = `${dir}/platform-online-${locale}.png`
    await page.screenshot({ path, mask: [page.locator(selector)], maskColor: '#ccd4df' })
    report.images.push({ path, name: 'platform-online', locale, role: 'admin', route: '/admin/system/online',
      viewport: page.viewportSize(), captured_at: new Date().toISOString(), sha256: sha(readFileSync(path)),
      masks: [{ field: 'session-token-id', selector, reason: 'Do not publish active session identifiers; actual page and session remain unchanged' }],
      actual_visual_review: 'PENDING', business_write_performed: false }); save()
    await context.close()
  }
  assert.equal(report.images.length, 4); assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, [])
  report.result = 'CAPTURED_FOUR_REAL_SESSION_MASKED_IMAGES_PENDING_ACTUAL_AI_VIEW'
} catch (error) { report.result = 'FAIL'; report.error = error.message; process.exitCode = 1 }
finally { report.finished_at = new Date().toISOString(); save(); await browser.close() }
console.log(JSON.stringify({ result: report.result, images: report.images.length, evidence: dir, error: report.error?.slice(0, 360) }))
