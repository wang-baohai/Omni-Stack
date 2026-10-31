<script setup lang="ts">
/**
 * @module components/workspace/WorkspaceStatsCard
 * 工作台概览的单张统计卡：挂载时先由 store 判定模块与权限再按需取数，
 * 加载、不可用（未发请求）、空值（请求成功但全为 0）、失败四种状态各自独立渲染，
 * 失败时只重试本卡，不重载菜单也不触碰其他卡；
 * 指标值为 null 时渲染占位符，绝不把请求异常或字段缺失显示成 0；
 * 失败文案只取归一化错误描述符经本地化后的安全文本，后端原始 message 与 Axios 技术串一律不上屏。
 */
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveDisplayText } from '@/utils/errors'
import { useWorkspaceStore } from '@/stores/workspace'
import type { WorkspaceStatsCardId } from '@/stores/workspace'

const props = defineProps<{
  /** 统计卡标识，决定取哪个已有接口。 */
  cardId: WorkspaceStatsCardId
  /** 卡片标题词条 key。 */
  titleKey: string
}>()

const { t } = useI18n()
const workspaceStore = useWorkspaceStore()

/** 本卡状态；未挂载完成前按加载态处理，避免闪出无内容的空壳。 */
const card = computed(() => workspaceStore.cards[props.cardId])
const isBusy = computed(() => card.value.status === 'idle' || card.value.status === 'loading')

/** 标题锚点 id：每张卡只挂载一次，按 cardId 派生即可保证同页唯一。 */
const headingId = computed(() => `ws-stats-${props.cardId}-title`)

/** 失败文案：有归一化描述符时按语义分类取安全文案，本地异常或响应形态不可用时退回通用文案。 */
const failureText = computed(() =>
  card.value.display === null ? t('request.requestFailed') : resolveDisplayText(card.value.display),
)

/** 取数与重试走同一条按卡路径，判定不通过时只落 unavailable 而不发请求。 */
function load(): void {
  void workspaceStore.loadCard(props.cardId)
}

onMounted(load)
</script>

<template>
  <section class="ws-card ws-stats" :aria-labelledby="headingId">
    <h2 :id="headingId" class="ws-card__title">{{ t(props.titleKey) }}</h2>
    <p v-if="isBusy" class="ws-card__status" role="status">{{ t('common.loading') }}</p>
    <p v-else-if="card.status === 'unavailable'" class="ws-card__status">{{ t('workspace.stats.unavailable') }}</p>
    <p v-else-if="card.status === 'empty'" class="ws-card__status">{{ t('workspace.stats.empty') }}</p>
    <div v-else-if="card.status === 'failed'" class="ws-card__status ws-card__status--failed" role="alert">
      <span>{{ failureText }}</span>
      <el-button text type="primary" @click="load">
        <el-icon><Refresh /></el-icon>
        <span>{{ t('common.retry') }}</span>
      </el-button>
    </div>
    <dl v-else class="ws-stats__list">
      <div v-for="metric in card.metrics" :key="metric.id" class="ws-stats__item">
        <dt class="ws-stats__label">{{ t(metric.labelKey) }}</dt>
        <dd class="ws-stats__value">{{ metric.value === null ? '—' : metric.value }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped lang="scss">
/*
 * 统计卡沿用概览栅格：数值与标签同行两端对齐，长词条靠 min-width:0 与折行避免横向溢出。
 */
.ws-stats__list {
  display: flex;
  flex-direction: column;
  gap: var(--omni-space-xs);
  margin: 0;
}

.ws-stats__item {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--omni-space-sm);
  min-width: 0;
}

.ws-stats__label {
  min-width: 0;
  color: var(--omni-text-secondary);
  font-size: 14px;
  overflow-wrap: anywhere;
}

.ws-stats__value {
  flex: none;
  margin: 0;
  color: var(--omni-text-primary);
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
</style>
