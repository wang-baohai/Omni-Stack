/** 系统配置、登录投影、菜单权限与角色范围的四语真实教学取证。 */
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, relative, resolve } from 'node:path'
import zhCN from '../../src/locales/zh-CN'
import enUS from '../../src/locales/en-US'
import jaJP from '../../src/locales/ja-JP'
import koKR from '../../src/locales/ko-KR'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

const messages = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const adminToken = process.env.E2E_ADMIN_TOKEN
const employeeToken = process.env.E2E_EMPLOYEE_TOKEN
const mutations = process.env.E2E_MUTATIONS === 'true'
const policyPath = '/api/auth/system-config/login-policy'
const registryPath = process.env.E2E_REGISTRY_PATH || ''
const runStamp = Date.now()
const testSha256 = createHash('sha256').update(readFileSync(resolve('e2e-docs/flows/system-platform-teaching.flows.spec.ts'))).digest('hex')
interface Policy { failureThreshold: number; failureWindowSeconds: number; version: number; source: string }
interface Role { id: number; roleCode: string; dataScope: string }
interface Permission { id: number; permissionCode: string; permissionName: string; type: string; checked?: boolean; children?: Permission[] }
interface Resource { locale: DocsLocale; roleCode: string; roleId: number | null; originalPolicy?: Policy; policyRestored?: boolean; roleDeleted?: boolean; finalPolicyVersion?: number }
const resources: Resource[] = []
const captures: Array<Record<string, unknown>> = []
const observations: Array<Record<string, unknown>> = []
let runtime: Record<string, unknown>
let reuseChineseEvidence = false

/** 词条与真实页面使用相同语言源；缺失的权限名称回退单独记录。 */
function label(locale: DocsLocale, key: string): string | undefined {
  let value: unknown = messages[locale]
  for (const part of key.split('.')) value = (value as Record<string, unknown> | undefined)?.[part]
  return typeof value === 'string' ? value : undefined
}
function text(locale: DocsLocale, key: string): string {
  const value = label(locale, key)
  expect(value, `Missing locale label: ${locale}/${key}`).toBeTruthy()
  return value!
}
function flatten(nodes: Permission[]): Permission[] { return nodes.flatMap((node) => [node, ...flatten(node.children || [])]) }
function persist() {
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath, `${JSON.stringify({ runStamp, project: process.env.E2E_SYSTEM_PROJECT, runtime, testSha256, resources, captures, observations }, null, 2)}\n`)
}

/** 请求只记录状态与非敏感业务键；不记录认证头或完整响应。 */
async function api<T>(context: APIRequestContext, method: string, path: string, data?: unknown, token = adminToken): Promise<T> {
  if (method !== 'GET') expect(mutations).toBe(true)
  const response = await context.fetch(path, { method, timeout: 15000, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...(data === undefined ? {} : { data }) })
  const result = await response.json()
  observations.push({ at: new Date().toISOString(), method, path, http: response.status(), code: result.code })
  persist()
  expect(response.status()).toBe(200)
  expect(result.code).toBe(200)
  return result.data as T
}

