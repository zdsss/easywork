<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="layout-aside">
      <div class="logo">
        <span>XiaoBai 工单系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="#001529"
        text-color="#ffffffa6"
        active-text-color="#1890ff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/workorders">
          <el-icon><Document /></el-icon>
          <span>工单管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/teams">
          <el-icon><UserFilled /></el-icon>
          <span>班组管理</span>
        </el-menu-item>
        <el-menu-item index="/inspection">
          <el-icon><CircleCheck /></el-icon>
          <span>质检管理</span>
        </el-menu-item>
        <el-menu-item index="/mes">
          <el-icon><Connection /></el-icon>
          <span>MES 集成</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <span class="user-name">{{ authStore.realName }}</span>
          <el-button type="text" @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/workorders')) return '/workorders'
  return path
})

const pageTitleMap = {
  '/dashboard': '仪表盘',
  '/workorders': '工单管理',
  '/users': '用户管理',
  '/teams': '班组管理',
  '/inspection': '质检管理',
  '/mes': 'MES 集成',
}

const pageTitle = computed(() => {
  if (route.path.startsWith('/workorders/create')) return '创建工单'
  if (route.path.match(/\/workorders\/\d+/)) return '工单详情'
  return pageTitleMap[route.path] || ''
})

function handleLogout() {
  authStore.logout()
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background-color: #001529;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  border-bottom: 1px solid #002140;
}

.layout-header {
  background-color: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-name {
  color: #333;
  font-size: 14px;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 24px;
}
</style>
