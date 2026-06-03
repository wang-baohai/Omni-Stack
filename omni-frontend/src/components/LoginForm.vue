<script setup lang="ts">
/**
 * 登录表单组件。
 * 包含租户选择、用户名/密码输入、验证码、SSO（OAuth2 授权码）登录和第三方 OAuth2 登录入口。
 * 通过 emit 向父组件通知登录成功事件。
 * 支持 OAuth2 授权码模式：当 URL 含 oauth2=true 时，登录后自动跳转授权端点。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  loginByPassword,
  sessionLogin,
  fetchCaptcha,
  listTenants,
  buildAuthorizeUrl,
  getThirdPartyLoginUrl,
} from '@/api/auth'
import type { TenantOption } from '@/api/auth'
import { generateCodeVerifier, generateCodeChallenge, generateState } from '@/utils/pkce'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 向父组件发送登录成功事件 */
const emit = defineEmits<{
  loginSuccess: []
}>()

/** OAuth2 客户端配置（redirectUri 使用动态 origin，避免 localhost/127.0.0.1 不匹配） */
const OAUTH2_CLIENT_ID = import.meta.env.VITE_OAUTH2_CLIENT_ID || 'omni-frontend'

/** sessionStorage 中存储 PKCE 参数的 key */
const PKCE_STORAGE_KEYS = {
  codeVerifier: 'oauth2_code_verifier',
  state: 'oauth2_state',
  clientId: 'oauth2_client_id',
  redirectUri: 'oauth2_redirect_uri',
  authorizeUrl: 'oauth2_authorize_url',
}

/** 是否处于 OAuth2 模式（由授权服务器重定向回来） */
const isOAuth2Mode = computed(() => route.query.oauth2 === 'true')

/** 表单引用 */
const formRef = ref<FormInstance>()
/** 登录按钮加载状态 */
const loading = ref(false)
/** 验证码加载状态 */
const captchaLoading = ref(false)
/** 验证码 Base64 图片 */
const captchaImage = ref('')
/** 验证码 Key（UUID） */
const captchaKey = ref('')
/** 可用租户列表 */
const tenants = ref<TenantOption[]>([])

/** 表单数据模型 */
const form = reactive({
  tenantId: 1,
  username: 'admin',
  password: 'admin123',
  captchaCode: '',
})

/** 表单校验规则 */
const rules: FormRules = {
  tenantId: [{ required: true, message: () => t('login.tenantPlaceholder'), trigger: 'change' }],
  username: [{ required: true, message: () => t('login.username'), trigger: 'blur' }],
  password: [
    { required: true, message: () => t('login.password'), trigger: 'blur' },
    { min: 6, message: '密码至少 6 个字符', trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: () => t('login.captchaPlaceholder'), trigger: 'blur' }],
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
    ElMessage.error('验证码加载失败')
    captchaImage.value = ''
  } finally {
    captchaLoading.value = false
  }
}

/**
 * 加载可用租户列表。
 */
async function loadTenants() {
  try {
    const { data: res } = await listTenants()
    tenants.value = res.data
  } catch {
    ElMessage.error('租户列表加载失败')
    tenants.value = []
  }
}

/**
 * 处理登录提交。
 * 在 OAuth2 模式下使用 session-login 创建会话后自动跳转授权端点；
 * 在普通模式下使用密码登录获取 JWT。
 */
async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    if (isOAuth2Mode.value) {
      // OAuth2 模式：创建会话后跳转到授权端点
      await sessionLogin({
        username: form.username,
        password: form.password,
        tenantId: form.tenantId,
        captchaKey: captchaKey.value,
        captchaCode: form.captchaCode,
      })
      // 从 sessionStorage 获取之前存储的授权 URL
      const authorizeUrl = sessionStorage.getItem(PKCE_STORAGE_KEYS.authorizeUrl)
      if (authorizeUrl) {
        sessionStorage.removeItem(PKCE_STORAGE_KEYS.authorizeUrl)
        window.location.href = authorizeUrl
      } else {
        // 如果没有存储的授权 URL，重新发起 OAuth2 流程
        await initiateOAuth2Flow()
      }
    } else {
      // 普通密码登录模式
      const { data: res } = await loginByPassword({
        username: form.username,
        password: form.password,
        tenantId: form.tenantId,
        captchaKey: captchaKey.value,
        captchaCode: form.captchaCode,
      })
      userStore.setToken(res.data.accessToken)
      userStore.setUsername(form.username)
      emit('loginSuccess')
    }
  } catch {
    // 错误消息已由 Axios 响应拦截器展示
  } finally {
    loading.value = false
    loadCaptcha()
  }
}

/**
 * 发起 OAuth2 授权码流程。
 * 生成 PKCE 参数，存储到 sessionStorage，然后跳转到登录页（携带 oauth2=true 标记）。
 * 用户在登录页输入凭证后，自动完成 session-login 并重定向到授权端点。
 */
