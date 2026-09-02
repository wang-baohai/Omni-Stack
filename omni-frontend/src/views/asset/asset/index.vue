<script setup lang="ts">
/** 资产台账页面，区分管理视图与固定 current_user 的“我的资产”视图。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  acceptAsset,
  allocateAsset,
  completeAssetMaintenance,
  createAsset,
  deleteAsset,
  getAsset,
  getAssetHistory,
  listAssetUserOptions,
  listAssetSupplierOptions,
  listAssets,
  listMyAssets,
  returnAsset,
  startAssetMaintenance,
  updateAsset,
  type AssetDetail,
  type AssetHistory,
  type MyAssetStatus,
  type AssetStatus,
  type AssetSummary,
  type AssetUserOption,
  type AssetSupplierOption,
} from '@/api/asset-asset'
import { usePermissionStore } from '@/stores/permission'

const permissionStore = usePermissionStore()
const { t } = useI18n()
const canViewLedger = computed(() => permissionStore.hasPermission('asset:asset:list'))
const canViewSelf = computed(() => permissionStore.hasPermission('asset:asset:self'))
const loading = ref(false)
const rows = ref<AssetSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const userOptions = ref<AssetUserOption[]>([])
const userOptionsLoading = ref(false)
const supplierOptions = ref<AssetSupplierOption[]>([])
const supplierOptionsLoading = ref(false)
const activeTab = ref<'ledger' | 'my'>(canViewLedger.value ? 'ledger' : 'my')
const query = reactive<{
  keyword: string
  status?: AssetStatus
  categoryCode: string
  ownerUnitId?: number
  locationCode: string
}>({
  keyword: '',
  status: undefined,
  categoryCode: '',
  ownerUnitId: undefined,
  locationCode: '',
})

type AssetStatusOption = {
  value: AssetStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}
const statusOptions = computed<AssetStatusOption[]>(() => [
  { value: 'IN_STOCK', label: t('assetLedger.statusInStock'), type: 'info' },
  { value: 'ALLOCATED', label: t('assetLedger.statusAllocated'), type: 'primary' },
  { value: 'IN_USE', label: t('assetLedger.statusInUse'), type: 'success' },
  { value: 'MAINTENANCE', label: t('assetLedger.statusMaintenance'), type: 'warning' },
  { value: 'TRANSFER', label: t('assetLedger.statusTransfer'), type: 'warning' },
  { value: 'DISPOSAL_PENDING', label: t('assetLedger.statusDisposalPending'), type: 'warning' },
  { value: 'DISPOSED', label: t('assetLedger.statusDisposed'), type: 'danger' },
  { value: 'SCRAPPED', label: t('assetLedger.statusScrapped'), type: 'danger' },
])
const statusMap = computed(() => Object.fromEntries(statusOptions.value.map((item) => [item.value, item])) as Record<AssetStatus, AssetStatusOption>)
const myAssetStatuses = new Set<MyAssetStatus>([
  'ALLOCATED',
  'IN_USE',
  'MAINTENANCE',
  'TRANSFER',
  'DISPOSAL_PENDING',
])
const visibleStatusOptions = computed(() => activeTab.value === 'ledger'
  ? statusOptions.value
  : statusOptions.value.filter((item) => myAssetStatuses.has(item.value as MyAssetStatus)))

function statusInfo(status: AssetStatus) {
  return statusMap.value[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = activeTab.value === 'my'
      ? await listMyAssets({
        keyword: query.keyword.trim() || undefined,
        status: query.status && myAssetStatuses.has(query.status as MyAssetStatus)
          ? query.status as MyAssetStatus
          : undefined,
        categoryCode: query.categoryCode.trim() || undefined,
        page: currentPage.value,
        size: pageSize.value,
      })
      : await listAssets({
        keyword: query.keyword.trim() || undefined,
        status: query.status,
        categoryCode: query.categoryCode.trim() || undefined,
        ownerUnitId: query.ownerUnitId,
        locationCode: query.locationCode.trim() || undefined,
        page: currentPage.value,
        size: pageSize.value,
      })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    loading.value = false
  }
}

function switchTab() {
  if (activeTab.value === 'my' && query.status && !myAssetStatuses.has(query.status as MyAssetStatus)) {
    query.status = undefined
  }
  currentPage.value = 1
  loadRows()
}

function search() {
  currentPage.value = 1
  loadRows()
}

function resetQuery() {
  Object.assign(query, {
    keyword: '',
    status: undefined,
    categoryCode: '',
    ownerUnitId: undefined,
    locationCode: '',
  })
  search()
}

const saveVisible = ref(false)
const saveRef = ref<FormInstance>()
const editingId = ref<number>()
const decimalPattern = /^\d{1,16}(?:\.\d{1,2})?$/
const form = reactive({
  name: '',
  categoryCode: '',
  specification: '',
  brand: '',
  model: '',
  supplierId: undefined as number | undefined,
  supplierNameSnapshot: '',
  purchaseDate: '',
  purchaseAmount: '',
  currencyCode: 'CNY',
  locationCode: '',
  warrantyExpiryDate: '',
  expectedLifeYears: undefined as number | undefined,
  remark: '',
  ownerUserId: undefined as number | undefined,
  ownerUnitId: undefined as number | undefined,
  version: 0,
})
const saveRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('assetLedger.nameRequired'), trigger: 'blur' }],
  categoryCode: [{ required: true, message: t('assetLedger.categoryRequired'), trigger: 'blur' }],
  purchaseAmount: [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value || decimalPattern.test(value)) callback()
      else callback(new Error(t('assetLedger.purchaseAmountInvalid')))
    },
    trigger: 'blur',
  }],
  currencyCode: [
    { required: true, message: t('assetLedger.currencyRequired'), trigger: 'blur' },
    { pattern: /^[A-Z]{3}$/, message: t('assetLedger.currencyInvalid'), trigger: 'blur' },
  ],
  ownerUserId: [{ required: true, message: t('assetLedger.managerRequired'), trigger: 'change' }],
  ownerUnitId: [{ required: true, message: t('assetLedger.managerUnitMissing'), trigger: 'change' }],
}))

function resetForm() {
  editingId.value = undefined
  Object.assign(form, {
    name: '', categoryCode: '', specification: '', brand: '', model: '', supplierId: undefined,
    supplierNameSnapshot: '', purchaseDate: '', purchaseAmount: '', currencyCode: 'CNY',
    locationCode: '', warrantyExpiryDate: '', expectedLifeYears: undefined, remark: '',
    ownerUserId: undefined, ownerUnitId: undefined, version: 0,
  })
}

/** 搜索可分配的启用用户，选择后自动使用其主部门。 */
async function searchUserOptions(keyword = '') {
  userOptionsLoading.value = true
  try {
    const response = await listAssetUserOptions(keyword.trim() || undefined)
    userOptions.value = response.data.data
  } finally {
    userOptionsLoading.value = false
  }
}

