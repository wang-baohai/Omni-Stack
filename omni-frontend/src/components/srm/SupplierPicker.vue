<script setup lang="ts">
/** SRM 供应商选择器。 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { listSuppliers, type SrmSupplier } from '@/api/srm-supplier'

const modelValue = defineModel<number>()
const { t } = useI18n()
const visible = ref(false)
const loading = ref(false)
const options = ref<SrmSupplier[]>([])

async function handleOpen() {
  visible.value = true
  loading.value = true
  try {
    const response = await listSuppliers({ page: 1, size: 50, status: 'APPROVED' })
    options.value = response.data.data.records
  } finally { loading.value = false }
}

function select(row: SrmSupplier) {
  modelValue.value = row.id
  visible.value = false
}
</script>

<template>
  <div class="supplier-picker">
    <el-input :model-value="modelValue ? `#${modelValue}` : ''" readonly :placeholder="t('srmCommon.selectSupplier')" @click="handleOpen" />
    <el-dialog v-model="visible" :title="t('srmCommon.selectSupplier')" width="680px" append-to-body>
      <el-table v-loading="loading" :data="options" border stripe highlight-current-row @current-change="select">
        <el-table-column prop="supplierNo" :label="t('srmCommon.number')" width="155" />
        <el-table-column prop="name" :label="t('srmCommon.name')" />
        <el-table-column prop="supplierType" :label="t('srmCommon.type')" width="110" />
        <el-table-column prop="levelCode" :label="t('srmCommon.level')" width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.supplier-picker { width: 100%; }
</style>
