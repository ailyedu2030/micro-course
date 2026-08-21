<!--
  统一学习页面 - 网易慕课风格
  路由路径: /student/learning
  Phase 5 · NetEase MOOC Style
  Round 11-3 重构：拆分为 ResourceToolbar / VideoSection / NotesPanel / ExerciseQuickPanel / ChapterSidebar
  Author: jackie
-->
<template>
  <div class="learning-view" :class="{ 'is-mobile': isMobile }">
    <!-- A11Y: 页面标题（视觉隐藏），避免子组件标题层级跳级 -->
    <h1 class="sr-only">{{ $t('student.dashboard') }}</h1>

    <!-- ===================== 1. 顶部导航栏 ===================== -->
    <ResourceToolbar
      :course-title="course.title"
      :loading="loading"
      :total-progress="totalProgress"
      :is-favorited="isFavorited"
      @back="goBack"
      @toggle-favorite="toggleFavorite"
      @show-notes="activeTab = 'note'"
    />

    <!-- ===================== 1.5 课程状态提示 ===================== -->
    <div v-if="courseStatusWarning" class="status-warning-bar">
      <el-alert :title="courseStatusWarning" type="warning" :closable="false" show-icon />
    </div>

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
    <div class="learning-body" v-loading="loading && !loadError" :element-loading-text="$t('learningView.loadingCourse')" element-loading-background="var(--el-bg-color-page)">
      <!-- P1: loadCourse 失败时显示错误覆层 + 重试入口 -->
      <div v-if="loadError" class="load-error-overlay">
        <el-result icon="error" :title="$t('learningView.loadFailed')" :sub-title="$t('learningView.loadErrorSubtitle')">
          <template #extra>
            <el-button type="primary" @click="retryLoad">{{ $t('learning.reload') }}</el-button>
            <el-button @click="goBack">{{ $t('learningView.backToCourse') }}</el-button>
          </template>
        </el-result>
      </div>

      <!-- 左：主内容区（60%） -->
      <div class="content-main" role="region" :aria-label="$t('learningView.learningContentAria')">
        <!-- 视频播放器 — 仅 VIDEO 章节显示 -->
        <VideoSection
          v-if="currentChapter?.sectionType === 'VIDEO'"
          :current-video="currentVideo"
          :initial-position="initialPosition"
          :prev-lesson="prevLesson"
          :next-lesson="nextLesson"
          @time-update="onChildTimeUpdate"
          @playing-change="onChildPlayingChange"
          @ended="markLessonComplete"
          @go-to-lesson="goToLesson"
        />

        <!-- 非 VIDEO 章节:显示类型对应的操作按钮 -->
        <div v-else-if="currentChapter?.sectionType === 'INTERACTIVE'" class="chapter-content-placeholder">
          <el-empty :description="currentChapter?.coursewareType === 'PPT' ? $t('learningView.chapterPptCourseware') : $t('learningView.chapterHtmlCourseware')">
            <el-button type="primary" @click="goChapterContent(currentChapter, 'INTERACTIVE')">{{ $t('learningView.enterCourseware') }}</el-button>
          </el-empty>
        </div>
        <div v-else-if="currentChapter?.sectionType === 'OFFLINE'" class="chapter-content-placeholder">
          <el-empty :description="$t('learningView.chapterOfflineCourse')">
            <el-button type="primary" @click="goChapterContent(currentChapter, 'OFFLINE')">{{ $t('learningView.viewSessions') }}</el-button>
          </el-empty>
        </div>
        <div v-else-if="currentChapter?.sectionType === 'EXERCISE'" class="chapter-content-placeholder">
          <el-empty :description="$t('learningView.chapterExercise')">
            <el-button type="primary" @click="goChapterContent(currentChapter, 'EXERCISE')">{{ $t('learningView.startExercise') }}</el-button>
          </el-empty>
        </div>

        <!-- Tab 内容区 -->
        <div class="tab-content-area">
          <!-- 课程 / 公告 / 讨论 Tab -->
          <NotesPanel
            :active-tab="activeTab"
            :current-chapter="currentChapter"
            :course-id="courseId"
            @change-tab="activeTab = $event"
          />

          <!-- 考试 Tab -->
          <div v-show="activeTab === 'exam'" class="tab-panel">
            <ExerciseQuickPanel
              :exercise-count="totalExerciseCount"
              @start-exercise="goExercise"
            />
          </div>
        </div>
      </div>

      <!-- 右：课程大纲（40%） -->
      <ChapterSidebar
        :chapters="chapters"
        :current-lesson-id="currentLessonId"
        v-model:expanded-chapters="expandedChapters"
        :stats-data="statsData"
        :is-mobile="isMobile"
        v-model:drawer-open="drawerOpen"
        @select-lesson="goToLesson"
        @continue-learning="continueLearning"
        @start-exercise="activeTab = 'exam'"
      />
    </div>

    <!-- 移动端：底部大纲抽屉触发按钮 -->
    <div v-if="isMobile && !drawerOpen" class="mobile-drawer-trigger" role="button" tabindex="0" :aria-label="$t('learningView.openOutlineAria')" @click="drawerOpen = true" @keydown.enter="drawerOpen = true">
      <el-icon><List /></el-icon>
      <span>{{ $t('course.outline') }}</span>
      <span class="trigger-progress">{{ totalProgress }}%</span>
    </div>

    <!-- 移动端遮罩层 -->
    <div v-if="isMobile && drawerOpen" class="drawer-overlay" aria-hidden="true" @click="drawerOpen = false" />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Document, Bell, ChatDotRound, Edit, List } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

