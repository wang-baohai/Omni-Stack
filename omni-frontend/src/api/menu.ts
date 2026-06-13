/**
 * @module api/menu
 * 动态菜单 API 模块。
 * 提供从后端获取用户菜单树的接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/**
 * 菜单树节点类型。
 */
export interface MenuNode {
  /** 权限 ID */
  id: number
  /** 父级 ID */
  parentId: number
  /** 权限编码 */
  permissionCode: string
  /** 权限名称（用于显示） */
  permissionName: string
  /** 类型：DIRECTORY / MENU */
  type: string
  /** 物化路径 */
  path: string
  /** 深度 */
  depth: number
  /** 排序值 */
  sort: number
  /** 状态 */
  status: number
  /** 子节点 */
  children: MenuNode[]
}

/**
 * 获取当前用户的动态菜单树。
 */
export function fetchMenuTree() {
  return request.get<ApiResponse<MenuNode[]>>('/auth/menus')
}
