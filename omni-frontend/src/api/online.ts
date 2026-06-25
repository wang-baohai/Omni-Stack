/**
 * @module api/online
 * 在线用户管理 API 模块。
 * 提供在线用户列表查询和强制踢出接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/** 在线用户信息 */
export interface OnlineUser {
  /** 用户 ID */
  userId: number
  /** 用户名 */
  username: string
  /** JWT ID（令牌唯一标识） */
  jti: string
}

/**
 * 查询当前在线用户列表。
 *
 * @returns 在线用户数组
 */
export function listOnlineUsers() {
  return request.get<ApiResponse<OnlineUser[]>>('/auth/online/list')
}

/**
 * 强制踢出指定在线用户。
 *
 * @param userId - 用户 ID
 * @returns 空结果
 */
export function kickOnlineUser(userId: number) {
  return request.delete<ApiResponse<void>>(`/auth/online/${userId}`)
}
