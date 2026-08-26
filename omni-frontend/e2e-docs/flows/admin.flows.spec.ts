/** 管理端主要模块四语言正式截图。 */
import { test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

const adminScenes = [
  { id: 'system-dashboard', route: '/admin/dashboard' },
  { id: 'system-users', route: '/admin/system/user' },
  { id: 'scheduling-system-jobs', route: '/admin/job/system-job' },
  { id: 'monitor-mq-messages', route: '/admin/base/mqmessage' },
  { id: 'workflow-models', route: '/admin/workflow/model' },
  { id: 'crm-overview', route: '/admin/crm/overview' },
  { id: 'srm-overview', route: '/admin/srm/overview' },
  { id: 'procurement-overview', route: '/admin/procurement/overview' },
  { id: 'procurement-approval-rules', route: '/admin/procurement/approval-route' },
  { id: 'asset-overview', route: '/admin/asset/overview' },
]

test.describe('管理员文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    for (const scene of adminScenes) {
      test(`${scene.id} / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto(scene.route)
        await waitForDocsPage(page, '.el-main, .app-main')
        await captureDocsImage(page, locale, scene.id)
      })
    }
  }
})

test.describe('审批规则响应式截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const viewport of [
    { id: 'mobile', width: 390, height: 844 },
    { id: 'tablet', width: 1024, height: 768 },
  ]) {
    test(`procurement-approval-rules-${viewport.id} / zh-CN`, async ({ page }) => {
      await prepareDocsPage(page, { locale: 'zh-CN', token: adminToken, username: 'admin', viewport })
      await page.goto('/admin/procurement/approval-route')
      await waitForDocsPage(page, '.approval-route-page')
      await captureDocsImage(page, 'zh-CN', `procurement-approval-rules-${viewport.id}`)
    })
  }
})
