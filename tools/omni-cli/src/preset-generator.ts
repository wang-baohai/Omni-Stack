import { createHash } from 'node:crypto';
import {
  constants as fsConstants,
  copyFileSync,
  existsSync,
  globSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  realpathSync,
  renameSync,
  rmSync,
  rmdirSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { basename, dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { parseDocument, stringify } from 'yaml';
import { expandChangelogClosure, loadCatalog, validateCatalogResources, validateClosureRuntimeDependencies } from './catalog.js';
import { COMPOSE_SERVICE_FILES } from './compose-model.js';
import { CliError } from './errors.js';
import { CRUD_TEMPLATE_VERSION } from './crud-generator.js';
import { resolvePreset } from './presets.js';
import { documentationDigest, pruneDocumentation } from './documentation-delivery.js';
import { GENERATOR_VERSION, SERVICE_TEMPLATE_VERSION } from './service-generator.js';
import type { ModuleCatalog, ModuleDefinition, PresetGenerationLock, PresetGenerationPlan } from './types.js';

export const PRESET_TEMPLATE_VERSION = '1.1.0';
const LOCK_FILE = 'scaffold.lock';
/** 源工程声明版本（非内容摘要；内容溯源由 lock.sourceDigest 承担）。 */
const SOURCE_VERSION = '1.0.0-SNAPSHOT';
const DIGEST_PATTERN = /^[0-9a-f]{64}$/;
// WP-011 S15 修正：.candidate-worktrees 是仓内嵌套的历史候选快照（内含整仓副本与本机绝对路径证据），
// 不得进入生成物；否则生成项目体积膨胀且携带与本交付无关的旧证据路径。
const IGNORED_SEGMENTS = new Set(['.git', '.qoder', '.agents', '.codex', '.idea', '.candidate-worktrees', '.artifacts', '.work', 'node_modules', 'target', 'dist']);

// WP-011 R03 返修：排除历史 WP 专用调试脚本与旧 E2E 工具，避免生成项目携带本机绝对路径与旧栈引用。
// 命中项：scripts/wp008-f/、scripts/wp009/、scripts/wp010/（WP-008/009/010 一次性调试工具，含旧 project 名）；
// omni-frontend/e2e/wp006-visual-screenshot.mjs（WP-006 专用截图工具，引用旧容器名）；
// omni-frontend/e2e/wp009-f/（WP-009 F 隔离靶场 E2E，引用旧 project）。
const GENERATED_SUBPATH_EXCLUDES = [
  'docs/.vitepress/source',
  'docs/.vitepress/cache',
  'docs/.vitepress/.temp',
  'docs/.vitepress/evidence',
  'docs/.vitepress/navigation.json',
  'scripts/wp008-f',
  'scripts/wp009',
  'scripts/wp010',
  'omni-frontend/e2e/wp006-visual-screenshot.mjs',
  'omni-frontend/e2e/wp009-f',
];

/**
 * 顶层交付入口白名单（WP-011 S15）：仓库根同时存放一次性治理/调试产物（旧运行日志、定点返修
 * PowerShell 脚本、临时 compose 覆盖、调试 .java/.class、fixture JSON、历史 handoff md），它们内含本机
 * 绝对路径与旧栈容器名。生成项目只拿下列正式交付入口，AC16“不依赖/不透传源仓库本机资源”才有意义。
 */
const DELIVERABLE_ROOT_DIRS = new Set([
  'ai', 'database', 'docker', 'docs', 'observability', 'omni-backend', 'omni-frontend', 'scaffold', 'scripts', 'tools',
]);
const DELIVERABLE_ROOT_FILES = new Set([
  '.dockerignore', '.env.example', '.gitignore', 'AGENTS.md', 'LICENSE', 'CHANGELOG.md',
  'compose.yaml', 'compose.apps.yaml', 'compose.infra.yaml', 'compose.observability.yaml',
  'compose.docs.yaml', 'compose.tools.yaml',
  'start.sh', 'start.bat', 'stop.sh', 'stop.bat',
]);

/** 判断顶层相对路径是否属于交付入口（目录名/固定文件名/README 多语）。 */
export function isDeliverableRootEntry(childPath: string): boolean {
  const segments = childPath.split(sep);
  const top = segments[0] ?? '';
  if (segments.length > 1) return DELIVERABLE_ROOT_DIRS.has(top);
  if (DELIVERABLE_ROOT_DIRS.has(top) || DELIVERABLE_ROOT_FILES.has(top)) return true;
  return /^README(\.[A-Za-z]{2})?\.md$/.test(top);
}

/** 构建只读预设生成计划。 */
export function planPresetGeneration(workspaceRoot: string, presetId: string, output: string): PresetGenerationPlan {
  if (!output || output.trim().length === 0) throw new CliError('preset create 必须提供非空 --output');
  const catalog = loadCatalog(workspaceRoot);
  validateCatalogResources(workspaceRoot, catalog);
  const resolvedPreset = resolvePreset(workspaceRoot, catalog, presetId);
  const selected = new Set(resolvedPreset.resolvedModules);
  validateSelectedModules(catalog, selected);
  validateClosureRuntimeDependencies(workspaceRoot, catalog, resolvedPreset.resolvedModules);
  const targetDirectory = isAbsolute(output) ? resolve(output) : resolve(workspaceRoot, output);
  validateTarget(workspaceRoot, targetDirectory);
  const selectedDefinitions = catalog.modules.filter((module) => selected.has(module.id));
  return {
    targetDirectory,
    resolved: resolvedPreset,
    omittedModules: catalog.modules.filter((module) => !selected.has(module.id)).map((module) => module.id),
    summary: summarize(selectedDefinitions),
  };
}

/** 原子复制并裁剪一个新的预设工程。 */
export function applyPresetGeneration(workspaceRoot: string, plan: PresetGenerationPlan): PresetGenerationLock {
  validateTarget(workspaceRoot, plan.targetDirectory);
  const parent = dirname(plan.targetDirectory);
  mkdirSync(parent, { recursive: true });
  const staging = resolve(parent, `.${basename(plan.targetDirectory)}.preset-staging-${process.pid}-${Date.now()}`);
  assertSafeChild(parent, staging);
  if (existsSync(staging)) throw new CliError(`预设 staging 已存在: ${staging}`);
  const catalog = loadCatalog(workspaceRoot);
  validateSelectedModules(catalog, new Set(plan.resolved.resolvedModules));
  validateClosureRuntimeDependencies(workspaceRoot, catalog, plan.resolved.resolvedModules);
  const sourceDigest = scaffoldManifestDigest(workspaceRoot);
  try {
    copyCleanWorkspace(workspaceRoot, staging, plan.targetDirectory);
    const presetDirectory = resolve(staging, 'scaffold', 'presets');
    const presetPath = resolve(presetDirectory, `${plan.resolved.preset.id}.yaml`);
    assertSafeChild(presetDirectory, presetPath);
    writeFileSync(presetPath, stringify(plan.resolved.preset, { lineWidth: 0 }), 'utf8');
    pruneWorkspace(staging, catalog, new Set(plan.resolved.resolvedModules), plan.resolved.preset.id, workspaceRoot);
    const lock = createLock(catalog, plan, presetPath, sourceDigest);
    writeFileSync(resolve(staging, LOCK_FILE), `${stringify(lock, { lineWidth: 0 })}`, 'utf8');
    validateGeneratedPreset(staging, plan.resolved.preset.id);
    if (existsSync(plan.targetDirectory)) rmdirSync(plan.targetDirectory);
    renameSync(staging, plan.targetDirectory);
    return lock;
  } catch (error) {
    removeVerifiedTree(staging, parent);
    if (error instanceof CliError) throw error;
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`预设工程生成失败，staging 已清理: ${detail}`);
  }
}

/** 校验生成工程锁和关键裁剪结果。 */
export function validateGeneratedPreset(targetDirectory: string, expectedPresetId?: string): PresetGenerationLock {
  const target = resolve(targetDirectory);
  const lockPath = resolve(target, LOCK_FILE);
  if (!existsSync(lockPath)) throw new CliError(`生成工程缺少 ${LOCK_FILE}`);
  const lock = parseDocument(readFileSync(lockPath, 'utf8')).toJS() as PresetGenerationLock;
  if (lock.generatedBy !== '@omni-stack/cli' || lock.templates?.preset !== PRESET_TEMPLATE_VERSION) {
    throw new CliError('scaffold.lock 所有权或预设模板版本无效');
  }
  if (expectedPresetId && lock.preset?.id !== expectedPresetId) throw new CliError('scaffold.lock 预设 ID 不匹配');
  for (const field of ['sourceDigest', 'catalogDigest', 'presetDigest', 'scaffoldDigest', 'docsDigest', 'aiDigest'] as const) {
    const value = lock[field];
    if (typeof value !== 'string' || !DIGEST_PATTERN.test(value)) {
      throw new CliError(`scaffold.lock 缺少合法的 ${field}，无法确认源与模板锁`);
    }
  }
  if (lock.catalogDigest !== fileDigest(resolve(target, 'scaffold/catalog/modules.yaml'))) {
    throw new CliError('scaffold.lock catalogDigest 与工程内 catalog 不一致');
  }
  if (lock.presetDigest !== fileDigest(resolve(target, 'scaffold/presets', `${lock.preset.id}.yaml`))) {
    throw new CliError('scaffold.lock presetDigest 与工程内预设不一致');
  }
  if (lock.scaffoldDigest !== scaffoldManifestDigest(target)) {
    throw new CliError('scaffold.lock scaffoldDigest 与工程内模板/清单不一致');
  }
  if (lock.docsDigest !== documentationDigest(target, 'docs')) throw new CliError('scaffold.lock docsDigest 与工程内 Wiki/文档配置不一致');
  if (lock.aiDigest !== documentationDigest(target, 'ai')) throw new CliError('scaffold.lock aiDigest 与工程内 AI 资产不一致');
  const selected = new Set(lock.modules.map((module) => module.id));
  const catalog = loadCatalog(target);
  if (catalog.modules.some((module) => !selected.has(module.id)) || catalog.modules.length !== selected.size) {
    throw new CliError('生成工程 catalog 与 scaffold.lock 模块不一致');
  }
  validateCatalogResources(target, catalog);
  return lock;
}

function validateSelectedModules(catalog: ModuleCatalog, selected: Set<string>): void {
  for (const module of catalog.modules.filter((candidate) => selected.has(candidate.id))) {
    for (const conflict of module.conflicts) {
      if (selected.has(conflict)) throw new CliError(`预设同时包含冲突模块: ${module.id} 与 ${conflict}`);
    }
  }
}

function summarize(modules: ModuleDefinition[]): PresetGenerationPlan['summary'] {
  const databases = modules.flatMap((module) => module.database.changelogs)
    .map((path) => path.match(/database\/changelog\/([^/]+)\//)?.[1])
    .filter((value): value is string => value !== undefined);
  return {
    backendModules: unique(modules.flatMap((module) => module.backendModules)),
    composeServices: unique(modules.flatMap((module) => module.composeServices)),
    gatewayRoutes: unique(modules.flatMap((module) => module.gatewayRoutes)),
    ports: [...new Set(modules.flatMap((module) => module.ports))].sort((left, right) => left - right),
    databases: unique(databases),
    permissionRoots: unique(modules.flatMap((module) => module.permissionRoots)),
    minimumMemoryMb: modules.reduce((total, module) => total + module.resourceHints.minimumMemoryMb, 0),
    recommendedMemoryMb: modules.reduce((total, module) => total + module.resourceHints.recommendedMemoryMb, 0),
  };
}

function pruneWorkspace(root: string, catalog: ModuleCatalog, selected: Set<string>, presetId: string, sourceRoot: string): void {
  const omitted = catalog.modules.filter((module) => !selected.has(module.id));
  const selectedDefinitions = catalog.modules.filter((module) => selected.has(module.id));
  const retainedDocs = new Set(selectedDefinitions.flatMap((module) => module.docs));
  const retainedChangelogs = new Set(selectedDefinitions.flatMap((module) => module.database.changelogs));
  // 反向扫描要求裁剪树内每个 changelog 都被归属，因此必须沿 include 链整棵删除，
  // 同时保留被留存模块共享的子文件（如 common/*-mq.yaml）。
  const retainedChangelogClosure = expandChangelogClosure(root, [...retainedChangelogs]);

  for (const module of omitted) {
    for (const backendModule of module.backendModules) removeRelative(root, `omni-backend/${backendModule}`);
    for (const pattern of [
      ...module.frontend.viewGlobs,
      ...module.frontend.componentGlobs,
      ...module.frontend.apiGlobs,
    ]) {
      for (const match of globSync(pattern, { cwd: root })) removeRelative(root, match.replaceAll('\\', '/'));
    }
    for (const document of module.docs) if (!retainedDocs.has(document)) removeRelative(root, document);
    for (const changelog of expandChangelogClosure(root, module.database.changelogs)) {
      if (!retainedChangelogClosure.has(changelog)) removeRelative(root, changelog);
    }
  }

  filterParentPom(root, selectedDefinitions);
  filterDockerfile(root, selectedDefinitions);
  filterCompose(root, selectedDefinitions);
  filterGateway(root, selectedDefinitions);
  filterMqBindings(root, catalog, selected);
  filterOptionalInfrastructure(root, catalog, selected);
  filterWorkflowBusinessModels(root, selected);
  filterCatalog(root, selected);
  filterAuthSeed(root, selected);
  filterSeedManifest(root, selected);
  refreshSeedSourceDigests(root);
  filterAdoptionBaseline(root, selected);
  filterPlatformDatabases(root, selected);
  filterMigrationTargets(root, selected);
  filterFrontendLocales(root, catalog, selected);
  filterHomeWorkspace(root, selected);
  filterFrontendRegistrations(root, omitted.map((module) => module.id));
  filterPresetFiles(root, presetId);
  if (!selected.has('workflow')) {
    removeRelative(root, 'scripts/fixtures/run-workflow-scope-fixture.ps1');
    removeRelative(root, 'omni-frontend/e2e-docs/flows/workflow-teaching.flows.spec.ts');
    removeRelative(root, 'omni-frontend/e2e-docs/flows/workflow-teaching-ui.flows.spec.ts');
    removeRelative(root, 'omni-frontend/e2e-docs/flows/workflow-negative-teaching.flows.spec.ts');
    removeRelative(root, 'omni-frontend/tests/workflow');
    removeRelative(root, 'docs/reference/evidence/workflow-2026-10-10.json');
    const packagePath = resolve(root, 'omni-frontend/package.json');
    const packageJson = JSON.parse(readFileSync(packagePath, 'utf8')) as { scripts: Record<string, string> };
    packageJson.scripts['test:unit'] = 'node --experimental-strip-types --test "tests/wp012/*.test.ts"';
    writeFileSync(packagePath, `${JSON.stringify(packageJson, null, 2)}\n`, 'utf8');
  }
  if (!selected.has('crm')) {
    removeRelative(root, 'omni-frontend/e2e-docs/flows/crm-teaching.flows.spec.ts');
    removeRelative(root, 'omni-frontend/e2e-docs/flows/crm-teaching-ui.flows.spec.ts');
    removeRelative(root, 'docs/reference/evidence/crm-2026-10-10.json');
  }
  if (!selected.has('xxl-job')) {
    removeRelative(root, 'omni-frontend/e2e-docs/flows/scheduling.flows.spec.ts');
    removeRelative(root, 'omni-frontend/e2e-docs/flows/scheduling-teaching-ui.flows.spec.ts');
    removeRelative(root, 'omni-frontend/e2e-docs/fixtures/xxl-audit.ts');
    removeRelative(root, 'docs/reference/evidence/scheduling-2026-10-10.json');
  }
  if (!selected.has('procurement') || !selected.has('asset')) {
    removeRelative(root, 'omni-frontend/e2e-docs/flows/supply-chain-teaching.flows.spec.ts');
    removeRelative(root, 'docs/reference/evidence/supply-chain-2026-10-09.json');
  }
  pruneDocumentation(root, selected, presetId, sourceRoot);
  removeEmptyDirectories(resolve(root, 'database/changelog'));
}

function filterParentPom(root: string, selectedModules: ModuleDefinition[]): void {
  const selected = new Set(selectedModules.flatMap((module) => module.backendModules));
  const path = resolve(root, 'omni-backend/pom.xml');
  const content = readFileSync(path, 'utf8').replace(/\s*<module>([^<]+)<\/module>/g, (match, artifact: string) =>
    selected.has(artifact) ? match : '');
  writeFileSync(path, content, 'utf8');
}

function filterDockerfile(root: string, selectedModules: ModuleDefinition[]): void {
  const selected = new Set(selectedModules.flatMap((module) => module.backendModules));
  const path = resolve(root, 'docker/backend/Dockerfile');
  const content = readFileSync(path, 'utf8').split(/\r?\n/).filter((line) => {
    const match = line.match(/^COPY omni-backend\/([^/\s]+)\/pom\.xml /);
    return !match || selected.has(match[1]!);
  }).join('\n');
  writeFileSync(path, `${content.replace(/\s*$/, '')}\n`, 'utf8');
}

function filterCompose(root: string, selectedModules: ModuleDefinition[]): void {
  const retained = new Set(selectedModules.flatMap((module) => module.composeServices));
  const backendServices = new Set(selectedModules.flatMap((module) => module.backendModules));
  for (const relativePath of COMPOSE_SERVICE_FILES) {
    const path = resolve(root, relativePath);
    const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
      services?: Record<string, {
        depends_on?: Record<string, unknown> | string[];
        environment?: Record<string, unknown>;
        profiles?: string[];
      }>;
    };
    for (const service of Object.keys(value.services ?? {})) if (!retained.has(service)) delete value.services?.[service];
    for (const [serviceName, service] of Object.entries(value.services ?? {})) {
      delete service.profiles;
      if (Array.isArray(service.depends_on)) {
        service.depends_on = service.depends_on.filter((dependency) => retained.has(dependency));
      } else if (service.depends_on) {
        for (const dependency of Object.keys(service.depends_on)) {
          if (!retained.has(dependency)) delete service.depends_on[dependency];
        }
      }
      if (!retained.has('xxl-job-admin') && backendServices.has(serviceName) && service.environment) {
        const xxlKeys = Object.keys(service.environment).filter((key) => key.startsWith('XXL_JOB_'));
        for (const key of xxlKeys) delete service.environment[key];
        if (xxlKeys.length > 0) service.environment.XXL_JOB_EXECUTOR_ENABLED = 'false';
      }
      if (!retained.has('rocketmq-namesrv') && backendServices.has(serviceName) && service.environment) {
        for (const key of Object.keys(service.environment)) if (key.includes('ROCKETMQ')) delete service.environment[key];
      }
    }
    writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
  }
}

function filterOptionalInfrastructure(root: string, catalog: ModuleCatalog, selected: Set<string>): void {
  if (selected.has('rocketmq')) return;
  const selectedBackendModules = catalog.modules
    .filter((module) => selected.has(module.id))
    .flatMap((module) => module.backendModules);
  for (const backendModule of selectedBackendModules) {
    const path = resolve(root, `omni-backend/${backendModule}/src/main/resources/application.yml`);
    if (!existsSync(path)) continue;
    const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
      spring?: { cloud?: {
        function?: { definition?: string };
        stream?: {
          rocketmq?: unknown;
          bindings?: Record<string, unknown>;
          function?: { autodetect?: boolean };
        };
      } };
    };
    const cloud = value.spring?.cloud;
    if (!cloud?.stream) continue;
    delete cloud.stream.rocketmq;
    cloud.stream.bindings = {};
    cloud.stream.function = { ...(cloud.stream.function ?? {}), autodetect: false };
    if (cloud.function) cloud.function.definition = '';
    writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
  }
}

function filterGateway(root: string, selectedModules: ModuleDefinition[]): void {
  const retained = new Set(selectedModules.flatMap((module) => module.gatewayRoutes));
  const path = resolve(root, 'omni-backend/omni-gateway/src/main/resources/application.yml');
  const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
    spring?: { cloud?: { gateway?: { server?: { webflux?: { routes?: Array<{ id?: string }> } } } } };
  };
  const webflux = value.spring?.cloud?.gateway?.server?.webflux;
  if (!webflux?.routes) throw new CliError('Gateway 配置缺少 routes');
  webflux.routes = webflux.routes.filter((route) => route.id && retained.has(route.id));
  const retainedServices = new Set(selectedModules.flatMap((module) => module.backendModules));
  for (const route of webflux.routes) {
    if (route.id !== 'block-internal-api-services') continue;
    const predicates = (route as { predicates?: string[] }).predicates ?? [];
    (route as { predicates?: string[] }).predicates = predicates.map((predicate) => {
      if (!predicate.startsWith('Path=')) return predicate;
      const paths = predicate.slice('Path='.length).split(',').filter((entry) => {
        const service = entry.match(/^\/(omni-[a-z-]+)\//)?.[1];
        return !service || retainedServices.has(service);
      });
      return `Path=${paths.join(',')}`;
    });
  }
  writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
}

function filterMqBindings(root: string, catalog: ModuleCatalog, selected: Set<string>): void {
  const selectedDefinitions = catalog.modules.filter((module) => selected.has(module.id));
  const selectedProducerDestinations = new Set(selectedDefinitions.flatMap((module) => module.mq.producers));
  const removedProducerDestinations = new Set(catalog.modules
    .filter((module) => !selected.has(module.id))
    .flatMap((module) => module.mq.producers)
    .filter((destination) => !selectedProducerDestinations.has(destination)));
  if (removedProducerDestinations.size === 0) return;

  for (const backendModule of selectedDefinitions.flatMap((module) => module.backendModules)) {
    const path = resolve(root, `omni-backend/${backendModule}/src/main/resources/application.yml`);
    if (!existsSync(path)) continue;
    const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
      spring?: { cloud?: {
        function?: { definition?: string };
        stream?: { bindings?: Record<string, { destination?: string }> };
      } };
    };
    const cloud = value.spring?.cloud;
    const bindings = cloud?.stream?.bindings;
    if (!bindings) continue;
    const removedFunctions = new Set<string>();
    for (const [bindingName, binding] of Object.entries(bindings)) {
      if (!binding.destination || !removedProducerDestinations.has(binding.destination)) continue;
      delete bindings[bindingName];
      const functionName = bindingName.match(/^(.+?)-(?:in|out)-\d+$/)?.[1];
      if (functionName) removedFunctions.add(functionName);
    }
    const definition = cloud?.function?.definition;
    if (definition && removedFunctions.size > 0) {
      cloud!.function!.definition = definition.split(';')
        .filter((functionName) => !removedFunctions.has(functionName))
        .join(';');
    }
    writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
  }
}

