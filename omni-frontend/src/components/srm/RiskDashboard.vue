<script setup lang="ts">
import type { RiskDashboardVO } from '@/api/srm-risk'

defineProps<{ dashboard: RiskDashboardVO }>()
</script>

<template>
  <div class="risk-dashboard">
    <div class="risk-cards">
      <el-card shadow="hover" class="risk-card risk-card--red">
        <div class="risk-card__count">{{ dashboard.redCount }}</div>
        <div class="risk-card__label">高风险 RED</div>
      </el-card>
      <el-card shadow="hover" class="risk-card risk-card--yellow">
        <div class="risk-card__count">{{ dashboard.yellowCount }}</div>
        <div class="risk-card__label">中风险 YELLOW</div>
      </el-card>
      <el-card shadow="hover" class="risk-card risk-card--green">
        <div class="risk-card__count">{{ dashboard.greenCount }}</div>
        <div class="risk-card__label">低风险 GREEN</div>
      </el-card>
    </div>
    <div v-if="dashboard.topRiskSuppliers?.length" class="risk-top">
      <h4>风险供应商 TOP</h4>
      <el-table :data="dashboard.topRiskSuppliers" size="small" stripe>
        <el-table-column prop="supplierName" label="供应商" min-width="150">
          <template #default="{ row }">{{ row.supplierName || row.supplierId }}</template>
        </el-table-column>
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <span :class="['risk-dot', `risk-dot--${row.overallLevel?.toLowerCase()}`]" />
            {{ row.overallLevel }}
          </template>
        </el-table-column>
        <el-table-column prop="redIndicatorCount" label="红色指标数" width="100" align="center" />
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.risk-dashboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.risk-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.risk-card {
  text-align: center;
  padding: 8px;
}
.risk-card__count {
  font-size: 32px;
  font-weight: bold;
}
.risk-card__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.risk-card--red .risk-card__count { color: var(--el-color-danger); }
.risk-card--yellow .risk-card__count { color: var(--el-color-warning); }
.risk-card--green .risk-card__count { color: var(--el-color-success); }
.risk-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.risk-dot--red { background: var(--el-color-danger); }
.risk-dot--yellow { background: var(--el-color-warning); }
.risk-dot--green { background: var(--el-color-success); }
.risk-top h4 {
  margin: 0 0 8px;
  font-size: 14px;
}
</style>
