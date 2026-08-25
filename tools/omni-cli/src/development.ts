import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadCatalog, resolveModuleClosure } from './catalog.js';
import { runCommand, platformCommand } from './command.js';
import { loadComposeApplication } from './compose-model.js';
import { requiredComposeEnvironmentNames } from './doctor.js';
import { CliError } from './errors.js';
import { resolvePreset } from './presets.js';

const RUNTIME_FOUNDATION_MODULES = ['gateway', 'mysql', 'redis', 'nacos'];
const LITE_PRESETS = new Set(['core', 'workflow', 'crm']);
const FUNCTION_ENVIRONMENT_KEYS = [
  'OMNI_AUTH_FUNCTION_DEFINITION',
  'OMNI_BASE_FUNCTION_DEFINITION',
  'OMNI_WORKFLOW_FUNCTION_DEFINITION',
  'OMNI_CRM_FUNCTION_DEFINITION',
  'OMNI_SRM_FUNCTION_DEFINITION',
  'OMNI_PROCUREMENT_FUNCTION_DEFINITION',
  'OMNI_ASSET_FUNCTION_DEFINITION',
];

export interface DevelopmentSelection {
  id: string;
  kind: 'preset' | 'module';
  profile: string;
  moduleIds: string[];
  services: string[];
  lite: boolean;
}

/** 由 catalog 依赖闭包计算本地开发所需的唯一服务组合。 */
export function planDevelopmentSelection(
  workspaceRoot: string,
  options: { preset?: string; module?: string },
): DevelopmentSelection {
  if ((options.preset === undefined) === (options.module === undefined)) {
    throw new CliError('必须且只能指定 --preset 或 --module');
  }
  const catalog = loadCatalog(workspaceRoot);
  const compose = loadComposeApplication(workspaceRoot);
  const kind = options.preset ? 'preset' : 'module';
  const id = options.preset ?? options.module!;
  if (options.module === 'frontend') {
    return {
      id,
      kind,
      profile: 'core',
      moduleIds: ['frontend'],
      services: ['omni-frontend'],
      lite: true,
    };
  }
  const moduleRuntimeExtensions = options.module === 'asset' ? ['rocketmq', 'xxl-job'] : [];
  const moduleIds = options.preset
    ? resolvePreset(workspaceRoot, catalog, options.preset).resolvedModules
    : resolveModuleClosure(catalog, [options.module!, ...RUNTIME_FOUNDATION_MODULES, ...moduleRuntimeExtensions]);
  const definitions = catalog.modules.filter((module) => moduleIds.includes(module.id));
  const lite = options.preset ? LITE_PRESETS.has(options.preset) : !['asset'].includes(options.module!);
  const services = unique(definitions.flatMap((module) => module.composeServices))
    .filter((service) => !lite || service !== 'nacos');
  const missing = services.filter((service) => compose.services[service] === undefined);
  if (missing.length > 0) throw new CliError(`开发组合引用未知 Compose 服务: ${missing.join(',')}`);
  return { id, kind, profile: options.preset ?? moduleProfile(options.module!), moduleIds, services, lite };
}

/** 启动一个预设或模块的最小开发组合。 */
export function startDevelopmentSelection(
  workspaceRoot: string,
  selection: DevelopmentSelection,
  build: boolean,
): void {
  if (!isFrontendOnly(selection)) requireEnvironmentFile(workspaceRoot);
  const args = ['compose', 'up', '-d', ...(build ? ['--build'] : []), ...selection.services];
  runCommand('docker', args, workspaceRoot, developmentCommandEnvironment(workspaceRoot, selection));
}

/** 构造 Compose 启动环境；frontend-only 仅为未启动服务补足插值占位。 */
export function developmentCommandEnvironment(
  workspaceRoot: string,
  selection: DevelopmentSelection,
): NodeJS.ProcessEnv {
  const environment = developmentEnvironment(selection);
  if (!isFrontendOnly(selection)) return environment;
  for (const name of requiredComposeEnvironmentNames(workspaceRoot)) {
    environment[name] = process.env[name] ?? 'unused-in-frontend-only-mode';
  }
  return environment;
}

/** 停止当前项目，默认保留命名卷。 */
export function stopDevelopmentEnvironment(
  workspaceRoot: string,
  deleteVolumes: boolean,
  confirmed: boolean,
): void {
  validateVolumeDeletion(deleteVolumes, confirmed);
  runCommand(
    'docker',
    ['compose', '--profile', '*', 'down', ...(deleteVolumes ? ['-v'] : [])],
    workspaceRoot,
    composeReadEnvironment(workspaceRoot),
  );
}

