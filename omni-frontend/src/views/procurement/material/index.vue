<script setup lang="ts">
/** 采购物料目录页面，维护多级品类树和租户共享物料。 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createProcurementCategory,
  createProcurementMaterial,
  deleteProcurementCategory,
  deleteProcurementMaterial,
  getProcurementMaterial,
  listProcurementCategories,
  listProcurementMaterials,
  updateProcurementCategory,
  updateProcurementMaterial,
  type CreateProcurementCategoryRequest,
  type CreateProcurementMaterialRequest,
  type ProcurementMaterial,
  type ProcurementMaterialCategory,
  type ProcurementMaterialStatus,
} from '@/api/procurement-material'

const categoryLoading = ref(false)
const materialLoading = ref(false)
const categories = ref<ProcurementMaterialCategory[]>([])
const materials = ref<ProcurementMaterial[]>([])
const selectedCategoryId = ref<number>()
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const query = reactive<{
  keyword: string
  status?: ProcurementMaterialStatus
  assetManaged?: boolean
}>({ keyword: '' })

const { t } = useI18n()

/** 物料品类选择树数据，非叶子节点 disabled。 */
const categoryTreeOptions = computed(() => {
  function buildNode(node: ProcurementMaterialCategory): {
    value: number
    label: string
    disabled: boolean
    children: ReturnType<typeof buildNode>[]
  } {
    const hasChildren = (node.children || []).length > 0
    return {
      value: node.id,
      label: node.categoryName,
      disabled: node.status !== 1 || hasChildren,
      children: (node.children || []).map(buildNode),
    }
  }
  return categories.value.map(buildNode)
})

const selectedCategory = computed(() => {
  function findNode(nodes: ProcurementMaterialCategory[]): ProcurementMaterialCategory | undefined {
    for (const node of nodes) {
      if (node.id === selectedCategoryId.value) return node
      const found = findNode(node.children || [])
      if (found) return found
    }
    return undefined
  }
  return findNode(categories.value)
})

async function loadCategories() {
  categoryLoading.value = true
  try {
    const response = await listProcurementCategories()
    categories.value = response.data.data
  } finally {
    categoryLoading.value = false
  }
}

async function loadMaterials() {
  materialLoading.value = true
  try {
    const response = await listProcurementMaterials({
      keyword: query.keyword || undefined,
      categoryId: selectedCategoryId.value,
      status: query.status,
      assetManaged: query.assetManaged,
      page: currentPage.value,
      size: pageSize.value,
    })
    materials.value = response.data.data.records
    total.value = response.data.data.total
  } finally {
    materialLoading.value = false
  }
}

function selectCategory(category?: ProcurementMaterialCategory) {
  selectedCategoryId.value = category?.id
  currentPage.value = 1
  loadMaterials()
}

function search() {
  currentPage.value = 1
  loadMaterials()
}

function resetQuery() {
  Object.assign(query, { keyword: '', status: undefined, assetManaged: undefined })
  selectedCategoryId.value = undefined
  search()
}

const categoryDialogVisible = ref(false)
const categoryFormRef = ref<FormInstance>()
const editingCategory = ref<ProcurementMaterialCategory>()
const categoryForm = reactive<CreateProcurementCategoryRequest & { version?: number }>({
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  sort: 0,
  status: 1,
})
const categoryRules: FormRules = {
  categoryCode: [
    { required: true, message: t('procurementCategoryMessages.codeRequired'), trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9_-]*$/, message: t('procurementCategoryMessages.codePattern'), trigger: 'blur' },
  ],
  categoryName: [{ required: true, message: t('procurementCategoryMessages.nameRequired'), trigger: 'blur' }],
}

function openCategoryCreate(parentId = 0) {
  editingCategory.value = undefined
  Object.assign(categoryForm, {
    parentId,
    categoryCode: '',
    categoryName: '',
    sort: 0,
    status: 1,
    version: undefined,
  })
  categoryDialogVisible.value = true
}

function openCategoryEdit(category: ProcurementMaterialCategory) {
  editingCategory.value = category
  Object.assign(categoryForm, {
    parentId: category.parentId,
    categoryCode: category.categoryCode,
    categoryName: category.categoryName,
    sort: category.sort,
    status: category.status,
    version: category.version,
  })
  categoryDialogVisible.value = true
}

