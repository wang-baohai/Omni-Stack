import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { existsSync, globSync, lstatSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { parse, stringify } from 'yaml';
import { CliError } from './errors.js';
import { loadCatalog } from './catalog.js';
import { locales, renderMatrix, renderReadmeSection, updateReadme } from './preset-doc-format.js';

interface Translation { path: string; status: string; reviewed_at?: string | null; [key: string]: unknown }
interface DocumentEntry {
  id: string; module: string; source: string; source_sha256: string;
  translations: Record<string, Translation>; [key: string]: unknown;
}
const digest = (content: string | Buffer): string => createHash('sha256').update(content).digest('hex');
const domainIds = ['workflow', 'crm', 'srm', 'procurement', 'asset'];
const moduleWords: Record<string, RegExp> = {
  workflow: /workflow|工作流|流程模型|会签|ワークフロー|워크플로/i,
  crm: /\bCRM\b|线索转换|商机|リード変換|영업 기회/i,
  srm: /\bSRM\b|supplier-portal|供应商门户|供应商管理|取引先ポータル|공급업체 포털/i,
  procurement: /procurement|采购|調達|조달/i,
  asset: /\basset\b|资产|資産|자산/i,
};
const localized = {
  '': { scope: '生成预设', excluded: '当前预设未包含', reviewed: '裁剪后的内容需要按当前摘要复核' },
  '.en': { scope: 'Generated preset', excluded: 'Excluded from this preset', reviewed: 'Pruned content requires review against current digests' },
  '.jp': { scope: '生成プリセット', excluded: 'このプリセットには含まれません', reviewed: '切り出した内容は現在のハッシュに基づくレビューが必要です' },
  '.kr': { scope: '생성 프리셋', excluded: '이 프리셋에 포함되지 않음', reviewed: '잘라낸 내용은 현재 해시를 기준으로 검토해야 합니다' },
};

/** 按显式模块块裁剪；组合块只有全部依赖存在才交付。 */
export function filterModuleBlocks(content: string, selected: Set<string>): string {
  return content.replace(/<!-- omni:modules:([^>]+) -->\s*([\s\S]*?)<!-- omni:modules:end -->\s*/g,
    (block, owners: string) => owners.split(',').every((owner) => selected.has(owner.trim())) ? block : '');
}

/** 生成工程使用自己的清单与源码，不向已移除模块保留可点击的入口。 */
export function pruneDocumentation(root: string, selected: Set<string>, presetId: string, sourceRoot: string): void {
  const manifestPath = resolve(root, 'docs/docs-manifest.yaml');
  if (!existsSync(manifestPath)) throw new CliError('预设交付缺少文档清单');
  const manifest = parse(readFileSync(manifestPath, 'utf8')) as { documents: DocumentEntry[]; release_policy?: { ai_review_authorization?: { reference?: string } } };
  const aliases: Record<string, string> = { authentication: 'auth', security: 'auth' };
  const retained = manifest.documents.filter((document) => {
    const module = aliases[document.module] ?? document.module;
    return !(domainIds.includes(module) || module === 'auth') || selected.has(module);
  }).filter((document) => document.id !== 'guide-scheduling' || selected.has('xxl-job'));
  for (const document of manifest.documents) {
    if (retained.includes(document)) continue;
    for (const path of [document.source, ...Object.values(document.translations ?? {}).map((translation) => translation.path)]) {
      removeFile(root, path);
    }
  }
  manifest.documents = retained;
  const files = globSync('**/*.md', { cwd: root }).filter((path) => !path.split(/[\\/]/).some((segment) => ['node_modules', 'target', 'dist', '.git'].includes(segment)));
  const authorizationReference = manifest.release_policy?.ai_review_authorization?.reference;
  for (const file of files) {
    // 授权决策按原文交付，不能追加预设提示后继续使用旧摘要。
    if (file.replaceAll('\\', '/') === authorizationReference) continue;
    const absolute = resolve(root, file);
    let content = filterModuleBlocks(readFileSync(absolute, 'utf8'), selected);
    // 老文档逐节映射业务域。没有显式标记的共享标题保留，行级源码表按实际路径筛选。
    content = pruneDomainSections(content, selected);
    const suffix = /\.(en|jp|kr)\.md$/.exec(file)?.[0].replace('.md', '') ?? '';
    const text = localized[suffix as keyof typeof localized];
    content = content.split('\n').filter((line) => {
      if (!line.startsWith('|')) return true;
      return ![...line.matchAll(/omni-(workflow|crm|srm|procurement|asset)\b/g)].some((match) => !selected.has(match[1]!));
    }).join('\n');
    let fence: string | undefined;
    content = content.split('\n').map((line) => {
      const marker = /^\s*(`{3,}|~{3,})/.exec(line);
      if (marker) { fence = fence ? undefined : marker[1]![0]; return line; }
      if (fence) return line;
      return line.replace(/(!?)\[([^\]]*)\]\(([^)]+)\)/g, (link, image: string, label: string, target: string) => {
        if (/^(https?:|mailto:|data:)/i.test(target)) return link;
        const anchor = target.split('#')[1];
        if (target.startsWith('#')) {
          return anchor && !markdownAnchors(content).has(decodeURIComponent(anchor)) ? `${label} (${text.excluded})` : link;
        }
        const path = target.split('#')[0]!.split(/\s+["']/)[0]!;
        const destination = resolve(dirname(absolute), decodeURIComponent(path));
        const child = relative(root, destination);
        if (child === '..' || child.startsWith(`..${sep}`)) throw new CliError(`文档链接越界: ${file}: ${target}`);
        if (existsSync(destination)) {
          if (anchor && destination.endsWith('.md') && !markdownAnchors(pruneDomainSections(filterModuleBlocks(readFileSync(destination, 'utf8'), selected), selected)).has(decodeURIComponent(anchor))) {
            return `${label} (${text.excluded})`;
          }
          return link;
        }
        // 图片缺失是源缺陷，不能以文字隐藏；业务文件缺失仅允许来自已移除模块。
        if (image) throw new CliError(`生成文档缺少图片: ${file}: ${target}`);
        const omitted = domainIds.some((id) => !selected.has(id) && (moduleWords[id]!.test(label + ' ' + path)));
        if (!omitted && !/scheduling|调度|スケジュール|스케줄/.test(label + path)) throw new CliError(`生成文档缺少非裁剪资源: ${file}: ${target}`);
        return `${label} (${text.excluded})`;
      });
    }).join('\n');
    if (file.replaceAll('\\', '/') === `README${suffix}.md`) {
      content = content.replace(/--profile full/g, `--profile ${presetId}`);
      content = content.replace(/^- .*\bCRM\b.*\bSRM\b.*$/gm, (line) => domainIds.every((id) => selected.has(id)) ? line : '');
    }
    if (/^(?:docs[\\/]|README)/.test(file)) {
      content = content.replace(/^> (?:生成预设|Generated preset|生成プリセット|생성 프리셋):.*\n\n/m, '');
      content = content.replace(/^(# .+\n)/, `$1\n> ${text.scope}: \`${presetId}\`; ${[...selected].join(', ')}. ${text.reviewed}.\n`);
    }
    writeFileSync(absolute, content, 'utf8');
  }
  const catalog = loadCatalog(root);
  for (const text of Object.values(locales)) {
    writeFileSync(resolve(root, `docs/preset-dependency-matrix${text.suffix}.md`), renderMatrix(root, catalog, text), 'utf8');
    const path = resolve(root, `README${text.suffix}.md`);
    writeFileSync(path, updateReadme(readFileSync(path, 'utf8'), renderReadmeSection(root, text)), 'utf8');
  }
  for (const document of retained) {
    const source = readFileSync(resolve(root, document.source), 'utf8');
    const translationChanged = Object.values(document.translations ?? {}).some((translation) =>
      typeof translation.translation_sha256 === 'string' && translation.translation_sha256 !== digest(readFileSync(resolve(root, translation.path), 'utf8')));
    if (document.source_sha256 !== digest(source) || translationChanged) {
      document.source_sha256 = digest(source);
      for (const translation of Object.values(document.translations ?? {})) {
        if (translation.status === 'synchronized') {
          translation.previous_review = { ...translation, reason: 'Preset pruning changed the reviewed source or translation' };
        }
        translation.status = 'present-unverified'; translation.reviewed_at = null;
        for (const key of ['reviewer', 'review_type', 'reviewed_source_sha256', 'translation_sha256', 'review_evidence', 'review_evidence_sha256']) delete translation[key];
      }
    }
  }
  // 证据只随仍有效且保留的文档交付，裁剪变化不得继承旧审校结论。
  const retainedReviewFiles = new Set(retained.flatMap((document) => Object.values(document.translations ?? {})
    .flatMap((translation) => typeof translation.review_evidence === 'string' ? [translation.review_evidence] : [])));
  for (const path of globSync('docs/reference/reviews/*.json', { cwd: root })) {
    if (!retainedReviewFiles.has(path.replaceAll('\\', '/'))) removeFile(root, path);
  }
  writeFileSync(manifestPath, stringify(manifest, { lineWidth: 0 }), 'utf8');
  execFileSync(process.execPath, [resolve(sourceRoot, 'tools/omni-cli/scripts/docs-review-queue.mjs'), '--generate'], {
    env: { ...process.env, WP002_TEST_ROOT: root }, stdio: 'pipe',
  });
  // 仅生成项目裁剪图片，源仓库图片不受影响；正文与保留的取证清单共同决定引用。
  pruneScreenshotMetadata(root, selected);
  pruneGeneratedImages(root);
  const aiPath = resolve(root, 'ai/manifest.yaml');
  if (!existsSync(aiPath)) throw new CliError('预设交付缺少 AI 清单');
  const ai = parse(readFileSync(aiPath, 'utf8')) as { assets: Array<{ module?: string; path: string }> };
  ai.assets = ai.assets.filter((asset) => {
    const keep = !asset.module || !domainIds.includes(asset.module) || selected.has(asset.module);
    if (!keep) removeFile(root, asset.path);
    return keep;
  });
  writeFileSync(aiPath, stringify(ai, { lineWidth: 0 }), 'utf8');
}

function markdownAnchors(content: string): Set<string> {
  const result = new Set<string>();
  const counts = new Map<string, number>();
  for (const line of content.split(/\r?\n/)) {
    const match = /^#{1,6}\s+(.+?)\s*$/.exec(line);
    if (!match) continue;
    const slug = match[1]!.trim().toLowerCase().replace(/[`*_~]/g, '').replace(/[^\p{L}\p{N}\s-]/gu, '').replace(/\s/g, '-');
    const count = counts.get(slug) ?? 0;
    counts.set(slug, count + 1);
    result.add(count ? `${slug}-${count}` : slug);
  }
  return result;
}

function pruneDomainSections(content: string, selected: Set<string>): string {
  const lines = content.split('\n');
  const output: string[] = [];
  let skippedLevel = 0;
  let fence = false;
  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) { if (!skippedLevel) output.push(line); fence = !fence; continue; }
    const heading = fence ? null : /^(#{2,6})\s+(.+)$/.exec(line);
    if (heading) {
      const level = heading[1]!.length;
      if (skippedLevel && level <= skippedLevel) skippedLevel = 0;
      if (!skippedLevel && domainIds.some((id) => !selected.has(id) && moduleWords[id]!.test(heading[2]!))) skippedLevel = level;
    }
    if (!skippedLevel) output.push(line);
  }
  return output.join('\n');
}

function pruneScreenshotMetadata(root: string, selected: Set<string>): void {
  const directory = resolve(root, 'omni-frontend/e2e-docs');
  for (const name of ['screenshot-manifest.yaml', 'screenshot-coverage.yaml']) {
    const path = resolve(directory, name);
    if (!existsSync(path)) continue;
    const value = parse(readFileSync(path, 'utf8')) as {
      screenshots?: Array<{ module: string; id: string; route: string }>;
      terminal_evidence?: Array<{ module: string }>;
      modules?: Array<{ id: string; existing_assets?: string[]; required_flows?: string[]; flow_modules?: Record<string, string[]>; flow_evidence?: Record<string, string>; gaps?: string[] }>;
    };
    const keep = (module: string): boolean => module === 'scheduling-workspace'
      ? selected.has('xxl-job')
      : !domainIds.includes(module) || selected.has(module);
    const portal = (text: string): boolean => /supplier-portal|\/portal-(?:login|register)/.test(text);
    // 共享权限模块中的业务路由也有明确归属，不能把裁掉领域的取证入口交给生成物。
    const routeIncluded = (route: string): boolean => !domainIds.some((module) =>
      !selected.has(module) && route.startsWith(`/admin/${module}/`));
    if (value.screenshots) value.screenshots = value.screenshots.filter((entry) =>
      keep(entry.module) && routeIncluded(entry.route) && (selected.has('srm') || !portal(entry.id + ' ' + entry.route)));
    if (value.terminal_evidence) value.terminal_evidence = value.terminal_evidence.filter((entry) => keep(entry.module));
    if (value.modules) value.modules = value.modules.filter((entry) => keep(entry.id)).map((entry) => {
      const removed = new Set((entry.required_flows ?? []).filter((flow) =>
        (entry.flow_modules?.[flow] ?? []).some((module) => !selected.has(module))));
      return {
        ...entry,
        ...(entry.required_flows ? { required_flows: entry.required_flows.filter((flow) => !removed.has(flow)) } : {}),
        ...(entry.flow_modules ? { flow_modules: Object.fromEntries(Object.entries(entry.flow_modules).filter(([flow]) => !removed.has(flow))) } : {}),
        ...(entry.flow_evidence ? { flow_evidence: Object.fromEntries(Object.entries(entry.flow_evidence).filter(([flow]) => !removed.has(flow))) } : {}),
        ...(entry.gaps ? { gaps: entry.gaps.filter((gap) => !removed.has(gap)) } : {}),
        ...(entry.existing_assets ? { existing_assets: entry.existing_assets.filter((asset) =>
          (selected.has('srm') || !portal(asset)) && !domainIds.some((module) => !selected.has(module) && asset.includes(`/${module}-`))) } : {}),
      };
    });
    writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
  }
}

function pruneGeneratedImages(root: string): void {
  const referenced = new Set<string>();
  for (const path of globSync('**/*.md', { cwd: root })) {
    if (path.split(/[\\/]/).some((part) => ['node_modules', 'dist', 'target'].includes(part))) continue;
    const file = resolve(root, path);
    for (const match of readFileSync(file, 'utf8').matchAll(/!\[[^\]]*\]\(([^\s)]+)(?:\s+[^)]*)?\)/g)) {
      if (/^(?:https?:|data:)/.test(match[1]!)) continue;
      referenced.add(relative(root, resolve(dirname(file), decodeURIComponent(match[1]!.split('#')[0]!))).replaceAll('\\', '/'));
    }
  }
  for (const name of ['screenshot-manifest.yaml', 'screenshot-coverage.yaml']) {
    const path = resolve(root, 'omni-frontend/e2e-docs', name);
    if (!existsSync(path)) continue;
    const value = parse(readFileSync(path, 'utf8')) as {
      screenshots?: Array<{ image: string }>;
      modules?: Array<{ existing_assets?: string[] }>;
    };
    const patterns = [...(value.screenshots ?? []).map((entry) => entry.image),
      ...(value.modules ?? []).flatMap((entry) => entry.existing_assets ?? [])];
    for (const pattern of patterns) {
      for (const image of globSync(pattern, { cwd: root })) referenced.add(image.replaceAll('\\', '/'));
    }
  }
  for (const image of globSync('docs/images/**/*', { cwd: root })) {
    const path = image.replaceAll('\\', '/');
    if (statSync(resolve(root, image)).isFile() && !referenced.has(path)) removeFile(root, path);
  }
}

function removeFile(root: string, path: string): void {
  const target = resolve(root, path);
  const child = relative(root, target);
  if (!child || child === '..' || child.startsWith(`..${sep}`)) throw new CliError(`拒绝删除仓库外文档: ${path}`);
  if (existsSync(target) && statSync(target).isFile()) rmSync(target);
}

/** 锁定规范文档/配置与 AI 源；不包含派生站点、依赖和图片运行产物。 */
export function documentationDigest(root: string, directory: 'docs' | 'ai'): string {
  const files: string[] = [];
  // glob 的默认枚举会漏过 .vitepress。逐层读取包含隐藏的规范配置，同时排除明确的派生目录。
  const visit = (path: string): void => {
    if (path.split('/').some((part) => ['node_modules', 'dist', 'target'].includes(part))) return;
    if (/^docs\/\.vitepress\/(?:source|cache|evidence|\.temp)(?:\/|$)/.test(path)) return;
    if (path === 'docs/.vitepress/navigation.json' || path.endsWith('/i18n-review-queue.md')) return;
    const absolute = resolve(root, path);
    const entry = lstatSync(absolute);
    if (entry.isSymbolicLink()) throw new CliError(`规范资产不接受外部符号链接: ${path}`);
    if (entry.isDirectory()) {
      for (const name of readdirSync(absolute).sort()) visit(`${path}/${name}`);
    } else if (entry.isFile()) files.push(path);
  };
  if (existsSync(resolve(root, directory))) visit(directory);
  files.sort();
  if (!files.length) throw new CliError(`生成工程缺少 ${directory} 规范源`);
  const hash = createHash('sha256');
  for (const path of files) hash.update(`${path}\t${digest(readFileSync(resolve(root, path)))}\n`);
  return hash.digest('hex');
}
