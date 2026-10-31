/** 采购经理范围取证：读取本部门与其他部门的既有请购，不创建或修改业务数据。 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import { expect, test } from '@playwright/test'
import zh from '../../src/locales/zh-CN'
import en from '../../src/locales/en-US'
import ja from '../../src/locales/ja-JP'
import ko from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface Identity { id: number; username: string; roleCodes: string[]; primaryUnitId: number }
interface Requisition { id: number; requisitionNo: string; status: string; requesterUnitId: number }
interface TeachingState {
  project: string; runId: string; baseline: string
  users: Record<string, Identity>
  objects: { requisition: Requisition; procurementScope20261012: { outsideRequest: Requisition } }
}
interface Capture { name: string; locale: DocsLocale; sha256: string; [key: string]: unknown }
const enabled = Boolean(process.env.E2E_TEACHING_STATE_FILE || process.env.E2E_TEACHING_TOKENS_FILE)
const project = 'omni-stack'
const viewport = { width: 1920, height: 1080 }
const route = '/admin/procurement/requisition'
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const sha = (value: string | Buffer) => createHash('sha256').update(value).digest('hex')
/** 所有运行状态、短期身份与原图必须在仓库外。 */
function outside(name: string) {
  const path = process.env[name]
  if (!path || !isAbsolute(path)) throw new Error(name + ' requires an absolute private path')
  const child = relative(resolve(process.cwd(), '..'), resolve(path))
  if (!child.startsWith('..' + sep)) throw new Error(name + ' must be outside the repository')
  return resolve(path)
}
let teaching: TeachingState
let tokens: Record<string, string>
let runtime: { project: string; containers: { service: string; image: string; ports: number }[] }
let registry: { schema: number; project: string; baseline: string; runtime: unknown; sources: unknown; captures: Capture[]; observations: unknown[]; [key: string]: unknown }
let registryPath: string
if (enabled) {
  if (!existsSync('/.dockerenv') || process.env.E2E_RUNTIME_MODE !== 'external') throw new Error('Local external Docker required')
  teaching = JSON.parse(readFileSync(outside('E2E_TEACHING_STATE_FILE'), 'utf8').replace(/^\uFEFF/, '')) as TeachingState
  tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8')) as Record<string, string>
  runtime = JSON.parse(readFileSync(outside('E2E_SCOPE_RUNTIME_FILE'), 'utf8').replace(/^\uFEFF/, '')) as typeof runtime
  expect(teaching.project).toBe(project)
  expect(runtime.project).toBe(project)
  expect(runtime.containers).toHaveLength(24)
  expect(runtime.containers.every(item => item.ports === 0)).toBe(true)
  expect(teaching.users.manager!.roleCodes).toContain('PROCUREMENT_MANAGER')
  expect(teaching.users.manager!.primaryUnitId).toBe(teaching.users.requester!.primaryUnitId)
  expect(teaching.users.procurementOutside!.roleCodes).toContain('PROCUREMENT_STAFF')
  expect(teaching.users.procurementOutside!.primaryUnitId).not.toBe(teaching.users.manager!.primaryUnitId)
  expect(teaching.objects.procurementScope20261012.outsideRequest.id).toBeGreaterThan(0)
  outside('E2E_DOCS_IMAGE_DIR')
  registryPath = outside('E2E_SCOPE_REGISTRY_FILE')
  const paths = ['omni-frontend/e2e-docs/flows/procurement-scope.flows.spec.ts',
    'omni-frontend/e2e-docs/fixtures/docs-page.ts', 'omni-frontend/src/views/procurement/requisition/index.vue',
    'scripts/sql/seed/auth.sql', 'docs/design/procurement-design.md']
  const sources = paths.map(path => ({ path, sha256: sha(readFileSync(resolve('..', path))) }))
  registry = existsSync(registryPath) ? JSON.parse(readFileSync(registryPath, 'utf8')) as typeof registry
    : { schema: 1, project, baseline: teaching.baseline, runtime, sources, captures: [], observations: [],
      started_at: new Date().toISOString(), role_meaning: 'PROCUREMENT_MANAGER / DEPT_AND_BELOW; procurement-admin-scope is a coverage scenario identifier, not a role code.' }
}
function persist() {
  registry.updated_at = new Date().toISOString()
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath + '.next', JSON.stringify(registry, null, 2) + '\n')
  renameSync(registryPath + '.next', registryPath)
}
function token(actor: string) {
  const value = tokens[actor + 'Token']!
  const claims = JSON.parse(Buffer.from(value.split('.')[1]!, 'base64url').toString()) as { exp: number; roles: string[] }
  expect(claims.exp * 1000).toBeGreaterThan(Date.now() + 60_000)
  expect(claims.roles).not.toContain('SUPER_ADMIN')
  if (actor === 'manager') expect(claims.roles).toContain('PROCUREMENT_MANAGER')
  return value
}
function label(locale: DocsLocale, path: string) {
  let value: unknown = dictionaries[locale]
  for (const key of path.split('.')) value = (value as Record<string, unknown>)?.[key]
  if (typeof value !== 'string' || !value) throw new Error('Missing label ' + locale + ' ' + path)
  return value
}
for (const locale of docsLocales) {
  test('采购经理组织范围 ' + locale, async ({ page }) => {
    test.skip(!enabled, '需要已核实的隔离 Docker 环境及仓库外真实教学状态')
    expect(registry.captures.filter(item => item.locale === locale)).toHaveLength(0)
    /** 只读请求保留实际 HTTP 与业务码，避免把 HTTP 200 误写成资源可见。 */
    async function get(actor: string, path: string, expected = 200) {
      const response = await page.request.get(path, { headers: { Authorization: 'Bearer ' + token(actor), 'X-Tenant-Id': '1' } })
      const result = await response.json() as { code: number; data: unknown }
      registry.observations.push({ at: new Date().toISOString(), locale, actor, path, method: 'GET', http: response.status(), code: result.code, expected })
      persist()
      expect(result.code).toBe(expected)
      if (expected === 200) expect(response.status()).toBe(200)
      return result.data
    }
    const inside = await get('manager', '/api/procurement/requisition/' + teaching.objects.requisition.id) as Requisition
    expect(inside.status).toBe('APPROVED')
    expect(inside.requesterUnitId).toBe(teaching.users.manager!.primaryUnitId)
    const outsideRecord = await get('procurementOutside', '/api/procurement/requisition/' + teaching.objects.procurementScope20261012.outsideRequest.id) as Requisition
    expect(outsideRecord.status).toBe('DRAFT')
    expect(outsideRecord.requesterUnitId).toBe(teaching.users.procurementOutside!.primaryUnitId)
    await get('manager', '/api/procurement/requisition/' + outsideRecord.id, 404)
    const outsideList = await get('manager', '/api/procurement/requisition/list?page=1&size=100&keyword=' + encodeURIComponent(outsideRecord.requisitionNo)) as { records: Requisition[] }
    expect(outsideList.records).toHaveLength(0)
    await prepareDocsPage(page, { locale, token: token('manager'), username: teaching.users.manager!.username, viewport })
    await page.goto(route)
    await waitForDocsPage(page, 'main')
    await expect(page).not.toHaveURL(/login|menu-load-error/)
    async function search(keyword: string) {
      const input = page.locator('main .el-form input').first()
      await input.fill(keyword)
      await Promise.all([page.waitForResponse(response => response.url().includes('/api/procurement/requisition/list')),
        input.press('Enter')])
      await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
      await waitForDocsPage(page, 'main')
    }
    async function capture(name: string, assertions: Record<string, unknown>) {
      await waitForDocsPage(page, 'main')
      await captureDocsImage(page, locale, name)
      const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, name + '.png')
      registry.captures.push({ name, locale, image, sha256: sha(readFileSync(image)), captured_at: new Date().toISOString(),
        actor: 'manager', roles: teaching.users.manager!.roleCodes, route, viewport, module: 'permissions-exceptions',
        flow: 'procurement-admin-scope', assertions, mocks: false, visual_review: 'pending',
        test: 'omni-frontend/e2e-docs/flows/procurement-scope.flows.spec.ts',
        test_sha256: sha(readFileSync(resolve('e2e-docs/flows/procurement-scope.flows.spec.ts'))) })
      persist()
    }
    await search(inside.requisitionNo)
    const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: inside.requisitionNo })
    await expect(row).toHaveCount(1)
    await capture('procurement-manager-scope', { visible_resource: inside.requisitionNo, requester_unit: inside.requesterUnitId, actor_scope: 'DEPT_AND_BELOW' })
    await row.getByRole('button', { name: /详情|Details?|詳細|상세/, exact: true }).click()
    const drawer = page.locator('.el-drawer:visible')
    await expect(drawer).toContainText(inside.requisitionNo)
    await expect(drawer).toContainText(label(locale, 'procurementRequisitionPage.statusApproved'))
    await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
    await capture('procurement-manager-scope-detail', { status: inside.status, resource: inside.requisitionNo, no_super_admin: true })
    await drawer.locator('.el-drawer__close-btn').click()
    await expect(drawer).not.toBeVisible()
    await search(outsideRecord.requisitionNo)
    await expect(page.locator('.el-table__empty-block')).toBeVisible()
    await capture('procurement-manager-outside-empty', { hidden_resource: outsideRecord.requisitionNo,
      outside_unit: outsideRecord.requesterUnitId, manager_detail_business_code: 404, manager_list_count: 0, actual_owner_detail_code: 200 })
    process.stdout.write('PROCUREMENT_SCOPE_READONLY_PASS locale=' + locale + '\n')
  })
}
