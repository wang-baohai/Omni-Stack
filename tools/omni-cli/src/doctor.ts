import { existsSync, readFileSync, statfsSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { loadCatalog, validateCatalogGraph, validateCatalogResources } from './catalog.js';
import { captureCommand } from './command.js';
import { loadComposeApplication } from './compose-model.js';
import { listPresetIds, resolvePreset } from './presets.js';

/** 参与 Compose 插值的全部文件；只扫两个分片会漏掉后续加入的必填变量。 */
const COMPOSE_INTERPOLATION_FILES = [
  'compose.yaml', 'compose.infra.yaml', 'compose.apps.yaml', 'compose.observability.yaml',
];
const DEVELOPMENT_PROFILES = ['core', 'workflow', 'crm', 'supply-chain', 'full', 'observability'];
const MINIMUM_FREE_GIB = 5;
const CAPTURED_DETAIL_LIMIT = 240;

export type DoctorStatus = 'pass' | 'fail' | 'skip';

export interface DoctorCheck {
  name: string;
  /** 兼容既有调用方：仅 `pass` 视为 true，`skip` 不等于通过。 */
  passed: boolean;
  detail: string;
  status: DoctorStatus;
  /** 失败或跳过时必须给出的定位/修复建议。 */
  hint: string;
}

/** 执行不改写工作区且不输出 Secret 值的开发环境诊断。 */
export function runDoctor(workspaceRoot: string): DoctorCheck[] {
  const checks: DoctorCheck[] = [
    checkNode(),
    checkJava(workspaceRoot),
    checkMavenWrapper(workspaceRoot),
    checkFrontend(workspaceRoot),
    checkCatalog(workspaceRoot),
    checkPresetClosure(workspaceRoot),
    checkEnvironmentFile(workspaceRoot),
    checkDisk(workspaceRoot),
  ];
  const compose = captureCommand('docker', ['compose', 'version', '--short'], workspaceRoot);
  checks.push(ran(
    'Docker Compose', compose,
    () => ok('Docker Compose', compose.stdout.trim()),
    () => fail('Docker Compose', captured(compose), '安装 Docker Desktop/Compose v2 插件并确保 `docker compose version` 可用'),
  ));
  const daemon = captureCommand('docker', ['info', '--format', '{{.ServerVersion}}'], workspaceRoot);
  checks.push(ran(
    'Docker daemon', daemon,
    () => ok('Docker daemon', `server ${daemon.stdout.trim()}`),
    () => fail('Docker daemon', captured(daemon), '启动 Docker daemon；Linux 下确认当前用户在 docker 组'),
  ));
  if (daemon.status === 0) checks.push(checkComposeProfiles(workspaceRoot));
  checks.push(checkPorts(workspaceRoot));
  checks.push(checkVolumeIsolation(workspaceRoot));
  const running = runningContainers(workspaceRoot);
  checks.push(running);
  checks.push(checkDatabase(workspaceRoot));
  checks.push(checkNacos(workspaceRoot));
  return checks;
}

/** 任一 `fail` 都必须让 CLI 以非零码退出；`skip` 只表示前置条件不成立。 */
export function doctorExitCode(checks: DoctorCheck[]): number {
  return checks.some((check) => check.status === 'fail') ? 1 : 0;
}

/** 从 Compose 插值表达式提取必填环境变量名（只返回名字，不返回提示文本或值）。 */
export function requiredComposeEnvironmentNames(workspaceRoot: string): string[] {
  const content = COMPOSE_INTERPOLATION_FILES
    .map((path) => resolve(workspaceRoot, path))
    .filter((path) => existsSync(path))
    .map((path) => readFileSync(path, 'utf8'))
    .join('\n');
  return [...new Set([...content.matchAll(/\$\{([A-Z][A-Z0-9_]*):\?/g)].map((match) => match[1]!))].sort();
}

/** 解析 env 文件中已设置为非空的变量名，不返回变量值。 */
export function parseConfiguredEnvironmentNames(content: string): Set<string> {
  const names = new Set<string>();
  for (const line of content.split(/\r?\n/)) {
    if (/^\s*#/.test(line)) continue;
    const match = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (match && match[2] && !/^(?:<.*>|replace-with(?:-|$))/i.test(match[2])) names.add(match[1]!);
  }
  return names;
}

/** 解析 Java version 输出中的主版本号。 */
export function parseJavaMajor(output: string): number | undefined {
  const match = output.match(/version\s+"(?:1\.)?(\d+)/i);
  return match ? Number(match[1]) : undefined;
}

function checkNode(): DoctorCheck {
  const [major = 0, minor = 0] = process.versions.node.split('.').map(Number);
  const detail = `${process.versions.node} (required >= 22.12.0)`;
  if (major > 22 || (major === 22 && minor >= 12)) return ok('Node.js', detail);
  return fail('Node.js', detail, '升级到 Node 22.12 或更高版本后重试');
}

function checkJava(workspaceRoot: string): DoctorCheck {
  const configuredHome = process.env.JAVA_HOME;
  const candidate = configuredHome
    ? resolve(configuredHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const result = captureCommand(candidate, ['-version'], workspaceRoot);
  const major = result.status === 0 ? parseJavaMajor(`${result.stdout}\n${result.stderr}`) : undefined;
  if (major === 25) return ok('JDK', `25${configuredHome ? ' via JAVA_HOME' : ''}`);
  if (major === undefined) {
    return fail('JDK', captured(result), '安装 JDK 25 或正确设置 JAVA_HOME 指向 JDK 25');
  }
  return fail('JDK', `${major} (required 25)${configuredHome ? ' via JAVA_HOME' : ''}`,
    `当前 JDK ${major} 不满足构建要求，切到 JDK 25 后重跑 doctor`);
}

function checkMavenWrapper(workspaceRoot: string): DoctorCheck {
  const path = resolve(workspaceRoot, 'omni-backend', process.platform === 'win32' ? 'mvnw.cmd' : 'mvnw');
  return existsSync(path)
    ? ok('Maven Wrapper', 'omni-backend/mvnw(.cmd) present')
    : fail('Maven Wrapper', 'omni-backend/mvnw(.cmd) missing', '在源仓库根执行本命令，或确认生成项目已包含 omni-backend wrapper');
}

function checkFrontend(workspaceRoot: string): DoctorCheck {
  const path = resolve(workspaceRoot, 'omni-frontend', 'package.json');
  return existsSync(path)
    ? ok('Frontend', 'omni-frontend/package.json present')
    : fail('Frontend', 'omni-frontend/package.json missing', '确认工作区包含 omni-frontend，或用 preset create 生成完整工程');
}

function checkCatalog(workspaceRoot: string): DoctorCheck {
  try {
    const catalog = loadCatalog(workspaceRoot);
    validateCatalogGraph(catalog);
    validateCatalogResources(workspaceRoot, catalog);
    const compose = loadComposeApplication(workspaceRoot);
    return ok('Module catalog', `${catalog.modules.length} modules, ${Object.keys(compose.services).length} services`);
  } catch (error) {
    return fail('Module catalog', message(error), '按 detail 指示修正 scaffold/catalog/modules.yaml 后重跑 catalog validate');
  }
}

/** preset 依赖闭包与 Compose 服务归属必须能在不启动容器的情况下自证。 */
export function checkPresetClosure(workspaceRoot: string): DoctorCheck {
  try {
    const catalog = loadCatalog(workspaceRoot);
    const compose = loadComposeApplication(workspaceRoot);
    const presets = listPresetIds(workspaceRoot);
    const problems: string[] = [];
    for (const presetId of presets) {
      const resolved = resolvePreset(workspaceRoot, catalog, presetId);
      const services = [...new Set(catalog.modules
        .filter((module) => resolved.resolvedModules.includes(module.id))
        .flatMap((module) => module.composeServices))];
      const missing = services.filter((service) => compose.services[service] === undefined);
      if (missing.length > 0) problems.push(`${presetId} -> unknown services ${missing.join(',')}`);
      for (const moduleId of resolved.resolvedModules) {
        const definition = catalog.modules.find((module) => module.id === moduleId);
        if (!definition) problems.push(`${presetId} -> unknown module ${moduleId}`);
      }
    }
    if (problems.length > 0) {
      return fail('Preset closure', problems.join('; ').slice(0, CAPTURED_DETAIL_LIMIT),
        '闭包引用了 catalog 或 Compose 中不存在的对象；先修 scaffold/catalog 与 compose 分片');
    }
    return ok('Preset closure', `${presets.length} presets resolve to existing modules and services`);
  } catch (error) {
    return fail('Preset closure', message(error), '该失败可在无容器条件下复现，属 catalog/preset 事实错误');
  }
}

function checkEnvironmentFile(workspaceRoot: string): DoctorCheck {
  const path = resolve(workspaceRoot, '.env');
  const required = requiredComposeEnvironmentNames(workspaceRoot);
  if (!existsSync(path)) {
    return fail('.env', `missing (${required.length} required names)`,
      `复制 .env.example 为 .env 并填写这些变量名：${required.join(', ')}`);
  }
  const configured = parseConfiguredEnvironmentNames(readFileSync(path, 'utf8'));
  const missing = required.filter((name) => !configured.has(name));
  return missing.length === 0
    ? ok('.env', `${required.length} required names configured (values not printed)`)
    : fail('.env', `missing names: ${missing.join(', ')}`, '只补齐列出的变量名；doctor 永不读取或打印变量值');
}

function checkDisk(workspaceRoot: string): DoctorCheck {
  try {
    const stats = statfsSync(workspaceRoot);
    const freeGb = Number(stats.bavail * stats.bsize) / 1024 / 1024 / 1024;
    return freeGb >= MINIMUM_FREE_GIB
      ? ok('Disk', `${freeGb.toFixed(1)} GiB free (required >= ${MINIMUM_FREE_GIB} GiB)`)
      : fail('Disk', `${freeGb.toFixed(1)} GiB free < ${MINIMUM_FREE_GIB} GiB`, '清理镜像/卷或换盘；不足会导致构建与迁移随机失败');
  } catch (error) {
    return fail('Disk', message(error), '确认工作区所在卷可访问；Windows 下检查盘符是否在线');
  }
}

function checkComposeProfiles(workspaceRoot: string): DoctorCheck {
  const envFile = existsSync(resolve(workspaceRoot, '.env')) ? '.env' : '.env.example';
  const failed: string[] = [];
  let firstError = '';
  for (const profile of DEVELOPMENT_PROFILES) {
    const result = captureCommand(
      'docker',
      ['compose', '--env-file', envFile, '--profile', profile, 'config', '--quiet'],
      workspaceRoot,
      interpolatePlaceholders(workspaceRoot),
    );
    if (result.status !== 0) {
      failed.push(profile);
      if (firstError === '') firstError = captured(result);
    }
  }
  return failed.length === 0
    ? ok('Compose profiles', `${DEVELOPMENT_PROFILES.length} profiles valid via ${envFile}`)
    : fail('Compose profiles', `invalid: ${failed.join(', ')}; ${firstError}`,
      `用 --env-file ${envFile} 单独执行 docker compose --profile <name> config 取完整报错`);
}

function checkPorts(workspaceRoot: string): DoctorCheck {
  const declared = declaredHostPorts(workspaceRoot);
  if (process.platform !== 'win32') {
    return {
      ...skipped('Ports', `${declared.size} declared host ports; conflict scan is only implemented on Windows`,
        '在 Linux/macOS 用 `ss -ltn` 人工核对这些端口'),
    };
  }
  const result = captureCommand('netstat', ['-ano', '-p', 'tcp'], workspaceRoot);
  if (result.status !== 0) {
    return fail('Ports', captured(result), 'netstat 不可用时改用 `Get-NetTCPConnection -State Listen`');
  }
  const listening = new Set([...result.stdout.matchAll(/:(\d+)\s+\S+\s+LISTENING/gi)].map((match) => match[1]!));
  const owned = portsPublishedByCurrentProject(workspaceRoot);
  const conflicts = [...declared].filter((port) => listening.has(port) && !owned.has(port));
  return conflicts.length === 0
    ? ok('Ports', `${declared.size} declared host ports; ${owned.size} already held by this project`)
    : fail('Ports', `in use by other processes: ${conflicts.join(', ')}`,
      '用 --set OMNI_*_HOST_PORT 偏移本行端口，或释放占用进程；不要把冲突归因于服务本身');
}

/** 当前 project 自己已发布的宿主端口不构成冲突。 */
function portsPublishedByCurrentProject(workspaceRoot: string): Set<string> {
  const result = captureCommand(
    'docker', ['ps', '--format', '{{.Ports}}'], workspaceRoot, interpolatePlaceholders(workspaceRoot),
  );
  if (result.status !== 0) return new Set();
  return new Set([...result.stdout.matchAll(/:([0-9]+)->/g)].map((match) => match[1]!));
}

function declaredHostPorts(workspaceRoot: string): Set<string> {
  try {
    const compose = loadComposeApplication(workspaceRoot);
    const ports = new Set<string>();
    for (const service of Object.values(compose.services)) {
      for (const port of service.ports ?? []) {
        const match = String(port).match(/\$\{[A-Z][A-Z0-9_]*:?-([0-9]+)}/) ?? String(port).match(/:(\d+)(?:\/\w+)?$/);
        if (match) ports.add(match[1]!);
      }
    }
    return ports;
  } catch {
    return new Set();
  }
}

/**
 * 命名卷必须按 project 隔离。compose 里写死 `name:` 会让多个 project 复用同一
 * 数据卷，`dev down --volumes` 因此可能删掉别的项目的库。
 */
function checkVolumeIsolation(workspaceRoot: string): DoctorCheck {
  const result = captureCommand(
    'docker', ['compose', '--profile', '*', 'config', '--volumes'], workspaceRoot,
    interpolatePlaceholders(workspaceRoot),
  );
  if (result.status !== 0) {
    return fail('Volume isolation', captured(result), '先让 docker compose config --volumes 成功，再判断卷归属');
  }
  const volumes = result.stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  if (volumes.length === 0) return skipped('Volume isolation', 'no named volumes declared', '若业务需要持久化，说明卷声明缺失');
  const project = implicitProjectName(workspaceRoot);
  const foreign = volumes.filter((volume) => {
    const owner = volumeOwnerProject(workspaceRoot, volume, interpolatePlaceholders(workspaceRoot));
    return owner !== undefined && owner !== project;
  });
  return foreign.length === 0
    ? ok('Volume isolation', `${volumes.length} named volumes resolved for project "${project}"`)
    : fail('Volume isolation', `${foreign.join(',')} 属于其他 project（当前 "${project}"）`,
      '为 compose 卷名加上 project 前缀（OMNI_*_VOLUME_NAME）后再复用 dev 命令；否则 dev down --volumes 会误删他人数据');
}

function runningContainers(workspaceRoot: string): DoctorCheck {
  const result = captureCommand(
    'docker', ['compose', '--profile', '*', 'ps', '-a', '--format', 'json'], workspaceRoot,
    interpolatePlaceholders(workspaceRoot),
  );
  if (result.status !== 0) {
    return fail('Runtime inventory', captured(result), '该报错来自 Compose 解析或 daemon，不等同于服务不健康');
  }
  const statuses = parseJsonLines<{ Service?: string; State?: string; Health?: string }>(result.stdout);
  if (statuses.length === 0) {
    return skipped('Runtime inventory', 'stack not running; database/Nacos checks skipped until dev up',
      '先 `omni dev up --preset <id>` 再复跑 doctor 以获得正向证据');
  }
  const unhealthy = statuses.filter((status) => status.Health === 'unhealthy' || status.State === 'dead');
  const summary = ['mysql', 'nacos'].map((service) => {
    const found = statuses.find((status) => status.Service === service);
    return `${service}=${found?.Health ?? found?.State ?? 'absent'}`;
  });
  return unhealthy.length === 0
    ? ok('Runtime inventory', `${statuses.length} containers; ${summary.join(', ')}`)
    : fail('Runtime inventory', `unhealthy: ${unhealthy.map((status) => status.Service).join(', ')}`,
      '用 `omni dev logs --module <id> --tail 200` 取证后再判断是配置还是依赖问题');
}

function checkDatabase(workspaceRoot: string): DoctorCheck {
  if (!containerRunning(workspaceRoot, 'mysql')) {
    return skipped('Database connectivity', 'mysql container not running', 'dev up 后重跑 doctor 才能验证真实连接');
  }
  const result = captureCommand(
    'docker',
    [
      'compose', 'exec', '-T', 'mysql', 'sh', '-c',
      'mysqladmin ping --protocol=tcp -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD"',
    ],
    workspaceRoot,
    interpolatePlaceholders(workspaceRoot),
  );
  return result.status === 0
    ? ok('Database connectivity', 'mysqladmin ping alive inside mysql container')
    : fail('Database connectivity', captured(result), '连接失败发生在容器内凭据或初始化阶段；检查迁移容器与 .env 变量名是否齐备');
}

function checkNacos(workspaceRoot: string): DoctorCheck {
  if (!containerRunning(workspaceRoot, 'nacos')) {
    return skipped('Nacos registration', 'nacos container not running', 'dev up 带 Nacos 的组合后重跑 doctor');
  }
  const reachable = captureCommand(
    'docker', ['compose', 'exec', '-T', 'nacos', 'curl', '-sf', '-m', '5', 'http://localhost:8848/nacos/'],
    workspaceRoot, interpolatePlaceholders(workspaceRoot),
  );
  if (reachable.status !== 0) {
    return fail('Nacos registration', captured(reachable), 'Nacos API 不可达，检查其 healthcheck 与 mysql 依赖是否已完成');
  }
  const listed = captureCommand(
    'docker',
    ['compose', 'exec', '-T', 'nacos', 'curl', '-sf', '-m', '5',
      'http://localhost:8848/nacos/v1/ns/operator/servers'],
    workspaceRoot, interpolatePlaceholders(workspaceRoot),
  );
  return listed.status === 0
    ? ok('Nacos registration', `api reachable; server list ${listed.stdout.trim().length} bytes`)
    : skipped('Nacos registration', 'api reachable but v1 operator endpoint unavailable',
      '确认镜像版本的注册查询端点（v3 使用 /nacos/v3/admin/...）后补正向证据');
}

/** 返回该目录会被 Compose 解析成的默认 project 名（只由目录名派生，不含秘密）。 */
export function implicitProjectName(workspaceRoot: string): string {
  return basename(resolve(workspaceRoot)).toLowerCase().replace(/[^a-z0-9_-]/g, '');
}

/** 返回卷的 `com.docker.compose.project` 标签；卷不存在时返回 undefined。 */
export function volumeOwnerProject(
  workspaceRoot: string,
  volumeName: string,
  environment: NodeJS.ProcessEnv = {},
): string | undefined {
  const result = captureCommand(
    'docker',
    ['volume', 'inspect', '--format', '{{ index .Label "com.docker.compose.project" }}', volumeName],
    workspaceRoot,
    environment,
  );
  if (result.status !== 0) return undefined;
  const owner = result.stdout.trim();
  return owner.length > 0 ? owner : undefined;
}

function containerRunning(workspaceRoot: string, service: string): boolean {
  const result = captureCommand(
    'docker', ['compose', '--profile', '*', 'ps', '-a', '--format', 'json', service], workspaceRoot,
    interpolatePlaceholders(workspaceRoot),
  );
  if (result.status !== 0) return false;
  return parseJsonLines<{ State?: string }>(result.stdout).some((status) => status.State === 'running');
}

/** 只读取变量名并填入非秘密占位，避免只读 Compose 操作因缺 .env 直接失败。 */
function interpolatePlaceholders(workspaceRoot: string): NodeJS.ProcessEnv {
  return Object.fromEntries(requiredComposeEnvironmentNames(workspaceRoot)
    .map((name) => [name, process.env[name] ?? 'unused-for-compose-read-operation']));
}

function parseJsonLines<T>(output: string): T[] {
  return output.split(/\r?\n/).filter(Boolean).flatMap((line) => {
    try {
      return [JSON.parse(line) as T];
    } catch {
      return [];
    }
  });
}

function ok(name: string, detail: string): DoctorCheck {
  return { name, detail: bound(detail), status: 'pass', passed: true, hint: '' };
}

function fail(name: string, detail: string, hint: string): DoctorCheck {
  return { name, detail: bound(detail), status: 'fail', passed: false, hint };
}

function skipped(name: string, detail: string, hint: string): DoctorCheck {
  return { name, detail: `SKIP ${bound(detail)}`, status: 'skip', passed: false, hint };
}

function ran(
  name: string,
  result: { status: number | null },
  onPass: () => DoctorCheck,
  onFail: () => DoctorCheck,
): DoctorCheck {
  return result.status === 0 ? onPass() : onFail();
}

/** 外部命令输出可能带路径或用户输入，统一截断并剔除非可打印字符。 */
function captured(result: { stdout: string; stderr: string; error?: Error }): string {
  const raw = result.error?.message ?? `${result.stderr}\n${result.stdout}`;
  const line = raw.split(/\r?\n/).map((value) => value.trim()).filter(Boolean)[0] ?? 'command failed';
  return line.replace(/[^\x20-\x7e]/g, '?').slice(0, CAPTURED_DETAIL_LIMIT);
}

function bound(detail: string): string {
  return detail.length <= CAPTURED_DETAIL_LIMIT ? detail : `${detail.slice(0, CAPTURED_DETAIL_LIMIT)}...`;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
