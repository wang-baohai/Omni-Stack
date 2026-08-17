<script setup lang="ts">
/**
 * 风险指标配置管理页面。
 * 左侧：指标类型列表（可增删改）
 * 右侧：选中指标类型的评分标准表格（可增删改）
 * 底部：得分阈值配置区域
 */
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRiskIndicatorTypes,
  createIndicatorType,
  updateIndicatorType,
  deleteIndicatorType,
  createCriterion,
  updateCriterion,
  deleteCriterion,
  getScoreThresholds,
  updateScoreThresholds,
  type RiskIndicatorTypeVO,
  type RiskCriterionVO,
  type RiskScoreThresholdVO,
} from '@/api/srm-risk-config'
import type { RiskLevel } from '@/api/srm-risk'

// ===== 指标类型列表 =====
const types = ref<RiskIndicatorTypeVO[]>([])
const typesLoading = ref(false)
const selectedType = ref<RiskIndicatorTypeVO | null>(null)

async function loadTypes() {
  typesLoading.value = true
  try {
    const { data: res } = await getRiskIndicatorTypes()
    types.value = res.data
    // 保持选中
    if (selectedType.value) {
      selectedType.value = types.value.find(t => t.id === selectedType.value!.id) || null
    }
  } catch {
    ElMessage.error('加载指标类型失败')
  } finally {
    typesLoading.value = false
  }
}

// ===== 指标类型对话框 =====
const typeDialogVisible = ref(false)
const typeDialogIsEdit = ref(false)
const typeEditingId = ref<number | null>(null)
const typeForm = ref({
  typeCode: '',
  typeName: '',
  description: '',
  sort: 0,
  autoCalc: 0,
  status: 1,
})

function openCreateType() {
  typeDialogIsEdit.value = false
  typeEditingId.value = null
  typeForm.value = { typeCode: '', typeName: '', description: '', sort: 0, autoCalc: 0, status: 1 }
  typeDialogVisible.value = true
}

function openEditType(row: RiskIndicatorTypeVO) {
  typeDialogIsEdit.value = true
  typeEditingId.value = row.id
  typeForm.value = {
    typeCode: row.typeCode,
    typeName: row.typeName,
    description: row.description || '',
    sort: row.sort,
    autoCalc: row.autoCalc,
    status: row.status,
  }
  typeDialogVisible.value = true
}

