/**
 * @module api/tenant
 * 租户管理 API 模块。
 * 提供租户的增删改查接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 租户实体 */
export interface SysTenant {
  /** 租户 ID */
  id: number
  /** 租户编码 */
  tenantCode: string
  /** 租户名称 */
  tenantName: string
  /** 域名 */
  domain: string | null
  /** 联系人 */
  contactName: string | null
  /** 联系电话 */
  contactPhone: string | null
  /** 状态（1=启用，0=禁用） */
  status: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/** 创建租户请求 */
export interface CreateTenantRequest {
  /** 租户编码 */
  tenantCode: string
  /** 租户名称 */
  tenantName: string
  /** 初始超级管理员密码，仅创建时提交 */
  adminPassword: string
  /** 域名 */
  domain?: string
  /** 联系人 */
  contactName?: string
  /** 联系电话 */
  contactPhone?: string
  /** 状态 */
  status?: number
}

/** 更新租户请求 */
export interface UpdateTenantRequest {
  /** 租户名称 */
  tenantName?: string
  /** 域名 */
  domain?: string
  /** 联系人 */
  contactName?: string
  /** 联系电话 */
  contactPhone?: string
  /** 状态 */
  status?: number
}

/**
 * 分页查询租户列表。
 *
 * @param page - 页码
 * @param size - 每页大小
 * @returns 分页租户列表
 */
export function listTenantsPage(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysTenant>>>('/auth/tenant/list', {
    params: { page, size },
  })
}

/**
 * 按 ID 查询租户详情。
 *
 * @param id - 租户 ID
 * @returns 租户实体
 */
export function getTenant(id: number) {
  return request.get<ApiResponse<SysTenant>>(`/auth/tenant/${id}`)
}

/**
 * 创建租户。
 *
 * @param data - 创建请求
 * @returns 创建成功的租户实体
 */
export function createTenant(data: CreateTenantRequest) {
  return request.post<ApiResponse<SysTenant>>('/auth/tenant', data)
}

/**
 * 更新租户信息。
 *
 * @param id - 租户 ID
 * @param data - 更新请求
 * @returns 更新后的租户实体
 */
export function updateTenant(id: number, data: UpdateTenantRequest) {
  return request.put<ApiResponse<SysTenant>>(`/auth/tenant/${id}`, data)
}

/**
 * 删除租户。
 *
 * @param id - 租户 ID
 * @returns 空结果
 */
export function deleteTenant(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/tenant/${id}`)
}
