<template>
  <div class="bundle-detail">
    <el-page-header @back="$router.back()" :content="bundle?.title" class="mg-bottom-16" />

    <div v-loading="loading" class="detail-body">
      <el-row :gutter="24">
        <el-col :span="16">
          <el-card shadow="never">
            <template #header><span>{{ $t('bundleDetail.subtitleCount', { count: items.length }) }}</span></template>
            <div
v-for="item in items" :key="item.id" class="course-row student-card-item"
              tabindex="0" role="button"
              :aria-label="$t('bundleDetail.courseAria', { title: item.courseTitle })"
              @click="goCourse(item.courseId)"
              @keydown.enter="goCourse(item.courseId)"
              @keydown.space.prevent="goCourse(item.courseId)"
>
              <span class="course-order">{{ item.sortOrder || '-' }}</span>
              <div class="course-info">
                <span class="course-title">{{ item.courseTitle }}</span>
                <span class="course-meta">{{ item.teacherName }} · 
                  <el-tag v-if="isCoursewareCourseType(item.courseType)" type="success" size="small">{{ item.courseType === 'HTML_COURSEWARE' ? $t('bundleDetail.htmlCourseware') : $t('bundleDetail.pptCourseware') }}</el-tag>
                  <el-tag v-else-if="item.courseType === 'OFFLINE'" type="info" size="small">{{ $t('bundleDetail.offline') }}</el-tag>
                  <el-tag v-else type="primary" size="small">{{ $t('bundleDetail.video') }}</el-tag>
                </span>
              </div>
              <div class="course-tags">
                <el-tag v-if="item.isRequired" type="danger" size="small">{{ $t('bundleDetail.required') }}</el-tag>
                <el-tag v-else type="info" size="small">{{ $t('bundleDetail.elective') }}</el-tag>
              </div>
              <el-icon class="go-icon"><ArrowRight /></el-icon>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" class="purchase-card">
            <div class="price-display">
              <span v-if="bundle?.isFree || !bundle?.price" class="free-text">{{ $t('app.free') }}</span>
              <span v-else class="paid-text">¥{{ bundle.price }}</span>
            </div>
            <p class="student-count" v-if="bundle?.studentCount">{{ $t('bundleDetail.studentsCount', { count: bundle.studentCount }) }}</p>
            <p class="desc-text" v-if="bundle?.description">{{ bundle.description }}</p>
            <p class="desc-text">{{ $t('bundleDetail.containsRequired', { count: requiredCount }) }}{{ electiveCount > 0 ? $t('bundleDetail.electiveSuffix', { count: electiveCount }) : '' }}</p>

            <el-button v-if="!isLoggedIn" type="primary" size="large" class="buy-btn" @click="goLogin">
              {{ $t('course.pleaseLogin') }}
            </el-button>
            <el-button v-else-if="isEnrolled" type="primary" size="large" class="buy-btn" @click="startLearning">
              {{ firstUncompleted ? $t('bundleDetail.startNext') : $t('course.startLearning') }}
            </el-button>
            <el-button v-else type="primary" size="large" class="buy-btn" :loading="buyLoading" @click="handleBuy">
              {{ bundle?.isFree || !bundle?.price ? $t('bundleDetail.joinNow') : $t('bundleDetail.buyNowPrice', { price: bundle?.price }) }}
            </el-button>
          </el-card>

          <el-card v-if="isEnrolled && requiredCourses.length" shadow="never" class="path-card">
            <template #header><span>{{ $t('bundleDetail.learningPath') }}</span></template>
            <div v-for="(c, idx) in requiredCourses" :key="c.id" class="path-row" :class="{ completed: courseProgress[c.courseId || c.id], active: firstUncompleted?.courseId === c.courseId || firstUncompleted?.id === c.id }">
              <span class="path-order">{{ idx + 1 }}</span>
              <span class="path-title">{{ c.courseTitle || $t('bundleDetail.courseFallback', { id: c.courseId }) }}</span>
              <el-tag v-if="courseProgress[c.courseId || c.id]" type="success" size="small">{{ $t('course.completed') }}</el-tag>
              <el-tag v-else type="warning" size="small">{{ $t('bundleDetail.notCompleted') }}</el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { getBundleById, getBundleEnrollmentStatus } from '@/api/bundle'
