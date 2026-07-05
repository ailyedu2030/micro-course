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
      <div class="page-header student-welcome">
        <div class="header-content">
          <!-- P2-7: 欢迎文案——当前硬编码，后续可从后端配置接口获取 welcomeText 替换 -->
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
            <el-col v-for="n in size" :key="n" :xs="24" :sm="12" :md="8" :lg="6">
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

        <!-- P1-4: 加载失败重试入口 -->
        <div v-else-if="loadError" class="error-state">
          <el-empty class="empty-state">
            <template #image>
              <el-icon class="empty-icon danger-icon"><Warning /></el-icon>
            </template>
            <template #description>
              <p>{{ loadErrorMessage }}</p>
            </template>
          </el-empty>
          <div class="retry-wrap">
            <el-button type="primary" @click="fetchEnrollments">点击重新加载</el-button>
          </div>
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
          <template #default>
            <el-button type="primary" @click="$router.push('/student/courses')">去课程广场选课</el-button>
          </template>
        </el-empty>

        <!-- 课程列表 -->
        <div v-else class="course-grid">
          <el-row :gutter="24">
            <el-col
              v-for="course in displayCourses"
              :key="course.courseId"
              :xs="24"
              :sm="12"
              :md="8"
              :lg="6"
            >
              <el-card
                class="course-card student-card-item"
                shadow="hover"
                role="button"
                tabindex="0"
                :aria-label="`继续学习 ${course.courseTitle || ''}`"
                @click="handleContinue(course.courseId)"
                @keydown.enter="handleContinue(course.courseId)"
                @keydown.space.prevent="handleContinue(course.courseId)"
              >
                <!-- 封面 -->
                <div class="course-cover">
                  <img
                    :src="effectiveCover(course)"
                    :alt="course.courseTitle"
                    @error="handleImgError($event)"
                  />
                  <!-- P2-4: 进度标签——数据未加载完毕时不显示"未开始"，避免闪烁 -->
                  <el-tag
                    v-if="activeTab === 'in-progress' && (course.progress || 0) > 0"
                    class="progress-chip"
                    type="primary"
                    effect="dark"
                  >
                    {{ course.progress || 0 }}%
                  </el-tag>
                  <el-tag
                    v-else-if="activeTab === 'in-progress' && dataLoaded && (course.progress || 0) === 0"
                    class="progress-chip"
                    type="info"
                    effect="dark"
                  >
                    未开始
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
                    v-else-if="activeTab === 'favorited'"
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
                  <div v-if="activeTab === 'in-progress'" class="progress-wrap student-progress">
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

                  <!-- P1-6: 视频进度——使用实际完成/总数统计 -->
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
                     <!-- 客户体验修复 v1.7.0: 添加退课按钮 (P0-UX-U4) -->
                     <el-button
                       v-if="activeTab === 'in-progress'"
                       size="small"
                       type="danger"
                       plain
                       @click.stop="handleDropOut(course)"
                       aria-label="退课"
                     >
                       退课
                     </el-button>
                   </div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <!-- P0-1: 分页——使用 totalDisplayElements（当前 Tab 过滤后数量）替代 totalElements -->
          <div v-if="totalDisplayElements > 0" class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="totalDisplayElements"
              :page-sizes="[9, 18, 36]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="handleSizeChange"
              @current-change="handlePageChange" aria-label="分页导航"
