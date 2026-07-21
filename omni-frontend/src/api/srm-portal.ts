/** @module api/srm-portal SRM 供应商门户 API。 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { PageResult } from '@/types/api'

export interface PortalProfile {
  supplierId: number
  supplierNo: string
  name: string
  supplierType: string | null
  creditCode: string | null
  website: string | null
  phone: string | null
  email: string | null
  region: string | null
  address: string | null
  status: string
  version: number
}

export interface EnrollRequest {
  requestId: string
  inviteToken: string
  name: string
  creditCode: string
  supplierType?: string
  industryCode?: string
  website?: string
  phone?: string
  email?: string
  region?: string
  address?: string
}

export type EnrollmentStatus =
  | 'PENDING_ROLE_ASSIGN'
  | 'ROLE_ASSIGN_FAILED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface EnrollmentState {
  requestId: string
  supplierId: number
  status: EnrollmentStatus
  lastErrorCode?: string | null
  retryCount?: number
  nextRetryTime?: string | null
}

export interface UpdatePortalProfileRequest {
  name?: string
  website?: string
  phone?: string
  email?: string
  region?: string
  address?: string
  version: number
}

export interface InviteItem {
  id: number
  inviteToken?: string
  status: string
  expiresTime: string
  maxUses: number
  usedCount: number
  createTime: string
  createBy: string | null
}

export interface CreateInviteRequest {
  maxUses?: number
  expiresHours?: number
}

export function enrollSupplier(data: EnrollRequest) {
  return request.post<ApiResponse<EnrollmentState>>('/srm/portal/enroll', data)
}

export function getPortalEnrollment() {
  return request.get<ApiResponse<EnrollmentState | null>>('/srm/portal/enrollment')
}

export function retryPortalEnrollment() {
  return request.post<ApiResponse<EnrollmentState>>('/srm/portal/enrollment/retry')
}

export function getPortalProfile() {
  return request.get<ApiResponse<PortalProfile>>('/srm/portal/profile')
}

export function updatePortalProfile(data: UpdatePortalProfileRequest) {
  return request.put<ApiResponse<PortalProfile>>('/srm/portal/profile', data)
}

/** 将已驳回的门户企业资料重新提交审核。 */
export function submitPortalProfile(version: number) {
  return request.post<ApiResponse<PortalProfile>>('/srm/portal/profile/submit', { version })
}

export function listInvites() {
  return request.get<ApiResponse<InviteItem[]>>('/srm/portal/invite/list')
}

export function createInvite(data: CreateInviteRequest) {
  return request.post<ApiResponse<InviteItem>>('/srm/portal/invite', data)
}

export function revokeInvite(inviteId: number) {
  return request.post<ApiResponse<void>>(`/srm/portal/invite/${inviteId}/revoke`)
}

/** 绩效评估记录 */
export interface PortalEvaluation {
  id: number
  supplierId: number
  supplierName: string
  evaluationPeriod: string
  totalScore: number
  evaluatorUserId: number
  evaluationTime: string
  status: string
  version: number
  items: PortalEvaluationItem[]
}

export interface PortalEvaluationItem {
  id: number
  evaluationId: number
  dimensionId: number
  indicatorName: string
  score: number
  weight: number
  remark: string
}


/** 查询门户绩效评估列表 */
export function listPortalEvaluations(params?: { page?: number; size?: number }) {
  return request.get<ApiResponse<PageResult<PortalEvaluation>>>('/srm/portal/evaluations', { params })
}

/** 查询门户绩效评估历史 */
export function listPortalEvaluationHistory() {
  return request.get<ApiResponse<PortalEvaluation[]>>('/srm/portal/evaluations/history')
}

