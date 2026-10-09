<script setup lang="ts">
/**
 * 管理后台布局组件。
 * 包含可折叠侧边栏（动态菜单）、顶部导航栏（首页、语言切换、主题切换、用户菜单）
 * 和主内容区域（通过 router-view 渲染子路由页面）。
 */
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import SidebarMenu from '@/components/SidebarMenu.vue'
import LanguageSelector from '@/components/LanguageSelector.vue'
import { clearAuthenticatedSession } from '@/router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

/** 固定菜单项（始终显示） */
const staticMenuItems = [
  { path: '/admin/dashboard', icon: 'Odometer', titleKey: 'common.dashboard' },
]

function handleLogout() {
  clearAuthenticatedSession()
  router.push('/login')
}

function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

</script>

<template>
  <div class="layout">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar" :class="{ 'sidebar--collapsed': appStore.sidebarCollapsed }">
        <div class="logo">
          <h1 v-show="!appStore.sidebarCollapsed">{{ t('common.appName') }}</h1>
          <h1 v-show="appStore.sidebarCollapsed">O</h1>
        </div>
        <div class="logo-accent"></div>
        <el-menu :default-active="route.path" :collapse="appStore.sidebarCollapsed" router>
          <!-- 固定菜单项 -->
          <el-menu-item
            v-for="item in staticMenuItems"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ t(item.titleKey) }}</template>
          </el-menu-item>

          <!-- 动态菜单项（递归渲染，支持任意深度嵌套） -->
          <SidebarMenu v-if="permissionStore.menuTree.length > 0" :nodes="permissionStore.menuTree" />
        </el-menu>
      </el-aside>

      <el-container>
        <!-- 顶部导航栏 -->
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
              <Fold v-if="!appStore.sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
          <div class="header-right">
            <el-button text @click="router.push('/')">
              <el-icon><HomeFilled /></el-icon>
              {{ t('common.home') }}
            </el-button>
            <LanguageSelector />
            <el-button text :title="t('theme.toggle')" @click="toggleTheme">
              <el-icon>
                <Moon v-if="appStore.theme === 'dark'" />
                <Sunny v-else />
              </el-icon>
            </el-button>
            <el-dropdown>
              <span class="user-info">
                <el-icon><User /></el-icon>
                {{ userStore.username || 'Admin' }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <!-- 主内容区域 -->
        <el-main class="main-content">
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

/* 平板断点：压缩顶栏左右内边距，为侧栏 + 内容区腾出横向空间。 */
@media (max-width: 1024px) {
  .header { padding: 0 16px; }
}

/* 移动断点：侧栏转为覆盖层，不挤占主内容；
   CSS !important 覆盖 el-aside 的 inline width，折叠时 0px 隐藏，展开时 220px 浮层。 */
@media (max-width: 768px) {
  .sidebar {
    position: fixed !important;
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
  .header {
    position: sticky;
    top: 0;
    z-index: 1000;
  }
  .header-right {
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 6px;
    min-width: 0;
  }
  .header-right :deep(.el-button) { padding: 4px 6px; }
  .user-info {
    min-width: 0;
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .main-content { padding: 16px; }
}

@media (max-width: 600px) {
  .header { padding: 0 8px; }
  .header-right { gap: 4px; min-width: 0; }
  .header-right :deep(.el-button) { padding: 4px; }
  .user-info {
    display: block;
    max-width: 76px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .main-content { padding: 8px; }
}
</style>
