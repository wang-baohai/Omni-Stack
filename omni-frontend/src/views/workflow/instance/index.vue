<script setup lang="ts">
/**
 * 流程实例管理页面。
 * 管理员按租户查看流程实例状态与发起信息。
 */
import { reactive, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDictOptions } from '@/composables/useDictOptions'
import { listAllInstances, type ProcessInstanceExt } from '@/api/workflow'
import ProcessProgressDialog from '@/components/workflow/ProcessProgressDialog.vue'
import ApprovalRecordsDialog from '@/components/workflow/ApprovalRecordsDialog.vue'

const { t } = useI18n()

// ===== 字典选项 =====
const { options: categoryOptions } = useDictOptions('workflow_category')

/** 根据字典值获取分类标签 */
function categoryLabel(value: string | null) {
  if (!value) return ''
  return categoryOptions.value.find(o => o.value === value)?.label || value
}

const list = ref<ProcessInstanceExt[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const search = reactive<{ title: string; status?: number }>({
  title: '',
  status: undefined,
})

function statusLabel(status: number) {
  if (status === 1) return t('workflow.pending')
  if (status === 2) return t('workflow.completed')
  return t('workflow.terminated')
}

function statusType(status: number) {
  if (status === 1) return 'warning'
  if (status === 2) return 'success'
  return 'danger'
}

async function loadList() {
  loading.value = true
  try {
    const res = await listAllInstances({
      title: search.title || undefined,
      status: search.status,
      page: page.value,
      size: size.value,
    })
    list.value = res.data.data.records
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  loadList()
}

function handleReset() {
  search.title = ''
  search.status = undefined
  handleSearch()
}

onMounted(() => loadList())

const progressDialogRef = ref<InstanceType<typeof ProcessProgressDialog>>()
const approvalRecordsRef = ref<InstanceType<typeof ApprovalRecordsDialog>>()

/** 打开流转进度弹窗 */
function handleProgress(row: ProcessInstanceExt) {
  if (!row.processDefinitionId) return
  progressDialogRef.value?.open(row.processInstanceId, row.processDefinitionId)
}

/** 打开审批记录弹窗 */
function handleApprovalRecords(row: ProcessInstanceExt) {
  approvalRecordsRef.value?.open(row.processInstanceId)
}
</script>

<template>
  <div class="page-container">
    <el-form :inline="true" @submit.prevent="handleSearch">
      <el-form-item :label="t('workflow.title')">
        <el-input v-model="search.title" :placeholder="t('workflow.title')" clearable />
      </el-form-item>
      <el-form-item :label="t('common.status')">
        <el-select v-model="search.status" clearable :placeholder="t('common.status')" style="width: 140px">
          <el-option :label="t('workflow.pending')" :value="1" />
          <el-option :label="t('workflow.completed')" :value="2" />
          <el-option :label="t('workflow.terminated')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" border stripe>
      <el-table-column prop="title" :label="t('workflow.title')" min-width="180" />
      <el-table-column prop="processKey" :label="t('workflow.processKey')" width="160" />
      <el-table-column prop="businessKey" :label="t('workflow.businessKey')" width="160" />
      <el-table-column prop="startUserName" :label="t('workflow.initiator')" width="140" />
      <el-table-column :label="t('workflow.category')" width="120">
        <template #default="{ row }">
          {{ categoryLabel(row.category) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" :label="t('workflow.startTime')" width="170" />
      <el-table-column prop="updateTime" :label="t('workflow.updateTime')" width="170" />
      <el-table-column :label="t('common.actions')" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            size="small"
            @click="handleProgress(row)"
          >流转进度</el-button>
          <el-button
            type="primary"
            link
            size="small"
            @click="handleApprovalRecords(row)"
          >审批记录</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[5, 10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadList"
        @current-change="loadList"
      />
    </div>

    <!-- 流转进度弹窗 -->
    <ProcessProgressDialog ref="progressDialogRef" />

    <!-- 审批记录弹窗 -->
    <ApprovalRecordsDialog ref="approvalRecordsRef" />
  </div>
</template>

<style scoped>
.page-container {
  padding: 20px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
