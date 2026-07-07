<script setup lang="ts">
/**
 * 工作流统计看板页面。
 * 展示管理端流程定义、运行实例与历史实例数量。
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getAdminStats, type AdminStats } from '@/api/workflow'

const { t } = useI18n()

const loading = ref(false)
const stats = ref<AdminStats>({
  definitionCount: 0,
  runningInstanceCount: 0,
  completedInstanceCount: 0,
  totalInstanceCount: 0,
})

const panels = computed(() => [
  {
    label: t('workflow.definitionCount'),
    value: stats.value.definitionCount,
    icon: 'SetUp',
  },
  {
    label: t('workflow.runningInstanceCount'),
    value: stats.value.runningInstanceCount,
    icon: 'Loading',
  },
  {
    label: t('workflow.completedInstanceCount'),
    value: stats.value.completedInstanceCount,
    icon: 'CircleCheck',
  },
  {
    label: t('workflow.totalInstanceCount'),
    value: stats.value.totalInstanceCount,
    icon: 'DataAnalysis',
  },
])

async function loadStats() {
  loading.value = true
  try {
    const res = await getAdminStats()
    stats.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(() => loadStats())
</script>

<template>
  <div v-loading="loading" class="page-container">
    <div class="toolbar">
      <el-button type="primary" @click="loadStats">
        <el-icon><RefreshRight /></el-icon>
        {{ t('common.refresh') }}
      </el-button>
    </div>

    <div class="stats-grid">
      <div v-for="panel in panels" :key="panel.label" class="stat-panel">
        <div class="stat-icon">
          <component :is="panel.icon" />
        </div>
        <div class="stat-content">
          <div class="stat-label">{{ panel.label }}</div>
          <div class="stat-value">{{ panel.value }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.stat-panel {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 112px;
  padding: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  font-size: 24px;
}

.stat-content {
  min-width: 0;
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.stat-value {
  margin-top: 8px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  font-weight: 600;
  line-height: 1;
}
</style>
