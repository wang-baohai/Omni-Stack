/**
 * @module error-display
 * @description 错误展示层归一化模块（纯函数、无副作用、不依赖 Axios 实例与 i18n 实例）。
 *
 * 判定主键**只能**是 `HTTP status + R.code +（无响应时）Axios error.code`；
 * 依据见 `docs/api-contract.md` §2 错误码表（:87-125）、§2.5 前端处理（:135-145）、
 * §8.4 trace 合同（:344-357）与 `error-contract-matrix.json` 的 `decisions_locked_for_s05`。
 *
 * 硬性规则：
 * - 禁止任何中文字面量匹配参与判定；
 * - 后端 `message` 与 Axios `error.message` 一律不得作为用户可见文案上屏；
 * - 输出只包含本地化 key、参数与 trace，展示文本由调用层经 `translate()` 解析；
 * - 未知码一律落到通用安全文案，绝不回落到后端原文。
 */

/** 错误语义分类：由稳定码值推导，与后端自然语言文案无关。 */
export type ErrorKind =
  | 'credentials'
  | 'sessionExpired'
  | 'unauthenticated'
  | 'permissionDenied'
  | 'validation'
  | 'validationFields'
  | 'notFound'
  | 'conflict'
  | 'resourceGone'
  | 'accountLocked'
  | 'rateLimited'
  | 'serverError'
  | 'dependencyUnavailable'
  | 'badGateway'
  | 'network'
  | 'timeout'
  | 'unknown'

/** 请求上下文：401 靠它区分「会话过期」与「登录凭据校验失败」，而非任何文案匹配。 */
export interface ErrorRequestContext {
  /** 该请求是否由凭据表单端点（登录 / 会话登录 / 注册）发出。 */
  readonly isCredentialEndpoint: boolean
  /** 发出该请求时是否已携带会话凭据（Authorization 头）。 */
  readonly hasActiveSession: boolean
}

/** 归一化输入：只承载机器可判别位。 */
export interface ErrorSignal {
  /** HTTP 状态码；无响应（断网/超时）时为 `null`。 */
  readonly httpStatus: number | null
  /** 响应体 `R.code`；非 `R` 形态或无 body 时为 `null`。 */
  readonly businessCode: number | null
  /** Axios 传输层错误码（`ECONNABORTED` / `ERR_NETWORK` 等）；有响应时为 `null`。 */
  readonly axiosErrorCode: string | null
  /** 是否存在响应体（用于区分网关默认错误页与 `R` 结构，随描述符透传给调用方）。 */
  readonly hasBody: boolean
  /** 从响应头读取的 `X-Trace-Id`。 */
  readonly traceId: string | null
  /** 请求上下文；缺省时按「更安全的重新登录」处理。 */
  readonly context?: ErrorRequestContext
  /** 从校验类响应中提取的 ASCII 字段名（不含任何后端中文原文）。 */
  readonly fieldNames?: readonly string[]
}

/** 归一化输出：本地化 key + 参数 + trace，以及调用方分类所需的稳定事实。 */
export interface ErrorDisplay {
  /** 语义分类。 */
  readonly kind: ErrorKind
  /** 首选本地化 key（由展示层经 `translate()` 解析）。 */
  readonly i18nKey: string
  /** 首选 key 尚未落盘时使用的既有安全 key，避免把 key 本身显示给用户。 */
  readonly fallbackKey: string
  /** 本地化命名参数；值只可能是 ASCII 字段名或 trace，绝不含后端文案。 */
  readonly params: Record<string, string | number>
  /** 用户可见排障凭据（`X-Trace-Id`）。 */
  readonly traceId: string | null
  /** 原始 HTTP 状态，供调用方分类。 */
  readonly httpStatus: number | null
  /** 原始业务码，供调用方分类。 */
  readonly businessCode: number | null
  /** 是否存在响应体。 */
  readonly hasBody: boolean
  /** 是否允许执行安全登出 + 回跳登录页；仅「会话过期」为 `true`。 */
  readonly shouldLogout: boolean
  /** 是否必须保留用户已输入内容与会话（400/401 凭据失败/403）。 */
  readonly preserveInput: boolean
}

