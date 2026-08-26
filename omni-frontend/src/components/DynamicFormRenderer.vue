<script setup lang="ts">
/**
 * 动态表单渲染器组件。
 * <p>根据 JSON Schema 配置动态生成表单字段，支持 string/number/boolean/textarea/select 五种类型。</p>
 *
 * @see SchemaFieldEditor
 */
import { computed, watch, onMounted, reactive, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  normalizeDynamicSchema,
  type DynamicFieldType,
  type DynamicFormValue,
  type DynamicFormValues,
  type DynamicSelectOption,
} from '@/types/schema'

const { t } = useI18n()

/** Schema 字段定义 */
interface SchemaField {
  /** 字段键名 */
  key: string
  /** 显示标签 */
  label: string
  /** 字段类型（string/number/boolean/textarea/select） */
  type: DynamicFieldType
  /** 是否必填 */
  required: boolean
  /** 默认值 */
  default?: DynamicFormValue
  /** 下拉选项（仅 type='select' 时有效） */
  options: DynamicSelectOption[]
}

const props = defineProps<{
  /** JSON Schema 配置对象 */
  schema: Record<string, unknown> | null
  /** 双向绑定的表单值 */
  modelValue: DynamicFormValues
}>()

const emit = defineEmits<{
  'update:modelValue': [value: DynamicFormValues]
}>()

/** 响应式表单值对象 */
const formValues = reactive<DynamicFormValues>({})

/**
 * 解析 Schema 为字段列表。
 * <p>同时兼容两种格式：
 * <ul>
 *   <li>扁平格式（SchemaFieldEditor 输出）：{ cupShape: { type, label, required, default, options } }</li>
 *   <li>标准 JSON Schema：{ type: 'object', properties: { cupShape: { type, title, enum, default } }, required: [...] }</li>
 * </ul>
 * </p>
 *
 * @returns SchemaField 数组
 */
const fieldList = computed<SchemaField[]>(() => {
  if (!props.schema) return []

  const normalized = normalizeDynamicSchema(props.schema)
  return normalized.entries.map(([key, config]) => {
    // 标准 JSON Schema 用 enum，扁平格式用 options
    let options: DynamicSelectOption[] = config.options || []
    if (options.length === 0 && Array.isArray(config.enum)) {
      options = config.enum.map((v: string) => ({ value: v, label: v }))
    }
    // enum 字段自动映射为 select 类型
    const type: DynamicFieldType = config.enum && options.length > 0
      ? 'select'
      : (config.type || 'string')

    return {
      key,
      label: config.label || config.title || key,
      type,
      required: config.required || normalized.required.has(key) || false,
      default: config.default,
      options,
    }
  })
})

/**
 * 初始化表单默认值：优先使用 modelValue，其次使用 Schema 默认值。
 */
function initDefaults() {
  for (const field of fieldList.value) {
    if (props.modelValue[field.key] !== undefined) {
      formValues[field.key] = props.modelValue[field.key]
    } else if (field.default !== undefined) {
      formValues[field.key] = field.default
    } else if (field.type === 'boolean') {
      formValues[field.key] = false
    } else if (field.type === 'number') {
      formValues[field.key] = 0
    } else {
      formValues[field.key] = ''
    }
  }
}

/**
 * 向父组件发射更新后的表单值（带防循环锁）。
 */
let isEmitting = false

function emitValues() {
  if (isEmitting) return
  isEmitting = true
  nextTick(() => {
    emit('update:modelValue', { ...formValues })
    isEmitting = false
  })
}

onMounted(() => {
  initDefaults()
  emitValues()
})

watch(() => props.schema, () => {
  if (isEmitting) return
  initDefaults()
  emitValues()
}, { deep: true })
</script>

<template>
  <!-- 动态表单渲染器：根据 JSON Schema 动态生成表单字段 -->
  <div class="dynamic-form-renderer">
    <!-- 表单字段区域 -->
    <div v-if="fieldList.length > 0" class="dynamic-form-fields">
      <div v-for="field in fieldList" :key="field.key" class="dynamic-form-field">
        <div class="field-label">
          {{ field.label }}
          <span v-if="field.required" class="field-required">*</span>
        </div>
        <!-- string -->
        <el-input
          v-if="field.type === 'string'"
          v-model="formValues[field.key]"
          :placeholder="'请输入' + field.label"
          @input="emitValues" />
        <!-- number -->
        <el-input-number
          v-else-if="field.type === 'number'"
          v-model="formValues[field.key]"
          style="width: 100%"
          @change="emitValues" />
        <!-- boolean -->
        <el-switch
          v-else-if="field.type === 'boolean'"
          v-model="formValues[field.key]"
          @change="emitValues" />
        <!-- textarea -->
        <el-input
          v-else-if="field.type === 'textarea'"
          v-model="formValues[field.key]"
          type="textarea" :rows="3"
          :placeholder="'请输入' + field.label"
          @input="emitValues" />
        <!-- select -->
        <el-select
          v-else-if="field.type === 'select'"
          v-model="formValues[field.key]"
          :placeholder="'请选择' + field.label"
          style="width: 100%"
          @change="emitValues">
          <el-option
            v-for="opt in field.options" :key="opt.value"
            :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
    </div>
    <!-- 空状态提示 -->
    <el-empty v-else :description="t('userJob.noParams')" :image-size="60" />
  </div>
</template>

<style scoped>
.dynamic-form-renderer {
  width: 100%;
}

.dynamic-form-fields {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.dynamic-form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  line-height: 1.4;
}

.field-required {
  color: var(--el-color-danger);
  margin-left: 2px;
}

.dynamic-form-field :deep(.el-input),
.dynamic-form-field :deep(.el-select),
.dynamic-form-field :deep(.el-input-number) {
  width: 100%;
}
</style>
