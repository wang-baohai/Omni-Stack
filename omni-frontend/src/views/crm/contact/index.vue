<script setup lang="ts">
/** CRM 联系人管理页面。联系人创建必须选择当前可访问客户。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { createContact, deleteContact, getContact, listContacts, setPrimaryContact, updateContact, type CrmContact } from '@/api/crm-contact'
import CustomerPicker from '@/components/crm/CustomerPicker.vue'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const canViewPii = computed(() => permissionStore.hasPermission('crm:pii:view'))
const loading = ref(false)
const tableData = ref<CrmContact[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive({ keyword: '', customerId: undefined as number | undefined, status: undefined as number | undefined })
const formDialogVisible = ref(false)
const editingContact = ref<CrmContact | null>(null)
const formCustomerId = ref<number>()
const formRef = ref<FormInstance>()
interface ContactForm {
  name: string
  department: string
  jobTitle: string
  mobile: string
  phone: string
  email: string
  decisionRole: string
  primary: boolean
  status: number
  version?: number
}
const form = reactive<ContactForm>({ name: '', department: '', jobTitle: '', mobile: '', phone: '', email: '', decisionRole: '', primary: false, status: 1 })
const rules = computed<FormRules<ContactForm>>(() => ({ name: [{ required: true, message: t('crmContact.nameRequired'), trigger: 'blur' }], email: [{ type: 'email', message: t('validation.emailFormat'), trigger: 'blur' }] }))

async function loadData() {
  loading.value = true
  try {
    const response = await listContacts({ keyword: query.keyword || undefined, customerId: query.customerId, status: query.status, page: currentPage.value, size: pageSize.value })
    tableData.value = response.data.data.records; total.value = response.data.data.total
  } finally { loading.value = false }
}
function search() { currentPage.value = 1; loadData() }
function resetQuery() { Object.assign(query, { keyword: '', customerId: undefined, status: undefined }); search() }
function resetForm() { formCustomerId.value = undefined; Object.assign(form, { name: '', department: '', jobTitle: '', mobile: '', phone: '', email: '', decisionRole: '', primary: false, status: 1, version: undefined }) }
function openCreate() { editingContact.value = null; resetForm(); formDialogVisible.value = true }
async function openEdit(row: CrmContact) {
  const response = await getContact(row.id)
  const detail = response.data.data
  editingContact.value = detail
  formCustomerId.value = detail.customerId
  Object.assign(form, { name: detail.name, department: detail.department || '', jobTitle: detail.jobTitle || '', mobile: canViewPii.value ? detail.mobile || '' : '', phone: canViewPii.value ? detail.phone || '' : '', email: canViewPii.value ? detail.email || '' : '', decisionRole: detail.decisionRole || '', primary: detail.primaryFlag === 1, status: detail.status, version: detail.version })
  formDialogVisible.value = true
}
async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !formCustomerId.value) return !formCustomerId.value && ElMessage.warning(t('crmUi.selectCustomer'))
  const base = { name: form.name, department: form.department, jobTitle: form.jobTitle, decisionRole: form.decisionRole }
  const sensitive = canViewPii.value ? { mobile: form.mobile, phone: form.phone, email: form.email } : {}
  if (editingContact.value) await updateContact(editingContact.value.id, { ...base, ...sensitive, status: form.status, version: form.version! })
  else await createContact(formCustomerId.value, { ...base, ...sensitive, primary: form.primary })
  ElMessage.success(t('common.success')); formDialogVisible.value = false; loadData()
}
async function handleDelete(row: CrmContact) { try { await ElMessageBox.confirm(t('crmContact.deleteConfirm', { name: row.name }), t('crmContact.deleteTitle'), { type: 'warning' }); await deleteContact(row.id, row.version); ElMessage.success(t('common.success')); loadData() } catch { /* 用户取消 */ } }
async function handlePrimary(row: CrmContact) { await setPrimaryContact(row.id, row.version); ElMessage.success(t('crmContact.primaryUpdated')); loadData() }
onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header><div class="card-header"><span>{{ t('common.crmContacts') }}</span><el-button v-permission="'crm:contact:create'" type="primary" @click="openCreate">{{ t('crmContact.create') }}</el-button></div></template>
      <el-form inline class="search-form"><el-form-item :label="t('crmContact.keyword')"><el-input v-model="query.keyword" clearable :placeholder="t('crmContact.keywordPlaceholder')" /></el-form-item><el-form-item :label="t('crmContact.customer')"><CustomerPicker v-model="query.customerId" /></el-form-item><el-form-item :label="t('common.status')"><el-select v-model="query.status" clearable style="width:120px"><el-option :label="t('common.enabled')" :value="1" /><el-option :label="t('common.disabled')" :value="0" /></el-select></el-form-item><el-form-item><el-button type="primary" @click="search">{{ t('common.search') }}</el-button><el-button @click="resetQuery">{{ t('common.reset') }}</el-button></el-form-item></el-form>
      <el-table v-loading="loading" :data="tableData" border stripe><el-table-column prop="name" :label="t('crmContact.name')" width="120" /><el-table-column :label="t('crmContact.customer')" min-width="130"><template #default="{ row }">{{ row.customerName || t('crmUi.customerNumber', { id: row.customerId }) }}</template></el-table-column><el-table-column prop="department" :label="t('crmContact.department')" width="120" /><el-table-column prop="jobTitle" :label="t('crmContact.jobTitle')" width="120" /><el-table-column prop="mobile" :label="t('crmContact.mobile')" width="140" /><el-table-column prop="email" :label="t('crmContact.email')" min-width="180" /><el-table-column prop="decisionRole" :label="t('crmContact.decisionRole')" width="110" /><el-table-column :label="t('crmContact.primaryContact')" width="100"><template #default="{ row }"><el-tag v-if="row.primaryFlag === 1" type="success">{{ t('common.yes') }}</el-tag><el-button v-else v-permission="'crm:contact:update'" link type="primary" @click="handlePrimary(row)">{{ t('crmContact.setPrimary') }}</el-button></template></el-table-column><el-table-column :label="t('crmContact.owner')" width="110"><template #default="{ row }">{{ row.ownerName || t('crmUi.userNumber', { id: row.ownerUserId }) }}</template></el-table-column><el-table-column :label="t('common.actions')" fixed="right" width="150"><template #default="{ row }"><el-button v-permission="'crm:contact:update'" size="small" @click="openEdit(row)">{{ t('common.edit') }}</el-button><el-button v-permission="'crm:contact:delete'" size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button></template></el-table-column></el-table>
      <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" class="pagination" :total="total" :page-sizes="[5, 10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="loadData" @size-change="currentPage = 1; loadData()" />
    </el-card>
    <el-dialog v-model="formDialogVisible" :title="editingContact ? t('crmContact.edit') : t('crmContact.create')" width="680px"><el-form ref="formRef" :model="form" :rules="rules" label-width="90px"><el-form-item :label="t('crmContact.customer')"><CustomerPicker v-model="formCustomerId" :disabled="Boolean(editingContact)" /></el-form-item><el-row :gutter="16"><el-col :span="12"><el-form-item :label="t('crmContact.name')" prop="name"><el-input v-model="form.name" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.department')"><el-input v-model="form.department" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.jobTitle')"><el-input v-model="form.jobTitle" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.decisionRole')"><el-input v-model="form.decisionRole" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.mobile')"><el-input v-model="form.mobile" :disabled="!canViewPii" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.phone')"><el-input v-model="form.phone" :disabled="!canViewPii" /></el-form-item></el-col><el-col :span="12"><el-form-item :label="t('crmContact.email')" prop="email"><el-input v-model="form.email" :disabled="!canViewPii" /></el-form-item></el-col><el-col v-if="editingContact" :span="6"><el-form-item :label="t('common.enabled')"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item></el-col><el-col v-else :span="6"><el-form-item :label="t('crmContact.primary')"><el-switch v-model="form.primary" /></el-form-item></el-col></el-row></el-form><template #footer><el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button><el-button v-permission="editingContact ? 'crm:contact:update' : 'crm:contact:create'" type="primary" @click="saveForm">{{ t('common.save') }}</el-button></template></el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
</style>
