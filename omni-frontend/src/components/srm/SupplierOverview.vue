<script setup lang="ts">
/** SRM 供应商 360 聚合视图。 */
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { getSupplierOverview, type SupplierOverview } from '@/api/srm-supplier'

const props = defineProps<{ supplierId?: number }>()
const { t } = useI18n()
const loading = ref(false)
const data = ref<SupplierOverview | null>(null)
const typeKeys: Record<string, string> = {
  MANUFACTURER: 'srmSupplierPage.typeManufacturer', DISTRIBUTOR: 'srmSupplierPage.typeDistributor',
  SERVICE: 'srmSupplierPage.typeService', OTHER: 'srmSupplierPage.typeOther',
}
const levelKeys: Record<string, string> = {
  STRATEGIC: 'srmEvaluationPage.levelStrategic', PREFERRED: 'srmEvaluationPage.levelPreferred',
  QUALIFIED: 'srmEvaluationPage.levelQualified', ELIMINATED: 'srmEvaluationPage.levelEliminated',
}
const statusKeys: Record<string, string> = {
  REGISTERING: 'srmSupplierPage.statusRegistering', REGISTERING_FAILED: 'srmSupplierPage.statusRegisteringFailed',
  PENDING_REVIEW: 'srmSupplierPage.statusPendingReview', APPROVING: 'srmSupplierPage.statusApproving',
  REJECTED: 'srmSupplierPage.statusRejected', APPROVED: 'srmSupplierPage.statusApproved',
  SUSPENDED: 'srmSupplierPage.statusSuspended', BLACKLISTED: 'srmSupplierPage.statusBlacklisted', ELIMINATED: 'srmSupplierPage.statusEliminated',
}
const qualificationStatusKeys: Record<string, string> = {
  ACTIVE: 'srmResources.statusActive', INACTIVE: 'srmResources.statusInactive',
}
const evaluationStatusKeys: Record<string, string> = {
  COMPLETED: 'srmEvaluationPage.statusCompleted', PENDING: 'srmEvaluationPage.statusPending',
  IN_PROGRESS: 'srmEvaluationPage.statusInProgress',
}
const riskLevelKeys: Record<string, string> = {
  RED: 'srmRiskCommon.high', YELLOW: 'srmRiskCommon.medium', GREEN: 'srmRiskCommon.low',
}
const indicatorTypeKeys: Record<string, string> = {
  FINANCIAL: 'srmSupplierOverview.indicatorFinancial', COMPLIANCE: 'srmSupplierOverview.indicatorCompliance',
  SUPPLY: 'srmSupplierOverview.indicatorSupply', QUALITY: 'srmSupplierOverview.indicatorQuality',
  COOPERATION: 'srmSupplierOverview.indicatorCooperation', CERTIFICATE: 'srmSupplierOverview.indicatorCertificate',
}

/** 与列表使用相同已有语言资源；自定义或未来未知代码保留原文。 */
function displayLabel(value: string | null | undefined, keys: Record<string, string>) {
  if (!value) return ''
  const key = keys[value]
  return key ? t(key) : value
}

async function load() {
  if (!props.supplierId) return
  loading.value = true
  try {
    const response = await getSupplierOverview(props.supplierId)
    data.value = response.data.data
  } finally { loading.value = false }
}

watch(() => props.supplierId, load)
onMounted(load)
</script>

