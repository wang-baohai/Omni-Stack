import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, extname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
const selectedScope = process.argv.find((argument) => argument.startsWith('--scope='))?.slice(8) || 'all'
const allowDraft = process.argv.includes('--allow-draft')
const failures = []

function absolute(relativePath) {
  return resolve(repositoryRoot, relativePath)
}

function read(relativePath) {
  return readFileSync(absolute(relativePath), 'utf8')
}

function fail(message) {
  failures.push(message)
}

function sha256(content) {
  return createHash('sha256').update(content).digest('hex')
}

function markdownSlug(title) {
  return title
    .trim()
    .toLowerCase()
    .replace(/[`*_~]/g, '')
    .replace(/[^\p{L}\p{N}\s-]/gu, '')
    .replace(/\s/g, '-')
}

function markdownAnchors(content) {
  const anchors = new Set()
  const counts = new Map()
  for (const line of content.split(/\r?\n/)) {
    const match = /^(#{1,6})\s+(.+?)\s*$/.exec(line)
    if (!match) continue
    const base = markdownSlug(match[2])
    const count = counts.get(base) || 0
    counts.set(base, count + 1)
    anchors.add(count === 0 ? base : `${base}-${count}`)
  }
  return anchors
}

function markdownFiles() {
  const manifest = YAML.parse(read('docs/docs-manifest.yaml'))
  const files = new Set(['README.md', 'README.en.md', 'README.jp.md', 'README.kr.md'])
  for (const document of manifest.documents) {
    files.add(document.source)
    for (const translation of Object.values(document.translations)) files.add(translation.path)
  }
  return [...files].filter((path) => existsSync(absolute(path)))
}

function checkLinks() {
  const linkPattern = /!?\[[^\]]*\]\(([^)]+)\)/g
  for (const relativePath of markdownFiles()) {
    const content = read(relativePath)
    let fenced = false
    for (const originalLine of content.split(/\r?\n/)) {
      if (/^\s*(```|~~~)/.test(originalLine)) {
        fenced = !fenced
        continue
      }
      if (fenced) continue
      const line = originalLine.replace(/`[^`]*`/g, '')
      for (const match of line.matchAll(linkPattern)) {
        let target = match[1].trim()
        if (target.startsWith('<') && target.endsWith('>')) target = target.slice(1, -1)
        target = target.split(/\s+["']/)[0]
        if (/^(https?:|mailto:|data:)/i.test(target)) continue
        const [rawPath, rawAnchor] = target.split('#', 2)
        const decodedPath = decodeURIComponent(rawPath || relativePath)
        const targetPath = rawPath
          ? resolve(dirname(absolute(relativePath)), decodedPath)
          : absolute(relativePath)
        if (!existsSync(targetPath)) {
          fail(`${relativePath}: 本地链接不存在：${target}`)
          continue
        }
        if (rawAnchor && extname(targetPath).toLowerCase() === '.md') {
          const anchors = markdownAnchors(readFileSync(targetPath, 'utf8'))
          if (!anchors.has(decodeURIComponent(rawAnchor).toLowerCase())) {
            fail(`${relativePath}: Markdown 锚点不存在：${target}`)
          }
        }
      }
    }
  }
}

function checkTranslations() {
  const manifest = YAML.parse(read('docs/docs-manifest.yaml'))
  for (const document of manifest.documents) {
    if (!existsSync(absolute(document.source))) {
      fail(`${document.id}: 中文事实源不存在：${document.source}`)
      continue
    }
    const digest = sha256(read(document.source))
    if (digest !== document.source_sha256) {
      fail(`${document.id}: 中文事实源摘要已变化，请同步翻译并刷新 manifest`)
    }
    for (const [locale, translation] of Object.entries(document.translations)) {
      if (!existsSync(absolute(translation.path))) {
        fail(`${document.id}/${locale}: 翻译文件不存在：${translation.path}`)
        continue
      }
      if (!allowDraft && translation.status !== 'synchronized') {
        fail(`${document.id}/${locale}: 翻译状态不是 synchronized`)
      }
      if (!allowDraft && !translation.reviewed_at) {
        fail(`${document.id}/${locale}: 缺少人工复核日期`)
      }
    }
  }
}

function checkScreenshots() {
  const manifestPath = 'omni-frontend/e2e-docs/screenshot-manifest.yaml'
  if (!existsSync(absolute(manifestPath))) {
    fail(`截图 manifest 不存在：${manifestPath}`)
    return
  }
  const manifest = YAML.parse(read(manifestPath))
  const ids = new Set()
  for (const item of manifest.screenshots || []) {
    if (!item.id || ids.has(item.id)) fail(`截图稳定 ID 缺失或重复：${item.id || '<empty>'}`)
    ids.add(item.id)
    if (!existsSync(absolute(item.image))) fail(`${item.id}: 正式图片不存在：${item.image}`)
    if (!existsSync(absolute(item.test))) fail(`${item.id}: Playwright 用例不存在：${item.test}`)
    if (!item.fixture || !item.viewport || !item.locale || !item.version) {
      fail(`${item.id}: fixture、viewport、locale 或 version 信息不完整`)
    }
  }
  const coverage = YAML.parse(read('omni-frontend/e2e-docs/screenshot-coverage.yaml'))
  if (!allowDraft) {
    for (const module of coverage.modules || []) {
      if (!['covered', 'exempt'].includes(module.status)) {
        fail(`${module.id}: 截图覆盖状态仍为 ${module.status}`)
      }
    }
  }
}

function checkSensitiveContent() {
  const suspiciousPatterns = [
    { name: '私钥', pattern: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/ },
    { name: 'JWT', pattern: /eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}/ },
    { name: 'Bearer 令牌', pattern: /Authorization:\s*Bearer\s+(?!<|\$\{|\*{3})[A-Za-z0-9._~-]{24,}/i },
  ]
  for (const relativePath of markdownFiles()) {
    const content = read(relativePath)
    for (const entry of suspiciousPatterns) {
      if (entry.pattern.test(content)) fail(`${relativePath}: 检测到疑似${entry.name}`)
    }
  }
}

const scopes = {
  links: checkLinks,
  i18n: checkTranslations,
  screenshots: checkScreenshots,
  sensitive: checkSensitiveContent,
}

if (selectedScope === 'all') {
  for (const check of Object.values(scopes)) check()
} else if (scopes[selectedScope]) {
  scopes[selectedScope]()
} else {
  fail(`未知检查范围：${selectedScope}`)
}

if (failures.length > 0) {
  for (const message of failures) process.stderr.write(`- ${message}\n`)
  process.stderr.write(`文档质量检查失败：${failures.length} 项\n`)
  process.exit(1)
}

process.stdout.write(`文档质量检查通过：scope=${selectedScope}${allowDraft ? '（允许草稿）' : ''}\n`)
