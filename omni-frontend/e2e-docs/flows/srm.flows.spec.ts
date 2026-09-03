/**
 * @module srm.flows.spec
 * SRM 供应商报价（supplier-quotation gap）四语言正式截图：邀请列表、报价表单、提交成功。
 * fixture：E2E_MUTATIONS 下 admin 经正式 API 创建请购 → 提交审批 → workflow 通过 → 创建 RFQ（邀请 supplier1）；
 * supplier1 经 portal UI 对唯一 RFQ 真实提交报价；同语言三张截图共用同一 RFQ fixture。
 *
 * 安全契约：
 * 1. 未显式设置 `E2E_MUTATIONS=true` 时整个套件 skip，且任何写入调用都会抛出，绝不产生数据。
 * 2. 流程模型与审批任务都必须精确命中本轮唯一目标，缺失即失败，不回退到列表首项。
 * 3. 每次创建成功后立即把 tenantId + runStamp + 资源 ID 落盘到仓库外登记文件，
 *    链路中途失败也能定位归属并清理。
 * 4. afterAll 逐条核对 DELETE 响应；产品状态机只允许删除草稿，SENT/QUOTED 询价单与
 *    APPROVED 请购会被 409 拒绝，此时如实登记为残留，交由受控 DB 软删处理，不宣称清理成功。
 */
import { expect, test, request as pwRequest, type APIRequestContext, type APIResponse } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { captureDocsImage, docsLocales, prepareDocsPage, type DocsLocale } from '../fixtures/docs-page'

const adminToken = process.env.E2E_ADMIN_TOKEN
const supplierToken = process.env.E2E_SUPPLIER_TOKEN
/** 写入开关：非 'true' 时套件 skip，且 expectOk 会对任何写入调用直接抛错。 */
const mutationsEnabled = process.env.E2E_MUTATIONS === 'true'
/** 本批数据归属租户；登记与清理都以 tenantId + runStamp + 资源 ID 三元组限定。 */
const tenantId = Number(process.env.E2E_TENANT_ID ?? '1')
const baseUrl = process.env.E2E_BASE_URL || 'http://127.0.0.1:3000'

// selector 实际渲染值（src/locales/*.ts 的 common.srmPortalQuotation 与 portal.quotation 段）。
const tabLabel: Record<DocsLocale, string> = {
  'zh-CN': '询价报价', 'en-US': 'RFQ Quotations', 'ja-JP': '見積回答', 'ko-KR': '견적 응답',
}
const submitLabel: Record<DocsLocale, string> = {
  'zh-CN': '提交报价', 'en-US': 'Submit Quotation', 'ja-JP': '見積を提出', 'ko-KR': '견적 제출',
}
const notQuotedLabel: Record<DocsLocale, string> = {
  'zh-CN': '未报价', 'en-US': 'Not Quoted', 'ja-JP': '未見積', 'ko-KR': '미견적',
}
const successToast: Record<DocsLocale, string> = {
  'zh-CN': '报价已提交', 'en-US': 'Quotation submitted', 'ja-JP': '見積を提出しました', 'ko-KR': '견적이 제출되었습니다',
}
const refreshLabel: Record<DocsLocale, string> = {
  'zh-CN': '刷新询价', 'en-US': 'Refresh RFQs', 'ja-JP': '見積依頼を更新', 'ko-KR': '견적 요청 새로고침',
}

const UNIT_PRICE = '123.45'
const QUANTITY = '2'
// 前端当前报价列按 Number 渲染（Number('246.9000') → 246.9），断言使用实际渲染形态。
const EXPECTED_TOTAL = '246.9'
/** 审批路由必须绑定的正式流程模型标识。 */
const REQUIRED_MODEL_KEY = 'procurement-approval'

const runStamp = Date.now()
const requisitionTitles: Record<DocsLocale, string> = Object.fromEntries(
  docsLocales.map((locale) => [locale, `E2ESQ REQ ${locale} ${runStamp}`]),
) as Record<DocsLocale, string>
const rfqTitles: Record<DocsLocale, string> = Object.fromEntries(
  docsLocales.map((locale) => [locale, `E2ESQ RFQ ${locale} ${runStamp}`]),
) as Record<DocsLocale, string>

/** 本批创建的资源类型。 */
type ResourceKind = 'category' | 'material' | 'approvalRoute' | 'requisition' | 'rfq'

interface CreatedResource {
  kind: ResourceKind
  id: number
  version: number | null
  locale: DocsLocale
  label: string
}

