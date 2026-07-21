<script setup lang="ts">
/** 供应商联系人、资质和银行账户维护抽屉。 */
import { computed, reactive, ref, watch } from 'vue'
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
const contactRules: FormRules<SaveContactRequest> = {
  name: [
    { required: true, message: '请输入联系人姓名', trigger: 'blur' },
    { max: 100, message: '联系人姓名不能超过 100 个字符', trigger: 'blur' },
  ],
  department: [{ max: 100, message: '部门不能超过 100 个字符', trigger: 'blur' }],
  jobTitle: [{ max: 100, message: '职务不能超过 100 个字符', trigger: 'blur' }],
  mobile: [{ max: 32, message: '手机号码不能超过 32 个字符', trigger: 'blur' }],
  phone: [{ max: 32, message: '电话号码不能超过 32 个字符', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 200, message: '邮箱不能超过 200 个字符', trigger: 'blur' },
  ],
  decisionRole: [{ max: 50, message: '决策角色不能超过 50 个字符', trigger: 'blur' }],
}
const maskedContactFields = ref(new Set<'mobile' | 'phone' | 'email'>())

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
  ElMessage.success('联系人保存成功')
  contactDialogVisible.value = false
  await loadResources()
}

async function removeContact(row: SrmContact) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(`确认删除联系人“${row.name}”？`, '删除确认', { type: 'warning' })
    await deleteContact(props.supplierId, row.id, row.version)
    ElMessage.success('联系人已删除')
    await loadResources()
  } catch { /* 用户取消 */ }
}

const qualificationDialogVisible = ref(false)
const qualificationFormRef = ref<FormInstance>()
const editingQualification = ref<SrmQualification | null>(null)
const qualificationForm = reactive<SaveQualificationRequest>({ qualificationName: '' })
const qualificationRules: FormRules<SaveQualificationRequest> = {
  qualificationName: [
    { required: true, message: '请输入资质名称', trigger: 'blur' },
    { max: 200, message: '资质名称不能超过 200 个字符', trigger: 'blur' },
  ],
  certificateNo: [{ max: 100, message: '证书编号不能超过 100 个字符', trigger: 'blur' }],
  issuingAuthority: [{ max: 200, message: '发证机关不能超过 200 个字符', trigger: 'blur' }],
}

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
    ElMessage.warning('资质到期日期不能早于发证日期')
    return
  }
  if (editingQualification.value) {
    await updateQualification(props.supplierId, editingQualification.value.id, { ...qualificationForm })
  } else {
    await createQualification(props.supplierId, { ...qualificationForm, version: undefined })
  }
  ElMessage.success('资质保存成功')
  qualificationDialogVisible.value = false
  await loadResources()
}

async function removeQualification(row: SrmQualification) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(`确认删除资质“${row.qualificationName}”？`, '删除确认', { type: 'warning' })
    await deleteQualification(props.supplierId, row.id, row.version)
    ElMessage.success('资质已删除')
    await loadResources()
  } catch { /* 用户取消 */ }
}

const bankDialogVisible = ref(false)
const bankFormRef = ref<FormInstance>()
const editingBank = ref<SrmBankAccount | null>(null)
const bankForm = reactive<SaveBankAccountRequest>({ accountName: '', accountNo: '', bankName: '', primaryFlag: false })
const maskedBankAccount = ref(false)
const bankRules: FormRules<SaveBankAccountRequest> = {
  accountName: [
    { required: true, message: '请输入账户名', trigger: 'blur' },
    { max: 200, message: '账户名不能超过 200 个字符', trigger: 'blur' },
  ],
  accountNo: [{
    validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
      if (!value && !editingBank.value) callback(new Error('请输入银行账号'))
      else if (value && value.length > 100) callback(new Error('银行账号不能超过 100 个字符'))
      else callback()
    },
    trigger: 'blur',
  }],
  bankName: [
    { required: true, message: '请输入银行名称', trigger: 'blur' },
    { max: 200, message: '银行名称不能超过 200 个字符', trigger: 'blur' },
  ],
  bankBranch: [{ max: 200, message: '开户支行不能超过 200 个字符', trigger: 'blur' }],
  bankCode: [{ max: 50, message: '银行代码不能超过 50 个字符', trigger: 'blur' }],
}

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
  ElMessage.success('银行账户保存成功')
  bankDialogVisible.value = false
  await loadResources()
}

async function removeBank(row: SrmBankAccount) {
  if (!props.supplierId) return
  try {
    await ElMessageBox.confirm(`确认删除银行账户“${row.accountName}”？`, '删除确认', { type: 'warning' })
    await deleteBankAccount(props.supplierId, row.id, row.version)
    ElMessage.success('银行账户已删除')
    await loadResources()
  } catch { /* 用户取消 */ }
}
</script>

