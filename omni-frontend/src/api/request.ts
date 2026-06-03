/**
 * @module request
 * @description 全局共享 Axios 实例配置模块。
 * 创建统一的 HTTP 客户端，配置基础 URL（从 VITE_API_BASE_URL 读取，默认 /api）、
 * 超时时间（15s）和凭证携带（withCredentials）。
 *
 * - 请求拦截器：自动从 Pinia userStore 读取 JWT，附加 Bearer Token 认证头。
 * - 响应拦截器：统一处理业务错误码（code !== 200 时弹出 ElMessage.error），
 *   401 状态码自动执行登出并跳转；HTTP 层错误统一展示错误消息。
 */
import axios from 'axios'
import type { AxiosInstance, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
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
    }
    return config
  },
  (error) => Promise.reject(error),
)

/**
 * 响应拦截器。
 * 统一处理业务错误码和 HTTP 错误，展示错误消息并处理认证过期。
 */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    // 业务状态码非 200，视为业务错误
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      // 401 表示认证过期，执行登出并跳转到首页
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/')
      }
      return Promise.reject(new Error(res.message))
    }
    return response
  },
  (error) => {
    // HTTP 层错误处理（网络异常、超时等）
    const message = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    // HTTP 401 表示认证过期
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    }
    return Promise.reject(error)
  },
)

export default service
