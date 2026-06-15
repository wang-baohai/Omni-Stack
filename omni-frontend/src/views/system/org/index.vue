<script setup lang="ts">
/**
 * 组织管理页面。
 * 以树形展示组织结构，支持增删改操作。
 */
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchOrgTree, createOrgUnit, updateOrgUnit, deleteOrgUnit,
  type OrgUnitTreeNode, type CreateOrgUnitRequest,
} from '@/api/org'

const { t } = useI18n()

/** 组织类型标签颜色映射 */
const typeTagMap: Record<string, string> = {
  ORG: 'primary',
  SUBSIDIARY: 'warning',
  DEPT: 'success',
  TEAM: 'info',
}

/**
 * 获取组织类型显示标签。
 */
function getTypeLabel(type: string): string {
  const key = `org.type${type.charAt(0) + type.slice(1).toLowerCase()}` as 'org.typeOrg' | 'org.typeSubsidiary' | 'org.typeDept' | 'org.typeTeam'
  return t(key)
}

/**
 * 获取组织类型标签颜色。
 */
function getTypeTagType(type: string): string {
  return typeTagMap[type] ?? 'info'
}

/** 组织树数据 */
const treeData = ref<OrgUnitTreeNode[]>([])
const loading = ref(false)

/** 表单对话框 */
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<CreateOrgUnitRequest & { name: string; type: string }>({
  parentId: 0,
  name: '',
  type: 'DEPT',
  sort: 0,
  status: 1,
})

/** 当前选中的节点 */
const selectedNode = ref<OrgUnitTreeNode | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await fetchOrgTree()
    treeData.value = res.data.data
  } finally {
    loading.value = false
  }
}

function handleNodeClick(data: OrgUnitTreeNode) {
  selectedNode.value = data
}

function openCreateDialog(parentId: number = 0) {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { parentId, name: '', type: 'DEPT', sort: 0, status: 1 })
  formDialogVisible.value = true
}

function openEditDialog(node: OrgUnitTreeNode) {
  isEdit.value = true
  editingId.value = node.id
  Object.assign(form, {
    parentId: node.parentId,
    name: node.name,
    type: node.type,
    sort: node.sort,
    status: node.status,
  })
  formDialogVisible.value = true
}

async function saveForm() {
  if (isEdit.value && editingId.value) {
    await updateOrgUnit(editingId.value, {
      name: form.name,
      type: form.type,
      sort: form.sort,
      status: form.status,
    })
  } else {
    await createOrgUnit(form)
  }
  ElMessage.success(t('common.success'))
  formDialogVisible.value = false
  loadData()
}

async function handleDelete(node: OrgUnitTreeNode) {
  try {
    await ElMessageBox.confirm(t('org.confirmDelete'), { type: 'warning' })
    await deleteOrgUnit(node.id)
    ElMessage.success(t('common.success'))
    loadData()
  } catch { /* cancelled */ }
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.organizations') }}</span>
          <el-button v-permission="'system:org:create'" type="primary" @click="openCreateDialog(0)">{{ t('common.create') }}</el-button>
        </div>
      </template>

      <el-tree
        v-loading="loading"
        :data="treeData"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <span class="node-label">
              <el-tag :type="getTypeTagType(data.type)" size="small">
                {{ getTypeLabel(data.type) }}
              </el-tag>
              {{ node.label }}
            </span>
            <span class="node-actions">
              <el-button v-permission="'system:org:create'" size="small" text type="primary" @click.stop="openCreateDialog(data.id)">
                {{ t('common.create') }}
              </el-button>
              <el-button v-permission="'system:org:update'" size="small" text type="warning" @click.stop="openEditDialog(data)">
                {{ t('common.edit') }}
              </el-button>
              <el-button v-permission="'system:org:delete'" size="small" text type="danger" @click.stop="handleDelete(data)">
                {{ t('common.delete') }}
              </el-button>
            </span>
          </div>
        </template>
      </el-tree>
    </el-card>

    <!-- 表单对话框 -->
    <el-dialog v-model="formDialogVisible" :title="isEdit ? t('common.edit') : t('common.create')" width="450px">
      <el-form :model="form" label-width="100px">
        <el-form-item :label="t('org.name')">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('org.type')">
          <el-select v-model="form.type">
            <el-option value="ORG" :label="t('org.typeOrg')" />
            <el-option value="SUBSIDIARY" :label="t('org.typeSubsidiary')" />
            <el-option value="DEPT" :label="t('org.typeDept')" />
            <el-option value="TEAM" :label="t('org.typeTeam')" />
          </el-select>
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
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 8px;
}
.node-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
.node-actions {
  opacity: 0;
  transition: opacity 0.2s;
}
.el-tree-node__content:hover .node-actions {
  opacity: 1;
}
</style>
