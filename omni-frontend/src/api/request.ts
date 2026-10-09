/**
 * @module request
 * @description 全局共享 Axios 实例配置模块。
 * 创建统一的 HTTP 客户端，配置基础 URL（从 VITE_API_BASE_URL 读取，默认 /api）、
 * 超时时间（15s）和凭证携带（withCredentials）。
 *
 * - 请求拦截器：自动从 Pinia userStore 读取 JWT，附加 Bearer Token 认证头。
 * - 响应拦截器：把 `HTTP status + R.code +（无响应时）Axios error.code` 归一化为
 *   `error-display.ts` 的描述符，再经 `translate()` 输出四语安全文案，并读取
 *   `X-Trace-Id` 作为用户可见排障凭据。
 *
 * 关键合同（`docs/api-contract.md` §2/§2.5/§8.4）：
 * - 后端 `message` 与 Axios `error.message` 永不上屏；只做码值驱动的本地化。
 * - 401：区分「会话过期」（登出 + 回跳）与「登录凭据校验失败」（保留会话与输入）。
 * - 403：保持会话，不登出、不跳登录页。
 * - 400：保留输入并给出字段级可修复提示。
 * - 409：刷新重试；500/502/503 与网络/超时：安全文案 + 重试语义。
 * - 抛出的错误对象携带 `businessCode`/`httpStatus`/`traceId`/`i18nKey`/`displayMessage`。
 */
import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'
import router, { clearAuthenticatedSession } from '@/router'
import { safeAppRedirect } from '@/utils/navigation'
import { translate } from '@/i18n'
import type { ErrorDisplay } from '@/utils/error-display'
import {
  classifyError,
  extractFieldNames,
  readBusinessCode,
  readServerMessage,
  readTraceId,
} from '@/utils/error-display'
import {
  ApiError,
  buildSignalFromAxiosError,
  readRequestContext,
  resolveDisplayText,
  translateExisting,
  withApiErrorView,
} from '@/utils/errors'

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
 * 统一处理需要「重新登录」的 401（会话过期 / 无会话未登录）。
 * 弹出对话框提示用户，确认后执行安全登出并跳转到登录页（携带当前页面路径作为 redirect 参数）。
 *
 * 仅允许 `error-display.ts` 判定为 `shouldLogout` 的描述符进入此函数：
 * 登录凭据校验失败（401）与 403/400/409/5xx 一律不得调用，以免误清会话与用户输入。
 *
 * @param display 归一化描述符
 */
function handleReauthentication(display: ErrorDisplay) {
  const userStore = useUserStore()
  if (showingExpiredDialog || userStore.isLoggingOut) return
  showingExpiredDialog = true
  const message = resolveDisplayText(display)
  // 「未登录」与「已过期」语义不同：优先取专用标题，A 侧词条未就绪时降级到现有过期标题。
  const titleKeys = display.kind === 'unauthenticated'
    ? ['request.loginRequiredTitle', 'request.loginExpiredTitle']
    : ['request.loginExpiredTitle']
  const currentPath = router.currentRoute.value.fullPath
  const redirect = safeAppRedirect(currentPath)
  const isPortal = currentPath.startsWith('/supplier-portal')
  clearAuthenticatedSession(false)
  ElMessageBox.alert(message, translateExisting(titleKeys) ?? translate('request.loginExpiredTitle'), {
    confirmButtonText: translate('request.loginAgain'),
    type: 'warning',
  }).finally(() => {
    showingExpiredDialog = false
    router.replace({
      name: isPortal ? 'PortalLogin' : 'Login',
      query: redirect ? { redirect } : undefined,
    })
  })
}

/**
 * 按描述符呈现用户可见文案。
 *
 * 需要重新登录的 401 走登出对话框；其余（含 401 凭据失败、403、400、404、409、5xx、网络/超时）
 * 只做本地化错误提示，不清会话、不改路由、不丢用户输入。
 *
 * @param display 归一化描述符
 */
function presentError(display: ErrorDisplay) {
  if (display.shouldLogout) {
    handleReauthentication(display)
    return
  }
  ElMessage.error(resolveDisplayText(display))
}

/**
 * 归一化成功响应中的业务错误（HTTP 200 + `code !== 200`）。
 *
 * @param response 成功到达的响应
 * @returns 归一化描述符
 */
function classifyBusinessError(response: AxiosResponse<ApiResponse>): ErrorDisplay {
  const data = response.data
  const serverMessage = readServerMessage(data)
  return classifyError({
    httpStatus: response.status,
    businessCode: readBusinessCode(data),
    axiosErrorCode: null,
    hasBody: data !== undefined && data !== null,
    traceId: readTraceId(response.headers),
    context: readRequestContext(response.config.url, response.config.headers),
    fieldNames: extractFieldNames(serverMessage),
  })
}

/**
 * 响应拦截器。
 * 统一按码值（HTTP status + `R.code`）分类错误、展示四语安全文案并附带 trace；
 * 后端原始 message 只作为诊断字段挂在错误对象上，不上屏。
 */
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data
    // 业务状态码非 200，视为业务错误
    if (res.code !== 200) {
      const display = classifyBusinessError(response)
      presentError(display)
      // 附加式增强：携带 businessCode/httpStatus/traceId/i18nKey 供调用方分类处理。
      return Promise.reject(new ApiError(display, readServerMessage(res)))
    }
    return response
  },
  (error: unknown) => {
    const userStore = useUserStore()
    if (userStore.isLoggingOut) return Promise.reject(error)
    if (!axios.isAxiosError(error)) {
      // 非 HTTP 层异常（如请求拦截器自身失败）：保持原样抛出，不做本地化猜测。
      ElMessage.error(translate('request.networkError'))
      return Promise.reject(error)
    }
    const display = classifyError(buildSignalFromAxiosError(error))
    presentError(display)
    // 保留 AxiosError 本体（response / isAxiosError 语义），仅就地附加分类信息。
    return Promise.reject(withApiErrorView(error, display))
  },
)

export default service
