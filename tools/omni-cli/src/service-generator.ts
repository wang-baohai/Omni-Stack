import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { basename, dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import Handlebars from 'handlebars';
import { loadCatalog } from './catalog.js';
import { CliError } from './errors.js';
import type {
  CreateServiceOptions,
  GeneratedFile,
  ServiceGenerationLock,
  ServiceGenerationPlan,
  ServiceSpec,
} from './types.js';

export const GENERATOR_VERSION = '0.5.0';
export const SERVICE_TEMPLATE_VERSION = '1.1.0';
const LOCK_FILE = 'omni-service.lock.json';
const TEMPLATE_ROOT = 'scaffold/templates/service';

interface TemplateEntry {
  template: string;
  output: string;
  when?: (spec: ServiceSpec) => boolean;
}

const TEMPLATE_ENTRIES: TemplateEntry[] = [
  { template: 'backend/pom.xml.hbs', output: 'omni-backend/{{artifactId}}/pom.xml' },
  { template: 'backend/Application.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/{{className}}Application.java' },
  { template: 'backend/SecurityConfig.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/config/SecurityConfig.java' },
  { template: 'backend/TenantTablePolicy.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/security/{{className}}TenantTablePolicy.java' },
  { template: 'backend/DataScopeTablePolicy.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/security/{{className}}DataScopeTablePolicy.java', when: (spec) => spec.enableDataScope },
  { template: 'backend/ModuleInfoController.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/controller/ModuleInfoController.java' },
  { template: 'backend/ModuleInfoService.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/service/ModuleInfoService.java' },
  { template: 'backend/ModuleInfoServiceImpl.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/service/impl/ModuleInfoServiceImpl.java' },
  { template: 'backend/mapper-package-info.java.hbs', output: 'omni-backend/{{artifactId}}/src/main/java/{{packagePath}}/mapper/package-info.java' },
  { template: 'backend/application.yml.hbs', output: 'omni-backend/{{artifactId}}/src/main/resources/application.yml' },
  { template: 'backend/application-dev.yml.hbs', output: 'omni-backend/{{artifactId}}/src/main/resources/application-dev.yml' },
  { template: 'backend/ServiceSkeletonContractTest.java.hbs', output: 'omni-backend/{{artifactId}}/src/test/java/{{packagePath}}/ServiceSkeletonContractTest.java' },
  { template: 'frontend/api.ts.hbs', output: 'omni-frontend/src/api/{{serviceId}}.ts' },
  { template: 'frontend/index.vue.hbs', output: 'omni-frontend/src/views/{{serviceId}}/overview/index.vue' },
  { template: 'database/db.changelog-service.yaml.hbs', output: 'database/changelog/{{serviceId}}/db.changelog-{{serviceId}}.yaml' },
  { template: 'frontend/zh-CN.fragment.ts.hbs', output: 'integration/frontend/{{serviceId}}.zh-CN.fragment.ts' },
  { template: 'frontend/en-US.fragment.ts.hbs', output: 'integration/frontend/{{serviceId}}.en-US.fragment.ts' },
  { template: 'integration/catalog-entry.yaml.hbs', output: 'integration/catalog/{{serviceId}}.yaml' },
  { template: 'integration/gateway-route.yaml.hbs', output: 'integration/gateway/{{serviceId}}.yaml' },
  { template: 'integration/compose-service.yaml.hbs', output: 'integration/compose/{{serviceId}}.yaml' },
  { template: 'integration/parent-pom-module.xml.hbs', output: 'integration/maven/{{serviceId}}.xml' },
  { template: 'integration/permission-seed.yaml.hbs', output: 'integration/permissions/{{serviceId}}.yaml' },
  { template: 'integration/menu.fragment.ts.hbs', output: 'integration/frontend/{{serviceId}}.menu.fragment.ts' },
  { template: 'docs/module.md.hbs', output: 'docs/{{serviceId}}.md' },
  { template: 'docs/i18n-status.yaml.hbs', output: 'docs/{{serviceId}}-i18n-status.yaml' },
  { template: 'generation-plan.md.hbs', output: 'GENERATION-PLAN.md' },
];

/** 解析并严格校验 create-service 输入。 */
export function resolveServiceSpec(workspaceRoot: string, serviceId: string, options: CreateServiceOptions): ServiceSpec {
  if (!/^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/.test(serviceId) || serviceId.length > 40) {
    throw new CliError('service-id 必须是 1～40 位小写字母、数字和单连字符组合，且以字母开头');
  }
  if (loadCatalog(workspaceRoot).modules.some((module) => module.id === serviceId)) {
    throw new CliError(`模块 ${serviceId} 已存在于 catalog，不能重复创建`);
  }

  const packageSuffix = serviceId.replaceAll('-', '.');
  const javaPackage = options.javaPackage ?? `com.omni.${packageSuffix}`;
  if (!/^[a-z_][a-z0-9_]*(?:\.[a-z_][a-z0-9_]*)+$/.test(javaPackage)) {
    throw new CliError('Java package 必须是合法的小写点分包名');
  }
  const words = serviceId.split('-');
  const className = words.map(capitalize).join('');
  const displayName = options.displayName ?? words.map(capitalize).join(' ');
  if (displayName.length === 0 || displayName.length > 80 || /[\r\n]/.test(displayName)) {
    throw new CliError('显示名称必须为 1～80 个字符且不能换行');
  }
  const apiPrefix = options.apiPrefix ?? `/api/${serviceId}`;
  if (!/^\/api\/[a-z0-9][a-z0-9/-]*$/.test(apiPrefix) || apiPrefix.includes('//') || apiPrefix.endsWith('/')) {
    throw new CliError('API 前缀必须是 /api/ 开头的小写路径，且不能以 / 结尾');
  }
  const usedPorts = scanConfiguredPorts(workspaceRoot);
  const enableMq = options.mq === true;
  const enableJob = options.job === true || enableMq;
  const enableOperLog = options.operLog === true;
  const servicePort = options.servicePort ?? firstAvailablePort(usedPorts, 8110, 8199);
  const managementPort = options.managementPort ?? firstAvailablePort(usedPorts, 19910, 19999, new Set([servicePort]));
  const xxlPort = options.xxlPort ?? firstAvailablePort(usedPorts, 9910, 9999, new Set([servicePort, managementPort]));
  validatePorts([servicePort, managementPort, ...(enableJob ? [xxlPort] : [])], usedPorts);

  const databaseName = options.databaseName ?? `omni_${serviceId.replaceAll('-', '_')}`;
  if (!/^[a-z][a-z0-9_]{1,62}$/.test(databaseName)) {
    throw new CliError('数据库名必须是 2～63 位小写字母、数字或下划线');
  }
  const tablePrefix = options.tablePrefix ?? `${serviceId.replaceAll('-', '_')}_`;
  if (!/^[a-z][a-z0-9_]{0,30}_$/.test(tablePrefix)) {
    throw new CliError('表前缀必须是小写字母、数字或下划线并以单个下划线结尾');
  }
  const dataScopeTables = [...new Set(options.dataScopeTable ?? [])].sort();
  for (const table of dataScopeTables) {
    if (!/^[a-z][a-z0-9_]{1,63}$/.test(table) || !table.startsWith(tablePrefix)) {
      throw new CliError(`DataScope 表 ${table} 非法或不属于表前缀 ${tablePrefix}`);
    }
  }
  const enableDataScope = options.dataScope === true || dataScopeTables.length > 0;
  if (enableDataScope && dataScopeTables.length === 0) {
    throw new CliError('启用 DataScope 时必须至少提供一个 --data-scope-table');
  }

  return {
    serviceId,
    artifactId: `omni-${serviceId}`,
    javaPackage,
    packagePath: javaPackage.replaceAll('.', '/'),
    className,
    displayName,
    apiPrefix,
    servicePort,
    managementPort,
    xxlPort,
    databaseName,
    tablePrefix,
    enableOperLog,
    enableJob,
    enableMq,
    enableDataScope,
    dataScopeTables,
  };
}

/** 渲染生成计划，但不修改文件系统。 */
export function planServiceGeneration(workspaceRoot: string, serviceId: string, options: CreateServiceOptions): ServiceGenerationPlan {
  const spec = resolveServiceSpec(workspaceRoot, serviceId, options);
  const targetDirectory = resolveTargetDirectory(workspaceRoot, options.output ?? `generated/${spec.artifactId}`);
  const context = templateContext(spec);
  const files = TEMPLATE_ENTRIES
    .filter((entry) => entry.when?.(spec) ?? true)
    .map((entry) => renderEntry(workspaceRoot, entry, context));
  ensureUniquePaths(files);
  return { targetDirectory, spec, files };
}

/** 原子写入服务生成包；重复执行相同输入时不产生变化。 */
export function applyServiceGeneration(plan: ServiceGenerationPlan): 'created' | 'unchanged' {
  if (existsSync(plan.targetDirectory)) {
    validateGeneratedService(plan.targetDirectory, plan.spec);
    return 'unchanged';
  }
  const parent = dirname(plan.targetDirectory);
  mkdirSync(parent, { recursive: true });
  const staging = resolve(parent, `.${basename(plan.targetDirectory)}.staging-${process.pid}-${Date.now()}`);
  assertChildPath(parent, staging);
  try {
    mkdirSync(staging, { recursive: false });
    for (const file of plan.files) {
      const destination = resolve(staging, ...file.path.split('/'));
      assertChildPath(staging, destination);
      mkdirSync(dirname(destination), { recursive: true });
      writeFileSync(destination, file.content, 'utf8');
    }
    const lock = createLock(plan);
    writeFileSync(resolve(staging, LOCK_FILE), `${JSON.stringify(lock, null, 2)}\n`, 'utf8');
    validateGeneratedService(staging, plan.spec);
    renameSync(staging, plan.targetDirectory);
    return 'created';
  } catch (error) {
    if (existsSync(staging)) rmSync(staging, { recursive: true, force: true });
    if (error instanceof CliError) throw error;
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`原子写入失败，staging 已清理：${detail}`);
  }
}

