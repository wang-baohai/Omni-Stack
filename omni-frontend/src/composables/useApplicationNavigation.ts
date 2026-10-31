/**
 * @module composables/useApplicationNavigation
 * 统一的应用导航派生入口。
 * 以当前权限 Store 的菜单树为唯一来源，复用 application-navigation 的纯转换与装饰，
 * 为卡片、抽屉、搜索与侧栏提供同一份派生集合，避免各组件各自 fetch/filter 或缓存旧账号数据。
 */
import { computed, type ComputedRef, type Ref } from 'vue'
import { storeToRefs } from 'pinia'
import { usePermissionStore, type MenuLoadState } from '@/stores/permission'
import { menuI18nMap } from '@/constants/menu'
import { iconMap, resolveViewComponent } from '@/router'
import {
  buildApplicationNavigation,
  decorateNavigation,
  type NavigationEntry,
  type NavigationExclusions,
  type ViewResolver,
} from '@/utils/application-navigation'

/** 共享派生结果：所有消费方读取同一组 computed，保证同账号同树得到一致集合。 */
export interface ApplicationNavigationViewModel {
  /** 模块级可进入卡片（默认入口指向首个真实叶子，无叶子不出现空卡片）。 */
  modules: ComputedRef<NavigationEntry[]>
  /** 全部可进入叶子页，供搜索与抽屉使用。 */
  pages: ComputedRef<NavigationEntry[]>
  /** 被排除项审计（无组件、门户隐藏、空目录、非法类型），来自同一次转换。 */
  exclusions: ComputedRef<NavigationExclusions>
  /** 菜单加载状态，透传 Store，供消费方区分未加载/加载失败。 */
  menuLoadState: Ref<MenuLoadState>
  /** 按所属应用稳定 ID 取该模块的菜单叶子集合。 */
  pagesOfApp: (appId: number) => NavigationEntry[]
}

/**
 * 使用当前账号已授权菜单派生应用导航视图模型。
 *
 * @returns 共享的响应式派生入口
 */
export function useApplicationNavigation(): ApplicationNavigationViewModel {
  const permissionStore = usePermissionStore()
  const { menuTree, menuLoadState } = storeToRefs(permissionStore)

  // 真实可进入性直接复用路由视图解析，避免用权限码首段或 MenuNode.path 冒充目标；
  // 找不到视图的菜单不会进入结果，也不会被兜底成假页面。
  const resolver: ViewResolver = (permissionCode: string) =>
    resolveViewComponent(permissionCode) !== undefined

  const navigation = computed(() =>
    buildApplicationNavigation(menuTree.value, resolver, { titleMap: menuI18nMap }),
  )

  // 图标来源单一（router 导出的 iconMap），未登记即 null，不伪造；当前无已登记 Wiki 路径，故不提供 wikiResolver。
  const decorated = computed(() =>
    decorateNavigation(navigation.value, {
      iconResolver: (permissionCode: string) => iconMap[permissionCode] ?? null,
    }),
  )

  const pagesByApp = computed(() => {
    const map = new Map<number, NavigationEntry[]>()
    for (const page of decorated.value.pages) {
      const bucket = map.get(page.appId)
      if (bucket) bucket.push(page)
      else map.set(page.appId, [page])
    }
    return map
  })

  return {
    modules: computed(() => decorated.value.modules),
    pages: computed(() => decorated.value.pages),
    exclusions: computed(() => navigation.value.exclusions),
    menuLoadState,
    pagesOfApp: (appId: number) => pagesByApp.value.get(appId) ?? [],
  }
}
