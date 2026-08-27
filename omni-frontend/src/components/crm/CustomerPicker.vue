<script setup lang="ts">
/** CRM 客户远程选择器，使用客户列表权限限定候选范围。 */
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listCustomers, type CrmCustomer } from '@/api/crm-customer'
import { usePermissionStore } from '@/stores/permission'

const props = withDefaults(defineProps<{
  modelValue?: number
  disabled?: boolean
  placeholder?: string
}>(), {
  modelValue: undefined,
  disabled: false,
  placeholder: undefined,
})

const { t } = useI18n()

const emit = defineEmits<{
  'update:modelValue': [value: number | undefined]
  change: [customer: CrmCustomer | undefined]
}>()

const permissionStore = usePermissionStore()
const options = ref<CrmCustomer[]>([])
const loading = ref(false)

async function loadOptions(keyword?: string) {
  if (!permissionStore.hasPermission('crm:customer:list')) return
  loading.value = true
  try {
    const response = await listCustomers({ keyword, page: 1, size: 20 })
    options.value = response.data.data.records
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
    :placeholder="permissionStore.hasPermission('crm:customer:list') ? (props.placeholder || t('crmUi.selectCustomer')) : t('crmUi.noCustomerPermission')"
    :disabled="props.disabled || !permissionStore.hasPermission('crm:customer:list')"
    :loading="loading"
    filterable
    remote
    clearable
    :remote-method="loadOptions"
    style="width: 100%"
    @update:model-value="handleChange"
  >
    <el-option
      v-for="customer in options"
      :key="customer.id"
      :label="`${customer.name} (${customer.customerNo})`"
      :value="customer.id"
    />
  </el-select>
</template>

<style scoped lang="scss">
</style>
