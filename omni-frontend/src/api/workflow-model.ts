/**
 * @module api/workflow-model
 * 工作流模型管理 + 身份查询 API 模块。
 * 对应后端 WorkflowModelController 和 WorkflowIdentityController。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

// ========== 类型定义 ==========

/** 流程模型 */
export interface ProcessModel {
  id: number
  tenantId: number
  modelKey: string
  modelName: string
  category: string | null
  status: number
  currentDraftVersionId: number | null
  currentPublishedVersionId: number | null
  createBy: string | null
  updateBy: string | null
  createTime: string
  updateTime: string
}

/** 流程模型版本 */
export interface ProcessModelVersion {
  id: number
  tenantId: number
  modelId: number
  version: number
  status: string
  bpmnXml: string | null
  designerJson: string | null
  xmlSha256: string | null
  deploymentId: string | null
  processDefinitionId: string | null
  engineProcessKey: string | null
  engineVersion: number | null
  publishTime: string | null
  publishBy: string | null
  createTime: string
  updateTime: string
}

/** 模型版本 VO（列表视图，不含 XML/JSON） */
export interface ModelVersionVO {
  id: number
  version: number
  status: string
  xmlSha256: string | null
  deploymentId: string | null
  processDefinitionId: string | null
  engineVersion: number | null
  publishBy: string | null
  publishTime: string | null
  createTime: string
}

/** 创建模型请求 */
export interface CreateModelRequest {
  modelKey: string
  modelName: string
  category?: string
  designerJson?: string
}

/** 保存草稿请求 */
export interface SaveDraftRequest {
  designerJson: string
  bpmnXml?: string
  modelName?: string
  category?: string
}

/** 校验结果 */
export interface ValidateResult {
  valid: boolean
  errors: string[]
  warnings: string[]
}

/** 发布结果 */
export interface PublishResult {
  versionId: number
  businessVersion: number
  deploymentId: string
  processDefinitionId: string
  engineVersion: number
}

/** 用户身份 VO */
export interface IdentityUserVO {
  userId: number
  username: string
  nickname: string
  unitId: number | null
  unitName: string | null
}

/** 角色身份 VO */
export interface IdentityRoleVO {
  id: number
  roleCode: string
  roleName: string
  dataScope: string | null
}

/** 组织树节点 VO */
export interface OrgTreeNodeVO {
  id: number
  parentId: number | null
  name: string
  type: string
  unitCode: string | null
  status: number
  children: OrgTreeNodeVO[] | null
}

/** 角色解析预览请求 */
export interface ResolvePreviewRequest {
  assignmentType: string
  roleCode?: string
  anchorType?: string
  anchorParams?: Record<string, unknown>
  scopeMode?: string
  simulateUserId?: number
}

/** 候选用户 */
export interface CandidateUser {
  userId: number
  username: string
  nickname: string
  unitName: string | null
}

/** 角色解析预览结果 */
export interface ResolvePreviewResult {
  candidateCount: number
  candidates: CandidateUser[]
}

// ========== 辅助函数 ==========

function tenantHeaders(): Record<string, string> {
  const userStore = useUserStore()
  const tenantId = getTenantIdFromToken(userStore.token)
  return tenantId ? { 'X-Tenant-Id': String(tenantId) } : {}
}

// ========== 模型管理 API ==========

/** 分页查询模型列表 */
export function listModels(params: {
  keyword?: string
  category?: string
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<ProcessModel>>>(
    '/workflow/model/list',
    { params, headers: tenantHeaders() },
  )
}

/** 获取单个模型详情 */
export function getModel(id: number) {
  return request.get<ApiResponse<ProcessModel>>(
    `/workflow/model/${id}`,
    { headers: tenantHeaders() },
  )
}

/** 创建模型 */
export function createModel(data: CreateModelRequest) {
  return request.post<ApiResponse<ProcessModel>>(
    '/workflow/model',
    data,
    { headers: tenantHeaders() },
  )
}

/** 保存草稿 */
export function saveDraft(id: number, data: SaveDraftRequest) {
  return request.put<ApiResponse<ProcessModelVersion>>(
    `/workflow/model/${id}/draft`,
    data,
    { headers: tenantHeaders() },
  )
}

/** 校验模型 */
export function validateModel(id: number) {
  return request.post<ApiResponse<ValidateResult>>(
    `/workflow/model/${id}/validate`,
    null,
    { headers: tenantHeaders() },
  )
}

/** 发布模型 */
export function publishModel(id: number) {
  return request.post<ApiResponse<PublishResult>>(
    `/workflow/model/${id}/publish`,
    null,
    { headers: tenantHeaders() },
  )
}

/** 获取模型版本列表 */
export function listVersions(id: number) {
  return request.get<ApiResponse<ModelVersionVO[]>>(
    `/workflow/model/${id}/versions`,
    { headers: tenantHeaders() },
  )
}

/** 获取指定版本详情 */
export function getVersion(versionId: number) {
  return request.get<ApiResponse<ProcessModelVersion>>(
    `/workflow/model/version/${versionId}`,
    { headers: tenantHeaders() },
  )
}

/** 删除模型 */
export function deleteModel(id: number) {
  return request.delete<ApiResponse<void>>(
    `/workflow/model/${id}`,
    { headers: tenantHeaders() },
  )
}

// ========== 身份查询 API ==========

/** 查询用户列表 */
export function listUsers(keyword?: string) {
  return request.get<ApiResponse<IdentityUserVO[]>>(
    '/workflow/identity/users',
    { params: { keyword }, headers: tenantHeaders() },
  )
}

/** 查询角色列表 */
export function listRoles() {
  return request.get<ApiResponse<IdentityRoleVO[]>>(
    '/workflow/identity/roles',
    { headers: tenantHeaders() },
  )
}

/** 获取组织架构树 */
export function getOrgTree() {
  return request.get<ApiResponse<OrgTreeNodeVO[]>>(
    '/workflow/identity/org-tree',
    { headers: tenantHeaders() },
  )
}

/** 获取组织单元下拉选项 */
export function getUnitOptions() {
  return request.get<ApiResponse<OrgTreeNodeVO[]>>(
    '/workflow/identity/unit-options',
    { headers: tenantHeaders() },
  )
}

/** 模拟解析审批候选人 */
export function resolvePreview(data: ResolvePreviewRequest) {
  return request.post<ApiResponse<ResolvePreviewResult>>(
    '/workflow/identity/resolve-preview',
    data,
    { headers: tenantHeaders() },
  )
}