import { createOrder, payOrder } from '@/api/order'
import { batchGetLearningProgress } from '@/api/learning-progress'
import { useUserStore } from '@/store/user'
import { isCoursewareCourseType } from '@/config/courseTypeConfig'
import { getMyEnrollments } from '@/api/enrollment'
import { filterCourseCollectionEnrollments } from '@/utils/enrollmentFilters'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const bundleId = computed(() => route.params.id)
const bundle = ref(null)
const items = ref([])
const loading = ref(false)
const buyLoading = ref(false)
const isEnrolled = ref(false)
const courseProgress = ref({})

const isLoggedIn = computed(() => !!userStore.token)
const requiredCount = computed(() => items.value.filter(i => i.isRequired).length)
const electiveCount = computed(() => items.value.filter(i => !i.isRequired).length)
const enrolledCourseIds = ref(new Set())

const requiredCourses = computed(() => items.value.filter(i => i.isRequired))
const firstUncompleted = computed(() => {
  return requiredCourses.value.find(c => !courseProgress.value[c.courseId || c.id])
})

onMounted(async () => {
  loading.value = true
  try {
    // 并行拉取套餐详情 + （登录时）报名状态，避免串行等待导致的"已购买"按钮闪烁
    const promises = [getBundleById(bundleId.value)]
    if (isLoggedIn.value) {
      promises.push(getBundleEnrollmentStatus(bundleId.value))
      promises.push(getMyEnrollments({ page: 0, size: 100 }))
    }
    const results = await Promise.all(promises)
    const bundleResp = results[0]
    bundle.value = bundleResp.data
    items.value = bundleResp.data.items || []
    if (results.length > 1) {
      isEnrolled.value = results[1].data.enrolled
    }
    if (results.length > 2) {
      const enrollData = results[2].data || []
      const list = filterCourseCollectionEnrollments(Array.isArray(enrollData) ? enrollData : (enrollData.items || []))
      enrolledCourseIds.value = new Set(list.map(e => e.courseId))
    }
    if (isEnrolled.value && items.value.length) {
      const courseIds = items.value.map(i => i.courseId || i.id).filter(Boolean)
      if (courseIds.length) {
        try {
          const { data: progressList } = await batchGetLearningProgress(courseIds)
          if (Array.isArray(progressList)) {
            const map = {}
            progressList.forEach(p => { map[p.courseId] = p.completed })
            courseProgress.value = map
          }
        } catch { /* 静默 */ }
      }
    }
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('bundleDetail.loadFailed')) }
  finally { loading.value = false }
})

const goLogin = () => router.push('/login')
const goCourse = (id) => router.push(`/student/courses/${id}`)

const startLearning = () => {
  const target = firstUncompleted.value || requiredCourses.value[0] || items.value[0]
  if (target) {
    router.push(`/student/courses/${target.courseId || target.id}`)
  } else {
    ElMessage.warning(t('bundleDetail.noCourses'))
  }
}

