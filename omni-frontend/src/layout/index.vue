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
import SidebarMenu from '@/components/SidebarMenu.vue'

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
