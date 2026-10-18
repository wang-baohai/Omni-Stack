/** 已完成工作流教学案例的只读四语取证，检查拒绝、撤回、委托和加减签历史。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test } from '@playwright/test'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface Teaching {
  schema: number; project: string; runId: string; baseline: string
  users: Record<string, { id: number; username: string; roleCodes: string[] }>
  objects: { workflowPhase: { status: string; cases: Record<string, { pid: string }> } }
}
interface Entry { result: string; assigneeId: string }
const enabled = process.env.E2E_WORKFLOW_TEACHING === 'read-only'
const dictionaries = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
let teaching: Teaching, tokens: Record<string, string>, evidencePath: string
const hash = (input: string | Buffer) => createHash('sha256').update(input).digest('hex')
const sourcePath = 'omni-frontend/e2e-docs/flows/workflow-teaching.flows.spec.ts'
const sourceHash = hash(readFileSync('e2e-docs/flows/workflow-teaching.flows.spec.ts'))

/** 原始证据和身份仅允许存放于仓库外。 */
function outside(value: string | undefined, name: string) {
  if (!value || !isAbsolute(value)) throw new Error(`${name} 必须为绝对路径`)
  const child = relative(resolve(process.cwd(), '..'), resolve(value))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) throw new Error(`${name} 必须在仓库外`)
  return resolve(value)
}
if (enabled) {
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('仅允许本机 Docker external 环境')
  teaching = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_STATE_FILE, '教学回执'), 'utf8')) as Teaching
  tokens = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_TOKENS_FILE, '短期身份'), 'utf8')) as Record<string, string>
  evidencePath = outside(process.env.E2E_TEACHING_EVIDENCE_PATH, '截图清单')
  outside(process.env.E2E_DOCS_IMAGE_DIR, '原图目录')
  const target = new URL(process.env.E2E_BASE_URL!)
  if (teaching.schema !== 1 || !/^omni-wiki-acceptance-[a-z0-9-]+$/.test(teaching.project)
    || !/^WIKI-[A-Z0-9-]+$/.test(teaching.runId) || teaching.objects.workflowPhase.status !== 'PASS'
    || target.hostname !== 'omni-frontend' || target.port !== '3000') throw new Error('需要已完成、明确归属的工作流案例')
  for (const name of ['E2E_WORKFLOW_RUNTIME_IMAGE', 'E2E_WORKFLOW_BACKEND_IMAGE']) {
    if (!/^sha256:[a-f0-9]{64}$/.test(process.env[name] || '')) throw new Error(`必须核对 ${name}`)
  }
}
function label(locale: DocsLocale, key: string) {
  let value: unknown = dictionaries[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown>)[part]
  if (typeof value !== 'string') throw new Error(`缺少语言键 ${locale}/${key}`)
  return value
}
function identity(actor: string) {
  const user = teaching.users[actor]!, token = tokens[`${actor}Token`]!
  if (user.roleCodes.includes('SUPER_ADMIN')) throw new Error('不能使用超级管理员取代普通业务身份')
  const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
  if (!claims.exp || claims.exp * 1000 < Date.now() + 60_000) throw new Error('教学身份即将过期')
  return { user, token, headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! } }
}
const scenes = [
  { id: 'reject', image: 'workflow-teaching-reject-progress', progress: true, status: 2 },
  { id: 'reject', image: 'workflow-teaching-reject-records', status: 2 },
  { id: 'terminate', image: 'workflow-teaching-withdraw-records', status: 0 },
  { id: 'delegate', image: 'workflow-teaching-delegate-records', status: 2 },
  { id: 'add-signer', image: 'workflow-teaching-add-signer-records', status: 2 },
  { id: 'remove-signer', image: 'workflow-teaching-remove-signer-records', status: 2 },
]

