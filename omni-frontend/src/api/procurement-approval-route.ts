/**
 * @module api/procurement-approval-route
 * 请购审批规则配置、试算与覆盖分析 API。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export type ProcurementApprovalRouteStatus = 'ACTIVE' | 'INACTIVE'
export type WorkflowAvailability =
  | 'AVAILABLE'
  | 'NOT_CURRENT'
  | 'UNAVAILABLE'
  | 'MODEL_ARCHIVED'
  | 'NOT_FOUND'
  | 'LEGACY_CATEGORY'

export interface ApprovalNode {
  id: string
  name: string
  type: 'START' | 'END' | 'APPROVAL' | 'GATEWAY' | 'SERVICE'
  roleCode?: string
  approvalMode?: 'ALL' | 'ANY'
  description?: string
}

export interface ApprovalEdge {
  id: string
  name?: string
  source: string
  target: string
  defaultBranch: boolean
  conditionSummary?: string
}

export interface WorkflowModelMetadata {
  id: number
  modelId: number
  modelKey: string
  modelName: string
  category: string
  version: number
  publishTime: string
  processDefinitionId: string
  status: string
  approvalPreviewVersion: number
  availability: WorkflowAvailability
}

export interface ApprovalGraph {
  approvalPreviewVersion: number
  modelVersion: WorkflowModelMetadata
  nodes: ApprovalNode[]
  edges: ApprovalEdge[]
  hasBranches: boolean
  linearSummary: string[] | null
}

export interface ProcurementApprovalRoute {
  id: number
  routeCode: string
  routeName: string
  categoryCode: string
  minAmount: string
  maxAmount: string | null
  modelVersionId: number
  modelName?: string
  modelVersion?: number
  modelPublishTime?: string
  workflowAvailability: WorkflowAvailability
  priority: number
  status: ProcurementApprovalRouteStatus
  version: number
  createTime: string
  updateTime: string
}

export interface ProcurementApprovalRouteQuery {
  keyword?: string
  categoryCode?: string
  status?: ProcurementApprovalRouteStatus
  page: number
  size: number
}

export interface CreateProcurementApprovalRouteRequest {
  routeName: string
  categoryCode: string
  minAmount: string
  maxAmount?: string
  modelVersionId: number
  status?: ProcurementApprovalRouteStatus
}

export interface UpdateProcurementApprovalRouteRequest
  extends CreateProcurementApprovalRouteRequest {
  version: number
  status: ProcurementApprovalRouteStatus
}

export interface ApprovalWorkflowOption {
  modelVersionId: number
  modelId: number
  modelKey: string
  modelName: string
  category: 'purchase'
  version: number
  publishTime: string
  approvalPreviewVersion: number
}

export interface MatchPreviewRequest {
  categoryCode: string
  totalAmount: string
}

export interface MatchPreview {
  outcome: 'MATCHED' | 'NO_MATCH' | 'AMBIGUOUS' | 'WORKFLOW_UNAVAILABLE'
  routeId?: number
  routeName?: string
  routeCode?: string
  categoryCode: string
  effectiveCategoryCode?: string
  defaultRule: boolean
  minAmount?: string
  maxAmount?: string | null
  modelVersionId?: number
  modelName?: string
  modelVersion?: number
  publishTime?: string
  approvalGraph?: ApprovalGraph
  actionMessage: string
  conflictingRouteIds: number[]
}

export interface CoverageSegment {
  minAmount: string
  maxAmount: string | null
  outcome: 'COVERED' | 'GAP' | 'AMBIGUOUS'
  source: 'EXACT' | 'DEFAULT' | 'NONE'
  routeIds: number[]
  routeName?: string
  workflowAvailability: WorkflowAvailability | 'NOT_APPLICABLE' | 'CONFLICT'
}

export interface CategoryCoverage {
  categoryCode: string
  categoryName: string
  complete: boolean
  segments: CoverageSegment[]
  issues: string[]
}

export interface CoverageReport {
  generatedAt: string
  workflowAvailability: 'AVAILABLE' | 'UNAVAILABLE'
  allRulesInactive: boolean
  noDefaultRule: boolean
  invalidModelRouteIds: number[]
  categories: CategoryCoverage[]
}

export interface ImpactReport {
  routeId: number
  routeName: string
  coverage: CoverageReport
  gapSegmentCount: number
  ambiguousSegmentCount: number
  actionMessage: string
}

export function listProcurementApprovalRoutes(params: ProcurementApprovalRouteQuery) {
  return request.get<ApiResponse<PageResult<ProcurementApprovalRoute>>>(
    '/procurement/approval-route/list',
    {
      params: {
        ...params,
        page: Math.max(params.page, 1),
        size: Math.min(Math.max(params.size, 1), 100),
      },
    },
  )
}

export function listApprovalWorkflowOptions() {
  return request.get<ApiResponse<ApprovalWorkflowOption[]>>(
    '/procurement/approval-route/workflow-options',
  )
}

export function previewApprovalRouteMatch(data: MatchPreviewRequest) {
  return request.post<ApiResponse<MatchPreview>>(
    '/procurement/approval-route/match-preview',
    data,
  )
}

export function getApprovalRouteCoverage() {
  return request.get<ApiResponse<CoverageReport>>(
    '/procurement/approval-route/coverage',
  )
}

export function getApprovalRouteImpact(routeId: number) {
  return request.get<ApiResponse<ImpactReport>>(
    '/procurement/approval-route/impact',
    { params: { routeId } },
  )
}

export function createProcurementApprovalRoute(
  data: CreateProcurementApprovalRouteRequest,
) {
  return request.post<ApiResponse<ProcurementApprovalRoute>>(
    '/procurement/approval-route',
    data,
  )
}

export function updateProcurementApprovalRoute(
  id: number,
  data: UpdateProcurementApprovalRouteRequest,
) {
  return request.put<ApiResponse<ProcurementApprovalRoute>>(
    `/procurement/approval-route/${id}`,
    data,
  )
}

export function deleteProcurementApprovalRoute(id: number, version: number) {
  return request.delete<ApiResponse<void>>(`/procurement/approval-route/${id}`, {
    params: { version },
  })
}
