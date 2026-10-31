/** CRM 图文教程取证：基础页、空/拒绝/校验与业务链路结果。私有不落库。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'

const { chromium, expect } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), `${name} requires an absolute private path`)
  const child = relative(process.cwd(), resolve(value))
  assert.ok(child === '..' || child.startsWith(`..${sep}`), `${name} must be outside the repository`)
  return resolve(value)
}

assert.ok(existsSync('/.dockerenv'), 'Run inside local Docker')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_CRM_PROJECT, 'omni-stack')
const base = process.env.E2E_BASE_URL || 'http://omni-frontend:3000'
const output = outside('E2E_DOCS_IMAGE_DIR')
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const state = JSON.parse(readFileSync(outside('E2E_CRM_TUTORIAL_STATE'), 'utf8'))
const keyword = state.keyword || 'WP25-LINK'
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']
const runtimeImage = process.env.E2E_RUNTIME_IMAGE_ID
assert.match(runtimeImage || '', /^sha256:[a-f0-9]{64}$/, 'Bind screenshots to the actual inspected frontend image')
const resultFile = resolve(output, 'capture-result.json')
mkdirSync(output, { recursive: true })
assert.ok(!existsSync(resultFile), 'Use a fresh private evidence directory; do not overwrite an earlier capture run')
const result = { started_at: new Date().toISOString(), identity_source: 'test-fixture', workers: 1,
  runtime_image_id: runtimeImage, capture_script_sha256: createHash('sha256').update(readFileSync(new URL(import.meta.url))).digest('hex'), shots: [] }
const save = () => writeFileSync(resultFile, JSON.stringify(result, null, 2) + '\n')
save()

/** 令牌只用于当前测试身份；签名与权限仍由真实网关和接口校验。 */
function identity(token, role) {
  const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))
  assert.equal(String(claims.tenant_id), '1', 'Use the explicitly authorized local development tenant')
  assert.ok(Number.isSafeInteger(Number(claims.sub)) && Number(claims.sub) > 0)
  assert.ok(typeof claims.username === 'string' && claims.username.length > 0)
  assert.ok(Date.now() < Number(claims.exp) * 1000, 'Refresh the fixture identity instead of capturing an expired session')
  assert.ok(Array.isArray(claims.roles))
  if (role === 'employee') assert.ok(claims.roles.includes('EMPLOYEE'))
  else assert.ok(claims.roles.some((value) => ['SUPER_ADMIN', 'ADMIN', 'CRM_ADMIN'].includes(value)))
  return { user_id: Number(claims.sub), username: claims.username, tenant_id: Number(claims.tenant_id), roles: claims.roles }
}

/** 只读检查真实标签的文字边界和遮挡，DOM有尺寸不能代替截图中实际可读。 */
async function readableLabel(locator, description) {
  await expect(locator).toHaveCount(1)
  await expect(locator).toBeVisible()
  const measure = () => locator.evaluate((element) => {
    const box = element.getBoundingClientRect()
    const cell = element.closest('td')?.getBoundingClientRect()
    const viewport = element.closest('.el-table__body-wrapper')?.getBoundingClientRect()
    const range = document.createRange()
    range.selectNodeContents(element)
    const text = range.getBoundingClientRect()
    const hits = [0.15, 0.5, 0.85].map((fraction) => {
      const hit = document.elementFromPoint(box.left + box.width * fraction, box.top + box.height / 2)
      return Boolean(hit && (element === hit || element.contains(hit)))
    })
    return { text: element.textContent.trim(), left: box.left, right: box.right,
      text_left: text.left, text_right: text.right, cell_left: cell?.left, cell_right: cell?.right,
      viewport_left: viewport?.left ?? 0, viewport_right: viewport?.right ?? innerWidth, unobscured: hits.every(Boolean) }
  })
  let measured
  const samples = []
  try {
    // 抽屉仍在真实入场动画中时不能采图；等到实际文字稳定且没有被遮挡。
    await expect.poll(async () => {
      measured = await measure()
      samples.push({ ...measured, at: new Date().toISOString() })
      return measured.unobscured && measured.left >= measured.viewport_left - 1
        && measured.right <= measured.viewport_right + 1
        && (measured.cell_left === undefined
          || (measured.text_left >= measured.cell_left - 1 && measured.text_right <= measured.cell_right + 1))
    }, { timeout: 5000, message: `${description}: wait for the real text to be readable and unobscured` }).toBe(true)
  } catch (error) {
    result.readability_failure = { description, samples }
    save()
    throw error
  }
  result.readability_checks ??= []
  result.readability_checks.push({ description, samples })
  assert.ok(measured.unobscured, `${description}: text is covered by another element`)
  assert.ok(measured.left >= measured.viewport_left - 1 && measured.right <= measured.viewport_right + 1,
    `${description}: label is outside the actual table viewport`)
  if (measured.cell_left !== undefined) {
    assert.ok(measured.text_left >= measured.cell_left - 1 && measured.text_right <= measured.cell_right + 1,
      `${description}: complete text must fit the actual cell`)
  }
  return measured
}

