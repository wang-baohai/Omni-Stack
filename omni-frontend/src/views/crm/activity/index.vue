<script setup lang="ts">
/** CRM 跟进活动页面：计划、完成、取消、重新计划和纯文本详情。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  cancelActivity,
  completeActivity,
  createActivity,
  deleteActivity,
  getActivity,
  listActivities,
  rescheduleActivity,
  updateActivity,
  type ActivityRootType,
  type ActivityStatus,
  type CrmActivity,
} from '@/api/crm-activity'
import OwnerSelector from '@/components/crm/OwnerSelector.vue'
import CustomerPicker from '@/components/crm/CustomerPicker.vue'
import { usePermissionStore } from '@/stores/permission'
import { useDictOptions } from '@/composables/useDictOptions'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canViewPii = computed(() => permissionStore.hasPermission('crm:pii:view'))

const { options: activityTypeOptions } = useDictOptions('crm_activity_type')
const { options: activityStatusOptions } = useDictOptions('crm_activity_status')
const { options: rootTypeOptions } = useDictOptions('crm_root_type')

/** 字典 value → label 查找表 */
function labelOf(options: { value: string; label: string }[], value?: string) {
  return options.find(o => o.value === value)?.label ?? value ?? '-'
}
const loading = ref(false)
const tableData = ref<CrmActivity[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{ rootType?: ActivityRootType; rootId?: number; status?: ActivityStatus; ownerUserId?: number; timeRange: string[] }>({ timeRange: [] })

const formDialogVisible = ref(false)
const editingActivity = ref<CrmActivity | null>(null)
const formRef = ref<FormInstance>()
interface ActivityForm {
  rootType: ActivityRootType
  rootId: number
  contactId?: number
  activityType: string
  subject: string
  content: string
  status: ActivityStatus
  plannedStartTime: string
  plannedEndTime: string
  completedTime: string
  nextActionTime: string
  version?: number
}
const form = reactive<ActivityForm>({ rootType: 'LEAD', rootId: 0, activityType: 'CALL', subject: '', content: '', status: 'PLANNED', plannedStartTime: '', plannedEndTime: '', completedTime: '', nextActionTime: '' })
const rootCustomerId = ref<number>()
const formRules = computed<FormRules<ActivityForm>>(() => ({
  rootType: [{ required: true, message: t('crmActivity.rootTypeRequired'), trigger: 'change' }],
  rootId: [{ required: true, message: t('crmActivity.rootIdRequired'), trigger: 'blur' }],
  subject: [{ required: true, message: t('crmActivity.subjectRequired'), trigger: 'blur' }],
  activityType: [{ required: true, message: t('crmActivity.activityTypeRequired'), trigger: 'change' }],
}))

const detailVisible = ref(false)
const detailActivity = ref<CrmActivity | null>(null)
const completeVisible = ref(false)
const commandTarget = ref<CrmActivity | null>(null)
const completeForm = reactive({ completedTime: '', nextActionTime: '' })
const rescheduleVisible = ref(false)
const rescheduleForm = reactive({ plannedStartTime: '', plannedEndTime: '' })

async function loadData() {
  loading.value = true
  try {
    const response = await listActivities({
      rootType: query.rootType, rootId: query.rootId, status: query.status, ownerUserId: query.ownerUserId,
      fromTime: query.timeRange[0], toTime: query.timeRange[1], page: currentPage.value, size: pageSize.value,
    })
    tableData.value = response.data.data.records; total.value = response.data.data.total
  } finally { loading.value = false }
}
function search() { currentPage.value = 1; loadData() }
function resetQuery() { Object.assign(query, { rootType: undefined, rootId: undefined, status: undefined, ownerUserId: undefined, timeRange: [] }); search() }
function resetForm() { Object.assign(form, { rootType: 'LEAD', rootId: 0, contactId: undefined, activityType: 'CALL', subject: '', content: '', status: 'PLANNED', plannedStartTime: '', plannedEndTime: '', completedTime: '', nextActionTime: '', version: undefined }); rootCustomerId.value = undefined }
function openCreate() { editingActivity.value = null; resetForm(); formDialogVisible.value = true }
async function openEdit(row: CrmActivity) {
  const response = await getActivity(row.id)
  const detail = response.data.data
  editingActivity.value = detail
  Object.assign(form, { rootType: detail.rootType, rootId: detail.rootId, contactId: detail.contactId || undefined, activityType: detail.activityType, subject: detail.subject, content: canViewPii.value ? detail.content || '' : '', status: detail.status, plannedStartTime: detail.plannedStartTime || '', plannedEndTime: detail.plannedEndTime || '', completedTime: detail.completedTime || '', nextActionTime: detail.nextActionTime || '', version: detail.version })
  if (detail.rootType === 'CUSTOMER') rootCustomerId.value = detail.rootId
  formDialogVisible.value = true
}
function handleRootCustomer(value?: number) { rootCustomerId.value = value; form.rootId = value || 0 }
async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingActivity.value) {
    const payload = {
      activityType: form.activityType,
      subject: form.subject,
      contactId: form.contactId,
      plannedStartTime: form.plannedStartTime || undefined,
      plannedEndTime: form.plannedEndTime || undefined,
      nextActionTime: form.nextActionTime || undefined,
      version: form.version!,
      ...(canViewPii.value ? { content: form.content } : {}),
    }
    await updateActivity(editingActivity.value.id, payload)
  } else {
    await createActivity({
      rootType: form.rootType,
      rootId: form.rootId,
      contactId: form.contactId,
      activityType: form.activityType,
      subject: form.subject,
      status: form.status,
      plannedStartTime: form.plannedStartTime || undefined,
      plannedEndTime: form.plannedEndTime || undefined,
      completedTime: form.completedTime || undefined,
      nextActionTime: form.nextActionTime || undefined,
      ...(canViewPii.value ? { content: form.content } : {}),
    })
  }
  ElMessage.success(t('common.success')); formDialogVisible.value = false; loadData()
}
async function handleDelete(row: CrmActivity) { try { await ElMessageBox.confirm(t('crmActivity.deleteConfirm', { subject: row.subject }), t('crmActivity.deleteTitle'), { type: 'warning' }); await deleteActivity(row.id, row.version); ElMessage.success(t('common.success')); loadData() } catch { /* 用户取消 */ } }
function openComplete(row: CrmActivity) { commandTarget.value = row; Object.assign(completeForm, { completedTime: new Date().toLocaleString('sv-SE').replace('T', ' ').slice(0, 19), nextActionTime: '' }); completeVisible.value = true }
async function saveComplete() { if (!commandTarget.value || !completeForm.completedTime) return; await completeActivity(commandTarget.value.id, { completedTime: completeForm.completedTime, nextActionTime: completeForm.nextActionTime || undefined, version: commandTarget.value.version }); ElMessage.success(t('crmActivity.completed')); completeVisible.value = false; loadData() }
async function handleCancel(row: CrmActivity) { const result = await ElMessageBox.prompt(t('crmActivity.cancelReasonPrompt'), t('crmActivity.cancelActivity'), { inputPattern: /\S+/, inputErrorMessage: t('crmActivity.cancelReasonRequired') }); await cancelActivity(row.id, { reason: result.value, version: row.version }); ElMessage.success(t('crmActivity.cancelled')); loadData() }
function openReschedule(row: CrmActivity) { commandTarget.value = row; Object.assign(rescheduleForm, { plannedStartTime: row.plannedStartTime || '', plannedEndTime: row.plannedEndTime || '' }); rescheduleVisible.value = true }
async function saveReschedule() { if (!commandTarget.value || !rescheduleForm.plannedStartTime) return; await rescheduleActivity(commandTarget.value.id, { plannedStartTime: rescheduleForm.plannedStartTime, plannedEndTime: rescheduleForm.plannedEndTime || undefined, version: commandTarget.value.version }); ElMessage.success(t('crmActivity.rescheduled')); rescheduleVisible.value = false; loadData() }
async function showDetail(row: CrmActivity) {
  const response = await getActivity(row.id)
  detailActivity.value = response.data.data
  detailVisible.value = true
}
onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmActivities') }}</span><el-button v-permission="'crm:activity:create'" type="primary" @click="openCreate">{{ t('crmActivity.create') }}</el-button></div></template>
      <el-form inline class="search-form"><el-form-item :label="t('crmActivity.rootType')"><el-select v-model="query.rootType" clearable style="width:130px"><el-option v-for="o in rootTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item><el-form-item :label="t('crmActivity.recordId')"><el-input-number v-model="query.rootId" :min="1" controls-position="right" style="width:130px" /></el-form-item><el-form-item :label="t('common.status')"><el-select v-model="query.status" clearable style="width:120px"><el-option v-for="o in activityStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item><el-form-item :label="t('crmContact.owner')"><OwnerSelector v-model="query.ownerUserId" /></el-form-item><el-form-item :label="t('crmActivity.plannedTime')"><el-date-picker v-model="query.timeRange" type="datetimerange" :start-placeholder="t('crmActivity.startTime')" :end-placeholder="t('crmActivity.endTime')" value-format="YYYY-MM-DD HH:mm:ss" /></el-form-item><el-form-item><el-button type="primary" @click="search">{{ t('common.search') }}</el-button><el-button @click="resetQuery">{{ t('common.reset') }}</el-button></el-form-item></el-form>
      <el-table v-loading="loading" :data="tableData" border stripe><el-table-column prop="subject" :label="t('crmActivity.subject')" min-width="180"><template #default="{ row }"><el-link type="primary" @click="showDetail(row)">{{ row.subject }}</el-link></template></el-table-column><el-table-column :label="t('crmActivity.type')" width="100"><template #default="{ row }">{{ labelOf(activityTypeOptions, row.activityType) }}</template></el-table-column><el-table-column :label="t('crmActivity.relatedObject')" min-width="180"><template #default="{ row }">{{ labelOf(rootTypeOptions, row.rootType) }}：{{ row.rootName || `#${row.rootId}` }}</template></el-table-column><el-table-column :label="t('common.status')" width="100"><template #default="{ row }"><el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'CANCELLED' ? 'info' : 'primary'">{{ labelOf(activityStatusOptions, row.status) }}</el-tag></template></el-table-column><el-table-column prop="plannedStartTime" :label="t('crmActivity.plannedStart')" width="170" /><el-table-column prop="completedTime" :label="t('crmActivity.completedTime')" width="170" /><el-table-column :label="t('crmContact.owner')" width="110"><template #default="{ row }">{{ row.ownerName || t('crmUi.userNumber', { id: row.ownerUserId }) }}</template></el-table-column><el-table-column :label="t('common.actions')" fixed="right" width="320"><template #default="{ row }"><el-button size="small" @click="showDetail(row)">{{ t('crmActivity.details') }}</el-button><el-button v-if="row.status === 'PLANNED'" v-permission="'crm:activity:complete'" size="small" type="success" @click="openComplete(row)">{{ t('crmActivity.complete') }}</el-button><el-dropdown trigger="click"><el-button size="small">{{ t('crmActivity.more') }}<el-icon><ArrowDown /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-if="row.status === 'PLANNED'" v-permission="'crm:activity:update'" @click="openEdit(row)">{{ t('common.edit') }}</el-dropdown-item><el-dropdown-item v-if="row.status === 'PLANNED'" v-permission="'crm:activity:cancel'" @click="handleCancel(row)">{{ t('common.cancel') }}</el-dropdown-item><el-dropdown-item v-if="row.status === 'CANCELLED'" v-permission="'crm:activity:update'" @click="openReschedule(row)">{{ t('crmActivity.reschedule') }}</el-dropdown-item><el-dropdown-item v-permission="'crm:activity:delete'" divided @click="handleDelete(row)">{{ t('common.delete') }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown></template></el-table-column></el-table>
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[5, 10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" />
    </el-card>

    <el-dialog v-model="formDialogVisible" :title="editingActivity ? t('crmActivity.edit') : t('crmActivity.create')" width="700px"><el-form ref="formRef" :model="form" :rules="formRules" label-width="100px"><el-row :gutter="16"><el-col :span="12"><el-form-item :label="t('crmActivity.rootType')" prop="rootType"><el-select v-model="form.rootType" :disabled="Boolean(editingActivity)" style="width:100%"><el-option v-for="o in rootTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmActivity.relatedRecord')" prop="rootId"><CustomerPicker v-if="form.rootType === 'CUSTOMER'" :model-value="rootCustomerId" :disabled="Boolean(editingActivity)" @update:model-value="handleRootCustomer" /><el-input-number v-else v-model="form.rootId" :min="1" :disabled="Boolean(editingActivity)" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmActivity.activityType')" prop="activityType"><el-select v-model="form.activityType" style="width:100%"><el-option v-for="o in activityTypeOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('common.status')"><el-select v-model="form.status" :disabled="Boolean(editingActivity)" style="width:100%"><el-option v-for="o in activityStatusOptions" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col><el-col :span="24"><el-form-item :label="t('crmActivity.subject')" prop="subject"><el-input v-model="form.subject" /></el-form-item></el-col><el-col :span="24"><el-form-item :label="t('crmActivity.content')"><el-input v-model="form.content" type="textarea" :rows="4" :disabled="!canViewPii" :placeholder="t('crmActivity.plainTextOnly')" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmActivity.plannedStart')"><el-date-picker v-model="form.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmActivity.plannedEnd')"><el-date-picker v-model="form.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col><el-col v-if="form.status === 'COMPLETED'" :span="12"><el-form-item :label="t('crmActivity.completedTime')"><el-date-picker v-model="form.completedTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmActivity.nextAction')"><el-date-picker v-model="form.nextActionTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="editingActivity ? 'crm:activity:update' : 'crm:activity:create'" type="primary" @click="saveForm">{{ t('common.save') }}</el-button></template></el-dialog>

    <el-dialog v-model="completeVisible" :title="t('crmActivity.completeActivity')" width="500px"><el-form :model="completeForm" label-width="100px"><el-form-item :label="t('crmActivity.completedTime')"><el-date-picker v-model="completeForm.completedTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item><el-form-item :label="t('crmActivity.nextAction')"><el-date-picker v-model="completeForm.nextActionTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-form><template #footer><el-button @click="completeVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="'crm:activity:complete'" type="primary" @click="saveComplete">{{ t('crmActivity.confirmComplete') }}</el-button></template></el-dialog>
    <el-dialog v-model="rescheduleVisible" :title="t('crmActivity.rescheduleActivity')" width="520px"><el-form :model="rescheduleForm" label-width="100px"><el-form-item :label="t('crmActivity.plannedStart')"><el-date-picker v-model="rescheduleForm.plannedStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item><el-form-item :label="t('crmActivity.plannedEnd')"><el-date-picker v-model="rescheduleForm.plannedEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" /></el-form-item></el-form><template #footer><el-button @click="rescheduleVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="'crm:activity:update'" type="primary" @click="saveReschedule">{{ t('crmActivity.confirmReschedule') }}</el-button></template></el-dialog>
    <el-drawer v-model="detailVisible" :title="t('crmActivity.activityDetails')" size="560px"><el-descriptions v-if="detailActivity" :column="1" border><el-descriptions-item :label="t('crmActivity.subject')">{{ detailActivity.subject }}</el-descriptions-item><el-descriptions-item :label="t('crmActivity.type')">{{ labelOf(activityTypeOptions, detailActivity.activityType) }}</el-descriptions-item><el-descriptions-item :label="t('common.status')">{{ labelOf(activityStatusOptions, detailActivity.status) }}</el-descriptions-item><el-descriptions-item :label="t('crmActivity.relatedObject')">{{ labelOf(rootTypeOptions, detailActivity.rootType) }}：{{ detailActivity.rootName || `#${detailActivity.rootId}` }}</el-descriptions-item><el-descriptions-item :label="t('crmActivity.plannedTime')">{{ detailActivity.plannedStartTime || '-' }} {{ t('crmActivity.to') }} {{ detailActivity.plannedEndTime || '-' }}</el-descriptions-item><el-descriptions-item :label="t('crmActivity.content')"><p class="plain-content">{{ detailActivity.content || '-' }}</p></el-descriptions-item><el-descriptions-item :label="t('crmActivity.performer')">{{ detailActivity.performedByName || (detailActivity.performedByUserId ? t('crmUi.userNumber', { id: detailActivity.performedByUserId }) : '-') }}</el-descriptions-item></el-descriptions></el-drawer>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
.plain-content { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
