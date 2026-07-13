<script setup lang="ts">
/** CRM 客户管理页面，包含客户 CRUD、状态、转移、黑名单和 Customer 360。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  blacklistCustomer,
  checkCustomerDuplicates,
  changeCustomerStatus,
  createCustomer,
  deleteCustomer,
  getCustomer,
  listCustomers,
  restoreCustomerFromBlacklist,
  transferCustomer,
  updateCustomer,
  type CrmCustomer,
  type CustomerStatus,
  type DuplicateCustomerCandidate,
  type SaveCustomerRequest,
} from '@/api/crm-customer'
import OwnerSelector from '@/components/crm/OwnerSelector.vue'
import CustomerOverview from '@/components/crm/CustomerOverview.vue'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canViewPii = computed(() => permissionStore.hasPermission('crm:pii:view'))
const loading = ref(false)
const tableData = ref<CrmCustomer[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; status?: CustomerStatus; ownerUserId?: number }>({ keyword: '' })
const statuses: Array<{ label: string; value: CustomerStatus }> = [
  { label: '潜在客户', value: 'POTENTIAL' }, { label: '活跃客户', value: 'ACTIVE' },
  { label: '沉睡客户', value: 'DORMANT' }, { label: '已流失', value: 'LOST' },
  { label: '黑名单', value: 'BLACKLISTED' },
]
const statusLabel = Object.fromEntries(statuses.map((item) => [item.value, item.label])) as Record<CustomerStatus, string>

const formDialogVisible = ref(false)
const editingCustomer = ref<CrmCustomer | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SaveCustomerRequest>({ name: '', customerType: '', industryCode: '', levelCode: '', sourceCode: '', creditCode: '', website: '', phone: '', email: '', region: '', address: '' })
const rules: FormRules<SaveCustomerRequest> = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}
const duplicateCandidates = ref<DuplicateCustomerCandidate[]>([])

const overviewVisible = ref(false)
const overviewCustomerId = ref<number>()
const transferVisible = ref(false)
const transferTarget = ref<CrmCustomer | null>(null)
const transferForm = reactive({ ownerUserId: undefined as number | undefined, reason: '', cascadeOpenOpportunities: false })
const statusVisible = ref(false)
const statusTarget = ref<CrmCustomer | null>(null)
const statusForm = reactive<{ status: CustomerStatus; reason: string }>({ status: 'ACTIVE', reason: '' })

async function loadData() {
  loading.value = true
  try {
    const response = await listCustomers({
      keyword: query.keyword || undefined, status: query.status, ownerUserId: query.ownerUserId,
      page: currentPage.value, size: pageSize.value,
    })
    tableData.value = response.data.data.records
    total.value = response.data.data.total
  } finally { loading.value = false }
}

function search() { currentPage.value = 1; loadData() }
function resetQuery() { Object.assign(query, { keyword: '', status: undefined, ownerUserId: undefined }); search() }
function resetForm() { Object.assign(form, { name: '', customerType: '', industryCode: '', levelCode: '', sourceCode: '', creditCode: '', website: '', phone: '', email: '', region: '', address: '', version: undefined }); duplicateCandidates.value = [] }
function openCreate() { editingCustomer.value = null; resetForm(); formDialogVisible.value = true }
async function openEdit(row: CrmCustomer) {
  const response = await getCustomer(row.id)
  const detail = response.data.data
  editingCustomer.value = detail
  Object.assign(form, { name: detail.name, customerType: detail.customerType || '', industryCode: detail.industryCode || '', levelCode: detail.levelCode || '', sourceCode: detail.sourceCode || '', creditCode: canViewPii.value ? detail.creditCode || '' : '', website: detail.website || '', phone: canViewPii.value ? detail.phone || '' : '', email: canViewPii.value ? detail.email || '' : '', region: detail.region || '', address: canViewPii.value ? detail.address || '' : '', version: detail.version })
  duplicateCandidates.value = []; formDialogVisible.value = true
}

function buildCustomerPayload(): SaveCustomerRequest {
  const payload: SaveCustomerRequest = {
    name: form.name,
    customerType: form.customerType,
    industryCode: form.industryCode,
    levelCode: form.levelCode,
    sourceCode: form.sourceCode,
    website: form.website,
    region: form.region,
    version: form.version,
  }
  if (canViewPii.value) {
    payload.creditCode = form.creditCode
    payload.phone = form.phone
    payload.email = form.email
    payload.address = form.address
  }
  return payload
}

async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = buildCustomerPayload()
  if (editingCustomer.value) await updateCustomer(editingCustomer.value.id, payload)
  else {
    const response = await checkCustomerDuplicates(payload)
    duplicateCandidates.value = response.data.data
    if (duplicateCandidates.value.length) await ElMessageBox.confirm(`发现 ${duplicateCandidates.value.length} 个可能重复客户，仍要创建吗？`, '重复候选提示', { type: 'warning' })
    await createCustomer(payload)
  }
  ElMessage.success(t('common.success')); formDialogVisible.value = false; loadData()
}

async function handleDelete(row: CrmCustomer) {
  try { await ElMessageBox.confirm(`确认删除客户“${row.name}”？存在开放商机时后端将拒绝删除。`, '删除确认', { type: 'warning' }); await deleteCustomer(row.id, row.version); ElMessage.success(t('common.success')); loadData() } catch { /* 用户取消 */ }
}

