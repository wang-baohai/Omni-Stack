<script setup lang="ts">
/**
 * 官网首页组件。
 * 直接展示由仓库规范正文构建的公开 Wiki；登录状态只改变顶栏账户区。
 * 任务、审批与执行日志等个人私有功能已迁至独立受保护路由 /workspace。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { usePermissionStore } from '@/stores/permission'
import { hasManagementAccess, hasSupplierPortalAccess } from '@/utils/access'
import { getRolesFromToken } from '@/utils/jwt'
import LanguageSelector from '@/components/LanguageSelector.vue'
import { clearAuthenticatedSession } from '@/router'
import { isDeploymentModuleEnabled } from '@/utils/deployment-modules'

const { t, locale } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const permissionStore = usePermissionStore()
/** 同时要求部署选中 SRM 且生成物保留真实门户路由，避免裁剪后留下空入口。 */
const portalAvailable = computed(() => isDeploymentModuleEnabled('srm')
  && router.hasRoute('PortalLogin') && router.hasRoute('SupplierPortal'))
const wikiFrame = ref<HTMLIFrameElement | null>(null)
const wikiReady = ref(false)
const wikiError = ref(false)
const wikiAttempt = ref(0)
const wikiLocales = new Set(['zh-CN', 'en-US', 'ja-JP', 'ko-KR'])
const wikiSrc = computed(() => `/wiki/${wikiLocales.has(locale.value) ? locale.value : 'zh-CN'}/wiki/index.html`)

watch(wikiSrc, () => { wikiReady.value = false; wikiError.value = false })

/** 检查实际文档正文，避免将 404 或 SPA 回退误认为 Wiki 已加载。 */
function handleWikiLoad() {
  try {
    wikiError.value = !wikiFrame.value?.contentDocument?.querySelector('.vp-doc')
  } catch {
    wikiError.value = true
  }
  wikiReady.value = true
}

/** 只重载同源公开文档，不访问任何私有业务接口。 */
function retryWiki() {
  wikiReady.value = false
  wikiError.value = false
  wikiAttempt.value += 1
}

/** 复用 VitePress 自身主题偏好，保持顶栏与实际文档的主题一致。 */
watch(() => appStore.theme, (theme) => {
  try { localStorage.setItem('vitepress-theme-appearance', theme) } catch { /* 存储不可用时保留文档自身主题 */ }
}, { immediate: true })

function handleWikiThemeChange(event: StorageEvent) {
  if (event.key === 'vitepress-theme-appearance'
      && (event.newValue === 'light' || event.newValue === 'dark')
      && event.newValue !== appStore.theme) appStore.setTheme(event.newValue)
}

/** 是否显示内部工作台入口；供应商角色固定使用独立门户。 */
const canAccessConsole = computed(() => {
  return hasManagementAccess(
    permissionStore.permissions,
    getRolesFromToken(userStore.token),
  )
})

/** 仅门户身份（无管理端角色）的账号落点为供应商门户。 */
const canAccessPortal = computed(() => {
  if (!portalAvailable.value) return false
  return hasSupplierPortalAccess(
    permissionStore.permissions,
    getRolesFromToken(userStore.token),
  )
})

/** 跳转到登录页 */
function goToLogin() {
  router.push({ name: 'Login' })
}

/** 跳转到供应商门户登录页 */
function goToPortalLogin() {
  router.push({ name: 'PortalLogin' })
}

/** 跳转到供应商门户。 */
function goToPortal() {
  router.push('/supplier-portal')
}

/** 只解析工作台真实同源路径；新页签共享既有会话，链接不携带令牌。 */
const workspaceHref = computed(() => router.resolve('/workspace/overview').href)

/** 处理用户登出 */
function handleLogout() {
  clearAuthenticatedSession()
}

/** 切换主题模式 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/**
 * 加载权限与菜单快照，使顶栏按现行 RBAC 身份规则展示账户入口。
 * 该读取与既有路由守卫同源，只用于身份判定，不在首页拉取任何私有统计数据。
 */
async function loadPermissions() {
  if (!permissionStore.menusLoaded) {
    try {
      permissionStore.initFromToken()
      await permissionStore.loadMenus()
    } catch {
      /* 权限加载失败不阻塞页面 */
    }
  }
}

onMounted(async () => {
  window.addEventListener('storage', handleWikiThemeChange)
  if (userStore.isLoggedIn) {
    await loadPermissions()
  }
})
onUnmounted(() => window.removeEventListener('storage', handleWikiThemeChange))
</script>

