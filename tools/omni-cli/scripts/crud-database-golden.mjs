// 黄金 CRUD 数据库回归：自举一次性隔离 MySQL（omni-wp011-crud-db-<8hex>），
// 不依赖任何共享容器；结束按登记的完整容器 ID 定点 stop+rm，命名卷保留。
import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { parseDocument } from 'yaml';
import { renderCrudGeneration } from '../dist/src/crud-generator.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const PROJECT_PATTERN = /^omni-wp011-crud-db-[a-z0-9]{8}$/;
const keepRequested = process.argv.includes('--keep');

// ---- 资源名守卫：任何不匹配 omni-wp011-crud-db-* 前缀的名字立即抛错 ----
function assertIsolationName(name) {
  if (typeof name !== 'string' || !PROJECT_PATTERN.test(name)) {
    throw new Error(`拒绝操作非 WP011 隔离资源：${String(name)}`);
  }
  return name;
}

// 容器/卷名允许在 project 名基础上加后缀，但必须以本 project 为前缀。
function assertResourceName(name, project) {
  assertIsolationName(project);
  if (typeof name !== 'string' || (name !== project && !name.startsWith(`${project}-`) && !name.startsWith(`${project}_`))) {
    throw new Error(`拒绝操作非本 case 前缀的资源：${String(name)}`);
  }
  return name;
}

// ---- owner guard：随机 suffix，卷/容器名冲突则换新 suffix，绝不复用他人资源 ----
function reserveIsolation() {
  for (let attempt = 0; attempt < 32; attempt += 1) {
    const suffix = randomBytes(4).toString('hex');
    const project = `omni-wp011-crud-db-${suffix}`;
    assertIsolationName(project);
    const volume = `${project}_mysql`;
    const container = `${project}-mysql`;
    const volumeList = dockerSafe(['volume', 'ls', '--filter', `name=^${volume}$`, '--format', '{{.Name}}']);
    if (volumeList.stdout.split(/\r?\n/).map((line) => line.trim()).includes(volume)) continue;
    const containerInspect = spawnSync('docker', ['inspect', container], { encoding: 'utf8' });
    if (containerInspect.status === 0) continue;
    return { project, volume, container };
  }
  throw new Error('无法预留未被占用的 omni-wp011-crud-db-* 隔离资源名');
}

