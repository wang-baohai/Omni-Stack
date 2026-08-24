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
