/**
 * @module api/user
 * 用户管理 API 模块。
 * 提供用户的增删改查、角色分配和状态切换接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface SysUser {
  id: number
  tenantId: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  avatar: string | null
  gender: number
  primaryUnitId: number
  status: number
  createTime: string
  updateTime: string
}

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysUser>>>('/auth/user/list', {
    params: { page, size },
  })
}

export function getUser(id: number) {
  return request.get<ApiResponse<SysUser>>(`/auth/user/${id}`)
}

export function createUser(data: {
  username: string
  password: string
  nickname?: string
  email?: string
  phone?: string
  gender?: number
  tenantId: number
}) {
  return request.post<ApiResponse<void>>('/auth/user', data)
}

export function updateUser(id: number, data: Partial<SysUser>) {
  return request.put<ApiResponse<void>>(`/auth/user/${id}`, data)
}

export function deleteUser(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/user/${id}`)
}

export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/user/${userId}/roles`, roleIds)
}

export function getUserRoleIds(userId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/user/${userId}/roles`)
}

export function toggleUserStatus(userId: number, status: number) {
  return request.put<ApiResponse<void>>(`/auth/user/${userId}/status`, null, {
    params: { status },
  })
}
