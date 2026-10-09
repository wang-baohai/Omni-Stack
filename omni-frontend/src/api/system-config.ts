/**
 * @module api/system-config
 * 系统配置 API 模块（D01）。
 * 本期唯一配置对象为登录保护策略。X-Tenant-Id 由 request 拦截器从 JWT 自动注入。
 */
import request from './request'
import type { ApiResponse } from '@/types/api'

/** 登录保护策略视图 */
export interface AuthLoginPolicy {
  failureThreshold: number
  failureWindowSeconds: number
  version: number
  source: 'DEFAULT' | 'TENANT_OVERRIDE'
  updateTime: string | null
  updatedBy: string | null
}

/** 登录保护策略更新请求 */
export interface LoginPolicyUpdatePayload {
  failureThreshold: number
  failureWindowSeconds: number
  expectedVersion: number
}

/**
 * 读取本租户登录保护策略（无持久化行时返回默认视图，不写库）。
 */
export function getLoginPolicy() {
  return request.get<ApiResponse<AuthLoginPolicy>>('/auth/system-config/login-policy')
}

/**
 * 更新本租户登录保护策略（乐观并发；陈旧版本返回业务码 409）。
 *
 * @param data - 更新负载
 */
export function updateLoginPolicy(data: LoginPolicyUpdatePayload) {
  return request.put<ApiResponse<AuthLoginPolicy>>('/auth/system-config/login-policy', data)
}
