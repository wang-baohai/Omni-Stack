<script setup lang="ts">
/**
 * 设备授权验证页面。
 * 用户在此页面输入 user_code 并完成授权确认。
 * 未登录时展示内嵌登录表单。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import LanguageSelector from '@/components/LanguageSelector.vue'
import {
  sessionLogin,
  fetchCaptcha,
  listTenants,
} from '@/api/auth'
import type { TenantOption } from '@/api/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 页面状态：checking(检测会话) -> login(未登录) -> ready(已登录待提交) -> submitting(提交中) -> success/error/denied */
type VerifyStatus = 'checking' | 'login' | 'ready' | 'submitting' | 'success' | 'error' | 'denied'
const status = ref<VerifyStatus>('checking')
/** 用户从设备页面获取的验证码（格式：XXXX-XXXX） */
const userCode = ref('')
/** 错误提示信息 */
const errorMessage = ref('')

/** 登录表单状态 */
const loginFormRef = ref<FormInstance>()
const loginLoading = ref(false)
const captchaImage = ref('')
const captchaKey = ref('')
const captchaLoading = ref(false)
const tenants = ref<TenantOption[]>([])

/** 内嵌登录表单数据 */
const loginForm = reactive({
  tenantId: undefined as number | undefined,
  username: '',
  password: '',
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
    if (loginForm.tenantId === undefined && tenants.value.length > 0) {
      loginForm.tenantId = tenants.value[0].id
    }
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
      tenantId: loginForm.tenantId!,
      captchaKey: captchaKey.value,
      captchaCode: loginForm.captchaCode,
    })
    status.value = 'ready'
    ElMessage.success(t('login.loginSuccess'))
  } catch {
    loadCaptcha()
  } finally {
    loginLoading.value = false
  }
}

/** 授权确认：提交 user_code 到 SAS 设备验证端点。
 * 
 * 流程：先验证 SAS 会话有效性，再用 redirect: 'manual' 提交设备验证。
 * SAS 验证成功后返回 302 重定向（location 指向 auth 服务器内网地址），
 * manual 模式下浏览器不跟随重定向，返回 opaqueredirect 类型即视为成功。
 * 
 * 如果会话已失效，在提交前的 session-check 中就会被检测到，
 * 避免将认证失败重定向误判为授权成功。
 */
async function handleAuthorize() {
  if (!userCode.value.trim()) {
    ElMessage.warning(t('device.verifyInstruction'))
    return
  }
  status.value = 'submitting'
  errorMessage.value = ''

  // 提交前先验证 SAS 会话是否有效，防止认证失败重定向被误判为成功
  try {
    const checkRes = await fetch('/api/auth/session-check', { credentials: 'include' })
    if (checkRes.ok) {
      const checkJson = await checkRes.json()
      if (checkJson.code !== 200) {
        status.value = 'login'
        loadCaptcha()
        loadTenants()
        ElMessage.warning(t('device.loginFirst'))
        return
      }
    }
  } catch {
    // session-check 失败，回退到登录
    status.value = 'login'
    loadCaptcha()
    loadTenants()
    return
  }

  try {
    // 提交 user_code 到设备验证端点
    const body = new URLSearchParams({ user_code: userCode.value })
    const response = await fetch('/oauth2/device_verification', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
      credentials: 'include',
      redirect: 'manual',
    })

    // SAS 验证成功时返回 302 重定向，manual 模式下为 opaqueredirect
    if (response.type === 'opaqueredirect' || response.status === 302) {
      status.value = 'success'
      return
    }

    const contentType = response.headers.get('content-type') || ''

    // SAS 可能返回 HTML 同意表单（当 requireAuthorizationConsent=true 时）
    if (contentType.includes('text/html')) {
      const html = await response.text()
      if (html.includes('consent_form')) {
        await submitDeviceConsent(html)
        return
      }
      // 其他 HTML 响应（如成功页面）
      status.value = 'success'
      return
    }

    if (response.ok) {
      status.value = 'success'
    } else {
      const text = await response.text().catch(() => '')
      errorMessage.value = text || `HTTP ${response.status}`
      status.value = 'error'
    }
  } catch {
    errorMessage.value = t('device.verifyFailed')
    status.value = 'error'
  }
}

