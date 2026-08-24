import { existsSync, readFileSync, statSync } from 'node:fs';
import { isAbsolute, relative, resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import { XMLParser } from 'fast-xml-parser';
import ts from 'typescript';
import { loadCatalog } from './catalog.js';
import { CliError } from './errors.js';
import { validateGeneratedService } from './service-generator.js';
import type { IntegrationOperation, ServiceIntegrationPlan } from './types.js';
import { readYamlFile } from './yaml.js';

const STRUCTURAL_TARGETS = {
  parentPom: 'omni-backend/pom.xml',
  gateway: 'omni-backend/omni-gateway/src/main/resources/application.yml',
  compose: 'docker-compose.yml',
  catalog: 'scaffold/catalog/modules.yaml',
  dockerfile: 'docker/backend/Dockerfile',
  menu: 'omni-frontend/src/constants/menu.ts',
  router: 'omni-frontend/src/router/index.ts',
  zhLocale: 'omni-frontend/src/locales/zh-CN.ts',
  enLocale: 'omni-frontend/src/locales/en-US.ts',
  authSeed: 'scripts/sql/seed/auth.sql',
  seedManifest: 'database/seed/manifest.yaml',
  platformChangelog: 'database/changelog/platform/db.changelog-platform.yaml',
  migrationCatalog: 'omni-backend/omni-db-migrator/src/main/java/com/omni/dbmigrator/migration/MigrationTargetCatalog.java',
} as const;

/**
 * 构建生成服务包接入当前 monorepo 的只读计划。
 *
 * <p>本函数只解析和检查，不修改任何目标文件。</p>
 */
export function planServiceIntegration(
  workspaceRoot: string,
  sourceDirectory: string,
  serviceId: string,
  options: { checkGit?: boolean } = {},
): ServiceIntegrationPlan {
  const source = resolveSourceDirectory(workspaceRoot, sourceDirectory);
  const lock = validateGeneratedService(source);
  if (lock.spec.serviceId !== serviceId) {
    throw new CliError(`服务包属于 ${lock.spec.serviceId}，与命令参数 ${serviceId} 不一致`);
  }
  const operations: IntegrationOperation[] = [];
  const conflicts: string[] = [];
  const warnings: string[] = [];

  planGeneratedTrees(workspaceRoot, source, lock.spec.artifactId, serviceId, operations, conflicts);
  inspectParentPom(workspaceRoot, lock.spec.artifactId, operations, conflicts);
  inspectGateway(workspaceRoot, lock.spec.artifactId, lock.spec.apiPrefix, serviceId, operations, conflicts);
  inspectCompose(workspaceRoot, lock.spec.artifactId, operations, conflicts);
  inspectCatalog(workspaceRoot, serviceId, operations, conflicts);
  inspectDockerfile(workspaceRoot, lock.spec.artifactId, operations, conflicts);
  inspectDatabaseIntegration(
    workspaceRoot,
    serviceId,
    lock.spec.databaseName,
    source,
    operations,
    conflicts,
  );
  inspectFrontendRegistration(workspaceRoot, lock.spec.className, serviceId, operations, conflicts);
  planSeedIntegration(workspaceRoot, serviceId, operations, warnings);
  if (options.checkGit === false) warnings.push('Git 目标文件检查由测试调用方显式隔离');
  else inspectDirtyTargets(workspaceRoot, operations, conflicts);

  return {
    serviceId,
    sourceDirectory: source,
    operations,
    conflicts: [...new Set(conflicts)],
    warnings: [...new Set(warnings)],
    ready: conflicts.length === 0,
  };
}

function planGeneratedTrees(
  workspaceRoot: string,
  source: string,
  artifactId: string,
  serviceId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const entries = [
    {
      kind: 'create-directory' as const,
      source: `omni-backend/${artifactId}`,
      target: `omni-backend/${artifactId}`,
      description: '创建后端 Maven 模块',
    },
    {
      kind: 'create-file' as const,
      source: `omni-frontend/src/api/${serviceId}.ts`,
      target: `omni-frontend/src/api/${serviceId}.ts`,
      description: '创建前端 typed API',
    },
    {
      kind: 'create-directory' as const,
      source: `omni-frontend/src/views/${serviceId}`,
      target: `omni-frontend/src/views/${serviceId}`,
      description: '创建前端模块页面',
    },
    {
      kind: 'create-file' as const,
      source: `docs/${serviceId}.md`,
      target: `docs/${serviceId}.md`,
      description: '创建模块维护说明',
    },
    {
      kind: 'create-file' as const,
      source: `docs/${serviceId}-i18n-status.yaml`,
      target: `docs/${serviceId}-i18n-status.yaml`,
      description: '创建多语言完成状态',
    },
    {
      kind: 'create-file' as const,
      source: `database/changelog/${serviceId}/db.changelog-${serviceId}.yaml`,
      target: `database/changelog/${serviceId}/db.changelog-${serviceId}.yaml`,
      description: '创建服务数据库 Liquibase 主文件',
    },
  ];
  for (const entry of entries) {
    const sourcePath = resolve(source, ...entry.source.split('/'));
    const targetPath = resolve(workspaceRoot, ...entry.target.split('/'));
    if (!existsSync(sourcePath)) conflicts.push(`服务包缺少生成文件：${entry.source}`);
    if (existsSync(targetPath)) conflicts.push(`目标已存在，拒绝覆盖：${entry.target}`);
    operations.push(entry);
  }
}

function inspectParentPom(
  workspaceRoot: string,
  artifactId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const target = STRUCTURAL_TARGETS.parentPom;
  const content = readRequiredFile(workspaceRoot, target);
  const parser = new XMLParser({
    ignoreAttributes: false,
    isArray: (_name, path) => path === 'project.modules.module',
  });
  let parsed: unknown;
  try {
    parsed = parser.parse(content);
  } catch (error) {
    throw new CliError(`${target} XML 无效：${messageOf(error)}`);
  }
  const modules = readXmlModules(parsed);
  if (modules.includes(artifactId)) conflicts.push(`父 POM 已包含模块：${artifactId}`);
  operations.push({ kind: 'modify-xml', target, description: `向 Maven reactor 登记 ${artifactId}` });
}

function inspectGateway(
  workspaceRoot: string,
  artifactId: string,
  apiPrefix: string,
  serviceId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const target = STRUCTURAL_TARGETS.gateway;
  const document = asRecord(readYamlFile(resolve(workspaceRoot, target)), target);
  const spring = asRecord(document.spring, `${target}: spring`);
  const cloud = asRecord(spring.cloud, `${target}: spring.cloud`);
  const gateway = asRecord(cloud.gateway, `${target}: spring.cloud.gateway`);
  const server = asRecord(gateway.server, `${target}: gateway.server`);
  const webflux = asRecord(server.webflux, `${target}: gateway.server.webflux`);
  const routes = asArray(webflux.routes, `${target}: routes`).map((route) => asRecord(route, `${target}: route`));
  if (routes.some((route) => route.id === artifactId)) conflicts.push(`Gateway route id 已存在：${artifactId}`);
  if (routes.some((route) => asStringArray(route.predicates).includes(`Path=${apiPrefix}/**`))) {
    conflicts.push(`Gateway API Path 已存在：${apiPrefix}/**`);
  }
  operations.push({ kind: 'modify-yaml', target, description: `登记 ${artifactId} 路由并扩展内部 API 屏蔽清单` });
  if (!routes.some((route) => route.id === 'block-internal-api-services')) {
    conflicts.push('Gateway 缺少 block-internal-api-services 安全路由');
  }
  if (!apiPrefix.endsWith(serviceId)) conflicts.push(`API 前缀 ${apiPrefix} 与 service-id ${serviceId} 不一致`);
}

function inspectCompose(
  workspaceRoot: string,
  artifactId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const target = STRUCTURAL_TARGETS.compose;
  const document = asRecord(readYamlFile(resolve(workspaceRoot, target)), target);
  const services = asRecord(document.services, `${target}: services`);
  if (Object.hasOwn(services, artifactId)) conflicts.push(`Compose service 已存在：${artifactId}`);
  operations.push({ kind: 'modify-yaml', target, description: `登记 ${artifactId} 服务、依赖、环境和健康检查` });
}

function inspectCatalog(
  workspaceRoot: string,
  serviceId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const catalog = loadCatalog(workspaceRoot);
  if (catalog.modules.some((module) => module.id === serviceId)) conflicts.push(`catalog 模块已存在：${serviceId}`);
  operations.push({ kind: 'modify-yaml', target: STRUCTURAL_TARGETS.catalog, description: `登记 ${serviceId} 模块依赖和权限根` });
}

function inspectDockerfile(
  workspaceRoot: string,
  artifactId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const target = STRUCTURAL_TARGETS.dockerfile;
  const content = readRequiredFile(workspaceRoot, target);
  const expectedCopy = `COPY omni-backend/${artifactId}/pom.xml ${artifactId}/`;
  if (content.includes(expectedCopy)) conflicts.push(`Dockerfile 已包含 POM 缓存条目：${artifactId}`);
  operations.push({ kind: 'modify-dockerfile', target, description: `登记 ${artifactId} POM 缓存层` });
}

function inspectDatabaseIntegration(
  workspaceRoot: string,
  serviceId: string,
  databaseName: string,
  source: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  const generatedChangelog = `database/changelog/${serviceId}/db.changelog-${serviceId}.yaml`;
  if (!existsSync(resolve(source, ...generatedChangelog.split('/')))) {
    conflicts.push(`服务包缺少生成文件：${generatedChangelog}`);
  }

  const platform = readRequiredFile(workspaceRoot, STRUCTURAL_TARGETS.platformChangelog);
  readYamlFile(resolve(workspaceRoot, STRUCTURAL_TARGETS.platformChangelog));
  if (new RegExp(`CREATE DATABASE IF NOT EXISTS ${escapeRegExp(databaseName)}\\b`).test(platform)) {
    conflicts.push(`平台 changelog 已包含数据库：${databaseName}`);
  }
  operations.push({
    kind: 'modify-yaml',
    target: STRUCTURAL_TARGETS.platformChangelog,
    description: `登记 ${databaseName} 平台建库语句`,
  });

  const catalog = readRequiredFile(workspaceRoot, STRUCTURAL_TARGETS.migrationCatalog);
  if (catalog.includes(`target("${serviceId}",`) || catalog.includes(`"${databaseName}"`)) {
    conflicts.push(`数据库迁移目标已包含服务或数据库：${serviceId}`);
  }
  operations.push({
    kind: 'modify-java',
    target: STRUCTURAL_TARGETS.migrationCatalog,
    description: `登记 ${serviceId} Liquibase 迁移目标`,
  });

}

function inspectFrontendRegistration(
  workspaceRoot: string,
  className: string,
  serviceId: string,
  operations: IntegrationOperation[],
  conflicts: string[],
): void {
  inspectTsObject(workspaceRoot, STRUCTURAL_TARGETS.menu, 'menuI18nMap', serviceId, conflicts);
  inspectTsObject(workspaceRoot, STRUCTURAL_TARGETS.menu, 'menuI18nMap', `${serviceId}:overview`, conflicts);
  operations.push({ kind: 'modify-typescript', target: STRUCTURAL_TARGETS.menu, description: `登记 ${serviceId} 菜单翻译映射` });
  inspectTsObject(workspaceRoot, STRUCTURAL_TARGETS.router, 'iconMap', serviceId, conflicts);
  inspectTsObject(workspaceRoot, STRUCTURAL_TARGETS.router, 'iconMap', `${serviceId}:overview`, conflicts);
  operations.push({ kind: 'modify-typescript', target: STRUCTURAL_TARGETS.router, description: `登记 ${serviceId} 菜单图标` });
  inspectLocaleCommon(workspaceRoot, STRUCTURAL_TARGETS.zhLocale, `${lowerFirst(className)}Management`, conflicts);
  inspectLocaleCommon(workspaceRoot, STRUCTURAL_TARGETS.zhLocale, `${lowerFirst(className)}Overview`, conflicts);
  inspectLocaleRoot(workspaceRoot, STRUCTURAL_TARGETS.zhLocale, serviceId, conflicts);
  inspectLocaleCommon(workspaceRoot, STRUCTURAL_TARGETS.enLocale, `${lowerFirst(className)}Management`, conflicts);
  inspectLocaleCommon(workspaceRoot, STRUCTURAL_TARGETS.enLocale, `${lowerFirst(className)}Overview`, conflicts);
  inspectLocaleRoot(workspaceRoot, STRUCTURAL_TARGETS.enLocale, serviceId, conflicts);
  operations.push({ kind: 'modify-typescript', target: STRUCTURAL_TARGETS.zhLocale, description: `登记 ${serviceId} 简体中文翻译` });
  operations.push({ kind: 'modify-typescript', target: STRUCTURAL_TARGETS.enLocale, description: `登记 ${serviceId} 英文翻译` });
}

function planSeedIntegration(
  workspaceRoot: string,
  serviceId: string,
  operations: IntegrationOperation[],
  warnings: string[],
): void {
  readRequiredFile(workspaceRoot, STRUCTURAL_TARGETS.authSeed);
  readYamlFile(resolve(workspaceRoot, STRUCTURAL_TARGETS.seedManifest));
  operations.push({ kind: 'modify-sql', target: STRUCTURAL_TARGETS.authSeed, description: `按自然键登记 ${serviceId} 菜单和 SUPER_ADMIN 权限` });
  operations.push({ kind: 'modify-yaml', target: STRUCTURAL_TARGETS.seedManifest, description: '刷新 auth seed SHA-256、module 和自然键断言' });
  warnings.push('权限 SQL 必须在写入层生成自然键幂等语句；planner 不分配固定自增 ID');
}

function inspectDirtyTargets(workspaceRoot: string, operations: IntegrationOperation[], conflicts: string[]): void {
  if (!existsSync(resolve(workspaceRoot, '.git'))) return;
  const targets = [...new Set(operations.map((operation) => operation.target))];
  const result = spawnSync('git', ['status', '--porcelain', '--', ...targets], {
    cwd: workspaceRoot,
    encoding: 'utf8',
    shell: false,
  });
  if (result.error) throw new CliError(`无法检查 Git 工作区：${result.error.message}`);
  if (result.status !== 0) throw new CliError(`Git 状态检查失败：${result.stderr.trim()}`);
  for (const line of result.stdout.split(/\r?\n/).filter(Boolean)) {
    conflicts.push(`目标文件存在未提交修改：${line.slice(3)}`);
  }
}

function inspectTsObject(
  workspaceRoot: string,
  target: string,
  variableName: string,
  key: string,
  conflicts: string[],
): void {
  const source = parseTypeScript(workspaceRoot, target);
  const object = findVariableObject(source, variableName);
  if (!object) throw new CliError(`${target} 缺少对象变量 ${variableName}`);
  if (object.properties.some((property) => propertyName(property.name) === key)) {
    conflicts.push(`${target} 已登记键：${key}`);
  }
}

function inspectLocaleCommon(workspaceRoot: string, target: string, key: string, conflicts: string[]): void {
  const source = parseTypeScript(workspaceRoot, target);
  const root = findDefaultExportObject(source, target);
  const commonProperty = root.properties.find((property) => propertyName(property.name) === 'common');
  if (!commonProperty || !ts.isPropertyAssignment(commonProperty) || !ts.isObjectLiteralExpression(commonProperty.initializer)) {
    throw new CliError(`${target} 缺少 common 翻译对象`);
  }
  if (commonProperty.initializer.properties.some((property) => propertyName(property.name) === key)) {
    conflicts.push(`${target} 已登记翻译键：${key}`);
  }
}

function inspectLocaleRoot(workspaceRoot: string, target: string, key: string, conflicts: string[]): void {
  const root = findDefaultExportObject(parseTypeScript(workspaceRoot, target), target);
  if (root.properties.some((property) => propertyName(property.name) === key)) {
    conflicts.push(`${target} 已登记根翻译键：${key}`);
  }
}

function findDefaultExportObject(source: ts.SourceFile, target: string): ts.ObjectLiteralExpression {
  let root: ts.ObjectLiteralExpression | undefined;
  source.forEachChild((node) => {
    if (ts.isExportAssignment(node) && ts.isObjectLiteralExpression(node.expression)) root = node.expression;
  });
  if (!root) throw new CliError(`${target} 缺少 export default 对象`);
  return root;
}

function parseTypeScript(workspaceRoot: string, target: string): ts.SourceFile {
  const content = readRequiredFile(workspaceRoot, target);
  const source = ts.createSourceFile(target, content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const diagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics ?? [];
  if (diagnostics.length > 0) {
    throw new CliError(`${target} TypeScript 语法无效：${diagnostics[0]?.messageText.toString() ?? '未知错误'}`);
  }
  return source;
}

function findVariableObject(source: ts.SourceFile, variableName: string): ts.ObjectLiteralExpression | undefined {
  let result: ts.ObjectLiteralExpression | undefined;
  source.forEachChild((node) => {
    if (!ts.isVariableStatement(node)) return;
    for (const declaration of node.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name)
          && declaration.name.text === variableName
          && declaration.initializer
          && ts.isObjectLiteralExpression(declaration.initializer)) {
        result = declaration.initializer;
      }
    }
  });
  return result;
}

