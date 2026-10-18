/** 在本机 Docker 内采集第三方可观测性页面原图，凭据只保留在进程内存。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.ok(existsSync('/.dockerenv'), 'Capture must run inside local Docker')
const [mode = 'dashboard', outputDirectory] = process.argv.slice(2)
assert.ok(['dashboard', 'alert', 'alert-controls'].includes(mode), 'Expected dashboard, alert or alert-controls')
assert.ok(outputDirectory, 'Provide a private output directory')
const output = resolve(outputDirectory, `observability-ui-${mode}.json`)
assert.ok(!existsSync(output), 'Existing receipt: inspect it before repeating capture')
const require = createRequire(new URL('../../omni-frontend/package.json', import.meta.url))
const { chromium } = require('playwright')
const digest = (bytes) => createHash('sha256').update(bytes).digest('hex')
const locales = [['zh-CN', 'zh-Hans'], ['en-US', 'en-US'], ['ja-JP', 'ja-JP'], ['ko-KR', 'ko-KR']]
const viewports = process.env.E2E_OBSERVABILITY_RESPONSIVE === 'true'
  ? [{ width: 1440, height: 1000 }, { width: 1024, height: 768 }, { width: 390, height: 844 }]
  : [{ width: 1440, height: 1000 }]
const records = []
const browser = await chromium.launch({ headless: true })

/** 只接受明确的本地容器服务，避免将管理凭据发送到其他主机。 */
function localServiceUrl(value, service) {
  const url = new URL(value)
  assert.equal(url.protocol, 'http:')
  assert.ok([service, 'localhost', '127.0.0.1'].includes(url.hostname), 'Expected a local service URL')
  assert.ok(!url.username && !url.password, 'Credentials must not appear in URLs')
  return url.origin
}

/** 字体与渲染稳定后保存原始截图；正文仅进入私有回执。 */
async function capture(page, context, record, closeContext = true) {
  await page.evaluate(async () => {
    await document.fonts.ready
    await new Promise((done) => requestAnimationFrame(() => requestAnimationFrame(done)))
  })
  const image = resolve(outputDirectory, record.locale, `${mode}-${record.viewport.width}${record.step ? `-${record.step}` : ''}.png`)
  mkdirSync(resolve(outputDirectory, record.locale), { recursive: true })
  await page.screenshot({ path: image, animations: 'disabled' })
  records.push({
    ...record,
    image,
    sha256: digest(readFileSync(image)),
    captured_at: new Date().toISOString(),
    html_language: await page.locator('html').getAttribute('lang'),
    url: page.url(),
    text: (await page.locator('body').innerText()).slice(0, 12000),
    mocks: false,
  })
  if (closeContext) await context.close()
}

