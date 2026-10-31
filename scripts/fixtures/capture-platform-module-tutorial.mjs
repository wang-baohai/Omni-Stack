/** 原位运行版本的平台模块教程实拍：只读、单 worker，不提交表单或更改业务数据。 */
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { chromium } from '/validation/source/omni-frontend/node_modules/playwright/index.mjs'
import { createLocalContext } from './goal-local-identities.mjs'

assert.ok(existsSync('/.dockerenv') && !existsSync('/var/run/docker.sock'))
assert.equal(process.cwd(), '/validation/source')
assert.match(process.env.E2E_RUNTIME_IMAGE_ID ?? '', /^sha256:[a-f0-9]{64}$/)
const run = process.argv[2]; assert.match(run ?? '', /^platform-modules-r[1-9][0-9]*$/)
const changedLabelsOnly = process.argv.includes('--changed-localized-labels-only')
const dir = '/validation/evidence/codex-2026-10-22/wp22-s03/' + run
assert.ok(!existsSync(dir)); mkdirSync(dir, { recursive: true })
const sha = raw => createHash('sha256').update(raw).digest('hex')
const source = readFileSync(new URL(import.meta.url))
writeFileSync(dir + '/source-used.mjs', source)
const report = { original_step: 'WP-22.S03', started_at: new Date().toISOString(), workers: 1,
  script: 'scripts/fixtures/capture-platform-module-tutorial.mjs', script_sha256: sha(source),
  runtime_image: process.env.E2E_RUNTIME_IMAGE_ID, identity_source: 'test-fixture',
  human_captcha_login_claimed: false, authentication_audit_may_be_written: true,
  annotated_get_operations_may_write_audit_and_outbox: true,
  response_mocked: false, display_dom_modified: false, business_write_performed: false,
  capture_scope: changedLabelsOnly ? 'Affected localized labels and four user forms; remaining scenes retain their original receipts' : 'All twenty scenes in all four locales',
  images: [], checks: [], page_errors: [], api_failures: [], blocked_mutations: [] }
