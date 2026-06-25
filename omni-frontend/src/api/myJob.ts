/**
 * @module api/myJob
 * 用户工作台 API 模块。
 * 路径前缀 /base/my-job，只需登录态，无需 RBAC 权限码。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { UserJobType } from './userJobType'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

/** 用户任务 */
export interface UserJob {
  id: number
  tenantId: number
  jobName: string
  jobType: string
  cronExpression: string
  jobParams: string | null
  status: number
  xxlJobId: number | null
  lastFireTime: string | null
  createTime: string
  updateTime: string
  createBy: string | null
  updateBy: string | null
}

/** 创建用户任务请求 */
export interface CreateUserJobRequest {
  jobName: string
  jobType: string
  cronExpression: string
  jobParams?: string
}

/** 更新用户任务请求 */
export interface UpdateUserJobRequest {
  jobName?: string
  cronExpression?: string
  jobParams?: string
}

/** 执行日志 */
export interface UserJobLog {
  id: number
  jobId: number
  tenantId: number | null
  jobName: string | null
  jobType: string | null
  fireTime: string | null
  executeTimeMs: number | null
  status: number | null
  errorMessage: string | null
  resultMessage: string | null
  createTime: string
}

/** 工作台任务统计 */
export interface MyJobStats {
  totalJobs: number
  todayExecuted: number
  todayFailed: number
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
 * 查询当前用户任务列表（分页）。
 *
 * @param params - 查询参数（任务名称、类型、状态、分页）
 * @returns 分页任务列表
 */
export function listMyJobs(params: {
  jobName?: string
  jobType?: string
  status?: number
  page: number
  size: number
}) {
  return request.get<ApiResponse<PageResult<UserJob>>>('/base/my-job/list', {
    params,
    headers: tenantHeaders(),
  })
}

/**
 * 查询启用状态的任务类型列表（供创建任务下拉使用）。
 *
 * @returns 启用状态的任务类型数组
 * @see api/userJobType
 */
export function getMyJobTypes() {
  return request.get<ApiResponse<UserJobType[]>>('/base/my-job/types', {
    headers: tenantHeaders(),
  })
}

/**
 * 查询当前用户任务统计数据（任务总数、今日执行/失败次数）。
 *
 * @returns 任务统计对象
 */
export function getMyJobStats() {
  return request.get<ApiResponse<MyJobStats>>('/base/my-job/stats', {
    headers: tenantHeaders(),
  })
}

/**
 * 创建用户任务（自动注册到 XXL-JOB）。
 *
 * @param data - 创建请求
 * @returns 创建成功的任务实体
 */
export function createMyJob(data: CreateUserJobRequest) {
  return request.post<ApiResponse<UserJob>>('/base/my-job', data, {
    headers: tenantHeaders(),
  })
}

/**
 * 更新用户任务（cron/参数变更时同步更新 XXL-JOB）。
 *
 * @param id - 任务 ID
 * @param data - 更新请求
 * @returns 更新后的任务实体
 */
export function updateMyJob(id: number, data: UpdateUserJobRequest) {
  return request.put<ApiResponse<UserJob>>(`/base/my-job/${id}`, data, {
    headers: tenantHeaders(),
  })
}

/**
 * 删除用户任务（同时从 XXL-JOB 注销）。
 *
 * @param id - 任务 ID
 * @returns 空结果
 */
export function deleteMyJob(id: number) {
  return request.delete<ApiResponse<void>>(`/base/my-job/${id}`, {
    headers: tenantHeaders(),
  })
}

/**
 * 切换用户任务状态（启用/停止对应 XXL-JOB 调度）。
 *
 * @param id - 任务 ID
 * @param status - 目标状态（1=启用，0=停止）
 * @returns 空结果
 */
export function toggleMyJobStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/base/my-job/${id}/status`, null, {
    params: { status },
    headers: tenantHeaders(),
  })
}

/**
 * 立即触发用户任务执行（通过 XXL-JOB triggerJob API）。
 *
 * @param id - 任务 ID
 * @returns 空结果
 */
export function triggerMyJob(id: number) {
  return request.post<ApiResponse<void>>(`/base/my-job/${id}/trigger`, null, {
    headers: tenantHeaders(),
  })
}

/**
 * 查询指定任务的执行日志（分页）。
 *
 * @param id - 任务 ID
 * @param params - 分页参数
 * @returns 分页执行日志列表
 */
export function getMyJobLogs(id: number, params: { page: number; size: number }) {
  return request.get<ApiResponse<PageResult<UserJobLog>>>(`/base/my-job/${id}/logs`, {
    params,
    headers: tenantHeaders(),
  })
}
