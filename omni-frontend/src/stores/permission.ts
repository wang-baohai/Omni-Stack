/**
 * @module stores/permission
 * 权限与动态菜单状态管理 Store。
 * 管理用户权限列表、动态路由菜单数据，支持从后端加载权限树。
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getPermissionsFromToken, getTenantIdFromToken } from '@/utils/jwt'
import { useUserStore } from './user'
import { fetchMenuTree, type MenuNode } from '@/api/menu'

/**
 * 权限 Store。
 * 存储当前用户的权限编码列表和动态菜单树。
 */
export const usePermissionStore = defineStore('permission', () => {
  /** 用户权限编码列表（从 JWT 解析） */
  const permissions = ref<string[]>([])

  /** 动态菜单树（从后端获取） */
  const menuTree = ref<MenuNode[]>([])

  /** 是否已加载菜单 */
  const menusLoaded = ref(false)

  /** 计算属性：用户是否有指定权限 */
  const hasPermission = computed(() => {
    return (code: string) => permissions.value.includes(code)
  })

  /**
   * 从 JWT Token 初始化权限列表。
   */
  function initFromToken() {
    const userStore = useUserStore()
    if (userStore.token) {
      permissions.value = getPermissionsFromToken(userStore.token)
    } else {
      permissions.value = []
    }
  }

  /**
   * 从后端加载动态菜单树。
   */
  async function loadMenus() {
    const userStore = useUserStore()
    const tenantId = getTenantIdFromToken(userStore.token)
    if (!tenantId) {
      menuTree.value = []
      menusLoaded.value = true
      return
    }

    try {
      const res = await fetchMenuTree()
      menuTree.value = res.data.data
      menusLoaded.value = true
    } catch {
      menuTree.value = []
    }
  }

  /**
   * 重置权限和菜单状态（登出时调用）。
   */
  function reset() {
    permissions.value = []
    menuTree.value = []
    menusLoaded.value = false
  }

  return {
    permissions,
    menuTree,
    menusLoaded,
    hasPermission,
    initFromToken,
    loadMenus,
    reset,
  }
})
