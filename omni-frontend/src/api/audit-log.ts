/**
 * @module api/audit-log
 * 审计日志 API 模块。
 * 提供审计日志只读分页查询接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 审计日志记录 */
export interface AuditLog {
  id: number
  tenantId: number
  eventType: string
  username: string
  userId: number | null
  ipAddress: string | null
  userAgent: string | null
  description: string | null
  extra: Record<string, unknown> | null
  createBy: string | null
  createTime: string
}

/** 审计日志查询参数 */
export interface AuditLogQuery {
  eventType?: string
  username?: string
  startTime?: string
  endTime?: string
  page: number
  size: number
}

/**
 * 分页查询审计日志。
 */
export function listAuditLogs(params: AuditLogQuery) {
  return request.get<ApiResponse<PageResult<AuditLog>>>('/auth/audit-log/list', {
    params,
  })
}
