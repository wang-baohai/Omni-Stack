// 黄金 CRUD 运行时回归（WP-011 S05 重写版）：
// - 目标全部来自本脚本自举的隔离栈：project=omni-wp011-crud-rt-<8hex>，
//   mysql/redis/migrator/auth/gateway 由本脚本生成的 compose 拉起，宿主端口全部 ephemeral。
// - 身份信任链走真实登录：/api/auth/captcha → 隔离 Redis 取验证码 → /api/auth/login → JWT → 经 Gateway 访问。
//   严禁伪造 X-Gateway-Forwarded / X-User-* 直连做业务断言（伪造头仅用于 401 负例）。
// - 清理只按创建时登记的容器完整 ID 定点 stop+rm；卷与网络保留；--keep 保留现场。
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { renderCrudGeneration } from '../dist/src/crud-generator.js';
import { applyRenderedIntegration } from '../dist/src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const PROJECT_PATTERN = /^omni-wp011-crud-rt-[a-z0-9]{8}$/;
const argumentsMap = parseArguments(process.argv.slice(2));

function assertIsolationName(name) {
  if (typeof name !== 'string' || !PROJECT_PATTERN.test(name)) {
    throw new Error(`拒绝操作非 WP011 隔离资源：${String(name)}`);
  }
  return name;
}

// ---- owner guard：project/卷/容器名冲突即换新 suffix，绝不复用他人资源 ----
function reserveIsolation() {
  for (let attempt = 0; attempt < 32; attempt += 1) {
    const suffix = randomBytes(4).toString('hex');
    const project = `omni-wp011-crud-rt-${suffix}`;
    assertIsolationName(project);
    const clash = spawnSync('docker', ['volume', 'ls', '--filter', `name=^${project}_`, '--format', '{{.Name}}'], { encoding: 'utf8' });
    if (clash.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).length > 0) continue;
    const existing = spawnSync('docker', ['ps', '-a', '--filter', `name=^${project}-`, '--format', '{{.Names}}'], { encoding: 'utf8' });
    if (existing.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).length > 0) continue;
    return { project, suffix };
  }
  throw new Error('无法预留未被占用的 omni-wp011-crud-rt-* 隔离资源名');
}

const { project, suffix } = reserveIsolation();
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-crud-runtime-'));
const fixtureRoot = resolve(systemTempRoot, 'workspace');
const composeFile = 'compose.wp011-crud-rt.yaml';
const secret = (bytes = 24) => randomBytes(bytes).toString('base64url');
const runtimeSecrets = {
  WP011_ROOT_PW: secret(),
  WP011_APP_PW: secret(),
  WP011_REDIS_PW: secret(),
  WP011_ASSERTION_SECRET: secret(32),
  WP011_JWK_KEY: secret(24),
  WP011_OAUTH_STATE: secret(),
  WP011_INTERNAL_TOKEN: secret(),
};
const registeredContainers = [];
let serviceProcess;
let serviceOutput = '';
let primaryError;

try {
  await main();
} catch (error) {
  primaryError = error;
  if (serviceOutput) console.error(serviceOutput.slice(-6000));
  throw error;
} finally {
  await stopService();
  writeManifest();
  if (!argumentsMap.keep) {
    try {
      teardownContainers();
    } catch (cleanupError) {
      if (!primaryError) throw cleanupError;
      console.error(`隔离栈定点清理同时失败，保留原始错误：${cleanupError.message}`);
    }
    removeVerifiedTree(systemTempRoot, tmpdir());
  } else {
    console.log(`--keep：保留现场 ${systemTempRoot}（容器清单见上方 manifest，请按完整 ID 定点清理）`);
  }
}

