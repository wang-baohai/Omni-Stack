<script setup lang="ts">
/**
 * Cron 表达式生成器（用户友好版）。
 * 通过频率类型选择器 + 动态条件表单配置调度规则，
 * 自动转换为标准 6 段式 Cron 表达式，并提供自然语言预览。
 */
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

/** 频率类型 */
type FrequencyType =
  | 'every_minute'
  | 'every_x_minutes'
  | 'every_hour'
  | 'every_x_hours'
  | 'daily'
  | 'weekly'
  | 'monthly'

const props = defineProps<{
  modelValue: string
  /** 紧凑模式：嵌入父表单时隐藏频率标签，避免重复 */
  compact?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// ─── 状态 ───

const freqType = ref<FrequencyType>('daily')
const resetWarning = ref(false)

const params = reactive({
  /** 每 X 分钟 / 每 X 小时的间隔 */
  interval: 5,
  /** 分钟（0-59） */
  minute: 0,
  /** 小时（0-23） */
  hour: 8,
  /** 星期几（0=周日 ~ 6=周六） */
  dayOfWeek: 1,
  /** 日期（1-31） */
  dayOfMonth: 1,
})

// ─── Cron 生成 ───

const cronExpression = computed(() => {
  const p = (n: number) => String(n)
  switch (freqType.value) {
  case 'every_minute':
    return '0 * * * * ?'
  case 'every_x_minutes':
    return `0 */${params.interval} * * * ?`
  case 'every_hour':
    return `0 ${p(params.minute)} * * * ?`
  case 'every_x_hours':
    return `0 ${p(params.minute)} */${params.interval} * * ?`
  case 'daily':
    return `0 ${p(params.minute)} ${p(params.hour)} * * ?`
  case 'weekly':
    return `0 ${p(params.minute)} ${p(params.hour)} ? * ${p(params.dayOfWeek)}`
  case 'monthly':
    return `0 ${p(params.minute)} ${p(params.hour)} ${p(params.dayOfMonth)} * ?`
  default:
    return '0 0 8 * * ?'
  }
})

// ─── 人类可读预览 ───

const pad = (n: number) => String(n).padStart(2, '0')

const weekDayLabels: Record<number, string> = {
  0: 'userJob.sun',
  1: 'userJob.mon',
  2: 'userJob.tue',
  3: 'userJob.wed',
  4: 'userJob.thu',
  5: 'userJob.fri',
  6: 'userJob.sat',
}

const previewText = computed(() => {
  switch (freqType.value) {
  case 'every_minute':
    return t('cron.preview.everyMinute')
  case 'every_x_minutes':
    return t('cron.preview.everyXMinutes', { n: params.interval })
  case 'every_hour':
    return t('cron.preview.everyHour', { m: params.minute })
  case 'every_x_hours':
    return t('cron.preview.everyXHours', { n: params.interval, m: params.minute })
  case 'daily':
    return t('cron.preview.daily', { h: pad(params.hour), m: pad(params.minute) })
  case 'weekly':
    return t('cron.preview.weekly', {
      day: t(weekDayLabels[params.dayOfWeek] || 'userJob.mon'),
      h: pad(params.hour),
      m: pad(params.minute),
    })
  case 'monthly':
    return t('cron.preview.monthly', { d: params.dayOfMonth, h: pad(params.hour), m: pad(params.minute) })
  default:
    return ''
  }
})

// ─── 反向解析 ───

function isNumeric(s: string): boolean {
  return /^\d+$/.test(s)
}

function parseCron(cron: string) {
  if (!cron) return
  const parts = cron.trim().split(/\s+/)
  if (parts.length < 6) return

  const [, min, hour, dom, , dow] = parts

  // 每分钟：min === '*'
  if (min === '*') {
    freqType.value = 'every_minute'
    return
  }

  // 每 X 分钟：min starts with */
  if (min.startsWith('*/')) {
    const n = parseInt(min.slice(2), 10)
    if (!isNaN(n) && n > 0) {
      freqType.value = 'every_x_minutes'
      params.interval = n
      return
    }
  }

  // 每小时：hour === '*' && min 为数字
  if (hour === '*' && isNumeric(min)) {
    freqType.value = 'every_hour'
    params.minute = parseInt(min, 10)
    return
  }

  // 每 X 小时：hour starts with */ && min 为数字
  if (hour.startsWith('*/') && isNumeric(min)) {
    const n = parseInt(hour.slice(2), 10)
    if (!isNaN(n) && n > 0) {
      freqType.value = 'every_x_hours'
      params.interval = n
      params.minute = parseInt(min, 10)
      return
    }
  }

  // 每天：dom === '*' && dow === '?' && hour 为数字
  if (dom === '*' && dow === '?' && isNumeric(hour) && isNumeric(min)) {
    freqType.value = 'daily'
    params.hour = parseInt(hour, 10)
    params.minute = parseInt(min, 10)
    return
  }

  // 每周：dow 为数字 && dow !== '?'
  if (dow !== '?' && isNumeric(dow) && isNumeric(hour) && isNumeric(min)) {
    freqType.value = 'weekly'
    params.dayOfWeek = parseInt(dow, 10)
    params.hour = parseInt(hour, 10)
    params.minute = parseInt(min, 10)
    return
  }

  // 每月：dom 为数字 && dom !== '?'
  if (dom !== '?' && isNumeric(dom) && isNumeric(hour) && isNumeric(min)) {
    freqType.value = 'monthly'
    params.dayOfMonth = parseInt(dom, 10)
    params.hour = parseInt(hour, 10)
    params.minute = parseInt(min, 10)
    return
  }

  // 无法识别 → 警告 + 重置
  resetWarning.value = true
  freqType.value = 'daily'
  params.hour = 8
  params.minute = 0
}

// ─── 频率类型切换时设置合理默认值 ───

function onFreqTypeChange() {
  resetWarning.value = false
  switch (freqType.value) {
  case 'every_x_minutes':
    params.interval = 5
    break
  case 'every_hour':
    params.minute = 0
    break
  case 'every_x_hours':
    params.interval = 2
    params.minute = 0
    break
  case 'daily':
    params.hour = 8
    params.minute = 0
    break
  case 'weekly':
    params.dayOfWeek = 1
    params.hour = 9
    params.minute = 0
    break
  case 'monthly':
    params.dayOfMonth = 1
    params.hour = 0
    params.minute = 0
    break
  }
}

// ─── 发射 Cron ───

function emitCron() {
  emit('update:modelValue', cronExpression.value)
}

// ─── 生命周期 & 监听 ───

onMounted(() => {
  parseCron(props.modelValue)
  emitCron()
})

watch(() => props.modelValue, (val) => {
  if (val && val !== cronExpression.value) {
    parseCron(val)
  }
})

watch(cronExpression, () => {
  emitCron()
})
</script>

<template>
  <div class="cron-generator">
    <!-- 无法识别的旧 Cron 警告 -->
    <el-alert
      v-if="resetWarning"
      type="warning"
      :description="t('cron.resetWarning')"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <el-form :label-width="compact ? '0px' : '80px'" size="default" :class="{ 'cron-compact': compact }">
      <!-- 频率类型选择 -->
      <el-form-item v-if="!compact" :label="t('cron.frequencyType')">
        <el-select v-model="freqType" :teleported="false" style="width: 100%" @change="onFreqTypeChange">
          <el-option value="every_minute" :label="t('cron.everyMinute')" />
          <el-option value="every_x_minutes" :label="t('cron.everyXMinutes')" />
          <el-option value="every_hour" :label="t('cron.everyHour')" />
          <el-option value="every_x_hours" :label="t('cron.everyXHours')" />
          <el-option value="daily" :label="t('cron.daily')" />
          <el-option value="weekly" :label="t('cron.weekly')" />
          <el-option value="monthly" :label="t('cron.monthly')" />
        </el-select>
      </el-form-item>
      <el-select v-else v-model="freqType" :teleported="false" style="width: 100%" @change="onFreqTypeChange">
        <el-option value="every_minute" :label="t('cron.everyMinute')" />
        <el-option value="every_x_minutes" :label="t('cron.everyXMinutes')" />
        <el-option value="every_hour" :label="t('cron.everyHour')" />
        <el-option value="every_x_hours" :label="t('cron.everyXHours')" />
        <el-option value="daily" :label="t('cron.daily')" />
        <el-option value="weekly" :label="t('cron.weekly')" />
        <el-option value="monthly" :label="t('cron.monthly')" />
      </el-select>

      <!-- 每 X 分钟：间隔 -->
      <el-form-item v-if="freqType === 'every_x_minutes'" :label="t('cron.interval')">
        <el-input-number v-model="params.interval" :min="1" :max="59" />
        <span class="cron-unit">{{ t('cron.minute') }}</span>
      </el-form-item>

      <!-- 每小时：在第几分 -->
      <el-form-item v-if="freqType === 'every_hour'" :label="t('cron.minute')">
        <el-input-number v-model="params.minute" :min="0" :max="59" />
        <span class="cron-unit">{{ t('cron.minute') }}</span>
      </el-form-item>

      <!-- 每 X 小时：间隔 + 在第几分 -->
      <template v-if="freqType === 'every_x_hours'">
        <el-form-item :label="t('cron.interval')">
          <el-input-number v-model="params.interval" :min="1" :max="23" />
          <span class="cron-unit">{{ t('cron.hour') }}</span>
        </el-form-item>
        <el-form-item :label="t('cron.minute')">
          <el-input-number v-model="params.minute" :min="0" :max="59" />
          <span class="cron-unit">{{ t('cron.minute') }}</span>
        </el-form-item>
      </template>

      <!-- 每天：时间 -->
      <template v-if="freqType === 'daily'">
        <el-form-item :label="t('cron.hour')">
          <el-input-number v-model="params.hour" :min="0" :max="23" />
          <span class="cron-unit">{{ t('cron.hour') }}</span>
        </el-form-item>
        <el-form-item :label="t('cron.minute')">
          <el-input-number v-model="params.minute" :min="0" :max="59" />
          <span class="cron-unit">{{ t('cron.minute') }}</span>
        </el-form-item>
      </template>

      <!-- 每周：星期几 + 时间 -->
      <template v-if="freqType === 'weekly'">
        <el-form-item :label="t('cron.dayOfWeek')">
          <el-select v-model="params.dayOfWeek" :teleported="false" style="width: 100%">
            <el-option :value="0" :label="t('userJob.sun')" />
            <el-option :value="1" :label="t('userJob.mon')" />
            <el-option :value="2" :label="t('userJob.tue')" />
            <el-option :value="3" :label="t('userJob.wed')" />
            <el-option :value="4" :label="t('userJob.thu')" />
            <el-option :value="5" :label="t('userJob.fri')" />
            <el-option :value="6" :label="t('userJob.sat')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('cron.hour')">
          <el-input-number v-model="params.hour" :min="0" :max="23" />
          <span class="cron-unit">{{ t('cron.hour') }}</span>
        </el-form-item>
        <el-form-item :label="t('cron.minute')">
          <el-input-number v-model="params.minute" :min="0" :max="59" />
          <span class="cron-unit">{{ t('cron.minute') }}</span>
        </el-form-item>
      </template>

      <!-- 每月：日期 + 时间 -->
      <template v-if="freqType === 'monthly'">
        <el-form-item :label="t('cron.dayOfMonth')">
          <el-input-number v-model="params.dayOfMonth" :min="1" :max="31" />
          <span class="cron-unit">{{ t('cron.day') }}</span>
        </el-form-item>
        <el-form-item :label="t('cron.hour')">
          <el-input-number v-model="params.hour" :min="0" :max="23" />
          <span class="cron-unit">{{ t('cron.hour') }}</span>
        </el-form-item>
        <el-form-item :label="t('cron.minute')">
          <el-input-number v-model="params.minute" :min="0" :max="59" />
          <span class="cron-unit">{{ t('cron.minute') }}</span>
        </el-form-item>
      </template>
    </el-form>

    <!-- 自然语言预览 -->
    <div class="cron-preview">
      <el-tag type="success" size="large" effect="plain">{{ previewText }}</el-tag>
    </div>
  </div>
</template>

<style scoped>
.cron-generator {
  width: 100%;
}

.cron-compact :deep(.el-form-item) {
  margin-bottom: 12px;
}

.cron-compact :deep(.el-form-item__label) {
  display: none;
}

.cron-unit {
  margin-left: 8px;
  color: var(--omni-text-secondary);
  font-size: 14px;
}

.cron-preview {
  margin-top: 12px;
}
</style>
