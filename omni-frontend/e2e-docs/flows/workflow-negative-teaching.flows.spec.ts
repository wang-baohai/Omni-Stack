/** 工作流负向教学：真实校验、发布拒绝与普通身份403，保留已存草稿且不重复成功写入。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface Model { id: number; modelKey: string; modelName: string; createBy: string; currentDraftVersionId: number; currentPublishedVersionId: number | null }
interface Version { id: number; status: string; bpmnXml: string; deploymentId: string | null; processDefinitionId: string | null }
interface Teaching { schema: number; project: string; runId: string; baseline: string; users: Record<string, { id: number; username: string; roleCodes: string[] }>; objects: { workflowPhase: { status: string; invalidModel: Model } } }
interface Progress { captures: Record<string, { image: string; sha256: string }>; publication?: { status: 'SUBMITTED' | 'REJECTED_CONFIRMED'; at: string; httpStatus?: number; businessCode?: number; message?: string }; snapshot?: unknown; forbidden?: unknown; validation?: unknown; status?: 'PASS' }
interface Journal { schema: number; project: string; baseline: string; locales: Partial<Record<DocsLocale, Progress>> }
const enabled = process.env.E2E_WORKFLOW_NEGATIVE === 'allow-owned-rejections'
const dictionaries = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const sourcePath = 'omni-frontend/e2e-docs/flows/workflow-negative-teaching.flows.spec.ts'
const hash = (bytes: string | Buffer) => createHash('sha256').update(bytes).digest('hex')
const sourceHash = hash(readFileSync('e2e-docs/flows/workflow-negative-teaching.flows.spec.ts'))
const json = <T>(file: string): T => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, '')) as T
let teaching: Teaching, tokens: Record<string, string>, journal: Journal, journalPath: string, evidencePath: string
function outside(value: string | undefined) {
  if (!value || !isAbsolute(value)) throw new Error('证据路径必须是仓库外绝对路径')
  const child = relative(resolve(process.cwd(), '..'), resolve(value))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) throw new Error('拒绝将身份或原始证据写入仓库')
  return resolve(value)
}
function persist() {
  mkdirSync(dirname(journalPath), { recursive: true })
  writeFileSync(`${journalPath}.next`, JSON.stringify(journal, null, 2)); renameSync(`${journalPath}.next`, journalPath)
}
if (enabled) {
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('仅允许本机Docker真实外部应用')
  teaching = json<Teaching>(outside(process.env.E2E_TEACHING_STATE_FILE))
  tokens = json<Record<string, string>>(outside(process.env.E2E_TEACHING_TOKENS_FILE))
  journalPath = outside(process.env.E2E_WORKFLOW_NEGATIVE_STATE_FILE)
  evidencePath = outside(process.env.E2E_TEACHING_EVIDENCE_PATH)
  outside(process.env.E2E_DOCS_IMAGE_DIR)
  const url = new URL(process.env.E2E_BASE_URL!)
  if (teaching.schema !== 1 || teaching.project !== process.env.E2E_WORKFLOW_UI_PROJECT || teaching.project !== 'omni-wiki-acceptance-20261009'
    || url.hostname !== 'omni-frontend' || url.port !== '3000' || teaching.objects.workflowPhase.status !== 'PASS') throw new Error('目标项目或既有案例不符')
  for (const key of ['E2E_WORKFLOW_RUNTIME_IMAGE', 'E2E_WORKFLOW_BACKEND_IMAGE']) if (!/^sha256:[a-f0-9]{64}$/.test(process.env[key] || '')) throw new Error('缺少已核对运行镜像')
  journal = existsSync(journalPath) ? json<Journal>(journalPath) : { schema: 1, project: teaching.project, baseline: teaching.baseline, locales: {} }
  if (journal.project !== teaching.project || journal.baseline !== teaching.baseline) throw new Error('不覆盖其他案例回执')
}
function label(locale: DocsLocale, key: string) {
  let value: unknown = dictionaries[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown>)[part]
  if (typeof value !== 'string') throw new Error(`缺少语言键 ${locale}/${key}`)
  return value
}
function identity(actor: string) {
  const user = teaching.users[actor]!, token = tokens[`${actor}Token`]!
  if (user.roleCodes.includes('SUPER_ADMIN')) throw new Error('负向教学必须使用普通业务身份')
  const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number; sub: string; username: string; tenant_id: number; scope: string[] }
  if (claims.exp * 1000 < Date.now() + 60_000) throw new Error('短期身份即将过期')
  if (String(user.id) !== claims.sub || user.username !== claims.username || claims.tenant_id !== 1 || !Array.isArray(claims.scope)) throw new Error('教学身份与令牌不一致')
  return { user, token, permissions: claims.scope, headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': '1' } }
}
for (const locale of docsLocales) test(`Workflow真实负向边界 / ${locale}`, async ({ page }) => {
  test.skip(!enabled, '需要明确授权的隔离项目、当前身份与仓库外回执')
  const manager = identity('manager'), outsider = identity('outsider'), model = teaching.objects.workflowPhase.invalidModel
  expect(manager.permissions).toContain('workflow:model:list'); expect(outsider.permissions).not.toContain('workflow:model:list')
  expect(model.createBy).toBe(manager.user.username); expect(model.modelKey).toBe('Wiki20261009A-InvalidKey')
  journal.locales[locale] ??= { captures: {} }; const p = journal.locales[locale]!; persist()
  const get = async <T>(path: string): Promise<T> => {
    const response = await page.request.get(path, { headers: manager.headers }).catch(() => { throw new Error(`只读请求失败 ${path}`) })
    expect(response.status()).toBe(200); const body = await response.json() as { code: number; data: T }; expect(body.code).toBe(200); return body.data
  }
  const snapshot = async () => {
    const current = await get<Model>(`/api/workflow/model/${model.id}`)
    const draft = await get<Version>(`/api/workflow/model/version/${current.currentDraftVersionId}`)
    const versions = await get<Version[]>(`/api/workflow/model/${model.id}/versions`)
    expect(current.currentPublishedVersionId).toBeNull(); expect(draft.status).toBe('DRAFT')
    expect(draft.deploymentId).toBeNull(); expect(draft.processDefinitionId).toBeNull(); expect(versions).toHaveLength(1)
    return { modelId: current.id, modelKey: current.modelKey, draftId: draft.id, draftStatus: draft.status, xmlSha256: hash(draft.bpmnXml), publishedId: current.currentPublishedVersionId, versions: versions.length, deploymentId: draft.deploymentId, processDefinitionId: draft.processDefinitionId }
  }
  const before = await snapshot(); if (p.snapshot) expect(before).toEqual(p.snapshot)
  p.snapshot = before; persist()
  if (p.status === 'PASS') { expect(Object.keys(p.captures)).toHaveLength(3); for (const x of Object.values(p.captures)) expect(hash(readFileSync(x.image))).toBe(x.sha256); return }
  if (p.publication?.status === 'SUBMITTED') throw new Error('发布结果未知，必须只读对账后处理，不重复提交')
  const capture = async (step: string, flow: string, actor: string, claim: string, capturePage: Page = page) => {
    if (p.captures[step]) { expect(hash(readFileSync(p.captures[step].image))).toBe(p.captures[step].sha256); return }
    await waitForDocsPage(capturePage, 'main'); await captureDocsImage(capturePage, locale, step)
    const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${step}.png`), sha256 = hash(readFileSync(image))
    const records = existsSync(evidencePath) ? json<Record<string, unknown>[]>(evidencePath) : []
    records.push({ schema: 1, step, module: 'workflow', flow, locale, actor, project: teaching.project, runId: `${teaching.runId}-WFNEG`, baseline: teaching.baseline, route: new URL(capturePage.url()).pathname, image, sha256, capturedAt: new Date().toISOString(), mocks: false, visualReview: 'pending', claim, test: sourcePath, testSha256: sourceHash, runtimeImage: process.env.E2E_WORKFLOW_RUNTIME_IMAGE, backendImage: process.env.E2E_WORKFLOW_BACKEND_IMAGE, viewport: { width: 1920, height: 1080 } })
    writeFileSync(`${evidencePath}.next`, JSON.stringify(records, null, 2)); renameSync(`${evidencePath}.next`, evidencePath)
    p.captures[step] = { image, sha256 }; persist()
  }
  await prepareDocsPage(page, { locale, token: manager.token, username: manager.user.username, viewport: { width: 1920, height: 1080 } })
  await page.goto('/admin/workflow/model'); await waitForDocsPage(page, 'main')
  await page.getByPlaceholder(label(locale, 'workflow.modelSearchPlaceholder'), { exact: true }).fill(model.modelKey)
  await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click()
  const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: model.modelKey }); await expect(row).toHaveCount(1)
  const validationResponse = page.waitForResponse(r => new URL(r.url()).pathname === `/api/workflow/model/${model.id}/validate`)
  await row.getByRole('button', { name: label(locale, 'workflow.validate'), exact: true }).click()
  const validation = await (await validationResponse).json() as { code: number; data: { valid: boolean; errors: string[] } }
  expect(validation.code).toBe(200); expect(validation.data.valid).toBe(false); expect(validation.data.errors.join(' ')).toContain(model.modelKey)
  p.validation = validation.data; persist()
  const dialog = page.locator('.el-dialog:visible').last()
  await expect(dialog.getByText(label(locale, 'workflow.validationFailed'), { exact: true })).toBeVisible()
  await expect(dialog.locator('.error-item').first()).toBeVisible()
  await capture('workflow-negative-validation', 'validation', 'manager', 'Saved draft process ID mismatch is reported by the real server validation dialog; no designer save')
  await dialog.getByRole('button', { name: label(locale, 'common.back'), exact: true }).click()
  if (!p.publication) {
    await row.getByRole('button', { name: label(locale, 'workflow.publish'), exact: true }).click()
    await expect(page.locator('.el-message-box')).toBeVisible()
    p.publication = { status: 'SUBMITTED', at: new Date().toISOString() }; persist()
    const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === `/api/workflow/model/${model.id}/publish` && r.request().method() === 'POST')
    await page.locator('.el-message-box__btns .el-button--primary').click()
    const response = await responsePromise, body = await response.json() as { code: number; message: string }
    expect(body.code).toBe(500)
    p.publication = { status: 'REJECTED_CONFIRMED', at: new Date().toISOString(), httpStatus: response.status(), businessCode: body.code, message: body.message }; persist()
    expect(body.message).toContain(model.modelKey)
    await expect(page.locator('.el-message--error').first()).toBeVisible()
    await capture('workflow-negative-publish', 'publish', 'manager', 'Real publish request is rejected with business500; error is visible; later read-only assertions confirm one unchanged DRAFT and no publication/deployment association')
  } else expect(p.captures['workflow-negative-publish']).toBeTruthy()
  expect(await snapshot()).toEqual(before)
  const denied = await page.request.get('/api/workflow/model/list?page=1&size=10', { headers: outsider.headers }).catch(() => { throw new Error('普通身份权限请求失败') })
  const deniedBody = await denied.json() as { code: number }; expect(deniedBody.code).toBe(403)
  p.forbidden = { httpStatus: denied.status(), businessCode: deniedBody.code, actorId: outsider.user.id, actor: 'outsider', roleCodes: outsider.user.roleCodes, permission: 'workflow:model:list', permissionPresent: false }; persist()
  // 新页面只注册一次身份初始化脚本，避免同页切换身份依赖多个脚本的执行顺序。
  const deniedPage = await page.context().newPage()
  try {
    await prepareDocsPage(deniedPage, { locale, token: outsider.token, username: outsider.user.username, viewport: { width: 1920, height: 1080 } })
    await deniedPage.goto('/admin/workflow/model'); await waitForDocsPage(deniedPage, 'main')
    await expect(deniedPage.locator('.status-page .el-result__title')).toHaveText(`403 ${label(locale, 'errorPage.forbiddenTitle')}`)
    await expect(deniedPage.locator('header, .navbar, .app-header').first()).toContainText(outsider.user.username)
    await capture('workflow-negative-model-forbidden', 'model-access-denied', 'outsider', 'Authenticated ordinary employee without workflow:model:list sees model-management403; separate live model-list API also returns403, not an expired-login simulation', deniedPage)
  } finally { await deniedPage.close() }
  expect(await snapshot()).toEqual(before); p.status = 'PASS'; persist()
})
