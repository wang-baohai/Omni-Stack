/**
 * @module api/srm-risk
 * SRM 风险指标与评估 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type RiskLevel = 'RED' | 'YELLOW' | 'GREEN'

export interface RiskIndicatorVO {
  id: number
  supplierId: number
  indicatorType: string
  indicatorTypeName: string | null
  indicatorValue: string
  riskLevel: RiskLevel
  criterionId: number | null
  score: number | null
  autoCalc: number | null
  assessmentTime: string
  remark: string | null
  version: number
  criteria: RiskCriterionOption[]
}

export interface RiskCriterionOption {
  id: number
  indicatorTypeId: number
  criterionLabel: string
  score: number
  riskLevel: RiskLevel
  sort: number
  status: number
}

export interface RiskAssessmentVO {
  id: number
  supplierId: number
  overallLevel: RiskLevel
  assessmentTime: string
  assessorUserId: number
  remark: string | null
}

export interface SupplierRiskSummary {
  supplierId: number
  supplierName: string | null
  overallLevel: RiskLevel
  redIndicatorCount?: number
  assessmentTime?: string | null
}

export interface SupplierRiskDetail {
  supplierId: number
  indicators: RiskIndicatorVO[]
  latestAssessment: RiskAssessmentVO | null
  history: RiskAssessmentVO[]
}

export interface RiskDashboardVO {
  redCount: number
  yellowCount: number
  greenCount: number
  topRiskSuppliers: SupplierRiskSummary[]
}

export interface UpdateRiskIndicatorRequest {
  version: number
  criterionId?: number
  indicatorValue?: string
  riskLevel?: RiskLevel
  remark?: string
}

export interface CreateRiskAssessmentRequest {
  remark?: string
}

/** 分页查询风险供应商。 */
export function listRiskSuppliers(params: { riskLevel?: RiskLevel; page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<SupplierRiskSummary>>>('/srm/risk/list', { params })
}

/** 查询单个供应商的风险聚合详情。 */
export function getSupplierRisk(supplierId: number) {
  return request.get<ApiResponse<SupplierRiskDetail>>(`/srm/supplier/${supplierId}/risk`)
}

/** 更新风险指标。 */
export function updateRiskIndicator(id: number, data: UpdateRiskIndicatorRequest) {
  return request.put<ApiResponse<RiskIndicatorVO>>(`/srm/risk/indicator/${id}`, data)
}

/** 创建综合风险评估。 */
export function createRiskAssessment(supplierId: number, data: CreateRiskAssessmentRequest) {
  return request.post<ApiResponse<RiskAssessmentVO>>(`/srm/risk/assessment/${supplierId}`, data)
}

/** 查询风险看板。 */
export function getRiskDashboard() {
  return request.get<ApiResponse<RiskDashboardVO>>('/srm/overview/risk-dashboard')
}
