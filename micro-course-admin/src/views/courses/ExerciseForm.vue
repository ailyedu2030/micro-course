<!--
  练习表单（创建/编辑）
  路由路径: /courses/:courseId/exercises/form
  Phase 6 - 教师端补齐
  Author: jackie
-->
<template>
  <div class="exercise-form-page">
    <el-breadcrumb separator="→" style="margin-bottom:20px">
      <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">首页</el-breadcrumb-item>
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
        <el-form-item label="章节" prop="chapterIds">
          <el-select v-model="formData.chapterIds" placeholder="请选择章节（可多选）" multiple collapse-tags class="full-width" :disabled="!formData.courseId">
            <el-option v-for="ch in chapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
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

        <!-- 题库统计 & 随机选题 -->
        <el-divider v-if="formData.courseId" />
        <el-form-item v-if="formData.courseId" label="题库统计">
          <div class="bank-stats">
            <el-tag v-for="s in bankStats" :key="s.type" :type="s.count > 0 ? 'primary' : 'info'" size="small" class="stat-tag">
              {{ s.label }}: {{ s.count }} 题
            </el-tag>
            <el-tag type="primary" size="small" effect="dark">共 {{ totalBankCount }} 题</el-tag>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId" label="随机选题">
          <div class="random-pick">
            <div class="pick-filter">
              <span class="pick-filter-label">难度</span>
              <el-select v-model="pickDifficulty" placeholder="全部难度" clearable size="small" style="width:120px">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </div>
            <div v-for="s in bankStats" :key="s.type" class="pick-row">
              <span class="pick-label">{{ s.label }}</span>
              <el-input-number v-model="s.pickCount" :min="0" :max="s.count" size="small" controls-position="right" class="pick-input" />
              <span class="pick-hint">/ {{ s.count }} 题</span>
            </div>
            <el-button type="success" size="small" :disabled="totalPickCount === 0" @click="handleRandomPick">
              随机抽取 {{ totalPickCount }} 题
            </el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="formData.courseId && exerciseQuestions.length > 0" label="已选题">
          <div class="selected-info">
            <span class="selected-count">{{ exerciseQuestions.length }} 题已选</span>
            <el-button type="danger" size="small" plain @click="exerciseQuestions = []">清空</el-button>
          </div>
        </el-form-item>

        <!-- 题目预览入口 -->
        <el-form-item v-if="exerciseQuestions.length > 0" label="操作">
          <el-button type="primary" plain @click="handlePreviewQuestions" class="preview-btn">
            <el-icon><View /></el-icon>预览题目 ({{ exerciseQuestions.length }} 题)
          </el-button>
        </el-form-item>
      </el-form>

      <div class="form-footer">
        <el-button @click="handleBack">取消</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">保存</el-button>
      </div>
    </el-card>

    <!-- 题目预览弹窗（逐题分步） -->
    <el-dialog v-model="previewDialogVisible" title="题目预览" width="650px" @close="handlePreviewClose" :close-on-press-escape="true">
      <div v-if="currentPreviewQuestion" class="preview-content">
        <div class="preview-progress">
          第 {{ currentPreviewIndex + 1 }} / {{ exerciseQuestions.length }} 题
        </div>
        <div class="preview-question-type">
          <el-tag v-if="currentPreviewQuestion.questionType === 'SINGLE'" type="primary" size="small">单选题</el-tag>
          <el-tag v-else-if="currentPreviewQuestion.questionType === 'MULTIPLE'" type="success" size="small">多选题</el-tag>
          <el-tag v-else-if="currentPreviewQuestion.questionType === 'JUDGE'" type="warning" size="small">判断题</el-tag>
          <el-tag v-else type="info" size="small">简答题</el-tag>
          <span class="preview-difficulty">
            <el-tag v-if="currentPreviewQuestion.difficulty === 1" type="success" size="small">简单</el-tag>
            <el-tag v-else-if="currentPreviewQuestion.difficulty === 2" type="warning" size="small">中等</el-tag>
            <el-tag v-else-if="currentPreviewQuestion.difficulty === 3" type="danger" size="small">困难</el-tag>
          </span>
          <span class="preview-score">分值：{{ currentPreviewQuestion.score }}</span>
        </div>
        <h3 class="preview-title">{{ currentPreviewQuestion.content }}</h3>

        <!-- 单选题 -->
        <div v-if="currentPreviewQuestion.questionType === 'SINGLE'" class="preview-options">
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
        <div v-else-if="currentPreviewQuestion.questionType === 'MULTIPLE'" class="preview-options">
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
        <div v-else-if="currentPreviewQuestion.questionType === 'JUDGE'" class="preview-options">
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
        <div v-if="currentPreviewQuestion.explanation" class="preview-analysis">
          <span class="analysis-label">答案解析：</span>
          <span class="analysis-value">{{ currentPreviewQuestion.explanation }}</span>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { View } from '@element-plus/icons-vue'
