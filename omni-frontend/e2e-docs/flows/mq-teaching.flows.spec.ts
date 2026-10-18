/**
 * @module mq-teaching.flows.spec
 * 独立教学项目的真实 Outbox 生命周期；不拦截接口、不篡改时钟或返回状态。
 * pending / retry / actions / terminal / fault / operlog 分阶段执行，续做先核对原始资源回执。
 * retry 等待宿主编排器 Release；Repair 仅修正路由，重发与忽略由真实页面提交。
 */
import { expect, test, request, type APIRequestContext, type Browser, type Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from 'node:fs'
import { dirname, isAbsolute, join, relative, resolve } from 'node:path'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage, type DocsLocale } from '../fixtures/docs-page'

interface Resource {
  locale: DocsLocale
  kind: 'recover' | 'skip' | 'replay'
  msgId: string
  eventId: string
  msgKey: string
}

interface Message {
  msgId: string
  msgKey: string
  status: number
  retryCount: number
  maxRetry: number
  nextRetryTime: string | null
  updateTime: string | null
  errorMsg: string | null
  brokerType: string
}

interface Fixture {
  project: string
  runId: string
  tenantId: number
  prefix: string
  synthetic: boolean
  resources: Resource[]
}

const phase = process.env.E2E_MQ_PHASE
const baseURL = process.env.E2E_BASE_URL
const adminToken = process.env.E2E_ADMIN_TOKEN
const outsiderToken = process.env.E2E_MQ_OUTSIDER_TOKEN
const imageDirectory = process.env.E2E_DOCS_IMAGE_DIR
const registryPath = process.env.E2E_REGISTRY_PATH
let fixture: Fixture
let api: APIRequestContext
let sourceSha256: string
let runtime: Record<string, unknown>
const captures: Array<Record<string, unknown>> = []
const observations: Array<Record<string, unknown>> = []
const actions: Array<Record<string, unknown>> = []
const text = {
  search: ['搜索', 'Search', '検索', '검색'],
  key: ['业务键', 'Business Key', '業務キー', '비즈니스 키'],
  close: ['关闭', 'Close', '閉じる', '닫기'],
  confirm: ['确认', 'Confirm', '確認', '확인'],
  pending: ['待发送', 'Pending', '保留中', '대기'],
  failed: ['失败', 'Failed', '失敗', '실패'],
  dead: ['死信', 'Dead Letter', 'デッドレター', '데드레터'],
  sent: ['已发送', 'Sent', '送信済み', '전송됨'],
  skipped: ['已忽略', 'Skipped', 'スキップ済み', '건너뜀'],
} as const
const label = (key: keyof typeof text, locale: DocsLocale) => text[key][docsLocales.indexOf(locale)]

/** 只保存脱敏结果和原图摘要，提交前的持久登记用于防止断流后重复写入。 */
function save() {
  if (!registryPath) throw new Error('缺少仓库外登记路径')
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(`${registryPath}.next`, JSON.stringify({
    phase, updatedAt: new Date().toISOString(), project: fixture?.project, runId: fixture?.runId,
    synthetic: true, producerTraceId: null, sourceSha256,
    testSha256: createHash('sha256').update(readFileSync(resolve('e2e-docs/flows/mq-teaching.flows.spec.ts'))).digest('hex'),
    runtime, captures, observations, actions,
  }, null, 2))
  renameSync(`${registryPath}.next`, registryPath)
}

/** 源码摘要覆盖当前前端生产源；镜像身份另外记录，不把摘要等同于镜像构建证明。 */
function digestSource(): string {
  const files: string[] = []
  function visit(directory: string) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name)
      if (entry.isDirectory()) visit(path)
      else if (entry.isFile()) files.push(path)
    }
  }
  visit(resolve('src'))
  return createHash('sha256').update(files.sort().map(path =>
    `${relative(process.cwd(), path)} ${createHash('sha256').update(readFileSync(path)).digest('hex')}\n`,
  ).join('')).digest('hex')
}

