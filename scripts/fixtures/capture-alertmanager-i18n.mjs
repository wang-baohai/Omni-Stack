/** 在本机 Docker 中验证四语原生 Alertmanager；只读 API，不新增告警或静默。 */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';

assert.ok(existsSync('/.dockerenv'), 'Run this verifier inside local Docker');
const output = resolve(process.argv[2] || '');
assert.ok(process.argv[2], 'Provide a new private output directory');
const receiptPath = resolve(output, 'alertmanager-i18n.json');
assert.ok(!existsSync(receiptPath), 'Inspect the existing receipt before repeating capture');
mkdirSync(output, { recursive: true });
const require = createRequire('/browser/package.json');
const { chromium } = require('playwright');
const base = new URL(process.env.E2E_ALERTMANAGER_UI_URL || 'http://alertmanager-ui:8080');
assert.ok(['alertmanager-ui', 'localhost', '127.0.0.1'].includes(base.hostname));
assert.equal(base.protocol, 'http:');
assert.ok(!base.username && !base.password);
const origin = base.origin;
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'];
const translationOrder = ['en-US', ...locales.filter(locale => locale !== 'en-US')];
const translations = new Map(readFileSync(new URL('../../observability/alertmanager-ui/messages.tsv', import.meta.url), 'utf8')
  .split(/\r?\n/u).filter(line => line && !line.startsWith('#')).map(line => {
    const [key, ...values] = line.split('|').map(value => value.replace(/\\u0020/gu, ' '));
    return [key, [key, ...values]];
  }));
const t = (key, locale) => translations.get(key)[translationOrder.indexOf(locale)];
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const records = [];
const checks = [];
const blockedWrites = [];
const pageErrors = [];
const browser = await chromium.launch({ headless: true });
const viewports = [{ width: 1440, height: 1000 }, { width: 1024, height: 768 }, { width: 390, height: 844 }];
const alertname = process.env.E2E_ALERTMANAGER_ALERT || 'OmniServiceDown';
const filter = encodeURIComponent(`{alertname="${alertname}"}`);
let status = 'FAIL';
let error;

async function stable(page) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    await new Promise(done => requestAnimationFrame(() => requestAnimationFrame(done)));
  });
}

async function capture(page, locale, viewport) {
  await stable(page);
  const suffix = viewport.width === 1440 ? '' : viewport.width === 1024 ? '-tablet' : '-mobile';
  const image = resolve(output, locale, `operations-alert-localized${suffix}.png`);
  mkdirSync(resolve(output, locale), { recursive: true });
  await page.screenshot({ path: image, animations: 'disabled' });
  const dimensions = await page.evaluate(() => ({ width: innerWidth, scroll_width: document.documentElement.scrollWidth }));
  assert.ok(dimensions.scroll_width <= dimensions.width + 1, `Horizontal overflow: ${locale} ${viewport.width}`);
  records.push({ locale, viewport, image, sha256: digest(readFileSync(image)), captured_at: new Date().toISOString(),
    route: new URL(page.url()).pathname + new URL(page.url()).hash, html_language: await page.locator('html').getAttribute('lang'),
    role: 'local observability maintainer', fixture: 'existing Prometheus OmniServiceDown alert; no new alert injected',
    expected: 'Real active alert visible with localized navigation, filter and action labels; no horizontal viewport overflow',
    original_application: 'Official Alertmanager v0.30.1 Elm UI with reviewed source translations and native language selector',
    mocks: false, dom_text_rewrite: false, visual_review: 'PENDING' });
}