function filterWorkflowBusinessModels(root: string, selected: Set<string>): void {
  if (!selected.has('workflow')) return;
  const modelOwners = new Map([
    ['procurement-approval', 'procurement'],
    ['asset-transfer', 'asset'],
    ['asset-disposal', 'asset'],
    ['supplier-onboarding', 'srm'],
  ]);
  const seedPath = resolve(root, 'scripts/sql/seed/workflow.sql');
  let seed = readFileSync(seedPath, 'utf8').replace(/\r\n?/g, '\n');
  const marker = 'INSERT IGNORE INTO wf_process_model (id, tenant_id, model_key';
  const starts: number[] = [];
  for (let offset = seed.indexOf(marker); offset >= 0; offset = seed.indexOf(marker, offset + marker.length)) starts.push(offset);
  if (starts.length === 0) throw new CliError('无法定位工作流默认模型种子');
  let filtered = seed.slice(0, starts[0]);
  for (let index = 0; index < starts.length; index += 1) {
    const block = seed.slice(starts[index], starts[index + 1] ?? seed.length);
    const modelKey = block.match(/VALUES \(\d+, 1, '([^']+)'/)?.[1];
    if (!modelKey) throw new CliError('工作流默认模型种子缺少 model_key');
    const owner = modelOwners.get(modelKey);
    if (!owner || selected.has(owner)) filtered += block;
  }
  writeFileSync(seedPath, `${filtered.replace(/\s*$/, '')}\n`, 'utf8');

  if (!selected.has('srm')) {
    removeRelative(root, 'omni-backend/omni-workflow/src/main/resources/bpmn/supplier-onboarding.bpmn20.xml');
  }
  const retainedBusinessModels = [...modelOwners.values()].some((owner) => selected.has(owner));
  if (!retainedBusinessModels) {
    removeRelative(root, 'omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/RequiredWorkflowModelInitializer.java');
    removeRelative(root, 'omni-backend/omni-workflow/src/test/java/com/omni/workflow/config/RequiredWorkflowModelInitializerTest.java');
    return;
  }
  const initializerPath = resolve(
    root,
    'omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/RequiredWorkflowModelInitializer.java',
  );
  let initializer = readFileSync(initializerPath, 'utf8');
  const retainedEntries = [
    ['supplier-onboarding', 'SRM_SUPPLIER_ONBOARDING', 'srm'],
    ['procurement-approval', 'purchase', 'procurement'],
    ['asset-transfer', 'ASSET_TRANSFER', 'asset'],
    ['asset-disposal', 'ASSET_DISPOSAL', 'asset'],
  ].filter((entry) => selected.has(entry[2]!));
  const renderedEntries = retainedEntries.map((entry, index) =>
    `            "${entry[0]}", "${entry[1]}"${index === retainedEntries.length - 1 ? '' : ','}`).join('\n');
  initializer = initializer.replace(
    /private static final Map<String, String> REQUIRED_MODELS = Map\.of\([\s\S]*?\);/,
    `private static final Map<String, String> REQUIRED_MODELS = Map.of(\n${renderedEntries});`,
  );
  writeFileSync(initializerPath, initializer, 'utf8');
  if (retainedEntries.length < modelOwners.size) {
    removeRelative(root, 'omni-backend/omni-workflow/src/test/java/com/omni/workflow/config/RequiredWorkflowModelInitializerTest.java');
  }
}

