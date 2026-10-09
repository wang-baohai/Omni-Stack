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
  /** relay 投递中的瞬态：认领后、broker ack 前的中间态，成功转 SENT、失败转 FAILED。 */
  PROCESSING = 5,
}

/**
 * MQ 消息状态显示信息。
 * labelKey 为 i18n 键（四语翻译值由 locale 文件提供），禁止在此硬编码界面文案；
 * 枚举名（PENDING 等）仅作为数据字典值，不直接面向用户展示。
 */
export const statusMap: Record<number, { labelKey: string; tagType: string }> = {
  [MqMessageStatus.PENDING]: { labelKey: 'mqMessage.statusPending', tagType: 'warning' },
  [MqMessageStatus.SENT]: { labelKey: 'mqMessage.statusSent', tagType: 'success' },
  [MqMessageStatus.FAILED]: { labelKey: 'mqMessage.statusFailed', tagType: 'danger' },
  [MqMessageStatus.DEAD_LETTER]: { labelKey: 'mqMessage.statusDeadLetter', tagType: 'danger' },
  [MqMessageStatus.SKIPPED]: { labelKey: 'mqMessage.statusSkipped', tagType: 'info' },
  [MqMessageStatus.PROCESSING]: { labelKey: 'mqMessage.statusProcessing', tagType: 'primary' },
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

/** MQ Outbox 与异步投递能力运行状态。 */
export interface MqRelayRuntimeStatus {
  outboxWriteEnabled: boolean
  deliveryEnabled: boolean
  mode: 'FULL' | 'OUTBOX_ONLY'
}

/** 查询当前消息投递运行状态。 */
export function getMqRelayRuntimeStatus() {
  return request.get<ApiResponse<MqRelayRuntimeStatus>>('/base/mq-message/runtime')
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
