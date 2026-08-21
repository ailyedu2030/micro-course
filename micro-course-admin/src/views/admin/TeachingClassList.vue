<!--
  教学班列表（管理端）
  路由路径: /admin/teaching-classes
  Author: jackie
-->
<template>
  <div class="teaching-class-list">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="breadcrumb-nav">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">{{ $t('layout.home') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('route.TeachingClassList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.semester')">
          <el-input v-model="searchForm.semester" :placeholder="$t('teachingClass.semesterPlaceholder')" clearable class="search-input" />
        </el-form-item>
        <el-form-item :label="$t('app.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('teachingClass.allStatuses')" clearable class="search-select">
            <el-option :label="$t('teachingClass.statusStopped')" :value="0" />
            <el-option :label="$t('teachingClass.statusActive')" :value="1" />
            <el-option :label="$t('teachingClass.statusCompleted')" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('teachingClass.query') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('teachingClass.listTitle') }}</span>
          <el-button type="primary" v-if="userRole === 'ADMIN'" @click="handleCreate">{{ $t('teachingClass.create') }}</el-button>
        </div>
      </template>
      <!-- 骨架屏 -->
      <el-skeleton v-if="loading" :rows="6" animated />

      <!-- 空状态 -->
      <el-empty
        v-else-if="!loading && tableData.length === 0"
        :description="$t('teachingClass.noData')"
        :image-size="120"
      />

      <!-- 数据表格 -->
      <el-table v-loading="loading" v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column prop="id" :label="$t('teachingClass.id')" width="80" />
        <el-table-column prop="name" :label="$t('teachingClass.name')" min-width="150" show-overflow-tooltip />
         <el-table-column prop="courseTitle" :label="$t('course.courseName')" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag type="primary" size="small" effect="plain">{{ row.courseTitle || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="teacherName" :label="$t('course.teachingTeacher')" width="120" show-overflow-tooltip />
        <el-table-column prop="semester" :label="$t('course.semester')" width="100" />
        <el-table-column prop="maxStudents" :label="$t('teachingClass.capacity')" width="80" align="center" />
        <el-table-column prop="studentCount" :label="$t('teachingClass.enrolledCount')" width="100" align="center">
          <template #default="{ row }">
            <span :class="(row.studentCount || 0) >= row.maxStudents ? 'text-danger' : 'text-success'">
              {{ row.studentCount || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" :label="$t('app.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('teachingClass.createdAt')" width="170" :formatter="$formatDateTime" />
        <el-table-column :label="$t('app.operation')" width="260" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="row.status === 1" type="warning" link size="small" @click="handleComplete(row)">{{ $t('teachingClass.complete') }}</el-button>
            <el-button v-if="row.status === 1" type="danger" link size="small" @click="handleCancel(row)">{{ $t('teachingClass.cancel') }}</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="tableData.length > 0" class="pagination-wrap">
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

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" @close="handleDialogClose" destroy-on-close :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item :label="$t('course.title')" prop="courseId">
          <el-select v-model="formData.courseId" :placeholder="$t('teachingClass.pleaseSelectCourse')" class="full-width" filterable @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('teachingClass.name')" prop="name">
          <el-input v-model="formData.name" :placeholder="$t('teachingClass.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('course.teachingTeacher')" prop="teacherId">
          <el-select v-model="formData.teacherId" :placeholder="$t('course.selectTeacher')" class="full-width" filterable>
            <el-option v-for="item in teacherOptions" :key="item.id" :label="item.realName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.semester')" prop="semester">
          <el-input v-model="formData.semester" :placeholder="$t('teachingClass.semesterPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('teachingClass.maxStudents')" prop="maxStudents">
          <el-input-number v-model="formData.maxStudents" :min="1" :max="999" class="full-width" />
        </el-form-item>

        <el-form-item :label="$t('teachingClass.location')">
          <el-input v-model="formData.location" :placeholder="$t('teachingClass.locationPlaceholder')" />
        </el-form-item>
        <!-- 上课时间表（动态行） -->
        <el-form-item :label="$t('teachingClass.schedule')">
          <div class="schedule-list">
            <div v-for="(schedule, index) in formData.classSchedules" :key="index" class="schedule-row">
              <el-select v-model="schedule.dayOfWeek" :placeholder="$t('teachingClass.dayOfWeek')" class="day-select" :aria-label="$t('teachingClass.dayOfWeek')">
                <el-option :label="$t('teachingClass.mon')" :value="1" />
                <el-option :label="$t('teachingClass.tue')" :value="2" />
                <el-option :label="$t('teachingClass.wed')" :value="3" />
                <el-option :label="$t('teachingClass.thu')" :value="4" />
                <el-option :label="$t('teachingClass.fri')" :value="5" />
                <el-option :label="$t('teachingClass.sat')" :value="6" />
                <el-option :label="$t('teachingClass.sun')" :value="7" />
              </el-select>
              <el-input-number v-model="schedule.startPeriod" :min="1" :max="12" class="period-input" :title="$t('teachingClass.startPeriod')" :aria-label="$t('teachingClass.startPeriod')" />
              <span class="period-separator">{{ $t('teachingClass.to') }}</span>
              <el-input-number v-model="schedule.endPeriod" :min="1" :max="12" class="period-input" :title="$t('teachingClass.endPeriod')" :aria-label="$t('teachingClass.endPeriod')" />
              <el-input v-model="schedule.startTime" :placeholder="$t('course.startTime')" class="time-input" :aria-label="$t('course.startTime')" />
              <el-input v-model="schedule.endTime" :placeholder="$t('course.endTime')" class="time-input" :aria-label="$t('course.endTime')" />
              <el-input v-model="schedule.location" :placeholder="$t('teachingClass.location')" class="location-input" :aria-label="$t('teachingClass.location')" />
              <el-button type="danger" link @click="removeSchedule(index)" :aria-label="$t('app.delete')">
<el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" link @click="addSchedule" :aria-label="$t('app.submit')">
<el-icon><Plus /></el-icon>{{ $t('teachingClass.addSchedule') }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getTeachingClasses,
  getTeachingClassById,
  createTeachingClass,
  updateTeachingClass,
  deleteTeachingClass,
  completeTeachingClass,
  cancelTeachingClass
} from '@/api/teaching-class'
import { getCourses } from '@/api/course'
import { getUsers } from '@/api/user'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const courseOptions = ref([])
const teacherOptions = ref([])

const searchForm = reactive({
  semester: '',
  status: null
})

const dialogVisible = ref(false)
const dialogTitle = ref(t('teachingClass.create'))
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  courseId: null,
  name: '',
  teacherId: null,
  semester: '',
  schedule: '',
  maxStudents: 30,
  location: '',
  classSchedules: []
})

const formRules = {
  courseId: [{ required: true, message: t('teachingClass.pleaseSelectCourse'), trigger: 'change' }],
  name: [{ required: true, message: t('teachingClass.namePlaceholder'), trigger: 'blur' }],
  teacherId: [{ required: true, message: t('course.selectTeacher'), trigger: 'change' }],
  semester: [{ required: true, message: t('teachingClass.semesterRequired'), trigger: 'blur' }],
  maxStudents: [{ required: true, message: t('teachingClass.maxStudentsRequired'), trigger: 'blur' }]
}

const statusMap = {
  0: { text: t('teachingClass.statusStopped'), type: 'info' },
  1: { text: t('teachingClass.statusActive'), type: 'success' },
  2: { text: t('teachingClass.statusCompleted'), type: 'warning' }
}

function getStatusText(status) {
  return statusMap[status]?.text || t('course.unknown')
}

function getStatusType(status) {
  return statusMap[status]?.type || 'info'
}

// 获取课程列表
async function fetchCourses() {
  try {
    const params = { size: 100 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  }
}

// 课程变化时提取教师列表
function handleCourseChange(courseId) {
  const course = courseOptions.value.find(c => c.id === courseId)
  if (course && course.teacherId) {
    formData.teacherId = course.teacherId
  }
  // 重置教学班名称
  const courseName = course?.title || ''
  if (formData.name === '' || formData.name.startsWith(courseName.slice(0, 10))) {
    // auto-fill name suggestion
  }
}

// 新增时间段
function addSchedule() {
  formData.classSchedules.push({
    dayOfWeek: null,
    startPeriod: 1,
    endPeriod: 2,
    startTime: '',
    endTime: '',
    location: ''
  })
}

// 移除时间段
async function removeSchedule(index) {
  try {
    await ElMessageBox.confirm(t('teachingClass.confirmRemoveSchedule'), t('teachingClass.confirmRemoveTitle'), {
      type: 'warning', confirmButtonText: t('teachingClass.remove'), cancelButtonText: t('common.cancel')
    })
    formData.classSchedules.splice(index, 1)
    ElMessage.success(t('teachingClass.scheduleRemoved'))
  } catch {}
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      semester: searchForm.semester || undefined,
      status: searchForm.status !== null ? searchForm.status : undefined
    }
    const { data } = await getTeachingClasses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('teachingClass.fetchFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchForm.semester = ''
  searchForm.status = null
  page.value = 1
  fetchData()
}

function handleSizeChange() {
  page.value = 1
  fetchData()
}

function handlePageChange() {
  fetchData()
}

function handleCreate() {
  dialogTitle.value = t('teachingClass.create')
  isEdit.value = false
  currentId.value = null
  formData.courseId = null
  formData.name = ''
  formData.teacherId = null
  formData.semester = ''
  formData.maxStudents = 30
  formData.location = ''
  formData.classSchedules = []
  dialogVisible.value = true
}

async function handleEdit(row) {
  dialogTitle.value = t('teachingClass.edit')
  isEdit.value = true
  currentId.value = row.id
  try {
    const { data } = await getTeachingClassById(row.id)
    formData.courseId = data.courseId
    formData.name = data.name
    formData.teacherId = data.teacherId
    formData.semester = data.semester
    formData.maxStudents = data.maxStudents
    formData.location = data.location || ''
    formData.classSchedules = data.classSchedules || []
  } catch {
    ElMessage.error(t('teachingClass.fetchDetailFailed'))
  }
  dialogVisible.value = true
}

async function handleComplete(row) {
  try {
    await ElMessageBox.confirm(t('teachingClass.confirmComplete', { name: row.name }), t('course.hintTitle'), { type: 'warning' })
    await completeTeachingClass(row.id)
    ElMessage.success(t('teachingClass.completeSuccess'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(t('teachingClass.completeFailed'))
  }
}

async function handleCancel(row) {
  let reason = ''
  try {
    await ElMessageBox.prompt(t('teachingClass.confirmCancel', { name: row.name }), t('teachingClass.cancelTitle'), {
      confirmButtonText: t('teachingClass.confirmCancelBtn'),
      cancelButtonText: t('common.cancel'),
      inputType: 'textarea',
      inputPlaceholder: t('teachingClass.cancelReasonPlaceholder'),
      inputValidator: (val) => !!val.trim() || t('teachingClass.cancelReasonRequired')
    }).then(({ value }) => { reason = value })
    await cancelTeachingClass(row.id, reason)
    ElMessage.success(t('teachingClass.cancelSuccess'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(t('teachingClass.cancelFailed'))
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(t('teachingClass.confirmDelete'), t('course.hintTitle'), { type: 'warning' })
    await deleteTeachingClass(row.id)
    ElMessage.success(t('course.deleteSuccess'))
    fetchData()
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) ElMessage.error(t('course.deleteFailed'))
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  // 排课必填字段校验：至少一条完整的时间段记录
  const hasValidSchedule = formData.classSchedules.length > 0 &&
    formData.classSchedules.every(s => s.dayOfWeek && s.startPeriod && s.endPeriod)
  if (!hasValidSchedule) {
    ElMessage.warning(t('teachingClass.scheduleRequired'))
    return
  }
  // P1 幂等修复: validate 回调是异步的, 必须在 await 前置位 loading 防连点重复提交
  if (submitLoading.value) return
  submitLoading.value = true
  await formRef.value.validate(async (valid) => {
    if (!valid) { submitLoading.value = false; return }
    try {
      if (isEdit.value) {
        await updateTeachingClass(currentId.value, formData)
        ElMessage.success(t('teachingClass.editSuccess'))
      } else {
        await createTeachingClass(formData)
        ElMessage.success(t('course.createSuccess'))
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? t('teachingClass.editFailed') : t('course.createFailed'))
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDialogClose() {
  formRef.value?.resetFields()
  // Manually clear classSchedules because resetFields() cannot reset dynamic array rows
  // that have no prop attribute in the form item
  formData.classSchedules = []
}

// P1-2026-08-21: 授课教师下拉无数据源(teacherOptions 从未加载), 补齐加载逻辑
async function fetchTeacherOptions() {
  try {
    const { data } = await getUsers({ role: 'TEACHER', page: 0, size: 100 })
    const list = Array.isArray(data) ? data : (data?.items || data?.records || [])
    teacherOptions.value = list
  } catch (e) {
    console.warn('[TeachingClassList] 教师列表加载失败', e)
  }
}

onMounted(() => {
  fetchCourses()
  fetchTeacherOptions()
  fetchData()
})
</script>

<style scoped>
.teaching-class-list {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.breadcrumb-nav {
  margin-bottom: var(--space-4);
}

.filter-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.filter-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card {
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

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
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

.search-input {
  width: 160px;
}

.search-select {
  width: 140px;
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
}

.data-table :deep(.el-table__header th) {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.data-table :deep(.el-table__row) {
  transition: background var(--duration-fast) var(--ease-out);
}

.full-width {
  width: 100%;
}

/* 时间表样式 */
.schedule-list {
  width: 100%;
}

.schedule-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
  flex-wrap: wrap;
}

.day-select {
  width: 100px;
}

.period-input {
  width: 80px;
}

.period-separator {
  color: var(--el-text-color-secondary);
  font-size: var(--text-sm);
}

.time-input {
  width: 100px;
}

.location-input {
  width: 120px;
}

.text-success {
  color: var(--el-color-success);
}

.text-danger {
  color: var(--el-color-danger);
}

/* 弹窗 border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
:deep(.el-dialog__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
:deep(.el-dialog__body) {
  padding: var(--space-5);
}
:deep(.el-dialog__footer) {
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
