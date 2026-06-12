<!--
  统一学习页面 - 视频+练习一体化
  路由路径: /student/learning
  Phase 5 · Indigo Design
  Author: jackie
-->
<template>
  <div class="learning-view">
    <!-- 1. 渐变顶部导航栏 -->
    <header class="learning-header">
      <div class="header-left">
        <el-button text class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回课程</span>
        </el-button>
      </div>
      <div class="header-center">
        <h1 class="course-title">{{ courseName }}</h1>
      </div>
      <div class="header-right">
        <el-select
          v-model="selectedChapterId"
          placeholder="选择章节"
          class="chapter-select"
          popper-class="chapter-dropdown"
          @change="onChapterChange"
        >
          <el-option
            v-for="ch in chapters"
            :key="ch.id"
            :label="ch.title"
            :value="ch.id"
          />
        </el-select>
      </div>
    </header>

    <div class="learning-body">
      <!-- 2. 左侧章节侧边栏 -->
      <aside class="chapter-sidebar">
        <div class="sidebar-inner">
          <div class="sidebar-title">课程章节</div>
          <div
            v-for="(ch, idx) in chapters"
            :key="ch.id"
            class="chapter-item"
            :class="{ active: selectedChapterId === ch.id }"
            @click="onChapterChange(ch.id)"
          >
            <div class="chapter-indicator">
              <span class="chapter-num">{{ idx + 1 }}</span>
            </div>
            <div class="chapter-content">
              <div class="chapter-title">{{ ch.title }}</div>
              <div class="chapter-tags">
                <span v-if="ch.hasVideo" class="tag tag-video">
                  <el-icon><VideoCamera /></el-icon>
                  视频
                </span>
                <span v-if="ch.hasExercise" class="tag tag-exercise">
                  <el-icon><Edit /></el-icon>
                  {{ ch.exerciseCount }} 练习
                </span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 3. 右侧内容区 -->
      <main class="learning-content">
        <!-- 视频播放区 -->
        <div class="video-container" :class="{ 'has-video': currentVideo }">
          <div v-if="currentVideo" class="video-wrapper">
            <video
              ref="videoRef"
              controls
              class="video-player"
              :src="currentVideo.url"
            >
              <source v-if="currentVideo.url" :src="currentVideo.url" :type="mimeType" />
            </video>
          </div>
          <div v-else class="empty-video">
            <el-icon size="48" color="#CBD5E1"><VideoCamera /></el-icon>
            <p>本章节暂无视频，请选择其他章节</p>
          </div>
        </div>

        <!-- 4. 练习入口卡片 -->
        <div v-if="currentExerciseCount > 0" class="exercise-entry">
          <div class="exercise-card">
            <div class="exercise-icon">
              <el-icon :size="36"><Edit /></el-icon>
            </div>
            <div class="exercise-info">
              <h3 class="exercise-title">随堂练习</h3>
              <p class="exercise-desc">
                本章节共 <strong>{{ currentExerciseCount }}</strong> 道练习题，巩固所学知识
              </p>
              <div class="exercise-progress">
                <el-progress
                  :percentage="progressPercent"
                  :stroke-width="8"
                  :color="progressColor"
                  :show-text="false"
                />
                <span class="progress-text">
                  {{ completedCount }}/{{ currentExerciseCount }} 已完成
                </span>
              </div>
            </div>
            <el-button
              type="primary"
              size="large"
              round
              class="start-btn"
              @click="goExercise"
            >
              <el-icon><CaretRight /></el-icon>
              开始练习
            </el-button>
          </div>
        </div>

        <!-- 无练习时的空状态 -->
        <div v-else class="exercise-empty">
          <div class="empty-card">
            <el-icon size="40" color="#CBD5E1"><Edit /></el-icon>
            <p>本章节暂无练习题</p>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft,
  Edit,
  VideoCamera,
  CaretRight
} from '@element-plus/icons-vue'
import { getMyEnrollments } from '@/api/enrollment'
import { getCourseById } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getVideos } from '@/api/video'
import { getExercises } from '@/api/exercise'
import { getCompletion } from '@/api/learning-progress'

const route = useRoute()
const router = useRouter()

const courseId = ref(parseInt(route.query.courseId) || null)
const courseName = ref('')
const chapters = ref([])
const selectedChapterId = ref(null)
const currentVideo = ref(null)
const currentExerciseCount = ref(0)

// 练习进度
const completedCount = ref(0)

const mimeType = computed(() => {
  if (!currentVideo.value?.url) return 'video/mp4'
  const ext = currentVideo.value.url.split('.').pop()?.toLowerCase()
  const map = { mp4: 'video/mp4', webm: 'video/webm', ogg: 'video/ogg', mov: 'video/quicktime' }
  return map[ext] || 'video/mp4'
})

const progressPercent = computed(() => {
  if (currentExerciseCount.value === 0) return 0
  return Math.round((completedCount.value / currentExerciseCount.value) * 100)
})

