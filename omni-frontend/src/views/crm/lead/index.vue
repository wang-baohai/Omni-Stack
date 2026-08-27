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

const statusOptions = computed<Array<{ label: string; value: LeadStatus }>>(() => [
  { label: t('crmLead.statusNew'), value: 'NEW' }, { label: t('crmLead.statusFollowing'), value: 'FOLLOWING' },
  { label: t('crmLead.statusQualified'), value: 'QUALIFIED' }, { label: t('crmLead.statusDisqualified'), value: 'DISQUALIFIED' },
  { label: t('crmLead.statusConverted'), value: 'CONVERTED' },
])
const statusLabel = computed(() => Object.fromEntries(statusOptions.value.map((item) => [item.value, item.label])) as Record<LeadStatus, string>)

const formDialogVisible = ref(false)
const editingLead = ref<CrmLead | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SaveLeadRequest>({ fullName: '', companyName: '', jobTitle: '', mobile: '', phone: '', email: '', region: '', address: '', sourceCode: '', industryCode: '', rating: '', nextFollowupTime: '' })
const formRules = computed<FormRules<SaveLeadRequest>>(() => ({
  fullName: [{ required: true, message: t('crmLead.nameRequired'), trigger: 'blur' }],
  email: [{ type: 'email', message: t('validation.emailFormat'), trigger: 'blur' }],
}))
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
  if (!isLeadActive(row)) return ElMessage.warning(t('crmLead.reopenBeforeEdit'))
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
      await ElMessageBox.confirm(t('crmLead.duplicateConfirm', { count: duplicateCandidates.value.length }), t('crmLead.duplicateTitle'), { type: 'warning' })
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
  if (!canDeleteLead(row)) return ElMessage.warning(t('crmLead.convertedCannotDelete'))
  try { await ElMessageBox.confirm(t('crmLead.deleteConfirm', { name: row.fullName }), t('crmLead.deleteTitle'), { type: 'warning' }); await deleteLead(row.id, row.version); ElMessage.success(t('common.success')); loadData() } catch { /* 用户取消 */ }
}

function openAssign(row: CrmLead) { if (!isLeadActive(row)) return ElMessage.warning(t('crmLead.terminalCannotAssign')); assignTarget.value = row; Object.assign(assignForm, { ownerUserId: row.ownerUserId, reason: '' }); assignDialogVisible.value = true }
function openBatchAssign() { assignTarget.value = null; Object.assign(assignForm, { ownerUserId: undefined, reason: '' }); assignDialogVisible.value = true }
async function saveAssign() {
  if (!assignForm.ownerUserId) return ElMessage.warning(t('crmLead.ownerRequired'))
  if (assignTarget.value) {
    await assignLead(assignTarget.value.id, { ownerUserId: assignForm.ownerUserId, reason: assignForm.reason || undefined, version: assignTarget.value.version })
  } else {
    const assignableRows = selectedRows.value.filter(isLeadActive)
    if (!assignableRows.length) return ElMessage.warning(t('crmLead.assignableRequired'))
    await batchAssignLeads({
      items: assignableRows.map((item) => ({ id: item.id, version: item.version })),
      ownerUserId: assignForm.ownerUserId,
      reason: assignForm.reason || undefined,
    })
  }
  ElMessage.success(t('crmLead.assigned')); assignDialogVisible.value = false; loadData()
}

function openFollowup(row: CrmLead) {
  if (!isLeadActive(row)) return ElMessage.warning(t('crmLead.terminalCannotFollowup'))
  followupTarget.value = row
  Object.assign(followupForm, { activityType: 'CALL', subject: t('crmLead.followupSubject', { name: row.fullName }), content: '', plannedStartTime: '', nextActionTime: '' })
  followupDialogVisible.value = true
}
async function saveFollowup() {
  if (!followupTarget.value || !followupForm.subject) return
  await createActivity({ rootType: 'LEAD', rootId: followupTarget.value.id, ...followupForm })
  ElMessage.success(t('crmLead.followupSaved')); followupDialogVisible.value = false; loadData()
}
function openTimeline(row: CrmLead) { followupTarget.value = row; timelineVisible.value = true }

async function handleQualify(row: CrmLead) {
  await ElMessageBox.confirm(t('crmLead.qualifyConfirm', { name: row.fullName }), t('crmLead.judgment'))
  await qualifyLead(row.id, row.version); ElMessage.success(t('crmLead.qualified')); loadData()
}
async function handleDisqualify(row: CrmLead) {
  const result = await ElMessageBox.prompt(t('crmLead.disqualifyReasonPrompt'), t('crmLead.disqualify'), { inputPattern: /\S+/, inputErrorMessage: t('crmLead.disqualifyReasonRequired') })
  await disqualifyLead(row.id, { reason: result.value, version: row.version }); ElMessage.success(t('crmLead.disqualified')); loadData()
}
async function handleReopen(row: CrmLead) {
  await reopenLead(row.id, row.version); ElMessage.success(t('crmLead.reopened')); loadData()
}

