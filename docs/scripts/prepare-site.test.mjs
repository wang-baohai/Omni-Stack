import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { sitePages, rewriteLinks, isReviewPending, validateWikiMetadata, groupWikiSections, buildWikiSidebar } from './prepare-site.mjs'
import { contentDigest } from '../../tools/omni-cli/scripts/translation-review.mjs'
import { markdownSlug, markdownAnchors } from './markdown-anchors.mjs'

test('numeric, multilingual and repeated headings retain repository link anchors', () => {
  assert.equal(markdownSlug('1. 系统定位'), '1-系统定位')
  assert.equal(markdownSlug('4. API / デバッグ'), '4-api--デバッグ')
  assert.deepEqual([...markdownAnchors('# Intro\n## 1. 系统定位\n```md\n## Fake\n```\n## 1. 系统定位')],
    ['intro', '1-系统定位', '1-系统定位-1'])
})

const document = { id: 'quick-start', source: 'docs/guides/quick-start.md', type: 'development-guide', translations: {
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

test('legacy groups produce no pages while chapter tutorials and engineering facts remain published', () => {
  const removed = [
    { ...document, id: 'wiki-feature-tour', type: 'wiki-guide' },
    { ...document, id: 'legacy-user-guide', type: 'user-guide' },
    { ...document, id: 'legacy-design', type: 'design' },
    { ...document, id: 'legacy-history', type: 'history' },
    { ...document, id: 'legacy-operations', type: 'operations-guide' },
  ]
  assert.deepEqual(sitePages({ documents: removed }), [])
  const current = { ...document, id: 'usage-srm', type: 'user-guide', wiki_section: 'WIKI-03' }
  const pages = sitePages({ documents: [...removed, current, document] })
  assert.equal(pages.length, 8)
  assert.deepEqual(new Set(pages.map((page) => page.document.id)), new Set(['usage-srm', 'quick-start']))
  assert.deepEqual(new Set(pages.map((page) => page.locale)), new Set(['zh-CN', 'en-US', 'ja-JP', 'ko-KR']))
})

test('links to removed Wiki pages resolve to preserved repository references, not dead internal routes', () => {
  const guide = { ...document, id: 'legacy-user-guide', type: 'user-guide' }
  const current = { ...document, id: 'usage-srm', source: 'docs/wiki/usage-srm.md', wiki_section: 'WIKI-03' }
  const pages = sitePages({ documents: [guide, current] })
  assert.equal(rewriteLinks('[legacy guide](../guides/quick-start.md)', current.source, pages, 'https://github.com/example/project'),
    '[legacy guide](https://github.com/example/project/blob/main/docs/guides/quick-start.md)')
  assert.throws(() => sitePages({ documents: [{ ...guide, wiki_section: 'WIKI-14' }] }), /invalid wiki_section/)
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

test('authorized logging and MQ chapters preserve the first ten IDs and old records remain compatible', () => {
  assert.doesNotThrow(() => validateWikiMetadata(document))
  const pages = sitePages({ documents: [document] })
  assert.equal(pages[0].section, null)
  assert.equal(pages[0].order, 0)

  const tagged = sitePages({ documents: [{ ...document, wiki_section: 'WIKI-03', wiki_order: 3 }] })
  assert.equal(tagged[1].section, 'WIKI-03')
  assert.equal(tagged[1].order, 3)

  const entries = [
    { id: 'three-b', section: 'WIKI-03', order: 2, text: 'B' },
    { id: 'three-a', section: 'WIKI-03', order: 1, text: 'A' },
    { id: 'one', section: 'WIKI-01', order: 0, text: 'One' },
    { id: 'engineering', section: null, order: 0, text: 'Engineering' },
  ]
  const grouped = groupWikiSections(entries, { 'WIKI-01': 'Chapter one', 'WIKI-03': 'Chapter three' })
  assert.deepEqual(grouped.chapters.map((group) => group.id), ['WIKI-01', 'WIKI-03'])
  assert.deepEqual(grouped.chapters[1].items.map((item) => item.id), ['three-a', 'three-b'])
  assert.deepEqual(grouped.engineering.map((item) => item.id), ['engineering'])

  const observability = sitePages({ documents: [{ ...document, wiki_section: 'WIKI-10', wiki_order: 1 }] })
  assert.equal(observability.length, 4)
  assert.ok(observability.every(page => page.section === 'WIKI-10' && page.order === 1))
  assert.deepEqual(groupWikiSections([
    { id: 'ten', section: 'WIKI-10', order: 1 },
    { id: 'nine', section: 'WIKI-09', order: 1 },
    { id: 'one', section: 'WIKI-01', order: 1 },
  ]).chapters.map(group => group.id), ['WIKI-01', 'WIKI-09', 'WIKI-10'])
  for (const section of ['WIKI-11', 'WIKI-12', 'WIKI-13']) {
    const added = sitePages({ documents: [{ ...document, wiki_section: section, wiki_order: 1 }] })
    assert.equal(added.length, 4)
    assert.ok(added.every(page => page.section === section && page.order === 1))
  }
  assert.throws(() => validateWikiMetadata({ id: 'bad', wiki_section: 'WIKI-14' }), /invalid wiki_section/)
  assert.throws(() => validateWikiMetadata({ id: 'bad', wiki_order: -1 }), /invalid wiki_order/)
  assert.throws(() => sitePages({ documents: [{ ...document, wiki_section: 'WIKI-14' }] }), /invalid wiki_section/)
})

test('four locales keep the same chapter order', () => {
  const labels = {
    'zh-CN': { 'WIKI-01': '项目介绍', 'WIKI-09': '二次开发', 'WIKI-10': '可观测性', 'WIKI-11': '统一日志采集', 'WIKI-12': '统一MQ管理' },
    'en-US': { 'WIKI-01': 'Introduction', 'WIKI-09': 'Extension', 'WIKI-10': 'Observability', 'WIKI-11': 'Log collection', 'WIKI-12': 'MQ management' },
    'ja-JP': { 'WIKI-01': '紹介', 'WIKI-09': '拡張', 'WIKI-10': '可観測性', 'WIKI-11': '統合ログ収集', 'WIKI-12': '統合MQ管理' },
    'ko-KR': { 'WIKI-01': '소개', 'WIKI-09': '확장', 'WIKI-10': '관측 가능성', 'WIKI-11': '통합 로그 수집', 'WIKI-12': '통합 MQ 관리' },
  }
  const entries = [
    { id: 'twelve', section: 'WIKI-12', order: 0, text: '12' },
    { id: 'eleven', section: 'WIKI-11', order: 0, text: '11' },
    { id: 'ten', section: 'WIKI-10', order: 0, text: '10' },
    { id: 'nine', section: 'WIKI-09', order: 0, text: '9' },
    { id: 'one', section: 'WIKI-01', order: 0, text: '1' },
  ]
  for (const locale of Object.keys(labels)) {
    const chapters = groupWikiSections(entries, labels[locale]).chapters
    assert.deepEqual(chapters.map((group) => group.id), ['WIKI-01', 'WIKI-09', 'WIKI-11', 'WIKI-12', 'WIKI-10'])
    assert.equal(chapters.at(-1).text, labels[locale]['WIKI-10'])
  }
})

test('actual sidebar in all four locales follows the user reading order including engineering facts', () => {
  const expected = ['WIKI-01', 'WIKI-03', 'WIKI-02', 'WIKI-04', 'engineering', 'WIKI-09', 'WIKI-08', 'WIKI-06', 'WIKI-07', 'WIKI-11', 'WIKI-12', 'WIKI-13', 'WIKI-05', 'WIKI-10']
  const entries = expected.map((section, index) => ({ section: section === 'engineering' ? null : section, order: 1, id: 'registered-' + index, text: 'Registered body', link: '/registered-' + index }))
  for (const locale of ['zh-CN', 'en-US', 'ja-JP', 'ko-KR']) {
    const labels = JSON.parse(readFileSync(new URL('../.vitepress/locales/' + locale + '.json', import.meta.url), 'utf8'))
    const groups = buildWikiSidebar(entries.toReversed(), labels)
    assert.deepEqual(groups.map(group => group.items[0].section ?? 'engineering'), expected)
    assert.equal(groups[4].text, labels.truth)
    assert.equal(groups[11].text, labels.chapters['WIKI-13'])
  }
})

test('unwritten chapters never become sidebar placeholders and article order remains local to its section', () => {
  const entries = [{ section: 'WIKI-03', order: 2, id: 'b' }, { section: null, id: 'fact' }, { section: 'WIKI-03', order: 1, id: 'a' }]
  const groups = buildWikiSidebar(entries, { chapters: { 'WIKI-03': 'Usage', 'WIKI-13': 'Not written' }, truth: 'Facts' })
  assert.deepEqual(groups.map(group => group.text), ['Usage', 'Facts'])
  assert.deepEqual(groups[0].items.map(item => item.id), ['a', 'b'])
  assert.deepEqual(groups[1].items.map(item => item.id), ['fact'])
})

test('reviewed upstream license links stay local while unlisted files stay repository links', () => {
  const pages = sitePages({ documents: [document] })
  const body = '[license](../reference/upstream/flowable-8.0.0/LICENSE.txt)\n' +
    '[provenance](../reference/upstream/flowable-8.0.0/provenance.json)\n' +
    '[unlisted](../reference/upstream/flowable-8.0.0/private.log)'
  const result = rewriteLinks(body, document.source, pages, 'https://github.com/example/project')
  assert.ok(result.includes('[license](/reference/upstream/flowable-8.0.0/LICENSE.txt)'))
  assert.ok(result.includes('[provenance](/reference/upstream/flowable-8.0.0/provenance.json)'))
  assert.ok(result.includes('[unlisted](https://github.com/example/project/blob/main/docs/reference/upstream/flowable-8.0.0/private.log)'))
})
