import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import YAML from 'yaml'
import { reviewErrors, translationLocales } from '../../tools/omni-cli/scripts/translation-review.mjs'
import { copyReferencedUpstreamAssets, upstreamAssetRoute } from './publish-upstream.mjs'

export const locales = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']

/** 章节标识保持稳定；XXL-JOB 使用用户追加的第十三个内容标识。 */
export const WIKI_SECTIONS = ['WIKI-01', 'WIKI-02', 'WIKI-03', 'WIKI-04', 'WIKI-05', 'WIKI-06', 'WIKI-07', 'WIKI-08', 'WIKI-09', 'WIKI-10', 'WIKI-11', 'WIKI-12', 'WIKI-13']
/** 阅读顺序只组织现有条目，不改变 WP/S 或原章节标识；工程事实穿插在启动之后。 */
export const WIKI_DISPLAY_ORDER = ['WIKI-01', 'WIKI-03', 'WIKI-02', 'WIKI-04', 'engineering', 'WIKI-09', 'WIKI-08', 'WIKI-06', 'WIKI-07', 'WIKI-11', 'WIKI-12', 'WIKI-13', 'WIKI-05', 'WIKI-10']

/** 校验登记的章节元数据；工程事实可以不标章节。 */
export function validateWikiMetadata(document) {
  if (document.wiki_section !== undefined && !WIKI_SECTIONS.includes(document.wiki_section)) {
    throw new Error(`${document.id}: invalid wiki_section ${document.wiki_section}`)
  }
  if (document.wiki_order !== undefined && (!Number.isInteger(document.wiki_order) || document.wiki_order < 0)) {
    throw new Error(`${document.id}: invalid wiki_order ${document.wiki_order}`)
  }
}

/** 按用户阅读顺序分组；空章节不输出，未标章节条目仍保持工程事实分类。 */
export function groupWikiSections(entries, labels = {}) {
  const chapters = WIKI_DISPLAY_ORDER.filter(id => id !== 'engineering').map((id) => ({
    id,
    text: labels[id] ?? id,
    items: entries.filter((entry) => entry.section === id).sort((left, right) => (left.order ?? 0) - (right.order ?? 0)),
  })).filter((group) => group.items.length > 0)
  return { chapters, engineering: entries.filter((entry) => !entry.section) }
}

/** VitePress 使用同一份分类结果；未登记四语正文的空组不产生占位入口。 */
export function buildWikiSidebar(entries, text) {
  const { chapters, engineering } = groupWikiSections(entries, text.chapters)
  const groups = new Map(chapters.map(group => [group.id, { text: group.text, items: group.items }]))
  groups.set('engineering', { text: text.truth, items: engineering })
  return WIKI_DISPLAY_ORDER.map(id => groups.get(id)).filter(group => group?.items.length > 0)
}

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')

/** 只发布登记章节与工程事实；旧学习路径、操作指南和设计历史不再生成 Wiki 页面。 */
export function isWikiPagePublished(document) {
  return Boolean(document.wiki_section) || (!document.id.startsWith('wiki-') &&
    ['system-truth', 'development-guide', 'module-guide'].includes(document.type))
}

