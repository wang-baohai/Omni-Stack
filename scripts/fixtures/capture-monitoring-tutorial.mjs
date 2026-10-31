/** 在唯一容器浏览器中顺序采集正式监控界面；不改看板、告警或业务数据。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { resolve } from 'node:path'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
const directory = resolve(process.argv[2] || '')
assert.ok(process.argv[2] && directory.startsWith('/validation/') && !existsSync(directory))
const discover = process.argv[3] === 'discover'
assert.ok(!process.argv[3] || discover)
assert.match(process.env.E2E_MONITOR_TRACE_ID ?? '', /^[a-f0-9]{32}$/)
assert.ok(process.env.GRAFANA_ADMIN_PASSWORD)
const traceId = process.env.E2E_MONITOR_TRACE_ID
const grafana = 'http://grafana:3000'
const require = createRequire('/validation/source/omni-frontend/package.json')
const { chromium } = require('playwright')
const sha = bytes => createHash('sha256').update(bytes).digest('hex')
mkdirSync(directory, { recursive: true })
writeFileSync(directory + '/source-used.mjs', readFileSync(new URL(import.meta.url)))
const report = { started_at: new Date().toISOString(), original_step: 'WP-22.S03', workers: 1,
  script: 'scripts/fixtures/capture-monitoring-tutorial.mjs', script_sha256: sha(readFileSync(new URL(import.meta.url))),
  configuration_sha256: Object.fromEntries(['compose.observability.yaml', 'observability/prometheus/prometheus.yml',
    'observability/otel-collector/config.yaml', 'observability/alloy/config.alloy',
    'observability/grafana/provisioning/datasources/datasources.yml'].map(path => [path, sha(readFileSync('/source/' + path))])),
  viewport: { width: 1920, height: 1080 }, trace_id: traceId, identity_source: 'local dedicated Grafana admin configuration',
  response_mocked: false, display_dom_modified: false, images: [], checks: [], page_errors: [], blocked_mutations: [], query_history_created: [], result: 'RUNNING' }
const save = () => writeFileSync(directory + '/result.json', JSON.stringify(report, null, 2) + '\n')
const browser = await chromium.launch({ args: ['--no-sandbox'] })
const dashboards = [
  ['platform', 'omni-platform', 'Omni Platform Overview'], ['red', 'omni-red', 'Service RED'],
  ['jvm', 'omni-jvm', 'JVM and Pools'], ['feign', 'omni-feign', 'Feign Clients'],
  ['mq', 'omni-mq', 'MQ and Outbox'], ['workflow', 'omni-workflow', 'Workflow'],
  ['migrations', 'omni-migrations', 'Database Migrations'],
]
const languages = discover ? [['en-US', 'en-US']] : [['zh-CN', 'zh-Hans'], ['en-US', 'en-US'], ['ja-JP', 'ja-JP'], ['ko-KR', 'ko-KR']]
async function makeContext(locale) {
  const context = await browser.newContext({ locale, viewport: report.viewport, reducedMotion: 'reduce' })
  context.setDefaultTimeout(30000)
  context.on('page', page => page.on('pageerror', error => { report.page_errors.push({ locale, message: error.message }); save() }))
  await context.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url())
    if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && !(url.origin === grafana &&
      (request.method() === 'POST' && ['/login', '/api/ds/query', '/api/query-history', '/apis/features.grafana.app/v0alpha1/namespaces/default/ofrep/v1/evaluate/flags'].includes(url.pathname) ||
       request.method() === 'PATCH' && url.pathname === '/api/user/preferences'))) {
      report.blocked_mutations.push({ method: request.method(), host: url.hostname, path: url.pathname }); save()
      return route.abort()
    }
    return route.continue()
  })
  return context
}
async function capture(page, name, locale) {
  await page.evaluate(() => document.fonts.ready)
  await page.waitForTimeout(650)
  const image = `${directory}/${name}-${locale}.png`
  await page.screenshot({ path: image, animations: 'disabled' })
  writeFileSync(`${directory}/${name}-${locale}.txt`, await page.locator('body').innerText())
  report.images.push({ name, locale, image, viewport: report.viewport, sha256: sha(readFileSync(image)),
    captured_at: new Date().toISOString(), route: new URL(page.url()).pathname + new URL(page.url()).search + new URL(page.url()).hash,
    actual_visual_review: 'PENDING', source_application: new URL(page.url()).hostname }); save()
}
const queryForLogs = '{service_name="omni-base"} | json | line_format "{{.message}}" |= "Started BaseApplication"'
function exploreUrl(uid, type, query) {
  const queries = type === 'loki'
    ? [{ refId: 'A', expr: query, queryType: 'range', datasource: { type, uid } }]
    : [{ refId: 'A', query, queryType: 'traceql', datasource: { type, uid } }]
  return grafana + '/explore?' + new URLSearchParams({ schemaVersion: '1', orgId: '1',
    panes: JSON.stringify({ left: { datasource: uid, queries, range: { from: 'now-1h', to: 'now' } } }) })
}
try {
  for (const [locale, language] of languages) {
    const context = await makeContext(locale)
    const login = await context.request.post(grafana + '/login', { data: { user: process.env.GRAFANA_ADMIN_USER || 'admin', password: process.env.GRAFANA_ADMIN_PASSWORD } })
    assert.equal(login.status(), 200)
    const preferenceResponse = await context.request.get(grafana + '/api/user/preferences')
    assert.equal(preferenceResponse.status(), 200)
    const preferences = await preferenceResponse.json()
    const historyRequests = []
    try {
      const changed = await context.request.patch(grafana + '/api/user/preferences', { data: { language } }); assert.equal(changed.status(), 200)
      const page = await context.newPage()
      page.on('response', response => {
        if (new URL(response.url()).pathname === '/api/query-history' && response.request().method() === 'POST') {
          historyRequests.push((async () => {
            const data = await response.json(); const uid = data.result?.uid || data.uid
            assert.ok(typeof uid === 'string' && /^[a-zA-Z0-9_-]+$/.test(uid), 'Query history response needs its actual created UID')
            report.query_history_created.push({ locale, uid, deleted: false }); save()
          })())
        }
      })
      for (const [name, uid, title] of discover ? dashboards.slice(0, 1) : dashboards) {
        const definition = await context.request.get(grafana + '/api/dashboards/uid/' + uid); assert.equal(definition.status(), 200)
        const dashboard = (await definition.json()).dashboard; assert.equal(dashboard.title, title)
        await page.goto(`${grafana}/d/${uid}?from=now-1h&to=now&timezone=browser`, { waitUntil: 'networkidle', timeout: 60000 })
        await page.getByText(title, { exact: true }).first().waitFor({ state: 'visible' })
        await capture(page, 'monitor-' + name, locale)
        report.checks.push({ locale, dashboard_uid: uid, title, panel_titles: dashboard.panels.map(panel => panel.title), result: 'NATIVE_DASHBOARD_RENDERED_NOT_BUSINESS_ACCEPTANCE' }); save()
      }
      await page.goto(exploreUrl('omni-loki', 'loki', queryForLogs), { waitUntil: 'networkidle', timeout: 60000 })
      await page.getByText('Started BaseApplication', { exact: false }).first().waitFor({ state: 'visible' })
      await capture(page, 'monitor-logs', locale)
      await page.goto(exploreUrl('omni-tempo', 'tempo', traceId), { waitUntil: 'networkidle', timeout: 60000 })
      await page.locator('body').filter({ hasText: 'omni-gateway: http get' }).waitFor({ state: 'visible' })
      await page.locator('body').filter({ hasText: 'omni-base' }).waitFor({ state: 'visible' })
      await capture(page, 'monitor-trace', locale)
    } finally {
      try {
        await Promise.all(historyRequests)
        for (const entry of report.query_history_created.filter(row => row.locale === locale && !row.deleted)) {
          const deleted = await context.request.delete(grafana + '/api/query-history/' + entry.uid)
          assert.equal(deleted.status(), 200); entry.deleted = true; save()
        }
      } finally {
        const restored = await context.request.patch(grafana + '/api/user/preferences', { data: { language: preferences.language || '' } })
        assert.equal(restored.status(), 200); report.checks.push({ locale, result: 'GRAFANA_ORIGINAL_LANGUAGE_RESTORED' }); save(); await context.close()
      }
    }
  }
  const native = await makeContext('en-US'), page = await native.newPage()
  await page.goto('http://prometheus:9090/targets', { waitUntil: 'domcontentloaded' })
  await page.locator('body').filter({ hasText: 'omni-platform' }).waitFor({ state: 'visible' })
  await capture(page, 'monitor-prometheus-targets', 'shared')
  await page.goto('http://prometheus:9090/query?' + new URLSearchParams({ 'g0.expr': 'up{job="omni-platform"}', 'g0.tab': '1', 'g0.range_input': '1h' }), { waitUntil: 'domcontentloaded' })
  await page.locator('body').filter({ hasText: 'omni-auth' }).waitFor({ state: 'visible' })
  await capture(page, 'monitor-prometheus-query', 'shared')
  await page.goto('http://alloy:12345', { waitUntil: 'networkidle' })
  await capture(page, 'monitor-alloy', 'shared'); await native.close()
  for (const [locale] of languages) {
    const context = await makeContext(locale), page = await context.newPage()
    await page.goto(`http://alertmanager-ui:8080/${locale}/#/alerts`, { waitUntil: 'networkidle' })
    assert.equal(await page.locator('html').getAttribute('lang'), locale)
    assert.equal(await page.locator('#omni-locale').inputValue(), locale)
    await capture(page, 'monitor-alertmanager', locale); await context.close()
  }
  report.result = 'CAPTURED_NATIVE_MONITORING_IMAGES_PENDING_ACTUAL_AI_VIEW'; report.finished_at = new Date().toISOString(); save()
  console.log(JSON.stringify({ result: report.result, images: report.images.length, checks: report.checks.length, page_errors: report.page_errors.length, evidence: directory }))
} catch (error) { report.result = 'FAIL'; report.error = error.message; save(); throw error }
finally { await browser.close() }
