import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { loadComposeApplication } from '../dist/src/compose-model.js';
import {
  applyPresetGeneration,
  planPresetGeneration,
  validateGeneratedPreset,
} from '../dist/src/preset-generator.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const OFFICIAL_PRESETS = ['core', 'workflow', 'crm', 'supply-chain', 'full'];
const BUSINESS_MODULES = ['workflow', 'crm', 'srm', 'procurement', 'asset'];
const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const argumentsMap = parseArguments(process.argv.slice(2));
const presetIds = argumentsMap.presets ?? OFFICIAL_PRESETS;
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-runtime-'));

try {
  for (const presetId of presetIds) {
    await verifyPresetRuntime(presetId);
  }
} finally {
  if (argumentsMap.keep) {
    console.log(`preset runtime workspace retained: ${systemTempRoot}`);
  } else {
    removeVerifiedTree(systemTempRoot, tmpdir());
  }
}

async function verifyPresetRuntime(presetId) {
  assert.ok(OFFICIAL_PRESETS.includes(presetId), `未知正式预设：${presetId}`);
  const suffix = randomBytes(4).toString('hex');
  const project = `omni-preset-runtime-${presetId.replaceAll(/[^a-z0-9]/g, '-')}-${suffix}`;
  assertProjectName(project);
  const target = resolve(systemTempRoot, `${presetId}-${suffix}`);
  const lock = applyPresetGeneration(workspaceRoot, planPresetGeneration(workspaceRoot, presetId, target));
  validateGeneratedPreset(target, presetId);

  const composeText = ['compose.infra.yaml', 'compose.apps.yaml']
    .map((path) => readFileSync(resolve(target, path), 'utf8')).join('\n');
  const compose = loadComposeApplication(target);
  const reservations = await reserveComposePorts(composeText);
  const environment = runtimeEnvironment(project, reservations, composeText);
  let started = false;
  let primaryError;

  try {
    runCompose(project, ['config', '--quiet'], target, environment);
    await releaseReservations(reservations);
    runCompose(project, ['up', '-d', ...(argumentsMap.skipBuild ? [] : ['--build'])], target, environment);
    started = true;
    const statuses = await waitForHealthyProject(project, target, environment, Object.keys(compose.services));
    await verifyHttpRuntime(project, environment, new Set(lock.modules.map((module) => module.id)));
    if (presetId === 'full') await verifyFullE2e(target, environment);
    console.log(
      `preset runtime valid: ${presetId}, services=${statuses.length}, modules=${lock.modules.length}, `
      + 'fresh=pass, health=pass, login=pass, menus=pass, base-dict=pass, '
      + `auth-boundary=pass, tenant-identity=pass${presetId === 'full' ? ', e2e=18/18' : ''}`,
    );
  } catch (error) {
    primaryError = error;
    if (started || projectExists(project, target, environment)) printProjectLogs(project, target, environment);
    throw error;
  } finally {
    await releaseReservations(reservations);
    if (!argumentsMap.keep) {
      try {
        downProject(project, target, environment);
      } catch (cleanupError) {
        if (!primaryError) throw cleanupError;
        console.error(`隔离 Compose 清理同时失败，保留原始错误：${cleanupError.message}`);
      }
    }
  }
}