import ResourceToolbar from '@/components/learning-view/ResourceToolbar.vue'
import VideoSection from '@/components/learning-view/VideoSection.vue'
import NotesPanel from '@/components/learning-view/NotesPanel.vue'
import ExerciseQuickPanel from '@/components/learning-view/ExerciseQuickPanel.vue'
import ChapterSidebar from '@/components/learning-view/ChapterSidebar.vue'

import { getCourseById } from '@/api/course'
import { getVideos } from '@/api/video'
import { getLearningProgress, updateLearningProgress, createLearningProgress, getStudyDays, getTotalTime } from '@/api/learning-progress'
import { getMyFavorites, addFavorite, removeFavorite } from '@/api/favorite'
import { getExercises } from '@/api/exercise'
import { useLearningProgressReporter } from '@/composables/useLearningProgressReporter'
import { useLearningProgressHeartbeat } from '@/composables/useLearningProgressHeartbeat'

// ==================== 路由 & 状态 ====================
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const courseId = computed(() => parseInt(route.query.courseId) || null)
const chapterIdFromQuery = computed(() => parseInt(route.query.chapterId) || null)

// ==================== 响应式 ====================
const isMobile = ref(window.innerWidth < 768)
let resizeRafId = null
const handleResize = () => {
  if (resizeRafId) cancelAnimationFrame(resizeRafId)
  resizeRafId = requestAnimationFrame(() => {
    isMobile.value = window.innerWidth < 768
    resizeRafId = null
  })
}
onMounted(() => window.addEventListener('resize', handleResize))
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (resizeRafId) cancelAnimationFrame(resizeRafId)
})

// ==================== Tab 配置 ====================
const tabs = [
  { key: 'course', label: t('learningView.tabCourse'), icon: 'Document' },
  { key: 'announcement', label: t('learningView.tabAnnouncement'), icon: 'Bell' },
  { key: 'discussion', label: t('learningView.tabDiscussion'), icon: 'ChatDotRound' },
  { key: 'exam', label: t('learningView.tabExam'), icon: 'Edit' }
]
const activeTab = ref('course')

// ==================== 课程数据 ====================
const course = ref({})
const chapters = ref([])
const progressMap = ref({}) // sectionId -> progress
const progressRawList = ref([]) // 原始进度列表，用于 chapterId 维度查询（非 VIDEO 章节）
const loading = ref(true)
const loadError = ref(false)
const drawerOpen = ref(false)
const expandedChapters = ref([])

// 课程状态警告
const courseStatusWarning = computed(() => {
  if (course.value?.status === 5) return t('learningView.courseUnpublishedWarning')
  if (course.value?.status === 6) return t('learningView.courseArchivedWarning')
  return ''
})

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

// ==================== 视频进度镜像（由 VideoSection 同步上来，用于进度保存） ====================
const currentTime = ref(0)
const isPlaying = ref(false)