/** 读取真实聚合接口，仅接受本回执的唯一业务键和租户。 */
async function messages(): Promise<Message[]> {
  const response = await api.get('/api/base/mq-message/list', { params: { page: 1, size: 50, msgKey: fixture.prefix } })
  expect(response.status()).toBe(200)
  const json = await response.json()
  expect(json.code).toBe(200)
  const rows = json.data.records as Message[]
  expect(rows).toHaveLength(fixture.resources.length)
  expect(rows.map(row => row.msgId).sort()).toEqual(fixture.resources.map(row => row.msgId).sort())
  observations.push({ at: new Date().toISOString(), rows: rows.map(row => ({
    msgId: row.msgId, status: row.status, retryCount: row.retryCount, maxRetry: row.maxRetry,
    nextRetryTime: row.nextRetryTime, updateTime: row.updateTime, errorMsg: row.errorMsg, brokerType: row.brokerType,
  })) })
  save()
  return rows
}

/** 按真实业务状态轮询；超时只报告未知，不触发重发或改库。 */
async function waitMessages(predicate: (rows: Message[]) => boolean, timeout: number): Promise<Message[]> {
  let rows: Message[] = []
  await expect.poll(async () => {
    rows = await messages()
    return predicate(rows)
  }, { timeout, intervals: [500, 1000, 1000] }).toBe(true)
  return rows
}

/** 创建真实会话页面，语言和令牌仅在内存/浏览器内注入。 */
async function openPage(browser: Browser, locale: DocsLocale): Promise<Page> {
  const context = await browser.newContext({ baseURL, viewport: { width: 1920, height: 1080 }, timezoneId: 'Asia/Shanghai', locale })
  const page = await context.newPage()
  await prepareDocsPage(page, { locale, token: adminToken, viewport: { width: 1920, height: 1080 } })
  await page.goto('/admin/base/mqmessage')
  await waitForDocsPage(page, '.card-header')
  return page
}

/** 搜索本批唯一业务键，等待真实响应及加载遮罩消失。 */
async function searchRow(page: Page, locale: DocsLocale, resource: Resource) {
  const field = page.locator('.filter-form .el-form-item').filter({ hasText: label('key', locale) }).locator('input')
  await field.fill(resource.msgKey)
  const response = page.waitForResponse(res => res.url().includes('/api/base/mq-message/list') && res.request().method() === 'GET')
  await page.getByRole('button', { name: label('search', locale), exact: true }).click()
  expect((await response).status()).toBe(200)
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.getByTestId('mq-load-error')).toHaveCount(0)
  const row = page.locator('.el-table__body tr').filter({ hasText: resource.msgKey }).first()
  await expect(row).toBeVisible()
  return row
}

/** 正式原图逐张登记；不允许以列表图顶替详情图。 */
async function capture(page: Page, locale: DocsLocale, name: string) {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.locator('.el-message--error:visible')).toHaveCount(0)
  await captureDocsImage(page, locale, name)
  const sha256 = createHash('sha256').update(readFileSync(join(imageDirectory!, locale, `${name}.png`))).digest('hex')
  expect(captures.some(item => item.sha256 === sha256)).toBe(false)
  captures.push({ locale, name, sha256, capturedAt: new Date().toISOString(), route: new URL(page.url()).pathname,
    actor: 'admin/SUPER_ADMIN', viewport: { width: 1920, height: 1080 }, visualReview: 'pending' })
  save()
}

