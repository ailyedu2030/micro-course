<!--
  统一学习页面 - 网易慕课风格
  路由路径: /student/learning
  Phase 5 · NetEase MOOC Style
  Author: jackie
-->
<template>
  <div class="learning-view" :class="{ 'is-mobile': isMobile }">
    <!-- ===================== 1. 顶部导航栏 ===================== -->
    <header class="learning-header">
      <div class="header-left">
        <el-button text class="back-btn" @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
          <span>返回</span>
        </el-button>
      </div>
      <div class="header-center">
        <h1 class="course-title">{{ course.title || '加载中...' }}</h1>
      </div>
      <div class="header-right">
        <!-- 总体进度 -->
        <div class="progress-indicator" @click="activeTab = 'course'" title="查看课程进度">
          <span class="progress-dots">
            <span
              v-for="i in 10"
              :key="i"
              class="dot"
              :class="{ filled: i <= Math.ceil(totalProgress / 10) }"
            />
          </span>
          <span class="progress-text">{{ totalProgress }}%</span>
        </div>
        <!-- 笔记按钮 -->
        <el-button text class="header-btn" @click="activeTab = 'course'" title="笔记">
          <el-icon><Edit /></el-icon>
          <span>笔记</span>
        </el-button>
        <!-- 收藏按钮 -->
        <el-button text class="header-btn" :class="{ 'is-favorited': isFavorited }" @click="toggleFavorite" :title="isFavorited ? '取消收藏' : '收藏课程'">
          <el-icon><Star /></el-icon>
          <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
        </el-button>
      </div>
    </header>

    <!-- ===================== 2. 顶部 Tab ===================== -->
    <div class="learning-tabs">
      <div class="tab-bar">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="tab-item"
          :class="{ active: activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </button>
      </div>
    </div>

    <!-- ===================== 3. 主体内容 ===================== -->
    <div class="learning-body">
      <!-- 左：主内容区（60%） -->
      <main class="content-main">
        <!-- 视频播放器 -->
        <div class="video-section">
          <div class="video-container">
            <!-- 加载骨架屏 -->
            <div v-if="videoLoading" class="video-skeleton">
              <div class="skeleton-shimmer" />
              <el-icon class="loading-icon" size="48"><Loading /></el-icon>
            </div>
            <!-- 视频元素 -->
            <video
              v-else-if="currentVideo"
              ref="videoRef"
              class="video-player"
              :src="currentVideo.url || currentVideo.playUrl"
              :poster="currentVideo.coverUrl"
              @loadedmetadata="onVideoLoaded"
              @timeupdate="onTimeUpdate"
              @play="isPlaying = true"
              @pause="isPlaying = false"
              @ended="onVideoEnded"
              @waiting="isBuffering = true"
              @canplay="isBuffering = false"
              playsinline
            />
            <!-- 无视频占位 -->
            <div v-else class="video-empty">
              <el-icon size="48" color="#475569"><VideoCamera /></el-icon>
              <p>本章节暂无视频</p>
            </div>

            <!-- 自定义控制栏 -->
            <div v-if="currentVideo && !videoLoading" class="video-controls" :class="{ visible: showControls || !isPlaying }">
              <!-- 进度条 -->
              <div class="progress-wrap" @click="seekVideo" @mousemove="onProgressHover" @mouseleave="hoverTime = null">
                <div class="progress-track">
                  <div class="progress-buffered" :style="{ width: bufferedPercent + '%' }" />
                  <div class="progress-filled" :style="{ width: playPercent + '%' }" />
                  <div class="progress-thumb" :style="{ left: playPercent + '%' }" />
                </div>
                <div v-if="hoverTime !== null" class="hover-time" :style="{ left: hoverX + 'px' }">{{ formatTime(hoverTime) }}</div>
              </div>

              <!-- 控制按钮行 -->
              <div class="controls-row">
                <div class="controls-left">
                  <!-- 播放/暂停 -->
                  <button class="ctrl-btn play-btn" @click="togglePlay">
                    <el-icon size="22"><component :is="isPlaying ? 'VideoPause' : 'VideoPlay'" /></el-icon>
                  </button>
                  <!-- 时间显示 -->
                  <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
                </div>
                <div class="controls-right">
                  <!-- 倍速选择 -->
                  <el-dropdown trigger="click" @command="setPlaybackRate">
                    <button class="ctrl-btn speed-btn">
                      {{ playbackRate }}x
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="rate in playbackRates" :key="rate" :command="rate" :active="playbackRate === rate">
                          {{ rate }}x
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <!-- 全屏 -->
                  <button class="ctrl-btn" @click="toggleFullscreen" title="全屏">
                    <el-icon size="18"><FullScreen /></el-icon>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- 上一节 / 下一节 导航 -->
          <div class="lesson-nav">
            <el-button
              v-if="prevLesson"
              class="nav-btn prev-btn"
              @click="goToLesson(prevLesson)"
            >
              <el-icon><ArrowLeft /></el-icon>
              上一节: {{ prevLesson.title }}
            </el-button>
            <div v-else />
            <el-button
              v-if="nextLesson"
              type="primary"
              class="nav-btn next-btn"
              @click="goToLesson(nextLesson)"
            >
              下一节: {{ nextLesson.title }}
              <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- Tab 内容区 -->
        <div class="tab-content-area">
          <!-- 课程 Tab -->
          <div v-show="activeTab === 'course'" class="tab-panel">
            <div class="course-content-card">
              <h3 class="content-title">
                <el-icon><Document /></el-icon>
                课程内容
              </h3>
              <div class="content-body">
                <p v-if="currentChapter?.description" class="chapter-desc">{{ currentChapter.description }}</p>
                <p v-else class="chapter-desc muted">本章节暂无课程内容描述</p>
                <!-- 关键概念高亮列表 -->
                <div v-if="currentChapter?.keyConcepts?.length" class="key-concepts">
                  <h4 class="concepts-title">关键概念</h4>
                  <ul class="concepts-list">
                    <li v-for="(concept, idx) in currentChapter.keyConcepts" :key="idx" class="concept-item">
                      {{ concept }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </div>

          <!-- 公告 Tab -->
          <div v-show="activeTab === 'announcement'" class="tab-panel">
            <div class="empty-state-card">
              <el-icon size="48" color="#CBD5E1"><Bell /></el-icon>
              <p class="empty-title">暂无公告</p>
              <p class="empty-desc">课程公告将在此处显示</p>
            </div>
          </div>

          <!-- 讨论 Tab -->
          <div v-show="activeTab === 'discussion'" class="tab-panel">
            <div class="empty-state-card">
              <el-icon size="48" color="#CBD5E1"><ChatDotRound /></el-icon>
              <p class="empty-title">暂无讨论</p>
              <p class="empty-desc">点击开始与同学和老师讨论</p>
              <el-button type="primary" plain size="small" @click="activeTab = 'course'">返回课程</el-button>
            </div>
          </div>

          <!-- 考试 Tab -->
          <div v-show="activeTab === 'exam'" class="tab-panel">
            <div v-if="currentExercises.length > 0" class="exam-entry-card">
              <div class="exam-header">
                <el-icon size="28" color="var(--indigo-600)"><Edit /></el-icon>
                <div class="exam-info">
                  <h3>章节练习</h3>
                  <p>共 {{ currentExercises.length }} 道练习题</p>
                </div>
              </div>
              <el-button type="primary" size="large" round @click="goExercise">
                <el-icon><CaretRight /></el-icon>
                开始练习
              </el-button>
            </div>
            <div v-else class="empty-state-card">
              <el-icon size="48" color="#CBD5E1"><Edit /></el-icon>
              <p class="empty-title">本章节暂无练习</p>
              <p class="empty-desc">完成视频学习后可获取练习资格</p>
            </div>
          </div>
        </div>
      </main>

      <!-- 右：课程大纲（40%） -->
      <aside class="content-sidebar" :class="{ 'drawer-open': drawerOpen && isMobile }">
        <!-- 移动端关闭按钮 -->
        <button v-if="isMobile" class="drawer-close-btn" @click="drawerOpen = false">
          <el-icon><Close /></el-icon>
        </button>

        <div class="sidebar-inner">
          <h3 class="sidebar-title">
            <el-icon><List /></el-icon>
            课程大纲
          </h3>

          <!-- 折叠章节列表 -->
          <el-collapse v-model="expandedChapters" class="chapter-collapse">
            <el-collapse-item
              v-for="ch in chapters"
              :key="ch.id"
              :name="ch.id"
              class="chapter-collapse-item"
            >
              <template #title>
                <div class="chapter-header">
                  <span class="chapter-name">{{ ch.title }}</span>
                  <div class="chapter-progress-bar">
                    <div class="chapter-progress-fill" :style="{ width: getChapterProgress(ch.id) + '%' }" />
                  </div>
                  <span class="chapter-progress-text">{{ getChapterProgress(ch.id) }}%</span>
                </div>
              </template>
              <div class="lesson-list">
                <div
                  v-for="lesson in ch.lessons"
                  :key="lesson.id"
                  class="lesson-item"
                  :class="{ active: currentLessonId === lesson.id, completed: lesson.status === 'COMPLETED' }"
                  @click="goToLesson(lesson)"
                >
                  <!-- 状态图标 -->
                  <span class="lesson-status-icon">
                    <el-icon v-if="lesson.status === 'COMPLETED'" color="#10B981"><CircleCheck /></el-icon>
                    <el-icon v-else-if="currentLessonId === lesson.id" color="#4F46E5"><VideoPlay /></el-icon>
                    <el-icon v-else color="#94A3B8"><VideoCamera /></el-icon>
                  </span>
                  <span class="lesson-title">{{ lesson.title }}</span>
                  <span v-if="lesson.duration" class="lesson-duration">{{ lesson.duration }}</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>

          <!-- 学习统计卡片 -->
          <div class="stats-card">
            <h4 class="stats-title">
              <el-icon><DataAnalysis /></el-icon>
              学习统计
            </h4>
            <div class="stats-grid">
              <div class="stat-item">
                <span class="stat-value">{{ statsData.videoCompleted }}/{{ statsData.videoTotal }}</span>
                <span class="stat-label">视频完成</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ statsData.exerciseCompleted }}/{{ statsData.exerciseTotal }}</span>
                <span class="stat-label">练习完成</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ statsData.totalTime }}</span>
                <span class="stat-label">累计学习</span>
              </div>
              <div class="stat-item">
                <span class="stat-value">{{ statsData.streakDays }}</span>
                <span class="stat-label">连续打卡</span>
              </div>
            </div>
            <div class="stats-actions">
              <el-button type="primary" size="small" @click="continueLearning">
                <el-icon><VideoPlay /></el-icon>
                继续学习
              </el-button>
              <el-button size="small" @click="activeTab = 'exam'">
                <el-icon><Edit /></el-icon>
                开始练习
              </el-button>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 移动端：底部大纲抽屉触发按钮 -->
    <div v-if="isMobile && !drawerOpen" class="mobile-drawer-trigger" @click="drawerOpen = true">
      <el-icon><List /></el-icon>
      <span>课程大纲</span>
      <span class="trigger-progress">{{ totalProgress }}%</span>
    </div>

    <!-- 移动端遮罩层 -->
    <div v-if="isMobile && drawerOpen" class="drawer-overlay" @click="drawerOpen = false" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Edit, Star, VideoCamera, VideoPlay, VideoPause,
  FullScreen, List, Document, Bell, ChatDotRound, CircleCheck, DataAnalysis,
  CaretRight, Close, Loading
} from '@element-plus/icons-vue'

