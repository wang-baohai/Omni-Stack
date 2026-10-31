/**
 * @module utils/application-navigation
 * 应用导航纯函数：把已授权菜单树按目录祖先转换为「应用 / 模块菜单 / 可解析页面」。
 * 无请求、无存储、无路由单例副作用；视图可解析性与标题映射经参数注入，便于单元测试复用。
 * 说明：MenuNode.path 是权限树物化路径而非前端 URL，路由路径统一由 permissionCode 派生。
 */
import type { MenuNode } from '@/api/menu'

/** 视图可解析判定；生产由路由组件映射注入，测试传入固定集合。 */
export type ViewResolver = (permissionCode: string) => boolean

/** 可解析页面：真实存在视图且内部控制台可见的 MENU 叶子。 */
export interface NavigationPage {
  /** 稳定权限 ID（后端 sys_permission.id） */
  id: number
  /** 权限编码，例如 base:operlog */
  permissionCode: string
  /** 前端可解析路由路径，例如 /admin/base/operlog；由 permissionCode 派生，非 MenuNode.path */
  routePath: string
  /** 展示标题的 i18n 键；无映射时为 null，交由消费方回退 */
  titleKey: string | null
  /** 回退标题（数据库 permissionName） */
  fallbackTitle: string
  /** 所属应用的稳定 ID（顶层祖先） */
  appId: number
  /** 所属应用权限编码（顶层祖先） */
  appCode: string
  /** 从应用根到直接父目录的祖先权限编码链，不含应用根本身 */
  groupPath: string[]
  /** 排序值，用于稳定排序 */
  sort: number
}

/** 应用内模块菜单分组；仅保留至少含一个可解析页面的目录。 */
export interface NavigationGroup {
  id: number
  permissionCode: string
  groups: NavigationGroup[]
  pages: NavigationPage[]
}

/** 由顶层目录祖先聚合出的应用模块卡片。 */
export interface NavigationModule {
  id: number
  permissionCode: string
  titleKey: string | null
  fallbackTitle: string
  sort: number
  groups: NavigationGroup[]
  pages: NavigationPage[]
  /** 该应用下可解析页面总数（含各层分组叶子） */
  leafCount: number
}

/** 被排除项的分类审计记录，不进入可点击结构。 */
export interface NavigationExclusions {
  /** MENU 无对应可解析视图组件 */
  noComponent: string[]
  /** 内部控制台不可见的门户项 */
  portalHidden: string[]
  /** 排除后不再含任何可解析页面的目录 */
  emptyDirectory: string[]
  /** 非 DIRECTORY/MENU 类型节点 */
  ignoredType: string[]
}

/** 转换结果。 */
export interface NavigationResult {
  modules: NavigationModule[]
  allPages: NavigationPage[]
  exclusions: NavigationExclusions
}

/** 转换选项。 */
export interface NavigationOptions {
  /** 内部控制台不可见的门户权限前缀，默认与路由动态注册一致排除 srm:portal: */
  portalHiddenPrefixes?: readonly string[]
  /** 权限编码到 i18n 键映射；纯函数不直接依赖 constants/menu 以便测试注入 */
  titleMap?: Record<string, string>
}

/** 与 router registerMenuRoute 的门户排除保持一致的默认前缀。 */
const DEFAULT_PORTAL_HIDDEN_PREFIXES: readonly string[] = ['srm:portal:']

/** 内部转换上下文：注入判定、映射、累加的排除表与当前应用归属。 */
interface TransformContext {
  resolver: ViewResolver
  portalHiddenPrefixes: readonly string[]
  titleMap: Record<string, string>
  exclusions: NavigationExclusions
  appId: number
  appCode: string
}

/** 由 permissionCode 派生管理端子路由路径，与 registerMenuRoute 的 path 规则一致。 */
function toRoutePath(permissionCode: string): string {
  return `/admin/${permissionCode.replace(/:/g, '/')}`
}

/** 稳定排序比较器；仅用于副本数组，避免修改输入树。 */
function bySort(a: MenuNode, b: MenuNode): number {
  return a.sort - b.sort
}

/** 统计一组页面与子分组下的叶子总数。 */
function countPages(pages: NavigationPage[], groups: NavigationGroup[]): number {
  return pages.length + groups.reduce((sum, g) => sum + countPages(g.pages, g.groups), 0)
}

/** 深度收集某结构内的全部页面。 */
function collectPages(node: { pages: NavigationPage[]; groups: NavigationGroup[] }): NavigationPage[] {
  const out: NavigationPage[] = []
  const walk = (cur: { pages: NavigationPage[]; groups: NavigationGroup[] }): void => {
    out.push(...cur.pages)
    for (const g of cur.groups) walk(g)
  }
  walk(node)
  return out
}

