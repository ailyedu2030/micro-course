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

      <!-- Loading skeleton -->
      <div v-if="loading" class="skeleton-wrap">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="exerciseList.length === 0" class="empty-wrap">
        <el-empty description="本章节暂无练习" />
      </div>

      <!-- PC: 2-column card grid -->
      <div v-else-if="!isMobile" class="exercise-cards pc-grid">
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

      <!-- H5: single column -->
      <div v-else class="exercise-cards h5-list">
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

      <!-- ===== PC 答题布局 ===== -->
      <template v-if="!isMobile">
        <!-- 顶部进度条（sticky） -->
        <div class="progress-bar-wrap">
          <div class="progress-inner">
            <span class="progress-text">第 {{ currentIndex + 1 }} / {{ totalQuestions }} 题</span>
            <el-progress
              :percentage="progressPercent"
              :show-text="false"
              :stroke-width="6"
              :color="progressColor"
            />
            <span v-if="timeLimit" class="time-left">
              <el-icon><Timer /></el-icon>
              {{ formatTimeLeft(timeLeft) }}
            </span>
          </div>
        </div>

        <!-- 主体：左侧题目 + 右侧答题卡 -->
        <div class="answer-main pc-layout">
          <!-- 左侧：题目区 -->
          <div class="question-area">
            <el-card class="question-card" shadow="never">
              <div class="question-type-bar">
                <el-tag size="small" effect="plain" :type="questionTypeTagType(currentQuestion.questionType)">
                  {{ questionTypeLabel(currentQuestion.questionType) }}
                </el-tag>
              </div>
              <div class="question-content">
                <p class="question-text">{{ currentQuestion.content }}</p>
              </div>

              <!-- 选项区 -->
              <div class="question-options">
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
                        'option-selected': answers[currentQuestion.id] === opt.value && !submitted,
                      }"
                    >
                      <el-radio :value="opt.value">
                        <span class="option-label">{{ opt.label }}.</span>
                        <span class="option-text">{{ opt.text }}</span>
                      </el-radio>
                    </div>
                  </el-radio-group>
                </template>

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

              <!-- 答案解析 -->
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

            <!-- 底部导航 -->
            <div class="bottom-nav-pc">
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
          </div>

          <!-- 右侧：答题卡面板 -->
          <div class="answer-sheet-panel">
            <el-card class="answer-sheet-card" shadow="never">
              <template #header>
                <div class="answer-sheet-header">
                  <span class="answer-sheet-title">答题卡</span>
                  <span class="answer-sheet-count">
                    {{ answeredCount }} / {{ totalQuestions }}
                  </span>
                </div>
              </template>
              <div class="question-dots">
                <div
                  v-for="(qId, idx) in questionIds"
                  :key="qId"
                  class="q-dot"
                  :class="{
                    'dot-current': idx === currentIndex,
                    'dot-answered': isQuestionAnswered(qId),
                    'dot-correct': submitted && isQuestionCorrect(qId),
                    'dot-wrong': submitted && isQuestionWrong(qId),
                  }"
                  @click="jumpToQuestion(idx)"
                >
                  {{ idx + 1 }}
                </div>
              </div>
              <div class="dot-legend">
                <span class="legend-item"><span class="dot dot-answered"></span> 已答</span>
                <span class="legend-item"><span class="dot dot-current"></span> 当前</span>
                <span class="legend-item"><span class="dot dot-wrong"></span> 错误</span>
                <span class="legend-item"><span class="dot dot-correct"></span> 正确</span>
              </div>
            </el-card>
          </div>
        </div>
      </template>

      <!-- ===== H5 答题布局 ===== -->
      <template v-else>
        <!-- 紧凑进度 -->
        <div class="h5-progress-bar">
          <div class="h5-progress-inner">
            <span class="h5-progress-text">{{ currentIndex + 1 }} / {{ totalQuestions }}</span>
            <el-progress
              :percentage="progressPercent"
              :show-text="false"
              :stroke-width="4"
              :color="progressColor"
              class="h5-progress"
            />
            <span v-if="timeLimit" class="time-left">
              <el-icon><Timer /></el-icon>
              {{ formatTimeLeft(timeLeft) }}
            </span>
          </div>
        </div>

        <!-- 全屏题目卡片 -->
        <div class="h5-question-wrap">
          <el-card class="question-card" shadow="never">
            <div class="question-type-bar">
              <el-tag size="small" effect="plain" :type="questionTypeTagType(currentQuestion.questionType)">
                {{ questionTypeLabel(currentQuestion.questionType) }}
              </el-tag>
            </div>
            <div class="question-content">
              <p class="question-text">{{ currentQuestion.content }}</p>
            </div>

            <div class="question-options">
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
                      'option-selected': answers[currentQuestion.id] === opt.value && !submitted,
                    }"
                  >
                    <el-radio :value="opt.value">
                      <span class="option-label">{{ opt.label }}.</span>
                      <span class="option-text">{{ opt.text }}</span>
                    </el-radio>
                  </div>
                </el-radio-group>
              </template>

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

        <!-- H5 底部 prev/next 导航 -->
        <div class="h5-bottom-nav">
          <el-button
            class="h5-nav-btn"
            @click="prevQuestion"
            :disabled="currentIndex === 0"
          >
            上一题
          </el-button>
          <template v-if="!submitted">
            <el-button
              v-if="currentIndex < totalQuestions - 1"
              type="primary"
              class="h5-nav-btn"
              @click="nextQuestion"
            >
              下一题
            </el-button>
            <el-button
              v-else
              type="success"
              class="h5-nav-btn"
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
              class="h5-nav-btn"
              @click="nextQuestion"
            >
              下一题
            </el-button>
            <template v-else>
              <el-button
                v-if="canRetry"
                type="warning"
                class="h5-nav-btn"
                @click="handleRetry"
              >
                重新答题
              </el-button>
              <el-button
                type="primary"
                class="h5-nav-btn"
                @click="handleBackToList"
              >
                返回练习列表
              </el-button>
            </template>
          </template>
        </div>
      </template>

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

