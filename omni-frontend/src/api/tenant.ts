/**
 * @module api/tenant
 * 租户管理 API 模块。
 * 提供租户的增删改查接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface SysTenant {
  id: number
  tenantCode: string
  tenantName: string
  domain: string | null
  contactName: string | null
  contactPhone: string | null
  status: number
  createTime: string
  updateTime: string
}

export interface CreateTenantRequest {
  tenantCode: string
  tenantName: string
  domain?: string
  contactName?: string
  contactPhone?: string
  status?: number
}

export interface UpdateTenantRequest {
  tenantName?: string
  domain?: string
  contactName?: string
  contactPhone?: string
  status?: number
}

export function listTenantsPage(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysTenant>>>('/auth/tenant/list', {
    params: { page, size },
  })
}

export function getTenant(id: number) {
  return request.get<ApiResponse<SysTenant>>(`/auth/tenant/${id}`)
}

export function createTenant(data: CreateTenantRequest) {
  return request.post<ApiResponse<SysTenant>>('/auth/tenant', data)
}

export function updateTenant(id: number, data: UpdateTenantRequest) {
  return request.put<ApiResponse<SysTenant>>(`/auth/tenant/${id}`, data)
}

export function deleteTenant(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/tenant/${id}`)
}
