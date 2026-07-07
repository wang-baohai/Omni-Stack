/**
 * @module api/workflow
 * 工作流引擎 API 模块。
 * 路径前缀 /workflow，对应后端 omni-workflow 微服务。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

// ========== 类型定义 ==========

/** 流程定义 */
export interface ProcessDefinition {
  id: string
  key: string
  name: string
  category: string | null
  version: number
  deploymentId: string
  resourceName: string
  suspended: boolean
  tenantId: string | null
}

/** 流程实例扩展 */
export interface ProcessInstanceExt {
  id: number
  tenantId: number
  processInstanceId: string
  processKey: string
  businessKey: string | null
  title: string
  startUserId: number
  startUserName: string
  category: string | null
  status: number
  processDefinitionId: string | null
  createTime: string
  updateTime: string
}

/** 待办任务 */
export interface TodoTask {
  id: number
  tenantId: number
  taskId: string
  processInstanceId: string
  processKey: string
  taskName: string
  assigneeId: number
  assigneeName: string
  title: string
  category: string | null
  createTime: string
}

/** 部署流程请求 */
export interface DeployProcessRequest {
  name: string
  category?: string
  bpmnXml: string
}

/** 发起流程请求 */
export interface StartProcessRequest {
  processKey: string
  title: string
  businessKey?: string
  category?: string
  variables?: Record<string, unknown>
  simulateUserId?: number
  simulateUserName?: string
}

/** 审批请求 */
export interface ApprovalRequest {
  approved: boolean
  comment?: string
  variables?: Record<string, unknown>
}

/** 工作台统计 */
export interface WorkspaceStats {
  todoCount: number
  myInitiatedRunning: number
  myInitiatedTotal: number
}

/** 管理端统计 */
export interface AdminStats {
  definitionCount: number
  runningInstanceCount: number
  completedInstanceCount: number
  totalInstanceCount: number
}

/** 流程活动节点信息 */
/** 逐人审批状态 */
export interface AssigneeStatus {
  userId: string
  userName: string
  status: 'completed' | 'active' | 'auto-completed'
}

export interface ActivityInfo {
  activityId: string
  activityName: string | null
  activityType: string
  assignee: string | null
  assigneeName: string | null
  startTime: string | null
  endTime: string | null
  status: 'completed' | 'active' | 'pending'
  /** 逐人审批状态（仅会签节点有值） */
  assigneeStatuses: AssigneeStatus[] | null
  /** 已完成人数（仅 active 会签节点） */
  completedCount: number | null
  /** 总人数（仅 active 会签节点） */
  totalCount: number | null
}

/** 流程进度响应 */
export interface ProcessProgressResponse {
  completedActivities: ActivityInfo[]
  activeActivityIds: string[]
  allActivities: ActivityInfo[]
}

/** 审批记录条目 */
export interface ApprovalRecord {
  nodeName: string
  assigneeId: string
  assigneeName: string
  result: 'approved' | 'rejected' | 'auto-approved' | 'cancelled' | 'pending'
  comment: string | null
  approvalTime: string | null
}

// ========== 辅助函数 ==========

function tenantHeaders(): Record<string, string> {
  const userStore = useUserStore()
  const tenantId = getTenantIdFromToken(userStore.token)
  return tenantId ? { 'X-Tenant-Id': String(tenantId) } : {}
}

// ========== 流程定义 API ==========