// ===== 响应式布局 =====
const isMobile = ref(window.innerWidth <= 768)
const progressColor = 'var(--role-primary)'

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

// ===== 答题卡辅助 =====
const answeredCount = computed(() => {
  return questionIds.value.filter(id => {
    const ans = answers[id]
    if (Array.isArray(ans)) return ans.length > 0
    return !!ans
  }).length
})

function isQuestionAnswered(qId) {
  const ans = answers[qId]
  if (Array.isArray(ans)) return ans.length > 0
  return !!ans
}

function isQuestionCorrect(qId) {
  const q = questions.value.find(q => q.id === qId)
  if (!q) return false
  if (q.questionType === 'MULTIPLE') {
    const userArr = multipleAnswers[qId] || []
    const correctArr = parseMultipleAnswer(q.answer)
    return userArr.length === correctArr.length && userArr.every(v => correctArr.includes(v))
  }
  return String(answers[qId] || '') === String(q.answer)
}

function isQuestionWrong(qId) {
  const q = questions.value.find(q => q.id === qId)
  if (!q) return false
  if (q.questionType === 'MULTIPLE') {
    const userArr = multipleAnswers[qId] || []
    const correctArr = parseMultipleAnswer(q.answer)
    return !(userArr.length === correctArr.length && userArr.every(v => correctArr.includes(v)))
  }
  return String(answers[qId] || '') !== String(q.answer)
}

function jumpToQuestion(idx) {
  currentIndex.value = idx
}

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
    const ids = (data.questions || []).map(q => q.questionId)
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
  } catch (e) {
    console.warn('[ExerciseTake] loadQuestion failed id=', id, e)
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
  if (submitting.value) return // 防重复提交
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
      duration,
      attemptNo: attemptNo.value
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
/* ===== 全局基础 ===== */
.exercise-take-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px 100px;
  min-height: 100vh;
}

/* ===== 练习列表 ===== */
.page-header {
  margin-bottom: var(--space-6);
}

.page-title {
  font-size: var(--text-2xl, 22px);
  font-weight: var(--weight-bold, 700);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-1);
}

.page-subtitle {
  font-size: var(--text-sm, 14px);
  color: var(--el-text-color-secondary);
  margin: 0;
}

.skeleton-wrap {
  padding: var(--space-2) 0;
}

