/** @module api/srm-overview SRM 概览统计与风险看板 API。 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { RiskLevel } from './srm-risk'

export interface SrmOverviewSummary {
  totalSuppliers: number
  approvedCount: number
  pendingReviewCount: number
  suspendedCount: number
  blacklistedCount: number
  eliminatedCount: number
  strategicCount: number
  preferredCount: number
  qualifiedCount: number
}

export interface SupplierRiskSummary {
  supplierId: number
  supplierName: string | null
  overallLevel: RiskLevel
  redIndicatorCount?: number
}

export interface RiskDashboard {
  redCount: number
  yellowCount: number
  greenCount: number
  topRiskSuppliers: SupplierRiskSummary[]
}

export function getSrmOverviewSummary() {
  return request.get<ApiResponse<SrmOverviewSummary>>('/srm/overview/summary')
}

export function getRiskDashboard() {
  return request.get<ApiResponse<RiskDashboard>>('/srm/overview/risk-dashboard')
}
