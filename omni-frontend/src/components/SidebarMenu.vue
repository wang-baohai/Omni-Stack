<script setup lang="ts">
/**
 * @module components/SidebarMenu
 * 递归侧边栏菜单组件。
 * 支持任意深度的权限树嵌套（DIRECTORY → DIRECTORY → ... → MENU），
 * 通过递归调用自身渲染多级子菜单。
 */
import { computed } from 'vue'
import type { MenuNode } from '@/api/menu'
import { menuI18nMap } from '@/constants/menu'
import { useI18n } from 'vue-i18n'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { isAssetSelfServiceUser } from '@/utils/access'
import { getRolesFromToken } from '@/utils/jwt'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const userStore = useUserStore()
const assetSelfServiceOnly = computed(() => isAssetSelfServiceUser(
  permissionStore.permissions,
  getRolesFromToken(userStore.token),
))

defineProps<{
  /** 当前层级的菜单节点列表 */
  nodes: MenuNode[]
}>()

/**
 * 权限码到 Element Plus 图标名称的映射。
 */
const iconMap: Record<string, string> = {
  'crm': 'TrendCharts',
  'crm:overview': 'DataAnalysis',
  'crm:lead': 'User',
  'crm:customer': 'OfficeBuilding',
  'crm:contact': 'Postcard',
  'crm:opportunity': 'TrendCharts',
  'crm:activity': 'Calendar',
  'srm': 'GoodsFilled',
  'srm:overview': 'DataAnalysis',
  'srm:supplier': 'OfficeBuilding',
  'srm:evaluation': 'Document',
  'srm:risk': 'Warning',
  'srm:portal': 'Promotion',
  'srm:portal:profile': 'Document',
  'srm:portal:evaluation': 'TrendCharts',
  'srm:portal:risk': 'Warning',
  'srm:invite': 'Message',
  'procurement': 'ShoppingCart',
  'procurement:overview': 'DataAnalysis',
  'procurement:material': 'Box',
  'procurement:approval-route': 'Guide',
  'procurement:requisition': 'DocumentAdd',
  'procurement:rfq': 'Tickets',
  'procurement:purchase-order': 'ShoppingBag',
  'procurement:goods-receipt': 'TakeawayBox',
  'asset': 'Coin',
  'asset:overview': 'DataAnalysis',
  'asset:asset': 'Box',
  'asset:transfer': 'Switch',
  'asset:disposal': 'Delete',
  'system:user': 'User',
  'system:role': 'UserFilled',
  'system:permission': 'Lock',
  'system:org': 'OfficeBuilding',
  'system:tenant': 'School',
  'system:oauth2': 'Key',
  'system:online': 'Monitor',
  'system:authrecord': 'Document',
  'system:auditlog': 'Notebook',
  'system:xssconfig': 'Filter',
  'base:dict': 'Collection',
  'base:operlog': 'Tickets',
  'base:mqmessage': 'MessageBox',
  'job:user-job-type': 'Files',
  'job:system-job': 'Timer',
  'workflow:definition': 'SetUp',
  'workflow:model': 'EditPen',
  'workflow:instance': 'List',
  'workflow:stats': 'DataAnalysis',
}

/** 获取菜单显示名称（优先 i18n 翻译，fallback 后端原始名称） */
function getMenuLabel(code: string, fallback: string): string {
  if (code === 'asset:asset' && assetSelfServiceOnly.value) {
    return t('common.assetMyAssets')
  }
  const key = menuI18nMap[code]
  return key ? t(key) : fallback
}

/** 根据权限码生成路由路径 */
function menuCodeToPath(permissionCode: string): string {
  return `/admin/${permissionCode.replace(/:/g, '/')}`
}

/** 获取图标名称 */
function getMenuIcon(code: string): string {
  return iconMap[code] || 'Document'
}

/**
 * 判断节点下是否存在可见的 MENU 类型后代。
 * 用于过滤掉没有叶子菜单的空目录。
 */
function hasVisibleMenuDescendant(node: MenuNode): boolean {
  if (node.permissionCode === 'srm:portal' || node.permissionCode.startsWith('srm:portal:')) return false
  if (node.type === 'MENU') return true
  if (!node.children || node.children.length === 0) return false
  return node.children.some(hasVisibleMenuDescendant)
}
</script>

<template>
  <template v-for="node in nodes" :key="node.id">
    <!-- DIRECTORY 节点：渲染为可展开的子菜单，内部递归 -->
    <el-sub-menu
      v-if="node.type === 'DIRECTORY' && node.children && node.children.some(hasVisibleMenuDescendant)"
      :index="'sub-' + node.id"
    >
      <template #title>
        <el-icon><component :is="getMenuIcon(node.permissionCode)" /></el-icon>
        <span>{{ getMenuLabel(node.permissionCode, node.permissionName) }}</span>
      </template>
      <!-- 递归渲染子节点 -->
      <SidebarMenu :nodes="node.children" />
    </el-sub-menu>
    <!-- MENU 节点：渲染菜单项 -->
    <el-menu-item
      v-else-if="node.type === 'MENU' && !node.permissionCode.startsWith('srm:portal:')"
      :index="menuCodeToPath(node.permissionCode)"
    >
      <el-icon><component :is="getMenuIcon(node.permissionCode)" /></el-icon>
      <template #title>{{ getMenuLabel(node.permissionCode, node.permissionName) }}</template>
    </el-menu-item>
  </template>
</template>