/** 搜索已批准供应商，选择后固化名称快照。 */
async function searchSupplierOptions(keyword = '') {
  supplierOptionsLoading.value = true
  try {
    const response = await listAssetSupplierOptions(keyword.trim() || undefined)
    supplierOptions.value = response.data.data
  } finally {
    supplierOptionsLoading.value = false
  }
}

function applySupplier(supplierId: number | undefined) {
  form.supplierNameSnapshot = supplierOptions.value.find(item => item.id === supplierId)?.name || ''
}

function userOptionLabel(option: AssetUserOption) {
  return t('assetLedger.userOption', { name: option.nickname || option.username, username: option.username, unitId: option.primaryUnitId })
}

function applyOwnerUser(userId: number | undefined) {
  const option = userOptions.value.find(item => item.id === userId)
  form.ownerUnitId = option?.primaryUnitId
}

function openCreate() {
  resetForm()
  saveVisible.value = true
  Promise.all([searchUserOptions(), searchSupplierOptions()])
}

async function openEdit(row: AssetSummary) {
  const response = await getAsset(row.id)
  const asset = response.data.data
  editingId.value = asset.id
  Object.assign(form, {
    name: asset.name,
    categoryCode: asset.categoryCode,
    specification: asset.specification || '',
    brand: asset.brand || '',
    model: asset.model || '',
    supplierId: asset.supplierId || undefined,
    supplierNameSnapshot: asset.supplierNameSnapshot || '',
    purchaseDate: asset.purchaseDate || '',
    purchaseAmount: asset.purchaseAmount || '',
    currencyCode: asset.currencyCode || 'CNY',
    locationCode: asset.locationCode || '',
    warrantyExpiryDate: asset.warrantyExpiryDate || '',
    expectedLifeYears: asset.expectedLifeYears || undefined,
    remark: asset.remark || '',
    ownerUserId: asset.ownerUserId,
    ownerUnitId: asset.ownerUnitId,
    version: asset.version,
  })
  saveVisible.value = true
  Promise.all([
    searchUserOptions(),
    searchSupplierOptions(asset.supplierNameSnapshot || ''),
  ])
}

