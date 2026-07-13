/** @module api/crm-contact CRM 联系人管理 API。 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface CrmContact {
  id: number
  customerId: number
  name: string
  department: string | null
  jobTitle: string | null
  mobile: string | null
  phone: string | null
  email: string | null
  decisionRole: string | null
  primaryFlag: number
  status: number
  ownerUserId: number
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  version: number
  createTime: string
  updateTime: string
}

export interface ContactQuery {
  keyword?: string
  customerId?: number
  status?: number
  page: number
  size: number
}

export interface CreateContactRequest {
  name: string
  department?: string
  jobTitle?: string
  mobile?: string
  phone?: string
  email?: string
  decisionRole?: string
  primary?: boolean
}

export interface UpdateContactRequest extends Omit<CreateContactRequest, 'primary'> {
  status?: number
  version: number
}

export function listContacts(params: ContactQuery) {
  return request.get<ApiResponse<PageResult<CrmContact>>>('/crm/contact/list', { params })
}

export function listCustomerContacts(customerId: number, params: { page: number; size: number }) {
  return request.get<ApiResponse<PageResult<CrmContact>>>(`/crm/customer/${customerId}/contact/list`, { params })
}

export function getContact(id: number) {
  return request.get<ApiResponse<CrmContact>>(`/crm/contact/${id}`)
}

export function createContact(customerId: number, data: CreateContactRequest) {
  return request.post<ApiResponse<CrmContact>>(`/crm/customer/${customerId}/contact`, data)
}

export function updateContact(id: number, data: UpdateContactRequest) {
  return request.put<ApiResponse<CrmContact>>(`/crm/contact/${id}`, data)
}

export function deleteContact(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/crm/contact/${id}`, { params: { version } })
}

export function setPrimaryContact(id: number, version: number) {
  return request.post<ApiResponse<CrmContact>>(`/crm/contact/${id}/primary`, { version })
}
