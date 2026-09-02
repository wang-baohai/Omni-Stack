/**
 * @module permissions.flows.spec
 * permissions-exceptions 模块正式截图：员工越权 403 与员工可见范围。
 * 复用 E2eTokenFixture 注入的 E2E_EMPLOYEE_TOKEN（zhangsan），认证已由 G2 真实验证。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage } from '../fixtures/docs-page'

const employeeToken = process.env.E2E_EMPLOYEE_TOKEN
const supplierToken = process.env.E2E_SUPPLIER_TOKEN
const adminToken = process.env.E2E_ADMIN_TOKEN

test.describe('权限例外文档截图', () => {
  test.skip(!employeeToken, '需要通过受信任环境注入 E2E_EMPLOYEE_TOKEN')

  for (const locale of docsLocales) {
    // 员工工作台 tab 文案（workflow.todo）：四语言渲染值。
    const todoTab = { 'zh-CN': '待我审批', 'en-US': 'My Tasks', 'ja-JP': 'My Tasks', 'ko-KR': 'My Tasks' }[locale]!

    // forbidden-403：已认证员工访问无权限管理页，必须呈现 AUTHENTICATED_BUT_FORBIDDEN 而非跳登录。
    test(`employee-forbidden-403 / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: employeeToken, username: 'zhangsan' })
      await page.goto('/admin/procurement/approval-route')
      await expect(page.getByText('403')).toBeVisible()
      await expect(page.getByRole('button', { name: /返回|Back|戻る|돌아가기/ }).first()).toBeVisible()
      // 认证状态证据：登录身份仍为 zhangsan（排除 401/未认证跳登录页的假 403）。
      await expect(page.locator('header, .navbar, .app-header').first()).toContainText('zhangsan')
      await captureDocsImage(page, locale, 'employee-forbidden-403')
    })

    // employee-scope：已认证员工的正常可见范围（审批工作台渲染且不含管理端 403）。
    test(`employee-workspace-scope / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: employeeToken, username: 'zhangsan' })
      await page.goto('/')
      const mainArea = page.locator('main').first()
      await expect(mainArea).toBeVisible()
      await expect(mainArea).toContainText(todoTab)
      await expect(page.locator('header, .navbar, .app-header').first()).toContainText('zhangsan')
      await expect(mainArea).not.toContainText('403')
      await captureDocsImage(page, locale, 'employee-workspace-scope')
    })
  }

  // supplier-scope：正式 seed supplier1（SUPPLIER 角色）访问其合法门户范围。
  test.describe('supplier-scope', () => {
    test.skip(!supplierToken, '需要通过受信任环境注入 E2E_SUPPLIER_TOKEN')
    for (const locale of docsLocales) {
      test(`supplier-portal-scope / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: supplierToken, username: 'supplier1' })
        await page.goto('/supplier-portal')
        await expect(page.locator('.portal-page').first()).toBeVisible()
        await expect(page.locator('header, .navbar, .app-header').first()).toContainText('supplier1')
        await captureDocsImage(page, locale, 'supplier-portal-scope')
      })
    }
  })

  // not-found-404：产品 NotFound 页（statusCode=404）对未知管理路由的真实行为。
  test.describe('not-found-404', () => {
    test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')
    for (const locale of docsLocales) {
      test(`resource-not-found / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto('/e2e-nonexistent-404-probe')
        await expect(page.getByText('404')).toBeVisible()
        await captureDocsImage(page, locale, 'resource-not-found-404')
      })
    }
  })

  // menu-failure：菜单接口 500 → 守卫重定向 /menu-load-error 降级页（本地化标题 + 重试/回首页）。
  test.describe('menu-failure', () => {
    test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')
    // 降级页核心文案（errorPage.menuLoadTitle / errorPage.retry 四语言渲染值；ja/ko 复用英文技术文案）。
    const menuFailTitle = {
      'zh-CN': '功能菜单加载失败',
      'en-US': 'Could not load the feature menu',
      'ja-JP': 'Could not load the feature menu',
      'ko-KR': 'Could not load the feature menu',
    }
    const retryLabel = { 'zh-CN': '重新加载', 'en-US': 'Retry', 'ja-JP': 'Retry', 'ko-KR': 'Retry' }
    for (const locale of docsLocales) {
      test(`admin-menu-load-failure / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        // deterministic test fault：仅存在于测试进程，拦截菜单接口返回 500。
        await page.route('**/api/auth/menus*', (route) =>
          route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":500,"message":"deterministic test fault"}' }))
        await page.goto('/admin/dashboard')
        // 降级语义：守卫捕获失败并重定向降级页，不得白屏或伪装成功菜单。
        await expect(page.locator('.menu-error-page').first()).toBeVisible()
        await expect(page.locator('.menu-error-page')).toContainText(menuFailTitle[locale]!)
        await expect(page.getByRole('button', { name: retryLabel[locale]! })).toBeVisible()
        await expect(page.locator('.menu-error-page')).not.toContainText('403')
        await captureDocsImage(page, locale, 'admin-menu-load-failure')
      })
    }
  })

  // api-failure：确定性测试故障——审批规则列表接口 500，验证列表页错误处理 UI。
  test.describe('api-failure', () => {
    test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')
    for (const locale of docsLocales) {
      test(`approval-route-list-failure / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto('/admin/procurement/approval-route')
        // deterministic test fault：仅在导航完成后拦截列表接口，页面真实执行现有错误处理。
        await page.route('**/api/procurement/approval-route/list*', (route) =>
          route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":500,"message":"deterministic test fault"}' }))
        await page.reload()
        await expect(page.locator('.approval-rules-page').first()).toBeVisible()
        await captureDocsImage(page, locale, 'approval-route-list-failure')
      })
    }
  })
})
