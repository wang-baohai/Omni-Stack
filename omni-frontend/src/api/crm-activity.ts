/** @module api/crm-activity CRM 跟进活动管理 API。 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ActivityRootType = 'LEAD' | 'CUSTOMER' | 'OPPORTUNITY'
export type ActivityStatus = 'PLANNED' | 'COMPLETED' | 'CANCELLED'

export interface CrmActivity {
  id: number
  rootType: ActivityRootType
  rootId: number
  rootName: string | null
  contactId: number | null
  activityType: string
  subject: string
  content: string | null
  status: ActivityStatus
  plannedStartTime: string | null
  plannedEndTime: string | null
  completedTime: string | null
  nextActionTime: string | null
  performedByUserId: number | null
  performedByName: string | null
  ownerUserId: number
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  version: number
  createTime: string
  updateTime: string
}

export interface ActivityQuery {
  rootType?: ActivityRootType
  rootId?: number
  status?: ActivityStatus
  ownerUserId?: number
  fromTime?: string
  toTime?: string
  page: number
  size: number
}

export interface CreateActivityRequest {
  rootType: ActivityRootType
  rootId: number
  contactId?: number
  activityType: string
  subject: string
  content?: string
  status?: ActivityStatus
  plannedStartTime?: string
  plannedEndTime?: string
  completedTime?: string
  nextActionTime?: string
}

export interface UpdateActivityRequest {
  contactId?: number
  activityType?: string
  subject?: string
  content?: string
  plannedStartTime?: string
  plannedEndTime?: string
  nextActionTime?: string
  version: number
}

export function listActivities(params: ActivityQuery) {
  return request.get<ApiResponse<PageResult<CrmActivity>>>('/crm/activity/list', { params })
}

export function listActivityTimeline(params: { rootType: ActivityRootType; rootId: number; limit?: number }) {
  return request.get<ApiResponse<CrmActivity[]>>('/crm/activity/timeline', { params })
}

export function getActivity(id: number) {
  return request.get<ApiResponse<CrmActivity>>(`/crm/activity/${id}`)
}

export function createActivity(data: CreateActivityRequest) {
  return request.post<ApiResponse<CrmActivity>>('/crm/activity', data)
}

export function updateActivity(id: number, data: UpdateActivityRequest) {
  return request.put<ApiResponse<CrmActivity>>(`/crm/activity/${id}`, data)
}

export function deleteActivity(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/crm/activity/${id}`, { params: { version } })
}

export function completeActivity(id: number, data: { completedTime: string; nextActionTime?: string; version: number }) {
  return request.post<ApiResponse<CrmActivity>>(`/crm/activity/${id}/complete`, data)
}

export function cancelActivity(id: number, data: { reason: string; version: number }) {
  return request.post<ApiResponse<CrmActivity>>(`/crm/activity/${id}/cancel`, data)
}

export function rescheduleActivity(id: number, data: {
  plannedStartTime: string
  plannedEndTime?: string
  version: number
}) {
  return request.post<ApiResponse<CrmActivity>>(`/crm/activity/${id}/reschedule`, data)
}