.empty-wrap {
  padding: var(--space-8) 0;
  display: flex;
  justify-content: center;
}

/* PC 2-column grid */
.exercise-cards.pc-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4);
}

/* H5 single column */
.exercise-cards.h5-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.exercise-card {
  border-radius: var(--radius-lg, 12px);
  transition: transform var(--duration-base, 200ms) ease,
              box-shadow var(--duration-base, 200ms) ease;
  cursor: pointer;
}

.exercise-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg, 0 4px 16px rgba(0,0,0,0.1));
}

:deep(.el-card__body) {
  padding: 0;
}

.card-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  gap: var(--space-4);
}

.card-info {
  flex: 1;
  min-width: 0;
}

.exercise-title {
  font-size: var(--text-base, 16px);
  font-weight: var(--weight-semibold, 600);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.exercise-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.card-actions {
  flex-shrink: 0;
}

.card-actions .el-button {
  cursor: pointer;
}

/* ===== 答题界面 PC 布局 ===== */
.progress-bar-wrap {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--role-primary-light);
  border-bottom: 1px solid rgba(99, 102, 241, 0.1);
  padding: 12px 0;
  margin-bottom: var(--space-5);
}

.progress-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 0 var(--space-4);
}

.progress-text {
  font-size: var(--text-sm, 14px);
  font-weight: var(--weight-semibold, 600);
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.time-left {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm, 14px);
  color: var(--el-color-warning);
  white-space: nowrap;
}

:deep(.el-progress) {
  flex: 1;
}

/* PC 双栏布局 */
.answer-main.pc-layout {
  display: flex;
  gap: var(--space-5);
  align-items: flex-start;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-4);
}

.question-area {
  flex: 0 0 60%;
  min-width: 0;
}

.answer-sheet-panel {
  flex: 0 0 38%;
  position: sticky;
  top: 80px;
}

/* 题目卡片 */
.question-card {
  border-radius: var(--radius-lg, 12px);
  transition: box-shadow var(--duration-base, 200ms) ease;
}

.question-card:hover {
  box-shadow: var(--shadow-lg, 0 4px 16px rgba(0,0,0,0.1));
}

:deep(.el-card__body) {
  padding: var(--space-5) var(--space-6);
}

.question-type-bar {
  margin-bottom: var(--space-4);
}

.question-content {
  margin-bottom: var(--space-5);
}

.question-text {
  font-size: var(--text-base, 16px);
  line-height: 1.8;
  color: var(--el-text-color-primary);
  margin: 0;
  white-space: pre-wrap;
}

.question-options {
  margin-top: var(--space-4);
}

.option-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: 100%;
}

.option-item {
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-lg, 12px);
  border: 1px solid var(--el-border-color);
  transition: background var(--duration-base, 200ms) ease,
              border-color var(--duration-base, 200ms) ease,
              transform var(--duration-base, 200ms) ease;
}

.option-item:hover {
  background: var(--el-fill-color-light);
  transform: translateY(-1px);
}

.option-item.option-selected {
  background: var(--role-primary-light);
  border-color: var(--role-primary);
}

.option-item.option-correct {
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success);
}

.option-item.option-wrong {
  background: var(--el-color-danger-light-9);
  border-color: var(--el-color-danger);
}

.option-label {
  font-weight: var(--weight-semibold, 600);
  margin-right: var(--space-1);
}

.option-text {
  font-size: var(--text-base, 15px);
  color: var(--el-text-color-primary);
}

.fill-input {
  margin-top: var(--space-1);
}

/* 答案解析 */
.answer-analysis {
  margin-top: var(--space-6);
  padding-top: 20px;
  border-top: 1px dashed var(--el-border-color);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.analysis-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  font-size: var(--text-sm, 14px);
  line-height: var(--leading-relaxed);
}

.analysis-label {
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  min-width: 70px;
}