/** 等实际加载遮罩和保存状态消失后保存原图及摘要。 */
async function capture(page: Page, locale: DocsLocale, name: string, flow: string) {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
  await captureDocsImage(page, locale, name)
  const image = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${name}.png`)
  captures.push({ locale, name, flow, route: new URL(page.url()).pathname, viewport: page.viewportSize(), sha256: createHash('sha256').update(readFileSync(image)).digest('hex'), captured_at: new Date().toISOString(), visual_review: 'pending', mocks: false })
  persist()
}

/** 中断后仍按唯一角色编码核对正式接口；配置只恢复原值，保留递增版本与审计。 */
async function cleanup(context: APIRequestContext, resource: Resource) {
  if (resource.originalPolicy) {
    let current = await api<Policy>(context, 'GET', policyPath)
    const original = resource.originalPolicy
    if (current.failureThreshold !== original.failureThreshold || current.failureWindowSeconds !== original.failureWindowSeconds) {
      current = await api<Policy>(context, 'PUT', policyPath, { failureThreshold: original.failureThreshold, failureWindowSeconds: original.failureWindowSeconds, expectedVersion: current.version })
    }
    expect(current.failureThreshold).toBe(original.failureThreshold)
    expect(current.failureWindowSeconds).toBe(original.failureWindowSeconds)
    resource.policyRestored = true
    resource.finalPolicyVersion = current.version
    persist()
  }
  const roles = await api<Role[]>(context, 'GET', '/api/auth/role/all')
  const owned = roles.filter((role) => role.roleCode === resource.roleCode)
  expect(owned.length).toBeLessThanOrEqual(1)
  if (owned.length) {
    if (resource.roleId !== null) expect(owned[0]!.id).toBe(resource.roleId)
    await api(context, 'DELETE', `/api/auth/role/${owned[0]!.id}`)
  }
  const remaining = await api<Role[]>(context, 'GET', '/api/auth/role/all')
  expect(remaining.some((role) => role.roleCode === resource.roleCode)).toBe(false)
  resource.roleDeleted = true
  persist()
}

test.describe('基础平台四语实际操作', () => {
  test.describe.configure({ mode: 'serial' })
  test.skip(!adminToken || !employeeToken || !mutations, '需要隔离环境短期身份和 E2E_MUTATIONS=true')
  test.beforeAll(() => {
    expect(existsSync('/.dockerenv')).toBe(true)
    expect(process.env.E2E_RUNTIME_MODE).toBe('external')
    expect(process.env.E2E_SYSTEM_PROJECT).toMatch(/^omni-(?:e2e|wiki-acceptance)-[a-z0-9-]+$/)
    for (const path of [registryPath, process.env.E2E_DOCS_IMAGE_DIR, process.env.E2E_SYSTEM_RUNTIME_FILE]) {
      expect(path && isAbsolute(path)).toBeTruthy()
      expect(relative(resolve('..'), path!).startsWith('..')).toBe(true)
    }
    expect(existsSync(registryPath), 'Existing registry must be reconciled before repeating writes').toBe(false)
    runtime = JSON.parse(readFileSync(process.env.E2E_SYSTEM_RUNTIME_FILE!, 'utf8').replace(/^\uFEFF/, ''))
    expect(runtime.project).toBe(process.env.E2E_SYSTEM_PROJECT)
    const containers = runtime.containers as Array<{ service: string; image: string; ports: number; status: string }>
    for (const service of ['omni-auth', 'omni-base', 'omni-gateway', 'omni-frontend']) {
      const container = containers.find((item) => item.service === service)
      expect(container?.image).toMatch(/^sha256:[a-f0-9]{64}$/)
      expect(container?.ports).toBe(0)
      expect(container?.status).toBe('running')
    }
    // 只复用已核对清理、原图摘要一致的六项成功步骤，避免重放配置写入。
    if (process.env.E2E_SYSTEM_PRIOR_REGISTRY) {
      const priorPath = process.env.E2E_SYSTEM_PRIOR_REGISTRY
      const priorImages = process.env.E2E_SYSTEM_PRIOR_IMAGES!
      const priorSource = process.env.E2E_SYSTEM_PRIOR_SOURCE!
      for (const path of [priorPath, priorImages, priorSource]) {
        expect(path && isAbsolute(path)).toBeTruthy()
        expect(relative(resolve('..'), path).startsWith('..')).toBe(true)
      }
      const prior = JSON.parse(readFileSync(priorPath, 'utf8'))
      expect(prior.project).toBe(process.env.E2E_SYSTEM_PROJECT)
      expect(prior.reconciliation.status).toBe('PASS_POLICY_ORIGINAL_VALUES_AND_OWNED_ROLE_REMOVED')
      expect(prior.resources).toHaveLength(1)
      expect(prior.resources[0]).toMatchObject({ locale: 'zh-CN', policyRestored: true, roleDeleted: true })
      expect(createHash('sha256').update(readFileSync(priorSource)).digest('hex')).toBe(prior.testSha256)
      const names = ['system-config-policy', 'system-config-saved', 'system-config-conflict', 'system-menu-permission-tree', 'system-role-scope-form', 'system-role-scope-saved']
      expect(prior.captures).toHaveLength(names.length)
      for (const name of names) {
        const captured = prior.captures.find((item: { locale: string; name: string }) => item.locale === 'zh-CN' && item.name === name)
        expect(captured).toBeTruthy()
        expect(createHash('sha256').update(readFileSync(resolve(priorImages, 'zh-CN', `${name}.png`))).digest('hex')).toBe(captured.sha256)
      }
      reuseChineseEvidence = true
      observations.push({ reused_completed_steps: names, locale: 'zh-CN', prior_test_sha256: prior.testSha256, prior_role_id: prior.resources[0].roleId, new_permission_role_is_separate: true })
    }
    persist()
  })

  for (const locale of docsLocales) {
    test(`配置并发、登录投影、菜单与角色 / ${locale}`, async ({ page }) => {
      test.setTimeout(150000)
      page.setDefaultTimeout(15000)
      page.setDefaultNavigationTimeout(20000)
      const reuseCompleted = locale === 'zh-CN' && reuseChineseEvidence
      const resource: Resource = { locale, roleCode: `WIKI_SYS_${runStamp}_${locale.replace('-', '')}`, roleId: null }
      resources.push(resource)
      persist()
      await prepareDocsPage(page, { locale, token: adminToken, username: 'admin' })
      try {
        const forbidden = await page.request.get(policyPath, { headers: { Authorization: `Bearer ${employeeToken}` } })
        expect(forbidden.status()).toBe(403)
        expect((await forbidden.json()).code).toBe(403)
        resource.originalPolicy = await api<Policy>(page.request, 'GET', policyPath)
        persist()
        if (!reuseCompleted) {
          await page.goto('/admin/system/config')
          await waitForDocsPage(page, '.policy-form')
          await expect(page.locator('.card-header')).toContainText(text(locale, 'systemConfig.title'))
          await capture(page, locale, 'system-config-policy', 'config')
          const threshold = resource.originalPolicy.failureThreshold === 10 ? 9 : 10
          await page.locator('.policy-form .el-input-number input').first().fill(String(threshold))
          await page.locator('.policy-form .el-input-number input').first().blur()
          const saveResponse = page.waitForResponse((response) => response.url().includes(policyPath) && response.request().method() === 'PUT')
          await page.getByRole('button', { name: text(locale, 'systemConfig.save'), exact: true }).click()
          const saved = await saveResponse
          expect(saved.status()).toBe(200)
          const savedBody = await saved.json()
          expect(savedBody.code).toBe(200)
          expect(savedBody.data.failureThreshold).toBe(threshold)
          expect(savedBody.data.version).toBeGreaterThan(resource.originalPolicy.version)
          await expect(page.locator('.el-message--success')).toContainText(text(locale, 'systemConfig.saveSuccess'))
          await capture(page, locale, 'system-config-saved', 'config')
          // 第二个真实请求推进权威版本，让当前页面携带旧版本产生冲突。
          await api<Policy>(page.request, 'PUT', policyPath, { failureThreshold: resource.originalPolicy.failureThreshold, failureWindowSeconds: resource.originalPolicy.failureWindowSeconds, expectedVersion: savedBody.data.version })
          const conflictResponse = page.waitForResponse((response) => response.url().includes(policyPath) && response.request().method() === 'PUT')
          const authoritativeResponse = page.waitForResponse((response) => response.url().includes(policyPath) && response.request().method() === 'GET')
          await page.getByRole('button', { name: text(locale, 'systemConfig.save'), exact: true }).click()
          const conflict = await conflictResponse
          expect(conflict.status()).toBe(200)
          expect((await conflict.json()).code).toBe(409)
          expect((await (await authoritativeResponse).json()).data.version).toBeGreaterThan(savedBody.data.version)
          await expect(page.locator('.conflict-note')).toBeVisible()
          await expect(page.locator('.policy-form .el-input-number input').first()).toHaveValue(String(threshold))
          await capture(page, locale, 'system-config-conflict', 'config')
        }

        const tree = await api<Permission[]>(page.request, 'GET', '/api/auth/permission/tree')
        const nodes = flatten(tree)
        expect(new Set(nodes.map((node) => node.type))).toEqual(new Set(['DIRECTORY', 'MENU', 'API']))
        if (!reuseCompleted) {
          await page.goto('/admin/system/permission')
          await waitForDocsPage(page, '.el-table')
          await expect(page.locator('.el-table__body .el-table__row').first()).toBeVisible()
          await capture(page, locale, 'system-menu-permission-tree', 'menu')
        }

        const created = await api<Role>(page.request, 'POST', '/api/auth/role', { roleCode: resource.roleCode, roleName: `Wiki Training ${locale}`, dataScope: reuseCompleted ? 'SELF' : 'DEPT', sort: 0, status: 1 })
        resource.roleId = created.id
        persist()
        await page.goto('/admin/system/role')
        await waitForDocsPage(page, '.el-table')
        await page.locator('.el-pagination .el-select').click()
        await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: /^100(?:\D|$)/ }).click()
        const row = page.locator('.el-table__body .el-table__row').filter({ hasText: resource.roleCode })
        await expect(row).toHaveCount(1)
        if (!reuseCompleted) {
          await row.getByRole('button', { name: text(locale, 'common.edit'), exact: true }).click()
          const dialog = page.locator('.el-dialog:visible')
          await dialog.locator('.el-select').click()
          await page.getByRole('option', { name: text(locale, 'role.dataScopeSelf'), exact: true }).click()
          await capture(page, locale, 'system-role-scope-form', 'data-scope')
          const updateResponse = page.waitForResponse((response) => response.url().endsWith(`/api/auth/role/${created.id}`) && response.request().method() === 'PUT')
          await dialog.getByRole('button', { name: text(locale, 'common.confirm'), exact: true }).click()
          expect((await (await updateResponse).json()).code).toBe(200)
          await expect(dialog).toHaveCount(0)
          expect((await api<Role>(page.request, 'GET', `/api/auth/role/${created.id}`)).dataScope).toBe('SELF')
          await expect(row).toContainText(text(locale, 'role.dataScopeSelf'))
          await capture(page, locale, 'system-role-scope-saved', 'data-scope')
        }
        await row.getByRole('button', { name: text(locale, 'role.assignPermissions'), exact: true }).click()
        const permissionDialog = page.locator('.el-dialog:visible')
        await expect(permissionDialog).toBeVisible()
        for (const code of ['system:loginrecord', 'system:loginrecord:list']) {
          const node = nodes.find((item) => item.permissionCode === code)
          expect(node).toBeTruthy()
          const key = `permission.perm_${code.replace(/[:.]/g, '_')}`
          const translated = label(locale, key)
          if (!translated) observations.push({ locale, permission_label_fallback: code, language: 'seed text' })
          const checkbox = permissionDialog.getByRole('checkbox', { name: translated || node!.permissionName, exact: true })
          const checkboxLabel = permissionDialog.locator('label.el-checkbox').filter({ has: page.getByRole('checkbox', { name: translated || node!.permissionName, exact: true }) })
          await checkboxLabel.scrollIntoViewIfNeeded()
          await checkboxLabel.click()
          await expect(checkbox).toBeChecked()
        }
        await capture(page, locale, 'system-role-permission-dialog', 'menu')
        const assignResponse = page.waitForResponse((response) => response.url().endsWith(`/api/auth/permission/role/${created.id}/assign`) && response.request().method() === 'POST')
        await permissionDialog.getByRole('button', { name: text(locale, 'common.confirm'), exact: true }).click()
        expect((await (await assignResponse).json()).code).toBe(200)
        const assigned = flatten(await api<Permission[]>(page.request, 'GET', `/api/auth/permission/role/${created.id}`)).filter((node) => node.checked)
        expect(assigned.map((node) => node.permissionCode).sort()).toEqual(['system:loginrecord', 'system:loginrecord:list'])

        await page.goto('/admin/system/loginrecord')
        await waitForDocsPage(page, '.filter-form')
        await page.locator('.filter-form .el-input input').first().fill('admin')
        await page.locator('.filter-form .el-select').nth(1).click()
        await page.getByRole('option', { name: text(locale, 'loginRecord.methodPassword'), exact: true }).click()
        const recordsResponse = page.waitForResponse((response) => response.url().includes('/api/auth/login-record/list') && response.url().includes('method=PASSWORD'))
        await page.getByRole('button', { name: text(locale, 'common.search'), exact: true }).click()
        const records = await (await recordsResponse).json()
        expect(records.code).toBe(200)
        expect(records.data.records.length).toBeGreaterThan(0)
        expect(records.data.records.every((record: { method: string; username: string }) => record.method === 'PASSWORD' && record.username === 'admin')).toBe(true)
        await expect(page.locator('.el-table__body .el-table__row').first()).toBeVisible()
        await capture(page, locale, 'system-login-record-filtered', 'login-record')
      } catch (error) {
        observations.push({ locale, phase_error: String(error).replace(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g, '[redacted]').slice(0, 800) })
        persist()
        throw error
      } finally {
        test.setTimeout(test.info().timeout + 45000)
        await cleanup(page.request, resource)
      }
    })
  }
})
