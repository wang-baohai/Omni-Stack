<script setup lang="ts">
/**
 * Schema 字段编辑器组件。
 * <p>可视化编辑 JSON Schema 参数模板，支持添加/删除字段、设置字段类型/必填/默认值、
 * 编辑下拉选项等操作。内部维护 FieldDef 数组，双向转换为 JSON Schema 字符串。</p>
 *
 * @see DynamicFormRenderer
 */
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { QuestionFilled } from '@element-plus/icons-vue'
import {
  isRecord,
  normalizeDynamicSchema,
  type DynamicFieldType,
  type DynamicSchemaFieldConfig,
  type DynamicSelectOption,
} from '@/types/schema'

const { t } = useI18n()

/** 字段定义（内部编辑模型） */
interface FieldDef {
  /** 字段键名 */
  key: string
  /** 显示标签 */
  label: string
  /** 字段类型（string/number/boolean/select/textarea） */
  type: DynamicFieldType
  /** 是否必填 */
  required: boolean
  /** 默认值（字符串类型） */
  defaultValue: string
  /** 默认值（布尔类型） */
  defaultBool: boolean
  /** 下拉选项 */
  options: DynamicSelectOption[]
}

const props = defineProps<{
  /** JSON Schema 字符串（双向绑定） */
  modelValue: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>()

/** 字段定义列表（内部编辑状态） */
const fields = ref<FieldDef[]>([])
/** 新增下拉选项的标签输入 */
const newOptionLabel = ref('')

/**
 * 添加一个新的空字段行。
 */
function addField() {
  fields.value.push({
    key: '',
    label: '',
    type: 'string',
    required: false,
    defaultValue: '',
    defaultBool: false,
    options: [],
  })
}

/**
 * 删除指定字段行。
 *
 * @param index - 字段索引
 */
function removeField(index: number) {
  fields.value.splice(index, 1)
}

/**
 * 字段类型变更时的回调，清理不兼容的默认值。
 *
 * @param index - 字段索引
 */
function onTypeChange(index: number) {
  const field = fields.value[index]
  if (field.type === 'select' && field.options.length === 0) {
    field.options = []
  }
  if (field.type === 'boolean') {
    field.defaultValue = ''
  }
}

/**
 * 为指定字段添加一个下拉选项。
 *
 * @param fieldIndex - 字段索引
 */
function addOption(fieldIndex: number) {
  const label = newOptionLabel.value.trim()
  if (!label) return
  fields.value[fieldIndex].options.push({ value: label, label })
  newOptionLabel.value = ''
}

/**
 * 删除指定字段的指定下拉选项。
 *
 * @param fieldIndex - 字段索引
 * @param optIndex - 选项索引
 */
function removeOption(fieldIndex: number, optIndex: number) {
  fields.value[fieldIndex].options.splice(optIndex, 1)
}

/**
 * 将内部字段列表构建为 JSON Schema 字符串。
 *
 * @returns JSON Schema 字符串，无有效字段时返回 null
 */
// fields → JSON Schema string
function buildSchema(): string | null {
  const validFields = fields.value.filter(f => f.key.trim())
  if (validFields.length === 0) return null

  const schema: Record<string, DynamicSchemaFieldConfig> = {}
  for (const f of validFields) {
    const entry: DynamicSchemaFieldConfig = {
      type: f.type,
      label: f.label || f.key,
      required: f.required,
    }
    if (f.type === 'boolean') {
      entry.default = f.defaultBool
    } else if (f.type === 'number') {
      if (f.defaultValue) entry.default = Number(f.defaultValue)
    } else if (f.type === 'select') {
      if (f.options.length > 0) entry.options = f.options
    } else {
      if (f.defaultValue) entry.default = f.defaultValue
    }
    schema[f.key.trim()] = entry
  }
  return JSON.stringify(schema)
}

/**
 * 解析 JSON Schema 字符串为内部字段列表。
 * <p>同时兼容扁平格式和标准 JSON Schema 格式（type: 'object' + properties）。</p>
 *
 * @param val - JSON Schema 字符串
 */
// JSON Schema string → fields
function parseSchema(val: string | null) {
  if (!val) {
    fields.value = []
    return
  }
  try {
    const parsed: unknown = JSON.parse(val)
    if (!isRecord(parsed)) {
      fields.value = []
      return
    }
    const normalized = normalizeDynamicSchema(parsed)

    fields.value = normalized.entries.map(([key, config]) => {
      // 标准 JSON Schema 用 enum，扁平格式用 options
      let options: Array<{ value: string; label: string }> = config.options || []
      if (options.length === 0 && Array.isArray(config.enum)) {
        options = config.enum.map((v: string) => ({ value: v, label: v }))
      }
      const type: DynamicFieldType = config.enum && options.length > 0 && !config.type
        ? 'select'
        : (config.type || 'string')

      return {
        key,
        label: config.label || config.title || key,
        type,
        required: config.required || normalized.required.has(key) || false,
        defaultValue: type === 'boolean' ? '' : (config.default != null ? String(config.default) : ''),
        defaultBool: type === 'boolean' ? !!config.default : false,
        options,
      }
    })
  } catch {
    fields.value = []
  }
}

// Watch fields → emit
watch(fields, () => {
  emit('update:modelValue', buildSchema())
}, { deep: true })

onMounted(() => {
  parseSchema(props.modelValue)
})

watch(() => props.modelValue, (val) => {
  const current = buildSchema()
  if (val !== current) {
    parseSchema(val)
  }
})
</script>

<template>
  <!-- Schema 字段编辑器：可视化编辑 JSON Schema 参数模板 -->
  <div class="schema-field-editor">
    <!-- 提示信息 -->
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      {{ t('userJobType.paramTemplateHint') }}
    </el-alert>
    <!-- 表头行 -->
    <el-row v-if="fields.length > 0" :gutter="8" class="header-row">
      <el-col :span="4">
        <span>{{ t('userJobType.headerFieldKey') }}</span>
        <el-tooltip :content="t('userJobType.tipFieldKey')" placement="top">
          <el-icon class="tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-col>
      <el-col :span="4">
        <span>{{ t('userJobType.headerFieldLabel') }}</span>
        <el-tooltip :content="t('userJobType.tipFieldLabel')" placement="top">
          <el-icon class="tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-col>
      <el-col :span="4">
        <span>{{ t('userJobType.headerFieldType') }}</span>
        <el-tooltip :content="t('userJobType.tipFieldType')" placement="top">
          <el-icon class="tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-col>
      <el-col :span="3">
        <span>{{ t('userJobType.headerFieldRequired') }}</span>
        <el-tooltip :content="t('userJobType.tipFieldRequired')" placement="top">
          <el-icon class="tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-col>
      <el-col :span="6">
        <span>{{ t('userJobType.headerFieldDefault') }}</span>
        <el-tooltip :content="t('userJobType.tipFieldDefault')" placement="top">
          <el-icon class="tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-col>
      <el-col :span="3"></el-col>
    </el-row>
    <!-- 字段列表 -->
    <div v-for="(field, index) in fields" :key="index" class="field-row">
      <el-row :gutter="8" align="middle">
        <el-col :span="4">
          <el-input v-model="field.key" :placeholder="t('userJobType.fieldKeyPlaceholder')" size="small" />
        </el-col>
        <el-col :span="4">
          <el-input v-model="field.label" :placeholder="t('userJobType.fieldLabelPlaceholder')" size="small" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="field.type" :placeholder="t('userJobType.fieldType')" size="small" @change="onTypeChange(index)">
            <el-option :label="t('userJobType.typeText')" value="string" />
            <el-option :label="t('userJobType.typeNumber')" value="number" />
            <el-option :label="t('userJobType.typeBoolean')" value="boolean" />
            <el-option :label="t('userJobType.typeSelect')" value="select" />
            <el-option :label="t('userJobType.typeTextarea')" value="textarea" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-checkbox v-model="field.required" :label="t('userJobType.fieldRequired')" size="small" />
        </el-col>
        <el-col :span="6">
          <el-input
            v-if="field.type !== 'boolean' && field.type !== 'select'"
            v-model="field.defaultValue" :placeholder="t('userJobType.fieldDefault')" size="small" />
          <el-switch v-else-if="field.type === 'boolean'" v-model="field.defaultBool" />
          <div v-else class="options-editor">
            <el-tag
              v-for="(opt, oi) in field.options" :key="oi" closable
              size="small" style="margin-right:4px" @close="removeOption(index, oi)">
              {{ opt.label }}
            </el-tag>
            <el-input
              v-model="newOptionLabel" :placeholder="t('userJobType.optionName')" size="small"
              style="width:80px;display:inline-block"
              @keyup.enter="addOption(index)" />
            <el-button link type="primary" size="small" @click="addOption(index)">+</el-button>
          </div>
        </el-col>
        <el-col :span="3">
          <el-button link type="danger" size="small" @click="removeField(index)">{{ t('userJobType.deleteField') }}</el-button>
        </el-col>
      </el-row>
    </div>
    <!-- 空状态提示 -->
    <el-empty v-if="fields.length === 0" :description="t('userJobType.emptyParams')" :image-size="60" />
    <!-- 添加字段按钮 -->
    <el-button type="primary" link style="margin-top:8px" @click="addField">
      + {{ t('userJobType.addField') }}
    </el-button>
  </div>
</template>

<style scoped>
.header-row {
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}
.header-row .el-col {
  display: flex;
  align-items: center;
  gap: 4px;
}
.tip-icon {
  color: #909399;
  cursor: pointer;
  font-size: 14px;
}
.field-row {
  margin-bottom: 8px;
}
.options-editor {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
