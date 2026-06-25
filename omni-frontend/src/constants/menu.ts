/**
 * 权限编码到 i18n 翻译键的共享映射。
 * 布局侧边栏和路由守卫共同引用此映射，确保菜单名称和页面标题始终使用 i18n 翻译，
 * 避免因数据库字符集问题导致中文乱码。
 */
export const menuI18nMap: Record<string, string> = {
  'system': 'common.systemManagement',
  'system:user': 'common.users',
  'system:role': 'common.roles',
  'system:permission': 'common.permissions',
  'system:org': 'common.organizations',
  'system:tenant': 'common.tenants',
  'system:oauth2': 'common.oauth2Clients',
  'system:online': 'common.onlineUsers',
  'system:authrecord': 'common.authRecords',
  'system:auditlog': 'common.auditLogs',
  'system:xssconfig': 'common.xssConfig',
  'base': 'common.baseManagement',
  'base:dict': 'common.dictManagement',
  'base:operlog': 'common.operLogs',
  'job': 'common.jobManagement',
  'job:user-job-type': 'common.userJobTypes',
  'job:system-job': 'common.systemJobs',
}