import { getExerciseById, createExercise, updateExercise, addQuestionsToExercise } from '@/api/exercise'
import { getCourses } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getQuestions } from '@/api/question'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const submitLoading = ref(false)
const courseOptions = ref([])
const chapterOptions = ref([])
const exerciseQuestions = ref([])
const courseIdFromRoute = computed(() => route.params.courseId)
const exerciseId = computed(() => route.query.exerciseId)
const isEdit = computed(() => !!exerciseId.value)

const formData = reactive({
  title: '',
  courseId: null,
  chapterIds: [],
  passScore: 60,
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

// ===== 题库统计 & 随机选题 =====
// P1-C-7: 难度字符串→整数转换
const DIFFICULTY_MAP = { 'EASY': 1, 'MEDIUM': 2, 'HARD': 3 }
function resolveDifficulty(val) {
  if (!val) return undefined
  const key = String(val).toUpperCase()
  return DIFFICULTY_MAP[key] || undefined
}

const TYPE_LABELS = { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', FILL: '填空题', SHORT_ANSWER: '简答题' }
const bankStats = ref([])
const totalBankCount = ref(0)
const totalPickCount = computed(() => bankStats.value.reduce((s, t) => s + (t.pickCount || 0), 0))
const pickDifficulty = ref('')

// 课程/章节/难度变化时刷新题库统计
async function refreshBankStats() {
  if (!formData.courseId) { bankStats.value = []; totalBankCount.value = 0; return }
  try {
    const params = { courseId: formData.courseId, size: 9999 }
    if (formData.chapterIds && formData.chapterIds.length > 0) params.chapterIds = formData.chapterIds.join(',')
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    const { data } = await getQuestions(params)
    const items = data?.items || []
    const filterDiff = pickDifficulty.value ? resolveDifficulty(pickDifficulty.value) : undefined
    const filtered = filterDiff != null ? items.filter(q => q.difficulty === filterDiff) : items
    totalBankCount.value = filtered.length
    const groups = {}
    for (const q of filtered) {
      const t = q.questionType || 'OTHER'
      groups[t] = (groups[t] || 0) + 1
    }
    bankStats.value = Object.entries(TYPE_LABELS)
      .map(([type, label]) => ({ type, label, count: groups[type] || 0, pickCount: 0 }))
      .filter(s => s.count > 0)
  } catch { bankStats.value = []; totalBankCount.value = 0 }
}
watch(() => formData.chapterIds, refreshBankStats, { deep: true })
watch(pickDifficulty, refreshBankStats)

async function handleRandomPick() {
  const picks = {}
  for (const s of bankStats.value) {
    if (s.pickCount > 0) picks[s.type] = s.pickCount
  }
  try {
    const params = { courseId: formData.courseId, size: 9999 }
    if (formData.chapterIds && formData.chapterIds.length > 0) params.chapterIds = formData.chapterIds.join(',')
    if (pickDifficulty.value) params.difficulty = resolveDifficulty(pickDifficulty.value)
    const { data } = await getQuestions(params)
    let all = data?.items || []
    // 如果后端不支持按难度过滤，前端再过滤一次
    if (pickDifficulty.value && all.some(q => q.difficulty)) {
      const rd = resolveDifficulty(pickDifficulty.value)
      all = all.filter(q => q.difficulty === rd)
    }
    const picked = []
    for (const [type, count] of Object.entries(picks)) {
      const pool = all.filter(q => (q.questionType || '') === type)
      const shuffled = [...pool].sort(() => Math.random() - 0.5)
      picked.push(...shuffled.slice(0, count))
    }
    exerciseQuestions.value = [...exerciseQuestions.value, ...picked]
    bankStats.value.forEach(s => { s.pickCount = 0 })
    ElMessage.success(`已随机抽取 ${picked.length} 题`)
  } catch (e) { ElMessage.error(e?.response?.data?.message || '随机选题失败') }
}

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
  if (question.questionType === 'JUDGE') {
    return ans === 'true' || ans === true ? '正确' : '错误'
  }
  if (question.questionType === 'MULTIPLE' && question.options) {
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
  formData.chapterIds = []
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
  if (submitLoading.value) return
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      let exId = exerciseId.value
      if (isEdit.value) {
        await updateExercise(exId, formData)
        ElMessage.success('编辑成功')
      } else {
        const { data } = await createExercise(formData)
        exId = data.id || data
        ElMessage.success('创建成功')
      }
      // 自动保存已选的随机题目
      if (exerciseQuestions.value.length > 0) {
        const qIds = exerciseQuestions.value.map(q => q.id).filter(Boolean)
        if (qIds.length > 0) {
          await addQuestionsToExercise(exId, { questionIds: qIds })
        }
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
    const params = { page: 0, size: 1000 }
    if (userStore?.role === 'TEACHER') params.teacherId = userStore.userId
    const { data } = await getCourses(params)
    courseOptions.value = data.items || []
  } catch {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchExercise = async () => {
  if (!exerciseId.value) return
  try {
    const { data } = await getExerciseById(exerciseId.value)
    if (data) {
      formData.title = data.title || ''
      formData.courseId = data.courseId
      formData.chapterIds = data.chapterIds || []
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
      if (data.questions) {
        exerciseQuestions.value = data.questions
      }
    }
  } catch {
    ElMessage.error('获取练习信息失败')
  }
}

onMounted(() => {
  if (courseIdFromRoute.value) {
    formData.courseId = Number(courseIdFromRoute.value)
    handleCourseChange(formData.courseId)
  }
  fetchCourseOptions()
  if (isEdit.value) {
    fetchExercise()
  }
})
</script>

<style scoped>
.exercise-form-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.form-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
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

.exercise-form {
  max-width: 700px;
}

.full-width {
  width: 100%;
}

.field-hint {
  margin-left: var(--space-3);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.form-footer {
  padding: var(--space-4) 0;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
}

/* 预览弹窗样式 */
.preview-content {
  padding: var(--space-2) 0;
}

.preview-progress {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-3);
}

.preview-question-type {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-4);
}

.preview-difficulty {
  margin-left: var(--space-2);
}

.preview-score {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.preview-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-5) 0;
  line-height: 1.6;
}

.preview-options {
  margin-bottom: var(--space-5);
  padding: var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.preview-option-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
  font-size: var(--text-base);
  color: var(--el-text-color-regular);
  line-height: 1.5;
}

.preview-option-item:last-child {
  margin-bottom: 0;
}

.option-label {
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  flex-shrink: 0;
}

.option-text {
  flex: 1;
}

.preview-answer {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-7);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
}

.answer-label {
  font-weight: var(--weight-semibold);
  color: var(--el-color-success);
  margin-right: var(--space-2);
}

.answer-value {
  color: var(--el-color-success-dark-2);
}

.preview-analysis {
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
}

.analysis-label {
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-secondary);
  margin-right: var(--space-2);
}

.analysis-value {
  color: var(--el-text-color-secondary);
}

:deep(.el-button) {
  border-radius: var(--radius-md);
}

:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}
</style>