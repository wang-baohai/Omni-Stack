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
import { getErrorMessage } from '@/utils/errors'
import { translate } from '@/i18n'
import { isDeploymentPermissionEnabled } from '@/utils/deployment-modules'

/** 菜单加载状态。 */
export type MenuLoadState = 'idle' | 'loading' | 'loaded' | 'failed'

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

  /** 菜单加载状态，用于区分尚未加载和加载失败。 */
  const menuLoadState = ref<MenuLoadState>('idle')

  /** 最近一次菜单加载失败信息。 */
  const menuLoadError = ref<string | null>(null)

  /** 合并并发菜单请求。 */
  let menuLoadPromise: Promise<void> | null = null

  /**
   * 会话代次：每次 `reset()` 递增。
   * 在途请求启动时捕获代次，成功、失败与清理写回前都核对它；
   * 旧身份的结果因此不会回填到新会话，也不会清掉新会话正在等待的 Promise。
   */
  let sessionEpoch = 0

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
      // 共享数据库的全量授权不能代表本次精简部署包含全部模块；只缩小客户端呈现范围。
      permissions.value = getPermissionsFromToken(userStore.token)
        .filter((code) => isDeploymentPermissionEnabled(code))
    } else {
      permissions.value = []
    }
  }

  /**
   * 从后端加载动态菜单树。
   * 请求启动时捕获当前会话代次；代次变化（退出或换号）后，旧请求的全部写回都被丢弃。
   */
  function loadMenus(): Promise<void> {
    if (menuLoadState.value === 'loaded') return Promise.resolve()
    if (menuLoadPromise) return menuLoadPromise
    const epoch = sessionEpoch
    menuLoadState.value = 'loading'
    menuLoadError.value = null
    const request = performMenuLoad(epoch).finally(() => {
      // 只清理自己拥有的加载引用：reset 后新会话可能已发起另一条请求。
      if (menuLoadPromise === request) menuLoadPromise = null
    })
    menuLoadPromise = request
    return request
  }

  async function performMenuLoad(epoch: number) {
    const userStore = useUserStore()
    const tenantId = getTenantIdFromToken(userStore.token)
    if (!tenantId) {
      if (epoch === sessionEpoch) {
        menuTree.value = []
        menusLoaded.value = true
        menuLoadState.value = 'loaded'
      }
      return
    }

    try {
      const res = await fetchMenuTree()
      if (epoch !== sessionEpoch) return
      menuTree.value = res.data.data
      menusLoaded.value = true
      menuLoadState.value = 'loaded'
    } catch (error) {
      // 陈旧失败既不写失败态也不覆盖新菜单，更不复用旧守卫的降级路径。
      if (epoch !== sessionEpoch) return
      menuTree.value = []
      menusLoaded.value = true
      menuLoadState.value = 'failed'
      menuLoadError.value = getErrorMessage(error, translate('request.menuLoadFailed'))
      throw error
    }
  }

  /** 清除失败状态并重新加载菜单。 */
  async function retryLoadMenus() {
    menusLoaded.value = false
    menuLoadState.value = 'idle'
    menuLoadError.value = null
    await loadMenus()
  }

  /**
   * 重置权限和菜单状态（登出时调用）。
   * 递增会话代次使所有在途请求失效；旧 Promise 的 finally 不会再清掉新请求的引用。
   */
  function reset() {
    sessionEpoch += 1
    permissions.value = []
    menuTree.value = []
    menusLoaded.value = false
    menuLoadState.value = 'idle'
    menuLoadError.value = null
    menuLoadPromise = null
  }

  return {
    permissions,
    menuTree,
    menusLoaded,
    menuLoadState,
    menuLoadError,
    hasPermission,
    initFromToken,
    loadMenus,
    retryLoadMenus,
    reset,
  }
})