/** 处理一个目录子树，返回分组；排除后无有效叶子则登记空目录并返回 null。 */
function buildGroup(node: MenuNode, ancestors: string[], ctx: TransformContext): NavigationGroup | null {
  const groups: NavigationGroup[] = []
  const pages: NavigationPage[] = []
  const children = [...node.children].sort(bySort)
  for (const child of children) {
    collectInto(child, ancestors, ctx, groups, pages)
  }
  if (groups.length === 0 && pages.length === 0) {
    ctx.exclusions.emptyDirectory.push(node.permissionCode)
    return null
  }
  return { id: node.id, permissionCode: node.permissionCode, groups, pages }
}

/** 将单个子节点归入分组/页面，或按原因登记排除；绝不写回输入节点。 */
function collectInto(
  node: MenuNode,
  ancestors: string[],
  ctx: TransformContext,
  groups: NavigationGroup[],
  pages: NavigationPage[],
): void {
  if (node.type === 'DIRECTORY') {
    const sub = buildGroup(node, [...ancestors, node.permissionCode], ctx)
    if (sub) groups.push(sub)
    return
  }
  if (node.type !== 'MENU') {
    ctx.exclusions.ignoredType.push(`${node.permissionCode}:${node.type}`)
    return
  }
  if (ctx.portalHiddenPrefixes.some((prefix) => node.permissionCode.startsWith(prefix))) {
    ctx.exclusions.portalHidden.push(node.permissionCode)
    return
  }
  if (!ctx.resolver(node.permissionCode)) {
    ctx.exclusions.noComponent.push(node.permissionCode)
    return
  }
  pages.push({
    id: node.id,
    permissionCode: node.permissionCode,
    routePath: toRoutePath(node.permissionCode),
    titleKey: ctx.titleMap[node.permissionCode] ?? null,
    fallbackTitle: node.permissionName,
    appId: ctx.appId,
    appCode: ctx.appCode,
    groupPath: ancestors,
    sort: node.sort,
  })
}

/** 将单个顶层节点转换为应用模块；无可解析页面时不生成可点空卡片。 */
function buildModule(root: MenuNode, ctx: TransformContext): NavigationModule | null {
  const groups: NavigationGroup[] = []
  const pages: NavigationPage[] = []

  if (root.type === 'MENU') {
    collectInto(root, [], ctx, groups, pages)
  } else if (root.type === 'DIRECTORY') {
    const children = [...root.children].sort(bySort)
    for (const child of children) {
      collectInto(child, [], ctx, groups, pages)
    }
  } else {
    ctx.exclusions.ignoredType.push(`${root.permissionCode}:${root.type}`)
    return null
  }

  const leafCount = countPages(pages, groups)
  if (leafCount === 0) {
    if (root.type === 'DIRECTORY') ctx.exclusions.emptyDirectory.push(root.permissionCode)
    return null
  }

  return {
    id: root.id,
    permissionCode: root.permissionCode,
    titleKey: ctx.titleMap[root.permissionCode] ?? null,
    fallbackTitle: root.permissionName,
    sort: root.sort,
    groups,
    pages,
    leafCount,
  }
}

/**
 * 把已授权菜单树按目录祖先转换为应用、模块菜单与可解析页面。
 *
 * 纯函数：不修改入参树、不发请求、不读写存储、不引用路由单例。
 * 归属判定跟随 DIRECTORY 祖先链，绝不按权限码首段切分模块，也不使用 MenuNode.path 作为 URL。
 *
 * @param menuTree 已授权菜单树（顶层数组，节点含 children）
 * @param resolver 视图可解析判定（生产由路由组件映射注入）
 * @param options 门户隐藏前缀与标题映射
 */
export function buildApplicationNavigation(
  menuTree: readonly MenuNode[],
  resolver: ViewResolver,
  options: NavigationOptions = {},
): NavigationResult {
  const exclusions: NavigationExclusions = {
    noComponent: [],
    portalHidden: [],
    emptyDirectory: [],
    ignoredType: [],
  }
  const ctxBase = {
    resolver,
    portalHiddenPrefixes: options.portalHiddenPrefixes ?? DEFAULT_PORTAL_HIDDEN_PREFIXES,
    titleMap: options.titleMap ?? {},
  }
  const modules: NavigationModule[] = []

  const roots = [...menuTree].sort(bySort)
  for (const root of roots) {
    const ctx: TransformContext = { ...ctxBase, exclusions, appId: root.id, appCode: root.permissionCode }
    const mod = buildModule(root, ctx)
    if (mod) modules.push(mod)
  }

  const allPages: NavigationPage[] = []
  for (const mod of modules) allPages.push(...collectPages(mod))

  return { modules, allPages, exclusions }
}

/** S02 装饰解析器：集中提供图标与 Wiki 入口，缺失即 null，绝不伪造 overview、假路由或授权卡片。 */
export interface NavigationDecorators {
  /** permissionCode → Element Plus 图标名（生产注入 router iconMap）；未命中返回 null。 */
  iconResolver?: (permissionCode: string) => string | null
  /** permissionCode → 已登记 Wiki 文档路由；未登记返回 null，不合成假入口。 */
  wikiResolver?: (permissionCode: string) => string | null
}

