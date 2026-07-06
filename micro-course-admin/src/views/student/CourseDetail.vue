<!--
  课程详情 · MOOC 风格重构
  设计参考: icourse163.org / study.163.com
  1200px 双栏布局 · 白色Hero · 绿色Tab · 左侧内容+右侧边栏
-->
<template>
  <div class="course-detail-page">
    <!-- 加载骨架屏 -->
    <div v-if="courseLoading" class="course-skeleton">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- 404 -->
    <div v-else-if="courseNotFound" class="not-found-page">
      <el-empty description="课程不存在或已下架" :image-size="160">
        <el-button type="primary" @click="router.push('/student/courses')">返回课程广场</el-button>
      </el-empty>
    </div>

    <template v-else>
      <!-- ====== 面包屑 ====== -->
      <div class="detail-breadcrumb">
        <router-link to="/student/courses">课程广场</router-link>
        <span class="bc-sep">/</span>
        <span>{{ course.categoryName || '课程详情' }}</span>
        <span class="bc-sep">/</span>
        <span class="bc-current">{{ course.title }}</span>
      </div>

      <!-- ====== Hero Card ====== -->
      <div class="hero-card">
        <div class="hero-left">
          <!-- 互动课程: 幻灯片预览 → 播放器 -->
          <div v-if="course.courseType === 'INTERACTIVE'" class="hero-img-box" @click="handlePlayPreview">
            <div class="hero-img-placeholder">
              <el-icon :size="56" color="#ccc"><Present /></el-icon>
            </div>
            <div class="hero-play-btn">
              <el-icon :size="28" color="#fff"><VideoPlay /></el-icon>
            </div>
          </div>
          <!-- 视频课程: 封面图 或 内嵌播放器 -->
          <div v-else-if="showPlayer" class="hero-img-box hero-player-active">
            <video ref="videoRef" class="hero-video" controls autoplay @click.stop />
            <button class="hero-close-player" @click.stop="stopPreview">
              <el-icon :size="18"><Close /></el-icon>
            </button>
          </div>
          <div v-else class="hero-img-box" @click="handlePlayPreview">
            <img v-if="course.coverUrl" :src="course.coverUrl" :alt="course.title" @error="handleCoverError" class="hero-img" />
            <div v-else class="hero-img-placeholder">
              <el-icon :size="56" color="#ccc"><VideoPlay /></el-icon>
            </div>
            <div class="hero-play-btn" :class="{ loading: videoLoading }">
              <el-icon v-if="!videoLoading" :size="28" color="#fff"><VideoPlay /></el-icon>
              <el-icon v-else :size="28" color="#fff" class="loading-icon"><Loading /></el-icon>
            </div>
          </div>
        </div>
        <div class="hero-right">
          <h1 class="hero-title">{{ course.title }}</h1>
          <p v-if="course.subtitle" class="hero-subtitle">{{ course.subtitle }}</p>
          <div class="hero-tags">
            <el-tag v-if="course.courseType === 'INTERACTIVE'" size="small" effect="plain" type="success">互动课程</el-tag>
            <el-tag v-if="course.difficulty" size="small" effect="plain">{{ difficultyText }}</el-tag>
            <el-tag v-if="course.categoryName" size="small" effect="plain" type="info">{{ course.categoryName }}</el-tag>
          </div>
          <div class="hero-stats">
            <span v-if="course.avgRating" class="hero-stat"><el-icon><Star /></el-icon> <strong>{{ course.avgRating.toFixed(1) }}</strong> 分</span>
            <span v-if="course.studentCount" class="hero-stat"><el-icon><User /></el-icon> <strong>{{ course.studentCount }}</strong> 人学习</span>
          </div>

          <!-- 定价面板 -->
          <div v-if="pricingInfo" class="hero-pricing">
            <div v-if="pricingInfo.free" class="pricing-free">
              <el-tag type="success" size="large" effect="dark">免费</el-tag>
              <span v-if="pricingInfo.feeNote" class="pricing-note">{{ pricingInfo.feeNote }}</span>
            </div>
            <div v-else class="pricing-paid">
              <span v-if="pricingInfo.listPrice && pricingInfo.listPrice > 0 && pricingInfo.finalPrice < pricingInfo.listPrice" class="pricing-original">¥{{ pricingInfo.listPrice }}</span>
              <span class="pricing-final">¥{{ pricingInfo.finalPrice }}</span>
              <span v-if="pricingInfo.feeNote" class="pricing-note">{{ pricingInfo.feeNote }}</span>
              <span v-else class="pricing-note">原价</span>
            </div>
          </div>
          <div v-else-if="!pricingLoading" class="hero-price-info">
            <span v-if="course.freeAccessScopeLabel" class="hero-price hero-price--free">
              <el-tag type="success" size="small" effect="light">{{ course.freeAccessScopeLabel }}</el-tag>
            </span>
            <span v-else-if="course.isFree || !course.price" class="hero-price hero-price--free">免费</span>
            <span v-else class="hero-price">
              <template v-if="course.listPrice && course.listPrice > 0">¥{{ course.listPrice }}</template>
              <template v-else>¥{{ course.price }}</template>
            </span>
          </div>
          <div v-else class="hero-price-info">
            <el-icon class="loading-icon"><Loading /></el-icon>
          </div>
          <div class="hero-actions">
            <template v-if="!isLoggedIn">
              <el-button type="primary" size="large" @click="goLogin">登录后学习</el-button>
            </template>
            <template v-else-if="isEnrolled">
              <el-button v-if="isWaitlisted" type="info" size="large" disabled>候补中</el-button>
              <el-button v-else type="primary" size="large" @click="goLearn">继续学习</el-button>
            </template>
            <template v-else>
              <!-- P1C-005: 课程状态为 CLOSED(5)/ARCHIVED(6) 时禁用操作按钮 -->
              <el-button v-if="course.status === 5" type="info" size="large" disabled>课程已下架</el-button>
              <el-button v-else-if="course.status === 6" type="info" size="large" disabled>课程已结束</el-button>
              <el-button v-else type="primary" size="large" :loading="enrollLoading" @click="handleEnroll">
                {{ isPaidForMe ? '立即购买' : '立即参加' }}
              </el-button>
              <el-button v-if="isPaidForMe && course.status !== 5 && course.status !== 6" size="large" @click="handleAddCart">
                <el-icon><ShoppingCart /></el-icon>加入购物车
              </el-button>
            </template>
          </div>
        </div>
      </div>

      <!-- ====== Tab 导航 ====== -->
      <div class="tab-nav">
        <button :class="{ active: activeTab === 'detail' }" @click="activeTab = 'detail'">课程详情</button>
        <button :class="{ active: activeTab === 'review' }" @click="activeTab = 'review'">课程评价</button>
      </div>

      <!-- ====== 主内容区 ====== -->
      <div class="detail-body" v-if="activeTab === 'detail'">
        <div class="detail-main">
          <!-- 课程介绍 -->
          <div class="section-card">
          <div class="section-head">
            <el-icon :size="20" class="section-head-icon"><Notebook /></el-icon>
            <h2 class="section-title">课程介绍</h2>
          </div>
            <div class="section-body">
              <p v-if="course.description" class="desc-text">{{ course.description }}</p>
              <p v-else class="desc-text desc-text--empty">暂无课程介绍</p>
            </div>
          </div>

          <!-- 课程大纲 / 幻灯片 -->
          <div class="section-card">
          <div class="section-head">
            <el-icon :size="20" class="section-head-icon">
              <List v-if="!isInteractive" /><Present v-else />
            </el-icon>
            <h2 class="section-title">{{ isInteractive ? '幻灯片' : '课程大纲' }}</h2>
              <span class="section-count">共 {{ isInteractive ? slides.length : courseChapters.length }} {{ isInteractive ? '页' : '章节' }}</span>
            </div>
            <div class="section-body">
              <!-- 互动课程: 幻灯片手风琴 -->
              <template v-if="isInteractive">
                <el-collapse v-if="slides.length > 0" v-model="activeChapters">
                  <el-collapse-item v-for="(sp, idx) in slides" :key="idx" :name="'s' + sp.pageNumber">
                    <template #title>
                      <span class="outline-idx">{{ sp.pageNumber }}</span>
                      <span class="outline-title">第 {{ sp.pageNumber }} 页</span>
                      <el-tag v-if="sp.audioDuration" size="small" type="success" effect="plain">已配音</el-tag>
                      <el-tag v-else size="small" type="info" effect="plain">待配音</el-tag>
                      <span v-if="sp.audioDuration" class="outline-duration">{{ formatDuration(sp.audioDuration) }}</span>
                    </template>
                    <p v-if="sp.extractedText" class="outline-desc">{{ sp.extractedText }}</p>
                    <el-button size="small" type="primary" text @click.stop="goToSlidePlayer">开始学习</el-button>
                  </el-collapse-item>
                </el-collapse>
                <el-empty v-else description="暂无幻灯片，请教师上传课件" :image-size="60" />
              </template>
              <!-- 视频课程: 章节手风琴 -->
              <template v-else>
                <el-collapse v-if="courseChapters.length > 0" v-model="activeChapters">
                  <el-collapse-item v-for="(ch, idx) in courseChapters" :key="ch.id" :name="ch.id">
                    <template #title>
                      <span class="outline-idx">{{ idx + 1 }}</span>
                      <span class="outline-title">{{ ch.title }}</span>
                      <el-tag v-if="ch.chapterType === 'VIDEO'" size="small" type="primary" effect="plain">📹 视频课</el-tag>
                      <el-tag v-else-if="ch.chapterType === 'INTERACTIVE'" size="small" type="success" effect="plain">🎯 互动课</el-tag>
                      <el-tag v-else-if="ch.chapterType === 'EXERCISE'" size="small" type="warning" effect="plain">📝 练习</el-tag>
                      <el-tag v-else-if="ch.chapterType === 'OFFLINE'" size="small" type="info" effect="plain">🏫 线下课 (需线下授课)</el-tag>
                      <el-tag v-else size="small" type="info" effect="plain">—</el-tag>
                      <span class="outline-duration">{{ formatDuration(ch.duration) }}</span>
                    </template>
                    <p v-if="ch.description" class="outline-desc">{{ ch.description }}</p>
                    <el-button size="small" type="primary" text @click.stop="handleChapterClick(ch)">开始学习</el-button>
                  </el-collapse-item>
                </el-collapse>
                <el-empty v-else description="暂无章节" :image-size="60" />
              </template>
            </div>
          </div>
        </div>

        <!-- 右侧边栏 -->
        <div class="detail-side">
          <!-- 教师卡片 -->
          <div class="side-card">
            <h3 class="side-card-title">授课教师</h3>
            <div class="teacher-block">
              <el-avatar v-if="teacher.avatar" :size="64" :src="teacher.avatar" :alt="(teacher.realName || course.teacherName || '教师') + '头像'" />
              <el-avatar v-else :size="64">{{ (teacher.realName || course.teacherName || '教').charAt(0) }}</el-avatar>
              <div class="teacher-info">
                <p class="teacher-name">{{ teacher.realName || course.teacherName || '暂无信息' }}</p>
                <p class="teacher-dept">{{ teacher.departmentName || '' }}</p>
              </div>
            </div>
          </div>

          <!-- 课程信息 -->
          <div class="side-card">
            <h3 class="side-card-title">课程信息</h3>
            <div class="info-list">
              <div class="info-item"><span class="info-label">课程类型</span><span class="info-value">{{ isInteractive ? '互动课程' : '视频课程' }}</span></div>
              <div class="info-item" v-if="pricingInfo">
                <span class="info-label">价格</span>
                <span class="info-value price">
                  <template v-if="pricingInfo.free">
                    <el-tag type="success" size="small" effect="light">免费</el-tag>
                    <span v-if="pricingInfo.feeNote" class="pricing-side-note">{{ pricingInfo.feeNote }}</span>
                  </template>
                  <template v-else>
                    <span v-if="pricingInfo.listPrice && pricingInfo.listPrice > 0 && pricingInfo.finalPrice < pricingInfo.listPrice" class="price-original">¥{{ pricingInfo.listPrice }}</span>
                    ¥{{ pricingInfo.finalPrice }}
                  </template>
                </span>
              </div>
              <div class="info-item" v-else-if="course.price && !course.isFree"><span class="info-label">价格</span><span class="info-value price">¥{{ course.price }}</span></div>
              <div class="info-item" v-if="course.studentCount"><span class="info-label">学习人数</span><span class="info-value">{{ course.studentCount }}</span></div>
              <div class="info-item" v-if="course.creditHours"><span class="info-label">学分</span><span class="info-value">{{ course.creditHours }}</span></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 评价 Tab -->
      <div class="detail-body" v-if="activeTab === 'review'">
        <div class="detail-main">
          <div class="section-card">
            <div class="section-head">
              <el-icon :size="20" class="section-head-icon"><Star /></el-icon>
              <h2 class="section-title">课程评价</h2>
              <el-button size="small" text type="primary" @click="openReviewDialog">写评价</el-button>
            </div>
            <div class="section-body" v-loading="reviewLoading">
              <div v-if="reviews.length > 0" class="review-list">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-top">
                    <el-avatar :size="36" :src="r.userAvatar" :alt="(r.userRealName || '用户') + '头像'">{{ (r.userRealName || '匿').charAt(0) }}</el-avatar>
                    <span class="review-user">{{ r.userRealName || '匿名用户' }}</span>
                    <el-rate v-model="r.rating" disabled size="small" />
                  </div>
                  <p class="review-content">{{ r.content }}</p>
                  <div class="review-footer">
                    <span class="review-time">{{ formatTime(r.createdAt) }}</span>
                    <el-button link size="small" :disabled="!canReply" @click="handleReply(r)">回复</el-button>
                    <el-button link size="small" type="warning" @click.stop="openReportDialog('REVIEW', r.id)">举报</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-else description="暂无评价" :image-size="60" />
              <!-- P2-001: 查看更多评价按钮 -->
              <div v-if="reviews.length > 0 && reviews.length >= 5" class="review-more-wrap" style="text-align:center;margin-top:var(--space-4)">
                <el-button text type="primary" @click="handleLoadMoreReviews">
                  查看更多评价
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportDialog.visible" title="举报" width="400px" @close="reportDialog.reason = ''">
      <el-form>
        <el-form-item label="举报原因">
          <el-input v-model="reportDialog.reason" type="textarea" :rows="3" placeholder="请描述举报原因..." maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="reportDialog.submitting" @click="submitReport">提交举报</el-button>
      </template>
    </el-dialog>

    <!-- 写评价弹窗 -->
    <el-dialog v-model="reviewDialogVisible" title="写评价" width="480px">
        <el-form :model="reviewForm" :rules="reviewRules">
          <el-form-item label="评分" prop="rating">
            <el-rate v-model="reviewForm.rating" />
          </el-form-item>
          <el-form-item label="评价" prop="content">
            <el-input v-model="reviewForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="reviewDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="reviewSubmitting" @click="handleSubmitReview">提交评价</el-button>
        </template>
      </el-dialog>

    <!-- 回复评价弹窗 -->
    <el-dialog v-model="replyDialogVisible" title="回复评价" width="480px">
      <el-form :model="replyForm" :rules="replyRules">
        <el-form-item label="回复" prop="content">
          <el-input v-model="replyForm.content" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="请输入回复内容..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="replySubmitting" @click="handleSubmitReply">提交回复</el-button>
      </template>
    </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, User, Notebook, List, Present, VideoPlay, Close, Loading, ShoppingCart, ArrowRight } from '@element-plus/icons-vue'