function propertyName(name: ts.PropertyName | undefined): string | undefined {
  if (!name) return undefined;
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name)) return name.text;
  return undefined;
}

function readXmlModules(value: unknown): string[] {
  const project = asRecord(asRecord(value, 'Maven XML').project, 'Maven project');
  const modules = asRecord(project.modules, 'Maven modules').module;
  return asArray(modules, 'Maven module').map((module) => {
    if (typeof module !== 'string') throw new CliError('Maven module 必须是字符串');
    return module;
  });
}

function readRequiredFile(workspaceRoot: string, target: string): string {
  const path = resolve(workspaceRoot, ...target.split('/'));
  if (!existsSync(path) || !statSync(path).isFile()) throw new CliError(`接入目标文件不存在：${target}`);
  return readFileSync(path, 'utf8');
}

function resolveSourceDirectory(workspaceRoot: string, sourceDirectory: string): string {
  const source = isAbsolute(sourceDirectory) ? resolve(sourceDirectory) : resolve(workspaceRoot, sourceDirectory);
  const workspaceRelative = relative(resolve(workspaceRoot), source);
  if (workspaceRelative === '' || workspaceRelative === '..' || workspaceRelative.startsWith(`..${sep}`)) {
    return source;
  }
  return source;
}

function asRecord(value: unknown, label: string): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) throw new CliError(`${label} 必须是对象`);
  return value as Record<string, unknown>;
}

function asArray(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) throw new CliError(`${label} 必须是数组`);
  return value;
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === 'string');
}

function lowerFirst(value: string): string {
  return `${value.charAt(0).toLowerCase()}${value.slice(1)}`;
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
