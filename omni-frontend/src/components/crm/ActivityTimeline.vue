<script setup lang="ts">
/** CRM 活动时间线。内容始终按纯文本渲染，禁止使用 v-html。 */
import { ref, watch } from 'vue'
import { listActivityTimeline, type ActivityRootType, type CrmActivity } from '@/api/crm-activity'
import { usePermissionStore } from '@/stores/permission'
import { useDictOptions } from '@/composables/useDictOptions'

const { options: activityTypeOptions } = useDictOptions('crm_activity_type')
const { options: activityStatusOptions } = useDictOptions('crm_activity_status')

function labelOf(options: { value: string; label: string }[], value?: string) {
  return options.find(o => o.value === value)?.label ?? value ?? '-'
}

const props = withDefaults(defineProps<{
  rootType: ActivityRootType
  rootId?: number
  limit?: number
}>(), {
  rootId: undefined,
  limit: 20,
})

const permissionStore = usePermissionStore()
const loading = ref(false)
const activities = ref<CrmActivity[]>([])

async function loadData() {
  if (!props.rootId || !permissionStore.hasPermission('crm:activity:list')) {
    activities.value = []
    return
  }
  loading.value = true
  try {
    const response = await listActivityTimeline({
      rootType: props.rootType,
      rootId: props.rootId,
      limit: props.limit,
    })
    activities.value = response.data.data
  } finally {
    loading.value = false
  }
}

function statusType(status: CrmActivity['status']) {
  if (status === 'COMPLETED') return 'success'
  if (status === 'CANCELLED') return 'info'
  return 'primary'
}

defineExpose({ reload: loadData })
watch(() => [props.rootType, props.rootId], loadData, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="activity-timeline">
    <el-alert
      v-if="!permissionStore.hasPermission('crm:activity:list')"
      title="当前账号没有跟进活动查看权限"
      type="info"
      :closable="false"
    />
    <el-empty v-else-if="activities.length === 0" description="暂无跟进记录" :image-size="80" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="activity in activities"
        :key="activity.id"
        :timestamp="activity.completedTime || activity.plannedStartTime || activity.createTime"
        placement="top"
      >
        <el-card shadow="never">
          <div class="timeline-heading">
            <strong>{{ activity.subject }}</strong>
            <el-tag :type="statusType(activity.status)" size="small">{{ labelOf(activityStatusOptions, activity.status) }}</el-tag>
          </div>
          <p v-if="activity.content" class="plain-content">{{ activity.content }}</p>
          <div class="timeline-meta">
            {{ labelOf(activityTypeOptions, activity.activityType) }} · {{ activity.performedByName || activity.ownerName || `用户 #${activity.performedByUserId || activity.ownerUserId}` }}
          </div>
        </el-card>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<style scoped lang="scss">
.activity-timeline {
  min-height: 120px;
}

.timeline-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.plain-content {
  margin: 10px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.timeline-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