import { getCourseById, getMyCoursePrice } from '@/api/course'
import { getPublicProfile } from '@/api/user'
import { getVideos } from '@/api/video'
import { enroll as enrollApi, getMyEnrollments, getCourseRanking } from '@/api/enrollment'
import { useCartStore } from '@/store/cart'
import { createOrder, payOrder } from '@/api/order'
import { getDefaultCover } from '@/utils/coverHelper'
import { createReview, getReviews } from '@/api/course-review'
import { createReport } from '@/api/review'
import { getLearningProgress } from '@/api/learning-progress'
import { getSlidePages } from '@/plugins/interactive/api/slide'
import { useUserStore } from '@/store/user'
import { getToken } from '@/utils/auth'
import Hls from 'hls.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const cartStore = useCartStore()

const courseId = computed(() => route.params.id)
const course = ref({})
const pricingInfo = ref(null)
const pricingLoading = ref(false)
const isPaidForMe = computed(() => {
  if (pricingInfo.value) return !pricingInfo.value.free && (pricingInfo.value.finalPrice || 0) > 0
  return !!(course.value.price && !course.value.isFree)
})
const courseChapters = computed(() => course.value.chapters || [])
const isInteractive = computed(() => course.value.courseType === 'INTERACTIVE')
const slides = ref([])
const slidesLoading = ref(false)
const teacher = ref({})
const courseLoading = ref(true)
const courseNotFound = ref(false)
const teacherLoading = ref(false)
const enrollLoading = ref(false)
const isEnrolled = ref(false)
const isWaitlisted = ref(false)
const reviewLoading = ref(false)
const reviews = ref([])
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const rankingList = ref([])
const activeChapters = ref([])
const activeTab = ref('detail')

