/**
 * @module api/online
 * 在线用户管理 API 模块。
 * 提供在线用户列表查询和强制踢出接口。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

export interface OnlineUser {
  userId: number
  username: string
  jti: string
}

export function listOnlineUsers() {
  return request.get<ApiResponse<OnlineUser[]>>('/auth/online/list')
}

export function kickOnlineUser(userId: number) {
  return request.delete<ApiResponse<void>>(`/auth/online/${userId}`)
}
