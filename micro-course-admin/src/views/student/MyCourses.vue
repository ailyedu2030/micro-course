<!--
  我的课程
  路由路径: /student/my-courses
  Phase 2
  Author: jackie
-->
<template>
  <div class="my-courses role-student fade-in">
    <!-- PC 布局 (≥769px) -->
    <div v-if="!isMobile" class="pc-layout">
      <!-- 页面 Header -->
      <div class="page-header">
        <div class="header-content">
          <h1 class="page-title">欢迎学习，{{ userStore.realName || '同学' }}</h1>
          <p class="page-subtitle">持续学习，遇见更好的自己</p>
        </div>
      </div>

      <!-- 顶部 Tab -->
      <div class="top-tabs">
        <el-tabs v-model="activeTab" class="course-tabs" @tab-change="handleTabChange">
          <el-tab-pane name="in-progress">
            <template #label>
              <span class="tab-label">
                <el-icon><Reading /></el-icon>
                进行中
                <span v-if="inProgressCourses.length > 0" class="tab-badge">{{ inProgressCourses.length }}</span>
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane name="completed">
            <template #label>
              <span class="tab-label">
                <el-icon><CircleCheck /></el-icon>
                已完成
                <span v-if="completedCourses.length > 0" class="tab-badge completed">{{ completedCourses.length }}</span>
              </span>
            </template>
          </el-tab-pane>
          <el-tab-pane name="favorited">
            <template #label>
              <span class="tab-label">
                <el-icon><Star /></el-icon>
                已收藏
                <span v-if="favoritedCourses.length > 0" class="tab-badge favorited">{{ favoritedCourses.length }}</span>
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 主内容区 -->
      <div class="main-content">
        <!-- Loading 状态 -->
        <div v-if="loading" class="course-grid">
          <el-row :gutter="24">
            <el-col v-for="n in 6" :key="n" :span="8">
              <el-card class="course-card skeleton-card" shadow="never">
                <el-skeleton animated>
                  <template #template>
                    <div class="skeleton-cover" />
                    <div class="skeleton-info">
                      <el-skeleton-item variant="text" class="skeleton-title" />
                      <el-skeleton-item variant="text" class="skeleton-meta" />
                      <el-skeleton-item variant="text" class="skeleton-progress" />
                    </div>
                  </template>
                </el-skeleton>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <!-- 空状态 -->
        <el-empty
          v-else-if="displayCourses.length === 0"
          class="empty-state"
        >
          <template #image>
            <el-icon class="empty-icon"><Folder /></el-icon>
          </template>
          <template #description>
            <p>{{ emptyDescription }}</p>
          </template>
        </el-empty>

        <!-- 课程列表 -->
        <div v-else class="course-grid">
          <el-row :gutter="24">
            <el-col
              v-for="course in displayCourses"
              :key="course.courseId"
              :span="8"
            >
              <el-card
                class="course-card"
                shadow="hover"
                @click="handleContinue(course.courseId)"
              >
                <!-- 封面 -->
                <div class="course-cover">
                  <img
                    v-if="course.coverUrl"
                    :src="course.coverUrl"
                    :alt="course.courseTitle"
                  />
                  <div v-else class="cover-placeholder">
                    <el-icon :size="40"><VideoPlay /></el-icon>
                  </div>
                  <!-- 进度标签 -->
                  <el-tag
                    v-if="activeTab === 'in-progress'"
                    class="progress-chip"
                    type="primary"
                    effect="dark"
                  >
                    {{ course.progress || 0 }}%
                  </el-tag>
                  <el-tag
                    v-else-if="activeTab === 'completed'"
                    class="progress-chip"
                    type="success"
                    effect="dark"
                  >
                    已完成
                  </el-tag>
                  <el-tag
                    v-else
                    class="progress-chip"
                    type="warning"
                    effect="dark"
                  >
                    收藏
                  </el-tag>
                </div>

                <!-- 课程信息 -->
                <div class="course-info">
                  <h3 class="course-title">{{ course.courseTitle }}</h3>
                  <p class="course-meta">
                    <el-icon><User /></el-icon>
                    <span>{{ course.teacherName || '未知教师' }}</span>
                  </p>

                  <!-- 进度条 (进行中) -->
                  <div v-if="activeTab === 'in-progress'" class="progress-wrap">
                    <el-progress
                      :percentage="course.progress || 0"
                      :stroke-width="6"
                      :color="progressColor"
                    />
                    <span class="progress-text">{{ course.progress || 0 }}%</span>
                  </div>

                  <!-- 练习进度 (进行中) -->
                  <div
                    v-if="activeTab === 'in-progress' && courseProgressMap[course.courseId]"
                    class="exercise-progress"
                  >
                    <span class="exercise-text">
                      练习 {{ courseProgressMap[course.courseId].completedExercises }}/{{ courseProgressMap[course.courseId].totalExercises }} 完成
                    </span>
                  </div>

                  <!-- 视频进度 (进行中) -->
                  <div
                    v-if="activeTab === 'in-progress' && videoProgressMap[course.courseId]"
                    class="video-progress"
                  >
                    <span class="video-progress-text">
                      视频 {{ videoProgressMap[course.courseId].completed }}/{{ videoProgressMap[course.courseId].total }} ({{ videoProgressMap[course.courseId].percent }}%)
                    </span>
                  </div>

                  <!-- 时间信息 -->
                  <div class="time-info">
                    <span class="time-text">
                      <el-icon><Clock /></el-icon>
                      {{ activeTab === 'completed' ? '完成于' : '最近学习' }}：
                      {{ formatTime(activeTab === 'completed' ? course.completedAt : (course.lastWatchAt || course.enrolledAt)) }}
                    </span>
                  </div>

                  <!-- 操作按钮 -->
                  <div class="card-actions">
                    <el-button
                      :type="activeTab === 'in-progress' ? 'primary' : 'default'"
                      size="small"
                      @click.stop="handleContinue(course.courseId)"
                    >
                      {{ activeTab === 'in-progress' ? '继续学习' : activeTab === 'completed' ? '查看详情' : '开始学习' }}
                    </el-button>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 分页 -->
          <div v-if="totalElements > 0" class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="totalElements"
              :page-sizes="[9, 18, 36]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- H5 布局 (≤768px) -->
    <div v-else class="h5-layout">
      <!-- 紧凑 Header -->
      <div class="h5-header">
        <h1 class="h5-title">欢迎学习，{{ userStore.realName || '同学' }}</h1>
      </div>

      <!-- 横向滚动 Tab Bar -->
      <div class="h5-tabs">
        <div class="h5-tab-scroll">
          <div class="h5-tab-bar">
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'in-progress' }"
              @click="activeTab = 'in-progress'"
            >
              <el-icon><Reading /></el-icon>
              进行中
              <span v-if="inProgressCourses.length > 0" class="h5-tab-badge">{{ inProgressCourses.length }}</span>
            </div>
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'completed' }"
              @click="activeTab = 'completed'"
            >
              <el-icon><CircleCheck /></el-icon>
              已完成
              <span v-if="completedCourses.length > 0" class="h5-tab-badge">{{ completedCourses.length }}</span>
            </div>
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'favorites' }"
              @click="activeTab = 'favorites'"
            >
              <el-icon><Star /></el-icon>
              收藏
              <span v-if="favoriteCourses.length > 0" class="h5-tab-badge">{{ favoriteCourses.length }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading 状态 -->
      <div v-if="loading" class="h5-course-list">
        <el-card v-for="n in 3" :key="n" class="h5-course-card" shadow="never">
          <el-skeleton animated>
            <template #template>
              <div class="skeleton-cover h5-skeleton-cover" />
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
        v-else-if="displayCourses.length === 0"
        class="empty-state"
      >
        <template #image>
          <el-icon class="empty-icon"><Folder /></el-icon>
        </template>
        <template #description>
          <p>{{ emptyDescription }}</p>
        </template>
      </el-empty>

      <!-- 课程列表 (单列) -->
      <div v-else class="h5-course-list">
        <el-card
          v-for="course in displayCourses"
          :key="course.courseId"
          class="h5-course-card"
          shadow="hover"
          @click="handleContinue(course.courseId)"
        >
          <!-- 封面 16:9 -->
          <div class="h5-course-cover">
            <img
              v-if="course.coverUrl"
              :src="course.coverUrl"
              :alt="course.courseTitle"
            />
            <div v-else class="cover-placeholder">
              <el-icon :size="32"><VideoPlay /></el-icon>
            </div>
            <!-- 进度标签 -->
            <el-tag
              v-if="activeTab === 'in-progress'"
              class="h5-progress-chip"
              type="primary"
              effect="dark"
            >
              {{ course.progress || 0 }}%
            </el-tag>
            <el-tag
              v-else-if="activeTab === 'completed'"
              class="h5-progress-chip"
              type="success"
              effect="dark"
            >
              已完成
            </el-tag>
            <el-tag
              v-else
              class="h5-progress-chip"
              type="warning"
              effect="dark"
            >
              收藏
            </el-tag>
          </div>

          <!-- 课程信息 -->
          <div class="h5-course-info">
            <h3 class="h5-course-title">{{ course.courseTitle }}</h3>
            <p class="h5-course-meta">
              <el-icon><User /></el-icon>
              {{ course.teacherName || '未知教师' }}
            </p>

            <!-- 进度条 -->
            <div v-if="activeTab === 'in-progress'" class="h5-progress-wrap">
              <el-progress
                :percentage="course.progress || 0"
                :stroke-width="4"
                :color="progressColor"
              />
              <span class="h5-progress-text">{{ course.progress || 0 }}%</span>
            </div>

            <!-- 练习进度 (进行中) -->
            <div
              v-if="activeTab === 'in-progress' && courseProgressMap[course.courseId]"
              class="h5-exercise-progress"
            >
              <span class="h5-exercise-text">
                练习 {{ courseProgressMap[course.courseId].completedExercises }}/{{ courseProgressMap[course.courseId].totalExercises }} 完成
              </span>
            </div>

            <!-- 视频进度 (进行中) -->
            <div
              v-if="activeTab === 'in-progress' && videoProgressMap[course.courseId]"
              class="h5-video-progress"
            >
              <span class="h5-video-progress-text">
                视频 {{ videoProgressMap[course.courseId].completed }}/{{ videoProgressMap[course.courseId].total }} ({{ videoProgressMap[course.courseId].percent }}%)
              </span>
            </div>

            <!-- 时间 -->
            <p class="h5-time-info">
              <el-icon><Clock /></el-icon>
              {{ activeTab === 'completed' ? '完成于' : '最近学习' }}：
              {{ formatTime(activeTab === 'completed' ? course.completedAt : (course.lastWatchAt || course.enrolledAt)) }}
            </p>

            <!-- 按钮 -->
            <el-button
              :type="activeTab === 'in-progress' ? 'primary' : 'default'"
              size="small"
              class="h5-action-btn"
              @click.stop="handleContinue(course.courseId)"
            >
              {{ activeTab === 'in-progress' ? '继续学习' : activeTab === 'completed' ? '查看详情' : '开始学习' }}
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 底部安全区 -->
      <div class="h5-bottom-safe" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Reading,
  CircleCheck,
  Star,
  Folder,
  VideoPlay,
  User,
  Clock
} from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'
import { getCompletion, getLearningProgress } from '../../api/learning-progress'
import { getChapters } from '../../api/chapter'
import { getMyFavorites } from '../../api/favorite'

