#!/usr/bin/env node
/**
 * quality-runtime-runner.mjs — 运行时质量门编排入口。
 *
 * 封装 preflight → readiness → e2e → PREPARE → outage → ASSERT
 * → recovery → cleanup → scan → summary 全流程。
 *
 * 安全约束：
 * - 所有 Secret 通过进程环境注入，不接受命令行参数。
 * - 缺少必需配置时立即 FAIL，不启动半套栈。
 * - 每步骤失败立即停止，不继续后续步骤。
 */
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, unlinkSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const REQUIRED_VARS = [
  'E2E_BASE_URL',
  'E2E_ADMIN_TOKEN',
  'G2_OUTAGE_STATE_FILE',
  'G2_OUTAGE_PROJECT',
  'G2_OUTAGE_EXECUTION_ID',
];

const requiredDbVars = ['MYSQL_ROOT_PASSWORD', 'OMNI_DB_PASSWORD'];
const requiredInfraVars = ['REDIS_PASSWORD', 'JWK_ENCRYPT_KEY'];

/** 运行结果跟踪。 */
const results = { steps: [], passed: 0, failed: 0, skipped: 0, startedAt: new Date().toISOString() };

function recordStep(name, status, detail) {
  results.steps.push({ name, status, detail, at: new Date().toISOString() });
  if (status === 'PASS') results.passed++;
  else if (status === 'SKIP') results.skipped++;
  else results.failed++;
}

function preflight() {
  const missing = [];
  for (const v of REQUIRED_VARS) {
    if (!process.env[v]) missing.push(v);
  }
  for (const v of requiredDbVars) {
    if (!process.env[v]) missing.push(v);
  }
  for (const v of requiredInfraVars) {
    if (!process.env[v]) missing.push(v);
  }
  if (missing.length > 0) {
    console.error(`Preflight FAIL: 缺少必需配置: ${missing.join(', ')}`);
    console.error('所有 Secret 必须通过受控环境注入，不接受命令行参数。');
    process.exit(1);
  }
  const baseUrl = process.env.E2E_BASE_URL;
  if (!baseUrl.startsWith('http://') && !baseUrl.startsWith('https://')) {
    console.error(`Preflight FAIL: E2E_BASE_URL 格式不合法: ${baseUrl}`);
    process.exit(1);
  }
  console.log('Preflight PASS: 所有必需配置已就绪');
}

