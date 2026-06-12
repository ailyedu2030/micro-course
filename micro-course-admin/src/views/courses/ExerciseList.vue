<!--
  练习列表
  路由路径: /courses/:courseId/exercises
  Phase 1
  Author: jackie
-->
<template>
  <div class="exercise-list">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="search-input-w160">
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
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
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
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="success" link size="small" @click="handlePreview(row)">预览</el-button>
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
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="580px" @close="handleDialogClose">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="练习标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入练习标题" />
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width">
            <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节ID" prop="chapterId">
          <el-input v-model.number="formData.chapterId" placeholder="请输入章节ID" type="number" />
        </el-form-item>
        <el-form-item label="及格分数" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="时间限制" prop="timeLimit">
          <el-input-number v-model="formData.timeLimit" :min="0" :max="300" placeholder="分钟" class="full-width" />
        </el-form-item>
        <el-form-item label="最大尝试次数" prop="maxAttempts">
          <el-input-number v-model="formData.maxAttempts" :min="0" :max="10" class="full-width" />
        </el-form-item>
        <el-form-item label="显示答案时机" prop="showAnswerWhen">
          <el-select v-model="formData.showAnswerWhen" placeholder="请选择" class="full-width">
            <el-option label="提交后" value="AFTER_SUBMIT" />
            <el-option label="结束后" value="AFTER_FINISH" />
            <el-option label="永不" value="NEVER" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目乱序" prop="shuffleQuestions">
          <el-switch v-model="formData.shuffleQuestions" />
          <span class="form-tip">开启后学员作答时题目顺序随机</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 题目预览弹窗 -->
    <el-dialog v-model="previewDialogVisible" :title="`预览: ${previewExercise.title}`" width="700px">
      <div v-if="previewLoading" v-loading="previewLoading" class="preview-loading" />
      <div v-else-if="previewQuestions.length === 0" class="preview-empty">暂无题目</div>
      <div v-else class="preview-content">
        <div class="preview-nav">
          <el-button size="small" :disabled="previewCurrentIndex === 0" @click="handlePrevQuestion">上一题</el-button>
          <span class="preview-indicator">{{ previewCurrentIndex + 1 }} / {{ previewQuestions.length }}</span>
          <el-button size="small" :disabled="previewCurrentIndex === previewQuestions.length - 1" @click="handleNextQuestion">下一题</el-button>
        </div>
        <div v-if="previewQuestions[previewCurrentIndex]" class="preview-question">
          <div class="question-content">
            <span class="question-label">题干:</span>
            <span>{{ previewQuestions[previewCurrentIndex].content }}</span>
          </div>
          <div v-if="previewQuestions[previewCurrentIndex].options" class="question-options">
            <div v-for="(opt, idx) in previewQuestions[previewCurrentIndex].options" :key="idx" class="option-item">
              <span class="option-label">{{ opt.label }}.</span>
              <span>{{ opt.content }}</span>
            </div>
          </div>
          <div class="question-answer">
            <span class="question-label">正确答案:</span>
            <span>{{ previewQuestions[previewCurrentIndex].answer }}</span>
          </div>
          <div v-if="previewQuestions[previewCurrentIndex].explanation" class="question-explanation">
            <span class="question-label">解析:</span>
            <span>{{ previewQuestions[previewCurrentIndex].explanation }}</span>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * 练习列表页面 - Phase 6 增强：题目乱序 + 题目预览
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getExercises, createExercise, updateExercise, deleteExercise } from '@/api/exercise'
import { getCourses } from '@/api/course'
import { getQuestions } from '@/api/question'

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
  showAnswerWhen: 'AFTER_SUBMIT',
  shuffleQuestions: false
})

const formRules = {
  title: [{ required: true, message: '请输入练习标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  chapterId: [{ required: true, message: '请输入章节ID', trigger: 'blur' }],
  passScore: [{ required: true, message: '请输入及格分数', trigger: 'blur' }]
}

// 预览相关
const previewDialogVisible = ref(false)
const previewLoading = ref(false)
const previewExercise = ref({})
const previewQuestions = ref([])
const previewCurrentIndex = ref(0)

const fetchCourses = async () => {
  try {
    const { data } = await getCourses({ size: 1000 })
    courses.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
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
  } catch {
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
  formData.shuffleQuestions = false
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
  formData.shuffleQuestions = row.shuffleQuestions || false
  dialogVisible.value = true
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该练习?', '提示', { type: 'warning' })
    await deleteExercise(row.id)
    ElMessage.success('删除成功')
    fetchData()
  } catch {
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
    } catch {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
}

// 预览相关
const handlePreview = async (row) => {
  previewExercise.value = row
  previewCurrentIndex.value = 0
  previewDialogVisible.value = true
  previewLoading.value = true
  try {
    const { data } = await getQuestions({ exerciseId: row.id, size: 100 })
    previewQuestions.value = data.items || []
  } catch {
    ElMessage.error('获取题目失败')
  } finally {
    previewLoading.value = false
  }
}

const handlePrevQuestion = () => {
  if (previewCurrentIndex.value > 0) {
    previewCurrentIndex.value--
  }
}

const handleNextQuestion = () => {
  if (previewCurrentIndex.value < previewQuestions.value.length - 1) {
    previewCurrentIndex.value++
  }
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

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.preview-empty {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.preview-nav {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.preview-indicator {
  font-size: 14px;
  color: #606266;
}

.preview-question {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 16px;
}

.question-label {
  font-weight: 600;
  margin-right: 8px;
}

.question-content {
  margin-bottom: 12px;
}

.question-options {
  margin-bottom: 12px;
}

.option-item {
  padding: 4px 0;
}

.option-label {
  font-weight: 500;
  margin-right: 4px;
}

.question-answer {
  margin-bottom: 12px;
  color: #67c23a;
}

.question-explanation {
  color: #909399;
  font-size: 13px;
}

@media (max-width: 768px) {
  .exercise-list {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }
}

.data-table { width: 100%; }
.full-width { width: 100%; }
.search-input-w160 { width: 160px; }
.preview-loading { min-height: 200px; }
</style>