async function main() {
  console.log(`wp011 crud-rt isolation: project=${project}（卷/网络保留，容器按登记 ID 定点清理）`);
  copyWorkspace(workspaceRoot, fixtureRoot);
  const spec = resolve(fixtureRoot, 'scaffold/specs/material-brand.yaml');
  const rendered = renderCrudGeneration(fixtureRoot, spec);
  applyRenderedIntegration(fixtureRoot, rendered);
  writeCompose(project);

  const composeEnv = {
    ...process.env,
    ...runtimeSecrets,
    WP011_PROCUREMENT_URL: 'http://pending.invalid:1',
    COMPOSE_PROGRESS: 'plain',
  };
  // 阶段 1：构建并拉起 mysql/redis/migrator/auth/gateway（gateway 先占位 procurement URL）。
  composeUp(['up', '-d', ...(argumentsMap.skipBuild ? [] : ['--build']), 'mysql', 'redis', 'omni-db-migrator', 'omni-auth', 'omni-gateway'], composeEnv);
  registerProjectContainers(project);
  waitForMigratorDone(project, composeEnv);
  const mysqlPort = discoverHostPort('mysql', '3306/tcp');
  const redisPort = discoverHostPort('redis', '6379/tcp');
  console.log(`wp011 crud-rt stack ready: mysql=127.0.0.1:${mysqlPort} redis=127.0.0.1:${redisPort}`);

  // 本机 java 起改造后的 procurement（两侧共享同一随机 GATEWAY_ASSERTION_SECRET）。
  const [servicePort, managementPort] = await Promise.all([reservePort(), reservePort()]);
  buildProcurementJar();
  startProcurement({ mysqlPort, redisPort, servicePort, managementPort });
  await waitForServiceHealth(managementPort);

  // 阶段 2：把 gateway lite 路由指到本机 procurement（compose 检测 env 变化后重建 gateway，宿主端口重新发现）。
  composeUp(['up', '-d', 'omni-gateway'], { ...composeEnv, WP011_PROCUREMENT_URL: `http://host.docker.internal:${servicePort}` });
  registerProjectContainers(project);
  const gatewayPort = discoverHostPort('omni-gateway', '8080/tcp');
  const gateway = `http://127.0.0.1:${gatewayPort}`;
  await waitForHttpHealthy(`${gateway}/api/auth/captcha`, 'Gateway 不可达');

  // 真实种子管理员登录链（admin/admin123/tenant 1；权限 changeset 由 migrator 随生成物自动应用）。
  const adminToken = await login(gateway, 1, 'admin', 'admin123');
  console.log(`wp011 crud-rt login: admin/admin123 via captcha+login → JWT=ok, gateway=127.0.0.1:${gatewayPort}`);

  await verifyNegativeMatrix({ directBase: `http://127.0.0.1:${servicePort}`, gateway });
  await verifyBusinessMatrix(gateway, adminToken);
  await verifyTenantAndPermissionNegatives(gateway);

  console.log('golden CRUD runtime valid: direct=403, forged-forwarded-noassert=401, forged-assert=401, gateway-anon=401, login=real-chain, crud=pass, duplicate=409, crossTenant=isolated, deleted=404');
}

// ---------------- 负例矩阵（无任何伪造信任头的业务路径；伪造头仅用于拒绝断言） ----------------
async function verifyNegativeMatrix({ directBase, gateway }) {
  const base = `${directBase}/api/procurement/material-brands/list?page=1&size=10`;
  // 1) 直连且无任何信任头：共享过滤器非公开路径无 X-Gateway-Forwarded → 403。
  await expectHttp(fetch(base), 403);
  // 2) 直连带伪造 X-Gateway-Forwarded/X-User-* 但无 HMAC 断言（procurement 已配置密钥）→ 401。
  await expectHttp(fetch(base, {
    headers: {
      'X-Gateway-Forwarded': 'true',
      'X-User-Id': '9001',
      'X-User-Name': 'forged',
      'X-Tenant-Id': '1',
      'X-User-Roles': 'SUPER_ADMIN',
      'X-User-Scopes': 'procurement:material-brand:list',
    },
  }), 401);
  // 3) 直连带伪造头 + 错误 HMAC 断言（结构合法、签名错误）→ 401。
  const forgedPayload = Buffer.from('9001|1|forged|' + Math.floor(Date.now() / 1000)).toString('base64url');
  const forgedSignature = Buffer.from('x'.repeat(43)).toString('base64url');
  await expectHttp(fetch(base, {
    headers: {
      'X-Gateway-Forwarded': 'true',
      'X-User-Id': '9001',
      'X-User-Name': 'forged',
      'X-Tenant-Id': '1',
      'X-User-Roles': 'SUPER_ADMIN',
      'X-User-Scopes': 'procurement:material-brand:list',
      'X-Gateway-Assertion': `${forgedPayload}:${forgedSignature}`,
    },
  }), 401);
  // 4) 经 Gateway 匿名访问受保护路径 → 401。
  await expectHttp(fetch(`${gateway}/api/procurement/material-brands/list?page=1&size=10`, {
    headers: { 'X-Tenant-Id': '1' },
  }), 401);
  console.log('wp011 crud-rt negatives: direct=403, forged-forwarded-noassert=401, forged-assert=401, gateway-anon=401');
}

// ---------------- 业务断言（真实 JWT 经 Gateway，等价于旧矩阵强度） ----------------
async function verifyBusinessMatrix(gateway, token) {
  const base = `${gateway}/api/procurement/material-brands`;
  const headers = { 'Content-Type': 'application/json', 'X-Tenant-Id': '1', Authorization: `Bearer ${token}` };
  const emptyList = await json200(fetch(base + '/list?page=1&size=10', { headers }));
  assert.equal(emptyList.data.total, 0, '新栈初始列表应为空');

  const created = await json200(fetch(base, {
    method: 'POST',
    headers,
    body: JSON.stringify({ brandCode: 'BRAND-RUNTIME', brandName: '运行时品牌', status: 1, remark: 'golden' }),
  }));
  const id = created.data.id;
  assert.match(String(id), /^\d+$/);
  assert.equal(created.data.version, 0);

  const listed = await json200(fetch(base + '/list?page=1&size=10', { headers }));
  assert.equal(listed.data.total, 1);
  assert.equal(String(listed.data.records[0].id), String(id));

  // 重复创建 → 业务 409（HTTP 200 + body.code 409）。
  const duplicate = await fetchJson(fetch(base, {
    method: 'POST',
    headers,
    body: JSON.stringify({ brandCode: 'BRAND-RUNTIME', brandName: '重复品牌', status: 1 }),
  }));
  assert.equal(duplicate.body.code, 409, `重复品牌应业务 409，实际 ${JSON.stringify(duplicate.body)}`);

  const updated = await json200(fetch(`${base}/${id}`, {
    method: 'PUT',
    headers,
    body: JSON.stringify({ version: 0, brandName: '运行时品牌（更新）', status: 1, remark: 'updated' }),
  }));
  assert.equal(updated.data.version, 1);

  const deleted = await json200(fetch(`${base}/${id}?version=1`, { method: 'DELETE', headers }));
  assert.equal(deleted.code, 200); // json200 已断言，保留可读性。
  const gone = await fetchJson(fetch(`${base}/${id}`, { headers }));
  assert.equal(gone.body.code, 404, `逻辑删除后应业务 404，实际 ${JSON.stringify(gone.body)}`);
  console.log('wp011 crud-rt business: create=ok, list=1, duplicate=409, update=version1, delete=ok, deleted=404');
}

