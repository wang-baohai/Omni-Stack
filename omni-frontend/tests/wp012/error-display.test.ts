/**
 * @module error-display.test
 * @description `error-display.ts` 归一化纯函数的定向测试（node:test，不依赖浏览器与 i18n 实例）。
 *
 * 特意置于 omni-frontend/tests/ 下（不在 src/），以便：
 * - 不被 UI 硬编码扫描（ui-i18n-audit 只扫 src/**）计入中文断言字面量；
 * - 不被 vue-tsc 构建强制纳入浏览器类型图（使用 node:test/node:assert）。
 *
 * 覆盖 error-contract-matrix.json 的 EC-* 分支判定：
 * 401 会话过期 vs 401 登录凭据失败、403 保持会话、400 字段级、404/409/410/423/429、
 * 500/502/503、网络与超时、非 R 形态响应体，以及「后端中文原文不参与判定且不上屏」。
 *
 * 运行方式：`node --experimental-strip-types --test tests/wp012/error-display.test.ts`（cwd=omni-frontend）。
 */
import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  classifyError,
  extractFieldNames,
  isCredentialEndpointUrl,
  nullableToUndefined,
  readBusinessCode,
  readTraceId,
} from '../../src/utils/error-display.ts'
import type { ErrorSignal } from '../../src/utils/error-display.ts'

/** 中文字面量检测：用于断言归一化结果里绝不出现后端自然语言文案。 */
const HAN_PATTERN = /[\u4E00-\u9FFF]/

/** 判断字符串是否含中日韩汉字。 */
function hasHan(value: string): boolean {
  return HAN_PATTERN.test(value)
}

/** 构造带默认值的错误信号。 */
function signal(overrides: Partial<ErrorSignal>): ErrorSignal {
  return {
    httpStatus: null,
    businessCode: null,
    axiosErrorCode: null,
    hasBody: false,
    traceId: null,
    ...overrides,
  }
}

test('EC-401-HTTP-EXPIRED：已有会话的非凭据端点 401 判定为会话过期并允许登出', () => {
  const display = classifyError(signal({
    httpStatus: 401,
    businessCode: 401,
    hasBody: true,
    traceId: '0af7651916353c001cf0b7d1924aa211',
    context: { isCredentialEndpoint: false, hasActiveSession: true },
  }))

  assert.equal(display.kind, 'sessionExpired')
  assert.equal(display.i18nKey, 'request.loginExpiredMessage')
  assert.equal(display.shouldLogout, true)
  assert.equal(display.traceId, '0af7651916353c001cf0b7d1924aa211')
})

test('EC-401-BIZ-LOGIN-FAIL：登录端点 HTTP200+code401 判定为凭据失败，不登出且保留输入', () => {
  const display = classifyError(signal({
    httpStatus: 200,
    businessCode: 401,
    hasBody: true,
    context: { isCredentialEndpoint: isCredentialEndpointUrl('/auth/login'), hasActiveSession: false },
  }))

  assert.equal(display.kind, 'credentials')
  assert.equal(display.i18nKey, 'request.credentialsInvalid')
  assert.equal(display.shouldLogout, false)
  assert.equal(display.preserveInput, true)
})

test('401 判定不得依赖后端中文文案：同一响应体换语言/换文案结果不变', () => {
  const base = {
    httpStatus: 200,
    businessCode: 401,
    hasBody: true,
    context: { isCredentialEndpoint: true, hasActiveSession: false },
  } satisfies Partial<ErrorSignal>
  const first = classifyError(signal(base))
  const second = classifyError(signal({ ...base, fieldNames: ['password'] }))

  assert.deepEqual(first, second)
  assert.ok(!hasHan(JSON.stringify(first)))
})

test('EC-403：保持会话、不登出，使用本地化拒绝文案', () => {
  const display = classifyError(signal({
    httpStatus: 403,
    businessCode: 403,
    hasBody: true,
    traceId: 'trace-403',
  }))

  assert.equal(display.kind, 'permissionDenied')
  assert.equal(display.i18nKey, 'request.forbiddenMessage')
  assert.equal(display.fallbackKey, 'request.requestFailed')
  assert.equal(display.shouldLogout, false)
  assert.equal(display.preserveInput, true)
})

test('EC-403 非 R 形态响应体（Spring Security 默认体）仍按 HTTP 状态分类', () => {
  const display = classifyError(signal({
    httpStatus: 403,
    businessCode: readBusinessCode('<html>Whitelabel Error Page</html>'),
    hasBody: true,
  }))

  assert.equal(display.businessCode, null)
  assert.equal(display.kind, 'permissionDenied')
  assert.equal(display.shouldLogout, false)
})

