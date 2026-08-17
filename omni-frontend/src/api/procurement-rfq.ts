/**
 * @module api/procurement-rfq
 * 采购询价、供应商邀请与定标 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { ProcurementPurchaseOrderDetail } from './procurement-purchase-order'

export type ProcurementRfqStatus =
  | 'DRAFT'
  | 'SENT'
  | 'CLOSED'
  | 'AWARDED'
  | 'CANCELLED'

export type ProcurementRfqInvitationStatus =
  | 'DRAFT'
  | 'INVITED'
  | 'QUOTED'
  | 'AWARDED'
  | 'REJECTED'
  | 'CANCELLED'

export interface ProcurementRfqSummary {
  id: number
  rfqNo: string
  requisitionId: number
  title: string
  quotationDeadline: string
  currencyCode: string
  status: ProcurementRfqStatus
  sentTime: string | null
  awardedSupplierId: number | null
  awardedQuotationId: number | null
  awardedQuotationVersion: number | null
  awardedTime: string | null
  ownerUserId: number
  ownerUnitId: number
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementRfqLine {
  id: number
  lineNo: number
  materialId: number
  materialCode: string
  materialName: string
  categoryCode: string
  unit: string
  quantity: string
  remark: string | null
  version: number
}

export interface ProcurementRfqSupplierInvitation {
  id: number
  supplierId: number
  supplierName: string
  invitedTime: string | null
  quotationId: number | null
  quotationVersion: number | null
  status: ProcurementRfqInvitationStatus
  version: number
}

export interface ProcurementRfqDetail extends ProcurementRfqSummary {
  lines: ProcurementRfqLine[]
  suppliers: ProcurementRfqSupplierInvitation[]
}

export interface ProcurementRfqSupplierOption {
  id: number
  supplierNo: string
  name: string
  levelCode: string | null
  categoryCode: string | null
}

export interface ProcurementRfqQuotationLine {
  id: number
  rfqLineId: number
  materialCode: string
  materialName: string
  unit: string
  unitPrice: string
  quantity: string
  lineAmount: string
  deliveryDays: number
  remark: string | null
}

export interface ProcurementRfqQuotation {
  id: number
  rfqId: number
  rfqNo: string
  supplierId: number
  supplierNameSnapshot: string
  quotationTime: string
  validUntil: string
  totalAmount: string
  currencyCode: string
  status: string
  version: number
  lines: ProcurementRfqQuotationLine[]
}

export interface ProcurementRfqQuery {
  keyword?: string
  requisitionId?: number
  status?: ProcurementRfqStatus
  deadlineFrom?: string
  deadlineTo?: string
  page: number
  size: number
}

export interface CreateProcurementRfqRequest {
  requisitionId: number
  title: string
  quotationDeadline: string
  supplierIds: number[]
}

export interface UpdateProcurementRfqRequest {
  version: number
  title: string
  quotationDeadline: string
  supplierIds: number[]
}

export interface AwardProcurementRfqRequest {
  rfqVersion: number
  quotationId: number
  quotationVersion: number
  title: string
  expectedDeliveryDate?: string
  deliveryAddress: string
  contactName: string
  contactPhone: string
}

export interface ProcurementRfqAwardResult {
  rfq: ProcurementRfqDetail
  purchaseOrder: ProcurementPurchaseOrderDetail
}

export function listProcurementRfqs(params: ProcurementRfqQuery) {
  return request.get<ApiResponse<PageResult<ProcurementRfqSummary>>>('/procurement/rfq/list', {
    params: {
      ...params,
      page: Math.max(params.page, 1),
      size: Math.min(Math.max(params.size, 1), 100),
    },
  })
}

export function getProcurementRfq(id: number) {
  return request.get<ApiResponse<ProcurementRfqDetail>>(`/procurement/rfq/${id}`)
}

export function getProcurementRfqComparison(id: number) {
  return request.get<ApiResponse<ProcurementRfqQuotation[]>>(
    `/procurement/rfq/${id}/comparison`,
  )
}

export function listProcurementRfqSupplierOptions(params: {
  keyword?: string
  categoryCode?: string
  limit?: number
}) {
  return request.get<ApiResponse<ProcurementRfqSupplierOption[]>>(
    '/procurement/rfq/supplier-options',
    { params: { ...params, limit: Math.min(Math.max(params.limit ?? 50, 1), 100) } },
  )
}

export function createProcurementRfq(data: CreateProcurementRfqRequest) {
  return request.post<ApiResponse<ProcurementRfqDetail>>('/procurement/rfq', data)
}

export function updateProcurementRfq(id: number, data: UpdateProcurementRfqRequest) {
  return request.put<ApiResponse<ProcurementRfqDetail>>(`/procurement/rfq/${id}`, data)
}

export function deleteProcurementRfq(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/rfq/${id}`, {
    params: { version },
  })
}

export function sendProcurementRfq(id: number, version: number) {
  return request.post<ApiResponse<ProcurementRfqDetail>>(`/procurement/rfq/${id}/send`, {
    version,
  })
}

export function awardProcurementRfq(id: number, data: AwardProcurementRfqRequest) {
  return request.post<ApiResponse<ProcurementRfqAwardResult>>(
    `/procurement/rfq/${id}/award`,
    data,
  )
}

export function cancelProcurementRfq(id: number, version: number) {
  return request.post<ApiResponse<ProcurementRfqDetail>>(`/procurement/rfq/${id}/cancel`, {
    version,
  })
}