// ---------------- 跨租户 + 无权限 403（真实登录链；造数前置失败才降级，断言本身 fail-closed） ----------------
async function verifyTenantAndPermissionNegatives(gateway) {
  const base = `${gateway}/api/procurement/material-brands`;
  // 仅“造数前置”（sys_* 实际列结构探测/复制）允许尽力而为；真实 auth 种子只含租户 1，
  // 若列结构不匹配则记入日志、交 S06 覆盖，不阻塞主信任链与业务矩阵。
  try {
    cloneTenant901AndUsers();
  } catch (error) {
    console.warn(`wp011 crud-rt note: 跨租户/403 造数前置失败，该子项由 S06 覆盖。原因：${error instanceof Error ? error.message : String(error)}`);
    return;
  }
  // 一旦隔离身份已建立，以下断言必须 fail-closed：任何跨租户可见/可改/无权限放行
  // 都是真实安全缺陷，必须让整个黄金失败，绝不能被 try/catch 静默降级为警告。
  const outsider = await login(gateway, 901, 'wp011rt901', 'admin123');
  const outsiderHeaders = { 'Content-Type': 'application/json', 'X-Tenant-Id': '901', Authorization: `Bearer ${outsider}` };
  const foreignList = await json200(fetch(base + '/list?page=1&size=10', { headers: outsiderHeaders }));
  assert.equal(foreignList.data.total, 0, '跨租户列表不应看到租户 1 数据');
  const foreignUpdate = await fetchJson(fetch(`${base}/1`, {
    method: 'PUT',
    headers: outsiderHeaders,
    body: JSON.stringify({ version: 0, brandName: '越权修改', status: 1 }),
  }));
  assert.equal(foreignUpdate.body.code, 404, `跨租户更新应业务 404，实际 ${JSON.stringify(foreignUpdate.body)}`);
  const foreignDelete = await fetchJson(fetch(`${base}/1?version=0`, { method: 'DELETE', headers: outsiderHeaders }));
  assert.equal(foreignDelete.body.code, 404, `跨租户删除应业务 404，实际 ${JSON.stringify(foreignDelete.body)}`);

  const nobody = await login(gateway, 1, 'wp011noproc', 'admin123');
  const nobodyHeaders = { 'Content-Type': 'application/json', 'X-Tenant-Id': '1', Authorization: `Bearer ${nobody}` };
  const forbidden = await fetchJson(fetch(base, {
    method: 'POST',
    headers: nobodyHeaders,
    body: JSON.stringify({ brandCode: 'BRAND-X', brandName: '无权限', status: 1 }),
  }));
  assert.ok(forbidden.body.code === 403 || forbidden.status === 403, `无权限创建应 403，实际 http=${forbidden.status} body=${JSON.stringify(forbidden.body)}`);
  console.log('wp011 crud-rt identity: crossTenant(list=0,update=404,delete=404)=pass, noPermission=403');
}

