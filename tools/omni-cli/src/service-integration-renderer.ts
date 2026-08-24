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
  authChangelog: 'database/changelog/auth/db.changelog-auth.yaml',
  seedManifest: 'database/seed/manifest.yaml',
  platformChangelog: 'database/changelog/platform/db.changelog-platform.yaml',
  migrationCatalog: 'omni-backend/omni-db-migrator/src/main/java/com/omni/dbmigrator/migration/MigrationTargetCatalog.java',
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
  addModified(changes, workspaceRoot, TARGETS.platformChangelog, (content) => renderPlatformChangelog(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.migrationCatalog, (content) => renderMigrationCatalog(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.menu, (content) => renderMenu(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.router, (content) => renderRouter(content, lock.spec));
  addModified(changes, workspaceRoot, TARGETS.zhLocale, (content) => renderLocale(content, lock.spec, 'zh-CN'));
  addModified(changes, workspaceRoot, TARGETS.enLocale, (content) => renderLocale(content, lock.spec, 'en-US'));
  addModified(changes, workspaceRoot, TARGETS.authChangelog, (content) => renderAuthChangelog(content, lock.spec));
  const permissionSql = generatedContent(
    changes,
    `scripts/sql/seed/${lock.spec.serviceId}-permissions.sql`,
  );
  addModified(
    changes,
    workspaceRoot,
    TARGETS.seedManifest,
    (content) => renderSeedManifest(content, lock.spec, sha256(permissionSql)),
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
    `database/changelog/${lock.spec.serviceId}/`,
    `database/changelog/auth/${lock.spec.serviceId}-permissions.yaml`,
    `scripts/sql/seed/${lock.spec.serviceId}-permissions.sql`,
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
    artifactId: spec.artifactId,
    kind: 'business',
    version: '1.0.0-SNAPSHOT',
    dependencies: ['auth', 'base'],
    optionalModules: spec.enableMq ? ['rocketmq', 'xxl-job'] : spec.enableJob ? ['xxl-job'] : [],
    conflicts: [],
    backendModules: [spec.artifactId],
    frontend: {
      viewGlobs: [],
      apiGlobs: [],
      i18nGlobs: ['omni-frontend/src/locales/*.ts'],
      i18nPrefixes: [spec.serviceId],
    },
    gatewayRoutes: [spec.artifactId],
    composeServices: [spec.artifactId],
    database: {
      changelogs: [`database/changelog/${spec.serviceId}/db.changelog-${spec.serviceId}.yaml`],
      seedSourceIds: [],
    },
    tenantProvisioning: 'none',
    permissionRoots: [spec.serviceId],
    provisioningSeedIds: [],
    nacosConfigs: [`${spec.artifactId}.yml`],
    ports: [spec.servicePort, spec.managementPort, ...(spec.enableJob ? [spec.xxlPort] : [])],
    mq: { producers: spec.enableMq ? [`${spec.serviceId}-domain-event`] : [], consumers: [] },
    xxl: { handlers: spec.enableJob ? ['mqRelayHandler'] : [], appNames: spec.enableJob ? [spec.artifactId] : [] },
    docs: [`docs/${spec.serviceId}.md`, `docs/${spec.serviceId}-i18n-status.yaml`],
    resourceHints: { minimumMemoryMb: 512, recommendedMemoryMb: 768 },
    deprecation: { status: 'active' },
    compatibility: { java: '25', node: '>=22.12.0', notes: ['由 create-service 生成'] },
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

function renderPlatformChangelog(content: string, spec: ServiceSpec): string {
  const document = yamlDocument(content, TARGETS.platformChangelog);
  const root = document.toJS() as {
    databaseChangeLog?: Array<{ changeSet?: { id?: string; comment?: string; changes?: Array<{ sql?: { sql?: string } }> } }>;
  };
  const entries = root.databaseChangeLog ?? [];
  const platform = entries.find((entry) => entry.changeSet?.id === 'platform-0001-create-databases')?.changeSet;
  if (!platform?.changes?.some((change) => typeof change.sql?.sql === 'string')) {
    throw new CliError('平台 changelog 缺少原始建库 changeSet');
  }
  const changeSetId = `platform-generated-${spec.serviceId}-create-database`;
  if (entries.some((entry) => entry.changeSet?.id === changeSetId)) {
    throw new CliError(`平台 changelog 已包含 changeSet：${changeSetId}`);
  }
  if (content.match(new RegExp(`CREATE DATABASE IF NOT EXISTS ${escapeRegExp(spec.databaseName)}\\b`))) {
    throw new CliError(`平台 changelog 已包含数据库：${spec.databaseName}`);
  }
  const block = `
  - changeSet:
      id: ${changeSetId}
      author: omni-cli
      labels: adoption-upgrade
      context: platform
      runInTransaction: false
      comment: 为 ${spec.displayName} 创建独立数据库
      preConditions:
        - dbms:
            type: mysql
      changes:
        - sql:
            splitStatements: false
            stripComments: true
            sql: CREATE DATABASE IF NOT EXISTS ${spec.databaseName} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
      rollback:
        - sql:
            sql: SELECT 1;
`;
  return `${content.replace(/\s*$/, '')}\n${block}`;
}

function renderMigrationCatalog(content: string, spec: ServiceSpec): string {
  const existing = [...content.matchAll(/target\("([a-z0-9-]+)",\s*"([a-z0-9_]+)",\s*(?:true|false)\)/g)];
  if (existing.some((match) => match[1] === spec.serviceId || match[2] === spec.databaseName)) {
    throw new CliError(`迁移目标已包含服务或数据库：${spec.serviceId}`);
  }
  const anchor = '            target("nacos", "nacos_config", true),';
  if (content.split(anchor).length !== 2) throw new CliError('迁移目标目录的 nacos 锚点不唯一');
  return content
    .replace('/** 按依赖顺序排列的九个目标数据库。 */', '/** 按依赖顺序排列的目标数据库。 */')
    .replace('@return 九个目标数据库', '@return 全部目标数据库')
    .replace(anchor, `            target("${spec.serviceId}", "${spec.databaseName}", false),\n${anchor}`);
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

function renderAuthChangelog(content: string, spec: ServiceSpec): string {
  const document = yamlDocument(content, TARGETS.authChangelog);
  const root = document.toJS() as { databaseChangeLog?: Array<{ include?: { file?: string } }> };
  const generatedFile = `database/changelog/auth/${spec.serviceId}-permissions.yaml`;
  if (root.databaseChangeLog?.some((entry) => entry.include?.file === generatedFile)) {
    throw new CliError(`Auth changelog 已包含生成权限文件：${generatedFile}`);
  }
  const block = `
  - include:
      file: ${generatedFile}
`;
  return `${content.replace(/\s*$/, '')}\n${block}`;
}

function renderSeedManifest(content: string, spec: ServiceSpec, permissionSeedSha: string): string {
  const document = yamlDocument(content, TARGETS.seedManifest);
  const root = document.toJS() as {
    sources?: Array<{ id?: string }>;
    assertions?: Array<{ id?: string }>;
  };
  const sourceId = permissionSourceId(spec);
  if (root.sources?.some((source) => source.id === sourceId)) {
    throw new CliError(`seed source 已存在：${sourceId}`);
  }
  const assertionId = permissionAssertionId(spec);
  if (root.assertions?.some((assertion) => assertion.id === assertionId)) {
    throw new CliError(`seed assertion 已存在：${assertionId}`);
  }
  const sourceAnchor = '# 兼容期模块 ID 镜像；';
  if (content.split(sourceAnchor).length !== 2) throw new CliError('seed manifest sources 结束锚点不唯一');
  const sourceBlock = `  - id: ${sourceId}
    module: auth
    resource: scripts/sql/seed/${spec.serviceId}-permissions.sql
    sha256: "${permissionSeedSha}"

`;
  const withSource = content.replace(sourceAnchor, `${sourceBlock}${sourceAnchor}`);
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
  return `${withSource.replace(/\s*$/, '')}${assertionBlock}`;
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
  for (const target of [
    TARGETS.gateway,
    TARGETS.compose,
    TARGETS.catalog,
    TARGETS.seedManifest,
    TARGETS.platformChangelog,
    TARGETS.authChangelog,
  ]) {
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
  const generatedChangelog = `database/changelog/${spec.serviceId}/db.changelog-${spec.serviceId}.yaml`;
  yamlDocument(requiredChange(byTarget, generatedChangelog), generatedChangelog);
  const permissionChangelog = `database/changelog/auth/${spec.serviceId}-permissions.yaml`;
  yamlDocument(requiredChange(byTarget, permissionChangelog), permissionChangelog);
  if (!requiredChange(byTarget, TARGETS.authChangelog).includes(`file: ${permissionChangelog}`)) {
    throw new CliError('渲染后 Auth 主 changelog 未引用生成权限 changeSet');
  }
  const platformSql = requiredChange(byTarget, TARGETS.platformChangelog);
  if (!platformSql.includes(`CREATE DATABASE IF NOT EXISTS ${spec.databaseName} `)) {
    throw new CliError('渲染后平台 changelog 未创建新数据库');
  }
  const migrationCatalog = requiredChange(byTarget, TARGETS.migrationCatalog);
  if (!migrationCatalog.includes(`target("${spec.serviceId}", "${spec.databaseName}", false)`)) {
    throw new CliError('渲染后迁移目标目录未登记新服务');
  }
  const manifest = yamlDocument(requiredChange(byTarget, TARGETS.seedManifest), TARGETS.seedManifest).toJS() as {
    sources?: Array<{ id?: string; resource?: string; sha256?: string }>;
    assertions?: Array<{ id?: string; expectedSha256?: string }>;
  };
  const permissionSqlTarget = `scripts/sql/seed/${spec.serviceId}-permissions.sql`;
  const permissionSource = manifest.sources?.find((source) => source.id === permissionSourceId(spec));
  if (permissionSource?.resource !== permissionSqlTarget
      || permissionSource.sha256 !== sha256(requiredChange(byTarget, permissionSqlTarget))) {
    throw new CliError('渲染后生成权限 seed SHA-256 与 manifest 不一致');
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

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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

function permissionSourceId(spec: ServiceSpec): string {
  return `auth-${spec.serviceId}-permissions`;
}

function permissionAssertionSha(spec: ServiceSpec): string {
  const values = [
    `${spec.serviceId}|DIRECTORY|`,
    `${spec.serviceId}:overview|MENU|${spec.serviceId}`,
    `${spec.serviceId}:read|API|${spec.serviceId}:overview`,
  ].map((value) => `seed_key:12=${canonicalSeedValue(value)}`).sort();
  return sha256(values.join('\n'));
}

function canonicalSeedValue(value: string): string {
  return value
    .replaceAll('\\', '\\\\')
    .replaceAll('|', '\\|')
    .replaceAll('=', '\\=')
    .replaceAll('\r', '\\r')
    .replaceAll('\n', '\\n')
    .replaceAll('\t', '\\t');
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

function generatedContent(changes: IntegrationFileChange[], target: string): string {
  const change = changes.find((item) => item.target === target && item.mode === 'create');
  if (!change) throw new CliError(`生成包缺少接入文件：${target}`);
  return change.after;
}

function lowerCamel(spec: ServiceSpec): string {
  return `${spec.className.charAt(0).toLowerCase()}${spec.className.slice(1)}`;
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
