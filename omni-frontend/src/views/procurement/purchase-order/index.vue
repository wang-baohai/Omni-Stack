<script setup lang="ts">
/** 采购订单页面，跟踪询价定标后生成订单的发送、确认和收货进度。 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelProcurementPurchaseOrder,
  confirmProcurementPurchaseOrder,
  deleteProcurementPurchaseOrder,
  getProcurementPurchaseOrder,
  listProcurementPurchaseOrders,
  sendProcurementPurchaseOrder,
  updateProcurementPurchaseOrder,
  type ProcurementPurchaseOrderDetail,
  type ProcurementPurchaseOrderStatus,
  type ProcurementPurchaseOrderSummary,
} from '@/api/procurement-purchase-order'
import PurchaseOrderTracker from '@/components/procurement/PurchaseOrderTracker.vue'

const loading = ref(false)
const rows = ref<ProcurementPurchaseOrderSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementPurchaseOrderStatus
  expectedDeliveryRange: string[]
}>({ keyword: '', status: undefined, expectedDeliveryRange: [] })

const statusOptions: Array<{
  value: ProcurementPurchaseOrderStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SENT', label: '已发送', type: 'primary' },
  { value: 'CONFIRMED', label: '已确认', type: 'success' },
  { value: 'PARTIAL_RECEIVED', label: '部分收货', type: 'warning' },
  { value: 'RECEIVED', label: '已收齐', type: 'success' },
  { value: 'CLOSED', label: '已关闭', type: 'info' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  ProcurementPurchaseOrderStatus,
  (typeof statusOptions)[number]
>

function statusInfo(status: ProcurementPurchaseOrderStatus) {
  return statusMap[status]
}

async function loadRows() {
  loading.value = true
  try {
    const response = await listProcurementPurchaseOrders({
      keyword: query.keyword.trim() || undefined,
      status: query.status,
      expectedDeliveryFrom: query.expectedDeliveryRange[0],
      expectedDeliveryTo: query.expectedDeliveryRange[1],
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
  Object.assign(query, { keyword: '', status: undefined, expectedDeliveryRange: [] })
  search()
}

const editVisible = ref(false)
const formRef = ref<FormInstance>()
const editing = ref<ProcurementPurchaseOrderDetail>()
const form = reactive({
  title: '',
  expectedDeliveryDate: '',
  deliveryAddress: '',
  contactName: '',
  contactPhone: '',
  version: 0,
})
const rules: FormRules = {
  title: [
    { required: true, message: '请输入订单标题', trigger: 'blur' },
    { max: 200, message: '订单标题不能超过 200 个字符', trigger: 'blur' },
  ],
  deliveryAddress: [
    { required: true, message: '请输入收货地址', trigger: 'blur' },
    { max: 500, message: '收货地址不能超过 500 个字符', trigger: 'blur' },
  ],
  contactName: [
    { required: true, message: '请输入联系人', trigger: 'blur' },
    { max: 100, message: '联系人不能超过 100 个字符', trigger: 'blur' },
  ],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { max: 50, message: '联系电话不能超过 50 个字符', trigger: 'blur' },
  ],
}

async function openEdit(row: ProcurementPurchaseOrderSummary) {
  const response = await getProcurementPurchaseOrder(row.id)
  editing.value = response.data.data
  Object.assign(form, {
    title: response.data.data.title,
    expectedDeliveryDate: response.data.data.expectedDeliveryDate || '',
    deliveryAddress: response.data.data.deliveryAddress,
    contactName: response.data.data.contactName,
    contactPhone: response.data.data.contactPhone,
    version: response.data.data.version,
  })
  editVisible.value = true
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !editing.value) return
  await updateProcurementPurchaseOrder(editing.value.id, {
    version: form.version,
    title: form.title.trim(),
    expectedDeliveryDate: form.expectedDeliveryDate || undefined,
    deliveryAddress: form.deliveryAddress.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
  })
  ElMessage.success('采购订单已更新')
  editVisible.value = false
  await loadRows()
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProcurementPurchaseOrderDetail>()

async function openDetail(row: ProcurementPurchaseOrderSummary) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const response = await getProcurementPurchaseOrder(row.id)
    detail.value = response.data.data
  } finally {
    detailLoading.value = false
  }
}

async function remove(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(`确认删除采购订单“${row.poNo}”？`, '删除确认', {
      type: 'warning',
    })
    await deleteProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success('采购订单已删除')
    await loadRows()
  } catch {
    // 用户取消或请求失败时保留当前列表。
  }
}

async function send(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(`确认发送采购订单“${row.poNo}”？`, '发送订单', {
      type: 'warning',
    })
    await sendProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success('采购订单已发送')
  } finally {
    await loadRows()
  }
}

async function confirm(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(`确认供应商已接受订单“${row.poNo}”？`, '确认订单')
    await confirmProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success('采购订单已确认')
  } finally {
    await loadRows()
  }
}

async function cancel(row: ProcurementPurchaseOrderSummary) {
  try {
    await ElMessageBox.confirm(`确认取消采购订单“${row.poNo}”？`, '取消确认', {
      type: 'warning',
    })
    await cancelProcurementPurchaseOrder(row.id, row.version)
    ElMessage.success('采购订单已取消')
  } finally {
    await loadRows()
  }
}

onMounted(loadRows)
</script>

<template>
  <div class="purchase-order-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="采购订单只由询价定标生成，中标报价及金额快照不可由客户端修改。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>采购订单</span>
          <span class="header-tip">请在询价比价页完成定标以生成订单</span>
        </div>
      </template>

      <el-form :inline="true" :model="query">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="订单号、标题或供应商"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 150px">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预计交付">
          <el-date-picker
            v-model="query.expectedDeliveryRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="poNo" label="订单号" min-width="180" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column
          prop="supplierNameSnapshot"
          label="供应商"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="订单金额" min-width="150" align="right">
          <template #default="{ row }">{{ row.totalAmount }} {{ row.currencyCode }}</template>
        </el-table-column>
        <el-table-column prop="expectedDeliveryDate" label="预计交付" min-width="120">
          <template #default="{ row }">{{ row.expectedDeliveryDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type">{{ statusInfo(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPhoneMasked" label="联系电话" min-width="130">
          <template #default="{ row }">{{ row.contactPhoneMasked || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="320" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:update'"
              link
              type="primary"
              @click="openEdit(row)"
            >
              编辑交付信息
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:send'"
              link
              type="success"
              @click="send(row)"
            >
              发送
            </el-button>
            <el-button
              v-if="row.status === 'SENT'"
              v-permission="'procurement:purchase-order:confirm'"
              link
              type="success"
              @click="confirm(row)"
            >
              确认
            </el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              v-permission="'procurement:purchase-order:delete'"
              link
              type="danger"
              @click="remove(row)"
            >
              删除
            </el-button>
            <el-button
              v-if="['DRAFT', 'SENT', 'CONFIRMED'].includes(row.status)"
              v-permission="'procurement:purchase-order:cancel'"
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

    <el-dialog v-model="editVisible" title="编辑订单交付信息" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="订单标题" prop="title">
          <el-input v-model="form.title" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="预计交付">
          <el-date-picker
            v-model="form.expectedDeliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="可留空"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="收货地址" prop="deliveryAddress">
          <el-input v-model="form.deliveryAddress" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="联系人" prop="contactName">
          <el-input v-model="form.contactName" maxlength="100" />
        </el-form-item>
        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" maxlength="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="采购订单详情" size="76%">
      <div v-loading="detailLoading">
        <PurchaseOrderTracker :order-detail="detail" />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.purchase-order-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

</style>
