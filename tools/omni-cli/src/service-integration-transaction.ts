import { randomUUID } from 'node:crypto';
import {
  existsSync,
  lstatSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmdirSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { basename, dirname, isAbsolute, relative, resolve, sep } from 'node:path';
import { CliError } from './errors.js';
import type { IntegrationFileChange, RenderedServiceIntegration } from './types.js';

interface PreparedChange {
  change: IntegrationFileChange;
  target: string;
  temporary: string;
  backup?: string;
  applied: boolean;
}

/** 接入事务执行结果。 */
export interface IntegrationTransactionResult {
  files: number;
  cleanupWarnings: string[];
}

/**
 * 原子应用已完成内存后置校验的接入变更集。
 */
export function applyRenderedIntegration(
  workspaceRoot: string,
  rendered: RenderedServiceIntegration,
  testOptions: { failAfter?: number } = {},
): IntegrationTransactionResult {
  if (!rendered.plan.ready) throw new CliError('接入计划未就绪，拒绝写入');
  const root = resolve(workspaceRoot);
  const transactionId = randomUUID().replaceAll('-', '');
  const createdDirectories = new Set<string>();
  const prepared: PreparedChange[] = [];

  try {
    for (const change of rendered.changes) {
      const target = resolve(root, ...change.target.split('/'));
      assertChildPath(root, target);
      assertNoSymlinkAncestors(root, target);
      verifyCurrentState(change, target);
      rememberMissingDirectories(root, dirname(target), createdDirectories);
      mkdirSync(dirname(target), { recursive: true });
      const temporary = resolve(dirname(target), `.${basename(target)}.omni-${transactionId}.tmp`);
      const backup = change.mode === 'modify'
        ? resolve(dirname(target), `.${basename(target)}.omni-${transactionId}.bak`)
        : undefined;
      if (existsSync(temporary) || (backup && existsSync(backup))) {
        throw new CliError(`事务临时路径冲突：${change.target}`);
      }
      writeFileSync(temporary, change.after, { encoding: 'utf8', flag: 'wx' });
      prepared.push({ change, target, temporary, ...(backup ? { backup } : {}), applied: false });
    }

    let appliedCount = 0;
    for (const item of prepared) {
      if (item.backup) renameSync(item.target, item.backup);
      try {
        renameSync(item.temporary, item.target);
      } catch (error) {
        if (item.backup && existsSync(item.backup) && !existsSync(item.target)) {
          renameSync(item.backup, item.target);
        }
        throw error;
      }
      item.applied = true;
      appliedCount += 1;
      if (testOptions.failAfter !== undefined && appliedCount === testOptions.failAfter) {
        throw new Error(`测试注入失败：第 ${appliedCount} 个文件`);
      }
    }

    for (const item of prepared) {
      const actual = readFileSync(item.target, 'utf8');
      if (actual !== item.change.after) throw new Error(`磁盘写后内容不一致：${item.change.target}`);
    }
  } catch (error) {
    const rollbackErrors = rollback(prepared, createdDirectories);
    const detail = error instanceof Error ? error.message : String(error);
    if (rollbackErrors.length > 0) {
      throw new CliError(`接入事务失败且回滚不完整：${detail}；${rollbackErrors.join('; ')}`);
    }
    throw new CliError(`接入事务失败，已完整回滚：${detail}`);
  }

  const cleanupWarnings: string[] = [];
  for (const item of prepared) {
    if (!item.backup || !existsSync(item.backup)) continue;
    try {
      unlinkSync(item.backup);
    } catch (error) {
      cleanupWarnings.push(`备份清理失败 ${item.backup}: ${messageOf(error)}`);
    }
  }
  return { files: prepared.length, cleanupWarnings };
}

function verifyCurrentState(change: IntegrationFileChange, target: string): void {
  if (change.mode === 'create') {
    if (existsSync(target)) throw new CliError(`创建目标已存在：${change.target}`);
    return;
  }
  if (!existsSync(target) || !statSync(target).isFile()) throw new CliError(`修改目标不是普通文件：${change.target}`);
  const current = readFileSync(target, 'utf8');
  if (current !== change.before) throw new CliError(`目标在渲染后发生变化，拒绝写入：${change.target}`);
}

function rollback(prepared: PreparedChange[], createdDirectories: Set<string>): string[] {
  const errors: string[] = [];
  for (const item of [...prepared].reverse()) {
    try {
      if (item.applied && existsSync(item.target)) unlinkSync(item.target);
      if (item.backup && existsSync(item.backup)) renameSync(item.backup, item.target);
      if (existsSync(item.temporary)) unlinkSync(item.temporary);
    } catch (error) {
      errors.push(`${item.change.target}: ${messageOf(error)}`);
    }
  }
  const directories = [...createdDirectories].sort((left, right) => right.length - left.length);
  for (const directory of directories) {
    try {
      if (existsSync(directory) && readdirSync(directory).length === 0) rmdirSync(directory);
    } catch (error) {
      errors.push(`${directory}: ${messageOf(error)}`);
    }
  }
  return errors;
}

function rememberMissingDirectories(root: string, directory: string, result: Set<string>): void {
  let current = resolve(directory);
  while (current !== root && !existsSync(current)) {
    assertChildPath(root, current);
    result.add(current);
    current = dirname(current);
  }
}

function assertNoSymlinkAncestors(root: string, target: string): void {
  const path = relative(root, target);
  let current = root;
  for (const segment of path.split(sep)) {
    current = resolve(current, segment);
    if (existsSync(current) && lstatSync(current).isSymbolicLink()) {
      throw new CliError(`目标路径包含符号链接，拒绝写入：${current}`);
    }
  }
}

function assertChildPath(root: string, target: string): void {
  const path = relative(resolve(root), resolve(target));
  if (!path || path === '..' || path.startsWith(`..${sep}`) || isAbsolute(path)) {
    throw new CliError(`接入目标越界：${target}`);
  }
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