import { getCourseById } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getVideos } from '@/api/video'
import { getExercises } from '@/api/exercise'
import { getLearningProgress, updateLearningProgress, createLearningProgress, getStudyDays, getTotalTime } from '@/api/learning-progress'
import { getMyFavorites, addFavorite, removeFavorite } from '@/api/favorite'

// ==================== 路由 & 状态 ====================
const route = useRoute()
const router = useRouter()
const courseId = computed(() => parseInt(route.query.courseId) || null)

// ==================== 响应式 ====================
const isMobile = ref(window.innerWidth < 768)
const handleResize = () => { isMobile.value = window.innerWidth < 768 }
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => window.removeEventListener('resize', handleResize))

// ==================== Tab 配置 ====================
const tabs = [
  { key: 'course', label: '课程', icon: 'Document' },
  { key: 'announcement', label: '公告', icon: 'Bell' },
  { key: 'discussion', label: '讨论', icon: 'ChatDotRound' },
  { key: 'exam', label: '考试', icon: 'Edit' }
]
const activeTab = ref('course')

// ==================== 课程数据 ====================
const course = ref({})
const chapters = ref([])
const progressMap = ref({}) // lessonId -> progress
const loading = ref(true)
const videoLoading = ref(true)
const drawerOpen = ref(false)
const expandedChapters = ref([])

