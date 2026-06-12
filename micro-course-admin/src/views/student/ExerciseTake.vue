<!--
  章节练习
  路由路径: /student/exercise/:chapterId
  Phase 2
  Author: jackie
-->
<template>
  <div class="exercise-take-page">
    <!-- ===== 练习入口：章节练习列表 ===== -->
    <div v-if="!exerciseStarted" class="exercise-list-view">
      <div class="page-header">
        <h2 class="page-title">随堂练习</h2>
        <p class="page-subtitle">共 {{ exerciseList.length }} 个练习</p>
      </div>

      <div v-if="loading" class="loading-wrap">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>

      <div v-else-if="exerciseList.length === 0" class="empty-wrap">
        <el-empty description="本章节暂无练习" />
      </div>

      <div v-else class="exercise-cards">
        <el-card
          v-for="ex in exerciseList"
          :key="ex.id"
          class="exercise-card"
          shadow="never"
        >
          <div class="card-body">
            <div class="card-info">
              <h3 class="exercise-title">{{ ex.title }}</h3>
              <div class="exercise-meta">
                <el-tag v-if="ex.questionCount" size="small" effect="plain">
                  {{ ex.questionCount }} 题
                </el-tag>
                <el-tag v-if="ex.timeLimit" size="small" effect="plain" type="info">
                  {{ ex.timeLimit }}分钟
                </el-tag>
                <el-tag v-if="ex.passScore" size="small" effect="plain" type="warning">
                  及格 {{ ex.passScore }}分
                </el-tag>
              </div>
            </div>
            <div class="card-actions">
              <el-button
                type="primary"
                size="default"
                @click="startExercise(ex)"
              >
                开始答题
              </el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- ===== 答题界面 ===== -->
    <div v-else class="exercise-answer-view">
      <!-- 顶部进度条 -->
      <div class="progress-bar-wrap">
        <div class="progress-inner">
          <span class="progress-text">第 {{ currentIndex + 1 }} / {{ totalQuestions }} 题</span>
          <el-progress
            :percentage="progressPercent"
            :show-text="false"
            :stroke-width="6"
            color="#667eea"
          />
         <span v-if="timeLimit" class="time-left">
            <el-icon><Timer /></el-icon>
            {{ formatTimeLeft(timeLeft) }}
          </span>
        </div>
      </div>

      <!-- 题目卡片 -->
      <div class="question-card-wrap">
        <el-card class="question-card" shadow="never">
          <!-- 题目类型标签 -->
          <div class="question-type-bar">
            <el-tag size="small" effect="plain" :type="questionTypeTagType(currentQuestion.questionType)">
              {{ questionTypeLabel(currentQuestion.questionType) }}
            </el-tag>
          </div>

          <!-- 题目内容 -->
          <div class="question-content">
            <p class="question-text">{{ currentQuestion.content }}</p>
          </div>

          <!-- 选项区 -->
          <div class="question-options">
            <!-- SINGLE / JUDGE -->
            <template v-if="currentQuestion.questionType === 'SINGLE' || currentQuestion.questionType === 'JUDGE'">
              <el-radio-group
                v-model="answers[currentQuestion.id]"
                class="option-group"
                :disabled="submitted"
              >
                <div
                  v-for="(opt, idx) in currentQuestion.options"
                  :key="idx"
                  class="option-item"
                  :class="{
                    'option-correct': submitted && opt.value === currentQuestion.answer,
                    'option-wrong': submitted && opt.value === answers[currentQuestion.id] && opt.value !== currentQuestion.answer,
                    'option-selected': answers[currentQuestion.id] === opt.value,
                  }"
                >
                  <el-radio :value="opt.value" :label="opt.value">
                    <span class="option-label">{{ opt.label }}.</span>
                    <span class="option-text">{{ opt.text }}</span>
                  </el-radio>
                </div>
              </el-radio-group>
            </template>

            <!-- MULTIPLE -->
            <template v-else-if="currentQuestion.questionType === 'MULTIPLE'">
              <el-checkbox-group
                v-model="multipleAnswers[currentQuestion.id]"
                class="option-group"
                :disabled="submitted"
              >
                <div
                  v-for="(opt, idx) in currentQuestion.options"
                  :key="idx"
                  class="option-item"
                  :class="{
                    'option-correct': submitted && isMultipleCorrect(opt.value, currentQuestion.answer),
                    'option-wrong': submitted && multipleAnswers[currentQuestion.id]?.includes(opt.value) && !isMultipleCorrect(opt.value, currentQuestion.answer),
                  }"
                >
                  <el-checkbox :value="opt.value" :label="opt.value">
                    <span class="option-label">{{ opt.label }}.</span>
                    <span class="option-text">{{ opt.text }}</span>
                  </el-checkbox>
                </div>
              </el-checkbox-group>
            </template>

            <!-- FILL -->
            <template v-else-if="currentQuestion.questionType === 'FILL'">
              <el-input
                v-model="answers[currentQuestion.id]"
                type="textarea"
                :rows="3"
                :disabled="submitted"
                placeholder="请输入您的答案"
                class="fill-input"
              />
            </template>
          </div>

          <!-- 答案解析（提交后显示） -->
          <div v-if="submitted" class="answer-analysis">
            <div class="analysis-row user-answer">
              <span class="analysis-label">您的答案：</span>
              <span
                class="analysis-value"
                :class="isCurrentCorrect ? 'text-success' : 'text-danger'"
              >
                <el-icon v-if="!isCurrentCorrect"><Close /></el-icon>
                <el-icon v-else><Check /></el-icon>
                {{ formatUserAnswer(currentQuestion) }}
              </span>
            </div>
            <div class="analysis-row correct-answer">
              <span class="analysis-label">正确答案：</span>
              <span class="analysis-value text-success">
                <el-icon><Check /></el-icon>
                {{ formatCorrectAnswer(currentQuestion) }}
              </span>
            </div>
            <div v-if="currentQuestion.explanation" class="analysis-row explanation">
              <span class="analysis-label">解析：</span>
              <span class="analysis-value explanation-text">{{ currentQuestion.explanation }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 底部导航按钮 -->
      <div class="bottom-nav">
        <el-button @click="prevQuestion" :disabled="currentIndex === 0">
         上一题
        </el-button>

        <div class="nav-center">
          <template v-if="!submitted">
            <el-button
              v-if="currentIndex < totalQuestions - 1"
              type="primary"
              @click="nextQuestion"
            >
              下一题
            </el-button>
            <el-button
              v-else
              type="success"
              :loading="submitting"
              @click="handleSubmit"
            >
              提交答案
            </el-button>
          </template>
          <template v-else>
            <el-button
              v-if="currentIndex < totalQuestions - 1"
              type="primary"
              @click="nextQuestion"
            >
              下一题
            </el-button>
            <template v-else>
              <el-button
                v-if="canRetry"
                type="warning"
                @click="handleRetry"
              >
                重新答题
              </el-button>
              <el-button
                type="primary"
                @click="handleBackToList"
              >
                返回练习列表
              </el-button>
            </template>
          </template>
        </div>
      </div>

      <!-- ===== 结果展示 ===== -->
      <el-dialog
        v-model="resultVisible"
        title="答题结果"
        width="440px"
        style="max-width: 500px;"
        :close-on-click-modal="false"
        :show-close="false"
      >
        <div class="result-content">
          <div class="result-score" :class="resultPassed ? 'passed' : 'failed'">
            <div class="score-number">{{ submitResult.score }}</div>
            <div class="score-label">得分</div>
           <div class="score-total">满分 {{ submitResult.totalScore }}</div>
          </div>
          <div class="result-status">
            <el-tag
              :type="resultPassed ? 'success' : 'danger'"
              size="large"
              effect="dark"
            >
              {{ resultPassed ? '恭喜通过！' : '未通过' }}
            </el-tag>
          </div>
          <div class="result-detail">
            答对 {{ correctCount }} / {{ totalQuestions }} 题
          </div>
        </div>
        <template #footer>
          <el-button @click="resultVisible = false">查看解析</el-button>
          <el-button v-if="canRetry" type="warning" @click="handleRetry">重新答题</el-button>
          <el-button type="primary" @click="handleBackToList">返回练习列表</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, Close, Timer, Loading } from '@element-plus/icons-vue'
