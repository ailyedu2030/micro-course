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
      :title="$t('microSpecialtyDetail.loadFailed')"
      :sub-title="$t('microSpecialtyDetail.networkError')"
      class="ms-detail-error"
    >
      <template #extra>
        <el-button type="primary" @click="fetchDetail">{{ $t('common.retry') }}</el-button>
      </template>
    </el-result>

    <!-- Not Found -->
    <el-result
      v-else-if="!ms"
      icon="warning"
      :title="$t('microSpecialtyDetail.notFound')"
      :sub-title="$t('microSpecialtyDetail.notFoundDesc')"
    >
      <template #extra>
        <el-button type="primary" @click="$router.push('/micro-specialties')">{{ $t('microSpecialtyDetail.backToSquare') }}</el-button>
      </template>
    </el-result>

    <!-- Content -->
    <template v-else>
      <!-- Breadcrumb -->
      <el-page-header @back="$router.back()" class="ms-page-header">
        <template #content>
          <span class="bc-path">
            <span class="bc-link" @click="$router.push('/micro-specialties')">{{ $t('microSpecialtyDetail.square') }}</span>
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
            <span class="ms-info-label">{{ $t('microSpecialtyDetail.department') }}</span>
            <span class="ms-info-value">{{ ms.departmentName || '—' }}</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">{{ $t('microSpecialtyDetail.leader') }}</span>
            <span class="ms-info-value">{{ ms.leadTeacherName || '—' }}</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">{{ $t('microSpecialtyDetail.totalCredits') }}</span>
            <span class="ms-info-value">{{ ms.totalCredits || 0 }} {{ $t('course.scoreUnit') }}</span>
          </div>
          <div class="ms-info-item">
            <span class="ms-info-label">{{ $t('microSpecialtyDetail.totalHours') }}</span>
            <span class="ms-info-value">{{ ms.totalHours || 0 }} {{ $t('microSpecialtyDetail.hoursUnit') }}</span>
          </div>
          <div class="ms-info-item" v-if="stats">
            <span class="ms-info-label">{{ $t('microSpecialtyDetail.enrollmentRate') }}</span>
            <span class="ms-info-value">{{ stats.enrollmentRate || '0%' }}</span>
          </div>
        </div>
      </el-card>

      <!-- Tabs -->
      <el-card shadow="never" class="ms-tabs-card">
        <el-tabs v-model="activeTab" class="ms-tabs">
          <el-tab-pane :label="$t('microSpecialtyDetail.tabCourses')" name="courses">
            <el-empty v-if="!courses.length" :description="$t('microSpecialtyDetail.noCourses')" />
            <template v-else>
              <!-- P1-2026-08-21: 移除 focus=failed 误导占位(课程 VO 无 per-course failed 字段，无法按失败过滤，
                   原 alert 声称"以下是未通过考核的课程"但列表实为全量课程) -->
              <!-- 修读要求汇总卡片 -->
              <div class="ms-requirements-card">
                <div class="ms-req-item">
                  <span class="ms-req-label">{{ $t('microSpecialtyDetail.required') }}</span>
                  <span class="ms-req-value ms-req-value--required">{{ requiredCount }} {{ $t('microSpecialtyDetail.courseUnit') }}</span>
                </div>
                <div class="ms-req-item">
                  <span class="ms-req-label">{{ $t('microSpecialtyDetail.elective') }}</span>
                  <span class="ms-req-value">{{ electiveCount }} {{ $t('microSpecialtyDetail.courseUnit') }}</span>
                </div>
                <div class="ms-req-item">
                  <span class="ms-req-label">{{ $t('microSpecialtyDetail.totalCredits') }}</span>
                  <span class="ms-req-value">{{ ms.totalCredits || 0 }} {{ $t('course.scoreUnit') }}</span>
                </div>
                <div v-if="ms.completionRule" class="ms-req-item ms-req-item--full">
                  <span class="ms-req-label">{{ $t('microSpecialtyDetail.completionRule') }}</span>
                  <span class="ms-req-value">{{ ms.completionRule }}</span>
                </div>
                <div v-if="ms.semester" class="ms-req-item">
                  <span class="ms-req-label">{{ $t('microSpecialtyDetail.suggestedSemester') }}</span>
                  <span class="ms-req-value">{{ ms.semester }}</span>
                </div>
              </div>
              <div class="ms-course-list">
              <div
                v-for="(item, i) in courses"
                :key="item.id"
                class="ms-course-item"
                :class="{
                  'ms-course-item--required': item.isRequired,
                  'ms-course-item--disabled': !courseClickable
                }"
                :role="courseClickable ? 'button' : undefined"
                :tabindex="courseClickable ? 0 : undefined"
                :aria-disabled="!courseClickable"
                @click="goCourse(item.courseId)"
                @keydown.enter.prevent="courseClickable && goCourse(item.courseId)"
                @keydown.space.prevent="courseClickable && goCourse(item.courseId)"
              >
                <span class="ms-course-order">{{ i + 1 }}</span>
                <div class="ms-course-info">
                  <span class="ms-course-title">
                    {{ item.courseTitle }}
                    <el-tag v-if="item.isRequired" type="danger" size="small">{{ $t('microSpecialtyDetail.required') }}</el-tag>
                    <el-tag v-else type="info" size="small">{{ $t('microSpecialtyDetail.elective') }}</el-tag>
                  </span>
                  <span class="ms-course-meta">
                    {{ item.teacherName || '—' }} · {{ item.credits || 0 }} {{ $t('course.credit') }}
                  </span>
                </div>
                <el-icon class="ms-go-icon"><ArrowRight /></el-icon>
              </div>
            </div>
            </template>
          </el-tab-pane>

          <el-tab-pane :label="$t('microSpecialtyDetail.tabTeachers')" name="teachers">
            <el-empty v-if="!teachers.length" :description="$t('microSpecialtyDetail.noTeachers')" />
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
                    <el-tag v-if="t.role === 'LEAD'" type="primary" size="small">{{ $t('microSpecialtyDetail.leader') }}</el-tag>
                    <el-tag v-else size="small">{{ $t('course.teachingTeacher') }}</el-tag>
                  </span>
                </div>
              </div>
            </div>
          </el-tab-pane>

           <el-tab-pane :label="$t('microSpecialtyDetail.tabDescription')" name="desc">
            <div class="ms-desc-content">
              <p v-if="ms.objectives" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.objectives') }}</strong>
                <span>{{ ms.objectives }}</span>
              </p>
              <p v-if="ms.description" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.projectIntro') }}</strong>
                <span>{{ ms.description }}</span>
              </p>
              <p v-if="ms.targetAudience" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.targetAudience') }}</strong>
                <span>{{ ms.targetAudience }}</span>
              </p>
              <p v-if="ms.admissionRequirement" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.admissionRequirement') }}</strong>
                <span>{{ ms.admissionRequirement }}</span>
              </p>
              <p v-if="ms.completionRule" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.completionRuleTitle') }}</strong>
                <span>{{ ms.completionRule }}</span>
              </p>
              <p v-if="ms.requirements" class="ms-desc-section">
                <strong>{{ $t('microSpecialtyDetail.requirements') }}</strong>
                <span>{{ ms.requirements }}</span>
              </p>
              <el-empty v-if="!ms.objectives && !ms.description && !ms.targetAudience && !ms.admissionRequirement && !ms.completionRule && !ms.requirements" :description="$t('microSpecialtyDetail.noDescription')" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- Bottom Fixed CTA Bar -->
      <div class="ms-bottom-bar">
        <div class="ms-bottom-bar-inner">
          <div class="ms-bottom-info">
            <span class="ms-bottom-credit">{{ ms.totalCredits || 0 }} {{ $t('course.credit') }}</span>
            <span class="ms-bottom-sep">|</span>
            <span class="ms-bottom-count">{{ $t('microSpecialtyDetail.courseCount', { count: ms.courseCount || 0 }) }}</span>
          </div>
          <div class="ms-bottom-actions">
            <!-- 未登录 -->
            <el-button v-if="!isLoggedIn" type="primary" size="large" @click="goLogin">
              {{ $t('course.pleaseLogin') }}
            </el-button>
            <el-button
              v-else-if="!isStudent"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.studentOnly') }}
            </el-button>
            <!-- FAILED / REJECTED → 重新申请 -->
            <el-button
              v-else-if="['FAILED', 'REJECTED'].includes(enrollmentStatus) && canReapply"
              type="primary"
              size="large"
              :loading="reapplyLoading"
              @click="handleReapply"
            >
              {{ $t('microSpecialtyDetail.reapply') }}
            </el-button>
            <el-button
              v-else-if="['FAILED', 'REJECTED'].includes(enrollmentStatus)"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.reapplyClosed') }}
            </el-button>
            <!-- PENDING → 审核中 -->
            <el-button
              v-else-if="enrollmentStatus === 'PENDING'"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.reviewing') }}
            </el-button>
            <!-- 已报名/进行中 -->
            <el-button
              v-else-if="['APPROVED', 'IN_PROGRESS'].includes(enrollmentStatus)"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.enrolled') }}
            </el-button>
            <!-- 已结业 -->
            <el-button
              v-else-if="enrollmentStatus === 'COMPLETED'"
              type="success"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.completed') }}
            </el-button>
            <!-- CERTIFIED 已认证 -->
            <el-button
              v-else-if="enrollmentStatus === 'CERTIFIED'"
              type="success"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.certified') }}
            </el-button>
            <!-- 已报名但已退出 -->
            <el-button
              v-else-if="enrollmentStatus === 'DROPPED' && canReapply"
              type="primary"
              size="large"
              :loading="reapplyLoading"
              @click="handleReapply"
            >
              {{ $t('microSpecialtyDetail.reenroll') }}
            </el-button>
            <el-button
              v-else-if="enrollmentStatus === 'DROPPED'"
              size="large"
              disabled
            >
              {{ $t('microSpecialtyDetail.reenrollClosed') }}
            </el-button>
            <!-- 未报名 — 显示明确引导 -->
            <el-button
              v-else
              type="primary"
              size="large"
              :loading="applyLoading"
              :disabled="!canEnroll || statusLoadFailed"
              @click="handleApply"
            >
              {{ statusLoadFailed ? '报名状态加载失败，请刷新重试' : (canEnroll ? $t('microSpecialtyDetail.applyNow') : (!isStudent ? $t('microSpecialtyDetail.studentOnly') : (ms?.status === 'RECRUITING' ? $t('course.pleaseLogin') : $t('microSpecialtyDetail.applyClosed')))) }}
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Notebook, User } from '@element-plus/icons-vue'
import {
  getMicroSpecialtyDetail
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
const { t: i18nT } = useI18n()

const msId = computed(() => route.params.id)
const ms = ref(null)
const stats = ref(null)
const loading = ref(false)
const error = ref(false)
const activeTab = ref(route.query.tab || 'courses')
// P1-2026-08-21: focusFailed 死代码已移除（无 failed 过滤数据支撑）
const gotoFirst = ref(route.query.goto === 'first')

const courses = ref([])

const teachers = ref([])

// Enrollment
const enrollmentId = ref(null)
const enrollmentStatus = ref(null)
const statusLoadFailed = ref(false) // P2: 报名状态查询失败标记
const applyLoading = ref(false)
const reapplyLoading = ref(false)

const isLoggedIn = computed(() => !!userStore.token)
const isStudent = computed(() => userStore.role === 'STUDENT')
const isStaffViewer = computed(() => isLoggedIn.value && !isStudent.value)

const canEnroll = computed(() => {
  if (!ms.value || !isLoggedIn.value || !isStudent.value) return false
  if (enrollmentStatus.value) return false // 已有报名记录
  return ms.value.status === 'RECRUITING'
})

const canReapply = computed(() => {
  if (!ms.value || !isLoggedIn.value || !isStudent.value) return false
  const reapplyStatuses = ['FAILED', 'REJECTED', 'DROPPED']
  return reapplyStatuses.includes(enrollmentStatus.value) && ms.value.status === 'RECRUITING'
})

const statusLabel = computed(() => {
  if (!ms.value) return ''
  const map = {
    DRAFT: i18nT('course.draft'),
    PENDING_REVIEW: i18nT('microSpecialtyDetail.reviewing'),
    APPROVED: i18nT('course.approved'),
    REJECTED: i18nT('microSpecialtyDetail.rejected'),
    ARCHIVED: i18nT('course.archived'),
    RECRUITING: i18nT('courseSquare.msRecruiting'),
    COMPLETED: i18nT('microSpecialtyDetail.completed'),
    CANCELLED: i18nT('microSpecialtyDetail.cancelled')
  }
  return map[ms.value.status] || ms.value.status || '—'
})

const statusTagType = computed(() => {
  if (!ms.value) return 'info'
  const map = {
    RECRUITING: 'success',
    COMPLETED: 'info',
    REJECTED: 'danger',
    DRAFT: 'info',
    PENDING_REVIEW: 'warning',
    APPROVED: 'success',
    ARCHIVED: 'info',
    CANCELLED: 'danger'
  }
  return map[ms.value.status] || 'info'
})

// 获取详情
const fetchDetail = async () => {
  loading.value = true
  error.value = false
  try {
    const detailRes = await getMicroSpecialtyDetail(msId.value)
    ms.value = detailRes.data
    courses.value = detailRes.data?.courses || []
    teachers.value = detailRes.data?.teachers || []
    stats.value = detailRes.data?.stats || null
  } catch (e) {
// eslint-disable-next-line no-console
    console.debug('[MSDetail] 加载详情失败:', e)
    ms.value = null
    courses.value = []
    teachers.value = []
    stats.value = null
    error.value = true
  } finally {
    loading.value = false
  }
}

// 检查报名状态
const checkEnrollment = async () => {
  if (!isLoggedIn.value || !isStudent.value) return
  try {
    const { data } = await getMyEnrollments()
    const enrollments = data?.items || data || []
    const found = enrollments.find(
      e => String(e.microSpecialtyId) === String(msId.value)
    )
    if (found) {
      enrollmentId.value = found.id
      enrollmentStatus.value = found.status
    }
  } catch (e) {
    // P2-2026-08-21: 查询失败不能误导用户认为"未报名"可立即报名(服务端虽兜底拒绝，但 UI 状态错误)
    statusLoadFailed.value = true
    console.warn('[MSDetail] 检查报名状态失败:', e)
  }
}

// 报名
const handleApply = async () => {
  if (!isStudent.value) {
    ElMessage.warning(i18nT('microSpecialtyDetail.studentOnlyApply'))
    return
  }
  try {
    await ElMessageBox.confirm(
      i18nT('microSpecialtyDetail.confirmApply', { title: ms.value.title }),
      i18nT('microSpecialtyDetail.applyConfirmTitle'),
      { confirmButtonText: i18nT('microSpecialtyDetail.confirmApplyBtn'), cancelButtonText: i18nT('app.cancel'), type: 'info' }
    )
    applyLoading.value = true
    await applyEnrollment({ microSpecialtyId: msId.value })
    ElMessage.success(i18nT('course.signupSuccess'))
    enrollmentStatus.value = 'PENDING'
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) {
// eslint-disable-next-line no-console
      console.debug('[MSDetail] 报名失败:', e)
      ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyDetail.applyFailed'))
    }
  } finally {
    applyLoading.value = false
  }
}