<template>
  <el-drawer v-model="visible" :title="`${supplierName || '供应商'} · 资料维护`" size="860px" destroy-on-close>
    <el-tabs v-model="activeTab" v-loading="loading">
      <el-tab-pane v-if="canListContact" label="联系人" name="contact">
        <div class="toolbar">
          <el-button v-permission="'srm:contact:create'" type="primary" @click="openContact()">新增联系人</el-button>
        </div>
        <el-table :data="contacts" border stripe>
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="department" label="部门" width="110" />
          <el-table-column prop="jobTitle" label="职务" width="110" />
          <el-table-column prop="mobile" label="手机" width="130" />
          <el-table-column prop="email" label="邮箱" min-width="160" />
          <el-table-column label="主要" width="70" align="center">
            <template #default="{ row }"><el-tag v-if="row.primaryFlag" type="success">是</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:contact:update'" link type="primary" @click="openContact(row)">编辑</el-button>
              <el-button v-permission="'srm:contact:delete'" link type="danger" @click="removeContact(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canListQualification" label="资质" name="qualification">
        <div class="toolbar">
          <el-button v-permission="'srm:qualification:create'" type="primary" @click="openQualification()">新增资质</el-button>
        </div>
        <el-table :data="qualifications" border stripe>
          <el-table-column prop="qualificationName" label="资质名称" min-width="150" />
          <el-table-column prop="certificateNo" label="证书编号" min-width="140" />
          <el-table-column prop="issuingAuthority" label="发证机关" min-width="140" />
          <el-table-column prop="issueDate" label="发证日期" width="110" />
          <el-table-column prop="expiryDate" label="到期日期" width="110" />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:qualification:update'" link type="primary" @click="openQualification(row)">编辑</el-button>
              <el-button v-permission="'srm:qualification:delete'" link type="danger" @click="removeQualification(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canListBank" label="银行账户" name="bank">
        <div class="toolbar">
          <el-button v-permission="'srm:bank-account:create'" type="primary" @click="openBank()">新增银行账户</el-button>
        </div>
        <el-table :data="bankAccounts" border stripe>
          <el-table-column prop="accountName" label="账户名" min-width="140" />
          <el-table-column prop="accountNo" label="银行账号" min-width="180" />
          <el-table-column prop="bankName" label="银行" min-width="140" />
          <el-table-column prop="bankBranch" label="支行" min-width="140" />
          <el-table-column label="默认" width="70" align="center">
            <template #default="{ row }"><el-tag v-if="row.primaryFlag" type="success">是</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'srm:bank-account:update'" link type="primary" @click="openBank(row)">编辑</el-button>
              <el-button v-permission="'srm:bank-account:delete'" link type="danger" @click="removeBank(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="contactDialogVisible" :title="editingContact ? '编辑联系人' : '新增联系人'" width="620px" append-to-body>
      <el-form ref="contactFormRef" :model="contactForm" :rules="contactRules" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="姓名" prop="name"><el-input v-model="contactForm.name" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="部门" prop="department"><el-input v-model="contactForm.department" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="职务" prop="jobTitle"><el-input v-model="contactForm.jobTitle" maxlength="100" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="决策角色" prop="decisionRole"><el-input v-model="contactForm.decisionRole" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="手机" prop="mobile"><el-input v-model="contactForm.mobile" maxlength="32" :placeholder="maskedContactFields.has('mobile') ? '已脱敏，留空保持不变' : ''" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话" prop="phone"><el-input v-model="contactForm.phone" maxlength="32" :placeholder="maskedContactFields.has('phone') ? '已脱敏，留空保持不变' : ''" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="邮箱" prop="email"><el-input v-model="contactForm.email" maxlength="200" :placeholder="maskedContactFields.has('email') ? '已脱敏，留空保持不变' : ''" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="主要联系人"><el-switch v-model="contactForm.primaryFlag" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button v-permission="editingContact ? 'srm:contact:update' : 'srm:contact:create'" type="primary" @click="saveContact">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qualificationDialogVisible" :title="editingQualification ? '编辑资质' : '新增资质'" width="620px" append-to-body>
      <el-form ref="qualificationFormRef" :model="qualificationForm" :rules="qualificationRules" label-width="90px">
        <el-form-item label="资质名称" prop="qualificationName"><el-input v-model="qualificationForm.qualificationName" maxlength="200" /></el-form-item>
        <el-form-item label="证书编号" prop="certificateNo"><el-input v-model="qualificationForm.certificateNo" maxlength="100" /></el-form-item>
        <el-form-item label="发证机关" prop="issuingAuthority"><el-input v-model="qualificationForm.issuingAuthority" maxlength="200" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="发证日期"><el-date-picker v-model="qualificationForm.issueDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="到期日期"><el-date-picker v-model="qualificationForm.expiryDate" type="date" value-format="YYYY-MM-DD" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="qualificationDialogVisible = false">取消</el-button>
        <el-button v-permission="editingQualification ? 'srm:qualification:update' : 'srm:qualification:create'" type="primary" @click="saveQualification">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="bankDialogVisible" :title="editingBank ? '编辑银行账户' : '新增银行账户'" width="620px" append-to-body>
      <el-form ref="bankFormRef" :model="bankForm" :rules="bankRules" label-width="90px">
        <el-form-item label="账户名" prop="accountName"><el-input v-model="bankForm.accountName" maxlength="200" /></el-form-item>
        <el-form-item label="银行账号" prop="accountNo"><el-input v-model="bankForm.accountNo" maxlength="100" :placeholder="maskedBankAccount ? '已脱敏，留空保持不变' : ''" /></el-form-item>
        <el-form-item label="银行名称" prop="bankName"><el-input v-model="bankForm.bankName" maxlength="200" /></el-form-item>
        <el-form-item label="开户支行" prop="bankBranch"><el-input v-model="bankForm.bankBranch" maxlength="200" /></el-form-item>
        <el-form-item label="银行代码" prop="bankCode"><el-input v-model="bankForm.bankCode" maxlength="50" /></el-form-item>
        <el-form-item label="默认账户"><el-switch v-model="bankForm.primaryFlag" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="bankDialogVisible = false">取消</el-button>
        <el-button v-permission="editingBank ? 'srm:bank-account:update' : 'srm:bank-account:create'" type="primary" @click="saveBank">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
</style>
