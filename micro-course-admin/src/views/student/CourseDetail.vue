<!--
  课程详情
  路由路径: /student/course/:id
  Phase 2
  Author: jackie
-->
<template>
  <div class="course-detail-page">
    <!-- Banner 区 -->
    <div class="course-banner">
      <div class="banner-content">
        <h1 class="course-title">{{ course.title }}</h1>
        <p class="course-subtitle">{{ course.subtitle }}</p>
        <div class="course-meta">
          <span v-if="course.avgRating">
            <el-icon><Star /></el-icon> {{ course.avgRating.toFixed(1) }}
          </span>
          <span v-if="course.studentCount">
            <el-icon><User /></el-icon> {{ course.studentCount }} 名学生
          </span>
        </div>
      </div>
      <div v-if="course.coverUrl && !isMobile" class="banner-cover">
        <img :src="course.coverUrl" :alt="course.title" />
      </div>
    </div>

    <!-- PC 布局 -->
    <div v-if="!isMobile" class="pc-layout">
      <!-- 内容区 -->
      <div class="course-content">
        <!-- 左侧：课程信息 + 大纲 -->
        <div class="main-column">
          <!-- 课程描述 -->
          <el-card class="desc-card card-hover">
            <template #header>
              <span class="section-title">课程介绍</span>
            </template>
            <p class="course-description">{{ course.description || '暂无课程介绍' }}</p>
          </el-card>

          <!-- 大纲列表 -->
          <el-card class="chapter-card card-hover">
            <template #header>
              <span class="section-title">课程大纲</span>
            </template>
            <el-table highlight-current-row @row-click="handleChapterClick"
              v-loading="chapterLoading"
              :data="chapters"
              stripe
              border
            >
              <el-table-column type="index" label="#" width="60" align="center" />
              <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
              <el-table-column prop="title" label="章节标题" min-width="160" show-overflow-tooltip />
              <el-table-column prop="chapterType" label="类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.chapterType === 'VIDEO'"
                    type="primary"
                    size="small"
                    effect="plain"
                  >
                    视频
                  </el-tag>
                  <el-tag
                    v-else-if="row.chapterType === 'EXERCISE'"
                    type="success"
                    size="small"
                    effect="plain"
                  >
                    练习
                  </el-tag>
                  <el-tag
                    v-else
                    type="info"
                    size="small"
                    effect="plain"
                  >
                    {{ row.chapterType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="duration" label="时长" width="100" align="center">
                <template #default="{ row }">
                  {{ formatDuration(row.duration) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 评价区 -->
          <el-card class="review-card card-hover">
            <template #header>
              <div class="review-header">
                <span class="section-title">课程评价</span>
                <el-button type="primary" size="small" text @click="openReviewDialog">
                  写评价
                </el-button>
              </div>
            </template>
            <div v-loading="reviewLoading" class="review-list">
              <template v-if="reviews.length > 0">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-user">
                    <el-avatar :size="32" :src="r.userAvatar">{{ r.userRealName?.charAt(0) }}</el-avatar>
                    <span class="review-username">{{ r.userRealName || '匿名用户' }}</span>
                  </div>
                  <el-rate v-model="r.rating" disabled size="small" class="review-rating" />
                  <p class="review-content">{{ r.content }}</p>
                  <span class="review-time">{{ formatTime(r.createdAt) }}</span>
                </div>
              </template>
              <el-empty v-else description="暂无评价，来说第一条吧！" />
            </div>
          </el-card>
        </div>

        <!-- 右侧：教师信息 -->
        <div class="side-column">
          <!-- 教师卡片 -->
          <el-card class="teacher-card card-hover">
            <template #header>
              <span class="section-title">授课教师</span>
            </template>
            <div v-loading="teacherLoading" class="teacher-info">
              <template v-if="teacher.id">
                <div class="teacher-avatar">
                  <el-avatar :size="72" :src="teacher.avatar">
                    {{ teacher.realName?.charAt(0) }}
                  </el-avatar>
                </div>
                <div class="teacher-name">{{ teacher.realName }}</div>
                <div class="teacher-bio">{{ teacher.bio || teacher.introduction || '暂无教师简介' }}</div>
              </template>
              <el-empty v-else description="加载教师信息中..." :image-size="60" />
            </div>
          </el-card>

          <!-- 学习同伴 -->
          <el-card class="companions-card card-hover">
            <div class="companions-tip">
              <el-icon><User /></el-icon>
              <span>当前 <strong>{{ studentCount }}</strong> 名同学在学习</span>
            </div>
          </el-card>

          <!-- 活跃排行 -->
          <el-card class="ranking-card card-hover">
            <template #header>
              <span class="section-title">学习排行</span>
            </template>
            <div v-loading="rankingLoading" class="ranking-list">
              <template v-if="rankingList.length > 0">
                <div
                  v-for="item in rankingList"
                  :key="item.rank"
                  class="ranking-item"
                  :class="{ 'ranking-current': item.isCurrentUser }"
                >
                  <span class="rank-num" :class="{ 'rank-top': item.rank <= 3 }">
                    {{ item.rank }}
                  </span>
                  <span class="rank-name">{{ item.userName }}</span>
                  <span class="rank-progress">{{ (item.progress * 100).toFixed(0) }}%</span>
                </div>
              </template>
              <el-empty v-else description="暂无排行数据" :image-size="60" />
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- H5 布局 -->
    <div v-else class="h5-layout">
      <!-- Tab 栏 -->
      <div class="h5-tabs">
        <div
          class="h5-tab"
          :class="{ active: h5ActiveTab === 'intro' }"
          @click="h5ActiveTab = 'intro'"
        >
          介绍
        </div>
        <div
          class="h5-tab"
          :class="{ active: h5ActiveTab === 'chapters' }"
          @click="h5ActiveTab = 'chapters'"
        >
          目录
        </div>
        <div
          class="h5-tab"
          :class="{ active: h5ActiveTab === 'reviews' }"
          @click="h5ActiveTab = 'reviews'"
        >
          评价
        </div>
      </div>

      <!-- Tab 内容 -->
      <div class="h5-content">
        <!-- 介绍 -->
        <div v-show="h5ActiveTab === 'intro'" class="h5-tab-content">
          <el-card class="desc-card card-hover">
            <p class="course-description">{{ course.description || '暂无课程介绍' }}</p>
          </el-card>
          <el-card class="teacher-card card-hover">
            <template #header>
              <span class="section-title">授课教师</span>
            </template>
            <div v-loading="teacherLoading" class="teacher-info">
              <template v-if="teacher.id">
                <div class="teacher-avatar">
                  <el-avatar :size="72" :src="teacher.avatar">
                    {{ teacher.realName?.charAt(0) }}
                  </el-avatar>
                </div>
                <div class="teacher-name">{{ teacher.realName }}</div>
                <div class="teacher-bio">{{ teacher.bio || teacher.introduction || '暂无教师简介' }}</div>
              </template>
              <el-empty v-else description="加载教师信息中..." :image-size="60" />
            </div>
          </el-card>
          <el-card class="companions-card card-hover">
            <div class="companions-tip">
              <el-icon><User /></el-icon>
              <span>当前 <strong>{{ studentCount }}</strong> 名同学在学习</span>
            </div>
          </el-card>
        </div>

        <!-- 目录 -->
        <div v-show="h5ActiveTab === 'chapters'" class="h5-tab-content">
          <el-card class="chapter-card card-hover">
            <el-table highlight-current-row @row-click="handleChapterClick"
              v-loading="chapterLoading"
              :data="chapters"
              stripe
              border
            >
              <el-table-column type="index" label="#" width="60" align="center" />
              <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
              <el-table-column prop="title" label="章节标题" min-width="160" show-overflow-tooltip />
              <el-table-column prop="chapterType" label="类型" width="120" align="center">
                <template #default="{ row }">
                  <el-tag
                    v-if="row.chapterType === 'VIDEO'"
                    type="primary"
                    size="small"
                    effect="plain"
                  >
                    视频
                  </el-tag>
                  <el-tag
                    v-else-if="row.chapterType === 'EXERCISE'"
                    type="success"
                    size="small"
                    effect="plain"
                  >
                    练习
                  </el-tag>
                  <el-tag
                    v-else
                    type="info"
                    size="small"
                    effect="plain"
                  >
                    {{ row.chapterType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="duration" label="时长" width="100" align="center">
                <template #default="{ row }">
                  {{ formatDuration(row.duration) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>

        <!-- 评价 -->
        <div v-show="h5ActiveTab === 'reviews'" class="h5-tab-content">
          <el-card class="review-card card-hover">
            <template #header>
              <div class="review-header">
                <span class="section-title">课程评价</span>
                <el-button type="primary" size="small" text @click="openReviewDialog">
                  写评价
                </el-button>
              </div>
            </template>
            <div v-loading="reviewLoading" class="review-list">
              <template v-if="reviews.length > 0">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-user">
                    <el-avatar :size="32" :src="r.userAvatar">{{ r.userRealName?.charAt(0) }}</el-avatar>
                    <span class="review-username">{{ r.userRealName || '匿名用户' }}</span>
                  </div>
                  <el-rate v-model="r.rating" disabled size="small" class="review-rating" />
                  <p class="review-content">{{ r.content }}</p>
                  <span class="review-time">{{ formatTime(r.createdAt) }}</span>
                </div>
              </template>
              <el-empty v-else description="暂无评价，来说第一条吧！" />
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 写评价弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="写课程评价" class="review-dialog">
      <el-form :model="reviewForm" :rules="reviewRules" label-width="80px">
        <el-form-item label="评分" prop="rating">
          <el-rate v-model="reviewForm.rating" />
        </el-form-item>
        <el-form-item label="评价内容" prop="content">
          <el-input
            v-model="reviewForm.content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="分享你的学习心得..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSubmitting" @click="handleSubmitReview">
          提交评价
        </el-button>
      </template>
    </el-dialog>

    <!-- 底部固定操作栏 -->
    <div class="fixed-bottom-bar">
      <div class="bar-inner">
        <span v-if="isLoggedIn" class="user-greeting">欢迎学习，{{ userStore.realName }}</span>
        <span v-else class="user-greeting">未登录</span>
        <div class="action-btns">
          <template v-if="!isLoggedIn">
            <el-button type="primary" @click="goLogin">请先登录</el-button>
          </template>
          <template v-else-if="isEnrolled">
            <el-button type="primary" @click="goLearn">继续学习</el-button>
          </template>
          <template v-else>
            <el-button type="primary" :loading="enrollLoading" @click="handleEnroll">
              立即报名
            </el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Star, User } from '@element-plus/icons-vue'
import { getCourseById } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getUserById } from '@/api/user'
import { enroll as enrollApi, getMyEnrollments, getCourseRanking } from '@/api/enrollment'
import { createReview, getReviews } from '@/api/course-review'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const courseId = computed(() => route.params.id)

const course = ref({})
const chapters = ref([])
const teacher = ref({})
const studentCount = computed(() => course.value.studentCount || 0)

const chapterLoading = ref(false)
const teacherLoading = ref(false)
const enrollLoading = ref(false)
const isEnrolled = ref(false)
const reviewLoading = ref(false)
const reviews = ref([])
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const rankingLoading = ref(false)
const rankingList = ref([])

const reviewForm = ref({
  rating: 5,
  content: ''
})

const reviewRules = {
  rating: [{ required: true, message: '请选择评分', trigger: 'change' }],
  content: [{ max: 500, message: '评价内容不超过500字', trigger: 'blur' }]
}

const isLoggedIn = computed(() => userStore.isLoggedIn)

// H5 Tab 状态
const h5ActiveTab = ref('intro')

// 响应式：移动端判断
const isMobile = ref(window.innerWidth <= 768)
const handleResize = () => {
  isMobile.value = window.innerWidth <= 768
}
window.addEventListener('resize', handleResize)

// 格式化时长：分钟 → XhYm
const formatDuration = (minutes) => {
  if (!minutes && minutes !== 0) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return ''
  const d = new Date(time)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 获取课程信息
const fetchCourse = async () => {
  if (!courseId.value) return
  try {
    const { data } = await getCourseById(courseId.value)
    course.value = data || {}
  } catch {
    ElMessage.error('获取课程信息失败')
  }
}

// 获取章节列表
const fetchChapters = async () => {
  if (!courseId.value) return
  chapterLoading.value = true
  try {
    const { data } = await getChapters({ courseId: courseId.value })
    chapters.value = data?.items || data || []
  } catch {
    ElMessage.error('获取章节列表失败')
  } finally {
    chapterLoading.value = false
  }
}

// 获取教师信息
const fetchTeacher = async () => {
  if (!course.value.teacherId) return
  teacherLoading.value = true
  try {
    const { data } = await getUserById(course.value.teacherId)
    teacher.value = data || {}
  } catch {
    teacher.value = {}
  } finally {
    teacherLoading.value = false
  }
}

// 检查当前用户是否已报名
const checkEnrollment = async () => {
  if (!isLoggedIn.value || !courseId.value) return
  const userId = userStore.userInfo?.id
  if (!userId) return
  try {
    const { data } = await getMyEnrollments(userId)
    const list = Array.isArray(data) ? data : (data?.items || [])
    isEnrolled.value = list.some(e => String(e.courseId) === String(courseId.value))
  } catch {
    isEnrolled.value = false
  }
}

// 报名
const handleEnroll = async () => {
  if (!isLoggedIn.value) {
    goLogin()
    return
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('用户信息未加载，请刷新重试')
    return
  }
  enrollLoading.value = true
  try {
    await enrollApi({ userId, courseId: courseId.value })
    ElMessage.success('报名成功')
    isEnrolled.value = true
  } catch {
    ElMessage.error('报名失败，请重试')
  } finally {
    enrollLoading.value = false
  }
}

const handleChapterClick = (row) => {
  if (row.chapterType === 'EXERCISE') {
    router.push(`/student/chapters/${row.id}/exercises`)
  } else {
    router.push(`/student/courses/${courseId.value}?chapterId=${row.id}`)
  }
}

const goLogin = () => {
  router.push('/login')
}

const goLearn = () => {
  router.push(`/student/courses/${courseId.value}`)
}

// 获取课程评价
const fetchReviews = async () => {
  if (!courseId.value) return
  reviewLoading.value = true
  try {
    const { data } = await getReviews(courseId.value, { page: 0, size: 5 })
    reviews.value = data?.items || data || []
  } catch {
    reviews.value = []
  } finally {
    reviewLoading.value = false
  }
}

// 获取课程排行
const fetchRanking = async () => {
  if (!courseId.value) return
  rankingLoading.value = true
  try {
    const { data } = await getCourseRanking(courseId.value, { limit: 10 })
    rankingList.value = data || []
  } catch {
    rankingList.value = []
  } finally {
    rankingLoading.value = false
  }
}

// 打开写评价弹窗
const openReviewDialog = () => {
  if (!isLoggedIn.value) {
    goLogin()
    return
  }
  reviewForm.value = { rating: 5, content: '' }
  reviewDialogVisible.value = true
}

// 提交评价
const handleSubmitReview = async () => {
  if (!reviewForm.value.rating) {
    ElMessage.warning('请选择评分')
    return
  }
  reviewSubmitting.value = true
  try {
    await createReview(courseId.value, {
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    ElMessage.success('评价提交成功')
    reviewDialogVisible.value = false
    fetchReviews()
  } catch {
    ElMessage.error('提交失败，请重试')
  } finally {
    reviewSubmitting.value = false
  }
}

onMounted(async () => {
  await fetchCourse()
  await Promise.all([
    fetchChapters(),
    fetchTeacher(),
    checkEnrollment(),
    fetchReviews(),
    fetchRanking()
  ])
})
</script>

<style scoped>
.course-detail-page {
  padding-bottom: 80px;
}

/* Banner 区 */
.course-banner {
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 100%);
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-8);
  color: #f5f5f5;
  position: relative;
  overflow: hidden;
}

.banner-content {
  flex: 1;
  max-width: 600px;
}

.course-title {
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  margin: 0 0 var(--space-3);
  line-height: 1.3;
}

.course-subtitle {
  font-size: var(--text-base);
  margin: 0 0 var(--space-4);
  opacity: 0.9;
}

.course-meta {
  display: flex;
  gap: var(--space-5);
  font-size: var(--text-sm);
}

.course-meta span {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.banner-cover {
  width: 280px;
  height: 180px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.banner-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* PC 布局 */
.pc-layout .course-content {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-6) var(--space-4);
  display: flex;
  gap: var(--space-6);
}

.main-column {
  flex: 7;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.side-column {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* H5 布局 */
.h5-layout {
  padding-bottom: 80px;
}

.h5-tabs {
  display: flex;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.h5-tab {
  flex: 1;
  padding: 12px 0;
  text-align: center;
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: all var(--duration-base) var(--ease-out);
  border-bottom: 2px solid transparent;
}

.h5-tab.active {
  color: var(--role-primary);
  border-bottom-color: var(--role-primary);
}

.h5-content {
  padding: 16px 12px;
}

.h5-tab-content {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

/* 评价卡片 */
.review-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.review-item {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 12px;
}

.review-item:last-child {
  border-bottom: none;
}

.review-user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}

.review-username {
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.review-rating {
  margin-bottom: var(--space-1);
}

.review-content {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  line-height: var(--leading-relaxed);
  margin: var(--space-1) 0 0;
  white-space: pre-wrap;
}

.review-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
}

/* 卡片通用 */
:deep(.el-card) {
  border-radius: var(--radius-lg);
  transition: box-shadow var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}

:deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

/* 卡片 hover 效果 */
:deep(.el-card.card-hover:hover) {
  box-shadow: var(--shadow-lg);
  transform: translateY(-2px);
}

/* 表格样式 */
:deep(.el-table) {
  border-radius: var(--radius-lg);
  overflow: hidden;
}

/* 对话框样式 */
:deep(.review-dialog) {
  max-width: 500px;
}

/* 按钮样式 */
:deep(.el-button) {
  cursor: pointer;
}

.section-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.course-description {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}

/* 教师卡片 */
.teacher-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-2) 0;
}

.teacher-avatar {
  margin-bottom: var(--space-3);
}

.teacher-name {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-2);
}

.teacher-bio {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  text-align: center;
  line-height: var(--leading-relaxed);
}

/* 学习同伴 */
.companions-card {
  background: var(--role-primary-light);
}

.companions-tip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.companions-tip strong {
  color: var(--role-primary);
  font-size: var(--text-base);
}

/* 排行卡片 */
.ranking-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.ranking-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-lg);
  background: var(--el-fill-color-light);
  transition: all var(--duration-base) var(--ease-out);
}

.ranking-item.ranking-current {
  background: var(--el-fill-color-light);
  border: 1px solid var(--role-primary);
}

.rank-num {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-circle);
  background: var(--el-fill-color);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

.rank-num.rank-top {
  background: linear-gradient(135deg, var(--role-primary) 0%, var(--role-primary-dark) 100%);
  color: #f5f5f5;
}

.ranking-item.ranking-current .rank-num {
  background: var(--role-primary);
  color: #f5f5f5;
}

.rank-name {
  flex: 1;
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-progress {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}

/* 底部固定栏 */
.fixed-bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: var(--el-bg-color-page);
  border-top: 1px solid var(--el-border-color);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
  z-index: 100;
}

.bar-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-3) var(--space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-greeting {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.action-btns {
  display: flex;
  gap: var(--space-3);
}

/* 响应式 */
@media (max-width: 768px) {
  .course-banner {
    height: auto;
    padding: 24px 20px;
    flex-direction: column;
    align-items: flex-start;
  }

  .banner-cover {
    display: none;
  }

  .pc-layout .course-content {
    flex-direction: column;
    padding: 16px 12px;
  }

  .main-column,
  .side-column {
    flex: unset;
    width: 100%;
  }

  .fixed-bottom-bar {
    padding: var(--space-3) var(--space-3);
  }

  .bar-inner {
    flex-direction: column;
    gap: var(--space-3);
    align-items: flex-start;
  }

  .action-btns {
    width: 100%;
    justify-content: flex-end;
  }
}

/* 章节表格行可点击 */
.chapter-card :deep(.el-table__row) {
  cursor: pointer;
}

.chapter-card :deep(.el-table__row:hover) {
  background-color: var(--el-fill-color-lighter);
}
</style>