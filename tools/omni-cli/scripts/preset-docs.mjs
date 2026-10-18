import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parseDocument } from 'yaml';
import { loadCatalog } from '../dist/src/catalog.js';
import { locales, renderMatrix, renderReadmeSection, updateReadme } from '../dist/src/preset-doc-format.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const catalog = loadCatalog(workspaceRoot);
const checkOnly = process.argv.slice(2).includes('--check');
assert.ok(process.argv.slice(2).every((argument) => argument === '--check'), '仅支持 --check 参数');


for (const [locale, text] of Object.entries(locales)) {
  const matrixPath = resolve(workspaceRoot, `docs/preset-dependency-matrix${text.suffix}.md`);
  emit(matrixPath, renderMatrix(workspaceRoot, catalog, text));
  const readmePath = resolve(workspaceRoot, `README${text.suffix}.md`);
  emit(readmePath, updateReadme(readFileSync(readmePath, 'utf8'), renderReadmeSection(workspaceRoot, text)));
  console.log(`${checkOnly ? 'checked' : 'generated'} preset docs: ${locale}`);
}
validateManifest();

function emit(path, content) {
  if (checkOnly) {
    assert.equal(existsSync(path), true, `缺少生成文档：${path}`);
    assert.equal(readFileSync(path, 'utf8').replace(/\r\n?/g, '\n'), content, `生成文档漂移：${path}`);
    return;
  }
  writeFileSync(path, content, 'utf8');
}

function validateManifest() {
  const manifest = parseDocument(readFileSync(resolve(workspaceRoot, 'docs/docs-manifest.yaml'), 'utf8')).toJS();
  const requiredIds = [
    'preset-quick-selection',
    'preset-maintenance',
    'preset-dependency-matrix',
    'custom-preset-tutorial',
    'preset-upgrade-guide',
  ];
  for (const id of requiredIds) {
    const document = manifest.documents?.find((entry) => entry.id === id);
    assert.ok(document, `docs-manifest 缺少 ${id}`);
    const sourcePath = resolve(workspaceRoot, document.source);
    assert.equal(existsSync(sourcePath), true, `${id} 中文事实源不存在`);
    const digest = createHash('sha256').update(readFileSync(sourcePath)).digest('hex');
    assert.equal(document.source_sha256, digest, `${id} 中文摘要漂移`);
    for (const locale of ['en-US', 'ja-JP', 'ko-KR']) {
      const translation = document.translations?.[locale];
      assert.ok(translation && translation.status !== 'missing', `${id} 缺少 ${locale} 翻译登记`);
      assert.equal(existsSync(resolve(workspaceRoot, translation.path)), true, `${id} 缺少 ${locale} 文件`);
    }
  }
}