/**
 * 兼容既有调用方的「无值」判定：页面用 `=== undefined` 区分「无响应（网络错误）」，
 * 因此挂在错误对象上的遗留字段必须把 `null` 收敛为 `undefined`，
 * 而归一化描述符内部仍保持 `number | null` 的完整类型。
 *
 * @param value 可能为 `null` 的值
 * @returns `null` 时返回 `undefined`
 */
export function nullableToUndefined<T>(value: T | null): T | undefined {
  return value ?? undefined
}

/** 排障凭据响应头名（大小写不敏感读取）。 */
export const TRACE_RESPONSE_HEADER = 'X-Trace-Id'

/** trace 只接受可见 ASCII 技术字符，避免把任意内容送上屏。 */
const TRACE_PATTERN = /^[A-Za-z0-9._-]{1,128}$/

/** 超时类 Axios 错误码。 */
const TIMEOUT_AXIOS_CODES: readonly string[] = ['ECONNABORTED', 'ETIMEDOUT']

/** 凭据表单端点：这些端点上的 401 属于「校验失败」，不得当作会话过期。 */
const CREDENTIAL_ENDPOINT_PATTERNS: readonly RegExp[] = [
  /\/auth\/login(?:\?|$)/i,
  /\/auth\/session-login(?:\?|$)/i,
  /\/auth\/register(?:\?|$)/i,
  /\/auth\/social-login(?:\?|$)/i,
  /\/srm\/portal\/auth\/login(?:\?|$)/i,
]

/** 校验消息中的字段名：仅接受 ASCII 标识符，中文原文一律不参与展示与判定。 */
const FIELD_NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,63}$/

/** 词条与行为策略表：新增语言包 key 只需改这张表。 */
interface DisplayPolicy {
  readonly i18nKey: string
  readonly fallbackKey: string
  readonly shouldLogout: boolean
  readonly preserveInput: boolean
}

