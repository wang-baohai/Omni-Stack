/** CRM 隔离教学结果的只读验证与四语言取证，不创建、推进或清理业务记录。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test } from '@playwright/test'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../fixtures/docs-page'

interface TeachingState {
  schema: number
  project: string
  runId: string
  baseline: string
  users: Record<string, { username: string; roleCodes: string[] }>
  objects: Record<string, Record<string, unknown>>
}
interface Scene {
  image: string
  object: string
  endpoint: string
  route: string
  status: string | number
  label: string
  flow: string
  idField?: string
  button?: RegExp
}

/** 教学回执、短期身份和原始证据均须显式存放在仓库外。 */
function outside(path: string | undefined, name: string): string {
  if (!path || !isAbsolute(path)) throw new Error(`${name} 必须为仓库外绝对路径`)
  const child = relative(resolve(process.cwd(), '..'), resolve(path))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) throw new Error(`${name} 不得位于仓库内`)
  return resolve(path)
}

const enabled = Boolean(process.env.E2E_TEACHING_STATE_FILE || process.env.E2E_TEACHING_TOKENS_FILE)
let state: TeachingState
let tokens: Record<string, string>
if (enabled) {
  if (process.env.E2E_RUNTIME_MODE !== 'external' || !existsSync('/.dockerenv')) throw new Error('CRM 取证必须在本机 Docker 内使用 external 模式')
  state = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_STATE_FILE, 'E2E_TEACHING_STATE_FILE'), 'utf8')) as TeachingState
  tokens = JSON.parse(readFileSync(outside(process.env.E2E_TEACHING_TOKENS_FILE, 'E2E_TEACHING_TOKENS_FILE'), 'utf8')) as Record<string, string>
  if (state.schema !== 1 || !/^omni-(?:e2e|wiki-acceptance)-[a-z0-9-]+$/.test(state.project)
      || !/^WIKI-[A-Z0-9-]+$/.test(state.runId) || !/^[a-f0-9]{40}$/.test(state.baseline) || state.objects.crmPhase?.status !== 'PASS') {
    throw new Error('缺少已完成、明确归属的 CRM 教学回执')
  }
  outside(process.env.E2E_TEACHING_EVIDENCE_PATH, 'E2E_TEACHING_EVIDENCE_PATH')
  outside(process.env.E2E_DOCS_IMAGE_DIR, 'E2E_DOCS_IMAGE_DIR')
}

const scenes: Scene[] = [
  { image: 'crm-teaching-lead-converted', object: 'crmLead', endpoint: '/api/crm/lead', route: '/admin/crm/lead', status: 'CONVERTED', label: 'fullName', flow: 'lead-conversion' },
  { image: 'crm-teaching-lead-reopened', object: 'crmLeadNegative', endpoint: '/api/crm/lead', route: '/admin/crm/lead', status: 'FOLLOWING', label: 'fullName', flow: 'lead' },
  { image: 'crm-teaching-activity-completed', object: 'crmActivity', endpoint: '/api/crm/activity', route: '/admin/crm/activity', status: 'COMPLETED', label: 'subject', flow: 'activity' },
  { image: 'crm-teaching-customer-active-360', object: 'crmCustomerActive', endpoint: '/api/crm/customer', route: '/admin/crm/customer', status: 'ACTIVE', label: 'name', flow: 'customer', button: /360/ },
  { image: 'crm-teaching-contact', object: 'crmConversion', idField: 'contactId', endpoint: '/api/crm/contact', route: '/admin/crm/contact', status: 1, label: 'name', flow: 'contact' },
  { image: 'crm-teaching-opportunity-won', object: 'crmOpportunityWon', endpoint: '/api/crm/opportunity', route: '/admin/crm/opportunity', status: 'WON', label: 'name', flow: 'opportunity-stage' },
  { image: 'crm-teaching-opportunity-reopened-history', object: 'crmOpportunityReopened', endpoint: '/api/crm/opportunity', route: '/admin/crm/opportunity', status: 'OPEN', label: 'name', flow: 'opportunity-stage', button: /历史|History|履歴|이력/ },
]

/** 仅使用库中真实普通角色签发的当前短期身份。 */
function identity(actor: string) {
  const user = state.users[actor]
  const token = tokens[`${actor}Token`]
  if (!user || !token || user.roleCodes.includes('SUPER_ADMIN')) throw new Error('缺少普通 CRM 教学身份')
  const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
  if (!claims.exp || claims.exp * 1000 < Date.now() + 60_000) throw new Error('教学身份已过期')
  return { user, token, headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! } }
}