function filterCatalog(root: string, selected: Set<string>): void {
  const path = resolve(root, 'scaffold/catalog/modules.yaml');
  const value = parseDocument(readFileSync(path, 'utf8')).toJS() as ModuleCatalog;
  value.modules = value.modules.filter((module) => selected.has(module.id)).map((module) => ({
    ...module,
    optionalModules: module.optionalModules.filter((moduleId) => selected.has(moduleId)),
    conflicts: module.conflicts.filter((moduleId) => selected.has(moduleId)),
  }));
  writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
}

function filterSeedManifest(root: string, selected: Set<string>): void {
  const path = resolve(root, 'database/seed/manifest.yaml');
  const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
    sources?: Array<{ module?: string; resource?: string }>;
    modules?: Array<{ id?: string }>;
    assertions?: Array<{
      id?: string;
      module?: string;
      expectedRows?: number;
      expectedSha256?: string;
    }>;
  };
  const removedSources = (value.sources ?? []).filter((source) => source.module && !selected.has(source.module));
  value.sources = (value.sources ?? []).filter((source) => !source.module || selected.has(source.module));
  value.modules = (value.modules ?? []).filter((module) => module.id && selected.has(module.id));
  value.assertions = (value.assertions ?? []).filter((assertion) => !assertion.module || selected.has(assertion.module));
  const workflowModelAssertion = value.assertions.find((assertion) => assertion.id === 'workflow-model-catalog');
  if (workflowModelAssertion) {
    const retainedModelKeys = [
      'leave',
      ...(selected.has('procurement') ? ['procurement-approval'] : []),
      ...(selected.has('asset') ? ['asset-transfer', 'asset-disposal'] : []),
      ...(selected.has('srm') ? ['supplier-onboarding'] : []),
    ];
    workflowModelAssertion.expectedRows = retainedModelKeys.length;
    workflowModelAssertion.expectedSha256 = workflowModelAssertionSha256(retainedModelKeys);
  }
  for (const source of removedSources) if (source.resource) removeRelative(root, source.resource);
  writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
}