// 总体进度
const totalProgress = computed(() => {
  if (!chapters.value.length) return 0
  const total = chapters.value.reduce((sum, ch) => sum + (ch.lessons?.length || 0), 0)
  if (total === 0) return 0
  const completed = chapters.value.reduce((sum, ch) => {
    return sum + (ch.lessons?.filter(l => l.status === 'COMPLETED').length || 0)
  }, 0)
  return Math.round((completed / total) * 100)
})

// ==================== 视频播放器状态 ====================
const videoRef = ref(null)
const isPlaying = ref(false)
const isBuffering = ref(false)
const showControls = ref(true)
const currentTime = ref(0)
const duration = ref(0)
const bufferedPercent = ref(0)
const hoverTime = ref(null)
const hoverX = ref(0)
const playbackRate = ref(1)
const playbackRates = [0.5, 0.75, 1, 1.25, 1.5, 2]
let controlsTimer = null
let progressSaveTimer = null

const playPercent = computed(() => {
  return duration.value > 0 ? (currentTime.value / duration.value) * 100 : 0
})

// ==================== 当前选中 ====================
const currentLessonId = ref(null)
const currentChapter = computed(() => chapters.value.find(ch => ch.lessons?.some(l => l.id === currentLessonId.value)) || null)

const currentVideo = computed(() => {
  if (!currentChapter.value || !currentLessonId.value) return null
  const lesson = currentChapter.value.lessons?.find(l => l.id === currentLessonId.value)
  return lesson?.video || null
})

