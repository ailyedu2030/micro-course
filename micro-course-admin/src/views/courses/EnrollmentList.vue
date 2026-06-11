<template>
  <div class="enrollment-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="所属课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable style="width: 200px">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>选课列表</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column prop="courseName" label="课程" min-width="150" />
        <el-table-column prop="userName" label="学生" min-width="100" />
        <el-table-column prop="progress" label="进度" width="100">
          <template #default="{ row }">
            {{ row.progress !== undefined ? row.progress + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="enrollmentStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.enrollmentStatus === 'PENDING'" type="warning">待审核</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'APPROVED'" type="success">已通过</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'WAITLIST'" type="info">候补</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'CANCELLED'" type="info">已取消</el-tag>
            <el-tag v-else-if="row.enrollmentStatus === 'REJECTED'" type="danger">已拒绝</el-tag>
            <el-tag v-else type="info">{{ row.enrollmentStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="grade" label="成绩" width="80">
          <template #default="{ row }">
            {{ row.grade !== undefined && row.grade !== null ? row.grade : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="enrolledAt" label="选课时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleCancel(row)">取消</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="学生ID" prop="userId">
          <el-input v-model="formData.userId" placeholder="请输入学生ID" type="number" />
        </el-form-item>
        <el-form-item label="进度" prop="progress">
          <el-slider v-model="formData.progress" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="enrollmentStatus">
          <el-select v-model="formData.enrollmentStatus" placeholder="请选择状态" style="width: 100%">
            <el-option label="待审核" value="PENDING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="候补" value="WAITLIST" />
            <el-option label="已取消" value="CANCELLED" />
            <el-option label="已拒绝" value="REJECTED" />
          </el-select>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCourseEnrollments, updateEnrollment, cancelEnrollment } from '@/api/enrollment'
import { getCourses } from '@/api/course'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  courseId: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('编辑选课')
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  courseId: null,
  userId: null,
  progress: 0,
  enrollmentStatus: 'PENDING'
})

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  userId: [{ required: true, message: '请输入学生ID', trigger: 'blur' }],
  enrollmentStatus: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const fetchCourses = async () => {
  try {
    const { data } = await getCourses({ size: 1000 })
    courseOptions.value = data.items || []
  } catch (error) {
    console.error('获取课程列表失败', error)
  }
}

const fetchData = async () => {
  if (!searchForm.courseId) return
  loading.value = true
  try {
    const { data } = await getCourseEnrollments(searchForm.courseId)
    const result = data
    tableData.value = Array.isArray(result) ? result : (result.items || [])
    totalElements.value = Array.isArray(result) ? result.length : (result.totalElements || tableData.value.length)
  } catch (error) {
    ElMessage.error('获取选课列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = ''
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑选课'
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.userId = row.userId
  formData.progress = row.progress || 0
  formData.enrollmentStatus = row.enrollmentStatus || 'PENDING'
  dialogVisible.value = true
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm('确定取消该选课记录?', '提示', { type: 'warning' })
    await cancelEnrollment(row.id)
    ElMessage.success('取消成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('取消失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await updateEnrollment(currentId.value, {
        courseId: formData.courseId,
        userId: formData.userId,
        progress: formData.progress,
        enrollmentStatus: formData.enrollmentStatus
      })
      ElMessage.success('编辑成功')
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error('编辑失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCourses()
  fetchData()
})
</script>

<style scoped>
.enrollment-list {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>