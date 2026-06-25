/**
 * @module api/org
 * 组织管理 API 模块。
 * 提供组织树的增删改查接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/** 组织单元实体 */
export interface SysOrgUnit {
  /** 组织单元 ID */
  id: number
  /** 租户 ID */
  tenantId: number
  /** 父级 ID */
  parentId: number
  /** 名称 */
  name: string
  /** 类型（DEPT/COMPANY/GROUP） */
  type: string
  /** 层级路径 */
  path: string
  /** 层级深度 */
  depth: number
  /** 排序号 */
  sort: number
  /** 状态（1=启用，0=禁用） */
  status: number
  /** 创建时间 */
  createTime: string
  /** 更新时间 */
  updateTime: string
}

/** 组织树节点（含子节点） */
export interface OrgUnitTreeNode extends SysOrgUnit {
  /** 子节点列表 */
  children: OrgUnitTreeNode[]
}

/** 创建组织单元请求 */
export interface CreateOrgUnitRequest {
  /** 父级 ID */
  parentId: number
  /** 名称 */
  name: string
  /** 类型 */
  type: string
  /** 排序号 */
  sort?: number
  /** 状态 */
  status?: number
}

/** 更新组织单元请求 */
export interface UpdateOrgUnitRequest {
  /** 名称 */
  name?: string
  /** 类型 */
  type?: string
  /** 排序号 */
  sort?: number
  /** 状态 */
  status?: number
}

/**
 * 获取组织树结构。
 *
 * @returns 组织树节点数组（根节点列表）
 */
export function fetchOrgTree() {
  return request.get<ApiResponse<OrgUnitTreeNode[]>>('/auth/org/tree')
}

/**
 * 按 ID 查询组织单元详情。
 *
 * @param id - 组织单元 ID
 * @returns 组织单元实体
 */
export function getOrgUnit(id: number) {
  return request.get<ApiResponse<SysOrgUnit>>(`/auth/org/${id}`)
}

/**
 * 创建组织单元。
 *
 * @param data - 创建请求
 * @returns 创建成功的组织单元实体
 */
export function createOrgUnit(data: CreateOrgUnitRequest) {
  return request.post<ApiResponse<SysOrgUnit>>('/auth/org', data)
}

/**
 * 更新组织单元。
 *
 * @param id - 组织单元 ID
 * @param data - 更新请求
 * @returns 更新后的组织单元实体
 */
export function updateOrgUnit(id: number, data: UpdateOrgUnitRequest) {
  return request.put<ApiResponse<SysOrgUnit>>(`/auth/org/${id}`, data)
}

/**
 * 删除组织单元。
 *
 * @param id - 组织单元 ID
 * @returns 空结果
 */
export function deleteOrgUnit(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/org/${id}`)
}
