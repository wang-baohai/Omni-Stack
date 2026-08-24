import assert from 'node:assert/strict';
import { resolve } from 'node:path';
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
});
