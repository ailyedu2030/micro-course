<template>
  <div class="teacher-offline-page">
    <div class="page-breadcrumb">
      <el-breadcrumb separator="→">
        <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">{{ $t('route.Home') }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="courseTitle">{{ courseTitle }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('teacherOffline.offlineMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ chapterTitle || $t('common.loading') }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="page-header">
      <h1>{{ chapterTitle || $t('teacherOffline.offlineMgmt') }}</h1>
      <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>{{ $t('teacherOffline.addSession') }}
      </el-button>
    </div>

    <div v-loading="loading">
      <el-empty v-if="!loading && sessions.length === 0" :description="$t('teacherOfflineSessions.emptySessions')" :image-size="120" />

      <el-card v-for="session in sessions" :key="session.id" class="session-card" shadow="never" @click="handleSessionClick(session)" style="cursor:pointer">
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
              <span>{{ session.location || $t('teacherOffline.pending') }}</span>
            </div>
            <div class="meta-row meta-notes" v-if="session.teacherNotes">
              <el-icon><ChatLineSquare /></el-icon>
              <span>{{ session.teacherNotes }}</span>
            </div>
          </div>
          <div class="session-attendance-summary">
            <el-tooltip :content="$t('teacherOffline.present')" placement="top">
              <span class="att-count att-present">{{ attendanceSummary(session).present }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip :content="$t('teacherOffline.late')" placement="top">
              <span class="att-count att-late">{{ attendanceSummary(session).late }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip :content="$t('teacherOffline.absent')" placement="top">
              <span class="att-count att-absent">{{ attendanceSummary(session).absent }}</span>
            </el-tooltip>
            <span class="att-sep">/</span>
            <el-tooltip :content="$t('teacherOffline.excused')" placement="top">
              <span class="att-count att-excused">{{ attendanceSummary(session).excused }}</span>
            </el-tooltip>
          </div>
          <div class="session-actions">
            <el-button size="small" @click.stop="openAttendanceDialog(session)">{{ $t('teacherOffline.attendanceMgmt') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" @click.stop="openEditDialog(session)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" size="small" type="danger" plain @click.stop="handleDelete(session)">{{ $t('app.delete') }}</el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog v-model="formDialogVisible" :title="isEditing ? $t('teacherOffline.editSession') : $t('teacherOffline.createSession')" width="520px" @close="handleDialogClose">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('teacherOffline.belongCourse')" v-if="courseTitle">
          <el-tag type="primary" effect="plain">{{ courseTitle }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('teacherOfflineSessions.belongChapter')" v-if="chapterTitle">
          <el-tag type="success" effect="plain">{{ chapterTitle }}</el-tag>
        </el-form-item>
        <el-form-item :label="$t('course.date')" prop="sessionDate">
          <el-date-picker v-model="form.sessionDate" type="date" :placeholder="$t('course.selectDate')" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('course.startTime')" prop="startTime">
              <el-time-picker v-model="form.startTime" :placeholder="$t('course.startTime')" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.endTime')" prop="endTime">
              <el-time-picker v-model="form.endTime" :placeholder="$t('course.endTime')" value-format="HH:mm:ss" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('course.location')" prop="location">
          <el-input v-model="form.location" :placeholder="$t('course.locationPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('course.sortOrder')" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item :label="$t('teacherOfflineSessions.teacherNotes')" prop="teacherNotes">
          <el-input v-model="form.teacherNotes" type="textarea" :rows="3" :placeholder="$t('teacherOfflineSessions.teacherNotesPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="formSubmitting" :disabled="formSubmitting" @click="handleFormSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 签到管理弹窗 -->
    <el-dialog v-model="attendanceDialogVisible" :title="$t('teacherOffline.attendanceMgmt')" width="700px">
      <template #default>
        <div class="attendance-summary-bar" v-if="selectedSession">
          <span>{{ formatDate(selectedSession.sessionDate) }} {{ formatTimeRange(selectedSession.startTime, selectedSession.endTime) }}</span>
          <span class="att-location">{{ selectedSession.location }}</span>
        </div>
        <el-table v-loading="attendanceLoading" :data="attendanceRecords" stripe border>
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="studentName" :label="$t('teacherOfflineSessions.name')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="studentNumber" :label="$t('teacherOffline.studentNumber')" width="130" show-overflow-tooltip />
          <el-table-column prop="checkinTime" :label="$t('teacherOffline.checkinTime')" width="160">
            <template #default="{ row }">
              {{ row.checkinTime ? formatDateTime(row.checkinTime) : '-' }}
            </template>
          </el-table-column>
          <el-table-column :label="$t('app.status')" width="130">
            <template #default="{ row }">
              <el-select v-model="row.status" size="small" @change="(val) => handleStatusChange(row, val)">
                <el-option :label="$t('teacherOffline.present')" value="PRESENT" />
                <el-option :label="$t('teacherOffline.late')" value="LATE" />
                <el-option :label="$t('teacherOffline.absent')" value="ABSENT" />
                <el-option :label="$t('teacherOffline.excused')" value="EXCUSED" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!attendanceLoading && attendanceRecords.length === 0" :description="$t('teacherOfflineSessions.noAttendance')" :image-size="80" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/store/user'
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
import { getCourseById } from '@/api/course'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
// P1-C 修复 (2026-08-04): userRole 未定义 → 新增/编辑/删除线下场次按钮全部隐藏
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const chapterId = computed(() => route.params.chapterId)
const courseId = computed(() => route.params.courseId || null)

const loading = ref(true)
const chapterTitle = ref('')
const courseTitle = ref('')
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
  sessionDate: [{ required: true, message: t('course.pleaseSelectDate'), trigger: 'change' }],
  startTime: [{ required: true, message: t('course.pleaseSelectStartTime'), trigger: 'change' }],
  endTime: [{ required: true, message: t('course.pleaseSelectEndTime'), trigger: 'change' }],
  location: [{ required: true, message: t('course.pleaseInputLocation'), trigger: 'blur' }, { max: 200, message: t('teacherOfflineSessions.locationMaxLen'), trigger: 'blur' }]
}

const attendanceDialogVisible = ref(false)
const attendanceLoading = ref(false)
const attendanceRecords = ref([])
const selectedSession = ref(null)

function formatMonth(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return t('teacherOfflineSessions.monthFormat', { month: d.getMonth() + 1 })
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
    chapterTitle.value = t('course.typeOfflineCourse')
  }
}

async function fetchCourse() {
  if (!courseId.value) return
  try {
    const { data } = await getCourseById(courseId.value)
    courseTitle.value = data?.title || ''
  } catch { /* 课程标题仅供面包屑展示 */ }
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
    ElMessage.warning(t('teacherOfflineSessions.sessionsLoadFailed'))
  }
}

async function fetchAttendanceSummary(sessionId) {
  try {
    const { data } = await getAttendance(sessionId, { page: 0, size: 100 })
    const records = data?.items || data || []
    attendanceMap.value[sessionId] = Array.isArray(records) ? records : []
  } catch {
    attendanceMap.value[sessionId] = []
  }
}

function handleDialogClose() {
  formRef.value?.resetFields()
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
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (formSubmitting.value) return
  if (!formRef.value) return
  formSubmitting.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { formSubmitting.value = false; return }
  } catch { formSubmitting.value = false; return }
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
      ElMessage.success(t('course.updateSuccess'))
    } else {
      await createOfflineSession(chapterId.value, payload)
      ElMessage.success(t('course.createSuccess'))
    }
    formDialogVisible.value = false
    await fetchSessions()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('teacherOfflineSessions.opFailedRetry'))
  } finally {
    formSubmitting.value = false
  }
}