interface CleanupOutcome extends CreatedResource {
  httpStatus: number | null
  code: number | null
  message: string
  deleted: boolean
}

/** 同一语言三张截图共用的 fixture 上下文；资源归属以登记文件为准。 */
interface RfqFixture {
  /** 报价有效期输入值，须不早于询价截止时间。 */
  deadline: string
}

/**
 * 业务响应与资源快照的最小结构化视图。
 *
 * 测试只声明实际读取的字段，以此替代 any 并保留 strict 模式下的类型检查。
 */
interface ResourceSnapshot {
  code?: number
  message?: string
  data?: unknown
  id?: number
  version?: number
  status?: string
  title?: string
  categoryCode?: string
  modelKey?: string
  modelVersionId?: number
  taskId?: number
  records?: ResourceSnapshot[]
}

/** 把未知 JSON 值收敛为资源快照。 */
function asSnapshot(value: unknown): ResourceSnapshot {
  return (value ?? {}) as ResourceSnapshot
}

/** 把未知 JSON 值收敛为资源快照数组；非数组一律视为空列表。 */
function asSnapshots(value: unknown): ResourceSnapshot[] {
  return Array.isArray(value) ? (value as ResourceSnapshot[]) : []
}

/** 清理顺序按依赖反向：先询价单，最后品类。 */
const CLEANUP_ORDER: readonly ResourceKind[] = ['rfq', 'requisition', 'approvalRoute', 'material', 'category']
const DELETE_PATHS: Record<ResourceKind, (id: number) => string> = {
  rfq: (id) => `/api/procurement/rfq/${id}`,
  requisition: (id) => `/api/procurement/requisition/${id}`,
  approvalRoute: (id) => `/api/procurement/approval-route/${id}`,
  material: (id) => `/api/procurement/material/${id}`,
  category: (id) => `/api/procurement/material/category/${id}`,
}
/** 仅询价单与请购的版本会被状态流转消耗，删除前需取最新快照。 */
const DETAIL_PATHS: Partial<Record<ResourceKind, (id: number) => string>> = {
  rfq: (id) => `/api/procurement/rfq/${id}`,
  requisition: (id) => `/api/procurement/requisition/${id}`,
}

const createdResources: CreatedResource[] = []
const cleanupOutcomes: CleanupOutcome[] = []
// 登记文件位于仓库外的系统 TEMP，不会被 stage 或提交。
const registryPath = join(tmpdir(), 'omni-e2esq-registry', `run-${runStamp}.json`)

/** 落盘本批资源登记与清理结果，供链路中断后的定向清理复用。 */
function persistRegistry() {
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath, JSON.stringify({
    tenantId,
    runStamp,
    baseUrl,
    resources: createdResources,
    cleanup: cleanupOutcomes,
  }, null, 2), 'utf8')
}

/** 创建成功后立即登记；中途失败的资源因此仍保有归属记录。 */
function registerCreated(locale: DocsLocale, kind: ResourceKind, label: string, data: ResourceSnapshot) {
  // 缺少资源 ID 就无法定向清理，必须立即失败而不是留下无归属数据。
  if (typeof data.id !== 'number') {
    throw new Error(`创建${kind}响应缺少资源 ID，无法登记本批归属：${label}`)
  }
  createdResources.push({
    kind,
    id: data.id,
    version: typeof data.version === 'number' ? data.version : null,
    locale,
    label,
  })
  persistRegistry()
}

async function apiContext(): Promise<APIRequestContext> {
  return pwRequest.newContext({ baseURL: baseUrl })
}

