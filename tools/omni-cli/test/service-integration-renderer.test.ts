import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { parseDocument } from 'yaml';
import { applyServiceGeneration, planServiceGeneration } from '../src/service-generator.js';
import { renderServiceIntegration } from '../src/service-integration-renderer.js';
import { applyRenderedIntegration } from '../src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const temporaryDirectories: string[] = [];
const fixtureTargets = [
  'omni-backend/pom.xml',
  'omni-backend/omni-gateway/src/main/resources/application.yml',
  'compose.apps.yaml',
  'scaffold/catalog/modules.yaml',
  'scaffold/schemas/module.schema.json',
  'docker/backend/Dockerfile',
  'omni-frontend/src/constants/menu.ts',
  'omni-frontend/src/router/index.ts',
  'omni-frontend/src/locales/zh-CN.ts',
  'omni-frontend/src/locales/en-US.ts',
  'omni-frontend/src/locales/ja-JP.ts',
  'omni-frontend/src/locales/ko-KR.ts',
  'database/changelog/auth/db.changelog-auth.yaml',
  'database/seed/manifest.yaml',
  'database/changelog/platform/db.changelog-platform.yaml',
  'omni-backend/omni-db-migrator/src/main/java/com/omni/dbmigrator/migration/MigrationTargetCatalog.java',
  'docs/docs-manifest.yaml',
  'docs/i18n-review-queue.md',
  'tools/omni-cli/scripts/translation-queue.mjs',
  'tools/omni-cli/scripts/translation-review.mjs',
];

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    const resolved = resolve(directory);
    assert.ok(resolved.startsWith(resolve(tmpdir())), `拒绝清理临时目录之外的路径：${resolved}`);
    rmSync(resolved, { recursive: true, force: true });
  }
});

