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

test('authorized AI review remains a strict evidence gate and cannot impersonate human review', () => {
  const fixture = mkdtempSync(resolve(tmpdir(), 'omni-ai-review-'));
  try {
    for (const path of ['docs/adr', 'docs/reference/reviews']) mkdirSync(resolve(fixture, path), { recursive: true });
    const source = '# 示例\n\n租户隔离和失败恢复。\n';
    const translated = '# Example\n\nTenant isolation and failure recovery.\n';
    const decision = '# Owner authorization\n\nExplicit AI semantic review.\n';
    const decisionPath = 'docs/adr/0003-review.md';
    const evidencePath = 'docs/reference/reviews/example.json';
    writeFileSync(resolve(fixture, 'docs/source.md'), source);
    writeFileSync(resolve(fixture, decisionPath), decision);
    const translations: Record<string, Record<string, unknown>> = {};
    const records: Record<string, Record<string, unknown>> = {};
    for (const [locale, suffix] of [['en-US', 'en'], ['ja-JP', 'jp'], ['ko-KR', 'kr']]) {
      const path = 'docs/source.' + suffix + '.md';
      writeFileSync(resolve(fixture, path), translated);
      translations[locale!] = { path, status: 'synchronized', review_type: 'AI_REVIEW', reviewer: 'Codex',
        reviewed_at: '2026-10-13 11:00:00', reviewed_source_sha256: hash(source), translation_sha256: hash(translated),
        review_evidence: evidencePath };
      records[locale!] = { path, reviewer: 'Codex', reviewed_at: '2026-10-13 11:00:00',
        result: 'approved', translation_sha256: hash(translated), findings: [], checks: {
          coverage_and_meaning: 'All sections and recovery instructions agree.',
          technical_literals: 'Commands, API paths and permission codes agree.',
          security_and_failure_semantics: 'Tenant boundaries and refusal results agree.',
          language_and_terminology: 'Target language and domain terminology reviewed.',
          images_and_links: 'Image references and repository links are present.',
        } };
    }
    const evidence = { review_type: 'AI_REVIEW', document_id: 'example', source: 'docs/source.md',
      source_sha256: hash(source), authorization_sha256: hash(decision), translations: records };
    const document = { id: 'example', source: 'docs/source.md', source_sha256: hash(source), translations };
    const authorization = { reference: decisionPath, reference_sha256: hash(decision),
      approved_by: 'project-owner', approved_at: '2026-10-13', reviewer: 'Codex' };
    const policy: Record<string, unknown> = { require_all_translations: true, require_human_review: false,
      ai_review_authorization: authorization };
    const save = (): void => {
      const raw = JSON.stringify(evidence, null, 2) + '\n';
      writeFileSync(resolve(fixture, evidencePath), raw);
      for (const value of Object.values(translations)) value.review_evidence_sha256 = hash(raw);
      writeFileSync(resolve(fixture, 'docs/docs-manifest.yaml'), stringify({ release_policy: policy, documents: [document] }));
    };
    const check = (): void => {
      execFileSync(process.execPath, [resolve(root, 'tools/omni-cli/scripts/docs-quality.mjs'), '--scope=i18n'],
        { env: { ...process.env, WP002_TEST_ROOT: fixture }, stdio: 'pipe' });
    };
    save(); check();
    delete policy.ai_review_authorization; save();
    assert.throws(() => check(), /Command failed/);
    policy.ai_review_authorization = authorization; save();
    translations['en-US']!.review_type = 'HUMAN_REVIEW'; save();
    assert.throws(() => check(), /Command failed/);
    translations['en-US']!.review_type = 'AI_REVIEW'; save();
    writeFileSync(resolve(fixture, decisionPath), decision + 'Changed.\n');
    assert.throws(() => check(), /Command failed/);
    writeFileSync(resolve(fixture, decisionPath), decision);
    writeFileSync(resolve(fixture, evidencePath), '{}\n');
    assert.throws(() => check(), /Command failed/);
    save(); evidence.document_id = 'different'; save();
    assert.throws(() => check(), /Command failed/);
    evidence.document_id = 'example';
    const record = records['en-US']!;
    record.findings = [{ status: 'open', description: 'Unresolved tenant boundary error.' }]; save();
    assert.throws(() => check(), /Command failed/);
    record.findings = []; save();
    writeFileSync(resolve(fixture, 'docs/source.en.md'), translated + 'Changed.\n');
    assert.throws(() => check(), /Command failed/);
    writeFileSync(resolve(fixture, 'docs/source.en.md'), translated); save(); check();
    assert.equal(readFileSync(resolve(fixture, 'docs/source.md'), 'utf8'), source);
  } finally { rmSync(fixture, { recursive: true, force: true }); }
});
