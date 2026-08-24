import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { loadCatalog, resolveModuleClosure, validateCatalogGraph, validateCatalogResources } from '../src/catalog.js';
import { CliError } from '../src/errors.js';
import type { ModuleDefinition } from '../src/types.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

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
});
