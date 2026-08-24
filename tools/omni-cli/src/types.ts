/** 租户初始化执行方式。 */
export type TenantProvisioningMode = 'local' | 'event' | 'none';

/** 当前与 Auth 兼容的模块清单定义。 */
export interface ModuleDefinition {
  id: string;
  kind: 'foundation' | 'capability' | 'business' | 'infrastructure';
  dependencies: string[];
  tenantProvisioning: TenantProvisioningMode;
  permissionRoots?: string[];
  provisioningSeedIds: string[];
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

/** 服务包接入操作类型。 */
export type IntegrationOperationKind = 'create-directory' | 'create-file' | 'modify-xml' | 'modify-yaml'
  | 'modify-typescript' | 'modify-sql' | 'modify-dockerfile';

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
