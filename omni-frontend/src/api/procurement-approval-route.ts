/**
 * @module api/procurement-approval-route
 * 请购审批路由配置 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementApprovalRouteStatus = 'ACTIVE' | 'INACTIVE'

export interface ProcurementApprovalRoute {
  id: number
  routeCode: string
  categoryCode: string
  minAmount: string
  maxAmount: string | null
  modelVersionId: number
  priority: number
  status: ProcurementApprovalRouteStatus
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementApprovalRouteQuery {
  keyword?: string
  categoryCode?: string
  status?: ProcurementApprovalRouteStatus
  page: number
  size: number
}

export interface CreateProcurementApprovalRouteRequest {
  routeCode: string
  categoryCode: string
  minAmount: string
  maxAmount?: string
  modelVersionId: number
  priority: number
  status?: ProcurementApprovalRouteStatus
}

export interface UpdateProcurementApprovalRouteRequest
  extends Omit<CreateProcurementApprovalRouteRequest, 'routeCode'> {
  version: number
  status: ProcurementApprovalRouteStatus
}

export function listProcurementApprovalRoutes(params: ProcurementApprovalRouteQuery) {
  return request.get<ApiResponse<PageResult<ProcurementApprovalRoute>>>(
    '/procurement/approval-route/list',
    {
      params: {
        ...params,
        page: Math.max(params.page, 1),
        size: Math.min(Math.max(params.size, 1), 100),
      },
    },
  )
}

export function createProcurementApprovalRoute(
  data: CreateProcurementApprovalRouteRequest,
) {
  return request.post<ApiResponse<ProcurementApprovalRoute>>(
    '/procurement/approval-route',
    data,
  )
}

export function updateProcurementApprovalRoute(
  id: number,
  data: UpdateProcurementApprovalRouteRequest,
) {
  return request.put<ApiResponse<ProcurementApprovalRoute>>(
    `/procurement/approval-route/${id}`,
    data,
  )
}

export function deleteProcurementApprovalRoute(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/approval-route/${id}`, {
    params: { version },
  })
}
