/**
 * @module router
 * 应用路由配置模块。
 * 定义静态路由表、创建 Vue Router 实例，并通过全局前置守卫实现认证拦截和动态路由加载。
 * 支持基于后端权限树的全动态菜单路由注册。
 */
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useWorkspaceStore } from '@/stores/workspace'
import type { MenuNode } from '@/api/menu'
import { menuI18nMap } from '@/constants/menu'
import { hasManagementAccess, hasSupplierPortalAccess, isAssetSelfServiceUser } from '@/utils/access'
import { getRolesFromToken } from '@/utils/jwt'
import i18n from '@/i18n'
import { APP_LANDINGS, resolveAppLanding, resolveMenuReloadTarget, safeAppRedirect } from '@/utils/navigation'
import { isDeploymentPermissionEnabled, isDeploymentRouteEnabled } from '@/utils/deployment-modules'

/** 面包屑单项：titleKey 缺失为 null，交由消费方回退 fallbackTitle，绝不伪造。 */
export interface RouteBreadcrumbItem {
  /** 该级权限编码，用于稳定标识与 i18n 查找 */
  code: string
  /** 标题 i18n 键；menuI18nMap 未登记为 null */
  titleKey: string | null
  /** 后端权限名称，作为无 i18n 时的回退显示 */
  fallbackTitle: string
}

/** 扩展 Vue Router 路由元信息类型 */
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    icon?: string
    requiresAuth?: boolean
    permissionCode?: string
    portalAccess?: boolean
    /** 所属应用（顶层菜单根）稳定 ID，供侧栏/面包屑定位模块 */
    appId?: number
    /** 所属应用权限编码（顶层根），如 base:operlog 归属 monitor */
    appCode?: string
    /** 从应用根到本页面的面包屑链，作为面包屑渲染依据 */
    breadcrumb?: RouteBreadcrumbItem[]
  }
}

/**
 * 约定式组件映射：通过 import.meta.glob 扫描 views 目录下所有 index.vue。
 * 使用 ** 通配符支持多级目录结构（如 views/system/user/index.vue）。
 */
const viewModules = import.meta.glob('../views/**/index.vue')

/**
 * 权限码到视图路径的特殊映射（当约定规则不适用时使用）。
 */
const viewOverrides: Record<string, string> = {
  'system:oauth2': '../views/oauth2-client/index.vue',
  'base:operlog': '../views/monitor/oper-log/index.vue',
}

/**
 * 根据权限码解析对应的视图组件路径。
 * 约定规则：permissionCode "system:user" -> 路径 "views/system/user/index.vue"
 * 特殊映射通过 viewOverrides 覆盖。
 * 导出供共享导航派生入口判定“真实可进入页面”，避免各组件复制解析逻辑。
 */
export function resolveViewComponent(permissionCode: string): (() => Promise<unknown>) | undefined {
  if (!isDeploymentPermissionEnabled(permissionCode)) return undefined
  // 优先使用特殊映射
  if (viewOverrides[permissionCode]) {
    return viewModules[viewOverrides[permissionCode]]
  }
  // 约定式：将 permissionCode 中的 ":" 替换为 "/"，形成目录路径
  const modulePath = permissionCode.replace(/:/g, '/')
  const key = `../views/${modulePath}/index.vue`
  return viewModules[key]
}

/**
 * 权限码到 Element Plus 图标名称的映射。
 * 导出供共享导航派生入口复用，保持图标来源单一，避免各组件各自维护副本。
 */
