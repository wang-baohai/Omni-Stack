import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { loadCatalog, resolveModuleClosure, validateCatalogGraph } from '../src/catalog.js';
import { CliError } from '../src/errors.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

describe('module catalog', () => {
  it('validates the repository catalog', () => {
    const catalog = loadCatalog(workspaceRoot);
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
            kind: 'business',
            dependencies: ['missing'],
            tenantProvisioning: 'none',
            provisioningSeedIds: [],
          },
        ],
      }),
      CliError,
    );
  });
});
