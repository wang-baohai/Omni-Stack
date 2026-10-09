import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { loadCatalog, resolveModuleClosure, validateCatalogGraph, validateCatalogResources, validateClosureRuntimeDependencies } from '../src/catalog.js';
import {
  composePortOverrides,
  composeProjectName,
  loadComposeApplication,
  parseHostPortMapping,
  shiftedPortEnv,
} from '../src/compose-model.js';
import { CliError } from '../src/errors.js';
import { resolvePreset } from '../src/presets.js';
import type { ModuleCatalog, ModuleDefinition } from '../src/types.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

function cloneCatalog(catalog: ModuleCatalog): ModuleCatalog {
  return structuredClone(catalog);
}

describe('module catalog', () => {
  it('validates the repository catalog', () => {
    const catalog = loadCatalog(workspaceRoot);
    validateCatalogResources(workspaceRoot, catalog);
    assert.equal(catalog.version, '1.0.0');
    assert.ok(catalog.modules.some((module) => module.id === 'asset'));
  });

  it('resolves dependencies in catalog order', () => {
    const catalog = loadCatalog(workspaceRoot);
    assert.deepEqual(resolveModuleClosure(catalog, ['asset']), [
      'platform', 'auth', 'base', 'workflow', 'srm', 'procurement', 'asset',
    ]);
  });

  it('rejects duplicate and forward dependencies', () => {
    assert.throws(
      () => validateCatalogGraph({
        version: '1.0.0',
        modules: [
          {
            id: 'broken',
            artifactId: 'omni-broken',
            kind: 'business',
            version: '1.0.0-SNAPSHOT',
            dependencies: ['missing'],
            optionalModules: [],
            conflicts: [],
            backendModules: [],
            frontend: { viewGlobs: [], componentGlobs: [], apiGlobs: [], i18nGlobs: [], i18nPrefixes: [] },
            gatewayRoutes: [],
            composeServices: [],
            database: { changelogs: [], seedSourceIds: [] },
            tenantProvisioning: 'none',
            permissionRoots: [],
            provisioningSeedIds: [],
            nacosConfigs: [],
            ports: [],
            mq: { producers: [], consumers: [] },
            xxl: { handlers: [], appNames: [] },
            docs: [],
            resourceHints: { minimumMemoryMb: 0, recommendedMemoryMb: 0 },
            deprecation: { status: 'active' },
            compatibility: { java: '25', node: '>=22.12.0', notes: [] },
          } satisfies ModuleDefinition,
        ],
      }),
      CliError,
    );
  });

  it('rejects conflicting modules while resolving the closure', () => {
    const catalog = cloneCatalog(loadCatalog(workspaceRoot));
    const gateway = catalog.modules.find((module) => module.id === 'gateway')!;
    gateway.conflicts = [...gateway.conflicts, 'base'];
    assert.throws(() => resolveModuleClosure(catalog, ['base', 'gateway']), /冲突/);
    assert.throws(() => resolveModuleClosure(catalog, ['gateway', 'base']), /冲突/);
    gateway.conflicts = [];
    assert.doesNotThrow(() => resolveModuleClosure(catalog, ['base', 'gateway']));
  });

  it('rejects selections that drop Compose runtime prerequisites', () => {
    const catalog = loadCatalog(workspaceRoot);
    assert.throws(() => validateClosureRuntimeDependencies(workspaceRoot, catalog, ['platform', 'auth']), /mysql|redis/);
    for (const presetId of ['core', 'workflow', 'crm', 'supply-chain', 'full']) {
      const resolved = resolvePreset(workspaceRoot, catalog, presetId);
      assert.doesNotThrow(
        () => validateClosureRuntimeDependencies(workspaceRoot, catalog, resolved.resolvedModules),
        `预设 ${presetId} 不应被运行时依赖检查拒绝`,
      );
    }
  });

  it('reports unmanaged i18n keys, ghost prefixes, unmanaged changelogs and undeclared MQ bindings', () => {
    const catalog = loadCatalog(workspaceRoot);

    const stripped = cloneCatalog(catalog);
    for (const module of stripped.modules) module.frontend.i18nPrefixes = [];
    assert.throws(() => validateCatalogResources(workspaceRoot, stripped), /i18n 顶层键未被 catalog 管理/);

    const ghost = cloneCatalog(catalog);
    ghost.modules.find((module) => module.id === 'platform')!.frontend.i18nPrefixes = ['definitelyNotALocaleKey'];
    assert.throws(() => validateCatalogResources(workspaceRoot, ghost), /i18n 前缀幽灵声明/);

    const orphanChangelog = cloneCatalog(catalog);
    const platform = orphanChangelog.modules.find((module) => module.id === 'platform')!;
    platform.database.changelogs = platform.database.changelogs.filter(
      (path) => path !== 'database/changelog/db.changelog-root.yaml',
    );
    assert.throws(() => validateCatalogResources(workspaceRoot, orphanChangelog), /changelog 未被 catalog 管理/);

    const badMq = cloneCatalog(catalog);
    badMq.modules.find((module) => module.id === 'asset')!.mq.producers = ['not-a-real-destination'];
    assert.throws(() => validateCatalogResources(workspaceRoot, badMq), /MQ producer/);

    const hiddenMq = cloneCatalog(catalog);
    const workflow = hiddenMq.modules.find((module) => module.id === 'workflow')!;
    workflow.mq.producers = workflow.mq.producers.filter((destination) => destination !== 'operlog-topic');
    assert.throws(() => validateCatalogResources(workspaceRoot, hiddenMq), /未声明的 MQ producer/);
  });
});

