#!/usr/bin/env node
/**
 * secret-scan.mjs — 敏感信息模式扫描。
 *
 * 支持三种模式：
 *   --staged        扫描 git 暂存区文本差异（默认）
 *   --file=<path>   扫描指定文件
 *   --archive=<path> 扫描 ZIP/HAR 归档内的文本内容
 *
 * 安全约束：
 * - 只输出「模式名 + 文件」结论，绝不回显匹配到的敏感内容本身。
 * - 退出码 0 = CLEAN，1 = HITS，2 = ERROR（含解码不确定/归档不可读，fail-closed）。
 *
 * 编码策略（WP-015 FR02 / RF07）：
 * - 支持明确可解码的 UTF-8、UTF-16LE、UTF-16BE（带 BOM 优先；无 BOM 走 NUL 分布启发）。
 * - 解码不确定、BOM 与载荷长度矛盾、或解码后仍含不合理 NUL/替换符文本 ⇒ ERROR，
 *   不允许默默按 UTF-8 得到虚假 CLEAN。
 * - 无 BOM 且非法 UTF-8 字节序列（典型：含二进制块的 mysqldump）：降级为 lossy UTF-8 并
 *   **继续扫描**，同时输出 DECODE_WARNING + DECODED_AS_UTF8_LOSSY_FALLBACK 显式披露；
 *   该降级保留既有 UTF8 文件的 HITS 行为，不把真实命中退化为无信息结果。
 * - --file 与 ZIP/HAR 抽取到的文本共用同一解码与 ERROR 处理；不可读归档保持 fail-closed。
 */
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync, statSync, readdirSync, mkdtempSync, rmSync } from 'node:fs'
import { basename, extname, resolve } from 'node:path'

const systemTempRoot = process.env.TEMP || process.env.TMP || undefined

