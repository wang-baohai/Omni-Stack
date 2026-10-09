<script setup lang="ts">
/** SRM 供应商管理页面，包含供应商 CRUD、状态机操作和 360 视图。 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  blacklistSupplier,
  cancelSupplier,
  createSupplier,
  deleteSupplier,
  eliminateSupplier,
  getSupplier,
  listOwnerOptions,
  listSuppliers,
  restoreSupplier,
  resumeSupplier,
  submitSupplier,
  suspendSupplier,
  transferSupplierOwner,
  updateSupplier,
  withdrawSupplier,
  type SaveSupplierRequest,
  type OwnerOption,
  type SrmSupplier,
  type SupplierStatus,
} from '@/api/srm-supplier'
import SupplierOverview from '@/components/srm/SupplierOverview.vue'
import SupplierResourcesDrawer from '@/components/srm/SupplierResourcesDrawer.vue'
import ProcessProgressDialog from '@/components/workflow/ProcessProgressDialog.vue'
import { useDictOptions } from '@/composables/useDictOptions'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const permissionStore = usePermissionStore()
const { options: categoryOptions, loading: categoryLoading } = useDictOptions('srm_supplier_category')
const loading = ref(false)
const tableData = ref<SrmSupplier[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  name: string
  status?: SupplierStatus
  categoryCode: string
  levelCode: string
  ownerUserId?: number
}>({ name: '', categoryCode: '', levelCode: '' })

const statuses = computed<Array<{ label: string; value: SupplierStatus; type: string }>>(() => [
  { label: t('srmSupplierPage.statusRegistering'), value: 'REGISTERING', type: 'info' },
  { label: t('srmSupplierPage.statusRegisteringFailed'), value: 'REGISTERING_FAILED', type: 'danger' },
  { label: t('srmSupplierPage.statusPendingReview'), value: 'PENDING_REVIEW', type: 'warning' },
  { label: t('srmSupplierPage.statusApproving'), value: 'APPROVING', type: 'warning' },
  { label: t('srmSupplierPage.statusRejected'), value: 'REJECTED', type: 'danger' },
  { label: t('srmSupplierPage.statusApproved'), value: 'APPROVED', type: 'success' },
  { label: t('srmSupplierPage.statusSuspended'), value: 'SUSPENDED', type: '' },
  { label: t('srmSupplierPage.statusBlacklisted'), value: 'BLACKLISTED', type: 'danger' },
  { label: t('srmSupplierPage.statusEliminated'), value: 'ELIMINATED', type: 'danger' },
])
const statusLabel = computed<Record<SupplierStatus, string>>(() =>
  Object.fromEntries(statuses.value.map((item) => [item.value, item.label])) as Record<SupplierStatus, string>)
const statusType = computed<Record<SupplierStatus, string>>(() =>
  Object.fromEntries(statuses.value.map((item) => [item.value, item.type])) as Record<SupplierStatus, string>)

const formDialogVisible = ref(false)
const editingSupplier = ref<SrmSupplier | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<SaveSupplierRequest>({
  name: '', supplierType: '', industryCode: '', creditCode: '', website: '',
  phone: '', email: '', region: '', address: '', categoryCode: '',
})
const ownerOptions = ref<OwnerOption[]>([])
const ownerLoading = ref(false)
const overviewDrawerVisible = ref(false)
const resourcesDrawerVisible = ref(false)
const selectedSupplier = ref<SrmSupplier | null>(null)
const maskedSupplierFields = ref(new Set<'phone' | 'email'>())
const maskedPlaceholder = computed(() => t('srmResourcesMessages.maskedPlaceholder'))
const rules = computed<FormRules<SaveSupplierRequest>>(() => ({
  name: [
    { required: true, message: t('srmSupplierMessages.nameRequired'), trigger: 'blur' },
    { max: 200, message: t('srmSupplierMessages.nameLength'), trigger: 'blur' },
  ],
  supplierType: [
    { required: true, message: t('srmSupplierMessages.typeRequired'), trigger: 'change' },
    { max: 50, message: t('srmSupplierMessages.typeLength'), trigger: 'change' },
  ],
  industryCode: [{ max: 50, message: t('srmSupplierMessages.industryCodeLength'), trigger: 'blur' }],
  creditCode: [{ max: 50, message: t('srmSupplierMessages.creditCodeLength'), trigger: 'blur' }],
  website: [{ max: 300, message: t('srmSupplierMessages.websiteLength'), trigger: 'blur' }],
  phone: [{ max: 32, message: t('srmSupplierMessages.phoneLength'), trigger: 'blur' }],
  email: [
    { type: 'email', message: t('srmSupplierMessages.emailInvalid'), trigger: 'blur' },
    { max: 200, message: t('srmSupplierMessages.emailLength'), trigger: 'blur' },
  ],
  region: [{ max: 100, message: t('srmSupplierMessages.regionLength'), trigger: 'blur' }],
  address: [{ max: 500, message: t('srmSupplierMessages.addressLength'), trigger: 'blur' }],
  categoryCode: [{ max: 50, message: t('srmSupplierMessages.categoryCodeLength'), trigger: 'change' }],
}))
const supplierTypes = ['MANUFACTURER', 'DISTRIBUTOR', 'SERVICE', 'OTHER']
const supplierTypeLabel = computed<Record<string, string>>(() => ({
  MANUFACTURER: t('srmSupplierPage.typeManufacturer'),
  DISTRIBUTOR: t('srmSupplierPage.typeDistributor'),
  SERVICE: t('srmSupplierPage.typeService'),
  OTHER: t('srmSupplierPage.typeOther'),
}))
const levelLabel = computed<Record<string, string>>(() => ({
  STRATEGIC: t('srmEvaluationPage.levelStrategic'),
  PREFERRED: t('srmEvaluationPage.levelPreferred'),
  QUALIFIED: t('srmEvaluationPage.levelQualified'),
  ELIMINATED: t('srmEvaluationPage.levelEliminated'),
}))
const categoryLabel = computed(() => Object.fromEntries(categoryOptions.value.map((item) => [item.value, item.label])))

async function loadData() {
  loading.value = true
  try {
    const response = await listSuppliers({
      name: query.name || undefined, status: query.status,
      categoryCode: query.categoryCode || undefined,
      levelCode: query.levelCode || undefined,
      ownerUserId: query.ownerUserId,
      page: currentPage.value, size: pageSize.value,
    })
    tableData.value = response.data.data.records
    total.value = response.data.data.total
  } finally { loading.value = false }
}

function search() { currentPage.value = 1; loadData() }
function resetQuery() {
  Object.assign(query, { name: '', status: undefined, categoryCode: '', levelCode: '', ownerUserId: undefined })
  search()
}

function resetForm() {
  maskedSupplierFields.value = new Set()
  Object.assign(form, {
    name: '', supplierType: '', industryCode: '', creditCode: '', website: '',
    phone: '', email: '', region: '', address: '', categoryCode: '',
    ownerUserId: undefined, version: undefined,
  })
}

async function loadOwners(keyword?: string) {
  if (!permissionStore.hasPermission('srm:owner:list')) return
  ownerLoading.value = true
  try {
    const response = await listOwnerOptions({ keyword: keyword || undefined, limit: 50 })
    ownerOptions.value = response.data.data
  } finally {
    ownerLoading.value = false
  }
}

function openCreate() {
  editingSupplier.value = null
  resetForm()
  loadOwners()
  formDialogVisible.value = true
}

async function openEdit(row: SrmSupplier) {
  const response = await getSupplier(row.id)
  const detail = response.data.data
  maskedSupplierFields.value = new Set()
  if (detail.phone?.includes('*')) maskedSupplierFields.value.add('phone')
  if (detail.email?.includes('*')) maskedSupplierFields.value.add('email')
  editingSupplier.value = detail
  Object.assign(form, {
    name: detail.name, supplierType: detail.supplierType || '', industryCode: detail.industryCode || '',
    creditCode: detail.creditCode || '', website: detail.website || '',
    phone: maskedSupplierFields.value.has('phone') ? '' : detail.phone || '',
    email: maskedSupplierFields.value.has('email') ? '' : detail.email || '',
    region: detail.region || '',
    address: detail.address || '', categoryCode: detail.categoryCode || '', ownerUserId: detail.ownerUserId,
    version: detail.version,
  })
  ownerOptions.value = detail.ownerUserId ? [{
    userId: detail.ownerUserId,
    username: detail.ownerName || t('srmSupplierPage.userWithId', { id: detail.ownerUserId }),
    nickname: detail.ownerName,
    unitName: detail.ownerUnitName,
  }] : []
  loadOwners()
  formDialogVisible.value = true
}

function openOverview(row: SrmSupplier) {
  selectedSupplier.value = row
  overviewDrawerVisible.value = true
}

function openResources(row: SrmSupplier) {
  selectedSupplier.value = row
  resourcesDrawerVisible.value = true
}

async function saveForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const request = { ...form }
  if (maskedSupplierFields.value.has('phone') && !request.phone) request.phone = undefined
  if (maskedSupplierFields.value.has('email') && !request.email) request.email = undefined
  if (editingSupplier.value) {
    const supplierId = editingSupplier.value.id
    const originalOwnerUserId = editingSupplier.value.ownerUserId
    const { ownerUserId: targetOwnerUserId, ...supplierUpdateRequest } = request

    const profileFields = [
      'name', 'supplierType', 'industryCode', 'creditCode', 'website',
      'phone', 'email', 'region', 'address', 'categoryCode',
    ] as const
    const profileChanged = profileFields.some((field) => {
      const nextValue = supplierUpdateRequest[field]
      if (nextValue === undefined && maskedSupplierFields.value.has(field as 'phone' | 'email')) return false
      return (nextValue ?? '') !== (editingSupplier.value?.[field] ?? '')
    })
    const ownerChanged = targetOwnerUserId !== originalOwnerUserId
    let latestVersion = editingSupplier.value.version

    if (profileChanged) {
      const response = await updateSupplier(supplierId, { ...supplierUpdateRequest, version: latestVersion })
      latestVersion = response.data.data.version
      editingSupplier.value = response.data.data
      form.version = latestVersion
    }

    if (ownerChanged) {
      if (!targetOwnerUserId) {
        ElMessage.warning(t('srmSupplierMessages.ownerRequired'))
        return
      }
      const response = await transferSupplierOwner(supplierId, {
        ownerUserId: targetOwnerUserId,
        version: latestVersion,
      })
      editingSupplier.value = response.data.data
      form.version = response.data.data.version
    }

    if (!profileChanged && !ownerChanged) {
      ElMessage.info(t('srmSupplierMessages.noChanges'))
      return
    }
  } else {
    await createSupplier(request)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
}

async function handleDelete(row: SrmSupplier) {
  try {
    await ElMessageBox.confirm(
      t('srmSupplierMessages.deleteConfirm', { name: row.name }),
      t('srmSupplierMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteSupplier(row.id, row.version)
    ElMessage.success(t('common.success'))
    loadData()
  } catch { /* 用户取消 */ }
}

