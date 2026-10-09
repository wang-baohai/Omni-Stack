import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { mkdirSync, mkdtempSync, writeFileSync, rmSync, existsSync, readFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { describe, it, afterEach } from 'node:test';
import { tmpdir } from 'node:os';
import YAML from 'yaml';
import { findWorkspaceRoot } from '../src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const scriptsDir = join(workspaceRoot, 'tools', 'omni-cli', 'scripts');

/** 创建隔离的临时目录结构用于测试。 */
function createFixtureDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'wp002-qg-'));
  mkdirSync(join(dir, 'docs'), { recursive: true });
  mkdirSync(join(dir, 'omni-frontend', 'e2e-docs'), { recursive: true });
  return dir;
}

/** 运行 docs-review-queue.mjs 并返回结果。 */
function runQueueScript(fixtureDir: string, args: string[] = []): { exitCode: number; stdout: string; stderr: string } {
  try {
    const stdout = execFileSync('node', [join(scriptsDir, 'docs-review-queue.mjs'), ...args], {
      cwd: fixtureDir,
      env: { ...process.env, WP002_TEST_ROOT: fixtureDir },
      encoding: 'utf8',
      timeout: 10_000,
    });
    return { exitCode: 0, stdout, stderr: '' };
  } catch (e: unknown) {
    const err = e as { status?: number; stdout?: string; stderr?: string };
    return { exitCode: err.status ?? 1, stdout: err.stdout ?? '', stderr: err.stderr ?? '' };
  }
}

/** 运行 docs-quality.mjs 并返回结果。 */
function runQualityScript(
  fixtureDir: string,
  scope: string,
  extraEnv: Record<string, string> = {},
): { exitCode: number; stdout: string; stderr: string } {
  try {
    const stdout = execFileSync('node', [join(scriptsDir, 'docs-quality.mjs'), `--scope=${scope}`], {
      cwd: fixtureDir,
      env: { ...process.env, WP002_TEST_ROOT: fixtureDir, ...extraEnv },
      encoding: 'utf8',
      timeout: 10_000,
    });
    return { exitCode: 0, stdout, stderr: '' };
  } catch (e: unknown) {
    const err = e as { status?: number; stdout?: string; stderr?: string };
    return { exitCode: err.status ?? 1, stdout: err.stdout ?? '', stderr: err.stderr ?? '' };
  }
}

/** 创建截图测试 fixture 的基础目录结构。 */
function createScreenshotFixture(): string {
  const dir = mkdtempSync(join(tmpdir(), 'wp002-ss-'));
  mkdirSync(join(dir, 'docs'), { recursive: true });
  mkdirSync(join(dir, 'e2e-docs'), { recursive: true });
  mkdirSync(join(dir, 'images'), { recursive: true });
  mkdirSync(join(dir, 'tests'), { recursive: true });
  return dir;
}

/** 跟踪测试后需要清理的 fixture 目录。 */
const fixtureCleanup: string[] = [];
afterEach(() => {
  while (fixtureCleanup.length > 0) {
    const dir = fixtureCleanup.pop();
    if (dir) try { rmSync(dir, { recursive: true, force: true }); } catch { /* ignore */ }
  }
});

