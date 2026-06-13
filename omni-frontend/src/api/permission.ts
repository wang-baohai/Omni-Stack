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

export function fetchPermissionTree() {
  return request.get<ApiResponse<PermissionNode[]>>('/auth/permission/tree')
}

export function fetchRolePermissionTree(roleId: number) {
  return request.get<ApiResponse<PermissionNode[]>>(`/auth/permission/role/${roleId}`)
}