async function initiateOAuth2Flow() {
  const codeVerifier = generateCodeVerifier()
  const codeChallenge = await generateCodeChallenge(codeVerifier)
  const state = generateState()

  // 动态生成 redirectUri，基于当前页面的 origin（解决 localhost vs 127.0.0.1 同源问题）
  const dynamicRedirectUri = `${window.location.origin}/callback`

  // 存储 PKCE 参数到 sessionStorage（在回调页面中使用）
  sessionStorage.setItem(PKCE_STORAGE_KEYS.codeVerifier, codeVerifier)
  sessionStorage.setItem(PKCE_STORAGE_KEYS.state, state)
  sessionStorage.setItem(PKCE_STORAGE_KEYS.clientId, OAUTH2_CLIENT_ID)
  sessionStorage.setItem(PKCE_STORAGE_KEYS.redirectUri, dynamicRedirectUri)

  // 构建授权 URL 并存储（以便 session-login 后取回）
  const authorizeUrl = buildAuthorizeUrl({
    clientId: OAUTH2_CLIENT_ID,
    redirectUri: dynamicRedirectUri,
    state,
    codeChallenge,
  })
  sessionStorage.setItem(PKCE_STORAGE_KEYS.authorizeUrl, authorizeUrl)

  // 跳转到登录页（oauth2=true 标记），用户在此输入凭证
  router.push({ name: 'Login', query: { oauth2: 'true' } })
}

/**
 * 处理企业 SSO 登录（OAuth2 授权码模式）。
 */
async function handleSsoLogin() {
  await initiateOAuth2Flow()
}

/**
 * 处理第三方 OAuth2 登录。
 *
 * @param provider 第三方登录提供商标识
 */
function handleThirdPartyLogin(provider: string) {
  window.location.href = getThirdPartyLoginUrl(provider, form.tenantId)
}

/** 组件挂载后加载验证码和租户列表 */
onMounted(() => {
  loadCaptcha()
  loadTenants()
})
</script>

