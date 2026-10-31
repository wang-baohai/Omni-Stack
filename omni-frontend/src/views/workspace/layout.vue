<script setup lang="ts">
/**
 * 个人工作台布局壳层：共享顶栏提供全授权应用、搜索、语言、主题和账户。
 * 品牌只展示当前工作台名称，不提供返回官网/Wiki或独立私有子页的导航。
 * 主体由工作台驾驶舱及兼容路由承载，业务列表继续沿用原权限和接口。
 * 壳层自身不发起任何私有统计或业务请求；数据加载由各子页按现有守卫与 API 负责。
 */
import { useI18n } from 'vue-i18n'
import ConsoleHeader from '@/components/ConsoleHeader.vue'

const { t } = useI18n()
</script>

<template>
  <div class="workspace-layout">
    <ConsoleHeader>
      <template #brand>
        <strong class="workspace-brand">{{ t('workspace.title') }}</strong>
      </template>
    </ConsoleHeader>

    <main class="ws-body">
      <router-view />
    </main>
  </div>
</template>

<style scoped lang="scss">
.workspace-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--omni-bg-base);
}

.workspace-brand {
  color: var(--omni-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.ws-body {
  flex: 1;
  min-width: 0;
  max-width: 1800px;
  width: 100%;
  margin: 0 auto;
  padding: var(--omni-space-xl);
}

@media (max-width: 768px) {
  .ws-body { padding: var(--omni-space-md); }
}
</style>