async function openConvert(row: CrmLead) {
  const source = (await getLead(row.id)).data.data
  convertTarget.value = source; convertStep.value = 0; existingContacts.value = []
  Object.assign(convertForm, { version: source.version, customerMode: 'CREATE', customerId: undefined, customerName: source.companyName || source.fullName, contactMode: 'CREATE', contactId: undefined, contactName: source.fullName, contactMobile: canViewPii.value ? source.mobile || '' : '', contactEmail: canViewPii.value ? source.email || '' : '', createOpportunity: true, opportunityName: t('crmLead.defaultOpportunityName', { name: source.companyName || source.fullName }), amount: undefined, expectedCloseDate: '' })
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
  if (convertForm.customerMode === 'CREATE' && !convertForm.customerName) return ElMessage.warning(t('crmLead.newCustomerRequired'))
  if (convertForm.customerMode === 'LINK' && !convertForm.customerId) return ElMessage.warning(t('crmLead.existingCustomerRequired'))
  if (convertForm.contactMode === 'CREATE' && !convertForm.contactName) return ElMessage.warning(t('crmLead.nameRequired'))
  if (convertForm.contactMode === 'LINK' && !convertForm.contactId) return ElMessage.warning(t('crmLead.existingContactRequired'))
  if (convertForm.createOpportunity && !convertForm.opportunityName) return ElMessage.warning(t('crmOpportunity.nameRequired'))
  const payload: ConvertLeadRequest = { ...convertForm }
  if (!canViewPii.value) {
    delete payload.contactMobile
    delete payload.contactEmail
  }
  const response = await convertLead(convertTarget.value.id, payload)
  ElMessage.success(t('crmLead.convertedSuccess', { id: response.data.data.customerId }))
  convertDialogVisible.value = false; loadData()
}

