<script setup lang="ts">
/**
 * Customer 360 视图。
 * 客户、联系人、商机、活动分别按各自权限和接口加载，禁止复用客户权限读取跨聚合数据。
 */
import { computed, ref, watch } from 'vue'
import { getCustomer, type CrmCustomer } from '@/api/crm-customer'
import { listCustomerContacts, type CrmContact } from '@/api/crm-contact'
import { listOpportunities, type CrmOpportunity } from '@/api/crm-opportunity'
import { listActivityTimeline, type CrmActivity } from '@/api/crm-activity'
import { usePermissionStore } from '@/stores/permission'

const props = defineProps<{
  customerId?: number
}>()

const permissionStore = usePermissionStore()
const customer = ref<CrmCustomer | null>(null)
const contacts = ref<CrmContact[]>([])
const opportunities = ref<CrmOpportunity[]>([])
const activities = ref<CrmActivity[]>([])
const loading = ref(false)

const canViewContacts = computed(() => permissionStore.hasPermission('crm:contact:list'))
const canViewOpportunities = computed(() => permissionStore.hasPermission('crm:opportunity:list'))
const canViewActivities = computed(() => permissionStore.hasPermission('crm:activity:list'))

async function loadData() {
  if (!props.customerId) return
  loading.value = true
  contacts.value = []
  opportunities.value = []
  activities.value = []
  try {
    const customerRequest = getCustomer(props.customerId)
    const contactRequest = canViewContacts.value
      ? listCustomerContacts(props.customerId, { page: 1, size: 100 })
      : null
    const opportunityRequest = canViewOpportunities.value
      ? listOpportunities({ customerId: props.customerId, page: 1, size: 100 })
      : null
    const activityRequest = canViewActivities.value
      ? listActivityTimeline({ rootType: 'CUSTOMER', rootId: props.customerId, limit: 30 })
      : null

    const [customerResponse, contactResponse, opportunityResponse, activityResponse] = await Promise.all([
      customerRequest,
      contactRequest,
      opportunityRequest,
      activityRequest,
    ])
    customer.value = customerResponse.data.data
    contacts.value = contactResponse?.data.data.records || []
    opportunities.value = opportunityResponse?.data.data.records || []
    activities.value = activityResponse?.data.data || []
  } finally {
    loading.value = false
  }
}

function money(value: number, currency = 'CNY') {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency }).format(value || 0)
}

watch(() => props.customerId, loadData, { immediate: true })
</script>

<template>
  <div v-loading="loading" class="customer-overview">
    <el-empty v-if="!customer" description="暂无客户数据" />
    <template v-else>
      <el-descriptions title="客户档案" :column="2" border>
        <el-descriptions-item label="客户编号">{{ customer.customerNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ customer.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ customer.status }}</el-descriptions-item>
        <el-descriptions-item label="负责人">{{ customer.ownerName || `用户 #${customer.ownerUserId}` }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ customer.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ customer.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="最近跟进">{{ customer.lastActivityTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次跟进">{{ customer.nextFollowupTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">联系人</el-divider>
      <el-alert v-if="!canViewContacts" title="无联系人查看权限" type="info" :closable="false" />
      <el-table v-else :data="contacts" size="small" border>
        <el-table-column prop="name" label="姓名" />
        <el-table-column prop="jobTitle" label="职务" />
        <el-table-column prop="mobile" label="手机" />
        <el-table-column label="主要联系人" width="100">
          <template #default="{ row }"><el-tag v-if="row.primaryFlag === 1" type="success">是</el-tag></template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">开放商机</el-divider>
      <el-alert v-if="!canViewOpportunities" title="无商机查看权限" type="info" :closable="false" />
      <el-table v-else :data="opportunities" size="small" border>
        <el-table-column prop="name" label="商机名称" />
        <el-table-column label="阶段"><template #default="{ row }">#{{ row.stageId }}</template></el-table-column>
        <el-table-column label="金额" width="140">
          <template #default="{ row }">{{ money(row.amount, row.currencyCode) }}</template>
        </el-table-column>
        <el-table-column prop="expectedCloseDate" label="预计成交" width="120" />
      </el-table>

      <el-divider content-position="left">最近活动</el-divider>
      <el-alert v-if="!canViewActivities" title="无跟进活动查看权限" type="info" :closable="false" />
      <el-timeline v-else-if="activities.length">
        <el-timeline-item
          v-for="activity in activities"
          :key="activity.id"
          :timestamp="activity.completedTime || activity.plannedStartTime || activity.createTime"
        >
          <strong>{{ activity.subject }}</strong>
          <p class="plain-content">{{ activity.content }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无活动" :image-size="70" />
    </template>
  </div>
</template>

<style scoped lang="scss">
.customer-overview {
  min-height: 260px;
}

.plain-content {
  margin: 4px 0;
  color: var(--el-text-color-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
