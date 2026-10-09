/**
 * @module api/systemJob
 * 系统任务管理 API 模块。
 * 路径前缀 /job/system-job，需要 RBAC 权限码。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'
import type { DynamicFieldType } from '@/types/schema'

/** 参数定义（来自后端 @ParamDef 注解） */
export interface ParamDefInfo {
  name: string
  label: string
  type: DynamicFieldType
  defaultValue: string
  required: boolean
  min: number
  max: number
}

/** 系统任务视图对象 */
export interface SystemJob {
  handlerName: string
  name: string
  description: string
  defaultCron: string
  routeStrategy: string
  paramDefs: ParamDefInfo[]
  xxlJobId: number | null
  actualCron: string | null
  actualParam: string | null
  status: 'UNREGISTERED' | 'RUNNING' | 'STOPPED'
}

/** 注册系统任务请求 */
export interface RegisterSystemJobRequest {
  handlerName: string
  cron: string
  params?: string
}

/**
 * 查询所有系统任务列表（合并元数据 + XXL-JOB 实际状态）。
 *
 * @returns 系统任务视图数组
 * @see api/myJob
 */
export function listSystemJobs() {
  return request.get<ApiResponse<SystemJob[]>>('/job/system-job/list')
}

/**
 * 注册系统任务到 XXL-JOB 调度中心。
 *
 * @param data - 注册请求（handlerName、cron、参数）
 * @returns 空结果
 */
export function registerSystemJob(data: RegisterSystemJobRequest) {
  return request.post<ApiResponse<void>>('/job/system-job/register', data)
}

/**
 * 启动系统任务。
 *
 * @param xxlJobId - XXL-JOB 任务 ID
 * @returns 空结果
 */
export function startSystemJob(xxlJobId: number) {
  return request.post<ApiResponse<void>>(`/job/system-job/${xxlJobId}/start`)
}

/**
 * 停止系统任务。
 *
 * @param xxlJobId - XXL-JOB 任务 ID
 * @returns 空结果
 */
export function stopSystemJob(xxlJobId: number) {
  return request.post<ApiResponse<void>>(`/job/system-job/${xxlJobId}/stop`)
}

/**
 * 立即触发系统任务执行。
 *
 * @param xxlJobId - XXL-JOB 任务 ID
 * @param param - 执行参数（可选）
 * @returns 空结果
 */
export function triggerSystemJob(xxlJobId: number, param?: string) {
  return request.post<ApiResponse<void>>(`/job/system-job/${xxlJobId}/trigger`, null, {
    params: param ? { param } : {},
  })
}

/**
 * 从 XXL-JOB 注销系统任务。
 *
 * @param xxlJobId - XXL-JOB 任务 ID
 * @returns 空结果
 */
export function unregisterSystemJob(xxlJobId: number) {
  return request.delete<ApiResponse<void>>(`/job/system-job/${xxlJobId}`)
}