async function submitType() {
  if (!typeForm.value.typeCode.trim() || !typeForm.value.typeName.trim()) {
    ElMessage.warning('编码和名称不能为空')
    return
  }
  try {
    if (typeDialogIsEdit.value && typeEditingId.value) {
      await updateIndicatorType(typeEditingId.value, typeForm.value)
      ElMessage.success('更新成功')
    } else {
      await createIndicatorType(typeForm.value)
      ElMessage.success('创建成功')
    }
    typeDialogVisible.value = false
    await loadTypes()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDeleteType(row: RiskIndicatorTypeVO) {
  try {
    await ElMessageBox.confirm(`确定删除指标类型「${row.typeName}」？`, '确认删除', { type: 'warning' })
    await deleteIndicatorType(row.id)
    ElMessage.success('删除成功')
    if (selectedType.value?.id === row.id) selectedType.value = null
    await loadTypes()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// ===== 评分标准 =====
const currentCriteria = computed(() => selectedType.value?.criteria || [])
const isAutoCalc = computed(() => selectedType.value?.autoCalc === 1)

// 评分标准对话框
const criterionDialogVisible = ref(false)
const criterionDialogIsEdit = ref(false)
const criterionEditingId = ref<number | null>(null)
const criterionForm = ref({
  criterionLabel: '',
  score: 1,
  riskLevel: 'GREEN' as RiskLevel,
  sort: 0,
  status: 1,
})

function openCreateCriterion() {
  if (!selectedType.value) return
  criterionDialogIsEdit.value = false
  criterionEditingId.value = null
  criterionForm.value = { criterionLabel: '', score: 1, riskLevel: 'GREEN', sort: 0, status: 1 }
  criterionDialogVisible.value = true
}

function openEditCriterion(row: RiskCriterionVO) {
  criterionDialogIsEdit.value = true
  criterionEditingId.value = row.id
  criterionForm.value = {
    criterionLabel: row.criterionLabel,
    score: row.score,
    riskLevel: row.riskLevel,
    sort: row.sort,
    status: row.status,
  }
  criterionDialogVisible.value = true
}

async function submitCriterion() {
  if (!criterionForm.value.criterionLabel.trim()) {
    ElMessage.warning('评分标准描述不能为空')
    return
  }
  if (!selectedType.value) return
  try {
    if (criterionDialogIsEdit.value && criterionEditingId.value) {
      await updateCriterion(criterionEditingId.value, criterionForm.value)
      ElMessage.success('更新成功')
    } else {
      await createCriterion({
        indicatorTypeId: selectedType.value.id,
        ...criterionForm.value,
      })
      ElMessage.success('创建成功')
    }
    criterionDialogVisible.value = false
    await loadTypes()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function handleDeleteCriterion(row: RiskCriterionVO) {
  try {
    await ElMessageBox.confirm(`确定删除评分标准「${row.criterionLabel}」？`, '确认删除', { type: 'warning' })
    await deleteCriterion(row.id)
    ElMessage.success('删除成功')
    await loadTypes()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// ===== 得分阈值 =====
const thresholds = ref<RiskScoreThresholdVO[]>([])
const thresholdsLoading = ref(false)

async function loadThresholds() {
  thresholdsLoading.value = true
  try {
    const { data: res } = await getScoreThresholds()
    thresholds.value = res.data
  } catch {
    ElMessage.error('加载得分阈值失败')
  } finally {
    thresholdsLoading.value = false
  }
}

async function saveThresholds() {
  try {
    await updateScoreThresholds(thresholds.value)
    ElMessage.success('阈值保存成功')
    await loadThresholds()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

// ===== 风险等级标签 =====
function riskTagType(level: string) {
  if (level === 'RED') return 'danger'
  if (level === 'YELLOW') return 'warning'
  return 'success'
}

function riskLabel(level: string) {
  if (level === 'RED') return '高风险'
  if (level === 'YELLOW') return '中风险'
  return '低风险'
}

// ===== 初始化 =====
onMounted(() => {
  loadTypes()
  loadThresholds()
})
</script>

<template>
  <div class="risk-config-page">
    <!-- 指标类型和评分标准 -->
    <el-row :gutter="16">
      <!-- 左侧：指标类型列表 -->
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>指标类型</span>
              <el-button v-permission="'srm:risk:config:update'" type="primary" size="small" @click="openCreateType">
                新增
              </el-button>
            </div>
          </template>
          <el-table
            v-loading="typesLoading"
            :data="types"
            highlight-current-row
            @current-change="(row: RiskIndicatorTypeVO) => selectedType = row"
            stripe
            border
            size="small"
          >
            <el-table-column prop="typeName" label="名称" min-width="100" />
            <el-table-column prop="typeCode" label="编码" width="120" />
            <el-table-column label="自动" width="60" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.autoCalc === 1" type="info" size="small">是</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button v-permission="'srm:risk:config:update'" link type="primary" size="small" @click="openEditType(row)">编辑</el-button>
                <el-button v-permission="'srm:risk:config:update'" link type="danger" size="small" @click="handleDeleteType(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：评分标准表格 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>
                评分标准
                <template v-if="selectedType">
                  — {{ selectedType.typeName }}
                  <el-tag v-if="isAutoCalc" type="info" size="small" style="margin-left: 8px">自动计算</el-tag>
                </template>
              </span>
              <el-button
                v-if="selectedType && !isAutoCalc"
                v-permission="'srm:risk:config:update'"
                type="primary"
                size="small"
                @click="openCreateCriterion"
              >
                新增
              </el-button>
            </div>
          </template>
          <el-empty v-if="!selectedType" description="请先选择左侧的指标类型" />
          <el-table
            v-else
            :data="currentCriteria"
            stripe
            border
            size="small"
          >
            <el-table-column prop="criterionLabel" label="评分标准描述" min-width="200" />
            <el-table-column prop="score" label="分值" width="80" align="center" />
            <el-table-column label="风险等级" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sort" label="排序" width="70" align="center" />
            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <template v-if="!isAutoCalc">
                  <el-button v-permission="'srm:risk:config:update'" link type="primary" size="small" @click="openEditCriterion(row)">编辑</el-button>
                  <el-button v-permission="'srm:risk:config:update'" link type="danger" size="small" @click="handleDeleteCriterion(row)">删除</el-button>
                </template>
                <span v-else style="color: #999; font-size: 12px">自动计算</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 得分阈值配置 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span>得分 → 风险等级映射阈值</span>
          <el-button v-permission="'srm:risk:config:update'" type="primary" size="small" @click="saveThresholds">
            保存阈值
          </el-button>
        </div>
      </template>
      <el-table v-loading="thresholdsLoading" :data="thresholds" stripe border size="small">
        <el-table-column label="风险等级" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最小分（含）" width="200" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.minScore" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="最大分（含）" width="200" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.maxScore" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200">
          <template #default="{ row }">
            总分在 {{ row.minScore }} ~ {{ row.maxScore }} 之间时，综合风险等级为
            <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 指标类型对话框 -->
    <el-dialog v-model="typeDialogVisible" :title="typeDialogIsEdit ? '编辑指标类型' : '新增指标类型'" width="500px">
      <el-form :model="typeForm" label-width="100px">
        <el-form-item label="指标编码" v-if="!typeDialogIsEdit">
          <el-input v-model="typeForm.typeCode" placeholder="如 FINANCIAL" :maxlength="50" />
        </el-form-item>
        <el-form-item label="指标编码" v-else>
          <el-input :model-value="typeForm.typeCode" disabled />
        </el-form-item>
        <el-form-item label="指标名称">
          <el-input v-model="typeForm.typeName" placeholder="如 财务风险" :maxlength="100" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" :maxlength="500" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="自动计算">
          <el-switch v-model="typeForm.autoCalc" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #999; font-size: 12px">开启后由系统自动计算（如资质风险）</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="typeForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitType">确定</el-button>
      </template>
    </el-dialog>

    <!-- 评分标准对话框 -->
    <el-dialog v-model="criterionDialogVisible" :title="criterionDialogIsEdit ? '编辑评分标准' : '新增评分标准'" width="500px">
      <el-form :model="criterionForm" label-width="100px">
        <el-form-item label="标准描述">
          <el-input v-model="criterionForm.criterionLabel" placeholder="如：流动比率 > 2" :maxlength="200" />
        </el-form-item>
        <el-form-item label="分值">
          <el-input-number v-model="criterionForm.score" :min="0" :max="100" />
          <span style="margin-left: 8px; color: #999; font-size: 12px">分值越高越危险</span>
        </el-form-item>
        <el-form-item label="风险等级">
          <el-select v-model="criterionForm.riskLevel">
            <el-option label="低风险 (GREEN)" value="GREEN" />
            <el-option label="中风险 (YELLOW)" value="YELLOW" />
            <el-option label="高风险 (RED)" value="RED" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="criterionForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="criterionForm.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="criterionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCriterion">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.risk-config-page {
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
</style>
