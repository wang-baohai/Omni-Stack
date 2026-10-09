/**
 * @module wp006-visual.flows.spec
 * WP006 采购链四语截图：连接 Docker 前端（external 模式），只读采集真实主链终态。
 *
 * 真实业务状态（对应 .qoder/wp006-evidence/r1 的 S09-S11 主链证据）：
 * - WP005 两类基础物料（WP005FIX_ASET_… 资产化 / WP005FIX_NOAS_… 非资产化）
 * - REQ PR-1-1 APPROVED（approvalAttempt、processInstanceId）
 * - RFQ-1-1 AWARDED（两供应商报价，winningQuotation 定点结果）
 * - PO-1-1 RECEIVED（两行，9000 CNY）
 * - GR-1-1 / GR-1-2 / GR-1-3 CONFIRMED（PASS/PENDING→PASS/FAIL 质检三态）
 * - 审批规则 5/6/7（LAPTOP 三金额区间 ACTIVE）
 *
 * 只读约束：唯一交互均为只读——详情 drawer（列表 GET）与规则试算
 * （match-preview 服务端只读解析）。不创建/修改/删除任何业务对象，
 * 不使用 page.route 注入业务数据；品类下拉值为租户共享 DB 名称。
 */
import { expect, test, type Page } from '@playwright/test'
import {
  captureDocsImage,
  docsLocales,
  prepareDocsPage,
  waitForDocsPage,
  type DocsViewport,
} from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN
const employeeToken = process.env.E2E_EMPLOYEE_TOKEN
const supplierToken = process.env.E2E_SUPPLIER_TOKEN
const supplier2Token = process.env.E2E_SUPPLIER2_TOKEN

// 与 docsLocales 顺序一一对应的操作文案。
const detailLabel = { 'zh-CN': '详情', 'en-US': 'Detail', 'ja-JP': '詳細', 'ko-KR': '상세' } as const
const rulesTitle = {
  'zh-CN': '请购审批规则',
  'en-US': 'Requisition Approval Rules',
  'ja-JP': '購買申請承認ルール',
  'ko-KR': '구매 신청 승인 규칙',
} as const
const overviewTitle = {
  'zh-CN': '采购概览',
  'en-US': 'Procurement Overview',
  'ja-JP': '調達概要',
  'ko-KR': '조달 개요',
} as const

// 品类名来自租户共享 DB 值（proc_material_category.category_name），与界面语言无关。
const laptopCategoryName = '笔记本电脑'
// 试算金额 5000 落入规则 5 [0, 10000) 区间（主链 REQ 同款规则），断言服务端返回的真实路由名。
const matchedRouteName = 'WP006FIX-LAPTOP-small'

const desktopViewport: DocsViewport = { width: 1440, height: 900 }
const responsiveViewports: Array<{ id: string; viewport: DocsViewport }> = [
  { id: 'mobile', viewport: { width: 390, height: 844 } },
  { id: 'tablet', viewport: { width: 1024, height: 768 } },
]

/** 打开指定单据行的详情 drawer 并等待目标内容渲染。 */
async function openRowDetail(page: Page, rowPattern: RegExp, locale: keyof typeof detailLabel) {
  const row = page.getByRole('row', { name: rowPattern })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: detailLabel[locale] }).click()
  const drawer = page.locator('.el-drawer:visible')
  await expect(drawer).toBeVisible()
  await expect(page.locator('.el-drawer__body .el-loading-mask:visible')).toHaveCount(0)
  return drawer
}

