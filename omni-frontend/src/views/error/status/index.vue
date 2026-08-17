<script setup lang="ts">
/**
 * 统一的 403/404 路由恢复页面。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const props = defineProps<{ statusCode: 403 | 404 }>()
const router = useRouter()
const { t } = useI18n()

const title = computed(() => props.statusCode === 403
  ? t('errorPage.forbiddenTitle')
  : t('errorPage.notFoundTitle'))
const description = computed(() => props.statusCode === 403
  ? t('errorPage.forbiddenDescription')
  : t('errorPage.notFoundDescription'))
</script>

<template>
  <main class="status-page">
    <el-result :icon="statusCode === 403 ? 'warning' : 'info'" :title="`${statusCode} ${title}`" :sub-title="description">
      <template #extra>
        <el-button type="primary" @click="router.push('/')">
          {{ t('errorPage.backHome') }}
        </el-button>
        <el-button @click="router.back()">
          {{ t('errorPage.goBack') }}
        </el-button>
      </template>
    </el-result>
  </main>
</template>

<style scoped lang="scss">
.status-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  padding: 32px;
  background: var(--el-bg-color-page);
}
</style>
