import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { test } from 'node:test'
import { parse } from 'yaml'

const root = resolve(import.meta.dirname, '../..')

/** usage 文章的边界：不放源码/命令/SQL/配置围栏，不挂源码片段标记，不要求跳到源码。 */
function usageViolations(content) {
  const violations = []
  if (/<!--\s*omni:source/.test(content)) violations.push('source-marker')
  let inFence = false
  for (const line of content.split('\n')) {
    const fence = /^```(\w*)/.exec(line)
    if (!fence) continue
    if (inFence) {
      inFence = false
      continue
    }
    inFence = true
    if (fence[1] !== 'mermaid') violations.push(`code-fence:${fence[1] || 'plain'}`)
  }
  if (/\]\([^)]*\/blob\//.test(content)) violations.push('required-source-link')
  return violations
}

function assertUsageArticle(content, file) {
  assert.deepEqual(usageViolations(content), [], `${file}: usage article must stay free of source, commands and required source jumps`)
}

function assertPrincipleArticle(content) {
  assert.match(content, /^```(?:java|typescript)/m, 'principle article may quote source')
}

/** 使用篇的边界对实际交付的每种语言都适用，不能只检查中文。 */
function usageArticlePaths(document) {
  const paths = [document.source]
  if ((document.language_scope || 'full') === 'full') {
    for (const locale of ['en-US', 'ja-JP', 'ko-KR']) {
      assert.ok(document.translations?.[locale]?.path, `${document.id}: missing usage translation ${locale}`)
      paths.push(document.translations[locale].path)
    }
  }
  return paths
}

test('usage-marked articles keep no code fences, source markers or required source links in every applicable language', () => {
  const manifest = parse(readFileSync(resolve(root, 'docs/docs-manifest.yaml'), 'utf8'))
  const usage = manifest.documents.filter((document) => document.article_kind === 'usage' && document.source.endsWith('.md'))
  assert.ok(usage.length > 0, '至少应有一篇 usage 文章参与检查')
  for (const document of usage) {
    for (const path of usageArticlePaths(document)) {
      assertUsageArticle(readFileSync(resolve(root, path), 'utf8').replaceAll('\r\n', '\n'), path)
    }
  }
})

test('a translated-only code fence is rejected and required language omissions cannot shrink usage coverage', () => {
  const document = { id: 'controlled-usage', source: 'guide.md', translations: {
    'en-US': { path: 'guide.en.md' }, 'ja-JP': { path: 'guide.jp.md' }, 'ko-KR': { path: 'guide.kr.md' },
  } }
  const contents = { 'guide.md': '点击保存。', 'guide.en.md': 'Click Save.', 'guide.jp.md': '保存します。',
    'guide.kr.md': '```sh\nnpm run dev\n```' }
  assert.throws(() => usageArticlePaths(document).forEach(path => assertUsageArticle(contents[path], path)), error =>
    error instanceof Error && error.message.startsWith('guide.kr.md: usage article') &&
    Array.isArray(error.actual) && error.actual.includes('code-fence:sh'))
  delete document.translations['ja-JP']
  assert.throws(() => usageArticlePaths(document), /missing usage translation ja-JP/)
  assert.deepEqual(usageArticlePaths({ ...document, language_scope: 'source-only' }), ['guide.md'])
})

test('the same Java excerpt fails in usage and is allowed in a principle article', () => {
  const snippet = '```java\nclass Sample {}\n```'
  assert.throws(() => assertUsageArticle(snippet, 'usage.md'), /code-fence/)
  assert.doesNotThrow(() => assertPrincipleArticle(snippet))
  assert.doesNotThrow(() => assertUsageArticle('Click `Save`, then read [the guide](../guides/quick-start.md).', 'ok.md'))
  assert.throws(() => assertUsageArticle('[source](https://github.com/a/b/blob/main/A.java)', 'link.md'), /required-source-link/)
  assert.doesNotThrow(() => assertUsageArticle('```mermaid\nflowchart LR\nA --> B\n```', 'diagram.md'))
})
