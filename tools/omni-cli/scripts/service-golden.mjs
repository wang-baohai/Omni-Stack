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
import { findWorkspaceRoot } from '../dist/src/workspace.js';

const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-service-golden-'));
const backendRoot = resolve(workspaceRoot, 'omni-backend');
const backendTarget = resolve(backendRoot, `.omni-service-golden-${process.pid}-${Date.now()}`);
const output = resolve(systemTempRoot, 'generated');

try {
  const plan = planServiceGeneration(workspaceRoot, 'inventory-sample', {
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

  const generatedModule = resolve(output, 'omni-backend/omni-inventory-sample');
  cpSync(generatedModule, backendTarget, { recursive: true, errorOnExist: true, force: false });
  const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
  const result = spawnSync(wrapper, ['-f', resolve(backendTarget, 'pom.xml'), 'test'], {
    cwd: backendRoot,
    env: process.env,
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, '黄金服务 Maven 测试失败；请先 install 当前公共模块');
  console.log(`golden service valid: ${lock.spec.artifactId}, files=${lock.files.length}`);
} finally {
  removeVerifiedTree(systemTempRoot, tmpdir());
  removeVerifiedTree(backendTarget, backendRoot);
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
