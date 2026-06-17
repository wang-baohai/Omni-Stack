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
import type { MenuNode } from '@/api/menu'

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
}

/**
 * 根据权限码解析对应的视图组件路径。
 * 约定规则：permissionCode "system:user" -> 路径 "views/system/user/index.vue"
 * 特殊映射通过 viewOverrides 覆盖。
 */
function resolveViewComponent(permissionCode: string): (() => Promise<unknown>) | undefined {
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
 */
const iconMap: Record<string, string> = {
  'system:user': 'User',
  'system:role': 'UserFilled',
  'system:permission': 'Lock',
  'system:org': 'OfficeBuilding',
  'system:tenant': 'School',
  'system:oauth2': 'Key',
  'system:online': 'Monitor',
  'system:authrecord': 'Document',
  'system:xssconfig': 'Filter',
  'base:dict': 'Collection',
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
    ],
  },
]

// 创建路由实例，使用 HTML5 History 模式
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: staticRoutes,
})

/**
 * 已动态注册的路由名称集合（用于避免重复注册）。
 */
const dynamicRouteNames = new Set<string>()

/**
 * 根据后端菜单树动态注册路由。
 * 仅处理 MENU 类型节点，通过约定式映射查找视图组件。
 *
 * @param menus 菜单树节点列表
 */
export function registerDynamicRoutes(menus: MenuNode[]) {
  // 找到 /admin 路由
  const adminRoute = router.getRoutes().find((r) => r.path === '/admin')
  if (!adminRoute) return

  for (const menu of menus) {
    registerMenuRoute(menu, adminRoute.name?.toString())
  }
}

/**
 * 递归注册单个菜单节点及其子节点。
 */
function registerMenuRoute(menu: MenuNode, parentName?: string) {
  // 仅处理 MENU 类型节点（DIRECTORY 类型仅作为分组，不需要路由）
  if (menu.type === 'MENU') {
    const component = resolveViewComponent(menu.permissionCode)
    if (component && !dynamicRouteNames.has(menu.permissionCode)) {
      // 从 permissionCode 提取最后一段作为路由路径
      const segments = menu.permissionCode.split(':')
      const routePath = segments[segments.length - 1]
      const routeName = menu.permissionCode.replace(/:/g, '-')

      router.addRoute('admin', {
        path: routePath,
        name: routeName,
        component,
        meta: {
          title: menu.permissionName,
          icon: iconMap[menu.permissionCode] || 'Document',
          requiresAuth: true,
        },
      })
      dynamicRouteNames.add(menu.permissionCode)
    }
  }

  // 递归处理子节点
  if (menu.children && menu.children.length > 0) {
    for (const child of menu.children) {
      registerMenuRoute(child, parentName)
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
 * 全局路由前置守卫。
 * - 无需认证的页面：已登录用户访问登录页时重定向到仪表盘，否则放行
 * - 需要认证的页面：未登录用户重定向到首页；已登录但菜单未加载时先加载动态路由
 */
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  if (to.meta.requiresAuth === false) {
    if (to.name === 'Login' && userStore.token) {
      next({ name: 'Dashboard' })
    } else {
      next()
    }
  } else if (!userStore.token) {
    next({ name: 'Home' })
  } else {
    // 已登录但菜单未加载：加载菜单并注册动态路由
    if (!permissionStore.menusLoaded) {
      try {
        permissionStore.initFromToken()
        await permissionStore.loadMenus()
        registerDynamicRoutes(permissionStore.menuTree)
        // 重新导航到目标路由（使用 fullPath 避免传递内部路由对象状态）
        next({ path: to.fullPath, replace: true })
      } catch (error) {
        console.error('路由守卫：菜单加载失败', error)
        next({ name: 'Home' })
      }
    } else {
      next()
    }
  }
})

export default router