import { getExercises, getExerciseById, submitExerciseRecord } from '@/api/exercise'
import { getQuestionById } from '@/api/question'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ===== 练习列表视图 =====
const loading = ref(false)
const exerciseList = ref([])
const exerciseStarted = ref(false)
const submitting = ref(false)
const submitted = ref(false)

const chapterId = computed(() => route.params.chapterId)

// ===== 答题相关状态 =====
const currentExercise = ref(null)
const questions = ref([])        // 完整题目数据（含 answer/explanation）
const questionIds = ref([])      // 题目ID列表（用于顺序）
const currentIndex = ref(0)
const answers = reactive({})     // questionId → answer string | string[]
const multipleAnswers = reactive({}) // questionId → string[] (多选用)
const timeLeft = ref(0)
let timerInterval = null

const totalQuestions = computed(() => questionIds.value.length)
const progressPercent = computed(() => {
  if (totalQuestions.value === 0) return 0
  return Math.round(((currentIndex.value + 1) / totalQuestions.value) * 100)
})

const currentQuestion = computed(() => {
  const id = questionIds.value[currentIndex.value]
  return questions.value.find(q => q.id === id) || {}
})

const timeLimit = computed(() => currentExercise.value?.timeLimit || 0)
const maxAttempts = computed(() => currentExercise.value?.maxAttempts || 999)
const attemptNo = ref(1)

