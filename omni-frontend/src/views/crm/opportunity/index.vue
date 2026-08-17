<script setup lang="ts">
/** CRM 商机页面：表格/Kanban 双视图、CRUD、负责人分配、阶段迁移与重开。 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignOpportunity,
  createOpportunity,
  deleteOpportunity,
  getOpportunityBoard,
  listOpportunityStageHistory,
  listPipelineStages,
  listPipelines,
  listOpportunities,
  moveOpportunityStage,
  reopenOpportunity,
  updateOpportunity,
  type CrmOpportunity,
  type CrmPipeline,
  type OpportunityBoard,
  type OpportunityStageHistory,
  type OpportunityStatus,
  type PipelineStage,
} from '@/api/crm-opportunity'
import { listCustomerContacts, type CrmContact } from '@/api/crm-contact'
import OwnerSelector from '@/components/crm/OwnerSelector.vue'
import CustomerPicker from '@/components/crm/CustomerPicker.vue'
import OpportunityStageBoard from '@/components/crm/OpportunityStageBoard.vue'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const activeView = ref<'table' | 'board'>('table')
const loading = ref(false)
const boardLoading = ref(false)
const tableData = ref<CrmOpportunity[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ keyword: string; status?: OpportunityStatus; stageId?: number; ownerUserId?: number; customerId?: number }>({ keyword: '' })
const pipelines = ref<CrmPipeline[]>([])
const stages = ref<PipelineStage[]>([])
const board = ref<OpportunityBoard | null>(null)
const boardPipelineId = ref<number>()

const formDialogVisible = ref(false)
const editingOpportunity = ref<CrmOpportunity | null>(null)
const formRef = ref<FormInstance>()
interface OpportunityForm {
  name: string
  customerId: number
  primaryContactId?: number
  pipelineId: number
  amount: number
  expectedCloseDate: string
  nextFollowupTime: string
  version?: number
}
const form = reactive<OpportunityForm>({ name: '', customerId: 0, primaryContactId: undefined, pipelineId: 0, amount: 0, expectedCloseDate: '', nextFollowupTime: '' })
const formCustomerId = computed<number | undefined>({ get: () => form.customerId || undefined, set: (value) => { form.customerId = value || 0 } })
const formContacts = ref<CrmContact[]>([])
const formRules: FormRules<OpportunityForm> = {
  name: [{ required: true, message: '请输入商机名称', trigger: 'blur' }],
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  pipelineId: [{ required: true, message: '请选择销售管道', trigger: 'change' }],
}

const stageDialogVisible = ref(false)
const stageTarget = ref<CrmOpportunity | null>(null)
const stageForm = reactive({ stageId: undefined as number | undefined, reason: '', lossReason: '' })
const targetStage = computed(() => stages.value.find((item) => item.id === stageForm.stageId))
const assignDialogVisible = ref(false)
const assignTarget = ref<CrmOpportunity | null>(null)
const assignForm = reactive({ ownerUserId: undefined as number | undefined, reason: '' })
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref<OpportunityStageHistory[]>([])
const historyTarget = ref<CrmOpportunity | null>(null)

async function loadPipelines() {
  const response = await listPipelines()
  pipelines.value = response.data.data
  if (!boardPipelineId.value) boardPipelineId.value = pipelines.value.find((item) => item.defaultFlag === 1)?.id || pipelines.value[0]?.id
}
async function loadStages(pipelineId?: number) {
  if (!pipelineId) { stages.value = []; return }
  const response = await listPipelineStages(pipelineId)
  stages.value = response.data.data
}
async function loadData() {
  loading.value = true
  try {
    const response = await listOpportunities({ ...query, keyword: query.keyword || undefined, page: currentPage.value, size: pageSize.value })
    tableData.value = response.data.data.records; total.value = response.data.data.total
  } finally { loading.value = false }
}
async function loadBoard() {
  boardLoading.value = true
  try { const response = await getOpportunityBoard({ pipelineId: boardPipelineId.value, ownerUserId: query.ownerUserId }); board.value = response.data.data } finally { boardLoading.value = false }
}
function search() {
  currentPage.value = 1
  if (activeView.value === 'table') loadData()
  else loadBoard()
}
function resetQuery() { Object.assign(query, { keyword: '', status: undefined, stageId: undefined, ownerUserId: undefined, customerId: undefined }); search() }

function resetForm() { Object.assign(form, { name: '', customerId: 0, primaryContactId: undefined, pipelineId: pipelines.value.find((item) => item.defaultFlag === 1)?.id || pipelines.value[0]?.id || 0, amount: 0, expectedCloseDate: '', nextFollowupTime: '', version: undefined }); formContacts.value = [] }
function openCreate() { editingOpportunity.value = null; resetForm(); formDialogVisible.value = true }
async function openEdit(row: CrmOpportunity) { editingOpportunity.value = row; Object.assign(form, { name: row.name, customerId: row.customerId, primaryContactId: undefined, pipelineId: row.pipelineId, amount: row.amount, expectedCloseDate: row.expectedCloseDate || '', nextFollowupTime: row.nextFollowupTime || '', version: row.version }); await loadFormContacts(row.customerId); form.primaryContactId = row.primaryContactId || undefined; formDialogVisible.value = true }
async function loadFormContacts(customerId: number) {
  formContacts.value = []; form.primaryContactId = undefined
  if (!customerId || !permissionStore.hasPermission('crm:contact:list')) return
  const response = await listCustomerContacts(customerId, { page: 1, size: 100 }); formContacts.value = response.data.data.records
}
async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingOpportunity.value) {
    await updateOpportunity(editingOpportunity.value.id, {
      name: form.name,
      primaryContactId: form.primaryContactId,
      amount: form.amount,
      expectedCloseDate: form.expectedCloseDate || undefined,
      nextFollowupTime: form.nextFollowupTime || undefined,
      version: form.version!,
    })
  } else {
    await createOpportunity({
      name: form.name,
      customerId: form.customerId,
      primaryContactId: form.primaryContactId,
      pipelineId: form.pipelineId || undefined,
      amount: form.amount,
      expectedCloseDate: form.expectedCloseDate || undefined,
    })
  }
  ElMessage.success(t('common.success')); formDialogVisible.value = false; await refreshCurrentView()
}
async function handleDelete(row: CrmOpportunity) { try { await ElMessageBox.confirm(`确认删除商机“${row.name}”？`, '删除确认', { type: 'warning' }); await deleteOpportunity(row.id, row.version); ElMessage.success(t('common.success')); refreshCurrentView() } catch { /* 用户取消 */ } }

