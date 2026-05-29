import request from './request'
import type { ApiResponse } from '@/types/api'

export interface LoginParams {
  username: string
  password: string
  tenantId: number
  captchaKey: string
  captchaCode: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  refreshToken?: string
}

export interface CaptchaResult {
  captchaKey: string
  captchaImage: string
}

export interface TenantOption {
  id: number
  name: string
  code: string
}

/** Password grant login */
export function loginByPassword(data: LoginParams) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', data)
}

/** Fetch captcha image (base64) */
export function fetchCaptcha() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha')
}

/** List available tenants (public) */
export function listTenants() {
  return request.get<ApiResponse<TenantOption[]>>('/auth/tenants')
}

/** SSO login redirect URL */
export function getSsoLoginUrl(tenantId: number) {
  return `/api/auth/sso/login?tenant_id=${tenantId}`
}

/** Third-party OAuth2 redirect URL */
export function getThirdPartyLoginUrl(provider: string, tenantId: number) {
  return `/api/auth/oauth2/${provider}?tenant_id=${tenantId}`
}
