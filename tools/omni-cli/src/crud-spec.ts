import { existsSync } from 'node:fs';
import { isAbsolute, resolve } from 'node:path';
import { loadCatalog } from './catalog.js';
import { CliError } from './errors.js';
import { formatSchemaErrors, loadSchema } from './schema.js';
import type { CrudFieldSpec, CrudJavaType, CrudSpec } from './types.js';
import { readYamlFile } from './yaml.js';

const RESERVED_FIELDS = new Set([
  'id',
  'tenantId',
  'ownerUserId',
  'ownerUnitId',
  'version',
  'deleted',
  'createBy',
  'createTime',
  'updateBy',
  'updateTime',
]);

const SQL_RESERVED_COLUMNS = new Set(['key', 'order', 'group', 'rank', 'schema', 'table', 'user']);

const TYPE_CONTRACTS: Record<CrudJavaType, {
  typescriptType: CrudFieldSpec['typescriptType'];
  databaseTypes: CrudFieldSpec['databaseType'][];
}> = {
  String: { typescriptType: 'string', databaseTypes: ['VARCHAR', 'TEXT'] },
  Integer: { typescriptType: 'number', databaseTypes: ['INT'] },
  Long: { typescriptType: 'string', databaseTypes: ['BIGINT'] },
  Boolean: { typescriptType: 'boolean', databaseTypes: ['TINYINT'] },
  LocalDate: { typescriptType: 'string', databaseTypes: ['DATE'] },
  LocalDateTime: { typescriptType: 'string', databaseTypes: ['DATETIME'] },
  BigDecimal: { typescriptType: 'string', databaseTypes: ['DECIMAL'] },
};

/** 加载 CRUD YAML，并执行 Schema、模块和跨字段语义校验。 */
export function loadCrudSpec(workspaceRoot: string, specFile: string): CrudSpec {
  const specPath = isAbsolute(specFile) ? resolve(specFile) : resolve(workspaceRoot, specFile);
  if (!existsSync(specPath)) throw new CliError(`CRUD 声明不存在：${specPath}`);
  return validateCrudSpec(workspaceRoot, readYamlFile(specPath));
}

/** 对已解析值执行全部 CRUD 安全边界校验。 */
export function validateCrudSpec(workspaceRoot: string, value: unknown): CrudSpec {
  const validate = loadSchema(workspaceRoot, 'crud.schema.json');
  if (!validate(value)) {
    throw new CliError(`CRUD 声明 Schema 校验失败: ${formatSchemaErrors(validate.errors)}`);
  }
  const spec = value as CrudSpec;
  const module = loadCatalog(workspaceRoot).modules.find((item) => item.id === spec.moduleId);
  if (module === undefined) throw new CliError(`CRUD 声明引用未知模块：${spec.moduleId}`);
  if (!['business', 'capability'].includes(module.kind)) {
    throw new CliError(`CRUD 只能生成到业务或能力模块：${spec.moduleId} kind=${module.kind}`);
  }
  if (spec.forbiddenCapabilities.length > 0) {
    throw new CliError(`CRUD 生成器拒绝复杂能力：${spec.forbiddenCapabilities.join(', ')}；请生成服务骨架后手写领域逻辑`);
  }
  if (!spec.tableName.startsWith(spec.tablePrefix)) {
    throw new CliError(`表 ${spec.tableName} 不属于声明前缀 ${spec.tablePrefix}`);
  }
  if (!spec.apiPath.startsWith(`/api/${spec.moduleId}/`)) {
    throw new CliError(`API 路径必须位于 /api/${spec.moduleId}/ 下`);
  }
  if (!spec.permissionResource.startsWith(`${spec.moduleId}:`)) {
    throw new CliError(`权限资源必须以 ${spec.moduleId}: 开头`);
  }
  if (!spec.menuParent.startsWith(`${spec.moduleId}:`)) {
    throw new CliError(`菜单父权限必须属于 ${spec.moduleId} 模块`);
  }
  if (!spec.roleCodes.includes('SUPER_ADMIN')) {
    throw new CliError('CRUD 权限至少必须绑定 SUPER_ADMIN，避免生成不可管理的菜单孤儿');
  }
  if (spec.dataScope && (!spec.tenant || spec.owner === 'none')) {
    throw new CliError('启用 DataScope 时必须启用 tenant 并声明 user/unit owner');
  }

  validateFields(spec);
  validateKeys(spec);
  if (spec.statusToggle) {
    const status = spec.fields.find((field) => field.name === 'status');
    if (status === undefined || status.control !== 'select' || status.dictionaryTypeCode === undefined) {
      throw new CliError('启用 statusToggle 时必须声明带字典的 status select 字段');
    }
  }
  return spec;
}

