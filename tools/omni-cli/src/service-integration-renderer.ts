import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { XMLParser } from 'fast-xml-parser';
import ts from 'typescript';
import { parseDocument, type Document } from 'yaml';
import { CliError } from './errors.js';
import { validateCatalogGraph } from './catalog.js';
import { formatSchemaErrors, loadSchema } from './schema.js';
import { validateGeneratedService } from './service-generator.js';
import { planServiceIntegration } from './service-integration.js';
import type {
  IntegrationFileChange,
  RenderedServiceIntegration,
  ServiceGenerationLock,
  ModuleCatalog,
  ServiceSpec,
} from './types.js';

const TARGETS = {
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
} as const;

/**
 * 在内存中构建服务包接入变更并执行全部语法和契约后置校验。
 */
export function renderServiceIntegration(
  workspaceRoot: string,
  sourceDirectory: string,
  serviceId: string,
  options: { checkGit?: boolean } = {},
): RenderedServiceIntegration {
  const source = resolve(sourceDirectory);
  const lock = validateGeneratedService(source);
  const plan = planServiceIntegration(workspaceRoot, source, serviceId, options);
  if (!plan.ready) throw new CliError(`接入计划存在冲突：${plan.conflicts.join('; ')}`);
  const changes: IntegrationFileChange[] = [];

  addGeneratedFiles(workspaceRoot, source, lock, changes);
  addModified(changes, workspaceRoot, TARGETS.parentPom, (content) => renderParentPom(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.gateway, (content) => renderGateway(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.compose, (content) => renderCompose(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.catalog, (content) => renderCatalog(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.dockerfile, (content) => renderDockerfile(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.menu, (content) => renderMenu(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.router, (content) => renderRouter(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.zhLocale, (content) => renderLocale(content, lock.spec, 'zh-CN'));
  addModified(changes, workspaceRoot, TARGETS.enLocale, (content) => renderLocale(content, lock.spec, 'en-US'));
  const authSeedChange = addModified(
    changes,
    workspaceRoot,
    TARGETS.authSeed,
    (content) => renderAuthSeed(content, lock.spec),
  );
  addModified(
    changes,
    workspaceRoot,
    TARGETS.seedManifest,
    (content) => renderSeedManifest(content, lock.spec, sha256(authSeedChange.after)),
  );

  ensureUniqueTargets(changes);
  validateRenderedChanges(workspaceRoot, changes, lock.spec);
  return { plan, changes };
}

function addGeneratedFiles(
  workspaceRoot: string,
  source: string,
  lock: ServiceGenerationLock,
  changes: IntegrationFileChange[],
): void {
  const prefixes = [
    `omni-backend/${lock.spec.artifactId}/`,
    `omni-frontend/src/api/${lock.spec.serviceId}.ts`,
    `omni-frontend/src/views/${lock.spec.serviceId}/`,
    `docs/${lock.spec.serviceId}.md`,
    `docs/${lock.spec.serviceId}-i18n-status.yaml`,
  ];
  for (const file of lock.files) {
    if (!prefixes.some((prefix) => file.path === prefix || file.path.startsWith(prefix))) continue;
    const target = resolve(workspaceRoot, ...file.path.split('/'));
    if (existsSync(target)) throw new CliError(`生成目标已存在：${file.path}`);
    changes.push({
      target: file.path,
      mode: 'create',
      after: readFileSync(resolve(source, ...file.path.split('/')), 'utf8'),
    });
  }
}

function addModified(
  changes: IntegrationFileChange[],
  workspaceRoot: string,
  target: string,
  render: (content: string) => string,
): IntegrationFileChange {
  const before = readFileSync(resolve(workspaceRoot, ...target.split('/')), 'utf8');
  const after = normalizeNewlines(render(before));
  if (before.replace(/\r\n/g, '\n') === after) throw new CliError(`结构化变换没有产生变化：${target}`);
  const change: IntegrationFileChange = { target, mode: 'modify', before, after };
  changes.push(change);
  return change;
}

function renderParentPom(content: string, spec: ServiceSpec): string {
  const modules = parseMavenModules(content);
  if (modules.includes(spec.artifactId)) throw new CliError(`父 POM 已包含 ${spec.artifactId}`);
  const closingTag = '</modules>';
  const closingIndex = content.indexOf(closingTag);
  if (closingIndex < 0 || closingIndex !== content.lastIndexOf(closingTag)) throw new CliError('父 POM modules 结束标签不唯一');
  const lineStart = content.lastIndexOf('\n', closingIndex) + 1;
  const result = `${content.slice(0, lineStart)}        <module>${spec.artifactId}</module>\n${content.slice(lineStart)}`;
  if (!parseMavenModules(result).includes(spec.artifactId)) throw new CliError('父 POM 写后校验失败');
  return result;
}

function renderGateway(content: string, spec: ServiceSpec): string {
  const document = yamlDocument(content, TARGETS.gateway);
  const path = ['spring', 'cloud', 'gateway', 'server', 'webflux', 'routes'];
  document.addIn(path, {
    id: `block-internal-${spec.serviceId}`,
    uri: 'no://op',
    predicates: [`Path=/${spec.artifactId}/internal/**,/${spec.artifactId}/api/internal/**`],
    filters: ['SetStatus=404'],
  });
  document.addIn(path, {
    id: spec.artifactId,
    uri: `lb://${spec.artifactId}`,
    predicates: [`Path=${spec.apiPrefix}/**`],
  });
  return document.toString({ lineWidth: 0 });
}

function renderCompose(content: string, spec: ServiceSpec): string {
  const document = yamlDocument(content, TARGETS.compose);
  const envName = spec.serviceId.replaceAll('-', '_').toUpperCase();
  const environment: Record<string, string> = {
    TZ: 'Asia/Shanghai',
    SERVER_PORT: '8080',
    MANAGEMENT_SERVER_PORT: '8080',
    MANAGEMENT_PORT: '8080',
    SPRING_PROFILES_ACTIVE: 'default',
    NACOS_SERVER_ADDR: 'nacos:8848',
    MYSQL_URL: `jdbc:mysql://mysql:3306/${spec.databaseName}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`,
    MYSQL_USERNAME: 'omni_app',
    MYSQL_PASSWORD: '${OMNI_DB_PASSWORD:?请在 .env 中配置 OMNI_DB_PASSWORD}',
    REDIS_HOST: 'redis',
    REDIS_PASSWORD: '${REDIS_PASSWORD:?请在 .env 中配置 REDIS_PASSWORD}',
    OMNI_INTERNAL_API_TOKEN: '${OMNI_INTERNAL_API_TOKEN:?请在 .env 中配置 OMNI_INTERNAL_API_TOKEN}',
  };
  const dependsOn: Record<string, { condition: string }> = {
    nacos: { condition: 'service_healthy' },
    redis: { condition: 'service_healthy' },
    mysql: { condition: 'service_healthy' },
    'omni-db-migrator': { condition: 'service_completed_successfully' },
    'omni-auth': { condition: 'service_healthy' },
  };
  if (spec.enableMq || spec.enableOperLog) {
    environment.ROCKETMQ_NAME_SERVER = 'rocketmq-namesrv:9876';
    dependsOn['rocketmq-broker'] = { condition: 'service_healthy' };
  }
  if (spec.enableJob) {
    environment.XXL_JOB_ADMIN_ADDRESSES = 'http://xxl-job-admin:8080/xxl-job-admin';
    environment.XXL_JOB_EXECUTOR_ADDRESS = `http://${spec.artifactId}:${spec.xxlPort}/`;
    environment.XXL_JOB_EXECUTOR_IP = spec.artifactId;
    environment.XXL_JOB_EXECUTOR_PORT = String(spec.xxlPort);
    environment.XXL_JOB_ACCESS_TOKEN = '${XXL_JOB_ACCESS_TOKEN:?请在 .env 中配置 XXL_JOB_ACCESS_TOKEN}';
    dependsOn['xxl-job-admin'] = { condition: 'service_healthy' };
  }
  document.setIn(['services', spec.artifactId], {
    build: {
      context: '.',
      dockerfile: 'docker/backend/Dockerfile',
      args: { SERVICE_NAME: spec.artifactId },
    },
    image: `${spec.artifactId}:latest`,
    restart: 'unless-stopped',
    ports: [`127.0.0.1:\${OMNI_${envName}_HOST_PORT:-${spec.servicePort}}:8080`],
    environment,
    depends_on: dependsOn,
    networks: ['omni-network'],
  });
  return document.toString({ lineWidth: 0 });
}

function renderCatalog(content: string, spec: ServiceSpec): string {
  const document = yamlDocument(content, TARGETS.catalog);
  const catalog = document.toJS() as { modules?: Array<{ id?: string; provisioningSeedIds?: string[] }> };
  const authIndex = catalog.modules?.findIndex((module) => module.id === 'auth') ?? -1;
  if (authIndex < 0) throw new CliError('catalog 缺少 auth 模块');
  document.addIn(['modules', authIndex, 'provisioningSeedIds'], permissionAssertionId(spec));
  document.addIn(['modules'], {
    id: spec.serviceId,
    kind: 'business',
    dependencies: ['auth', 'base'],
    tenantProvisioning: 'none',
    permissionRoots: [spec.serviceId],
    provisioningSeedIds: [],
  });
  return document.toString({ lineWidth: 0 });
}

function renderDockerfile(content: string, spec: ServiceSpec): string {
  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const pomCopyPattern = /^COPY omni-backend\/[a-z0-9-]+\/pom\.xml [a-z0-9-]+\/$/;
  const indexes = lines.map((line, index) => (pomCopyPattern.test(line) ? index : -1)).filter((index) => index >= 0);
  if (indexes.length === 0) throw new CliError('Dockerfile 缺少后端 POM 缓存 COPY 指令');
  const line = `COPY omni-backend/${spec.artifactId}/pom.xml ${spec.artifactId}/`;
  lines.splice(indexes[indexes.length - 1]! + 1, 0, line);
  return lines.join('\n');
}

function renderMenu(content: string, spec: ServiceSpec): string {
  return insertVariableProperties(content, TARGETS.menu, 'menuI18nMap', [
    `  '${spec.serviceId}': 'common.${lowerCamel(spec)}Management',`,
    `  '${spec.serviceId}:overview': 'common.${lowerCamel(spec)}Overview',`,
  ]);
}

function renderRouter(content: string, spec: ServiceSpec): string {
  return insertVariableProperties(content, TARGETS.router, 'iconMap', [
    `  '${spec.serviceId}': 'Grid',`,
    `  '${spec.serviceId}:overview': 'DataAnalysis',`,
  ]);
}

function renderLocale(content: string, spec: ServiceSpec, locale: 'zh-CN' | 'en-US'): string {
  let result = insertLocaleCommon(content, locale === 'zh-CN' ? TARGETS.zhLocale : TARGETS.enLocale, [
    `    ${lowerCamel(spec)}Management: '${sqlSafeText(spec.displayName)}',`,
    `    ${lowerCamel(spec)}Overview: '${sqlSafeText(locale === 'zh-CN' ? `${spec.displayName}概览` : `${spec.displayName} Overview`)}',`,
  ]);
  const target = locale === 'zh-CN' ? TARGETS.zhLocale : TARGETS.enLocale;
  const source = parseTypeScript(result, target);
  const root = findDefaultExportObject(source, target);
  const brace = root.getEnd() - 1;
  const lineStart = result.lastIndexOf('\n', brace) + 1;
  const labels = locale === 'zh-CN'
    ? ['服务标识', '接入状态', '暂无模块状态']
    : ['Service ID', 'Integration status', 'No module status available'];
  const property = [
    `  '${spec.serviceId}': {`,
    `    title: '${sqlSafeText(spec.displayName)}',`,
    `    serviceId: '${labels[0]}',`,
    `    status: '${labels[1]}',`,
    `    empty: '${labels[2]}',`,
    '  },',
  ].join('\n');
  result = `${result.slice(0, lineStart)}${property}\n${result.slice(lineStart)}`;
  parseTypeScript(result, target);
  return result;
}

function renderAuthSeed(content: string, spec: ServiceSpec): string {
  const serviceCode = sqlLiteral(spec.serviceId);
  const overviewCode = sqlLiteral(`${spec.serviceId}:overview`);
  const readCode = sqlLiteral(`${spec.serviceId}:read`);
  const display = sqlLiteral(spec.displayName);
  const block = `
-- generated-by: @omni-stack/cli；${spec.displayName}模块权限模板（自然键幂等）
INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, 0, '${serviceCode}', '${display}', 'DIRECTORY', '', 1, 50, 1, 'system'
FROM sys_tenant tenant
WHERE tenant.tenant_code = 'default'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.tenant_id = tenant.id AND existing.permission_code = '${serviceCode}'
  );

UPDATE sys_permission permission
JOIN sys_tenant tenant ON tenant.id = permission.tenant_id AND tenant.tenant_code = 'default'
SET permission.path = CONCAT('/', permission.id, '/')
WHERE permission.permission_code = '${serviceCode}';

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, parent.id, '${overviewCode}', '${display}概览', 'MENU', '', 2, 1, 1, 'system'
FROM sys_tenant tenant
JOIN sys_permission parent ON parent.tenant_id = tenant.id AND parent.permission_code = '${serviceCode}'
WHERE tenant.tenant_code = 'default'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.tenant_id = tenant.id AND existing.permission_code = '${overviewCode}'
  );

UPDATE sys_permission permission
JOIN sys_tenant tenant ON tenant.id = permission.tenant_id AND tenant.tenant_code = 'default'
JOIN sys_permission parent ON parent.id = permission.parent_id AND parent.tenant_id = permission.tenant_id
SET permission.path = CONCAT(parent.path, permission.id, '/')
WHERE permission.permission_code = '${overviewCode}';

INSERT INTO sys_permission
    (tenant_id, parent_id, permission_code, permission_name, type, path, depth, sort, status, create_by)
SELECT tenant.id, parent.id, '${readCode}', '查看${display}', 'API', '', 3, 1, 1, 'system'
FROM sys_tenant tenant
JOIN sys_permission parent ON parent.tenant_id = tenant.id AND parent.permission_code = '${overviewCode}'
WHERE tenant.tenant_code = 'default'
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission existing
      WHERE existing.tenant_id = tenant.id AND existing.permission_code = '${readCode}'
  );

UPDATE sys_permission permission
JOIN sys_tenant tenant ON tenant.id = permission.tenant_id AND tenant.tenant_code = 'default'
JOIN sys_permission parent ON parent.id = permission.parent_id AND parent.tenant_id = permission.tenant_id
SET permission.path = CONCAT(parent.path, permission.id, '/')
WHERE permission.permission_code = '${readCode}';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.tenant_id = role.tenant_id
WHERE role.tenant_id = (SELECT id FROM sys_tenant WHERE tenant_code = 'default')
  AND role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code IN ('${serviceCode}', '${overviewCode}', '${readCode}')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );
`;
  return `${content.replace(/\s*$/, '')}\n${block}`;
}

function renderSeedManifest(content: string, spec: ServiceSpec, authSeedSha: string): string {
  const document = yamlDocument(content, TARGETS.seedManifest);
  const root = document.toJS() as {
    sources?: Array<{ id?: string }>;
    assertions?: Array<{ id?: string }>;
  };
  const sourceIndex = root.sources?.findIndex((source) => source.id === 'auth-bootstrap') ?? -1;
  if (sourceIndex < 0) throw new CliError('seed manifest 缺少 auth-bootstrap source');
  const assertionId = permissionAssertionId(spec);
  if (root.assertions?.some((assertion) => assertion.id === assertionId)) {
    throw new CliError(`seed assertion 已存在：${assertionId}`);
  }
  const shaNode = document.getIn(['sources', sourceIndex, 'sha256'], true) as { range?: [number, number, number] } | undefined;
  const range = shaNode?.range;
  if (!range) throw new CliError('seed manifest auth SHA 节点缺少源码范围');
  const withDigest = `${content.slice(0, range[0])}"${authSeedSha}"${content.slice(range[1])}`;
  const assertionBlock = `

  - id: ${assertionId}
    module: auth
    database: omni_auth
    query: >-
      SELECT CONCAT_WS('|', permission.permission_code, permission.type,
      COALESCE(parent.permission_code, '')) AS seed_key
      FROM sys_permission permission LEFT JOIN sys_permission parent ON parent.id = permission.parent_id
      WHERE permission.tenant_id = (SELECT id FROM sys_tenant WHERE tenant_code = 'default')
      AND (permission.permission_code = '${spec.serviceId}'
      OR permission.permission_code LIKE '${spec.serviceId}:%')
    expectedRows: 3
    expectedSha256: "${permissionAssertionSha(spec)}"
`;
  return `${withDigest.replace(/\s*$/, '')}${assertionBlock}`;
}

function validateRenderedChanges(
  workspaceRoot: string,
  changes: IntegrationFileChange[],
  spec: ServiceSpec,
): void {
  const byTarget = new Map(changes.map((change) => [change.target, change.after]));
  if (!parseMavenModules(requiredChange(byTarget, TARGETS.parentPom)).includes(spec.artifactId)) {
    throw new CliError('渲染后父 POM 未登记新模块');
  }
  for (const target of [TARGETS.gateway, TARGETS.compose, TARGETS.catalog, TARGETS.seedManifest]) {
    yamlDocument(requiredChange(byTarget, target), target);
  }
  const catalogValue = yamlDocument(requiredChange(byTarget, TARGETS.catalog), TARGETS.catalog).toJS();
  const catalogSchema = loadSchema(workspaceRoot, 'module.schema.json');
  if (!catalogSchema(catalogValue)) {
    throw new CliError(`渲染后 catalog Schema 无效：${formatSchemaErrors(catalogSchema.errors)}`);
  }
  const catalog = catalogValue as ModuleCatalog;
  validateCatalogGraph(catalog);
  for (const target of [TARGETS.menu, TARGETS.router, TARGETS.zhLocale, TARGETS.enLocale]) {
    parseTypeScript(requiredChange(byTarget, target), target);
  }
  const manifest = yamlDocument(requiredChange(byTarget, TARGETS.seedManifest), TARGETS.seedManifest).toJS() as {
    sources?: Array<{ id?: string; sha256?: string }>;
    assertions?: Array<{ id?: string; expectedSha256?: string }>;
  };
  const authSource = manifest.sources?.find((source) => source.id === 'auth-bootstrap');
  if (authSource?.sha256 !== sha256(requiredChange(byTarget, TARGETS.authSeed))) {
    throw new CliError('渲染后 auth seed SHA-256 与 manifest 不一致');
  }
  const assertion = manifest.assertions?.find((item) => item.id === permissionAssertionId(spec));
  if (assertion?.expectedSha256 !== permissionAssertionSha(spec)) {
    throw new CliError('渲染后权限断言摘要不一致');
  }
  const authModule = catalog.modules.find((module) => module.id === 'auth');
  const generatedModule = catalog.modules.find((module) => module.id === spec.serviceId);
  if (!authModule?.provisioningSeedIds.includes(permissionAssertionId(spec))) {
    throw new CliError('渲染后 Auth catalog 未引用新权限断言');
  }
  if (generatedModule?.tenantProvisioning !== 'none' || generatedModule.provisioningSeedIds.length !== 0) {
    throw new CliError('无领域默认数据的新服务必须保持 tenantProvisioning none');
  }
}

function yamlDocument(content: string, target: string): Document.Parsed {
  const document = parseDocument(content, { uniqueKeys: true });
  if (document.errors.length > 0) {
    throw new CliError(`${target} YAML 无效：${document.errors.map((error) => error.message).join('; ')}`);
  }
  return document;
}

function parseMavenModules(content: string): string[] {
  const parser = new XMLParser({
    ignoreAttributes: false,
    isArray: (_name, path) => path === 'project.modules.module',
  });
  const parsed = parser.parse(content) as { project?: { modules?: { module?: unknown[] } } };
  const modules = parsed.project?.modules?.module;
  if (!Array.isArray(modules) || !modules.every((module) => typeof module === 'string')) {
    throw new CliError('父 POM modules 结构无效');
  }
  return modules;
}

function insertVariableProperties(content: string, target: string, variableName: string, lines: string[]): string {
  const source = parseTypeScript(content, target);
  let object: ts.ObjectLiteralExpression | undefined;
  source.forEachChild((node) => {
    if (!ts.isVariableStatement(node)) return;
    for (const declaration of node.declarationList.declarations) {
      if (ts.isIdentifier(declaration.name)
          && declaration.name.text === variableName
          && declaration.initializer
          && ts.isObjectLiteralExpression(declaration.initializer)) object = declaration.initializer;
    }
  });
  if (!object) throw new CliError(`${target} 缺少对象变量 ${variableName}`);
  const lineStart = content.lastIndexOf('\n', object.getEnd() - 1) + 1;
  const result = `${content.slice(0, lineStart)}${lines.join('\n')}\n${content.slice(lineStart)}`;
  parseTypeScript(result, target);
  return result;
}

function insertLocaleCommon(content: string, target: string, lines: string[]): string {
  const root = findDefaultExportObject(parseTypeScript(content, target), target);
  const common = root.properties.find((property) => propertyName(property.name) === 'common');
  if (!common || !ts.isPropertyAssignment(common) || !ts.isObjectLiteralExpression(common.initializer)) {
    throw new CliError(`${target} 缺少 common 对象`);
  }
  const lineStart = content.lastIndexOf('\n', common.initializer.getEnd() - 1) + 1;
  const result = `${content.slice(0, lineStart)}${lines.join('\n')}\n${content.slice(lineStart)}`;
  parseTypeScript(result, target);
  return result;
}

function parseTypeScript(content: string, target: string): ts.SourceFile {
  const source = ts.createSourceFile(target, content, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const diagnostics = (source as ts.SourceFile & { parseDiagnostics?: readonly ts.Diagnostic[] }).parseDiagnostics ?? [];
  if (diagnostics.length > 0) throw new CliError(`${target} TypeScript 写后语法无效`);
  return source;
}

function findDefaultExportObject(source: ts.SourceFile, target: string): ts.ObjectLiteralExpression {
  let root: ts.ObjectLiteralExpression | undefined;
  source.forEachChild((node) => {
    if (ts.isExportAssignment(node) && ts.isObjectLiteralExpression(node.expression)) root = node.expression;
  });
  if (!root) throw new CliError(`${target} 缺少 export default 对象`);
  return root;
}

function propertyName(name: ts.PropertyName | undefined): string | undefined {
  if (!name) return undefined;
  if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name)) return name.text;
  return undefined;
}

function permissionAssertionId(spec: ServiceSpec): string {
  return `auth-${spec.serviceId}-permission-catalog`;
}

function permissionAssertionSha(spec: ServiceSpec): string {
  const values = [
    `${spec.serviceId}|DIRECTORY|`,
    `${spec.serviceId}:overview|MENU|${spec.serviceId}`,
    `${spec.serviceId}:read|API|${spec.serviceId}:overview`,
  ].map((value) => `seed_key:12=${value}`).sort();
  return sha256(values.join('\n'));
}

function ensureUniqueTargets(changes: IntegrationFileChange[]): void {
  const targets = new Set<string>();
  for (const change of changes) {
    if (targets.has(change.target)) throw new CliError(`接入变更目标重复：${change.target}`);
    targets.add(change.target);
  }
}

function requiredChange(changes: Map<string, string>, target: string): string {
  const content = changes.get(target);
  if (content === undefined) throw new CliError(`缺少渲染变更：${target}`);
  return content;
}

function lowerCamel(spec: ServiceSpec): string {
  return `${spec.className.charAt(0).toLowerCase()}${spec.className.slice(1)}`;
}

function sqlLiteral(value: string): string {
  return value.replaceAll('\\', '\\\\').replaceAll("'", "''");
}

function sqlSafeText(value: string): string {
  return value.replaceAll('\\', '\\\\').replaceAll("'", "\\'");
}

function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

function normalizeNewlines(value: string): string {
  return `${value.replace(/\r\n/g, '\n').replace(/\n*$/, '')}\n`;
}
