<script setup lang="ts">
/**
 * @module views/workspace/index
 * 工作台概览页：账户信息、待办摘要、应用目录、最近访问与收藏五张卡片共用同一份统一导航派生集合。
 * 账户区只读当前登录状态的用户名与 JWT 角色，不额外发请求，也不展示本项目里不存在的会员等级或云资源配额；
 * 应用目录与抽屉取同一个模块集合，入口按同一方案解析为同源真实 href 并在新标签打开，
 * 菜单计数取同一份叶子派生结果，不重拼路径也不合成假目标；
 * 审批与个人任务复用已有视图，嵌入同一驾驶舱；统计由共享 Store 取真实值并独立处理失败。
 * 工作流视图由可选模块映射装载，裁剪预设没有该文件时不产生静态依赖或请求；
 * 最近访问与收藏读同一份「租户 + 稳定用户」作用域下的工作台偏好记录（stores/workspace），
 * 加载后立刻与当前可见集合求交：失权项、跨账号项与损坏记录里的 ID 都不再出现，收藏本身也永不授予访问权限；
 * 收藏条目只持久化稳定 ID，标题与目标每次由统一导航重新解析，不缓存旧标题也不合成假目标；
 * 最近访问的写入由 S04 的成功导航钩子负责，本页只渲染已存在的记录，记录为空时保持真实空态；
 * 加载中/加载失败/无可见应用三种状态复用同一份 menuLoadState，失败态给重试入口而不粉饰成空集合。
 */