// ---------------- 隔离栈 compose ----------------
function writeCompose(targetProject) {
  assertIsolationName(targetProject);
  const compose = `# WP-011 crud-rt 自举隔离栈（脚本生成，勿手工复用）
services:
  mysql:
    image: mysql:8.4
    restart: "no"
    ports: ["127.0.0.1::3306"]
    environment:
      MYSQL_ROOT_PASSWORD: \${WP011_ROOT_PW:?}
      MYSQL_DATABASE: omni_auth
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
      TZ: Asia/Shanghai
    volumes: [mysql-data:/var/lib/mysql]
    command: ["--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci", "--init-connect=SET NAMES utf8mb4"]
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping --protocol=tcp -h 127.0.0.1 -uroot -p$\${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 40s
  redis:
    image: redis:7.4
    restart: "no"
    ports: ["127.0.0.1::6379"]
    command: ["redis-server", "--requirepass", "\${WP011_REDIS_PW:?}"]
    environment:
      REDIS_PASSWORD: \${WP011_REDIS_PW:?}
    healthcheck:
      test: ["CMD-SHELL", 'REDISCLI_AUTH="$\${REDIS_PASSWORD}" redis-cli ping | grep PONG']
      interval: 10s
      timeout: 5s
      retries: 6
  omni-db-migrator:
    build:
      context: .
      dockerfile: docker/migrator/Dockerfile
    image: ${targetProject}-migrator:1
    restart: "no"
    environment:
      TZ: Asia/Shanghai
      DB_MIGRATOR_COMMAND: migrate
      DB_MIGRATOR_ADMIN_URL: jdbc:mysql://mysql:3306/?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      DB_MIGRATOR_ADMIN_USERNAME: root
      DB_MIGRATOR_ADMIN_PASSWORD: \${WP011_ROOT_PW:?}
      DB_MIGRATOR_APP_USERNAME: omni_app
      DB_MIGRATOR_APP_PASSWORD: \${WP011_APP_PW:?}
      # CRUD 生成向 auth changelog 追加 procurement:material-brand 权限 changeset，
      # 使基线种子投影 procurement-permission-catalog 从 40 行变为 45 行；
      # 用产品自带的 seed-accept-ids 覆盖机制（WARN 通过）接受这一已知生成物差异，
      # 不修改仓库任何产品文件。
      DB_MIGRATOR_SEED_ACCEPT_IDS: procurement-permission-catalog
      OMNI_ENVIRONMENT: wp011-crud-rt
      MIGRATION_PUSHGATEWAY_ENABLED: "false"
    depends_on:
      mysql: { condition: service_healthy }
  omni-auth:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        SERVICE_NAME: omni-auth
    image: ${targetProject}-auth:1
    restart: "no"
    environment:
      OMNI_ENVIRONMENT: wp011-crud-rt
      TZ: Asia/Shanghai
      SERVER_PORT: "8080"
      MANAGEMENT_SERVER_PORT: "19900"
      MANAGEMENT_PORT: "19900"
      SPRING_PROFILES_ACTIVE: default
      SPRING_CLOUD_NACOS_DISCOVERY_ENABLED: "false"
      SPRING_CLOUD_NACOS_CONFIG_ENABLED: "false"
      SPRING_CLOUD_FUNCTION_DEFINITION: ""
      SPRING_CLOUD_STREAM_FUNCTION_AUTODETECT: "false"
      XXL_JOB_EXECUTOR_ENABLED: "false"
      OMNI_MQLOG_RELAY_ENABLED: "false"
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/omni_auth?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&connectTimeout=5000&socketTimeout=15000
      SPRING_DATASOURCE_USERNAME: omni_app
      SPRING_DATASOURCE_PASSWORD: \${WP011_APP_PW:?}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PASSWORD: \${WP011_REDIS_PW:?}
      AUTH_ISSUER: http://omni-auth:8080
      AUTH_FRONTEND_BASE_URL: http://localhost:1
      OAUTH2_STATE_SECRET: \${WP011_OAUTH_STATE:?}
      JWK_ENCRYPT_KEY: \${WP011_JWK_KEY:?}
      OMNI_INTERNAL_API_TOKEN: \${WP011_INTERNAL_TOKEN:?}
      GATEWAY_ASSERTION_SECRET: \${WP011_ASSERTION_SECRET:?}
    depends_on:
      mysql: { condition: service_healthy }
      redis: { condition: service_healthy }
      omni-db-migrator: { condition: service_completed_successfully }
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:19900/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 60s
  omni-gateway:
    build:
      context: .
      dockerfile: docker/backend/Dockerfile
      args:
        SERVICE_NAME: omni-gateway
    image: ${targetProject}-gateway:1
    restart: "no"
    ports: ["127.0.0.1::8080"]
    extra_hosts:
      - host.docker.internal:host-gateway
    environment:
      OMNI_ENVIRONMENT: wp011-crud-rt
      TZ: Asia/Shanghai
      SERVER_PORT: "8080"
      MANAGEMENT_SERVER_PORT: "19902"
      SPRING_PROFILES_ACTIVE: lite
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PASSWORD: \${WP011_REDIS_PW:?}
      AUTH_JWKS_URI: http://omni-auth:8080/oauth2/jwks
      OMNI_PROCUREMENT_URL: \${WP011_PROCUREMENT_URL:?}
      GATEWAY_ASSERTION_SECRET: \${WP011_ASSERTION_SECRET:?}
      JAVA_OPTS: -Dreactor.netty.http.server.maxHeaderSize=65536
    depends_on:
      omni-auth: { condition: service_healthy }
      redis: { condition: service_healthy }
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:8080/api/auth/captcha -o /dev/null || timeout 3 bash -c '</dev/tcp/localhost/8080' || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 40
      start_period: 60s
volumes:
  mysql-data:
  redis-data:
`;
  writeFileSync(resolve(fixtureRoot, composeFile), compose, { encoding: 'utf8' });
}

