<!--
  微专业详情页
  路由: /student/micro-specialties/:id
  Phase 10
-->
<template>
  <div class="ms-detail fade-in">
    <!-- Loading -->
    <div v-if="loading" v-loading="loading" class="ms-detail-loading">
      <el-skeleton animated>
        <template #template>
          <div class="sk-banner" />
          <div class="sk-row">
            <el-skeleton-item variant="text" style="width: 60%; height: 24px;" />
            <el-skeleton-item variant="text" style="width: 40%; height: 24px;" />
            <el-skeleton-item variant="text" style="width: 50%; height: 24px;" />
          </div>
          <div class="sk-tabs">
            <el-skeleton-item variant="text" style="width: 100%; height: 200px;" />
          </div>
        </template>
      </el-skeleton>
    </div>

    <!-- Error -->
    <el-result
      v-else-if="error"
      icon="error"
      title="加载失败"
      sub-title="网络异常，请稍后重试"
      class="ms-detail-error"
    >
      <template #extra>
        <el-button type="primary" @click="fetchDetail">重试</el-button>
      </template>
    </el-result>

    <!-- Not Found -->
    <el-result
      v-else-if="!ms"
      icon="warning"
      title="微专业不存在"
      sub-title="该微专业可能已被下架或删除"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/student/courses')">返回课程广场</el-button>
      </template>
    </el-result>

    <!-- Content -->
    <template v-else>
      <!-- Breadcrumb -->
      <el-page-header @back="$router.back()" class="ms-page-header">
        <template #content>
          <span class="bc-path">
            <span class="bc-link" @click="$router.push('/student/courses')">课程广场</span>
            <span class="bc-sep">/</span>
            <span class="bc-current">{{ ms.title }}</span>
          </span>
        </template>
      </el-page-header>

      <!-- Cover Banner -->
      <div class="ms-banner">
        <img v-if="ms.coverUrl" :src="ms.coverUrl" :alt="ms.title" class="ms-banner-img" />
        <div v-else class="ms-banner-placeholder">
          <el-icon :size="64"><Notebook /></el-icon>
        </div>
        <div class="ms-banner-overlay">
          <h1 class="ms-banner-title">{{ ms.title }}</h1>
          <el-tag
            :type="statusTagType"
            size="large"
            class="ms-status-tag"
          >
            {{ statusLabel }}
          </el-tag>
        </div>
      </div>

      <!-- Basic Info Row -->
      <el-card shadow="never" class="ms-info-card">
        <div class="ms-info-row">
          <div class="ms-info-item">
            <span class="ms-info-label">所属学院</span>
            <span class="ms-info-value">{{ ms.departmentName || '—' }}</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">负责人</span>
            <span class="ms-info-value">{{ ms.leadTeacherName || '—' }}</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">总学分</span>
            <span class="ms-info-value">{{ ms.totalCredits || 0 }} 分</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">总学时</span>
            <span class="ms-info-value">{{ ms.totalHours || 0 }} 小时</span>
          </div>
          <div class="ms-info-item" v-if="stats">
            <span class="ms-info-label">选课率</span>
            <span class="ms-info-value">{{ stats.enrollmentRate || '0%' }}</span>
          </div>
        </div>
      </el-card>

      <!-- Tabs -->
      <el-card shadow="never" class="ms-tabs-card">
        <el-tabs v-model="activeTab" class="ms-tabs">
          <el-tab-pane label="培养方案" name="courses">
            <!-- Loading -->
            <div v-if="coursesLoading" class="ms-tab-loading">
              <el-skeleton :count="3" animated />
            </div>
            <!-- Error -->
            <el-result
              v-else-if="coursesError"
              icon="error"
              title="课程加载失败"
              sub-title="请稍后重试"
            >
              <template #extra>
                <el-button type="primary" @click="fetchCourses">重试</el-button>
              </template>
            </el-result>
            <!-- Empty -->
            <el-empty v-else-if="!courses.length" description="暂无课程安排" />
            <!-- Course List -->
            <div v-else class="ms-course-list">
              <div
                v-for="(item, i) in courses"
                :key="item.id"
                class="ms-course-item"
                :class="{ 'ms-course-item--required': item.isRequired }"
                role="button"
                tabindex="0"
                @click="goCourse(item.courseId)"
                @keydown.enter="goCourse(item.courseId)"
              >
                <span class="ms-course-order">{{ i + 1 }}</span>
                <div class="ms-course-info">
                  <span class="ms-course-title">
                    {{ item.courseTitle }}
                    <el-tag v-if="item.isRequired" type="danger" size="small">必修</el-tag>
                    <el-tag v-else type="info" size="small">选修</el-tag>
                  </span>
                  <span class="ms-course-meta">
                    {{ item.teacherName || '—' }} · {{ item.creditHours || 0 }} 学分
                  </span>
                </div>
                <div class="ms-course-status">
                  <el-tag v-if="item.enrollmentStatus === 'ENROLLED'" type="success" size="small">已修读</el-tag>
                  <el-tag v-else-if="item.enrollmentStatus === 'IN_PROGRESS'" type="warning" size="small">进行中</el-tag>
                </div>
                <el-icon class="ms-go-icon"><ArrowRight /></el-icon>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="教师团队" name="teachers">
            <!-- Loading -->
            <div v-if="teachersLoading" class="ms-tab-loading">
              <el-skeleton :count="3" animated />
            </div>
            <!-- Error -->
            <el-result
              v-else-if="teachersError"
              icon="error"
              title="教师信息加载失败"
              sub-title="请稍后重试"
            >
              <template #extra>
                <el-button type="primary" @click="fetchTeachers">重试</el-button>
              </template>
            </el-result>
            <!-- Empty -->
            <el-empty v-else-if="!teachers.length" description="暂无教师信息" />
            <!-- Teacher List -->
            <div v-else class="ms-teacher-list">
              <div
                v-for="t in teachers"
                :key="t.id || t.teacherId"
                class="ms-teacher-item"
              >
                <el-avatar :size="48" :src="t.avatarUrl">
                  <el-icon :size="24"><User /></el-icon>
                </el-avatar>
                <div class="ms-teacher-info">
                  <span class="ms-teacher-name">{{ t.teacherName || t.name }}</span>
                  <span class="ms-teacher-role">
                    <el-tag v-if="t.role === 'LEAD'" type="primary" size="small">负责人</el-tag>
                    <el-tag v-else size="small">授课教师</el-tag>
                  </span>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="详细介绍" name="desc">
            <div class="ms-desc-content">
              <p v-if="ms.objectives" class="ms-desc-section">
                <strong>培养目标</strong>
                <span>{{ ms.objectives }}</span>
              </p>
              <p v-if="ms.description" class="ms-desc-section">
                <strong>项目介绍</strong>
                <span>{{ ms.description }}</span>
              </p>
              <p v-if="ms.requirements" class="ms-desc-section">
                <strong>报名要求</strong>
                <span>{{ ms.requirements }}</span>
              </p>
              <el-empty v-if="!ms.objectives && !ms.description && !ms.requirements" description="暂无详细介绍" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- Bottom Fixed CTA Bar -->
      <div class="ms-bottom-bar">
        <div class="ms-bottom-bar-inner">
          <div class="ms-bottom-info">
            <span class="ms-bottom-credit">{{ ms.totalCredits || 0 }} 学分</span>
            <span class="ms-bottom-sep">|</span>
            <span class="ms-bottom-count">{{ ms.courseCount || 0 }} 门课程</span>
          </div>
          <div class="ms-bottom-actions">
            <!-- 未登录 -->
            <el-button v-if="!isLoggedIn" type="primary" size="large" @click="goLogin">
              请先登录
            </el-button>
            <!-- FAILED / DROPPED / REJECTED → 重新申请 -->
            <el-button
              v-else-if="['FAILED', 'DROPPED', 'REJECTED'].includes(enrollmentStatus)"
              type="primary"
              size="large"
              :loading="reapplyLoading"
              @click="handleReapply"
            >
              重新申请
            </el-button>
            <!-- 已报名/进行中 -->
            <el-button
              v-else-if="['ENROLLED', 'IN_PROGRESS'].includes(enrollmentStatus)"
              size="large"
              disabled
            >
              已报名
            </el-button>
            <!-- 已结业 -->
            <el-button
              v-else-if="enrollmentStatus === 'COMPLETED'"
              type="success"
              size="large"
              disabled
            >
              已结业
            </el-button>
            <!-- 已报名但已退出 -->
            <el-button
              v-else-if="enrollmentStatus === 'WITHDRAWN'"
              type="primary"
              size="large"
              :loading="reapplyLoading"
              @click="handleReapply"
            >
              重新报名
            </el-button>
            <!-- 未报名 -->
            <el-button
              v-else
              type="primary"
              size="large"
              :loading="applyLoading"
              :disabled="!canEnroll"
              @click="handleApply"
            >
              {{ canEnroll ? '立即报名' : '当前不可报名' }}
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Notebook, User } from '@element-plus/icons-vue'
import {
  getMicroSpecialtyDetail,
  getCourses,
  getTeachers,
  getStats
} from '@/api/microSpecialty'
import {
  applyEnrollment,
  getMyEnrollments,
  reapplyEnrollment
} from '@/api/microSpecialty'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const msId = computed(() => route.params.id)
const ms = ref(null)
const stats = ref(null)
const loading = ref(false)
const error = ref(false)
const activeTab = ref('courses')

