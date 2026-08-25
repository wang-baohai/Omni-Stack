import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
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
    assert.equal(plan.operations.length, 21);
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