<template>
  <div class="login-form-wrapper">
    <!-- 表单头部：应用名称和副标题 -->
    <div class="login-form-header">
      <h2 class="login-form-title gradient-text">{{ t('common.appName') }}</h2>
      <p class="login-form-subtitle">{{ t('login.title') }}</p>
    </div>

    <!-- OAuth2 模式提示横幅 -->
    <div v-if="isOAuth2Mode" class="oauth2-banner">
      <el-icon class="oauth2-banner-icon"><Key /></el-icon>
      <div class="oauth2-banner-text">
        <strong>{{ t('login.oauth2ModeTitle') }}</strong>
        <span>{{ t('login.oauth2ModeTip') }}</span>
      </div>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      size="large"
      @submit.prevent="handleLogin"
    >
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

      <!-- 用户名输入 -->
      <el-form-item prop="username">
        <el-input
          v-model="form.username"
          :placeholder="t('login.username')"
          prefix-icon="User"
        />
      </el-form-item>

      <!-- 密码输入 -->
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          :placeholder="t('login.password')"
          prefix-icon="Lock"
          show-password
        />
      </el-form-item>

      <!-- 验证码输入 + 验证码图片 -->
      <el-form-item prop="captchaCode">
        <div class="captcha-row">
          <el-input
            v-model="form.captchaCode"
            :placeholder="t('login.captchaPlaceholder')"
            prefix-icon="Key"
            class="captcha-input"
            @keyup.enter="handleLogin"
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

      <!-- 登录按钮 -->
      <el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          native-type="submit"
          class="login-btn"
        >
          {{ isOAuth2Mode ? t('login.oauth2ModeTitle') : t('login.loginButton') }}
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 企业 SSO 登录（仅在非 OAuth2 模式下显示） -->
    <div v-if="!isOAuth2Mode" class="login-sso-section">
      <el-button class="sso-btn" @click="handleSsoLogin">
        <el-icon><OfficeBuilding /></el-icon>
        {{ t('login.ssoLogin') }}
      </el-button>
    </div>

    <!-- 分隔线 + 第三方登录（仅在非 OAuth2 模式下显示） -->
    <template v-if="!isOAuth2Mode">
      <!-- 分隔线 -->
      <div class="login-divider">
        <span>{{ t('login.thirdParty') }}</span>
      </div>

      <!-- 第三方 OAuth2 登录按钮 -->
      <div class="login-third-party">
        <button
          class="third-party-btn github"
          :title="t('login.github')"
          @click="handleThirdPartyLogin('github')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor">
            <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
          </svg>
        </button>
        <button
          class="third-party-btn google"
          :title="t('login.google')"
          @click="handleThirdPartyLogin('google')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22">
            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" />
            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" />
            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
          </svg>
        </button>
        <button
          class="third-party-btn wechat"
          :title="t('login.wechat')"
          @click="handleThirdPartyLogin('wechat')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="#07C160">
            <path d="M8.691 2.188C3.891 2.188 0 5.476 0 9.53c0 2.212 1.17 4.203 3.002 5.55a.59.59 0 01.213.665l-.39 1.48c-.019.07-.048.141-.048.213 0 .163.13.295.29.295a.328.328 0 00.167-.054l1.903-1.114a.864.864 0 01.717-.098 10.16 10.16 0 002.837.403c.276 0 .543-.027.811-.05-.857-2.578.157-4.972 1.932-6.446 1.703-1.415 3.882-1.98 5.853-1.838-.576-3.583-4.196-6.348-8.596-6.348zM5.785 5.991c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178A1.17 1.17 0 014.623 7.17c0-.651.52-1.18 1.162-1.18zm5.813 0c.642 0 1.162.529 1.162 1.18a1.17 1.17 0 01-1.162 1.178 1.17 1.17 0 01-1.162-1.178c0-.651.52-1.18 1.162-1.18zm3.338 4.356c-1.797-.058-3.622.503-4.97 1.652-1.618 1.38-2.378 3.354-1.636 5.382.673 1.843 2.413 3.076 4.498 3.635 1.677.45 3.556.385 5.107-.241a.68.68 0 01.553.074l1.459.858a.256.256 0 00.13.04.227.227 0 00.224-.228c0-.056-.022-.11-.037-.164l-.3-1.14a.46.46 0 01.165-.514c1.417-1.045 2.324-2.595 2.324-4.304 0-2.847-2.612-5.03-6.517-5.05zM14.5 13.11c.494 0 .895.407.895.91a.9.9 0 01-.895.907.9.9 0 01-.895-.908c0-.502.4-.909.895-.909zm4.5 0c.494 0 .894.407.894.91a.9.9 0 01-.894.907.9.9 0 01-.896-.908c0-.502.4-.909.895-.909z" />
          </svg>
        </button>
        <button
          class="third-party-btn gitee"
          :title="t('login.gitee')"
          @click="handleThirdPartyLogin('gitee')"
        >
          <svg viewBox="0 0 24 24" width="22" height="22" fill="#C71D23">
            <path d="M11.984 0A12 12 0 000 12a12 12 0 0012 12 12 12 0 0012-12A12 12 0 0012 0a12 12 0 00-.016 0zm6.09 5.333c.328 0 .593.266.592.593v1.482a.594.594 0 01-.593.592h-4.21c-.682 0-1.235.553-1.235 1.235v.593h4.846c.327 0 .593.265.593.592v1.482a.594.594 0 01-.593.593h-4.846v2.37c0 .682-.553 1.235-1.235 1.235H9.92a.593.593 0 01-.593-.593v-2.43H5.925a.593.593 0 01-.593-.592v-1.482c0-.328.266-.593.593-.593h3.402v-.593c0-.682.554-1.235 1.236-1.235h.593V6.52c0-.328.265-.593.592-.593h4.238c.034-.006.065-.006.098-.006l.004.012z" />
          </svg>
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.login-form-wrapper {
  width: 100%;
}

.login-form-header {
  text-align: center;
  margin-bottom: var(--omni-space-lg);
}

.login-form-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
}

.login-form-subtitle {
  margin: 8px 0 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.oauth2-banner {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: var(--omni-space-md);
  border-radius: var(--omni-radius-md);
  background: var(--omni-bg-glass);
  border: 1px solid var(--omni-border-glass);
}

.oauth2-banner-icon {
  flex-shrink: 0;
  font-size: 20px;
  color: var(--omni-color-primary);
  margin-top: 2px;
}

.oauth2-banner-text {
  display: flex;
  flex-direction: column;
  gap: 4px;

  strong {
    font-size: 14px;
    color: var(--omni-text-primary);
  }

  span {
    font-size: 12px;
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

.login-sso-section {
  margin-top: var(--omni-space-md);
}

.sso-btn {
  width: 100%;
  height: 40px;
}

.login-divider {
  display: flex;
  align-items: center;
  margin: var(--omni-space-lg) 0 var(--omni-space-md);
  gap: var(--omni-space-md);

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: var(--omni-border-color);
  }

  span {
    font-size: 12px;
    color: var(--omni-text-tertiary);
    white-space: nowrap;
  }
}

.login-third-party {
  display: flex;
  justify-content: center;
  gap: var(--omni-space-lg);
}

.third-party-btn {
  width: 44px;
  height: 44px;
  border-radius: var(--omni-radius-full);
  border: 1px solid var(--omni-border-glass);
  background: var(--omni-bg-glass);
  color: var(--omni-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all var(--omni-duration-fast) var(--omni-ease-smooth);

  &:hover {
    border-color: var(--omni-border-glow);
    color: var(--omni-text-accent);
    background: var(--omni-bg-glass-hover);
    transform: translateY(-2px);
    box-shadow: var(--omni-shadow-glow);
  }

  &.github:hover {
    color: var(--omni-text-primary);
  }
}
</style>
