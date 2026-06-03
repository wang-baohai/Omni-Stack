<script setup lang="ts">
/**
 * 仪表盘页面组件。
 * 展示系统概览统计数据（模拟数据）和技术栈信息。
 * 后续可接入真实数据源。
 */
</script>

<template>
  <div class="dashboard">
    <!-- 统计卡片区域 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>用户总数</template>
          <div class="stat-value">1,024</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>活跃服务</template>
          <div class="stat-value">12</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>今日 API 调用</template>
          <div class="stat-value">8,432</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>错误率</template>
          <div class="stat-value">0.12%</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 欢迎信息和技术栈描述 -->
    <el-card class="welcome-card" style="margin-top: var(--omni-space-lg)">
      <template #header>欢迎</template>
      <p>Omni-Stack 微服务管理平台已就绪。</p>
      <el-descriptions :column="2" border style="margin-top: var(--omni-space-md)">
        <el-descriptions-item label="后端">Spring Boot 4.0 + Spring Cloud 2025 + Spring Cloud Alibaba</el-descriptions-item>
        <el-descriptions-item label="前端">Vue 3.5 + Vite 6 + Pinia + TypeScript</el-descriptions-item>
        <el-descriptions-item label="注册中心">Nacos</el-descriptions-item>
        <el-descriptions-item label="网关">Spring Cloud Gateway</el-descriptions-item>
        <el-descriptions-item label="服务调用">OpenFeign</el-descriptions-item>
        <el-descriptions-item label="熔断降级">Sentinel</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.dashboard {
  animation: omni-fade-in-up 0.5s var(--omni-ease-smooth) both;
}

.stat-card {
  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--omni-shadow-lg), var(--omni-shadow-glow);
  }
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  text-align: center;
  background: var(--omni-gradient-text);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: -0.02em;
}

.welcome-card {
  p {
    color: var(--omni-text-secondary);
    line-height: 1.6;
  }
}
</style>