<template>
  <div v-loading="loading" class="supplier-overview">
    <template v-if="data">
      <el-descriptions :column="2" border :title="t('srmSupplierOverview.basicInfo')">
        <el-descriptions-item :label="t('srmSupplierOverview.no')">{{ data.supplierNo }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.name')">{{ data.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.type')">{{ displayLabel(data.supplierType, typeKeys) }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.level')">{{ displayLabel(data.levelCode, levelKeys) }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.status')"><el-tag>{{ displayLabel(data.status, statusKeys) }}</el-tag></el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.owner')">
          {{ data.ownerName || (data.ownerUserId ? `#${data.ownerUserId}` : t('srmSupplierOverview.unassigned')) }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.phone')">{{ data.phone }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.email')">{{ data.email }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.region')">{{ data.region }}</el-descriptions-item>
        <el-descriptions-item :label="t('srmSupplierOverview.address')" :span="2">{{ data.address }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">{{ t('srmSupplierOverview.contacts') }}</el-divider>
      <el-table :data="data.contacts" size="small" border>
        <el-table-column prop="name" :label="t('srmSupplierOverview.contactName')" width="100" />
        <el-table-column prop="jobTitle" :label="t('srmSupplierOverview.jobTitle')" width="100" />
        <el-table-column prop="mobile" :label="t('srmSupplierOverview.mobile')" width="130" />
        <el-table-column prop="email" :label="t('srmSupplierOverview.email')" />
        <el-table-column prop="decisionRole" :label="t('srmSupplierOverview.role')" width="80" />
        <el-table-column :label="t('srmSupplierOverview.primary')" min-width="90">
          <template #default="{ row }"><el-icon v-if="row.primaryFlag"><Star /></el-icon></template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">{{ t('srmSupplierOverview.qualifications') }}</el-divider>
      <el-table :data="data.qualifications" size="small" border>
        <el-table-column prop="qualificationName" :label="t('srmSupplierOverview.qualificationName')" />
        <el-table-column prop="certificateNo" :label="t('srmSupplierOverview.certificateNo')" width="150" />
        <el-table-column prop="issuingAuthority" :label="t('srmSupplierOverview.issuingAuthority')" width="150" />
        <el-table-column prop="expiryDate" :label="t('srmSupplierOverview.expiryDate')" width="110" />
        <el-table-column :label="t('srmSupplierOverview.status')" min-width="100">
          <template #default="{ row }">{{ displayLabel(row.status, qualificationStatusKeys) }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">{{ t('srmSupplierOverview.bankAccounts') }}</el-divider>
      <el-table :data="data.bankAccounts" size="small" border>
        <el-table-column prop="accountName" :label="t('srmSupplierOverview.accountName')" width="150" />
        <el-table-column prop="accountNo" :label="t('srmSupplierOverview.accountNo')" />
        <el-table-column prop="bankName" :label="t('srmSupplierOverview.bankName')" width="150" />
        <el-table-column prop="bankBranch" :label="t('srmSupplierOverview.bankBranch')" width="150" />
      </el-table>

      <el-divider content-position="left">{{ t('srmSupplierOverview.recentEvaluations') }}</el-divider>
      <el-table :data="data.recentEvaluations" size="small" border>
        <el-table-column prop="evaluationPeriod" :label="t('srmSupplierOverview.period')" min-width="180" />
        <el-table-column prop="totalScore" :label="t('srmSupplierOverview.totalScore')" width="100" />
        <el-table-column prop="evaluationTime" :label="t('srmSupplierOverview.time')" width="170" />
        <el-table-column :label="t('srmSupplierOverview.status')" min-width="120">
          <template #default="{ row }">{{ displayLabel(row.status, evaluationStatusKeys) }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">{{ t('srmSupplierOverview.riskIndicators') }}</el-divider>
      <el-table :data="data.riskIndicators" size="small" border>
        <el-table-column :label="t('srmSupplierOverview.indicatorType')" width="140">
          <template #default="{ row }">{{ displayLabel(row.indicatorType, indicatorTypeKeys) }}</template>
        </el-table-column>
        <el-table-column prop="indicatorValue" :label="t('srmSupplierOverview.indicatorValue')" />
        <el-table-column :label="t('srmSupplierOverview.riskLevel')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'RED' ? 'danger' : row.riskLevel === 'YELLOW' ? 'warning' : 'success'">
              {{ displayLabel(row.riskLevel, riskLevelKeys) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assessmentTime" :label="t('srmSupplierOverview.assessmentTime')" width="170" />
      </el-table>

      <template v-if="data.latestRiskAssessment">
        <el-divider content-position="left">{{ t('srmSupplierOverview.latestRiskAssessment') }}</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('srmSupplierOverview.overallLevel')">
            <el-tag :type="data.latestRiskAssessment.overallLevel === 'RED' ? 'danger' : data.latestRiskAssessment.overallLevel === 'YELLOW' ? 'warning' : 'success'">
              {{ displayLabel(data.latestRiskAssessment.overallLevel, riskLevelKeys) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('srmSupplierOverview.assessmentTime')">
            {{ data.latestRiskAssessment.assessmentTime }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
.supplier-overview { padding: 8px 0; }
</style>
