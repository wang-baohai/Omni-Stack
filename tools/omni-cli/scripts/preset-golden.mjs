import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, globSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { parseDocument } from 'yaml';
import { loadCatalog } from '../dist/src/catalog.js';
import {
  applyPresetGeneration,
  planPresetGeneration,
  validateGeneratedPreset,
} from '../dist/src/preset-generator.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const OFFICIAL_PRESETS = ['core', 'workflow', 'crm', 'supply-chain', 'full'];
const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const sourceCatalog = loadCatalog(workspaceRoot);
const argumentsMap = parseArguments(process.argv.slice(2));
const presetIds = argumentsMap.presets ?? OFFICIAL_PRESETS;
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-golden-'));

try {
  for (const presetId of presetIds) {
    assert.ok(OFFICIAL_PRESETS.includes(presetId), `未知正式预设：${presetId}`);
    const target = resolve(systemTempRoot, presetId);
    const plan = planPresetGeneration(workspaceRoot, presetId, target);
    applyPresetGeneration(workspaceRoot, plan);
    const lock = validateGeneratedPreset(target, presetId);
    validateNoOmittedResiduals(target, sourceCatalog, new Set(lock.modules.map((module) => module.id)));

    if (!argumentsMap.structuralOnly) {
      const backendRoot = resolve(target, 'omni-backend');
      const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
      run(wrapper, ['clean', argumentsMap.verifyOnly ? 'verify' : 'install'], backendRoot, jdkEnvironment());
      const frontendRoot = resolve(target, 'omni-frontend');
      run('npm', ['ci'], frontendRoot);
      run('npm', ['run', 'lint'], frontendRoot);
      run('npm', ['run', 'build'], frontendRoot);
      run('docker', ['compose', 'config', '--quiet'], target, composeEnvironment());
    }
    console.log(`preset golden valid: ${presetId}, modules=${lock.modules.length}`);
  }
} finally {
  if (argumentsMap.keep) {
    console.log(`preset golden workspace retained: ${systemTempRoot}`);
  } else {
    removeVerifiedTree(systemTempRoot, tmpdir());
  }
}

function parseArguments(args) {
  const value = { presets: undefined, structuralOnly: false, verifyOnly: false, keep: false };
  for (const argument of args) {
    if (argument === '--structural-only') value.structuralOnly = true;
    else if (argument === '--verify-only') value.verifyOnly = true;
    else if (argument === '--keep') value.keep = true;
    else if (argument.startsWith('--presets=')) {
      value.presets = argument.slice('--presets='.length).split(',').map((entry) => entry.trim()).filter(Boolean);
      assert.ok(value.presets.length > 0, '--presets 至少包含一个预设');
    } else {
      throw new Error(`未知参数：${argument}`);
    }
  }
  return value;
}

