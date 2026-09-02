#!/usr/bin/env node
/**
 * docs-review-queue.mjs — 文档翻译人工复核队列的生成与校验。
 *
 * 背景约束：docs/docs-manifest.yaml 中的译文 status 只能由实际复核人
 * 在完成人工复核后更新为 synchronized 并填写 reviewed_at；本脚本
 * 仅从 manifest 生成队列视图与一致性校验，绝不代写状态或日期。
 *
 * 用法：
 *   node docs-review-queue.mjs --generate  从 manifest 重新生成 docs/i18n-review-queue.md
 *   node docs-review-queue.mjs --check     校验队列与 manifest 一致并输出统计（默认）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const MANIFEST = path.join(ROOT, 'docs/docs-manifest.yaml');
const QUEUE = path.join(ROOT, 'docs/i18n-review-queue.md');
const LOCALES = ['en-US', 'ja-JP', 'ko-KR'];
const generate = process.argv.includes('--generate');

const manifestText = fs.readFileSync(MANIFEST, 'utf8');
// 与 docs-quality.mjs 一致使用 yaml 包解析。
const yamlModule = await import('yaml');
const manifest = yamlModule.parse(manifestText);

const priorityOfType = (type) =>
  type === 'system-truth' ? 'P0' : type === 'development-guide' || type === 'module-guide' ? 'P1' : 'P2';

const rows = [];
for (const doc of manifest.documents) {
  for (const locale of LOCALES) {
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
    });
  }
}

const done = rows.filter((r) => r.status === 'synchronized' && r.reviewedAt);
const pending = rows.filter((r) => !(r.status === 'synchronized' && r.reviewedAt));

/** 统计 markdown 二级标题数量，用于检测源/译结构漂移。 */
const countH2 = (p) => {
  const abs = path.resolve(ROOT, p);
  if (!fs.existsSync(abs)) return null;
  return (fs.readFileSync(abs, 'utf8').match(/^## /gm) || []).length;
};
const structuralDrift = [];
for (const doc of manifest.documents) {
  const sourceH2 = countH2(doc.source);
  if (sourceH2 == null) continue;
  for (const locale of LOCALES) {
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
  lines.push('# 文档翻译人工复核队列');
  lines.push('');
  lines.push('> 本文件由 `node tools/omni-cli/scripts/docs-review-queue.mjs --generate` 自动生成，');
  lines.push('> 请勿手工编辑条目；复核结论由复核人在 docs/docs-manifest.yaml 更新 status/reviewed_at 后重新生成本文件。');
  lines.push('');
  lines.push(`- 生成时间：${new Date().toISOString().slice(0, 10)}`);
  lines.push(`- 中文事实源：${manifest.documents.length} 篇；译文待复核：${pending.length} 篇（en-US/ja-JP/ko-KR 各 ${manifest.documents.length}）`);
  lines.push(`- 已完成人工复核：${done.length} 篇`);
  lines.push('');
  lines.push('## 复核流程');
  lines.push('');
  lines.push('1. 复核人对照中文事实源逐节核对译文：术语、代码块、命令、路径、链接、数字与表格必须与源一致；');
  lines.push('2. 代码块、命令、API 路径、权限码不翻译；叙述性文案保持目标语言自然表达；');
  lines.push('3. 复核通过后，由复核人（非翻译初稿生成者）在 docs/docs-manifest.yaml 将对应译文 status 改为 synchronized 并填写 reviewed_at（ISO 日期），随后重新生成本队列；');
  lines.push('4. 全部 synchronized 后 `npm run docs:i18n:check` 才能转绿；严禁未经复核直接改状态或日期。');
  lines.push('');
  for (const priority of ['P0', 'P1', 'P2']) {
    const group = manifest.documents.filter((d) => priorityOfType(d.type) === priority);
    if (!group.length) continue;
    lines.push(`## ${priority} ${priority === 'P0' ? 'system-truth 系统真相' : priority === 'P1' ? '开发/模块指南' : '其他'}（${group.length} 篇 × 3 语言）`);
    lines.push('');
    lines.push('| 文档 | 中文事实源 | 源摘要(前12) | en-US | ja-JP | ko-KR |');
    lines.push('| --- | --- | --- | --- | --- | --- |');
    for (const doc of group) {
      const cell = (locale) => {
        const t = doc.translations[locale];
        if (!t) return '<missing>';
        return t.status === 'synchronized'
          ? `synchronized@${t.reviewed_at}`
          : t.status;
      };
      lines.push(`| ${doc.id} | ${doc.source} | ${String(doc.source_sha256).slice(0, 12)} | ${cell('en-US')} | ${cell('ja-JP')} | ${cell('ko-KR')} |`);
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

if (generate) {
  fs.writeFileSync(QUEUE, renderQueue(), 'utf8');
  console.log(`复核队列已生成：docs/i18n-review-queue.md（待复核 ${pending.length} 篇，已完成 ${done.length} 篇）`);
  process.exit(0);
}

// 默认 --check：队列文档与 manifest 一致性 + 统计
let failed = 0;
const fail = (message) => {
  console.error(`- ${message}`);
  failed += 1;
};
if (!fs.existsSync(QUEUE)) {
  fail('复核队列文档不存在：docs/i18n-review-queue.md（先执行 --generate）');
} else {
  const queueText = fs.readFileSync(QUEUE, 'utf8');
  for (const row of rows) {
    if (!queueText.includes(`| ${row.id} |`)) fail(`队列缺少文档行：${row.id}`);
  }
  const queuePending = (queueText.match(/present-unverified/g) || []).length;
  const manifestPending = rows.filter((r) => r.status === 'present-unverified').length;
  if (queuePending !== manifestPending) {
    fail(`队列待复核计数 ${queuePending} 与 manifest ${manifestPending} 不一致，请重新 --generate`);
  }
}
if (manifest.documents.some((d) => !fs.existsSync(path.resolve(ROOT, d.source)))) {
  fail('存在缺失的中文事实源');
}
for (const row of rows) {
  if (!fs.existsSync(path.resolve(ROOT, row.path))) fail(`${row.id}/${row.locale}: 译文不存在 ${row.path}`);
}
if (failed) {
  console.error(`复核队列校验失败：${failed} 项`);
  process.exit(1);
}
console.log(`复核队列校验通过：事实源 ${manifest.documents.length} 篇，译文 ${rows.length} 篇，待复核 ${pending.length}，已完成 ${done.length}`);
