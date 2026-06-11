<template>
  <div class="student-grades">
    <!-- 搜索区 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="选择课程">
          <el-select v-model="searchForm.courseId" placeholder="请选择课程" clearable class="course-select" @change="handleCourseChange">
            <el-option v-for="item in courseOptions" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 表格区 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>学员成绩</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="tableData" stripe border class="data-table">
        <el-table-column type="index" label="序号" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="realName" label="姓名" width="120" />
        <el-table-column prop="totalWatchTime" label="学习时长" width="120" align="center">
          <template #default="{ row }">
            {{ formatWatchTime(row.totalWatchTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="videoProgress" label="视频进度" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.videoProgress || 0" :stroke-width="10" />
          </template>
        </el-table-column>
        <el-table-column prop="exerciseAvgScore" label="练习平均分" width="120" align="center">
          <template #default="{ row }">
            {{ row.exerciseAvgScore != null ? row.exerciseAvgScore.toFixed(1) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="lastWatchAt" label="最近活跃时间" min-width="180">
          <template #default="{ row }">
            {{ row.lastWatchAt || '-' }}
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,sizes,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
/**
 * 学员成绩明细页面
 * @author Claude Code Agent
 */
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCourses } from '@/api/course'
import { getCourseEnrollments } from '@/api/enrollment'
import { useUserStore } from '@/store/user'

const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const courseOptions = ref([])

const searchForm = reactive({
  courseId: ''
})

const formatWatchTime = (minutes) => {
  if (!minutes) return '-'
  if (minutes < 60) return `${minutes}分钟`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}小时${mins}分钟` : `${hours}小时`
}

const fetchCourses = async () => {
  try {
    const teacherId = userStore.userInfo?.id
    const { data } = await getCourses({ size: 1000, teacherId })
    courseOptions.value = data.items || []
    if (route.query.courseId) {
      searchForm.courseId = Number(route.query.courseId)
    }
  } catch (error) {
    ElMessage.error('获取课程列表失败')
  }
}

const fetchData = async () => {
  if (!searchForm.courseId) {
    tableData.value = []
    totalElements.value = 0
    return
  }
  loading.value = true
  try {
    const params = {
      page: page.value - 1,
      size: size.value,
      courseId: searchForm.courseId
    }
    const { data } = await getCourseEnrollments(params)
    const result = data
    if (Array.isArray(result)) {
      tableData.value = result
      totalElements.value = result.length
    } else {
      tableData.value = result.items || []
      totalElements.value = result.totalElements || tableData.value.length
    }
  } catch (error) {
    ElMessage.error('获取学员成绩失败')
  } finally {
    loading.value = false
  }
}

const handleCourseChange = () => {
  page.value = 1
  fetchData()
}

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

onMounted(() => {
  fetchCourses()
  if (searchForm.courseId) {
    fetchData()
  }
})
</script>

<style scoped>
.student-grades {
  padding: 20px;
}

.search-card {
  margin-bottom: 16px;
}

.course-select {
  width: 200px;
}

.data-table {
  width: 100%;
}

.table-card :deep(.el-card__header) {
  padding: 12px 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .student-grades {
    padding: 12px;
  }

  .search-card {
    margin-bottom: 12px;
  }
}
</style>