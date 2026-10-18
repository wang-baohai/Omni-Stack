/** SRM 真实教学取证：独立 USER 入驻、三节点准入、生命周期与门户范围。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'
import zh from '../../omni-frontend/src/locales/zh-CN.ts'
import en from '../../omni-frontend/src/locales/en-US.ts'
import ja from '../../omni-frontend/src/locales/ja-JP.ts'
import ko from '../../omni-frontend/src/locales/ko-KR.ts'
import { captureDocsImage, docsLocales, prepareDocsPage, waitForDocsPage } from '../../omni-frontend/e2e-docs/fixtures/docs-page.ts'

const { chromium, expect } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')
const project = 'omni-wiki-acceptance-20261009'
const runId = 'WIKI-20261012-SRM-E'
const base = 'http://omni-frontend:3000'
const dictionaries = { 'zh-CN': zh, 'en-US': en, 'ja-JP': ja, 'ko-KR': ko }
const sha = value => createHash('sha256').update(value).digest('hex')
/** 所有原图、操作登记和短期身份都必须在仓库外。 */
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), `${name} requires an absolute private path`)
  const child = relative(process.cwd(), resolve(value))
  assert.ok(child === '..' || child.startsWith(`..${sep}`), `${name} must be outside the repository`)
  return resolve(value)
}
assert.ok(existsSync('/.dockerenv'), 'Run inside local Docker')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_SRM_PROJECT, project)
assert.equal(process.env.E2E_SRM_WRITES, 'allow-owned-project')
const phase = process.argv[2]
assert.ok(['enroll', 'approve', 'lifecycle-visual', 'boundary', 'retire'].includes(phase), 'Explicit enroll, approve, lifecycle-visual, boundary or retire phase required')
const teachingPath = outside('E2E_TEACHING_STATE_FILE')
const teaching = JSON.parse(readFileSync(teachingPath, 'utf8').replace(/^\uFEFF/, ''))
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const journalPath = outside('E2E_SRM_STATE_FILE')
const secretsPath = outside('E2E_SRM_SECRETS_FILE')
const runtimePath = outside('E2E_SRM_RUNTIME_FILE')
outside('E2E_DOCS_IMAGE_DIR')
assert.equal(teaching.project, project)
assert.equal(teaching.objects.srmTeaching20261012.runId, runId)
assert.equal(teaching.users.supplierE.username, 'wiki26b_supplier_e')
assert.equal(teaching.users.supplierE.id, teaching.objects.srmTeaching20261012.userId)
assert.equal(teaching.users.supplierE.primaryUnitId, teaching.users.supplierC.primaryUnitId)
const runtime = JSON.parse(readFileSync(runtimePath, 'utf8').replace(/^\uFEFF/, ''))
assert.equal(runtime.project, project)
assert.equal(runtime.containers.length, 24)
assert.ok(runtime.containers.every(x => x.ports === 0), 'Isolation forbids published host ports')
const sourceFiles = ['scripts/fixtures/capture-srm-teaching.mjs', 'omni-frontend/e2e-docs/fixtures/docs-page.ts',
  'omni-frontend/src/router/index.ts', 'omni-frontend/src/utils/access.ts',
  'omni-frontend/src/views/portal-register/index.vue', 'omni-frontend/src/views/supplier-portal/index.vue',
  'omni-frontend/src/views/srm/supplier/index.vue', 'omni-frontend/src/views/srm/invite/index.vue',
  'omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java',
  'omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierServiceImpl.java']
const sources = sourceFiles.map(path => ({ path, sha256: sha(readFileSync(path)) }))
const journal = existsSync(journalPath) ? JSON.parse(readFileSync(journalPath, 'utf8'))
  : { schema: 1, project, runId, baseline: teaching.baseline, started_at: new Date().toISOString(), runtime,
    sources, source_sha256: sha(JSON.stringify(sources)), operations: {}, observations: [], captures: [],
    limits: ['Account prepared by the formal administrator API; no CAPTCHA self-registration or password login is claimed.',
      'The owned supplier is retained for the following uninvited supplier boundary test; final cleanup is pending.'] }
