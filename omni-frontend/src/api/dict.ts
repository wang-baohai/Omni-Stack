/**
 * @module api/dict
 * 数据字典 API 模块。
 * 提供字典类型和字典数据的增删改查接口，以及缓存刷新功能。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

/** 字典类型 */
export interface DictType {
  id: number
  tenantId: number
  typeCode: string
  typeName: string
  remark: string | null
  sort: number
  status: number
  createTime: string
  updateTime: string
  createBy: string | null
  updateBy: string | null
}

/** 字典数据 */
export interface DictData {
  id: number
  tenantId: number
  typeCode: string
  dictValue: string
  dictLabel: string
  tagType: string | null
  remark: string | null
  sort: number
  status: number
  createTime: string
  updateTime: string
  createBy: string | null
  updateBy: string | null
}

/** 创建字典类型请求 */
export interface CreateDictTypeRequest {
  typeCode: string
  typeName: string
  remark?: string
  sort?: number
}

/** 更新字典类型请求 */
export interface UpdateDictTypeRequest {
  typeName?: string
  remark?: string
  sort?: number
  status?: number
}

/** 创建字典数据请求 */
export interface CreateDictDataRequest {
  typeCode: string
  dictValue: string
  dictLabel: string
  tagType?: string
  remark?: string
  sort?: number
}

/** 更新字典数据请求 */
export interface UpdateDictDataRequest {
  dictValue?: string
  dictLabel?: string
  tagType?: string
  remark?: string
  sort?: number
  status?: number
}

/**
 * 从 JWT 获取租户 ID，注入 X-Tenant-Id 请求头。
 *
 * @returns 包含 X-Tenant-Id 的请求头对象
 */
function tenantHeaders(): Record<string, string> {
  const userStore = useUserStore()
  const tenantId = getTenantIdFromToken(userStore.token)
  return tenantId ? { 'X-Tenant-Id': String(tenantId) } : {}
}

/**
 * 分页查询字典类型列表。
 *
 * @param params - 查询参数（编码、名称、状态、分页）
 * @returns 分页字典类型列表
 */
export function listDictTypes(params: {
  typeCode?: string
  typeName?: string
  status?: number
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<DictType>>>('/base/dict/type/list', {
    params,
    headers: tenantHeaders(),
  })
}

/**
 * 按 ID 查询字典类型详情。
 *
 * @param id - 字典类型 ID
 * @returns 字典类型实体
 */
export function getDictType(id: number) {
  return request.get<ApiResponse<DictType>>(`/base/dict/type/${id}`)
}

/**
 * 创建字典类型。
 *
 * @param data - 创建请求
 * @returns 创建成功的字典类型实体
 */
export function createDictType(data: CreateDictTypeRequest) {
  return request.post<ApiResponse<DictType>>('/base/dict/type', data, {
    headers: tenantHeaders(),
  })
}

/**
 * 更新字典类型。
 *
 * @param id - 字典类型 ID
 * @param data - 更新请求
 * @returns 更新后的字典类型实体
 */
export function updateDictType(id: number, data: UpdateDictTypeRequest) {
  return request.put<ApiResponse<DictType>>(`/base/dict/type/${id}`, data, {
    headers: tenantHeaders(),
  })
}

/**
 * 删除字典类型（级联删除字典数据）。
 *
 * @param id - 字典类型 ID
 * @returns 空结果
 */
export function deleteDictType(id: number) {
  return request.delete<ApiResponse<void>>(`/base/dict/type/${id}`)
}

/**
 * 切换字典类型状态。
 *
 * @param id - 字典类型 ID
 * @param status - 目标状态（1=启用，0=禁用）
 * @returns 空结果
 */
export function toggleDictTypeStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/base/dict/type/${id}/status`, null, {
    params: { status },
    headers: tenantHeaders(),
  })
}

/**
 * 按字典类型编码分页查询字典数据。
 *
 * @param params - 查询参数（类型编码、分页）
 * @returns 分页字典数据列表
 */
export function listDictData(params: { typeCode: string; page: number; size: number }) {
  return request.get<ApiResponse<PageResult<DictData>>>('/base/dict/data/list', {
    params,
    headers: tenantHeaders(),
  })
}

/**
 * 创建字典数据。
 *
 * @param data - 创建请求
 * @returns 创建成功的字典数据实体
 */
export function createDictData(data: CreateDictDataRequest) {
  return request.post<ApiResponse<DictData>>('/base/dict/data', data, {
    headers: tenantHeaders(),
  })
}

/**
 * 更新字典数据。
 *
 * @param id - 字典数据 ID
 * @param data - 更新请求
 * @returns 更新后的字典数据实体
 */
export function updateDictData(id: number, data: UpdateDictDataRequest) {
  return request.put<ApiResponse<DictData>>(`/base/dict/data/${id}`, data, {
    headers: tenantHeaders(),
  })
}

/**
 * 删除字典数据。
 *
 * @param id - 字典数据 ID
 * @returns 空结果
 */
export function deleteDictData(id: number) {
  return request.delete<ApiResponse<void>>(`/base/dict/data/${id}`)
}

/**
 * 刷新指定类型编码的字典缓存。
 *
 * @param typeCode - 字典类型编码
 * @returns 空结果
 */
export function refreshDictCache(typeCode: string) {
  return request.post<ApiResponse<void>>('/base/dict/data/refresh-cache', null, {
    params: { typeCode },
    headers: tenantHeaders(),
  })
}
