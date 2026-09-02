<script setup lang="ts">
/** 供应商联系人、资质和银行账户维护抽屉。 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { usePermissionStore } from '@/stores/permission'
import {
  createBankAccount,
  createContact,
  createQualification,
  deleteBankAccount,
  deleteContact,
  deleteQualification,
  listBankAccounts,
  listContacts,
  listQualifications,
  updateBankAccount,
  updateContact,
  updateQualification,
  type SaveBankAccountRequest,
  type SaveContactRequest,
  type SaveQualificationRequest,
  type SrmBankAccount,
  type SrmContact,
  type SrmQualification,
} from '@/api/srm-supplier'

const props = defineProps<{
  modelValue: boolean
  supplierId?: number
  supplierName?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const { t } = useI18n()
const permissionStore = usePermissionStore()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const activeTab = ref('contact')
const contacts = ref<SrmContact[]>([])
const qualifications = ref<SrmQualification[]>([])
const bankAccounts = ref<SrmBankAccount[]>([])

const canListContact = computed(() => permissionStore.hasPermission('srm:contact:list'))
const canListQualification = computed(() => permissionStore.hasPermission('srm:qualification:list'))
const canListBank = computed(() => permissionStore.hasPermission('srm:bank-account:list'))

async function loadResources() {
  if (!props.supplierId || !visible.value) return
  if (canListContact.value) activeTab.value = 'contact'
  else if (canListQualification.value) activeTab.value = 'qualification'
  else if (canListBank.value) activeTab.value = 'bank'
  loading.value = true
  try {
    const tasks: Promise<void>[] = []
    if (canListContact.value) {
      tasks.push(listContacts(props.supplierId).then((response) => { contacts.value = response.data.data }))
    }
    if (canListQualification.value) {
      tasks.push(listQualifications(props.supplierId).then((response) => { qualifications.value = response.data.data }))
    }
    if (canListBank.value) {
      tasks.push(listBankAccounts(props.supplierId).then((response) => { bankAccounts.value = response.data.data }))
    }
    await Promise.all(tasks)
  } finally {
    loading.value = false
  }
}

watch(() => [props.modelValue, props.supplierId], loadResources)

const contactDialogVisible = ref(false)
const contactFormRef = ref<FormInstance>()
const editingContact = ref<SrmContact | null>(null)
const contactForm = reactive<SaveContactRequest>({ name: '', primaryFlag: false })
const contactRules = computed<FormRules<SaveContactRequest>>(() => ({
  name: [
    { required: true, message: t('srmResourcesMessages.contactNameRequired'), trigger: 'blur' },
    { max: 100, message: t('srmResourcesMessages.contactNameLength'), trigger: 'blur' },
  ],
  department: [{ max: 100, message: t('srmResourcesMessages.departmentLength'), trigger: 'blur' }],
  jobTitle: [{ max: 100, message: t('srmResourcesMessages.jobTitleLength'), trigger: 'blur' }],
  mobile: [{ max: 32, message: t('srmResourcesMessages.mobileLength'), trigger: 'blur' }],
  phone: [{ max: 32, message: t('srmResourcesMessages.phoneLength'), trigger: 'blur' }],
  email: [
    { type: 'email', message: t('srmResourcesMessages.emailInvalid'), trigger: 'blur' },
    { max: 200, message: t('srmResourcesMessages.emailLength'), trigger: 'blur' },
  ],
  decisionRole: [{ max: 50, message: t('srmResourcesMessages.decisionRoleLength'), trigger: 'blur' }],
}))
const maskedContactFields = ref(new Set<'mobile' | 'phone' | 'email'>())
const maskedPlaceholder = computed(() => t('srmResourcesMessages.maskedPlaceholder'))

function openContact(row?: SrmContact) {
  editingContact.value = row || null
  maskedContactFields.value = new Set()
  if (row?.mobile?.includes('*')) maskedContactFields.value.add('mobile')
  if (row?.phone?.includes('*')) maskedContactFields.value.add('phone')
  if (row?.email?.includes('*')) maskedContactFields.value.add('email')
  Object.assign(contactForm, {
    name: row?.name || '',
    department: row?.department || '',
    jobTitle: row?.jobTitle || '',
    mobile: maskedContactFields.value.has('mobile') ? '' : row?.mobile || '',
    phone: maskedContactFields.value.has('phone') ? '' : row?.phone || '',
    email: maskedContactFields.value.has('email') ? '' : row?.email || '',
    decisionRole: row?.decisionRole || '',
    primaryFlag: row?.primaryFlag || false,
    version: row?.version,
  })
  contactDialogVisible.value = true
}

async function saveContact() {
  if (!props.supplierId || !(await contactFormRef.value?.validate().catch(() => false))) return
  const request = { ...contactForm }
  if (maskedContactFields.value.has('mobile') && !request.mobile) request.mobile = undefined
  if (maskedContactFields.value.has('phone') && !request.phone) request.phone = undefined
  if (maskedContactFields.value.has('email') && !request.email) request.email = undefined
  if (editingContact.value) {
    await updateContact(props.supplierId, editingContact.value.id, request)
  } else {
    await createContact(props.supplierId, { ...request, version: undefined })
  }
  ElMessage.success(t('srmResourcesMessages.contactSaved'))
  contactDialogVisible.value = false
  await loadResources()
}

async function removeContact(row: SrmContact) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(
      t('srmResourcesMessages.contactDeleteConfirm', { name: row.name }),
      t('srmResourcesMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteContact(props.supplierId, row.id, row.version)
    ElMessage.success(t('srmResourcesMessages.contactDeleted'))
    await loadResources()
  } catch { /* 用户取消 */ }
}

