import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { renderCrudGeneration, validateExistingCrud } from '../dist/src/crud-generator.js';
import { applyRenderedIntegration } from '../dist/src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';
import { captureLint, lintFingerprint } from './lint-baseline.mjs';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-crud-golden-'));
const fixtureRoot = resolve(systemTempRoot, 'workspace');

try {
  copyWorkspace(workspaceRoot, fixtureRoot);
  const spec = resolve(fixtureRoot, 'scaffold/specs/material-brand.yaml');
  const rendered = renderCrudGeneration(fixtureRoot, spec);
  assert.equal(rendered.plan.unchanged, false);
  // S06 lint 分层：先在未应用的 fixture 上捕基线，再应用生成后对比（同一 npm ci 由后续步骤统一执行）
  const frontendRoot = resolve(fixtureRoot, 'omni-frontend');
  run('npm', ['ci', '--ignore-scripts'], frontendRoot);
  const baselineErrors = lintFingerprint(captureLint(frontendRoot));
  // S04 模板 1.1.0：新增 ja-JP/ko-KR i18n fragment，27→29（WP-011 CC-2 合同变更，非放宽断言）
  assert.equal(rendered.changes.length, 29, '黄金 CRUD 变更数量漂移');
  assert.deepEqual(applyRenderedIntegration(fixtureRoot, rendered), {
    files: rendered.changes.length,
    cleanupWarnings: [],
  });
  const lock = validateExistingCrud(fixtureRoot, spec);
  assert.equal(lock.files.length, 18, '黄金 CRUD 锁定文件数量漂移');
  assert.equal(renderCrudGeneration(fixtureRoot, spec).plan.unchanged, true, '第二次生成必须无差异');

  const backendRoot = resolve(fixtureRoot, 'omni-backend');
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  run(wrapper, ['clean', 'verify', '-pl', 'omni-procurement', '-am'], backendRoot, jdkEnvironment());

  // 层级 2：生成后 lint 错误集必须与基线完全相等（生成物零新增）；层级 3：生成物自身 eslint 0 error。
  run('npm', ['run', 'build'], frontendRoot);
  const afterErrors = lintFingerprint(captureLint(frontendRoot));
  const newErrors = afterErrors.filter((e) => !baselineErrors.includes(e));
  assert.deepEqual(newErrors, [], `生成物引入新 lint 错误：${newErrors.join(', ')}`);
  const generatedLintTargets = rendered.changes
    .map((change) => change.target)
    .filter((target) => target.startsWith('omni-frontend/src/') && /\.(ts|vue)$/.test(target))
    .map((target) => target.replace(/^omni-frontend\//, ''));
  assert.ok(generatedLintTargets.length > 0, 'CRUD 生成物必须含前端源码文件');
  const gen = spawnSync('npx', ['eslint', ...generatedLintTargets], { cwd: frontendRoot, encoding: 'utf8', shell: process.platform === 'win32' });
  if (gen.status !== 0) throw new Error(`生成物自身 lint 未清零：\n${(gen.stdout ?? '') + (gen.stderr ?? '')}`);
  console.log(`golden CRUD lint: baselineErrors=${baselineErrors.length}, newErrors=0, generatedFilesChecked=${generatedLintTargets.length}`);

  console.log(`golden CRUD valid: ${rendered.plan.aggregateKey}, generated=${lock.files.length}, changes=${rendered.changes.length}`);
} finally {
  removeVerifiedTree(systemTempRoot, tmpdir());
}

function copyWorkspace(sourceRoot, targetRoot) {
  const ignoredSegments = new Set([
    '.git', '.qoder', '.agents', '.codex', '.idea', 'node_modules', 'target', 'dist',
  ]);
  cpSync(sourceRoot, targetRoot, {
    recursive: true,
    errorOnExist: true,
    force: false,
    filter(source) {
      const child = relative(sourceRoot, source);
      if (!child) return true;
      const segments = child.split(sep);
      if (segments.some((segment) => ignoredSegments.has(segment))) return false;
      const name = basename(source);
      return name === '.env.example' || !name.startsWith('.env');
    },
  });
}

function run(command, args, cwd, extraEnvironment = {}) {
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `黄金命令失败：${command} ${args.join(' ')}`);
}

/** 捕获 lint 输出与指纹提取已抽到 lint-baseline.mjs，供 preset-golden 共用（避免导入即执行本脚本）。 */

function jdkEnvironment() {
  const javaHome = process.env.JAVA_HOME && process.env.JAVA_HOME.includes('jdk-25')
    ? process.env.JAVA_HOME
    : 'C:\\APP\\JDK25\\jdk-25.0.2';
  return {
    JAVA_HOME: javaHome,
    PATH: `${resolve(javaHome, 'bin')}${process.platform === 'win32' ? ';' : ':'}${process.env.PATH ?? ''}`,
  };
}

function removeVerifiedTree(target, allowedParent) {
  const resolvedTarget = resolve(target);
  const resolvedParent = resolve(allowedParent);
  const child = relative(resolvedParent, resolvedTarget);
  if (!child || child === '..' || child.startsWith(`..${sep}`) || basename(resolvedTarget).length < 8) {
    throw new Error(`拒绝清理未验证路径：${resolvedTarget}`);
  }
  if (existsSync(resolvedTarget)) rmSync(resolvedTarget, { recursive: true, force: true });
}