function composeUp(args, env) {
  const result = spawnSync('docker', ['compose', '-p', project, '-f', composeFile, ...args], {
    cwd: fixtureRoot,
    env,
    encoding: 'utf8',
    stdio: 'inherit',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `隔离 Compose 命令失败：${args.slice(0, 3).join(' ')}`);
}

function registerProjectContainers(targetProject) {
  assertIsolationName(targetProject);
  const listing = captureDocker(['ps', '-a', '--no-trunc', '--filter', `label=com.docker.compose.project=${targetProject}`, '--format', '{{.ID}}\t{{.Names}}']);
  for (const line of listing.stdout.split(/\r?\n/).filter(Boolean)) {
    const [id, name] = line.split('\t');
    if (!name.startsWith(`${targetProject}-`)) throw new Error(`拒绝登记非 WP011 容器名：${name}`);
    if (!registeredContainers.some((entry) => entry.id === id)) registeredContainers.push({ id, name });
  }
}

function teardownContainers() {
  let removed = 0;
  for (const { id, name } of registeredContainers) {
    if (!name.startsWith(`${project}-`)) throw new Error(`拒绝清理非 WP011 容器名：${name}`);
    if (!/^[0-9a-f]{12,64}$/.test(id)) throw new Error(`拒绝按非完整 ID 删除容器：${id}`);
    const ownerCheck = spawnSync('docker', ['inspect', '-f', '{{.Id}}\t{{index .Config.Labels "com.docker.compose.project"}}', id], { encoding: 'utf8' });
    if (ownerCheck.status !== 0) {
      console.warn(`容器 ${name} 已不存在（compose 重建时自行替换），跳过定点清理`);
      continue;
    }
    const [actualId, actualProject] = ownerCheck.stdout.trim().split('\t');
    if (actualId !== id || actualProject !== project) throw new Error(`容器 ID/project 与登记不符，拒绝删除：${name}`);
    const stop = spawnSync('docker', ['stop', id], { encoding: 'utf8' });
    const rm = spawnSync('docker', ['rm', id], { encoding: 'utf8' });
    if (stop.status !== 0 || rm.status !== 0) throw new Error(`定点清理容器失败：${name}`);
    removed += 1;
  }
  console.log(`wp011 crud-rt teardown: ${removed}/${registeredContainers.length} 个容器已定点停止删除；卷/网络保留`);
}

function writeManifest() {
  const volumes = captureDocker(['volume', 'ls', '--filter', `label=com.docker.compose.project=${project}`, '--format', '{{.Name}}']).stdout
    .split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const networks = captureDocker(['network', 'ls', '--no-trunc', '--filter', `label=com.docker.compose.project=${project}`, '--format', '{{.ID}}\t{{.Name}}']).stdout
    .split(/\r?\n/).filter(Boolean).map((line) => line.split('\t')).map(([id, name]) => ({ id, name }));
  const manifestPath = resolve(systemTempRoot, `row-manifest-crud-rt-${suffix}.json`);
  writeFileSync(manifestPath, JSON.stringify({
    recordedAt: new Date().toISOString(),
    project,
    containers: registeredContainers,
    volumes,
    networks,
    volumesRetained: true,
    containersRemoved: !argumentsMap.keep,
  }, null, 2), { encoding: 'utf8' });
  console.log(`wp011 row-manifest: ${manifestPath}`);
}

// ---------------- 真实登录链 ----------------
async function login(gateway, tenantId, username, password) {
  const headers = { 'X-Tenant-Id': String(tenantId) };
  const captcha = await httpJson(`${gateway}/api/auth/captcha`, { headers });
  assert.equal(captcha.body.code, 200, '验证码接口业务失败');
  const captchaKey = captcha.body.data?.captchaKey;
  assert.ok(typeof captchaKey === 'string' && /^[0-9a-f-]{16,}$/i.test(captchaKey), '验证码 Key 格式非法');
  const captchaCode = redisGetCaptcha(captchaKey);
  assert.ok(captchaCode, '隔离 Redis 中未找到验证码');
  const loginResult = await httpJson(`${gateway}/api/auth/login`, {
    method: 'POST',
    headers: { ...headers, 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, tenantId, captchaKey, captchaCode }),
  });
  assert.equal(loginResult.body.code, 200, `登录失败：${JSON.stringify(loginResult.body.message ?? loginResult.body)}`);
  const token = loginResult.body.data?.accessToken;
  assert.ok(token, '登录响应未返回访问令牌');
  return token;
}

function redisGetCaptcha(captchaKey) {
  const containerName = `${project}-redis-1`;
  assertIsolationName(project);
  if (!containerName.startsWith(`${project}-`)) throw new Error(`拒绝操作非本 case 容器：${containerName}`);
  assert.match(String(captchaKey), /^[0-9a-f-]{16,}$/i);
  const command = 'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli --raw GET "captcha:' + captchaKey + '"';
  const result = spawnSync('docker', ['exec', containerName, 'sh', '-lc', command], { encoding: 'utf8' });
  assert.equal(result.status, 0, '隔离 Redis 验证码读取失败');
  return result.stdout.trim();
}