function workflowModelAssertionSha256(modelKeys: string[]): string {
  // SeedVerificationService 对 MySQL VARCHAR（JDBC type 12）结果生成同一规范串。
  const canonicalRows = [...modelKeys].sort().map((modelKey) => `seed_key:12=${modelKey}`);
  return createHash('sha256').update(canonicalRows.join('\n'), 'utf8').digest('hex');
}

function filterAuthSeed(root: string, selected: Set<string>): void {
  const canonicalPath = resolve(root, 'scripts/sql/seed/auth.sql');
  let canonicalContent = readFileSync(canonicalPath, 'utf8').replace(/\r\n?/g, '\n');
  canonicalContent = removeAuthSeedModules(canonicalContent, selected);
  writeFileSync(canonicalPath, `${canonicalContent.replace(/\s*$/, '')}\n`, 'utf8');
  // WP-011 R04 修复：历史冻结副本区段结构与 canonical auth.sql 不同，
  // 仅切除 sys_permission INSERT 块和末尾 ASSET_ADMIN 块，不触及 sys_user 块，
  // 避免误删 zhangsan 等测试用户。403 检测仍能正确工作，因为角色权限关联随权限块一同删除。
  const historyPath = resolve(root, 'database/seed/history/auth-0003-bootstrap-seed.sql');
  if (!existsSync(historyPath)) return;
  let historyContent = readFileSync(historyPath, 'utf8').replace(/\r\n?/g, '\n');
  // 业务模块权限块边界（元组字符串与历史副本实际内容完全对应）
  if (!selected.has('workflow')) {
    historyContent = removeSqlRangeByTuple(historyContent, "(200, 1, 0,   'workflow'", "(300, 1, 0,   'crm'");
  }
  if (!selected.has('crm')) {
    historyContent = removeSqlRangeByTuple(historyContent, "(300, 1, 0,   'crm'", "(400, 1, 0,   'srm'");
  }
  if (!selected.has('srm')) {
    historyContent = removeSqlRangeByTuple(historyContent, "(400, 1, 0,   'srm'", "(500, 1, 0,   'procurement'");
  }
  if (!selected.has('procurement')) {
    // procurement 块到 sys_org_unit 销售部门块之间，不触及 sys_user
    historyContent = removeSqlRangeByTuple(historyContent, "(500, 1, 0,   'procurement'", "(100, 1, 1, '\u9500\u552e\u90e8'");
  }
  if (!selected.has('asset')) {
    // 历史副本末尾：从 ASSET_ADMIN role INSERT 块开始截断至文件尾（与 canonical auth.sql 同逻辑）
    const assetTuple = historyContent.indexOf("(40, 1, 'ASSET_ADMIN'");
    if (assetTuple >= 0) {
      const truncateStart = historyContent.lastIndexOf('\nINSERT IGNORE', assetTuple);
      if (truncateStart >= 0) {
        historyContent = historyContent.slice(0, truncateStart + 1);
      }
    }
  }
  writeFileSync(historyPath, `${historyContent.replace(/\s*$/, '')}\n`, 'utf8');
}

