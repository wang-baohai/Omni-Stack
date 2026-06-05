/**
 * @module api/oauth2-client
 * OAuth2 客户端管理 API 模块。
 * 提供客户端的增删改查接口，与后端 OAuth2ClientController 对应。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/**
 * OAuth2 客户端视图对象。
 */
export interface OAuth2ClientVO {
  /** 客户端内部 ID（UUID） */
  id: string
  /** OAuth2 客户端标识符 */
  clientId: string
  /** 客户端显示名称 */
  clientName: string
  /** 客户端密钥（脱敏后为 "******"，null 表示无密钥） */
  clientSecret: string | null
  /** 支持的客户端认证方式列表 */
  authenticationMethods: string[]
  /** 支持的授权类型列表 */
  grantTypes: string[]
  /** 授权回调地址列表 */
  redirectUris: string[]
  /** 登出后回调地址列表 */
  postLogoutRedirectUris: string[]
  /** 授权作用域列表 */
  scopes: string[]
  /** 是否要求用户授权确认 */
  requireConsent: boolean
  /** 是否要求 PKCE 证明 */
  requireProofKey: boolean
  /** 客户端创建时间 */
  createdAt: string
}

/**
 * 创建 OAuth2 客户端请求参数。
 */
export interface CreateOAuth2ClientRequest {
  /** 客户端显示名称 */
  clientName: string
  /** 客户端标识符，留空则自动生成 UUID */
  clientId?: string
  /** 客户端密钥，PKCE 公有客户端可省略 */
  clientSecret?: string
  /** 支持的客户端认证方式 */
  authenticationMethods: string[]
  /** 支持的授权类型 */
  grantTypes: string[]
  /** 授权回调地址列表 */
  redirectUris?: string[]
  /** 登出后回调地址列表 */
  postLogoutRedirectUris?: string[]
  /** 授权作用域列表 */
  scopes: string[]
  /** 是否要求用户授权确认 */
  requireConsent: boolean
  /** 是否要求 PKCE 证明 */
  requireProofKey: boolean
}

/**
 * 更新 OAuth2 客户端请求参数。
 */
export interface UpdateOAuth2ClientRequest {
  /** 客户端显示名称 */
  clientName: string
  /** 支持的客户端认证方式 */
  authenticationMethods: string[]
  /** 支持的授权类型 */
  grantTypes: string[]
  /** 授权回调地址列表 */
  redirectUris?: string[]
  /** 登出后回调地址列表 */
  postLogoutRedirectUris?: string[]
  /** 授权作用域列表 */
  scopes: string[]
  /** 是否要求用户授权确认 */
  requireConsent: boolean
  /** 是否要求 PKCE 证明 */
  requireProofKey: boolean
}

/**
 * 分页查询 OAuth2 客户端列表。
 *
 * @param page - 页码（从 1 开始）
 * @param size - 每页数量
 * @returns 包含客户端列表的分页结果
 */
export function listOAuth2Clients(page: number, size: number) {
  return request.get<ApiResponse<{ records: OAuth2ClientVO[]; total: number; size: number; current: number; pages: number }>>(
    '/auth/oauth2-client/list',
    { params: { page, size } },
  )
}

/**
 * 获取单个 OAuth2 客户端详情。
 *
 * @param id - 客户端内部 ID（UUID）
 * @returns 客户端视图对象
 */
export function getOAuth2Client(id: string) {
  return request.get<ApiResponse<OAuth2ClientVO>>(`/auth/oauth2-client/${id}`)
}

/**
 * 创建 OAuth2 客户端。
 *
 * @param data - 创建请求参数
 * @returns 创建后的客户端视图对象
 */
export function createOAuth2Client(data: CreateOAuth2ClientRequest) {
  return request.post<ApiResponse<OAuth2ClientVO>>('/auth/oauth2-client', data)
}

/**
 * 更新 OAuth2 客户端。
 *
 * @param id - 客户端内部 ID（UUID）
 * @param data - 更新请求参数
 * @returns 更新后的客户端视图对象
 */
export function updateOAuth2Client(id: string, data: UpdateOAuth2ClientRequest) {
  return request.put<ApiResponse<OAuth2ClientVO>>(`/auth/oauth2-client/${id}`, data)
}

/**
 * 删除 OAuth2 客户端。
 *
 * @param id - 客户端内部 ID（UUID）
 */
export function deleteOAuth2Client(id: string) {
  return request.delete<ApiResponse<void>>(`/auth/oauth2-client/${id}`)
}
