/** 调度教学：普通用户真实创建、暂停、编辑、手动/定时执行、失败日志与删除。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test, type Locator, type Page } from '@playwright/test'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'
import { createXxlAudit, type XxlExecution } from '../fixtures/xxl-audit'

interface Job { id: number; xxlJobId: number; jobName: string; jobType: string; cronExpression: string; jobParams: string; status: number; createBy: string }
interface Log { id: number; jobId: number; status: number; fireTime: string; resultMessage: string | null; errorMessage: string | null }
interface Operation { status: 'SUBMITTED' | 'COMMITTED' | 'NOT_SENT'; path: string; at: string; result?: unknown }
interface Progress {
  operations: Record<string, Operation>
  captures: Record<string, { image: string; sha256: string }>
  good?: Job; bad?: Job; manualLog?: Log; scheduledLog?: Log; failureLog?: Log
  resumedAt?: string; status?: 'PASS'
  xxlSuccess?: XxlExecution[]; xxlFailure?: XxlExecution[]; remoteDeleted?: boolean
  originJob?: Job; originLogs?: XxlExecution[]; originDeleted?: boolean
}
interface Journal { schema: number; project: string; runId: string; baseline: string; locales: Partial<Record<DocsLocale, Progress>> }
interface Teaching { schema: number; project: string; runId: string; baseline: string; users: Record<string, { username: string; roleCodes: string[] }>; objects: { schedulingSetup: { status: string; typeCode: string } } }
const enabled = process.env.E2E_SCHEDULING_UI_WRITES === 'allow-owned-project'
test.use({ actionTimeout: 20_000 })
const dictionaries = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const hash = (value: string | Buffer) => createHash('sha256').update(value).digest('hex')
const sourcePath = 'omni-frontend/e2e-docs/flows/scheduling-teaching-ui.flows.spec.ts'
const sourceHash = hash(readFileSync(resolve('e2e-docs/flows/scheduling-teaching-ui.flows.spec.ts')))
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
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('仅允许本机 Docker external 环境')
  for (const key of ['E2E_SCHEDULING_FRONTEND_IMAGE', 'E2E_SCHEDULING_BACKEND_IMAGE']) if (!/^sha256:[a-f0-9]{64}$/.test(process.env[key] || '')) throw new Error(`必须提供已核对镜像摘要 ${key}`)
  teaching = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_STATE_FILE, '教学回执'), 'utf8')) as Teaching
  tokens = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_TOKENS_FILE, '短期身份'), 'utf8')) as Record<string, string>
  journalPath = outside(process.env.E2E_SCHEDULING_UI_STATE_FILE, '操作回执')
  evidencePath = outside(process.env.E2E_TEACHING_EVIDENCE_PATH, '截图清单')
  outside(process.env.E2E_DOCS_IMAGE_DIR, '原图目录')
  const target = new URL(process.env.E2E_BASE_URL!)
  if (teaching.schema !== 1 || teaching.project !== process.env.E2E_SCHEDULING_UI_PROJECT || !/^omni-wiki-acceptance-[a-z0-9-]+$/.test(teaching.project)
    || !/^WIKI-[A-Z0-9-]+$/.test(teaching.runId) || target.hostname !== 'omni-frontend' || target.port !== '3000' || teaching.objects.schedulingSetup.status !== 'READY') throw new Error('隔离项目或调度准备状态不符')
  if (teaching.users.requester!.roleCodes.includes('SUPER_ADMIN')) throw new Error('个人任务必须使用普通用户')
  journal = existsSync(journalPath) ? JSON.parse(readFileSync(journalPath, 'utf8')) as Journal
    : { schema: 1, project: teaching.project, runId: `${teaching.runId}-SCHUI`, baseline: teaching.baseline, locales: {} }
  if (journal.project !== teaching.project || journal.runId !== `${teaching.runId}-SCHUI`) throw new Error('拒绝覆盖其他演练回执')
  persist()
}
function label(locale: DocsLocale, key: string): string {
  let value: unknown = dictionaries[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown>)[part]
  if (typeof value !== 'string') throw new Error(`语言键不存在 ${locale}/${key}`)
  return value
}
function field(page: Page, scope: Locator, locale: DocsLocale, key: string) {
  const escaped = label(locale, key).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return scope.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${escaped}$`) }) })
}
const dialog = (page: Page) => page.locator('.el-dialog:visible').last()

for (const locale of docsLocales) test(`Scheduling 普通用户真实生命周期 / ${locale}`, async ({ page }) => {
  test.skip(!enabled, '需要显式授权本次隔离教学项目、普通身份及仓库外回执')
  test.setTimeout(240_000)
  for (const actor of ['admin', 'requester', 'manager']) {
    const token = tokens[`${actor}Token`]!
    const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
    if (claims.exp * 1000 < Date.now() + 240_000) throw new Error(`短期身份即将过期 ${actor}`)
  }
  const p = journal.locales[locale] ??= { operations: {}, captures: {} }; persist()
  const xxl = await createXxlAudit(outside(process.env.E2E_SCHEDULING_PRIVATE_ENV_FILE, '隔离调度中心配置'))
  const prefix = `${journal.runId}-${locale}`, goodName = `${prefix}-WATER`, editedName = `${goodName}-EDITED`, badName = `${prefix}-UNHANDLED`
  const request = async <T>(actor: string, path: string, method = 'GET', body?: unknown, expected = 200): Promise<T> => {
    const response = await page.request.fetch(path, { method, headers: { Authorization: `Bearer ${tokens[`${actor}Token`]}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! }, ...(body === undefined ? {} : { data: body }) }).catch(() => { throw new Error(`真实 API 网络失败 ${method} ${path}；不输出身份请求头`) })
    const value = await response.json() as { code: number; data: T; message?: string }
    if (expected === 200) expect(response.status(), `${method} ${path}`).toBe(200)
    expect(value.code, `${method} ${path}: ${value.message || ''}`).toBe(expected)
    return value.data
  }
  const list = () => request<{ records: Job[] }>('requester', `/api/base/my-job/list?page=1&size=100&jobName=${encodeURIComponent(prefix)}`)
  const logs = (id: number) => request<{ records: Log[] }>('requester', `/api/base/my-job/${id}/logs?page=1&size=100`)
  const inspect = async (id: number) => { const row = (await list()).records.find(x => x.id === id); expect(row).toBeTruthy(); return row! }
  const operation = async <T>(key: string, path: string, action: () => Promise<T>) => {
    if (p.operations[key]?.status === 'COMMITTED') return p.operations[key].result as T
    if (p.operations[key]?.status === 'SUBMITTED') throw new Error(`操作结果未知，先只读核对 ${key}`)
    p.operations[key] = { status: 'SUBMITTED', path, at: new Date().toISOString() }; persist()
    const result = await action()
    p.operations[key] = { status: 'COMMITTED', path, at: new Date().toISOString(), result: result ?? null }; persist()
    return result
  }
  const submit = async <T>(key: string, path: string, method: string, click: () => Promise<unknown>) => operation(key, path, async () => {
    const [response] = await Promise.all([
      page.waitForResponse(r => { const u = new URL(r.url()); return u.pathname + u.search === path && r.request().method() === method }),
      click(),
    ])
    const value = await response.json() as { code: number; data: T; message?: string }
    expect(response.status()).toBe(200); expect(value.code, value.message || path).toBe(200)
    return value.data
  })
  const goto = async (actor: string, path: string) => {
    await prepareDocsPage(page, { locale, token: tokens[`${actor}Token`], username: actor === 'admin' ? 'admin' : teaching.users[actor]!.username, viewport: { width: 1920, height: 1080 } })
    await page.goto(path); await waitForDocsPage(page, 'main'); await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  }
  const capture = async (step: string, flow: string, claim: string, actor = 'requester') => {
    if (p.captures[step]) { expect(hash(readFileSync(p.captures[step].image))).toBe(p.captures[step].sha256); return }
    await waitForDocsPage(page, 'main'); await captureDocsImage(page, locale, step)
    const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${step}.png`), sha256 = hash(readFileSync(image))
    const evidence = existsSync(evidencePath) ? JSON.parse(readFileSync(evidencePath, 'utf8')) as Record<string, unknown>[] : []
    evidence.push({ schema: 1, step, module: 'scheduling', flow, locale, actor, project: journal.project, runId: prefix, baseline: journal.baseline, route: new URL(page.url()).pathname, claim, image, sha256, capturedAt: new Date().toISOString(), mocks: false, visualReview: 'pending', test: sourcePath, testSha256: sourceHash, runtimeImage: process.env.E2E_SCHEDULING_FRONTEND_IMAGE, backendImage: process.env.E2E_SCHEDULING_BACKEND_IMAGE, viewport: { width: 1920, height: 1080 } })
    writeFileSync(`${evidencePath}.next`, JSON.stringify(evidence, null, 2)); renameSync(`${evidencePath}.next`, evidencePath)
    p.captures[step] = { image, sha256 }; persist()
  }
  const jobsPage = async () => {
    await goto('requester', '/')
    await page.getByRole('tab', { name: label(locale, 'workflow.myJobs'), exact: true }).click()
    await expect(page.locator('.el-tab-pane:visible .el-loading-mask:visible')).toHaveCount(0)
  }
  const row = (name: string) => page.locator('.el-tab-pane:visible .el-table__body-wrapper tr').filter({ hasText: name })
  const chooseFrequency = async (daily: boolean) => {
    const cron = dialog(page).locator('.cron-generator')
    await cron.locator('.el-select').first().click()
    await page.getByRole('option', { name: label(locale, daily ? 'cron.daily' : 'cron.everyMinute'), exact: true }).click()
    if (daily) {
      await field(page, cron, locale, 'cron.hour').locator('input').fill('23')
      await field(page, cron, locale, 'cron.minute').locator('input').fill('59')
      await field(page, dialog(page), locale, 'userJob.jobName').locator('input').focus()
    }
  }
  const createForm = async (name: string, typeName: string) => {
    await page.getByRole('button', { name: label(locale, 'workspace.createJob'), exact: true }).click()
    await field(page, dialog(page), locale, 'userJob.jobName').locator('input').fill(name)
    await field(page, dialog(page), locale, 'userJob.jobType').locator('.el-select').click()
    await page.getByRole('option', { name: typeName, exact: true }).click()
    await chooseFrequency(true)
  }
  const cup = async (name: string) => {
    await dialog(page).locator('.dynamic-form-field').filter({ hasText: '杯型' }).locator('.el-select').click()
    await page.getByRole('option', { name, exact: true }).click()
  }
  const confirm = () => dialog(page).getByRole('button', { name: label(locale, 'common.confirm'), exact: true }).click()
  const toggle = async (key: string, job: Job, name: string, status: number) => {
    // 已确认的早期状态可能在后续步骤合法改变，续做时不重放或断言旧状态。
    if (p.operations[key]?.status === 'COMMITTED') return
    await submit(key, `/api/base/my-job/${job.id}/status?status=${status}`, 'PUT', () => row(name).locator('.el-switch').click())
    await expect(row(name).getByRole('switch')).toHaveAttribute('aria-checked', status === 1 ? 'true' : 'false')
    expect((await inspect(job.id)).status).toBe(status)
  }
  const trigger = async (key: string, job: Job, name: string) => {
    await submit(key, `/api/base/my-job/${job.id}/trigger`, 'POST', async () => {
      await row(name).getByRole('button', { name: label(locale, 'userJob.triggerNow'), exact: true }).click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
    })
  }
  const showLogs = async (name: string) => {
    await row(name).getByRole('button', { name: label(locale, 'workspace.viewLogs'), exact: true }).click()
    await expect(dialog(page).locator('.el-loading-mask:visible')).toHaveCount(0)
  }
  const deleteJob = async (key: string, job: Job, name: string) => {
    await submit(key, `/api/base/my-job/${job.id}`, 'DELETE', async () => {
      await row(name).getByRole('button', { name: label(locale, 'common.delete'), exact: true }).click()
      await page.locator('.el-message-box__btns .el-button--primary').click()
    })
    await expect(row(name)).toHaveCount(0); expect((await list()).records.some(x => x.id === job.id)).toBe(false)
  }
  if (p.status === 'PASS') {
    expect((await list()).records.filter(row => row.id !== p.originJob?.id)).toHaveLength(0)
    for (const record of Object.values(p.captures)) expect(hash(readFileSync(record.image))).toBe(record.sha256)
    // 首次中文验收删除了调度中心日志；只补做缺少的 Cron 来源证明，不重放已完成的 UI 生命周期。
    if (!p.xxlSuccess && !p.originDeleted) {
      const name = `${prefix}-ORIGIN`
      p.originJob = await operation('origin:create', '/api/base/my-job', () => request<Job>('requester', '/api/base/my-job', 'POST', { jobName: name, jobType: 'Task-00001', cronExpression: '0 59 23 * * ?', jobParams: '{"cupShape":"大杯"}' })); persist()
      await operation('origin:pause', `/api/base/my-job/${p.originJob.id}/status?status=0`, () => request('requester', `/api/base/my-job/${p.originJob!.id}/status?status=0`, 'PUT'))
      await operation('origin:edit', `/api/base/my-job/${p.originJob.id}`, () => request('requester', `/api/base/my-job/${p.originJob!.id}`, 'PUT', { cronExpression: '0 * * * * ?' }))
      await operation('origin:manual', `/api/base/my-job/${p.originJob.id}/trigger`, () => request('requester', `/api/base/my-job/${p.originJob!.id}/trigger`, 'POST'))
      await expect.poll(async () => (await logs(p.originJob!.id)).records.some(x => x.status === 1), { timeout: 45_000, intervals: [500, 1000] }).toBe(true)
      await operation('origin:resume', `/api/base/my-job/${p.originJob.id}/status?status=1`, () => request('requester', `/api/base/my-job/${p.originJob!.id}/status?status=1`, 'PUT'))
      await expect.poll(async () => (await xxl.logs(p.originJob!.xxlJobId, p.originJob!.id)).some(x => x.origin === 'CRON' && x.handleCode === 200), { timeout: 90_000, intervals: [1000, 2000] }).toBe(true)
      await operation('origin:stop', `/api/base/my-job/${p.originJob.id}/status?status=0`, () => request('requester', `/api/base/my-job/${p.originJob!.id}/status?status=0`, 'PUT'))
      p.originLogs = await xxl.logs(p.originJob.xxlJobId, p.originJob.id); persist()
      expect(p.originLogs.some(x => x.origin === 'MANUAL' && x.handleCode === 200)).toBe(true)
      await jobsPage(); await showLogs(name)
      await capture('scheduling-ui-cron-origin-proof', 'execution-log', 'Supplemental API-created owned job: actual manual and Cron success correlated with XXL trigger origins')
      await dialog(page).locator('.el-dialog__headerbtn').click()
      await operation('origin:delete', `/api/base/my-job/${p.originJob.id}`, () => request('requester', `/api/base/my-job/${p.originJob!.id}`, 'DELETE'))
      expect(await xxl.absent(p.originJob.xxlJobId)).toBe(true); p.originDeleted = true; persist()
    }
    return
  }
  await jobsPage()
  if (!p.good) {
    expect((await list()).records.filter(x => x.jobName === goodName)).toHaveLength(0)
    await createForm(goodName, '喝水提醒'); await cup('小杯')
    await capture('scheduling-ui-create-form', 'personal-create', 'Ordinary user chooses real enabled type, daily23:59 and small cup before saving')
    p.good = await submit<Job>('createGood', '/api/base/my-job', 'POST', confirm); persist()
    expect(p.good.createBy).toBe(teaching.users.requester!.username); expect(p.good.cronExpression).toBe('0 59 23 * * ?'); expect(p.good.xxlJobId).toBeGreaterThan(0)
  }
  await expect(row(goodName)).toHaveCount(1)
  await capture('scheduling-ui-created', 'personal-create', 'Real local row and returned XXL job ID confirmed; initially enabled daily job')
  await toggle('pauseInitial', p.good, goodName, 0)
  await capture('scheduling-ui-paused', 'pause', 'Ordinary owner pauses the job; switch is off and next time is absent')
  if (p.operations.editGood?.status !== 'COMMITTED') {
    await row(goodName).getByRole('button', { name: label(locale, 'common.edit'), exact: true }).click()
    await field(page, dialog(page), locale, 'userJob.jobName').locator('input').fill(editedName)
    await chooseFrequency(false); await cup('大杯')
    await capture('scheduling-ui-edit-form', 'schedule', 'While paused, edit to every minute and large cup; type remains immutable')
    await submit<Job>('editGood', `/api/base/my-job/${p.good.id}`, 'PUT', confirm)
  }
  await expect(row(editedName)).toHaveCount(1)
  const edited = await inspect(p.good.id); expect(edited.cronExpression).toBe('0 * * * * ?'); expect(JSON.parse(edited.jobParams).cupShape).toBe('大杯'); expect(edited.status).toBe(0)
  if (!p.manualLog) {
    const before = (await logs(p.good.id)).records[0]?.id || 0
    await trigger('manualWhilePaused', p.good, editedName)
    await expect.poll(async () => (await logs(p.good!.id)).records.some(x => x.id > before && x.status === 1), { timeout: 45_000, intervals: [500, 1000, 2000] }).toBe(true)
    p.manualLog = (await logs(p.good.id)).records.find(x => x.id > before)!; persist()
    expect(p.manualLog.resultMessage).toContain('大杯'); expect((await inspect(p.good.id)).status).toBe(0)
  }
  await showLogs(editedName)
  await expect(dialog(page)).toContainText('大杯')
  await capture('scheduling-ui-manual-success', 'trigger', 'Trigger accepted while paused; actual successful execution log contains large cup result')
  await dialog(page).locator('.el-dialog__headerbtn').click()
  if (!p.resumedAt) { p.resumedAt = new Date().toISOString(); persist() }
  await toggle('resume', p.good, editedName, 1)
  await capture('scheduling-ui-resumed', 'resume', 'Switch enabled; every-minute Cron becomes active again')
  if (!p.scheduledLog) {
    await expect.poll(async () => (await logs(p.good!.id)).records.some(x => x.id > p.manualLog!.id && x.status === 1), { timeout: 90_000, intervals: [1000, 2000] }).toBe(true)
    p.scheduledLog = (await logs(p.good.id)).records.find(x => x.id > p.manualLog!.id)!; persist()
  }
  await toggle('pauseAfterSchedule', p.good, editedName, 0)
  await expect.poll(async () => (await xxl.logs(p.good!.xxlJobId, p.good!.id)).some(x => x.origin === 'CRON' && x.handleCode === 200), { timeout: 20_000, intervals: [500, 1000] }).toBe(true)
  p.xxlSuccess = await xxl.logs(p.good.xxlJobId, p.good.id); persist()
  expect(p.xxlSuccess.some(x => x.origin === 'MANUAL' && x.handleCode === 200)).toBe(true)
  await showLogs(editedName)
  await expect(dialog(page).locator('.el-table__body-wrapper tr')).toHaveCount((await logs(p.good.id)).records.length)
  await capture('scheduling-ui-scheduled-success', 'execution-log', 'Later success log arrived without another manual request; Cron origin checked separately against XXL logs')
  await dialog(page).locator('.el-dialog__headerbtn').click()
  await capture('scheduling-ui-paused-final', 'pause', 'Owner stops future scheduled executions after observing the Cron run')
  await operation('ownershipBoundaries', `/api/base/my-job/${p.good.id}`, async () => {
    const outsider = await request<{ records: Job[] }>('manager', `/api/base/my-job/list?page=1&size=100&jobName=${encodeURIComponent(prefix)}`)
    expect(outsider.records).toHaveLength(0)
    for (const [method, suffix, body] of [['GET', '/logs', undefined], ['PUT', '', { jobName: 'unauthorized' }], ['PUT', '/status?status=1', undefined], ['POST', '/trigger', undefined], ['DELETE', '', undefined]] as const) await request('manager', `/api/base/my-job/${p.good!.id}${suffix}`, method, body, 403)
    expect((await inspect(p.good!.id)).status).toBe(0)
    return { listExcluded: true, logs: 403, edit: 403, status: 403, trigger: 403, delete: 403 }
  })
  await deleteJob('deleteGood', p.good, editedName)
  expect(await xxl.absent(p.good.xxlJobId)).toBe(true)
  await capture('scheduling-ui-deleted', 'personal-delete', 'Deleted owned job absent from owner list; remote absence checked separately')
  if (!p.bad) {
    await createForm(badName, teaching.objects.schedulingSetup.typeCode)
    p.bad = await submit<Job>('createBad', '/api/base/my-job', 'POST', confirm); persist()
  }
  await expect(row(badName)).toHaveCount(1)
  await toggle('pauseBad', p.bad, badName, 0)
  if (!p.failureLog) {
    await trigger('triggerBad', p.bad, badName)
    await expect.poll(async () => (await logs(p.bad!.id)).records.some(x => x.status === 0), { timeout: 45_000, intervals: [500, 1000, 2000] }).toBe(true)
    p.failureLog = (await logs(p.bad.id)).records.find(x => x.status === 0)!; persist()
    expect(p.failureLog.errorMessage).toContain(teaching.objects.schedulingSetup.typeCode)
  }
  await showLogs(badName)
  await expect.poll(async () => (await xxl.logs(p.bad!.xxlJobId, p.bad!.id)).some(x => x.handleCode === 500), { timeout: 20_000, intervals: [500, 1000] }).toBe(true)
  p.xxlFailure = await xxl.logs(p.bad.xxlJobId, p.bad.id); persist()
  const errorCell = dialog(page).locator('.el-table__body-wrapper tr').first().locator('td').last()
  await errorCell.hover()
  await expect(page.locator('.el-popper:visible').filter({ hasText: teaching.objects.schedulingSetup.typeCode })).toBeVisible()
  await capture('scheduling-ui-handler-failure', 'execution-failure', 'Real executor could not find deliberately absent handler; failed log and error tooltip visible')
  await dialog(page).locator('.el-dialog__headerbtn').click()
  await deleteJob('deleteBad', p.bad, badName)
  expect(await xxl.absent(p.bad.xxlJobId)).toBe(true); p.remoteDeleted = true; persist()
  await goto('admin', '/admin/job/system-job')
  await expect(page.locator('.el-table__body-wrapper tr').filter({ hasText: 'mqRelayHandler' })).toHaveCount(1)
  await capture('scheduling-ui-system-jobs', 'system-job', 'Actual system task metadata and live XXL statuses; no system task mutation', 'admin')
  await goto('admin', '/admin/job/user-job-type')
  await expect(page.locator('.el-table__body-wrapper tr').filter({ hasText: teaching.objects.schedulingSetup.typeCode })).toHaveCount(1)
  await capture('scheduling-ui-job-types', 'job-type', 'Global task catalog shows seeded drink-water type and owned missing-handler fixture', 'admin')
  expect((await list()).records).toHaveLength(0)
  p.status = 'PASS'; persist()
})