try {
  if (mode === 'dashboard') {
    const grafana = localServiceUrl(process.env.E2E_GRAFANA_URL || 'http://grafana:3000', 'grafana')
    assert.ok(process.env.GRAFANA_ADMIN_PASSWORD, 'Inject the private Grafana password through Docker --env-file')
    for (const [locale, language] of locales) {
      for (const viewport of viewports) {
        const context = await browser.newContext({ locale, viewport, reducedMotion: 'reduce' })
        const login = await context.request.post(`${grafana}/login`, {
          data: { user: process.env.GRAFANA_ADMIN_USER || 'admin', password: process.env.GRAFANA_ADMIN_PASSWORD },
        })
        assert.equal(login.status(), 200, 'Grafana login failed')
        const original = await context.request.get(`${grafana}/api/user/preferences`)
        assert.equal(original.status(), 200)
        const originalPreferences = await original.json()
        try {
          const preferences = await context.request.patch(`${grafana}/api/user/preferences`, { data: { language } })
          assert.equal(preferences.status(), 200)
          const dashboard = await context.request.get(`${grafana}/api/dashboards/uid/omni-platform`)
          assert.equal(dashboard.status(), 200)
          const panelTitles = (await dashboard.json()).dashboard.panels.map((panel) => panel.title)
          const page = await context.newPage()
          const queryStatuses = []
          page.on('response', (response) => {
            if (new URL(response.url()).pathname === '/api/ds/query') queryStatuses.push(response.status())
          })
          await page.goto(`${grafana}/d/omni-platform/omni-platform-overview?from=now-15m&to=now&timezone=UTC`, {
            waitUntil: 'networkidle', timeout: 60000,
          })
          await page.getByText('Omni Platform Overview', { exact: true }).first().waitFor({ state: 'visible', timeout: 20000 })
          assert.ok(queryStatuses.length > 0, 'No dashboard datasource response observed')
          assert.ok(queryStatuses.every((status) => status === 200), 'Dashboard datasource request failed')
          // 恢复用户语言后再关闭上下文，采集页面保持已加载的实际语言。
          const restored = await context.request.patch(`${grafana}/api/user/preferences`, { data: { language: originalPreferences.language || '' } })
          assert.equal(restored.status(), 200)
          await capture(page, context, { locale, requested_language: language, viewport, panel_titles: panelTitles, datasource_statuses: queryStatuses })
        } catch (error) {
          await context.request.patch(`${grafana}/api/user/preferences`, { data: { language: originalPreferences.language || '' } }).catch(() => {})
          await context.close()
          throw error
        }
      }
    }
  } else if (mode === 'alert-controls') {
    const alertmanager = localServiceUrl(process.env.E2E_ALERTMANAGER_URL || 'http://alertmanager:9093', 'alertmanager')
    const viewport = { width: 390, height: 844 }
    const context = await browser.newContext({ locale: 'en-US', viewport, reducedMotion: 'reduce' })
    const page = await context.newPage()
    const blocked = []
    // 仅切换浏览器筛选状态，禁止创建静默、修改配置或发送其他服务端命令。
    await page.route('**/*', async (route) => {
      const request = route.request()
      if (!['GET', 'HEAD'].includes(request.method())) {
        blocked.push({ method: request.method(), path: new URL(request.url()).pathname })
        return route.abort()
      }
      return route.continue()
    })
    const beforeResponse = await context.request.get(`${alertmanager}/api/v2/alerts`)
    assert.equal(beforeResponse.status(), 200)
    const activeBefore = await beforeResponse.json()
    await page.goto(`${alertmanager}/#/alerts`, { waitUntil: 'networkidle', timeout: 60000 })
    const checkbox = page.locator('input[type="checkbox"]').nth(2)
    assert.equal(await page.locator('input[type="checkbox"]').count(), 3)
    const originalChecked = await checkbox.isChecked()
    await page.evaluate(async () => { await document.fonts.ready })
    await page.screenshot({ animations: 'disabled' })
    await capture(page, context, { locale: 'shared', actual_ui_language: 'English; third-party native UI', viewport, step: 'left', server_alert_count: activeBefore.length }, false)
    await page.mouse.move(195, 350)
    await page.mouse.wheel(900, 0)
    const deadline = Date.now() + 5000
    while (Date.now() < deadline && await page.evaluate(() => scrollX) === 0) {
      await new Promise((done) => setTimeout(done, 100))
    }
    const scrolled = await page.evaluate(() => ({ x: scrollX, width: innerWidth, scroll_width: document.documentElement.scrollWidth }))
    const box = await checkbox.boundingBox()
    assert.ok(scrolled.x > 0 && box && box.x >= 0 && box.x + box.width <= viewport.width, 'Rightmost checkbox must be reachable through native horizontal scrolling')
    await page.getByText('Muted', { exact: true }).click()
    assert.equal(await checkbox.isChecked(), !originalChecked)
    await capture(page, context, { locale: 'shared', actual_ui_language: 'English; third-party native UI', viewport, step: 'right-toggle', scroll: scrolled, native_checkbox_toggle: true }, false)
    await page.getByText('Muted', { exact: true }).click()
    assert.equal(await checkbox.isChecked(), originalChecked)
    await page.mouse.wheel(-900, 0)
    const afterResponse = await context.request.get(`${alertmanager}/api/v2/alerts`)
    assert.equal(afterResponse.status(), 200)
    assert.deepEqual(blocked, [])
    records.push({ step: 'readonly-controls-summary', viewport, restored_checkbox: true, persisted_writes: 0,
      alert_count_before: activeBefore.length, alert_count_after: (await afterResponse.json()).length,
      limitation: 'Current alerts only; this check does not retrigger or prove delivery of an OmniServiceDown alert.', mocks: false })
    await context.close()
  } else {
    const alertmanager = localServiceUrl(process.env.E2E_ALERTMANAGER_URL || 'http://alertmanager:9093', 'alertmanager')
    for (const viewport of viewports) {
      const context = await browser.newContext({ locale: 'en-US', viewport, reducedMotion: 'reduce' })
      const page = await context.newPage()
      await page.goto(`${alertmanager}/#/alerts?filter=%7Balertname%3D%22OmniServiceDown%22%7D`, { waitUntil: 'networkidle', timeout: 60000 })
      await page.getByText('OmniServiceDown', { exact: false }).first().waitFor({ state: 'visible', timeout: 20000 })
      await capture(page, context, { locale: 'shared', actual_ui_language: 'English; third-party native UI', viewport })
    }
  }
  const receipt = {
    runtime: 'local Docker', mode, status: 'CAPTURED_VISUAL_REVIEW_PENDING',
    browser_image: process.env.E2E_BROWSER_IMAGE || 'mcr.microsoft.com/playwright:v1.61.1-noble',
    test: 'scripts/fixtures/capture-observability.mjs',
    test_sha256: digest(readFileSync(new URL(import.meta.url))), records,
  }
  mkdirSync(resolve(outputDirectory), { recursive: true })
  writeFileSync(output, `${JSON.stringify(receipt, null, 2)}\n`)
  console.log(JSON.stringify({ mode, captured: records.length, status: receipt.status }))
} finally {
  await browser.close()
}
