import { createHash } from 'node:crypto'

export const translationLocales = ['en-US', 'ja-JP', 'ko-KR']
export const contentDigest = (content) => createHash('sha256').update(content).digest('hex')
export const aiReviewTopics = ['coverage_and_meaning', 'technical_literals',
  'security_and_failure_semantics', 'language_and_terminology', 'images_and_links']

/** 默认保留人工复核；经所有者明确授权的 AI 审校必须携带当前内容的可追溯证据。 */
export function reviewErrors(document, locale, translation, sourceContent, translatedContent, context = {}) {
  const errors = []
  const prefix = document.id + '/' + locale
  const ai = translation.review_type === 'AI_REVIEW'
  const label = ai ? 'AI审校' : '人工复核'
  const fail = (message) => errors.push(prefix + ': ' + message)
  if (translation.status !== 'synchronized') return [prefix + ': 翻译状态不是 synchronized']
  if (!translation.reviewed_at || Number.isNaN(Date.parse(translation.reviewed_at))) fail('缺少有效' + label + '日期')
  if (translation.reviewed_source_sha256 !== contentDigest(sourceContent)) fail(label + '源摘要失效')
  if (translation.translation_sha256 !== contentDigest(translatedContent)) fail(label + '译文摘要失效')
  if (!ai) {
    if (translation.review_type !== 'HUMAN_REVIEW') fail('缺少 HUMAN_REVIEW 签核类型')
    if (!translation.reviewer?.trim() || /^(?:codex|chatgpt|ai|gpt[-\s\d.]*)$/i.test(translation.reviewer.trim())) {
      fail('缺少真实人工复核人')
    }
    return errors
  }

  const policy = context.policy || {}
  const authorization = policy.ai_review_authorization
  if (policy.require_human_review !== false || !authorization?.reference
      || !authorization.approved_by?.trim() || Number.isNaN(Date.parse(authorization.approved_at || ''))
      || !/^[a-f0-9]{64}$/.test(authorization.reference_sha256 || '')
      || !authorization.reviewer?.trim() || translation.reviewer !== authorization.reviewer) {
    fail('缺少所有者明确授权的 AI_REVIEW 策略')
    return errors
  }
  if (!/^docs\/adr\/[A-Za-z0-9._-]+\.md$/.test(authorization.reference)
      || !/^docs\/reference\/reviews\/[A-Za-z0-9._-]+\.json$/.test(translation.review_evidence || '')) {
    fail('AI审校授权或证据路径不合法')
    return errors
  }
  try {
    const decision = context.readEvidence?.(authorization.reference)
    if (typeof decision !== 'string' || contentDigest(decision) !== authorization.reference_sha256) {
      fail('AI审校授权依据不存在或摘要失效')
    }
    const raw = context.readEvidence?.(translation.review_evidence)
    if (typeof raw !== 'string' || contentDigest(raw) !== translation.review_evidence_sha256) {
      fail('AI审校证据不存在或摘要失效')
      return errors
    }
    const evidence = JSON.parse(raw)
    const record = evidence.translations?.[locale]
    if (evidence.review_type !== 'AI_REVIEW' || evidence.document_id !== document.id
        || evidence.source !== document.source || evidence.source_sha256 !== contentDigest(sourceContent)
        || evidence.authorization_sha256 !== authorization.reference_sha256
        || record?.result !== 'approved' || record.reviewer !== translation.reviewer
        || record.reviewed_at !== translation.reviewed_at || record.path !== translation.path
        || record.translation_sha256 !== contentDigest(translatedContent)) {
      fail('AI审校证据与当前文档、语言或复核记录不一致')
    }
    if (aiReviewTopics.some((topic) => typeof record?.checks?.[topic] !== 'string'
        || record.checks[topic].trim().length < 12)) {
      fail('AI审校证据缺少逐项语义、技术字面量、安全、语言或图片链接结论')
    }
    if (!Array.isArray(record?.findings) || record.findings.some((finding) => finding.status !== 'resolved')) {
      fail('AI审校仍有未解决问题或缺少问题清单')
    }
  } catch {
    fail('AI审校依据无法读取或解析')
  }
  return errors
}
