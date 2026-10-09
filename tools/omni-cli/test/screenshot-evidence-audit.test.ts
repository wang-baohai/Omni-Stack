import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { deflateSync } from 'node:zlib';
import { mkdirSync, mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { tmpdir } from 'node:os';
import { describe, it, afterEach } from 'node:test';
import { resolve } from 'node:path';
import { findWorkspaceRoot } from '../src/workspace.js';

/**
 * screenshot-evidence-audit.mjs 的反例测试（WP-014 新增）。
 *
 * 目的：证明这道门真的会拒绝假绿，而不是只镜像当前实现。
 * 覆盖：缺字段、重复 ID、缺失文件、SHA 失配、假视口、同字节图冒充不同状态/语言、
 * CLI receipt 充当 Web 格子、DEBUG 申请正式、工程检查者冒填独立方、
 * 正式模式缺独立决定、决定绑定过期 SHA、路径穿越、覆盖 strict 反查。
 */

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const AUDIT = join(workspaceRoot, 'tools', 'omni-cli', 'scripts', 'screenshot-evidence-audit.mjs');
const REAL_TEST_SOURCE = 'omni-frontend/e2e-docs/flows/admin.flows.spec.ts';

let currentDir: string | null = null;

/** CRC32（构造合法 PNG 用）。 */
function crc32(buf: Buffer): number {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf.byteLength > i ? buf[i] ?? 0 : 0;
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

/** 生成一张真实合法的 w×h 灰度 PNG（不透明字节，可被 IHDR 解析）。 */
function makePng(width: number, height: number): Buffer {
  const chunk = (type: string, data: Buffer): Buffer => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const td = Buffer.alloc(4);
    td.write(type, 0, 'ascii');
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(Buffer.concat([td, data])), 0);
    return Buffer.concat([len, td, data, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 0; // color type gray
  const row = Buffer.alloc(width + 1); // 每行首字节为 filter type 0
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

/** 建立隔离目录并写入图片，返回 imagesRoot。 */
function setup(images: Record<string, Buffer>): string {
  const dir = mkdtempSync(join(tmpdir(), 'wp014-audit-'));
  currentDir = dir;
  mkdirSync(join(dir, 'docs', 'images', 'zh-CN'), { recursive: true });
  for (const [rel, buf] of Object.entries(images)) {
    const abs = join(dir, rel);
    mkdirSync(join(abs, '..'), { recursive: true });
    writeFileSync(abs, buf);
  }
  return dir;
}

/** 写候选 jsonl 并执行审计。 */
function run(dir: string, rows: object[], extraArgs: string[] = []): { exitCode: number; stdout: string; stderr: string } {
  const file = join(dir, 'candidate.jsonl');
  writeFileSync(file, rows.map((r) => JSON.stringify(r)).join('\n') + '\n', 'utf8');
  try {
    const stdout = execFileSync('node', [AUDIT, '--candidate', file, '--images-root', dir, ...extraArgs], {
      encoding: 'utf8',
      timeout: 30_000,
    });
    return { exitCode: 0, stdout, stderr: '' };
  } catch (e: unknown) {
    const err = e as { status?: number; stdout?: string; stderr?: string };
    return { exitCode: err.status ?? 1, stdout: err.stdout ?? '', stderr: err.stderr ?? '' };
  }
}

/** 执行正式模式审计。 */
function runOfficial(dir: string, rows: object[], extraArgs: string[] = []): { exitCode: number; stdout: string; stderr: string } {
  const file = join(dir, 'official.jsonl');
  writeFileSync(file, rows.map((r) => JSON.stringify(r)).join('\n') + '\n', 'utf8');
  try {
    const stdout = execFileSync('node', [AUDIT, '--official', file, '--images-root', dir, ...extraArgs], {
      encoding: 'utf8',
      timeout: 30_000,
    });
    return { exitCode: 0, stdout, stderr: '' };
  } catch (e: unknown) {
    const err = e as { status?: number; stdout?: string; stderr: string } & { stderr?: string };
    return { exitCode: err.status ?? 1, stdout: err.stdout ?? '', stderr: err.stderr ?? '' };
  }
}

afterEach(() => {
  if (currentDir) {
    rmSync(currentDir, { recursive: true, force: true });
    currentDir = null;
  }
});

/** 一条完整合法的候选行（1440x900 @DPR1）。 */
function goodRow(over: Record<string, unknown> = {}): Record<string, unknown> {
  const img = String(over.image ?? 'docs/images/zh-CN/a.png');
  const buf = makePng(1440, 900);
  return {
    case_id: 'WP014-demo-flow-entry',
    module: 'demo',
    flow: 'flow',
    state: 'entry',
    locale: 'zh-CN',
    role: 'admin',
    viewport: '1440x900',
    dpr: 1,
    png_size: '1440x900',
    evidence_layer: 'REAL_RUNTIME_UI',
    captured_at_utc: '2026-10-03T09:30:00Z',
    provider: 'QoderIDE/1.32.2 builtin Electron webview',
    session: 'pageIdx=0',
    image: img,
    sha256: createHash('sha256').update(buf).digest('hex'),
    test_source: REAL_TEST_SOURCE,
    masks: [],
    engineering_review: { actor_kind: 'Qoder', reviewer: 'WP014-controller', reviewed_at: '2026-10-03T09:31:00Z', opinion: '已打开原图核对页面标题与状态列' },
    ...over,
  };
}

describe('screenshot-evidence-audit：合法候选应通过', () => {
  it('完整绑定来源/视口/SHA/工程检查的候选行 exit 0', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow()]);
    assert.equal(r.exitCode, 0, r.stderr || r.stdout);
    assert.match(r.stdout, /审计通过/);
  });
});

describe('screenshot-evidence-audit：反例必须失败（防假绿）', () => {
  it('缺少必需元数据字段 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const row = goodRow();
    delete row.provider;
    const r = run(dir, [row]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /缺少必需元数据字段：provider/);
  });

  it('重复 case_id → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow(), goodRow()]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /重复 case_id/);
  });

  it('图片文件不存在 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ image: 'docs/images/zh-CN/ghost.png', sha256: 'x'.repeat(64) })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /图片文件不存在/);
  });

  it('登记 SHA 与字节不符（源/SHA 失配）→ 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ sha256: '0'.repeat(64) })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /SHA 失配/);
  });

  it('假视口（PNG 像素 ≠ 声明 CSS viewport×DPR）→ 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(2159, 1321) });
    const r = run(dir, [goodRow({ image: 'docs/images/zh-CN/a.png' })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /CSS 视口 .* 与 PNG 2159x1321 不一致/);
  });

  it('同字节图片冒充不同状态（列表复制成详情）→ 失败', () => {
    const png = makePng(1440, 900);
    const dir = setup({ 'docs/images/zh-CN/list.png': png });
    const sha = createHash('sha256').update(png).digest('hex');
    const r = run(dir, [
      goodRow({ case_id: 'c-list', state: 'list', image: 'docs/images/zh-CN/list.png', sha256: sha }),
      goodRow({ case_id: 'c-detail', state: 'detail', image: 'docs/images/zh-CN/list.png', sha256: sha }),
    ]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /同一字节图片被用于|图片路径/);
  });

  it('同字节图片跨语言复用（未真正按语言重拍）→ 失败', () => {
    const png = makePng(1440, 900);
    const dir = setup({ 'docs/images/ja-JP/x.png': png });
    const sha = createHash('sha256').update(png).digest('hex');
    const r = run(dir, [
      goodRow({ case_id: 'c-ja', locale: 'ja-JP', image: 'docs/images/ja-JP/x.png', sha256: sha }),
      goodRow({ case_id: 'c-ko', locale: 'ko-KR', image: 'docs/images/ja-JP/x.png', sha256: sha }),
    ]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /同一字节图片被用于/);
  });

  it('CLI receipt 充当 Web 格子 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ evidence_layer: 'CLI_TEXT' })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /CLI_TEXT receipt 不得充当 Web 格子/);
  });

  it('test_source 不存在 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ test_source: 'omni-frontend/e2e-docs/flows/nope.spec.ts' })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /test_source 不存在/);
  });

  it('masks 未显式声明 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const row = goodRow();
    delete row.masks;
    const r = run(dir, [row]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /masks 必须为显式数组/);
  });

  it('工程检查者冒填独立方（actor_kind=Codex）→ 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ engineering_review: { actor_kind: 'Codex', reviewer: 'Codex', reviewed_at: '2026-10-03T09:31:00Z', opinion: 'x' } })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /actor_kind 非法或缺失|不得把独立审图方/);
  });

  it('路径穿越 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow({ image: '../../../Windows/win.ini' })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /路径越界|解析后逃出/);
  });

  it('候选模式允许无独立决定（不因待审而失败）', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = run(dir, [goodRow()]);
    assert.equal(r.exitCode, 0, r.stderr || r.stdout);
  });

  it('正式模式缺独立决定 → 失败（未批准不得 official）', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = runOfficial(dir, [goodRow()]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /缺少独立决定/);
  });

  it('正式模式决定 reviewer 非允许独立方 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = runOfficial(dir, [goodRow({ decision: { reviewer: 'Qoder', value: 'APPROVED', decided_at: '2026-10-03T10:00:00Z' } })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /不在允许独立方/);
  });

  it('正式模式 DEBUG 图 → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = runOfficial(dir, [goodRow({
      evidence_layer: 'DEBUG',
      decision: { reviewer: 'Codex', value: 'APPROVED', decided_at: '2026-10-03T10:00:00Z' },
    })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /DEBUG 图不得申请正式发布/);
  });

  it('正式模式决定绑定的图片 SHA 已变更 → 失败（决定失效）', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const r = runOfficial(dir, [goodRow({
      decision: { reviewer: 'Codex', value: 'APPROVED', decided_at: '2026-10-03T10:00:00Z', image_sha256: 'f'.repeat(64) },
    })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /决定已失效|图片 SHA/);
  });

  it('strict flows：required flow 无登记 case → 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': makePng(1440, 900) });
    const cov = join(dir, 'coverage.yaml');
    writeFileSync(cov, ['modules:', '  - id: demo', '    status: partial', '    required_flows: [flow, missing-flow]'].join('\n'), 'utf8');
    const r = run(dir, [goodRow()], ['--coverage', cov, '--strict-flows']);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /required flow 无登记 case/);
  });

  it('损坏 PNG（magic 不符）→ 失败', () => {
    const dir = setup({ 'docs/images/zh-CN/a.png': Buffer.from('not a png at all') });
    const r = run(dir, [goodRow({ sha256: createHash('sha256').update(Buffer.from('not a png at all')).digest('hex') })]);
    assert.equal(r.exitCode, 1);
    assert.match(r.stderr, /PNG 完整性失败/);
  });
});
