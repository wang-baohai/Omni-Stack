/**
 * 校验登录回跳地址，只允许当前应用内的绝对路径。
 *
 * @param value 路由 query 中的 redirect 值
 * @return 可用的站内路径；无效时返回 undefined
 */
export function safeAppRedirect(value: unknown): string | undefined {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) return undefined
  if (value.includes('\\') || [...value].some(char => char.charCodeAt(0) < 32)) return undefined
  if (['/login', '/portal-login'].some(path => value.startsWith(path))) return undefined
  return value
}