const qualificationDialogVisible = ref(false)
const qualificationFormRef = ref<FormInstance>()
const editingQualification = ref<SrmQualification | null>(null)
const qualificationForm = reactive<SaveQualificationRequest>({ qualificationName: '' })
const qualificationRules = computed<FormRules<SaveQualificationRequest>>(() => ({
  qualificationName: [
    { required: true, message: t('srmResourcesMessages.qualificationNameRequired'), trigger: 'blur' },
    { max: 200, message: t('srmResourcesMessages.qualificationNameLength'), trigger: 'blur' },
  ],
  certificateNo: [{ max: 100, message: t('srmResourcesMessages.certificateNoLength'), trigger: 'blur' }],
  issuingAuthority: [{ max: 200, message: t('srmResourcesMessages.issuingAuthorityLength'), trigger: 'blur' }],
}))

function openQualification(row?: SrmQualification) {
  editingQualification.value = row || null
  Object.assign(qualificationForm, {
    qualificationName: row?.qualificationName || '',
    certificateNo: row?.certificateNo || '',
    issuingAuthority: row?.issuingAuthority || '',
    issueDate: row?.issueDate || '',
    expiryDate: row?.expiryDate || '',
    version: row?.version,
  })
  qualificationDialogVisible.value = true
}

async function saveQualification() {
  if (!props.supplierId || !(await qualificationFormRef.value?.validate().catch(() => false))) return
  if (qualificationForm.issueDate && qualificationForm.expiryDate
    && qualificationForm.expiryDate < qualificationForm.issueDate) {
    ElMessage.warning(t('srmResourcesMessages.expiryBeforeIssue'))
    return
  }
  if (editingQualification.value) {
    await updateQualification(props.supplierId, editingQualification.value.id, { ...qualificationForm })
  } else {
    await createQualification(props.supplierId, { ...qualificationForm, version: undefined })
  }
  ElMessage.success(t('srmResourcesMessages.qualificationSaved'))
  qualificationDialogVisible.value = false
  await loadResources()
}

async function removeQualification(row: SrmQualification) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(
      t('srmResourcesMessages.qualificationDeleteConfirm', { name: row.qualificationName }),
      t('srmResourcesMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteQualification(props.supplierId, row.id, row.version)
    ElMessage.success(t('srmResourcesMessages.qualificationDeleted'))
    await loadResources()
  } catch { /* 用户取消 */ }
}

const bankDialogVisible = ref(false)
const bankFormRef = ref<FormInstance>()
const editingBank = ref<SrmBankAccount | null>(null)
const bankForm = reactive<SaveBankAccountRequest>({ accountName: '', accountNo: '', bankName: '', primaryFlag: false })
const maskedBankAccount = ref(false)
const bankRules = computed<FormRules<SaveBankAccountRequest>>(() => ({
  accountName: [
    { required: true, message: t('srmResourcesMessages.accountNameRequired'), trigger: 'blur' },
    { max: 200, message: t('srmResourcesMessages.accountNameLength'), trigger: 'blur' },
  ],
  accountNo: [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value && !editingBank.value) callback(new Error(t('srmResourcesMessages.accountNoRequired')))
      else if (value && value.length > 100) callback(new Error(t('srmResourcesMessages.accountNoLength')))
      else callback()
    },
    trigger: 'blur',
  }],
  bankName: [
    { required: true, message: t('srmResourcesMessages.bankNameRequired'), trigger: 'blur' },
    { max: 200, message: t('srmResourcesMessages.bankNameLength'), trigger: 'blur' },
  ],
  bankBranch: [{ max: 200, message: t('srmResourcesMessages.bankBranchLength'), trigger: 'blur' }],
  bankCode: [{ max: 50, message: t('srmResourcesMessages.bankCodeLength'), trigger: 'blur' }],
}))

