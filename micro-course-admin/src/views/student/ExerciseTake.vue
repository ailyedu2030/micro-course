<!--
  章节练习
  路由路径: /student/chapters/:chapterId/exercises
  Phase 2
  Author: jackie
-->
<template>
  <div class="exercise-take-page">
    <!-- ===== 练习入口：章节练习列表 ===== -->
    <div v-if="!exerciseStarted" class="exercise-list-view">
      <div class="page-header">
        <h2 class="page-title">{{ $t('exerciseTake.title') }}</h2>
        <p class="page-subtitle">{{ $t('exerciseTake.count', { count: exerciseList.length }) }}</p>
      </div>

      <!-- Loading skeleton -->
      <div v-if="loading" class="skeleton-wrap">
        <el-skeleton :rows="5" animated />
      </div>

      <div v-else-if="exerciseList.length === 0" class="empty-wrap">
        <el-empty :description="$t('exerciseTake.empty')" />
      </div>

      <!-- PC: 2-column card grid -->
      <div v-else-if="!isMobile" class="exercise-cards pc-grid">
        <el-card
          v-for="ex in exerciseList"
          :key="ex.id"
          class="exercise-card student-card-item"
          shadow="never"
        >
          <div class="card-body">
            <div class="card-info">
              <h3 class="exercise-title">{{ ex.title }}</h3>
              <div class="exercise-meta">
                <el-tag v-if="ex.questionCount" size="small" effect="plain">
                  {{ $t('exerciseTake.questionCount', { count: ex.questionCount }) }}
                </el-tag>
                <el-tag v-if="ex.timeLimit" size="small" effect="plain" type="info">
                  {{ $t('exerciseTake.minutes', { count: ex.timeLimit }) }}
                </el-tag>
                <el-tag v-if="ex.passScore" size="small" effect="plain" type="warning">
                  {{ $t('exerciseTake.passScore', { score: ex.passScore }) }}
                </el-tag>
              </div>
            </div>
            <div class="card-actions">
              <el-button
                type="primary"
                size="default"
                @click="startExercise(ex)"
              >
                {{ $t('exerciseTake.start') }}
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
          class="exercise-card student-card-item"
          shadow="never"
        >
          <div class="card-body">
            <div class="card-info">
              <h3 class="exercise-title">{{ ex.title }}</h3>
              <div class="exercise-meta">
                <el-tag v-if="ex.questionCount" size="small" effect="plain">
                  {{ $t('exerciseTake.questionCount', { count: ex.questionCount }) }}
                </el-tag>
                <el-tag v-if="ex.timeLimit" size="small" effect="plain" type="info">
                  {{ $t('exerciseTake.minutes', { count: ex.timeLimit }) }}
                </el-tag>
                <el-tag v-if="ex.passScore" size="small" effect="plain" type="warning">
                  {{ $t('exerciseTake.passScore', { score: ex.passScore }) }}
                </el-tag>
              </div>
            </div>
            <div class="card-actions">
              <el-button
                type="primary"
                size="default"
                @click="startExercise(ex)"
              >
                {{ $t('exerciseTake.start') }}
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
            <span class="progress-text" aria-live="polite">{{ $t('exerciseTake.progress', { current: currentIndex + 1, total: totalQuestions, answered: answeredCount }) }}</span>
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
            <span class="time-elapsed">
              <el-icon><Timer /></el-icon>
              {{ $t('exerciseTake.elapsed', { time: formatTimeLeft(elapsedTime) }) }}
            </span>
          </div>
        </div>

        <!-- 主体：左侧题目 + 右侧答题卡 -->
        <div class="answer-main pc-layout">
          <!-- 左侧：题目区 -->
          <div class="question-area">
            <el-card v-if="questionsLoading" class="question-card" shadow="never">
              <el-skeleton :rows="8" animated />
            </el-card>
            <el-card v-else class="question-card" shadow="never" :data-question-index="currentIndex">
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

                <template v-else-if="currentQuestion.questionType === 'FILL' || currentQuestion.questionType === 'SHORT_ANSWER'">
                  <el-input
                    v-model="answers[currentQuestion.id]"
                    type="textarea"
                    :rows="3"
                    :disabled="submitted"
                    :placeholder="$t('exerciseTake.answerPlaceholder')"
                    class="fill-input"
                  />
                </template>
                <template v-else-if="currentQuestion.questionType === 'ESSAY'">
                  <el-input
                    v-model="answers[currentQuestion.id]"
                    type="textarea"
                    :rows="5"
                    :disabled="submitted"
                    :placeholder="$t('exerciseTake.solutionPlaceholder')"
                    class="fill-input"
                  />
                </template>
              </div>

              <!-- 答案解析 -->
              <div v-if="submitted" class="answer-analysis">
                <div class="analysis-row user-answer">
                  <span class="analysis-label">{{ $t('exerciseTake.yourAnswer') }}</span>
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
                  <span class="analysis-label">{{ $t('exerciseTake.correctAnswer') }}</span>
                  <span class="analysis-value text-success">
                    <el-icon><Check /></el-icon>
                    {{ formatCorrectAnswer(currentQuestion) }}
                  </span>
                </div>
                <div v-if="currentQuestion.explanation" class="analysis-row explanation">
                  <span class="analysis-label">{{ $t('exerciseTake.explanation') }}</span>
                  <span class="analysis-value explanation-text">{{ currentQuestion.explanation }}</span>
                </div>
              </div>
            </el-card>

            <!-- 底部导航 -->
            <div class="bottom-nav-pc">
              <el-button @click="prevQuestion" :disabled="currentIndex === 0">
                {{ $t('exerciseTake.prev') }}
              </el-button>
              <div class="nav-center">
                <template v-if="!submitted">
                  <el-button
                    v-if="currentIndex < totalQuestions - 1"
                    type="primary"
                    @click="nextQuestion"
                  >
                    {{ $t('exerciseTake.next') }}
                  </el-button>
                  <el-button
                    v-else
                    type="success"
                    :loading="submitting"
                    :disabled="submitting"
                    @click="handleSubmit"
                  >
                    {{ $t('exerciseTake.submit') }}
                  </el-button>
                </template>
                <template v-else>
                  <!-- 提交失败重试按钮 -->
                  <el-button
                    v-if="submitError"
                    type="danger"
                    @click="handleRetrySubmit"
                  >
                    {{ $t('exerciseTake.retrySubmit') }}
                  </el-button>
                  <el-button
                    v-if="currentIndex < totalQuestions - 1"
                    type="primary"
                    @click="nextQuestion"
                  >
                    {{ $t('exerciseTake.next') }}
                  </el-button>
                  <template v-else>
                    <el-button
                      v-if="canRetry"
                      type="warning"
                      @click="handleRetry"
                    >
                      {{ $t('exerciseTake.retry') }}
                    </el-button>
                    <el-button
                      type="primary"
                      @click="handleBackToList"
                    >
                      {{ $t('exerciseTake.backToList') }}
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
                  <span class="answer-sheet-title">{{ $t('exerciseTake.answerSheet') }}</span>
                  <span class="answer-sheet-count">
                    {{ answeredCount }} / {{ totalQuestions }}
                  </span>
                </div>
              </template>
              <div class="question-dots">
                <button
                  v-for="(qId, idx) in questionIds"
                  :key="qId"
                  type="button"
                  class="q-dot"
                  :class="{
                    'dot-current': idx === currentIndex,
                    'dot-answered': isQuestionAnswered(qId),
                    'dot-correct': submitted && isQuestionCorrect(qId),
                    'dot-wrong': submitted && isQuestionWrong(qId),
                  }"
                  :aria-label="$t('exerciseTake.jumpTo', { n: idx + 1 })"
                  @click="jumpToQuestion(idx)"
                >
                  {{ idx + 1 }}
                </button>
              </div>
              <div class="dot-legend">
                <span class="legend-item"><span class="dot dot-answered"></span> {{ $t('exerciseTake.answered') }}</span>
                <span class="legend-item"><span class="dot dot-current"></span> {{ $t('exerciseTake.current') }}</span>
                <span class="legend-item"><span class="dot dot-wrong"></span> {{ $t('exerciseTake.wrong') }}</span>
                <span class="legend-item"><span class="dot dot-correct"></span> {{ $t('exerciseTake.correct') }}</span>
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
            <span class="h5-progress-text" aria-live="polite">{{ $t('exerciseTake.progressH5', { current: currentIndex + 1, total: totalQuestions, answered: answeredCount }) }}</span>
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
            <span class="time-elapsed">
              <el-icon><Timer /></el-icon>
              {{ $t('exerciseTake.elapsed', { time: formatTimeLeft(elapsedTime) }) }}
            </span>
          </div>
        </div>

        <!-- 全屏题目卡片 -->
        <div class="h5-question-wrap">
          <el-card v-if="questionsLoading" class="question-card" shadow="never">
            <el-skeleton :rows="8" animated />
          </el-card>
          <el-card v-else class="question-card" shadow="never" :data-question-index="currentIndex">
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

              <template v-else-if="currentQuestion.questionType === 'FILL' || currentQuestion.questionType === 'SHORT_ANSWER'">
                <el-input
                  v-model="answers[currentQuestion.id]"
                  type="textarea"
                  :rows="3"
                  :disabled="submitted"
                  :placeholder="$t('exerciseTake.answerPlaceholder')"
                  class="fill-input"
                />
              </template>
              <template v-else-if="currentQuestion.questionType === 'ESSAY'">
                <el-input
                  v-model="answers[currentQuestion.id]"
                  type="textarea"
                  :rows="5"
                  :disabled="submitted"
                  :placeholder="$t('exerciseTake.solutionPlaceholder')"
                  class="fill-input"
                />
              </template>
            </div>

            <div v-if="submitted" class="answer-analysis">
              <div class="analysis-row user-answer">
                <span class="analysis-label">{{ $t('exerciseTake.yourAnswer') }}</span>
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
                <span class="analysis-label">{{ $t('exerciseTake.correctAnswer') }}</span>
                <span class="analysis-value text-success">
                  <el-icon><Check /></el-icon>
                  {{ formatCorrectAnswer(currentQuestion) }}
                </span>
              </div>
              <div v-if="currentQuestion.explanation" class="analysis-row explanation">
                <span class="analysis-label">{{ $t('exerciseTake.explanation') }}</span>
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
            {{ $t('exerciseTake.prev') }}
          </el-button>
          <template v-if="!submitted">
            <el-button
              v-if="currentIndex < totalQuestions - 1"
              type="primary"
              class="h5-nav-btn"
              @click="nextQuestion"
            >
              {{ $t('exerciseTake.next') }}
            </el-button>
            <el-button
              v-else
              type="success"
              class="h5-nav-btn"
              :loading="submitting"
              :disabled="submitting"
              @click="handleSubmit"
            >
              {{ $t('exerciseTake.submit') }}
            </el-button>
          </template>
          <template v-else>
            <!-- 提交失败重试按钮 -->
            <el-button
              v-if="submitError"
              type="danger"
              class="h5-nav-btn"
              @click="handleRetrySubmit"
            >
              {{ $t('exerciseTake.retrySubmit') }}
            </el-button>
            <el-button
              v-if="currentIndex < totalQuestions - 1"
              type="primary"
              class="h5-nav-btn"
              @click="nextQuestion"
            >
              {{ $t('exerciseTake.next') }}
            </el-button>
            <template v-else>
              <el-button
                v-if="canRetry"
                type="warning"
                class="h5-nav-btn"
                @click="handleRetry"
              >
                {{ $t('exerciseTake.retry') }}
              </el-button>
              <el-button
                type="primary"
                class="h5-nav-btn"
                @click="handleBackToList"
              >
                {{ $t('exerciseTake.backToList') }}
              </el-button>
            </template>
          </template>
        </div>

        <!-- 浮动答题卡（移动端） -->
        <teleport to="body">
          <button
            type="button"
            class="answer-sheet-fab"
            :aria-label="$t('exerciseTake.openSheet')"
            :aria-expanded="String(sheetVisible)"
            @click="toggleAnswerSheet"
          >
            <el-icon><Grid /></el-icon>
            <span>{{ $t('exerciseTake.answerSheet') }}</span>
          </button>
          <transition name="fade">
            <div v-show="sheetVisible" class="answer-sheet-overlay" @click="closeAnswerSheet"></div>
          </transition>
          <transition name="slide-up">
            <div v-show="sheetVisible" class="answer-sheet-panel" role="dialog" aria-modal="true" :aria-label="$t('exerciseTake.sheetAria')">
              <div class="answer-sheet-header">
                <span>{{ $t('exerciseTake.answerSheet') }}</span>
                <span class="sheet-progress">{{ answeredCount }}/{{ totalQuestions }}</span>
                <el-button text :aria-label="$t('exerciseTake.closeSheet')" @click="closeAnswerSheet">{{ $t('common.close') }}</el-button>
              </div>
              <div class="answer-sheet-grid">
                <button
                  v-for="(q, idx) in questions"
                  :key="q.id"
                  type="button"
                  class="answer-sheet-item"
                  :class="{ answered: answers[q.id], current: currentIndex === idx }"
                  :aria-label="$t('exerciseTake.jumpTo', { n: idx + 1 })"
                  @click="jumpToQuestion(idx); closeAnswerSheet()"
                >
                  {{ idx + 1 }}
                </button>
              </div>
            </div>
          </transition>
        </teleport>
      </template>

      <!-- ===== 结果展示 ===== -->
      <el-dialog
        v-model="resultVisible"
        :title="$t('exerciseTake.resultTitle')"
        width="440px"
        style="max-width: 500px;"
        :close-on-click-modal="false"
        :close-on-press-escape="true"
