<script setup lang="ts">
/**
 * 供应商自助注册页面。
 * 本页面只创建认证账号；登录后在供应商门户使用邀请令牌完成入驻。
 */
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchCaptcha, registerUser, listTenants } from '@/api/auth'
import type { TenantOption } from '@/api/auth'
import { useAppStore } from '@/stores/app'
import LanguageSelector from '@/components/LanguageSelector.vue'

const { t } = useI18n()
const router = useRouter()
const appStore = useAppStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaLoading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const tenants = ref<TenantOption[]>([])
/** 注册成功标志，显示登录并继续入驻的提示。 */
const registerSuccess = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  tenantId: undefined as number | undefined,
  captchaCode: '',
})

/** 确认密码校验 */
function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && value !== form.password) {
    callback(new Error(t('register.passwordMismatch')))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: () => t('register.username'), trigger: 'blur' },
    { min: 3, max: 32, message: () => t('validation.usernameLength'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: () => t('register.password'), trigger: 'blur' },
    { min: 6, message: () => t('validation.passwordMin'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: () => t('register.confirmPassword'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  tenantId: [{ required: true, message: () => t('login.tenantPlaceholder'), trigger: 'change' }],
  captchaCode: [{ required: true, message: () => t('register.captchaPlaceholder'), trigger: 'blur' }],
}

async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const { data: res } = await fetchCaptcha()
    captchaKey.value = res.data.captchaKey
    captchaImage.value = res.data.captchaImage
  } catch {
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

async function loadTenants() {
  try {
    const { data: res } = await listTenants()
    tenants.value = res.data
    if (form.tenantId === undefined && tenants.value.length > 0) {
      form.tenantId = tenants.value[0].id
    }
  } catch {
    tenants.value = []
  }
}

/**
 * 处理注册提交：
 * 1. 调用 auth register 创建用户账号（自动分配 USER 角色）
 * 2. 显示成功提示，引导用户登录并完成供应商入驻
 */
async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 步骤 1: 创建用户账号
    await registerUser({
      username: form.username,
      password: form.password,
      tenantId: form.tenantId!,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode,
    })

    // 记住注册时选择的租户，登录页自动回填
    localStorage.setItem('last_tenant_id', String(form.tenantId))

    // 步骤 2: 显示成功提示
    registerSuccess.value = true
  } catch {
    // 错误消息已由 Axios 响应拦截器展示
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}


onMounted(() => {
  loadCaptcha()
  loadTenants()
})
</script>

<template>
  <div class="register-page">
    <!-- 顶部工具栏 -->
    <div class="register-top-bar">
      <div class="register-top-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.home') }}
        </el-button>
      </div>
      <div class="register-top-right">
        <LanguageSelector />
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon>
            <Moon v-if="appStore.theme === 'dark'" />
            <Sunny v-else />
          </el-icon>
        </el-button>
      </div>
    </div>

    <!-- 注册表单区域 -->
    <div class="register-main">
      <div class="register-card glass-surface">
        <div class="register-form-header">
          <h2 class="register-form-title gradient-text">{{ t('portalRegister.title') }}</h2>
          <p class="register-form-subtitle">{{ t('portalRegister.subtitle') }}</p>
        </div>

        <!-- 注册成功提示 -->
        <template v-if="registerSuccess">
          <el-result icon="success" :title="t('portalRegister.registerSuccess')">
            <template #extra>
              <el-button type="primary" @click="router.push('/portal-login')">
                {{ t('portalRegister.goLogin') }}
              </el-button>
            </template>
          </el-result>
        </template>

        <!-- 注册表单 -->
        <template v-else>
          <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleRegister">
            <!-- 账号信息 -->
            <el-form-item prop="tenantId">
              <el-select v-model="form.tenantId" :placeholder="t('login.tenantPlaceholder')" style="width: 100%">
                <el-option v-for="tenant in tenants" :key="tenant.id" :label="tenant.name" :value="tenant.id" />
              </el-select>
            </el-form-item>
            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="t('register.username')" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" :placeholder="t('register.password')" prefix-icon="Lock" show-password />
            </el-form-item>
            <el-form-item prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" :placeholder="t('register.confirmPassword')" prefix-icon="Lock" show-password />
            </el-form-item>

            <!-- 验证码 -->
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input v-model="form.captchaCode" :placeholder="t('register.captchaPlaceholder')" prefix-icon="Key" class="captcha-input" @keyup.enter="handleRegister" />
                <div class="captcha-image" :title="t('login.refreshCaptcha')" @click="loadCaptcha">
                  <img v-if="captchaImage" :src="captchaImage" alt="captcha" />
                  <span v-else class="captcha-loading">{{ t('common.loading') }}</span>
                </div>
              </div>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="loading" native-type="submit" class="register-btn">
                {{ t('register.registerButton') }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="register-login-section">
            <span>{{ t('portalRegister.hasAccount') }}</span>
            <router-link to="/portal-login" class="login-link">{{ t('portalRegister.goLogin') }}</router-link>
          </div>
        </template>
      </div>
    </div>

    <footer class="register-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.register-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
}
.register-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--omni-space-md) var(--omni-space-lg);
}
.register-top-left { display: flex; align-items: center; }
.register-top-right { display: flex; align-items: center; gap: var(--omni-space-xs); }
.register-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--omni-space-xl);
}
.register-card {
  width: 100%;
  max-width: 480px;
  padding: var(--omni-space-xl);
  animation: omni-fade-in-up 0.6s var(--omni-ease-smooth) both;
}
.register-form-header { text-align: center; margin-bottom: var(--omni-space-lg); }
.register-form-title { font-size: 28px; font-weight: 700; margin: 0; }
.register-form-subtitle { margin: 8px 0 0; color: var(--omni-text-secondary); font-size: 14px; }
.captcha-row { display: flex; gap: var(--omni-space-sm); width: 100%; align-items: flex-start; }
.captcha-input { flex: 1; }
.captcha-image {
  flex-shrink: 0;
  width: 130px;
  height: 40px;
  border-radius: var(--omni-radius-md);
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--omni-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--omni-bg-input);
  &:hover { border-color: var(--omni-border-glow); }
  img { width: 100%; height: 100%; object-fit: cover; }
}
.captcha-loading { font-size: 12px; color: var(--omni-text-tertiary); }
.register-btn { width: 100%; height: 44px; font-size: 15px; }
.register-login-section {
  text-align: center;
  margin-top: var(--omni-space-md);
  font-size: 14px;
  color: var(--omni-text-secondary);
  display: flex;
  justify-content: center;
  gap: var(--omni-space-xs);
  align-items: center;
}
.login-link { color: var(--omni-color-primary); text-decoration: none; font-weight: 500; &:hover { text-decoration: underline; } }
.register-footer { text-align: center; padding: var(--omni-space-lg); color: var(--omni-text-tertiary); font-size: 13px; }
</style>
