<script setup lang="ts">
/**
 * OAuth2 授权码回调页面。
 * 同时处理两种回调流程：
 * 1. 社交登录回调（GitHub 等）：JWT 通过 URL fragment（#token=xxx）传入
 * 2. OAuth2 授权码回调（企业 SSO）：通过 authorization code + PKCE 换取令牌
 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exchangeCodeForToken } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

/** 加载状态 */
const loading = ref(true)
/** 提示信息 */
const message = ref(t('oauthCallback.completingLogin'))

/** sessionStorage 中存储 PKCE 参数的 key */
const STORAGE_KEY_CODE_VERIFIER = 'oauth2_code_verifier'
const STORAGE_KEY_STATE = 'oauth2_state'
const STORAGE_KEY_CLIENT_ID = 'oauth2_client_id'
const STORAGE_KEY_REDIRECT_URI = 'oauth2_redirect_uri'

/** OAuth2 客户端配置（redirectUri 使用动态 origin 作为 fallback） */
const CLIENT_ID = import.meta.env.VITE_OAUTH2_CLIENT_ID || 'omni-frontend'
const REDIRECT_URI = import.meta.env.VITE_OAUTH2_REDIRECT_URI || `${window.location.origin}/callback`

onMounted(async () => {
  // 优先检查社交登录回调（URL fragment 中的 token）
  if (handleSocialLoginCallback()) {
    return
  }

  // 否则走 OAuth2 授权码 + PKCE 流程
  await handleOAuth2CodeCallback()
})

/**
 * 处理社交登录回调。
 * 后端重定向到 /callback#token=xxx&username=yyy，从 URL hash 中提取 JWT。
 * @returns true 表示已处理（无论成功或失败），false 表示不是社交登录回调
 */
function handleSocialLoginCallback(): boolean {
  const hash = window.location.hash
  if (!hash || !hash.includes('token=') && !hash.includes('error=')) {
    return false
  }

  const params = parseHashParams(hash)

  // 处理错误
  if (params.error) {
    ElMessage.error(decodeURIComponent(params.error))
    loading.value = false
    router.replace({ name: 'Login' })
    return true
  }

  const token = params.token
  if (!token) {
    return false
  }

  // 清理 URL 中的 hash（避免 JWT 留在地址栏）
  history.replaceState(null, '', window.location.pathname)

  // 存储令牌
  userStore.setToken(token)
  const username = params.username
    ? decodeURIComponent(params.username)
    : extractUsernameFromJwt(token)
  if (username) {
    userStore.setUsername(username)
  }

  ElMessage.success(t('login.loginSuccess'))
  loading.value = false
  router.replace({ name: 'Dashboard' })
  return true
}

/**
 * 处理 OAuth2 授权码 + PKCE 回调（企业 SSO 流程）。
 */
async function handleOAuth2CodeCallback() {
  const code = route.query.code as string
  const state = route.query.state as string
  const error = route.query.error as string

  // 处理错误响应
  if (error) {
    ElMessage.error(t('oauthCallback.loginFailed', { error }))
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 验证 code 参数
  if (!code) {
    ElMessage.error(t('oauthCallback.missingCode'))
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 验证 state 参数（CSRF 防护）
  const storedState = sessionStorage.getItem(STORAGE_KEY_STATE)
  if (state !== storedState) {
    ElMessage.error(t('oauthCallback.stateValidationFailed'))
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 获取 PKCE 参数
  const codeVerifier = sessionStorage.getItem(STORAGE_KEY_CODE_VERIFIER)
  if (!codeVerifier) {
    ElMessage.error(t('oauthCallback.missingPkce'))
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 在清理前读取 clientId 和 redirectUri（避免读取时序错误）
  const clientId = sessionStorage.getItem(STORAGE_KEY_CLIENT_ID) || CLIENT_ID
  const redirectUri = sessionStorage.getItem(STORAGE_KEY_REDIRECT_URI) || REDIRECT_URI

  // 清理 sessionStorage
  sessionStorage.removeItem(STORAGE_KEY_CODE_VERIFIER)
  sessionStorage.removeItem(STORAGE_KEY_STATE)
  sessionStorage.removeItem(STORAGE_KEY_CLIENT_ID)
  sessionStorage.removeItem(STORAGE_KEY_REDIRECT_URI)

  try {
    message.value = t('oauthCallback.exchangingToken')
    const tokenResponse = await exchangeCodeForToken({
      code,
      codeVerifier,
      clientId,
      redirectUri,
    })

    // 存储令牌
    userStore.setToken(tokenResponse.access_token)

    // 从 JWT 中提取用户名
    const username = extractUsernameFromJwt(tokenResponse.access_token)
    if (username) {
      userStore.setUsername(username)
    }

    ElMessage.success(t('login.loginSuccess'))
    router.replace({ name: 'Dashboard' })
  } catch {
    ElMessage.error(t('oauthCallback.exchangeFailed'))
    router.replace({ name: 'Login' })
  } finally {
    loading.value = false
  }
}

/**
 * 解析 URL hash 中的键值对。
 * 例如 "#token=xxx&username=yyy" → { token: "xxx", username: "yyy" }
 */
function parseHashParams(hash: string): Record<string, string> {
  const result: Record<string, string> = {}
  const raw = hash.startsWith('#') ? hash.slice(1) : hash
  for (const pair of raw.split('&')) {
    const [key, ...rest] = pair.split('=')
    if (key) {
      result[key] = rest.join('=')
    }
  }
  return result
}

/**
 * 从 JWT payload 中提取用户名（不做签名验证，Gateway 会验证）。
 */
function extractUsernameFromJwt(token: string): string | null {
  try {
    const parts = token.split('.')
    if (parts.length !== 3) return null
    const payload = JSON.parse(atob(parts[1]))
    return payload.username || payload.sub || null
  } catch {
    return null
  }
}
</script>

<template>
  <div class="callback-container">
    <div class="callback-content">
      <el-icon v-if="loading" class="callback-spinner" :size="48">
        <Loading />
      </el-icon>
      <p class="callback-message">{{ message }}</p>
    </div>
  </div>
</template>

<style scoped>
.callback-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--omni-bg-primary, #f5f5f5);
}

.callback-content {
  text-align: center;
}

.callback-spinner {
  animation: spin 1s linear infinite;
  color: var(--el-color-primary);
}

.callback-message {
  margin-top: 16px;
  color: var(--omni-text-secondary, #666);
  font-size: 14px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