const patterns = [
  { name: 'JWT', re: /eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}/g },
  { name: 'BEARER_REAL_TOKEN', re: /Bearer\s+(?!<|\$\{|\*{3}|`)[A-Za-z0-9._~-]{24,}/gi },
  { name: 'PRIVATE_KEY', re: /-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/g },
  { name: 'CRED_IN_URL', re: /(?:mysql|postgres|redis|amqp|https?):\/\/[^:/\s]+:[^@/\s]{6,}@/gi },
  { name: 'SECRET_ASSIGNMENT', re: /(?:password|passwd|secret|api[_-]?key|access[_-]?key)\s*[:=]\s*['"]?[A-Za-z0-9/+_.-]{16,}/gi },
  { name: 'AWS_STYLE_KEY', re: /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/g },
  { name: 'GH_TOKEN', re: /\bgh[pousr]_[A-Za-z0-9]{20,}\b/g },
]

const findings = []
let scanErrors = 0
/** 非致命解码降级计数（仍完成扫描，但必须显式披露，不得静默）。 */
let decodeWarnings = 0
/** 已解码文本条目计数与编码分布（用于证明真实扫描范围，不是仅更新报告数字）。 */
const decodedCounters = { total: 0, byEncoding: {} }

/** 解码策略错误：不可确定编码时必须走 ERROR 分支，不得降级为 CLEAN。 */
class DecodeError extends Error {}

const NUL_PROBE_LIMIT = 65536
/** 解码结果中 U+FFFD / 控制符占比超过该阈值即认为解码不成立。 */
const MAX_BAD_CHAR_RATIO = 0.02

/** 统计前 limit 字节中 NUL 的位置奇偶分布。 */
function nulProfile(buf) {
  const end = Math.min(buf.length, NUL_PROBE_LIMIT)
  let nul = 0, odd = 0, even = 0
  for (let i = 0; i < end; i++) {
    if (buf[i] === 0x00) { nul++; if (i % 2 === 1) odd++; else even++ }
  }
  return { nul, odd, even, ratio: end === 0 ? 0 : nul / end }
}

/** 校验解码后的文本本身合理：不允许残留高比例 NUL/替换符。 */
function assertDecodedSane(text, encoding) {
  if (text.length === 0) return
  let bad = 0
  const probeLen = Math.min(text.length, 200000)
  for (let i = 0; i < probeLen; i++) {
    const code = text.charCodeAt(i)
    if (code === 0xfffd || code === 0x00) bad++
  }
  const ratio = bad / probeLen
  if (ratio > MAX_BAD_CHAR_RATIO) {
    throw new DecodeError(`decoded_as=${encoding} 仍含 ${Math.round(ratio * 10000) / 100}% 的 NUL/替换符，编码判定不成立`)
  }
}

/** 严格 UTF-8 解码；非法序列 ⇒ DecodeError（不 lossy 通过）。 */
function decodeUtf8Strict(buf, label) {
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(buf)
  } catch (err) {
    throw new DecodeError(`${label}: 不是合法 UTF-8（存在非法字节序列），且无法判定为受支持的 UTF-16`)
  }
}

function swapPairs(buf) {
  const out = Buffer.alloc(buf.length)
  for (let i = 0; i + 1 < buf.length; i += 2) { out[i] = buf[i + 1]; out[i + 1] = buf[i] }
  return out
}

/**
 * 统一解码入口：返回 { text, encoding }；任何不确定情况抛 DecodeError。
 * encoding ∈ UTF8_BOM | UTF8 | UTF16LE_BOM | UTF16BE_BOM | UTF16LE | UTF16BE
 */
function decodeBuffer(buf, label) {
  if (buf.length === 0) return { text: '', encoding: 'EMPTY' }

  if (buf.length >= 3 && buf[0] === 0xef && buf[1] === 0xbb && buf[2] === 0xbf) {
    const text = decodeUtf8Strict(buf.subarray(3), label)
    assertDecodedSane(text, 'UTF8_BOM')
    return { text, encoding: 'UTF8_BOM' }
  }
  if (buf.length >= 2 && buf[0] === 0xff && buf[1] === 0xfe) {
    const body = buf.subarray(2)
    if (body.length % 2 !== 0) throw new DecodeError(`${label}: UTF-16LE BOM 与奇数字节载荷矛盾，解码不可确定`)
    const text = body.toString('utf16le')
    assertDecodedSane(text, 'UTF16LE_BOM')
    return { text, encoding: 'UTF16LE_BOM' }
  }
  if (buf.length >= 2 && buf[0] === 0xfe && buf[1] === 0xff) {
    const body = buf.subarray(2)
    if (body.length % 2 !== 0) throw new DecodeError(`${label}: UTF-16BE BOM 与奇数字节载荷矛盾，解码不可确定`)
    const text = swapPairs(body).toString('utf16le')
    assertDecodedSane(text, 'UTF16BE_BOM')
    return { text, encoding: 'UTF16BE_BOM' }
  }

  const np = nulProfile(buf)
  if (np.ratio > 0.02) {
    // 无 BOM 的 UTF-16 猜测：NUL 集中在奇数位 ⇒ LE，集中在偶数位 ⇒ BE。
    // 判定不置信或长度矛盾时一律 ERROR，不允许按 UTF-8 得到虚假 CLEAN。
    const confident = np.ratio > 0.15 && (np.odd >= np.even * 3 || np.even >= np.odd * 3)
    if (!confident) throw new DecodeError(`${label}: NUL 占比 ${Math.round(np.ratio * 100)}% 但 UTF-16 对齐不置信（odd=${np.odd}/even=${np.even}），编码不可确定`)
    if (buf.length % 2 !== 0) throw new DecodeError(`${label}: 无 BOM UTF-16 候选但字节长度为奇数，解码不可确定`)
    const text = np.odd >= np.even ? buf.toString('utf16le') : swapPairs(buf).toString('utf16le')
    const enc = np.odd >= np.even ? 'UTF16LE' : 'UTF16BE'
    assertDecodedSane(text, enc)
    return { text, encoding: enc }
  }

  // 无 BOM 且 NUL 密度低：走 UTF-8 入口（严格失败则显式降级，仍完成扫描）
  return decodeNoBom(buf, label)
}

/**
 * 无 BOM 文本的降级入口：严格 UTF-8 失败时降级 lossy UTF-8 并继续扫描，
 * 显式记 WARN（不静默），以保留既有 UTF8 命中能力（例如含二进制块的 dump）。
 */
function decodeNoBom(buf, label) {
  try {
    const text = decodeUtf8Strict(buf, label)
    assertDecodedSane(text, 'UTF8')
    return { text, encoding: 'UTF8' }
  } catch (strictErr) {
    const text = buf.toString('utf8')
    assertDecodedSane(text, 'UTF8_LOSSY_FALLBACK')
    decodeWarnings++
    console.error(`DECODE_WARNING file=${label} reason=${strictErr.message || strictErr} action=lossy_utf8_fallback_still_scanned`)
    return { text, encoding: 'UTF8_LOSSY_FALLBACK' }
  }
}

/** 扫描任意缓冲：共用解码与 ERROR 处理；不可解码即计入 scanErrors。 */
function scanBuffer(buf, fileLabel) {
  let decoded
  try {
    decoded = decodeBuffer(buf, fileLabel)
  } catch (err) {
    scanErrors++
    console.error(`DECODE_ERROR file=${fileLabel} reason=${err.message || err}`)
    return
  }
  decodedCounters.total++
  decodedCounters.byEncoding[decoded.encoding] = (decodedCounters.byEncoding[decoded.encoding] || 0) + 1
  scanText(decoded.text, fileLabel)
}

/** 扫描文本内容，只登记模式名与文件标签。 */
function scanText(text, fileLabel) {
  for (const line of text.split('\n')) {
    const trimmed = line.endsWith('\r') ? line.slice(0, -1) : line
    for (const pattern of patterns) {
      pattern.re.lastIndex = 0
      if (pattern.re.test(trimmed)) {
        findings.push({ pattern: pattern.name, file: fileLabel })
      }
    }
  }
}

/** 扫描 git 暂存区文本差异。 */
function scanStaged() {
  const diff = execFileSync('git', ['diff', '--cached', '--no-color', '--no-ext-diff'], {
    maxBuffer: 512 * 1024 * 1024,
  }).toString('utf8')

  const addedLines = diff.split('\n').filter((line) => line.startsWith('+') && !line.startsWith('+++'))
  let currentFile = '<unknown>'
  const stagedFiles = []
  const binarySkipped = []

  for (const line of diff.split('\n')) {
    if (line.startsWith('+++ b/')) {
      currentFile = line.slice(6)
      stagedFiles.push(currentFile)
      continue
    }
    if (line.startsWith('Binary files')) {
      // 诚实披露：git 判定为二进制的暂存项不会进入文本扫描，需由 --file 扫描覆盖
      binarySkipped.push(line)
      continue
    }
    if (!line.startsWith('+') || line.startsWith('+++')) continue
    const text = line.slice(1)
    for (const pattern of patterns) {
      pattern.re.lastIndex = 0
      if (pattern.re.test(text)) {
        findings.push({ pattern: pattern.name, file: currentFile })
      }
    }
  }

  decodedCounters.total += stagedFiles.length
  decodedCounters.byEncoding.GIT_DIFF_TEXT = stagedFiles.length
  console.log(`SCANNED_STAGED_TEXT_FILES=${stagedFiles.length}`)
  console.log(`SCANNED_ADDED_LINES=${addedLines.length}`)
  console.log(`STAGED_BINARY_SKIPPED=${binarySkipped.length}`)
  for (const b of binarySkipped) console.log(`  BINARY_NOT_TEXT_SCANNED ${b}`)
}

/**
 * 通用 ZIP 展开：优先 unzip；命令不存在（ENOENT）时回退 PowerShell Expand-Archive。
 * 损坏归档 / 展开失败 / 依赖均不可用均抛异常，由调用方决定非零退出。
 */
function expandZipAnywhere(zipPath) {
  const tmpDir = mkdtempSync(resolve(systemTempRoot ?? process.env.TEMP ?? '.', 'wp015-scan-'))
  try {
    try {
      execFileSync('unzip', ['-q', zipPath, '-d', tmpDir], {
        maxBuffer: 64 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'],
      })
    } catch (err) {
      if (!(err && err.code === 'ENOENT')) throw err
      // $ErrorActionPreference='Stop'：PowerShell cmdlet 非 terminating error 默认不影响进程退出码，
      // 必须显式停止否则损坏 ZIP 会被误报成功（实测 corrupt.zip 报错但 PS_EXIT=0）
      execFileSync('powershell', ['-NoProfile', '-Command',
        `$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath '${zipPath.replace(/'/g, "''")}' -DestinationPath '${tmpDir.replace(/'/g, "''")}' -Force`], {
        encoding: 'utf8', timeout: 60_000, stdio: ['pipe', 'pipe', 'pipe'],
      })
    }
    return tmpDir
  } catch (err) {
    try { rmSync(tmpDir, { recursive: true, force: true }) } catch { /* 临时目录清理尽力 */ }
    throw err
  }
}

/** 扫描指定文件（支持文本和 ZIP/HAR 归档）。 */
function scanFile(filePath) {
  const absPath = resolve(filePath)
  if (!existsSync(absPath)) {
    console.error(`文件不存在: ${filePath}`)
    process.exit(2)
  }
  const ext = extname(absPath).toLowerCase()
  const stat = statSync(absPath)
  console.log(`SCANNING_FILE=${filePath} (${stat.size} bytes)`)

  if (ext === '.har') {
    // HAR 是 JSON 格式：先走统一解码，格式解析失败属读取失败，必须非零退出不允许报 CLEAN
    let harText
    try {
      harText = decodeBuffer(readFileSync(absPath), filePath).text
    } catch (err) {
      console.error(`HAR 解码失败: ${filePath}: ${err.message || err}`)
      process.exit(2)
    }
    try {
      JSON.parse(harText)
    } catch (err) {
      console.error(`HAR 内容不是合法 JSON（可能损坏或被篡改）: ${filePath}: ${err.message || err}`)
      process.exit(2)
    }
    scanText(harText, filePath)
    decodedCounters.total++
    decodedCounters.byEncoding.HAR = (decodedCounters.byEncoding.HAR || 0) + 1
  } else if (ext === '.zip') {
    // ZIP 归档：统一展开到临时目录后扫描（文本条目 + 嵌套归档递归），
    // 展开依赖与归档损坏均导致非零退出，不允许静默报 CLEAN
    const textExts = new Set(['.txt', '.md', '.json', '.yaml', '.yml', '.xml', '.sql', '.dump', '.csv', '.tsv', '.log', '.js', '.ts', '.mjs', '.cjs', '.env', '.cfg', '.conf', '.properties', '.html', '.har'])
    const scanExtractedDir = (dir, prefix) => {
      let scanned = 0
      const walk = (d, p) => {
        for (const name of readdirSync(d)) {
          const full = resolve(d, name)
          if (statSync(full).isDirectory()) { walk(full, `${p}${name}/`); continue }
          if (extname(full).toLowerCase() === '.zip') {
            // D02 嵌套归档：递归展开继续扫描，防止负载藏于嵌套 ZIP 漏扫
            let innerTmp
            try {
              innerTmp = expandZipAnywhere(full)
              scanned += scanExtractedDir(innerTmp, `${p}${name}:`)
            } catch (err) {
              scanErrors++
              console.error(`嵌套 ZIP 解压失败: ${filePath}:${p}${name} (${err.message || err})`)
            } finally {
              if (innerTmp) { try { rmSync(innerTmp, { recursive: true, force: true }) } catch { /* 临时目录清理尽力 */ } }
            }
            continue
          }
          if (!textExts.has(extname(full).toLowerCase())) continue
          // 与 --file 普通文本共用解码与 ERROR 处理：UTF-16 条目不得被当成 UTF-8 CLEAN
          scanBuffer(readFileSync(full), `${filePath}:${p}${name}`)
          scanned++
        }
      }
      walk(dir, prefix)
      return scanned
    }
    let tmpRoot
    try {
      tmpRoot = expandZipAnywhere(absPath)
      const scanned = scanExtractedDir(tmpRoot, '')
      console.log(`ZIP_TEXT_ENTRIES=${scanned}`)
    } catch (err) {
      console.error(`无法读取 ZIP 归档: ${filePath}: ${err.message || err}`)
      if (tmpRoot) { try { rmSync(tmpRoot, { recursive: true, force: true }) } catch { /* 临时目录清理尽力 */ } }
      process.exit(2)
    } finally {
      if (tmpRoot) { try { rmSync(tmpRoot, { recursive: true, force: true }) } catch { /* 临时目录清理尽力 */ } }
    }
  } else {
    // 普通文件：统一走解码入口（UTF8 / UTF16LE / UTF16BE），不可确定则 ERROR
    scanBuffer(readFileSync(absPath), filePath)
  }
}

// 解析命令行参数
const args = process.argv.slice(2)
const mode = args.find((a) => a.startsWith('--'))?.split('=')[0] || '--staged'

if (mode === '--staged' || args.length === 0) {
  scanStaged()
} else if (mode === '--file') {
  const filePath = args.find((a) => a.startsWith('--file='))?.slice(7)
  if (!filePath) { console.error('用法: --file=<path>'); process.exit(2) }
  scanFile(filePath)
} else if (mode === '--archive') {
  const archivePath = args.find((a) => a.startsWith('--archive='))?.slice(10)
  if (!archivePath) { console.error('用法: --archive=<path>'); process.exit(2) }
  scanFile(archivePath)
} else {
  console.error(`未知模式: ${mode}`)
  console.error('用法: secret-scan.mjs [--staged | --file=<path> | --archive=<path>]')
  process.exit(2)
}

// 输出结果
console.log(`PATTERNS_APPLIED=${patterns.length}`)
console.log(`DECODED_ENTRIES=${decodedCounters.total}`)
console.log(`DECODE_WARNINGS=${decodeWarnings}`)
for (const [enc, n] of Object.entries(decodedCounters.byEncoding)) {
  console.log(`DECODED_AS_${enc}=${n}`)
}
if (scanErrors > 0) {
  console.log(`SCAN_ERRORS=${scanErrors}`)
  console.log('SECRET_SCAN_RESULT=ERROR')
  process.exit(2)
} else if (findings.length === 0) {
  console.log('SECRET_SCAN_RESULT=CLEAN')
  process.exit(0)
} else {
  console.log(`SECRET_SCAN_RESULT=HITS(${findings.length})`)
  for (const finding of findings) {
    console.log(`  HIT pattern=${finding.pattern} file=${finding.file}`)
  }
  process.exit(1)
}