export const iconMap: Record<string, string> = {
  'system': 'Setting',
  'base': 'DataAnalysis',
  'job': 'Timer',
  'monitor': 'Monitor',
  'workflow': 'Guide',
  'crm': 'TrendCharts',
  'crm:overview': 'DataAnalysis',
  'crm:lead': 'User',
  'crm:customer': 'OfficeBuilding',
  'crm:contact': 'Postcard',
  'crm:opportunity': 'TrendCharts',
  'crm:activity': 'Calendar',
  'srm': 'GoodsFilled',
  'srm:overview': 'DataAnalysis',
  'srm:supplier': 'OfficeBuilding',
  'srm:evaluation': 'Document',
  'srm:risk': 'Warning',
  'srm:risk:config': 'Setting',
  'srm:portal': 'Promotion',
  'srm:portal:profile': 'Document',
  'srm:portal:evaluation': 'TrendCharts',
  'srm:invite': 'Message',
  'procurement': 'ShoppingCart',
  'procurement:overview': 'DataAnalysis',
  'procurement:material': 'Box',
  'procurement:approval-route': 'Guide',
  'procurement:requisition': 'DocumentAdd',
  'procurement:rfq': 'Tickets',
  'procurement:purchase-order': 'ShoppingBag',
  'procurement:goods-receipt': 'TakeawayBox',
  'asset': 'Coin',
  'asset:overview': 'DataAnalysis',
  'asset:asset': 'Box',
  'asset:transfer': 'Switch',
  'asset:disposal': 'Delete',
  'system:user': 'User',
  'system:role': 'UserFilled',
  'system:permission': 'Lock',
  'system:org': 'OfficeBuilding',
  'system:tenant': 'School',
  'system:oauth2': 'Key',
  'system:online': 'Monitor',
  'system:authrecord': 'Document',
  'system:auditlog': 'Document',
  'system:xssconfig': 'Filter',
  'system:config': 'Setting',
  'system:loginrecord': 'Histogram',
  'base:dict': 'Collection',
  'base:operlog': 'Tickets',
  'base:mqmessage': 'MessageBox',
  'job:user-job-type': 'Files',
  'job:system-job': 'Timer',
  'workflow:definition': 'SetUp',
  'workflow:model': 'EditPen',
  'workflow:instance': 'List',
  'workflow:stats': 'DataAnalysis',
}

/**
 * 静态路由配置表。
 * 包含无需认证的公开页面和管理后台的布局容器。
 */
const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/home/index.vue'),
    meta: { title: 'Omni-Stack', requiresAuth: false },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: 'Login', requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue'),
    meta: { title: 'Register', requiresAuth: false },
  },
  {
    path: '/portal-login',
    name: 'PortalLogin',
    component: () => import('@/views/portal-login/index.vue'),
    meta: { title: 'Supplier Portal Login', requiresAuth: false },
  },
  {
    path: '/portal-register',
    name: 'PortalRegister',
    component: () => import('@/views/portal-register/index.vue'),
    meta: { title: 'Supplier Registration', requiresAuth: false },
  },
  {
    path: '/callback',
    name: 'OAuth2Callback',
    component: () => import('@/views/callback/index.vue'),
    meta: { title: 'OAuth2 Callback', requiresAuth: false },
  },
  {
    path: '/device',
    name: 'DeviceAuth',
    component: () => import('@/views/device/index.vue'),
    meta: { title: 'Device Authorization', requiresAuth: false },
  },
  {
    path: '/device/verify',
    name: 'DeviceVerify',
    component: () => import('@/views/device/verify.vue'),
    meta: { title: 'Device Verification', requiresAuth: false },
  },
  {
    path: '/consent',
    name: 'Consent',
    component: () => import('@/views/consent/index.vue'),
    meta: { title: 'Authorization Consent', requiresAuth: false },
  },
  {
    path: '/supplier-portal',
    name: 'SupplierPortal',
    component: () => import('@/views/supplier-portal/index.vue'),
    meta: { title: 'Supplier Portal', requiresAuth: true, portalAccess: true },
  },
  {
    path: '/menu-load-error',
    name: 'MenuLoadError',
    component: () => import('@/views/error/menu-load/index.vue'),
    meta: { title: 'Menu Load Error', requiresAuth: true },
  },
  {
    path: '/admin',
    name: 'admin',
    component: () => import('@/layout/index.vue'),
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: 'Dashboard', icon: 'Odometer', requiresAuth: true },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'AdminAccessDenied',
        component: () => import('@/views/error/status/index.vue'),
        props: { statusCode: 403 },
        meta: { title: 'Access Denied', requiresAuth: true },
      },
    ],
  },
  {
    // 个人工作台：独立受保护页面，与官网公开落地页和管理控制台分离；
    // 沿用 /admin 的 403 兜底规则，未识别的工作台地址落回现行 403/404 处理。
    path: '/workspace',
    name: 'Workspace',
    component: () => import('@/views/workspace/layout.vue'),
    redirect: '/workspace/overview',
    children: [
      {
        path: 'overview',
        name: 'WorkspaceOverview',
        component: () => import('@/views/workspace/index.vue'),
        meta: { title: 'Workspace Overview', requiresAuth: true },
      },
      {
        path: 'approvals',
        name: 'WorkspaceApprovals',
        // 旧书签定位同一驾驶舱，认证与供应商边界继续由目标路由守卫处理。
        redirect: to => ({ name: 'WorkspaceOverview', query: to.query, hash: '#approvals' }),
        meta: { title: 'Workspace Approvals', requiresAuth: true },
      },
      {
        path: 'jobs',
        name: 'WorkspaceJobs',
        redirect: to => ({ name: 'WorkspaceOverview', query: to.query, hash: '#my-tasks' }),
        meta: { title: 'Workspace Jobs', requiresAuth: true },
      },
      {
        path: ':pathMatch(.*)*',
        name: 'WorkspaceAccessDenied',
        component: () => import('@/views/error/status/index.vue'),
        props: { statusCode: 403 },
        meta: { title: 'Access Denied', requiresAuth: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/status/index.vue'),
    props: { statusCode: 404 },
    meta: { title: 'Not Found', requiresAuth: false },
  },
]

// 创建路由实例，使用 HTML5 History 模式
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: staticRoutes.filter((route) => isDeploymentRouteEnabled(route.path)),
})

