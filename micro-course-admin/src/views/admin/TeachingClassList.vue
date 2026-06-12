<!--
  教学班列表（管理端）
  路由路径: /admin/teaching-classes
  Author: jackie
-->
<template>
  <div class="teaching-class-list">
    <!-- 搜索区 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程名">
          <el-input v-model="searchForm.courseName" placeholder="请输入课程名称" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="教师名">
          <el-input v-model="searchForm.teacherName" placeholder="请输入教师名称" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="searchForm.semester" placeholder="如 2024-1" clearable class="search-input" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable class="search-select">
            <el-option label="待开课" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结课" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">教学班列表</span>
          <el-button type="primary" v-if="userRole !== 'ACADEMIC'" @click="handleCreate">新增教学班</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="教学班名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="授课教师" width="120" show-overflow-tooltip />
        <el-table-column prop="semester" label="学期" width="100" />
        <el-table-column prop="maxStudents" label="容量" width="80" align="center" />
        <el-table-column prop="currentStudents" label="已选人数" width="100" align="center">
          <template #default="{ row }">
            <span :class="row.currentStudents >= row.maxStudents ? 'text-danger' : 'text-success'">
              {{ row.currentStudents || 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该教学班？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
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

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" @close="handleDialogClose" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-position="top">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width" filterable @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="教学班名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入教学班名称" />
        </el-form-item>
        <el-form-item label="授课教师" prop="teacherId">
          <el-select v-model="formData.teacherId" placeholder="请选择授课教师" class="full-width" filterable>
            <el-option v-for="item in teacherOptions" :key="item.id" :label="item.realName || item.username" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期" prop="semester">
          <el-input v-model="formData.semester" placeholder="如 2024-1" />
        </el-form-item>
        <el-form-item label="最大人数" prop="maxStudents">
          <el-input-number v-model="formData.maxStudents" :min="1" :max="999" class="full-width" />
        </el-form-item>

        <el-form-item label="上课地点">
          <el-input v-model="formData.location" placeholder="如 教学楼A101" />
        </el-form-item>
        <!-- 上课时间表（动态行） -->
        <el-form-item label="上课时间表">
          <div class="schedule-list">
            <div v-for="(schedule, index) in formData.classSchedules" :key="index" class="schedule-row">
              <el-select v-model="schedule.dayOfWeek" placeholder="星期" class="day-select">
                <el-option label="周一" :value="1" />
                <el-option label="周二" :value="2" />
                <el-option label="周三" :value="3" />
                <el-option label="周四" :value="4" />
                <el-option label="周五" :value="5" />
                <el-option label="周六" :value="6" />
                <el-option label="周日" :value="7" />
              </el-select>
              <el-input-number v-model="schedule.startPeriod" :min="1" :max="12" class="period-input" title="开始节次" />
              <span class="period-separator">至</span>
              <el-input-number v-model="schedule.endPeriod" :min="1" :max="12" class="period-input" title="结束节次" />
              <el-input v-model="schedule.startTime" placeholder="开始时间" class="time-input" />
              <el-input v-model="schedule.endTime" placeholder="结束时间" class="time-input" />
              <el-input v-model="schedule.location" placeholder="上课地点" class="location-input" />
              <el-button type="danger" link @click="removeSchedule(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button type="primary" link @click="addSchedule">
              <el-icon><Plus /></el-icon>添加时间段
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import {
  getTeachingClasses,
  getTeachingClassById,
  createTeachingClass,
  updateTeachingClass,
  deleteTeachingClass,
  getCourses
} from '@/api/teaching-class'

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
  courseName: '',
  teacherName: '',
  semester: '',
  status: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增教学班')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  courseId: null,
  name: '',
  teacherId: null,
  semester: '',
  maxStudents: 30,
  location: '',
  classSchedules: []
})

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  name: [{ required: true, message: '请输入教学班名称', trigger: 'blur' }],
  teacherId: [{ required: true, message: '请选择授课教师', trigger: 'change' }],
  semester: [{ required: true, message: '请输入学期', trigger: 'blur' }],
  maxStudents: [{ required: true, message: '请输入最大人数', trigger: 'blur' }]
}

const statusMap = {
  0: { text: '待开课', type: 'info' },
  1: { text: '进行中', type: 'success' },
  2: { text: '已结课', type: 'warning' }
}

function getStatusText(status) {
  return statusMap[status]?.text || '未知'
}

function getStatusType(status) {
  return statusMap[status]?.type || 'info'
}

// 获取课程列表
async function fetchCourses() {
  try {
    const { data } = await getCourses({ size: 1000 })
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
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

// 添加时间段
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
function removeSchedule(index) {
  formData.classSchedules.splice(index, 1)
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseName: searchForm.courseName || undefined,
      teacherName: searchForm.teacherName || undefined,
      semester: searchForm.semester || undefined,
      status: searchForm.status !== null ? searchForm.status : undefined
    }
    const { data } = await getTeachingClasses(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取教学班列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  searchForm.courseName = ''
  searchForm.teacherName = ''
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
  dialogTitle.value = '新增教学班'
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
  dialogTitle.value = '编辑教学班'
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
    ElMessage.error('获取教学班详情失败')
  }
  dialogVisible.value = true
}

async function handleDelete(row) {
  try {
    await deleteTeachingClass(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
    ElMessage.error('删除失败')
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateTeachingClass(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createTeachingClass(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

function handleDialogClose() {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.teaching-class-list {
  padding: var(--space-4);
}

.filter-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
  background: var(--color-white, #ffffff);
  transition: box-shadow 200ms ease;
}

.filter-card:hover {
  box-shadow: var(--shadow-md);
}

.table-card {
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
  transition: box-shadow 200ms ease;
}

.table-card:hover {
  box-shadow: var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-3) var(--space-4);
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: 500;
  color: var(--color-text-primary, #303133);
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
}

.data-table {
  width: 100%;
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
</style>