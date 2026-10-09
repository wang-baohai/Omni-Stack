/**
 * WP-011 S06：黄金测试共用的 lint 基线工具。
 *
 * 背景：源仓存在未跟踪历史脚本的 lint 债务（WP-010 已登记为历史 FAIL），
 * 生成项目会原样继承这些文件。为不把历史债务误判为本包生成缺陷，也不放宽门禁，
 * 黄金脚本改用分层断言：生成物错误集必须 ⊆ 源仓基线（零新增），并如实记录基线规模。
 */
import { spawnSync } from 'node:child_process';

/** 捕获 lint 输出（容忍非零退出，历史债务由分层断言处理）。 */
export function captureLint(frontendRoot) {
  const result = spawnSync('npm', ['run', 'lint'], {
    cwd: frontendRoot,
    encoding: 'utf8',
    shell: process.platform === 'win32',
    maxBuffer: 32 * 1024 * 1024,
  });
  return `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
}

/**
 * 提取“相对路径|行:列|规则”指纹集合（stylish 表头行切换当前文件）。
 * 传入 root 时路径相对化，以便不同根目录（源仓 vs 生成物）的基线可互相比对。
 */
export function lintFingerprint(output, root) {
  const set = [];
  let current = '';
  const prefix = root ? root.replace(/\\/g, '/') : '';
  for (const line of output.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (/^[A-Za-z]:[\\/]|^\//.test(trimmed) && /\.(ts|vue|js|tsx|mts)$/.test(trimmed)) {
      current = trimmed.replace(/\\/g, '/');
      continue;
    }
    const matched = trimmed.match(/^(\d+):(\d+)\s+(?:error|warning)\s+.*\s+([\w@/.-]+)$/);
    if (!matched) continue;
    let path = current;
    if (prefix && path.startsWith(prefix)) path = path.slice(prefix.length + 1);
    set.push(`${path}|${matched[1]}:${matched[2]}|${matched[3]}`);
  }
  return set;
}
