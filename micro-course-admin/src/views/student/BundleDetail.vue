<template>
  <div class="bundle-detail">
    <el-page-header @back="$router.back()" :content="bundle?.title" class="mg-bottom-16" />

    <div v-loading="loading" class="detail-body">
      <el-row :gutter="24">
        <el-col :span="16">
          <el-card shadow="never">
            <template #header><span>套件子课（{{ items.length }} 门）</span></template>
            <div v-for="item in items" :key="item.id" class="course-row"
              tabindex="0" role="button"
              :aria-label="'课程：' + item.courseTitle"
              @click="goCourse(item.courseId)"
              @keydown.enter="goCourse(item.courseId)"
              @keydown.space.prevent="goCourse(item.courseId)">
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
            <el-button v-else-if="isEnrolled" type="primary" size="large" class="buy-btn" disabled>
              已购买
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
import { ElMessage } from 'element-plus'
import { ArrowRight } from '@element-plus/icons-vue'
import { getBundleById } from '@/api/bundle'
import { createOrder, payOrder } from '@/api/order'
import { getMyEnrollments } from '@/api/enrollment'
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
    const { data } = await getBundleById(bundleId.value)
    bundle.value = data
    items.value = data.items || []
  } catch { ElMessage.error('加载套件失败') }
  finally { loading.value = false }

  if (isLoggedIn.value) {
    try {
      const { data } = await getMyEnrollments(userStore.userInfo.id)
      const enrolledCourseIds = new Set((data.items || data || []).map(e => e.courseId))
      const requiredIds = items.value.filter(i => i.isRequired).map(i => i.courseId)
      isEnrolled.value = requiredIds.every(id => enrolledCourseIds.has(id))
    } catch {}
  }
})

const goLogin = () => router.push('/login')
const goCourse = (id) => router.push(`/student/courses/${id}`)

const handleBuy = async () => {
  buyLoading.value = true
  try {
    // Buy through first required course as entry point
    const firstRequired = items.value.find(i => i.isRequired)
    if (!firstRequired) { ElMessage.warning('套件无必修课'); return }
    const { data: order } = await createOrder({ userId: userStore.userInfo.id, courseId: firstRequired.courseId, bundleId: bundleId.value })
    if (order.status === 'PAID') {
      isEnrolled.value = true
      ElMessage.success('加入成功')
      return
    }
    await payOrder(order.id, 'BALANCE')
    isEnrolled.value = true
    ElMessage.success('购买成功，已选修所有必修课')
  } catch { ElMessage.error('操作失败') }
  finally { buyLoading.value = false }
}
</script>

<style scoped>
.bundle-detail { padding: 16px; max-width: 1400px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: 16px; }
.detail-body { min-height: 300px; }

.course-row { display: flex; align-items: center; gap: 12px; padding: 12px 8px; border-bottom: 1px solid #f0f0f0; cursor: pointer; transition: background .2s; }
.course-row:hover { background: #fafafa; }
.course-order { width: 32px; text-align: center; color: #c0c4cc; font-size: 16px; font-weight: 600; }
.course-info { flex: 1; }
.course-title { font-size: 15px; color: #303133; font-weight: 500; }
.course-meta { display: block; font-size: 12px; color: #909399; margin-top: 2px; }
.go-icon { color: #c0c4cc; font-size: 18px; }

.purchase-card { position: sticky; top: 16px; text-align: center; }
.price-display { font-size: 36px; font-weight: 700; margin: 16px 0 8px; }
.free-text { color: #67c23a; }
.paid-text { color: #f56c6c; }
.student-count { font-size: 13px; color: #c0c4cc; margin: 0 0 12px; }
.desc-text { font-size: 13px; color: #909399; margin: 4px 0; }
.buy-btn { width: 100%; margin-top: 16px; height: 46px; font-size: 16px; }
</style>