/** 从内容中按区段删除未选入预设的业务模块权限 / 角色 / 用户 / 菜单条目。 */
function removeAuthSeedModules(content: string, selected: Set<string>): string {
  if (!selected.has('workflow')) {
    content = removeSqlRangeByTuple(content, "(200, 1, 0,   'workflow'", "(300, 1, 0,   'crm'");
  }
  if (!selected.has('crm')) {
    content = removeSqlRangeByTuple(content, "(300, 1, 0,   'crm'", "(400, 1, 0,   'srm'");
  }
  if (!selected.has('srm')) {
    content = removeSqlRangeByTuple(content, "(400, 1, 0,   'srm'", "(500, 1, 0,   'procurement'");
  }
  if (!selected.has('procurement')) {
    content = removeSqlRangeByTuple(content, "(500, 1, 0,   'procurement'", "(100, 1, 1, '销售部'");
  }
  if (!selected.has('workflow')) {
    content = removeSqlRangeByTuple(content, "(10, 1, 'EMPLOYEE'", "(200, 1, 'supplier1'");
  }
  if (!selected.has('srm')) {
    content = removeSqlRangeByTuple(content, "(200, 1, 'supplier1'", "(40, 1, 'ASSET_ADMIN'");
  }
  if (!selected.has('asset')) {
    const tuple = content.indexOf("(40, 1, 'ASSET_ADMIN'");
    if (tuple < 0) throw new CliError('无法定位 asset 权限种子区段');
    const start = content.lastIndexOf('\nINSERT IGNORE', tuple);
    if (start < 0) throw new CliError('无法定位 asset 权限种子起点');
    content = content.slice(0, start + 1);
  }
  return content;
}

