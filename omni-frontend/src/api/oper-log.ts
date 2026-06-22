/**
 * @module api/oper-log
 * 操作日志 API 模块。
 * 提供操作日志只读分页查询接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 操作日志记录 */
export interface OperLog {
  id: number
  tenantId: number
  operUsername: string | null
  operTime: string
  module: string | null
  operType: string
  requestMethod: string | null
  requestUrl: string | null
  requestParams: string | null
  responseStatus: number
  ipAddress: string | null
  userAgent: string | null
  executionTime: number | null
  oldValue: string | null
  newValue: string | null
  errorMsg: string | null
  createTime: string
}

/** 操作日志查询参数 */
export interface OperLogQuery {
  module?: string
  operType?: string
  operUsername?: string
  startTime?: string
  endTime?: string
  page: number
  size: number
}

/**
 * 分页查询操作日志。
 */
export function listOperLogs(params: OperLogQuery) {
  return request.get<ApiResponse<PageResult<OperLog>>>('/base/oper-log/list', {
    params,
  })
}
