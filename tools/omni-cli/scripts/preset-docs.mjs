import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { parseDocument } from 'yaml';
import { loadCatalog } from '../dist/src/catalog.js';
import { listPresetIds, resolvePreset } from '../dist/src/presets.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const catalog = loadCatalog(workspaceRoot);
const checkOnly = process.argv.slice(2).includes('--check');
assert.ok(process.argv.slice(2).every((argument) => argument === '--check'), '仅支持 --check 参数');

const locales = {
  'zh-CN': {
    suffix: '', title: '预设依赖矩阵', generated: '本文件由 `scaffold/catalog/modules.yaml` 自动生成，请勿手工维护事实表。',
    module: '模块', kind: '类型', required: '必选依赖', optional: '可选模块', conflicts: '冲突', backend: '后端模块', compose: 'Compose 服务', permissions: '权限根',
    preset: '预设', explicit: '显式模块', resolved: '依赖闭包', memory: '内存（最低/建议）', none: '无', readmeTitle: '项目裁剪预设', readmeLink: '选择指南',
  },
  'en-US': {
    suffix: '.en', title: 'Preset Dependency Matrix', generated: 'Generated from `scaffold/catalog/modules.yaml`; do not maintain the fact tables manually.',
    module: 'Module', kind: 'Kind', required: 'Required dependencies', optional: 'Optional modules', conflicts: 'Conflicts', backend: 'Backend modules', compose: 'Compose services', permissions: 'Permission roots',
    preset: 'Preset', explicit: 'Explicit modules', resolved: 'Dependency closure', memory: 'Memory (min/recommended)', none: 'None', readmeTitle: 'Project Presets', readmeLink: 'Selection guide',
  },
  'ja-JP': {
    suffix: '.jp', title: 'プリセット依存関係マトリクス', generated: '`scaffold/catalog/modules.yaml` から自動生成されます。事実表を手動で編集しないでください。',
    module: 'モジュール', kind: '種別', required: '必須依存', optional: '任意モジュール', conflicts: '競合', backend: 'バックエンド', compose: 'Compose サービス', permissions: '権限ルート',
    preset: 'プリセット', explicit: '明示モジュール', resolved: '依存クロージャ', memory: 'メモリ（最小/推奨）', none: 'なし', readmeTitle: 'プロジェクトプリセット', readmeLink: '選択ガイド',
  },
  'ko-KR': {
    suffix: '.kr', title: '프리셋 의존성 매트릭스', generated: '`scaffold/catalog/modules.yaml`에서 자동 생성됩니다. 사실 표를 수동으로 관리하지 마세요.',
    module: '모듈', kind: '유형', required: '필수 의존성', optional: '선택 모듈', conflicts: '충돌', backend: '백엔드 모듈', compose: 'Compose 서비스', permissions: '권한 루트',
    preset: '프리셋', explicit: '명시 모듈', resolved: '의존성 클로저', memory: '메모리(최소/권장)', none: '없음', readmeTitle: '프로젝트 프리셋', readmeLink: '선택 가이드',
  },
};

for (const [locale, text] of Object.entries(locales)) {
  const matrixPath = resolve(workspaceRoot, `docs/preset-dependency-matrix${text.suffix}.md`);
  emit(matrixPath, renderMatrix(text));
  const readmePath = resolve(workspaceRoot, `README${text.suffix}.md`);
  emit(readmePath, updateReadme(readFileSync(readmePath, 'utf8'), renderReadmeSection(text)));
  console.log(`${checkOnly ? 'checked' : 'generated'} preset docs: ${locale}`);
}
validateManifest();

function renderMatrix(text) {
  const moduleRows = catalog.modules.map((module) => `| ${module.id} | ${module.kind} | ${join(module.dependencies, text.none)} | ${join(module.optionalModules, text.none)} | ${join(module.conflicts, text.none)} | ${join(module.backendModules, text.none)} | ${join(module.composeServices, text.none)} | ${join(module.permissionRoots, text.none)} |`);
  const presetRows = listPresetIds(workspaceRoot).map((presetId) => {
    const resolved = resolvePreset(workspaceRoot, catalog, presetId);
    const definitions = catalog.modules.filter((module) => resolved.resolvedModules.includes(module.id));
    const minimum = definitions.reduce((sum, module) => sum + module.resourceHints.minimumMemoryMb, 0);
    const recommended = definitions.reduce((sum, module) => sum + module.resourceHints.recommendedMemoryMb, 0);
    return `| ${presetId} | ${join(resolved.explicitModules, text.none)} | ${join(resolved.resolvedModules, text.none)} | ${minimum} MB / ${recommended} MB |`;
  });
  return `<!-- generated-by: @omni-stack/cli preset-docs; catalog-version: ${catalog.version} -->\n# ${text.title}\n\n> ${text.generated}\n\n## ${text.module}\n\n| ${text.module} | ${text.kind} | ${text.required} | ${text.optional} | ${text.conflicts} | ${text.backend} | ${text.compose} | ${text.permissions} |\n|---|---|---|---|---|---|---|---|\n${moduleRows.join('\n')}\n\n## ${text.preset}\n\n| ${text.preset} | ${text.explicit} | ${text.resolved} | ${text.memory} |\n|---|---|---|---|\n${presetRows.join('\n')}\n`;
}

function renderReadmeSection(text) {
  const rows = listPresetIds(workspaceRoot).map((presetId) => {
    const resolved = resolvePreset(workspaceRoot, catalog, presetId);
    return `| ${presetId} | ${join(resolved.explicitModules, text.none)} | ${join(resolved.resolvedModules, text.none)} |`;
  });
  return `<!-- omni:preset-table:start -->\n## ${text.readmeTitle}\n\n| ${text.preset} | ${text.explicit} | ${text.resolved} |\n|---|---|---|\n${rows.join('\n')}\n\n[${text.readmeLink}](docs/preset-quick-selection${text.suffix}.md) · [${text.title}](docs/preset-dependency-matrix${text.suffix}.md)\n<!-- omni:preset-table:end -->`;
}

function updateReadme(content, section) {
  const normalized = content.replace(/\r\n?/g, '\n');
  const pattern = /<!-- omni:preset-table:start -->[\s\S]*?<!-- omni:preset-table:end -->/;
  const updated = pattern.test(normalized) ? normalized.replace(pattern, section) : `${normalized.replace(/\s*$/, '')}\n\n${section}`;
  return `${updated.replace(/\s*$/, '')}\n`;
}

function emit(path, content) {
  if (checkOnly) {
    assert.equal(existsSync(path), true, `缺少生成文档：${path}`);
    assert.equal(readFileSync(path, 'utf8').replace(/\r\n?/g, '\n'), content, `生成文档漂移：${path}`);
    return;
  }
  writeFileSync(path, content, 'utf8');
}

function join(values, fallback) {
  return values.length === 0 ? fallback : values.join(', ');
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