assert.equal(journal.project, project)
assert.equal(journal.runId, runId)
assert.ok(!journal.phase_complete?.includes(phase), 'Successful phases require reconciliation, not replay')
assert.ok(!Object.values(journal.operations).some(x => x.status === 'SUBMITTING'), 'Reconcile unknown submissions before further writes')
journal.attempts ||= []
journal.attempts.push({ phase, started_at: new Date().toISOString(), sources, runtime_snapshot: runtime })
let secrets = existsSync(secretsPath) ? JSON.parse(readFileSync(secretsPath, 'utf8')) : {}
/** 原子落盘，断流不会把已提交操作遗忘。 */
function persist() {
  mkdirSync(dirname(journalPath), { recursive: true })
  journal.updated_at = new Date().toISOString()
  writeFileSync(`${journalPath}.next`, `${JSON.stringify(journal, null, 2)}\n`)
  renameSync(`${journalPath}.next`, journalPath)
}
function persistSecrets() { writeFileSync(secretsPath, JSON.stringify(secrets, null, 2), { mode: 0o600 }) }
function token(actor) {
  const value = tokens[`${actor}Token`]
  assert.equal(typeof value, 'string', `Missing ${actor} short-lived fixture identity`)
  const claims = JSON.parse(Buffer.from(value.split('.')[1], 'base64url').toString())
  assert.ok(claims.exp * 1000 > Date.now() + 60_000, 'Refresh identity rather than bypass expiry')
  if (actor !== 'admin') assert.ok(!(claims.roles || []).some(x => String(x).includes('SUPER_ADMIN')))
  return value
}
/** 日志只记状态、角色和非敏感业务路径，不保存认证头或请求正文。 */
async function api(actor, path, method = 'GET', body, expected = 200) {
  const response = await fetch(`${base}${path}`, { method, headers: { Authorization: `Bearer ${token(actor)}`,
    'X-Tenant-Id': '1', ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30_000) })
  const result = await response.json()
  journal.observations.push({ at: new Date().toISOString(), actor, path, method, http: response.status, code: result.code })
  persist()
  assert.equal(result.code, expected, `${actor} ${method} ${path}: HTTP ${response.status} / business ${result.code}`)
  if (expected === 200) assert.equal(response.status, 200)
  return result.data
}
/** 唯一操作键已经提交时复用回执，不重放业务写入。 */
async function mutate(key, actor, path, method, body) {
  const digest = sha(JSON.stringify({ actor, path, method, body }))
  const old = journal.operations[key]
  if (old) {
    assert.equal(old.digest, digest, `Changed operation input: ${key}`)
    assert.equal(old.status, 'COMMITTED', `Reconcile ${key} before retrying`)
    return old.result
  }
  journal.operations[key] = { digest, status: 'SUBMITTING', at: new Date().toISOString(), actor, path, method }
  persist()
  const result = await api(actor, path, method, body)
  journal.operations[key] = { ...journal.operations[key], status: 'COMMITTED', result, finished_at: new Date().toISOString() }
  persist()
  return result
}
async function poll(fn, description) {
  const deadline = Date.now() + 90_000
  while (Date.now() < deadline) {
    const value = await fn()
    if (value) return value
    await new Promise(done => setTimeout(done, 1500))
  }
  throw new Error(`Timed out: ${description}; reconcile the journal before restarting`)
}
function label(locale, key) {
  let value = dictionaries[locale]
  for (const segment of key.split('.')) value = value?.[segment]
  assert.equal(typeof value, 'string', `Missing ${locale} label ${key}`)
  return value
}
const browser = await chromium.launch()
journal.tools = { node: process.version, playwright: createRequire(resolve('omni-frontend/package.json'))('@playwright/test/package.json').version,
  chromium: browser.version(), loader: 'tsx from the existing CLI dependency volume' }
