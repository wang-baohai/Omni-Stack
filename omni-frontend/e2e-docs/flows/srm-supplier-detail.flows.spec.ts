/**
 * 真实 Docker 供应商三态截图：校验失败、创建结果、编辑详情。
 * 创建自动尝试启动准入流程；仅通过正式接口撤销本轮活动流程并软删除供应商。
 * 系统审计、Outbox 和流程历史按既有契约保留；图片先暂存，通过质检后才登记。
 *
 * 跨进程保护：registry 采用 INTENT→SUBMITTING→COMMITTED 状态机，
 * 启动时检查同目标未闭合条目，拒绝 execution=unknown，物理目标由
 * serverUuid + database + tenantId 唯一定义。
 */
import { expect, test, type APIRequestContext, type APIResponse } from '@playwright/test'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { ApiResponse, PageResult } from '../../src/types/api'
import zh from '../../src/locales/zh-CN'
import en from '../../src/locales/en-US'
import ja from '../../src/locales/ja-JP'
import ko from '../../src/locales/ko-KR'
import { docsLocales, waitForDocsPage } from '../fixtures/docs-page'
import {
  buildTargetDigest, newSnapshot, persistRegistry as persistSnapshot,
  registerIntent, markSubmitting, markCommitted, markCleaned, startupCheck,
  requireExecution, requireAttempt, findUnsettledByName,
  type RegistrySnapshot, type TargetDigest,
} from '../helpers/e2e-registry'

const messages = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const baseURL = process.env.E2E_BASE_URL
const adminToken = process.env.E2E_ADMIN_TOKEN
const tenantId = Number(process.env.E2E_TENANT_ID)
if (!Number.isInteger(tenantId) || tenantId <= 0) {
  throw new Error('必须显式指定正整数 E2E_TENANT_ID，不接受默认回退')
}
const runStamp = process.env.E2E_RUN_STAMP || String(Date.now())
const registryPath = process.env.E2E_REGISTRY_PATH
  || join(tmpdir(), 'omni-e2esd-registry', 'repair-' + runStamp + '.json')
const execution = requireExecution()
const attempt = requireAttempt()

interface Supplier {
  id: number
  name: string
  version: number
  status: string
  processInstanceId?: string
}

/** 内存快速索引；持久化以 registry snapshot 为准。 */
const intentions: string[] = []
const resources = new Map<number, Supplier>()
let snapshot: RegistrySnapshot
let targetDigest: TargetDigest
let api: APIRequestContext

/** 持久化 registry 到磁盘。 */
function persistRegistry() { persistSnapshot(registryPath, snapshot) }

function requireMutations() {
  if (process.env.E2E_MUTATIONS !== 'true' || !adminToken) throw new Error('缺少写入开关或 admin fixture Token')
}

/** 只输出状态码，不将响应体或认证头写入诊断日志。 */
async function dataOf<T>(response: APIResponse): Promise<T> {
  expect(response.ok(), 'HTTP ' + response.status()).toBeTruthy()
  const body = await response.json() as ApiResponse<T>
  expect(body.code, '业务响应状态码').toBe(200)
  return body.data
}

/** 正式 name 参数分页、精确名称匹配；拒绝 keyword、首行兜底和错误分页结构。 */
async function exactSuppliers(name: string): Promise<Supplier[]> {
  expect(intentions).toContain(name)
  const matches: Supplier[] = []
  for (let page = 1; page <= 10; page += 1) {
    const data = await dataOf<PageResult<Supplier>>(await api.get('/api/srm/supplier/list', {
      params: { name, page, size: 100 },
    }))
    expect(Array.isArray(data.records), '分页 data.records 必须是数组').toBe(true)
    matches.push(...data.records.filter((row) => row.name === name))
    if (page * data.size >= data.total) return matches
  }
  throw new Error('精确名称查询超过清理分页上限，停止并保留登记')
}

