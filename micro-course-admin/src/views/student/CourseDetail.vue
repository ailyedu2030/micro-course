<!--
  课程详情 · MOOC 风格重构
  设计参考: icourse163.org / study.163.com
  1200px 双栏布局 · 白色Hero · 绿色Tab · 左侧内容+右侧边栏
-->
<template>
  <div class="course-detail-page">
    <!-- 404 -->
    <div v-if="courseNotFound" class="not-found-page">
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
            <span v-if="course.price && !course.isFree" class="hero-price">¥{{ course.price }}</span>
            <span v-else class="hero-price hero-price--free">免费</span>
          </div>
          <div class="hero-actions">
            <template v-if="!isLoggedIn">
              <el-button type="primary" size="large" @click="goLogin">登录后学习</el-button>
            </template>
            <template v-else-if="isEnrolled">
              <el-button type="primary" size="large" @click="goLearn">继续学习</el-button>
            </template>
            <template v-else>
              <el-button type="primary" size="large" :loading="enrollLoading" @click="handleEnroll">
                {{ course.price && !course.isFree ? '立即购买' : '立即参加' }}
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
              <el-icon :size="20" color="#00cc7e"><Notebook /></el-icon>
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
              <el-icon :size="20" color="#00cc7e">
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
                      <el-tag v-if="ch.chapterType === 'VIDEO'" size="small" type="primary" effect="plain">视频</el-tag>
                      <el-tag v-else-if="ch.chapterType === 'EXERCISE'" size="small" type="success" effect="plain">练习</el-tag>
                      <el-tag v-else size="small" type="info" effect="plain">{{ ch.chapterType }}</el-tag>
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
              <el-avatar v-if="teacher.avatar" :size="64" :src="teacher.avatar" />
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
              <div class="info-item" v-if="course.price && !course.isFree"><span class="info-label">价格</span><span class="info-value price">¥{{ course.price }}</span></div>
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
              <el-icon :size="20" color="#00cc7e"><Star /></el-icon>
              <h2 class="section-title">课程评价</h2>
              <el-button size="small" text type="primary" @click="openReviewDialog">写评价</el-button>
            </div>
            <div class="section-body" v-loading="reviewLoading">
              <div v-if="reviews.length > 0" class="review-list">
                <div v-for="r in reviews" :key="r.id" class="review-item">
                  <div class="review-top">
                    <el-avatar :size="36" :src="r.userAvatar">{{ (r.userRealName || '匿').charAt(0) }}</el-avatar>
                    <span class="review-user">{{ r.userRealName || '匿名用户' }}</span>
                    <el-rate v-model="r.rating" disabled size="small" />
                  </div>
                  <p class="review-content">{{ r.content }}</p>
                  <span class="review-time">{{ formatTime(r.createdAt) }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无评价" :image-size="60" />
            </div>
          </div>
        </div>
      </div>

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
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, User, Notebook, List, Present, VideoPlay, Close, Loading } from '@element-plus/icons-vue'
import { getCourseById } from '@/api/course'
import { getPublicProfile } from '@/api/user'
import { getVideos } from '@/api/video'
import { enroll as enrollApi, getMyEnrollments, getCourseRanking } from '@/api/enrollment'
import { createOrder, payOrder } from '@/api/order'
import { createReview, getReviews } from '@/api/course-review'
import { getSlidePages } from '@/plugins/interactive/api/slide'
import { useUserStore } from '@/store/user'
import { getToken } from '@/utils/auth'
import Hls from 'hls.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const courseId = computed(() => route.params.id)
const course = ref({})
const courseChapters = computed(() => course.value.chapters || [])
const isInteractive = computed(() => course.value.courseType === 'INTERACTIVE')
const slides = ref([])
const slidesLoading = ref(false)
const teacher = ref({})
const courseLoading = ref(false)
const courseNotFound = ref(false)
const teacherLoading = ref(false)
const enrollLoading = ref(false)
const isEnrolled = ref(false)
const reviewLoading = ref(false)
const reviews = ref([])
const reviewDialogVisible = ref(false)
const reviewSubmitting = ref(false)
const rankingList = ref([])
const activeChapters = ref([])
const activeTab = ref('detail')

