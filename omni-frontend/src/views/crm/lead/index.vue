<script setup lang="ts">
/** CRM 线索页面：CRUD、快速跟进、分配、状态判定和转换向导。 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  assignLead,
  batchAssignLeads,
  checkLeadDuplicates,
  convertLead,
  createLead,
  deleteLead,
  disqualifyLead,
  getLead,
  listLeads,
  qualifyLead,
  reopenLead,
  updateLead,
  type ConvertLeadRequest,
  type CrmLead,
  type DuplicateLeadCandidate,
  type LeadStatus,
  type SaveLeadRequest,
} from '@/api/crm-lead'
import { createActivity } from '@/api/crm-activity'
import { listCustomerContacts, type CrmContact } from '@/api/crm-contact'
import OwnerSelector from '@/components/crm/OwnerSelector.vue'
import CustomerPicker from '@/components/crm/CustomerPicker.vue'
import ActivityTimeline from '@/components/crm/ActivityTimeline.vue'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canViewPii = computed(() => permissionStore.hasPermission('crm:pii:view'))
const loading = ref(false)
const tableData = ref<CrmLead[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const selectedRows = ref<CrmLead[]>([])
const query = reactive<{ keyword: string; status?: LeadStatus; ownerUserId?: number; sourceCode: string }>({ keyword: '', sourceCode: '' })

const statusOptions: Array<{ label: string; value: LeadStatus }> = [
  { label: '新线索', value: 'NEW' }, { label: '跟进中', value: 'FOLLOWING' },
  { label: '已合格', value: 'QUALIFIED' }, { label: '无效', value: 'DISQUALIFIED' },
  { label: '已转换', value: 'CONVERTED' },
]
const statusLabel = Object.fromEntries(statusOptions.map((item) => [item.value, item.label])) as Record<LeadStatus, string>

const formDialogVisible = ref(false)
const editingLead = ref<CrmLead | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SaveLeadRequest>({ fullName: '', companyName: '', jobTitle: '', mobile: '', phone: '', email: '', region: '', address: '', sourceCode: '', industryCode: '', rating: '', nextFollowupTime: '' })
const formRules: FormRules<SaveLeadRequest> = {
  fullName: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}
const duplicateCandidates = ref<DuplicateLeadCandidate[]>([])

const assignDialogVisible = ref(false)
const assignTarget = ref<CrmLead | null>(null)
const assignForm = reactive({ ownerUserId: undefined as number | undefined, reason: '' })

const followupDialogVisible = ref(false)
const followupTarget = ref<CrmLead | null>(null)
const timelineVisible = ref(false)
const followupForm = reactive({ activityType: 'CALL', subject: '', content: '', plannedStartTime: '', nextActionTime: '' })

const convertDialogVisible = ref(false)
const convertStep = ref(0)
const convertTarget = ref<CrmLead | null>(null)
const existingContacts = ref<CrmContact[]>([])
const convertForm = reactive<ConvertLeadRequest>({
  version: 0, customerMode: 'CREATE', customerId: undefined, customerName: '',
  contactMode: 'CREATE', contactId: undefined, contactName: '', contactMobile: '', contactEmail: '',
  createOpportunity: true, opportunityName: '', amount: undefined, expectedCloseDate: '',
})

const canBatchAssign = computed(() => selectedRows.value.length > 0)

function isLeadActive(row: CrmLead) {
  return row.status === 'NEW' || row.status === 'FOLLOWING' || row.status === 'QUALIFIED'
}

function canDeleteLead(row: CrmLead) {
  return row.status !== 'CONVERTED'
}

async function loadData() {
  loading.value = true
  try {
    const response = await listLeads({
      keyword: query.keyword || undefined,
      status: query.status,
      ownerUserId: query.ownerUserId,
      sourceCode: query.sourceCode || undefined,
      page: currentPage.value,
      size: pageSize.value,
    })
    tableData.value = response.data.data.records
    total.value = response.data.data.total
  } finally { loading.value = false }
}

function resetQuery() { Object.assign(query, { keyword: '', status: undefined, ownerUserId: undefined, sourceCode: '' }); currentPage.value = 1; loadData() }
function search() { currentPage.value = 1; loadData() }

function resetForm() {
  Object.assign(form, { fullName: '', companyName: '', jobTitle: '', mobile: '', phone: '', email: '', region: '', address: '', sourceCode: '', industryCode: '', rating: '', nextFollowupTime: '', version: undefined })
  duplicateCandidates.value = []
}

function openCreate() { editingLead.value = null; resetForm(); formDialogVisible.value = true }
async function openEdit(row: CrmLead) {
  if (!isLeadActive(row)) return ElMessage.warning('终态线索请先重新激活后再编辑')
  const response = await getLead(row.id)
  const detail = response.data.data
  editingLead.value = detail
  Object.assign(form, { fullName: detail.fullName, companyName: detail.companyName || '', jobTitle: detail.jobTitle || '', mobile: canViewPii.value ? detail.mobile || '' : '', phone: canViewPii.value ? detail.phone || '' : '', email: canViewPii.value ? detail.email || '' : '', region: detail.region || '', address: canViewPii.value ? detail.address || '' : '', sourceCode: detail.sourceCode || '', industryCode: detail.industryCode || '', rating: detail.rating || '', nextFollowupTime: detail.nextFollowupTime || '', version: detail.version })
  duplicateCandidates.value = []
  formDialogVisible.value = true
}

function buildLeadPayload(): SaveLeadRequest {
  const payload: SaveLeadRequest = {
    fullName: form.fullName,
    companyName: form.companyName,
    jobTitle: form.jobTitle,
    region: form.region,
    sourceCode: form.sourceCode,
    industryCode: form.industryCode,
    rating: form.rating,
    nextFollowupTime: form.nextFollowupTime,
    version: form.version,
  }
  if (canViewPii.value) {
    payload.mobile = form.mobile
    payload.phone = form.phone
    payload.email = form.email
    payload.address = form.address
  }
  return payload
}

async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const payload = buildLeadPayload()
  if (!editingLead.value) {
    const duplicateResponse = await checkLeadDuplicates(payload)
    duplicateCandidates.value = duplicateResponse.data.data
    if (duplicateCandidates.value.length) {
      await ElMessageBox.confirm(`发现 ${duplicateCandidates.value.length} 条可能重复的线索，仍要创建吗？`, '重复候选提示', { type: 'warning' })
    }
    await createLead(payload)
  } else {
    await updateLead(editingLead.value.id, payload)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
}

async function handleDelete(row: CrmLead) {
  if (!canDeleteLead(row)) return ElMessage.warning('已转换线索不可删除')
  try { await ElMessageBox.confirm(`确认删除线索“${row.fullName}”？`, '删除确认', { type: 'warning' }); await deleteLead(row.id, row.version); ElMessage.success(t('common.success')); loadData() } catch { /* 用户取消 */ }
}