const {
  persistProgress: persistLessonProgress,
  resetProgressReporter: resetLessonProgressReporter
} = useLearningProgressReporter({
  getDedupKey: () => currentLessonId.value ? `progress_dedup_lesson_${currentLessonId.value}` : '',
  shouldPersist: ({ force }) => {
    if (!force && !isPlaying.value) return false
    if (!currentLessonId.value) return false
    return /^\d+$/.test(String(currentLessonId.value))
  },
  getProgressRecord: () => progressMap.value[currentLessonId.value],
  setProgressRecord: (record) => {
    if (!currentLessonId.value || !record?.id) return
    progressMap.value[currentLessonId.value] = {
      ...progressMap.value[currentLessonId.value],
      ...record,
      sectionId: currentLessonId.value
    }
  },
  createPayload: ({ watchDelta, completed }) => ({
    courseId: courseId.value,
    chapterId: currentChapter.value?.id,
    sectionId: currentLessonId.value,
    ...(completed
      ? { completed: true }
      : {
          videoPosition: Math.floor(currentTime.value),
          watchDelta
        })
  }),
  updatePayload: ({ watchDelta, completed }) => (
    completed
      ? { completed: true }
      : {
          videoPosition: Math.floor(currentTime.value),
          watchDelta
        }
  ),
  createProgress: createLearningProgress,
  updateProgress: updateLearningProgress,
  findExistingProgress: async () => {
    const existing = await getLearningProgress({
      courseId: courseId.value,
      sectionId: currentLessonId.value
    })
    return (existing.data || []).find(p => p.sectionId === currentLessonId.value)
  },
  onError: ({ error, failureCount }) => {
    console.warn('[LearningView] saveVideoProgress 保存进度失败', error)
    if (failureCount % 3 === 0) {
      ElMessage.warning(t('learningView.progressSaveWarning'))
    }
  }
})

function onChildTimeUpdate(t) { currentTime.value = t }
function onChildPlayingChange(p) { isPlaying.value = p }

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

// P1-C-2026-08-21: 课程级练习（无章节归属）——教师建练习未选章节时学生仍可见可作答
const courseLevelExercises = ref([])
const totalExerciseCount = computed(() => {
  return (currentExercises.value?.length || 0) + (courseLevelExercises.value?.length || 0)
})

