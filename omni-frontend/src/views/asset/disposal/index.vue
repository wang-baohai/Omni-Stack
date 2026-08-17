<script setup lang="ts">
/** 资产处置页面，丢弃和报废共用审批与实物处置闭环。 */
import { computed, onMounted, reactive, ref } from 'vue'
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
const canList = computed(() => permissionStore.hasPermission('asset:disposal:list'))
const loading = ref(false)
const rows = ref<AssetDisposal[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; disposalType?: AssetDisposalType; status?: AssetOperationStatus }>({ keyword: '', disposalType: undefined, status: undefined })
const statuses: Array<{ value: AssetOperationStatus; label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = [
  { value: 'PENDING_APPROVAL', label: '待审批', type: 'warning' }, { value: 'START_FAILED', label: '启动失败', type: 'danger' },
  { value: 'APPROVED', label: '已批准', type: 'success' }, { value: 'REJECTED', label: '已拒绝', type: 'danger' },
  { value: 'COMPLETED', label: '已完成', type: 'success' }, { value: 'CANCELLED', label: '已取消', type: 'info' },
]
const statusMap = Object.fromEntries(statuses.map((item) => [item.value, item])) as Record<AssetOperationStatus, (typeof statuses)[number]>

function statusInfo(status: AssetOperationStatus) {
  return statusMap[status]
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
const rules: FormRules = {
  assetId: [{ required: true, message: '请选择资产', trigger: 'change' }], disposalType: [{ required: true, message: '请选择处置类型', trigger: 'change' }],
  reason: [{ required: true, message: '请输入处置原因', trigger: 'blur' }],
  residualValue: [{ validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => { if (!value || decimalPattern.test(value)) callback(); else callback(new Error('残值须为十进制字符串，最多 16 位整数和 2 位小数')) }, trigger: 'blur' }],
}
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
  ElMessage.success('处置申请已创建并提交审批')
  createVisible.value = false
  if (canList.value) await loadRows()
}

const detailVisible = ref(false)
const detail = ref<AssetDisposal>()
async function openDetail(row: AssetDisposal) { detailVisible.value = true; detail.value = (await getAssetDisposal(row.id)).data.data }
async function execute(row: AssetDisposal, action: 'complete' | 'cancel' | 'retry') {
  const labels = { complete: '确认实物处置', cancel: '取消处置申请', retry: '重试启动审批' }
  await ElMessageBox.confirm(`确认${labels[action]}“${row.disposalNo}”？`, labels[action], { type: 'warning' })
  if (action === 'complete') await completeAssetDisposal(row.id, row.version); else if (action === 'cancel') await cancelAssetDisposal(row.id, row.version); else await retryAssetDisposal(row.id, row.version)
  ElMessage.success(`${labels[action]}成功`); await loadRows()
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
    <el-alert type="warning" :closable="false" show-icon title="处置完成后资产进入不可恢复终态；审批通过不等于实物处置完成。" />
    <el-card shadow="never">
      <template #header><div class="card-header"><span>资产处置</span><el-button v-permission="'asset:disposal:create'" type="primary" @click="openCreate">发起处置</el-button></div></template>
      <el-form v-if="canList" :inline="true" :model="query">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="处置单号或资产" @keyup.enter="search" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="query.disposalType" clearable placeholder="全部" style="width: 130px"><el-option label="丢弃" value="DISCARD" /><el-option label="报废" value="SCRAP" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable placeholder="全部" style="width: 150px"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-if="canList" v-loading="loading" :data="rows" stripe>
        <el-table-column prop="disposalNo" label="处置单号" min-width="180" /><el-table-column prop="assetNo" label="资产编号" min-width="160" /><el-table-column prop="assetName" label="资产名称" min-width="180" />
        <el-table-column label="类型" min-width="90"><template #default="{ row }"><el-tag>{{ row.disposalType === 'SCRAP' ? '报废' : '丢弃' }}</el-tag></template></el-table-column>
        <el-table-column prop="residualValue" label="残值" min-width="110" align="right"><template #default="{ row }">{{ row.residualValue || '—' }}</template></el-table-column>
        <el-table-column label="状态" min-width="120"><template #default="{ row }"><el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag></template></el-table-column>
        <el-table-column label="操作" min-width="270" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="row.status === 'APPROVED'" v-permission="'asset:disposal:complete'" link type="success" @click="execute(row, 'complete')">完成处置</el-button>
            <el-button v-if="canRetry(row)" v-permission="'asset:disposal:retry'" link type="warning" @click="execute(row, 'retry')">重试</el-button><el-button v-if="canCancel(row)" v-permission="'asset:disposal:cancel'" link type="danger" @click="execute(row, 'cancel')">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="canList" v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" background layout="total, sizes, prev, pager, next, jumper" :total="total" :page-sizes="[5, 10, 20, 50, 100]" @current-change="loadRows" @size-change="search" />
      <el-empty v-else description="当前账号无处置列表权限" />
    </el-card>
    <el-dialog v-model="createVisible" title="发起资产处置" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="140px">
        <el-form-item label="资产" prop="assetId"><el-select v-model="form.assetId" filterable remote :remote-method="loadAssetOptions" :loading="assetOptionsLoading" placeholder="按资产编号或名称搜索" style="width: 100%"><el-option v-for="asset in assetOptions" :key="asset.id" :value="asset.id" :label="`${asset.assetNo} · ${asset.name}`" /></el-select></el-form-item><el-form-item label="处置类型" prop="disposalType"><el-radio-group v-model="form.disposalType"><el-radio-button value="DISCARD">丢弃</el-radio-button><el-radio-button value="SCRAP">报废</el-radio-button></el-radio-group></el-form-item>
        <el-form-item label="残值" prop="residualValue"><el-input v-model="form.residualValue" placeholder="可留空，使用十进制字符串" /></el-form-item><el-form-item label="处置方式"><el-input v-model="form.disposalMethod" maxlength="500" placeholder="回收、捐赠、销毁等" /></el-form-item>
        <el-form-item label="处置原因" prop="reason"><el-input v-model="form.reason" type="textarea" :rows="4" maxlength="1000" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button v-permission="'asset:disposal:create'" type="primary" @click="submitCreate">
          提交审批
        </el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="detailVisible" title="处置详情" size="58%">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="处置单号">{{ detail.disposalNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusMap[detail.status].label }}</el-descriptions-item><el-descriptions-item label="资产">{{ detail.assetNo }} / {{ detail.assetName }}</el-descriptions-item><el-descriptions-item label="类型">{{ detail.disposalType === 'SCRAP' ? '报废' : '丢弃' }}</el-descriptions-item>
        <el-descriptions-item label="残值">{{ detail.residualValue || '—' }}</el-descriptions-item><el-descriptions-item label="处置方式">{{ detail.disposalMethod || '—' }}</el-descriptions-item><el-descriptions-item label="流程实例">{{ detail.processInstanceId || detail.workflowStartStatus }}</el-descriptions-item><el-descriptions-item label="原资产状态">{{ detail.previousAssetStatus }}</el-descriptions-item><el-descriptions-item label="原因" :span="2">{{ detail.reason }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<style scoped>
.asset-operation-page { display: flex; flex-direction: column; gap: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { justify-content: flex-end; margin-top: 16px; }
</style>
