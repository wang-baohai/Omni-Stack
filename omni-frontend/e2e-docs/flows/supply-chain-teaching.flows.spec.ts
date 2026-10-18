/**
 * @module supply-chain-teaching.flows.spec
 * 读取已完成的隔离教学业务记录，验证真实终态并采集四语言图片。
 * 本用例不创建业务数据；操作过程与失败证据由教学运行回执单独记录。
 */
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
  objects: Record<string, unknown>
}

interface Scene {
  image: string
  actor: string
  object: string
  endpoint: string
  route: string
  status: string
  label: string
  module: 'procurement' | 'asset'
  flow: string
  detail?: boolean
}

const stateFile = process.env.E2E_TEACHING_STATE_FILE
const tokenFile = process.env.E2E_TEACHING_TOKENS_FILE
const enabled = Boolean(stateFile || tokenFile)
let teaching: TeachingState
let tokens: Record<string, string>

/** 凭据、教学数据和执行证据只允许使用仓库外的显式文件。 */
function outsideRepository(path: string | undefined, name: string): string {
  if (!path || !isAbsolute(path)) throw new Error(`${name} 必须是仓库外绝对路径`)
  const repository = resolve(process.cwd(), '..')
  const child = relative(repository, resolve(path))
  if (!child || (child !== '..' && !child.startsWith(`..${sep}`))) {
    throw new Error(`${name} 不得位于仓库内`)
  }
  return resolve(path)
}

if (enabled) {
  if (process.env.E2E_RUNTIME_MODE !== 'external' || !existsSync('/.dockerenv')) {
    throw new Error('教学取证必须使用本机 Docker 内的 external 模式')
  }
  teaching = JSON.parse(readFileSync(outsideRepository(stateFile, 'E2E_TEACHING_STATE_FILE'), 'utf8')) as TeachingState
  tokens = JSON.parse(readFileSync(outsideRepository(tokenFile, 'E2E_TEACHING_TOKENS_FILE'), 'utf8')) as Record<string, string>
  if (teaching.schema !== 1 || !/^omni-(?:e2e|wiki-acceptance)-[a-z0-9-]+$/.test(teaching.project)
      || !/^WIKI-[A-Z0-9-]+$/.test(teaching.runId) || !/^[a-f0-9]{40}$/.test(teaching.baseline)) {
    throw new Error('教学回执缺少明确的隔离项目、唯一业务键或源码基线')
  }
  outsideRepository(process.env.E2E_TEACHING_EVIDENCE_PATH, 'E2E_TEACHING_EVIDENCE_PATH')
  outsideRepository(process.env.E2E_DOCS_IMAGE_DIR, 'E2E_DOCS_IMAGE_DIR')
}

const scenes: Scene[] = [
  { image: 'procurement-requisition-approved', actor: 'buyer', object: 'requisition', endpoint: '/api/procurement/requisition', route: '/admin/procurement/requisition', status: 'APPROVED', label: 'title', module: 'procurement', flow: 'requisition-approval', detail: true },
  { image: 'procurement-requisition-rejected', actor: 'buyer', object: 'rejectedRequisition', endpoint: '/api/procurement/requisition', route: '/admin/procurement/requisition', status: 'REJECTED', label: 'title', module: 'procurement', flow: 'requisition-approval', detail: true },
  { image: 'procurement-requisition-cancelled', actor: 'buyer', object: 'cancelledRequisition', endpoint: '/api/procurement/requisition', route: '/admin/procurement/requisition', status: 'CANCELLED', label: 'title', module: 'procurement', flow: 'requisition-approval', detail: true },
  { image: 'procurement-rfq-awarded', actor: 'buyer', object: 'rfq', endpoint: '/api/procurement/rfq', route: '/admin/procurement/rfq', status: 'AWARDED', label: 'title', module: 'procurement', flow: 'rfq', detail: true },
  { image: 'procurement-purchase-order-received', actor: 'buyer', object: 'purchaseOrder', endpoint: '/api/procurement/purchase-order', route: '/admin/procurement/purchase-order', status: 'RECEIVED', label: 'title', module: 'procurement', flow: 'purchase-order', detail: true },
  { image: 'procurement-goods-receipt-fail-nonasset', actor: 'buyer', object: 'receipt1', endpoint: '/api/procurement/goods-receipt', route: '/admin/procurement/goods-receipt', status: 'CONFIRMED', label: 'grNo', module: 'procurement', flow: 'goods-receipt', detail: true },
  { image: 'procurement-goods-receipt-pass', actor: 'buyer', object: 'receipt2', endpoint: '/api/procurement/goods-receipt', route: '/admin/procurement/goods-receipt', status: 'CONFIRMED', label: 'grNo', module: 'procurement', flow: 'goods-receipt', detail: true },
  { image: 'asset-transfer-rejected', actor: 'assetstaff', object: 'transferRejected', endpoint: '/api/asset/transfer', route: '/admin/asset/transfer', status: 'REJECTED', label: 'transferNo', module: 'asset', flow: 'transfer-approval', detail: true },
  { image: 'asset-transfer-completed', actor: 'assetstaff', object: 'transferApproved', endpoint: '/api/asset/transfer', route: '/admin/asset/transfer', status: 'COMPLETED', label: 'transferNo', module: 'asset', flow: 'transfer-approval', detail: true },
  { image: 'asset-disposal-rejected', actor: 'assetstaff', object: 'disposalRejected', endpoint: '/api/asset/disposal', route: '/admin/asset/disposal', status: 'REJECTED', label: 'disposalNo', module: 'asset', flow: 'disposal-approval', detail: true },
  { image: 'asset-disposal-completed', actor: 'assetstaff', object: 'disposalApproved', endpoint: '/api/asset/disposal', route: '/admin/asset/disposal', status: 'COMPLETED', label: 'disposalNo', module: 'asset', flow: 'disposal-approval', detail: true },
  { image: 'asset-scrapped-card', actor: 'assetstaff', object: 'assetCards.1', endpoint: '/api/asset/asset', route: '/admin/asset/asset', status: 'SCRAPPED', label: 'assetNo', module: 'asset', flow: 'ledger', detail: true },
]