/** 可点击进入的目标；模块卡片与叶子页共用此形状。 */
export interface NavigationEntry {
  kind: 'module' | 'page'
  /** 稳定权限 ID */
  id: number
  permissionCode: string
  /** 所属应用稳定 ID */
  appId: number
  /** 所属应用权限编码 */
  appCode: string
  /** 标题 i18n 键；无映射为 null，交由消费方回退 fallbackTitle */
  titleKey: string | null
  fallbackTitle: string
  /** 图标名；未登记为 null，不伪造 */
  icon: string | null
  /** 真实可进入路由；无可用叶子时为 null（不生成假 overview/卡片） */
  routePath: string | null
  /** 是否存在真实可进入目标 */
  enterable: boolean
  /** 已登记 Wiki 文档路由；未登记为 null */
  wikiPath: string | null
  /** 排序值，用于稳定展示 */
  sort: number
}

/**
 * 从已授权派生页选择搜索范围，不按 URL 前缀猜测应用归属。
 *
 * @param entries 当前身份的导航叶子；不追加其他来源或缓存旧身份结果
 * @param appId undefined 为工作台全局；null/非法编号为未知应用并拒绝全部；正整数为当前应用
 * @returns 可进入且属于指定范围的菜单副本
 */
export function filterNavigationSearchPages(
  entries: readonly NavigationEntry[],
  appId: number | null | undefined,
): NavigationEntry[] {
  if (appId !== undefined && (appId === null || !Number.isSafeInteger(appId) || appId <= 0)) return []
  return entries.filter((entry) => entry.kind === 'page' && entry.enterable &&
    entry.routePath !== null && entry.routePath !== '' && (appId === undefined || entry.appId === appId))
}

/** 装饰后的派生导航：顶层模块卡片 + 全部叶子页。 */
export interface DecoratedNavigation {
  modules: NavigationEntry[]
  pages: NavigationEntry[]
}

/** 取模块内 sort 最小（并列取 id 最小）的真实叶子作为目录默认入口；无叶子返回 null。 */
function pickDefaultLeaf(mod: NavigationModule): NavigationPage | null {
  const leaves = collectPages(mod)
  let best: NavigationPage | null = null
  for (const p of leaves) {
    if (
      best === null ||
      p.sort < best.sort ||
      (p.sort === best.sort && p.id < best.id)
    ) {
      best = p
    }
  }
  return best
}

/** 由叶子页构造可进入目标；routePath 已由 S01 依 permissionCode 派生。 */
function toPageEntry(page: NavigationPage, d: NavigationDecorators): NavigationEntry {
  return {
    kind: 'page',
    id: page.id,
    permissionCode: page.permissionCode,
    appId: page.appId,
    appCode: page.appCode,
    titleKey: page.titleKey,
    fallbackTitle: page.fallbackTitle,
    icon: d.iconResolver ? d.iconResolver(page.permissionCode) : null,
    routePath: page.routePath,
    enterable: true,
    wikiPath: d.wikiResolver ? d.wikiResolver(page.permissionCode) : null,
    sort: page.sort,
  }
}

/** 由应用模块构造可进入卡片：入口选首个真实可用叶子；无叶子返回 null，不出空卡片。 */
function toModuleEntry(mod: NavigationModule, d: NavigationDecorators): NavigationEntry | null {
  const leaf = pickDefaultLeaf(mod)
  if (!leaf) return null
  return {
    kind: 'module',
    id: mod.id,
    permissionCode: mod.permissionCode,
    appId: mod.id,
    appCode: mod.permissionCode,
    titleKey: mod.titleKey,
    fallbackTitle: mod.fallbackTitle,
    icon: d.iconResolver ? d.iconResolver(mod.permissionCode) : null,
    // 目录入口指向真实叶子路由，绝不合成 /admin/<module>/overview
    routePath: leaf.routePath,
    enterable: true,
    wikiPath: d.wikiResolver ? d.wikiResolver(mod.permissionCode) : null,
    sort: mod.sort,
  }
}

/**
 * 在 S01 转换结果之上集中解析名称、图标、真实路由、默认可进入页面与 Wiki 入口。
 *
 * 纯函数：不引用 router 单例、不发请求、不读写存储；图标与 Wiki 经参数注入，缺失返回 null。
 * 模块默认入口选首个真实可用叶子；S01 已排除无叶子目录，此处仍防御性不生成不可进入空卡片。
 * 绝不合成不存在的 overview、假菜单路径或硬编码授权卡片。
 */
export function decorateNavigation(
  result: NavigationResult,
  decorators: NavigationDecorators = {},
): DecoratedNavigation {
  const modules: NavigationEntry[] = []
  const pages: NavigationEntry[] = []
  for (const mod of result.modules) {
    const moduleEntry = toModuleEntry(mod, decorators)
    if (moduleEntry) modules.push(moduleEntry)
    for (const page of collectPages(mod)) pages.push(toPageEntry(page, decorators))
  }
  return { modules, pages }
}
