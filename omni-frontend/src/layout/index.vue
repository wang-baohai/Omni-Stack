<script setup lang="ts">
/**
 * 管理后台（模块）布局组件。
 * 侧栏只渲染当前模块的子树：模块归属按 route.meta.appId 与菜单根的稳定 ID 匹配，
 * 可见性、图标、标题与路由路径一律取自应用卡片/抽屉/菜单搜索共用的同一份派生结果，
 * 因此 CRM 内不会出现采购菜单，裁剪预设下也不会留下指向未装配视图的死链；
 * 组件内不按权限码前缀猜目录。
 * 面包屑只读真实路由 meta 的祖先链，未登记时整条不渲染，绝不合成层级。
 * 侧栏折叠由本布局的独立按钮控制；本模块顶栏不提供全局应用按钮。
 * 模块切换用菜单 key 重建，未知路由与无权限路由不会保留上一个模块的菜单。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElBreadcrumb, ElBreadcrumbItem } from 'element-plus'
import type { MenuNode } from '@/api/menu'
import type { RouteBreadcrumbItem } from '@/router'
import type { NavigationEntry } from '@/utils/application-navigation'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import { useApplicationNavigation } from '@/composables/useApplicationNavigation'
import SidebarMenu from '@/components/SidebarMenu.vue'
import ConsoleHeader from '@/components/ConsoleHeader.vue'

/** 管理首页仅保留自己的入口；未知模块或私有错误页不回退到该入口。 */
const DASHBOARD_PATH = '/admin/dashboard'

const { t, te } = useI18n()
const route = useRoute()
const appStore = useAppStore()
const permissionStore = usePermissionStore()
const { pagesOfApp } = useApplicationNavigation()

/** 当前模块（顶层应用根）的稳定 ID；来自动态注册时写入的 meta，非字符串前缀推断。 */
const currentAppId = computed<number | null>(() => route.meta.appId ?? null)

/** 菜单根与 meta.appId 同一来源（registerDynamicRoutes 以顶层节点作为 appRoot）。 */
const moduleRoot = computed<MenuNode | null>(() => {
  const appId = currentAppId.value
  if (appId === null) return null
  return permissionStore.menuTree.find((node) => node.id === appId) ?? null
})

/** 侧栏只接收当前模块子树：目录根取其子级，单页根自身作为唯一项。 */
const moduleNodes = computed<MenuNode[]>(() => {
  const root = moduleRoot.value
  if (root === null) return []
  return root.type === 'MENU' ? [root] : root.children
})

/** 与卡片/抽屉/搜索同源的该模块可进入页面；不在此集合内的菜单不渲染。 */
const visiblePages = computed<ReadonlyMap<string, NavigationEntry>>(() => {
  const appId = currentAppId.value
  if (appId === null) return new Map<string, NavigationEntry>()
  return new Map(pagesOfApp(appId).map((page) => [page.permissionCode, page]))
})

/** 模块或落点变化时重建 el-menu，避免残留上一个模块的展开态与选中态。 */
const menuKey = computed(() => `module-menu-${currentAppId.value ?? 'landing'}`)

/** 面包屑直接来自真实路由 meta；目录不可跳转，所以全部渲染为文本，不合成死链。 */
const breadcrumbs = computed<readonly RouteBreadcrumbItem[]>(() => route.meta.breadcrumb ?? [])

/** 面包屑标题：命中 i18n 键用本地化文案，否则回退后端权限名，不伪造。 */
function breadcrumbTitle(item: RouteBreadcrumbItem): string {
  return item.titleKey !== null && te(item.titleKey) ? t(item.titleKey) : item.fallbackTitle
}

/** 折叠控件的可访问名由同一份 app store 折叠态派生，不新建第二套状态。 */
const collapseLabel = computed(() =>
  t(appStore.sidebarCollapsed ? 'common.expandSidebar' : 'common.collapseSidebar'),
)
</script>