// 视频恢复位置（传给 VideoSection）
const initialPosition = computed(() => progressMap.value[currentLessonId.value]?.videoPosition || 0)

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
function formatTotalTime(data) {
  if (!data) return '0h'
  const raw = typeof data === 'number' ? data : data.totalSeconds
  const seconds = (typeof raw === 'number' && !isNaN(raw)) ? raw : 0
  if (seconds <= 0) return '0h'
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.round(seconds / 60)}min`
  return `${(seconds / 3600).toFixed(1)}h`
}

function buildProgressMap(progressList) {
  const map = {}
  ;(progressList || []).forEach(p => { map[p.sectionId] = p })
  return map
}

// ==================== API 加载（修复 N+1） ====================
async function retryLoad() {
  if (!courseId.value) return
  loadError.value = false
  loading.value = true
  await loadCourse(courseId.value)
}

async function loadCourse(cid) {
  loading.value = true
  try {
    // ✅ 并行获取课程、进度、视频、课程级练习
    const [courseRes, progressRes, videosRes, courseExRes] = await Promise.all([
      getCourseById(cid),
      getLearningProgress({ courseId: cid }),
      getVideos({ courseId: cid, size: 100 }),
      getExercises({ courseId: cid, size: 100 }).catch(() => ({ data: { items: [] } }))
    ])
    // 课程级练习 = 无章节归属（chapterIds 为空）
    const courseExList = courseExRes?.data?.items || (Array.isArray(courseExRes?.data) ? courseExRes.data : [])
    courseLevelExercises.value = courseExList.filter(ex => !ex.chapterIds || ex.chapterIds.length === 0)

    course.value = courseRes.data || {}

    // A11Y-023: 动态设置页面标题
    if (course.value.title) {
      document.title = t('learningView.documentTitle', { title: course.value.title })
    }

    // ✅ 构建 progressMap（key=sectionId）+ 保存原始列表供 chapterId 查询
    const rawProgress = progressRes.data || []
    progressMap.value = buildProgressMap(rawProgress)
    progressRawList.value = rawProgress

    // 构建视频映射：chapterId → videos[]
    const videosList = videosRes.data?.items || []
    const videosByChapter = {}
    videosList.forEach(v => {
      const chId = v.chapterId
      if (!videosByChapter[chId]) videosByChapter[chId] = []
      videosByChapter[chId].push(v)
    })

    // 构建章节-课时树（根据 sectionType 构建不同的 lessons）
    const rawChapters = courseRes.data.chapters || []
    chapters.value = rawChapters.map(ch => {
      let lessons = []
      if (ch.sectionType === 'VIDEO') {
        const videos = videosByChapter[ch.id] || []
        if (videos.length === 0) {
          lessons = [{
            id: `empty-${ch.id}`,
            title: t('learningView.noVideosInChapter'),
            type: 'VIDEO',
            chapterId: ch.id,
            status: 'NOT_STARTED',
            duration: 0
          }]
        } else {
          lessons = videos.map(v => {
            const prog = progressMap.value[v.id]
            return {
              id: v.id,
              title: v.title,
              duration: v.duration,
              type: 'VIDEO',
              video: { ...v, url: v.url, coverUrl: v.coverUrl, playUrl: v.url },
              status: prog?.completed ? 'COMPLETED' : 'NOT_STARTED',
              chapterId: ch.id
            }
          })
        }
      } else if (ch.sectionType === 'INTERACTIVE') {
        lessons = [{
          id: `slide-${ch.id}`,
          title: ch.coursewareType === 'PPT' ? t('course.typePptCourseware') : t('course.typeHtmlCourseware'),
          type: 'INTERACTIVE',
          chapterId: ch.id,
          status: 'NOT_STARTED',
          duration: ch.duration || 0
        }]
      } else if (ch.sectionType === 'OFFLINE') {
        lessons = [{
          id: `offline-${ch.id}`,
          title: t('learningView.offlineCheckIn'),
          type: 'OFFLINE',
          chapterId: ch.id,
          status: 'NOT_STARTED',
          duration: 0
        }]
      } else if (ch.sectionType === 'EXERCISE') {
        // 练习:生成一个可点击的"开始练习"条目
        lessons = [{
          id: `exercise-${ch.id}`,
          title: t('learningView.lessonExercise'),
          type: 'EXERCISE',
          chapterId: ch.id,
          status: 'NOT_STARTED',
          duration: 0
        }]
      }
      return {
        ...ch,
        lessons,
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
// eslint-disable-next-line no-console
    console.debug('loadCourse error:', err)
    loadError.value = true
    ElMessage.error(t('learningView.loadCourseFailed'))
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
        if (ch.lessons) {
          ch.lessons.forEach(l => {
            if (ch.sectionType === 'VIDEO') {
              // VIDEO 章节: 按 sectionId 查找进度
              const prog = progressMap.value[l.id]
              l.status = prog?.completed ? 'COMPLETED' : 'NOT_STARTED'
            } else {
              // 非 VIDEO 章节: 按 chapterId 查找进度（子页面通过 chapterId 创建进度记录）
              const chapterProgress = progressRawList.value.find(p => p.chapterId === ch.id)
              l.status = chapterProgress?.completed ? 'COMPLETED' : 'NOT_STARTED'
            }
          })
        }
      })
    }

    // 练习统计：单课程进度接口已按课时级聚合 completedExercises/totalExercises，
    // 任一进度记录都携带课程级汇总值，取首条即可（无记录时回退 0）
    const progressAgg = progressRawList.value[0] || {}
    statsData.value = {
      videoCompleted: chapters.value.reduce((sum, ch) => sum + (ch.lessons?.filter(l => l.status === 'COMPLETED').length || 0), 0),
      videoTotal: chapters.value.reduce((sum, ch) => sum + (ch.lessons?.length || 0), 0),
      exerciseCompleted: progressAgg.completedExercises ?? 0,
      exerciseTotal: progressAgg.totalExercises ?? 0,
      totalTime: formatTotalTime(timeRes.data),
      streakDays: (studyDaysRes.data?.totalDays) || 0
    }
  } catch (err) {
// eslint-disable-next-line no-console
    console.debug('loadProgress error:', err)
    ElMessage.warning(t('learningView.progressLoadWarning'))
  }
}

async function checkFavorite() {
  if (!courseId.value) return
  try {
    const res = await getMyFavorites()
    const favorites = res.data || []
    isFavorited.value = favorites.some(f => String(f.courseId) === String(courseId.value))
  } catch (e) {
    console.warn('[LearningView] checkFavorite 查询收藏状态失败', e)
  }
}

async function toggleFavorite() {
  try {
    if (isFavorited.value) {
      // 取消收藏（需要找到收藏ID）
      const res = await getMyFavorites()
      const fav = (res.data || []).find(f => f.courseId === courseId.value)
      if (fav) {
        await removeFavorite(fav.id)
        isFavorited.value = false
        ElMessage.success(t('learningView.favoriteRemoved'))
      }
    } else {
      const res = await addFavorite({ courseId: courseId.value })
      isFavorited.value = true
      if (res.data?.alreadyFavorited) {
        ElMessage.warning(t('learningView.alreadyFavorited'))
      } else {
        ElMessage.success(t('learningView.favoriteAdded'))
      }
    }
  } catch (err) {
    ElMessage.error(t('course.operationFailed'))
  }
}

// ==================== 课时选择 ====================
function selectLesson(sectionId) {
  currentLessonId.value = sectionId
  // 视频状态（loading/error/buffer 等）重置由 VideoSection 监听 currentVideo 变化处理
  // 此处仅同步进度镜像，避免切换瞬间 saveVideoProgress 误判
  isPlaying.value = false
  currentTime.value = 0
  resetLessonProgressReporter()
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

// ==================== 进度保存（每 10 秒） ====================
async function saveVideoProgress(force = false) {
  await persistLessonProgress({ force })
}

const {
  startHeartbeat: startLessonProgressHeartbeat,
  stopHeartbeat: stopLessonProgressHeartbeat,
  restartHeartbeat: restartLessonProgressHeartbeat
} = useLearningProgressHeartbeat({
  onInterval: saveVideoProgress,
  onBeforeUnmountPersist: () => saveVideoProgress(true)
})

async function markLessonComplete() {
  if (!currentLessonId.value) return
  // P0-7: 串行化 - 先暂停定时器，防止与 saveVideoProgress 竞态
  stopLessonProgressHeartbeat()
  let marked = false
  try {
    marked = await persistLessonProgress({ force: true, completed: true })
  } catch (e) {
    console.warn('[LearningView] markLessonComplete 标记完成失败', e)
    ElMessage.warning(t('learningView.completeMarkFailed'))
  } finally {
    // P2-3: 仅在 API 成功后更新本地状态
    if (marked) {
      const lesson = allLessons.value.find(l => l.id === currentLessonId.value)
      if (lesson) lesson.status = 'COMPLETED'
    }
    // P0-7: 恢复定时器
    restartLessonProgressHeartbeat()
  }
}

// ==================== 导航 & 交互 ====================
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/student/courses')
  }
}

function goExercise() {
  // P1-C-2026-08-21 修复：不依赖 currentLessonId（未选课时点击"开始练习"无响应）。
  // 优先级：当前章节练习 → 课程级练习 → 第一个有练习的章节 → 当前章节（空态跳转）
  const chId = currentChapter.value?.id
  const chEx = currentExercises.value || []
  if (chId && chEx.length > 0) {
    router.push(`/student/chapters/${chId}/exercises`)
    return
  }
  if (courseLevelExercises.value?.length > 0) {
    router.push(`/student/courses/${courseId.value}/exercises`)
    return
  }
  const firstCh = chapters.value.find(ch => (ch.exercises || []).length > 0)
  if (firstCh) {
    router.push(`/student/chapters/${firstCh.id}/exercises`)
    return
  }
  if (chId) {
    router.push(`/student/chapters/${chId}/exercises`)
  }
}

function goChapterContent(chapter, type) {
  const courseIdVal = courseId.value
  if (type === 'INTERACTIVE') {
    router.push(`/student/courses/${courseIdVal}/slides/player?chapterId=${chapter.id}`)
  } else if (type === 'OFFLINE') {
    router.push(`/student/chapters/${chapter.id}/offline`)
  } else if (type === 'EXERCISE') {
    router.push(`/student/chapters/${chapter.id}/exercises`)
  }
}

function continueLearning() {
  // 找到下一个未完成的课时
  const next = allLessons.value.find(l => l.status !== 'COMPLETED')
  if (next) {
    goToLesson(next)
  } else {
    ElMessage.info(t('learningView.allLessonsCompleted'))
  }
}

// ==================== 生命周期 ====================
onMounted(async () => {
  if (!courseId.value) {
    // 无 courseId 时跳转角色首页（非 STUDENT 角色避免重定向到 /student/courses 被 guard 拦截）
    const token = localStorage.getItem('micro_course_token')
    if (token && useUserStore().role && useUserStore().role !== 'STUDENT') {
      router.replace('/teacher/dashboard')
    } else {
      router.replace('/student/courses')
    }
    return
  }
  await loadCourse(courseId.value)

  // 如果 URL 中有 chapterId 参数，自动定位到该章节
  if (chapterIdFromQuery.value) {
    const targetChapter = chapters.value.find(ch => ch.id === chapterIdFromQuery.value)
    if (targetChapter && targetChapter.lessons?.length > 0) {
      selectLesson(targetChapter.lessons[0].id)
    }
  }

  await Promise.all([loadProgress(), checkFavorite()])

  // 启动进度保存定时器（每 10 秒，P1-10: saveVideoProgress 内部判断 isPlaying）
  resetLessonProgressReporter()
  startLessonProgressHeartbeat()
})

// P1-6: 监听 courseId 变化（URL query 切换课程）
const stopCourseWatch = watch(() => route.query.courseId, (newId) => {
  if (newId) loadCourse(parseInt(newId))
})

onBeforeUnmount(() => {
  stopCourseWatch()
})
</script>

<style scoped>
/* ===== 设计 Token 引用 ===== */
.learning-view {
  --color-primary: #6366F1;
  --color-primary-dark: #4F46E5;
  --color-primary-darker: #4338CA;
  --color-primary-light: #EEF2FF;
  --color-primary-100: #E0E7FF;

  background: var(--el-bg-color-page);
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
}

/* ==================== 2. 顶部 Tab ==================== */
.learning-tabs {
  background: var(--el-fill-color-blank);
  border-bottom: 1px solid var(--el-border-color);
  position: sticky;
  top: 56px;
  z-index: 99;
}

.tab-bar {
  display: flex;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  overflow-x: auto;
  scrollbar-width: none;
}
.tab-bar::-webkit-scrollbar { display: none; }

.tab-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1-5);
  padding: var(--space-4) var(--space-5);
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-secondary);
  background: none;
  border: none;
  border-bottom: 2.5px solid transparent;
  cursor: pointer;
  white-space: nowrap;
  transition: all var(--duration-base) var(--ease-out);
  margin-bottom: -1px;
}
.tab-item .el-icon { font-size: 16px; }
.tab-item:hover { color: var(--color-primary-dark); }
.tab-item.active {
  color: var(--color-primary-dark);
  border-bottom-color: var(--color-primary-dark);
  font-weight: var(--weight-semibold);
}

/* ==================== 3. 主体布局 ==================== */
.learning-body {
  flex: 1;
  display: flex;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  padding: var(--space-6);
  gap: var(--space-6);
  align-items: flex-start;
}

/* ==================== 4. 左主内容区 ==================== */
.content-main {
  flex: 1.5;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

/* Tab 内容区 */
.tab-content-area { flex: 1; }

.tab-panel { animation: fadeIn 0.2s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

/* ==================== 5. 移动端响应式 ==================== */
/* 课程状态提示条 */
.status-warning-bar {
  max-width: 1400px;
  margin: var(--space-2) auto 0;
  padding: 0 var(--space-6);
  width: 100%;
}

@media (max-width: 768px) {
  .tab-bar { padding: 0 var(--space-4); }
  .tab-item { padding: var(--space-3) var(--space-3-5); font-size: var(--text-sm); }

  .learning-body {
    padding: var(--space-4);
    flex-direction: column;
    gap: 0;
  }

  .content-main { width: 100%; }

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
    gap: var(--space-2);
    padding: var(--space-3);
    background: var(--el-fill-color-blank);
    border-top: 1px solid var(--el-border-color);
    font-size: var(--text-base);
    font-weight: var(--weight-medium);
    color: var(--el-text-color-primary);
    cursor: pointer;
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    z-index: 99;
    box-shadow: var(--shadow-sm);
  }
  .trigger-progress {
    margin-left: auto;
    font-size: var(--text-sm);
    color: var(--color-primary-dark);
    font-weight: var(--weight-bold);
  }
}
.load-error-overlay {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: var(--space-8);
}
</style>
