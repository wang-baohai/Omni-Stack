<script setup lang="ts">
/**
 * 设备授权页面（模拟 IoT 设备）。
 * 发起设备授权请求，展示 user_code 和验证链接，轮询 token。
 */
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import LanguageSelector from '@/components/LanguageSelector.vue'
import {
  requestDeviceAuthorization,
  pollDeviceToken,
  DEVICE_CLIENT_ID,
} from '@/api/auth'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 状态：idle -> waiting -> success / expired / error */
type DeviceStatus = 'idle' | 'waiting' | 'success' | 'expired' | 'error'
const status = ref<DeviceStatus>('idle')
const loading = ref(true)
const userCode = ref('')
const verifyUrl = ref('')
const countdown = ref(0)
const errorMessage = ref('')
const qrCodeDataUrl = ref('')

/** 设备码（用于轮询 token，不展示给用户） */
let deviceCode = ''
/** 轮询 token 的定时器引用 */
let pollTimer: ReturnType<typeof setInterval> | null = null
/** 倒计时定时器引用 */
let countdownTimer: ReturnType<typeof setInterval> | null = null

/** 发起设备授权流程 */
async function initDeviceFlow() {
  loading.value = true
  status.value = 'idle'
  errorMessage.value = ''
  try {
    const resp = await requestDeviceAuthorization()
    deviceCode = resp.device_code
    userCode.value = resp.user_code
    // 构造前端验证页 URL（不使用 SAS 返回的 verification_uri）
    verifyUrl.value = `${window.location.origin}/device/verify?user_code=${encodeURIComponent(resp.user_code)}`
    countdown.value = resp.expires_in
    status.value = 'waiting'
    loading.value = false

    // 生成验证页 QR 码（编码完整的 verify URL，用户扫码后直接跳转到验证页面并预填 user_code）
    qrCodeDataUrl.value = await QRCode.toDataURL(verifyUrl.value, {
      width: 360,
      margin: 2,
      color: { dark: '#000000', light: '#ffffff' },
    })

    // 启动倒计时
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearTimers()
        status.value = 'expired'
      }
    }, 1000)

    // 启动轮询
    const intervalMs = (resp.interval || 5) * 1000
    pollTimer = setInterval(() => pollOnce(), intervalMs)
  } catch {
    status.value = 'error'
    errorMessage.value = t('device.requestFailed')
    loading.value = false
  }
}

/** 单次轮询 */
async function pollOnce() {
  try {
    const token = await pollDeviceToken(deviceCode, DEVICE_CLIENT_ID)
    if (token) {
      clearTimers()
      status.value = 'success'
      userStore.setToken(token.access_token)
      const username = extractUsernameFromJwt(token.access_token)
      if (username) userStore.setUsername(username)
      ElMessage.success(t('device.success'))
      setTimeout(() => router.replace({ name: 'Dashboard' }), 1000)
    }
    // token 为 null 表示 authorization_pending 或 slow_down，继续轮询
  } catch (err) {
    const msg = err instanceof Error ? err.message : ''
    // 临时性网络错误：不停止轮询，等待下一次重试
    if (msg.includes('Failed to fetch') || msg.includes('NetworkError') || msg.includes('timeout')) {
      return
    }
    // 永久错误：停止轮询并展示错误信息
    clearTimers()
    status.value = 'error'
    if (msg.includes('expired_token')) {
      errorMessage.value = t('device.expired')
    } else if (msg.includes('access_denied')) {
      errorMessage.value = t('device.authorized')
    } else {
      errorMessage.value = t('device.pollFailed')
    }
  }
}

/** 清除所有定时器 */
function clearTimers() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

/** 从 JWT 中提取用户名（不做签名验证） */
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

/** 切换主题 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 切换语言 */

/** 格式化倒计时 */
function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