import { computed, defineAsyncComponent, nextTick, onMounted, watch, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { useWorkspaceStore } from '@/stores/workspace'
import { useApplicationNavigation } from '@/composables/useApplicationNavigation'
import { getRolesFromToken } from '@/utils/jwt'
import WorkspaceEntryList from '@/components/workspace/WorkspaceEntryList.vue'
import WorkspaceStatsCard from '@/components/workspace/WorkspaceStatsCard.vue'
import WorkspaceJobs from '@/views/workspace/jobs/index.vue'
import type { NavigationEntry } from '@/utils/application-navigation'

const { t, te } = useI18n()
const router = useRouter()
const route = useRoute()
const permissionStore = usePermissionStore()
const userStore = useUserStore()
const workspaceStore = useWorkspaceStore()
const { menuLoadState } = storeToRefs(permissionStore)

/** 可选审批视图只在当前生成物包含该模块时解析，避免核心预设留下缺失导入。 */
const approvalModules = import.meta.glob('/src/views/workspace/approvals/index.vue')
const approvalLoader = approvalModules['/src/views/workspace/approvals/index.vue']
const WorkspaceApprovals = approvalLoader
  ? defineAsyncComponent(approvalLoader as () => Promise<{ default: Component }>)
  : null
/** 同时检查实际模块和当前权限，失权时卸载审批面板。 */
const showWorkflow = computed(() =>
  WorkspaceApprovals !== null && permissionStore.permissions.some((code) => code === 'workflow' || code.startsWith('workflow:')),
)
const { modules, pages, pagesOfApp } = useApplicationNavigation()

/** 标题解析与侧栏、抽屉一致：有 i18n 键取译文，缺键回退后端名称，不编造标题。 */
function titleOf(titleKey: string | null, fallback: string): string {
  return titleKey !== null && te(titleKey) ? t(titleKey) : fallback
}

/** 账户身份只取当前登录状态；未登录时角色为空数组，不用空令牌解出假角色。 */
const account = computed(() => ({
  name: userStore.username,
  roles: userStore.token === '' ? [] : getRolesFromToken(userStore.token),
}))

/** 头像文字只取用户名首字符大写，不引入外部图片，也不伪造昵称。 */
const accountInitial = computed(() => account.value.name.slice(0, 1).toUpperCase())

/** 加载与失败态沿用同一份菜单加载状态，不把失败粉饰成空集合。 */
const isLoading = computed(() => menuLoadState.value === 'idle' || menuLoadState.value === 'loading')
const isFailed = computed(() => menuLoadState.value === 'failed')

/**
 * 模块入口 href：与抽屉同源方案，只由路由表解析派生；
 * matched 为空表示该目标当前不可解析，返回 null 交由列表渲染为禁用项，绝不合成新标签死链。
 */
function moduleEntryHref(routePath: string | null): string | null {
  if (routePath === null) return null
  const resolved = router.resolve(routePath)
  return resolved.matched.length > 0 ? resolved.href : null
}

/** 应用目录条目：模块集合与抽屉共用，计数取同一份叶子派生结果，不另发请求。 */
const appEntries = computed(() =>
  modules.value.map((app) => ({
    id: String(app.id),
    title: titleOf(app.titleKey, app.fallbackTitle),
    icon: app.icon,
    meta: t('common.appMenuCount', { count: pagesOfApp(app.id).length }),
    href: moduleEntryHref(app.routePath),
    routePath: app.routePath,
  })),
)

/** 失败后只重试菜单本身，不重载整页或改写其他状态。 */
function retryMenus(): void {
  void permissionStore.retryLoadMenus().catch(() => undefined)
}

/** 可见导航索引：模块与叶子按稳定 ID 建表，收藏与最近访问的标题、目标和可见性只从这张表取。 */
const visibleEntries = computed(() => {
  const map = new Map<string, NavigationEntry>()
  for (const entry of [...modules.value, ...pages.value]) map.set(String(entry.id), entry)
  return map
})

/** 记录 ID → 概览条目：标题与归属每次重新解析，目标仍由路由表派生，不缓存也不合成。 */
function entryOf(entry: NavigationEntry): {
  id: string
  title: string
  meta: string
  href: string | null
  routePath: string | null
} {
  const app = modules.value.find((module) => module.id === entry.appId)
  return {
    id: String(entry.id),
    title: titleOf(entry.titleKey, entry.fallbackTitle),
    meta: app === undefined ? entry.appCode : titleOf(app.titleKey, app.fallbackTitle),
    href: moduleEntryHref(entry.routePath),
    routePath: entry.routePath,
  }
}

/** 收藏条目：按记录的稳定顺序与当前可见集合求交，失权或跨账号 ID 自然不再出现。 */
function entriesOf(ids: string[]): ReturnType<typeof entryOf>[] {
  return ids
    .map((id) => visibleEntries.value.get(id))
    .filter((entry): entry is NavigationEntry => entry !== undefined)
    .map((entry) => entryOf(entry))
}

const favoriteEntries = computed(() => entriesOf(workspaceStore.favoriteIds))
const recentEntries = computed(() => entriesOf(workspaceStore.recentIds))

/** 收藏写入只接受当前可见集合里的 ID：收藏永远给不出访问权限，也存不进不可见的目标。 */
function toggleFavorite(id: string): void {
  if (!visibleEntries.value.has(id)) return
  workspaceStore.toggleFavorite(id)
}

/** 子面板写操作成功后只刷新对应真实统计；并发由 Store 合并，不重载整个工作台。 */
function refreshStats(cardId: 'jobs' | 'workflow'): void {
  void workspaceStore.loadCard(cardId)
}

/** 兼容旧地址和同页锚点，仅定位已登记面板；等待实际 DOM 挂载后滚动。 */
async function scrollToPanel(): Promise<void> {
  if (route.hash !== '#approvals' && route.hash !== '#my-tasks') return
  await nextTick()
  document.getElementById(route.hash.slice(1))?.scrollIntoView({ block: 'start' })
}
watch(() => route.hash, scrollToPanel, { flush: 'post' })

/** 挂载时载入当前身份偏好并定位实际面板；身份变化由 Store 负责。 */
onMounted(() => workspaceStore.loadPreferences())
onMounted(scrollToPanel)
</script>

<template>
  <div class="ws-overview">
    <header class="ws-hero">
      <div>
        <h1>{{ t('workspace.title') }}</h1>
        <p>{{ t('workspace.overview.todo') }} · {{ t('workspace.overview.apps') }}</p>
      </div>
      <el-button @click="refreshStats('jobs'); showWorkflow && refreshStats('workflow')">
        <el-icon><Refresh /></el-icon>{{ t('common.refresh') }}
      </el-button>
    </header>

    <section class="ws-card ws-card--account" aria-labelledby="ws-overview-account">
      <h2 id="ws-overview-account" class="ws-card__title">{{ t('workspace.overview.account') }}</h2>
      <div class="ws-account">
        <el-avatar :size="52" class="ws-account__avatar">{{ accountInitial }}</el-avatar>
        <div class="ws-account__meta">
          <p class="ws-account__name">{{ account.name }}</p>
          <div v-if="account.roles.length > 0" class="ws-account__roles">
            <span class="ws-account__roles-label">{{ t('workspace.overview.roles') }}</span>
            <el-tag v-for="role in account.roles" :key="role" type="info" size="small">{{ role }}</el-tag>
          </div>
        </div>
      </div>
    </section>

    <section class="ws-card ws-card--todo" aria-labelledby="ws-overview-todo">
      <h2 id="ws-overview-todo" class="ws-card__title">{{ t('workspace.overview.todo') }}</h2>
      <!-- 同页定位实际操作面板，保留浏览器原生锚点与键盘导航。 -->
      <ul class="ws-todo">
        <li v-if="showWorkflow">
          <a class="ws-todo__link" href="#approvals">{{ t('workflow.todo') }}</a>
        </li>
        <li>
          <a class="ws-todo__link" href="#my-tasks">{{ t('workspace.myJobs') }}</a>
        </li>
      </ul>
    </section>

    <!-- 统计卡各自独立：组件挂载时先判定模块与权限再请求已有接口，一卡失败既不重载菜单也不清空其他卡。 -->
    <WorkspaceStatsCard card-id="jobs" title-key="workspace.stats.jobsTitle" />
    <template v-if="showWorkflow">
      <WorkspaceStatsCard card-id="workflow" title-key="workspace.stats.workflowTitle" />
    </template>

    <section class="ws-card ws-card--apps" aria-labelledby="ws-overview-apps">
      <h2 id="ws-overview-apps" class="ws-card__title">{{ t('workspace.overview.apps') }}</h2>
      <p v-if="isLoading" class="ws-card__status" role="status">{{ t('common.loading') }}</p>
      <div v-else-if="isFailed" class="ws-card__status ws-card__status--failed" role="alert">
        <span>{{ t('request.menuLoadFailed') }}</span>
        <el-button text type="primary" @click="retryMenus">
          <el-icon><Refresh /></el-icon>
          <span>{{ t('common.retry') }}</span>
        </el-button>
      </div>
      <WorkspaceEntryList
        v-else
        :items="appEntries"
        :empty-text="t('common.noAccessibleApp')"
        :new-tab-title="t('common.openInNewTab')"
        :favorite-ids="workspaceStore.favoriteIds"
        :favorite-title="t('common.favorite')"
        :unfavorite-title="t('common.unfavorite')"
        @toggle-favorite="toggleFavorite"
      />
    </section>

    <section class="ws-card ws-card--recent" aria-labelledby="ws-overview-recent">
      <h2 id="ws-overview-recent" class="ws-card__title">{{ t('workspace.overview.recent') }}</h2>
      <!-- 记录由 S04 的成功导航钩子写入；菜单未加载或加载失败时不做求交，避免把无法判定的 ID 当成可见项渲染。 -->
      <p v-if="isLoading" class="ws-card__status" role="status">{{ t('common.loading') }}</p>
      <p v-else-if="isFailed" class="ws-card__status" role="alert">{{ t('request.menuLoadFailed') }}</p>
      <WorkspaceEntryList
        v-else
        :items="recentEntries"
        :empty-text="t('workspace.overview.noRecent')"
        :new-tab-title="t('common.openInNewTab')"
        :favorite-ids="workspaceStore.favoriteIds"
        :favorite-title="t('common.favorite')"
        :unfavorite-title="t('common.unfavorite')"
        @toggle-favorite="toggleFavorite"
      />
    </section>

    <section class="ws-card ws-card--favorites" aria-labelledby="ws-overview-favorites">
      <h2 id="ws-overview-favorites" class="ws-card__title">{{ t('workspace.overview.favorites') }}</h2>
      <!-- 收藏只渲染与当前可见集合求交后的条目：失权、跨账号或损坏记录里的 ID 一律不出现。 -->
      <p v-if="isLoading" class="ws-card__status" role="status">{{ t('common.loading') }}</p>
      <p v-else-if="isFailed" class="ws-card__status" role="alert">{{ t('request.menuLoadFailed') }}</p>
      <WorkspaceEntryList
        v-else
        :items="favoriteEntries"
        :empty-text="t('workspace.overview.noFavorites')"
        :new-tab-title="t('common.openInNewTab')"
        :favorite-ids="workspaceStore.favoriteIds"
        :favorite-title="t('common.favorite')"
        :unfavorite-title="t('common.unfavorite')"
        @toggle-favorite="toggleFavorite"
      />
    </section>

    <section v-if="showWorkflow" id="approvals" class="ws-card ws-card--operations" aria-labelledby="ws-approvals-title">
      <h2 id="ws-approvals-title" class="ws-card__title">{{ t('workflow.todo') }}</h2>
      <component :is="WorkspaceApprovals" embedded @changed="refreshStats('workflow')" />
    </section>
    <section id="my-tasks" class="ws-card ws-card--operations" :aria-label="t('workspace.myJobs')">
      <WorkspaceJobs embedded @changed="refreshStats('jobs')" />
    </section>
  </div>
</template>

<style scoped lang="scss">
/*
 * 驾驶舱使用十二列栅格，统计和实际操作同页，窄屏收成一列，
 * 靠 min-width:0 与 anywhere 折行避免长用户名、长权限码撑出横向滚动条。
 */
.ws-overview {
  display: grid;
  gap: var(--omni-space-lg);
  grid-template-columns: repeat(12, minmax(0, 1fr));
  align-items: start;
  > * { min-width: 0; grid-column: span 6; }
  > .ws-hero, > .ws-card--apps { grid-column: 1 / -1; }
  > .ws-card--account, > .ws-card--favorites { grid-column: span 4; }
  > .ws-card--todo, > .ws-card--recent { grid-column: span 8; }
  > .ws-card--operations { grid-column: span 6; scroll-margin-top: 84px; }
  :deep(.ws-stats__list) { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  :deep(.ws-stats__item) { display: flex; flex-direction: column; align-items: flex-start; }
  :deep(.ws-stats__value) { font-size: 30px; font-weight: 700; color: var(--omni-text-accent); }
  :deep(.el-table), :deep(.el-tabs), :deep(.jobs-page) { min-width: 0; max-width: 100%; }
  :deep(.pagination) { overflow-x: auto; justify-content: flex-start; }
}

.ws-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--omni-border-glass);
  border-radius: var(--omni-radius-md);
  background: linear-gradient(115deg, var(--el-color-primary-light-9), var(--omni-bg-glass));
  h1 { margin: 0 0 8px; font-size: 26px; color: var(--omni-text-primary); }
  p { margin: 0; color: var(--omni-text-secondary); }
}

