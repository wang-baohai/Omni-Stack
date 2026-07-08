/**
 * @module api/mq-message
 * MQ 消息记录 API 模块。
 * 提供消息记录的分页查询、详情查看、重发和忽略操作。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

/** MQ 消息状态枚举 */
export enum MqMessageStatus {
  PENDING = 0,
  SENT = 1,
  FAILED = 2,
  DEAD_LETTER = 3,
  SKIPPED = 4,
}

/** MQ 消息状态显示信息 */
export const statusMap: Record<number, { label: string; tagType: string }> = {
  [MqMessageStatus.PENDING]: { label: 'PENDING', tagType: 'warning' },
  [MqMessageStatus.SENT]: { label: 'SENT', tagType: 'success' },
  [MqMessageStatus.FAILED]: { label: 'FAILED', tagType: 'danger' },
  [MqMessageStatus.DEAD_LETTER]: { label: 'DEAD_LETTER', tagType: 'danger' },
  [MqMessageStatus.SKIPPED]: { label: 'SKIPPED', tagType: 'info' },
}

/** MQ 消息记录 */
export interface MqMessage {
  id: number
  msgId: string
  topic: string
  bindingName: string
  tag: string | null
  msgKey: string | null
  payload: string
  brokerType: string
  status: number
  retryCount: number
  maxRetry: number
  nextRetryTime: string | null
  errorMsg: string | null
  serviceName: string
  tenantId: number | null
  createTime: string
  updateTime: string | null
}

/** MQ 消息查询参数 */
export interface MqMessageQuery {
  status?: number
  topic?: string
  msgKey?: string
  serviceName?: string
  beginTime?: string
  endTime?: string
  page: number
  size: number
}

/**
 * 分页查询消息记录。
 */
export function listMqMessages(params: MqMessageQuery) {
  return request.get<ApiResponse<PageResult<MqMessage>>>('/base/mq-message/list', {
    params,
  })
}

/**
 * 查询消息详情。
 */
export function getMqMessageDetail(msgId: string) {
  return request.get<ApiResponse<MqMessage>>(`/base/mq-message/${msgId}`)
}

/**
 * 手动重发消息。
 */
export function resendMessage(msgId: string) {
  return request.post<ApiResponse<null>>(`/base/mq-message/${msgId}/resend`)
}

/**
 * 标记忽略消息。
 */
export function skipMessage(msgId: string) {
  return request.post<ApiResponse<null>>(`/base/mq-message/${msgId}/skip`)
}
