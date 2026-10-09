/**
 * @module srm-portal-responsive.flows.spec
 * SRM 供应商门户在移动/平板视口下的稳定可用性四语言正式截图（coverage: srm `stable-mobile-flow`）。
 *
 * 只读采集：仅打开门户并切换页签、点击刷新，不创建/修改/删除任何报价或供应商数据，
 * 因此不要求 `E2E_MUTATIONS`，也无数据收尾。
 *
 * 页签与按钮文案沿用 `srm.flows.spec.ts` 中已实测通过的四语言 selector 值，不重新推测。
 * 视口尺寸沿用仓库既有响应式约定（procurement approval-rules 的 390×844 与 1024×768）。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, type DocsLocale } from '../fixtures/docs-page'

const supplierToken = process.env.E2E_SUPPLIER_TOKEN

/** 询价报价页签的实际渲染文案（与 srm.flows.spec.ts 一致，已由通过的运行验证）。 */
const tabLabel: Record<DocsLocale, string> = {
  'zh-CN': '询价报价', 'en-US': 'RFQ Quotations', 'ja-JP': '見積回答', 'ko-KR': '견적 응답',
}
const refreshLabel: Record<DocsLocale, string> = {
  'zh-CN': '刷新询价', 'en-US': 'Refresh RFQs', 'ja-JP': '見積依頼を更新', 'ko-KR': '견적 요청 새로고침',
}

/** 响应式视口，沿用仓库既有约定。 */
const viewports = [
  { id: 'mobile', width: 390, height: 844 },
  { id: 'tablet', width: 1024, height: 768 },
] as const

test.describe('SRM 供应商门户响应式文档截图', () => {
  test.skip(!supplierToken, '需要通过受信任环境注入 E2E_SUPPLIER_TOKEN')

  for (const viewport of viewports) {
    for (const locale of docsLocales) {
      test(`srm-portal-quotation-${viewport.id} / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, {
          locale,
          token: supplierToken,
          username: 'supplier1',
          viewport: { width: viewport.width, height: viewport.height },
        })
        await page.goto('/supplier-portal')
        // 页签在该视口下必须可见且可点击：这是「稳定可用」的核心判据，而非仅截图。
        const tab = page.getByRole('tab', { name: tabLabel[locale] })
        await expect(tab).toBeVisible()
        await tab.click()
        // 刷新按钮在该视口下仍可交互，证明操作入口未被窄屏挤出或遮挡。
        await expect(page.getByRole('button', { name: refreshLabel[locale] }).first()).toBeVisible()
        await expect(page.getByRole('button', { name: refreshLabel[locale] }).first()).toBeEnabled()
        await captureDocsImage(page, locale, `srm-portal-quotation-${viewport.id}`)
      })
    }
  }
})
