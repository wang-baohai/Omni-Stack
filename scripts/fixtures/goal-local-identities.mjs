import assert from 'node:assert/strict'
import { createHash, randomBytes } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { chmodSync, existsSync, lstatSync, mkdirSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

// 本地受控测试入口：不在宿主执行，不创建业务栈或 HTTP 服务，秘密只进入固定验证卷。
const source = '/validation/source'
export const privateRoot = '/validation/test-fixtures/goal-20261022'
const evidenceRoot = '/validation/evidence/codex-2026-10-22/wp07-s03'
const stateFile = resolve(privateRoot, 'identities.json')
const configFile = resolve(privateRoot, 'runtime-secrets.json')
const sha256 = (value) => createHash('sha256').update(value).digest('hex')
const specs = [
  ['employee', 'EMPLOYEE', 'a'], ['salesRep', 'SALES_REP', 'a'], ['salesPeer', 'SALES_REP', 'b'],
  ['salesManager', 'SALES_MANAGER', 'a'], ['crmViewer', 'CRM_VIEWER', 'a'],
  ['procStaff', 'PROCUREMENT_STAFF', 'a'], ['procPeer', 'PROCUREMENT_STAFF', 'b'],
  ['procManager', 'PROCUREMENT_MANAGER', 'a'], ['srmAdmin', 'SRM_ADMIN', 'a'],
  ['srmManager', 'SRM_MANAGER', 'a'], ['srmCompliance', 'SRM_COMPLIANCE', 'a'],
  ['srmDirector', 'SRM_DIRECTOR', 'a'], ['assetUser', 'ASSET_USER', 'a'],
  ['assetManager', 'ASSET_MANAGER', 'a'], ['supplier', 'USER', 'a'], ['lockProbe', 'EMPLOYEE', 'b'],
]

function assertLocal() {
  assert.equal(process.env.OMNI_GOAL_ALLOW_LOCAL_FIXTURE, '2026-10-22', '缺少本次本地 fixture 显式启用标志')
  assert.ok(existsSync('/.dockerenv'), '仅允许 Docker 内执行')
  assert.equal(realpathSync(source), source)
  assert.equal(realpathSync(privateRoot), privateRoot)
  assert.ok(!lstatSync(configFile).isSymbolicLink())
  chmodSync(privateRoot, 0o700)
  mkdirSync(evidenceRoot, { recursive: true })
}

function writePrivate(path, value) {
  assert.ok(path.startsWith(`${privateRoot}/`) && !lstatIfExists(path)?.isSymbolicLink())
  writeFileSync(path, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 })
  chmodSync(path, 0o600)
}

function lstatIfExists(path) {
  return existsSync(path) ? lstatSync(path) : undefined
}

let classpath
function fixtureClasspath() {
  if (classpath) return classpath
  const auth = resolve(source, 'omni-backend/omni-auth/target')
  // 复用 Maven 实际通过的完整测试 classpath，包含 ReflectionTestUtils 等测试依赖。
  // 应用 fat jar 不携带这些测试类，不能拿其运行时库代替测试 classpath。
  const report = readFileSync(resolve(auth, 'surefire-reports/TEST-com.omni.auth.e2e.LocalChallengeFixtureTest.xml'), 'utf8')
  const match = report.match(/<property name="surefire\.test\.class\.path" value="([^"]*)"/)
  assert.ok(match, '必须先在规定容器完成当前 Maven 测试，取得真实 test classpath')
  classpath = match[1].replaceAll('&quot;', '"').replaceAll('&apos;', "'").replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&amp;', '&')
    .split(':').filter(Boolean).join(':')
  for (const path of classpath.split(':')) {
    assert.ok((path.startsWith(`${source}/omni-backend/`) || path.startsWith('/validation/cache/m2/')) && existsSync(path),
      '测试 classpath 只允许固定卷中的当前 reactor 产物与既有依赖')
  }
  return classpath
}

function runJava(className, args, extraEnv = {}) {
  const result = spawnSync('java', ['--enable-native-access=ALL-UNNAMED', '-Djava.io.tmpdir=/validation/test-fixtures/tmp',
    '-cp', fixtureClasspath(), className, ...args], {
    env: { ...process.env, ...extraEnv }, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024,
  })
  // 原输出只保存在受限卷内；不将可能含连接细节的异常直接转发到普通日志。
  const file = resolve(privateRoot, 'last-java-output.json')
  writePrivate(file, { className, args, exit_code: result.status, stdout: result.stdout, stderr: result.stderr })
  assert.equal(result.status, 0, `${className} 执行失败；受限诊断见 ${file}`)
}