/** 分页查询流程定义列表 */
export function listProcessDefinitions(params: {
  name?: string
  category?: string
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<ProcessDefinition>>>(
    '/workflow/process-definition/list',
    { params, headers: tenantHeaders() },
  )
}

/** 获取流程定义 BPMN XML */
export function getProcessDefinitionBpmn(processDefinitionId: string) {
  return request.get<ApiResponse<string>>(
    `/workflow/process-definition/${processDefinitionId}/bpmn`,
    { headers: tenantHeaders() },
  )
}

/** 部署流程定义 */
export function deployProcess(data: DeployProcessRequest) {
  return request.post<ApiResponse<string>>(
    '/workflow/process-definition/deploy',
    data,
    { headers: tenantHeaders() },
  )
}

/** 挂起流程定义 */
export function suspendProcessDefinition(processDefinitionId: string) {
  return request.put<ApiResponse<void>>(
    `/workflow/process-definition/${processDefinitionId}/suspend`,
    null,
    { headers: tenantHeaders() },
  )
}

/** 激活流程定义 */
export function activateProcessDefinition(processDefinitionId: string) {
  return request.put<ApiResponse<void>>(
    `/workflow/process-definition/${processDefinitionId}/activate`,
    null,
    { headers: tenantHeaders() },
  )
}

/** 删除部署 */
export function deleteDeployment(deploymentId: string) {
  return request.delete<ApiResponse<void>>(
    `/workflow/process-definition/${deploymentId}`,
    { headers: tenantHeaders() },
  )
}

// ========== 流程实例 API ==========

/** 发起流程 */
export function startProcess(data: StartProcessRequest) {
  return request.post<ApiResponse<string>>(
    '/workflow/process-instance/start',
    data,
    { headers: tenantHeaders() },
  )
}

/** 查询我发起的 */
export function listMyInitiated(params: {
  title?: string
  status?: number
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<ProcessInstanceExt>>>(
    '/workflow/process-instance/my-initiated',
    { params, headers: tenantHeaders() },
  )
}

/** 查询我已办的 */
export function listMyCompleted(params: {
  title?: string
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<ProcessInstanceExt>>>(
    '/workflow/process-instance/my-completed',
    { params, headers: tenantHeaders() },
  )
}

/** 终止流程 */
export function terminateProcess(processInstanceId: string, reason?: string) {
  return request.put<ApiResponse<void>>(
    `/workflow/process-instance/${processInstanceId}/terminate`,
    null,
    { params: { reason }, headers: tenantHeaders() },
  )
}

/** 管理员查询所有流程实例 */
export function listAllInstances(params: {
  title?: string
  status?: number
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<ProcessInstanceExt>>>(
    '/workflow/process-instance/list',
    { params, headers: tenantHeaders() },
  )
}

/** 获取流程实例流转进度 */
export function getProcessProgress(processInstanceId: string) {
  return request.get<ApiResponse<ProcessProgressResponse>>(
    `/workflow/process-instance/${processInstanceId}/progress`,
    { headers: tenantHeaders() },
  )
}

/** 获取流程实例审批记录 */
export function getApprovalRecords(processInstanceId: string) {
  return request.get<ApiResponse<ApprovalRecord[]>>(
    `/workflow/process-instance/${processInstanceId}/approval-records`,
    { headers: tenantHeaders() },
  )
}

// ========== 任务 API ==========

/** 查询待办任务 */
export function listTodoTasks(params: {
  title?: string
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<TodoTask>>>(
    '/workflow/task/todo',
    { params, headers: tenantHeaders() },
  )
}

/** 查询待办数量 */
export function getTodoCount() {
  return request.get<ApiResponse<number>>(
    '/workflow/task/count',
    { headers: tenantHeaders() },
  )
}

/** 获取任务表单数据 */
export function getTaskFormData(taskId: string) {
  return request.get<ApiResponse<Record<string, unknown>>>(
    `/workflow/task/${taskId}/form`,
    { headers: tenantHeaders() },
  )
}

// ========== 审批 API ==========

/** 审批通过/驳回 */
export function completeApproval(taskId: string, data: ApprovalRequest) {
  return request.post<ApiResponse<void>>(
    `/workflow/approval/${taskId}/complete`,
    data,
    { headers: tenantHeaders() },
  )
}

/** 加签 */
export function addSigner(taskId: string, newUserId: string) {
  return request.post<ApiResponse<void>>(
    `/workflow/approval/${taskId}/add-signer`,
    null,
    { params: { newUserId }, headers: tenantHeaders() },
  )
}

/** 减签 */
export function removeSigner(taskId: string, targetUserId: string) {
  return request.post<ApiResponse<void>>(
    `/workflow/approval/${taskId}/remove-signer`,
    null,
    { params: { targetUserId }, headers: tenantHeaders() },
  )
}

/** 委托 */
export function delegateTask(taskId: string, targetUserId: string) {
  return request.post<ApiResponse<void>>(
    `/workflow/approval/${taskId}/delegate`,
    null,
    { params: { targetUserId }, headers: tenantHeaders() },
  )
}

// ========== 统计 API ==========

/** 工作台统计 */
export function getWorkspaceStats() {
  return request.get<ApiResponse<WorkspaceStats>>(
    '/workflow/stats/workspace',
    { headers: tenantHeaders() },
  )
}

/** 管理端统计 */
export function getAdminStats() {
  return request.get<ApiResponse<AdminStats>>(
    '/workflow/stats/admin',
    { headers: tenantHeaders() },
  )
}