// 重新申请
const handleReapply = async () => {
  if (!isStudent.value) {
    ElMessage.warning(i18nT('microSpecialtyDetail.studentOnlyReapply'))
    return
  }
  if (!canReapply.value) {
    ElMessage.warning(i18nT('microSpecialtyDetail.reapplyNotRecruiting'))
    return
  }
  try {
    await ElMessageBox.confirm(
      i18nT('microSpecialtyDetail.confirmReapply'),
      i18nT('microSpecialtyDetail.reapply'),
      { confirmButtonText: i18nT('app.confirm'), cancelButtonText: i18nT('app.cancel'), type: 'info' }
    )
    reapplyLoading.value = true
    await reapplyEnrollment(enrollmentId.value)
    ElMessage.success(i18nT('microSpecialtyDetail.reapplied'))
    enrollmentStatus.value = 'PENDING'
  } catch (e) {
    if (!['cancel', 'close'].includes(e)) {
// eslint-disable-next-line no-console
      console.debug('[MSDetail] 重新申请失败:', e)
      ElMessage.error(e?.response?.data?.message || i18nT('microSpecialtyDetail.operationFailed'))
    }
  } finally {
    reapplyLoading.value = false
  }
}

// 修读要求统计
const requiredCount = computed(() => courses.value.filter(c => c.isRequired).length)
const electiveCount = computed(() => courses.value.filter(c => !c.isRequired).length)