.analysis-value {
  color: var(--el-text-color-primary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.text-success {
  color: var(--el-color-success);
  font-weight: var(--weight-semibold, 600);
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: var(--weight-semibold, 600);
}

.explanation-text {
  color: var(--el-text-color-secondary);
  white-space: pre-wrap;
}

/* PC 底部导航 */
.bottom-nav-pc {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.bottom-nav-pc .el-button {
  cursor: pointer;
  min-width: 100px;
}

.nav-center {
  display: flex;
  gap: var(--space-3);
  justify-content: center;
}

.nav-center .el-button {
  cursor: pointer;
}

/* ===== 答题卡面板 ===== */
.answer-sheet-card {
  border-radius: var(--radius-lg, 12px);
}

:deep(.el-card__header) {
  padding: var(--space-3) var(--space-4);
}

.answer-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.answer-sheet-title {
  font-weight: var(--weight-semibold, 600);
  color: var(--el-text-color-primary);
  font-size: var(--text-base, 16px);
}

.answer-sheet-count {
  font-size: var(--text-sm, 14px);
  color: var(--el-text-color-secondary);
}

.question-dots {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

.q-dot {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  border: 2px solid var(--el-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-xs, 12px);
  font-weight: var(--weight-semibold, 600);
  cursor: pointer;
  transition: all var(--duration-base, 200ms) ease;
  background: var(--el-bg-color-overlay);
  color: var(--el-text-color-secondary);
}

.q-dot:hover {
  border-color: var(--role-primary);
  color: var(--role-primary);
}

.q-dot.dot-current {
  border-color: var(--role-primary);
  background: var(--role-primary-light);
  color: var(--role-primary);
}

.q-dot.dot-answered {
  border-color: var(--role-primary);
  background: var(--role-primary-light);
  color: var(--role-primary);
}

.q-dot.dot-correct {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.q-dot.dot-wrong {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.dot-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.legend-item {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
}

.legend-item .dot {
  width: 12px;
  height: 12px;
  border-radius: var(--radius-sm);
  border: 2px solid;
}

.legend-item .dot.dot-answered {
  border-color: var(--role-primary);
  background: var(--role-primary-light);
}

.legend-item .dot.dot-current {
  border-color: var(--role-primary);
  background: var(--role-primary-light);
}

.legend-item .dot.dot-wrong {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.legend-item .dot.dot-correct {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

/* ===== H5 布局 ===== */
.h5-progress-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--role-primary-light);
  padding: var(--space-3) var(--space-3);
  margin-bottom: var(--space-3);
}

.h5-progress-inner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.h5-progress-text {
  font-size: var(--text-xs, 12px);
  font-weight: var(--weight-semibold, 600);
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.h5-progress {
  flex: 1;
}

.h5-question-wrap {
  padding: 0 var(--space-3);
  margin-bottom: 80px;
}

.h5-question-wrap .question-card:hover {
  box-shadow: var(--shadow-lg, 0 4px 16px rgba(0,0,0,0.1));
}

.h5-question-wrap :deep(.el-card__body) {
  padding: var(--space-4);
}

.h5-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--role-primary-light);
  border-top: 1px solid rgba(99, 102, 241, 0.1);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  z-index: 100;
  padding: var(--space-3) var(--space-3);
  display: flex;
  gap: var(--space-3);
}

.h5-nav-btn {
  flex: 1;
  cursor: pointer;
}

/* ===== 结果弹窗 ===== */
.result-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-5);
  padding: 16px 0;
}

.result-score {
  text-align: center;
  width: 120px;
  height: 120px;
  border-radius: var(--radius-circle);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.result-score.passed {
  background: linear-gradient(135deg, var(--role-primary-light), var(--el-bg-color-overlay));
  border: 3px solid var(--role-primary);
}

.result-score.failed {
  background: var(--el-color-danger-light-9);
  border: 3px solid var(--el-color-danger);
}

.score-number {
  font-size: 36px;
  font-weight: var(--weight-bold, 700);
  line-height: 1;
}

.passed .score-number {
  color: var(--role-primary);
}

.failed .score-number {
  color: var(--el-color-danger);
}

.score-label {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
}

.score-total {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-placeholder);
}

.result-status {
  font-size: var(--text-lg, 18px);
}

.result-detail {
  font-size: var(--text-sm, 14px);
  color: var(--el-text-color-secondary);
}

/* ===== 全局按钮指针 ===== */
.exercise-take-page :deep(.el-button) {
  cursor: pointer;
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .exercise-take-page {
    padding: 16px 12px 100px;
  }

  .exercise-cards.pc-grid {
    grid-template-columns: 1fr;
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
}
</style>