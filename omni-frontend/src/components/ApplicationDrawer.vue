<script setup lang="ts">
/**
 * @module components/ApplicationDrawer
 * 全局应用抽屉：分类、分组与搜索全部复用统一导航派生结果与同一份已授权菜单树。
 * 开合状态由共享顶栏集中持有，本组件只按 props 渲染并回执关闭动作；
 * 条目一律指向已解析的真实叶子路由，无叶子的应用不出现，也不合成 overview 等假目标；
 * 模块入口渲染 router.resolve 派生的同源真实 href，交浏览器原生在新标签打开并加 rel=noopener noreferrer，
 * 因此 Ctrl/中键/复制链接可用且不依赖异步 window.open（不会被弹窗拦截）；href 只含路由路径，不带令牌或认证暂存值；
 * 应用抽屉中的模块及菜单入口均在新标签打开；模块自身侧栏仍在当前标签路由（见 SidebarMenu）；
 * 分组标题取真实 DIRECTORY 祖先节点，绝不按权限码首段切分；
 * 搜索只在当前账号可见集合内做本地过滤，不发起额外菜单请求，也不放大权限；
 * 收藏读工作台共享 store 里「租户 + 稳定用户」作用域下的同一份响应式记录，与概览页因此天然同步；
 * 抽屉只渲染与当前可见集合求交后的收藏，收藏不授予任何访问权限，跨标签登录同步仍由 WP-06 接入。
 */
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useWorkspaceStore } from '@/stores/workspace'
import { useApplicationNavigation } from '@/composables/useApplicationNavigation'
import { menuI18nMap } from '@/constants/menu'
import type { MenuNode } from '@/api/menu'
import type { NavigationEntry } from '@/utils/application-navigation'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (event: 'close'): void }>()

const { t, te } = useI18n()
const router = useRouter()
const permissionStore = usePermissionStore()
const workspaceStore = useWorkspaceStore()
const { modules, pages, menuLoadState } = useApplicationNavigation()

/** 抽屉内搜索词：只本地过滤当前账号已授权集合。 */
const keyword = ref('')

/** 标题解析沿用侧栏同一份编码到 i18n 键映射；缺键回退后端名称，不编造标题。 */
function titleOf(titleKey: string | null, fallback: string): string {
  return titleKey !== null && te(titleKey) ? t(titleKey) : fallback
}

/** 权限树祖先表：抽屉分组标题与父级归属只取真实节点。 */
const directories = computed(() => {
  const map = new Map<string, { parent: string | null; titleKey: string | null; fallback: string }>()
  const walk = (nodes: MenuNode[], parent: string | null): void => {
    for (const node of nodes) {
      map.set(node.permissionCode, {
        parent,
        titleKey: menuI18nMap[node.permissionCode] ?? null,
        fallback: node.permissionName,
      })
      walk(node.children ?? [], node.permissionCode)
    }
  }
  walk(permissionStore.menuTree, null)
  return map
})

/** 叶子的直接父目录；直接挂在应用根下的叶子返回 null，交由分类平铺。 */
function parentDirectory(page: NavigationEntry, appCode: string): { code: string; title: string } | null {
  const parent = directories.value.get(page.permissionCode)?.parent ?? null
  if (parent === null || parent === appCode) return null
  const meta = directories.value.get(parent)
  return { code: parent, title: meta ? titleOf(meta.titleKey, meta.fallback) : parent }
}

/** 抽屉叶子视图：真实路由加已解析标题与所属分组名。 */
interface DrawerLeaf {
  page: NavigationEntry
  href: string | null
  title: string
  group: string | null
  /** 收藏记录里使用的稳定 ID（`sys_permission.id` 的字符串形式）。 */
  favoriteId: string
}

/** 分类条目：一个应用及其分组、直属叶子；href 为模块入口派生出的同源新标签目标。 */
interface DrawerCategory {
  app: NavigationEntry
  title: string
  href: string | null
  /** 模块入口的稳定 ID，与概览页收藏记录同一个键。 */
  favoriteId: string
  direct: DrawerLeaf[]
  groups: { code: string; title: string; leaves: DrawerLeaf[] }[]
  leaves: DrawerLeaf[]
}

/**
 * 模块入口 href：只由路由表解析派生（createWebHistory 下即同源路径），
 * matched 为空表示该目标当前不可解析，返回 null 交由模板渲染为无跳转项，绝不合成新标签死链。
 */