const save = () => writeFileSync(dir + '/result.json', JSON.stringify(report, null, 2) + '\n')
const scenes = [
  ['users', '/admin/system/user'], ['roles', '/admin/system/role'], ['orgs', '/admin/system/org'],
  ['tenant', '/admin/system/tenant'], ['oauth2', '/admin/system/oauth2'], ['audit', '/admin/system/auditlog'],
  ['login-record', '/admin/system/loginrecord'], ['auth-record', '/admin/system/authrecord'],
  ['online', '/admin/system/online'], ['config', '/admin/system/config'], ['xss', '/admin/system/xssconfig'],
  ['dict', '/admin/base/dict'], ['operlog', '/admin/base/operlog'], ['mq-messages', '/admin/base/mqmessage'],
  ['job-types', '/admin/job/user-job-type'], ['system-jobs', '/admin/job/system-job'],
  ['workflow-models', '/admin/workflow/definition'], ['workflow-instances', '/admin/workflow/instance'],
  ['user-dialog', '/admin/system/user'], ['access-denied', '/admin/system/user'],
]
const localizedScenes = ['orgs', 'tenant', 'audit', 'job-types', 'system-jobs', 'workflow-models', 'workflow-instances']
const expectedLabels = {
  'ja-JP': { orgs: ['組織', '部門'], tenant: ['ドメイン', '初期化状態', '準備完了'],
    audit: ['ユーザー名', '説明', '操作者', '日時'], 'job-types': ['説明'],
    'system-jobs': ['説明', '状態', '実行中', '停止', '登録解除'],
    'workflow-models': ['有効', '停止'], 'workflow-instances': ['完了', '終了'] },
  'ko-KR': { orgs: ['조직', '부서'], tenant: ['도메인', '초기화 상태', '준비 완료'],
    audit: ['사용자 이름', '설명', '작업자', '시간'], 'job-types': ['설명'],
    'system-jobs': ['설명', '상태', '실행 중', '중지', '등록 해제'],
    'workflow-models': ['활성', '중지'], 'workflow-instances': ['완료', '종료됨'] },
}
const local = createLocalContext(), admin = local.bootstrap(), employee = await local.login('employee')
const browser = await chromium.launch({ args: ['--no-sandbox'] })
async function privacyMasks(page, scene) {
  // 用户弹窗使用页面原生脱敏，额外遮罩背景单元格会挡住前景表单。
  if (scene === 'user-dialog') {
    let fields = 0
    for (const th of await page.locator('.el-table__header-wrapper th.el-table__cell').all()) {
      if (!/邮箱|电话|手机|Email|Phone|メール|電話|이메일|전화/i.test(await th.innerText())) continue
      const column = (await th.getAttribute('class')).split(/\s+/).find(value => /^el-table_\d+_column_\d+$/.test(value))
      assert.ok(column); fields++
      const cells = await page.locator('.el-table__body-wrapper td.' + column + ' .cell').allTextContents()
      assert.ok(cells.every(value => value.trim() === '-' || value.includes('***')), '联系方式须保持页面原生脱敏')
    }
    assert.equal(fields, 2)
    return { locators: [], descriptions: [] }
  }
  if (scene === 'online') {
    const th = page.locator('.el-table__header-wrapper th.el-table__cell').filter({ hasText: /Token ID|トークン ID|토큰 ID/ }).first()
    const column = (await th.getAttribute('class')).split(/\s+/).find(value => /^el-table_\d+_column_\d+$/.test(value))
    assert.ok(column, '会话标识必须定位到真实的数据列')
    const selector = '.el-table__body-wrapper td.' + column + ' .cell'
    return { locators: [page.locator(selector)], descriptions: [{ field: 'session_identifier', selector,
      reason: 'Mask active session identifiers; keep the real table header and actions visible' }] }
  }
  if (scene !== 'users') return { locators: [], descriptions: [] }
  const locators = [], descriptions = []
  for (const th of await page.locator('.el-table__header-wrapper th.el-table__cell').all()) {
    const label = (await th.innerText()).trim()
    if (!/邮箱|电话|手机|Email|Phone|メール|電話|이메일|전화/i.test(label)) continue
    const column = (await th.getAttribute('class')).split(/\s+/).find(value => /^el-table_\d+_column_\d+$/.test(value))
    assert.ok(column, 'Contact column must have an actual Element Plus column class')
    locators.push(page.locator('.el-table__body-wrapper td.' + column + ' .cell'))
    descriptions.push({ field: /邮箱|Email|メール|이메일/i.test(label) ? 'email' : 'phone',
      selector: '.el-table__body-wrapper td.' + column + ' .cell', reason: 'Protect real shared contact values; header and UI remain unchanged' })
  }
  assert.equal(descriptions.length, 2, 'Email and phone data columns must both be masked')
  return { locators, descriptions }
}
try {
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    for (const [scene, route] of scenes) {
      // 定向复拍只替换实际有变的十八图；不改变正式覆盖清单或旧图的取证版本。
      if (changedLabelsOnly && scene !== 'user-dialog' && !(expectedLabels[locale] && localizedScenes.includes(scene))) continue
      const denied = scene === 'access-denied', actor = denied ? 'employee' : 'admin'
      const token = denied ? employee : admin, username = denied ? local.state.accounts.employee.username : 'admin'
      const context = await browser.newContext({ locale, viewport: { width: 1920, height: 1080 } })
      context.setDefaultTimeout(20000)
      context.on('page', page => {
        page.on('pageerror', error => { report.page_errors.push({ locale, scene, error: error.message }); save() })
        page.on('response', response => {
          if (response.url().includes('/api/') && response.status() >= 400) {
            report.api_failures.push({ locale, scene, path: new URL(response.url()).pathname, status: response.status(), expected: denied }); save()
          }
        })
      })
      await context.route('**/api/**', async request => {
        if (['GET', 'HEAD', 'OPTIONS'].includes(request.request().method())) return request.continue()
        report.blocked_mutations.push({ locale, scene, method: request.request().method(), path: new URL(request.request().url()).pathname })
        save(); return request.abort()
      })
      await context.addInitScript(({ token, username, locale }) => {
        // 初始 about:blank 没有可用存储；仅在被测应用的真实源写入受控测试会话。
        if (location.origin !== 'http://omni-frontend:3000') return
        localStorage.setItem('token', token); localStorage.setItem('username', username)
        localStorage.setItem('omni-lang', locale); localStorage.setItem('omni-theme', 'light')
      }, { token, username, locale })
      const page = await context.newPage()
      await page.goto('http://omni-frontend:3000' + route, { waitUntil: 'networkidle' })
      if (denied) {
        await page.locator('.status-page .el-result__title').filter({ hasText: /^403\s/ }).waitFor()
        assert.equal(await page.locator('.status-page .el-result__extra').count(), 0)
        assert.equal(await page.locator('a[href="/"],a[href^="/workspace/"]').count(), 0)
        assert.equal(await page.locator('aside .el-menu-item').count(), 0, '私有拒绝页不得露出仪表盘兜底菜单')
      } else {
        assert.equal(new URL(page.url()).pathname, route)
        await page.locator(scene === 'config' ? '.el-form' : '.el-table, .el-tree').first().waitFor()
        assert.equal(await page.locator('.console-header__apps').count(), 0)
        assert.equal(await page.locator('header a[href="/"],header a[href^="/workspace/"]').count(), 0)
        await page.locator('.console-header__search').waitFor()
        if (scene === 'user-dialog') {
          const create = page.locator('button').filter({ hasText: /新建|新增|New|Create|新規|追加|추가/ }).first()
          await create.click(); await page.locator('.el-dialog:visible').waitFor()
          assert.equal(await page.locator('.el-dialog:visible input[type="password"]').inputValue(), '')
        }
      }
      if (await page.locator('.el-notification').count()) await page.locator('.el-notification').last().waitFor({ state: 'hidden' })
      await page.evaluate(() => document.fonts.ready)
      await page.waitForTimeout(350)
      const labels = expectedLabels[locale]?.[scene]
      if (labels) {
        const body = await page.locator('body').innerText()
        for (const label of labels) assert.ok(body.includes(label), `${locale}/${scene} missing visible localized label: ${label}`)
        assert.equal(await page.getByText(/^(Organization|Department|Domain|Initialization|Ready|Username|Description|Operator|Time|Status|Running|Stop|Unregister|Active|Suspend|Completed|Terminated)$/, { exact: true }).count(), 0,
          '目标语言可见字段、状态和按钮不得回退英文')
      }
      if (scene === 'user-dialog') {
        const bounds = await page.locator('.el-dialog:visible .el-form-item').evaluateAll(items => items.map(item => {
          const label = item.querySelector('.el-form-item__label').getBoundingClientRect()
          const input = item.querySelector('.el-input, .el-select').getBoundingClientRect()
          return { labelHeight: label.height, inputHeight: input.height, inputWidth: input.width }
        }))
        assert.equal(bounds.length, 6)
        assert.ok(bounds.every(row => row.labelHeight <= row.inputHeight + 1 && row.inputWidth >= 180), '长标签须保持单行且输入控件仍可读')
      }
      const masks = await privacyMasks(page, scene)
      const image = `${dir}/platform-${scene}-${locale}.png`
      await page.screenshot({ path: image, mask: masks.locators, maskColor: '#ccd4df' })
      writeFileSync(`${dir}/${scene}-${locale}-body.txt`, await page.locator('body').innerText())
      report.images.push({ path: image, name: 'platform-' + scene, locale, role: actor,
        requested_route: route, route: new URL(page.url()).pathname + new URL(page.url()).hash,
        viewport: page.viewportSize(), captured_at: new Date().toISOString(), sha256: sha(readFileSync(image)),
        masks: masks.descriptions, actual_visual_review: 'PENDING', business_write_performed: false })
      report.checks.push({ locale, scene, result: 'PASS_ACTUAL_PAGE_AND_MODULE_HEADER_BOUNDARY',
        limit: 'Read-only page or unsubmitted form; not CRUD, approval or authentication acceptance' }); save()
      await context.close()
    }
  }
  assert.equal(report.images.length, changedLabelsOnly ? 18 : 80)
  assert.deepEqual(report.page_errors, []); assert.deepEqual(report.blocked_mutations, [])
  assert.deepEqual(report.api_failures.filter(row => !row.expected), [])
  report.result = `CAPTURED_${report.images.length}_REAL_MODULE_IMAGES_PENDING_ACTUAL_AI_VIEW`
} catch (error) { report.result = 'FAIL'; report.error = error.message; process.exitCode = 1 }
finally { report.finished_at = new Date().toISOString(); save(); await browser.close() }
console.log(JSON.stringify({ result: report.result, images: report.images.length, evidence: dir, error: report.error?.slice(0, 360) }))