.ws-card--apps :deep(.entry-list) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.ws-card--apps {
  :deep(.entry-list__row) { padding: 10px; border: 1px solid var(--omni-border-glass); border-radius: var(--omni-radius-md); }
  :deep(.entry-list__link) { display: grid; grid-template-columns: 40px minmax(0, 1fr); gap: 4px 12px; align-items: center; }
  :deep(.entry-list__icon) { grid-row: 1 / 3; width: 40px; height: 40px; font-size: 22px; color: var(--el-color-primary); background: var(--el-color-primary-light-9); border-radius: 10px; }
  :deep(.entry-list__title) { grid-column: 2; font-size: 15px; font-weight: 600; }
  :deep(.entry-list__meta) { grid-column: 2; }
}

.ws-card {
  min-width: 0;
  padding: var(--omni-space-lg);
  border: 1px solid var(--omni-border-glass);
  border-radius: var(--omni-radius-md);
  background: var(--omni-bg-glass);
}

.ws-card__title {
  margin: 0 0 var(--omni-space-md);
  font-size: 15px;
  font-weight: 600;
  color: var(--omni-text-primary);
}

.ws-card__status {
  margin: 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.ws-card__status--failed {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--omni-space-sm);
}

.ws-account {
  display: flex;
  align-items: center;
  gap: var(--omni-space-md);
  min-width: 0;
}