/**
 * 已动态注册的路由名称集合（用于避免重复注册）。
 */
const dynamicRouteNames = new Set<string>()

/**
 * 根据后端菜单树动态注册路由。
 * 仅处理 MENU 类型节点，通过约定式映射查找视图组件。
 * 每个顶层菜单即其下所有页面的所属应用，据此写入模块标识与面包屑依据。
 *
 * @param menus 菜单树节点列表
 */
export function registerDynamicRoutes(menus: MenuNode[]) {
  // 找到 /admin 路由
  const adminRoute = router.getRoutes().find((r) => r.path === '/admin')
  if (!adminRoute) return

  for (const root of menus) {
    registerMenuRoute(root, root, [])
  }
}

/** 由祖先链与当前节点构造面包屑：应用根 → 中间目录 → 本页面。 */
function buildBreadcrumb(ancestors: readonly MenuNode[], self: MenuNode): RouteBreadcrumbItem[] {
  return [...ancestors, self].map((node) => ({
    code: node.permissionCode,
    titleKey: menuI18nMap[node.permissionCode] ?? null,
    fallbackTitle: node.permissionName,
  }))
}

/**
 * 递归注册单个菜单节点及其子节点。
 *
 * @param menu 当前节点
 * @param appRoot 所属应用（顶层根节点），提供稳定 appId/appCode
 * @param ancestors 从应用根到当前节点父级的祖先链，用于面包屑
 */