// Courses tab
const courses = ref([])
const coursesLoading = ref(false)
const coursesError = ref(false)

// Teachers tab
const teachers = ref([])
const teachersLoading = ref(false)
const teachersError = ref(false)

// Enrollment
const enrollmentStatus = ref(null)
const applyLoading = ref(false)
const reapplyLoading = ref(false)

const isLoggedIn = computed(() => !!userStore.token)

const canEnroll = computed(() => {
  if (!ms.value) return false
  return ms.value.status === 'OPEN' || ms.value.status === 'RECRUITING'
})

const statusLabel = computed(() => {
  if (!ms.value) return ''
  const map = {
    DRAFT: '草稿',
    PENDING: '审核中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    OPEN: '招生中',
    CLOSED: '已截止',
    ARCHIVED: '已归档',
    RECRUITING: '招生中',
    COMPLETED: '已结业'
  }
  return map[ms.value.status] || ms.value.status || '—'
})

const statusTagType = computed(() => {
  if (!ms.value) return 'info'
  const map = {
    OPEN: 'success',
    RECRUITING: 'success',
    CLOSED: 'warning',
    COMPLETED: 'info',
    REJECTED: 'danger',
    DRAFT: 'info',
    PENDING: 'warning',
    APPROVED: 'success',
    ARCHIVED: 'info'
  }
  return map[ms.value.status] || 'info'
})

