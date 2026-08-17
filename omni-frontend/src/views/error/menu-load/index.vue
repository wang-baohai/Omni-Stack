<script setup lang="ts">
/**
 * 动态菜单加载失败恢复页。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { usePermissionStore } from '@/stores/permission'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const permissionStore = usePermissionStore()
const retrying = ref(false)

async function retry() {
  retrying.value = true
  try {
    await permissionStore.retryLoadMenus()
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch {
    ElMessage.error(t('errorPage.menuLoadRetryFailed'))
  } finally {
    retrying.value = false
  }
}
</script>

<template>
  <main class="menu-error-page">
    <el-result icon="warning" :title="t('errorPage.menuLoadTitle')" :sub-title="t('errorPage.menuLoadDescription')">
      <template #extra>
        <el-button type="primary" :loading="retrying" @click="retry">
          {{ t('errorPage.retry') }}
        </el-button>
        <el-button @click="router.push('/')">
          {{ t('errorPage.backHome') }}
        </el-button>
      </template>
    </el-result>
  </main>
</template>

<style scoped lang="scss">
.menu-error-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--el-bg-color-page);
}
</style>