function removeSqlRangeByTuple(content: string, startTuple: string, endTuple: string): string {
  const tupleStart = content.indexOf(startTuple);
  const tupleEnd = content.indexOf(endTuple);
  if (tupleStart < 0 || tupleEnd < 0 || tupleEnd <= tupleStart) {
    throw new CliError(`无法定位权限种子区段: ${startTuple}`);
  }
  const start = content.lastIndexOf('\nINSERT IGNORE', tupleStart);
  const end = content.lastIndexOf('\nINSERT IGNORE', tupleEnd);
  if (start < 0 || end < 0 || end <= start) throw new CliError(`权限种子区段边界无效: ${startTuple}`);
  return `${content.slice(0, start + 1)}${content.slice(end + 1)}`;
}

function refreshSeedSourceDigests(root: string): void {
  const path = resolve(root, 'database/seed/manifest.yaml');
  const value = parseDocument(readFileSync(path, 'utf8')).toJS() as {
    sources?: Array<{ resource?: string; sha256?: string }>;
  };
  for (const source of value.sources ?? []) {
    if (!source.resource) throw new CliError('种子清单 source 缺少 resource');
    const resourcePath = resolve(root, ...source.resource.split('/'));
    if (!existsSync(resourcePath)) throw new CliError(`种子清单资源不存在: ${source.resource}`);
    const canonical = readFileSync(resourcePath, 'utf8').replace(/\r\n?/g, '\n');
    source.sha256 = createHash('sha256').update(canonical, 'utf8').digest('hex');
  }
  writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
}

function filterAdoptionBaseline(root: string, selected: Set<string>): void {
  const path = resolve(root, 'database/adoption/baseline-09a29fe.yaml');
  const value = parseDocument(readFileSync(path, 'utf8')).toJS() as { targets?: Array<{ id?: string }> };
  value.targets = (value.targets ?? []).filter((target) => target.id && selected.has(target.id));
  writeFileSync(path, stringify(value, { lineWidth: 0 }), 'utf8');
}

function filterPlatformDatabases(root: string, selected: Set<string>): void {
  const databases = new Map([
    ['auth', 'omni_auth'], ['base', 'omni_base'], ['workflow', 'omni_workflow'], ['crm', 'omni_crm'],
    ['srm', 'omni_srm'], ['procurement', 'omni_procurement'], ['asset', 'omni_asset'],
    ['nacos', 'nacos_config'], ['xxl-job', 'xxl_job'],
  ]);
  const path = resolve(root, 'database/changelog/platform/db.changelog-platform.yaml');
  let content = readFileSync(path, 'utf8');
  for (const [moduleId, database] of databases) {
    if (!selected.has(moduleId)) content = content.replace(new RegExp(`^\\s+CREATE DATABASE IF NOT EXISTS ${database} .+\\r?\\n`, 'm'), '');
  }
  writeFileSync(path, content, 'utf8');
}

function filterMigrationTargets(root: string, selected: Set<string>): void {
  const path = resolve(root, 'omni-backend/omni-db-migrator/src/main/java/com/omni/dbmigrator/migration/MigrationTargetCatalog.java');
  let content = readFileSync(path, 'utf8');
  const listPattern = /(private static final List<MigrationTarget> TARGETS = List\.of\()([\s\S]*?)(\);)/;
  const listMatch = content.match(listPattern);
  if (!listMatch) throw new CliError('无法定位数据库迁移目标列表');
  const retainedTargets = [...listMatch[2]!.matchAll(/^\s*(target\("([a-z-]+)"[^\n]*?\))[,;]?\s*$/gm)]
    .filter((match) => selected.has(match[2]!))
    .map((match) => `            ${match[1]}`);
  if (retainedTargets.length === 0) throw new CliError('预设必须至少保留一个数据库迁移目标');
  const renderedTargets = retainedTargets.map((line, index) =>
    `${line}${index === retainedTargets.length - 1 ? '' : ','}`).join('\n');
  content = content.replace(listPattern, `$1\n${renderedTargets}$3`);
  const targetCount = retainedTargets.length;
  content = content.replaceAll('的九个目标数据库', `的 ${targetCount} 个目标数据库`)
    .replaceAll('九个目标数据库', `${targetCount} 个目标数据库`)
    .replaceAll('九个目标', `${targetCount} 个目标`);
  writeFileSync(path, `${content.replace(/\s*$/, '')}\n`, 'utf8');
}

function filterHomeWorkspace(root: string, selected: Set<string>): void {
  const path = resolve(root, 'omni-frontend/src/views/home/index.vue');
  let content = readFileSync(path, 'utf8');
  if (!selected.has('workflow')) {
    content = removeImport(content, '@/api/workflow');
    content = removeImport(content, '@/api/procurement-requisition');
    content = removeImport(content, '@/api/asset-transfer');
    content = removeImport(content, '@/api/asset-disposal');
    content = content.replace("import { useDictOptions } from '@/composables/useDictOptions'\n", '');
    content = removeBetween(content, '// ─── 流程分类字典映射 ───', '// ─── 通用状态 ───', true);
    content = content.replace(/\s*\/\/ 加载工作流统计和待办\r?\n\s*loadWfStats\(\)\r?\n\s*loadTodoList\(\)/, '');
    content = removeBetween(content, '// ─── 工作流标签页状态 ───', '</script>', false);
    content = removeBetween(content, '        <!-- Tab 1: 待我审批 -->', '        <!-- Tab 4: 我的定时任务 -->', true);
    content = content.replace('<el-tabs v-model="activeTab" @tab-change="handleTabChange">', '<el-tabs model-value="jobs">');
    content = removeBetween(content, '      <!-- 审批对话框 -->', '    </main>', true);
    content = content.replace('</el-tabs>\n\n    </main>', '</el-tabs>\n    </main>');
  } else if (!selected.has('procurement') && !selected.has('asset')) {
    content = removeImport(content, '@/api/procurement-requisition');
    content = removeImport(content, '@/api/asset-transfer');
    content = removeImport(content, '@/api/asset-disposal');
    content = content.replace(
      /  completeApproval, getTaskFormData, getWorkspaceStats,/,
      '  completeApproval, getWorkspaceStats,',
    );
    content = content.replace(
      /const businessFormLoading = ref\(false\)[\s\S]*?const approvalCanSubmit = computed\(\(\) => Boolean\([\s\S]*?\)\)\r?\n/,
      'const approvalCanSubmit = computed(() => Boolean(approvalTask.value))\n',
    );
    content = content.replace(
      /function positiveInteger\(value: unknown\): number \| null \{[\s\S]*?\r?\n\}\r?\n\r?\n/,
      '',
    );
    content = content.replace(
      /async function openApproval\(task: TodoTask\) \{[\s\S]*?\n\}/,
      `async function openApproval(task: TodoTask) {\n  approvalTask.value = task\n  approvalForm.approved = true\n  approvalForm.comment = ''\n  approvalVisible.value = true\n}`,
    );
    content = content.replace(
      /  if \(hasBusinessApprovalView\.value\) \{[\s\S]*?  \} else \{\r?\n    ElMessage\.success\(t\('common\.success'\)\)\r?\n  \}/,
      "  ElMessage.success(t('common.success'))",
    );
    content = content.replace(":width=\"hasBusinessApprovalView ? '860px' : '500px'\"", 'width="500px"');
    content = removeBetween(content, '        <div v-loading="businessFormLoading">', '        <el-form :model="approvalForm" label-width="100">', true);
  }
  writeFileSync(path, `${content.replace(/\s*$/, '')}\n`, 'utf8');
}