function registerMenuRoute(menu: MenuNode, appRoot: MenuNode, ancestors: readonly MenuNode[]) {
  // 仅处理 MENU 类型节点（DIRECTORY 类型仅作为分组，不需要路由）
  if (menu.type === 'MENU' && !menu.permissionCode.startsWith('srm:portal:')) {
    const component = resolveViewComponent(menu.permissionCode)
    if (component && !dynamicRouteNames.has(menu.permissionCode)) {
      // 从 permissionCode 提取所有段作为路由路径（避免不同模块同名冲突）
      const routePath = menu.permissionCode.replace(/:/g, '/')
      const routeName = menu.permissionCode.replace(/:/g, '-')

      router.addRoute('admin', {
        path: routePath,
        name: routeName,
        component,
        meta: {
          title: menu.permissionName,
          icon: iconMap[menu.permissionCode] || 'Document',
          requiresAuth: true,
          permissionCode: menu.permissionCode,
          appId: appRoot.id,
          appCode: appRoot.permissionCode,
          breadcrumb: buildBreadcrumb(ancestors, menu),
        },
      })
      dynamicRouteNames.add(menu.permissionCode)
    }
  }

  // 递归处理子节点：应用根保持不变，当前节点并入祖先链
  if (menu.children && menu.children.length > 0) {
    const nextAncestors = [...ancestors, menu]
    for (const child of menu.children) {
      registerMenuRoute(child, appRoot, nextAncestors)
    }
  }
}

/**
 * 清除所有动态注册的路由（登出时调用）。
 */
export function clearDynamicRoutes() {
  for (const name of dynamicRouteNames) {
    router.removeRoute(name.replace(/:/g, '-'))
  }
  dynamicRouteNames.clear()
}

/**
 * 统一清理认证会话、权限快照和动态路由。
 *
 * @param explicit 是否由用户主动退出
 */
export function clearAuthenticatedSession(explicit = true) {
  useUserStore().logout(explicit)
  usePermissionStore().reset()
  clearDynamicRoutes()
}

/**
 * 仅回收本标签内存中的会话、权限快照与动态路由，不写共享存储。
 * 跨标签同步必须先回收旧身份再由同步入口写入新身份，避免两标签互相清理形成回声。
 */
export function clearSessionInMemory() {
  useUserStore().clearLocalSession()
  usePermissionStore().reset()
  clearDynamicRoutes()
}

/**
 * 全局路由前置守卫。
 * - 无需认证的页面：已登录用户访问登录页时重定向到仪表盘，否则放行
 * - 需要认证的页面：未登录用户重定向到首页；已登录但菜单未加载时先加载动态路由
 */
/**
 * 公开路由 name → docTitle 语言包键映射（WP-012 F10）。
 * document.title 经 i18n 解析，不再把英文字面量裸显给 en/ja/ko 用户；
 * 原 meta.title 保留供面包屑等回退使用，不删字段。
 */
const STATIC_DOC_TITLE_KEYS: Record<string, string> = {
  Home: 'home',
  Login: 'login',
  Register: 'register',
  PortalLogin: 'portalLogin',
  PortalRegister: 'portalRegister',
  OAuth2Callback: 'oauthCallback',
  DeviceAuth: 'deviceAuth',
  DeviceVerify: 'deviceVerify',
  Consent: 'consent',
  SupplierPortal: 'supplierPortal',
}

