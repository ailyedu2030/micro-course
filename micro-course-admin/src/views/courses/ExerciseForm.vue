<!--
  练习表单（创建/编辑）
  路由路径: /courses/:courseId/exercises/form
  Phase 6 - 教师端补齐
  Author: jackie
-->
<template>
  <div class="exercise-form-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>课程管理</el-breadcrumb-item>
      <el-breadcrumb-item>{{ isEdit ? '编辑练习' : '新增练习' }}</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="form-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ isEdit ? '编辑练习' : '新增练习' }}</span>
          <el-button @click="handleBack">返回</el-button>
        </div>
      </template>

      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="exercise-form">
        <el-form-item label="练习标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入练习标题" />
        </el-form-item>
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="formData.courseId" placeholder="请选择课程" class="full-width" @change="handleCourseChange">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" prop="chapterId">
          <el-select v-model="formData.chapterId" placeholder="请选择章节" class="full-width" :disabled="!formData.courseId">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时长(分钟)" prop="duration">
          <el-input-number v-model="formData.duration" :min="0" class="full-width" />
        </el-form-item>
        <el-form-item label="及格分数" prop="passScore">
          <el-input-number v-model="formData.passScore" :min="0" :max="100" class="full-width" />
        </el-form-item>
        <el-form-item label="时间限制" prop="timeLimit">
          <el-input-number v-model="formData.timeLimit" :min="0" placeholder="0表示无限制" class="full-width" />
        </el-form-item>
        <el-form-item label="答题次数" prop="maxAttempts">
          <el-input-number v-model="formData.maxAttempts" :min="0" placeholder="0表示无限制" class="full-width" />
        </el-form-item>
        <el-form-item label="题目乱序" prop="shuffleQuestions">
          <el-switch v-model="formData.shuffleQuestions" />
          <span class="field-hint">开启后学员作答时题目顺序随机</span>
        </el-form-item>
        <el-form-item label="选项乱序" prop="shuffleOptions">
          <el-switch v-model="formData.shuffleOptions" />
          <span class="field-hint">开启后学员作答时选项顺序随机</span>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入练习描述" />
        </el-form-item>

        <!-- 题目预览入口 -->
        <el-form-item v-if="exerciseQuestions.length > 0" label="题目预览">
          <el-button type="primary" plain @click="handlePreviewQuestions">
            预览题目 ({{ exerciseQuestions.length }} 题)
          </el-button>
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-button @click="handleBack">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
      </div>
    </el-card>

    <!-- 题目预览弹窗（逐题分步） -->
    <el-dialog v-model="previewDialogVisible" title="题目预览" width="650px" @close="handlePreviewClose" :close-on-press-escape="true">
      <div v-if="currentPreviewQuestion" class="preview-content">
        <div class="preview-progress">
          第 {{ currentPreviewIndex + 1 }} / {{ exerciseQuestions.length }} 题
        </div>
        <div class="preview-question-type">
          <el-tag v-if="currentPreviewQuestion.questionType === 'SINGLE_CHOICE'" type="primary" size="small">单选题</el-tag>
          <el-tag v-else-if="currentPreviewQuestion.questionType === 'MULTIPLE_CHOICE'" type="success" size="small">多选题</el-tag>
          <el-tag v-else-if="currentPreviewQuestion.questionType === 'TRUE_FALSE'" type="warning" size="small">判断题</el-tag>
          <el-tag v-else type="info" size="small">简答题</el-tag>
          <span class="preview-difficulty">
            <el-tag v-if="currentPreviewQuestion.difficulty === 'EASY'" type="success" size="small">简单</el-tag>
            <el-tag v-else-if="currentPreviewQuestion.difficulty === 'MEDIUM'" type="warning" size="small">中等</el-tag>
            <el-tag v-else-if="currentPreviewQuestion.difficulty === 'HARD'" type="danger" size="small">困难</el-tag>
          </span>
          <span class="preview-score">分值：{{ currentPreviewQuestion.score }}</span>
        </div>
        <h3 class="preview-title">{{ currentPreviewQuestion.content }}</h3>

        <!-- 单选题 -->
        <div v-if="currentPreviewQuestion.questionType === 'SINGLE_CHOICE'" class="preview-options">
          <div
            v-for="(opt, idx) in parsedOptions(currentPreviewQuestion.options)"
            :key="idx"
            class="preview-option-item"
          >
            <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][idx] }}.</span>
            <span class="option-text">{{ opt.label }}</span>
          </div>
        </div>

        <!-- 多选题 -->
        <div v-else-if="currentPreviewQuestion.questionType === 'MULTIPLE_CHOICE'" class="preview-options">
          <div
            v-for="(opt, idx) in parsedOptions(currentPreviewQuestion.options)"
            :key="idx"
            class="preview-option-item"
          >
            <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][idx] }}.</span>
            <span class="option-text">{{ opt.label }}</span>
          </div>
        </div>

        <!-- 判断题 -->
        <div v-else-if="currentPreviewQuestion.questionType === 'TRUE_FALSE'" class="preview-options">
          <div class="preview-option-item"><span class="option-label">A.</span><span class="option-text">正确</span></div>
          <div class="preview-option-item"><span class="option-label">B.</span><span class="option-text">错误</span></div>
        </div>

        <!-- 简答题 -->
        <div v-else-if="currentPreviewQuestion.questionType === 'SHORT_ANSWER'" class="preview-options">
          <el-input type="textarea" :rows="3" placeholder="学员在此输入答案" disabled />
        </div>

        <!-- 正确答案 -->
        <div class="preview-answer">
          <span class="answer-label">正确答案：</span>
          <span class="answer-value">{{ displayAnswer(currentPreviewQuestion) }}</span>
        </div>

        <!-- 答案解析 -->
        <div v-if="currentPreviewQuestion.analysis" class="preview-analysis">
          <span class="analysis-label">答案解析：</span>
          <span class="analysis-value">{{ currentPreviewQuestion.analysis }}</span>
        </div>
      </div>

      <template #footer>
        <el-button :disabled="currentPreviewIndex === 0" @click="handlePrevQuestion">上一题</el-button>
        <el-button :disabled="currentPreviewIndex === exerciseQuestions.length - 1" type="primary" @click="handleNextQuestion">下一题</el-button>
        <el-button @click="previewDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getExercises, createExercise, updateExercise } from '@/api/exercise'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'