const currentExercises = computed(() => {
  if (!currentChapter.value) return []
  return currentChapter.value.exercises || []
})

// 上一节 / 下一节
const allLessons = computed(() => {
  return chapters.value.flatMap(ch => ch.lessons || [])
})

const currentLessonIndex = computed(() => allLessons.value.findIndex(l => l.id === currentLessonId.value))
const prevLesson = computed(() => currentLessonIndex.value > 0 ? allLessons.value[currentLessonIndex.value - 1] : null)
const nextLesson = computed(() => currentLessonIndex.value < allLessons.value.length - 1 ? allLessons.value[currentLessonIndex.value + 1] : null)

// ==================== 统计 & 收藏 ====================
const statsData = ref({ videoCompleted: 0, videoTotal: 0, exerciseCompleted: 0, exerciseTotal: 0, totalTime: '0h', streakDays: 0 })
const isFavorited = ref(false)

// ==================== 工具函数 ====================
function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '00:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function formatTotalTime(data) {
  if (!data) return '0h'
  const seconds = data.totalSeconds || data || 0
  if (seconds < 60) return `${seconds}s`
  if (seconds < 3600) return `${Math.round(seconds / 60)}min`
  return `${(seconds / 3600).toFixed(1)}h`
}

function getChapterProgress(chapterId) {
  const ch = chapters.value.find(c => c.id === chapterId)
  if (!ch?.lessons?.length) return 0
  const completed = ch.lessons.filter(l => l.status === 'COMPLETED').length
  return Math.round((completed / ch.lessons.length) * 100)
}

function buildProgressMap(progressList) {
  const map = {}
  ;(progressList || []).forEach(p => { map[p.chapterId] = p })
  return map
}