/>
          </div>
        </div>
      </div>
    </div>

    <!-- H5 布局 (≤768px) -->
    <div v-else class="h5-layout">
      <!-- 紧凑 Header -->
      <div class="h5-header student-welcome">
        <!-- P2-7: 欢迎文案——当前硬编码，后续可从后端配置接口获取 welcomeText 替换 -->
        <h1 class="h5-title">欢迎学习，{{ userStore.realName || '同学' }}</h1>
      </div>

      <!-- P0-4: H5 横向滚动 Tab Bar——切换时重置页码 -->
      <div class="h5-tabs">
        <div class="h5-tab-scroll">
          <div class="h5-tab-bar">
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'in-progress' }"
              @click="handleH5TabChange('in-progress')"
            >
              <el-icon><Reading /></el-icon>
              进行中
              <span v-if="inProgressCourses.length > 0" class="h5-tab-badge">{{ inProgressCourses.length }}</span>
            </div>
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'completed' }"
              @click="handleH5TabChange('completed')"
            >
              <el-icon><CircleCheck /></el-icon>
              已完成
              <span v-if="completedCourses.length > 0" class="h5-tab-badge">{{ completedCourses.length }}</span>
            </div>
            <div
              class="h5-tab-item"
              :class="{ active: activeTab === 'favorited' }"
              @click="handleH5TabChange('favorited')"
            >
              <el-icon><Star /></el-icon>
              收藏
              <span v-if="favoritedCourses.length > 0" class="h5-tab-badge">{{ favoritedCourses.length }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading 状态 -->
      <div v-if="loading" class="h5-course-list">
          <el-card v-for="n in size" :key="n" class="h5-course-card" shadow="never">
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

      <!-- P1-4: 加载失败重试入口 (H5) -->
      <div v-else-if="loadError" class="error-state">
        <el-empty class="empty-state">
          <template #image>
            <el-icon class="empty-icon danger-icon"><Warning /></el-icon>
          </template>
          <template #description>
            <p>{{ loadErrorMessage }}</p>
          </template>
        </el-empty>
        <div class="retry-wrap">
          <el-button type="primary" @click="fetchEnrollments">点击重新加载</el-button>
        </div>
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
          class="h5-course-card student-card-item"
          shadow="hover"
          role="button"
          tabindex="0"
          :aria-label="`继续学习 ${course.courseTitle || ''}`"
          @click="handleContinue(course.courseId)"
          @keydown.enter="handleContinue(course.courseId)"
          @keydown.space.prevent="handleContinue(course.courseId)"
        >
          <!-- 封面 16:9 -->
          <div class="h5-course-cover">
            <img
              :src="effectiveCover(course)"
              :alt="course.courseTitle"
              @error="handleImgError($event)"
            />
            <!-- P2-4: 进度标签——数据未加载完毕时不显示"未开始"，避免闪烁 -->
            <el-tag
              v-if="activeTab === 'in-progress' && (course.progress || 0) > 0"
              class="h5-progress-chip"
              type="primary"
              effect="dark"
            >
              {{ course.progress || 0 }}%
            </el-tag>
            <el-tag
              v-else-if="activeTab === 'in-progress' && dataLoaded && (course.progress || 0) === 0"
              class="h5-progress-chip"
              type="info"
              effect="dark"
            >
              未开始
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
              v-else-if="activeTab === 'favorited'"
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
            <div v-if="activeTab === 'in-progress'" class="h5-progress-wrap student-progress">
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

            <!-- P1-6: 视频进度——使用实际完成/总数统计 -->
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

            <!-- 按钮组: 主按钮 + 退课 (仅进行中) -->
            <div class="h5-action-row">
              <el-button
                :type="activeTab === 'in-progress' ? 'primary' : 'default'"
                size="small"
                class="h5-action-btn"
                @click.stop="handleContinue(course.courseId)"
              >
                {{ activeTab === 'in-progress' ? '继续学习' : activeTab === 'completed' ? '查看详情' : '开始学习' }}
              </el-button>
              <!-- 客户体验修复 v1.7.0: H5 也加退课按钮 (P0-UX-U4 mobile variant) -->
              <el-button
                v-if="activeTab === 'in-progress'"
                size="small"
                plain
                type="danger"
                class="h5-dropout-btn"
                aria-label="退课"
                @click.stop="handleDropOut(course)"
              >
                退课
              </el-button>
            </div>
          </div>
        </el-card>
      </div>

      <!-- P2-6: 底部安全区——适配 iPhone 圆角底部 -->
      <div class="h5-bottom-safe" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useUrlPagination } from '@/composables/useUrlPagination';
import { swrCache } from '@/composables/useStaleWhileRevalidate';
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Reading,
  CircleCheck,
  Star,
  Folder,
  VideoPlay,
  User,
  Clock,
  Warning
} from '@element-plus/icons-vue'
import { useUserStore } from '../../store/user'
import { getMyEnrollments, cancelEnrollment } from '../../api/enrollment'
import { getCompletion, getLearningProgress, batchGetLearningProgress } from '../../api/learning-progress'
import { getChapters } from '../../api/chapter'
import { getMyFavorites } from '../../api/favorite'
import { getCourseById } from '../../api/course'
import { getDefaultCover } from '../../utils/coverHelper'

