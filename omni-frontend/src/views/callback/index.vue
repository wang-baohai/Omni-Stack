<script setup lang="ts">
/**
 * OAuth2 授权码回调页面。
 * 处理授权服务器重定向，提取 authorization code 并换取访问令牌。
 */
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { exchangeCodeForToken } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

/** 加载状态 */
const loading = ref(true)
/** 提示信息 */
const message = ref('Completing login...')

/** sessionStorage 中存储 PKCE 参数的 key */
const STORAGE_KEY_CODE_VERIFIER = 'oauth2_code_verifier'
const STORAGE_KEY_STATE = 'oauth2_state'
const STORAGE_KEY_CLIENT_ID = 'oauth2_client_id'
const STORAGE_KEY_REDIRECT_URI = 'oauth2_redirect_uri'

/** OAuth2 客户端配置（redirectUri 使用动态 origin 作为 fallback） */
const CLIENT_ID = import.meta.env.VITE_OAUTH2_CLIENT_ID || 'omni-frontend'
const REDIRECT_URI = import.meta.env.VITE_OAUTH2_REDIRECT_URI || `${window.location.origin}/callback`

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  const error = route.query.error as string

  // 处理错误响应
  if (error) {
    ElMessage.error(`OAuth2 登录失败: ${error}`)
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 验证 code 参数
  if (!code) {
    ElMessage.error('缺少授权码')
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 验证 state 参数（CSRF 防护）
  const storedState = sessionStorage.getItem(STORAGE_KEY_STATE)
  if (state !== storedState) {
    ElMessage.error('安全验证失败，请重新登录')
    loading.value = false
    router.replace({ name: 'Login' })
    return
  }

  // 获取 PKCE 参数
  const codeVerifier = sessionStorage.getItem(STORAGE_KEY_CODE_VERIFIER)
  if (!codeVerifier) {
    ElMessage.error('PKCE 参数缺失，请重新登录')
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
    message.value = '正在换取访问令牌...'
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

    ElMessage.success('登录成功')
    router.replace({ name: 'Dashboard' })
  } catch {
    ElMessage.error('令牌换取失败，请重新登录')
    router.replace({ name: 'Login' })
  } finally {
    loading.value = false
  }
})

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
