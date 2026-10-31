import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadCatalog, resolveModuleClosure } from './catalog.js';
import { captureCommand, platformCommand, runCommand } from './command.js';
import { loadComposeApplication } from './compose-model.js';
import { implicitProjectName, requiredComposeEnvironmentNames, volumeOwnerProject } from './doctor.js';
import { CliError } from './errors.js';
import { resolvePreset } from './presets.js';

const RUNTIME_FOUNDATION_MODULES = ['gateway', 'mysql', 'redis', 'nacos'];
/** 预设的隐式 lite 归属；显式 `--mode` 优先于此表。 */
const LITE_PRESETS = new Set(['core', 'workflow', 'crm']);
/** 需要实时消费者或调度器的服务，lite 模式没有这些客户端的显式关闭证据路径。 */
const HEAVY_INFRASTRUCTURE_SERVICES = ['rocketmq-namesrv', 'rocketmq-broker', 'xxl-job-admin'];
/** 需要实时消费者（MQ/调度）的业务服务：catalog 里没有 Outbox-only 证据声明，lite 一律拒绝。 */
const LITE_UNSAFE_CONSUMER_SERVICES = ['omni-srm', 'omni-procurement', 'omni-asset'];
const OBSERVABILITY_SERVICES = [
  'otel-collector', 'prometheus', 'pushgateway', 'node-exporter', 'cadvisor',
  'alertmanager', 'alertmanager-ui', 'tempo', 'loki', 'alloy', 'grafana',
];
const FUNCTION_ENVIRONMENT_KEYS = [
  'OMNI_AUTH_FUNCTION_DEFINITION',
  'OMNI_BASE_FUNCTION_DEFINITION',
  'OMNI_WORKFLOW_FUNCTION_DEFINITION',
  'OMNI_CRM_FUNCTION_DEFINITION',
  'OMNI_SRM_FUNCTION_DEFINITION',
  'OMNI_PROCUREMENT_FUNCTION_DEFINITION',
  'OMNI_ASSET_FUNCTION_DEFINITION',
];
/** 参与 Compose 插值的仓库内文件；漏掉任一个都会让隔离参数静默失效。 */
const COMPOSE_INTERPOLATION_FILES = [
  'compose.yaml', 'compose.infra.yaml', 'compose.apps.yaml', 'compose.observability.yaml',
];
/** 允许经 CLI 覆盖的非秘密变量名：宿主端口与命名卷名。 */
const OVERRIDABLE_NAME_PATTERN = /^(?:OMNI_[A-Z0-9_]*_HOST_PORT|[A-Z0-9_]*_VOLUME_NAME)$/;
const INTERPOLATION_PATTERN = /\$\{([A-Z][A-Z0-9_]*)/g;
const PROJECT_NAME_PATTERN = /^[a-z0-9][a-z0-9_-]{1,62}$/;

export interface DevelopmentSelection {
  id: string;
  kind: 'preset' | 'module';
  profile: string;
  moduleIds: string[];
  services: string[];
  lite: boolean;
}

// exactOptionalPropertyTypes 下调用方会直接传 commander 的 undefined 选项，故显式允许 undefined。
export interface DevelopmentOptions {
  preset?: string | undefined;
  module?: string | undefined;
  /** 显式模式开关，优先于预设/模块的隐式推断。 */
  mode?: 'lite' | 'full' | undefined;
}

export interface DevelopmentRunOptions {
  /** Compose project 名；卷/网络/容器都按它命名，缺省时由 Compose 依目录名推导。 */
  projectName?: string | undefined;
  /** `NAME=VALUE` 形式的白名单覆盖（宿主端口、卷名），值永不进入日志。 */
  overrides?: string[] | undefined;
}

/** 由 catalog 依赖闭包计算本地开发所需的唯一服务组合。 */
export function planDevelopmentSelection(
  workspaceRoot: string,
  options: DevelopmentOptions,
): DevelopmentSelection {
  if ((options.preset === undefined) === (options.module === undefined)) {
    throw new CliError('必须且只能指定 --preset 或 --module');
  }
  if (options.mode !== undefined && options.mode !== 'lite' && options.mode !== 'full') {
    throw new CliError(`未知模式: ${String(options.mode)}（只支持 lite 或 full）`);
  }
  const catalog = loadCatalog(workspaceRoot);
  const compose = loadComposeApplication(workspaceRoot);
  const kind = options.preset ? 'preset' : 'module';
  const id = options.preset ?? options.module!;
  if (options.module === 'frontend') {
    if (options.mode === 'full') throw new CliError('frontend-only 组合没有 full 模式；请改用 --module base');
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
  const closureServices = unique(definitions.flatMap((module) => module.composeServices));
  const liteSafeClosure = !LITE_UNSAFE_CONSUMER_SERVICES
    .some((service) => closureServices.includes(service));
  const lite = options.mode !== undefined
    ? options.mode === 'lite'
    // 未显式指定模式时沿用隐式归属，但闭包含实时消费者的必须落到 full
    : (options.preset
      ? LITE_PRESETS.has(options.preset)
      : !['asset'].includes(options.module!)) && liteSafeClosure;
  const services = lite ? closureServices.filter((service) => service !== 'nacos') : closureServices;
  const missing = services.filter((service) => compose.services[service] === undefined);
  if (missing.length > 0) throw new CliError(`开发组合引用未知 Compose 服务: ${missing.join(',')}`);
  if (lite) assertLiteSelectable(closureServices, id, options.mode === 'lite');
  return { id, kind, profile: options.preset ?? moduleProfile(options.module!), moduleIds, services, lite };
}

/**
 * lite 只省略 Nacos；闭包里只要含 SRM/Procurement/Asset，降级就没有 Outbox-only 证据：
 * 若连带省略 MQ/调度服务则实时链路断，若保留则 "lite" 名不副实。两种情形都必须 fail-closed，
 * 不能靠吞异常继续启动。
 */
function assertLiteSelectable(services: string[], id: string, explicit: boolean): void {
  const consumers = LITE_UNSAFE_CONSUMER_SERVICES.filter((service) => services.includes(service));
  if (consumers.length === 0) return;
  const kept = HEAVY_INFRASTRUCTURE_SERVICES.filter((service) => services.includes(service));
  throw new CliError(
    `${id} 的组合包含实时消费者服务 ${consumers.join(',')}，lite 不支持：`
    + (kept.length > 0
      ? `该闭包同时依赖 ${kept.join(',')}`
      : '该闭包未声明所需的 rocketmq/xxl-job 基础设施')
    + '；catalog 中尚无 Outbox-only 边界证据，'
    + (explicit ? '请改用 --mode full。' : '隐式判定已回退到 full，显式 --mode lite 会被拒绝。'),
  );
}

/** 启动一个预设或模块的最小开发组合。 */
export function startDevelopmentSelection(
  workspaceRoot: string,
  selection: DevelopmentSelection,
  build = false,
  observability = false,
  run: DevelopmentRunOptions = {},
): void {
  const projectName = normalizeProjectName(run.projectName);
  if (!isFrontendOnly(selection)) requireEnvironmentFile(workspaceRoot);
  const environment = developmentCommandEnvironment(workspaceRoot, selection, observability, run);
  // WP-011 S18：镜像标签按 project 作域，防止不同开发栈互相覆盖 omni-*:latest 并被静默复用。
  if (projectName && environment.OMNI_IMAGE_NAMESPACE === undefined) environment.OMNI_IMAGE_NAMESPACE = projectName.toLowerCase();
  warnOnForeignVolumes(workspaceRoot, projectName, environment);
  const observationServices = observability ? OBSERVABILITY_SERVICES : [];
  const args = [
    'compose', ...projectArgs(projectName), 'up', '-d',
    ...(build ? ['--build'] : []),
    ...selection.services, ...observationServices,
  ];
  runCommand('docker', args, workspaceRoot, environment);
}

/** 构造 Compose 启动环境；frontend-only 仅为未启动服务补足插值占位。 */
export function developmentCommandEnvironment(
  workspaceRoot: string,
  selection: DevelopmentSelection,
  observability = false,
  run: DevelopmentRunOptions = {},
): NodeJS.ProcessEnv {
  const overrides = parseOverrideAssignments(run.overrides ?? [], workspaceRoot);
  const environment: NodeJS.ProcessEnv = {
    ...developmentEnvironment(selection),
    ...(observability ? observabilityEnvironment() : {}),
    // 前端源码目录未裁剪时，也必须与 catalog 已解析的部署闭包一致。
    ...(!isFrontendOnly(selection) ? { OMNI_FRONTEND_MODULES: selection.moduleIds.join(',') } : {}),
    ...overrides,
  };
  if (!isFrontendOnly(selection)) return environment;
  for (const name of requiredComposeEnvironmentNames(workspaceRoot)) {
    environment[name] = environment[name] ?? process.env[name] ?? 'unused-in-frontend-only-mode';
  }
  return environment;
}

/** 启用本地观测栈所需的非 Secret 开关。 */
export function observabilityEnvironment(): NodeJS.ProcessEnv {
  return {
    OTLP_EXPORT_ENABLED: 'true',
    OTLP_TRACES_ENDPOINT: 'http://otel-collector:4318/v1/traces',
    TRACING_SAMPLING_PROBABILITY: process.env.TRACING_SAMPLING_PROBABILITY ?? '1.0',
    STRUCTURED_LOGGING_FORMAT: 'ecs',
    MIGRATION_PUSHGATEWAY_ENABLED: 'true',
    MIGRATION_PUSHGATEWAY_ADDRESS: 'pushgateway:9091',
  };
}

/**
 * 停止当前项目，默认保留命名卷。
 *
 * 删除卷必须同时满足：显式 `--volumes` + 显式 `--confirm-delete-volumes`
 * + 显式 `--project-name` + 每个已解析卷的 owner 都属于该 project。
 */
export function stopDevelopmentEnvironment(
  workspaceRoot: string,
  deleteVolumes = false,
  confirmed = false,
  projectName?: string,
  overrides: string[] = [],
): void {
  const project = normalizeProjectName(projectName);
  validateVolumeDeletion(deleteVolumes, confirmed, project);
  const environment = composeReadEnvironment(workspaceRoot, parseOverrideAssignments(overrides, workspaceRoot));
  if (deleteVolumes) assertVolumesOwnedByProject(workspaceRoot, project!, environment);
  runCommand(
    'docker',
    composeDownArguments(deleteVolumes, project),
    workspaceRoot,
    environment,
  );
}

/**
 * 构造 `dev down` 的参数。默认不带 `-v`，也永不使用 `--remove-orphans` 或
 * 任何 prune 分支；只有显式删卷才会附加 `-v`。
 */
export function composeDownArguments(deleteVolumes: boolean, projectName?: string): string[] {
  return [
    'compose', ...projectArgs(projectName), '--profile', '*', 'down',
    ...(deleteVolumes ? ['-v'] : []),
  ];
}

/**
 * 卷删除校验：只允许显式 flag 组合，且删卷必须绑定显式 project，
 * 以免误删其他 project 的历史卷。
 */
export function validateVolumeDeletion(
  deleteVolumes: boolean,
  confirmed: boolean,
  projectName?: string,
): void {
  if (deleteVolumes && !confirmed) {
    throw new CliError('删除命名卷不可恢复；请同时追加 --confirm-delete-volumes');
  }
  if (deleteVolumes && !projectName) {
    throw new CliError(
      '删除命名卷必须显式指定 --project-name，以免波及其他 Compose project 的历史卷；'
      + '不带 --volumes 的 dev down 默认保留全部卷',
    );
  }
}

/** 读取 Compose 解析后的卷名，逐个核对 owner label 是否属于目标 project。 */
export function assertVolumesOwnedByProject(
  workspaceRoot: string,
  projectName: string,
  environment: NodeJS.ProcessEnv = {},
): void {
  for (const volume of resolvedComposeVolumeNames(workspaceRoot, environment)) {
    const owner = volumeOwnerProject(workspaceRoot, volume, environment);
    if (owner === undefined) continue; // 卷不存在，无数据可删
    if (owner !== projectName) {
      throw new CliError(
        `拒绝删除卷 ${volume}：它属于 project "${owner}"，不是 "${projectName}"。`
        + '请修正 --project-name，或先解除 Compose 中的固定卷名声明',
      );
    }
  }
}

/** Compose 解析后的命名卷清单（只读 `config --volumes`）。 */
export function resolvedComposeVolumeNames(
  workspaceRoot: string,
  environment: NodeJS.ProcessEnv = {},
): string[] {
  const result = captureCommand(
    'docker', ['compose', '--profile', '*', 'config', '--volumes'], workspaceRoot, environment,
  );
  if (result.status !== 0) throw new CliError(`无法解析 Compose 卷清单: ${firstFailureLine(result)}`);
  return result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
}

/** 显示所有 profile 的容器状态。 */
export function showDevelopmentStatus(workspaceRoot: string, projectName?: string): void {
  runCommand(
    'docker',
    ['compose', ...projectArgs(normalizeProjectName(projectName)), '--profile', '*', 'ps', '-a'],
    workspaceRoot,
    composeReadEnvironment(workspaceRoot),
  );
}

/** 查看指定模块自身服务日志。 */
export function showModuleLogs(
  workspaceRoot: string,
  moduleId: string,
  follow = false,
  tail?: number,
  projectName?: string,
): void {
  const catalog = loadCatalog(workspaceRoot);
  const module = catalog.modules.find((candidate) => candidate.id === moduleId);
  if (!module) throw new CliError(`未知模块: ${moduleId}`);
  if (tail !== undefined && (!Number.isInteger(tail) || tail < 0)) throw new CliError('--tail 必须是非负整数');
  const services = module.composeServices.filter((service) => service.startsWith('omni-'));
  if (services.length === 0) throw new CliError(`模块 ${moduleId} 没有可查看的应用服务`);
  runCommand(
    'docker',
    [
      'compose', ...projectArgs(normalizeProjectName(projectName)), '--profile', '*', 'logs',
      ...(follow ? ['--follow'] : []),
      ...(tail === undefined ? [] : ['--tail', String(tail)]),
      ...services,
    ],
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
  runCommand(platformCommand('mvnw', true), ['-pl', runnable, '-am', 'test'], backend, environment);
}

/**
 * 构造 lite/full 模式功能开关，不输出任何 Secret。
 *
 * lite 显式关闭 Nacos/XXL/MQ 客户端；full 不合成任何开关值，让 `.env` 与
 * Compose 默认值保持唯一权威，避免 CLI 静默覆盖本地配置。
 */
export function developmentEnvironment(selection: DevelopmentSelection): NodeJS.ProcessEnv {
  if (!selection.lite) return {};
  return {
    OMNI_RUNTIME_PROFILE: 'lite',
    XXL_JOB_ENABLED: 'false',
    OMNI_MQ_RELAY_ENABLED: 'false',
    OMNI_MQ_AUTODETECT: 'false',
    NACOS_CLIENT_ENABLED: 'false',
    ...Object.fromEntries(FUNCTION_ENVIRONMENT_KEYS.map((key) => [key, ''])),
  };
}

/** 校验并解析 `NAME=VALUE` 覆盖，只接受宿主端口与卷名白名单。 */
export function parseOverrideAssignments(assignments: string[], workspaceRoot: string): NodeJS.ProcessEnv {
  const declared = new Set(declaredOverrideNames(workspaceRoot));
  const environment: NodeJS.ProcessEnv = {};
  for (const assignment of assignments) {
    const separator = assignment.indexOf('=');
    if (separator <= 0) throw new CliError(`覆盖参数必须是 NAME=VALUE 形式: ${assignment.split('=')[0]}`);
    const name = assignment.slice(0, separator);
    const value = assignment.slice(separator + 1);
    if (!OVERRIDABLE_NAME_PATTERN.test(name)) {
      throw new CliError(`不允许覆盖变量 ${name}；只有宿主端口与命名卷名可以经 CLI 传入`);
    }
    if (!declared.has(name)) throw new CliError(`Compose 未声明可覆盖变量 ${name}`);
    if (/[\r\n\0]/.test(value)) throw new CliError(`变量 ${name} 的值包含非法字符`);
    if (name.endsWith('_HOST_PORT') && !/^(?:0|[1-9][0-9]{0,4})$/.test(value)) {
      throw new CliError(`变量 ${name} 必须是 0-65535 的端口号`);
    }
    if (name.endsWith('_HOST_PORT') && Number(value) > 65535) {
      throw new CliError(`变量 ${name} 必须是 0-65535 的端口号`);
    }
    if (name.endsWith('_VOLUME_NAME') && !/^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$/.test(value)) {
      throw new CliError(`变量 ${name} 只允许字母、数字、点、下划线或连字符`);
    }
    environment[name] = value;
  }
  return environment;
}

/** 列出 Compose 分片中声明的宿主端口/卷名覆盖点（只看名字，不看值）。 */
export function declaredOverrideNames(workspaceRoot: string): string[] {
  const names = new Set<string>();
  for (const relativePath of COMPOSE_INTERPOLATION_FILES) {
    const path = resolve(workspaceRoot, relativePath);
    if (!existsSync(path)) continue;
    for (const match of readFileSync(path, 'utf8').matchAll(INTERPOLATION_PATTERN)) {
      if (OVERRIDABLE_NAME_PATTERN.test(match[1]!)) names.add(match[1]!);
    }
  }
  return [...names].sort();
}

/** 规范化 project 名；缺省返回 undefined，让 Compose 继续按目录名推导。 */
export function normalizeProjectName(value?: string): string | undefined {
  if (value === undefined || value === '') return undefined;
  if (!PROJECT_NAME_PATTERN.test(value)) {
    // 不回显未校验原值，避免换行/控制字符注入日志与终端
    throw new CliError(
      '非法的 --project-name：必须以小写字母或数字开头，长度 2-63，仅允许 [a-z0-9_-]（原值已隐去）',
    );
  }
  return value;
}

/** 生成 Compose 全局 project 参数。 */
export function projectArgs(projectName?: string): string[] {
  return projectName === undefined ? [] : ['--project-name', projectName];
}

function requireEnvironmentFile(workspaceRoot: string): void {
  if (!existsSync(resolve(workspaceRoot, '.env'))) {
    throw new CliError('未找到 .env；请先复制 .env.example 并填写本地 Secret');
  }
}

function composeReadEnvironment(workspaceRoot: string, extra: NodeJS.ProcessEnv = {}): NodeJS.ProcessEnv {
  const placeholders = Object.fromEntries(requiredComposeEnvironmentNames(workspaceRoot)
    .map((name) => [name, process.env[name] ?? 'unused-for-compose-read-operation']));
  return { ...placeholders, ...extra };
}

/**
 * 只读预警：Compose 使用固定卷名时多个 project 会共享同一数据卷。
 * 只打印卷名与 owner，绝不打印任何变量值。
 */
function warnOnForeignVolumes(
  workspaceRoot: string,
  projectName: string | undefined,
  environment: NodeJS.ProcessEnv,
): void {
  let volumes: string[];
  try {
    volumes = resolvedComposeVolumeNames(workspaceRoot, environment);
  } catch {
    return; // 卷解析失败由后续 up 命令给出真实错误，不在此处放大
  }
  const ownerProject = projectName ?? implicitProjectName(workspaceRoot);
  for (const volume of volumes) {
    const owner = volumeOwnerProject(workspaceRoot, volume, environment);
    if (owner !== undefined && owner !== ownerProject) {
      console.log(`WARNING shared volume: ${volume} 由 project "${owner}" 创建，本次 project 为 "${ownerProject}"`);
    }
  }
}

function firstFailureLine(result: { stderr: string; error?: Error }): string {
  const raw = result.error?.message ?? result.stderr ?? '';
  const line = raw.split(/\r?\n/).find((value) => value.trim().length > 0);
  return line === undefined ? 'command unavailable' : printable(line.trim()).slice(0, 240);
}

function printable(value: string): string {
  return value.replace(/[^\x20-\x7e]/g, '?');
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
