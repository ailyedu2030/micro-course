<!--
  学生端布局组件
  路由路径: (layout)
  Phase 2
  Author: jackie
-->
<template>
  <div class="student-container">
    <router-view />
    <div class="tabbar">
      <router-link to="/student/courses" class="tab-item" active-class="active">
        <el-icon><Grid /></el-icon><span>广场</span>
      </router-link>
      <router-link to="/student/my-courses" class="tab-item" active-class="active">
        <el-icon><VideoPlay /></el-icon><span>课程</span>
      </router-link>
      <router-link to="/student/learning" class="tab-item" active-class="active">
        <el-icon><DataLine /></el-icon><span>学习</span>
      </router-link>
      <router-link to="/student/notifications" class="tab-item" active-class="active">
        <el-icon><Bell /></el-icon><span>消息</span>
      </router-link>
      <el-dropdown trigger="click" @command="handleDropdownCommand">
        <router-link to="/student/profile" class="tab-item" active-class="active">
          <el-icon><User /></el-icon><span>我的</span>
        </router-link>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="reviews">
              <el-icon><Star /></el-icon>我的评价
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>设置
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Grid, VideoPlay, DataLine, Bell, User, Star, Setting } from '@element-plus/icons-vue'

const router = useRouter()

const handleDropdownCommand = (command) => {
  if (command === 'reviews') {
    router.push('/student/reviews')
  } else if (command === 'settings') {
    router.push('/student/settings')
  }
}
</script>

<style scoped>
.student-container {
  min-height: 100vh;
  padding-bottom: 70px;
  background: #f5f5f5;
}

.tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: #fff;
  display: flex;
  justify-content: space-around;
  align-items: center;
  border-top: 1px solid #eee;
  z-index: 100;
  box-shadow: 0 -1px 4px rgba(0, 0, 0, 0.06);
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 11px;
  color: #999;
  text-decoration: none;
}

.tab-item .el-icon {
  font-size: 22px;
  margin-bottom: 2px;
}

.tab-item.active {
  color: #409eff;
}

:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  padding: 8px 16px;
}

:deep(.el-dropdown-menu__item .el-icon) {
  font-size: 16px;
}

@media (min-width: 768px) {
  .tabbar {
    max-width: 480px;
    left: 50%;
    transform: translateX(-50%);
    border-radius: 12px 12px 0 0;
  }
}
</style>
