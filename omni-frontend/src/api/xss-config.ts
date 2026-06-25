/**
 * @module api/xss-config
 * XSS 防护配置 API 模块。
 * 提供 XSS 全局开关管理、黑名单规则增删改查接口。
 */
import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

/** XSS 防护设置 */
export interface XssSettings {
  enabled: boolean
  rules: BlacklistRule[]
}

/** XSS 黑名单规则 */
export interface BlacklistRule {
  id: number
  ruleName: string
  ruleType: string
  pattern: string
  enabled: number
  description: string | null
  sortOrder: number | null
}

/** 创建 XSS 规则请求 */
export interface CreateXssRuleRequest {
  ruleName: string
  ruleType: string
  pattern: string
  description?: string
  sortOrder?: number
}

/** 更新 XSS 规则请求 */
export interface UpdateXssRuleRequest {
  ruleName?: string
  ruleType?: string
  pattern?: string
  description?: string
  sortOrder?: number
  enabled?: number
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
 * 获取 XSS 防护设置（全局开关 + 规则列表）。
 *
 * @returns XSS 防护设置对象
 */
export function getXssSettings() {
  return request.get<ApiResponse<XssSettings>>('/auth/xss-config/settings', {
    headers: tenantHeaders(),
  })
}

/**
 * 切换 XSS 防护全局开关。
 *
 * @param enabled - 是否启用全局 XSS 防护
 * @returns 空结果
 */
export function toggleXssGlobal(enabled: boolean) {
  return request.put<ApiResponse<void>>('/auth/xss-config/toggle', null, {
    params: { enabled },
    headers: tenantHeaders(),
  })
}

/**
 * 分页查询 XSS 黑名单规则列表。
 *
 * @param page - 页码
 * @param size - 每页大小
 * @returns 分页规则列表
 */
export function listXssRules(page: number, size: number) {
  return request.get<ApiResponse<PageResult<BlacklistRule>>>('/auth/xss-config/rules/list', {
    params: { page, size },
    headers: tenantHeaders(),
  })
}

/**
 * 创建 XSS 黑名单规则。
 *
 * @param data - 创建请求
 * @returns 创建成功的规则实体
 */
export function createXssRule(data: CreateXssRuleRequest) {
  return request.post<ApiResponse<BlacklistRule>>('/auth/xss-config/rules', data, {
    headers: tenantHeaders(),
  })
}

/**
 * 更新 XSS 黑名单规则。
 *
 * @param id - 规则 ID
 * @param data - 更新请求
 * @returns 更新后的规则实体
 */
export function updateXssRule(id: number, data: UpdateXssRuleRequest) {
  return request.put<ApiResponse<BlacklistRule>>(`/auth/xss-config/rules/${id}`, data)
}

/**
 * 删除 XSS 黑名单规则。
 *
 * @param id - 规则 ID
 * @returns 空结果
 */
export function deleteXssRule(id: number) {
  return request.delete<ApiResponse<void>>(`/auth/xss-config/rules/${id}`)
}

/**
 * 切换单条 XSS 黑名单规则的启用状态。
 *
 * @param id - 规则 ID
 * @param enabled - 是否启用
 * @returns 空结果
 */
export function toggleXssRule(id: number, enabled: boolean) {
  return request.put<ApiResponse<void>>(`/auth/xss-config/rules/${id}/toggle`, null, {
    params: { enabled },
  })
}
