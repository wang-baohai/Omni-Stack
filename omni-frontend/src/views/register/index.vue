<script setup lang="ts">
/**
 * 注册页面组件。
 * 包含顶部工具栏（返回首页、语言切换、主题切换）、注册表单卡片和页脚。
 * 表单字段：用户名、密码、确认密码、昵称（选填）、邮箱（选填）、验证码。
 */
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { fetchCaptcha, registerUser, listTenants } from '@/api/auth'
import type { RegisterParams, TenantOption } from '@/api/auth'
import { useAppStore } from '@/stores/app'
import LanguageSelector from '@/components/LanguageSelector.vue'

const { t } = useI18n()
const router = useRouter()
const appStore = useAppStore()

/** 表单引用 */
const formRef = ref<FormInstance>()
/** 注册按钮加载状态 */
const loading = ref(false)
/** 验证码加载状态 */
const captchaLoading = ref(false)
/** 验证码 Base64 图片 */
const captchaImage = ref('')
/** 验证码 Key（UUID） */
const captchaKey = ref('')
/** 租户列表 */
const tenants = ref<TenantOption[]>([])

/** 表单数据模型 */
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: '',
  tenantId: undefined as number | undefined,
  captchaCode: '',
})

/** 自定义确认密码校验器 */
function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value && value !== form.password) {
    callback(new Error(t('register.passwordMismatch')))
  } else {
    callback()
  }
}

/** 表单校验规则 */
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
  email: [
    { type: 'email', message: () => t('validation.emailFormat'), trigger: 'blur' },
  ],
  tenantId: [{ required: true, message: () => t('login.tenantPlaceholder'), trigger: 'change' }],
  captchaCode: [{ required: true, message: () => t('register.captchaPlaceholder'), trigger: 'blur' }],
}

/**
 * 加载验证码图片。
 */
async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const { data: res } = await fetchCaptcha()
    captchaKey.value = res.data.captchaKey
    captchaImage.value = res.data.captchaImage
  } catch {
    ElMessage.error(t('login.captchaFailed'))
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

/**
 * 加载租户列表。
 */
async function loadTenants() {
  try {
    const { data: res } = await listTenants()
    tenants.value = res.data
  } catch {
    ElMessage.error(t('login.tenantLoadFailed'))
    tenants.value = []
  }
}

/**
 * 处理注册提交。
 */
async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const params: RegisterParams = {
      username: form.username,
      password: form.password,
      tenantId: form.tenantId!,
      captchaKey: captchaKey.value,
      captchaCode: form.captchaCode,
    }
    if (form.nickname) {
      params.nickname = form.nickname
    }
    if (form.email) {
      params.email = form.email
    }
    await registerUser(params)
    // 记住注册时选择的租户，登录页自动回填
    localStorage.setItem('last_tenant_id', String(form.tenantId))
    ElMessage.success(t('register.registerSuccess'))
    router.push({ name: 'Login' })
  } catch {
    // 错误消息已由 Axios 响应拦截器展示
  } finally {
    loading.value = false
    loadCaptcha()
  }
}

/** 切换主题模式 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */

/** 组件挂载后加载验证码和租户列表 */
onMounted(() => {
  loadCaptcha()
  loadTenants()
})
</script>

<template>
  <div class="register-page">
    <!-- 顶部工具栏：返回首页 + 功能按钮 -->
    <div class="register-top-bar">
      <div class="register-top-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.home') }}
        </el-button>
      </div>
      <div class="register-top-right">
        <!-- 语言切换 -->
        <LanguageSelector />
        <!-- 主题切换 -->
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
          <h2 class="register-form-title gradient-text">{{ t('common.appName') }}</h2>
          <p class="register-form-subtitle">{{ t('register.title') }}</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          size="large"
          @submit.prevent="handleRegister"
        >
          <!-- 用户名输入 -->
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              :placeholder="t('register.username')"
              prefix-icon="User"
            />
          </el-form-item>

          <!-- 密码输入 -->
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="t('register.password')"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <!-- 确认密码输入 -->
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              :placeholder="t('register.confirmPassword')"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>

          <!-- 昵称输入（选填） -->
          <el-form-item prop="nickname">
            <el-input
              v-model="form.nickname"
              :placeholder="t('register.nickname')"
              prefix-icon="UserFilled"
            />
          </el-form-item>

          <!-- 邮箱输入（选填） -->
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              :placeholder="t('register.email')"
              prefix-icon="Message"
            />
          </el-form-item>

          <!-- 租户选择 -->
          <el-form-item prop="tenantId">
            <el-select v-model="form.tenantId" :placeholder="t('login.tenantPlaceholder')" style="width: 100%">
              <el-option
                v-for="tenant in tenants"
                :key="tenant.id"
                :label="tenant.name"
                :value="tenant.id"
              />
            </el-select>
          </el-form-item>

          <!-- 验证码输入 + 验证码图片 -->
          <el-form-item prop="captchaCode">
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                :placeholder="t('register.captchaPlaceholder')"
                prefix-icon="Key"
                class="captcha-input"
                @keyup.enter="handleRegister"
              />
              <div
                class="captcha-image"
                :title="t('login.refreshCaptcha')"
                @click="loadCaptcha"
              >
                <img v-if="captchaImage" :src="captchaImage" alt="captcha" />
                <span v-else class="captcha-loading">{{ t('common.loading') }}</span>
              </div>
            </div>
          </el-form-item>

          <!-- 注册按钮 -->
          <el-form-item>
            <el-button
              type="primary"
              :loading="loading"
              native-type="submit"
              class="register-btn"
            >
              {{ t('register.registerButton') }}
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 已有账号跳转登录 -->
        <div class="register-login-section">
          <span>{{ t('register.hasAccount') }}</span>
          <router-link to="/login" class="login-link">{{ t('register.goLogin') }}</router-link>
        </div>
      </div>
    </div>

    <!-- 页脚 -->
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

.register-top-left {
  display: flex;
  align-items: center;
}

.register-top-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
}

.register-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--omni-space-xl);
}

.register-card {
  width: 100%;
  max-width: 420px;
  padding: var(--omni-space-xl);
  animation: omni-fade-in-up 0.6s var(--omni-ease-smooth) both;
}

.register-form-header {
  text-align: center;
  margin-bottom: var(--omni-space-lg);
}

.register-form-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

.register-form-subtitle {
  margin: 8px 0 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.captcha-row {
  display: flex;
  gap: var(--omni-space-sm);
  width: 100%;
  align-items: flex-start;
}

.captcha-input {
  flex: 1;
}

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
  transition: border-color var(--omni-duration-fast);

  &:hover {
    border-color: var(--omni-border-glow);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.captcha-loading {
  font-size: 12px;
  color: var(--omni-text-tertiary);
}

.register-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

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

.login-link {
  color: var(--omni-color-primary);
  text-decoration: none;
  font-weight: 500;

  &:hover {
    text-decoration: underline;
  }
}

.register-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}
</style>
