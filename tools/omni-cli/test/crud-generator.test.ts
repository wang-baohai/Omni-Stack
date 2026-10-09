import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { afterEach, describe, it } from 'node:test';
import { parseDocument } from 'yaml';
import { renderCrudGeneration, validateExistingCrud } from '../src/crud-generator.js';
import { applyRenderedIntegration } from '../src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const specFile = resolve(workspaceRoot, 'scaffold/specs/material-brand.yaml');
const temporaryDirectories: string[] = [];
const fixtureTargets = [
  'scaffold/catalog/modules.yaml',
  'scaffold/schemas/crud.schema.json',
  'scaffold/schemas/module.schema.json',
  'database/changelog/procurement/db.changelog-procurement.yaml',
  'database/changelog/auth/db.changelog-auth.yaml',
  'database/seed/manifest.yaml',
  'omni-frontend/src/constants/menu.ts',
  'omni-frontend/src/router/index.ts',
  'omni-frontend/src/locales/zh-CN.ts',
  'omni-frontend/src/locales/en-US.ts',
  'omni-frontend/src/locales/ja-JP.ts',
  'omni-frontend/src/locales/ko-KR.ts',
  'omni-backend/omni-procurement/src/main/java/com/omni/procurement/security/ProcDataPermissionHandler.java',
];

afterEach(() => {
  for (const directory of temporaryDirectories.splice(0)) {
    const resolved = resolve(directory);
    assert.ok(resolved.startsWith(resolve(tmpdir())), `拒绝清理临时目录之外的路径：${resolved}`);
    rmSync(resolved, { recursive: true, force: true });
  }
});

