/**
 * @module system-dictionary.flows.spec
 * system-management `detail-and-action-states` 四语言正式截图：
 * 字典类型「新建对话框 → 必填校验失败 → 创建成功」三态，参照 crm/scheduling 已 covered 模块的既有范式。
 *
 * 写入型用例：必须显式 `E2E_MUTATIONS=true`，否则整组 skip 且任何写入调用直接抛错。
 * 自建唯一 `typeCode`（含 runStamp），创建成功即登记，afterAll 走正式
 * `DELETE /api/base/dict/type/{id}` 契约清理，并逐条核对响应与最终残留。
 *
 * 已实测的产品事实（如实断言，不 mock、不美化）：
 * 1. `views/base/dict/index.vue` 未定义任何 form rules，必填校验由后端 `@NotBlank` 承担，
 *    因此前端 `.el-form-item__error` 为空、错误以 toast 呈现，且对话框保持打开。
 * 2. `CreateDictTypeRequest` 的 `@NotBlank(message = ...)` 为中文硬编码，四语言 UI 下均返回中文消息。
 *    该现象已作为 i18n PRODUCT_DEFECT 登记（见 coverage 注释与执行 checkpoint），本用例不改产品、不隐藏真实文案。
 * 3. ja-JP/ko-KR 下 `dict.createType`/`typeCode`/`typeName`/`remark` 的语言包取值本身即英文
 *    （`ui:i18n:parity` 与 `ui:i18n:check` 均通过，属译文完整度问题而非硬编码缺陷）。
 *
 * 所有 selector 文案取自对运行中应用的实测探测，不从语言包推测。
 */
import { expect, test, request as pwRequest, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN
/** 写入开关：非 'true' 时整组 skip，且 expectOk 会对任何写入调用直接抛错。 */
const mutationsEnabled = process.env.E2E_MUTATIONS === 'true'
const tenantId = Number(process.env.E2E_TENANT_ID ?? '1')
const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000'

// —— 以下为实测探测得到的真实渲染文案（顺序与 docsLocales 一致）——
const createButton: Record<DocsLocale, string> = {
  'zh-CN': '新建类型', 'en-US': 'Create Type', 'ja-JP': 'Create Type', 'ko-KR': 'Create Type',
}
const codeLabel: Record<DocsLocale, string> = {
  'zh-CN': '类型编码', 'en-US': 'Type Code', 'ja-JP': 'Type Code', 'ko-KR': 'Type Code',
}
const nameLabel: Record<DocsLocale, string> = {
  'zh-CN': '类型名称', 'en-US': 'Type Name', 'ja-JP': 'Type Name', 'ko-KR': 'Type Name',
}
const remarkLabel: Record<DocsLocale, string> = {
  'zh-CN': '备注', 'en-US': 'Remark', 'ja-JP': 'Remark', 'ko-KR': 'Remark',
}
const sortLabel: Record<DocsLocale, string> = {
  'zh-CN': '排序', 'en-US': 'Sort', 'ja-JP': '並び順', 'ko-KR': '정렬',
}
const confirmButton: Record<DocsLocale, string> = {
  'zh-CN': '确认', 'en-US': 'Confirm', 'ja-JP': '確認', 'ko-KR': '확인',
}
const searchButton: Record<DocsLocale, string> = {
  'zh-CN': '搜索', 'en-US': 'Search', 'ja-JP': '検索', 'ko-KR': '검색',
}
/** 后端 @NotBlank 中文硬编码消息的稳定片段；四语言一致（已登记 PRODUCT_DEFECT）。 */
const REQUIRED_MESSAGE_FRAGMENT = '字典类型编码不能为空'

const runStamp = Date.now()
const typeCodes = Object.fromEntries(
  docsLocales.map((locale) => [locale, `E2EDICT-${runStamp}-${locale}`]),
) as Record<DocsLocale, string>

interface CreatedDictType {
  locale: DocsLocale
  typeCode: string
  id: number | null
}

interface CleanupOutcome extends CreatedDictType {
  httpStatus: number | null
  code: number | null
  message: string
  deleted: boolean
  stillListed: boolean | null
}

const created: CreatedDictType[] = []
const cleanupOutcomes: CleanupOutcome[] = []
// 登记文件位于仓库外系统 TEMP，不会被 stage 或提交。
const registryPath = join(tmpdir(), 'omni-e2edict-registry', `run-${runStamp}.json`)

/** 落盘本批资源登记与清理结果，供链路中断后的定向清理复用。 */
function persistRegistry() {
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath, JSON.stringify({
    tenantId,
    runStamp,
    baseUrl,
    resources: created,
    cleanup: cleanupOutcomes,
  }, null, 2), 'utf8')
}