async function verifyHttpRuntime(project, environment, selectedModules) {
  const gatewayPort = requiredEnvironment(environment, 'OMNI_GATEWAY_HOST_PORT');
  const frontendPort = requiredEnvironment(environment, 'OMNI_FRONTEND_HOST_PORT');
  const gateway = `http://127.0.0.1:${gatewayPort}`;
  const frontendResponse = await fetchWithTimeout(`http://127.0.0.1:${frontendPort}/`);
  assert.equal(frontendResponse.status, 200, '前端首页不可访问');

  const tenantHeaders = { 'X-Tenant-Id': '1' };
  const anonymousProtected = await fetchWithTimeout(`${gateway}/api/base/dict/type/list?page=1&size=10`, {
    headers: {
      ...tenantHeaders,
      'X-User-Id': '1',
      'X-User-Roles': 'ADMIN',
    },
  });
  assert.equal(anonymousProtected.status, 401, '伪造身份头未被网关拒绝');

  const captcha = await requestJson(`${gateway}/api/auth/captcha`, { headers: tenantHeaders });
  assert.equal(captcha.code, 200, '验证码接口业务状态失败');
  assert.ok(captcha.data?.captchaKey, '验证码接口未返回 captchaKey');
  const captchaCode = redisCaptcha(project, environment, captcha.data.captchaKey);
  assert.ok(captchaCode, '隔离 Redis 中未找到验证码');

  const login = await requestJson(`${gateway}/api/auth/login`, {
    method: 'POST',
    headers: { ...tenantHeaders, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: 'admin',
      password: 'admin123',
      tenantId: 1,
      captchaKey: captcha.data.captchaKey,
      captchaCode,
    }),
  });
  assert.equal(login.code, 200, '开发种子管理员登录失败');
  const token = login.data?.accessToken;
  assert.ok(token, '登录响应未返回访问令牌');
  const authenticatedHeaders = { ...tenantHeaders, Authorization: `Bearer ${token}` };

  const menus = await requestJson(`${gateway}/api/auth/menus`, { headers: authenticatedHeaders });
  assert.equal(menus.code, 200, '动态菜单接口业务状态失败');
  const serializedMenus = JSON.stringify(menus.data ?? []);
  for (const moduleId of BUSINESS_MODULES) {
    if (!selectedModules.has(moduleId)) {
      assert.doesNotMatch(serializedMenus, new RegExp(`(?:^|[/.:_-])${moduleId}(?:$|[/.:_-])`, 'i'), `菜单残留已裁剪模块：${moduleId}`);
    }
  }

  const dictTypes = await requestJson(`${gateway}/api/base/dict/type/list?page=1&size=10`, {
    headers: authenticatedHeaders,
  });
  assert.equal(dictTypes.code, 200, '基础字典列表业务状态失败');
  assert.ok(dictTypes.data, '基础字典列表缺少数据');

  const spoofedTenant = await requestJson(`${gateway}/api/base/dict/type/list?page=1&size=10`, {
    headers: { ...authenticatedHeaders, 'X-Tenant-Id': '999999' },
  });
  assert.deepEqual(spoofedTenant, dictTypes, '客户端租户头覆盖了 JWT 租户身份');
}

function verifyFullE2e(target, environment) {
  const backend = resolve(target, 'omni-backend');
  const frontend = resolve(target, 'omni-frontend');
  const tokenFile = resolve(systemTempRoot, `e2e-tokens-${randomBytes(8).toString('hex')}.json`);
  const javaHome = process.env.JAVA_HOME || 'C:\\APP\\JDK25\\jdk-25.0.2';
  const javaPath = resolve(javaHome, 'bin');
  const commandEnvironment = {
    ...environment,
    JAVA_HOME: javaHome,
    PATH: `${javaPath}${sep}${process.env.PATH ?? ''}`,
    E2E_FIXTURE_OUTPUT: tokenFile,
    E2E_FIXTURE_DB_URL: `jdbc:mysql://127.0.0.1:${requiredEnvironment(environment, 'OMNI_MYSQL_HOST_PORT')}`
      + '/omni_auth?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
      + '&allowPublicKeyRetrieval=true',
    E2E_FIXTURE_DB_USERNAME: 'omni_app',
    E2E_FIXTURE_DB_PASSWORD: requiredEnvironment(environment, 'OMNI_DB_PASSWORD'),
    E2E_FIXTURE_REDIS_HOST: '127.0.0.1',
    E2E_FIXTURE_REDIS_PORT: requiredEnvironment(environment, 'OMNI_REDIS_HOST_PORT'),
    E2E_FIXTURE_REDIS_PASSWORD: requiredEnvironment(environment, 'REDIS_PASSWORD'),
    E2E_FIXTURE_JWK_ENCRYPT_KEY: requiredEnvironment(environment, 'JWK_ENCRYPT_KEY'),
  };

  try {
    run(platformCommand('mvnw', true), ['-pl', 'omni-auth', '-am', 'install', '-DskipTests'], backend, commandEnvironment);
    run(platformCommand('mvnw', true), [
      '-pl', 'omni-auth',
      'org.codehaus.mojo:exec-maven-plugin:3.5.0:java',
      '-Dexec.mainClass=com.omni.auth.e2e.E2eTokenFixture',
      '-Dexec.classpathScope=test',
    ], backend, commandEnvironment);
    assert.ok(existsSync(tokenFile), 'E2E 令牌夹具未生成输出文件');
    const tokens = JSON.parse(readFileSync(tokenFile, 'utf8'));
    assert.ok(tokens.adminToken && tokens.employeeToken && tokens.supplierToken, 'E2E 令牌夹具输出不完整');

    run(platformCommand('npm'), ['ci'], frontend, environment);
    run(platformCommand('npm'), ['run', 'test:e2e'], frontend, {
      ...environment,
      E2E_BASE_URL: `http://127.0.0.1:${requiredEnvironment(environment, 'OMNI_FRONTEND_HOST_PORT')}`,
      E2E_ADMIN_TOKEN: tokens.adminToken,
      E2E_EMPLOYEE_TOKEN: tokens.employeeToken,
      E2E_SUPPLIER_TOKEN: tokens.supplierToken,
      E2E_MUTATIONS: 'true',
    });
  } finally {
    if (existsSync(tokenFile)) rmSync(tokenFile, { force: true });
  }
}

