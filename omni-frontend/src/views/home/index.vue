<script setup lang="ts">
/**
 * 首页组件。
 * 展示产品介绍、导航入口，支持登录/未登录两种状态显示。
 * 包含顶部导航栏、Hero 区域和页脚。
 */
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { storeLang } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 跳转到登录页 */
function goToLogin() {
  router.push({ name: 'Login' })
}

/** 跳转到控制台（仪表盘） */
function goToConsole() {
  router.push({ name: 'Dashboard' })
}

/** 处理用户登出 */
function handleLogout() {
  userStore.logout()
}

/** 切换主题模式 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */
function toggleLang() {
  const newLang = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLang
  storeLang(newLang)
}
</script>

<template>
  <div class="home-page">
    <!-- 顶部导航栏：Logo + 功能按钮 + 用户菜单 -->
    <header class="home-header">
      <div class="home-header-left">
        <span class="home-logo gradient-text">{{ t('common.appName') }}</span>
      </div>
      <div class="home-header-right">
        <!-- 语言切换 -->
        <el-button text :title="t('lang.switch')" @click="toggleLang">
          <el-icon><Globe /></el-icon>
          {{ locale === 'zh-CN' ? 'EN' : '中' }}
        </el-button>
        <!-- 主题切换 -->
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon>
            <Moon v-if="appStore.theme === 'dark'" />
            <Sunny v-else />
          </el-icon>
        </el-button>
        <!-- 未登录状态：显示登录按钮 -->
        <template v-if="!userStore.isLoggedIn">
          <el-button type="primary" @click="goToLogin">
            <el-icon><User /></el-icon>
            {{ t('common.login') }}
          </el-button>
        </template>
        <!-- 已登录状态：显示用户信息和控制台入口 -->
        <template v-else>
          <el-dropdown>
            <span class="home-user-info">
              <el-avatar :size="32" class="home-avatar">
                <el-icon :size="18"><User /></el-icon>
              </el-avatar>
              <span class="home-username">{{ userStore.username || 'Admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" @click="goToConsole">
            <el-icon><Monitor /></el-icon>
            {{ t('common.console') }}
          </el-button>
        </template>
      </div>
    </header>

    <!-- Hero 主视觉区域：标题、描述和操作按钮 -->
    <main class="home-hero">
      <div class="hero-orb"></div>
      <div class="hero-content">
        <h1 class="home-hero-title">
          <span class="gradient-text">{{ t('home.welcome') }}</span>
        </h1>
        <p class="home-hero-subtitle">{{ t('common.subtitle') }}</p>
        <p class="home-hero-desc">{{ t('home.desc') }}</p>
        <div class="home-hero-actions">
          <!-- 已登录：进入控制台；未登录：开始使用 -->
          <el-button v-if="userStore.isLoggedIn" type="primary" size="large" @click="goToConsole">
            {{ t('home.goToConsole') }}
          </el-button>
          <el-button v-else type="primary" size="large" @click="goToLogin">
            {{ t('home.getStarted') }}
          </el-button>
        </div>
      </div>
    </main>

    <!-- 页脚 -->
    <footer class="home-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
  overflow-x: hidden;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--omni-space-xl);
  height: 72px;
  background: var(--omni-bg-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--omni-border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.home-header-left {
  display: flex;
  align-items: center;
}

.home-logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.home-header-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
}

.home-user-info {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
  cursor: pointer;
  color: var(--omni-text-primary);
  transition: color var(--omni-duration-fast);

  &:hover {
    color: var(--omni-text-accent);
  }
}

.home-avatar {
  background: var(--omni-gradient-primary);
}

.home-username {
  font-size: 14px;
  font-weight: 500;
  color: var(--omni-text-primary);
}

.home-hero {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: var(--omni-space-3xl) var(--omni-space-lg);
  position: relative;
}

.hero-orb {
  position: absolute;
  width: 600px;
  height: 600px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(0, 153, 204, 0.08) 0%,
    rgba(108, 71, 214, 0.05) 50%,
    transparent 70%
  );
  filter: blur(60px);
  pointer-events: none;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

html.dark .hero-orb {
  background: radial-gradient(
    circle,
    rgba(0, 212, 255, 0.10) 0%,
    rgba(123, 97, 255, 0.06) 50%,
    transparent 70%
  );
}

.hero-content {
  position: relative;
  z-index: 1;
  animation: omni-fade-in-up 0.8s var(--omni-ease-smooth) both;
}

.home-hero-title {
  font-size: 56px;
  font-weight: 700;
  margin: 0 0 var(--omni-space-md);
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.home-hero-subtitle {
  font-size: 22px;
  margin: 0 0 var(--omni-space-md);
  color: var(--omni-text-secondary);
  font-weight: 500;
}

.home-hero-desc {
  font-size: 16px;
  margin: 0 0 var(--omni-space-xl);
  color: var(--omni-text-tertiary);
  max-width: 560px;
  line-height: 1.6;
  margin-left: auto;
  margin-right: auto;
}

.home-hero-actions {
  display: flex;
  gap: var(--omni-space-md);
  justify-content: center;
}

.home-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 14px;
  border-top: 1px solid var(--omni-border-color);
}
</style>