>
        <div class="result-content">
          <div class="result-score" :class="resultPassed ? 'passed' : 'failed'">
            <div class="score-number">{{ submitResult.needsManualGrading && submitResult.score === 0 ? $t('exerciseTake.pendingGrading') : submitResult.score }}</div>
            <div class="score-label">{{ $t('exerciseTake.score') }}</div>
            <div class="score-total">{{ $t('exerciseTake.totalScore', { score: submitResult.totalScore }) }}</div>
          </div>
          <div class="result-status">
            <el-tag
              :type="resultPassed ? 'success' : 'danger'"
              size="large"
              effect="dark"
            >
              {{ resultPassed ? $t('exerciseTake.passed') : $t('exerciseTake.failed') }}
            </el-tag>
          </div>
          <div class="result-detail">
            {{ $t('exerciseTake.correctCount', { count: correctCount, total: totalQuestions }) }}
          </div>
          <div v-if="submitResult.needsManualGrading" class="result-manual-hint">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ $t('exerciseTake.manualGradingHint') }}</span>
          </div>
        </div>
        <template #footer>
          <el-button @click="handleViewAnalysis">{{ $t('exerciseTake.viewAnalysis') }}</el-button>
          <el-button v-if="canRetry" type="warning" @click="handleRetry">{{ $t('exerciseTake.retry') }}</el-button>
          <el-button type="primary" @click="handleBackToList">{{ $t('exerciseTake.backToList') }}</el-button>
        </template>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Grid, Timer, Loading, WarningFilled } from '@element-plus/icons-vue'