const handleBuy = async () => {
  buyLoading.value = true
  try {
    // P1C-012: 检查所有课程（不仅是必修课），找到第一个未选课未购买的课程
    const firstUnenrolled = items.value.find(i => !enrolledCourseIds.value.has(i.courseId || i.id))
    if (!firstUnenrolled) {
      ElMessage.success(t('bundleDetail.allEnrolled'))
      isEnrolled.value = true
      return
    }
    const { data: order } = await createOrder({ courseId: firstUnenrolled.courseId, bundleId: bundleId.value })
    if (order.status === 'PAID') {
      isEnrolled.value = true
      ElMessage.success(t('bundleDetail.joinedSuccess'))
      // 重新拉取套餐数据，更新 studentCount 等
      const { data } = await getBundleById(bundleId.value)
      bundle.value = data
      items.value = data.items || []
      return
    }
    const amount = bundle.value?.price || order.amount || 0
    try {
      await ElMessageBox.confirm(
        t('bundleDetail.confirmPayMessage', { amount }),
        t('bundleDetail.payConfirmTitle'),
        { confirmButtonText: t('order.pay'), cancelButtonText: t('common.cancel'), type: 'warning' }
      )
    } catch { buyLoading.value = false; return }
    await payOrder(order.id, 'BALANCE')
    // P2-2026-08-21: 支付成功后的刷新/状态拉取失败不得掩盖支付成功
    // （原 getMyEnrollments 抛错落入外层 catch 弹"操作失败"→ 诱导用户重复下单）
    try {
      // P1C-012: 购买成功时重新拉取 enrollment 状态确认所有课程已注册
      const { data: myEnrollments } = await getMyEnrollments({ page: 0, size: 100 })
      const list = filterCourseCollectionEnrollments(Array.isArray(myEnrollments) ? myEnrollments : (myEnrollments?.items || []))
      enrolledCourseIds.value = new Set(list.map(e => e.courseId))
    } catch (e2) { console.warn('[BundleDetail] 支付后刷新选课状态失败', e2) }
    // 重新拉取最新状态
    try {
      const { data: status } = await getBundleEnrollmentStatus(bundleId.value)
      isEnrolled.value = status.enrolled
      const { data: bundleData } = await getBundleById(bundleId.value)
      bundle.value = bundleData
      items.value = bundleData.items || []
    } catch { /* 静默 */ }
    ElMessage.success(t('bundleDetail.purchaseSuccess'))
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('common.failed')) }
  finally { buyLoading.value = false }
}
</script>

<style scoped>
.bundle-detail { padding: var(--space-6); min-height: 100dvh; max-width: 1400px; margin: 0 auto; background: var(--el-bg-color-page); }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.detail-body { min-height: 300px; }

.course-row { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-2); border-bottom: 1px solid var(--el-border-color-lighter); cursor: pointer; transition: background var(--duration-base) var(--ease-out); }
.course-row:hover { background: var(--el-fill-color-light); }
.course-order { width: 32px; text-align: center; color: var(--el-text-color-secondary); font-size: var(--text-md); font-weight: var(--weight-semibold); }
.course-info { flex: 1; }
.course-title { font-size: var(--text-md); color: var(--el-text-color-primary); font-weight: var(--weight-medium); }
.course-meta { display: block; font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-top: 2px; }
.go-icon { color: var(--el-text-color-secondary); font-size: var(--text-lg); }

.purchase-card { position: sticky; top: var(--space-4); text-align: center; }
.price-display { font-size: 36px; font-weight: var(--weight-bold); margin: var(--space-4) 0 var(--space-2); }
.free-text { color: var(--el-color-success); }
.paid-text { color: var(--el-color-danger); }
.student-count { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin: 0 0 var(--space-3); }
.desc-text { font-size: var(--text-sm); color: var(--el-text-color-secondary); margin: var(--space-1) 0; }
.buy-btn { width: 100%; margin-top: var(--space-4); height: 46px; font-size: var(--text-md); }
.path-card { margin-top: var(--space-3); }
.path-row { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0; border-bottom: 1px solid var(--el-border-color-lighter); font-size: var(--text-sm); }
.path-row:last-child { border-bottom: none; }
.path-row.completed { opacity: 0.6; }
.path-row.active { background: var(--el-color-primary-light-9); border-radius: var(--radius-sm); padding: var(--space-2) var(--space-1); }
.path-order { width: 20px; height: 20px; border-radius: 50%; background: var(--el-color-primary); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.path-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
