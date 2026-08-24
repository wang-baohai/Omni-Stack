import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { parseDocument } from 'yaml';
import { renderCrudGeneration } from '../dist/src/crud-generator.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const container = 'omni-g1-mysql-1';
const suffix = randomBytes(4).toString('hex');
const authDatabase = `omni_crud_auth_${suffix}`;
const procurementDatabase = `omni_crud_proc_${suffix}`;

try {
  assertContainer();
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
  dropVerifiedDatabases();
}

function assertContainer() {
  const result = spawnSync('docker', ['inspect', '--format', '{{.State.Running}}', container], { encoding: 'utf8' });
  assert.equal(result.status, 0, `${container} 不存在`);
  assert.equal(result.stdout.trim(), 'true', `${container} 未运行`);
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
  const result = mysql(database, ['--batch', '--skip-column-names'], statement);
  assert.equal(result.status, 0, `查询失败：${statement}`);
  return result.stdout.trim();
}

function databaseSql(database, statement) {
  const result = mysql(database, [], statement);
  assert.equal(result.status, 0, `SQL 执行失败：database=${database}`);
}

function adminSql(statement) {
  const result = mysql(undefined, [], statement);
  assert.equal(result.status, 0, '管理员 SQL 执行失败');
}

function assertSqlFails(database, statement) {
  const result = mysql(database, [], statement);
  assert.notEqual(result.status, 0, '预期唯一约束拒绝重复记录，但 SQL 成功');
}

function mysql(database, extraArgs, input) {
  assert.ok(extraArgs.every((arg) => ['--batch', '--skip-column-names'].includes(arg)), '拒绝不受支持的 mysql 参数');
  if (database) assert.match(database, /^omni_crud_(auth|proc)_[a-f0-9]{8}$/);
  const command = `exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 ${extraArgs.join(' ')} ${database ?? ''}`;
  const args = ['exec', '-i', container, 'sh', '-lc', command];
  return spawnSync('docker', args, { input, encoding: 'utf8', maxBuffer: 10 * 1024 * 1024 });
}

function dropVerifiedDatabases() {
  for (const database of [authDatabase, procurementDatabase]) {
    if (!/^omni_crud_(auth|proc)_[a-f0-9]{8}$/.test(database)) throw new Error(`拒绝清理未验证数据库：${database}`);
  }
  const result = mysql(undefined, [], `DROP DATABASE IF EXISTS \`${authDatabase}\`; DROP DATABASE IF EXISTS \`${procurementDatabase}\`;`);
  if (result.status !== 0) throw new Error('黄金数据库清理失败');
}

function required(values, target) {
  const value = values.get(target);
  assert.ok(value, `缺少渲染变更：${target}`);
  return value;
}
