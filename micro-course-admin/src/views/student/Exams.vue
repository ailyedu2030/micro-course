<!--
  我的考试
  路由路径: /student/exams
  Phase 5
  Author: jackie
-->
<template>
  <div class="exams-page role-student fade-in">
    <!-- PC 布局 (≥769px) -->
    <div v-if="!isMobile" class="pc-layout">
      <!-- 面包屑导航 -->
      <div class="breadcrumb-wrap">
        <el-breadcrumb>
          <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>考试中心</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <!-- 页面 Header -->
      <div class="page-header">
        <div class="header-content">
          <h1 class="page-title">我的考试</h1>
          <p class="page-subtitle">查看我的考试安排</p>
        </div>
      </div>

      <!-- 主内容区 -->
      <div class="main-content">
        <!-- Loading 状态 -->
        <div v-if="loading" class="exam-list">
          <el-card v-for="n in 4" :key="n" class="exam-card skeleton-card student-card-item" shadow="never">
            <el-skeleton animated>
              <template #template>
                <div class="skeleton-info">
                  <el-skeleton-item variant="text" class="skeleton-title" />
                  <el-skeleton-item variant="text" class="skeleton-meta" />
                  <el-skeleton-item variant="text" class="skeleton-meta" />
                </div>
              </template>
            </el-skeleton>
          </el-card>
        </div>

        <!-- 错误状态 -->
        <el-result
          v-else-if="errorState"
          icon="error"
          title="加载失败"
          sub-title="考试数据加载异常，请稍后重试"
          class="error-state"
        >
          <template #extra>
            <el-button type="primary" @click="fetchExams">重新加载</el-button>
          </template>
        </el-result>

        <!-- 考试列表（含分类 Tab） -->
        <div v-else class="exam-list">
          <el-tabs v-model="activeTab" class="exam-tabs">
            <el-tab-pane label="待参加" name="pending" :disabled="loading" />
            <el-tab-pane label="已完成" name="completed" :disabled="loading" />
          </el-tabs>

          <el-card
            v-for="exam in filteredExamList"
            :key="exam.examId"
            class="exam-card student-card-item"
            shadow="hover"
            @click="handleExamClick(exam)"
            style="cursor:pointer"
          >
            <div class="exam-info">
              <h3 class="exam-title">{{ exam.examTitle || exam.title }}</h3>
              <p class="exam-course">
                <el-icon><Reading /></el-icon>
                <span>{{ exam.courseName || exam.courseTitle || '未知课程' }}</span>
              </p>
              <p v-if="exam.examTime" class="exam-time">
                <el-icon><Clock /></el-icon>
                <span>{{ formatTime(exam.examTime) }}</span>
              </p>
              <p v-if="exam.duration" class="exam-duration">
                <el-icon><Timer /></el-icon>
                <span>时长：{{ exam.duration }}分钟</span>
              </p>
              <div class="exam-actions">
                <el-button
                  v-if="exam._expired"
                  type="info"
                  size="small"
                  disabled
                >
                  已截止
                </el-button>
                <el-button
                  v-else-if="!exam._attempted"
                  type="primary"
                  size="small"
                  @click="handleJoinExam(exam)"
                >
                  参加考试
                </el-button>
                <el-button
                  v-else
                  type="info"
                  size="small"
                  disabled
                >
                  已完成
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 空状态（当前 Tab 无数据） -->
          <el-empty
            v-if="filteredExamList.length === 0"
            class="empty-state"
          >
            <template #image>
              <el-icon class="empty-icon"><Tickets /></el-icon>
            </template>
            <template #description>
              <p>{{ activeTab === 'pending' ? '暂无待参加的考试' : activeTab === 'completed' ? '暂无已完成的考试' : '暂无已截止的考试' }}</p>
            </template>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- H5 布局 (≤768px) -->
    <div v-else class="h5-layout">
      <!-- 面包屑导航 -->
      <div class="h5-breadcrumb-wrap">
        <el-breadcrumb separator="→">
          <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>考试中心</el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="h5-header">
        <h1 class="h5-title">我的考试</h1>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="h5-exam-list">
        <el-card v-for="n in 3" :key="n" class="h5-exam-card student-card-item" shadow="never">
          <el-skeleton animated>
            <template #template>
              <div class="skeleton-info">
                <el-skeleton-item variant="text" class="skeleton-title" />
                <el-skeleton-item variant="text" class="skeleton-meta" />
              </div>
            </template>
          </el-skeleton>
        </el-card>
      </div>

      <!-- 错误状态 -->
      <el-result
        v-else-if="errorState"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
        class="error-state"
      >
        <template #extra>
          <el-button type="primary" size="small" @click="fetchExams">重新加载</el-button>
        </template>
      </el-result>

      <!-- 考试列表（含分类 Tab） -->
      <div v-else class="h5-exam-list">
        <el-tabs v-model="activeTab" class="exam-tabs h5-tabs">
          <el-tab-pane label="待参加" name="pending" :disabled="loading" />
          <el-tab-pane label="已完成" name="completed" :disabled="loading" />
        </el-tabs>

        <el-card
          v-for="exam in filteredExamList"
          :key="exam.examId"
          class="h5-exam-card student-card-item"
          shadow="hover"
        >
          <h3 class="h5-exam-title">{{ exam.examTitle || exam.title }}</h3>
          <p class="h5-exam-course">
            <el-icon><Reading /></el-icon>
            {{ exam.courseName || exam.courseTitle || '未知课程' }}
          </p>
          <p v-if="exam.examTime" class="h5-exam-time">
            <el-icon><Clock /></el-icon>
            {{ formatTime(exam.examTime) }}
          </p>
          <el-button
            v-if="exam._expired"
            type="info"
            size="small"
            class="h5-action-btn"
            disabled
          >
            已截止
          </el-button>
          <el-button
            v-else-if="!exam._attempted"
            type="primary"
            size="small"
            class="h5-action-btn"
            @click="handleJoinExam(exam)"
          >
            参加考试
          </el-button>
          <el-button
            v-else
            type="info"
            size="small"
            class="h5-action-btn"
            disabled
          >
            已完成
          </el-button>
        </el-card>

        <!-- 空状态（当前 Tab 无数据） -->
        <el-empty
          v-if="filteredExamList.length === 0"
          class="empty-state"
        >
          <template #image>
            <el-icon class="empty-icon"><Tickets /></el-icon>
          </template>
          <template #description>
            <p>{{ activeTab === 'pending' ? '暂无待参加的考试' : '暂无已完成的考试' }}</p>
          </template>
        </el-empty>
      </div>

      <div class="h5-bottom-safe" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Tickets, Reading, Clock, Timer } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { getMyExams } from '../../api/exam'
