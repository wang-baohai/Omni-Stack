import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由配置表。
 * 每个路由通过 meta 字段定义页面标题、是否需要认证等元数据。
 */
const routes: RouteRecordRaw[] = [
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
        path: 'oauth2-clients',
        name: 'OAuth2Clients',
        component: () => import('@/views/oauth2-client/index.vue'),
        meta: { title: 'OAuth2 Clients', icon: 'Key', requiresAuth: true },
      },
    ],
  },
]

// 创建路由实例，使用 HTML5 History 模式
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

/**
 * 全局路由前置守卫。
 * - 无需认证的页面：已登录用户访问登录页时重定向到仪表盘，否则放行
 * - 需要认证的页面：未登录用户重定向到首页
 */
router.beforeEach((to, _from, next) => {
  const userStore = useUserStore()

  if (to.meta.requiresAuth === false) {
    // 无需认证的页面：如果已登录且访问登录页，重定向到仪表盘
    if (to.name === 'Login' && userStore.token) {
      next({ name: 'Dashboard' })
    } else {
      next()
    }
  } else if (!userStore.token) {
    // 需要认证但未登录，重定向到首页
    next({ name: 'Home' })
  } else {
    // 已登录，正常放行
    next()
  }
})

export default router