// ==================== API 加载（修复 N+1） ====================
async function loadCourse(cid) {
  loading.value = true
  try {
    // ✅ 并行获取课程、进度、视频
    const [courseRes, progressRes, videosRes] = await Promise.all([
      getCourseById(cid),
      getLearningProgress({ courseId: cid }),
      getVideos({ courseId: cid, size: 200 })
    ])

    course.value = courseRes.data || {}

    // ✅ 构建 progressMap（key=chapterId）
    progressMap.value = buildProgressMap(progressRes.data || [])

    // 构建视频映射：chapterId → videos[]
    const videosList = videosRes.data?.items || []
    const videosByChapter = {}
    videosList.forEach(v => {
      const chId = v.chapterId
      if (!videosByChapter[chId]) videosByChapter[chId] = []
      videosByChapter[chId].push(v)
    })

    // 构建章节-课时树
    const rawChapters = courseRes.data.chapters || []
    chapters.value = rawChapters.map(ch => {
      const prog = progressMap.value[ch.id]
      const videos = videosByChapter[ch.id] || []
      // 将视频作为该章节的 lessons
      const lessons = videos.map(v => ({
        id: v.id,
        title: v.title,
        duration: v.duration,
        video: { ...v, url: v.url, coverUrl: v.coverUrl, playUrl: v.url },
        status: prog?.completed ? 'COMPLETED' : 'NOT_STARTED'
      }))
      return {
        ...ch,
        lessons: lessons.length > 0 ? lessons : (ch.lessons || []),
        exercises: ch.exercises || []
      }
    })

    // 设置默认展开第一章
    if (chapters.value.length > 0) {
      expandedChapters.value = [chapters.value[0].id]
      // 默认选中第一节
      if (chapters.value[0].lessons?.length > 0) {
        selectLesson(chapters.value[0].lessons[0].id)
      }
    }
  } catch (err) {
    console.error('loadCourse error:', err)
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
}

async function loadProgress() {
  if (!courseId.value) return
  try {
    // ✅ 移除重复的 getLearningProgress（已由 loadCourse 并行获取并构建 progressMap）
    const [studyDaysRes, timeRes] = await Promise.all([getStudyDays(), getTotalTime()])

    // progressMap 已在 loadCourse 中构建，此处直接使用
    // 更新章节中课时的完成状态（防御：chapters 可能尚未加载）
    if (chapters.value.length > 0) {
      chapters.value.forEach(ch => {
        const prog = progressMap.value[ch.id]
        if (ch.lessons) {
          ch.lessons.forEach(l => {
            l.status = prog?.completed ? 'COMPLETED' : 'NOT_STARTED'
          })
        }
      })
    }

    statsData.value = {
      videoCompleted: chapters.value.reduce((sum, ch) => sum + (ch.lessons?.filter(l => l.status === 'COMPLETED').length || 0), 0),
      videoTotal: chapters.value.reduce((sum, ch) => sum + (ch.lessons?.length || 0), 0),
      exerciseCompleted: 0,
      exerciseTotal: 0,
      totalTime: formatTotalTime(timeRes.data),
      streakDays: (studyDaysRes.data?.totalDays) || 0
    }
  } catch (err) {
    console.error('loadProgress error:', err)
  }
}

async function checkFavorite() {
  if (!courseId.value) return
  try {
    const res = await getMyFavorites()
    const favorites = res.data || []
    isFavorited.value = favorites.some(f => f.targetId === courseId.value && f.type === 'COURSE')
  } catch {
    // ignore
  }
}

async function toggleFavorite() {
  try {
    if (isFavorited.value) {
      // 取消收藏（需要找到收藏ID）
      const res = await getMyFavorites()
      const fav = (res.data || []).find(f => f.targetId === courseId.value && f.type === 'COURSE')
      if (fav) {
        await removeFavorite(fav.id)
        isFavorited.value = false
        ElMessage.success('已取消收藏')
      }
    } else {
      await addFavorite({ targetId: courseId.value, type: 'COURSE' })
      isFavorited.value = true
      ElMessage.success('已添加收藏')
    }
  } catch (err) {
    ElMessage.error('操作失败')
  }
}

// ==================== 课时选择 ====================
function selectLesson(lessonId) {
  currentLessonId.value = lessonId
  videoLoading.value = true
  isPlaying.value = false
  currentTime.value = 0
  duration.value = 0
  // 恢复播放进度（通过章节ID查找）
  const chapterId = currentChapter.value?.id
  const saved = progressMap.value[chapterId]
  if (saved?.videoPosition && videoRef.value) {
    videoRef.value.currentTime = saved.videoPosition
  }
  nextTick(() => { videoLoading.value = false })
}

function goToLesson(lesson) {
  if (!lesson) return
  selectLesson(lesson.id)
  // 如果当前章未展开，自动展开
  const ch = chapters.value.find(c => c.lessons?.some(l => l.id === lesson.id))
  if (ch && !expandedChapters.value.includes(ch.id)) {
    expandedChapters.value.push(ch.id)
  }
  // 移动端关闭抽屉
  if (isMobile.value) drawerOpen.value = false
}

// ==================== 视频控制 ====================
function togglePlay() {
  if (!videoRef.value) return
  if (isPlaying.value) {
    videoRef.value.pause()
  } else {
    videoRef.value.play()
  }
}

function onVideoLoaded() {
  duration.value = videoRef.value.duration
  videoLoading.value = false
  // 恢复播放位置（通过章节ID查找）
  const chapterId = currentChapter.value?.id
  const saved = progressMap.value[chapterId]
  if (saved?.videoPosition) {
    videoRef.value.currentTime = saved.videoPosition
  }
}

function onTimeUpdate() {
  if (!videoRef.value) return
  currentTime.value = videoRef.value.currentTime
  // 更新 buffer
  if (videoRef.value.buffered.length > 0) {
    bufferedPercent.value = (videoRef.value.buffered.end(videoRef.value.buffered.length - 1) / duration.value) * 100
  }
}

function onVideoEnded() {
  isPlaying.value = false
  // 标记完成
  markLessonComplete()
}

function seekVideo(e) {
  if (!videoRef.value || !duration.value) return
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  videoRef.value.currentTime = ratio * duration.value
}

function onProgressHover(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  hoverTime.value = ratio * duration.value
  hoverX.value = e.clientX - rect.left
}

function setPlaybackRate(rate) {
  playbackRate.value = rate
  if (videoRef.value) videoRef.value.playbackRate = rate
}

function toggleFullscreen() {
  const container = document.querySelector('.video-container')
  if (!container) return
  if (!document.fullscreenElement) {
    container.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

function onControlsMouseMove() {
  showControls.value = true
  clearTimeout(controlsTimer)
  controlsTimer = setTimeout(() => {
    if (isPlaying.value) showControls.value = false
  }, 3000)
}

// ==================== 进度保存（每 10 秒） ====================
async function saveVideoProgress() {
  if (!currentChapter.value || !videoRef.value) return
  try {
    const chapterId = currentChapter.value.id
    const lessonProgress = progressMap.value[chapterId]
    if (lessonProgress?.id) {
      await updateLearningProgress(lessonProgress.id, {
        videoPosition: Math.floor(videoRef.value.currentTime),
        completed: false
      })
    } else {
      const res = await createLearningProgress({
        courseId: courseId.value,
        chapterId: chapterId,
        videoPosition: Math.floor(videoRef.value.currentTime),
        videoProgress: 0
      })
      const newId = res.data?.id || (res.data || res).id
      if (newId) {
        progressMap.value[chapterId] = { id: newId }
      }
    }
  } catch {
    // 静默失败
  }
}

async function markLessonComplete() {
  if (!currentChapter.value) return
  try {
    const chapterId = currentChapter.value.id
    const lessonProgress = progressMap.value[chapterId]
    if (lessonProgress?.id) {
      await updateLearningProgress(lessonProgress.id, { completed: true })
    } else {
      const res = await createLearningProgress({
        courseId: courseId.value,
        chapterId: chapterId,
        completed: true
      })
      const newId = res.data?.id || (res.data || res).id
      if (newId) {
        progressMap.value[chapterId] = { id: newId }
      }
    }
    // 更新本地状态
    const lesson = allLessons.value.find(l => l.id === currentLessonId.value)
    if (lesson) lesson.status = 'COMPLETED'
  } catch {
    // 静默
  }
}

// ==================== 导航 & 交互 ====================
function goBack() { router.back() }

function goExercise() {
  if (!currentLessonId.value) return
  router.push(`/student/chapters/${currentLessonId.value}/exercises`)
}

function continueLearning() {
  // 找到下一个未完成的课时
  const next = allLessons.value.find(l => l.status !== 'COMPLETED')
  if (next) {
    goToLesson(next)
  } else {
    ElMessage.info('已完成所有课时')
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  if (!courseId.value) {
    ElMessage.warning('请先选择一门课程')
    router.replace('/student/courses')
    return
  }
  await loadCourse(courseId.value)
  await Promise.all([loadProgress(), checkFavorite()])

  // 启动进度保存定时器（每 10 秒）
  progressSaveTimer = setInterval(saveVideoProgress, 10000)
})

onUnmounted(() => {
  clearInterval(progressSaveTimer)
  clearTimeout(controlsTimer)
})
</script>

<style scoped>
/* ===== 设计 Token 引用 ===== */
.learning-view {
  --indigo-50: #EEF2FF;
  --indigo-100: #E0E7FF;
  --indigo-500: #6366F1;
  --indigo-600: #4F46E5;
  --indigo-700: #4338CA;
  --indigo-900: #312E81;
  --bg-page: #F5F6FA;
  --bg-card: #FFFFFF;
  --text-primary: #1E293B;
  --text-secondary: #64748B;
  --text-muted: #94A3B8;
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 16px;
  --shadow-card: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  --shadow-md: 0 4px 16px rgba(0,0,0,0.08);
  --shadow-indigo: 0 4px 20px rgba(79,70,229,0.15);
  --transition: 200ms ease;

  background: var(--bg-page);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ==================== 1. 顶部导航栏 ==================== */
.learning-header {
  display: flex;
  align-items: center;
  padding: 14px 24px;
  background: linear-gradient(135deg, var(--indigo-600) 0%, var(--indigo-500) 100%);
  color: white;
  gap: 16px;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: var(--shadow-indigo);
}

.header-left,
.header-right { flex: 1; display: flex; align-items: center; }
.header-right { gap: 8px; justify-content: flex-end; }
.header-center { flex: 2; text-align: center; }

.back-btn,
.header-btn {
  color: rgba(255,255,255,0.9) !important;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  transition: background var(--transition);
}
.back-btn:hover,
.header-btn:hover { background: rgba(255,255,255,0.15) !important; }
.back-btn .el-icon,
.header-btn .el-icon { font-size: 15px; }
.header-btn.is-favorited { color: #FCD34D !important; }

.course-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: white;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 进度指示器 */
.progress-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(255,255,255,0.15);
  transition: background var(--transition);
}
.progress-indicator:hover { background: rgba(255,255,255,0.25); }
.progress-dots { display: flex; gap: 3px; }
.dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: rgba(255,255,255,0.35);
  transition: background var(--transition);
}
.dot.filled { background: white; }
.progress-text { font-size: 13px; font-weight: 600; }

/* ==================== 2. 顶部 Tab ==================== */
.learning-tabs {
  background: white;
  border-bottom: 1px solid #E4E7ED;
  position: sticky;
  top: 56px;
  z-index: 99;
}

.tab-bar {
  display: flex;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  overflow-x: auto;
  scrollbar-width: none;
}
.tab-bar::-webkit-scrollbar { display: none; }

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 14px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  background: none;
  border: none;
  border-bottom: 2.5px solid transparent;
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--transition);
  margin-bottom: -1px;
}
.tab-item .el-icon { font-size: 16px; }
.tab-item:hover { color: var(--indigo-600); }
.tab-item.active {
  color: var(--indigo-600);
  border-bottom-color: var(--indigo-600);
  font-weight: 600;
}

/* ==================== 3. 主体布局 ==================== */
.learning-body {
  flex: 1;
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  padding: 24px;
  gap: 24px;
  align-items: flex-start;
}

/* ==================== 4. 左主内容区 ==================== */
.content-main {
  flex: 1.5;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 视频区 */
.video-section { display: flex; flex-direction: column; gap: 16px; }

.video-container {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #0F172A;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-md);
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0F172A;
  display: block;
}

.video-skeleton {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #1E293B;
  position: relative;
  overflow: hidden;
}
.skeleton-shimmer {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.05) 50%, transparent 100%);
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  from { transform: translateX(-100%); }
  to { transform: translateX(100%); }
}
.loading-icon { color: rgba(255,255,255,0.3); animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.video-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: linear-gradient(145deg, #1E293B, #0F172A);
  color: #64748B;
}

/* ===== 自定义控制栏 ===== */
.video-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px 16px 10px;
  background: linear-gradient(transparent, rgba(0,0,0,0.8));
  opacity: 0;
  transition: opacity var(--transition);
}
.video-controls.visible,
.video-container:hover .video-controls { opacity: 1; }