const router = useRouter()
const userStore = useUserStore()

// 响应式设备检测
const isMobile = ref(window.innerWidth <= 768)
const updateDevice = () => {
  isMobile.value = window.innerWidth <= 768
}
onMounted(() => {
  window.addEventListener('resize', updateDevice)
  fetchEnrollments()
})
onUnmounted(() => {
  window.removeEventListener('resize', updateDevice)
})

const activeTab = ref('in-progress')
const loading = ref(false)
const enrollments = ref([])
const page = ref(1)
const size = ref(9)
const totalElements = ref(0)
// 课程练习进度映射: { [courseId]: { completedExercises, totalExercises } }
const courseProgressMap = ref({})
// 课程视频进度映射: { [courseId]: { total, completed, percent } }
const videoProgressMap = ref({})

const progressColor = 'var(--role-primary)'

const inProgressCourses = computed(() =>
  (enrollments.value || []).filter(e => !e.completed)
)

const completedCourses = computed(() =>
  (enrollments.value || []).filter(e => e.completed)
)

// 模拟收藏课程（实际项目中应从后端获取）
const favoritedCourses = computed(() =>
  (enrollments.value || []).filter(e => e.favorited)
)

const displayCourses = computed(() => {
  const list = activeTab.value === 'in-progress'
    ? inProgressCourses.value
    : activeTab.value === 'completed'
    ? completedCourses.value
    : favoritedCourses.value
  // 简单分页
  const start = (page.value - 1) * size.value
  return list.slice(start, start + size.value)
})

