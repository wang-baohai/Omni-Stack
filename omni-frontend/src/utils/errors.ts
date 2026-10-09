/**
 * @module errors
 * @description 错误展示与分类工具。
 *
 * 所有用户可见文本必须由 `error-display.ts` 的归一化结果经 `translate()` 解析得到；
 * 后端 `message` 与 Axios `error.message` 属于技术串，只能作为受控诊断数据挂在错误对象上，
 * 绝不上屏（依据：`docs/api-contract.md` §2、§2.5、§8.4；`error-contract-matrix.json`）。
 */
import { isAxiosError } from 'axios'
import type { AxiosError } from 'axios'
import { translate } from '@/i18n'
import type {
  ErrorDisplay,
  ErrorKind,
  ErrorRequestContext,
  ErrorSignal,
} from '@/utils/error-display'
import {
  classifyError,
  extractFieldNames,
  hasAuthorizationHeader,
  isCredentialEndpointUrl,
  nullableToUndefined,
  readBusinessCode,
  readServerMessage,
  readTraceId,
} from '@/utils/error-display'

/** 词条缺失时的最终兜底 key（四语均已存在）。 */
const LAST_RESORT_KEY = 'request.unknownError'

/** 排障凭据展示模板 key。 */
const TRACE_HINT_KEY = 'request.traceHint'

/**
 * 已归一化错误对象的对外契约。
 *
 * 由 `request.ts` 拦截器产出：业务错误为 `ApiError` 实例，
 * HTTP/传输层错误为被就地增强的 `AxiosError`（保留 `response`/`isAxiosError` 语义，
 * 使既有调用方的 `businessCode`/`httpStatus`/`response.status` 分类不回退）。
 */
export interface ApiErrorView {
  /** 归一化描述符。 */
  readonly display: ErrorDisplay
  /** 语义分类。 */
  readonly kind: ErrorKind
  /** 本地化 key。 */
  readonly i18nKey: string
  /** 业务码 `R.code`；无 `R` 体时为 `null`。 */
  readonly businessCode: number | null
  /** HTTP 状态；无响应时为 `null`。 */
  readonly httpStatus: number | null
  /** 排障凭据 `X-Trace-Id`。 */
  readonly traceId: string | null
  /** 当前语言下的安全展示文本。 */
  readonly displayMessage: string
  /** 标记位，用于类型收窄。 */
  readonly isApiError: true
}

/** 判断未知值是否为已归一化的错误对象。 */
function isApiErrorView(error: unknown): error is ApiErrorView {
  if (typeof error !== 'object' || error === null) return false
  const candidate = error as { isApiError?: unknown; display?: unknown; displayMessage?: unknown }
  return candidate.isApiError === true
    && typeof candidate.display === 'object'
    && candidate.display !== null
    && typeof candidate.displayMessage === 'string'
}

/**
 * 解析本地化文本：首选 key → 备选 key → 最终兜底，并追加 trace。
 *
 * `translate()` 对缺失 key 会原样返回 key，因此这里显式识别并降级，
 * 保证任何情况下都不会把内部 key 或后端原文显示给用户。
 *
 * @param display 归一化描述符
 * @returns 当前语言下的安全文案
 */
export function resolveDisplayText(display: ErrorDisplay): string {
  const text = translateKnown(display.i18nKey, display.params)
    ?? translateKnown(display.fallbackKey, display.params)
    ?? translateKnown(LAST_RESORT_KEY, {})
    ?? ''
  if (display.traceId === null) return text
  const hint = translateKnown(TRACE_HINT_KEY, { traceId: display.traceId })
  return hint === null ? text : `${text} ${hint}`
}

/** 翻译一个 key；缺失或空值返回 `null` 以便降级。 */
function translateKnown(key: string, params: Record<string, string | number>): string | null {
  const text = translate(key, params)
  if (text === key || text.length === 0) return null
  return text
}

/**
 * 按优先级依次尝试多个 key，返回第一个已落盘的译文。
 *
 * 用于标题等非主文案的展示位，保证 A 侧词条尚未集成时不会把 key 本身上屏。
 *
 * @param keys 候选 key（按优先级）
 * @param params 本地化命名参数
 * @returns 已存在的译文；全部缺失时返回 `null`
 */
export function translateExisting(
  keys: readonly string[],
  params: Record<string, string | number> = {},
): string | null {
  for (const key of keys) {
    const text = translateKnown(key, params)
    if (text !== null) return text
  }
  return null
}

/**
 * 从任意 `unknown` 值构造错误信号（供 `getErrorMessage` 等非拦截器上下文复用）。
 *
 * @param error 捕获到的错误
 * @returns 归一化描述符；无法判定时返回 `null`
 */
export function describeError(error: unknown): ErrorDisplay | null {
  if (isApiErrorView(error)) return error.display
  if (!isAxiosError(error)) return null
  return classifyError(buildSignalFromAxiosError(error))
}

/**
 * 从 Axios 错误提取归一化输入。
 *
 * 只读取码值与响应头，不读取任何自然语言文案参与判定。
 *
 * @param error Axios 错误
 * @returns 错误信号
 */