.ws-account__avatar {
  flex: none;
  background: var(--omni-text-accent);
  font-size: 20px;
}

.ws-account__meta {
  min-width: 0;
}

.ws-account__name {
  margin: 0 0 var(--omni-space-xs);
  font-size: 16px;
  font-weight: 600;
  color: var(--omni-text-primary);
  overflow-wrap: anywhere;
}

.ws-account__roles {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--omni-space-xs);
}

.ws-account__roles-label {
  color: var(--omni-text-secondary);
  font-size: 12px;
}

.ws-todo {
  display: flex;
  flex-wrap: wrap;
  gap: var(--omni-space-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

.ws-todo__link {
  display: block;
  padding: 8px 10px;
  border-radius: var(--omni-radius-md);
  color: var(--omni-text-primary);
  font-size: 14px;
  text-decoration: none;
  overflow-wrap: anywhere;
  transition: background-color var(--omni-duration-fast), color var(--omni-duration-fast);
}

.ws-todo__link:hover,
.ws-todo__link:focus-visible {
  background: var(--omni-bg-glass);
  color: var(--omni-text-accent);
}

@media (max-width: 1200px) {
  .ws-overview > .ws-card--operations { grid-column: 1 / -1; }
  .ws-card--apps :deep(.entry-list) { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 768px) {
  .ws-overview {
    grid-template-columns: 1fr;
    > :is(.ws-card, .ws-stats, .ws-hero) { grid-column: 1 / -1; }
    :deep(.ws-stats__value) { font-size: 24px; }
  }
  .ws-card--apps :deep(.entry-list) { grid-template-columns: 1fr; }
  .ws-hero { padding: 16px; flex-wrap: wrap; }
  .ws-card { padding: 16px; }
}
</style>
