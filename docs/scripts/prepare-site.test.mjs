import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sitePages, rewriteLinks, isReviewPending } from './prepare-site.mjs'
import { contentDigest } from '../../tools/omni-cli/scripts/translation-review.mjs'
import { markdownSlug, markdownAnchors } from './markdown-anchors.mjs'

test('numeric, multilingual and repeated headings retain repository link anchors', () => {
  assert.equal(markdownSlug('1. 系统定位'), '1-系统定位')
  assert.equal(markdownSlug('4. API / デバッグ'), '4-api--デバッグ')
  assert.deepEqual([...markdownAnchors('# Intro\n## 1. 系统定位\n```md\n## Fake\n```\n## 1. 系统定位')],
    ['intro', '1-系统定位', '1-系统定位-1'])
})

const document = { id: 'quick-start', source: 'docs/guides/quick-start.md', translations: {
  'en-US': { path: 'docs/guides/quick-start.en.md', status: 'present-unverified' },
  'ja-JP': { path: 'docs/guides/quick-start.jp.md', status: 'present-unverified' },
  'ko-KR': { path: 'docs/guides/quick-start.kr.md', status: 'present-unverified' },
} }

test('each language has its own route and missing translations fail', () => {
  const pages = sitePages({ documents: [document] })
  assert.equal(pages.length, 4)
  assert.equal(pages[1].route, 'en-US/guides/quick-start')
  assert.throws(() => sitePages({ documents: [{ ...document, translations: {} }] }), /missing/)
  const references = sitePages({ documents: [{ ...document, language_scope: 'zh-CN', translations: {} }] })
  assert.equal(references.length, 4)
  assert.equal(references[1].sourceOnly, true)
  assert.match(rewriteLinks('[reference](quick-start.md)', document.source, references, 'https://gitee.com/a/b'), /\/zh-CN\/guides\/quick-start\.md/)
})

test('repository relative links become local pages or platform source links without rewriting commands', () => {
  const pages = sitePages({ documents: [document] })
  const content = '[next](quick-start.en.md#start)\n![screen](../images/zh-CN/login.png)\n[source](../../AGENTS.md)\n[evidence](../reference/evidence/scheduling-2026-10-10.json)\n[log](../reference/evidence/private.log)\n[other](../other.json)\n```md\n[next](missing.md)\n```'
  const result = rewriteLinks(content, document.source, pages, 'https://gitee.com/example/project')
  assert.match(result, /\/en-US\/guides\/quick-start\.md#start/)
  assert.match(result, /\/images\/zh-CN\/login\.png/)
  assert.match(result, /https:\/\/gitee.com\/example\/project\/blob\/main\/AGENTS.md/)
  assert.ok(result.includes('[evidence](/reference/evidence/scheduling-2026-10-10.json)'))
  assert.ok(result.includes('[log](https://gitee.com/example/project/blob/main/docs/reference/evidence/private.log)'))
  assert.ok(result.includes('[other](https://gitee.com/example/project/blob/main/docs/other.json)'))
  assert.match(result, /\[next\]\(missing\.md\)/)
  assert.throws(() => rewriteLinks('[escape](../../../private.md)', document.source, pages, 'https://gitee.com/a/b'), /escapes/)
  assert.match(rewriteLinks('[source](../../AGENTS.md)', document.source, pages, 'https://gitee.com/a/b',
    { repositoryRoot: '/moved/project', sourceRef: 'fc165e3' }), /\/blob\/fc165e3\/AGENTS.md/)
})

test('stale or automatic signatures remain visible as pending in the website', () => {
  const source = 'source', translated = 'translation'
  const review = { path: 'unused.md', status: 'synchronized', reviewed_at: '2026-10-09',
    reviewer: 'Test human fixture', review_type: 'HUMAN_REVIEW', reviewed_source_sha256: contentDigest(source),
    translation_sha256: contentDigest(translated) }
  const item = { ...document, translations: Object.fromEntries(['en-US', 'ja-JP', 'ko-KR'].map((locale) => [locale, { ...review }])) }
  const texts = { 'en-US': translated, 'ja-JP': translated, 'ko-KR': translated }
  assert.equal(isReviewPending(item, 'zh-CN', source, texts), false)
  assert.equal(isReviewPending(item, 'en-US', source, { ...texts, 'en-US': 'changed' }), true)
  item.translations['ja-JP'].reviewer = 'Codex'
  assert.equal(isReviewPending(item, 'zh-CN', source, texts), true)
  assert.equal(isReviewPending(item, 'en-US', source, texts), false)
})

test('owner-authorized AI evidence controls the website notice and stale content stays pending', () => {
  const source = 'current source', translated = 'current translation', decision = 'Owner explicitly authorizes AI review.'
  const path = document.translations['en-US'].path
  const at = '2026-10-13 11:00:00'
  const evidencePath = 'docs/reference/reviews/quick-start.json'
  const decisionPath = 'docs/adr/0003-review.md'
  const raw = JSON.stringify({ review_type: 'AI_REVIEW', document_id: document.id, source: document.source,
    source_sha256: contentDigest(source), authorization_sha256: contentDigest(decision), translations: {
      'en-US': { path, reviewer: 'Codex', reviewed_at: at, result: 'approved',
        translation_sha256: contentDigest(translated), findings: [], checks: {
          coverage_and_meaning: 'All sections agree in this controlled fixture.',
          technical_literals: 'Commands and protocol literals agree in this fixture.',
          security_and_failure_semantics: 'Tenant boundaries and failures agree in this fixture.',
          language_and_terminology: 'Terminology reviewed in this controlled fixture.',
          images_and_links: 'References agree in this controlled fixture.',
        } },
    } })
  const review = { path, status: 'synchronized', reviewer: 'Codex', reviewed_at: at, review_type: 'AI_REVIEW',
    reviewed_source_sha256: contentDigest(source), translation_sha256: contentDigest(translated),
    review_evidence: evidencePath, review_evidence_sha256: contentDigest(raw) }
  const item = { ...document, translations: { ...document.translations, 'en-US': review } }
  const context = { policy: { require_human_review: false, ai_review_authorization: { reference: decisionPath,
    reference_sha256: contentDigest(decision), approved_by: 'project-owner', approved_at: '2026-10-13', reviewer: 'Codex' } },
    readEvidence: (file) => file === decisionPath ? decision : file === evidencePath ? raw : null }
  assert.equal(isReviewPending(item, 'en-US', source, { 'en-US': translated }, context), false)
  assert.equal(isReviewPending(item, 'en-US', source, { 'en-US': translated }), true)
  assert.equal(isReviewPending(item, 'en-US', source, { 'en-US': translated + ' changed' }, context), true)
  assert.equal(isReviewPending(item, 'zh-CN', source, { 'en-US': translated }, context), true)
  assert.equal(isReviewPending(item, 'en-US', source, { 'en-US': translated },
    { ...context, readEvidence: () => null }), true)
})


test('text links to delivered screenshots stay inside the independent site', () => {
  const pages = sitePages({ documents: [document] })
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const content = `[open mobile image](../images/${locale}/operations-dashboard-mobile.png)`
    for (const repositoryUrl of ['https://github.com/example/project', 'https://gitee.com/example/project']) {
      assert.equal(rewriteLinks(content, document.source, pages, repositoryUrl),
        `[open mobile image](/images/${locale}/operations-dashboard-mobile.png)`)
    }
  }
})
