import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import test from 'node:test';
import { stringify } from 'yaml';
import { findWorkspaceRoot } from '../src/workspace.js';

const root = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const hash = (value: string): string => createHash('sha256').update(value).digest('hex');

test('human review requires all locales and both current digests; content-only is distinct', () => {
  const fixture = mkdtempSync(resolve(tmpdir(), 'omni-i18n-review-'));
  try {
    mkdirSync(resolve(fixture, 'docs'));
    const source = '# 示例\n\n当前契约。\n';
    const translation = '# Example\n\nCurrent contract.\n';
    writeFileSync(resolve(fixture, 'docs/source.md'), source);
    const translations: Record<string, Record<string, unknown>> = {};
    for (const [locale, suffix] of [['en-US', 'en'], ['ja-JP', 'jp'], ['ko-KR', 'kr']]) {
      writeFileSync(resolve(fixture, `docs/source.${suffix}.md`), translation);
      translations[locale!] = { path: `docs/source.${suffix}.md`, status: 'synchronized', review_type: 'HUMAN_REVIEW', reviewer: 'Human fixture reviewer', reviewed_at: '2026-10-09', reviewed_source_sha256: hash(source), translation_sha256: hash(translation) };
    }
    const document = { id: 'example', source: 'docs/source.md', source_sha256: hash(source), translations };
    const save = (): void => { writeFileSync(resolve(fixture, 'docs/docs-manifest.yaml'), stringify({ release_policy: { require_human_review: true, require_all_translations: true }, documents: [document] })); };
    const check = (scope = 'i18n'): void => {
      execFileSync(process.execPath, [resolve(root, 'tools/omni-cli/scripts/docs-quality.mjs'), `--scope=${scope}`], {
        env: { ...process.env, WP002_TEST_ROOT: fixture }, stdio: 'pipe',
      });
    };
    save(); check();
    translations['en-US']!.reviewer = 'Codex'; save();
    assert.throws(() => check(), /Command failed/);
    check('i18n-content');
    translations['en-US']!.reviewer = 'Human fixture reviewer';
    writeFileSync(resolve(fixture, 'docs/source.en.md'), translation + 'Changed.\n');
    save(); assert.throws(() => check(), /Command failed/);
    writeFileSync(resolve(fixture, 'docs/source.en.md'), translation);
    const japanese = translations['ja-JP']; delete translations['ja-JP'];
    save(); assert.throws(() => check('i18n-content'), /Command failed/);
    translations['ja-JP'] = japanese!;
    translations['en-US']!.status = 'present-unverified'; save();
    assert.throws(() => check(), /Command failed/);
    check('i18n-content');
    assert.equal(readFileSync(resolve(fixture, 'docs/source.md'), 'utf8'), source);
  } finally { rmSync(fixture, { recursive: true, force: true }); }
});