function openAssign(row: CrmLead) { if (!isLeadActive(row)) return ElMessage.warning('终态线索不可分配'); assignTarget.value = row; Object.assign(assignForm, { ownerUserId: row.ownerUserId, reason: '' }); assignDialogVisible.value = true }
function openBatchAssign() { assignTarget.value = null; Object.assign(assignForm, { ownerUserId: undefined, reason: '' }); assignDialogVisible.value = true }
async function saveAssign() {
  if (!assignForm.ownerUserId) return ElMessage.warning('请选择负责人')
  if (assignTarget.value) {
    await assignLead(assignTarget.value.id, { ownerUserId: assignForm.ownerUserId, reason: assignForm.reason || undefined, version: assignTarget.value.version })
  } else {
    const assignableRows = selectedRows.value.filter(isLeadActive)
    if (!assignableRows.length) return ElMessage.warning('请选择可分配的线索')
    await batchAssignLeads({
      items: assignableRows.map((item) => ({ id: item.id, version: item.version })),
      ownerUserId: assignForm.ownerUserId,
      reason: assignForm.reason || undefined,
    })
  }
  ElMessage.success('分配成功'); assignDialogVisible.value = false; loadData()
}

function openFollowup(row: CrmLead) {
  if (!isLeadActive(row)) return ElMessage.warning('终态线索不可新增跟进')
  followupTarget.value = row
  Object.assign(followupForm, { activityType: 'CALL', subject: `跟进 ${row.fullName}`, content: '', plannedStartTime: '', nextActionTime: '' })
  followupDialogVisible.value = true
}
async function saveFollowup() {
  if (!followupTarget.value || !followupForm.subject) return
  await createActivity({ rootType: 'LEAD', rootId: followupTarget.value.id, ...followupForm })
  ElMessage.success('跟进记录已保存'); followupDialogVisible.value = false; loadData()
}
function openTimeline(row: CrmLead) { followupTarget.value = row; timelineVisible.value = true }

