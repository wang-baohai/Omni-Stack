/**
 * @module api/role
 * 角色管理 API 模块。
 * 提供角色的增删改查、权限分配和部门分配接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface SysRole {
  id: number
  tenantId: number
  roleCode: string
  roleName: string
  dataScope: string
  sort: number
  status: number
  createTime: string
  updateTime: string
}

export interface CreateRoleRequest {
  roleCode: string
  roleName: string
  dataScope: string
  sort?: number
  status?: number
  permissionIds?: number[]
}

export interface UpdateRoleRequest {
  roleName?: string
  dataScope?: string
  sort?: number
  status?: number
  permissionIds?: number[]
}

export function listRoles(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysRole>>>('/auth/role/list', {
    params: { page, size },
  })
}

export function getRole(id: number) {
  return request.get<ApiResponse<SysRole>>(`/auth/role/${id}`)
}

export function createRole(data: CreateRoleRequest) {
  return request.post<ApiResponse<SysRole>>('/auth/role', data)
}

export function updateRole(id: number, data: UpdateRoleRequest) {
  return request.put<ApiResponse<SysRole>>(`/auth/role/${id}`, data)
}

export function deleteRole(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/role/${id}`)
}

export function listAllRoles() {
  return request.get<ApiResponse<SysRole[]>>('/auth/role/all')
}

export function assignRolePermissions(roleId: number, permissionIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/permission/role/${roleId}/assign`, permissionIds)
}

export function getRolePermissionIds(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/permission/role/${roleId}`)
}

export function assignRoleDepts(roleId: number, deptIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/role/${roleId}/depts`, deptIds)
}

export function getRoleDeptIds(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/role/${roleId}/depts`)
}
