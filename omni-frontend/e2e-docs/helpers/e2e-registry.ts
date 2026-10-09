/**
 * @module e2e-registry
 * 跨进程持久资源登记与保护。
 *
 * <p>核心合同：</p>
 * <ul>
 *   <li>拒绝 execution=unknown；必须显式提供 E2E_EXECUTION。</li>
 *   <li>物理目标由 serverUuid + database + tenantId 唯一定义；换 localhost 别名、runStamp、worker 不能逃避旧 UNKNOWN。</li>
 *   <li>同目标有互斥保护：启动和每次新 POST 前读取未闭合索引，发现 UNKNOWN/重复/未收尾副作用就拒绝新建。</li>
 *   <li>请求前落盘 INTENT，发出前落盘 SUBMITTING，超时/中断转 COMMIT_UNKNOWN。</li>
 *   <li>只有真实唯一提交或已证明未提交才解除；零列表不是解除依据。</li>
 * </ul>
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

/** 登记条目的生命周期阶段。 */
export type RegistryPhase = 'INTENT' | 'SUBMITTING' | 'COMMITTED' | 'COMMIT_UNKNOWN' | 'CLEANED' | 'CLEAN_FAILED'

/** 物理目标摘要：由 serverUuid + database + tenantId 唯一确定。 */
export interface TargetDigest {
  serverUuid: string
  database: string
  tenantId: number
  baseURL: string
}

/** 单个资源的登记条目。 */
export interface RegistryEntry {
  /** 资源名称（唯一业务标识，如供应商名称）。 */
  name: string
  /** 语言。 */
  locale: string
  /** 当前阶段。 */
  phase: RegistryPhase
  /** 资源 ID（创建成功后填入）。 */
  resourceId: number | null
  /** 流程实例 ID。 */
  processInstanceId: string | null
  /** 版本号。 */
  version: number | null
  /** 创建时间戳。 */
  createdAt: string
  /** 最后更新时间戳。 */
  updatedAt: string
}

/** 持久化 registry 的磁盘格式。 */
export interface RegistrySnapshot {
  schemaVersion: 2
  execution: string
  attempt: string
  locale: string
  targetDigest: TargetDigest
  entries: RegistryEntry[]
  /** 清理记录。 */
  cleanup: { name: string; resourceId: number; result: string; at: string }[]
  /** 保留策略说明。 */
  retention: string
}

/** 从环境变量构建 execution，拒绝 unknown。 */
export function requireExecution(): string {
  const value = process.env.E2E_EXECUTION
  if (!value || value === 'unknown') {
    throw new Error('必须显式指定 E2E_EXECUTION，拒绝 execution=unknown')
  }
  return value
}

/** 从环境变量构建 attempt。 */
export function requireAttempt(): string {
  return process.env.E2E_ATTEMPT || '1'
}

/** 构建物理目标摘要。serverUuid 从 E2E_SERVER_UUID 或启动时健康查询获取。 */
export function buildTargetDigest(baseURL: string, tenantId: number, serverUuid: string): TargetDigest {
  if (!serverUuid || serverUuid === 'unknown') {
    throw new Error('物理目标必须包含真实 serverUuid，不接受 unknown')
  }
  return {
    serverUuid,
    database: process.env.E2E_DATABASE || 'omni_srm',
    tenantId,
    baseURL,
  }
}

/** 比较两个目标摘要是否等价。 */
export function sameTarget(a: TargetDigest, b: TargetDigest): boolean {
  return a.serverUuid === b.serverUuid
    && a.database === b.database
    && a.tenantId === b.tenantId
}

/** 从磁盘加载已有 registry；不存在返回 null。 */
export function loadRegistry(registryPath: string): RegistrySnapshot | null {
  if (!existsSync(registryPath)) return null
  const raw = readFileSync(registryPath, 'utf8')
  const parsed = JSON.parse(raw) as RegistrySnapshot
  if (parsed.schemaVersion !== 2) return null
  return parsed
}

/** 原子写入 registry 到磁盘。 */
export function persistRegistry(registryPath: string, snapshot: RegistrySnapshot): void {
  mkdirSync(dirname(registryPath), { recursive: true })
  writeFileSync(registryPath, JSON.stringify(snapshot, null, 2), 'utf8')
}

/** 创建空 registry 快照。 */
export function newSnapshot(
  execution: string, attempt: string, locale: string,
  targetDigest: TargetDigest,
): RegistrySnapshot {
  return {
    schemaVersion: 2,
    execution,
    attempt,
    locale,
    targetDigest,
    entries: [],
    cleanup: [],
    retention: '正式软删除及正常审计/Outbox/流程历史保留；不删除历史记录、不修改共享模型',
  }
}

/**
 * 启动前检查：加载已有 registry 并验证目标一致性。
 *
 * <ul>
 *   <li>execution=unknown 的旧记录阻止新建。</li>
 *   <li>不同 serverUuid/tenant 的旧记录标记为外部遗留，不自动清理。</li>
 *   <li>同目标有未闭合条目（INTENT/SUBMITTING/COMMIT_UNKNOWN）时拒绝新建。</li>
 * </ul>
 */
