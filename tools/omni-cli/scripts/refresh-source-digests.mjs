#!/usr/bin/env node
/**
 * refresh-source-digests.mjs — 刷新 docs-manifest.yaml 中的中文事实源 SHA-256。
 *
 * 何时使用：修改了任何 manifest 管理的中文事实源（docs/*.md、docs/guides/*.md）之后，
 * 译文尚未按新源复核前，运行本脚本让 manifest 记录新的源摘要。
 * 译文 status/reviewed_at 由人工复核流程管理，本脚本绝不触碰。
 *
 * 用法：node refresh-source-digests.mjs [--check]
 *   默认：重算全部源摘要并写回 manifest，报告漂移数量。
 *   --check：只报告漂移，不写回（漂移存在时退出码 1）。
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const MANIFEST = path.join(ROOT, 'docs/docs-manifest.yaml');
const checkOnly = process.argv.includes('--check');

const { parse } = await import('yaml');
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

let text = fs.readFileSync(MANIFEST, 'utf8');
for (const d of drifted) {
  const anchor = `source: ${d.source}\n    source_sha256: `;
  const start = text.indexOf(anchor);
  if (start < 0) {
    console.error(`未找到锚点：${d.source}`);
    process.exit(1);
  }
  const cursor = start + anchor.length;
  text = text.slice(0, cursor) + d.to + text.slice(cursor + 64);
}
fs.writeFileSync(MANIFEST, text, 'utf8');
console.log(`manifest 已刷新 ${drifted.length} 个源摘要（译文状态保持不变，待人工复核）。`);
