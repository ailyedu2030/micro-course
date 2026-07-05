<template>
  <div class="bundle-detail">
    <el-page-header @back="$router.back()" :content="bundle?.title" class="mg-bottom-16" />

    <div v-loading="loading" class="detail-body">
      <el-row :gutter="24">
        <el-col :span="16">
          <el-card shadow="never">
            <template #header><span>套件子课（{{ items.length }} 门）</span></template>
            <div
v-for="item in items" :key="item.id" class="course-row student-card-item"
              tabindex="0" role="button"
              :aria-label="'课程：' + item.courseTitle"
              @click="goCourse(item.courseId)"
              @keydown.enter="goCourse(item.courseId)"
              @keydown.space.prevent="goCourse(item.courseId)"
>
              <span class="course-order">{{ item.sortOrder || '-' }}</span>
              <div class="course-info">
                <span class="course-title">{{ item.courseTitle }}</span>
                <span class="course-meta">{{ item.teacherName }} · 
                  <el-tag v-if="item.courseType === 'INTERACTIVE'" type="success" size="small">互动</el-tag>
                  <el-tag v-else type="primary" size="small">视频</el-tag>
                </span>
              </div>
              <div class="course-tags">
                <el-tag v-if="item.isRequired" type="danger" size="small">必修</el-tag>
                <el-tag v-else type="info" size="small">选修</el-tag>
              </div>
              <el-icon class="go-icon"><ArrowRight /></el-icon>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card shadow="never" class="purchase-card">
            <div class="price-display">
              <span v-if="bundle?.isFree || !bundle?.price" class="free-text">免费</span>
              <span v-else class="paid-text">¥{{ bundle.price }}</span>
            </div>
            <p class="student-count" v-if="bundle?.studentCount">{{ bundle.studentCount }} 人已学习</p>
            <p class="desc-text" v-if="bundle?.description">{{ bundle.description }}</p>
            <p class="desc-text">含 {{ requiredCount }} 门必修课{{ electiveCount > 0 ? ' + ' + electiveCount + ' 门选修课' : '' }}</p>

            <el-button v-if="!isLoggedIn" type="primary" size="large" class="buy-btn" @click="goLogin">
              请先登录
            </el-button>
            <el-button v-else-if="isEnrolled" type="primary" size="large" class="buy-btn" @click="startLearning">
              开始学习
            </el-button>
            <el-button v-else type="primary" size="large" class="buy-btn" :loading="buyLoading" @click="handleBuy">
              {{ bundle?.isFree || !bundle?.price ? '立即加入' : '立即购买 · ¥' + bundle?.price }}
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { getBundleById, getBundleEnrollmentStatus } from '@/api/bundle'
import { createOrder, payOrder } from '@/api/order'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const bundleId = computed(() => route.params.id)
const bundle = ref(null)
const items = ref([])
const loading = ref(false)
const buyLoading = ref(false)
const isEnrolled = ref(false)

const isLoggedIn = computed(() => !!userStore.token)
const requiredCount = computed(() => items.value.filter(i => i.isRequired).length)
const electiveCount = computed(() => items.value.filter(i => !i.isRequired).length)

onMounted(async () => {
  loading.value = true
  try {
    // 并行拉取套餐详情 + （登录时）报名状态，避免串行等待导致的"已购买"按钮闪烁
    const promises = [getBundleById(bundleId.value)]
    if (isLoggedIn.value) {
      promises.push(getBundleEnrollmentStatus(bundleId.value))
    }
    const results = await Promise.all(promises)
    const bundleResp = results[0]
    bundle.value = bundleResp.data
    items.value = bundleResp.data.items || []
    if (results.length > 1) {
      isEnrolled.value = results[1].data.enrolled
    }
  } catch (e) { ElMessage.error(e?.response?.data?.message || '加载套件失败') }
  finally { loading.value = false }
})

const goLogin = () => router.push('/login')
const goCourse = (id) => router.push(`/student/courses/${id}`)

const startLearning = () => {
  const firstCourse = bundle.value?.courses?.[0] || items.value?.[0]
  if (firstCourse) {
    router.push(`/student/courses/${firstCourse.courseId || firstCourse.id}`)
  } else {
    ElMessage.warning('套餐内暂无课程')
  }
}

const handleBuy = async () => {
  buyLoading.value = true
  try {
    const firstRequired = items.value.find(i => i.isRequired)
    if (!firstRequired) { ElMessage.warning('套件无必修课'); return }
    const { data: order } = await createOrder({ courseId: firstRequired.courseId, bundleId: bundleId.value })
    if (order.status === 'PAID') {
      isEnrolled.value = true
      ElMessage.success('加入成功')
      // 重新拉取套餐数据，更新 studentCount 等
      const { data } = await getBundleById(bundleId.value)
      bundle.value = data
      items.value = data.items || []
      return
    }
    const amount = bundle.value?.price || order.amount || 0
    try {
      await ElMessageBox.confirm(
        `确认支付 ¥${amount} 吗？`,
        '支付确认',
        { confirmButtonText: '确认支付', cancelButtonText: '取消', type: 'warning' }
      )
    } catch { buyLoading.value = false; return }
    await payOrder(order.id, 'BALANCE')
    // 重新拉取最新状态
    try {
      const { data: status } = await getBundleEnrollmentStatus(bundleId.value)
      isEnrolled.value = status.enrolled
      const { data: bundleData } = await getBundleById(bundleId.value)
      bundle.value = bundleData
      items.value = bundleData.items || []
    } catch { /* 静默 */ }
    ElMessage.success('购买成功，已选修所有必修课')
  } catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
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
</style>