const progressColor = computed(() => {
  const p = progressPercent.value
  if (p === 100) return '#10B981'
  if (p >= 50) return '#F59E0B'
  return '#4F46E5'
})

// 从 localStorage 恢复上次学习位置
const savedCourseId = localStorage.getItem('lastLearningCourse')

onMounted(async () => {
  if (!courseId.value && savedCourseId) {
    courseId.value = parseInt(savedCourseId)
  }
  if (!courseId.value) {
    const { data } = await getMyEnrollments({})
    const items = data?.items || data || []
    if (items.length > 0) {
      courseId.value = items[0].courseId
    }
  }
  if (courseId.value) {
    localStorage.setItem('lastLearningCourse', courseId.value)
    await loadCourse(courseId.value)
  }
})

async function loadCourse(cid) {
  const courseRes = await getCourseById(cid)
  courseName.value = courseRes.data?.title || ''

  const chRes = await getChapters({ courseId: cid, size: 100 })
  chapters.value = chRes.data?.items || chRes.data || []

  // 加载每个章节的视频和练习信息
  for (const ch of chapters.value) {
    const vidRes = await getVideos({ chapterId: ch.id, size: 1 })
    ch.videos = vidRes.data?.items || vidRes.data || []
    ch.hasVideo = ch.videos.length > 0

    const exRes = await getExercises({ chapterId: ch.id, size: 50 })
    ch.exercises = exRes.data?.items || exRes.data || []
    ch.exerciseCount = ch.exercises.length
    ch.hasExercise = ch.exerciseCount > 0
  }

  if (chapters.value.length > 0) {
    selectChapter(chapters.value[0].id)
  }
}

async function selectChapter(chapterId) {
  selectedChapterId.value = chapterId
  const ch = chapters.value.find(c => c.id === chapterId)
  if (ch) {
    currentVideo.value = ch.videos[0] || null
    currentExerciseCount.value = ch.exerciseCount

    // 加载该章节的练习完成进度
    try {
      const progressRes = await getCompletion({ chapterId: chapterId })
      completedCount.value = progressRes.data?.completedCount || 0
    } catch {
      completedCount.value = 0
    }
  }
}

function onChapterChange(chapterId) {
  selectChapter(chapterId)
}

function goExercise() {
  router.push(`/student/chapters/${selectedChapterId.value}/exercises`)
}

function goBack() {
  router.push('/student/my-courses')
}
</script>

<style scoped>
/* ===== 变量 ===== */
.learning-view {
  --indigo-primary: #4F46E5;
  --indigo-accent: #6366F1;
  --indigo-light: #EEF2FF;
  --indigo-glow: rgba(79, 70, 229, 0.15);

  --text-title: #1E293B;
  --text-secondary: #64748B;
  --text-muted: #94A3B8;

  --bg-page: #F5F6FA;
  --bg-card: #ffffff;
  --bg-hover: #F8FAFC;

  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-pill: 50px;

  --shadow-card: 0 1px 3px rgba(0, 0, 0, 0.06);
  --shadow-hover: 0 4px 16px rgba(0, 0, 0, 0.08);
  --shadow-indigo: 0 4px 20px rgba(79, 70, 229, 0.15);
  --shadow-indigo-hover: 0 8px 24px rgba(79, 70, 229, 0.2);

  --transition: 200ms ease;

  min-height: calc(100vh - 64px);
  background: var(--bg-page);
  display: flex;
  flex-direction: column;
}

/* ===== 1. 渐变顶部导航栏 ===== */
.learning-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: linear-gradient(135deg, var(--indigo-primary) 0%, var(--indigo-accent) 100%);
  color: white;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
  box-shadow: var(--shadow-indigo);
  gap: 16px;
}

.header-left,
.header-right {
  flex: 1;
  display: flex;
  align-items: center;
}

.header-right {
  justify-content: flex-end;
}

.header-center {
  flex: 2;
  text-align: center;
}

.back-btn {
  color: white !important;
  font-size: 14px;
  opacity: 0.9;
  transition: opacity var(--transition);
}

.back-btn:hover {
  opacity: 1;
  background: rgba(255, 255, 255, 0.1) !important;
}

.back-btn .el-icon {
  margin-right: 4px;
}

.course-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: white;
  letter-spacing: 0.3px;
}

.chapter-select {
  width: 200px;
}

.chapter-select :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.15);
  border: 1.5px solid rgba(255, 255, 255, 0.4);
  border-radius: var(--radius-sm);
  box-shadow: none;
  color: white;
}

.chapter-select :deep(.el-input__wrapper:hover) {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.6);
}

.chapter-select :deep(.el-input__wrapper.is-focus) {
  background: rgba(255, 255, 255, 0.25);
  border-color: white;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}

.chapter-select :deep(.el-input__inner) {
  color: white;
}

.chapter-select :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.7);
}

.chapter-select :deep(.el-select__caret") {
  color: white !important;
}

