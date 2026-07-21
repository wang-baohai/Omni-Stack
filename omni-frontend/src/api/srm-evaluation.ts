/**
 * @module api/srm-evaluation
 * SRM 绩效评估 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface EvaluationItemVO {
  id: number
  evaluationId: number
  dimensionId: number
  indicatorName: string
  score: number
  weight: number
  remark: string | null
}

export interface EvaluationVO {
  id: number
  supplierId: number
  supplierName: string | null
  templateId: number
  evaluationPeriod: string
  totalScore: number
  evaluatorUserId: number
  evaluationTime: string
  status: string
  version: number
  ownerUserId: number | null
  ownerUnitId: number | null
  items: EvaluationItemVO[]
}

export interface EvaluationItemInput {
  dimensionId: number
  score: number
  indicatorName?: string
  remark?: string
}

export interface CreateEvaluationRequest {
  supplierId: number
  evaluationPeriod: string
  items: EvaluationItemInput[]
}

export interface EvaluationDimension {
  id: number
  indicatorName: string
  weight: number
  sort: number
}

export interface DefaultEvaluationTemplate {
  id: number
  name: string
  version: number
  dimensions: EvaluationDimension[]
}

/** 分页查询评估列表。 */
export function listEvaluations(params: { supplierId?: number; page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<EvaluationVO>>>('/srm/evaluation/list', { params })
}

/** 查询评估详情。 */
export function getEvaluation(id: number) {
  return request.get<ApiResponse<EvaluationVO>>(`/srm/evaluation/${id}`)
}

/** 查询供应商评估历史。 */
export function supplierEvaluationHistory(supplierId: number) {
  return request.get<ApiResponse<EvaluationVO[]>>(`/srm/supplier/${supplierId}/evaluation/history`)
}

/** 创建评估。 */
export function createEvaluation(data: CreateEvaluationRequest) {
  return request.post<ApiResponse<EvaluationVO>>('/srm/evaluation', data)
}

/** 查询评估模板维度。 */
export function getDefaultEvaluationTemplate() {
  return request.get<ApiResponse<DefaultEvaluationTemplate>>('/srm/evaluation/template/default/dimensions')
}