describe('service integration renderer', () => {
  it('renders and validates every target entirely in memory', () => {
    const fixtureRoot = createWorkspaceFixture();
    const packageRoot = createGeneratedPackage();
    const originalPom = readFileSync(resolve(fixtureRoot, 'omni-backend/pom.xml'), 'utf8');
    const originalPlatform = parseDocument(readFileSync(
      resolve(fixtureRoot, 'database/changelog/platform/db.changelog-platform.yaml'),
      'utf8',
    )).toJS() as { databaseChangeLog: unknown[] };
    const rendered = renderServiceIntegration(
      fixtureRoot,
      packageRoot,
      'inventory-sample',
      { checkGit: false },
    );

    assert.equal(rendered.changes.length, 39);
    assert.equal(readFileSync(resolve(fixtureRoot, 'omni-backend/pom.xml'), 'utf8'), originalPom);
    assert.equal(existsSync(resolve(fixtureRoot, 'omni-backend/omni-inventory-sample')), false);

    const byTarget = new Map(rendered.changes.map((change) => [change.target, change.after]));
    const documentation = parseDocument(required(byTarget, 'docs/docs-manifest.yaml')).toJS() as {
      documents: Array<{ id: string; source_sha256: string; translations: Record<string, { path: string; status: string; reviewed_at: unknown; reviewer?: string }> }>;
    };
    const registered = documentation.documents.find((entry) => entry.id === 'service-inventory-sample');
    assert.ok(registered);
    assert.equal(registered.source_sha256, createHash('sha256').update(required(byTarget, 'docs/inventory-sample.md')).digest('hex'));
    for (const locale of ['en-US', 'ja-JP', 'ko-KR']) {
      const translation: { path: string; status: string; reviewed_at: unknown; reviewer?: string } = registered.translations[locale]!;
      assert.ok(required(byTarget, translation.path).startsWith('# Inventory Sample'));
      assert.equal(translation.status, 'present-unverified');
      assert.equal(translation.reviewed_at, null);
      assert.equal(translation.reviewer, undefined);
    }
    assert.match(required(byTarget, 'docs/i18n-review-queue.md'), /\| service-inventory-sample \| docs\/inventory-sample.md \| [a-f0-9]{12} \| present-unverified \| present-unverified \| present-unverified \|/);
    assert.match(required(byTarget, 'omni-backend/pom.xml'), /<module>omni-inventory-sample<\/module>/);
    assert.match(required(byTarget, 'scripts/sql/seed/inventory-sample-permissions.sql'), /NOT EXISTS/);
    assert.doesNotMatch(
      required(byTarget, 'scripts/sql/seed/inventory-sample-permissions.sql'),
      /VALUES \(\d+, 1, \d+, 'inventory-sample'/,
    );
    assert.match(
      required(byTarget, 'database/changelog/auth/db.changelog-auth.yaml'),
      /database\/changelog\/auth\/inventory-sample-permissions\.yaml/,
    );
    assert.match(
      required(byTarget, 'database/changelog/auth/inventory-sample-permissions.yaml'),
      /labels: adoption-upgrade/,
    );
    assert.match(required(byTarget, 'omni-frontend/src/constants/menu.ts'), /inventory-sample:overview/);
    assert.match(
      required(byTarget, 'database/changelog/platform/db.changelog-platform.yaml'),
      /CREATE DATABASE IF NOT EXISTS omni_inventory_sample /,
    );
    const renderedPlatform = parseDocument(required(
      byTarget,
      'database/changelog/platform/db.changelog-platform.yaml',
    )).toJS() as { databaseChangeLog: unknown[] };
    assert.deepEqual(renderedPlatform.databaseChangeLog[0], originalPlatform.databaseChangeLog[0]);
    assert.match(
      required(byTarget, 'database/changelog/platform/db.changelog-platform.yaml'),
      /id: platform-generated-inventory-sample-create-database/,
    );
    assert.match(
      required(byTarget, 'database/changelog/platform/db.changelog-platform.yaml'),
      /labels: adoption-upgrade/,
    );
    assert.match(
      required(byTarget, 'omni-backend/omni-db-migrator/src/main/java/com/omni/dbmigrator/migration/MigrationTargetCatalog.java'),
      /target\("inventory-sample", "omni_inventory_sample", false\)/,
    );
    assert.match(
      required(byTarget, 'database/changelog/inventory-sample/db.changelog-inventory-sample.yaml'),
      /db\.changelog-common-mq\.yaml/,
    );

    const gateway = parseDocument(required(
      byTarget,
      'omni-backend/omni-gateway/src/main/resources/application.yml',
    )).toJS() as { spring: { cloud: { gateway: { server: { webflux: { routes: Array<{ id: string }> } } } } } };
    assert.ok(gateway.spring.cloud.gateway.server.webflux.routes.some((route) => route.id === 'omni-inventory-sample'));
    assert.ok(gateway.spring.cloud.gateway.server.webflux.routes.some((route) => route.id === 'block-internal-inventory-sample'));

    const compose = parseDocument(required(byTarget, 'compose.apps.yaml')).toJS() as {
      services: Record<string, { ports: string[]; environment: Record<string, string> }>;
    };
    assert.deepEqual(compose.services['omni-inventory-sample']?.ports, [
      '127.0.0.1:${OMNI_INVENTORY_SAMPLE_HOST_PORT:-8110}:8080',
    ]);
    assert.equal(compose.services['omni-inventory-sample']?.environment.XXL_JOB_EXECUTOR_PORT, '9910');
    // HMAC 继承：生成服务的 compose 段必须强制注入 GATEWAY_ASSERTION_SECRET（缺失即 compose 报错，不容忍 fail-open）。
    assert.match(
      compose.services['omni-inventory-sample']?.environment.GATEWAY_ASSERTION_SECRET ?? '',
      /\$\{GATEWAY_ASSERTION_SECRET:\?/,
    );

    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /inventorySampleManagement:/);
    // WP-011 S14 合同：顶层 i18n 命名空间为合法 JS 标识符（camelCase），不再用带引号的 kebab serviceId
    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /\n  inventorySample: \{/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /連携ステータス/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ko-KR.ts'), /inventorySampleManagement:/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ko-KR.ts'), /연동 상태/);

    const catalog = parseDocument(required(byTarget, 'scaffold/catalog/modules.yaml')).toJS() as {
      modules: Array<{
        id: string;
        provisioningSeedIds: string[];
        gatewayRoutes?: string[];
        frontend?: { i18nPrefixes?: string[] };
        mq?: { producers?: string[] };
      }>;
    };
    // WP-011 S14 合同：新服务的内部拦截路由 / i18n 命名空间 / operlog producer 必须同步进入 catalog 归属
    const generated = catalog.modules.find((module) => module.id === 'inventory-sample');
    assert.deepEqual(generated?.gatewayRoutes, ['omni-inventory-sample', 'block-internal-inventory-sample']);
    assert.deepEqual(generated?.frontend?.i18nPrefixes, ['inventorySample']);
    assert.deepEqual(generated?.mq?.producers, ['operlog-topic', 'inventory-sample-domain-event']);
    assert.ok(catalog.modules.find((module) => module.id === 'auth')?.provisioningSeedIds
      .includes('auth-inventory-sample-permission-catalog'));

    const manifest = parseDocument(required(byTarget, 'database/seed/manifest.yaml')).toJS() as {
      sources: Array<{ id: string; resource: string }>;
      assertions: Array<{ id: string; module: string; expectedRows: number; expectedSha256: string }>;
    };
    assert.equal(
      manifest.sources.find((item) => item.id === 'auth-inventory-sample-permissions')?.resource,
      'scripts/sql/seed/inventory-sample-permissions.sql',
    );
    const assertion = manifest.assertions.find((item) => item.id === 'auth-inventory-sample-permission-catalog');
    assert.ok(assertion);
    assert.equal(assertion.module, 'auth');
    assert.equal(assertion.expectedRows, 3);
    assert.equal(
      assertion.expectedSha256,
      'dee57b7e3bbb35f6e86c8bb2ed128457aaeae735e8e40d0b8913a95383d8634b',
    );
  });

  it('applies all rendered files and removes transaction backups', () => {
    const fixtureRoot = createWorkspaceFixture();
    const rendered = renderServiceIntegration(
      fixtureRoot,
      createGeneratedPackage(),
      'inventory-sample',
      { checkGit: false },
    );

    const result = applyRenderedIntegration(fixtureRoot, rendered);

    assert.deepEqual(result, { files: 39, cleanupWarnings: [] });
    for (const change of rendered.changes) {
      assert.equal(readFileSync(resolve(fixtureRoot, ...change.target.split('/')), 'utf8'), change.after);
    }
    assert.deepEqual(listTransactionArtifacts(fixtureRoot), []);
  });

  it('rolls back every file when a mid-transaction failure is injected', () => {
    const fixtureRoot = createWorkspaceFixture();
    const rendered = renderServiceIntegration(
      fixtureRoot,
      createGeneratedPackage(),
      'inventory-sample',
      { checkGit: false },
    );
    const originals = new Map(rendered.changes
      .filter((change) => change.mode === 'modify')
      .map((change) => [change.target, readFileSync(resolve(fixtureRoot, ...change.target.split('/')), 'utf8')]));

    assert.throws(
      () => applyRenderedIntegration(fixtureRoot, rendered, { failAfter: 38 }),
      /已完整回滚/,
    );
    for (const change of rendered.changes) {
      const target = resolve(fixtureRoot, ...change.target.split('/'));
      if (change.mode === 'create') assert.equal(existsSync(target), false, change.target);
      else assert.equal(readFileSync(target, 'utf8'), originals.get(change.target), change.target);
    }
    assert.deepEqual(listTransactionArtifacts(fixtureRoot), []);
  });
});