function validateFields(spec: CrudSpec): void {
  const fieldNames = new Set<string>();
  const columnNames = new Set<string>();
  const i18nKeys = new Set<string>();
  for (const field of spec.fields) {
    if (RESERVED_FIELDS.has(field.name)) throw new CliError(`字段 ${field.name} 由生成器管理，不能重复声明`);
    if (SQL_RESERVED_COLUMNS.has(field.columnName)) throw new CliError(`列名 ${field.columnName} 是保留字`);
    ensureUnique(fieldNames, field.name, '字段名');
    ensureUnique(columnNames, field.columnName, '列名');
    ensureUnique(i18nKeys, field.i18nKey, 'i18n key');
    validateTypeContract(field);
    validatePresentation(field);
    validatePii(field);
  }
}

function validateTypeContract(field: CrudFieldSpec): void {
  const contract = TYPE_CONTRACTS[field.javaType];
  if (field.typescriptType !== contract.typescriptType || !contract.databaseTypes.includes(field.databaseType)) {
    throw new CliError(`字段 ${field.name} 类型映射不安全：${field.javaType}/${field.typescriptType}/${field.databaseType}`);
  }
  if (field.databaseType === 'VARCHAR' && field.length === undefined) {
    throw new CliError(`VARCHAR 字段 ${field.name} 必须声明 length`);
  }
  if (field.javaType === 'BigDecimal') {
    if (field.decimalAsString !== true || field.decimalPrecision === undefined || field.decimalScale === undefined
        || field.decimalScale >= field.decimalPrecision) {
      throw new CliError(`Decimal 字段 ${field.name} 必须按字符串传输，并声明 scale < precision`);
    }
  } else if (field.decimalAsString !== undefined || field.decimalPrecision !== undefined || field.decimalScale !== undefined) {
    throw new CliError(`非 Decimal 字段 ${field.name} 不能声明 Decimal 参数`);
  }
  if (field.javaType === 'LocalDateTime' && field.dateTimeFormat !== 'yyyy-MM-dd HH:mm:ss') {
    throw new CliError(`LocalDateTime 字段 ${field.name} 必须使用 yyyy-MM-dd HH:mm:ss`);
  }
  if (field.javaType !== 'LocalDateTime' && field.dateTimeFormat !== undefined) {
    throw new CliError(`非 LocalDateTime 字段 ${field.name} 不能声明 dateTimeFormat`);
  }
}

function validatePresentation(field: CrudFieldSpec): void {
  if (field.query !== (field.queryOperator !== 'none')) {
    throw new CliError(`字段 ${field.name} 的 query 与 queryOperator 不一致`);
  }
  if (field.queryOperator === 'like' && field.javaType !== 'String') {
    throw new CliError(`字段 ${field.name} 只有 String 可使用 like 查询`);
  }
  if (field.queryOperator === 'between' && !['Integer', 'Long', 'LocalDate', 'LocalDateTime', 'BigDecimal'].includes(field.javaType)) {
    throw new CliError(`字段 ${field.name} 不支持 between 查询`);
  }
  if (field.control === 'select' && field.dictionaryTypeCode === undefined) {
    throw new CliError(`select 字段 ${field.name} 必须声明 dictionaryTypeCode`);
  }
  if (field.control !== 'select' && field.dictionaryTypeCode !== undefined) {
    throw new CliError(`非 select 字段 ${field.name} 不能声明 dictionaryTypeCode`);
  }
}

function validatePii(field: CrudFieldSpec): void {
  if (field.pii === 'none') {
    if (field.maskStrategy !== undefined && field.maskStrategy !== 'none') {
      throw new CliError(`非 PII 字段 ${field.name} 不能声明掩码策略`);
    }
    return;
  }
  if (field.maskStrategy === undefined || field.maskStrategy === 'none') {
    throw new CliError(`PII 字段 ${field.name} 必须显式声明 maskStrategy，生成器不会推断`);
  }
}

function validateKeys(spec: CrudSpec): void {
  const available = new Set(spec.fields.map((field) => field.name));
  ['id', 'createTime', 'updateTime'].forEach((field) => available.add(field));
  if (spec.tenant) available.add('tenantId');
  if (spec.owner === 'user' || spec.owner === 'user-and-unit') available.add('ownerUserId');
  if (spec.owner === 'unit' || spec.owner === 'user-and-unit') available.add('ownerUnitId');
  if (spec.optimisticLock) available.add('version');
  if (spec.logicalDelete) available.add('deleted');

  const names = new Set<string>();
  for (const key of [...spec.uniqueConstraints, ...spec.indexes]) {
    ensureUnique(names, key.name, '索引/约束名');
    for (const field of key.fields) {
      if (!available.has(field)) throw new CliError(`${key.name} 引用未知字段：${field}`);
    }
  }
  for (const constraint of spec.uniqueConstraints) {
    if (spec.tenant && !constraint.fields.includes('tenantId')) {
      throw new CliError(`租户表唯一约束 ${constraint.name} 必须包含 tenantId`);
    }
  }
  if (!available.has(spec.defaultSort.field)) {
    throw new CliError(`默认排序引用未知字段：${spec.defaultSort.field}`);
  }
}

function ensureUnique(values: Set<string>, value: string, label: string): void {
  if (values.has(value)) throw new CliError(`${label}重复：${value}`);
  values.add(value);
}