import { getExercises, getExerciseById, submitExerciseRecord } from '@/api/exercise'
import { getMyAttemptCount } from '@/api/exercise-record'
import { useUserStore } from '@/store/user'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ===== 响应式布局 =====
const isMobile = ref(window.innerWidth <= 768)
const progressColor = 'var(--role-primary)'

function handleResize() {
  isMobile.value = window.innerWidth <= 768
}

// ===== 练习列表视图 =====
const loading = ref(false)
const questionsLoading = ref(false)
const exerciseList = ref([])
const exerciseStarted = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const submitError = ref(false)
const sheetVisible = ref(false)

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
let autoStartTimer = null
const elapsedTime = ref(0)
let elapsedTimerInterval = null

const totalQuestions = computed(() => questionIds.value.length)
const progressPercent = computed(() => {
  if (totalQuestions.value === 0) return 0
  return Math.round((answeredCount.value / totalQuestions.value) * 100)
})

const currentQuestion = computed(() => {
  const id = questionIds.value[currentIndex.value]
  return questions.value.find(q => q.id === id) || {}
})

const timeLimit = computed(() => currentExercise.value?.timeLimit || 0)
const maxAttempts = computed(() => currentExercise.value?.maxAttempts || 999)
const attemptNo = ref(1)

// P1-C: 超时倒计时警告标记
let warned60 = false
let warned30 = false
let warned10 = false