const router = useRouter()
const route = useRoute()

const formRef = ref(null)
const submitLoading = ref(false)
const courseOptions = ref([])
const chapterOptions = ref([])
const exerciseQuestions = ref([])
const exerciseId = computed(() => route.params.id)
const isEdit = computed(() => !!exerciseId.value)

const formData = reactive({
  title: '',
  courseId: null,
  chapterId: null,
  duration: 30,
  passScore: 60,
  description: '',
  timeLimit: null,
  maxAttempts: null,
  shuffleQuestions: false,
  shuffleOptions: false
})

const formRules = {
  title: [{ required: true, message: '请输入练习标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }]
}

// 题目预览相关
const previewDialogVisible = ref(false)
const currentPreviewIndex = ref(0)
const currentPreviewQuestion = computed(() => exerciseQuestions.value[currentPreviewIndex.value] || null)

const parsedOptions = (optionsStr) => {
  if (!optionsStr) return []
  try {
    return JSON.parse(optionsStr)
  } catch {
    return []
  }
}

const displayAnswer = (question) => {
  if (!question?.answer) return '-'
  const ans = question.answer
  if (question.questionType === 'TRUE_FALSE') {
    return ans === 'true' || ans === true ? '正确' : '错误'
  }
  if (question.questionType === 'MULTIPLE_CHOICE' && question.options) {
    const opts = parsedOptions(question.options)
    const labels = opts.map(o => o.label)
    return ans.split(',').map(a => {
      const idx = labels.findIndex(l => l === a.trim())
      return idx >= 0 ? String.fromCharCode(65 + idx) : a.trim()
    }).join(',')
  }
  return ans
}

const handlePreviewQuestions = () => {
  currentPreviewIndex.value = 0
  previewDialogVisible.value = true
}

const handlePreviewClose = () => {
  previewDialogVisible.value = false
}

const handlePrevQuestion = () => {
  if (currentPreviewIndex.value > 0) {
    currentPreviewIndex.value--
  }
}

const handleNextQuestion = () => {
  if (currentPreviewIndex.value < exerciseQuestions.value.length - 1) {
    currentPreviewIndex.value++
  }
}

const handleCourseChange = async (val) => {
  formData.chapterId = null
  if (val) {
    try {
      const { data } = await getChapters({ courseId: val })
      chapterOptions.value = data.items || []
    } catch {
      ElMessage.error('获取章节列表失败')
    }
  } else {
    chapterOptions.value = []
  }
}

const handleBack = () => {
  router.back()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value) {
        await updateExercise(exerciseId.value, formData)
        ElMessage.success('编辑成功')
      } else {
        await createExercise(formData)
        ElMessage.success('创建成功')
      }
      router.back()
    } catch {
      ElMessage.error(isEdit.value ? '编辑失败' : '创建失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const fetchCourseOptions = async () => {
  try {
    const { data } = await getCourses({ page: 0, size: 1000 })
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchExercise = async () => {
  if (!exerciseId.value) return
  try {
    const { data } = await getExercises({ id: exerciseId.value })
    if (data) {
      formData.title = data.title || ''
      formData.courseId = data.courseId
      formData.chapterId = data.chapterId
      formData.duration = data.duration || 30
      formData.passScore = data.passScore || 60
      formData.description = data.description || ''
      formData.timeLimit = data.timeLimit || null
      formData.maxAttempts = data.maxAttempts || null
      formData.shuffleQuestions = data.shuffleQuestions || false
      formData.shuffleOptions = data.shuffleOptions || false

      // 加载章节列表
      if (data.courseId) {
        const { data: chData } = await getChapters({ courseId: data.courseId })
        chapterOptions.value = chData.items || []
      }

      // 加载已选题目的详细信息
      if (data.questionDetails) {
        exerciseQuestions.value = data.questionDetails
      }
    }
  } catch {
    ElMessage.error('获取练习信息失败')
  }
}

onMounted(() => {
  fetchCourseOptions()
  if (isEdit.value) {
    fetchExercise()
  }
})
</script>

<style scoped>
.exercise-form-page {
  padding: 24px;
  background: #F5F6FA;
  min-height: 100%;
}

.form-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: none;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
}

.exercise-form {
  max-width: 700px;
}

.full-width {
  width: 100%;
}

.field-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.form-footer {
  padding: 16px 0;
  border-top: 1px solid #F1F5F9;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* 预览弹窗样式 */
.preview-content {
  padding: 8px 0;
}

.preview-progress {
  font-size: 13px;
  color: #64748B;
  margin-bottom: 12px;
}

.preview-question-type {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.preview-difficulty {
  margin-left: 8px;
}

.preview-score {
  font-size: 13px;
  color: #64748B;
}

.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
  margin: 0 0 20px 0;
  line-height: 1.6;
}

.preview-options {
  margin-bottom: 20px;
  padding: 16px;
  background: #F8FAFC;
  border-radius: 8px;
}

.preview-option-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #334155;
  line-height: 1.5;
}

.preview-option-item:last-child {
  margin-bottom: 0;
}

.option-label {
  font-weight: 600;
  color: #1E293B;
  flex-shrink: 0;
}

.option-text {
  flex: 1;
}

.preview-answer {
  margin-top: 16px;
  padding: 12px 16px;
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  border-radius: 8px;
  font-size: 14px;
}

.answer-label {
  font-weight: 600;
  color: #166534;
  margin-right: 8px;
}

.answer-value {
  color: #15803D;
}

.preview-analysis {
  margin-top: 12px;
  padding: 12px 16px;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 14px;
}

.analysis-label {
  font-weight: 600;
  color: #475569;
  margin-right: 8px;
}

.analysis-value {
  color: #64748B;
}

:deep(.el-button) {
  border-radius: 8px;
}

:deep(.el-dialog) {
  border-radius: 12px;
}
</style>