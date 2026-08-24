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
