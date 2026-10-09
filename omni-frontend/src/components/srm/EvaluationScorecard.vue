<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EvaluationDimension, EvaluationItemInput } from '@/api/srm-evaluation'

const props = defineProps<{ dimensions: EvaluationDimension[] }>()
const { t } = useI18n()
const emit = defineEmits<{ (e: 'update:items', value: EvaluationItemInput[]): void }>()

const scores = reactive<Array<{ score: number; remark: string }>>(
  props.dimensions.map(() => ({ score: 0, remark: '' })),
)

watch(() => props.dimensions, (dims) => {
  while (scores.length < dims.length) scores.push({ score: 0, remark: '' })
  scores.length = dims.length
}, { immediate: true })

function emitItems() {
  const items: EvaluationItemInput[] = props.dimensions.map((dim, i) => ({
    dimensionId: dim.id,
    score: scores[i].score,
    indicatorName: dim.indicatorName,
    remark: scores[i].remark || undefined,
  }))
  emit('update:items', items)
}

watch(scores, emitItems, { deep: true })

const computedTotal = computed(() => {
  let total = 0
  props.dimensions.forEach((dim, i) => {
    total += (scores[i].score / 5) * dim.weight
  })
  return total.toFixed(2)
})

const levelLabel = computed(() => {
  const s = Number(computedTotal.value)
  if (s >= 90) return t('srmEvaluationCommon.strategic')
  if (s >= 75) return t('srmEvaluationCommon.preferred')
  if (s >= 60) return t('srmEvaluationCommon.qualified')
  return t('srmEvaluationCommon.eliminated')
})

const levelTagType = computed(() => {
  const s = Number(computedTotal.value)
  if (s >= 90) return 'success'
  if (s >= 75) return ''
  if (s >= 60) return 'warning'
  return 'danger'
})
</script>

<template>
  <div class="evaluation-scorecard">
    <div v-for="(dim, index) in dimensions" :key="dim.id" class="score-item">
      <div class="score-item__header">
        <span class="score-item__name">{{ dim.indicatorName }}</span>
        <el-tag size="small" type="info">{{ t('srmEvaluationCommon.weight', { value: dim.weight }) }}</el-tag>
      </div>
      <div class="score-item__body">
        <el-rate
          v-model="scores[index].score"
          :max="5"
          show-score
          :texts="[t('srmEvaluationCommon.bad'), t('srmEvaluationCommon.poor'), t('srmEvaluationCommon.average'), t('srmEvaluationCommon.good'), t('srmEvaluationCommon.excellent')]"
        />
        <el-input
          v-model="scores[index].remark"
          :placeholder="t('srmEvaluationCommon.remarkOptional')"
          maxlength="500"
          show-word-limit
          size="small"
          class="score-item__remark"
        />
      </div>
    </div>
    <div class="scorecard-summary">
      <span>{{ t('srmEvaluationCommon.weightedTotal') }}</span>
      <span class="scorecard-summary__total">{{ computedTotal }}</span>
      <el-tag :type="levelTagType" class="scorecard-summary__level">{{ levelLabel }}</el-tag>
    </div>
  </div>
</template>

<style scoped>
.evaluation-scorecard {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.score-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.score-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.score-item__name {
  font-weight: 600;
  font-size: 14px;
}
.score-item__body {
  display: flex;
  align-items: center;
  gap: 12px;
}
.score-item__remark {
  flex: 1;
}
.scorecard-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 16px;
}
.scorecard-summary__total {
  font-size: 24px;
  font-weight: bold;
  color: var(--el-color-primary);
}
</style>