// 客户体验修复 v1.7.0: 课程 coverUrl 通常为 null,用类别感知的 SVG 兜底,
// 避免"千课一面"的全灰占位。effectiveCover 返回真实 URL 或生成的 data URI
const effectiveCover = (course) => {
  if (course?.coverUrl) return course.coverUrl
  return getDefaultCover({
    id: course?.courseId || course?.id,
    title: course?.courseTitle || course?.title,
    categoryId: course?.categoryId,
    teacherName: course?.teacherName
  })
}

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
/** P0-2: 存放仅收藏但未选课的课程（来自 getMyFavorites + getCourseById 补充） */
const extraFavorites = ref([])
const page = ref(1)
const size = ref(9)
const totalElements = ref(0)

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, null, [])
// 课程练习进度映射: { [courseId]: { completedExercises, totalExercises } }
const courseProgressMap = ref({})
// 课程视频进度映射: { [courseId]: { total, completed, percent } }
const videoProgressMap = ref({})
/** P2-4: 数据是否已完全加载——防止"未开始"标签在数据加载中闪烁 */
const dataLoaded = ref(false)
/** P1-4: 加载失败标志 */
const loadError = ref(false)
/** P2-2: 区分错误类型的提示信息 */
const loadErrorMessage = ref('加载课程失败')

// P1-2: 封面图加载失败占位图
const FALLBACK_COVER = 'data:image/svg+xml;charset=UTF-8,%3Csvg%20xmlns%3D%22http%3A//www.w3.org/2000/svg%22%20width%3D%22320%22%20height%3D%22180%22%3E%3Crect%20fill%3D%22%23f0f0f0%22%20width%3D%22320%22%20height%3D%22180%22/%3E%3Ctext%20fill%3D%22%23bbb%22%20font-size%3D%2216%22%20x%3D%2250%25%22%20y%3D%2250%25%22%20text-anchor%3D%22middle%22%20dy%3D%22.3em%22%3E%E6%9A%82%E6%97%A0%E5%B0%81%E9%9D%A2%3C/text%3E%3C/svg%3E'
// P1I-020: completionMap 提升为模块级，供懒加载函数使用
let completionMap = {}

const progressColor = 'var(--role-primary)'

const inProgressCourses = computed(() =>
  (enrollments.value || []).filter(e => !e.completed)
)

const completedCourses = computed(() =>
  (enrollments.value || []).filter(e => e.completed)
)

/** P0-2: 已收藏课程 = 选课中已收藏的 + 仅收藏但未选课的 */
const favoritedCourses = computed(() => {
  const enrolled = (enrollments.value || []).filter(e => e.favorited)
  const enrolledIds = new Set(enrolled.map(e => String(e.courseId)))
  const extras = (extraFavorites.value || []).filter(f => !enrolledIds.has(String(f.courseId)))
  return [...enrolled, ...extras]
})

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

/** P0-1 / P1-1: 当前 Tab 过滤后的总数——用于分页器 */
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

/** P2-1: formatTime——使用显式时区转换，兜底处理无效日期 */
const formatTime = (timeStr) => {
  if (!timeStr) return '暂无'
  try {
    const d = new Date(timeStr)
    if (isNaN(d.getTime())) return '暂无'
    // 使用 toLocaleDateString 确保按浏览器本地时区显示
    return d.toLocaleDateString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
    }).replace(/\//g, '-')
  } catch {
    return '暂无'
  }
}

/** P1-2: 封面图加载失败处理——替换为占位图 */
const handleImgError = (event) => {
  if (event?.target) {
    event.target.src = FALLBACK_COVER
    event.target.onerror = null // 防止无限循环
  }
}

