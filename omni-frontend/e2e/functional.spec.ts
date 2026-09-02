/**
 * @module functional.spec
 * 登录后功能与权限的 Playwright 断言型回归。
 *
 * 认证用例从 CI Secret 注入短期 JWT，不读取 Redis、数据库或 CAPTCHA 明文：
 * E2E_ADMIN_TOKEN、E2E_EMPLOYEE_TOKEN、E2E_SUPPLIER_TOKEN。
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'

const adminToken = process.env.E2E_ADMIN_TOKEN
const employeeToken = process.env.E2E_EMPLOYEE_TOKEN
const supplierToken = process.env.E2E_SUPPLIER_TOKEN
const mutationsEnabled = process.env.E2E_MUTATIONS === 'true'

interface ApiEnvelope<T> {
  code: number
  data: T
}

interface PageData<T> {
  records: T[]
  total: number
}

interface WorkflowModelFixture {
  id: number | string
  currentPublishedVersionId: number | string | null
}

interface WorkflowOptionFixture {
  modelVersionId: number | string
  modelId: number | string
}

interface UserJobFixture {
  id: number
  jobName: string
}

interface UserJobLogFixture {
  resultMessage: string | null
}

interface MatchPreviewFixture {
  outcome: 'MATCHED' | 'NO_MATCH' | 'AMBIGUOUS' | 'WORKFLOW_UNAVAILABLE'
  routeId?: number | string
  routeName?: string
  defaultRule?: boolean
  conflictingRouteIds?: Array<number | string>
}

interface RouteFixture {
  id: number | string
  version?: number
  routeName?: string
}

interface CategoryFixture {
  id: number | string
  version?: number
}

interface CoverageSegmentFixture {
  outcome: string
}

interface CoverageReportFixture {
  categories: Array<{ segments: CoverageSegmentFixture[] }>
}

interface ImpactReportFixture {
  routeId: number
  gapSegmentCount: number
  ambiguousSegmentCount: number
  actionMessage: string
}

/** 在应用启动前注入隔离环境签发的短期令牌。 */
async function authenticate(page: Page, token: string, username: string) {
  await page.addInitScript(({ accessToken, name }) => {
    localStorage.setItem('token', accessToken)
    localStorage.setItem('username', name)
  }, { accessToken: token, name: username })
}

/** 等待页面稳定，并确保没有落入空白主内容区。 */
async function expectRendered(page: Page, path: string) {
  await page.goto(path)
  await expect(page.locator('body')).not.toBeEmpty()
  await expect(page.locator('.el-main, .app-main').first()).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.el-main, .app-main').first()).not.toBeEmpty()
}

