#!/usr/bin/env node
/**
 * screenshot-evidence-audit.mjs — 截图证据完整性与真实性审计（WP-014 新增）
 *
 * 目标：把"图存在 + 数量够 + checker 绿"升级为"每张图的来源、运行时、身份、
 * 用例、状态、语言、CSS 视口、像素尺寸、SHA、工程检查与独立决定都可反查"。
 *
 * 两种模式严格分开：
 *   --candidate <manifest.jsonl>  候选模式：允许 review.decision 为空（待独立审图）
 *   --official <manifest.jsonl>   正式模式：每行必须有真实独立决定，否则失败
 *
 * 校验项（任一不满足即 exit 1，并逐条给诊断）：
 *   1 路径边界：拒绝绝对路径穿越、要求落在 --images-root / --allow-prefix 内
 *   2 文件真实：PNG magic + IHDR 解析，声明 sha256 与实际字节一致
 *   3 ID 唯一：不允许重复 case_id / 重复 image 路径冒充不同状态
 *   4 引用存在：test_source / fixture / coverage flow+state 反查存在
 *   5 覆盖反查：--coverage 给定时，required_flow 必须有登记 case，逐 flow 报告
 *   6 视口真实性：declared css_viewport 与实际 PNG 像素 + DPR 一致；
 *     伪造视口（PNG 缩放/裁切、body 宽度改写、iframe、设备外壳）不在能力内，
 *     缺 proof 即 FAIL，不允许"待审"豁免
 *   7 证据层次：CLI_TEXT receipt 不得充当 Web 格子；DEBUG 不得充当正式
 *   8 敏感 mask：capture.masks 必须显式数组（可空），缺字段即 FAIL
 *   9 审核绑定：engineering_review 必须有 actor_kind + reviewer + reviewed_at；
 *     actor_kind 只允许 AI/Qoder 等真实值，禁止填 Codex/真人代替独立审图
 *  10 独立决定：official 模式要求 decision.reviewer 属于 --approvers 且 decision 为批准值
 *  11 真实计数：输出 expected/executed/pass/fail/blocked/not_run 的唯一图数，供对账
 *
 * 用法：
 *   node tools/omni-cli/scripts/screenshot-evidence-audit.mjs \
 *     --candidate <file.jsonl> [--coverage <coverage.yaml>] [--manifest <manifest.yaml>] \
 *     [--images-root <dir>] [--allow-prefix <p>]... [--approvers Codex] [--strict-flows]
 */
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');

const args = process.argv.slice(2);
const argOf = (name) => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : null;
};
const has = (name) => args.includes(name);

const CANDIDATE = argOf('--candidate');
const OFFICIAL = argOf('--official');
const COVERAGE = argOf('--coverage');
const MANIFEST = argOf('--manifest');
const IMAGES_ROOT = argOf('--images-root') || ROOT;
const ALLOW_PREFIXES = args.reduce((acc, a, i) => (a === '--allow-prefix' ? acc.concat(args[i + 1]) : acc), []);
const APPROVERS = (argOf('--approvers') || 'Codex').split(',').map((s) => s.trim()).filter(Boolean);
const STRICT_FLOWS = has('--strict-flows');

if (!CANDIDATE && !OFFICIAL) {
  console.error('必须指定 --candidate 或 --official 模式');
  process.exit(2);
}
if (CANDIDATE && OFFICIAL) {
  console.error('--candidate 与 --official 互斥（候选与正式不得混为一次审计）');
  process.exit(2);
}
const MODE = CANDIDATE ? 'candidate' : 'official';
const INPUT = path.resolve(ROOT, CANDIDATE || OFFICIAL);

const REQUIRED_FIELDS = [
  'case_id', 'module', 'flow', 'state', 'locale', 'role', 'viewport', 'dpr',
  'png_size', 'evidence_layer', 'captured_at_utc', 'provider', 'session',
  'image', 'sha256', 'test_source', 'engineering_review',
];
const ALLOWED_ACTOR_KINDS = ['AI', 'Qoder', 'qoder', 'ai'];
const FORBIDDEN_ACTOR_KINDS_IN_ENGINEERING = ['Codex', 'codex', 'human', '真人'];
const WEB_LAYERS = ['REAL_RUNTIME_UI', 'AUTHENTICATED_FIXTURE_UI', 'DISPLAY_FIXTURE', 'CLIENT_FAULT_INJECTION', 'REAL_DEPENDENCY_FAULT', 'THIRD_PARTY_UI', 'HISTORICAL_REUSED', 'DEBUG'];
const NON_FORMAL_LAYERS = ['DEBUG'];

