/** @module api/procurement-overview 采购概览与多维支出分析 API。 */
import request from './request'
import type { ApiResponse } from '@/types/api'

export type ProcurementSpendDimension = 'CATEGORY' | 'SUPPLIER' | 'DEPARTMENT'

export interface ProcurementOverviewStatusCount {
  status: string
  count: number
}

export interface ProcurementOverviewCurrencyAmount {
  currencyCode: string
  amount: string
}

export interface ProcurementOverviewSummary {
  pendingApprovalRequisitionCount: number
  waitingQuotationRfqCount: number
  purchaseOrderStatusCounts: ProcurementOverviewStatusCount[]
  draftGoodsReceiptCount: number
  committedAmountsByCurrency: ProcurementOverviewCurrencyAmount[]
}

export interface ProcurementSpendItem {
  dimension: ProcurementSpendDimension
  dimensionKey: string
  dimensionName: string
  currencyCode: string
  amount: string
}

export function getProcurementOverviewSummary() {
  return request.get<ApiResponse<ProcurementOverviewSummary>>('/procurement/overview/summary')
}

export function getProcurementSpendAnalysis(
  dimension: ProcurementSpendDimension,
  limit = 20,
) {
  return request.get<ApiResponse<ProcurementSpendItem[]>>(
    '/procurement/overview/spend-analysis',
    { params: { dimension, limit: Math.min(Math.max(limit, 1), 100) } },
  )
}
