import { reviewErrors } from './translation-review.mjs';
const ALL_LOCALES = ['en-US', 'ja-JP', 'ko-KR'];
const KNOWN_SCOPES = new Set(['zh-CN', 'full']);
/** 获取文档的有效翻译 locale 列表。 */
export function effectiveLocales(doc) {
  const scope = doc.language_scope || 'full';
  if (!KNOWN_SCOPES.has(scope)) {
    throw new Error(`文档 ${doc.id} 的 language_scope "${scope}" 不在已知范围 [${[...KNOWN_SCOPES].join(', ')}]`);
  }
  if (scope === 'zh-CN') return [];
  return ALL_LOCALES;
}

/** 从当前清单和虚拟文件读取器生成唯一复核队列，接入事务无需先写目标文件。 */
export function buildReviewQueue(manifest, read, generatedAt = new Date().toISOString().slice(0, 10)) {
const priorityOfType = (type) =>
  type === 'system-truth' ? 'P0' : type === 'development-guide' || type === 'module-guide' ? 'P1' : 'P2';

const rows = [];
const chineseOnlyCount = [];
for (const doc of manifest.documents) {
  const locales = effectiveLocales(doc);
  if (locales.length === 0) {
    chineseOnlyCount.push(doc.id);
    continue;
  }
  for (const locale of locales) {
    rows.push({
      id: doc.id,
      type: doc.type,
      priority: priorityOfType(doc.type),
      source: doc.source,
      sha12: String(doc.source_sha256 || '').slice(0, 12),
      locale,
      path: doc.translations[locale]?.path ?? '<missing>',
      status: doc.translations[locale]?.status ?? '<missing>',
      reviewedAt: doc.translations[locale]?.reviewed_at ?? null,
      reviewType: doc.translations[locale]?.review_type ?? null,
      reviewErrors: doc.translations[locale]?.path && read(doc.source) !== null && read(doc.translations[locale].path) !== null
        ? reviewErrors(doc, locale, doc.translations[locale], read(doc.source), read(doc.translations[locale].path), { policy: manifest.release_policy, readEvidence: read })
        : ['译文不存在'],
    });
  }
}

const done = rows.filter((r) => r.reviewErrors.length === 0);
const pending = rows.filter((r) => r.reviewErrors.length !== 0);

/** 统计 markdown 二级标题数量，用于检测源/译结构漂移。 */
const countH2 = (p) => {
  const content = read(p);
  return content === null ? null : (content.match(/^## /gm) || []).length;
};
const structuralDrift = [];
for (const doc of manifest.documents) {
  const locales = effectiveLocales(doc);
  if (locales.length === 0) continue;
  const sourceH2 = countH2(doc.source);
  if (sourceH2 == null) continue;
  for (const locale of locales) {
    const t = doc.translations[locale];
    if (!t) continue;
    const translationH2 = countH2(t.path);
    if (translationH2 != null && translationH2 !== sourceH2) {
      structuralDrift.push({ id: doc.id, locale, sourceH2, translationH2, path: t.path });
    }
  }
}

function renderQueue() {
  const lines = [];
  lines.push('# 文档翻译复核队列');
  lines.push('');
  lines.push('> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，');
  lines.push('> 请勿手工编辑条目；复核人必须在 manifest 中更新 status/reviewed_at 后重新生成本文件。');
  lines.push('');
  lines.push(`- 生成时间：${generatedAt}`);
  lines.push(`- 中文事实源：${manifest.documents.length} 篇（Chinese-only ${chineseOnlyCount.length} 篇；需翻译 ${manifest.documents.length - chineseOnlyCount.length} 篇）`);
  lines.push(`- 译文待复核：${pending.length} 篇（en-US/ja-JP/ko-KR）`);
  lines.push(`- 已完成复核：${done.length} 篇（HUMAN_REVIEW ${done.filter((row) => row.reviewType === 'HUMAN_REVIEW').length}；AI_REVIEW ${done.filter((row) => row.reviewType === 'AI_REVIEW').length}）`);
  if (chineseOnlyCount.length > 0) {
    lines.push(`- Chinese-only 文档（不生成译文行）：${chineseOnlyCount.join(', ')}`);
  }
  lines.push('');
  lines.push('## 复核流程');
  lines.push('');
  lines.push('1. 复核人对照中文事实源逐节核对译文：术语、代码块、命令、路径、链接、数字与表格必须与源一致；');
  lines.push('2. 代码块、命令、API 路径、权限码不翻译；叙述性文案保持目标语言自然表达；');
  lines.push('3. 按当前授权完成实际复核后记录 reviewer、reviewed_at、review_type、reviewed_source_sha256 和 translation_sha256；HUMAN_REVIEW 要求真人，AI_REVIEW 要求清单中的所有者授权及摘要绑定的逐项审校证据，不能冒称人工；');
  lines.push('4. 全部 synchronized 后 `npm run docs:i18n:check` 才能转绿；严禁未经复核直接改状态或日期。');
  lines.push('');
  for (const priority of ['P0', 'P1', 'P2']) {
    const group = manifest.documents.filter((d) => effectiveLocales(d).length > 0 && priorityOfType(d.type) === priority);
    if (!group.length) continue;
    lines.push(`## ${priority} ${priority === 'P0' ? 'system-truth 系统真相' : priority === 'P1' ? '开发/模块指南' : '其他'}（${group.length} 篇 × 3 语言）`);
    lines.push('');
    lines.push('| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |');
    lines.push('| --- | --- | --- | --- | --- | --- |');
    for (const doc of group) {
      const cell = (locale) => {
        const t = doc.translations[locale];
        if (!t) return '<missing>';
        const row = rows.find((item) => item.id === doc.id && item.locale === locale);
        return t.status === 'synchronized' && row.reviewErrors.length === 0
          ? `synchronized@${t.reviewed_at}`
          : t.status === 'synchronized' ? 'invalid-review' : t.status;
      };
      lines.push(`| ${doc.id} | ${doc.source} | ${String(doc.source_sha256).slice(0, 12)} | ${cell('en-US')} | ${cell('ja-JP')} | ${cell('ko-KR')} |`);
    }
    lines.push('');
  }
  if (chineseOnlyCount.length > 0) {
    lines.push('## Chinese-only 文档（仅中文事实源）');
    lines.push('');
    lines.push('| 文档 | 类型 | 中文事实源 |');
    lines.push('| --- | --- | --- |');
    for (const id of chineseOnlyCount) {
      const doc = manifest.documents.find((d) => d.id === id);
      lines.push(`| ${doc.id} | ${doc.type} | ${doc.source} |`);
    }
    lines.push('');
  }
  lines.push('## 复核记录（由复核人追加）');
  lines.push('');
  lines.push('| 日期 | 复核人 | 文档 | 语言 | 结论 |');
  lines.push('| --- | --- | --- | --- | --- |');
  lines.push('|  |  |  |  |  |');
  lines.push('');
  lines.push('## 章节结构差异（复核优先级提示）');
  lines.push('');
  if (!structuralDrift.length) {
    lines.push('未发现 `##` 节数不一致的译文；结构漂移风险低。');
  } else {
    lines.push('以下译文的 `##` 节数与中文源不一致（可能缺少新增章节或残留旧结构），建议优先复核：');
    lines.push('');
    lines.push('| 文档 | 语言 | 源 `##` 节数 | 译文 `##` 节数 | 文件 |');
    lines.push('| --- | --- | --- | --- | --- |');
    for (const d of structuralDrift) {
      lines.push(`| ${d.id} | ${d.locale} | ${d.sourceH2} | ${d.translationH2} | ${d.path} |`);
    }
  }
  lines.push('');
  return lines.join('\n');
}

return { rows, chineseOnlyCount, done, pending, structuralDrift, rendered: renderQueue() };
}