/** 登记真实采集结果；视觉审核作为后续独立步骤。 */
function record(image: string, locale: string, actor: string, route: string, flow: string, status: unknown) {
  const imagePath = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${image}.png`)
  const path = outside(process.env.E2E_TEACHING_EVIDENCE_PATH, 'E2E_TEACHING_EVIDENCE_PATH')
  const entries = existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) as unknown[] : []
  entries.push({ schema: 1, step: image, module: 'crm', flow, locale, actor, roles: state.users[actor]!.roleCodes,
    route, status, viewport: { width: 1920, height: 1080 }, project: state.project, runId: state.runId, baseline: state.baseline,
    image: imagePath, sha256: createHash('sha256').update(readFileSync(imagePath)).digest('hex'), capturedAt: new Date().toISOString(),
    mocks: false, visualReview: 'pending', test: 'omni-frontend/e2e-docs/flows/crm-teaching.flows.spec.ts',
    testSha256: createHash('sha256').update(readFileSync(resolve('e2e-docs/flows/crm-teaching.flows.spec.ts'))).digest('hex') })
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(`${path}.next`, JSON.stringify(entries, null, 2))
  renameSync(`${path}.next`, path)
}

for (const locale of docsLocales) {
  test.describe(`CRM 真实教学结果 ${locale}`, () => {
    test.skip(!enabled, '需要显式的仓库外教学回执与短期身份')
    for (const scene of scenes) {
      test(scene.image, async ({ page }) => {
        const { user, token, headers } = identity('crmrep')
        const id = state.objects[scene.object]?.[scene.idField || 'id']
        if (!id) throw new Error(`教学回执缺少 ${scene.object} ID`)
        const response = await page.request.get(`${scene.endpoint}/${String(id)}`, { headers })
        expect(response.status()).toBe(200)
        const result = await response.json() as { code: number; data: Record<string, unknown> }
        expect(result.code).toBe(200)
        expect(String(result.data.id)).toBe(String(id))
        expect(result.data.status).toBe(scene.status)
        if (scene.flow === 'activity') {
          expect(result.data.rootType).toBe('CUSTOMER')
          expect(String(result.data.rootId)).toBe(String(state.objects.crmConversion!.customerId))
        }
        await prepareDocsPage(page, { locale, token, username: user.username, viewport: { width: 1920, height: 1080 } })
        await page.goto(scene.route)
        await waitForDocsPage(page, 'main')
        const label = String(result.data[scene.label])
        const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: label })
        await expect(row).toHaveCount(1)
        if (['lead', 'lead-conversion', 'activity', 'opportunity-stage'].includes(scene.flow)) {
          const tag = row.locator('.el-tag').first()
          await expect(tag).toBeVisible()
          await expect(tag).not.toBeEmpty()
          const bounds = await tag.boundingBox()
          const actions = await row.locator('td').last().boundingBox()
          expect(bounds && actions && bounds.x + bounds.width <= actions.x).toBeTruthy()
        }
        if (scene.button) {
          const opportunityResponse = scene.flow === 'customer'
            ? page.waitForResponse((response) => response.url().includes('/api/crm/opportunity/list'))
            : null
          await row.getByRole('button', { name: scene.button }).first().click()
          const drawer = page.locator('.el-drawer').filter({ visible: true }).first()
          await expect(drawer).toBeVisible()
          if (scene.image.endsWith('-history')) await expect(drawer).toContainText('REOPEN')
          else {
            await expect(drawer).toContainText(label)
            const response = await opportunityResponse!
            expect(response.status()).toBe(200)
            const result = await response.json() as { code: number; data: { records: Array<{ id: unknown; status: string }> } }
            expect(result.code).toBe(200)
            expect(result.data.records.length).toBeGreaterThan(0)
            expect(result.data.records.every((item) => item.status === 'OPEN')).toBe(true)
            expect(result.data.records.some((item) => String(item.id) === String(state.objects.crmOpportunityWon!.id))).toBe(false)
            await expect(drawer).not.toContainText(String(state.objects.crmOpportunityWon!.name))
            await expect(drawer).toContainText(String(state.objects.crmOpportunityReopened!.name))
          }
        }
        await expect(page).not.toHaveURL(/\/login|menu-load-error/)
        await waitForDocsPage(page, 'main')
        await captureDocsImage(page, locale, scene.image)
        record(scene.image, locale, 'crmrep', scene.route, scene.flow, scene.status)
      })
    }
    test('crm-teaching-outside-scope', async ({ page }) => {
      const outsideUser = identity('outsider')
      const lead = state.objects.crmLead!
      const response = await page.request.get(`/api/crm/lead/${String(lead.id)}`, { headers: outsideUser.headers })
      expect((await response.json() as { code: number }).code).toBe(404)
      const list = await page.request.get(`/api/crm/lead/list?page=1&size=100&keyword=${encodeURIComponent(state.runId)}`, { headers: outsideUser.headers })
      expect(list.status()).toBe(200)
      const result = await list.json() as { code: number; data: { records: Array<{ id: unknown }> } }
      expect(result.code).toBe(200)
      expect(result.data.records.some((row) => String(row.id) === String(lead.id))).toBe(false)
      const manager = await page.request.get(`/api/crm/lead/${String(lead.id)}`, { headers: identity('crmmanager').headers })
      expect(manager.status()).toBe(200)
      expect((await manager.json() as { code: number }).code).toBe(200)
      await prepareDocsPage(page, { locale, token: outsideUser.token, username: outsideUser.user.username, viewport: { width: 1920, height: 1080 } })
      await page.goto('/admin/crm/lead')
      await waitForDocsPage(page, 'main')
      await expect(page.locator('.el-table')).toBeVisible()
      await expect(page.locator('.el-table')).not.toContainText(String(lead.fullName))
      await expect(page).not.toHaveURL(/\/login|\/403|menu-load-error/)
      await captureDocsImage(page, locale, 'crm-teaching-outside-scope')
      record('crm-teaching-outside-scope', locale, 'outsider', '/admin/crm/lead', 'lead', 'OUTSIDE_SCOPE')
    })
  })
}
