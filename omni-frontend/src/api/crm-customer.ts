/**
 * @module api/crm-customer
 * CRM 客户管理 API。Customer 360 的跨聚合数据由对应领域 API 单独加载。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { CrmActivity } from './crm-activity'
import type { CrmContact } from './crm-contact'
import type { CrmOpportunity } from './crm-opportunity'

export type CustomerStatus = 'POTENTIAL' | 'ACTIVE' | 'DORMANT' | 'LOST' | 'BLACKLISTED'

export interface CrmCustomer {
  id: number
  customerNo: string
  name: string
  customerType: string | null
  industryCode: string | null
  levelCode: string | null
  sourceCode: string | null
  creditCode: string | null
  website: string | null
  phone: string | null
  email: string | null
  region: string | null
  address: string | null
  status: CustomerStatus
  ownerUserId: number
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  lastActivityTime: string | null
  nextFollowupTime: string | null
  version: number
  createTime: string
  updateTime: string
}

export interface CustomerQuery {
  keyword?: string
  status?: CustomerStatus
  ownerUserId?: number
  page: number
  size: number
}

export interface SaveCustomerRequest {
  name: string
  customerType?: string
  industryCode?: string
  levelCode?: string
  sourceCode?: string
  creditCode?: string
  website?: string
  phone?: string
  email?: string
  region?: string
  address?: string
  ownerUserId?: number
  version?: number
}

export interface DuplicateCustomerCandidate {
  id: number
  number: string
  name: string
  matchedBy: string
  maskedContact: string | null
}

export interface CustomerOverview {
  customer: CrmCustomer
  contacts: CrmContact[]
  openOpportunities: CrmOpportunity[]
  recentActivities: CrmActivity[]
  convertedLeadIds: number[]
}

export function listCustomers(params: CustomerQuery) {
  return request.get<ApiResponse<PageResult<CrmCustomer>>>('/crm/customer/list', { params })
}

export function getCustomer(id: number) {
  return request.get<ApiResponse<CrmCustomer>>(`/crm/customer/${id}`)
}

export function getCustomerOverview(id: number) {
  return request.get<ApiResponse<CustomerOverview>>(`/crm/customer/${id}/overview`)
}

export function createCustomer(data: SaveCustomerRequest) {
  return request.post<ApiResponse<CrmCustomer>>('/crm/customer', data)
}

export function updateCustomer(id: number, data: SaveCustomerRequest) {
  return request.put<ApiResponse<CrmCustomer>>(`/crm/customer/${id}`, data)
}

export function deleteCustomer(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/crm/customer/${id}`, { params: { version } })
}

export function checkCustomerDuplicates(data: Pick<SaveCustomerRequest, 'name' | 'creditCode' | 'phone'>) {
  return request.post<ApiResponse<DuplicateCustomerCandidate[]>>('/crm/customer/duplicate-check', data)
}

export function changeCustomerStatus(id: number, data: { status: CustomerStatus; version: number }) {
  return request.post<ApiResponse<CrmCustomer>>(`/crm/customer/${id}/status`, data)
}

export function transferCustomer(id: number, data: {
  ownerUserId: number
  reason?: string
  cascadeOpenOpportunities: boolean
  version: number
}) {
  return request.post<ApiResponse<CrmCustomer>>(`/crm/customer/${id}/transfer`, data)
}

export function blacklistCustomer(id: number, version: number) {
  const data = { version }
  return request.post<ApiResponse<CrmCustomer>>(`/crm/customer/${id}/blacklist`, data)
}

export function restoreCustomerFromBlacklist(id: number, version: number) {
  const data = { version }
  return request.post<ApiResponse<CrmCustomer>>(`/crm/customer/${id}/restore-from-blacklist`, data)
}
