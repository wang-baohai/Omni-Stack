import { existsSync, globSync, readFileSync } from 'node:fs';
import { basename, dirname, resolve } from 'node:path';
import { composeDependencies, loadComposeApplication } from './compose-model.js';
import { CliError } from './errors.js';
import { formatSchemaErrors, loadSchema } from './schema.js';
import type { ModuleCatalog, ModuleDefinition } from './types.js';
import { readYamlFile } from './yaml.js';

/** 加载并完整校验模块清单。 */
export function loadCatalog(workspaceRoot: string): ModuleCatalog {
  const catalogPath = resolve(workspaceRoot, 'scaffold', 'catalog', 'modules.yaml');
  const value = readYamlFile(catalogPath);
  const validate = loadSchema(workspaceRoot, 'module.schema.json');
  if (!validate(value)) {
    throw new CliError(`模块清单 Schema 校验失败: ${formatSchemaErrors(validate.errors)}`);
  }
  const catalog = value as ModuleCatalog;
  validateCatalogGraph(catalog);
  return catalog;
}

/** 校验唯一性、拓扑顺序和循环依赖。 */
export function validateCatalogGraph(catalog: ModuleCatalog): void {
  const known = new Set<string>();
  for (const module of catalog.modules) {
    if (known.has(module.id)) {
      throw new CliError(`模块 ID 重复: ${module.id}`);
    }
    for (const dependency of module.dependencies) {
      if (!known.has(dependency)) {
        throw new CliError(`模块 ${module.id} 的依赖尚未声明: ${dependency}`);
      }
    }
    known.add(module.id);
  }
  for (const module of catalog.modules) {
    for (const reference of [...module.optionalModules, ...module.conflicts]) {
      if (!known.has(reference)) throw new CliError(`模块 ${module.id} 引用了未知模块: ${reference}`);
      if (reference === module.id) throw new CliError(`模块 ${module.id} 不能引用自身`);
    }
    if (module.dependencies.some((dependency) => module.conflicts.includes(dependency))) {
      throw new CliError(`模块 ${module.id} 不能同时依赖并冲突同一模块`);
    }
    if (module.resourceHints.recommendedMemoryMb < module.resourceHints.minimumMemoryMb) {
      throw new CliError(`模块 ${module.id} 的推荐内存不能低于最小内存`);
    }
  }
}

