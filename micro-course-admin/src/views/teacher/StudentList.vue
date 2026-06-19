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
        <el-form-item label="选择课程">
          <el-select
            v-model="searchForm.courseId"
            placeholder="请选择课程"
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
        <el-form-item label="班级">
          <el-input
            v-model="searchForm.className"
            placeholder="输入班级名称"
            clearable
            class="filter-input"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="专业">
          <el-input
            v-model="searchForm.majorName"
            placeholder="输入专业名称"
            clearable
            class="filter-input"
            @clear="handleSearch"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="全部状态"
            clearable
            class="status-select"
            @change="handleSearch"
          >
            <el-option label="待审核" value="PENDING" />
            <el-option label="已报名" value="ENROLLED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch" aria-label="重置"><el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset" aria-label="导出"><el-icon><RefreshRight /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card shadow-hover" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">学员列表</span>
          <div class="card-actions">
            <el-button
              v-if="tableData.length > 0"
              type="primary"
              @click="handleExport"
            >
              <el-icon><Download /></el-icon>导出 Excel
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
        title="数据加载失败"
        sub-title="请稍后重试"
        class="error-result"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData">重试</el-button>
        </template>
      </el-result>

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        description="暂无学员数据"
        :image-size="120"
      />

      <!-- 数据表格 -->
      <el-table
        v-else
        v-loading="loading" :aria-busy="loading"
        :data="tableData"
        stripe
        border
        class="data-table"
        ref="tableRef"
        @row-click="handleRowClick"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="username" label="学号" width="140" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" width="120" show-overflow-tooltip />
        <el-table-column prop="className" label="班级" min-width="140" show-overflow-tooltip />
        <el-table-column prop="majorName" label="专业" min-width="140" show-overflow-tooltip />
        <el-table-column prop="progress" label="学习进度" width="180" align="center">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.progress || 0"
                :stroke-width="8"
                :color="getProgressColor(row.progress)"
              />
              <span class="progress-text">{{ row.progress || 0 }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" label="选课时间" width="170">
          <template #default="{ row }">
            <span class="text-secondary">{{ formatDate(row.enrolledAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastWatchAt" label="最近活跃" width="170">
          <template #default="{ row }">
            <span :class="isRecent(row.lastWatchAt) ? 'text-primary-color' : 'text-secondary'">
              {{ formatDate(row.lastWatchAt) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="handleViewDetail(row)" aria-label="查看详情"><el-icon><View /></el-icon>详情
            </el-button>
            <el-button type="primary" link @click.stop="handleSendMessage(row)" aria-label="发送消息"><el-icon><Message /></el-icon>发消息
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
          @current-change="handlePageChange" aria-label="分页导航" />
      </div>
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="detailVisible"
      title="学员详情"
      width="600px"
      destroy-on-close
     :close-on-press-escape="true">
      <el-descriptions :column="2" border v-if="currentStudent">
        <el-descriptions-item label="学号">{{ currentStudent.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ currentStudent.realName }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ currentStudent.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="专业">{{ currentStudent.majorName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentStudent.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机">{{ currentStudent.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="选课时间" :span="2">{{ formatDate(currentStudent.enrolledAt) }}</el-descriptions-item>
        <el-descriptions-item label="学习进度" :span="2">
          <el-progress :percentage="currentStudent.progress || 0" :stroke-width="10" />
        </el-descriptions-item>
        <el-descriptions-item label="最近活跃" :span="2">{{ formatDate(currentStudent.lastWatchAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 发消息弹窗 -->
    <el-dialog
      v-model="messageVisible"
      title="发送消息"
      width="500px"
      destroy-on-close
     :close-on-press-escape="true">
      <el-form :model="messageForm" label-width="80px">
        <el-form-item label="收件人">
          <el-input :model-value="currentStudent?.realName || ''" disabled />
        </el-form-item>
        <el-form-item label="消息内容" required>
          <el-input
            v-model="messageForm.content"
            type="textarea"
            :rows="4"
            placeholder="请输入消息内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="messageVisible = false">取消</el-button>
        <el-button type="primary" :loading="sendingMessage" @click="confirmSendMessage">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 教师端 - 学员列表
 * Vue 3.4 Composition API + script setup
 */
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as XLSX from 'xlsx'
import {
  Search, RefreshRight, Download, View, Message
} from '@element-plus/icons-vue'
import { getCourses } from '@/api/course'
import { getCourseEnrollments, getEnrollments, getStudentDetail } from '@/api/enrollment'
import { sendNotification } from '@/api/notification'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

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
  page.value = 1
  if (searchForm.courseId) {
    fetchData()
  } else {
    tableData.value = []
    totalElements.value = 0
  }
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
    const teacherId = userStore.userInfo?.id
    const { data } = await getCourses({ size: 9999, teacherId })
    courseOptions.value = data.items || []
    if (route.query.courseId) {
      searchForm.courseId = Number(route.query.courseId)
    }
  } catch (err) {
    console.error('[StudentList] fetchCourses failed:', err)
    ElMessage.error('获取课程列表失败')
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
    console.error('[StudentList] fetchData failed:', err)
    error.value = true
    ElMessage.error('获取学员列表失败')
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

// a11y:el-table 行键盘支持(A11Y-018)
const tableRef = ref(null)
let _keydownBound = false
function bindTableKeyboard() {
  if (_keydownBound) return
  const tbody = tableRef.value?.$el?.querySelector('tbody')
  if (!tbody) return
  tbody.addEventListener('keydown', (e) => {
    const tr = e.target.closest('tr')
    if (!tr) return
    if (e.key !== 'Enter' && e.key !== ' ') return
    const idx = Array.from(tbody.querySelectorAll('tr')).indexOf(tr)
    const row = tableData.value?.[idx]
    if (row) {
      e.preventDefault()
      handleRowClick(row)
    }
  })
  tbody.querySelectorAll('tr').forEach((tr, idx) => {
    tr.setAttribute('tabindex', '0')
    tr.setAttribute('role', 'button')
    tr.setAttribute('aria-label', `选择学员 ${tableData.value?.[idx]?.realName || tableData.value?.[idx]?.username || ''}`)
  })
  _keydownBound = true
}
onMounted(() => nextTick(bindTableKeyboard))

// 查看详情（P0-2: 调用后端 getStudentDetail 获取完整信息）
async function handleViewDetail(row) {
  detailVisible.value = true
  try {
    const { data } = await getStudentDetail(row.userId)
    currentStudent.value = { ...row, ...data }
  } catch (err) {
    console.error('[StudentList] getStudentDetail failed:', err)
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
    ElMessage.warning('请输入消息内容')
    return
  }
  sendingMessage.value = true
  try {
    await sendNotification({
      userId: currentStudent.value.userId,
      type: 'SYSTEM',
      title: '教师通知',
      content: messageForm.content
    })
    ElMessage.success('消息已发送')
    messageVisible.value = false
  } catch (err) {
    console.error('[StudentList] sendNotification failed:', err)
    ElMessage.error('发送失败，请稍后重试')
  } finally {
    sendingMessage.value = false
  }
}

// 导出 Excel
function handleExport() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无数据可导出')
    return
  }
  const exportData = tableData.value.map((item, index) => ({
    序号: index + 1,
    学号: item.username || '',
    姓名: item.realName || '',
    班级: item.className || '',
    专业: item.majorName || '',
    进度: `${item.progress || 0}%`,
    选课时间: formatDate(item.enrolledAt),
    最近活跃: formatDate(item.lastWatchAt)
  }))
  const ws = XLSX.utils.json_to_sheet(exportData)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '学员列表')
  const date = new Date().toISOString().split('T')[0]
  XLSX.writeFile(wb, `students-${date}.xlsx`)
  ElMessage.success('导出成功')
}

// 工具方法
function formatDate(isoString) {
  if (!isoString) return '-'
  const d = new Date(isoString)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function getProgressColor(progress) {
  if (progress >= 80) return '#67c23a'
  if (progress >= 50) return '#409eff'
  if (progress >= 30) return '#e6a23c'
  return '#909399'
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
  padding: 24px;
  background: #F5F6FA;
  min-height: calc(100vh - 120px);
}

/* 搜索区 */
.search-card {
  margin-bottom: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
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
  margin-bottom: 24px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  transition: box-shadow 200ms ease, transform 200ms ease;
}

.table-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  transform: translateY(-1px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.card-actions :deep(.el-button) {
  border-radius: 8px;
  transition: transform 200ms ease, box-shadow 200ms ease;
}

.card-actions :deep(.el-button--primary) {
  transition: transform 200ms ease, box-shadow 200ms ease;
}

.card-actions :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(79, 70, 229, 0.3);
}

/* 表格 */
.data-table {
  width: 100%;
  cursor: pointer;
}

.data-table :deep(.el-table__header-wrapper th) {
  background: #F8FAFC;
  color: #1E293B;
  font-weight: 600;
  font-size: 14px;
}

.data-table :deep(.el-table__row) {
  transition: background-color 200ms ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background: #F1F5F9;
}

/* 进度条单元格 */
.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-cell .el-progress {
  flex: 1;
}

.progress-text {
  font-size: 12px;
  color: #475569;
  min-width: 36px;
  text-align: right;
}

/* 错误态 */
.error-result {
  padding: 48px 0;
}

/* 分页 */
.pagination-wrap {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

/* 文字辅助类 */
.text-secondary {
  color: #475569;
  font-size: 13px;
}

.text-primary-color {
  color: #4F46E5;
  font-size: 13px;
}

/* 弹窗样式 */
.student-list-container :deep(.el-dialog) {
  border-radius: 12px;
}

.student-list-container :deep(.el-button) {
  border-radius: 8px;
}

.student-list-container :deep(.el-button--primary) {
  border-radius: 8px;
  transition: transform 200ms ease, box-shadow 200ms ease;
}

.student-list-container :deep(.el-button--primary:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(79, 70, 229, 0.3);
}
</style>