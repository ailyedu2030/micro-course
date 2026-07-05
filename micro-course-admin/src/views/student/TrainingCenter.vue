<!--
  训练中心
  路由路径: /student/training
  Phase 9
  Author: Phase9-Development-Team
-->
<template>
  <div class="training-center">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>培训中心</el-breadcrumb-item>
    </el-breadcrumb>

    <h2 class="page-title">训练中心</h2>
    <p class="page-subtitle">选择课程章节进行随堂练习</p>

    <!-- 骨架屏加载 -->
    <div v-if="loading" class="loading-wrap">
      <el-skeleton :rows="4" animated />
    </div>

    <!-- 加载失败 -->
    <el-result v-else-if="error" icon="error" title="加载失败" sub-title="训练数据加载异常，请稍后重试">
      <template #extra>
        <el-button type="primary" @click="fetchData">重新加载</el-button>
      </template>
    </el-result>

    <!-- 空状态 -->
    <el-empty v-else-if="enrollments.length === 0" description="你还没有报名任何课程">
      <el-button type="primary" @click="$router.push('/student/courses')">去选课</el-button>
    </el-empty>

    <!-- 课程列表 -->
    <div v-else class="course-list">
        <el-card v-for="enr in enrollments" :key="enr.courseId" class="course-card student-card-item" shadow="hover" @click="goCourse(enr)" style="cursor:pointer">
        <div class="course-header">
          <h3>{{ enr.courseName }}</h3>
          <el-progress :percentage="Math.round(enr.progress || 0)" :stroke-width="8" class="student-progress" />
        </div>
        <el-scrollbar class="chapter-scrollbar">
          <div class="chapter-list">
            <div v-for="ch in enr.chapters" :key="ch.id" class="chapter-item" role="button" tabindex="0" :aria-label="`进入章节练习 ${ch.title}`" @click="goExercise(ch.id)" @keydown.enter="goExercise(ch.id)" @keydown.space.prevent="goExercise(ch.id)">
              <span>{{ ch.title }}</span>
              <el-tag v-if="ch.exerciseCount > 0" size="small" type="success">{{ ch.exerciseCount }} 练习</el-tag>
              <el-tag v-else size="small" type="info">暂无练习</el-tag>
            </div>
            <el-empty v-if="!enr.chapters || enr.chapters.length === 0" description="暂无章节" :image-size="40" />
          </div>
        </el-scrollbar>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyEnrollments } from '@/api/enrollment'
import { getChapters } from '@/api/chapter'
import { getExercises } from '@/api/exercise'

const router = useRouter()
const loading = ref(true)
const error = ref(false)
const enrollments = ref([])

/**
 * 修复 N+1：三重嵌套串行 → 两批并行
 * 请求数：从 N×M+1 降为 N+M+1（并行后 wall-clock 大幅缩短）
 * - 第一批：并行获取所有课程的章节（N 请求）
 * - 第二批：并行获取所有章节的练习（N×M 请求，但并行了）
 */
onMounted(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  error.value = false
  try {
    // Step 1: 获取我的课程列表（1 请求）
    const { data } = await getMyEnrollments({ completed: false })
    const items = data?.items || data || []

    if (items.length === 0) {
      enrollments.value = []
      return
    }

    // Step 2: 并行获取所有课程的章节（N 请求，并行）
    const chapterResults = await Promise.all(
      items.map(e => getChapters({ courseId: e.courseId, size: 100 }))
    )
    const chapterLists = chapterResults.map(r => r.data?.items || [])

    // Step 3: 收集所有章节 ID，并行获取所有章节的练习（N×M 请求，并行）
    const allChapterIds = chapterLists.flatMap(list => list.map(c => c.id))
    const exerciseResults = await Promise.all(
      allChapterIds.map(cid => getExercises({ chapterId: cid, size: 50 }))
    )

    // 建立 chapterId → exerciseCount 的映射
    const exerciseCountMap = new Map()
    let idx = 0
    for (const chId of allChapterIds) {
      const exercises = exerciseResults[idx]?.data?.items || []
      exerciseCountMap.set(chId, exercises.length)
      idx++
    }

    // 组装数据
    for (let i = 0; i < items.length; i++) {
      items[i].chapters = chapterLists[i].map(ch => ({
        ...ch,
        exerciseCount: exerciseCountMap.get(ch.id) || 0
      }))
    }

    enrollments.value = items
  } catch (e) {
    console.error('[TrainingCenter] 训练中心加载失败', e)
    error.value = true
    ElMessage.error('加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function goCourse(enr) {
  router.push(`/student/courses/${enr.courseId}`)
}

function goExercise(chapterId) {
  router.push(`/student/chapters/${chapterId}/exercises`)
}
</script>

<style scoped>
.training-center {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-6);
  min-height: 100dvh;
  background: var(--el-bg-color-page);
  animation: fadeIn var(--duration-slow) var(--ease-out);
}
.page-breadcrumb { margin-bottom: var(--space-4); }
.page-title {
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin-bottom: var(--space-2);
  letter-spacing: var(--tracking-tight);
}
.page-subtitle {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  margin-bottom: var(--space-6);
}
.course-list { display: flex; flex-direction: column; gap: var(--space-4); }
.course-card {
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}
.course-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-tinted-lg), var(--shadow-lg) !important;
}
.course-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-4);
}
.course-header h3 {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}
.chapter-list { display: flex; flex-direction: column; gap: var(--space-2); }
.chapter-scrollbar { max-height: 320px; }
.chapter-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--space-3) var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-out),
              transform var(--duration-base) var(--ease-out);
}
.chapter-item:hover {
  background: var(--role-primary-light-9);
  transform: translateX(4px);
}
.chapter-item:active {
  transform: translateX(2px) scale(0.99);
}
.loading-wrap { padding: var(--space-8); }
</style>