/** 自动提交 SAS 设备授权同意表单（当 requireAuthorizationConsent=true 时使用）。
 * 
 * 解析服务器返回的 HTML 同意表单，提取 client_id、state 和所有 scope，
 * 然后构造第二个 POST 请求完成授权确认。
 * 
 * @param html - 服务器返回的 HTML 响应内容，包含 consent_form 表单
 */
async function submitDeviceConsent(html: string) {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')
  const form = doc.querySelector('form[name="consent_form"]')
  if (!form) {
    errorMessage.value = t('device.verifyFailed')
    status.value = 'error'
    return
  }

  // 从表单中提取隐藏字段：client_id 和 state（用于防止 CSRF）
  const clientId = (form.querySelector('input[name="client_id"]') as HTMLInputElement)?.value || ''
  const state = (form.querySelector('input[name="state"]') as HTMLInputElement)?.value || ''

  // 自动勾选所有请求的作用域（包括已选中和未选中的）
  const scopeCheckboxes = form.querySelectorAll<HTMLInputElement>('input[name="scope"]:checked, input[name="scope"]')
  const scopes: string[] = []
  scopeCheckboxes.forEach(cb => scopes.push(cb.value))

  if (scopes.length === 0) {
    errorMessage.value = t('device.verifyFailed')
    status.value = 'error'
    return
  }

  // 构造同意表单的请求体：client_id + state + user_code + 所有 scope
  const consentBody = new URLSearchParams({
    client_id: clientId,
    state: state,
    user_code: userCode.value,
  })
  scopes.forEach(s => consentBody.append('scope', s))

  // 第二步：提交同意表单到同一端点
  const response2 = await fetch('/oauth2/device_verification', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: consentBody.toString(),
    credentials: 'include',
    redirect: 'manual',
  })

  if (response2.type === 'opaqueredirect' || response2.status === 302 || response2.ok) {
    status.value = 'success'
  } else {
    errorMessage.value = `Consent failed: HTTP ${response2.status}`
    status.value = 'error'
  }
}

/** 拒绝授权 */
function handleDeny() {
  status.value = 'denied'
  ElMessage.info(t('device.authorized'))
}

/** 切换主题 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */

onMounted(async () => {
  // 从 URL 参数读取预填的 user_code
  const queryCode = route.query.user_code as string
  if (queryCode) {
    userCode.value = queryCode
  }

  // 优先检查前端 Pinia store 的 token
  if (isLoggedIn.value) {
    status.value = 'ready'
    return
  }

  // 检测 SAS 服务端 HttpSession 是否已认证（与 consent 页面逻辑一致）
  try {
    const res = await fetch('/api/auth/session-check', { credentials: 'include' })
    if (res.ok) {
      const json = await res.json()
      if (json.code === 200) {
        status.value = 'ready'
        return
      }
    }
  } catch { /* 忽略错误，回退到登录表单 */ }

  status.value = 'login'
  loadCaptcha()
  loadTenants()
})
</script>

