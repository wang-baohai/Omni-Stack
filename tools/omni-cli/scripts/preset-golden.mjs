import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, globSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, relative, resolve, sep } from 'node:path';
import { parseDocument } from 'yaml';
import { loadCatalog } from '../dist/src/catalog.js';
import { validateGeneratedStructure } from './preset-structure-assertions.mjs';
import { loadComposeApplication } from '../dist/src/compose-model.js';
import {
  applyPresetGeneration,
  planPresetGeneration,
  validateGeneratedPreset,
} from '../dist/src/preset-generator.js';
import { findWorkspaceRoot } from '../dist/src/workspace.js';
import { lintFingerprint } from './lint-baseline.mjs';
import { jdkEnvironment } from './jdk-environment.mjs';
import { initializeEnvironment } from './init-env.mjs';

const OFFICIAL_PRESETS = ['core', 'workflow', 'crm', 'supply-chain', 'full'];
const workspaceRoot = findWorkspaceRoot(resolve(import.meta.dirname, '..'));
const sourceCatalog = loadCatalog(workspaceRoot);
const argumentsMap = parseArguments(process.argv.slice(2));
const presetIds = argumentsMap.presets ?? OFFICIAL_PRESETS;
const systemTempRoot = mkdtempSync(resolve(tmpdir(), 'omni-preset-golden-'));

try {
  for (const presetId of presetIds) {
    assert.ok(OFFICIAL_PRESETS.includes(presetId), `未知正式预设：${presetId}`);
    const target = resolve(systemTempRoot, presetId);
    const plan = planPresetGeneration(workspaceRoot, presetId, target);
    applyPresetGeneration(workspaceRoot, plan);
    const lock = validateGeneratedPreset(target, presetId);
    validateGeneratedStructure(target, sourceCatalog, new Set(lock.modules.map((module) => module.id)));

    if (!argumentsMap.structuralOnly) {
      // 使用交付项目自己的模板初始化私有配置；配置失败在昂贵构建之前报告。
      initializeEnvironment(target);
      run('docker', ['compose', '--env-file', '.env', 'config', '--quiet'], target);
      const backendRoot = resolve(target, 'omni-backend');
      const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
      run(wrapper, ['clean', argumentsMap.verifyOnly ? 'verify' : 'install'], backendRoot, jdkEnvironment());
      const frontendRoot = resolve(target, 'omni-frontend');
      run('npm', ['ci'], frontendRoot);
      // WP-011 R03 返修：生成物必须自身 lint exit 0，不得以"相对源仓新增为 0"替代。
      // 过滤掉 e2e/wp006-visual-screenshot.mjs、e2e/wp009-f/ 后，生成项目应无 lint 错误。
      const lintResult = spawnSync('npm', ['run', 'lint'], {
        cwd: frontendRoot,
        encoding: 'utf8',
        shell: process.platform === 'win32',
        maxBuffer: 32 * 1024 * 1024,
      });
      const lintOutput = `${lintResult.stdout ?? ''}\n${lintResult.stderr ?? ''}`;
      const lintErrors = lintFingerprint(lintOutput, frontendRoot);
      assert.equal(lintResult.status, 0, `${presetId} 生成物 npm run lint exit ${lintResult.status}, errors:\n${lintOutput.slice(-2000)}`);
      assert.deepEqual(lintErrors, [], `${presetId} 生成物 lint 仍有错误：${lintErrors.slice(0, 5).join(', ')}`);
      console.log(`preset golden lint: ${presetId} errors=0 exit=0 strict=pass`);
      run('npm', ['run', 'build'], frontendRoot);
    }
    console.log(`preset golden valid: ${presetId}, modules=${lock.modules.length}`);
  }
} finally {
  if (argumentsMap.keep) {
    console.log(`preset golden workspace retained: ${systemTempRoot}`);
  } else {
    removeVerifiedTree(systemTempRoot, tmpdir());
  }
}

function parseArguments(args) {
  const value = { presets: undefined, structuralOnly: false, verifyOnly: false, keep: false };
  for (const argument of args) {
    if (argument === '--structural-only') value.structuralOnly = true;
    else if (argument === '--verify-only') value.verifyOnly = true;
    else if (argument === '--keep') value.keep = true;
    else if (argument.startsWith('--presets=')) {
      value.presets = argument.slice('--presets='.length).split(',').map((entry) => entry.trim()).filter(Boolean);
      assert.ok(value.presets.length > 0, '--presets 至少包含一个预设');
    } else {
      throw new Error(`未知参数：${argument}`);
    }
  }
  return value;
}

function run(command, args, cwd, extraEnvironment = {}) {
  console.log(`preset golden command: ${command} ${args.join(' ')}`);
  const result = spawnSync(command, args, {
    cwd,
    env: { ...process.env, ...extraEnvironment },
    encoding: 'utf8',
    shell: process.platform === 'win32',
    stdio: 'inherit',
  });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `预设黄金命令失败：${command} ${args.join(' ')}`);
}

function unique(values) {
  return [...new Set(values)];
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
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