onMounted(() => initDeviceFlow())
onUnmounted(() => clearTimers())
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
          <el-icon :size="28" class="device-icon"><Monitor /></el-icon>
          <h2 class="device-title">{{ t('device.title') }}</h2>
        </div>

        <!-- 加载状态 -->
        <div v-if="status === 'idle'" class="device-status">
          <el-icon class="device-spinner" :size="40"><Loading /></el-icon>
          <p class="device-status-text">{{ t('device.requesting') }}</p>
        </div>

        <!-- 等待授权状态 -->
        <div v-else-if="status === 'waiting'" class="device-status">
          <p class="device-instruction">{{ t('device.instruction') }}</p>
          <div class="user-code-display">
            <span class="user-code-text">{{ userCode }}</span>
          </div>
          <div v-if="qrCodeDataUrl" class="qr-code-section">
            <img :src="qrCodeDataUrl" :alt="t('device.scanQrCode')" class="qr-code-image" />
            <p class="qr-code-label">{{ t('device.scanQrCode') }}</p>
          </div>
          <p class="verify-link-label">{{ t('device.orManual') }}</p>
          <div class="verify-link-section">
            <a :href="verifyUrl" target="_blank" rel="noopener" class="verify-link">
              {{ verifyUrl }}
            </a>
          </div>
          <div class="device-countdown">
            <el-icon><Timer /></el-icon>
            <span>{{ formatCountdown(countdown) }}</span>
          </div>
          <div class="device-waiting">
            <el-icon class="device-spinner-sm" :size="16"><Loading /></el-icon>
            <span>{{ t('device.waiting') }}</span>
          </div>
        </div>

        <!-- 成功状态 -->
        <div v-else-if="status === 'success'" class="device-status">
          <el-icon :size="48" class="device-success-icon"><CircleCheck /></el-icon>
          <p class="device-status-text success-text">{{ t('device.success') }}</p>
        </div>

        <!-- 过期状态 -->
        <div v-else-if="status === 'expired'" class="device-status">
          <el-icon :size="48" class="device-warning-icon"><WarningFilled /></el-icon>
          <p class="device-status-text">{{ t('device.expired') }}</p>
          <el-button type="primary" class="device-retry-btn" @click="initDeviceFlow">
            {{ t('device.retry') }}
          </el-button>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="status === 'error'" class="device-status">
          <el-icon :size="48" class="device-error-icon"><CircleClose /></el-icon>
          <p class="device-status-text error-text">{{ errorMessage }}</p>
          <el-button type="primary" class="device-retry-btn" @click="initDeviceFlow">
            {{ t('device.retry') }}
          </el-button>
        </div>
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

.device-status {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-md);
}

.device-spinner {
  color: var(--omni-color-primary);
  animation: spin 1s linear infinite;
}

.device-spinner-sm {
  animation: spin 1s linear infinite;
  color: var(--omni-text-secondary);
}

.device-status-text {
  font-size: 14px;
  color: var(--omni-text-secondary);
  text-align: center;
}

.success-text {
  color: var(--el-color-success);
  font-weight: 500;
}

.error-text {
  color: var(--el-color-danger);
}

.device-instruction {
  font-size: 14px;
  color: var(--omni-text-secondary);
  text-align: center;
  margin: 0;
  line-height: 1.6;
}

.user-code-display {
  padding: var(--omni-space-lg) var(--omni-space-xl);
  border-radius: var(--omni-radius-lg);
  background: var(--omni-bg-glass);
  border: 2px dashed var(--omni-color-primary);
  text-align: center;
  width: 100%;
}

.user-code-text {
  font-family: 'Courier New', Courier, monospace;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 0.3em;
  color: var(--omni-text-primary);
  user-select: all;
}

.qr-code-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--omni-space-xs);
}

.qr-code-image {
  width: 180px;
  height: 180px;
  border-radius: var(--omni-radius-md);
  border: 1px solid var(--omni-border-color);
}

.qr-code-label {
  font-size: 13px;
  color: var(--omni-text-secondary);
  margin: 0;
}

.verify-link-label {
  font-size: 12px;
  color: var(--omni-text-tertiary);
  margin: 0;
}

.verify-link-section {
  width: 100%;
  text-align: center;
  word-break: break-all;
}

.verify-link {
  font-size: 13px;
  color: var(--omni-color-primary);
  text-decoration: none;
  transition: opacity var(--omni-duration-fast);

  &:hover {
    text-decoration: underline;
    opacity: 0.8;
  }
}

.device-countdown {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
  font-size: 14px;
  color: var(--omni-text-secondary);
}

.device-waiting {
  display: flex;
  align-items: center;
  gap: var(--omni-space-xs);
  font-size: 13px;
  color: var(--omni-text-tertiary);
}

.device-success-icon {
  color: var(--el-color-success);
}

.device-warning-icon {
  color: var(--el-color-warning);
}

.device-error-icon {
  color: var(--el-color-danger);
}

.device-retry-btn {
  margin-top: var(--omni-space-sm);
}

.device-footer {
  text-align: center;
  padding: var(--omni-space-lg);
  color: var(--omni-text-tertiary);
  font-size: 13px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
