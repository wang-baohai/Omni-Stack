<script setup lang="ts">
/** 采购询价页面，覆盖草稿维护、供应商邀请、发送及状态跟踪。 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelProcurementRfq,
  createProcurementRfq,
  deleteProcurementRfq,
  getProcurementRfq,
  listProcurementRfqs,
  listProcurementRfqSupplierOptions,
  sendProcurementRfq,
  updateProcurementRfq,
  type ProcurementRfqDetail,
  type ProcurementRfqStatus,
  type ProcurementRfqSummary,
  type ProcurementRfqSupplierOption,
} from '@/api/procurement-rfq'
import {
  listProcurementRequisitions,
  type ProcurementRequisitionSummary,
} from '@/api/procurement-requisition'
import RfqCompareView from '@/components/procurement/RfqCompareView.vue'

const loading = ref(false)
const rows = ref<ProcurementRfqSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementRfqStatus
  deadlineRange: string[]
}>({ keyword: '', status: undefined, deadlineRange: [] })

const statusOptions: Array<{
  value: ProcurementRfqStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SENT', label: '报价中', type: 'primary' },
  { value: 'CLOSED', label: '已截止', type: 'warning' },
  { value: 'AWARDED', label: '已定标', type: 'success' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  ProcurementRfqStatus,
  (typeof statusOptions)[number]
>

function statusInfo(status: ProcurementRfqStatus) {
  return statusMap[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementRfqs({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      deadlineFrom: query.deadlineRange[0],
      deadlineTo: query.deadlineRange[1],
      page: currentPage.value,
      size: pageSize.value,
    })
    rows.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  currentPage.value = 1
  loadRows()
}

function resetQuery() {
  Object.assign(query, { keyword: '', status: undefined, deadlineRange: [] })
  search()
}

const requisitionLoading = ref(false)
const requisitionOptions = ref<ProcurementRequisitionSummary[]>([])

async function loadRequisitionOptions(keyword?: string) {
  requisitionLoading.value = true
  try {
    const response = await listProcurementRequisitions({
      keyword: keyword?.trim() || undefined,
      status: 'APPROVED',
      page: 1,
      size: 100,
    })
    requisitionOptions.value = response.data.data.records
  } finally {
    requisitionLoading.value = false
  }
}

function selectedRequisition() {
  return requisitionOptions.value.find((item) => item.id === form.requisitionId)
}

const supplierLoading = ref(false)
const supplierOptions = ref<ProcurementRfqSupplierOption[]>([])

async function loadSupplierOptions(keyword?: string) {
  supplierLoading.value = true
  try {
    const response = await listProcurementRfqSupplierOptions({
      keyword: keyword?.trim() || undefined,
      categoryCode: selectedRequisition()?.primaryCategoryCode,
      limit: 100,
    })
    const existing = new Map(supplierOptions.value.map((item) => [item.id, item]))
    for (const item of response.data.data) existing.set(item.id, item)
    supplierOptions.value = [...existing.values()]
  } finally {
    supplierLoading.value = false
  }
}

const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<ProcurementRfqDetail>()
const form = reactive<{
  requisitionId?: number
  title: string
  quotationDeadline: string
  supplierIds: number[]
  version?: number
}>({ title: '', quotationDeadline: '', supplierIds: [] })
const rules: FormRules = {
  requisitionId: [{ required: true, message: '请选择已审批请购单', trigger: 'change' }],
  title: [
    { required: true, message: '请输入询价标题', trigger: 'blur' },
    { max: 200, message: '询价标题不能超过 200 个字符', trigger: 'blur' },
  ],
  quotationDeadline: [{ required: true, message: '请选择报价截止时间', trigger: 'change' }],
  supplierIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一个供应商' }],
}

async function openCreate() {
  editing.value = undefined
  supplierOptions.value = []
  Object.assign(form, {
    requisitionId: undefined,
    title: '',
    quotationDeadline: '',
    supplierIds: [],
    version: undefined,
  })
  dialogVisible.value = true
  await loadRequisitionOptions()
}

async function onRequisitionChanged() {
  const requisition = selectedRequisition()
  form.supplierIds = []
  supplierOptions.value = []
  if (requisition && !form.title.trim()) form.title = `${requisition.title}询价`
  if (requisition) await loadSupplierOptions()
}

function preserveSupplierSnapshots(detail: ProcurementRfqDetail) {
  const existing = new Map(supplierOptions.value.map((item) => [item.id, item]))
  for (const supplier of detail.suppliers) {
    if (!existing.has(supplier.supplierId)) {
      existing.set(supplier.supplierId, {
        id: supplier.supplierId,
        supplierNo: `#${supplier.supplierId}`,
        name: supplier.supplierName,
        levelCode: null,
        categoryCode: null,
      })
    }
  }
  supplierOptions.value = [...existing.values()]
}

async function openEdit(row: ProcurementRfqSummary) {
  const response = await getProcurementRfq(row.id)
  const detail = response.data.data
  editing.value = detail
  await loadRequisitionOptions()
  if (!requisitionOptions.value.some((item) => item.id === detail.requisitionId)) {
    requisitionOptions.value.push({
      id: detail.requisitionId,
      requisitionNo: `#${detail.requisitionId}`,
      title: detail.title,
      requesterUserId: 0,
      requesterUnitId: 0,
      primaryCategoryCode: detail.lines[0]?.categoryCode || '',
      totalAmount: '0',
      currencyCode: detail.currencyCode,
      status: 'APPROVED',
      workflowStartStatus: 'STARTED',
      approvalAttempt: 0,
      version: 0,
      createTime: '',
      updateTime: '',
    })
  }
  Object.assign(form, {
    requisitionId: detail.requisitionId,
    title: detail.title,
    quotationDeadline: detail.quotationDeadline,
    supplierIds: detail.suppliers.map((supplier) => supplier.supplierId),
    version: detail.version,
  })
  supplierOptions.value = []
  await loadSupplierOptions()
  preserveSupplierSnapshots(detail)
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.requisitionId) return
  const request = {
    title: form.title.trim(),
    quotationDeadline: form.quotationDeadline,
    supplierIds: [...new Set(form.supplierIds)],
  }
  if (editing.value) {
    await updateProcurementRfq(editing.value.id, {
      ...request,
      version: form.version ?? editing.value.version,
    })
  } else {
    await createProcurementRfq({ ...request, requisitionId: form.requisitionId })
  }
  ElMessage.success('询价单保存成功')
  dialogVisible.value = false
  await loadRows()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementRfqDetail>()

async function openDetail(row: ProcurementRfqSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getProcurementRfq(row.id)
    detail.value = response.data.data
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(`确认删除询价单“${row.rfqNo}”？`, '删除确认', {
      type: 'warning',
    })
    await deleteProcurementRfq(row.id, row.version)
    ElMessage.success('询价单已删除')
    await loadRows()
  } catch {
    // 用户取消或请求失败时保留当前列表。
  }
}

async function send(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(
      `发送后将锁定询价快照并通知供应商，确认发送“${row.rfqNo}”？`,
      '发送询价',
      { type: 'warning' },
    )
    await sendProcurementRfq(row.id, row.version)
    ElMessage.success('询价单已发送')
  } finally {
    await loadRows()
  }
}

const comparisonVisible = ref(false)
const comparisonRfq = ref<ProcurementRfqSummary>()

function openComparison(row: ProcurementRfqSummary) {
  comparisonRfq.value = row
  comparisonVisible.value = true
}

async function cancel(row: ProcurementRfqSummary) {
  try {
    await ElMessageBox.confirm(`确认取消询价单“${row.rfqNo}”？`, '取消确认', {
      type: 'warning',
    })
    await cancelProcurementRfq(row.id, row.version)
    ElMessage.success('询价单已取消')
  } finally {
    await loadRows()
  }
}

onMounted(loadRows)
</script>

<template>
  <div class="rfq-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="询价单从已审批请购生成；发送时会再次校验供应商资格并锁定物料与邀请快照。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>询价管理</span>
          <el-button v-permission="'procurement:rfq:create'" type="primary" @click="openCreate">
            新建询价
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="询价单号或标题"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 130px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="query.deadlineRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="rfqNo" label="询价单号" min-width="180" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="quotationDeadline" label="报价截止时间" min-width="175" />
        <el-table-column prop="currencyCode" label="币种" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="175" />
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:send'"
              link
              type="success"
              @click="send(row)"
            >
              发送
            </el-button>
            <el-button
              v-if="row.status === 'SENT'"
              v-permission="'procurement:rfq:award'"
              link
              type="success"
              @click="openComparison(row)"
            >
              比价定标
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:rfq:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'SENT'"
              v-permission="'procurement:rfq:cancel'"
              link
              type="danger"
              @click="cancel(row)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        @current-change="loadRows"
        @size-change="search"
      />
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑询价单' : '新建询价单'"
      width="720px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="来源请购" prop="requisitionId">
          <el-select
            v-model="form.requisitionId"
            :disabled="Boolean(editing)"
            filterable
            remote
            :remote-method="loadRequisitionOptions"
            :loading="requisitionLoading"
            placeholder="选择已审批请购单"
            style="width: 100%"
            @change="onRequisitionChanged"
          >
            <el-option
              v-for="item in requisitionOptions"
              :key="item.id"
              :label="`${item.requisitionNo} · ${item.title}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="询价标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="报价截止" prop="quotationDeadline">
          <el-date-picker
            v-model="form.quotationDeadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择未来时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="受邀供应商" prop="supplierIds">
          <el-select
            v-model="form.supplierIds"
            multiple
            filterable
            remote
            :remote-method="loadSupplierOptions"
            :loading="supplierLoading"
            :disabled="!form.requisitionId"
            placeholder="选择合格供应商"
            style="width: 100%"
          >
            <el-option
              v-for="item in supplierOptions"
              :key="item.id"
              :label="`${item.supplierNo} · ${item.name}`"
              :value="item.id"
            >
              <span>{{ item.supplierNo }} · {{ item.name }}</span>
              <span class="option-extra">{{ item.categoryCode || '未分类' }}</span>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存草稿</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="询价详情" size="72%">
      <div v-loading="detailLoading">
        <el-descriptions v-if="detail" :column="3" border>
          <el-descriptions-item label="询价单号">{{ detail.rfqNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusInfo(detail.status).type">
              {{ statusInfo(detail.status).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="币种">{{ detail.currencyCode }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="请购 ID">{{ detail.requisitionId }}</el-descriptions-item>
          <el-descriptions-item label="报价截止">{{ detail.quotationDeadline }}</el-descriptions-item>
          <el-descriptions-item label="发送时间">{{ detail.sentTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.awardedQuotationId" label="中标报价">
            #{{ detail.awardedQuotationId }} / v{{ detail.awardedQuotationVersion }}
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.awardedTime" label="定标时间">
            {{ detail.awardedTime }}
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="detail">
          <h3>询价明细</h3>
          <el-table :data="detail.lines" border>
            <el-table-column prop="lineNo" label="行号" width="70" />
            <el-table-column prop="materialCode" label="物料编码" min-width="140" />
            <el-table-column prop="materialName" label="物料名称" min-width="180" />
            <el-table-column prop="categoryCode" label="品类" min-width="120" />
            <el-table-column prop="quantity" label="数量" min-width="120" align="right" />
            <el-table-column prop="unit" label="单位" width="90" />
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          </el-table>

          <h3>供应商邀请</h3>
          <el-table :data="detail.suppliers" border>
            <el-table-column prop="supplierName" label="供应商" min-width="200" />
            <el-table-column prop="status" label="邀请状态" width="110" />
            <el-table-column prop="invitedTime" label="邀请时间" min-width="175">
              <template #default="{ row }">{{ row.invitedTime || '—' }}</template>
            </el-table-column>
            <el-table-column prop="quotationId" label="报价 ID" min-width="110">
              <template #default="{ row }">{{ row.quotationId || '—' }}</template>
            </el-table-column>
            <el-table-column prop="quotationVersion" label="报价版本" width="100">
              <template #default="{ row }">{{ row.quotationVersion ?? '—' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <RfqCompareView v-model="comparisonVisible" :rfq-row="comparisonRfq" @awarded="loadRows" />
  </div>
</template>

<style scoped>
.rfq-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.option-extra {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

h3 {
  margin: 24px 0 12px;
}
</style>
