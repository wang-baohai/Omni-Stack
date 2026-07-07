<script setup lang="ts">
/**
 * 审批记录弹窗组件。
 * 以 el-table 展示流程实例的逐人审批记录，包括节点名称、审批人、
 * 审批结果（通过/驳回/自动通过/已取消/待审批）、审批意见和审批时间。
 */
import { ref } from 'vue'
import { getApprovalRecords, type ApprovalRecord } from '@/api/workflow'

const visible = ref(false)
const loading = ref(false)
const records = ref<ApprovalRecord[]>([])

/** 打开弹窗并加载审批记录 */
async function open(processInstanceId: string) {
  visible.value = true
  loading.value = true
  try {
    const res = await getApprovalRecords(processInstanceId)
    records.value = res.data.data
  } finally {
    loading.value = false
  }
}

/** 审批结果中文标签 */
function resultLabel(result: string) {
  const map: Record<string, string> = {
    approved: '通过',
    rejected: '驳回',
    'auto-approved': '自动通过',
    cancelled: '已取消',
    pending: '待审批',
  }
  return map[result] ?? result
}

/** 审批结果 el-tag 类型 */
function resultType(result: string) {
  const map: Record<string, string> = {
    approved: 'success',
    rejected: 'danger',
    'auto-approved': 'info',
    cancelled: 'info',
    pending: 'warning',
  }
  return map[result] ?? 'info'
}

defineExpose({ open })
</script>

<template>
  <el-dialog title="审批记录" v-model="visible" width="850px" destroy-on-close>
    <el-table v-loading="loading" :data="records" border stripe max-height="500">
      <el-table-column prop="nodeName" label="节点名称" width="140" />
      <el-table-column prop="assigneeName" label="审批人" width="100" />
      <el-table-column label="审批结果" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="resultType(row.result)" size="small">
            {{ resultLabel(row.result) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="comment" label="审批意见" min-width="200">
        <template #default="{ row }">
          {{ row.comment || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="approvalTime" label="审批时间" width="170">
        <template #default="{ row }">
          {{ row.approvalTime || '-' }}
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
