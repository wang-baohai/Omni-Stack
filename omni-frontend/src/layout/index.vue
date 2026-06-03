<script setup lang="ts">
/**
 * 管理后台布局组件。
 * 包含可折叠侧边栏、顶部导航栏（首页、语言切换、主题切换、用户菜单）
 * 和主内容区域（通过 router-view 渲染子路由页面）。
 */
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { storeLang } from '@/i18n'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 处理用户登出：清除状态并跳转到首页 */
function handleLogout() {
  userStore.logout()
  router.push('/')
}

/** 切换主题模式（暗色/浅色互切） */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言（中文/英文互切），同时持久化语言偏好 */
function toggleLang() {
  const newLang = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLang
  storeLang(newLang)
}
</script>

<template>
  <div class="layout">
    <el-container>
      <!-- 侧边栏：包含 Logo 和导航菜单 -->
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
        <div class="logo">
          <h1 v-show="!appStore.sidebarCollapsed">{{ t('common.appName') }}</h1>
          <h1 v-show="appStore.sidebarCollapsed">O</h1>
        </div>
        <div class="logo-accent"></div>
        <el-menu
          :default-active="route.path"
          :collapse="appStore.sidebarCollapsed"
          router
        >
          <el-menu-item index="/admin/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>{{ t('common.dashboard') }}</template>
          </el-menu-item>
          <el-menu-item index="/admin/oauth2-clients">
            <el-icon><Key /></el-icon>
            <template #title>{{ t('common.oauth2Clients') }}</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <!-- 顶部导航栏：折叠按钮、功能按钮组、用户菜单 -->
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
              <Fold v-if="!appStore.sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
          <div class="header-right">
            <!-- 返回首页 -->
            <el-button text @click="router.push('/')">
              <el-icon><HomeFilled /></el-icon>
              {{ t('common.home') }}
            </el-button>
            <!-- 语言切换按钮 -->
            <el-button text :title="t('lang.switch')" @click="toggleLang">
              <el-icon><Globe /></el-icon>
              {{ locale === 'zh-CN' ? 'EN' : '中' }}
            </el-button>
            <!-- 主题切换按钮 -->
            <el-button text :title="t('theme.toggle')" @click="toggleTheme">
              <el-icon>
                <Moon v-if="appStore.theme === 'dark'" />
                <Sunny v-else />
              </el-icon>
            </el-button>
            <!-- 用户下拉菜单 -->
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

        <!-- 主内容区域：渲染子路由页面 -->
        <el-main class="main-content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>
