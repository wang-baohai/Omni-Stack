<script setup lang="ts">
/** 资产调拨页面，审批在 Workflow 完成，业务交接在本页显式完成。 */
import { computed, onMounted, reactive, ref } from 'vue'
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
const canList = computed(() => permissionStore.hasPermission('asset:transfer:list'))
const loading = ref(false)
const rows = ref<AssetTransfer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; status?: AssetOperationStatus }>({ keyword: '', status: undefined })
const statusOptions: Array<{ value: AssetOperationStatus; label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = [
  { value: 'PENDING_APPROVAL', label: '待审批', type: 'warning' },
  { value: 'START_FAILED', label: '启动失败', type: 'danger' },
  { value: 'APPROVED', label: '已批准', type: 'success' },
  { value: 'REJECTED', label: '已拒绝', type: 'danger' },
  { value: 'COMPLETED', label: '已完成', type: 'success' },
  { value: 'CANCELLED', label: '已取消', type: 'info' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<AssetOperationStatus, (typeof statusOptions)[number]>

function statusInfo(status: AssetOperationStatus) {
  return statusMap[status]
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
const rules: FormRules = {
  assetId: [{ required: true, message: '请选择资产', trigger: 'change' }],
  toUserId: [{ required: true, message: '请选择目标用户', trigger: 'change' }],
  toUnitId: [{ required: true, message: '目标用户缺少主部门，无法调拨', trigger: 'change' }],
  reason: [{ required: true, message: '请输入调拨原因', trigger: 'blur' }],
}

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
  ElMessage.success('调拨申请已创建并提交审批')
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
  const labels = { complete: '完成调拨交接', cancel: '取消调拨', retry: '重试启动审批' }
  await ElMessageBox.confirm(`确认${labels[action]}“${row.transferNo}”？`, labels[action], { type: 'warning' })
  if (action === 'complete') await completeAssetTransfer(row.id, row.version)
  else if (action === 'cancel') await cancelAssetTransfer(row.id, row.version)
  else await retryAssetTransfer(row.id, row.version)
  ElMessage.success(`${labels[action]}成功`)
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
    <el-alert type="info" :closable="false" show-icon title="审批动作在 Workflow 待办中执行；审批通过后须在此确认实物交接。" />
    <el-card shadow="never">
      <template #header><div class="card-header"><span>资产调拨</span><el-button v-permission="'asset:transfer:create'" type="primary" @click="openCreate">发起调拨</el-button></div></template>
      <el-form v-if="canList" :inline="true" :model="query">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="调拨单号或资产" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 150px"><el-option v-for="option in statusOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-if="canList" v-loading="loading" :data="rows" stripe>
        <el-table-column prop="transferNo" label="调拨单号" min-width="180" />
        <el-table-column prop="assetNo" label="资产编号" min-width="160" />
        <el-table-column prop="assetName" label="资产名称" min-width="180" />
        <el-table-column prop="fromUnitId" label="原部门" min-width="100"><template #default="{ row }">{{ row.fromUnitId || '—' }}</template></el-table-column>
        <el-table-column prop="toUnitId" label="目标部门" min-width="100" />
        <el-table-column prop="toUserId" label="目标用户" min-width="100" />
        <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'APPROVED'" v-permission="'asset:transfer:complete'" link type="success" @click="execute(row, 'complete')">完成交接</el-button>
            <el-button v-if="canRetry(row)" v-permission="'asset:transfer:retry'" link type="warning" @click="execute(row, 'retry')">重试</el-button>
            <el-button v-if="canCancel(row)" v-permission="'asset:transfer:cancel'" link type="danger" @click="execute(row, 'cancel')">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="canList" v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
      <el-empty v-else description="当前账号无调拨列表权限" />
    </el-card>

    <el-dialog v-model="createVisible" title="发起资产调拨" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item label="资产" prop="assetId">
          <el-select v-model="form.assetId" filterable remote :remote-method="loadAssetOptions" :loading="assetOptionsLoading" placeholder="按资产编号或名称搜索" style="width: 100%">
            <el-option v-for="asset in assetOptions" :key="asset.id" :value="asset.id" :label="`${asset.assetNo} · ${asset.name}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标用户" prop="toUserId">
          <el-select v-model="form.toUserId" filterable remote :remote-method="loadUserOptions" :loading="userOptionsLoading" placeholder="按账号或昵称搜索" style="width: 100%" @change="selectTargetUser">
            <el-option v-for="user in userOptions" :key="user.id" :value="user.id" :label="`${user.nickname || user.username}（${user.username}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标部门" prop="toUnitId"><el-input-number v-model="form.toUnitId" disabled controls-position="right" /></el-form-item>
        <el-form-item label="目标位置"><el-input v-model="form.toLocation" maxlength="100" /></el-form-item>
        <el-form-item label="调拨原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button v-permission="'asset:transfer:create'" type="primary" @click="submitCreate">
          提交审批
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="调拨详情" size="58%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="调拨单号">{{ detail.transferNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusMap[detail.status].label }}</el-descriptions-item>
        <el-descriptions-item label="资产">{{ detail.assetNo }} / {{ detail.assetName }}</el-descriptions-item><el-descriptions-item label="原状态">{{ detail.previousAssetStatus }}</el-descriptions-item>
        <el-descriptions-item label="原用户/部门">{{ detail.fromUserId || '—' }} / {{ detail.fromUnitId || '—' }}</el-descriptions-item><el-descriptions-item label="目标用户/部门">{{ detail.toUserId }} / {{ detail.toUnitId }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ detail.fromLocation || '—' }} → {{ detail.toLocation || '—' }}</el-descriptions-item><el-descriptions-item label="流程实例">{{ detail.processInstanceId || detail.workflowStartStatus }}</el-descriptions-item>
        <el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.asset-operation-page { display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
