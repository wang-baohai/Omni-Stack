import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const base = process.env.OMNI_BASE_URL || 'http://localhost:3000';
const adminToken = process.env.OMNI_ADMIN_TOKEN;
const assetUserToken = process.env.OMNI_ASSET_USER_TOKEN;
const locales = (process.env.OMNI_LOCALES || 'zh-CN,en-US,ja-JP,ko-KR').split(',');
const outputRoot = process.env.OMNI_SCREENSHOT_ROOT || 'docs/images';
if (!adminToken || !assetUserToken) {
  throw new Error('OMNI_ADMIN_TOKEN and OMNI_ASSET_USER_TOKEN are required');
}

const scenes = [
  { flow: 'asset-overview', file: 'asset-overview', route: '/admin/asset/overview', step: 'entry', role: 'administrator' },
  { flow: 'asset-ledger', file: 'asset-ledger', route: '/admin/asset/asset', step: 'list', role: 'administrator' },
  { flow: 'asset-detail', file: 'asset-detail', route: '/admin/asset/asset', step: 'detail', role: 'administrator', action: 'open first detail drawer' },
  { flow: 'asset-create-dialog', file: 'asset-create-dialog', route: '/admin/asset/asset', step: 'create-or-edit', role: 'administrator', action: 'open manual stock-in dialog' },
  { flow: 'asset-transfer-completed', file: 'asset-transfer-completed', route: '/admin/asset/transfer', step: 'success', role: 'administrator' },
  { flow: 'asset-transfer-rejected', file: 'asset-transfer-rejected', route: '/admin/asset/transfer', step: 'failure-or-forbidden', role: 'administrator' },
  { flow: 'asset-disposal-completed', file: 'asset-disposal-completed', route: '/admin/asset/disposal', step: 'success', role: 'administrator' },
  { flow: 'asset-my-in-use', file: 'asset-my-in-use', route: '/admin/asset/asset', step: 'key-action', role: 'asset-user' },
  { flow: 'asset-my-returned', file: 'asset-my-returned', route: '/admin/asset/asset', step: 'success', role: 'asset-user' },
];

const browser = await chromium.launch({ args: ['--no-sandbox'] });
for (const locale of locales) {
  const target = path.join(outputRoot, locale);
  fs.mkdirSync(target, { recursive: true });
  for (const scene of scenes) {
    const token = scene.role === 'asset-user' ? assetUserToken : adminToken;
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale });
    await context.addInitScript(([value, lang]) => {
      localStorage.setItem('token', value);
      localStorage.setItem('omni-lang', lang);
    }, [token, locale]);
    const page = await context.newPage();
    await page.goto(`${base}${scene.route}`, { waitUntil: 'networkidle', timeout: 60_000 });
    await page.waitForTimeout(1500);
    if (scene.flow === 'asset-detail') {
      const detail = page.locator('button').filter({ hasText: /Details|詳細|상세|详情/ }).first();
      if (await detail.count()) {
        await detail.click();
        await page.waitForTimeout(1000);
      }
    }
    if (scene.flow === 'asset-create-dialog') {
      const create = page.locator('button').filter({ hasText: /Manual|手動|수동|手动/ }).first();
      if (await create.count()) {
        await create.click();
        await page.waitForTimeout(1000);
      }
    }
    await page.screenshot({ path: path.join(target, `${scene.file}.png`) });
    await context.close();
  }
}
await browser.close();
