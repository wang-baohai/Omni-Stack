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
 * 2. 后端返回字段校验失败，前端统一错误层显示本地化字段名提示和排障码，不直接展示服务端消息。
 * 3. 按当前日/韩语言包验证类型名称和编码；备注仍继承英文基础词条，保留真实表现。
 *
 * 所有 selector 文案取自对运行中应用的实测探测，不从语言包推测。
 */
import { expect, test, request as pwRequest, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN
/** 写入开关：非 'true' 时整组 skip，且 expectOk 会对任何写入调用直接抛错。 */
const mutationsEnabled = process.env.E2E_MUTATIONS === 'true'
const tenantId = Number(process.env.E2E_TENANT_ID ?? '1')
const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000'

// —— 以下为实测探测得到的真实渲染文案（顺序与 docsLocales 一致）——
const createButton: Record<DocsLocale, string> = {
  'zh-CN': '新建类型', 'en-US': 'Create Type', 'ja-JP': 'タイプ新規作成', 'ko-KR': '타입 추가',
}
const codeLabel: Record<DocsLocale, string> = {
  'zh-CN': '类型编码', 'en-US': 'Type Code', 'ja-JP': 'タイプコード', 'ko-KR': '타입 코드',
}
const nameLabel: Record<DocsLocale, string> = {
  'zh-CN': '类型名称', 'en-US': 'Type Name', 'ja-JP': 'タイプ名', 'ko-KR': '타입 이름',
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
/** 当前统一错误层的四语言字段校验提示；字段标识由真实响应提供。 */
const requiredMessage: Record<DocsLocale, string> = {
  'zh-CN': '以下字段填写有误：', 'en-US': 'Please correct the following fields:',
  'ja-JP': '次の入力内容をご確認ください：', 'ko-KR': '다음 입력 항목을 확인해 주세요:',
}

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
const captureRecords: Array<Record<string, unknown>> = []
let runtime: Record<string, unknown>
let sourceSha256: string
const testSha256 = createHash('sha256').update(readFileSync(resolve('e2e-docs/flows/system-dictionary.flows.spec.ts'))).digest('hex')
// 登记文件位于仓库外系统 TEMP，不会被 stage 或提交。
const registryPath = process.env.E2E_REGISTRY_PATH
  ? `${process.env.E2E_REGISTRY_PATH.replace(/\.json$/, '')}-${runStamp}.json`
  : join(tmpdir(), 'omni-e2edict-registry', `run-${runStamp}.json`)

/** 落盘本批资源登记与清理结果，供链路中断后的定向清理复用。 */
function persistRegistry() {
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath, JSON.stringify({
    tenantId,
    runStamp,
    baseUrl,
    resources: created,
    cleanup: cleanupOutcomes,
    runtime,
    sourceScope: 'omni-frontend/src (sorted paths and file SHA-256)',
    sourceSha256,
    testSha256,
    captures: captureRecords,
  }, null, 2), 'utf8')
}

/** 正式截图保留运行来源、原图摘要和采集时间，不以测试通过代替视觉复核。 */
async function captureDictionaryState(page: Page, locale: DocsLocale, imageName: string) {
  await captureDocsImage(page, locale, imageName)
  const image = join(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${imageName}.png`)
  const imageSha256 = createHash('sha256').update(readFileSync(image)).digest('hex')
  expect(captureRecords.some((item) => item.locale === locale && item.imageSha256 === imageSha256),
    '表单、校验失败和创建结果不能使用相同图像').toBe(false)
  captureRecords.push({ locale, imageName, imageSha256, capturedAt: new Date().toISOString(),
    route: '/admin/base/dict', viewport: page.viewportSize(), visualReview: 'pending', mocks: false })
  persistRegistry()
}

/** 规范化源码清单只包含生产前端源码，避免缓存、私有配置和测试输出混入摘要。 */
function frontendSourceDigest() {
  const files: string[] = []
  function visit(directory: string) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const file = join(directory, entry.name)
      if (entry.isDirectory()) visit(file)
      else if (entry.isFile()) files.push(file)
      else throw new Error('源码目录不接受符号链接或特殊文件')
    }
  }
  visit(resolve('src'))
  const content = files.sort().map((file) => `${relative(process.cwd(), file)} ${createHash('sha256').update(readFileSync(file)).digest('hex')}\n`).join('')
  return createHash('sha256').update(content).digest('hex')
}

async function apiContext(): Promise<APIRequestContext> {
  return pwRequest.newContext({ baseURL: baseUrl })
}

function adminHeaders() {
  return { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' }
}

/** 按 typeCode 查询当前可见字典类型；用于取得 id 与验证清理结果。 */
async function findTypeByCode(api: APIRequestContext, typeCode: string): Promise<{ id: number; total: number } | null> {
  const response = await api.get(`/api/base/dict/type/list?page=1&size=20&typeCode=${encodeURIComponent(typeCode)}`, {
    headers: adminHeaders(),
  })
  expect(response.status(), '清理回查必须成功，不能把接口失败当作记录不存在').toBe(200)
  const payload = await response.json()
  expect(payload.code, '清理回查业务响应必须成功').toBe(200)
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

  test.beforeAll(() => {
    expect(existsSync('/.dockerenv'), '写入和截图必须在本机 Docker 内').toBe(true)
    expect(process.env.E2E_RUNTIME_MODE).toBe('external')
    const project = process.env.E2E_DICTIONARY_PROJECT
    expect(project).toMatch(/^omni-(?:e2e|wiki-acceptance)-[a-z0-9][a-z0-9-]+$/)
    for (const output of [process.env.E2E_DOCS_IMAGE_DIR, process.env.E2E_REGISTRY_PATH, process.env.E2E_DICTIONARY_RUNTIME_FILE]) {
      expect(output, '正式产物路径必须显式提供').toBeTruthy()
      expect(isAbsolute(output!)).toBe(true)
      expect(relative(resolve('..'), output!).startsWith('..'), '禁止把私有证据写入仓库').toBe(true)
    }
    runtime = JSON.parse(readFileSync(process.env.E2E_DICTIONARY_RUNTIME_FILE!, 'utf8').replace(/^\uFEFF/, ''))
    expect(runtime.project).toBe(project)
    const containers = runtime.runtime as Array<{ service: string; image_id: string; project: string; host_ports: unknown[] }>
    for (const service of ['omni-frontend', 'omni-base', 'omni-auth', 'omni-gateway']) {
      const container = containers.find((item) => item.service === service)
      expect(container?.project).toBe(project)
      expect(container?.image_id).toMatch(/^sha256:[a-f0-9]{64}$/)
      expect(container?.host_ports).toEqual([])
    }
    sourceSha256 = frontendSourceDigest()
  })

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
    const neverCreated = cleanupOutcomes.filter((item) => item.id === null && item.stillListed === false).length
    const residual = cleanupOutcomes.filter((item) =>
      !((item.deleted || item.id === null) && item.stillListed === false))
    const lines = [
      `[dict-cleanup] tenantId=${tenantId} runStamp=${runStamp} registered=${created.length} deleted=${deleted} neverCreated=${neverCreated} residual=${residual.length} registry=${registryPath}`,
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
        await prepareDocsPage(page, { locale, token: adminToken, username: 'admin', viewport: { width: 1920, height: 1080 } })
        await page.goto('/admin/base/dict')
        await waitForDocsPage(page, '.el-main, .app-main')
        await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)

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
        await captureDictionaryState(page, locale, 'system-dictionary-create-form')

        // 状态二：必填校验失败（failure）。后端字段错误经统一错误层本地化，
        // 以 toast 呈现且对话框保持打开。
        const validationResponse = page.waitForResponse((response) => response.request().method() === 'POST'
          && new URL(response.url()).pathname === '/api/base/dict/type')
        await dialog.locator('button').filter({ hasText: confirmButton[locale] }).first().click()
        const validation = await validationResponse
        expect(validation.status()).toBe(400)
        expect((await validation.json()).code).toBe(400)
        const toast = page.locator('.el-message--error').first()
        await expect(toast).toBeVisible()
        await expect(toast).toContainText(requiredMessage[locale])
        await expect(toast).toContainText('typeCode')
        await expect(toast).toContainText('typeName')
        await expect(dialog).toBeVisible()
        // visible 仍可能处于进入动画；确认完全显示且未被模态层遮挡再采集。
        await expect(toast).toHaveCSS('opacity', '1')
        await expect.poll(() => toast.evaluate((element) => {
          const box = element.getBoundingClientRect()
          const top = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
          return box.y >= 0 && box.bottom <= window.innerHeight && (element === top || element.contains(top))
        })).toBe(true)
        await captureDictionaryState(page, locale, 'system-dictionary-create-validation')
        // 等待真实提示自然退出，避免将上一步错误留在成功截图中；不删除或改写 DOM。
        await expect(toast).toBeHidden({ timeout: 10_000 })

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
        await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
        await expect(page.locator('.el-message--error:visible')).toHaveCount(0)
        await captureDictionaryState(page, locale, 'system-dictionary-create-success')

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