async function saveCategory() {
  const valid = await categoryFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (editingCategory.value) {
    await updateProcurementCategory(editingCategory.value.id, {
      version: categoryForm.version ?? editingCategory.value.version,
      parentId: categoryForm.parentId,
      categoryName: categoryForm.categoryName.trim(),
      sort: categoryForm.sort ?? 0,
      status: categoryForm.status ?? 1,
    })
  } else {
    await createProcurementCategory({
      parentId: categoryForm.parentId,
      categoryCode: categoryForm.categoryCode.trim(),
      categoryName: categoryForm.categoryName.trim(),
      sort: categoryForm.sort ?? 0,
      status: categoryForm.status ?? 1,
    })
  }
  ElMessage.success(t('procurementMaterialPage.saveSuccess'))
  categoryDialogVisible.value = false
  await loadCategories()
}

async function removeCategory(category: ProcurementMaterialCategory) {
  try {
    await ElMessageBox.confirm(
      t('procurementMaterialPage.categoryDeleteConfirm', { name: category.categoryName }),
      t('procurementMaterialPage.deleteTitle'),
      { type: 'warning' },
    )
    await deleteProcurementCategory(category.id, category.version)
    if (selectedCategoryId.value === category.id) selectedCategoryId.value = undefined
    ElMessage.success(t('procurementMaterialPage.deleteSuccess'))
    await Promise.all([loadCategories(), loadMaterials()])
  } catch {
    // 用户取消时保持当前页面状态。
  }
}

const materialDialogVisible = ref(false)
const materialFormRef = ref<FormInstance>()
const editingMaterial = ref<ProcurementMaterial>()
const materialForm = reactive<CreateProcurementMaterialRequest & { version?: number }>({
  categoryId: 0,
  materialCode: '',
  materialName: '',
  specification: '',
  unit: 'EA',
  assetManaged: false,
  status: 'ACTIVE',
})
const materialRules: FormRules = {
  categoryId: [{ required: true, message: t('procurementMaterialMessages.categoryRequired'), trigger: 'change' }],
  materialCode: [
    { required: true, message: t('procurementMaterialMessages.codeRequired'), trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9_.-]*$/, message: t('procurementMaterialMessages.codePattern'), trigger: 'blur' },
  ],
  materialName: [{ required: true, message: t('procurementMaterialMessages.nameRequired'), trigger: 'blur' }],
  unit: [{ required: true, message: t('procurementMaterialMessages.unitRequired'), trigger: 'change' }],
}
const unitOptions = ['EA', 'PCS', 'UNIT', 'SET', 'KG', 'M', 'L', 'BOX', 'HOUR', 'SERVICE']
const assetUnits = new Set(['EA', 'PCS', 'UNIT', 'SET'])

watch(
  () => materialForm.unit,
  (unit) => {
    if (!assetUnits.has((unit || '').toUpperCase())) materialForm.assetManaged = false
  },
)

function isLeafCategory(nodes: ProcurementMaterialCategory[], id: number): boolean {
  for (const node of nodes) {
    if (node.id === id) return !node.children?.length
    if (node.children?.length && isLeafCategory(node.children, id)) return true
  }
  return false
}

function openMaterialCreate() {
  editingMaterial.value = undefined
  const defaultId =
    selectedCategoryId.value && isLeafCategory(categories.value, selectedCategoryId.value)
      ? selectedCategoryId.value
      : 0
  Object.assign(materialForm, {
    categoryId: defaultId,
    materialCode: '',
    materialName: '',
    specification: '',
    unit: 'EA',
    assetManaged: false,
    status: 'ACTIVE',
    version: undefined,
  })
  materialDialogVisible.value = true
}

async function openMaterialEdit(row: ProcurementMaterial) {
  const response = await getProcurementMaterial(row.id)
  editingMaterial.value = response.data.data
  Object.assign(materialForm, {
    categoryId: response.data.data.categoryId,
    materialCode: response.data.data.materialCode,
    materialName: response.data.data.materialName,
    specification: response.data.data.specification || '',
    unit: response.data.data.unit,
    assetManaged: response.data.data.assetManaged,
    status: response.data.data.status,
    version: response.data.data.version,
  })
  materialDialogVisible.value = true
}

