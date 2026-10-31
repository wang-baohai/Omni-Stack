import { listPresetIds, resolvePreset } from './presets.js';
import type { ModuleCatalog } from './types.js';
export const locales = {
  'zh-CN': {
    suffix: '', title: '预设依赖矩阵', generated: '本文件由 `scaffold/catalog/modules.yaml` 自动生成，请勿手工维护事实表。',
    module: '模块', kind: '类型', required: '必选依赖', optional: '可选模块', conflicts: '冲突', backend: '后端模块', compose: 'Compose 服务', permissions: '权限根',
    preset: '预设', explicit: '显式模块', resolved: '依赖闭包', memory: '内存（最低/建议）', none: '无', readmeTitle: '项目裁剪预设', readmeLink: '完整 Wiki：项目介绍、图文使用、启动与开发',
  },
  'en-US': {
    suffix: '.en', title: 'Preset Dependency Matrix', generated: 'Generated from `scaffold/catalog/modules.yaml`; do not maintain the fact tables manually.',
    module: 'Module', kind: 'Kind', required: 'Required dependencies', optional: 'Optional modules', conflicts: 'Conflicts', backend: 'Backend modules', compose: 'Compose services', permissions: 'Permission roots',
    preset: 'Preset', explicit: 'Explicit modules', resolved: 'Dependency closure', memory: 'Memory (min/recommended)', none: 'None', readmeTitle: 'Project Presets', readmeLink: 'Full Wiki: introduction, illustrated usage, startup, and development',
  },
  'ja-JP': {
    suffix: '.jp', title: 'プリセット依存関係マトリクス', generated: '`scaffold/catalog/modules.yaml` から自動生成されます。事実表を手動で編集しないでください。',
    module: 'モジュール', kind: '種別', required: '必須依存', optional: '任意モジュール', conflicts: '競合', backend: 'バックエンド', compose: 'Compose サービス', permissions: '権限ルート',
    preset: 'プリセット', explicit: '明示モジュール', resolved: '依存クロージャ', memory: 'メモリ（最小/推奨）', none: 'なし', readmeTitle: 'プロジェクトプリセット', readmeLink: 'Wiki 全体：紹介、画像付き操作、起動、開発',
  },
  'ko-KR': {
    suffix: '.kr', title: '프리셋 의존성 매트릭스', generated: '`scaffold/catalog/modules.yaml`에서 자동 생성됩니다. 사실 표를 수동으로 관리하지 마세요.',
    module: '모듈', kind: '유형', required: '필수 의존성', optional: '선택 모듈', conflicts: '충돌', backend: '백엔드 모듈', compose: 'Compose 서비스', permissions: '권한 루트',
    preset: '프리셋', explicit: '명시 모듈', resolved: '의존성 클로저', memory: '메모리(최소/권장)', none: '없음', readmeTitle: '프로젝트 프리셋', readmeLink: '전체 Wiki: 소개, 그림 사용법, 기동, 개발',
  },
};

type DocLabels = typeof locales['zh-CN'];

export function renderMatrix(workspaceRoot: string, catalog: ModuleCatalog, text: DocLabels): string {
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

export function renderReadmeSection(workspaceRoot: string, text: DocLabels): string {
  const presets = listPresetIds(workspaceRoot).map((presetId) => `\`${presetId}\``).join(' · ');
  // README 只保留预设名称与完整 Wiki；选择步骤和依赖表仍在各自文档中维护。
  return `<!-- omni:preset-table:start -->\n${presets}\n\n[${text.readmeLink}](docs/wiki/index${text.suffix}.md)\n<!-- omni:preset-table:end -->`;
}

export function updateReadme(content: string, section: string): string {
  const normalized = content.replace(/\r\n?/g, '\n');
  const pattern = /<!-- omni:preset-table:start -->[\s\S]*?<!-- omni:preset-table:end -->/;
  const updated = pattern.test(normalized) ? normalized.replace(pattern, section) : `${normalized.replace(/\s*$/, '')}\n\n${section}`;
  return `${updated.replace(/\s*$/, '')}\n`;
}


function join(values: string[], fallback: string): string { return values.length === 0 ? fallback : values.join(', '); }
