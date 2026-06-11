<template>
  <div class="my-courses-container">
    <div class="header">
      <h2>我的课程</h2>
    </div>

    <el-tabs v-model="activeTab" class="course-tabs">
      <el-tab-pane label="进行中" name="in-progress">
        <div v-loading="loading" class="card-grid">
          <template v-if="inProgressCourses.length > 0">
            <el-card
              v-for="course in inProgressCourses"
              :key="course.courseId"
              class="course-card"
              shadow="hover"
            >
              <div class="card-content">
                <h3 class="course-title">{{ course.courseTitle }}</h3>
                <div class="progress-wrap">
                  <el-progress
                    :percentage="course.progress || 0"
                    :stroke-width="8"
                    :color="progressColor"
                  />
                  <span class="progress-text">{{ course.progress || 0 }}%</span>
                </div>
                <div class="card-footer">
                  <span class="last-time">最近学习：{{ formatTime(course.lastWatchAt || course.enrolledAt) }}</span>
                  <el-button type="primary" size="small" @click="handleContinue(course.courseId)">
                    继续学习
                  </el-button>
                </div>
              </div>
            </el-card>
          </template>
          <el-empty v-else description="暂无进行中的课程" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="已完成" name="completed">
        <div v-loading="loading" class="card-grid">
          <template v-if="completedCourses.length > 0">
            <el-card
              v-for="course in completedCourses"
              :key="course.courseId"
              class="course-card completed"
              shadow="hover"
            >
              <div class="card-content">
                <h3 class="course-title">{{ course.courseTitle }}</h3>
                <div class="progress-wrap">
                  <el-progress
                    :percentage="100"
                    :stroke-width="8"
                    :color="completedColor"
                  />
                  <span class="progress-text">100%</span>
                </div>
                <div class="card-footer">
                  <span class="last-time">完成时间：{{ formatTime(course.completedAt || course.enrolledAt) }}</span>
                  <el-button type="success" size="small" @click="handleViewDetail(course.courseId)">
                    查看详情
                  </el-button>
                </div>
              </div>
            </el-card>
          </template>
          <el-empty v-else description="暂无已完成的课程" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../../store/user'
import { getMyEnrollments } from '../../api/enrollment'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('in-progress')
const loading = ref(false)
const enrollments = ref([])

const progressColor = '#409eff'
const completedColor = '#67c23a'

const inProgressCourses = computed(() =>
  enrollments.value.filter(e => !e.completed)
)

const completedCourses = computed(() =>
  enrollments.value.filter(e => e.completed)
)

const formatTime = (timeStr) => {
  if (!timeStr) return '暂无'
  const d = new Date(timeStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const fetchEnrollments = async () => {
  if (!userStore.userInfo?.id) {
    await userStore.getInfo()
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('无法获取用户信息')
    return
  }
  loading.value = true
  try {
    const res = await getMyEnrollments(userId)
    // 后端返回已按 enrolledAt DESC 排序
    enrollments.value = res.data || []
  } catch (e) {
    ElMessage.error('加载课程失败')
  } finally {
    loading.value = false
  }
}

const handleContinue = (courseId) => {
  router.push(`/student/courses/${courseId}/learn`)
}

const handleViewDetail = (courseId) => {
  router.push(`/student/courses/${courseId}/detail`)
}

onMounted(() => {
  fetchEnrollments()
})
</script>

<style scoped>
.my-courses-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.header {
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.course-tabs {
  background: #fff;
  padding: 0 20px;
  border-radius: 8px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  padding: 20px 0;
}

.course-card {
  padding: 20px;
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.course-title {
  margin: 0;
  font-size: 16px;
  color: #303133;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-wrap :deep(.el-progress) {
  flex: 1;
}

.progress-text {
  font-size: 14px;
  color: #606266;
  min-width: 36px;
  text-align: right;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.last-time {
  font-size: 12px;
  color: #909399;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>