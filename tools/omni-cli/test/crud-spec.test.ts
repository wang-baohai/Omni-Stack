import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { describe, it } from 'node:test';
import { loadCrudSpec, validateCrudSpec } from '../src/crud-spec.js';
import type { CrudSpec } from '../src/types.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));

describe('CRUD spec', () => {
  it('validates the material-brand golden declaration', () => {
    const spec = goldenSpec();
    assert.equal(spec.moduleId, 'procurement');
    assert.equal(spec.fields.length, 4);
    assert.deepEqual(spec.uniqueConstraints[0]?.fields, ['tenantId', 'brandCode']);
  });

  it('rejects domain capabilities outside the safe generator boundary', () => {
    const spec = cloneGolden();
    spec.forbiddenCapabilities = ['workflow'];
    assert.throws(() => validateCrudSpec(workspaceRoot, spec), /拒绝复杂能力.*workflow/);
  });

  it('rejects unsafe Java TypeScript and database mappings', () => {
    const spec = cloneGolden();
    spec.fields[0]!.typescriptType = 'number';
    assert.throws(() => validateCrudSpec(workspaceRoot, spec), /类型映射不安全/);
  });

  it('requires explicit PII masking instead of inference', () => {
    const spec = cloneGolden();
    spec.fields[1]!.pii = 'name';
    spec.fields[1]!.maskStrategy = 'none';
    assert.throws(() => validateCrudSpec(workspaceRoot, spec), /必须显式声明 maskStrategy/);
  });

  it('requires tenant columns in tenant-scoped unique constraints', () => {
    const spec = cloneGolden();
    spec.uniqueConstraints[0]!.fields = ['brandCode'];
    assert.throws(() => validateCrudSpec(workspaceRoot, spec), /必须包含 tenantId/);
  });
});

function goldenSpec(): CrudSpec {
  return loadCrudSpec(workspaceRoot, 'scaffold/specs/material-brand.yaml');
}

function cloneGolden(): CrudSpec {
  return structuredClone(goldenSpec());
}
