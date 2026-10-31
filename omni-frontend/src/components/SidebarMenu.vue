<script setup lang="ts">
/**
 * @module components/SidebarMenu
 * 模块侧栏菜单组件（递归）。
 * 支持任意深度的嵌套（DIRECTORY → DIRECTORY → ... → MENU），通过递归调用自身渲染多级子菜单。
 * 可见性、路由路径、标题与图标一律取自父级传入的同一份派生导航结果（visiblePages）：
 * 不在该集合内的菜单不渲染，因此裁剪预设下不会出现指向未装配视图的死链，
 * 组件内也不按权限码前缀或本地映射表推断归属，避免与应用卡片、抽屉、菜单搜索产生分歧。
 */
import { computed } from 'vue'
import type { MenuNode } from '@/api/menu'
import type { NavigationEntry } from '@/utils/application-navigation'
import { menuI18nMap } from '@/constants/menu'
import { iconMap } from '@/router'
import { useI18n } from 'vue-i18n'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { isAssetSelfServiceUser } from '@/utils/access'
import { getRolesFromToken } from '@/utils/jwt'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const userStore = useUserStore()

const props = defineProps<{
  /** 当前层级的菜单节点列表；由模块布局限定为当前模块的子树 */
  nodes: MenuNode[]
  /** 共享派生结果中该模块的可进入页面，按权限码索引；不在此集合内的节点不渲染 */
  visiblePages: ReadonlyMap<string, NavigationEntry>
}>()

const assetSelfServiceOnly = computed(() => isAssetSelfServiceUser(
  permissionStore.permissions,
  getRolesFromToken(userStore.token),
))

/** 取派生页面；目录节点与未派生出的菜单返回 undefined。 */
function pageOf(permissionCode: string): NavigationEntry | undefined {
  return props.visiblePages.get(permissionCode)
}

/** 节点子树内是否存在派生结果认可的可见叶子；目录据此决定是否渲染。 */
function hasVisibleLeaf(node: MenuNode): boolean {
  if (node.type === 'MENU') return props.visiblePages.has(node.permissionCode)
  if (node.type !== 'DIRECTORY') return false
  return node.children.some(hasVisibleLeaf)
}

/** 菜单项标题：优先派生结果的 i18n 键，未登记回退后端名称；资产自助视角保留现行专属文案。 */
function getMenuLabel(node: MenuNode): string {
  if (node.permissionCode === 'asset:asset' && assetSelfServiceOnly.value) {
    return t('common.assetMyAssets')
  }
  const page = pageOf(node.permissionCode)
  if (page) return page.titleKey ? t(page.titleKey) : page.fallbackTitle
  const key = menuI18nMap[node.permissionCode]
  return key ? t(key) : node.permissionName
}

/** 目录标题：目录不进入派生页面集合，仍按 i18n 映射命中，未登记回退后端名称。 */
function getDirectoryLabel(node: MenuNode): string {
  const key = menuI18nMap[node.permissionCode]
  return key ? t(key) : node.permissionName
}

/** 菜单项图标沿用路由导出的单一 iconMap（派生结果已带出），未登记时回落通用图标。 */
function getMenuIcon(permissionCode: string): string {
  return pageOf(permissionCode)?.icon ?? iconMap[permissionCode] ?? 'Document'
}

/** 目录图标与动态路由 meta 同源（router 的 iconMap），不再各自维护一份权限码表。 */
function getDirectoryIcon(permissionCode: string): string {
  return iconMap[permissionCode] ?? 'Document'
}
</script>

<template>
  <template v-for="node in nodes" :key="node.id">
    <!-- DIRECTORY 节点：仅当子树内存在可进入页面时渲染为可展开子菜单 -->
    <el-sub-menu
      v-if="node.type === 'DIRECTORY' && hasVisibleLeaf(node)"
      :index="'sub-' + node.id"
    >
      <template #title>
        <el-icon><component :is="getDirectoryIcon(node.permissionCode)" /></el-icon>
        <span>{{ getDirectoryLabel(node) }}</span>
      </template>
      <!-- 递归渲染子节点，继续复用同一份派生集合 -->
      <SidebarMenu :nodes="node.children" :visible-pages="visiblePages" />
    </el-sub-menu>
    <!-- MENU 节点：只有出现在共享派生页面集合里的菜单才渲染，索引即派生出的真实路由 -->
    <el-menu-item
      v-else-if="node.type === 'MENU' && visiblePages.has(node.permissionCode)"
      :index="pageOf(node.permissionCode)?.routePath ?? ''"
    >
      <el-icon><component :is="getMenuIcon(node.permissionCode)" /></el-icon>
      <template #title>{{ getMenuLabel(node) }}</template>
    </el-menu-item>
  </template>
</template>
