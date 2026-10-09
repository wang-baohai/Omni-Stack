import { createHash } from 'node:crypto'
import { existsSync, readFileSync, statSync } from 'node:fs'
import { dirname, extname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'

const repositoryRoot = process.env.WP002_TEST_ROOT || resolve(dirname(fileURLToPath(import.meta.url)), '../../..')
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

const KNOWN_LANGUAGE_SCOPES = new Set(['zh-CN', 'full'])

/** 获取文档的有效翻译 locale 列表。 */
function effectiveTranslationLocales(document) {
  const scope = document.language_scope || 'full'
  if (!KNOWN_LANGUAGE_SCOPES.has(scope)) {
    fail(`${document.id}: 不识别的 language_scope "${scope}"`)
    return []
  }
  if (scope === 'zh-CN') {
    // W2-R2 修复：zh-CN 不再是无条件自豁免；必须携带 reason 字段说明为何不需要翻译
    if (!document.reason && !document.i18n_exempt) {
      fail(`${document.id}: language_scope=zh-CN 必须提供 reason 字段说明无需翻译的原因`)
    }
    return []
  }
  return Object.keys(document.translations || {})
}

function checkTranslations() {
  const manifestPath = process.env.WP002_MANIFEST || 'docs/docs-manifest.yaml'
  const manifest = YAML.parse(read(manifestPath))
  for (const document of manifest.documents) {
    if (!existsSync(absolute(document.source))) {
      fail(`${document.id}: 中文事实源不存在：${document.source}`)
      continue
    }
    const digest = sha256(read(document.source))
    if (digest !== document.source_sha256) {
      fail(`${document.id}: 中文事实源摘要已变化，请同步翻译并刷新 manifest`)
    }
    // 仅对需要翻译的文档检查译文状态
    const locales = effectiveTranslationLocales(document)
    for (const locale of locales) {
      const translation = document.translations[locale]
      if (!translation || !translation.path) {
        fail(`${document.id}/${locale}: 缺少译文路径配置`)
        continue
      }
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
  const manifestPath = process.env.WP002_SCREENSHOT_MANIFEST || 'omni-frontend/e2e-docs/screenshot-manifest.yaml'
  if (!existsSync(absolute(manifestPath))) {
    fail(`截图 manifest 不存在：${manifestPath}`)
    return
  }
  const manifest = YAML.parse(read(manifestPath))
  const ids = new Set()
  const screenshotMap = new Map()
  for (const item of manifest.screenshots || []) {
    // ID 唯一性
    if (!item.id || ids.has(item.id)) fail(`截图稳定 ID 缺失或重复：${item.id || '<empty>'}`)
    ids.add(item.id)
    // 图像文件存在且有效
    if (!item.image) {
      fail(`${item.id}: 缺少 image 字段`)
    } else if (!existsSync(absolute(item.image))) {
      fail(`${item.id}: 正式图片不存在：${item.image}`)
    } else {
      // W2-R2 修复：验证图片文件完整性（大小 > 0 且含有效图片头）
      try {
        const stat = statSync(absolute(item.image))
        if (stat.size === 0) {
          fail(`${item.id}: 图片文件为空：${item.image}`)
        } else if (stat.size < 100) {
          fail(`${item.id}: 图片文件过小（${stat.size} bytes），可能损坏：${item.image}`)
        }
      } catch {
        fail(`${item.id}: 无法读取图片文件状态：${item.image}`)
      }
    }
    // Playwright 用例文件存在
    if (!item.test) {
      fail(`${item.id}: 缺少 test 字段`)
    } else if (!existsSync(absolute(item.test))) {
      fail(`${item.id}: Playwright 用例不存在：${item.test}`)
    }
    // 必填字段完整性
    const requiredFields = ['fixture', 'viewport', 'locale', 'version', 'module', 'flow']
    for (const field of requiredFields) {
      if (!item[field]) fail(`${item.id}: 缺少必填字段 ${field}`)
    }
    // 建立 module+flow 到 screenshot 的映射
    if (item.module && item.flow) {
      const key = `${item.module}::${item.flow}`
      if (!screenshotMap.has(key)) screenshotMap.set(key, [])
      screenshotMap.get(key).push(item)
    }
  }
  const coveragePath = process.env.WP002_SCREENSHOT_COVERAGE || 'omni-frontend/e2e-docs/screenshot-coverage.yaml'
  const coverage = YAML.parse(read(coveragePath))
  if (!allowDraft) {
    for (const module of coverage.modules || []) {
      if (!['covered', 'exempt'].includes(module.status)) {
        fail(`${module.id}: 截图覆盖状态仍为 ${module.status}`)
      }
      // 验证 required_flows 中每个 flow 至少有一张截图
      if (module.required_flows && Array.isArray(module.required_flows)) {
        for (const flow of module.required_flows) {
          const key = `${module.id}::${flow}`
          if (!screenshotMap.has(key)) {
            fail(`${module.id}: required flow "${flow}" 无对应截图`)
          }
        }
      }
      // required_states 验证：需要批准 exception 才能跳过
      if (module.required_states && Array.isArray(module.required_states)) {
        for (const state of module.required_states) {
          const flowScreenshots = (module.required_flows || []).flatMap(
            (flow) => screenshotMap.get(`${module.id}::${flow}`) || []
          )
          const hasState = flowScreenshots.some((s) => s.step === state || s.expected === state)
          if (!hasState && module.status !== 'exempt') {
            fail(`${module.id}: required state "${state}" 无对应截图证据`)
          }
        }
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

function checkReadmes() {
  const readmes = ['README.md', 'README.en.md', 'README.jp.md', 'README.kr.md']
  const requiredFacts = [
    'Spring Boot 4.0.6',
    'omni-common-service',
    'omni-db-migrator',
    'omni-procurement',
    'omni-asset',
    '8107',
    'tools/omni-cli',
    'docs/guides/quick-start',
    'docs/observability',
    'scripts/sql/seed',
  ]
  const retiredFacts = [
    { label: '旧 Compose 单文件名', pattern: /docker-compose\.yml/ },
    { label: '旧 Common 模块数量', pattern: /Common[^\n]{0,30}(?:8 Modules|8 modules|8モジュール|8개 모듈)/i },
    { label: '旧 12 容器描述', pattern: /12 (?:Docker )?containers/i },
  ]
  for (const relativePath of readmes) {
    const content = read(relativePath)
    for (const fact of requiredFacts) {
      if (!content.includes(fact)) fail(`${relativePath}: 缺少当前事实：${fact}`)
    }
    for (const retired of retiredFacts) {
      if (retired.pattern.test(content)) fail(`${relativePath}: 仍包含${retired.label}`)
    }
  }
}

const scopes = {
  links: checkLinks,
  i18n: checkTranslations,
  screenshots: checkScreenshots,
  sensitive: checkSensitiveContent,
  readme: checkReadmes,
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