const reviewForm = ref({ rating: 5, content: '' })
const reviewRules = { rating: [{ required: true, message: '请选择评分', trigger: 'change' }], content: [{ required: true, message: '请输入评价内容', trigger: 'blur' }, { max: 500, message: '评价内容不超过500字', trigger: 'blur' }] }
const isLoggedIn = computed(() => userStore.isLoggedIn)
const hasProgress = ref(false)

// P1I-008: canReply 应基于用户角色和选课状态，而非课程状态
const canReply = computed(() => {
  // 未登录不可回复
  if (!isLoggedIn.value) return false
  // 教师/管理员/教务处可以回复
  const role = userStore.role
  if (role === 'TEACHER' || role === 'ADMIN' || role === 'ACADEMIC') return true
  // 学生需已选课才能回复
  return isEnrolled.value
})

const difficultyText = computed(() => {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难', BEGINNER: '初级', INTERMEDIATE: '中级', ADVANCED: '高级' }
  return map[course.value.difficulty] || course.value.difficulty || ''
})

// 内嵌视频播放
const showPlayer = ref(false)
const videoLoading = ref(false)
const previewVideoUrl = ref('')
let hlsInstance = null
const videoRef = ref(null)

const handlePlayPreview = async () => {
  if (isInteractive.value) { goLearn(); return }
  if (previewVideoUrl.value) { showPlayer.value = true; nextTick(() => initInlinePlayer()); return }
  videoLoading.value = true
  try {
    const res = await getVideos({ courseId: courseId.value, page: 0, size: 1 })
    const items = res.data?.items || []
    if (items.length > 0) {
      const v = items[0]
      previewVideoUrl.value = v.hlsUrl || v.url || ''
    }
    if (previewVideoUrl.value) {
      showPlayer.value = true
      nextTick(() => initInlinePlayer())
    } else {
      ElMessage.info('暂无课程预览视频')
    }
  } catch { ElMessage.info('暂无课程预览视频') }
  finally { videoLoading.value = false }
}