// 获取详情
const fetchDetail = async () => {
  loading.value = true
  error.value = false
  try {
    const [detailRes, statsRes] = await Promise.all([
      getMicroSpecialtyDetail(msId.value),
      getStats(msId.value).catch(() => ({ data: null }))
    ])
    ms.value = detailRes.data
    stats.value = statsRes.data
  } catch (e) {
    console.error('[MSDetail] 加载详情失败:', e)
    error.value = true
  } finally {
    loading.value = false
  }
}

// 获取课程
const fetchCourses = async () => {
  coursesLoading.value = true
  coursesError.value = false
  try {
    const { data } = await getCourses(msId.value)
    courses.value = data || []
  } catch (e) {
    console.error('[MSDetail] 加载课程失败:', e)
    coursesError.value = true
  } finally {
    coursesLoading.value = false
  }
}

// 获取教师
const fetchTeachers = async () => {
  teachersLoading.value = true
  teachersError.value = false
  try {
    const { data } = await getTeachers(msId.value)
    teachers.value = data || []
  } catch (e) {
    console.error('[MSDetail] 加载教师失败:', e)
    teachersError.value = true
  } finally {
    teachersLoading.value = false
  }
}

// 检查报名状态
const checkEnrollment = async () => {
  if (!isLoggedIn.value) return
  try {
    const { data } = await getMyEnrollments()
    const enrollments = data?.items || data || []
    const found = enrollments.find(
      e => String(e.microSpecialtyId) === String(msId.value)
    )
    if (found) {
      enrollmentStatus.value = found.status
    }
  } catch (e) {
    console.warn('[MSDetail] 检查报名状态失败:', e)
  }
}

// 报名
const handleApply = async () => {
  try {
    await ElMessageBox.confirm(
      `确认报名微专业「${ms.value.title}」？`,
      '报名确认',
      { confirmButtonText: '确认报名', cancelButtonText: '取消', type: 'info' }
    )
    applyLoading.value = true
    await applyEnrollment({ microSpecialtyId: msId.value })
    ElMessage.success('报名成功')
    enrollmentStatus.value = 'ENROLLED'
  } catch (e) {
    if (e !== 'cancel') {
      console.error('[MSDetail] 报名失败:', e)
      ElMessage.error(e?.response?.data?.message || '报名失败，请稍后重试')
    }
  } finally {
    applyLoading.value = false
  }
}