function moduleEntryHref(routePath: string | null): string | null {
  if (routePath === null) return null
  const resolved = router.resolve(routePath)
  return resolved.matched.length > 0 ? resolved.href : null
}

/** 分类与分组由派生集合按 appId 聚合，可见性与卡片、搜索、侧栏完全一致。 */
const categories = computed<DrawerCategory[]>(() => {
  const out: DrawerCategory[] = []
  for (const app of modules.value) {
    const direct: DrawerLeaf[] = []
    const groups: DrawerCategory['groups'] = []
    for (const page of pages.value) {
      if (page.appId !== app.id) continue
      const parent = parentDirectory(page, app.permissionCode)
      const leaf: DrawerLeaf = {
        page,
        href: moduleEntryHref(page.routePath),
        title: titleOf(page.titleKey, page.fallbackTitle),
        group: parent?.title ?? null,
        favoriteId: String(page.id),
      }
      if (parent === null) {
        direct.push(leaf)
        continue
      }
      const existing = groups.find((group) => group.code === parent.code)
      if (existing) existing.leaves.push(leaf)
      else groups.push({ code: parent.code, title: parent.title, leaves: [leaf] })
    }
    if (direct.length === 0 && groups.length === 0) continue
    out.push({
      app,
      title: titleOf(app.titleKey, app.fallbackTitle),
      href: moduleEntryHref(app.routePath),
      favoriteId: String(app.id),
      direct,
      groups,
      leaves: [...direct, ...groups.flatMap((group) => group.leaves)],
    })
  }
  return out
})

/** 抽屉里可收藏的目标：分类（模块入口）与其全部可见叶子。 */
interface DrawerTarget {
  id: string
  title: string
  meta: string
  href: string | null
  routePath: string | null
}

/**
 * 稳定 ID → 抽屉目标：收藏记录里只有 ID，标题、归属与入口每次按当前可见集合重新解析，
 * 失权或跨账号的 ID 在表里查不到，因而既渲染不出来也点不进去。
 */
const targetsById = computed(() => {
  const map = new Map<string, DrawerTarget>()
  for (const category of categories.value) {
    map.set(category.favoriteId, {
      id: category.favoriteId,
      title: category.title,
      meta: t('common.applications'),
      href: category.href,
      routePath: category.app.routePath,
    })
    for (const leaf of category.leaves) {
      map.set(leaf.favoriteId, {
        id: leaf.favoriteId,
        title: leaf.title,
        meta: leaf.group === null ? category.title : `${category.title} · ${leaf.group}`,
        href: leaf.href,
        routePath: leaf.page.routePath,
      })
    }
  }
  return map
})

/** 抽屉收藏集合：与概览页读同一份 store 记录，顺序同为收藏先后，因此两处内容一致。 */
const favoriteTargets = computed(() => {
  const out: DrawerTarget[] = []
  for (const id of workspaceStore.favoriteIds) {
    const target = targetsById.value.get(id)
    if (target !== undefined) out.push(target)
  }
  return out
})

/** 收藏写入只接受当前可见目标：收藏给不出访问权限，也存不进抽屉里看不到的 ID。 */
function toggleFavorite(id: string): void {
  if (!targetsById.value.has(id)) return
  workspaceStore.toggleFavorite(id)
}

/** 收藏按钮文案：已收藏时给出取消收藏的提示。 */
function favoriteLabel(id: string): string {
  return workspaceStore.isFavorite(id) ? t('common.unfavorite') : t('common.favorite')
}

/** 搜索结果只指向已授权真实入口，应用与菜单都交给浏览器新标签。 */
interface SearchHit {
  key: string
  kind: 'app' | 'menu'
  title: string
  meta: string
  routePath: string | null
  href: string | null
}

const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())

function matches(haystack: string): boolean {
  return haystack.toLowerCase().includes(normalizedKeyword.value)
}

/** 命中范围限分类标题、分组名、菜单标题与权限编码；不引入任何未授权目标。 */
const searchHits = computed<SearchHit[]>(() => {
  if (normalizedKeyword.value === '') return []
  const hits: SearchHit[] = []
  for (const category of categories.value) {
    if (matches(category.title) || matches(category.app.permissionCode)) {
      hits.push({
        key: `app-${category.app.id}`,
        kind: 'app',
        title: category.title,
        meta: t('common.applications'),
        routePath: category.app.routePath,
        href: category.href,
      })
    }
    for (const leaf of category.leaves) {
      if (matches(leaf.title) || matches(leaf.page.permissionCode) || (leaf.group !== null && matches(leaf.group))) {
        hits.push({
          key: `menu-${leaf.page.id}`,
          kind: 'menu',
          title: leaf.title,
          meta: leaf.group === null ? category.title : `${category.title} · ${leaf.group}`,
          routePath: leaf.page.routePath,
          href: leaf.href,
        })
      }
    }
  }
  return hits
})

