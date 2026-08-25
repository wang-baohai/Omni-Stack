import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
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
