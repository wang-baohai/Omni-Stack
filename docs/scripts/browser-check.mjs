import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { sitePages } from './prepare-site.mjs'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(resolve(root, 'omni-frontend/package.json'))
const { chromium } = require('@playwright/test')
const version = require('@playwright/test/package.json').version
assert.equal(version, '1.61.1', 'Playwright package must match the pinned container image')
const base = new URL(process.env.WIKI_URL || 'http://wiki:4173/')
assert.match(base.protocol, /^https?:$/)
const output = resolve(process.env.WIKI_EVIDENCE_DIR || '/evidence')
if (output === root || output.startsWith(root + '/')) throw new Error('Browser evidence must be outside the repository')
mkdirSync(output, { recursive: true })
const evidence = { started_at: new Date().toISOString(), tool: `Playwright ${version}`, runtime: 'local Docker',
  url: base.href, source_head: process.env.WIKI_SOURCE_HEAD || null, image: process.env.WIKI_IMAGE_ID || null,
  checks: [], screenshots: [] }
const browser = await chromium.launch()
const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, timezoneId: 'Asia/Shanghai', colorScheme: 'light' })
const page = await context.newPage()
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']
const searchLabels = ['搜索文档', 'Search docs', '文書を検索', '문서 검색']
const suffixes = ['', '.en', '.jp', '.kr']

async function screenshot(name, locale, expected, fullPage = true) {
  const path = resolve(output, `${name}-${locale}.png`)
  await page.screenshot({ path, fullPage, animations: 'disabled' })
  const bytes = readFileSync(path)
  evidence.screenshots.push({ path, locale, role: 'public documentation reader', route: page.url(),
    viewport: page.viewportSize(), width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20),
    expected, collected_at: new Date().toISOString(), sha256: createHash('sha256').update(bytes).digest('hex'),
    test: 'docs/scripts/browser-check.mjs', visual_review: 'pending' })
}