function openBank(row?: SrmBankAccount) {
  editingBank.value = row || null
  maskedBankAccount.value = Boolean(row?.accountNo?.includes('*'))
  Object.assign(bankForm, {
    accountName: row?.accountName || '',
    accountNo: maskedBankAccount.value ? '' : row?.accountNo || '',
    bankName: row?.bankName || '',
    bankBranch: row?.bankBranch || '',
    bankCode: row?.bankCode || '',
    primaryFlag: row?.primaryFlag || false,
    version: row?.version,
  })
  bankDialogVisible.value = true
}

async function saveBank() {
  if (!props.supplierId || !(await bankFormRef.value?.validate().catch(() => false))) return
  const request = { ...bankForm }
  if (maskedBankAccount.value && !request.accountNo) request.accountNo = undefined
  if (editingBank.value) {
    await updateBankAccount(props.supplierId, editingBank.value.id, request)
  } else {
    await createBankAccount(props.supplierId, { ...request, version: undefined })
  }
  ElMessage.success(t('srmResourcesMessages.bankSaved'))
  bankDialogVisible.value = false
  await loadResources()
}

async function removeBank(row: SrmBankAccount) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(
      t('srmResourcesMessages.bankDeleteConfirm', { name: row.accountName }),
      t('srmResourcesMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteBankAccount(props.supplierId, row.id, row.version)
    ElMessage.success(t('srmResourcesMessages.bankDeleted'))
    await loadResources()
  } catch { /* 用户取消 */ }
}
</script>

