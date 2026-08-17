<script setup lang="ts">
/**
 * XSS 防护配置管理页面。
 * 提供全局开关切换、黑名单规则增删改查和单条规则启用/禁用功能。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getXssSettings,
  toggleXssGlobal,
  listXssRules,
  createXssRule,
  updateXssRule,
  deleteXssRule,
  toggleXssRule,
  type BlacklistRule,
  type CreateXssRuleRequest,
} from '@/api/xss-config'
import type { PageResult } from '@/types/api'

const { t } = useI18n()

/** 全局开关状态 */
const globalEnabled = ref(false)
const globalLoading = ref(false)

/** 规则列表数据 */
const tableData = ref<BlacklistRule[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)

/** 表单对话框 */
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CreateXssRuleRequest & { description: string; sortOrder: number }>({
  ruleName: '',
  ruleType: 'HTML_TAG',
  pattern: '',
  description: '',
  sortOrder: 0,
})

/** 规则类型选项 */
const ruleTypeOptions = [
  { value: 'HTML_TAG', labelKey: 'xssConfig.ruleTypeHtmlTag' },
  { value: 'EVENT_HANDLER', labelKey: 'xssConfig.ruleTypeEventHandler' },
  { value: 'DANGEROUS_PROTOCOL', labelKey: 'xssConfig.ruleTypeDangerousProtocol' },
  { value: 'CUSTOM_PATTERN', labelKey: 'xssConfig.ruleTypeCustomPattern' },
]

/** 加载全局设置 */
async function loadSettings() {
  try {
    const res = await getXssSettings()
    globalEnabled.value = res.data.data.enabled
  } catch { /* 忽略错误，使用默认值 */ }
}

/** 切换全局开关 */
async function handleGlobalToggle(val: boolean) {
  globalLoading.value = true
  try {
    await toggleXssGlobal(val)
    ElMessage.success(t('common.success'))
  } catch {
    // 回滚开关状态
    globalEnabled.value = !val
  } finally {
    globalLoading.value = false
  }
}

/** 加载规则列表 */
async function loadRules() {
  loading.value = true
  try {
    const res = await listXssRules(currentPage.value, pageSize.value)
    const data = res.data.data as PageResult<BlacklistRule>
    tableData.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadRules()
}

/** 打开新建对话框 */
function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    ruleName: '',
    ruleType: 'HTML_TAG',
    pattern: '',
    description: '',
    sortOrder: 0,
  })
  formDialogVisible.value = true
}

/** 打开编辑对话框 */
function openEditDialog(row: BlacklistRule) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    pattern: row.pattern,
    description: row.description || '',
    sortOrder: row.sortOrder || 0,
  })
  formDialogVisible.value = true
}

/** 保存规则表单 */
async function saveForm() {
  if (isEdit.value && editingId.value) {
    await updateXssRule(editingId.value, {
      ruleName: form.ruleName,
      ruleType: form.ruleType,
      pattern: form.pattern,
      description: form.description || undefined,
      sortOrder: form.sortOrder,
    })
  } else {
    await createXssRule({
      ruleName: form.ruleName,
      ruleType: form.ruleType,
      pattern: form.pattern,
      description: form.description || undefined,
      sortOrder: form.sortOrder,
    })
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadRules()
}

/** 删除规则 */
async function handleDelete(row: BlacklistRule) {
  try {
    await ElMessageBox.confirm(t('xssConfig.confirmDelete'), { type: 'warning' })
    await deleteXssRule(row.id)
    ElMessage.success(t('common.success'))
    loadRules()
  } catch { /* 取消操作 */ }
}

/** 切换单条规则启用状态 */
async function handleToggleRule(row: BlacklistRule, val: boolean) {
  try {
    await toggleXssRule(row.id, val)
    ElMessage.success(t('common.success'))
  } catch {
    // 回滚
    row.enabled = val ? 0 : 1
  }
}

/** 获取规则类型的显示名称 */
function getRuleTypeLabel(type: string): string {
  const opt = ruleTypeOptions.find((o) => o.value === type)
  return opt ? t(opt.labelKey) : type
}

onMounted(() => {
  loadSettings()
  loadRules()
})
</script>

<template>
  <div class="page-container">
    <!-- 全局开关卡片 -->
    <el-card class="global-switch-card">
      <div class="global-switch">
        <div class="global-switch-info">
          <span class="global-switch-title">{{ t('xssConfig.globalSwitch') }}</span>
          <span class="global-switch-desc">{{ t('xssConfig.globalSwitchDesc') }}</span>
        </div>
        <el-switch
          v-model="globalEnabled"
          v-permission="'system:xssconfig:update'"
          :loading="globalLoading"
          @change="handleGlobalToggle"
        />
      </div>
    </el-card>

    <!-- 规则列表卡片 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('xssConfig.ruleList') }}</span>
          <el-button
            v-permission="'system:xssconfig:create'"
            type="primary"
            @click="openCreateDialog"
          >
            {{ t('common.create') }}
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tableData" stripe border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="ruleName" :label="t('xssConfig.ruleName')" width="180" />
        <el-table-column :label="t('xssConfig.ruleType')" width="150">
          <template #default="{ row }">
            <el-tag>{{ getRuleTypeLabel(row.ruleType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pattern" :label="t('xssConfig.pattern')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" :label="t('xssConfig.description')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sortOrder" :label="t('xssConfig.sortOrder')" width="80" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-switch
              v-permission="'system:xssconfig:update'"
              :model-value="row.enabled === 1"
              @change="(val: boolean) => handleToggleRule(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:xssconfig:update'"
              size="small"
              @click="openEditDialog(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-permission="'system:xssconfig:delete'"
              size="small"
              type="danger"
              @click="handleDelete(row)"
            >
              {{ t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="currentPage = 1; handlePageChange(1)"
      />
    </el-card>

    <!-- 规则表单对话框 -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEdit ? t('xssConfig.editRule') : t('xssConfig.createRule')"
      width="550px"
    >
      <el-form :model="form" label-width="120px">
        <el-form-item :label="t('xssConfig.ruleName')" required>
          <el-input v-model="form.ruleName" :placeholder="t('xssConfig.ruleNameRequired')" />
        </el-form-item>
        <el-form-item :label="t('xssConfig.ruleType')" required>
          <el-select v-model="form.ruleType" style="width: 100%">
            <el-option
              v-for="opt in ruleTypeOptions"
              :key="opt.value"
              :value="opt.value"
              :label="t(opt.labelKey)"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('xssConfig.pattern')" required>
          <el-input
            v-model="form.pattern"
            :placeholder="t('xssConfig.patternPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('xssConfig.description')">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            :placeholder="t('xssConfig.descriptionPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('xssConfig.sortOrder')">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}
.global-switch-card {
  margin-bottom: 20px;
}
.global-switch {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.global-switch-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.global-switch-title {
  font-weight: 600;
  font-size: 16px;
}
.global-switch-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
