/**
 * @module e2e-registry.test
 * R03 确定性保护测试：验证 registry 跨进程保护、旧 UNKNOWN 拒绝、目标隔离和状态机。
 *
 * 运行方式：npx tsx e2e-docs/helpers/e2e-registry.test.ts
 * 不依赖 Playwright、浏览器或后端；纯函数测试。
 */
import { mkdtempSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import {
  buildTargetDigest, sameTarget, startupCheck, newSnapshot, persistRegistry,
  registerIntent, markSubmitting, markCommitted, markCommitUnknown, markCleaned,
  requireExecution,
  type RegistrySnapshot, type TargetDigest,
} from './e2e-registry'

let passed = 0
let failed = 0

function assert(condition: boolean, message: string) {
  if (condition) {
    passed++
    process.stdout.write('  ✓ ' + message + '\n')
  } else {
    failed++
    process.stdout.write('  ✗ FAIL: ' + message + '\n')
  }
}

function tempDir(): string {
  return mkdtempSync(join(tmpdir(), 'e2e-reg-test-'))
}

function digest(uuid: string, db: string, tenant: number): TargetDigest {
  return { serverUuid: uuid, database: db, tenantId: tenant, baseURL: 'http://127.0.0.1:3000' }
}

// ---- Test 1: execution=unknown 拒绝 ----
process.stdout.write('\n[Test 1] execution=unknown 拒绝\n')
{
  const dir = tempDir()
  const path = join(dir, 'registry.json')
  // 写入一个 execution=unknown 的旧 registry
  const old: RegistrySnapshot = {
    schemaVersion: 2, execution: 'unknown', attempt: '1', locale: 'zh-CN',
    targetDigest: digest('uuid-1', 'omni_srm', 1),
    entries: [], cleanup: [], retention: 'test',
  }
  persistRegistry(path, old)

  const result = startupCheck(path, digest('uuid-1', 'omni_srm', 1), 'repair-exec-1')
  assert(!result.canProceed, '旧 execution=unknown 阻止新建')
  assert(result.reason.includes('execution_unknown'), '原因包含 execution_unknown')
  rmSync(dir, { recursive: true })
}

// ---- Test 2: 不同目标不干扰 ----
process.stdout.write('\n[Test 2] 不同 serverUuid 的旧 registry 不干扰\n')
{
  const dir = tempDir()
  const path = join(dir, 'registry.json')
  const old: RegistrySnapshot = {
    schemaVersion: 2, execution: 'old-exec', attempt: '1', locale: 'zh-CN',
    targetDigest: digest('uuid-old', 'omni_srm', 1),
    entries: [{ name: 'old-resource', locale: 'zh-CN', phase: 'COMMITTED', resourceId: 1, processInstanceId: null, version: 0, createdAt: '', updatedAt: '' }],
    cleanup: [], retention: 'test',
  }
  persistRegistry(path, old)

  const result = startupCheck(path, digest('uuid-new', 'omni_srm', 1), 'new-exec')
  assert(result.canProceed, '不同 serverUuid 不阻止')
  assert(result.reason.includes('different_target'), '原因包含 different_target')
  rmSync(dir, { recursive: true })
}

// ---- Test 3: 同目标未闭合条目阻止 ----
process.stdout.write('\n[Test 3] 同目标有 INTENT 未闭合条目阻止新建\n')
{
  const dir = tempDir()
  const path = join(dir, 'registry.json')
  const d = digest('uuid-1', 'omni_srm', 1)
  const old: RegistrySnapshot = newSnapshot('old-exec', '1', 'zh-CN', d)
  registerIntent(old, 'E2ESD-test-zh-CN', 'zh-CN')
  persistRegistry(path, old)

  const result = startupCheck(path, d, 'new-exec')
  assert(!result.canProceed, '同目标有 INTENT 条目阻止新建')
  assert(result.unsettledCount === 1, '未闭合计数为 1')
  rmSync(dir, { recursive: true })
}

// ---- Test 4: 同目标全部已闭合允许继续 ----
process.stdout.write('\n[Test 4] 同目标全部已闭合允许继续\n')
{
  const dir = tempDir()
  const path = join(dir, 'registry.json')
  const d = digest('uuid-1', 'omni_srm', 1)
  const old: RegistrySnapshot = newSnapshot('old-exec', '1', 'zh-CN', d)
  registerIntent(old, 'E2ESD-old-zh-CN', 'zh-CN')
  markSubmitting(old, 'E2ESD-old-zh-CN')
  markCommitted(old, 'E2ESD-old-zh-CN', 42, 1, 'proc-1')
  markCleaned(old, 'E2ESD-old-zh-CN', 42, 'deleted')
  persistRegistry(path, old)

  const result = startupCheck(path, d, 'new-exec')
  assert(result.canProceed, '全部已闭合允许继续')
  assert(result.reason === 'all_entries_settled', '原因标记正确')
  rmSync(dir, { recursive: true })
}

// ---- Test 5: 零列表不自动解锁 ----
process.stdout.write('\n[Test 5] 零条目但有 COMMIT_UNKNOWN 仍阻止\n')
{
  const dir = tempDir()
  const path = join(dir, 'registry.json')
  const d = digest('uuid-1', 'omni_srm', 1)
  const old: RegistrySnapshot = newSnapshot('old-exec', '1', 'zh-CN', d)
  registerIntent(old, 'E2ESD-uncertain', 'zh-CN')
  markSubmitting(old, 'E2ESD-uncertain')
  markCommitUnknown(old, 'E2ESD-uncertain')
  persistRegistry(path, old)

  const result = startupCheck(path, d, 'new-exec')
  assert(!result.canProceed, 'COMMIT_UNKNOWN 阻止新建')
  assert(result.unknownCount === 1, 'UNKNOWN 计数为 1')
  rmSync(dir, { recursive: true })
}

// ---- Test 6: 清理失败保留 ----
process.stdout.write('\n[Test 6] 清理失败条目保留在 registry\n')
{
  const d = digest('uuid-1', 'omni_srm', 1)
  const snap = newSnapshot('exec-1', '1', 'zh-CN', d)
  registerIntent(snap, 'test-resource', 'zh-CN')
  markSubmitting(snap, 'test-resource')
  markCommitted(snap, 'test-resource', 99, 1, null)
  markCleaned(snap, 'test-resource', 99, 'failed')

  const entry = snap.entries.find((e) => e.name === 'test-resource')
  assert(entry !== undefined, '条目仍存在')
  assert(entry!.phase === 'CLEAN_FAILED', '阶段为 CLEAN_FAILED')
  assert(snap.cleanup.length === 1, '清理记录有 1 条')
  assert(snap.cleanup[0]!.result === 'failed', '清理结果为 failed')
}

// ---- Test 7: requireExecution 拒绝 unknown ----
process.stdout.write('\n[Test 7] requireExecution 拒绝 unknown\n')
{
  const orig = process.env.E2E_EXECUTION
  delete process.env.E2E_EXECUTION
  let threw = false
  try { requireExecution() } catch { threw = true }
  assert(threw, '未设置 E2E_EXECUTION 时抛出异常')

  process.env.E2E_EXECUTION = 'unknown'
  threw = false
  try { requireExecution() } catch { threw = true }
  assert(threw, 'E2E_EXECUTION=unknown 时抛出异常')

  process.env.E2E_EXECUTION = 'valid-exec'
  threw = false
  let value = ''
  try { value = requireExecution() } catch { threw = true }
  assert(!threw && value === 'valid-exec', '合法值正常返回')

  if (orig !== undefined) process.env.E2E_EXECUTION = orig
  else delete process.env.E2E_EXECUTION
}

// ---- Test 8: sameTarget 正确比较 ----
process.stdout.write('\n[Test 8] sameTarget 正确比较\n')
{
  const a = digest('uuid-1', 'omni_srm', 1)
  const b = digest('uuid-1', 'omni_srm', 1)
  const c = digest('uuid-2', 'omni_srm', 1)
  const d = digest('uuid-1', 'omni_workflow', 1)
  const e = digest('uuid-1', 'omni_srm', 2)

  assert(sameTarget(a, b), '相同目标返回 true')
  assert(!sameTarget(a, c), '不同 serverUuid 返回 false')
  assert(!sameTarget(a, d), '不同 database 返回 false')
  assert(!sameTarget(a, e), '不同 tenantId 返回 false')
}

// ---- Test 9: buildTargetDigest 拒绝 unknown serverUuid ----
process.stdout.write('\n[Test 9] buildTargetDigest 拒绝 unknown serverUuid\n')
{
  let threw = false
  try { buildTargetDigest('http://localhost:3000', 1, 'unknown') } catch { threw = true }
  assert(threw, 'serverUuid=unknown 时抛出异常')

  threw = false
  try { buildTargetDigest('http://localhost:3000', 1, '') } catch { threw = true }
  assert(threw, 'serverUuid 为空时抛出异常')

  process.env.E2E_DATABASE = 'omni_srm'
  const d = buildTargetDigest('http://localhost:3000', 1, 'real-uuid')
  assert(d.serverUuid === 'real-uuid', '合法 UUID 正常返回')
  assert(d.database === 'omni_srm', 'database 正确')
  delete process.env.E2E_DATABASE
}

// ---- 汇总 ----
process.stdout.write('\n' + '='.repeat(40) + '\n')
process.stdout.write(`Registry protection tests: ${passed} passed, ${failed} failed\n`)
if (failed > 0) process.exit(1)
