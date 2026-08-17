<script setup lang="ts">
/**
 * 供应商门户登录页面组件。
 * 复用 LoginForm 组件，登录成功后跳转到供应商门户工作台。
 */
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginForm from '@/components/LoginForm.vue'
import { useAppStore } from '@/stores/app'
import { storeLang } from '@/i18n'
import { safeAppRedirect } from '@/utils/navigation'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const appStore = useAppStore()

/** 登录成功回调：跳转到供应商门户工作台 */
function handleSuccess() {
  ElMessage.success(t('login.loginSuccess'))
  router.push(safeAppRedirect(route.query.redirect) || '/supplier-portal')
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
  <div class="login-page">
    <!-- 顶部工具栏：返回首页 + 功能按钮 -->
    <div class="login-top-bar">
      <div class="login-top-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.home') }}
        </el-button>
      </div>
      <div class="login-top-right">
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
      </div>
    </div>

    <!-- 登录表单区域 -->
    <div class="login-main">
      <div class="login-card glass-surface">
        <!-- 供应商门户标题 -->
        <div class="portal-header">
          <h2 class="portal-title gradient-text">{{ t('portalLogin.title') }}</h2>
          <p class="portal-subtitle">{{ t('portalLogin.subtitle') }}</p>
        </div>
        <LoginForm
          simple
          register-path="/portal-register"
          @login-success="handleSuccess"
        />
      </div>
    </div>

    <!-- 页脚 -->
    <footer class="login-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
}

.login-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--omni-space-md) var(--omni-space-lg);
}

.login-top-left {
  display: flex;
  align-items: center;
}

.login-top-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
}

.login-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--omni-space-xl);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: var(--omni-space-xl);
  animation: omni-fade-in-up 0.6s var(--omni-ease-smooth) both;
}

.portal-header {
  text-align: center;
  margin-bottom: var(--omni-space-md);
}

.portal-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

.portal-subtitle {
  margin: 8px 0 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.login-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}
</style>
