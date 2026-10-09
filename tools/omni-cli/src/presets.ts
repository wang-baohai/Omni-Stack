import { existsSync, readdirSync } from 'node:fs';
import { basename, extname, isAbsolute, resolve } from 'node:path';
import { CliError } from './errors.js';
import { resolveModuleClosure } from './catalog.js';
import { formatSchemaErrors, loadSchema } from './schema.js';
import type { ModuleCatalog, PresetDefinition, ResolvedPreset } from './types.js';
import { readYamlFile } from './yaml.js';

/** 列出所有正式预设 ID。 */
export function listPresetIds(workspaceRoot: string): string[] {
  const directory = resolve(workspaceRoot, 'scaffold', 'presets');
  return readdirSync(directory)
    .filter((file) => ['.yaml', '.yml'].includes(extname(file)))
    .map((file) => basename(file, extname(file)))
    .sort();
}

/** 加载并校验单个预设。 */
export function loadPreset(workspaceRoot: string, presetId: string): PresetDefinition {
  if (!/^[a-z][a-z0-9-]*$/.test(presetId)) {
    throw new CliError(`预设 ID 无效: ${presetId}`);
  }
  const filePath = resolve(workspaceRoot, 'scaffold', 'presets', `${presetId}.yaml`);
  if (!existsSync(filePath)) throw new CliError(`预设不存在: ${presetId}`);
  const value = readYamlFile(filePath);
  const validate = loadSchema(workspaceRoot, 'preset.schema.json');
  if (!validate(value)) {
    throw new CliError(`预设 ${presetId} Schema 校验失败: ${formatSchemaErrors(validate.errors)}`);
  }
  const preset = value as PresetDefinition;
  if (preset.id !== presetId) {
    throw new CliError(`预设文件名与 id 不一致: ${presetId} != ${preset.id}`);
  }
  return preset;
}

/** 加载正式预设 ID 或用户提供的自定义 YAML 文件。 */
export function loadPresetInput(workspaceRoot: string, presetOrFile: string): PresetDefinition {
  const candidate = isAbsolute(presetOrFile) ? resolve(presetOrFile) : resolve(workspaceRoot, presetOrFile);
  if (!existsSync(candidate)) return loadPreset(workspaceRoot, presetOrFile);
  if (!['.yaml', '.yml'].includes(extname(candidate).toLowerCase())) {
    throw new CliError(`自定义预设必须是 YAML 文件: ${presetOrFile}`);
  }
  const value = readYamlFile(candidate);
  const validate = loadSchema(workspaceRoot, 'preset.schema.json');
  if (!validate(value)) {
    throw new CliError(`自定义预设 Schema 校验失败: ${formatSchemaErrors(validate.errors)}`);
  }
  return value as PresetDefinition;
}

/** 解析预设的依赖闭包。 */
export function resolvePreset(
  workspaceRoot: string,
  catalog: ModuleCatalog,
  presetId: string,
): ResolvedPreset {
  const preset = loadPresetInput(workspaceRoot, presetId);
  return {
    preset,
    explicitModules: [...preset.modules],
    resolvedModules: resolveModuleClosure(catalog, preset.modules),
  };
}
