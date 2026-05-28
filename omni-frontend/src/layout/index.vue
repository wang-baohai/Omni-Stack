<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <el-container>
      <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="sidebar">
        <div class="logo">
          <h1 v-show="!appStore.sidebarCollapsed">Omni-Stack</h1>
          <h1 v-show="appStore.sidebarCollapsed">O</h1>
        </div>
        <el-menu
          :default-active="route.path"
          :collapse="appStore.sidebarCollapsed"
          router
          background-color="#304156"
          text-color="#bfcbd9"
          active-text-color="#409eff"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>Dashboard</template>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container>
        <el-header class="header">
          <div class="header-left">
            <el-icon class="collapse-btn" @click="appStore.toggleSidebar">
              <Fold v-if="!appStore.sidebarCollapsed" />
              <Expand v-else />
            </el-icon>
          </div>
          <div class="header-right">
            <el-dropdown>
              <span class="user-info">
                <el-icon><User /></el-icon>
                {{ userStore.username || 'Admin' }}
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">Logout</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main class="main-content">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>