try {
  const queue = locales.map((locale) => new URL(`${locale}/wiki/index.html`, base).href)
  const seen = new Map()
  const anchors = []
  while (queue.length) {
    const url = queue.shift()
    if (seen.has(url)) continue
    assert.ok(seen.size < 400, 'Unexpected unbounded documentation navigation')
    const response = await page.goto(url, { waitUntil: 'networkidle' })
    assert.equal(response?.status(), 200, `Missing documentation page: ${url}`)
    await page.locator('.vp-doc h1').first().waitFor()
    await page.waitForFunction(() => [...document.querySelectorAll('.vp-doc img')].every((image) => image.complete && image.naturalWidth > 0))
    await page.waitForFunction(() => document.querySelector('[data-diagram-error="true"]') || !document.querySelector('.vp-doc div.language-mermaid'))
    assert.equal(await page.locator('[data-diagram-error="true"]').count(), 0, `Diagram rendering failed: ${url}`)
    assert.equal(await page.locator('.vp-doc div.language-mermaid').count(), 0, `Diagram stayed as source code: ${url}`)
    const data = await page.evaluate(() => ({
      title: document.querySelector('.vp-doc h1')?.textContent,
      ids: [...document.querySelectorAll('[id]')].map((element) => element.id),
      links: [...document.querySelectorAll('a[href]')].map((element) => element.href),
      overflow: document.documentElement.scrollWidth > innerWidth + 1,
      images: document.querySelectorAll('.vp-doc img').length,
    }))
    assert.ok(data.title?.trim(), `Empty title: ${url}`)
    assert.equal(data.overflow, false, `Page-level horizontal overflow: ${url}`)
    seen.set(url, data)
    for (const link of data.links) {
      const target = new URL(link)
      if (target.origin !== base.origin || !target.pathname.startsWith(base.pathname)) continue
      if (target.hash) anchors.push({ source: url, target: target.href })
      target.hash = ''
      if (target.pathname.endsWith('.html') && !seen.has(target.href)) queue.push(target.href)
    }
  }
  for (const { source, target } of anchors) {
    const link = new URL(target)
    const anchor = decodeURIComponent(link.hash.slice(1))
    link.hash = ''
    if (seen.has(link.href)) assert.ok(seen.get(link.href).ids.includes(anchor), `Missing anchor ${target}, from ${source}`)
  }
  evidence.checks.push({ name: 'all reachable pages, local links, anchors, images and desktop layout', status: 'pass', pages: seen.size,
    images: [...seen.values()].reduce((sum, value) => sum + value.images, 0), anchors: anchors.length })
  const manifest = YAML.parse(readFileSync(resolve(root, 'docs/docs-manifest.yaml'), 'utf8'))
  const published = new Set(sitePages(manifest).map((item) => item.document.id))
  const removed = manifest.documents.filter((item) => item.source.endsWith('.md') && !published.has(item.id))
  for (const item of removed) {
    const route = item.source.replace(/^docs\//, '').replace(/\.md$/, '.html')
    for (const locale of locales) {
      const response = await context.request.get(new URL(`${locale}/${route}`, base).href)
      assert.equal(response.status(), 404, `Removed Wiki page remains published: ${locale}/${route}`)
    }
  }
  evidence.checks.push({ name: 'removed legacy pages return real 404 in every locale', status: 'pass', pages: removed.length * locales.length })
  for (const [index, locale] of locales.entries()) {
    await page.goto(new URL(`${locale}/wiki/index.html`, base).href)
    assert.equal(await page.locator('html').getAttribute('lang'), locale)
    const labels = JSON.parse(readFileSync(resolve(root, `docs/.vitepress/locales/${locale}.json`), 'utf8'))
    const readingOrder = ['WIKI-01', 'WIKI-03', 'WIKI-02', 'WIKI-04', 'engineering', 'WIKI-09', 'WIKI-08',
      'WIKI-06', 'WIKI-07', 'WIKI-11', 'WIKI-12', 'WIKI-13', 'WIKI-05', 'WIKI-10']
    assert.deepEqual((await page.locator('.VPSidebar h2').allTextContents()).map(title => title.trim()),
      readingOrder.map(id => id === 'engineering' ? labels.truth : labels.chapters[id]), `${locale}: required sidebar reading order`)
    assert.equal(await page.locator('.vp-doc table').first().locator('tbody tr').count(), 14)
    await screenshot('wiki-index', locale, 'Thirteen localized content chapters and engineering reference in the required fourteen-group reading order')
    await page.getByRole('button', { name: searchLabels[index], exact: true }).click()
    await page.locator('#localsearch-input').fill('GETDEL')
    const results = page.locator('.VPLocalSearchBox .results a')
    await results.first().waitFor()
    const links = await results.evaluateAll((elements) => elements.map((element) => element.href))
    assert.ok(links.length > 0, `${locale}: local full-text search has no result`)
    assert.ok(links.every((link) => new URL(link).pathname.startsWith(base.pathname + locale + '/')), `${locale}: search leaks other locales`)
    await screenshot('wiki-search', locale, 'Local search finds current-locale GETDEL source explanation', false)
    await page.keyboard.press('Escape')
    await page.goto(new URL(`${locale}/wiki/preset-core.html`, base).href)
    const source = readFileSync(resolve(root, `docs/wiki/preset-core${suffixes[index]}.md`), 'utf8')
    const title = /^#\s+(.+)$/m.exec(source)[1]
    const displayedTitle = await page.locator('.vp-doc h1').evaluate((element) => {
      const copy = element.cloneNode(true)
      copy.querySelectorAll('.header-anchor').forEach((anchor) => anchor.remove())
      return copy.textContent.trim()
    })
    assert.equal(displayedTitle, title)
    await screenshot('wiki-preset-core', locale, 'Current localized core preset startup chapter')
    await page.setViewportSize({ width: 390, height: 844 })
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${locale}: mobile overflow`)
    await screenshot('wiki-mobile', locale, 'Quick-start remains readable at 390×844', false)
    await page.setViewportSize({ width: 1440, height: 900 })
    evidence.checks.push({ name: `${locale} navigation, search, title, images and mobile`, status: 'pass' })
  }
  assert.deepEqual(errors, [], 'Browser JavaScript errors')
  evidence.status = 'pass'
} catch (error) {
  evidence.status = 'fail'
  evidence.error = error.message
  console.error(error.message)
  process.exitCode = 1
} finally {
  evidence.finished_at = new Date().toISOString()
  writeFileSync(resolve(output, 'wiki-browser-evidence.json'), JSON.stringify(evidence, null, 2) + '\n')
  await browser.close()
  console.log(JSON.stringify({ status: evidence.status, checks: evidence.checks, screenshots: evidence.screenshots.length, report: resolve(output, 'wiki-browser-evidence.json') }))
}
