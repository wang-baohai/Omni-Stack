<script setup lang="ts">
/** 资产调拨页面，审批在 Workflow 完成，业务交接在本页显式完成。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelAssetTransfer,
  completeAssetTransfer,
  createAssetTransfer,
  getAssetTransfer,
  listAssetTransfers,
  retryAssetTransfer,
  type AssetOperationStatus,
  type AssetTransfer,
} from '@/api/asset-transfer'
import {
  listAssetOperationOptions,
  listAssetUserOptions,
  type AssetOperationOption,
  type AssetUserOption,
} from '@/api/asset-asset'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const { t } = useI18n()
const canList = computed(() => permissionStore.hasPermission('asset:transfer:list'))
const loading = ref(false)
const rows = ref<AssetTransfer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; status?: AssetOperationStatus }>({ keyword: '', status: undefined })
type TransferStatusOption = { value: AssetOperationStatus; label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }
const statusOptions = computed<TransferStatusOption[]>(() => [
  { value: 'PENDING_APPROVAL', label: t('assetDisposal.statusPending'), type: 'warning' },
  { value: 'START_FAILED', label: t('assetDisposal.statusStartFailed'), type: 'danger' },
  { value: 'APPROVED', label: t('assetDisposal.statusApproved'), type: 'success' },
  { value: 'REJECTED', label: t('assetDisposal.statusRejected'), type: 'danger' },
  { value: 'COMPLETED', label: t('assetDisposal.statusCompleted'), type: 'success' },
  { value: 'CANCELLED', label: t('assetDisposal.statusCancelled'), type: 'info' },
])
const statusMap = computed(() => Object.fromEntries(statusOptions.value.map((item) => [item.value, item])) as Record<AssetOperationStatus, TransferStatusOption>)

function statusInfo(status: AssetOperationStatus) {
  return statusMap.value[status]
}

async function loadRows() {
  if (!canList.value) return
  loading.value = true
  try {
    const response = await listAssetTransfers({ keyword: query.keyword.trim() || undefined, status: query.status, page: currentPage.value, size: pageSize.value })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    loading.value = false
  }
}

function search() { currentPage.value = 1; loadRows() }
function resetQuery() { Object.assign(query, { keyword: '', status: undefined }); search() }

const createVisible = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ assetId: undefined as number | undefined, toUserId: undefined as number | undefined, toUnitId: undefined as number | undefined, toLocation: '', reason: '' })
const rules = computed<FormRules>(() => ({
  assetId: [{ required: true, message: t('assetDisposal.assetRequired'), trigger: 'change' }],
  toUserId: [{ required: true, message: t('assetTransfer.targetUserRequired'), trigger: 'change' }],
  toUnitId: [{ required: true, message: t('assetTransfer.targetUnitMissing'), trigger: 'change' }],
  reason: [{ required: true, message: t('assetTransfer.reasonRequired'), trigger: 'blur' }],
}))

const assetOptions = ref<AssetOperationOption[]>([])
const userOptions = ref<AssetUserOption[]>([])
const assetOptionsLoading = ref(false)
const userOptionsLoading = ref(false)

async function loadAssetOptions(keyword = '') {
  assetOptionsLoading.value = true
  try {
    assetOptions.value = (await listAssetOperationOptions('transfer', keyword.trim())).data.data
  } finally {
    assetOptionsLoading.value = false
  }
}

async function loadUserOptions(keyword = '') {
  userOptionsLoading.value = true
  try {
    userOptions.value = (await listAssetUserOptions(keyword.trim())).data.data
  } finally {
    userOptionsLoading.value = false
  }
}

function selectTargetUser(userId?: number) {
  form.toUnitId = userOptions.value.find((user) => user.id === userId)?.primaryUnitId
}

async function openCreate() {
  Object.assign(form, { assetId: undefined, toUserId: undefined, toUnitId: undefined, toLocation: '', reason: '' })
  createVisible.value = true
  await Promise.all([loadAssetOptions(), loadUserOptions()])
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.assetId || !form.toUserId || !form.toUnitId) return
  await createAssetTransfer({ assetId: form.assetId, toUserId: form.toUserId, toUnitId: form.toUnitId, toLocation: form.toLocation.trim() || undefined, reason: form.reason.trim() })
  ElMessage.success(t('assetTransfer.created'))
  createVisible.value = false
  if (canList.value) await loadRows()
}

const detailVisible = ref(false)
const detail = ref<AssetTransfer>()
async function openDetail(row: AssetTransfer) {
  detailVisible.value = true
  const response = await getAssetTransfer(row.id)
  detail.value = response.data.data
}

async function execute(row: AssetTransfer, action: 'complete' | 'cancel' | 'retry') {
  const labels = { complete: t('assetTransfer.completeHandover'), cancel: t('assetTransfer.cancelTransfer'), retry: t('assetDisposal.retryApproval') }
  await ElMessageBox.confirm(t('assetDisposal.actionConfirm', { action: labels[action], no: row.transferNo }), labels[action], { type: 'warning' })
  if (action === 'complete') await completeAssetTransfer(row.id, row.version)
  else if (action === 'cancel') await cancelAssetTransfer(row.id, row.version)
  else await retryAssetTransfer(row.id, row.version)
  ElMessage.success(t('assetDisposal.actionSuccess', { action: labels[action] }))
  await loadRows()
}

function canRetry(row: AssetTransfer) {
  return row.status === 'START_FAILED'
    || (row.status === 'PENDING_APPROVAL' && row.workflowStartStatus === 'PENDING')
}

function canCancel(row: AssetTransfer) {
  return row.status === 'START_FAILED' && row.workflowStartStatus === 'FAILED'
}

onMounted(() => {
  if (canList.value) loadRows()
})
</script>

<template>
  <div class="asset-operation-page">
    <el-alert type="info" :closable="false" show-icon :title="t('assetTransfer.workflowHint')" />
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('assetTransfer.title') }}</span><el-button v-permission="'asset:transfer:create'" type="primary" @click="openCreate">{{ t('assetTransfer.initiate') }}</el-button></div></template>
      <el-form v-if="canList" :inline="true" :model="query">
        <el-form-item :label="t('assetDisposal.keyword')"><el-input v-model="query.keyword" clearable :placeholder="t('assetTransfer.keywordPlaceholder')" @keyup.enter="search" /></el-form-item>
        <el-form-item :label="t('common.status')"><el-select v-model="query.status" clearable :placeholder="t('assetDisposal.all')" style="width: 150px"><el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">{{ t('common.search') }}</el-button><el-button @click="resetQuery">{{ t('common.reset') }}</el-button></el-form-item>
      </el-form>
      <el-table v-if="canList" v-loading="loading" :data="rows" stripe>
        <el-table-column prop="transferNo" :label="t('assetTransfer.transferNo')" min-width="180" />
        <el-table-column prop="assetNo" :label="t('assetDisposal.assetNo')" min-width="160" />
        <el-table-column prop="assetName" :label="t('assetDisposal.assetName')" min-width="180" />
        <el-table-column prop="fromUnitId" :label="t('assetTransfer.fromUnit')" min-width="100"><template #default="{ row }">{{ row.fromUnitId || '—' }}</template></el-table-column>
        <el-table-column prop="toUnitId" :label="t('assetTransfer.toUnit')" min-width="100" />
        <el-table-column prop="toUserId" :label="t('assetTransfer.toUser')" min-width="100" />
        <el-table-column :label="t('common.status')" min-width="120"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column :label="t('common.actions')" min-width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">{{ t('assetDisposal.details') }}</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="'asset:transfer:complete'" link type="success" @click="execute(row, 'complete')">{{ t('assetTransfer.complete') }}</el-button>
            <el-button v-if="canRetry(row)" v-permission="'asset:transfer:retry'" link type="warning" @click="execute(row, 'retry')">{{ t('assetDisposal.retry') }}</el-button>
            <el-button v-if="canCancel(row)" v-permission="'asset:transfer:cancel'" link type="danger" @click="execute(row, 'cancel')">{{ t('common.cancel') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="canList" v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
      <el-empty v-else :description="t('assetTransfer.noPermission')" />
    </el-card>

    <el-dialog v-model="createVisible" :title="t('assetTransfer.createTitle')" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item :label="t('assetDisposal.asset')" prop="assetId">
          <el-select v-model="form.assetId" filterable remote :remote-method="loadAssetOptions" :loading="assetOptionsLoading" :placeholder="t('assetDisposal.assetSearch')" style="width: 100%">
            <el-option v-for="asset in assetOptions" :key="asset.id" :value="asset.id" :label="`${asset.assetNo} · ${asset.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('assetTransfer.toUser')" prop="toUserId">
          <el-select v-model="form.toUserId" filterable remote :remote-method="loadUserOptions" :loading="userOptionsLoading" :placeholder="t('assetTransfer.userSearch')" style="width: 100%" @change="selectTargetUser">
            <el-option v-for="user in userOptions" :key="user.id" :value="user.id" :label="`${user.nickname || user.username}（${user.username}）`" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('assetTransfer.toUnit')" prop="toUnitId"><el-input-number v-model="form.toUnitId" disabled controls-position="right" /></el-form-item>
        <el-form-item :label="t('assetTransfer.toLocation')"><el-input v-model="form.toLocation" maxlength="100" /></el-form-item>
        <el-form-item :label="t('assetTransfer.reason')" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'asset:transfer:create'" type="primary" @click="submitCreate">
          {{ t('assetDisposal.submitApproval') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="t('assetTransfer.detailTitle')" size="58%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item :label="t('assetTransfer.transferNo')">{{ detail.transferNo }}</el-descriptions-item><el-descriptions-item :label="t('common.status')">{{ statusMap[detail.status].label }}</el-descriptions-item>
        <el-descriptions-item :label="t('assetDisposal.asset')">{{ detail.assetNo }} / {{ detail.assetName }}</el-descriptions-item><el-descriptions-item :label="t('assetTransfer.previousStatus')">{{ detail.previousAssetStatus }}</el-descriptions-item>
        <el-descriptions-item :label="t('assetTransfer.fromUserUnit')">{{ detail.fromUserId || '—' }} / {{ detail.fromUnitId || '—' }}</el-descriptions-item><el-descriptions-item :label="t('assetTransfer.toUserUnit')">{{ detail.toUserId }} / {{ detail.toUnitId }}</el-descriptions-item>
        <el-descriptions-item :label="t('assetTransfer.location')">{{ detail.fromLocation || '—' }} → {{ detail.toLocation || '—' }}</el-descriptions-item><el-descriptions-item :label="t('assetDisposal.processInstance')">{{ detail.processInstanceId || detail.workflowStartStatus }}</el-descriptions-item>
        <el-descriptions-item :label="t('assetTransfer.reason')" :span="2">{{ detail.reason }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.asset-operation-page { display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
