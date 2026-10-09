<script setup lang="ts">
/** 隔离展示业务人员通常不需要操作的技术标识。 */
import { useI18n } from 'vue-i18n'
import type { ProcurementApprovalRoute } from '@/api/procurement-approval-route'

defineProps<{
  route: ProcurementApprovalRoute
}>()

const { t } = useI18n()
</script>

<template>
  <el-collapse class="advanced-info">
    <el-collapse-item :title="t('procurementApprovalRules.technicalInfo')" name="technical">
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item :label="t('procurementApprovalRules.routeCode')">
          {{ route.routeCode }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.modelVersionId')">
          {{ route.modelVersionId }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.priority')">
          {{ route.priority }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('procurementApprovalRules.updatedAt')">
          {{ route.updateTime }}
        </el-descriptions-item>
      </el-descriptions>
    </el-collapse-item>
  </el-collapse>
</template>

<style scoped>
.advanced-info {
  margin-top: 8px;
}
</style>
