<script setup lang="ts">
/**
 * @module components/ConsoleHeader
 * 统一控制台顶栏：工作台提供全授权应用入口，业务标签只聚焦当前应用。
 * 顶栏承载搜索、语言、主题与账户；只有工作台挂载全局应用抽屉，两种模式都没有返回官网或Wiki的入口，
 * 品牌与侧栏折叠等布局专属控件由消费方通过插槽提供，避免在每个业务页复制布局。
 * 全局应用按钮只切换本组件持有的抽屉开合状态，不再绑定侧栏折叠；
 * 抽屉本体由本组件持有并渲染，开合真值与焦点归还集中在一处，消费方无需各自挂载。
 * 语言、主题与账户沿用现有 LanguageSelector、app store 与 user store，不新建第二套状态。
 * 菜单搜索只在同一份已授权派生页面集合内做本地过滤，不请求额外搜索服务，也不放大权限。
 */
import { computed, ref, watch, type ComponentPublicInstance } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { clearAuthenticatedSession } from '@/router'
import { useApplicationNavigation } from '@/composables/useApplicationNavigation'
import { filterNavigationSearchPages, type NavigationEntry } from '@/utils/application-navigation'
import ApplicationDrawer from '@/components/ApplicationDrawer.vue'
import LanguageSelector from '@/components/LanguageSelector.vue'

/** 业务标签移除全局应用入口，并只搜索当前应用；工作台保留全授权菜单。 */
const props = defineProps<{ moduleOnly?: boolean }>()

/** 抽屉开合真值集中在顶栏，抽屉本体只回执关闭请求。 */
const appDrawerOpen = ref(false)

/** 应用按钮实例：抽屉关闭后把焦点归还触发器，键盘用户不会落在隐藏节点上。 */
const appsTriggerRef = ref<ComponentPublicInstance | null>(null)

const { t, te } = useI18n()
const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const { modules, pages, menuLoadState } = useApplicationNavigation()

/** 用户名回退与既有布局一致，不额外请求用户资料。 */
const displayName = computed(() => userStore.username || 'Admin')

function toggleAppDrawer() {
  if (props.moduleOnly) return
  appDrawerOpen.value = !appDrawerOpen.value
}

function closeAppDrawer() {
  appDrawerOpen.value = false
}

/** 抽屉收起后把焦点还给触发按钮，键盘与读屏用户不会停在已隐藏的抽屉节点上。 */
watch(appDrawerOpen, (open) => {
  if (open) return
  const trigger = (appsTriggerRef.value?.$el ?? null) as HTMLElement | null
  trigger?.focus?.()
})

/** 切换主题模式：沿用 app store 单一主题状态，不在顶栏另存一份。 */
function toggleTheme() {
  appStore.setTheme(appStore.theme === 'dark' ? 'light' : 'dark')
}

/** 登出并清理会话后返回登录页。 */
function handleLogout() {
  clearAuthenticatedSession()
  void router.push('/login')
}

/** 标题解析单一来源：命中 i18n 键用本地化文案，否则回退后端菜单名，不伪造标题。 */
function entryTitle(entry: Pick<NavigationEntry, 'titleKey' | 'fallbackTitle'>): string {
  return entry.titleKey !== null && te(entry.titleKey) ? t(entry.titleKey) : entry.fallbackTitle
}

const moduleTitles = computed(() => {
  const map = new Map<number, string>()
  for (const mod of modules.value) map.set(mod.appId, entryTitle(mod))
  return map
})

/** 缺少真实应用归属时拒绝全部候选，不能把业务页悄悄退回全局搜索。 */
const currentAppId = computed(() => typeof route.meta.appId === 'number' ? route.meta.appId : null)
const searchPages = computed(() =>
  filterNavigationSearchPages(pages.value, props.moduleOnly ? currentAppId.value : undefined),
)

interface MenuSearchOption {
  value: string
  label: string
  group: string
  routePath: string
}

/** 搜索候选与卡片、抽屉、侧栏共用同一份派生结果：仅取真实可进入叶子并按路由去重。 */
const searchOptions = computed<MenuSearchOption[]>(() => {
  const seen = new Set<string>()
  const options: MenuSearchOption[] = []
  for (const page of searchPages.value) {
    const routePath = page.routePath
    if (!page.enterable || routePath === null || seen.has(routePath)) continue
    seen.add(routePath)
    options.push({
      value: `${page.appCode}:${routePath}`,
      label: entryTitle(page),
      group: moduleTitles.value.get(page.appId) ?? page.appCode,
      routePath,
    })
  }
  return options
})

/** 选中即清空：顶栏搜索是一次性跳转入口，不保留“已选中”状态冒充当前页。 */
const selectedMenu = ref('')

watch(() => route.meta.appId, () => { selectedMenu.value = '' })
watch(() => props.moduleOnly, (moduleOnly) => {
  selectedMenu.value = ''
  if (moduleOnly) appDrawerOpen.value = false
})

const searchNoDataText = computed(() => {
  if (menuLoadState.value === 'loading') return t('common.loading')
  if (menuLoadState.value === 'failed') return t('request.menuLoadFailed')
  return t('common.menuNoMatch')
})