.progress-wrap {
  position: relative;
  padding: 8px 0;
  cursor: pointer;
}
.progress-track {
  position: relative;
  height: 4px;
  background: rgba(255,255,255,0.2);
  border-radius: 2px;
  overflow: visible;
}
.progress-buffered {
  position: absolute;
  height: 100%;
  background: rgba(255,255,255,0.35);
  border-radius: 2px;
  transition: width 0.3s;
}
.progress-filled {
  position: absolute;
  height: 100%;
  background: var(--indigo-500);
  border-radius: 2px;
  transition: width 0.1s;
}
.progress-thumb {
  position: absolute;
  top: 50%;
  width: 12px;
  height: 12px;
  background: white;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 4px rgba(0,0,0,0.4);
  transition: left 0.1s;
}
.hover-time {
  position: absolute;
  bottom: 24px;
  transform: translateX(-50%);
  background: rgba(0,0,0,0.8);
  color: white;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
  white-space: nowrap;
}

.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.controls-left,
.controls-right { display: flex; align-items: center; gap: 10px; }

.ctrl-btn {
  background: none;
  border: none;
  color: white;
  cursor: pointer;
  padding: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: opacity var(--transition);
}
.ctrl-btn:hover { opacity: 0.8; }
.play-btn { padding: 4px 8px; }
.speed-btn {
  font-size: 13px;
  font-weight: 600;
  padding: 4px 8px;
  background: rgba(255,255,255,0.15);
  border-radius: var(--radius-sm);
}
.time-display {
  font-size: 13px;
  color: rgba(255,255,255,0.9);
  font-variant-numeric: tabular-nums;
}

