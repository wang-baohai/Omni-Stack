/**
 * @module api/procurement-material
 * 采购物料品类与物料目录 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementMaterialStatus = 'ACTIVE' | 'INACTIVE'

export interface ProcurementMaterialCategory {
  id: number
  parentId: number
  categoryCode: string
  categoryName: string
  sort: number
  status: number
  version: number
  children: ProcurementMaterialCategory[]
}

export interface ProcurementMaterial {
  id: number
  categoryId: number
  categoryCode: string
  categoryName: string
  materialCode: string
  materialName: string
  specification: string | null
  unit: string
  assetManaged: boolean
  status: ProcurementMaterialStatus
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementMaterialQuery {
  keyword?: string
  categoryId?: number
  status?: ProcurementMaterialStatus
  assetManaged?: boolean
  page: number
  size: number
}

export interface CreateProcurementCategoryRequest {
  parentId: number
  categoryCode: string
  categoryName: string
  sort?: number
  status?: number
}

export interface UpdateProcurementCategoryRequest {
  version: number
  parentId: number
  categoryName: string
  sort: number
  status: number
}

export interface CreateProcurementMaterialRequest {
  categoryId: number
  materialCode: string
  materialName: string
  specification?: string
  unit: string
  assetManaged: boolean
  status?: ProcurementMaterialStatus
}

export interface UpdateProcurementMaterialRequest
  extends Omit<CreateProcurementMaterialRequest, 'materialCode'> {
  version: number
  status: ProcurementMaterialStatus
}

export function listProcurementCategories() {
  return request.get<ApiResponse<ProcurementMaterialCategory[]>>(
    '/procurement/material/category/list',
  )
}

export function createProcurementCategory(data: CreateProcurementCategoryRequest) {
  return request.post<ApiResponse<ProcurementMaterialCategory>>(
    '/procurement/material/category',
    data,
  )
}

export function updateProcurementCategory(
  id: number,
  data: UpdateProcurementCategoryRequest,
) {
  return request.put<ApiResponse<ProcurementMaterialCategory>>(
    `/procurement/material/category/${id}`,
    data,
  )
}

export function deleteProcurementCategory(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/material/category/${id}`, {
    params: { version },
  })
}

export function listProcurementMaterials(params: ProcurementMaterialQuery) {
  return request.get<ApiResponse<PageResult<ProcurementMaterial>>>('/procurement/material/list', {
    params: {
      ...params,
      page: Math.max(params.page, 1),
      size: Math.min(Math.max(params.size, 1), 100),
    },
  })
}

export function getProcurementMaterial(id: number) {
  return request.get<ApiResponse<ProcurementMaterial>>(`/procurement/material/${id}`)
}

export function createProcurementMaterial(data: CreateProcurementMaterialRequest) {
  return request.post<ApiResponse<ProcurementMaterial>>('/procurement/material', data)
}

export function updateProcurementMaterial(
  id: number,
  data: UpdateProcurementMaterialRequest,
) {
  return request.put<ApiResponse<ProcurementMaterial>>(`/procurement/material/${id}`, data)
}

export function deleteProcurementMaterial(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/material/${id}`, {
    params: { version },
  })
}