const progressDialogRef = ref<InstanceType<typeof ProcessProgressDialog>>()

async function handleSubmit(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.submitConfirm', { name: row.name }),
    t('srmSupplierMessages.submitTitle'),
  )
  await submitSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.submitted'))
  loadData()
}

async function handleWithdraw(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.withdrawConfirm', { name: row.name }),
    t('srmSupplierMessages.withdrawTitle'),
  )
  await withdrawSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.withdrawn'))
  loadData()
}

async function handleCancel(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.cancelConfirm', { name: row.name }),
    t('srmSupplierMessages.cancelTitle'),
    { type: 'warning' },
  )
  await cancelSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.cancelled'))
  loadData()
}

function openProcessProgress(row: SrmSupplier) {
  if (row.processInstanceId) {
    progressDialogRef.value?.open(row.processInstanceId, '')
  }
}

async function handleSuspend(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.suspendConfirm', { name: row.name }),
    t('srmSupplierMessages.suspendTitle'),
    { type: 'warning' },
  )
  await suspendSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.suspended'))
  loadData()
}

async function handleResume(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.resumeConfirm', { name: row.name }),
    t('srmSupplierMessages.resumeTitle'),
  )
  await resumeSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.resumed'))
  loadData()
}

async function handleRestore(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.restoreConfirm', { name: row.name }),
    t('srmSupplierMessages.restoreTitle'),
  )
  await restoreSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.restored'))
  loadData()
}

