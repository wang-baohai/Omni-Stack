/** 公开入口四语言正式截图。 */
import { test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

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