.chapter-select :deep(.el-select__dropdown") {
  border-radius: var(--radius-md);
}

/* ===== 2. 主体布局 ===== */
.learning-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== 3. 左侧章节侧边栏 ===== */
.chapter-sidebar {
  width: 260px;
  background: var(--bg-card);
  border-radius: var(--radius-md);
  margin: 24px 0 24px 24px;
  box-shadow: var(--shadow-card);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-inner {
  padding: 16px 12px;
  overflow-y: auto;
  flex: 1;
}

.sidebar-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  padding: 0 8px 12px;
  border-bottom: 1px solid #F1F5F9;
  margin-bottom: 12px;
}

.chapter-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 6px;
  transition: background var(--transition), transform var(--transition), box-shadow var(--transition);
  border: 1.5px solid transparent;
}

.chapter-item:hover {
  background: var(--bg-hover);
  transform: translateY(-1px);
}

.chapter-item.active {
  background: var(--indigo-light);
  border-left: 3px solid var(--indigo-primary);
}

.chapter-indicator {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #E2E8F0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition);
}

.chapter-item.active .chapter-indicator {
  background: var(--indigo-primary);
}

.chapter-num {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary);
  transition: color var(--transition);
}

.chapter-item.active .chapter-num {
  color: white;
}

.chapter-content {
  flex: 1;
  min-width: 0;
}

.chapter-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-title);
  margin-bottom: 6px;
  line-height: 1.4;
}

.chapter-item.active .chapter-title {
  color: var(--indigo-primary);
  font-weight: 600;
}

.chapter-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 20px;
  font-weight: 500;
}

.tag-video {
  background: #FEF3C7;
  color: #D97706;
}

.tag-exercise {
  background: #DCFCE7;
  color: #16A34A;
}

/* ===== 4. 右侧内容区 ===== */
.learning-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* ===== 5. 视频播放区 ===== */
.video-container {
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-hover);
  background: #0F172A;
  transition: box-shadow var(--transition);
}

.video-wrapper {
  position: relative;
  width: 100%;
}

.video-player {
  width: 100%;
  display: block;
  background: #0F172A;
  max-height: 500px;
}

/* 空状态 */
.empty-video {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  gap: 16px;
  background: linear-gradient(145deg, #1E293B 0%, #0F172A 100%);
}

.empty-video p {
  color: #64748B;
  font-size: 15px;
  margin: 0;
}

/* ===== 6. 练习入口卡片 ===== */
.exercise-entry {
  width: 100%;
}

.exercise-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 28px 32px;
  box-shadow: var(--shadow-card);
  display: flex;
  align-items: center;
  gap: 24px;
  transition: transform var(--transition), box-shadow var(--transition);
}

.exercise-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-indigo-hover);
}

.exercise-icon {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--indigo-light) 0%, #E0E7FF 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--indigo-primary);
}

.exercise-info {
  flex: 1;
  min-width: 0;
}

.exercise-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-title);
  margin: 0 0 6px;
}

.exercise-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 14px;
}

.exercise-desc strong {
  color: var(--indigo-primary);
}

.exercise-progress {
  display: flex;
  align-items: center;
  gap: 12px;
}

.exercise-progress .el-progress {
  flex: 1;
}

.progress-text {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.start-btn {
  flex-shrink: 0;
  padding: 14px 36px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  border-radius: var(--radius-pill) !important;
  background: linear-gradient(135deg, var(--indigo-primary), var(--indigo-accent)) !important;
  border: none !important;
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.35);
  transition: all var(--transition);
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.start-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 20px rgba(79, 70, 229, 0.5);
}

.start-btn:active {
  transform: scale(0.98);
}

/* 无练习空状态 */
.exercise-empty {
  width: 100%;
}

.empty-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: 40px 32px;
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.empty-card p {
  color: var(--text-muted);
  font-size: 14px;
  margin: 0;
}

/* ===== 7. 响应式 ===== */
@media (max-width: 768px) {
  .learning-header {
    border-radius: 0 0 var(--radius-md) var(--radius-md);
    padding: 12px 16px;
    flex-wrap: wrap;
  }

  .header-left,
  .header-right {
    flex: 0 0 auto;
  }

  .header-center {
    flex: 1 1 100%;
    order: -1;
    text-align: left;
    padding-bottom: 8px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.2);
    margin-bottom: 8px;
  }

  .course-title {
    font-size: 17px;
  }

  .back-btn {
    font-size: 13px;
  }

  .chapter-select {
    width: 160px;
  }

  .chapter-sidebar {
    display: none;
  }

  .learning-content {
    padding: 16px;
    gap: 16px;
  }

  .exercise-card {
    flex-direction: column;
    text-align: center;
    padding: 24px 20px;
    gap: 20px;
  }

  .exercise-info {
    width: 100%;
  }

  .exercise-progress {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }

  .progress-text {
    text-align: center;
  }

  .start-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>