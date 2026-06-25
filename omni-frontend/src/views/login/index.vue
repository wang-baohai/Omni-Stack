<script setup lang="ts">
/**
 * 登录页面组件。
 * 包含顶部工具栏（返回首页、语言切换、主题切换）、
 * 登录表单卡片和页脚。通过 LoginForm 子组件处理实际登录逻辑。
 */
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import LoginForm from '@/components/LoginForm.vue'
import { useAppStore } from '@/stores/app'
import { storeLang } from '@/i18n'

const { t, locale } = useI18n()
const router = useRouter()
const appStore = useAppStore()

/** 登录成功回调：显示成功提示并跳转到工作台 */
function handleSuccess() {
  ElMessage.success(t('login.loginSuccess'))
  router.push({ name: 'Home' })
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
        <LoginForm @login-success="handleSuccess" />
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

.login-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}
</style>