function filterFrontendLocales(root: string, catalog: ModuleCatalog, selected: Set<string>): void {
  const retainedPrefixes = new Set(catalog.modules
    .filter((module) => selected.has(module.id))
    .flatMap((module) => module.frontend.i18nPrefixes));
  const removedPrefixes = new Set(catalog.modules
    .filter((module) => !selected.has(module.id))
    .flatMap((module) => module.frontend.i18nPrefixes)
    .filter((prefix) => !retainedPrefixes.has(prefix)));
  if (removedPrefixes.size === 0) return;
  for (const relativePath of globSync('omni-frontend/src/locales/*.ts', { cwd: root })) {
    const path = resolve(root, relativePath);
    const lines = readFileSync(path, 'utf8').split(/\r?\n/);
    const topLevelProperties = lines
      .map((line, index) => ({ match: line.match(/^  ([A-Za-z][A-Za-z0-9]*): \{$/), index }))
      .filter((entry): entry is { match: RegExpMatchArray; index: number } => entry.match !== null);
    const removeLines = new Set<number>();
    for (let index = 0; index < topLevelProperties.length; index += 1) {
      const property = topLevelProperties[index]!;
      if (!removedPrefixes.has(property.match[1]!)) continue;
      const end = topLevelProperties[index + 1]?.index ?? lines.length - 1;
      for (let line = property.index; line < end; line += 1) removeLines.add(line);
    }
    const content = lines.filter((_line, index) => !removeLines.has(index)).join('\n');
    writeFileSync(path, `${content.replace(/\s*$/, '')}\n`, 'utf8');
  }
}

function removeImport(content: string, modulePath: string): string {
  const lines = content.split(/\r?\n/);
  for (let index = lines.length - 1; index >= 0; index -= 1) {
    if (!lines[index]!.includes(`from '${modulePath}'`)) continue;
    let start = index;
    while (start > 0 && !lines[start]!.startsWith('import ')) start -= 1;
    if (!lines[start]!.startsWith('import ')) throw new CliError(`无法定位前端导入起点: ${modulePath}`);
    lines.splice(start, index - start + 1);
    index = start;
  }
  return lines.join('\n');
}

function removeBetween(content: string, startMarker: string, endMarker: string, keepEnd: boolean): string {
  const start = content.indexOf(startMarker);
  const end = content.indexOf(endMarker, start + startMarker.length);
  if (start < 0 || end < 0) throw new CliError(`无法裁剪前端工作台区段: ${startMarker}`);
  return `${content.slice(0, start)}${keepEnd ? content.slice(end) : `${endMarker}${content.slice(end + endMarker.length)}`}`;
}

function filterFrontendRegistrations(root: string, omittedModuleIds: string[]): void {
  const businessIds = omittedModuleIds.filter((id) => ['workflow', 'crm', 'srm', 'procurement', 'asset'].includes(id));
  if (businessIds.length === 0) return;
  const expression = new RegExp(`^\\s+'(?:${businessIds.join('|')})(?::[^']*)?':`);
  for (const relativePath of ['omni-frontend/src/constants/menu.ts', 'omni-frontend/src/router/index.ts']) {
    const path = resolve(root, relativePath);
    let content = readFileSync(path, 'utf8').split(/\r?\n/).filter((line) => !expression.test(line)).join('\n');
    if (businessIds.includes('srm') && relativePath.endsWith('router/index.ts')) {
      content = removeRouteObjects(content, new Set(['PortalLogin', 'PortalRegister', 'SupplierPortal']));
    }
    writeFileSync(path, `${content.replace(/\s*$/, '')}\n`, 'utf8');
  }
}

function removeRouteObjects(content: string, routeNames: Set<string>): string {
  let result = content;
  for (const routeName of routeNames) {
    const nameIndex = result.indexOf(`    name: '${routeName}',`);
    if (nameIndex < 0) continue;
    const start = result.lastIndexOf('  {\n', nameIndex);
    if (start < 0) throw new CliError(`无法定位前端静态路由: ${routeName}`);
    let depth = 0;
    let end = -1;
    for (let index = start; index < result.length; index += 1) {
      if (result[index] === '{') depth += 1;
      if (result[index] === '}') {
        depth -= 1;
        if (depth === 0) {
          end = result[index + 1] === ',' ? index + 2 : index + 1;
          break;
        }
      }
    }
    if (end < 0) throw new CliError(`前端静态路由括号不完整: ${routeName}`);
    if (result[end] === '\n') end += 1;
    result = `${result.slice(0, start)}${result.slice(end)}`;
  }
  return result;
}

function filterPresetFiles(root: string, presetId: string): void {
  const directory = resolve(root, 'scaffold/presets');
  for (const file of readdirSync(directory)) {
    if (file !== `${presetId}.yaml`) removeRelative(root, `scaffold/presets/${file}`);
  }
}

function copyCleanWorkspace(sourceRoot: string, targetRoot: string, finalTarget: string): void {
  const normalizedTarget = resolve(targetRoot);
  const normalizedFinal = resolve(finalTarget);
  // 先固定每个目录的去重条目，再逐个复制，避免共享挂载遍历期间重复枚举同一路径。
  const visit = (source: string, destination: string): void => {
      const resolvedSource = resolve(source);
      if (resolvedSource === normalizedTarget || resolvedSource.startsWith(`${normalizedTarget}${sep}`)) return;
      if (resolvedSource === normalizedFinal || resolvedSource.startsWith(`${normalizedFinal}${sep}`)) return;
      const child = relative(sourceRoot, resolvedSource);
      if (child.split(sep).some((segment) => IGNORED_SEGMENTS.has(segment))) return;
      // 排除特定子路径（WP-011 R03：不携带历史 WP 专用工具）
      const normalizedChild = child.split(sep).join('/');
      if (GENERATED_SUBPATH_EXCLUDES.some(ex => normalizedChild === ex || normalizedChild.startsWith(ex + '/'))) return;
      if (child && !isDeliverableRootEntry(child)) return;
      const name = basename(resolvedSource);
      if (name !== '.env.example' && name.startsWith('.env')) return;
      if (name.endsWith('.tsbuildinfo')) return;
      const sourceState = lstatSync(source);
      if (sourceState.isSymbolicLink()) throw new CliError(`交付源不得含指向工作区外的符号链接: ${child}`);
      if (sourceState.isDirectory()) {
        mkdirSync(destination);
        const children = [...new Set(readdirSync(source))].sort();
        for (const entry of children) visit(resolve(source, entry), resolve(destination, entry));
      } else if (sourceState.isFile()) {
        copyFileSync(source, destination, fsConstants.COPYFILE_EXCL);
      } else {
        throw new CliError(`交付源含非普通文件: ${child}`);
      }
  };
  visit(sourceRoot, targetRoot);
}

function createLock(
  catalog: ModuleCatalog,
  plan: PresetGenerationPlan,
  presetPath: string,
  sourceDigest: string,
): PresetGenerationLock {
  const selected = new Set(plan.resolved.resolvedModules);
  const targetRoot = dirname(dirname(dirname(presetPath)));
  return {
    generatedBy: '@omni-stack/cli',
    generatorVersion: GENERATOR_VERSION,
    sourceVersion: SOURCE_VERSION,
    sourceDigest,
    catalogDigest: fileDigest(resolve(targetRoot, 'scaffold/catalog/modules.yaml')),
    presetDigest: fileDigest(presetPath),
    scaffoldDigest: scaffoldManifestDigest(targetRoot),
    docsDigest: documentationDigest(targetRoot, 'docs'),
    aiDigest: documentationDigest(targetRoot, 'ai'),
    catalogVersion: catalog.version,
    preset: { id: plan.resolved.preset.id, version: plan.resolved.preset.version },
    modules: catalog.modules.filter((module) => selected.has(module.id)).map((module) => ({ id: module.id, version: module.version })),
    templates: { preset: PRESET_TEMPLATE_VERSION, service: SERVICE_TEMPLATE_VERSION, crud: CRUD_TEMPLATE_VERSION },
  };
}

function fileDigest(path: string): string {
  if (!existsSync(path)) throw new CliError(`无法计算摘要，文件不存在: ${path}`);
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

/** 对 scaffold/ 下全部文件生成稳定清单摘要（路径排序 + 内容 SHA-256）。 */
function scaffoldManifestDigest(root: string): string {
  const directory = resolve(root, 'scaffold');
  if (!existsSync(directory)) throw new CliError(`无法计算摘要，scaffold 目录不存在: ${directory}`);
  const files = globSync('scaffold/**/*', { cwd: root })
    .map((entry) => entry.replaceAll('\\', '/'))
    .filter((relativePath) => {
      const path = resolve(root, ...relativePath.split('/'));
      return existsSync(path) && statSync(path).isFile();
    })
    .sort();
  if (files.length === 0) throw new CliError(`scaffold 目录为空，无法计算摘要: ${directory}`);
  const hash = createHash('sha256');
  for (const relativePath of files) {
    hash.update(`${relativePath}\t${fileDigest(resolve(root, ...relativePath.split('/')))}\n`, 'utf8');
  }
  return hash.digest('hex');
}

function validateTarget(workspaceRoot: string, targetDirectory: string): void {
  const target = resolve(targetDirectory);
  if (target === resolve(workspaceRoot) || target === resolve(workspaceRoot, 'omni-backend') || target === resolve(workspaceRoot, 'omni-frontend')) {
    throw new CliError('输出目录不能覆盖工作区或正式源码根目录');
  }
  if (existsSync(target)) {
    if (lstatSync(target).isSymbolicLink()) throw new CliError(`输出目录不能是符号链接: ${target}`);
    if (!statSync(target).isDirectory() || readdirSync(target).length > 0) {
      throw new CliError('输出目录必须不存在或为空');
    }
  }
  assertRealPathBoundary(target);
  if (basename(target).length < 2) throw new CliError('输出目录名称过短');
}

/** 输出路径的每一级现有祖先都不得是符号链接，避免写入解析到目录外。 */
function assertRealPathBoundary(target: string): void {
  let current = target;
  for (;;) {
    if (!existsSync(current)) {
      const parent = dirname(current);
      if (parent === current) return;
      current = parent;
      continue;
    }
    if (lstatSync(current).isSymbolicLink()) throw new CliError(`输出路径包含符号链接，已拒绝: ${current}`);
    if (current === resolve(target) && realpathExists(current)) {
      const real = realpathSync(current);
      if (!samePath(real, current)) throw new CliError(`输出路径解析后越界: ${current} -> ${real}`);
    }
    const parent = dirname(current);
    if (parent === current) return;
    current = parent;
  }
}

function realpathExists(path: string): boolean {
  try {
    realpathSync(path);
    return true;
  } catch {
    return false;
  }
}

function samePath(left: string, right: string): boolean {
  const normalize = (value: string) => {
    const trimmed = resolve(value).replace(/[\\/]+$/, '');
    return process.platform === 'win32' ? trimmed.toLowerCase() : trimmed;
  };
  return normalize(left) === normalize(right);
}

function removeRelative(root: string, path: string): void {
  const target = resolve(root, ...path.split('/'));
  assertSafeChild(root, target);
  if (existsSync(target)) rmSync(target, { recursive: true, force: true });
}

function removeVerifiedTree(target: string, parent: string): void {
  if (!existsSync(target)) return;
  assertSafeChild(parent, target);
  rmSync(target, { recursive: true, force: true });
}

function assertSafeChild(parent: string, child: string): void {
  const relativePath = relative(resolve(parent), resolve(child));
  if (!relativePath || relativePath === '..' || relativePath.startsWith(`..${sep}`) || basename(resolve(child)).length < 2) {
    throw new CliError(`拒绝操作未验证路径: ${resolve(child)}`);
  }
}

function removeEmptyDirectories(directory: string): void {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory)) {
    const child = resolve(directory, entry);
    if (statSync(child).isDirectory()) removeEmptyDirectories(child);
  }
  if (readdirSync(directory).length === 0) rmdirSync(directory);
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

/** 为证据测试提供稳定锁摘要。 */
export function presetLockSha256(lock: PresetGenerationLock): string {
  return createHash('sha256').update(JSON.stringify(lock)).digest('hex');
}
