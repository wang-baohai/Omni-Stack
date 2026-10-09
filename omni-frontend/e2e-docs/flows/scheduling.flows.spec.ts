/**
 * @module scheduling.flows.spec
 * 调度工作台深度状态四语言正式截图：任务类型页、创建校验失败、个人任务生命周期（创建→编辑→列表）。
 * fixture：E2E_MUTATIONS 下通过 UI 自建唯一个人任务（喝水提醒 demo handler，无外部副作用），运行结束 deleteMyJob 清理。
 */
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN

const myJobsTab = {
  'zh-CN': '我的定时任务', 'en-US': 'My Scheduled Jobs',
  'ja-JP': 'My Scheduled Jobs', 'ko-KR': 'My Scheduled Jobs',
}
const createJob = { 'zh-CN': '创建任务', 'en-US': 'Create Job', 'ja-JP': 'Create Job', 'ko-KR': 'Create Job' }
const editJob = { 'zh-CN': '编辑任务', 'en-US': 'Edit Job', 'ja-JP': 'Edit Job', 'ko-KR': 'Edit Job' }
const jobNameLabel = { 'zh-CN': '任务名称', 'en-US': 'Job Name', 'ja-JP': 'Job Name', 'ko-KR': 'Job Name' }
const jobTypesLabel = { 'zh-CN': '任务类型', 'en-US': 'Job Types', 'ja-JP': 'ジョブ種別', 'ko-KR': '작업 유형' }

const runStamp = Date.now()
const jobNames = Object.fromEntries(
  docsLocales.map((locale) => [locale, { created: `E2E SCH ${locale} ${runStamp}`, edited: `E2E SCH ${locale} ${runStamp} EDITED` }]),
) as Record<string, { created: string; edited: string }>

test.describe('调度工作台深度状态文档截图', () => {
  test.skip(!adminToken, '需要通过受信任环境注入 E2E_ADMIN_TOKEN')

  test.afterAll(async () => {
    // fixture 清理：按名称检索本运行创建的个人任务，走正式 deleteMyJob 契约删除。
    if (!process.env.E2E_ADMIN_TOKEN) return
    const { request: pwRequest } = await import('@playwright/test')
    const context = await pwRequest.newContext({ baseURL: 'http://127.0.0.1:3000' })
    const headers = { Authorization: `Bearer ${process.env.E2E_ADMIN_TOKEN}` }
    for (const locale of docsLocales) {
      const list = await context.get('/api/base/my-job/list?page=1&size=50', { headers })
      const rows = (await list.json())?.data?.records ?? []
      for (const row of rows) {
        if (row.jobName === jobNames[locale]!.created || row.jobName === jobNames[locale]!.edited) {
          await context.delete(`/base/my-job/${row.id}`, { headers })
        }
      }
    }
    await context.dispose()
  })

  for (const locale of docsLocales) {
    // job-type：任务类型管理页入口状态。
    test(`job-type-page / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/admin/job/user-job-type')
      await expect(page.locator('.el-main, .app-main').first()).toContainText(jobTypesLabel[locale]!)
      await captureDocsImage(page, locale, 'scheduling-job-type')
    })

    // failure：确定性测试故障——拦截创建接口返回 500，页面执行真实错误处理（ElMessage）。
    test(`personal-create-validation / ${locale}`, async ({ page }) => {
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/')
      await page.getByRole('tab', { name: myJobsTab[locale]! }).click()
      await page.getByRole('button', { name: createJob[locale]! }).click()
      const dialog = page.locator('.el-dialog').filter({ hasText: createJob[locale]! })
      await expect(dialog).toBeVisible()
      await dialog.locator('.el-form-item').filter({ hasText: jobNameLabel[locale]! }).locator('input').fill(jobNames[locale]!.created)
      await dialog.locator('.el-form-item').filter({ hasText: jobTypesText(locale) }).locator('.el-select').click()
      await page.getByRole('option', { name: '喝水提醒' }).click()
      const cupField = dialog.locator('.dynamic-form-field').filter({ hasText: '杯型' })
      await cupField.locator('.el-select').click()
      await page.getByRole('option', { name: '小杯', exact: true }).click()
      // deterministic test fault：仅存在于测试进程，创建接口返回 500。
      await page.route('**/api/base/my-job', (route) =>
        route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":500,"message":"deterministic test fault"}' }))
      await dialog.getByRole('button', { name: confirmLabel() }).click()
      await expect(page.locator('.el-message--error, .el-message').first()).toBeVisible()
      await captureDocsImage(page, locale, 'scheduling-personal-create-validation')
      await dialog.getByRole('button', { name: /取消|Cancel|キャンセル|취소/ }).click()
      await expect(dialog).toBeHidden()
    })

    // lifecycle：创建唯一任务 → 编辑改名 → 列表真实反映（key action + success result）。
    test(`personal-lifecycle / ${locale}`, async ({ page }) => {
      const names = jobNames[locale]!
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      await page.goto('/')
      await page.getByRole('tab', { name: myJobsTab[locale]! }).click()
      await page.getByRole('button', { name: createJob[locale]! }).click()
      const dialog = page.locator('.el-dialog').filter({ hasText: createJob[locale]! })
      await expect(dialog).toBeVisible()
      // 任务类型选择 demo handler（喝水提醒，无外部副作用 seed 类型）。
      await dialog.locator('.el-form-item').filter({ hasText: jobNameLabel[locale]! }).locator('input').fill(names.created)
      await dialog.locator('.el-form-item').filter({ hasText: jobTypesText(locale) }).locator('.el-select').click()
      await page.getByRole('option', { name: '喝水提醒' }).click()
      const cupField = dialog.locator('.dynamic-form-field').filter({ hasText: '杯型' })
      await cupField.locator('.el-select').click()
      await page.getByRole('option', { name: '小杯', exact: true }).click()
      await dialog.getByRole('button', { name: confirmLabel() }).click()
      await expect(dialog).toBeHidden()
      // lifecycle action：编辑改名，列表真实反映。
      const row = page.locator('.el-table__body tr').filter({ hasText: names.created })
      await expect(row).toHaveCount(1)
      await row.getByRole('button', { name: /编辑|Edit|編集|편집/ }).first().click()
      const editDialog = page.locator('.el-dialog').filter({ hasText: editJob[locale]! })
      await expect(editDialog).toBeVisible()
      await editDialog.locator('.el-form-item').filter({ hasText: jobNameLabel[locale]! }).locator('input').fill(names.edited)
      await editDialog.getByRole('button', { name: confirmLabel() }).click()
      await expect(editDialog).toBeHidden()
      const editedRow = page.locator('.el-table__body tr').filter({ hasText: names.edited })
      await expect(editedRow).toHaveCount(1)
      await captureDocsImage(page, locale, 'scheduling-personal-lifecycle')
    })
  }
})

/** 对话框确认按钮文案（common.confirm 四语言）。 */
function confirmLabel() {
  return /确认|Confirm|確認|확인/
}

/** 任务类型表单 label（真实 key：userJob.jobType，非 common.userJobTypes）。 */
function jobTypesText(locale: string) {
  return { 'zh-CN': '任务类型', 'en-US': 'Job Type', 'ja-JP': 'Job Type', 'ko-KR': 'Job Type' }[locale]!
}