test.describe('WP006 采购主链只读截图（admin）', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  for (const locale of docsLocales) {
    // 物料目录：真实 WP005 两类基础物料（borrowed，不修改）。
    test(`procurement-material-list / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/material')
      await waitForDocsPage(page, '.material-page')
      await expect(page.locator('.material-page')).toContainText('WP005FIX')
      // 就绪断言：品类树与表格异步加载全部结束，避免残留 loading 态入图。
      await page.waitForLoadState('networkidle')
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await captureDocsImage(page, locale, 'procurement-material-list')
    })

    // 请购列表：PR-1-1 真实 APPROVED。
    test(`procurement-requisition-list / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/requisition')
      await waitForDocsPage(page, '.requisition-page')
      await expect(page.locator('.requisition-page')).toContainText('PR-1-1')
      await captureDocsImage(page, locale, 'procurement-requisition-list')
    })

    // 请购详情：审批 attempt、流程实例 ID 与明细行（真实 workflow 审批结果呈现）。
    test(`procurement-requisition-detail / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/requisition')
      await waitForDocsPage(page, '.requisition-page')
      const drawer = await openRowDetail(page, /PR-1-1/, locale)
      await expect(drawer).toContainText('PR-1-1')
      await captureDocsImage(page, locale, 'procurement-requisition-detail')
    })

    // RFQ 列表：RFQ-1-1 真实 AWARDED。
    test(`procurement-rfq-list / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/rfq')
      await waitForDocsPage(page, '.rfq-page')
      await expect(page.locator('.rfq-page')).toContainText('RFQ-1-1')
      await captureDocsImage(page, locale, 'procurement-rfq-list')
    })

    // RFQ 详情：报价接收（两供应商 quotationId）与定点结果（winningQuotation #id / v）。
    test(`procurement-rfq-detail-award / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/rfq')
      await waitForDocsPage(page, '.rfq-page')
      const drawer = await openRowDetail(page, /RFQ-1-1/, locale)
      await expect(drawer).toContainText('RFQ-1-1')
      // 定点结果行：#<quotationId> / v<version>，来自真实 award 快照。
      await expect(drawer).toContainText(/#\d+ \/ v\d+/)
      await captureDocsImage(page, locale, 'procurement-rfq-detail-award')
    })

    // 采购订单列表：PO-1-1 真实 RECEIVED。
    test(`procurement-purchase-order-list / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/purchase-order')
      await waitForDocsPage(page, '.purchase-order-page')
      await expect(page.locator('.purchase-order-page')).toContainText('PO-1-1')
      await captureDocsImage(page, locale, 'procurement-purchase-order-list')
    })

    // 采购订单详情：PurchaseOrderTracker 行级进度（两行真实收货进度）。
    test(`procurement-purchase-order-detail / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/purchase-order')
      await waitForDocsPage(page, '.purchase-order-page')
      const drawer = await openRowDetail(page, /PO-1-1/, locale)
      await expect(drawer).toContainText('PO-1-1')
      await captureDocsImage(page, locale, 'procurement-purchase-order-detail')
    })

    // 收货列表：三张真实收货单（分批收货）。
    test(`procurement-goods-receipt-list / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/goods-receipt')
      await waitForDocsPage(page, '.goods-receipt-page')
      await expect(page.locator('.goods-receipt-page')).toContainText('GR-1-1')
      await expect(page.locator('.goods-receipt-page')).toContainText('GR-1-2')
      await expect(page.locator('.goods-receipt-page')).toContainText('GR-1-3')
      await captureDocsImage(page, locale, 'procurement-goods-receipt-list')
    })

    // 收货详情：GR-1-2 质检两态真实呈现（material1 行 FAIL、material2 行 PASS）。
    test(`procurement-goods-receipt-detail-quality / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/goods-receipt')
      await waitForDocsPage(page, '.goods-receipt-page')
      const drawer = await openRowDetail(page, /GR-1-2/, locale)
      await expect(drawer).toContainText('GR-1-2')
      await captureDocsImage(page, locale, 'procurement-goods-receipt-detail-quality')
    })

    // 采购概览：真实主链数据补核（覆盖 WP005 期空数据旧图）。
    test(`procurement-overview / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/overview')
      await waitForDocsPage(page, '.procurement-overview-page')
      await expect(page.locator('.procurement-overview-page')).toContainText(overviewTitle[locale])
      await captureDocsImage(page, locale, 'procurement-overview')
    })

    // 审批规则桌面 1440×900：真实 LAPTOP 三区间规则列表。
    test(`procurement-approval-rules / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin', viewport: desktopViewport })
      await page.goto('/admin/procurement/approval-route')
      await waitForDocsPage(page, '.approval-rules-page')
      await expect(page.locator('.approval-rules-page')).toContainText(rulesTitle[locale])
      await expect(page.locator('.approval-rules-page')).toContainText('LAPTOP')
      await captureDocsImage(page, locale, 'procurement-approval-rules')
    })

    // 规则响应式 390×844 / 1024×768：四语关键可用性（无横向溢出、入口完整）。
    for (const { id, viewport } of responsiveViewports) {
      test(`procurement-approval-rules-${id} / ${locale}`, async ({ page }) => {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin', viewport })
        await page.goto('/admin/procurement/approval-route')
        await waitForDocsPage(page, '.approval-rules-page')
        await expect(page.locator('.approval-rules-page')).toContainText(rulesTitle[locale])
        await captureDocsImage(page, locale, `procurement-approval-rules-${id}`)
      })
    }

    // 规则试算成功：服务端 match-preview 只读解析，MATCHED 呈现真实路由名与审批图。
    test(`procurement-rule-match-success / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/procurement/approval-route')
      await waitForDocsPage(page, '.approval-rules-page')
      const tester = page.locator('.match-tester')
      await tester.locator('.el-select').first().click()
      await page.locator('.el-select-dropdown__item:visible', { hasText: laptopCategoryName })
        .first()
        .click()
      // 金额输入是试算表单第二个 form-item（第一个是品类下拉）。
      const amountInput = tester.locator('.match-form .el-form-item').nth(1).locator('input')
      await amountInput.fill('5000')
      await tester.locator('button[type="submit"]').click()
      const result = tester.locator('.match-result')
      await expect(result.locator('.el-alert--success')).toBeVisible()
      await expect(result).toContainText(matchedRouteName)
      await captureDocsImage(page, locale, 'procurement-rule-match-success')
    })
  }
})

