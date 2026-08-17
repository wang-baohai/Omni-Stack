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
  /** 显式登出进行中；用于忽略已发出请求随后到达的 401。 */
  const isLoggingOut = ref(false)
  /** 是否已登录（计算属性，基于 token 是否存在） */
  const isLoggedIn = computed(() => !!token.value)

  /**
   * 设置 JWT 令牌并持久化到 localStorage。
   *
   * @param newToken 新的 JWT 令牌
   */
  function setToken(newToken: string) {
    isLoggingOut.value = false
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

  /** 登出：清除令牌和用户名；动态路由及权限由路由层统一回收。 */
  function logout(explicit = true) {
    isLoggingOut.value = explicit
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  return { token, username, isLoggedIn, isLoggingOut, setToken, setUsername, logout }
})
