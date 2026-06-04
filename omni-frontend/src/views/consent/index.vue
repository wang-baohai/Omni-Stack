<script setup lang="ts">
/**
 * OAuth2 授权确认页面。
 * 当第三方客户端设置了 requireAuthorizationConsent=true 时，
 * SAS 会重定向到此页面，让用户选择是否授权。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { storeLang } from '@/i18n'
import {
  sessionLogin,
  fetchCaptcha,
  listTenants,
} from '@/api/auth'
import type { TenantOption } from '@/api/auth'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 页面状态：checking(检测会话中) -> login(未登录) -> consent(已登录待确认) -> submitting(提交中) -> success/error/denied */
type ConsentStatus = 'checking' | 'login' | 'consent' | 'submitting' | 'success' | 'error' | 'denied'
const status = ref<ConsentStatus>('checking')
/** 错误提示信息 */
const errorMessage = ref('')

/** 从 SAS 重定向参数中提取的授权请求信息 */
const clientId = ref('')
const state = ref('')
const requestedScopes = ref<string[]>([])
/** 用户选中的作用域 */
const selectedScopes = ref<string[]>([])

/** 登录表单状态 */
const loginFormRef = ref<FormInstance>()
const loginLoading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)
const tenants = ref<TenantOption[]>([])

/** 内嵌登录表单数据 */
const loginForm = reactive({
  tenantId: 1,
  username: 'admin',
  password: 'admin123',
  captchaCode: '',
})

/** 登录表单验证规则 */
const loginRules: FormRules = {
  tenantId: [{ required: true, message: () => t('login.tenantPlaceholder'), trigger: 'change' }],
  username: [{ required: true, message: () => t('login.username'), trigger: 'blur' }],
  password: [
    { required: true, message: () => t('login.password'), trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: () => t('login.captchaPlaceholder'), trigger: 'blur' }],
}

/** 是否已登录 */
const isLoggedIn = computed(() => !!userStore.token)

/** 作用域描述映射：将 OAuth2 scope 名称映射为用户可理解的说明 */
const scopeDescriptions: Record<string, { label: string; descKey: string; icon: string }> = {
  openid: { label: 'OpenID', descKey: 'consent.scopeOpenid', icon: 'User' },
  profile: { label: 'Profile', descKey: 'consent.scopeProfile', icon: 'Avatar' },
  email: { label: 'Email', descKey: 'consent.scopeEmail', icon: 'Message' },
}

/** 获取作用域的展示信息 */
function getScopeInfo(scope: string) {
  return scopeDescriptions[scope] || { label: scope, descKey: 'consent.scopeCustom', icon: 'Document' }
}

/** 加载验证码 */
async function loadCaptcha() {
  captchaLoading.value = true
  try {
    const { data: res } = await fetchCaptcha()
    captchaKey.value = res.data.captchaKey
    captchaImage.value = res.data.captchaImage
  } catch {
    ElMessage.error(t('login.captchaFailed'))
  } finally {
    captchaLoading.value = false
  }
}

/** 加载租户列表 */
async function loadTenants() {
  try {
    const { data: res } = await listTenants()
    tenants.value = res.data
  } catch {
    tenants.value = []
  }
}

/** 内嵌登录处理 */
async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loginLoading.value = true
  try {
    await sessionLogin({
      username: loginForm.username,
      password: loginForm.password,
      tenantId: loginForm.tenantId,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode,
    })
    status.value = 'consent'
    ElMessage.success(t('login.loginSuccess'))
  } catch {
    loadCaptcha()
  } finally {
    loginLoading.value = false
  }
}

/** 全选/取消全选作用域 */
function toggleAllScopes() {
  if (selectedScopes.value.length === requestedScopes.value.length) {
    selectedScopes.value = []
  } else {
    selectedScopes.value = [...requestedScopes.value]
  }
}

/** 是否全部选中 */
const allScopesSelected = computed(
  () => selectedScopes.value.length === requestedScopes.value.length && requestedScopes.value.length > 0,
)