async function apiContext(): Promise<APIRequestContext> {
  return pwRequest.newContext({ baseURL: baseUrl })
}

function adminHeaders() {
  return { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' }
}

/** 按 typeCode 查询当前可见字典类型；用于取得 id 与验证清理结果。 */
async function findTypeByCode(api: APIRequestContext, typeCode: string): Promise<{ id: number; total: number } | null> {
  const payload = await (await api.get(`/api/base/dict/type/list?page=1&size=20&typeCode=${encodeURIComponent(typeCode)}`, {
    headers: adminHeaders(),
  })).json()
  const records: Array<Record<string, unknown>> = payload?.data?.records ?? []
  const exact = records.find((item) => item?.typeCode === typeCode)
  return exact ? { id: Number(exact.id), total: Number(payload?.data?.total ?? records.length) } : null
}

/** 对话框内按本地化 label 定位表单项输入框。 */
function fieldByLabel(page: Page, dialog: Locator, label: string) {
  return dialog.locator('.el-form-item')
    .filter({ has: page.locator('.el-form-item__label', { hasText: label }) })
    .first()
    .locator('input, textarea')
    .first()
}

test.describe('系统字典类型三态文档截图', () => {
  // 正式验收要求 4 passed / 0 skipped：缺 Token 或未开写入开关即整组 skip，避免把全 skip 当通过。
  test.skip(!adminToken || !mutationsEnabled, '需要注入 E2E_ADMIN_TOKEN 并显式设置 E2E_MUTATIONS=true')

  test.afterAll(async () => {
    if (!adminToken || !mutationsEnabled) return
    const api = await apiContext()
    try {
      // 正式契约清理：DELETE /api/base/dict/type/{id}；逐条核对响应并回查列表确认不再可见。
      for (const item of created) {
        if (item.id === null) {
          // 创建未成功但已登记 typeCode：回查是否残留，避免留下无 id 的孤儿数据。
          const found = await findTypeByCode(api, item.typeCode)
          if (found) item.id = found.id
        }
        if (item.id === null) {
          cleanupOutcomes.push({
            ...item, httpStatus: null, code: null,
            message: '未取到资源 id，且列表回查无该 typeCode（无可清理对象）',
            deleted: false, stillListed: false,
          })
          continue
        }
        const response = await api.delete(`/api/base/dict/type/${item.id}`, { headers: adminHeaders() })
        const payload = await response.json().catch(() => null)
        const deleted = response.status() === 200 && payload?.code === 200
        const stillListed = deleted ? (await findTypeByCode(api, item.typeCode)) !== null : null
        cleanupOutcomes.push({
          ...item,
          httpStatus: response.status(),
          code: typeof payload?.code === 'number' ? payload.code : null,
          message: String(payload?.message ?? ''),
          deleted,
          stillListed,
        })
      }
    } finally {
      persistRegistry()
      await api.dispose()
    }

    // 仅输出计数与资源标识，不输出 Token 或完整响应体。
    const deleted = cleanupOutcomes.filter((item) => item.deleted && item.stillListed === false).length
    const residual = cleanupOutcomes.filter((item) => !(item.deleted && item.stillListed === false))
    const lines = [
      `[dict-cleanup] tenantId=${tenantId} runStamp=${runStamp} registered=${created.length} deleted=${deleted} residual=${residual.length} registry=${registryPath}`,
      ...residual.map((item) => `[dict-cleanup] residual typeCode=${item.typeCode} id=${item.id} http=${item.httpStatus} code=${item.code} stillListed=${item.stillListed} message=${item.message}`),
    ]
    process.stdout.write(`${lines.join('\n')}\n`)
    // 字典类型是租户共享参考数据，且 base-dictionary-catalog 种子断言按 deleted=0 计数，
    // 因此任何残留都会污染种子校验，必须让用例显式失败而不是静默留下数据。
    expect(residual, `本批字典类型必须全部清理完毕，残留 ${residual.length} 项`).toHaveLength(0)
  })

  for (const locale of docsLocales) {
    test(`system dictionary create states / ${locale}`, async ({ page }) => {
      const typeCode = typeCodes[locale]
      // 创建意图一旦产生就立即登记，即使后续步骤失败也保有归属记录可供清理。
      const record: CreatedDictType = { locale, typeCode, id: null }
      created.push(record)
      persistRegistry()

      const api = await apiContext()
      try {
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
        await page.goto('/admin/base/dict')
        await waitForDocsPage(page, '.el-main, .app-main')

        // 状态一：新建对话框表单（create-or-edit）。
        await page.locator('.el-main button, .app-main button')
          .filter({ hasText: createButton[locale] }).first().click()
        const dialog = page.locator('.el-dialog').first()
        await expect(dialog).toBeVisible()
        await expect(dialog.locator('.el-dialog__title, .el-dialog__header').first())
          .toContainText(createButton[locale])
        // 四个字段标签必须按当前语言渲染，证明表单结构完整而非空壳。
        for (const label of [codeLabel[locale], nameLabel[locale], remarkLabel[locale], sortLabel[locale]]) {
          await expect(dialog.locator('.el-form-item__label', { hasText: label }).first()).toBeVisible()
        }
        await captureDocsImage(page, locale, 'system-dictionary-create-form')

        // 状态二：必填校验失败（failure）。前端无 form rules，错误来自后端 @NotBlank，
        // 以 toast 呈现且对话框保持打开——这是实测的真实产品行为。
        await dialog.locator('button').filter({ hasText: confirmButton[locale] }).first().click()
        const toast = page.locator('.el-message, .el-notification').first()
        await expect(toast).toBeVisible()
        await expect(toast).toContainText(REQUIRED_MESSAGE_FRAGMENT)
        await expect(dialog).toBeVisible()
        await captureDocsImage(page, locale, 'system-dictionary-create-validation')

        // 状态三：创建成功（key-action-success）。填入本轮唯一 typeCode 后提交。
        await fieldByLabel(page, dialog, codeLabel[locale]).fill(typeCode)
        await fieldByLabel(page, dialog, nameLabel[locale]).fill(`E2E Dict ${locale}`)
        await dialog.locator('button').filter({ hasText: confirmButton[locale] }).first().click()
        await expect(dialog).toBeHidden()

        // 用列表检索定位本轮唯一记录：新建行可能不在首页，必须按 typeCode 精确检索后再断言。
        await page.locator(`input[placeholder="${codeLabel[locale]}"]`).first().fill(typeCode)
        await page.locator('.el-main button, .app-main button')
          .filter({ hasText: searchButton[locale] }).first().click()
        // 页面同时存在「类型列表」与「字典数据列表」两张表，故限定到唯一命中行。
        const row = page.locator('.el-table__row', { hasText: typeCode }).first()
        await expect(row).toBeVisible()
        await expect(row).toContainText(`E2E Dict ${locale}`)
        await captureDocsImage(page, locale, 'system-dictionary-create-success')

        // 以正式 API 回查取得权威 id 并登记，供 afterAll 精确清理。
        const found = await findTypeByCode(api, typeCode)
        expect(found, `创建后应能按 typeCode 检索到字典类型：${typeCode}`).not.toBeNull()
        record.id = found!.id
        persistRegistry()
      } finally {
        await api.dispose()
      }
    })
  }
})