const canRetry = computed(() => attemptNo.value < maxAttempts.value)

// ===== 结果 =====
const resultVisible = ref(false)
const submitResult = ref({})
const resultPassed = computed(() => submitResult.value.passed)
const correctCount = computed(() =>
  (submitResult.value.answers || []).filter(a => a.isCorrect).length
)

// ===== 生命周期 =====
onMounted(async () => {
  await fetchExerciseList()
})

onUnmounted(() => {
  clearTimer()
})

// ===== API =====
async function fetchExerciseList() {
  if (!chapterId.value) return
  loading.value = true
  try {
    const { data } = await getExercises({ chapterId: chapterId.value })
    exerciseList.value = Array.isArray(data) ? data : (data?.items || [])
  } catch {
    ElMessage.error('获取练习列表失败')
  } finally {
    loading.value = false
  }
}

async function startExercise(exercise) {
  try {
    const { data } = await getExerciseById(exercise.id)
    currentExercise.value = data

    // 加载每个题目的完整内容
    const ids = data.questionIds || []
    questionIds.value = ids
    questions.value = []

    await Promise.allSettled(
      ids.map(id => loadQuestion(id))
    )

    // 重置答题状态
    attemptNo.value = (data.attemptNo || 0) + 1
    Object.keys(answers).forEach(k => delete answers[k])
    Object.keys(multipleAnswers).forEach(k => delete multipleAnswers[k])
    submitted.value = false
    exerciseStarted.value = true
    resultVisible.value = false

    // 启动计时器
    if (data.timeLimit) {
      timeLeft.value = data.timeLimit * 60
      startTimer()
    }
  } catch {
    ElMessage.error('加载练习详情失败')
  }
}

async function loadQuestion(id) {
  try {
    const { data } = await getQuestionById(id)
    // 解析 options JSON
    if (data.options && typeof data.options === 'string') {
      try {
        data.options = JSON.parse(data.options)
      } catch {
        data.options = []
      }
    }
    questions.value.push(data)
  } catch {
    // skip
  }
}

// ===== 计时器 =====
function startTimer() {
  clearTimer()
  timerInterval = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--
    } else {
      clearTimer()
      ElMessage.warning('时间到，自动提交！')
      doSubmit()
    }
  }, 1000)
}

function clearTimer() {
  if (timerInterval) {
    clearInterval(timerInterval)
    timerInterval = null
  }
}

