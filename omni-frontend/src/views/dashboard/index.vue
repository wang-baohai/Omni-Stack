<script setup lang="ts">
/**
 * 仪表盘页面组件。
 * 展示已交付能力与技术基线；未接入的运营指标不生成模拟值。
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <div class="dashboard">
    <el-alert
      :title="t('dashboardPage.metricsUnavailable')"
      :description="t('dashboardPage.metricsDescription')"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- 欢迎信息和技术栈描述 -->
    <el-card class="welcome-card" style="margin-top: var(--omni-space-lg)">
      <template #header>{{ t('dashboardPage.welcome') }}</template>
      <p>{{ t('dashboardPage.ready') }}</p>
      <el-descriptions :column="2" border style="margin-top: var(--omni-space-md)">
        <el-descriptions-item :label="t('dashboardPage.backend')">Spring Boot 4.0.6 + Spring Cloud 2025.1.1</el-descriptions-item>
        <el-descriptions-item :label="t('dashboardPage.frontend')">Vue 3.5.35 + Vite 8.2.1 + TypeScript 5.9</el-descriptions-item>
        <el-descriptions-item :label="t('dashboardPage.coreServices')">Auth / Base / Workflow / CRM / SRM / Procurement / Asset / Gateway</el-descriptions-item>
        <el-descriptions-item :label="t('dashboardPage.infrastructure')">MySQL / Redis / Nacos / RocketMQ / XXL-JOB</el-descriptions-item>
        <el-descriptions-item :label="t('dashboardPage.serviceCalls')">OpenFeign + Nacos Discovery</el-descriptions-item>
        <el-descriptions-item :label="t('dashboardPage.reliableMessaging')">Transactional Outbox + RocketMQ</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  animation: omni-fade-in-up 0.5s var(--omni-ease-smooth) both;
}

.welcome-card {
  p {
    color: var(--omni-text-secondary);
    line-height: 1.6;
  }
}
</style>