/** 删除数据卷必须由调用方进行二次显式确认。 */
export function validateVolumeDeletion(deleteVolumes: boolean, confirmed: boolean): void {
  if (deleteVolumes && !confirmed) {
    throw new CliError('删除命名卷不可恢复；请同时追加 --confirm-delete-volumes');
  }
}

/** 显示所有 profile 的容器状态。 */
export function showDevelopmentStatus(workspaceRoot: string): void {
  runCommand(
    'docker', ['compose', '--profile', '*', 'ps', '-a'], workspaceRoot, composeReadEnvironment(workspaceRoot),
  );
}

/** 查看指定模块自身服务日志。 */
export function showModuleLogs(workspaceRoot: string, moduleId: string, follow: boolean): void {
  const catalog = loadCatalog(workspaceRoot);
  const module = catalog.modules.find((candidate) => candidate.id === moduleId);
  if (!module) throw new CliError(`未知模块: ${moduleId}`);
  const services = module.composeServices.filter((service) => service.startsWith('omni-'));
  if (services.length === 0) throw new CliError(`模块 ${moduleId} 没有可查看的应用服务`);
  runCommand(
    'docker',
    ['compose', '--profile', '*', 'logs', ...(follow ? ['--follow'] : []), ...services],
    workspaceRoot,
    composeReadEnvironment(workspaceRoot),
  );
}

/** 运行指定后端模块及其 Maven reactor 依赖测试。 */
export function testModuleDependencies(workspaceRoot: string, moduleId: string): void {
  const catalog = loadCatalog(workspaceRoot);
  const module = catalog.modules.find((candidate) => candidate.id === moduleId);
  if (!module) throw new CliError(`未知模块: ${moduleId}`);
  const runnable = module.backendModules.find((artifact) => artifact === `omni-${moduleId}`)
    ?? module.backendModules[0];
  if (!runnable) throw new CliError(`模块 ${moduleId} 没有 Maven 模块`);
  const backend = resolve(workspaceRoot, 'omni-backend');
  const javaHome = process.env.JAVA_HOME;
  const environment = javaHome
    ? {
        JAVA_HOME: javaHome,
        PATH: `${resolve(javaHome, 'bin')}${process.platform === 'win32' ? ';' : ':'}${process.env.PATH ?? ''}`,
      }
    : {};
  runCommand(
    platformCommand('mvnw', true),
    ['-pl', runnable, '-am', 'test'],
    backend,
    environment,
  );
}

/** 构造 lite/full 模式显式功能开关，不输出任何 Secret。 */
export function developmentEnvironment(selection: DevelopmentSelection): NodeJS.ProcessEnv {
  if (!selection.lite) {
    return {
      OMNI_RUNTIME_PROFILE: 'default',
      XXL_JOB_ENABLED: process.env.XXL_JOB_ENABLED ?? 'true',
      OMNI_MQ_RELAY_ENABLED: process.env.OMNI_MQ_RELAY_ENABLED ?? 'true',
      OMNI_MQ_AUTODETECT: process.env.OMNI_MQ_AUTODETECT ?? 'true',
    };
  }
  return {
    OMNI_RUNTIME_PROFILE: 'lite',
    XXL_JOB_ENABLED: 'false',
    OMNI_MQ_RELAY_ENABLED: 'false',
    OMNI_MQ_AUTODETECT: 'false',
    NACOS_CLIENT_ENABLED: 'false',
    ...Object.fromEntries(FUNCTION_ENVIRONMENT_KEYS.map((key) => [key, ''])),
  };
}

function requireEnvironmentFile(workspaceRoot: string): void {
  if (!existsSync(resolve(workspaceRoot, '.env'))) {
    throw new CliError('未找到 .env；请先复制 .env.example 并填写本地 Secret');
  }
}

function composeReadEnvironment(workspaceRoot: string): NodeJS.ProcessEnv {
  return Object.fromEntries(requiredComposeEnvironmentNames(workspaceRoot)
    .map((name) => [name, process.env[name] ?? 'unused-for-compose-read-operation']));
}

function isFrontendOnly(selection: DevelopmentSelection): boolean {
  return selection.services.length === 1 && selection.services[0] === 'omni-frontend';
}

function moduleProfile(moduleId: string): string {
  if (['platform', 'auth', 'base', 'gateway'].includes(moduleId)) return 'core';
  if (moduleId === 'srm' || moduleId === 'procurement' || moduleId === 'asset') return 'supply-chain';
  return moduleId;
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}
