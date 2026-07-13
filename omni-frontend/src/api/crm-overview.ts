/** @module api/crm-overview CRM 只读统计与待跟进 API。 */
import request from './request'
import type { ApiResponse } from '@/types/api'

export interface CrmOverviewSummary {
  newLeadCount: number
  qualifiedLeadCount: number
  convertedLeadCount: number
  openOpportunityCount: number
  openOpportunityAmount: number
  wonOpportunityCount: number
  wonOpportunityAmount: number
  overdueFollowupCount: number
  todayFollowupCount: number
  leadConversionRate: number
  opportunityWinRate: number
  currencyCode: string
}

export interface FunnelItem {
  stageId: number
  stageName: string
  stageType: 'OPEN' | 'WON' | 'LOST'
  count: number
  amount: number
  currencyCode: string
}

export interface FollowupItem {
  rootType: 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY'
  rootId: number
  number: string
  name: string
  nextFollowupTime: string
  ownerUserId: number
  overdue: boolean
}

export function getCrmOverviewSummary() {
  return request.get<ApiResponse<CrmOverviewSummary>>('/crm/overview/summary')
}

export function getCrmFunnel(params?: { pipelineId?: number }) {
  return request.get<ApiResponse<FunnelItem[]>>('/crm/overview/funnel', { params: { pipelineId: params?.pipelineId } })
}

export function getCrmFollowups() {
  return request.get<ApiResponse<FollowupItem[]>>('/crm/overview/follow-ups')
}