describe('compose port and project parametrization', () => {
  it('parses literal, ${VAR} and ${VAR:-default} host ports', () => {
    assert.deepEqual(parseHostPortMapping('8080'), {
      raw: '8080',
      hostIp: undefined,
      host: { literal: 8080 },
      container: undefined,
      effectiveHostPort: 8080,
    });
    const templated = parseHostPortMapping('127.0.0.1:${OMNI_MYSQL_HOST_PORT:-3306}:3306');
    assert.equal(templated?.hostIp, '127.0.0.1');
    assert.equal(templated?.host.envVar, 'OMNI_MYSQL_HOST_PORT');
    assert.equal(templated?.host.envDefault, 3306);
    assert.equal(templated?.container?.literal, 3306);
    const bare = parseHostPortMapping('${OMNI_REDIS_HOST_PORT}:6379');
    assert.equal(bare?.host.envVar, 'OMNI_REDIS_HOST_PORT');
    assert.equal(bare?.host.envDefault, undefined);
    assert.equal(parseHostPortMapping('not-a-port'), null);
  });

  it('exposes every parameterized host port and a per-preset project name', () => {
    const model = loadComposeApplication(workspaceRoot);
    const overrides = composePortOverrides(model);
    assert.ok(overrides.length >= 18, `参数化宿主端口数量过少: ${overrides.length}`);
    assert.ok(overrides.every((entry) => /^OMNI_[A-Z0-9_]+_HOST_PORT$/.test(entry.envVar)));
    assert.equal(new Set(overrides.map((entry) => entry.envVar)).size, overrides.length, '同一环境变量被多个服务复用');
    const shifted = shiftedPortEnv(model, 100);
    assert.equal(shifted.OMNI_MYSQL_HOST_PORT, '3406');
    assert.equal(new Set(Object.keys(shifted)).size, overrides.length);
    const presetIds = ['core', 'workflow', 'crm', 'supply-chain', 'full'];
    assert.deepEqual(presetIds.map((presetId) => composeProjectName(presetId)), presetIds.map((presetId) => `omni-${presetId}`));
    assert.equal(new Set(presetIds.map((presetId) => composeProjectName(presetId))).size, presetIds.length, '预设 Compose 项目名不唯一');
    assert.ok(composeProjectName('Supply Chain 预设').startsWith('omni-supply-chain'));
    const scoped = presetIds.map((presetId) => composeProjectName(presetId, workspaceRoot));
    assert.equal(new Set(scoped).size, presetIds.length, '同一工作区下预设项目名仍须互斥');
    assert.ok(scoped.every((name) => /^omni-[a-z0-9._-]+$/.test(name)), JSON.stringify(scoped));
    assert.ok(composeProjectName('core', workspaceRoot).startsWith('omni-core-'));
  });
});
