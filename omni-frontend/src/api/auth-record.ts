/**
 * @module api/auth-record
 * 授权记录 API 模块。
 * 提供 OAuth2 授权记录的只读查询接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** OAuth2 授权记录实体 */
export interface AuthRecord {
  /** 授权记录 ID */
  id: string
  /** 注册客户端 ID */
  registeredClientId: string
  /** 授权主体名称（用户名） */
  principalName: string
  /** 授权类型（authorization_code/password 等） */
  authorizationGrantType: string
  /** 已授权的作用域 */
  authorizedScopes: string
  /** 创建时间 */
  createdAt: string
}

/**
 * 分页查询 OAuth2 授权记录列表。
 *
 * @param page - 页码
 * @param size - 每页大小
 * @returns 分页授权记录列表
 */
export function listAuthRecords(page: number, size: number) {
  return request.get<ApiResponse<PageResult<AuthRecord>>>('/auth/auth-record/list', {
    params: { page, size },
  })
}
