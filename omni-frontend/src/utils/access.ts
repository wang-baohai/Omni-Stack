/** 供应商账号中不构成内部管理身份的基础角色。 */
const NON_MANAGEMENT_SUPPLIER_ROLES = new Set(['USER', 'SUPPLIER'])
const ASSET_SELF_SERVICE_ROLES = new Set(['USER', 'ASSET_USER'])

/**
 * 判断当前账号是否具备管理端身份。
 *
 * USER 在入驻后会与 SUPPLIER 并存，且 USER 自带部分系统/任务/流程只读权限；
 * 因此只要包含 SUPPLIER，就必须存在第三个独立内部角色才可进入管理端。
 * 不含 SUPPLIER 的普通内部账号继续按真实权限判断，避免影响既有 USER 工作台。
 */
export function hasManagementAccess(permissions: string[], roles: string[] = []): boolean {
  if (roles.includes('SUPPLIER')) {
    return roles.some((role) => !NON_MANAGEMENT_SUPPLIER_ROLES.has(role))
  }

  return permissions.some((code) => (
    code.startsWith('system:')
    || code.startsWith('base:')
    || code.startsWith('job:')
    || code.startsWith('workflow:')
    || code.startsWith('crm:')
    || code.startsWith('srm:overview:')
    || code.startsWith('srm:supplier:')
    || code.startsWith('srm:contact:')
    || code.startsWith('srm:qualification:')
    || code.startsWith('srm:bank-account:')
    || code.startsWith('srm:evaluation:')
    || code.startsWith('srm:risk:')
    || code.startsWith('srm:owner:')
    || code.startsWith('srm:invite:')
    || code === 'srm:portal:invite'
    || code.startsWith('srm:portal:invite:')
    || code.startsWith('asset:')
  ))
}

/**
 * 判断当前账号是否仅以资产使用人身份进入管理端。
 */
export function isAssetSelfServiceUser(permissions: string[], roles: string[]): boolean {
  return roles.length > 0
    && roles.every((role) => ASSET_SELF_SERVICE_ROLES.has(role))
    && permissions.includes('asset:asset:self')
    && !permissions.includes('asset:asset:list')
}