function validateNoOmittedResiduals(target, catalog, selected) {
  const selectedDefinitions = catalog.modules.filter((module) => selected.has(module.id));
  const omittedDefinitions = catalog.modules.filter((module) => !selected.has(module.id));
  const retainedDocs = new Set(selectedDefinitions.flatMap((module) => module.docs));
  const retainedChangelogs = new Set(selectedDefinitions.flatMap((module) => module.database.changelogs));

  for (const module of omittedDefinitions) {
    for (const artifact of module.backendModules) {
      assert.equal(existsSync(resolve(target, 'omni-backend', artifact)), false, `残留 Maven 模块：${artifact}`);
    }
    for (const pattern of [
      ...module.frontend.viewGlobs,
      ...module.frontend.componentGlobs,
      ...module.frontend.apiGlobs,
    ]) {
      assert.deepEqual(globSync(pattern, { cwd: target }), [], `残留前端资源：${pattern}`);
    }
    for (const document of module.docs) {
      if (!retainedDocs.has(document)) assert.equal(existsSync(resolve(target, document)), false, `残留文档：${document}`);
    }
    for (const changelog of module.database.changelogs) {
      if (!retainedChangelogs.has(changelog)) {
        assert.equal(existsSync(resolve(target, changelog)), false, `残留数据库变更：${changelog}`);
      }
    }
  }

  const parentPom = readFileSync(resolve(target, 'omni-backend/pom.xml'), 'utf8');
  const actualMavenModules = [...parentPom.matchAll(/<module>([^<]+)<\/module>/g)].map((match) => match[1]);
  assert.deepEqual(
    [...actualMavenModules].sort(),
    unique(selectedDefinitions.flatMap((module) => module.backendModules)).sort(),
    'Maven 模块矩阵漂移',
  );

  const compose = parseDocument(readFileSync(resolve(target, 'docker-compose.yml'), 'utf8')).toJS();
  const actualComposeServices = Object.keys(compose.services ?? {});
  assert.deepEqual(
    [...actualComposeServices].sort(),
    unique(selectedDefinitions.flatMap((module) => module.composeServices)).sort(),
    'Compose 服务矩阵漂移',
  );
  for (const [serviceName, service] of Object.entries(compose.services ?? {})) {
    const dependencies = Array.isArray(service.depends_on) ? service.depends_on : Object.keys(service.depends_on ?? {});
    for (const dependency of dependencies) {
      assert.ok(actualComposeServices.includes(dependency), `${serviceName} 依赖已裁掉的 Compose 服务 ${dependency}`);
    }
  }

  const gateway = parseDocument(readFileSync(
    resolve(target, 'omni-backend/omni-gateway/src/main/resources/application.yml'),
    'utf8',
  )).toJS();
  const gatewayRoutes = gateway.spring.cloud.gateway.server.webflux.routes;
  assert.deepEqual(gatewayRoutes.map((route) => route.id), unique(selectedDefinitions.flatMap((module) => module.gatewayRoutes)), 'Gateway 路由矩阵漂移');
  const omittedServiceNames = new Set(omittedDefinitions.flatMap((module) => module.backendModules));
  for (const route of gatewayRoutes) {
    const rendered = JSON.stringify(route);
    for (const serviceName of omittedServiceNames) assert.doesNotMatch(rendered, new RegExp(`/${escapeRegExp(serviceName)}/`));
  }

  const seedManifest = parseDocument(readFileSync(resolve(target, 'database/seed/manifest.yaml'), 'utf8')).toJS();
  const expectedSeedIds = new Set(selectedDefinitions.flatMap((module) => module.database.seedSourceIds));
  assert.deepEqual(new Set((seedManifest.sources ?? []).map((source) => source.id)), expectedSeedIds, '数据库种子矩阵漂移');
  const authSeed = readFileSync(resolve(target, 'scripts/sql/seed/auth.sql'), 'utf8');
  for (const root of omittedDefinitions.flatMap((module) => module.permissionRoots)) {
    assert.doesNotMatch(authSeed, new RegExp(`'${escapeRegExp(root)}(?::|')`), `残留权限根：${root}`);
  }

  const removedProducerDestinations = new Set(omittedDefinitions.flatMap((module) => module.mq.producers)
    .filter((destination) => !selectedDefinitions.some((module) => module.mq.producers.includes(destination))));
  for (const application of globSync('omni-backend/*/src/main/resources/application.yml', { cwd: target })) {
    const content = readFileSync(resolve(target, application), 'utf8');
    for (const destination of removedProducerDestinations) {
      assert.doesNotMatch(content, new RegExp(`destination:\\s*${escapeRegExp(destination)}(?:\\s|$)`), `残留 MQ 绑定：${destination}`);
    }
  }

  const retainedI18nPrefixes = new Set(selectedDefinitions.flatMap((module) => module.frontend.i18nPrefixes));
  const removedI18nPrefixes = new Set(omittedDefinitions.flatMap((module) => module.frontend.i18nPrefixes)
    .filter((prefix) => !retainedI18nPrefixes.has(prefix)));
  for (const locale of globSync('omni-frontend/src/locales/*.ts', { cwd: target })) {
    const content = readFileSync(resolve(target, locale), 'utf8');
    for (const prefix of removedI18nPrefixes) {
      assert.doesNotMatch(content, new RegExp(`^  ${escapeRegExp(prefix)}: \\{$`, 'm'), `残留国际化前缀：${prefix}`);
    }
  }

  const workflowSeedPath = resolve(target, 'scripts/sql/seed/workflow.sql');
  if (existsSync(workflowSeedPath)) {
    const workflowSeed = readFileSync(workflowSeedPath, 'utf8');
    const businessModels = new Map([
      ['supplier-onboarding', 'srm'],
      ['procurement-approval', 'procurement'],
      ['asset-transfer', 'asset'],
      ['asset-disposal', 'asset'],
    ]);
    for (const [modelKey, owner] of businessModels) {
      if (!selected.has(owner)) assert.doesNotMatch(workflowSeed, new RegExp(escapeRegExp(modelKey)), `残留工作流模型：${modelKey}`);
    }
  }
}

function run(command, args, cwd, extraEnvironment = {}) {
  console.log(`preset golden command: ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `预设黄金命令失败：${command} ${args.join(' ')}`);
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

function composeEnvironment() {
  return {
    MYSQL_ROOT_PASSWORD: 'golden-only-root-password',
    OMNI_DB_PASSWORD: 'golden-only-app-password',
    REDIS_PASSWORD: 'golden-only-redis-password',
    XXL_JOB_ACCESS_TOKEN: 'golden-only-xxl-token',
    XXL_JOB_ADMIN_USERNAME: 'golden-admin',
    XXL_JOB_ADMIN_PASSWORD: 'golden-only-admin-password',
    NACOS_AUTH_TOKEN: 'golden-only-nacos-token-value-that-is-long-enough-for-validation',
    NACOS_AUTH_IDENTITY_KEY: 'golden-only-key',
    NACOS_AUTH_IDENTITY_VALUE: 'golden-only-value',
    JWK_ENCRYPT_KEY: 'golden-only-jwk-encryption-key-32bytes',
    OAUTH2_STATE_SECRET: 'golden-only-oauth2-state-secret',
    OMNI_INTERNAL_API_TOKEN: 'golden-only-internal-token',
  };
}

function unique(values) {
  return [...new Set(values)];
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