/** 同意授权：将选中的作用域提交回 SAS /oauth2/authorize 端点 */
async function handleApprove() {
  if (selectedScopes.value.length === 0) {
    ElMessage.warning(t('consent.selectAtLeastOne'))
    return
  }
  status.value = 'submitting'
  errorMessage.value = ''
  try {
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = '/oauth2/authorize'
    form.style.display = 'none'

    // client_id 隐藏字段
    addHiddenInput(form, 'client_id', clientId.value)
    // state 防 CSRF 令牌
    addHiddenInput(form, 'state', state.value)
    // 选中的作用域（每个 scope 一个 input，与 SAS 默认表单行为一致）
    selectedScopes.value.forEach(scope => {
      addHiddenInput(form, 'scope', scope)
    })

    document.body.appendChild(form)
    form.submit()
  } catch {
    errorMessage.value = t('consent.submitFailed')
    status.value = 'error'
  }
}

/** 拒绝授权 */
async function handleDeny() {
  status.value = 'submitting'
  errorMessage.value = ''
  try {
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = '/oauth2/authorize'
    form.style.display = 'none'

    addHiddenInput(form, 'client_id', clientId.value)
    addHiddenInput(form, 'state', state.value)
    // 不传任何 scope，SAS 将返回 access_denied 错误

    document.body.appendChild(form)
    form.submit()
  } catch {
    errorMessage.value = t('consent.submitFailed')
    status.value = 'error'
  }
}

/** 向表单添加隐藏字段 */
function addHiddenInput(form: HTMLFormElement, name: string, value: string) {
  const input = document.createElement('input')
  input.type = 'hidden'
  input.name = name
  input.value = value
  form.appendChild(input)
}

/** 切换主题 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */
function toggleLang() {
  const newLang = locale.value === 'zh-CN' ? 'en-US' : 'zh-CN'
  locale.value = newLang
  storeLang(newLang)
}

onMounted(async () => {
  // 从 URL 查询参数中解析 SAS 传递的授权请求信息
  clientId.value = (route.query.client_id as string) || ''
  state.value = (route.query.state as string) || ''
  const scopeParam = (route.query.scope as string) || ''
  requestedScopes.value = scopeParam ? scopeParam.split(/[,\s]+/).filter(Boolean) : []
  selectedScopes.value = [...requestedScopes.value] // 默认全选

  // 检查登录状态：优先检查前端 JWT，再检查 SAS 会话
  if (isLoggedIn.value) {
    status.value = 'consent'
    return
  }

  // 尝试通过 SAS 会话 Cookie 检测是否已认证（OAuth2 重定向场景）
  try {
    const res = await fetch('/api/auth/session-check', { credentials: 'include' })
    if (res.ok) {
      const json = await res.json()
      if (json.code === 200) {
        status.value = 'consent'
        return
      }
    }
  } catch {
    // 会话检测失败，降级显示登录表单
  }

  status.value = 'login'
  loadCaptcha()
  loadTenants()
})
</script>

