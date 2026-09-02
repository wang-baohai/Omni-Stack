#!/usr/bin/env node
/**
 * locale-parity-check.mjs — 四语言 locale 资源一致性检查。
 *
 * 规则：
 *   R1  四语言扁平键集合必须完全一致；
 *   R2  含 {placeholder} 的键在四语言中的占位符集合必须一致；
 *   R3  非中文 locale 不得出现与 zh-CN 完全相同的整段中文文案
 *       （相同值 + 含 CJK + 长度 >= 6，语言名称等白名单除外）；
 *   R4  ja/ko 不得通过大范围继承 enUS 掩盖未翻译功能
 *       （命名空间键数 >= 5 且与 en 相同比率 > 60% 即报告；
 *        默认 warning，--strict 时失败）。
 *
 * 白名单为精确键列表，禁止全局豁免。新增白名单条目必须在 PR 描述中说明理由。
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const DIR = path.join(ROOT, 'omni-frontend/src/locales');
const LOCALES = ['zh-CN', 'en-US', 'ja-JP', 'ko-KR'];
const NON_ZH = ['en-US', 'ja-JP', 'ko-KR'];
const CJK = /[\u3040-\u30ff\uac00-\ud7af\u4e00-\u9fff]/;

/** 白名单：R3 中允许与 zh-CN 同值的键（按 locale 或 '*'）。 */
const ZH_IDENTICAL_WHITELIST = {
  '*': [
    // 语言自身名称与短名，属于专有名词，四语言 intentionally 相同。
    'lang.zhCN', 'lang.enUS', 'lang.jaJP', 'lang.koKR',
    'lang.zhShort', 'lang.enShort', 'lang.jaShort', 'lang.koShort',
  ],
  'ja-JP': [
    // 中日同形合法词：日文正确译文与中文恰好同形，非未翻译 fallback。
    'common.save', 'common.actions', 'common.warning',
    'crmContact.primary', 'crmActivity.content',
    'assetLedger.years', 'assetLedger.statusInUse',
    'workspaceApproval.quantity',
    'workflow.statusNormal', 'workflow.warningCount', 'workflow.unsaved',
    'srmCommon.name', 'srmSupplierOverview.name', 'srmSupplierOverview.primary',
    'procurementMaterialPage.actions', 'procurementApprovalRules.actions',
    'assetOverviewPage.inUse',
    'procurementGoodsReceiptForm.qualityPass', 'procurementGoodsReceiptForm.qualityFail',
    'procurementRequisitionPage.quantity', 'procurementRfqCompare.materialLabel',
    'operLog.statusSuccess', 'procurementOverviewPage.spendAnalysis',
  ],
};

/** R4 豁免：允许与 en 保持一致的命名空间（技术词/专有名词为主）。 */
const NS_INHERIT_WHITELIST = new Set([]);

const strict = process.argv.includes('--strict');
const load = (l) => {
  const code = fs.readFileSync(path.join(DIR, `${l}.ts`), 'utf8')
    .replace(/export\s+default/, '')
    .replace(/^import.*$/gm, '');
  return new Function('enUS', `return (${code})`)(cache['en-US']);
};
const flat = (obj, prefix = '', out = new Map()) => {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') flat(v, key, out);
    else out.set(key, String(v));
  }
  return out;
};

const cache = {};
const maps = {};
for (const l of LOCALES) {
  cache[l] = load(l);
  maps[l] = flat(cache[l]);
}
const allKeys = new Set();
LOCALES.forEach((l) => maps[l].forEach((_, k) => allKeys.add(k)));

const errors = [];
const warnings = [];

// R1 键集合一致
for (const l of LOCALES) {
  const missing = [...allKeys].filter((k) => !maps[l].has(k));
  if (missing.length) errors.push(`R1[${l}] 缺少 ${missing.length} 个键: ${missing.slice(0, 20).join(', ')}${missing.length > 20 ? ' ...' : ''}`);
}

// R2 placeholder 一致
const ph = (s) => [...new Set([...String(s).matchAll(/\{(\w+)\}/g)].map((m) => m[1]))].sort().join(',');
for (const k of allKeys) {
  if (!LOCALES.every((l) => maps[l].has(k))) continue;
  const variants = new Set(LOCALES.map((l) => ph(maps[l].get(k))));
  if (variants.size > 1) errors.push(`R2 ${k}: placeholder 不一致 → ${LOCALES.map((l) => `${l}[${ph(maps[l].get(k)) || '-'}]`).join(' ')}`);
}

// R3 整段中文残留
for (const l of NON_ZH) {
  const wl = new Set([...(ZH_IDENTICAL_WHITELIST['*'] || []), ...(ZH_IDENTICAL_WHITELIST[l] || [])]);
  for (const [k, v] of maps[l]) {
    if (!CJK.test(v) || v.length < 6) continue;
    if (maps['zh-CN'].get(k) !== v || wl.has(k)) continue;
    errors.push(`R3[${l}] ${k}: 与 zh-CN 完全相同的中文文案 → "${v}"`);
  }
}

// R4 命名空间级 enUS 掩盖
for (const l of ['ja-JP', 'ko-KR']) {
  const nsStat = new Map();
  for (const [k, v] of maps[l]) {
    if (!maps['en-US'].has(k) || !maps['en-US'].get(k)) continue;
    const ns = k.split('.').slice(0, 2).join('.');
    const s = nsStat.get(ns) || { total: 0, same: 0 };
    s.total += 1;
    if (maps['en-US'].get(k) === v) s.same += 1;
    nsStat.set(ns, s);
  }
  for (const [ns, s] of nsStat) {
    if (s.total >= 5 && s.same / s.total > 0.6 && !NS_INHERIT_WHITELIST.has(`${l}:${ns}`) && !NS_INHERIT_WHITELIST.has(`*:${ns}`)) {
      const item = `R4[${l}] ${ns}: ${s.same}/${s.total} 键与 en-US 相同（疑似未翻译继承）`;
      (strict ? errors : warnings).push(item);
    }
  }
}

const label = strict ? '严格' : '宽松';
if (errors.length) {
  console.error(`locale parity 检查失败（${label}）：${errors.length} 项`);
  errors.forEach((e) => console.error('  ' + e));
  process.exit(1);
}
if (warnings.length) {
  console.warn(`locale parity 警告（${label}）：${warnings.length} 项`);
  warnings.forEach((w) => console.warn('  ' + w));
}
console.log(`locale parity 检查通过（${label}）：四语言各 ${maps['zh-CN'].size} 键，0 键缺失，0 placeholder 不一致。`);