async function waitForHealthyProject(project, cwd, environment, expectedServices) {
  const deadline = Date.now() + 12 * 60_000;
  while (Date.now() < deadline) {
    const statuses = composeStatuses(project, cwd, environment);
    const byService = new Map(statuses.map((status) => [status.Service, status]));
    const failed = statuses.find((status) =>
      status.Health === 'unhealthy'
      || (status.State === 'exited' && (status.Service !== 'omni-db-migrator' || Number(status.ExitCode) !== 0)));
    if (failed) throw new Error(`Compose 服务失败：${failed.Service}, state=${failed.State}, health=${failed.Health}`);
    const ready = expectedServices.every((service) => {
      const status = byService.get(service);
      if (!status) return false;
      if (service === 'omni-db-migrator') return status.State === 'exited' && Number(status.ExitCode) === 0;
      return status.State === 'running' && (!status.Health || status.Health === 'healthy');
    });
    if (ready) return statuses;
    await delay(2_000);
  }
  throw new Error(`Compose 健康检查超时：${project}`);
}

function composeStatuses(project, cwd, environment) {
  const result = capture('docker', ['compose', '-p', project, 'ps', '-a', '--format', 'json'], cwd, environment);
  return result.stdout.split(/\r?\n/).filter(Boolean).map((line) => JSON.parse(line));
}

function redisCaptcha(project, environment, captchaKey) {
  assert.match(captchaKey, /^[0-9a-f-]{16,}$/i, '验证码 Key 格式非法');
  const result = capture('docker', [
    'compose', '-p', project, 'exec', '-T', 'redis',
    'redis-cli', '--raw', '-a', requiredEnvironment(environment, 'REDIS_PASSWORD'),
    'GET', `captcha:${captchaKey}`,
  ], systemTempRoot, environment);
  return result.stdout.trim();
}

function runCompose(project, args, cwd, environment) {
  assertProjectName(project);
  run('docker', ['compose', '-p', project, ...args], cwd, environment);
}

function downProject(project, cwd, environment) {
  assertProjectName(project);
  const result = spawnSync('docker', ['compose', '-p', project, 'down', '-v', '--remove-orphans'], {
    cwd,
    env: { ...process.env, ...environment },
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const detail = result.error?.message ?? result.stderr ?? result.stdout ?? `exit=${result.status}`;
    throw new Error(`隔离 Compose 清理失败：${project}\n${detail}`);
  }
}

function projectExists(project, cwd, environment) {
  const result = spawnSync('docker', ['compose', '-p', project, 'ps', '-a', '-q'], {
    cwd,
    env: { ...process.env, ...environment },
    encoding: 'utf8',
  });
  return result.status === 0 && result.stdout.trim().length > 0;
}

function printProjectLogs(project, cwd, environment) {
  assertProjectName(project);
  const result = spawnSync('docker', ['compose', '-p', project, 'logs', '--no-color', '--tail', '160'], {
    cwd,
    env: { ...process.env, ...environment },
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  });
  const output = `${result.stdout ?? ''}\n${result.stderr ?? ''}`.trim();
  if (output) console.error(output.slice(-16_000));
}

function runtimeEnvironment(project, reservations, composeText) {
  const environment = {
    COMPOSE_PROGRESS: 'plain',
    MYSQL_ROOT_PASSWORD: 'runtime-golden-root-only',
    OMNI_DB_PASSWORD: 'runtime-golden-app-only',
    REDIS_PASSWORD: 'runtime-golden-redis-only',
    XXL_JOB_ACCESS_TOKEN: 'runtime-golden-xxl-token',
    XXL_JOB_ADMIN_USERNAME: 'runtime-golden-admin',
    XXL_JOB_ADMIN_PASSWORD: 'runtime-golden-admin-only',
    NACOS_AUTH_TOKEN: 'cnVudGltZS1nb2xkZW4tbmFjb3MtdG9rZW4tcnVudGltZS1nb2xkZW4tbmFjb3M=',
    NACOS_AUTH_IDENTITY_KEY: 'runtime-golden-key',
    NACOS_AUTH_IDENTITY_VALUE: 'runtime-golden-value',
    JWK_ENCRYPT_KEY: 'runtime-golden-jwk-key-32bytes!',
    OAUTH2_STATE_SECRET: 'runtime-golden-oauth-state-secret',
    OMNI_INTERNAL_API_TOKEN: 'runtime-golden-internal-api-token',
  };
  for (const [name, reservation] of reservations) environment[name] = String(reservation.port);
  for (const name of findVolumeEnvironmentNames(composeText)) {
    environment[name] = `${project}-${name.toLowerCase().replaceAll('_', '-')}`;
  }
  return environment;
}

async function reserveComposePorts(composeText) {
  const names = [...new Set([...composeText.matchAll(/\$\{(OMNI_[A-Z0-9_]*HOST_PORT)(?::|})/g)].map((match) => match[1]))];
  const reservations = new Map();
  for (const name of names) reservations.set(name, await reservePort());
  return reservations;
}

function findVolumeEnvironmentNames(composeText) {
  return [...new Set([...composeText.matchAll(/\$\{(OMNI_[A-Z0-9_]*VOLUME_NAME)(?::|})/g)].map((match) => match[1]))];
}

function reservePort() {
  return new Promise((resolvePromise, reject) => {
    const server = createServer();
    server.unref();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      assert.ok(address && typeof address === 'object');
      resolvePromise({ port: address.port, server });
    });
  });
}

