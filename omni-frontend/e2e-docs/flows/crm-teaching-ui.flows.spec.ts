/** CRM 教学逐步操作：普通销售角色、真实接口、隔离 Docker、写前回执和失败后只读核对。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test, type Locator, type Page } from '@playwright/test'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface BusinessRecord {
  id: number
  version: number
  status: string
  fullName?: string
  companyName?: string
  subject?: string
  rootType?: string
  rootId?: number
  stageId?: number
  customerId?: number
  name?: string
}
interface Conversion { customerId: number; contactId: number; opportunityId: number }
interface Operation { status: 'SUBMITTED' | 'COMMITTED'; path: string; at: string; result?: unknown }
interface Progress {
  lead?: BusinessRecord
  activity?: BusinessRecord
  conversion?: Conversion
  opportunity?: BusinessRecord
  operations: Record<string, Operation>
  captures: Record<string, { sha256: string; image: string }>
  status?: 'PASS'
}
interface UiState {
  schema: number
  project: string
  runId: string
  baseline: string
  locales: Partial<Record<DocsLocale, Progress>>
}
interface Teaching {
  schema: number
  project: string
  runId: string
  baseline: string
  users: Record<string, { username: string; roleCodes: string[] }>
  objects: { crmPhase: { status: string }; crmPipeline: { stages: { id: number; stageCode: string; stageName: string; probability: number }[] } }
}

const enabled = process.env.E2E_CRM_UI_WRITES === 'allow-owned-project'
const locales = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const hash = (bytes: string | Buffer) => createHash('sha256').update(bytes).digest('hex')
const testPath = 'omni-frontend/e2e-docs/flows/crm-teaching-ui.flows.spec.ts'
const testHash = hash(readFileSync(resolve('e2e-docs/flows/crm-teaching-ui.flows.spec.ts')))
let teaching: Teaching
let state: UiState
let tokens: Record<string, string>
let statePath: string
let evidencePath: string

/** 所有身份、原图和续做回执必须在仓库外。 */
function outside(value: string | undefined, name: string) {
  if (!value || !isAbsolute(value)) throw new Error(`${name} 需要绝对路径`)
  const child = relative(resolve(process.cwd(), '..'), resolve(value))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) throw new Error(`${name} 必须在仓库外`)
  return resolve(value)
}
function persist() {
  mkdirSync(dirname(statePath), { recursive: true })
  writeFileSync(`${statePath}.next`, JSON.stringify(state, null, 2))
  renameSync(`${statePath}.next`, statePath)
}
if (enabled) {
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('逐步操作仅允许本机 Docker external 环境')
  teaching = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_STATE_FILE, '教学回执'), 'utf8')) as Teaching
  tokens = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_TOKENS_FILE, '短期身份'), 'utf8')) as Record<string, string>
  statePath = outside(process.env.E2E_CRM_UI_STATE_FILE, '操作回执')
  evidencePath = outside(process.env.E2E_TEACHING_EVIDENCE_PATH, '截图清单')
  outside(process.env.E2E_DOCS_IMAGE_DIR, '原图目录')
  if (teaching.schema !== 1 || !/^omni-wiki-acceptance-[a-z0-9-]+$/.test(teaching.project)
    || teaching.objects.crmPhase.status !== 'PASS' || !/^WIKI-[A-Z0-9-]+$/.test(teaching.runId)
    || !/^[a-f0-9]{40}$/.test(teaching.baseline)) throw new Error('缺少明确归属且已通过领域验收的教学环境')
  const baseUrl = new URL(process.env.E2E_BASE_URL!)
  if (process.env.E2E_CRM_UI_PROJECT !== teaching.project || baseUrl.hostname !== 'omni-frontend' || baseUrl.port !== '3000') throw new Error('目标必须显式绑定本次 Docker 内网教学项目及真实前端端口')
  state = existsSync(statePath) ? JSON.parse(readFileSync(statePath, 'utf8')) as UiState
    : { schema: 1, project: teaching.project, runId: `${teaching.runId}-UI`, baseline: teaching.baseline, locales: {} }
  if (state.schema !== 1 || state.project !== teaching.project || state.runId !== `${teaching.runId}-UI`) throw new Error('拒绝覆盖其他演练回执')
  persist()
}

/** 使用产品本身的语言字典定位控件，避免把回退英文当成翻译成功。 */
function label(locale: DocsLocale, key: string): string {
  let value: unknown = locales[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown>)[part]
  if (typeof value !== 'string') throw new Error(`不存在的语言键 ${locale}/${key}`)
  return value
}
function escaped(value: string) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }
function formField(page: Page, dialog: Locator, locale: DocsLocale, key: string) {
  return dialog.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label').filter({ hasText: new RegExp(`^${escaped(label(locale, key))}[：:]?$`) }) })
}
function liveDialog(page: Page) { return page.locator('.el-dialog:visible').last() }
function now(offset = 0) {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(new Date(Date.now() + offset))
}