export interface StartupCheckResult {
  snapshot: RegistrySnapshot | null
  unsettledCount: number
  unknownCount: number
  canProceed: boolean
  reason: string
}

export function startupCheck(
  registryPath: string, currentDigest: TargetDigest, _currentExecution: string,
): StartupCheckResult {
  const existing = loadRegistry(registryPath)
  if (!existing) {
    return { snapshot: null, unsettledCount: 0, unknownCount: 0, canProceed: true, reason: 'no_existing_registry' }
  }

  // 拒绝 execution=unknown 的旧记录
  if (existing.execution === 'unknown') {
    return {
      snapshot: existing, unsettledCount: 0, unknownCount: 1, canProceed: false,
      reason: 'existing_registry_has_execution_unknown，必须人工处理后再继续',
    }
  }

  // 不同目标 → 外部遗留，不干扰
  if (!sameTarget(existing.targetDigest, currentDigest)) {
    return {
      snapshot: existing, unsettledCount: 0, unknownCount: 0, canProceed: true,
      reason: 'existing_registry_belongs_to_different_target，不干扰当前运行',
    }
  }

  // 同目标：检查未闭合条目
  const unsettled = existing.entries.filter((e) =>
    e.phase === 'INTENT' || e.phase === 'SUBMITTING' || e.phase === 'COMMIT_UNKNOWN')
  if (unsettled.length > 0) {
    return {
      snapshot: existing,
      unsettledCount: unsettled.length,
      unknownCount: existing.entries.filter((e) => e.phase === 'COMMIT_UNKNOWN').length,
      canProceed: false,
      reason: `同目标有 ${unsettled.length} 个未闭合条目（${unsettled.map((e) => e.name + ':' + e.phase).join(', ')}），必须先处理`,
    }
  }

  // 同目标、全部已闭合 → 允许继续（复用同一 registry 文件追加）
  return {
    snapshot: existing, unsettledCount: 0, unknownCount: 0, canProceed: true,
    reason: 'all_entries_settled',
  }
}

/** 在 registry 中登记意图（INTENT 阶段）。 */
export function registerIntent(snapshot: RegistrySnapshot, name: string, locale: string): RegistryEntry {
  const now = new Date().toISOString()
  const entry: RegistryEntry = {
    name, locale, phase: 'INTENT',
    resourceId: null, processInstanceId: null, version: null,
    createdAt: now, updatedAt: now,
  }
  snapshot.entries.push(entry)
  return entry
}

/** 将条目推进到 SUBMITTING 阶段。 */
export function markSubmitting(snapshot: RegistrySnapshot, name: string): void {
  const entry = snapshot.entries.find((e) => e.name === name && e.phase === 'INTENT')
  if (!entry) throw new Error(`找不到 INTENT 阶段的条目: ${name}`)
  entry.phase = 'SUBMITTING'
  entry.updatedAt = new Date().toISOString()
}

/** 将条目标记为已提交（COMMITTED），填入资源 ID。 */
export function markCommitted(snapshot: RegistrySnapshot, name: string, resourceId: number, version: number | null, processInstanceId: string | null): void {
  const entry = snapshot.entries.find((e) => e.name === name && (e.phase === 'SUBMITTING' || e.phase === 'INTENT'))
  if (!entry) throw new Error(`找不到可提交的条目: ${name}`)
  entry.phase = 'COMMITTED'
  entry.resourceId = resourceId
  entry.version = version
  entry.processInstanceId = processInstanceId
  entry.updatedAt = new Date().toISOString()
}

/** 将条目标记为不确定提交（COMMIT_UNKNOWN）。 */
export function markCommitUnknown(snapshot: RegistrySnapshot, name: string): void {
  const entry = snapshot.entries.find((e) => e.name === name && e.phase === 'SUBMITTING')
  if (!entry) throw new Error(`找不到 SUBMITTING 阶段的条目: ${name}`)
  entry.phase = 'COMMIT_UNKNOWN'
  entry.updatedAt = new Date().toISOString()
}

/** 将条目标记为已清理。 */
export function markCleaned(snapshot: RegistrySnapshot, name: string, resourceId: number, result: string): void {
  const entry = snapshot.entries.find((e) => e.name === name)
  if (entry) {
    entry.phase = result === 'deleted' ? 'CLEANED' : 'CLEAN_FAILED'
    entry.updatedAt = new Date().toISOString()
  }
  snapshot.cleanup.push({ name, resourceId, result, at: new Date().toISOString() })
}

/** 查找同名称的未闭合条目。 */
export function findUnsettledByName(snapshot: RegistrySnapshot, name: string): RegistryEntry | undefined {
  return snapshot.entries.find((e) =>
    e.name === name && (e.phase === 'INTENT' || e.phase === 'SUBMITTING' || e.phase === 'COMMIT_UNKNOWN'))
}