/** 加载与失败态沿用同一份菜单加载状态，不把失败粉饰成空集合。 */
const isLoading = computed(() => menuLoadState.value === 'idle' || menuLoadState.value === 'loading')
const isFailed = computed(() => menuLoadState.value === 'failed')

function closeDrawer(): void {
  emit('close')
}

/** 顶部关闭回执；仅当抽屉自身请求关闭时上抛，开合真值仍由顶栏持有。 */
function handleModelValue(value: unknown): void {
  if (value === false) closeDrawer()
}

/** 失败后只重试菜单本身，不重载整页或改写其他状态。 */
function retryMenus(): void {
  void permissionStore.retryLoadMenus().catch(() => undefined)
}

/** 搜索点击只收起抽屉，真实href由浏览器原生在新标签打开。 */
function activateHit(hit: SearchHit): void {
  if (hit.href !== null) closeDrawer()
}

/** 模块入口链接属性：可解析时给出真实同源 href 与新标签 rel；不可解析时退化为无跳转按钮，绝不合成死链。 */
function moduleEntryAttrs(href: string | null): Record<string, string> {
  return href === null
    ? { type: 'button', disabled: 'disabled' }
    : { href, target: '_blank', rel: 'noopener noreferrer', title: t('common.openInNewTab') }
}

/** 收藏点击只收起抽屉，不把工作台或当前模块标签替换成其他业务页面。 */
function openTarget(target: DrawerTarget): void {
  if (target.href !== null) closeDrawer()
}

/** 关闭后清空搜索词，避免下次打开残留上次过滤条件；打开时按当前身份载入 store 里的共享偏好，同一作用域键只读一次，身份变化由 store 自身重载。 */
watch(
  () => props.open,
  (open) => {
    if (open) workspaceStore.loadPreferences()
    else keyword.value = ''
  },
  { immediate: true },
)
</script>

