/**
 * @module api/srm-supplier
 * SRM 供应商管理 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type SupplierStatus =
  | 'REGISTERING'
  | 'REGISTERING_FAILED'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'APPROVED'
  | 'SUSPENDED'
  | 'BLACKLISTED'
  | 'ELIMINATED'
export type SupplierLevel = 'STRATEGIC' | 'PREFERRED' | 'QUALIFIED' | 'ELIMINATED'

export interface SrmSupplier {
  id: number
  supplierNo: string
  name: string
  supplierType: string | null
  industryCode: string | null
  creditCode: string | null
  website: string | null
  phone: string | null
  email: string | null
  region: string | null
  address: string | null
  categoryCode: string | null
  levelCode: SupplierLevel | null
  status: SupplierStatus
  assignedTime: string | null
  lastEvaluationTime: string | null
  ownerUserId: number | null
  ownerUnitId: number | null
  ownerName: string | null
  ownerUnitName: string | null
  version: number
  createTime: string
  createBy: string | null
}

export interface SrmSupplierDetail extends SrmSupplier {
  contacts: SrmContact[]
  qualifications: SrmQualification[]
  bankAccounts: SrmBankAccount[]
}

export interface SrmContact {
  id: number
  supplierId: number
  name: string
  department: string | null
  jobTitle: string | null
  mobile: string | null
  phone: string | null
  email: string | null
  decisionRole: string | null
  primaryFlag: boolean
  status: string
  version: number
}

export interface SrmQualification {
  id: number
  supplierId: number
  qualificationName: string
  certificateNo: string | null
  issuingAuthority: string | null
  issueDate: string | null
  expiryDate: string | null
  status: string
  version: number
}

export interface SrmBankAccount {
  id: number
  supplierId: number
  accountName: string
  accountNo: string
  bankName: string
  bankBranch: string | null
  bankCode: string | null
  primaryFlag: boolean
  status: string
  version: number
}

export interface SupplierOverview extends SrmSupplier {
  contacts: SrmContact[]
  qualifications: SrmQualification[]
  bankAccounts: SrmBankAccount[]
  recentEvaluations: SrmEvaluation[]
  riskIndicators: SrmRiskIndicator[]
  latestRiskAssessment: SrmRiskAssessment | null
}

export interface SrmEvaluation {
  id: number
  supplierId: number
  templateId: number
  evaluationPeriod: string
  totalScore: number
  evaluatorUserId: number
  evaluationTime: string
  status: string
  version: number
  ownerUserId: number
  ownerUnitId: number | null
}

export interface SrmRiskIndicator {
  id: number
  supplierId: number
  indicatorType: string
  indicatorValue: string | null
  riskLevel: string
  assessmentTime: string
  remark: string | null
}

export interface SrmRiskAssessment {
  id: number
  supplierId: number
  overallLevel: string
  assessmentTime: string
  assessorUserId: number
  remark: string | null
}

export interface SupplierQuery {
  name?: string
  categoryCode?: string
  levelCode?: string
  status?: SupplierStatus
  ownerUserId?: number
  ownerUnitId?: number
  page: number
  size: number
}

export interface SaveSupplierRequest {
  name: string
  supplierType?: string
  industryCode?: string
  creditCode?: string
  website?: string
  phone?: string
  email?: string
  region?: string
  address?: string
  categoryCode?: string
  ownerUserId?: number
  version?: number
}

export interface TransferSupplierOwnerRequest {
  ownerUserId: number
  version: number
}

export type UpdateSupplierRequest = Omit<SaveSupplierRequest, 'ownerUserId'>

export interface OwnerOption {
  userId: number
  username: string
  nickname: string | null
  unitName: string | null
}

export interface SaveContactRequest {
  name: string
  department?: string
  jobTitle?: string
  mobile?: string
  phone?: string
  email?: string
  decisionRole?: string
  primaryFlag?: boolean
  version?: number
}

export interface SaveQualificationRequest {
  qualificationName: string
  certificateNo?: string
  issuingAuthority?: string
  issueDate?: string
  expiryDate?: string
  version?: number
}

export interface SaveBankAccountRequest {
  accountName: string
  accountNo?: string
  bankName: string
  bankBranch?: string
  bankCode?: string
  primaryFlag?: boolean
  version?: number
}

export function listSuppliers(params: SupplierQuery) {
  return request.get<ApiResponse<PageResult<SrmSupplier>>>('/srm/supplier/list', {
    params: { ...params, page: Math.max(params.page, 1), size: Math.min(Math.max(params.size, 1), 100) },
  })
}

export function getSupplier(id: number) {
  return request.get<ApiResponse<SrmSupplierDetail>>(`/srm/supplier/${id}`)
}

export function getSupplierOverview(id: number) {
  return request.get<ApiResponse<SupplierOverview>>(`/srm/supplier/${id}/overview`)
}

export function createSupplier(data: SaveSupplierRequest) {
  return request.post<ApiResponse<SrmSupplier>>('/srm/supplier', data)
}

export function updateSupplier(id: number, data: UpdateSupplierRequest) {
  return request.put<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}`, data)
}

/** 使用专用命令转移供应商负责人，普通资料 PUT 不得携带 ownerUserId。 */
export function transferSupplierOwner(id: number, data: TransferSupplierOwnerRequest) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/transfer`, data)
}

export function deleteSupplier(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/srm/supplier/${id}`, { params: { version } })
}

