import { chromium } from 'playwright'
import { execFileSync } from 'child_process'
import { mkdirSync } from 'fs'
import { join } from 'path'
import http from 'http'

const FRONTEND = 'http://127.0.0.1:13000'
const GATEWAY_HOST = '127.0.0.1'
const GATEWAY_PORT = 18102
const OUTDIR = join('..', '.qoder', 'wp006-evidence', 'r12-visual', 'screenshots')
mkdirSync(OUTDIR, { recursive: true })
const DOCKER = 'C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe'

function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let data = ''
      res.on('data', c => data += c)
      res.on('end', () => resolve(JSON.parse(data)))
    }).on('error', reject)
  })
}

function httpPost(url, body) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(body)
    const req = http.request(url, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } }, res => {
      let resp = ''
      res.on('data', c => resp += c)
      res.on('end', () => resolve(JSON.parse(resp)))
    })
    req.on('error', reject)
    req.write(data)
    req.end()
  })
}

function redisGet(key) {
  return execFileSync(DOCKER, ['exec', 'omni-wp001-duzr-redis-1', 'redis-cli', '-a', 'b1bb37ac4cc6fe327a8828b968f7b0476038f28c3132afd5', '--raw', 'GET', key], { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim()
}

async function getFreshToken() {
  const captchaResp = await httpGet(`http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/auth/captcha`)
  const key = captchaResp.data.captchaKey
  const code = redisGet(`captcha:${key}`)
  console.log(`[INFO] Captcha: ${code} (key: ${key})`)
  const loginResp = await httpPost(`http://${GATEWAY_HOST}:${GATEWAY_PORT}/api/auth/login`, {
    username: 'admin', password: 'admin123', captchaCode: code, captchaKey: key, tenantId: 1,
  })
  return loginResp.data?.accessToken || null
}

async function screenshot(page, name) {
  const path = join(OUTDIR, `${name}.png`)
  await page.screenshot({ path, fullPage: false })
  console.log(`[OK] ${path}`)
  return path
}

async function navigateWithToken(page, token, path = '/admin/dashboard') {
  // 先到登录页注入 token
  await page.goto(`${FRONTEND}/login`, { waitUntil: 'domcontentloaded' })
  await page.evaluate((t) => { localStorage.setItem('token', t) }, token)
  // 直接导航到目标路径（路由守卫会自动加载菜单并注册动态路由）
  await page.goto(`${FRONTEND}${path}`, { waitUntil: 'networkidle', timeout: 15000 }).catch(() => {})
  // 等待侧边栏菜单渲染完成
  await page.waitForTimeout(3000)
  try { await page.waitForSelector('.el-menu-item.is-active', { timeout: 5000 }) } catch { /* menu might not highlight */ }
}

async function main() {
  const token = await getFreshToken()
  if (!token) { console.error('Failed to get token'); process.exit(1) }
  console.log(`[OK] Got fresh admin token (${token.length} chars)`)

  const browser = await chromium.launch({ headless: true })
  const configs = [
    { width: 1440, height: 900, label: 'desktop' },
    { width: 1024, height: 768, label: 'tablet' },
    { width: 390, height: 844, label: 'mobile' },
  ]

  for (const cfg of configs) {
    const context = await browser.newContext({ viewport: { width: cfg.width, height: cfg.height }, locale: 'zh-CN' })
    const page = await context.newPage()

    console.log(`\n=== ${cfg.label} (${cfg.width}x${cfg.height}) ===`)
    // Dashboard (admin layout with sidebar)
    await navigateWithToken(page, token, '/admin/dashboard')
    console.log(`URL: ${page.url()}`)

    await screenshot(page, `dashboard-${cfg.label}`)

    // Sidebar items
    try {
      const sidebarTexts = await page.$$eval('.el-menu-item, .el-sub-menu__title', els => els.map(e => e.textContent?.trim()).filter(Boolean))
      console.log(`Sidebar: ${sidebarTexts.slice(0, 20).join(' | ')}`)
    } catch { /* ignore */ }

    // Procurement pages (routes registered under /admin parent)
    for (const route of ['/admin/procurement/requisition', '/admin/procurement/rfq', '/admin/procurement/purchase-order', '/admin/procurement/goods-receipt']) {
      const name = route.split('/').pop()
      await navigateWithToken(page, token, route)
      console.log(`URL: ${page.url()}`)
      await screenshot(page, `${name}-${cfg.label}`)
    }

    // Mobile sidebar test
    if (cfg.label === 'mobile') {
      try {
        const toggle = await page.$('.hamburger-container, .sidebar-toggle')
        if (toggle) { await toggle.click(); await page.waitForTimeout(500); await screenshot(page, `sidebar-open-${cfg.label}`) }
      } catch { /* ignore */ }
    }

    await context.close()
  }

  // English desktop
  const enCtx = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'en-US' })
  const enPage = await enCtx.newPage()
  console.log('\n=== English desktop ===')
  await navigateWithToken(enPage, token, '/admin/dashboard')
  await screenshot(enPage, 'dashboard-en')
  await navigateWithToken(enPage, token, '/admin/procurement/requisition')
  console.log(`URL: ${enPage.url()}`)
  await screenshot(enPage, 'requisition-en')
  await navigateWithToken(enPage, token, '/admin/procurement/rfq')
  await screenshot(enPage, 'rfq-en')
  await navigateWithToken(enPage, token, '/admin/procurement/purchase-order')
  await screenshot(enPage, 'purchase-order-en')
  await navigateWithToken(enPage, token, '/admin/procurement/goods-receipt')
  await screenshot(enPage, 'goods-receipt-en')
  await enCtx.close()

  await browser.close()
  console.log('\n=== All screenshots complete ===')
}

main().catch(e => { console.error(e.message); process.exit(1) })
