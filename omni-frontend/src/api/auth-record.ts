/**
 * @module api/auth-record
 * 授权记录 API 模块。
 * 提供 OAuth2 授权记录的只读查询接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface AuthRecord {
  id: string
  registeredClientId: string
  principalName: string
  authorizationGrantType: string
  authorizedScopes: string
  createdAt: string
}

export function listAuthRecords(page: number, size: number) {
  return request.get<ApiResponse<PageResult<AuthRecord>>>('/auth/auth-record/list', {
    params: { page, size },
  })
}
