<!--
  通知列表
  路由路径: /notifications
  Phase 2
  Author: jackie
-->
<template>
  <div class="notification-list-page">
    <!-- 顶栏 -->
    <el-card class="toolbar-card" shadow="never">
      <div class="toolbar">
        <div class="left-info">
          <span class="unread-tip">未读消息：<el-badge :value="unreadCount" :max="99" /></span>
        </div>
        <el-button type="primary" @click="handleMarkAllRead" :disabled="!unreadCount">全部标记已读</el-button>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <!-- 类型过滤标签页 -->
      <div class="type-filter-bar">
        <el-button
          v-for="tab in typeTabs"
          :key="tab.value"
          :type="typeFilter === tab.value ? 'primary' : 'default'"
          size="small"
          @click="handleTypeChange(tab.value)"
        >
{{ tab.label }}
</el-button>
      </div>

      <!-- 骨架屏 -->
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无通知消息"
        :image-size="120"
      />

      <!-- ====== PC 数据表格 (> 768px) ====== -->
      <el-table
        v-else-if="!isMobile"
        :data="tableData"
        stripe
        border
        class="data-table"
        :row-class-name="rowClassName"
        @row-click="handleRowClick"
        style="cursor: pointer;"
      >
        <el-table-column prop="type" label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="getNotifTagType(row.type)" size="small" effect="light">
              {{ getNotifTagLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'title-unread': !row.isRead }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="200">
          <template #default="{ row }">
            {{ truncate(row.content, 50) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column prop="isRead" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-badge v-if="!row.isRead" is-dot class="unread-dot">
              <el-tag type="warning" size="small" effect="light">未读</el-tag>
            </el-badge>
            <el-tag v-else type="info" size="small" effect="light">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" type="primary" link size="small" @click.stop="handleMarkRead(row)">标记已读</el-button>
            <span v-else class="dash-placeholder">—</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- ====== H5 卡片列表 (≤ 768px) ====== -->
      <div v-else class="card-list">
        <div
          v-for="row in tableData"
          :key="row.id"
          class="notification-card"
          :class="{ 'card-unread': !row.isRead }"
          @click="handleRowClick(row)"
        >
          <div class="card-header">
            <el-tag :type="getNotifTagType(row.type)" size="small" effect="light">
              {{ getNotifTagLabel(row.type) }}
            </el-tag>
            <span class="card-time">{{ row.createdAt }}</span>
          </div>
          <div class="card-title" :class="{ 'title-unread': !row.isRead }">{{ row.title }}</div>
          <div class="card-content">{{ truncate(row.content, 80) }}</div>
          <div class="card-footer">
            <el-badge v-if="!row.isRead" is-dot class="unread-dot">
              <el-tag type="warning" size="small" effect="light">未读</el-tag>
            </el-badge>
            <el-tag v-else type="info" size="small" effect="light">已读</el-tag>
            <el-button
              v-if="!row.isRead"
              type="primary"
              link
              size="small"
              @click.stop="handleMarkRead(row)"
            >
标记已读
</el-button>
          </div>
        </div>
      </div>

      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useNotificationStore } from '@/store/notification'

// ---------------------------------------------------------------------------
// Store & Router
// ---------------------------------------------------------------------------
const notificationStore = useNotificationStore()
const route = useRoute()
const router = useRouter()

// ---------------------------------------------------------------------------
// P0-5: 统一使用 Store 的 unreadCount（移除组件本地 unreadCount ref）
// ---------------------------------------------------------------------------
const unreadCount = computed(() => notificationStore.unreadCount)

// ---------------------------------------------------------------------------
// 响应式：移动端检测
// ---------------------------------------------------------------------------
const isMobile = ref(window.innerWidth <= 768)
function onResize() { isMobile.value = window.innerWidth <= 768 }
onMounted(() => window.addEventListener('resize', onResize))
onUnmounted(() => window.removeEventListener('resize', onResize))

// ---------------------------------------------------------------------------
// 分页 & 过滤状态（P2: 从 URL query 初始化）
// ---------------------------------------------------------------------------
const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(Number(route.query.page) || 1)
const size = ref(Number(route.query.size) || 10)
const typeFilter = ref(route.query.type || '')

const typeTabs = [
  { label: '全部', value: '' },
  { label: '选课', value: 'ENROLLMENT' },
  { label: '成绩', value: 'GRADE' },
  { label: '讨论', value: 'DISCUSSION' },
  { label: '系统', value: 'SYSTEM' }
]

const truncate = (text, length) => {
  if (!text) return ''
  return text.length > length ? text.substring(0, length) + '…' : text
}

// 通知类型标签映射: 系统(blue), 课程/选课(green), 成绩/考试(orange)
const notifTagMap = {
  SYSTEM: { label: '系统通知', type: 'primary' },     // 蓝色
  ENROLLMENT: { label: '课程通知', type: 'success' }, // 绿色
  GRADE: { label: '考试通知', type: 'warning' },      // 橙色
  DISCUSSION: { label: '讨论通知', type: 'primary' },  // 蓝色
  EXAM: { label: '考试通知', type: 'warning' }         // 橙色
}

function getNotifTagType(type) {
  return notifTagMap[type]?.type || 'info'
}

function getNotifTagLabel(type) {
  return notifTagMap[type]?.label ?? '系统通知'
}

// 未读行高亮
function rowClassName({ row }) {
  return row.isRead ? '' : 'row-unread'
}

// ---------------------------------------------------------------------------
// P2: 同步分页/过滤状态到 URL query
// ---------------------------------------------------------------------------
function syncQueryToUrl() {
  const query = {}
  if (page.value !== 1) query.page = page.value
  if (size.value !== 10) query.size = size.value
  if (typeFilter.value) query.type = typeFilter.value
  router.replace({ query })
}

// ---------------------------------------------------------------------------
// P2: 统一使用 Store action 获取数据
// ---------------------------------------------------------------------------
const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    if (typeFilter.value) params.type = typeFilter.value
    const data = await notificationStore.fetchList(params)
    tableData.value = notificationStore.list
    totalElements.value = notificationStore.totalElements
  } catch {
    ElMessage.error('获取通知列表失败')
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------------------
// P2: 统一使用 Store action 标记已读
// ---------------------------------------------------------------------------
const handleMarkRead = async (row) => {
  await notificationStore.markRead(row.id)
  // store.markRead 已更新 list 里的 isRead 和 unreadCount
  const item = tableData.value.find(n => n.id === row.id)
  if (item) item.isRead = true
}

// ---------------------------------------------------------------------------
// P1: "全部标记已读" 添加二次确认
// ---------------------------------------------------------------------------
const handleMarkAllRead = async () => {
  try {
    await ElMessageBox.confirm(
      `确认将所有 ${unreadCount.value} 条未读消息标记为已读？`,
      '全部标记已读',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  await notificationStore.markAllRead()
  tableData.value.forEach(n => { n.isRead = true })
  ElMessage.success('全部已标记为已读')
}

// ---------------------------------------------------------------------------
// P1: 通知行点击跳转（根据 type + relatedId）
// ---------------------------------------------------------------------------
const ROUTE_MAP = {
  ENROLLMENT: (id) => `/student/courses/${id}`,
  GRADE: (id) => `/student/courses/${id}`,
  DISCUSSION: (id) => `/student/courses/${id}`,
}

async function handleRowClick(row) {
  // 自动标记已读
  if (!row.isRead) {
    await notificationStore.markRead(row.id)
    row.isRead = true
  }
  // 有关联资源则跳转
  if (row.relatedId && ROUTE_MAP[row.type]) {
    router.push(ROUTE_MAP[row.type](row.relatedId))
  }
}

const handleSizeChange = () => {
  page.value = 1
  syncQueryToUrl()
  fetchData()
}

const handlePageChange = () => {
  syncQueryToUrl()
  fetchData()
}

const handleTypeChange = (value) => {
  typeFilter.value = value
  page.value = 1
  syncQueryToUrl()
  fetchData()
}

// ---------------------------------------------------------------------------
// 初始化
// ---------------------------------------------------------------------------
onMounted(() => {
  fetchData()
  notificationStore.fetchUnreadCount()
})
</script>

<style scoped>
.notification-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.toolbar-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.type-filter-bar {
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9) !important;
}

.dash-placeholder {
  color: var(--el-text-color-placeholder);
}

/* 未读行高亮 */
.data-table :deep(.row-unread) {
  background-color: var(--role-primary-light-9) !important;
}

.data-table :deep(.row-unread:hover > td) {
  background-color: var(--role-primary-light-7) !important;
}

.title-unread {
  font-weight: var(--weight-semibold);
  color: var(--role-primary);
}

/* 未读圆点 */
.unread-dot {
  line-height: 1;
}

.unread-dot :deep(.el-badge__content.is-dot) {
  top: 2px;
  right: -2px;
}

/* ====== P1: H5 卡片列表 (≤ 768px) ====== */
.card-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.notification-card {
  background: var(--el-bg-color-overlay, #fff);
  border: 1px solid var(--el-border-color-lighter, #ebeef5);
  border-radius: var(--radius-md);
  padding: var(--space-3);
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.notification-card:active {
  background-color: var(--el-fill-color-light, #f5f7fa);
}

.notification-card.card-unread {
  background-color: var(--role-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-2);
}

.card-time {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary, #909399);
}

.card-title {
  font-size: var(--text-base);
  margin-bottom: var(--space-1);
  line-height: 1.4;
}

.card-content {
  font-size: var(--text-sm, 13px);
  color: var(--el-text-color-secondary, #909399);
  line-height: 1.5;
  margin-bottom: var(--space-2);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

@media (max-width: 768px) {
  .notification-list-page {
    padding: var(--space-3);
  }

  .toolbar-card {
    margin-bottom: var(--space-3);
  }

  .toolbar {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>
