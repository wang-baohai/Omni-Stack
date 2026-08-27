<script setup lang="ts">
/** 资产处置页面，丢弃和报废共用审批与实物处置闭环。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelAssetDisposal,
  completeAssetDisposal,
  createAssetDisposal,
  getAssetDisposal,
  listAssetDisposals,
  retryAssetDisposal,
  type AssetDisposal,
  type AssetDisposalType,
} from '@/api/asset-disposal'
import type { AssetOperationStatus } from '@/api/asset-transfer'
import {
  listAssetOperationOptions,
  type AssetOperationOption,
} from '@/api/asset-asset'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const { t } = useI18n()
const canList = computed(() => permissionStore.hasPermission('asset:disposal:list'))
const loading = ref(false)
const rows = ref<AssetDisposal[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; disposalType?: AssetDisposalType; status?: AssetOperationStatus }>({ keyword: '', disposalType: undefined, status: undefined })
type DisposalStatusOption = { value: AssetOperationStatus; label: string; type: 'info' | 'warning' | 'success' | 'danger' }
const statuses = computed<DisposalStatusOption[]>(() => [
  { value: 'PENDING_APPROVAL', label: t('assetDisposal.statusPending'), type: 'warning' }, { value: 'START_FAILED', label: t('assetDisposal.statusStartFailed'), type: 'danger' },
  { value: 'APPROVED', label: t('assetDisposal.statusApproved'), type: 'success' }, { value: 'REJECTED', label: t('assetDisposal.statusRejected'), type: 'danger' },
  { value: 'COMPLETED', label: t('assetDisposal.statusCompleted'), type: 'success' }, { value: 'CANCELLED', label: t('assetDisposal.statusCancelled'), type: 'info' },
])
const statusMap = computed(() => Object.fromEntries(statuses.value.map((item) => [item.value, item])) as Record<AssetOperationStatus, DisposalStatusOption>)

function statusInfo(status: AssetOperationStatus) {
  return statusMap.value[status]
}

async function loadRows() {
  if (!canList.value) return
  loading.value = true
  try {
    const response = await listAssetDisposals({ keyword: query.keyword.trim() || undefined, disposalType: query.disposalType, status: query.status, page: currentPage.value, size: pageSize.value })
    rows.value = response.data.data.records; total.value = response.data.data.total
  } finally { loading.value = false }
}
function search() { currentPage.value = 1; loadRows() }
function resetQuery() { Object.assign(query, { keyword: '', disposalType: undefined, status: undefined }); search() }

const createVisible = ref(false)
const formRef = ref<FormInstance>()
const decimalPattern = /^\d{1,16}(?:\.\d{1,2})?$/
const form = reactive({ assetId: undefined as number | undefined, disposalType: 'DISCARD' as AssetDisposalType, reason: '', residualValue: '', disposalMethod: '' })
const rules = computed<FormRules>(() => ({
  assetId: [{ required: true, message: t('assetDisposal.assetRequired'), trigger: 'change' }], disposalType: [{ required: true, message: t('assetDisposal.typeRequired'), trigger: 'change' }],
  reason: [{ required: true, message: t('assetDisposal.reasonRequired'), trigger: 'blur' }],
  residualValue: [{ validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => { if (!value || decimalPattern.test(value)) callback(); else callback(new Error(t('assetDisposal.residualInvalid'))) }, trigger: 'blur' }],
}))
const assetOptions = ref<AssetOperationOption[]>([])
const assetOptionsLoading = ref(false)
async function loadAssetOptions(keyword = '') {
  assetOptionsLoading.value = true
  try {
    assetOptions.value = (await listAssetOperationOptions('disposal', keyword.trim())).data.data
  } finally {
    assetOptionsLoading.value = false
  }
}
async function openCreate() {
  Object.assign(form, { assetId: undefined, disposalType: 'DISCARD', reason: '', residualValue: '', disposalMethod: '' })
  createVisible.value = true
  await loadAssetOptions()
}
async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.assetId) return
  await createAssetDisposal({ assetId: form.assetId, disposalType: form.disposalType, reason: form.reason.trim(), residualValue: form.residualValue || undefined, disposalMethod: form.disposalMethod.trim() || undefined })
  ElMessage.success(t('assetDisposal.created'))
  createVisible.value = false
  if (canList.value) await loadRows()
}

const detailVisible = ref(false)
const detail = ref<AssetDisposal>()
async function openDetail(row: AssetDisposal) { detailVisible.value = true; detail.value = (await getAssetDisposal(row.id)).data.data }
async function execute(row: AssetDisposal, action: 'complete' | 'cancel' | 'retry') {
  const labels = { complete: t('assetDisposal.confirmPhysical'), cancel: t('assetDisposal.cancelRequest'), retry: t('assetDisposal.retryApproval') }
  await ElMessageBox.confirm(t('assetDisposal.actionConfirm', { action: labels[action], no: row.disposalNo }), labels[action], { type: 'warning' })
  if (action === 'complete') await completeAssetDisposal(row.id, row.version); else if (action === 'cancel') await cancelAssetDisposal(row.id, row.version); else await retryAssetDisposal(row.id, row.version)
  ElMessage.success(t('assetDisposal.actionSuccess', { action: labels[action] })); await loadRows()
}

function canRetry(row: AssetDisposal) {
  return row.status === 'START_FAILED'
    || (row.status === 'PENDING_APPROVAL' && row.workflowStartStatus === 'PENDING')
}

function canCancel(row: AssetDisposal) {
  return row.status === 'START_FAILED' && row.workflowStartStatus === 'FAILED'
}

onMounted(() => {
  if (canList.value) loadRows()
})
</script>

<template>
  <div class="asset-operation-page">
    <el-alert type="warning" :closable="false" show-icon :title="t('assetDisposal.warning')" />
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('assetDisposal.title') }}</span><el-button v-permission="'asset:disposal:create'" type="primary" @click="openCreate">{{ t('assetDisposal.initiate') }}</el-button></div></template>
      <el-form v-if="canList" :inline="true" :model="query">
        <el-form-item :label="t('assetDisposal.keyword')"><el-input v-model="query.keyword" clearable :placeholder="t('assetDisposal.keywordPlaceholder')" @keyup.enter="search" /></el-form-item>
        <el-form-item :label="t('assetDisposal.type')"><el-select v-model="query.disposalType" clearable :placeholder="t('assetDisposal.all')" style="width: 130px"><el-option :label="t('assetDisposal.discard')" value="DISCARD" /><el-option :label="t('assetDisposal.scrap')" value="SCRAP" /></el-select></el-form-item>
        <el-form-item :label="t('common.status')"><el-select v-model="query.status" clearable :placeholder="t('assetDisposal.all')" style="width: 150px"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">{{ t('common.search') }}</el-button><el-button @click="resetQuery">{{ t('common.reset') }}</el-button></el-form-item>
      </el-form>
      <el-table v-if="canList" v-loading="loading" :data="rows" stripe>
        <el-table-column prop="disposalNo" :label="t('assetDisposal.disposalNo')" min-width="180" /><el-table-column prop="assetNo" :label="t('assetDisposal.assetNo')" min-width="160" /><el-table-column prop="assetName" :label="t('assetDisposal.assetName')" min-width="180" />
        <el-table-column :label="t('assetDisposal.type')" min-width="90"><template #default="{ row }"><el-tag>{{ row.disposalType === 'SCRAP' ? t('assetDisposal.scrap') : t('assetDisposal.discard') }}</el-tag></template></el-table-column>
        <el-table-column prop="residualValue" :label="t('assetDisposal.residualValue')" min-width="110" align="right"><template #default="{ row }">{{ row.residualValue || '—' }}</template></el-table-column>
        <el-table-column :label="t('common.status')" min-width="120"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column :label="t('common.actions')" min-width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('assetDisposal.details') }}</el-button><el-button v-if="row.status === 'APPROVED'" v-permission="'asset:disposal:complete'" link type="success" @click="execute(row, 'complete')">{{ t('assetDisposal.complete') }}</el-button>
            <el-button v-if="canRetry(row)" v-permission="'asset:disposal:retry'" link type="warning" @click="execute(row, 'retry')">{{ t('assetDisposal.retry') }}</el-button><el-button v-if="canCancel(row)" v-permission="'asset:disposal:cancel'" link type="danger" @click="execute(row, 'cancel')">{{ t('common.cancel') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="canList" v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
      <el-empty v-else :description="t('assetDisposal.noPermission')" />
    </el-card>
    <el-dialog v-model="createVisible" :title="t('assetDisposal.createTitle')" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item :label="t('assetDisposal.asset')" prop="assetId"><el-select v-model="form.assetId" filterable remote :remote-method="loadAssetOptions" :loading="assetOptionsLoading" :placeholder="t('assetDisposal.assetSearch')" style="width: 100%"><el-option v-for="asset in assetOptions" :key="asset.id" :value="asset.id" :label="`${asset.assetNo} · ${asset.name}`" /></el-select></el-form-item><el-form-item :label="t('assetDisposal.type')" prop="disposalType"><el-radio-group v-model="form.disposalType"><el-radio-button value="DISCARD">{{ t('assetDisposal.discard') }}</el-radio-button><el-radio-button value="SCRAP">{{ t('assetDisposal.scrap') }}</el-radio-button></el-radio-group></el-form-item>
        <el-form-item :label="t('assetDisposal.residualValue')" prop="residualValue"><el-input v-model="form.residualValue" :placeholder="t('assetDisposal.residualPlaceholder')" /></el-form-item><el-form-item :label="t('assetDisposal.method')"><el-input v-model="form.disposalMethod" maxlength="500" :placeholder="t('assetDisposal.methodPlaceholder')" /></el-form-item>
        <el-form-item :label="t('assetDisposal.reason')" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'asset:disposal:create'" type="primary" @click="submitCreate">
          {{ t('assetDisposal.submitApproval') }}
        </el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="detailVisible" :title="t('assetDisposal.detailTitle')" size="58%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item :label="t('assetDisposal.disposalNo')">{{ detail.disposalNo }}</el-descriptions-item><el-descriptions-item :label="t('common.status')">{{ statusMap[detail.status].label }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.asset')">{{ detail.assetNo }} / {{ detail.assetName }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.type')">{{ detail.disposalType === 'SCRAP' ? t('assetDisposal.scrap') : t('assetDisposal.discard') }}</el-descriptions-item>
        <el-descriptions-item :label="t('assetDisposal.residualValue')">{{ detail.residualValue || '—' }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.method')">{{ detail.disposalMethod || '—' }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.processInstance')">{{ detail.processInstanceId || detail.workflowStartStatus }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.previousStatus')">{{ detail.previousAssetStatus }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.reason')" :span="2">{{ detail.reason }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.asset-operation-page { display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