/** 从规范清单构造实际发布的路径；正文仍只维护在仓库 Markdown 中。 */
export function sitePages(manifest) {
  return manifest.documents.filter((document) => document.source.endsWith('.md')).flatMap((document) => {
    validateWikiMetadata(document)
    if (!isWikiPagePublished(document)) return []
    const sourceRoute = document.source.replace(/^docs\//, '').replace(/\.md$/, '')
    const section = document.wiki_section ?? null
    const order = document.wiki_order ?? 0
    const pages = [{ source: document.source, route: `zh-CN/${sourceRoute}`, locale: 'zh-CN', document, section, order }]
    if ((document.language_scope || 'full') === 'full') {
      for (const locale of locales.slice(1)) {
        const translation = document.translations?.[locale]
        if (!translation?.path || translation.status === 'missing') throw new Error(`${document.id}: missing ${locale}`)
        pages.push({ source: translation.path, route: `${locale}/${sourceRoute}`, locale, document, section, order })
      }
    } else {
      // 豁免文档保留中文正文；其他语言提供说明与规范源入口，避免语言切换静默 404。
      for (const locale of locales.slice(1)) pages.push({ source: document.source, route: `${locale}/${sourceRoute}`, locale, document, sourceOnly: true, section, order })
    }
    return pages
  })
}

/** 保留仓库可读相对链接，站点投影中转成页面、图片或托管平台源码链接。 */
export function rewriteLinks(content, source, pages, repositoryUrl, { repositoryRoot = root, sourceRef = 'main' } = {}) {
  const routeMap = new Map(pages.filter((page) => !page.sourceOnly).map((page) => [page.source, `/${page.route}.md`]))
  let fence = null
  return content.split('\n').map((line) => {
    const marker = /^\s*(`{3,}|~{3,})/.exec(line)
    if (marker) {
      if (!fence) fence = marker[1][0]
      else if (fence === marker[1][0]) fence = null
      return line
    }
    if (fence) return line
    return line.replace(/(!?\[[^\]]*\]\()([^\s)]+)(\))/g, (match, prefix, target, end) => {
      if (/^(?:https?:|mailto:|data:|#)/i.test(target)) return match
      const [path, anchor] = target.split('#', 2)
      const absolute = resolve(repositoryRoot, dirname(source), decodeURIComponent(path))
      const repoPath = relative(repositoryRoot, absolute).split(sep).join('/')
      if (repoPath.startsWith('../') || repoPath === '..') throw new Error(`${source}: link escapes repository: ${target}`)
      const fragment = anchor ? `#${anchor}` : ''
      if (routeMap.has(repoPath)) return `${prefix}${routeMap.get(repoPath)}${fragment}${end}`
      if (repoPath.startsWith('docs/images/')) {
        return `${prefix}/${repoPath.slice(5)}${fragment}${end}`
      }
      // 正式脱敏证据随独立站点交付，不依赖远端仓库的分支或网络。
      if (/^docs\/reference\/evidence\/[a-z0-9][a-z0-9.-]*\.json$/.test(repoPath)) {
        return `${prefix}/${repoPath.slice(5)}${fragment}${end}`
      }
      const upstreamRoute = upstreamAssetRoute(repoPath)
      if (upstreamRoute) return `${prefix}${upstreamRoute}${fragment}${end}`
      const encoded = repoPath.split('/').map(encodeURIComponent).join('/')
      return `${prefix}${repositoryUrl}/blob/${encodeURIComponent(sourceRef)}/${encoded}${fragment}${end}`
    })
  }).join('\n')
}

/** 站点提示与严格质量门使用同一签核判断，不只相信 synchronized 字面值。 */
export function isReviewPending(document, locale, source, translations, reviewContext = {}) {
  if ((document.language_scope || 'full') !== 'full') return false
  const selected = locale === 'zh-CN' ? translationLocales : [locale]
  return selected.some((language) => {
    const entry = document.translations?.[language]
    return !entry || reviewErrors(document, language, entry, source, translations[language] || '', reviewContext).length > 0
  })
}