// 重新申请
const handleReapply = async () => {
  try {
    await ElMessageBox.confirm(
      '确认重新申请该微专业？',
      '重新申请',
      { confirmButtonText: '确认', cancelButtonText: '取消', type: 'info' }
    )
    reapplyLoading.value = true
    await reapplyEnrollment(msId.value)
    ElMessage.success('已重新申请')
    enrollmentStatus.value = 'PENDING'
  } catch (e) {
    if (e !== 'cancel') {
      console.error('[MSDetail] 重新申请失败:', e)
      ElMessage.error(e?.response?.data?.message || '操作失败，请稍后重试')
    }
  } finally {
    reapplyLoading.value = false
  }
}

const goCourse = (courseId) => {
  if (!courseId) return
  router.push(`/student/courses/${courseId}`)
}

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(async () => {
  await fetchDetail()
  if (ms.value) {
    await Promise.all([
      fetchCourses(),
      fetchTeachers(),
      checkEnrollment()
    ])
  }
})
</script>

<style scoped>
.ms-detail {
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-4) var(--space-6) 100px;
  min-height: 100dvh;
}
/* Loading */
.ms-detail-loading {
  padding: var(--space-6) 0;
}
.sk-banner {
  width: 100%;
  height: 240px;
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-4);
}
.sk-row {
  display: flex;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  margin-bottom: var(--space-4);
}
.sk-tabs {
  padding: var(--space-4);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
}
/* Error */
.ms-detail-error {
  padding: 80px 0;
}
/* Page Header */
.ms-page-header {
  margin-bottom: var(--space-4);
}
.bc-path {
  font-size: var(--text-sm);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.bc-link {
  color: var(--role-primary);
  cursor: pointer;
}
.bc-link:hover {
  text-decoration: underline;
}
.bc-sep {
  color: var(--el-text-color-placeholder);
}
.bc-current {
  color: var(--el-text-color-primary);
  font-weight: var(--weight-medium);
}
/* Banner */
.ms-banner {
  position: relative;
  width: 100%;
  height: 280px;
  border-radius: var(--radius-xl);
  overflow: hidden;
  margin-bottom: var(--space-4);
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}
.ms-banner-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ms-banner-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.4);
}
.ms-banner-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, transparent 30%, rgba(0, 0, 0, 0.5) 100%);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  padding: 32px;
}
.ms-banner-title {
  color: #fff;
  font-size: 28px;
  font-weight: var(--weight-bold);
  margin: 0 0 12px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}
.ms-status-tag {
  font-size: var(--text-sm);
}
/* Info Row */
.ms-info-card {
  margin-bottom: var(--space-4);
}
.ms-info-row {
  display: flex;
  gap: var(--space-6);
  flex-wrap: wrap;
}
.ms-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 100px;
}
.ms-info-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.ms-info-value {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}
/* Tabs */
.ms-tabs-card {
  margin-bottom: var(--space-6);
}
.ms-tab-loading {
  padding: var(--space-4);
}
/* Course List */
.ms-course-list {
  display: flex;
  flex-direction: column;
}
.ms-course-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  cursor: pointer;
  outline: none;
  transition: background var(--duration-base) var(--ease-out);
}
.ms-course-item:hover {
  background: var(--el-fill-color-lighter);
}
.ms-course-item:focus-visible {
  outline: 2px solid var(--role-primary);
  outline-offset: -2px;
}
.ms-course-item--required {
  border-left: 3px solid var(--el-color-danger);
}
.ms-course-order {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--el-fill-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--text-sm);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.ms-course-info {
  flex: 1;
  min-width: 0;
}
.ms-course-title {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}
.ms-course-meta {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.ms-course-status {
  flex-shrink: 0;
}
.ms-go-icon {
  color: var(--el-text-color-placeholder);
  flex-shrink: 0;
}
/* Teacher List */
.ms-teacher-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}
.ms-teacher-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--el-fill-color-lighter);
  border-radius: var(--radius-lg);
  min-width: 240px;
  flex: 1;
}
.ms-teacher-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ms-teacher-name {
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}
/* Desc */
.ms-desc-content {
  padding: var(--space-2) 0;
  line-height: var(--leading-relaxed);
}
.ms-desc-section {
  margin-bottom: var(--space-4);
}
.ms-desc-section strong {
  display: block;
  font-size: var(--text-md);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-2);
}
.ms-desc-section span {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
}
/* Bottom Bar */
.ms-bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--el-bg-color-overlay);
  border-top: 1px solid var(--el-border-color-lighter);
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
  padding: var(--space-3) var(--space-6);
}
.ms-bottom-bar-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ms-bottom-info {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}
.ms-bottom-credit {
  font-weight: var(--weight-semibold);
  color: var(--role-primary);
}
.ms-bottom-sep {
  color: var(--el-border-color);
}
</style>
