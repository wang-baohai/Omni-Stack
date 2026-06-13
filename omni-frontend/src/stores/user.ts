import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

/**
 * 用户状态管理 Store。
 * 管理 JWT 令牌、用户名和登录状态，通过 localStorage 实现持久化。
 */
export const useUserStore = defineStore('user', () => {
  /** JWT 访问令牌，从 localStorage 恢复 */
  const token = ref<string>(localStorage.getItem('token') || '')
  /** 当前用户名，从 localStorage 恢复 */
  const username = ref<string>(localStorage.getItem('username') || '')
  /** 是否已登录（计算属性，基于 token 是否存在） */
  const isLoggedIn = computed(() => !!token.value)

  /**
   * 设置 JWT 令牌并持久化到 localStorage。
   *
   * @param newToken 新的 JWT 令牌
   */
  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  /**
   * 设置用户名并持久化到 localStorage。
   *
   * @param name 用户名
   */
  function setUsername(name: string) {
    username.value = name
    localStorage.setItem('username', name)
  }

  /** 登出：清除令牌和用户名，同时清除 localStorage 中的对应数据 */
  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    // 清除动态路由和权限状态
    import('@/router').then(({ clearDynamicRoutes }) => clearDynamicRoutes())
    import('@/stores/permission').then(({ usePermissionStore }) => {
      usePermissionStore().reset()
    })
  }

  return { token, username, isLoggedIn, setToken, setUsername, logout }
})