const initInlinePlayer = () => {
  const video = videoRef.value
  if (!video || !previewVideoUrl.value) return
  if (Hls.isSupported()) {
    if (hlsInstance) hlsInstance.destroy()
    hlsInstance = new Hls({ xhrSetup: (xhr) => { const t = getToken(); if (t) xhr.setRequestHeader('Authorization', 'Bearer ' + t) } })
    hlsInstance.loadSource(previewVideoUrl.value)
    hlsInstance.attachMedia(video)
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => video.play())
    hlsInstance.on(Hls.Events.ERROR, (e, d) => { if (d.fatal) { stopPreview(); ElMessage.error('视频播放出错') } })
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = previewVideoUrl.value; video.play()
  }
}

const stopPreview = () => {
  showPlayer.value = false
  if (hlsInstance) { hlsInstance.destroy(); hlsInstance = null }
  if (videoRef.value) { videoRef.value.pause(); videoRef.value.src = '' }
}

const isMobile = ref(window.innerWidth <= 768)
const handleResize = () => { isMobile.value = window.innerWidth <= 768 }
window.addEventListener('resize', handleResize)
onBeforeUnmount(() => { window.removeEventListener('resize', handleResize); if (hlsInstance) { hlsInstance.destroy(); hlsInstance = null } })

const defaultCoverUrl = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="280" height="180" fill="%23e0e0e0"><rect width="280" height="180" rx="8"/><text x="140" y="95" text-anchor="middle" fill="%23999" font-size="16">暂无封面</text></svg>')
const handleCoverError = (e) => { e.target.src = defaultCoverUrl }

// 课程章节时长格式化: 数据库存的是 **秒** (10800 = 3h, 14400 = 4h),前端曾误按分钟处理
// 导致显示 "180h 240h" 这种离谱值,真实时长只有几小时。修复: 改用 /3600 换算。
const formatDuration = (seconds) => {
  if (!seconds && seconds !== 0) return '-'
  const totalMin = Math.floor(seconds / 60)  // 先转分钟
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return h === 0 ? `${m}m` : m === 0 ? `${h}h` : `${h}h${m}m`
}
const formatTime = (t) => { if (!t) return ''; const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }

const fetchCourse = async () => {
  if (!courseId.value) return; courseLoading.value = true
  try { 
    const { data } = await getCourseById(courseId.value)
    course.value = data || {}
    // 客户体验修复 v1.7.0: 课程 coverUrl 通常为 null,补上类别感知默认封面
    if (course.value && !course.value.coverUrl) {
      course.value.coverUrl = getDefaultCover(course.value)
    }
    if (!data?.id) courseNotFound.value = true 
  }
  catch (e) { if (e.response?.status === 404) courseNotFound.value = true; else ElMessage.error('获取课程信息失败') }
  finally { courseLoading.value = false }
}

const fetchPricingInfo = async () => {
  if (!courseId.value || !isLoggedIn.value) return
  pricingLoading.value = true
  try {
    const { data } = await getMyCoursePrice(courseId.value)
    pricingInfo.value = data || null
  } catch (e) {
    // 静默失败，使用默认价格展示
    pricingInfo.value = null
  } finally {
    pricingLoading.value = false
  }
}

const fetchTeacher = async () => {
  if (!course.value.teacherId) return; teacherLoading.value = true
  try { const { data } = await getPublicProfile(course.value.teacherId); teacher.value = data || {} }
  catch (e) { console.warn('[CourseDetail] fetchTeacher 获取教师信息失败', e); teacher.value = {}; ElMessage.error('获取教师信息失败，请稍后重试') }
  finally { teacherLoading.value = false }
}

const checkEnrollment = async () => {
  if (!isLoggedIn.value || !courseId.value) return
  const uid = userStore.userInfo?.id; if (!uid) return
  try { const { data } = await getMyEnrollments(); const list = Array.isArray(data) ? data : (data?.items || []); const match = list.find(e => String(e.courseId) === String(courseId.value)); isEnrolled.value = match && ['ENROLLED','APPROVED','COMPLETED','WAITLIST','SUSPENDED'].includes(match.enrollmentStatus); isWaitlisted.value = match?.enrollmentStatus === 'WAITLIST' }
  catch (e) { console.warn('[CourseDetail] checkEnrollment 获取选课状态失败', e); isEnrolled.value = false }
}

const handleEnroll = async () => {
  if (!isLoggedIn.value) { goLogin(); return }
  const uid = userStore.userInfo?.id; if (!uid) { ElMessage.error('用户信息未加载'); return }
  enrollLoading.value = true
  try {
    // 使用 pricingInfo 判断是否免费（考虑了免费范围/折扣）
    const isFreeForMe = pricingInfo.value?.free || !course.value.price || course.value.isFree
    const finalPrice = pricingInfo.value?.finalPrice ?? course.value.price ?? 0

    const enrollTarget = () => {
      const isInteractive = course.value?.courseType === 'INTERACTIVE'
      return isInteractive
        ? `/student/courses/${courseId.value}/slides/player`
        : `/student/learning?courseId=${courseId.value}`
    }

    if (!isFreeForMe && finalPrice > 0) {
      const { data: order } = await createOrder({ courseId: courseId.value })
      if (order.status === 'PAID') { isEnrolled.value = true; ElMessage.success('选课成功'); router.push(enrollTarget()); return }
      await ElMessageBox.confirm(
        `确认支付 ¥${Number(finalPrice).toFixed(2)}？${pricingInfo.value?.feeNote ? '\n' + pricingInfo.value.feeNote : ''}`,
        '确认支付',
        { confirmButtonText: '支付', cancelButtonText: '取消', type: 'info' }
      )
      await payOrder(order.id, 'BALANCE'); isEnrolled.value = true; ElMessage.success('支付成功'); router.push(enrollTarget())
      return
    }
    // 免费课程：增加确认环节
    const freeNote = pricingInfo.value?.feeNote ? `（${pricingInfo.value.feeNote}）` : ''
    await ElMessageBox.confirm(`确认加入学习？${freeNote}`, '加入课程', { confirmButtonText: '确认加入', cancelButtonText: '取消', type: 'info' })
    await enrollApi({ userId: uid, courseId: courseId.value, sourceChannel: 'SEARCH' }); ElMessage.success('报名成功'); isEnrolled.value = true; router.push(enrollTarget())
  } catch (e) {
    if (e?.toString().includes('cancel')) {
      ElMessage.info('已取消支付')
      return
    }
    if (e?.response?.data?.code === 8002 || e?.response?.status === 409) isEnrolled.value = true
  } finally { enrollLoading.value = false }
}

async function handleAddCart() {
  if (!isLoggedIn.value) { goLogin(); return }
  const c = course.value
  if (!c) return
  const added = await cartStore.addItem({
    id: Number(courseId.value),
    title: c.title,
    coverUrl: c.coverUrl,
    price: pricingInfo.value?.finalPrice ?? c.price,
    isFree: pricingInfo.value?.free ?? c.isFree,
    teacherName: c.teacherName,
  })
  if (added) ElMessage.success('已加入购物车')
  else ElMessage.info('该课程已在购物车中')
}

const handleChapterClick = (row) => {
  if (row.chapterType === 'EXERCISE') {
    router.push(`/student/chapters/${row.id}/exercises`)
  } else if (row.chapterType === 'INTERACTIVE') {
    router.push(`/student/courses/${courseId.value}/slides/player?chapterId=${row.id}`)
  } else if (row.chapterType === 'OFFLINE') {
    router.push(`/student/chapters/${row.id}/offline`)
  } else {
    router.push(`/student/learning?courseId=${courseId.value}&chapterId=${row.id}`)
  }
}
const goLogin = () => router.push({ path: '/login', query: { redirect: route.fullPath } })
const goLearn = () => {
  if (isWaitlisted.value) {
    ElMessage.info('您处于候补队列，暂不可学习')
    return
  }
  router.push(isInteractive.value ? `/student/courses/${courseId.value}/slides/player` : `/student/learning?courseId=${courseId.value}`)
}
const goToSlidePlayer = () => router.push(`/student/courses/${courseId.value}/slides/player`)