function dockerSafe(args) {
  return spawnSync('docker', args, { encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
}

const isolation = reserveIsolation();
const authDatabase = `omni_crud_auth_${isolation.project.slice(-8)}`;
const procurementDatabase = `omni_crud_proc_${isolation.project.slice(-8)}`;
let containerId;

try {
  containerId = bootstrapMysql(isolation);
  console.log(`wp011 crud-db isolation: project=${isolation.project} container=${containerId} volume=${isolation.volume}（卷保留，口令不出宿主）`);

  adminSql(`CREATE DATABASE \`${authDatabase}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nCREATE DATABASE \`${procurementDatabase}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`);
  databaseSql(authDatabase, authBootstrap());

  const rendered = renderCrudGeneration(workspaceRoot, 'scaffold/specs/material-brand.yaml');
  const byTarget = new Map(rendered.changes.map((change) => [change.target, change.after]));
  const ddlDocument = parseDocument(required(
    byTarget,
    'database/changelog/procurement/generated-material-brand-0001.yaml',
  )).toJS();
  const ddl = ddlDocument.databaseChangeLog?.[0]?.changeSet?.changes?.[0]?.sql?.sql;
  assert.equal(typeof ddl, 'string', '黄金 changeSet 必须包含可执行 SQL');
  databaseSql(procurementDatabase, ddl);

  const seed = required(byTarget, 'scripts/sql/seed/procurement-material-brand-permissions.sql');
  databaseSql(authDatabase, seed);
  databaseSql(authDatabase, seed);

  assert.equal(queryScalar(procurementDatabase, `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${procurementDatabase}' AND table_name='proc_material_brand'`), '1');
  assert.equal(queryScalar(procurementDatabase, `SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${procurementDatabase}' AND table_name='proc_material_brand' AND column_name='brand_code_active_guard' AND extra LIKE '%STORED GENERATED%'`), '1');
  assert.equal(queryScalar(authDatabase, "SELECT COUNT(*) FROM sys_permission WHERE permission_code IN ('procurement:material-brand','procurement:material-brand:list','procurement:material-brand:create','procurement:material-brand:update','procurement:material-brand:delete')"), '5');
  assert.equal(queryScalar(authDatabase, "SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role role ON role.id=rp.role_id JOIN sys_permission permission ON permission.id=rp.permission_id WHERE role.role_code='SUPER_ADMIN' AND permission.permission_code LIKE 'procurement:material-brand%'"), '5');

  databaseSql(procurementDatabase, "INSERT INTO proc_material_brand (tenant_id,brand_code,brand_name,status,version,deleted) VALUES (1,'BRAND-A','品牌 A',1,0,0);");
  assertSqlFails(procurementDatabase, "INSERT INTO proc_material_brand (tenant_id,brand_code,brand_name,status,version,deleted) VALUES (1,'BRAND-A','重复品牌',1,0,0);");
  databaseSql(procurementDatabase, "UPDATE proc_material_brand SET deleted=1 WHERE tenant_id=1 AND brand_code='BRAND-A' AND deleted=0; INSERT INTO proc_material_brand (tenant_id,brand_code,brand_name,status,version,deleted) VALUES (1,'BRAND-A','品牌 A2',1,0,0); UPDATE proc_material_brand SET deleted=1 WHERE tenant_id=1 AND brand_code='BRAND-A' AND deleted=0; INSERT INTO proc_material_brand (tenant_id,brand_code,brand_name,status,version,deleted) VALUES (1,'BRAND-A','品牌 A3',1,0,0); INSERT INTO proc_material_brand (tenant_id,brand_code,brand_name,status,version,deleted) VALUES (2,'BRAND-A','租户二品牌',1,0,0);");
  assert.equal(queryScalar(procurementDatabase, "SELECT COUNT(*) FROM proc_material_brand WHERE tenant_id=1 AND brand_code='BRAND-A'"), '3');
  assert.equal(queryScalar(procurementDatabase, "SELECT COUNT(*) FROM proc_material_brand WHERE tenant_id=2 AND brand_code='BRAND-A' AND deleted=0"), '1');

  console.log('golden CRUD database valid: fresh DDL=1, permissions=5, roleBindings=5, tenantIsolation=pass, logicalDeleteReuse=pass');
} finally {
  if (containerId) {
    if (!keepRequested) {
      dropVerifiedDatabases();
      teardownMysql(isolation, containerId);
    } else {
      console.log(`--keep：保留现场 container=${containerId} volume=${isolation.volume}（数据库未清理，仅供定点排查）`);
    }
  }
}

// ---- 自举一次性 MySQL：口令仅经宿主 env 注入容器内部，不落 argv/日志 ----
function bootstrapMysql({ project, volume, container }) {
  assertIsolationName(project);
  assertResourceName(container, project);
  assertResourceName(volume, project);
  const rootPassword = randomBytes(24).toString('base64url');
  // docker CLI 的 -e VAR（不带值）从宿主同名客户端 env 取值；口令只在 spawn 进程环境内，不进 argv/日志。
  const env = { ...process.env, MYSQL_ROOT_PASSWORD: rootPassword };
  const run = spawnSync('docker', [
    'run', '-d',
    '--name', container,
    '--label', `com.docker.compose.project=${project}`,
    '--label', 'com.omni.wp011=crud-db',
    // -e 不带值：docker 从宿主客户端 env 取值注入容器，口令不落 argv/日志
    '-e', 'MYSQL_ROOT_PASSWORD',
    '-v', `${volume}:/var/lib/mysql`,
    'mysql:8.4',
  ], { encoding: 'utf8', env, maxBuffer: 10 * 1024 * 1024 });
  if (run.status !== 0) throw new Error(`隔离 MySQL 启动失败：${redact(run.stderr || run.stdout)}`);
  const inspect = spawnSync('docker', ['inspect', '-f', '{{.Id}}', container], { encoding: 'utf8' });
  assert.equal(inspect.status, 0, `无法读取隔离容器完整 ID：${container}`);
  waitForMysqlReady(container);
  return inspect.stdout.trim();
}

function waitForMysqlReady(container) {
  // mysqladmin ping 在认证失败时也返回 0（access denied 即服务器已应答），且初始化临时服务阶段
  // 也会应答；必须用带口令的 SELECT 1 连续两轮成功作为就绪判据。
  const deadline = Date.now() + 240_000;
  let consecutive = 0;
  while (Date.now() < deadline) {
    const probe = mysqlExec(undefined, ['mysql', '--batch', '--skip-column-names'], 'SELECT 1;');
    if (probe.status === 0 && probe.stdout.trim() === '1') {
      consecutive += 1;
      if (consecutive >= 2) return;
    } else {
      consecutive = 0;
      const state = spawnSync('docker', ['inspect', '-f', '{{.State.Status}} {{.State.ExitCode}}', container], { encoding: 'utf8' });
      if (state.status === 0 && /exited|dead/.test(state.stdout)) {
        throw new Error(`隔离 MySQL 容器异常退出：${state.stdout.trim()}`);
      }
    }
    sleepSync(3000);
  }
  throw new Error('隔离 MySQL 就绪等待超时（240s）');
}

function teardownMysql({ project, container, volume }, id) {
  assertIsolationName(project);
  assertResourceName(container, project);
  if (!/^[0-9a-f]{12,64}$/.test(id)) throw new Error(`拒绝按非完整 ID 删除容器：${id}`);
  const ownerCheck = spawnSync('docker', ['inspect', '-f', '{{.Id}} {{index .Config.Labels "com.docker.compose.project"}}', id], { encoding: 'utf8' });
  assert.equal(ownerCheck.status, 0, `登记的容器 ID 已不存在，拒绝盲删：${id}`);
  const [actualId, actualProject] = ownerCheck.stdout.trim().split(/\s+/);
  assert.equal(actualId, id, `容器 ID 与登记不符，拒绝删除：${actualId}`);
  assert.equal(actualProject, project, `容器 project 标签与登记不符，拒绝删除：${actualProject}`);
  const stop = spawnSync('docker', ['stop', id], { encoding: 'utf8' });
  if (stop.status !== 0) throw new Error(`定点停止隔离容器失败：${redact(stop.stderr)}`);
  const rm = spawnSync('docker', ['rm', id], { encoding: 'utf8' });
  if (rm.status !== 0) throw new Error(`定点删除隔离容器失败：${redact(rm.stderr)}`);
  console.log(`wp011 crud-db teardown: 容器 ${id} 已定点停止删除，卷 ${volume} 保留`);
}

function authBootstrap() {
  return `
CREATE TABLE sys_tenant (
  id bigint NOT NULL PRIMARY KEY,
  tenant_code varchar(64) NOT NULL UNIQUE
);
CREATE TABLE sys_permission (
  id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
  tenant_id bigint NOT NULL,
  parent_id bigint NOT NULL,
  permission_code varchar(128) NOT NULL,
  permission_name varchar(128) NOT NULL,
  type varchar(16) NOT NULL,
  path varchar(512) NOT NULL DEFAULT '',
  depth int NOT NULL,
  sort int NOT NULL,
  status tinyint NOT NULL,
  create_by varchar(64),
  UNIQUE KEY uk_permission_code (tenant_id, permission_code)
);
CREATE TABLE sys_role (
  id bigint NOT NULL PRIMARY KEY,
  tenant_id bigint NOT NULL,
  role_code varchar(64) NOT NULL
);
CREATE TABLE sys_role_permission (
  role_id bigint NOT NULL,
  permission_id bigint NOT NULL,
  PRIMARY KEY (role_id, permission_id)
);
INSERT INTO sys_tenant VALUES (1, 'default');
INSERT INTO sys_permission (id,tenant_id,parent_id,permission_code,permission_name,type,path,depth,sort,status,create_by)
VALUES (1,1,0,'procurement','采购执行管理','DIRECTORY','/1/',1,1,1,'system'),
       (2,1,1,'procurement:material','物料目录','MENU','/1/2/',2,1,1,'system');
INSERT INTO sys_role VALUES (1,1,'SUPER_ADMIN');
`;
}

function queryScalar(database, statement) {
  const result = mysqlExec(database, ['mysql', '--batch', '--skip-column-names'], statement);
  assert.equal(result.status, 0, `查询失败：${statement} :: ${redact(result.stderr)}`);
  return result.stdout.trim();
}

function databaseSql(database, statement) {
  const result = mysqlExec(database, ['mysql'], statement);
  assert.equal(result.status, 0, `SQL 执行失败：database=${database} :: ${redact(result.stderr)}`);
}

function adminSql(statement) {
  const result = mysqlExec(undefined, ['mysql'], statement);
  assert.equal(result.status, 0, `管理员 SQL 执行失败 :: ${redact(result.stderr)}`);
}

function assertSqlFails(database, statement) {
  const result = mysqlExec(database, ['mysql'], statement);
  assert.notEqual(result.status, 0, '预期唯一约束拒绝重复记录，但 SQL 成功');
}

// 口令只读容器内部 env（sh -lc "$MYSQL_ROOT_PASSWORD"），宿主进程 argv 不含口令。
function mysqlExec(database, cli, input) {
  assert.ok(Array.isArray(cli) && cli.length > 0);
  const allowed = new Set(['mysql', 'mysqladmin', 'ping', '--batch', '--skip-column-names', '--silent']);
  assert.ok(cli.every((arg) => allowed.has(arg)), '拒绝不受支持的 mysql 参数');
  if (database) assert.match(database, /^omni_crud_(auth|proc)_[a-z0-9]{8}$/);
  const [tool, ...flags] = cli;
  const prefix = tool === 'mysql'
    ? '-uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4'
    : '-uroot -p"$MYSQL_ROOT_PASSWORD"';
  const command = `exec ${tool} ${prefix} ${flags.join(' ')} ${database ?? ''}`.trimEnd();
  const args = ['exec', '-i', assertContainerName(), 'sh', '-lc', command];
  return spawnSync('docker', args, { input, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
}

function assertContainerName() {
  assertResourceName(isolation.container, isolation.project);
  return isolation.container;
}

function dropVerifiedDatabases() {
  for (const database of [authDatabase, procurementDatabase]) {
    if (!/^omni_crud_(auth|proc)_[a-z0-9]{8}$/.test(database)) throw new Error(`拒绝清理未验证数据库：${database}`);
  }
  const result = mysqlExec(undefined, ['mysql'], `DROP DATABASE IF EXISTS \`${authDatabase}\`; DROP DATABASE IF EXISTS \`${procurementDatabase}\`;`);
  if (result.status !== 0) console.error('警告：验证库清理失败（容器即将删除，卷保留不受影响）');
}

function required(values, target) {
  const value = values.get(target);
  assert.ok(value, `缺少渲染变更：${target}`);
  return value;
}

function sleepSync(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function redact(text) {
  return String(text ?? '').replace(/MYSQL_ROOT_PASSWORD\S*/gi, 'MYSQL_ROOT_PASSWORD=[REDACTED]').slice(0, 500);
}