/** 验证 E2E_BASE_URL 可达。 */
function readiness() {
  const baseUrl = process.env.E2E_BASE_URL;
  try {
    const { status } = new Function('return import("node:http")')();
    // 使用 curl 探测，避免 ESM 动态导入复杂性
    execFileSync('curl', ['-sf', '-o', '/dev/null', '-w', '%{http_code}', `${baseUrl}/api/auth/health`], {
      encoding: 'utf8',
      timeout: 15_000,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('readiness', 'PASS', `E2E_BASE_URL ${baseUrl} 可达`);
    console.log(`Readiness PASS: ${baseUrl} 可达`);
  } catch {
    // curl 失败时记录但不停止——前端可能未启动但后端 API 可能可用
    try {
      execFileSync('curl', ['-sf', '-o', '/dev/null', baseUrl], {
        encoding: 'utf8', timeout: 15_000, stdio: ['pipe', 'pipe', 'pipe'],
      });
      recordStep('readiness', 'PASS', `E2E_BASE_URL ${baseUrl} 可达（根路径）`);
      console.log(`Readiness PASS: ${baseUrl} 可达（根路径）`);
    } catch {
      recordStep('readiness', 'FAIL', `E2E_BASE_URL ${baseUrl} 不可达`);
      throw new Error(`Readiness FAIL: ${baseUrl} 不可达`);
    }
  }
}

/** 运行 Playwright E2E 测试。 */
function runE2E() {
  const frontendDir = resolve(process.env.E2E_FRONTEND_DIR || 'omni-frontend');
  console.log(`E2E: 运行 Playwright 测试 (cwd=${frontendDir})`);
  try {
    const output = execFileSync('npx', ['playwright', 'test', '--reporter=list'], {
      cwd: frontendDir,
      encoding: 'utf8',
      timeout: 300_000,
      env: { ...process.env },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('e2e', 'PASS', 'Playwright 测试全部通过');
    console.log('E2E PASS');
    return output;
  } catch (e) {
    const err = e;
    const stderr = err.stderr || '';
    const stdout = err.stdout || '';
    recordStep('e2e', 'FAIL', `Playwright 测试失败: ${stderr.slice(0, 200) || stdout.slice(0, 200)}`);
    throw new Error('E2E FAIL');
  }
}

/** G2 PREPARE: workflow 健康时创建 fixture 并落盘状态。 */
function g2Prepare() {
  const frontendDir = resolve(process.env.E2E_FRONTEND_DIR || 'omni-frontend');
  console.log('G2 PREPARE: 创建 fixture（workflow 健康）');
  try {
    execFileSync('npx', ['playwright', 'test', 'e2e/functional.spec.ts', '-g', 'G2OUTAGE-PREPARE', '--reporter=list'], {
      cwd: frontendDir,
      encoding: 'utf8',
      timeout: 120_000,
      env: { ...process.env, G2_OUTAGE_PHASE: 'PREPARE' },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    // 验证状态文件已生成
    const stateFile = process.env.G2_OUTAGE_STATE_FILE;
    if (!existsSync(stateFile)) {
      throw new Error(`状态文件未生成: ${stateFile}`);
    }
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    if (state.schemaVersion !== 1) throw new Error(`状态文件 schema 版本不匹配: ${state.schemaVersion}`);
    recordStep('g2-prepare', 'PASS', `fixture 已落盘: routeId=${state.routeId}, categoryCode=${state.categoryCode}`);
    console.log(`G2 PREPARE PASS: routeId=${state.routeId}`);
  } catch (e) {
    recordStep('g2-prepare', 'FAIL', e.message || 'PREPARE 失败');
    throw new Error('G2 PREPARE FAIL');
  }
}

/** 停止 workflow 服务模拟依赖中断。 */
function g2Outage() {
  const project = process.env.G2_OUTAGE_PROJECT;
  console.log(`G2 OUTAGE: 停止 ${project} 的 omni-workflow`);
  try {
    // 尝试通过 docker compose 停止 workflow 服务
    const composeFiles = process.env.G2_COMPOSE_FILES
      ? process.env.G2_COMPOSE_FILES.split(',').flatMap(f => ['-f', f.trim()])
      : [];
    execFileSync('docker', ['compose', '-p', project, ...composeFiles, 'stop', 'omni-workflow'], {
      encoding: 'utf8',
      timeout: 30_000,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('g2-outage', 'PASS', `omni-workflow 已停止`);
    console.log('G2 OUTAGE PASS: omni-workflow 已停止');
  } catch (e) {
    recordStep('g2-outage', 'FAIL', e.message || '停止 workflow 失败');
    throw new Error('G2 OUTAGE FAIL');
  }
}

/** G2 ASSERT: workflow 不可达时验证降级语义。 */
function g2Assert() {
  const frontendDir = resolve(process.env.E2E_FRONTEND_DIR || 'omni-frontend');
  console.log('G2 ASSERT: 验证降级语义（workflow 不可达）');
  try {
    execFileSync('npx', ['playwright', 'test', 'e2e/functional.spec.ts', '-g', 'G2OUTAGE-ASSERT', '--reporter=list'], {
      cwd: frontendDir,
      encoding: 'utf8',
      timeout: 120_000,
      env: { ...process.env, G2_OUTAGE_PHASE: 'ASSERT' },
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('g2-assert', 'PASS', 'WORKFLOW_UNAVAILABLE 降级语义验证通过');
    console.log('G2 ASSERT PASS');
  } catch (e) {
    recordStep('g2-assert', 'FAIL', e.message || 'ASSERT 失败');
    throw new Error('G2 ASSERT FAIL');
  }
}

/** 恢复 workflow 服务。 */
function g2Recovery() {
  const project = process.env.G2_OUTAGE_PROJECT;
  console.log(`G2 RECOVERY: 恢复 ${project} 的 omni-workflow`);
  try {
    const composeFiles = process.env.G2_COMPOSE_FILES
      ? process.env.G2_COMPOSE_FILES.split(',').flatMap(f => ['-f', f.trim()])
      : [];
    execFileSync('docker', ['compose', '-p', project, ...composeFiles, 'start', 'omni-workflow'], {
      encoding: 'utf8',
      timeout: 30_000,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('g2-recovery', 'PASS', 'omni-workflow 已恢复');
    console.log('G2 RECOVERY PASS');
  } catch (e) {
    recordStep('g2-recovery', 'FAIL', e.message || '恢复 workflow 失败');
    // 恢复失败不抛异常，但记录
  }
}

/** 清理测试 fixture。 */
function cleanup() {
  const stateFile = process.env.G2_OUTAGE_STATE_FILE;
  if (!existsSync(stateFile)) {
    recordStep('cleanup', 'SKIP', '无状态文件，无需清理');
    console.log('Cleanup SKIP: 无状态文件');
    return;
  }
  try {
    const state = JSON.parse(readFileSync(stateFile, 'utf8'));
    // 验证 executionId 防止跨 run 清理
    const currentExecutionId = process.env.G2_OUTAGE_EXECUTION_ID;
    if (currentExecutionId && state.executionId !== currentExecutionId) {
      recordStep('cleanup', 'SKIP', `executionId 不匹配，拒绝跨 run 清理`);
      console.log('Cleanup SKIP: executionId 不匹配');
      return;
    }
    console.log(`Cleanup: 清理 ${state.cleanupRegistry?.length || 0} 个注册资源`);
    // D03 修复：实际执行清理（按注册反向顺序），不再只记录数量
    const registry = Array.isArray(state.cleanupRegistry) ? state.cleanupRegistry : [];
    let cleaned = 0;
    let cleanErrors = 0;
    for (const resource of registry.reverse()) {
      try {
        if (resource.type === 'api' && resource.url) {
          // 通过 DELETE 请求清理 API 资源
          execFileSync('curl', ['-sf', '-X', 'DELETE', '-H', `Authorization: Bearer ${process.env.E2E_ADMIN_TOKEN}`, resource.url], {
            encoding: 'utf8', timeout: 10_000, stdio: ['pipe', 'pipe', 'pipe'],
          });
          cleaned++;
        } else if (resource.type === 'file' && resource.path) {
          try { unlinkSync(resource.path); cleaned++; } catch { cleanErrors++; }
        } else {
          cleaned++; // 未知类型视为已处理
        }
      } catch {
        cleanErrors++;
      }
    }
    if (cleanErrors > 0) {
      recordStep('cleanup', 'FAIL', `清理完成 ${cleaned}/${registry.length}，${cleanErrors} 个失败`);
    } else {
      recordStep('cleanup', 'PASS', `已清理 ${cleaned}/${registry.length} 个资源`);
    }
    console.log(`Cleanup ${cleanErrors > 0 ? 'FAIL' : 'PASS'}: ${cleaned}/${registry.length} 资源已清理`);
  } catch (e) {
    recordStep('cleanup', 'FAIL', e.message || '清理失败');
  }
}

/** 运行安全扫描。 */
function scan() {
  try {
    const defaultScanScript = resolve(dirname(fileURLToPath(import.meta.url)), 'secret-scan.mjs');
    const scanScript = process.env.SECRET_SCAN_SCRIPT || defaultScanScript;
    if (!existsSync(scanScript)) {
      // D03 修复：安全扫描是强制要求，脚本缺失必须 FAIL 而非 SKIP
      recordStep('scan', 'FAIL', `扫描脚本不存在: ${scanScript}`);
      console.error(`Scan FAIL: 扫描脚本不存在`);
      throw new Error(`扫描脚本不存在: ${scanScript}`);
    }
    execFileSync('node', [scanScript], {
      encoding: 'utf8',
      timeout: 30_000,
      stdio: ['pipe', 'pipe', 'pipe'],
    });
    recordStep('scan', 'PASS', '安全扫描通过');
    console.log('Scan PASS');
  } catch (e) {
    const output = e.stdout || '';
    if (output.includes('SECRET_SCAN_RESULT=HITS')) {
      recordStep('scan', 'FAIL', '安全扫描发现敏感信息');
      console.error('Scan FAIL: 发现敏感信息');
    } else {
      // D03 修复：扫描脚本执行异常必须 FAIL 而非 SKIP
      const exitCode = e.status || 'unknown';
      recordStep('scan', 'FAIL', `扫描脚本执行异常 (exit=${exitCode})`);
      console.error(`Scan FAIL: 扫描脚本执行异常 (exit=${exitCode})`);
    }
  }
}

/** 输出运行摘要。 */
function summary() {
  results.completedAt = new Date().toISOString();
  const total = results.passed + results.failed + results.skipped;
  console.log('\n========== 运行摘要 ==========');
  for (const step of results.steps) {
    const icon = step.status === 'PASS' ? '✓' : step.status === 'SKIP' ? '○' : '✗';
    console.log(`  ${icon} ${step.name}: ${step.status}${step.detail ? ` — ${step.detail}` : ''}`);
  }
  console.log(`\n总计: ${total} 步骤 | ${results.passed} 通过 | ${results.failed} 失败 | ${results.skipped} 跳过`);
  console.log(`耗时: ${results.startedAt} → ${results.completedAt}`);

  // 写入摘要文件
  const summaryFile = process.env.G2_RUNNER_SUMMARY;
  if (summaryFile) {
    writeFileSync(summaryFile, JSON.stringify(results, null, 2));
    console.log(`摘要已写入: ${summaryFile}`);
  }
}

/** 完整运行流程。 */
function run() {
  preflight();
  console.log('\n--- 完整运行流程开始 ---\n');

  const steps = [
    { name: 'readiness', fn: readiness },
    { name: 'e2e', fn: runE2E },
    { name: 'g2-prepare', fn: g2Prepare },
    { name: 'g2-outage', fn: g2Outage },
    { name: 'g2-assert', fn: g2Assert },
    { name: 'g2-recovery', fn: g2Recovery },
    { name: 'cleanup', fn: cleanup },
    { name: 'scan', fn: scan },
  ];

  let aborted = false;
  for (const step of steps) {
    try {
      step.fn();
    } catch {
      aborted = true;
      // 即使中断也要尝试恢复和清理
      if (step.name === 'g2-outage' || step.name === 'g2-prepare') {
        try { g2Recovery(); } catch { /* 恢复尽力执行 */ }
        try { cleanup(); } catch { /* 清理尽力执行 */ }
      }
      break;
    }
  }

  if (!aborted) {
    // 正常完成时执行扫描
    try { scan(); } catch { /* 扫描失败不阻塞摘要 */ }
  }

  summary();

  if (results.failed > 0) {
    process.exit(1);
  }
}

// 主入口
const command = process.argv[2] || 'preflight';

if (command === 'preflight') {
  preflight();
} else if (command === 'run') {
  run();
} else {
  console.error(`未知子命令: ${command}`);
  console.error('可用子命令: preflight, run');
  process.exit(1);
}
