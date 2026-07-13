<!--
  章节列表
  路由路径: /courses/:courseId/chapters
  Phase 1
  Author: jackie
-->
<template>
  <div class="chapter-list-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>章节管理</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w240" :disabled="courseOptions.length <= 1" @change="handleSearch">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="currentCourse">
          <el-button @click="goBackToCourse">返回课程详情</el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格卡 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">
            <span v-if="currentCourse">{{ currentCourse.title }} - 章节列表</span>
            <span v-else>章节列表</span>
          </span>
          <el-button type="primary" :disabled="!searchForm.courseId" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" @click="handleCreate">新增章节</el-button>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="6" animated />
      <el-empty v-else-if="!searchForm.courseId" description="请先选择课程" />
      <el-empty v-else-if="tableData.length === 0" description="暂无章节数据" />
      <el-table v-else :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="chapterType" label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.chapterType === 'VIDEO'" type="primary" size="small">视频</el-tag>
            <el-tag v-else-if="row.chapterType === 'EXERCISE'" type="warning" size="small">📝 练习</el-tag>
            <el-tag v-else-if="row.chapterType === 'INTERACTIVE'" type="success" size="small">互动课件</el-tag>
            <el-tag v-else-if="row.chapterType === 'OFFLINE'" type="info" size="small">线下课</el-tag>
            <el-tag v-else type="info" size="small">{{ row.chapterType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长" width="100" align="center">
          <template #default="{ row }">
            {{ row.duration ? `${row.duration}分钟` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap" v-if="!loading && tableData.length > 0">
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

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" @change="onCourseChange" placeholder="请选择课程" class="full-width">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入章节标题" />
        </el-form-item>
        <el-form-item label="类型" prop="chapterType">
          <el-select v-model="formData.chapterType" placeholder="请选择类型" class="full-width">
            <el-option label="视频" value="VIDEO" />
            <el-option label="互动课件" value="INTERACTIVE" />
            <el-option label="练习" value="EXERCISE" />
            <el-option label="线下课" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="formData.sortOrder" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="formData.duration" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getChapters, createChapter, updateChapter, deleteChapter } from '@/api/chapter'
import { getCourses } from '@/api/course'

const userStore = useUserStore()
const userRole = computed(() => userStore.role)

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)

const searchForm = reactive({
  courseId: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增章节')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)
const courseOptions = ref([])
const currentCourse = computed(() => courseOptions.value.find(c => c.id === searchForm.courseId) || null)
const router = useRouter()

const formData = reactive({
  courseId: null,
  title: '',
  chapterType: 'VIDEO',
  sortOrder: 0,
  duration: 0,
  description: ''
})

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  title: [{ required: true, message: '请输入章节标题', trigger: 'blur' }],
  chapterType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  sortOrder: [{ required: true, message: '请输入排序号', trigger: 'blur' }, { type: 'number', min: 0, message: '最小为0', trigger: 'blur' }]
}

const fetchData = async () => {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = {
      courseId: searchForm.courseId,
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getChapters(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取章节列表失败')
  } finally {
    loading.value = false
  }
}

 const fetchCourseOptions = async () => {
  try {
    const params = { page: 0, size: 1000 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
    if (!searchForm.courseId && courseOptions.value.length === 1) {
      searchForm.courseId = courseOptions.value[0].id
      handleSearch()
    }
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = null
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

const goBackToCourse = () => {
  if (searchForm.courseId) router.push(`/teacher/courses/${searchForm.courseId}`)
}
const handleCreate = () => {
  dialogTitle.value = '新增章节'
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.title = ''
  formData.chapterType = 'VIDEO'
  formData.sortOrder = 0
  formData.duration = 0
  formData.description = ''
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑章节'
  isEdit.value = true
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.title = row.title
  formData.chapterType = row.chapterType
  formData.sortOrder = row.sortOrder
  formData.duration = row.duration || 0
  formData.description = row.description || ''
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该章节?', '提示', { type: 'warning' })
    await deleteChapter(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (submitLoading.value) return
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch { return }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updateChapter(currentId.value, formData)
      ElMessage.success('编辑成功')
    } else {
      await createChapter(formData)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || (isEdit.value ? '编辑失败' : '创建失败'))
  } finally {
    submitLoading.value = false
  }
}

const onCourseChange = (val) => {
  // 课程切换—预留联动逻辑
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCourseOptions()
})
</script>

<style scoped>
.chapter-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
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

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.full-width {
  width: 100%;
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w240 {
  width: 240px;
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .chapter-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }

  .filter-input-w240 {
    width: 100%;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }

  .pagination-wrap {
    justify-content: center;
  }
}
</style>