export function submitSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/submit`, { version, reason })
}

export function approveSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/approve`, { version, reason })
}

export function rejectSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/reject`, { version, reason })
}

export function suspendSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/suspend`, { version, reason })
}

export function resumeSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/resume`, { version, reason })
}

export function blacklistSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/blacklist`, { version, reason })
}

export function eliminateSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/eliminate`, { version, reason })
}

export function restoreSupplier(id: number, version: number, reason?: string) {
  return request.post<ApiResponse<SrmSupplier>>(`/srm/supplier/${id}/restore-from-blacklist`, { version, reason })
}

export function listOwnerOptions(params?: { keyword?: string; limit?: number }) {
  return request.get<ApiResponse<OwnerOption[]>>('/srm/options/owners', { params })
}

export function listContacts(supplierId: number) {
  return request.get<ApiResponse<SrmContact[]>>(`/srm/supplier/${supplierId}/contact/list`)
}

export function createContact(supplierId: number, data: SaveContactRequest) {
  return request.post<ApiResponse<SrmContact>>(`/srm/supplier/${supplierId}/contact`, data)
}

export function updateContact(supplierId: number, id: number, data: SaveContactRequest) {
  return request.put<ApiResponse<SrmContact>>(`/srm/supplier/${supplierId}/contact/${id}`, data)
}

export function deleteContact(supplierId: number, id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/srm/supplier/${supplierId}/contact/${id}`, { params: { version } })
}

export function listQualifications(supplierId: number) {
  return request.get<ApiResponse<SrmQualification[]>>(`/srm/supplier/${supplierId}/qualification/list`)
}

export function createQualification(supplierId: number, data: SaveQualificationRequest) {
  return request.post<ApiResponse<SrmQualification>>(`/srm/supplier/${supplierId}/qualification`, data)
}

export function updateQualification(supplierId: number, id: number, data: SaveQualificationRequest) {
  return request.put<ApiResponse<SrmQualification>>(`/srm/supplier/${supplierId}/qualification/${id}`, data)
}

export function deleteQualification(supplierId: number, id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/srm/supplier/${supplierId}/qualification/${id}`, { params: { version } })
}

export function listBankAccounts(supplierId: number) {
  return request.get<ApiResponse<SrmBankAccount[]>>(`/srm/supplier/${supplierId}/bank-account/list`)
}

export function createBankAccount(supplierId: number, data: SaveBankAccountRequest) {
  return request.post<ApiResponse<SrmBankAccount>>(`/srm/supplier/${supplierId}/bank-account`, data)
}

export function updateBankAccount(supplierId: number, id: number, data: SaveBankAccountRequest) {
  return request.put<ApiResponse<SrmBankAccount>>(`/srm/supplier/${supplierId}/bank-account/${id}`, data)
}

export function deleteBankAccount(supplierId: number, id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/srm/supplier/${supplierId}/bank-account/${id}`, { params: { version } })
}