test.describe('SRM supplier detail and action states', () => {
  test.beforeAll(async ({ playwright }) => {
    requireMutations()
    if (!baseURL || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(baseURL).hostname)) {
      throw new Error('只允许显式指定本地 Docker 前端地址')
    }
    api = await playwright.request.newContext({
      baseURL, timeout: 120_000,
      extraHTTPHeaders: { Authorization: 'Bearer ' + adminToken, 'X-Tenant-Id': String(tenantId) },
    })
    await dataOf(await api.get('/api/auth/menus'))

    // 物理目标：serverUuid 从环境变量获取，必须在 R06 前确定。
    const serverUuid = process.env.E2E_SERVER_UUID || 'pending-detection'
    targetDigest = buildTargetDigest(baseURL, tenantId, serverUuid)

    // 启动前跨进程保护检查。
    const check = startupCheck(registryPath, targetDigest, execution)
    if (!check.canProceed) {
      throw new Error('registry 启动检查失败: ' + check.reason)
    }
    snapshot = check.snapshot || newSnapshot(execution, attempt, docsLocales[0], targetDigest)
    process.stdout.write('[srm-detail-registry] check=' + check.reason
      + ' unsettled=' + check.unsettledCount + ' path=' + registryPath + '\n')
  })

  test.afterEach(async () => {
    if (!api) return
    const failures: number[] = []
    // 回收未成功登记响应的创建；删除前再次读取当前版本和名称。
    for (const name of intentions) {
      const matches = await exactSuppliers(name)
      for (const supplier of matches) resources.set(supplier.id, supplier)
      // 找到正式资源后，将 INTENT/SUBMITTING 推进到 COMMITTED。
      if (matches.length > 0) {
        const entry = findUnsettledByName(snapshot, name)
        if (entry && entry.phase !== 'COMMITTED') {
          markCommitted(snapshot, name, matches[0]!.id, matches[0]!.version, matches[0]!.processInstanceId ?? null)
        }
      }
    }
    persistRegistry()
    for (const supplier of resources.values()) {
      if (snapshot.cleanup.some((entry) => entry.resourceId === supplier.id && entry.result === 'deleted')) continue
      try {
        requireMutations()
        let current = await dataOf<Supplier>(await api.get('/api/srm/supplier/' + supplier.id))
        expect(current.name).toBe(supplier.name)
        expect(intentions).toContain(current.name)
        if (current.status === 'APPROVING') {
          expect(current.processInstanceId, '本轮活动流程必须有精确实例 ID').toBeTruthy()
          await dataOf(await api.post('/api/srm/supplier/' + current.id + '/cancel', {
            data: { version: current.version },
          }))
          current = await dataOf<Supplier>(await api.get('/api/srm/supplier/' + supplier.id))
        }
        expect(['PENDING_REVIEW', 'REJECTED']).toContain(current.status)
        await dataOf(await api.delete('/api/srm/supplier/' + current.id, {
          params: { version: current.version },
        }))
        markCleaned(snapshot, current.name, current.id, 'deleted')
      } catch {
        markCleaned(snapshot, supplier.name, supplier.id, 'failed')
        failures.push(supplier.id)
      } finally {
        persistRegistry()
      }
    }
    for (const name of intentions) failures.push(...(await exactSuppliers(name)).map((row) => row.id))
    const unsettledNow = snapshot.entries.filter((e) =>
      e.phase === 'INTENT' || e.phase === 'SUBMITTING' || e.phase === 'COMMIT_UNKNOWN')
    process.stdout.write('[srm-detail-cleanup] registered=' + resources.size + ' failures=' + failures.length
      + ' unsettled=' + unsettledNow.length + ' registry=' + registryPath + '\n')
    expect(unsettledNow.map((e) => e.name), '请求超时可能延迟提交；空列表不能证明清理完成，按登记停止并人工收口').toEqual([])
    expect(failures, '正式接口清理及精确名称回查必须全部成功').toEqual([])
  })

  test.afterAll(async () => { await api?.dispose() })

  for (const locale of docsLocales) {
    test('supplier detail and action states / ' + locale, async ({ page }, testInfo) => {
      test.setTimeout(240_000)
      const copy = messages[locale]
      const labels = copy.srmSupplierPage
      const requestSummary: string[] = []
      const browserErrors: string[] = []
      page.on('request', (request) => {
        const path = new URL(request.url()).pathname
        if (request.method() !== 'GET' && path.includes('/srm/supplier')) requestSummary.push(request.method() + ' ' + path)
      })
      page.on('pageerror', (error) => browserErrors.push(error.name))
      const name = 'E2ESD-' + runStamp + '-' + locale
      intentions.push(name)
      snapshot.locale = locale
      registerIntent(snapshot, name, locale)
      persistRegistry()
      // 不使用展示 mock 或过去时钟；页面和全部 API 均来自现有 Docker。
      await page.addInitScript(({ token, lang }) => {
        localStorage.setItem('token', token)
        localStorage.setItem('username', 'admin')
        localStorage.setItem('omni-lang', lang)
      }, { token: adminToken!, lang: locale })
      await page.goto('/admin/srm/supplier')
      await waitForDocsPage(page, '.el-table')
      await page.getByPlaceholder(labels.nameOrNo, { exact: true }).fill(name)
      const searchResponse = page.waitForResponse((response) => {
        const url = new URL(response.url())
        return url.pathname === '/api/srm/supplier/list' && url.searchParams.get('name') === name
      })
      await page.getByRole('button', { name: copy.common.search, exact: true }).click()
      await searchResponse
      await expect(page.locator('.el-table__row')).toHaveCount(0)

      await page.getByRole('button', { name: labels.create, exact: true }).click()
      const dialog = page.getByRole('dialog', { name: labels.create, exact: true })
      const save = dialog.getByRole('button', { name: copy.common.save, exact: true })
      await save.click()
      await expect(dialog.locator('.el-form-item__error').getByText(copy.srmSupplierMessages.nameRequired, { exact: true })).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('srm-supplier-create-validation.png') })

      await dialog.locator('.el-form-item').filter({ hasText: labels.supplierName }).locator('input').fill(name)
      const typeSelect = dialog.locator('.el-form-item').filter({ hasText: copy.srmSupplierOverview.type }).locator('.el-select')
      await typeSelect.click()
      await page.locator('.el-select-dropdown:visible').getByRole('option', { name: labels.typeManufacturer, exact: true }).click()
      await expect(typeSelect).toContainText(labels.typeManufacturer)
      await typeSelect.press('Tab')
      await expect(dialog.locator('.el-form-item__error')).toHaveCount(0)

      // 先监听响应，填表后只提交一次；收到响应立即登记，不以轮询代替保存。
      markSubmitting(snapshot, name)
      persistRegistry()
      const creation = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/srm/supplier'
        && response.request().method() === 'POST', { timeout: 180_000 }).then(async (response) => {
        const body = await response.json() as ApiResponse<Supplier>
        expect(body.code, '创建业务状态码').toBe(200)
        expect(body.data.name).toBe(name)
        expect(Number.isSafeInteger(body.data.id)).toBe(true)
        resources.set(body.data.id, body.data)
        markCommitted(snapshot, name, body.data.id, body.data.version, body.data.processInstanceId ?? null)
        persistRegistry()
        return body.data
      })
      const [, created] = await Promise.all([save.click(), creation]).catch(async (error: unknown) => {
        const fields = await dialog.locator('.el-form-item').evaluateAll((items) => items.map((item) => ({
          label: item.querySelector('.el-form-item__label')?.textContent,
          invalid: item.classList.contains('is-error'),
          validating: item.classList.contains('is-validating'),
        })))
        process.stdout.write('[save-diagnostic] ' + JSON.stringify({ requestSummary, browserErrors, fields }) + '\n')
        throw error
      })
      await expect(dialog).toBeHidden({ timeout: 20_000 })
      const found = await exactSuppliers(name)
      expect(found, '单次提交只能创建一条供应商').toHaveLength(1)
      expect(found[0]!.id).toBe(created.id)
      const current = await dataOf<Supplier>(await api.get('/api/srm/supplier/' + created.id))
      expect(['PENDING_REVIEW', 'APPROVING']).toContain(current.status)

      const row = page.locator('.el-table__row').filter({ hasText: name })
      await expect(row).toHaveCount(1)
      await expect(row).toBeVisible()
      const status = current.status === 'APPROVING' ? labels.statusApproving : labels.statusPendingReview
      await expect(row.locator('.el-tag').getByText(status, { exact: true })).toBeVisible()
      await page.screenshot({ path: testInfo.outputPath('srm-supplier-created.png') })

      // Overview 360 视图抽屉验证（R04：真 Overview，非 Edit 代替）
      await row.getByRole('button', { name: copy.srmSupplierPage.overview, exact: true }).click()
      const overviewDrawer = page.locator('.el-drawer').filter({ hasText: copy.srmSupplierOverview.basicInfo })
      await expect(overviewDrawer).toBeVisible({ timeout: 15_000 })
      await expect(overviewDrawer.locator('.el-drawer__header').first()).toContainText(name)
      await page.screenshot({ path: testInfo.outputPath('srm-supplier-overview.png') })
      await overviewDrawer.getByRole('button', { name: copy.common.close || '×' }).or(page.locator('.el-drawer__header .el-drawer__close-btn')).first().click()
      await expect(overviewDrawer).toBeHidden({ timeout: 10_000 })

      // Edit 保留为补充验证
      await row.getByRole('button', { name: copy.common.edit, exact: true }).click()
      const detail = page.getByRole('dialog', { name: labels.edit, exact: true })
      await expect(detail).toBeVisible()
      await expect(detail.locator('.el-form-item').filter({ hasText: labels.supplierName }).locator('input')).toHaveValue(name)
      await page.screenshot({ path: testInfo.outputPath('srm-supplier-detail.png') })
      await detail.getByRole('button', { name: copy.common.cancel, exact: true }).click()
      await expect(detail).toBeHidden()
      await testInfo.attach('scenario-evidence', {
        contentType: 'application/json',
        body: Buffer.from(JSON.stringify({ locale, tenantId, runStamp, id: created.id, status: current.status })),
      })
    })
  }
})
