<script setup lang="ts">
/**
 * 仪表盘页面组件。
 * 展示已交付能力与技术基线；未接入的运营指标不生成模拟值。
 */
</script>

<template>
  <div class="dashboard">
    <el-alert
      title="运营指标尚未接入统一 Metrics 数据源"
      description="用户数、调用量和错误率不再展示模拟值；请从各业务概览页查看有权访问的真实业务统计。"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- 欢迎信息和技术栈描述 -->
    <el-card class="welcome-card" style="margin-top: var(--omni-space-lg)">
      <template #header>欢迎</template>
      <p>Omni-Stack 微服务管理平台已就绪。</p>
      <el-descriptions :column="2" border style="margin-top: var(--omni-space-md)">
        <el-descriptions-item label="后端">Spring Boot 4.0.6 + Spring Cloud 2025.1.1</el-descriptions-item>
        <el-descriptions-item label="前端">Vue 3.5.35 + Vite 8.2.1 + TypeScript 5.9</el-descriptions-item>
        <el-descriptions-item label="核心服务">Auth / Base / Workflow / CRM / SRM / Procurement / Asset / Gateway</el-descriptions-item>
        <el-descriptions-item label="基础设施">MySQL / Redis / Nacos / RocketMQ / XXL-JOB</el-descriptions-item>
        <el-descriptions-item label="服务调用">OpenFeign + Nacos Discovery</el-descriptions-item>
        <el-descriptions-item label="可靠消息">Transactional Outbox + RocketMQ</el-descriptions-item>
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