for (const locale of docsLocales) {
  test(`CRM 逐步真实 UI / ${locale}`, async ({ page }) => {
    test.skip(!enabled, '需要显式授权本次隔离教学项目写入及仓库外回执')
    test.setTimeout(180_000)
    const user = teaching.users.crmrep!
    const token = tokens.crmrepToken!
    if (!token || user.roleCodes.includes('SUPER_ADMIN')) throw new Error('必须使用普通销售短期身份')
    const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
    if (claims.exp * 1000 < Date.now() + 240_000) throw new Error('普通销售身份即将过期')
    const headers = { Authorization: `Bearer ${token}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! }
    const progress = state.locales[locale] ??= { operations: {}, captures: {} }
    persist()
    const prefix = `${state.runId}-${locale}`
    const leadName = `${prefix} Lead`, customerName = `${prefix} Customer`, activityName = `${prefix} Call`, opportunityName = `${prefix} Opportunity`
    const get = async <T>(path: string): Promise<T> => {
      const response = await page.request.get(path, { headers }).catch(() => {
        throw new Error(`真实 API 网络请求失败：${path}；短期身份与请求头不写入诊断输出`)
      })
      expect(response.status()).toBe(200)
      const body = await response.json() as { code: number; data: T }
      expect(body.code).toBe(200)
      return body.data
    }
    const goto = async (path: string) => { await page.goto(path); await waitForDocsPage(page, 'main') }
    const row = (text: string) => page.locator('.el-table__body-wrapper tr').filter({ hasText: text })
    const capture = async (step: string, flow: string, expected: string) => {
      if (progress.captures[step]) {
        expect(hash(readFileSync(progress.captures[step].image))).toBe(progress.captures[step].sha256)
        return
      }
      await waitForDocsPage(page, 'main')
      await captureDocsImage(page, locale, step)
      const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${step}.png`)
      const sha256 = hash(readFileSync(image))
      const records = existsSync(evidencePath) ? JSON.parse(readFileSync(evidencePath, 'utf8')) as Record<string, unknown>[] : []
      records.push({ schema: 1, step, module: 'crm', flow, locale, actor: 'crmrep', roles: user.roleCodes,
        route: new URL(page.url()).pathname, status: expected, viewport: { width: 1920, height: 1080 },
        project: state.project, runId: state.runId, baseline: state.baseline, image, sha256,
        capturedAt: new Date().toISOString(), mocks: false, visualReview: 'pending', test: testPath, testSha256: testHash })
      writeFileSync(`${evidencePath}.next`, JSON.stringify(records, null, 2)); renameSync(`${evidencePath}.next`, evidencePath)
      progress.captures[step] = { image, sha256 }; persist()
    }
    /** 发送前落盘；观察失败不重发，由下次执行先读真实记录核对结果。 */
    const submit = async <T>(key: string, path: string, click: () => Promise<unknown>): Promise<T> => {
      if (progress.operations[key]?.status === 'COMMITTED') return progress.operations[key].result as T
      if (progress.operations[key]?.status === 'SUBMITTED') throw new Error(`操作结果未知，必须先只读核对 ${key}`)
      progress.operations[key] = { status: 'SUBMITTED', path, at: new Date().toISOString() }; persist()
      const observed = page.waitForResponse(r => new URL(r.url()).pathname === path && r.request().method() === 'POST')
      await click()
      const response = await observed
      const body = await response.json() as { code: number; data: T }
      expect(response.status()).toBe(200); expect(body.code).toBe(200)
      progress.operations[key] = { status: 'COMMITTED', path, at: new Date().toISOString(), result: body.data }; persist()
      return body.data
    }
    await prepareDocsPage(page, { locale, token, username: user.username, viewport: { width: 1920, height: 1080 } })
    if (progress.status === 'PASS') {
      expect(progress.opportunity).toBeDefined()
      expect((await get<BusinessRecord>(`/api/crm/opportunity/${progress.opportunity!.id}`)).status).toBe('WON')
      expect(Object.keys(progress.captures)).toHaveLength(12)
      for (const record of Object.values(progress.captures)) expect(hash(readFileSync(record.image))).toBe(record.sha256)
      test.info().annotations.push({ type: 'reused-success', description: '已成功且未变化的 UI 链路只读复核，不重复写入' })
      return
    }
    if (!progress.lead) {
      const existing = (await get<{ records: BusinessRecord[] }>(`/api/crm/lead/list?page=1&size=100&keyword=${encodeURIComponent(leadName)}`)).records.filter(r => r.fullName === leadName)
      if (existing.length) {
        if (existing.length !== 1 || !progress.operations.createLead || existing[0]!.companyName !== customerName) throw new Error('同名记录缺少本次写前回执，拒绝覆盖')
        progress.lead = existing[0]; persist()
      } else {
        if (progress.operations.createLead) throw new Error('历史提交未找到结果，需人工核对而非自动重建')
        await goto('/admin/crm/lead')
        await page.getByRole('button', { name: label(locale, 'crmLead.create'), exact: true }).click()
        const dialog = liveDialog(page)
        await formField(page, dialog, locale, 'crmLead.name').locator('input').fill(leadName)
        await formField(page, dialog, locale, 'crmLead.company').locator('input').fill(customerName)
        await capture('crm-ui-lead-create-form', 'lead', 'Unique lead form, no write yet')
        progress.lead = await submit<BusinessRecord>('createLead', '/api/crm/lead', () => dialog.getByRole('button', { name: label(locale, 'common.save'), exact: true }).click())
        expect(progress.lead.status).toBe('NEW'); persist()
      }
    }
    let lead = await get<BusinessRecord>(`/api/crm/lead/${progress.lead.id}`)
    if (!progress.activity) {
      const existing = (await get<{ records: BusinessRecord[] }>(`/api/crm/activity/list?page=1&size=100&rootType=LEAD&rootId=${lead.id}`)).records.filter(r => r.subject === activityName)
      if (existing.length) {
        if (existing.length !== 1 || !progress.operations.createActivity) throw new Error('活动缺少本次归属回执')
        progress.activity = existing[0]; persist()
      } else {
        if (progress.operations.createActivity) throw new Error('活动提交结果未知，拒绝自动重建')
        expect(lead.status).toBe('NEW')
        await goto('/admin/crm/activity')
        await page.getByRole('button', { name: label(locale, 'crmActivity.create'), exact: true }).click()
        const dialog = liveDialog(page)
        await formField(page, dialog, locale, 'crmActivity.relatedRecord').locator('input').fill(String(lead.id))
        await formField(page, dialog, locale, 'crmActivity.subject').locator('input').fill(activityName)
        const planned = formField(page, dialog, locale, 'crmActivity.plannedStart').locator('input')
        await planned.fill(now(-3_600_000)); await planned.press('Tab'); await dialog.locator('.el-dialog__header').click()
        await capture('crm-ui-activity-plan-form', 'activity', 'LEAD/CALL/PLANNED form')
        progress.activity = await submit<BusinessRecord>('createActivity', '/api/crm/activity', () => dialog.getByRole('button', { name: label(locale, 'common.save'), exact: true }).click())
        expect(progress.activity.status).toBe('PLANNED'); persist()
        expect((await get<BusinessRecord>(`/api/crm/lead/${lead.id}`)).status).toBe('NEW')
        await expect(dialog).toBeHidden(); await expect(row(activityName)).toHaveCount(1)
        await capture('crm-ui-activity-planned-result', 'activity', 'PLANNED; lead remains NEW')
      }
    }
    let activity = await get<BusinessRecord>(`/api/crm/activity/${progress.activity.id}`)
    if (activity.status === 'PLANNED') {
      await goto('/admin/crm/activity')
      await row(activityName).getByRole('button', { name: label(locale, 'crmActivity.complete'), exact: true }).click()
      const dialog = liveDialog(page)
      await expect(formField(page, dialog, locale, 'crmActivity.completedTime').locator('input')).not.toHaveValue('')
      await capture('crm-ui-activity-complete-form', 'activity', 'Actual completion time before submit')
      activity = await submit<BusinessRecord>('completeActivity', `/api/crm/activity/${activity.id}/complete`, () => dialog.getByRole('button', { name: label(locale, 'crmActivity.confirmComplete'), exact: true }).click())
    }
    expect(activity.status).toBe('COMPLETED')
    progress.activity = activity; persist()
    lead = await get<BusinessRecord>(`/api/crm/lead/${lead.id}`)
    if (lead.status === 'FOLLOWING') {
      await goto('/admin/crm/lead')
      await row(leadName).getByRole('button', { name: label(locale, 'crmActivity.more'), exact: true }).click()
      await page.getByRole('menuitem', { name: label(locale, 'crmLead.qualify'), exact: true }).click()
      const box = page.locator('.el-message-box:visible')
      await expect(box).toBeVisible()
      lead = await submit<BusinessRecord>('qualify', `/api/crm/lead/${lead.id}/qualify`, () => box.locator('.el-message-box__btns .el-button--primary').click())
    }
    if (lead.status === 'QUALIFIED') {
      await goto('/admin/crm/lead'); await expect(row(leadName)).toHaveCount(1)
      await capture('crm-ui-lead-qualified', 'lead', 'QUALIFIED after completed follow-up')
      await row(leadName).getByRole('button', { name: label(locale, 'crmActivity.more'), exact: true }).click()
      await page.getByRole('menuitem', { name: label(locale, 'crmLead.convert'), exact: true }).click()
      const dialog = liveDialog(page)
      await expect(formField(page, dialog, locale, 'crmCustomerOverview.name').locator('input')).toHaveValue(customerName)
      await capture('crm-ui-convert-customer', 'lead-conversion', 'Step 1 CREATE customer')
      const next = () => dialog.getByRole('button', { name: label(locale, 'crmLead.next'), exact: true }).click()
      await next(); await expect(formField(page, dialog, locale, 'crmContact.name').locator('input')).toHaveValue(leadName)
      await capture('crm-ui-convert-contact', 'lead-conversion', 'Step 2 CREATE contact')
      await next()
      await formField(page, dialog, locale, 'crmOpportunity.name').locator('input').fill(opportunityName)
      await formField(page, dialog, locale, 'crmLead.estimatedAmount').locator('input').fill('12500')
      const closeDate = formField(page, dialog, locale, 'crmOpportunity.expectedCloseDate').locator('input')
      await closeDate.fill('2026-12-31'); await closeDate.press('Tab'); await dialog.locator('.el-dialog__header').click()
      await capture('crm-ui-convert-opportunity', 'lead-conversion', 'Step 3 CREATE opportunity amount 12500')
      await next(); await expect(dialog.locator('.el-descriptions')).toContainText(opportunityName)
      await capture('crm-ui-convert-confirm', 'lead-conversion', 'Step 4 verify before atomic conversion')
      progress.conversion = await submit<Conversion>('convert', `/api/crm/lead/${lead.id}/convert`, () => dialog.getByRole('button', { name: label(locale, 'crmLead.confirmConvert'), exact: true }).click())
      persist(); await expect(dialog).toBeHidden()
      lead = await get<BusinessRecord>(`/api/crm/lead/${lead.id}`)
      expect(lead.status).toBe('CONVERTED')
      await capture('crm-ui-lead-converted-result', 'lead-conversion', 'CONVERTED from the four real wizard steps')
    }
    expect(lead.status).toBe('CONVERTED')
    if (!progress.conversion && progress.operations.convert?.status === 'COMMITTED') progress.conversion = progress.operations.convert.result as Conversion
    if (!progress.conversion) throw new Error('转换结果未知，保留回执并只读核对，禁止重复创建')
    const conversion = progress.conversion
    const migrated = await get<BusinessRecord>(`/api/crm/activity/${activity.id}`)
    expect(migrated.rootType).toBe('CUSTOMER'); expect(migrated.rootId).toBe(conversion.customerId)
    let opportunity = await get<BusinessRecord>(`/api/crm/opportunity/${conversion.opportunityId}`)
    const stages = teaching.objects.crmPipeline.stages
    for (const code of ['PROPOSAL', 'WON']) {
      const stage = stages.find(s => s.stageCode === code)!
      const discovery = stages.find(s => s.stageCode === 'DISCOVERY')!
      if (opportunity.stageId === stage.id || opportunity.status === 'WON') continue
      if (code === 'PROPOSAL') expect(opportunity.stageId).toBe(discovery.id)
      await goto('/admin/crm/opportunity')
      await row(opportunityName).getByRole('button', { name: label(locale, 'crmOpportunity.moveStage'), exact: true }).click()
      const dialog = liveDialog(page)
      await formField(page, dialog, locale, 'crmOpportunity.targetStage').locator('.el-select').click()
      await page.getByRole('option', { name: `${stage.stageName} (${stage.probability}%)`, exact: true }).click()
      await formField(page, dialog, locale, 'crmOpportunity.changeReason').locator('textarea').fill(`${prefix} ${code}`)
      if (code === 'PROPOSAL') await capture('crm-ui-opportunity-stage-form', 'opportunity-stage', 'Target PROPOSAL and change reason before submit')
      opportunity = await submit<BusinessRecord>(`stage-${code}`, `/api/crm/opportunity/${opportunity.id}/stage`, () => dialog.getByRole('button', { name: label(locale, 'crmOpportunity.confirmMove'), exact: true }).click())
      expect(opportunity.stageId).toBe(stage.id)
      await expect(dialog).toBeHidden()
    }
    expect(opportunity.status).toBe('WON')
    expect((await get<BusinessRecord>(`/api/crm/customer/${conversion.customerId}`)).status).toBe('ACTIVE')
    await goto('/admin/crm/opportunity'); await expect(row(opportunityName)).toHaveCount(1)
    await capture('crm-ui-opportunity-won-result', 'opportunity-stage', 'WON; linked customer ACTIVE')
    progress.lead = lead; progress.opportunity = opportunity
    expect(Object.keys(progress.captures)).toHaveLength(12)
    progress.status = 'PASS'; persist()
  })
}