async function handleBlacklist(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.blacklistConfirm', { name: row.name }),
    t('srmSupplierMessages.blacklistTitle'),
    { type: 'warning' },
  )
  await blacklistSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.blacklisted'))
  loadData()
}

async function handleEliminate(row: SrmSupplier) {
  await ElMessageBox.confirm(
    t('srmSupplierMessages.eliminateConfirm', { name: row.name }),
    t('srmSupplierMessages.eliminateTitle'),
    { type: 'warning' },
  )
  await eliminateSupplier(row.id, row.version)
  ElMessage.success(t('srmSupplierMessages.eliminated'))
  loadData()
}

onMounted(() => {
  loadData()
  loadOwners()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ t('common.srmSuppliers') }}</span>
          <el-button v-permission="'srm:supplier:create'" type="primary" @click="openCreate">
            {{ t('srmSupplierPage.create') }}
          </el-button>
        </div>
      </template>
      <el-form inline class="search-form">
        <el-form-item :label="t('procurementRfqPage.keyword')">
          <el-input v-model="query.name" clearable :placeholder="t('srmSupplierPage.nameOrNo')" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('srmSupplierOverview.status')">
          <el-select v-model="query.status" clearable style="width: 130px">
            <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('srmEvaluationPage.level')">
          <el-select v-model="query.levelCode" clearable style="width: 130px">
            <el-option v-for="(label, value) in levelLabel" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementRequisitionPage.category')">
          <el-select
            v-model="query.categoryCode"
            clearable
            filterable
            :loading="categoryLoading.value"
            :placeholder="t('srmSupplierPage.allCategories')"
            style="width: 150px"
          >
            <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-permission="'srm:owner:list'" :label="t('srmSupplierOverview.owner')">
          <el-select
            v-model="query.ownerUserId"
            filterable
            remote
            clearable
            :remote-method="loadOwners"
            :loading="ownerLoading"
            :placeholder="t('srmSupplierPage.allOwners')"
            style="width: 180px"
          >
            <el-option
              v-for="owner in ownerOptions"
              :key="owner.userId"
              :label="owner.nickname || owner.username"
              :value="owner.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('common.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="supplierNo" :label="t('srmSupplierPage.supplierNo')" width="155" />
        <el-table-column prop="name" :label="t('srmSupplierPage.supplierName')" min-width="200" />
        <el-table-column :label="t('srmSupplierOverview.type')" width="110">
          <template #default="{ row }">{{ supplierTypeLabel[row.supplierType] || row.supplierType }}</template>
        </el-table-column>
        <el-table-column :label="t('procurementRequisitionPage.category')" width="120">
          <template #default="{ row }">{{ categoryLabel[row.categoryCode] || row.categoryCode }}</template>
        </el-table-column>
        <el-table-column :label="t('srmEvaluationPage.level')" width="110">
          <template #default="{ row }">{{ levelLabel[row.levelCode] || row.levelCode }}</template>
        </el-table-column>
        <el-table-column :label="t('srmSupplierOverview.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status as SupplierStatus]">{{ statusLabel[row.status as SupplierStatus] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('srmSupplierOverview.owner')" width="110">
          <template #default="{ row }">
            {{ row.ownerName || (row.ownerUserId ? t('srmSupplierPage.userWithId', { id: row.ownerUserId }) : t('srmSupplierOverview.unassigned')) }}
          </template>
        </el-table-column>
        <el-table-column prop="createTime" :label="t('procurementRequisitionPage.createTime')" width="170" />
        <el-table-column :label="t('common.actions')" fixed="right" width="360">
          <template #default="{ row }">
            <el-button size="small" @click="openOverview(row)">{{ t('srmSupplierPage.overview') }}</el-button>
            <el-button v-if="row.status !== 'ELIMINATED'" v-permission="'srm:supplier:update'" size="small" @click="openEdit(row)">
              {{ t('common.edit') }}
            </el-button>
            <el-dropdown trigger="click">
              <el-button size="small">{{ t('srmSupplierPage.more') }}<el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-if="permissionStore.hasPermission('srm:contact:list') || permissionStore.hasPermission('srm:qualification:list') || permissionStore.hasPermission('srm:bank-account:list')"
                    @click="openResources(row)"
                  >
                    {{ t('srmSupplierPage.maintainResources') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'REJECTED'" v-permission="'srm:supplier:create'" @click="handleSubmit(row)">
                    {{ t('srmSupplierPage.resubmit') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVING'" v-permission="'srm:supplier:withdraw'" @click="handleWithdraw(row)">
                    {{ t('srmSupplierPage.withdraw') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVING'" v-permission="'srm:supplier:cancel'" @click="handleCancel(row)">
                    {{ t('srmSupplierPage.cancelApproval') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.processInstanceId" @click="openProcessProgress(row)">
                    {{ t('srmSupplierPage.approvalProgress') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED'" v-permission="'srm:supplier:suspend'" @click="handleSuspend(row)">
                    {{ t('srmSupplierPage.suspend') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'SUSPENDED'" v-permission="'srm:supplier:resume'" @click="handleResume(row)">
                    {{ t('srmSupplierPage.resume') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'BLACKLISTED'" v-permission="'srm:supplier:restore'" @click="handleRestore(row)">
                    {{ t('srmSupplierPage.removeFromBlacklist') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED'" v-permission="'srm:supplier:blacklist'" @click="handleBlacklist(row)">
                    {{ t('srmSupplierPage.addToBlacklist') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 'APPROVED' || row.status === 'SUSPENDED'" v-permission="'srm:supplier:eliminate'" @click="handleEliminate(row)">
                    {{ t('srmSupplierPage.eliminate') }}
                  </el-dropdown-item>
                  <el-dropdown-item v-permission="'srm:supplier:delete'" divided @click="handleDelete(row)">
                    {{ t('common.delete') }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadData"
        @size-change="currentPage = 1; loadData()"
      />
    </el-card>

    <el-dialog
      v-model="formDialogVisible"
      :title="editingSupplier ? t('srmSupplierPage.edit') : t('srmSupplierPage.create')"
      width="760px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="t('srmSupplierPage.supplierName')" prop="name">
              <el-input v-model="form.name" maxlength="200" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="t('srmSupplierOverview.type')" prop="supplierType">
              <el-select v-model="form.supplierType" style="width: 100%">
                <el-option v-for="item in supplierTypes" :key="item" :label="supplierTypeLabel[item] || item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierPage.industry')" prop="industryCode"><el-input v-model="form.industryCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item :label="t('procurementRequisitionPage.category')" prop="categoryCode">
              <el-select v-model="form.categoryCode" clearable filterable :loading="categoryLoading.value" style="width: 100%">
                <el-option v-for="option in categoryOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierPage.creditCode')" prop="creditCode"><el-input v-model="form.creditCode" maxlength="50" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierPage.website')" prop="website"><el-input v-model="form.website" maxlength="300" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.phone')" prop="phone"><el-input v-model="form.phone" maxlength="32" :placeholder="maskedSupplierFields.has('phone') ? maskedPlaceholder : ''" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.email')" prop="email"><el-input v-model="form.email" maxlength="200" :placeholder="maskedSupplierFields.has('email') ? maskedPlaceholder : ''" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item v-permission="'srm:supplier:transfer'" :label="t('srmSupplierOverview.owner')">
              <el-select
                v-model="form.ownerUserId"
                filterable
                remote
                :clearable="!editingSupplier"
                :remote-method="loadOwners"
                :loading="ownerLoading"
                :placeholder="t('srmSupplierPage.ownerPlaceholder')"
                style="width: 100%"
              >
                <el-option
                  v-for="owner in ownerOptions"
                  :key="owner.userId"
                  :label="`${owner.nickname || owner.username}${owner.unitName ? ` · ${owner.unitName}` : ''}`"
                  :value="owner.userId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item :label="t('srmSupplierOverview.region')" prop="region"><el-input v-model="form.region" maxlength="100" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('srmSupplierOverview.address')" prop="address"><el-input v-model="form.address" maxlength="500" show-word-limit /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button v-permission="editingSupplier ? 'srm:supplier:update' : 'srm:supplier:create'" type="primary" @click="saveForm">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="overviewDrawerVisible"
      :title="t('srmSupplierPage.overviewTitle', { name: selectedSupplier?.name || t('srmResources.supplier') })"
      size="900px"
      destroy-on-close
    >
      <SupplierOverview :supplier-id="selectedSupplier?.id" />
    </el-drawer>

    <SupplierResourcesDrawer
      v-model="resourcesDrawerVisible"
      :supplier-id="selectedSupplier?.id"
      :supplier-name="selectedSupplier?.name"
    />

    <ProcessProgressDialog ref="progressDialogRef" />
  </div>
</template>

<style scoped lang="scss">
.page-container { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.search-form { margin-bottom: 4px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 18px; }
</style>
