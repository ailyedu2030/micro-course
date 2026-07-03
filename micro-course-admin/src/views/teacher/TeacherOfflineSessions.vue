<template>
  <div class="teacher-offline-page">
    <div class="page-breadcrumb">
      <el-breadcrumb>
        <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>线下课管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ chapterTitle || '加载中...' }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="page-header">
      <h1>{{ chapterTitle || '线下课管理' }}</h1>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>新增场次
      </el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!loading && sessions.length === 0" description="暂未安排线下课程" :image-size="120" />

      <el-card v-for="session in sessions" :key="session.id" class="session-card" shadow="never">
        <div class="session-header">
          <div class="session-date-badge">
            <span class="badge-month">{{ formatMonth(session.sessionDate) }}</span>
            <span class="badge-day">{{ formatDayNum(session.sessionDate) }}</span>
          </div>
          <div class="session-meta">
            <div class="meta-row">
              <el-icon><Clock /></el-icon>
              <span>{{ formatTimeRange(session.startTime, session.endTime) }}</span>
            </div>
            <div class="meta-row">
              <el-icon><Location /></el-icon>
              <span>{{ session.location || '待定' }}</span>
            </div>
            <div class="meta-row meta-notes" v-if="session.teacherNotes">
              <el-icon><ChatLineSquare /></el-icon>
              <span>{{ session.teacherNotes }}</span>
            </div>
          </div>
          <div class="session-attendance-summary">
            <el-tooltip content="已签到" placement="top">
              <span class="att-count att-present">{{ attendanceSummary(session).present }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip content="迟到" placement="top">
              <span class="att-count att-late">{{ attendanceSummary(session).late }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip content="缺勤" placement="top">
              <span class="att-count att-absent">{{ attendanceSummary(session).absent }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip content="请假" placement="top">
              <span class="att-count att-excused">{{ attendanceSummary(session).excused }}</span>
            </el-tooltip>
          </div>
          <div class="session-actions">
            <el-button size="small" @click="openAttendanceDialog(session)">签到管理</el-button>
            <el-button size="small" @click="openEditDialog(session)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="handleDelete(session)">删除</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="isEditing ? '编辑场次' : '新增场次'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="日期" prop="sessionDate">
          <el-date-picker v-model="form.sessionDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="form.startTime" placeholder="开始时间" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="form.endTime" placeholder="结束时间" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地点" prop="location">
          <el-input v-model="form.location" placeholder="如：教学楼 A-101" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="教师备注" prop="teacherNotes">
          <el-input v-model="form.teacherNotes" type="textarea" :rows="3" placeholder="选填，如注意事项、准备材料等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="formSubmitting" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 签到管理弹窗 -->
    <el-dialog v-model="attendanceDialogVisible" title="签到管理" width="700px">
      <template #default>
        <div class="attendance-summary-bar" v-if="selectedSession">
          <span>{{ formatDate(selectedSession.sessionDate) }} {{ formatTimeRange(selectedSession.startTime, selectedSession.endTime) }}</span>
          <span class="att-location">{{ selectedSession.location }}</span>
        </div>
        <el-table v-loading="attendanceLoading" :data="attendanceRecords" stripe border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="studentName" label="姓名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="studentNumber" label="学号" width="130" show-overflow-tooltip />
          <el-table-column prop="checkinTime" label="签到时间" width="160">
            <template #default="{ row }">
              {{ row.checkinTime ? formatDateTime(row.checkinTime) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-select v-model="row.status" size="small" @change="(val) => handleStatusChange(row, val)">
                <el-option label="已签到" value="PRESENT" />
                <el-option label="迟到" value="LATE" />
                <el-option label="缺勤" value="ABSENT" />
                <el-option label="请假" value="EXCUSED" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!attendanceLoading && attendanceRecords.length === 0" description="暂无签到记录" :image-size="80" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Clock, Location, ChatLineSquare } from '@element-plus/icons-vue'
import {
  getOfflineSessions,
  createOfflineSession,
  updateOfflineSession,
  deleteOfflineSession,
  getAttendance,
  updateAttendance
} from '@/api/offline-session'
import { getChapterById } from '@/api/chapter'

const router = useRouter()
const route = useRoute()
const chapterId = computed(() => route.params.chapterId)

const loading = ref(true)
const chapterTitle = ref('')
const sessions = ref([])
const attendanceMap = ref({})

const formDialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref(null)
const formSubmitting = ref(false)
const formRef = ref(null)

const form = reactive({
  sessionDate: '',
  startTime: '',
  endTime: '',
  location: '',
  teacherNotes: '',
  sortOrder: 0
})

const formRules = {
  sessionDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }]
}

const attendanceDialogVisible = ref(false)
const attendanceLoading = ref(false)
const attendanceRecords = ref([])
const selectedSession = ref(null)

function formatMonth(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}月`
}
function formatDayNum(dateStr) {
  if (!dateStr) return '--'
  return new Date(dateStr).getDate()
}
function formatDate(dateStr) {
  if (!dateStr) return '--'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function formatDateTime(t) {
  if (!t) return '-'
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function formatTimeRange(start, end) {
  if (!start) return '--'
  const fmt = (t) => (t && t.length >= 5 ? t.slice(0, 5) : t || '')
  const s = fmt(start)
  const e = fmt(end)
  return e ? `${s}-${e}` : s
}

function attendanceSummary(session) {
  const records = attendanceMap.value[session.id] || []
  return {
    present: records.filter(r => r.status === 'PRESENT').length,
    late: records.filter(r => r.status === 'LATE').length,
    absent: records.filter(r => r.status === 'ABSENT').length,
    excused: records.filter(r => r.status === 'EXCUSED').length
  }
}

async function fetchChapter() {
  try {
    const { data } = await getChapterById(chapterId.value)
    chapterTitle.value = data?.title || ''
  } catch {
    chapterTitle.value = '线下课程'
  }
}

async function fetchSessions() {
  try {
    const { data } = await getOfflineSessions(chapterId.value, { page: 0, size: 100 })
    const items = (data?.items || data || [])
    items.sort((a, b) => {
      if (!a.sessionDate) return 1
      if (!b.sessionDate) return -1
      const dateCompare = new Date(a.sessionDate) - new Date(b.sessionDate)
      if (dateCompare !== 0) return dateCompare
      return (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
    })
    sessions.value = items
    await Promise.all(items.map(s => fetchAttendanceSummary(s.id)))
  } catch {
    sessions.value = []
  }
}

async function fetchAttendanceSummary(sessionId) {
  try {
    const { data } = await getAttendance(sessionId, { page: 0, size: 999 })
    const records = data?.items || data || []
    attendanceMap.value[sessionId] = Array.isArray(records) ? records : []
  } catch {
    attendanceMap.value[sessionId] = []
  }
}

function openCreateDialog() {
  isEditing.value = false
  editingId.value = null
  form.sessionDate = ''
  form.startTime = ''
  form.endTime = ''
  form.location = ''
  form.teacherNotes = ''
  form.sortOrder = 0
  formDialogVisible.value = true
}

function openEditDialog(session) {
  isEditing.value = true
  editingId.value = session.id
  form.sessionDate = session.sessionDate || ''
  form.startTime = session.startTime || ''
  form.endTime = session.endTime || ''
  form.location = session.location || ''
  form.teacherNotes = session.teacherNotes || ''
  form.sortOrder = session.sortOrder ?? 0
  formDialogVisible.value = true
}

async function handleFormSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  formSubmitting.value = true
  try {
    const payload = {
      sessionDate: form.sessionDate,
      startTime: form.startTime,
      endTime: form.endTime,
      location: form.location,
      teacherNotes: form.teacherNotes || undefined,
      sortOrder: form.sortOrder ?? 0
    }
    if (isEditing.value) {
      await updateOfflineSession(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createOfflineSession(chapterId.value, payload)
      ElMessage.success('创建成功')
    }
    formDialogVisible.value = false
    await fetchSessions()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请重试')
  } finally {
    formSubmitting.value = false
  }
}

async function handleDelete(session) {
  try {
    await ElMessageBox.confirm(`确定删除 ${formatDate(session.sessionDate)} 的场次？`, '删除确认', { type: 'warning' })
    await deleteOfflineSession(session.id)
    ElMessage.success('删除成功')
    await fetchSessions()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || '删除失败')
    }
  }
}

async function openAttendanceDialog(session) {
  selectedSession.value = session
  attendanceDialogVisible.value = true
  attendanceLoading.value = true
  try {
    const { data } = await getAttendance(session.id, { page: 0, size: 999 })
    const records = data?.items || data || []
    attendanceRecords.value = Array.isArray(records) ? records : []
  } catch {
    attendanceRecords.value = []
    ElMessage.error('获取签到记录失败')
  } finally {
    attendanceLoading.value = false
  }
}

async function handleStatusChange(row, newStatus) {
  try {
    await updateAttendance(selectedSession.value.id, row.id, { status: newStatus })
    ElMessage.success('状态已更新')
    await fetchAttendanceSummary(selectedSession.value.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '更新失败')
  }
}

onMounted(async () => {
  await Promise.all([fetchChapter(), fetchSessions()])
  loading.value = false
})
</script>

<style scoped>
.teacher-offline-page {
  padding: var(--space-6);
  max-width: 1100px;
  margin: 0 auto;
  min-height: 100dvh;
  background: var(--el-bg-color-page);
}
.page-breadcrumb {
  margin-bottom: var(--space-5);
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5);
}
.page-header h1 {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0;
}

.session-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) var(--ease-out);
}
.session-card:hover {
  box-shadow: var(--shadow-sm);
}
.session-header {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  flex-wrap: wrap;
}
.session-date-badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
  padding: var(--space-2);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  flex-shrink: 0;
}
.badge-month {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.badge-day {
  font-size: 24px;
  font-weight: var(--weight-bold);
  color: var(--el-color-primary);
  line-height: 1.2;
}
.session-meta {
  flex: 1;
  min-width: 160px;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  margin-bottom: var(--space-1);
}
.meta-row:last-child {
  margin-bottom: 0;
}
.meta-row .el-icon {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
.meta-notes {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  font-style: italic;
}
.session-attendance-summary {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  flex-shrink: 0;
}
.att-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  cursor: default;
}
.att-present {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.att-late {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}
.att-absent {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.att-excused {
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
}
.att-sep {
  color: var(--el-border-color);
  font-size: var(--text-xs);
}
.session-actions {
  display: flex;
  gap: var(--space-2);
  flex-shrink: 0;
}

.attendance-summary-bar {
  display: flex;
  gap: var(--space-3);
  align-items: center;
  margin-bottom: var(--space-4);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}
.att-location {
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
}

@media (max-width: 768px) {
  .teacher-offline-page {
    padding: var(--space-4);
  }
  .session-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-3);
  }
  .session-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
