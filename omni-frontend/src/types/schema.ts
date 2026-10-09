/** 动态表单支持的值类型。 */
export type DynamicFormValue = string | number | boolean | null

/** 动态表单键值集合。 */
export type DynamicFormValues = Record<string, DynamicFormValue>

/** 动态字段支持的控件类型。 */
export type DynamicFieldType = 'string' | 'number' | 'boolean' | 'select' | 'textarea'

/** 动态下拉选项。 */
export interface DynamicSelectOption {
  value: string
  label: string
}

/** 扁平或标准 JSON Schema 中的单字段配置。 */
export interface DynamicSchemaFieldConfig {
  type?: DynamicFieldType
  label?: string
  title?: string
  required?: boolean
  default?: DynamicFormValue
  options?: DynamicSelectOption[]
  enum?: string[]
}

/** 解析后的 Schema 字段条目。 */
export interface DynamicSchemaEntries {
  entries: Array<[string, DynamicSchemaFieldConfig]>
  required: Set<string>
}

/** 判断未知值是否为普通对象。 */
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** 将未知字段配置安全转换为受支持的最小结构。 */
export function toDynamicFieldConfig(value: unknown): DynamicSchemaFieldConfig {
  if (!isRecord(value)) return {}
  const options = Array.isArray(value.options)
    ? value.options.filter(isRecord).map(option => ({
      value: String(option.value ?? ''),
      label: String(option.label ?? option.value ?? ''),
    }))
    : undefined
  const enumValues = Array.isArray(value.enum) ? value.enum.map(String) : undefined
  const rawType = typeof value.type === 'string' ? value.type : undefined
  const supportedTypes: DynamicFieldType[] = ['string', 'number', 'boolean', 'select', 'textarea']

  return {
    type: supportedTypes.includes(rawType as DynamicFieldType) ? rawType as DynamicFieldType : undefined,
    label: typeof value.label === 'string' ? value.label : undefined,
    title: typeof value.title === 'string' ? value.title : undefined,
    required: typeof value.required === 'boolean' ? value.required : undefined,
    default: typeof value.default === 'string'
      || typeof value.default === 'number'
      || typeof value.default === 'boolean'
      || value.default === null
      ? value.default
      : undefined,
    options,
    enum: enumValues,
  }
}

/** 同时解析扁平格式与标准 object/properties JSON Schema。 */
export function normalizeDynamicSchema(schema: Record<string, unknown>): DynamicSchemaEntries {
  const standard = schema.type === 'object' && isRecord(schema.properties)
  const source = standard && isRecord(schema.properties) ? schema.properties : schema
  const requiredValues = standard && Array.isArray(schema.required)
    ? schema.required.filter((value): value is string => typeof value === 'string')
    : []

  return {
    entries: Object.entries(source).map(([key, value]) => [key, toDynamicFieldConfig(value)]),
    required: new Set(requiredValues),
  }
}