async function save() {
  const valid = await saveRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.ownerUserId || !form.ownerUnitId) return
  const commonPayload = {
    name: form.name.trim(),
    categoryCode: form.categoryCode.trim(),
    specification: form.specification.trim() || undefined,
    brand: form.brand.trim() || undefined,
    model: form.model.trim() || undefined,
    supplierId: form.supplierId,
    supplierNameSnapshot: form.supplierNameSnapshot.trim() || undefined,
    purchaseDate: form.purchaseDate || undefined,
    purchaseAmount: form.purchaseAmount || undefined,
    currencyCode: form.currencyCode.trim(),
    warrantyExpiryDate: form.warrantyExpiryDate || undefined,
    expectedLifeYears: form.expectedLifeYears,
    remark: form.remark.trim() || undefined,
    ownerUserId: form.ownerUserId,
    ownerUnitId: form.ownerUnitId,
  }
  if (editingId.value) await updateAsset(editingId.value, { ...commonPayload, version: form.version })
  else await createAsset({ ...commonPayload, locationCode: form.locationCode.trim() || undefined })
  ElMessage.success(editingId.value ? t('assetLedger.updated') : t('assetLedger.stocked'))
  saveVisible.value = false
  await loadRows()
}

const detailVisible = ref(false)
const detail = ref<AssetDetail>()
const history = ref<AssetHistory[]>([])

async function openDetail(row: AssetSummary) {
  detailVisible.value = true
  const [detailResponse, historyResponse] = await Promise.all([
    getAsset(row.id),
    getAssetHistory(row.id),
  ])
  detail.value = detailResponse.data.data
  history.value = historyResponse.data.data.records
}

const allocateVisible = ref(false)
const allocating = ref<AssetSummary>()
const allocateForm = reactive({ targetUserId: undefined as number | undefined, targetUnitId: undefined as number | undefined, remark: '' })
const allocateRef = ref<FormInstance>()
const allocateRules = computed<FormRules>(() => ({
  targetUserId: [{ required: true, message: t('assetTransfer.targetUserRequired'), trigger: 'change' }],
  targetUnitId: [{ required: true, message: t('assetTransfer.targetUnitMissing'), trigger: 'change' }],
}))

function openAllocate(row: AssetSummary) {
  allocating.value = row
  Object.assign(allocateForm, { targetUserId: undefined, targetUnitId: undefined, remark: '' })
  allocateVisible.value = true
  searchUserOptions()
}

function applyAllocateUser(userId: number | undefined) {
  const option = userOptions.value.find(item => item.id === userId)
  allocateForm.targetUnitId = option?.primaryUnitId
  allocateRef.value?.validateField('targetUnitId').catch(() => undefined)
}

