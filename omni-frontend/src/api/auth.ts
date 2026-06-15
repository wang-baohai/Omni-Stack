/**
 * @module api/auth
 * 认证与授权 API 模块。
 * 封装密码登录、验证码、租户列表、OAuth2 PKCE 流程、设备授权和第三方社交登录等接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/**
 * 登录请求参数类型。
 */
export interface LoginParams {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
  /** 租户 ID */
  tenantId: number
  /** 验证码 Key（UUID） */
  captchaKey: string
  /** 验证码内容 */
  captchaCode: string
}

/**
 * 登录响应结果类型。
 */
export interface LoginResult {
  /** JWT 访问令牌 */
  accessToken: string
  /** 令牌类型（固定 "Bearer"） */
  tokenType: string
  /** 令牌有效期（秒） */
  expiresIn: number
  /** 刷新令牌（可选） */
  refreshToken?: string
}

/**
 * 验证码结果类型。
 */
export interface CaptchaResult {
  /** 验证码唯一标识 */
  captchaKey: string
  /** Base64 编码的验证码图片 */
  captchaImage: string
}

/**
 * 租户选项类型。
 */
export interface TenantOption {
  /** 租户 ID */
  id: number
  /** 租户名称 */
  name: string
  /** 租户编码 */
  code: string
}

/**
 * 使用密码授权方式登录。
 *
 * @param data - 登录请求参数（用户名、密码、租户 ID、验证码）
 * @returns 包含 JWT 访问令牌的登录结果
 */
export function loginByPassword(data: LoginParams) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', data)
}

/**
 * 会话模式登录（用于 OAuth2 授权码流程）。
 * 创建服务端 HttpSession，不返回 JWT。
 */
export function sessionLogin(data: LoginParams) {
  return request.post<ApiResponse<string>>('/auth/session-login', data)
}

/**
 * 获取验证码图片（Base64 编码）。
 *
 * @returns 包含验证码 Key 和 Base64 图片的结果
 */
export function fetchCaptcha() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha')
}

/**
 * 获取可用租户列表（公开接口）。
 *
 * @returns 可用租户选项数组
 */
export function listTenants() {
  return request.get<ApiResponse<TenantOption[]>>('/auth/tenants')
}

/**
 * OAuth2 Token 响应类型（遵循 OAuth2 规范，非 R<T> 格式）。
 */
export interface OAuth2TokenResponse {
  access_token: string
  token_type: string
  expires_in: number
  refresh_token?: string
  id_token?: string
}

/**
 * 构建 OAuth2 授权 URL。
 */
export function buildAuthorizeUrl(params: {
  clientId: string
  redirectUri: string
  state: string
  codeChallenge: string
}): string {
  const searchParams = new URLSearchParams({
    response_type: 'code',
    client_id: params.clientId,
    redirect_uri: params.redirectUri,
    scope: 'openid profile',
    state: params.state,
    code_challenge: params.codeChallenge,
    code_challenge_method: 'S256',
  })
  return `/oauth2/authorize?${searchParams.toString()}`
}

/**
 * 使用授权码换取访问令牌。
 * 注意：此请求使用 application/x-www-form-urlencoded 格式，响应为 OAuth2 标准格式。
 */
export async function exchangeCodeForToken(params: {
  code: string
  codeVerifier: string
  clientId: string
  redirectUri: string
}): Promise<OAuth2TokenResponse> {
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code: params.code,
    code_verifier: params.codeVerifier,
    client_id: params.clientId,
    redirect_uri: params.redirectUri,
  })
  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
    credentials: 'include',
  })
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Token exchange failed: ${response.status} ${errorText}`)
  }
  return response.json() as Promise<OAuth2TokenResponse>
}

/** 获取第三方 OAuth2 登录重定向 URL */
export function getThirdPartyLoginUrl(provider: string, tenantId: number) {
  return `/api/auth/oauth2/${provider}?tenant_id=${tenantId}`
}

/** 注册请求参数类型 */
export interface RegisterParams {
  /** 用户名 */
  username: string
  /** 密码 */
  password: string
  /** 昵称（可选） */
  nickname?: string
  /** 邮箱（可选） */
  email?: string
  /** 租户 ID */
  tenantId: number
  /** 验证码 Key（UUID） */
  captchaKey: string
  /** 验证码内容 */
  captchaCode: string
}

/**
 * 用户自助注册。
 *
 * @param data - 注册请求参数（用户名、密码、验证码等）
 * @returns 操作结果
 */
export function registerUser(data: RegisterParams) {
  return request.post<ApiResponse<void>>('/auth/register', data)
}

/** 设备授权客户端 ID（与后端 DeviceClientInitializer 保持一致） */
export const DEVICE_CLIENT_ID = 'omni-device'

/**
 * 设备授权响应类型（RFC 8628）。
 */
export interface DeviceAuthorizationResponse {
  /** 设备码（设备端使用，用于轮询 token） */
  device_code: string
  /** 用户码（用户在验证页面输入） */
  user_code: string
  /** 验证端点 URI */
  verification_uri: string
  /** 包含预填 user_code 的完整验证 URI（可选） */
  verification_uri_complete?: string
  /** 设备码有效期（秒） */
  expires_in: number
  /** 轮询间隔（秒） */
  interval: number
}

/**
 * 请求设备授权码（RFC 8628 第一步）。
 * 注意：使用 native fetch()，响应为 OAuth2 标准格式。
 */
export async function requestDeviceAuthorization(): Promise<DeviceAuthorizationResponse> {
  const body = new URLSearchParams({
    client_id: DEVICE_CLIENT_ID,
    scope: 'profile',
  })
  const response = await fetch('/oauth2/device_authorization', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  })
  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`Device authorization request failed: ${response.status} ${errorText}`)
  }
  return response.json() as Promise<DeviceAuthorizationResponse>
}

/**
 * 轮询设备授权 token（RFC 8628 第三步）。
 * @returns token 响应，或 null 表示授权尚待处理（authorization_pending / slow_down）
 */
export async function pollDeviceToken(
  deviceCode: string,
  clientId: string,
): Promise<OAuth2TokenResponse | null> {
  const body = new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
    device_code: deviceCode,
    client_id: clientId,
  })
  const response = await fetch('/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  })
  if (!response.ok) {
    const errorJson = await response.json().catch(() => ({})) as { error?: string }
    // authorization_pending: 用户尚未授权，slow_down: 轮询过快
    if (errorJson.error === 'authorization_pending' || errorJson.error === 'slow_down') {
      return null
    }
    throw new Error(`Device token polling failed: ${errorJson.error || response.status}`)
  }
  return response.json() as Promise<OAuth2TokenResponse>
}
