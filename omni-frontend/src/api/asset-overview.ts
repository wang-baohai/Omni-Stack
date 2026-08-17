/**
 * @module api/asset-overview
 * 资产总览与多维分布 API。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { AssetStatus } from './asset-asset'

export type AssetDistributionDimension = 'STATUS' | 'CATEGORY' | 'DEPARTMENT' | 'LOCATION'

export interface AssetOverviewSummary {
  totalCount: number
  inStockCount: number
  allocatedCount: number
  inUseCount: number
  maintenanceCount: number
  transferCount: number
  disposalPendingCount: number
  terminalCount: number
  amountsByCurrency: Array<{ currencyCode: string; amount: string }>
}

export interface AssetDistributionItem {
  dimension: AssetDistributionDimension
  dimensionKey: string
  dimensionName: string
  status?: AssetStatus
  currencyCode: string | null
  count: number
  amount: string | null
}

export function getAssetOverviewSummary() {
  return request.get<ApiResponse<AssetOverviewSummary>>('/asset/overview/summary')
}

export function getAssetDistribution(dimension: AssetDistributionDimension, limit = 20) {
  return request.get<ApiResponse<AssetDistributionItem[]>>('/asset/overview/distribution', {
    params: { dimension, limit: Math.min(Math.max(limit, 1), 100) },
  })
}
