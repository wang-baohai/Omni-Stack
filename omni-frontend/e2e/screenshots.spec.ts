/**
 * @module screenshots.spec
 * 系统功能截图自动化脚本。
 * 通过 Playwright 自动截取 21 张系统核心功能截图，输出到 docs/images/ 目录。
 *
 * 前置条件：
 * 1. Docker 中间件已启动（MySQL, Redis, Nacos 等）
 * 2. 后端 Auth(8100) + Base(8101) + Gateway(8102) + Workflow(8103) + CRM(8104) + SRM(8105) 服务已启动
 * 3. 前端 dev server 已启动（localhost:3000）
 * 4. 数据库已初始化（init-all.sql 已执行，SRM 数据已导入）
 *
 * 运行命令：cd omni-frontend && npx playwright test
 */
import { test, expect, type Page } from '@playwright/test'
import Redis from 'ioredis'
import path from 'path'
import { fileURLToPath } from 'url'

/** 截图输出目录（相对于 omni-frontend/） */
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const OUT = path.resolve(__dirname, '../../docs/images')

/** 后端 Auth 服务直连地址（用于调验证码 API） */
const AUTH_BASE = 'http://localhost:8100'

/** Redis 连接配置 */
const REDIS_HOST = '127.0.0.1'
const REDIS_PORT = 6379

/**
 * 从 Redis 获取验证码文本。
 * 验证码存储格式：captcha:{uuid}，一次性使用。
 */
async function getCaptchaFromRedis(key: string): Promise<string> {
  const redis = new Redis({ host: REDIS_HOST, port: REDIS_PORT, lazyConnect: true })
  try {
    await redis.connect()
    const code = await redis.get(`captcha:${key}`)
    if (!code) throw new Error(`Redis 中未找到验证码: captcha:${key}`)
    return code
  } finally {
    await redis.quit()
  }
}

/**
 * 获取可用租户列表，返回第一个租户的 id。
 */
async function getFirstTenantId(): Promise<number> {
  const res = await fetch(`${AUTH_BASE}/api/auth/tenants`)
  const json = await res.json()
  if (!json.data || json.data.length === 0) throw new Error('无可用租户，请检查数据库初始化')
  return json.data[0].id
}

/**
 * 登录 helper：拦截页面自带的验证码 API → 从 Redis 读验证码文本 → 填表单 → 提交。
 * 利用 Playwright 的 route 拦截功能，捕获页面加载时 /api/auth/captcha 响应中的 captchaKey。
 */
async function login(page: Page): Promise<void> {
  // 拦截验证码 API 响应，提取 captchaKey
  let captchaKey = ''
  await page.route('**/api/auth/captcha', async (route) => {
    const response = await route.fetch()
    const json = await response.json()
    captchaKey = json.data.captchaKey
    await route.fulfill({ response })
  })

  await page.goto('/login')
  await page.waitForSelector('.login-form-wrapper', { timeout: 10000 })

  // 等待验证码图片加载（说明 captcha API 已返回）
  await page.waitForSelector('.captcha-image img', { timeout: 10000 })
  await page.waitForTimeout(500)

  // 从 Redis 读取验证码文本（使用页面加载时拦截到的 captchaKey）
  if (!captchaKey) throw new Error('未能拦截到验证码 captchaKey')
  const captchaCode = await getCaptchaFromRedis(captchaKey)

  // 选择租户（点击下拉框 → 选择第一个选项）
  await page.click('.el-select .el-select__wrapper')
  await page.waitForSelector('.el-select-dropdown__item', { timeout: 5000 })
  await page.click('.el-select-dropdown__item')

  // 填写用户名和密码（清除默认值后重新填入）
  const usernameInput = page.locator('.el-form-item').filter({ hasText: '' }).locator('input').nth(0)
  // 使用更精确的选择器：第二个 el-form-item 是用户名，第三个是密码
  const formItems = page.locator('.el-form-item')
  await formItems.nth(1).locator('input').fill('admin')
  await formItems.nth(2).locator('input').fill('admin123')

  // 填写验证码
  const captchaInput = page.locator('.captcha-input input')
  await captchaInput.fill(captchaCode)

  // 点击登录
  await page.click('.login-btn')

  // 等待跳转（登录成功后会离开 /login）
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 15000 })
  await page.waitForTimeout(2000)

  // 清除路由拦截
  await page.unroute('**/api/auth/captcha')
}