// P2-001: 分页加载评价
const reviewPage = ref(0)
const reviewTotalElements = ref(0)
const REVIEW_PAGE_SIZE = 5

const fetchReviews = async (append = false) => {
  if (!courseId.value) return; reviewLoading.value = true
  try {
    const { data } = await getReviews(courseId.value, { page: reviewPage.value, size: REVIEW_PAGE_SIZE })
    const items = data?.items || data || []
    if (append) {
      reviews.value = [...reviews.value, ...items]
    } else {
      reviews.value = items
    }
    reviewTotalElements.value = data?.totalElements || items.length
  }
  catch (e) { console.warn('[CourseDetail] fetchReviews 获取评价失败', e); ElMessage.warning('评价数据加载失败'); reviews.value = [] }
  finally { reviewLoading.value = false }
}

const handleLoadMoreReviews = async () => {
  reviewPage.value++
  await fetchReviews(true)
}

const fetchRanking = async () => {
  if (!courseId.value) return
  try { const { data } = await getCourseRanking(courseId.value, { limit: 10 }); rankingList.value = data || [] } catch (e) { console.warn('[CourseDetail] fetchRanking 获取排行失败', e); ElMessage.warning('排行榜加载失败'); rankingList.value = [] }
}

const checkProgress = async () => {
  if (!isLoggedIn.value || !courseId.value) return
  try {
    const { data } = await getLearningProgress({ courseId: courseId.value })
    // OP-0042: 检查任意进度记录 — 兼容无视频章节（练习完成/线下签到）
    hasProgress.value = !!(data && Array.isArray(data) && data.some(item =>
      item.completed ||
      (item.videoProgress != null && item.videoProgress >= 80) ||
      item.exerciseCompleted ||
      item.offlineAttended
    ))
  } catch (e) {
    hasProgress.value = false
  }
}

const openReviewDialog = () => {
  if (!isLoggedIn.value) { ElMessage.warning('请先登录'); return goLogin() }
  if (!isEnrolled.value) { ElMessage.warning('请先选修该课程'); return }
  if (!hasProgress.value) { ElMessage.warning('请完成课程学习后再评价（学习进度 ≥ 80%）'); return }
  reviewForm.value = { rating: 5, content: '' }; reviewDialogVisible.value = true
}
const reportDialog = reactive({ visible: false, targetType: '', targetId: null, reason: '', submitting: false })

const openReportDialog = (type, id) => {
  reportDialog.targetType = type
  reportDialog.targetId = id
  reportDialog.reason = ''
  reportDialog.visible = true
}

const submitReport = async () => {
  if (!reportDialog.reason.trim()) { ElMessage.warning('请输入举报原因'); return }
  reportDialog.submitting = true
  try {
    await createReport({
      targetType: reportDialog.targetType,
      targetId: reportDialog.targetId,
      reason: reportDialog.reason.trim()
    })
    ElMessage.success('举报已提交，管理员将审核')
    reportDialog.visible = false
  } catch (e) { ElMessage.error(e?.response?.data?.message || '提交失败') }
  finally { reportDialog.submitting = false }
}

// P1C-006: 实现回复评价功能
const replyDialogVisible = ref(false)
const replyTarget = ref(null)
const replyForm = ref({ content: '' })
const replySubmitting = ref(false)
const replyRules = { content: [{ required: true, message: '请输入回复内容', trigger: 'blur' }, { max: 500, message: '回复内容不超过500字', trigger: 'blur' }] }

const handleReply = (review) => {
  replyTarget.value = review
  replyForm.value = { content: '' }
  replyDialogVisible.value = true
}

const handleSubmitReply = async () => {
  if (!replyForm.value.content.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  if (!replyTarget.value?.id) return
  replySubmitting.value = true
  try {
    await createReview(courseId.value, {
      content: replyForm.value.content.trim(),
      parentId: replyTarget.value.id
    })
    ElMessage.success('回复成功')
    replyDialogVisible.value = false
    fetchReviews()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '回复失败，请重试')
  } finally {
    replySubmitting.value = false
  }
}

const handleSubmitReview = async () => {
  if (!reviewForm.value.rating) { ElMessage.warning('请选择评分'); return }
  reviewSubmitting.value = true
  try { await createReview(courseId.value, { rating: reviewForm.value.rating, content: reviewForm.value.content }); ElMessage.success('评价提交成功'); reviewDialogVisible.value = false; fetchReviews(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '提交失败，请重试') }
  finally { reviewSubmitting.value = false }
}

const fetchSlides = async () => { slidesLoading.value = true; try { const { data } = await getSlidePages(courseId.value); slides.value = data || [] } catch (e) { console.warn('[CourseDetail] fetchSlides 获取课件失败', e); slides.value = [] } finally { slidesLoading.value = false } }

onMounted(async () => { await fetchCourse(); if (courseNotFound.value) return; if (isInteractive.value) fetchSlides(); reviewPage.value = 0; await Promise.all([fetchTeacher(), checkEnrollment(), checkProgress(), fetchReviews(), fetchRanking(), fetchPricingInfo()]) })
</script>

<style scoped>
/* ====== 全局 ====== */
.course-detail-page {
  padding: var(--space-6);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
  background: var(--el-bg-color-page);
  padding-bottom: var(--space-8);
}
.not-found-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
.course-skeleton {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-6) 0;
}