<template>
  <el-drawer
    :model-value="props.open"
    :title="t('common.applications')"
    direction="ltr"
    size="min(420px, 100%)"
    append-to-body
    :close-on-click-modal="true"
    :close-on-press-escape="true"
    :trap-focus="true"
    @update:model-value="handleModelValue"
  >
    <div class="app-drawer__body">
      <el-input
        v-model="keyword"
        clearable
        class="app-drawer__search"
        :aria-label="t('common.menuSearch')"
        :placeholder="t('common.menuSearchPlaceholder')"
      />

      <p v-if="isLoading" class="app-drawer__status" role="status">{{ t('common.loading') }}</p>

      <div v-else-if="isFailed" class="app-drawer__status app-drawer__status--failed" role="alert">
        <span>{{ t('request.menuLoadFailed') }}</span>
        <el-button text type="primary" @click="retryMenus">
          <el-icon><Refresh /></el-icon>
          <span>{{ t('common.retry') }}</span>
        </el-button>
      </div>

      <template v-else-if="normalizedKeyword !== ''">
        <el-empty v-if="searchHits.length === 0" :description="t('common.menuNoMatch')" />
        <ul v-else class="app-drawer__hits">
          <li v-for="hit in searchHits" :key="hit.key">
            <component
              :is="hit.href === null ? 'button' : 'a'"
              v-bind="moduleEntryAttrs(hit.href)"
              class="app-drawer__link"
              @click="activateHit(hit)"
            >
              <span class="app-drawer__link-title">{{ hit.title }}</span>
              <span class="app-drawer__link-meta">{{ hit.meta }}</span>
            </component>
          </li>
        </ul>
      </template>

      <template v-else>
        <section v-if="favoriteTargets.length > 0" class="app-drawer__favorites">
          <h3 class="app-drawer__favorites-title">{{ t('workspace.overview.favorites') }}</h3>
          <ul class="app-drawer__items">
            <li v-for="target in favoriteTargets" :key="`fav-${target.id}`">
              <div class="app-drawer__row">
                <component
                  :is="target.href === null ? 'button' : 'a'"
                  v-bind="moduleEntryAttrs(target.href)"
                  class="app-drawer__link"
                  @click="openTarget(target)"
                >
                  <span class="app-drawer__link-title">{{ target.title }}</span>
                  <span class="app-drawer__link-meta">{{ target.meta }}</span>
                </component>
                <button
                  aria-pressed="true"
                  class="app-drawer__favorite app-drawer__favorite--active"
                  type="button"
                  :aria-label="favoriteLabel(target.id)"
                  :title="favoriteLabel(target.id)"
                  @click="toggleFavorite(target.id)"
                >
                  <el-icon><Star /></el-icon>
                </button>
              </div>
            </li>
          </ul>
        </section>

        <el-empty v-if="categories.length === 0" :description="t('common.noAccessibleApp')" />

        <ul v-else class="app-drawer__categories">
          <li v-for="category in categories" :key="`app-${category.app.id}`" class="app-drawer__category">
            <div class="app-drawer__row">
              <component
                :is="category.href === null ? 'button' : 'a'"
                v-bind="moduleEntryAttrs(category.href)"
                class="app-drawer__app"
                @click="closeDrawer"
              >
                <el-icon v-if="category.app.icon"><component :is="category.app.icon" /></el-icon>
                <span class="app-drawer__app-title">{{ category.title }}</span>
                <span class="app-drawer__app-count" :aria-label="t('common.appMenuCount', { count: category.leaves.length })">{{
                  category.leaves.length
                }}</span>
              </component>
              <button
                :aria-pressed="workspaceStore.isFavorite(category.favoriteId)"
                class="app-drawer__favorite"
                :class="{ 'app-drawer__favorite--active': workspaceStore.isFavorite(category.favoriteId) }"
                type="button"
                :aria-label="favoriteLabel(category.favoriteId)"
                :title="favoriteLabel(category.favoriteId)"
                @click="toggleFavorite(category.favoriteId)"
              >
                <el-icon><Star /></el-icon>
              </button>
            </div>
            <ul v-if="category.direct.length" class="app-drawer__items">
              <li v-for="leaf in category.direct" :key="leaf.page.id">
                <div class="app-drawer__row">
                  <component :is="leaf.href === null ? 'button' : 'a'" v-bind="moduleEntryAttrs(leaf.href)" class="app-drawer__link" :disabled="leaf.href === null" @click="closeDrawer">
                    <span class="app-drawer__link-title">{{ leaf.title }}</span>
                  </component>
                  <button
                    :aria-pressed="workspaceStore.isFavorite(leaf.favoriteId)"
                    class="app-drawer__favorite"
                    :class="{ 'app-drawer__favorite--active': workspaceStore.isFavorite(leaf.favoriteId) }"
                    type="button"
                    :aria-label="favoriteLabel(leaf.favoriteId)"
                    :title="favoriteLabel(leaf.favoriteId)"
                    @click="toggleFavorite(leaf.favoriteId)"
                  >
                    <el-icon><Star /></el-icon>
                  </button>
                </div>
              </li>
            </ul>
            <div v-for="group in category.groups" :key="group.code" class="app-drawer__group">
              <p class="app-drawer__group-title">{{ group.title }}</p>
              <ul class="app-drawer__items">
                <li v-for="leaf in group.leaves" :key="leaf.page.id">
                  <div class="app-drawer__row">
                    <component :is="leaf.href === null ? 'button' : 'a'" v-bind="moduleEntryAttrs(leaf.href)" class="app-drawer__link" :disabled="leaf.href === null" @click="closeDrawer">
                      <span class="app-drawer__link-title">{{ leaf.title }}</span>
                    </component>
                    <button
                      :aria-pressed="workspaceStore.isFavorite(leaf.favoriteId)"
                      class="app-drawer__favorite"
                      :class="{ 'app-drawer__favorite--active': workspaceStore.isFavorite(leaf.favoriteId) }"
                      type="button"
                      :aria-label="favoriteLabel(leaf.favoriteId)"
                      :title="favoriteLabel(leaf.favoriteId)"
                      @click="toggleFavorite(leaf.favoriteId)"
                    >
                      <el-icon><Star /></el-icon>
                    </button>
                  </div>
                </li>
              </ul>
            </div>
          </li>
        </ul>
      </template>

      <div class="app-drawer__footer">
        <el-button @click="closeDrawer">{{ t('common.close') }}</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