const totalDisplayElements = computed(() => {
  if (activeTab.value === 'in-progress') return inProgressCourses.value.length
  if (activeTab.value === 'completed') return completedCourses.value.length
  return favoritedCourses.value.length
})

const emptyDescription = computed(() => {
  if (activeTab.value === 'in-progress') return '暂无进行中的课程'
  if (activeTab.value === 'completed') return '暂无已完成的课程'
  return '暂无收藏的课程'
})

const formatTime = (timeStr) => {
  if (!timeStr) return '暂无'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchEnrollments = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('无法获取用户信息')
    return
  }
  loading.value = true
  try {
    const res = await getMyEnrollments(userId)
    const list = res.data || []

    // 并行获取整体完成度（用于修正进度条）
    const completionData = await getCompletion({ userId }).catch(() => ({}))
    const completionMap = completionData?.data || {}

    // 对每门进行中课程获取练习完成进度
    const inProgress = list.filter(e => !e.completed)
    const progressPromises = inProgress.map(e =>
      getLearningProgress({ courseId: e.courseId }).catch(() => null)
    )
    const progressResults = await Promise.all(progressPromises)
    const newProgressMap = {}
    progressResults.forEach((presult, idx) => {
      const courseId = inProgress[idx].courseId
      if (presult?.data) {
        const pdata = presult.data
        newProgressMap[courseId] = {
          completedExercises: pdata.completedExercises ?? pdata.completed ?? 0,
          totalExercises: pdata.totalExercises ?? pdata.total ?? 0,
          progress: completionMap[courseId]?.progress ?? inProgress[idx].progress ?? 0
        }
      }
    })
    courseProgressMap.value = newProgressMap

    // 对每门进行中课程获取章节总数，计算视频进度
    const chapterPromises = inProgress.map(e =>
      getChapters({ courseId: e.courseId, size: 1000 }).catch(() => null)
    )
    const chapterResults = await Promise.all(chapterPromises)
    const newVideoProgressMap = {}
    chapterResults.forEach((cresult, idx) => {
      const courseId = inProgress[idx].courseId
      const chapters = cresult?.data?.items || cresult?.data || []
      const totalChapters = chapters.length || 1
      const progressPercent = completionMap[courseId]?.progress ?? inProgress[idx].progress ?? 0
      const completedVideos = Math.round(totalChapters * progressPercent / 100)
      newVideoProgressMap[courseId] = {
        total: totalChapters,
        completed: completedVideos,
        percent: progressPercent
      }
    })
    videoProgressMap.value = newVideoProgressMap

    // 用 completion 数据修正 enrollment 进度，并获取收藏列表
    let favoriteSet = new Set()
    try {
      const favRes = await getMyFavorites()
      const favList = favRes?.data || []
      favoriteSet = new Set(favList.map(f => String(f.courseId)))
    } catch { /* ignore */ }
    enrollments.value = list.map(e => {
      const cp = completionMap[e.courseId]
      const updatedProgress = cp?.progress ?? e.progress ?? 0
      return { ...e, progress: updatedProgress, favorited: favoriteSet.has(String(e.courseId)) }
    })
    totalElements.value = enrollments.value.length
  } catch {
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  page.value = 1
}

