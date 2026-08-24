import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { renderCrudGeneration, validateExistingCrud } from '../dist/src/crud-generator.js';
import { applyRenderedIntegration } from '../dist/src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-crud-golden-'));
const fixtureRoot = resolve(systemTempRoot, 'workspace');

try {
  copyWorkspace(workspaceRoot, fixtureRoot);
  const spec = resolve(fixtureRoot, 'scaffold/specs/material-brand.yaml');
  const rendered = renderCrudGeneration(fixtureRoot, spec);
  assert.equal(rendered.plan.unchanged, false);
  assert.equal(rendered.changes.length, 27, '黄金 CRUD 变更数量漂移');
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

  const frontendRoot = resolve(fixtureRoot, 'omni-frontend');
  run('npm', ['ci', '--ignore-scripts'], frontendRoot);
  run('npm', ['run', 'build'], frontendRoot);
  run('npm', ['run', 'lint'], frontendRoot);

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
