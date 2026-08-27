<script setup lang="ts">
/** CRM 负责人远程选择器，仅在拥有负责人候选权限时发起查询。 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCrmOwners, type CrmOwnerOption } from '@/api/crm-lead'
import { usePermissionStore } from '@/stores/permission'

const props = withDefaults(defineProps<{
  modelValue?: number
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
}>(), {
  modelValue: undefined,
  placeholder: undefined,
  clearable: true,
  disabled: false,
})

const { t } = useI18n()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  change: [owner: CrmOwnerOption | undefined]
}>()

const permissionStore = usePermissionStore()
const options = ref<CrmOwnerOption[]>([])
const loading = ref(false)

async function loadOptions(keyword?: string) {
  if (!permissionStore.hasPermission('crm:owner:list')) return
  loading.value = true
  try {
    const response = await listCrmOwners(keyword)
    options.value = response.data.data
  } finally {
    loading.value = false
  }
}

function handleChange(value: number | undefined) {
  emit('update:modelValue', value)
  emit('change', options.value.find((item) => item.id === value))
}

onMounted(() => loadOptions())
</script>

<template>
  <el-select
    :model-value="props.modelValue"
    :placeholder="permissionStore.hasPermission('crm:owner:list') ? (props.placeholder || t('crmUi.selectOwner')) : t('crmUi.noOwnerPermission')"
    :clearable="props.clearable"
    :disabled="props.disabled || !permissionStore.hasPermission('crm:owner:list')"
    :loading="loading"
    filterable
    remote
    :remote-method="loadOptions"
    style="width: 100%"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="owner in options"
      :key="owner.id"
      :label="`${owner.nickname || owner.username}${owner.primaryUnitId ? ` · ${t('crmUi.departmentNumber', { id: owner.primaryUnitId })}` : ''}`"
      :value="owner.id"
    />
  </el-select>
</template>

<style scoped lang="scss">
</style>