/** 各语义分类的本地化 key 与交互策略。 */
const POLICIES: Record<ErrorKind, DisplayPolicy> = {
  credentials: {
    i18nKey: 'request.credentialsInvalid',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
  sessionExpired: {
    i18nKey: 'request.loginExpiredMessage',
    fallbackKey: 'request.loginExpiredMessage',
    shouldLogout: true,
    preserveInput: false,
  },
  /**
   * 401 但发出请求时根本没有会话（匿名直访问受保护端点）：
   * 同样需要回到登录页，但语义上是「请先登录」，不应谎称「登录已过期」。
   */
  unauthenticated: {
    i18nKey: 'request.loginRequiredMessage',
    fallbackKey: 'request.loginExpiredMessage',
    shouldLogout: true,
    preserveInput: false,
  },
  permissionDenied: {
    i18nKey: 'request.forbiddenMessage',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
  validation: {
    i18nKey: 'request.badRequest',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
  validationFields: {
    i18nKey: 'request.fieldsInvalid',
    fallbackKey: 'request.badRequest',
    shouldLogout: false,
    preserveInput: true,
  },
  notFound: {
    i18nKey: 'request.notFound',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
  conflict: {
    i18nKey: 'request.conflictRefresh',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
  resourceGone: {
    i18nKey: 'request.resourceGone',
    fallbackKey: 'request.conflictRefresh',
    shouldLogout: false,
    preserveInput: true,
  },
  accountLocked: {
    i18nKey: 'request.accountLocked',
    fallbackKey: 'request.credentialsInvalid',
    shouldLogout: false,
    preserveInput: true,
  },
  rateLimited: {
    i18nKey: 'request.rateLimited',
    fallbackKey: 'request.unavailableMessage',
    shouldLogout: false,
    preserveInput: true,
  },
  serverError: {
    i18nKey: 'request.serverError',
    fallbackKey: 'request.unknownError',
    shouldLogout: false,
    preserveInput: true,
  },
  dependencyUnavailable: {
    i18nKey: 'request.unavailableMessage',
    fallbackKey: 'request.unavailableMessage',
    shouldLogout: false,
    preserveInput: true,
  },
  badGateway: {
    i18nKey: 'request.badGateway',
    fallbackKey: 'request.unavailableMessage',
    shouldLogout: false,
    preserveInput: true,
  },
  network: {
    i18nKey: 'request.networkError',
    fallbackKey: 'request.networkError',
    shouldLogout: false,
    preserveInput: true,
  },
  timeout: {
    i18nKey: 'request.timeout',
    fallbackKey: 'request.networkError',
    shouldLogout: false,
    preserveInput: true,
  },
  unknown: {
    i18nKey: 'request.requestFailed',
    fallbackKey: 'request.requestFailed',
    shouldLogout: false,
    preserveInput: true,
  },
}

/**
 * 判断给定请求 URL 是否为凭据表单端点。
 *
 * @param url Axios 请求配置中的 URL
 * @returns 属于凭据端点返回 `true`
 */
export function isCredentialEndpointUrl(url: unknown): boolean {
  if (typeof url !== 'string' || url.length === 0) return false
  return CREDENTIAL_ENDPOINT_PATTERNS.some(pattern => pattern.test(url))
}

/**
 * 判断请求头里是否已带 Authorization（即发出请求时是否已有会话）。
 *
 * @param headers Axios 请求头（`AxiosHeaders`、`Headers` 或普通对象）
 * @returns 携带 Authorization 头返回 `true`
 */
export function hasAuthorizationHeader(headers: unknown): boolean {
  return readHeader(headers, 'authorization') !== null
}

/** 大小写不敏感地读取头值，兼容 AxiosHeaders / Headers / 普通对象。 */
function readHeader(headers: unknown, name: string): string | null {
  if (!headers || typeof headers !== 'object') return null
  const lowerName = name.toLowerCase()

  const holder = headers as { get?: (key: string) => unknown }
  if (typeof holder.get === 'function') {
    const direct = normalizeHeader(holder.get(name))
    if (direct !== null) return direct
    const lower = normalizeHeader(holder.get(lowerName))
    if (lower !== null) return lower
  }

  const record = headers as Record<string, unknown>
  for (const key of Object.keys(record)) {
    if (key.toLowerCase() !== lowerName) continue
    const value = normalizeHeader(record[key])
    if (value !== null) return value
  }
  return null
}

/** 把头值收敛为字符串；数组取首个标量项；其余形态视为无效。 */
function normalizeHeader(value: unknown): string | null {
  if (typeof value === 'string') return value
  if (typeof value === 'number') return String(value)
  if (Array.isArray(value)) {
    const first = value.find(item => typeof item === 'string' || typeof item === 'number')
    return first === undefined ? null : String(first)
  }
  return null
}

/**
 * 从响应头读取 `X-Trace-Id`，作为用户可见排障凭据（`docs/api-contract.md:353-356`）。
 *
 * @param headers 响应头对象
 * @returns 合法 trace 或 `null`；非法内容一律丢弃，不进 UI
 */
export function readTraceId(headers: unknown): string | null {
  const raw = readHeader(headers, TRACE_RESPONSE_HEADER)
  if (raw === null) return null
  const trimmed = raw.trim()
  return TRACE_PATTERN.test(trimmed) ? trimmed : null
}

/**
 * 从响应体读取 `R.code`。非 `R` 形态（Spring Security 默认体、SCG 默认页、HTML）返回 `null`。
 *
 * @param data 响应体
 * @returns 业务码或 `null`
 */
export function readBusinessCode(data: unknown): number | null {
  if (!data || typeof data !== 'object') return null
  const code = (data as { code?: unknown }).code
  if (typeof code === 'number' && Number.isFinite(code)) return code
  if (typeof code === 'string' && /^\d+$/.test(code.trim())) return Number(code.trim())
  return null
}

/**
 * 读取响应体原始 `message`，**仅供受控诊断与 ASCII 字段名提取**，永不作为展示文本。
 *
 * @param data 响应体
 * @returns 后端原文或 `null`
 */
export function readServerMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null
  const message = (data as { message?: unknown }).message
  return typeof message === 'string' ? message : null
}

/**
 * 从校验类 `message` 中抽取 ASCII 字段名（如 `username`、`dictType`）。
 *
 * 这里不做任何中文字面量匹配：只保留纯 ASCII 标识符片段，
 * 中文 defaultMessage 及任何后端自然语言内容一律被丢弃。
 *
 * @param serverMessage 后端原始 message
 * @returns 去重后的字段名列表；无可信字段名时返回空数组
 */
export function extractFieldNames(serverMessage: string | null): string[] {
  if (serverMessage === null) return []
  const names: string[] = []
  for (const segment of serverMessage.split(';')) {
    const head = segment.split(':')[0]
    if (head === undefined) continue
    const candidate = head.trim()
    if (FIELD_NAME_PATTERN.test(candidate) && !names.includes(candidate)) {
      names.push(candidate)
    }
  }
  return names
}

/**
 * 无响应（断网、超时、DNS/连接失败）时的分类：只看 Axios `error.code`。
 *
 * @param axiosErrorCode Axios 传输层错误码
 * @returns 语义分类
 */
function classifyTransport(axiosErrorCode: string | null): ErrorKind {
  if (axiosErrorCode !== null && TIMEOUT_AXIOS_CODES.includes(axiosErrorCode.toUpperCase())) {
    return 'timeout'
  }
  return 'network'
}

/**
 * 码值 → 语义分类表（`401` 需结合请求上下文，不在此表内）。
 *
 * 表里没有的码一律走通用安全文案，绝不猜语义。
 */
const CODE_KINDS: Partial<Record<number, ErrorKind>> = {
  400: 'validation',
  403: 'permissionDenied',
  404: 'notFound',
  409: 'conflict',
  410: 'resourceGone',
  423: 'accountLocked',
  429: 'rateLimited',
  500: 'serverError',
  502: 'badGateway',
  503: 'dependencyUnavailable',
}

/**
 * 码值 → 语义分类。401 交由请求上下文判定，绝不参考任何文案。
 *
 * @param code 生效码（优先传输层 401/403，其次 `R.code`，最后 HTTP 状态）
 * @param context 请求上下文
 * @returns 语义分类
 */
function classifyCode(code: number, context: ErrorRequestContext): ErrorKind {
  if (code === 401) {
    // 登录表单自身的 401 是「凭据校验失败」：保留会话与输入，不弹「登录已过期」。
    if (context.isCredentialEndpoint) return 'credentials'
    // 其余 401 按是否已携带会话区分「过期」与「未登录」。
    return context.hasActiveSession ? 'sessionExpired' : 'unauthenticated'
  }
  const mapped = CODE_KINDS[code]
  if (mapped !== undefined) return mapped
  return code >= 500 ? 'serverError' : 'unknown'
}

/** 按语义分类组装最终描述符。 */
function buildDisplay(kind: ErrorKind, signal: ErrorSignal, params: Record<string, string | number>): ErrorDisplay {
  const policy = POLICIES[kind]
  return {
    kind,
    i18nKey: policy.i18nKey,
    fallbackKey: policy.fallbackKey,
    params,
    traceId: signal.traceId,
    httpStatus: signal.httpStatus,
    businessCode: signal.businessCode,
    hasBody: signal.hasBody,
    shouldLogout: policy.shouldLogout,
    preserveInput: policy.preserveInput,
  }
}

/**
 * 把错误信号归一化为 `{ i18nKey, params, traceId }` 描述符（纯函数）。
 *
 * 传输层 401/403 优先于 body 业务码：网关与安全过滤器可能在无 `R` 体时直接改写状态。
 *
 * @param signal 错误信号
 * @returns 展示描述符
 */
export function classifyError(signal: ErrorSignal): ErrorDisplay {
  const context: ErrorRequestContext = signal.context ?? {
    isCredentialEndpoint: false,
    hasActiveSession: true,
  }
  const params: Record<string, string | number> = {}
  if (signal.traceId !== null) params.traceId = signal.traceId

  // 1) 无响应：仅按 Axios 传输层错误码分类，技术串不上屏。
  if (signal.httpStatus === null) {
    return buildDisplay(classifyTransport(signal.axiosErrorCode), signal, params)
  }

  const transport = signal.httpStatus === 401 || signal.httpStatus === 403
    ? signal.httpStatus
    : null
  const effectiveCode = transport ?? signal.businessCode ?? signal.httpStatus
  let kind = classifyCode(effectiveCode, context)

  // 2) 400：有可信 ASCII 字段名时给出字段级可修复提示，否则给通用参数文案。
  if (kind === 'validation') {
    const fields = (signal.fieldNames ?? []).filter(name => FIELD_NAME_PATTERN.test(name))
    if (fields.length > 0) {
      params.fields = fields.join(', ')
      kind = 'validationFields'
    }
  }

  return buildDisplay(kind, signal, params)
}