export function createLocalContext() {
  assertLocal()
  const config = JSON.parse(readFileSync(configFile, 'utf8'))
  assert.equal(config.environment, 'local')
  assert.equal(config.namespace, 'goal_20261022')
  assert.equal(config.redisHost, 'redis')
  assert.match(config.dbUrl, /^jdbc:mysql:\/\/mysql:3306\/omni_auth(?:\?|$)/)
  if (!config.challengeSeed) {
    config.challengeSeed = randomBytes(32).toString('hex')
    writePrivate(configFile, config)
  }
  const state = existsSync(stateFile) ? JSON.parse(readFileSync(stateFile, 'utf8'))
    : { namespace: config.namespace, tenant_id: 1, accounts: {}, organizations: {}, operations: [], created_at: new Date().toISOString() }
  assert.equal(state.namespace, config.namespace)
  const save = () => writePrivate(stateFile, state)
  const record = (event) => {
    state.operations.push({ at: new Date().toISOString(), ...event })
    save()
  }
  const challenge = (label, ttl = 300) => {
    assert.match(label, /^[a-z][a-z0-9-]{0,47}$/)
    runJava('com.omni.auth.e2e.LocalChallengeFixture', [label, String(ttl)])
    return JSON.parse(readFileSync(resolve(privateRoot, `challenge-${label}.json`), 'utf8'))
  }
  const api = async (path, { method = 'GET', token, body, expected = 200, details = false } = {}) => {
    let response
    try {
      response = await fetch(`http://omni-gateway:8080${path}`, { method,
        headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
        ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(30000) })
    } catch {
      record({ method, path, outcome: method === 'GET' ? 'TRANSPORT_ERROR' : 'WRITE_OUTCOME_UNCERTAIN' })
      throw new Error(`${method} ${path} 网络失败；写请求必须先按自然键核对，不自动重发`)
    }
    const json = await response.json()
    const actual = json.code ?? response.status
    record({ method, path, http_status: response.status, business_code: actual })
    if (Array.isArray(expected)) assert.ok(expected.includes(actual), `${method} ${path} 返回非预期业务码 ${actual}`)
    else assert.equal(actual, expected, `${method} ${path} 业务码不符（HTTP ${response.status}）`)
    return details ? { data: json.data, code: actual, http_status: response.status } : json.data
  }
  const bootstrap = () => {
    challenge('bootstrap-guard')
    const output = resolve(privateRoot, 'bootstrap-tokens.json')
    if (existsSync(output)) { assert.ok(!lstatSync(output).isSymbolicLink()); rmSync(output) }
    runJava('com.omni.auth.e2e.Wp008TokenFixture', [], {
      WP008_FIXTURE_OUTPUT: output, WP008_FIXTURE_EXPECT_DB_UUID: config.expectedDbUuid,
      WP008_FIXTURE_DB_URL: config.dbUrl, WP008_FIXTURE_DB_USERNAME: config.dbUsername,
      WP008_FIXTURE_DB_PASSWORD: config.dbPassword, WP008_FIXTURE_REDIS_HOST: config.redisHost,
      WP008_FIXTURE_REDIS_PORT: String(config.redisPort), WP008_FIXTURE_REDIS_PASSWORD: config.redisPassword,
      WP008_FIXTURE_JWK_ENCRYPT_KEY: config.jwkEncryptKey, WP008_FIXTURE_IDENTITIES: 'admin:1:admin:SUPER_ADMIN',
    })
    chmodSync(output, 0o600)
    return JSON.parse(readFileSync(output, 'utf8')).admin
  }
  const login = async (alias) => {
    const account = state.accounts[alias]
    assert.ok(account?.id && account.password, `缺少本次专用身份 ${alias}`)
    const fixture = challenge(`login-${alias.toLowerCase()}`)
    const result = await api('/api/auth/login', { method: 'POST', body: { tenantId: 1,
      username: account.username, password: account.password, captchaKey: fixture.captchaKey, captchaCode: fixture.captchaCode } })
    const claims = JSON.parse(Buffer.from(result.accessToken.split('.')[1], 'base64url').toString('utf8'))
    assert.equal(String(claims.sub), String(account.id))
    assert.equal(String(claims.tenant_id), '1')
    account.token = result.accessToken
    account.token_expires_at = new Date(claims.exp * 1000).toISOString()
    account.actual_roles = claims.roles
    account.actual_permissions = claims.scope
    account.identity_source = 'test-fixture'
    save()
    return account.token
  }
  const publish = () => {
    const safe = { namespace: state.namespace, tenant_id: 1, at: new Date().toISOString(), identity_source: 'test-fixture',
      organizations: state.organizations, operations: state.operations, seed_runs: state.seed_runs,
      self_registration: state.self_registration ? { id: state.self_registration.id, username: state.self_registration.username,
        created: state.self_registration.created, role_ids: state.self_registration.role_ids, identity_source: 'test-fixture' } : undefined,
      tenant_probe: state.tenant_probe ? { id: state.tenant_probe.id, tenant_code: state.tenant_probe.tenant_code,
        source: 'formal-api-test-seed', rollback: '通过正式更新 API 禁用本次测试租户，不删业务历史或清库' } : undefined,
      accounts: Object.fromEntries(Object.entries(state.accounts).map(([alias, account]) => [alias, {
        id: account.id, username: account.username, organization_id: account.organization_id,
        role_ids: account.role_ids, actual_roles: account.actual_roles, actual_permissions: account.actual_permissions,
        identity_source: account.identity_source, configuration_changes: account.configuration_changes,
        rollback: account.id ? { method: 'PUT', path: `/api/auth/user/${account.id}/status?status=0`,
          note: '仅禁用本 Goal 专用身份，保留审批/业务历史；角色/作用域按后续登记精确撤销' } : undefined,
      }])) }
    writeFileSync(resolve(evidenceRoot, 'test-identities.json'), JSON.stringify(safe, null, 2) + '\n')
    return safe
  }
  return { config, state, save, record, challenge, api, bootstrap, login, publish }
}