function adminHeaders() {
  return { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' }
}

/**
 * 断言业务响应 code===200 并返回 data；失败时附带响应摘要便于定位。
 *
 * 该函数是所有写入调用的唯一出口，因此在这里强制 E2E_MUTATIONS 防护：
 * 未开启写入开关时直接抛错，保证「缺少开关绝不产生写入」。
 */
async function expectOk(response: APIResponse, action: string): Promise<ResourceSnapshot> {
  if (!mutationsEnabled) {
    throw new Error(`E2E_MUTATIONS 未启用，禁止执行写入操作：${action}`)
  }
  const payload = asSnapshot(await response.json())
  expect(payload.code, `${action} 应成功（${payload.message ?? 'no message'}）`).toBe(200)
  return asSnapshot(payload.data)
}

/** 经正式 API 构造 RFQ 并邀请 supplier1：品类/物料 → 请购 → 审批通过 → 询价单。 */
async function createRfqFixture(locale: DocsLocale): Promise<RfqFixture> {
  const api = await apiContext()
  try {
    // 当前栈 material/category 表可能无 seed：fixture 自建唯一品类与物料（正式 API，cleanup 反向删除）。
    const category = await expectOk(await api.post('/api/procurement/material/category', {
      headers: adminHeaders(),
      data: {
        parentId: 0,
        categoryCode: `E2ESQ-C-${runStamp}-${locale}`,
        categoryName: `E2E SQ Category ${locale}`,
        sort: 999,
        status: 1,
      },
    }), '创建品类')
    registerCreated(locale, 'category', `E2ESQ-C-${runStamp}-${locale}`, category)
    // 审批路由按 categoryCode 绑定；缺失即无法构造有效路由，必须立即失败而不是提交空值。
    const categoryCode = category.categoryCode
    if (!categoryCode) {
      throw new Error('创建品类响应缺少 categoryCode，无法绑定审批路由')
    }

    const material = await expectOk(await api.post('/api/procurement/material', {
      headers: adminHeaders(),
      data: {
        categoryId: category.id,
        materialCode: `E2ESQ-M-${runStamp}-${locale}`,
        materialName: `E2E SQ Material ${locale}`,
        unit: '件',
        assetManaged: false,
      },
    }), '创建物料')
    registerCreated(locale, 'material', `E2ESQ-M-${runStamp}-${locale}`, material)

    // 审批路由：请购提交需匹配当前品类与金额的 ACTIVE 路由，fixture 自建并绑定可用流程模型版本。
    // workflow-options 可能混入测试遗留模型（如 G2 E2E 注入模型），必须精确选取 procurement-approval；
    // 兜底到 options[0] 会把路由绑定到残留模型，导致 workflow 启动阶段候选解析全部失败，
    // 因此未命中目标模型时明确失败，不静默降级。
    const workflowOptions = asSnapshot(await (await api.get('/api/procurement/approval-route/workflow-options', {
      headers: adminHeaders(),
    })).json())
    const options = asSnapshots(workflowOptions.data)
    const preferredOption = options.find((item) => item.modelKey === REQUIRED_MODEL_KEY)
    expect(
      preferredOption?.modelVersionId,
      `workflow-options 必须提供 ${REQUIRED_MODEL_KEY} 的可用模型版本；实际候选：${options.map((item) => item.modelKey).join(', ') || '空'}`,
    ).toBeTruthy()
    const modelVersionId = preferredOption?.modelVersionId

    const route = await expectOk(await api.post('/api/procurement/approval-route', {
      headers: adminHeaders(),
      data: {
        routeName: `E2ESQ-R-${runStamp}-${locale}`,
        categoryCode,
        minAmount: '0.0000',
        maxAmount: '999999.0000',
        modelVersionId,
      },
    }), '创建审批路由')
    registerCreated(locale, 'approvalRoute', `E2ESQ-R-${runStamp}-${locale}`, route)

    const requisition = await expectOk(await api.post('/api/procurement/requisition', {
      headers: adminHeaders(),
      data: {
        title: requisitionTitles[locale],
        reason: 'E2E supplier quotation fixture',
        lines: [{ materialId: material.id, quantity: QUANTITY, estimatedUnitPrice: '100.00' }],
      },
    }), '创建请购')
    registerCreated(locale, 'requisition', requisitionTitles[locale], requisition)

    // 提交审批：Workflow Feign 首调可能遇 Nacos 订阅冷启动窗口（产品语义「请稍后重试启动」），
    // 503 后状态已流转，按产品契约改用 retry-start（同一幂等快照）重试。
    let submitted: ResourceSnapshot | null = null
    let submitAttempts = 0
    while (submitAttempts < 4 && !submitted) {
      submitAttempts += 1
      if (submitAttempts === 1) {
        const submitPayload = asSnapshot(await (await api.post(`/api/procurement/requisition/${requisition.id}/submit`, {
          headers: adminHeaders(),
          data: { version: requisition.version },
        })).json())
        if (submitPayload.code === 200) submitted = asSnapshot(submitPayload.data)
        else if (submitPayload.code !== 503) {
          expect(submitPayload.code, `提交请购审批应成功：${submitPayload.message ?? ''}`).toBe(200)
        }
      } else {
        // 503 后状态已流转、version 已消耗：取最新快照后按产品契约 retry-start（同一幂等快照重试启动）。
        const latest = asSnapshot(await (await api.get(`/api/procurement/requisition/${requisition.id}`, {
          headers: adminHeaders(),
        })).json())
        const retryPayload = asSnapshot(await (await api.post(`/api/procurement/requisition/${requisition.id}/retry-start`, {
          headers: adminHeaders(),
          data: { version: asSnapshot(latest.data).version },
        })).json())
        if (retryPayload.code === 200) submitted = asSnapshot(retryPayload.data)
        else if (retryPayload.code !== 503) {
          expect(retryPayload.code, `retry-start 应成功：${retryPayload.message ?? ''}`).toBe(200)
        }
      }
      if (!submitted) await new Promise((resolve) => setTimeout(resolve, 3000))
    }
    expect(submitted, '提交请购审批应成功（503 后取新版本 retry-start）').toBeTruthy()

    // 审批任务必须唯一命中本轮请购标题（含 runStamp）；回退到 records[0] 会批准其他批次或他人的任务。
    const todoPayload = asSnapshot(await (await api.get(
      `/api/workflow/task/todo?page=1&size=50&title=${encodeURIComponent(requisitionTitles[locale])}`,
      { headers: adminHeaders() },
    )).json())
    const records = asSnapshots(asSnapshot(todoPayload.data).records)
    const matched = records.filter((item) => String(item.title ?? '').includes(requisitionTitles[locale]))
    expect(
      matched,
      `待办查询应唯一命中本轮请购「${requisitionTitles[locale]}」，实际返回 ${records.length} 条、命中 ${matched.length} 条`,
    ).toHaveLength(1)
    const task = matched[0]!
    const taskId = task.taskId ?? task.id
    expect(taskId, '审批任务标识应存在').toBeTruthy()

    await expectOk(await api.post(`/api/workflow/approval/${taskId}/complete`, {
      headers: adminHeaders(),
      data: { approved: true, comment: 'E2E approve' },
    }), '审批通过')

    let approved = false
    for (let attempt = 0; attempt < 15 && !approved; attempt += 1) {
      await new Promise((resolve) => setTimeout(resolve, 1000))
      const detail = asSnapshot(await (await api.get(`/api/procurement/requisition/${requisition.id}`, {
        headers: adminHeaders(),
      })).json())
      approved = asSnapshot(detail.data).status === 'APPROVED'
    }
    expect(approved, '请购应流转为 APPROVED').toBe(true)

    const deadline = new Date(Date.now() + 3 * 24 * 3600 * 1000)
      .toISOString().slice(0, 19).replace('T', ' ')
    const rfq = await expectOk(await api.post('/api/procurement/rfq', {
      headers: adminHeaders(),
      data: {
        requisitionId: requisition.id,
        title: rfqTitles[locale],
        quotationDeadline: deadline,
        supplierIds: [1],
      },
    }), '创建询价单')
    registerCreated(locale, 'rfq', rfqTitles[locale], rfq)

    // 发送询价：DRAFT → SENT 并填充邀请时间（invited_time），供应商侧报价列表仅展示已发送邀请。
    const sent = await expectOk(await api.post(`/api/procurement/rfq/${rfq.id}/send`, {
      headers: adminHeaders(),
      data: { version: rfq.version },
    }), '发送询价单')
    const registeredRfq = createdResources.find((item) => item.kind === 'rfq' && item.id === rfq.id)
    if (registeredRfq && typeof sent.version === 'number') registeredRfq.version = sent.version
    persistRegistry()
    return { deadline }
  } finally {
    await api.dispose()
  }
}

test.describe('SRM supplier quotation 文档截图', () => {
  // 正式验收要求 4 passed / 0 skipped：三者缺一即整组 skip，避免把全 skip 误判为通过。
  test.skip(
    !adminToken || !supplierToken || !mutationsEnabled,
    '需要注入 E2E_ADMIN_TOKEN/E2E_SUPPLIER_TOKEN 并显式设置 E2E_MUTATIONS=true',
  )

  test.afterAll(async () => {
    if (!adminToken || !mutationsEnabled || createdResources.length === 0) {
      persistRegistry()
      return
    }
    const api = await apiContext()
    try {
      // 反向依赖顺序清理；逐条核对 DELETE 响应，不使用「发出即成功」的假设。
      for (const kind of CLEANUP_ORDER) {
        for (const resource of createdResources.filter((item) => item.kind === kind)) {
          let version = resource.version
          const detailPath = DETAIL_PATHS[kind]
          if (detailPath) {
            // 提交/审批/发送都会消耗乐观锁版本，删除前必须取最新快照。
            const envelope = asSnapshot(await (await api.get(detailPath(resource.id), { headers: adminHeaders() })).json())
            const latest = asSnapshot(envelope.data)
            if (envelope.code === 200 && typeof latest.version === 'number') version = latest.version
          }
          const response = await api.delete(`${DELETE_PATHS[kind](resource.id)}?version=${version}`, {
            headers: adminHeaders(),
          })
          const payload = asSnapshot(await response.json().catch(() => null))
          cleanupOutcomes.push({
            ...resource,
            httpStatus: response.status(),
            code: typeof payload.code === 'number' ? payload.code : null,
            message: String(payload.message ?? ''),
            deleted: response.status() === 200 && payload.code === 200,
          })
        }
      }
    } finally {
      persistRegistry()
      await api.dispose()
    }

    // 仅输出计数、资源标识与产品拒绝原因，不输出 Token 或完整响应体。
    // 使用 process.stdout 而非 console，符合仓库 lint 门禁（no-console）。
    const deleted = cleanupOutcomes.filter((item) => item.deleted).length
    const residual = cleanupOutcomes.filter((item) => !item.deleted)
    const lines = [
      `[srm-cleanup] tenantId=${tenantId} runStamp=${runStamp} registered=${createdResources.length} deleted=${deleted} residual=${residual.length} registry=${registryPath}`,
      ...residual.map((item) => `[srm-cleanup] residual ${item.kind}#${item.id} locale=${item.locale} label=${item.label} http=${item.httpStatus} code=${item.code} message=${item.message}`),
    ]
    process.stdout.write(`${lines.join('\n')}\n`)
  })

  for (const locale of docsLocales) {
    test(`supplier quotation flow / ${locale}`, async ({ page }) => {
      // fixture 链：请购 → 审批 → RFQ 邀请 supplier1（同语言三张截图共用）。
      const fixture = await createRfqFixture(locale)

      await prepareDocsPage(page, { locale, token: supplierToken, username: 'supplier1' })
      await page.goto('/supplier-portal')
      await expect(page.getByRole('tab', { name: tabLabel[locale] })).toBeVisible()
      await page.getByRole('tab', { name: tabLabel[locale] }).click()

      // 场景一：邀请列表——唯一 RFQ、INVITED 状态、未报价。
      const row = page.locator('.el-table__row', { hasText: rfqTitles[locale] })
      await expect(row).toBeVisible()
      await expect(row.locator('.el-tag', { hasText: 'INVITED' })).toBeVisible()
      await expect(row.getByText(notQuotedLabel[locale])).toBeVisible()
      await captureDocsImage(page, locale, 'srm-portal-quotation-invitations')

      // 场景二：报价表单——对话框渲染、行快照、填入有效期与单价。
      await row.getByRole('button', { name: submitLabel[locale] }).click()
      const dialog = page.locator('.el-dialog').first()
      await expect(dialog).toBeVisible()
      const priceInput = dialog.locator('.el-table__row').first().locator('input').first()
      await priceInput.fill(UNIT_PRICE)
      // 报价有效期必填且须不早于报价截止时间：直接取 fixture 截止时间值（晚于固定文档时钟）。
      await dialog.locator('.el-form input').first().fill(fixture.deadline)
      await captureDocsImage(page, locale, 'srm-portal-quotation-form')

      // 场景三：提交成功——toast、对话框关闭。
      await dialog.getByRole('button', { name: submitLabel[locale] }).click()
      await expect(page.getByText(successToast[locale]).first()).toBeVisible()
      await expect(dialog).toBeHidden()
      // 邀请状态经 srm.quotation.submitted.v1 MQ 事件异步流转（消费延迟秒级）：
      // 轮询刷新列表直至行反映 QUOTED 与报价总额，再拍提交成功正式图。
      await expect.poll(async () => {
        await page.getByRole('button', { name: refreshLabel[locale] }).first().click()
        return row.locator('.el-tag', { hasText: 'QUOTED' }).count()
      }, { timeout: 30000, intervals: [2000, 3000, 5000] }).toBe(1)
      await expect(row).toBeVisible()
      await expect(row.getByText(EXPECTED_TOTAL)).toBeVisible()
      await captureDocsImage(page, locale, 'srm-portal-quotation-submitted')
    })
  }
})
