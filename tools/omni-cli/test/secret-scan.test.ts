import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, it, before, after } from 'node:test';
import { tmpdir } from 'node:os';
import { findWorkspaceRoot } from '../src/workspace.js';

/**
 * secret-scan.mjs 编码与扫描范围回归测试（WP-015 FR02 / RF07）。
 *
 * 全部秘密为运行时生成的合成占位值，绝不含真实 dump/JWT 原文；
 * 断言方向为「漏洞必须先被抓到、修后负例必须被拒、既有 UTF8 行为必须保持」。
 */
const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const scanner = join(workspaceRoot, 'tools', 'omni-cli', 'scripts', 'secret-scan.mjs');

/** 合成假 JWT：结构匹配扫描正则，语义上明确为不可用占位。 */
const FAKE_JWT = [
  'eyJ' + 'U1lOVEhFVElDWFl6Tm90QVJlYWxUb2tlbg',
  'U1lOVEhFVElDTm90QVJlYWxTaWduYXR1cmVCbG9ja1BhZGRpbmcyMg',
  'U1lOVEhFVElDRmFrZVNpZ25hdHVyZUJsb2NrMDA',
].join('.');

let fixtureDir = '';

/** 运行扫描器，返回退出码与合并输出。 */
function run(args: string[]): { exitCode: number; out: string } {
  try {
    const stdout = execFileSync('node', [scanner, ...args], {
      encoding: 'utf8',
      maxBuffer: 64 * 1024 * 1024,
      timeout: 60_000,
    });
    return { exitCode: 0, out: stdout };
  } catch (e: unknown) {
    const err = e as { status?: number; stdout?: string; stderr?: string };
    return { exitCode: err.status ?? 1, out: (err.stdout ?? '') + (err.stderr ?? '') };
  }
}

/** 从输出中提取 SECRET_SCAN_RESULT 标记。 */
function resultOf(out: string): string {
  const m = /SECRET_SCAN_RESULT=(CLEAN|HITS\(\d+\)|ERROR)/.exec(out);
  return m?.[1] ?? 'NO_RESULT_LINE';
}

function utf16be(buf: Buffer): Buffer {
  const out = Buffer.alloc(buf.length);
  for (let i = 0; i + 1 < buf.length; i += 2) {
    out.writeUInt16BE(buf.readUInt16LE(i), i);
  }
  return out;
}

function write(name: string, buf: Buffer): string {
  const p = join(fixtureDir, name);
  writeFileSync(p, buf);
  return p;
}

before(() => {
  fixtureDir = mkdtempSync(join(tmpdir(), 'wp015-secret-scan-'));
});

after(() => {
  rmSync(fixtureDir, { recursive: true, force: true });
});