const reviewForm = ref({ rating: 5, content: '' })
const reviewRules = { rating: [{ required: true, message: '请选择评分', trigger: 'change' }], content: [{ max: 500, message: '评价内容不超过500字', trigger: 'blur' }] }
const isLoggedIn = computed(() => userStore.isLoggedIn)

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
      previewVideoUrl.value = v.hls_url || v.url || ''
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

const formatDuration = (m) => { if (!m && m !== 0) return '-'; const h = Math.floor(m/60); const s = m%60; return h===0 ? `${s}m` : s===0 ? `${h}h` : `${h}h${s}m` }
const formatTime = (t) => { if (!t) return ''; const d = new Date(t); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}` }

const fetchCourse = async () => {
  if (!courseId.value) return; courseLoading.value = true
  try { const { data } = await getCourseById(courseId.value); course.value = data || {}; if (!data?.id) courseNotFound.value = true }
  catch (e) { if (e.response?.status === 404) courseNotFound.value = true; else ElMessage.error('获取课程信息失败') }
  finally { courseLoading.value = false }
}

const fetchTeacher = async () => {
  if (!course.value.teacherId) return; teacherLoading.value = true
  try { const { data } = await getPublicProfile(course.value.teacherId); teacher.value = data || {} }
  catch { teacher.value = {} }
  finally { teacherLoading.value = false }
}

const checkEnrollment = async () => {
  if (!isLoggedIn.value || !courseId.value) return
  const uid = userStore.userInfo?.id; if (!uid) return
  try { const { data } = await getMyEnrollments(uid); const list = Array.isArray(data) ? data : (data?.items || []); isEnrolled.value = list.some(e => String(e.courseId) === String(courseId.value)) }
  catch { isEnrolled.value = false }
}

const handleEnroll = async () => {
  if (!isLoggedIn.value) { goLogin(); return }
  const uid = userStore.userInfo?.id; if (!uid) { ElMessage.error('用户信息未加载'); return }
  enrollLoading.value = true
  try {
    if (course.value.price && !course.value.isFree) {
      const { data: order } = await createOrder({ userId: uid, courseId: courseId.value })
      if (order.status === 'PAID') { isEnrolled.value = true; ElMessage.success('选课成功'); return }
      await ElMessageBox.confirm(`确认支付 ¥${course.value.price}？`, '确认支付', { confirmButtonText: '支付', cancelButtonText: '取消', type: 'info' })
      await payOrder(order.id, 'BALANCE'); isEnrolled.value = true; ElMessage.success('支付成功')
      return
    }
    await enrollApi({ userId: uid, courseId: courseId.value }); ElMessage.success('报名成功'); isEnrolled.value = true
  } catch (e) {
    if (e?.response?.data?.code === 8002 || e?.response?.status === 409) isEnrolled.value = true
  } finally { enrollLoading.value = false }
}

const handleChapterClick = (row) => {
  router.push(row.chapterType === 'EXERCISE' ? `/student/chapters/${row.id}/exercises` : `/student/learning?courseId=${courseId.value}&chapterId=${row.id}`)
}
const goLogin = () => router.push({ path: '/login', query: { redirect: route.fullPath } })
const goLearn = () => { router.push(isInteractive.value ? `/student/courses/${courseId.value}/slides/player` : `/student/learning?courseId=${courseId.value}`) }
const goToSlidePlayer = () => router.push(`/student/courses/${courseId.value}/slides/player`)

const fetchReviews = async () => {
  if (!courseId.value) return; reviewLoading.value = true
  try { const { data } = await getReviews(courseId.value, { page: 0, size: 5 }); reviews.value = data?.items || data || [] }
  catch { reviews.value = [] }
  finally { reviewLoading.value = false }
}

const fetchRanking = async () => {
  if (!courseId.value) return
  try { const { data } = await getCourseRanking(courseId.value, { limit: 10 }); rankingList.value = data || [] } catch { rankingList.value = [] }
}

const openReviewDialog = () => { if (!isLoggedIn.value) goLogin(); else { reviewForm.value = { rating: 5, content: '' }; reviewDialogVisible.value = true } }
const handleSubmitReview = async () => {
  if (!reviewForm.value.rating) { ElMessage.warning('请选择评分'); return }
  reviewSubmitting.value = true
  try { await createReview(courseId.value, { rating: reviewForm.value.rating, content: reviewForm.value.content }); ElMessage.success('评价提交成功'); reviewDialogVisible.value = false; fetchReviews(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
  catch { ElMessage.error('提交失败，请重试') }
  finally { reviewSubmitting.value = false }
}

const fetchSlides = async () => { slidesLoading.value = true; try { const { data } = await getSlidePages(courseId.value); slides.value = data || [] } catch { slides.value = [] } finally { slidesLoading.value = false } }

onMounted(async () => { await fetchCourse(); if (courseNotFound.value) return; if (isInteractive.value) fetchSlides(); await Promise.all([fetchTeacher(), checkEnrollment(), fetchReviews(), fetchRanking()]) })
</script>

<style scoped>
/* ====== 全局 ====== */
.course-detail-page { background: #f5f5f5; min-height: 100vh; padding-bottom: 40px; }
.not-found-page { display: flex; align-items: center; justify-content: center; min-height: 400px; }

/* ====== 统一容器 ====== */
.detail-breadcrumb,
.hero-card,
.tab-nav,
.detail-body { max-width: 1200px; margin-left: auto; margin-right: auto; }

/* ====== 面包屑 ====== */
.detail-breadcrumb { padding: 16px 0 0; font-size: 13px; color: #999; }
.detail-breadcrumb a { color: #666; text-decoration: none; }
.detail-breadcrumb a:hover { color: #00cc7e; }
.bc-sep { margin: 0 6px; color: #ddd; }
.bc-current { color: #333; }

/* ====== Hero Card ====== */
.hero-card { margin-top: 16px; background: #fff; border-radius: 8px; display: flex; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.06); }
.hero-left { width: 480px; flex-shrink: 0; }
.hero-img-box { width: 100%; height: 270px; background: #f0f0f0; position: relative; cursor: pointer; }
.hero-img-box:hover .hero-play-btn:not(.loading) { transform: translate(-50%,-50%) scale(1.1); background: rgba(0,0,0,.8); }
.hero-player-active { cursor: default; }
.hero-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-img-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f8f9fa, #e9ecef); }
.hero-video { width: 100%; height: 100%; object-fit: contain; background: #000; display: block; }
.hero-play-btn {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
  width: 64px; height: 64px; border-radius: 50%;
  background: rgba(0,0,0,.55); display: flex; align-items: center; justify-content: center;
  transition: all .25s ease; pointer-events: none;
}
.hero-play-btn.loading { pointer-events: none; }
.hero-close-player {
  position: absolute; top: 8px; right: 8px; width: 32px; height: 32px;
  border-radius: 50%; border: none; background: rgba(0,0,0,.6);
  color: #fff; display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: background .2s; z-index: 10;
}
.hero-close-player:hover { background: rgba(0,0,0,.85); }
.loading-icon { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.hero-right { flex: 1; padding: 32px; display: flex; flex-direction: column; }
.hero-title { font-size: 24px; font-weight: 600; color: #333; margin: 0 0 12px; line-height: 1.4; }
.hero-subtitle { font-size: 14px; color: #666; margin: 0 0 16px; line-height: 1.6; }
.hero-tags { display: flex; gap: 8px; margin-bottom: 16px; }
.hero-stats { display: flex; gap: 24px; align-items: center; font-size: 14px; color: #666; margin-bottom: 20px; }
.hero-stat { display: flex; align-items: center; gap: 4px; color: #999; }
.hero-stat .el-icon { color: #ffc53d; }
.hero-stat strong { color: #333; margin: 0 2px; }
.hero-price { margin-left: auto; font-size: 28px; font-weight: 700; color: #ff5722; }
.hero-price--free { color: #00cc7e; }
.hero-actions { margin-top: auto; }

/* ====== Tab Nav ====== */
.tab-nav { background: #fff; display: flex; border-bottom: 1px solid #eee; }
.tab-nav button { padding: 14px 24px; border: none; background: none; font-size: 16px; color: #666; cursor: pointer; border-bottom: 3px solid transparent; transition: all .2s; }
.tab-nav button:hover { color: #00cc7e; }
.tab-nav button.active { color: #00cc7e; border-bottom-color: #00cc7e; font-weight: 600; }

/* ====== Main Body ====== */
.detail-body { margin-top: 20px; display: flex; gap: 20px; align-items: flex-start; }
.detail-main { flex: 1; min-width: 0; }
.detail-side { width: 300px; flex-shrink: 0; }

/* ====== Section Card ====== */
.section-card { background: #fff; border-radius: 8px; padding: 24px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.section-head { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.section-title { font-size: 18px; font-weight: 600; color: #333; margin: 0; }
.section-count { font-size: 13px; color: #999; margin-left: auto; }
.section-body { padding: 0; }
.desc-text { font-size: 14px; color: #333; line-height: 2; margin: 0; }
.desc-text--empty { color: #999; }

/* ====== Outline / Slides ====== */
.outline-idx { display: inline-flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 6px; background: #f0fdf4; color: #00cc7e; font-size: 13px; font-weight: 600; margin-right: 12px; flex-shrink: 0; }
.outline-title { font-size: 16px; font-weight: 500; color: #333; flex: 1; }
.outline-duration { font-size: 13px; color: #999; margin-left: 12px; }
.outline-desc { font-size: 14px; color: #666; line-height: 1.8; padding-left: 38px; margin: 0 0 8px; }
.el-collapse { border: none; }
:deep(.el-collapse-item__header) { height: auto; min-height: 52px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; font-size: 15px; align-items: center; }
:deep(.el-collapse-item__wrap) { border-bottom: none; }
:deep(.el-collapse-item__content) { padding-bottom: 12px; }

/* ====== Sidebar ====== */
.side-card { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,.04); }
.side-card-title { font-size: 15px; font-weight: 600; color: #333; margin: 0 0 16px; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
.teacher-block { display: flex; align-items: center; gap: 12px; }
.teacher-name { font-size: 15px; font-weight: 600; color: #333; margin: 0 0 4px; }
.teacher-dept { font-size: 13px; color: #999; margin: 0; }

.info-list { display: flex; flex-direction: column; gap: 12px; }
.info-item { display: flex; justify-content: space-between; align-items: center; font-size: 14px; }
.info-label { color: #999; }
.info-value { color: #333; font-weight: 500; }
.info-value.price { color: #ff5722; font-weight: 700; }

/* ====== Reviews ====== */
.review-list { display: flex; flex-direction: column; gap: 16px; }
.review-item { padding-bottom: 16px; border-bottom: 1px solid #f5f5f5; }
.review-item:last-child { border-bottom: none; padding-bottom: 0; }
.review-top { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.review-user { font-size: 14px; color: #333; flex: 1; }
.review-content { font-size: 14px; color: #333; line-height: 1.8; margin: 0 0 4px; }
.review-time { font-size: 12px; color: #ccc; }

/* ====== Responsive ====== */
@media (max-width: 768px) {
  .hero-card { flex-direction: column; margin: 0; border-radius: 0; }
  .hero-left { width: 100%; }
  .hero-img-box { height: 200px; }
  .hero-right { padding: 20px; }
  .hero-stats { flex-wrap: wrap; gap: 12px; }
  .hero-price { margin-left: 0; font-size: 22px; }
  .detail-body { flex-direction: column; padding: 0 12px; }
  .detail-side { width: 100%; }
  .tab-nav { position: sticky; top: 0; z-index: 10; }
}
</style>