test.describe('WP006 采购禁止态截图（employee 403 / supplier 域隔离）', () => {
  test.skip(!employeeToken && !supplierToken, '需要通过受信任环境注入 E2E_EMPLOYEE_TOKEN / E2E_SUPPLIER_TOKEN')

  // 真实角色禁止（403 页呈现）：EMPLOYEE 无采购管理权限，访问物料页呈现 403 拒绝页而非空列表。
  for (const locale of docsLocales) {
    test(`procurement-forbidden-403 / ${locale}`, async ({ page }) => {
      test.skip(!employeeToken, '需要通过受信任环境注入 E2E_EMPLOYEE_TOKEN')
      await prepareDocsPage(page, { locale, token: employeeToken, username: 'zhangsan' })
      await page.goto('/admin/procurement/material')
      await expect(page.getByText('403')).toBeVisible()
      await expect(page.getByRole('button', { name: /返回|Back|戻る|돌아가기/ }).first()).toBeVisible()
      await captureDocsImage(page, locale, 'procurement-forbidden-403')
    })
  }

  // 真实角色域隔离（守卫重定向）：SUPPLIER 访问采购管理路由被守卫重定向回供应商门户，
  // 不进入 403 拒绝页而是回到其授权域——这是当前产品的真实禁止语义，如实呈现。
  for (const locale of docsLocales) {
    test(`procurement-supplier-domain-isolation / ${locale}`, async ({ page }) => {
      test.skip(!supplierToken, '需要通过受信任环境注入 E2E_SUPPLIER_TOKEN')
      await prepareDocsPage(page, { locale, token: supplierToken, username: 'supplier1' })
      await page.goto('/admin/procurement/material')
      await expect(page).toHaveURL(/\/supplier-portal/)
      await expect(page.locator('.portal-page, main').first()).toBeVisible()
      await captureDocsImage(page, locale, 'procurement-supplier-domain-isolation')
    })
  }
})

test.describe('WP006 供应商门户真实报价记录（supplier2，报价接收侧呈现）', () => {
  test.skip(!supplier2Token, '需要通过受信任环境注入 E2E_SUPPLIER2_TOKEN')

  // 门户询价报价页签：本轮真实交易的报价记录（RFQ-1-1 报价 #2 已中标），
  // 与上一包 srm-portal-quotation-* 独立 fixture 资产相互独立，来源为 WP006 主链。
  const tabLabel = { 'zh-CN': '询价报价', 'en-US': 'RFQ Quotations', 'ja-JP': '見積回答', 'ko-KR': '견적 응답' } as const

  for (const locale of docsLocales) {
    test(`procurement-portal-quotation-record / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: supplier2Token, username: 'supplier2' })
      await page.goto('/supplier-portal')
      const tab = page.getByRole('tab', { name: tabLabel[locale] })
      await expect(tab).toBeVisible()
      await tab.click()
      await expect(page.locator('.portal-page, main').first()).toContainText('RFQ-1-1')
      await captureDocsImage(page, locale, 'procurement-portal-quotation-record')
    })
  }
})
