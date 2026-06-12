<!--
  统一学习页面 - 视频+练习一体化
  路由路径: /student/learning
  Phase 5
  Author: jackie
-->
<template>
  <div class="learning-view">
    <!-- 顶部课程导航 -->
    <div class="learning-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>返回课程
      </el-button>
      <span class="course-title">{{ courseName }}</span>
      <el-select v-model="selectedChapterId" placeholder="选择章节" class="chapter-select" @change="onChapterChange">
        <el-option v-for="ch in chapters" :key="ch.id" :label="ch.title" :value="ch.id" />
      </el-select>
    </div>

    <div class="learning-body">
      <!-- 左侧章节列表 -->
      <aside class="chapter-sidebar">
        <div v-for="ch in chapters" :key="ch.id"
             class="chapter-item"
             :class="{ active: selectedChapterId === ch.id }"
             @click="onChapterChange(ch.id)">
          <div class="chapter-title">{{ ch.title }}</div>
          <div class="chapter-meta">
            <el-tag v-if="ch.hasVideo" size="small" type="primary">视频</el-tag>
            <el-tag v-if="ch.hasExercise" size="small" type="success">{{ ch.exerciseCount }}练习</el-tag>
          </div>
        </div>
      </aside>

      <!-- 右侧内容区 -->
      <main class="learning-content">
        <!-- 视频播放区 -->
        <div v-if="currentVideo" class="video-section">
          <video ref="videoRef" controls class="video-player" :src="currentVideo.url">
            <source v-if="currentVideo.url" :src="currentVideo.url" :type="mimeType" />
          </video>
        </div>
        <el-empty v-else description="本章节暂无视频" />

        <!-- 练习入口 -->
        <div v-if="currentExerciseCount > 0" class="exercise-section">
          <el-button type="primary" size="large" @click="goExercise">
            <el-icon><Edit /></el-icon>开始随堂练习 ({{ currentExerciseCount }}题)
          </el-button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Edit } from '@element-plus/icons-vue'
import { getMyEnrollments } from '@/api/enrollment'
import { getCourseById } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { getVideos } from '@/api/video'
import { getExercises } from '@/api/exercise'
import { getLearningProgress } from '@/api/learning-progress'

const route = useRoute()
const router = useRouter()

const courseId = ref(parseInt(route.query.courseId) || null)
const courseName = ref('')
const chapters = ref([])
const selectedChapterId = ref(null)
const currentVideo = ref(null)
const currentExerciseCount = ref(0)

// 从 localStorage 恢复上次学习位置
const savedCourseId = localStorage.getItem('lastLearningCourse')

onMounted(async () => {
  if (!courseId.value && savedCourseId) {
    courseId.value = parseInt(savedCourseId)
  }
  if (!courseId.value) {
    // 如果没选课程，显示我的课程列表
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

function onChapterChange(chapterId) {
  selectChapter(chapterId)
}

function selectChapter(chapterId) {
  selectedChapterId.value = chapterId
  const ch = chapters.value.find(c => c.id === chapterId)
  if (ch) {
    currentVideo.value = ch.videos[0] || null
    currentExerciseCount.value = ch.exerciseCount
  }
}

function goExercise() {
  router.push(`/student/chapters/${selectedChapterId.value}/exercises`)
}

function goBack() {
  router.push('/student/my-courses')
}
</script>

<style scoped>
.learning-view { height: calc(100vh - 64px); display: flex; flex-direction: column; }
.learning-header { display: flex; align-items: center; gap: 16px; padding: 12px 24px; background: white; border-bottom: 1px solid #F1F5F9; }
.course-title { font-size: 18px; font-weight: 600; flex: 1; }
.chapter-select { width: 200px; }
.learning-body { flex: 1; display: flex; overflow: hidden; }
.chapter-sidebar { width: 220px; background: white; border-right: 1px solid #F1F5F9; overflow-y: auto; padding: 8px; }
.chapter-item { padding: 12px; border-radius: 8px; cursor: pointer; margin-bottom: 4px; transition: background 0.2s; }
.chapter-item:hover { background: #F8FAFC; }
.chapter-item.active { background: #EEF2FF; border-left: 3px solid #6366F1; }
.chapter-title { font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.chapter-meta { display: flex; gap: 4px; }
.learning-content { flex: 1; padding: 24px; overflow-y: auto; display: flex; flex-direction: column; align-items: center; gap: 24px; }
.video-section { width: 100%; max-width: 800px; }
.video-player { width: 100%; border-radius: 12px; background: #000; }
.exercise-section { margin-top: 16px; }
@media (max-width: 768px) {
  .chapter-sidebar { display: none; }
  .learning-body { flex-direction: column; }
  .chapter-select { display: block; }
}
</style>