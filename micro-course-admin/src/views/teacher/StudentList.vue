<!--
  教师端 - 学生列表
  /teacher/students
  Author: jackie
-->
<template>
  <div class="student-list-container">
    <!-- 搜索筛选区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.selectCourse')">
          <el-select
            v-model="searchForm.courseId"
            :placeholder="$t('exercise.selectCourse')"
            :aria-label="$t('course.selectCourse')"
            clearable
            class="course-select"
            @change="handleCourseChange"
          >
            <el-option
              v-for="item in courseOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('userSearch.classLabel')">
          <el-input
            v-model="searchForm.className"
            :placeholder="$t('studentList.classNamePlaceholder')"
            clearable
            class="filter-input"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('userSearch.major')">
          <el-input
            v-model="searchForm.majorName"
            :placeholder="$t('studentList.majorNamePlaceholder')"
            clearable
            class="filter-input"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item :label="$t('course.status')">
          <el-select
            v-model="searchForm.status"
            :placeholder="$t('studentList.allStatuses')"
            clearable
            class="status-select"
            @change="handleSearch"
          >
            <el-option :label="$t('course.pendingReview')" value="PENDING" />
            <el-option :label="$t('studentList.statusEnrolled')" value="ENROLLED" />
            <el-option :label="$t('studentList.statusCancelled')" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" :aria-label="$t('app.search')">
<el-icon><Search /></el-icon>{{ $t('app.search') }}
          </el-button>
          <el-button @click="handleReset" :aria-label="$t('app.reset')">
