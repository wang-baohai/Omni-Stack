<script setup lang="ts">
/** SRM 供应商 360 聚合视图。 */
import { onMounted, ref, watch } from 'vue'
import { getSupplierOverview, type SupplierOverview } from '@/api/srm-supplier'

const props = defineProps<{ supplierId?: number }>()
const loading = ref(false)
const data = ref<SupplierOverview | null>(null)

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
      <el-descriptions :column="2" border title="基本信息">
        <el-descriptions-item label="编号">{{ data.supplierNo }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ data.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ data.supplierType }}</el-descriptions-item>
        <el-descriptions-item label="等级">{{ data.levelCode }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag>{{ data.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="负责人">{{ data.ownerName || (data.ownerUserId ? `#${data.ownerUserId}` : '未分配') }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ data.phone }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ data.email }}</el-descriptions-item>
        <el-descriptions-item label="地区">{{ data.region }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ data.address }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">联系人</el-divider>
      <el-table :data="data.contacts" size="small" border>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="jobTitle" label="职位" width="100" />
        <el-table-column prop="mobile" label="手机" width="130" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="decisionRole" label="角色" width="80" />
        <el-table-column label="主要" width="60">
          <template #default="{ row }"><el-icon v-if="row.primaryFlag"><Star /></el-icon></template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">资质</el-divider>
      <el-table :data="data.qualifications" size="small" border>
        <el-table-column prop="qualificationName" label="资质名称" />
        <el-table-column prop="certificateNo" label="证书编号" width="150" />
        <el-table-column prop="issuingAuthority" label="发证机关" width="150" />
        <el-table-column prop="expiryDate" label="到期日" width="110" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>

      <el-divider content-position="left">银行账户</el-divider>
      <el-table :data="data.bankAccounts" size="small" border>
        <el-table-column prop="accountName" label="户名" width="150" />
        <el-table-column prop="accountNo" label="账号" />
        <el-table-column prop="bankName" label="银行" width="150" />
        <el-table-column prop="bankBranch" label="支行" width="150" />
      </el-table>

      <el-divider content-position="left">近期评估</el-divider>
      <el-table :data="data.recentEvaluations" size="small" border>
        <el-table-column prop="evaluationPeriod" label="周期" width="100" />
        <el-table-column prop="totalScore" label="总分" width="80" />
        <el-table-column prop="evaluationTime" label="时间" width="170" />
        <el-table-column prop="status" label="状态" width="80" />
      </el-table>

      <el-divider content-position="left">风险指标</el-divider>
      <el-table :data="data.riskIndicators" size="small" border>
        <el-table-column prop="indicatorType" label="指标类型" width="120" />
        <el-table-column prop="indicatorValue" label="指标值" />
        <el-table-column label="风险等级" width="100">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'RED' ? 'danger' : row.riskLevel === 'YELLOW' ? 'warning' : 'success'">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assessmentTime" label="评估时间" width="170" />
      </el-table>

      <template v-if="data.latestRiskAssessment">
        <el-divider content-position="left">最新风险评估</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="综合等级">
            <el-tag :type="data.latestRiskAssessment.overallLevel === 'RED' ? 'danger' : data.latestRiskAssessment.overallLevel === 'YELLOW' ? 'warning' : 'success'">
              {{ data.latestRiskAssessment.overallLevel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评估时间">{{ data.latestRiskAssessment.assessmentTime }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </template>
  </div>
</template>

<style scoped lang="scss">
.supplier-overview { padding: 8px 0; }
</style>