/** 校验生成包锁文件、输入及全部文件哈希。 */
export function validateGeneratedService(targetDirectory: string, expectedSpec?: ServiceSpec): ServiceGenerationLock {
  const target = resolve(targetDirectory);
  const lockPath = resolve(target, LOCK_FILE);
  if (!existsSync(lockPath)) throw new CliError(`目标目录缺少 ${LOCK_FILE}，拒绝覆盖未知内容`);
  let lock: ServiceGenerationLock;
  try {
    lock = JSON.parse(readFileSync(lockPath, 'utf8')) as ServiceGenerationLock;
  } catch {
    throw new CliError(`${LOCK_FILE} 不是合法 JSON`);
  }
  if (!isServiceGenerationLock(lock)) throw new CliError(`${LOCK_FILE} 结构不合法`);
  if (lock.generatedBy !== '@omni-stack/cli' || lock.templateVersion !== SERVICE_TEMPLATE_VERSION) {
    throw new CliError('生成包所有权或模板版本不匹配，拒绝覆盖');
  }
  if (expectedSpec && stableJson(lock.spec) !== stableJson(expectedSpec)) {
    throw new CliError('目标目录由不同的 create-service 输入生成，拒绝覆盖');
  }
  for (const file of lock.files) {
    if (!isSafeRelativePath(file.path)) throw new CliError(`锁文件包含不安全路径：${file.path}`);
    const actualPath = resolve(target, ...file.path.split('/'));
    if (!existsSync(actualPath) || !statSync(actualPath).isFile()) {
      throw new CliError(`生成文件缺失：${file.path}`);
    }
    const actualHash = sha256(readFileSync(actualPath));
    if (actualHash !== file.sha256) throw new CliError(`生成文件已被修改：${file.path}`);
  }
  const actualFiles = listFiles(target).filter((path) => path !== LOCK_FILE).sort();
  const lockedFiles = lock.files.map((file) => file.path).sort();
  if (stableJson(actualFiles) !== stableJson(lockedFiles)) {
    throw new CliError('目标目录包含锁文件未登记的文件，拒绝覆盖');
  }
  return lock;
}