const handleSizeChange = () => {
  page.value = 1
}

const handlePageChange = () => {
  // displayCourses computed 会自动响应 page 变化
}

const handleContinue = (courseId) => {
  router.push(`/student/learning?courseId=${courseId}`)
}
</script>

<style scoped>
/* ================================================
   PC 布局
   ================================================ */
.pc-layout {
  min-height: 100vh;
  background: var(--el-bg-color);
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

/* 顶部 Tab */
.top-tabs {
  margin: calc(var(--space-4) * -1) var(--space-5) var(--space-4);
  position: relative;
  z-index: 10;
}

.course-tabs {
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  padding: 0 var(--space-4);
}

.course-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.course-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-base);
  cursor: pointer;
}

.tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 var(--space-1);
  font-size: var(--text-xs);
  font-weight: var(--weight-bold);
  color: var(--el-color-white);
  background: var(--role-primary);
  border-radius: 9px;
  cursor: pointer;
}

.tab-badge.completed {
  background: var(--el-color-success);
}

.tab-badge.favorited {
  background: var(--el-color-warning);
}

/* 主内容区 */
.main-content {
  padding: 0 var(--space-5) var(--space-5);
}

/* 课程网格 */
.course-grid {
  min-height: 400px;
}

.course-card {
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
  margin-bottom: var(--space-4);
  overflow: hidden;
}