<template>
  <div class="device-page">
    <!-- 顶部工具栏 -->
    <div class="device-top-bar">
      <div class="device-top-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          {{ t('common.home') }}
        </el-button>
      </div>
      <div class="device-top-right">
        <LanguageSelector />
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon>
            <Moon v-if="appStore.theme === 'dark'" />
            <Sunny v-else />
          </el-icon>
        </el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="device-main">
      <div class="device-card glass-surface">
        <div class="device-card-header">
          <el-icon :size="28" class="device-icon"><Key /></el-icon>
          <h2 class="device-title">{{ t('device.verifyTitle') }}</h2>
        </div>

        <!-- 检测会话状态 -->
        <template v-if="status === 'checking'">
          <div class="verify-result">
            <el-icon class="device-spinner" :size="40"><Loading /></el-icon>
            <p class="verify-result-text">{{ t('consent.checking') }}</p>
          </div>
        </template>

        <!-- 内嵌登录表单 -->
        <template v-else-if="status === 'login'">
          <div class="verify-login-banner">
            <el-icon><InfoFilled /></el-icon>
            <span>{{ t('device.loginFirst') }}</span>
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
                {{ t('device.loginAndVerify') }}
              </el-button>
            </el-form-item>
          </el-form>
        </template>

        <!-- 授权确认 -->
        <template v-else-if="status === 'ready' || status === 'submitting'">
          <p class="verify-instruction">{{ t('device.verifyInstruction') }}</p>
          <el-input
            v-model="userCode"
            :placeholder="t('device.userCodeLabel')"
            size="large"
            class="user-code-input"
            :disabled="status === 'submitting'"
          />
          <div class="verify-actions">
            <el-button
              type="primary"
              :loading="status === 'submitting'"
              class="verify-btn"
              @click="handleAuthorize"
            >
              {{ t('device.authorize') }}
            </el-button>
            <el-button
              :disabled="status === 'submitting'"
              class="verify-btn"
              @click="handleDeny"
            >
              {{ t('device.deny') }}
            </el-button>
          </div>
        </template>

        <!-- 验证成功 -->
        <template v-else-if="status === 'success'">
          <div class="verify-result">
            <el-icon :size="48" class="verify-success-icon"><CircleCheck /></el-icon>
            <p class="verify-result-text">{{ t('device.verifySuccess') }}</p>
          </div>
        </template>

        <!-- 验证失败 -->
        <template v-else-if="status === 'error'">
          <div class="verify-result">
            <el-icon :size="48" class="verify-error-icon"><CircleClose /></el-icon>
            <p class="verify-result-text error-text">{{ errorMessage }}</p>
            <el-button type="primary" @click="status = 'ready'">{{ t('device.retry') }}</el-button>
          </div>
        </template>

        <!-- 已拒绝 -->
        <template v-else-if="status === 'denied'">
          <div class="verify-result">
            <el-icon :size="48" class="verify-warning-icon"><WarningFilled /></el-icon>
            <p class="verify-result-text">{{ t('device.authorized') }}</p>
            <el-button @click="router.push({ name: 'Home' })">{{ t('common.home') }}</el-button>
          </div>
        </template>
      </div>
    </div>

    <!-- 页脚 -->
    <footer class="device-footer">
      <span>&copy; 2026 {{ t('common.appName') }}</span>
    </footer>
  </div>
</template>

<style scoped lang="scss">
.device-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
}

.device-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--omni-space-md) var(--omni-space-lg);
}

.device-top-left {
  display: flex;
  align-items: center;
}

.device-top-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
}

.device-main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: var(--omni-space-xl);
}

.device-card {
  width: 100%;
  max-width: 480px;
  padding: var(--omni-space-xl);
  animation: omni-fade-in-up 0.6s var(--omni-ease-smooth) both;
}

.device-card-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-sm);
  margin-bottom: var(--omni-space-lg);
}

.device-icon {
  color: var(--omni-color-primary);
}

.device-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--omni-text-primary);
}

.verify-login-banner {
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

.verify-instruction {
  font-size: 14px;
  color: var(--omni-text-secondary);
  text-align: center;
  margin: 0 0 var(--omni-space-md);
}

.user-code-input {
  text-align: center;
  font-family: 'Courier New', Courier, monospace;
  font-size: 18px;
  letter-spacing: 0.2em;
  margin-bottom: var(--omni-space-lg);
}

.verify-actions {
  display: flex;
  gap: var(--omni-space-md);
}

.verify-btn {
  flex: 1;
  height: 44px;
}

.verify-result {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-md);
  padding: var(--omni-space-lg) 0;
}

.verify-result-text {
  font-size: 15px;
  color: var(--omni-text-primary);
  text-align: center;
}

.verify-success-icon {
  color: var(--el-color-success);
}

.verify-error-icon {
  color: var(--el-color-danger);
}

.verify-warning-icon {
  color: var(--el-color-warning);
}

.error-text {
  color: var(--el-color-danger);
}

.device-spinner {
  color: var(--omni-color-primary);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.device-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}
</style>