export function prepareSite(repositoryRoot = root) {
  const docs = resolve(repositoryRoot, 'docs')
  const output = resolve(docs, '.vitepress/source')
  // 只清理本工具专属投影目录，规范文档与静态产物不受影响。
  if (!output.startsWith(`${docs}${sep}.vitepress${sep}`)) throw new Error('invalid site output')
  rmSync(output, { recursive: true, force: true })
  mkdirSync(output, { recursive: true })
  const manifest = YAML.parse(readFileSync(resolve(docs, 'docs-manifest.yaml'), 'utf8'))
  const pages = sitePages(manifest)
  const repositoryUrl = process.env.DOCS_REPOSITORY_URL || 'https://github.com/wang-baohai/Omni-Stack'
  const sourceRef = process.env.DOCS_SOURCE_REF || 'main'
  const base = process.env.DOCS_BASE || '/'
  if (!/^\/(?:[A-Za-z0-9_-]+\/)*$/.test(base)) throw new Error('DOCS_BASE must be / or a safe path ending in /')
  if (!/^[A-Za-z0-9_-]+(?:[./][A-Za-z0-9_-]+)*$/.test(sourceRef)) throw new Error('Invalid DOCS_SOURCE_REF')
  if (!/^https:\/\/(?:github\.com|gitee\.com)\/[^/]+\/[^/]+\/?$/.test(repositoryUrl)) {
    throw new Error('DOCS_REPOSITORY_URL must be a GitHub/Gitee repository URL')
  }
  const reviewText = {
    'zh-CN': '翻译复核尚未完成。当前分支用于审阅；页面生成成功不代表发布验收通过。',
    'en-US': 'Translation review is pending. This branch is for review; a successful site build does not mean release acceptance.',
    'ja-JP': '翻訳のレビューは未完了です。このブランチはレビュー用です。サイトのビルド成功はリリース承認を意味しません。',
    'ko-KR': '번역 검토가 완료되지 않았습니다. 이 브랜치는 검토용이며 사이트 빌드 성공이 릴리스 승인을 의미하지 않습니다.',
  }
  const navigation = Object.fromEntries(locales.map((locale) => [locale, []]))
  for (const page of pages) {
    const path = resolve(repositoryRoot, page.source)
    if (!existsSync(path)) throw new Error(`missing source: ${page.source}`)
    const content = readFileSync(path, 'utf8').replace(/\r\n?/g, '\n')
    const title = /^#\s+(.+)$/m.exec(content)?.[1] || page.document.id
    const destination = resolve(output, `${page.route}.md`)
    mkdirSync(dirname(destination), { recursive: true })
    const sourceContent = readFileSync(resolve(repositoryRoot, page.document.source), 'utf8')
    const translations = Object.fromEntries(Object.entries(page.document.translations || {}).map(([locale, entry]) =>
      [locale, existsSync(resolve(repositoryRoot, entry.path)) ? readFileSync(resolve(repositoryRoot, entry.path), 'utf8') : '']))
    const pending = isReviewPending(page.document, page.locale, sourceContent, translations, {
      policy: manifest.release_policy,
      readEvidence: (path) => existsSync(resolve(repositoryRoot, path)) ? readFileSync(resolve(repositoryRoot, path), 'utf8') : null,
    })
    const banner = pending ? `> ${reviewText[page.locale]}\n\n` : ''
    // Vue 模板分隔符在规范文档中也可能表示协议字段，用 v-pre 保留原文。
    const interfaceCopy = JSON.parse(readFileSync(resolve(docs, `.vitepress/locales/${page.locale}.json`), 'utf8'))
    const originalRoute = `zh-CN/${page.document.source.replace(/^docs\//, '').replace(/\.md$/, '')}`
    const body = page.sourceOnly
      ? `# ${interfaceCopy.sourceOnlyTitle}: ${title}\n\n${interfaceCopy.sourceOnlyNotice}\n\n[${interfaceCopy.sourceOnlyLink}](/${originalRoute}.md)\n`
      : rewriteLinks(content, page.source, pages, repositoryUrl.replace(/\/$/, ''), { repositoryRoot, sourceRef })
    writeFileSync(destination, `---\noutline: [2, 3]\n---\n\n${banner}<div v-pre>\n\n${body}\n\n</div>\n`)
    if (!page.sourceOnly) navigation[page.locale].push({ text: title, link: `/${page.route}`, type: page.document.type, id: page.document.id, section: page.section, order: page.order })
  }
  if (existsSync(resolve(docs, 'images'))) cpSync(resolve(docs, 'images'), resolve(output, 'public/images'), { recursive: true })
  copyReferencedUpstreamAssets(repositoryRoot, output, pages)
  const evidenceDirectory = resolve(docs, 'reference/evidence')
  if (existsSync(evidenceDirectory)) {
    const publicEvidence = resolve(output, 'public/reference/evidence')
    mkdirSync(publicEvidence, { recursive: true })
    // 只带正式目录中的直接 JSON 文件，不复制子目录、软链接、日志或私有配置。
    for (const entry of readdirSync(evidenceDirectory, { withFileTypes: true })) {
      if (!entry.isFile() || !/^[a-z0-9][a-z0-9.-]*\.json$/.test(entry.name)) continue
      const source = resolve(evidenceDirectory, entry.name)
      JSON.parse(readFileSync(source, 'utf8'))
      cpSync(source, resolve(publicEvidence, entry.name))
    }
  }
  // 语言根路径供标题链接和对应页面切换使用，首页复用同一份规范正文。
  for (const locale of locales) {
    const home = readFileSync(resolve(output, locale, 'wiki/index.md'), 'utf8')
    const metadata = `search: false\nhead:\n  - - link\n    - rel: canonical\n      href: ${base}${locale}/wiki/index.html\n`
    writeFileSync(resolve(output, locale, 'index.md'), home.replace(/^---\n/, `---\n${metadata}`))
  }
  writeFileSync(resolve(output, 'index.md'), '# Omni-Stack Wiki\n\n' + locales.map((locale) => `[${locale}](/${locale}/wiki/index.md)`).join(' · ') + '\n')
  writeFileSync(resolve(docs, '.vitepress/navigation.json'), JSON.stringify(navigation, null, 2) + '\n')
  console.log(`Prepared ${pages.length} canonical pages and ${locales.length} language homes from one repository content source.`)
  return pages
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) prepareSite()
