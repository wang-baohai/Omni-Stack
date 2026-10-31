/** 本次部署可选业务模块；基础平台始终由实际组件和权限共同约束。 */
const OPTIONAL_MODULES = new Set(['workflow', 'crm', 'srm', 'procurement', 'asset'])

/** 由 Vite 按实际保留的视图目录写入；未经过构建的 Node 测试可保持未定义。 */
declare const __OMNI_PHYSICAL_MODULES__: readonly string[]

/**
 * 判断正式 CLI 构建所选模块是否包含目标模块。
 * 未设置选择的直接构建保持全量行为；生成工程仍须另有实际组件，不能借此恢复已裁文件。
 */
export function isDeploymentModuleEnabled(
  moduleId: string,
  selection: string | undefined = import.meta.env?.VITE_OMNI_MODULES,
): boolean {
  if (OPTIONAL_MODULES.has(moduleId) && typeof __OMNI_PHYSICAL_MODULES__ !== 'undefined'
    && !__OMNI_PHYSICAL_MODULES__.includes(moduleId)) return false
  if (!selection?.trim()) return true
  return selection.split(',').some((item) => item.trim() === moduleId)
}

/** 展示权限与部署范围求交，不改变令牌或服务端授权。 */
export function isDeploymentPermissionEnabled(
  permissionCode: string,
  selection: string | undefined = import.meta.env?.VITE_OMNI_MODULES,
): boolean {
  const moduleId = permissionCode.split(':')[0]
  return !OPTIONAL_MODULES.has(moduleId) || isDeploymentModuleEnabled(moduleId, selection)
}

/** 未部署 SRM 时不注册供应商专属公开入口。 */
export function isDeploymentRouteEnabled(
  routePath: string,
  selection: string | undefined = import.meta.env?.VITE_OMNI_MODULES,
): boolean {
  return !['/supplier-portal', '/portal-login', '/portal-register'].includes(routePath)
    || isDeploymentModuleEnabled('srm', selection)
}
