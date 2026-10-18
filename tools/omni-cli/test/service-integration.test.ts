import assert from 'node:assert/strict';
import { appendFileSync, copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { applyServiceGeneration, planServiceGeneration } from '../src/service-generator.js';
import { planServiceIntegration } from '../src/service-integration.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const temporaryDirectories: string[] = [];

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    const resolved = resolve(directory);
    assert.ok(resolved.startsWith(resolve(tmpdir())), `拒绝清理临时目录之外的路径：${resolved}`);
    rmSync(resolved, { recursive: true, force: true });
  }
});

describe('service integration planner', () => {
  it('parses every structural target and returns a read-only ready plan', () => {
    const packageRoot = createGeneratedPackage();
    const plan = planServiceIntegration(workspaceRoot, packageRoot, 'inventory-sample', { checkGit: false });

    assert.equal(plan.ready, true, plan.conflicts.join('\n'));
    assert.equal(plan.operations.length, 28);
    assert.ok(plan.operations.some((operation) => operation.target === 'docs/inventory-sample.jp.md'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-yaml' && operation.target === 'docs/docs-manifest.yaml'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-markdown' && operation.target === 'docs/i18n-review-queue.md'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-xml'
      && operation.target === 'omni-backend/pom.xml'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-yaml'
      && operation.target === 'compose.apps.yaml'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-typescript'
      && operation.target === 'omni-frontend/src/router/index.ts'));
    assert.ok(plan.operations.some((operation) => operation.kind === 'modify-java'
      && operation.target.endsWith('/MigrationTargetCatalog.java')));
    assert.ok(plan.operations.some((operation) => operation.kind === 'create-file'
      && operation.target === 'database/changelog/inventory-sample/db.changelog-inventory-sample.yaml'));
    assert.match(plan.warnings.join('\n'), /自然键/);
  });
});

function createGeneratedPackage(): string {
  const parent = mkdtempSync(resolve(tmpdir(), 'omni-cli-integration-test-'));
  temporaryDirectories.push(parent);
  const output = resolve(parent, 'inventory-package');
  const generationPlan = planServiceGeneration(workspaceRoot, 'inventory-sample', {
    output,
    operLog: true,
    mq: true,
    dataScope: true,
    dataScopeTable: ['inventory_sample_item'],
    tablePrefix: 'inventory_sample_',
  });
  applyServiceGeneration(generationPlan);
  return output;
}

describe('service integration locale ownership', () => {
  for (const locale of ['ja-JP', 'ko-KR']) {
    it('rejects uncommitted ' + locale + ' translations before integration', () => {
      const packageRoot = createGeneratedPackage();
      const targetRoot = createTrackedTargetWorkspace();
      const localePath = 'omni-frontend/src/locales/' + locale + '.ts';
      const target = resolve(targetRoot, localePath);
      appendFileSync(target, '\n// 用户尚未提交的翻译调整。\n', 'utf8');
      const before = readFileSync(target, 'utf8');

      const plan = planServiceIntegration(targetRoot, packageRoot, 'inventory-sample');

      assert.equal(readFileSync(target, 'utf8'), before);
      assert.equal(plan.ready, false);
      assert.ok(plan.conflicts.some((conflict) => conflict.includes(localePath)), plan.conflicts.join('\n'));
    });
  }
});

function createTrackedTargetWorkspace(): string {
  const targetRoot = mkdtempSync(resolve(tmpdir(), 'omni-cli-integration-locale-test-'));
  temporaryDirectories.push(targetRoot);
  const targets = [
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
  ];
  for (const path of targets) {
    const target = resolve(targetRoot, path);
    mkdirSync(dirname(target), { recursive: true });
    copyFileSync(resolve(workspaceRoot, path), target);
  }
  execFileSync('git', ['init', '--quiet'], { cwd: targetRoot });
  execFileSync('git', ['add', '--', ...targets], { cwd: targetRoot });
  execFileSync('git', ['-c', 'user.name=Omni CLI test', '-c', 'user.email=omni-cli-test@example.invalid',
    '-c', 'core.hooksPath=/dev/null', 'commit', '--quiet', '-m', 'fixture'], { cwd: targetRoot });
  return targetRoot;
}
