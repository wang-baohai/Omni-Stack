/**
 * @module api/crm-lead
 * CRM 线索管理 API，覆盖查询、CRUD、分配、状态命令、重复检测和转换。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type LeadStatus = 'NEW' | 'FOLLOWING' | 'QUALIFIED' | 'DISQUALIFIED' | 'CONVERTED'

/** CRM 负责人选项 */
export interface CrmOwnerOption {
  id: number
  username: string
  nickname: string | null
  primaryUnitId: number | null
  avatar: string | null
}

/** 线索视图 */
export interface CrmLead {
  id: number
  leadNo: string
  fullName: string
  companyName: string | null
  jobTitle: string | null
  mobile: string | null
  phone: string | null
  email: string | null
  region: string | null
  address: string | null
  sourceCode: string | null
  industryCode: string | null
  rating: string | null
  status: LeadStatus
  disqualifyReason: string | null
  ownerUserId: number
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  lastActivityTime: string | null
  nextFollowupTime: string | null
  convertedTime: string | null
  version: number
  createTime: string
  updateTime: string
}

export interface LeadQuery {
  keyword?: string
  status?: LeadStatus
  ownerUserId?: number
  sourceCode?: string
  page: number
  size: number
}

export interface SaveLeadRequest {
  fullName: string
  companyName?: string
  jobTitle?: string
  mobile?: string
  phone?: string
  email?: string
  region?: string
  address?: string
  sourceCode?: string
  industryCode?: string
  rating?: string
  nextFollowupTime?: string
  ownerUserId?: number
  version?: number
}

export interface DuplicateLeadCandidate {
  id: number
  number: string
  name: string
  matchedBy: string
  maskedContact: string | null
}

export interface ConvertLeadRequest {
  version: number
  customerMode: 'CREATE' | 'LINK'
  customerId?: number
  customerName?: string
  contactMode: 'CREATE' | 'LINK'
  contactId?: number
  contactName?: string
  contactMobile?: string
  contactEmail?: string
  createOpportunity: boolean
  opportunityName?: string
  amount?: number
  expectedCloseDate?: string
}

export interface LeadConversionResult {
  conversionId: number
  leadId: number
  customerId: number
  contactId: number
  opportunityId: number | null
  convertedTime: string
  idempotentReplay: boolean
}

export function listLeads(params: LeadQuery) {
  return request.get<ApiResponse<PageResult<CrmLead>>>('/crm/lead/list', { params })
}

export function getLead(id: number) {
  return request.get<ApiResponse<CrmLead>>(`/crm/lead/${id}`)
}

export function createLead(data: SaveLeadRequest) {
  return request.post<ApiResponse<CrmLead>>('/crm/lead', data)
}

export function updateLead(id: number, data: SaveLeadRequest) {
  return request.put<ApiResponse<CrmLead>>(`/crm/lead/${id}`, data)
}

export function deleteLead(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/crm/lead/${id}`, { params: { version } })
}

export function checkLeadDuplicates(data: Pick<SaveLeadRequest, 'companyName' | 'mobile' | 'email'>) {
  return request.post<ApiResponse<DuplicateLeadCandidate[]>>('/crm/lead/duplicate-check', data)
}

export function assignLead(id: number, data: { ownerUserId: number; reason?: string; version: number }) {
  return request.post<ApiResponse<CrmLead>>(`/crm/lead/${id}/assign`, data)
}

export function batchAssignLeads(data: { items: Array<{ id: number; version: number }>; ownerUserId: number; reason?: string }) {
  return request.post<ApiResponse<CrmLead[]>>('/crm/lead/batch-assign', data)
}

export function qualifyLead(id: number, version: number) {
  return request.post<ApiResponse<CrmLead>>(`/crm/lead/${id}/qualify`, { version })
}

export function disqualifyLead(id: number, data: { reason: string; version: number }) {
  return request.post<ApiResponse<CrmLead>>(`/crm/lead/${id}/disqualify`, data)
}

export function reopenLead(id: number, version: number) {
  return request.post<ApiResponse<CrmLead>>(`/crm/lead/${id}/reopen`, { version })
}

export function convertLead(id: number, data: ConvertLeadRequest) {
  return request.post<ApiResponse<LeadConversionResult>>(`/crm/lead/${id}/convert`, data)
}

export function listCrmOwners(keyword?: string) {
  return request.get<ApiResponse<CrmOwnerOption[]>>('/crm/options/owners', { params: { keyword } })
}
