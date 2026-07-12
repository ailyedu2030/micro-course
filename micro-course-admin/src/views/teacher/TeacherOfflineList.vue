<template>
  <div class="teacher-offline-list-page">
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/teacher/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>线下课管理</el-breadcrumb-item>
      <el-breadcrumb-item v-if="selectedCourseTitle">{{ selectedCourseTitle }}</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="所属课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w240" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="searchForm.chapterId" placeholder="请选择章节" clearable :disabled="!searchForm.courseId" class="filter-input-w240">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ chapterTitle || '线下场次列表' }}</span>
          <div class="header-actions">
            <el-button type="primary" :disabled="!searchForm.chapterId" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon>新增场次
            </el-button>
          </div>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty :description="searchForm.chapterId ? '暂无线下安排，点击「新增场次」添加' : '请先选择课程和章节'" :image-size="120" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column label="日期" width="130" align="center">
          <template #default="{ row }">
            {{ row.sessionDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160" align="center">
          <template #default="{ row }">
            {{ row.startTime ? row.startTime.substring(0, 5) : '-' }} ~ {{ row.endTime ? row.endTime.substring(0, 5) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="地点" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.location || '待定' }}
          </template>
        </el-table-column>
        <el-table-column label="签到情况" width="200" align="center">
          <template #default="{ row }">
            <span class="att-badge att-present">{{ row.presentCount || 0 }} 已签到</span>
            <span class="att-badge att-late">{{ row.lateCount || 0 }} 迟到</span>
            <span class="att-badge att-absent">{{ row.absentCount || 0 }} 缺勤</span>
            <span class="att-badge att-excused">{{ row.excusedCount || 0 }} 请假</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handleAttendance(row)">签到管理</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
          @current-change="handlePageChange" aria-label="分页导航"
/>
      </div>
    </el-card>

    <el-dialog v-model="showCreateDialog" :title="isEdit ? '编辑场次' : '新增场次'" width="500px" @close="resetForm">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="日期" prop="sessionDate">
          <el-date-picker v-model="formData.sessionDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" class="full-width" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker v-model="formData.startTime" placeholder="开始" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker v-model="formData.endTime" placeholder="结束" value-format="HH:mm:ss" class="full-width" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="地点" prop="location">
          <el-input v-model="formData.location" placeholder="如：教学楼 A-101" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.teacherNotes" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ isEdit ? '保存' : '新增' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAttendanceDialog" title="签到管理" width="600px">
      <el-table :data="attendanceData" stripe border v-loading="attendanceLoading">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="studentName" label="学员姓名" min-width="120" />
        <el-table-column prop="studentNumber" label="学号" width="120" />
        <el-table-column label="签到状态" width="140" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PRESENT'" type="success" size="small">已签到</el-tag>
            <el-tag v-else-if="row.status === 'LATE'" type="warning" size="small">迟到</el-tag>
            <el-tag v-else-if="row.status === 'ABSENT'" type="danger" size="small">缺勤</el-tag>
            <el-tag v-else-if="row.status === 'EXCUSED'" type="info" size="small">请假</el-tag>
            <el-tag v-else type="info" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkinTime" label="签到时间" width="160" align="center">
          <template #default="{ row }">
            {{ row.checkinTime ? new Date(row.checkinTime).toLocaleString('zh-CN') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-select v-model="row.editStatus" size="small" placeholder="修改状态" @change="(val) => handleUpdateAttendance(row, val)">
              <el-option label="已签到" value="PRESENT" />
              <el-option label="迟到" value="LATE" />
              <el-option label="缺勤" value="ABSENT" />
              <el-option label="请假" value="EXCUSED" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showAttendanceDialog = false">关闭</el-button>
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

const userStore = useUserStore()

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
  if (!searchForm.chapterId) return '线下场次列表'
  const ch = chapterOptions.value.find(c => c.id === searchForm.chapterId)
  return ch ? `「${ch.title}」场次` : '线下场次列表'
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
  sessionDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }]
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
    ElMessage.error('获取课程列表失败')
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
    chapterOptions.value = (data?.items || []).filter(ch => ch.chapterType === 'OFFLINE')
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
    ElMessage.error('获取线下场次失败')
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
  if (!formRef.value) return
  try { const v = await formRef.value.validate(); if (!v) return } catch { return }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateOfflineSession(editingId.value, {
        sessionDate: formData.sessionDate,
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location,
        teacherNotes: formData.teacherNotes || undefined
      })
      ElMessage.success('更新成功')
    } else {
      await createOfflineSession(searchForm.chapterId, {
        sessionDate: formData.sessionDate,
        startTime: formData.startTime,
        endTime: formData.endTime,
        location: formData.location,
        teacherNotes: formData.teacherNotes || undefined
      })
      ElMessage.success('创建成功')
    }
    showCreateDialog.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || (isEdit.value ? '更新失败' : '创建失败'))
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm('确定删除该场次?', '提示', { type: 'warning' }) } catch { return }
  try {
    await deleteOfflineSession(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
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
    ElMessage.error('获取签到记录失败')
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
    ElMessage.success('签到状态已更新')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || '更新失败')
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