async function releaseReservations(reservations) {
  await Promise.all([...reservations.values()].map((reservation) => new Promise((resolvePromise) => {
    if (!reservation.server.listening) return resolvePromise();
    reservation.server.close(resolvePromise);
  })));
}

async function requestJson(url, options = {}) {
  const response = await fetchWithTimeout(url, options);
  const body = await response.json();
  assert.ok(response.ok, `HTTP 请求失败：${response.status}, url=${new URL(url).pathname}`);
  return body;
}

function fetchWithTimeout(url, options = {}) {
  return fetch(url, { ...options, signal: AbortSignal.timeout(20_000) });
}

function run(command, args, cwd, extraEnvironment = {}) {
  console.log(`preset runtime command: ${command} ${args.join(' ')}`);
  const invocation = commandInvocation(command, args);
  const result = spawnSync(invocation.command, invocation.args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    stdio: 'inherit',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `预设运行命令失败：${command} ${args.join(' ')}`);
}

function commandInvocation(command, args) {
  if (process.platform !== 'win32' || !command.toLowerCase().endsWith('.cmd')) {
    return { command, args };
  }
  const commandLine = [command, ...args].map(quoteWindowsCommandArgument).join(' ');
  return { command: process.env.ComSpec || 'cmd.exe', args: ['/d', '/c', commandLine] };
}

function quoteWindowsCommandArgument(value) {
  assert.doesNotMatch(value, /[\r\n&|<>^%!]/, 'Windows 命令参数包含不安全字符');
  if (!/[\t "]/u.test(value)) return value;
  return `"${value.replaceAll('"', '""')}"`;
}

function platformCommand(command, workspaceLocal = false) {
  if (process.platform === 'win32') return `${command}.cmd`;
  return workspaceLocal ? `./${command}` : command;
}

function capture(command, args, cwd, extraEnvironment = {}) {
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `命令失败：${command} ${args.slice(0, 5).join(' ')}`);
  return result;
}

function requiredEnvironment(environment, name) {
  const value = environment[name];
  assert.ok(value, `缺少运行时环境变量：${name}`);
  return value;
}

function assertProjectName(project) {
  assert.match(project, /^omni-preset-runtime-(?:core|workflow|crm|supply-chain|full)-[a-f0-9]{8}$/);
}

function parseArguments(args) {
  const value = { presets: undefined, keep: false, skipBuild: false };
  for (const argument of args) {
    if (argument === '--keep') value.keep = true;
    else if (argument === '--skip-build') value.skipBuild = true;
    else if (argument.startsWith('--presets=')) {
      value.presets = argument.slice('--presets='.length).split(',').map((entry) => entry.trim()).filter(Boolean);
      assert.ok(value.presets.length > 0, '--presets 至少包含一个预设');
    } else {
      throw new Error(`未知参数：${argument}`);
    }
  }
  return value;
}

function delay(milliseconds) {
  return new Promise((resolvePromise) => setTimeout(resolvePromise, milliseconds));
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