try {
  const request = await browser.newContext();
  const before = await request.request.get(`${origin}/api/v2/alerts`);
  assert.equal(before.status(), 200);
  const active = await before.json();
  assert.ok(active.some(alert => alert.labels.alertname === alertname), `Expected existing real ${alertname} alert`);
  const provenance = await request.request.get(`${origin}/build-provenance.json`);
  assert.equal(provenance.status(), 200);
  const build = await provenance.json();
  assert.equal(build.archive_sha256, 'b3577c531d4c05e0f45f90b02224c81684be9f4083720a01cc05bfa07107fc93');
  assert.equal(build.native_ui, 'Elm');
  await request.close();

  for (const locale of locales) {
    for (const viewport of viewports) {
      const context = await browser.newContext({ locale, viewport, reducedMotion: 'reduce' });
      const page = await context.newPage();
      page.on('pageerror', failure => pageErrors.push(failure.message));
      await page.route('**/*', async route => {
        const req = route.request();
        if (!['GET', 'HEAD'].includes(req.method())) {
          blockedWrites.push({ method: req.method(), path: new URL(req.url()).pathname });
          return route.abort();
        }
        return route.continue();
      });
      await page.goto(`${origin}/${locale}/#/alerts?filter=${filter}`, { waitUntil: 'networkidle' });
      await page.getByText(alertname, { exact: false }).first().waitFor({ state: 'visible' });
      assert.equal(await page.locator('html').getAttribute('lang'), locale);
      assert.equal(await page.locator('#omni-locale').inputValue(), locale);
      assert.equal(await page.locator('#omni-locale option').count(), 4);
      const localizedApi = await context.request.get(`${origin}/${locale}/api/v2/alerts`);
      assert.equal(localizedApi.status(), 200);
      assert.ok((await localizedApi.json()).some(alert => alert.labels.alertname === alertname && Array.isArray(alert.status.silencedBy)));
      const muted = page.locator('.custom-control-description').filter({ hasText: t('Muted', locale) }).first();
      const checkbox = page.locator('input[type="checkbox"]').nth(2);
      const wasChecked = await checkbox.isChecked();
      await muted.click();
      assert.equal(await checkbox.isChecked(), !wasChecked);
      await muted.click();
      assert.equal(await checkbox.isChecked(), wasChecked);
      const expand = page.getByText(t('Expand all groups', locale), { exact: true });
      if (await expand.isVisible()) await expand.click();
      await capture(page, locale, viewport);

      if (viewport.width === 1440 || viewport.width === 390) {
        await page.locator(`a[href="#/status"]`).click();
        await page.getByText(t('Version Information', locale), { exact: true }).waitFor({ state: 'visible' });
        await page.locator('a[href="#/settings"]').click();
        await page.getByText(t('Monday', locale), { exact: true }).click();
        await page.waitForFunction(() => JSON.parse(localStorage.getItem('firstDayOfWeek')) === 'Monday');
        assert.equal(await page.evaluate(() => JSON.parse(localStorage.getItem('firstDayOfWeek'))), 'Monday');
        await page.reload({ waitUntil: 'networkidle' });
        assert.equal(await page.locator('input[value="Monday"]').isChecked(), true);
        const otherLocale = locale === 'zh-CN' ? 'en-US' : 'zh-CN';
        await page.locator('#omni-locale').selectOption(otherLocale);
        await page.waitForURL(`${origin}/${otherLocale}/#/settings`);
        await page.getByText(t('First day of the week:', otherLocale), { exact: true }).waitFor({ state: 'visible' });
        assert.equal(await page.locator('input[value="Monday"]').isChecked(), true);
        await page.locator('#omni-locale').selectOption(locale);
        await page.waitForURL(`${origin}/${locale}/#/settings`);
        await page.getByText(t('New Silence', locale), { exact: true }).click();
        await page.getByRole('button', { name: t('Create', locale), exact: true }).click();
        await page.getByText(t('Matchers are required', locale), { exact: true }).waitFor({ state: 'visible' });
        await page.getByText(t('Should not be empty', locale), { exact: true }).first().waitFor({ state: 'visible' });
        await page.locator('button').filter({ has: page.locator('.fa-calendar') }).click();
        await page.locator('.modal.show').waitFor({ state: 'visible' });
        await page.getByText(t('Set Date/Time', locale), { exact: true }).waitFor({ state: 'visible' });
        assert.equal((await page.locator('.weekheader').innerText()).replace(/\s/gu, ''), [t('Mon', locale), t('Tue', locale), t('Wed', locale), t('Thu', locale), t('Fri', locale), t('Sat', locale), t('Sun', locale)].join(''));
        await page.getByRole('button', { name: t('Cancel', locale), exact: true }).click();
        checks.push({ locale, width: viewport.width, settings_protocol_value: 'Monday', language_switch_preserves_route: true,
          silence_validation_localized: true, calendar_localized: true, native_muted_control_restored: true });
      }
      await context.close();
    }
  }
  assert.deepEqual(blockedWrites, [], 'Validation must not try any persistent API write');
  assert.deepEqual(pageErrors, []);
  status = 'PASS_FUNCTIONAL_VISUAL_REVIEW_PENDING';
} catch (failure) {
  error = failure.stack;
  throw failure;
} finally {
  await browser.close();
  writeFileSync(receiptPath, JSON.stringify({ status, runtime: 'local Docker', captured_at: new Date().toISOString(),
    browser_image: process.env.E2E_BROWSER_IMAGE || 'mcr.microsoft.com/playwright:v1.61.1-noble', browser: 'Chromium',
    test: 'scripts/fixtures/capture-alertmanager-i18n.mjs', test_sha256: digest(readFileSync(new URL(import.meta.url))),
    records, checks, blocked_writes: blockedWrites, actual_server_writes: 0, page_errors: pageErrors, error }, null, 2) + '\n');
}
