import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { isAbsolute, relative, resolve, sep } from 'node:path'

const pngSignature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])

/** 仅纯命令行操作允许终端证据；业务页面仍须真实截图。 */
export function supportsTerminalEvidence(module, flow) {
  return (module === 'scaffold-development' && ['create-service', 'crud-generate', 'preset-select', 'lite-mode'].includes(flow))
    || (module === 'operations' && ['compose-start', 'health-check', 'trace'].includes(flow))
}

/** 校验真实 PNG 的签名、块边界和结束块，拒绝改后缀的文本与截断文件。 */
export function pngErrors(bytes) {
  if (bytes.length < 33 || !bytes.subarray(0, 8).equals(pngSignature)) return ['PNG 签名无效']
  let cursor = 8
  let header = false
  let data = false
  while (cursor + 12 <= bytes.length) {
    const length = bytes.readUInt32BE(cursor)
    const type = bytes.toString('ascii', cursor + 4, cursor + 8)
    const end = cursor + length + 12
    if (end > bytes.length) return ['PNG 数据块被截断']
    if (!header) {
      if (type !== 'IHDR' || length !== 13 || !bytes.readUInt32BE(cursor + 8) || !bytes.readUInt32BE(cursor + 12)) {
        return ['PNG IHDR 无效']
      }
      header = true
    }
    if (type === 'IDAT') data = true
    if (type === 'IEND') return length === 0 && data && end === bytes.length ? [] : ['PNG 结束块无效']
    cursor = end
  }
  return ['PNG 缺少完整 IEND']
}

/** 证据只能引用交付根目录内的普通相对路径。 */
function artifactPath(root, path) {
  if (typeof path !== 'string' || !path || isAbsolute(path) || /^[A-Za-z]:|\\/.test(path)) return null
  const full = resolve(root, path)
  const child = relative(resolve(root), full)
  if (!child || child === '..' || child.startsWith(`..${sep}`)) return null
  return full
}

/** CLI 证据使用可审阅原始文本和内容摘要，不用网页伪装终端截图。 */
export function terminalErrors(root, item) {
  const errors = []
  for (const field of ['id', 'module', 'flow', 'step', 'artifact', 'sha256', 'command', 'source_commit', 'source_sha256', 'captured_at', 'test']) {
    if (typeof item[field] !== 'string' || !item[field].trim()) errors.push(`终端证据缺少 ${field}`)
  }
  if (item.kind !== 'terminal' || item.locale !== 'shared') errors.push('终端证据必须声明 kind=terminal、locale=shared')
  if (!supportsTerminalEvidence(item.module, item.flow)) errors.push('此业务步骤不允许用终端证据替代截图')
  if (item.runtime !== 'local Docker') errors.push('终端证据必须来自 local Docker')
  if (item.exit_code !== 0) errors.push('终端证据退出码必须为 0')
  if (!/^[a-f0-9]{40}$/.test(item.source_commit ?? '')) errors.push('终端证据 source_commit 无效')
  for (const field of ['sha256', 'source_sha256']) {
    if (!/^[a-f0-9]{64}$/.test(item[field] ?? '')) errors.push(`终端证据 ${field} 无效`)
  }
  if (!Number.isFinite(Date.parse(item.captured_at))) errors.push('终端证据采集时间无效')
  if (!item.tool?.name || !item.tool?.version || !/^sha256:[a-f0-9]{64}$/.test(item.tool?.image_id ?? '')) errors.push('终端证据缺少工具版本或 Docker 镜像身份')
  if (item.review?.status !== 'passed' || !item.review?.reviewer) errors.push('终端文本尚未完成审阅')
  const test = artifactPath(root, item.test)
  if (!test || !existsSync(test)) errors.push('终端证据测试入口不存在或越界')
  const path = artifactPath(root, item.artifact)
  if (!path || !existsSync(path)) return [...errors, '终端证据文件不存在或越界']
  const bytes = readFileSync(path)
  if (!bytes.length || bytes.includes(0)) errors.push('终端证据必须为非空 UTF-8 文本')
  let content
  try { content = new TextDecoder('utf-8', { fatal: true }).decode(bytes) } catch { errors.push('终端证据不是有效 UTF-8 文本') }
  if (createHash('sha256').update(bytes).digest('hex') !== item.sha256) errors.push('终端证据 SHA-256 不匹配')
  if (!Array.isArray(item.assertions) || !item.assertions.length || item.assertions.some((assertion) =>
    typeof assertion.contains !== 'string' || !assertion.contains || !content?.includes(assertion.contains))) {
    errors.push('终端证据缺少可核对的成功断言或断言不匹配')
  }
  return errors
}