const leadId = Number(state.leadId)
const customerId = Number(state.conversion?.customerId)
const opportunityId = Number(state.conversion?.opportunityId)
const completedActivityId = Number(state.completedActivityId)
for (const id of [leadId, customerId, opportunityId, completedActivityId]) {
  assert.ok(Number.isSafeInteger(id) && id > 0, 'The private teaching state must bind actual lead, customer, opportunity and completed-activity IDs')
}

/** 基础页与负例；结果页在业务链路完成后由同一脚本按关键词搜索。 */
const scenes = [
  { id: 'crm-overview', route: '/admin/crm/overview', as: 'admin' },
  { id: 'crm-leads', route: '/admin/crm/lead', as: 'admin' },
  { id: 'crm-lead-dialog', route: '/admin/crm/lead', as: 'admin', dialog: true },
  { id: 'crm-lead-validation', route: '/admin/crm/lead', as: 'admin', dialog: true, submit: true },
  { id: 'crm-customers', route: '/admin/crm/customer', as: 'admin' },
  { id: 'crm-customer-360', route: '/admin/crm/customer', as: 'admin', object: 'customer', open: /360/ },
  { id: 'crm-contacts', route: '/admin/crm/contact', as: 'admin' },
  { id: 'crm-contacts-primary-actions', flow: 'crm-contacts', route: '/admin/crm/contact', as: 'admin', focusPrimary: true },
  { id: 'crm-opportunities', route: '/admin/crm/opportunity', as: 'admin' },
  { id: 'crm-opportunity-won', route: '/admin/crm/opportunity', as: 'admin', object: 'opportunity' },
  { id: 'crm-activities', route: '/admin/crm/activity', as: 'admin' },
  { id: 'crm-activity-completed', route: '/admin/crm/activity', as: 'admin', object: 'activity' },
  { id: 'crm-lead-converted', route: '/admin/crm/lead', as: 'admin', object: 'lead' },
  { id: 'crm-lead-empty', route: '/admin/crm/lead', as: 'admin', search: 'ZZZ-NO-SUCH-LEAD' },
  { id: 'crm-employee-403', route: '/admin/crm/lead', as: 'employee' },
]