function openOverview(row: CrmCustomer) { overviewCustomerId.value = row.id; overviewVisible.value = true }
function openTransfer(row: CrmCustomer) { transferTarget.value = row; Object.assign(transferForm, { ownerUserId: row.ownerUserId, reason: '', cascadeOpenOpportunities: false }); transferVisible.value = true }
async function saveTransfer() {
  if (!transferTarget.value || !transferForm.ownerUserId) return ElMessage.warning('请选择新负责人')
  await transferCustomer(transferTarget.value.id, { ...transferForm, ownerUserId: transferForm.ownerUserId, reason: transferForm.reason || undefined, version: transferTarget.value.version })
  ElMessage.success('客户已转移'); transferVisible.value = false; loadData()
}
function openStatus(row: CrmCustomer) { statusTarget.value = row; Object.assign(statusForm, { status: row.status === 'POTENTIAL' ? 'ACTIVE' : row.status, reason: '' }); statusVisible.value = true }
async function saveStatus() {
  if (!statusTarget.value) return
  await changeCustomerStatus(statusTarget.value.id, { status: statusForm.status, version: statusTarget.value.version })
  ElMessage.success('客户状态已更新'); statusVisible.value = false; loadData()
}
async function handleBlacklist(row: CrmCustomer) {
  await ElMessageBox.confirm(`确认将客户“${row.name}”加入黑名单？`, '加入黑名单', { type: 'warning' })
  await blacklistCustomer(row.id, row.version); ElMessage.success('已加入黑名单'); loadData()
}
async function handleRestore(row: CrmCustomer) {
  await ElMessageBox.confirm(`确认将客户“${row.name}”移出黑名单？`, '移出黑名单')
  await restoreCustomerFromBlacklist(row.id, row.version); ElMessage.success('已移出黑名单'); loadData()
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmCustomers') }}</span><el-button v-permission="'crm:customer:create'" type="primary" @click="openCreate">新建客户</el-button></div></template>
      <el-form inline class="search-form"><el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="名称/编号/电话" @keyup.enter="search" /></el-form-item><el-form-item label="状态"><el-select v-model="query.status" clearable style="width:130px"><el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="负责人"><OwnerSelector v-model="query.ownerUserId" /></el-form-item><el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="resetQuery">重置</el-button></el-form-item></el-form>
      <el-table v-loading="loading" :data="tableData" border stripe><el-table-column prop="customerNo" label="客户编号" width="145" /><el-table-column prop="name" label="客户名称" min-width="180"><template #default="{ row }"><el-link type="primary" @click="openOverview(row)">{{ row.name }}</el-link></template></el-table-column><el-table-column prop="industryCode" label="行业" width="110" /><el-table-column prop="levelCode" label="级别" width="90" /><el-table-column prop="phone" label="电话" width="135" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'BLACKLISTED' ? 'danger' : row.status === 'ACTIVE' ? 'success' : 'info'">{{ statusLabel[row.status as CustomerStatus] }}</el-tag></template></el-table-column><el-table-column label="负责人" width="110"><template #default="{ row }">{{ row.ownerName || `用户 #${row.ownerUserId}` }}</template></el-table-column><el-table-column prop="nextFollowupTime" label="下次跟进" width="170" /><el-table-column label="操作" fixed="right" width="310"><template #default="{ row }"><el-button size="small" @click="openOverview(row)">360 视图</el-button><el-button v-permission="'crm:customer:update'" size="small" @click="openEdit(row)">编辑</el-button><el-dropdown trigger="click"><el-button size="small">更多<el-icon><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-permission="'crm:customer:transfer'" @click="openTransfer(row)">转移负责人</el-dropdown-item><el-dropdown-item v-if="row.status !== 'BLACKLISTED'" v-permission="'crm:customer:status'" @click="openStatus(row)">变更状态</el-dropdown-item><el-dropdown-item v-if="row.status !== 'BLACKLISTED'" v-permission="'crm:customer:blacklist'" @click="handleBlacklist(row)">加入黑名单</el-dropdown-item><el-dropdown-item v-else v-permission="'crm:customer:blacklist'" @click="handleRestore(row)">移出黑名单</el-dropdown-item><el-dropdown-item v-permission="'crm:customer:delete'" divided @click="handleDelete(row)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column></el-table>
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingCustomer ? '编辑客户' : '新建客户'" width="760px"><el-form ref="formRef" :model="form" :rules="rules" label-width="90px"><el-row :gutter="16"><el-col :span="12"><el-form-item label="客户名称" prop="name"><el-input v-model="form.name" /></el-form-item></el-col><el-col :span="12"><el-form-item label="客户类型"><el-input v-model="form.customerType" /></el-form-item></el-col><el-col :span="12"><el-form-item label="行业"><el-input v-model="form.industryCode" /></el-form-item></el-col><el-col :span="12"><el-form-item label="级别"><el-select v-model="form.levelCode" clearable style="width:100%"><el-option v-for="level in ['A','B','C','D']" :key="level" :label="level" :value="level" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="来源"><el-input v-model="form.sourceCode" /></el-form-item></el-col><el-col :span="12"><el-form-item label="信用代码"><el-input v-model="form.creditCode" :disabled="!canViewPii" /></el-form-item></el-col><el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" :disabled="!canViewPii" /></el-form-item></el-col><el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" :disabled="!canViewPii" /></el-form-item></el-col><el-col :span="12"><el-form-item label="网址"><el-input v-model="form.website" /></el-form-item></el-col><el-col :span="12"><el-form-item label="地区"><el-input v-model="form.region" /></el-form-item></el-col><el-col :span="24"><el-form-item label="地址"><el-input v-model="form.address" :disabled="!canViewPii" /></el-form-item></el-col></el-row></el-form><el-alert v-if="duplicateCandidates.length" :title="`发现 ${duplicateCandidates.length} 个重复候选`" type="warning" :closable="false" /><template #footer><el-button @click="formDialogVisible = false">取消</el-button><el-button v-permission="editingCustomer ? 'crm:customer:update' : 'crm:customer:create'" type="primary" @click="saveForm">保存</el-button></template></el-dialog>

    <el-drawer v-model="overviewVisible" title="Customer 360" size="760px"><CustomerOverview :customer-id="overviewCustomerId" /></el-drawer>
    <el-dialog v-model="transferVisible" title="转移客户" width="520px"><el-form :model="transferForm" label-width="140px"><el-form-item label="新负责人"><OwnerSelector v-model="transferForm.ownerUserId" /></el-form-item><el-form-item label="转移开放商机"><el-switch v-model="transferForm.cascadeOpenOpportunities" /><span class="hint">默认不级联商机</span></el-form-item><el-form-item label="原因"><el-input v-model="transferForm.reason" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="transferVisible = false">取消</el-button><el-button v-permission="'crm:customer:transfer'" type="primary" @click="saveTransfer">确认转移</el-button></template></el-dialog>
    <el-dialog v-model="statusVisible" title="变更客户状态" width="480px"><el-form :model="statusForm" label-width="90px"><el-form-item label="目标状态"><el-select v-model="statusForm.status" style="width:100%"><el-option v-for="item in statuses.filter((item) => item.value !== 'BLACKLISTED')" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item></el-form><template #footer><el-button @click="statusVisible = false">取消</el-button><el-button v-permission="'crm:customer:status'" type="primary" @click="saveStatus">确认</el-button></template></el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.hint { margin-left: 10px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