async function saveMaterial() {
  const valid = await materialFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const common = {
    categoryId: materialForm.categoryId,
    materialName: materialForm.materialName.trim(),
    specification: materialForm.specification?.trim() || undefined,
    unit: materialForm.unit.trim().toUpperCase(),
    assetManaged: materialForm.assetManaged,
    status: materialForm.status || 'ACTIVE',
  }
  if (editingMaterial.value) {
    await updateProcurementMaterial(editingMaterial.value.id, {
      ...common,
      version: materialForm.version ?? editingMaterial.value.version,
      status: common.status,
    })
  } else {
    await createProcurementMaterial({
      ...common,
      materialCode: materialForm.materialCode.trim(),
    })
  }
  ElMessage.success(t('procurementMaterialPage.saveSuccess'))
  materialDialogVisible.value = false
  loadMaterials()
}

async function removeMaterial(row: ProcurementMaterial) {
  try {
    await ElMessageBox.confirm(t('procurementMaterialMessages.materialDeleteConfirm', { name: row.materialName }), t('procurementMaterialPage.deleteTitle'), {
      type: 'warning',
    })
    await deleteProcurementMaterial(row.id, row.version)
    ElMessage.success(t('procurementMaterialPage.deleteSuccess'))
    loadMaterials()
  } catch {
    // 用户取消时保持当前页面状态。
  }
}

onMounted(async () => {
  await loadCategories()
  await loadMaterials()
})
</script>