test('EC-400-VALIDATION：有可信 ASCII 字段名时输出字段级提示', () => {
  const fields = extractFieldNames('dictType: 字典类型不能为空; remark: 备注不能为空')
  const display = classifyError(signal({
    httpStatus: 400,
    businessCode: 400,
    hasBody: true,
    fieldNames: fields,
  }))

  assert.deepEqual(fields, ['dictType', 'remark'])
  assert.equal(display.kind, 'validationFields')
  assert.equal(display.i18nKey, 'request.fieldsInvalid')
  assert.equal(display.params.fields, 'dictType, remark')
  assert.equal(display.preserveInput, true)
})

test('EC-400：AuthExceptionHandler 空串缺陷下退化为通用参数文案', () => {
  const display = classifyError(signal({
    httpStatus: 400,
    businessCode: 400,
    hasBody: true,
    fieldNames: extractFieldNames(''),
  }))

  assert.deepEqual(extractFieldNames(''), [])
  assert.equal(display.kind, 'validation')
  assert.equal(display.i18nKey, 'request.badRequest')
})

test('中文消息不参与字段提取，任何汉字都不进 params', () => {
  const fields = extractFieldNames('密码错误，已连续失败 3 次')

  assert.deepEqual(fields, [])
  assert.ok(!hasHan(fields.join(',')))
})

test('EC-404：业务 200+code404 与 HTTP 404 均走本地化缺失文案', () => {
  const biz = classifyError(signal({ httpStatus: 200, businessCode: 404, hasBody: true }))
  const http = classifyError(signal({ httpStatus: 404, businessCode: null, hasBody: false }))

  assert.equal(biz.kind, 'notFound')
  assert.equal(biz.i18nKey, 'request.notFound')
  assert.equal(http.kind, 'notFound')
  assert.equal(http.shouldLogout, false)
})

test('EC-409-CONFLICT：给出刷新重试语义且保留输入', () => {
  const display = classifyError(signal({ httpStatus: 200, businessCode: 409, hasBody: true }))

  assert.equal(display.i18nKey, 'request.conflictRefresh')
  assert.equal(display.preserveInput, true)
  assert.equal(display.shouldLogout, false)
})

test('EC-410-423-429-502：不被当作通用请求失败，各分支独立本地化 key', () => {
  const gone = classifyError(signal({ httpStatus: 200, businessCode: 410, hasBody: true }))
  const locked = classifyError(signal({ httpStatus: 200, businessCode: 423, hasBody: true }))
  const limited = classifyError(signal({ httpStatus: 200, businessCode: 429, hasBody: true }))
  const gateway = classifyError(signal({ httpStatus: 200, businessCode: 502, hasBody: true }))

  assert.equal(gone.i18nKey, 'request.resourceGone')
  assert.equal(locked.i18nKey, 'request.accountLocked')
  assert.equal(limited.i18nKey, 'request.rateLimited')
  assert.equal(gateway.i18nKey, 'request.badGateway')
  for (const item of [gone, locked, limited, gateway]) {
    assert.notEqual(item.i18nKey, 'request.requestFailed')
    assert.ok(!hasHan(item.i18nKey))
  }
})

test('EC-500-GENERIC：HTTP500 与 HTTP200+code500 都归为服务内部错误且不登出', () => {
  const http = classifyError(signal({ httpStatus: 500, businessCode: 500, hasBody: true }))
  const biz = classifyError(signal({ httpStatus: 200, businessCode: 500, hasBody: true }))

  assert.equal(http.kind, 'serverError')
  assert.equal(biz.kind, 'serverError')
  assert.equal(biz.i18nKey, 'request.serverError')
  assert.equal(biz.fallbackKey, 'request.unknownError')
  assert.equal(biz.shouldLogout, false)
})

test('EC-503：网关 fail-closed 与依赖不可用都走「服务暂不可用/稍后重试」', () => {
  const gateway = classifyError(signal({ httpStatus: 503, businessCode: null, hasBody: false }))
  const dependency = classifyError(signal({ httpStatus: 200, businessCode: 503, hasBody: true }))

  assert.equal(gateway.i18nKey, 'request.unavailableMessage')
  assert.equal(dependency.i18nKey, 'request.unavailableMessage')
  assert.equal(dependency.preserveInput, true)
  assert.equal(dependency.shouldLogout, false)
})

test('EC-NETWORK-TIMEOUT：无响应时仅按 Axios error.code 分类', () => {
  const timeout = classifyError(signal({ axiosErrorCode: 'ECONNABORTED' }))
  const network = classifyError(signal({ axiosErrorCode: 'ERR_NETWORK' }))
  const unknownTransport = classifyError(signal({ axiosErrorCode: 'ERR_BAD_RESPONSE' }))

  assert.equal(timeout.kind, 'timeout')
  assert.equal(timeout.i18nKey, 'request.timeout')
  assert.equal(timeout.fallbackKey, 'request.networkError')
  assert.equal(network.i18nKey, 'request.networkError')
  assert.equal(unknownTransport.i18nKey, 'request.networkError')
  for (const item of [timeout, network, unknownTransport]) {
    assert.equal(item.httpStatus, null)
    assert.equal(item.shouldLogout, false)
  }
})

