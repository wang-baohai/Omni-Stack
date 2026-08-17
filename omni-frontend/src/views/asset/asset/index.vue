<script setup lang="ts">
/** 资产台账页面，区分管理视图与固定 current_user 的“我的资产”视图。 */
import { computed, onMounted, reactive, ref } from 'vue'
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

const statusOptions: Array<{
  value: AssetStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}> = [
  { value: 'IN_STOCK', label: '在库', type: 'info' },
  { value: 'ALLOCATED', label: '待领用', type: 'primary' },
  { value: 'IN_USE', label: '使用中', type: 'success' },
  { value: 'MAINTENANCE', label: '维修中', type: 'warning' },
  { value: 'TRANSFER', label: '调拨中', type: 'warning' },
  { value: 'DISPOSAL_PENDING', label: '待处置', type: 'warning' },
  { value: 'DISPOSED', label: '已丢弃', type: 'danger' },
  { value: 'SCRAPPED', label: '已报废', type: 'danger' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  AssetStatus,
  (typeof statusOptions)[number]
>
const myAssetStatuses = new Set<MyAssetStatus>([
  'ALLOCATED',
  'IN_USE',
  'MAINTENANCE',
  'TRANSFER',
  'DISPOSAL_PENDING',
])
const visibleStatusOptions = computed(() => activeTab.value === 'ledger'
  ? statusOptions
  : statusOptions.filter((item) => myAssetStatuses.has(item.value as MyAssetStatus)))

function statusInfo(status: AssetStatus) {
  return statusMap[status]
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
const saveRules: FormRules = {
  name: [{ required: true, message: '请输入资产名称', trigger: 'blur' }],
  categoryCode: [{ required: true, message: '请输入品类编码', trigger: 'blur' }],
  purchaseAmount: [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value || decimalPattern.test(value)) callback()
      else callback(new Error('原值须为十进制字符串，最多 16 位整数和 2 位小数'))
    },
    trigger: 'blur',
  }],
  currencyCode: [
    { required: true, message: '请输入币种代码', trigger: 'blur' },
    { pattern: /^[A-Z]{3}$/, message: '币种必须是 3 位大写代码', trigger: 'blur' },
  ],
  ownerUserId: [{ required: true, message: '请选择资产管理员', trigger: 'change' }],
  ownerUnitId: [{ required: true, message: '所选管理员缺少有效主部门', trigger: 'change' }],
}

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
  return `${option.nickname || option.username}（${option.username} · 部门 #${option.primaryUnitId}）`
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
  ElMessage.success(editingId.value ? '资产已更新' : '资产已入库')
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
const allocateRules: FormRules = {
  targetUserId: [{ required: true, message: '请选择目标用户', trigger: 'change' }],
  targetUnitId: [{ required: true, message: '目标用户缺少有效主部门', trigger: 'change' }],
}

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
    ElMessage.warning('请先选择具有有效主部门的目标用户')
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
  ElMessage.success('资产已分配，等待员工确认领用')
  allocateVisible.value = false
  await loadRows()
}

async function executeCommand(row: AssetSummary, action: 'accept' | 'return' | 'maintenanceStart' | 'maintenanceComplete') {
  const labels = { accept: '确认领用', return: '退还资产', maintenanceStart: '送修', maintenanceComplete: '修复归还' }
  await ElMessageBox.confirm(`确认${labels[action]}“${row.assetNo}”？`, labels[action], { type: 'warning' })
  if (action === 'accept') await acceptAsset(row.id, row.version)
  else if (action === 'return') await returnAsset(row.id, row.version)
  else if (action === 'maintenanceStart') await startAssetMaintenance(row.id, row.version)
  else await completeAssetMaintenance(row.id, row.version)
  ElMessage.success(`${labels[action]}成功`)
  await loadRows()
}

