/**
 * @module crm.flows.spec
 * CRM 线索深度状态四语言正式截图：create 表单、必填校验失败、创建成功、越权 403。
 * fixture：E2E_MUTATIONS 下通过 UI 自建唯一线索，运行结束 API 清理（deleteLead 正式契约）。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN
const employeeToken = process.env.E2E_EMPLOYEE_TOKEN

const createLabel = { 'zh-CN': '新建线索', 'en-US': 'Create Lead', 'ja-JP': 'リードを作成', 'ko-KR': '리드 추가' }
const nameRequired = {
  'zh-CN': '请输入联系人姓名', 'en-US': 'Enter the contact name',
  'ja-JP': '連絡先の氏名を入力してください', 'ko-KR': '연락처 이름을 입력하세요',
}
const saveLabel = { 'zh-CN': '保存', 'en-US': 'Save', 'ja-JP': '保存', 'ko-KR': '저장' }

const runStamp = Date.now()
const leadNames = Object.fromEntries(
  docsLocales.map((locale) => [locale, `E2E CRM ${locale} ${runStamp}`]),
) as Record<string, string>

test.describe('CRM 深度状态文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  test.afterAll(async () => {
    // fixture 清理：按名称检索本运行创建的线索，走正式 deleteLead 契约删除。
    if (!process.env.E2E_ADMIN_TOKEN) return
    const context = await playwrightRequest()
    for (const locale of docsLocales) {
      const name = leadNames[locale]
      const list = await context.get(`/api/crm/lead/list?page=1&size=50&keyword=${encodeURIComponent(name)}`, {
        headers: { Authorization: `Bearer ${process.env.E2E_ADMIN_TOKEN}` },
      })
      const rows = (await list.json())?.data?.records ?? []
      for (const row of rows) {
        if (row.fullName === name) {
          await context.delete(`/api/crm/lead/${row.id}?version=${row.version}`, {
            headers: { Authorization: `Bearer ${process.env.E2E_ADMIN_TOKEN}` },
          })
        }
      }
    }
    await context.dispose()
  })

  /** 独立 API 上下文（afterAll 无 request fixture）。 */
  async function playwrightRequest() {
    const { request: pwRequest } = await import('@playwright/test')
    return pwRequest.newContext({ baseURL: 'http://127.0.0.1:3000' })
  }

  for (const locale of docsLocales) {
    // create-form + validation-failure：新建对话框渲染，空提交触发必填校验。
    test(`lead-create-form-validation / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/crm/lead')
      await expect(page.getByRole('button', { name: createLabel[locale]! }).first()).toBeVisible()
      await page.getByRole('button', { name: createLabel[locale]! }).first().click()
      const dialog = page.locator('.el-dialog').first()
      await expect(dialog).toBeVisible()
      // 必填校验失败：空 fullName 提交。
      await dialog.getByRole('button', { name: saveLabel[locale]! }).click()
      await expect(page.getByText(nameRequired[locale]!).first()).toBeVisible()
      await captureDocsImage(page, locale, 'crm-lead-create-validation')
      // 关闭对话框，供成功场景复用。
      await dialog.getByRole('button', { name: /取消|Cancel|キャンセル|취소/ }).click()
      await expect(dialog).toBeHidden()
    })

    // create-success：填写唯一线索并保存，列表真实反映新数据。
    test(`lead-create-success / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/crm/lead')
      await page.getByRole('button', { name: createLabel[locale]! }).first().click()
      const dialog = page.locator('.el-dialog').first()
      await expect(dialog).toBeVisible()
      const nameInput = dialog.locator('.el-form-item').filter({ hasText: /姓名|Name|氏名|이름/ }).first().locator('input').first()
      await nameInput.fill(leadNames[locale]!)
      await dialog.getByRole('button', { name: saveLabel[locale]! }).click()
      await expect(dialog).toBeHidden()
      // 成功证据：搜索框检索唯一名称，列表真实出现新线索。
      const keywordInput = page.locator('.el-main input[type="text"], .app-main input[type="text"]').first()
      await keywordInput.fill(leadNames[locale]!)
      await keywordInput.press('Enter')
      await expect(page.locator('.el-table__body')).toContainText(leadNames[locale]!)
      await captureDocsImage(page, locale, 'crm-lead-create-success')
    })

    // permission-and-failure：已认证员工访问 CRM 深链，呈现 AUTHENTICATED_BUT_FORBIDDEN。
    test(`lead-forbidden-403 / ${locale}`, async ({ page }) => {
      test.skip(!employeeToken, '需要通过受信任环境注入 E2E_EMPLOYEE_TOKEN')
      await prepareDocsPage(page, { locale, token: employeeToken, username: 'zhangsan' })
      await page.goto('/admin/crm/lead')
      await expect(page.getByText('403')).toBeVisible()
      await expect(page.locator('header, .navbar, .app-header').first()).toContainText('zhangsan')
      await captureDocsImage(page, locale, 'crm-lead-forbidden-403')
    })
  }
})
