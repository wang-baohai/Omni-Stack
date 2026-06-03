/**
 * PKCE (Proof Key for Code Exchange) 工具函数。
 * 用于 OAuth2 授权码流程中的安全参数生成。
 */

const CHARSET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~'

/**
 * 生成随机 Code Verifier（43-128 字符）。
 * 使用 Web Crypto API 确保密码学安全。
 */
export function generateCodeVerifier(): string {
  const array = new Uint8Array(64)
  crypto.getRandomValues(array)
  return Array.from(array, (byte) => CHARSET[byte % CHARSET.length]).join('')
}

/**
 * 从 Code Verifier 生成 Code Challenge（SHA-256 + base64url）。
 *
 * @param verifier Code Verifier 字符串
 * @returns base64url 编码的 Code Challenge
 */
export async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder()
  const data = encoder.encode(verifier)
  const digest = await crypto.subtle.digest('SHA-256', data)
  return base64UrlEncode(new Uint8Array(digest))
}

/**
 * 生成随机 state 参数用于 CSRF 防护。
 */
export function generateState(): string {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * base64url 编码（不含填充符）。
 */
function base64UrlEncode(buffer: Uint8Array): string {
  let binary = ''
  for (const byte of buffer) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}
