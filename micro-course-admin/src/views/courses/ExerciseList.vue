<!--
  练习列表
  路由路径: /courses/:courseId/exercises
  Phase 1
  Author: jackie
-->
<template>
  <div class="exercise-list-page">
    <!-- 顶栏筛选卡 -->
    <el-card class="search-card filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="filter-input-w200" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="searchForm.chapterId" placeholder="请选择章节" clearable class="filter-input-w200" :disabled="!searchForm.courseId">
            <el-option v-for="item in chapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
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
          <span class="card-title">练习列表</span>
          <el-button type="primary" @click="handleCreate">新增练习</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <template #empty>
          <el-empty description="暂无练习数据" />
        </template>
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" min-width="120" />
        <el-table-column prop="chapterName" label="章节" min-width="120" />
        <el-table-column prop="questionCount" label="题目数" width="100" align="center">
          <template #default="{ row }">
            {{ row.questionCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分钟)" width="120" align="center">
          <template #default="{ row }">
            {{ row.duration ? `${row.duration}分钟` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="passScore" label="及格分数" width="100" align="center">
          <template #default="{ row }">
            {{ row.passScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="500px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width" @change="handleFormCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" prop="chapterId">
          <el-select v-model="formData.chapterId" placeholder="请选择章节" class="full-width" :disabled="!formData.courseId">
            <el-option v-for="item in formChapterOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入练习标题" />
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="formData.duration" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="及格分数" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" placeholder="请输入描述" :rows="3" />
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
import { getExercises, createExercise, updateExercise, deleteExercise } from '@/api/exercise'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])
const chapterOptions = ref([])
const formChapterOptions = ref([])

const searchForm = reactive({
  courseId: null,
  chapterId: null
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增练习')
const isEdit = ref(false)
const currentId = ref(null)
const formRef = ref(null)

const formData = reactive({
  courseId: null,
  chapterId: null,
  title: '',
  duration: 30,
  passScore: 60,
  description: ''
})

const formRules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  chapterId: [{ required: true, message: '请选择章节', trigger: 'change' }],
  title: [{ required: true, message: '请输入练习标题', trigger: 'blur' }]
}

const fetchCourseOptions = async () => {
  try {
    const { data } = await getCourses({ page: 0, size: 1000 })
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchChapterOptions = async (courseId) => {
  if (!courseId) {
    chapterOptions.value = []
    return
  }
  try {
    const { data } = await getChapters({ courseId })
    chapterOptions.value = data.items || []
  } catch {
    ElMessage.error('获取章节列表失败')
  }
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
      chapterId: searchForm.chapterId || undefined,
      page: page.value - 1,
      size: size.value
    }
    const { data } = await getExercises(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error('获取练习列表失败')
  } finally {
    loading.value = false
  }
}

const handleCourseChange = (val) => {
  searchForm.chapterId = null
  if (val) {
    fetchChapterOptions(val)
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.courseId = null
  searchForm.chapterId = null
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

const handleCreate = () => {
  dialogTitle.value = '新增练习'
  isEdit.value = false
  currentId.value = null
  formData.courseId = searchForm.courseId
  formData.chapterId = null
  formData.title = ''
  formData.duration = 30
  formData.passScore = 60
  formData.description = ''
  formChapterOptions.value = searchForm.chapterId ? chapterOptions.value : []
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑练习'
  isEdit.value = true
  currentId.value = row.id
  formData.courseId = row.courseId
  formData.chapterId = row.chapterId
  formData.title = row.title
  formData.duration = row.duration || 30
  formData.passScore = row.passScore || 60
  formData.description = row.description || ''
  formChapterOptions.value = chapterOptions.value
  dialogVisible.value = true
}

const handleFormCourseChange = async (val) => {
  formData.chapterId = null
  if (val) {
    const { data } = await getChapters({ courseId: val })
    formChapterOptions.value = data.items || []
  } else {
    formChapterOptions.value = []
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该练习?', '提示', { type: 'warning' })
    await deleteExercise(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateExercise(currentId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createExercise(formData)
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

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

onMounted(() => {
  fetchCourseOptions()
})
</script>

<style scoped>
.exercise-list-page {
  padding: var(--space-5);
}

.filter-card {
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
}

.table-card {
  border-radius: var(--radius-md);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-3) var(--space-5);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: var(--text-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: flex-end;
}

.data-table {
  width: 100%;
  border-radius: var(--radius-md);
  overflow: hidden;
}

.data-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--color-bg-page);
}

.full-width {
  width: 100%;
}

.filter-input-w200 {
  width: 200px;
}

@media (max-width: 768px) {
  .exercise-list-page {
    padding: var(--space-3);
  }

  .filter-card {
    margin-bottom: var(--space-3);
  }

  .filter-input-w200 {
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