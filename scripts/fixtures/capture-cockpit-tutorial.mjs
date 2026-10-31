/** 只读采集统一驾驶舱与个人任务表单，保留原数据；不提交任务或审批。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext } from './goal-local-identities.mjs'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^cockpit-tutorial-images-r[1-9][0-9]*$/)
const dir = '/validation/evidence/codex-2026-10-22/wp22-s03/' + run
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = value => createHash('sha256').update(value).digest('hex')
const report = { original_step: 'WP-22.S03', started_at: new Date().toISOString(), workers: 1,
  runtime_image: process.env.E2E_RUNTIME_IMAGE_ID, script: 'scripts/fixtures/capture-cockpit-tutorial.mjs',
  script_sha256: sha(readFileSync(new URL(import.meta.url))), identity_source: 'test-fixture',
  human_captcha_login_claimed: false, response_mocked: false, display_dom_modified: false,
  images: [], checks: [], page_errors: [], blocked_mutations: [] }
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const local = createLocalContext(); const token = local.bootstrap()
const types = await local.api('/api/base/my-job/types', { token })
const water = types.find(type => type.typeCode === 'Task-00001'); assert.ok(water?.typeName)
const browser = await chromium.launch({ args: ['--no-sandbox'] })
async function capture(page, name, locale) {
  if (await page.locator('.el-notification').count()) await page.locator('.el-notification').last().waitFor({ state: 'hidden' })
  await page.evaluate(() => document.fonts.ready); await page.waitForTimeout(350)
  const path = `${dir}/${name}-${locale}.png`; await page.screenshot({ path })
  report.images.push({ path, name, locale, route: new URL(page.url()).pathname + new URL(page.url()).hash,
    captured_at: new Date().toISOString(), viewport: page.viewportSize(), sha256: sha(readFileSync(path)),
    actual_visual_review: 'PENDING', business_write_performed: false }); save()
}
try {
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const context = await browser.newContext({ locale, viewport: { width: 1920, height: 1080 } })
    context.setDefaultTimeout(20000)
    context.on('page', page => page.on('pageerror', error => { report.page_errors.push(error.message); save() }))
    await context.route('**/api/**', async route => {
      const method = route.request().method()
      if (['GET', 'HEAD', 'OPTIONS'].includes(method)) return route.continue()
      report.blocked_mutations.push({ method, path: new URL(route.request().url()).pathname }); save(); return route.abort()
    })
    const page = await context.newPage()
    await page.goto('http://omni-frontend:3000/', { waitUntil: 'networkidle' })
    await page.evaluate(({ token, locale }) => {
      localStorage.setItem('token', token); localStorage.setItem('username', 'admin')
      localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
    }, { token, locale })
    await page.goto('http://omni-frontend:3000/workspace/overview', { waitUntil: 'networkidle' })
    await page.locator('#approvals .el-tabs').waitFor(); await page.locator('#my-tasks .el-table').waitFor()
    assert.equal(await page.locator('.console-header__apps').count(), 1)
    await capture(page, 'platform-workspace', locale)
    for (const [selector, name] of [['#approvals', 'platform-approvals'], ['#my-tasks', 'platform-jobs']]) {
      const panel = page.locator(selector)
      await panel.evaluate(element => element.scrollIntoView({ block: 'start', behavior: 'instant' }))
      const box = await panel.boundingBox(); assert.ok(box)
      assert.ok(Math.min(box.y + box.height, 1080) - Math.max(box.y, 0) >= Math.min(300, box.height))
      await capture(page, name, locale)
    }
    const create = page.locator('#my-tasks .ws-card-header .el-button'); assert.equal(await create.count(), 1)
    await create.click()
    const dialog = page.locator('.el-dialog:visible'); await dialog.waitFor()
    assert.ok(await dialog.locator('.el-form-item').count() >= 3)
    await dialog.locator('.el-form-item').nth(0).locator('input').fill('Wiki reminder preview')
    await dialog.locator('.el-form-item').nth(1).locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: water.typeName }).click()
    await dialog.locator('.dynamic-form-renderer .dynamic-form-field').first().waitFor()
    await capture(page, 'xxl-job-create-form', locale)
    await dialog.locator('.el-dialog__headerbtn').click(); await dialog.waitFor({ state: 'hidden' })
    report.checks.push({ locale, result: 'PASS_READONLY_PANELS_AND_REAL_DYNAMIC_FORM', expected_type: 'Task-00001',
      limit: '表单仅填写未提交，现有任务仅读；不证明新增、执行、审批或删除闭环。' }); save()
    await context.close()
  }
  assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, [])
  report.result = 'CAPTURED_16_REAL_IMAGES_PENDING_ACTUAL_AI_VIEW'
} catch (error) { report.result = 'FAIL'; report.error = error.message; process.exitCode = 1 }
finally { report.finished_at = new Date().toISOString(); save(); await browser.close() }
console.log(JSON.stringify({ result: report.result, images: report.images.length, evidence: dir }))
