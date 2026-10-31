/**
 * @module stores/workspace
 * @description 工作台概览统计的按卡取数状态机。
 *
 * 每张卡先判定「本次部署是否包含该模块 + 当前账号是否具备该口径」再决定是否发请求，
 * 判定不通过时状态为 `unavailable`，绝不发出该业务请求（缺模块的部署里这类请求只会得到 404/503 噪音）；
 * 请求成功且各项均为 0 记为 `empty`，字段缺失或非有限数记为 `null` 值交卡片渲染占位符，
 * 请求失败或响应形态不可用记为 `failed` 并保留归一化错误描述符；
 * 任何分支都不会把请求异常写成成功的 0，也不新增或改走后端聚合接口。
 * 卡与卡彼此独立：同一卡并发调用复用进行中的请求，重试只重发本卡，不触碰菜单也不清空其他卡。
 *
 * 收藏与最近访问是同一条「租户 + 稳定用户」作用域下的响应式记录，工作台概览与应用抽屉共用本 store 读同一份数据：
 * 持久化内容只有应用/菜单的稳定 ID 与顺序，标题、路径、令牌一律不入库；
 * 作用域键只由 JWT 的 `tenant_id` 与 `sub` 派生，用户名可改可重名故不参与键；
 * 身份缺失（未登录、令牌无法解码、缺少任一 claim）时作用域键为 `null`，既不读也不写任何共享或公共键；
 * 本 store 只做记录与稳定排序，「收藏是否可见」由消费方用同一份已授权导航集合求交决定，收藏本身不赋予任何权限。
 * 最近访问的唯一写入方是路由的访问成功钩子：它先确认导航成功，再把已授权菜单树与该路由的权限码交给 `recordVisit`，
 * 本 store 因此既不发菜单请求也不做授权判定，只把匹配到的菜单叶子稳定 ID 记进当前身份的作用域键。
 */
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { getMyJobStats } from '@/api/myJob'
import { getWorkspaceStats } from '@/api/workflow'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { describeError } from '@/utils/errors'
import { getTenantIdFromToken, getUserIdFromToken } from '@/utils/jwt'
import type { ErrorDisplay } from '@/utils/error-display'

/**
 * 收藏与最近访问的持久化记录：两个数组都只存导航条目的稳定 ID（`sys_permission.id` 的字符串形式）。
 * 收藏按加入先后稳定排列，最近访问按访问先后倒序排列。
 */
export interface WorkspacePreferences {
  favorites: string[]
  recents: string[]
}

/** 最近访问上限：去重后只保留最近若干项，写入与读取两侧共用同一个常量。 */
export const RECENT_LIMIT = 12

/** 存储键前缀：键名本身带完整作用域，跨账号跨租户不可能互相命中。 */
const PREFERENCE_STORAGE_PREFIX = 'omni-workspace-preferences'

/** 空记录：身份缺失、存储缺失或内容损坏时的统一回退，不携带任何历史 ID。 */
function emptyPreferences(): WorkspacePreferences {
  return { favorites: [], recents: [] }
}

/** 只接受非空字符串 ID 并按原顺序去重：非数组、非字符串项与重复项在读取阶段即丢弃。 */
function readIds(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  const out: string[] = []
  for (const item of value) {
    if (typeof item !== 'string' || item === '' || out.includes(item)) continue
    out.push(item)
  }
  return out
}

/** 解析存储值：损坏 JSON、非对象形态与非法字段一律降级为空记录，绝不回落到别的键或公共命名空间。 */
function parsePreferences(raw: string | null): WorkspacePreferences {
  if (raw === null) return emptyPreferences()
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return emptyPreferences()
  }
  if (parsed === null || typeof parsed !== 'object') return emptyPreferences()
  const source = parsed as Record<string, unknown>
  return { favorites: readIds(source.favorites), recents: readIds(source.recents) }
}

/**
 * 访问登记只看菜单节点的这三个字段与子节点。
 * 用结构化局部类型而不是引入菜单 API 类型，偏好 store 因此不获得任何菜单加载或授权能力。
 */
interface VisitMenuNode {
  readonly id: number
  readonly type: string
  readonly permissionCode: string
  readonly children?: readonly VisitMenuNode[]
}