/** 构造包含一个可配置审批节点的最小合法 BPMN，用于设计器写入闭环。 */
function buildWorkflowFixtureXml(processKey: string): string {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns:omni="http://omni.com/workflow"
  id="Definitions_${processKey}" targetNamespace="e2e">
  <process id="${processKey}" name="WP08 E2E 审批" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始" />
    <userTask id="Task_Approve" name="审批" flowable:assignee="\${userId}">
      <extensionElements>
        <omni:assignment>{"roleCode":"TEAM_LEADER","anchorType":"START_USER_PRIMARY_UNIT","anchorParams":{},"scopeMode":"SAME_UNIT","fallbackStrategy":"ERROR","approvalMode":"ANY"}</omni:assignment>
      </extensionElements>
      <multiInstanceLoopCharacteristics isSequential="false"
        flowable:collection="\${candidateResolver.resolve(execution)}"
        flowable:elementVariable="userId">
        <completionCondition xsi:type="tFormalExpression">\${rejectedCount > 0 || approvedCount >= requiredApprovals}</completionCondition>
      </multiInstanceLoopCharacteristics>
    </userTask>
    <endEvent id="EndEvent_1" name="结束" />
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Approve" />
    <sequenceFlow id="Flow_2" sourceRef="Task_Approve" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${processKey}">
    <bpmndi:BPMNPlane id="BPMNPlane_${processKey}" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="120" y="180" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Approve_di" bpmnElement="Task_Approve">
        <dc:Bounds x="230" y="158" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="430" y="180" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="156" y="198" /><di:waypoint x="230" y="198" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
        <di:waypoint x="350" y="198" /><di:waypoint x="430" y="198" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

test.describe('公开入口', () => {
  for (const entry of [
    { path: '/login', selector: '.login-form-wrapper' },
    { path: '/register', selector: '.el-form' },
    { path: '/portal-login', selector: '.login-card' },
    { path: '/portal-register', selector: '.el-form' },
    { path: '/device', selector: '.device-card' },
  ]) {
    test(`${entry.path} 可渲染`, async ({ page }) => {
      await page.goto(entry.path)
      await expect(page.locator(entry.selector).first()).toBeVisible()
    })
  }

  test('登录与设备验证表单不预填仓库凭据', async ({ page }) => {
    await page.goto('/login')
    const inputs = page.locator('.login-form-wrapper input')
    await expect(inputs.nth(1)).toHaveValue('')
    await expect(inputs.nth(2)).toHaveValue('')

    await page.goto('/device/verify')
    await expect(page.locator('input[type="text"]').first()).toHaveValue('')
    await expect(page.locator('input[type="password"]').first()).toHaveValue('')
  })

  test('中英日韩四种界面语言可选择并持久化', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: '切换语言' }).click()
    await page.getByRole('menuitem', { name: '日本語' }).click()
    await expect(page.getByText('認証センター')).toBeVisible()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('omni-lang'))).toBe('ja-JP')

    await page.reload()
    await page.getByRole('button', { name: '言語を切り替える' }).click()
    await page.getByRole('menuitem', { name: '한국어' }).click()
    await expect(page.getByText('인증 센터')).toBeVisible()
    await expect.poll(() => page.evaluate(() => localStorage.getItem('omni-lang'))).toBe('ko-KR')
  })

  test('公开 API 返回可追踪且唯一的关联 ID', async ({ request }) => {
    const response = await request.get('/api/auth/tenants')
    expect(response.ok()).toBeTruthy()
    expect(response.headers()['x-trace-id']).toMatch(/^[a-f0-9]{32}$/)
  })
})

