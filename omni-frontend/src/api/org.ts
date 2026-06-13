/**
 * @module api/org
 * 组织管理 API 模块。
 * 提供组织树的增删改查接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

export interface SysOrgUnit {
  id: number
  tenantId: number
  parentId: number
  name: string
  type: string
  path: string
  depth: number
  sort: number
  status: number
  createTime: string
  updateTime: string
}

export interface OrgUnitTreeNode extends SysOrgUnit {
  children: OrgUnitTreeNode[]
}

export interface CreateOrgUnitRequest {
  parentId: number
  name: string
  type: string
  sort?: number
  status?: number
}

export interface UpdateOrgUnitRequest {
  name?: string
  type?: string
  sort?: number
  status?: number
}

export function fetchOrgTree() {
  return request.get<ApiResponse<OrgUnitTreeNode[]>>('/auth/org/tree')
}

export function getOrgUnit(id: number) {
  return request.get<ApiResponse<SysOrgUnit>>(`/auth/org/${id}`)
}

export function createOrgUnit(data: CreateOrgUnitRequest) {
  return request.post<ApiResponse<SysOrgUnit>>('/auth/org', data)
}

export function updateOrgUnit(id: number, data: UpdateOrgUnitRequest) {
  return request.put<ApiResponse<SysOrgUnit>>(`/auth/org/${id}`, data)
}

export function deleteOrgUnit(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/org/${id}`)
}