<template>
  <div class="layout">
    <el-container>
      <!-- 侧边栏：只吃当前模块子树 + 共享派生集合 -->
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
        <div class="logo">
          <h1 v-show="!appStore.sidebarCollapsed">{{ t('common.appName') }}</h1>
          <h1 v-show="appStore.sidebarCollapsed">O</h1>
        </div>
        <div class="logo-accent"></div>
        <el-menu :key="menuKey" :default-active="route.path" :collapse="appStore.sidebarCollapsed" router>
          <!-- 仅管理首页本身显示此项；未知模块及无权限页面保持空菜单。 -->
          <el-menu-item v-if="currentAppId === null && route.name === 'Dashboard'" :index="DASHBOARD_PATH">
            <el-icon><Odometer /></el-icon>
            <template #title>{{ t('common.dashboard') }}</template>
          </el-menu-item>

          <!-- 动态菜单项（递归渲染，支持任意深度嵌套），可见性由父级传入的派生集合决定 -->
          <SidebarMenu :nodes="moduleNodes" :visible-pages="visiblePages" />
        </el-menu>
      </el-aside>

      <el-container direction="vertical">
        <!-- 业务标签只保留当前模块搜索、语言/主题与账户，不提供全局应用或首页/工作台入口。 -->
        <ConsoleHeader module-only>
          <template #leading>
            <el-button text :aria-label="collapseLabel" class="collapse-btn" @click="appStore.toggleSidebar">
              <el-icon>
                <Fold v-if="!appStore.sidebarCollapsed" />
                <Expand v-else />
              </el-icon>
            </el-button>
          </template>
        </ConsoleHeader>

        <!-- 主内容区域 -->
        <el-main class="main-content">
          <!-- 面包屑随真实路由更新：层级来自该路由的祖先链，未登记时不渲染 -->
          <el-breadcrumb v-if="breadcrumbs.length > 0" class="module-breadcrumb" separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.code">
              {{ breadcrumbTitle(item) }}
            </el-breadcrumb-item>
          </el-breadcrumb>
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped lang="scss">
/*
 * 响应式根因修复（不依赖运行时截图，S12 复核实际尺寸）：
 * 1) 主内容区是 el-container 的弹性子项，默认 min-width:auto 会被内部
 *    多列固定最小宽度的 el-table 顶开，导致整页横向溢出；置 0 后
 *    表格转为容器内部滚动，页面本身不再外溢（非用 overflow:hidden 掩盖按钮）。
 */
.main-content {
  min-width: 0;
  max-width: 100%;
}

/* 面包屑只占一行，与页面主体之间留一级间距，避免贴住标题。 */
.module-breadcrumb {
  margin-bottom: var(--omni-space-md);
  font-size: 13px;
}

/* 平板断点：顶栏内边距压缩已收进 ConsoleHeader，这里不再重复一份头部断点规则。 */

/* 移动断点：侧栏转为覆盖层，不挤占主内容；
   CSS !important 覆盖 el-aside 的 inline width，折叠时 0px 隐藏，展开时 220px 浮层。
   顶栏的粘性定位与右侧工具换行同样由 ConsoleHeader 负责。 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed !important;
    /* 覆盖层需要实色底，避免底层表格文字透入菜单影响阅读。 */
    background: var(--omni-bg-base);
    top: 0;
    left: 0;
    height: 100vh;
    z-index: 1001;
    overflow: hidden;
    transition: width 0.28s ease;
    box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
    /* 默认折叠态：宽度 0 完全隐藏 */
    width: 0 !important;
  }
  /* 侧栏未折叠时展开为 220px 覆盖层 */
  .sidebar:not(.sidebar--collapsed) {
    width: 220px !important;
  }
  /* 折叠按钮换成真实 button 后自带内边距，窄屏收回与顶栏其余图标同一密度。 */
  .collapse-btn {
    padding: 4px 6px;
  }
  .main-content { padding: 16px; }
}

@media (max-width: 600px) {
  .main-content { padding: 8px; }
}
</style>
