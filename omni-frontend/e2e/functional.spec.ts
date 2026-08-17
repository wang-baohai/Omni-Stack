/**
 * @module functional.spec
 * 登录后功能与权限的 Playwright 断言型回归。
 *
 * 认证用例从 CI Secret 注入短期 JWT，不读取 Redis、数据库或 CAPTCHA 明文：
 * E2E_ADMIN_TOKEN、E2E_EMPLOYEE_TOKEN、E2E_SUPPLIER_TOKEN。
 */
import { expect, test, type Page } from '@playwright/test'

const adminToken = process.env.E2E_ADMIN_TOKEN
const employeeToken = process.env.E2E_EMPLOYEE_TOKEN
const supplierToken = process.env.E2E_SUPPLIER_TOKEN
const mutationsEnabled = process.env.E2E_MUTATIONS === 'true'

/** 在应用启动前注入隔离环境签发的短期令牌。 */
async function authenticate(page: Page, token: string, username: string) {
  await page.addInitScript(({ accessToken, name }) => {
    localStorage.setItem('token', accessToken)
    localStorage.setItem('username', name)
  }, { accessToken: token, name: username })
}

/** 等待页面稳定，并确保没有落入空白主内容区。 */
async function expectRendered(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('body')).not.toBeEmpty()
  await expect(page.locator('.el-main, .app-main').first()).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.el-main, .app-main').first()).not.toBeEmpty()
}

test.describe('公开入口', () => {
  for (const entry of [
    { path: '/login', selector: '.login-form-wrapper' },
    { path: '/register', selector: '.el-form' },
    { path: '/portal-login', selector: '.login-card' },
    { path: '/portal-register', selector: '.el-form' },
    { path: '/device', selector: '.device-card' },
  ]) {
    test(`${entry.path} 可渲染`, async ({ page }) => {
      await page.goto(entry.path)
      await expect(page.locator(entry.selector).first()).toBeVisible()
    })
  }

  test('登录与设备验证表单不预填仓库凭据', async ({ page }) => {
    await page.goto('/login')
    const inputs = page.locator('.login-form-wrapper input')
    await expect(inputs.nth(1)).toHaveValue('')
    await expect(inputs.nth(2)).toHaveValue('')

    await page.goto('/device/verify')
    await expect(page.locator('input[type="text"]').first()).toHaveValue('')
    await expect(page.locator('input[type="password"]').first()).toHaveValue('')
  })

  test('公开 API 返回可追踪且唯一的关联 ID', async ({ request }) => {
    const response = await request.get('/api/auth/tenants')
    expect(response.ok()).toBeTruthy()
    expect(response.headers()['x-trace-id']).toMatch(/^[a-f0-9]{32}$/)
  })
})

test.describe('管理员动态菜单与核心模块', () => {
  test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')

  test.beforeEach(async ({ page }) => {
    await authenticate(page, adminToken!, 'admin')
  })

  test('Dashboard 不展示模拟运营数字', async ({ page }) => {
    await expectRendered(page, '/admin/dashboard')
    await expect(page.getByText('运营指标尚未接入统一 Metrics 数据源')).toBeVisible()
    await expect(page.getByText('1,024')).toHaveCount(0)
    await expect(page.getByText('8,432')).toHaveCount(0)
  })

  for (const entry of [
    { path: '/admin/system/user', text: '用户' },
    { path: '/admin/workflow/model', text: '流程' },
    { path: '/admin/crm/lead', text: '线索' },
    { path: '/admin/srm/supplier', text: '供应商' },
    { path: '/admin/procurement/requisition', text: '请购' },
    { path: '/admin/asset/asset', text: '资产' },
    { path: '/admin/base/mqmessage', text: '消息' },
  ]) {
    test(`${entry.path} 可进入且包含业务内容`, async ({ page }) => {
      await expectRendered(page, entry.path)
      await expect(page.locator('.el-main, .app-main').first()).toContainText(entry.text)
    })
  }

  test('用户任务可创建、触发、产生日志并清理', async ({ request }) => {
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行写入闭环')
    const headers = { Authorization: `Bearer ${adminToken}` }
    const typesResponse = await request.get('/api/base/my-job/types', { headers })
    expect(typesResponse.ok()).toBeTruthy()
    const typesBody = await typesResponse.json()
    expect(typesBody.code).toBe(200)
    expect(typesBody.data.length).toBeGreaterThan(0)

    const jobName = `e2e-${Date.now()}`
    let jobId: number | undefined
    try {
      const createResponse = await request.post('/api/base/my-job', {
        headers,
        data: {
          jobName,
          jobType: typesBody.data[0].typeCode,
          cronExpression: '0 0 0 1 1 ? 2099',
        },
      })
      expect(createResponse.ok()).toBeTruthy()
      const created = await createResponse.json()
      expect(created.code).toBe(200)
      jobId = created.data.id

      const triggerResponse = await request.post(`/api/base/my-job/${jobId}/trigger`, { headers })
      expect(triggerResponse.ok()).toBeTruthy()
      await expect.poll(async () => {
        const logsResponse = await request.get(`/api/base/my-job/${jobId}/logs?page=1&size=10`, { headers })
        const logs = await logsResponse.json()
        return logs.data?.total || 0
      }, { timeout: 20_000, intervals: [1_000, 2_000] }).toBeGreaterThan(0)
    } finally {
      if (jobId) {
        const deleteResponse = await request.delete(`/api/base/my-job/${jobId}`, { headers })
        expect(deleteResponse.ok()).toBeTruthy()
      }
    }
  })
})

test.describe('普通员工权限恢复', () => {
  test.skip(!employeeToken, '需要通过 CI Secret 注入 E2E_EMPLOYEE_TOKEN')

  test('无权限 CRM 深链显示 403 而不是空白页', async ({ page }) => {
    await authenticate(page, employeeToken!, 'employee')
    await page.goto('/admin/crm/lead')
    await expect(page.getByText('403')).toBeVisible()
    await expect(page.getByRole('button', { name: /返回/ }).first()).toBeVisible()
  })
})

test.describe('供应商门户', () => {
  test.skip(!supplierToken, '需要通过 CI Secret 注入 E2E_SUPPLIER_TOKEN')

  test('窄屏门户可进入且后台路由被隔离', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticate(page, supplierToken!, 'supplier')
    await page.goto('/supplier-portal')
    await expect(page.locator('.portal-page')).toBeVisible()
    await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')

    await page.goto('/admin/dashboard')
    await expect(page).toHaveURL(/\/supplier-portal/)
  })
})