.course-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg) !important;
}

.course-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  background: var(--role-primary-light);
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--role-primary);
  opacity: 0.5;
}

.progress-chip {
  position: absolute;
  top: var(--space-3);
  right: var(--space-3);
  cursor: pointer;
}

.course-info {
  padding: var(--space-4);
}

.course-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  line-height: 1.4;
  min-height: 2.8em;
}

.course-meta {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.progress-wrap {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.progress-wrap :deep(.el-progress) {
  flex: 1;
}

.progress-text {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--role-primary);
  min-width: 36px;
  text-align: right;
}

.exercise-progress {
  margin-bottom: var(--space-3);
}

.exercise-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.video-progress {
  margin-bottom: var(--space-2);
}

.video-progress-text {
  font-size: var(--text-xs);
  color: var(--role-primary);
  font-weight: var(--weight-medium);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.time-info {
  margin-bottom: var(--space-3);
}

.time-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.card-actions {
  display: flex;
  justify-content: flex-end;
}

.card-actions :deep(.el-button) {
  cursor: pointer;
}

/* 分页 */
.pagination-wrap {
  margin-top: var(--space-5);
  display: flex;
  justify-content: center;
}

/* ================================================
   H5 布局
   ================================================ */
.h5-layout {
  min-height: 100vh;
  background: var(--el-bg-color);
  padding-bottom: 56px;
}

/* 紧凑 Header */
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

/* 横向滚动 Tab Bar */
.h5-tabs {
  background: var(--el-bg-color-overlay);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.h5-tab-scroll {
  display: flex;
}

.h5-tab-bar {
  display: flex;
  gap: var(--space-2);
}

.h5-tab-item {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-2) var(--space-4);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out);
  white-space: nowrap;
}

.h5-tab-item.active {
  color: var(--el-color-white);
  background: var(--role-primary);
}

.h5-tab-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 16px;
  height: 16px;
  padding: 0 var(--space-1);
  font-size: var(--text-xs);
  font-weight: var(--weight-bold);
  color: var(--el-color-white);
  background: var(--role-primary);
  border-radius: var(--radius-md);
}

.h5-tab-item.active .h5-tab-badge {
  background: var(--el-bg-color-overlay);
  color: var(--role-primary);
}

/* 课程列表 */
.h5-course-list {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.h5-course-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.h5-course-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
}

.h5-course-cover {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
  background: var(--role-primary-light);
}

.h5-course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.h5-progress-chip {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  font-size: var(--text-xs);
  padding: 2px 6px;
  cursor: pointer;
}

.h5-course-info {
  padding: var(--space-3);
}

.h5-course-title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.h5-course-meta {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.h5-progress-wrap {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.h5-progress-wrap :deep(.el-progress) {
  flex: 1;
}

.h5-progress-text {
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--role-primary);
  min-width: 30px;
  text-align: right;
}

.h5-exercise-progress {
  margin-bottom: var(--space-2);
}

.h5-exercise-text {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.h5-video-progress {
  margin-bottom: var(--space-2);
}

.h5-video-progress-text {
  font-size: var(--text-xs);
  color: var(--role-primary);
  font-weight: var(--weight-medium);
}

.h5-time-info {
  margin: 0 0 var(--space-3);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.h5-action-btn {
  width: 100%;
  cursor: pointer;
}

.h5-bottom-safe {
  height: 56px;
}

/* ================================================
   Skeleton Loading
   ================================================ */
.skeleton-card {
  border-radius: var(--radius-lg);
}

.skeleton-cover {
  aspect-ratio: 16 / 9;
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-lg) var(--radius-lg) 0 0;
}

.h5-skeleton-cover {
  margin-bottom: var(--space-3);
}

.skeleton-info {
  padding: var(--space-4);
}

.skeleton-title {
  height: 20px;
  margin-bottom: var(--space-2);
  width: 80%;
}

.skeleton-meta {
  height: 14px;
  margin-bottom: var(--space-2);
  width: 60%;
}

.skeleton-progress {
  height: 8px;
  margin-bottom: var(--space-2);
  width: 100%;
}

/* ================================================
   Empty State
   ================================================ */
.empty-state {
  padding: var(--space-8) 0;
}

.empty-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
}
</style>