function renderEntry(workspaceRoot: string, entry: TemplateEntry, context: Record<string, unknown>): GeneratedFile {
  const templatePath = resolve(workspaceRoot, TEMPLATE_ROOT, ...entry.template.split('/'));
  if (!existsSync(templatePath)) throw new CliError(`服务模板不存在：${entry.template}`);
  const output = Handlebars.compile(entry.output, { noEscape: true })(context);
  if (!isSafeRelativePath(output)) throw new CliError(`模板输出路径不安全：${output}`);
  const content = normalizeNewlines(Handlebars.compile(readFileSync(templatePath, 'utf8'), { noEscape: true })(context));
  return { path: output, content, sha256: sha256(content) };
}

function templateContext(spec: ServiceSpec): Record<string, unknown> {
  return {
    ...spec,
    generatorVersion: GENERATOR_VERSION,
    templateVersion: SERVICE_TEMPLATE_VERSION,
    dataScopeTablesCsv: spec.dataScopeTables.join(', '),
    dependencyOperLog: spec.enableOperLog,
    dependencyJob: spec.enableJob,
    dependencyMq: spec.enableMq,
    dependencyMqTransport: spec.enableMq || spec.enableOperLog,
    enableStream: spec.enableMq || spec.enableOperLog,
    vueOpen: '{{',
    vueClose: '}}',
    managementPortExpression: `\${MANAGEMENT_PORT:${spec.managementPort}}`,
    xxlPortExpression: `\${XXL_JOB_EXECUTOR_PORT:${spec.xxlPort}}`,
    frontendApiPrefix: spec.apiPrefix.slice('/api'.length),
    lowerCamelClassName: `${spec.className.charAt(0).toLowerCase()}${spec.className.slice(1)}`,
  };
}

