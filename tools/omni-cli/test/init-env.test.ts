import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import test from 'node:test';
import { findWorkspaceRoot } from '../src/workspace.js';

test('fresh environment has valid secret shapes and repeat execution preserves original credentials', () => {
  const root = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
  const fixture = mkdtempSync(resolve(tmpdir(), 'omni-env-init-'));
  try {
    copyFileSync(resolve(root, '.env.example'), resolve(fixture, '.env.example'));
    const args = [resolve(root, 'tools/omni-cli/scripts/init-env.mjs'), '--root', fixture];
    const output = execFileSync(process.execPath, args, { encoding: 'utf8' });
    const content = readFileSync(resolve(fixture, '.env'), 'utf8');
    const values = Object.fromEntries(content.split(/\r?\n/).filter((line) => /^[A-Z0-9_]+=/.test(line)).map((line) => { const index = line.indexOf('='); return [line.slice(0, index), line.slice(index + 1)]; }));
    assert.equal(values.JWK_ENCRYPT_KEY?.length, 32);
    assert.ok((values.XXL_JOB_ADMIN_PASSWORD?.length ?? 0) >= 14 && (values.XXL_JOB_ADMIN_PASSWORD?.length ?? 0) <= 20);
    assert.ok(Buffer.from(values.NACOS_AUTH_TOKEN!, 'base64').length >= 32);
    for (const name of ['MYSQL_ROOT_PASSWORD', 'OMNI_DB_PASSWORD', 'REDIS_PASSWORD', 'GATEWAY_ASSERTION_SECRET', 'NACOS_PASSWORD', 'OAUTH2_STATE_SECRET']) {
      assert.ok(values[name] && !values[name]!.startsWith('replace-'));
      assert.equal(output.includes(values[name]!), false);
    }
    assert.throws(() => execFileSync(process.execPath, args, { stdio: 'pipe' }), /Command failed/);
    assert.equal(readFileSync(resolve(fixture, '.env'), 'utf8'), content);
  } finally { rmSync(fixture, { recursive: true, force: true }); }
});
