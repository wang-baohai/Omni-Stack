/**
 * @module functional.spec
 * 登录后功能与权限的 Playwright 断言型回归。
 *
 * 认证用例从 CI Secret 注入短期 JWT，不读取 Redis、数据库或 CAPTCHA 明文：
 * E2E_ADMIN_TOKEN、E2E_EMPLOYEE_TOKEN、E2E_SUPPLIER_TOKEN。
 */
import { expect, test, type Page } from '@playwright/test'

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
  id: number
  currentPublishedVersionId: number | null
}

interface UserJobFixture {
  id: number
  jobName: string
}

interface UserJobLogFixture {
  resultMessage: string | null
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