<template>
  <div class="material-page">
    <el-card class="category-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>{{ t('procurementMaterialPage.categoryPanelTitle') }}</span>
          <el-button
            v-permission="'procurement:material:create'"
            type="primary"
            link
            @click="openCategoryCreate(0)"
          >
            {{ t('procurementMaterialPage.createRootCategory') }}
          </el-button>
        </div>
      </template>
      <el-button class="all-materials" link @click="selectCategory()">{{ t('procurementMaterialPage.allMaterials') }}</el-button>
      <el-tree
        v-loading="categoryLoading"
        :data="categories"
        node-key="id"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        :props="{ label: 'categoryName', children: 'children' }"
        @node-click="selectCategory"
      >
        <template #default="{ data }">
          <div class="category-node">
            <span :class="{ inactive: data.status !== 1 }">{{ data.categoryName }}</span>
            <span class="category-actions">
              <el-button
                v-permission="'procurement:material:create'"
                link
                @click.stop="openCategoryCreate(data.id)"
              >{{ t('procurementMaterialPage.childCategory') }}</el-button>
              <el-button
                v-permission="'procurement:material:update'"
                link
                @click.stop="openCategoryEdit(data)"
              >{{ t('common.edit') }}</el-button>
              <el-button
                v-permission="'procurement:material:delete'"
                link
                type="danger"
                @click.stop="removeCategory(data)"
              >{{ t('common.delete') }}</el-button>
            </span>
          </div>
        </template>
      </el-tree>
    </el-card>

    <el-card class="material-panel" shadow="never">
      <template #header>
        <div class="panel-header">
          <span>{{ selectedCategory ? selectedCategory.categoryName : t('procurementMaterialPage.materialPanelTitleAll') }}</span>
          <el-button
            v-permission="'procurement:material:create'"
            type="primary"
            @click="openMaterialCreate"
          >
            {{ t('procurementMaterialPage.createMaterial') }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" :model="query" class="query-form">
        <el-form-item :label="t('procurementMaterialPage.keyword')">
          <el-input v-model="query.keyword" clearable :placeholder="t('procurementMaterialPage.keywordPlaceholder')" @keyup.enter="search" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.status')">
          <el-select v-model="query.status" clearable :placeholder="t('procurementApprovalRules.allStatuses')" style="width: 120px">
            <el-option :label="t('procurementApprovalRules.active')" value="ACTIVE" />
            <el-option :label="t('procurementApprovalRules.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.assetManaged')">
          <el-select v-model="query.assetManaged" clearable :placeholder="t('procurementApprovalRules.allStatuses')" style="width: 120px">
            <el-option :label="t('procurementMaterialPage.yes')" :value="true" />
            <el-option :label="t('procurementMaterialPage.no')" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">{{ t('procurementApprovalRules.search') }}</el-button>
          <el-button @click="resetQuery">{{ t('procurementApprovalRules.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="materialLoading" :data="materials" stripe>
        <el-table-column prop="materialCode" :label="t('procurementMaterialPage.materialCode')" min-width="150" />
        <el-table-column prop="materialName" :label="t('procurementMaterialPage.materialName')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="categoryName" :label="t('procurementMaterialPage.categoryName')" min-width="140" />
        <el-table-column prop="specification" :label="t('procurementMaterialPage.specification')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="unit" :label="t('procurementMaterialPage.unit')" width="80" />
        <el-table-column :label="t('procurementMaterialPage.assetManaged')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.assetManaged ? 'success' : 'info'">
              {{ row.assetManaged ? t('procurementMaterialPage.yes') : t('procurementMaterialPage.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('procurementMaterialPage.status')" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === 'ACTIVE' ? t('procurementMaterialPage.active') : t('procurementMaterialPage.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('procurementMaterialPage.actions')" width="150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'procurement:material:update'"
              link
              type="primary"
              @click="openMaterialEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
            <el-button
              v-permission="'procurement:material:delete'"
              link
              type="danger"
              @click="removeMaterial(row)"
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
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadMaterials"
        @size-change="search"
      />
    </el-card>

    <el-dialog
      v-model="categoryDialogVisible"
      :title="editingCategory ? t('procurementMaterialPage.editCategoryTitle') : t('procurementMaterialPage.createCategoryTitle')"
      width="520px"
      destroy-on-close
    >
      <el-form ref="categoryFormRef" :model="categoryForm" :rules="categoryRules" label-width="100px">
        <el-form-item :label="t('procurementMaterialPage.parentCategory')">
          <el-tree-select
            v-model="categoryForm.parentId"
            :data="[{ value: 0, label: t('procurementMaterialPage.noParentCategory'), disabled: false, children: categoryTreeOptions }]"
            :disabled="Boolean(editingCategory)"
            :check-strictly="true"
            filterable
            :render-after-expand="false"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.categoryCode')" prop="categoryCode">
          <el-input v-model="categoryForm.categoryCode" :disabled="Boolean(editingCategory)" maxlength="50" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.categoryName')" prop="categoryName">
          <el-input v-model="categoryForm.categoryName" maxlength="100" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.sort')">
          <el-input-number v-model="categoryForm.sort" :min="0" :max="999999" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.status')">
          <el-switch v-model="categoryForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="categoryDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveCategory">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="materialDialogVisible"
      :title="editingMaterial ? t('procurementMaterialPage.editMaterialTitle') : t('procurementMaterialPage.createMaterialTitle')"
      width="620px"
      destroy-on-close
    >
      <el-form ref="materialFormRef" :model="materialForm" :rules="materialRules" label-width="100px">
        <el-form-item :label="t('procurementMaterialPage.materialCode')" prop="materialCode">
          <el-input v-model="materialForm.materialCode" :disabled="Boolean(editingMaterial)" maxlength="64" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.materialName')" prop="materialName">
          <el-input v-model="materialForm.materialName" maxlength="200" />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.category')" prop="categoryId">
          <el-tree-select
            v-model="materialForm.categoryId"
            :data="categoryTreeOptions"
            filterable
            :check-strictly="true"
            :render-after-expand="false"
            :placeholder="t('procurementMaterialPage.leafCategoryPlaceholder')"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.specification')">
          <el-input v-model="materialForm.specification" type="textarea" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.unit')" prop="unit">
          <el-select v-model="materialForm.unit" allow-create filterable style="width: 100%">
            <el-option v-for="unit in unitOptions" :key="unit" :label="unit" :value="unit" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.assetManaged')">
          <el-switch
            v-model="materialForm.assetManaged"
            :disabled="!assetUnits.has((materialForm.unit || '').toUpperCase())"
          />
          <span class="form-tip">{{ t('procurementMaterialPage.assetUnitTip') }}</span>
        </el-form-item>
        <el-form-item :label="t('procurementMaterialPage.status')">
          <el-radio-group v-model="materialForm.status">
            <el-radio value="ACTIVE">{{ t('procurementMaterialPage.active') }}</el-radio>
            <el-radio value="INACTIVE">{{ t('procurementMaterialPage.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="materialDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveMaterial">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.material-page {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 16px;
}

.category-panel,
.material-panel {
  min-height: calc(100vh - 132px);
}

.panel-header,
.category-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.category-node {
  width: 100%;
  min-width: 0;
}

.category-actions {
  display: none;
  white-space: nowrap;
}

.category-node:hover .category-actions {
  display: inline-flex;
}

.category-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}

.inactive {
  color: var(--el-text-color-placeholder);
  text-decoration: line-through;
}

.all-materials {
  margin-bottom: 8px;
}

.query-form {
  margin-bottom: 4px;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.form-tip {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .material-page {
    grid-template-columns: 1fr;
  }

  .category-panel,
  .material-panel {
    min-height: auto;
  }
}
</style>
