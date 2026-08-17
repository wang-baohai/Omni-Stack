/**
 * @module api/procurement-purchase-order
 * 采购订单跟踪与状态命令 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementPurchaseOrderStatus =
  | 'DRAFT'
  | 'SENT'
  | 'CONFIRMED'
  | 'PARTIAL_RECEIVED'
  | 'RECEIVED'
  | 'CLOSED'
  | 'CANCELLED'

export interface ProcurementPurchaseOrderSummary {
  id: number
  poNo: string
  rfqId: number
  supplierId: number
  supplierNameSnapshot: string
  title: string
  totalAmount: string
  currencyCode: string
  status: ProcurementPurchaseOrderStatus
  orderTime: string | null
  expectedDeliveryDate: string | null
  actualDeliveryDate: string | null
  deliveryAddressMasked: string | null
  contactNameMasked: string | null
  contactPhoneMasked: string | null
  ownerUserId: number
  ownerUnitId: number
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementPurchaseOrderLine {
  id: number
  lineNo: number
  rfqLineId: number
  materialId: number
  materialCode: string
  materialName: string
  categoryCode: string
  unit: string
  quantity: string
  unitPrice: string
  totalPrice: string
  deliveryDays: number
  expectedDeliveryDate: string | null
  receivedQuantity: string
  remainingQuantity: string
  remark: string | null
  version: number
}

export interface ProcurementPurchaseOrderDetail extends ProcurementPurchaseOrderSummary {
  quotationId: number
  quotationVersion: number
  deliveryAddress: string
  contactName: string
  contactPhone: string
  lines: ProcurementPurchaseOrderLine[]
}

export interface ProcurementPurchaseOrderQuery {
  keyword?: string
  rfqId?: number
  supplierId?: number
  status?: ProcurementPurchaseOrderStatus
  expectedDeliveryFrom?: string
  expectedDeliveryTo?: string
  page: number
  size: number
}

export interface UpdateProcurementPurchaseOrderRequest {
  version: number
  title: string
  expectedDeliveryDate?: string
  deliveryAddress: string
  contactName: string
  contactPhone: string
}

export function listProcurementPurchaseOrders(params: ProcurementPurchaseOrderQuery) {
  return request.get<ApiResponse<PageResult<ProcurementPurchaseOrderSummary>>>(
    '/procurement/purchase-order/list',
    {
      params: {
        ...params,
        page: Math.max(params.page, 1),
        size: Math.min(Math.max(params.size, 1), 100),
      },
    },
  )
}

export function getProcurementPurchaseOrder(id: number) {
  return request.get<ApiResponse<ProcurementPurchaseOrderDetail>>(
    `/procurement/purchase-order/${id}`,
  )
}

export function updateProcurementPurchaseOrder(
  id: number,
  data: UpdateProcurementPurchaseOrderRequest,
) {
  return request.put<ApiResponse<ProcurementPurchaseOrderDetail>>(
    `/procurement/purchase-order/${id}`,
    data,
  )
}

export function deleteProcurementPurchaseOrder(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/purchase-order/${id}`, {
    params: { version },
  })
}

export function sendProcurementPurchaseOrder(id: number, version: number) {
  return request.post<ApiResponse<ProcurementPurchaseOrderDetail>>(
    `/procurement/purchase-order/${id}/send`,
    { version },
  )
}

export function confirmProcurementPurchaseOrder(id: number, version: number) {
  return request.post<ApiResponse<ProcurementPurchaseOrderDetail>>(
    `/procurement/purchase-order/${id}/confirm`,
    { version },
  )
}

export function cancelProcurementPurchaseOrder(id: number, version: number) {
  return request.post<ApiResponse<ProcurementPurchaseOrderDetail>>(
    `/procurement/purchase-order/${id}/cancel`,
    { version },
  )
}
