/** 租户初始化执行方式。 */
export type TenantProvisioningMode = 'local' | 'event' | 'none';

/** 当前与 Auth 兼容的模块清单定义。 */
export interface ModuleDefinition {
  id: string;
  artifactId: string;
  kind: 'foundation' | 'capability' | 'business' | 'infrastructure';
  version: string;
  dependencies: string[];
  optionalModules: string[];
  conflicts: string[];
  backendModules: string[];
  frontend: {
    viewGlobs: string[];
    apiGlobs: string[];
    i18nGlobs: string[];
    i18nPrefixes: string[];
  };
  gatewayRoutes: string[];
  composeServices: string[];
  database: {
    changelogs: string[];
    seedSourceIds: string[];
  };
  tenantProvisioning: TenantProvisioningMode;
  permissionRoots: string[];
  provisioningSeedIds: string[];
  nacosConfigs: string[];
  ports: number[];
  mq: { producers: string[]; consumers: string[] };
  xxl: { handlers: string[]; appNames: string[] };
  docs: string[];
  resourceHints: { minimumMemoryMb: number; recommendedMemoryMb: number };
  deprecation: { status: 'active' | 'deprecated'; since?: string; replacement?: string };
  compatibility: { java: string; node: string; notes: string[] };
}

/** 模块清单根对象。 */
export interface ModuleCatalog {
  version: string;
  modules: ModuleDefinition[];
}

/** 项目裁剪预设。 */
export interface PresetDefinition {
  id: string;
  version: string;
  displayName: string;
  description: string;
  modules: string[];
}

/** 预设解析结果。 */
export interface ResolvedPreset {
  preset: PresetDefinition;
  explicitModules: string[];
  resolvedModules: string[];
}

/** 新服务生成参数。 */
export interface ServiceSpec {
  serviceId: string;
  artifactId: string;
  javaPackage: string;
  packagePath: string;
  className: string;
  displayName: string;
  apiPrefix: string;
  servicePort: number;
  managementPort: number;
  xxlPort: number;
  databaseName: string;
  tablePrefix: string;
  enableOperLog: boolean;
  enableJob: boolean;
  enableMq: boolean;
  enableDataScope: boolean;
  dataScopeTables: string[];
}

/** create-service 原始命令参数。 */
export interface CreateServiceOptions {
  javaPackage?: string;
  displayName?: string;
  apiPrefix?: string;
  servicePort?: number;
  managementPort?: number;
  xxlPort?: number;
  databaseName?: string;
  tablePrefix?: string;
  operLog?: boolean;
  job?: boolean;
  mq?: boolean;
  dataScope?: boolean;
  dataScopeTable?: string[];
  output?: string;
  apply?: boolean;
}

/** 单个生成文件计划。 */
export interface GeneratedFile {
  path: string;
  content: string;
  sha256: string;
}

/** 新服务生成计划。 */
export interface ServiceGenerationPlan {
  targetDirectory: string;
  spec: ServiceSpec;
  files: GeneratedFile[];
}

/** 生成器锁文件。 */
export interface ServiceGenerationLock {
  generatedBy: '@omni-stack/cli';
  generatorVersion: string;
  templateVersion: string;
  spec: ServiceSpec;
  files: Array<{ path: string; sha256: string }>;
}

/** CRUD 声明支持的字段类型。 */
export type CrudJavaType = 'String' | 'Integer' | 'Long' | 'Boolean' | 'LocalDate' | 'LocalDateTime' | 'BigDecimal';

/** CRUD 声明中的字段定义。 */
export interface CrudFieldSpec {
  name: string;
  columnName: string;
  comment: string;
  javaType: CrudJavaType;
  typescriptType: 'string' | 'number' | 'boolean';
  databaseType: 'VARCHAR' | 'TEXT' | 'INT' | 'BIGINT' | 'TINYINT' | 'DATE' | 'DATETIME' | 'DECIMAL';
  length?: number;
  decimalPrecision?: number;
  decimalScale?: number;
  required: boolean;
  immutable: boolean;
  list: boolean;
  query: boolean;
  form: boolean;
  detail: boolean;
  queryOperator: 'none' | 'eq' | 'like' | 'ge' | 'le' | 'between';
  pii: 'none' | 'name' | 'phone' | 'email' | 'id-card' | 'address' | 'custom';
  maskStrategy?: 'none' | 'name' | 'phone' | 'email' | 'id-card' | 'address' | 'custom';
  decimalAsString?: boolean;
  dateTimeFormat?: 'yyyy-MM-dd HH:mm:ss';
  control: 'input' | 'textarea' | 'number' | 'switch' | 'select' | 'date' | 'datetime';
  dictionaryTypeCode?: string;
  i18nKey: string;
}

/** CRUD 声明中的索引或唯一约束。 */
export interface CrudKeySpec {
  name: string;
  fields: string[];
}

/** 经 Schema 和语义校验的单表 CRUD 声明。 */
export interface CrudSpec {
  moduleId: string;
  aggregateName: string;
  displayName: string;
  aggregateMode: 'single-table';
  tableName: string;
  tablePrefix: string;
  apiPath: string;
  permissionResource: string;
  menuParent: string;
  roleCodes: string[];
  tenant: boolean;
  dataScope: boolean;
  owner: 'none' | 'user' | 'unit' | 'user-and-unit';
  optimisticLock: boolean;
  logicalDelete: boolean;
  statusToggle: boolean;
  forbiddenCapabilities: Array<'saga' | 'workflow' | 'inbox' | 'outbox' | 'multiAggregate'
    | 'piiInference' | 'complexMoney' | 'inventory' | 'overReceipt' | 'assetIdempotency'>;
  fields: CrudFieldSpec[];
  uniqueConstraints: CrudKeySpec[];
  indexes: CrudKeySpec[];
  defaultSort: { field: string; direction: 'asc' | 'desc' };
}

/** CRUD 生成器锁文件。 */
export interface CrudGenerationLock {
  generatedBy: '@omni-stack/cli';
  generatorVersion: string;
  templateVersion: string;
  specSha256: string;
  spec: CrudSpec;
  files: Array<{ path: string; sha256: string }>;
  registrations: string[];
}

/** CRUD 生成计划。 */
export interface CrudGenerationPlan {
  aggregateKey: string;
  specFile: string;
  operations: IntegrationOperation[];
  conflicts: string[];
  warnings: string[];
  ready: boolean;
  unchanged: boolean;
}

/** 在内存中渲染并校验完成的 CRUD 变更集。 */
export interface RenderedCrudGeneration {
  plan: CrudGenerationPlan;
  changes: IntegrationFileChange[];
}

/** 服务包接入操作类型。 */
export type IntegrationOperationKind = 'create-directory' | 'create-file' | 'modify-xml' | 'modify-yaml'
  | 'modify-typescript' | 'modify-java' | 'modify-sql' | 'modify-dockerfile';

/** 单个 monorepo 接入操作。 */
export interface IntegrationOperation {
  kind: IntegrationOperationKind;
  target: string;
  description: string;
  source?: string;
}

/** 服务包只读接入计划。 */
export interface ServiceIntegrationPlan {
  serviceId: string;
  sourceDirectory: string;
  operations: IntegrationOperation[];
  conflicts: string[];
  warnings: string[];
  ready: boolean;
}

/** 接入事务中的单文件变更。 */
export interface IntegrationFileChange {
  target: string;
  mode: 'create' | 'modify';
  after: string;
  before?: string;
}

/** 只在内存中构建的服务接入变更集。 */
export interface RenderedServiceIntegration {
  plan: ServiceIntegrationPlan;
  changes: IntegrationFileChange[];
}