router.beforeEach(async (to) => {
  // 使用 i18n 解析页面标题（动态路由优先使用 i18n 翻译，避免数据库乱码）
  const { t } = i18n.global
  const permCode = to.meta.permissionCode as string | undefined
  const staticTitleKey = typeof to.name === 'string' ? STATIC_DOC_TITLE_KEYS[to.name] : undefined
  if (permCode && menuI18nMap[permCode]) {
    document.title = t(menuI18nMap[permCode])
  } else if (staticTitleKey) {
    document.title = t(`docTitle.${staticTitleKey}`)
  } else if (to.meta.title) {
    document.title = to.meta.title as string
  }

  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (to.meta.requiresAuth === false) {
    // 已登录用户访问登录页时重定向
    if (to.name === 'Login' && userStore.token) {
      return APP_LANDINGS.site
    } else if (to.name === 'PortalLogin' && userStore.token) {
      return APP_LANDINGS.portal
    } else {
      return true
    }
  } else if (!userStore.token) {
    const redirect = safeAppRedirect(to.fullPath)
    // 门户入口保留独立登录体验，其他受保护页面进入管理端登录。
    if (to.meta.portalAccess) {
      return { name: 'PortalLogin', query: redirect ? { redirect } : undefined }
    } else {
      return { name: 'Login', query: redirect ? { redirect } : undefined }
    }
  } else {
    // 静态门户路由同样依赖 JWT 权限，不能只依赖动态菜单加载时的初始化。
    permissionStore.initFromToken()
    const roles = getRolesFromToken(userStore.token)
    const assetSelfServiceOnly = isAssetSelfServiceUser(permissionStore.permissions, roles)
    if (permCode === 'asset:asset' && assetSelfServiceOnly) {
      document.title = t('common.assetMyAssets')
    }
    // 菜单失败降级页：目标只能来自本守卫产出的已校验值。外部伪造的 redirect 在此改写为
    // 安全落点，已是安全值时直接放行，避免在降级页上反复重定向形成导航循环。
    if (to.name === 'MenuLoadError') {
      const safeTarget = resolveAppLanding(to.query.redirect, { entry: 'console', assetSelfServiceOnly })
      if (safeTarget === to.query.redirect) return true
      return { path: safeTarget, replace: true }
    }
    // 已登录但菜单未加载：加载菜单并注册动态路由
    if (!permissionStore.menusLoaded) {
      try {
        await permissionStore.loadMenus()
        registerDynamicRoutes(permissionStore.menuTree)
        // 只传递公开地址字段，保留冷启动深链的查询参数与章节锚点。
        return resolveMenuReloadTarget(to)
      } catch {
        // 降级目标必须是已校验站内路径：原目标合法则保留，被篡改则回落到控制台落点。
        const degradeTarget = resolveAppLanding(to.fullPath, { entry: 'console', assetSelfServiceOnly })
        return { name: 'MenuLoadError', query: { redirect: degradeTarget } }
      }
    } else {
      // 菜单已加载（可能由 Home 页预加载），确保动态路由已注册
      // Home 页面会预加载菜单数据（menusLoaded=true），但不会注册动态路由，
      // 因此从 Home 导航到管理后台时，需要在此处补注册。
      registerDynamicRoutes(permissionStore.menuTree)
      const isSupplier = roles.includes('SUPPLIER')
      const hasManagementPermission = hasManagementAccess(permissionStore.permissions, roles)
      const canAccessPortal = hasSupplierPortalAccess(permissionStore.permissions, roles)

      if (to.meta.portalAccess && !canAccessPortal) {
        return APP_LANDINGS.site
      }

      // 只有门户权限的 USER/SUPPLIER 账号不得进入管理后台。
      if (to.path.startsWith('/admin') && isSupplier && !hasManagementPermission) {
        return APP_LANDINGS.portal
      }
      if (to.path.startsWith('/admin') && !hasManagementPermission) {
        return canAccessPortal ? APP_LANDINGS.portal : APP_LANDINGS.site
      }
      if (to.path === '/admin/dashboard' && assetSelfServiceOnly) {
        return APP_LANDINGS.assetSelf
      }
      return true
    }
  }
})

/**
 * 访问成功钩子：最近访问的唯一写入口。
 * 放在 afterEach 而不是 beforeEach，是因为 afterEach 只在导航被确认后才触发，且它的返回值不参与导航决策：
 * 守卫里的重定向、取消与失败会以 failure 传出，本钩子据此直接跳过，因此失败导航不会新增记录，
 * 原生 <a>、浏览器前进后退与现有守卫行为都不受影响。
 * 交给 recordVisit 的只有该路由的权限码与当前已授权菜单树，登记结果是菜单叶子的稳定 ID；
 * 认证回调、403/404 兜底与不存在路径没有 permissionCode，也不会命中菜单叶子，因此不落任何记录，
 * 完整 URL、query、hash 与令牌从不参与登记。
 *
 * @param to 已成功抵达的路由
 * @param _from 导航前的路由，登记不需要
 * @param failure 导航失败或中断原因，成功时为空
 */
router.afterEach((to, _from, failure) => {
  if (failure) return
  useWorkspaceStore().recordVisit(usePermissionStore().menuTree, to.meta.permissionCode)
})

export default router