const canRetry = computed(() => attemptNo.value < maxAttempts.value)

// ===== 答题卡辅助 =====
const answeredCount = computed(() => {
  return questionIds.value.filter(id => isQuestionAnswered(id)).length
})

function isQuestionAnswered(qId) {
  const q = questions.value.find(q => q.id === qId)
  if (q && q.questionType === 'MULTIPLE') {
    const arr = multipleAnswers[qId]
    return Array.isArray(arr) && arr.length > 0
  }
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
  nextTick(() => {
    const el = document.querySelector('.question-card')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  })
}

function toggleAnswerSheet() {
  sheetVisible.value = !sheetVisible.value
}

function closeAnswerSheet() {
  sheetVisible.value = false
}

// ===== 结果 =====
const resultVisible = ref(false)
const submitResult = ref({})
const resultPassed = computed(() => submitResult.value.passed)
// 2026-08-04 修复：后端 ExerciseRecordVO.answers 为 JSON 字符串而非数组，
// 原 (answers || []).filter 直接抛 "filter is not a function" → 答题结果弹窗崩溃。
const resultAnswers = computed(() => {
  const raw = submitResult.value.answers
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try { return JSON.parse(raw) || [] } catch { return [] }
  }
  return []
})
const correctCount = computed(() =>
  resultAnswers.value.filter(a => a.isCorrect).length
)

