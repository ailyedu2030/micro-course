<template>
  <div class="teacher-offline-list-page">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">{{ $t('layout.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('teacherOffline.offlineMgmt') }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="selectedCourseTitle">{{ selectedCourseTitle }}</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('teacherOffline.belongCourse')">
          <el-select v-model="searchForm.courseId" :placeholder="$t('teacherOffline.pleaseSelectCourse')" clearable class="filter-input-w240" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.chapter')">
          <el-select v-model="searchForm.chapterId" :placeholder="$t('course.pleaseSelectChapter')" clearable :disabled="!searchForm.courseId" class="filter-input-w240">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('userSearch.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('app.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ chapterTitle || $t('teacherOffline.offlineSessionList') }}</span>
          <div class="header-actions">
            <el-button type="primary" :disabled="!searchForm.chapterId" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon>{{ $t('teacherOffline.addSession') }}
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty :description="searchForm.chapterId ? $t('teacherOffline.noSessionsWithChapter') : $t('teacherOffline.selectCourseChapterFirst')" :image-size="120" />
        </template>
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column :label="$t('course.date')" width="130" align="center">
          <template #default="{ row }">
            {{ row.sessionDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.time')" width="160" align="center">
          <template #default="{ row }">
            {{ row.startTime ? row.startTime.substring(0, 5) : '-' }} ~ {{ row.endTime ? row.endTime.substring(0, 5) : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('course.location')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.location || $t('teacherOffline.pending') }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('teacherOffline.attendance')" width="200" align="center">
          <template #default="{ row }">
            <span class="att-badge att-present">{{ row.presentCount || 0 }} {{ $t('teacherOffline.present') }}</span>
            <span class="att-badge att-late">{{ row.lateCount || 0 }} {{ $t('teacherOffline.late') }}</span>
            <span class="att-badge att-absent">{{ row.absentCount || 0 }} {{ $t('teacherOffline.absent') }}</span>
            <span class="att-badge att-excused">{{ row.excusedCount || 0 }} {{ $t('teacherOffline.excused') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="success" link size="small" @click="handleAttendance(row)">{{ $t('teacherOffline.attendanceMgmt') }}</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
      </div>
    </el-card>

    <el-dialog v-model="showCreateDialog" :title="isEdit ? $t('teacherOffline.editSession') : $t('teacherOffline.createSession')" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('course.date')" prop="sessionDate">
          <el-date-picker v-model="formData.sessionDate" type="date" :placeholder="$t('course.selectDate')" value-format="YYYY-MM-DD" class="full-width" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('course.startTime')" prop="startTime">
              <el-time-picker v-model="formData.startTime" :placeholder="$t('teacherOffline.startPlaceholder')" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.endTime')" prop="endTime">
              <el-time-picker v-model="formData.endTime" :placeholder="$t('teacherOffline.endPlaceholder')" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('course.location')" prop="location">
          <el-input v-model="formData.location" :placeholder="$t('course.locationPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('course.remark')">
          <el-input v-model="formData.teacherNotes" type="textarea" :rows="2" :placeholder="$t('course.optional')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ isEdit ? $t('app.save') : $t('course.add') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAttendanceDialog" :title="$t('teacherOffline.attendanceMgmt')" width="600px">
      <el-table :data="attendanceData" stripe border v-loading="attendanceLoading">
        <el-table-column type="index" :label="$t('course.index')" width="60" align="center" />
        <el-table-column prop="studentName" :label="$t('teacherOffline.studentName')" min-width="120" />
        <el-table-column prop="studentNumber" :label="$t('teacherOffline.studentNumber')" width="120" />
        <el-table-column :label="$t('teacherOffline.attendanceStatus')" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PRESENT'" type="success" size="small">{{ $t('teacherOffline.present') }}</el-tag>
            <el-tag v-else-if="row.status === 'LATE'" type="warning" size="small">{{ $t('teacherOffline.late') }}</el-tag>
            <el-tag v-else-if="row.status === 'ABSENT'" type="danger" size="small">{{ $t('teacherOffline.absent') }}</el-tag>
            <el-tag v-else-if="row.status === 'EXCUSED'" type="info" size="small">{{ $t('teacherOffline.excused') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkinTime" :label="$t('teacherOffline.checkinTime')" width="160" align="center">
          <template #default="{ row }">
            {{ row.checkinTime ? new Date(row.checkinTime).toLocaleString('zh-CN') : '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="140" align="center">
          <template #default="{ row }">
            <el-select v-model="row.editStatus" size="small" :placeholder="$t('teacherOffline.changeStatus')" @change="(val) => handleUpdateAttendance(row, val)">
              <el-option :label="$t('teacherOffline.present')" value="PRESENT" />
              <el-option :label="$t('teacherOffline.late')" value="LATE" />
              <el-option :label="$t('teacherOffline.absent')" value="ABSENT" />
              <el-option :label="$t('teacherOffline.excused')" value="EXCUSED" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAttendanceDialog = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getOfflineSessions, createOfflineSession, updateOfflineSession, deleteOfflineSession, getAttendance, updateAttendance } from '@/api/offline-session'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const userStore = useUserStore()
// 审计 2026-08-14 修复: 离线录播下载必须校验权限。
// 全站无统一 hasPerm 工具, 采用角色守卫; 后端接口层亦需独立鉴权。
const userRole = computed(() => userStore.role)
const DOWNLOAD_OFFLINE_RECORDING = 'DOWNLOAD_OFFLINE_RECORDING'
function hasPerm(perm) {
  if (perm === DOWNLOAD_OFFLINE_RECORDING) return userRole.value === 'ADMIN'
  return false
}
function ensureDownloadPermission() {
  if (!hasPerm(DOWNLOAD_OFFLINE_RECORDING)) {
    ElMessage.error(t('teacherOffline.noDownloadPerm'))
    return false
  }
  return true
}

const loading = ref(false)
const submitLoading = ref(false)
const attendanceLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const courseOptions = ref([])
const chapterOptions = ref([])
const selectedCourseTitle = ref('')

const searchForm = reactive({
  courseId: null,
  chapterId: null
})

const chapterTitle = computed(() => {
  if (!searchForm.chapterId) return t('teacherOffline.offlineSessionList')
  const ch = chapterOptions.value.find(c => c.id === searchForm.chapterId)
  return ch ? t('teacherOffline.sessionTitleFormat', { title: ch.title }) : t('teacherOffline.offlineSessionList')
})

const showCreateDialog = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const formRef = ref(null)
const formData = reactive({
  sessionDate: '',
  startTime: '',
  endTime: '',
  location: '',
  teacherNotes: ''
})
const formRules = {
  sessionDate: [{ required: true, message: t('course.pleaseSelectDate'), trigger: 'change' }],
  startTime: [{ required: true, message: t('course.pleaseSelectStartTime'), trigger: 'change' }],
  endTime: [{ required: true, message: t('course.pleaseSelectEndTime'), trigger: 'change' }],
  location: [{ required: true, message: t('course.pleaseInputLocation'), trigger: 'blur' }]
}

const showAttendanceDialog = ref(false)
const attendanceData = ref([])
const currentAttendanceSessionId = ref(null)

const fetchCourses = async () => {
  try {
    const params = { size: 200 }
    if (userStore.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data?.items || []
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

const handleCourseChange = (courseId) => {
  searchForm.chapterId = null
  chapterOptions.value = []
  selectedCourseTitle.value = ''
  if (!courseId) return
  const course = courseOptions.value.find(c => c.id === courseId)
  selectedCourseTitle.value = course?.title || ''
  fetchChapters(courseId)
}

const fetchChapters = async (courseId) => {
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    chapterOptions.value = data?.items || []
  } catch {
    chapterOptions.value = []
  }
}

const fetchData = async () => {
  if (!searchForm.chapterId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = { page: page.value - 1, size: size.value }
    const { data } = await getOfflineSessions(searchForm.chapterId, params)
    tableData.value = data?.items || []
    totalElements.value = data?.totalElements || 0
  } catch {
    ElMessage.error(t('teacherOffline.fetchSessionsFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = null
  searchForm.chapterId = null
  chapterOptions.value = []
  selectedCourseTitle.value = ''
  page.value = 1
  tableData.value = []
  totalElements.value = 0
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const resetForm = () => {
  formData.sessionDate = ''
  formData.startTime = ''
  formData.endTime = ''
  formData.location = ''
  formData.teacherNotes = ''
  isEdit.value = false
  editingId.value = null
  formRef.value?.resetFields()
}

const handleEdit = (row) => {
  isEdit.value = true
  editingId.value = row.id
  formData.sessionDate = row.sessionDate
  formData.startTime = row.startTime
  formData.endTime = row.endTime
  formData.location = row.location || ''
  formData.teacherNotes = row.teacherNotes || ''
  showCreateDialog.value = true
}

const handleSubmit = async () => {
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位防连点重复提交
  if (submitLoading.value) return
  if (!formRef.value) return
  submitLoading.value = true
  try { const v = await formRef.value.validate(); if (!v) { submitLoading.value = false; return } } catch { submitLoading.value = false; return }
  try {
    if (isEdit.value) {
      await updateOfflineSession(editingId.value, {
        sessionDate: formData.sessionDate,
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location,
        teacherNotes: formData.teacherNotes || undefined
      })
      ElMessage.success(t('course.updateSuccess'))
    } else {
      await createOfflineSession(searchForm.chapterId, {
        sessionDate: formData.sessionDate,
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location,
        teacherNotes: formData.teacherNotes || undefined
      })
      ElMessage.success(t('course.createSuccess'))
    }
    showCreateDialog.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || (isEdit.value ? t('teacherOffline.updateFailed') : t('course.createFailed')))
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm(t('teacherOffline.confirmDeleteSession'), t('course.hintTitle'), { type: 'warning' }) } catch { return }
  try {
    await deleteOfflineSession(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('course.deleteFailed'))
  }
}

const handleAttendance = async (row) => {
  currentAttendanceSessionId.value = row.id
  showAttendanceDialog.value = true
  attendanceLoading.value = true
  try {
    const { data } = await getAttendance(row.id, { page: 0, size: 200 })
    attendanceData.value = (data?.items || []).map(r => ({ ...r, editStatus: r.status }))
  } catch {
    ElMessage.error(t('teacherOffline.fetchAttendanceFailed'))
    attendanceData.value = []
  } finally {
    attendanceLoading.value = false
  }
}

const handleUpdateAttendance = async (row, newStatus) => {
  try {
    await updateAttendance(currentAttendanceSessionId.value, row.id, { status: newStatus })
    row.status = newStatus
    row.editStatus = newStatus
    ElMessage.success(t('teacherOffline.updateAttendanceSuccess'))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('teacherOffline.updateFailed'))
    row.editStatus = row.status
  }
}

onMounted(() => {
  fetchCourses()
})
</script>

<style scoped>
.teacher-offline-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}
.page-breadcrumb { margin-bottom: var(--space-4); }
.filter-card { margin-bottom: var(--space-6); background: var(--el-fill-color-blank); border-radius: var(--radius-lg); box-shadow: var(--shadow-xs), var(--shadow-sm); }
.table-card { background: var(--el-fill-color-blank); border-radius: var(--radius-lg); box-shadow: var(--shadow-xs), var(--shadow-sm); transition: box-shadow var(--duration-base) var(--ease-out); }
.table-card:hover { box-shadow: var(--shadow-md), var(--shadow-lg); }
.table-card :deep(.el-card__header) { padding: var(--space-4) var(--space-5); border-bottom: 1px solid var(--el-border-color-lighter); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: var(--space-2); }
.card-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); letter-spacing: var(--tracking-wide); }
.pagination-wrap { margin-top: var(--space-4); display: flex; justify-content: center; padding: var(--space-4) var(--space-5); border-top: 1px solid var(--el-border-color-lighter); }
.data-table { width: 100%; border-radius: var(--radius-lg); overflow: hidden; }
.data-table :deep(.el-table__header) th { color: var(--el-text-color-primary); }
.data-table :deep(.el-table__row) { transition: background-color var(--duration-fast) var(--ease-out); }
.data-table :deep(.el-table__row:hover > td) { background-color: var(--role-primary-light-9); }
.data-table :deep(.el-table__row--striped > td) { background: transparent; }
.filter-input-w240 { width: 240px; }
.full-width { width: 100%; }
:deep(.el-button) { border-radius: var(--radius-md); }
:deep(.el-dialog) { border-radius: var(--radius-lg); }
.att-badge { display: inline-block; font-size: var(--text-xs); padding: 1px 6px; border-radius: var(--radius-sm); margin: 0 2px; }
.att-present { color: var(--el-color-success); background: var(--el-color-success-light-9); }
.att-late { color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.att-absent { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.att-excused { color: var(--el-color-info); background: var(--el-color-info-light-9); }
@media (max-width: 768px) {
  .teacher-offline-list-page { padding: var(--space-4); }
  .filter-card { margin-bottom: var(--space-4); }
}
</style>