/** 校验 catalog 声明的源码、Compose、路由、数据库和文档落点。 */
export function validateCatalogResources(workspaceRoot: string, catalog: ModuleCatalog): void {
  const ownedBackendModules = new Set<string>();
  const ownedComposeServices = new Set<string>();
  const ownedGatewayRoutes = new Set<string>();
  const declaredSeedSources = new Set<string>();
  const declaredSeedAssertions = new Set<string>();

  const seedManifest = readYamlFile(resolve(workspaceRoot, 'database', 'seed', 'manifest.yaml')) as {
    sources?: Array<{ id?: string }>;
    assertions?: Array<{ id?: string }>;
  };
  for (const source of seedManifest.sources ?? []) if (source.id) declaredSeedSources.add(source.id);
  for (const assertion of seedManifest.assertions ?? []) if (assertion.id) declaredSeedAssertions.add(assertion.id);

  for (const module of catalog.modules) {
    for (const backendModule of module.backendModules) {
      requirePath(workspaceRoot, `omni-backend/${backendModule}`, module.id);
      if (!ownedBackendModules.add(backendModule)) throw new CliError(`后端模块被重复归属: ${backendModule}`);
    }
    for (const pattern of [
      ...module.frontend.viewGlobs,
      ...module.frontend.componentGlobs,
      ...module.frontend.apiGlobs,
      ...module.frontend.i18nGlobs,
    ]) requireGlob(workspaceRoot, pattern, module.id);
    for (const changelog of module.database.changelogs) requirePath(workspaceRoot, changelog, module.id);
    for (const document of module.docs) requirePath(workspaceRoot, document, module.id);
    for (const sourceId of module.database.seedSourceIds) {
      if (!declaredSeedSources.has(sourceId)) throw new CliError(`模块 ${module.id} 引用了未知 seed source: ${sourceId}`);
    }
    for (const assertionId of module.provisioningSeedIds) {
      if (!declaredSeedAssertions.has(assertionId)) throw new CliError(`模块 ${module.id} 引用了未知 seed assertion: ${assertionId}`);
    }
    for (const service of module.composeServices) {
      if (!ownedComposeServices.add(service)) throw new CliError(`Compose 服务被重复归属: ${service}`);
    }
    for (const route of module.gatewayRoutes) {
      if (!ownedGatewayRoutes.add(route)) throw new CliError(`Gateway 路由被重复归属: ${route}`);
    }
  }

  const parentPom = readFileSync(resolve(workspaceRoot, 'omni-backend', 'pom.xml'), 'utf8');
  const reactorModules = [...parentPom.matchAll(/<module>([^<]+)<\/module>/g)].map((match) => match[1]!);
  assertExactOwnership('Maven reactor 模块', reactorModules, ownedBackendModules);

  const compose = loadComposeApplication(workspaceRoot);
  const composeServiceIds = Object.keys(compose.services);
  assertExactOwnership('Compose 服务', composeServiceIds, ownedComposeServices);
  for (const [serviceId, service] of Object.entries(compose.services)) {
    const dependencies = composeDependencies(service);
    const dangling = dependencies.filter((dependency) => !composeServiceIds.includes(dependency));
    if (dangling.length > 0) throw new CliError(`Compose 服务 ${serviceId} 存在悬空依赖: ${dangling.join(',')}`);
  }

  const gateway = readFileSync(resolve(workspaceRoot, 'omni-backend', 'omni-gateway', 'src', 'main', 'resources', 'application.yml'), 'utf8');
  const routeIds = [...gateway.matchAll(/^\s+- id:\s+([a-z0-9-]+)\s*$/gm)].map((match) => match[1]!);
  assertExactOwnership('Gateway 路由', routeIds, ownedGatewayRoutes);

  validateI18nOwnership(workspaceRoot, catalog);
  validateChangelogOwnership(workspaceRoot, catalog);
  if (catalog.modules.some((module) => module.id === 'rocketmq')) validateMqDeclarations(workspaceRoot, catalog);
}

/** locale 文件里的顶层 i18n 键必须由 catalog 归属，且声明的前缀必须真实存在。 */
function validateI18nOwnership(workspaceRoot: string, catalog: ModuleCatalog): void {
  const owners = new Map<string, string[]>();
  for (const module of catalog.modules) {
    for (const prefix of module.frontend.i18nPrefixes) owners.set(prefix, [...(owners.get(prefix) ?? []), module.id]);
  }
  const localeFiles = globSync('omni-frontend/src/locales/*.ts', { cwd: workspaceRoot }).map((path) => path.replaceAll('\\', '/'));
  if (localeFiles.length === 0) throw new CliError('未发现前端 locale 文件，无法校验 i18n 归属');
  const zhKeys = new Set(topLevelI18nKeys(readFileSync(resolve(workspaceRoot, 'omni-frontend/src/locales/zh-CN.ts'), 'utf8')));
  for (const [prefix, moduleIds] of owners) {
    if (!zhKeys.has(prefix)) throw new CliError(`i18n 前缀幽灵声明: ${moduleIds.join(',')} -> ${prefix}`);
  }
  for (const relativePath of localeFiles) {
    for (const key of topLevelI18nKeys(readFileSync(resolve(workspaceRoot, ...relativePath.split('/')), 'utf8'))) {
      if (!owners.has(key)) throw new CliError(`i18n 顶层键未被 catalog 管理: ${basename(relativePath)}/${key}`);
    }
  }
}