export function buildSignalFromAxiosError(error: AxiosError): ErrorSignal {
  const response = error.response
  const data = response?.data
  const serverMessage = readServerMessage(data)
  return {
    httpStatus: typeof response?.status === 'number' ? response.status : null,
    businessCode: readBusinessCode(data),
    axiosErrorCode: typeof error.code === 'string' ? error.code : null,
    hasBody: data !== undefined && data !== null,
    traceId: readTraceId(response?.headers),
    context: readRequestContext(error.config?.url, error.config?.headers),
    fieldNames: extractFieldNames(serverMessage),
  }
}

/**
 * 由请求上下文构造 401 判别所需信息。
 *
 * @param url 请求 URL
 * @param headers 请求头
 * @returns 请求上下文
 */
export function readRequestContext(url: unknown, headers: unknown): ErrorRequestContext {
  return {
    isCredentialEndpoint: isCredentialEndpointUrl(url),
    hasActiveSession: hasAuthorizationHeader(headers),
  }
}

/**
 * 归一化错误实体：`Error` 子类，携带完整类型化的分类信息与本地化文本。
 *
 * `message` 被设置为安全文案，使历史调用方 `ElMessage.error(err.message)` 也不再泄漏；
 * 后端/Axios 原始技术串只保留在不可见的 `technicalDetail` 字段中供诊断。
 */
export class ApiError extends Error {
  /** 归一化描述符。 */
  readonly display: ErrorDisplay
  /** 语义分类。 */
  readonly kind: ErrorKind
  /** 本地化 key。 */
  readonly i18nKey: string
  /** 业务码。 */
  readonly businessCode: number | null
  /** HTTP 状态。 */
  readonly httpStatus: number | null
  /** 排障凭据。 */
  readonly traceId: string | null
  /** 后端/Axios 原始技术串，仅诊断用，禁止上屏。 */
  readonly technicalDetail: string | null
  /** 标记位。 */
  readonly isApiError = true as const

  /**
   * @param display 归一化描述符
   * @param technicalDetail 后端或 Axios 原始技术串（不上屏）
   */
  constructor(display: ErrorDisplay, technicalDetail: string | null = null) {
    super(resolveDisplayText(display))
    this.name = 'ApiError'
    this.display = display
    this.kind = display.kind
    this.i18nKey = display.i18nKey
    this.businessCode = display.businessCode
    this.httpStatus = display.httpStatus
    this.traceId = display.traceId
    this.technicalDetail = technicalDetail
  }

  /** 当前语言下的安全展示文本（随语言切换实时解析）。 */
  get displayMessage(): string {
    return resolveDisplayText(this.display)
  }
}

/**
 * 就地增强 `AxiosError`：附加归一化分类信息，同时保留 `response`/`isAxiosError` 语义。
 *
 * 现有调用方（系统配置页）依赖 `businessCode`/`httpStatus`/`response.status` 做分支，
 * 并用 `=== undefined` 识别「无响应（网络错误）」，因此无响应时这两个遗留字段暴露
 * `undefined`（而非 `null`），保证该分支与改动前等价；描述符内部仍为 `number | null`。
 *
 * @param error Axios 错误
 * @param display 归一化描述符
 * @returns 增强后的错误对象
 */
export function withApiErrorView(error: AxiosError, display: ErrorDisplay): AxiosError {
  const technical = readServerMessage(error.response?.data) ?? error.message
  Object.defineProperty(error, 'display', { value: display, configurable: true })
  Object.defineProperty(error, 'kind', { value: display.kind, configurable: true })
  Object.defineProperty(error, 'i18nKey', { value: display.i18nKey, configurable: true })
  Object.defineProperty(error, 'businessCode', {
    value: nullableToUndefined(display.businessCode),
    configurable: true,
  })
  Object.defineProperty(error, 'httpStatus', {
    value: nullableToUndefined(display.httpStatus),
    configurable: true,
  })
  Object.defineProperty(error, 'traceId', { value: display.traceId, configurable: true })
  Object.defineProperty(error, 'technicalDetail', { value: technical, configurable: true })
  Object.defineProperty(error, 'isApiError', { value: true, configurable: true })
  Object.defineProperty(error, 'displayMessage', {
    configurable: true,
    get(): string {
      return resolveDisplayText(display)
    },
  })
  // 历史调用方直接打印 `error.message`：同样替换为安全本地化文案，技术串不外泄。
  error.message = resolveDisplayText(display)
  return error
}

/**
 * 从未知异常中提取可安全展示的消息。
 *
 * 向后兼容：签名与返回类型不变。内部不再裸显后端中文或 Axios 技术串，
 * 而是返回归一化错误对象的安全文案；无法归一化时才使用调用方提供的 `fallback`。
 *
 * @param error 捕获到的错误
 * @param fallback 调用方给出的本地化兜底文案
 * @returns 可展示文本
 */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isApiErrorView(error)) return error.displayMessage
  if (isAxiosError(error)) return resolveDisplayText(classifyError(buildSignalFromAxiosError(error)))
  // 纯本地异常（非 HTTP/Axios 来源）沿用原信息，避免削弱本地调试价值。
  if (error instanceof Error && error.message) return error.message
  return fallback
}

/** 判断 Element Plus 确认框是否由用户主动取消。 */
export function isUserCancelled(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}
