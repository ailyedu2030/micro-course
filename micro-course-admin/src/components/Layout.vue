<!--
  管理后台布局组件
  路由路径: (layout)
  Phase 1
  Author: jackie
-->
<template>
  <el-container class="layout-container">
    <!-- 桌面端侧边栏 -->
    <el-aside v-show="!isMobile" :width="collapsed?'64px':'200px'" class="layout-aside">
      <div class="layout-logo">
        {{ collapsed ? '微课' : '微课管理平台' }}
      </div>
      <el-menu :default-active="$route.path" :collapse="collapsed" router class="layout-menu">
        <el-menu-item index="/departments"><el-icon><OfficeBuilding /></el-icon><template #title>院系管理</template></el-menu-item>
        <el-menu-item index="/majors"><el-icon><Reading /></el-icon><template #title>专业管理</template></el-menu-item>
        <el-menu-item index="/classes"><el-icon><School /></el-icon><template #title>班级管理</template></el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon><template #title>用户管理</template></el-menu-item>
        <el-sub-menu index="admin">
          <template #title><el-icon><DataAnalysis /></el-icon><span>管理后台</span></template>
          <el-menu-item index="/admin/dashboard">数据看板</el-menu-item>
          <el-menu-item index="/admin/logs">操作日志</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="teacher">
          <template #title><el-icon><Avatar /></el-icon><span>教师端</span></template>
          <el-menu-item index="/teacher/dashboard">教师看板</el-menu-item>
          <el-menu-item index="/teacher/students">学员管理</el-menu-item>
          <el-menu-item index="/teacher/grades">成绩明细</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="gate2">
          <template #title><el-icon><Notebook /></el-icon><span>课程管理</span></template>
          <el-menu-item index="/courses">课程列表</el-menu-item>
          <el-menu-item index="/courses/review">课程审核</el-menu-item>
          <el-menu-item index="/course-categories">分类管理</el-menu-item>
          <el-menu-item index="/chapters">章节管理</el-menu-item>
          <el-menu-item index="/tags">标签管理</el-menu-item>
          <el-menu-item index="/videos">视频管理</el-menu-item>
          <el-menu-item index="/enrollments">选课管理</el-menu-item>
          <el-menu-item index="/questions">题库管理</el-menu-item>
          <el-menu-item index="/exercises">练习管理</el-menu-item>
          <el-menu-item index="/favorites">收藏管理</el-menu-item>
          <el-menu-item index="/discussions">讨论区</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <!-- 移动端 Drawer -->
    <el-drawer v-model="drawerVisible" direction="ltr" :with-header="false" size="200px">
      <div class="layout-logo">
        微课管理平台
      </div>
      <el-menu :default-active="$route.path" :collapse="collapsed" router class="layout-menu" @select="drawerVisible=false">
        <el-menu-item index="/departments"><el-icon><OfficeBuilding /></el-icon><template #title>院系管理</template></el-menu-item>
        <el-menu-item index="/majors"><el-icon><Reading /></el-icon><template #title>专业管理</template></el-menu-item>
        <el-menu-item index="/classes"><el-icon><School /></el-icon><template #title>班级管理</template></el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon><template #title>用户管理</template></el-menu-item>
        <el-sub-menu index="admin">
          <template #title><el-icon><DataAnalysis /></el-icon><span>管理后台</span></template>
          <el-menu-item index="/admin/dashboard">数据看板</el-menu-item>
          <el-menu-item index="/admin/logs">操作日志</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="teacher">
          <template #title><el-icon><Avatar /></el-icon><span>教师端</span></template>
          <el-menu-item index="/teacher/dashboard">教师看板</el-menu-item>
          <el-menu-item index="/teacher/students">学员管理</el-menu-item>
          <el-menu-item index="/teacher/grades">成绩明细</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="gate2">
          <template #title><el-icon><Notebook /></el-icon><span>课程管理</span></template>
          <el-menu-item index="/courses">课程列表</el-menu-item>
          <el-menu-item index="/courses/review">课程审核</el-menu-item>
          <el-menu-item index="/course-categories">分类管理</el-menu-item>
          <el-menu-item index="/chapters">章节管理</el-menu-item>
          <el-menu-item index="/tags">标签管理</el-menu-item>
          <el-menu-item index="/videos">视频管理</el-menu-item>
          <el-menu-item index="/enrollments">选课管理</el-menu-item>
          <el-menu-item index="/questions">题库管理</el-menu-item>
          <el-menu-item index="/exercises">练习管理</el-menu-item>
          <el-menu-item index="/favorites">收藏管理</el-menu-item>
          <el-menu-item index="/discussions">讨论区</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-drawer>
    <el-container>
      <el-header class="layout-header">
        <el-icon v-if="isMobile" class="header-icon" @click="drawerVisible=true">
          <Expand />
        </el-icon>
        <el-icon v-else class="header-icon" @click="collapsed=!collapsed">
          <Fold v-if="!collapsed" /><Expand v-else />
        </el-icon>
        <div class="header-right">
          <el-icon class="header-icon" @click="$router.push('/notifications')">
            <el-badge :value="notificationStore.unreadCount" :hidden="!notificationStore.unreadCount" :max="99"><Bell /></el-badge>
          </el-icon>
          <span class="header-username">{{ userStore.realName || userStore.username }}</span>
          <el-dropdown @command="handleCommand">
            <el-icon class="header-icon el-icon--right"><ArrowDown /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useNotificationStore } from '@/store/notification'
import { OfficeBuilding, Reading, School, User, Fold, Expand, ArrowDown, Notebook, Bell, DataAnalysis, Avatar } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()
const collapsed = ref(false)
const drawerVisible = ref(false)

const isMobile = computed(() => window.innerWidth < 768)

const handleCommand = async (cmd) => {
  if (cmd === 'logout') { await userStore.logout(); router.push('/login') }
}

onMounted(() => {
  notificationStore.startPolling(30000)
})

onUnmounted(() => {
  notificationStore.stopPolling()
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: var(--sidebar-bg, #304156);
  transition: width 300ms ease;
  overflow: hidden;
}

.layout-menu {
  background-color: var(--sidebar-bg, #304156);
  border-right: none;
  --el-menu-text-color: var(--sidebar-text, #bfcbd9);
  --el-menu-active-color: var(--sidebar-active, #409eff);
  --el-menu-hover-bg-color: var(--sidebar-hover, #1f2d3d);
  --el-menu-hover-text-color: var(--sidebar-active, #409eff);
}

.layout-menu:not(.el-menu--collapse) {
  width: 200px;
}

.layout-logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #f5f5f5;
  font-size: 18px;
  font-weight: 700;
  background: var(--sidebar-logo-bg, #1f2d3d);
}

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  transition: box-shadow 200ms ease;
}

.layout-header:hover {
  box-shadow: var(--shadow-sm);
}

.header-icon {
  font-size: 20px;
  cursor: pointer;
  color: #666;
  transition: color 200ms ease;
}

.header-icon:hover {
  color: var(--el-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
}

.header-username {
  margin-right: 8px;
  color: #606266;
  font-size: 14px;
}

.layout-main {
  background: var(--page-bg, #f0f2f5);
  padding: 12px;
}
</style>