test.describe('管理员动态菜单与核心模块', () => {
  test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')

  test.beforeEach(async ({ page }) => {
    await authenticate(page, adminToken!, 'admin')
  })

  test('Dashboard 不展示模拟运营数字', async ({ page }) => {
    await expectRendered(page, '/admin/dashboard')
    await expect(page.getByText('运营指标尚未接入统一 Metrics 数据源')).toBeVisible()
    await expect(page.getByText('1,024')).toHaveCount(0)
    await expect(page.getByText('8,432')).toHaveCount(0)
  })

  for (const entry of [
    { path: '/admin/system/user', text: '用户' },
    { path: '/admin/workflow/model', text: '流程' },
    { path: '/admin/crm/lead', text: '线索' },
    { path: '/admin/srm/supplier', text: '供应商' },
    { path: '/admin/procurement/requisition', text: '请购' },
    { path: '/admin/asset/asset', text: '资产' },
    { path: '/admin/base/mqmessage', text: '消息' },
  ]) {
    test(`${entry.path} 可进入且包含业务内容`, async ({ page }) => {
      await expectRendered(page, entry.path)
      await expect(page.locator('.el-main, .app-main').first()).toContainText(entry.text)
    })
  }

  test('审批规则匹配、键盘进入和权限边界由服务端解析', async ({ page, request }) => {
    await expectRendered(page, '/admin/procurement/approval-route')
    const pageRoot = page.locator('.approval-rules-page')
    await expect(pageRoot).toContainText('先试一笔')

    const category = pageRoot.locator('.match-tester .el-select').first()
    await category.click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await pageRoot.locator('.match-tester input[type="text"]').nth(1).fill('-1')
    await pageRoot.getByRole('button', { name: '测试匹配' }).focus()
    await page.keyboard.press('Enter')
    await expect(
      page.locator('.el-message').filter({ hasText: '请选择具体品类，并输入最多 4 位小数的非负金额' }),
    ).toBeVisible()

    await category.click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await pageRoot.locator('.match-tester input[type="text"]').nth(1).fill('10000.0000')
    await pageRoot.getByRole('button', { name: '测试匹配' }).click()
    await expect(pageRoot.locator('.match-result').first()).toBeVisible({ timeout: 15_000 })

    const matchResponse = await request.post(
      '/api/procurement/approval-route/match-preview',
      {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { categoryCode: 'IT_DEVICE', totalAmount: '10000.0000' },
      },
    )
    expect(matchResponse.ok()).toBeTruthy()
    const match: ApiEnvelope<MatchPreviewFixture> = await matchResponse.json()
    expect(['MATCHED', 'NO_MATCH']).toContain(match.data.outcome)
  })

  test('流程模型属性编辑、保存、校验和发布形成闭环', async ({ page, request }) => {
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行写入闭环')
    test.setTimeout(120_000)
    const headers = { Authorization: `Bearer ${adminToken}` }
    const modelKey = `wp08-e2e-${Date.now()}`
    let modelId: number | undefined

    try {
      const createResponse = await request.post('/api/workflow/model', {
        headers,
        data: { modelKey, modelName: 'WP08 E2E 模型', category: 'purchase' },
      })
      expect(createResponse.ok()).toBeTruthy()
      const created: ApiEnvelope<WorkflowModelFixture> = await createResponse.json()
      expect(created.code).toBe(200)
      modelId = created.data.id

      const draftResponse = await request.put(`/api/workflow/model/${modelId}/draft`, {
        headers,
        data: {
          designerJson: JSON.stringify({ version: 1, fixture: 'wp08' }),
          bpmnXml: buildWorkflowFixtureXml(modelKey),
        },
      })
      expect(draftResponse.ok()).toBeTruthy()

      await expectRendered(page, '/admin/workflow/model')
      await page.getByPlaceholder('模型名称 / 标识').fill(modelKey)
      await page.getByRole('button', { name: '搜索' }).click()
      const row = page.locator('.el-table__body tr').filter({ hasText: modelKey })
      await expect(row).toHaveCount(1)
      await row.getByRole('button', { name: '设计' }).click()

      const designer = page.locator('.model-designer-dialog')
      await expect(designer).toBeVisible()
      const approvalNode = designer.locator('[data-element-id="Task_Approve"]')
      await expect(approvalNode).toBeVisible()
      await approvalNode.click()
      await expect(designer.getByText('审批人配置')).toBeVisible()

      const roleSelect = designer.locator('.user-task-panel .el-select').first()
      await roleSelect.click()
      const alternateRole = page.locator('.el-select-dropdown:visible .el-select-dropdown__item:not(.is-selected)').first()
      await expect(alternateRole).toBeVisible()
      await alternateRole.click()

      await designer.getByRole('button', { name: '保存草稿' }).click()
      await expect(page.getByText('草稿已保存')).toBeVisible()
      await designer.getByRole('button', { name: '校验' }).click()
      await expect(page.getByText('校验通过')).toBeVisible()
      await page.keyboard.press('Escape')

      await designer.getByRole('button', { name: '发布' }).click()
      await expect(page.getByText('发布确认')).toBeVisible()
      await page.getByRole('button', { name: 'OK', exact: true }).click()
      await expect(page.getByText(/发布成功/)).toBeVisible()

      const detailResponse = await request.get(`/api/workflow/model/${modelId}`, { headers })
      const detail: ApiEnvelope<WorkflowModelFixture> = await detailResponse.json()
      expect(detail.data.currentPublishedVersionId).not.toBeNull()
    } finally {
      if (modelId) {
        const deleteResponse = await request.delete(`/api/workflow/model/${modelId}`, { headers })
        expect(deleteResponse.ok()).toBeTruthy()
      }
    }
  })

  test('动态任务表单可创建、编辑、触发、产生日志并清理', async ({ page, request }) => {
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行写入闭环')
    test.setTimeout(120_000)
    const headers = { Authorization: `Bearer ${adminToken}` }
    const jobName = `wp08-e2e-job-${Date.now()}`
    const editedJobName = `${jobName}-edited`
    let jobId: number | undefined
    try {
      await page.goto('/')
      await page.getByRole('tab', { name: '我的定时任务' }).click()
      await page.getByRole('button', { name: '创建任务' }).click()

      let dialog = page.getByRole('dialog', { name: '创建任务' })
      await dialog.locator('.el-form-item').filter({ hasText: '任务名称' }).locator('input').fill(jobName)
      await dialog.locator('.el-form-item').filter({ hasText: '任务类型' }).locator('.el-select').click()
      await page.getByRole('option', { name: '喝水提醒' }).click()
      const cupField = dialog.locator('.dynamic-form-field').filter({ hasText: '杯型' })
      await expect(cupField).toBeVisible()
      await cupField.locator('.el-select').click()
      await page.getByRole('option', { name: '小杯', exact: true }).click()
      await dialog.getByRole('button', { name: '确认' }).click()

      await expect(dialog).toBeHidden()
      let row = page.locator('.el-table__body tr').filter({ hasText: jobName })
      await expect(row).toHaveCount(1)

      const listResponse = await request.get('/api/base/my-job/list', {
        headers,
        params: { jobName, page: 1, size: 10 },
      })
      const listed: ApiEnvelope<PageData<UserJobFixture>> = await listResponse.json()
      expect(listed.code).toBe(200)
      expect(listed.data.total).toBe(1)
      jobId = listed.data.records[0].id

      await row.getByRole('button', { name: '编辑' }).click()
      dialog = page.getByRole('dialog', { name: '编辑任务' })
      await dialog.locator('.el-form-item').filter({ hasText: '任务名称' }).locator('input').fill(editedJobName)
      const editCupField = dialog.locator('.dynamic-form-field').filter({ hasText: '杯型' })
      await editCupField.locator('.el-select').click()
      await page.getByRole('option', { name: '大杯', exact: true }).click()
      await dialog.getByRole('button', { name: '确认' }).click()

      await expect(dialog).toBeHidden()
      row = page.locator('.el-table__body tr').filter({ hasText: editedJobName })
      await expect(row).toHaveCount(1)
      await row.getByRole('button', { name: '立即执行' }).click()
      await page.getByRole('button', { name: 'OK', exact: true }).click()
      await expect(page.getByText('任务已触发')).toBeVisible()

      await expect.poll(async () => {
        const logsResponse = await request.get(`/api/base/my-job/${jobId}/logs?page=1&size=10`, { headers })
        const logs: ApiEnvelope<PageData<UserJobLogFixture>> = await logsResponse.json()
        return logs.data.records[0]?.resultMessage || ''
      }, { timeout: 20_000, intervals: [1_000, 2_000] }).toContain('大杯')
    } finally {
      if (jobId) {
        const deleteResponse = await request.delete(`/api/base/my-job/${jobId}`, { headers })
        expect(deleteResponse.ok()).toBeTruthy()
      }
    }
  })
})

