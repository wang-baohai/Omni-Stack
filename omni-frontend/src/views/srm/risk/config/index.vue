<script setup lang="ts">
/**
 * 风险指标配置管理页面。
 * 左侧：指标类型列表（可增删改）；右侧：选中指标类型的评分标准表；底部：得分阈值配置区域。
 */
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { getErrorMessage, isUserCancelled } from '@/utils/errors'

const { t } = useI18n()

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
      selectedType.value = types.value.find(item => item.id === selectedType.value!.id) || null
    }
  } catch {
    ElMessage.error(t('srmRiskConfigMessages.loadTypesFailed'))
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
    ElMessage.warning(t('srmRiskConfigMessages.codeNameRequired'))
    return
  }
  try {
    if (typeDialogIsEdit.value && typeEditingId.value) {
      await updateIndicatorType(typeEditingId.value, typeForm.value)
      ElMessage.success(t('srmRiskConfigMessages.updated'))
    } else {
      await createIndicatorType(typeForm.value)
      ElMessage.success(t('srmRiskConfigMessages.created'))
    }
    typeDialogVisible.value = false
    await loadTypes()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, t('srmRiskConfigMessages.operationFailed')))
  }
}

async function handleDeleteType(row: RiskIndicatorTypeVO) {
  try {
    await ElMessageBox.confirm(
      t('srmRiskConfigMessages.deleteTypeConfirm', { name: row.typeName }),
      t('srmRiskConfigMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteIndicatorType(row.id)
    ElMessage.success(t('srmRiskConfigMessages.deleted'))
    if (selectedType.value?.id === row.id) selectedType.value = null
    await loadTypes()
  } catch (error: unknown) {
    if (!isUserCancelled(error)) ElMessage.error(getErrorMessage(error, t('srmRiskConfigMessages.deleteFailed')))
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
    ElMessage.warning(t('srmRiskConfigMessages.criterionRequired'))
    return
  }
  if (!selectedType.value) return
  try {
    if (criterionDialogIsEdit.value && criterionEditingId.value) {
      await updateCriterion(criterionEditingId.value, criterionForm.value)
      ElMessage.success(t('srmRiskConfigMessages.updated'))
    } else {
      await createCriterion({
        indicatorTypeId: selectedType.value.id,
        ...criterionForm.value,
      })
      ElMessage.success(t('srmRiskConfigMessages.created'))
    }
    criterionDialogVisible.value = false
    await loadTypes()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, t('srmRiskConfigMessages.operationFailed')))
  }
}

async function handleDeleteCriterion(row: RiskCriterionVO) {
  try {
    await ElMessageBox.confirm(
      t('srmRiskConfigMessages.deleteCriterionConfirm', { name: row.criterionLabel }),
      t('srmRiskConfigMessages.deleteTitle'),
      { type: 'warning' },
    )
    await deleteCriterion(row.id)
    ElMessage.success(t('srmRiskConfigMessages.deleted'))
    await loadTypes()
  } catch (error: unknown) {
    if (!isUserCancelled(error)) ElMessage.error(getErrorMessage(error, t('srmRiskConfigMessages.deleteFailed')))
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
    ElMessage.error(t('srmRiskConfigMessages.loadThresholdsFailed'))
  } finally {
    thresholdsLoading.value = false
  }
}

async function saveThresholds() {
  try {
    await updateScoreThresholds(thresholds.value)
    ElMessage.success(t('srmRiskConfigMessages.thresholdSaved'))
    await loadThresholds()
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, t('srmRiskConfigMessages.saveFailed')))
  }
}

// ===== 风险等级标签 =====
function riskTagType(level: string) {
  if (level === 'RED') return 'danger'
  if (level === 'YELLOW') return 'warning'
  return 'success'
}

