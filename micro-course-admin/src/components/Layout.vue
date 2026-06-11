<template>
  <el-container style="height:100vh">
    <el-aside :width="collapsed?'64px':'200px'" style="background:#304156;transition:width .3s;overflow:hidden">
      <div style="height:60px;line-height:60px;text-align:center;color:#fff;font-size:18px;font-weight:700;background:#1f2d3d">
        {{ collapsed ? '微课' : '微课管理平台' }}
      </div>
      <el-menu :default-active="$route.path" :collapse="collapsed" router background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/departments"><el-icon><OfficeBuilding /></el-icon><template #title>院系管理</template></el-menu-item>
        <el-menu-item index="/majors"><el-icon><Reading /></el-icon><template #title>专业管理</template></el-menu-item>
        <el-menu-item index="/classes"><el-icon><School /></el-icon><template #title>班级管理</template></el-menu-item>
        <el-menu-item index="/users"><el-icon><User /></el-icon><template #title>用户管理</template></el-menu-item>
        <el-menu-item index="/courses"><el-icon><Notebook /></el-icon><template #title>课程管理</template></el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header style="background:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 20px;box-shadow:0 1px 4px rgba(0,0,0,.1)">
        <el-icon style="font-size:20px;cursor:pointer;color:#666" @click="collapsed=!collapsed">
          <Fold v-if="!collapsed" /><Expand v-else />
        </el-icon>
        <div style="display:flex;align-items:center">
          <span style="margin-right:10px;color:#666">{{ userStore.realName || userStore.username }}</span>
          <el-dropdown @command="handleCommand">
            <el-icon class="el-icon--right" style="cursor:pointer"><ArrowDown /></el-icon>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main style="background:#f0f2f5;padding:20px"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { OfficeBuilding, Reading, School, User, Fold, Expand, ArrowDown, Notebook } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const handleCommand = async (cmd) => {
  if (cmd === 'logout') { await userStore.logout(); router.push('/login') }
}
</script>
