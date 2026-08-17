/**
 * @module api/procurement-goods-receipt
 * 采购收货草稿、确认及质检结果 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementGoodsReceiptStatus = 'DRAFT' | 'CONFIRMED'
export type ProcurementGoodsReceiptQualityStatus = 'PASS' | 'FAIL' | 'PENDING'

export interface ProcurementGoodsReceiptSummary {
  id: number
  grNo: string
  poId: number
  poNo: string
  receiverUserId: number
  receiveTime: string
  remark: string | null
  status: ProcurementGoodsReceiptStatus
  confirmedTime: string | null
  ownerUserId: number
  ownerUnitId: number
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementGoodsReceiptLine {
  id: number
  lineNo: number
  poLineId: number
  materialId: number
  materialCode: string
  materialName: string
  categoryCode: string
  unit: string
  assetManaged: boolean
  orderedQuantity: string
  receivedQuantity: string
  qualityStatus: ProcurementGoodsReceiptQualityStatus
  qualityResultTime: string | null
  remark: string | null
  version: number
}

export interface ProcurementGoodsReceiptDetail extends ProcurementGoodsReceiptSummary {
  lines: ProcurementGoodsReceiptLine[]
}

export interface ProcurementGoodsReceiptQuery {
  keyword?: string
  poId?: number
  status?: ProcurementGoodsReceiptStatus
  receiveTimeFrom?: string
  receiveTimeTo?: string
  page: number
  size: number
}

export interface ProcurementGoodsReceiptLineInput {
  poLineId: number
  receivedQuantity: string
  qualityStatus: ProcurementGoodsReceiptQualityStatus
  remark?: string
}

export interface CreateProcurementGoodsReceiptRequest {
  poId: number
  receiveTime: string
  remark?: string
  lines: ProcurementGoodsReceiptLineInput[]
}

export interface ProcurementGoodsReceiptQualityResultLineInput {
  goodsReceiptLineId: number
  qualityStatus: Exclude<ProcurementGoodsReceiptQualityStatus, 'PENDING'>
}

export function listProcurementGoodsReceipts(params: ProcurementGoodsReceiptQuery) {
  return request.get<ApiResponse<PageResult<ProcurementGoodsReceiptSummary>>>(
    '/procurement/goods-receipt/list',
    {
      params: {
        ...params,
        page: Math.max(params.page, 1),
        size: Math.min(Math.max(params.size, 1), 100),
      },
    },
  )
}

export function getProcurementGoodsReceipt(id: number) {
  return request.get<ApiResponse<ProcurementGoodsReceiptDetail>>(
    `/procurement/goods-receipt/${id}`,
  )
}

export function createProcurementGoodsReceipt(data: CreateProcurementGoodsReceiptRequest) {
  return request.post<ApiResponse<ProcurementGoodsReceiptDetail>>(
    '/procurement/goods-receipt',
    data,
  )
}

export function confirmProcurementGoodsReceipt(id: number, version: number) {
  return request.post<ApiResponse<ProcurementGoodsReceiptDetail>>(
    `/procurement/goods-receipt/${id}/confirm`,
    { version },
  )
}

export function submitProcurementGoodsReceiptQualityResult(
  id: number,
  version: number,
  lines: ProcurementGoodsReceiptQualityResultLineInput[],
) {
  return request.post<ApiResponse<ProcurementGoodsReceiptDetail>>(
    `/procurement/goods-receipt/${id}/quality-result`,
    { version, lines },
  )
}