watch(() => convertForm.customerId, loadExistingContacts)
onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmLeads') }}</span><div><el-button v-permission="'crm:lead:assign'" :disabled="!canBatchAssign" @click="openBatchAssign">{{ t('crmLead.batchAssign') }}</el-button><el-button v-permission="'crm:lead:create'" type="primary" @click="openCreate">{{ t('crmLead.create') }}</el-button></div></div></template>
      <el-form inline class="search-form">
        <el-form-item :label="t('crmLead.keyword')"><el-input v-model="query.keyword" clearable :placeholder="t('crmLead.keywordPlaceholder')" @keyup.enter="search" /></el-form-item>
        <el-form-item :label="t('common.status')"><el-select v-model="query.status" clearable style="width: 130px"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item :label="t('crmLead.source')"><el-input v-model="query.sourceCode" clearable style="width: 130px" /></el-form-item>
        <el-form-item :label="t('crmContact.owner')"><OwnerSelector v-model="query.ownerUserId" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">{{ t('common.search') }}</el-button><el-button @click="resetQuery">{{ t('common.reset') }}</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tableData" border stripe @selection-change="selectedRows = $event">
        <el-table-column type="selection" width="46" :selectable="isLeadActive" /><el-table-column prop="leadNo" :label="t('crmLead.number')" width="145" />
        <el-table-column prop="fullName" :label="t('crmLead.name')" width="110" /><el-table-column prop="companyName" :label="t('crmLead.company')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="mobile" :label="t('crmContact.mobile')" width="135" /><el-table-column prop="sourceCode" :label="t('crmLead.source')" width="100" />
        <el-table-column :label="t('common.status')" width="100"><template #default="{ row }"><el-tag>{{ statusLabel[row.status as LeadStatus] }}</el-tag></template></el-table-column>
        <el-table-column :label="t('crmContact.owner')" width="110"><template #default="{ row }">{{ row.ownerName || t('crmUi.userNumber', { id: row.ownerUserId }) }}</template></el-table-column><el-table-column prop="nextFollowupTime" :label="t('crmCustomerOverview.nextFollowup')" width="170" />
        <el-table-column :label="t('common.actions')" fixed="right" width="400">
          <template #default="{ row }">
            <el-button size="small" @click="openTimeline(row)">{{ t('crmLead.timeline') }}</el-button>
            <el-button v-if="isLeadActive(row)" v-permission="'crm:activity:create'" size="small" type="primary" @click="openFollowup(row)">{{ t('crmLead.quickFollowup') }}</el-button>
            <el-dropdown v-if="row.status !== 'CONVERTED'" trigger="click">
              <el-button size="small">{{ t('crmActivity.more') }}<el-icon><ArrowDown /></el-icon></el-button><template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="isLeadActive(row)" v-permission="'crm:lead:update'" @click="openEdit(row)">{{ t('common.edit') }}</el-dropdown-item>
                  <el-dropdown-item v-if="isLeadActive(row)" v-permission="'crm:lead:assign'" @click="openAssign(row)">{{ t('crmOpportunity.assign') }}</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'FOLLOWING'" v-permission="'crm:lead:update'" @click="handleQualify(row)">{{ t('crmLead.qualify') }}</el-dropdown-item>
                  <el-dropdown-item v-if="row.status !== 'CONVERTED' && row.status !== 'DISQUALIFIED'" v-permission="'crm:lead:disqualify'" @click="handleDisqualify(row)">{{ t('crmLead.disqualify') }}</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'DISQUALIFIED'" v-permission="'crm:lead:update'" @click="handleReopen(row)">{{ t('crmLead.reopen') }}</el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'QUALIFIED'" v-permission="'crm:lead:convert'" @click="openConvert(row)">{{ t('crmLead.convert') }}</el-dropdown-item>
                  <el-dropdown-item v-if="canDeleteLead(row)" v-permission="'crm:lead:delete'" divided @click="handleDelete(row)">{{ t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[5, 10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingLead ? t('crmLead.edit') : t('crmLead.create')" width="720px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('crmLead.name')" prop="fullName"><el-input v-model="form.fullName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmLead.company')"><el-input v-model="form.companyName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmContact.jobTitle')"><el-input v-model="form.jobTitle" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmContact.mobile')"><el-input v-model="form.mobile" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmContact.phone')"><el-input v-model="form.phone" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmContact.email')" prop="email"><el-input v-model="form.email" :disabled="!canViewPii" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmLead.source')"><el-input v-model="form.sourceCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmCustomer.industry')"><el-input v-model="form.industryCode" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmLead.rating')"><el-select v-model="form.rating" clearable style="width:100%"><el-option v-for="rating in ['A','B','C','D']" :key="rating" :label="rating" :value="rating" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmCustomerOverview.nextFollowup')"><el-date-picker v-model="form.nextFollowupTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('crmCustomer.region')"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('crmCustomer.address')"><el-input v-model="form.address" :disabled="!canViewPii" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <el-alert v-if="duplicateCandidates.length" :title="t('crmLead.duplicateCandidates', { count: duplicateCandidates.length })" type="warning" show-icon :closable="false" />
      <template #footer><el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="editingLead ? 'crm:lead:update' : 'crm:lead:create'" type="primary" @click="saveForm">{{ t('common.save') }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="assignDialogVisible" :title="assignTarget ? t('crmLead.assign') : t('crmLead.batchAssignTitle', { count: selectedRows.length })" width="480px"><el-form :model="assignForm" label-width="90px"><el-form-item :label="t('crmContact.owner')"><OwnerSelector v-model="assignForm.ownerUserId" /></el-form-item><el-form-item :label="t('crmOpportunity.reason')"><el-input v-model="assignForm.reason" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="assignDialogVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="'crm:lead:assign'" type="primary" @click="saveAssign">{{ t('crmOpportunity.confirmAssign') }}</el-button></template></el-dialog>

    <el-dialog v-model="followupDialogVisible" :title="t('crmLead.quickFollowup')" width="620px"><el-form :model="followupForm" label-width="100px"><el-form-item :label="t('crmActivity.activityType')"><el-select v-model="followupForm.activityType"><el-option :label="t('crmLead.activityCall')" value="CALL" /><el-option :label="t('crmLead.activityVisit')" value="VISIT" /><el-option :label="t('crmLead.activityEmail')" value="EMAIL" /><el-option :label="t('crmLead.activityMeeting')" value="MEETING" /></el-select></el-form-item><el-form-item :label="t('crmActivity.subject')"><el-input v-model="followupForm.subject" /></el-form-item><el-form-item :label="t('crmLead.followupContent')"><el-input v-model="followupForm.content" type="textarea" :rows="4" :placeholder="t('crmActivity.plainTextOnly')" /></el-form-item><el-form-item :label="t('crmActivity.plannedTime')"><el-date-picker v-model="followupForm.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item><el-form-item :label="t('crmActivity.nextAction')"><el-date-picker v-model="followupForm.nextActionTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item></el-form><template #footer><el-button @click="followupDialogVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="'crm:activity:create'" type="primary" @click="saveFollowup">{{ t('crmLead.saveFollowup') }}</el-button></template></el-dialog>

    <el-drawer v-model="timelineVisible" :title="t('crmLead.timelineTitle', { name: followupTarget?.fullName || '' })" size="560px"><ActivityTimeline root-type="LEAD" :root-id="followupTarget?.id" /></el-drawer>

    <el-dialog v-model="convertDialogVisible" :title="t('crmLead.convertWizard')" width="760px" :close-on-click-modal="false">
      <el-steps :active="convertStep" finish-status="success" align-center><el-step :title="t('crmContact.customer')" /><el-step :title="t('crmCustomerOverview.contacts')" /><el-step :title="t('crmOpportunity.name')" /><el-step :title="t('common.confirm')" /></el-steps>
      <div class="convert-body">
        <el-form v-if="convertStep === 0" :model="convertForm" label-width="110px"><el-form-item :label="t('crmLead.customerHandling')"><el-radio-group v-model="convertForm.customerMode"><el-radio-button value="CREATE">{{ t('crmCustomer.create') }}</el-radio-button><el-radio-button value="LINK">{{ t('crmLead.linkExisting') }}</el-radio-button></el-radio-group></el-form-item><el-form-item v-if="convertForm.customerMode === 'CREATE'" :label="t('crmCustomerOverview.name')"><el-input v-model="convertForm.customerName" /></el-form-item><el-form-item v-else :label="t('crmLead.existingCustomer')"><CustomerPicker v-model="convertForm.customerId" /></el-form-item></el-form>
        <el-form v-else-if="convertStep === 1" :model="convertForm" label-width="110px"><el-form-item :label="t('crmLead.contactHandling')"><el-radio-group v-model="convertForm.contactMode"><el-radio-button value="CREATE">{{ t('crmContact.create') }}</el-radio-button><el-radio-button value="LINK" :disabled="convertForm.customerMode !== 'LINK'">{{ t('crmLead.linkExisting') }}</el-radio-button></el-radio-group></el-form-item><template v-if="convertForm.contactMode === 'CREATE'"><el-form-item :label="t('crmContact.name')"><el-input v-model="convertForm.contactName" /></el-form-item><el-form-item :label="t('crmContact.mobile')"><el-input v-model="convertForm.contactMobile" :disabled="!canViewPii" /></el-form-item><el-form-item :label="t('crmContact.email')"><el-input v-model="convertForm.contactEmail" :disabled="!canViewPii" /></el-form-item></template><el-form-item v-else :label="t('crmLead.existingContact')"><el-select v-model="convertForm.contactId" style="width:100%"><el-option v-for="contact in existingContacts" :key="contact.id" :label="`${contact.name} ${contact.mobile || ''}`" :value="contact.id" /></el-select></el-form-item></el-form>
        <el-form v-else-if="convertStep === 2" :model="convertForm" label-width="110px"><el-form-item :label="t('crmLead.createOpportunity')"><el-switch v-model="convertForm.createOpportunity" /></el-form-item><template v-if="convertForm.createOpportunity"><el-form-item :label="t('crmOpportunity.name')"><el-input v-model="convertForm.opportunityName" /></el-form-item><el-form-item :label="t('crmLead.estimatedAmount')"><el-input-number v-model="convertForm.amount" :min="0" :precision="2" style="width:100%" /></el-form-item><el-form-item :label="t('crmOpportunity.expectedCloseDate')"><el-date-picker v-model="convertForm.expectedCloseDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></template></el-form>
        <el-descriptions v-else :column="2" border :title="t('crmLead.convertConfirm')"><el-descriptions-item :label="t('crmLead.lead')">{{ convertTarget?.fullName }}</el-descriptions-item><el-descriptions-item :label="t('crmContact.customer')">{{ convertForm.customerMode === 'CREATE' ? convertForm.customerName : t('crmLead.existingCustomerNumber', { id: convertForm.customerId }) }}</el-descriptions-item><el-descriptions-item :label="t('crmCustomerOverview.contacts')">{{ convertForm.contactMode === 'CREATE' ? convertForm.contactName : t('crmLead.existingContactNumber', { id: convertForm.contactId }) }}</el-descriptions-item><el-descriptions-item :label="t('crmOpportunity.name')">{{ convertForm.createOpportunity ? convertForm.opportunityName : t('crmLead.doNotCreate') }}</el-descriptions-item></el-descriptions>
      </div>
      <template #footer><el-button v-if="convertStep > 0" @click="convertStep--">{{ t('crmLead.previous') }}</el-button><el-button v-if="convertStep < 3" type="primary" @click="convertStep++">{{ t('crmLead.next') }}</el-button><el-button v-else v-permission="'crm:lead:convert'" type="primary" @click="submitConversion">{{ t('crmLead.confirmConvert') }}</el-button></template>
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