async function openStage(row: CrmOpportunity, stage?: PipelineStage) {
  stageTarget.value = row
  if (row.pipelineId !== boardPipelineId.value || !stages.value.length) await loadStages(row.pipelineId)
  Object.assign(stageForm, { stageId: stage?.id, reason: '', lossReason: '' }); stageDialogVisible.value = true
}
async function saveStage() {
  if (!stageTarget.value || !stageForm.stageId) return ElMessage.warning('请选择目标阶段')
  if (targetStage.value?.stageType === 'LOST' && !stageForm.lossReason.trim()) return ElMessage.warning('输单原因不能为空')
  await moveOpportunityStage(stageTarget.value.id, { stageId: stageForm.stageId, reason: stageForm.reason || undefined, lossReason: stageForm.lossReason || undefined, version: stageTarget.value.version })
  ElMessage.success('阶段迁移成功'); stageDialogVisible.value = false; refreshCurrentView()
}
function openAssign(row: CrmOpportunity) { assignTarget.value = row; Object.assign(assignForm, { ownerUserId: row.ownerUserId, reason: '' }); assignDialogVisible.value = true }
async function saveAssign() { if (!assignTarget.value || !assignForm.ownerUserId) return; await assignOpportunity(assignTarget.value.id, { ownerUserId: assignForm.ownerUserId, reason: assignForm.reason || undefined, version: assignTarget.value.version }); ElMessage.success('分配成功'); assignDialogVisible.value = false; refreshCurrentView() }
async function handleReopen(row: CrmOpportunity) { await ElMessageBox.confirm(`确认重开商机“${row.name}”？`, '重开商机'); await reopenOpportunity(row.id, row.version); ElMessage.success('商机已重开'); refreshCurrentView() }
async function openHistory(row: CrmOpportunity) { historyTarget.value = row; historyVisible.value = true; historyLoading.value = true; try { const response = await listOpportunityStageHistory(row.id); historyRows.value = response.data.data } finally { historyLoading.value = false } }
async function refreshCurrentView() { await Promise.all([loadData(), activeView.value === 'board' ? loadBoard() : Promise.resolve()]) }
function money(value: number, currency = 'CNY') { return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value || 0) }
function stageName(stageId: number | null) { return stageId ? stages.value.find((item) => item.id === stageId)?.stageName || `阶段 #${stageId}` : '-' }

