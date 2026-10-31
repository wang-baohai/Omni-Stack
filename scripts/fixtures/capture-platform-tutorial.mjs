/** 平台图文教程的四语取证：官网、登录入口、工作台、系统、字典、运维、任务与审批。私有不落库。 */
import assert from 'node:assert/strict'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'

const { chromium } = createRequire(resolve('omni-frontend/package.json'))('@playwright/test')

/** 所有原图与短期身份必须在仓库外。 */
function outside(name) {
  const value = process.env[name]
  assert.ok(value && isAbsolute(value), `${name} requires an absolute private path`)
  const child = relative(process.cwd(), resolve(value))
  assert.ok(child === '..' || child.startsWith(`..${sep}`), `${name} must be outside the repository`)
  return resolve(value)
}

assert.ok(existsSync('/.dockerenv'), 'Run inside local Docker')
assert.equal(process.env.E2E_RUNTIME_MODE, 'external')
assert.equal(process.env.E2E_PLATFORM_PROJECT, 'omni-stack')
const base = process.env.E2E_BASE_URL || 'http://omni-frontend:3000'
const output = outside('E2E_DOCS_IMAGE_DIR')
const tokens = JSON.parse(readFileSync(outside('E2E_TEACHING_TOKENS_FILE'), 'utf8'))
const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']

/** 场景与目标文件名一一对应；as 为空表示匿名访问。 */
const scenarios = [
  ['site-home', '/', null], ['login', '/login', null], ['register', '/register', null],
  ['portal-login', '/portal-login', null], ['portal-register', '/portal-register', null],
  ['device', '/device', null], ['consent', '/consent', null],
  ['workspace', '/workspace/overview', 'admin'], ['approvals', '/workspace/approvals', 'admin'],
  ['jobs', '/workspace/jobs', 'admin'], ['users', '/admin/system/user', 'admin'],
  ['roles', '/admin/system/role', 'admin'], ['orgs', '/admin/system/org', 'admin'],
  ['tenant', '/admin/system/tenant', 'admin'], ['oauth2', '/admin/system/oauth2', 'admin'],
  ['audit', '/admin/system/auditlog', 'admin'], ['login-record', '/admin/system/loginrecord', 'admin'],
  ['auth-record', '/admin/system/authrecord', 'admin'], ['online', '/admin/system/online', 'admin'],
  ['config', '/admin/system/config', 'admin'], ['xss', '/admin/system/xssconfig', 'admin'],
  ['dict', '/admin/base/dict', 'admin'], ['operlog', '/admin/base/operlog', 'admin'],
  ['mq-messages', '/admin/base/mqmessage', 'admin'], ['job-types', '/admin/job/user-job-type', 'admin'],
  ['system-jobs', '/admin/job/system-job', 'admin'], ['workflow-models', '/admin/workflow/definition', 'admin'],
  ['workflow-instances', '/admin/workflow/instance', 'admin'],
  ['access-denied', '/admin/system/user', 'employee'], ['not-found', '/this-path-does-not-exist', null],
  ['user-dialog', '/admin/system/user', 'admin'],
]

const browser = await chromium.launch({ args: ['--no-sandbox'] })
for (const locale of locales) {
  const dir = resolve(output, locale)
  mkdirSync(dir, { recursive: true })
  for (const [slug, route, as] of scenarios) {
    const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, locale })
    const token = as ? tokens[as] : null
    assert.ok(!as || token, `missing token for ${as}`)
    await context.addInitScript(([t, l]) => {
      localStorage.setItem('omni-lang', l)
      if (t) localStorage.setItem('token', t)
    }, [token, locale])
    const page = await context.newPage()
    await page.goto(base + route, { waitUntil: 'networkidle', timeout: 60_000 })
    if (slug === 'user-dialog') {
      const create = page.locator('button', { hasText: /新建|新增|New|Create|新規|追加|추가/ }).first()
      if (await create.count()) await create.click()
      await page.waitForTimeout(1200)
    } else {
      await page.waitForTimeout(2500)
    }
    await page.screenshot({ path: resolve(dir, `platform-${slug}.png`) })
    await context.close()
  }
}
await browser.close()
console.log(`captured ${scenarios.length * locales.length} platform tutorial images under ${dirname(resolve(output, 'x'))}`)