// ===== 生命周期 =====
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  window.addEventListener('keydown', handleKeydown)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  await fetchExerciseList()
})

onUnmounted(() => {
  clearTimer()
  clearElapsedTimer()
  if (autoStartTimer) { clearTimeout(autoStartTimer); autoStartTimer = null }
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

// ===== 键盘导航（无障碍）=====
// 答题界面下方向键切换上/下一题。焦点在选项组/输入框内时不拦截，
// 让 el-radio-group / el-checkbox-group 的原生方向键行为优先。
function handleKeydown(e) {
  if (sheetVisible.value && e.key === 'Escape') {
    e.preventDefault()
    closeAnswerSheet()
    return
  }
  if (!exerciseStarted.value) return
  const t = e.target
  if (
    t &&
    (t.closest?.('.el-radio-group') ||
      t.closest?.('.el-checkbox-group') ||
      ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName))
  ) {
    return
  }
  if (e.key === 'ArrowLeft') {
    e.preventDefault()
    prevQuestion()
  } else if (e.key === 'ArrowRight') {
    e.preventDefault()
    nextQuestion()
  }
}

// ===== API =====
async function fetchExerciseList() {
  if (!chapterId.value) return
  loading.value = true
  try {
    const { data } = await getExercises({ chapterId: chapterId.value })
    exerciseList.value = Array.isArray(data) ? data : (data?.items || [])

    // P0-1: 如果路由 query 包含 examId，自动开始考试（从考试列表导航过来）
    const autoExamId = route.query.examId
    if (autoExamId) {
      const autoExam = exerciseList.value.find(
        ex => String(ex.id) === String(autoExamId) || String(ex.exerciseId) === String(autoExamId)
      )
      if (autoExam) {
        // 延时确保组件渲染完成后自动开始
        autoStartTimer = setTimeout(() => startExercise(autoExam), 300)
      }
    }
  } catch {
    ElMessage.error(t('exercise.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

async function startExercise(exercise) {
  try {
    const { data } = await getExerciseById(exercise.id)
    currentExercise.value = data
    // A11Y-030: 设置页面标题
    document.title = (data.title || t('exerciseTake.title')) + t('exerciseTake.titleSuffix')

    // 题目内容直接取自练习响应内嵌数据（R14 已内嵌完整题目：questionType/content/options/answer/explanation）。
    // P0 修复：此前逐题调用 getQuestionById（教师端接口）→ 学生 403「题目加载失败」，随堂练习不可用。
    // 2026-08-04 修复：后端兼容遗留类型别名（SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE/
    // FILL_BLANK/COMPREHENSIVE），未归一化时选项区按 'SINGLE' 等标准类型匹配 → 选项空白。
    const normalizeType = (raw) => {
      const map = {
        SINGLE_CHOICE: 'SINGLE',
        MULTIPLE_CHOICE: 'MULTIPLE',
        TRUE_FALSE: 'JUDGE',
        FILL_BLANK: 'FILL',
        SHORT_ANSWER: 'SHORT_ANSWER',
        COMPREHENSIVE: 'ESSAY'
      }
      return map[raw] || raw
    }
    // 选项归一化 → 统一 { value, label, text } 结构（答题端模板依赖这三个字段）
    // 兼容三种数据格式：
    //   1. 字符串数组 ["0","1"]（历史/导入数据）
    //   2. 编辑端对象 [{ label: '2', correct: true }]（QuestionList.vue 提交格式，
    //      label 即选项内容，answer 存正确 label）
    //   3. 完整对象 [{ value, label, text }]
    const normalizeOptions = (raw) => {
      let arr = raw
      if (typeof raw === 'string') {
        try { arr = JSON.parse(raw) } catch { return [] }
      }
      if (!Array.isArray(arr)) return []
      return arr.map((opt, i) => {
        const letter = String.fromCharCode(65 + i)
        if (typeof opt === 'string') {
          return { value: opt, label: letter, text: opt }
        }
        if (opt && typeof opt === 'object') {
          if (opt.value !== undefined || opt.text !== undefined) {
            return {
              value: String(opt.value),
              label: opt.label !== undefined ? String(opt.label) : letter,
              text: opt.text !== undefined ? String(opt.text) : ''
            }
          }
          // QuestionList 编辑端格式：{ label: 选项内容, correct: boolean }
          const content = opt.label !== undefined ? String(opt.label) : ''
          return { value: content, label: letter, text: content }
        }
        return { value: String(opt), label: letter, text: String(opt) }
      })
    }
    // P1-C 修复：判断题 options 缺失（旧数据/导入空选项）时兜底注入"正确/错误"，
    // 否则选项区循环渲染为空、学生无法作答
    const embedded = (data.questions || []).map(q => {
      const questionType = normalizeType(q.questionType)
      let options = normalizeOptions(q.options)
      if ((questionType === 'JUDGE' || questionType === 'TRUE_FALSE') && options.length === 0) {
        options = [
          { value: 'true', label: 'A', text: t('exerciseTake.correct') },
          { value: 'false', label: 'B', text: t('exerciseTake.wrong') }
        ]
      }
      return {
        id: q.questionId,
        questionId: q.questionId,
        score: q.score,
        questionType,
        content: q.content,
        options,
        answer: q.answer,
        explanation: q.explanation
      }
    })
    questionIds.value = embedded.map(q => q.questionId)
    questions.value = embedded
    questionsLoading.value = false

    // 从后端获取真实的答题次数（防止刷新页面绕过限制）
    try {
      const { data: attemptData } = await getMyAttemptCount(exercise.id)
      attemptNo.value = (attemptData?.attemptCount || 0) + 1
    } catch {
      attemptNo.value = 1
    }

    // 重置答题状态
    Object.keys(answers).forEach(k => delete answers[k])
    Object.keys(multipleAnswers).forEach(k => delete multipleAnswers[k])
    submitted.value = false
    exerciseStarted.value = true
    resultVisible.value = false
    currentIndex.value = 0

    // 启动计时器
    if (data.timeLimit) {
      timeLeft.value = data.timeLimit * 60
      startTimer()
    }

    // 启动答题用时计时器
    startElapsedTimer()
  } catch {
    ElMessage.error(t('exerciseTake.loadDetailFailed'))
  }
}

// ===== 计时器（P1 修复: 使用 performance.now() 防止页面休眠/切换时漂移）=====
const timerStartAt = ref(0)       // performance.now() 基准
const elapsedStartAt = ref(0)     // 已用计时器基准

function startTimer() {
  clearTimer()
  timerStartAt.value = performance.now()
  // P1-C: 每次启动计时器时重置倒计时警告标记
  warned60 = false
  warned30 = false
  warned10 = false
  timerInterval = setInterval(() => {
    // 使用 performance.now() 计算实际经过秒数，避免 setInterval 漂移
    const elapsed = Math.floor((performance.now() - timerStartAt.value) / 1000)
    const totalSeconds = currentExercise.value?.timeLimit ? currentExercise.value.timeLimit * 60 : 0
    const remaining = Math.max(0, totalSeconds - elapsed)
    timeLeft.value = remaining
    // P1-C: 超时倒计时警告
    if (remaining <= 60 && remaining > 58 && !warned60) { warned60 = true; ElMessage.warning(t('exerciseTake.timeWarn', { seconds: 60 })) }
    if (remaining <= 30 && remaining > 28 && !warned30) { warned30 = true; ElMessage.warning(t('exerciseTake.timeWarn', { seconds: 30 })) }
    if (remaining <= 10 && remaining > 8 && !warned10) { warned10 = true; ElMessage.warning(t('exerciseTake.timeWarn', { seconds: 10 })) }
    if (remaining <= 0) {
      clearTimer()
      // P1I-087: 超时提交前先序列化当前题目答案（确保 textarea 等 v-model 可能延迟刷新的场景不丢失)
      const curQ = currentQuestion.value
      if (curQ) {
        if (curQ.questionType === 'MULTIPLE') {
          // 多选题: 确保 multipleAnswers 已记录当前选中项
          if (!multipleAnswers[curQ.id]) multipleAnswers[curQ.id] = []
        } else if (answers[curQ.id] === undefined) {
          // 单选题/判断题/填空题: 确保 answers 已有初始值
          answers[curQ.id] = answers[curQ.id] || ''
        }
      }
      ElMessage.warning(t('exerciseTake.timeUp'))
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
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  return `${m}:${String(s).padStart(2, '0')}`
}

function startElapsedTimer() {
  clearElapsedTimer()
  elapsedTime.value = 0
  elapsedStartAt.value = performance.now()
  elapsedTimerInterval = setInterval(() => {
    // 使用 performance.now() 计算实际经过秒数
    elapsedTime.value = Math.floor((performance.now() - elapsedStartAt.value) / 1000)
  }, 1000)
}

function clearElapsedTimer() {
  if (elapsedTimerInterval) {
    clearInterval(elapsedTimerInterval)
    elapsedTimerInterval = null
  }
}

/** P1 修复: 页面可见性变化时校准计时器（页面切换/休眠回来后修正漂移） */
function handleVisibilityChange() {
  if (document.hidden) return
  // 页面恢复可见：计时器中的 setInterval 会在下一个 tick 自动通过 performance.now() 校准
  // 无需额外操作，因为计时器每次 tick 都基于 performance.now() 计算真实经过时间
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
  // 检查是否所有题都答了（包括多选题）
  const unanswered = questionIds.value.filter(id => !isQuestionAnswered(id))
  if (unanswered.length > 0) {
    try {
      await ElMessageBox.confirm(
        t('exerciseTake.confirmUnanswered', { count: unanswered.length }),
        t('exerciseTake.unfinishedTitle'),
        { confirmButtonText: t('exerciseTake.confirmSubmit'), cancelButtonText: t('exerciseTake.continueAnswering'), type: 'warning' }
      )
    } catch {
      // 用户选择继续答题 → 跳转到第一个未答题目
      const firstUnansweredIdx = questionIds.value.findIndex(id => !isQuestionAnswered(id))
      if (firstUnansweredIdx >= 0) currentIndex.value = firstUnansweredIdx
      return
    }
  }
  // 检查多选题是否完整作答（仅选1项可能不完整）
  const partialMultiples = questionIds.value.filter(id => {
    const q = questions.value.find(q => q.id === id)
    if (!q || q.questionType !== 'MULTIPLE') return false
    const arr = multipleAnswers[id]
    return Array.isArray(arr) && arr.length === 1
  })
  if (partialMultiples.length > 0) {
    try {
      await ElMessageBox.confirm(
        t('exerciseTake.confirmPartialMulti', { count: partialMultiples.length }),
        t('exerciseTake.multipleTitle'),
        { confirmButtonText: t('exerciseTake.confirmSubmit'), cancelButtonText: t('exerciseTake.continueAnswering'), type: 'warning' }
      )
    } catch {
      const firstPartialIdx = questionIds.value.findIndex(id => partialMultiples.includes(id))
      if (firstPartialIdx >= 0) currentIndex.value = firstPartialIdx
      return
    }
  }
  // P1-UX: 提交锁设置在 confirm 回调全部通过之后，避免 confirm 期间按钮锁定
  if (submitting.value) return // 幂等守卫
  // submitting 标志由 doSubmit 内部管理（doSubmit 同时被超时回调直接调用，
  // 其入口幂等守卫 + 提交锁可防止手动提交与超时提交并发双提交）
  await doSubmit()
}

async function doSubmit() {
  // 幂等守卫：手动提交与超时自动提交可能同时触发（timer 到期直接调用 doSubmit），
  // 防止双提交产生重复答题记录
  if (submitting.value) return
  if (!currentExercise.value?.id) {
    ElMessage.error(t('exerciseTake.missingInfo'))
    submitting.value = false
    return
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error(t('exerciseTake.notLoggedIn'))
    submitting.value = false
    return
  }

  const duration = elapsedTime.value || (timeLimit.value ? timeLimit.value * 60 - timeLeft.value : 0)

  const answerList = questionIds.value.map(qId => ({
    questionId: qId,
    answer: multipleAnswers[qId]
      ? JSON.stringify(multipleAnswers[qId].sort())
      : (answers[qId] || '')
  }))

  submitting.value = true
  try {
    const { data } = await submitExerciseRecord({
      exerciseId: currentExercise.value.id,
      answers: answerList,
      duration,
      attemptNo: attemptNo.value
    })
    // API 成功后才停止计时器、标记已提交
    clearTimer()
    clearElapsedTimer()
    submitted.value = true
    submitResult.value = data
    resultVisible.value = true
    if (data.needsManualGrading) {
      ElMessage.info(t('exerciseTake.manualGradingHint'))
    }
  } catch {
    submitError.value = true
    ElMessage.error(t('exerciseTake.submitFailed'))
    // submitted 保持 false，计时器继续运行，用户可重试
  } finally {
    submitting.value = false
  }
}

// P1C: 提交失败重试（保留答案）
async function handleRetrySubmit() {
  submitError.value = false
  // 保留当前 answers/multipleAnswers 状态，重新提交
  await doSubmit()
}

// ===== 重做 =====
async function handleRetry() {
  if (!canRetry.value) {
    ElMessage.warning(t('exerciseTake.maxAttemptsReached'))
    return
  }
  resultVisible.value = false
  // 重新从后端加载题目并重置状态
  if (currentExercise.value) {
    await startExercise(currentExercise.value)
  }
}

// ===== 返回列表 =====
function handleBackToList() {
  clearTimer()
  clearElapsedTimer()
  exerciseStarted.value = false
  resultVisible.value = false
  submitted.value = false
  currentIndex.value = 0
  currentExercise.value = null
  questions.value = []
  questionIds.value = []
}

// ===== 查看解析 =====
function handleViewAnalysis() {
  resultVisible.value = false
  // P1I-017: 仅在当前不在第一题时才重置索引，避免无意义重渲染
  if (currentIndex.value !== 0) {
    currentIndex.value = 0
  }
  nextTick(() => {
    const el = document.querySelector('.question-card')
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
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
    // P1-C 修复：兼容纯逗号分隔格式（"2,4"），此前仅支持 JSON 数组导致
    // 编辑端创建的多选题（answer=选项值 join(',')）永远判定错误
    return String(answer).split(',').map(v => v.trim()).filter(Boolean)
  }
}

function isMultipleCorrect(value, answer) {
  const arr = parseMultipleAnswer(answer)
  return arr.includes(value)
}

function questionTypeLabel(type) {
  const map = { SINGLE: t('question.typeSingle'), MULTIPLE: t('question.typeMultiple'), JUDGE: t('question.typeJudge'), FILL: t('question.typeFill'), ESSAY: t('question.typeEssay'), SHORT_ANSWER: t('question.typeShortAnswer') }
  return map[type] || type
}

function questionTypeTagType(type) {
  const map = { SINGLE: 'primary', MULTIPLE: 'warning', JUDGE: 'info', FILL: 'success', ESSAY: 'danger' }
  return map[type] || 'info'
}

function formatUserAnswer(q) {
  if (q.questionType === 'MULTIPLE') {
    const arr = multipleAnswers[q.id] || []
    return arr.length ? arr.join('、') : t('exerciseTake.notAnswered')
  }
  if (q.questionType === 'FILL') {
    return answers[q.id] || t('exerciseTake.notAnswered')
  }
  // SINGLE / JUDGE
  const opt = q.options?.find(o => o.value === answers[q.id])
  return opt ? `${opt.label}. ${opt.text}` : (answers[q.id] || t('exerciseTake.notAnswered'))
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
    return q.answer || t('course.none')
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
  min-height: 100dvh;
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

.time-elapsed {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm, 14px);
  color: var(--el-text-color-secondary);
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
  padding: 0;
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
  padding: var(--space-3) var(--space-3) calc(var(--space-3) + env(safe-area-inset-bottom, 0px));
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

.result-manual-hint {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-xs, 12px);
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border-radius: var(--radius-md, 8px);
  padding: var(--space-2) var(--space-3);
  width: 100%;
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

/* ===== 浮动答题卡（移动端）===== */
.answer-sheet-fab {
  position: fixed;
  bottom: 80px;
  right: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 28px;
  background: var(--role-primary, #409eff);
  color: #fff;
  font-size: 10px;
  gap: 2px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  border: none;
}

.answer-sheet-fab:active {
  transform: scale(0.92);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
}

.answer-sheet-fab .el-icon {
  font-size: 20px;
}

.answer-sheet-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 10000;
}

.answer-sheet-panel {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-height: 50vh;
  background: #fff;
  border-radius: 12px 12px 0 0;
  z-index: 10001;
  overflow-y: auto;
  padding: 16px;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.12);
}

.answer-sheet-panel .answer-sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  font-weight: 600;
  font-size: 16px;
}

.sheet-progress {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  font-weight: normal;
}

.answer-sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  padding-bottom: 8px;
}

.answer-sheet-item {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f0f0f0;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  transition: background 0.2s, color 0.2s, border-color 0.2s;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  border: none;
}

.answer-sheet-item:active {
  transform: scale(0.9);
}

.answer-sheet-item.answered {
  background: #67c23a;
  color: #fff;
}

.answer-sheet-item.current {
  border: 2px solid #409eff;
  background: #ecf5ff;
  color: #409eff;
}

.q-dot:focus-visible,
.answer-sheet-fab:focus-visible,
.answer-sheet-item:focus-visible {
  outline: 2.5px solid var(--role-primary, #6366f1);
  outline-offset: 2px;
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--role-primary) 20%, transparent);
  border-color: var(--role-primary, #6366f1);
}

/* P1-UX: prefers-reduced-motion 时用高对比度实线代替动画焦点 */
@media (prefers-reduced-motion: reduce) {
  .q-dot:focus-visible,
  .answer-sheet-fab:focus-visible,
  .answer-sheet-item:focus-visible {
    outline: 3px solid #1a56db;
    outline-offset: 2px;
    box-shadow: none;
  }
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