const resolveCourseDetailPath = (courseId) => {
  if (!courseId) return null
  if (isStudent.value) return `/student/courses/${courseId}`
  if (userStore.role === 'TEACHER') return `/teacher/courses/${courseId}`
  if (['ACADEMIC', 'ADMIN'].includes(userStore.role)) return `/courses/${courseId}`
  return null
}

// 课程是否可点击：学生取决于报名状态，教职工可直接查看课程详情
const courseClickable = computed(() => {
  if (!isLoggedIn.value) return false
  if (isStaffViewer.value) return true
  if (!enrollmentStatus.value) return false
  const allowed = ['PENDING', 'APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CERTIFIED']
  return allowed.includes(enrollmentStatus.value)
})

const goCourse = (courseId) => {
  if (!courseId) return
  if (!isLoggedIn.value) {
    ElMessage.warning(i18nT('microSpecialtyDetail.loginToStudy'))
    goLogin()
    return
  }
  const targetPath = resolveCourseDetailPath(courseId)
  if (!targetPath) {
    ElMessage.warning(i18nT('microSpecialtyDetail.noCourseEntry'))
    return
  }
  if (isStaffViewer.value) {
    router.push(targetPath)
    return
  }
  if (!enrollmentStatus.value) {
    ElMessage.warning(i18nT('microSpecialtyDetail.enrollToStudy'))
    return
  }
  if (!courseClickable.value) {
    const reapplyTip = ms.value?.status === 'RECRUITING'
      ? i18nT('microSpecialtyDetail.needReapply')
      : i18nT('microSpecialtyDetail.reapplyClosed')
    const tipMap = {
      DROPPED: i18nT('microSpecialtyDetail.droppedTip', { tip: reapplyTip }),
      REJECTED: i18nT('microSpecialtyDetail.rejectedTip', { tip: reapplyTip }),
      FAILED: i18nT('microSpecialtyDetail.failedTip', { tip: reapplyTip })
    }
    ElMessage.warning(tipMap[enrollmentStatus.value] || i18nT('microSpecialtyDetail.courseNotAccessible'))
    return
  }
  router.push(targetPath)
}

const goLogin = () => {
  router.push({ path: '/login', query: { redirect: route.fullPath } })
}

onMounted(async () => {
  await fetchDetail()
  if (ms.value) {
    await checkEnrollment()
    if (gotoFirst.value && courses.value.length > 0) {
      gotoFirst.value = false
      goCourse(courses.value[0].courseId)
    }
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
.mg-bottom-12 { margin-bottom: var(--space-3); }
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
.ms-requirements-card {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--el-color-primary-light-8));
  border-radius: var(--radius-lg);
  border: 1px solid var(--el-color-primary-light-7);
}
.ms-req-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 80px;
}
.ms-req-item--full {
  flex-basis: 100%;
}
.ms-req-label {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}
.ms-req-value {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}
.ms-req-value--required {
  color: var(--el-color-danger);
}
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
.ms-course-item--disabled {
  opacity: 0.45;
  cursor: not-allowed;
  pointer-events: auto;
}
.ms-course-item--disabled:hover {
  background: transparent;
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

/* R2: Bottom CTA buttons focus-visible */
.ms-bottom-bar :deep(.el-button:focus-visible) {
  outline: 2px solid var(--role-primary);
  outline-offset: 2px;
}
</style>
