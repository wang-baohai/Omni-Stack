/**
 * @module api/login-record
 * 登录记录 API 模块（D02）。
 * 提供安全审计只读投影的分页查询接口；数据源为后端 sys_audit_log 的登录事件。
 * X-Tenant-Id 由 request 拦截器从 JWT 自动注入。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 登录记录（后端已脱敏与白名单映射） */
export interface LoginRecord {
  id: number
  createTime: string
  username: string
  result: string
  method: string
  provider: string | null
  reasonCode: string | null
  ipMasked: string
  clientSummary: string
}

/** 登录记录查询参数 */
export interface LoginRecordQuery {
  page: number
  size: number
  username?: string
  result?: string
  method?: string
  startTime?: string
  endTime?: string
}

/**
 * 分页查询登录记录。
 *
 * @param params - 查询参数
 * @returns 脱敏登录记录分页
 */
export function listLoginRecords(params: LoginRecordQuery) {
  return request.get<ApiResponse<PageResult<LoginRecord>>>('/auth/login-record/list', {
    params,
  })
}