const browser = await chromium.launch({ args: ['--no-sandbox'] })
try {
const preflight = await browser.newContext()
const adminIdentity = identity(tokens.adminToken, 'admin')
const headers = { Authorization: `Bearer ${tokens.adminToken}`, 'X-Tenant-Id': String(adminIdentity.tenant_id) }
const objects = {}
for (const [name, id] of [['lead', leadId], ['customer', customerId], ['opportunity', opportunityId], ['activity', completedActivityId]]) {
  const response = await preflight.request.get(`${base}/api/crm/${name}/${id}`, { headers })
  assert.equal(response.status(), 200)
  const body = await response.json()
  assert.equal(body.code, 200, `The actual ${name} must be readable`)
  assert.equal(Number(body.data.id), id)
  objects[name] = body.data
  const actualName = name === 'lead' ? body.data.fullName : name === 'activity' ? body.data.subject : body.data.name
  assert.ok(actualName.includes(keyword), 'Only the explicitly tracked teaching namespace may be captured as a result')
}
assert.equal(objects.lead.status, 'CONVERTED')
assert.equal(objects.opportunity.status, 'WON')
assert.equal(Number(objects.opportunity.customerId), customerId)
assert.equal(objects.activity.status, 'COMPLETED')
assert.equal(objects.activity.rootType, 'CUSTOMER')
assert.equal(Number(objects.activity.rootId), customerId)
assert.ok(objects.activity.completedTime, 'A completed activity needs its actual completion time')
result.objects = { lead_id: leadId, customer_id: customerId, opportunity_id: opportunityId, completed_activity_id: completedActivityId }
result.preflight_identity = adminIdentity
save()
await preflight.close()
for (const locale of locales) {
  const dir = resolve(output, locale)
  mkdirSync(dir, { recursive: true })
  for (const scene of scenes) {
    const token = tokens[`${scene.as}Token`]
    assert.ok(token, `missing ${scene.as}Token`)
    const actor = identity(token, scene.as)
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale })
    const page = await context.newPage()
    await page.goto(base + '/', { waitUntil: 'networkidle', timeout: 60_000 })
    await page.evaluate(([t, l, username]) => {
      localStorage.setItem('omni-lang', l); localStorage.setItem('token', t); localStorage.setItem('username', username)
    }, [token, locale, actor.username])
    await page.goto(base + scene.route, { waitUntil: 'networkidle', timeout: 60_000 })
    await page.waitForTimeout(2000)
    await expect(page.locator('.console-header__username')).toHaveText(actor.username)
    let denial
    if (scene.as === 'employee') {
      await expect(page.locator('.el-result__title')).toHaveText(/^403\s/)
      const response = await context.request.get(`${base}/api/crm/lead/list?page=1&size=1`, {
        headers: { Authorization: `Bearer ${token}`, 'X-Tenant-Id': String(actor.tenant_id) },
      })
      const body = await response.json()
      assert.ok(response.status() === 403 || (response.status() === 200 && body.code === 403), 'The actual employee must also be refused by the server')
      assert.ok(!body.data?.records, 'A refusal must not include CRM rows')
      denial = { http_status: response.status(), business_code: body.code ?? null }
    } else {
      assert.equal(new URL(page.url()).pathname, scene.route, 'Do not capture an unexpected login or error redirect as success')
      await expect(page.locator('.el-card').first()).toBeVisible()
    }
    let rowText
    const target = scene.object ? objects[scene.object] : null
    const search = scene.search || (scene.object === 'lead' ? target.fullName : scene.object && scene.object !== 'activity' ? target.name : null)
    if (search) {
      const input = page.locator('input[placeholder]').first()
      await input.fill(search)
      const response = page.waitForResponse((res) => new URL(res.url()).pathname.endsWith('/list') && res.url().includes('keyword='))
      await page.getByRole('button', { name: /^(搜索|Search|検索|검색)$/ }).click()
      const searched = await response
      const payload = await searched.json()
      assert.equal(payload.code, 200, 'Search must really succeed')
      if (scene.id === 'crm-lead-empty') {
        assert.equal(payload.data.total, 0)
        await expect(page.locator('.el-table__empty-text')).toBeVisible()
      } else {
        assert.ok(payload.data.total > 0, 'A result screenshot requires the actual matching business record')
        assert.ok(payload.data.records.some((record) => Number(record.id) === Number(target.id)))
        rowText = search
        await expect(page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: rowText }).first()).toBeVisible()
      }
    }
    if (scene.object === 'activity') {
      const rootLabels = { 'zh-CN': '客户', 'en-US': 'Customer', 'ja-JP': '顧客', 'ko-KR': '고객' }
      const completedLabels = { 'zh-CN': '已完成', 'en-US': 'Completed', 'ja-JP': '完了', 'ko-KR': '완료' }
      await page.locator('.search-form .el-select').nth(0).click()
      await page.getByRole('option', { name: rootLabels[locale], exact: true }).click()
      await page.locator('.search-form .el-input-number input').fill(String(customerId))
      await page.locator('.search-form .el-input-number input').press('Tab')
      await page.locator('.search-form .el-select').nth(1).click()
      await page.getByRole('option', { name: completedLabels[locale], exact: true }).click()
      const response = page.waitForResponse((res) => {
        const url = new URL(res.url())
        return url.pathname.endsWith('/crm/activity/list') && url.searchParams.get('rootType') === 'CUSTOMER'
          && url.searchParams.get('rootId') === String(customerId) && url.searchParams.get('status') === 'COMPLETED'
      })
      await page.getByRole('button', { name: /^(搜索|Search|検索|검색)$/ }).click()
      const payload = await (await response).json()
      assert.equal(payload.code, 200)
      assert.ok(payload.data.records.some((record) => Number(record.id) === completedActivityId && record.status === 'COMPLETED'))
      rowText = target.subject
      await expect(page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: rowText })).toHaveCount(1)
    }
    if (scene.dialog) {
      await page.locator('.card-header').getByRole('button', { name: /^(新建线索|Create Lead|リードを作成|리드 추가)$/ }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
    }
    if (scene.submit) {
      const dialog = page.getByRole('dialog')
      await dialog.getByRole('button', { name: /^(保存|Save|저장)$/ }).click()
      await expect(dialog.locator('.el-form-item__error').first()).toBeVisible()
    }
    if (scene.open) {
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: rowText }).first()
      await row.getByRole('button', { name: scene.open }).click()
      const drawer = page.getByRole('dialog', { name: 'Customer 360', exact: true })
      await expect(drawer).toBeVisible()
      await expect(drawer.locator('.customer-overview')).toBeVisible()
      await expect(drawer.getByText(target.name, { exact: true })).toBeVisible()
      const activeLabels = { 'zh-CN': '活跃客户', 'en-US': 'Active', 'ja-JP': '有効な顧客', 'ko-KR': '활성 고객' }
      assert.equal(target.status, 'ACTIVE')
      await readableLabel(drawer.getByText(activeLabels[locale], { exact: true }), `${locale} customer 360 status`)
    }
    const expectedStatus = {
      'crm-lead-converted': { 'zh-CN': '已转换', 'en-US': 'Converted', 'ja-JP': '転換済み', 'ko-KR': '전환됨' },
      'crm-opportunity-won': { 'zh-CN': '赢单', 'en-US': 'Won', 'ja-JP': '受注', 'ko-KR': '수주' },
      'crm-activity-completed': { 'zh-CN': '已完成', 'en-US': 'Completed', 'ja-JP': '完了', 'ko-KR': '완료' },
    }[scene.id]?.[locale]
    let statusExposure
    let completionTimeExposure
    if (expectedStatus) {
      const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: rowText }).first()
      const status = row.locator('.el-tag').filter({ hasText: expectedStatus })
      await expect(status).toHaveCount(1)
      await expect(status).toHaveText(expectedStatus)
      const scroll = page.locator('.el-table__body-wrapper .el-scrollbar__wrap').first()
      await scroll.hover()
      await page.mouse.wheel(10000, 0)
      await page.waitForTimeout(500)
      statusExposure = await readableLabel(status, `${locale} ${scene.id} status`)
      if (scene.object === 'activity') {
        completionTimeExposure = await readableLabel(row.getByRole('cell', { name: target.completedTime, exact: true }),
          `${locale} completed activity actual time`)
      }
    }
    if (scene.id === 'crm-customers' || scene.id === 'crm-activities') {
      await readableLabel(page.locator('.el-table__body-wrapper tbody tr').first().locator('.el-tag').first(),
        `${locale} ${scene.id} first status`)
    }
    let primaryButton
    if (scene.focusPrimary) {
      const scroll = page.locator('.el-table__body-wrapper .el-scrollbar__wrap').first()
      await scroll.hover(); await page.mouse.wheel(10000, 0); await page.waitForTimeout(500)
      const button = page.getByRole('button', { name: /^(设为主要|Set as Primary|主要に設定|주 연락처로 설정)$/ }).first()
      await expect(button).toBeVisible()
      primaryButton = await button.evaluate((element) => {
        const box = element.getBoundingClientRect(), cell = element.closest('td').getBoundingClientRect()
        return { text: element.textContent.trim(), left: box.left, right: box.right, column_left: cell.left, column_right: cell.right }
      })
      assert.ok(primaryButton.left >= primaryButton.column_left && primaryButton.right <= primaryButton.column_right,
        'The complete primary-contact action label must fit its column')
    }
    await page.evaluate(() => document.fonts.ready)
    await page.waitForTimeout(1200)
    const image = resolve(dir, `${scene.id}.png`)
    assert.ok(!existsSync(image), 'Do not overwrite an earlier screenshot')
    await page.screenshot({ path: image })
    result.shots.push({ id: scene.id, flow: scene.flow || scene.id, locale, role: scene.as, identity_source: 'test-fixture', route: scene.route,
      identity: actor, refusal: denial, object_id: target?.id ?? null,
      primary_button: primaryButton,
      status_exposure: statusExposure, completion_time_exposure: completionTimeExposure,
      final_url: page.url(), viewport: page.viewportSize(), image, captured_at: new Date().toISOString(),
      sha256: createHash('sha256').update(readFileSync(image)).digest('hex'), result: 'PASS' })
    save()
    await context.close()
  }
}
result.finished_at = new Date().toISOString()
result.result = 'PASS'
save()
console.log(`captured ${scenes.length * locales.length} crm tutorial images under ${dirname(resolve(output, 'x'))}`)
} catch (error) {
  result.result = 'FAIL'
  result.error = error.message
  save()
  throw error
} finally {
  await browser.close()
}