watch(() => form.customerId, (value, oldValue) => { if (value !== oldValue) loadFormContacts(value) })
watch(boardPipelineId, async (value) => { await loadStages(value); if (activeView.value === 'board') loadBoard() })
watch(activeView, (value) => { if (value === 'board') loadBoard() })
onMounted(async () => { await loadPipelines(); await loadStages(boardPipelineId.value); loadData() })
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmOpportunities') }}</span><div><el-radio-group v-model="activeView"><el-radio-button value="table"><el-icon><List /></el-icon> 表格</el-radio-button><el-radio-button value="board"><el-icon><Grid /></el-icon> 看板</el-radio-button></el-radio-group><el-button v-permission="'crm:opportunity:create'" type="primary" @click="openCreate">新建商机</el-button></div></div></template>
      <el-form inline class="search-form"><el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="商机/客户/编号" /></el-form-item><el-form-item v-if="activeView === 'table'" label="状态"><el-select v-model="query.status" clearable style="width:120px"><el-option label="开放" value="OPEN" /><el-option label="赢单" value="WON" /><el-option label="输单" value="LOST" /></el-select></el-form-item><el-form-item v-if="activeView === 'table'" label="阶段"><el-select v-model="query.stageId" clearable style="width:140px"><el-option v-for="stage in stages" :key="stage.id" :label="stage.stageName" :value="stage.id" /></el-select></el-form-item><el-form-item v-else label="管道"><el-select v-model="boardPipelineId" style="width:160px"><el-option v-for="pipeline in pipelines" :key="pipeline.id" :label="pipeline.name" :value="pipeline.id" /></el-select></el-form-item><el-form-item label="负责人"><OwnerSelector v-model="query.ownerUserId" /></el-form-item><el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="resetQuery">重置</el-button></el-form-item></el-form>
      <template v-if="activeView === 'table'"><el-table v-loading="loading" :data="tableData" border stripe><el-table-column prop="opportunityNo" label="商机编号" width="150" /><el-table-column prop="name" label="商机名称" min-width="180" /><el-table-column label="客户" width="120"><template #default="{ row }">{{ row.customerName || `客户 #${row.customerId}` }}</template></el-table-column><el-table-column label="阶段" width="130"><template #default="{ row }">{{ stageName(row.stageId) }}</template></el-table-column><el-table-column label="金额" width="140"><template #default="{ row }">{{ money(row.amount, row.currencyCode) }}</template></el-table-column><el-table-column label="概率" width="90"><template #default="{ row }">{{ row.probability }}%</template></el-table-column><el-table-column prop="expectedCloseDate" label="预计成交" width="120" /><el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'danger' : 'primary'">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="负责人" width="110"><template #default="{ row }">{{ row.ownerName || `用户 #${row.ownerUserId}` }}</template></el-table-column><el-table-column label="操作" fixed="right" width="350"><template #default="{ row }"><el-button size="small" @click="openHistory(row)">历史</el-button><el-button v-if="row.status === 'OPEN'" v-permission="'crm:opportunity:stage'" size="small" type="primary" @click="openStage(row)">迁移阶段</el-button><el-dropdown trigger="click"><el-button size="small">更多<el-icon><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-permission="'crm:opportunity:update'" @click="openEdit(row)">编辑</el-dropdown-item><el-dropdown-item v-permission="'crm:opportunity:assign'" @click="openAssign(row)">分配</el-dropdown-item><el-dropdown-item v-if="row.status !== 'OPEN'" v-permission="'crm:opportunity:reopen'" @click="handleReopen(row)">重开</el-dropdown-item><el-dropdown-item v-permission="'crm:opportunity:delete'" divided @click="handleDelete(row)">删除</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column></el-table><el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[10,20,50,100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" /></template>
      <OpportunityStageBoard v-else :board="board" :loading="boardLoading" :can-move="permissionStore.hasPermission('crm:opportunity:stage')" @move="openStage" @open="openHistory" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingOpportunity ? '编辑商机' : '新建商机'" width="680px"><el-form ref="formRef" :model="form" :rules="formRules" label-width="100px"><el-form-item label="商机名称" prop="name"><el-input v-model="form.name" /></el-form-item><el-form-item label="客户" prop="customerId"><CustomerPicker v-model="formCustomerId" :disabled="Boolean(editingOpportunity)" /></el-form-item><el-form-item label="主要联系人"><el-select v-model="form.primaryContactId" clearable style="width:100%" :disabled="!form.customerId"><el-option v-for="contact in formContacts" :key="contact.id" :label="`${contact.name} ${contact.mobile || ''}`" :value="contact.id" /></el-select></el-form-item><el-form-item label="销售管道" prop="pipelineId"><el-select v-model="form.pipelineId" style="width:100%" :disabled="Boolean(editingOpportunity)"><el-option v-for="pipeline in pipelines" :key="pipeline.id" :label="pipeline.name" :value="pipeline.id" /></el-select></el-form-item><el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="币种"><el-input value="使用租户默认币种" disabled /></el-form-item><el-form-item label="预计成交日"><el-date-picker v-model="form.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item><el-form-item v-if="editingOpportunity" label="下次跟进"><el-date-picker v-model="form.nextFollowupTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-form><template #footer><el-button @click="formDialogVisible = false">取消</el-button><el-button v-permission="editingOpportunity ? 'crm:opportunity:update' : 'crm:opportunity:create'" type="primary" @click="saveForm">保存</el-button></template></el-dialog>

    <el-dialog v-model="stageDialogVisible" title="迁移商机阶段" width="520px"><el-form :model="stageForm" label-width="90px"><el-form-item label="目标阶段"><el-select v-model="stageForm.stageId" style="width:100%"><el-option v-for="stage in stages" :key="stage.id" :label="`${stage.stageName} (${stage.probability}%)`" :value="stage.id" :disabled="stage.id === stageTarget?.stageId" /></el-select></el-form-item><el-form-item v-if="targetStage?.stageType === 'LOST'" label="输单原因"><el-input v-model="stageForm.lossReason" type="textarea" :rows="3" /></el-form-item><el-form-item label="变更原因"><el-input v-model="stageForm.reason" type="textarea" :rows="3" :placeholder="targetStage && targetStage.sort < (stages.find((item) => item.id === stageTarget?.stageId)?.sort || 0) ? '阶段回退必须填写原因' : '选填'" /></el-form-item></el-form><template #footer><el-button @click="stageDialogVisible = false">取消</el-button><el-button v-permission="'crm:opportunity:stage'" type="primary" @click="saveStage">确认迁移</el-button></template></el-dialog>
    <el-dialog v-model="assignDialogVisible" title="分配商机" width="480px"><el-form :model="assignForm" label-width="90px"><el-form-item label="负责人"><OwnerSelector v-model="assignForm.ownerUserId" /></el-form-item><el-form-item label="原因"><el-input v-model="assignForm.reason" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button v-permission="'crm:opportunity:assign'" type="primary" @click="saveAssign">确认分配</el-button></template></el-dialog>
    <el-drawer v-model="historyVisible" :title="`${historyTarget?.name || ''} · 阶段历史`" size="680px"><el-table v-loading="historyLoading" :data="historyRows" border><el-table-column label="原阶段"><template #default="{ row }">{{ stageName(row.fromStageId) }}</template></el-table-column><el-table-column label="目标阶段"><template #default="{ row }">{{ stageName(row.toStageId) }}</template></el-table-column><el-table-column prop="changeReason" label="原因" min-width="160" /><el-table-column label="操作人"><template #default="{ row }">用户 #{{ row.changedByUserId }}</template></el-table-column><el-table-column prop="changedTime" label="时间" width="170" /></el-table></el-drawer>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; font-weight: 600; }
.card-header > div { display: flex; gap: 12px; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
</style>