/* ====== 统一容器 ====== */
.detail-breadcrumb,
.hero-card,
.tab-nav,
.detail-body {
  max-width: 1200px;
  margin-left: auto;
  margin-right: auto;
}

/* ====== 面包屑 ====== */
.detail-breadcrumb {
  padding: var(--space-4) 0 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-placeholder);
}
.detail-breadcrumb a {
  color: var(--el-text-color-secondary);
  text-decoration: none;
  transition: color var(--duration-base) var(--ease-out);
}
.detail-breadcrumb a:hover { color: var(--role-primary); }
.bc-sep { margin: 0 var(--space-2); color: var(--el-border-color); }
.bc-current { color: var(--el-text-color-primary); font-weight: var(--weight-medium); }

/* ====== Hero Card ====== */
.hero-card {
  margin-top: var(--space-5);
  margin-bottom: var(--space-1);
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-2xl);
  display: flex;
  overflow: hidden;
  box-shadow: var(--shadow-tinted-lg);
}
.hero-left { width: 520px; flex-shrink: 0; }
.hero-img-box {
  width: 100%;
  height: 270px;
  background: var(--el-fill-color-light);
  position: relative;
  cursor: pointer;
}
.hero-img-box:hover .hero-play-btn:not(.loading) {
  transform: translate(-50%,-50%) scale(1.1);
  background: var(--role-primary);
}
.hero-player-active { cursor: default; }
.hero-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-img-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, var(--el-fill-color), var(--el-fill-color-light));
}
.hero-video { width: 100%; height: 100%; object-fit: contain; background: #000; display: block; }
.hero-play-btn {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  width: 72px; height: 72px; border-radius: 50%;
  background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark));
  display: flex; align-items: center; justify-content: center;
  transition: all var(--duration-base) var(--ease-out);
  pointer-events: none;
  box-shadow: 0 4px 24px color-mix(in srgb, var(--role-primary) 35%, transparent);
  animation: hero-pulse 3s ease-in-out infinite;
}

@keyframes hero-pulse {
  0%, 100% { box-shadow: 0 4px 24px color-mix(in srgb, var(--role-primary) 35%, transparent); }
  50% { box-shadow: 0 4px 40px color-mix(in srgb, var(--role-primary) 50%, transparent), 0 0 0 8px color-mix(in srgb, var(--role-primary) 10%, transparent); }
}

.hero-img-box:hover .hero-play-btn:not(.loading) {
  transform: translate(-50%,-50%) scale(1.08);
}
.hero-play-btn.loading { pointer-events: none; }
.hero-close-player {
  position: absolute; top: var(--space-2); right: var(--space-2);
  width: 32px; height: 32px; border-radius: 50%;
  border: none; background: rgba(0,0,0,.55);
  color: #fff; display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: background var(--duration-base); z-index: 10;
}
.hero-close-player:hover { background: rgba(0,0,0,.8); }
.loading-icon { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.hero-right {
  flex: 1; padding: var(--space-7) var(--space-8);
  display: flex; flex-direction: column;
}
.hero-title {
  font-size: 32px;
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-3);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  text-wrap: balance;
}
.hero-subtitle {
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
  margin: 0 0 var(--space-4);
  line-height: var(--leading-relaxed);
}
.hero-tags { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.hero-stats {
  display: flex; gap: var(--space-5);
  align-items: center; font-size: var(--text-sm);
  color: var(--el-text-color-secondary); margin-bottom: var(--space-5);
}
.hero-stat { display: flex; align-items: center; gap: var(--space-1); }
.hero-stat .el-icon { color: var(--el-color-warning); }
.hero-stat strong { color: var(--el-text-color-primary); margin: 0 2px; font-weight: var(--weight-semibold); }
.hero-price {
  margin-left: auto; font-size: 28px;
  font-weight: var(--weight-bold); color: var(--role-primary);
  font-variant-numeric: tabular-nums;
}
.hero-price--free { color: var(--el-color-success); font-weight: var(--weight-medium); font-size: 22px; }

/* 定价面板 */
.hero-pricing { margin: var(--space-2) 0; }
.pricing-free { display: flex; align-items: center; gap: var(--space-2); }
.pricing-paid { display: flex; align-items: baseline; gap: var(--space-2); flex-wrap: wrap; }
.pricing-original { font-size: var(--text-base); color: var(--el-text-color-disabled); text-decoration: line-through; }
.pricing-final { font-size: 28px; font-weight: var(--weight-bold); color: var(--role-primary); font-variant-numeric: tabular-nums; }
.pricing-note { font-size: var(--text-xs); color: var(--el-color-success); }
.hero-price-info { margin: var(--space-2) 0; display: flex; align-items: center; gap: var(--space-2); }
.pricing-side-note { font-size: var(--text-xs); color: var(--el-color-success); margin-left: var(--space-1); }
.price-original { font-size: var(--text-xs); color: var(--el-text-color-disabled); text-decoration: line-through; margin-right: var(--space-1); }
.hero-price-info .loading-icon { animation: rotating 1s linear infinite; }

.hero-actions { margin-top: auto; }
.hero-actions .el-button--primary {
  background: linear-gradient(135deg, var(--role-primary), var(--role-primary-dark));
  border: none;
  padding: var(--space-3) var(--space-6);
  font-size: var(--text-md);
  border-radius: var(--radius-md);
  transition: all var(--duration-base) var(--ease-out);
  min-height: 44px;  /* P1-C 修复 Round 3: 移动端 Apple HIG 44px 触控目标 */
}
.hero-actions .el-button {
  min-height: 44px;  /* P1-C 修复: iPhone 用户点错 (客户体验报告 P1-4) */
}
.hero-actions .el-button--primary:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-primary);
}