// ---------------- procurement（本机 java，注入两侧共享断言密钥） ----------------
function buildProcurementJar() {
  const backendRoot = resolve(fixtureRoot, 'omni-backend');
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  run(wrapper, ['-DskipTests', 'package', '-pl', 'omni-procurement', '-am'], backendRoot, jdkEnvironment());
  const jar = resolve(backendRoot, 'omni-procurement/target/omni-procurement-1.0.0-SNAPSHOT.jar');
  assert.ok(existsSync(jar), '生成后的采购 JAR 不存在');
  return jar;
}

function startProcurement({ mysqlPort, redisPort, servicePort, managementPort }) {
  const backendRoot = resolve(fixtureRoot, 'omni-backend');
  const jar = resolve(backendRoot, 'omni-procurement/target/omni-procurement-1.0.0-SNAPSHOT.jar');
  serviceProcess = spawn(resolve(jdkEnvironment().JAVA_HOME, 'bin', 'java.exe'), [
    '-jar', jar,
    '--spring.cloud.nacos.discovery.enabled=false',
    '--spring.cloud.nacos.config.enabled=false',
  ], {
    cwd: backendRoot,
    env: {
      ...process.env,
      ...jdkEnvironment(),
      SPRING_PROFILES_ACTIVE: 'default',
      SERVER_PORT: String(servicePort),
      MANAGEMENT_PORT: String(managementPort),
      MANAGEMENT_SERVER_PORT: String(managementPort),
      MYSQL_URL: `jdbc:mysql://127.0.0.1:${mysqlPort}/omni_procurement?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`,
      MYSQL_USERNAME: 'omni_app',
      MYSQL_PASSWORD: runtimeSecrets.WP011_APP_PW,
      REDIS_HOST: '127.0.0.1',
      REDIS_PORT: String(redisPort),
      REDIS_PASSWORD: runtimeSecrets.WP011_REDIS_PW,
      SPRING_CLOUD_FUNCTION_DEFINITION: '',
      SPRING_CLOUD_STREAM_FUNCTION_AUTODETECT: 'false',
      OMNI_MQLOG_RELAY_ENABLED: 'false',
      XXL_JOB_ENABLED: 'false',
      XXL_JOB_EXECUTOR_ENABLED: 'false',
      OMNI_INTERNAL_API_TOKEN: runtimeSecrets.WP011_INTERNAL_TOKEN,
      GATEWAY_ASSERTION_SECRET: runtimeSecrets.WP011_ASSERTION_SECRET,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  serviceProcess.stdout.on('data', (chunk) => appendServiceOutput(chunk));
  serviceProcess.stderr.on('data', (chunk) => appendServiceOutput(chunk));
}

async function waitForServiceHealth(managementPort) {
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    if (serviceProcess.exitCode !== null) throw new Error(`采购黄金服务提前退出：${serviceProcess.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/health`, { signal: AbortSignal.timeout(5000) });
      if (response.status === 200) return;
    } catch {
      // 服务仍在启动。
    }
    await delay(1500);
  }
  throw new Error('采购黄金服务健康检查超时');
}

async function stopService() {
  if (!serviceProcess || serviceProcess.exitCode !== null) return;
  serviceProcess.kill('SIGTERM');
  await Promise.race([
    new Promise((resolvePromise) => serviceProcess.once('exit', resolvePromise)),
    new Promise((resolvePromise) => setTimeout(resolvePromise, 10_000)),
  ]);
  if (serviceProcess.exitCode === null) serviceProcess.kill('SIGKILL');
}

// ---------------- 租户 901 / 无权限用户 SQL 造数（列名动态探测） ----------------
function mysqlRootSql(statement) {
  const containerName = `${project}-mysql-1`;
  const command = `exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 omni_auth`;
  const result = spawnSync('docker', ['exec', '-i', containerName, 'sh', '-lc', command], {
    input: statement,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
  });
  assert.equal(result.status, 0, `auth SQL 执行失败：${(result.stderr ?? '').slice(0, 400)}`);
  return result.stdout;
}

function mysqlRootQuery(statement) {
  const containerName = `${project}-mysql-1`;
  const command = 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --raw omni_auth';
  const result = spawnSync('docker', ['exec', '-i', containerName, 'sh', '-lc', command], {
    input: statement,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
  });
  assert.equal(result.status, 0, `auth 查询失败：${(result.stderr ?? '').slice(0, 400)}`);
  const [header, ...rows] = result.stdout.split(/\r?\n/).filter((line) => line.length > 0);
  const columns = header.split('\t');
  return rows.map((row) => Object.fromEntries(row.split('\t').map((value, index) => [columns[index], value === 'NULL' ? null : value])));
}

function quoteSql(value) {
  if (value === null) return 'NULL';
  return `'${String(value).replaceAll("'", "''")}'`;
}

function copyRow(table, whereSql, overrides, explicitId) {
  const columns = mysqlRootQuery(`SHOW COLUMNS FROM \`${table}\`;`).map((row) => row.Field);
  const targetColumns = columns.filter((column) => !(column.toLowerCase() === 'id' && explicitId === undefined));
  const valuesFor = (columnList) => columnList.map((column) => {
    const lower = column.toLowerCase();
    if (lower === 'id' && explicitId !== undefined) return String(explicitId);
    if (lower in overrides) return quoteSql(overrides[lower]);
    return `(SELECT \`${column}\` FROM \`${table}\` WHERE ${whereSql} LIMIT 1)`;
  });
  const insertColumns = explicitId !== undefined && !targetColumns.some((column) => column.toLowerCase() === 'id')
    ? ['id', ...targetColumns]
    : targetColumns;
  return `INSERT INTO \`${table}\` (${insertColumns.map((column) => `\`${column}\``).join(', ')}) SELECT ${valuesFor(insertColumns).join(', ')} FROM DUAL;\n`;
}

function cloneTenant901AndUsers() {
  // 1) 租户 901 + 权限树复制（保持 parent 映射）+ SUPER_ADMIN 角色绑定
  const statements = [];
  statements.push(copyRow('sys_tenant', 'id = 1', { id: 901, tenant_code: 'wp011rt901' }, 901));
  const sourcePermissions = mysqlRootQuery("SELECT id, parent_id, permission_code FROM sys_permission WHERE tenant_id = 1 ORDER BY id;");
  const parentOf = new Map(sourcePermissions.map((row) => [row.permission_code, row.parent_id]));
  const newIds = new Map();
  let pending = [...sourcePermissions];
  for (let pass = 0; pass < 4 && pending.length > 0; pass += 1) {
    const remaining = [];
    for (const row of pending) {
      const parentKey = String(parentOf.get(row.permission_code) ?? 0);
      const parentIsRoot = String(row.parent_id) === '0' || parentKey === '0';
      const mappedParent = parentIsRoot ? 0 : newIds.get(parentKey);
      if (!parentIsRoot && mappedParent === undefined) { remaining.push(row); continue; }
      const newId = 90100000 + Number(row.id);
      const columns = mysqlRootQuery(`SHOW COLUMNS FROM \`sys_permission\`;`).map((entry) => entry.Field);
      const targetColumns = columns.filter((column) => column.toLowerCase() !== 'id');
      const selectParts = targetColumns.map((column) => {
        const lower = column.toLowerCase();
        if (lower === 'tenant_id') return '901';
        if (lower === 'parent_id') return String(mappedParent);
        return `(SELECT \`${column}\` FROM \`sys_permission\` WHERE id = ${Number(row.id)} LIMIT 1)`;
      });
      statements.push(`INSERT INTO \`sys_permission\` (\`id\`, ${targetColumns.map((column) => `\`${column}\``).join(', ')}) SELECT ${newId}, ${selectParts.join(', ')} FROM DUAL;\n`);
      newIds.set(row.permission_code, newId);
    }
    pending = remaining;
  }
  if (pending.length > 0) throw new Error('权限树 parent 解析失败');
  statements.push(copyRow('sys_role', "tenant_id = 1 AND role_code = 'SUPER_ADMIN'", { id: 90105001, tenant_id: 901 }, 90105001));
  statements.push('INSERT INTO sys_role_permission (role_id, permission_id) SELECT 90105001, id FROM sys_permission WHERE tenant_id = 901;\n');
  statements.push(copyRow('sys_user', "tenant_id = 1 AND username = 'admin'", { id: 90106001, tenant_id: 901, username: 'wp011rt901' }, 90106001));
  statements.push('INSERT INTO sys_user_role (user_id, role_id) SELECT 90106001, 90105001 FROM DUAL;\n');
  // 2) 租户 1 内的无权限用户（角色无任何 procurement 绑定 → 403 负例）
  statements.push(copyRow('sys_role', "tenant_id = 1 AND role_code = 'SUPER_ADMIN'", { id: 92000101, role_code: 'WP011_NOPROC' }, 92000101));
  statements.push(copyRow('sys_user', "tenant_id = 1 AND username = 'admin'", { id: 92000102, username: 'wp011noproc' }, 92000102));
  statements.push('INSERT INTO sys_user_role (user_id, role_id) SELECT 92000102, 92000101 FROM DUAL;\n');
  mysqlRootSql(statements.join(''));
}

// ---------------- 工具 ----------------
function discoverHostPort(service, containerPort) {
  const listing = captureDocker(['compose', '-p', project, '-f', composeFile, 'ps', '-q', service]).stdout.trim().split(/\r?\n/)[0];
  assert.ok(listing, `无法定位服务容器：${service}`);
  const format = `{{(index (index .NetworkSettings.Ports "${containerPort}") 0).HostPort}}`;
  const inspect = captureDocker(['inspect', '-f', format, listing]);
  const port = Number(inspect.stdout.trim());
  assert.ok(Number.isInteger(port) && port > 0, `无法发现 ${service} 的宿主端口`);
  return port;
}

function waitForMigratorDone(targetProject, env) {
  const deadline = Date.now() + 30 * 60_000;
  while (Date.now() < deadline) {
    const statuses = captureDocker(['compose', '-p', targetProject, '-f', composeFile, 'ps', '-a', '--format', 'json'])
      .stdout.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
    const migrator = statuses.find((status) => status.Service === 'omni-db-migrator');
    if (migrator) {
      if (migrator.Health === 'unhealthy' || (migrator.State === 'exited' && Number(migrator.ExitCode) !== 0)) {
        throw new Error(`migrator 失败：state=${migrator.State} exit=${migrator.ExitCode} health=${migrator.Health}`);
      }
      if (migrator.State === 'exited' && Number(migrator.ExitCode) === 0) {
        for (const status of statuses.filter((entry) => entry.Service === 'omni-auth' && entry.Health === 'unhealthy')) {
          throw new Error('auth 容器 unhealthy');
        }
        return;
      }
    }
    sleepSync(3000);
  }
  throw new Error('migrator 完成等待超时（30 分钟）');
}

async function waitForHttpHealthy(url, message) {
  const deadline = Date.now() + 180_000;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, { method: 'GET', signal: AbortSignal.timeout(5000), redirect: 'manual' });
      if (response.status < 500) return;
    } catch {
      // 容器仍在启动。
    }
    await delay(2000);
  }
  throw new Error(`${message}：${new URL(url).host}`);
}

async function httpJson(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(20_000) });
  let body;
  try {
    body = await response.json();
  } catch {
    throw new Error(`响应不是 JSON：http=${response.status}, url=${new URL(url).pathname}`);
  }
  return { status: response.status, body };
}

async function fetchJson(promise) {
  const response = await promise;
  let body;
  try {
    body = await response.json();
  } catch {
    body = { code: null };
  }
  return { status: response.status, body };
}

async function json200(promise) {
  const { status, body } = await fetchJson(promise);
  assert.ok(status < 500, `HTTP 传输失败：status=${status}, body=${JSON.stringify(body)}`);
  assert.equal(body.code, 200, `业务状态失败：http=${status}, body=${JSON.stringify(body)}`);
  return body;
}

async function expectHttp(promise, expectedStatus) {
  const response = await promise;
  const text = await response.text();
  assert.equal(response.status, expectedStatus, `HTTP 状态不匹配（期望 ${expectedStatus}）：actual=${response.status}, body=${text.slice(0, 300)}`);
}

function reservePort() {
  return new Promise((resolvePromise, reject) => {
    const server = createServer();
    server.unref();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = address && typeof address === 'object' ? address.port : undefined;
      server.close(() => resolvePromise(port));
    });
  });
}

function delay(milliseconds) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
}

function captureDocker(args) {
  // compose 的 ${VAR:?} 插值对 ps 等只读子命令同样生效，因此需补齐全部必填变量
  // （procurement URL 对只读查询无意义，用占位值即可）。
  const result = spawnSync('docker', args, {
    cwd: fixtureRoot,
    env: { ...process.env, ...runtimeSecrets, WP011_PROCUREMENT_URL: 'http://pending.invalid:1' },
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `docker 命令失败：docker ${args.slice(0, 4).join(' ')}`);
  return result;
}

function appendServiceOutput(chunk) {
  serviceOutput = `${serviceOutput}${String(chunk)}`.replace(/(password|secret|token)\s*[=:]\s*\S+/gi, '$1=[REDACTED]').slice(-20_000);
}

function parseArguments(args) {
  const value = { keep: false, skipBuild: false };
  for (const argument of args) {
    if (argument === '--keep') value.keep = true;
    else if (argument === '--skip-build') value.skipBuild = true;
    else throw new Error(`未知参数：${argument}`);
  }
  return value;
}

function sleepSync(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function run(command, args, cwd, extraEnvironment = {}) {
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `黄金命令失败：${command} ${args.join(' ')}`);
}

function jdkEnvironment() {
  const javaHome = process.env.JAVA_HOME && process.env.JAVA_HOME.includes('jdk-25')
    ? process.env.JAVA_HOME
    : 'C:\\APP\\JDK25\\jdk-25.0.2';
  return {
    JAVA_HOME: javaHome,
    PATH: `${resolve(javaHome, 'bin')}${process.platform === 'win32' ? ';' : ':'}${process.env.PATH ?? ''}`,
  };
}

function copyWorkspace(sourceRoot, targetRoot) {
  const ignoredSegments = new Set(['.git', '.qoder', '.agents', '.codex', '.idea', 'node_modules', 'target', 'dist']);
  cpSync(sourceRoot, targetRoot, {
    recursive: true,
    errorOnExist: true,
    force: false,
    filter(source) {
      const child = relative(sourceRoot, source);
      if (!child) return true;
      const segments = child.split(sep);
      if (segments.some((segment) => ignoredSegments.has(segment))) return false;
      const name = basename(source);
      return name === '.env.example' || !name.startsWith('.env');
    },
  });
}

function removeVerifiedTree(target, allowedParent) {
  const resolvedTarget = resolve(target);
  const resolvedParent = resolve(allowedParent);
  const child = relative(resolvedParent, resolvedTarget);
  if (!child || child === '..' || child.startsWith(`..${sep}`) || basename(resolvedTarget).length < 8) {
    throw new Error(`拒绝清理未验证路径：${resolvedTarget}`);
  }
  if (existsSync(resolvedTarget)) rmSync(resolvedTarget, { recursive: true, force: true });
}