for (const locale of docsLocales) test.describe(`Workflow 真实历史 ${locale}`, () => {
  test.skip(!enabled, '需要仓库外已完成案例与当前短期身份')
  for (const scene of scenes) test(scene.image, async ({ page }) => {
    const manager = identity('manager'), starter = identity('requester')
    const pid = teaching.objects.workflowPhase.cases[scene.id]?.pid
    expect(pid).toBeTruthy()
    const title = `${teaching.runId}-WFAPI ${scene.id}`
    const get = async <T>(path: string): Promise<T> => {
      const response = await page.request.get(path, { headers: starter.headers }).catch(() => { throw new Error(`只读 API 网络失败 ${path}，不输出身份请求头`) })
      expect(response.status()).toBe(200)
      const result = await response.json() as { code: number; data: T }
      expect(result.code).toBe(200)
      return result.data
    }
    const instances = await get<{ records: Array<{ processInstanceId: string; status: number }> }>(`/api/workflow/process-instance/my-initiated?page=1&size=100&title=${encodeURIComponent(title)}`)
    expect(instances.records.find(x => x.processInstanceId === pid)?.status).toBe(scene.status)
    const entries = await get<Entry[]>(`/api/workflow/process-instance/${pid}/approval-records`)
    if (scene.id === 'reject') {
      expect(entries.filter(x => x.result === 'rejected')).toHaveLength(1)
      expect(entries.filter(x => x.result === 'auto-approved')).toHaveLength(2)
      const progress = await get<{ completedActivities: Array<{ activityId: string }>; activeActivityIds: string[] }>(`/api/workflow/process-instance/${pid}/progress`)
      expect(progress.activeActivityIds).toHaveLength(0)
      expect(progress.completedActivities.some(x => x.activityId === 'rejected-end')).toBe(true)
    } else if (scene.id === 'terminate') expect(entries.filter(x => x.result === 'cancelled')).toHaveLength(3)
    else {
      expect(entries.filter(x => x.result === 'approved')).toHaveLength(scene.id === 'add-signer' ? 4 : 3)
      if (scene.id === 'delegate' || scene.id === 'add-signer') expect(entries.some(x => String(x.assigneeId) === String(teaching.users.crmmanager!.id))).toBe(true)
    }
    await prepareDocsPage(page, { locale, token: manager.token, username: manager.user.username, viewport: { width: 1920, height: 1080 } })
    await page.goto('/admin/workflow/instance'); await waitForDocsPage(page, 'main')
    await page.getByPlaceholder(label(locale, 'workflow.title'), { exact: true }).fill(title)
    await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click()
    const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: title }); await expect(row).toHaveCount(1)
    await row.getByRole('button', { name: label(locale, scene.progress ? 'workflow.processProgress' : 'workflow.approvalRecords'), exact: true }).click()
    const modal = page.locator('.el-dialog:visible').last(); await expect(modal.locator('.el-loading-mask:visible')).toHaveCount(0)
    if (scene.progress) await expect(modal.locator('.bpmn-viewer-wrap .djs-element').first()).toBeVisible()
    else await expect(modal.locator('.el-table__body-wrapper tr')).toHaveCount(entries.length)
    await waitForDocsPage(page, 'main'); await captureDocsImage(page, locale, scene.image)
    const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${scene.image}.png`)
    const evidence = existsSync(evidencePath) ? JSON.parse(readFileSync(evidencePath, 'utf8')) as Record<string, unknown>[] : []
    evidence.push({ schema: 1, step: scene.image, module: 'workflow', flow: scene.id, locale, actor: 'manager', roles: manager.user.roleCodes,
      project: teaching.project, runId: teaching.runId, baseline: teaching.baseline, route: '/admin/workflow/instance', processInstanceId: pid,
      claim: `Read-only actual ${scene.id} result after committed ordinary API actions; generic status ${scene.status} is not an approval decision`,
      image, sha256: hash(readFileSync(image)), capturedAt: new Date().toISOString(), viewport: { width: 1920, height: 1080 }, mocks: false, visualReview: 'pending',
      test: sourcePath, testSha256: sourceHash, runtimeImage: process.env.E2E_WORKFLOW_RUNTIME_IMAGE, backendImage: process.env.E2E_WORKFLOW_BACKEND_IMAGE })
    mkdirSync(dirname(evidencePath), { recursive: true }); writeFileSync(`${evidencePath}.next`, JSON.stringify(evidence, null, 2)); renameSync(`${evidencePath}.next`, evidencePath)
  })
})
