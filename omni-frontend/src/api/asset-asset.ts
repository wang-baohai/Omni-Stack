/**
 * @module api/asset-asset
 * 资产台账、我的资产与生命周期命令 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** 资产管理员或使用人候选。 */
export interface AssetUserOption {
  id: number
  username: string
  nickname: string | null
  primaryUnitId: number
  avatar: string | null
}

/** 可发起调拨或处置的资产候选。 */
export interface AssetOperationOption {
  id: number
  assetNo: string
  name: string
  status: AssetStatus
  currentUserId: number | null
  currentUnitId: number | null
}

/** 已批准供应商候选。 */
export interface AssetSupplierOption {
  id: number
  supplierNo: string
  name: string
  status: string
  levelCode: string | null
  categoryCode: string | null
}

/** 搜索当前租户内启用的资产用户候选。 */
export function listAssetUserOptions(keyword?: string) {
  return request.get<ApiResponse<AssetUserOption[]>>('/asset/options/users', {
    params: { keyword: keyword || undefined, limit: 30 },
  })
}

/** 搜索当前数据范围内可发起调拨或处置的资产。 */
export function listAssetOperationOptions(type: 'transfer' | 'disposal', keyword?: string) {
  return request.get<ApiResponse<AssetOperationOption[]>>(`/asset/options/${type}-assets`, {
    params: { keyword: keyword || undefined, limit: 30 },
  })
}

/** 搜索当前租户已批准的供应商候选。 */
export function listAssetSupplierOptions(keyword?: string) {
  return request.get<ApiResponse<AssetSupplierOption[]>>('/asset/options/suppliers', {
    params: { keyword: keyword || undefined, limit: 30 },
  })
}

export type AssetStatus =
  | 'IN_STOCK'
  | 'ALLOCATED'
  | 'IN_USE'
  | 'MAINTENANCE'
  | 'TRANSFER'
  | 'DISPOSAL_PENDING'
  | 'DISPOSED'
  | 'SCRAPPED'

export type MyAssetStatus =
  | 'ALLOCATED'
  | 'IN_USE'
  | 'MAINTENANCE'
  | 'TRANSFER'
  | 'DISPOSAL_PENDING'

export interface AssetSummary {
  id: number
  assetNo: string
  name: string
  categoryCode: string
  specification: string | null
  brand: string | null
  model: string | null
  purchaseAmount: string | null
  currencyCode: string | null
  locationCode: string | null
  status: AssetStatus
  currentUserId: number | null
  currentUnitId: number | null
  ownerUserId: number
  ownerUnitId: number
  activeOperationType: 'TRANSFER' | 'DISPOSAL' | null
  activeOperationId: number | null
  version: number
  createTime: string
  updateTime: string
}

export interface AssetDetail extends AssetSummary {
  supplierId: number | null
  supplierNameSnapshot: string | null
  sourcePoId: number | null
  sourceGrId: number | null
  sourceGrLineId: number | null
  sourceUnitSequence: number | null
  sourcePoNo: string | null
  sourceGrNo: string | null
  purchaseDate: string | null
  allocatedTime: string | null
  warrantyExpiryDate: string | null
  expectedLifeYears: number | null
  remark: string | null
}

export interface AssetHistory {
  id: number
  assetId: number
  fromStatus: AssetStatus | null
  toStatus: AssetStatus
  changedByUserId: number
  changedTime: string
  remark: string | null
}

export interface AssetQuery {
  keyword?: string
  status?: AssetStatus
  categoryCode?: string
  ownerUnitId?: number
  locationCode?: string
  page: number
  size: number
}

export interface MyAssetQuery {
  keyword?: string
  status?: MyAssetStatus
  categoryCode?: string
  page: number
  size: number
}

export interface SaveAssetRequest {
  name: string
  categoryCode: string
  specification?: string
  brand?: string
  model?: string
  supplierId?: number
  supplierNameSnapshot?: string
  purchaseDate?: string
  purchaseAmount?: string
  currencyCode: string
  locationCode?: string
  warrantyExpiryDate?: string
  expectedLifeYears?: number
  remark?: string
  ownerUserId: number
  ownerUnitId: number
}

export interface UpdateAssetRequest extends Omit<SaveAssetRequest, 'locationCode'> {
  version: number
}

export function listAssets(params: AssetQuery) {
  return request.get<ApiResponse<PageResult<AssetSummary>>>('/asset/asset/list', {
    params: { ...params, page: Math.max(params.page, 1), size: Math.min(Math.max(params.size, 1), 100) },
  })
}

export function listMyAssets(params: MyAssetQuery) {
  return request.get<ApiResponse<PageResult<AssetSummary>>>('/asset/asset/my', {
    params: { ...params, page: Math.max(params.page, 1), size: Math.min(Math.max(params.size, 1), 100) },
  })
}

export function getAsset(id: number) {
  return request.get<ApiResponse<AssetDetail>>(`/asset/asset/${id}`)
}

export function getAssetHistory(id: number, page = 1, size = 100) {
  return request.get<ApiResponse<PageResult<AssetHistory>>>(`/asset/asset/${id}/history`, {
    params: { page: Math.max(page, 1), size: Math.min(Math.max(size, 1), 100) },
  })
}

export function createAsset(data: SaveAssetRequest) {
  return request.post<ApiResponse<AssetDetail>>('/asset/asset', data)
}

export function updateAsset(id: number, data: UpdateAssetRequest) {
  return request.put<ApiResponse<AssetDetail>>(`/asset/asset/${id}`, data)
}

export function deleteAsset(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/asset/asset/${id}`, { params: { version } })
}

export function allocateAsset(
  id: number,
  data: { version: number; targetUserId: number; targetUnitId: number; remark?: string },
) {
  return request.post<ApiResponse<AssetDetail>>(`/asset/asset/${id}/allocate`, data)
}

export function acceptAsset(id: number, version: number, remark?: string) {
  return request.post<ApiResponse<AssetDetail>>(`/asset/asset/${id}/accept`, { version, remark })
}

export function returnAsset(id: number, version: number, remark?: string) {
  return request.post<ApiResponse<AssetDetail>>(`/asset/asset/${id}/return`, { version, remark })
}

export function startAssetMaintenance(id: number, version: number, remark?: string) {
  return request.post<ApiResponse<AssetDetail>>(`/asset/asset/${id}/maintenance/start`, {
    version,
    remark,
  })
}

export function completeAssetMaintenance(id: number, version: number, remark?: string) {
  return request.post<ApiResponse<AssetDetail>>(`/asset/asset/${id}/maintenance/complete`, {
    version,
    remark,
  })
}