async function submitAllocate() {
  const valid = await allocateRef.value?.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning(t('assetLedger.validTargetUserRequired'))
    return
  }
  if (!allocating.value || !allocateForm.targetUserId || !allocateForm.targetUnitId) {
    return
  }
  await allocateAsset(allocating.value.id, {
    version: allocating.value.version,
    targetUserId: allocateForm.targetUserId,
    targetUnitId: allocateForm.targetUnitId,
    remark: allocateForm.remark.trim() || undefined,
  })
  ElMessage.success(t('assetLedger.allocated'))
  allocateVisible.value = false
  await loadRows()
}

async function executeCommand(row: AssetSummary, action: 'accept' | 'return' | 'maintenanceStart' | 'maintenanceComplete') {
  const labels = { accept: t('assetLedger.accept'), return: t('assetLedger.returnAsset'), maintenanceStart: t('assetLedger.sendMaintenance'), maintenanceComplete: t('assetLedger.completeMaintenance') }
  await ElMessageBox.confirm(t('assetDisposal.actionConfirm', { action: labels[action], no: row.assetNo }), labels[action], { type: 'warning' })
  if (action === 'accept') await acceptAsset(row.id, row.version)
  else if (action === 'return') await returnAsset(row.id, row.version)
  else if (action === 'maintenanceStart') await startAssetMaintenance(row.id, row.version)
  else await completeAssetMaintenance(row.id, row.version)
  ElMessage.success(t('assetDisposal.actionSuccess', { action: labels[action] }))
  await loadRows()
}

async function remove(row: AssetSummary) {
  await ElMessageBox.confirm(t('assetLedger.deleteConfirm', { no: row.assetNo }), t('assetLedger.deleteTitle'), { type: 'warning' })
  await deleteAsset(row.id, row.version)
  ElMessage.success(t('assetLedger.deleted'))
  await loadRows()
}

onMounted(loadRows)
</script>

