import { isAxiosError } from 'axios'

/** 从未知异常中提取可安全展示的消息。 */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError(error)) {
    const data = error.response?.data
    if (typeof data === 'object' && data !== null && 'message' in data
      && typeof data.message === 'string' && data.message) {
      return data.message
    }
    if (error.message) return error.message
  }
  return error instanceof Error && error.message ? error.message : fallback
}

/** 判断 Element Plus 确认框是否由用户主动取消。 */
export function isUserCancelled(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}