async function handleDelete(session) {
  try {
    await ElMessageBox.confirm(t('teacherOfflineSessions.confirmDeleteSession', { date: formatDate(session.sessionDate) }), t('teacherOfflineSessions.deleteConfirmTitle'), { type: 'warning' })
    await deleteOfflineSession(session.id)
    ElMessage.success(t('course.deleteSuccess'))
    await fetchSessions()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || t('course.deleteFailed'))
    }
  }
}

async function openAttendanceDialog(session) {
  selectedSession.value = session
  attendanceDialogVisible.value = true
  attendanceLoading.value = true
  try {
    const { data } = await getAttendance(session.id, { page: 0, size: 100 })
    const records = data?.items || data || []
    attendanceRecords.value = Array.isArray(records) ? records : []
  } catch {
    attendanceRecords.value = []
    ElMessage.error(t('teacherOffline.fetchAttendanceFailed'))
  } finally {
    attendanceLoading.value = false
  }
}

async function handleStatusChange(row, newStatus) {
  try {
    await updateAttendance(selectedSession.value.id, row.id, { status: newStatus })
    ElMessage.success(t('teacherOfflineSessions.statusUpdated'))
    await fetchAttendanceSummary(selectedSession.value.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('teacherOffline.updateFailed'))
  }
}

function handleSessionClick(session) {
  router.push(`/teacher/courses/${courseId.value}/chapters/${session.id}/manage-offline`)
}

onMounted(async () => {
  await Promise.all([fetchChapter(), fetchCourse(), fetchSessions()])
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
