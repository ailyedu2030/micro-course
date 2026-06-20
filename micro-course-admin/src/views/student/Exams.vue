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

        <!-- 空状态 -->
        <el-empty
          v-else-if="!errorState && examList.length === 0"
          class="empty-state"
        >
          <template #image>
            <el-icon class="empty-icon"><Tickets /></el-icon>
          </template>
          <template #description>
            <p>暂无考试安排</p>
          </template>
          <template #default>
            <p class="empty-tip">你还没有报名的考试</p>
          </template>
        </el-empty>

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

        <!-- 考试列表 -->
        <div v-else class="exam-list">
          <el-card
            v-for="exam in examList"
            :key="exam.examId"
            class="exam-card student-card-item"
            shadow="hover"
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
                <el-button type="primary" size="small" @click="handleJoinExam(exam)">
                  参加考试
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- H5 布局 (≤768px) -->
    <div v-else class="h5-layout">
      <!-- 面包屑导航 -->
      <div class="h5-breadcrumb-wrap">
        <el-breadcrumb>
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

      <!-- 空状态 -->
      <el-empty
        v-else-if="!errorState && examList.length === 0"
        class="empty-state"
      >
        <template #image>
          <el-icon class="empty-icon"><Tickets /></el-icon>
        </template>
        <template #description>
          <p>暂无考试安排</p>
        </template>
        <template #default>
          <p class="empty-tip">你还没有报名的考试</p>
        </template>
      </el-empty>

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

      <!-- 考试列表 -->
      <div v-else class="h5-exam-list">
        <el-card
          v-for="exam in examList"
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
          <el-button type="primary" size="small" class="h5-action-btn" @click="handleJoinExam(exam)">
            参加考试
          </el-button>
        </el-card>
      </div>

      <div class="h5-bottom-safe" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Tickets, Reading, Clock, Timer } from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'

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

const formatTime = (timeStr) => {
  if (!timeStr) return '未定'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const fetchExams = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('无法获取用户信息')
    return
  }
  loading.value = true
  errorState.value = false
  try {
    // 获取已报名的课程
    const res = await getMyEnrollments({ userId })
    const enrollments = res.data || []

    // 目前没有独立的学生考试 API，先显示空状态
    // 未来可扩展：从后端获取考试数据后填充 examList
    examList.value = []

    // 如果后端提供考试数据，可以这样处理：
    // examList.value = enrollments
    //   .filter(e => e.exams && e.exams.length > 0)
    //   .flatMap(e => e.exams.map(exam => ({ ...exam, courseName: e.courseTitle })))
  } catch {
    errorState.value = true
    ElMessage.error('加载考试信息失败')
  } finally {
    loading.value = false
  }
}

const handleJoinExam = (exam) => {
  ElMessage.info('考试功能即将上线')
  // 未来可扩展：router.push(`/student/exams/${exam.examId}`)
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
  padding: var(--space-5);
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