async function remove(row: AssetSummary) {
  await ElMessageBox.confirm(`确认删除在库资产“${row.assetNo}”？`, '删除确认', { type: 'warning' })
  await deleteAsset(row.id, row.version)
  ElMessage.success('资产已删除')
  await loadRows()
}

onMounted(loadRows)
</script>

<template>
  <div class="asset-ledger-page">
    <el-tabs v-model="activeTab" @tab-change="switchTab">
      <el-tab-pane v-if="canViewLedger" label="资产台账" name="ledger" />
      <el-tab-pane v-if="canViewSelf" label="我的资产" name="my" />
    </el-tabs>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ activeTab === 'ledger' ? '资产台账' : '我的资产' }}</span>
          <el-button v-if="activeTab === 'ledger'" v-permission="'asset:asset:create'" type="primary" @click="openCreate">
            手动入库
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="资产编号或名称" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option v-for="option in visibleStatusOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="品类">
          <el-input v-model="query.categoryCode" clearable maxlength="50" placeholder="品类编码" />
        </el-form-item>
        <el-form-item v-if="activeTab === 'ledger'" label="管理部门">
          <el-input-number
            v-model="query.ownerUnitId"
            :min="1"
            controls-position="right"
            placeholder="部门 ID"
          />
        </el-form-item>
        <el-form-item v-if="activeTab === 'ledger'" label="位置">
          <el-input v-model="query.locationCode" clearable placeholder="位置编码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="assetNo" label="资产编号" min-width="170" />
        <el-table-column prop="name" label="资产名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryCode" label="品类" min-width="130" />
        <el-table-column prop="brand" label="品牌" min-width="110"><template #default="{ row }">{{ row.brand || '—' }}</template></el-table-column>
        <el-table-column prop="locationCode" label="位置" min-width="120"><template #default="{ row }">{{ row.locationCode || '—' }}</template></el-table-column>
        <el-table-column label="原值" min-width="140" align="right"><template #default="{ row }">{{ row.purchaseAmount ? `${row.purchaseAmount} ${row.currencyCode}` : '—' }}</template></el-table-column>
        <el-table-column label="状态" min-width="110"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="350" fixed="right">
          <template #default="{ row }">
            <el-button v-if="activeTab === 'ledger'" link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:allocate'" link type="success" @click="openAllocate(row)">分配</el-button>
            <el-button v-if="activeTab === 'my' && row.status === 'ALLOCATED'" v-permission="'asset:asset:accept'" link type="success" @click="executeCommand(row, 'accept')">确认领用</el-button>
            <el-button v-if="activeTab === 'my' && ['ALLOCATED', 'IN_USE'].includes(row.status)" v-permission="'asset:asset:return'" link type="warning" @click="executeCommand(row, 'return')">退还</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_USE'" v-permission="'asset:asset:maintenance'" link type="warning" @click="executeCommand(row, 'maintenanceStart')">送修</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'MAINTENANCE'" v-permission="'asset:asset:maintenance'" link type="success" @click="executeCommand(row, 'maintenanceComplete')">修复归还</el-button>
            <el-button v-if="activeTab === 'ledger' && row.status === 'IN_STOCK'" v-permission="'asset:asset:delete'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
    </el-card>

    <el-dialog v-model="saveVisible" :title="editingId ? '编辑资产' : '手动入库'" width="760px">
      <el-form ref="saveRef" :model="form" :rules="saveRules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="资产名称" prop="name"><el-input v-model="form.name" maxlength="200" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="品类编码" prop="categoryCode"><el-input v-model="form.categoryCode" maxlength="64" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="品牌"><el-input v-model="form.brand" maxlength="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="型号"><el-input v-model="form.model" maxlength="100" /></el-form-item></el-col>
          <el-col v-if="!editingId" :span="8"><el-form-item label="位置"><el-input v-model="form.locationCode" maxlength="100" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="规格"><el-input v-model="form.specification" maxlength="500" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商"><el-select v-model="form.supplierId" filterable remote clearable :remote-method="searchSupplierOptions" :loading="supplierOptionsLoading" placeholder="按编号或名称搜索" style="width: 100%" @change="applySupplier"><el-option v-for="option in supplierOptions" :key="option.id" :value="option.id" :label="`${option.supplierNo} · ${option.name}`" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="供应商快照"><el-input v-model="form.supplierNameSnapshot" disabled placeholder="随供应商自动带入" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="采购日期"><el-date-picker v-model="form.purchaseDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="资产原值" prop="purchaseAmount"><el-input v-model="form.purchaseAmount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="币种" prop="currencyCode"><el-input v-model="form.currencyCode" maxlength="3" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="保修到期"><el-date-picker v-model="form.warrantyExpiryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="预计寿命"><el-input-number v-model="form.expectedLifeYears" :min="1" :max="200" /><span class="unit-tip">年</span></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="管理员用户" prop="ownerUserId">
              <el-select
                v-model="form.ownerUserId"
                filterable
                remote
                clearable
                :remote-method="searchUserOptions"
                :loading="userOptionsLoading"
                placeholder="按姓名或账号搜索"
                style="width: 100%"
                @change="applyOwnerUser"
              >
                <el-option v-for="option in userOptions" :key="option.id" :label="userOptionLabel(option)" :value="option.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="管理部门" prop="ownerUnitId"><el-input v-model="form.ownerUnitId" disabled placeholder="随用户自动带入" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3" maxlength="1000" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="saveVisible = false">取消</el-button>
        <el-button
          v-permission="editingId ? 'asset:asset:update' : 'asset:asset:create'"
          type="primary"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="allocateVisible" title="分配资产" width="520px">
      <el-form ref="allocateRef" :model="allocateForm" :rules="allocateRules" label-width="100px">
        <el-form-item label="目标用户" prop="targetUserId">
          <el-select
            v-model="allocateForm.targetUserId"
            filterable
            remote
            clearable
            :remote-method="searchUserOptions"
            :loading="userOptionsLoading"
            placeholder="按姓名或账号搜索"
            style="width: 100%"
            @change="applyAllocateUser"
          >
            <el-option v-for="option in userOptions" :key="option.id" :label="userOptionLabel(option)" :value="option.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标部门" prop="targetUnitId"><el-input v-model="allocateForm.targetUnitId" disabled placeholder="随用户自动带入" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="allocateForm.remark" type="textarea" :rows="3" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="allocateVisible = false">取消</el-button>
        <el-button v-permission="'asset:asset:allocate'" type="primary" @click="submitAllocate">
          确认分配
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="资产详情" size="72%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="资产编号">{{ detail.assetNo }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="statusInfo(detail.status).type">{{ statusInfo(detail.status).label }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="品类">{{ detail.categoryCode }}</el-descriptions-item>
          <el-descriptions-item label="名称" :span="2">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="品牌/型号">{{ detail.brand || '—' }} / {{ detail.model || '—' }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ detail.locationCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前用户">{{ detail.currentUserId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="当前部门">{{ detail.currentUnitId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="原值">{{ detail.purchaseAmount ? `${detail.purchaseAmount} ${detail.currencyCode}` : '—' }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ detail.supplierNameSnapshot || detail.supplierId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="采购来源">{{ detail.sourcePoNo || '手动入库' }} / {{ detail.sourceGrNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '—' }}</el-descriptions-item>
        </el-descriptions>
        <h3>变更历史</h3>
        <el-table :data="history" border>
          <el-table-column prop="changedTime" label="时间" min-width="170" />
          <el-table-column prop="fromStatus" label="原状态" min-width="120"><template #default="{ row }">{{ row.fromStatus || '新建' }}</template></el-table-column>
          <el-table-column prop="toStatus" label="新状态" min-width="120" />
          <el-table-column prop="changedByUserId" label="操作人" min-width="100" />
          <el-table-column prop="remark" label="说明" min-width="240"><template #default="{ row }">{{ row.remark || '—' }}</template></el-table-column>
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
