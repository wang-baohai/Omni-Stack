import { existsSync, globSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
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

  const compose = readYamlFile(resolve(workspaceRoot, 'docker-compose.yml')) as {
    services?: Record<string, { depends_on?: Record<string, unknown> | string[] }>;
  };
  const composeServiceIds = Object.keys(compose.services ?? {});
  assertExactOwnership('Compose 服务', composeServiceIds, ownedComposeServices);
  for (const [serviceId, service] of Object.entries(compose.services ?? {})) {
    const dependencies = Array.isArray(service.depends_on)
      ? service.depends_on
      : Object.keys(service.depends_on ?? {});
    const dangling = dependencies.filter((dependency) => !composeServiceIds.includes(dependency));
    if (dangling.length > 0) throw new CliError(`Compose 服务 ${serviceId} 存在悬空依赖: ${dangling.join(',')}`);
  }

  const gateway = readFileSync(resolve(workspaceRoot, 'omni-backend', 'omni-gateway', 'src', 'main', 'resources', 'application.yml'), 'utf8');
  const routeIds = [...gateway.matchAll(/^\s+- id:\s+([a-z0-9-]+)\s*$/gm)].map((match) => match[1]!);
  assertExactOwnership('Gateway 路由', routeIds, ownedGatewayRoutes);
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
    selected.add(id);
  };

  requestedIds.forEach(visit);
  return catalog.modules.filter((module: ModuleDefinition) => selected.has(module.id)).map((module) => module.id);
}