describe('CRUD generator', () => {
  it('renders a complete golden vertical slice without writing files', () => {
    const fixture = createWorkspaceFixture();
    const rendered = renderCrudGeneration(fixture, specFile);
    const byTarget = new Map(rendered.changes.map((change) => [change.target, change.after]));

    assert.equal(rendered.plan.ready, true);
    assert.equal(rendered.plan.unchanged, false);
    assert.ok(rendered.changes.length >= 27);
    assert.equal(existsSync(resolve(fixture, 'scaffold/locks/crud/procurement-material-brand.lock.json')), false);
    assert.match(required(byTarget, 'scripts/sql/seed/procurement-material-brand-permissions.sql'), /NOT EXISTS/);
    assert.match(required(byTarget, 'database/changelog/procurement/generated-material-brand-0001.yaml'), /proc_material_brand/);
    assert.match(required(byTarget, 'omni-frontend/src/views/procurement/material-brand/index.vue'), /v-permission/);
    assert.match(required(byTarget, 'omni-backend/omni-procurement/src/main/java/com/omni/procurement/controller/MaterialBrandController.java'), /procurement:material-brand:create/);
    // AC05/AC17：运行时的四语言 i18n 键必须同源登记（旧实现只写 zh-CN/en-US）。
    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /\n  materialBrand: \{/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /削除しました/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ja-JP.ts'), /TODO\(WP-012\)/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ko-KR.ts'), /\n  materialBrand: \{/);
    assert.match(required(byTarget, 'omni-frontend/src/locales/ko-KR.ts'), /삭제했습니다/);
    assert.match(required(byTarget, 'omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/MaterialBrandServiceImpl.java'), /com\.omni\.common\.core\.result\.BusinessException/);
    assert.doesNotMatch(required(byTarget, 'omni-backend/omni-procurement/src/main/java/com/omni/procurement/service/impl/MaterialBrandServiceImpl.java'), /tenantId.*request/i);
    const responseView = required(byTarget, 'omni-backend/omni-procurement/src/main/java/com/omni/procurement/dto/MaterialBrandVO.java');
    assert.match(responseView, /com\.fasterxml\.jackson\.databind\.annotation\.JsonSerialize/);
    assert.match(responseView, /tools\.jackson\.databind\.annotation\.JsonSerialize/);

    for (const target of [
      'database/changelog/procurement/generated-material-brand-0001.yaml',
      'database/changelog/auth/generated-procurement-material-brand-permissions.yaml',
      'database/seed/manifest.yaml',
    ]) {
      assert.deepEqual(parseDocument(required(byTarget, target)).errors, []);
    }
  });

  it('applies atomically and treats exact regeneration as unchanged', () => {
    const fixture = createWorkspaceFixture();
    const rendered = renderCrudGeneration(fixture, specFile);
    const result = applyRenderedIntegration(fixture, rendered);

    assert.equal(result.files, rendered.changes.length);
    const lock = validateExistingCrud(fixture, specFile);
    assert.equal(lock.files.length, 18);
    const second = renderCrudGeneration(fixture, specFile);
    assert.equal(second.plan.unchanged, true);
    assert.deepEqual(second.changes, []);
  });

  it('stops on generated file drift and rolls back injected failures', () => {
    const fixture = createWorkspaceFixture();
    const rendered = renderCrudGeneration(fixture, specFile);

    assert.throws(() => applyRenderedIntegration(fixture, rendered, { failAfter: 7 }), /已完整回滚/);
    assert.equal(existsSync(resolve(fixture, 'scaffold/locks/crud/procurement-material-brand.lock.json')), false);

    applyRenderedIntegration(fixture, renderCrudGeneration(fixture, specFile));
    const generated = resolve(fixture, 'docs/generated/procurement-material-brand.md');
    writeFileSync(generated, `${readFileSync(generated, 'utf8')}人工修改\n`, 'utf8');
    assert.throws(() => renderCrudGeneration(fixture, specFile), /人工漂移/);
  });

  it('registers DataScope only when both owner columns are declared', () => {
    const fixture = createWorkspaceFixture();
    const scopedSpec = resolve(fixture, 'scaffold/specs/scoped-brand.yaml');
    mkdirSync(dirname(scopedSpec), { recursive: true });
    writeFileSync(scopedSpec, readFileSync(specFile, 'utf8')
      .replace('dataScope: false', 'dataScope: true')
      .replace('owner: none', 'owner: user-and-unit'), 'utf8');

    const rendered = renderCrudGeneration(fixture, scopedSpec);
    const handler = rendered.changes.find((change) => change.target.endsWith('ProcDataPermissionHandler.java'));
    assert.match(handler?.after ?? '', /"proc_material_brand", new ScopeColumns\("owner_user_id", "owner_unit_id"\)/);
  });

  it('refuses forbidden saga capability before writing any file', () => {
    const fixture = createWorkspaceFixture();
    const sagaSpec = resolve(fixture, 'scaffold/specs/saga-brand.yaml');
    mkdirSync(dirname(sagaSpec), { recursive: true });
    writeFileSync(sagaSpec, readFileSync(specFile, 'utf8')
      .replace('forbiddenCapabilities: []', 'forbiddenCapabilities: [saga]'), 'utf8');

    assert.throws(() => renderCrudGeneration(fixture, sagaSpec), /拒绝复杂能力.*saga/);
    // 写前拒绝：磁盘上不得留下任何生成物。
    assert.equal(existsSync(resolve(fixture, 'scaffold/locks/crud/procurement-material-brand.lock.json')), false);
    assert.equal(existsSync(resolve(fixture, 'omni-backend/omni-procurement/src/main/java/com/omni/procurement/entity/ProcMaterialBrand.java')), false);
    assert.equal(existsSync(resolve(fixture, 'database/changelog/procurement/generated-material-brand-0001.yaml')), false);
  });

  it('rejects regeneration when the lock file is deleted but registrations remain', () => {
    const fixture = createWorkspaceFixture();
    applyRenderedIntegration(fixture, renderCrudGeneration(fixture, specFile));
    const lockFile = resolve(fixture, 'scaffold/locks/crud/procurement-material-brand.lock.json');
    rmSync(lockFile);

    // 缺少锁文件时目标准入必须被拒绝，不得静默覆盖已登记内容。
    assert.throws(() => renderCrudGeneration(fixture, specFile), /生成目标已存在且不归当前锁文件所有|登记项已存在但缺少 CRUD 锁文件/);
    assert.equal(existsSync(lockFile), false);
  });

  it('rejects silently overwriting when the declaration changed after apply', () => {
    const fixture = createWorkspaceFixture();
    applyRenderedIntegration(fixture, renderCrudGeneration(fixture, specFile));
    const changedSpec = resolve(fixture, 'scaffold/specs/material-brand-changed.yaml');
    mkdirSync(dirname(changedSpec), { recursive: true });
    writeFileSync(changedSpec, readFileSync(specFile, 'utf8')
      .replace('displayName: 物料品牌', 'displayName: 物料品牌（改）'), 'utf8');

    assert.throws(() => renderCrudGeneration(fixture, changedSpec), /声明已变化/);
  });

  it('detects handwritten removal of a registered section entry', () => {
    const fixture = createWorkspaceFixture();
    applyRenderedIntegration(fixture, renderCrudGeneration(fixture, specFile));
    const menuFile = resolve(fixture, 'omni-frontend/src/constants/menu.ts');
    const menu = readFileSync(menuFile, 'utf8');
    writeFileSync(menuFile, menu.replace(`  'procurement:material-brand': 'materialBrand.title',\n`, ''), 'utf8');

    // generated-section 级所有权：登记区块被手删后拒绝继续当作已接入。
    assert.throws(() => renderCrudGeneration(fixture, specFile), /登记项缺失/);
  });
});

function createWorkspaceFixture(): string {
  const root = mkdtempSync(resolve(tmpdir(), 'omni-cli-crud-workspace-'));
  temporaryDirectories.push(root);
  for (const target of fixtureTargets) {
    const destination = resolve(root, ...target.split('/'));
    mkdirSync(dirname(destination), { recursive: true });
    cpSync(resolve(workspaceRoot, ...target.split('/')), destination);
  }
  mkdirSync(resolve(root, 'omni-backend/omni-procurement'), { recursive: true });
  return root;
}

function required(values: Map<string, string>, target: string): string {
  const value = values.get(target);
  assert.ok(value, `缺少变更：${target}`);
  return value;
}