<template>
  <el-drawer
    v-model="visible"
    :title="t('srmResources.title', { name: supplierName || t('srmResources.supplier') })"
    size="860px"
    destroy-on-close
  >
    <el-tabs v-model="activeTab" v-loading="loading">
      <el-tab-pane v-if="canListContact" :label="t('srmResources.contacts')" name="contact">
        <div class="toolbar">
          <el-button v-permission="'srm:contact:create'" type="primary" @click="openContact()">
            {{ t('srmResources.createContact') }}
          </el-button>
        </div>
        <el-table :data="contacts" border stripe>
          <el-table-column prop="name" :label="t('srmSupplierOverview.contactName')" width="110" />
          <el-table-column prop="department" :label="t('srmResources.department')" width="110" />
          <el-table-column prop="jobTitle" :label="t('srmSupplierOverview.jobTitle')" width="110" />
          <el-table-column prop="mobile" :label="t('srmSupplierOverview.mobile')" width="130" />
          <el-table-column prop="email" :label="t('srmSupplierOverview.email')" min-width="160" />
          <el-table-column :label="t('srmSupplierOverview.primary')" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.primaryFlag" type="success">{{ t('srmResources.yes') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:contact:update'" link type="primary" @click="openContact(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-permission="'srm:contact:delete'" link type="danger" @click="removeContact(row)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canListQualification" :label="t('srmResources.qualifications')" name="qualification">
        <div class="toolbar">
          <el-button v-permission="'srm:qualification:create'" type="primary" @click="openQualification()">
            {{ t('srmResources.createQualification') }}
          </el-button>
        </div>
        <el-table :data="qualifications" border stripe>
          <el-table-column prop="qualificationName" :label="t('srmSupplierOverview.qualificationName')" min-width="150" />
          <el-table-column prop="certificateNo" :label="t('srmSupplierOverview.certificateNo')" min-width="140" />
          <el-table-column prop="issuingAuthority" :label="t('srmSupplierOverview.issuingAuthority')" min-width="140" />
          <el-table-column prop="issueDate" :label="t('srmResources.issueDate')" width="110" />
          <el-table-column prop="expiryDate" :label="t('srmSupplierOverview.expiryDate')" width="110" />
          <el-table-column prop="status" :label="t('srmSupplierOverview.status')" width="90" />
          <el-table-column :label="t('common.actions')" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:qualification:update'" link type="primary" @click="openQualification(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-permission="'srm:qualification:delete'" link type="danger" @click="removeQualification(row)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canListBank" :label="t('srmResources.bankAccounts')" name="bank">
        <div class="toolbar">
          <el-button v-permission="'srm:bank-account:create'" type="primary" @click="openBank()">
            {{ t('srmResources.createBank') }}
          </el-button>
        </div>
        <el-table :data="bankAccounts" border stripe>
          <el-table-column prop="accountName" :label="t('srmSupplierOverview.accountName')" min-width="140" />
          <el-table-column prop="accountNo" :label="t('srmSupplierOverview.accountNo')" min-width="180" />
          <el-table-column prop="bankName" :label="t('srmSupplierOverview.bankName')" min-width="140" />
          <el-table-column prop="bankBranch" :label="t('srmSupplierOverview.bankBranch')" min-width="140" />
          <el-table-column :label="t('srmResources.default')" width="70" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.primaryFlag" type="success">{{ t('srmResources.yes') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:bank-account:update'" link type="primary" @click="openBank(row)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-permission="'srm:bank-account:delete'" link type="danger" @click="removeBank(row)">
                {{ t('common.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="contactDialogVisible"
      :title="editingContact ? t('srmResources.editContact') : t('srmResources.createContact')"
      width="620px"
      append-to-body
    >
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('srmSupplierOverview.contactName')" prop="name">
              <el-input v-model="contactForm.name" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item :label="t('srmResources.department')" prop="department"><el-input v-model="contactForm.department" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.jobTitle')" prop="jobTitle"><el-input v-model="contactForm.jobTitle" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmResources.decisionRole')" prop="decisionRole"><el-input v-model="contactForm.decisionRole" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.mobile')" prop="mobile"><el-input v-model="contactForm.mobile" maxlength="32" :placeholder="maskedContactFields.has('mobile') ? maskedPlaceholder : ''" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmResources.phone')" prop="phone"><el-input v-model="contactForm.phone" maxlength="32" :placeholder="maskedContactFields.has('phone') ? maskedPlaceholder : ''" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('srmSupplierOverview.email')" prop="email"><el-input v-model="contactForm.email" maxlength="200" :placeholder="maskedContactFields.has('email') ? maskedPlaceholder : ''" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('srmResources.primaryContact')"><el-switch v-model="contactForm.primaryFlag" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="editingContact ? 'srm:contact:update' : 'srm:contact:create'" type="primary" @click="saveContact">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="qualificationDialogVisible"
      :title="editingQualification ? t('srmResources.editQualification') : t('srmResources.createQualification')"
      width="620px"
      append-to-body
    >
      <el-form ref="qualificationFormRef" :model="qualificationForm" :rules="qualificationRules" label-width="90px">
        <el-form-item :label="t('srmSupplierOverview.qualificationName')" prop="qualificationName"><el-input v-model="qualificationForm.qualificationName" maxlength="200" /></el-form-item>
        <el-form-item :label="t('srmSupplierOverview.certificateNo')" prop="certificateNo"><el-input v-model="qualificationForm.certificateNo" maxlength="100" /></el-form-item>
        <el-form-item :label="t('srmSupplierOverview.issuingAuthority')" prop="issuingAuthority"><el-input v-model="qualificationForm.issuingAuthority" maxlength="200" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item :label="t('srmResources.issueDate')"><el-date-picker v-model="qualificationForm.issueDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.expiryDate')"><el-date-picker v-model="qualificationForm.expiryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="qualificationDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="editingQualification ? 'srm:qualification:update' : 'srm:qualification:create'" type="primary" @click="saveQualification">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="bankDialogVisible"
      :title="editingBank ? t('srmResources.editBank') : t('srmResources.createBank')"
      width="620px"
      append-to-body
    >
      <el-form ref="bankFormRef" :model="bankForm" :rules="bankRules" label-width="90px">
        <el-form-item :label="t('srmResources.accountName')" prop="accountName"><el-input v-model="bankForm.accountName" maxlength="200" /></el-form-item>
        <el-form-item :label="t('srmSupplierOverview.accountNo')" prop="accountNo"><el-input v-model="bankForm.accountNo" maxlength="100" :placeholder="maskedBankAccount ? maskedPlaceholder : ''" /></el-form-item>
        <el-form-item :label="t('srmSupplierOverview.bankName')" prop="bankName"><el-input v-model="bankForm.bankName" maxlength="200" /></el-form-item>
        <el-form-item :label="t('srmSupplierOverview.bankBranch')" prop="bankBranch"><el-input v-model="bankForm.bankBranch" maxlength="200" /></el-form-item>
        <el-form-item :label="t('srmResources.bankCode')" prop="bankCode"><el-input v-model="bankForm.bankCode" maxlength="50" /></el-form-item>
        <el-form-item :label="t('srmResources.defaultAccount')"><el-switch v-model="bankForm.primaryFlag" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bankDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="editingBank ? 'srm:bank-account:update' : 'srm:bank-account:create'" type="primary" @click="saveBank">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
</style>
