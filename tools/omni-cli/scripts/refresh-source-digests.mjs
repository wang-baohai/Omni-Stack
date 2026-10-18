#!/usr/bin/env node
/**
 * refresh-source-digests.mjs — 刷新 docs-manifest.yaml 中的中文事实源 SHA-256。
 *
 * 何时使用：修改了任何 manifest 管理的中文事实源（docs/*.md、docs/guides/*.md）之后，
 * 译文尚未按新源复核前，运行本脚本让 manifest 记录新的源摘要。
 * 源发生变化时使旧语义复核失效；本脚本从不批准复核或填写复核日期。
 *
 * 用法：node refresh-source-digests.mjs [--check]
 *   默认：重算源摘要、使受影响复核失效并写回，报告漂移数量。
 *   --check：只报告漂移，不写回（漂移存在时退出码 1）。
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const MANIFEST = path.join(ROOT, 'docs/docs-manifest.yaml');
const checkOnly = process.argv.includes('--check');

const { parse, stringify } = await import('yaml');
const manifest = parse(fs.readFileSync(MANIFEST, 'utf8'));

const drifted = [];
for (const doc of manifest.documents) {
  const abs = path.join(ROOT, doc.source);
  const sha = crypto.createHash('sha256').update(fs.readFileSync(abs)).digest('hex');
  if (sha !== doc.source_sha256) drifted.push({ id: doc.id, source: doc.source, from: doc.source_sha256, to: sha });
}

if (!drifted.length) {
  console.log('全部中文事实源摘要与 manifest 一致，无需刷新。');
  process.exit(0);
}

console.log(`检测到 ${drifted.length} 个源摘要漂移：`);
drifted.forEach((d) => console.log(`  ${d.id} (${d.source})`));

if (checkOnly) process.exit(1);

for (const d of drifted) {
  const document = manifest.documents.find((entry) => entry.id === d.id);
  document.source_sha256 = d.to;
  for (const translation of Object.values(document.translations || {})) {
    if (translation.status === 'synchronized') {
      translation.previous_review = {
        reviewer: translation.reviewer || null, reviewed_at: translation.reviewed_at || null,
        source_sha256: translation.reviewed_source_sha256 || d.from,
        translation_sha256: translation.translation_sha256 || null,
        review_type: translation.review_type || null,
      };
    }
    translation.status = 'present-unverified';
    translation.reviewed_at = null;
    delete translation.reviewed_source_sha256;
    delete translation.review_type;
    delete translation.reviewer;
    delete translation.translation_sha256;
  }
}
fs.writeFileSync(MANIFEST, stringify(manifest, { lineWidth: 0 }), 'utf8');
console.log(`manifest 已刷新 ${drifted.length} 个源摘要；受影响译文等待按现行授权策略重新完成语义复核。`);
