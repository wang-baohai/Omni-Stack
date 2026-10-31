<script setup lang="ts">
/**
 * @module components/workspace/WorkspaceEntryList
 * 工作台概览的条目列表：只渲染调用方从统一导航派生出的真实目标，不自行拼路径，也不合成兜底链接。
 * 带同源 href 的条目交浏览器原生在新标签打开并加 rel=noopener noreferrer，
 * 因此 Ctrl/中键/复制链接可用且不依赖异步 window.open（不会被弹窗拦截）；
 * 只有路由路径的条目留在当前标签路由；两者都解析不出目标时渲染为禁用按钮，绝不产生可点却无跳转的假入口；
 * href 只含路由路径，不带令牌或认证暂存值；
 * 空集合文案由调用方传入，本组件不猜语义，也不自行发起任何请求。
 * 收藏星按钮由调用方按「是否允许收藏」开启并持有收藏集合，本组件只上抛被点的稳定 ID，
 * 既不读写偏好记录也不判断权限：目标解析不出来的条目连收藏按钮一起禁用，收藏因此永远产生不了入口。
 */
import { useRouter } from 'vue-router'

/** 概览条目：目标字段由统一导航与路由表解析得出，null 表示该跳转方式当前不可用。 */
interface WorkspaceEntry {
  /** 稳定标识，取导航条目 ID，避免同名应用或菜单互相覆盖。 */
  id: string
  /** 已解析标题。 */
  title: string
  /** 已登记的应用图标；缺少时不生成替代图标。 */
  icon?: string | null
  /** 归属说明，例如所属应用名或菜单分组名。 */
  meta: string
  /** 新标签目标：路由表派生出的同源 href。 */
  href: string | null
  /** 当前标签目标：真实叶子路由路径。 */
  routePath: string | null
}

const props = withDefaults(
  defineProps<{
    items: WorkspaceEntry[]
    emptyText: string
    newTabTitle: string
    /** 已收藏的稳定 ID 集合；为 `null` 时整列不渲染收藏控件，收藏状态仍由调用方的共享记录持有。 */
    favoriteIds?: string[] | null
    /** 未收藏时的按钮提示。 */
    favoriteTitle?: string
    /** 已收藏时的按钮提示。 */
    unfavoriteTitle?: string
  }>(),
  { favoriteIds: null, favoriteTitle: '', unfavoriteTitle: '' },
)

const emit = defineEmits<{ (event: 'toggle-favorite', id: string): void }>()

const router = useRouter()

/** 该条目当前是否已收藏。 */
function isFavorited(item: WorkspaceEntry): boolean {
  return props.favoriteIds !== null && props.favoriteIds.includes(item.id)
}

/** 条目链接属性：可新标签打开时给真实 href 与 rel，其余渲染为按钮。 */
function entryAttrs(item: WorkspaceEntry): Record<string, string> {
  return item.href === null
    ? { type: 'button' }
    : { href: item.href, target: '_blank', rel: 'noopener noreferrer', title: props.newTabTitle }
}

/** 无任何真实目标的条目保持禁用，避免给出可点却什么都不发生的假入口。 */
function entryDisabled(item: WorkspaceEntry): boolean {
  return item.href === null && item.routePath === null
}

/** 当前标签进入真实目标；新标签条目交浏览器原生处理，点击不改当前标签。 */
function openInCurrentTab(item: WorkspaceEntry): void {
  if (item.href !== null || item.routePath === null) return
  void router.push(item.routePath)
}
</script>

<template>
  <ul v-if="props.items.length > 0" class="entry-list">
    <li v-for="item in props.items" :key="item.id" class="entry-list__item">
      <div class="entry-list__row">
        <component
          :is="item.href === null ? 'button' : 'a'"
          v-bind="entryAttrs(item)"
          class="entry-list__link"
          :disabled="entryDisabled(item)"
          @click="openInCurrentTab(item)"
        >
          <el-icon v-if="item.icon" class="entry-list__icon"><component :is="item.icon" /></el-icon>
          <span class="entry-list__title">{{ item.title }}</span>
          <span class="entry-list__meta">{{ item.meta }}</span>
        </component>
        <button
          v-if="props.favoriteIds !== null"
          type="button"
          class="entry-list__favorite"
          :class="{ 'entry-list__favorite--active': isFavorited(item) }"
          :disabled="entryDisabled(item)"
          :aria-pressed="isFavorited(item)"
          :title="isFavorited(item) ? props.unfavoriteTitle : props.favoriteTitle"
          @click="emit('toggle-favorite', item.id)"
        >
          <el-icon><Star /></el-icon>
          <span class="entry-list__favorite-label">{{ isFavorited(item) ? props.unfavoriteTitle : props.favoriteTitle }}</span>
        </button>
      </div>
    </li>
  </ul>
  <p v-else class="entry-list__empty">{{ props.emptyText }}</p>
</template>

<style scoped lang="scss">
/*
 * 列表只负责排版：整行可点、长标题截断但保留完整文本，窄屏靠 min-width:0 与折行避免横向溢出。
 */
.entry-list {
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-xs);
  margin: 0;
  padding: 0;
  list-style: none;
}

.entry-list__item {
  min-width: 0;
}

/* 链接与收藏按钮并排成一行：链接吃掉剩余宽度，按钮自身不裁切也不挤掉标题。 */
.entry-list__row {
  display: flex;
  align-items: stretch;
  gap: 4px;
  min-width: 0;
}

.entry-list__favorite {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  padding: 8px 10px;
  border: 0;
  border-radius: var(--omni-radius-md);
  background: transparent;
  color: var(--omni-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: color var(--omni-duration-fast), background-color var(--omni-duration-fast);
}

.entry-list__favorite:hover,
.entry-list__favorite:focus-visible {
  background: var(--omni-bg-glass);
  color: var(--omni-text-accent);
}

.entry-list__favorite--active {
  color: var(--omni-text-accent);
}

/* 目录列宽有限：长语言下可见文案会把标题挤成逐字母断行；保留可访问名，视觉只留图标。 */
.entry-list__favorite-label {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.entry-list__favorite[disabled],
.entry-list__favorite[disabled]:hover {
  color: var(--omni-text-secondary);
  background: transparent;
  cursor: not-allowed;
}

.entry-list__link {
  display: flex;
  flex: 1 1 0;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--omni-space-sm);
  min-width: 0;
  padding: 8px 10px;
  border: 0;
  border-radius: var(--omni-radius-md);
  background: transparent;
  color: var(--omni-text-primary);
  font-size: 14px;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  transition: background-color var(--omni-duration-fast), color var(--omni-duration-fast);
}

.entry-list__link:hover,
.entry-list__link:focus-visible {
  background: var(--omni-bg-glass);
  color: var(--omni-text-accent);
}

.entry-list__link[disabled],
.entry-list__link[disabled]:hover {
  color: var(--omni-text-secondary);
  background: transparent;
  cursor: not-allowed;
}

.entry-list__title {
  min-width: 0;
  overflow-wrap: anywhere;
}

.entry-list__meta {
  flex: none;
  color: var(--omni-text-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.entry-list__empty {
  margin: 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
}
</style>