<template>
  <div class="asset-ledger-page">
    <el-tabs v-model="activeTab" @tab-change="switchTab">
      <el-tab-pane v-if="canViewLedger" :label="t('assetLedger.ledger')" name="ledger" />
      <el-tab-pane v-if="canViewSelf" :label="t('assetLedger.myAssets')" name="my" />
    </el-tabs>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ activeTab === 'ledger' ? t('assetLedger.ledger') : t('assetLedger.myAssets') }}</span>
          <el-button v-if="activeTab === 'ledger'" v-permission="'asset:asset:create'" type="primary" @click="openCreate">
            {{ t('assetLedger.manualStock') }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item :label="t('assetDisposal.keyword')">
          <el-input v-model="query.keyword" clearable :placeholder="t('assetLedger.keywordPlaceholder')" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="query.status" clearable :placeholder="t('assetDisposal.all')" style="width: 150px">
            <el-option v-for="option in visibleStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('assetLedger.category')">
          <el-input v-model="query.categoryCode" clearable maxlength="50" :placeholder="t('assetLedger.categoryCode')" />
        </el-form-item>
        <el-form-item v-if="activeTab === 'ledger'" :label="t('assetLedger.managerUnit')">
          <el-input-number
            v-model="query.ownerUnitId"
            :min="1"
            controls-position="right"
            :placeholder="t('assetLedger.unitId')"
          />
        </el-form-item>
        <el-form-item v-if="activeTab === 'ledger'" :label="t('assetLedger.location')">
          <el-input v-model="query.locationCode" clearable :placeholder="t('assetLedger.locationCode')" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="assetNo" :label="t('assetDisposal.assetNo')" min-width="170" />
        <el-table-column prop="name" :label="t('assetDisposal.assetName')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryCode" :label="t('assetLedger.category')" min-width="130" />
        <el-table-column prop="brand" :label="t('assetLedger.brand')" min-width="110"><template #default="{ row }">{{ row.brand || '—' }}</template></el-table-column>
        <el-table-column prop="locationCode" :label="t('assetLedger.location')" min-width="120"><template #default="{ row }">{{ row.locationCode || '—' }}</template></el-table-column>
        <el-table-column :label="t('assetLedger.originalValue')" min-width="140" align="right"><template #default="{ row }">{{ row.purchaseAmount ? `${row.purchaseAmount} ${row.currencyCode}` : '—' }}</template></el-table-column>
        <el-table-column :label="t('common.status')" min-width="110"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column :label="t('common.actions')" min-width="350" fixed="right">
          <template #default="{ row }">
            <el-button v-if="activeTab === 'ledger'" link type="primary" @click="openDetail(row)">{{ t('assetDisposal.details') }}</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:update'" link type="primary" @click="openEdit(row)">{{ t('common.edit') }}</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:allocate'" link type="success" @click="openAllocate(row)">{{ t('assetLedger.allocate') }}</el-button>
            <el-button v-if="activeTab === 'my' && row.status === 'ALLOCATED'" v-permission="'asset:asset:accept'" link type="success" @click="executeCommand(row, 'accept')">{{ t('assetLedger.accept') }}</el-button>
            <el-button v-if="activeTab === 'my' && ['ALLOCATED', 'IN_USE'].includes(row.status)" v-permission="'asset:asset:return'" link type="warning" @click="executeCommand(row, 'return')">{{ t('assetLedger.return') }}</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_USE'" v-permission="'asset:asset:maintenance'" link type="warning" @click="executeCommand(row, 'maintenanceStart')">{{ t('assetLedger.sendMaintenance') }}</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'MAINTENANCE'" v-permission="'asset:asset:maintenance'" link type="success" @click="executeCommand(row, 'maintenanceComplete')">{{ t('assetLedger.completeMaintenance') }}</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:delete'" link type="danger" @click="remove(row)">{{ t('common.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
    </el-card>

    <el-dialog v-model="saveVisible" :title="editingId ? t('assetLedger.editAsset') : t('assetLedger.manualStock')" width="760px">
      <el-form ref="saveRef" :model="form" :rules="saveRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('assetDisposal.assetName')" prop="name"><el-input v-model="form.name" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('assetLedger.categoryCode')" prop="categoryCode"><el-input v-model="form.categoryCode" maxlength="64" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('assetLedger.brand')"><el-input v-model="form.brand" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('assetLedger.model')"><el-input v-model="form.model" maxlength="100" /></el-form-item></el-col>
          <el-col v-if="!editingId" :span="8"><el-form-item :label="t('assetLedger.location')"><el-input v-model="form.locationCode" maxlength="100" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('assetLedger.specification')"><el-input v-model="form.specification" maxlength="500" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmCommon.supplier')"><el-select v-model="form.supplierId" filterable remote clearable :remote-method="searchSupplierOptions" :loading="supplierOptionsLoading" :placeholder="t('assetLedger.supplierSearch')" style="width: 100%" @change="applySupplier"><el-option v-for="option in supplierOptions" :key="option.id" :value="option.id" :label="`${option.supplierNo} · ${option.name}`" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('assetLedger.supplierSnapshot')"><el-input v-model="form.supplierNameSnapshot" disabled :placeholder="t('assetLedger.autoSupplier')" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('assetLedger.purchaseDate')"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('assetLedger.assetValue')" prop="purchaseAmount"><el-input v-model="form.purchaseAmount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item :label="t('assetLedger.currency')" prop="currencyCode"><el-input v-model="form.currencyCode" maxlength="3" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('assetLedger.warrantyExpiry')"><el-date-picker v-model="form.warrantyExpiryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('assetLedger.expectedLife')"><el-input-number v-model="form.expectedLifeYears" :min="1" :max="200" /><span class="unit-tip">{{ t('assetLedger.years') }}</span></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item :label="t('assetLedger.managerUser')" prop="ownerUserId">
              <el-select
                v-model="form.ownerUserId"
                filterable
                remote
                clearable
                :remote-method="searchUserOptions"
                :loading="userOptionsLoading"
                :placeholder="t('assetLedger.userSearch')"
                style="width: 100%"
                @change="applyOwnerUser"
              >
                <el-option v-for="option in userOptions" :key="option.id" :label="userOptionLabel(option)" :value="option.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item :label="t('assetLedger.managerUnit')" prop="ownerUnitId"><el-input v-model="form.ownerUnitId" disabled :placeholder="t('assetLedger.autoUserUnit')" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('assetLedger.remark')"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          v-permission="editingId ? 'asset:asset:update' : 'asset:asset:create'"
          type="primary"
          @click="save"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="allocateVisible" :title="t('assetLedger.allocateAsset')" width="520px">
      <el-form ref="allocateRef" :model="allocateForm" :rules="allocateRules" label-width="100px">
        <el-form-item :label="t('assetTransfer.toUser')" prop="targetUserId">
          <el-select
            v-model="allocateForm.targetUserId"
            filterable
            remote
            clearable
            :remote-method="searchUserOptions"
            :loading="userOptionsLoading"
            :placeholder="t('assetLedger.userSearch')"
            style="width: 100%"
            @change="applyAllocateUser"
          >
            <el-option v-for="option in userOptions" :key="option.id" :label="userOptionLabel(option)" :value="option.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('assetTransfer.toUnit')" prop="targetUnitId"><el-input v-model="allocateForm.targetUnitId" disabled :placeholder="t('assetLedger.autoUserUnit')" /></el-form-item>
        <el-form-item :label="t('assetLedger.remark')"><el-input v-model="allocateForm.remark" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="allocateVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="'asset:asset:allocate'" type="primary" @click="submitAllocate">
          {{ t('assetLedger.confirmAllocate') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="t('assetLedger.detailTitle')" size="72%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item :label="t('assetDisposal.assetNo')">{{ detail.assetNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')"><el-tag :type="statusInfo(detail.status).type">{{ statusInfo(detail.status).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.category')">{{ detail.categoryCode }}</el-descriptions-item>
          <el-descriptions-item :label="t('srmCommon.name')" :span="2">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.brandModel')">{{ detail.brand || '—' }} / {{ detail.model || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.location')">{{ detail.locationCode || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.currentUser')">{{ detail.currentUserId || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.currentUnit')">{{ detail.currentUnitId || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.originalValue')">{{ detail.purchaseAmount ? `${detail.purchaseAmount} ${detail.currencyCode}` : '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('srmCommon.supplier')">{{ detail.supplierNameSnapshot || detail.supplierId || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.procurementSource')">{{ detail.sourcePoNo || t('assetLedger.manualStock') }} / {{ detail.sourceGrNo || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('assetLedger.remark')" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3>{{ t('assetLedger.changeHistory') }}</h3>
        <el-table :data="history" border>
          <el-table-column prop="changedTime" :label="t('crmOpportunity.time')" min-width="170" />
          <el-table-column prop="fromStatus" :label="t('assetTransfer.previousStatus')" min-width="120"><template #default="{ row }">{{ row.fromStatus || t('common.create') }}</template></el-table-column>
          <el-table-column prop="toStatus" :label="t('assetLedger.newStatus')" min-width="120" />
          <el-table-column prop="changedByUserId" :label="t('crmOpportunity.operator')" min-width="100" />
          <el-table-column prop="remark" :label="t('assetLedger.description')" min-width="240"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.asset-ledger-page { display: flex; flex-direction: column; gap: 12px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { justify-content: flex-end; margin-top: 16px; }
.unit-tip { margin-left: 8px; color: var(--el-text-color-secondary); }
h3 { margin: 24px 0 12px; }
</style>
