/**
 * @module api/asset-transfer
 * 资产调拨申请与审批后交接 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { AssetStatus } from './asset-asset'

export type AssetOperationStatus =
  | 'PENDING_APPROVAL'
  | 'START_FAILED'
  | 'APPROVED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'CANCELLED'

export interface AssetTransfer {
  id: number
  transferNo: string
  assetId: number
  assetNo: string
  assetName: string
  fromUserId: number | null
  fromUnitId: number | null
  toUserId: number
  toUnitId: number
  fromLocation: string | null
  toLocation: string | null
  reason: string
  status: AssetOperationStatus
  previousAssetStatus: AssetStatus
  workflowStartStatus: 'PENDING' | 'STARTED' | 'FAILED'
  processInstanceId: string | null
  approvedTime: string | null
  completedTime: string | null
  version: number
  createTime: string
}

export interface AssetTransferQuery {
  keyword?: string
  status?: AssetOperationStatus
  page: number
  size: number
}

export interface CreateAssetTransferRequest {
  assetId: number
  toUserId: number
  toUnitId: number
  toLocation?: string
  reason: string
}

export function listAssetTransfers(params: AssetTransferQuery) {
  return request.get<ApiResponse<PageResult<AssetTransfer>>>('/asset/transfer/list', {
    params: { ...params, page: Math.max(params.page, 1), size: Math.min(Math.max(params.size, 1), 100) },
  })
}

export function getAssetTransfer(id: number) {
  return request.get<ApiResponse<AssetTransfer>>(`/asset/transfer/${id}`)
}

export function getAssetTransferApprovalView(id: number, taskId: string) {
  return request.get<ApiResponse<AssetTransfer>>(`/asset/transfer/${id}/approval-view`, {
    params: { taskId },
  })
}

export function createAssetTransfer(data: CreateAssetTransferRequest) {
  return request.post<ApiResponse<AssetTransfer>>('/asset/transfer', data)
}

export function completeAssetTransfer(id: number, version: number) {
  return request.post<ApiResponse<AssetTransfer>>(`/asset/transfer/${id}/complete`, { version })
}

export function cancelAssetTransfer(id: number, version: number) {
  return request.post<ApiResponse<AssetTransfer>>(`/asset/transfer/${id}/cancel`, { version })
}

export function retryAssetTransfer(id: number, version: number) {
  return request.post<ApiResponse<AssetTransfer>>(`/asset/transfer/${id}/retry-start`, { version })
}
