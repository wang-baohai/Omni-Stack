import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { spawnSync } from 'node:child_process';
import {
  applyServiceGeneration,
  planServiceGeneration,
  validateGeneratedService,
} from '../dist/src/service-generator.js';
import { renderServiceIntegration } from '../dist/src/service-integration-renderer.js';
import { applyRenderedIntegration } from '../dist/src/service-integration-transaction.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';
import { initializeEnvironment } from './init-env.mjs';
import { jdkEnvironment } from './jdk-environment.mjs';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-service-golden-'));
const fixtureRoot = resolve(systemTempRoot, 'workspace');
const output = resolve(systemTempRoot, 'generated');

try {
  copyWorkspace(workspaceRoot, fixtureRoot);
  const plan = planServiceGeneration(fixtureRoot, 'inventory-sample', {
    output,
    operLog: true,
    mq: true,
    dataScope: true,
    dataScopeTable: ['inventory_sample_item'],
    tablePrefix: 'inventory_sample_',
  });
  assert.equal(applyServiceGeneration(plan), 'created');
  const lock = validateGeneratedService(output, plan.spec);
  assert.equal(lock.spec.enableJob, true, 'MQ 必须自动启用 Job');
  // 模板 1.5.1 保留四语言维护文档。
  assert.equal(lock.files.length, 33, '黄金生成包锁定文件数量漂移');

  const rendered = renderServiceIntegration(fixtureRoot, output, 'inventory-sample', { checkGit: false });
  // 文档清单登记与复核队列生成各占一项；与正式 planner 的 28 项契约一致。
  assert.equal(rendered.plan.operations.length, 28, '黄金接入操作数量漂移');
  // 文档正文、正式清单和复核队列一并进入接入事务。
  assert.equal(rendered.changes.length, 39, '黄金接入文件数量漂移');
  assert.deepEqual(applyRenderedIntegration(fixtureRoot, rendered), { files: 39, cleanupWarnings: [] });

  // 复用正式初始化入口，先验证所有必填配置，避免构建后才发现环境模板漂移。
  initializeEnvironment(fixtureRoot);
  run('docker', ['compose', '--env-file', '.env', 'config', '--quiet'], fixtureRoot);
  const backendRoot = resolve(fixtureRoot, 'omni-backend');
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  run(wrapper, ['clean', 'install', '-pl', 'omni-inventory-sample,omni-db-migrator', '-am'], backendRoot, jdkEnvironment());

  console.log(`golden service valid: ${lock.spec.artifactId}, generated=${lock.files.length}, integrated=${rendered.changes.length}`);
} finally {
  removeVerifiedTree(systemTempRoot, tmpdir());
}

function copyWorkspace(sourceRoot, targetRoot) {
  const ignoredSegments = new Set([
    '.git',
    '.qoder',
    '.agents',
    '.codex',
    '.idea',
    'node_modules',
    'target',
    'dist',
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

function removeVerifiedTree(target, allowedParent) {
  const resolvedTarget = resolve(target);
  const resolvedParent = resolve(allowedParent);
  const child = relative(resolvedParent, resolvedTarget);
  if (!child || child === '..' || child.startsWith(`..${sep}`) || basename(resolvedTarget).length < 8) {
    throw new Error(`拒绝清理未验证路径：${resolvedTarget}`);
  }
  if (existsSync(resolvedTarget)) rmSync(resolvedTarget, { recursive: true, force: true });
}