function formatTimeLeft(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

// ===== 导航 =====
function prevQuestion() {
  if (currentIndex.value > 0) currentIndex.value--
}

function nextQuestion() {
  if (currentIndex.value < totalQuestions.value - 1) currentIndex.value++
}

// ===== 提交 =====
async function handleSubmit() {
  // 检查是否所有题都答了
  const unanswered = questionIds.value.filter(id => {
    const ans = answers[id]
    if (Array.isArray(ans)) return ans.length === 0
    return !ans
  })
  if (unanswered.length > 0 && currentIndex.value < totalQuestions.value - 1) {
    ElMessage.warning(`还有 ${unanswered.length} 题未作答，请检查`)
    return
  }
  await doSubmit()
}

async function doSubmit() {
  clearTimer()
  submitting.value = true
  submitted.value = true

  if (!currentExercise.value?.id) {
    ElMessage.error('练习信息缺失，请刷新重试')
    submitting.value = false
    submitted.value = false
    return
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('用户未登录，请重新登录')
    submitting.value = false
    submitted.value = false
    return
  }

  const duration = timeLimit.value ? timeLimit.value * 60 - timeLeft.value : 0

  const answerList = questionIds.value.map(qId => ({
    questionId: qId,
    answer: multipleAnswers[qId]
      ? JSON.stringify(multipleAnswers[qId].sort())
      : (answers[qId] || '')
  }))

  try {
    const { data } = await submitExerciseRecord({
      exerciseId: currentExercise.value.id,
      userId,
      answers: answerList,
      duration
    })
    submitResult.value = data
    resultVisible.value = true
  } catch {
    ElMessage.error('提交失败，请重试')
    submitted.value = false
  } finally {
    submitting.value = false
  }
}

// ===== 重做 =====
function handleRetry() {
  if (!canRetry.value) {
    ElMessage.warning('已达到最大答题次数')
    return
  }
  attemptNo.value++
  // 重置答案
  questionIds.value.forEach(id => {
    answers[id] = ''
    multipleAnswers[id] = []
  })
  submitted.value = false
  resultVisible.value = false
  currentIndex.value = 0

  // 重启计时器
  if (timeLimit.value) {
    timeLeft.value = timeLimit.value * 60
    startTimer()
  }
}

// ===== 返回列表 =====
function handleBackToList() {
  clearTimer()
  exerciseStarted.value = false
  resultVisible.value = false
  submitted.value = false
  currentIndex.value = 0
  currentExercise.value = null
  questions.value = []
  questionIds.value = []
}

// ===== 辅助方法 =====
// 判断当前题是否正确
const isCurrentCorrect = computed(() => {
  const q = currentQuestion.value
  const userAns = answers[q.id]
  if (q.questionType === 'MULTIPLE') {
    const userArr = multipleAnswers[q.id] || []
    const correctArr = parseMultipleAnswer(q.answer)
    return (
      userArr.length === correctArr.length &&
      userArr.every(v => correctArr.includes(v))
    )
  }
  return String(userAns || '') === String(q.answer)
})

function parseMultipleAnswer(answer) {
  if (!answer) return []
  try {
    return JSON.parse(answer)
  } catch {
    return []
  }
}

function isMultipleCorrect(value, answer) {
  const arr = parseMultipleAnswer(answer)
  return arr.includes(value)
}

function questionTypeLabel(type) {
  const map = { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', FILL: '填空题' }
  return map[type] || type
}

function questionTypeTagType(type) {
  const map = { SINGLE: 'primary', MULTIPLE: 'warning', JUDGE: 'info', FILL: 'success' }
  return map[type] || 'info'
}

function formatUserAnswer(q) {
  if (q.questionType === 'MULTIPLE') {
    const arr = multipleAnswers[q.id] || []
    return arr.length ? arr.join('、') : '未作答'
  }
  if (q.questionType === 'FILL') {
    return answers[q.id] || '未作答'
  }
  // SINGLE / JUDGE
  const opt = q.options?.find(o => o.value === answers[q.id])
  return opt ? `${opt.label}. ${opt.text}` : (answers[q.id] || '未作答')
}

function formatCorrectAnswer(q) {
  if (q.questionType === 'MULTIPLE') {
    const arr = parseMultipleAnswer(q.answer)
    return arr.map(v => {
      const opt = q.options?.find(o => o.value === v)
      return opt ? `${opt.label}. ${opt.text}` : v
    }).join('、') || q.answer
  }
  if (q.questionType === 'FILL') {
    return q.answer || '无'
  }
  const opt = q.options?.find(o => String(o.value) === String(q.answer))
  return opt ? `${opt.label}. ${opt.text}` : q.answer
}
</script>

<style scoped>
.exercise-take-page {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px 100px;
  min-height: 100vh;
}

/* ===== 练习列表 ===== */
.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 4px;
}

.page-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.loading-wrap,
.empty-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: #909399;
}

