import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { parseDocument } from 'yaml';
import { renderCrudGeneration } from '../dist/src/crud-generator.js';
import { applyRenderedIntegration } from '../dist/src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const container = 'omni-g1-mysql-1';
const procurementContainer = 'omni-g1-omni-procurement-1';
const suffix = randomBytes(4).toString('hex');
const database = `omni_crud_runtime_${suffix}`;
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-crud-runtime-'));
const fixtureRoot = resolve(systemTempRoot, 'workspace');
const servicePort = 28216;
const managementPort = 28217;
let serviceProcess;
let serviceOutput = '';
let databaseCreated = false;

try {
  assertContainer();
  assertPortFree(servicePort);
  assertPortFree(managementPort);
  copyWorkspace(workspaceRoot, fixtureRoot);
  const spec = resolve(fixtureRoot, 'scaffold/specs/material-brand.yaml');
  const rendered = renderCrudGeneration(fixtureRoot, spec);
  applyRenderedIntegration(fixtureRoot, rendered);

  const ddlDocument = parseDocument(rendered.changes.find((change) =>
    change.target === 'database/changelog/procurement/generated-material-brand-0001.yaml')?.after ?? '').toJS();
  const ddl = ddlDocument.databaseChangeLog?.[0]?.changeSet?.changes?.[0]?.sql?.sql;
  const commonMqDocument = parseDocument(readFileSync(
    resolve(fixtureRoot, 'database/changelog/common/0001-mq-schema.yaml'), 'utf8')).toJS();
  const commonMqDdl = commonMqDocument.databaseChangeLog?.[0]?.changeSet?.changes?.[0]?.sql?.sql;
  assert.equal(typeof ddl, 'string');
  assert.equal(typeof commonMqDdl, 'string');
  adminSql(`CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL PRIVILEGES ON \`${database}\`.* TO 'omni_app'@'%'; FLUSH PRIVILEGES;`);
  databaseCreated = true;
  databaseSql(commonMqDdl);
  databaseSql(ddl);

  const backendRoot = resolve(fixtureRoot, 'omni-backend');
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  run(wrapper, ['-DskipTests', 'package', '-pl', 'omni-procurement', '-am'], backendRoot, jdkEnvironment());
  const jar = resolve(backendRoot, 'omni-procurement/target/omni-procurement-1.0.0-SNAPSHOT.jar');
  assert.ok(existsSync(jar), '生成后的采购 JAR 不存在');

  const procurementEnvironment = containerEnvironment(procurementContainer);
  const redisEnvironment = containerEnvironment('omni-g1-redis-1');
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
      MYSQL_URL: `jdbc:mysql://127.0.0.1:23316/${database}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`,
      MYSQL_USERNAME: 'omni_app',
      MYSQL_PASSWORD: requiredEnvironment(procurementEnvironment, 'MYSQL_PASSWORD'),
      REDIS_HOST: '127.0.0.1',
      REDIS_PORT: '26379',
      REDIS_PASSWORD: requiredEnvironment(redisEnvironment, 'REDIS_PASSWORD'),
      ROCKETMQ_NAME_SERVER: '127.0.0.1:30876',
      XXL_JOB_ENABLED: 'false',
      OMNI_INTERNAL_API_TOKEN: `runtime-${suffix}-internal-api-token-golden`,
    },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  serviceProcess.stdout.on('data', (chunk) => appendServiceOutput(chunk));
  serviceProcess.stderr.on('data', (chunk) => appendServiceOutput(chunk));
  await waitForHealth();
  await verifyHttpMatrix();

  console.log('golden CRUD runtime valid: direct=403, anonymous=401, noScope=403, crud=pass, duplicate=409, crossTenant=isolated, deleted=404');
} catch (error) {
  if (serviceOutput) console.error(serviceOutput.slice(-6000));
  throw error;
} finally {
  await stopService();
  cleanupDatabase();
  removeVerifiedTree(systemTempRoot, tmpdir());
}

async function verifyHttpMatrix() {
  const base = `http://127.0.0.1:${servicePort}/api/procurement/material-brands`;
  await expectCode(fetch(`${base}/list?page=1&size=10`), 403);
  await expectCode(fetch(`${base}/list?page=1&size=10`, { headers: { 'X-Gateway-Forwarded': 'true' } }), 401);
  await expectCode(request(base, 101, '', '/list?page=1&size=10'), 403);

  const created = await expectCode(request(base, 101, 'procurement:material-brand:create', '', {
    method: 'POST',
    body: JSON.stringify({ brandCode: 'BRAND-RUNTIME', brandName: '运行时品牌', status: 1, remark: 'golden' }),
  }), 200);
  const id = created.data.id;
  assert.match(id, /^\d+$/);
  assert.equal(created.data.version, 0);

  const listed = await expectCode(request(base, 101, 'procurement:material-brand:list', '/list?page=1&size=10'), 200);
  assert.equal(listed.data.total, 1);
  assert.equal(listed.data.records[0].id, id);
  const otherTenant = await expectCode(request(base, 202, 'procurement:material-brand:list', '/list?page=1&size=10'), 200);
  assert.equal(otherTenant.data.total, 0);

  await expectCode(request(base, 202, 'procurement:material-brand:update', `/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ version: 0, brandName: '越权修改', status: 1, remark: null }),
  }), 404, 200);
  const updated = await expectCode(request(base, 101, 'procurement:material-brand:update', `/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ version: 0, brandName: '运行时品牌（更新）', status: 1, remark: 'updated' }),
  }), 200);
  assert.equal(updated.data.version, 1);

  await expectCode(request(base, 101, 'procurement:material-brand:create', '', {
    method: 'POST',
    body: JSON.stringify({ brandCode: 'BRAND-RUNTIME', brandName: '重复品牌', status: 1 }),
  }), 409, 200);
  await expectCode(request(base, 101, 'procurement:material-brand:delete', `/${id}?version=1`, { method: 'DELETE' }), 200);
  await expectCode(request(base, 101, 'procurement:material-brand:list', `/${id}`), 404, 200);
}

