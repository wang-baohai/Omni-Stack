/** @module api/crm-opportunity CRM 商机、销售管道与阶段命令 API。 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type OpportunityStatus = 'OPEN' | 'WON' | 'LOST'
export type OpportunityStageType = 'OPEN' | 'WON' | 'LOST'

export interface PipelineStage {
  id: number
  pipelineId: number
  stageCode: string
  stageName: string
  stageType: OpportunityStageType
  probability: number
  sort: number
}

export interface CrmPipeline {
  id: number
  code: string
  name: string
  defaultFlag: number
  sort: number
}

export interface CrmOpportunity {
  id: number
  opportunityNo: string
  name: string
  customerId: number
  customerName: string | null
  primaryContactId: number | null
  sourceLeadId: number | null
  pipelineId: number
  stageId: number
  status: OpportunityStatus
  amount: number
  currencyCode: string
  probability: number
  expectedCloseDate: string | null
  actualCloseTime: string | null
  lossReason: string | null
  ownerUserId: number
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  stageChangeTime: string | null
  nextFollowupTime: string | null
  version: number
  createTime: string
  updateTime: string
}

export interface OpportunityQuery {
  keyword?: string
  status?: OpportunityStatus
  stageId?: number
  ownerUserId?: number
  customerId?: number
  page: number
  size: number
}

export interface CreateOpportunityRequest {
  name: string
  customerId: number
  primaryContactId?: number
  pipelineId?: number
  stageId?: number
  amount: number
  expectedCloseDate?: string
  ownerUserId?: number
}

export interface UpdateOpportunityRequest {
  name?: string
  primaryContactId?: number
  amount?: number
  expectedCloseDate?: string
  nextFollowupTime?: string
  version: number
}

export interface OpportunityStageHistory {
  id: number
  opportunityId: number
  fromStageId: number | null
  toStageId: number
  fromStatus: OpportunityStatus | null
  toStatus: OpportunityStatus
  changeReason: string | null
  changedByUserId: number
  changedTime: string
}

export interface OpportunityBoardColumn {
  stage: PipelineStage
  opportunities: CrmOpportunity[]
  totalAmount: number
}

export interface OpportunityBoard {
  columns: OpportunityBoardColumn[]
}

interface OpportunityBoardResponse {
  stages: PipelineStage[]
  opportunitiesByStage: Record<string, CrmOpportunity[]>
}

export function listOpportunities(params: OpportunityQuery) {
  return request.get<ApiResponse<PageResult<CrmOpportunity>>>('/crm/opportunity/list', { params })
}

export function getOpportunityBoard(params?: { pipelineId?: number; ownerUserId?: number }) {
  return request.get<ApiResponse<OpportunityBoardResponse>>('/crm/opportunity/board', { params }).then((response) => {
    const raw = response.data.data
    const data: OpportunityBoard = {
      columns: raw.stages.map((stage) => {
        const opportunities = raw.opportunitiesByStage[String(stage.id)] || []
        return {
          stage,
          opportunities,
          totalAmount: opportunities.reduce((sum, item) => sum + Number(item.amount || 0), 0),
        }
      }),
    }
    return { ...response, data: { ...response.data, data } }
  })
}

export function getOpportunity(id: number) {
  return request.get<ApiResponse<CrmOpportunity>>(`/crm/opportunity/${id}`)
}

export function listOpportunityStageHistory(id: number) {
  return request.get<ApiResponse<OpportunityStageHistory[]>>(`/crm/opportunity/${id}/stage-history`)
}

export function listPipelines() {
  return request.get<ApiResponse<CrmPipeline[]>>('/crm/pipeline/list')
}

export function listPipelineStages(pipelineId: number) {
  return request.get<ApiResponse<PipelineStage[]>>(`/crm/pipeline/${pipelineId}/stages`)
}

export function createOpportunity(data: CreateOpportunityRequest) {
  return request.post<ApiResponse<CrmOpportunity>>('/crm/opportunity', data)
}

export function updateOpportunity(id: number, data: UpdateOpportunityRequest) {
  return request.put<ApiResponse<CrmOpportunity>>(`/crm/opportunity/${id}`, data)
}

export function deleteOpportunity(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/crm/opportunity/${id}`, { params: { version } })
}

export function assignOpportunity(id: number, data: { ownerUserId: number; reason?: string; version: number }) {
  return request.post<ApiResponse<CrmOpportunity>>(`/crm/opportunity/${id}/assign`, data)
}

export function moveOpportunityStage(id: number, data: {
  stageId: number
  reason?: string
  lossReason?: string
  version: number
}) {
  return request.post<ApiResponse<CrmOpportunity>>(`/crm/opportunity/${id}/stage`, data)
}

export function reopenOpportunity(id: number, version: number) {
  return request.post<ApiResponse<CrmOpportunity>>(`/crm/opportunity/${id}/reopen`, { version })
}