/** P2-2: 根据错误类型生成对应的提示消息 */
const getErrorMessage = (err) => {
  if (!err) return '加载课程失败，请稍后重试'
  const status = err?.response?.status
  if (status === 401 || status === 403) return '登录已过期，请重新登录'
  if (status === 500) return '服务器异常，请稍后重试'
  if (status >= 502 && status <= 504) return '服务暂时不可用，请稍后重试'
  if (!navigator.onLine || err?.message === 'Network Error') return '网络连接异常，请检查网络后重试'
  return '加载课程失败，请稍后重试'
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
  loadError.value = false
  dataLoaded.value = false
  try {
    // P0-5: 不再传 userId——后端从 JWT 中获取
    const res = await getMyEnrollments()
    const list = res.data || []

    // P1-5: 使用 Promise.allSettled 替代 Promise.all，防止单个失败导致全部中断
    const completionData = await getCompletion().catch(() => ({}))
    completionMap = completionData?.data || {}

    // R8 P0-3: 批量获取学习进度（替代 per-course N+1）
    const inProgress = list.filter(e => !e.completed)
    const newProgressMap = {}
    if (inProgress.length > 0) {
      const courseIds = inProgress.map(e => e.courseId)
      const batchRes = await batchGetLearningProgress(courseIds).catch(() => null)
      if (batchRes?.data) {
        const progressList = Array.isArray(batchRes.data) ? batchRes.data : [batchRes.data]
        for (const pdata of progressList) {
          if (pdata?.courseId) {
            newProgressMap[pdata.courseId] = {
              completedExercises: pdata.completedExercises ?? pdata.completed ?? 0,
              totalExercises: pdata.totalExercises ?? pdata.total ?? 0,
              completedVideos: pdata.completedVideos ?? 0,
              totalVideos: pdata.totalVideos ?? 0,
              progress: completionMap[pdata.courseId]?.progress ?? inProgress.find(e => e.courseId === pdata.courseId)?.progress ?? 0
            }
          }
        }
      }
    }
    courseProgressMap.value = newProgressMap

    // P1I-020: 章节数据改为懒加载（仅在切换到"进行中"Tab 时触发），避免 N+1 请求阻塞初始渲染
    // videoProgressMap 将在 handleTabChange 中按需填充

    // P0-2: 获取完整收藏列表，补充仅收藏未选课的课程
    let favoriteSet = new Set()
    const newExtraFavorites = []
    try {
      const favRes = await getMyFavorites()
      const favList = favRes?.data || []
      favoriteSet = new Set(favList.map(f => String(f.courseId)))

      // 找出收藏了但不在 enrollments 中的课程
      const enrolledIds = new Set(list.map(e => String(e.courseId)))
      const nonEnrolledFavs = favList.filter(f => !enrolledIds.has(String(f.courseId)))

      // 批量获取这些课程的详细信息
      if (nonEnrolledFavs.length > 0) {
        const courseResults = await Promise.allSettled(
          nonEnrolledFavs.map(f => getCourseById(f.courseId))
        )
        courseResults.forEach((result, idx) => {
          const fav = nonEnrolledFavs[idx]
          if (result.status === 'fulfilled' && result.value?.data) {
            const course = result.value.data
            newExtraFavorites.push({
              courseId: fav.courseId,
              courseTitle: course.title || course.courseTitle || fav.courseTitle || '未知课程',
              coverUrl: course.coverUrl || course.cover || null,
              teacherName: course.teacherName || course.teacher || null,
              favorited: true,
              completed: false,
              progress: 0,
              enrolledAt: fav.createdAt,
              _isFavoriteOnly: true // 标记为仅收藏
            })
          } else {
            // 即使获取课程详情失败，也展示基本信息
            newExtraFavorites.push({
              courseId: fav.courseId,
              courseTitle: fav.courseTitle || '未知课程',
              coverUrl: null,
              teacherName: null,
              favorited: true,
              completed: false,
              progress: 0,
              enrolledAt: fav.createdAt,
              _isFavoriteOnly: true
            })
          }
        })
      }
    } catch (e) {
      // P1-3: getMyFavorites 失败时输出警告日志
      console.warn('[MyCourses] getMyFavorites 失败', e)
    }

    extraFavorites.value = newExtraFavorites
    enrollments.value = list.map(e => {
      const cp = completionMap[e.courseId]
      const updatedProgress = cp?.progress ?? e.progress ?? 0
      return { ...e, progress: updatedProgress, favorited: favoriteSet.has(String(e.courseId)) }
    })
    totalElements.value = enrollments.value.length
    dataLoaded.value = true
    // P1I-020: 初始 Tab 为"进行中"时，立即懒加载视频进度
    if (activeTab.value === 'in-progress') {
      loadVideoProgress()
    }
  } catch (err) {
    // P2-2: 根据 HTTP 状态码显示不同的错误信息
    const message = getErrorMessage(err)
    loadErrorMessage.value = message
    loadError.value = true
    ElMessage.error(message)
  } finally {
    loading.value = false
  }
}

