<script setup lang="ts">
/** CRM 商机阶段看板；阶段选择只触发受控迁移事件，不直接修改本地权威状态。 */
import type { CrmOpportunity, OpportunityBoard, PipelineStage } from '@/api/crm-opportunity'

defineProps<{
  board: OpportunityBoard | null
  loading?: boolean
  canMove?: boolean
}>()

const emit = defineEmits<{
  move: [opportunity: CrmOpportunity, stage: PipelineStage]
  open: [opportunity: CrmOpportunity]
}>()

function formatAmount(amount: number, currencyCode = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: currencyCode }).format(amount || 0)
}
</script>

<template>
  <div v-loading="loading" class="stage-board">
    <el-empty v-if="!board || board.columns.length === 0" description="暂无商机阶段数据" />
    <div v-else class="stage-columns">
      <section v-for="column in board.columns" :key="column.stage.id" class="stage-column">
        <header>
          <div>
            <strong>{{ column.stage.stageName }}</strong>
            <el-tag size="small" effect="plain">{{ column.opportunities.length }}</el-tag>
          </div>
          <span>{{ formatAmount(column.totalAmount) }}</span>
        </header>
        <div class="stage-list">
          <el-card
            v-for="opportunity in column.opportunities"
            :key="opportunity.id"
            class="opportunity-card"
            shadow="hover"
            @click="emit('open', opportunity)"
          >
            <strong>{{ opportunity.name }}</strong>
            <span>客户 #{{ opportunity.customerId }}</span>
            <span>{{ formatAmount(opportunity.amount, opportunity.currencyCode) }}</span>
            <el-dropdown
              v-if="canMove && opportunity.status === 'OPEN'"
              trigger="click"
              @command="(stage: PipelineStage) => emit('move', opportunity, stage)"
              @click.stop
            >
              <el-button v-permission="'crm:opportunity:stage'" size="small" text type="primary">
                迁移阶段
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="target in board.columns.map((item) => item.stage)"
                    :key="target.id"
                    :command="target"
                    :disabled="target.id === opportunity.stageId"
                  >
                    {{ target.stageName }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-card>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stage-board {
  min-height: 260px;
  overflow-x: auto;
}

.stage-columns {
  display: flex;
  gap: 14px;
  min-width: max-content;
}

.stage-column {
  width: 280px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.stage-column header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--el-text-color-secondary);
}

.stage-column header div {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--el-text-color-primary);
}

.stage-list {
  display: grid;
  gap: 10px;
}

.opportunity-card {
  cursor: pointer;
}

.opportunity-card :deep(.el-card__body) {
  display: grid;
  gap: 8px;
}

.opportunity-card span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
