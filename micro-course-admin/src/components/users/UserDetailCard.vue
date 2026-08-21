<!--
  UserDetailCard — 用户详情信息卡片
  用于 admin/users 页面的详情弹窗
-->
<template>
  <el-dialog
    :model-value="visible"
    title="用户详情"
    width="560px"
    destroy-on-close
    :close-on-press-escape="true"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-descriptions :column="2" border v-if="user">
      <el-descriptions-item label="ID">{{ user.id || '-' }}</el-descriptions-item>
      <el-descriptions-item label="账号">{{ user.username || '-' }}</el-descriptions-item>
      <el-descriptions-item label="姓名">{{ user.realName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="角色">
        <el-tag size="small" :type="roleTagType">
          {{ roleLabel }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="院系">{{ user.departmentName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="专业">{{ user.majorName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="班级">{{ user.className || '-' }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag size="small" :type="user.status === 1 ? 'success' : 'danger'">
          {{ user.status === 1 ? '启用' : '禁用' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="注册时间" :span="2">
        {{ formatTime(user.createdAt) }}
      </el-descriptions-item>
      <el-descriptions-item label="邮箱" :span="2">{{ user.email || '-' }}</el-descriptions-item>
      <el-descriptions-item label="手机" :span="2">{{ user.phone || '-' }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  user: { type: Object, default: null }
})

defineEmits(['update:visible'])

const roleTagType = computed(() => {
  const map = { STUDENT: 'success', TEACHER: 'warning', ADMIN: 'danger', ACADEMIC: '' }
  return map[props.user?.role] || 'info'
})

const roleLabel = computed(() => {
  const map = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', ACADEMIC: '教务' }
  return map[props.user?.role] || props.user?.role || '-'
})

function formatTime(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  // P2-2026-08-21: 非法日期防御(原输出 NaN-NaN-NaN 00:00:00)
  if (isNaN(d.getTime())) return '-'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
</script>