import { getChapters } from '../../api/chapter'
import { getLearningProgress } from '../../api/learning-progress'
import { getMyAttemptCount } from '../../api/exercise-record'

const router = useRouter()
const userStore = useUserStore()

// 响应式设备检测
const isMobile = ref(window.innerWidth <= 768)
const updateDevice = () => {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => {
  window.addEventListener('resize', updateDevice)
  fetchExams()
})
onUnmounted(() => {
  window.removeEventListener('resize', updateDevice)
})

const loading = ref(false)
const errorState = ref(false)
const examList = ref([])
const activeTab = ref('pending')

// 按状态分类的考试列表
const pendingExams = computed(() => examList.value.filter(e => !e._attempted && !e._expired))
const completedExams = computed(() => examList.value.filter(e => e._attempted))
const expiredExams = computed(() => examList.value.filter(e => e._expired))

// 根据当前 tab 显示的列表
const filteredExamList = computed(() => {
  if (activeTab.value === 'pending') return pendingExams.value
  if (activeTab.value === 'completed') return completedExams.value
  if (activeTab.value === 'expired') return expiredExams.value
  return examList.value
})

const formatTime = (timeStr) => {
  if (!timeStr) return '未定'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const fetchExams = async () => {
  loading.value = true
  errorState.value = false
  try {
    // J3-01: 调用真实的考试列表 API（基于 exercises 表 is_exam=true）
    const res = await getMyExams()
    const rawList = (res.data || []).map(exam => ({
      ...exam,
      examId: exam.id,
      examTitle: exam.title,
      courseTitle: exam.courseTitle || '未知课程',
      examTime: exam.startTime || exam.createdAt || null,
      duration: exam.timeLimit || null
    }))

    // 批量查询每场考试是否已作答（用于状态分类）
    const attemptResults = await Promise.allSettled(
      rawList.map(exam => getMyAttemptCount(exam.examId))
    )

    examList.value = rawList.map((exam, idx) => {
      const attemptRes = attemptResults[idx]
      const attempted = attemptRes.status === 'fulfilled' && attemptRes.value?.data?.attemptCount > 0
      const now = new Date()
      const examEnd = exam.examTime ? new Date(new Date(exam.examTime).getTime() + (exam.timeLimit || 0) * 60000) : null
      const expired = examEnd && examEnd < now
      return { ...exam, _attempted: attempted, _expired: expired }
    })
  } catch {
    errorState.value = true
    ElMessage.error('加载考试信息失败')
  } finally {
    loading.value = false
  }
}

/** 检查前置章节是否全部完成 */
async function checkPrerequisiteChapters(exam) {
  const courseId = exam.courseId
  const chapterId = exam.chapterId
  if (!courseId || !chapterId) return true // 缺失信息则放行

  try {
    // 1. 获取课程下所有章节（按 sortOrder 排序）
    const chaptersRes = await getChapters({ courseId, page: 0, size: 200 })
    const chapters = chaptersRes.data?.items || chaptersRes.data || []
    if (chapters.length === 0) return true

    // 找到当前章节的 sortOrder
    const currentChapter = chapters.find(c => c.id === chapterId || c.chapterId === chapterId)
    if (!currentChapter) return true

    const currentSortOrder = currentChapter.sortOrder || 0

    // 2. 获取用户在全部章节的学习进度
    const userId = userStore.userInfo?.id
    if (!userId) return true

    const progressRes = await getLearningProgress({ userId, courseId })
    const progressList = progressRes.data || []

    // 3. 检查所有 sortOrder < currentSortOrder 的章节是否已完成
    const previousChapters = chapters.filter(c => (c.sortOrder || 0) < currentSortOrder)
    for (const prev of previousChapters) {
      const progress = progressList.find(p => Number(p.chapterId) === Number(prev.id))
      if (!progress || !progress.completed) {
        return false
      }
    }
    return true
  } catch (e) {
    console.warn('[Exams] 前置章节检查失败，放行:', e)
    return true // 检查失败时放行，避免阻塞
  }
}

const handleJoinExam = async (exam) => {
  // P0: 检查前置章节是否全部完成
  const canProceed = await checkPrerequisiteChapters(exam)
  if (!canProceed) {
    ElMessageBox.alert('请先完成前置章节的学习和练习，再参加本场考试', '章节未完成', {
      confirmButtonText: '知道了',
      type: 'warning'
    })
    return
  }

  // 导航到考试/练习页面（传 examId 以便 ExerciseTake 自动开始考试）
  const chapterId = exam.chapterId || exam.id
  router.push({ name: 'StudentExerciseTake', params: { chapterId }, query: { examId: exam.examId } })
}

const handleExamClick = (exam) => {
  router.push(`/student/exams/${exam.examId}`)
}
</script>

<style scoped>
/* ================================================
   PC 布局
   ================================================ */
.pc-layout {
  min-height: 100dvh;
  background: var(--el-bg-color);
}

.breadcrumb-wrap {
  padding: var(--space-4) var(--space-5) 0;
}

/* 页面 Header */
.page-header {
  height: 120px;
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-content {
  text-align: center;
  color: var(--el-color-white);
}

.page-title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  letter-spacing: 1px;
}

.page-subtitle {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  opacity: 0.85;
}

/* 主内容区 */
.main-content {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* 考试卡片列表 */
.exam-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  max-width: 800px;
  margin: 0 auto;
}

.exam-card {
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.exam-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
}

/* Tab 样式 */
.exam-tabs {
  margin-bottom: var(--space-4);
}

.exam-tabs :deep(.el-tabs__header) {
  margin: 0 0 var(--space-3);
}

.exam-tabs.h5-tabs :deep(.el-tabs__header) {
  margin: 0 0 var(--space-3);
}

.exam-info {
  padding: var(--space-2);
}

.exam-title {
  margin: 0 0 var(--space-3);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.exam-course,
.exam-time,
.exam-duration {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.exam-actions {
  margin-top: var(--space-3);
  display: flex;
  justify-content: flex-end;
}

/* ================================================
   H5 布局
   ================================================ */
.h5-layout {
  min-height: 100dvh;
  background: var(--el-bg-color);
  padding-bottom: 56px;
}

.h5-breadcrumb-wrap {
  padding: var(--space-3) var(--space-4) 0;
}

.h5-header {
  height: 80px;
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.h5-title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
  color: var(--el-color-white);
  letter-spacing: 1px;
}

.h5-exam-list {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.h5-exam-card {
  border-radius: var(--radius-lg);
}

.h5-exam-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.h5-exam-course,
.h5-exam-time {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.h5-action-btn {
  width: 100%;
  margin-top: var(--space-3);
}

.h5-bottom-safe {
  height: 56px;
}

/* ================================================
   Empty State
   ================================================ */
.empty-state {
  padding: var(--space-8) 0;
}

.error-state {
  padding: var(--space-8) 0;
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
}

.empty-tip {
  margin-top: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

/* ================================================
   Skeleton
   ================================================ */
.skeleton-card {
  border-radius: var(--radius-lg);
}

.skeleton-info {
  padding: var(--space-3);
}

.skeleton-title {
  height: 20px;
  margin-bottom: var(--space-2);
  width: 70%;
}

.skeleton-meta {
  height: 14px;
  margin-bottom: var(--space-2);
  width: 50%;
}
</style>