/** 工作台搜索在新标签进入；业务搜索再次核对归属且只在当前标签导航。 */
function handleMenuSelect(value: unknown) {
  const target = String(value)
  const hit = searchOptions.value.find((option) => option.value === target)
  selectedMenu.value = ''
  if (!hit || hit.routePath === route.path) return
  const resolved = router.resolve(hit.routePath)
  if (resolved.matched.length === 0) return
  if (props.moduleOnly) {
    if (resolved.meta.appId !== currentAppId.value) return
    void router.push(hit.routePath)
    return
  }
  window.open(resolved.href, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <header class="header console-header">
    <div class="header-left">
      <slot name="leading" />
      <el-button
        v-if="!props.moduleOnly"
        ref="appsTriggerRef"
        text
        class="console-header__apps"
        :title="t('common.applications')"
        :aria-label="t('common.applications')"
        :aria-expanded="appDrawerOpen"
        @click="toggleAppDrawer"
      >
        <el-icon><Grid /></el-icon>
        <span class="console-header__apps-label">{{ t('common.applications') }}</span>
      </el-button>
      <slot name="brand" />
    </div>

    <div class="header-right">
      <el-select
        v-model="selectedMenu"
        filterable
        class="console-header__search"
        :aria-label="t('common.menuSearch')"
        :placeholder="t('common.menuSearchPlaceholder')"
        :no-data-text="searchNoDataText"
        @change="handleMenuSelect"
      >
        <el-option
          v-for="option in searchOptions"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        >
          <span class="console-header__option">
            <span class="console-header__option-label">{{ option.label }}</span>
            <span class="console-header__option-group">{{ option.group }}</span>
          </span>
        </el-option>
      </el-select>
      <LanguageSelector />
      <el-button text :title="t('theme.toggle')" :aria-label="t('theme.toggle')" @click="toggleTheme">
        <el-icon>
          <Moon v-if="appStore.theme === 'dark'" />
          <Sunny v-else />
        </el-icon>
      </el-button>
      <el-dropdown>
        <span class="user-info">
          <el-icon><User /></el-icon>
          <span class="console-header__username">{{ displayName }}</span>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="handleLogout">{{ t('common.logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <slot name="extra" />
    </div>

    <ApplicationDrawer v-if="!props.moduleOnly" :open="appDrawerOpen" @close="closeAppDrawer" />
  </header>
</template>

<style scoped lang="scss">
/*
 * 顶栏自身的断点规则集中在本组件，避免每个布局重复一份头部响应式样式：
 * 窄屏压缩内边距、隐藏应用按钮文字标签、允许右侧工具换行，
 * 工作台应用入口与两种模式的搜索、语言、主题、账户按当前模式保留。
 */
.console-header {
  gap: var(--omni-space-md);

  .header-left,
  .header-right {
    min-width: 0;
    gap: var(--omni-space-sm);
  }

  .header-left {
    flex-shrink: 0;
  }
}

.console-header__search {
  width: 220px;
  min-width: 0;
}

/*
 * 搜索结果行：菜单标题与所属模块名共处一行，两侧都必须可截断。
 * 用 flex 而不是 float:right：浮动的长模块名（中/英/日/韩）会溢出弹层宽度，
 * 也让标题先被挤掉；flex + min-width:0 使两者各自省略号，关键信息仍可读。
 */
.console-header__option {
  display: flex;
  align-items: baseline;
  gap: var(--omni-space-sm);
  min-width: 0;
  width: 100%;
}

.console-header__option-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-header__option-group {
  flex: none;
  max-width: 45%;
  margin-left: auto;
  color: var(--omni-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
 * 1024 以下左侧入口、模块导航与右侧工具合计宽度可能超过一行：
 * 全局 .header 是固定 64px 高且不换行，这一段会横向外溢成整页滚动条。
 * 本组件在同一断点先改为可换行的自适应高度，保证全局入口与搜索始终可点。
 */
@media (max-width: 1024px) {
  .console-header {
    flex-wrap: wrap;
    height: auto;
    min-height: 56px;
    padding: var(--omni-space-xs) 16px;
    gap: var(--omni-space-sm);
    row-gap: 4px;
  }

  .console-header__apps-label {
    display: none;
  }

  .console-header__search {
    width: 160px;
  }
}

@media (max-width: 768px) {
  .console-header {
    position: sticky;
    top: 0;
    /* 顶栏必须高于覆盖层侧栏（1001），展开侧栏后全局入口仍可真实点击。 */
    z-index: 1002;
    flex-wrap: wrap;
    height: auto;
    min-height: 64px;
    padding: var(--omni-space-xs) 16px;

    .header-right {
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 6px;
      min-width: 0;
    }

    .header-right :deep(.el-button) {
      padding: 4px 6px;
    }
  }

  .console-header__search {
    width: 140px;
  }

  .console-header__username {
    max-width: 96px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

@media (max-width: 600px) {
  .console-header {
    padding: var(--omni-space-xs) 8px;

    .header-right {
      gap: 4px;
    }

    .header-right :deep(.el-button) {
      padding: 4px;
    }
  }

  .console-header__search {
    width: 112px;
  }

  .console-header__username {
    display: none;
  }
}
</style>
