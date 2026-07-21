/**
 * @module request
 * @description 全局共享 Axios 实例配置模块。
 * 创建统一的 HTTP 客户端，配置基础 URL（从 VITE_API_BASE_URL 读取，默认 /api）、
 * 超时时间（15s）和凭证携带（withCredentials）。
 *
 * - 请求拦截器：自动从 Pinia userStore 读取 JWT，附加 Bearer Token 认证头。
 * - 响应拦截器：统一处理业务错误码（code !== 200 时弹出 ElMessage.error），
 *   401 状态码弹出过期对话框并跳转登录页（携带 redirect 参数）；HTTP 层错误统一展示错误消息。
 */
import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'
import router from '@/router'

// 创建 Axios 实例，配置基础 URL 和超时时间
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  withCredentials: true,
})

/**
 * 请求拦截器。
 * 在每个请求发送前自动附加 Bearer Token 认证头。
 */
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    // 如果用户已登录，在请求头中附加 JWT 令牌
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
      const tenantId = getTenantIdFromToken(userStore.token)
      if (tenantId) {
        config.headers['X-Tenant-Id'] = String(tenantId)
      }
    }
    return config
  },
  (error) => Promise.reject(error),
)

/** 防止并发 401 弹出多个对话框的标志 */
let showingExpiredDialog = false

/**
 * 统一处理 401 认证过期。
 * 弹出对话框提示用户，确认后执行登出并跳转到登录页（携带当前页面路径作为 redirect 参数）。
 *
 * @param message 过期提示消息
 */
function handle401(message: string) {
  if (showingExpiredDialog) return
  showingExpiredDialog = true
  const userStore = useUserStore()
  userStore.logout()
  ElMessageBox.alert(message, '登录过期', {
    confirmButtonText: '重新登录',
    type: 'warning',
  }).finally(() => {
    showingExpiredDialog = false
    router.push('/')
  })
}

/**
 * 响应拦截器。
 * 统一处理业务错误码和 HTTP 错误，展示错误消息并处理认证过期。
 */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    // 业务状态码非 200，视为业务错误
    if (res.code !== 200) {
      if (res.code === 401) {
        // 401 表示认证过期，弹出过期对话框
        handle401(res.message || '登录已过期，请重新登录')
      } else {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      // HTTP 401 表示认证过期，读取后端返回的结构化消息
      const message = error.response?.data?.message || '登录已过期，请重新登录'
      handle401(message)
    } else {
      // 其他 HTTP 层错误（网络异常、超时等）
      const message = error.response?.data?.message || error.message || '网络错误'
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default service