/* 课时导航 */
.lesson-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.nav-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Tab 内容区 */
.tab-content-area { flex: 1; }

.tab-panel { animation: fadeIn 0.2s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

.course-content-card {
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.content-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  padding: 16px 20px;
  border-bottom: 1px solid #F1F5F9;
  margin: 0;
}
.content-body { padding: 20px; }
.chapter-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
  margin: 0 0 16px;
}
.chapter-desc.muted { color: var(--text-muted); font-style: italic; }

.key-concepts { margin-top: 16px; }
.concepts-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 10px;
}
.concepts-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.concept-item {
  font-size: 14px;
  color: var(--text-secondary);
  padding: 8px 12px;
  background: var(--indigo-50);
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--indigo-600);
}

.empty-state-card {
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  padding: 64px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}
.empty-title { font-size: 16px; font-weight: 600; color: var(--text-primary); margin: 0; }
.empty-desc { font-size: 14px; color: var(--text-muted); margin: 0; }

.exam-entry-card {
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.exam-header { display: flex; align-items: center; gap: 14px; }
.exam-info h3 { font-size: 16px; font-weight: 600; margin: 0 0 4px; }
.exam-info p { font-size: 13px; color: var(--text-secondary); margin: 0; }

/* ==================== 5. 右侧课程大纲 ==================== */
.content-sidebar {
  width: 380px;
  flex-shrink: 0;
  position: sticky;
  top: 140px;
  max-height: calc(100vh - 160px);
  overflow-y: auto;
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
}

.sidebar-inner {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
}

.sidebar-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F1F5F9;
}