async function seed(context) {
  const { state, save, record, api, bootstrap, login } = context
  const start = state.operations.length
  const token = bootstrap()
  const roles = await api('/api/auth/role/all', { token })
  const roleByCode = new Map(roles.map((role) => [role.roleCode, role]))
  const flatten = (rows) => rows.flatMap((row) => [row, ...flatten(row.children || [])])
  let organizations = flatten(await api('/api/auth/org/tree', { token }))
  const parent = organizations.find((org) => Number(org.parentId) === 0 && org.status === 1)
  assert.ok(parent, '需要原租户合法根组织，不自动创建另一租户或 schema')
  for (const group of ['a', 'b', 'work']) {
    const name = `goal_20261022_${group}`
    const parentId = group === 'work' ? state.organizations.a.id : parent.id
    const type = group === 'work' ? 'TEAM' : 'DEPT'
    let org = organizations.find((item) => item.name === name)
    if (org && !state.organizations[group]?.create_attempted) throw new Error(`组织自然键已存在但无本次归属记录: ${name}`)
    if (!org) {
      state.organizations[group] = { name, parent_id: parentId, create_attempted: true }
      save()
      org = await api('/api/auth/org', { token, method: 'POST', body: { parentId, name, type, status: 1, sort: 990 } })
      state.organizations[group].id = org.id
      record({ object: 'organization', id: org.id, name, created_by_goal: true })
      organizations = [...organizations, org]
    }
    if (!state.organizations[group].id) {
      assert.equal(Number(org.parentId), Number(parentId), '不接管其他组织下的同名对象')
      assert.equal(org.type, type)
      state.organizations[group].id = org.id
      record({ object: 'organization', id: org.id, name, outcome: 'UNCERTAIN_WRITE_RECONCILED_BY_NATURAL_KEY' })
    }
    assert.equal(Number(state.organizations[group].id), Number(org.id))
  }
  const allUsers = async () => {
    const users = []
    for (let page = 1; ; page++) {
      const data = await api(`/api/auth/user/list?page=${page}&size=100`, { token })
      const items = data.records ?? data.list
      assert.ok(Array.isArray(items), '用户分页结构与当前契约不符')
      users.push(...items)
      if (users.length >= Number(data.total) || items.length === 0) return users
      assert.ok(page < 100, '用户分页异常')
    }
  }
  let users = await allUsers()
  for (const [alias, initialRoleCode, group] of specs) {
    const roleCode = alias === 'supplier' && state.accounts.supplier?.portal_enrolled ? 'SUPPLIER' : initialRoleCode
    const role = roleByCode.get(roleCode)
    assert.ok(role?.id && Number(role.tenantId) === 1 && role.status === 1, `既有角色不可用: ${roleCode}`)
    let account = state.accounts[alias]
    const username = `goal_20261022_${alias.toLowerCase()}`
    let user = users.find((item) => item.username === username)
    if (user && !account?.create_attempted) throw new Error(`账号已存在但无本 Goal 创建记录: ${username}`)
    if (!account) {
      account = state.accounts[alias] = { username, password: `G!${randomBytes(24).toString('base64url')}`, create_attempted: false }
      save()
    }
    if (!user) {
      account.create_attempted = true
      save()
      await api('/api/auth/user', { token, method: 'POST', body: { tenantId: 1, username, password: account.password, nickname: username } })
      users = await allUsers()
      user = users.find((item) => item.username === username)
      assert.ok(user?.id, '创建后必须由正式查询确认结果')
      record({ object: 'user', id: user.id, username, created_by_goal: true })
    }
    account.id = user.id
    // 销售经理位于父部门，代表位于子团队，用真实层级验证 DEPT_AND_BELOW；其余候选人保持已登记组织。
    const organizationKey = alias === 'salesManager' ? 'a' : group === 'a' ? 'work' : group
    account.organization_id = state.organizations[organizationKey].id
    const beforeRoles = await api(`/api/auth/user/${user.id}/roles`, { token })
    const before = { primaryUnitId: user.primaryUnitId, role_ids: beforeRoles }
    if (Number(user.primaryUnitId) !== Number(account.organization_id)) {
      await api(`/api/auth/user/${user.id}`, { token, method: 'PUT', body: { primaryUnitId: account.organization_id } })
    }
    if (JSON.stringify(beforeRoles.map(Number).sort()) !== JSON.stringify([Number(role.id)])) {
      await api(`/api/auth/user/${user.id}/roles`, { token, method: 'POST', body: [role.id] })
    }
    const afterRoles = await api(`/api/auth/user/${user.id}/roles`, { token })
    assert.deepEqual(afterRoles.map(Number), [Number(role.id)])
    account.role_ids = [role.id]
    account.configuration_changes ??= []
    if (Number(before.primaryUnitId) !== Number(account.organization_id) || JSON.stringify(beforeRoles) !== JSON.stringify(afterRoles)) {
      account.configuration_changes.push({ at: new Date().toISOString(), before_sha256: sha256(JSON.stringify(before)),
        after_sha256: sha256(JSON.stringify({ primaryUnitId: account.organization_id, role_ids: afterRoles })),
        before, after: { primaryUnitId: account.organization_id, role_ids: afterRoles }, method: 'formal-api' })
    }
    save()
    if (alias !== 'lockProbe' || !state.lock_probe_complete) await login(alias)
    assert.deepEqual(account.actual_roles, [roleCode], `${alias} 不能继承默认 USER 的共享污染授权`)
  }
  const configurationWrites = state.operations.slice(start).filter((operation) =>
    operation.method !== 'GET' && operation.path !== '/api/auth/login')
  state.seed_runs ??= []
  state.seed_runs.push({ at: new Date().toISOString(), configuration_writes: configurationWrites.length,
    configuration_operations: configurationWrites, accounts: Object.keys(state.accounts).length,
    organizations: Object.keys(state.organizations).length })
  save()
  const result = context.publish()
  console.log(JSON.stringify({ namespace: result.namespace, accounts: Object.keys(result.accounts).length,
    organizations: Object.keys(result.organizations).length, configuration_writes: configurationWrites.length,
    identity_source: result.identity_source, result: 'IDENTITIES_PREPARED' }))
}

