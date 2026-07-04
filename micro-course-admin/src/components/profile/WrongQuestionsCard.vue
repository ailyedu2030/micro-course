<!--
  我的错题卡片（Round 11-3 从 Profile.vue 拆分）
  WrongQuestionsCard: 错题列表 + 课程筛选 + 重温视频，自包含 fetch
  Author: jackie
-->
<template>
  <el-card class="profile-card wrong-questions-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>我的错题</span>
      </div>
    </template>

    <div class="wrong-toolbar">
      <el-select v-model="selectedCourseId" placeholder="选择课程筛选" clearable @change="fetchWrongQuestions" :class="{ 'course-select': isMobile }">
        <el-option
          v-for="course in myCourses"
          :key="course.courseId"
          :label="course.courseTitle"
          :value="course.courseId"
        />
      </el-select>
    </div>

    <div class="wrong-table-wrapper">
      <!-- PC 表格 -->
      <el-table v-if="!isMobile" v-loading="wrongLoading" :aria-busy="wrongLoading" :data="wrongQuestions" stripe border max-height="400" class="data-table wrong-questions-table" aria-label="错题列表">
        <el-table-column prop="questionContent" label="错题内容" min-width="200">
          <template #default="{ row }">
            <span>{{ row.questionContent }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseTitle" label="所属课程" width="150" />
        <el-table-column prop="chapterTitle" label="所属章节" width="150" />
        <el-table-column prop="wrongCount" label="错误次数" width="100" align="center" />
        <el-table-column prop="correctAnswer" label="正确答案" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.correctAnswer }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="入库时间" width="170" />
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.chapterId"
              type="primary"
              size="small"
              text
              @click="handleReviewVideo(row)"
            >
              重温视频
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 移动端表格 -->
      <el-table v-else v-loading="wrongLoading" :aria-busy="wrongLoading" :data="wrongQuestions" stripe border max-height="300" class="data-table wrong-questions-table" aria-label="错题列表">
        <el-table-column prop="questionContent" label="错题内容" min-width="150">
          <template #default="{ row }">
            <span>{{ row.questionContent || row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseTitle" label="课程" width="100" show-overflow-tooltip />
        <el-table-column prop="correctAnswer" label="答案" width="70" align="center">
          <template #default="{ row }">
            <el-tag type="success" size="small">{{ row.correctAnswer }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.chapterId"
              type="primary"
              size="small"
              text
              @click="handleReviewVideo(row)"
            >
              重温
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-if="!wrongLoading && wrongQuestions.length === 0" description="暂无错题记录，继续保持！" />
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getMyWrongQuestions } from '@/api/wrong-question'
import { getMyEnrollments } from '@/api/enrollment'

defineProps({
  isMobile: { type: Boolean, default: false }
})

const router = useRouter()
const userStore = useUserStore()

const wrongQuestions = ref([])
const wrongLoading = ref(false)
const myCourses = ref([])
const selectedCourseId = ref('')

const fetchMyEnrollments = async () => {
  try {
    const userId = userStore.userInfo?.id
    if (!userId) return
    const res = await getMyEnrollments({ userId })
    myCourses.value = res.data || []
  } catch {
    // silent
  }
}

const fetchWrongQuestions = async () => {
  wrongLoading.value = true
  try {
    const params = {}
    if (selectedCourseId.value) params.courseId = selectedCourseId.value
    const res = await getMyWrongQuestions(params)
    wrongQuestions.value = res.data || []
  } catch {
    ElMessage.error('获取错题记录失败')
  } finally {
    wrongLoading.value = false
  }
}

const handleReviewVideo = (row) => {
  if (!row.chapterId) return
  const query = { chapterId: row.chapterId }
  if (row.watchPosition) {
    query.timestamp = row.watchPosition
  }
  router.push({ path: `/student/courses/${row.courseId}`, query })
}

fetchMyEnrollments()
fetchWrongQuestions()
</script>

<style scoped>
.profile-card {
  margin-bottom: var(--space-5);
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}
.profile-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
.card-header {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.wrong-toolbar {
  margin-bottom: var(--space-4);
  display: flex;
  gap: var(--space-3);
}
.course-select {
  width: 200px;
}

.wrong-table-wrapper {
  border-radius: var(--radius-md);
  overflow: hidden;
}
.wrong-questions-table {
  border-radius: var(--radius-md);
}
.wrong-questions-table .el-button {
  cursor: pointer;
}
.wrong-questions-card :deep(.el-card__header) {
  padding: 12px var(--space-5);
}

:deep(.el-button) { cursor: pointer; }

@media (max-width: 768px) {
  .profile-card { margin-bottom: var(--space-4); }

  .wrong-toolbar {
    flex-direction: column;
    gap: var(--space-2);
  }
  .wrong-toolbar .el-select,
  .course-select {
    width: 100%;
  }
}
</style>
