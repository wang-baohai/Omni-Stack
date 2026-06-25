/**
 * @module api/user
 * 用户管理 API 模块。
 * 提供用户的增删改查、角色分配和状态切换接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 系统用户实体 */
export interface SysUser {
  /** 用户 ID */
  id: number
  /** 租户 ID */
  tenantId: number
  /** 用户名（登录账号） */
  username: string
  /** 昵称 */
  nickname: string
  /** 邮箱 */
  email: string | null
  /** 手机号 */
  phone: string | null
  /** 头像 URL */
  avatar: string | null
  /** 性别（0=未知，1=男，2=女） */
  gender: number
  /** 主组织单元 ID */
  primaryUnitId: number
  /** 状态（1=启用，0=禁用） */
  status: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/**
 * 分页查询用户列表。
 *
 * @param page - 页码
 * @param size - 每页大小
 * @returns 分页用户列表
 */
export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysUser>>>('/auth/user/list', {
    params: { page, size },
  })
}

/**
 * 按 ID 查询用户详情。
 *
 * @param id - 用户 ID
 * @returns 用户实体
 */
export function getUser(id: number) {
  return request.get<ApiResponse<SysUser>>(`/auth/user/${id}`)
}

/**
 * 创建用户。
 *
 * @param data - 创建参数（用户名、密码、昵称等）
 * @returns 空结果
 */
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

/**
 * 更新用户信息。
 *
 * @param id - 用户 ID
 * @param data - 更新参数（部分字段）
 * @returns 空结果
 */
export function updateUser(id: number, data: Partial<SysUser>) {
  return request.put<ApiResponse<void>>(`/auth/user/${id}`, data)
}

/**
 * 删除用户。
 *
 * @param id - 用户 ID
 * @returns 空结果
 */
export function deleteUser(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/user/${id}`)
}

/**
 * 为用户分配角色。
 *
 * @param userId - 用户 ID
 * @param roleIds - 角色 ID 数组
 * @returns 空结果
 * @see api/role
 */
export function assignUserRoles(userId: number, roleIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/user/${userId}/roles`, roleIds)
}

/**
 * 查询用户已分配的角色 ID 列表。
 *
 * @param userId - 用户 ID
 * @returns 角色 ID 数组
 */
export function getUserRoleIds(userId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/user/${userId}/roles`)
}

/**
 * 切换用户状态（启用/禁用）。
 *
 * @param userId - 用户 ID
 * @param status - 目标状态（1=启用，0=禁用）
 * @returns 空结果
 */
export function toggleUserStatus(userId: number, status: number) {
  return request.put<ApiResponse<void>>(`/auth/user/${userId}/status`, null, {
    params: { status },
  })
}
