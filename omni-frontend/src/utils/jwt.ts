/**
 * @module jwt
 * JWT 令牌解析工具。
 * 用于从 JWT 中解码用户信息、权限列表和租户 ID 等 claims。
 */

/**
 * JWT Payload 结构定义。
 */
export interface JwtPayload {
  /** 用户 ID（字符串格式） */
  sub: string
  /** 租户 ID */
  tenant_id: number
  /** 用户名 */
  username: string
  /** 角色编码列表 */
  roles: string[]
  /** 权限编码列表 */
  scope: string[]
  /** JWT 唯一标识 */
  jti: string
  /** 签发时间（epoch 秒） */
  iat: number
  /** 过期时间（epoch 秒） */
  exp: number
}

/**
 * 解码 JWT Token 并返回 payload。
 * 注意：此方法仅解码，不验证签名。
 *
 * @param token JWT 字符串
 * @returns 解码后的 payload 对象
 */
export function decodeJwt(token: string): JwtPayload | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) {
      return null
    }
    // Base64Url 解码
    const base64Url = parts[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    )
    return JSON.parse(jsonPayload) as JwtPayload
  } catch {
    return null
  }
}

/**
 * 从 JWT 中提取权限列表。
 *
 * @param token JWT 字符串
 * @returns 权限编码数组
 */
export function getPermissionsFromToken(token: string): string[] {
  const payload = decodeJwt(token)
  return payload?.scope ?? []
}

/**
 * 从 JWT 中提取角色列表。
 *
 * @param token JWT 字符串
 * @returns 角色编码数组
 */
export function getRolesFromToken(token: string): string[] {
  const payload = decodeJwt(token)
  return payload?.roles ?? []
}

/**
 * 从 JWT 中提取租户 ID。
 *
 * @param token JWT 字符串
 * @returns 租户 ID
 */
export function getTenantIdFromToken(token: string): number | null {
  const payload = decodeJwt(token)
  return payload?.tenant_id ?? null
}

/**
 * 检查 JWT 是否已过期。
 *
 * @param token JWT 字符串
 * @returns 是否已过期
 */
export function isTokenExpired(token: string): boolean {
  const payload = decodeJwt(token)
  if (!payload?.exp) return true
  return Date.now() >= payload.exp * 1000
}