describe('secret-scan 编码解码策略', () => {
  it('正常非敏感 UTF-8 文本仍判 CLEAN（既有行为保持）', () => {
    const p = write('clean-utf8.txt', Buffer.from('just a normal line\nno secrets here\n', 'utf8'));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 0, r.out);
    assert.equal(resultOf(r.out), 'CLEAN');
  });

  it('UTF-8 含秘密判 HITS，且输出不回显秘密值', () => {
    const p = write('hit-utf8.txt', Buffer.from('token: ' + FAKE_JWT + '\n', 'utf8'));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
    assert.ok(!r.out.includes(FAKE_JWT), '扫描输出绝不回显匹配内容');
  });

  it('UTF-16LE + BOM 含秘密必须 HITS，不得再出现虚假 CLEAN（RF07 根因）', () => {
    const p = write('hit-utf16le-bom.txt', Buffer.concat([
      Buffer.from([0xff, 0xfe]),
      Buffer.from('token: ' + FAKE_JWT + '\n', 'utf16le'),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
    assert.match(r.out, /DECODED_AS_UTF16LE_BOM=1/);
  });

  it('UTF-16BE + BOM 含秘密必须 HITS', () => {
    const p = write('hit-utf16be-bom.txt', Buffer.concat([
      Buffer.from([0xfe, 0xff]),
      utf16be(Buffer.from('token: ' + FAKE_JWT + '\n', 'utf16le')),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
    assert.match(r.out, /DECODED_AS_UTF16BE_BOM=1/);
  });

  it('UTF-16LE 无 BOM 含秘密必须 HITS（按 NUL 分布判定编码）', () => {
    const p = write('hit-utf16le-nobom.txt', Buffer.from('token: ' + FAKE_JWT + '\n', 'utf16le'));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
    assert.match(r.out, /DECODED_AS_UTF16LE=1/);
  });

  it('UTF-8 + BOM 含秘密必须 HITS', () => {
    const p = write('hit-utf8-bom.txt', Buffer.concat([
      Buffer.from([0xef, 0xbb, 0xbf]),
      Buffer.from('token: ' + FAKE_JWT + '\n', 'utf8'),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
  });

  it('BOM 与奇数字节载荷矛盾 ⇒ ERROR（非零），不得 CLEAN', () => {
    const p = write('odd-utf16.txt', Buffer.concat([
      Buffer.from([0xff, 0xfe]),
      Buffer.from('token: ' + FAKE_JWT, 'utf16le'),
      Buffer.from([0x41]),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 2, r.out);
    assert.equal(resultOf(r.out), 'ERROR');
  });

  it('NUL 分布不合理的高二进制文本 ⇒ ERROR，不得 CLEAN', () => {
    const p = write('nul-garbage.bin.txt', Buffer.concat([
      Buffer.from([0x00, 0x01, 0x02, 0x03]),
      Buffer.alloc(200, 0x00),
      Buffer.from('trailing'),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 2, r.out);
    assert.equal(resultOf(r.out), 'ERROR');
  });

  it('含非法 UTF-8 字节序列且无法判定 UTF-16 ⇒ ERROR（ERROR 优于虚假 CLEAN）', () => {
    const p = write('invalid-utf8.txt', Buffer.concat([
      Buffer.from('prefix\n', 'utf8'),
      Buffer.from([0xc3, 0x28, 0xa0, 0x41, 0xff, 0xfe, 0x80]),
      Buffer.from('x= ' + FAKE_JWT + '\n', 'utf8'),
    ]));
    const r = run(['--file=' + p]);
    assert.ok(r.exitCode !== 0, '不可确定编码不得以 CLEAN 通过: ' + r.out);
    assert.ok(resultOf(r.out) === 'ERROR' || resultOf(r.out).startsWith('HITS'), r.out);
  });

  it('含二进制块的 UTF-8 dump 降级为 lossy 后仍完成扫描并命中（既有行为保持 + 显式披露）', () => {
    // 用足够大的干净正文稀释非法字节占比，复现真实 mysqldump（少量二进制块 + 大量文本）的形态
    const filler = Buffer.from('INSERT INTO `t` VALUES (1,\'plain row\');\n'.repeat(600), 'utf8');
    const p = write('dumpish.sql', Buffer.concat([
      filler,
      Buffer.from([0xff, 0xfe, 0x80, 0xf5, 0xc3, 0x28]),
      Buffer.from(')' + FAKE_JWT + ';\n', 'utf8'),
    ]));
    const r = run(['--file=' + p]);
    assert.equal(r.exitCode, 1, r.out);
    assert.match(resultOf(r.out), /^HITS/);
    assert.match(r.out, /DECODE_WARNING/);
    assert.match(r.out, /DECODED_AS_UTF8_LOSSY_FALLBACK=1/);
  });
});

describe('secret-scan 归档扫描范围', () => {
  it('ZIP 内 UTF-16LE 文本条目必须与 --file 共用解码路径并命中', () => {
    const srcDir = join(fixtureDir, 'zip-src');
    mkdirSync(srcDir, { recursive: true });
    writeFileSync(join(srcDir, 'payload-utf16.sql'), Buffer.concat([
      Buffer.from([0xff, 0xfe]),
      Buffer.from('INSERT INTO `x` VALUES (' + FAKE_JWT + ');', 'utf16le'),
    ]));
    const zipPath = join(fixtureDir, 'utf16-entry.zip');
    if (process.platform === 'win32') {
      execFileSync('powershell', ['-NoProfile', '-Command',
        `$ErrorActionPreference='Stop'; Compress-Archive -LiteralPath '${srcDir.replaceAll("'", "''")}' -DestinationPath '${zipPath.replaceAll("'", "''")}' -Force`]);
    } else {
      // 容器使用项目必需 JDK 的 ZIP 实现，不依赖 Windows PowerShell。
      execFileSync('jar', ['--create', '--file', zipPath, '-C', srcDir, '.']);
    }
    rmSync(srcDir, { recursive: true, force: true });

    for (const mode of ['--archive=', '--file=']) {
      const r = run([mode + zipPath]);
      assert.equal(r.exitCode, 1, mode + ' 应命中: ' + r.out);
      assert.match(resultOf(r.out), /^HITS/);
      assert.match(r.out, /DECODED_AS_UTF16LE_BOM=1/);
      assert.ok(!r.out.includes(FAKE_JWT), '归档扫描同样不得回显内容');
    }
  });

  it('损坏 ZIP 保持 fail-closed 非零退出', () => {
    const good = Buffer.concat([
      Buffer.from([0x50, 0x4b, 0x03, 0x04]),
      Buffer.alloc(400, 0x41),
    ]);
    const p = join(fixtureDir, 'corrupt.zip');
    writeFileSync(p, good.subarray(0, 60));
    const r = run(['--archive=' + p]);
    assert.notEqual(r.exitCode, 0, '损坏归档不得 CLEAN: ' + r.out);
  });
});
