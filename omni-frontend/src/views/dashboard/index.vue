<script setup lang="ts">
// Dashboard - add real data integration as needed
</script>

<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>Total Users</template>
          <div class="stat-value">1,024</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>Active Services</template>
          <div class="stat-value">12</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>API Calls Today</template>
          <div class="stat-value">8,432</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <template #header>Error Rate</template>
          <div class="stat-value">0.12%</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="welcome-card" style="margin-top: var(--omni-space-lg)">
      <template #header>Welcome</template>
      <p>Omni-Stack microservices management platform is ready.</p>
      <el-descriptions :column="2" border style="margin-top: var(--omni-space-md)">
        <el-descriptions-item label="Backend">Spring Boot 4.0 + Spring Cloud 2025 + Spring Cloud Alibaba</el-descriptions-item>
        <el-descriptions-item label="Frontend">Vue 3.5 + Vite 6 + Pinia + TypeScript</el-descriptions-item>
        <el-descriptions-item label="Registry">Nacos</el-descriptions-item>
        <el-descriptions-item label="Gateway">Spring Cloud Gateway</el-descriptions-item>
        <el-descriptions-item label="Service Call">OpenFeign</el-descriptions-item>
        <el-descriptions-item label="Circuit Breaker">Sentinel</el-descriptions-item>
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