<template>
  <div class="home-page">
    <!-- 顶部导航栏：Logo + 身份相关账户区 -->
    <header class="home-header">
      <div class="home-header-left">
        <span class="home-logo gradient-text">{{ t('common.appName') }}</span>
      </div>
      <div class="home-header-right">
        <!-- 语言切换 -->
        <LanguageSelector />
        <!-- 主题切换 -->
        <el-button text :title="t('theme.toggle')" @click="toggleTheme">
          <el-icon>
            <Moon v-if="appStore.theme === 'dark'" />
            <Sunny v-else />
          </el-icon>
        </el-button>
        <!-- 未登录状态：显示登录按钮 -->
        <template v-if="!userStore.isLoggedIn">
          <el-button v-if="portalAvailable" text @click="goToPortalLogin">{{ t('common.supplierPortal') }}</el-button>
          <el-button type="primary" @click="goToLogin">
            <el-icon><User /></el-icon>
            {{ t('common.login') }}
          </el-button>
        </template>
        <!-- 已登录状态：账户区仅改变这里，公开落地页正文保持不变 -->
        <template v-else>
          <el-dropdown>
            <span class="home-user-info">
              <el-avatar :size="32" class="home-avatar">
                <el-icon :size="18"><User /></el-icon>
              </el-avatar>
              <span class="home-username">{{ userStore.username || 'Admin' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <!-- 内部账号只有个人工作台入口，业务模块从工作台的新标签入口进入。 -->
          <a
            v-if="canAccessConsole"
            :href="workspaceHref"
            class="el-button el-button--primary home-workspace-entry"
            target="_blank"
            rel="noopener noreferrer"
            :title="t('common.openInNewTab')"
          >
            <el-icon><Grid /></el-icon>
            {{ t('workspace.title') }}
          </a>
          <!-- 仅门户身份：进入供应商门户 -->
          <el-button v-if="!canAccessConsole && canAccessPortal" type="primary" @click="goToPortal">
            <el-icon><Promotion /></el-icon>
            {{ t('common.supplierPortal') }}
          </el-button>
        </template>
      </div>
    </header>

    <!-- 正文来自同一镜像中的真实 Wiki，游客和登录用户阅读相同内容。 -->
    <main class="home-wiki" :aria-label="t('home.wikiTitle')">
      <div v-if="!wikiReady" class="wiki-message" role="status">{{ t('home.wikiLoading') }}</div>
      <div v-if="wikiError" class="wiki-message" role="alert">
        <p>{{ t('home.wikiUnavailable') }}</p>
        <el-button @click="retryWiki">{{ t('common.retry') }}</el-button>
        <a :href="wikiSrc" target="_blank" rel="noopener noreferrer">{{ t('home.openWiki') }}</a>
      </div>
      <iframe
        v-if="!wikiError"
        :key="`${wikiSrc}-${wikiAttempt}`"
        ref="wikiFrame"
        class="home-wiki-frame"
        :src="wikiSrc"
        :title="t('home.wikiTitle')"
        @load="handleWikiLoad"
        @error="wikiReady = true; wikiError = true"
      />
    </main>
  </div>
</template>

<style scoped lang="scss">
.home-workspace-entry { text-decoration: none; }

.home-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  background-color: var(--omni-bg-base);
  overflow-x: hidden;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--omni-space-xl);
  height: 72px;
  flex-shrink: 0;
  background: var(--omni-bg-glass);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--omni-border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

.home-header-left {
  display: flex;
  align-items: center;
}

.home-logo {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.home-header-right {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
}

.home-user-info {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
  cursor: pointer;
  color: var(--omni-text-primary);
  transition: color var(--omni-duration-fast);

  &:hover {
    color: var(--omni-text-accent);
  }
}

.home-avatar {
  background: var(--omni-gradient-primary);
}

.home-username {
  font-size: 14px;
  font-weight: 500;
  color: var(--omni-text-primary);
}

.home-wiki { flex: 1; min-height: 0; position: relative; }
.home-wiki-frame { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
.wiki-message { padding: 32px; text-align: center; }
.wiki-message a { margin-left: 16px; color: var(--omni-text-accent); }

@media (max-width: 640px) {
  .home-header { height: auto; min-height: 64px; padding: 12px 16px; flex-wrap: wrap; gap: 8px; }
  .home-logo { font-size: 18px; }
  .home-header-right { margin-left: auto; gap: 4px; flex-wrap: wrap; }
}
</style>