/**
 * 在已授权菜单树里按权限码定位可登记的菜单叶子。
 * 只接受 `MENU` 类型且带正整数稳定 ID 的节点：目录、异常 ID 与树外权限码（如直接输入地址的未授权页）一律返回 `null`，
 * 因此最近访问里不会出现假页面，也不会因为一条路由记录就获得访问权。
 *
 * @param menus 当前账号的已授权菜单树
 * @param permissionCode 成功抵达的路由所携带的权限码
 * @returns 匹配到的菜单叶子；不存在或不可登记时为 `null`
 */
export function findVisitMenuLeaf(
  menus: readonly VisitMenuNode[],
  permissionCode: string,
): VisitMenuNode | null {
  for (const node of menus) {
    if (
      node.permissionCode === permissionCode &&
      node.type === 'MENU' &&
      Number.isInteger(node.id) &&
      node.id > 0
    ) {
      return node
    }
    const child = node.children === undefined ? null : findVisitMenuLeaf(node.children, permissionCode)
    if (child !== null) return child
  }
  return null
}

/** 统计卡标识：与已有的两个统计接口一一对应。 */
export type WorkspaceStatsCardId = 'jobs' | 'workflow'

/**
 * 单卡状态。`unavailable` 表示判定未通过因而没有发请求，
 * `empty` 表示请求成功且数值全为 0，二者都与 `failed` 严格区分。
 */
export type WorkspaceStatsStatus = 'idle' | 'unavailable' | 'loading' | 'loaded' | 'empty' | 'failed'

/** 单项指标：`value` 为 `null` 表示响应里没有可用数值，渲染为占位符而不是 0。 */
export interface WorkspaceStatsMetric {
  /** 稳定标识，取响应字段名。 */
  id: string
  /** 指标名词条 key。 */
  labelKey: string
  /** 指标值；不可用时为 `null`。 */
  value: number | null
}

/** 单卡状态快照。 */
export interface WorkspaceStatsCardState {
  /** 当前状态。 */
  status: WorkspaceStatsStatus
  /** 指标集合，仅 `loaded`/`empty` 时非空。 */
  metrics: WorkspaceStatsMetric[]
  /** 失败时的归一化错误描述符；本地异常或响应形态不可用时为 `null`。 */
  display: ErrorDisplay | null
}

/** workflow 模块权限码根：种子数据里该模块的权限树根编码为 `workflow`，预设裁剪会整棵移除缺失模块的权限。 */
const WORKFLOW_PERMISSION_ROOT = 'workflow'

/** 生成只带状态的空白卡快照。 */
function blankCard(status: WorkspaceStatsStatus): WorkspaceStatsCardState {
  return { status, metrics: [], display: null }
}

