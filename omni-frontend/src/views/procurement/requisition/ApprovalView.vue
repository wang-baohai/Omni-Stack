<script setup lang="ts">
/**
 * 采购请购审批详情视图。
 * 归属 procurement 模块：核心审批工作台不静态导入本文件所属 API，仅在 workflow 与 procurement 同时存在时经 glob 动态装载。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  getProcurementRequisitionApprovalView,
  type ProcurementRequisitionApprovalView,
} from '@/api/procurement-requisition'

const props = defineProps<{
  taskId: string
  variables: Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'loaded'): void
  (e: 'failed', message: string): void
}>()

const { t } = useI18n()
const view = ref<ProcurementRequisitionApprovalView | null>(null)

function positiveInteger(value: unknown): number | null {
  const text = typeof value === 'number' || typeof value === 'string' ? String(value) : ''
  return /^\d+$/.test(text) && text !== '0' ? Number(text) : null
}

onMounted(async () => {
  try {
    const requisitionId = positiveInteger(props.variables.requisitionId)
    if (!requisitionId) throw new Error(t('workspaceApproval.invalidRequisitionId'))
    const res = await getProcurementRequisitionApprovalView(requisitionId, props.taskId)
    view.value = res.data.data
    emit('loaded')
  } catch (error) {
    emit('failed', error instanceof Error ? error.message : t('workspaceApproval.formLoadFailed'))
  }
})
</script>

<template>
  <el-descriptions v-if="view" :column="2" border class="approval-business-form">
    <el-descriptions-item :label="t('workspaceApproval.requisitionNo')">
      {{ view.requisition.requisitionNo }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.category')">
      {{ view.requisition.primaryCategoryCode }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.title')" :span="2">
      {{ view.requisition.title }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.requester')">
      #{{ view.requisition.requesterUserId }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.requesterOrg')">
      #{{ view.requisition.requesterUnitId }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.estimatedAmount')">
      {{ view.requisition.totalAmount }}
      {{ view.requisition.currencyCode }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.approvalRound')">
      {{ view.requisition.approvalAttempt }}
    </el-descriptions-item>
    <el-descriptions-item :label="t('workspaceApproval.requisitionReason')" :span="2">
      {{ view.requisition.reason || '—' }}
    </el-descriptions-item>
  </el-descriptions>
  <el-table
    v-if="view"
    :data="view.requisition.lines"
    border
    size="small"
    class="approval-business-lines"
  >
    <el-table-column prop="lineNo" label="#" width="50" />
    <el-table-column prop="materialCode" :label="t('workspaceApproval.materialCode')" min-width="120" />
    <el-table-column prop="materialName" :label="t('workspaceApproval.materialName')" min-width="150" />
    <el-table-column prop="quantity" :label="t('workspaceApproval.quantity')" min-width="105" align="right" />
    <el-table-column prop="unit" :label="t('workspaceApproval.unit')" width="70" />
    <el-table-column prop="estimatedUnitPrice" :label="t('workspaceApproval.estimatedUnitPrice')" min-width="110" align="right" />
    <el-table-column prop="estimatedTotalPrice" :label="t('workspaceApproval.lineAmount')" min-width="120" align="right" />
  </el-table>
</template>

<style scoped lang="scss">
.approval-business-form {
  margin: 16px 0;
}

.approval-business-lines {
  margin-bottom: 18px;
}
</style>
