/** 在唯一容器浏览器中只读采集现有 XXL-JOB 原生页签；凭据只从环境进入内存。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'), '页面采集容器不得挂载 Docker socket')
const require = createRequire('/validation/source/omni-frontend/package.json')
const { chromium } = require('playwright')
const output = process.argv[2]
assert.ok(output, '需要固定验证卷内的私有输出路径')
const directory = resolve(output)
assert.ok(directory.startsWith('/validation/') && !existsSync(directory), '不覆盖既有证据，不使用其他临时目录')
const base = new URL(process.env.E2E_XXL_ADMIN_URL || 'http://xxl-job-admin:8080/xxl-job-admin/')
assert.equal(base.protocol, 'http:')
assert.ok(['xxl-job-admin', 'localhost', '127.0.0.1'].includes(base.hostname))
assert.equal(base.pathname, '/xxl-job-admin/')
assert.ok(!base.username && !base.password && !base.search && !base.hash)
assert.ok(process.env.E2E_XXL_ADMIN_USERNAME && process.env.E2E_XXL_ADMIN_PASSWORD)
mkdirSync(directory, { recursive: true })
writeFileSync(resolve(directory, 'source-used.mjs'), readFileSync(new URL(import.meta.url)))
const report = { started_at: new Date().toISOString(), workers: 1, language: 'zh-CN', images: [], page_errors: [],
  capture_script: 'scripts/fixtures/capture-xxl-job-admin.mjs',
  capture_script_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'),
  identity_source: 'local dedicated scheduling-admin configuration', business_mutations: false }
const browser = await chromium.launch({ args: ['--no-sandbox'] })
const context = await browser.newContext({ locale: 'zh-CN', viewport: { width: 1920, height: 1080 } })
const page = await context.newPage()
page.on('pageerror', error => report.page_errors.push(error.message))
const hash = bytes => createHash('sha256').update(bytes).digest('hex')
try {
  await page.goto(base.href, { waitUntil: 'networkidle' })
  await page.locator('[name="userName"]').fill(process.env.E2E_XXL_ADMIN_USERNAME)
  await page.locator('[name="password"]').fill(process.env.E2E_XXL_ADMIN_PASSWORD)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(url => !url.pathname.includes('/login'))
  for (const [label, slug, name] of [['运行报表', 'dashboard', 'dashboard'], ['任务管理', 'jobinfo', 'tasks'],
    ['调度日志', 'joblog', 'logs'], ['执行器管理', 'jobgroup', 'executors']]) {
    await page.locator('.sidebar-menu a').filter({ hasText: label }).click()
    await page.waitForTimeout(1800)
    const frame = page.frames().find(candidate => new URL(candidate.url()).pathname === base.pathname + slug)
    assert.ok(frame, '原生页签未打开：' + slug)
    await frame.waitForLoadState('networkidle')
    await frame.evaluate(() => document.fonts.ready)
    const path = resolve(directory, 'xxl-admin-' + name + '.png')
    await page.screenshot({ path })
    report.images.push({ path, route: base.pathname + slug, captured_at: new Date().toISOString(), viewport: page.viewportSize(),
      image_sha256: hash(readFileSync(path)), mocks: false, ai_review: 'PENDING_ACTUAL_IMAGE_REVIEW' })
  }
  assert.deepEqual(report.page_errors, [])
  report.result = 'PASS_READONLY_NATIVE_CAPTURE_NOT_BUSINESS_EXECUTION_ACCEPTANCE'
} catch (error) {
  report.result = 'FAIL'; report.error = error.message; process.exitCode = 1
} finally {
  report.finished_at = new Date().toISOString()
  writeFileSync(resolve(directory, 'receipt.json'), JSON.stringify(report, null, 2) + '\n', { mode: 0o600 })
  await browser.close()
}
console.log(JSON.stringify({ result: report.result, images: report.images.length, output: directory }))