/** 读取有限数值；字段缺失、非数字或非有限值一律返回 `null`，不参与零值判定。 */
function readCount(source: Record<string, unknown>, key: string): number | null {
  const value = source[key]
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

/**
 * 工作台 store。
 * 统计部分只调用已有接口；偏好部分只在当前身份作用域内读写本地记录，两者都不参与授权判定。
 */
export const useWorkspaceStore = defineStore('workspace', () => {
  /** 两张卡的状态集合。 */
  const cards = ref<Record<WorkspaceStatsCardId, WorkspaceStatsCardState>>({
    jobs: blankCard('idle'),
    workflow: blankCard('idle'),
  })

  /** 进行中的请求：同一张卡的并发调用复用一个 Promise。 */
  const inFlight = new Map<WorkspaceStatsCardId, Promise<void>>()

  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  /** 任务统计判定：`/base/my-job/stats` 属基础模块（恒在）且只需登录态，无权限码要求，故未登录即不发请求。 */
  const jobsAllowed = computed(() => userStore.token !== '')

  /**
   * 流程统计判定：前端可观测的「该部署包含 workflow 且当前账号可见」证据只有该模块的权限码，
   * 因此以 `workflow` 根码或其 `workflow:` 子码是否存在为准，不猜模块清单也不合成兜底。
   */
  const workflowAllowed = computed(() =>
    permissionStore.permissions.some(
      (code) => code === WORKFLOW_PERMISSION_ROOT || code.startsWith(`${WORKFLOW_PERMISSION_ROOT}:`),
    ),
  )

  /** 按卡取可见性判定结果。 */
  function isAllowed(cardId: WorkspaceStatsCardId): boolean {
    return cardId === 'jobs' ? jobsAllowed.value : workflowAllowed.value
  }

  /** 把已知响应字段映射为指标；响应体不是可用对象或全部字段不可用时返回 `null` 交调用方判为失败。 */
  function metricsOf(cardId: WorkspaceStatsCardId, payload: unknown): WorkspaceStatsMetric[] | null {
    if (payload === null || typeof payload !== 'object') return null
    const source = payload as Record<string, unknown>
    const metrics: WorkspaceStatsMetric[] = cardId === 'jobs'
      ? [
        { id: 'totalJobs', labelKey: 'workspace.stats.totalJobs', value: readCount(source, 'totalJobs') },
        { id: 'todayExecuted', labelKey: 'workspace.stats.todayExecuted', value: readCount(source, 'todayExecuted') },
        { id: 'todayFailed', labelKey: 'workspace.stats.todayFailed', value: readCount(source, 'todayFailed') },
      ]
      : [
        { id: 'todoCount', labelKey: 'workflow.todoCount', value: readCount(source, 'todoCount') },
        { id: 'myInitiatedRunning', labelKey: 'workspace.stats.myInitiatedRunning', value: readCount(source, 'myInitiatedRunning') },
        { id: 'myInitiatedTotal', labelKey: 'workspace.stats.myInitiatedTotal', value: readCount(source, 'myInitiatedTotal') },
      ]
    return metrics.some((metric) => metric.value !== null) ? metrics : null
  }

  /**
   * 载入单张卡：先判定模块与权限，再决定是否发请求；进行中的请求直接复用。
   *
   * @param cardId 统计卡标识
   * @returns 该卡本轮取数的结束 Promise
   */
  function loadCard(cardId: WorkspaceStatsCardId): Promise<void> {
    const existing = inFlight.get(cardId)
    if (existing !== undefined) return existing
    if (!isAllowed(cardId)) {
      cards.value[cardId] = blankCard('unavailable')
      return Promise.resolve()
    }
    cards.value[cardId] = blankCard('loading')
    const task = performLoad(cardId).finally(() => {
      inFlight.delete(cardId)
    })
    inFlight.set(cardId, task)
    return task
  }

  /** 实际取数：只调用本卡对应的既有接口，失败只写本卡状态，不影响另一张卡。 */
  async function performLoad(cardId: WorkspaceStatsCardId): Promise<void> {
    try {
      const response = cardId === 'jobs' ? await getMyJobStats() : await getWorkspaceStats()
      const metrics = metricsOf(cardId, response.data.data)
      if (metrics === null) {
        cards.value[cardId] = blankCard('failed')
        return
      }
      const isEmpty = metrics.every((metric) => metric.value === 0)
      cards.value[cardId] = { status: isEmpty ? 'empty' : 'loaded', metrics, display: null }
    } catch (error) {
      cards.value[cardId] = { status: 'failed', metrics: [], display: describeError(error) }
    }
  }

  /** 当前身份的偏好作用域键；任一 claim 缺失、非有限数或令牌不可解码时为 `null`。 */
  const preferenceKey = computed<string | null>(() => {
    const token = userStore.token
    if (token === '') return null
    const userId = getUserIdFromToken(token)
    const tenantId = getTenantIdFromToken(token)
    if (userId === null || tenantId === null) return null
    if (!Number.isFinite(userId) || !Number.isFinite(tenantId)) return null
    return `${PREFERENCE_STORAGE_PREFIX}:${tenantId}:${userId}`
  })

  /** 内存中的偏好记录：工作台与抽屉读同一个引用，因此两处天然一致。 */
  const preferences = ref<WorkspacePreferences>(emptyPreferences())

  /** 已加载到内存的作用域键，防止身份未变时重复读盘，也防止把上一个身份的列表写进新身份的键。 */
  const loadedKey = ref<string | null>(null)

  /**
   * 写入本地记录：只在作用域键存在时落盘，键为 `null` 时内存改动不外泄到任何共享键。
   * 落盘内容仅有两个 ID 数组，顺序即排序结果。
   */
  function persistPreferences(next: WorkspacePreferences): void {
    preferences.value = next
    const key = preferenceKey.value
    if (key === null) return
    loadedKey.value = key
    localStorage.setItem(key, JSON.stringify({ version: 1, favorites: next.favorites, recents: next.recents }))
  }

  /**
   * 载入当前身份的收藏与最近访问：同一作用域键已加载过则原样复用。
   * 身份缺失时清空内存记录并保持未加载标记，不读取任何键，也不返回别人的记录。
   */
  function loadPreferences(): void {
    const key = preferenceKey.value
    if (key === null) {
      preferences.value = emptyPreferences()
      loadedKey.value = null
      return
    }
    if (loadedKey.value === key) return
    preferences.value = parsePreferences(localStorage.getItem(key))
    loadedKey.value = key
  }

  /** 身份变化即丢弃上一份内存记录并按新键重载；新身份无效时停在空记录而非公共命名空间。 */
  watch(preferenceKey, () => {
    preferences.value = emptyPreferences()
    loadedKey.value = null
    loadPreferences()
  })

  /** 当前内存记录里是否已收藏该稳定 ID；可见性判断留给消费方，收藏不代表可访问。 */
  function isFavorite(id: string): boolean {
    return preferences.value.favorites.includes(id)
  }

  /**
   * 收藏或取消收藏：新增追加到末尾、移除保留其余相对顺序，因此排序稳定。
   *
   * @param id 导航条目的稳定 ID
   * @returns 操作后的收藏状态；身份缺失或 ID 为空时不改动记录并返回 `false`
   */
  function toggleFavorite(id: string): boolean {
    if (preferenceKey.value === null || id === '') return false
    const current = preferences.value.favorites
    if (current.includes(id)) {
      persistPreferences({ ...preferences.value, favorites: current.filter((item) => item !== id) })
      return false
    }
    persistPreferences({ ...preferences.value, favorites: [...current, id] })
    return true
  }

  /**
   * 记录一次访问：先去除旧位置再把该 ID 放到最前，超过 RECENT_LIMIT 的旧项被裁掉。
   * 身份缺失或 ID 为空时记录不变，调用方（S04 的成功导航钩子）无需再判空。
   *
   * @param id 导航条目的稳定 ID
   */
  function recordRecent(id: string): void {
    if (preferenceKey.value === null || id === '') return
    const recents = [id, ...preferences.value.recents.filter((item) => item !== id)].slice(0, RECENT_LIMIT)
    persistPreferences({ ...preferences.value, recents })
  }

  /**
   * 登记一次访问：由路由的访问成功钩子调用，只认已授权菜单里真实存在的 `MENU` 叶子。
   * 先按当前身份载入记录，新标签首访因此是在已存储历史上追加而不是用空列表覆盖；
   * 身份缺失、权限码缺失或该码在菜单树里没有可登记叶子时直接返回 `false` 且不写任何存储，
   * 所以认证回调、403/404 兜底、不存在路径与无权页面都进不了最近访问，登记内容也只有稳定 ID。
   *
   * @param menus 当前账号的已授权菜单树，由调用方从权限 Store 取得
   * @param permissionCode 成功抵达路由携带的权限码，静态路由为 `undefined`
   * @returns 是否写入了这条访问记录
   */
  function recordVisit(
    menus: readonly VisitMenuNode[],
    permissionCode: string | null | undefined,
  ): boolean {
    if (preferenceKey.value === null) return false
    if (typeof permissionCode !== 'string' || permissionCode === '') return false
    const leaf = findVisitMenuLeaf(menus, permissionCode)
    if (leaf === null) return false
    loadPreferences()
    recordRecent(String(leaf.id))
    return true
  }

  return {
    cards,
    jobsAllowed,
    workflowAllowed,
    loadCard,
    preferences,
    preferenceKey,
    favoriteIds: computed(() => preferences.value.favorites),
    recentIds: computed(() => preferences.value.recents),
    loadPreferences,
    isFavorite,
    toggleFavorite,
    recordRecent,
    recordVisit,
  }
})
