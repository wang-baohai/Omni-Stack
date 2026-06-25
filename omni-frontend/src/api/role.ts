/**
 * @module api/role
 * 角色管理 API 模块。
 * 提供角色的增删改查、权限分配和部门分配接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 系统角色实体 */
export interface SysRole {
  /** 角色 ID */
  id: number
  /** 租户 ID */
  tenantId: number
  /** 角色编码 */
  roleCode: string
  /** 角色名称 */
  roleName: string
  /** 数据范围（ALL/DEPT/DEPT_AND_CHILD/SELF） */
  dataScope: string
  /** 排序号 */
  sort: number
  /** 状态（1=启用，0=禁用） */
  status: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/** 创建角色请求 */
export interface CreateRoleRequest {
  /** 角色编码 */
  roleCode: string
  /** 角色名称 */
  roleName: string
  /** 数据范围 */
  dataScope: string
  /** 排序号 */
  sort?: number
  /** 状态 */
  status?: number
  /** 权限 ID 列表 */
  permissionIds?: number[]
}

/** 更新角色请求 */
export interface UpdateRoleRequest {
  /** 角色名称 */
  roleName?: string
  /** 数据范围 */
  dataScope?: string
  /** 排序号 */
  sort?: number
  /** 状态 */
  status?: number
  /** 权限 ID 列表 */
  permissionIds?: number[]
}

/**
 * 分页查询角色列表。
 *
 * @param page - 页码
 * @param size - 每页大小
 * @returns 分页角色列表
 */
export function listRoles(page: number, size: number) {
  return request.get<ApiResponse<PageResult<SysRole>>>('/auth/role/list', {
    params: { page, size },
  })
}

/**
 * 按 ID 查询角色详情。
 *
 * @param id - 角色 ID
 * @returns 角色实体
 */
export function getRole(id: number) {
  return request.get<ApiResponse<SysRole>>(`/auth/role/${id}`)
}

/**
 * 创建角色。
 *
 * @param data - 创建请求
 * @returns 创建成功的角色实体
 */
export function createRole(data: CreateRoleRequest) {
  return request.post<ApiResponse<SysRole>>('/auth/role', data)
}

/**
 * 更新角色。
 *
 * @param id - 角色 ID
 * @param data - 更新请求
 * @returns 更新后的角色实体
 */
export function updateRole(id: number, data: UpdateRoleRequest) {
  return request.put<ApiResponse<SysRole>>(`/auth/role/${id}`, data)
}

/**
 * 删除角色。
 *
 * @param id - 角色 ID
 * @returns 空结果
 */
export function deleteRole(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/role/${id}`)
}

/**
 * 查询所有角色列表（不分页）。
 *
 * @returns 全部角色数组
 */
export function listAllRoles() {
  return request.get<ApiResponse<SysRole[]>>('/auth/role/all')
}

/**
 * 为角色分配权限。
 *
 * @param roleId - 角色 ID
 * @param permissionIds - 权限 ID 数组
 * @returns 空结果
 */
export function assignRolePermissions(roleId: number, permissionIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/permission/role/${roleId}/assign`, permissionIds)
}

/**
 * 查询角色已分配的权限 ID 列表。
 *
 * @param roleId - 角色 ID
 * @returns 权限 ID 数组
 */
export function getRolePermissionIds(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/permission/role/${roleId}`)
}

/**
 * 为角色分配数据部门。
 *
 * @param roleId - 角色 ID
 * @param deptIds - 部门 ID 数组
 * @returns 空结果
 */
export function assignRoleDepts(roleId: number, deptIds: number[]) {
  return request.post<ApiResponse<void>>(`/auth/role/${roleId}/depts`, deptIds)
}

/**
 * 查询角色已分配的数据部门 ID 列表。
 *
 * @param roleId - 角色 ID
 * @returns 部门 ID 数组
 */
export function getRoleDeptIds(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/auth/role/${roleId}/depts`)
}
