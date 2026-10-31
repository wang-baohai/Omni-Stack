/** 只读采集公开Wiki首页四语原图；只在本机唯一Docker环境运行，不需要测试身份。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, lstatSync, mkdirSync, readFileSync, realpathSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

const { chromium } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
assert.ok(existsSync('/.dockerenv'), '仅在本机Docker中执行')
assert.ok(!existsSync('/var/run/docker.sock'), '页面验证角色不得挂载Docker socket')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_PLATFORM_PROJECT, 'omni-stack')
assert.equal(process.env.E2E_PLATFORM_PRESET, 'full')
const base = process.env.E2E_BASE_URL || 'http://omni-frontend:3000'
assert.equal(base, 'http://omni-frontend:3000', '只访问本次唯一环境的标准前端端口')
const image = process.env.E2E_RUNTIME_IMAGE_ID
assert.match(image ?? '', /^sha256:[a-f0-9]{64}$/)
const output = resolve(process.env.E2E_DOCS_IMAGE_DIR || '')
assert.ok(output.startsWith('/validation/evidence/'), '原图仅保存到固定验证卷的证据目录')
assert.equal(realpathSync('/validation/evidence'), '/validation/evidence')
let current = '/validation/evidence'
for (const part of output.slice(current.length + 1).split('/')) {
  current += '/' + part
  if (existsSync(current)) assert.ok(!lstatSync(current).isSymbolicLink(), '拒绝证据目录符号链接')
}
mkdirSync(output, { recursive: true })
assert.equal(realpathSync(output), output)
const receipt = resolve(output, 'capture.json')
assert.ok(!existsSync(receipt), '不覆盖已有取证回执，请指定新的证据批次')
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
// 用户指定的阅读顺序，工程事实独立插在启动之后；编号不承担显示排序。
const readingOrder = ['WIKI-01', 'WIKI-03', 'WIKI-02', 'WIKI-04', 'engineering', 'WIKI-09', 'WIKI-08',
  'WIKI-06', 'WIKI-07', 'WIKI-11', 'WIKI-12', 'WIKI-13', 'WIKI-05', 'WIKI-10']
const report = { fixture: 'omni-stack/public-wiki-home', at: new Date().toISOString(), workers: 1,
  preset: 'full', project: 'omni-stack',
  script: 'scripts/fixtures/capture-wiki-home.mjs', script_sha256: sha(readFileSync(new URL(import.meta.url))),
  runtime_image_id: image, identity_source: 'anonymous-no-token', human_captcha_login_claimed: false,
  response_mocked: false, display_dom_modified: false, screenshots: [], errors: [] }
const save = () => writeFileSync(receipt, JSON.stringify(report, null, 2) + '\n')
const browser = await chromium.launch({ args: ['--no-sandbox'] })
try {
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const directory = resolve(output, locale); mkdirSync(directory, { recursive: true })
    const imagePath = resolve(directory, 'platform-site-home.png')
    assert.ok(!existsSync(imagePath), '不覆盖已有原图')
    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, locale })
    await context.addInitScript(locale => localStorage.setItem('omni-lang', locale), locale)
    const page = await context.newPage(), privateRequests = []
    page.on('pageerror', error => report.errors.push({ locale, message: error.message }))
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/')) privateRequests.push(new URL(request.url()).pathname) })
    await page.goto(base, { waitUntil: 'networkidle' })
    assert.equal(new URL(page.url()).pathname, '/')
    assert.equal(await page.evaluate(() => localStorage.getItem('token')), null)
    assert.equal(await page.locator('iframe.home-wiki-frame').getAttribute('src'), `/wiki/${locale}/wiki/index.html`)
    const frame = page.frameLocator('iframe.home-wiki-frame')
    await frame.locator('.vp-doc h1').waitFor({ state: 'visible' })
    await page.locator('.home-wiki [role="status"]').waitFor({ state: 'hidden' })
    assert.equal(await page.locator('.home-wiki [role="alert"]').count(), 0)
    assert.equal(await frame.locator('html').getAttribute('lang'), locale)
    assert.equal(await frame.locator('.vp-doc table').first().locator('tbody tr').count(), 14)
    const text = JSON.parse(readFileSync(resolve(`docs/.vitepress/locales/${locale}.json`), 'utf8'))
    const expectedTitles = readingOrder.map(id => id === 'engineering' ? text.truth : text.chapters[id])
    assert.deepEqual((await frame.locator('.VPSidebar h2').allTextContents()).map(title => title.trim()), expectedTitles)
    assert.equal(await frame.locator('html').evaluate(root => root.scrollWidth > innerWidth + 1), false)
    await frame.locator('html').evaluate(() => document.fonts.ready)
    await page.evaluate(() => document.fonts.ready)
    assert.deepEqual(privateRequests, [])
    await page.screenshot({ path: imagePath, animations: 'disabled' })
    report.screenshots.push({ locale, image: imagePath, route: '/', role: 'anonymous', viewport: page.viewportSize(),
      captured_at: new Date().toISOString(), image_sha256: sha(readFileSync(imagePath)),
      wiki_body_sha256: sha(await frame.locator('.vp-doc').innerText()), wiki_chapters: 13, reading_groups: 14,
      reading_order: readingOrder, preset: 'full',
      header: await page.locator('.home-header').innerText(), actual_visual_review: 'PENDING' })
    save(); await context.close()
  }
  assert.deepEqual(report.errors, []); report.result = 'CAPTURED_NOT_AI_REVIEWED'; save()
  console.log(JSON.stringify({ result: report.result, screenshots: report.screenshots.length, receipt }))
} catch (error) { report.result = 'FAIL'; report.error = error.message; save(); throw error }
finally { await browser.close() }
