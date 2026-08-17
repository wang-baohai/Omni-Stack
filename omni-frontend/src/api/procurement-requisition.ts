/**
 * @module api/procurement-requisition
 * 采购请购申请与审批业务视图 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementRequisitionStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'APPROVING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED'

export type ProcurementWorkflowStartStatus =
  | 'NOT_STARTED'
  | 'PENDING'
  | 'FAILED'
  | 'STARTED'

export interface ProcurementRequisitionSummary {
  id: number
  requisitionNo: string
  title: string
  requesterUserId: number
  requesterUnitId: number
  primaryCategoryCode: string
  totalAmount: string
  currencyCode: string
  status: ProcurementRequisitionStatus
  workflowStartStatus: ProcurementWorkflowStartStatus
  approvalAttempt: number
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementRequisitionLine {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  categoryCode: string
  unit: string
  quantity: string
  estimatedUnitPrice: string
  estimatedTotalPrice: string
  remark: string | null
  version: number
}

export interface ProcurementRequisitionDetail extends ProcurementRequisitionSummary {
  reason: string | null
  workflowBusinessKey: string | null
  workflowModelVersionId: number | null
  processInstanceId: string | null
  approvedTime: string | null
  workflowCompletedTime: string | null
  lines: ProcurementRequisitionLine[]
}

export interface ProcurementRequisitionApprovalView {
  taskId: string
  requisition: ProcurementRequisitionDetail
}

export interface ProcurementRequisitionQuery {
  keyword?: string
  status?: ProcurementRequisitionStatus
  categoryCode?: string
  page: number
  size: number
}

export interface ProcurementRequisitionLineInput {
  materialId: number
  quantity: string
  estimatedUnitPrice: string
  remark?: string
}

export interface CreateProcurementRequisitionRequest {
  title: string
  reason?: string
  lines: ProcurementRequisitionLineInput[]
}

export interface UpdateProcurementRequisitionRequest
  extends CreateProcurementRequisitionRequest {
  version: number
}

export function listProcurementRequisitions(params: ProcurementRequisitionQuery) {
  return request.get<ApiResponse<PageResult<ProcurementRequisitionSummary>>>(
    '/procurement/requisition/list',
    {
      params: {
        ...params,
        page: Math.max(params.page, 1),
        size: Math.min(Math.max(params.size, 1), 100),
      },
    },
  )
}

export function getProcurementRequisition(id: number) {
  return request.get<ApiResponse<ProcurementRequisitionDetail>>(
    `/procurement/requisition/${id}`,
  )
}

export function getProcurementRequisitionApprovalView(id: number, taskId: string) {
  return request.get<ApiResponse<ProcurementRequisitionApprovalView>>(
    `/procurement/requisition/${id}/approval-view`,
    { params: { taskId } },
  )
}

export function createProcurementRequisition(data: CreateProcurementRequisitionRequest) {
  return request.post<ApiResponse<ProcurementRequisitionDetail>>(
    '/procurement/requisition',
    data,
  )
}

export function updateProcurementRequisition(
  id: number,
  data: UpdateProcurementRequisitionRequest,
) {
  return request.put<ApiResponse<ProcurementRequisitionDetail>>(
    `/procurement/requisition/${id}`,
    data,
  )
}

export function deleteProcurementRequisition(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/requisition/${id}`, {
    params: { version },
  })
}

export function submitProcurementRequisition(id: number, version: number) {
  return request.post<ApiResponse<ProcurementRequisitionDetail>>(
    `/procurement/requisition/${id}/submit`,
    { version },
  )
}

export function retryProcurementRequisitionStart(id: number, version: number) {
  return request.post<ApiResponse<ProcurementRequisitionDetail>>(
    `/procurement/requisition/${id}/retry-start`,
    { version },
  )
}

export function cancelProcurementRequisition(id: number, version: number) {
  return request.post<ApiResponse<ProcurementRequisitionDetail>>(
    `/procurement/requisition/${id}/cancel`,
    { version },
  )
}