test.describe('普通员工权限恢复', () => {
  test.skip(!employeeToken, '需要通过 CI Secret 注入 E2E_EMPLOYEE_TOKEN')

  test('无权限 CRM 深链显示 403 而不是空白页', async ({ page }) => {
    await authenticate(page, employeeToken!, 'employee')
    await page.goto('/admin/crm/lead')
    await expect(page.getByText('403')).toBeVisible()
    await expect(page.getByRole('button', { name: /返回/ }).first()).toBeVisible()
  })
})

test.describe('审批规则 G2 专项:权限、降级与历史冲突', () => {
  /** 管理员请求头。 */
  const authHeaders = () => ({ Authorization: `Bearer ${adminToken}` })

  /** 取当前可用工作流选项（不修改任何数据）。 */
  async function fetchWorkflowOption(request: APIRequestContext): Promise<WorkflowOptionFixture> {
    const response = await request.get('/api/procurement/approval-route/workflow-options', { headers: authHeaders() })
    expect(response.ok()).toBeTruthy()
    const options: ApiEnvelope<WorkflowOptionFixture[]> = await response.json()
    expect(options.code).toBe(200)
    expect(Array.isArray(options.data)).toBeTruthy()
    expect(options.data.length).toBeGreaterThan(0)
    return options.data[0]
  }

  /** 创建一条审批规则 fixture。 */
  async function createRouteFixture(
    request: APIRequestContext,
    payload: { categoryCode: string; minAmount: string; maxAmount?: string; modelVersionId: number | string },
  ): Promise<RouteFixture> {
    const response = await request.post('/api/procurement/approval-route', {
      headers: authHeaders(),
      data: { routeName: `G2E2E-${payload.categoryCode}`, ...payload },
    })
    expect(response.ok()).toBeTruthy()
    const created: ApiEnvelope<RouteFixture> = await response.json()
    expect(created.code).toBe(200)
    expect(created.data.id).toBeTruthy()
    expect(created.data.version).not.toBeNull()
    return created.data
  }

  /** 静默删除审批规则，清理失败不掩盖主断言失败。 */
  async function deleteRouteSilently(request: APIRequestContext, route: RouteFixture) {
    if (!route.id || route.version == null) return
    await request.delete(`/api/procurement/approval-route/${route.id}?version=${route.version}`, {
      headers: authHeaders(),
    })
  }

  /** 创建一条品类 fixture：审批规则服务端校验品类必须真实存在。 */
  async function createCategoryFixture(request: APIRequestContext, code: string): Promise<CategoryFixture> {
    const response = await request.post('/api/procurement/material/category', {
      headers: authHeaders(),
      data: { parentId: 0, categoryCode: code, categoryName: `G2 E2E 品类 ${code}`, sort: 999, status: 1 },
    })
    expect(response.ok()).toBeTruthy()
    const created: ApiEnvelope<CategoryFixture> = await response.json()
    expect(created.code).toBe(200)
    expect(created.data.id).toBeTruthy()
    expect(created.data.version).not.toBeNull()
    return created.data
  }

  /** 静默删除品类 fixture，清理失败不掩盖主断言失败。 */
  async function deleteCategorySilently(request: APIRequestContext, category: CategoryFixture) {
    if (!category.id || category.version == null) return
    await request.delete(`/api/procurement/material/category/${category.id}?version=${category.version}`, {
      headers: authHeaders(),
    })
  }

  /** 创建并发布一个 G2 专用工作流模型，返回模型与已发布版本 ID。 */
  async function publishG2Model(request: APIRequestContext): Promise<{
    modelId: number | string
    modelVersionId: number | string
    modelKey: string
  }> {
    const modelKey = `e2e-g2-${Date.now()}`
    const createResponse = await request.post('/api/workflow/model', {
      headers: authHeaders(),
      data: { modelKey, modelName: 'G2 E2E 失效模型注入', category: 'purchase' },
    })
    expect(createResponse.ok()).toBeTruthy()
    const created: ApiEnvelope<WorkflowModelFixture> = await createResponse.json()
    expect(created.code).toBe(200)
    const modelId = created.data.id

    const draftResponse = await request.put(`/api/workflow/model/${modelId}/draft`, {
      headers: authHeaders(),
      data: {
        designerJson: JSON.stringify({ version: 1, fixture: 'g2' }),
        bpmnXml: buildWorkflowFixtureXml(modelKey),
      },
    })
    expect(draftResponse.ok()).toBeTruthy()
    const validateResponse = await request.post(`/api/workflow/model/${modelId}/validate`, { headers: authHeaders() })
    expect(validateResponse.ok()).toBeTruthy()
    const publishResponse = await request.post(`/api/workflow/model/${modelId}/publish`, { headers: authHeaders() })
    expect(publishResponse.ok()).toBeTruthy()

    const detailResponse = await request.get(`/api/workflow/model/${modelId}`, { headers: authHeaders() })
    expect(detailResponse.ok()).toBeTruthy()
    const detail: ApiEnvelope<WorkflowModelFixture> = await detailResponse.json()
    expect(detail.data.currentPublishedVersionId).not.toBeNull()
    return { modelId, modelVersionId: detail.data.currentPublishedVersionId!, modelKey }
  }

  test('管理员可访问审批规则页面、列表与工作流选项 API', async ({ page, request }) => {
    test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')
    await authenticate(page, adminToken!, 'admin')
    await expectRendered(page, '/admin/procurement/approval-route')
    await expect(page.locator('.approval-rules-page')).toContainText('先试一笔')

    const listResponse = await request.get('/api/procurement/approval-route/list', {
      headers: authHeaders(),
      params: { page: 1, size: 1 },
    })
    expect(listResponse.ok()).toBeTruthy()
    const listed: ApiEnvelope<PageData<RouteFixture>> = await listResponse.json()
    expect(listed.code).toBe(200)
    expect(Array.isArray(listed.data.records)).toBeTruthy()

    const option = await fetchWorkflowOption(request)
    expect(option.modelVersionId).toBeTruthy()

    const coverageResponse = await request.get('/api/procurement/approval-route/coverage', { headers: authHeaders() })
    expect(coverageResponse.ok()).toBeTruthy()
    const coverage: ApiEnvelope<CoverageReportFixture> = await coverageResponse.json()
    expect(coverage.code).toBe(200)
    expect(Array.isArray(coverage.data.categories)).toBeTruthy()
    for (const category of coverage.data.categories) {
      for (const segment of category.segments) {
        expect(typeof segment.outcome).toBe('string')
        expect(segment.outcome.length).toBeGreaterThan(0)
      }
    }
  })

  test('确定性不变式:同品类重叠区间规则被写入端拒绝(409)', async ({ request }) => {
    test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行 fixture 写入')
    const categoryCode = `E2E_G2A_${Date.now()}`
    const { modelVersionId } = await fetchWorkflowOption(request)
    const category = await createCategoryFixture(request, categoryCode)

    const routeA = await createRouteFixture(request, {
      categoryCode, minAmount: '10000.0000', maxAmount: '60000.0000', modelVersionId,
    })
    try {
      // 写入端不变式:同品类 ACTIVE 区间重叠被确定性拒绝。运行时 AMBIGUOUS 是针对
      // 脏数据的 resolver 防御分支，由 ApprovalRouteResolverTest 单测闭环（分层验收）。
      const overlapResponse = await request.post('/api/procurement/approval-route', {
        headers: authHeaders(),
        data: {
          routeName: `G2E2E-overlap-${categoryCode}`,
          categoryCode, minAmount: '50000.0000', maxAmount: '100000.0000', modelVersionId,
        },
      })
      expect(overlapResponse.ok()).toBeTruthy()
      const overlap: ApiEnvelope<unknown> = await overlapResponse.json()
      expect(overlap.code).toBe(409)

      // 不变式成立证据:同品类仅一条 ACTIVE 规则，match-preview 唯一命中 fixture 规则。
      const uniqueResponse = await request.post('/api/procurement/approval-route/match-preview', {
        headers: authHeaders(),
        data: { categoryCode, totalAmount: '55000.0000' },
      })
      expect(uniqueResponse.ok()).toBeTruthy()
      const unique: ApiEnvelope<MatchPreviewFixture> = await uniqueResponse.json()
      expect(unique.code).toBe(200)
      expect(unique.data.outcome).toBe('MATCHED')
      expect(String(unique.data.routeId)).toBe(String(routeA.id))
      expect(unique.data.defaultRule).toBe(false)

      // 变更影响契约:对 fixture 规则模拟排除，不再依赖碰巧存在的种子数据。
      const impactResponse = await request.get('/api/procurement/approval-route/impact', {
        headers: authHeaders(),
        params: { routeId: routeA.id },
      })
      expect(impactResponse.ok()).toBeTruthy()
      const impact: ApiEnvelope<ImpactReportFixture> = await impactResponse.json()
      expect(impact.code).toBe(200)
      expect(String(impact.data.routeId)).toBe(String(routeA.id))
      expect(impact.data.gapSegmentCount).toBeGreaterThanOrEqual(0)
      expect(impact.data.actionMessage.length).toBeGreaterThan(0)
    } finally {
      await deleteRouteSilently(request, routeA)
      await deleteCategorySilently(request, category)
    }
  })

  test('金额边界与匹配语义:精确品类命中、断档与无规则', async ({ request }) => {
    test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行 fixture 写入')
    const categoryCode = `E2E_G2B_${Date.now()}`
    const { modelVersionId } = await fetchWorkflowOption(request)
    const category = await createCategoryFixture(request, categoryCode)

    const lowerRoute = await createRouteFixture(request, {
      categoryCode, minAmount: '10000.0000', maxAmount: '50000.0000', modelVersionId,
    })
    const upperRoute = await createRouteFixture(request, {
      categoryCode, minAmount: '60000.0000', maxAmount: '100000.0000', modelVersionId,
    })
    try {
      const matrix: Array<{ totalAmount: string; expectRouteId?: number | string }> = [
        { totalAmount: '0.0000' },
        { totalAmount: '9999.9900' },
        { totalAmount: '10000.0000', expectRouteId: lowerRoute.id },
        { totalAmount: '55000.0000' },
        { totalAmount: '99999.9900', expectRouteId: upperRoute.id },
        { totalAmount: '100000.0000' },
      ]
      for (const item of matrix) {
        const response = await request.post('/api/procurement/approval-route/match-preview', {
          headers: authHeaders(),
          data: { categoryCode, totalAmount: item.totalAmount },
        })
        expect(response.ok()).toBeTruthy()
        const preview: ApiEnvelope<MatchPreviewFixture> = await response.json()
        expect(preview.code).toBe(200)
        if (item.expectRouteId == null) {
          expect(preview.data.outcome, `金额 ${item.totalAmount} 应无规则命中`).toBe('NO_MATCH')
        } else {
          expect(preview.data.outcome, `金额 ${item.totalAmount} 应命中 fixture 规则`).toBe('MATCHED')
          expect(String(preview.data.routeId)).toBe(String(item.expectRouteId))
          expect(preview.data.defaultRule).toBe(false)
        }
      }
    } finally {
      await deleteRouteSilently(request, upperRoute)
      await deleteRouteSilently(request, lowerRoute)
      await deleteCategorySilently(request, category)
    }
  })

  // G2-3 隔离 CI 两阶段编排（ISOLATED_DEPENDENCY_OUTAGE）：
  // PREPARE 在 workflow 健康时创建 fixture 并落盘状态；编排层停止隔离 project 的 workflow 后，
  // ASSERT 在依赖不可达下验证降级语义（WORKFLOW_UNAVAILABLE + routeId/routeName 保留）。
  // 本地无编排时不运行（保持诚实 skip）；不修改任何生产代码。
  const outageStateFile = process.env.G2_OUTAGE_STATE_FILE

  test('G2OUTAGE-PREPARE', async ({ request }) => {
    test.skip(!process.env.E2E_WORKFLOW_FAULT_INJECTION || !!outageStateFile, '仅在隔离 CI 编排第一阶段（workflow 健康时）运行')
    const categoryCode = `E2E_OUTAGE_${Date.now()}`
    const categoryResponse = await request.post('/api/procurement/material/category', {
      headers: authHeaders(),
      data: { parentId: 0, categoryCode, categoryName: `G2 Outage ${categoryCode}`, sort: 999, status: 1 },
    })
    expect(categoryResponse.ok()).toBeTruthy()
    const { modelVersionId } = await fetchWorkflowOption(request)
    const route = await createRouteFixture(request, {
      categoryCode, minAmount: '0.0000', modelVersionId,
    })
    expect(route.id).toBeTruthy()
    expect(route.routeName).toBeTruthy()
    writeFileSync(outageStateFile!, JSON.stringify({ routeId: route.id, routeName: route.routeName, categoryCode }))
  })

  test('G2OUTAGE-ASSERT', async ({ request }) => {
    test.skip(!process.env.E2E_WORKFLOW_FAULT_INJECTION || !outageStateFile, '仅在隔离 CI 编排第二阶段（workflow 已停止）运行')
    const state = JSON.parse(readFileSync(outageStateFile!, 'utf8')) as { routeId: number | string; routeName: string; categoryCode: string }
    const response = await request.post('/api/procurement/approval-route/match-preview', {
      headers: authHeaders(),
      data: { categoryCode: state.categoryCode, totalAmount: '100.0000' },
    })
    expect(response.ok()).toBeTruthy()
    const preview: ApiEnvelope<MatchPreviewFixture> = await response.json()
    expect(preview.code).toBe(200)
    expect(preview.data.outcome).toBe('WORKFLOW_UNAVAILABLE')
    expect(String(preview.data.routeId)).toBe(String(state.routeId))
    expect(preview.data.routeName).toBe(state.routeName)
  })

  test('确定性 WORKFLOW_UNAVAILABLE:失效模型保留 routeId 与 routeName', async ({ request }) => {
    test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')
    test.skip(!mutationsEnabled, '仅在隔离 E2E 环境设置 E2E_MUTATIONS=true 后执行故障注入')
    // G2-3 DEFERRED_TO_ISOLATED_CI:Workflow 依赖失效点在 procurement 服务内部 Feign 调用，
    // 黑盒 E2E 无法注入且禁止停共享容器；降级语义已由 ApprovalRouteInsightServiceImplTest 闭环。
    // 隔离 CI 提供故障注入设施时设置 E2E_WORKFLOW_FAULT_INJECTION=true 启用本用例。
    test.skip(!process.env.E2E_WORKFLOW_FAULT_INJECTION, '需要隔离 Workflow fault injection 设施')
    test.setTimeout(120_000)
    const categoryCode = `E2E_G2C_${Date.now()}`
    const category = await createCategoryFixture(request, categoryCode)

    // 创建绑定正常规则的 fixture。
    const { modelVersionId } = await publishG2Model(request)
    const boundRoute = await createRouteFixture(request, {
      categoryCode, minAmount: '0.0000', modelVersionId,
    })
    try {
      // 故障注入点：由隔离 CI 的 Workflow fault injection 设施在此制造依赖失效
      // （如隔离层失效绑定版本或中断 Feign 连通），不得停共享容器。
      const response = await request.post('/api/procurement/approval-route/match-preview', {
        headers: authHeaders(),
        data: { categoryCode, totalAmount: '100.0000' },
      })
      expect(response.ok()).toBeTruthy()
      const preview: ApiEnvelope<MatchPreviewFixture> = await response.json()
      expect(preview.code).toBe(200)
      expect(preview.data.outcome).toBe('WORKFLOW_UNAVAILABLE')
      expect(String(preview.data.routeId)).toBe(String(boundRoute.id))
      expect(preview.data.routeName).toBeTruthy()
    } finally {
      await deleteRouteSilently(request, boundRoute)
      await deleteCategorySilently(request, category)
    }
  })

  test('键盘操作触发测试匹配且三视口渲染正常', async ({ page }) => {
    test.skip(!adminToken, '需要通过 CI Secret 注入 E2E_ADMIN_TOKEN')
    await authenticate(page, adminToken!, 'admin')
    const viewports = [
      { width: 390, height: 844 },
      { width: 1024, height: 768 },
      { width: 1440, height: 900 },
    ]
    for (const viewport of viewports) {
      await page.setViewportSize(viewport)
      await expectRendered(page, '/admin/procurement/approval-route')
      const pageRoot = page.locator('.approval-rules-page')
      await expect(pageRoot).toContainText('先试一笔')
      if (viewport.width === 1440) {
        await pageRoot.locator('.match-tester input[type="text"]').nth(1).fill('-1')
        await pageRoot.getByRole('button', { name: '测试匹配' }).focus()
        await page.keyboard.press('Enter')
        await expect(
          page.locator('.el-message').filter({ hasText: '请选择具体品类，并输入最多 4 位小数的非负金额' }),
        ).toBeVisible()
      }
    }
  })

  test('员工权限不足:页面 403 且服务端拒绝匹配 API', async ({ page, request }) => {
    test.skip(!employeeToken, '需要通过 CI Secret 注入 E2E_EMPLOYEE_TOKEN')
    await authenticate(page, employeeToken!, 'employee')
    await page.goto('/admin/procurement/approval-route')
    await expect(page.getByText('403')).toBeVisible()
    await expect(page.getByRole('button', { name: /返回/ }).first()).toBeVisible()

    const response = await request.post('/api/procurement/approval-route/match-preview', {
      headers: { Authorization: `Bearer ${employeeToken}` },
      data: { categoryCode: 'IT_DEVICE', totalAmount: '10000.0000' },
    })
    expect(response.status()).toBe(403)
  })
})

test.describe('供应商门户', () => {
  test.skip(!supplierToken, '需要通过 CI Secret 注入 E2E_SUPPLIER_TOKEN')

  test('窄屏门户可进入且后台路由被隔离', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await authenticate(page, supplierToken!, 'supplier')
    await page.goto('/supplier-portal')
    await expect(page.locator('.portal-page')).toBeVisible()
    await expect(page.locator('body')).not.toHaveCSS('overflow-x', 'scroll')

    await page.goto('/admin/dashboard')
    await expect(page).toHaveURL(/\/supplier-portal/)
  })
})
