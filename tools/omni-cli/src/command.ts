import assert from 'node:assert/strict';
import { spawnSync, type SpawnSyncReturns } from 'node:child_process';
import { CliError } from './errors.js';

/** 以继承终端的方式执行外部命令。 */
export function runCommand(
  command: string,
  args: string[],
  cwd: string,
  environment: NodeJS.ProcessEnv = {},
): void {
  const result = invoke(command, args, cwd, environment, 'inherit');
  if (result.error) throw new CliError(`无法启动命令 ${command}: ${result.error.message}`);
  if (result.status !== 0) throw new CliError(`命令执行失败 (${result.status ?? 'unknown'}): ${command} ${args.join(' ')}`);
}

/** 捕获外部命令输出且不使用 shell。 */
export function captureCommand(
  command: string,
  args: string[],
  cwd: string,
  environment: NodeJS.ProcessEnv = {},
): { status: number | null; stdout: string; stderr: string; error?: Error } {
  const result = invoke(command, args, cwd, environment, 'pipe');
  return {
    status: result.status,
    stdout: result.stdout ?? '',
    stderr: result.stderr ?? '',
    ...(result.error ? { error: result.error } : {}),
  };
}

/** 返回当前平台可执行的 Maven/npm Wrapper 名称。 */
export function platformCommand(command: string, workspaceLocal = false): string {
  if (process.platform === 'win32') return `${command}.cmd`;
  return workspaceLocal ? `./${command}` : command;
}

function invoke(
  command: string,
  args: string[],
  cwd: string,
  environment: NodeJS.ProcessEnv,
  stdio: 'inherit' | 'pipe',
): SpawnSyncReturns<string> {
  const invocation = commandInvocation(command, args);
  return spawnSync(invocation.command, invocation.args, {
    cwd,
    env: { ...process.env, ...environment },
    encoding: 'utf8',
    stdio,
    maxBuffer: 20 * 1024 * 1024,
  });
}

function commandInvocation(command: string, args: string[]): { command: string; args: string[] } {
  if (process.platform !== 'win32' || !command.toLowerCase().endsWith('.cmd')) return { command, args };
  const commandLine = [command, ...args].map(quoteWindowsCommandArgument).join(' ');
  return { command: process.env.ComSpec || 'cmd.exe', args: ['/d', '/c', commandLine] };
}

function quoteWindowsCommandArgument(value: string): string {
  assert.doesNotMatch(value, /[\r\n&|<>^%!]/, 'Windows 命令参数包含不安全字符');
  if (!/[\t "]/u.test(value)) return value;
  return `"${value.replaceAll('"', '""')}"`;
}
