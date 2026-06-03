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

/** 使用密码授权方式登录 */
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

/** 获取验证码图片（Base64 编码） */
export function fetchCaptcha() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha')
}

/** 获取可用租户列表（公开接口） */
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
