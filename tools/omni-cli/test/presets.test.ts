import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { relative, resolve, sep } from 'node:path';
import { describe, it } from 'node:test';
import { loadCatalog } from '../src/catalog.js';
import { listPresetIds, resolvePreset } from '../src/presets.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

describe('presets', () => {
  it('publishes exactly five formal presets', () => {
    assert.deepEqual(listPresetIds(workspaceRoot), ['core', 'crm', 'full', 'supply-chain', 'workflow']);
  });

  it('full preset contains every business module', () => {
    const resolved = resolvePreset(workspaceRoot, loadCatalog(workspaceRoot), 'full');
    for (const moduleId of ['auth', 'base', 'gateway', 'workflow', 'crm', 'srm', 'procurement', 'asset']) {
      assert.ok(resolved.resolvedModules.includes(moduleId), `missing module ${moduleId}`);
    }
  });

  it('resolves a custom YAML file through the same dependency rules', () => {
    const temporaryRoot = mkdtempSync(resolve(tmpdir(), 'omni-custom-preset-'));
    try {
      const customPreset = resolve(temporaryRoot, 'supplier.yaml');
      writeFileSync(customPreset, [
        'id: supplier-workspace',
        'version: "1.0.0"',
        'displayName: 供应商工作台',
        'description: 自定义供应商与工作流组合。',
        'modules: [srm, gateway, mysql, redis, nacos]',
        '',
      ].join('\n'), 'utf8');

      const resolved = resolvePreset(workspaceRoot, loadCatalog(workspaceRoot), customPreset);

      assert.equal(resolved.preset.id, 'supplier-workspace');
      for (const moduleId of ['platform', 'auth', 'base', 'workflow', 'srm', 'gateway', 'mysql', 'redis', 'nacos']) {
        assert.ok(resolved.resolvedModules.includes(moduleId), `missing custom dependency ${moduleId}`);
      }
      assert.equal(resolved.resolvedModules.includes('procurement'), false);
    } finally {
      const child = relative(resolve(tmpdir()), resolve(temporaryRoot));
      assert.ok(child && child !== '..' && !child.startsWith(`..${sep}`), '拒绝清理系统临时目录之外的路径');
      rmSync(temporaryRoot, { recursive: true, force: true });
    }
  });
});