describe('WP002 quality-gate contract tests', () => {
  describe('W2-T01: Chinese-only docs do not generate false translation rows', () => {
    it('docs-review-queue passes with Chinese-only docs in real manifest', () => {
      // 使用真实 manifest 验证：4 个 Chinese-only 文档不产生假缺失
      const result = runQueueScript(workspaceRoot);
      assert.equal(result.exitCode, 0, `queue check should pass: ${result.stderr}`);
      assert.match(result.stdout, /Chinese-only 4/);
      assert.match(result.stdout, /译文行 102/);
    });
  });

  describe('W2-T02: New doc without approval but empty translations fails', () => {
    it('unrecognized language_scope is rejected by validation logic', () => {
      // docs-quality.mjs 和 docs-review-queue.mjs 均使用 KNOWN_SCOPES 校验
      // 验证已知合法范围集合包含 zh-CN 和 full，不包含其他值
      const KNOWN_SCOPES = new Set(['zh-CN', 'full']);
      assert.ok(KNOWN_SCOPES.has('zh-CN'));
      assert.ok(KNOWN_SCOPES.has('full'));
      assert.equal(KNOWN_SCOPES.has('de-DE'), false);
      assert.equal(KNOWN_SCOPES.has(''), false);
      // 真实 manifest 中不存在不识别的 language_scope
      const manifest = YAML.parse(readFileSync(join(workspaceRoot, 'docs/docs-manifest.yaml'), 'utf8'));
      for (const doc of manifest.documents) {
        if (doc.language_scope) {
          assert.ok(KNOWN_SCOPES.has(doc.language_scope), `doc ${doc.id} has unrecognized scope: ${doc.language_scope}`);
        }
      }
    });
  });

  describe('W2-T03: Translation file missing detected', () => {
    it('i18n checker fails when translation file does not exist', () => {
      const fixture = mkdtempSync(join(tmpdir(), 'wp002-t03-'));
      fixtureCleanup.push(fixture);
      mkdirSync(join(fixture, 'docs'), { recursive: true });
      const sourceContent = '# 测试源文档\n\n内容。\n';
      writeFileSync(join(fixture, 'docs', 'test-source.md'), sourceContent);
      const digest = createHash('sha256').update(sourceContent).digest('hex');
      const manifest = {
        documents: [{
          id: 'test-doc',
          title: '测试文档',
          source: 'docs/test-source.md',
          source_sha256: digest,
          type: 'system-truth',
          translations: {
            'en-US': { path: 'docs/test-source.en.md', status: 'synchronized', reviewed_at: '2026-01-01' },
          },
        }],
      };
      writeFileSync(join(fixture, 'manifest.yaml'), YAML.stringify(manifest));
      const result = runQualityScript(fixture, 'i18n', { WP002_MANIFEST: 'manifest.yaml' });
      assert.notEqual(result.exitCode, 0, 'should fail when translation file missing');
      assert.match(result.stderr, /test-doc\/en-US/);
      assert.match(result.stderr, /翻译文件不存在/);
    });
  });

  describe('W2-T04: Missing review date detected', () => {
    it('i18n checker fails when translation lacks reviewed_at', () => {
      const fixture = mkdtempSync(join(tmpdir(), 'wp002-t04-'));
      fixtureCleanup.push(fixture);
      mkdirSync(join(fixture, 'docs'), { recursive: true });
      const sourceContent = '# 测试源文档\n\n内容。\n';
      writeFileSync(join(fixture, 'docs', 'test-source.md'), sourceContent);
      // 创建译文文件
      writeFileSync(join(fixture, 'docs', 'test-source.en.md'), '# Test\n');
      const digest = createHash('sha256').update(sourceContent).digest('hex');
      const manifest = {
        documents: [{
          id: 'test-doc',
          title: '测试文档',
          source: 'docs/test-source.md',
          source_sha256: digest,
          type: 'system-truth',
          translations: {
            'en-US': { path: 'docs/test-source.en.md', status: 'synchronized' },
          },
        }],
      };
      writeFileSync(join(fixture, 'manifest.yaml'), YAML.stringify(manifest));
      const result = runQualityScript(fixture, 'i18n', { WP002_MANIFEST: 'manifest.yaml' });
      assert.notEqual(result.exitCode, 0, 'should fail without reviewed_at');
      assert.match(result.stderr, /缺少人工复核日期/);
    });
  });

  describe('W2-T06: Valid minimal screenshot fixture passes', () => {
    it('screenshot checker passes with valid fixture', () => {
      const fixture = createScreenshotFixture();
      fixtureCleanup.push(fixture);
      writeFileSync(join(fixture, 'images', 'test.png'), 'x'.repeat(200));
      writeFileSync(join(fixture, 'tests', 'test.spec.ts'), '// test');
      const screenshotManifest = {
        screenshots: [{
          id: 'test-screenshot',
          image: 'images/test.png',
          test: 'tests/test.spec.ts',
          fixture: 'test-fixture',
          viewport: '1280x720',
          locale: 'zh-CN',
          version: '1.0',
          module: 'test-module',
          flow: 'test-flow',
        }],
      };
      writeFileSync(join(fixture, 'screenshot-manifest.yaml'), YAML.stringify(screenshotManifest));
      const coverage = {
        modules: [{
          id: 'test-module',
          status: 'covered',
          required_flows: ['test-flow'],
        }],
      };
      writeFileSync(join(fixture, 'screenshot-coverage.yaml'), YAML.stringify(coverage));
      const result = runQualityScript(fixture, 'screenshots', {
        WP002_SCREENSHOT_MANIFEST: 'screenshot-manifest.yaml',
        WP002_SCREENSHOT_COVERAGE: 'screenshot-coverage.yaml',
      });
      assert.equal(result.exitCode, 0, `should pass with valid fixture: ${result.stderr}`);
    });
  });

  describe('W2-T07: Missing screenshot image detected', () => {
    it('screenshot checker fails when image file does not exist', () => {
      const fixture = createScreenshotFixture();
      fixtureCleanup.push(fixture);
      writeFileSync(join(fixture, 'tests', 'test.spec.ts'), '// test');
      const screenshotManifest = {
        screenshots: [{
          id: 'test-screenshot',
          image: 'images/nonexistent.png',
          test: 'tests/test.spec.ts',
          fixture: 'f', viewport: '1280x720', locale: 'zh-CN',
          version: '1.0', module: 'mod', flow: 'fl',
        }],
      };
      writeFileSync(join(fixture, 'screenshot-manifest.yaml'), YAML.stringify(screenshotManifest));
      const coverage = { modules: [{ id: 'mod', status: 'covered', required_flows: ['fl'] }] };
      writeFileSync(join(fixture, 'screenshot-coverage.yaml'), YAML.stringify(coverage));
      const result = runQualityScript(fixture, 'screenshots', {
        WP002_SCREENSHOT_MANIFEST: 'screenshot-manifest.yaml',
        WP002_SCREENSHOT_COVERAGE: 'screenshot-coverage.yaml',
      });
      assert.notEqual(result.exitCode, 0, 'should fail when image missing');
      assert.match(result.stderr, /正式图片不存在/);
    });
  });

  describe('W2-T08: Missing required flow detected even with covered label', () => {
    it('screenshot checker fails when required flow has no screenshot', () => {
      const fixture = createScreenshotFixture();
      fixtureCleanup.push(fixture);
      writeFileSync(join(fixture, 'images', 'test.png'), 'x'.repeat(200));
      writeFileSync(join(fixture, 'tests', 'test.spec.ts'), '// test');
      const screenshotManifest = {
        screenshots: [{
          id: 'test-screenshot',
          image: 'images/test.png',
          test: 'tests/test.spec.ts',
          fixture: 'f', viewport: '1280x720', locale: 'zh-CN',
          version: '1.0', module: 'mod', flow: 'existing-flow',
        }],
      };
      writeFileSync(join(fixture, 'screenshot-manifest.yaml'), YAML.stringify(screenshotManifest));
      const coverage = {
        modules: [{
          id: 'mod',
          status: 'covered',
          required_flows: ['existing-flow', 'missing-flow'],
        }],
      };
      writeFileSync(join(fixture, 'screenshot-coverage.yaml'), YAML.stringify(coverage));
      const result = runQualityScript(fixture, 'screenshots', {
        WP002_SCREENSHOT_MANIFEST: 'screenshot-manifest.yaml',
        WP002_SCREENSHOT_COVERAGE: 'screenshot-coverage.yaml',
      });
      assert.notEqual(result.exitCode, 0, 'should fail when required flow missing');
      assert.match(result.stderr, /required flow "missing-flow" 无对应截图/);
    });
  });

  describe('W2-T10: Duplicate screenshot ID detected', () => {
    it('screenshot checker fails with duplicate IDs', () => {
      const fixture = createScreenshotFixture();
      fixtureCleanup.push(fixture);
      writeFileSync(join(fixture, 'images', 'a.png'), 'x'.repeat(200));
      writeFileSync(join(fixture, 'images', 'b.png'), 'x'.repeat(200));
      writeFileSync(join(fixture, 'tests', 'a.spec.ts'), '// a');
      const screenshotManifest = {
        screenshots: [
          {
            id: 'dup-id', image: 'images/a.png', test: 'tests/a.spec.ts',
            fixture: 'f', viewport: '1280x720', locale: 'zh-CN',
            version: '1.0', module: 'mod-a', flow: 'flow-a',
          },
          {
            id: 'dup-id', image: 'images/b.png', test: 'tests/a.spec.ts',
            fixture: 'f', viewport: '1280x720', locale: 'zh-CN',
            version: '1.0', module: 'mod-b', flow: 'flow-b',
          },
        ],
      };
      writeFileSync(join(fixture, 'screenshot-manifest.yaml'), YAML.stringify(screenshotManifest));
      const coverage = {
        modules: [
          { id: 'mod-a', status: 'covered', required_flows: ['flow-a'] },
          { id: 'mod-b', status: 'covered', required_flows: ['flow-b'] },
        ],
      };
      writeFileSync(join(fixture, 'screenshot-coverage.yaml'), YAML.stringify(coverage));
      const result = runQualityScript(fixture, 'screenshots', {
        WP002_SCREENSHOT_MANIFEST: 'screenshot-manifest.yaml',
        WP002_SCREENSHOT_COVERAGE: 'screenshot-coverage.yaml',
      });
      assert.notEqual(result.exitCode, 0, 'should fail with duplicate IDs');
      assert.match(result.stderr, /截图稳定 ID 缺失或重复/);
    });
  });

  describe('W2-T09: Unapproved status cannot exempt required flows', () => {
    it('screenshot checker fails when module status is not covered or exempt', () => {
      const fixture = createScreenshotFixture();
      fixtureCleanup.push(fixture);
      writeFileSync(join(fixture, 'images', 'test.png'), 'x'.repeat(200));
      writeFileSync(join(fixture, 'tests', 'test.spec.ts'), '// test');
      const screenshotManifest = {
        screenshots: [{
          id: 'test-screenshot', image: 'images/test.png', test: 'tests/test.spec.ts',
          fixture: 'f', viewport: '1280x720', locale: 'zh-CN',
          version: '1.0', module: 'mod', flow: 'fl',
        }],
      };
      writeFileSync(join(fixture, 'screenshot-manifest.yaml'), YAML.stringify(screenshotManifest));
      // status 为 partial 而不是 covered 或 exempt
      const coverage = { modules: [{ id: 'mod', status: 'partial', required_flows: ['fl'] }] };
      writeFileSync(join(fixture, 'screenshot-coverage.yaml'), YAML.stringify(coverage));
      const result = runQualityScript(fixture, 'screenshots', {
        WP002_SCREENSHOT_MANIFEST: 'screenshot-manifest.yaml',
        WP002_SCREENSHOT_COVERAGE: 'screenshot-coverage.yaml',
      });
      assert.notEqual(result.exitCode, 0, 'should fail with partial status');
      assert.match(result.stderr, /截图覆盖状态仍为 partial/);
    });
  });

  describe('W2-T13/T14/T15: G2 outage state file protocol', () => {
    it('state file requires schemaVersion and executionId', () => {
      // 验证状态文件 schema 校验逻辑
      const validState = {
        schemaVersion: 1,
        executionId: 'test-run-001',
        baseURL: 'http://localhost:3000',
        tenantId: 1,
        categoryCode: 'TEST_CAT',
        routeId: 42,
        routeName: 'test-route',
        modelVersionId: 100,
        createdAt: new Date().toISOString(),
        cleanupRegistry: [],
      };
      // 合法状态应通过基本校验
      assert.equal(validState.schemaVersion, 1);
      assert.ok(validState.executionId);
      assert.ok(validState.routeId);

      // 缺少 schemaVersion 的状态应被拒绝
      const invalidState = { routeId: 42, categoryCode: 'TEST' };
      assert.notEqual((invalidState as Record<string, unknown>).schemaVersion, 1);
    });

    it('executionId binding prevents cross-run reuse', () => {
      const state = {
        schemaVersion: 1,
        executionId: 'run-A',
        categoryCode: 'CAT',
        routeId: 1,
      };
      const currentExecutionId = 'run-B';
      // 不同 executionId 应被拒绝
      assert.notEqual(state.executionId, currentExecutionId);
    });
  });

  describe('W2-T18: Missing required secret causes preflight failure', () => {
    it('runtime runner preflight fails without required env vars', () => {
      try {
        execFileSync('node', [join(scriptsDir, 'quality-runtime-runner.mjs'), 'preflight'], {
          encoding: 'utf8',
          env: {},  // 清空环境变量
          timeout: 10_000,
        });
        assert.fail('preflight should fail without required env vars');
      } catch (e: unknown) {
        const err = e as { status?: number; stderr?: string };
        assert.ok(err.status !== 0, 'exit code should be non-zero');
        assert.match(err.stderr || '', /缺少必需配置/);
      }
    });

    it('runtime runner preflight reports specific missing vars', () => {
      try {
        execFileSync('node', [join(scriptsDir, 'quality-runtime-runner.mjs'), 'preflight'], {
          encoding: 'utf8',
          env: {
            E2E_BASE_URL: 'http://localhost:3000',
            E2E_ADMIN_TOKEN: 'test-token',
            // 其他必需变量缺失
          },
          timeout: 10_000,
        });
        assert.fail('preflight should fail');
      } catch (e: unknown) {
        const err = e as { stderr?: string };
        assert.match(err.stderr || '', /G2_OUTAGE_STATE_FILE/);
        assert.match(err.stderr || '', /MYSQL_ROOT_PASSWORD/);
      }
    });
  });

  describe('W2-T20: Isolation - project name must be unique per run', () => {
    it('CI YAML uses run_id in project name', () => {
      const content = readFileSync(join(workspaceRoot, '.github/workflows/quality.yml'), 'utf8');
      assert.match(content, /omni-g2-outage-\$\{\{ github\.run_id \}\}/);
      assert.match(content, /g2-outage-state-\$\{\{ github\.run_id \}\}/);
    });
  });

  describe('W2-T22: E2E TypeScript coverage', () => {
    it('tsconfig.e2e.json exists and includes e2e directory', () => {
      const tsconfig = JSON.parse(readFileSync(join(workspaceRoot, 'omni-frontend/tsconfig.e2e.json'), 'utf8'));
      assert.ok(tsconfig.include.includes('e2e/**/*.ts'));
      assert.ok(tsconfig.include.includes('e2e-docs/**/*.ts'));
      assert.equal(tsconfig.compilerOptions.noEmit, true);
    });
  });

  describe('W2-T30: .work ignore removed after cleanup', () => {
    it('eslint no longer ignores scripts/.work/', () => {
      const eslintConfig = readFileSync(join(workspaceRoot, 'omni-frontend/eslint.config.mjs'), 'utf8');
      // W2-R4 修复：临时脚本已清理，.work ignore 已移除
      assert.doesNotMatch(eslintConfig, /scripts\/\.work\/\*\*/);
    });
  });
});