function riskLabel(level: string) {
  if (level === 'RED') return t('srmRiskPage.riskHigh')
  if (level === 'YELLOW') return t('srmRiskPage.riskMedium')
  return t('srmRiskPage.riskLow')
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
              <span>{{ t('srmRiskConfig.indicatorTypes') }}</span>
              <el-button v-permission="'srm:risk:config:update'" type="primary" size="small" @click="openCreateType">
                {{ t('srmRiskConfig.create') }}
              </el-button>
            </div>
          </template>
          <el-table
            v-loading="typesLoading"
            :data="types"
            highlight-current-row
            stripe
            border
            size="small"
            @current-change="(row: RiskIndicatorTypeVO) => selectedType = row"
          >
            <el-table-column prop="typeName" :label="t('srmSupplierOverview.name')" min-width="100" />
            <el-table-column prop="typeCode" :label="t('srmRiskConfig.code')" width="120" />
            <el-table-column :label="t('srmRiskConfig.auto')" width="60" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.autoCalc === 1" type="info" size="small">{{ t('srmResources.yes') }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="120" align="center">
              <template #default="{ row }">
                <el-button v-permission="'srm:risk:config:update'" link type="primary" size="small" @click="openEditType(row)">
                  {{ t('common.edit') }}
                </el-button>
                <el-button v-permission="'srm:risk:config:update'" link type="danger" size="small" @click="handleDeleteType(row)">
                  {{ t('common.delete') }}
                </el-button>
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
                {{ t('srmRiskConfig.scoringCriteria') }}
                <template v-if="selectedType">
                  — {{ selectedType.typeName }}
                  <el-tag v-if="isAutoCalc" type="info" size="small" style="margin-left: 8px">
                    {{ t('srmRiskPage.autoCalculated') }}
                  </el-tag>
                </template>
              </span>
              <el-button
                v-if="selectedType && !isAutoCalc"
                v-permission="'srm:risk:config:update'"
                type="primary"
                size="small"
                @click="openCreateCriterion"
              >
                {{ t('srmRiskConfig.create') }}
              </el-button>
            </div>
          </template>
          <el-empty v-if="!selectedType" :description="t('srmRiskConfig.selectTypeFirst')" />
          <el-table
            v-else
            :data="currentCriteria"
            stripe
            border
            size="small"
          >
            <el-table-column prop="criterionLabel" :label="t('srmRiskConfig.criterionDescription')" min-width="200" />
            <el-table-column prop="score" :label="t('srmRiskConfig.score')" width="80" align="center" />
            <el-table-column :label="t('srmSupplierOverview.riskLevel')" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="sort" :label="t('procurementMaterialPage.sort')" width="70" align="center" />
            <el-table-column :label="t('common.actions')" width="120" align="center">
              <template #default="{ row }">
                <template v-if="!isAutoCalc">
                  <el-button v-permission="'srm:risk:config:update'" link type="primary" size="small" @click="openEditCriterion(row)">
                    {{ t('common.edit') }}
                  </el-button>
                  <el-button v-permission="'srm:risk:config:update'" link type="danger" size="small" @click="handleDeleteCriterion(row)">
                    {{ t('common.delete') }}
                  </el-button>
                </template>
                <span v-else style="color: #999; font-size: 12px">{{ t('srmRiskPage.autoCalculated') }}</span>
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
          <span>{{ t('srmRiskConfig.thresholdTitle') }}</span>
          <el-button v-permission="'srm:risk:config:update'" type="primary" size="small" @click="saveThresholds">
            {{ t('srmRiskConfig.saveThresholds') }}
          </el-button>
        </div>
      </template>
      <el-table v-loading="thresholdsLoading" :data="thresholds" stripe border size="small">
        <el-table-column :label="t('srmSupplierOverview.riskLevel')" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="riskTagType(row.riskLevel)">{{ riskLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('srmRiskConfig.minScore')" width="200" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.minScore" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column :label="t('srmRiskConfig.maxScore')" width="200" align="center">
          <template #default="{ row }">
            <el-input-number v-model="row.maxScore" :min="0" :max="100" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column :label="t('srmRiskConfig.description')" min-width="200">
          <template #default="{ row }">
            {{ t('srmRiskConfig.thresholdDescription', { min: row.minScore, max: row.maxScore }) }}
            <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 指标类型对话框 -->
    <el-dialog
      v-model="typeDialogVisible"
      :title="typeDialogIsEdit ? t('srmRiskConfig.editType') : t('srmRiskConfig.createType')"
      width="500px"
    >
      <el-form :model="typeForm" label-width="100px">
        <el-form-item v-if="!typeDialogIsEdit" :label="t('srmRiskConfig.code')">
          <el-input v-model="typeForm.typeCode" placeholder="FINANCIAL" :maxlength="50" />
        </el-form-item>
        <el-form-item v-else :label="t('srmRiskConfig.code')">
          <el-input :model-value="typeForm.typeCode" disabled />
        </el-form-item>
        <el-form-item :label="t('srmSupplierOverview.name')">
          <el-input v-model="typeForm.typeName" :placeholder="t('srmRiskConfig.typeNamePlaceholder')" :maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('srmRiskConfig.description')">
          <el-input v-model="typeForm.description" type="textarea" :rows="2" :maxlength="500" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.sort')">
          <el-input-number v-model="typeForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('srmRiskConfig.autoCalc')">
          <el-switch v-model="typeForm.autoCalc" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #999; font-size: 12px">{{ t('srmRiskConfig.autoCalcHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('srmSupplierOverview.status')">
          <el-switch
            v-model="typeForm.status"
            :active-value="1"
            :inactive-value="0"
            :active-text="t('common.enabled')"
            :inactive-text="t('common.disabled')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitType">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 评分标准对话框 -->
    <el-dialog
      v-model="criterionDialogVisible"
      :title="criterionDialogIsEdit ? t('srmRiskConfig.editCriterion') : t('srmRiskConfig.createCriterion')"
      width="500px"
    >
      <el-form :model="criterionForm" label-width="100px">
        <el-form-item :label="t('srmRiskConfig.criterionDescription')">
          <el-input v-model="criterionForm.criterionLabel" :placeholder="t('srmRiskConfig.criterionPlaceholder')" :maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('srmRiskConfig.score')">
          <el-input-number v-model="criterionForm.score" :min="0" :max="100" />
          <span style="margin-left: 8px; color: #999; font-size: 12px">{{ t('srmRiskConfig.higherScoreRiskier') }}</span>
        </el-form-item>
        <el-form-item :label="t('srmSupplierOverview.riskLevel')">
          <el-select v-model="criterionForm.riskLevel">
            <el-option :label="`${t('srmRiskPage.riskLow')} (GREEN)`" value="GREEN" />
            <el-option :label="`${t('srmRiskPage.riskMedium')} (YELLOW)`" value="YELLOW" />
            <el-option :label="`${t('srmRiskPage.riskHigh')} (RED)`" value="RED" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.sort')">
          <el-input-number v-model="criterionForm.sort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('srmSupplierOverview.status')">
          <el-switch
            v-model="criterionForm.status"
            :active-value="1"
            :inactive-value="0"
            :active-text="t('common.enabled')"
            :inactive-text="t('common.disabled')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="criterionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitCriterion">{{ t('common.confirm') }}</el-button>
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