/*
 * 抽屉面板宽度由 size 的 min(420px, 100%) 约束，滚动与层级交给 el-drawer，
 * 本组件只约束条目排版：长标题截断但保留整行可点区域，不隐藏关键操作。
 */
.app-drawer__body {
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-md);
  min-width: 0;
}

.app-drawer__status {
  margin: 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.app-drawer__status--failed {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--omni-space-sm);
  /* 日/韩失败文案较长，不换行会把重试按钮挤出面板宽度，恢复入口就不可点。 */
  flex-wrap: wrap;
}

.app-drawer__categories,
.app-drawer__hits,
.app-drawer__items {
  margin: 0;
  padding: 0;
  list-style: none;
}

.app-drawer__category + .app-drawer__category {
  margin-top: var(--omni-space-md);
  padding-top: var(--omni-space-md);
  border-top: 1px solid var(--omni-border-glass);
}

.app-drawer__app {
  display: flex;
  align-items: center;
  gap: var(--omni-space-sm);
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--omni-radius-md);
  background: transparent;
  color: var(--omni-text-primary);
  font-size: 15px;
  font-weight: 600;
  text-align: left;
  /* 模块入口渲染为 a 时保持与 button 同一观感，不出现下划线与浏览器默认色 */
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--omni-duration-fast);

  &:hover {
    background: var(--omni-bg-glass);
    color: var(--omni-text-accent);
  }
}

.app-drawer__app-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-drawer__app-count {
  flex: none;
  margin-left: auto;
  color: var(--omni-text-secondary);
  font-size: 12px;
  font-weight: 400;
}

.app-drawer__group-title {
  margin: var(--omni-space-sm) 0 4px;
  padding-left: 8px;
  color: var(--omni-text-secondary);
  font-size: 12px;
  letter-spacing: 0.04em;
}

.app-drawer__link {
  display: flex;
  align-items: baseline;
  gap: var(--omni-space-sm);
  width: 100%;
  padding: 5px 8px 5px 16px;
  border: 0;
  border-radius: var(--omni-radius-md);
  background: transparent;
  color: var(--omni-text-primary);
  font-size: 14px;
  text-align: left;
  /* 命中模块入口时渲染为 a：与菜单命中的 button 保持同一观感 */
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--omni-duration-fast);

  &:hover {
    background: var(--omni-bg-glass);
    color: var(--omni-text-accent);
  }
}

.app-drawer__link-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 分组名（分类 · 分组）在 420px 面板内不可顶宽：先让标题占位，元数据自身截断。 */
.app-drawer__link-meta {
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
  color: var(--omni-text-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/*
 * el-drawer 的正文自身滚动：关闭按钮必须吸附在面板底部并保持可见，
 * 否则长列表滚到底才能关闭，遮罩外又无第二关键操作（窄屏尤甚）。
 * 背景沿用抽屉自身色变量（含暗色），不用 overflow:hidden 裁掉内容。
 */
.app-drawer__footer {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  padding-top: var(--omni-space-sm);
  background: var(--el-drawer-bg-color, var(--el-bg-color));
}

/*
 * 收藏区与分类列表同源于可见集合，只靠分隔线区分层级；
 * 以下规则一律排在既有选择器之后，避免改变上文规则的被引用顺序。
 */
.app-drawer__favorites {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: var(--omni-space-sm);
  padding-bottom: var(--omni-space-md);
  border-bottom: 1px solid var(--omni-border-glass);
}

.app-drawer__favorites-title {
  margin: 0;
  color: var(--omni-text-secondary);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

/* 链接与收藏按钮并排成一行：整行仍可点，按钮自身不裁切也不挤掉标题。 */
.app-drawer__row {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-width: 0;
}

/* 行内链接的 flex 基准归零，否则原有 width:100% 加上星按钮宽度会把整行撑出 420px 面板。 */
.app-drawer__row > .app-drawer__app,
.app-drawer__row > .app-drawer__link {
  flex: 1 1 0;
  min-width: 0;
}

.app-drawer__favorite {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  padding: 0;
  border: 0;
  border-radius: var(--omni-radius-md);
  background: transparent;
  color: var(--omni-text-secondary);
  cursor: pointer;
  transition: color var(--omni-duration-fast), background-color var(--omni-duration-fast);
}

.app-drawer__favorite:hover,
.app-drawer__favorite:focus-visible {
  background: var(--omni-bg-glass);
  color: var(--omni-text-accent);
}

.app-drawer__favorite--active {
  color: var(--omni-text-accent);
}
</style>
