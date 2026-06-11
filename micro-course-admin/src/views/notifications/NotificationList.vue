<!--
  通知列表
  路由路径: /notifications
  Phase 2
  Author: jackie
-->
<template>
  <div class="notification-list">
    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="left-info">
          <span class="unread-tip">未读消息：<el-badge :value="unreadCount" :max="99" /></span>
        </div>
        <el-button type="primary" @click="handleMarkAllRead" :disabled="!unreadCount">全部标记已读</el-button>
      </div>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column prop="type" label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.type === 'COURSE_APPROVED'" type="success" size="small">课程通过</el-tag>
            <el-tag v-else-if="row.type === 'COURSE_REJECTED'" type="danger" size="small">课程驳回</el-tag>
            <el-tag v-else-if="row.type === 'NEW_COURSE'" type="warning" size="small">新课发布</el-tag>
            <el-tag v-else type="info" size="small">系统通知</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="200">
          <template #default="{ row }">
            {{ truncate(row.content, 50) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="isRead" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRead" type="info" size="small">已读</el-tag>
            <el-tag v-else type="warning" size="small">未读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" type="primary" link size="small" @click="handleMarkRead(row)">标记已读</el-button>
            <span v-else class="dash-placeholder">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotifications, markAsRead, markAllAsRead, getUnreadCount } from '@/api/notification'

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const unreadCount = ref(0)

const truncate = (text, length) => {
  if (!text) return ''
  return text.length > length ? text.substring(0, length) + '…' : text
}

const fetchUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data || 0
  } catch (e) {
    ElMessage.error('获取未读数量失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    const res = await getNotifications(params)
    tableData.value = res.data?.items || []
    totalElements.value = res.data?.totalElements || 0
  } catch (e) {
    ElMessage.error('获取通知列表失败')
  } finally {
    loading.value = false
  }
}

const handleMarkRead = async (row) => {
  try {
    await markAsRead(row.id)
    row.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    ElMessage.success('已标记为已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleMarkAllRead = async () => {
  try {
    await markAllAsRead()
    unreadCount.value = 0
    tableData.value.forEach(n => { n.isRead = true })
    ElMessage.success('全部已标记为已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

onMounted(() => {
  fetchData()
  fetchUnreadCount()
})
</script>

<style scoped>
.notification-list {
  padding: 20px;
}

.toolbar-card {
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-info {
  display: flex;
  align-items: center;
}

.unread-tip {
  font-size: 14px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.dash-placeholder {
  color: #999;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.data-table { width: 100%; }
</style>