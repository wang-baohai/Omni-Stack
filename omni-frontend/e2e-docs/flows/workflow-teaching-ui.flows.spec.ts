/** 工作流教学：真实设计器、普通发起人/审批人、ALL/ANY 与发布版本隔离。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test, type Locator, type Page } from '@playwright/test'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface Model { id: number; modelKey: string; currentDraftVersionId: number }
interface Published { versionId: number; businessVersion: number; processDefinitionId: string; engineVersion: number }
interface Instance { processInstanceId: string; processDefinitionId: string; modelVersionId: number; businessVersion: number; status: number }
interface Task { taskId: string; processInstanceId: string; assigneeId: number }
interface RecordEntry { result: string; assigneeId: string }
interface Operation { status: 'SUBMITTED' | 'COMMITTED'; path: string; at: string; result?: unknown }
interface Progress {
  operations: Record<string, Operation>
  captures: Record<string, { image: string; sha256: string }>
  model?: Model
  versionOne?: Published
  versionTwo?: Published
  allInstanceId?: string
  anyInstanceId?: string
  taskElementId?: string
  status?: 'PASS'
}
interface Journal { schema: number; project: string; runId: string; baseline: string; locales: Partial<Record<DocsLocale, Progress>> }
interface Teaching {
  schema: number; project: string; runId: string; baseline: string
  users: Record<string, { username: string; id: number; roleCodes: string[] }>
  objects: { workflowSetup: { candidates: string[] }; teachingApproverRole: { roleCode: string } }
}
const enabled = process.env.E2E_WORKFLOW_UI_WRITES === 'allow-owned-project'
test.use({ actionTimeout: 20_000 })
const dictionaries = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const hash = (value: string | Buffer) => createHash('sha256').update(value).digest('hex')
const sourcePath = 'omni-frontend/e2e-docs/flows/workflow-teaching-ui.flows.spec.ts'
const sourceHash = hash(readFileSync(resolve('e2e-docs/flows/workflow-teaching-ui.flows.spec.ts')))
const frontendSourcePaths = ['src/composables/useBpmnExtension.ts', 'src/composables/useBpmnModeler.ts', 'src/components/workflow/ModelDesigner.vue']
const frontendSourceHash = hash(frontendSourcePaths.map(path => `${path}\n${readFileSync(path, 'utf8')}`).join('\n'))
let teaching: Teaching, journal: Journal, tokens: Record<string, string>, journalPath: string, evidencePath: string
function outside(value: string | undefined, name: string) {
  if (!value || !isAbsolute(value)) throw new Error(`${name} 必须为绝对路径`)
  const child = relative(resolve(process.cwd(), '..'), resolve(value))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) throw new Error(`${name} 必须在仓库外`)
  return resolve(value)
}
function persist() {
  mkdirSync(dirname(journalPath), { recursive: true })
  writeFileSync(`${journalPath}.next`, JSON.stringify(journal, null, 2)); renameSync(`${journalPath}.next`, journalPath)
}
if (enabled) {
  if (!/^sha256:[a-f0-9]{64}$/.test(process.env.E2E_WORKFLOW_RUNTIME_IMAGE || '')) throw new Error('必须提供已核对的隔离前端镜像摘要')
  if (!/^sha256:[a-f0-9]{64}$/.test(process.env.E2E_WORKFLOW_BACKEND_IMAGE || '')) throw new Error('必须提供已核对的隔离工作流镜像摘要')
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('仅允许本机 Docker external 环境')
  teaching = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_STATE_FILE, '教学回执'), 'utf8')) as Teaching
  tokens = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_TOKENS_FILE, '短期身份'), 'utf8')) as Record<string, string>
  journalPath = outside(process.env.E2E_WORKFLOW_UI_STATE_FILE, '操作回执')
  evidencePath = outside(process.env.E2E_TEACHING_EVIDENCE_PATH, '截图清单')
  outside(process.env.E2E_DOCS_IMAGE_DIR, '原图目录')
  const target = new URL(process.env.E2E_BASE_URL!)
  if (teaching.schema !== 1 || teaching.project !== process.env.E2E_WORKFLOW_UI_PROJECT
    || !/^omni-wiki-acceptance-[a-z0-9-]+$/.test(teaching.project) || !/^WIKI-[A-Z0-9-]+$/.test(teaching.runId)
    || target.hostname !== 'omni-frontend' || target.port !== '3000' || teaching.objects.workflowSetup.candidates.length !== 3) throw new Error('教学项目归属、候选人或真实前端端口不符')
  journal = existsSync(journalPath) ? JSON.parse(readFileSync(journalPath, 'utf8')) as Journal
    : { schema: 1, project: teaching.project, runId: `${teaching.runId}-WFUI`, baseline: teaching.baseline, locales: {} }
  if (journal.schema !== 1 || journal.project !== teaching.project || journal.runId !== `${teaching.runId}-WFUI`) throw new Error('拒绝覆盖其他演练回执')
  persist()
}
function label(locale: DocsLocale, key: string): string {
  let value: unknown = dictionaries[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown>)[part]
  if (typeof value !== 'string') throw new Error(`语言键不存在 ${locale}/${key}`)
  return value
}
function field(page: Page, scope: Locator, locale: DocsLocale, key: string) {
  return scope.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: label(locale, key) }) })
}
const dialog = (page: Page) => page.locator('.el-dialog:visible').last()

for (const locale of docsLocales) test(`Workflow 真实设计与会签 / ${locale}`, async ({ page }) => {
  test.skip(!enabled, '需要显式授权本次隔离教学项目、普通身份及仓库外回执')
  test.setTimeout(240_000)
  const p = journal.locales[locale] ??= { operations: {}, captures: {} }; persist()
  const prefix = `${journal.runId}-${locale}`, modelKey = `Wiki20261009A-UI-${locale}`
  for (const actor of ['manager', 'requester', ...teaching.objects.workflowSetup.candidates]) {
    if (teaching.users[actor]!.roleCodes.includes('SUPER_ADMIN')) throw new Error('工作流动作必须使用普通身份')
    const token = tokens[`${actor}Token`]!
    const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
    if (claims.exp * 1000 < Date.now() + 240_000) throw new Error(`短期身份即将过期 ${actor}`)
  }
  const request = async <T>(actor: string, path: string, method = 'GET', body?: unknown): Promise<T> => {
    const response = await page.request.fetch(path, { method, headers: { Authorization: `Bearer ${tokens[`${actor}Token`]}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! }, ...(body === undefined ? {} : { data: body }) }).catch(() => { throw new Error(`真实 API 网络失败 ${method} ${path}；不输出身份请求头`) })
    const value = await response.json() as { code: number; data: T; message?: string }
    expect(response.status(), `${method} ${path}: ${value.message || ''}`).toBe(200)
    expect(value.code, `${method} ${path}: ${value.message || ''}`).toBe(200)
    return value.data
  }
  const operation = async <T>(key: string, path: string, action: () => Promise<T>) => {
    if (p.operations[key]?.status === 'COMMITTED') return p.operations[key].result as T
    if (p.operations[key]?.status === 'SUBMITTED') throw new Error(`操作结果未知，先只读核对 ${key}`)
    p.operations[key] = { status: 'SUBMITTED', path, at: new Date().toISOString() }; persist()
    const result = await action()
    p.operations[key] = { status: 'COMMITTED', path, at: new Date().toISOString(), result: result ?? null }; persist()
    return result
  }
  const submit = async <T>(key: string, path: string, method: string, click: () => Promise<unknown>) => operation(key, path, async () => {
    const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === method)
    await click(); const response = await responsePromise
    const value = await response.json() as { code: number; data: T }
    expect(response.status()).toBe(200); expect(value.code).toBe(200)
    return value.data
  })
  const goto = async (actor: string, path: string) => {
    await prepareDocsPage(page, { locale, token: tokens[`${actor}Token`], username: teaching.users[actor]!.username, viewport: { width: 1920, height: 1080 } })
    await page.goto(path); await waitForDocsPage(page, 'main')
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  }
  const capture = async (step: string, flow: string, claim: string) => {
    if (p.captures[step]) { expect(hash(readFileSync(p.captures[step].image))).toBe(p.captures[step].sha256); return }
    await waitForDocsPage(page, 'main'); await captureDocsImage(page, locale, step)
    const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${step}.png`), sha256 = hash(readFileSync(image))
    const evidence = existsSync(evidencePath) ? JSON.parse(readFileSync(evidencePath, 'utf8')) as Record<string, unknown>[] : []
    evidence.push({ schema: 1, step, module: 'workflow', flow, locale, project: journal.project, runId: prefix, baseline: journal.baseline, route: new URL(page.url()).pathname, claim, image, sha256, capturedAt: new Date().toISOString(), mocks: false, visualReview: 'pending', test: sourcePath, testSha256: sourceHash, runtimeImage: process.env.E2E_WORKFLOW_RUNTIME_IMAGE, backendImage: process.env.E2E_WORKFLOW_BACKEND_IMAGE, frontendSourceSha256: frontendSourceHash, viewport: { width: 1920, height: 1080 } })
    writeFileSync(`${evidencePath}.next`, JSON.stringify(evidence, null, 2)); renameSync(`${evidencePath}.next`, evidencePath)
    p.captures[step] = { image, sha256 }; persist()
  }
  const modelRow = () => page.locator('.el-table__body-wrapper tr').filter({ hasText: modelKey })
  const modelList = async () => {
    await goto('manager', '/admin/workflow/model')
    await page.getByPlaceholder(label(locale, 'workflow.modelSearchPlaceholder'), { exact: true }).fill(modelKey)
    await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click()
    await expect(modelRow()).toHaveCount(1)
  }
  const openDesigner = async () => {
    await modelList(); await modelRow().getByRole('button', { name: label(locale, 'workflow.design'), exact: true }).click()
    await expect(page.locator('.model-designer-dialog')).toBeVisible()
    await expect(page.locator('.designer-body .el-loading-mask:visible')).toHaveCount(0)
  }
  const closeDesigner = async () => page.locator('.designer-header').getByRole('button', { name: label(locale, 'common.close'), exact: true }).click()
  const versions = async (step: string) => {
    await modelList(); await modelRow().getByRole('button', { name: label(locale, 'workflow.version'), exact: true }).click()
    await expect(dialog(page).getByText('PUBLISHED', { exact: true })).toBeVisible()
    await expect(dialog(page).locator('.el-loading-mask:visible')).toHaveCount(0)
    await capture(step, 'model-publish', 'Real published version rows; business and engine versions are separate fields')
    await dialog(page).getByRole('button', { name: label(locale, 'common.back'), exact: true }).click()
  }
  const todos = async (actor: string, pid: string) => (await request<{ records: Task[] }>(actor, `/api/workflow/task/todo?page=1&size=100&title=${encodeURIComponent(prefix)}`)).records.filter(x => x.processInstanceId === pid)
  const allTasks = async (pid: string) => {
    const groups = await Promise.all(teaching.objects.workflowSetup.candidates.map(actor => todos(actor, pid)))
    return groups.flat()
  }
  const instances = async () => (await request<{ records: Instance[] }>('requester', `/api/workflow/process-instance/my-initiated?page=1&size=100&title=${encodeURIComponent(prefix)}`)).records
  const inspectInstance = async (pid: string) => { const record = (await instances()).find(x => x.processInstanceId === pid); expect(record).toBeTruthy(); return record! }
  const records = (pid: string) => request<RecordEntry[]>('requester', `/api/workflow/process-instance/${pid}/approval-records`)
  const overlay = async (title: string, step: string, recordView = false) => {
    await goto('manager', '/admin/workflow/instance')
    await page.getByPlaceholder(label(locale, 'workflow.title'), { exact: true }).fill(title)
    await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click()
    const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: title }); await expect(row).toHaveCount(1)
    await row.getByRole('button', { name: label(locale, recordView ? 'workflow.approvalRecords' : 'workflow.processProgress'), exact: true }).click()
    if (!recordView) await expect(dialog(page).locator('.bpmn-viewer-wrap .djs-element').first()).toBeVisible()
    await expect(dialog(page).locator('.el-loading-mask:visible')).toHaveCount(0)
    await capture(step, recordView ? 'approval-history' : 'instance-progress', title)
  }
  if (p.status === 'PASS') {
    expect((await inspectInstance(p.allInstanceId!)).status).toBe(2)
    expect((await records(p.anyInstanceId!)).filter(x => x.result === 'auto-approved')).toHaveLength(2)
    for (const c of Object.values(p.captures)) expect(hash(readFileSync(c.image))).toBe(c.sha256)
    return
  }
  if (!p.model) {
    await goto('manager', '/admin/workflow/model')
    await page.getByRole('button', { name: label(locale, 'common.create'), exact: true }).click()
    await field(page, dialog(page), locale, 'workflow.modelKey').locator('input').fill(modelKey)
    await field(page, dialog(page), locale, 'workflow.modelName').locator('input').fill(`${prefix} approvals`)
    await field(page, dialog(page), locale, 'workflow.category').locator('.el-select').click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await capture('workflow-ui-model-create-form', 'model-create', 'Filled creation form before submission; stable model key and category')
    p.model = await submit<Model>('createModel', '/api/workflow/model', 'POST', () => dialog(page).getByRole('button', { name: label(locale, 'common.confirm'), exact: true }).click()); persist()
  }
  if (!p.versionOne) {
    await openDesigner()
    if (p.operations.saveVersionOne?.status !== 'COMMITTED') {
      await expect(page.locator('.designer-canvas .djs-shape[data-element-id="StartEvent_1"]')).toBeVisible()
      await capture('workflow-ui-model-initial-canvas', 'model-design', 'New model has a start event and its own process key')
      await page.locator('.designer-canvas .djs-shape[data-element-id="StartEvent_1"]').click()
      await page.locator('.djs-context-pad.open [data-action="append.user-task"]').click()
      await page.locator('.djs-direct-editing-content').fill(`${prefix} approval`)
      await page.keyboard.press('Enter')
      const task = page.locator('.designer-canvas .djs-shape[data-element-id^="Activity_"]'); await expect(task).toHaveCount(1)
      // 追加后节点已选中；diagram-js 18 再次点击同一节点会取消选择。
      p.taskElementId = (await task.getAttribute('data-element-id'))!; persist()
      const panel = page.locator('.user-task-panel')
      await field(page, panel, locale, 'workflow.approvalRole').locator('.el-select').click()
      await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: `${teaching.runId} approver actions` }).click()
      await field(page, panel, locale, 'workflow.approvalOrganization').locator('.el-select').click()
      await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: label(locale, 'workflow.initiatorParentOrganization') }).click()
      await panel.getByText(label(locale, 'workflow.approvalModeAll'), { exact: true }).click()
      await expect(panel.getByRole('radio', { name: label(locale, 'workflow.approvalModeAll'), exact: true })).toBeChecked()
      await panel.getByRole('button', { name: label(locale, 'workflow.previewCandidates'), exact: true }).click()
      await expect(dialog(page).locator('.el-table__body-wrapper tr')).toHaveCount(3)
      await capture('workflow-ui-assignment-all-preview', 'candidate-resolution', 'Parent organization plus owned approver role resolves three ordinary candidates; ALL selected')
      await dialog(page).locator('.el-dialog__headerbtn').click()
      await page.locator('.djs-context-pad.open [data-action="append.end-event"]').click()
      await page.keyboard.press('Escape')
      await submit('saveVersionOne', `/api/workflow/model/${p.model.id}/draft`, 'PUT', () => page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.saveDraft'), exact: true }).click())
    }
    const model = await request<Model>('manager', `/api/workflow/model/${p.model.id}`)
    let draft = await request<{ bpmnXml: string }>('manager', `/api/workflow/model/version/${model.currentDraftVersionId}`)
    if (draft.bpmnXml.includes('flowable:ExecutionListener')) {
      // 仅修复已核实的旧草稿序列化缺陷，使用独立操作回执，不重发原保存请求。
      await page.locator(`.designer-canvas .djs-shape[data-element-id="${p.taskElementId}"]`).click()
      await submit('repairVersionOneSerialization', `/api/workflow/model/${p.model.id}/draft`, 'PUT', () => page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.saveDraft'), exact: true }).click())
      draft = await request<{ bpmnXml: string }>('manager', `/api/workflow/model/version/${model.currentDraftVersionId}`)
    }
    expect(draft.bpmnXml).toContain(`id="${modelKey}"`)
    expect(draft.bpmnXml).toContain('executionListener'); expect(draft.bpmnXml).not.toContain('taskListener')
    expect(draft.bpmnXml).toContain('multiInstanceLoopCharacteristics'); expect(draft.bpmnXml).toContain('flowable:collection="candidateUserIds"'); expect(draft.bpmnXml).toContain('flowable:assignee="${userId}"')
    const validationPromise = page.waitForResponse(r => new URL(r.url()).pathname === `/api/workflow/model/${p.model!.id}/validate`)
    await page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.validate'), exact: true }).click()
    expect((await (await validationPromise).json()).data.valid).toBe(true)
    await expect(dialog(page).getByText(label(locale, 'workflow.validationPassed'), { exact: true })).toBeVisible()
    await capture('workflow-ui-model-validation', 'model-validate', 'Server validates actual designer-exported XML successfully')
    await dialog(page).locator('.el-dialog__headerbtn').click()
    p.versionOne = await submit<Published>('publishVersionOne', `/api/workflow/model/${p.model.id}/publish`, 'POST', async () => {
      await page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.publish'), exact: true }).click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
    }); expect(p.versionOne.businessVersion).toBe(1); persist(); await closeDesigner()
  }
  await versions('workflow-ui-model-version-one')
  p.allInstanceId = await operation('startAll', '/api/workflow/process-instance/start', () => request<string>('requester', '/api/workflow/process-instance/start', 'POST', { modelVersionId: p.versionOne!.versionId, title: `${prefix} ALL`, businessKey: `${prefix}-ALL` })); persist()
  if (p.operations.approveAllFirst?.status !== 'COMMITTED') {
    expect(await allTasks(p.allInstanceId)).toHaveLength(3)
    await goto('srmmanager', '/')
    const row = page.locator('.workspace .el-table__body-wrapper tr').filter({ hasText: `${prefix} ALL` }); await expect(row).toHaveCount(1)
    await capture('workflow-ui-ordinary-todo', 'ordinary-approval', 'Actual ordinary candidate receives one task belonging to this instance')
    await row.getByRole('button', { name: label(locale, 'workflow.approve'), exact: true }).click()
    const task = (await todos('srmmanager', p.allInstanceId))[0]!
    await field(page, dialog(page), locale, 'workflow.comment').locator('textarea').fill(`${prefix} first ALL approval`)
    await capture('workflow-ui-approval-form', 'ordinary-approval', 'Approval form is loaded for the assigned candidate before committing')
    await submit('approveAllFirst', `/api/workflow/approval/${task.taskId}/complete`, 'POST', () => dialog(page).getByRole('button', { name: label(locale, 'common.confirm'), exact: true }).click())
  }
  expect(await allTasks(p.allInstanceId)).toHaveLength(2); expect((await inspectInstance(p.allInstanceId)).status).toBe(1)
  await overlay(`${prefix} ALL`, 'workflow-ui-all-partial-progress')
  if (!p.versionTwo) {
    await openDesigner()
    const task = page.locator(`.designer-canvas .djs-shape[data-element-id="${p.taskElementId}"]`); await task.click()
    await page.locator('.user-task-panel').getByText(label(locale, 'workflow.approvalModeAny'), { exact: true }).click()
    await expect(page.locator('.user-task-panel').getByRole('radio', { name: label(locale, 'workflow.approvalModeAny'), exact: true })).toBeChecked()
    await capture('workflow-ui-assignment-any', 'model-design', 'Edit new draft to ANY while previous ALL instance remains active')
    await submit('saveVersionTwo', `/api/workflow/model/${p.model.id}/draft`, 'PUT', () => page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.saveDraft'), exact: true }).click())
    p.versionTwo = await submit<Published>('publishVersionTwo', `/api/workflow/model/${p.model.id}/publish`, 'POST', async () => {
      await page.locator('.designer-header').getByRole('button', { name: label(locale, 'workflow.publish'), exact: true }).click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
    }); expect(p.versionTwo.businessVersion).toBe(2); persist(); await closeDesigner()
  }
  await versions('workflow-ui-model-versions-two')
  const old = await inspectInstance(p.allInstanceId)
  expect(old.processDefinitionId).toBe(p.versionOne.processDefinitionId); expect(old.modelVersionId).toBe(p.versionOne.versionId); expect(old.businessVersion).toBe(1); expect(old.status).toBe(1)
  p.anyInstanceId = await operation('startAny', '/api/workflow/process-instance/start', () => request<string>('requester', '/api/workflow/process-instance/start', 'POST', { modelVersionId: p.versionTwo!.versionId, title: `${prefix} ANY`, businessKey: `${prefix}-ANY` })); persist()
  if (p.operations.approveAny?.status !== 'COMMITTED') {
    expect(await allTasks(p.anyInstanceId)).toHaveLength(3)
    const task = (await todos('srmmanager', p.anyInstanceId))[0]!
    await operation('approveAny', `/api/workflow/approval/${task.taskId}/complete`, () => request('srmmanager', `/api/workflow/approval/${task.taskId}/complete`, 'POST', { approved: true, comment: `${prefix} ANY first approval` }))
  }
  expect(await allTasks(p.anyInstanceId)).toHaveLength(0); expect((await inspectInstance(p.anyInstanceId)).status).toBe(2)
  expect((await records(p.anyInstanceId)).filter(x => x.result === 'auto-approved')).toHaveLength(2)
  await overlay(`${prefix} ANY`, 'workflow-ui-any-auto-approved-progress')
  for (const actor of ['compliance', 'director']) {
    if (p.operations[`finishAll:${actor}`]?.status === 'COMMITTED') continue
    const task = (await todos(actor, p.allInstanceId))[0]!
    await operation(`finishAll:${actor}`, `/api/workflow/approval/${task.taskId}/complete`, () => request(actor, `/api/workflow/approval/${task.taskId}/complete`, 'POST', { approved: true, comment: `${prefix} ALL ${actor}` }))
  }
  expect((await inspectInstance(p.allInstanceId)).status).toBe(2)
  expect((await records(p.allInstanceId)).filter(x => x.result === 'approved')).toHaveLength(3)
  await overlay(`${prefix} ALL`, 'workflow-ui-all-approved-records', true)
  p.status = 'PASS'; persist()
})