<el-icon><RefreshRight /></el-icon>{{ $t('app.reset') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card shadow-hover" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('studentList.title') }}</span>
          <div class="card-actions">
            <el-button
              v-if="tableData.length > 0"
              type="primary"
              @click="handleExport"
            >
              <el-icon><Download /></el-icon>{{ $t('operationLogs.exportExcel') }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 加载中 -->
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 错误态 -->
      <el-result
        v-else-if="error"
        icon="error"
        :title="$t('studentList.loadFailed')"
        :sub-title="$t('operationLogs.retryLater')"
        class="error-result"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        :description="$t('studentList.noMatch')"
        :image-size="120"
      >
        <template #default>
          <el-button type="primary" @click="handleReset">{{ $t('course.clearFilter') }}</el-button>
        </template>
      </el-empty>

      <!-- 数据表格 -->
      <el-table
        v-else
        v-loading="loading" :aria-busy="loading"
        :data="tableData"
        stripe
        border
        highlight-current-row
        tabindex="0"
        class="data-table"
        ref="tableRef"
        @row-click="handleRowClick"
        @row-keydown.enter="handleRowClick"
      >
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="username" :label="$t('userList.studentNo')" width="140" show-overflow-tooltip />
        <el-table-column prop="realName" :label="$t('user.realName')" width="120" show-overflow-tooltip />
        <el-table-column prop="className" :label="$t('userSearch.classLabel')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="majorName" :label="$t('userSearch.major')" min-width="140" show-overflow-tooltip />
        <el-table-column prop="progress" :label="$t('learning.progressLabel')" width="180" align="center">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.progress || 0"
                :stroke-width="8"
                :color="getProgressColor(row.progress)"
                :aria-label="$t('studentList.progressAria', { progress: row.progress || 0 })"
              />
              <span class="progress-text">{{ row.progress || 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" :label="$t('studentList.enrolledAt')" width="170" :formatter="$formatDateTime" />
        <el-table-column prop="lastWatchAt" :label="$t('studentList.lastActive')" width="170">
          <template #default="{ row }">
            <span :class="isRecent(row.lastWatchAt) ? 'text-primary-color' : 'text-secondary'">
              {{ formatDate(row.lastWatchAt) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleViewDetail(row)" :aria-label="$t('course.viewDetail')">
<el-icon><View /></el-icon>{{ $t('app.detail') }}
            </el-button>
            <el-button type="primary" link @click.stop="handleSendMessage(row)" :aria-label="$t('studentList.sendMessageAria')">
<el-icon><Message /></el-icon>{{ $t('studentList.sendMessage') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div v-if="tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      :title="$t('studentList.detailTitle')"
      width="600px"
      destroy-on-close
     :close-on-press-escape="true"
>
      <el-descriptions :column="2" border v-if="currentStudent">
        <el-descriptions-item :label="$t('userList.studentNo')">{{ currentStudent.username }}</el-descriptions-item>
        <el-descriptions-item :label="$t('user.realName')">{{ currentStudent.realName }}</el-descriptions-item>
        <el-descriptions-item :label="$t('userSearch.classLabel')">{{ currentStudent.className || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('userSearch.major')">{{ currentStudent.majorName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('user.email')">{{ currentStudent.email || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('userList.phoneLabel')">{{ currentStudent.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="$t('studentList.enrolledAt')" :span="2">{{ formatDate(currentStudent.enrolledAt) }}</el-descriptions-item>
        <el-descriptions-item :label="$t('learning.progressLabel')" :span="2">
          <el-progress :percentage="currentStudent.progress || 0" :stroke-width="10" :aria-label="$t('studentList.progressAria', { progress: currentStudent.progress || 0 })" />
        </el-descriptions-item>
        <el-descriptions-item :label="$t('studentList.lastActive')" :span="2">{{ formatDate(currentStudent.lastWatchAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 发消息弹窗 -->
    <el-dialog
      v-model="messageVisible"
      :title="$t('studentList.sendMessageAria')"
      width="500px"
      destroy-on-close
     :close-on-press-escape="true"
>
      <el-form :model="messageForm" label-width="80px">
        <el-form-item :label="$t('studentList.recipient')">
          <el-input :model-value="currentStudent?.realName || ''" disabled />
        </el-form-item>
        <el-form-item :label="$t('studentList.messageContent')" required>
          <el-input
            v-model="messageForm.content"
            type="textarea"
            :rows="4"
            :placeholder="$t('studentList.messageContentPlaceholder')"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="messageVisible = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="sendingMessage" :disabled="sendingMessage" @click="confirmSendMessage">{{ $t('studentList.send') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 教师端 - 学员列表
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Workbook } from 'exceljs'
import {
  Search, RefreshRight, Download, View, Message
} from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { fetchAllPages } from '@/utils/fetchAllPages'
import { getCourseEnrollments, getEnrollments, getStudentDetail, exportEnrollments } from '@/api/enrollment'
import { sendNotification } from '@/api/notification'
import { useUserStore } from '@/store/user'
import { useTableKeyboardNavigation } from '@/composables/useTableKeyboardNavigation'

const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

// 加载状态
const loading = ref(false)
const error = ref(false)

// 表格数据
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

// 搜索表单
const searchForm = reactive({
  courseId: '',
  className: '',
  majorName: '',
  status: ''
})

// 详情弹窗
const detailVisible = ref(false)
const currentStudent = ref(null)

// 发消息弹窗
const messageVisible = ref(false)
const sendingMessage = ref(false)
const messageForm = reactive({ content: '' })

// 课程变化
function handleCourseChange() {
  // 审计 2026-08-14 修复: 清空课程后必须继续应用班级/专业/状态筛选,
  // 不能直接清空表格 —— fetchData 在无 courseId 时走 getEnrollments
  // (teacherId + className/majorName/status), 筛选与课程独立生效
  page.value = 1
  fetchData()
}

// 搜索
function handleSearch() {
  page.value = 1
  fetchData()
}

// 重置
function handleReset() {
  searchForm.courseId = ''
  searchForm.className = ''
  searchForm.majorName = ''
  searchForm.status = ''
  page.value = 1
  fetchData()
}

// 获取课程列表
async function fetchCourses() {
  try {
    const teacherId = userStore.userId
    // P1-I-2026-08-15（R3 审查）· 循环分页拉全量课程，规避后端 size 上限触发 400
    courseOptions.value = await fetchAllPages(getCourses, { teacherId }, 100)
    if (route.query.courseId) {
      searchForm.courseId = Number(route.query.courseId)
    }
  } catch (err) {
// eslint-disable-next-line no-console
    console.debug('[StudentList] fetchCourses failed:', err)
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

// 获取学员数据
async function fetchData() {
  loading.value = true
  error.value = false
  try {
    let result
    if (searchForm.courseId) {
      // 按课程查询（P1-2: 分页）
      const params = {
        page: page.value - 1,
        size: size.value,
        courseId: searchForm.courseId
      }
      const { data } = await getCourseEnrollments(params)
      result = data
    } else {
      // 查询教师所有课程的学生（P0-4/P1-4: 服务端过滤 className/majorName）
      const params = {
        page: page.value - 1,
        size: size.value,
        teacherId: userStore.userId,
        className: searchForm.className || undefined,
        majorName: searchForm.majorName || undefined,
        status: searchForm.status || undefined
      }
      const { data } = await getEnrollments(params)
      result = data
    }
    tableData.value = result.items || []
    totalElements.value = result.totalElements || tableData.value.length
  } catch (err) {
// eslint-disable-next-line no-console
    console.debug('[StudentList] fetchData failed:', err)
    error.value = true
    ElMessage.error(t('studentList.fetchFailed'))
  } finally {
    loading.value = false
  }
}

// 翻页
function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

// 行点击（AUD-教师-9:打开详情弹窗）
function handleRowClick(row) {
  handleViewDetail(row)
}

const tableRef = ref(null)
const { refreshTableKeyboard } = useTableKeyboardNavigation({
  tableRef,
  tableData,
  onActivate: handleRowClick,
  getAriaLabel: (row) => t('studentList.selectStudentAria', { name: row?.realName || row?.username || '' })
})
onMounted(() => refreshTableKeyboard())

// 查看详情（P0-2: 调用后端 getStudentDetail 获取完整信息）
async function handleViewDetail(row) {
  detailVisible.value = true
  try {
    const { data } = await getStudentDetail(row.userId)
    currentStudent.value = { ...row, ...data }
  } catch (err) {
// eslint-disable-next-line no-console
    console.debug('[StudentList] getStudentDetail failed:', err)
    // fallback: 使用表格行数据
    currentStudent.value = row
  }
}

// 发消息
function handleSendMessage(row) {
  currentStudent.value = row
  messageForm.content = ''
  messageVisible.value = true
}

// 确认发送消息（P0-1: 补充 type/title 字段）
async function confirmSendMessage() {
  if (!messageForm.content.trim()) {
    ElMessage.warning(t('studentList.messageContentPlaceholder'))
    return
  }
  sendingMessage.value = true
  try {
    await sendNotification({
      userId: currentStudent.value.userId,
      type: 'SYSTEM',
      title: t('studentList.teacherNotification'),
      content: messageForm.content
    })
    ElMessage.success(t('studentList.messageSent'))
    messageVisible.value = false
  } catch (err) {
// eslint-disable-next-line no-console
    console.debug('[StudentList] sendNotification failed:', err)
    ElMessage.error(t('studentList.sendFailed'))
  } finally {
    sendingMessage.value = false
  }
}

// 导出 Excel（P1-修复: 优先使用后端导出接口，客户端 XLSX 作为兜底）
async function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning(t('studentList.noExportData'))
    return
  }

  // 优先调用后端导出接口（支持服务端分页全量导出）
  if (searchForm.courseId) {
    try {
      const res = await exportEnrollments(searchForm.courseId)
      const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      const date = new Date().toISOString().split('T')[0]
      link.download = `students-course-${searchForm.courseId}-${date}.xlsx`
      link.click()
      URL.revokeObjectURL(url)
      ElMessage.success(t('studentList.exportSuccess'))
      return
    } catch (err) {
      console.warn('[StudentList] 后端导出失败，回退到客户端导出', err)
      // 后端导出失败时回退到客户端 XLSX 导出
    }
  }

  // 客户端 XLSX 兜底导出（当前页数据）
  const exportData = tableData.value.map((item, index) => ({
    [t('course.index')]: index + 1,
    [t('userList.studentNo')]: item.username || '',
    [t('user.realName')]: item.realName || '',
    [t('userSearch.classLabel')]: item.className || '',
    [t('userSearch.major')]: item.majorName || '',
    [t('studentList.progressShort')]: `${item.progress || 0}%`,
    [t('studentList.enrolledAt')]: formatDate(item.enrolledAt),
    [t('studentList.lastActive')]: formatDate(item.lastWatchAt)
  }))
  const wb = new Workbook()
  const ws = wb.addWorksheet(t('studentList.title'))
  ws.addRows(exportData.map(row => Object.values(row)))
  const date = new Date().toISOString().split('T')[0]
  await wb.xlsx.writeFile(`students-${date}.xlsx`)
  ElMessage.success(t('studentList.exportSuccess'))
}

// 工具方法
function formatDate(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function getProgressColor(progress) {
  if (progress >= 80) return 'var(--el-color-success)'
  if (progress >= 50) return 'var(--el-color-primary)'
  if (progress >= 30) return 'var(--el-color-warning)'
  return 'var(--el-color-info)'
}

function isRecent(isoString) {
  if (!isoString) return false
  const diff = Date.now() - new Date(isoString).getTime()
  return diff < 3 * 24 * 60 * 60 * 1000 // 3天内
}

onMounted(() => {
  fetchCourses()
  // 默认加载该教师所有课程的学生
  fetchData()
})
</script>

<style scoped>
.student-list-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: calc(100dvh - 120px);
  max-width: 1440px;
  margin: 0 auto;
}

/* 搜索区 */
.search-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.course-select {
  width: 220px;
}

.filter-input {
  width: 140px;
}

.status-select {
  width: 120px;
}

/* 表格卡片 */
.table-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
  transform: translateY(-2px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.card-actions {
  display: flex;
  gap: var(--space-2);
}

.card-actions :deep(.el-button) {
  border-radius: var(--radius-md);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.card-actions :deep(.el-button--primary) {
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.card-actions :deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-primary);
}

/* 表格 */
.data-table {
  width: 100%;
  cursor: pointer;
}

.data-table :deep(.el-table__header-wrapper th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9);
}

/* 进度条单元格 */
.progress-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.progress-cell .el-progress {
  flex: 1;
}

.progress-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  min-width: 36px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* 错误态 */
.error-result {
  padding: var(--space-9) 0;
}

/* 分页 */
.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 文字辅助类 */
.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

.text-primary-color {
  color: var(--role-primary);
  font-size: var(--text-sm);
}

/* 弹窗样式 */
.student-list-container :deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

.student-list-container :deep(.el-button) {
  border-radius: var(--radius-md);
}

.student-list-container :deep(.el-button--primary) {
  border-radius: var(--radius-md);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.student-list-container :deep(.el-button--primary:hover) {
  transform: translateY(-2px);
  box-shadow: var(--shadow-primary);
}
</style>
