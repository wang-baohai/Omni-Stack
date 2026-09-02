/** 管理端主要模块四语言正式截图。 */
import { test } from '@playwright/test'
import { expect } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

// expectText 与 docsLocales 顺序一一对应，作为每张截图前的核心文案断言。
const adminScenes = [
  { id: 'system-dashboard', route: '/admin/dashboard', expectText: ['欢迎', 'Welcome', 'ようこそ', '환영합니다'] },
  { id: 'system-users', route: '/admin/system/user', expectText: ['用户管理', 'User Management', 'ユーザー管理', '사용자 관리'] },
  { id: 'scheduling-system-jobs', route: '/admin/job/system-job', expectText: ['Cron 表达式', 'Cron Expression', 'Cron Expression', 'Cron Expression'] },
  { id: 'monitor-mq-messages', route: '/admin/base/mqmessage', expectText: ['MQ 消息记录', 'MQ Messages', 'MQ Messages', 'MQ Messages'] },
  { id: 'workflow-models', route: '/admin/workflow/model', expectText: ['流程分类', 'Category', 'プロセス分類', '프로세스 분류'] },
  { id: 'crm-overview', route: '/admin/crm/overview', expectText: ['销售概览', 'Sales Overview', '営業概要', '영업 개요'] },
  { id: 'srm-overview', route: '/admin/srm/overview', expectText: ['供应商总数', 'Total Suppliers', 'サプライヤー総数', '전체 공급업체'] },
  { id: 'procurement-overview', route: '/admin/procurement/overview', expectText: ['采购概览', 'Procurement Overview', '調達概要', '조달 개요'] },
  { id: 'procurement-approval-rules', route: '/admin/procurement/approval-route', expectText: ['请购审批规则', 'Requisition Approval Rules', '購買申請承認ルール', '구매 신청 승인 규칙'] },
  { id: 'asset-overview', route: '/admin/asset/overview', expectText: ['资产总数', 'Total Assets', '資産総数', '전체 자산'] },
]

test.describe('管理员文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    for (const scene of adminScenes) {
      test(`${scene.id} / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto(scene.route)
        await waitForDocsPage(page, '.el-main, .app-main')
        // 核心文案断言：确保语言正确加载且主内容渲染，不能只 waitFor 后截图。
        await expect(page.locator('.el-main, .app-main').first())
          .toContainText(scene.expectText[docsLocales.indexOf(locale)])
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
      await waitForDocsPage(page, '.approval-rules-page')
      await expect(page.locator('.approval-rules-page')).toContainText('请购审批规则')
      await captureDocsImage(page, 'zh-CN', `procurement-approval-rules-${viewport.id}`)
    })
  }
})
