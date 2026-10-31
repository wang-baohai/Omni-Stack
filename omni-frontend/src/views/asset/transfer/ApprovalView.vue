<script setup lang="ts">
/**
 * 资产调拨审批详情视图。
 * 归属 asset 模块：核心审批工作台不静态导入本文件所属 API，仅在 workflow 与 asset 同时存在时经 glob 动态装载。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getAssetTransferApprovalView,
  type AssetTransfer,
} from '@/api/asset-transfer'

const props = defineProps<{
  taskId: string
  variables: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'loaded'): void
  (e: 'failed', message: string): void
}>()

const { t } = useI18n()
const view = ref<AssetTransfer | null>(null)

function positiveInteger(value: unknown): number | null {
  const text = typeof value === 'number' || typeof value === 'string' ? String(value) : ''
  return /^\d+$/.test(text) && text !== '0' ? Number(text) : null
}

onMounted(async () => {
  try {
    const transferId = positiveInteger(props.variables.transferId)
    if (!transferId) throw new Error(t('workspaceApproval.invalidTransferId'))
    const res = await getAssetTransferApprovalView(transferId, props.taskId)
    view.value = res.data.data
    emit('loaded')
  } catch (error) {
    emit('failed', error instanceof Error ? error.message : t('workspaceApproval.formLoadFailed'))
  }
})
</script>

<template>
  <el-descriptions v-if="view" :column="2" border class="approval-business-form">
    <el-descriptions-item :label="t('workspaceApproval.transferNo')">
      {{ view.transferNo }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.currentStatus')">
      {{ view.status }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.asset')" :span="2">
      {{ view.assetNo }} /
      {{ view.assetName }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.fromUserOrg')">
      {{ view.fromUserId || '—' }} /
      {{ view.fromUnitId || '—' }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.toUserOrg')">
      {{ view.toUserId }} /
      {{ view.toUnitId }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.location')" :span="2">
      {{ view.fromLocation || '—' }}
      →
      {{ view.toLocation || '—' }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.transferReason')" :span="2">
      {{ view.reason }}
    </el-descriptions-item>
  </el-descriptions>
</template>

<style scoped lang="scss">
.approval-business-form {
  margin: 16px 0;
}
</style>
