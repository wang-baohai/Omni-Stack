/** 平台公开入口教程实拍：匿名只读，不提交认证、注册、授权或门户业务表单。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^platform-public-r[1-9][0-9]*$/)
const changedPortalLinksOnly = process.argv[3] === '--changed-portal-links-only'
assert.ok(process.argv[3] === undefined || changedPortalLinksOnly)
const dir = '/validation/evidence/codex-2026-10-22/wp22-s03/' + run
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = raw => createHash('sha256').update(raw).digest('hex'), source = readFileSync(new URL(import.meta.url))
writeFileSync(dir + '/source-used.mjs', source)
const report = { original_step: 'WP-22.S03', started_at: new Date().toISOString(), workers: 1,
  script: 'scripts/fixtures/capture-platform-public-tutorial.mjs', script_sha256: sha(source),
  runtime_image: process.env.E2E_RUNTIME_IMAGE_ID, identity_source: 'anonymous-no-token',
  human_captcha_login_claimed: false, response_mocked: false, display_dom_modified: false,
  business_write_performed: false, images: [], page_errors: [], blocked_mutations: [] }
report.capture_scope = changedPortalLinksOnly ? 'Only two changed JA/KR portal-register links; other public-page evidence is reused separately' : 'All seven public entry states in four languages'
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const scenes = [['login', '/login'], ['register', '/register'], ['portal-login', '/portal-login'],
  ['portal-register', '/portal-register'], ['device', '/device'], ['consent', '/consent'], ['not-found', '/this-path-does-not-exist']]
const browser = await chromium.launch({ args: ['--no-sandbox'] })
try {
  for (const locale of changedPortalLinksOnly ? ['ja-JP', 'ko-KR'] : ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    for (const [scene, route] of changedPortalLinksOnly ? scenes.filter(([name]) => name === 'portal-register') : scenes) {
      const context = await browser.newContext({ locale, viewport: { width: 1920, height: 1080 } })
      context.on('page', page => page.on('pageerror', error => { report.page_errors.push({ locale, scene, error: error.message }); save() }))
      await context.addInitScript(locale => {
        if (location.origin !== 'http://omni-frontend:3000') return
        localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
      }, locale)
      await context.route('**/api/**', async request => {
        if (['GET', 'HEAD', 'OPTIONS'].includes(request.request().method())) return request.continue()
        report.blocked_mutations.push({ locale, scene, method: request.request().method(), path: new URL(request.request().url()).pathname })
        save(); return request.abort()
      })
      const page = await context.newPage()
      await page.goto('http://omni-frontend:3000' + route, { waitUntil: 'networkidle' })
      assert.equal(new URL(page.url()).pathname, route)
      assert.equal(await page.evaluate(() => localStorage.getItem('token')), null)
      await page.evaluate(() => document.fonts.ready)
      await page.waitForTimeout(350)
      const body = await page.locator('body').innerText()
      if (scene === 'not-found') {
        await page.locator('.status-page .el-result__title').filter({ hasText: /^404\s/ }).waitFor()
        assert.equal(await page.locator('.status-page .el-result__extra .el-button').count(), 2)
      } else assert.ok(body.trim().length > 40)
      if (changedPortalLinksOnly) await page.getByRole('link', { name: locale === 'ja-JP' ? 'ログイン' : '로그인', exact: true }).waitFor()
      const image = `${dir}/platform-${scene}-${locale}.png`
      await page.screenshot({ path: image })
      writeFileSync(`${dir}/${scene}-${locale}-body.txt`, body)
      report.images.push({ path: image, name: 'platform-' + scene, locale, role: 'anonymous', route,
        viewport: page.viewportSize(), masks: [], captured_at: new Date().toISOString(), sha256: sha(readFileSync(image)),
        actual_visual_review: 'PENDING', business_write_performed: false,
        limit: scene === 'device' ? 'Actual newly generated short-lived code/QR waiting state, not completed device confirmation'
          : scene === 'consent' ? 'Actual anonymous pre-login entry, not a scope list or completed authorization' : 'Actual public entry or error page; form not submitted' }); save()
      await context.close()
    }
  }
  assert.equal(report.images.length, changedPortalLinksOnly ? 2 : 28); assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, [])
  report.result = changedPortalLinksOnly ? 'CAPTURED_TWO_CHANGED_REAL_PORTAL_LINK_IMAGES_PENDING_ACTUAL_AI_VIEW' : 'CAPTURED_28_REAL_PUBLIC_IMAGES_PENDING_ACTUAL_AI_VIEW'
} catch (error) { report.result = 'FAIL'; report.error = error.message; process.exitCode = 1 }
finally { report.finished_at = new Date().toISOString(); save(); await browser.close() }
console.log(JSON.stringify({ result: report.result, images: report.images.length, evidence: dir, error: report.error?.slice(0, 360) }))