/**
 * 导航到管理后台页面（需先登录）。
 * 等待侧边栏和内容区渲染完成。
 */
async function navigateTo(page: Page, path: string): Promise<void> {
  await page.goto(path)
  // 等待布局渲染（侧边栏 + 内容区）
  await page.waitForSelector('.el-main, .app-main', { timeout: 15000 })
  // 等待可能的表格/数据加载
  await page.waitForTimeout(2000)
}

// ===== 截图测试用例 =====

test('01 - 登录页全景', async ({ page }) => {
  await page.goto('/login')
  await page.waitForSelector('.login-form-wrapper', { timeout: 10000 })
  // 等待验证码图片加载
  await page.waitForSelector('.captcha-image img', { timeout: 10000 }).catch(() => {})
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(OUT, 'login.png'), fullPage: true })
})

test('02 - 注册页', async ({ page }) => {
  await page.goto('/register')
  await page.waitForSelector('.el-form', { timeout: 10000 })
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(OUT, 'register.png'), fullPage: true })
})

test('03 - 社交登录按钮区域', async ({ page }) => {
  await page.goto('/login')
  await page.waitForSelector('.login-form-wrapper', { timeout: 10000 })
  // 等待第三方登录按钮渲染
  await page.waitForSelector('.login-third-party', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(OUT, 'social-login-buttons.png'), fullPage: true })
})

test('04 - OAuth2 授权确认页', async ({ page }) => {
  // consent 页面需要 URL 参数才能渲染 consent 状态
  await page.goto('/consent?client_id=omni-frontend&scope=openid,profile,email&state=test-state-123')
  await page.waitForSelector('.consent-card', { timeout: 10000 })
  // 等待 scope 列表渲染
  await page.waitForSelector('.consent-scope-item', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(OUT, 'social-consent.png'), fullPage: true })
})

// ===== 以下测试需要先登录 =====

test('05 - 数据看板 Dashboard', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/dashboard')
  await page.screenshot({ path: path.join(OUT, 'dashboard.png'), fullPage: true })
})

test('06 - 用户管理', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/user')
  await page.screenshot({ path: path.join(OUT, 'system-user.png'), fullPage: true })
})

test('07 - 字典管理', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/dict')
  await page.screenshot({ path: path.join(OUT, 'system-dict.png'), fullPage: true })
})

test('08 - XSS 防护配置', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/xssconfig')
  await page.screenshot({ path: path.join(OUT, 'system-xss.png'), fullPage: true })
})

test('09 - 操作日志', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/operlog')
  await page.screenshot({ path: path.join(OUT, 'monitor-operlog.png'), fullPage: true })
})

test('10 - MQ 消息记录', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/mqmessage')
  await page.screenshot({ path: path.join(OUT, 'monitor-mqmessage.png'), fullPage: true })
})

test('11 - 系统任务管理', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/system-job')
  await page.screenshot({ path: path.join(OUT, 'job-system.png'), fullPage: true })
})

test('12 - 用户工作台', async ({ page }) => {
  await login(page)
  // Home 页面不使用 admin 布局，直接导航
  await page.goto('/')
  // 等待页面渲染
  await page.waitForTimeout(3000)
  await page.screenshot({ path: path.join(OUT, 'job-workspace.png'), fullPage: true })
})