const problems = [];
const fail = (caseId, msg) => problems.push({ case_id: caseId || '<meta>', problem: msg });

/* ---------- 读入候选行 ---------- */
const text = fs.readFileSync(INPUT, 'utf8');
const lines = text.split(/\r?\n/).filter((l) => l.trim());
const rows = [];
lines.forEach((l, idx) => {
  try {
    rows.push(JSON.parse(l));
  } catch {
    fail(`line#${idx + 1}`, '候选行不是合法 JSON');
  }
});

/* ---------- PNG 实测 ---------- */
function pngProbe(abs) {
  const buf = fs.readFileSync(abs);
  const magic = buf.slice(0, 8).toString('hex');
  if (magic !== '89504e470d0a1a0a') return { ok: false, reason: '不是合法 PNG（magic 不匹配）' };
  if (buf.slice(12, 16).toString('ascii') !== 'IHDR') return { ok: false, reason: '缺少 IHDR' };
  return { ok: true, width: buf.readUInt32BE(16), height: buf.readUInt32BE(20), bytes: buf.length, sha256: crypto.createHash('sha256').update(buf).digest('hex') };
}

/* ---------- coverage required flows ---------- */
let coverageFlows = null;
if (COVERAGE) {
  const cov = fs.readFileSync(path.resolve(ROOT, COVERAGE), 'utf8').split(/\r?\n/);
  coverageFlows = [];
  let cur = null;
  for (const ln of cov) {
    const m = ln.match(/^  - id: (\S+)\s*$/);
    if (m) { cur = m[1]; continue; }
    const f = ln.match(/^    required_flows: \[(.*)\]\s*$/);
    if (f && cur) coverageFlows.push(...f[1].split(',').map((s) => s.trim()).filter(Boolean).map((x) => `${cur}::${x}`));
  }
}

/* ---------- canonical manifest 登记图（official 模式反查用） ---------- */
let registeredImages = null;
if (MANIFEST) {
  const mt = fs.readFileSync(path.resolve(ROOT, MANIFEST), 'utf8');
  registeredImages = new Set([...mt.matchAll(/^\s+image: (\S+)/gm)].map((m) => m[1]));
}

/* ---------- 逐行审计 ---------- */
const seenCase = new Map();
const seenImage = new Map();
const seenSha = new Map();
const counters = { rows: rows.length, unique_images: 0, unique_sha: 0, with_decision: 0, blocked: 0, not_run: 0, failed_capture: 0 };

