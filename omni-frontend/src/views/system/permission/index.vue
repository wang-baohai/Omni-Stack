<script setup lang="ts">
/**
 * 权限管理页面（只读）。
 * 以树形表格展示系统权限树。
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { fetchPermissionTree, type PermissionNode } from '@/api/permission'

const { t } = useI18n()

/** 权限树数据 */
const treeData = ref<PermissionNode[]>([])
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const res = await fetchPermissionTree()
    treeData.value = res.data.data
  } finally {
    loading.value = false
  }
}

/** 获取类型标签样式 */
function getTypeTagType(type: string): string {
  switch (type) {
  case 'DIRECTORY': return 'info'
  case 'MENU': return 'primary'
  case 'API': return 'success'
  default: return 'info'
  }
}

/** 翻译权限类型 */
function getTypeLabel(type: string): string {
  const keyMap: Record<string, string> = {
    DIRECTORY: 'permission.typeDirectory',
    MENU: 'permission.typeMenu',
    API: 'permission.typeApi',
  }
  const key = keyMap[type]
  return key ? t(key) : type
}

/** 翻译权限名称（基于 permissionCode 映射 i18n 键） */
function getPermName(code: string, fallback: string): string {
  const key = `permission.perm_${code.replace(/[:.]/g, '_')}`
  const translated = t(key)
  return translated !== key ? translated : fallback
}

onMounted(loadData)
</script>

<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('common.permissions') }}</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="treeData" row-key="id" border default-expand-all>
        <el-table-column prop="permissionName" :label="t('permission.name')" min-width="200">
          <template #default="{ row }">
            {{ getPermName(row.permissionCode, row.permissionName) }}
          </template>
        </el-table-column>
        <el-table-column prop="permissionCode" :label="t('permission.code')" min-width="200" />
        <el-table-column prop="type" :label="t('permission.type')" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)" size="small">
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="depth" :label="t('permission.depth')" width="80" />
        <el-table-column prop="sort" :label="t('common.sort')" width="80" />
        <el-table-column :label="t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.page-container {
  padding: 20px;
}
.card-header {
  font-weight: 600;
}
</style>