function createWorkspaceFixture(): string {
  const root = mkdtempSync(resolve(tmpdir(), 'omni-cli-render-workspace-'));
  temporaryDirectories.push(root);
  for (const target of fixtureTargets) {
    const destination = resolve(root, ...target.split('/'));
    mkdirSync(dirname(destination), { recursive: true });
    cpSync(resolve(workspaceRoot, ...target.split('/')), destination);
  }
  return root;
}

function createGeneratedPackage(): string {
  const parent = mkdtempSync(resolve(tmpdir(), 'omni-cli-render-package-'));
  temporaryDirectories.push(parent);
  const output = resolve(parent, 'inventory-package');
  applyServiceGeneration(planServiceGeneration(workspaceRoot, 'inventory-sample', {
    output,
    operLog: true,
    mq: true,
    dataScope: true,
    dataScopeTable: ['inventory_sample_item'],
    tablePrefix: 'inventory_sample_',
  }));
  return output;
}

function required(values: Map<string, string>, target: string): string {
  const value = values.get(target);
  assert.ok(value, `缺少变更：${target}`);
  return value;
}

function listTransactionArtifacts(root: string, current = root): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    const path = resolve(current, entry.name);
    if (entry.isDirectory()) result.push(...listTransactionArtifacts(root, path));
    else if (entry.name.includes('.omni-') && (entry.name.endsWith('.tmp') || entry.name.endsWith('.bak'))) {
      result.push(path);
    }
  }
  return result;
}