// P1I-020: 按需加载章节视频进度（避免 N+1 请求）
let videoProgressLoaded = false
async function loadVideoProgress() {
  if (videoProgressLoaded) return
  const inProgress = inProgressCourses.value
  if (inProgress.length === 0) return
  const chapterResults = await Promise.allSettled(
    inProgress.map(e => getChapters({ courseId: e.courseId, size: 1000 }))
  )
  const newVideoProgressMap = {}
  chapterResults.forEach((result, idx) => {
    const courseId = inProgress[idx].courseId
    if (result.status === 'fulfilled') {
      const chapters = result.value?.data?.items || result.value?.data || []
      const totalChapters = chapters.length || 0
      const progressEntry = courseProgressMap.value[courseId]
      let completedVideos = 0
      const progressPercent = completionMap?.[courseId]?.progress ?? inProgress[idx].progress ?? 0
      if (progressEntry && totalChapters > 0) {
        completedVideos = progressEntry.completedVideos
          ?? Math.min(Math.round(totalChapters * progressPercent / 100), totalChapters)
      }
      if (totalChapters > 0) {
        newVideoProgressMap[courseId] = {
          total: totalChapters,
          completed: completedVideos,
          percent: progressPercent
        }
      }
    }
  })
  videoProgressMap.value = newVideoProgressMap
  videoProgressLoaded = true
}

const handleTabChange = () => {
  page.value = 1
  // P1I-020: 切换到"进行中"Tab 时懒加载视频进度
  if (activeTab.value === 'in-progress') {
    loadVideoProgress()
  }
}

/** P0-4: H5 Tab 切换——重置页码 */
const handleH5TabChange = (tab) => {
  activeTab.value = tab
  page.value = 1
}

const handleSizeChange = () => {
  page.value = 1
}

const handlePageChange = () => {
  // displayCourses computed 会自动响应 page 变化
}

const handleContinue = async (courseId) => {
  // P1-C: 根据课程 type 决定跳转（互动课件 → SlidePlayer，其他 → LearningView）
  try {
    const res = await getCourseById(courseId)
    if (res.data?.courseType === 'INTERACTIVE') {
      // 直接从后端获取学习进度，取最后学习的章节
      try {
        const progressRes = await getLearningProgress({ courseId })
        const records = progressRes.data || []
        // 取最后一个有进度的章节（lastWatchAt最新的）
        const sorted = records.filter(r => r.chapterId).sort((a, b) =>
          new Date(b.lastWatchAt || 0) - new Date(a.lastWatchAt || 0))
        const lastChapterId = sorted[0]?.chapterId || ''
        const suffix = lastChapterId ? `?chapterId=${lastChapterId}` : ''
        router.push(`/student/courses/${courseId}/slides/player${suffix}`)
      } catch {
        router.push(`/student/courses/${courseId}/slides/player`)
      }
      return
    }
  } catch { /* 查询失败则降级到 learning 页面 */ }
  router.push(`/student/learning?courseId=${courseId}`)
}

// 客户体验修复 v1.7.0: 退课处理 (P0-UX-U4)
// course 对象来自 API 返回的选课记录,course.id = enrollmentId
const handleDropOut = async (course) => {
  if (!course || !course.id) {
    ElMessage.error('退课失败:缺少选课记录 ID')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认退课？退课后课程将不再显示在您的课程列表中`,
      '退课确认',
      { confirmButtonText: '确认退课', cancelButtonText: '取消', type: 'warning' }
    )
    await cancelEnrollment(course.id)
    ElMessage.success('退课成功')
    await fetchEnrollments()
  } catch (e) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error('退课失败: ' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}
</script>

<style scoped>
/* ================================================
   PC 布局
   ================================================ */
.pc-layout {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
}

/* 页面 Header */
.page-header {
  height: 120px;
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

/* P1-4: 错误重试状态 */
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-8) 0;
}

.retry-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
}

/* ================================================
   H5 布局
   ================================================ */
.h5-layout {
  min-height: 100dvh;
  background: var(--el-bg-color);
  padding-bottom: 56px;
}

/* 紧凑 Header */
.h5-header {
  height: 80px;
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

/* 客户体验修复 v1.7.0: H5 退课按钮 + 主按钮并排 */
.h5-action-row {
  display: flex;
  gap: var(--space-2, 8px);
  align-items: center;
}

.h5-action-row .h5-action-btn {
  flex: 1;  /* 主按钮占主要宽度 */
}

.h5-action-row .h5-dropout-btn {
  flex: 0 0 auto;  /* 退课按钮固定宽度 */
  min-width: 64px;
  min-height: 44px;  /* P1-C 修复: Apple HIG 44px 最小触控目标 */
}

/* P1-C 修复 Round 3: iPhone 用户点错问题 (客户体验报告 P1-4) */
.h5-action-row .el-button {
  min-height: 44px;
}

/* P2-6: H5 底部安全区——适配 iPhone 等圆角底部设备 */
.h5-bottom-safe {
  height: calc(56px + env(safe-area-inset-bottom, 0px));
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
.danger-icon { color: var(--el-color-danger); }
</style>