function createLock(plan: ServiceGenerationPlan): ServiceGenerationLock {
  return {
    generatedBy: '@omni-stack/cli',
    generatorVersion: GENERATOR_VERSION,
    templateVersion: SERVICE_TEMPLATE_VERSION,
    spec: plan.spec,
    files: plan.files.map(({ path, sha256: hash }) => ({ path, sha256: hash })),
  };
}

function resolveTargetDirectory(workspaceRoot: string, output: string): string {
  const target = resolve(workspaceRoot, output);
  if (target === resolve(workspaceRoot)) throw new CliError('输出目录不能是工作区根目录');
  if (basename(target).length === 0) throw new CliError('输出目录无效');
  return target;
}

function scanConfiguredPorts(workspaceRoot: string): Set<number> {
  const ports = new Set<number>();
  const backendRoot = resolve(workspaceRoot, 'omni-backend');
  if (!existsSync(backendRoot)) return ports;
  for (const moduleName of readdirSync(backendRoot)) {
    const configPath = resolve(backendRoot, moduleName, 'src/main/resources/application.yml');
    if (!existsSync(configPath)) continue;
    const content = readFileSync(configPath, 'utf8');
    for (const match of content.matchAll(/(?:port:\s*|PORT:)(\d{2,5})/g)) {
      const value = Number(match[1]);
      if (Number.isInteger(value)) ports.add(value);
    }
  }
  return ports;
}

function firstAvailablePort(used: Set<number>, start: number, end: number, reserved = new Set<number>()): number {
  for (let port = start; port <= end; port += 1) {
    if (!used.has(port) && !reserved.has(port)) return port;
  }
  throw new CliError(`端口区间 ${start}-${end} 已用尽`);
}

function validatePorts(ports: number[], used: Set<number>): void {
  const seen = new Set<number>();
  for (const port of ports) {
    if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new CliError(`端口 ${port} 不合法`);
    if (seen.has(port)) throw new CliError(`端口 ${port} 在本次生成参数中重复`);
    if (used.has(port)) throw new CliError(`端口 ${port} 已被当前工作区使用`);
    seen.add(port);
  }
}

function listFiles(root: string, current = root): string[] {
  const result: string[] = [];
  for (const entry of readdirSync(current, { withFileTypes: true })) {
    const absolute = resolve(current, entry.name);
    if (entry.isDirectory()) result.push(...listFiles(root, absolute));
    else if (entry.isFile()) result.push(relative(root, absolute).split(sep).join('/'));
    else throw new CliError(`生成目录包含不支持的文件类型：${absolute}`);
  }
  return result;
}

function ensureUniquePaths(files: GeneratedFile[]): void {
  const paths = new Set<string>();
  for (const file of files) {
    if (paths.has(file.path)) throw new CliError(`模板生成了重复路径：${file.path}`);
    paths.add(file.path);
  }
}

function assertChildPath(parent: string, child: string): void {
  const path = relative(resolve(parent), resolve(child));
  if (path.length === 0 || path === '..' || path.startsWith(`..${sep}`) || isAbsolute(path)) {
    throw new CliError(`路径越界：${child}`);
  }
}

function isSafeRelativePath(path: string): boolean {
  return path.length > 0 && !isAbsolute(path) && !path.split(/[\\/]/).includes('..');
}

function sha256(value: string | Buffer): string {
  return createHash('sha256').update(value).digest('hex');
}

function normalizeNewlines(value: string): string {
  return `${value.replace(/\r\n/g, '\n').replace(/\n*$/, '')}\n`;
}

function stableJson(value: unknown): string {
  return JSON.stringify(value);
}

function isServiceGenerationLock(value: unknown): value is ServiceGenerationLock {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as Partial<ServiceGenerationLock>;
  return candidate.generatedBy === '@omni-stack/cli'
    && typeof candidate.generatorVersion === 'string'
    && typeof candidate.templateVersion === 'string'
    && candidate.spec !== null
    && typeof candidate.spec === 'object'
    && typeof candidate.spec.serviceId === 'string'
    && Array.isArray(candidate.files)
    && candidate.files.every((file) => file !== null
      && typeof file === 'object'
      && typeof file.path === 'string'
      && /^[a-f0-9]{64}$/.test(file.sha256));
}

function capitalize(value: string): string {
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`;
}
