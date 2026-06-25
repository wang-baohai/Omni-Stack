/**
 * @module api/userJobType
 * 任务类型管理 API 模块。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 任务类型 */
export interface UserJobType {
  id: number
  typeCode: string
  typeName: string
  description: string | null
  paramTemplate: string | null
  status: number
  createTime: string
  updateTime: string
}

/** 创建任务类型请求 */
export interface CreateUserJobTypeRequest {
  typeCode: string
  typeName: string
  description?: string
  paramTemplate?: string | null
}

/** 更新任务类型请求 */
export interface UpdateUserJobTypeRequest {
  typeName?: string
  description?: string
  paramTemplate?: string | null
}

/**
 * 分页查询任务类型列表。
 *
 * @param params - 查询参数（编码、名称、状态、分页）
 * @returns 分页任务类型列表
 */
export function listUserJobTypes(params: {
  typeCode?: string
  typeName?: string
  status?: number
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<UserJobType>>>('/job/user-job-type/list', { params })
}

/**
 * 按 ID 查询任务类型详情。
 *
 * @param id - 任务类型 ID
 * @returns 任务类型实体
 */
export function getUserJobType(id: number) {
  return request.get<ApiResponse<UserJobType>>(`/job/user-job-type/${id}`)
}

/**
 * 查询所有启用类型（供前端下拉使用）。
 *
 * @returns 启用状态的任务类型数组
 */
export function listEnabledJobTypes() {
  return request.get<ApiResponse<UserJobType[]>>('/job/user-job-type/types')
}

/**
 * 创建任务类型。
 *
 * @param data - 创建请求
 * @returns 创建成功的任务类型实体
 */
export function createUserJobType(data: CreateUserJobTypeRequest) {
  return request.post<ApiResponse<UserJobType>>('/job/user-job-type', data)
}

/**
 * 更新任务类型。
 *
 * @param id - 任务类型 ID
 * @param data - 更新请求
 * @returns 更新后的任务类型实体
 */
export function updateUserJobType(id: number, data: UpdateUserJobTypeRequest) {
  return request.put<ApiResponse<UserJobType>>(`/job/user-job-type/${id}`, data)
}

/**
 * 删除任务类型。
 *
 * @param id - 任务类型 ID
 * @returns 空结果
 */
export function deleteUserJobType(id: number) {
  return request.delete<ApiResponse<void>>(`/job/user-job-type/${id}`)
}

/**
 * 切换任务类型状态。
 *
 * @param id - 任务类型 ID
 * @param status - 目标状态（1=启用，0=禁用）
 * @returns 空结果
 */
export function toggleUserJobTypeStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/job/user-job-type/${id}/status`, null, {
    params: { status },
  })
}