/** 提取 locale 顶层键（与生成器裁剪使用的同一识别规则）。 */
export function topLevelI18nKeys(content: string): string[] {
  return [...content.matchAll(/^  ([A-Za-z][A-Za-z0-9]*): \{$/gm)].map((match) => match[1]!);
}

/** 反向扫描数据库 changelog：每个正式文件必须被某个模块直接或经 include 传递管理。 */
function validateChangelogOwnership(workspaceRoot: string, catalog: ModuleCatalog): void {
  const managed = new Set<string>();
  for (const module of catalog.modules) {
    for (const path of expandChangelogClosure(workspaceRoot, module.database.changelogs)) managed.add(path);
  }
  for (const relativePath of globSync('database/changelog/**/*.yaml', { cwd: workspaceRoot })) {
    const normalized = relativePath.replaceAll('\\', '/');
    if (!managed.has(normalized)) throw new CliError(`数据库 changelog 未被 catalog 管理: ${normalized}`);
  }
}

/** 沿 Liquibase include 链展开模块声明的 changelog 集合（生成器剪枝共用同一规则）。 */
export function expandChangelogClosure(workspaceRoot: string, declared: string[]): Set<string> {
  const visited = new Set<string>();
  const queue = [...declared];
  while (queue.length > 0) {
    const current = queue.shift()!;
    if (visited.has(current)) continue;
    const absolute = resolve(workspaceRoot, ...current.split('/'));
    if (!existsSync(absolute)) continue;
    visited.add(current);
    const text = readFileSync(absolute, 'utf8');
    for (const match of text.matchAll(/^\s*(?:path|file):\s*["']?([^"'\n#]+?)["']?\s*$/gm)) {
      const reference = match[1]!.trim();
      if (!reference.endsWith('.yaml') && !reference.endsWith('.yml')) continue;
      for (const candidate of changelogCandidates(current, reference)) {
        if (existsSync(resolve(workspaceRoot, ...candidate.split('/')))) queue.push(candidate);
      }
    }
  }
  return visited;
}

/** include 引用既可能是仓库相对路径，也可能是相对父 changelog 的路径。 */
function changelogCandidates(includingFile: string, reference: string): string[] {
  const normalizedReference = reference.replaceAll('\\', '/').replace(/^\.\//, '');
  const parentDirectory = dirname(includingFile);
  const relativeToParent = parentDirectory === '.' ? normalizedReference : `${parentDirectory}/${normalizedReference}`;
  const collapse = (path: string) => {
    const segments: string[] = [];
    for (const segment of path.split('/')) {
      if (segment === '.' || segment === '') continue;
      if (segment === '..') segments.pop();
      else segments.push(segment);
    }
    return segments.join('/');
  };
  return [normalizedReference, collapse(relativeToParent)];
}

/** MQ 声明必须与后端 application.yml 的 Stream bindings 双向一致。 */
function validateMqDeclarations(workspaceRoot: string, catalog: ModuleCatalog): void {
  for (const module of catalog.modules) {
    const actual = { producers: new Set<string>(), consumers: new Set<string>() };
    for (const backendModule of module.backendModules) {
      const path = resolve(workspaceRoot, 'omni-backend', backendModule, 'src/main/resources/application.yml');
      if (!existsSync(path)) continue;
      const bindings = (readYamlFile(path) as {
        spring?: { cloud?: { stream?: { bindings?: Record<string, { destination?: string }> } } };
      })?.spring?.cloud?.stream?.bindings ?? {};
      for (const [bindingName, binding] of Object.entries(bindings)) {
        if (!binding?.destination) continue;
        if (/-out-\d+$/.test(bindingName)) actual.producers.add(binding.destination);
        else if (/-in-\d+$/.test(bindingName)) actual.consumers.add(binding.destination);
      }
    }
    for (const destination of module.mq.producers) {
      if (!actual.producers.has(destination)) throw new CliError(`模块 ${module.id} 声明的 MQ producer 无对应 binding: ${destination}`);
    }
    for (const destination of module.mq.consumers) {
      if (!actual.consumers.has(destination)) throw new CliError(`模块 ${module.id} 声明的 MQ consumer 无对应 binding: ${destination}`);
    }
    for (const destination of actual.producers) {
      if (!module.mq.producers.includes(destination)) throw new CliError(`模块 ${module.id} 存在未声明的 MQ producer: ${destination}`);
    }
    for (const destination of actual.consumers) {
      if (!module.mq.consumers.includes(destination)) throw new CliError(`模块 ${module.id} 存在未声明的 MQ consumer: ${destination}`);
    }
  }
}

/** 校验选中模块的 Compose 运行时依赖也全部落在闭包内，缺失时在写文件前拒绝。 */
export function validateClosureRuntimeDependencies(
  workspaceRoot: string,
  catalog: ModuleCatalog,
  selectedIds: string[],
): void {
  const selected = new Set(selectedIds);
  const ownerOfService = new Map(catalog.modules.flatMap((module) => module.composeServices.map((service) => [service, module.id] as const)));
  const compose = loadComposeApplication(workspaceRoot);
  const missing = new Map<string, Set<string>>();
  for (const [serviceId, service] of Object.entries(compose.services)) {
    const owner = ownerOfService.get(serviceId);
    if (owner === undefined || !selected.has(owner)) continue;
    for (const dependency of composeDependencies(service)) {
      const dependencyOwner = ownerOfService.get(dependency);
      if (dependencyOwner === undefined || dependencyOwner === owner || selected.has(dependencyOwner)) continue;
      if (!missing.has(owner)) missing.set(owner, new Set());
      missing.get(owner)!.add(`${dependencyOwner}(${dependency})`);
    }
  }
  if (missing.size > 0) {
    const detail = [...missing.entries()]
      .map(([moduleId, requirements]) => `${moduleId} 缺少 ${[...requirements].sort().join(',')}`)
      .join('; ');
    throw new CliError(`预设模块缺少 Compose 运行时依赖，已在写文件前拒绝: ${detail}`);
  }
}

function requirePath(workspaceRoot: string, path: string, moduleId: string): void {
  if (!existsSync(resolve(workspaceRoot, ...path.split('/')))) {
    throw new CliError(`模块 ${moduleId} 声明的路径不存在: ${path}`);
  }
}

function requireGlob(workspaceRoot: string, pattern: string, moduleId: string): void {
  if (globSync(pattern, { cwd: workspaceRoot }).length === 0) {
    throw new CliError(`模块 ${moduleId} 声明的 glob 无匹配: ${pattern}`);
  }
}

function assertExactOwnership(label: string, actualValues: string[], ownedValues: Set<string>): void {
  const unmanaged = actualValues.filter((value) => !ownedValues.has(value));
  const missing = [...ownedValues].filter((value) => !actualValues.includes(value));
  if (unmanaged.length > 0 || missing.length > 0) {
    throw new CliError(`${label}归属不完整: unmanaged=${unmanaged.join(',') || '-'}, missing=${missing.join(',') || '-'}`);
  }
}

/** 返回按 catalog 顺序排列的依赖闭包。 */
export function resolveModuleClosure(catalog: ModuleCatalog, requestedIds: string[]): string[] {
  const byId = new Map(catalog.modules.map((module) => [module.id, module]));
  const selected = new Set<string>();
  const visiting = new Set<string>();

  const visit = (id: string): void => {
    const module = byId.get(id);
    if (module === undefined) {
      throw new CliError(`未知模块: ${id}`);
    }
    if (selected.has(id)) return;
    if (visiting.has(id)) throw new CliError(`模块依赖存在循环: ${id}`);
    visiting.add(id);
    module.dependencies.forEach(visit);
    visiting.delete(id);
    assertNoConflict(module, byId, selected);
    selected.add(id);
  };

  requestedIds.forEach(visit);
  return catalog.modules.filter((module: ModuleDefinition) => selected.has(module.id)).map((module) => module.id);
}

/** 闭包内任意方向命中的冲突声明都必须拒绝。 */
function assertNoConflict(module: ModuleDefinition, byId: Map<string, ModuleDefinition>, selected: Set<string>): void {
  for (const conflictId of module.conflicts) {
    if (!selected.has(conflictId)) continue;
    throw new CliError(`模块组合存在冲突: ${module.id} 与 ${conflictId}`);
  }
  for (const selectedId of selected) {
    if (byId.get(selectedId)?.conflicts.includes(module.id) === true) {
      throw new CliError(`模块组合存在冲突: ${module.id} 与 ${selectedId}`);
    }
  }
}