async function checkAuthentication(context) {
  const { state, save, api, challenge, bootstrap, login } = context
  const results = []
  const checkpoint = (item) => {
    results.push(item)
    writeFileSync(resolve(evidenceRoot, 'authentication-checks-in-progress.json'), JSON.stringify({
      at: new Date().toISOString(), complete: false, namespace: state.namespace,
      identity_source: 'test-fixture', cases: results,
    }, null, 2) + '\n')
  }
  const employee = state.accounts.employee
  assert.ok(employee?.id, '先完成本次身份种子')
  const credentials = (account, fixture, extra = {}) => ({ username: account.username, password: account.password,
    tenantId: 1, captchaKey: fixture.captchaKey, captchaCode: fixture.captchaCode, ...extra })
  const rejected = async (name, account, fixture, expected, extra = {}, path = '/api/auth/login') => {
    const result = await api(path, { method: 'POST', body: credentials(account, fixture, extra), expected, details: true })
    assert.ok(result.data == null, '认证拒绝不能返回 token 或会话身份')
    checkpoint({ case: name, http_status: result.http_status, business_code: result.code, result: 'PASS' })
  }
  await login('employee')
  checkpoint({ case: 'real-password-login-with-test-challenge', result: 'PASS', identity_source: 'test-fixture' })
  const wrong = challenge('wrong-answer')
  await rejected('wrong-answer-rejected', employee, wrong, 400, { captchaCode: 'intentionally-invalid' })
  await rejected('wrong-answer-consumes-challenge', employee, wrong, 400)
  const expired = challenge('expired', 1)
  await new Promise((resolve) => setTimeout(resolve, 1600))
  await rejected('real-redis-ttl-expiry', employee, expired, 400)
  const replay = challenge('login-replay')
  await api('/api/auth/login', { method: 'POST', body: credentials(employee, replay) })
  await rejected('successful-login-consumes-challenge', employee, replay, 400)
  const wrongPassword = challenge('wrong-password')
  await rejected('wrong-password-rejected', employee, wrongPassword, 401, { password: randomBytes(24).toString('hex') })
  await login('employee')
  checkpoint({ case: 'correct-password-recovers-before-lock-threshold', result: 'PASS' })

  // 优先使用现有已 ACTIVE 的另一个租户；确实没有时，仅通过正式接口准备一条本次测试租户。
  const admin = bootstrap()
  let tenants = await api('/api/auth/tenants')
  let other = tenants.find((tenant) => Number(tenant.id) !== 1)
  if (!other) {
    const code = 'goal_20261022_scope'
    state.tenant_probe ??= { tenant_code: code, admin_password: `G!${randomBytes(24).toString('base64url')}`, create_attempted: false }
    const known = await api('/api/auth/tenant/list?page=1&size=100', { token: admin })
    let tenant = known.records.find((item) => item.tenantCode === code)
    if (tenant && !state.tenant_probe.create_attempted) throw new Error('不接管无本次创建记录的同名租户')
    if (!tenant) {
      state.tenant_probe.create_attempted = true
      save()
      tenant = await api('/api/auth/tenant', { token: admin, method: 'POST', body: {
        tenantCode: code, tenantName: 'Goal 20261022 isolation fixture', adminPassword: state.tenant_probe.admin_password, status: 1,
      } })
    }
    state.tenant_probe.id = tenant.id
    save()
    for (let attempt = 0; attempt < 60; attempt++) {
      tenants = await api('/api/auth/tenants')
      other = tenants.find((item) => Number(item.id) === Number(tenant.id))
      if (other) break
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }
    assert.ok(other, '本次最小租户尚未由正式 provisioning 达到 ACTIVE；不得改状态取得成功')
  }
  await rejected('credentials-do-not-cross-an-active-tenant', employee, challenge('wrong-tenant'), 401, { tenantId: other.id })
  checkpoint({ case: 'active-tenant-boundary', tenant_id: other.id,
    tenant_source: state.tenant_probe?.id === other.id ? 'formal-api-test-seed' : 'existing-active-tenant', result: 'PASS' })

  // 自注册只创建一次，重复用户名与重放均走原验证链；账号口令保存在受限状态文件。
  state.self_registration ??= { username: 'goal_20261022_selfreg', password: `G!${randomBytes(24).toString('base64url')}`, created: false, create_attempted: false }
  save()
  const registration = state.self_registration
  const findRegisteredUser = async () => {
    for (let page = 1; page < 100; page++) {
      const users = await api(`/api/auth/user/list?page=${page}&size=100`, { token: admin })
      const found = users.records.find((user) => user.username === registration.username)
      if (found) return found
      if (page >= users.pages) return undefined
    }
    throw new Error('用户分页异常，未确认自注册结果')
  }
  if (!registration.created) {
    const found = await findRegisteredUser()
    if (found && !registration.create_attempted) throw new Error('不接管无本次自注册记录的同名用户')
    if (!found) {
      registration.create_attempted = true
      save()
      const fixture = challenge('register-success')
      await api('/api/auth/register', { method: 'POST', body: credentials(registration, fixture) })
      registration.created = true
      save()
      checkpoint({ case: 'real-self-registration-with-test-challenge', result: 'PASS' })
      await rejected('registration-replay-rejected', registration, fixture, 400, {}, '/api/auth/register')
    } else {
      registration.id = found.id
      registration.created = true
      checkpoint({ case: 'self-registration-write-reconciled-by-natural-key', result: 'RECONCILED', user_id: found.id })
    }
    save()
  }
  const registered = await findRegisteredUser()
  assert.ok(registered?.id, '自注册账号必须由正式查询确认')
  registration.id = registered.id
  registration.role_ids = await api(`/api/auth/user/${registration.id}/roles`, { token: admin })
  const roles = await api('/api/auth/role/all', { token: admin })
  const defaultRole = roles.find((role) => role.roleCode === 'USER')
  assert.deepEqual(registration.role_ids.map(Number), [Number(defaultRole.id)], '自注册必须分配当前租户默认 USER 角色')
  save()
  checkpoint({ case: 'self-registration-assigns-real-default-role', user_id: registration.id, role_ids: registration.role_ids, result: 'PASS' })
  // 用户存在检查的具体业务码由当前正式服务定义，不能通过放宽成功码掩盖重复建号。
  await rejected('duplicate-registration-rejected', registration, challenge('register-duplicate'), 400, {}, '/api/auth/register')

  const probe = state.accounts.lockProbe
  if (!state.lock_probe_complete) {
    await login('lockProbe')
    let locked = false
    for (let attempt = 1; attempt <= 100; attempt++) {
      const result = await api('/api/auth/login', { method: 'POST', details: true, expected: [401, 423],
        body: credentials(probe, challenge('lock-probe'), { password: randomBytes(24).toString('hex') }) })
      assert.ok(result.data == null)
      if (result.code === 423) {
        checkpoint({ case: 'actual-account-lockout', attempts: attempt, http_status: result.http_status, business_code: result.code, result: 'PASS' })
        locked = true
        break
      }
    }
    assert.ok(locked, '独立测试账号没有按原实际策略锁定，不修改策略或计数制造成功')
    await rejected('correct-password-cannot-bypass-lockout', probe, challenge('locked-correct-password'), 423)
    state.lock_probe_complete = true
    save()
  } else {
    checkpoint({ case: 'actual-account-lockout', result: 'REUSE_PRIOR_RESULT', note: '不重复制造锁定；复用本次已保存原证据' })
  }
  const output = { at: new Date().toISOString(), namespace: state.namespace, runtime: 'local Docker',
    identity_source: 'test-fixture', captcha_validator: 'production CaptchaServiceImpl unchanged', cases: results }
  writeFileSync(resolve(evidenceRoot, `authentication-checks-${Date.now()}.json`), JSON.stringify(output, null, 2) + '\n')
  writeFileSync(resolve(evidenceRoot, 'authentication-checks.json'), JSON.stringify(output, null, 2) + '\n')
  context.publish()
  console.log(JSON.stringify({ result: 'AUTHENTICATION_CHECKS_PASS', cases: results.length, identity_source: output.identity_source }))
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const context = createLocalContext()
  if (process.argv[2] === 'seed') await seed(context)
  else if (process.argv[2] === 'bootstrap') { context.bootstrap(); console.log('BOOTSTRAP_STORED_IN_PRIVATE_VOLUME') }
  else if (process.argv[2] === 'scopes') {
    runJava('com.omni.auth.e2e.LocalRoleScopeFixture', ['apply'])
    console.log('OWNED_CANDIDATE_SCOPES_VERIFIED')
  } else if (process.argv[2] === 'auth-check') await checkAuthentication(context)
  else throw new Error('只接受 seed、bootstrap、scopes 或 auth-check；不执行任意命令')
}