/* ====== Tab Nav ====== */
.tab-nav {
  background: var(--el-bg-color-overlay);
  display: flex;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  box-shadow: var(--shadow-tinted-sm);
  padding: 0 var(--space-2);
  position: relative;
}

.tab-nav::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: var(--space-6);
  right: var(--space-6);
  height: 1px;
  background: var(--el-border-color-lighter);
}
.tab-nav button {
  padding: var(--space-4) var(--space-6);
  border: none;
  background: none;
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-secondary);
  cursor: pointer;
  position: relative;
  transition: all var(--duration-base) var(--ease-out);
}
.tab-nav button::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: var(--space-6);
  right: var(--space-6);
  height: 3px;
  background: var(--role-primary);
  border-radius: var(--radius-pill);
  transform: scaleX(0);
  transition: transform var(--duration-base) var(--ease-out);
}
.tab-nav button:hover { color: var(--role-primary); }
.tab-nav button.active {
  color: var(--role-primary);
  font-weight: var(--weight-semibold);
}
.tab-nav button.active::after {
  transform: scaleX(1);
}

/* ====== Main Body ====== */
.detail-body {
  margin-top: var(--space-5);
  display: flex;
  gap: var(--space-5);
  align-items: flex-start;
}
.detail-main { flex: 1; min-width: 0; }
.detail-side { width: 300px; flex-shrink: 0; }

:deep(html) {
  scroll-behavior: smooth;
}

/* ====== Section Card ====== */
.section-card {
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  padding: var(--space-6);
  margin-bottom: var(--space-4);
  box-shadow: var(--shadow-tinted-sm);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}
.section-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-tinted-lg), var(--shadow-md);
}
.section-head {
  display: flex; align-items: center;
  gap: var(--space-2); margin-bottom: var(--space-4);
}
.section-head-icon {
  color: var(--role-primary);
}
.section-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0;
  letter-spacing: var(--tracking-tight);
}
.section-count {
  font-size: var(--text-sm);
  color: var(--el-text-color-placeholder);
  margin-left: auto;
}
.section-body { padding: 0; }
.desc-text {
  font-size: var(--text-base);
  color: var(--el-text-color-primary);
  line-height: var(--leading-relaxed);
  margin: 0;
  text-wrap: pretty;
}
.desc-text--empty { color: var(--el-text-color-placeholder); }

/* ====== Outline / Slides ====== */
.outline-idx {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; border-radius: var(--radius-sm);
  background: var(--role-primary-light-9);
  color: var(--role-primary);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  margin-right: var(--space-3);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}
.outline-title {
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  flex: 1;
}
.outline-duration {
  font-size: var(--text-sm);
  color: var(--el-text-color-placeholder);
  margin-left: var(--space-3);
}
.outline-desc {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  line-height: var(--leading-relaxed);
  padding-left: 38px;
  margin: 0 0 var(--space-2);
}
.el-collapse { border: none; }
:deep(.el-collapse-item__header) {
  height: auto; min-height: 52px;
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 15px;
  align-items: center;
}
:deep(.el-collapse-item__wrap) { border-bottom: none; }
:deep(.el-collapse-item__content) { padding-bottom: var(--space-3); }

/* ====== Sidebar ====== */
.side-card {
  background: var(--el-bg-color-overlay);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  margin-bottom: var(--space-4);
  box-shadow: var(--shadow-tinted-sm);
}
.side-card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--el-border-color-lighter);
  letter-spacing: var(--tracking-tight);
}
.teacher-block { display: flex; align-items: center; gap: var(--space-3); }
.teacher-name {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-1);
}
.teacher-dept {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin: 0;
}

.info-list { display: flex; flex-direction: column; gap: var(--space-3); }
.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--text-sm);
}
.info-label { color: var(--el-text-color-secondary); }
.info-value { color: var(--el-text-color-primary); font-weight: var(--weight-medium); }
.info-value.price { color: var(--role-primary); font-weight: var(--weight-bold); }

/* ====== Reviews ====== */
.review-list { display: flex; flex-direction: column; gap: var(--space-4); }
.review-item {
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.review-item:last-child { border-bottom: none; padding-bottom: 0; }
.review-top {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-2);
}
.review-user {
  font-size: var(--text-base);
  color: var(--el-text-color-primary);
  flex: 1;
  font-weight: var(--weight-medium);
}
.review-content {
  font-size: var(--text-base);
  color: var(--el-text-color-primary);
  line-height: var(--leading-relaxed);
  margin: 0 0 var(--space-1);
}
.review-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review-time {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
}

/* ====== Responsive ====== */
@media (max-width: 768px) {
  .hero-card { flex-direction: column; margin: 0; border-radius: 0; box-shadow: none; }
  .hero-left { width: 100%; }
  .hero-img-box { height: 200px; }
  .hero-right { padding: var(--space-5); }
  .hero-stats { flex-wrap: wrap; gap: var(--space-3); }
  .hero-price { margin-left: 0; font-size: 22px; }
  .detail-body { flex-direction: column; padding: 0 var(--space-3); }
  .detail-side { width: 100%; }
  .tab-nav {
    position: sticky;
    top: 0;
    z-index: var(--z-sticky);
    border-radius: 0;
  }
}
</style>