<template>
  <div class="consent-page">
    <!-- 顶部工具栏 -->
    <div class="consent-top-bar">
      <div class="consent-top-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.home') }}
        </el-button>
      </div>
      <div class="consent-top-right">
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
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="consent-main">
      <div class="consent-card glass-surface">
        <div class="consent-card-header">
          <el-icon :size="28" class="consent-icon"><DocumentChecked /></el-icon>
          <h2 class="consent-title">{{ t('consent.title') }}</h2>
        </div>

        <!-- 会话检测中 -->
        <template v-if="status === 'checking'">
          <div class="consent-result">
            <el-icon :size="32" class="consent-loading-icon"><Loading /></el-icon>
            <p class="consent-result-text">{{ t('consent.checking') }}</p>
          </div>
        </template>

        <!-- 内嵌登录表单 -->
        <template v-else-if="status === 'login'">
          <div class="consent-login-banner">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ t('consent.loginFirst') }}</span>
          </div>
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            size="large"
            @submit.prevent="handleLogin"
          >
            <el-form-item prop="tenantId">
              <el-select v-model="loginForm.tenantId" :placeholder="t('login.tenantPlaceholder')" style="width: 100%">
                <el-option
                  v-for="tenant in tenants"
                  :key="tenant.id"
                  :label="tenant.name"
                  :value="tenant.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item prop="username">
              <el-input v-model="loginForm.username" :placeholder="t('login.username')" prefix-icon="User" />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                :placeholder="t('login.password')"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
            <el-form-item prop="captchaCode">
              <div class="captcha-row">
                <el-input
                  v-model="loginForm.captchaCode"
                  :placeholder="t('login.captchaPlaceholder')"
                  prefix-icon="Key"
                  class="captcha-input"
                  @keyup.enter="handleLogin"
                />
                <div class="captcha-image" :title="t('login.refreshCaptcha')" @click="loadCaptcha">
                  <img v-if="captchaImage" :src="captchaImage" alt="captcha" />
                  <span v-else class="captcha-loading">{{ t('common.loading') }}</span>
                </div>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loginLoading" native-type="submit" class="login-btn">
                {{ t('consent.loginAndContinue') }}
              </el-button>
            </el-form-item>
          </el-form>
        </template>

        <!-- 授权确认表单 -->
        <template v-else-if="status === 'consent' || status === 'submitting'">
          <!-- 客户端信息 -->
          <div class="consent-client-info">
            <el-icon :size="20"><Monitor /></el-icon>
            <div>
              <p class="consent-client-name">{{ clientId }}</p>
              <p class="consent-client-desc">{{ t('consent.requestingAccess') }}</p>
            </div>
          </div>

          <!-- 说明文字 -->
          <p class="consent-instruction">{{ t('consent.instruction') }}</p>

          <!-- 作用域选择 -->
          <div class="consent-scopes">
            <div class="consent-scope-header">
              <span class="consent-scope-title">{{ t('consent.permissions') }}</span>
              <el-button text size="small" @click="toggleAllScopes">
                {{ allScopesSelected ? t('consent.deselectAll') : t('consent.selectAll') }}
              </el-button>
            </div>

            <el-checkbox-group v-model="selectedScopes" :disabled="status === 'submitting'">
              <div
                v-for="scope in requestedScopes"
                :key="scope"
                class="consent-scope-item"
              >
                <el-checkbox :value="scope" class="consent-scope-checkbox">
                  <div class="consent-scope-content">
                    <div class="consent-scope-label">
                      <el-icon :size="16"><component :is="getScopeInfo(scope).icon" /></el-icon>
                      <span class="consent-scope-name">{{ getScopeInfo(scope).label }}</span>
                    </div>
                    <p class="consent-scope-desc">{{ t(getScopeInfo(scope).descKey) }}</p>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
          </div>

          <!-- 安全提示 -->
          <div class="consent-security-notice">
            <el-icon><WarningFilled /></el-icon>
            <p>{{ t('consent.securityNotice') }}</p>
          </div>

          <!-- 操作按钮 -->
          <div class="consent-actions">
            <el-button
              type="primary"
              :loading="status === 'submitting'"
              class="consent-btn"
              @click="handleApprove"
            >
              {{ t('consent.approve') }}
            </el-button>
            <el-button
              :disabled="status === 'submitting'"
              class="consent-btn"
              @click="handleDeny"
            >
              {{ t('consent.deny') }}
            </el-button>
          </div>
        </template>

        <!-- 成功（通常不会到达这里，因为表单提交后浏览器会跳转到 SAS 的 redirect_uri） -->
        <template v-else-if="status === 'success'">
          <div class="consent-result">
            <el-icon :size="48" class="consent-success-icon"><CircleCheck /></el-icon>
            <p class="consent-result-text">{{ t('consent.approved') }}</p>
          </div>
        </template>

        <!-- 错误 -->
        <template v-else-if="status === 'error'">
          <div class="consent-result">
            <el-icon :size="48" class="consent-error-icon"><CircleClose /></el-icon>
            <p class="consent-result-text error-text">{{ errorMessage }}</p>
            <el-button type="primary" @click="status = 'consent'">{{ t('device.retry') }}</el-button>
          </div>
        </template>

        <!-- 已拒绝 -->
        <template v-else-if="status === 'denied'">
          <div class="consent-result">
            <el-icon :size="48" class="consent-warning-icon"><WarningFilled /></el-icon>
            <p class="consent-result-text">{{ t('consent.denied') }}</p>
            <el-button @click="router.push({ name: 'Home' })">{{ t('common.home') }}</el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- 页脚 -->
    <footer class="consent-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.consent-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
}

