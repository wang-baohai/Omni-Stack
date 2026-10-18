#!/usr/bin/env node
/**
 * docs-review-queue.mjs — 文档翻译复核队列的生成与校验。
 *
 * 背景约束：docs/docs-manifest.yaml 中的译文 status 只能由实际复核人
 * 在按当前清单策略完成实际复核后更新为 synchronized 并填写 reviewed_at；本脚本
 * 仅从 manifest 生成队列视图与一致性校验，绝不代写状态或日期。
 *
 * 语言范围：每篇文档通过 language_scope 字段声明适用语言策略。
 *   - language_scope: zh-CN 表示仅中文，不生成译文行。
 *   - 未声明 language_scope 的文档默认需要 en-US/ja-JP/ko-KR 三种译文。
 *   - 不识别的 language_scope 值视为错误。
 *
 * 用法：
 *   node docs-review-queue.mjs --generate  从 manifest 重新生成 docs/i18n-review-queue.md
 *   node docs-review-queue.mjs --check     校验队列与 manifest 一致并输出统计（默认）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildReviewQueue, effectiveLocales } from './translation-queue.mjs';

const ROOT = process.env.WP002_TEST_ROOT || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const MANIFEST = path.join(ROOT, 'docs/docs-manifest.yaml');
const QUEUE = path.join(ROOT, 'docs/i18n-review-queue.md');
const KNOWN_SCOPES = new Set(['zh-CN', 'full']);
const generate = process.argv.includes('--generate');

const manifestText = fs.readFileSync(MANIFEST, 'utf8');
const yamlModule = await import('yaml');
const manifest = yamlModule.parse(manifestText);

const { rows, chineseOnlyCount, done, pending, rendered } = buildReviewQueue(manifest,
  (relativePath) => fs.existsSync(path.resolve(ROOT, relativePath)) ? fs.readFileSync(path.resolve(ROOT, relativePath), 'utf8') : null);
const renderQueue = () => rendered;
if (generate) {
  fs.writeFileSync(QUEUE, renderQueue(), 'utf8');
  console.log(`复核队列已生成：docs/i18n-review-queue.md（待复核 ${pending.length} 篇，已完成 ${done.length} 篇，Chinese-only ${chineseOnlyCount.length} 篇）`);
  process.exit(0);
}

// 默认 --check：按 doc ID + locale + path + 实际状态逐项验证
let failed = 0;
const fail = (message) => {
  console.error(`- ${message}`);
  failed += 1;
};
for (const row of rows) {
  if (row.status === 'synchronized' && row.reviewErrors.length) {
    fail(`${row.id}/${row.locale}: 无效复核记录：${row.reviewErrors.join('；')}`);
  }
}

// 验证所有中文事实源存在
for (const doc of manifest.documents) {
  if (!fs.existsSync(path.resolve(ROOT, doc.source))) {
    fail(`${doc.id}: 中文事实源不存在：${doc.source}`);
  }
}

// 验证 language_scope 合法性
for (const doc of manifest.documents) {
  const scope = doc.language_scope;
  if (scope && !KNOWN_SCOPES.has(scope)) {
    fail(`${doc.id}: 不识别的 language_scope "${scope}"`);
  }
}

// 验证译文文件存在性（仅对需要翻译的文档）
for (const doc of manifest.documents) {
  const locales = effectiveLocales(doc);
  for (const locale of locales) {
    const t = doc.translations[locale];
    if (!t || !t.path) {
      fail(`${doc.id}/${locale}: 缺少译文路径配置`);
      continue;
    }
    if (!fs.existsSync(path.resolve(ROOT, t.path))) {
      fail(`${doc.id}/${locale}: 译文文件不存在：${t.path}`);
    }
  }
}

// 验证队列文件与 manifest 的逐项一致性
if (!fs.existsSync(QUEUE)) {
  fail('复核队列文档不存在：docs/i18n-review-queue.md（先执行 --generate）');
} else {
  const queueText = fs.readFileSync(QUEUE, 'utf8');
  const withoutDate = (text) => text.replace(/^- 生成时间：.*$/m, '- 生成时间：<date>');
  if (withoutDate(queueText) !== withoutDate(renderQueue())) fail('队列与当前清单、摘要或复核状态不同，请重新 --generate');
  // 逐行验证：每个需要翻译的 doc+locale 组合在队列中有对应行
  for (const row of rows) {
    const expectedCell = row.status === 'synchronized' && row.reviewedAt
      ? `synchronized@${row.reviewedAt}`
      : row.status;
    // 验证文档 ID 出现在表格中
    if (!queueText.includes(`| ${row.id} |`)) {
      fail(`队列缺少文档行：${row.id}`);
    }
  }
  // 验证 Chinese-only 文档不应出现在翻译表格的翻译列中
  for (const id of chineseOnlyCount) {
    // Chinese-only 文档应在 Chinese-only 分组中，不应有翻译列
    if (!queueText.includes(`| ${id} |`)) {
      fail(`队列缺少 Chinese-only 文档行：${id}`);
    }
  }
  // 统计计数一致性：队列中 present-unverified 出现次数应等于 manifest 中的待复核数
  const queuePending = (queueText.match(/present-unverified/g) || []).length;
  const manifestPending = rows.filter((r) => r.status === 'present-unverified').length;
  if (queuePending !== manifestPending) {
    fail(`队列待复核计数 ${queuePending} 与 manifest ${manifestPending} 不一致，请重新 --generate`);
  }
}

if (failed) {
  console.error(`复核队列校验失败：${failed} 项`);
  process.exit(1);
}
console.log(`复核队列校验通过：事实源 ${manifest.documents.length} 篇（Chinese-only ${chineseOnlyCount.length}），译文行 ${rows.length} 篇，待复核 ${pending.length}，已完成 ${done.length}`);
