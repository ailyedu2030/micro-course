<template>
  <div class="exercise-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable style="width: 160px">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>练习管理</span>
          <el-button type="primary" @click="handleCreate">新增练习</el-button>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border style="width: 100%">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" width="150" />
        <el-table-column prop="chapterId" label="章节ID" width="100" align="center" />
        <el-table-column prop="questionCount" label="题目数" width="90" align="center">
          <template #default="{ row }">
            {{ row.questionCount ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="80" align="center">
          <template #default="{ row }">
            {{ row.totalScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="passScore" label="及格分" width="80" align="center">
          <template #default="{ row }">
            {{ row.passScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
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

    <!-- 弹窗区 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="练习标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入练习标题" />
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节ID" prop="chapterId">
          <el-input v-model.number="formData.chapterId" placeholder="请输入章节ID" type="number" />
        </el-form-item>
        <el-form-item label="及格分数" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间限制" prop="timeLimit">
          <el-input-number v-model="formData.timeLimit" :min="0" :max="300" placeholder="分钟" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大尝试次数" prop="maxAttempts">
          <el-input-number v-model="formData.maxAttempts" :min="0" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="显示答案时机" prop="showAnswerWhen">
          <el-select v-model="formData.showAnswerWhen" placeholder="请选择" style="width: 100%">
            <el-option label="提交后" value="AFTER_SUBMIT" />
            <el-option label="结束后" value="AFTER_FINISH" />
            <el-option label="永不" value="NEVER" />
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
import { getExercises, createExercise, updateExercise, deleteExercise } from '@/api/exercise'
import { getCourses } from '@/api/course'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courses = ref([])

const searchForm = reactive({
  courseId: ''
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增练习')
const isEdit = ref(false)
const formRef = ref(null)
const currentId = ref(null)

const formData = reactive({
  title: '',
  courseId: '',
  chapterId: null,
  passScore: 60,
  timeLimit: 60,
  maxAttempts: 3,
  showAnswerWhen: 'AFTER_SUBMIT'
})

const formRules = {
  title: [{ required: true, message: '请输入练习标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  chapterId: [{ required: true, message: '请输入章节ID', trigger: 'blur' }],
  passScore: [{ required: true, message: '请输入及格分数', trigger: 'blur' }]
}

const fetchCourses = async () => {
  try {
    const { data } = await getCourses({ size: 1000 })
    courses.value = data.items || []
  } catch (error) {
    console.error('获取课程列表失败', error)
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId || undefined
    }
    const { data } = await getExercises(params)
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch (error) {
    ElMessage.error('获取练习列表失败')
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

const handleCreate = () => {
  dialogTitle.value = '新增练习'
  isEdit.value = false
  currentId.value = null
  formData.title = ''
  formData.courseId = ''
  formData.chapterId = null
  formData.passScore = 60
  formData.timeLimit = 60
  formData.maxAttempts = 3
  formData.showAnswerWhen = 'AFTER_SUBMIT'
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑练习'
  isEdit.value = true
  currentId.value = row.id
  formData.title = row.title || ''
  formData.courseId = row.courseId || ''
  formData.chapterId = row.chapterId || null
  formData.passScore = row.passScore ?? 60
  formData.timeLimit = row.timeLimit ?? 60
  formData.maxAttempts = row.maxAttempts ?? 3
  formData.showAnswerWhen = row.showAnswerWhen || 'AFTER_SUBMIT'
  dialogVisible.value = true
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
        ElMessage.success('更新成功')
      } else {
        await createExercise(formData)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
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
.exercise-list {
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