/** 页面使用真正接口和当前时钟；复用既有语言与遮罩 helper。 */
async function pageFor(actor, locale, route, selector = 'main') {
  const context = await browser.newContext({ baseURL: base, viewport: { width: 1440, height: 1000 },
    timezoneId: 'Asia/Shanghai', reducedMotion: 'reduce', colorScheme: 'light' })
  const page = await context.newPage()
  await prepareDocsPage(page, { locale, token: actor ? token(actor) : undefined, username: teaching.users[actor]?.username })
  await page.goto(route)
  await waitForDocsPage(page, selector)
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  return { page, context }
}
async function capture(page, locale, name, flow, actor, assertion) {
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.locator('.el-button.is-loading:visible')).toHaveCount(0)
  await captureDocsImage(page, locale, name)
  const image = resolve(process.env.E2E_DOCS_IMAGE_DIR, locale, `${name}.png`)
  const roles = actor ? JSON.parse(Buffer.from(token(actor).split('.')[1], 'base64url').toString()).roles : []
  journal.captures.push({ locale, name, flow, actor: actor || 'public', roles,
    route: new URL(page.url()).pathname, viewport: page.viewportSize(), image, sha256: sha(readFileSync(image)),
    captured_at: new Date().toISOString(), assertions: assertion, test_sha256: sources[0].sha256,
    source_sha256: sha(JSON.stringify(sources)),
    runtime_image: runtime.containers.find(x => x.service === 'omni-frontend').image,
    visual_review: 'pending', mocks: false })
  persist()
}
/** 同一业务时点切换四种真实语言，截图不承担 API 状态断言。 */
async function captureAll(name, flow, actor, route, selector, prepare) {
  for (const locale of docsLocales) {
    const prior = journal.captures.find(x => x.locale === locale && x.name === name)
    if (prior) {
      assert.equal(sha(readFileSync(prior.image)), prior.sha256, 'Do not overwrite a successful unchanged capture')
      continue
    }
    const { page, context } = await pageFor(actor, locale, route, selector)
    try { const assertion = await prepare?.(page, locale); await capture(page, locale, name, flow, actor, assertion || {}) }
    finally { await context.close() }
  }
  console.log(`SRM_CAPTURE_PASS ${name} locales=4`)
}
async function supplierCapture(name, flow, supplier, openOverview = false) {
  await captureAll(name, flow, 'srmstaff', '/admin/srm/supplier', 'main', async (page, locale) => {
    const query = page.locator('.search-form input').first()
    if (await query.count()) { await query.fill(supplier.name); await page.getByRole('button', { name: label(locale, 'common.search'), exact: true }).click() }
    const row = page.locator('.el-table__body-wrapper tr').filter({ hasText: supplier.name })
    await expect(row).toHaveCount(1)
    await expect(row).toContainText(label(locale, `srmSupplierPage.status${supplier.status[0]}${supplier.status.slice(1).toLowerCase().replace(/_([a-z])/g, (_, c) => c.toUpperCase())}`))
    if (openOverview) {
      await row.getByRole('button', { name: label(locale, 'srmSupplierPage.overview'), exact: true }).click()
      await expect(page.locator('.el-drawer:visible')).toBeVisible()
      await expect(page.locator('.el-drawer:visible')).toContainText(supplier.status)
    }
    return { supplier_id: supplier.id, status: supplier.status, version: supplier.version, exact_row: true, overview: openOverview }
  })
}
try {
  assert.equal((await fetch(`${base}/login`)).status, 200)
  if (phase === 'enroll') {
    const roles = await api('admin', '/api/auth/role/all')
    const ids = await api('admin', `/api/auth/user/${teaching.users.supplierE.id}/roles`)
    assert.deepEqual(ids.map(String), [String(roles.find(x => x.roleCode === 'USER').id)])
    assert.equal(await api('supplierE', '/api/srm/portal/enrollment'), null)
    await api('supplierE', '/api/srm/portal/profile', 'GET', undefined, 403)
    await captureAll('srm-portal-account-entry', 'portal-register', null, '/portal-register', '.register-card', async page => {
      await expect(page.locator('.captcha-image, .captcha-img').first()).toBeVisible()
      return { scope: 'Public account registration form only; CAPTCHA not solved or submitted' }
    })
    await captureAll('srm-invite-create-form', 'invite', 'srmstaff', '/admin/srm/invite', 'main', async (page, locale) => {
      await page.getByRole('button', { name: label(locale, 'srmInvitePage.create'), exact: true }).click()
      const dialog = page.locator('.el-dialog:visible')
      await dialog.getByRole('spinbutton').nth(0).fill('1'); await dialog.getByRole('spinbutton').nth(1).fill('1')
      return { maxUses: 1, expiresHours: 1, scope: 'Form preview; invitation is created once through the formal API' }
    })
    if (!journal.invite) {
      const invite = await mutate('invite:E', 'srmstaff', '/api/srm/portal/invite', 'POST', { maxUses: 1, expiresHours: 1 })
      secrets.inviteToken = invite.inviteToken; persistSecrets()
      delete journal.operations['invite:E'].result.inviteToken
      journal.invite = { id: invite.id, expiresTime: invite.expiresTime, maxUses: invite.maxUses }; persist()
    }
    const company = { name: `${runId} Vendor E`, creditCode: 'WIKI20261012SRME', supplierType: 'MANUFACTURER', industryCode: 'ELECTRONICS' }
    for (const locale of docsLocales) {
      const { page, context } = await pageFor('supplierE', locale, '/supplier-portal', '.portal-page')
      try {
        const form = page.locator('.enrollment-form')
        await expect(form).toBeVisible()
        await form.locator('input[type=password]').fill(secrets.inviteToken)
        const fill = async (key, value) => form.locator('.el-form-item').filter({ has: page.locator('.el-form-item__label', { hasText: label(locale, `supplierPortal.labels.${key}`) }) }).locator('input').fill(value)
        await fill('companyName', company.name); await fill('creditCode', company.creditCode)
        await fill('industryCode', company.industryCode)
        await form.locator('.el-select').click(); await page.getByRole('option', { name: label(locale, 'supplierPortal.types.MANUFACTURER'), exact: true }).click()
        await capture(page, locale, 'srm-portal-enrollment-form', 'portal-register', 'supplierE', { company: company.name, invite_masked: true, default_role: 'USER', scope: 'Filled form before the one real enrollment' })
      } finally { await context.close() }
    }
    const { page, context } = await pageFor('supplierE', 'zh-CN', '/supplier-portal', '.portal-page')
    try {
      const form = page.locator('.enrollment-form')
      await form.locator('input[type=password]').fill(secrets.inviteToken)
      const items = form.locator('.el-form-item')
      for (const [key, value] of [['companyName', company.name], ['creditCode', company.creditCode], ['industryCode', company.industryCode]]) {
        await items.filter({ has: page.locator('.el-form-item__label', { hasText: label('zh-CN', `supplierPortal.labels.${key}`) }) }).locator('input').fill(value)
      }
      await form.locator('.el-select').click(); await page.getByRole('option', { name: label('zh-CN', 'supplierPortal.types.MANUFACTURER'), exact: true }).click()
      journal.operations['enroll:E'] = { status: 'SUBMITTING', actor: 'supplierE', at: new Date().toISOString() }; persist()
      const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === '/api/srm/portal/enroll' && r.request().method() === 'POST')
      await form.getByRole('button', { name: label('zh-CN', 'supplierPortal.actions.submitEnrollment'), exact: true }).click()
      const response = await responsePromise; const result = await response.json()
      assert.equal(response.status(), 200); assert.equal(result.code, 200)
      secrets.enrollmentRequest = response.request().postDataJSON(); persistSecrets()
      journal.operations['enroll:E'] = { status: 'COMMITTED', actor: 'supplierE', result: result.data, finished_at: new Date().toISOString() }
      journal.enrollment = result.data; persist()
    } finally { await context.close() }
    const duplicate = await api('supplierE', '/api/srm/portal/enroll', 'POST', secrets.enrollmentRequest)
    assert.equal(duplicate.supplierId, journal.enrollment.supplierId)
    journal.enrollment = await poll(async () => { const row = await api('supplierE', '/api/srm/portal/enrollment'); return row?.status === 'COMPLETED' ? row : null }, 'Portal Saga completion')
    journal.idempotency = { same_supplier: true, requestId: journal.enrollment.requestId }
    const assigned = await api('admin', `/api/auth/user/${teaching.users.supplierE.id}/roles`)
    assert.ok(assigned.map(String).includes(String(roles.find(x => x.roleCode === 'SUPPLIER').id)))
    journal.supplier = await api('srmstaff', `/api/srm/supplier/${journal.enrollment.supplierId}`)
    assert.equal(journal.supplier.status, 'APPROVING'); assert.equal(journal.supplier.workflowStartStatus, 'STARTED'); assert.ok(journal.supplier.processInstanceId)
    assert.notEqual(journal.supplier.ownerUserId, teaching.users.supplierE.id)
    await captureAll('srm-portal-enrollment-completed', 'portal-register', 'supplierE', '/supplier-portal', '.portal-page', async page => {
      await expect(page.locator('.enrollment-detail')).toContainText('COMPLETED')
      return { saga: 'COMPLETED', stale_USER_token: true, role_refresh_required: true }
    })
    await supplierCapture('srm-admission-running', 'admission', journal.supplier)
    journal.status = 'ENROLLED_REFRESH_SUPPLIER_IDENTITY_REQUIRED'
  } else if (phase === 'approve') {
    assert.equal(journal.enrollment?.status, 'COMPLETED')
    const claims = JSON.parse(Buffer.from(token('supplierE').split('.')[1], 'base64url').toString())
    assert.ok(claims.roles.includes('SUPPLIER') || claims.roles.includes('ROLE_SUPPLIER'), 'Use a refreshed fixture identity after the Saga role assignment')
    const currentEnrollment = await api('supplierE', '/api/srm/portal/enrollment')
    assert.equal(currentEnrollment.status, 'COMPLETED')
    assert.equal(currentEnrollment.supplierId, journal.supplier.id)
    const currentSupplier = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`)
    assert.equal(currentSupplier.processInstanceId, journal.supplier.processInstanceId, 'Reconcile any changed approval instance before writing')
    assert.equal(currentSupplier.status, 'APPROVING', 'Reconcile any changed business state before writing')
    journal.supplier = currentSupplier
    persist()
    await captureAll('srm-portal-enterprise-reviewing', 'admission', 'supplierE', '/supplier-portal', '.portal-page', async (page, locale) => {
      await expect(page.locator('body')).toContainText(label(locale, 'supplierPortal.statuses.APPROVING'))
      return { supplier_id: journal.supplier.id, status: 'APPROVING', identity: 'USER + SUPPLIER' }
    })
    const pid = journal.supplier.processInstanceId
    journal.approvals ||= []
    for (const actor of ['srmmanager', 'compliance', 'director']) {
      if (journal.approvals.some(x => x.actor === actor)) continue
      const task = await poll(async () => { const data = await api(actor, '/api/workflow/task/todo?page=1&size=100'); return data.records.find(x => x.processInstanceId === pid) }, `${actor} candidate task`)
      if (actor === 'srmmanager') await captureAll('srm-admission-manager-todo', 'admission', actor, '/', 'main', async page => {
        await expect(page.locator('body')).toContainText(journal.supplier.name)
        return { process_instance_id: pid, task_id: task.taskId, candidate: teaching.users[actor].id }
      })
      await mutate(`approve:E:${actor}`, actor, `/api/workflow/approval/${task.taskId}/complete`, 'POST', { approved: true, comment: `${runId} ${actor} teaching approval` })
      journal.approvals.push({ actor, task_id: task.taskId, process_instance_id: pid, approved: true }); persist()
    }
    journal.supplier = await poll(async () => { const row = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`); return row.status === 'APPROVED' ? row : null }, 'Approval completion event')
    if (journal.supplier.ownerUserId !== teaching.users.srmstaff.id) journal.supplier = await mutate('owner:E', 'srmstaff', `/api/srm/supplier/${journal.supplier.id}/transfer`, 'POST', { version: journal.supplier.version, ownerUserId: teaching.users.srmstaff.id })
    journal.supplier = await mutate('category:E', 'srmstaff', `/api/srm/supplier/${journal.supplier.id}`, 'PUT', { version: journal.supplier.version, categoryCode: teaching.objects.category.categoryCode })
    assert.notEqual(journal.supplier.ownerUserId, teaching.users.supplierE.id)
    await supplierCapture('srm-supplier-approved', 'activation', journal.supplier, true)
    await captureAll('srm-portal-enterprise-approved', 'activation', 'supplierE', '/supplier-portal', '.portal-page', async (page, locale) => {
      await expect(page.locator('body')).toContainText(label(locale, 'supplierPortal.statuses.APPROVED'))
      return { status: 'APPROVED', approval_nodes: journal.approvals.length }
    })
    const optionsPath = `/api/procurement/rfq/supplier-options?categoryCode=${encodeURIComponent(teaching.objects.category.categoryCode)}`
    assert.ok((await api('buyer', optionsPath)).some(x => x.id === journal.supplier.id))
    for (const [suffix, status] of [['suspend', 'SUSPENDED'], ['resume', 'APPROVED'], ['blacklist', 'BLACKLISTED'], ['restore-from-blacklist', 'APPROVED']]) {
      const current = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`)
      journal.supplier = await mutate(`lifecycle:E:${suffix}`, 'srmstaff', `/api/srm/supplier/${current.id}/${suffix}`, 'POST', { version: current.version, reason: `${runId} ${suffix}` })
      assert.equal(journal.supplier.status, status)
      await supplierCapture(`srm-lifecycle-${suffix}`, 'lifecycle', journal.supplier)
      const eligible = (await api('buyer', optionsPath)).some(x => x.id === journal.supplier.id)
      assert.equal(eligible, status === 'APPROVED')
      journal.lifecycle ||= []; journal.lifecycle.push({ command: suffix, status, eligible_for_procurement: eligible }); persist()
    }
    await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}?version=${journal.supplier.version}`, 'DELETE', undefined, 409)
    journal.cleanup = { status: 'RETAINED_FOR_NEXT_OWNED_BOUNDARY_TEST', supplier_id: journal.supplier.id, user_id: teaching.users.supplierE.id,
      reason: 'Formal lifecycle and portal history correctly prohibit deletion. No database reset or product-state workaround was used.' }
    journal.status = 'APPROVAL_LIFECYCLE_PASS_CLEANUP_PENDING'
  }
  if (phase === 'lifecycle-visual') {
    assert.ok(journal.phase_complete.includes('approve'), 'Reconcile the completed admission first')
    const optionsPath = `/api/procurement/rfq/supplier-options?categoryCode=${encodeURIComponent(teaching.objects.category.categoryCode)}`
    journal.visual_recapture ||= { reason: 'The fixed action column hides state text in the original list images.', steps: [] }
    for (const [suffix, before, status] of [['suspend', 'APPROVED', 'SUSPENDED'], ['resume', 'SUSPENDED', 'APPROVED'], ['blacklist', 'APPROVED', 'BLACKLISTED'], ['restore-from-blacklist', 'BLACKLISTED', 'APPROVED']]) {
      const name = `srm-lifecycle-${suffix}-detail`
      const key = `lifecycle:E:visual:${suffix}`
      const prior = journal.operations[key]
      const capturesComplete = docsLocales.every(locale => journal.captures.some(x => x.locale === locale && x.name === name))
      if (prior?.status === 'COMMITTED' && capturesComplete && journal.visual_recapture.steps.some(x => x.command === suffix)) continue
      const current = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`)
      assert.equal(current.name, `${runId} Vendor E`)
      assert.equal(current.ownerUserId, teaching.users.srmstaff.id)
      if (prior) {
        assert.equal(prior.status, 'COMMITTED')
        assert.equal(current.status, prior.result.status, 'Cannot recapture a historical state from the current page')
        assert.equal(current.version, prior.result.version, 'Reconcile intervening writes before resuming')
        journal.supplier = current
      } else {
        assert.equal(current.status, before)
        assert.equal(current.version, journal.supplier.version, 'Reconcile intervening writes before resuming')
        journal.supplier = await mutate(key, 'srmstaff', `/api/srm/supplier/${current.id}/${suffix}`, 'POST', { version: current.version, reason: `${runId} visible detail evidence: ${suffix}` })
      }
      assert.equal(journal.supplier.status, status)
      await supplierCapture(name, 'lifecycle', journal.supplier, true)
      const eligible = (await api('buyer', optionsPath)).some(x => x.id === journal.supplier.id)
      assert.equal(eligible, status === 'APPROVED')
      journal.visual_recapture.steps.push({ command: suffix, status, version: journal.supplier.version, eligible_for_procurement: eligible })
      persist()
    }
    journal.status = 'APPROVAL_LIFECYCLE_VISIBLE_PASS_CLEANUP_PENDING'
  }
  if (phase === 'boundary') {
    assert.ok(journal.phase_complete.includes('approve'), 'Admission must have completed before testing invitation isolation')
    const current = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`)
    assert.equal(current.status, 'APPROVED')
    const rfq = await api('buyer', `/api/procurement/rfq/${teaching.objects.rfq.id}`)
    const invited = rfq.suppliers.map(x => x.supplierId)
    assert.ok(!invited.includes(current.id))
    assert.ok(invited.includes(teaching.objects.supplierC.id) && invited.includes(teaching.objects.supplierD.id))
    const profile = await api('supplierE', '/api/srm/portal/profile')
    assert.equal(profile.supplierId, current.id)
    const invitations = await api('supplierE', '/api/srm/portal/quotation/invitations')
    assert.ok(!invitations.some(x => x.rfqId === rfq.id))
    await api('supplierE', `/api/srm/portal/quotation/invitations/${rfq.id}`, 'GET', undefined, 404)
    const invitedDetail = await api('supplierC', `/api/srm/portal/quotation/invitations/${rfq.id}`)
    assert.equal(invitedDetail.rfqId, rfq.id)
    journal.boundary = { supplier_id: current.id, supplier_status: current.status, rfq_id: rfq.id, rfq_status: rfq.status,
      invited_supplier_ids: invited, uninvited_list_excludes_rfq: true, uninvited_detail_code: 404, invited_detail_code: 200,
      business_writes: 0, scope: 'Read isolation for the existing awarded RFQ; no claim about new quotation submission.' }
    persist()
    await captureAll('srm-approved-uninvited-quotation-empty', 'supplier-quotation', 'supplierE', '/supplier-portal', '.portal-page', async (page, locale) => {
      await page.getByRole('tab', { name: label(locale, 'common.srmPortalQuotation'), exact: true }).click()
      await expect(page.locator('.el-empty:visible')).toContainText(label(locale, 'portal.quotation.empty'))
      await expect(page.locator('.el-table__body-wrapper tr:visible')).toHaveCount(0)
      return { supplier_id: current.id, supplier_status: 'APPROVED', not_invited_to_rfq: rfq.id, visible_rows: 0 }
    })
    journal.status = 'ADMISSION_LIFECYCLE_UNINVITED_READ_BOUNDARY_PASS_CLEANUP_PENDING'
  }
  if (phase === 'retire') {
    assert.ok(journal.phase_complete.includes('boundary'), 'Finish the approved/uninvited boundary before retiring this sample')
    const current = await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}`)
    assert.equal(current.name, `${runId} Vendor E`)
    assert.equal(current.ownerUserId, teaching.users.srmstaff.id)
    const retired = journal.operations['retire:E']
    if (retired) {
      assert.equal(retired.status, 'COMMITTED')
      assert.equal(current.status, 'ELIMINATED')
      assert.equal(current.version, retired.result.version)
      journal.supplier = current
    } else {
      assert.equal(current.status, 'APPROVED')
      assert.equal(current.version, journal.supplier.version)
      journal.supplier = await mutate('retire:E', 'srmstaff', `/api/srm/supplier/${current.id}/eliminate`, 'POST', { version: current.version, reason: `${runId} owned teaching sample completed` })
    }
    assert.equal(journal.supplier.status, 'ELIMINATED')
    await supplierCapture('srm-lifecycle-eliminated-detail', 'lifecycle', journal.supplier, true)
    const optionsPath = `/api/procurement/rfq/supplier-options?categoryCode=${encodeURIComponent(teaching.objects.category.categoryCode)}`
    assert.ok(!(await api('buyer', optionsPath)).some(x => x.id === journal.supplier.id))
    await api('srmstaff', `/api/srm/supplier/${journal.supplier.id}/resume`, 'POST', { version: journal.supplier.version, reason: `${runId} terminal-state negative check` }, 409)
    await api('supplierE', '/api/srm/portal/quotation/invitations', 'GET', undefined, 409)
    const invites = await api('srmstaff', '/api/srm/portal/invite/list')
    const invite = invites.find(x => x.id === journal.invite.id)
    assert.ok(invite)
    if (invite.status === 'ACTIVE') await mutate('revoke:invite:E', 'srmstaff', `/api/srm/portal/invite/${invite.id}/revoke`, 'POST')
    const after = (await api('srmstaff', '/api/srm/portal/invite/list')).find(x => x.id === journal.invite.id)
    assert.notEqual(after.status, 'ACTIVE')
    journal.cleanup = { status: 'OWNED_BUSINESS_SAMPLE_RETIRED_HISTORY_RETAINED', supplier_id: journal.supplier.id,
      supplier_status: 'ELIMINATED', invite_id: journal.invite.id, invite_status: after.status, user_id: teaching.users.supplierE.id,
      procurement_eligible: false, terminal_resume_code: 409, quotation_read_code: 409,
      account_cleanup: 'Owned fixture identity remains enabled until final project-wide cleanup; no production account is affected.',
      reason: 'Formal deletion was rejected because lifecycle and Portal history must be retained. No history reset or database deletion.' }
    journal.limits = journal.limits.filter(x => !x.includes('retained for the following uninvited'))
    journal.limits.push('The dedicated sample was eliminated and its invite revoked; immutable history and fixture account remain in the owned isolated project until final cleanup.')
    journal.status = 'SRM_TEACHING_BUSINESS_PASS_HISTORY_RETAINED'
  }
  journal.phase_complete ||= []; journal.phase_complete.push(phase)
  journal.last_failure = journal.failure || journal.last_failure
  delete journal.failure
  journal.attempts.at(-1).finished_at = new Date().toISOString()
  journal.attempts.at(-1).status = 'PASS'
  const latestTeaching = JSON.parse(readFileSync(teachingPath, 'utf8'))
  assert.equal(latestTeaching.users.supplierE.id, teaching.users.supplierE.id)
  latestTeaching.users.supplierE.roleCodes = ['USER', 'SUPPLIER']
  Object.assign(latestTeaching.objects.srmTeaching20261012, { status: journal.status, supplierId: journal.supplier.id,
    processInstanceId: journal.supplier.processInstanceId, finalStatus: journal.supplier.status, enrollmentStatus: journal.enrollment.status })
  latestTeaching.updatedAt = new Date().toISOString()
  writeFileSync(`${teachingPath}.next`, JSON.stringify(latestTeaching, null, 2)); renameSync(`${teachingPath}.next`, teachingPath)
  persist()
  console.log(`SRM_TEACHING_PHASE_PASS ${phase} captures=${journal.captures.length} status=${journal.status}`)
} catch (error) {
  journal.status = 'FAILED_RECONCILE_BEFORE_RETRY'; journal.failure = { at: new Date().toISOString(), phase, error: error.message }
  journal.failures ||= []; journal.failures.push(journal.failure)
  journal.attempts.at(-1).status = 'FAILED'
  journal.attempts.at(-1).finished_at = new Date().toISOString()
  persist()
  throw error
} finally { await browser.close() }
