<script setup lang="ts">
/**
 * 版本历史对话框。
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { listVersions, type ModelVersionVO } from '@/api/workflow-model'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  modelId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()

const versions = ref<ModelVersionVO[]>([])
const loading = ref(false)

watch(() => props.visible, async (val) => {
  if (val && props.modelId) {
    loading.value = true
    try {
      const res = await listVersions(props.modelId)
      versions.value = res.data.data
    } finally {
      loading.value = false
    }
  }
})

function statusTagType(status: string): string {
  switch (status) {
  case 'PUBLISHED': return 'success'
  case 'DRAFT': return 'warning'
  case 'FAILED': return 'danger'
  case 'ARCHIVED': return 'info'
  default: return ''
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('workflow.versionHistory')"
    width="700px"
    @close="handleClose"
  >
    <el-table v-loading="loading" :data="versions" stripe border size="small">
      <el-table-column prop="version" :label="t('workflow.businessVersion')" width="100" align="center" />
      <el-table-column :label="t('common.status')" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="engineVersion" :label="t('workflow.engineVersion')" width="100" align="center" />
      <el-table-column prop="publishBy" :label="t('workflow.publishedBy')" width="120" />
      <el-table-column prop="publishTime" :label="t('workflow.publishTime')" width="180" />
      <el-table-column prop="xmlSha256" label="SHA-256" min-width="180">
        <template #default="{ row }">
          <span v-if="row.xmlSha256" class="sha-text">{{ row.xmlSha256.slice(0, 16) }}...</span>
          <span v-else class="text-muted">—</span>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="handleClose">{{ t('common.back') }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.sha-text {
  font-family: monospace;
  font-size: 12px;
}
.text-muted {
  color: var(--el-text-color-placeholder);
}
</style>