test('13 - BPMN 流程设计器', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/model')

  // 等待模型列表加载（应有"请假审批"模型）
  await page.waitForSelector('.el-table', { timeout: 10000 })
  await page.waitForTimeout(1000)

  // 点击"设计"按钮打开设计器对话框
  const designBtn = page.locator('text=设计').first()
  await designBtn.click()

  // 等待 bpmn-js 设计器渲染（对话框 + canvas）
  await page.waitForSelector('.el-dialog', { timeout: 10000 })
  // bpmn-js 渲染到 .bjs-container 或 canvas
  await page.waitForSelector('.bjs-container, [ref="viewer"], .djs-container', { timeout: 15000 }).catch(() => {})
  // 给 bpmn-js 额外渲染时间
  await page.waitForTimeout(3000)

  await page.screenshot({ path: path.join(OUT, 'workflow-designer.png'), fullPage: false })
})

test('14 - 设备授权发起页', async ({ page }) => {
  await login(page)
  await page.goto('/device')
  await page.waitForSelector('.device-card', { timeout: 10000 })
  // 等待 user_code 和 QR 码渲染
  await page.waitForTimeout(3000)
  await page.screenshot({ path: path.join(OUT, 'social-device-init.png'), fullPage: true })
})

test('15 - 设备授权验证页', async ({ page }) => {
  await login(page)
  await page.goto('/device/verify')
  await page.waitForSelector('.device-card', { timeout: 10000 })
  // 等待验证码输入框渲染
  await page.waitForSelector('.user-code-input', { timeout: 5000 }).catch(() => {})
  await page.waitForTimeout(500)
  await page.screenshot({ path: path.join(OUT, 'social-device-verify.png'), fullPage: true })
})

// ===== SRM 供应商管理截图 =====

test('16 - SRM 供应商概览', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/srm/overview')
  await page.screenshot({ path: path.join(OUT, 'srm-overview.png'), fullPage: true })
})

test('17 - SRM 供应商列表', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/srm/supplier')
  await page.screenshot({ path: path.join(OUT, 'srm-supplier-list.png'), fullPage: true })
})

test('18 - SRM 绩效评估', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/srm/evaluation')
  await page.screenshot({ path: path.join(OUT, 'srm-evaluation.png'), fullPage: true })
})

test('19 - SRM 风险看板', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/srm/risk')
  await page.screenshot({ path: path.join(OUT, 'srm-risk.png'), fullPage: true })
})

test('20 - SRM 邀请管理', async ({ page }) => {
  await login(page)
  await navigateTo(page, '/admin/srm/invite')
  await page.screenshot({ path: path.join(OUT, 'srm-invite.png'), fullPage: true })
})

test('21 - SRM 供应商门户', async ({ page }) => {
  // 拦截验证码 API 响应，提取 captchaKey
  let captchaKey = ''
  await page.route('**/api/auth/captcha', async (route) => {
    const response = await route.fetch()
    const json = await response.json()
    captchaKey = json.data.captchaKey
    await route.fulfill({ response })
  })

  await page.goto('/portal-login')
  await page.waitForSelector('.login-card', { timeout: 10000 })
  await page.waitForSelector('.captcha-image img', { timeout: 10000 })
  await page.waitForTimeout(500)

  // 从 Redis 读取验证码
  if (!captchaKey) throw new Error('未能拦截到验证码 captchaKey')
  const captchaCode = await getCaptchaFromRedis(captchaKey)

  // 选择租户
  await page.click('.el-select .el-select__wrapper')
  await page.waitForSelector('.el-select-dropdown__item', { timeout: 5000 })
  await page.click('.el-select-dropdown__item')

  // 填写用户名和密码
  const formItems = page.locator('.el-form-item')
  await formItems.nth(1).locator('input').fill('supplier1')
  await formItems.nth(2).locator('input').fill('supplier123')

  // 填写验证码
  const captchaInput = page.locator('.captcha-input input')
  await captchaInput.fill(captchaCode)

  // 点击登录
  await page.click('.login-btn')

  // 等待跳转到供应商门户
  await page.waitForURL(url => url.pathname.includes('/supplier-portal'), { timeout: 15000 })
  await page.waitForTimeout(3000)

  await page.unroute('**/api/auth/captcha')
  await page.screenshot({ path: path.join(OUT, 'srm-portal.png'), fullPage: true })
})