/* Collapse */
.chapter-collapse { border: none !important; }
.chapter-collapse :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  background: #F8FAFC;
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  margin-bottom: 4px;
  border: none;
}
.chapter-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  background: transparent;
}
.chapter-collapse :deep(.el-collapse-item__content) { padding: 0 0 8px; }
.chapter-collapse :deep(.el-icon) { font-size: 14px; }

.chapter-header {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.chapter-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-progress-bar {
  width: 60px;
  height: 4px;
  background: #E2E8F0;
  border-radius: 2px;
  overflow: hidden;
}
.chapter-progress-fill {
  height: 100%;
  background: var(--indigo-500);
  border-radius: 2px;
  transition: width 0.3s;
}
.chapter-progress-text { font-size: 12px; color: var(--text-muted); min-width: 30px; text-align: right; }

.lesson-list { padding: 4px 0 4px 8px; }
.lesson-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition);
  border-left: 3px solid transparent;
}
.lesson-item:hover { background: #F1F5F9; }
.lesson-item.active {
  background: var(--indigo-50);
  border-left-color: var(--indigo-600);
}
.lesson-item.completed .lesson-title { color: var(--text-muted); }

.lesson-status-icon { display: flex; align-items: center; font-size: 14px; }
.lesson-title { flex: 1; font-size: 13px; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lesson-item.active .lesson-title { color: var(--indigo-600); font-weight: 500; }
.lesson-duration { font-size: 12px; color: var(--text-muted); white-space: nowrap; }

/* 统计卡片 */
.stats-card {
  background: linear-gradient(135deg, var(--indigo-50), #E0E7FF);
  border-radius: var(--radius-md);
  padding: 16px;
  border: 1px solid var(--indigo-100);
}
.stats-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--indigo-700);
  margin: 0 0 12px;
}
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}
.stat-item { display: flex; flex-direction: column; gap: 2px; }
.stat-value { font-size: 18px; font-weight: 700; color: var(--indigo-700); }
.stat-label { font-size: 12px; color: var(--indigo-500); }
.stats-actions { display: flex; gap: 8px; }
.stats-actions .el-button { flex: 1; }

/* ==================== 6. 移动端响应式 ==================== */
@media (max-width: 768px) {
  .learning-header {
    padding: 10px 16px;
    gap: 8px;
  }
  .course-title { font-size: 15px; }
  .progress-text { display: none; }
  .header-btn span { display: none; }
  .header-btn { padding: 6px; }

  .tab-bar { padding: 0 16px; }
  .tab-item { padding: 12px 14px; font-size: 13px; }

  .learning-body {
    padding: 16px;
    flex-direction: column;
    gap: 0;
  }

  .content-main { width: 100%; }
  .video-container { border-radius: var(--radius-md); }

  .content-sidebar {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    width: 100%;
    max-height: 70vh;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    z-index: 200;
    transform: translateY(100%);
    transition: transform 0.3s ease;
    box-shadow: 0 -4px 24px rgba(0,0,0,0.12);
  }
  .content-sidebar.drawer-open { transform: translateY(0); }

  .drawer-close-btn {
    position: absolute;
    top: 12px;
    right: 12px;
    background: none;
    border: none;
    font-size: 20px;
    color: var(--text-muted);
    cursor: pointer;
    z-index: 1;
    padding: 4px;
  }

  .drawer-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.4);
    z-index: 199;
  }

  .mobile-drawer-trigger {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 12px;
    background: white;
    border-top: 1px solid #E4E7ED;
    font-size: 14px;
    font-weight: 500;
    color: var(--text-primary);
    cursor: pointer;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 99;
    box-shadow: 0 -2px 8px rgba(0,0,0,0.06);
  }
  .trigger-progress {
    margin-left: auto;
    font-size: 13px;
    color: var(--indigo-600);
    font-weight: 700;
  }

  .lesson-nav .nav-btn { font-size: 12px; }
  .exam-entry-card { flex-direction: column; align-items: flex-start; }
}
</style>