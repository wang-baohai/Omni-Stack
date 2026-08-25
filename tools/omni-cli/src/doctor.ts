import { existsSync, readFileSync, statfsSync } from 'node:fs';
import { resolve } from 'node:path';
import { loadCatalog } from './catalog.js';
import { captureCommand } from './command.js';
import { COMPOSE_SERVICE_FILES, loadComposeApplication } from './compose-model.js';

export interface DoctorCheck {
  name: string;
  passed: boolean;
  detail: string;
}

/** 执行不改写工作区且不输出 Secret 值的开发环境诊断。 */
export function runDoctor(workspaceRoot: string): DoctorCheck[] {
  const checks: DoctorCheck[] = [
    checkNode(),
    checkJava(workspaceRoot),
    checkMavenWrapper(workspaceRoot),
    checkFrontend(workspaceRoot),
    checkCatalog(workspaceRoot),
    checkEnvironmentFile(workspaceRoot),
    checkDisk(workspaceRoot),
  ];
  const compose = captureCommand('docker', ['compose', 'version', '--short'], workspaceRoot);
  checks.push({
    name: 'Docker Compose',
    passed: compose.status === 0,
    detail: compose.status === 0 ? compose.stdout.trim() : commandFailure(compose),
  });
  const daemon = captureCommand('docker', ['info', '--format', '{{.ServerVersion}}'], workspaceRoot);
  checks.push({
    name: 'Docker daemon',
    passed: daemon.status === 0,
    detail: daemon.status === 0 ? `server ${daemon.stdout.trim()}` : commandFailure(daemon),
  });
  if (compose.status === 0) checks.push(checkComposeProfiles(workspaceRoot));
  checks.push(checkPorts(workspaceRoot));
  if (daemon.status === 0) checks.push(checkRunningServices(workspaceRoot));
  return checks;
}

