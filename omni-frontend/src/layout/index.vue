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
import { storeLang } from '@/i18n'
import type { MenuNode } from '@/api/menu'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

/** 固定菜单项（始终显示） */
const staticMenuItems = [
  { path: '/admin/dashboard', icon: 'Odometer', titleKey: 'common.dashboard' },
]

/** 权限编码到 i18n 翻译键的映射 */
const menuI18nMap: Record<string, string> = {
  'system': 'common.systemManagement',
  'system:user': 'common.users',
  'system:role': 'common.roles',
  'system:permission': 'common.permissions',
  'system:org': 'common.organizations',
  'system:tenant': 'common.tenants',
  'system:oauth2': 'common.oauth2Clients',
  'system:online': 'common.onlineUsers',
  'system:authrecord': 'common.authRecords',
  'system:auditlog': 'common.auditLogs',
}

/** 获取菜单显示名称（优先 i18n 翻译，fallback 后端原始名称） */
function getMenuLabel(code: string, fallback: string): string {
  const key = menuI18nMap[code]
  return key ? t(key) : fallback
}

/**
 * 根据权限码生成路由路径。
 * 约定：permissionCode "system:user" -> 路径 "/admin/user"
 */
function menuCodeToPath(permissionCode: string): string {
  const segments = permissionCode.split(':')
  return `/admin/${segments[segments.length - 1]}`
}

/**
 * 权限码到 Element Plus 图标名称的映射。
 */
const iconMap: Record<string, string> = {
  'system:user': 'User',
  'system:role': 'UserFilled',
  'system:permission': 'Lock',
  'system:org': 'OfficeBuilding',
  'system:tenant': 'School',
  'system:oauth2': 'Key',
  'system:online': 'Monitor',
  'system:authrecord': 'Document',
  'system:auditlog': 'Notebook',
}

/**
 * 获取菜单节点的图标名称。
 */
function getMenuIcon(code: string): string {
  return iconMap[code] || 'Document'
}

function handleLogout() {
  userStore.logout()
  router.push('/')
}

function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

function toggleLang() {
  const newLang = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLang
  storeLang(newLang)
}
</script>

<template>
  <div class="layout">
    <el-container>
      <!-- 侧边栏 -->
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
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

          <!-- 动态菜单项（从后端权限树渲染） -->
          <template v-if="permissionStore.menuTree.length > 0">
            <el-menu-item-group v-if="appStore.sidebarCollapsed">
              <!-- 折叠模式下只显示 MENU 节点 -->
              <template v-for="dirNode in permissionStore.menuTree" :key="dirNode.id">
                <template v-for="child in dirNode.children" :key="child.id">
                  <el-menu-item
                    v-if="child.type === 'MENU'"
                    :index="menuCodeToPath(child.permissionCode)"
                  >
                    <el-icon><component :is="getMenuIcon(child.permissionCode)" /></el-icon>
                    <template #title>{{ getMenuLabel(child.permissionCode, child.permissionName) }}</template>
                  </el-menu-item>
                </template>
              </template>
            </el-menu-item-group>

            <!-- 展开模式下显示分组和菜单 -->
            <template v-else>
              <template v-for="dirNode in permissionStore.menuTree" :key="dirNode.id">
                <el-sub-menu
                  v-if="dirNode.children && dirNode.children.some((c: MenuNode) => c.type === 'MENU')"
                  :index="'sub-' + dirNode.id"
                >
                  <template #title>
                    <el-icon><component :is="getMenuIcon(dirNode.permissionCode)" /></el-icon>
                    <span>{{ getMenuLabel(dirNode.permissionCode, dirNode.permissionName) }}</span>
                  </template>
                  <template v-for="child in dirNode.children" :key="child.id">
                    <el-menu-item
                      v-if="child.type === 'MENU'"
                      :index="menuCodeToPath(child.permissionCode)"
                    >
                      <el-icon><component :is="getMenuIcon(child.permissionCode)" /></el-icon>
                      <template #title>{{ getMenuLabel(child.permissionCode, child.permissionName) }}</template>
                    </el-menu-item>
                  </template>
                </el-sub-menu>
              </template>
            </template>
          </template>
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
            <el-button text :title="t('lang.switch')" @click="toggleLang">
              <el-icon><Globe /></el-icon>
              {{ locale === 'zh-CN' ? 'EN' : '中' }}
            </el-button>
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
