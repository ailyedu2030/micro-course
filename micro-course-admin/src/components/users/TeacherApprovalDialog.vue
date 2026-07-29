<!--
  TeacherApprovalDialog — 教师入驻审核弹窗
  从 users/UserList.vue 提取
-->
<template>
  <el-dialog
    :model-value="visible"
    title="教师入驻审核"
    width="700px"
    destroy-on-close
    :close-on-press-escape="true"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--space-4)">
      <template #title>
        待审核教师列表。审核通过后，教师将获得创建课程的权限。
      </template>
    </el-alert>
    <el-table v-loading="teacherLoading" :aria-busy="teacherLoading" :data="pendingTeachers" stripe border class="data-table">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="username" label="账号" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="teacherNo" label="教师编号" min-width="120" />
      <el-table-column prop="departmentName" label="院系" min-width="120" />
      <el-table-column prop="createdAt" label="申请时间" min-width="160" />
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button type="success" size="small" @click="handleApprove(row)">通过</el-button>
          <el-button type="danger" size="small" @click="handleReject(row)">驳回</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="pendingTeachers.length === 0 && !teacherLoading" class="empty-tip">
      <el-empty description="暂无待审核教师" :image-size="80" />
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, updateTeacherStatus } from '@/api/user'

const props = defineProps({
  visible: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible'])

const teacherLoading = ref(false)
const pendingTeachers = ref([])

async function loadPendingTeachers() {
  teacherLoading.value = true
  try {
    const { data } = await getUsers({
      role: 'TEACHER',
      teacherStatus: 0,
      size: 100
    })
    const items = data.items || []
    pendingTeachers.value = items
      .filter(t => t.teacherStatus === 0)
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .slice(0, 20)
    if (pendingTeachers.value.length === 0) {
      ElMessage.info('暂无待审核教师')
    }
  } catch {
    pendingTeachers.value = []
    ElMessage.error('获取待审核教师列表失败')
  } finally {
    teacherLoading.value = false
  }
}

async function handleApprove(row) {
  try {
    await updateTeacherStatus(row.id, { teacherStatus: 1, reason: '' })
    ElMessage.success(`教师 ${row.realName} 审核通过`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleReject(row) {
  try {
    await updateTeacherStatus(row.id, { teacherStatus: 2, reason: '' })
    ElMessage.success(`教师 ${row.realName} 已驳回`)
    loadPendingTeachers()
  } catch {
    ElMessage.error('操作失败')
  }
}

watch(() => props.visible, (val) => {
  if (val) loadPendingTeachers()
})
</script>

<style scoped>
.data-table { width: 100%; }
.empty-tip { padding: var(--space-4) 0; }
:deep(.el-dialog) { border-radius: var(--radius-lg); }
</style>