test.describe('独立教学 Outbox 真实生命周期', () => {
  test.skip(!phase, '通过 E2E_MQ_PHASE 显式选择阶段；准备与清理使用受控教学夹具')
  test.setTimeout(480_000)

  test.beforeAll(async () => {
    expect(existsSync('/.dockerenv')).toBe(true)
    expect(process.env.E2E_RUNTIME_MODE).toBe('external')
    expect(baseURL).toBe('http://omni-frontend:3000')
    expect(adminToken).toBeTruthy()
    expect(['pending', 'retry', 'actions', 'terminal', 'fault', 'operlog']).toContain(phase)
    for (const output of [imageDirectory, registryPath, process.env.E2E_MQ_FIXTURE_FILE, process.env.E2E_MQ_RUNTIME_FILE]) {
      expect(output && isAbsolute(output) && !resolve(output).startsWith('/workspace/')).toBeTruthy()
    }
    expect(existsSync(registryPath!)).toBe(false)
    fixture = JSON.parse(readFileSync(process.env.E2E_MQ_FIXTURE_FILE!, 'utf8').replace(/^\uFEFF/, '')) as Fixture
    expect(fixture.project).toMatch(/^omni-(e2e|wiki-acceptance)-[a-z0-9-]+$/)
    expect(fixture.synthetic).toBe(true)
    expect(fixture.tenantId).toBe(Number(process.env.E2E_TENANT_ID))
    for (const resource of fixture.resources) {
      expect(resource.msgId).toMatch(/^[a-f0-9-]{36}$/)
      expect(resource.msgKey).toBe(`${fixture.prefix}-${resource.locale}-${resource.kind}`)
    }
    runtime = JSON.parse(readFileSync(process.env.E2E_MQ_RUNTIME_FILE!, 'utf8').replace(/^\uFEFF/, ''))
    expect(runtime.project).toBe(fixture.project)
    const containers = runtime.containers as Array<{ service: string; image_id: string; state: string; host_ports: unknown[] }>
    for (const service of ['omni-frontend', 'omni-gateway', 'omni-base', 'omni-auth', 'omni-crm']) {
      const container = containers.find(item => item.service === service)
      expect(container?.state).toBe('running')
      expect(container?.host_ports).toEqual([])
      expect(container?.image_id).toMatch(/^sha256:[a-f0-9]{64}$/)
    }
    if (phase === 'actions' || phase === 'operlog') {
      expect(outsiderToken).toBeTruthy()
    }
    if (phase === 'actions') expect(process.env.E2E_MUTATIONS).toBe('true')
    sourceSha256 = digestSource()
    api = await request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${adminToken}`, 'X-Tenant-Id': String(fixture.tenantId) } })
    save()
  })

  test.afterAll(async () => { await api?.dispose() })

  test('按原回执完成所选阶段并保存真实状态', async ({ browser }) => {
    const pages = await Promise.all(docsLocales.map(locale => openPage(browser, locale)))
    try {
      if (phase === 'pending') {
        expect((await messages()).every(row => row.status === 0 && row.retryCount === 0)).toBe(true)
        await Promise.all(docsLocales.map(async (locale, index) => {
          const resource = fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!
          const row = await searchRow(pages[index], locale, resource)
          await expect(row.getByTestId('mq-status-tag')).toHaveText(label('pending', locale))
          await expect(row.getByTestId('mq-resend-button')).toHaveCount(0)
          await expect(row.getByTestId('mq-skip-button')).toHaveCount(0)
          await capture(pages[index], locale, 'mq-teaching-pending')
        }))
      } else if (phase === 'retry') {
        await Promise.all(docsLocales.map((locale, index) => searchRow(pages[index], locale,
          fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!)))
        observations.push({ at: new Date().toISOString(), event: 'READY_FOR_RELEASE' }); save()
        for (const retryCount of [1, 2]) {
          const rows = await waitMessages(items => items.every(row => row.status === 2 && row.retryCount === retryCount), 90_000)
          for (const row of rows) {
            expect(row.errorMsg).toContain('wiki-unregistered')
            const next = Date.parse(`${row.nextRetryTime?.replace(' ', 'T')}+08:00`)
            const updated = Date.parse(`${row.updateTime?.replace(' ', 'T')}+08:00`)
            expect((next - updated) / 1000).toBe(2 ** retryCount * 10)
          }
          if (retryCount === 1) await Promise.all(docsLocales.map(async (locale, index) => {
            const page = pages[index]
            const row = await searchRow(page, locale, fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!)
            await expect(row.getByTestId('mq-status-tag')).toHaveText(label('failed', locale))
            await expect(row.getByTestId('mq-resend-button')).toBeVisible()
            await expect(row.getByTestId('mq-skip-button')).toHaveCount(0)
            await row.getByTestId('mq-detail-button').click()
            const dialog = page.getByTestId('mq-detail-dialog')
            await expect(dialog.getByTestId('mq-detail-status')).toHaveText(label('failed', locale))
            await expect(dialog.getByTestId('mq-detail-errormsg')).toContainText('wiki-unregistered')
            await capture(page, locale, 'mq-teaching-failed-detail')
            await dialog.getByRole('button', { name: label('close', locale), exact: true }).click()
          }))
        }
        expect((await waitMessages(rows => rows.every(row => row.status === 3 && row.retryCount === 3), 90_000))
          .every(row => row.nextRetryTime === null)).toBe(true)
        await Promise.all(docsLocales.map(async (locale, index) => {
          const page = pages[index]
          const row = await searchRow(page, locale, fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!)
          await expect(row.getByTestId('mq-status-tag')).toHaveText(label('dead', locale))
          await expect(row.getByTestId('mq-resend-button')).toBeVisible()
          await expect(row.getByTestId('mq-skip-button')).toBeVisible()
          await capture(page, locale, 'mq-teaching-dead-letter')
          await row.getByTestId('mq-detail-button').click()
          await expect(page.getByTestId('mq-detail-errormsg')).toContainText('wiki-unregistered')
          await capture(page, locale, 'mq-teaching-dead-detail')
        }))
      } else if (phase === 'actions') {
        const outsider = await request.newContext({ baseURL, extraHTTPHeaders: { Authorization: `Bearer ${outsiderToken}`, 'X-Tenant-Id': String(fixture.tenantId) } })
        try {
          for (const locale of docsLocales) {
            const page = pages[docsLocales.indexOf(locale)]
            for (const kind of ['recover', 'skip'] as const) {
              const resource = fixture.resources.find(item => item.locale === locale && item.kind === kind)!
              const before = (await messages()).find(row => row.msgId === resource.msgId)!
              expect(before.status).toBe(3)
              expect(before.brokerType).toBe(kind === 'recover' ? 'rocketmq' : 'wiki-unregistered')
              const action = kind === 'recover' ? 'resend' : 'skip'
              const forbidden = await outsider.post(`/api/base/mq-message/${resource.msgId}/${action}`)
              expect(forbidden.status()).toBe(403); expect((await forbidden.json()).code).toBe(403)
              observations.push({ at: new Date().toISOString(), msgId: resource.msgId, action, actor: 'outsider', http: 403, code: 403 }); save()
              const row = await searchRow(page, locale, resource)
              await row.getByTestId(`mq-${action}-button`).click()
              const dialog = page.locator('.el-message-box')
              await expect(dialog).toBeVisible()
              await capture(page, locale, `mq-teaching-${action}-confirm`)
              const operation = { msgId: resource.msgId, msgKey: resource.msgKey, action, status: 'SUBMITTING', at: new Date().toISOString() }
              actions.push(operation); save()
              const response = page.waitForResponse(res => res.url().endsWith(`/api/base/mq-message/${resource.msgId}/${action}`) && res.request().method() === 'POST')
              await dialog.getByRole('button', { name: label('confirm', locale), exact: true }).click()
              const result = await response
              expect(result.status()).toBe(200); expect((await result.json()).code).toBe(200)
              operation.status = 'COMMITTED'; save()
              await waitMessages(rows => rows.find(item => item.msgId === resource.msgId)?.status === (kind === 'recover' ? 1 : 4), 60_000)
              const finalRow = await searchRow(page, locale, resource)
              await expect(finalRow.getByTestId('mq-status-tag')).toHaveText(label(kind === 'recover' ? 'sent' : 'skipped', locale))
              await expect(finalRow.getByTestId('mq-resend-button')).toHaveCount(0)
              await expect(finalRow.getByTestId('mq-skip-button')).toHaveCount(0)
              await capture(page, locale, `mq-teaching-${kind === 'recover' ? 'sent' : 'skipped'}`)
              const repeated = await api.post(`/api/base/mq-message/${resource.msgId}/${action}`)
              expect(repeated.status()).toBe(200); expect((await repeated.json()).code).toBe(400)
              observations.push({ at: new Date().toISOString(), msgId: resource.msgId, action: 'terminal-repeat', http: 200, code: 400 }); save()
            }
          }
        } finally { await outsider.dispose() }
      } else if (phase === 'operlog') {
        await messages()
        const params = { page: 1, size: 50, module: fixture.prefix, operUsername: 'wiki_mq_fixture' }
        const response = await api.get('/api/base/oper-log/list', { params })
        expect(response.status()).toBe(200)
        const result = await response.json()
        expect(result.code).toBe(200)
        expect(result.data.total).toBe(4)
        expect(result.data.records.map((row: { requestUrl: string }) => row.requestUrl).sort()).toEqual(
          fixture.resources.filter(item => item.kind === 'recover').map(item => `/fixture/mq/${item.msgId}`).sort())
        const outsider = await request.newContext({ baseURL, extraHTTPHeaders: {
          Authorization: `Bearer ${outsiderToken}`, 'X-Tenant-Id': String(fixture.tenantId),
        } })
        try {
          const allowed = await outsider.get('/api/base/oper-log/list', { params })
          expect(allowed.status()).toBe(200)
          const ordinaryResult = await allowed.json()
          expect(ordinaryResult.code).toBe(200)
          expect(ordinaryResult.data.total).toBe(4)
          // 当前 USER 种子包含操作日志读取权，不把该身份误称为此接口的无权限账号。
          observations.push({ at: new Date().toISOString(), event: 'OPERLOG_SEEDED_USER_READ_ALLOWED', http: 200, code: 200, total: 4 })
          save()
        } finally { await outsider.dispose() }
        await Promise.all(docsLocales.map(async (locale, index) => {
          const page = pages[index]
          await page.goto('/admin/base/operlog')
          await waitForDocsPage(page, '.card-header')
          const fields = page.locator('.filter-form .el-form-item')
          await fields.nth(0).locator('input').fill(fixture.prefix)
          await fields.nth(2).locator('input').fill('wiki_mq_fixture')
          const loaded = page.waitForResponse(res => res.url().includes('/api/base/oper-log/list') && res.url().includes(fixture.prefix))
          await page.getByRole('button', { name: label('search', locale), exact: true }).click()
          expect((await loaded).status()).toBe(200)
          await expect(page.locator('.el-table__body tr')).toHaveCount(4)
          await capture(page, locale, 'mq-teaching-operlog-list')
          const resource = fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!
          await page.locator('.el-table__body tr').filter({ hasText: resource.msgId }).click()
          const dialog = page.getByRole('dialog')
          await expect(dialog).toContainText(`/fixture/mq/${resource.msgId}`)
          await expect(dialog).toContainText('wiki_mq_fixture')
          await capture(page, locale, 'mq-teaching-operlog-detail')
        }))
        observations.push({ at: new Date().toISOString(), event: 'FOUR_UNIQUE_CONSUMER_ROWS_AFTER_REPLAY_UI_PASS' })
        save()
      } else if (phase === 'fault') {
        await messages()
        await Promise.all(docsLocales.map((locale, index) => searchRow(pages[index], locale,
          fixture.resources.find(item => item.locale === locale && item.kind === 'recover')!)))
        observations.push({ at: new Date().toISOString(), event: 'READY_FOR_BASE_STOP' }); save()
        let lastStatus: number | undefined
        await expect.poll(async () => {
          const response = await api.get('/api/base/mq-message/runtime')
          const status = response.status()
          if (status !== lastStatus) {
            observations.push({ at: new Date().toISOString(), event: 'BASE_STOP_STATUS_TRANSITION',
              http: status, traceId: response.headers()['x-trace-id'] ?? null })
            lastStatus = status
            save()
          }
          return status
        }, { timeout: 240_000, intervals: [1000, 2000] }).toBe(503)
        await Promise.all(docsLocales.map(async (locale, index) => {
          const page = pages[index]
          const response = page.waitForResponse(res => res.url().includes('/api/base/mq-message/runtime') && res.request().method() === 'GET')
          await page.getByRole('button', { name: label('search', locale), exact: true }).click()
          const failed = await response
          expect(failed.status()).toBe(503)
          const traceId = failed.headers()['x-trace-id']
          expect(traceId).toMatch(/^[a-f0-9]{32}$/)
          await expect(page.getByTestId('mq-load-error')).toContainText(traceId)
          await expect(page.locator('.el-table')).toHaveCount(0)
          await capture(page, locale, 'mq-teaching-service-unavailable')
          observations.push({ at: new Date().toISOString(), locale, http: 503, traceId, event: 'REAL_BASE_UNAVAILABLE' }); save()
        }))
        observations.push({ at: new Date().toISOString(), event: 'READY_FOR_BASE_RESTART' }); save()
        await expect.poll(async () => (await api.get('/api/base/mq-message/runtime')).status(),
          { timeout: 180_000, intervals: [1000, 2000] }).toBe(200)
        await Promise.all(docsLocales.map(async (locale, index) => {
          const page = pages[index]
          const response = page.waitForResponse(res => res.url().includes('/api/base/mq-message/list') && res.request().method() === 'GET')
          await page.getByTestId('mq-load-error').getByRole('button').click()
          expect((await response).status()).toBe(200)
          await expect(page.getByTestId('mq-load-error')).toHaveCount(0)
          await expect(page.locator('.el-table')).toBeVisible()
          await capture(page, locale, 'mq-teaching-service-recovered')
        }))
        await messages()
        observations.push({ at: new Date().toISOString(), event: 'REAL_BASE_RECOVERY_PASS' }); save()
      } else {
        await waitMessages(rows => rows.every(row => row.status === (fixture.resources.find(item => item.msgId === row.msgId)!.kind === 'skip' ? 4 : 1)), 60_000)
      }
    } finally { await Promise.all(pages.map(page => page.context().close())) }
  })
})
