/** 公开入口四语言正式截图。 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

const publicScenes = [
  { id: 'auth-login', route: '/login', selector: '.login-form-wrapper' },
  { id: 'auth-register', route: '/register', selector: '.el-form' },
  { id: 'auth-device-code', route: '/device', selector: '.device-card' },
  { id: 'supplier-portal-login', route: '/portal-login', selector: '.login-card' },
]

for (const locale of docsLocales) {
  for (const scene of publicScenes) {
    test(`${scene.id} / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale })
      await page.goto(scene.route)
      await waitForDocsPage(page, scene.selector)
      await captureDocsImage(page, locale, scene.id)
    })
  }
}

// expired-session：会话过期对话框（确定性测试故障——拦截待办接口返回 401，页面执行真实 handle401 降级）。
const expiredCopy = {
  'zh-CN': { title: '登录过期', action: '重新登录' },
  'en-US': { title: 'Session Expired', action: 'Sign In Again' },
  'ja-JP': { title: 'セッション期限切れ', action: '再ログイン' },
  'ko-KR': { title: '세션 만료', action: '다시 로그인' },
}

test.describe('expired-session', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    test(`session-expired-dialog / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/')
      await expect(page.locator('main').first()).toBeVisible()
      // deterministic test fault：仅存在于测试进程，待办接口返回结构化 401 触发真实 handle401。
      await page.route('**/api/workflow/**', (route) =>
        route.fulfill({ status: 401, contentType: 'application/json', body: '{"code":401,"message":"deterministic test fault: session expired"}' }))
      await page.reload()
      const dialog = page.locator('.el-message-box')
      await expect(dialog).toBeVisible()
      await expect(dialog).toContainText(expiredCopy[locale]!.title)
      await expect(dialog).toContainText(expiredCopy[locale]!.action)
      await captureDocsImage(page, locale, 'session-expired-dialog')
    })
  }
})
