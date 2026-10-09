import { existsSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { CliError } from './errors.js';
import { readYamlFile } from './yaml.js';

export const COMPOSE_SERVICE_FILES = ['compose.infra.yaml', 'compose.apps.yaml'] as const;

export interface ComposeServiceDefinition {
  depends_on?: Record<string, unknown> | string[];
  environment?: Record<string, unknown>;
  ports?: Array<string | number>;
  profiles?: string[];
}

export interface ComposeApplicationModel {
  services: Record<string, ComposeServiceDefinition>;
  volumes: Record<string, unknown>;
}

/** 加载拆分后的 Compose 服务模型并拒绝重复定义。 */
export function loadComposeApplication(workspaceRoot: string): ComposeApplicationModel {
  const services: Record<string, ComposeServiceDefinition> = {};
  const volumes: Record<string, unknown> = {};
  for (const relativePath of COMPOSE_SERVICE_FILES) {
    const path = resolve(workspaceRoot, relativePath);
    if (!existsSync(path)) throw new CliError(`缺少 Compose 分片: ${relativePath}`);
    const value = readYamlFile(path) as {
      services?: Record<string, ComposeServiceDefinition>;
      volumes?: Record<string, unknown>;
    };
    for (const [serviceId, service] of Object.entries(value.services ?? {})) {
      if (services[serviceId] !== undefined) throw new CliError(`Compose 服务重复定义: ${serviceId}`);
      services[serviceId] = service;
    }
    for (const [volumeId, volume] of Object.entries(value.volumes ?? {})) {
      if (volumes[volumeId] !== undefined) throw new CliError(`Compose 数据卷重复定义: ${volumeId}`);
      volumes[volumeId] = volume;
    }
  }
  return { services, volumes };
}

/** 返回服务声明中的依赖列表。 */
export function composeDependencies(service: ComposeServiceDefinition): string[] {
  return Array.isArray(service.depends_on)
    ? service.depends_on
    : Object.keys(service.depends_on ?? {});
}

/** 端口单段解析结果（支持字面量、${VAR} 与 ${VAR:-default}）。 */
export interface ComposePortSegment {
  literal?: number;
  envVar?: string;
  envDefault?: number;
}

/** 一条宿主端口映射的完整解析结果。 */
export interface ComposeHostPort {
  raw: string;
  hostIp?: string | undefined;
  host: ComposePortSegment;
  container?: ComposePortSegment | undefined;
  /** 未注入任何环境变量时的宿主端口；无法静态判定时为 undefined。 */
  effectiveHostPort?: number | undefined;
}

function parsePortSegment(token: string): ComposePortSegment {
  const trimmed = token.trim();
  if (/^\d+$/.test(trimmed)) return { literal: Number(trimmed) };
  const variable = trimmed.match(/^\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([0-9]+))?\}$/);
  if (variable) {
    return variable[2] === undefined
      ? { envVar: variable[1]! }
      : { envVar: variable[1]!, envDefault: Number(variable[2]!) };
  }
  return {};
}

/** 拆分 ports 映射；必须跳过 ${VAR:-default} 内部的冒号。 */
function splitPortMapping(text: string): string[] {
  const parts: string[] = [];
  let current = '';
  let depth = 0;
  for (const character of text) {
    if (character === '{') depth += 1;
    else if (character === '}') depth -= 1;
    if (character === ':' && depth === 0) {
      parts.push(current);
      current = '';
      continue;
    }
    current += character;
  }
  parts.push(current);
  return parts;
}

/** 解析单条 ports 映射；无法识别时返回 null 而不抱错，便于扫描异构写法。 */
export function parseHostPortMapping(raw: string | number): ComposeHostPort | null {
  const text = String(raw).trim();
  if (text.length === 0) return null;
  const parts = splitPortMapping(text);
  if (parts.length > 3) return null;
  const hostIp = parts.length === 3 ? parts[0] : undefined;
  const hostToken = parts.length === 3 ? parts[1]! : parts[0]!;
  const containerToken = parts.length === 3 ? parts[2] : parts.length === 2 ? parts[1] : undefined;
  const host = parsePortSegment(hostToken);
  const container = containerToken === undefined ? undefined : parsePortSegment(containerToken!);
  const effectiveHostPort = host.literal ?? host.envDefault;
  if (Object.keys(host).length === 0) return null;
  return { raw: text, hostIp, host, container, effectiveHostPort };
}

/** 返回单个服务的全部宿主端口映射（已解析变量名与默认值）。 */
export function composeHostPorts(model: ComposeApplicationModel, serviceId: string): ComposeHostPort[] {
  const service = model.services[serviceId];
  if (!service) throw new CliError(`未知 Compose 服务: ${serviceId}`);
  const result: ComposeHostPort[] = [];
  for (const entry of service.ports ?? []) {
    const parsed = parseHostPortMapping(entry);
    if (parsed) result.push(parsed);
  }
  return result;
}

/** 汇总项目内所有可参数化的宿主端口，供并行/串行运行时注入偏移。 */
export function composePortOverrides(model: ComposeApplicationModel): Array<{ serviceId: string; envVar: string; defaultPort: number }> {
  const overrides: Array<{ serviceId: string; envVar: string; defaultPort: number }> = [];
  for (const [serviceId, service] of Object.entries(model.services)) {
    for (const entry of service.ports ?? []) {
      const parsed = parseHostPortMapping(entry);
      if (!parsed?.host.envVar || parsed.host.envDefault === undefined) continue;
      overrides.push({ serviceId, envVar: parsed.host.envVar, defaultPort: parsed.host.envDefault });
    }
  }
  return overrides.sort((left, right) => left.envVar.localeCompare(right.envVar));
}

/**
 * 生成 docker compose 项目名：统一 `omni-<label>` 前缀，仅允许小写字母、数字、点、下划线和连字符。
 * 传入 workspaceRoot 时，若目录名未包含 label，则追加目录段，以保证同一预设的并行实例互不重叠。
 */
export function composeProjectName(label: string, workspaceRoot?: string): string {
  const primary = sanitizeProjectSegment(label);
  if (primary.length === 0) throw new CliError(`无法从 ${label} 生成合法的 Compose 项目名`);
  let name = `omni-${primary}`;
  if (workspaceRoot !== undefined) {
    const directory = sanitizeProjectSegment(basename(resolve(workspaceRoot))).replace(/^omni-+/, '');
    if (directory.length > 0 && !primary.includes(directory)) name = `${name}-${directory}`;
  }
  return name.slice(0, 60);
}

function sanitizeProjectSegment(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9._-]+/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '');
}

/** 按端口偏移生成一组环境变量赋值（同项目名一起用于并行隔离）。 */
export function shiftedPortEnv(model: ComposeApplicationModel, offset: number): Record<string, string> {
  if (!Number.isInteger(offset) || offset < 0 || offset > 20000) throw new CliError(`端口偏移必须是 0..20000 的整数: ${offset}`);
  const env: Record<string, string> = {};
  for (const override of composePortOverrides(model)) {
    const shifted = override.defaultPort + offset;
    if (shifted > 65535) throw new CliError(`端口偏移后越界: ${override.envVar} ${shifted}`);
    env[override.envVar] = String(shifted);
  }
  return env;
}