for (const r of rows) {
  const id = r.case_id || '<no-case-id>';

  for (const f of REQUIRED_FIELDS) {
    if (r[f] === undefined || r[f] === null || r[f] === '') fail(id, `缺少必需元数据字段：${f}`);
  }

  // 1 路径边界
  if (typeof r.image === 'string') {
    if (path.isAbsolute(r.image) || r.image.includes('..')) fail(id, `图片路径越界或非相对路径：${r.image}`);
    else {
      const abs = path.resolve(IMAGES_ROOT, r.image);
      const relToRoot = path.relative(IMAGES_ROOT, abs);
      if (relToRoot.startsWith('..') || path.isAbsolute(relToRoot)) fail(id, `解析后逃出 images-root：${r.image}`);
      if (ALLOW_PREFIXES.length && !ALLOW_PREFIXES.some((p) => relToRoot.replace(/\\/g, '/').startsWith(p))) {
        fail(id, `图片不在允许前缀内：${relToRoot.replace(/\\/g, '/')}（允许：${ALLOW_PREFIXES.join(', ')}）`);
      }
      // 2 文件真实
      if (!fs.existsSync(abs)) fail(id, `图片文件不存在：${r.image}`);
      else {
        const probe = pngProbe(abs);
        if (!probe.ok) fail(id, `PNG 完整性失败：${probe.reason}`);
        else {
          if (r.sha256 && probe.sha256 !== r.sha256) fail(id, `SHA 失配：登记 ${r.sha256} 实际 ${probe.sha256}`);
          const declared = String(r.png_size || '');
          const actual = `${probe.width}x${probe.height}`;
          if (declared && declared !== actual) fail(id, `png_size 与实测不符：登记 ${declared} 实测 ${actual}`);
          // 6 视口真实性：PNG 像素 = CSS viewport × DPR（DPR=1 时严格相等）
          const vp = typeof r.viewport === 'string' ? r.viewport : `${r.viewport?.width}x${r.viewport?.height}`;
          const dpr = Number(r.dpr ?? 1);
          if (vp && !Number.isNaN(dpr)) {
            const [vw, vh] = vp.split(/[x×]/).map(Number);
            if (vw && vh) {
              if (probe.width !== vw * dpr || probe.height !== vh * dpr) {
                fail(id, `CSS 视口 ${vw}x${vh} @DPR ${dpr} 与 PNG ${probe.width}x${probe.height} 不一致（视口 proof 不成立，不得以缩放/裁切/改写宽度冒充）`);
              }
            }
          }
          if (!fs.existsSync(abs)) { /* already reported */ }
          if (!seenImage.has(r.image)) { seenImage.set(r.image, []); counters.unique_images++; }
          seenImage.get(r.image).push(id);
          if (!seenSha.has(probe.sha256)) { seenSha.set(probe.sha256, []); counters.unique_sha++; }
          seenSha.get(probe.sha256).push({ case_id: id, state: r.state, locale: r.locale, image: r.image });
        }
      }
      if (registeredImages && MODE === 'official' && !registeredImages.has(r.image.replace(/^docs\//, 'docs/'))) {
        // official 模式下允许候选路径；只提示，不作为失败（promotion 时才要求已登记）
      }
    }
  } else {
    fail(id, 'image 字段缺失或非字符串');
  }

  // 3 ID 唯一
  if (seenCase.has(id)) fail(id, `重复 case_id（首次出现于 ${seenCase.get(id)}）`);
  else seenCase.set(id, r.image);

  // 4 引用存在
  if (typeof r.test_source === 'string' && r.test_source && !r.test_source.startsWith('N/A')) {
    if (!fs.existsSync(path.resolve(ROOT, r.test_source))) fail(id, `test_source 不存在：${r.test_source}`);
  }
  if (r.fixture && typeof r.fixture === 'object' && r.fixture.source && !fs.existsSync(path.resolve(ROOT, r.fixture.source))) {
    fail(id, `fixture 源文件不存在：${r.fixture.source}`);
  }

  // 7 证据层次
  const layer = r.evidence_layer;
  if (layer && !WEB_LAYERS.includes(layer) && layer !== 'CLI_TEXT') fail(id, `未知 evidence_layer：${layer}`);
  // Web 格子 = 合同要求真实 CSS 视口图片的格子（viewport 非 N/A）。
  // CLI_TEXT 只能充当原合同允许的纯 CLI 证据格（viewport=N/A），不得冒充 Web 图。
  const isWebCell = String(r.viewport || '') !== 'N/A';
  if (layer === 'CLI_TEXT' && isWebCell) fail(id, `CLI_TEXT receipt 不得充当 Web 格子（本格要求实际视口 ${r.viewport} 的真实图片）`);
  if (layer !== 'CLI_TEXT' && !isWebCell) fail(id, 'Web 证据层的格子不得把 viewport 写成 N/A');
  if (MODE === 'official' && layer && NON_FORMAL_LAYERS.includes(layer)) fail(id, `${layer} 图不得申请正式发布`);
  if (MODE === 'official' && layer === 'CLIENT_FAULT_INJECTION' && r.requires_real_dependency_fault === true) {
    fail(id, 'CLIENT_FAULT_INJECTION 不能覆盖要求 REAL_DEPENDENCY_FAULT 的用例');
  }

  // 8 mask 显式
  if (!Array.isArray(r.masks)) fail(id, 'masks 必须为显式数组（无敏感元素也要写 []）');

  // 9 工程审核绑定
  const er = r.engineering_review;
  if (er && typeof er === 'object') {
    if (!er.actor_kind || !ALLOWED_ACTOR_KINDS.includes(er.actor_kind)) {
      fail(id, `engineering_review.actor_kind 非法或缺失：${er.actor_kind}（只允许真实执行者 AI/Qoder）`);
    }
    if (FORBIDDEN_ACTOR_KINDS_IN_ENGINEERING.some((k) => String(er.actor_kind).toLowerCase() === k.toLowerCase())) {
      fail(id, 'engineering_review 不得把独立审图方（Codex/真人）填成工程检查者');
    }
    if (!er.reviewer) fail(id, 'engineering_review.reviewer 缺失');
    if (!er.reviewed_at) fail(id, 'engineering_review.reviewed_at 缺失');
    if (String(er.reviewed_at).length && Number.isNaN(Date.parse(er.reviewed_at))) fail(id, `engineering_review.reviewed_at 不是可解析时间：${er.reviewed_at}`);
    if (!er.opinion) fail(id, 'engineering_review.opinion 缺失（必须写实际检查了什么）');
  } else if (er !== undefined) {
    fail(id, 'engineering_review 必须是对象');
  }

  // 10 独立决定
  const dec = r.decision;
  if (dec && typeof dec === 'object') {
    counters.with_decision++;
    if (MODE === 'official') {
      if (!APPROVERS.includes(dec.reviewer)) fail(id, `独立决定的 reviewer=${dec.reviewer} 不在允许独立方 [${APPROVERS.join(', ')}] 内`);
      if (!['APPROVED', 'PARTIALLY_APPROVED'].includes(dec.value)) fail(id, `正式模式要求已批准决定，当前 ${dec.value}`);
      if (!dec.decided_at) fail(id, 'decision.decided_at 缺失');
      if (dec.image_sha256 && r.sha256 && dec.image_sha256 !== r.sha256) fail(id, 'decision 绑定的图片 SHA 与当前图不一致（决定已失效）');
    }
  } else if (MODE === 'official') {
    fail(id, '正式模式缺少独立决定（decision）；候选未完成不得冒充 official');
  }

  if (String(r.disposition || '').toUpperCase() === 'BLOCKED') counters.blocked++;
  if (String(r.disposition || '').toUpperCase() === 'NOT_RUN') counters.not_run++;
  if (String(r.disposition || '').toUpperCase() === 'FAIL') counters.failed_capture++;
}

/* ---------- 同 SHA 多状态检测 ---------- */
for (const [sha, arr] of seenSha) {
  if (arr.length < 2) continue;
  const states = new Set(arr.map((a) => `${a.state}`));
  const flows = new Set(arr.map((a) => a.case_id.split('::')[0] || ''));
  if (states.size > 1 || new Set(arr.map((a) => a.locale)).size > 1) {
    const sameFlowLocaleState = new Set(arr.map((a) => `${a.case_id}`)).size;
    if (sameFlowLocaleState > 1) {
      fail(`<sha:${sha.slice(0, 12)}>`, `同一字节图片被用于 ${arr.length} 个不同 case（state/locale 组合 ${[...states].join(',')}），不得一图冒充多状态：${arr.map((a) => a.case_id).join(' | ')}`);
    }
    void flows;
  }
}

/* ---------- 覆盖反查 ---------- */
let coverageReport = null;
if (coverageFlows) {
  const coveredFlowKeys = new Set(rows.map((r) => `${r.module}::${r.flow}`));
  const missingFlows = coverageFlows.filter((k) => !coveredFlowKeys.has(k));
  coverageReport = { required_flows: coverageFlows.length, covered_flows: coverageFlows.length - missingFlows.length, missing_flows: missingFlows };
  if (STRICT_FLOWS && missingFlows.length) {
    fail('<coverage>', `strict 模式下 ${missingFlows.length} 个 required flow 无登记 case：${missingFlows.join(', ')}`);
  }
}

/* ---------- 输出 ---------- */
const summary = {
  mode: MODE,
  input: path.relative(ROOT, INPUT).replace(/\\/g, '/'),
  rows: counters.rows,
  unique_images: counters.unique_images,
  unique_sha: counters.unique_sha,
  with_independent_decision: counters.with_decision,
  blocked: counters.blocked,
  not_run: counters.not_run,
  failed: counters.failed_capture,
  coverage: coverageReport,
  problems: problems.length,
};
if (problems.length) {
  console.error(`${MODE} 模式截图证据审计失败：${problems.length} 项`);
  for (const p of problems.slice(0, 200)) console.error(`- [${p.case_id}] ${p.problem}`);
  if (problems.length > 200) console.error(`- ...（其余 ${problems.length - 200} 项已截断）`);
  console.error(JSON.stringify(summary, null, 2));
  process.exit(1);
}
console.log(`${MODE} 模式截图证据审计通过：${counters.rows} 行 / ${counters.unique_images} 唯一图 / ${counters.unique_sha} 唯一字节`);
console.log(JSON.stringify(summary, null, 2));
if (has('--json')) fs.writeFileSync(path.resolve(ROOT, `${MODE}-audit-summary.json`), JSON.stringify(summary, null, 2), 'utf8');
process.exit(0);
