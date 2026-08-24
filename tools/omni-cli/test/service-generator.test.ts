import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import ts from 'typescript';
import { CliError } from '../src/errors.js';
import {
  applyServiceGeneration,
  planServiceGeneration,
  resolveServiceSpec,
  validateGeneratedService,
} from '../src/service-generator.js';
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

describe('create-service generator', () => {
  it('plans a safe default service without writing files', () => {
    const output = createTemporaryTarget();
    const plan = planServiceGeneration(workspaceRoot, 'inventory-sample', { output });

    assert.equal(plan.spec.artifactId, 'omni-inventory-sample');
    assert.equal(plan.spec.javaPackage, 'com.omni.inventory.sample');
    assert.equal(plan.spec.enableDataScope, false);
    assert.ok(plan.files.some((file) => file.path.endsWith('/pom.xml')));
    assert.ok(!plan.files.some((file) => file.path.endsWith('DataScopeTablePolicy.java')));
    assert.equal(existsSync(output), false);
  });

  it('writes atomically, validates hashes and is idempotent', () => {
    const output = createTemporaryTarget();
    const options = {
      output,
      job: true,
      mq: true,
      dataScope: true,
      dataScopeTable: ['inventory_sample_item'],
      tablePrefix: 'inventory_sample_',
    };
    const plan = planServiceGeneration(workspaceRoot, 'inventory-sample', options);

    assert.equal(applyServiceGeneration(plan), 'created');
    const lock = validateGeneratedService(output, plan.spec);
    assert.ok(lock.files.length >= 20);
    assert.ok(lock.files.some((file) => file.path.endsWith('DataScopeTablePolicy.java')));
    assert.equal(applyServiceGeneration(plan), 'unchanged');
    assert.match(
      readFileSync(resolve(output, 'omni-frontend/src/views/inventory-sample/overview/index.vue'), 'utf8'),
      /\{\{ moduleInfo\.serviceId \}\}/,
    );
    assert.match(
      readFileSync(resolve(output, 'omni-frontend/src/api/inventory-sample.ts'), 'utf8'),
      /request\.get<ApiResponse<InventorySampleModuleInfo>>\('\/inventory-sample\/status'\)/,
    );
    assertTypeScriptParses(
      `export default {\n${readFileSync(resolve(output, 'integration/frontend/inventory-sample.zh-CN.fragment.ts'), 'utf8')}\n}`,
    );
    assertTypeScriptParses(readFileSync(resolve(output, 'integration/frontend/inventory-sample.menu.fragment.ts'), 'utf8'));
  });

  it('rejects unsafe DataScope and existing workspace conflicts', () => {
    assert.throws(
      () => resolveServiceSpec(workspaceRoot, 'inventory-sample', { dataScope: true }),
      /必须至少提供一个/,
    );
    assert.throws(() => resolveServiceSpec(workspaceRoot, 'asset', {}), /已存在于 catalog/);
    assert.equal(resolveServiceSpec(workspaceRoot, 'message-sample', { mq: true }).enableJob, true);
  });

  it('detects drift and never treats unknown content as idempotent', () => {
    const output = createTemporaryTarget();
    const plan = planServiceGeneration(workspaceRoot, 'inventory-sample', { output });
    applyServiceGeneration(plan);
    const generatedFile = resolve(output, 'docs/inventory-sample.md');
    writeFileSync(generatedFile, `${readFileSync(generatedFile, 'utf8')}人工修改\n`, 'utf8');

    assert.throws(() => validateGeneratedService(output), /已被修改/);
    assert.throws(() => applyServiceGeneration(plan), CliError);
  });

  it('rejects an unknown non-empty target directory', () => {
    const output = createTemporaryTarget();
    const plan = planServiceGeneration(workspaceRoot, 'inventory-sample', { output });
    writeFileSync(resolve(output, '..', 'unknown.txt'), 'unknown', 'utf8');
    const unknownTarget = resolve(output, '..');

    assert.throws(
      () => applyServiceGeneration({ ...plan, targetDirectory: unknownTarget }),
      /缺少 omni-service\.lock\.json/,
    );
    assert.equal(readFileSync(resolve(unknownTarget, 'unknown.txt'), 'utf8'), 'unknown');
  });
});

function createTemporaryTarget(): string {
  const parent = mkdtempSync(resolve(tmpdir(), 'omni-cli-service-test-'));
  temporaryDirectories.push(parent);
  return resolve(parent, 'generated-service');
}

function assertTypeScriptParses(content: string): void {
  const source = ts.createSourceFile('generated.ts', content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const diagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics ?? [];
  assert.deepEqual(diagnostics, []);
}