async function handleQualify(row: CrmLead) {
  await ElMessageBox.confirm(`确认将“${row.fullName}”判定为合格线索？`, '线索判定')
  await qualifyLead(row.id, row.version); ElMessage.success('线索已合格'); loadData()
}
async function handleDisqualify(row: CrmLead) {
  const result = await ElMessageBox.prompt('请填写无效原因', '判定无效', { inputPattern: /\S+/, inputErrorMessage: '无效原因不能为空' })
  await disqualifyLead(row.id, { reason: result.value, version: row.version }); ElMessage.success('已判定为无效线索'); loadData()
}
async function handleReopen(row: CrmLead) {
  await reopenLead(row.id, row.version); ElMessage.success('线索已重新激活'); loadData()
}

async function openConvert(row: CrmLead) {
  const source = (await getLead(row.id)).data.data
  convertTarget.value = source; convertStep.value = 0; existingContacts.value = []
  Object.assign(convertForm, { version: source.version, customerMode: 'CREATE', customerId: undefined, customerName: source.companyName || source.fullName, contactMode: 'CREATE', contactId: undefined, contactName: source.fullName, contactMobile: canViewPii.value ? source.mobile || '' : '', contactEmail: canViewPii.value ? source.email || '' : '', createOpportunity: true, opportunityName: `${source.companyName || source.fullName} 商机`, amount: undefined, expectedCloseDate: '' })
  convertDialogVisible.value = true
}

async function loadExistingContacts(customerId?: number) {
  existingContacts.value = []
  if (!customerId || !permissionStore.hasPermission('crm:contact:list')) return
  const response = await listCustomerContacts(customerId, { page: 1, size: 100 })
  existingContacts.value = response.data.data.records
}

async function submitConversion() {
  if (!convertTarget.value) return
  if (convertForm.customerMode === 'CREATE' && !convertForm.customerName) return ElMessage.warning('请输入新客户名称')
  if (convertForm.customerMode === 'LINK' && !convertForm.customerId) return ElMessage.warning('请选择已有客户')
  if (convertForm.contactMode === 'CREATE' && !convertForm.contactName) return ElMessage.warning('请输入联系人姓名')
  if (convertForm.contactMode === 'LINK' && !convertForm.contactId) return ElMessage.warning('请选择已有联系人')
  if (convertForm.createOpportunity && !convertForm.opportunityName) return ElMessage.warning('请输入商机名称')
  const payload: ConvertLeadRequest = { ...convertForm }
  if (!canViewPii.value) {
    delete payload.contactMobile
    delete payload.contactEmail
  }
  const response = await convertLead(convertTarget.value.id, payload)
  ElMessage.success(`转换成功：客户 #${response.data.data.customerId}`)
  convertDialogVisible.value = false; loadData()
}

