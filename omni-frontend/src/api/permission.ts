/**
 * @module api/permission
 * 权限管理 API 模块。
 * 提供权限树的只读查询接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { MenuNode } from './menu'

/** 权限树节点（包含 checked 状态） */
export interface PermissionNode extends MenuNode {
  /** 是否已分配给角色 */
  checked: boolean
}

/**
 * 获取全量权限树（用于角色权限分配页面）。
 *
 * @returns 权限树节点数组
 */
export function fetchPermissionTree() {
  return request.get<ApiResponse<PermissionNode[]>>('/auth/permission/tree')
}

/**
 * 获取指定角色的权限树（已分配节点 checked=true）。
 *
 * @param roleId - 角色 ID
 * @returns 权限树节点数组（含 checked 状态）
 */
export function fetchRolePermissionTree(roleId: number) {
  return request.get<ApiResponse<PermissionNode[]>>(`/auth/permission/role/${roleId}`)
}
