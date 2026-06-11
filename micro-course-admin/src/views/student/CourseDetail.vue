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
      <div v-if="course.coverUrl" class="banner-cover">
        <img :src="course.coverUrl" :alt="course.title" />
      </div>
    </div>

    <!-- 内容区 -->
    <div class="course-content">
      <!-- 左侧：课程信息 + 大纲 -->
      <div class="main-column">
        <!-- 课程描述 -->
        <el-card class="desc-card" shadow="never">
          <template #header>
            <span class="section-title">课程介绍</span>
          </template>
          <p class="course-description">{{ course.description || '暂无课程介绍' }}</p>
        </el-card>

        <!-- 大纲列表 -->
        <el-card class="chapter-card" shadow="never">
          <template #header>
            <span class="section-title">课程大纲</span>
          </template>
          <el-table
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

      <!-- 右侧：教师信息 -->
      <div class="side-column">
        <el-card class="teacher-card" shadow="never">
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
        <el-card class="companions-card" shadow="never">
          <div class="companions-tip">
            <el-icon><User /></el-icon>
            <span>当前 <strong>{{ studentCount }}</strong> 名同学在学习</span>
          </div>
        </el-card>
      </div>
    </div>

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
import { enroll as enrollApi, getMyEnrollments } from '@/api/enrollment'
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

const isLoggedIn = computed(() => userStore.isLoggedIn)

// 格式化时长：分钟 → XhYm
const formatDuration = (minutes) => {
  if (!minutes && minutes !== 0) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h === 0) return `${m}m`
  if (m === 0) return `${h}h`
  return `${h}h${m}m`
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

const goLogin = () => {
  router.push('/login')
}

const goLearn = () => {
  router.push(`/student/courses/${courseId.value}`)
}

onMounted(async () => {
  await fetchCourse()
  await Promise.all([
    fetchChapters(),
    fetchTeacher(),
    checkEnrollment()
  ])
})
</script>

<style scoped>
.course-detail-page {
  padding-bottom: 80px;
}

/* Banner 区 */
.course-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 48px;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.banner-content {
  flex: 1;
  max-width: 600px;
}

.course-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px;
  line-height: 1.3;
}

.course-subtitle {
  font-size: 16px;
  margin: 0 0 16px;
  opacity: 0.9;
}

.course-meta {
  display: flex;
  gap: 20px;
  font-size: 14px;
}

.course-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.banner-cover {
  width: 280px;
  height: 180px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 8px 24px rgba(0,0,0,0.3);
}

.banner-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* 内容区 */
.course-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
  display: flex;
  gap: 24px;
}

.main-column {
  flex: 7;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-column {
  flex: 3;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 卡片通用 */
:deep(.el-card) {
  border-radius: 8px;
}

:deep(.el-card__header) {
  padding: 14px 20px;
  border-bottom: 1px solid #f0f0f0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.course-description {
  font-size: 14px;
  color: #606266;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}

/* 教师卡片 */
.teacher-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}

.teacher-avatar {
  margin-bottom: 12px;
}

.teacher-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.teacher-bio {
  font-size: 13px;
  color: #909399;
  text-align: center;
  line-height: 1.6;
}

/* 学习同伴 */
.companions-card {
  background: #f5f7fa;
}

.companions-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
}

.companions-tip strong {
  color: #667eea;
  font-size: 16px;
}

/* 底部固定栏 */
.fixed-bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  border-top: 1px solid #e4e7ed;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.08);
  z-index: 100;
}

.bar-inner {
  max-width: 960px;
  margin: 0 auto;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-greeting {
  font-size: 14px;
  color: #909399;
}

.action-btns {
  display: flex;
  gap: 12px;
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

  .course-content {
    flex-direction: column;
    padding: 16px 12px;
  }

  .main-column,
  .side-column {
    flex: unset;
    width: 100%;
  }
}
</style>