function request(base, tenantId, scope, path, options = {}) {
  return fetch(`${base}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-Gateway-Forwarded': 'true',
      'X-User-Id': '9001',
      'X-User-Name': 'crud-golden',
      'X-Tenant-Id': String(tenantId),
      'X-User-Roles': 'SUPER_ADMIN',
      'X-User-Scopes': scope,
      ...(options.headers ?? {}),
    },
  });
}

async function expectCode(responsePromise, expected, expectedHttp = expected) {
  const response = await responsePromise;
  const body = await response.json();
  assert.equal(response.status, expectedHttp, `HTTP 状态不匹配：expected=${expectedHttp}, bodyCode=${body.code}`);
  assert.equal(body.code, expected, `响应 code 不匹配：expected=${expected}`);
  return body;
}

async function waitForHealth() {
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    if (serviceProcess.exitCode !== null) throw new Error(`采购黄金服务提前退出：${serviceProcess.exitCode}`);
    try {
      const response = await fetch(`http://127.0.0.1:${managementPort}/actuator/health`);
      if (response.status === 200) return;
    } catch {
      // 服务仍在启动。
    }
    await new Promise((resolvePromise) => setTimeout(resolvePromise, 1000));
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

function appendServiceOutput(chunk) {
  serviceOutput = `${serviceOutput}${String(chunk)}`.slice(-20_000);
}

function assertContainer() {
  const result = spawnSync('docker', ['inspect', '--format', '{{.State.Running}}', container], { encoding: 'utf8' });
  assert.equal(result.status, 0);
  assert.equal(result.stdout.trim(), 'true');
}

function containerEnvironment(name) {
  const result = spawnSync('docker', ['inspect', '--format', '{{json .Config.Env}}', name], { encoding: 'utf8' });
  assert.equal(result.status, 0, `无法读取容器环境：${name}`);
  return Object.fromEntries(JSON.parse(result.stdout).map((entry) => {
    const index = entry.indexOf('=');
    return [entry.slice(0, index), entry.slice(index + 1)];
  }));
}

function requiredEnvironment(environment, name) {
  const value = environment[name];
  assert.ok(value, `容器缺少环境变量：${name}`);
  return value;
}

function assertPortFree(port) {
  const result = spawnSync('powershell.exe', ['-NoProfile', '-Command', `(Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Measure-Object).Count`], { encoding: 'utf8' });
  assert.equal(result.status, 0);
  assert.equal(result.stdout.trim(), '0', `端口 ${port} 已被占用`);
}

function adminSql(statement) {
  const result = mysql(undefined, statement);
  assert.equal(result.status, 0, '管理员 SQL 执行失败');
}

function databaseSql(statement) {
  const result = mysql(database, statement);
  assert.equal(result.status, 0, '运行时数据库 SQL 执行失败');
}

function mysql(targetDatabase, input) {
  if (targetDatabase) assert.match(targetDatabase, /^omni_crud_runtime_[a-f0-9]{8}$/);
  const command = `exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 ${targetDatabase ?? ''}`;
  return spawnSync('docker', ['exec', '-i', container, 'sh', '-lc', command], {
    input,
    encoding: 'utf8',
    maxBuffer: 10 * 1024 * 1024,
  });
}

function cleanupDatabase() {
  if (!databaseCreated) return;
  if (!/^omni_crud_runtime_[a-f0-9]{8}$/.test(database)) throw new Error(`拒绝清理未验证数据库：${database}`);
  const result = mysql(undefined, `REVOKE ALL PRIVILEGES ON \`${database}\`.* FROM 'omni_app'@'%'; DROP DATABASE IF EXISTS \`${database}\`; FLUSH PRIVILEGES;`);
  if (result.status !== 0) throw new Error('运行时黄金数据库清理失败');
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

function removeVerifiedTree(target, allowedParent) {
  const resolvedTarget = resolve(target);
  const resolvedParent = resolve(allowedParent);
  const child = relative(resolvedParent, resolvedTarget);
  if (!child || child === '..' || child.startsWith(`..${sep}`) || basename(resolvedTarget).length < 8) {
    throw new Error(`拒绝清理未验证路径：${resolvedTarget}`);
  }
  if (existsSync(resolvedTarget)) rmSync(resolvedTarget, { recursive: true, force: true });
}
