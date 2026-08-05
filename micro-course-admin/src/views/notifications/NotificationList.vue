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
          <span class="unread-tip">{{ $t('notification.unread') }}：<el-badge :value="unreadCount" :max="99" /></span>
        </div>
        <el-button type="primary" @click="handleMarkAllRead" :disabled="!unreadCount || markingAll" :loading="markingAll">{{ $t('notification.markAllRead') }}</el-button>
      </div>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <!-- 类型过滤标签页 -->
      <div class="type-filter-bar" role="group" aria-label="通知类型过滤">
        <el-button
          v-for="tab in typeTabs"
          :key="tab.value"
          :type="typeFilter === tab.value ? 'primary' : 'default'"
          size="small"
          :aria-pressed="typeFilter === tab.value"
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
        :description="$t('notification.noData')"
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
        <el-table-column prop="type" :label="$t('notification.type')" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="getNotifTagType(row.type)" size="small" effect="light">
              {{ getNotifTagLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="$t('notification.titleLabel')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'title-unread': !row.isRead }">{{ row.title }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="content" :label="$t('notification.contentLabel')" min-width="200">
          <template #default="{ row }">
            {{ truncate(row.content, 50) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('notification.time')" width="170" :formatter="$formatDateTime" />
        <el-table-column prop="isRead" :label="$t('notification.status')" width="100" align="center">
          <template #default="{ row }">
            <el-badge v-if="!row.isRead" is-dot class="unread-dot">
              <el-tag type="warning" size="small" effect="light">{{ $t('notification.unread') }}</el-tag>
            </el-badge>
            <el-tag v-else type="info" size="small" effect="light">{{ $t('notification.read') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.isRead" type="primary" link size="small" @click.stop="handleMarkRead(row)">{{ $t('notification.markRead') }}</el-button>
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
            <span class="card-time">{{ $formatDateTime(row.createdAt) }}</span>
          </div>
          <div class="card-title" :class="{ 'title-unread': !row.isRead }">{{ row.title }}</div>
          <div class="card-content">{{ truncate(row.content, 80) }}</div>
          <div class="card-footer">
            <el-badge v-if="!row.isRead" is-dot class="unread-dot">
              <el-tag type="warning" size="small" effect="light">{{ $t('notification.unread') }}</el-tag>
            </el-badge>
            <el-tag v-else type="info" size="small" effect="light">{{ $t('notification.read') }}</el-tag>
            <el-button
              v-if="!row.isRead"
              type="primary"
              link
              :size="isMobile ? 'default' : 'small'"
              @click.stop="handleMarkRead(row)"
              class="mark-read-btn"
            >
{{ $t('notification.markRead') }}
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useNotificationStore } from '@/store/notification'
import { useUserStore } from '@/store/user'
const { t } = useI18n()

// ---------------------------------------------------------------------------
// Store & Router
// ---------------------------------------------------------------------------
const notificationStore = useNotificationStore()
const userStore = useUserStore()
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
  { label: t('app.all'), value: '' },
  { label: t('notification.enrollment'), value: 'ENROLLMENT' },
  { label: t('notification.grade'), value: 'GRADE' },
  { label: t('notification.discussion'), value: 'DISCUSSION' },
  { label: t('notification.system'), value: 'SYSTEM' }
]

const truncate = (text, length) => {
  if (!text) return ''
  return text.length > length ? text.substring(0, length) + '…' : text
}

// 通知类型标签映射: 系统(blue), 课程/选课(green), 成绩/考试(orange)
const notifTagMap = {
  SYSTEM: { label: () => t('notification.system'), type: 'primary' },
  ENROLLMENT: { label: () => t('notification.enrollment'), type: 'success' },
  GRADE: { label: () => t('notification.grade'), type: 'warning' },
  DISCUSSION: { label: () => t('notification.discussion'), type: 'primary' },
  EXAM: { label: () => t('notification.grade'), type: 'warning' }
}

function getNotifTagType(type) {
  return notifTagMap[type]?.type || 'info'
}

function getNotifTagLabel(type) {
  if (notifTagMap[type]) return notifTagMap[type].label()
  if (!type) return t('notification.system')
  // 后端持久化全量类型码（如 ENROLLMENT_SUCCESS），按前缀归类展示
  if (type.startsWith('ENROLLMENT') || type.startsWith('MS_ENROLLMENT')) return t('notification.enrollment')
  if (type.startsWith('EXERCISE') || type.startsWith('GRADE') || type.startsWith('EXAM')
    || type === 'MS_CERTIFICATE_ISSUED' || type === 'MS_COMPLETED') return t('notification.grade')
  if (type.startsWith('DISCUSSION')) return t('notification.discussion')
  return t('notification.system')
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

// P1-I: 反向同步 — URL query 变化（浏览器前进/后退）→ 组件状态
watch(() => route.query, (newQuery) => {
  const qPage = Number(newQuery.page) || 1
  const qSize = Number(newQuery.size) || 10
  const qType = newQuery.type || ''
  if (qPage !== page.value || qSize !== size.value || qType !== typeFilter.value) {
    page.value = qPage
    size.value = qSize
    typeFilter.value = qType
    fetchData()
  }
})

// ---------------------------------------------------------------------------
// P2: 统一使用 Store action 获取数据
// ---------------------------------------------------------------------------
const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    if (typeFilter.value) params.type = typeFilter.value
    await notificationStore.fetchList(params)
    tableData.value = notificationStore.list
    totalElements.value = notificationStore.totalElements
  } catch {
    ElMessage.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------------------
// P2: 统一使用 Store action 标记已读
// ---------------------------------------------------------------------------
const handleMarkRead = async (row) => {
  try {
    await notificationStore.markRead(row.id)
    // store.markRead 已更新 list 里的 isRead 和 unreadCount
    const item = tableData.value.find(n => n.id === row.id)
    if (item) item.isRead = true
  } catch {
    console.warn('[Notification] markRead 失败', row.id)
  }
}

// ---------------------------------------------------------------------------
// P1: "全部标记已读" 添加二次确认 + loading 状态
// ---------------------------------------------------------------------------
const markingAll = ref(false)
const handleMarkAllRead = async () => {
  if (markingAll.value) return
  try {
    await ElMessageBox.confirm(
      `确认将所有 ${unreadCount.value} 条未读消息标记为已读？`,
      '全部标记已读',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }
  markingAll.value = true
  try {
    await notificationStore.markAllRead()
    tableData.value.forEach(n => { n.isRead = true })
    ElMessage.success(t('notification.markAllRead'))
  } catch (e) {
    ElMessage.error(t('common.failed'))
  } finally {
    markingAll.value = false
  }
}

// ---------------------------------------------------------------------------
// P1 + P1C-033: 通知行点击跳转（根据 type + relatedId，含精确路径参数）
// 覆盖全部 NotificationType 枚举值：选课/成绩/讨论/审核/系统/微专业
// ---------------------------------------------------------------------------
function resolveCoursePath(id) {
  if (!id) return null
  if (userStore.role === 'STUDENT') return `/student/courses/${id}`
  if (userStore.role === 'TEACHER') return `/teacher/courses/${id}`
  return `/courses/${id}`
}

function resolveNotificationRoute(row) {
  const id = row.relatedId
  switch (row.type) {
    case 'ENROLLMENT_SUCCESS':
    case 'ENROLLMENT_WAITLIST':
    case 'ENROLLMENT':
    case 'ENROLLMENT_DROPPED':
    case 'GRADE_ISSUED':
    case 'GRADE':
    case 'DISCUSSION_REPLY':
    case 'DISCUSSION':
    case 'DISCUSSION_POST_APPROVED':
    case 'COURSE_PUBLISHED':
      return resolveCoursePath(id)
    case 'COURSE_APPROVED':
    case 'COURSE_REJECTED':
    case 'COURSE_UNPUBLISHED':
    case 'COURSE_REVIEW_REMINDER':
      return resolveCoursePath(id)
    case 'EXERCISE_GRADED':
      return userStore.role === 'STUDENT' ? resolveCoursePath(id) : '/exercises'
    case 'VIDEO_TRANSCODED':
      return id ? `/courses/${id}/videos` : '/videos'
    case 'MS_INVITE_LEAD':
    case 'MS_INVITE_TEAM':
    case 'MS_INVITE_ACCEPTED':
    case 'MS_INVITE_EXPIRED':
      return '/teacher/micro-specialties/invites'
    case 'MS_INVITE_CROSS_DEPT':
      return ['ACADEMIC', 'ADMIN'].includes(userStore.role) ? '/academic/micro-specialties/cross-dept' : '/teacher/micro-specialties/invites'
    case 'MS_PROPOSAL_APPROVED':
    case 'MS_PROPOSAL_REJECTED':
      return '/teacher/micro-specialties/my-proposals'
    case 'MS_SUBMITTED':
      return ['ACADEMIC', 'ADMIN'].includes(userStore.role) ? '/academic/micro-specialties/review' : '/teacher/micro-specialties/my-proposals'
    case 'MS_APPROVED':
    case 'MS_REJECTED':
    case 'MS_FEATURED_APPROVED':
    case 'MS_FEATURED_REJECTED':
    case 'MS_OPENED':
    case 'MS_TEAM_REMOVED':
    case 'MS_TEAM_LEFT':
    case 'MS_CANCELLED':
    case 'MS_LEAD_TRANSFERRED':
    case 'MS_ARCHIVED':
      if (userStore.role === 'STUDENT') return '/student/my-micro-specialties'
      if (['ACADEMIC', 'ADMIN'].includes(userStore.role)) return '/academic/micro-specialties/review'
      return id ? `/teacher/micro-specialties/${id}/manage` : '/teacher/micro-specialties'
    case 'MS_CERTIFICATE_ISSUED':
    case 'MS_ENROLLMENT_APPROVED':
    case 'MS_ENROLLMENT_REJECTED':
    case 'MS_ENROLLMENT_AUTO_ENROLL':
    case 'MS_ENROLLMENT_PENDING':
    case 'MS_ENROLLMENT_DROPPED':
    case 'MS_ENROLLMENT_REAPPLIED':
    case 'MS_ENROLLMENT_FAILED':
    case 'MS_COMPLETED':
      return userStore.role === 'STUDENT' ? '/student/my-micro-specialties' : null
    default:
      return null
  }
}

async function handleRowClick(row) {
  // 自动标记已读
  if (!row.isRead) {
    try {
      await notificationStore.markRead(row.id)
      row.isRead = true
    } catch {
      console.warn('[Notification] 点击标记已读失败', row.id)
    }
  }
  const target = resolveNotificationRoute(row)
  if (target) {
    router.push(target)
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
  /* A11Y(2026-08-05): primary(#5b60ea) 在 light-9(#eef2ff) 底上仅 4.36:1；
     改用 primary-dark(#4f46e5) → 5.62:1 达标 */
  color: var(--role-primary-dark);
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

/* P1-UX: 移动端标记已读按钮最小触控 44×44px */
.mark-read-btn {
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.mark-read-btn:focus-visible {
  outline: 2px solid var(--role-primary);
  outline-offset: 2px;
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