for (const locale of docsLocales) {
  test.describe(`隔离教学终态 ${locale}`, () => {
    test.skip(!enabled, '需要显式的仓库外教学回执和短期身份文件')
    for (const scene of scenes) {
      test(`${scene.image}`, async ({ page }) => {
        const user = teaching.users[scene.actor]
        const token = tokens[`${scene.actor}Token`]
        if (!user || !token || user.roleCodes.includes('SUPER_ADMIN')) throw new Error('缺少普通教学身份')
        const claims = JSON.parse(Buffer.from(token.split('.')[1]!, 'base64url').toString()) as { exp: number }
        if (!claims.exp || claims.exp * 1000 < Date.now() + 60_000) throw new Error('教学令牌已过期，请重新签发受控身份')
        const [objectName, index] = scene.object.split('.')
        const stored = index === undefined ? teaching.objects[objectName!] : (teaching.objects[objectName!] as unknown[])[Number(index)]
        const item = stored as Record<string, unknown>
        if (!item?.id || !item[scene.label]) throw new Error(`教学回执缺少 ${scene.object}`)
        const response = await page.request.get(`${scene.endpoint}/${String(item.id)}`, {
          headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': process.env.E2E_TENANT_ID! },
        })
        expect(response.status()).toBe(200)
        const result = await response.json() as { code: number; data: Record<string, unknown> }
        expect(result.code).toBe(200)
        expect(result.data.status).toBe(scene.status)
        expect(String(result.data.id)).toBe(String(item.id))
        if (scene.object === 'purchaseOrder') {
          expect(typeof result.data.totalAmount).toBe('string')
          expect(Number(result.data.totalAmount)).toBe(3500)
        }
        if (scene.object === 'assetCards.1') {
          expect(result.data.activeOperationId).toBeNull()
          expect(typeof result.data.purchaseAmount).toBe('string')
        }
        await prepareDocsPage(page, { locale, token, username: user.username, viewport: { width: 1440, height: 1000 } })
        await page.goto(scene.route)
        await waitForDocsPage(page, 'main')
        const label = String(result.data[scene.label])
        const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: label })
        await expect(row).toHaveCount(1)
        if (scene.detail) {
          await row.getByRole('button', { name: /详情|Details?|詳細|상세/ }).first().click()
          const drawer = page.locator('.el-drawer, .el-dialog').filter({ visible: true }).first()
          await expect(drawer).toBeVisible()
          await expect(drawer).toContainText(label)
        }
        await expect(page).not.toHaveURL(/\/login|menu-load-error/)
        await waitForDocsPage(page, 'main')
        await captureDocsImage(page, locale, scene.image)
        const imagePath = resolve(process.env.E2E_DOCS_IMAGE_DIR!, locale, `${scene.image}.png`)
        const registryPath = outsideRepository(process.env.E2E_TEACHING_EVIDENCE_PATH, 'E2E_TEACHING_EVIDENCE_PATH')
        const registry = existsSync(registryPath) ? JSON.parse(readFileSync(registryPath, 'utf8')) as unknown[] : []
        registry.push({ schema: 1, step: scene.image, module: scene.module, flow: scene.flow, locale,
          actor: scene.actor, roles: user.roleCodes, route: scene.route, viewport: { width: 1440, height: 1000 },
          project: teaching.project, runId: teaching.runId, baseline: teaching.baseline, status: scene.status,
          image: imagePath, sha256: createHash('sha256').update(readFileSync(imagePath)).digest('hex'),
          capturedAt: new Date().toISOString(), mocks: false, visualReview: 'pending',
          test: 'omni-frontend/e2e-docs/flows/supply-chain-teaching.flows.spec.ts',
          testSha256: createHash('sha256').update(readFileSync(resolve('e2e-docs/flows/supply-chain-teaching.flows.spec.ts'))).digest('hex'),
        })
        mkdirSync(dirname(registryPath), { recursive: true })
        writeFileSync(`${registryPath}.next`, JSON.stringify(registry, null, 2))
        renameSync(`${registryPath}.next`, registryPath)
      })
    }
  })
}