test('EC-HTTP200-BIZ-ERROR：未知业务码落通用安全文案，不回落到后端原文', () => {
  const display = classifyError(signal({
    httpStatus: 200,
    businessCode: 418,
    hasBody: true,
    fieldNames: extractFieldNames('服务器内部错误：请联系管理员处理'),
  }))

  assert.equal(display.i18nKey, 'request.requestFailed')
  assert.equal(display.businessCode, 418)
  assert.ok(!hasHan(JSON.stringify(display)))
})

test('X-Trace-Id 读取大小写不敏感，且非法内容被丢弃', () => {
  assert.equal(readTraceId({ 'x-trace-id': 'abc123' }), 'abc123')
  assert.equal(readTraceId({ 'X-Trace-Id': 'ABC-123_z.9' }), 'ABC-123_z.9')
  assert.equal(readTraceId({ 'X-TRACE-ID': ' spaced ' }), 'spaced')
  assert.equal(readTraceId({ authorization: 'Bearer x' }), null)
  assert.equal(readTraceId({ 'X-Trace-Id': '中文 排障' }), null)
  assert.equal(readTraceId({ 'X-Trace-Id': ['first', 'second'] }), 'first')
  assert.equal(readTraceId({ get: (key: string) => (key.toLowerCase() === 'x-trace-id' ? 'via-get' : undefined) }), 'via-get')
})

test('传输层 401/403 优先于 body 业务码，避免网关状态被 body 覆盖', () => {
  const display = classifyError(signal({
    httpStatus: 403,
    businessCode: 500,
    hasBody: true,
    context: { isCredentialEndpoint: false, hasActiveSession: true },
  }))

  assert.equal(display.kind, 'permissionDenied')
  assert.equal(display.businessCode, 500)
})

test('凭据端点判定覆盖登录/会话登录/注册，且不误伤 login-record 等管理端点', () => {
  assert.equal(isCredentialEndpointUrl('/auth/login'), true)
  assert.equal(isCredentialEndpointUrl('/auth/login?x=1'), true)
  assert.equal(isCredentialEndpointUrl('/auth/session-login'), true)
  assert.equal(isCredentialEndpointUrl('/auth/register'), true)
  assert.equal(isCredentialEndpointUrl('/auth/login-record/list'), false)
  assert.equal(isCredentialEndpointUrl('/auth/me'), false)
  assert.equal(isCredentialEndpointUrl(undefined), false)
})

test('401 三分支：凭据失败不登出，有会话过期与无会话未登录均回跳登录', () => {
  const credentials = classifyError(signal({
    httpStatus: 200,
    businessCode: 401,
    hasBody: true,
    context: { isCredentialEndpoint: true, hasActiveSession: false },
  }))
  const expired = classifyError(signal({
    httpStatus: 401,
    businessCode: 401,
    hasBody: true,
    context: { isCredentialEndpoint: false, hasActiveSession: true },
  }))
  const anonymous = classifyError(signal({
    httpStatus: 401,
    businessCode: null,
    hasBody: false,
    context: { isCredentialEndpoint: false, hasActiveSession: false },
  }))

  assert.equal(credentials.kind, 'credentials')
  assert.equal(credentials.shouldLogout, false)
  assert.equal(credentials.preserveInput, true)

  assert.equal(expired.kind, 'sessionExpired')
  assert.equal(expired.i18nKey, 'request.loginExpiredMessage')
  assert.equal(expired.shouldLogout, true)

  assert.equal(anonymous.kind, 'unauthenticated')
  assert.equal(anonymous.i18nKey, 'request.loginRequiredMessage')
  assert.equal(anonymous.fallbackKey, 'request.loginExpiredMessage')
  assert.equal(anonymous.shouldLogout, true)
})

/**
 * 向后兼容回归：既有调用方（views/system/config/index.vue）用
 * `businessCode === undefined && httpStatus === undefined && !response` 识别网络错误，
 * 挂在错误对象上的遗留字段必须在无响应时收敛为 undefined，否则该分支会退化成通用服务错误。
 */
test('遗留字段兼容：无响应时暴露 undefined，有响应时暴露具体码值', () => {
  const transport = classifyError(signal({ axiosErrorCode: 'ERR_NETWORK' }))

  assert.equal(nullableToUndefined(transport.httpStatus), undefined)
  assert.equal(nullableToUndefined(transport.businessCode), undefined)

  const forbidden = classifyError(signal({ httpStatus: 403, businessCode: 403, hasBody: true }))

  assert.equal(nullableToUndefined(forbidden.httpStatus), 403)
  assert.equal(nullableToUndefined(forbidden.businessCode), 403)

  const conflict = classifyError(signal({ httpStatus: 200, businessCode: 409, hasBody: true }))

  assert.equal(nullableToUndefined(conflict.businessCode), 409)
  assert.equal(nullableToUndefined(null), undefined)
  assert.equal(nullableToUndefined(0), 0)
})