.loading-wrap .el-icon {
  font-size: 24px;
}

.exercise-cards {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.exercise-card {
  border-radius: 8px;
  transition: box-shadow 0.2s ease;
  cursor: pointer;
}

.exercise-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

:deep(.el-card__body) {
  padding: 0;
}

.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  gap: 16px;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.exercise-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.exercise-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.card-actions {
  flex-shrink: 0;
}

.card-actions .el-button {
  cursor: pointer;
}

/* ===== 答题界面 ===== */
.progress-bar-wrap {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #f5f5f5;
  border-bottom: 1px solid #f0f0f0;
  padding: 12px 0;
  margin-bottom: 20px;
}

.progress-inner {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.time-left {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  color: #e6a23c;
  white-space: nowrap;
}

:deep(.el-progress) {
  flex: 1;
}

/* 题目卡片 */
.question-card {
  border-radius: 8px;
  margin-bottom: 80px;
  transition: box-shadow 0.2s ease;
}

.question-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

:deep(.el-card__body) {
  padding: 20px 24px;
}

.question-type-bar {
  margin-bottom: 16px;
}

.question-content {
  margin-bottom: 20px;
}

.question-text {
  font-size: 16px;
  line-height: 1.8;
  color: #303133;
  margin: 0;
  white-space: pre-wrap;
}

.question-options {
  margin-top: 16px;
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.option-item {
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  transition: background 0.2s;
}

.option-item:hover {
  background: #f5f7fa;
}

.option-item.option-selected {
  background: #ecf5ff;
  border-color: #409eff;
}

.option-item.option-correct {
  background: #f0f9eb;
  border-color: #67c23a;
}

.option-item.option-wrong {
  background: #fef0f0;
  border-color: #f56c6c;
}

.option-label {
  font-weight: 600;
  margin-right: 4px;
}

.option-text {
  font-size: 15px;
  color: #303133;
}

.fill-input {
  margin-top: 4px;
}

/* 答案解析 */
.answer-analysis {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px dashed #e4e7ed;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.analysis-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 14px;
  line-height: 1.6;
}

.analysis-label {
  color: #909399;
  white-space: nowrap;
  min-width: 70px;
}

.analysis-value {
  color: #303133;
  display: flex;
  align-items: center;
  gap: 4px;
}

.text-success {
  color: #67c23a;
  font-weight: 600;
}

.text-danger {
  color: #f56c6c;
  font-weight: 600;
}

.explanation-text {
  color: #606266;
  white-space: pre-wrap;
}

/* 底部导航 */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #f5f5f5;
  border-top: 1px solid #e4e7ed;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  z-index: 100;
  padding: 12px 16px;
}

.bottom-nav .el-button {
  min-width: 100px;
  cursor: pointer;
}

.nav-center {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.nav-center .el-button {
  cursor: pointer;
}

/* 结果弹窗 */
.result-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 16px 0;
}

.result-score {
  text-align: center;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.result-score.passed {
  background: #f0f9eb;
  border: 3px solid #67c23a;
}

.result-score.failed {
  background: #fef0f0;
  border: 3px solid #f56c6c;
}

.score-number {
  font-size: 36px;
  font-weight: 700;
  color: inherit;
  line-height: 1;
}

.passed .score-number {
  color: #67c23a;
}

.failed .score-number {
  color: #f56c6c;
}

.score-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.score-total {
  font-size: 12px;
  color: #c0c4cc;
}

.result-status {
  font-size: 18px;
}

.result-detail {
  font-size: 14px;
  color: #606266;
}

/* All buttons cursor */
.exercise-take-page :deep(.el-button) {
  cursor: pointer;
}

/* 响应式 */
@media (max-width: 768px) {
  .exercise-take-page {
    padding: 16px 12px 100px;
  }

  .card-body {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-actions {
    width: 100%;
  }

  .card-actions .el-button {
    width: 100%;
  }

  .question-card :deep(.el-card__body) {
    padding: 16px;
  }

  .bottom-nav {
    padding: 10px 12px;
  }

  .bottom-nav .el-button {
    min-width: 80px;
  }
}
</style>