watch(() => convertForm.customerId, loadExistingContacts)
onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmLeads') }}</span><div><el-button v-permission="'crm:lead:assign'" :disabled="!canBatchAssign" @click="openBatchAssign">批量分配</el-button><el-button v-permission="'crm:lead:create'" type="primary" @click="openCreate">新建线索</el-button></div></div></template>
      <el-form inline class="search-form">
        <el-form-item label="关键词"><el-input v-model="query.keyword" clearable placeholder="姓名/公司/手机/邮箱" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 130px"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="来源"><el-input v-model="query.sourceCode" clearable style="width: 130px" /></el-form-item>
        <el-form-item label="负责人"><OwnerSelector v-model="query.ownerUserId" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">搜索</el-button><el-button @click="resetQuery">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="46" :selectable="isLeadActive" /><el-table-column prop="leadNo" label="线索编号" width="145" />
        <el-table-column prop="fullName" label="姓名" width="110" /><el-table-column prop="companyName" label="公司" min-width="160" show-overflow-tooltip />
        <el-table-column prop="mobile" label="手机" width="135" /><el-table-column prop="sourceCode" label="来源" width="100" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag>{{ statusLabel[row.status as LeadStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="负责人" width="110"><template #default="{ row }">{{ row.ownerName || `用户 #${row.ownerUserId}` }}</template></el-table-column><el-table-column prop="nextFollowupTime" label="下次跟进" width="170" />
        <el-table-column label="操作" fixed="right" width="400">
          <template #default="{ row }">
            <el-button size="small" @click="openTimeline(row)">时间线</el-button>
            <el-button v-if="isLeadActive(row)" v-permission="'crm:activity:create'" size="small" type="primary" @click="openFollowup(row)">快速跟进</el-button>
            <el-dropdown v-if="row.status !== 'CONVERTED'" trigger="click">
              <el-button size="small">更多<el-icon><ArrowDown /></el-icon></el-button><template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="isLeadActive(row)" v-permission="'crm:lead:update'" @click="openEdit(row)">编辑</el-dropdown-item>
                  <el-dropdown-item v-if="isLeadActive(row)" v-permission="'crm:lead:assign'" @click="openAssign(row)">分配</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'FOLLOWING'" v-permission="'crm:lead:update'" @click="handleQualify(row)">判定合格</el-dropdown-item>
                  <el-dropdown-item v-if="row.status !== 'CONVERTED' && row.status !== 'DISQUALIFIED'" v-permission="'crm:lead:disqualify'" @click="handleDisqualify(row)">判定无效</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'DISQUALIFIED'" v-permission="'crm:lead:update'" @click="handleReopen(row)">重新激活</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'QUALIFIED'" v-permission="'crm:lead:convert'" @click="openConvert(row)">转换</el-dropdown-item>
                  <el-dropdown-item v-if="canDeleteLead(row)" v-permission="'crm:lead:delete'" divided @click="handleDelete(row)">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[5, 10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingLead ? '编辑线索' : '新建线索'" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="姓名" prop="fullName"><el-input v-model="form.fullName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="公司"><el-input v-model="form.companyName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="职位"><el-input v-model="form.jobTitle" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机"><el-input v-model="form.mobile" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="邮箱" prop="email"><el-input v-model="form.email" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="来源"><el-input v-model="form.sourceCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="行业"><el-input v-model="form.industryCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="评级"><el-select v-model="form.rating" clearable style="width:100%"><el-option v-for="rating in ['A','B','C','D']" :key="rating" :label="rating" :value="rating" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="下次跟进"><el-date-picker v-model="form.nextFollowupTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地区"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="地址"><el-input v-model="form.address" :disabled="!canViewPii" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-alert v-if="duplicateCandidates.length" :title="`发现 ${duplicateCandidates.length} 条重复候选`" type="warning" show-icon :closable="false" />
      <template #footer><el-button @click="formDialogVisible = false">取消</el-button><el-button v-permission="editingLead ? 'crm:lead:update' : 'crm:lead:create'" type="primary" @click="saveForm">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" :title="assignTarget ? '分配线索' : `批量分配 ${selectedRows.length} 条线索`" width="480px"><el-form :model="assignForm" label-width="90px"><el-form-item label="负责人"><OwnerSelector v-model="assignForm.ownerUserId" /></el-form-item><el-form-item label="原因"><el-input v-model="assignForm.reason" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="assignDialogVisible = false">取消</el-button><el-button v-permission="'crm:lead:assign'" type="primary" @click="saveAssign">确认分配</el-button></template></el-dialog>

    <el-dialog v-model="followupDialogVisible" title="快速跟进" width="620px"><el-form :model="followupForm" label-width="100px"><el-form-item label="活动类型"><el-select v-model="followupForm.activityType"><el-option label="电话" value="CALL" /><el-option label="拜访" value="VISIT" /><el-option label="邮件" value="EMAIL" /><el-option label="会议" value="MEETING" /></el-select></el-form-item><el-form-item label="主题"><el-input v-model="followupForm.subject" /></el-form-item><el-form-item label="跟进内容"><el-input v-model="followupForm.content" type="textarea" :rows="4" placeholder="仅支持纯文本" /></el-form-item><el-form-item label="计划时间"><el-date-picker v-model="followupForm.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item><el-form-item label="下次行动"><el-date-picker v-model="followupForm.nextActionTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item></el-form><template #footer><el-button @click="followupDialogVisible = false">取消</el-button><el-button v-permission="'crm:activity:create'" type="primary" @click="saveFollowup">保存跟进</el-button></template></el-dialog>

    <el-drawer v-model="timelineVisible" :title="`${followupTarget?.fullName || ''} · 跟进时间线`" size="560px"><ActivityTimeline root-type="LEAD" :root-id="followupTarget?.id" /></el-drawer>

    <el-dialog v-model="convertDialogVisible" title="线索转换向导" width="760px" :close-on-click-modal="false">
      <el-steps :active="convertStep" finish-status="success" align-center><el-step title="客户" /><el-step title="联系人" /><el-step title="商机" /><el-step title="确认" /></el-steps>
      <div class="convert-body">
        <el-form v-if="convertStep === 0" :model="convertForm" label-width="110px"><el-form-item label="客户处理"><el-radio-group v-model="convertForm.customerMode"><el-radio-button value="CREATE">新建客户</el-radio-button><el-radio-button value="LINK">关联已有</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="convertForm.customerMode === 'CREATE'" label="客户名称"><el-input v-model="convertForm.customerName" /></el-form-item><el-form-item v-else label="已有客户"><CustomerPicker v-model="convertForm.customerId" /></el-form-item></el-form>
        <el-form v-else-if="convertStep === 1" :model="convertForm" label-width="110px"><el-form-item label="联系人处理"><el-radio-group v-model="convertForm.contactMode"><el-radio-button value="CREATE">新建联系人</el-radio-button><el-radio-button value="LINK" :disabled="convertForm.customerMode !== 'LINK'">关联已有</el-radio-button></el-radio-group></el-form-item><template v-if="convertForm.contactMode === 'CREATE'"><el-form-item label="姓名"><el-input v-model="convertForm.contactName" /></el-form-item><el-form-item label="手机"><el-input v-model="convertForm.contactMobile" :disabled="!canViewPii" /></el-form-item><el-form-item label="邮箱"><el-input v-model="convertForm.contactEmail" :disabled="!canViewPii" /></el-form-item></template><el-form-item v-else label="已有联系人"><el-select v-model="convertForm.contactId" style="width:100%"><el-option v-for="contact in existingContacts" :key="contact.id" :label="`${contact.name} ${contact.mobile || ''}`" :value="contact.id" /></el-select></el-form-item></el-form>
        <el-form v-else-if="convertStep === 2" :model="convertForm" label-width="110px"><el-form-item label="同时创建商机"><el-switch v-model="convertForm.createOpportunity" /></el-form-item><template v-if="convertForm.createOpportunity"><el-form-item label="商机名称"><el-input v-model="convertForm.opportunityName" /></el-form-item><el-form-item label="预计金额"><el-input-number v-model="convertForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item label="预计成交日"><el-date-picker v-model="convertForm.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></template></el-form>
        <el-descriptions v-else :column="2" border title="转换确认"><el-descriptions-item label="线索">{{ convertTarget?.fullName }}</el-descriptions-item><el-descriptions-item label="客户">{{ convertForm.customerMode === 'CREATE' ? convertForm.customerName : `已有客户 #${convertForm.customerId}` }}</el-descriptions-item><el-descriptions-item label="联系人">{{ convertForm.contactMode === 'CREATE' ? convertForm.contactName : `已有联系人 #${convertForm.contactId}` }}</el-descriptions-item><el-descriptions-item label="商机">{{ convertForm.createOpportunity ? convertForm.opportunityName : '不创建' }}</el-descriptions-item></el-descriptions>
      </div>
      <template #footer><el-button v-if="convertStep > 0" @click="convertStep--">上一步</el-button><el-button v-if="convertStep < 3" type="primary" @click="convertStep++">下一步</el-button><el-button v-else v-permission="'crm:lead:convert'" type="primary" @click="submitConversion">确认转换</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.convert-body { min-height: 280px; padding: 30px 20px 0; }
</style>
