/**
 * @module api/asset-disposal
 * 资产丢弃、报废申请及实物处置 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { AssetStatus } from './asset-asset'
import type { AssetOperationStatus } from './asset-transfer'

export type AssetDisposalType = 'DISCARD' | 'SCRAP'

export interface AssetDisposal {
  id: number
  disposalNo: string
  assetId: number
  assetNo: string
  assetName: string
  disposalType: AssetDisposalType
  reason: string
  residualValue: string | null
  disposalMethod: string | null
  status: AssetOperationStatus
  previousAssetStatus: AssetStatus
  workflowStartStatus: 'PENDING' | 'STARTED' | 'FAILED'
  processInstanceId: string | null
  approvedTime: string | null
  completedTime: string | null
  finalApproverUserId: number | null
  finalApproverRemark: string | null
  version: number
  createTime: string
}

export interface AssetDisposalQuery {
  keyword?: string
  disposalType?: AssetDisposalType
  status?: AssetOperationStatus
  page: number
  size: number
}

export interface CreateAssetDisposalRequest {
  assetId: number
  disposalType: AssetDisposalType
  reason: string
  residualValue?: string
  disposalMethod?: string
}

export function listAssetDisposals(params: AssetDisposalQuery) {
  return request.get<ApiResponse<PageResult<AssetDisposal>>>('/asset/disposal/list', {
    params: { ...params, page: Math.max(params.page, 1), size: Math.min(Math.max(params.size, 1), 100) },
  })
}

export function getAssetDisposal(id: number) {
  return request.get<ApiResponse<AssetDisposal>>(`/asset/disposal/${id}`)
}

export function getAssetDisposalApprovalView(id: number, taskId: string) {
  return request.get<ApiResponse<AssetDisposal>>(`/asset/disposal/${id}/approval-view`, {
    params: { taskId },
  })
}

export function createAssetDisposal(data: CreateAssetDisposalRequest) {
  return request.post<ApiResponse<AssetDisposal>>('/asset/disposal', data)
}

export function completeAssetDisposal(id: number, version: number) {
  return request.post<ApiResponse<AssetDisposal>>(`/asset/disposal/${id}/complete`, { version })
}

export function cancelAssetDisposal(id: number, version: number) {
  return request.post<ApiResponse<AssetDisposal>>(`/asset/disposal/${id}/cancel`, { version })
}

export function retryAssetDisposal(id: number, version: number) {
  return request.post<ApiResponse<AssetDisposal>>(`/asset/disposal/${id}/retry-start`, { version })
}
