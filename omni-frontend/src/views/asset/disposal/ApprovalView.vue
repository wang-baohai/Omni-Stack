<script setup lang="ts">
/**
 * 资产处置审批详情视图。
 * 归属 asset 模块：核心审批工作台不静态导入本文件所属 API，仅在 workflow 与 asset 同时存在时经 glob 动态装载。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getAssetDisposalApprovalView,
  type AssetDisposal,
} from '@/api/asset-disposal'

const props = defineProps<{
  taskId: string
  variables: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'loaded'): void
  (e: 'failed', message: string): void
}>()

const { t } = useI18n()
const view = ref<AssetDisposal | null>(null)

function positiveInteger(value: unknown): number | null {
  const text = typeof value === 'number' || typeof value === 'string' ? String(value) : ''
  return /^\d+$/.test(text) && text !== '0' ? Number(text) : null
}

onMounted(async () => {
  try {
    const disposalId = positiveInteger(props.variables.disposalId)
    if (!disposalId) throw new Error(t('workspaceApproval.invalidDisposalId'))
    const res = await getAssetDisposalApprovalView(disposalId, props.taskId)
    view.value = res.data.data
    emit('loaded')
  } catch (error) {
    emit('failed', error instanceof Error ? error.message : t('workspaceApproval.formLoadFailed'))
  }
})
</script>

<template>
  <el-descriptions v-if="view" :column="2" border class="approval-business-form">
    <el-descriptions-item :label="t('workspaceApproval.disposalNo')">
      {{ view.disposalNo }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.disposalType')">
      {{ view.disposalType === 'SCRAP' ? t('workspaceApproval.scrap') : t('workspaceApproval.discard') }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.asset')" :span="2">
      {{ view.assetNo }} /
      {{ view.assetName }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.previousAssetStatus')">
      {{ view.previousAssetStatus }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.residualValue')">
      {{ view.residualValue || '—' }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.disposalMethod')" :span="2">
      {{ view.disposalMethod || '—' }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.disposalReason')" :span="2">
      {{ view.reason }}
    </el-descriptions-item>
  </el-descriptions>
</template>

<style scoped lang="scss">
.approval-business-form {
  margin: 16px 0;
}
</style>
