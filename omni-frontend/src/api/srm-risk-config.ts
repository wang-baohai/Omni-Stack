/**
 * @module api/srm-risk-config
 * SRM 风险指标配置管理 API。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { RiskLevel } from './srm-risk'

export interface RiskCriterionVO {
  id: number
  indicatorTypeId: number
  criterionLabel: string
  score: number
  riskLevel: RiskLevel
  sort: number
  status: number
}

export interface RiskIndicatorTypeVO {
  id: number
  typeCode: string
  typeName: string
  description: string | null
  sort: number
  autoCalc: number
  status: number
  criteria: RiskCriterionVO[]
}

export interface RiskScoreThresholdVO {
  id: number
  riskLevel: RiskLevel
  minScore: number
  maxScore: number
}

/** 查询所有启用的指标类型及其评分标准。 */
export function getRiskIndicatorTypes() {
  return request.get<ApiResponse<RiskIndicatorTypeVO[]>>('/srm/risk/config/types')
}

/** 创建指标类型。 */
export function createIndicatorType(data: Partial<RiskIndicatorTypeVO>) {
  return request.post<ApiResponse<RiskIndicatorTypeVO>>('/srm/risk/config/types', data)
}

/** 更新指标类型。 */
export function updateIndicatorType(id: number, data: Partial<RiskIndicatorTypeVO>) {
  return request.put<ApiResponse<RiskIndicatorTypeVO>>(`/srm/risk/config/types/${id}`, data)
}

/** 删除指标类型。 */
export function deleteIndicatorType(id: number) {
  return request.delete<ApiResponse<void>>(`/srm/risk/config/types/${id}`)
}

/** 创建评分标准。 */
export function createCriterion(data: Partial<RiskCriterionVO>) {
  return request.post<ApiResponse<RiskCriterionVO>>('/srm/risk/config/criteria', data)
}

/** 更新评分标准。 */
export function updateCriterion(id: number, data: Partial<RiskCriterionVO>) {
  return request.put<ApiResponse<RiskCriterionVO>>(`/srm/risk/config/criteria/${id}`, data)
}

/** 删除评分标准。 */
export function deleteCriterion(id: number) {
  return request.delete<ApiResponse<void>>(`/srm/risk/config/criteria/${id}`)
}

/** 查询得分阈值列表。 */
export function getScoreThresholds() {
  return request.get<ApiResponse<RiskScoreThresholdVO[]>>('/srm/risk/config/thresholds')
}

/** 批量更新得分阈值。 */
export function updateScoreThresholds(data: RiskScoreThresholdVO[]) {
  return request.put<ApiResponse<RiskScoreThresholdVO[]>>('/srm/risk/config/thresholds', data)
}