.consent-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--omni-space-md) var(--omni-space-lg);
}

.consent-top-left {
  display: flex;
  align-items: center;
}

.consent-top-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
}

.consent-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--omni-space-xl);
}

.consent-card {
  width: 100%;
  max-width: 520px;
  padding: var(--omni-space-xl);
  animation: omni-fade-in-up 0.6s var(--omni-ease-smooth) both;
}

.consent-card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-sm);
  margin-bottom: var(--omni-space-lg);
}

.consent-icon {
  color: var(--omni-color-primary);
}

.consent-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--omni-text-primary);
}

.consent-login-banner {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: var(--omni-space-md);
  border-radius: var(--omni-radius-md);
  background: var(--omni-bg-glass);
  border: 1px solid var(--omni-border-glass);
  align-items: flex-start;

  .el-icon {
    flex-shrink: 0;
    font-size: 18px;
    color: var(--omni-color-primary);
    margin-top: 2px;
  }

  span {
    font-size: 13px;
    color: var(--omni-text-secondary);
    line-height: 1.5;
  }
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

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

/* 客户端信息区域 */
.consent-client-info {
  display: flex;
  gap: 12px;
  padding: 14px 16px;
  margin-bottom: var(--omni-space-md);
  border-radius: var(--omni-radius-md);
  background: var(--omni-bg-glass);
  border: 1px solid var(--omni-border-glass);
  align-items: flex-start;

  .el-icon {
    flex-shrink: 0;
    color: var(--omni-color-primary);
    margin-top: 2px;
  }
}

.consent-client-name {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--omni-text-primary);
}

.consent-client-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--omni-text-secondary);
}

.consent-instruction {
  font-size: 14px;
  color: var(--omni-text-secondary);
  margin: 0 0 var(--omni-space-md);
  line-height: 1.6;
}

/* 作用域选择区域 */
.consent-scopes {
  margin-bottom: var(--omni-space-md);
}

.consent-scope-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--omni-space-sm);
}

.consent-scope-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--omni-text-primary);
}

.consent-scope-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border-radius: var(--omni-radius-md);
  border: 1px solid var(--omni-border-color);
  transition: border-color var(--omni-duration-fast), background-color var(--omni-duration-fast);

  &:hover {
    border-color: var(--omni-border-glow);
    background-color: var(--omni-bg-glass);
  }

  :deep(.el-checkbox) {
    width: 100%;
    align-items: flex-start;
  }

  :deep(.el-checkbox__label) {
    width: 100%;
    padding-left: 8px;
  }
}

.consent-scope-checkbox {
  width: 100%;
}

.consent-scope-content {
  width: 100%;
}

.consent-scope-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.consent-scope-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--omni-text-primary);
}

.consent-scope-desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--omni-text-tertiary);
  line-height: 1.5;
}

/* 安全提示 */
.consent-security-notice {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: var(--omni-space-lg);
  border-radius: var(--omni-radius-md);
  background: var(--omni-bg-glass);
  border: 1px solid var(--omni-border-glass);
  align-items: flex-start;

  .el-icon {
    flex-shrink: 0;
    font-size: 16px;
    color: var(--el-color-warning);
    margin-top: 1px;
  }

  p {
    margin: 0;
    font-size: 12px;
    color: var(--omni-text-tertiary);
    line-height: 1.6;
  }
}

/* 操作按钮 */
.consent-actions {
  display: flex;
  gap: var(--omni-space-md);
}

.consent-btn {
  flex: 1;
  height: 44px;
}

/* 结果状态 */
.consent-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-md);
  padding: var(--omni-space-lg) 0;
}

.consent-result-text {
  font-size: 15px;
  color: var(--omni-text-primary);
  text-align: center;
}

.consent-loading-icon {
  color: var(--omni-color-primary);
  animation: rotating 1.5s linear infinite;
}

@keyframes rotating {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.consent-success-icon {
  color: var(--el-color-success);
}

.consent-error-icon {
  color: var(--el-color-danger);
}

.consent-warning-icon {
  color: var(--el-color-warning);
}

.error-text {
  color: var(--el-color-danger);
}

.consent-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}
</style>