/** 从 Compose 插值表达式提取必填环境变量名。 */
export function requiredComposeEnvironmentNames(workspaceRoot: string): string[] {
  const content = COMPOSE_SERVICE_FILES
    .map((path) => readFileSync(resolve(workspaceRoot, path), 'utf8'))
    .join('\n');
  return [...new Set([...content.matchAll(/\$\{([A-Z][A-Z0-9_]*):\?/g)].map((match) => match[1]!))].sort();
}

/** 解析 env 文件中已设置为非空的变量名，不返回变量值。 */
export function parseConfiguredEnvironmentNames(content: string): Set<string> {
  const names = new Set<string>();
  for (const line of content.split(/\r?\n/)) {
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
  return {
    name: 'Node.js',
    passed: major > 22 || (major === 22 && minor >= 12),
    detail: `${process.versions.node} (required >= 22.12.0)`,
  };
}

function checkJava(workspaceRoot: string): DoctorCheck {
  const configuredHome = process.env.JAVA_HOME;
  const candidate = configuredHome
    ? resolve(configuredHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
    : 'java';
  const result = captureCommand(candidate, ['-version'], workspaceRoot);
  const major = result.status === 0 ? parseJavaMajor(`${result.stdout}\n${result.stderr}`) : undefined;
  return {
    name: 'JDK',
    passed: major === 25,
    detail: major === undefined ? commandFailure(result) : `${major} (required 25)${configuredHome ? ' via JAVA_HOME' : ''}`,
  };
}

function checkMavenWrapper(workspaceRoot: string): DoctorCheck {
  const passed = existsSync(resolve(workspaceRoot, 'omni-backend', 'mvnw.cmd'))
    || existsSync(resolve(workspaceRoot, 'omni-backend', 'mvnw'));
  return { name: 'Maven Wrapper', passed, detail: 'omni-backend/mvnw(.cmd)' };
}

function checkFrontend(workspaceRoot: string): DoctorCheck {
  const passed = existsSync(resolve(workspaceRoot, 'omni-frontend', 'package.json'));
  return { name: 'Frontend', passed, detail: 'omni-frontend/package.json' };
}

function checkCatalog(workspaceRoot: string): DoctorCheck {
  try {
    const catalog = loadCatalog(workspaceRoot);
    const compose = loadComposeApplication(workspaceRoot);
    return {
      name: 'Module catalog',
      passed: true,
      detail: `${catalog.modules.length} modules, ${Object.keys(compose.services).length} services`,
    };
  } catch (error) {
    return { name: 'Module catalog', passed: false, detail: error instanceof Error ? error.message : String(error) };
  }
}

function checkEnvironmentFile(workspaceRoot: string): DoctorCheck {
  const path = resolve(workspaceRoot, '.env');
  const required = requiredComposeEnvironmentNames(workspaceRoot);
  if (!existsSync(path)) return { name: '.env', passed: false, detail: `missing (${required.length} required names)` };
  const configured = parseConfiguredEnvironmentNames(readFileSync(path, 'utf8'));
  const missing = required.filter((name) => !configured.has(name));
  return {
    name: '.env',
    passed: missing.length === 0,
    detail: missing.length === 0 ? `${required.length} required names configured` : `missing names: ${missing.join(', ')}`,
  };
}

function checkDisk(workspaceRoot: string): DoctorCheck {
  try {
    const stats = statfsSync(workspaceRoot);
    const freeGb = Number(stats.bavail * stats.bsize) / 1024 / 1024 / 1024;
    return { name: 'Disk', passed: freeGb >= 5, detail: `${freeGb.toFixed(1)} GiB free (recommended >= 5 GiB)` };
  } catch (error) {
    return { name: 'Disk', passed: false, detail: error instanceof Error ? error.message : String(error) };
  }
}

function checkComposeProfiles(workspaceRoot: string): DoctorCheck {
  const envFile = existsSync(resolve(workspaceRoot, '.env')) ? '.env' : '.env.example';
  const profiles = ['core', 'workflow', 'crm', 'supply-chain', 'full'];
  const failed: string[] = [];
  for (const profile of profiles) {
    const result = captureCommand(
      'docker', ['compose', '--env-file', envFile, '--profile', profile, 'config', '--quiet'], workspaceRoot,
    );
    if (result.status !== 0) failed.push(profile);
  }
  return {
    name: 'Compose profiles',
    passed: failed.length === 0,
    detail: failed.length === 0 ? `${profiles.join(', ')} valid` : `invalid: ${failed.join(', ')}`,
  };
}

function checkPorts(workspaceRoot: string): DoctorCheck {
  const compose = loadComposeApplication(workspaceRoot);
  const defaults = Object.values(compose.services).flatMap((service) => service.ports ?? [])
    .map((port) => String(port).match(/\$\{[A-Z0-9_]+:-([0-9]+)}/)?.[1])
    .filter((port): port is string => port !== undefined);
  if (process.platform !== 'win32') {
    return { name: 'Ports', passed: true, detail: `${new Set(defaults).size} declared ports (conflict scan available on Windows)` };
  }
  const result = captureCommand('netstat', ['-ano', '-p', 'tcp'], workspaceRoot);
  if (result.status !== 0) return { name: 'Ports', passed: false, detail: commandFailure(result) };
  const listening = new Set([...result.stdout.matchAll(/:(\d+)\s+\S+\s+LISTENING/gi)].map((match) => match[1]!));
  const conflicts = [...new Set(defaults)].filter((port) => listening.has(port));
  return {
    name: 'Ports',
    passed: conflicts.length === 0,
    detail: conflicts.length === 0 ? `${new Set(defaults).size} default ports available` : `in use: ${conflicts.join(', ')}`,
  };
}

function checkRunningServices(workspaceRoot: string): DoctorCheck {
  const result = captureCommand('docker', ['compose', '--profile', '*', 'ps', '-a', '--format', 'json'], workspaceRoot);
  if (result.status !== 0) return { name: 'Runtime health', passed: false, detail: commandFailure(result) };
  const statuses = result.stdout.split(/\r?\n/).filter(Boolean).flatMap((line) => {
    try {
      return [JSON.parse(line) as { Service?: string; State?: string; Health?: string }];
    } catch {
      return [];
    }
  });
  if (statuses.length === 0) {
    return { name: 'Runtime health', passed: true, detail: 'stack not running; database/Nacos checks deferred until dev up' };
  }
  const unhealthy = statuses.filter((status) => status.Health === 'unhealthy' || status.State === 'dead');
  const mysql = statuses.find((status) => status.Service === 'mysql');
  const nacos = statuses.find((status) => status.Service === 'nacos');
  return {
    name: 'Runtime health',
    passed: unhealthy.length === 0,
    detail: unhealthy.length > 0
      ? `unhealthy: ${unhealthy.map((status) => status.Service).join(', ')}`
      : `${statuses.length} containers; mysql=${mysql?.Health ?? mysql?.State ?? 'off'}, nacos=${nacos?.Health ?? nacos?.State ?? 'off'}`,
  };
}

function commandFailure(result: { stderr: string; error?: Error }): string {
  return result.error?.message ?? (result.stderr.trim() || 'command unavailable');
}
