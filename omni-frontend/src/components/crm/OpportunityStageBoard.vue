<script setup lang="ts">
/** CRM 商机阶段看板；阶段选择/拖拽均只触发受控迁移事件，不直接修改本地权威状态。 */
import { ref } from 'vue'
import type { CrmOpportunity, OpportunityBoard, PipelineStage } from '@/api/crm-opportunity'
import { useI18n } from 'vue-i18n'

const { t, locale } = useI18n()

const props = defineProps<{
  board: OpportunityBoard | null
  loading?: boolean
  canMove?: boolean
}>()

const emit = defineEmits<{
  move: [opportunity: CrmOpportunity, stage: PipelineStage]
  open: [opportunity: CrmOpportunity]
}>()

/** 拖拽瞬态视觉状态：仅用于反馈，不作为权威阶段来源；提交/失败后由父级刷新看板恢复。 */
const draggingId = ref<number | null>(null)
const dragOverStageId = ref<number | null>(null)

function formatAmount(amount: number, currencyCode = 'CNY') {
  return new Intl.NumberFormat(locale.value, { style: 'currency', currency: currencyCode }).format(amount || 0)
}

/** 卡片是否可拖拽：仅开放商机且有迁移权限。 */
function draggable(o: CrmOpportunity): boolean {
  return !!props.canMove && o.status === 'OPEN'
}

function onDragStart(o: CrmOpportunity) {
  if (!draggable(o)) {
    return
  }
  draggingId.value = o.id
}

function onDragEnd() {
  draggingId.value = null
  dragOverStageId.value = null
}

function onColumnDragOver(stage: PipelineStage) {
  dragOverStageId.value = stage.id
}

/** 落入目标列：复用与下拉相同的受控 move 事件（权限/version/原因/状态限制由父级 stage API 强制）；不本地改权威状态。 */
function onDrop(stage: PipelineStage) {
  const id = draggingId.value
  dragOverStageId.value = null
  if (!id) {
    return
  }
  const opportunity = props.board?.columns
    .flatMap((c) => c.opportunities)
    .find((o) => o.id === id)
  draggingId.value = null
  if (!opportunity || !draggable(opportunity) || opportunity.stageId === stage.id) {
    return
  }
  emit('move', opportunity, stage)
}
</script>

<template>
  <div v-loading="loading" class="stage-board">
    <el-empty v-if="!board || board.columns.length === 0" :description="t('crmUi.noOpportunityStages')" />
    <div v-else class="stage-columns">
      <section v-for="column in board.columns" :key="column.stage.id" class="stage-column">
        <header>
          <div>
            <strong>{{ column.stage.stageName }}</strong>
            <el-tag size="small" effect="plain">{{ column.opportunities.length }}</el-tag>
          </div>
          <span>{{ formatAmount(column.totalAmount) }}</span>
        </header>
        <div
          class="stage-list"
          :class="{ 'stage-list--over': dragOverStageId === column.stage.id }"
          @dragover.prevent="canMove && onColumnDragOver(column.stage)"
          @dragleave="dragOverStageId === column.stage.id && (dragOverStageId = null)"
          @drop.prevent="onDrop(column.stage)"
        >
          <el-card
            v-for="opportunity in column.opportunities"
            :key="opportunity.id"
            class="opportunity-card"
            :class="{ 'opportunity-card--dragging': draggingId === opportunity.id }"
            :draggable="String(draggable(opportunity))"
            shadow="hover"
            @dragstart="onDragStart(opportunity)"
            @dragend="onDragEnd"
            @click="emit('open', opportunity)"
          >
            <strong>{{ opportunity.name }}</strong>
            <span>{{ opportunity.customerName || t('crmUi.customerNumber', { id: opportunity.customerId }) }}</span>
            <span>{{ formatAmount(opportunity.amount, opportunity.currencyCode) }}</span>
            <el-dropdown
              v-if="canMove && opportunity.status === 'OPEN'"
              trigger="click"
              @command="(stage: PipelineStage) => emit('move', opportunity, stage)"
              @click.stop
            >
              <el-button v-permission="'crm:opportunity:stage'" size="small" text type="primary">
                {{ t('crmUi.moveStage') }}
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

.opportunity-card--dragging {
  opacity: 0.5;
}

.stage-list--over {
  outline: 2px dashed var(--el-color-primary);
  outline-offset: 4px;
  border-radius: 8px;
}
</style>
